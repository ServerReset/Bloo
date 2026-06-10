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
    );

    companion object {
        fun fromName(name: String?): Brand =
            entries.firstOrNull { it.name == name } ?: HYUNDAI

        /** Map a vehicle's brand indicator ("G" = Genesis) to a [Brand]. */
        fun fromIndicator(indicator: String?): Brand =
            if (indicator.equals("G", ignoreCase = true)) GENESIS else HYUNDAI
    }
}

/** The telematics brand a vehicle belongs to. */
val Vehicle.brand: Brand get() = Brand.fromIndicator(brandIndicator)
