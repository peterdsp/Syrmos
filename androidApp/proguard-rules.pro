-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.syrmos.**$$serializer { *; }
-keepclassmembers class com.syrmos.** {
    *** Companion;
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# SQLDelight
-keep class app.cash.sqldelight.** { *; }

# Koin
-keep class org.koin.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Compose
-dontwarn androidx.compose.**

# kotlinx-datetime
-keep class kotlinx.datetime.** { *; }
