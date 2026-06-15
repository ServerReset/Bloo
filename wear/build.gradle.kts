plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.bloo.wear"
    compileSdk = 37

    defaultConfig {
        // Must match the phone app so the Wearable Data Layer pairs the two.
        applicationId = "com.bloo.bluelink"
        // Wear OS 3+ (the first Compose-capable Wear release).
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Reuse the phone app's checked-in debug key. The Data Layer only links
        // a phone and watch app when they share both applicationId AND signature.
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "Bloo-Wear.apk"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Google's Wear OS Compose stack (Material 3 Expressive for watches).
    val wear = "1.5.1"
    implementation("androidx.wear.compose:compose-material3:$wear")
    implementation("androidx.wear.compose:compose-foundation:$wear")
    implementation("androidx.wear.compose:compose-navigation:$wear")
    implementation("androidx.wear:wear-input:1.1.0")

    // Regular Compose primitives (Box/Column/Canvas/pager) + icons, aligned via
    // the Compose BOM so the versions match what Wear Compose is built against.
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    debugImplementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended")
}
