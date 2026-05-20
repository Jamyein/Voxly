# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using in build.gradle.

# ========================================
# Keep jaudiotagger classes
# ========================================
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ========================================
# Kyant0/taglib classes (io.github.kyant0:taglib)
# Kyant0/taglib 是 TagLib 的 Kotlin 封装，内部使用 Kotlin Metadata
# 必须 keep，否则 R8 会 strip 掉 metadata 导致 propertyMap 字段读取异常
# 特别是 DATE/YEAR 字段在 release 构建中会返回 null
# ========================================
-keep class com.kyant.taglib.** { *; }
-dontwarn com.kyant.taglib.**

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

-keep,allowobfuscation interface com.voxly.data.remote.** {
    <methods>;
}

-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ========================================
# Kotlin Data Classes - 防止 R8 优化导致 nullable 字段访问异常
# ========================================
-keep class com.voxly.domain.model.** { *; }

# ========================================
# ComposeRuntimeFlags — SlotTable link buffer optimization
# R8 在 release 构建中会编译期求值 isLinkBufferComposerEnabled 为 false，
# 因为 setDefault 是空方法。使用 -assumevalues 强制 R8 保留 true 语义。
# ========================================
-assumevalues class androidx.compose.runtime.ComposeRuntimeFlags {
    boolean isLinkBufferComposerEnabled return true;
}

# ========================================
# JNI rules (ReplayGain native scanner)
# Keep class name stable for JNI_OnLoad FindClass/RegisterNatives.
# ========================================
-keep class com.voxly.data.local.replaygain.native.EbuR128NativeScanner { *; }
