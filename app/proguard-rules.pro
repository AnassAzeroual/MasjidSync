# Keep Hilt generated components.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.**

# Room generated implementations are referenced from generated code only.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# WorkManager workers are instantiated reflectively by name.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# Play Services geofencing receivers.
-keep class com.mosquetracker.app.detection.GeofenceBroadcastReceiver { *; }
-keep class com.mosquetracker.app.detection.BootReceiver { *; }
