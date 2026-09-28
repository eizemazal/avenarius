package com.avenarius.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service kept alive for the duration of a call so mic/camera capture
 * keeps running when the app is backgrounded. Declared with the microphone + camera
 * foreground-service types (Android 14+ requires the type to match the capture).
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Звонки", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // A service started with startForegroundService() MUST reach startForeground()
        // before anything else — returning early (even to stop) is exactly what Android
        // punishes with ForegroundServiceDidNotStartInTimeException. [start] only starts
        // us when a capture permission is held, so this succeeds; the stop request is
        // handled afterwards.
        val foreground = goForeground()
        if (intent?.action == ACTION_STOP || !foreground) {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY // a call can't be resumed by a restart
    }

    private fun goForeground(): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 14+ throws SecurityException if a declared type's runtime
                // permission isn't granted *right now* — so only claim what we hold.
                val type = grantedCaptureTypes(this)
                if (type == 0) {
                    false
                } else {
                    startForeground(ID, notification(), type)
                    true
                }
            } else {
                startForeground(ID, notification())
                true
            }
        } catch (e: Exception) {
            // IllegalStateException (background start not allowed), SecurityException
            // (permission revoked in between) — the call still runs, just not as a FGS.
            false
        }

    private fun notification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Звонок")
            .setContentText("Идёт звонок")
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

    companion object {
        private const val CHANNEL = "avenarius_calls"
        private const val ID = 2

        /** FGS type mask limited to the capture permissions currently granted. */
        fun grantedCaptureTypes(context: Context): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
            var type = 0
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            return type
        }

        private const val ACTION_STOP = "com.avenarius.app.action.STOP_CALL_SERVICE"

        /** Whether we asked for the service to run (and haven't asked it to stop since). */
        @Volatile private var requested = false

        fun start(context: Context) {
            // Without mic or camera permission (the user declined) the service could not
            // legally go foreground — and once startForegroundService() is called it must.
            // So don't start it at all; the call itself works without it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && grantedCaptureTypes(context) == 0) return
            val intent = Intent(context, CallService::class.java)
            // startForegroundService itself throws (ForegroundServiceStartNotAllowed) when
            // the app isn't allowed to start one right now; the call works without it.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                requested = true
            }
        }

        /**
         * Stops via a command rather than stopService(): commands are delivered in order,
         * so a start that hasn't been handled yet still reaches startForeground() first.
         * Stopping it before that point is itself a DidNotStartInTime failure.
         */
        fun stop(context: Context) {
            if (!requested) return
            requested = false
            runCatching {
                context.startService(Intent(context, CallService::class.java).setAction(ACTION_STOP))
            }.onFailure {
                // Not allowed from the background — then the service isn't in the
                // foreground either (it would make us a foreground app), so plain stop is safe.
                runCatching { context.stopService(Intent(context, CallService::class.java)) }
            }
        }
    }
}
