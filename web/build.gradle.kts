
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}


kotlin {
        js(IR) {
            browser {
                testTask {
                    useKarma {
                        useChromeHeadless()
                    }
                }
                binaries.executable()
            }
        }


    sourceSets {
        jsMain.dependencies {
            implementation(project(":mct"))
//            implementation(kotlinWrappers.web)
//            implementation(kotlinWrappers.browser)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }

        commonTest.dependencies {
            implementation(libs.bundles.kotest)
        }
    }
}
