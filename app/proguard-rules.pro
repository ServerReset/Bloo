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

# WorkManager's OWN internal Room database (WorkDatabase) hit the same problem from
# the other direction: this app's first real release run crashed cold on
# "Failed to create an instance of class androidx.work.impl.WorkDatabase" the moment
# anything first touched WorkManager (WorkManagerInit.of(), on a background thread --
# see that class's own doc for why the automatic androidx.startup initializer is
# disabled here). Room resolves its generated `WorkDatabase_Impl` implementation via a
# `Class.forName(...)` call built from a STRING at runtime, not a static reference R8's
# analysis can see -- so plain shrinking strips the generated class as apparently
# unreferenced. work-runtime's own consumer rules normally cover this for the default
# auto-init path; they evidently don't reliably survive this app's manual/deferred one,
# and this was never actually exercised before now (see the file-level comment on
# -dontobfuscate above -- assembleRelease succeeding was never proof of this).
-keep class androidx.work.impl.** { *; }

# --- Glance (home-screen widget) -------------------------------------------------
# Speculative, added while chasing a widget reported as stuck on its static
# car_widget_loading.xml placeholder forever -- even a full remove-and-re-add never
# reaches EITHER of this app's own two failure points (provideGlance's own try/catch,
# or onCompositionError), which is the same symptom shape as the WorkManager keep
# rule above: something Glance itself needs is being resolved reflectively (Glance
# runs its own composition session through WorkManager internally, via a worker/
# session class this app never references by type, exactly the pattern that already
# bit WorkManager's OWN internal database above) and R8's shrinker has no static
# reference telling it that class is live. No confirmed stack trace pointing at a
# specific class yet -- AppLog.log() calls were added at CarWidgetReceiver.onEnabled/
# onUpdate and CarWidget.provideGlance specifically to get one -- so this keeps the
# whole package rather than guessing at one class name, the same tradeoff already
# made for androidx.work.impl above and just as cheap: -dontobfuscate means nothing
# here was going to be renamed either way, only (maybe) stripped.
-keep class androidx.glance.** { *; }

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
