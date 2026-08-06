package com.example.feedlite.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.feedlite.MainActivity
import com.example.feedlite.R

/**
 * 新文章通知（v1.34）：后台同步抓取到新文章后弹系统通知。
 *
 * - 需要 [UpdateSettings.UpdateConfig.notifyEnabled] 开启；
 * - Android 13+ 未授予 POST_NOTIFICATIONS 权限时静默跳过（[areNotificationsEnabled] 兜底）；
 * - 固定通知 id，重复弹窗覆盖旧通知（不堆积）。
 */
class NewArticleNotifier(private val context: Context) {

    fun notify(total: Int, summary: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return // 权限被拒 / 通知总开关关闭：静默跳过

        createChannel()

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_home) // RSS 信号图标（通知小图标要求纯 alpha 造型）
            .setContentTitle("$total 篇新文章")
            .setContentText(summary)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "新文章提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "后台同步抓取到新文章时提醒"
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "new_articles"
        private const val NOTIFICATION_ID = 1001
    }
}
