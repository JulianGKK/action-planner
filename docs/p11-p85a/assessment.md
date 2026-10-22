# §85a — bounded Task history, WIP

Base: `f85b558637c1ab824636ffc8945d224299a9e711`, branch
`p11-performance-optimization`; local/remote checked. AGENTS.md, PRODUCT.md,
P5a task/CAS/session contract and issue #45 read.

## Cause and product change

`WorkspaceStore.read(TASKS)` and `ProductTaskRepository.board` both called
`TaskStore.board`, which fetched `TaskDao.events(actionId)` without a limit.
The two Task detail composables then filtered the already materialized action-wide
list. Navigation, counters and editor fields need only Task snapshots, not events.

The real board now loads tasks only and returns `eventsComplete=false`; the empty
legacy list explicitly means NOT REQUESTED, never an empty chronicle. Existing bounded
full-board benchmark readers and demo data retain their explicit complete-list semantics.
Both affected product detail consumers now use one on-demand history page. TaskModel
replaces pages, clears them on editor/navigation/refresh/revoke and never accumulates
a chronicle. UI distinguishes not requested, loading, failure/budget/revoke, actual
zero total, range/total and further pages. Restart is explicit. Editor needs no history.

ProductTaskRepository.history reuses CompactTaskStore's <=32-event reader, metadata
sizing/admission before payload extraction, strict UTF-8 handling and Room usePrepared.
Order remains `(occurredAt,rowid)` ascending, including tied timestamps. Next is emitted
only when another row exists, so an exact 32-row final page has no spurious empty followup.
An explicit page number / last-page entry avoids forcing thousands of Next requests within
a 180s session. eventsAtPage reads exactly ONE `(occurredAt,rowid)` anchor with numeric OFFSET,
then calls the SAME admitted keyset materializer in the SAME transaction. OFFSET applies only
to two scalar sort keys, never an Event/DAO list. Invalid/stale/overflowing positions reject
before payload extraction. SQLite may sort keys natively; this is NOT a native memory bound.
No position/authority survives model/session clear. The full Task history entry accepts PREPARATION/ACTIVE/COMPLETED; ordinary compact ACTIVE
readers remain ACTIVE-only. Persisted status and the complete expected Task snapshot are
checked in the transaction before materialization. No schema/index/key/cipher change.

Every page acquires fresh READY, then an original readticket from the EXISTING shared
mutation port. Cursor action/task/generation/epoch must match; no shadow epoch or ticket
port. Worker liveness, operations permission, document namespace and owner-thread central
publication remain checked. The existing ActionDatabaseAccess cleanup-confirmation marker
must confirm Room/coroutine/driver end before a value leaves the keyscope. Ticket release is targeted. CORE/DOCUMENT pending and UNKNOWN
therefore refuse pages; stale pages after writes must explicitly restart. Exactly one page
request is in flight per existing repository, including Main. Duplicated dispatch is one-shot;
unknown executor acceptance stays occupied. Upstream/cleanup exceptions preserve failure,
block the current session when still current and keep the page slot unavailable; they never
become a successful empty result or authorize an optimistic retry/keylease.

Task writes/transition transaction/CAS/event append code is unchanged. Legacy storage tests
now inspect persisted events explicitly rather than treating the unfetched board as complete;
the original event-count/type/atomicity assertions remain. No new cache, dependency, owner,
budget, session duration or §84 retirement producer. The single compact-core lifetime remains
booked during old presentation/fullview uncertainty. Fullview/page co-retention is NOT a new
joint-memory certification; the existing 8+8-MiB NO-GO remains.

## Validation and limits

Final selected tests/builds, exact results and Android reports are archived alongside this
assessment. These are targeted task/workspace/paging/lifetime checks, not a replay of the
historical P11 series. Earlier failed build attempts are retained as failures.

The original §83 Android-RED is historical evidence of the old eager path and stays intact.
New §85a Android evidence is classified separately. A complete 4,001-page native traversal is
not claimed by sampling first pages. No p95/physical native-memory certification from a
single run or process PSS/FD sample. Existing #32–#39 release gates, #36 editor ABA and §84
presentation/frame/native calibration remain independent. Product RAM gates remain false.

Only isolated P77 packages may be updated after device, ID, variant, manifest, runner and
installed/new signature checks. The protected P76 demo, regular app and P11probe remain
untouched; protection snapshots and own-fixture cleanup are archived.

## Results

- **PASS / HOSTGETESTET:** 290 distinct selected host tests: 57 Data + 233 App, zero
  failures/errors/skips. Shared P77 App tests (233) repeated, not additional unique tests.
  Sequential concentrated traversal: every one of 128,001 selected events in 4,001 pages,
  exact IDs/timestamp ties/order, no accumulated output list; total stored 129,000. Host IPC/
  SQL traversal took about 59 minutes; this is not native latency evidence. The unchanged
  keyset materializer was tested exhaustively before adding the scalar direct-entry wrapper;
  that wrapper has separate real-SQLite middle/end/invalid/overflow/revoke/failure tests.
- Small/medium/large host boards: 20/40/240, 100/250/8,000 and 200/1,000/129,000
  people/tasks/events. No board query touched task_events. Unicode Task snapshots remain
  unchanged. PREPARATION/COMPLETED history, Task editor/CAS/transitions, counters, stale
  callbacks, budget rejection and scope-failure vs empty are covered.
- **PASS / BUILD:** final targeted Debug unit/APK/App+Data Androidtest compilation,
  P77 unit/APK/test-APK/isolation and Release/safety builds, all offline. See build logs.
  Earlier failures are retained: nullable runner code, wrong P77 unit target, mixed old/new
  App compilation after the hour-long Data run, and a stopped runner build in the wrong
  directory. Its own accidental parent-directory log was moved back into the project.
- **ANDROID-GREEN (final code):** real Runtime/READY/shared mutation ticket/Room/SQLCipher/
  SessionDbKeyOwner/TaskModel/Compose path on RFCT1186YRJ / SM-M526BR. Synthetic credential
  authority is restricted to the existing P77 package. Public model actions drive the test;
  actual OnDraw, accessibility content and genuine own-window swipes verify presentation.
  No complete IME/button-touch E2E claim.
- Final stress: exactly 200 people / 1,000 tasks / 129,000 stored events, selected total
  128,001. Workspace delivered in **5,303 ms**, eager events **0**, eventsComplete=false.
  Real editor form/draw and persisted edit/refresh succeed. Sequential pages contain 32
  events each; page 4,001 contains 1 event at offset 128,000; page 2,001 contains 32 at
  offset 64,000. Exact visible range labels verified. Direct-entry samples including UI
  checks: **7,307 / 10,340 ms**. This is not p95. Absolute 180s and idle 30s unchanged.
- Actual page scopes held at Main are denied after CORE and DOCUMENT admission. Those writer
  completions/UNKNOWN are explicitly injected metadata, not vendor transaction failures.
  A real Task editor write precedes them. Old cursor epoch rejected; restart after confirmed
  writer end succeeds; UNKNOWN denies before another lease; Lock clears page/selection.
- **HARNESS-ABBRUCH:** three early raw FAIL reports are kept: two accessibility scroll/locator
  failures below a long title and one transient null accessibility root. No history request
  had started. The unchanged product APK subsequently passed with awaited, actual own-window
  swipes; artifact hashes preserve that comparison. No product assertions were weakened.
  The small report marker was later corrected to avoid claiming stress-only direct-page work.

## Remaining acceptance

The old §83 90s/11-touch ANDROID-RED is untouched historical evidence. New reports are separate;
no old eager-list assertion is relabeled PASS. The functional eager Workspace/editor timeout
is fixed in the measured P77 path. Complete host coverage plus native middle/end entry shows
all history remains addressable without materializing it at once.

**NICHT AUSGEFÜHRT:** all 4,001 pages on Android, full historical P11 Android suites, complete
numeric-keyboard/touch E2E, new genuine SQLCipher cleanup fault injection, p95 and full memory
calibration. These are not hidden test skips. Scope confirmation requires known Room/coroutine/
driver end after actual keyscope return; individual CursorWindow pointers/opaque allocator/key
copies were not separately measured in this new endpoint. FD/PSS are process samples only.

**BLOCKER / next slice:** independent review of #45 and focused remaining native paging/error/
touch acceptance. The §84 joint 8+8-MiB/native/frame/fullview calibration remains NO-GO; direct
scalar sorting is also uncalibrated native allocation. No budget raise or optimistic retirement.
#32/#36/#39/RED-4 stay independent; reuseEnabled=false and ramReuseEnabled=false stay unchanged.

See changed-files.txt, host-{full,final,p77}/, android-cases.csv and unedited runner reports.

Final custom-run balance: **8 invocations, 5 scoped ANDROID-GREEN, 3 HARNESS-ABBRUCH**
(raw FAIL retained), 0 product ANDROID-RED in these new attempts. Custom runner, not JUnit.
Final small and stress cases pass; final small correctly reports no stress-only direct-page
work. All three protected package snapshots match after the last install; own fixture
inventory is empty. APK hashes/manifests and final source-snapshot hashes are archived.
Build captures normalize terminal trailing whitespace only; native reports are unedited.

Reproduce the targeted builds/tests normally from the repository. The exhaustive host test
in CompactTaskStoreTest is deliberately expensive (~59 minutes on this host). For a fresh
P77 build, run `:app:assembleP77runtime :app:assembleP77runtimeAndroidTest
:app:verifyP77RuntimeIsolation -PpagTestBuildType=p77runtime --offline`. The archived installer
accepts the APK project root as its second argument, e.g. `python3 docs/p11-p85a/device-control.py
install .`; first run `before`, then reverify the exact device and own package/signature
inventory. Default artifact root records the isolated source-copy build used in this run.
Invoke only the explicit `P85TaskHistoryProbe` runner, e.g. run-case.py with `review stress
task_history`, and end with installer `after`. Never install protected IDs or clear app data.
