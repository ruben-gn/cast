# Keep Kotlin Serialization DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
# The @Serializable API DTOs live in the :shared-models module under cast.api.*
-keep,includedescriptorclasses class cast.api.**$$serializer { *; }
-keepclassmembers class cast.api.** { *** Companion; }
-keepclasseswithmembers class cast.api.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembernames,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
