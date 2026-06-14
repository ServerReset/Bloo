# Keep kotlinx.serialization generated serializers for the shared wire models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.bloo.bluelink.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.bloo.bluelink.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
