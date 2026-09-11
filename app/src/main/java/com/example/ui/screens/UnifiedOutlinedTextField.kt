package com.example.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.util.Formatters

/**
 * Single input implementation used throughout the application.
 * The Arabic UI stays RTL, but numeric characters are always English 0-9.
 * The TextFieldValue is intentionally remembered without using `value` as
 * the remember key; otherwise every typed character recreates the value and
 * resets the cursor/selection, which causes Arabic text to be entered in the
 * wrong direction.
 */
@Composable
fun UnifiedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(7.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF0E6B38),
        unfocusedBorderColor = Color(0xFFD4DDD7),
        cursorColor = Color(0xFF0E6B38)
    )
) {
    val normalizedValue = remember(value) { Formatters.englishDigits(value) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(normalizedValue)) }
    var wasFocused by remember { mutableStateOf(false) }

    // Synchronize only when the parent really changes the text externally.
    // Do not recreate TextFieldValue on every keystroke: that destroys the
    // IME-provided cursor position and is the source of the previous bug.
    LaunchedEffect(normalizedValue) {
        if (fieldValue.text != normalizedValue) {
            val safeSelection = fieldValue.selection.end.coerceIn(0, normalizedValue.length)
            fieldValue = TextFieldValue(
                text = normalizedValue,
                selection = TextRange(safeSelection)
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                val normalizedText = Formatters.englishDigits(next.text)
                val delta = normalizedText.length - next.text.length
                val normalizedSelection = TextRange(
                    (next.selection.start + delta).coerceIn(0, normalizedText.length),
                    (next.selection.end + delta).coerceIn(0, normalizedText.length)
                )
                fieldValue = next.copy(
                    text = normalizedText,
                    selection = normalizedSelection
                )
                onValueChange(normalizedText)
            },
            modifier = modifier.onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    wasFocused = true
                    if (fieldValue.text.isNotEmpty()) {
                        fieldValue = fieldValue.copy(
                            selection = TextRange(0, fieldValue.text.length)
                        )
                    }
                } else if (!state.isFocused) {
                    wasFocused = false
                }
            },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = colors
        )
    }
}
