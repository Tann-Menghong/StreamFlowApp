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
# Keep Rhino itself so YouTube JS signature deobfuscation survives R8 in release builds
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }

# protobuf-javalite (transitive from NewPipe v0.26.x — used for YouTube innertube params)
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Media3 session
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**
