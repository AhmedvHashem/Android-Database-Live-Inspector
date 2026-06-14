plugins {
    id("java")
    // Plugin versions are centralized in settings.gradle.kts → pluginManagement.
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.ahmedvhashem.databaseliveinspector"
version = "1.0.0"

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

        // Target the locally installed Android Studio so the compile classpath matches the
        // runtime exactly — the App Inspection API (com.android.tools.idea.appinspection.*) is
        // internal and version-specific, so we deliberately avoid a downloaded distribution.
        local("/Users/hashem/Applications/Android Studio.app/Contents")
//        androidStudio("2025.2.1.11")
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

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        id = "dev.ahmedvhashem.databaseliveinspector"
        name = "Database Live Inspector"
        version = project.version.toString()
        description = """
            Live SQLite/Room database inspector for Android — contributes a tab to Android Studio's
            App Inspection window.
        """.trimIndent()
        vendor {
            name = "Hashem"
            url = "https://github.com/ahmedvhashem/android-database-live-inspector"
        }
        ideaVersion {
            // Built against Android Studio 2025.2 (platform build 253).
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}
