import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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
            implementation(compose.desktop.currentOs)
            implementation(project(":mct"))
            implementation(project(":util"))
            implementation(libs.compose.material3)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.filekit.dialogs.compose)
        }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
}

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "mct.gui.MainKt"
    }
}
