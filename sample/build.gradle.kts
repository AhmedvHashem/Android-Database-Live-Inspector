plugins {
    id("com.android.application")
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

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
}
