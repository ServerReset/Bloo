plugins {
    id("java-library")
}

// A PLAIN JVM library (NOT com.android.library) holding compileOnly stubs of hidden
// framework AIDL interfaces (IPackageManager / IPackageInstaller /
// IPackageInstallerSession / IIntentSender / IIntentReceiver) so :app can compile the
// Shizuku silent-install path. This matches the official Shizuku demo's
// `demo-hidden-api-stub` and is REQUIRED: AGP does not support `compileOnly` on an AAR
// (Android-library) dependency — that combo can silently dex these android.*-package
// classes into the APK, which then throws SecurityException("Prohibited package name")
// at runtime. As a plain JAR consumed via compileOnly, the classes are reliably kept
// OUT of the APK; at runtime the real framework classes are used (via ShizukuBinderWrapper
// + reflection, with HiddenApiBypass lifting the non-SDK block).
//
// Because there is no android.jar on a java-library's classpath, the few framework
// types these interfaces reference (android.os.Binder/IBinder/IInterface/RemoteException/
// Bundle and android.content.Intent) are also stubbed here purely so THIS module
// self-compiles. In :app those names resolve to the real platform classes on the
// bootclasspath (which shadow these stubs), so they never leak or conflict.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
