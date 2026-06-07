# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bloo.bluelink.data.**$$serializer { *; }
-keepclassmembers class com.bloo.bluelink.data.** {
    *** Companion;
}
