package com.bloo.bluelink.data

/**
 * A supported US telematics brand. Genesis US runs on the same Hyundai-shaped
 * backend ("…telematics.hyundaiusa.com") with its own host + OAuth client, so
 * the entire request/path structure is shared — only these per-brand values
 * change.
 *
 * Values are taken from the community reverse-engineering projects referenced
 * in [Models]. They are real production endpoints (nothing simulated); the
 * Genesis credentials in particular are community-derived and can be corrected
 * here in one place if Genesis ever rotates them.
 */
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
     * Kia US runs on a completely different backend (api.owners.kia.com, the
     * "Kia Connect" API) served by [KiaUsaApi]/[KiaRepository] rather than the
     * Hyundai-shaped [BlueLinkApi]; [KiaUsaApi] reads its endpoint and client
     * credentials from this entry.
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
     * Hyundai/Genesis/Kia Canada all run on one shared backend family (unrelated
     * to both the US Hyundai/Genesis "HATA" API and Kia's separate US backend),
     * served by [CanadaApi]/[CanadaRepository]: same client_id/client_secret and
     * request shapes across all three, differing only by host. Ported from the
     * community hyundai_kia_connect_api project's KiaUvoApiCA (see [CanadaApi]
     * doc comment for the exact source). Sign-in always goes through an
     * email one-time code (see [usesOtpLogin]).
     */
    HYUNDAI_CA(
        // Multi-letter, distinct from HYUNDAI's plain "H": brandIndicator is our
        // own internal tag (repositoryFor/BlueLinkRepository/KiaRepository always
        // overwrite whatever the wire sent with brand.code, never read it back
        // out of the raw API response), so it's safe to make region-distinct
        // here -- and it must be, since Brand.fromIndicator is how every command
        // path (repoFor(v), TileCommandRunner, WearCommandRunner) re-derives
        // which backend/host a saved Vehicle belongs to.
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
    );

    /** True when the login form shows a one-time-code challenge instead of
     *  logging in directly (Kia US, and all three Canada brands). Distinct
     *  from [requiresPin]: Kia US skips the PIN field entirely, while Canada
     *  still needs one (its own commands are PIN-gated) alongside the code. */
    val usesOtpLogin: Boolean get() = this == KIA || isCanada

    /** False only for Kia US, whose commands aren't PIN-gated at all; every
     *  other brand (including Canada, gated behind CanadaApi.pinAuth) needs
     *  the login form's PIN field filled in. */
    val requiresPin: Boolean get() = this != KIA

    /** True for the three Canada brands, which share [CanadaApi]/[CanadaRepository]
     *  rather than [BlueLinkApi]/[KiaUsaApi]. */
    val isCanada: Boolean get() = this == HYUNDAI_CA || this == GENESIS_CA || this == KIA_CA

    companion object {
        // Looks up an enum entry by its exact `name` (e.g. "KIA"); falls back to
        // HYUNDAI (the original, pre-multi-brand default) if `name` is null or
        // doesn't match any current entry, so a legacy/blank stored value never
        // throws NoSuchElementException.
        fun fromName(name: String?): Brand =
            entries.firstOrNull { it.name == name } ?: HYUNDAI

        /** Map a vehicle's brand indicator back to a [Brand] -- the exact reverse
         *  of the `code` each brand stamps onto its own vehicles (see
         *  BlueLinkRepository.vehicles / KiaRepository.toVehicle / this file's CA
         *  entries), so this is also how every command path (repoFor(v),
         *  TileCommandRunner, WearCommandRunner) re-derives which backend/host a
         *  saved [Vehicle] belongs to. Checks the 3-letter Canada codes first
         *  since they'd otherwise be swallowed by the single-letter US checks
         *  below (e.g. "KCA" contains no exact match to "K"). */
        fun fromIndicator(indicator: String?): Brand = when {
            indicator == HYUNDAI_CA.code -> HYUNDAI_CA
            indicator == GENESIS_CA.code -> GENESIS_CA
            indicator == KIA_CA.code -> KIA_CA
            indicator.equals("G", ignoreCase = true) -> GENESIS
            indicator.equals("K", ignoreCase = true) -> KIA
            else -> HYUNDAI
        }
    }
}

// Brand.Companion.fromNameOrNull was deleted here: no callers anywhere. It was a strict
// null-returning counterpart to fromName (which defaults to HYUNDAI); nothing needed the strict
// variant. Re-add from git history if a caller ever wants "unknown brand -> caller handles it".

/** The telematics brand a vehicle belongs to. */
val Vehicle.brand: Brand get() = Brand.fromIndicator(brandIndicator)

/**
 * Brand-specific apps, sites and phone numbers — the single source of truth.
 * Everything that opens an OEM app, owner page or assistance line reads from
 * here (owner links, the OEM-app launcher, app shortcuts), so a rotated URL or
 * package name is a one-line fix. All URLs are the brands' real US owner-portal
 * pages (verified June 2026).
 */
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
    // Builds the Play Store listing URL on the fly from appPackage rather than
    // storing it as its own field, since it's always the same template per package id.
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
        // Canada owner portals ARE the API host itself (mybluelink.ca etc. serve
        // both the web owner portal and this app's REST API), unlike the US
        // brands above where the API host is a separate api.* subdomain from the
        // consumer-facing owners.* site. Roadside numbers verified against each
        // brand's public Canada support page (July 2026); no Play Store
        // companion-app package or Features-on-Demand store confirmed for
        // Canada, so those reuse the US package/store as a best-effort fallback
        // rather than pointing at something unverified.
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
    }

/**
 * Whether this car's head unit supports the connected-car content store
 * (Features on Demand: display themes, ambient-lighting patterns…). That's a
 * ccNC-era feature: Hyundai/Genesis US report head-unit generation 3+ for
 * ccNC, while older Gen5W cars report 2. Kia US doesn't expose a generation,
 * so Kia stays eligible and the store page itself gates by VIN.
 */
val Vehicle.supportsConnectedStore: Boolean
    get() = brand == Brand.KIA || (!brand.isCanada && (generation.trim().toIntOrNull() ?: 0) >= 3)

/**
 * Whether this car is an older Gen5W (pre-ccNC) Hyundai/Genesis head unit —
 * a non-Kia car that reports head-unit generation below 3. Unlike
 * [supportsConnectedStore], the generation fallback here is 3 (assume modern
 * ccNC, i.e. NOT Gen5W) when the value is missing/unparseable.
 */
val Vehicle.isGen5W: Boolean
    get() = brand != Brand.KIA && !brand.isCanada && (generation.trim().toIntOrNull() ?: 3) < 3

/** Horn & Lights / Flash Lights (rcs/rhl/light, rcs/rhl/hnl) exist on the
 *  Hyundai/Genesis US telematics API this app already uses for lock/unlock;
 *  Kia's US API (Kia Connect) has no equivalent endpoint, and no equivalent
 *  was found in the Canada backend either ([CanadaApi] only exposes
 *  lock/unlock/climate/charge). */
val Vehicle.supportsHornLights: Boolean
    get() = brand != Brand.KIA && !brand.isCanada
