package com.example.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class LiveAudioPlayer(
    private val sampleRate: Int = 24000,
    private val onAmplitudeChanged: ((Float) -> Unit)? = null
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val lock = Any()

    companion object {
        private const val TAG = "LiveAudioPlayer"
    }

    fun start() {
        synchronized(lock) {
            if (audioTrack != null) return

            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, sampleRate * 2)

                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                audioTrack?.play()
                isPlaying = true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing LiveAudioPlayer AudioTrack", e)
            }
        }
    }

    fun playChunk(pcmBytes: ByteArray) {
        synchronized(lock) {
            if (!isPlaying || audioTrack == null) {
                start()
            }
            try {
                audioTrack?.write(pcmBytes, 0, pcmBytes.size)
                val amp = calculateRms(pcmBytes)
                onAmplitudeChanged?.invoke(amp)
            } catch (e: Exception) {
                Log.w(TAG, "Error writing PCM chunk to AudioTrack", e)
            }
        }
    }

    /**
     * Immediate Barge-in / Interruption:
     * Halts playback, flushes buffered audio, and resets stream.
     */
    fun stopAndFlush() {
        synchronized(lock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                onAmplitudeChanged?.invoke(0f)
            } catch (e: Exception) {
                Log.w(TAG, "Error flushing AudioTrack on barge-in", e)
            }
        }
    }

    fun release() {
        synchronized(lock) {
            isPlaying = false
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            } finally {
                audioTrack = null
                onAmplitudeChanged?.invoke(0f)
            }
        }
    }

    private fun calculateRms(pcmBytes: ByteArray): Float {
        var sum = 0.0
        val sampleCount = pcmBytes.size / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until sampleCount) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / sampleCount)
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }
}
