plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "dev.ahmedvhashem.databaseliveinspector.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ahmedvhashem.databaseliveinspector.sample"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Agent: debug-only so the capture layer is stripped from release builds entirely.
    debugImplementation(project(":agent"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.material)
}
