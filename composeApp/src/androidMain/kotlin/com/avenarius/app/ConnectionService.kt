package com.avenarius.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avenarius.app.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app process (and therefore the shared
 * [Session.client] connection + its keep-alive ping) alive while the app is
 * backgrounded, and raises a notification for each incoming message.
 *
 * Android requires a foreground service to show an ongoing notification — that's
 * the permanent "Авенариус активен" entry.
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob())
    private var collectorJob: Job? = null

    /** Cache of resolved sender names (for group members not in your contacts). */
    private val nameCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(ONGOING_ID, buildOngoingNotification())
        if (collectorJob == null) {
            collectorJob =
                scope.launch {
                    Session.client.incoming.collect { msg -> onIncoming(msg) }
                }
        }
        // If killed, restart so the connection comes back.
        return START_STICKY
    }

    private suspend fun onIncoming(msg: Message) {
        val myId = Session.prefs.userId
        if (msg.senderId == myId) return // our own echo
        // Don't notify for the chat the user is actively viewing.
        if (Session.appInForeground && Session.openChatId == msg.chatId) return

        val info = Session.chatInfo[msg.chatId]
        val sender = resolveName(msg.senderId)
        val (title, body) =
            if (info == null || info.isDialog) {
                // 1:1 dialog: title is the person, body is the message.
                (sender ?: info?.title ?: "Новое сообщение") to msg.text
            } else {
                // Group: title is the chat, body names the actual sender.
                info.title to "${sender ?: "Кто-то"}: ${msg.text}"
            }
        notifyMessage(msg.chatId, title, body)
    }

    /** Sender display name: synced contacts -> cache -> fetched on demand (groups). */
    private suspend fun resolveName(userId: Long): String? {
        Session.contacts[userId]?.let { return it }
        nameCache[userId]?.let { return it }
        val name = runCatching { Session.client.fetchContactName(userId) }.getOrNull()
        if (name != null) nameCache[userId] = name
        return name
    }

    private fun notifyMessage(
        chatId: Long,
        title: String,
        body: String,
    ) {
        val openIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
            }
        val pending =
            PendingIntent.getActivity(
                this,
                chatId.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .build()
        // One notification per chat (newer messages replace the older bubble).
        if (canNotify()) {
            NotificationManagerCompat.from(this).notify(chatId.hashCode(), notification)
        }
    }

    private fun buildOngoingNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Авенариус активен")
            .setContentText("Поддерживаем соединение для новых сообщений")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Соединение", NotificationManager.IMPORTANCE_LOW),
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun canNotify(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

    override fun onDestroy() {
        collectorJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ONGOING = "avenarius_connection"
        private const val CHANNEL_MESSAGES = "avenarius_messages"
        private const val ONGOING_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}
