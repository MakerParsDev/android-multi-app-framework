# Keep line numbers for Crashlytics deobfuscation
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# Emit a usage report so CI can verify R8 is actually stripping dead code
-printusage build/outputs/mapping/release/usage.txt

# Known harmless missing annotation in some GMS artifacts
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# Parcelable creators used via reflection
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}



# Strip logs in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}
