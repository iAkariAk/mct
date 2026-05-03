@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.fus.internal.isCiBuild

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.shadow)
}

kotlin {
    jvmToolchain(21)

    jvm {
        mainRun {
            mainClass.set("mct.gui.MainKt")
        }
    }

    sourceSets {
        jvmMain.dependencies {
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
            implementation(project(":mct"))
            implementation(project(":extra"))
            implementation(libs.compose.material3)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}

compose.desktop {
    application {
        mainClass = "mct.gui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageVersion = "0.0.1"
        }

        buildTypes.release {
            proguard {
                version.set("7.9.1")
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
}
