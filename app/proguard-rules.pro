# Phase 1 baseline keep rules.
# Keep the Room schema surface that is discovered via generated code and annotations.
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# org.json is used heavily with string keys; keep names stable for easier crash diagnostics.
-keepnames class org.json.** { *; }
