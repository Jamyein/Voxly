# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep jaudiotagger classes
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ========================================
# Enhanced Gson rules (critical for TypeToken)
# ========================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep TypeToken classes (prevents ClassCastException)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class com.google.gson.reflect.TypeToken { *; }

# Keep Gson DefaultDateTypeAdapter and other internal adapters
-keep class com.google.gson.internal.** { *; }
-keepclassmembers class com.google.gson.internal.** { *; }
-keep class com.google.gson.internal.bind.** { *; }

# ========================================
# Enhanced Retrofit rules
# ========================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Exceptions

# Keep Retrofit service interface methods with full signature
-keep,allowobfuscation interface com.voxly.data.remote.tengx.TengxApi {
    <methods>;
}
-keep,allowobfuscation interface com.voxly.data.remote.wangy.WangyApi {
    <methods>;
}
-keep,allowobfuscation interface com.voxly.data.remote.itunes.ITunesApi {
    <methods>;
}
-keep,allowobfuscation interface com.voxly.data.remote.musicbrainz.MusicBrainzApi {
    <methods>;
}

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep Retrofit Converter and related classes
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-keep class retrofit2.converter.** { *; }
-keep class retrofit2.converter.gson.** { *; }
-keep class retrofit2.http.** { *; }

# Keep all service interfaces (critical for Retrofit proxy)
-keep,allowobfuscation interface * extends retrofit2.CallAdapterFactory
-keep,allowobfuscation interface * extends retrofit2.Converter$Factory
-keep,allowobfuscation interface * extends retrofit2.ServiceMethod

# Keep Retrofit platform and helper classes
-keep class retrofit2.-lambda.** { *; }
-keep class retrofit2.-void.** { *; }
-keep class retrofit2.Utils {
    <methods>;
}
-keep class retrofit2.ServiceMethod {
    <fields>;
}

# Don't optimize these packages - can break reflection
-dontoptimize
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Prevent inlining that might break reflection
-keepclassmembers,allowobfuscation,allowshrinking class * {
    @retrofit2.http.* <methods>;
}

# ========================================
# Keep OkHttp
# ========================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep OkHttp internal classes
-keep class okhttp3.internal.** { *; }
-keep class okio.** { *; }

# ========================================
# Keep all API model classes
# ========================================
-keep class com.voxly.data.remote.tengx.model.** { *; }
-keep class com.voxly.data.remote.wangy.model.** { *; }
-keep class com.voxly.data.remote.itunes.model.** { *; }
-keep class com.voxly.data.remote.musicbrainz.model.** { *; }

# Keep generic type information for Retrofit Gson Converter
# This prevents "Class cannot be cast to ParameterizedType" errors
-keepattributes Signature
-keep,allowobfuscation,allowshrinking class retrofit2.Response {
    <fields>;
}
-keep,allowobfuscation,allowshrinking class retrofit2.Response<T> {
    <init>(...);
}
-keepclassmembers,allowobfuscation,allowshrinking class retrofit2.Response {
    <init>(...);
}
-keepclassmembers,allowobfuscation,allowshrinking class retrofit2.Response<*> {
    <init>(...);
}

# Keep Retrofit interfaces (required for reflection-based creation)
-keep class com.voxly.data.remote.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ========================================
# Prevent R8 from stripping type information
# ========================================
-keepclassmembers,allowobfuscation class * {
    <init>(...);
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========================================
# Kotlin-specific rules
# ========================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin jvm functions
-keepclassmembers class kotlin.jvm.** {
    <fields>;
}
