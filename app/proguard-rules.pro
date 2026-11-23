# Room entities and enums are read back by generated code; keep enum names stable
# because they are stored as text in the database and in backup files.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}
-keep class com.moneytracker.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn org.json.**
