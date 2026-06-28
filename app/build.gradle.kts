plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.streamflow.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streamflow.app"
        minSdk = 24
        targetSdk = 34
        // CI overrides these via -PreleaseVersionName/-PreleaseVersionCode so the APK
        // attached to a GitHub release actually reports the tagged version; the literals
        // below are only the fallback for local/dev builds.
        versionCode = (project.findProperty("releaseVersionCode") as String?)?.toIntOrNull() ?: 8
        versionName = (project.findProperty("releaseVersionName") as String?) ?: "0.7.0"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // AGP's auto-generated debug keystore is regenerated per machine, so every
        // GitHub Actions run (a fresh VM) signed releases with a different random key,
        // making every "update" install fail with a signature mismatch. Pinning a
        // committed keystore here keeps every CI-built APK signed identically.
        getByName("debug") {
            storeFile = file("streamflow-debug.keystore")
            storePassword = "streamflow-debug"
            keyAlias = "streamflow-debug"
            keyPassword = "streamflow-debug"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Networking used by the extractor's Downloader implementation
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // YouTube parsing backend (same approach NewPipe uses: no official API, no ads)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    // Playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Local persistence: watch history / bookmarks (Premium-like "library")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Thumbnail loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
