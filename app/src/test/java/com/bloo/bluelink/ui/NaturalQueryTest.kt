package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaturalQueryTest {
    @Test
    fun politeScaffoldingIsDropped() {
        assertEquals(listOf("text", "bigger"), searchTokens("how do I make the text bigger?"))
        assertEquals(listOf("vibration"), searchTokens("please turn off the vibrations"))
    }

    @Test
    fun knownPhrasesBecomeIndexVocabulary() {
        assertTrue("theme" in searchTokens("turn on dark mode"))
        assertTrue("haptic" in searchTokens("stop the buzzing"))
        assertTrue("location" in searchTokens("where is my car"))
        assertTrue("scale" in searchTokens("the text is too small"))
    }

    @Test
    fun aQueryOfOnlyScaffoldingStillSearches() {
        assertEquals(listOf("off"), searchTokens("off"))
        assertEquals(listOf("update"), searchTokens("update"))
    }

    @Test
    fun pluralsTrimButSynonymKeysStay() {
        assertEquals(listOf("window"), searchTokens("windows"))
        // "seats" is itself a synonym key, so it keeps its form.
        assertEquals(listOf("seats"), searchTokens("seats"))
        // "alerts" is itself a synonym key, so it keeps its form.
        assertEquals(listOf("alerts"), searchTokens("alerts"))
    }

    @Test
    fun registriesHaveNoDuplicateTitles() {
        val titles = ToggleSettings.map { it.title } + RangeSettings.map { it.title } + ChoiceSettings.map { it.title }
        assertEquals(titles.size, titles.toSet().size, "duplicate title across the settings registries: $titles")
    }

    @Test
    fun everyRegisteredSettingCanBeFoundByItsOwnTitleWords() {
        val specs = RangeSettings + ChoiceSettings
        for (s in specs) {
            val entry = SearchEntry(s.title, "${s.title} ${s.keywords} ${s.phrases}".lowercase()) {}
            val tokens = searchTokens(s.title)
            assertTrue(searchScore(tokens, entry, fuzzy = false) != null, "'${s.title}' cannot be found by its own title: $tokens")
        }
    }
}
