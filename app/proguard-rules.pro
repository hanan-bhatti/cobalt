# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Preserve annotations and signatures for Kotlin reflection and Moshi
-keepattributes *Annotation*,Signature,InnerClasses

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room and serialized integration models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }

# Keep Glance App Widget components
-keep class com.example.widget.** { *; }
-keep class androidx.glance.** { *; }

# General keep rules for Coroutines / Moshi just in case
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlin.reflect.jvm.internal.** { *; }

# The pinned editor has optional IME diagnostic messages containing text. Never enable them in release.
# No class-name keep rule is needed: this is an optimizer input, not reflective application code.
-assumevalues class io.github.rosemoe.sora.widget.EditorInputConnection {
    static boolean DEBUG return false;
}


# WorkManager persists this class name in its durable link-metadata queue.
-keep class com.example.links.LinkPreviewWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# WorkManager also persists InputMerger names and reflects a no-argument constructor.
# Full R8 removed that constructor in release, causing NoSuchMethodException and
# failing one-time work before the Worker could run. Cover both built-in mergers
# and any future custom merger, preserving only the reflective entry point.
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
