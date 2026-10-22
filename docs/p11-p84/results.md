# §84 productive outstanding lifetime repair — WIP, release gate NO-GO

Base/local/remote verified at start: `17d883fbf9493495a444540ed0a21a2daade9b4f`, branch
p11-performance-optimization. Independent §83 reviews in #33/#34/#44 read. No new cache/demo,
UI, dependency, migration, schema/cipher/key format, session duration or mutation epoch.
Normal `reuseEnabled=false` and `ramReuseEnabled=false` remain unchanged.

## IMPLEMENTIERT

[Ownership contract and phase/charge matrix](lifetime-contract.md).

The actual ProductActiveCoreLoader now has ONE outstanding ownership record. It reserves the
existing transition envelope before ticket/worker/key admission; the real ActiveCoreStore
must also pass its local receipt through this record before either component materializes.
One worker body, one Main body and one payload handoff are clearable cells, not immutable
payload captures in queued Runnables. Dispatch acknowledgements and one-shot guards prevent
late/duplicate/canceled shells from releasing another read or accumulating candidate queues.
At most ONE admitted worker/candidate lifetime; an exposed retained lifetime also blocks a
replacement until trustworthy presentation retirement exists. This is deliberately strict.

Original ProductActiveCoreLifetime handles revocation from admission onward using the ONE
existing ticket subscription. It transfers the same original metadata lifetime to the VM.
Its close is an authority end, not a memory-retirement proof. Compose observation/disposal
are separate metadata events. Old consumer/frame uncertainty remains fully booked.

Known DB/coroutine/driver end and real borrowed-key finally are required for non-exposed
retirement. Unexpected scoped errors or suppressed cleanup errors stay sticky unconfirmed;
there is no successful retirement just because a DB handle happens to be closed. Existing
B2StatementCleanup and primary/suppressed exception handling remain unchanged.

## HOSTGETESTET

The original P83OverlapBudgetTest characterization stays. New tests call the actual productive
loader and real SQLite host store: oldR 2,102,832 / newT 8,387,520 (sum 10,490,352) is refused
BEFORE another SQL read/materialization, not merely summed after both values were allocated.
A real 200/1000 plan passes the productive admission again AFTER explicit unpublished-candidate
cancel, worker end and Main acknowledgement. An old empty shell can then run late/duplicate
without invalidating the new ticket or delivering the old payload.

Other targeted cases cover pending second/third requests, unacknowledged lost Main, worker
revocation, concurrent duplicate worker dispatch, uncertain queue acceptance, duplicate Main,
CORE/DOCUMENT pending and UNKNOWN, lock/BLOCKED, generation/action change, BUDGET_REFUSED,
known no-DB reader failure vs unconfirmed scope end, disposed Compose, full-view fencing, and
original-ticket/fresh-READY hit authority with no new SQL or idle renewal.

The previous direct `load("b")` lifetime test assumed that superseding an outstanding result
was admissible. That case is now a separate stronger backpressure test: request b is refused,
old a ticket/payload remain unchanged and authorized for a, no second read/epoch appears.
It is not skipped or silently dropped. Synthetic host producer signatures were adapted to
explicit admission; the old P73 bridge remains synthetic, not native admission evidence.

Final host counts and full validation logs are recorded in host-counts.json. Full regular
suite: app 688 + data 1,157 + security 4 = **1,849**, no failure/error/skip; domain NO-SOURCE.
Whole P77 app suite: **690 executions** (688 shared tests + 2 P77-specific), no failure/skip;
not 690 additional unique regular tests. Required offline Debug/Release/Androidtest/manifest
and separate P77 isolation build pass. Early compilation/test failures remain archived.

## ANDROID-GREEN — scoped production results

The same P77 Runtime→READY→original ticket→real Room/SQLCipher/keyowner→loader/lifetime→VM/
Compose path is used, with the established package-guarded synthetic credential authority.
The old §83 three-graph raw reports remain unchanged and open for comparison. The §83 scanner
is reused, extended only to inspect the new atomic handoff cells/metadata wrappers.

`roles32/multi_pending` explicitly requests SECOND and THIRD reads while Main has a complete
candidate. Both return false before new tickets/readers; the existing candidate is unchanged.
After delivery, a real old core is rooted in actual Compose slots. The same failed-hit/fresh-
read navigation attempt from §83 then requests second and third navigation again: both are
refused while the actual old Compose root is still present. The old-frame record remains
booked after VM clear and actual Compose disposal. The former old Compose + second Main +
third worker combination is now unreachable: the fence prevents even the unsafe second step,
not merely the test's third navigation. No navigation request was omitted to get GREEN.

Normal 200/1000/6 and 200/1000/32 initial reads pass with original local charges. 16 authorized
RAM navigations per retained case leave SQL reader/ticket counts unchanged. Full normal
TaskBoard/editor remains reachable; one actual OnDraw with form open is observed. Returning
to fresh compact materialization is conservatively DEFERRED while full/presentation retirement
is unconfirmed. This is not full displayed-core replacement or release-capacity acceptance.

`pending_discard` clears both callback and payload cells but first asserts that a new read
is STILL refused. Only after the old shell acknowledges can the next actual SQLCipher read
start. Old shell duplicates and old lifetime close do not invalidate that new read. A separate
new-READY-generation case repeats this real handoff. `pending_lost` never receives the Main
acknowledgement and stays booked even after explicit cancel; no GC or fake disposal releases it.

Worker lock is injected in the actual open native row scope. CORE task create and DOCUMENT
import use actual repository admission and held worker queues; independent readticket and
writer-pending fences are checked. The document input is invalid before reserve/source and
is NOT a successful import proof. Generation/action/BLOCKED and UNKNOWN completion deny
late publication; UNKNOWN is metadata injection, not a native transaction-failure claim.

All inspected core cursors/windows/driver/key end are checked using existing diagnostics:
nonzero Window during the row hold; closed observed cursors, zero observed Window pointers,
known preflight/Room/driver end and wiped actual borrowed Java array after core completion.
Only inspected resources are claimed. Full Workspace/native history scopes and physical
native key copies/allocator frees are not certified by those core hooks.

## ANDROID-RED / HARNESS-ABBRUCH / NICHT BEWIESEN

The first §84 roles100 budget attempt wrongly held its rejected-result Main task while waiting
for the model error and retirement. At idle expiration it correctly stayed REVOKED/booked,
then the runner timed out. It is a **HARNESS-ABBRUCH**, despite raw FAIL, because the intended
budget-result acknowledgement was never executed. Its unedited report is retained. The fixed
runner explicitly dispatches that held result before asserting BUDGET_REFUSED/no payload/
original ticket death/known scope end and retirement. No assertion was weakened.

The extra cleanup-uncertain case reuses §82 rowloop + post-Room-close injected wrapper faults
around actual resources. It preserves primary/suppressed identity and resource observations,
but the productive memory owner stays FAILED_OR_UNCONFIRMED with the entire transition held.
It is NOT a genuine SQLCipher reset/close-before-release reproduction and no diagnostic rescue
is a productive cleanup success.

The §83 stress RED is **unchanged**, NOT rerun as GREEN: 129,000 persisted TaskEvents, 90s
Full-Workspace timeout despite 11 real touch pairs, native process heap sample 44,969,680 B.
Root cause is the eager full path: WorkspaceStore → TaskStore.board → TaskDao.events action-
wide list → full map of events/TaskBoard → Main/UI. It fetches/materializes the complete history,
not a bounded on-demand page. Events are not discarded/shortened, no session deadline changed.
This remains a functional release blocker for the separate immediate §85 history/detail fix.

## BLOCKER and capacity limits

- One candidate per Runtime/Loader is now enforced; no new candidate queue can grow behind a
  pending/retained/uncertain lifetime. This is production code, not measurement alone.
- Initial normal 200/1000 capacity and hits work. After any value escapes to a consumer there
  is NO trustworthy frame/consumer retirement producer yet. A subsequent displayed-core
  fresh read (including regular gate-off navigation) is intentionally refused. The concrete
  missing fix is a verified presentation/consumer end plus calibrated bounded native/frame
  reservation on this SAME lifetime; never treat VM clear/Dispose/OnDraw/generation as end.
- Full-view return and eager 129k history need the §85 bounded detail/history ownership
  integration. The existing fullViewStarting fence preserves the boundary; it is not a hidden
  unbounded fallback or a second snapshot owner. Full-view cost is UNCONFIRMED, not zero.
- The whole 8 MiB transition booking is a conservative exclusion allocation, not evidence
  that native/ART/Compose costs physically fit. Every report says calibration UNCONFIRMED.
  No exact native reserve is reverse-engineered from PSS. No general 8+8-MiB certification.
- Old/new Activity/Runtime rendering overlap, all snapshot/layout/RenderThread references,
  native allocator/key-copy census remain unproved. No global singleton/second owner introduced.
- #32 pre-release native cleanup failures, #39 secure Keyguard/DeviceCredential, #36 editor ABA,
  RED-4 and the remaining release checks stay open. RAM release gate stays NO-GO.

Raw ART/native heap/PSS/FD/GC samples are separate process observations with diagnostic overhead,
not additive deep sizes. The phase table, local receipt and FULL envelope reservation must not
be confused. No forced GC, heap dump, new data dependency or performance optimization round.

## Reproduction / protection

Run `python3 docs/p11-p84/summarize.py` to recompute case/phase/native inventories and hashes.
Build APK pair with `-PpagTestBuildType=p77runtime` and verifyP77RuntimeIsolation. The runner
is explicitly org.pag.prototype.P84OutstandingCoreProbe, never a connected all-class suite.
Freshly verify Samsung RFCT1186YRJ/SM-M526BR, package IDs, manifest, runner, variant and both
installed/new signers before using the reviewable device-control.py / run-case.py.

Only own org.pag.prototype.a.p77runtime and .test are updated. The three protected apps remain
installed with unchanged version/install/update/signature metadata, including P76 until 1.0.
No data clear/uninstall/global cleanup or device lock/credential change. End-of-run own fixture
inventory is checked separately. Product reuse stays false. Regular WIP commit/push only;
STOP for independent GitHub review, no merge/release/force-push.

## Final correlated samples and run balance

20 explicit custom-runner invocations: **19 scoped ANDROID-GREEN, 0 ANDROID-RED,
1 HARNESS-ABBRUCH**. These are not JUnit executions. See case-results.csv with raw report
hashes; no zero-JUnit result is counted as a pass. The inherited §83 history ANDROID-RED
remains a separate unresolved release finding, not part of this new-run count.

Maximum observed pending candidates: **1**; scanner-observed rooted Core graphs: **1** in
these controlled phases. This does not census every ART/RenderThread reference. Normal
native fixture local receipts vary with generated text: R 1,655,732–1,655,772 B and
T 6,599,120–6,599,280 B; roles32 reached R 1,879,216 / T 7,493,056 B. The productive owner
books T **8,388,608 B**, not local T plus another allowance. R is separately bounded by
8,388,608 B. Revoked old presentation moves its obligation into the still-exclusive T;
R=0 in metadata does not assert old physical references vanished. The normal baseline
R 1,655,712 / T 6,599,040 remains admitted in the host counterexample suite.

Across phase samples, raw process maxima were ART used 48,762,528 B, NativeHeap
12,363,552 B, PSS 185,337 KiB, FD 138. Maxima occur at different times and MUST NOT be
summed. reader_window_open observed one live inspected Window; post-core completion
samples observed zero inspected live Windows. These are process samples and inspected
handle counts, not deep Core sizes or calibrated allocator reserves. Full per-phase values,
charges, time, uncertainty and reference observations are in phases.csv / phase-maxima.json.

State.retirementConfirmed concerns ONLY the known compact-core ownership chain. It can
coexist with fullViewUnconfirmed=true; that never certifies the full projection or frames.
The final read-only own-fixture inventory is empty. Protected package metadata comparison
passed after the final installed APK pair. installed-artifacts.json identifies that pair;
main-series-artifacts.json preserves the earlier series provenance. Build logs preserve
failures as well as successful final runs; trailing terminal whitespace alone is normalized
in archived build/device text, while native runner reports remain unedited.
