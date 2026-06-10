package com.bloo.bluelink.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager

/**
 * Plays a short celebratory sound for the first-run confetti moment.
 *
 * If a bundled clip exists at `res/raw/celebrate` (drop in a royalty-free
 * fireworks / party-popper file to use it), that's played; otherwise it falls
 * back to the device's default notification sound. No audio is synthesized.
 */
object Fireworks {

    fun playSound(context: Context) {
        val ctx = context.applicationContext
        runCatching {
            val resId = ctx.resources.getIdentifier("celebrate", "raw", ctx.packageName)
            if (resId != 0) {
                MediaPlayer.create(ctx, resId)?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(ctx, uri)?.play()
            }
        }
    }
}
