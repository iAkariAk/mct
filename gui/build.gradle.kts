@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.fus.internal.isCiBuild

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.shadow)
}

kotlin {
    // Named "desktop" rather than the default "jvm" so the platform-specific source set is
    // `desktopMain`; the Compose desktop application below binds to it by target type, not name, so
    // `:gui:run` and the packaging tasks keep their names.
    jvm("desktop") {
        mainRun {
            mainClass.set("mct.gui.MainKt")
        }
    }

    android {
        namespace = "mct.gui"
        compileSdk = 36
        // 26 rather than 24: mordant's JVM terminal interface (pulled in through the CLI) is dexed
        // only from API 26 up, and the project workflow runs the CLI in process.
        minSdk = 26
        // Matches the project toolchain: `:mct`/`:cli`/`:extra` are published at JVM target 25 and
        // the compiler refuses to inline their bytecode into anything lower, so the Android target
        // has to be on the same level. D8 handles that class-file version; the desugaring above
        // covers the APIs it needs.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
        // `java.time` behind `Clock`/`formatEpochDate`, and `java.util.concurrent.atomic` behind the
        // console state, are API 26+; desugaring supplies them down to minSdk.
        enableCoreLibraryDesugaring = true
    }

    sourceSets {
        // The code both JVM targets share. A plain `srcDir` rather than a hierarchy group: the
        // AGP Kotlin target does not take part in the hierarchy template, so a group would leave
        // `androidMain` with no parent and every `expect` in commonMain unresolved on Android.
        // The physical directory is shared, so there is still exactly one copy of each file.
        getByName("desktopMain").kotlin.srcDir("src/jvmSharedMain/kotlin")
        getByName("androidMain").kotlin.srcDir("src/jvmSharedMain/kotlin")

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.adaptive.navigation.suite)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(project(":mct"))
            implementation(project(":extra"))
            implementation(project(":cli"))
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.material.kolor)
            implementation(libs.kmpalette.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json.okio)
        }

        getByName("desktopMain").dependencies {
            if (!isCiBuild()) {
                implementation(compose.desktop.currentOs)
            } else {
                val composeVersion = libs.versions.compose.get()
                listOf(
                    "windows-x64",
                    "linux-arm64",
                    "linux-x64",
                    "macos-arm64",
                ).forEach { platform ->
                    implementation("org.jetbrains.compose.desktop:desktop-jvm-$platform:$composeVersion")
                }
            }
            implementation(libs.kiteimage)
            implementation(libs.kiteimage.compose)
            // Supplies Dispatchers.Main on the desktop.
            implementation(libs.kotlinx.coroutines.swing)
        }

        getByName("androidMain").dependencies {
            implementation(libs.kiteimage)
            implementation(libs.kiteimage.compose)
            // Supplies Dispatchers.Main on Android.
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

dependencies {
    // The desugaring runtime is consumed by the app's dexing step, so the dependency is declared
    // here as well as enabled on the target above: the library variant alone does not put it on the
    // app's compile classpath.
    add("coreLibraryDesugaring", libs.com.android.desugar.jdk.libs)
}

compose.desktop {
    application {
        mainClass = "mct.gui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "mct"
            packageVersion = "0.0.1"
        }

        jvmArgs("--enable-native-access=ALL-UNNAMED")

        buildTypes.release {
            proguard {
                version = libs.versions.proguard
                configurationFiles.from(project.file("proguard-rules.pro"))
                optimize = true
                obfuscate = true
                joinOutputJars = true
            }
        }
    }
}

// The shadow plugin does not prefix its task with the target name, so this stays `shadowJar`
// (unlike KGP's own `desktopRun`).
tasks.withType<ShadowJar>().configureEach {
    mergeServiceFiles()
}



