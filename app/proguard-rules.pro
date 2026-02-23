# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep jaudiotagger classes
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Keep Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep all API model classes (critical for Gson deserialization)
-keep class com.voxly.data.remote.tengx.model.** { *; }
-keep class com.voxly.data.remote.wangy.model.** { *; }
-keep class com.voxly.data.remote.musicbrainz.model.** { *; }
-keep class com.voxly.data.remote.itunes.model.** { *; }

# Keep Retrofit interfaces (required for reflection-based creation)
-keep class com.voxly.data.remote.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
