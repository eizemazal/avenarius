# Avenarius R8/ProGuard rules.
#
# Most libraries (Ktor, Coil3, kotlinx-coroutines, kotlinx-serialization) ship their
# own consumer rules, applied automatically. These are extra, conservative keeps.

# Our domain models are parsed/built by hand from MessagePack/JSON; keep them intact
# so nothing surprising happens if any field is touched reflectively.
-keep class com.avenarius.app.model.** { *; }

# kotlinx.serialization annotations/metadata (defensive — core rules usually suffice).
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.serialization.**

# Ktor occasionally references optional engines/features that aren't on our classpath.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
