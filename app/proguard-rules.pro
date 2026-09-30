# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# NewPipeExtractor no publica consumer rules; hay que conservar las clases del
# extractor tal cual las carga ServiceList y el renderer de resultados (B41).
-keep class org.schabi.newpipe.** { *; }

# Los Log.* con datos sensibles (cookies/cabeceras, B32; cuerpo de 4xx, B33) no
# deben llegar al APK publicado: R8 los elimina en release (B41).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

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