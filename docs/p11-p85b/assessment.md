# §85b — Task history final acceptance, WIP (#45)

Base `4593333685fa45354354e745d874f9741545e14d`, branch
`p11-performance-optimization`. AGENTS.md, PRODUCT.md, §85a assessment and #45 read.
No architecture/schema/index/crypto/dependency/UI change. §84, writer/session gates,
32-row limit, 30s idle/180s absolute, RAM gates and budgets unchanged.

## Cause and narrowly scoped fix

Actual P77 Runtime → ProductTaskRepository → original READY/ticket/key owner →
ActionDatabaseAccess → Room/SQLCipher on Samsung SM-M526BR / RFCT1186YRJ.
The database is reopened and fully checked on each page. Approximately 2.65s per
request goes to existing native opens (~0.58s each), cipher_integrity_check (~0.34s),
integrity_check (~0.92s), foreign_key_check (~0.21s) and Room schema/open overhead.
ROOM_OPEN_SCHEMA includes ROOM_NATIVE_OPEN; nested phases must NOT be added twice.
No security preflight has been skipped or weakened.

Task-wide UTF-8/title/key preflight costs ~0.71s; event validation ~0.32s;
selected-task COUNT ~0.12s. Standard pages repeatedly select the same <=32 scalar
keys for max-length sizing, summed sizing and payload fetch (~0.27s SQL step each).
The payload statement's remaining ~0.16s for 32 rows includes decoding, domain objects,
gates, diagnostic reflection, and driver calls; it is NOT a pure Kotlin allocation
measurement. Last-page payload has one event. Retained event list is always <=32.

Before this change, eventsAtPage ran task preflight and COUNT, read one OFFSET
anchor, then called events, which repeated the same task preflight and COUNT in the
SAME read transaction. The fix passes the transaction-local count to a private
post-preflight materializer. Both public entry points still perform every distinct
validation; event-wide type/enum validation, task presence, cursor existence,
metadata sizing, budget admission, UTF-8 decoding, liveness and publication remain.
No preflight result survives the transaction/request; no cache, new owner or retry.
Only CompactTaskStore's direct-entry orchestration changes in production.

## Query plans / schema work stopped

EXPLAIN QUERY PLAN runs on the same SQLCipher connection with the actual bound
parameters, before the measured statement step. Only fixed SQL and plan detail are
recorded, never bindings, keys or payloads. Raw before/after reports retain all plans.

- Event validation uses index_task_events_actionId_taskId.
- COUNT uses that covering index.
- OFFSET anchor uses that index PLUS USE TEMP B-TREE FOR ORDER BY; ~0.29/0.30s
  at middle/end before, ~0.30/0.31s after. OFFSET reads one scalar anchor, not a prefix
  of Event objects. This timing includes scan/sort/offset, not isolated sorter CPU.
- Each sizing/fetch selection uses the action/task index and temporary ORDER BY,
  then INTEGER PRIMARY KEY rowid lookups for the admitted rows.
- Cursor existence uses INTEGER PRIMARY KEY. The continuation probe scans the
  action/task index and grows to ~0.14/0.28s at middle/end.

An index covering actionId/taskId/occurredAt could change this plan, but requires
schema/migration design and review. This part is **STOPPED / not implemented**, as
requested. Sorter/temp/CursorWindow/allocator memory is not calibrated or certified.
Repeated <=32 key selection is documented, with no new key list/cache in this slice.

## Comparable measurements

Three repetitions per position before and after; same encrypted synthetic fixture:
200 people / 1,000 Tasks / exactly 129,000 action Events, selected task total 128,001.
No full 4,001-page Android series. Same runner/timing/diagnostic flow, first repetition
also gathers plans. Model delivery is sampled on Main (~20ms polling plus scheduling),
so it can be observed slightly after OnDraw. OnDraw is the host view's first draw
with the new page model, not a compositor/presentation timestamp or p95.

Medians in milliseconds:

| Position | Transaction before → after | Model delivery before → after | OnDraw before → after |
|---|---:|---:|---:|
| first | 2,170 → 2,196 | 4,909 → 4,948 | 4,877 → 4,914 |
| second (Next) | 2,162 → 2,212 | 4,884 → 4,955 | 4,857 → 4,927 |
| middle (2,001) | 3,404 → 2,637 | 6,064 → 5,324 | 6,099 → 5,351 |
| last (4,001) | 3,393 → 2,621 | 6,078 → 5,336 | 6,071 → 5,320 |

Direct model delivery improves ~740/742ms (~12.2%); transaction ~768/773ms.
Standard pages are unchanged by the fix and show ~1–1.5% run variation, no claimed
speedup. The earlier §85a 7.307/10.340s samples included additional UI locator/scroll
work; they are not comparable SQL-only timings. SQLCipher open/integrity time is
measured separately. Encrypted SQL step times inherently include SQLCipher/driver/
I/O costs; no claim to isolate encryption CPU from SQLite scan/sort CPU.

All individual samples and classified SQL/DB phases are in latencies.json and the
unaltered before/after runner outputs. These engineering observations do not replace
#32/#33 native-memory or §84 joint 8+8-MiB/presentation lifetime acceptance.

## Validation (final results below)

52 targeted host tests: CompactTaskStoreTest 25, TaskModelTest 27; zero failures,
errors or skips. The historical expensive 4,001-page host traversal was explicitly
excluded, not replayed. Existing §85a exhaustive keyset evidence remains historical;
the keyset SQL/materializer/order itself is unchanged. New full small-history reference
checks compare every row/page for PREPARATION/ACTIVE/COMPLETED with nonmonotonic times
and ties. Direct first/middle/last on 129,000 stored Events, <=32 payload reads,
exact totals, Unicode, budgets, invalid positions, pre-/post-anchor revoke, off-page
invalid Events and failures are covered by the targeted tests.

Initial build failures are retained: sandbox Gradle socket denial, missing artifacts
in project cache, and rejected copied live cache metadata. The existing ~/.gradle
cache was subsequently used with explicit execution approval, offline, with no
added/updated dependency. No source/project changes outside this repository.

Builds passed offline: P77 target/test APKs and verifyP77RuntimeIsolation, Debug APK,
Release APK and verifyReleaseVariantSafety. The release guard now explicitly rejects
P85PagingDiagnostics in DEX, and passes. Test helpers are in debug/androidTest sources;
normal ProductRuntime and ProductTaskRepository composition are unchanged.

The first four touch attempts retain raw FAILs. They pass physical first/second-page
navigation but the field locator supplies stale accessibility coordinates after scrolling.
The own-view synthetic capture (harness-stale-field.png) shows the field far below the
reported y=450–616 target: taps hit the summary, not the editor. READY and enabled=true
were observed. A fifth attempt failed before the first tap because UiAutomation.clearCache is
unavailable on this device (NoSuchMethodError). The final harness uses the supported
AccessibilityNodeInfo.refresh on every inspected node before reading its bounds. No product fix, focus command, accessibility click or
set-text action substitutes for physical taps/keyboard digits. The screenshot was taken
inside the test process from its own synthetic decor view, not by weakening FLAG_SECURE.

The final stress touch case passed: real field focus and IME visibility, actual numeric
keyboard digit taps for 0/4002/19-digit overflow/2001/1/2, real clearing/back key events,
disabled invalid-page action (also physically tapped), unchanged prior page for invalid
values, Next, Last and direct 1/2/middle. Exact IDs/order, total 128,001 and row counts
32/32/32/1 are checked; visible range labels are reached with physical swipes.
No accessibility ACTION_CLICK/SET_TEXT drives the history interactions.

Physical final samples from touch UP to OnDraw (single observations, no p95): first
5,018ms; Next 4,958ms; Last 5,331ms; middle 5,375ms; numeric first 4,930ms; numeric
second 5,217ms. The separate ondraw_ns field in the touch report starts BEFORE locating
and scrolling to the button; it must not be confused with tap_to_ondraw_ns or the
SQL/transaction measurements. The controlled before/after timing case calls public
model actions after setup; the touch case exercises the existing UI handlers.

Editor/lock stress case passed separately within the unchanged session limits:
physical Open/Cancel returns to the same Task with history cleared, reloads a real
page, physical Open/Save commits through the real repository/CAS path and refreshes
the non-eager workspace. A subsequent page is held at Main; physical Sperren displays
the locked screen, then the old Main runnable is executed and publishes no history
or selection. Editor and page assertions are not injected completion metadata.

CORE and DOCUMENT Main fences passed with visible Revoke text and no late payload;
UNKNOWN denies a physical restart before another read. Writer completion/UNKNOWN in
these cases are explicitly injected metadata, not vendor failures. The separate
statement-fault case runs the real repository/keyscope/Room/SQLCipher endpoint, throws
at its test statement wrapper, reaches BLOCKED pixels and clears history/selection.
It is not a genuine SQLCipher native-close fault. Budget rendering is not newly
exercised on Android; existing targeted host budget refusal assertions pass.

Final custom-run balance: **11 invocations: 6 ANDROID-GREEN, 5 HARNESS-ABBRUCH**
(raw FAILs retained, four stale-coordinate attempts and one unsupported API). No
product ANDROID-RED in the final cases. Each GREEN requires explicit assertions and
fixture cleanup, not zero JUnit tests. android-cases.csv records report hashes.
Protected P76 demo, regular app and P11probe remain installed and unchanged in
version/update/install/signature snapshots. Only the checked P77 target/test packages
were updated. Final own-fixture inventory is empty; the diagnostic PNG was removed
from the target cache. No global clear or credential/lock-setting changes.

## Verdict and residual risk

**PASS for #45's requested §85b functional/paging acceptance in the isolated Android
scope.** Leave #45 open for the independent review of this WIP; no merge or release.
The history remains completely addressable, with the unchanged full ordering, <=32
payload and exact counts. Actual IME/touch navigation, editor return and safe rejection
are now demonstrated. Standard page latency remains about 5s and direct entry about
5.3s on this device. No universal device/latency guarantee or p95 from three samples.

**Separate NO-GO gates remain unchanged:** §84 joint 8+8-MiB/native/frame/fullview
retirement, #32/#33 sorting/temp/CursorWindow/allocator calibration, #36 editor ABA,
#39 real secure credential authorization and RED-4. No new genuine vendor cleanup-fault
proof. No full 4,001-page Android traversal or replay of historical P11 suites.
Index/schema work remains stopped pending its own reviewed migration scope.

## Reproduction

Use the existing offline dependency cache. Targeted host command:

```
./gradlew :data:testDebugUnitTest --tests org.pag.data.product.CompactTaskStoreTest :app:testDebugUnitTest --tests org.pag.prototype.product.TaskModelTest -I docs/p11-p85b/targeted-tests.init.gradle --offline
```

Build P77 with `:app:assembleP77runtime :app:assembleP77runtimeAndroidTest
:app:verifyP77RuntimeIsolation -PpagTestBuildType=p77runtime --offline`.
Run device-control.py `before .`, then `install .`; run-case.py accepts series,
fixture and exactly one of timings/touch/editor_lock/fences/storage_error. Only the
isolated RFCT1186YRJ/P77 target is allowed. End with device-control.py `after .`.
Before/after benchmarking requires the respective CompactTaskStore implementation;
the baseline product source is available at the base commit. summarize.py rebuilds
latencies.json from the raw reports. Installed and baseline artifact hashes, manifests,
source hashes, host XMLs and failed build/harness reports are retained alongside this file.
