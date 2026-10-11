# --- Natív hidak (JNI/JNA) – a natív kód név szerint keresi ezeket ---
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-keep class com.redravencomputing.whispercore.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# --- MediaPipe LLM (Gemma) ---
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# --- JavaMail (napi összefoglaló e-mail) ---
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

# --- Room / WorkManager (a Worker osztályokat reflexióval példányosítja) ---
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class hu.orajegyzet.data.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin coroutines ---
-dontwarn kotlinx.coroutines.debug.**

# --- Összeomlási jelentésekhez ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
