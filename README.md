# Assignment 4: Matchmaking Facade (variant 16)

This Java 17 console application implements the assigned **Video Game Matchmaking** domain. The common facade coordinates three independent, disk-backed subsystems: party reservation (A), server allocation (B), then anti-cheat confirmation and session logging (C). Each subsystem can also be called directly. No external service or database is needed.

## Build, run, and reset

On Windows, with Java 17+ and Maven 3.9+, run `.\run.cmd` in PowerShell. It builds the runnable JAR and opens the menu without changing PowerShell's script policy. Alternatively, run `mvn package` then `java -jar target/matchmaking-facade-1.0.0.jar`. Optional JAR argument: a data directory. The default is `data/` in the current directory.

Run `mvn verify` for all tests and the JaCoCo coverage check. The HTML report is generated at `target/site/jacoco/index.html` (CSV and XML are beside it). A [coverage CSV snapshot](docs/coverage.csv) is also committed. JaCoCo checks at least 80% line coverage of the production code other than the interactive `Main` menu. The project includes **14 tests**, including console input, both compensation depths, and continuation after restarting the menu. On this Windows account, Maven's test fork mangles the Cyrillic user path; running Maven from an ASCII `subst` drive works (example below). This is an environment issue, not a project dependency.

```powershell
$projectPath = (Get-Location).Path
subst X: $projectPath
New-Item -ItemType Directory -Force X:\target\tmp | Out-Null
$env:TEMP = 'X:\target\tmp'; $env:TMP = 'X:\target\tmp'
Push-Location X:\
mvn -q '-Dmaven.repo.local=C:\Users\48F4~1\.m2\repository' verify
Pop-Location
subst X: /D
```

To reset for a clean live demo, close the app and delete only its `data/` directory: `Remove-Item -Recurse -Force ./data`. Tests use a fresh temporary directory per test and never touch this demo data.

## Live demo

The menu contains only workflow and administration operations:

| Option | Action |
|---|---|
| 1 | Create a request; choose whether to run all stages immediately or stop after A |
| 2 | Perform the next saved step for a request ID |
| 3 | Inspect saved states for a request ID |
| 4 | Manually compensate a request ID |
| 0 | Exit |

For a full run, choose **1**, enter the player data, and answer **yes** to **Run all steps now?** The application prints the ID before B or C can reject the request, and displays the actual saved state after success or compensation. Both failures are triggered through ordinary input; no separate demo buttons or preset requests are needed.

| Scenario | Player | Rating | Party size | Ping | Suspicious | Final persisted state |
|---|---|---:|---:|---:|---|---|
| Success | Anuar | 1500 | 2 | 40 | no | COMPLETED / RESERVED / ALLOCATED / CONFIRMED |
| B rejects high latency | Anuar | 1500 | 2 | 121 | no | COMPENSATED / CANCELLED / NONE / NONE |
| C rejects suspicious player | Anuar | 1500 | 2 | 40 | yes | COMPENSATED / CANCELLED / RELEASED / NONE |

For a restart demonstration, choose **1** and answer **no** to **Run all steps now?** A is saved and the app stops at `PENDING_B`. Copy the ID, exit with **0**, restart, and choose **3** to inspect that ID. Choose **2** to perform B, inspect again, then **2** to perform C. Option **4** manually compensates a completed or interrupted workflow; a completed request ends with `REVOKED / RELEASED / CANCELLED` for C/B/A. An invalid rating such as `-1` demonstrates rejection at A before any subsystem entity is committed.

## Design and persistence

`PartyService` uses `RatingRule`, `PartyTicket`, and `PartyRepository`. `ServerService` uses `LatencyRule`, `ServerAllocation`, and `ServerRepository`. `MatchService` uses `AntiCheatRule`, `MatchRecord`, and `MatchRepository`. Each package has its own entity, pure decision class, service, and gateway. The entities enforce legal transitions themselves; the facade cannot set a status directly. None of the three packages imports another subsystem.

Each repository writes its own `.properties` file under `data/party`, `data/server`, or `data/match`. `WorkflowRepository` separately writes `data/workflow/<id>.properties`. `Store` writes a temporary file and atomically replaces the record. An app restart creates new objects that read those files. This is durable local state, not a static map.

The common `MatchmakingFacade` persists `PENDING_B`, `PENDING_C`, `COMPLETED`, `COMPENSATING`, or `COMPENSATED`. It checks the *actual saved entities* before advancing or undoing. If B rejects high latency after A committed, it cancels A. If C rejects the player after A and B committed, it releases B then cancels A. The `COMPENSATING` state lets a later run finish an interrupted manual undo. A stale progress marker after a saved B or C entity does not duplicate that entity. `AdminFacade` offers only inspection and manual compensation.

The design assumes one CLI process writing a data directory at a time. A production multiplayer service would need concurrency control and transactional guarantees across these separate files.

## Principle of Least Knowledge

Without a facade, a client would need friends in every subsystem and would have to coordinate them itself:

```java
PartyTicket ticket = parties.reserve(id, player, rating, size);
try {
    ServerAllocation slot = servers.allocate(id, ping);
    try { matches.confirm(id, suspicious); }
    catch (StageRejected error) { servers.release(id); parties.cancel(id); throw error; }
} catch (StageRejected error) { /* distinguish B from C and avoid double undo */ }
```

With the facade, the normal client has one immediate friend and one operation:

```java
Workflow result = matchmaking.run(player, rating, size, ping, suspicious);
```

The facade owns sequencing, compensation, and restart behavior. The console receives the result, not internal repository objects. For inspection or manual undo, it talks to the narrower `AdminFacade`.

## Facade versus Adapter

The problem is to make several existing operations act like one coherent matchmaking action. Party reservation, server allocation, and anti-cheat confirmation already have useful APIs; the client needs their order, failure handling, and recovery hidden behind a simpler entry point. That is the intent of a **Facade**. An Adapter would translate one incompatible interface into another expected interface, such as turning a third-party server API's response into `ServerAllocation`. Such translation would not coordinate A, B, and C or reverse committed steps. Here the two facades organize workflows and administration; they do not convert interface shapes.

## Test design and diagram

The JUnit tests cover the successful chain, B and C rejection with saved end states, restart between stages, stale workflow markers, manual and interrupted compensation, invalid first-step input, and direct use of all three subsystems. A `@TempDir` fixture makes tests fast, independent, and repeatable. The `gameAt(disk)` helper reads like a small domain-specific test language. Assertions make them self-validating. Tests and the code they cover were committed together. The vector class diagram is at [docs/class-diagram.svg](docs/class-diagram.svg). A step-by-step Russian explanation is at [docs/study-guide-ru.md](docs/study-guide-ru.md).
