package com.bloo.bluelink.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.TitleSubtitle
import com.bloo.uicommon.secondaryTextColor

/*
 * The parts the full-screen sheets (charging history, chargers nearby, the trip planner, the car store) are built from, so each one
 * loads, lists and reports the same way, in the app's own components rather than stock Material ones.
 */

/** What a sheet shows for a list it fetched: the [error] if there is one, a spinner while [items] is null, [empty] for none, else [content]. */
@Composable
internal fun <T> SheetLoad(items: List<T>?, error: String?, empty: String, content: @Composable (List<T>) -> Unit) {
    when {
        error != null -> SheetNote(error, isError = true)
        items == null -> SheetLoading()
        items.isEmpty() -> SheetNote(empty)
        else -> content(items)
    }
}

/** A sheet's spinner, centred in the space it has. */
@Composable
internal fun SheetLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = GapSection), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

/** A line of explanation in a sheet; [isError] shows it in the error colour. */
@Composable
internal fun SheetNote(text: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    BodySmallText(
        text,
        modifier = modifier.padding(vertical = GapRow),
        color = if (isError) MaterialTheme.colorScheme.error else secondaryTextColor(),
    )
}

/** A sheet's scrolling list, its entries [GapRow] apart. */
@Composable
internal fun ColumnScope.SheetList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GapRow),
        content = content,
    )
}

/**
 * One entry in a sheet's list: [title] over its [lines] of detail (blank ones dropped), on the shared inner glass panel. With [onClick]
 * the whole panel is the tap target.
 */
@Composable
internal fun SheetRow(title: String, lines: List<String?>, onClick: (() -> Unit)? = null) {
    val detail = lines.filterNotNull().filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(StandardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .innerGlassPanel(GapGroup),
    ) {
        TitleSubtitle(title, detail)
    }
}
