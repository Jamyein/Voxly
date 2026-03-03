package com.voxly.di

import android.content.Context
import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proxy interceptor that applies proxy settings from DataStore.
 * Supports three modes: disabled, custom proxy, and system proxy.
 *
 * Note: Proxy settings are cached in memory for performance.
 * Call [refreshProxySettings] to update cache after settings change.
 */
@Singleton
class ProxyInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache for proxy settings
    @Volatile private var cachedProxy: Proxy? = null
    @Volatile private var cacheValid = false

    // System proxy lookup results (cached)
    @Volatile private var systemProxyCache: Proxy? = null

    init {
        // Initialize proxy settings on first use
        refreshProxySettings()
    }

    /**
     * Refresh proxy settings from DataStore.
     * Call this after proxy settings are changed.
     */
    fun refreshProxySettings() {
        scope.launch {
            try {
                val settingsDataStore = SettingsDataStore(context)
                cachedProxy = loadProxyFromSettings(settingsDataStore)
                cacheValid = true
                Logger.d(TAG, "Proxy settings refreshed: $cachedProxy")
            } catch (e: Exception) {
                Logger.e("Failed to refresh proxy settings", e, TAG)
                cachedProxy = Proxy.NO_PROXY
                cacheValid = true
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Wait for cache to be valid (with timeout)
        if (!cacheValid) {
            refreshProxySettings()
            // Small delay to allow initial load (rare case)
            Thread.sleep(50)
        }

        // Use cached proxy
        val proxy = cachedProxy ?: Proxy.NO_PROXY

        // If no proxy or proxy disabled, proceed without proxy
        if (proxy == Proxy.NO_PROXY) {
            return chain.proceed(originalRequest)
        }

        // Create a new client with the proxy for this request
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .build()

        // Re-build the request with the same attributes
        val newRequest = originalRequest.newBuilder()
            .url(originalRequest.url)
            .headers(originalRequest.headers)
            .build()

        return client.newCall(newRequest).execute()
    }

    private suspend fun loadProxyFromSettings(settingsDataStore: SettingsDataStore): Proxy {
        val proxyEnabled = settingsDataStore.proxyEnabled.first()
        if (!proxyEnabled) {
            return Proxy.NO_PROXY
        }

        val proxyType = settingsDataStore.proxyType.first()

        // System proxy - use Android's default proxy settings
        if (proxyType == "SYSTEM") {
            return getSystemProxy()
        }

        val proxyHost = settingsDataStore.proxyHost.first()
        val proxyPort = settingsDataStore.proxyPort.first()

        if (proxyHost.isBlank() || proxyPort <= 0) {
            return Proxy.NO_PROXY
        }

        val proxyTypeEnum = when (proxyType) {
            "SOCKS" -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }

        return Proxy(proxyTypeEnum, java.net.InetSocketAddress(proxyHost, proxyPort))
    }

    private fun getSystemProxy(): Proxy {
        // Check cache first
        systemProxyCache?.let { if (it != Proxy.NO_PROXY) return it }

        try {
            // Get system proxy from Android's ProxySelector
            val systemProxy = java.net.ProxySelector.getDefault()
                ?.select(java.net.URI("http://${java.net.InetAddress.getLocalHost().hostName}"))
                ?.firstOrNull { it != Proxy.NO_PROXY }

            if (systemProxy != null && systemProxy != Proxy.NO_PROXY) {
                systemProxyCache = systemProxy
                return systemProxy
            }

            // Fallback: try to get from system properties (VPN apps often set these)
            val httpProxyHost = System.getProperty("http.proxyHost")
            val httpProxyPort = System.getProperty("http.proxyPort")

            if (!httpProxyHost.isNullOrBlank()) {
                val port = httpProxyPort?.toIntOrNull() ?: 8080
                val proxy = Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress(httpProxyHost, port))
                systemProxyCache = proxy
                return proxy
            }

            // Try HTTPS proxy as fallback
            val httpsProxyHost = System.getProperty("https.proxyHost")
            val httpsProxyPort = System.getProperty("https.proxyPort")

            if (!httpsProxyHost.isNullOrBlank()) {
                val port = httpsProxyPort?.toIntOrNull() ?: 8080
                val proxy = Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress(httpsProxyHost, port))
                systemProxyCache = proxy
                return proxy
            }
        } catch (e: Exception) {
            Logger.e("Failed to get system proxy", e, TAG)
        }

        return Proxy.NO_PROXY
    }

    companion object {
        private const val TAG = "ProxyInterceptor"
    }
}
