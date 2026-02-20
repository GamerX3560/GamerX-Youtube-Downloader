-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# yt-dlp
-keep class com.yausername.youtubedl_android.** { *; }
-keep class io.github.junkfood02.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.gamerx.downloader.**$$serializer { *; }
-keepclassmembers class com.gamerx.downloader.** { *** Companion; }
-keepclasseswithmembers class com.gamerx.downloader.** { kotlinx.serialization.KSerializer serializer(...); }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
