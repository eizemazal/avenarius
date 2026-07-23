package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import com.avenarius.app.model.DeviceContact

/**
 * Reads the device address book (name + phone), requesting the READ_CONTACTS
 * permission on first use. Returns an empty list until granted/loaded, and on
 * platforms without an address book (desktop).
 */
@Composable
expect fun rememberDeviceContacts(): List<DeviceContact>
