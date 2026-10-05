package com.bloo.bluelink.ui

import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import com.bloo.uicommon.blockPageSwipe

/**
 * The app's one text field. Anything else is the stock [OutlinedTextField]'s, and a caller that
 * really needs a different shape or colours can still pass them.
 */
@Composable
internal fun BlooTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    shape: Shape = FieldShape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.blockPageSwipe(),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = shape,
        colors = colors,
    )
}

/**
 * The app's one PIN field: masked, number keypad, and (for the app PIN) digits only and capped at
 * [maxDigits].
 */
@Composable
internal fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    label: String? = null,
    digitsOnly: Boolean = true,
    maxDigits: Int = com.bloo.bluelink.data.PinCrypto.PIN_MAX_DIGITS,
    revealed: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    colors: TextFieldColors = borderlessFieldColors(),
) {
    BlooTextField(
        value = value,
        onValueChange = { onValueChange(if (digitsOnly) it.take(maxDigits).filter(Char::isDigit) else it) },
        modifier = modifier,
        label = label?.let { { androidx.compose.material3.Text(it) } },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = true,
        colors = colors,
        visualTransformation = if (revealed) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
    )
}

/** The eye button that shows or hides a secret ([noun] is what it reveals, for TalkBack): password fields and secret rows. */
@Composable
internal fun RevealToggle(shown: Boolean, noun: String, onToggle: () -> Unit) {
    MorphIconButton(onClick = onToggle) {
        Icon(
            if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (shown) "Hide $noun" else "Show $noun",
            modifier = Modifier.size(20.dp),
        )
    }
}
