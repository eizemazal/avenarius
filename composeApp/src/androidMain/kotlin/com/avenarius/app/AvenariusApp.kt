package com.avenarius.app

import android.app.Application
import android.content.Context
import com.avenarius.app.data.Prefs
import com.avenarius.app.net.MaxClient

/**
 * App-scoped singletons shared by the UI (MainActivity/AppViewModel) and the
 * background [ConnectionService]. The single [client] is what lets the connection
 * survive when the Activity is gone — the service keeps the process alive and the
 * client's coroutines (reader + ping) keep running.
 *
 * The volatile flags are written by the Activity and read by the service to decide
 * whether a given incoming message should raise a notification.
 */
object Session {
    lateinit var client: MaxClient
        private set
    lateinit var prefs: Prefs
        private set

    @Volatile var appInForeground: Boolean = false
    @Volatile var openChatId: Long? = null
    @Volatile var contacts: Map<Long, String> = emptyMap()

    fun init(context: Context) {
        if (::client.isInitialized) return
        prefs = Prefs(AndroidStorage(context.applicationContext))
        client = MaxClient()
    }
}

class AvenariusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}
