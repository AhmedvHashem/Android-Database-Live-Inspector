# Database Live Inspector

Database Live Inspector is an Android Studio App Inspection plugin for watching Room/SQLite database activity from a running debug app. It records SQL statements, bind args, timing, errors, and small result previews, then shows them in a custom App Inspection tab.

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
./gradlew :protocol:publishToMavenLocal :agent:publishToMavenLocal
```

## App setup

Add the agent to your debug app and wrap your Room builder:

```kotlin
dependencies {
    debugImplementation("dev.ahmedvhashem.databaseliveinspector:agent:1.0.0")
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

<img width="2006" height="489" alt="Screenshot 2026-06-16 at 5 33 29 PM" src="https://github.com/user-attachments/assets/b4a86eec-93dc-487f-bff6-a03b65159990" />
<img width="2006" height="493" alt="Screenshot 2026-06-16 at 5 33 32 PM" src="https://github.com/user-attachments/assets/1143f386-ae57-4aba-8bfa-8a31c4ea1dbc" />

