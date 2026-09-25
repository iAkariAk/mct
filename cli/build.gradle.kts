@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class, KotlinNativeCacheApi::class)

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
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
            api(libs.arrow.suspendapp)
            api(libs.clikt)
            api(libs.mordant)
            api(libs.mordant.coroutines)
            api(libs.mordant.markdown)
            api(libs.kotlinx.serialization.json.okio)
            api(libs.kotlinx.schema.generator.json)
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.io.okio)
            api(libs.ktoml.core)
            api(libs.kiteimage)
            implementation(project(":mct"))
            implementation(project(":extra"))
        }

        commonTest.dependencies {
            implementation(libs.bundles.kotest)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("META-INF/*.kotlin_module")
}

