plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yadavard.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yadavard.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "2.8.1"
    }
    // One fixed key for every build so each new APK installs over the previous one without losing data.
    // It is intentionally public: this app is distributed as a personal sideloaded APK, not via a store.
    signingConfigs {
        create("yadar") {
            storeFile = file("yadar.keystore")
            storePassword = "yadar-public-key"
            keyAlias = "yadar"
            keyPassword = "yadar-public-key"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("yadar") }
        release {
            signingConfig = signingConfigs.getByName("yadar")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
