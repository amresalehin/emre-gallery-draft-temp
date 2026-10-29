# Proguard rules for Emre Gallery

# ONNX Runtime JNI
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Tesseract 4 Android JNI
-keep class com.googlecode.tesseract.android.** { *; }
-keep class cz.adaptech.tesseract4android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-dontwarn cz.adaptech.tesseract4android.**

# OSMDroid
-keepclassmembers class org.osmdroid.** { *; }
-keep class org.osmdroid.views.MapView { *; }
-keep class org.osmdroid.tileprovider.modules.** { *; }
-dontwarn org.osmdroid.**

# Coil
-dontwarn coil.**

# Media3 ExoPlayer (Consumer rules already embedded; suppress optional warnings)
-dontwarn androidx.media3.**
