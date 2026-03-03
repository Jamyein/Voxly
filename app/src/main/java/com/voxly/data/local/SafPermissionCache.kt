package com.voxly.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF 权限缓存类，用于优化批量元数据编辑性能
 *
 * 优化策略：
 * 1. 缓存 treeUri 与物理路径的映射关系 (O(1) 查找)
 * 2. 缓存文件夹级别的 Document ID，避免重复逐级查找
 * 3. 批量处理同一文件夹下的文件时复用已解析的父目录 ID
 */
@Singleton
class SafPermissionCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SafPermissionCache"
    }

    // TreeUri -> 物理路径映射
    private val treeUriToPathMap = mutableMapOf<Uri, String>()

    // 物理路径 -> TreeUri 映射
    private val pathToTreeUriMap = mutableMapOf<String, Uri>()

    // 文件夹 Document ID 缓存: (TreeUri, folderRelativePath) -> DocumentId
    private val folderDocIdCache = mutableMapOf<Pair<Uri, String>, String>()

    // 根目录的 Document ID 缓存: TreeUri -> Root DocumentId
    private val rootDocIdCache = mutableMapOf<Uri, String>()

    // 缓存是否已初始化
    private var isInitialized = false

    /**
     * 从持久化权限初始化缓存
     * 应在批量操作开始时调用
     */
    fun initialize() {
        if (isInitialized) {
            return
        }

        val permissions = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }

        Timber.d(TAG, "Initializing cache with ${permissions.size} persisted permissions")

        for (perm in permissions) {
            val path = mapTreeUriToPath(perm.uri)
            if (path != null) {
                treeUriToPathMap[perm.uri] = path
                pathToTreeUriMap[path] = perm.uri

                // 预缓存根目录的 Document ID
                val rootDocId = DocumentsContract.getTreeDocumentId(perm.uri)
                rootDocIdCache[perm.uri] = rootDocId

                Timber.d(TAG, "Cached: ${perm.uri} -> $path")
            }
        }

        isInitialized = true
        Timber.d(TAG, "Cache initialized: ${treeUriToPathMap.size} entries")
    }

    /**
     * 清空缓存
     * 当权限发生变化时调用
     */
    fun invalidate() {
        treeUriToPathMap.clear()
        pathToTreeUriMap.clear()
        folderDocIdCache.clear()
        rootDocIdCache.clear()
        isInitialized = false
        Timber.d(TAG, "Cache invalidated")
    }

    /**
     * 获取文件路径对应的 TreeUri
     */
    fun getTreeUriForPath(filePath: String): Uri? {
        ensureInitialized()

        val normalizedPath = normalizeFilePath(filePath)

        // 直接查找
        pathToTreeUriMap[normalizedPath]?.let { return it }

        // 前缀匹配 (file is inside the tree)
        for ((path, uri) in pathToTreeUriMap) {
            if (normalizedPath.startsWith("$path/")) {
                return uri
            }
        }

        return null
    }

    /**
     * 获取 TreeUri 对应的物理路径
     */
    fun getPathForTreeUri(treeUri: Uri): String? {
        ensureInitialized()
        return treeUriToPathMap[treeUri]
    }

    /**
     * 获取或查找文件夹的 Document ID
     * 优化：同一文件夹下的多首歌只需解析一次
     */
    fun getOrFindFolderDocId(treeUri: Uri, folderRelativePath: String): String? {
        ensureInitialized()

        val cacheKey = treeUri to folderRelativePath
        folderDocIdCache[cacheKey]?.let { return it }

        // 从缓存中获取根 Document ID
        val rootDocId = rootDocIdCache[treeUri] ?: return null

        // 如果是根目录，直接返回
        if (folderRelativePath.isBlank()) {
            folderDocIdCache[cacheKey] = rootDocId
            return rootDocId
        }

        // 查找文件夹的 Document ID
        val folderDocId = findFolderDocId(treeUri, rootDocId, folderRelativePath)
        if (folderDocId != null) {
            folderDocIdCache[cacheKey] = folderDocId
        }

        return folderDocId
    }

    /**
     * 缓存单个文件的 Document ID (用于文件级别的缓存)
     */
    fun cacheFileDocId(treeUri: Uri, relativePath: String, docId: String) {
        val cacheKey = treeUri to relativePath
        folderDocIdCache[cacheKey] = docId
    }

    /**
     * 获取缓存的文件 Document ID
     */
    fun getCachedFileDocId(treeUri: Uri, relativePath: String): String? {
        val cacheKey = treeUri to relativePath
        return folderDocIdCache[cacheKey]
    }

    /**
     * 批量添加同一 TreeUri 下的多个文件 Document ID
     */
    fun cacheFileDocIds(treeUri: Uri, relativePathsAndDocIds: List<Pair<String, String>>) {
        for ((relativePath, docId) in relativePathsAndDocIds) {
            val cacheKey = treeUri to relativePath
            folderDocIdCache[cacheKey] = docId
        }
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }

    /**
     * 映射 tree URI 到文件系统路径
     */
    private fun mapTreeUriToPath(treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (documentId.startsWith("raw:")) return documentId.removePrefix("raw:")

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
            else -> if (relative.isEmpty()) "/storage/$volume" else "/storage/$volume/$relative"
        }

        return normalizeFilePath(path)
    }

    /**
     * 规范化文件路径
     */
    private fun normalizeFilePath(filePath: String): String {
        var normalized = filePath.replace('\\', '/')
        normalized = normalized.replace(Regex("//+"), "/")
        normalized = normalized.trimEnd('/')
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC)
        normalized = try {
            java.net.URLDecoder.decode(normalized, "UTF-8")
        } catch (e: Exception) {
            normalized
        }
        return normalized
    }

    /**
     * 在树中查找文件夹的 Document ID
     */
    private fun findFolderDocId(treeUri: Uri, rootDocId: String, folderRelativePath: String): String? {
        val segments = folderRelativePath.split('/').filter { it.isNotBlank() }
        var currentDocId = rootDocId

        for (segment in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            var nextDocId: String? = null
            val normalizedSegment = Normalizer.normalize(segment, Normalizer.Form.NFC)

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex)
                    val normalizedName = Normalizer.normalize(displayName ?: "", Normalizer.Form.NFC)
                    if (normalizedName == normalizedSegment) {
                        nextDocId = cursor.getString(idIndex)
                        break
                    }
                }
            }

            currentDocId = nextDocId ?: return null
        }

        return currentDocId
    }
}
