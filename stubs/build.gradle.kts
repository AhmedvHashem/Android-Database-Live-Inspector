plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

repositories { mavenCentral() }

dependencies {
    // The root gradle.properties disables auto-stdlib; this is a plain JVM module, so add it.
    implementation(libs.kotlin.stdlib)
}
