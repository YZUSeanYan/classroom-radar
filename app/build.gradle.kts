plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.classroomradar.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.classroomradar.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 14
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The bundled offline models already dominate the package. Restricting the
            // native runtime to modern 64-bit Android removes three duplicate ORT/JNI
            // binaries without changing recognition quality on supported devices.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += setOf("onnx", "model")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    // AndroidLiquidGlass (Kyant0 backdrop) implements the real liquid-glass renderer.
    // It is pure Compose/AGSL (no native .so), so the arm64-only abiFilters above
    // already satisfy the 64-bit-only packaging requirement. 2.0.0 is compiled against
    // API 37 and would force a compileSdk/AGP upgrade, so use 1.0.6 - same renderer
    // generation that still supports compileSdk 36.
    implementation("io.github.kyant0:backdrop:1.0.6")
    // backdrop 1.0.6 is built against compose-ui 1.10; BOM 2026.06.01 maps ui 1.11.4,
    // the newest BOM still compatible with compileSdk 36 / AGP 9.0.1.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
