// Root build — no plugins applied at this level; each submodule owns its plugin set.

// Get version from the -PreleaseVersion property, GITHUB_REF_NAME, or fall back to the latest
// Git tag.
val releaseVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
    .orElse(providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
        isIgnoreExitValue = true
    }.standardOutput.asText)
    .map { it.trim().removePrefix("v") }
    .orElse("0.0.1-SNAPSHOT")

allprojects {
    group = "dev.ahmedvhashem.databaseliveinspector"
    version = releaseVersion.get()
}

// Single user-facing build command. The dependsOn list grows as :inspector comes online
// (Stage 3 of plan.md).
tasks.register("buildAll") {
    group = "build"
    description = "Builds the plugin zip (with the dex bundled), the agent AAR, and the inspector dex."
    dependsOn(":plugin:buildPlugin", ":agent:assembleRelease")
}

tasks.register("publishAgent") {
    group = "publishing"
    description = "Publishes the agent AAR to Maven Central. Requires -PreleaseVersion."
    dependsOn(":agent:publishToMavenCentral")
}

tasks.register("publishPlugin") {
    group = "publishing"
    description = "Publishes the IDE plugin to JetBrains Marketplace. Requires -PreleaseVersion."
    dependsOn(":plugin:publishPlugin")
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes both the agent to Maven Central and the plugin to JetBrains Marketplace."
    dependsOn("publishAgent", "publishPlugin")
}
