plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.ahmedvhashem.databaseliveinspector"

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

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
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
        ideaVersion {
            // Preserve the compatibility floor already advertised by the Marketplace release.
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}
