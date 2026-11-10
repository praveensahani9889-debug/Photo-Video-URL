# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Photo Video URL
# ---------------------------------------------------------------------------
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Data classes that are written to / read from Firebase or stored locally keep their member names
-keep class com.praveen.studio.photovideo.url.data.model.** { *; }

# androidx.security:security-crypto (Tink) reads protobuf fields by reflection
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# Optional classes that libraries mention but the app does not ship
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.joda.time.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Classes created through reflection by AndroidX (ViewModels and WorkManager workers)
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
