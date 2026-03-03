@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    rulesMode = RulesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "mct"

include("mct", "cli")