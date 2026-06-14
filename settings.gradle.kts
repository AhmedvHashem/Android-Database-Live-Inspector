pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }

    plugins {
        kotlin("jvm") version "2.3.21"
        kotlin("plugin.serialization") version "2.3.21"
        id("org.jetbrains.intellij.platform") version "2.16.0"
        id("com.android.library") version "9.2.1"
    }
}

rootProject.name = "Android Database Live Inspector"

include(":plugin")
include(":protocol")
include(":agent")
include(":inspector")
include(":stubs")
