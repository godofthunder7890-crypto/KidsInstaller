# Keep installer classes
-keep class com.system.services.** { *; }
-keepclassmembers class com.system.services.** { *; }

# Kotlin + coroutines
-keepattributes Kotlin*
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# OkHttp + Okio (used for download + version check)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# JSON (used via reflection in parseRelease / SHA256Helper)
-keep class org.json.** { *; }

# WorkManager (UpdateWorker must be instantiatable by WorkManager at runtime)
-keep class androidx.work.** { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# PackageInstaller + broadcast receivers (must survive minification)
-keep class * extends android.content.BroadcastReceiver { *; }

# R classes
-keepclassmembers class **.R$* { public static <fields>; }

# Keep BuildConfig fields used at runtime
-keep class com.system.services.BuildConfig { *; }
