# NewPipeExtractor uses reflection-free parsing but pulls in Rhino/Jsoup for JS challenge solving.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**

# Media3
-dontwarn androidx.media3.**

# Room
-keep class androidx.room.** { *; }
