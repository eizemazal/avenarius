package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.avenarius.app.ui.components.ErrorText
import com.avenarius.app.ui.components.SmallSpinner

@Composable
private fun CenteredForm(content: @Composable ColumnScopeAlias.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

// Alias so the lambda above reads clearly; Column's scope is ColumnScope.
private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
internal fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var phone by remember { mutableStateOf("+7") }
    CenteredForm {
        Text("Авенариус", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Введите номер телефона для входа или регистрации в Max",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(phone) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Получить код")
        }
    }
}

@Composable
internal fun CodeScreen(
    busy: Boolean,
    error: String?,
    codeLength: Int,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    CenteredForm {
        Text("Подтверждение", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Введите код из SMS ($codeLength цифр)",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { ch -> ch.isDigit() } },
            label = { Text("Код") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(code) },
            enabled = !busy && code.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Войти")
        }
    }
}

@Composable
internal fun PasswordScreen(
    busy: Boolean,
    error: String?,
    hint: String?,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    CenteredForm {
        Text("Пароль для входа", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Для входа на новом устройстве введите пароль, заданный в настройках Max (Безопасность → Пароль для входа).",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!hint.isNullOrBlank()) {
            Text("Подсказка: $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(password) },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Войти")
        }
    }
}

@Composable
internal fun RegisterScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    CenteredForm {
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Этот номер ещё не зарегистрирован в Max. Введите имя, чтобы создать аккаунт.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(name) },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Создать аккаунт")
        }
    }
}
