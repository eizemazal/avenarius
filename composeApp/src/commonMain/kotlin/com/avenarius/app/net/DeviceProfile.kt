package com.avenarius.app.net

/**
 * The real, per-device values the handshake reports to Max. Previously these were a single
 * static, emulator-shaped string sent by every install (e.g. `deviceName` claimed x86_64
 * while `arch` said arm64) — a trivial cross-account fingerprint. Now each install reports
 * its own device, so the population looks like ordinary phones rather than one cluster.
 *
 * `arch` is deliberately NOT here: it stays pinned to arm64-v8a because the login
 * attestation ([AuthAttest]) carries arm64 native-library hashes and the server validates
 * against the arch we report.
 */
data class DeviceProfile(
    /** Human device name, e.g. "Xiaomi 23021RAAEG" / "samsung SM-A515F". */
    val deviceName: String,
    /** e.g. "Android 14". */
    val osVersion: String,
    /** "<dpi>dpi <dpi>dpi <width>x<height>", the format the client has always used. */
    val screen: String,
    /** App/UI language, e.g. "ru". */
    val locale: String,
    /** Device language, e.g. "ru". */
    val deviceLocale: String,
    /** IANA timezone id, e.g. "Europe/Moscow". */
    val timezone: String,
)

/**
 * The current device's profile. On Android these are the real `Build`/display/locale
 * values; on desktop (which can't be a real phone, yet the server only knows Android
 * clients) a plausible phone profile derived deterministically from [deviceId], so it is
 * stable per install. [deviceId] is the same stable per-install id sent in the handshake.
 */
expect fun deviceProfile(deviceId: String): DeviceProfile
