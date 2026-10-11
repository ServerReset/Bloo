package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Two simulated devices exchanging one Drive file in memory, through the same export / hash / merge
 * code the app runs. It stands in for a live round trip: what a phone writes is what a tablet reads,
 * settings, per-car values keyed by VIN, service fields and photos included.
 */
class TwoDeviceSyncTest {
    private val vin = "5NMS33AD1KH000000"

    /** One device: its stored preferences, its dirty keys, and the hash it last synced. */
    private class Device(val id: String, val prefs: MutableMap<String, Any> = mutableMapOf()) {
        val dirty = mutableSetOf<String>()
        var lastHash: String? = null
        var photos: Map<String, String> = emptyMap()

        fun edit(key: String, value: Any) { prefs[key] = value; dirty += key }
        fun remove(key: String) { prefs.remove(key); dirty += key }
    }

    /** What a sync pass writes: this device's content, with its hash. */
    private fun upload(d: Device, priorRemoved: Set<String> = emptySet()): String {
        val hash = SyncMerge.portableContentHash(d.prefs, d.dirty, d.photos, priorRemoved)
        val json = SyncMerge.buildExportForMainToMain(
            prefs = d.prefs, dirtyKeys = d.dirty, photos = d.photos, hash = hash, primaryDeviceId = null,
            selfDevice = SyncMerge.SyncDevice(d.id, d.id, "test", "1", 0L), knownDevices = emptyList(),
            nowMs = 0L, fileId = "file", priorRemoved = priorRemoved,
        )
        d.dirty.clear()
        d.lastHash = hash
        return json
    }

    /** What a sync pass reads: apply the file to this device, keeping anything it edited since it last synced. */
    private fun download(d: Device, file: String) {
        val meta = assertNotNull(SyncMerge.parseMeta(file))
        if (meta.hash == d.lastHash) return // the change gate: nothing new
        val plan = assertNotNull(SyncMerge.mergePlan(file, guarded = d.dirty))
        plan.stringPuts.forEach { (k, v) -> d.prefs[k] = v }
        plan.boolPuts.forEach { (k, v) -> d.prefs[k] = v }
        plan.removes.forEach { d.prefs.remove(it) }
        val root = Json.parseToJsonElement(file).jsonObject
        d.photos = (root["photos"] as? JsonObject)?.mapValues { (it.value as JsonPrimitive).content } ?: emptyMap()
        d.lastHash = meta.hash
    }

    @Test
    fun aSettingAndPerCarValuesCrossFromPhoneToTablet() {
        val phone = Device("phone")
        val tablet = Device("tablet")
        phone.edit("ui_scale", "1.2")
        phone.edit("haptics_enabled", false)
        phone.edit("svc_last_$vin", "42000")
        phone.edit("svc_interval_$vin", "7500")
        phone.edit("plate_$vin", "8ABC123")
        phone.edit("ai_auto_summarize", true)
        download(tablet, upload(phone))
        assertEquals("1.2", tablet.prefs["ui_scale"])
        assertEquals(false, tablet.prefs["haptics_enabled"])
        assertEquals("42000", tablet.prefs["svc_last_$vin"])
        assertEquals("7500", tablet.prefs["svc_interval_$vin"])
        assertEquals("8ABC123", tablet.prefs["plate_$vin"])
        assertEquals(true, tablet.prefs["ai_auto_summarize"])
    }

    @Test
    fun aCarPhotoTravelsInTheSameFile() {
        val phone = Device("phone")
        val tablet = Device("tablet")
        phone.photos = mapOf(vin to "BASE64JPEGBYTES")
        phone.edit("img_$vin", "/data/user/0/app/files/cars/car_photo.jpg") // a local path: never exported itself
        val file = upload(phone)
        assertFalse("/data/user" in file, "a device-local file path leaked into the synced file")
        download(tablet, file)
        assertEquals("BASE64JPEGBYTES", tablet.photos[vin])
        assertNull(tablet.prefs["img_$vin"], "the path is rewritten by the app after it saves the photo locally")
    }

    @Test
    fun aChangedPhotoChangesTheHashSoTheOtherDeviceNoticesIt() {
        val phone = Device("phone")
        phone.photos = mapOf(vin to "OLD")
        val before = SyncMerge.parseMeta(upload(phone))!!.hash
        phone.photos = mapOf(vin to "NEW")
        val after = SyncMerge.parseMeta(upload(phone))!!.hash
        assertNotEquals(before, after)
    }

    @Test
    fun aDeletionOnOneDeviceReachesTheOther() {
        val phone = Device("phone")
        val tablet = Device("tablet")
        phone.edit("plate_$vin", "8ABC123")
        download(tablet, upload(phone))
        assertEquals("8ABC123", tablet.prefs["plate_$vin"])
        tablet.remove("plate_$vin")
        download(phone, upload(tablet))
        assertNull(phone.prefs["plate_$vin"])
    }

    @Test
    fun anUnsyncedLocalEditIsNeverOverwrittenByTheFile() {
        val phone = Device("phone")
        val tablet = Device("tablet")
        phone.edit("ui_scale", "1.0")
        download(tablet, upload(phone))
        phone.edit("ui_scale", "1.3")
        val stale = upload(phone.also { it.dirty.clear() }) // another device's older write of the same key
        tablet.edit("ui_scale", "0.9")                      // the tablet edits it before it next syncs
        download(tablet, stale)
        assertEquals("0.9", tablet.prefs["ui_scale"], "the guarded local edit must survive")
    }

    @Test
    fun deviceLocalStateNeverLeavesADevice() {
        val phone = Device("phone")
        phone.edit("sync_uri", "content://drive/file")
        phone.edit("last_vehicle_vin", vin)
        phone.edit("alert_door_$vin", true)
        phone.edit("notif_complete_sent_$vin", true)
        phone.edit("seamless_install_shizuku", true)
        phone.edit("theme_mode", "DARK")
        val tablet = Device("tablet")
        download(tablet, upload(phone))
        assertEquals("DARK", tablet.prefs["theme_mode"])
        assertEquals(setOf("theme_mode"), tablet.prefs.keys)
    }

    @Test
    fun anUnchangedFileIsNotReimported() {
        val phone = Device("phone")
        val tablet = Device("tablet")
        phone.edit("ui_scale", "1.1")
        val file = upload(phone)
        download(tablet, file)
        tablet.prefs["ui_scale"] = "locally-adjusted-without-marking-dirty"
        download(tablet, file) // same hash: the gate skips the import entirely
        assertEquals("locally-adjusted-without-marking-dirty", tablet.prefs["ui_scale"])
    }

    @Test
    fun aCarTheOtherDeviceDoesNotHaveIsStillKept() {
        // The tablet is not signed in to the car yet: its per-car values must still arrive, so they
        // are in place the moment the car appears.
        val phone = Device("phone")
        val tablet = Device("tablet")
        phone.edit("svc_interval_$vin", "5000")
        download(tablet, upload(phone))
        assertEquals("5000", tablet.prefs["svc_interval_$vin"])
    }
}
