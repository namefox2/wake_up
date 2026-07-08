# App data models (Gson / Firebase deserialization)
-keep class com.silentlink.app.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

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
