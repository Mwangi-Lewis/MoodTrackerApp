plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.moodtrackerapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.moodtrackerapp"
        minSdk = 24
        targetSdk = 35
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

    buildFeatures {
        compose = true
        // You’re not using ML Model Binding generated classes, so keep this OFF.
        // Turn it on only if you configure the mlModelBinding tool window.
        mlModelBinding = false
    }

    // Compose compiler that matches Kotlin 1.9.24
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    // Ensure TFLite models are not compressed by aapt
    // (either of these blocks is fine for AGP 8.x; keeping both is harmless)
    android {
        // …
        androidResources {
            noCompress.addAll(listOf("tflite", "lite"))
        }

        packaging {
            resources {
                excludes.addAll(
                    listOf(
                        "/META-INF/{AL2.0,LGPL2.1}",
                        "META-INF/LICENSE*",
                        "META-INF/NOTICE*"
                    )
                )
            }
        }

    }
}

dependencies {

    // ---------- Compose stack ----------
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.material3:material3:1.3.0")

    // Material icons (provides Outlined icons like Mic, AttachFile, StarBorder, etc.)
    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    // ---------- Coroutines ----------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ---------- Firebase ----------
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // build.gradle (app)
    implementation("com.google.android.material:material:1.12.0")
    
    implementation(libs.androidx.foundation)

    // ---------- CameraX ----------
    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("com.google.guava:guava:31.1-android")

    // ListenableFuture for ProcessCameraProvider (AndroidX shaded; Guava not required)
    implementation("androidx.concurrent:concurrent-futures:1.1.0")

    // ---------- ML Kit face detection ----------
    implementation("com.google.mlkit:face-detection:16.1.6")

    // ---------- TensorFlow Lite (CPU) ----------
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.runtime:runtime:1.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")




    // build.gradle (Module)
    dependencies {
        implementation("androidx.compose.material:material-icons-extended:1.7.5") // or version that matches your BOM
    }

    // ---------- Tests ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}