# kotlinx.serialization generates a synthetic serializer for the internal preferences DTO;
# keep it so R8 doesn't strip the reflective bits the runtime looks up by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.lifeledger.core.datastore.**$$serializer { *; }
-keepclassmembers class com.lifeledger.core.datastore.** {
    *** Companion;
}
-keepclasseswithmembers class com.lifeledger.core.datastore.** {
    kotlinx.serialization.KSerializer serializer(...);
}
