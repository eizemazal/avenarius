package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import com.avenarius.app.model.DeviceContact

// Desktop has no address book.
@Composable
actual fun rememberDeviceContacts(): List<DeviceContact> = emptyList()
