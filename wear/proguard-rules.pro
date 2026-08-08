# R8 configuration for the :wear release build.
#
# These rules are LIVE as of this commit. They spent their whole existence inert
# -- both build types set isMinifyEnabled = false, so R8 never read this file --
# which is exactly why the scoping bug in the header comment below survived so
# long unnoticed. See app/proguard-rules.pro for the full reasoning; the two
# modules are kept deliberately at parity.

# Shrinking on, RENAMING off. The watch is the worse surface to guess on: there is
# no log in front of the user, and every screen it draws depends on decoding a
# kotlinx.serialization payload that arrived over the Data Layer. A renaming
# failure here is a blank watch app with nothing to report. See the long note in
# app/proguard-rules.pro.
-dontobfuscate

# --- kotlinx.serialization ------------------------------------------------------
# The entire phone<->watch protocol is kotlinx.serialization JSON (every WearSync
# payload type). A stripped $$serializer does not fail the build -- it breaks every
# Data Layer decode at runtime.
#
# Widened from com.bloo.bluelink.data.** to com.bloo.** . The old scope carried the
# claim that data/ "is where every @Serializable type in this app actually lives --
# :wear declares none of its own (verified)". The :wear half of that re-checks out
# today, but the :app half did not (see app/proguard-rules.pro), and a keep scoped
# to a package is a rule that silently stops covering a type the moment someone
# adds one somewhere else. Scope it to the whole app namespace instead: the cost is
# a handful of retained Companion members, and the failure it prevents is invisible
# until a user's watch stops working.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.bloo.**$$serializer { *; }
-keepclassmembers class com.bloo.** {
    *** Companion;
}
-keepclasseswithmembers class com.bloo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
