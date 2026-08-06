# Room and KSP-generated code rely on reflection over entity and DAO names.
-keep class com.lifeledger.core.database.** { *; }
-keep class com.lifeledger.core.model.** { *; }

# SQLCipher ships native code reached through JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Hilt/Dagger generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Kotlin serialization keeps the generated serializers on @Serializable classes.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Life Ledger never ships a crash reporter, so keeping line numbers costs nothing and
# makes a locally captured stack trace readable.
-keepattributes SourceFile,LineNumberTable
