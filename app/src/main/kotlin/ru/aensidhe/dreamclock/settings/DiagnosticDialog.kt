package ru.aensidhe.dreamclock.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.aensidhe.dreamclock.R

private const val CLIP_LABEL = "dreamclock-diagnostic"
private val TEXT_MAX_HEIGHT = 320.dp

/**
 * Shows [text] until the user dismisses it, with a button that puts the untruncated text on the
 * system clipboard. Click-outside dismissal is off on purpose: it is easy to trigger by accident
 * on a D-pad and the text is the whole point of the dialog.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DiagnosticDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.diagnostic_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = TEXT_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (copied) Text(stringResource(R.string.diagnostic_copied))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            context
                                .getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
                            copied = true
                        },
                    ) { Text(stringResource(R.string.action_copy)) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocus),
                    ) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}
