package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct JVM pins for the settings-search matching/ranking helpers in
 * [SettingsIndex.kt] (withinOneEdit, hasFuzzyWord, hasWordStarting,
 * expandToken, searchScore).
 *
 * These are the helpers a false positive is expensive: the search feeds
 * [TileCommandRunner] for command queries, and the ranking helpers feed
 * [SettingsSearchResults] which a user reads at a glance. Both were private to
 * SettingsScreen.kt for most of their lives -- documented by eye, never
 * pinned -- and both are pure string math with no Compose dependency, so they
 * test exactly like PinLock.kt's arithmetic does.
 */
class SettingsSearchIndexTest {

    // --- withinOneEdit ------------------------------------------------------

    @Test
    fun identical_isWithinOneEdit() {
        assertTrue(withinOneEdit("charge", "charge"))
        assertTrue(withinOneEdit("", ""))
    }

    @Test
    fun singleInsertion_isWithinOneEdit() {
        assertTrue(withinOneEdit("charge", "charges"))
        assertTrue(withinOneEdit("charge", "charget"))
        assertTrue(withinOneEdit("thermostat", "thermostatt"))
    }

    @Test
    fun singleDeletion_isWithinOneEdit() {
        assertTrue(withinOneEdit("charges", "charge"))
        assertTrue(withinOneEdit("carge", "charge"))
    }

    @Test
    fun singleSubstitution_isWithinOneEdit() {
        assertTrue(withinOneEdit("car", "cat"))
        assertTrue(withinOneEdit("charge", "charke"))
        assertTrue(withinOneEdit("range", "rabge"))
    }

    @Test
    fun twoOrMoreEdits_areNot() {
        assertFalse(withinOneEdit("charge", "charke x"))   // 2+ edits
        assertFalse(withinOneEdit("car", "carpet"))        // 3 insertions
        assertFalse(withinOneEdit("haptic", "static"))     // the bug guard in its own doc
        // ("", "a") is a single insertion -- genuinely within one edit, and the
        // length-gap == 1 path exists exactly to allow it.
        assertTrue(withinOneEdit("", "a"))
    }

    @Test
    fun lengthGapOfOne_isTheMostItTolerates() {
        assertFalse(withinOneEdit("ab", "abcd")) // 2-gap
        assertTrue(withinOneEdit("ab", "abc"))   // exactly 1
    }

    // --- hasFuzzyWord -------------------------------------------------------

    @Test
    fun fuzzyWord_matchesWholeWordOnly() {
        // "charge" must not fuzzy-match a substring of a longer word ("carshield")
        assertFalse(hasFuzzyWord("carshield cover", "charge"))
        assertTrue(hasFuzzyWord("battery charge", "charge"))
    }

    @Test
    fun fuzzyWord_matchesFirstLastAndMiddleWords() {
        // Exact words hit trivially; the interesting cases are genuine
        // ONE-edit variants: "charges" (s appended) and "charfet" (g -> f).
        assertTrue(hasFuzzyWord("charges and numbers", "charge"))
        assertTrue(hasFuzzyWord("tap start charge", "charge"))
        assertTrue(hasFuzzyWord("abort charjge now", "charge"))
        // "charging" is a TWO-edit gap -- must NOT fuzzy-match.
        assertFalse(hasFuzzyWord("charging is on", "charge"))
    }

    @Test
    fun fuzzyWord_requiresFourPlusCharsAtCallSiteButHelperIsPure() {
        // The >= 4 length gate lives in searchScore, not here; the helper
        // itself just does the word-edit test. One-edit variance on "odometr"
        // (a single substitution of "odometer"); note "odomtr" is a TWO-edit
        // deletion gap and must NOT fuzzy-match.
        assertTrue(hasFuzzyWord("odometer reading", "odometr"))
        assertFalse(hasFuzzyWord("odometer reading", "odomtr"))
    }

    // --- hasWordStarting ----------------------------------------------------

    @Test
    fun wordStarting_isZeroWidthGuard() {
        assertTrue(hasWordStarting("charge limit", "ch"))
        assertFalse(hasWordStarting("battery charge limit", "carge")) // mid-word still starts
        assertTrue(hasWordStarting("car", "ca"))
        assertTrue(hasWordStarting("vehicle car", "car"))
        // Must NOT match a substring mid-word ("one" inside "bone").
        assertFalse(hasWordStarting("bone", "one"))
    }

    // --- expandToken --------------------------------------------------------

    @Test
    fun expandToken_unmappedIsItself() {
        assertEquals(listOf("zebra"), expandToken("zebra"))
    }

    @Test
    fun expandToken_synonymsAttachAfterLiteral() {
        val forms = expandToken("vibrate")
        assertEquals("vibrate", forms.first())
        assertTrue("haptic" in forms, "expected haptic in $forms")
        // Synonym expansion lands with penalty in searchScore; ordering here
        // defines which form is "the literal".
        assertEquals(listOf("vibrate", "haptic"), forms)
    }

    @Test
    fun expandToken_chainProof() {
        // Synonyms are written token -> app vocabulary; expanding a synonym
        // itself must not recurse (token "night" maps to theme/dark, and
        // "dark" maps to theme/night -- no infinite loop, capped at one hop).
        val forms = expandToken("dark")
        assertEquals(listOf("dark", "theme", "night"), forms)
    }

    // --- searchScore --------------------------------------------------------

    private val entry = { title: String, hay: String -> SearchEntry(title, hay) {} }

    @Test
    fun tokensAreAndedAnyTokenFailureIsNull() {
        assertNotNull(searchScore(listOf("charge"), entry("Charge limit", "charge charging limit"), fuzzy = false))
        assertNull(searchScore(listOf("charge"), entry("Range", "dte range"), fuzzy = false))
        assertNull(searchScore(listOf("odometer", "plate"), entry("Odometer", "odometer"), fuzzy = false))
    }

    @Test
    fun titleMatchOutranksHaystackMatch() {
        val titleMatch = searchScore(listOf("charge"), entry("Charge limit", "battery"), fuzzy = false)!!
        val hayMatch = searchScore(listOf("charge"), entry("Grease", "charging port"), fuzzy = false)!!
        assertTrue(titleMatch > hayMatch, "title $titleMatch > haystack $hayMatch")
    }

    @Test
    fun synonymHitsArePenalizedButMatch() {
        // "vibrate" hits the title's word-start; "buzz" only reaches the entry
        // through its synonym expansion ("buzz" -> "haptic"), which loads 30
        // penalty. Same entry, same haystack, both must match.
        val e = entry("Vibration", "vibrate haptic feedback")
        val literal = searchScore(listOf("vibrate"), e, fuzzy = false)!!
        val synonym = searchScore(listOf("buzz"), e, fuzzy = false)!!
        assertNotNull(synonym)
        assertTrue(literal > synonym, "literal $literal vs synonym $synonym")
    }

    @Test
    fun fuzzyModeFindsNearMisses() {
        assertNotNull(searchScore(listOf("odometr"), entry("Odometer", "odometer"), fuzzy = true))
        assertNull(searchScore(listOf("odometr"), entry("Odometer", "odometer"), fuzzy = false))
    }

    @Test
    fun shorterTitleWinsTies() {
        val short = searchScore(listOf("charge limit"), entry("Charge limit", "charge limit"), fuzzy = false)!!
        val long = searchScore(listOf("charge limit"), entry("Charge limit notification threshold", "charge limit notification"), fuzzy = false)!!
        assertTrue(short > long, "$short vs $long")
    }
}
