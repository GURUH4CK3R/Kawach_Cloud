# ProGuard & R8 Optimization Rules for Kawach Cloud Release

# Keep TDLib JNI classes, native methods, and MTProto models
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keep class io.github.tdlibandroid.** { *; }
-keepclassmembers class io.github.tdlibandroid.** { *; }
-dontwarn org.drinkless.tdlib.**
-dontwarn io.github.tdlibandroid.**

# Keep Room Database, Entities, and DAOs
-keep class com.example.data.local.** { *; }
-keepclassmembers class com.example.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Keep Kawach Domain and Data Models
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }

# Keep Media3 ExoPlayer for in-app video/audio preview playback
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# Keep Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
