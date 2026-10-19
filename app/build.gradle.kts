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
        // The online version is published on Cafe Bazaar under com.yadavard.app.plus, so it also installs next to
        // the personal offline build (com.yadavard.app). The package can never change once published.
        applicationIdSuffix = ".plus"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "3.2.1"
    }
    // Test builds use one fixed, public key so each new APK installs over the previous one.
    // Store builds use the private Bazaar key from CI secrets (see .github/workflows/android.yml); when it is not
    // configured, the store build is simply skipped.
    val storeKey = System.getenv("YADAR_STORE_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        create("yadar") {
            storeFile = file("yadar.keystore")
            storePassword = "yadar-public-key"
            keyAlias = "yadar"
            keyPassword = "yadar-public-key"
        }
        if (storeKey != null) create("store") {
            storeFile = storeKey
            storePassword = System.getenv("YADAR_STORE_PASSWORD")
            keyAlias = System.getenv("YADAR_STORE_ALIAS")
            keyPassword = System.getenv("YADAR_STORE_KEY_PASSWORD")
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
        if (storeKey != null) create("store") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("store")
            matchingFallbacks += "release"
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
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
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
