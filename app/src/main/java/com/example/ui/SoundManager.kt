package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class SoundManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var isMuted = false

    fun toggleMute() {
        isMuted = !isMuted
    }

    fun getMuted(): Boolean = isMuted

    private fun playTone(
        startFreq: Float,
        endFreq: Float,
        durationMs: Int,
        type: ToneType = ToneType.SINE
    ) {
        if (isMuted) return
        scope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val freq = startFreq + (endFreq - startFreq) * progress
                    val t = i.toFloat() / sampleRate
                    
                    val angle = 2.0 * Math.PI * freq * t
                    val sample = when (type) {
                        ToneType.SINE -> sin(angle)
                        ToneType.SQUARE -> if (sin(angle) >= 0) 1.0 else -1.0
                        ToneType.NOISE -> Math.random() * 2.0 - 1.0
                    }
                    
                    // Apply amplitude envelope to avoid clicks (fade out)
                    val envelope = if (progress > 0.8f) (1.0f - progress) / 0.2f else 1.0f
                    buffer[i] = (sample * 8000 * envelope).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
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
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                
                // Release audio track resources when done
                scope.launch {
                    kotlinx.coroutines.delay(durationMs + 100L)
                    try {
                        audioTrack.stop()
                    } catch (e: Exception) {}
                    try {
                        audioTrack.release()
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playShoot() {
        playTone(900f, 200f, 120, ToneType.SINE)
    }

    fun playExplosion() {
        playTone(180f, 40f, 300, ToneType.NOISE)
    }

    fun playHit() {
        playTone(600f, 400f, 60, ToneType.SQUARE)
    }

    fun playPowerUp() {
        playTone(300f, 1200f, 250, ToneType.SINE)
    }

    enum class ToneType {
        SINE, SQUARE, NOISE
    }
}
