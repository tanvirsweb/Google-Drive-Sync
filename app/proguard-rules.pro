-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room entities and their enum columns (stored by name via TypeConverters)
-keep class com.tanvir.googlesync.data.** { *; }
-keepclassmembers enum * { *; }
# Gson is only used through its JsonParser tree API (no reflection on our classes).
-dontwarn com.google.gson.**
