package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * The map rendered on the JVM (Robolectric), so the layout can be looked at without a device. Each screenshot is written to the test log as
 * base64 between MAPSHOT_BEGIN and MAPSHOT_END lines; decode it to a PNG to see what the map draws. Tiles do not load here (no network), so the
 * screenshots show the layout, the pins and the edge pins over a blank map, not the map's own tiles.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val anchor = GeoLocation(37.7749, -122.4194)

    /** A few cars around the anchor: one inside the map, some near the edges, one far away in each direction. */
    private val cars = listOf(
        CarPin("anchor", anchor, "Anchor", photo = null),
        CarPin("inside", GeoLocation(37.7749, -122.4194 + 0.004), "Inside", photo = null),
        CarPin("east", GeoLocation(37.7749, -122.4194 + 0.02), "East", photo = null),
        CarPin("farNorth", GeoLocation(37.7749 + 0.03, -122.4194), "Far north", photo = null),
        CarPin("southwest", GeoLocation(37.7749 - 0.02, -122.4194 - 0.03), "Southwest", photo = null),
    )

    private fun render(name: String, widthDp: Int, heightDp: Int) {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(widthDp.dp, heightDp.dp)) {
                    CarMap(
                        location = anchor,
                        modifier = Modifier.size(widthDp.dp, heightDp.dp),
                        deviceLocation = GeoLocation(37.7749 - 0.005, -122.4194 + 0.012),
                        cars = cars,
                        anchorVin = "anchor",
                        selectedVin = "inside",
                    )
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val encoded = Base64.getEncoder().encodeToString(png)
        println("MAPSHOT_BEGIN $name ${bitmap.width}x${bitmap.height}")
        encoded.chunked(1000).forEach { println(it) }
        println("MAPSHOT_END $name")
    }

    @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    @Test
    fun phoneMapWithEdgePins() = render("phone-411x891", 411, 891)

    @Config(sdk = [35], qualifiers = "w840dp-h1280dp-xxhdpi")
    @Test
    fun tabletMapWithEdgePins() = render("tablet-840x1280", 840, 1280)
}
