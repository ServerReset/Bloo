package com.bloo.bluelink.ui

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
 * The app's one text field. Every field is [FieldShape] and keeps horizontal drags to itself (so
 * selecting text or moving the caret never pages the app), which used to be repeated at each of
 * twenty call sites. Anything else is the stock [OutlinedTextField]'s, and a caller that really
 * needs a different shape or colours can still pass them.
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
 * [maxDigits]. The lock screen, the change-PIN dialog, onboarding's set-a-PIN pair, the account card and
 * sign-in all enter a PIN, and each had hand-written the same six lines of masking and keyboard options.
 * [revealed] unmasks it (sign-in's eye toggle); [onDone] makes the keypad's Done key act.
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
