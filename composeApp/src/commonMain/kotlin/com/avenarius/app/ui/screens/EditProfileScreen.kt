package com.avenarius.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.rememberSingleImagePickLauncher
import com.avenarius.app.ui.theme.Spacing

/** Edit-profile screen: change avatar, first/last name and bio. */
@Composable
internal fun EditProfileScreen(
    initialFirstName: String,
    initialLastName: String,
    initialDescription: String,
    /** Our profile has loaded; until then the bio is unknown and saving stays off. */
    profileLoaded: Boolean = true,
    currentAvatarUrl: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (firstName: String, lastName: String, description: String, avatar: PickedMedia?) -> Unit,
) {
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    // From Settings our bio arrives a moment after the screen opens: it is filled in then,
    // unless the user has typed there already.
    var description by remember { mutableStateOf(initialDescription) }
    var descriptionTouched by remember { mutableStateOf(false) }
    LaunchedEffect(profileLoaded, initialDescription) {
        if (profileLoaded && !descriptionTouched) description = initialDescription
    }
    var avatar by remember { mutableStateOf<PickedMedia?>(null) }
    val pickAvatar = rememberSingleImagePickLauncher { avatar = it }

    Scaffold(
        topBar = { SubPageBar("Редактирование", onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).clickable { pickAvatar() }, contentAlignment = Alignment.Center) {
                val picked = avatar
                if (picked != null) {
                    AsyncImage(
                        model = picked.content.previewModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Avatar(firstName.ifBlank { "Я" }, currentAvatarUrl, 96.dp)
                }
            }
            Text("Изменить фото", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Фамилия") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    descriptionTouched = true
                },
                label = { Text("О себе") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Button(
                onClick = { onSave(firstName, lastName, description, avatar) },
                enabled = firstName.isNotBlank() && !busy && profileLoaded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy || !profileLoaded) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Сохранить")
            }
        }
    }
}
