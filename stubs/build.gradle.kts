plugins {
    kotlin("jvm")
}

repositories { mavenCentral() }

dependencies {
    // The root gradle.properties disables auto-stdlib; this is a plain JVM module, so add it.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}

kotlin {
    jvmToolchain(17)
}
