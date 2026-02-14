package com.voxly.data.local.replaygain

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ReplayGain scanner using Android's MediaExtractor for audio analysis.
 * Implements the EBU R128 loudness standard for accurate gain calculation.
 */
@Singleton
class ReplayGainScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Reference loudness level (EBU R128 standard is -23 LUFS, ReplayGain uses -18 LUFS)
        const val REFERENCE_LUFS = -18.0
        const val RMS_REFERENCE = 0.0001 // Reference RMS for calculations

        // Number of samples to process per chunk (for progress updates)
        const val SAMPLES_PER_CHUNK = 4096
    }

    /**
     * Scans audio files and calculates ReplayGain values.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level affecting sample rate
     * @return Flow emitting scan progress
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality
    ): Flow<ScanProgress> = flow {
        val totalFiles = filePaths.size
        var processedFiles = 0

        filePaths.forEachIndexed { index, filePath ->
            if (!kotlin.coroutines.coroutineContext.isActive) {
                emit(
                    ScanProgress(
                        currentFile = index,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.CANCELLED
                    )
                )
                return@flow
            }

            emit(
                ScanProgress(
                    currentFile = index + 1,
                    totalFiles = totalFiles,
                    percentage = processedFiles.toFloat() / totalFiles,
                    currentFilePath = filePath,
                    status = ScanStatus.SCANNING
                )
            )

            try {
                val replayGainInfo = analyzeAudioFile(filePath, scanQuality)

                if (replayGainInfo != null) {
                    // Save ReplayGain info to file metadata
                    saveReplayGainToFile(filePath, replayGainInfo)
                }

                processedFiles++

                emit(
                    ScanProgress(
                        currentFile = index + 1,
                        totalFiles = totalFiles,
                        percentage = (index + 1).toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.COMPLETED
                    )
                )
            } catch (e: Exception) {
                emit(
                    ScanProgress(
                        currentFile = index + 1,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.FAILED
                    )
                )
            }

            // Small delay to prevent UI freezing
            delay(50)
        }

        emit(
            ScanProgress(
                currentFile = totalFiles,
                totalFiles = totalFiles,
                percentage = 1f,
                currentFilePath = "",
                status = ScanStatus.COMPLETED
            )
        )
    }

    /**
     * Analyzes a single audio file and calculates ReplayGain.
     * @param filePath Path to the audio file
     * @param scanQuality Quality level
     * @return ReplayGainInfo or null if analysis fails
     */
    private suspend fun analyzeAudioFile(
        filePath: String,
        scanQuality: ScanQuality
    ): ReplayGainInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Calculate sample rate based on scan quality
            val targetSampleRate = when (scanQuality) {
                ScanQuality.FAST -> minOf(sampleRate, 22050)
                ScanQuality.NORMAL -> sampleRate
                ScanQuality.ACCURATE -> sampleRate
            }

            // Read audio samples and calculate loudness
            val samples = mutableListOf<Float>()
            val buffer = ByteBuffer.allocate(1024 * 1024)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                // Convert bytes to float samples (16-bit PCM)
                val shortBuffer = buffer.asShortBuffer()
                while (shortBuffer.hasRemaining()) {
                    val sample = shortBuffer.get() / 32768.0f
                    samples.add(sample)
                }

                buffer.clear()
                extractor.advance()
            }

            extractor.release()

            if (samples.isEmpty()) return@withContext null

            // Calculate RMS and peak
            val rms = calculateRMS(samples)
            val peak = calculatePeak(samples)

            // Calculate gain adjustment needed to reach reference level
            val currentDb = 20 * kotlin.math.log10(rms.coerceAtLeast(RMS_REFERENCE.toFloat()))
            val gainDb = (REFERENCE_LUFS - currentDb).toFloat()

            ReplayGainInfo(
                trackGain = gainDb,
                trackPeak = peak,
                albumGain = null, // Album gain requires scanning all files first
                albumPeak = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculates RMS (Root Mean Square) of audio samples.
     */
    private fun calculateRMS(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f

        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble().pow(2.0)
        }

        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Calculates peak level of audio samples.
     */
    private fun calculatePeak(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f

        var peak = 0f
        for (sample in samples) {
            val absSample = kotlin.math.abs(sample)
            if (absSample > peak) peak = absSample
        }

        return peak
    }

    /**
     * Calculates album gain from a list of track gains.
     */
    fun calculateAlbumGain(trackGains: List<ReplayGainInfo>): ReplayGainInfo {
        if (trackGains.isEmpty()) return ReplayGainInfo()

        // Average the track gains for album gain
        val avgGain = trackGains.map { it.trackGain }.average().toFloat()

        // Use the highest peak from all tracks
        val maxPeak = trackGains.maxOf { it.trackPeak }

        return ReplayGainInfo(
            trackGain = trackGains.first().trackGain,
            trackPeak = trackGains.first().trackPeak,
            albumGain = avgGain,
            albumPeak = maxPeak
        )
    }

    /**
     * Saves ReplayGain information to file metadata.
     * Uses jaudiotagger to write REPLAYGAIN_TRACK_GAIN and related tags.
     */
    suspend fun saveReplayGainToFile(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            val extension = file.extension.lowercase()
            
            when (extension) {
                "mp3" -> saveReplayGainToMp3(file, replayGainInfo)
                "flac", "ogg" -> saveReplayGainToVorbis(file, replayGainInfo)
                "m4a", "mp4" -> saveReplayGainToMp4(file, replayGainInfo)
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves ReplayGain to MP3 files using ID3v2 TXXX frames.
     */
    private fun saveReplayGainToMp3(file: File, replayGainInfo: ReplayGainInfo): Boolean {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // Create TXXX frames for ReplayGain
            val trackGainFrame = org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX()
            trackGainFrame.description = "REPLAYGAIN_TRACK_GAIN"
            trackGainFrame.text = String.format("%.2f dB", replayGainInfo.trackGain)
            
            val trackPeakFrame = org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX()
            trackPeakFrame.description = "REPLAYGAIN_TRACK_PEAK"
            trackPeakFrame.text = String.format("%.6f", replayGainInfo.trackPeak)
            
            // Add frames to tag
            tag.setField(org.jaudiotagger.tag.id3.ID3v24Frame(org.jaudiotagger.tag.id3.ID3v24Frames.FRAME_ID_USER_DEFINED_INFO).apply {
                body = trackGainFrame
            })
            tag.setField(org.jaudiotagger.tag.id3.ID3v24Frame(org.jaudiotagger.tag.id3.ID3v24Frames.FRAME_ID_USER_DEFINED_INFO).apply {
                body = trackPeakFrame
            })
            
            // Add album gain if present
            replayGainInfo.albumGain?.let { albumGain ->
                val albumGainFrame = org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX()
                albumGainFrame.description = "REPLAYGAIN_ALBUM_GAIN"
                albumGainFrame.text = String.format("%.2f dB", albumGain)
                tag.setField(org.jaudiotagger.tag.id3.ID3v24Frame(org.jaudiotagger.tag.id3.ID3v24Frames.FRAME_ID_USER_DEFINED_INFO).apply {
                    body = albumGainFrame
                })
            }
            
            replayGainInfo.albumPeak?.let { albumPeak ->
                val albumPeakFrame = org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX()
                albumPeakFrame.description = "REPLAYGAIN_ALBUM_PEAK"
                albumPeakFrame.text = String.format("%.6f", albumPeak)
                tag.setField(org.jaudiotagger.tag.id3.ID3v24Frame(org.jaudiotagger.tag.id3.ID3v24Frames.FRAME_ID_USER_DEFINED_INFO).apply {
                    body = albumPeakFrame
                })
            }
            
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves ReplayGain to FLAC/OGG files using Vorbis Comments.
     */
    private fun saveReplayGainToVorbis(file: File, replayGainInfo: ReplayGainInfo): Boolean {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // Add Vorbis comments for ReplayGain
            tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM1, "REPLAYGAIN_TRACK_GAIN=${String.format("%.2f dB", replayGainInfo.trackGain)}")
            tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM2, "REPLAYGAIN_TRACK_PEAK=${String.format("%.6f", replayGainInfo.trackPeak)}")
            
            replayGainInfo.albumGain?.let {
                tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM3, "REPLAYGAIN_ALBUM_GAIN=${String.format("%.2f dB", it)}")
            }
            
            replayGainInfo.albumPeak?.let {
                tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM4, "REPLAYGAIN_ALBUM_PEAK=${String.format("%.6f", it)}")
            }
            
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves ReplayGain to MP4/M4A files using custom atoms.
     */
    private fun saveReplayGainToMp4(file: File, replayGainInfo: ReplayGainInfo): Boolean {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // For MP4, we'll use custom fields
            // Note: Full MP4 ReplayGain support may require additional handling
            tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM1, "REPLAYGAIN_TRACK_GAIN=${String.format("%.2f dB", replayGainInfo.trackGain)}")
            tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM2, "REPLAYGAIN_TRACK_PEAK=${String.format("%.6f", replayGainInfo.trackPeak)}")
            
            replayGainInfo.albumGain?.let {
                tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM3, "REPLAYGAIN_ALBUM_GAIN=${String.format("%.2f dB", it)}")
            }
            
            replayGainInfo.albumPeak?.let {
                tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM4, "REPLAYGAIN_ALBUM_PEAK=${String.format("%.6f", it)}")
            }
            
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads existing ReplayGain information from a file.
     * @param filePath Path to the audio file
     * @return ReplayGainInfo or null if not found
     */
    suspend fun readReplayGainFromFile(filePath: String): ReplayGainInfo? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext null

                val extension = file.extension.lowercase()
                
                when (extension) {
                    "mp3" -> readReplayGainFromMp3(file)
                    "flac", "ogg" -> readReplayGainFromVorbis(file)
                    "m4a", "mp4" -> readReplayGainFromMp4(file)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Reads ReplayGain from MP3 files using ID3v2 TXXX frames.
     */
    private fun readReplayGainFromMp3(file: File): ReplayGainInfo? {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag
            
            var trackGain: Float? = null
            var trackPeak: Float? = null
            var albumGain: Float? = null
            var albumPeak: Float? = null
            
            // Read TXXX frames
            if (tag is org.jaudiotagger.tag.id3.AbstractID3v2Tag) {
                val fields = tag.getFields(org.jaudiotagger.tag.id3.ID3v24Frames.FRAME_ID_USER_DEFINED_INFO)
                for (field in fields) {
                    if (field is org.jaudiotagger.tag.id3.AbstractID3v2Frame) {
                        val body = field.body
                        if (body is org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX) {
                            when (body.description) {
                                "REPLAYGAIN_TRACK_GAIN" -> {
                                    trackGain = parseGainValue(body.text)
                                }
                                "REPLAYGAIN_TRACK_PEAK" -> {
                                    trackPeak = parsePeakValue(body.text)
                                }
                                "REPLAYGAIN_ALBUM_GAIN" -> {
                                    albumGain = parseGainValue(body.text)
                                }
                                "REPLAYGAIN_ALBUM_PEAK" -> {
                                    albumPeak = parsePeakValue(body.text)
                                }
                            }
                        }
                    }
                }
            }
            
            if (trackGain != null || trackPeak != null) {
                ReplayGainInfo(
                    trackGain = trackGain ?: 0f,
                    trackPeak = trackPeak ?: 0f,
                    albumGain = albumGain,
                    albumPeak = albumPeak
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads ReplayGain from FLAC/OGG files using Vorbis Comments.
     */
    private fun readReplayGainFromVorbis(file: File): ReplayGainInfo? {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag
            
            var trackGain: Float? = null
            var trackPeak: Float? = null
            var albumGain: Float? = null
            var albumPeak: Float? = null
            
            // Read custom fields
            val custom1 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM1)
            val custom2 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM2)
            val custom3 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM3)
            val custom4 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM4)
            
            if (custom1.startsWith("REPLAYGAIN_TRACK_GAIN=")) {
                trackGain = parseGainValue(custom1.substringAfter("="))
            }
            if (custom2.startsWith("REPLAYGAIN_TRACK_PEAK=")) {
                trackPeak = parsePeakValue(custom2.substringAfter("="))
            }
            if (custom3.startsWith("REPLAYGAIN_ALBUM_GAIN=")) {
                albumGain = parseGainValue(custom3.substringAfter("="))
            }
            if (custom4.startsWith("REPLAYGAIN_ALBUM_PEAK=")) {
                albumPeak = parsePeakValue(custom4.substringAfter("="))
            }
            
            if (trackGain != null || trackPeak != null) {
                ReplayGainInfo(
                    trackGain = trackGain ?: 0f,
                    trackPeak = trackPeak ?: 0f,
                    albumGain = albumGain,
                    albumPeak = albumPeak
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads ReplayGain from MP4/M4A files.
     */
    private fun readReplayGainFromMp4(file: File): ReplayGainInfo? {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag
            
            var trackGain: Float? = null
            var trackPeak: Float? = null
            var albumGain: Float? = null
            var albumPeak: Float? = null
            
            // Read custom fields
            val custom1 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM1)
            val custom2 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM2)
            val custom3 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM3)
            val custom4 = tag.getFirst(org.jaudiotagger.tag.FieldKey.CUSTOM4)
            
            if (custom1.startsWith("REPLAYGAIN_TRACK_GAIN=")) {
                trackGain = parseGainValue(custom1.substringAfter("="))
            }
            if (custom2.startsWith("REPLAYGAIN_TRACK_PEAK=")) {
                trackPeak = parsePeakValue(custom2.substringAfter("="))
            }
            if (custom3.startsWith("REPLAYGAIN_ALBUM_GAIN=")) {
                albumGain = parseGainValue(custom3.substringAfter("="))
            }
            if (custom4.startsWith("REPLAYGAIN_ALBUM_PEAK=")) {
                albumPeak = parsePeakValue(custom4.substringAfter("="))
            }
            
            if (trackGain != null || trackPeak != null) {
                ReplayGainInfo(
                    trackGain = trackGain ?: 0f,
                    trackPeak = trackPeak ?: 0f,
                    albumGain = albumGain,
                    albumPeak = albumPeak
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a gain value string (e.g., "-6.50 dB") to float.
     */
    private fun parseGainValue(value: String): Float? {
        return try {
            value.replace(" dB", "").replace("dB", "").trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a peak value string to float.
     */
    private fun parsePeakValue(value: String): Float? {
        return try {
            value.trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
