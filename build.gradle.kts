import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.goncalossilva.resources) apply false
}

subprojects {
    group = "io.github.iakariak.mct"
    version = "0.0-SNAPSHOT"

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        kotlin.compilerOptions {
            freeCompilerArgs.addAll(
                "-Xcontext-parameters",
                "-Xwarning-level=NOTHING_TO_INLINE:disabled"
            )
            optIn.addAll(
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "arrow.core.raise.ExperimentalRaiseAccumulateApi",
                "kotlin.contracts.ExperimentalContracts",
                "net.benwoodworth.knbt.OkioApi"
            )
        }
    }
}