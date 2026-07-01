package com.bloo.bluelink.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Fires the system Package Installer for a downloaded APK. minSdk is 26, so
 * canRequestPackageInstalls()/ACTION_MANAGE_UNKNOWN_APP_SOURCES are always
 * available — no pre-O fallback needed.
 */
object UpdateInstaller {

    /** Whether the OS will currently let this app request an install. Check at
     *  the moment the user taps "Install" — not proactively — and if false,
     *  send them through [unknownSourcesSettingsIntent] first. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Hand the downloaded APK to the system installer via a FileProvider
     *  content:// URI (installers can't be given a raw file:// URI on API 24+). */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
