plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.pladum.tablero.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.pladum.tablero.tv"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
