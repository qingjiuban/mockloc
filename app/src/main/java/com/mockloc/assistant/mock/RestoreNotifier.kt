package com.mockloc.assistant.mock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mockloc.assistant.MainActivity
import com.mockloc.assistant.R
import com.mockloc.assistant.data.model.MockPoint

/** 重启后提示用户可以恢复上次模拟会话 */
object RestoreNotifier {

    private const val CHANNEL_ID = "mock_location_restore"
    private const val NOTIFICATION_ID = 2102

    fun show(context: Context, point: MockPoint?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = context.getString(R.string.notification_channel_desc)
                    },
                )
            }
        }

        val openIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_RESTORE_SESSION, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = point?.let {
            context.getString(R.string.notification_restore_text) + " · " + it.displayName
        } ?: context.getString(R.string.notification_restore_text)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_restore_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
