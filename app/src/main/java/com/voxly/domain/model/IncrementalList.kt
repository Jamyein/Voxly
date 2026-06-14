package com.voxly.domain.model

/**
 * Diff-based list update command, modelled after Gramophone's IncrementalList.
 *
 * Instead of replacing the whole list (which forces LazyColumn to compare
 * `equals()` on every item), the Aggregator emits small operations that the
 * UI applies locally — O(diff size) instead of O(list size).
 *
 * For a 10000-item library, a single "Album X added" mutation is one
 * [Insert] event, not a 10000-item list replacement.
 *
 * Each event carries a [after] snapshot so late subscribers (replay = 1)
 * can recover the full state from the last event.
 */
sealed class IncrementalList<T> {
    abstract val after: List<T>

    /** Items were inserted at the end of the list. */
    data class Insert<T>(
        val items: List<T>,
        override val after: List<T>,
    ) : IncrementalList<T>() {
        init {
            require(items.isNotEmpty()) { "Insert.items must not be empty" }
        }
    }

    /** Items were removed from the list. [items] are the *removed* snapshots, for UI animation. */
    data class Remove<T>(
        val items: List<T>,
        override val after: List<T>,
    ) : IncrementalList<T>() {
        init {
            require(items.isNotEmpty()) { "Remove.items must not be empty" }
        }
    }

    /** Items were updated in place (mtime change, metadata edit, etc.). [items] are the new values. */
    data class Update<T>(
        val items: List<T>,
        override val after: List<T>,
    ) : IncrementalList<T>() {
        init {
            require(items.isNotEmpty()) { "Update.items must not be empty" }
        }
    }

    /** The list was completely reset (sort change, filter toggle, etc.). [after] is the full new list. */
    data class Reset<T>(
        override val after: List<T>,
    ) : IncrementalList<T>()
}
