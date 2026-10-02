package com.bloo.bluelink.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * The deck's little sounds, synthesized by the platform tone generator so there are no assets to
 * ship. They ride the notification stream, so silent mode and the notification volume silence them
 * like any other notification sound.
 */
internal object OnboardingSounds {
    // Building a ToneGenerator blocks for tens of ms, so it never happens on the UI thread: that
    // stall landed on every card settle and was a visible hitch in the swipe.
    private val worker by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

    private fun play(tone: Int, ms: Int, volume: Int) {
        worker.execute {
            runCatching {
                val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
                generator.startTone(tone, ms)
                Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, ms + 150L)
            }
        }
    }

    /** A tiny blip as a card settles. */
    fun blip() = play(ToneGenerator.TONE_DTMF_D, 40, 35)

    /** A happy two-note ding: something got done. */
    fun ding() = play(ToneGenerator.TONE_PROP_ACK, 180, 70)
}
