package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Matching an account car to a bundled picture: the model and the paint must both agree, or no picture is chosen. */
class CarPictureMatchTest {
    private fun pic(group: String, colour: String) = CarPicture(group, colour, "file:///android_asset/cars/${group}-$colour.webp")

    private val pictures = listOf(
        pic("IONIQ 5", "Atlas White"), pic("IONIQ 5", "Atlas White Matte"), pic("IONIQ 5", "Cyber Grey"),
        pic("IONIQ 5 N", "Cyber Grey"), pic("IONIQ 5 (original)", "Cyber Grey"),
        pic("EV6", "Runway Red"), pic("IONIQ 9", "Abyss Black"),
    )

    @Test fun anExactColourOnTheRightModel() {
        assertEquals("Atlas White", matchCarPicture("2024 IONIQ 5", "Daisy", "Atlas White", pictures)?.colour)
        assertEquals("EV6", matchCarPicture("2023 EV6", "Red", "runway red", pictures)?.group)
    }

    @Test fun thePlainColourBeatsItsMatteTwinUnlessTheCarSaysMatte() {
        assertEquals("Atlas White", matchCarPicture("2024 IONIQ 5", "", "Atlas White", pictures)?.colour)
        assertEquals("Atlas White Matte", matchCarPicture("2024 IONIQ 5", "", "Atlas White Matte", pictures)?.colour)
    }

    @Test fun theNVariantNeedsAnNCar() {
        assertEquals("IONIQ 5 N", matchCarPicture("2025 IONIQ 5 N", "", "Cyber Grey", pictures)?.group)
        assertEquals("IONIQ 5", matchCarPicture("2025 IONIQ 5", "", "Cyber Grey", pictures)?.group)
    }

    @Test fun aLooserNameStillFindsItsPaintWhenOnlyOneFits() {
        val set = pictures + pic("IONIQ 5", "Glacial White Pearl") + pic("IONIQ 5", "Digital Teal")
        assertEquals("Glacial White Pearl", matchCarPicture("2024 IONIQ 5", "", "Glacial White", set)?.colour)
        assertEquals("Digital Teal", matchCarPicture("2024 IONIQ 5", "", "Digital Teal Metallic", set)?.colour)
    }

    @Test fun aBareColourFamilyPicksTheOnlyPaintOfThatFamilyOnTheModel() {
        val set = pictures + pic("IONIQ 5", "Lucid Blue") + pic("IONIQ 5 (original)", "Lucid Blue") + pic("IONIQ 5 N", "Performance Blue Matte")
        assertEquals("Lucid Blue", matchCarPicture("2023 IONIQ 5", "Daisy", "BLUE", set)?.colour)
        assertEquals("IONIQ 5", matchCarPicture("2023 IONIQ 5", "Daisy", "BLUE", set)?.group)
    }

    @Test fun aBareColourFamilyWithTwoPaintsOnTheModelChoosesNothing() {
        val set = pictures + pic("IONIQ 6", "Biophilic Blue") + pic("IONIQ 6", "Transmission Blue")
        assertNull(matchCarPicture("2023 IONIQ 6", "Daisy", "BLUE", set))
    }

    @Test fun twoPaintsThatFitEquallyChooseNothing() {
        val set = pictures + pic("IONIQ 5", "Gravity Gold Matte") + pic("IONIQ 5", "Gravity Silver Metal")
        assertNull(matchCarPicture("2024 IONIQ 5", "", "Gravity Gold Silver", set))
    }

    @Test fun noGuessWithoutAColourOrWithAModelThatIsNotBundled() {
        assertNull(matchCarPicture("2024 IONIQ 5", "", null, pictures))
        assertNull(matchCarPicture("2024 IONIQ 5", "", "Hot Pink", pictures))
        assertNull(matchCarPicture("2024 Kona", "", "Atlas White", pictures))
    }
}
