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
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/iakariak/knbt") // This's my fork who fixed some bugs and added wasm support
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        mavenCentral()
        google()
        mavenLocal()
    }

    versionCatalogs {
        create("kotlinWrappers") {
            val wrappersVersion = "2026.7.0"
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