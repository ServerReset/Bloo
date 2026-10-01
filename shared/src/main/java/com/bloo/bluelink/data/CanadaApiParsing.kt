package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

/** The Canada API's status parsing and climate command, as extensions of [CanadaApi], kept out of the class so it stays readable. */

/**
 * Maps `result.status` onto the shared [VehicleStatus] model. Field names
 * here match [VehicleStatus]'s own almost exactly (both ultimately trace
 * back to the same community reverse-engineering lineage), so this walks
 * the tree directly rather than via kotlinx.serialization decode, keeping
 * the same defensive per-field null handling as [KiaUsaApi.parseStatus]
 * for the fields whose shape does differ (evStatus.drvDistance/remainTime2).
 */
internal fun CanadaApi.parseStatus(vs: JsonObject, modelYear: Int?): VehicleStatus {
    val ev = vs["evStatus"] as? JsonObject
    val evStatus = if (ev == null) null else EvStatus(
        batteryCharge = ev["batteryCharge"].flag(),
        batteryStatus = ev["batteryStatus"].int(),
        batteryPlugin = (ev["batteryPlugin"] ?: vs["batteryPlugin"]).int(),
        drvDistance = run {
            // `ev` FIRST, falling back to the top-level status object -- the same shape
            // `batteryPlugin` three lines above already uses, and for the same reason: CA
            // puts some EV fields under evStatus and some beside it. This read was
            // top-level ONLY, so EvStatus.drvDistance was always empty for a Canadian EV
            // and every surface that shows electric range fell back to nothing.
            val range = ev.path("drvDistance", "0", "rangeByFuel", "totalAvailableRange")
                ?: ev.path("drvDistance", "0", "rangeByFuel", "evModeRange")
                ?: vs.path("drvDistance", "0", "rangeByFuel", "totalAvailableRange")
                ?: vs.path("drvDistance", "0", "rangeByFuel", "evModeRange")
            range?.path("value").dbl()?.kmToMi()
                ?.let { listOf(DrvDistance(RangeByFuel(Dte(it, range.path("unit").int())))) }
                ?: emptyList()
        },
        remainTime2 = RemainTime2(
            atc = ev.path("remainTime2", "atc", "value").dbl()?.let { TimeValue(it, 1) },
            etc1 = ev.path("remainTime2", "etc1", "value").dbl()?.let { TimeValue(it, 1) },
            etc3 = ev.path("remainTime2", "etc3", "value").dbl()?.let { TimeValue(it, 1) },
        ).takeIf { it.atc != null || it.etc1 != null || it.etc3 != null },
        // KNOWN GAP: reservChargeInfos (the AC/DC charge-LIMIT targets) is left null for
        // Canada. Because of that, the UI now hides the editable charge-limit controls on
        // CA cars (Brand.supportsChargeLimits = !isCanada) rather than show sliders seeded
        // from the 80/90 defaults, and every reading surface self-hides on the null too. So
        // this is currently a graceful "feature absent", not a broken control -- but the
        // underlying data is still missing. The CA status body (rltmvhclsts / lstvhclsts)
        // does NOT carry the targets inline, unlike KiaUsaApi, which merges them from a
        // separate read (evc/gts, see KiaUsaApi.chargeTargets). The CA read counterpart is
        // believed to be evc/selsoc (the community myUVO/CA client's _get_charge_limits;
        // note the CA write side already here, setChargeTargets -> evc/setsoc, uses field
        // "level" not Kia's "targetSOClevel"). That endpoint's exact shape can't be
        // confirmed from this repo (no CA fixtures, CI can't hit the network), so it is
        // deliberately NOT added on inference -- doing so would ship an unverified API
        // contract. To re-enable the controls: confirm evc/selsoc's response against a real
        // CA account, merge it in status() with the same runCatching best-effort wrapper
        // KiaUsaApi uses (a bad path then degrades to today's behaviour), and flip
        // Brand.supportsChargeLimits back on.
    )
    return VehicleStatus(
        doorLock = vs["doorLock"].flag(),
        airCtrlOn = vs["airCtrlOn"].flag(),
        engine = vs["engine"].flag(),
        acc = vs["acc"].flag(),
        trunkOpen = vs["trunkOpen"].flag(),
        hoodOpen = vs["hoodOpen"].flag(),
        defrost = vs["defrost"].flag(),
        doorOpen = (vs["doorOpen"] as? JsonObject)?.let {
            DoorOpen(it["frontLeft"].int(), it["frontRight"].int(), it["backLeft"].int(), it["backRight"].int())
        },
        windowOpen = (vs["windowOpen"] as? JsonObject)?.let {
            WindowOpen(it["frontLeft"].int(), it["frontRight"].int(), it["backLeft"].int(), it["backRight"].int())
        },
        tirePressureLamp = (vs["tirePressureLamp"] as? JsonObject)?.let {
            TirePressureLamp(
                tirePressureLampAll = it["tirePressureLampAll"].int(),
                tirePressureLampFL = it["tirePressureLampFL"].int(),
                tirePressureLampFR = it["tirePressureLampFR"].int(),
                tirePressureLampRL = it["tirePressureLampRL"].int(),
                tirePressureLampRR = it["tirePressureLampRR"].int(),
            )
        },
        // Canada reports distance in km (the CA app has no imperial option),
        // but the rest of Bloo treats every Dte/RangeByFuel value as miles
        // internally, converting to km only at display time based on the
        // user's own unit preference (see formatDistance) -- so this needs
        // to be normalized to miles right here at the parse boundary, or a
        // metric-mode user sees the km figure re-multiplied by 1.609 on top
        // of an already-km number (reported by a user: Bluelink said 263 km,
        // Bloo showed 423 km -- 263 * 1.609 ≈ 423).
        dte = (vs["dte"] as? JsonObject)?.let { Dte(it["value"].dbl()?.kmToMi(), it["unit"].int()) },
        airTemp = (vs["airTemp"] as? JsonObject)?.let {
            val unit = it["unit"].int()
            // The CA backend reports the climate setpoint the same hex-"H"
            // Celsius-index way it ENCODES it (tempToHex, e.g. "0AH"). Decode
            // it back to a °F numeric string here — every consumer feeds
            // airTemp.value straight into degLabel(), which treats its input
            // as °F, so an undecoded "0AH" rendered as garbage "0AH°". Sibling
            // of the 868 km→mi fix on dte just above.
            // Normalised to °F HERE, so every surface downstream -- the phone
            // UI readers -- gets one unit rather than each having to know
            // this backend's conventions. A reader that sees only
            // airTemp.value through a payload with no unit code could not
            // interpret a Celsius value correctly
            // however careful the formatting was at that end.
            //
            // Two shapes arrive on unit 0. The hex "0AH" index decodes via
            // hexTempToF. A plain number on unit 0 is a Celsius reading, and
            // that is the one that was being read as Fahrenheit: a car sitting
            // at 22.5°C displayed as (22.5 - 32) * 5/9 = -5°C. Reported from a
            // real device.
            //
            // The unit code is rewritten to 1 alongside, because it has to keep
            // describing the value beside it -- leaving 0 on a converted °F
            // number would tell degLabel the value is Celsius and convert it a
            // second time.
            // NOT normalised to °F. Converting here would round a 22.5°C
            // setpoint to 72.5°F and back to 22°C, and this backend's table is
            // IN half degrees, so that loses half a degree on the common case
            // rather than an edge one. The value stays exactly as the car
            // reported it and the unit code says what it is.
            val rawTemp = it["value"]?.str()
            val decoded = hexTempToF(rawTemp, unit, modelYear)
            TempValue(decoded, if (decoded != rawTemp) 1 else unit)
        },
        battery = (vs["battery"] as? JsonObject)?.let { Battery12V(batSoc = it["batSoc"].int()) },
        evStatus = evStatus,
        dateTime = vs.path("lastStatusDate").str(),
        steerWheelHeat = vs["steerWheelHeat"].int(),
        sideBackWindowHeat = vs["sideBackWindowHeat"].int(),
        sideMirrorHeat = vs["sideMirrorHeat"].int(),
        seatHeaterVentState = (vs["seatHeaterVentState"] as? JsonObject)?.let {
            SeatHeaterVentState(
                it["flSeatHeatState"].int(), it["frSeatHeatState"].int(),
                it["rlSeatHeatState"].int(), it["rrSeatHeatState"].int(),
            )
        },
        lowFuelLight = vs["lowFuelLight"].flag(),
        washerFluidStatus = vs["washerFluidStatus"].flag(),
        breakOilStatus = vs["breakOilStatus"].flag(),
        smartKeyBatteryWarning = vs["smartKeyBatteryWarning"].flag(),
        fuelLevel = vs["fuelLevel"].int(),
    )
}

/** Start climate / remote start. Temperature comes in as Fahrenheit
 *  ([ClimateRequest.tempF], the shared UI's unit) and is converted to the
 *  nearest Celsius half-degree, then hex-encoded — see [tempToHex]. */
suspend fun CanadaApi.startClimate(
    session: CanadaSession, v: CanadaVehicleSummary, pAuth: String, req: ClimateRequest,
) = withContext(Dispatchers.IO) {
    val hexTemp = tempToHex(req.tempF, v.year)
    val anySeat = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
        .any { it != SeatLevel.OFF }
    fun climateFields() = buildJsonObject {
        put("airCtrl", 1)
        put("defrost", req.defrost)
        // heating1 is a bundle-selector, not a plain boolean -- see BlueLinkApi's own
        // (US/Genesis) copy of this same field for the sourced values (0/2/3/4, not a
        // 0/1 toggle) and the real-world report that motivated the fix.
        put("heating1", if (req.steeringWheelHeat.isOn) 3 else 0)
        put("igniOnDuration", req.durationMinutes)
        put("airTemp", buildJsonObject {
            put("value", hexTemp)
            put("unit", 0)
            put("hvacTempType", if (v.isEv) 1 else 0)
        })
        // Best-effort heat-only scale (0 off .. 3 high) -- see class doc:
        // the real per-seat command encoding isn't documented anywhere in
        // the reference project this was ported from.
        if (anySeat) {
            fun cmd(level: SeatLevel) = if (level.isHeat) (level.apiValue - 5) else 0
            put("seatHeaterVentCMD", buildJsonObject {
                put("drvSeatOptCmd", cmd(req.seatFrontLeft))
                put("astSeatOptCmd", cmd(req.seatFrontRight))
                put("rlSeatOptCmd", cmd(req.seatRearLeft))
                put("rrSeatOptCmd", cmd(req.seatRearRight))
            })
        }
    }
    val body = if (v.isEv) {
        buildJsonObject { put("pin", session.pin.orEmpty()); put("hvacInfo", climateFields()) }
    } else {
        buildJsonObject { put("setting", climateFields()); put("pin", session.pin.orEmpty()) }
    }
    val path = if (v.isEv) "evc/rfon" else "rmtstrt"
    val request = Request.Builder().url(apiUrl + path)
        .post(body.toString().toRequestBody(jsonMedia)).commandHeaders(session, v.id, pAuth).build()
    call(request)
    Unit
}

/** Convert a Fahrenheit setpoint to the API's zero-padded-hex-plus-"H"
 *  index encoding. Mirrors KiaUvoApiCA's get_index_into_hex_temp exactly:
 *  the nearest half-degree Celsius value's *index* into the model-year's
 *  lookup table, hex-formatted as e.g. "0AH". */
internal fun CanadaApi.tempToHex(tempF: Int, modelYear: Int?): String {
    val celsius = (tempF - 32) * 5.0 / 9.0
    val table = if ((modelYear ?: TEMP_RANGE_MODEL_YEAR) >= TEMP_RANGE_MODEL_YEAR) TEMP_RANGE_NEW else TEMP_RANGE_OLD
    val rounded = Math.round(celsius * 2) / 2.0
    val clamped = rounded.coerceIn(table.first(), table.last())
    val index = table.indices.minByOrNull { kotlin.math.abs(table[it] - clamped) } ?: 0
    return Integer.toHexString(index).padStart(2, '0').uppercase(Locale.US) + "H"
}

/** Inverse of [tempToHex]: decode the API's hex-"H" Celsius-index setpoint
 *  (e.g. "0AH") back to a °F numeric string for display. Mirrors KiaUvoApiCA's
 *  get_hex_temp_into_index guard — only `unit == 0` hex-"H" values are the
 *  encoded setpoint; anything else (a plain number, "OFF", unit != 0) is
 *  passed through untouched so [degLabel] handles it. Round-trips with
 *  tempToHex to within the table's half-degree resolution. */
internal fun CanadaApi.hexTempToF(raw: String?, unit: Int?, modelYear: Int?): String? {
    if (raw == null) return null
    if (unit != 0 || !raw.endsWith("H")) return raw
    val index = raw.dropLast(1).toIntOrNull(16) ?: return raw
    val table = if ((modelYear ?: TEMP_RANGE_MODEL_YEAR) >= TEMP_RANGE_MODEL_YEAR) TEMP_RANGE_NEW else TEMP_RANGE_OLD
    val celsius = table.getOrNull(index) ?: return raw
    return (celsius * 9.0 / 5.0 + 32.0).toString()
}
