# Keep annotations, generic signatures, and Kotlin metadata for Retrofit, Moshi, and Room
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters,SourceFile,LineNumberTable

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Keep Settings, Room Entities/DAOs, and Moshi JSON models intact
-keep class com.example.settingsstore.** { *; }
-keep class com.example.customwidgets.*Dto { *; }
-keep class com.example.customwidgets.*Response { *; }
-keep interface com.example.customwidgets.*Api { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class com.squareup.moshi.** { *; }

# Keep NotificationListenerService entry point
-keep class com.example.contextautomation.LiveNotificationBridge { *; }

# Retrofit & OkHttp safe warnings
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlin.reflect.jvm.internal.**
