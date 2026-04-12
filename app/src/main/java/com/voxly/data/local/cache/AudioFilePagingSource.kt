package com.voxly.data.local.cache

import androidx.paging.PagingSource
import androidx.paging.PagingState

class AudioFilePagingSource(
    private val dao: CachedAudioFileDao,
    private val directoryPath: String? = null
) : PagingSource<Int, CachedAudioFileEntity>() {
    
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
            
            val totalCount = if (directoryPath != null) {
                dao.getTotalCountByDirectory(directoryPath)
            } else {
                dao.getTotalCount()
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
}