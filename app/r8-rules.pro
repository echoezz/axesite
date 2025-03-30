# Enable R8 full mode
-allowaccessmodification
-repackageclasses
-keepattributes Exceptions,InnerClasses,Signature,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable



# Aggressive method inlining
-assumevalues class * {
    boolean debug() return false;
    boolean isDebug() return false;
}

# Don't use mixed case class names, to ensure cross-platform compatibility
-dontusemixedcaseclassnames