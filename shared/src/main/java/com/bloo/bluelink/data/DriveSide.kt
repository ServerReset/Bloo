package com.bloo.bluelink.data

import java.util.Locale

/**
 * Which side of the car the driver sits on.
 *
 * This exists for ONE reason: Bloo describes seats by where they physically
 * are ([ClimateRequest.seatFrontLeft] / [ClimateRequest.seatFrontRight]), and
 * some car APIs describe them by WHO SITS IN THEM (a "driver seat" and a
 * "passenger seat"). Translating between the two needs to know the drive side,
 * and getting it wrong swaps the two front seats: the driver's heat setting
 * lands on the empty passenger seat and vice versa.
 *
 * It has never come up before because every market Bloo supported was
 * left-hand drive -- the US and Canada backends both map the driver's seat
 * straight from `seatFrontLeft`, and are right to. Europe is the first region
 * that includes right-hand-drive markets.
 */
enum class DriveSide {
    LEFT,
    RIGHT,
    ;

    /** The letter Hyundai's CCS2 climate payload uses for `drvSeatLoc`. */
    val ccs2Code: String get() = if (this == RIGHT) "R" else "L"
}

/**
 * The right-hand-drive countries in and around Hyundai's Europe region, by
 * ISO 3166-1 alpha-2 code.
 *
 * Deliberately scoped to markets a European Bluelink account can plausibly
 * belong to rather than every RHD country on earth: Ireland and Malta and
 * Cyprus are EU member states, and the UK is the region's largest RHD market
 * and still served by Hyundai's EU endpoints after Brexit.
 */
private val RIGHT_HAND_DRIVE_COUNTRIES = setOf("GB", "IE", "MT", "CY")

/**
 * The drive side implied by an ISO country code, defaulting to [DriveSide.LEFT].
 *
 * Defaulting left is the safe direction on two counts: it is the overwhelming
 * majority of the region, and it is the value the payload already carried, so
 * an unrecognised or absent country behaves exactly as it did before.
 */
fun driveSideFor(countryCode: String?): DriveSide =
    if (countryCode?.uppercase(Locale.US) in RIGHT_HAND_DRIVE_COUNTRIES) {
        DriveSide.RIGHT
    } else {
        DriveSide.LEFT
    }

/**
 * The drive side to assume for this device.
 *
 * Inferred from the phone's region, which is a heuristic and worth being honest
 * about: a British owner who has their phone set to US English gets LEFT and
 * their two front seats swapped. The alternatives were worse, though. The CCS2
 * vehicle payload has no field identified as the drive side, and guessing a
 * wire path that cannot be verified against a real car is how you ship
 * something that looks right and silently is not. A settings toggle would be
 * exact, but it asks every user in the region to answer a question almost none
 * of them should have to think about.
 *
 * So: infer, default safely, and keep the whole thing behind one function that
 * a real signal can replace without touching the payload code.
 */
fun deviceDriveSide(): DriveSide = driveSideFor(Locale.getDefault().country)

/**
 * The countries Hyundai's Europe region serves, as the lower-case codes its
 * IDP login expects.
 *
 * The EU/EEA plus the UK and the non-EU European markets Bluelink covers. It
 * is a fixed list rather than "any country" on purpose -- see [euLoginCountry]
 * for what happens to anything outside it.
 */
private val EUROPE_LOGIN_COUNTRIES = setOf(
    "at", "be", "bg", "ch", "cy", "cz", "de", "dk", "ee", "es", "fi", "fr",
    "gb", "gr", "hr", "hu", "ie", "is", "it", "lt", "lu", "lv", "mt", "nl",
    "no", "pl", "pt", "ro", "se", "si", "sk",
)

/** Germany, the value the EU sign-in URL carried for every user before this. */
private const val DEFAULT_EU_LOGIN_COUNTRY = "de"

/**
 * The `country` to send on the Europe OAuth authorize URL.
 *
 * It was pinned to "de" for everyone, which is the one value guaranteed to be
 * right for exactly one market. Sending the user's own country is the obvious
 * improvement, but doing it naively could BREAK sign-ins that work today: a
 * German owner whose phone is set to US English would start sending "us" to a
 * European IDP that has no such market.
 *
 * So this only ever substitutes a country the region actually serves, and falls
 * back to "de" otherwise. That makes it safe in the direction that matters --
 * every user who signs in today still sends a country the endpoint accepts, and
 * users in the other thirty markets now send their own instead of Germany's.
 */
fun euLoginCountry(countryCode: String? = Locale.getDefault().country): String {
    val code = countryCode?.lowercase(Locale.US).orEmpty()
    return if (code in EUROPE_LOGIN_COUNTRIES) code else DEFAULT_EU_LOGIN_COUNTRY
}
