package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.TwoFaFlow
import com.avenarius.app.ui.TwoFaStep
import com.avenarius.app.ui.components.ErrorText
import com.avenarius.app.ui.components.SmallSpinner

/**
 * Settings → "Пароль для входа": the official client's set-up / change / remove flow
 * for the login password (2FA), one step per [TwoFaStep]. Wording follows the official
 * app's strings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TwoFaScreen(
    flow: TwoFaFlow,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStartSetup: () -> Unit,
    onSubmitPassword: (password: String, repeat: String) -> Unit,
    onSubmitHint: (String) -> Unit,
    onSubmitEmail: (String) -> Unit,
    onSkipEmail: () -> Unit,
    onSubmitEmailCode: (String) -> Unit,
    onCheckPassword: (String) -> Unit,
    onChangePassword: () -> Unit,
    onDisable: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пароль для входа") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") } },
            )
        },
    ) { padding ->
        // Keyed on the step so each step starts with empty fields.
        androidx.compose.runtime.key(flow.step) {
            StepForm(Modifier.padding(padding)) {
                when (flow.step) {
                    TwoFaStep.INTRO -> IntroStep(flow.enabled, busy, error, onStartSetup)
                    TwoFaStep.PASSWORD -> PasswordStep(flow.changing, busy, error, onSubmitPassword)
                    TwoFaStep.HINT -> HintStep(busy, error, onSubmitHint)
                    TwoFaStep.EMAIL -> EmailStep(busy, error, onSubmitEmail, onSkipEmail)
                    TwoFaStep.EMAIL_CODE -> EmailCodeStep(flow.pendingEmail, busy, error, onSubmitEmailCode)
                    TwoFaStep.DONE_SET ->
                        DoneStep(
                            title = if (flow.changing) "Вы изменили пароль" else "Вы установили пароль",
                            text = "Теперь для входа в профиль каждый раз нужно вводить пароль и код из СМС.",
                            onBack = onBack,
                        )
                    TwoFaStep.CHECK -> CheckStep(flow.hint, busy, error, onCheckPassword)
                    TwoFaStep.MANAGE -> ManageStep(flow, busy, error, onChangePassword, onDisable)
                    TwoFaStep.DONE_REMOVED ->
                        DoneStep(
                            title = "Вы отключили пароль",
                            text = "Для входа понадобится только код из СМС.",
                            onBack = onBack,
                        )
                }
            }
        }
    }
}

@Composable
private fun StepForm(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

@Composable
private fun PrimaryButton(
    label: String,
    busy: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = !busy && enabled, modifier = Modifier.fillMaxWidth()) {
        if (busy) SmallSpinner() else Text(label)
    }
}

@Composable
private fun IntroStep(
    enabled: Boolean?,
    busy: Boolean,
    error: String?,
    onStart: () -> Unit,
) {
    // Only claim "off" once the server has said so; until then the state is unknown.
    Text(
        if (enabled == false) "Отключён" else "Состояние уточняется…",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Дополнительный пароль для каждого нового входа в Max. Установите его, чтобы ещё сильнее защитить профиль: " +
            "без пароля войти в него с нового устройства будет нельзя.",
        style = MaterialTheme.typography.bodyMedium,
    )
    ErrorText(error)
    PrimaryButton("Установить пароль", busy, onClick = onStart)
}

@Composable
private fun PasswordStep(
    changing: Boolean,
    busy: Boolean,
    error: String?,
    onSubmit: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    Text(if (changing) "Придумайте новый пароль" else "Придумайте пароль", style = MaterialTheme.typography.headlineSmall)
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
    PrimaryButton("Продолжить", busy, enabled = password.isNotEmpty() && repeat.isNotEmpty()) { onSubmit(password, repeat) }
}

@Composable
private fun HintStep(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var hint by remember { mutableStateOf("") }
    Text("Подсказка (необязательно)", style = MaterialTheme.typography.headlineSmall)
    Text("Она поможет вспомнить пароль. Подсказка не должна совпадать с паролем.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = hint,
        onValueChange = { hint = it },
        label = { Text("Введите подсказку") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    PrimaryButton(if (hint.isBlank()) "Пропустить" else "Продолжить", busy) { onSubmit(hint) }
}

@Composable
private fun EmailStep(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    Text("Укажите почту для восстановления пароля", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Только с почтой можно восстановить пароль. Без почты вы можете потерять доступ к профилю, если забудете его.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    PrimaryButton("Указать почту", busy, enabled = email.contains('@')) { onSubmit(email) }
    TextButton(onClick = onSkip, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Пропустить") }
}

@Composable
private fun EmailCodeStep(
    email: String?,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Text("Какой код пришёл на почту?", style = MaterialTheme.typography.headlineSmall)
    Text("Проверьте почту ${email ?: ""}".trim(), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter { ch -> ch.isLetterOrDigit() } },
        label = { Text("Код") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    PrimaryButton("Продолжить", busy, enabled = code.isNotEmpty()) { onSubmit(code) }
}

@Composable
private fun CheckStep(
    hint: String?,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    Text("Включён", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text("Введите пароль для входа", style = MaterialTheme.typography.headlineSmall)
    Text("Чтобы изменить или отключить пароль, сначала подтвердите текущий.", style = MaterialTheme.typography.bodyMedium)
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
    PrimaryButton("Продолжить", busy, enabled = password.isNotEmpty()) { onSubmit(password) }
}

@Composable
private fun ManageStep(
    flow: TwoFaFlow,
    busy: Boolean,
    error: String?,
    onChangePassword: () -> Unit,
    onDisable: () -> Unit,
) {
    var confirmDisable by remember { mutableStateOf(false) }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Хотите отключить пароль для входа?") },
            text = { Text("Для входа понадобится только код из СМС.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    onDisable()
                }) { Text("Отключить пароль") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Оставить пароль") } },
        )
    }
    Text("Ваш профиль дополнительно защищён", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Теперь для входа в профиль каждый раз нужно вводить пароль и код из СМС.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        if (flow.email != null) "Почта для восстановления: ${flow.email}" else "Почта для восстановления не указана",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ErrorText(error)
    PrimaryButton("Изменить пароль", busy, onClick = onChangePassword)
    OutlinedButton(onClick = { confirmDisable = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Отключить пароль", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DoneStep(
    title: String,
    text: String,
    onBack: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(text, style = MaterialTheme.typography.bodyMedium)
    PrimaryButton("Готово", busy = false, onClick = onBack)
}
