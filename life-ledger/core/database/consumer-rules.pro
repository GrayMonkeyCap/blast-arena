# SQLCipher's Java classes are the other half of a JNI boundary: the native library looks
# them up by name, so obfuscating or stripping them breaks database open at runtime rather
# than at build time.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Room resolves DAO implementations and the generated database by name at runtime.
-keep class com.lifeledger.core.database.LifeLedgerDatabase_Impl { *; }

# Entities are constructed reflectively by nothing, but their names appear in the exported
# schema JSON that migration tests diff against; keeping them keeps those tests honest.
-keep class com.lifeledger.core.database.entity.** { *; }
