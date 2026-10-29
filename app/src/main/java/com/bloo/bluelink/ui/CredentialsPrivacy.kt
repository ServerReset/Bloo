package com.bloo.bluelink.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.bloo.bluelink.data.PrivacyCopy
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SecondaryTextStyle

/**
 * What happens to a person's credentials, said the same way wherever it is said: the sign-in screen and the Accounts card both use this
 * note, so the promise cannot drift apart. [brandLabel] names who the password is sent to (or null for "your manufacturer").
 */
@Composable
internal fun CredentialsPrivacyNote(brandLabel: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
            Icon(AppIcons.Lock, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            RevealText(
                "Your password never leaves this phone, except to sign in",
                style = PrimaryTextStyle(),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        }
        RevealText(
            "Your password and service PIN are encrypted on this phone with AES-256-GCM, a widely trusted standard. The key is sealed inside the Android Keystore, the phone's hardware security chip, so the app can read the " +
                "secrets back but nobody else can. Bloo has no server, so no copy of your data is kept anywhere but this phone. " +
                "They go only to ${brandLabel ?: "your manufacturer"}, to sign in and send commands.",
            style = SecondaryTextStyle(),
            color = scheme.onSurfaceVariant,
        )
        // Three ways to read it: inside the app (the plain copy, kept for offline), the plain HTML page, or the styled web page.
        var choosing by remember { mutableStateOf(false) }
        var reading by remember { mutableStateOf(false) }
        val context = LocalContext.current
        if (!choosing) {
            MorphTextButton(
                text = "Read the privacy policy",
                onClick = { choosing = true },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                fillOnPress = false,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                MorphTextButton(text = "In the app", onClick = { choosing = false; reading = true })
                MorphTextButton(
                    text = "HTML page",
                    onClick = { choosing = false; openUrl(context, PrivacyCopy.PLAIN_URL) },
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    fillOnPress = false,
                )
                MorphTextButton(
                    text = "Web page",
                    onClick = { choosing = false; openUrl(context, PrivacyCopy.STYLED_URL) },
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    fillOnPress = false,
                )
            }
        }
        if (reading) PrivacyPolicyViewer(onClose = { reading = false })
    }
}

/**
 * The privacy policy, shown inside the app. The text is the plain copy the app keeps: the last one it pulled from the site, or the one
 * bundled with the app before any pull. It is pulled again whenever the viewer opens and the site can be reached, so it stays current
 * and still reads offline. JavaScript stays off: the policy is a document, not a page.
 */
@Composable
internal fun PrivacyPolicyViewer(onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var html by remember { mutableStateOf(PrivacyCopy.local(context)) }
    LaunchedEffect(Unit) { PrivacyCopy.pull(context.applicationContext)?.let { html = it } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = scheme.surface) {
            Box(Modifier.fillMaxSize()) {
                // The text fills the screen between the title and the floating bar, so nothing scrolls under either.
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = TitleHeight)
                        .padding(bottom = BarReserve),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            webViewClient = WebViewClient()
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    // Reload only when the text changes, so a recomposition does not restart the page.
                    update = { view ->
                        if (view.tag != html) {
                            view.tag = html
                            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                        }
                    },
                )
                // The title, at the top, over the status bar's own space.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(TitleHeight)
                        .padding(horizontal = GapGroup),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RevealText("Privacy policy", style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                }
                // The actions float at the bottom edge in a frosted pill, above the navigation bar.
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = GapGroup, vertical = GapGroup)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = GapRow, vertical = GapRow),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MorphTextButton(text = "Close", onClick = onClose)
                        MorphTextButton(
                            text = "HTML page",
                            onClick = { openUrl(context, PrivacyCopy.PLAIN_URL) },
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            fillOnPress = false,
                        )
                        MorphTextButton(
                            text = "Web page",
                            onClick = { openUrl(context, PrivacyCopy.STYLED_URL) },
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            fillOnPress = false,
                        )
                    }
                }
            }
        }
    }
}

/** The title strip at the top of the viewer. */
private val TitleHeight = 56.dp

/** Room left at the bottom of the text for the floating action pill, so the last lines of the policy can clear it. */
private val BarReserve = 120.dp
