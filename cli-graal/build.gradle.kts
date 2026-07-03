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
    // kotlin-logging 8.0.01 ships with GraalVM substitution classes that reference
    // KLoggerFactory$Companion which was refactored away. Exclude them to avoid
    // native-image build errors.
    exclude("io/github/oshai/kotlinlogging/internal/Target_**")
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

            useFatJar = false
            classpath.setFrom(tasks.shadowJar)
            buildArgs.add("--link-at-build-time")
            buildArgs.add("--initialize-at-build-time=io.github.oshai.kotlinlogging")

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
