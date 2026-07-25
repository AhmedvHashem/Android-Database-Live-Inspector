import org.gradle.api.tasks.bundling.Jar

plugins {
    // AGP 9.0+ ships Kotlin support built in — no separate kotlin-android plugin needed.
    id("com.android.library")
    `maven-publish`
}

group = "dev.ahmedvhashem.databaseliveinspector"

evaluationDependsOn(":protocol")
val protocolJar = project(":protocol").tasks.named<Jar>("jar").flatMap { it.archiveFile }
val githubRepository = providers.environmentVariable("GITHUB_REPOSITORY")
    .orElse("AhmedvHashem/Android-Database-Live-Inspector")

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
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${githubRepository.get()}")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR")
                    .orElse("github-actions")
                    .get()
                password = providers.environmentVariable("GITHUB_TOKEN")
                    .orElse("missing-token")
                    .get()
            }
        }
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

repositories {
    google()
    mavenCentral()
}

dependencies {
    // A project dependency would be emitted as a separate Maven dependency and would make the
    // published agent unusable unless :protocol were published too. A local JAR dependency is
    // packaged under the AAR's libs/ directory, keeping the agent a single consumable artifact.
    implementation(files(protocolJar))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") {
        exclude(group = "org.jetbrains.kotlin")
    }
    // Wraps Room's open-helper factory; the public API surface returns RoomDatabase.Builder<T>,
    // so consumers (the app) already have room-runtime on their classpath.
    implementation("androidx.room:room-runtime:2.8.4")

    testImplementation("junit:junit:4.13.2")
}
