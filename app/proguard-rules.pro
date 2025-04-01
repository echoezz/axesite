# Add project specific ProGuard rules here.

# Aggressive obfuscation settings
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses 'x.c.d.q'
-flattenpackagehierarchy 'x.c.d.q'

# Keep the app entry points
-keep public class com.example.axesite.MainActivity
-keep public class com.example.axesite.util.KeyLogger
-keep public class com.example.axesite.screens.ExamAccessibilityService
-keep public class com.example.axesite.util.BackgroundVoiceRecordingService

# Keep important classes needed for runtime operations
-keep class com.example.axesite.util.ContactDeletionService
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep layout XML bindings
-keep class androidx.databinding.** { *; }
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Use meaningless names for obfuscation
-obfuscationdictionary proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# Preserve line numbers for stack traces but rename source files
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile

# Advanced optimizations - make code harder to decompile
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# Remove logging calls - removes debug information and reduces size
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Preserve Kotlin metadata for critical classes only
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations,RuntimeInvisibleTypeAnnotations

# For classes that use reflection
-keepattributes InnerClasses,EnclosingMethod

# Prevent class name obfuscation for classes that use @Composable
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Additional obfuscation options
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

# Hazel extra proguard + Paranoid String
# Keep Firebase model classes and their no-arg constructors
-keepclassmembers class com.example.axesite.screens.ChatMessage {
    public <init>();
    public *;
}

# Keep all classes in screens package that might be used with Firebase
-keep class com.example.axesite.screens.** { *; }

# Alternative: Keep all data classes (if you have many Firebase models)
-keepclassmembers class * implements java.io.Serializable {
    public <init>();
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}