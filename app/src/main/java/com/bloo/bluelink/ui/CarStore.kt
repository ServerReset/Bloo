package com.bloo.bluelink.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.StoreAccess
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.Vehicle

/** Cookie the store reads its token from (the official app's name for it). */
private const val STORE_COOKIE = "t"

/** Added to the WebView's user agent: the store page looks for it, as it does in the official app. */
private const val STORE_UA_SUFFIX = "_CCS_APP_AOS"

/**
 * The Bluelink Store (Hyundai's features on demand), opened in the app the way the official app opens it: a token fetched for the car, set
 * as a cookie, then the store page loaded in a WebView. Shown full-screen; Close, or the back button, leaves it.
 */
@Composable
internal fun CarStoreDialog(vm: AppViewModel, v: Vehicle, onDismiss: () -> Unit) {
    var access by remember(v.vin) { mutableStateOf<StoreAccess?>(null) }
    var error by remember(v.vin) { mutableStateOf<String?>(null) }
    LaunchedEffect(v.vin) {
        runCatching { vm.canadaRepo(vm.brandOf(v)).storeAccess(v) }
            .onSuccess { access = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't open the store for this car." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Car store", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        "Features on demand from Hyundai. What you can buy is up to Hyundai.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                val token = access
                when {
                    error != null -> Text(error.orEmpty(), modifier = Modifier.padding(16.dp))
                    token == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                    else -> CarStoreWeb(
                        url = token.rerouteUrl ?: vm.brandOf(v).links.storeUrl,
                        access = token,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CarStoreWeb(url: String, access: StoreAccess, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = settings.userAgentString + STORE_UA_SUFFIX
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url
                        if (u.scheme == "https" || u.scheme == "http") return false
                        // tel:, mailto: and the like go to whatever handles them.
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(view, true)
                setStoreCookie(cookies, url, access)
                cookies.flush()
                view.loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

/** The cookie the official app sets: t=<token>;Max-Age=<expiresIn>;Domain=<host>; Path=/ on scheme://host. */
private fun setStoreCookie(cookies: CookieManager, url: String, access: StoreAccess) {
    val u = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val host = u.host ?: return
    val age = access.expiresIn?.let { ";Max-Age=$it" }.orEmpty()
    cookies.setCookie("${u.scheme ?: "https"}://$host", "$STORE_COOKIE=${access.token}$age;Domain=$host; Path=/")
}

/** Whether the in-app store is offered for [brand]; the other brands keep opening their store in the browser. */
internal fun usesInAppStore(brand: Brand): Boolean = brand == Brand.HYUNDAI_CA
