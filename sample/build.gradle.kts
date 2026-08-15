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

val agentSource = providers.gradleProperty("sampleAgentSource").orElse("maven")
val agentVersion = providers.gradleProperty("agentVersion").orElse("1.0.4")

dependencies {
    // Agent: debug-only so the capture layer is stripped from release builds entirely.
    when (agentSource.get()) {
        "maven" -> debugImplementation(
            "dev.ahmedvhashem.databaseliveinspector:agent:${agentVersion.get()}"
        )
        "local" -> debugImplementation(project(":agent"))
        else -> error("sampleAgentSource must be 'maven' or 'local', got '${agentSource.get()}'")
    }

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.material)
}
