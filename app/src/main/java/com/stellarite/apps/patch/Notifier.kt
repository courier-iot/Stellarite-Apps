package com.stellarite.apps.patch

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Notifications for background patching. */
object Notifier {
    private const val CHANNEL = "auto-patch"

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, title: String, text: String, tap: PendingIntent?) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Auto-Patch")
                .build()
        )
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked; nothing else to do.
        }
    }

    fun confirm(ctx: Context, label: String, confirm: Intent) {
        val pi = PendingIntent.getActivity(
            ctx, label.hashCode(), confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(ctx, label.hashCode(), "$label update ready", "Tap to finish installing.", pi)
    }

    fun updated(ctx: Context, label: String) =
        post(ctx, label.hashCode(), "$label patched", "Auto-Patch installed the latest version.", null)

    fun failed(ctx: Context, label: String, reason: String?) =
        post(ctx, label.hashCode(), "$label patch failed", reason ?: "Android rejected the update.", null)
}
