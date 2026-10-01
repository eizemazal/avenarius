package com.avenarius.app.net

import android.os.Build
import com.avenarius.app.Session
import java.util.Locale
import java.util.TimeZone

actual fun deviceProfile(deviceId: String): DeviceProfile {
    val metrics = Session.appContext.resources.displayMetrics
    val dpi = metrics.densityDpi
    // Portrait orientation like a phone held normally: narrower side first.
    val w = minOf(metrics.widthPixels, metrics.heightPixels)
    val h = maxOf(metrics.widthPixels, metrics.heightPixels)
    val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
    val model = Build.MODEL?.trim().orEmpty()
    val name = listOf(manufacturer, model).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { "Android" }
    val lang = Locale.getDefault().language.ifBlank { "ru" }
    return DeviceProfile(
        deviceName = name,
        osVersion = "Android ${Build.VERSION.RELEASE}",
        screen = "${dpi}dpi ${dpi}dpi ${w}x$h",
        locale = lang,
        deviceLocale = lang,
        timezone = TimeZone.getDefault().id,
    )
}
