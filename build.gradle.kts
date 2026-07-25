// Root build — no plugins applied at this level; each submodule owns its plugin set.

// Release builds pass this from a vX.Y.Z Git tag. Local builds intentionally remain snapshots
// so they cannot be confused with artifacts published by the release workflow.
val releaseVersion = providers.gradleProperty("releaseVersion").orElse("1.0.1-SNAPSHOT")

allprojects {
    version = releaseVersion.get()
}
//
// Single user-facing build command. The dependsOn list grows as :inspector comes online
// (Stage 3 of plan.md).
tasks.register("buildAll") {
    group = "build"
    description = "Builds the plugin zip (with the dex bundled), the agent AAR, and the inspector dex."
    dependsOn(":plugin:buildPlugin", ":agent:assembleRelease")
}
