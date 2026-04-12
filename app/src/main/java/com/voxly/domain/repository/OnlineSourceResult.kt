package com.voxly.domain.repository

/**
 * Streaming search result with payload and source marker.
 * Used by Flow-based search methods to emit results incrementally.
 */
public sealed class OnlineSourceResult {
    /**
     * A release result from a metadata source.
     */
    public data class ReleaseResult(
        val release: OnlineRelease,
        val source: OnlineSource
    ) : OnlineSourceResult()

    /**
     * A recording (track) result from a metadata source.
     */
    public data class RecordingResult(
        val recording: OnlineRecording,
        val source: OnlineSource
    ) : OnlineSourceResult()

    /**
     * Indicates that a particular source has completed sending results.
     */
    public data class SourceCompleted(val source: OnlineSource) : OnlineSourceResult()

    /**
     * An error from a particular source.
     */
    public data class Error(val source: OnlineSource, val message: String) : OnlineSourceResult()
}
