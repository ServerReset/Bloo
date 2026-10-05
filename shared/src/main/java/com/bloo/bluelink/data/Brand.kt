package com.bloo.bluelink.data

/** A supported US telematics brand. */
enum class Brand(
    val code: String,
    val baseUrl: String,
    val host: String,
    val clientId: String,
    val clientSecret: String,
    val label: String,
) {
    HYUNDAI(
        code = "H",
        baseUrl = "https://api.telematics.hyundaiusa.com",
        host = "api.telematics.hyundaiusa.com",
        clientId = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920",
        clientSecret = "v558o935-6nne-423i-baa8",
        label = "Hyundai",
    ),
    GENESIS(
        code = "G",
        baseUrl = "https://api.genesis.telematics.hyundaiusa.com",
        host = "api.genesis.telematics.hyundaiusa.com",
        clientId = "3020afa2-30ff-412a-aa51-d28fbe901e10",
        clientSecret = "KUy49XxPzLpLuoK0xhBC77W6VXhmtQR9iQhmIFjjoY4IpxsV",
        label = "Genesis",
    ),

    /**
     * Kia US runs on a completely different backend (api.owners.kia.com, the "Kia Connect" API)
     * served by [KiaUsaApi]/[KiaRepository] rather than the Hyundai-shaped [BlueLinkApi];
     * [KiaUsaApi] reads its endpoint and client credentials from this entry.
     */
    KIA(
        code = "K",
        baseUrl = "https://api.owners.kia.com",
        host = "api.owners.kia.com",
        clientId = "SPACL716-APL",
        clientSecret = "sydnat-9kykci-Kuhtep-h5nK",
        label = "Kia",
    ),

    /**
     * Hyundai/Genesis/Kia Canada all run on one shared backend family (unrelated to both the US
     * Hyundai/Genesis "HATA" API and Kia's separate US backend), served by
     * [CanadaApi]/[CanadaRepository]: same client_id/client_secret and request shapes across all
     * three, differing only by host.
     */
    HYUNDAI_CA(
        // Multi-letter, distinct from HYUNDAI's plain "H": brandIndicator is our own internal tag
        // (repositoryFor/BlueLinkRepository/KiaRepository always overwrite whatever the wire sent
        // with brand.code, never read it back out of the raw API response), so it's safe to make
        // region-distinct here -- and it must be, since Brand.fromIndicator is how every command
        // path (repoFor(v), TileCommandRunner, CarCommandRunner) re-derives which backend/host a
        // saved Vehicle belongs to.
        code = "HCA",
        baseUrl = "https://mybluelink.ca/tods/api",
        host = "mybluelink.ca",
        clientId = "HATAHSPACA0232141ED9722C67715A0B",
        clientSecret = "CLISCR01AHSPA",
        label = "Hyundai (Canada)",
    ),
    GENESIS_CA(
        code = "GCA",
        baseUrl = "https://genesisconnect.ca/tods/api",
        host = "genesisconnect.ca",
        clientId = "HATAHSPACA0232141ED9722C67715A0B",
        clientSecret = "CLISCR01AHSPA",
        label = "Genesis (Canada)",
    ),
    KIA_CA(
        code = "KCA",
        baseUrl = "https://kiaconnect.ca/tods/api",
        host = "kiaconnect.ca",
        clientId = "HATAHSPACA0232141ED9722C67715A0B",
        clientSecret = "CLISCR01AHSPA",
        label = "Kia (Canada)",
    ),

    /** Hyundai Bluelink Europe on the CCAPI ("CCS2") platform, served by [EuApi]/[EuRepository]. */
    HYUNDAI_EU(
        code = "HEU",
        baseUrl = "https://prd.eu-ccapi.hyundai.com:8080",
        host = "prd.eu-ccapi.hyundai.com",
        clientId = "6d477c38-3ca4-4cf3-9557-2a1929a94654",
        // From hyundai_kia_connect_api KiaUvoApiEU.py (Hyundai EU).
        // base64("$clientId:$clientSecret") reproduces that project's hard-coded
        // BASIC_AUTHORIZATION token (see EuApi.login).
        clientSecret = "KUy49XxPzLpLuoK0xhBC77W6VXhmtQR9iQhmIFjjoY4IpxsV",
        label = "Hyundai (Europe)",
    );

    /**
     * False only for Kia US, whose commands aren't PIN-gated at all; every other brand (including
     * Canada, gated behind CanadaApi.pinAuth) needs the login form's PIN field filled in.
     */
    val requiresPin: Boolean get() = this != KIA

    /** Whether sign-in itself is blocked without a PIN. */
    val pinRequiredToSignIn: Boolean get() = requiresPin && !isEurope

    /**
     * True for the three Canada brands, which share [CanadaApi]/[CanadaRepository] rather than
     * [BlueLinkApi]/[KiaUsaApi].
     */
    val isCanada: Boolean get() = this == HYUNDAI_CA || this == GENESIS_CA || this == KIA_CA

    /**
     * Whether this brand can report AC/DC charge-limit targets, and therefore whether the editable
     * charge-limit controls are worth showing.
     */
    val supportsChargeLimits: Boolean get() = !isCanada

    /**
     * True for the Europe (CCAPI/CCS2) brands, served by [EuApi]/[EuRepository]. Only Hyundai EU
     * today; Kia/Genesis EU would join this check.
     */
    val isEurope: Boolean get() = this == HYUNDAI_EU

    /**
     * Whether a status refresh can tell us if remote climate is actually RUNNING
     * (`VehicleStatus.airCtrlOn`).
     */
    val reportsClimateState: Boolean get() = !isEurope

    /** Whether this brand's backend can return trip history at all. */
    val supportsTrips: Boolean get() = this == HYUNDAI || this == GENESIS

    companion object {
        /** The sign-in regions, as the keys the login pickers select by. */
        const val REGION_US = "US"
        const val REGION_CA = "CA"
        const val REGION_EU = "EU"

        /**
         * The brands offered for a sign-in region, in display order. Europe would have inherited
         * the same gap.
         */
        fun brandsForRegion(region: String): List<Brand> = when (region) {
            REGION_CA -> listOf(HYUNDAI_CA, GENESIS_CA, KIA_CA)
            REGION_EU -> listOf(HYUNDAI_EU)
            else -> listOf(HYUNDAI, GENESIS, KIA)
        }

        fun regionOf(brand: Brand): String = when {
            brand.isCanada -> REGION_CA
            brand.isEurope -> REGION_EU
            else -> REGION_US
        }

        /**
         * The brand's label without its region suffix, for a picker that already says which region
         * you are in. "Hyundai (Canada)" -> "Hyundai".
         */
        fun shortLabel(brand: Brand): String =
            brand.label.removeSuffix(" (Canada)").removeSuffix(" (Europe)")

        /**
         * Map a vehicle's brand indicator back to a [Brand] -- the exact reverse of the `code` each
         * brand stamps onto its own vehicles (see BlueLinkRepository.vehicles /
         * KiaRepository.toVehicle / this file's CA entries), so this is also how every command path
         * (repoFor(v), TileCommandRunner, CarCommandRunner) re-derives which backend/host a saved
         * [Vehicle] belongs to.
         */
        fun fromIndicator(indicator: String?): Brand = when {
            indicator == HYUNDAI_CA.code -> HYUNDAI_CA
            indicator == GENESIS_CA.code -> GENESIS_CA
            indicator == KIA_CA.code -> KIA_CA
            indicator == HYUNDAI_EU.code -> HYUNDAI_EU
            indicator.equals("G", ignoreCase = true) -> GENESIS
            indicator.equals("K", ignoreCase = true) -> KIA
            else -> HYUNDAI
        }
    }
}

/** The telematics brand a vehicle belongs to. */
val Vehicle.brand: Brand get() = Brand.fromIndicator(brandIndicator)

/** Brand-specific apps, sites and phone numbers — the single source of truth. */
data class BrandLinks(
    /** Play Store package of the official companion app. */
    val appPackage: String,
    /** Short app name ("Bluelink"), used as "<name> app" / "Open the <name> app". */
    val appName: String,
    val ownersUrl: String,
    val dealerLabel: String,
    val dealerUrl: String,
    val manualsUrl: String,
    /** Online service-appointment scheduler. */
    val serviceScheduleUrl: String,
    /** 24/7 roadside-assistance line, digits only. */
    val roadsidePhone: String,
    /** Connected-car content store (Features on Demand: themes, lighting…). */
    val storeUrl: String,
) {
    // Builds the Play Store listing URL on the fly from appPackage rather than storing it as its
    // own field, since it's always the same template per package id.
    val playStoreUrl: String get() = "https://play.google.com/store/apps/details?id=$appPackage"
}

val Brand.links: BrandLinks
    get() = when (this) {
        Brand.HYUNDAI -> BrandLinks(
            appPackage = "com.stationdm.bluelink",
            appName = "Bluelink",
            ownersUrl = "https://owners.hyundaiusa.com/us/en",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.hyundaiusa.com/us/en/dealer-locator",
            manualsUrl = "https://owners.hyundaiusa.com/us/en/resources",
            serviceScheduleUrl = "https://owners.hyundaiusa.com/us/en/page/schedule-service",
            roadsidePhone = "8002437766",
            storeUrl = "https://commerce.hyundai.com/us/en/commerce/fod",
        )
        Brand.GENESIS -> BrandLinks(
            appPackage = "com.stationdm.genesis",
            appName = "Genesis",
            ownersUrl = "https://owners.genesis.com/us/en/",
            dealerLabel = "Find a retailer",
            dealerUrl = "https://www.genesis.com/us/en/find-a-retailer.html",
            manualsUrl = "https://owners.genesis.com/us/en/resources.html",
            serviceScheduleUrl = "https://owners.genesis.com/us/en/page/schedule-service.html",
            roadsidePhone = "8443409741",
            storeUrl = "https://owners.genesis.com/us/en/page/connected-services.html",
        )
        Brand.KIA -> BrandLinks(
            appPackage = "com.myuvo.link",
            appName = "Kia Access",
            ownersUrl = "https://owners.kia.com/us/en/kia-owner-portal.html",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.kia.com/us/en/find-a-dealer",
            manualsUrl = "https://www.kia.com/us/en/owners",
            serviceScheduleUrl = "https://owners.kia.com/us/en/service-page/schedule-service.html",
            roadsidePhone = "8003334542",
            storeUrl = "https://owners.kia.com/us/en/kiaConnectStore/themes.html",
        )
        // Canada owner portals ARE the API host itself (mybluelink.ca etc. serve both the web owner
        // portal and this app's REST API), unlike the US brands above where the API host is a
        // separate api.* subdomain from the consumer-facing owners.* site.
        Brand.HYUNDAI_CA -> BrandLinks(
            appPackage = "com.stationdm.bluelink",
            appName = "Bluelink",
            ownersUrl = "https://www.hyundaicanada.com/en/owners-section",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.hyundaicanada.com/en/showroom/find-a-dealer",
            manualsUrl = "https://www.hyundaicanada.com/en/owners-section/manuals-and-warranties",
            serviceScheduleUrl = "https://www.hyundaicanada.com/en/owners-section/schedule-service",
            roadsidePhone = "8002689958",
            storeUrl = "https://commerce.hyundai.com/us/en/commerce/fod",
        )
        Brand.GENESIS_CA -> BrandLinks(
            appPackage = "com.stationdm.genesis",
            appName = "Genesis",
            ownersUrl = "https://www.genesis.com/ca/en/owners/",
            dealerLabel = "Find a retailer",
            dealerUrl = "https://www.genesis.com/ca/en/retailer-locator.html",
            manualsUrl = "https://www.genesis.com/ca/en/support/faq.html",
            serviceScheduleUrl = "https://www.genesis.com/ca/en/owners/owners-experience/roadside-assistance.html",
            roadsidePhone = "8444364333",
            storeUrl = "https://owners.genesis.com/us/en/page/connected-services.html",
        )
        Brand.KIA_CA -> BrandLinks(
            appPackage = "com.myuvo.link",
            appName = "Kia Access",
            ownersUrl = "https://www.kia.ca/en/owners",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.kia.ca/en/dealers",
            manualsUrl = "https://www.kia.ca/en/owners/manuals-and-guides",
            serviceScheduleUrl = "https://www.kia.ca/en/owners/kia-ownership/24-hour-roadside-assistance",
            roadsidePhone = "8664445421",
            storeUrl = "https://owners.kia.com/us/en/kiaConnectStore/themes.html",
        )
        // Europe: pan-European owner portal (country is chosen on the site). No single EU roadside
        // number (it's per-country), so it's left blank — callers already guard empty phone
        // strings.
        Brand.HYUNDAI_EU -> BrandLinks(
            appPackage = "com.hyundai.bluelink.eu",
            appName = "Bluelink",
            ownersUrl = "https://www.hyundai.com/eu/en/owners.html",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.hyundai.com/eu/en/find-a-dealer.html",
            manualsUrl = "https://www.hyundai.com/eu/en/owners/e-manual.html",
            serviceScheduleUrl = "https://www.hyundai.com/eu/en/owners/book-a-service.html",
            roadsidePhone = "",
            storeUrl = "https://www.hyundai.com/eu/en/owners.html",
        )
    }

/**
 * Whether this car's head unit supports the connected-car content store (Features on Demand:
 * display themes, ambient-lighting patterns…).
 */
val Vehicle.supportsConnectedStore: Boolean
    get() = brand == Brand.KIA || (!brand.isCanada && !brand.isEurope && (generation.trim().toIntOrNull() ?: 0) >= 3)

/**
 * Whether this car is an older Gen5W (pre-ccNC) Hyundai/Genesis head unit — a non-Kia car that
 * reports head-unit generation below 3. Unlike [supportsConnectedStore], the generation fallback
 * here is 3 (assume modern ccNC, i.e.
 */
val Vehicle.isGen5W: Boolean
    get() = brand != Brand.KIA && !brand.isCanada && !brand.isEurope && (generation.trim().toIntOrNull() ?: 3) < 3

/**
 * True for exactly the population where [isGen5W] varies at all: a Hyundai/Genesis US car, the only
 * brand/region combination whose API reports a real head-unit generation number.
 */
val Vehicle.platformOverridable: Boolean
    get() = brand != Brand.KIA && !brand.isCanada && !brand.isEurope

/** On [Brand] rather than [Vehicle], because not every caller has a Vehicle. */
val Brand.supportsHornLights: Boolean
    get() = this != Brand.KIA && !isCanada && !isEurope

/**
 * [Brand.supportsHornLights] for a [Vehicle], which is what the phone screens have to hand. One
 * rule, two spellings of the same question.
 */
val Vehicle.supportsHornLights: Boolean
    get() = brand.supportsHornLights
