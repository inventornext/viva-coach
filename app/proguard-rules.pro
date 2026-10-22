-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.inventornext.vivacoach.**$$serializer { *; }
-keepclassmembers class com.inventornext.vivacoach.** {
    *** Companion;
}
-keepclasseswithmembers class com.inventornext.vivacoach.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
