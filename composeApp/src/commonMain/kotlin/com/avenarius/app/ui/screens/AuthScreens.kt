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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.avenarius.app.ui.PasswordRestore
import com.avenarius.app.ui.RestoreStep
import com.avenarius.app.ui.components.ErrorText
import com.avenarius.app.ui.components.SmallSpinner
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.nowMillis
import kotlinx.coroutines.delay

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
    onForgotPassword: () -> Unit = {},
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
        // As in the official client: recovery goes through the linked e-mail; without one
        // the password can't be restored at all, so point to the guide instead.
        if (!email.isNullOrBlank()) {
            TextButton(onClick = onForgotPassword, enabled = !busy) { Text("Забыли пароль?") }
            Text(
                "Код для восстановления придёт на почту $email.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Если вы не помните пароль и не указали почту для восстановления, войти в профиль не получится.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LoginHelpLink(label = "Забыли пароль?", anchor = "password")
        }
        if (error != null) LoginHelpLink(anchor = "password")
    }
}

/** "Забыли пароль?": e-mail code → new password → optional hint, then signed in. */
@Composable
internal fun RestorePasswordScreen(
    restore: PasswordRestore,
    busy: Boolean,
    error: String?,
    onSubmitCode: (String) -> Unit,
    onResendCode: () -> Unit,
    onSubmitPassword: (password: String, repeat: String) -> Unit,
    onSubmitHint: (String) -> Unit,
) {
    // Fresh, empty fields on each step.
    androidx.compose.runtime.key(restore.step) {
        CenteredForm {
            Text("Восстановление пароля", style = MaterialTheme.typography.headlineMedium)
            when (restore.step) {
                RestoreStep.EMAIL_CODE -> RestoreCodeStep(restore, busy, error, onSubmitCode, onResendCode)
                RestoreStep.NEW_PASSWORD -> RestorePasswordStep(busy, error, onSubmitPassword)
                RestoreStep.HINT -> RestoreHintStep(busy, error, onSubmitHint)
            }
        }
    }
}

@Composable
private fun RestoreCodeStep(
    restore: PasswordRestore,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    // Seconds until "send again" unlocks, ticking down once a second.
    var secondsLeft by remember(restore.resendAtMs) { mutableStateOf(0L) }
    LaunchedEffect(restore.resendAtMs) {
        while (true) {
            secondsLeft = ((restore.resendAtMs - nowMillis()) / 1000).coerceAtLeast(0)
            if (secondsLeft == 0L) break
            delay(1000)
        }
    }
    Text("Какой код пришёл на почту?", style = MaterialTheme.typography.titleMedium)
    Text("Проверьте почту ${restore.email}", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = code,
        onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.let { f -> restore.codeLength?.let { f.take(it) } ?: f } },
        label = { Text("Код") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    Button(onClick = { onSubmit(code) }, enabled = !busy && code.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        if (busy) SmallSpinner() else Text("Продолжить")
    }
    TextButton(onClick = onResend, enabled = !busy && secondsLeft == 0L) {
        Text(if (secondsLeft > 0) "Получить новый код можно через $secondsLeft с" else "Получить новый код")
    }
}

@Composable
private fun RestorePasswordStep(
    busy: Boolean,
    error: String?,
    onSubmit: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    Text("Придумайте новый пароль", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = repeat,
        onValueChange = { repeat = it },
        label = { Text("Повторите пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = repeat.isNotEmpty() && repeat != password,
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    Button(
        onClick = { onSubmit(password, repeat) },
        enabled = !busy && password.isNotEmpty() && repeat.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) SmallSpinner() else Text("Продолжить")
    }
}

@Composable
private fun RestoreHintStep(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var hint by remember { mutableStateOf("") }
    Text("Подсказка (необязательно)", style = MaterialTheme.typography.titleMedium)
    Text("Она поможет вспомнить пароль. Подсказка не должна совпадать с паролем.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = hint,
        onValueChange = { hint = it },
        label = { Text("Введите подсказку") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    Button(onClick = { onSubmit(hint) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) SmallSpinner() else Text(if (hint.isBlank()) "Пропустить и войти" else "Сохранить и войти")
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
    /** The profile-recovery web URL (from the server, or the default). */
    recoveryUrl: String,
    onRetry: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    CenteredForm {
        Text("Нужен пароль для входа", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Max ограничил вход в этот профиль. Есть два пути вернуть доступ.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Если у вас есть другой телефон, где вы уже вошли в Max:\n" +
                "откройте там Max → Настройки → Безопасность → Пароль для входа, установите пароль, " +
                "затем вернитесь сюда и войдите снова — после кода из СМС Авенариус попросит этот пароль.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Если другого устройства нет — пройдите восстановление профиля на сайте Max. " +
                "Откройте его с этого телефона через мобильный интернет (не Wi-Fi) и без VPN: " +
                "сайт проверит, что SIM-карта с вашим номером стоит в этом устройстве.",
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
        // Opens Max's self-recovery web flow in the browser — the same page the official
        // app opens; the carrier check happens there, not in the app.
        OutlinedButton(onClick = { uriHandler.openUriSafely(recoveryUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text("Восстановить профиль")
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
