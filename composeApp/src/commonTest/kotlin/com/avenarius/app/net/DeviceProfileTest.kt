package com.avenarius.app.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs against the desktop (jvm) actual; the Android actual is covered on-device. */
class DeviceProfileTest {
    @Test
    fun stablePerInstallAndVariedAcrossInstalls() {
        val a = deviceProfile("install-aaaa")
        assertEquals(a, deviceProfile("install-aaaa")) // same id → same device each launch
        val distinct = (0 until 40).map { deviceProfile("install-$it").deviceName }.toSet()
        assertTrue(distinct.size > 1, "different installs must not all report the same device")
    }

    @Test
    fun looksLikeARealAndroidPhone() {
        val p = deviceProfile("install-xyz")
        assertTrue(p.osVersion.startsWith("Android "), p.osVersion)
        assertTrue(Regex("""\d+dpi \d+dpi \d+x\d+""").matches(p.screen), p.screen)
        // The old static tells we removed: no emulator/x86 wording in the device name.
        assertTrue(!p.deviceName.contains("x86") && !p.deviceName.contains("Generic"), p.deviceName)
        assertTrue(p.deviceName.isNotBlank() && p.timezone.isNotBlank() && p.locale.isNotBlank())
    }
}
