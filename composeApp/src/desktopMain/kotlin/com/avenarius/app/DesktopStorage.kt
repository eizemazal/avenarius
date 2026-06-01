package com.avenarius.app

import com.avenarius.app.data.AppStorage
import java.io.File
import java.util.Properties

/**
 * [AppStorage] backed by a plain properties file under the user's home dir.
 * This is the desktop counterpart of AndroidStorage.
 */
class DesktopStorage : AppStorage {
    private val file = File(System.getProperty("user.home"), ".avenarius/prefs.properties")
    private val props = Properties()

    init {
        if (file.exists()) file.inputStream().use { props.load(it) }
    }

    override fun getString(key: String): String? = props.getProperty(key)

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "Avenarius") }
    }
}
