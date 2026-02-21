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

# Keep Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }

# Keep data classes for Gson
-keep class com.mp3tag.android.data.remote.musicbrainz.model.** { *; }
-keep class com.voxly.data.remote.wangy.model.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
