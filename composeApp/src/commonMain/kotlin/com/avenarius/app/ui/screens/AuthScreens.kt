package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avenarius.app.ui.AppLinks
import com.avenarius.app.ui.components.ErrorText
import com.avenarius.app.ui.components.SmallSpinner
import com.avenarius.app.ui.components.openUriSafely

@Composable
private fun CenteredForm(content: @Composable ColumnScopeAlias.() -> Unit) {
    // The form stays centred while it fits, and scrolls (rather than being clipped)
    // on short screens or once the soft keyboard has eaten half of the window.
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

// Alias so the lambda above reads clearly; Column's scope is ColumnScope.
private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/** A text link to a section of the sign-in guide on GitHub. */
@Composable
private fun LoginHelpLink(
    label: String = "Не получается войти? Подробнее",
    anchor: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUriSafely(AppLinks.loginHelp(anchor)) }) { Text(label) }
}

@Composable
internal fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var phone by remember { mutableStateOf("+7") }
    CenteredForm {
        Text("Авенариус", style = MaterialTheme.typography.headlineMedium)
        // What this app is, for the many who never open "About": said plainly, with
        // nothing it can't back up (messages do still go through Max's servers).
        Text(
            "Неофициальный клиент Max с открытым исходным кодом. Без рекламы и трекеров — " +
                "только то, что нужно для переписки и звонков. Сообщения по-прежнему идут через серверы Max.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUriSafely(AppLinks.GITHUB) }) { Text("Исходный код на GitHub") }
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
        if (error != null) LoginHelpLink()
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
        if (error != null) LoginHelpLink(anchor = "sms-code")
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
    email: String? = null,
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
        // Same guidance the official client gives under "Забыли пароль?": recovery goes
        // through the linked e-mail; without one the password can't be restored here.
        Text(
            if (!email.isNullOrBlank()) {
                "Забыли пароль? Восстановить его можно через почту $email в официальном приложении Max."
            } else {
                "Если вы не помните пароль и не указали почту для восстановления, войти в профиль не получится."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        LoginHelpLink(label = if (error != null) "Не получается войти? Подробнее" else "Забыли пароль?", anchor = "password")
        Button(
            onClick = { onSubmit(password) },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Войти")
        }
    }
}

/**
 * The server accepted the SMS code but won't sign this device in until the account has
 * a login password — which can only be created from a device that is already signed
 * in. Wording follows the official client's "restricted login" screen.
 */
@Composable
internal fun RestrictedLoginScreen(
    /** The server's own wording, if it gave one. */
    detail: String?,
    onRetry: () -> Unit,
) {
    CenteredForm {
        Text("Нужен пароль для входа", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Max требует пароль для входа в этот профиль. Обычно так бывает, когда профиль уже открыт " +
                "на другом телефоне в официальном приложении Max.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Что сделать:\n" +
                "1. На том телефоне откройте Max → Настройки → Безопасность → Пароль для входа и установите пароль.\n" +
                "2. Вернитесь сюда и войдите снова: после кода из СМС Авенариус попросит этот пароль.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!detail.isNullOrBlank()) {
            Text(
                "Сообщение Max: $detail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Повторить вход")
        }
        LoginHelpLink(label = "Подробнее о входе", anchor = "password-required")
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
        if (error != null) LoginHelpLink()
        Button(
            onClick = { onSubmit(name) },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Создать аккаунт")
        }
    }
}
