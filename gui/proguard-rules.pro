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

# ── FileKit (file picker dialogs) ─────────────────────
-keep class io.github.vinceglb.filekit.** { *; }
-dontwarn io.github.vinceglb.filekit.**

# ── SLF4J ──────────────────────────────────────────────────────────────
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

-dontwarn **
