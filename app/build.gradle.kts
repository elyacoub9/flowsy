import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.elyacoub9.flowsy"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.elyacoub9.flowsy"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing values live in keystore.properties (gitignored, never printed or committed).
    val keystoreFile = rootProject.file("keystore.properties")
    val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isDebuggable = false
            // R8: shrink, optimize and remove unused code and resources (rules in src/main/keepRules).
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
        // Same R8 build as release, signed with the debug key and installed as a separate app (.check),
        // so the instrumented tests can run against minified code without touching real data.
        create("releaseCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".check"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // gradlew.bat connectedReleaseCheckAndroidTest -PtestRelease runs the instrumented tests on the R8 build.
    if (project.hasProperty("testRelease")) testBuildType = "releaseCheck"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
}

// A release build without keystore.properties stops with a clear message (no secret is printed).
val hasReleaseKeystore = rootProject.file("keystore.properties").exists()
tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
    // Copied into a local value so the task action keeps no reference to this build script (configuration cache).
    val keystorePresent = hasReleaseKeystore
    doFirst {
        if (!keystorePresent) {
            throw GradleException("keystore.properties is missing in the project folder: the release build cannot be signed. Create it from keystore.properties.template.")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // The R8 test build (releaseCheck) needs the same empty test activity; the real release does not get it.
    "releaseCheckImplementation"(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
