# Keep app classes readable in crash reports
-keepattributes SourceFile,LineNumberTable
# ML Kit / Play services ship their own consumer rules; keep text recognition entry points just in case
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.**
