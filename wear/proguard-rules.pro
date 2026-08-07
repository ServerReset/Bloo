# Keep kotlinx.serialization generated serializers for the shared wire models.
#
# These rules are INERT today: both build types set isMinifyEnabled = false, so
# R8 never consults this file. They are kept at parity with app/proguard-rules.pro
# anyway because the entire phone<->watch protocol is kotlinx.serialization JSON
# (WearSync's every payload type), so the day minify is switched on for :wear,
# missing serializer keeps would not fail the build -- they would strip the
# generated $$serializer classes and break every Data Layer decode at RUNTIME,
# on a surface with no logs in front of the user.
#
# The two keeps below were present in app/ and absent here, which is the whole
# asymmetry: the Companion and serializer(...) rules alone are not enough, since
# it is the generated $$serializer CLASS that has to survive.
#
# Scoped to com.bloo.bluelink.data because that is where every @Serializable type
# in this app actually lives -- :wear declares none of its own (verified), it only
# consumes the ones :shared defines.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.bloo.bluelink.data.**$$serializer { *; }
-keepclassmembers class com.bloo.bluelink.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.bloo.bluelink.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
