# Consumer ProGuard / R8 rules for :engine (libmpv JNI bridge and track/state models)

-keepattributes *Annotation*, InnerClasses, Signature

# JNI entry points and constants invoked by/into libjupitermpv.so
-keep class com.jupiter.engine.MpvNative { *; }
-keepclasseswithmembernames,includedescriptorclasses class com.jupiter.engine.** {
    native <methods>;
}

# JNI callback interface and concrete implementations resolved via GetObjectClass + GetMethodID
-keep interface com.jupiter.engine.MpvEventCallback { *; }
-keep class * implements com.jupiter.engine.MpvEventCallback {
    <methods>;
}

# Engine models, reducers, events, and JSON parser
-keep class com.jupiter.engine.TrackModels* { *; }
-keep class com.jupiter.engine.MpvJsonParser* { *; }
-keep class com.jupiter.engine.VideoTrack { *; }
-keep class com.jupiter.engine.AudioTrack { *; }
-keep class com.jupiter.engine.SubtitleTrack { *; }
-keep class com.jupiter.engine.Chapter { *; }
-keep class com.jupiter.engine.ParsedTrackList { *; }
-keep enum com.jupiter.engine.HwDecMode { *; }
-keep class com.jupiter.engine.PlaybackState { *; }
-keep class com.jupiter.engine.PlaybackStateReducer { *; }
-keep enum com.jupiter.engine.EngineLifecycleState { *; }
-keep class com.jupiter.engine.MpvEvent { *; }
-keep class com.jupiter.engine.MpvEvent$* { *; }

# Keep any @Serializable companion/serializer classes in :engine
-if @kotlinx.serialization.Serializable class com.jupiter.engine.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.jupiter.engine.**
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.jupiter.engine.**
-keep,includedescriptorclasses class <1>$$serializer { *; }
