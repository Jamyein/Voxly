# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using in build.gradle.

# ========================================
# Keep jaudiotagger classes
# ========================================
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ========================================
# Kotlinx Serialization rules
# ========================================
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Kotlinx Serialization model classes
-keep,includedescriptorclasses class com.voxly.**$$serializer { *; }
-keepclassmembers class com.voxly.** {
    *** Companion;
}
-keepclasseswithmembers class com.voxly.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========================================
# Retrofit rules
# ========================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Retrofit service interfaces
-keep,allowobfuscation interface com.voxly.data.remote.** {
    <methods>;
}

# Keep Retrofit core classes
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-keep class retrofit2.http.** { *; }

-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ========================================
# OkHttp rules
# ========================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.internal.** { *; }
-keep class okio.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ========================================
# Kotlin rules
# ========================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
