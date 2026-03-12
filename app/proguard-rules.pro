# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ===== Hilt / Dagger =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.InstallIn class *
-keep @javax.inject.Singleton class *

# ===== Gson =====
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# 保留所有資料模型（用於 JSON 序列化）
-keep class com.moooo_works.letsgogps.data.** { *; }
-keep class com.moooo_works.letsgogps.domain.model.** { *; }

# ===== Google Maps =====
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.maps.android.**

# ===== Google Play Billing =====
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ===== AdMob =====
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ===== Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ===== Kotlin =====
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ===== DataStore =====
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite* { <fields>; }
