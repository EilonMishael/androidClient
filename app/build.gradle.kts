plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" // Added for Kotlinx Serialization
    // Removed alias(libs.plugins.kotlin.compose) as we are not using Compose
}

android {
    namespace = "com.example.messagingapp"
    compileSdk = 34 // WebRTC usually works well with recent SDK versions

    defaultConfig {
        applicationId = "com.example.messagingapp"
        minSdk = 24 // Minimum SDK for WebRTC support
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        // We will use traditional XML layouts for now as requested.
        // compose = true
        viewBinding = true // Enable View Binding for easier UI element access
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Removed Compose-related dependencies

    // WebRTC
    // implementation("org.webrtc:google-webrtc:1.0.32006") // Removed deprecated Google WebRTC dependency
    implementation("com.dafruits:webrtc:123.0.0") // Using community-maintained WebRTC fork

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle extensions for LiveData and ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Material Design components
    implementation("com.google.android.material:material:1.10.0")

    // Kotlinx Serialization for JSON (for future signaling)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Removed Compose-related androidTest/debug dependencies
}