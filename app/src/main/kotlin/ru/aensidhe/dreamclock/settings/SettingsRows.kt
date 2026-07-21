package ru.aensidhe.dreamclock.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme as composeDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Clamps a stepper value to `[min, max]`. Pure so it can be unit-tested without Compose. */
fun clampStepper(
    value: Int,
    min: Int,
    max: Int,
): Int = value.coerceIn(min, max)

/**
 * A row showing [label] and the current [value], with two buttons that nudge the value down/up
 * by [step] (clamped to `[min, max]`) and report the result via [onChange]. Only the two buttons
 * are D-pad-focusable — the row itself is a plain layout, not a focusable/clickable list item.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onChange(clampStepper(value - step, min, max)) }) { Text("-") }
            Text(value.toString(), Modifier.padding(horizontal = 8.dp))
            Button(onClick = { onChange(clampStepper(value + step, min, max)) }) { Text("+") }
        }
    }
}

/**
 * A D-pad-focusable text field for [label]. Masks input with dots when [isSecret]. Commits the
 * current text via [onCommit] on the keyboard Done action and when focus is lost.
 */
@Composable
fun TextFieldRow(
    label: String,
    value: String,
    isSecret: Boolean,
    onCommit: (String) -> Unit,
) {
    var text by rememberSaveable(value) { mutableStateOf(value) }
    var hadFocus by remember { mutableStateOf(false) }
    ComposeMaterialTheme(colorScheme = composeDarkColorScheme()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { androidx.compose.material3.Text(label) },
            singleLine = true,
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
            modifier =
                Modifier.onFocusChanged { focusState ->
                    if (hadFocus && !focusState.isFocused) onCommit(text)
                    hadFocus = focusState.isFocused
                },
        )
    }
}
