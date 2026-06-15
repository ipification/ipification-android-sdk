-optimizationpasses 5
-dontusemixedcaseclassnames
-repackageclasses ''
-allowaccessmodification
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes MethodParameters
-keepclassmembers enum * {
    public *;
}
-keepnames class ** { *; }

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-dontwarn kotlin.**
-dontwarn kotlin.reflect.jvm.internal.**

# Public SDK API. Keep package names stable for client apps and app-level R8.
-keep class com.ipification.mobile.sdk.ip.** { *; }
-keep class com.ipification.mobile.sdk.im.** { *; }
-keep class com.ipification.mobile.sdk.sms.** { *; }
-keep class com.ipification.mobile.sdk.ts43.** { *; }

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-keepattributes Signature
-keepattributes Annotation
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.conscrypt.ConscryptHostnameVerifier


# Keep all classes annotated with @Parcelize
-keep class kotlinx.parcelize.** { *; }

# Credential Manager
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# Java 11 StringConcatFactory support for R8
-dontwarn java.lang.invoke.StringConcatFactory
