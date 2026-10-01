package com.avenarius.app.net

import java.util.Locale
import java.util.TimeZone
import kotlin.math.absoluteValue

// Desktop can't be a real phone, but the server only knows Android clients. Pick a
// plausible, real phone model deterministically from the install's deviceId so the
// reported device is stable across launches and varies across installs.
private data class Phone(
    val name: String,
    val android: String,
    val dpi: Int,
    val w: Int,
    val h: Int,
)

private val PHONES =
    listOf(
        Phone("samsung SM-A515F", "12", 420, 1080, 2400),
        Phone("samsung SM-G991B", "14", 425, 1080, 2400),
        Phone("Xiaomi 2201123G", "13", 440, 1080, 2400),
        Phone("Xiaomi 23021RAAEG", "14", 395, 1080, 2400),
        Phone("Redmi Note 12", "13", 395, 1080, 2400),
        Phone("realme RMX3630", "14", 450, 1080, 2412),
        Phone("HUAWEI STK-L21", "10", 480, 1080, 2340),
        Phone("vivo V2120", "13", 408, 1080, 2400),
    )

actual fun deviceProfile(deviceId: String): DeviceProfile {
    val phone = PHONES[(deviceId.hashCode().absoluteValue) % PHONES.size]
    val lang = Locale.getDefault().language.ifBlank { "ru" }
    return DeviceProfile(
        deviceName = phone.name,
        osVersion = "Android ${phone.android}",
        screen = "${phone.dpi}dpi ${phone.dpi}dpi ${phone.w}x${phone.h}",
        locale = lang,
        deviceLocale = lang,
        timezone = TimeZone.getDefault().id,
    )
}
