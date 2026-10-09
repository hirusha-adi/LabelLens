plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Where the sample API in ../api is reachable from the phone. The default works on a
// device or emulator after `adb reverse tcp:8787 tcp:8787`. Override with
// -PapiBaseUrl=http://192.168.1.20:8787 to reach it over Wi-Fi instead.
val apiBaseUrl = providers.gradleProperty("apiBaseUrl").getOrElse("http://127.0.0.1:8787")

android {
    namespace = "app.labellens"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.labellens"
        minSdk = 28
        targetSdk = 37
        versionCode = 412
        versionName = "2.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
