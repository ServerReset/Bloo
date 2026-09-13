package com.bloo.bluelink.widget

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * The "Copy error" button's click target on the widget's own crash screen
 * (car_widget_error.xml, wired up in CarWidget.onCompositionError). Plain
 * BroadcastReceiver + PendingIntent extra, not a Glance action: composition
 * itself just failed, so this whole screen is rendered via ordinary
 * RemoteViews, not Glance, and this is the matching ordinary-Android way to
 * give one of its buttons something to do. Really basic on purpose -- the
 * whole point is a way to get the exact exception text off the device with
 * no adb, no logcat, nothing else in the path that could itself be broken.
 */
class WidgetErrorCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Bloo widget error", text))
    }

    companion object {
        const val EXTRA_TEXT = "bloo.widget.error_text"
    }
}
