package com.voxly.data.local.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.voxly.data.local.SafPermissionCache
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.text.Normalizer
import javax.inject.Inject
import com.voxly.core.util.PathUtils
import javax.inject.Singleton

enum class SafGrantType {
    TREE,
    DOCUMENT
}

data class SafGrantMatch(
    val permission: android.content.UriPermission,
    val grantType: SafGrantType,
    val basePath: String,
    val matchLength: Int
)

@Singleton
class SafWriteAccessService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safPermissionCache: SafPermissionCache
) {
    companion object {
        private const val TAG = "SafWriteAccessService"

        internal fun calculateMatchLength(normalizedFilePath: String, normalizedBasePath: String): Int {
            return if (
                normalizedFilePath == normalizedBasePath ||
                normalizedFilePath.startsWith("$normalizedBasePath/")
            ) {
                normalizedBasePath.length
            } else {
                -1
            }
        }
    }

    fun persistPermission(uri: Uri, grantType: SafGrantType): Result<Unit> = runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        if (grantType == SafGrantType.TREE) {
            // Tree-level grants can affect path-to-docId cache correctness.
            safPermissionCache.invalidate()
        }
    }

    fun findValidWritePermission(filePath: String): android.content.UriPermission? {
        return findBestWriteGrant(filePath)?.permission
    }

    fun findBestWriteGrant(filePath: String): SafGrantMatch? {
        val normalizedFilePath = PathUtils.normalizeFilePath(filePath)
        val permissions = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }

        if (permissions.isEmpty()) {
            return null
        }

        val candidates = permissions.mapNotNull { permission ->
            val grantType = detectGrantType(permission.uri) ?: return@mapNotNull null
            val basePath = when (grantType) {
                SafGrantType.TREE -> mapTreeUriToPath(permission.uri)
                SafGrantType.DOCUMENT -> mapDocumentUriToPath(permission.uri)
            } ?: return@mapNotNull null

            val normalizedBasePath = PathUtils.normalizeFilePath(basePath)
            val matchLength = calculateMatchLength(normalizedFilePath, normalizedBasePath)
            if (matchLength < 0) {
                return@mapNotNull null
            }

            SafGrantMatch(
                permission = permission,
                grantType = grantType,
                basePath = normalizedBasePath,
                matchLength = matchLength
            )
        }.sortedByDescending { it.matchLength }

        return candidates.firstOrNull { isPermissionValid(it.permission, filePath) }
    }

    fun resolveWritableDocumentUri(filePath: String): Uri? {
        val permission = findValidWritePermission(filePath) ?: return null
        return resolveDocumentUri(filePath, permission)
    }

    fun resolveDocumentUri(filePath: String, permission: android.content.UriPermission): Uri? {
        return when (detectGrantType(permission.uri)) {
            SafGrantType.TREE -> {
                val relativePath = getRelativePath(filePath, permission)
                findDocumentUriInTree(permission.uri, relativePath)
            }
            SafGrantType.DOCUMENT -> {
                val docPath = mapDocumentUriToPath(permission.uri)?.let { PathUtils.normalizeFilePath(it) } ?: return null
                val normalizedFilePath = PathUtils.normalizeFilePath(filePath)
                if (normalizedFilePath == docPath) permission.uri else null
            }
            null -> null
        }
    }

    fun getRelativePath(filePath: String, permission: android.content.UriPermission): String {
        val treePath = mapTreeUriToPath(permission.uri) ?: return File(filePath).name
        val normalizedFilePath = PathUtils.normalizeFilePath(filePath)
        val normalizedTreePath = PathUtils.normalizeFilePath(treePath)
        return if (normalizedFilePath.startsWith("$normalizedTreePath/")) {
            normalizedFilePath.removePrefix("$normalizedTreePath/")
        } else {
            File(filePath).name
        }
    }

    fun isPermissionValid(permission: android.content.UriPermission, filePath: String): Boolean {
        return try {
            val targetDocUri = resolveDocumentUri(filePath, permission) ?: return false
            context.contentResolver.openFileDescriptor(targetDocUri, "rw")?.use { true } ?: false
        } catch (e: SecurityException) {
            Timber.w(TAG, "Permission validation failed with SecurityException for: $filePath", e)
            false
        } catch (e: Exception) {
            Timber.w(TAG, "Permission validation failed for: $filePath", e)
            false
        }
    }

    fun normalizeFilePath(filePath: String): String {
        return try {
            var normalized = filePath.replace('\\', '/')
            normalized = normalized.replace(Regex("//+"), "/")
            normalized = normalized.trimEnd('/')
            normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC)
            normalized = try {
                java.net.URLDecoder.decode(normalized, "UTF-8")
            } catch (_: Exception) {
                normalized
            }

            val file = File(normalized)
            if (file.exists()) {
                return Normalizer.normalize(file.canonicalPath.replace('\\', '/'), Normalizer.Form.NFC)
            }
            normalized
        } catch (_: Exception) {
            filePath.replace('\\', '/').replace(Regex("//+"), "/").trimEnd('/')
        }
    }

    fun mapTreeUriToPath(treeUri: Uri): String? {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        return mapDocumentIdToPath(treeDocId)
    }

    private fun mapDocumentUriToPath(documentUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull() ?: return null
        return mapDocumentIdToPath(documentId)
    }

    private fun mapDocumentIdToPath(documentId: String): String? {
        if (documentId.startsWith("raw:")) {
            return PathUtils.normalizeFilePath(documentId.removePrefix("raw:"))
        }

        val parts = documentId.split(":", limit = 2)
        val volume = parts.firstOrNull().orEmpty()
        val relative = parts.getOrNull(1)?.trim('/').orEmpty()

        val path = when {
            volume.equals("primary", ignoreCase = true) -> {
                val root = "/storage/emulated/0"
                if (relative.isEmpty()) root else "$root/$relative"
            }
            volume.equals("home", ignoreCase = true) -> {
                val root = "/storage/emulated/0/Documents"
                if (relative.isEmpty()) root else "$root/$relative"
            }
            volume.isNotBlank() -> {
                if (relative.isEmpty()) "/storage/$volume" else "/storage/$volume/$relative"
            }
            else -> null
        }

        return path?.let { PathUtils.normalizeFilePath(it) }
    }

    private fun detectGrantType(uri: Uri): SafGrantType? {
        return when {
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess -> SafGrantType.TREE
            runCatching { DocumentsContract.getDocumentId(uri) }.isSuccess -> SafGrantType.DOCUMENT
            else -> null
        }
    }

    /**
     * Finds document URI in tree using progressive query.
     */
    private fun findDocumentUriInTree(treeUri: Uri, relativePath: String): Uri? {
        safPermissionCache.initialize()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (relativePath.isBlank()) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        }

        val normalizedRelative = relativePath.trim('/').replace('\\', '/')
        val segments = normalizedRelative.split('/').filter { it.isNotBlank() }
        val totalSegments = segments.size

        var currentDocId = rootDocId
        var startIndex = 0

        val folderPaths = mutableListOf<String>()
        for (i in 0 until totalSegments - 1) {
            folderPaths.add(segments.subList(0, i + 1).joinToString("/"))
        }

        for ((index, folderPath) in folderPaths.withIndex()) {
            val cachedFolderDocId = safPermissionCache.getOrFindFolderDocId(treeUri, folderPath)
            if (cachedFolderDocId != null) {
                currentDocId = cachedFolderDocId
                startIndex = index + 1
            } else {
                break
            }
        }

        for (i in startIndex until totalSegments) {
            val segment = segments[i]
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            var nextDocId: String? = null
            val normalizedSegment = Normalizer.normalize(segment, Normalizer.Form.NFC)

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex)
                    val normalizedName = Normalizer.normalize(displayName ?: "", Normalizer.Form.NFC)
                    if (normalizedName.equals(normalizedSegment, ignoreCase = true)) {
                        nextDocId = cursor.getString(idIndex)
                        break
                    }
                }
            }

            if (nextDocId == null) {
                Timber.w(TAG, "findDocumentUriInTree: segment not found: $segment")
                return null
            }

            currentDocId = nextDocId
            if (i < totalSegments - 1) {
                val folderPath = segments.subList(0, i + 1).joinToString("/")
                safPermissionCache.cacheFileDocId(treeUri, folderPath, currentDocId)
            }
        }

        return DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
    }
}
