@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        maven("https://raw.githubusercontent.com/graalvm/native-build-tools/snapshots")
        gradlePluginPortal()
    }
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
    "extra",
    "cli",
    "cli-graal",
    "gui",
//    "web"
)