# Keep installer classes
-keep class com.system.services.** { *; }
-keepclassmembers class com.system.services.** { *; }

# Kotlin
-keepattributes Kotlin*
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# R classes
-keepclassmembers class **.R$* { public static <fields>; }
