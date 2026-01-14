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

# MSAL / Microsoft Identity - suppress missing annotation warnings
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn com.google.auto.value.AutoValue

# Keep MSAL classes
-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.aad.** { *; }

# OpenTelemetry
-dontwarn io.opentelemetry.**

# Danalock SDK dependencies (optional/legacy classes referenced by SDK)
-dontwarn io.swagger.annotations.**
-dontwarn org.jdeferred.**
-dontwarn org.threeten.bp.**
-dontwarn io.gsonfire.**

# Old OkHttp 2.x classes (Danalock SDK uses legacy OkHttp)
-dontwarn com.squareup.okhttp.**

# Keep Danalock SDK classes
-keep class com.danalock.** { *; }
-keep class com.polycontrol.** { *; }
-keep class com.poly_control.** { *; }

# Masterlock SDK
-keep class com.masterlock.** { *; }
-dontwarn com.masterlock.**