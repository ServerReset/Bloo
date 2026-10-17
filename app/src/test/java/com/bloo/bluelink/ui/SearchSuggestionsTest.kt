package com.bloo.bluelink.ui

import com.bloo.bluelink.data.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The empty search's example settings must each find themselves; they used to be typed literals that matched nothing. */
class SearchSuggestionsTest {
    @Test fun everySuggestedSettingHasACatalogTitle() {
        SuggestedSettingKeys.forEach { key -> assertNotNull(SettingsCatalog.titleOf(key), "no catalog title for $key") }
    }

    @Test fun everySuggestedSettingFindsItself() {
        SuggestedSettingKeys.forEach { key ->
            val title = SettingsCatalog.titleOf(key)!!
            val entry = SearchEntry(title, "$title ${SettingsCatalog.keywordsOf(key)}".lowercase()) {}
            assertNotNull(searchScore(searchTokens(title), entry, fuzzy = false), "searching \"$title\" finds nothing for $key")
        }
    }

    @Test fun everySuggestedSettingIsOfferedInSimpleMode() {
        SuggestedSettingKeys.forEach { key ->
            assertTrue(isSettingAvailableInMode(SettingsCatalog.titleOf(key)!!, "simple"), "$key is hidden in simple mode")
        }
    }
}
