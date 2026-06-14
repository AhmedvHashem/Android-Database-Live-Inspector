// Root build — no plugins applied at this level; each submodule owns its plugin set.
//
// Single user-facing build command. The dependsOn list grows as :inspector comes online
// (Stage 3 of plan.md).
tasks.register("buildAll") {
    group = "build"
    description = "Builds the plugin zip (with the dex bundled), the agent AAR, and the inspector dex."
    dependsOn(":plugin:buildPlugin", ":agent:assembleRelease")
}
