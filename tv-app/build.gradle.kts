plugins {
    alias(libs.plugins.android.application)
}

// The one number to bump for a release (major.minor.patch, minor and patch under 100). The release workflow checks
// that the pushed tag is v<this>; versionCode follows from it, so every release installs over the one before.
val appVersionName = "0.1.0"
val appVersionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "minor and patch must stay under 100: $appVersionName" }
    major * 10_000 + minor * 100 + patch
}

// Release signing, from Gradle properties: ~/.gradle/gradle.properties locally, ORG_GRADLE_PROJECT_* in the release
// workflow (docs/releasing.md). Without them the release APK is built unsigned.
val releaseKeystore = providers.gradleProperty("TABLERO_KEYSTORE_FILE").orNull

android {
    namespace = "net.pladum.tablero.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.pladum.tablero.tv"
        minSdk = 22
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.gradleProperty("TABLERO_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("TABLERO_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("TABLERO_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        // The language is chosen in the app, so an App Bundle must ship every language to every device.
        language { enableSplit = false }
    }

    lint {
        // Every bundled language must stay complete (already the default severity; kept explicit on purpose).
        error += "MissingTranslation"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // LocalesConfigTest reads the resource files directly; without this a new values-* folder that adds no
            // resource ids would leave the test task up to date.
            it.inputs.dir("src/main/res").withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
