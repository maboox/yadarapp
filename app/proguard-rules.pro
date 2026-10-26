# The app uses no reflection; the default Android/Compose rules are sufficient.
-dontwarn org.jetbrains.annotations.**

# The mascot is drawn from SVG layer files by AndroidSVG.
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**
