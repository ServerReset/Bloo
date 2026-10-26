package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reading a car's paint colour and trim off whatever an account lists, under whatever the brand calls them. */
class VehiclePaintTest {
    private fun paint(json: String) = paintOf(Json.parseToJsonElement(json).jsonObject)

    @Test fun readsAFlatColourAndTrim() {
        val p = paint("""{"modelName":"IONIQ 5","exteriorColor":"Atlas White","trim":"Preferred"}""")
        assertEquals("Atlas White", p.colour)
        assertEquals("Preferred", p.trim)
    }

    @Test fun readsOneLevelDownAsEuropeNestsIt() {
        val p = paint("""{"vehicleName":"EV6","detailInfo":{"outColor":"Runway Red","inColor":"Black"}}""")
        assertEquals("Runway Red", p.colour)
    }

    @Test fun aReadableNameBeatsACode() {
        val p = paint("""{"colorCode":"SWP","colorName":"Snow White Pearl"}""")
        assertEquals("Snow White Pearl", p.colour)
    }

    @Test fun aCodeIsKeptWhenThereIsNoName() {
        assertEquals("SWP", paint("""{"colorCode":"SWP"}""").colour)
    }

    @Test fun anInteriorColourIsNotThePaint() {
        assertNull(paint("""{"interiorColor":"Black","inColor":"Grey"}""").colour)
    }

    @Test fun hyundaiUsListsAGenericColourAndNoModelNameOnlyHints() {
        val p = paint("""{"nickName":"Daisy","color":"BLUE","interiorColor":"BLACK","sapColorCode":"U4G","hmaModel":"IONIQ 5","series":"SEL","modelCode":"NE","trim":"SEL","modelYear":"2023"}""")
        assertEquals("BLUE", p.colour)
        assertEquals("SEL", p.trim)
        assertEquals("IONIQ 5", p.model)
    }

    @Test fun aModelNameBeatsTheOtherHints() {
        assertEquals("IONIQ 6", paint("""{"modelName":"IONIQ 6","hmaModel":"X","series":"Y"}""").model)
    }

    @Test fun aBareYearGetsTheHintAsItsModelAndAFullNameIsLeftAlone() {
        assertEquals("2023 IONIQ 5", modelWithName("2023", "IONIQ 5"))
        assertEquals("2023 IONIQ 5", modelWithName("2023 IONIQ 5", "SEL"))
        assertEquals("2023", modelWithName("2023", null))
    }

    @Test fun blankOrNonTextFieldsAreIgnored() {
        val p = paint("""{"exteriorColor":"  ","color":12,"trim":null}""")
        assertNull(p.colour)
        assertNull(p.trim)
    }
}
