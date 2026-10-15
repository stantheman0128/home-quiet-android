# Android entry points referenced only from the manifest. AAPT generates keep rules for these,
# but we keep them explicitly so a refactor can never let R8 strip or rename them.
-keep class tw.stan.homequiet.service.QuietNotificationListener { *; }
-keep class tw.stan.homequiet.service.MonitorService { *; }
-keep class tw.stan.homequiet.service.BootReceiver { *; }
-keep class tw.stan.homequiet.service.GeofenceReceiver { *; }
-keep class tw.stan.homequiet.HomeQuietApp { *; }
-keep class tw.stan.homequiet.MainActivity { *; }
# Play Services geofencing (consumer rules ship with the AAR; this is belt and braces).
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**
