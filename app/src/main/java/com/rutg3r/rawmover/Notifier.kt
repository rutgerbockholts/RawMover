package com.rutg3r.rawmover

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    // Channel importance can't be raised after creation, so new IDs replace the old low-importance ones.
    private const val CH_MOVES = "moves_v2"
    private const val CH_PROBLEMS = "problems_v2"
    private val OLD_CHANNELS = listOf("moves", "problems")
    private const val ID_MOVED = 1
    private const val ID_FAILED = 2
    private const val ID_PERMISSION = 3

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
        // IMPORTANCE_HIGH = heads-up pop-up
        nm.createNotificationChannel(
            NotificationChannel(CH_MOVES, "Moved videos", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "A short summary when raw videos were moved" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PROBLEMS, "Problems", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Missing permission or a failed move" }
        )
    }

    fun moved(context: Context, entries: List<MoveEntry>, targetRelPath: String) {
        val title = if (entries.size == 1) "Moved 1 raw video" else "Moved ${entries.size} raw videos"
        val lines = entries.map { "${it.name} (${formatDuration(it.durationMs)}, ${formatSize(it.sizeBytes)})" }
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
            .setSummaryText("→ $targetRelPath")
        post(
            context, ID_MOVED,
            NotificationCompat.Builder(context, CH_MOVES)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentTitle(title)
                .setContentText(lines.first())
                .setStyle(style)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
        )
    }

    fun failed(context: Context, names: List<String>) {
        post(
            context, ID_FAILED,
            NotificationCompat.Builder(context, CH_PROBLEMS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Could not move ${names.size} video(s)")
                .setContentText(names.joinToString())
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
        )
    }

    fun permissionMissing(context: Context) {
        post(
            context, ID_PERMISSION,
            NotificationCompat.Builder(context, CH_PROBLEMS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("RawMover needs \"All files access\"")
                .setContentText("Raw videos are not being moved. Tap to fix.")
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
        )
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}
