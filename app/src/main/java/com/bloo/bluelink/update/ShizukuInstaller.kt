package com.bloo.bluelink.update

import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch

/**
 * Optional silent APK install via Shizuku (local ADB / root), used by the self-update
 * flow when the user opts in AND Shizuku is running. It drives a real
 * [PackageInstaller] session through the privileged Shizuku identity — the "clean
 * route" the official Shizuku demo uses: wrap the framework "package" binder in
 * [ShizukuBinderWrapper] so calls execute in the shell/root server, reach the hidden
 * [IPackageInstaller], and build the SDK [PackageInstaller]/[PackageInstaller.Session]
 * wrappers via reflected @hide constructors (the hidden framework AIDL types are
 * supplied by the :hidden-api-stub compileOnly module; [rikka.hidden]/HiddenApiBypass
 * lifts the runtime non-SDK block on the reflected constructors, done once at app
 * start in MainActivity).
 *
 * Everything is wrapped so a missing/dead Shizuku binder never crashes — callers fall
 * back to the normal system-installer intent on [Result.failure]. minSdk is 26 (O), so
 * only the S+ and O+ [PackageInstaller] constructors are needed (no legacy branch).
 */
object ShizukuInstaller {

    /** Shizuku installed AND its service reachable (running + this app armed). */
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** Whether the user has already granted this app the Shizuku runtime permission. */
    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Whether the manager showed "deny & don't ask again" (so we shouldn't re-prompt). */
    fun shouldShowRationale(): Boolean = runCatching {
        Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    /** Ask for the Shizuku runtime permission; the result arrives on the listener
     *  MainActivity registers. No-op if the binder is dead. */
    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    /**
     * Install [apk] silently. Blocking (awaits the commit result on a latch) — call
     * off the main thread. Returns [Result.failure] on any error so the caller can
     * fall back to the tap-through system installer.
     *
     * [callerPackageName] is this app's package; used as the installer package only
     * when Shizuku runs as root (uid 0). Under ADB (uid 2000) the installer must be a
     * package that uid owns, so "com.android.shell" is used (matches the Shizuku demo).
     */
    fun installApk(apk: File, callerPackageName: String): Result<Unit> = runCatching {
        require(apk.exists() && apk.length() > 0) { "APK missing or empty: ${apk.path}" }

        // Wrap the framework "package" service so the call runs in the privileged
        // Shizuku server, reach the hidden package installer, and wrap it too.
        val packageManager = IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")),
        )
        val installer = IPackageInstaller.Stub.asInterface(
            ShizukuBinderWrapper(packageManager.getPackageInstaller().asBinder()),
        )

        val isRoot = Shizuku.getUid() == 0
        val installerPackageName = if (isRoot) callerPackageName else "com.android.shell"
        val userId = if (isRoot) Process.myUserHandle().hashCode() else 0

        val pi = createPackageInstaller(installer, installerPackageName, null, userId)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // INSTALL_REPLACE_EXISTING (0x2) + INSTALL_ALLOW_TEST (0x4), the demo's flags —
        // replace-existing is what makes "update over the installed build" work.
        val flags = getInstallFlags(params) or 0x00000002 or 0x00000004
        setInstallFlags(params, flags)

        val sessionId = pi.createSession(params)
        val rawSession = IPackageInstallerSession.Stub.asInterface(
            ShizukuBinderWrapper(installer.openSession(sessionId).asBinder()),
        )
        val session = createSession(rawSession)

        try {
            FileInputStream(apk).use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val len = input.read(buf)
                        if (len <= 0) break
                        output.write(buf, 0, len)
                    }
                    session.fsync(output)
                }
            }

            val result = arrayOfNulls<Intent>(1)
            val latch = CountDownLatch(1)
            val adaptor = object : IIntentSender.Stub() {
                override fun send(
                    code: Int, intent: Intent?, resolvedType: String?,
                    finishedReceiver: IIntentReceiver?, requiredPermission: String?, options: Bundle?,
                ): Int {
                    result[0] = intent
                    latch.countDown()
                    return 0
                }

                override fun send(
                    code: Int, intent: Intent?, resolvedType: String?, whitelistToken: IBinder?,
                    finishedReceiver: IIntentReceiver?, requiredPermission: String?, options: Bundle?,
                ) {
                    result[0] = intent
                    latch.countDown()
                }
            }
            val intentSender = IntentSender::class.java
                .getConstructor(IIntentSender::class.java)
                .newInstance(adaptor)
            session.commit(intentSender)
            latch.await()

            val committed = result[0]
            val status = committed?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                ?: PackageInstaller.STATUS_FAILURE
            if (status != PackageInstaller.STATUS_SUCCESS) {
                val msg = committed?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                throw IllegalStateException("Shizuku install failed: status=$status ${msg ?: ""}")
            }
        } finally {
            runCatching { session.close() }
        }
    }

    // --- Reflected @hide constructors (framework types from :hidden-api-stub) --------

    private fun createPackageInstaller(
        installer: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int,
    ): PackageInstaller {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType,
            ).newInstance(installer, installerPackageName, installerAttributionTag, userId) as PackageInstaller
        } else {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java, String::class.java, Int::class.javaPrimitiveType,
            ).newInstance(installer, installerPackageName, userId) as PackageInstaller
        }
    }

    private fun createSession(session: IPackageInstallerSession): PackageInstaller.Session {
        return PackageInstaller.Session::class.java
            .getConstructor(IPackageInstallerSession::class.java)
            .newInstance(session) as PackageInstaller.Session
    }

    private fun getInstallFlags(params: PackageInstaller.SessionParams): Int {
        val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        return field.getInt(params)
    }

    private fun setInstallFlags(params: PackageInstaller.SessionParams, value: Int) {
        val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        field.setInt(params, value)
    }
}
