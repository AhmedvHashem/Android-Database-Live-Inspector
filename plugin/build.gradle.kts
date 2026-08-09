import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.ahmedvhashem.databaseliveinspector"

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    publishing {
        token = providers.gradleProperty("JETBRAINS_MARKETPLACE_TOKEN")
    }

    // Run the same Plugin Verifier Marketplace runs, before uploading: ./gradlew verifyPlugin
    pluginVerification {
        ides {
            create(IntelliJPlatformType.AndroidStudio, "2026.1.2.10")
        }
    }

    pluginConfiguration {
        id = "dev.ahmedvhashem.databaseliveinspector"
        name = "Android Database Live Inspector"
        version = project.version.toString()
        description = """
            Live SQLite/Room database inspector for Android — contributes a tab to Android Studio's
            App Inspection window.
        """.trimIndent()
        vendor {
            name = "Ahmed Hashem"
            url = "https://github.com/ahmedvhashem/android-database-live-inspector"
        }
        // Pinned to the platform build we compile against (Android Studio 2026.1.2.10 = 261.*).
        // The App Inspection APIs are internal, so widening this range means shipping against
        // classes we never compiled or verified against.
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "261.*"
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Wire JSON types are shared with :agent / :inspector — same module on the JVM side.
    implementation(project(":protocol"))

    intellijPlatform {
        // app inspection API source code is at https://github.com/JetBrains/android/tree/idea/2026.1/app-inspection

        // App Inspection APIs are internal and version-specific, so use an exact stable Android
        // Studio build. Unlike a local installation path, this is reproducible on CI and across
        // contributor machines.
        androidStudio("2026.1.2.10")
        // Brings android.jar (which contains the app-inspection IDE classes + the
        // appInspectorTabProvider extension point) onto the compile classpath.
        bundledPlugin("org.jetbrains.android")
    }
}

// Stage 5: bundle the inspector DEX jar into the plugin's classpath at `inspector/inspector.jar`,
// where `AppInspectorJar(name = "inspector.jar", developmentDirectory = "inspector", ...)` finds
// it at launch time. Rebuilt automatically whenever :inspector changes.
tasks.named<ProcessResources>("processResources") {
    dependsOn(":inspector:dexJar")
    from(project(":inspector").tasks.named("dexJar")) {
        into("inspector")
    }
}
