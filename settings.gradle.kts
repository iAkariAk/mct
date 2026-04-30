@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "0.10.0"
}

dependencyResolutionManagement {
    rulesMode = RulesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }

    versionCatalogs {
        create("kotlinWrappers") {
            val wrappersVersion = "2026.4.2"
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
        }
    }
}


rootProject.name = "mct"

include(
    "mct",
    "cli",
    "gui",
//    "web"
)