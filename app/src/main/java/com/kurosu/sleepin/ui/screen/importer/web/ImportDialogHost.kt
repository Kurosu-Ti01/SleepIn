package com.kurosu.sleepin.ui.screen.importer.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Renders the dialog a script is currently waiting on.
 *
 * One dialog at a time, because a page can ask for several in a row and the ViewModel is holding the
 * rest in its queue: showing only the head means every request is eventually answered or explicitly
 * cancelled, in the order the script made them.
 *
 * @param onAnswer called once with the request id and what the user did. The id comes from the same
 *   request that produced the button, so a second answer from one dialog cannot be mistaken for a reply
 *   to whatever the script asked next.
 * @param onValidate runs a prompt's validation function in the page and returns the message to show,
 *   or null when the input is acceptable.
 */
@Composable
fun ImportDialogHost(
    request: ImportWebRequest.Dialog?,
    onAnswer: (callId: String, answer: ImportDialogAnswer) -> Unit,
    onValidate: suspend (validateName: String, input: String) -> String?
) {
    if (request == null) return
    val callId = request.callId

    when (request) {
        is ImportWebRequest.Alert -> AlertDialog(
            onDismissRequest = { onAnswer(callId, ImportDialogAnswer.Cancelled) },
            title = { Text(request.title) },
            text = { Text(request.message) },
            confirmButton = {
                TextButton(onClick = { onAnswer(callId, ImportDialogAnswer.Confirmed) }) {
                    Text(request.confirmText)
                }
            }
        )

        is ImportWebRequest.Prompt -> PromptDialog(
            request = request,
            onAnswer = { answer -> onAnswer(callId, answer) },
            onValidate = onValidate
        )

        is ImportWebRequest.Choose -> ChooseDialog(
            request = request,
            onAnswer = { answer -> onAnswer(callId, answer) }
        )
    }
}

/**
 * Text input dialog.
 *
 * Validation happens in the page, so the dialog has to stay open while the answer comes back: a
 * rejected input shows the message the script returned and leaves the promise unsettled.
 */
@Composable
private fun PromptDialog(
    request: ImportWebRequest.Prompt,
    onAnswer: (ImportDialogAnswer) -> Unit,
    onValidate: suspend (validateName: String, input: String) -> String?
) {
    // Keyed on the request so a new prompt never inherits the previous one's text or error.
    var input by remember(request.callId) { mutableStateOf(request.initialValue) }
    var error by remember(request.callId) { mutableStateOf<String?>(null) }
    var isValidating by remember(request.callId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { onAnswer(ImportDialogAnswer.Cancelled) },
        title = { Text(request.title) },
        text = {
            Column {
                if (request.hint.isNotBlank()) {
                    Text(
                        text = request.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    enabled = !isValidating,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isValidating,
                onClick = {
                    val validateName = request.validateName
                    if (validateName == null) {
                        onAnswer(ImportDialogAnswer.Text(input))
                        return@TextButton
                    }
                    isValidating = true
                    scope.launch {
                        val message = onValidate(validateName, input)
                        isValidating = false
                        if (message == null) {
                            onAnswer(ImportDialogAnswer.Text(input))
                        } else {
                            error = message
                        }
                    }
                }
            ) {
                Text(if (isValidating) "校验中…" else "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(ImportDialogAnswer.Cancelled) }) { Text("取消") }
        }
    )
}

/**
 * Single-choice dialog.
 *
 * The rows handle the click and the radio buttons are inert: two live click targets inside one another
 * would fire the answer twice, which the ViewModel tolerates but which would also look like a
 * double-tap to the user.
 */
@Composable
private fun ChooseDialog(
    request: ImportWebRequest.Choose,
    onAnswer: (ImportDialogAnswer) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onAnswer(ImportDialogAnswer.Cancelled) },
        title = { Text(request.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                request.options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAnswer(ImportDialogAnswer.Option(index)) }
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(selected = index == request.defaultIndex, onClick = null)
                        Text(
                            text = option,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(ImportDialogAnswer.Option(request.defaultIndex)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(ImportDialogAnswer.Cancelled) }) { Text("取消") }
        }
    )
}
