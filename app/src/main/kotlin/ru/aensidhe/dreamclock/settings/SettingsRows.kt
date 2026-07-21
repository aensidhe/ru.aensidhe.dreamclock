package ru.aensidhe.dreamclock.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Fits three digits at body text size; the largest configured stepper maximum is 60. */
private val STEPPER_VALUE_WIDTH = 48.dp

/** Clamps a stepper value to `[min, max]`. Pure so it can be unit-tested without Compose. */
fun clampStepper(
    value: Int,
    min: Int,
    max: Int,
): Int = value.coerceIn(min, max)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    downFocus: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    onChange: (Int) -> Unit,
) {
    // Set on the buttons themselves: an override on the enclosing Row does not reach them, and
    // without it a vertical press falls back to the geometrically nearest focusable. The steppers
    // sit at the right edge while the buttons above and below sit at the left, so that fallback
    // picks the wrong target.
    val overrides =
        Modifier.focusProperties {
            if (downFocus != null) down = downFocus
            if (upFocus != null) up = upFocus
        }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onChange(clampStepper(value - step, min, max)) },
                modifier = overrides,
            ) { Text("-") }
            Text(
                value.toString(),
                Modifier.width(STEPPER_VALUE_WIDTH),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { onChange(clampStepper(value + step, min, max)) },
                modifier = overrides,
            ) { Text("+") }
        }
    }
}

@Composable
fun TextFieldRow(
    label: String,
    value: String,
    isSecret: Boolean,
    downFocus: FocusRequester? = null,
    onCommit: (String) -> Unit,
) {
    var text by
        if (isSecret) {
            remember(value) { mutableStateOf(value) }
        } else {
            rememberSaveable(value) { mutableStateOf(value) }
        }
    var hadFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    ComposeMaterialTheme(colorScheme = composeDarkColorScheme()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { androidx.compose.material3.Text(label) },
            singleLine = true,
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onCommit(text)
                        // moveFocus, not clearFocus: clearing returns focus to the root, which
                        // resolves upwards. Down honours the override below when there is one.
                        keyboard?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(
                        if (downFocus == null) Modifier else Modifier.focusProperties { down = downFocus },
                    ).onFocusChanged { focusState ->
                        if (hadFocus && !focusState.isFocused) onCommit(text)
                        hadFocus = focusState.isFocused
                    },
        )
    }
}
