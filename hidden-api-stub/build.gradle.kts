plugins {
    id("com.android.library")
}

// compileOnly-consumed stubs of hidden framework AIDL interfaces (IPackageManager /
// IPackageInstaller / IPackageInstallerSession / IIntentSender) so :app can compile the
// Shizuku silent-install path. These are NEVER shipped — at runtime the real framework
// classes are used (reached via ShizukuBinderWrapper + reflection, with HiddenApiBypass
// lifting the non-SDK block). Using an Android library (has android.jar on the classpath)
// keeps the stub set tiny: only the genuinely-hidden types are declared here.
android {
    namespace = "com.bloo.bluelink.hiddenapi"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
