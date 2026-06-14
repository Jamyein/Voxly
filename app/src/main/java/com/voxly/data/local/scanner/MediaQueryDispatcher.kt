package com.voxly.data.local.scanner

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.WorkerThread

/**
 * Chainable Builder for MediaStore queries, inspired by BoomingMusic's
 * [MediaQueryDispatcher].
 *
 * Instead of constructing raw projection/selection/args arrays at each
 * call site, use the builder to compose clauses dynamically — especially
 * useful for whitelist/blacklist paths, minimum duration, and other
 * runtime-filtered selections.
 *
 * Example:
 * ```kotlin
 * MediaQueryDispatcher()
 *     .uri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
 *     .projection(DISPLAY_NAME, RELATIVE_PATH, DATE_MODIFIED)
 *     .addSelection("${MediaStore.Audio.Media.IS_MUSIC} != 0")
 *     .addSelection("${MediaStore.Audio.Media.DURATION} >= ?", args = [30000])
 *     .addSelection("${MediaStore.Audio.Media.DATA} NOT LIKE ?", args = ["/storage/emulated/0/Alarms/%"])
 *     .sort(MediaStore.Audio.Media.TITLE + " COLLATE UNICODE ASC")
 *     .dispatch(contentResolver)
 * ```
 */
class MediaQueryDispatcher @JvmOverloads constructor(
    private var _uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    private var _projection: Array<String>? = null,
    private val _selectionClauses: MutableList<String> = mutableListOf(),
    private val _selectionArgs: MutableList<String> = mutableListOf(),
    private var _sortOrder: String? = null
) {

    fun uri(uri: Uri): MediaQueryDispatcher = apply { _uri = uri }
    fun projection(vararg cols: String): MediaQueryDispatcher = apply { _projection = arrayOf(*cols) }

    /**
     * Add a SELECTION clause. Multiple clauses are joined with [mode].
     * Selection values (prepared parameters, `?` placeholders) go in [args].
     *
     * Example:
     * ```kotlin
     * dispatcher
     *     .addSelection("${AudioColumns.DATA} NOT LIKE ?", args = ["/Alarms/%"])
     *     .addSelection("${AudioColumns.DURATION} >= ?", args = ["30000"])
     * ```
     */
    fun addSelection(
        clause: String,
        mode: String = "AND",
        vararg args: String
    ): MediaQueryDispatcher = apply {
        if (clause.isNotBlank()) {
            if (_selectionClauses.isEmpty()) {
                _selectionClauses.add(clause)
            } else {
                _selectionClauses.add("$mode $clause")
            }
            _selectionArgs.addAll(args)
        }
    }

    fun sort(order: String?): MediaQueryDispatcher = apply { _sortOrder = order }

    /**
     * Execute the query and return the [Cursor]. Must be called on a
     * background thread (the same as any MediaStore cursor read).
     */
    @WorkerThread
    fun dispatch(contentResolver: ContentResolver): Cursor? {
        val selection = _selectionClauses.joinToString(" ")
            .takeIf { it.isNotEmpty() }
        val selectionArgs = _selectionArgs.toTypedArray()
            .takeIf { it.isNotEmpty() }
        return contentResolver.query(
            _uri, _projection, selection, selectionArgs, _sortOrder
        )
    }
}
