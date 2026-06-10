package com.bloo.bluelink.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A synthesized fireworks sound — a low launch/boom followed by scattered
 * crackles — generated as PCM at runtime so we don't ship an audio asset.
 */
object Fireworks {

    fun playSound() {
        thread(isDaemon = true) {
            runCatching {
                val sr = 44100
                val seconds = 1.8
                val n = (sr * seconds).toInt()
                val mix = FloatArray(n)

                // Low boom near the start: a ~70 Hz tone with a fast exponential decay.
                for (i in 0 until n) {
                    val t = i.toFloat() / sr
                    if (t < 0.6f) {
                        val env = exp((-t * 7.0)).toFloat()
                        mix[i] += (sin(2.0 * Math.PI * 68.0 * t).toFloat()) * env * 0.55f
                    }
                }

                // Crackles: many short decaying noise pops scattered after the boom.
                val rnd = Random(System.nanoTime())
                repeat(60) {
                    val start = (rnd.nextDouble(0.25, 1.6) * sr).toInt()
                    val len = (rnd.nextDouble(0.01, 0.05) * sr).toInt()
                    val amp = rnd.nextDouble(0.15, 0.5).toFloat()
                    var last = 0f
                    for (j in 0 until len) {
                        val idx = start + j
                        if (idx >= n) break
                        val env = exp((-j.toDouble() / len * 5.0)).toFloat()
                        // Lightly low-passed white noise = a crisp "tick/crackle".
                        val white = rnd.nextFloat() * 2f - 1f
                        last = last * 0.5f + white * 0.5f
                        mix[idx] += last * env * amp
                    }
                }

                // Normalise and convert to 16-bit PCM.
                var peak = 1e-4f
                for (v in mix) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
                val gain = 0.9f / peak
                val pcm = ShortArray(n) { (mix[it] * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
            }
        }
    }
}
