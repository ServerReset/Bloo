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
 * package name is a one-line fix.
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
    /** 24/7 roadside-assistance line, digits only. */
    val roadsidePhone: String,
    /** In-car payments (Hyundai Pay etc.) — managed on the brand's pages; no public API. */
    val payLabel: String,
    val payUrl: String,
    /** Plug & Charge enrollment/management page. */
    val plugChargeUrl: String,
    /** Connected-car content store (Features on Demand: themes, lighting…). */
    val storeUrl: String,
) {
    val playStoreUrl: String get() = "https://play.google.com/store/apps/details?id=$appPackage"
}

val Brand.links: BrandLinks
    get() = when (this) {
        Brand.HYUNDAI -> BrandLinks(
            appPackage = "com.stationdm.bluelink",
            appName = "Bluelink",
            ownersUrl = "https://owners.hyundaiusa.com",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.hyundaiusa.com/us/en/dealer-locator",
            manualsUrl = "https://www.hyundaiusa.com/us/en/owner-resources",
            roadsidePhone = "8002437766",
            payLabel = "Hyundai Pay",
            payUrl = "https://www.hyundaiusa.com/us/en/hyundai-pay",
            plugChargeUrl = "https://www.hyundaiusa.com/us/en/plug-and-charge",
            storeUrl = "https://owners.hyundaiusa.com",
        )
        Brand.GENESIS -> BrandLinks(
            appPackage = "com.stationdm.genesis",
            appName = "Genesis",
            ownersUrl = "https://owners.genesis.com",
            dealerLabel = "Find a retailer",
            dealerUrl = "https://www.genesis.com/us/en/find-a-retailer.html",
            manualsUrl = "https://www.genesis.com/us/en/owners.html",
            roadsidePhone = "8443409741",
            payLabel = "In-car payments",
            payUrl = "https://owners.genesis.com",
            plugChargeUrl = "https://www.genesis.com/us/en/plug-and-charge.html",
            storeUrl = "https://owners.genesis.com",
        )
        Brand.KIA -> BrandLinks(
            appPackage = "com.myuvo.link",
            appName = "Kia Access",
            ownersUrl = "https://owners.kia.com",
            dealerLabel = "Find a dealer",
            dealerUrl = "https://www.kia.com/us/en/find-a-dealer",
            manualsUrl = "https://www.kia.com/us/en/owners",
            roadsidePhone = "8003334542",
            payLabel = "In-car payments",
            payUrl = "https://owners.kia.com",
            plugChargeUrl = "https://www.kia.com/us/en/plug-and-charge",
            storeUrl = "https://owners.kia.com",
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
