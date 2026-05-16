# ── Okio ──────────────────────────────────────────────
-dontwarn okio.**
-keep class okio.** { *; }
-keep interface okio.** { *; }


# ── JNA (used by filekit-dialogs-compose) ─────────────
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * extends com.sun.jna.PointerType { *; }
-keep class * extends com.sun.jna.Union { *; }
-keepclasseswithmembernames class * { native <methods>; }

# ── FileKit (file picker dialogs) ─────────────────────
-keep class io.github.vinceglb.filekit.** { *; }
-dontwarn io.github.vinceglb.filekit.**
