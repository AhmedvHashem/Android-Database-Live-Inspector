# Database Live Inspector — architecture notes (superseded)

> **`plan.md` is the operative plan.** This file records the architectural decisions that led to
> the current `:stubs` module design and is kept for reference. The `:stubs` module described
> here has been fully integrated into `plan.md`. Do not use this file as the source of truth.

---

## 1. The shift in one paragraph

The original plan had four modules: `:plugin`, `:protocol`, `:agent`, `:inspector`. The
`:inspector` DEX contained the `Inspector` subclass, the `InspectorFactory`, and an `AgentBridge`
reflective trampoline (~125 LOC combined). **This plan keeps four modules but collapses the heavy
on-device code into `:agent`:** the `Inspector` and `InspectorFactory` implementations live in the
app's AAR, directly accessible from the app classloader. The plugin still bundles a tiny
`inspector.jar` pushed to the device by AS, but that JAR comes from a new `:stubs` module — it
contains a single ~20-line stub `InspectorFactory` that uses `Class.forName` to delegate to the
real Factory in `:agent`. One reflection call replaces the full `AgentBridge`. No services-file-
only trick; no class-absent JAR.

---

## 2. Strategy still in force

Same as `plan.md` §2.4 — committed to **γ** (agent AAR shipped with the app + one line at
Room-builder time). Live capture only, Network-Inspector-style UI, tracing and redaction dropped.
The four scope decisions from `plan.md` §2.3 stand.

---

## 3. Handoff context (so this plan stands alone)

### 3.1 Repos
| | Path | Role |
|---|---|---|
| Standalone plugin (reference) | `/Users/hashem/Code/Hashem/RoomDBInspector` | Untouched. Source-of-truth for UI, protocol types, timeline model. |
| Standalone agent (reference) | `/Users/hashem/Code/Hashem/glovo-interview-android/inspector-agent` | Untouched. Source-of-truth for capture wrapper + cursor sampler. |
| This project (target) | `/Users/hashem/Code/Hashem/DatabaseLiveInspector` | Current: empty App Inspection tab PoC, confirmed working in AS. To be expanded per this plan. |

### 3.2 Verified facts (carry across)
- **PoC works.** A third-party `AppInspectorTabProvider` registers on Android Studio 253
  (`AI-253.32098.37.2534.15232325`). The empty-tab spike already confirmed this visually.
- **AS install path:** `/Users/hashem/Applications/Android Studio.app`. The plugin compiles
  against it via `intellijPlatform { local("…/Android Studio.app/Contents") + bundledPlugin("org.jetbrains.android") }`.
- **Kotlin version pin (gotcha):** AS 253 ships kotlinc **2.2.20**. JVM modules in this repo
  must use `kotlin("jvm") version "2.2.20"` or the build fails reading platform-jar metadata.
- **Current namespace:** package / group / plugin id all `dev.ahmedvhashem.databaseliveinspector`,
  vendor `Hashem` (plugin.xml is authoritative; build.gradle.kts has stale `"Database Live Inspector"`
  to fix on first edit pass).
- **App Inspection API shape** (from `javap` against the local `android.jar`):
  ```kotlin
  interface AppInspectorTabProvider : Comparable<AppInspectorTabProvider> {
      val launchConfigs: List<AppInspectorLaunchConfig>
      val displayName: String
      fun createTab(project, ideServices, processDescriptor, messengerTargets, parentDisposable): AppInspectorTab
      // + default isApplicable / icon / learnMoreUrl / supportsOffline
  }
  class AppInspectorLaunchConfig(id: String, params: AppInspectorLaunchParams)
  class FrameworkInspectorLaunchParams(jar: AppInspectorJar) : AppInspectorLaunchParams
  class AppInspectorJar(name: String, developmentDirectory: String?, releaseDirectory: String?)
  sealed class AppInspectorMessengerTarget {
      class Resolved(messenger: AppInspectorMessenger) : AppInspectorMessengerTarget()
      class Unresolved(error: String) : AppInspectorMessengerTarget()
  }
  interface AppInspectorMessenger {
      suspend fun sendRawCommand(bytes: ByteArray): ByteArray
      val eventFlow: Flow<ByteArray>
      val scope: CoroutineScope
  }
  ```
- **Result-row capture is already working** in the standalone (`SELECT * FROM order_items` →
  9 rows × 5 cols carried on `query_finished` end-to-end), and the cursor sampler carries the
  recently-fixed frame-cap safety (column cap, full-cell budget, writer drops oversize *events*
  via `dropped_events` rather than killing the session). **Do not re-derive these caps anywhere
  else** — one source of truth.

### 3.3 References
- JetBrains AS source mirror (the App Inspection internals and example inspectors):
  `https://github.com/JetBrains/android/tree/idea/2026.1/app-inspection`
- androidx.inspection README (warns the JAR is "provided by Android Studio at runtime, unlike
  regular libraries"):
  `https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/inspection/inspection/README.md`
- Dev device: Pixel 6, USB serial `1B281FDF6001DG`.

---

## 4. Project structure

```
DatabaseLiveInspector/
├── settings.gradle.kts                              ← include :plugin, :protocol, :agent, :stubs
├── build.gradle.kts                                  ← root: registers the `buildAll` task
├── gradle.properties, gradlew, gradle/wrapper/
├── plan.md                                            ← prior, longer-form plan (kept as reference)
├── plan-no-inspector.md                               ← this file (operative)
│
├── plugin/                                            ← IntelliJ plugin (Kotlin/JVM 2.2.20)
│   ├── build.gradle.kts                               ← intellij-platform 2.x; processResources depends on :stubs:dexJar
│   └── src/main/
│       ├── kotlin/dev/ahmedvhashem/databaseliveinspector/
│       │   ├── DatabaseLiveInspectorTabProvider.kt
│       │   ├── ui/
│       │   │   ├── InspectorPanel.kt                  ← toolbar + top JBTable + bottom JBTabbedPane
│       │   │   ├── QueryTableModel.kt
│       │   │   ├── RequestTab.kt                      ← SQL / args / db / thread / status / errorMessage
│       │   │   └── ResponseTab.kt                     ← captured rows grid + summary
│       │   └── session/
│       │       ├── TimelineModel.kt                   ← bounded log; queryId-keyed pairing
│       │       └── MessengerSession.kt                ← AppInspectorMessenger adapter
│       └── resources/
│           ├── META-INF/plugin.xml
│           └── inspector/inspector.jar                ← produced by :stubs:dexJar
│
├── protocol/                                          ← shared JVM module: wire types + codec
│   ├── build.gradle.kts                               ← kotlin("jvm") 2.2.20 + kotlinx-serialization
│   └── src/main/kotlin/dev/ahmedvhashem/databaseliveinspector/protocol/
│       ├── Messages.kt                                ← sealed ProtocolMessage; 7 subtypes
│       └── ProtocolCodec.kt                           ← encode/decode
│
├── agent/                                             ← Android library. All on-device logic.
│   ├── build.gradle.kts                               ← com.android.library; produces AAR only
│   └── src/main/
│       ├── AndroidManifest.xml                        ← empty (library)
│       └── kotlin/dev/ahmedvhashem/databaseliveinspector/agent/
│           ├── DatabaseLiveInspector.kt               ← public API: install / attachTo / setEnabled
│           ├── DatabaseLiveInspectorInspector.kt      ← extends androidx.inspection.Inspector
│           ├── DatabaseLiveInspectorInspectorFactory.kt ← extends androidx.inspection.InspectorFactory
│           ├── capture/
│           │   ├── RoomCaptureProvider.kt             ← copied verbatim from inspector-agent
│           │   ├── RecordedBindArg.kt
│           │   └── BindArgRecorder.kt
│           ├── query/
│           │   ├── CursorResultSampler.kt             ← copied verbatim (frame-cap safety inside)
│           │   └── CellFormatter.kt
│           └── internal/
│               ├── BoundedEventQueue.kt
│               ├── KillSwitch.kt                      ← sysprop renamed: debug.dbliveinspector.enabled
│               └── Limits.kt                          ← length-cap constants
│
└── stubs/                                             ← kotlin("jvm") 2.2.20; produces a dexed inspector.jar
    ├── build.gradle.kts                               ← compileOnly :inspection jars from AS; dexJar task
    └── src/main/
        ├── kotlin/dev/ahmedvhashem/databaseliveinspector/stubs/
        │   └── DatabaseLiveInspectorInspectorFactory.kt   ← ~20-line stub; Class.forName → agent Factory
        └── resources/META-INF/services/
            └── androidx.inspection.InspectorFactory
               ← single line: dev.ahmedvhashem.databaseliveinspector.stubs.DatabaseLiveInspectorInspectorFactory
```

**Four modules total.** `:stubs` is the only new piece vs a pure 3-module plan; it replaces the
services-file-only trick with an explicit stub that is easier to verify and debug.

---

## 5. Data flow

```
┌──────────────────────────────────┐        ┌──────────────────────────────────────────────────┐
│ Android Studio (JVM)             │        │ Debuggable app process (ART)                     │
│                                  │  bytes │                                                  │
│ plugin/                          │ ◄────► │ agent/  (AAR — all on-device logic)             │
│ ├ TabProvider                    │ via    │ ├ DatabaseLiveInspector            (public API)  │
│ ├ InspectorPanel                 │ AppIn- │ ├ RoomCaptureProvider              (capture)     │
│ ├ MessengerSession ──────────────┤ specn  │ ├ CursorResultSampler              (capture)     │
│ │  uses AppInspectorMessenger    │ messen │ ├ BoundedEventQueue                (queue)       │
│ ├ TimelineModel                  │ ger    │ ├ DatabaseLiveInspectorInspector   ◄─ AS calls   │
│ └ protocol/  (Messages, Codec)   │        │ │   .onReceiveCommand / .sendEvent  this         │
│                                  │        │ └ DatabaseLiveInspectorInspectorFactory          │
│                                  │        │     ◄─ found via Class.forName from stub         │
│                                  │        │                                                  │
│ Bundled "inspector.jar"          │ pushed │ Injected by AS, loaded into child classloader.  │
│ (produced by :stubs:dexJar)      │ to     │ ServiceLoader finds the stub Factory in this    │
│ ┌──────────────────────────────┐ │ device │ JAR → stub calls Class.forName to reach the     │
│ │ StubInspectorFactory.class   │ │ ────►  │ real Factory in the agent (parent classloader). │
│ │ META-INF/services/…Factory   │ │        │                                                  │
│ └──────────────────────────────┘ │        │                                                  │
└──────────────────────────────────┘        └──────────────────────────────────────────────────┘
```

Runtime flow when AS opens the tab:
1. Plugin's `TabProvider` declares `AppInspectorJar("inspector.jar", "inspector/", "inspector/")`.
2. AS pushes that JAR onto the device and loads it into an inspector classloader (parent = app).
3. The framework calls `ServiceLoader.load(InspectorFactory::class.java)` against that classloader.
4. ServiceLoader reads `META-INF/services/androidx.inspection.InspectorFactory` from the JAR →
   finds the FQN `dev.ahmedvhashem.databaseliveinspector.stubs.DatabaseLiveInspectorInspectorFactory`.
5. The stub class is in the JAR itself — loads immediately.
6. `stubFactory.createInspector(connection, env)` calls `Class.forName(…AgentInspectorFactory…)`
   → walks up to the parent (app) classloader → finds the real Factory in the agent AAR → calls
   its `createInspector` → `DatabaseLiveInspectorInspector(connection)`.
7. The inspector wires itself as the agent's event sink and sends `app_info`. Capture flows.

If the app doesn't include the agent at all → step 6's `Class.forName` throws `ClassNotFoundException`
→ stub returns an `AgentMissingInspector` sentinel → plugin's `createTab` shows "Agent library not
present" placeholder. Graceful.

---

## 6. The wire (unchanged from plan.md §4.2)

7 message types. Keep JSON via kotlinx-serialization (`encodeDefaults = false`).

**Events (agent → plugin):** `app_info`, `query_started`, `query_finished` (with the additive
`resultColumns / resultRows / resultRowCount / resultTruncated` fields), `dropped_events`,
`agent_error`.

**Commands (plugin → agent):** `set_capture` → `capture_state`. Both are `{enabled: Boolean}`.
One field. No redaction, no sample rate.

---

## 7. Build wiring — `./gradlew buildAll`

### 7.1 Settings
```kotlin
// settings.gradle.kts
rootProject.name = "Android Database Live Inspector"
include(":plugin", ":protocol", ":agent", ":stubs")
```

### 7.2 Single user-facing task
```kotlin
// root build.gradle.kts
tasks.register("buildAll") {
    group = "build"
    description = "Builds the plugin zip (with bundled dex) and the agent AAR."
    dependsOn(":plugin:buildPlugin", ":agent:assembleRelease", ":stubs:dexJar")
}
```

### 7.3 The bundled JAR — produced by `:stubs:dexJar`
`:stubs` is a `kotlin("jvm")` module that compiles one ~20-line stub Factory class and DEXes it
into `inspector.jar`. The `:plugin` module's resource processing depends on it:

```kotlin
// plugin/build.gradle.kts
tasks.processResources {
    dependsOn(":stubs:dexJar")
    from(project(":stubs").tasks.named<Jar>("dexJar")) { into("inspector") }
}
```

The `:stubs:dexJar` task is the same d8-based approach from `plan.md §5.4` — just applied to
a 1-file module:

```kotlin
// stubs/build.gradle.kts
val dexJar by tasks.registering(JavaExec::class) {
    dependsOn("compileKotlin")
    val d8Jar = "/Users/hashem/Library/Android/sdk/build-tools/34.0.0/lib/d8.jar"
    classpath = files(d8Jar)
    mainClass.set("com.android.tools.r8.D8")
    val inputJar = tasks.named<Jar>("jar").get().archiveFile
    val output = layout.buildDirectory.file("stubs/inspector.jar")
    args = listOf("--output", output.get().asFile.absolutePath, inputJar.get().asFile.absolutePath)
    inputs.file(inputJar)
    outputs.file(output)
}
```

`compileOnly` dependencies needed by `:stubs` at compile time: `androidx.inspection` jar from the
local AS install (`Contents/plugins/android/resources/transport/…` or equivalent — resolve exactly
during Stage 3). No runtime dep on `:agent`.

At runtime, `AppInspectorJar("inspector.jar", "inspector/", "inspector/")` in the plugin's
classpath finds the dexed JAR and AS pushes it to the device.

---

## 8. The `:stubs` module — full definition

`:stubs` is a `kotlin("jvm") version "2.2.20"` module with a single source file and the
services registration file.

### 8.1 Source file (~25 LOC)
```kotlin
// stubs/src/main/kotlin/dev/ahmedvhashem/databaseliveinspector/stubs/
//   DatabaseLiveInspectorInspectorFactory.kt

package dev.ahmedvhashem.databaseliveinspector.stubs

import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorFactory

internal class DatabaseLiveInspectorInspectorFactory :
    InspectorFactory<Inspector>("dev.ahmedvhashem.databaseliveinspector") {

    override fun createInspector(connection: Connection, env: InspectorEnvironment): Inspector =
        try {
            Class.forName(
                "dev.ahmedvhashem.databaseliveinspector.agent.DatabaseLiveInspectorInspector"
            )
                .getConstructor(Connection::class.java)
                .newInstance(connection) as Inspector
        } catch (_: ClassNotFoundException) {
            AgentMissingInspector(connection)
        }
}
```

`AgentMissingInspector` is a minimal `Inspector` subclass (in the same file) that immediately
sends an `agent_error` event with `fatal = false` so the plugin's status label can show
"Agent library not present" without crashing.

### 8.2 Services file
```
stubs/src/main/resources/META-INF/services/androidx.inspection.InspectorFactory
```
Contents (one line):
```
dev.ahmedvhashem.databaseliveinspector.stubs.DatabaseLiveInspectorInspectorFactory
```

### 8.3 What `:agent` retains
The agent AAR keeps `DatabaseLiveInspectorInspector` and `DatabaseLiveInspectorInspectorFactory`
as before — real classes in the AAR, available on the app's classpath. `:stubs` has zero
compile-time dependency on `:agent`; the link is the string literal FQN in `Class.forName`.
Stabilize that FQN as a `@Keep`-annotated public class; treat it as an ABI surface.

---

## 9. Simplicity rules (carry across from plan.md §6)

- No abstract interface with one impl.
- No factory unless the framework requires one. (`InspectorFactory` is the one exception.)
- Functions over classes when stateless. `Limits`, `ProtocolCodec` are `object`s.
- Co-locate small types. `RecordedBindArg` hierarchy + rendering helper in one file. `Messages.kt`
  holds all wire types.
- No `*Manager` / `*Service` / `*Coordinator` names.
- Plain Swing + `invokeLater` in UI. The only coroutine boundary is inside `MessengerSession`.
- One source of truth for frame-safety caps (`CursorResultSampler`). Nothing re-derives them.

When in doubt, the most boring version wins.

---

## 10. Implementation stages (~4.5–5.5 working days)

### Stage 1 — `:protocol` module (~0.5 day)
- Copy `RoomDBInspector/src/main/kotlin/com/roomdbinspector/plugin/protocol/{Messages,ProtocolCodec}.kt`
  to `protocol/src/main/kotlin/dev/ahmedvhashem/databaseliveinspector/protocol/`.
- Trim `Messages.kt` to the 7 kept message types. `set_capture` becomes `{enabled: Boolean}`.
  Add `AppInfo` event.
- Port the golden tests for the surviving types.

### Stage 2 — `:agent` AAR (~2 days)
- Copy verbatim from `glovo-interview-android/inspector-agent`:
  - `capture/{RoomCaptureProvider, RecordedBindArg, BindArgRecorder}.kt`
  - `query/{CursorResultSampler, CellFormatter}.kt` — sampler loses its `RedactionLevel`
    parameter; renders raw text/numbers.
  - `internal/{BoundedEventQueue, KillSwitch}.kt` — rename sysprop to `debug.dbliveinspector.enabled`.
- Write fresh:
  - `DatabaseLiveInspector.kt` (~80 LOC) — public API + bounded queue + sink wiring.
  - `DatabaseLiveInspectorInspector.kt` (~80 LOC) — `Inspector(connection)` subclass; handles
    `set_capture`, forwards capture events. Emits `app_info` on bind. Annotate with `@Keep`.
  - `DatabaseLiveInspectorInspectorFactory.kt` (~10 LOC) — `InspectorFactory("dev.ahmedvhashem.databaseliveinspector")`,
    returns a `DatabaseLiveInspectorInspector(connection)`. Annotate with `@Keep`.
  - `internal/Limits.kt` (~10 LOC) — `MAX_SQL_CHARS = 8192`, `MAX_ARG_PREVIEW_CHARS = 256`.
- No services file in `:agent` — that lives in `:stubs`.

### Stage 3 — `:stubs` DEX (~0.5 day)
- Write `stubs/src/main/kotlin/.../stubs/DatabaseLiveInspectorInspectorFactory.kt` per §8.1.
- Add the services file per §8.2.
- Write `stubs/build.gradle.kts` per §8.3, including the `dexJar` task.
- Locate the `androidx.inspection` jar in the local AS install and wire the `compileOnly` dep.
- Verify: `./gradlew :stubs:dexJar` produces `build/stubs/inspector.jar` containing one `.dex`
  file + the `META-INF/services/` entry.

### Stage 4 — `:plugin` re-host (~1.5 days)
- Copy from `RoomDBInspector/src/main/kotlin/com/roomdbinspector/plugin/`:
  - `session/TimelineModel.kt` (trim snapshot fields + §5.3 window handling).
  - `ui/TableModels.kt` (trim Snapshot column).
  - `ui/InspectorPanel.kt` — major surgery: strip device combo, connect/disconnect, Query tab,
    DB Explorer tree, schema cache; replace bottom split with a `JBTabbedPane` containing
    `RequestTab` + `ResponseTab`.
- Write fresh:
  - `session/MessengerSession.kt` (~120 LOC) — collects `messenger.eventFlow` in one coroutine,
    decodes JSON into `ProtocolMessage`, hops to EDT, feeds `TimelineModel`. Single suspend
    `setCapture(enabled)`.
  - `ui/RequestTab.kt` (~60 LOC) — formatted property view of the selected row.
  - `ui/ResponseTab.kt` (~80 LOC) — `JBTable` over `resultColumns`/`resultRows` + summary header.
- `DatabaseLiveInspectorTabProvider`: switch `launchConfigs` from `emptyList()` to
  `listOf(AppInspectorLaunchConfig("dev.ahmedvhashem.databaseliveinspector", FrameworkInspectorLaunchParams(AppInspectorJar("inspector.jar", "inspector/", "inspector/"))))`.
  `createTab` unwraps the `Resolved` messenger and constructs the panel.
- Fix the vendor-name drift between `plugin.xml` (`Hashem`) and `build.gradle.kts`
  (stale `Database Live Inspector`).
- Wire `processResources` to depend on `:stubs:dexJar` per §7.3.

### Stage 5 — On-device validation (~0.5 day)
1. `./gradlew buildAll` produces `plugin.zip` + `agent.aar`.
2. Add `implementation(project(":agent"))` to the glovo test app
   (`/Users/hashem/Code/Hashem/glovo-interview-android/app`) via `mavenLocal` publish or a
   composite build. Add the one-line opt-in:
   ```kotlin
   DatabaseLiveInspector.install(context)
   DatabaseLiveInspector.attachTo(builder, "orders_database")
   ```
3. Install the plugin in Android Studio, run the app, open the App Inspection tool window, select
   the glovo process.
4. **Pass:** the "Database Live Inspector" tab appears with a `Resolved` messenger. Trigger a
   query → live `query_started`/`query_finished` with result rows appears in the timeline.
5. **Fail (stub Factory not found by ServiceLoader):** consult `idea.log` to confirm the FQN
   mismatch or services-file issue; fix the services file entry or the package and rebuild.

### Stage 6 — Verification (~0.5 day)
- Plugin unit tests for `TimelineModel` + the new tab renderers.
- Agent unit tests carried verbatim: `CursorResultSamplerTest`, `BoundedEventQueueTest`,
  `CellFormatterTest`.
- On-device E2E parity with the standalone's `tools/probe.py --selftest`:
  `SELECT * FROM order_items` → 9 rows × 5 cols, `resultRowCount=9`, no truncation.
- No-agent path: install the plugin against an app that does not depend on the agent → tab
  opens, status label says "Agent library not present", no crash.

---

## 11. Risks
- **`Class.forName` ABI contract.** The link between `:stubs` and `:agent` is a string literal
  FQN. If the agent class is renamed or the package changes, the stub silently fails at runtime
  and `AgentMissingInspector` fires. Mitigate: annotate with `@Keep`, and add an ABI test that
  compiles both modules and checks the class exists by the expected name.
- **Internal API drift in App Inspection.** `AppInspectorTabProvider` is `Non-Dynamic`,
  undocumented. Re-test on each AS release. Pin `sinceBuild = "253"` for now.
- **Build-time dependency direction.** The plugin's `processResources` depends on `:stubs:dexJar`.
  That is a *publication* dependency, not a code one — the plugin has zero code references to
  `:stubs` or `:agent`. Keep it that way.
- **`androidx.inspection` jar location in AS install.** Must be located precisely during Stage 3.
  The path changes between AS releases; pin to the specific AS version used.
- **No redaction on the wire.** Intentional. README must call this out so it isn't surprising.
- **Pause UX.** When the user pauses, the agent stops *enqueuing* new events but already-queued
  events still drain. Document.

---

## 12. Next session — start here
1. Re-confirm γ (default).
2. Fix the vendor drift between `plugin.xml` and `build.gradle.kts`.
3. **Stage 1** (`:protocol`) — small, unblocks everything else.
4. **Stage 2** (`:agent`) — all on-device logic; remember `@Keep` on the two public classes.
5. **Stage 3** (`:stubs`) — the ~25-line stub + services file + `dexJar` task.
6. **Stage 4** (`:plugin` re-host with new UI).
7. **Stage 5** — on-device validation against the glovo test app.
8. **Stage 6** — unit tests + E2E screenshot.

Rough total: **~4.5–5.5 working days**.
