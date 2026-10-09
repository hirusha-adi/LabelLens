plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Where the API in ../api is reachable from the phone. The default works on a
// device or emulator after `adb reverse tcp:8787 tcp:8787`. Override with
// -PapiBaseUrl=http://192.168.1.20:8787 to reach it over Wi-Fi instead.
val apiBaseUrl = providers.gradleProperty("apiBaseUrl").getOrElse("http://127.0.0.1:8787")

// The OAuth "Web application" client ID from Google Cloud, the same one entered in Supabase's
// Google provider. It is public by design. The Android OAuth client (package name + SHA-1)
// must exist in the same Google project, but its ID is not used anywhere.
val googleWebClientId = providers.gradleProperty("googleWebClientId")
    .getOrElse("989048634874-irjt4kp906tl68ba2jcv1ciepinclj9v.apps.googleusercontent.com")

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
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    testImplementation(libs.junit)
}
