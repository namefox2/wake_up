# App data models (Gson / Firebase deserialization)
-keep class com.silentlink.app.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# 크래시 스택 트레이스에서 실제 파일명과 라인 번호 보존 (클래스/메서드 난독화는 유지)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Gson
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# App classes used by Gson/Firebase (keep field names for serialization)
-keepclassmembers class com.silentlink.app.** {
    <fields>;
}
-keep class com.silentlink.app.App { *; }
-keep class com.silentlink.app.CrashLogger { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }

# AdMob / Mediation
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.mediation.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# DataStore / Protobuf
-keep class androidx.datastore.** { *; }

# Accompanist
-dontwarn com.google.accompanist.**
