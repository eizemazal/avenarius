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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 14+ throws SecurityException if a declared type's runtime
                // permission isn't granted *right now* — so only claim what we hold.
                val type = grantedCaptureTypes(this)
                if (type == 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(ID, notification(), type)
            } else {
                startForeground(ID, notification())
            }
        } catch (e: Exception) {
            // IllegalStateException (background start not allowed), SecurityException
            // (permission race) — either way the call still runs, just not as a FGS.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
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

        fun start(context: Context) {
            val intent = Intent(context, CallService::class.java)
            // startForegroundService itself throws (ForegroundServiceStartNotAllowed) when
            // the app isn't allowed to start one right now; the call works without it.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}
