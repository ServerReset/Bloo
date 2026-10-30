package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.PhotoTile

private val NonAlnum = Regex("[^a-z0-9]+")

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

/**
 * The bundled picture that is this car, or null when its model or its colour is not known well enough to say: the group is
 * found as [CarPictures.groupFor] does it and the colour must match a picture's colour (whole name, or one inside the other, a
 * plain colour preferred over its matte twin unless the car says matte). Wrong paint is worse than none, so no guess is made.
 */
internal fun matchCarPicture(model: String, name: String, colour: String?, pictures: List<CarPicture>): CarPicture? {
    fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    fun words(s: String) = s.lowercase().split(NonAlnum).filter { it.isNotEmpty() && it != "matte" }.toSet()
    val wanted = colour?.let(::squash)?.takeIf { it.isNotEmpty() } ?: return null
    val wantedWords = words(colour)
    val said = squash("$model $name")
    val wantsN = said.contains("ioniq5n")
    val wantsMatte = wanted.contains("matte")
    val scored = pictures
        .filter { said.contains(squash(it.group.substringBefore(" (").removeSuffix(" N"))) }
        .filter { (it.group.contains(" N")) == wantsN }
        .mapNotNull { p ->
            val c = squash(p.colour)
            val shared = words(p.colour).intersect(wantedWords).size
            val score = when {
                c == wanted -> 4
                c.removeSuffix("matte") == wanted.removeSuffix("matte") && (wantsMatte == c.endsWith("matte")) -> 4
                c.contains(wanted) || wanted.contains(c) -> 3
                // "Glacial White" for "Glacial White Pearl": two shared words is the same paint, one ("white") is not.
                shared >= 2 -> 2
                else -> 0
            }
            if (score == 0) null else Triple(p, score, if ("(" in p.group) 0 else 1)
        }
    val best = scored.maxOfOrNull { it.second } ?: return null
    val top = scored.filter { it.second == best }
    // A loose match must be the only one: two different paints that fit equally (a bare "BLUE" on a model with two blues) means
    // the account has not said which.
    if (best < 4 && top.map { it.first.colour }.distinct().size > 1) return null
    return top.maxWithOrNull(compareBy<Triple<CarPicture, Int, Int>> { it.third }.thenBy { if (wantsMatte == it.first.colour.endsWith("Matte")) 1 else 0 })?.first
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
        buttons = { DialogButton("Close", onDismiss) },
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
        val shown = remember(pictures, group) { pictures.filter { it.group == group } }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(340.dp),
            horizontalArrangement = Arrangement.spacedBy(GapRow),
            verticalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            items(shown, key = { it.url }) { p ->
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

/**
 * A car's photo in the shared [PhotoTile]: the picture from the car's photo URL, or the car glyph without one. The map's pins, cluster
 * chips and off-screen pointers all show a car this way.
 */
@Composable
internal fun CarPhotoTile(
    photo: String?,
    tint: Color,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(44.dp, 32.dp),
    selected: Boolean = false,
    glyphSize: Dp = 18.dp,
) {
    PhotoTile(
        hasPhoto = photo != null,
        glyph = AppIcons.DirectionsCar,
        tint = tint,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = size,
        selected = selected,
        glyphSize = glyphSize,
        photo = { tileModifier ->
            AsyncImage(
                model = rememberPhotoModel(photo!!),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = tileModifier,
            )
        },
    )
}
