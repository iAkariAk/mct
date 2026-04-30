@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.goncalossilva.resources)
}

kotlin {
    jvmToolchain(21)
        jvm {
            testRuns.named("test") {
                executionTask.configure {
                    useJUnitPlatform()
                }
            }
        }

//        js(IR) {
//            browser {
//                testTask {
//                    useKarma {
//                        useChromeHeadless()
//                    }
//                }
//            }
//            nodejs { testTask { useMocha() } }
//
//        }
//        wasmJs {
//            browser()
//            nodejs()
//        }
        mingwX64()
        linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.serialization.json.okio)
            api(libs.kotlinx.coroutines.core)
            api(project.dependencies.platform((libs.arrow.stack)))
            api(libs.bundles.arrow)
            api(libs.korlibs.io)
            api(libs.knbt)
            api(libs.bundles.okio)
            api(libs.jetbrains.annotations)
        }

        commonTest.dependencies {
            implementation(libs.bundles.kotest)
            implementation(libs.goncalossilva.resources)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
