# Keep distDebug readable and debuggable while still shrinking code/resources.
-dontobfuscate
-dontoptimize

# Preserve source and line mapping for stack traces in debug distribution builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
