package com.bloo.bluelink.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphIconButton
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
        var reading by remember { mutableStateOf(false) }
        MorphTextButton(
            text = "Read the privacy policy",
            onClick = { reading = true },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            fillOnPress = false,
        )
        if (reading) PrivacyPolicyViewer(onClose = { reading = false })
    }
}

/**
 * The privacy policy, shown inside the app from the copy bundled with it (assets/privacy.html), so it reads the same offline and
 * matches the website's page. JavaScript stays off: the policy is a document, not a page.
 */
@Composable
internal fun PrivacyPolicyViewer(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MorphIconButton(onClick = onClose, description = "Close the privacy policy") {
                        Icon(AppIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    RevealText("Privacy policy", style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = true
                            webViewClient = WebViewClient()
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            loadUrl("file:///android_asset/privacy.html")
                        }
                    },
                )
            }
        }
    }
}
