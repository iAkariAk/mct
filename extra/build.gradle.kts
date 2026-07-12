@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class, KotlinNativeCacheApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeBinaryContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.shadow)
    alias(libs.plugins.goncalossilva.resources)
}

kotlin {
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
            api(libs.kotlinx.serialization.json.okio)
            api(libs.openai.client)
            api(libs.ktor.client.core)
            api(libs.ktor.client.cio)
            api(libs.ktor.client.logging)
            api(libs.ktor.client.contentNegotiation)
            api(libs.ktor.serialization.kotlinx.json)
            implementation(libs.slf4j.nop)
            implementation(project(":mct"))
        }

        commonTest.dependencies {
            implementation(libs.bundles.kotest)
            implementation(libs.goncalossilva.resources)
        }

        jvmMain.dependencies {
            implementation(libs.jtokkit)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
