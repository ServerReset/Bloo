package com.bloo.bluelink.data

import java.util.Locale

/** Which side of the car the driver sits on. */
enum class DriveSide {
    LEFT,
    RIGHT,
    ;

    /** The letter Hyundai's CCS2 climate payload uses for `drvSeatLoc`. */
    val ccs2Code: String get() = if (this == RIGHT) "R" else "L"
}

/**
 * The right-hand-drive countries in and around Hyundai's Europe region, by ISO 3166-1 alpha-2 code.
 */
private val RIGHT_HAND_DRIVE_COUNTRIES = setOf("GB", "IE", "MT", "CY")

/** The drive side implied by an ISO country code, defaulting to [DriveSide.LEFT]. */
fun driveSideFor(countryCode: String?): DriveSide =
    if (countryCode?.uppercase(Locale.US) in RIGHT_HAND_DRIVE_COUNTRIES) {
        DriveSide.RIGHT
    } else {
        DriveSide.LEFT
    }

/**
 * The drive side to assume for this device. Inferred from the phone's region, which is a heuristic
 * and worth being honest about: a British owner who has their phone set to US English gets LEFT and
 * their two front seats swapped.
 */
fun deviceDriveSide(): DriveSide = driveSideFor(Locale.getDefault().country)

/**
 * The countries Hyundai's Europe region serves, as the lower-case codes its IDP login expects. The
 * EU/EEA plus the UK and the non-EU European markets Bluelink covers.
 */
private val EUROPE_LOGIN_COUNTRIES = setOf(
    "at", "be", "bg", "ch", "cy", "cz", "de", "dk", "ee", "es", "fi", "fr",
    "gb", "gr", "hr", "hu", "ie", "is", "it", "lt", "lu", "lv", "mt", "nl",
    "no", "pl", "pt", "ro", "se", "si", "sk",
)

private const val DEFAULT_EU_LOGIN_COUNTRY = "de"

/** The `country` to send on the Europe OAuth authorize URL. */
fun euLoginCountry(countryCode: String? = Locale.getDefault().country): String {
    val code = countryCode?.lowercase(Locale.US).orEmpty()
    return if (code in EUROPE_LOGIN_COUNTRIES) code else DEFAULT_EU_LOGIN_COUNTRY
}
