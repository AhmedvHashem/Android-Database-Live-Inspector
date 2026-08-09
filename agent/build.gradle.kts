import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "dev.ahmedvhashem.databaseliveinspector.agent"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    description = "Java Docs"
    archiveClassifier.set("javadoc")
    from(layout.projectDirectory.file("src/main/javadoc/index.html"))
}

mavenPublishing {
    coordinates(project.group.toString(), "agent", project.version.toString())

    configure(
        AndroidSingleVariantLibrary(
            // Static src/main/javadoc/index.html stub, not generated Javadoc — attached below.
            javadocJar = JavadocJar.None(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        )
    )

    pom {
        name.set("Android Database Live Inspector Agent")
        description.set("Room/SQLite query capture agent for Android Database Live Inspector.")
        url.set("https://github.com/AhmedvHashem/Android-Database-Live-Inspector")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("AhmedvHashem")
                name.set("Ahmed Hashem")
                url.set("https://github.com/AhmedvHashem")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/AhmedvHashem/Android-Database-Live-Inspector.git")
            developerConnection.set("scm:git:ssh://git@github.com/AhmedvHashem/Android-Database-Live-Inspector.git")
            url.set("https://github.com/AhmedvHashem/Android-Database-Live-Inspector")
        }
    }

    signAllPublications()
    publishToMavenCentral(automaticRelease = true)
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("maven") {
        artifact(javadocJar)
    }
}


evaluationDependsOn(":protocol")
val protocolJar = project(":protocol").tasks.named<Jar>("jar").flatMap { it.archiveFile }
repositories {
    google()
    mavenCentral()
}

dependencies {
    // A project dependency would be emitted as a separate Maven dependency and would make the
    // published agent unusable unless :protocol were published too. A local JAR dependency is
    // packaged under the AAR's libs/ directory, keeping the agent a single consumable artifact.
    implementation(files(protocolJar))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") {
        exclude(group = "org.jetbrains.kotlin")
    }
    // Wraps Room's open-helper factory; the public API surface returns RoomDatabase.Builder<T>,
    // so consumers (the app) already have room-runtime on their classpath.
    implementation("androidx.room:room-runtime:2.8.4")

    testImplementation("junit:junit:4.13.2")
}
