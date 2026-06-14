plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "dev.ahmedvhashem.databaseliveinspector"
version = "1.0.0"

java {
    withSourcesJar()
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "protocol"
            version = project.version.toString()
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // The root `gradle.properties` disables the auto-stdlib dep because the IntelliJ plugin
    // host provides it at runtime. A plain JVM library has no such host, so we add it back
    // explicitly here. (The :agent / :inspector consumers run on Android where AGP ships
    // stdlib; the :plugin consumer ignores this at runtime in favor of the IDE's stdlib.)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
