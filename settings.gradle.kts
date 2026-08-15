pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }

    plugins {
        kotlin("jvm") version "2.4.10"
        kotlin("android") version "2.4.10"
        kotlin("plugin.serialization") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.11"
        id("org.jetbrains.intellij.platform") version "2.18.1"
        id("com.android.library") version "9.3.1"
        id("com.android.application") version "9.3.1"
        id("com.vanniktech.maven.publish") version "0.37.0"
    }
}

rootProject.name = "Android Database Live Inspector"

include(":plugin")
include(":protocol")
include(":agent")
include(":inspector")
include(":stubs")
include(":sample")
