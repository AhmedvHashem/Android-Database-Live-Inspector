# Android Database Live Inspector

Android Database Live Inspector is an Android Studio App Inspection plugin for watching Room/SQLite database activity from a running debug app. It records SQL statements, bind args, timing, errors, and small result previews, then shows them in a custom App Inspection tab.

**Install from JetBrains Marketplace:** [Install Plugin](https://plugins.jetbrains.com/embeddable/install/33121)

## What is inside

- `plugin`: Android Studio plugin UI and App Inspection tab.
- `inspector`: on-device inspector DEX that Android Studio injects into the app process.
- `agent`: app-side Room/SQLite wrapper that captures query events.
- `protocol`: shared JSON message types.
- `stubs`: compile-time inspection API stubs.

## Build

```bash
./gradlew buildAll
```

The plugin zip is written under `plugin/build/distributions/`. Install it in Android Studio with **Settings > Plugins > Install Plugin from Disk**.

To use the agent from another local app:

```bash
./gradlew :agent:publishToMavenLocal
```

## App setup

The agent is published to GitHub Packages with its protocol implementation bundled into the
AAR. Add the repository to your app, using a GitHub Packages-capable token for local builds:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/AhmedvHashem/Android-Database-Live-Inspector")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}
```

Keep `gpr.user` and `gpr.key` in your user-level `~/.gradle/gradle.properties`, then add the
agent to your debug app and wrap your Room builder:

```kotlin
dependencies {
    debugImplementation("dev.ahmedvhashem.databaseliveinspector:agent:VERSION")
}
```

```kotlin
import dev.ahmedvhashem.databaseliveinspector.agent.DatabaseLiveInspector

DatabaseLiveInspector.install(context)

val builder = Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
DatabaseLiveInspector.attachTo(builder, "app.db")
val db = builder.build()
```

Run the debug app, open Android Studio's **App Inspection** tool window, and select **Database Live Inspector**.

## Release

Releases use one version for the plugin and agent. Add a repository secret named
`JETBRAINS_MARKETPLACE_TOKEN`, merge the release changes through a pull request targeting
`main`, then tag the merged commit with a stable semantic version:

```bash
git switch main
git pull --ff-only
git tag v1.0.2
git push origin v1.0.2
```

The publish workflow rejects non-`vX.Y.Z` tags, commits outside `main`, and commits that GitHub
does not associate with a merged pull request targeting `main`. It publishes the agent to GitHub
Packages first and then publishes the plugin to the default JetBrains Marketplace channel.

<img width="1968" height="408" alt="Screenshot 2026-06-16 at 5 44 46 PM" src="https://github.com/user-attachments/assets/af60060d-1ace-4e40-9375-104c21a573c0" />
<img width="1965" height="412" alt="Screenshot 2026-06-16 at 5 45 15 PM" src="https://github.com/user-attachments/assets/9ddfe8ef-fa20-4e15-8f9f-a0b8a82fc8c0" />
