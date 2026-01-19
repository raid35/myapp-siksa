# ✅ قواعد ProGuard محسنة لتقليل حجم APK

# الاحتفاظ بالكلاسات الأساسية
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ✅ تحسينات Media3
-keep class androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**

# ✅ تحسينات Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ✅ Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.example.siksa**$$serializer { *; }
-keepclassmembers class com.siksa.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.siksa.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ✅ إزالة Logs في الإصدار النهائي
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ✅ تحسين الكود
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# ✅ إزالة الكود غير المستخدم
-dontwarn **
-ignorewarnings

# ✅ تحسين أسماء الكلاسات
-repackageclasses 'com.example.siksa.obfuscated'
-allowaccessmodification

# ✅ تحسين الموارد
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ✅ حماية إضافية للكود
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
