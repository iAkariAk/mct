@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class, ExperimentalTime::class)
@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertCompilationFilter
import kotlin.time.ExperimentalTime
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type as BKType

plugins {
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.powerassert)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.goncalossilva.resources)
}

kotlin {
    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
//  No support due to kmp-zip
//    js {
//        browser {
//            testTask {
//                useKarma {
//                    useChromeHeadless()
//                }
//            }
//        }
//        nodejs { testTask { useMocha() } }
//
//    }
    wasmJs {
        browser()
        nodejs()
    }
    mingwX64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.serialization.json.okio)
            api(libs.kotlinx.serialization.cbor)
            api(libs.kotlinx.serialization.protobuf)
            api(libs.kotlinx.coroutines.core)
            api(project.dependencies.platform((libs.arrow.stack)))
            api(libs.bundles.arrow)
            api(libs.knbt)
            api(libs.bundles.okio)
            api(libs.bundles.kotlinx.io)
            api(libs.kmpzip.core)
            api(libs.kmpzip.okio)
            api(libs.kmpdiff)
            api(libs.kompress.core)
            api(libs.kompress.zlib)
            api(libs.jetbrains.annotations)
            api(libs.kotlinx.schema.generator.json)
        }

        wasmJsMain.dependencies {
            implementation(kotlinWrappers.js)
        }

        commonTest.dependencies {
            implementation(libs.bundles.kotest)
            implementation(libs.goncalossilva.resources)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }

    powerAssert {
        functions = listOf("kotlin.assert", "kotlin.require", "kotlin.check")
        compilationFilter = PowerAssertCompilationFilter.ALL
    }
}

buildkonfig {
    packageName = "mct"
    exposeObjectWithName = "MCTBuildInfo"

    defaultConfigs {
        buildConfigField(BKType.STRING, "VERSION", version.toString(), const = true)
        buildConfigField(BKType.INT, "VERSION_CODE", "1", const = true)
        buildConfigField(BKType.LONG, "BUILD_TIME", System.currentTimeMillis().toString(), const = true)
    }
}