package com.mp3tag.android.di

import android.content.Context
import com.mp3tag.android.data.local.AudioFileScanner
import com.mp3tag.android.data.local.metadata.JaudiotaggerMetadataProcessor
import com.mp3tag.android.data.local.replaygain.ReplayGainScanner
import com.mp3tag.android.data.remote.itunes.ITunesApi
import com.mp3tag.android.data.remote.itunes.ITunesRepository
import com.mp3tag.android.data.remote.lrclib.LRCLibApi
import com.mp3tag.android.data.repository.AggregatedOnlineMetadataRepository
import com.mp3tag.android.data.remote.musicbrainz.MusicBrainzApi
import com.mp3tag.android.data.remote.musicbrainz.MusicBrainzRepository
import com.mp3tag.android.data.repository.AudioRepositoryImpl
import com.mp3tag.android.data.repository.LyricsRepositoryImpl
import com.mp3tag.android.data.repository.ReplayGainRepositoryImpl
import com.mp3tag.android.domain.repository.AudioRepository
import com.mp3tag.android.domain.repository.LyricsRepository
import com.mp3tag.android.domain.repository.OnlineMetadataRepository
import com.mp3tag.android.domain.repository.ReplayGainRepository
import com.mp3tag.android.domain.usecase.BatchAlbumArtUseCase
import com.mp3tag.android.domain.usecase.BatchEditMetadataUseCase
import com.mp3tag.android.domain.usecase.BatchReplayGainUseCase
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
import javax.inject.Named
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
    @Named("musicbrainz")
    fun provideMusicBrainzRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(MusicBrainzApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicBrainzApi(@Named("musicbrainz") retrofit: Retrofit): MusicBrainzApi {
        return retrofit.create(MusicBrainzApi::class.java)
    }

    @Provides
    @Singleton
    @Named("itunes")
    fun provideITunesRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ITunesApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideITunesApi(@Named("itunes") retrofit: Retrofit): ITunesApi {
        return retrofit.create(ITunesApi::class.java)
    }

    @Provides
    @Singleton
    @Named("lrclib")
    fun provideLRCLibRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LRCLibApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLRCLibApi(@Named("lrclib") retrofit: Retrofit): LRCLibApi {
        return retrofit.create(LRCLibApi::class.java)
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

    @Provides
    @Singleton
    fun provideReplayGainScanner(
        @ApplicationContext context: Context
    ): ReplayGainScanner {
        return ReplayGainScanner(context)
    }

    @Provides
    @Singleton
    fun provideMusicBrainzRepository(
        @ApplicationContext context: Context,
        musicBrainzApi: MusicBrainzApi
    ): MusicBrainzRepository {
        return MusicBrainzRepository(context, musicBrainzApi)
    }

    @Provides
    @Singleton
    fun provideITunesRepository(
        @ApplicationContext context: Context,
        iTunesApi: ITunesApi
    ): ITunesRepository {
        return ITunesRepository(context, iTunesApi)
    }

    @Provides
    @Singleton
    fun provideBatchEditMetadataUseCase(
        audioRepository: AudioRepository
    ): BatchEditMetadataUseCase {
        return BatchEditMetadataUseCase(audioRepository)
    }

    @Provides
    @Singleton
    fun provideBatchReplayGainUseCase(
        replayGainRepository: ReplayGainRepository
    ): BatchReplayGainUseCase {
        return BatchReplayGainUseCase(replayGainRepository)
    }

    @Provides
    @Singleton
    fun provideBatchAlbumArtUseCase(
        audioRepository: AudioRepository
    ): BatchAlbumArtUseCase {
        return BatchAlbumArtUseCase(audioRepository)
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        metadataProcessor: JaudiotaggerMetadataProcessor,
        lrclibApi: LRCLibApi
    ): LyricsRepository {
        return LyricsRepositoryImpl(context, metadataProcessor, lrclibApi)
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

    @Binds
    @Singleton
    abstract fun bindOnlineMetadataRepository(
        aggregatedRepository: AggregatedOnlineMetadataRepository
    ): OnlineMetadataRepository
}
