pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }

    plugins {
        kotlin("jvm") version "2.3.21"
        kotlin("plugin.serialization") version "2.3.21"
        id("org.jetbrains.intellij.platform") version "2.18.1"
        id("com.android.library") version "9.3.1"
    }
}

rootProject.name = "Android Database Live Inspector"

include(":plugin")
include(":protocol")
include(":agent")
include(":inspector")
include(":stubs")
