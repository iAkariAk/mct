# R8 rules for the Android release build.
#
# Modelled on `gui/proguard-rules.pro`, which is the JVM desktop equivalent: the same libraries are
# linked in, so they need the same keeps. The desktop rules also cover ProGuard-only concerns
# (JNA, korlibs, the Skiko paragraph factory, `joinOutputJars`) that have no Android counterpart and
# are omitted here.

# ── Okio ──────────────────────────────────────────────
-dontwarn okio.**
-keep class okio.** { *; }
-keep interface okio.** { *; }

# ── FileKit (file picker dialogs) ─────────────────────
# Loaded through androidx-startup and reached reflectively from the picker intents.
-keep class io.github.vinceglb.filekit.** { *; }
-dontwarn io.github.vinceglb.filekit.**

# ── Ktor (uses internal reflection for engines, channels, etc.) ─────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

-dontwarn **
