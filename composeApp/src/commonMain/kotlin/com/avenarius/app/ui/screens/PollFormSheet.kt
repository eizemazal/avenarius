package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.GroupCard
import com.avenarius.app.ui.components.PillTextField
import com.avenarius.app.ui.components.groupRowDivider
import com.avenarius.app.ui.theme.Spacing

/** The official form's limits: a 200-character question, 2 to 12 answers. */
private const val QUESTION_MAX = 200
private const val ANSWERS_MIN = 2
private const val ANSWERS_MAX = 12

/** «Новый опрос»: question, answers, the settings the server's bits confirm, «Создать». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollFormSheet(
    onCreate: (question: String, answers: List<String>, settings: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var question by remember { mutableStateOf("") }
    var answers by remember { mutableStateOf(List(ANSWERS_MIN) { "" }) }
    var anonymous by remember { mutableStateOf(false) }
    var multiple by remember { mutableStateOf(false) }
    var revote by remember { mutableStateOf(true) }
    val filled = answers.count { it.isNotBlank() }
    AppSheet(onDismiss) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l)
                .padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Новый опрос", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.s))
            PillTextField(
                question,
                { question = it.take(QUESTION_MAX) },
                "Введите вопрос",
                Modifier.fillMaxWidth(),
                leading = null,
                capitalize = true,
            )
            Text(
                "Варианты ответов",
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary,
                modifier = Modifier.padding(start = Spacing.l, top = Spacing.s),
            )
            answers.forEachIndexed { i, a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillTextField(
                        a,
                        { v -> answers = answers.toMutableList().also { it[i] = v } },
                        "Введите ответ",
                        Modifier.weight(1f),
                        leading = null,
                        capitalize = true,
                    )
                    if (answers.size > ANSWERS_MIN) {
                        IconButton(onClick = { answers = answers.filterIndexed { j, _ -> j != i } }) {
                            Icon(AppIcons.Close, contentDescription = "Убрать ответ")
                        }
                    }
                }
            }
            if (answers.size < ANSWERS_MAX) {
                TextButton(onClick = { answers = answers + "" }) {
                    Icon(AppIcons.Add, contentDescription = null)
                    Text("Добавить ответ", maxLines = 1, modifier = Modifier.padding(start = Spacing.s))
                }
                Text(
                    "Можно добавить ещё ${ANSWERS_MAX - answers.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            GroupCard("Настройки опроса", Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.s)) {
                PollSwitch("Анонимный", anonymous) { anonymous = it }
                PollSwitch("Несколько ответов", multiple) { multiple = it }
                PollSwitch("Можно переголосовать", revote) { revote = it }
            }
            Button(
                onClick = { onCreate(question, answers, (if (anonymous) 1 else 0) or (if (multiple) 2 else 0) or (if (revote) 4 else 0)) },
                enabled = question.isNotBlank() && filled >= ANSWERS_MIN,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Создать") }
        }
    }
}

@Composable
private fun PollSwitch(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .groupRowDivider(Spacing.l)
            .toggleable(value = on, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.m))
        Switch(checked = on, onCheckedChange = null)
    }
}
