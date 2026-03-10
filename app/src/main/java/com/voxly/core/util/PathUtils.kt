package com.voxly.core.util

import java.io.File
import java.text.Normalizer

/**
 * Utility functions for file path operations.
 */
object PathUtils {

    /**
     * Normalizes a file path for consistent comparison and storage.
     * - Replaces backslashes with forward slashes
     * - Removes duplicate slashes
     * - Removes trailing slashes
     * - Normalizes Unicode (NFC form)
     * - Decodes URL-encoded characters
     * - Resolves canonical path when file exists
     */
    @JvmStatic
    fun normalizeFilePath(filePath: String): String {
        return try {
            // 1. Replace backslashes with forward slashes
            var normalized = filePath.replace('\\', '/')

            // 2. Remove duplicate slashes
            normalized = normalized.replace(Regex("//+"), "/")

            // 3. Remove trailing slash
            normalized = normalized.trimEnd('/')

            // 4. Normalize Unicode (NFC form)
            normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC)

            // 5. Handle URL-encoded characters if present
            normalized = try {
                java.net.URLDecoder.decode(normalized, "UTF-8")
            } catch (e: Exception) {
                normalized
            }

            // 6. Try to get canonical path for proper path resolution (only if file exists)
            val file = File(normalized)
            if (file.exists()) {
                val canonical = file.canonicalPath
                // Apply normalization again after canonical resolution
                return Normalizer.normalize(canonical.replace('\\', '/'), Normalizer.Form.NFC)
            }

            normalized
        } catch (e: Exception) {
            // If anything fails, return cleaned original path
            filePath.replace('\\', '/').replace(Regex("//+"), "/").trimEnd('/')
        }
    }
}
