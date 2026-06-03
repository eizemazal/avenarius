package com.avenarius.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.rememberSingleImagePickLauncher

/** Edit-profile screen: change avatar, first/last name and bio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileScreen(
    initialFirstName: String,
    initialLastName: String,
    initialDescription: String,
    currentAvatarUrl: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (firstName: String, lastName: String, description: String, avatar: PickedMedia?) -> Unit,
) {
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var description by remember { mutableStateOf(initialDescription) }
    var avatar by remember { mutableStateOf<PickedMedia?>(null) }
    val pickAvatar = rememberSingleImagePickLauncher { avatar = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).clickable { pickAvatar() }, contentAlignment = Alignment.Center) {
                val picked = avatar
                if (picked != null) {
                    AsyncImage(
                        model = picked.bytes,
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
                onValueChange = { description = it },
                label = { Text("О себе") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Button(
                onClick = { onSave(firstName, lastName, description, avatar) },
                enabled = firstName.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Сохранить")
            }
        }
    }
}
