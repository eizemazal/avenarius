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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_ID, buildOngoingNotification())
        if (collectorJob == null) {
            collectorJob = scope.launch {
                Session.client.incoming.collect { msg -> onIncoming(msg) }
            }
        }
        // If killed, restart so the connection comes back.
        return START_STICKY
    }

    private fun onIncoming(msg: Message) {
        val myId = Session.prefs.userId
        if (msg.senderId == myId) return // our own echo
        // Don't notify for the chat the user is actively viewing.
        if (Session.appInForeground && Session.openChatId == msg.chatId) return
        notifyMessage(msg)
    }

    private fun notifyMessage(msg: Message) {
        val title = Session.contacts[msg.senderId] ?: "Новое сообщение"
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CHAT_ID, msg.chatId)
        }
        val pending = PendingIntent.getActivity(
            this,
            msg.chatId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(msg.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        // One notification per chat (newer messages replace the older bubble).
        if (canNotify()) {
            NotificationManagerCompat.from(this).notify(msg.chatId.hashCode(), notification)
        }
    }

    private fun buildOngoingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Авенариус активен")
            .setContentText("Поддерживаем соединение для новых сообщений")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

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

    private fun canNotify(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

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
