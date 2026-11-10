plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.bunnlist.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.devmohd.bunnlist"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"
        // Google sign-in: the Web OAuth client ID (public, not a secret). Empty hides the button.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("googleWebClientId").getOrElse("")}\"")
    }

    signingConfigs {
        // One shared debug key, so test builds from any machine update each other and
        // match the SHA-1 registered for the Android OAuth client. Debug only, never for Play.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ship native debug symbols in the bundle, so Play can symbolicate crashes and ANRs.
            ndk { debugSymbolLevel = "FULL" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.palette)
    implementation(libs.exif)
    implementation(libs.security.crypto)
}
