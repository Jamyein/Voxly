package com.voxly.di

import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttp 5.4 [EventListener] that records per-call latency and DNS/connect/TLS timings, and
 * forwards the metrics to Timber (which the project's [com.voxly.core.util.FileLoggingTree] picks
 * up automatically so they end up in the user-exported log zip).
 *
 * Two intentional constraints:
 * - This is observational only. It never blocks the call, never rethrows, and never writes to
 *   the OkHttp pipeline. A misbehaving logger cannot break networking.
 * - The [Factory] returns a fresh listener per [Call] so listeners can be garbage-collected when
 *   the call ends, even though the [callStartNanos] map is shared across listeners for the
 *   duration of a single [com.voxly.di.AppModule.provideOkHttpClient] instance.
 *
 * Tag is fixed at `OkHttp.Metrics` so the LogViewerScreen filter can scope to it.
 */
class OkHttpMetrics private constructor() : EventListener() {

    private val callStartNanos = ConcurrentHashMap<Call, Long>()

    override fun callStart(call: Call) {
        callStartNanos[call] = System.nanoTime()
    }

    override fun callEnd(call: Call) {
        logCompletion(call, success = true, throwable = null)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        logCompletion(call, success = false, throwable = ioe)
    }

    override fun canceled(call: Call) {
        // Treat cancellation as a failure path so the metrics log still records the duration.
        logCompletion(call, success = false, throwable = null, canceled = true)
    }

    private fun logCompletion(
        call: Call,
        success: Boolean,
        throwable: Throwable?,
        canceled: Boolean = false,
    ) {
        val startNanos = callStartNanos.remove(call) ?: return
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000L

        val url = redactUrl(call.request().url)
        val outcome = when {
            canceled -> "CANCELED"
            success -> "OK"
            else -> "FAIL"
        }
        val message = buildString {
            append(outcome).append(' ').append(durationMs).append("ms ").append(url)
            throwable?.let { append(" err=").append(it.javaClass.simpleName) }
        }

        // Use Timber directly; FileLoggingTree will persist this when file logging is enabled.
        // The DEBUG/INFO branch is filtered out in release by FileLoggingTree itself.
        val priority = if (success) Log.INFO else Log.WARN
        Timber.tag(TAG).log(priority, message)

        if (throwable != null) {
            Timber.tag(TAG).w(throwable, "OkHttp call failed: %s", url)
        }
    }

    /**
     * Strip the query string so we don't leak the proxy auth signature / token to the log.
     * The path is preserved for debugging; only the query (and fragment) is dropped.
     */
    private fun redactUrl(url: HttpUrl): String =
        url.newBuilder().query(null).fragment(null).build().toString()

    companion object {
        private const val TAG = "OkHttp.Metrics"

        /**
         * Factory used by [okhttp3.OkHttpClient.Builder.eventListenerFactory]. Returns a fresh
         * [OkHttpMetrics] per call; the per-call instance holds no state, only the shared
         * [callStartNanos] entry. Safe to use from any thread OkHttp runs on (dispatcher,
         * connection pool, application interceptor).
         */
        val Factory: EventListener.Factory = EventListener.Factory { OkHttpMetrics() }
    }
}
