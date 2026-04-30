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
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(project(":mct"))
            implementation(project(":cli"))
            implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.20")
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
