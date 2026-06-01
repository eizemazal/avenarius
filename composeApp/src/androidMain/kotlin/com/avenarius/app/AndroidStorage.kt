package com.avenarius.app

import android.content.Context
import com.avenarius.app.data.AppStorage

/** [AppStorage] backed by Android SharedPreferences. */
class AndroidStorage(context: Context) : AppStorage {
    private val prefs = context.getSharedPreferences("avenarius", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }
}
