package com.bloo.bluelink.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Create a notification channel once, idempotently — the one copy of an idiom that was
 * hand-written three times across :app and :wear (the phone's alerts and live-charge
 * channels, and the watch's alerts channel).
 *
 * Each copy did the same three things: guard on API O (channels don't exist below it),
 * read the channel back by id and only create it when missing, then create it with a
 * name/importance/description. Re-declaring an existing channel would clobber none of the
 * user's own per-channel settings — Android ignores the importance/name on re-create — but
 * the existence check still avoids the wasted call, and, more importantly, one definition
 * means a future tweak to how channels are declared lands once rather than in three places
 * that had already started to diverge (one guarded with `if (SDK < O) return`, the others
 * with `if (SDK >= O) { ... }`; identical in effect, but the kind of drift that hides a real
 * difference later).
 *
 * Lives in :shared because it is plain framework code (no Compose/Glance/Material), and
 * :shared is an `android.library` that already reaches `android.content.Context` and
 * `android.app.*` from its stores — so both consumers can call it.
 *
 * @param showBadge whether the channel shows a launcher badge; the two alert channels rely
 *   on the platform default (true), the live-charge channel passes false.
 */
fun ensureNotificationChannel(
    context: Context,
    id: String,
    name: String,
    importance: Int,
    description: String,
    showBadge: Boolean = true,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(id) != null) return
    manager.createNotificationChannel(
        NotificationChannel(id, name, importance).apply {
            this.description = description
            setShowBadge(showBadge)
        },
    )
}
