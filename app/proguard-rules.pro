# R8 configuration for the release build.
#
# --- Why -dontobfuscate ---------------------------------------------------------
# Shrinking is ON (isMinifyEnabled = true); RENAMING is deliberately left off.
# Two reasons, and the first is decisive:
#
#  1. This build cannot be verified before it ships. CI has no emulator, so a
#     release APK is never RUN anywhere -- `assembleRelease` proves R8 completed,
#     not that the app works. Renaming is the half of R8 whose failures land at
#     RUNTIME rather than at build time: a missing keep renames or strips a class
#     and the crash surfaces in the user's hands, not in the build log. Shrinking's
#     failures are overwhelmingly build-time. So take the half that CI can verify.
#  2. Stack traces stay readable. This app shows its own log to the user (AppLog,
#     the in-app diagnostics), and nothing in the release pipeline uploads a
#     mapping file -- so an obfuscated crash would be undecipherable by the one
#     person able to report it.
#
# Turning renaming on later is a one-line deletion here, but do it only after a
# release APK has actually been installed and exercised: sign-in, a Drive sync, a
# widget at two sizes, the watch pairing.
-dontobfuscate

# --- kotlinx.serialization ------------------------------------------------------
# The entire phone<->watch protocol, every persisted JSON blob, and the settings
# backup format are kotlinx.serialization. A stripped $$serializer does not fail
# the build; it fails the decode, at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Scoped to com.bloo.bluelink.** and NOT com.bloo.bluelink.data.** as it was.
# The old scope shipped with a comment asserting that data/ "is where every
# @Serializable type in this app actually lives -- verified". That is wrong for
# :app, and R8 would have quietly stripped the two exceptions:
#   - com.bloo.bluelink.ui.CustomPaletteData             (Theme.kt)
#   - com.bloo.bluelink.widget.WidgetConfigStore$Stored  (private, nested)
# Both persist to DataStore, so the symptom would have been an empty custom
# palette list and every placed widget losing its configuration -- with no build
# warning anywhere.
-keep,includedescriptorclasses class com.bloo.bluelink.**$$serializer { *; }
-keepclassmembers class com.bloo.bluelink.** {
    *** Companion;
}

# --- WorkManager ----------------------------------------------------------------
# Nine CoroutineWorkers, each instantiated REFLECTIVELY by WorkManager's default
# WorkerFactory from a class name persisted in its own database. No call site
# references them by type at construction, so shrinking has no reason to believe
# the two-arg constructor is live. work-runtime ships consumer rules of its own,
# but a redundant keep costs nothing and the failure mode here is runtime-only and
# invisible: an alert, a widget refresh or a Drive sync that silently never runs
# again after an update.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Optional / reflective third parties ----------------------------------------
# Shizuku is OPTIONAL, gated at runtime behind Shizuku.pingBinder(), and its
# provider reaches hidden platform constructors. R8 must not fail the build over
# references it cannot resolve.
#
# ShizukuInstaller's own getDeclaredField("installFlags") targets
# PackageInstaller.SessionParams -- a PLATFORM class, which R8 never renames or
# removes, so that call site needs no keep at all.
-dontwarn rikka.shizuku.**
-dontwarn dev.rikka.shizuku.**
-keep class rikka.shizuku.** { *; }
