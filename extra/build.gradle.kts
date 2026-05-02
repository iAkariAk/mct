@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class, KotlinNativeCacheApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeBinaryContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.shadow)
}

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }

        mainRun {
            mainClass.set("mct.cli.MainKt")
        }

    }

    val exeConfigure: KotlinNativeBinaryContainer.() -> Unit = {
        executable {
            baseName = "mct"
            entryPoint = "mct.cli.main"

            disableNativeCache(DisableCacheInKotlinVersion.`2_3_20`, "ld invocation reported errors")
        }
    }
    mingwX64 {
        binaries(exeConfigure)
    }
    linuxX64 {
        binaries(exeConfigure)
    }

    tasks.named<Jar>("jvmJar") {
        manifest {
            attributes["Main-Class"] = "mct.cli.MainKt"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json.okio)
            implementation(libs.openai.client)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(project(":mct"))
        }

        commonTest.dependencies {
            implementation(libs.bundles.kotest)
        }

        jvmMain.dependencies {
            implementation(libs.jtokkit)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
