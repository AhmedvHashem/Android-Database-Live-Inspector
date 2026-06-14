import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.library")
}

android {
    namespace = "dev.ahmedvhashem.databaseliveinspector.inspector"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

repositories {
    google()
    mavenCentral()
}

dependencies {
    // The inspector dex deliberately does NOT depend on :protocol or kotlinx-serialization.
    // Reason: when the app doesn't include the agent AAR, neither :protocol nor
    // kotlinx-serialization are guaranteed to be on the app's classloader (the dex's parent).
    // ART would fail to verify our classes if they referenced types not reachable in the
    // parent classloader. Instead, the inspector handles JSON as raw byte arrays:
    //   - Capture-stream events from the agent are forwarded as-is (already encoded by
    //     :agent's ProtocolCodec into bytes).
    //   - AppInfo / AgentError / CaptureState replies use hand-rolled JSON literals (small
    //     and fully under our control).
    //   - SetCapture is decoded by simple string inspection of the incoming bytes — we only
    //     accept that one command in v1.
    // Bundle is the AAR's classes.jar only (5 .class files).
    compileOnly(project(":stubs"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}

/**
 * Produces `inspector.jar`: a DEX jar with `classes.dex` (from our 3 compiled inspector classes)
 * plus a `META-INF/services/androidx.inspection.InspectorFactory` registration. This is what
 * the plugin's `AppInspectorJar` points at and what AS pushes to the device.
 *
 * Nothing else is bundled — kotlin-stdlib, kotlinx-serialization, the :protocol module, and
 * the :agent module are all resolved at runtime via the inspector classloader's parent
 * delegation to the app's classloader.
 */
val dexJar by tasks.registering {
    group = "build"
    description = "Builds inspector.jar (d8-dexed inspector + service file) for plugin bundling."

    dependsOn("assembleRelease")

    val classesJar = layout.buildDirectory.file(
        "intermediates/aar_main_jar/release/syncReleaseLibJars/classes.jar"
    )
    val outputJar = layout.buildDirectory.file("inspector/inspector.jar")
    val workDir = layout.buildDirectory.dir("inspector-work")
    val androidHome = providers.environmentVariable("ANDROID_HOME").orElse(
        providers.systemProperty("user.home").map { "$it/Library/Android/sdk" }
    )
    val factoryFqn = "dev.ahmedvhashem.databaseliveinspector.inspector.DatabaseLiveInspectorInspectorFactory"

    inputs.file(classesJar)
    outputs.file(outputJar)

    doLast {
        // Auto-detect latest installed build-tools version rather than hard-coding 37.0.0 —
        // contributors with 36.x or 38.x then don't hit an opaque "No such file" failure.
        val buildToolsDir = file("${androidHome.get()}/build-tools")
        val latestBuildTools = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?: throw GradleException(
                "No Android SDK build-tools found under $buildToolsDir — run " +
                    "'sdkmanager \"build-tools;37.0.0\"' (or any recent version).",
            )
        val d8 = File(latestBuildTools, "d8").absolutePath
        require(file(d8).exists()) {
            "d8 not found at $d8 (build-tools ${latestBuildTools.name} appears incomplete)"
        }
        val work = workDir.get().asFile
        work.deleteRecursively()
        work.mkdirs()

        // 1. d8 our compiled classes into classes.dex (ProcessBuilder rather than project.exec,
        // which Gradle 9 removed; ExecOperations DI would work too but this is simpler).
        val proc = ProcessBuilder(d8, "--output", work.absolutePath, classesJar.get().asFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val d8Output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        if (exit != 0) {
            throw GradleException("d8 failed with exit $exit:\n$d8Output")
        }

        // 2. Assemble final jar: classes.dex + META-INF/services declaration.
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            file("${work.absolutePath}/classes.dex").inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/services/androidx.inspection.InspectorFactory"))
            zip.write("$factoryFqn\n".toByteArray())
            zip.closeEntry()
        }
        logger.lifecycle("Built ${out.absolutePath}")
    }
}
