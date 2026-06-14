plugins {
    // AGP 9.0+ ships Kotlin support built in — no separate kotlin-android plugin needed.
    id("com.android.library")
    `maven-publish`
}

group = "dev.ahmedvhashem.databaseliveinspector"
version = "1.0.0"

android {
    namespace = "dev.ahmedvhashem.databaseliveinspector.agent"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    publishing {
        singleVariant("release") { /* defaults */ }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "agent"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(project(":protocol"))
    // Wraps Room's open-helper factory; the public API surface returns RoomDatabase.Builder<T>,
    // so consumers (the app) already have room-runtime on their classpath.
    implementation("androidx.room:room-runtime:2.8.4")

    testImplementation("junit:junit:4.13.2")
}
