# ── Okio ──────────────────────────────────────────────
-dontwarn okio.**
-keep class okio.** { *; }
-keep interface okio.** { *; }

# ── Compose text factory ──────────────────────────────
# ProGuard's return-type optimization makes this factory fail JVM verification.
# Keep its bytecode intact while still permitting shrinking and obfuscation.
-keep,allowshrinking,allowobfuscation class androidx.compose.ui.text.ParagraphKt__ActualParagraph_skikoKt {
    public static androidx.compose.ui.text.Paragraph Paragraph*(androidx.compose.ui.text.ParagraphIntrinsics, long, int, int);
}


# ── JNA (used by filekit-dialogs-compose) ─────────────
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# ── korlibs (solved MethodHandle.invoke polymorphic signature) ─
-dontwarn korlibs.ffi.FFILib_jvmKt

# ── FileKit (file picker dialogs) ─────────────────────
-keep class io.github.vinceglb.filekit.** { *; }
-dontwarn io.github.vinceglb.filekit.**

# ── Ktor (entire framework — uses internal reflection for engine, channels, etc.) ─
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── kotlinx.io (used by Ktor internally — reflective field access) ──────────
-keep class kotlinx.io.** { *; }
-dontwarn kotlinx.io.**

# ── kotlinx.coroutines (kept for safety) ─────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── OpenAI Client (com.aallam.openai) ─────────────────────────────────
-keep class com.aallam.openai.** { *; }
-dontwarn com.aallam.openai.**

# ── SLF4J ──────────────────────────────────────────────────────────────
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

-dontwarn **
