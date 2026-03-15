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

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.monday8am.edgelab.**$$serializer { *; }
-keepclassmembers class com.monday8am.edgelab.** {
    *** Companion;
}
-keepclasseswithmembers class com.monday8am.edgelab.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# LiteRT-LM native libraries
-keep class com.google.ai.edge.litertlm.** { *; }

# Suppress warnings for unused transitives in libraries
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.Unsafe
-dontwarn javax.naming.**
-dontwarn javax.xml.**
-dontwarn javax.activation.**
-dontwarn javax.enterprise.**
-dontwarn io.micrometer.**
-dontwarn reactor.**