package com.bloo.bluelink

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SyncMerge
import com.bloo.bluelink.data.imageUrl
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.licensePlate
import com.bloo.bluelink.data.performMainToMainSync
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.syncFileChangedRemotely
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Drive pipeline end to end on a real device: the real [SettingsStore] (DataStore, dirty tracking,
 * the sync pass, the change poll, the photo import) against a real file, with a second "device" written
 * as another install of the app would write it. The only thing standing in for Drive is a file URI; a
 * provider that reports no modified time, which exercises the content-hash fallback of the live poll.
 */
class DriveSyncPipelineTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val vin = "TESTVIN0000000001"
    private lateinit var file: File
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        file = File(ctx.cacheDir, "drive-sync-test.bloo").apply { delete() }
        store = SettingsStore(ctx)
        runBlocking { store.setSyncUri(Uri.fromFile(file).toString()) }
    }

    @After
    fun tearDown() {
        runBlocking {
            store.setSyncUri(null)
            store.setLicensePlate(vin, "")
            store.setLastServiceMiles(vin, null)
        }
        File(ctx.filesDir, "cars/car_${vin}_synced.jpg").delete()
        file.delete()
    }

    @Test
    fun aLocalEditUploadsAndAnotherDevicesEditAndPhotoArriveThroughThePoll() = runBlocking {
        // This device edits: a per-car value keyed by VIN, and a service field.
        store.setLicensePlate(vin, "MINE123")
        store.setLastServiceMiles(vin, 41_000)
        val first = store.performMainToMainSync()
        assertTrue("the first pass should upload this device's edits", first.uploaded)
        val written = file.readText()
        assertTrue("the edit is in the synced file", "MINE123" in written && "svc_last_$vin" in written)

        // Another device now writes the file: a newer plate, service values, and a car photo.
        val meta = SyncMerge.parseMeta(written.substringAfter('\n'))!!
        val prefs = mapOf("plate_$vin" to "THEIRS456", "svc_last_$vin" to "42500", "svc_interval_$vin" to "7500")
        val photoBytes = byteArrayOf(1, 2, 3)
        val photos = mapOf(vin to android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP))
        val now = System.currentTimeMillis()
        val body = SyncMerge.buildExportForMainToMain(
            prefs = prefs, dirtyKeys = emptySet(), photos = photos,
            hash = SyncMerge.portableContentHash(prefs, emptySet(), photos),
            primaryDeviceId = null,
            selfDevice = SyncMerge.SyncDevice("other-device", "Tablet", "test", "1", now),
            knownDevices = emptyList(), nowMs = now, fileId = meta.fileId ?: "file",
        )
        file.writeText("$now\n$body")

        // The poll notices it (the content peek is throttled to 30s after boot on first use).
        val upFor = SystemClock.elapsedRealtime()
        if (upFor < 31_000L) Thread.sleep(31_000L - upFor)
        assertTrue("the poll should see another device's write", store.syncFileChangedRemotely())

        val second = store.performMainToMainSync()
        assertTrue("the pass should import what the poll saw", second.imported)
        assertEquals("THEIRS456", store.licensePlate(vin))
        assertEquals(42_500, store.lastServiceMiles(vin))
        assertEquals(7_500, store.serviceIntervalMiles(vin))
        val photoFile = File(ctx.filesDir, "cars/car_${vin}_synced.jpg")
        assertTrue("the car photo was written to this device", photoFile.exists())
        assertArrayEquals(photoBytes, photoFile.readBytes())
        assertTrue("the car's photo setting now points at it", store.imageUrl(vin)?.endsWith("car_${vin}_synced.jpg") == true)
    }

    @Test
    fun anUnchangedFileIsNotReportedAsAChange() = runBlocking {
        store.setLicensePlate(vin, "SAME")
        store.performMainToMainSync()
        val upFor = SystemClock.elapsedRealtime()
        if (upFor < 31_000L) Thread.sleep(31_000L - upFor)
        // Our own write is not another device's change.
        assertTrue("our own upload must not look like a remote change", !store.syncFileChangedRemotely())
    }
}
