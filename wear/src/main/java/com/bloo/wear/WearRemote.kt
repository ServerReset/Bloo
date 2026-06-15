package com.bloo.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper

/** Opens a URL on the paired phone (e.g. the car's location in Google Maps). */
object WearRemote {
    fun openOnPhone(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(url))
            RemoteActivityHelper(context).startRemoteActivity(intent)
        }
    }

    /** Open the phone dialer pre-filled with [number] (digits only). */
    fun dialOnPhone(context: Context, number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("tel:$number"))
            RemoteActivityHelper(context).startRemoteActivity(intent)
        }
    }
}
