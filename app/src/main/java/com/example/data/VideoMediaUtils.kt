package com.example.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

private const val MEDIA_TAG = "VideoMediaUtils"

/**
 * Duration capability definition for the active Gemini video generation model.
 */
object VideoDurationSpec {
    const val MIN_GENERATE_SECONDS = 3
    const val MAX_GENERATE_SECONDS = 15
    const val DEFAULT_GENERATE_SECONDS = 10

    const val MIN_EXTEND_SECONDS = 3
    const val MAX_EXTEND_SECONDS = 10
    const val DEFAULT_EXTEND_SECONDS = 5
    const val MAX_CUMULATIVE_SECONDS = 30

    val ALL_UI_SECONDS: List<Int> = (1..15).toList()
    val SUPPORTED_GENERATE_SECONDS: Set<Int> = (MIN_GENERATE_SECONDS..MAX_GENERATE_SECONDS).toSet()
    val UNSUPPORTED_GENERATE_SECONDS: Set<Int> = setOf(1, 2)
    val SUPPORTED_EXTEND_SECONDS: List<Int> = (MIN_EXTEND_SECONDS..MAX_EXTEND_SECONDS).toList()

    fun isGenerateDurationSupported(seconds: Int): Boolean = seconds in SUPPORTED_GENERATE_SECONDS
    fun isExtendDurationSupported(seconds: Int): Boolean = seconds in MIN_EXTEND_SECONDS..MAX_EXTEND_SECONDS

    fun parseSecondsOrNull(raw: String): Int? {
        val clean = raw.trim().lowercase(Locale.US)
        val match = Regex("""^(\d+)\s*(?:s|sec|secs|second|seconds)?$""").matchEntire(clean) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun formatDurationLabel(seconds: Int): String =
        if (seconds == 1) "1 second" else "$seconds seconds"

    fun formatMeasuredSeconds(durationMs: Long, fallbackLabel: String = ""): String {
        if (durationMs <= 0L) return fallbackLabel.ifBlank { "0s" }
        val tenths = (durationMs / 100.0)
        val wholeSec = durationMs / 1000L
        return if (durationMs % 1000L < 150L || durationMs % 1000L > 850L) {
            "${((durationMs + 500L) / 1000L)}s"
        } else {
            String.format(Locale.US, "%.1fs", tenths / 10.0).also {
                if (wholeSec < 0) return fallbackLabel
            }
        }
    }
}

fun interface VideoDurationInspector {
    fun measureDurationMs(file: File): Long
}

class AndroidVideoDurationInspector : VideoDurationInspector {
    override fun measureDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        } catch (e: Exception) {
            Log.w(MEDIA_TAG, "Failed to inspect MP4 duration for ${file.name}: ${e.message}")
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}

fun interface Mp4Combiner {
    fun combine(firstFile: File, secondFile: File, outputFile: File): Boolean
}

/**
 * Losslessly concatenates two compatible MP4 segments (video + optional audio tracks)
 * into a single unified MP4 container using Android MediaExtractor and MediaMuxer.
 */
object AndroidMp4Combiner : Mp4Combiner {
    override fun combine(firstFile: File, secondFile: File, outputFile: File): Boolean {
        if (!firstFile.exists() || firstFile.length() == 0L || !secondFile.exists() || secondFile.length() == 0L) {
            return false
        }
        val extractor1 = MediaExtractor()
        val extractor2 = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor1.setDataSource(firstFile.absolutePath)
            extractor2.setDataSource(secondFile.absolutePath)

            val trackPair = findMatchingTracks(extractor1, extractor2) ?: return false
            val (videoTrack1, audioTrack1, videoTrack2, audioTrack2) = trackPair

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val outVideoTrack = if (videoTrack1 >= 0) {
                muxer.addTrack(extractor1.getTrackFormat(videoTrack1))
            } else {
                -1
            }
            val outAudioTrack = if (audioTrack1 >= 0) {
                muxer.addTrack(extractor1.getTrackFormat(audioTrack1))
            } else {
                -1
            }
            if (outVideoTrack < 0 && outAudioTrack < 0) return false

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            var videoOffsetUs = 0L
            if (videoTrack1 >= 0 && outVideoTrack >= 0) {
                videoOffsetUs = writeTrackSamples(extractor1, videoTrack1, muxer, outVideoTrack, 0L, buffer, bufferInfo)
            }
            var audioOffsetUs = 0L
            if (audioTrack1 >= 0 && outAudioTrack >= 0) {
                audioOffsetUs = writeTrackSamples(extractor1, audioTrack1, muxer, outAudioTrack, 0L, buffer, bufferInfo)
            }

            // Use slightly padded end timestamp as base offset for segment 2 (~33ms = 1 frame at 30fps)
            val seg2VideoBaseUs = if (videoOffsetUs > 0L) videoOffsetUs + 33_333L else 0L
            val seg2AudioBaseUs = if (audioOffsetUs > 0L) audioOffsetUs + 21_333L else seg2VideoBaseUs

            if (videoTrack2 >= 0 && outVideoTrack >= 0) {
                writeTrackSamples(extractor2, videoTrack2, muxer, outVideoTrack, seg2VideoBaseUs, buffer, bufferInfo)
            }
            if (audioTrack2 >= 0 && outAudioTrack >= 0) {
                writeTrackSamples(extractor2, audioTrack2, muxer, outAudioTrack, seg2AudioBaseUs, buffer, bufferInfo)
            }

            muxer.stop()
            outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to mux MP4 segments cleanly: ${e.message}", e)
            runCatching { outputFile.delete() }
            false
        } finally {
            runCatching { extractor1.release() }
            runCatching { extractor2.release() }
            runCatching { muxer?.release() }
        }
    }

    private data class TrackMapping(
        val video1: Int,
        val audio1: Int,
        val video2: Int,
        val audio2: Int
    )

    private fun findMatchingTracks(ex1: MediaExtractor, ex2: MediaExtractor): TrackMapping? {
        var v1 = -1
        var a1 = -1
        for (i in 0 until ex1.trackCount) {
            val mime = ex1.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (v1 < 0 && mime.startsWith("video/")) v1 = i
            if (a1 < 0 && mime.startsWith("audio/")) a1 = i
        }
        var v2 = -1
        var a2 = -1
        for (i in 0 until ex2.trackCount) {
            val mime = ex2.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (v2 < 0 && mime.startsWith("video/")) v2 = i
            if (a2 < 0 && mime.startsWith("audio/")) a2 = i
        }
        if (v1 < 0 || v2 < 0) return null
        return TrackMapping(v1, a1, v2, a2)
    }

    private fun writeTrackSamples(
        extractor: MediaExtractor,
        sourceTrack: Int,
        muxer: MediaMuxer,
        destTrack: Int,
        baseOffsetUs: Long,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ): Long {
        extractor.selectTrack(sourceTrack)
        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var maxWrittenPtsUs = baseOffsetUs
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            val flags = extractor.sampleFlags
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = baseOffsetUs + sampleTimeUs
            bufferInfo.flags = flags

            muxer.writeSampleData(destTrack, buffer, bufferInfo)
            if (bufferInfo.presentationTimeUs > maxWrittenPtsUs) {
                maxWrittenPtsUs = bufferInfo.presentationTimeUs
            }
            extractor.advance()
        }
        extractor.unselectTrack(sourceTrack)
        return maxWrittenPtsUs
    }
}
