package com.avenarius.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.avenarius.app.model.DeviceContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberDeviceContacts(): List<DeviceContact> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    val load = {
        scope.launch { contacts = withContext(Dispatchers.IO) { queryContacts(context) } }
        Unit
    }
    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) load()
        }
    // Ask once when the picker first appears; the address book is exactly relevant then.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            load()
        } else {
            requestPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    return contacts
}

/** Reads name + phone rows, de-duplicated by (name, normalised phone). */
private fun queryContacts(context: Context): List<DeviceContact> {
    val projection =
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
    val out = LinkedHashMap<String, DeviceContact>()
    runCatching {
        context.contentResolver
            .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val rawPhone = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isBlank() || rawPhone.isBlank()) continue
                    val normalized = "+" + rawPhone.filter { it.isDigit() }
                    if (normalized.length < 7) continue
                    out.putIfAbsent("$name|$normalized", DeviceContact(name, normalized))
                }
            }
    }
    return out.values.toList()
}
