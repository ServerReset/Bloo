package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton

/** One bundled car picture: which model it is, which colour, and the asset it is read from. */
internal data class CarPicture(val group: String, val colour: String, val url: String)

/**
 * The car pictures that ship inside the app (a transparent cut-out per model and paint colour, in assets/cars). The catalogue is
 * read off the file names (`car-ioniq-5-n-atlas-white-matte.webp` is the IONIQ 5 N in Atlas White Matte), so adding a picture
 * is dropping a file in the folder.
 */
internal object CarPictures {
    private const val DIR = "cars"
    const val URL_PREFIX = "file:///android_asset/$DIR/"

    private val models = listOf("ioniq-5", "ioniq-6", "ioniq-9", "ev6", "ev9")
    private val variants = mapOf("n" to " N", "original" to " (original)", "facelift" to " (facelift)")

    @Volatile private var cached: List<CarPicture>? = null

    fun all(context: Context): List<CarPicture> = cached ?: load(context).also { cached = it }

    private fun load(context: Context): List<CarPicture> =
        (runCatching { context.assets.list(DIR) }.getOrNull().orEmpty()).mapNotNull { file ->
            val slug = file.substringBeforeLast('.')
            val rest = slug.removePrefix("car-")
            val model = models.firstOrNull { rest.startsWith("$it-") } ?: return@mapNotNull null
            var tail = rest.removePrefix("$model-")
            var label = model.replace("-", " ").uppercase()
            variants.entries.firstOrNull { tail.startsWith("${it.key}-") }?.let {
                label += it.value
                tail = tail.removePrefix("${it.key}-")
            }
            CarPicture(label, colourName(tail), URL_PREFIX + file)
        }.sortedWith(compareBy({ it.group }, { it.colour }))

    private fun colourName(slug: String): String =
        slug.split('-').joinToString(" ") { if (it == "with") it else it.replaceFirstChar(Char::titlecase) }

    /** The group that fits [v] best (a car called "IONIQ 5" opens on the IONIQ 5), or null when none does. */
    fun groupFor(v: Vehicle, pictures: List<CarPicture>): String? {
        fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val said = squash("${v.model} ${v.name}")
        val wantsN = said.contains("ioniq5n")
        return pictures.map { it.group }.distinct()
            .filter { said.contains(squash(it.substringBefore(" (").removeSuffix(" N"))) }
            .minByOrNull { g -> if (g.endsWith(" N") == wantsN && "(" !in g) 0 else 1 }
    }
}

/** A cut-out picture is drawn whole against the pebble (not cropped, not frosted): a PNG the user chose, or a bundled one. */
internal fun isCutoutPhoto(url: String): Boolean =
    url.endsWith(".png", ignoreCase = true) || url.startsWith(CarPictures.URL_PREFIX)

/** The button that opens the picture chooser for [v], and the chooser itself. */
@Composable
internal fun CarPictureButton(v: Vehicle, current: String?, vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    MorphTextButton("Pick a Bloo car picture", onClick = { open = true })
    if (open) CarPictureDialog(v, current, onPick = { vm.setVehicleImage(v.vin, it); open = false }, onDismiss = { open = false })
}

@Composable
private fun CarPictureDialog(v: Vehicle, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pictures = remember { CarPictures.all(context) }
    val groups = remember(pictures) { pictures.map { it.group }.distinct() }
    var group by remember {
        mutableStateOf(pictures.firstOrNull { it.url == current }?.group ?: CarPictures.groupFor(v, pictures) ?: groups.firstOrNull())
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = AppIcons.DirectionsCar,
        title = "Car picture",
        text = {
            if (pictures.isEmpty()) BodyMediumText("No pictures are bundled with this build.")
            else CarPictureGrid(pictures, groups, group, current, { group = it }, onPick)
        },
        buttons = { MorphTextButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth()) },
    )
}

@Composable
private fun CarPictureGrid(
    pictures: List<CarPicture>,
    groups: List<String>,
    group: String?,
    current: String?,
    onGroup: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        BodySmallText("Choose your model, then your paint colour.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(GapRow)) {
            groups.forEach { g ->
                MorphTextButton(g, onClick = { onGroup(g) }, emphasis = if (g == group) ButtonEmphasis.Confirm else ButtonEmphasis.Tonal)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(340.dp),
            horizontalArrangement = Arrangement.spacedBy(GapRow),
            verticalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            items(pictures.filter { it.group == group }, key = { it.url }) { p ->
                Column(
                    Modifier.clip(StandardShape).clickable { onPick(p.url) },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    AsyncImage(model = p.url, contentDescription = "${p.group} in ${p.colour}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(70.dp))
                    BodySmallText(p.colour + if (p.url == current) " ✓" else "")
                }
            }
        }
    }
}
