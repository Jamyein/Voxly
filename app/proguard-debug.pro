# ProGuard rules for debug build
# Enable R8 to shrink and optimize

# Allow R8 to optimize (removed -dontoptimize)
# Allow R8 to obfuscate (removed -dontobfuscate)

# Preserve source and line mapping for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Compose related classes
-keep class androidx.compose.** { *; }

# Keep TagLib native methods
-keep class com.kyant.taglib.** { *; }
