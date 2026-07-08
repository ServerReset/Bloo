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
            val future = RemoteActivityHelper(context).startRemoteActivity(intent)
            // startRemoteActivity resolves asynchronously; ignoring the future made
            // a tap silently do nothing when the phone was unreachable. Surface it
            // as a watch notification, the same way standalone command failures do.
            future.addListener({
                runCatching { future.get() }.onFailure {
                    WearNotifications.post(
                        context,
                        ("open$url").hashCode(),
                        "Couldn't open on phone",
                        "Bring your phone nearby and try again.",
                    )
                }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        }
    }

    /** Open the phone dialer pre-filled with [number] (digits only). */
    fun dialOnPhone(context: Context, number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("tel:$number"))
            val future = RemoteActivityHelper(context).startRemoteActivity(intent)
            future.addListener({
                runCatching { future.get() }.onFailure {
                    WearNotifications.post(
                        context,
                        ("dial$number").hashCode(),
                        "Couldn't open dialer on phone",
                        "Bring your phone nearby and try again.",
                    )
                }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        }
    }
}
