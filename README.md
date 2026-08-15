<img width="64" height="64" alt="default" src="https://github.com/user-attachments/assets/a676f1b3-8ac1-48dd-817b-4364e95f75d7" />

# Android Database Live Inspector

Android Database Live Inspector is an Android Studio App Inspection plugin for watching Room/SQLite database activity from a running debug app. It records SQL statements, bind args, timing, errors, and small result previews, then shows them in a custom App Inspection tab.

**Install from JetBrains Marketplace:** [Install Plugin](https://plugins.jetbrains.com/plugin/33121-android-database-live-inspector)

<img width="1451" height="883" alt="request" src="https://github.com/user-attachments/assets/594861ab-b041-4f4f-bce6-a007811a80e6" />

<img width="1450" height="883" alt="respone" src="https://github.com/user-attachments/assets/871351a7-727b-44f8-ae4c-48bc95f86632" />

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

Release versions of the agent are published to Maven Central with their protocol implementation
bundled into the AAR.
It can be used from any app without GitHub credentials:

```kotlin
repositories {
    mavenCentral()
}
```

Add the agent to your debug app and wrap your Room builder:

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
