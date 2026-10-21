package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable

/** The expand control for pebbles with no action button: the chevron on its own, a single fully round button. */
@Composable
internal fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    /** Reports the chevron's held-down state, so the pebble card can square its own shape with it. */
    onPressChange: ((Boolean) -> Unit)? = null,
) = SplitExpandButton(action = null, expanded = expanded, onToggle = onToggle, onChevronPressChange = onPressChange)
