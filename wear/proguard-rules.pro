# R8 configuration for the watch release.
#
# --- Why -dontobfuscate ---------------------------------------------------------
# The same split the phone makes, for the same reason, and it is not negotiable here either: this
# pipeline never RUNS a watch build. Renaming is the half of R8 whose failures land at RUNTIME, on a
# watch nobody in CI has; shrinking's failures are overwhelmingly build-time, which is why shrinking is
# on and renaming is off. See app/proguard-rules.pro for the long version.
#
# There is nothing in this module that reads a class by name -- no Class.forName, no reflection -- so
# the keeps below are about the serialized payloads and the Data Layer, not about this app's own code.
# Every service, activity and receiver here is named in the manifest, which R8 keeps on its own.
-dontobfuscate

# --- kotlinx.serialization ------------------------------------------------------
# The watch decodes the phone's snapshot and settings payloads, which are kotlinx.serialization. A
# stripped $$serializer does not fail the build; it fails the decode, at runtime, on the watch.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Wearable Data Layer --------------------------------------------------------
# The listener is manifest-named and kept, but the Data Layer resolves the capability and node types
# it hands that listener through reflection, and it is the one path in this app that has to work with
# the phone app absent, backgrounded, or on a different build.
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**
