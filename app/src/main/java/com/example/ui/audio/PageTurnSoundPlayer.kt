package com.example.ui.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesizes and plays a subtle, natural paper-page-turn rustle via AudioTrack
 * without relying on external audio files.
 */
class PageTurnSoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleRate = 22050
    private val samples: ShortArray by lazy { synthesizePaperRustle() }

    private fun synthesizePaperRustle(): ShortArray {
        val durationSec = 0.14
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        var prev = 0.0
        var prev2 = 0.0
        val rng = Random(42)

        for (i in 0 until count) {
            val t = i.toDouble() / count
            // Smooth two-peak envelope mimicking finger lift + page settle
            val attack = sin(Math.PI * t).coerceAtLeast(0.0)
            val decay = exp(-3.5 * t)
            val env = attack * (0.65 + 0.35 * decay)

            // Filtered noise for soft paper texture (band-limited)
            val white = rng.nextDouble(-1.0, 1.0)
            val smooth = 0.55 * white + 0.30 * prev + 0.15 * prev2
            val highPass = smooth - 0.65 * prev
            prev2 = prev
            prev = smooth

            val value = (highPass * env * 0.24 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = value.toShort()
        }
        return out
    }

    fun playPageTurn(muted: Boolean) {
        if (muted) return
        scope.launch {
            try {
                val bufferBytes = samples.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.setNotificationMarkerPosition(samples.size)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        try {
                            track?.stop()
                            track?.release()
                        } catch (_: Exception) {
                        }
                    }

                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                track.play()
            } catch (_: Exception) {
                // Ignore audio hardware unavailability on headless/muted devices
            }
        }
    }
}
