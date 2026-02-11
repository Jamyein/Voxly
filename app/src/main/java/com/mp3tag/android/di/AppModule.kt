package com.mp3tag.android.di

import android.content.Context
import com.mp3tag.android.data.local.AudioFileScanner
import com.mp3tag.android.data.local.metadata.JaudiotaggerMetadataProcessor
import com.mp3tag.android.data.remote.musicbrainz.MusicBrainzApi
import com.mp3tag.android.data.repository.AudioRepositoryImpl
import com.mp3tag.android.data.repository.ReplayGainRepositoryImpl
import com.mp3tag.android.domain.repository.AudioRepository
import com.mp3tag.android.domain.repository.ReplayGainRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(MusicBrainzApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicBrainzApi(retrofit: Retrofit): MusicBrainzApi {
        return retrofit.create(MusicBrainzApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAudioFileScanner(
        @ApplicationContext context: Context
    ): AudioFileScanner {
        return AudioFileScanner(context)
    }

    @Provides
    @Singleton
    fun provideJaudiotaggerMetadataProcessor(
        @ApplicationContext context: Context
    ): JaudiotaggerMetadataProcessor {
        return JaudiotaggerMetadataProcessor(context)
    }
}

/**
 * Hilt module binding repository interfaces to implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAudioRepository(
        audioRepositoryImpl: AudioRepositoryImpl
    ): AudioRepository

    @Binds
    @Singleton
    abstract fun bindReplayGainRepository(
        replayGainRepositoryImpl: ReplayGainRepositoryImpl
    ): ReplayGainRepository
}
