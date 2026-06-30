-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class kotlin.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# Rhino JS engine (transitive from NewPipe) references Java SE classes not in Android
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**

# Media3 session
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**
