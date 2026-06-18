buildscript {
    dependencies {
        classpath(libs.proguard)
    }
}

plugins {
    application
    kotlin("jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.graalvm.native)
}

dependencies {
    implementation(project(":cli"))
}

val MAIN_CLASS = "mct.cli.MainKt"

application {
    mainClass = MAIN_CLASS
}

tasks.shadowJar {
    archiveClassifier.set("all")
}

graalvmNative {
    metadataRepository {
        enabled = true
    }

    binaries {
        named("main") {
            imageName = "mct"
            mainClass = MAIN_CLASS
            debug = false
            verbose = false
            sharedLibrary = false
            richOutput = false
            quickBuild = false

            useFatJar = true
            buildArgs.add("--link-at-build-time")

            buildArgs.add("-O4")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:+InstallExitHandlers")
            buildArgs.add("--enable-url-protocols=https")
            // Runtime options
            runtimeArgs.add("--help")

        }
    }

    agent {
        defaultMode.set("standard")
        enabled.set(true)

        modes {
            standard {
            }
            conditional {
                userCodeFilterPath.set("filter.json")
                extraFilterPath.set("filter.json")
            }
            direct {
                options.add("experimental-configuration-with-origins")
            }
        }

        callerFilterFiles.from("filter.json")
        accessFilterFiles.from("filter.json")
        builtinCallerFilter.set(true)
        builtinHeuristicFilter.set(true)
        enableExperimentalPredefinedClasses.set(false)
        enableExperimentalUnsafeAllocationTracing.set(false)
        trackReflectionMetadata.set(true)

        metadataCopy {
            inputTaskNames.add("run")
            outputDirectories.add("src/main/resources/META-INF/native-image/io.github.iakariak.mct/cli-graal/")
            mergeWithExisting.set(true)
        }

        tasksToInstrumentPredicate.set { true }
    }
}