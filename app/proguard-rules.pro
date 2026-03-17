# 1. حل مشكلة التحذير الأصلي (R8 Async Parsing)
-dontwarn com.android.tools.r8.internal.**

# ✅ الاحتفاظ بالكلاسات الأساسية للنظام (قواعد قياسية)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ✅ تحسينات Media3 & ExoPlayer (تقليل النطاق)
# بدلاً من حماية كل شيء، نحمي فقط ما يحتاجه المشغل للعمل عبر الـ Reflection
-keep class androidx.media3.common.util.Assertions
-keep class androidx.media3.exoplayer.ExoPlayer
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**

# ✅ تحسينات Compose
# مكتبات Compose الحديثة تأتي بقواعدها الخاصة، لا داعي لحماية الحزمة كاملة يدوياً
-dontwarn androidx.compose.**

# ✅ Kotlin Serialization (الحل الأذكى باستخدام الـ Annotations)
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

# حماية أي كلاس يستخدم @Serializable بشكل تلقائي بدلاً من تحديد حزمة معينة
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** Companion;
}

# ✅ مكتبات الشبكة والصور (OkHttp & Coil)
# OkHttp يسرب قواعده تلقائياً، نحتاج فقط لتجاهل التحذيرات
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**

# ✅ إزالة Logs
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int d(...);
}

# ✅ تحسين الكود (Optimization)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# ✅ تحسين التشفير (Obfuscation)
-repackageclasses 'com.example.siksa.internal'
-keepattributes SourceFile,LineNumberTable,EnclosingMethod,Exceptions
-renamesourcefileattribute SourceFile
