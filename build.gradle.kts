plugins {
    id("java")
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "com.hashem.databaseliveinspector"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
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

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        id = "com.hashem.databaseliveinspector"
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
