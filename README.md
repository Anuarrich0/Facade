# Assignment 4: Matchmaking Facade (variant 16)

This Java 17 console application implements the assigned **Video Game Matchmaking** domain. The common facade coordinates three independent, disk-backed subsystems: party reservation (A), server allocation (B), then anti-cheat confirmation and session logging (C). Each subsystem can also be called directly. No external service or database is needed.

## Build, run, and reset

On Windows, with Java 17+ and Maven 3.9+, run `./run.ps1` in PowerShell. It builds the runnable JAR and opens the menu. Alternatively, run `mvn package` then `java -jar target/matchmaking-facade-1.0.0.jar`. Optional JAR argument: a data directory. The default is `data/` in the current directory.

Run `mvn verify` for all tests and the JaCoCo coverage check. The HTML report is generated at `target/site/jacoco/index.html` (CSV and XML are beside it). A [coverage CSV snapshot](docs/coverage.csv) is also committed. JaCoCo checks at least 80% line coverage of the production code other than the interactive `Main` menu. The test run in this checkout has **11 passing tests and 200/201 covered lines (99.5%)**. The one uncovered line is a filesystem error path in `Store`. On this Windows account, Maven's test fork mangles the Cyrillic user path; running Maven from an ASCII `subst` drive works (example below). This is an environment issue, not a project dependency.

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

Menu option **1** runs all three stages and prints the workflow ID and persisted states. Use these realistic inputs:

| Scenario | Player | Rating | Party size | Ping | Suspicious | Final persisted state |
|---|---|---:|---:|---:|---|---|
| Success | Anuar | 1500 | 2 | 40 | false | COMPLETED / RESERVED / ALLOCATED / CONFIRMED |
| B rejects high latency | Anuar | 1500 | 2 | 121 | false | COMPENSATED / CANCELLED / NONE / NONE |
| C rejects suspicious player | Anuar | 1500 | 2 | 40 | true | COMPENSATED / CANCELLED / RELEASED / NONE |

Option **2** stops after A has persisted a reservation. Write down the printed ID. Option **4** inspects it. Exit with **0**, start the app again, then option **3** advances B; inspect again and advance C. This demonstrates recovery from disk. Option **5** manually compensates a completed or interrupted workflow, then inspection shows `REVOKED / RELEASED / CANCELLED` for C/B/A. The ID is printed before any B or C failure, so rejected workflows can be inspected too.

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
