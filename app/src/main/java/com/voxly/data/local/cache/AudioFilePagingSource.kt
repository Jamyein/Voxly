package com.voxly.data.local.cache

import androidx.paging.PagingSource
import androidx.paging.PagingState

class AudioFilePagingSource(
    private val dao: CachedAudioFileDao,
    private val directoryPath: String? = null
) : PagingSource<Int, CachedAudioFileEntity>() {

    /**
     * Cached total row count for the active [directoryPath]. Paging 3.5 does not buffer this
     * itself, and re-querying `SELECT COUNT(*)` on every append/prepend multiplies index lookups
     * for no benefit while the dataset is stable. We refresh on [LoadParams.Refresh] and on
     * [invalidate] (which fires when Room's invalidation tracker signals a write to the table).
     *
     * @Volatile because Paging may run REFRESH and APPEND concurrently when the user scrolls
     * fast after a refresh; the cache must publish a value that any in-flight load can read.
     */
    @Volatile
    private var cachedTotalCount: Int? = null

    init {
        // PagingSource.invalidate() is final in Paging 3.5; the only way to react to
        // invalidation is via registerInvalidatedCallback. The callback fires when the source
        // is marked invalid (manual .refresh, Room invalidation tracker, scope cancellation).
        registerInvalidatedCallback {
            cachedTotalCount = null
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CachedAudioFileEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CachedAudioFileEntity> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize

            val entities = if (directoryPath != null) {
                dao.getAudioFilesPagedByDirectory(directoryPath, page * pageSize, pageSize)
            } else {
                dao.getAudioFilesPaged(page * pageSize, pageSize)
            }

            val totalCount = if (params is LoadParams.Refresh) {
                val fresh = queryTotalCount()
                cachedTotalCount = fresh
                fresh
            } else {
                cachedTotalCount ?: queryTotalCount().also { cachedTotalCount = it }
            }

            val nextKey = if (entities.isEmpty() || (page + 1) * pageSize >= totalCount) {
                null
            } else {
                page + 1
            }

            val prevKey = if (page == 0) null else page - 1

            LoadResult.Page(
                data = entities,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // Note: PagingSource.invalidate() is final in Paging 3.5 and cannot be overridden. The
    // callback registered in init {} is the only hook for clearing cachedTotalCount.

    private suspend fun queryTotalCount(): Int =
        if (directoryPath != null) {
            dao.getTotalCountByDirectory(directoryPath)
        } else {
            dao.getTotalCount()
        }
}