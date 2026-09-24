package com.avenarius.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avenarius.app.model.CallDirection
import com.avenarius.app.model.CallKind
import com.avenarius.app.model.CallState
import com.avenarius.app.model.CallStatus
import com.avenarius.app.model.Message
import com.avenarius.app.model.previewLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        // On Android 12+ starting a foreground service from the background is not
        // allowed and throws ForegroundServiceStartNotAllowedException (a subclass of
        // IllegalStateException). This can happen on a START_STICKY restart while the
        // app is backgrounded — bail out gracefully instead of crashing; MainActivity
        // will start us again next time it's in the foreground.
        try {
            startForeground(ONGOING_ID, buildOngoingNotification())
        } catch (e: IllegalStateException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (collectorJob == null) {
            collectorJob =
                scope.launch {
                    Session.client.incoming.collect { msg -> onIncoming(msg) }
                }
        }
        if (callJob == null) {
            callJob =
                scope.launch {
                    Session.callSession.state.collect { call -> onCallState(call) }
                }
        }
        // "Отклонить" on the incoming-call notification.
        if (intent?.action == ACTION_DECLINE_CALL) Session.callSession.decline()
        // If killed, restart so the connection comes back.
        return START_STICKY
    }

    // ------------------------------------------------------------------ incoming calls

    private var callJob: Job? = null

    /** conversationId of the call we are currently ringing for, or null. */
    private var ringingId: String? = null
    private var ringtone: Ringtone? = null
    private var ringLoop: Job? = null
    private var vibrator: Vibrator? = null

    /**
     * An inbound call is only state in [Session.callSession]; when the app is in the
     * background nothing renders that state, so the service must do what a phone does:
     * ring, vibrate, and put up a full-screen call notification with accept/decline.
     */
    private suspend fun onCallState(call: CallState?) {
        val ringing = call != null && call.status == CallStatus.RINGING && call.direction == CallDirection.INCOMING
        if (ringing && call != null) {
            if (call.conversationId == ringingId) return
            ringingId = call.conversationId
            startRinging()
            val resolved = call.peerName ?: Session.contacts[call.peerId] ?: resolveName(call.peerId)
            // Feed the name back into the call state: with the Activity gone there is no
            // ViewModel to do it, and the overlay opened from the notification would
            // otherwise show "Неизвестный".
            if (call.peerName == null && resolved != null) Session.callSession.setPeerInfo(call.conversationId, resolved, null)
            val name = resolved ?: "Неизвестный"
            // In the foreground the IncomingCallOverlay is already on screen; the
            // notification is for when it isn't (background / locked).
            if (!Session.appInForeground && canNotify()) {
                NotificationManagerCompat.from(this).notify(CALL_ID, buildCallNotification(call, name))
            }
        } else if (ringingId != null) {
            ringingId = null
            stopRinging()
            NotificationManagerCompat.from(this).cancel(CALL_ID)
        }
    }

    private fun startRinging() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val tone = uri?.let { runCatching { RingtoneManager.getRingtone(this, it) }.getOrNull() }
            if (tone != null) {
                tone.audioAttributes =
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ringtone = tone
                // Ringtone.isLooping only exists from API 28; restarting it ourselves
                // works everywhere.
                ringLoop =
                    scope.launch {
                        while (isActive) {
                            if (tone.isPlaying.not()) runCatching { tone.play() }
                            delay(500)
                        }
                    }
            }
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            val v =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            vibrator = v
            val pattern = longArrayOf(0, 800, 1200) // buzz 0.8 s, pause 1.2 s, repeat
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            }
        }
    }

    private fun stopRinging() {
        ringLoop?.cancel()
        ringLoop = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun buildCallNotification(
        call: CallState,
        name: String,
    ): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open =
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_INCOMING_CALL, true)
            }
        val show = PendingIntent.getActivity(this, REQ_CALL_OPEN, open, flags)
        val accept =
            PendingIntent.getActivity(
                this,
                REQ_CALL_ACCEPT,
                Intent(open).putExtra(MainActivity.EXTRA_ACCEPT_CALL, true),
                flags,
            )
        val decline =
            PendingIntent.getService(
                this,
                REQ_CALL_DECLINE,
                Intent(this, ConnectionService::class.java).setAction(ACTION_DECLINE_CALL),
                flags,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(name)
            .setContentText(if (call.kind == CallKind.VIDEO) "Входящий видеозвонок" else "Входящий звонок")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(show)
            // Wakes the screen with the call UI (needs USE_FULL_SCREEN_INTENT; where the
            // OS withholds it, the heads-up notification with the two actions remains).
            .setFullScreenIntent(show, true)
            .addAction(0, "Отклонить", decline)
            .addAction(0, "Принять", accept)
            .setTimeoutAfter(CALL_NOTIFICATION_TIMEOUT_MS)
            .build()
    }

    // Android 14+/15 impose a daily time limit on `dataSync` foreground services.
    // When it's reached the system calls onTimeout and we MUST stop promptly, else it
    // throws ForegroundServiceDidNotStopInTimeException. The connection is simply
    // re-established the next time the app is foregrounded.
    override fun onTimeout(startId: Int) = stopCleanly()

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) = stopCleanly()

    private fun stopCleanly() {
        collectorJob?.cancel()
        collectorJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun onIncoming(msg: Message) {
        val myId = Session.prefs.userId
        if (msg.senderId == myId) return // our own echo
        // Don't show system notifications while the app is on screen — the user is
        // already here and the in-app chat list / open chat updates live. We only
        // notify when backgrounded.
        if (Session.appInForeground) return

        val info = Session.chatInfo[msg.chatId]
        // Respect the per-chat mute setting — no notifications for muted conversations.
        if (info?.muted == true) return
        val sender = resolveName(msg.senderId)
        // previewLabel, not text: a photo/voice/file message has no text and would
        // otherwise produce a blank notification.
        val preview = msg.previewLabel()
        val (title, body) =
            if (info == null || info.isDialog) {
                // 1:1 dialog: title is the person, body is the message.
                (sender ?: info?.title ?: "Новое сообщение") to preview
            } else {
                // Group: title is the chat, body names the actual sender.
                info.title to "${sender ?: "Кто-то"}: $preview"
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
        // Sound/vibration are driven by the service itself (looping ringtone), not the
        // channel — otherwise both would play, and the channel can't loop.
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun canNotify(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

    override fun onDestroy() {
        collectorJob?.cancel()
        callJob?.cancel()
        callJob = null
        stopRinging()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ONGOING = "avenarius_connection"
        private const val CHANNEL_MESSAGES = "avenarius_messages"
        private const val CHANNEL_CALLS = "avenarius_incoming_calls"
        private const val ONGOING_ID = 1

        /** Notification id of the ringing-call entry (ids >1 are otherwise chat-id hashes). */
        private const val CALL_ID = 3
        private const val REQ_CALL_OPEN = 101
        private const val REQ_CALL_ACCEPT = 102
        private const val REQ_CALL_DECLINE = 103
        private const val CALL_NOTIFICATION_TIMEOUT_MS = 60_000L
        private const val ACTION_DECLINE_CALL = "com.avenarius.app.action.DECLINE_CALL"

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
