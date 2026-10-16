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
# The Linux picker goes through the XDG portal, and dbus-java picks its transport through
# `META-INF/services/org.freedesktop.dbus.spi.transport.ITransportProvider`: the provider is named
# by that file alone, so shrinking deletes it and the portal transport cannot be created. The
# interface keeps its name as well — the file is looked up *by* that name, and ProGuard does not
# rename resource files.
-keep class org.freedesktop.dbus.spi.transport.ITransportProvider { *; }
-keep class org.freedesktop.dbus.transport.jre.** { *; }

# ── kotlinx.coroutines ────────────────────────────────
# `JobSupport` carries a compiler-generated `cancel()` bridge that reaches `Job`'s default method
# through `invokespecial`, which the JVM accepts only while `Job` is one of its *direct*
# superinterfaces. `Job` is redundant in that list — `ChildJob` and `ParentJob` extend it — so the
# shrinker drops it and leaves the bridge behind; the class then fails verification with
# `VerifyError: Bad invokespecial instruction: interface method to invoke is not in a direct
# superinterface`. A class that is kept is exempt from that pruning, which is why the rule has no
# `allowshrinking` (that flag would re-enable it).
-keep,allowobfuscation class kotlinx.coroutines.JobSupport

# ── Ktor ──────────────────────────────────────────────
# `HttpClient {}` without an explicit engine resolves its engine through
# `META-INF/services/io.ktor.client.HttpClientEngineContainer`, and the content-negotiation
# extension is registered the same way (`…KotlinxSerializationExtensionProvider`). Both providers
# are named only by those files — nothing in the code references them — so shrinking deletes them
# and the lookup fails at runtime with "Failed to find HTTP client engine implementation".
# `gui-android` carries the same rule for its R8 build.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Mordant (the CLI, run in process) ─────────────────
# The terminal interface comes from `META-INF/services/…TerminalInterfaceProvider` and, for the
# implementations the build did not name, from a list of class names held as strings; neither is a
# static reference, so all three providers are shrunk away and `Terminal()` has nothing to load.
# The provider interface keeps its name as well: the service file is looked up *by* that name, and
# ProGuard does not rename resource files.
-keep class com.github.ajalt.mordant.terminal.TerminalInterfaceProvider { *; }
-keep class com.github.ajalt.mordant.terminal.terminalinterface.** { *; }

# ── kotlinx-schema-generator ──────────────────────────
# Its generators are discovered through `META-INF/services/…core.SchemaGenerator`; only the
# serialization-based one is referenced from code, and a service file that names deleted classes
# makes every lookup throw `ServiceConfigurationError`.
-keep class kotlinx.schema.generator.** { *; }

# ── Native window frame (Win32) ───────────────────────
# `NativeWindowFrame` takes its window procedure from a lookup *by name*
# (`MethodHandles.lookup().bind(this, "handleMessage", …)`), so the member has to survive shrinking,
# and its descriptor must not be optimized. Obfuscation renames it, the lookup then fails, and the
# window silently loses its native frame — minimize/maximize animations and snap — while the app
# stays otherwise intact.
-keepclassmembers class mct.gui.window.NativeWindowFrame {
    long handleMessage(long, int, long, long);
}

# ── Enum machinery ────────────────────────────────────
# `Class.getEnumConstants()` — the path behind `Enum.valueOf(Class, name)` — reaches the constants
# by looking up the *method named* `values`, and it reports a bogus
# `IllegalArgumentException: <class> is not an enum class` when the name is gone: `isEnum()` is
# still true and the `ACC_ENUM` flags are still there, but the constants come back null.
# Obfuscation renames both members, so every enum parsed by name breaks in the release build — the
# GUI's `-l Info -l Warning -l Error` (a Clikt `.convert { LoggerLevel.valueOf(it) }`) fails with
# `invalid value for -l: … is not an enum class` and every command run from the GUI aborts.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── SLF4J ──────────────────────────────────────────────────────────────
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

-dontwarn **
