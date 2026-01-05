# ProGuard rules for Vocal Remover App

# Keep application class
-keep class com.example.vocalremover.VocalRemoverApp { *; }

# Keep main activity
-keep class com.example.vocalremover.MainActivity { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.support.**
-keep class org.tensorflow.lite.metadata.** { *; }
-dontwarn org.tensorflow.lite.metadata.**

# Keep model classes
-keep class com.example.vocalremover.MLModelManager$MLResults { *; }
-keep class com.example.vocalremover.MLModelManager$StemsResults { *; }
-keep class com.example.vocalremover.MLModelManager$GenreClassification { *; }
-keep class com.example.vocalremover.MLModelManager$AudioFeatures { *; }
-keep class com.example.vocalremover.AudioAnalyzer$AudioFeatures { *; }
-keep class com.example.vocalremover.AudioAnalyzer$FrequencyAnalysis { *; }
-keep class com.example.vocalremover.AudioAnalyzer$BeatAnalysis { *; }
-keep class com.example.vocalremover.AudioAnalyzer$MusicalFeatures { *; }

# Audio filters
-keep class com.example.vocalremover.AudioFilter { *; }
-keep class com.example.vocalremover.** implements com.example.vocalremover.AudioFilter { *; }
-keep class com.example.vocalremover.FilterChain { *; }
-keep class com.example.vocalremover.FilterPresets { *; }
-keep class com.example.vocalremover.FilterUtils { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Apache Commons Compress
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }

# Commons IO
-dontwarn commons-io.**
-keep class commons-io.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
