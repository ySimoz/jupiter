# Jupiter release R8 / ProGuard keep rules

-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# Kotlinx Serialization (Navigation Compose routes, StreamHeaderConfig, companions & $$serializer)
-keep,includedescriptorclasses class com.jupiter.app.core.navigation.** { *; }
-keep,includedescriptorclasses class com.jupiter.app.data.repository.StreamHeaderConfig { *; }
-keep,includedescriptorclasses class com.jupiter.app.data.repository.StreamHeaderConfig$* { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep,includedescriptorclasses class <1>$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Room database, entities, DAOs, and generated implementations
-keep class com.jupiter.app.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Background media playback service
-keep class com.jupiter.app.service.MediaPlaybackService { *; }
-keep class com.jupiter.app.service.MediaPlaybackService$* { *; }

# Surface & JNI native callbacks
-keep class android.view.Surface { *; }
-keep class com.jupiter.engine.MpvNative { *; }
-keep interface com.jupiter.engine.MpvEventCallback { *; }
-keep class * implements com.jupiter.engine.MpvEventCallback {
    <methods>;
}
