package com.voxly.di

import android.content.Context
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proxy interceptor that applies proxy settings from DataStore.
 * Supports three modes: disabled, custom proxy, and system proxy.
 */
@Singleton
class ProxyInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Get proxy settings synchronously (blocking)
        val proxy = getProxy()
        
        // If no proxy or proxy disabled, proceed without proxy
        if (proxy == Proxy.NO_PROXY || proxy == null) {
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
    
    private fun getProxy(): Proxy? {
        return try {
            runBlocking {
                val settingsDataStore = SettingsDataStore(context)
                val proxyEnabled = settingsDataStore.proxyEnabled.first()
                if (!proxyEnabled) {
                    return@runBlocking Proxy.NO_PROXY
                }
                
                val proxyType = settingsDataStore.proxyType.first()
                
                // System proxy - use Android's default proxy settings
                if (proxyType == "SYSTEM") {
                    // Get system proxy from Android's ProxySelector
                    val systemProxy = java.net.ProxySelector.getDefault()
                        ?.select(java.net.URI("http://${java.net.InetAddress.getLocalHost().hostName}"))
                        ?.firstOrNull { it != Proxy.NO_PROXY }
                    
                    // Fallback: try to get from system properties (VPN apps often set these)
                    if (systemProxy == null || systemProxy == Proxy.NO_PROXY) {
                        val httpProxyHost = System.getProperty("http.proxyHost")
                        val httpProxyPort = System.getProperty("http.proxyPort")
                        
                        if (!httpProxyHost.isNullOrBlank()) {
                            val port = httpProxyPort?.toIntOrNull() ?: 8080
                            return@runBlocking Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress(httpProxyHost, port))
                        }
                        
                        // Try HTTPS proxy as fallback
                        val httpsProxyHost = System.getProperty("https.proxyHost")
                        val httpsProxyPort = System.getProperty("https.proxyPort")
                        
                        if (!httpsProxyHost.isNullOrBlank()) {
                            val port = httpsProxyPort?.toIntOrNull() ?: 8080
                            return@runBlocking Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress(httpsProxyHost, port))
                        }
                        
                        return@runBlocking Proxy.NO_PROXY
                    }
                    
                    return@runBlocking systemProxy
                }
                
                val proxyHost = settingsDataStore.proxyHost.first()
                val proxyPort = settingsDataStore.proxyPort.first()
                
                if (proxyHost.isBlank() || proxyPort <= 0) {
                    return@runBlocking Proxy.NO_PROXY
                }
                
                val proxyTypeEnum = when (proxyType) {
                    "SOCKS" -> Proxy.Type.SOCKS
                    else -> Proxy.Type.HTTP
                }
                
                java.net.Proxy(proxyTypeEnum, java.net.InetSocketAddress(proxyHost, proxyPort))
            }
        } catch (e: Exception) {
            // If anything goes wrong, don't use proxy
            return Proxy.NO_PROXY
        }
    }
}
