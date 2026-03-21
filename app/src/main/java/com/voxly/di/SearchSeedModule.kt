package com.voxly.di

import com.voxly.presentation.viewmodel.SearchSeedHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

/**
 * Hilt Module，提供 ActivityRetainedScoped 的 SearchSeedHolder 实例。
 * 在同一 Activity 生命周期内，所有注入 SearchSeedHolder 的 ViewModel 共享同一实例。
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
object SearchSeedModule {

    @ActivityRetainedScoped
    @Provides
    fun provideSearchSeedHolder(): SearchSeedHolder = SearchSeedHolder()
}
