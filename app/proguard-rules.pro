# Keep A17 Assist engine and inject packages (including AIDL & Shizuku UserService)
-keep class com.a17.assist.ffvn.engine.** { *; }
-keep class com.a17.assist.ffvn.inject.** { *; }
-keep class com.a17.assist.ffvn.data.** { *; }
-keep class rikka.shizuku.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.a17.assist.ffvn.data.**$$serializer { *; }
-keepclassmembers class com.a17.assist.ffvn.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.a17.assist.ffvn.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
