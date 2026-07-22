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
    );

    /** True when sign-in uses a one-time code and no service PIN (Kia US). */
    val usesOtpLogin: Boolean get() = this == KIA

    companion object {
        // Looks up an enum entry by its exact `name` (e.g. "KIA"); falls back to
        // HYUNDAI (the original, pre-multi-brand default) if `name` is null or
        // doesn't match any current entry, so a legacy/blank stored value never
        // throws NoSuchElementException.
        fun fromName(name: String?): Brand =
            entries.firstOrNull { it.name == name } ?: HYUNDAI

        /** Map a vehicle's brand indicator ("G" = Genesis, "K" = Kia) to a [Brand]. */
        fun fromIndicator(indicator: String?): Brand = when {
            indicator.equals("G", ignoreCase = true) -> GENESIS
            indicator.equals("K", ignoreCase = true) -> KIA
            else -> HYUNDAI
        }
    }
}

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
    }

/**
 * Whether this car's head unit supports the connected-car content store
 * (Features on Demand: display themes, ambient-lighting patterns…). That's a
 * ccNC-era feature: Hyundai/Genesis US report head-unit generation 3+ for
 * ccNC, while older Gen5W cars report 2. Kia US doesn't expose a generation,
 * so Kia stays eligible and the store page itself gates by VIN.
 */
val Vehicle.supportsConnectedStore: Boolean
    get() = brand == Brand.KIA || (generation.trim().toIntOrNull() ?: 0) >= 3

/** Horn & Lights / Flash Lights (rcs/rhl/light, rcs/rhl/hnl) exist on the
 *  Hyundai/Genesis US telematics API this app already uses for lock/unlock;
 *  Kia's US API (Kia Connect) has no equivalent endpoint. */
val Vehicle.supportsHornLights: Boolean
    get() = brand != Brand.KIA
