plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dwarpallabs.linkguard"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dwarpallabs.linkguard"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from ~/.gradle/gradle.properties (never the repo). Without those
    // properties, as in CI or on F-Droid's build servers, the release build is simply unsigned.
    val keystorePath = providers.gradleProperty("linkguard.keystore").orNull?.trim()
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("linkguard.keystore.password").get()
                keyAlias = providers.gradleProperty("linkguard.key.alias").get()
                keyPassword = providers.gradleProperty("linkguard.key.password").get()
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // F-Droid build: must never pull in Google dependencies.
        create("foss") { dimension = "distribution" }
        // Play Store build: later adds Play Integrity and Safe Browsing.
        create("play") { dimension = "distribution" }
    }

    buildTypes {
        release {
            // Whole-app R8. The template's packageScope (androidx/kotlin/kotlinx only) produced
            // release builds that crash at startup with IllegalAccessError.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Lists every values-* language in Android 13+'s per-app language setting
    // (Settings → Apps → LinkGuard → Language). The default language is in res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}