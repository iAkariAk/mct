plugins {
    // AGP's built-in Kotlin: applying `org.jetbrains.kotlin.android` as well would fail, because the
    // `kotlin` extension is already registered by AGP 9.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.iakariak.mct"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.iakariak.mct"
        // Matches `:gui`'s Android target; see the note there.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "snapshot"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        // `:gui`'s Android target is desugared too, but L8 runs at app dex time, so the app module
        // is the site that actually has to enable it and carry the runtime.
        isCoreLibraryDesugaringEnabled = true
        // Kept equal to the Kotlin level below: the shared Compose code is compiled at JVM 25, and
        // the compiler refuses to inline it into anything lower. D8 accepts that class-file version.
        sourceCompatibility = JavaVersion.toVersion(25)
        targetCompatibility = JavaVersion.toVersion(25)
    }

    buildTypes {
        release {
            // Shrinking and obfuscation mirror the desktop release (`gui`'s `buildTypes.release`),
            // whose rules this module's `proguard-rules.pro` is modelled on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro"),
            )
            // Signed with the debug key on purpose: this APK is distributed by sideloading from CI,
            // not through a store, so there is no release keychain to protect. A real signing config
            // replaces this when one exists.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        // Same level as the toolchain and as `:gui`'s Android target, so the shared Compose code it
        // calls can be inlined.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

dependencies {
    implementation(project(":gui"))
    implementation(libs.androidx.activity.compose)
    coreLibraryDesugaring(libs.com.android.desugar.jdk.libs)
}
