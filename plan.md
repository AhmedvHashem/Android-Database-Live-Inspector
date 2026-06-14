# Database Live Inspector — implementation plan

> Self-contained. Designed to survive session compaction: a continuation should be possible
> from this file alone, plus the read-only sources it points at.

---

## 1. What this project is, in one paragraph

A four-module Gradle monorepo that ships an Android-Studio plugin contributing a tab to AS's
**App Inspection** window. The tab shows a live, Network-Inspector-style timeline of every SQL
query the running debuggable app executes through Room — with a per-row Request / Response split.
The app opts in by depending on a tiny `agent` AAR published from this same repo and adding one
line at Room-builder time. The IDE half (`plugin` module) bundles a small `inspector.jar` from
the `:stubs` module; AS pushes that DEX to the device, where a stub `InspectorFactory` delegates
via `Class.forName` to the real `Inspector` and `InspectorFactory` implementations that live in
the `:agent` AAR. Events stream back over App Inspection's transport.

---

## 2. Background, prior work, and verified facts (handoff context)

### 2.1 What already exists
Three repos, two are the **source of truth to copy from** (do not edit) and the third is the
target of this plan:

| | Path | Role |
|---|---|---|
| **Standalone plugin (reference)** | `/Users/hashem/Code/Hashem/RoomDBInspector` | Working IntelliJ plugin that talks to its own JSON-over-`localabstract` transport. UI, timeline, protocol types live here. |
| **Standalone agent (reference)** | `/Users/hashem/Code/Hashem/glovo-interview-android/inspector-agent` | Working app-side agent with delegating `SupportSQLiteOpenHelper.Factory` capture + cursor sampler. |
| **This project (target)** | `/Users/hashem/Code/Hashem/DatabaseLiveInspector` | Today: only the IntelliJ plugin entry with an empty App-Inspection tab. To be expanded per this plan. |

### 2.2 What's already verified
- **PoC tab works.** A third-party `AppInspectorTabProvider` *does* register on AS 253
  (`AI-253.32098.37.2534.15232325`, the 2025.2 line). The empty-tab proved it visually inside
  the App Inspection window. The plugin under this repo is that PoC.
- **Local AS install path:** `/Users/hashem/Applications/Android Studio.app`. The plugin compiles
  against it via `intellijPlatform { local("…/Android Studio.app/Contents") + bundledPlugin("org.jetbrains.android") }`.
- **Kotlin version pin (this is a gotcha):** AS 253 ships kotlinc **2.2.20** (`Contents/plugins/Kotlin/kotlinc/build.txt`).
  Older Kotlin compilers fail to read its metadata (`binary version 2.3.0, expected 2.1.0`). The
  plugin module is pinned to `kotlin("jvm") version "2.2.20"`. Same constraint will apply to any
  new JVM module under this repo (the Android modules use AGP-bundled Kotlin).
- **App Inspection API shape** (extracted via `javap` against AS's local `android.jar` and
  confirmed reachable from third-party plugins; full signatures are reproduced below):
  - EP id: `com.android.tools.idea.appinspection.inspector.ide.appInspectorTabProvider`
  - `AppInspectorTabProvider.createTab(project, ideServices, processDescriptor, messengerTargets, parentDisposable)`
  - `AppInspectorMessenger` exposes `sendRawCommand(byte[]): byte[]` (suspend) and
    `eventFlow: Flow<ByteArray>` — payload is opaque to the framework.
  - `AppInspectorMessengerTarget = Resolved(messenger) | Unresolved(error: String)` — so an
    inspector failure surfaces as `Unresolved`, not a crashed tab.
  - `AppInspectorJar(name, developmentDirectory, releaseDirectory)` packages an on-device dex.
  - `FrameworkInspectorLaunchParams(jar: AppInspectorJar) : AppInspectorLaunchParams`.
- **Result-row capture works end-to-end** (a feature added on the standalone in the recent
  session): `query_finished` carries `resultColumns / resultRows / resultRowCount /
  resultTruncated`, populated by `CursorResultSampler` at cursor-close time. **Frame-cap safety
  was hardened** after a high-severity finding — the sampler caps columns
  (`MAX_COLUMNS = 64`), counts every cell incl. null/blob toward a 32 KiB total budget, and the
  transport's writer loop drops any single oversize *event* (still fatal for *responses*) and
  reports it via `dropped_events`. The reused `CursorResultSampler.kt` and `BoundedEventQueue.kt`
  already carry these fixes. **Do not re-derive these caps anywhere else** — there must be one
  source of truth.

### 2.3 What was decided to drop
Four explicit scope decisions:

1. **UI: Network-Inspector style** — top pane is the query list; bottom pane is a `JBTabbedPane`
   with per-row **Request** and **Response** tabs.
2. **Live capture only.** No query console, no schema browser, no DB explorer. Studio's own
   Database Inspector already does all of that — we won't duplicate it.
3. **Drop tracing.** `TraceScope` / `recordSnapshot` / `result_snapshot` / §5.3 window heuristic /
   Snapshot column — all deleted. The app never actually used the tracing API.
4. **Drop redaction.** No `off`/`balanced`/`strict` levels; bind args and captured cells are raw.
   Length caps survive separately as `Limits.kt` (8192 SQL, 256 arg preview, 256 per cell + the
   total-byte budget in `CursorResultSampler`) — those are frame-safety, not redaction.

### 2.4 Open decisions
**On-device strategy — β vs γ.** Without α (which only existed to serve query/schema/browse, all
out of scope), the choice is:

| | Effort | App opt-in? |
|---|---|---|
| **β. Native SQLite hooks** | multi-week R&D, reinventing `androidx.sqliteinspection` on an unsupported surface | none |
| **γ. Agent AAR + one-line opt-in** | ~5–6 days, reuses existing delegating-factory capture | one line at Room-builder time |

**Recommended: γ.** This plan from §3 onward assumes γ. If β is later chosen, replace §5 stages
2+3 with native-hook work; §1, §4 (UI), and §5 stages 1, 4, 5 stay.

### 2.5 Reference sources to consult during implementation
- **JetBrains AS source mirror** (the App Inspection internal APIs, plus example inspectors):
  `https://github.com/JetBrains/android/tree/idea/2026.1/app-inspection` — top-level dirs `api`,
  `ide`, `inspector`, `inspectors`, `integration`. The `inspectors/` subtree has working
  inspector implementations whose patterns we can crib.
- **androidx.inspection README** (warns that the JAR is "provided by Android Studio in runtime,
  unlike regular libraries"): `https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/inspection/inspection/README.md`
- **Dev device:** Pixel 6. USB serial `1B281FDF6001DG`. Over wireless the serial gains spaces
  (e.g. `adb-1B281FDF6001DG-Ow4JVm (2)._adb-tls-connect._tcp` after Bonjour conflict renames) —
  not relevant here because App Inspection handles adb, but worth knowing for the test app side.

### 2.6 Current state of this repo (committed)
```
DatabaseLiveInspector/
├── build.gradle.kts                                ← single-module IntelliJ plugin
├── settings.gradle.kts                             ← rootProject.name = "Android Database Live Inspector"
├── gradle.properties, gradlew, gradle/wrapper/
├── plan.md                                          ← this file
└── src/
    ├── main/kotlin/dev/ahmedvhashem/databaseliveinspector/
    │   └── DatabaseLiveInspectorTabProvider.kt     ← empty-tab PoC, uses StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR
    └── main/resources/META-INF/plugin.xml          ← <id>dev.ahmedvhashem.databaseliveinspector</id>
```
**Known minor drift to fix on first edit pass:** `build.gradle.kts` says
`vendor { name = "Database Live Inspector" }` while `plugin.xml` says `<vendor>Hashem</vendor>`.
Either is fine — pick one and align both. The plugin.xml `<vendor>` is authoritative.

---

## 3. Target project structure (after implementation)

```
DatabaseLiveInspector/
├── settings.gradle.kts                        ← lists subprojects: :plugin, :protocol, :agent, :stubs
├── build.gradle.kts                            ← root: registers the `buildAll` task; no plugin config
├── gradle.properties, gradlew, gradle/wrapper/
├── plan.md                                     ← this file
│
├── plugin/                                     ← IntelliJ plugin (Kotlin/JVM, kotlin 2.2.20)
│   ├── build.gradle.kts                        ← intellij-platform 2.x; processResources depends on :stubs:dexJar
│   └── src/main/
│       ├── kotlin/dev/ahmedvhashem/databaseliveinspector/
│       │   ├── DatabaseLiveInspectorTabProvider.kt      ← registers the tab; reuses StudioIcons icon
│       │   ├── ui/
│       │   │   ├── InspectorPanel.kt                    ← root Swing component for the tab
│       │   │   ├── QueryTableModel.kt                   ← top-list TableModel
│       │   │   ├── RequestTab.kt                        ← bottom tab #1
│       │   │   └── ResponseTab.kt                       ← bottom tab #2
│       │   └── session/
│       │       ├── TimelineModel.kt                     ← bounded log of rows; pairs by queryId
│       │       └── MessengerSession.kt                  ← App Inspection messenger adapter
│       └── resources/
│           ├── META-INF/plugin.xml
│           └── inspector/inspector.jar                  ← DEX bundle written by Gradle from :stubs
│
├── protocol/                                   ← shared JVM module: JSON wire types + codec
│   ├── build.gradle.kts                        ← plain kotlin("jvm") 2.2.20 + kotlinx-serialization
│   └── src/main/kotlin/dev/ahmedvhashem/databaseliveinspector/protocol/
│       ├── Messages.kt                                  ← sealed ProtocolMessage + ~7 subtypes
│       └── ProtocolCodec.kt                             ← single `encode(msg): ByteArray` + `decode(bytes): ProtocolMessage?`
│
├── agent/                                      ← Android library (AAR) — app opt-in; all on-device logic
│   ├── build.gradle.kts                        ← com.android.library; minSdk 26; depends on :protocol
│   └── src/main/
│       ├── AndroidManifest.xml                          ← empty (library has no entry point)
│       └── kotlin/dev/ahmedvhashem/databaseliveinspector/agent/
│           ├── DatabaseLiveInspector.kt                 ← public API: install / attachTo / setEnabled / attachInspectorSink
│           ├── DatabaseLiveInspectorInspector.kt        ← @Keep; extends androidx.inspection.Inspector
│           ├── DatabaseLiveInspectorInspectorFactory.kt ← @Keep; extends androidx.inspection.InspectorFactory
│           ├── capture/
│           │   ├── RoomCaptureProvider.kt               ← delegating SupportSQLiteOpenHelper.Factory (copied verbatim)
│           │   ├── BindArgRecorder.kt                   ← SupportSQLiteProgram stub (copied)
│           │   └── RecordedBindArg.kt                   ← sealed record type (copied)
│           ├── query/
│           │   ├── CursorResultSampler.kt               ← copied verbatim (carries the frame-cap fix)
│           │   └── CellFormatter.kt                     ← copied verbatim
│           └── internal/
│               ├── BoundedEventQueue.kt                 ← copied verbatim (carries `recordDropped()`)
│               ├── KillSwitch.kt                        ← copied verbatim (`debug.dbliveinspector.enabled`)
│               └── Limits.kt                            ← new tiny constants holder
│
└── stubs/                                      ← kotlin("jvm") 2.2.20; produces a dexed inspector.jar
    ├── build.gradle.kts                        ← compileOnly inspection jars from AS install; dexJar task
    └── src/main/
        ├── kotlin/dev/ahmedvhashem/databaseliveinspector/stubs/
        │   └── DatabaseLiveInspectorInspectorFactory.kt ← ~25-line stub; delegates to :agent via Class.forName
        └── resources/META-INF/services/
            └── androidx.inspection.InspectorFactory     ← single line: stubs.DatabaseLiveInspectorInspectorFactory
```

Total: 4 modules. ~16 Kotlin files. No interfaces with single implementations. No abstract
factories beyond what `androidx.inspection` requires.

---

## 4. Code architecture (data + control flow)

### 4.1 Three processes, three classloaders, one JSON contract
```
┌────────────────────────────────┐         ┌───────────────────────────────────────────┐
│  Android Studio (JVM, JBR 21)  │         │  Debuggable app process (ART, on device)  │
│                                │         │                                           │
│  plugin/                       │  bytes  │  stubs/   (DEX, injected by AS)           │
│  ├── DatabaseLiveInspector…    │ ◄─────► │  └── StubInspectorFactory                 │
│  │   TabProvider               │  via    │        Class.forName ─────────┐            │
│  ├── InspectorPanel (Swing)    │  App    │                               ▼            │
│  ├── TimelineModel             │  Inspn  │  agent/   (AAR, packaged in user app)     │
│  └── MessengerSession ─────────┤  messen │  ├── DatabaseLiveInspector (public API)   │
│      uses AppInspectorMessenger│  ger    │  ├── DatabaseLiveInspectorInspector  @Keep │
│         from android.jar       │         │  ├── DatabaseLiveInspectorInspectorFactory │
│  protocol/  (Messages, Codec)  │         │  ├── RoomCaptureProvider                  │
│         used by plugin AND     │         │  │   wraps user's open-helper factory     │
│         ↓ embedded in agent dex│         │  ├── CursorResultSampler                  │
└────────────────────────────────┘         │  └── BoundedEventQueue                    │
                                           └───────────────────────────────────────────┘
```

### 4.2 The JSON wire — minimum surface (v1)
**Events (inspector → plugin):**
- `app_info` — emitted once when the inspector binds: `{appId, pid}`. Replaces `hello_ack`.
- `query_started` — `{queryId, dbName, sql, args[], threadName, tsMs}`.
- `query_finished` — `{queryId, status, durationMs, errorMessage, tsMs}` + the additive
  `resultColumns / resultRows / resultRowCount / resultTruncated` (carried from the recent
  feature; default values omitted via `encodeDefaults = false`).
- `dropped_events` — `{count, tsMs}`.
- `agent_error` — `{message, fatal, tsMs}`.

**Commands (plugin → inspector):**
- `set_capture` → `capture_state` — both are `{enabled: Boolean}`. One field. No `redaction`, no
  `sampleRate`.

That's it. Seven message types, one command pair. The codec is one ~50-line Kotlin file.

### 4.3 The inspector — what each method does
`androidx.inspection.Inspector` has exactly two override points. The implementation lives in
`:agent` (not in the injected `:stubs` JAR). `AgentBridge` is gone; the Inspector calls
`DatabaseLiveInspector` directly since it's in the same classloader as the app.

```kotlin
// agent/.../DatabaseLiveInspectorInspector.kt  (@Keep annotated)
class DatabaseLiveInspectorInspector(
    connection: Connection,
) : Inspector(connection) {

    init {
        DatabaseLiveInspector.attachInspectorSink { event ->
            connection.sendEvent(event)   // raw bytes already encoded by the agent
        }
        connection.sendEvent(ProtocolCodec.encode(AppInfo(Process.myPid(), packageName)))
    }

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        when (val msg = ProtocolCodec.decode(data)) {
            is SetCapture -> {
                DatabaseLiveInspector.setEnabled(msg.enabled)
                callback.reply(ProtocolCodec.encode(CaptureState(enabled = msg.enabled)))
            }
            else -> callback.reply(ProtocolCodec.encode(
                AgentError("Unsupported command: ${msg?.javaClass?.simpleName}", false, now())))
        }
    }

    override fun onDispose() { DatabaseLiveInspector.detach() }
}
```

The stub in `:stubs` is ~25 lines — just a `Class.forName` trampoline that finds this class and
calls its constructor. If the agent AAR is absent, the stub returns an `AgentMissingInspector`
sentinel that immediately sends `agent_error` and does nothing else.

### 4.4 The agent — what the app sees
Same public shape as today's reference agent. Apps add one dep and one line:

```kotlin
// In app/build.gradle.kts:
implementation("dev.ahmedvhashem.databaseliveinspector:agent:1.0.0")

// In AppDatabase factory:
val builder = Room.databaseBuilder(context, AppDatabase::class.java, "name")
DatabaseLiveInspector.install(context)               // idempotent, no-op on release builds
DatabaseLiveInspector.attachTo(builder, "name")      // wraps openHelperFactory
val db = builder.build()
```

Internally:
- `install(context)` checks `FLAG_DEBUGGABLE` and `debug.dbliveinspector.enabled`; sets up the
  bounded event queue.
- `attachTo(builder, name)` calls `builder.openHelperFactory(InspectorOpenHelperFactory(delegate))`
  — the existing `RoomCaptureProvider` from `inspector-agent/.../capture/RoomCaptureProvider.kt`,
  copied verbatim.
- `attachInspectorSink(consumer)` (called reflectively by the inspector dex) stashes the consumer;
  every queued event is delivered to it.
- `setEnabled(enabled)` toggles capture without dropping queued events.

### 4.5 The plugin UI — what the user sees
- Toolbar: **Pause/Resume** (sends `set_capture`), **Clear** (clears `TimelineModel` locally only),
  status label bound to the messenger lifecycle.
- Top JBTable backed by `QueryTableModel` (rows from `TimelineModel.snapshotRows()`).
  Columns: `Time | DB | SQL | Thread | Duration (ms) | Status`. Selection drives the bottom tabs.
- Bottom `JBTabbedPane`:
  - **Request tab** (`RequestTab.kt`) — formatted property view of the selected row's
    `sql / args / dbName / threadName / startTsMs / durationMs / status / errorMessage`.
  - **Response tab** (`ResponseTab.kt`) — `JBTable` rendering `resultColumns + resultRows`. Header
    label: `"N rows captured"` or `"N rows captured (preview truncated)"`; for errors or
    non-row-returning statements, a single-line message instead of the grid.
- `TimelineModel` is bounded (drop-oldest, default cap 10k rows), pairs `query_started` and
  `query_finished` by exact `queryId`. **Re-renders only when the selected row's content
  actually changes** (`lastRenderedRow != selectedRow`) — this is the fix from the recent code
  review; carry it across so live events don't clobber the user's text selection.

### 4.6 Threading rules (carry these from the standalone)
- **EDT-only** for Swing: every model mutation that drives the UI hops through `ApplicationManager.getApplication().invokeLater { … }`.
- **No coroutines in UI code.** The `Flow<ByteArray>` from `AppInspectorMessenger.eventFlow` is
  the *only* coroutine boundary. It lives inside `MessengerSession`; everything outside it is
  plain blocking calls or `invokeLater`.
- **Capture path never blocks the app's query thread.** Cursor sampling happens at cursor-close
  time; the captured event goes onto the bounded queue (drop-oldest); the inspector dex pulls
  from the queue on its own thread. Same model as today.
- **Duration is stamped *before* sampling**, not after — current `AgentCore` does this; preserve it.

---

## 5. Build wiring — single command

### 5.1 `settings.gradle.kts`
```kotlin
rootProject.name = "Android Database Live Inspector"
include(":plugin", ":protocol", ":agent", ":stubs")
```

### 5.2 Root `build.gradle.kts`
```kotlin
// Single user-facing command.
tasks.register("buildAll") {
    group = "build"
    description = "Builds the plugin zip (with the stubs dex bundled) and the agent AAR."
    dependsOn(":plugin:buildPlugin", ":agent:assembleRelease", ":stubs:dexJar")
}
```
Invocation: `./gradlew buildAll`. That's the one command.

### 5.3 How the dex gets into the plugin zip
In `plugin/build.gradle.kts`:
```kotlin
tasks.processResources {
    dependsOn(":stubs:dexJar")
    from(project(":stubs").tasks.named<Jar>("dexJar")) { into("inspector") }
}
```
At runtime the plugin's `AppInspectorJar("inspector.jar", developmentDirectory = "inspector/",
releaseDirectory = "inspector/")` finds it on the classpath.

### 5.4 The `:stubs:dexJar` task
`:stubs` is a `kotlin("jvm")` module — simpler than an Android library because it has only one
file and needs no AGP. Its standard `jar` task output is passed directly to d8:
```kotlin
// stubs/build.gradle.kts
plugins { kotlin("jvm") version "2.2.20" }

dependencies {
    // Lift from local AS install — resolve exact path during Stage 3.
    // Candidate: Contents/plugins/android/resources/transport/agent-command-lib.jar or similar.
    compileOnly(files("<path-to-inspection-jar-from-AS-install>"))
}

val dexJar by tasks.registering(JavaExec::class) {
    dependsOn("jar")
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

### 5.5 Module Kotlin versions
- `:plugin`, `:protocol`, `:stubs` → `kotlin("jvm") version "2.2.20"` (must match the IDE's
  bundled kotlinc; see §2.2).
- `:agent` → Kotlin version comes from AGP-bundled Kotlin. Fine; the agent compiles against
  Android APIs and its output is consumed via AAR, not Kotlin metadata.

---

## 6. Simplicity rules (apply throughout)

The user explicitly asked for the simplest possible code. Enforce by default:
- **No abstract interface that has exactly one impl.** Use the concrete class.
- **No factory unless the framework requires one.** `androidx.inspection.InspectorFactory` is
  required; nothing else is.
- **Functions over classes when there's no state.** `Limits` and `ProtocolCodec` are `object`s.
- **Co-locate small types.** `RecordedBindArg` sealed hierarchy + its rendering helper live in
  one file. `Messages.kt` holds all wire types in one file.
- **No `*Manager`, no `*Service`, no `*Coordinator` names.** Name things by what they do.
- **No reactive primitives in UI code.** Plain Swing + `invokeLater`. The single `Flow` boundary
  is in `MessengerSession`.
- **No new event types "for future use."** Each message type must justify itself today.
- **One source of truth for frame-safety caps.** `CursorResultSampler` enforces; nothing else
  re-derives.

When in doubt during implementation, prefer the most boring version of the change.

---

## 7. Implementation stages (γ, ~4.5–5.5 working days, parallelizable)

Each stage starts with a one-line goal and lists *exactly* what to copy from the standalone
references and what to write fresh.

### Stage 1 — `:protocol` module (~0.5 day)
**Goal:** the JSON wire types exist and round-trip.
- Copy `RoomDBInspector/src/main/kotlin/com/roomdbinspector/plugin/protocol/{Messages,ProtocolCodec}.kt`
  → `protocol/src/main/kotlin/dev/ahmedvhashem/databaseliveinspector/protocol/`. Repackage.
- **Trim** `Messages.kt`: keep `QueryStarted`, `QueryFinished` (with result fields),
  `DroppedEvents`, `AgentError`, `SetCapture`, `CaptureState`. Add `AppInfo`. Delete everything
  else (Hello/HelloAck, ListDatabases/Databases, GetSchema/SchemaResult, RunQuery/QueryResult,
  CancelQuery/CancelAck, Ping/Pong, ResultSnapshot, Invalidation*, ErrorMessage). The `set_capture`
  shape becomes `{enabled: Boolean}`.
- Port the golden tests for the surviving types; pin the wire bytes.

### Stage 2 — `:agent` AAR (~2 days)
**Goal:** apps can integrate with one dep + one line; capture works; the full Inspector lives here.
- Copy from `glovo-interview-android/inspector-agent/src/main/java/com/roomdbinspector/agent/`:
  - `capture/RoomCaptureProvider.kt`, `capture/QueryEventSink.kt` (rename to local types).
  - `query/CursorResultSampler.kt`, `query/CellFormatter.kt` — **verbatim** (the frame-cap fix
    must be preserved exactly).
  - `internal/BoundedEventQueue.kt`, `internal/KillSwitch.kt` — verbatim. Rename the sysprop:
    `debug.roomdbinspector.enabled` → `debug.dbliveinspector.enabled`.
- Write fresh:
  - `agent/DatabaseLiveInspector.kt` (~80 LOC) — public API + in-process queue glue. Drop all
    of `AgentCore`'s run_query/get_schema/list_databases/registry plumbing.
  - `agent/DatabaseLiveInspectorInspector.kt` (~80 LOC) — see §4.3. Annotate with `@Keep`.
  - `agent/DatabaseLiveInspectorInspectorFactory.kt` (~10 LOC) — `InspectorFactory(…)`, returns
    a new `DatabaseLiveInspectorInspector(connection)`. Annotate with `@Keep`.
  - `internal/Limits.kt` — `const val MAX_SQL_CHARS = 8192; const val MAX_ARG_PREVIEW_CHARS = 256`.
- Depends on `:protocol`.

### Stage 3 — `:stubs` DEX (~0.5 day)
**Goal:** the bundled JAR exists, the stub Factory delegates to `:agent` via reflection.
- One source file `stubs/.../DatabaseLiveInspectorInspectorFactory.kt` (~25 LOC):
  - `InspectorFactory` subclass; `createInspector` does `Class.forName(…agent…Inspector…)`,
    constructs it, returns it. On `ClassNotFoundException` returns `AgentMissingInspector`.
  - `AgentMissingInspector` is a tiny `Inspector` subclass in the same file that sends one
    `agent_error` event and does nothing else.
- Services file `META-INF/services/androidx.inspection.InspectorFactory` → points to the stub FQN.
- `stubs/build.gradle.kts`: `kotlin("jvm") version "2.2.20"` + `compileOnly` inspection jar from
  the local AS install + `dexJar` task per §5.4.
- Verify: `./gradlew :stubs:dexJar` produces `build/stubs/inspector.jar` with `.dex` + services entry.

### Stage 4 — `:plugin` re-host (~1.5 days)
**Goal:** the existing PoC tab becomes a real working UI bound to the messenger.
- Copy from `RoomDBInspector/src/main/kotlin/com/roomdbinspector/plugin/`:
  - `session/TimelineModel.kt` → trim out snapshot fields and §5.3 window handling.
  - `ui/TableModels.kt` → trim to top-table model + result-grid model; drop `Snapshot` column.
  - `ui/InspectorPanel.kt` → major surgery; see §4.5. Strip device combo, connect/disconnect,
    auto-attach, the Query tab, the DB Explorer tree, schema cache. Down from 748 LOC to ~300.
- Write fresh:
  - `ui/RequestTab.kt` (~60 LOC).
  - `ui/ResponseTab.kt` (~80 LOC).
  - `session/MessengerSession.kt` (~120 LOC) — one coroutine collecting `eventFlow`, one suspend
    `setCapture(enabled)`. No reconnect, no ping/pong, no requestId tracking.
- Depends on `:protocol`.

### Stage 5 — Wire the tab provider (~0.5 day)
- `DatabaseLiveInspectorTabProvider.launchConfigs`: replace `emptyList()` with
  `listOf(AppInspectorLaunchConfig("dev.ahmedvhashem.databaseliveinspector",
  FrameworkInspectorLaunchParams(AppInspectorJar("inspector.jar", "inspector/", "inspector/"))))`.
- `createTab`: unwrap the single `Resolved` messenger target; build `InspectorPanel(project, MessengerSession(messenger, scope))`.
- Wire `processResources` to depend on `:stubs:dexJar` per §5.3.
- Align the vendor field across `plugin.xml` and `build.gradle.kts` (fix the §2.6 drift).

### Stage 6 — Verification (~0.5 day)
1. `./gradlew buildAll` produces a plugin zip + an agent AAR + the stubs dex bundled inside the zip.
2. Plugin unit tests for `TimelineModel` + the new `RequestTab`/`ResponseTab` rendering paths.
3. Agent unit tests carry across (`CursorResultSamplerTest`, `BoundedEventQueueTest`).
4. On-device E2E: install the agent AAR into the glovo test app
   (`/Users/hashem/Code/Hashem/glovo-interview-android`), install plugin in Android Studio,
   open App Inspection on the running app → tab shows live `query_started`/`query_finished` for
   `SELECT * FROM order_items` with 9 rows × 5 cols, `resultRowCount = 9`, no truncation —
   parity with the standalone's `tools/probe.py --selftest live_capture_result_rows` check.
5. No-agent path: install plugin against an app without the agent dep → tab opens, status label
   shows "Agent library not present", no crash, no events.

---

## 8. File-by-file copy/adapt/drop ledger

### Copy verbatim (or near-verbatim)
| From | To | LOC |
|---|---|---|
| `inspector-agent/.../capture/{RoomCaptureProvider,QueryEventSink,RecordedBindArg,BindArgRecorder}.kt` | `agent/capture/` | ~330 |
| `inspector-agent/.../query/{CursorResultSampler,CellFormatter}.kt` (sampler loses redaction param) | `agent/query/` | ~280 |
| `inspector-agent/.../internal/{BoundedEventQueue,KillSwitch}.kt` | `agent/internal/` | ~310 |
| `RoomDBInspector/.../protocol/*.kt` (trimmed) | `protocol/.../protocol/` | ~150 of 382 |
| `RoomDBInspector/.../session/TimelineModel.kt` (trimmed) | `plugin/session/` | ~90 of 142 |
| `RoomDBInspector/.../ui/TableModels.kt` (trimmed; Snapshot col removed) | `plugin/ui/` | ~70 of 86 |
| `RoomDBInspector/.../ui/InspectorPanel.kt` (heavy surgery, see Stage 4) | `plugin/ui/` | ~300 of 748 |
| **new** `agent/internal/Limits.kt` | — | ~10 |

**~1,540 LOC reused.**

### Write fresh
| File | LOC |
|---|---|
| `agent/DatabaseLiveInspector.kt` (public API + queue glue) | ~80 |
| `agent/DatabaseLiveInspectorInspector.kt` (@Keep) | ~80 |
| `agent/DatabaseLiveInspectorInspectorFactory.kt` (@Keep) | ~10 |
| `stubs/DatabaseLiveInspectorInspectorFactory.kt` (stub + AgentMissingInspector) | ~25 |
| `plugin/session/MessengerSession.kt` | ~120 |
| `plugin/ui/RequestTab.kt` | ~60 |
| `plugin/ui/ResponseTab.kt` | ~80 |

**~455 LOC new.** (`AgentBridge.kt` ~30 LOC removed; stub replaces it at 25 LOC; Inspector/Factory
moved from `:inspector` into `:agent` where they no longer need a reflective bridge.)

### Drop entirely
- `inspector-agent/.../query/{QueryExecutor,QueryPager,ReadOnlyGuard}.kt` (no run_query, ~280 LOC).
- `inspector-agent/.../schema/SchemaReader.kt` (no get_schema, 153 LOC).
- `inspector-agent/.../api/TraceScope.kt` + `beginTrace`/`recordSnapshot` (~50 LOC).
- `inspector-agent/.../internal/Redactor.kt` + `RedactionLevel` + `RedactorTest.kt` (~100 LOC).
- `inspector-agent/.../transport/{SocketServer,FrameCodec}.kt` (~335 LOC) — AS owns transport.
- `inspector-agent/.../internal/AgentCore.kt`'s run_query/get_schema/list_databases/cancel_query
  handlers + database registry + `describeDatabase` + invalidation observer (~300 LOC).
- `RoomDBInspector/.../adb/AdbService.kt`, `transport/*`, `autoattach/*`, plus the transport half
  of `InspectorSessionService` (~691 LOC) — replaced by `MessengerSession`.
- `RoomDBInspector/.../ui/InspectorPanel.kt`'s Query tab, DB Explorer tree, schema cache, device
  combo, connect/disconnect, refresh actions (~400 LOC of 748).
- `protocol/Messages.kt`'s removed message types (~230 LOC of 382) + `redaction`/`sampleRate`
  fields on `set_capture`/`capture_state`.

---

## 9. Risks & known unknowns
- **Internal API drift.** `AppInspectorTabProvider` is undocumented and `Non-Dynamic`. Re-verify
  on each AS release; pin `sinceBuild`. Source mirror: `JetBrains/android@idea/2026.1`.
- **`Class.forName` ABI contract.** The stub's link to `:agent` is a string literal FQN. If the
  agent class is renamed or its package changes, the stub silently falls back to `AgentMissingInspector`
  at runtime. Mitigate: annotate `DatabaseLiveInspectorInspector` with `@Keep` to prevent R8
  from renaming it, and add an ABI test that verifies the class exists by its expected name.
- **`androidx.inspection` jar at compile time.** Per the AndroidX README, this artifact is
  "provided by Android Studio in runtime, unlike regular libraries." Lift the JAR from the local
  AS install (`compileOnly files(…)`) for `:stubs` at compile time; resolve the exact path during
  Stage 3. The path changes between AS releases — pin to the specific AS version used.
- **DEX packaging glue.** `:stubs:dexJar` is the only piece without an off-the-shelf Gradle
  plugin. The plan in §5.4 is simple; expect ~1 hour pinning the exact jar path. The module is
  `kotlin("jvm")` so there's no AGP intermediates ambiguity — the standard `jar` task output
  goes directly to d8.
- **No redaction means raw user data on the wire.** Intentional: debuggable-only, on the
  developer's own dev device. README must call this out so it isn't surprising.
- **Pause UX while events are queued.** When the user clicks Pause, the agent stops *enqueuing*
  new events, but already-queued events still drain. Acceptable; document.
- **Coroutines vs plain threads.** `MessengerSession` is the only file with `kotlinx-coroutines`.
  Hop to `invokeLater` before touching `TimelineModel` or any Swing component.

---

## 10. Suggested sequencing for the next session
1. Confirm strategy: **γ** (default) or β.
2. Fix the §2.6 vendor-name drift between `plugin.xml` and `build.gradle.kts`.
3. **Stage 1** (`:protocol`) — small, unblocks everything else.
4. **Stage 2** (`:agent`) — all on-device logic including `Inspector` + `InspectorFactory`; add `@Keep`.
5. **Stage 3** (`:stubs`) — the ~25-line stub + services file + `dexJar` task; verify the jar.
6. **Stage 4** (`:plugin` re-host) — UI + `MessengerSession`.
7. **Stage 5** — wire the tab provider to the bundled dex; `./gradlew buildAll` becomes the
   one-command rebuild.
8. **Stage 6** — verification: produce a screenshot of the live timeline against the glovo app and
   archive it next to this plan.
