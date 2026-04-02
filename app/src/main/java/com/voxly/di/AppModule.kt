package com.voxly.di

import com.voxly.data.remote.NetworkConstants

import android.content.Context
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.replaygain.Ebur128ReplayGainScanner
import com.voxly.data.remote.itunes.ITunesApi
import com.voxly.data.remote.itunes.ITunesRepository

import com.voxly.data.remote.tengx.TengxApi
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.tengx.TengxRepositoryImpl
import com.voxly.data.remote.wangy.WangyApi
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.data.remote.wangy.WangyRepositoryImpl
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.data.remote.musicbrainz.MusicBrainzApi
import com.voxly.data.remote.musicbrainz.MusicBrainzRepository
import com.voxly.data.repository.AudioRepositoryImpl
import com.voxly.data.repository.LyricsRepositoryImpl
import com.voxly.data.repository.ReplayGainRepositoryImpl
import com.voxly.data.repository.RoomRecentEditsRepository
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.RecentEditsRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.usecase.BatchAlbumArtUseCase
import com.voxly.domain.usecase.BatchEditMetadataUseCase
import com.voxly.domain.usecase.BatchEngine
import com.voxly.domain.usecase.BatchReplayGainUseCase
import com.voxly.domain.usecase.MemoryPressureMonitor
import com.voxly.domain.usecase.UnifiedScanManager
import com.voxly.domain.usecase.UnifiedScanManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the application-level CoroutineScope.
     * Used for long-running operations that should outlive individual screens.
     */
    @Provides
    @Singleton
    @Named("ApplicationScope")
    fun provideApplicationCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        proxyInterceptor: ProxyInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // User-Agent interceptor for all requests
        val userAgentInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", NetworkConstants.DEFAULT_USER_AGENT)
                .build()
            chain.proceed(requestWithUserAgent)
        }

        // Cache interceptor for image responses (7 days cache)
        val cacheInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            
            // Check if this is an image response
            val contentType = response.header("Content-Type")
            if (contentType?.startsWith("image/") == true) {
                response.newBuilder()
                    .header("Cache-Control", "max-age=604800") // 7 days
                    .build()
            } else {
                response
            }
        }

        // HTTP Cache (50MB for image caching)
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 50 * 1024 * 1024)

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(proxyInterceptor)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(cacheInterceptor)
            .addInterceptor(loggingInterceptor)
            // Enable retry on connection failure
            .retryOnConnectionFailure(true)
            // Follow redirects
            .followRedirects(true)
            .followSslRedirects(true)
            // Connection pool for connection reuse
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            // DNS cache
            .dns(okhttp3.Dns.SYSTEM)
            // Protocols
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            // Timeouts
            .connectTimeout(NetworkConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @Named("musicbrainz")
    fun provideMusicBrainzRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(MusicBrainzApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
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
    fun provideITunesRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ITunesApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideITunesApi(@Named("itunes") retrofit: Retrofit): ITunesApi {
        return retrofit.create(ITunesApi::class.java)
    }



    @Provides
    @Singleton
    @Named("tengx")
    fun provideTengxRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(TengxApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideTengxApi(@Named("tengx") retrofit: Retrofit): TengxApi {
        return retrofit.create(TengxApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTengxRepository(
        tengxApi: TengxApi
    ): TengxRepository {
        return TengxRepositoryImpl(tengxApi)
    }

    @Provides
    @Singleton
    @Named("wangy")
    fun provideWangyRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(WangyApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideWangyApi(@Named("wangy") retrofit: Retrofit): WangyApi {
        return retrofit.create(WangyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWangyRepository(
        wangyApi: WangyApi
    ): WangyRepository {
        return WangyRepositoryImpl(wangyApi)
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
        iTunesApi: ITunesApi,
        settingsDataStore: SettingsDataStore
    ): ITunesRepository {
        return ITunesRepository(iTunesApi, settingsDataStore)
    }

    @Provides
    @Singleton
    fun provideMemoryPressureMonitor(
        @ApplicationContext context: Context
    ): MemoryPressureMonitor = MemoryPressureMonitor(context)

    @Provides
    @Singleton
    fun provideBatchEngine(
        memoryPressureMonitor: MemoryPressureMonitor
    ): BatchEngine<String> = BatchEngine(memoryPressureMonitor = memoryPressureMonitor)

    @Provides
    @Singleton
    fun provideBatchEditMetadataUseCase(
        audioRepository: AudioRepository,
        batchEngine: BatchEngine<String>
    ): BatchEditMetadataUseCase {
        return BatchEditMetadataUseCase(audioRepository, batchEngine)
    }

    @Provides
    @Singleton
    fun provideBatchReplayGainUseCase(
        replayGainRepository: ReplayGainRepository,
        batchEngine: BatchEngine<String>
    ): BatchReplayGainUseCase {
        return BatchReplayGainUseCase(replayGainRepository, batchEngine)
    }

    @Provides
    @Singleton
    fun provideBatchAlbumArtUseCase(
        audioRepository: AudioRepository,
        batchEngine: BatchEngine<String>
    ): BatchAlbumArtUseCase {
        return BatchAlbumArtUseCase(audioRepository, batchEngine)
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        metadataProcessor: TagLibMetadataProcessor,
        settingsDataStore: SettingsDataStore,
        wangyRepository: WangyRepository,
        tengxRepository: TengxRepository
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            metadataProcessor = metadataProcessor,
            settingsDataStore = settingsDataStore,
            wangyRepository = wangyRepository,
            tengxRepository = tengxRepository
        )
    }

    @Provides
    @Singleton
    fun provideMusicCacheDatabaseProvider(
        @ApplicationContext context: Context
    ): MusicCacheDatabaseProvider {
        return MusicCacheDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideUnifiedScanManager(
        audioFileScanner: AudioFileScanner,
        settingsDataStore: SettingsDataStore,
        @ApplicationContext context: Context,
        @Named("ApplicationScope") scope: CoroutineScope
    ): UnifiedScanManager {
        return UnifiedScanManagerImpl(audioFileScanner, settingsDataStore, scope)
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

    @Binds
    @Singleton
    abstract fun bindRecentEditsRepository(
        recentEditsRepository: RoomRecentEditsRepository
    ): RecentEditsRepository
}
