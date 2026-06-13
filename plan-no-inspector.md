# Database Live Inspector — single-module plan (no separate inspector module)

> **This supersedes `plan.md`.** Same project, same goal, same scope decisions — but the
> architecture collapses to one runtime module. Read this first; `plan.md` remains as the
> longer-form reference with more comparative context (β vs γ, file-by-file ledger, etc.).
> Self-contained for cross-session handoff.

---

## 1. The shift in one paragraph

The previous plan had two on-device pieces: an `:agent` AAR shipped with the app, and an
`:inspector` DEX bundled with the plugin and injected by Android Studio. They communicated via a
reflection bridge inside the inspector dex. **Now collapsed to one module:** all real on-device
code (capture wrapper, cursor sampler, queue, kill switch, the `androidx.inspection.Inspector`
subclass, the `InspectorFactory` subclass) lives in `:agent`. The plugin still has to bundle a
JAR for App Inspection to push to the device — but that JAR contains **only a `META-INF/services`
file** pointing at the Factory class that lives in the agent. The reflection bridge disappears.

If the services-file-only approach turns out not to work (classloader-resolution depending on
framework internals), the fallback is a ~20-line stub Factory class compiled into the bundled
JAR — see §8.

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
- **Current namespace:** package / group / plugin id all `com.hashem.databaseliveinspector`,
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
├── settings.gradle.kts                              ← include :plugin, :protocol, :agent
├── build.gradle.kts                                  ← root: registers the `buildAll` task
├── gradle.properties, gradlew, gradle/wrapper/
├── plan.md                                            ← prior, longer-form plan (kept as reference)
├── plan-no-inspector.md                               ← this file (operative)
│
├── plugin/                                            ← IntelliJ plugin (Kotlin/JVM 2.2.20)
│   ├── build.gradle.kts                               ← intellij-platform 2.x; processResources bundles the dex jar
│   └── src/main/
│       ├── kotlin/com/hashem/databaseliveinspector/
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
│           └── inspector/inspector.jar                ← produced by :agent:inspectorBundleJar
│
├── protocol/                                          ← shared JVM module: wire types + codec
│   ├── build.gradle.kts                               ← kotlin("jvm") 2.2.20 + kotlinx-serialization
│   └── src/main/kotlin/com/hashem/databaseliveinspector/protocol/
│       ├── Messages.kt                                ← sealed ProtocolMessage; 7 subtypes
│       └── ProtocolCodec.kt                           ← encode/decode
│
└── agent/                                             ← Android library. Contains EVERYTHING on-device.
    ├── build.gradle.kts                               ← com.android.library; produces AAR + a tiny "inspectorBundleJar"
    └── src/main/
        ├── AndroidManifest.xml                        ← empty (library)
        ├── kotlin/com/hashem/databaseliveinspector/agent/
        │   ├── DatabaseLiveInspector.kt               ← public API: install / attachTo / setEnabled
        │   ├── DatabaseLiveInspectorInspector.kt      ← extends androidx.inspection.Inspector
        │   ├── DatabaseLiveInspectorInspectorFactory.kt ← extends androidx.inspection.InspectorFactory
        │   ├── capture/
        │   │   ├── RoomCaptureProvider.kt             ← copied verbatim from inspector-agent
        │   │   ├── RecordedBindArg.kt
        │   │   └── BindArgRecorder.kt
        │   ├── query/
        │   │   ├── CursorResultSampler.kt             ← copied verbatim (frame-cap safety inside)
        │   │   └── CellFormatter.kt
        │   └── internal/
        │       ├── BoundedEventQueue.kt
        │       ├── KillSwitch.kt                      ← sysprop renamed: debug.dbliveinspector.enabled
        │       └── Limits.kt                          ← length-cap constants
        └── resources/
            └── META-INF/services/
                └── androidx.inspection.InspectorFactory
                   ← single line: com.hashem.databaseliveinspector.agent.DatabaseLiveInspectorInspectorFactory
```

**Three modules total.** No `:inspector` module. No `AgentBridge`. No reflection in normal flow.

---

## 5. Data flow

```
┌──────────────────────────────────┐        ┌──────────────────────────────────────────────────┐
│ Android Studio (JVM)             │        │ Debuggable app process (ART)                     │
│                                  │  bytes │                                                  │
│ plugin/                          │ ◄────► │ agent/  (one AAR — contains EVERYTHING)          │
│ ├ TabProvider                    │ via    │ ├ DatabaseLiveInspector            (public API)  │
│ ├ InspectorPanel                 │ AppIn- │ ├ RoomCaptureProvider              (capture)     │
│ ├ MessengerSession ──────────────┤ specn  │ ├ CursorResultSampler              (capture)     │
│ │  uses AppInspectorMessenger    │ messen │ ├ BoundedEventQueue                (queue)       │
│ ├ TimelineModel                  │ ger    │ ├ DatabaseLiveInspectorInspector   ◄─ AS calls   │
│ └ protocol/  (Messages, Codec)   │        │ │   .onReceiveCommand / .sendEvent  this         │
│                                  │        │ └ …Factory  ◄─ ServiceLoader finds via the      │
│                                  │        │     services file in the injected JAR           │
│                                  │        │                                                  │
│ Bundled "inspector.jar"          │ pushed │ Injected by AS, loaded into a child classloader │
│ ┌──────────────────────────────┐ │ to     │ whose parent is the app classloader. The        │
│ │ META-INF/services/…Factory   │ │ device │ services file names a class that lives in the   │
│ │ (one line, no .class files)  │ │ ────►  │ agent (already on the app's classpath).         │
│ └──────────────────────────────┘ │        │                                                  │
└──────────────────────────────────┘        └──────────────────────────────────────────────────┘
```

Runtime flow when AS opens the tab:
1. Plugin's `TabProvider` declares `AppInspectorJar("inspector.jar", "inspector/", "inspector/")`.
2. AS pushes that JAR onto the device and loads it into an inspector classloader (parent = app).
3. The framework calls `ServiceLoader.load(InspectorFactory::class.java)` against that classloader.
4. ServiceLoader reads `META-INF/services/androidx.inspection.InspectorFactory` from the JAR →
   finds the FQN `com.hashem.databaseliveinspector.agent.DatabaseLiveInspectorInspectorFactory`.
5. Class load walks up to the parent (app) classloader → finds the factory in the agent AAR.
6. `factory.createInspector(connection, env)` → `DatabaseLiveInspectorInspector(connection)`.
7. The inspector wires itself as the agent's event sink and sends `app_info`. Capture flows.

If the app doesn't include the agent at all → step 5 throws `ClassNotFoundException` → AS reports
`AppInspectorMessengerTarget.Unresolved("…")` → our `createTab` shows a "Agent library not present"
placeholder. Graceful.

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
include(":plugin", ":protocol", ":agent")
```

### 7.2 Single user-facing task
```kotlin
// root build.gradle.kts
tasks.register("buildAll") {
    group = "build"
    description = "Builds the plugin zip (with bundled dex) and the agent AAR."
    dependsOn(":plugin:buildPlugin", ":agent:assembleRelease")
}
```

### 7.3 The bundled JAR — produced by `:agent:inspectorBundleJar`
A tiny Gradle task inside the `:agent` module writes a single-entry JAR containing only
`META-INF/services/androidx.inspection.InspectorFactory`. No class files. Conceptually:

```kotlin
val inspectorBundleJar by tasks.registering(Jar::class) {
    archiveFileName.set("inspector.jar")
    destinationDirectory.set(layout.buildDirectory.dir("inspector-bundle"))
    from(layout.projectDirectory.dir("src/main/resources")) {
        include("META-INF/services/**")
    }
}
```

The `:plugin` module's resource processing depends on it and copies the file into
`src/main/resources/inspector/inspector.jar`:

```kotlin
// plugin/build.gradle.kts
tasks.processResources {
    dependsOn(":agent:inspectorBundleJar")
    from(project(":agent").tasks.named<Jar>("inspectorBundleJar")) { into("inspector") }
}
```

At runtime, `AppInspectorJar("inspector.jar", "inspector/", "inspector/")` finds it in the
plugin's classpath and AS pushes it to the device.

**Why no d8 / no DEX conversion?** Because the JAR has no .class files, there's nothing to dex.
ART/ServiceLoader walks the JAR's `META-INF` directly. The class load happens against the
parent classloader (the app), and the agent's classes were already dexed when the app was built.

---

## 8. The experiment + fallback (must be tested early)

The whole simplification rests on this assumption: **AS's inspector-classloader can resolve
classes from the app's classpath (the agent AAR's classes) when ServiceLoader walks
META-INF/services**.

Reasons this should work:
- App Inspection inspectors routinely *inspect* the app's classes — that requires a classloader
  with parent access to the app, which means class resolution flows up.
- This is the standard Android classloader-delegation behavior.

Reasons this might not work:
- AS might use an isolated classloader for the inspector (no parent delegation) to enforce a
  clean boundary.
- ServiceLoader behavior on `PathClassLoader`/`DexClassLoader` over a JAR with no `classes.dex`
  is something I haven't seen documented; an implementation might short-circuit and not even
  try to read META-INF.

**Verify in Stage 4 (first thing).** If it works, proceed. If it doesn't, the fallback is small:

### Fallback: ~20-line stub Factory inside the bundled JAR
Add a single Kotlin file to a new tiny `:inspector-stub` source root inside `:agent`, compiled
and dexed into the bundled JAR. The stub Factory just delegates:

```kotlin
class DatabaseLiveInspectorInspectorFactory :
    InspectorFactory<Inspector>("com.hashem.databaseliveinspector") {
    override fun createInspector(connection: Connection, env: InspectorEnvironment): Inspector =
        try {
            Class.forName("com.hashem.databaseliveinspector.agent.DatabaseLiveInspectorInspector")
                .getConstructor(Connection::class.java)
                .newInstance(connection) as Inspector
        } catch (e: ClassNotFoundException) {
            AgentMissingInspector(connection)
        }
}
```

The bundle JAR then contains the stub class + the services file. The agent AAR loses the
Factory class (since the stub one in the bundle is the registered one). This is one extra file
of complexity; revert to this only if the no-class-file approach fails.

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

## 10. Implementation stages (~4–5 working days)

Smaller than plan.md because `:inspector` and the reflection bridge are gone.

### Stage 1 — `:protocol` module (~0.5 day)
- Copy `RoomDBInspector/src/main/kotlin/com/roomdbinspector/plugin/protocol/{Messages,ProtocolCodec}.kt`
  to `protocol/src/main/kotlin/com/hashem/databaseliveinspector/protocol/`.
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
    `set_capture`, forwards capture events. Emits `app_info` on bind.
  - `DatabaseLiveInspectorInspectorFactory.kt` (~10 LOC) — `InspectorFactory("com.hashem.databaseliveinspector")`,
    returns a `DatabaseLiveInspectorInspector(connection)`.
  - `internal/Limits.kt` (~10 LOC) — `MAX_SQL_CHARS = 8192`, `MAX_ARG_PREVIEW_CHARS = 256`.
  - `src/main/resources/META-INF/services/androidx.inspection.InspectorFactory` — one line:
    `com.hashem.databaseliveinspector.agent.DatabaseLiveInspectorInspectorFactory`.
- Add the `inspectorBundleJar` Gradle task per §7.3.

### Stage 3 — `:plugin` re-host (~1.5 days)
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
  `listOf(AppInspectorLaunchConfig("com.hashem.databaseliveinspector", FrameworkInspectorLaunchParams(AppInspectorJar("inspector.jar", "inspector/", "inspector/"))))`.
  `createTab` unwraps the `Resolved` messenger and constructs the panel.
- Fix the vendor-name drift between `plugin.xml` (`Hashem`) and `build.gradle.kts`
  (stale `Database Live Inspector`).

### Stage 4 — Validate the services-file-only approach on device (~0.5 day)
**First thing before declaring this stage done:**
1. `./gradlew buildAll` produces `plugin.zip` + `agent.aar` + the bundle JAR inside the zip.
2. Add `implementation(project(":agent"))` to the glovo test app
   (`/Users/hashem/Code/Hashem/glovo-interview-android/app`) — temporarily, via a `mavenLocal`
   publish or a composite build. Add the one-line opt-in:
   ```kotlin
   DatabaseLiveInspector.install(context)
   DatabaseLiveInspector.attachTo(builder, "orders_database")
   ```
3. Install the plugin in Android Studio, run the app, open the App Inspection tool window, select
   the glovo process.
4. **Pass:** the "Database Live Inspector" tab appears and the framework calls `createTab` with a
   `Resolved` messenger. Trigger a query in the app → live `query_started`/`query_finished` with
   result rows appears in the timeline.
5. **Fail (ServiceLoader couldn't find our Factory):** symptoms = `Unresolved("…")` target in
   `createTab`, or `idea.log` shows ServiceLoader returned no factories for the inspector ID. Apply
   the §8 fallback (add the stub Factory class to the bundle JAR), rebuild, retry. Estimated
   additional time: ~0.5 day to add the stub + rebuild + verify.

### Stage 5 — Verification (~0.5 day)
- Plugin unit tests for `TimelineModel` + the new tab renderers (no headless Swing pitfalls —
  just data-class assertions on what the panel would render).
- Agent unit tests carried verbatim: `CursorResultSamplerTest`, `BoundedEventQueueTest`,
  `CellFormatterTest`.
- On-device E2E parity with the standalone's `tools/probe.py --selftest`:
  `SELECT * FROM order_items` → 9 rows × 5 cols, `resultRowCount=9`, no truncation.
- No-agent path: install the plugin against an app that does not depend on the agent → tab
  opens, status label says "Agent library not present", no crash.

---

## 11. Risks
- **The classloader/ServiceLoader assumption.** The whole §7.3 design hinges on it. Stage 4
  verifies it before any further work is built on top. Fallback in §8 is small and known.
- **Internal API drift in App Inspection.** `AppInspectorTabProvider` is `Non-Dynamic`,
  undocumented. Re-test on each AS release. Pin `sinceBuild = "253"` for now.
- **Build-time circular dependency hazard.** The plugin's `processResources` depends on the
  agent's `inspectorBundleJar`. That's a *publication* dependency, not a code one — the
  plugin module has zero code references to `:agent`. Keep it that way; if the plugin ever needs
  a class from the agent at compile time, that's a sign the architecture has drifted.
- **No redaction on the wire.** Intentional. README must call this out so it isn't surprising.
- **Pause UX.** When the user pauses, the agent stops *enqueuing* new events but already-queued
  events still drain. Document.

---

## 12. Next session — start here
1. Re-confirm γ (default).
2. Fix the vendor drift between `plugin.xml` and `build.gradle.kts`.
3. **Stage 1** (`:protocol`) — small, unblocks everything else.
4. **Stage 2** (`:agent` — all on-device code in one module).
5. **Stage 3** (`:plugin` re-host with new UI).
6. **Stage 4** — run the services-file-only experiment on device. If it works, ship. If not,
   apply the §8 fallback (~20 lines added).
7. **Stage 5** — verify against the glovo test app; archive a screenshot of live capture.

Rough total: **~4–5 working days** if the experiment passes; **+0.5 day** if the fallback is needed.
