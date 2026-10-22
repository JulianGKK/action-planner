# §83 Joint 8+8 MiB: WIP evidence, acceptance BLOCKED

Base/local/remote at start: `bffe9951cff11bbca9f610cd2f92a72149716a82`,
`p11-performance-optimization`. Latest independent §82 reviews in #32/#33/#34/#44 were
read. This work does **not** close #33/#34 or activate normal product RAM reuse.
No cache/owner architecture, dependency, migration, cipher/key format, UI or budget changed.

## Implemented and host-tested

- Actual CoreReadTestHooks now report the existing aggregate admission and observe the
  completed candidate AFTER DB/Room/driver and borrowed-key finally return. Defaults have
  no test producer; only the existing guarded P77 authority installs P83 diagnostics.
- P77 fixture selection adds 20/40, 100/250, 200/1000, 6/32/100 functions, supplementary
  Unicode/UTF8 and long-text stress data. Pseudonyms/titles are not truncated for admission.
  Stress stores the original 1,000 task-created events plus 128,000 additional legal-type
  cancel/reopen events (129,000 in total). No real person data or keys are logged.
- ProductActiveCoreLoader now retires the EXACT original ticket for a rejected result
  without a retention lifetime. Previously a current BUDGET_REFUSED callback could leave
  its readticket live. New host regression covers one read/delivery, no retry/epoch change,
  and old-targeted-revoke after a newer admission; native roles100 verifies rejection and
  original ticket death. No session or writer fence is relaxed.
- The full P77 host suite exposed an existing AppModeTest that assumed only regular/demo
  IDs. It now checks the exact P76/P77 IDs and mutual exclusion of variant flags as well.
  No test was skipped or assertion removed. The failing early build remains archived.

Host final: app 688 + data 1,144 + security 4 = **1,836** regular tests, zero errors/failures/
skips; domain has NO-SOURCE. The **entire P77 app host suite (690 executions)** also passes,
including the 688 shared tests and 2 P77 collector tests; these are not 690 additional unique
regular tests. Required offline Debug/Release/Androidtest compilation/manifests and separate
P77 isolation pass. Logs include early sandbox/build/test failures and final successful runs.
See host-counts.json, host-budget-counterexamples.txt and full-offline-complete.txt.

## Android execution inventory

20 actual Custom-Instrumentation invocations: **16 ANDROID-GREEN, 3 ANDROID-RED,
1 HARNESS-ABBRUCH**, no JUnit-zero-count pass or skips. Of the 14 main-series cases,
13 are GREEN and stress is RED; two earlier valid replacement runs are GREEN, early stress
is RED, the first touch probe is HARNESS-ABBRUCH, its corrected repeat is RED, and the final
large editor-Draw probe is GREEN. Raw PASS/FAIL strings and SHA256s remain in case-results.csv.
GREEN denotes the stated observation/revoke assertion, NOT overall joint-budget acceptance.
The joint acceptance result for the entire slice is BLOCKER / NOT PROVEN.

## Actual joint reference observations

See [reference inventory](reference-matrix.md), [phases.csv](phases.csv), original runner
reports and [phase maxima](phase-maxima.json). Times share elapsedRealtimeNanos. A short,
bounded Main hold prevents recomposition of the actual old subtree while the real worker
continues. This is scheduling injection, not a synthetic in-memory payload or second VM.

1. Original core is authoritative in the one VM slot and strongly reachable from the actual
   WrappedComposition.original slot graph. First PERSONS composition is observed.
2. Denying an original hit uses the existing fresh-read fallback, retiring the old ticket/slot.
   At the first PEOPLE_ROW the old payload remains in the real Compose slots, a known native
   cursor/window is open (nonzero pointer, row contents present), Room/driver are open and
   borrowed keylease is live. No claim that native resources are already free at this phase.
3. After all native/key cleanup, the new complete payload remains on the paused worker.
   Then its actual ProductActiveCoreLoader Main Runnable retains it, while old Compose still
   retains the old payload. IDs and strong root paths are captured, not inferred from GC.
4. Running that Runnable on Main installs the new VM core while the old Compose graph is
   still reachable. Closing the old lifetime again cannot revoke the new slot/ticket.
5. A subsequent composition has the new core in actual slots. Absence of the old core on
   these particular paths is NOT proof of complete heap/snapshot/frame/GPU retirement.
6. The full-view path clears the compact VM slot before materializing the existing full
   Workspace/TaskBoard. Small/medium/large/32-role boards and editor model states succeed.
   Earlier runs close the form in the same Main callback; they do NOT prove an editor frame.
   The final `editor-large-replace` runner additionally waits for an actual OnDraw while the
   form remains open, then returns to navigation. It does not assert pixel/GPU contents.
7. `roles32/multi_pending` keeps the existing second result Runnable and starts a third normal
   read with the SAME runtime/loader/namespace/mutation port. Old Compose + second Main result
   + third worker result are three DISTINCT simultaneously strongly referenced core graphs.
   At third-reader-open, old Compose and the pending result overlap the open native/key scope.
   No extra cache/registry or second diagnostic task Gate creates these payloads.

The maximum proven number of compact core graphs is **three**, not one. Their retained
receipt equivalents are 3 × 1,879,156 = **5,637,468 bytes**. These are engineering receipt
sums, NOT measured ART deep sizes. One VM slot cannot bound all outstanding graphs.
The loader checks `latestLoad` at Main execution, but obsolete Runnable captures have no
observed global count/retirement bound while queued. No arbitrary GC-based release is assumed.

## Charges and fixtures

Representative main-series receipts below are requested engineering bytes, not process
memory. Small variation across runs comes from the selected first-page text/ID lengths;
the CSV is authoritative for each invocation. Limits remain exactly 8,388,608 each.

| Fixture (people/tasks/functions) | Retained | Transition | Result |
|---|---:|---:|---|
| small 20/40/6 | 1,501,784 | 5,983,328 | core and full view GREEN |
| medium 100/250/6 | 1,655,572 | 6,598,480 | core and full view GREEN |
| large 200/1000/6 | 1,655,712 | 6,599,040 | core and full view GREEN |
| 200/1000/32 | 1,879,216 | 7,493,056 | core and full view GREEN |
| 200/1000/100 | 2,464,208 | 9,833,024 | BUDGET_REFUSED before core payload |
| stress 200/1000/6, long Unicode + 129k stored events | 1,955,232 | 7,797,120 | compact core GREEN phases; full history RED |

100 functions is a security rejection observation: no candidate/partial VM core, model error
flag set, original ticket dead, known cleanup confirmed. The existing error UI branch follows
that flag; this runner does not independently inspect error pixels/Accessibility text. The
9,833,024 requested transition bytes are NOT an admitted allocation or sealed receipt.
There is no fallback lease, silent retry, artificial payload shortening or automatic full-view
fallback for that refusal. This does not substitute for realistic 200/1000 capacity acceptance.

The existing real-SQLite host `twoIndividuallyFittingRealProjectionsAreJointlyRefusedBeforeAnyPayload`
passes: people and tasks individually fit, their aggregate does not, and payload fetch count is
zero. New host overlap characterization keeps two real independently admitted results alive:
oldR=2,102,832; newT=8,387,520; an assignment charging the old graph to transition while the
new transition obligation remains yields **10,490,352**. This is a conservative accounting
collision, NOT measured heap overflow or proof that total 16 MiB was physically exceeded.

Similarly the native three-graph case has old/pending R=1,879,156 each and third-read
T=7,492,816: keeping old R in retained and pending R plus new T in transition requests
**9,371,972 transition** and **11,251,128 combined engineering bytes**. No joint admission
currently adjudicates those live obligations. Temporary-factor allowances cannot silently be
reassigned or double-counted as certified native/Compose reserves. Actual allocations may be
far smaller than these allowances. This evidence establishes the enforcement/calibration gap,
not a universal actual-memory breach for every normal fixture.

## Measurement limits and stress RED

Object census reports actual known DTO, distinct String, UTF16-unit and list-entry counts
from the materialized domain graphs (objects.csv). It excludes untraversed runtime internals,
backing capacities, JIT/stack/global objects and native allocations. It is not deep heap bytes.
ART-used/committed, native allocated heap, PSS, FD and GC are process counters, sampled with
traversal and instrumentation overhead. No forced GC or key-bearing heap dumps were created.
Per-phase maxima are observed SAMPLES, not continuous maxima or hard bounds. Values from
different timestamps must not be added as a simultaneous peak or subtracted to isolate SQLCipher.

The native Window initial size is not a capacity ceiling (§74). Known P83 core windows have
nonzero pointers during the row hold and zero pointers after product cleanup; all observed
statements/cursors close, preflight and Room return, known B2 driver end is confirmed and the
borrowed Java array is all zero. This is scoped core evidence, not a census of native key copies
or allocator buffers. Full Workspace reader native scopes are NOT wired to these hooks; old
core `closed=true` is not evidence that a concurrent FULL reader is closed.

Stress without interaction: early full-view timeout and the precise repeat showing
`LOCKED / B0-IDLE-EXPIRED` are ANDROID-RED for the full-view/historical-capacity scenario.
Core overlap/cleanup phases before that remain observed, but the whole invocation is RED.
The full 129k-event history and stress editor are NOT proven retrievable on this path.
No session policy is extended and no history is discarded to get GREEN.

A separate `history_touch` attempt initially failed before any touch because an Accessibility
root was null: HARNESS-ABBRUCH, despite its raw FAIL. The corrected probe uses bounds of the
focused own Activity, injects real DOWN/UP touch events every ~8s, and keeps the unchanged
normal interaction policy. It observes 11 such touches but STILL times out after the 90s
full-view wait. That is another ANDROID-RED; it is not hidden by the idle explanation.
During that full-reader wait the process native-heap sample reaches **44,969,680 bytes**,
PSS **185,524 KiB**, ART-used **17,426,080 bytes**, FD **140** (see phase-maxima.json).
These separate sample maxima are NOT component sizes or a combined native+ART allocation sum.
The Full DB/Window end after those timeouts is not proven by the core-only diagnostics.

## Targeted revoke classification

Actual old Compose and a real worker/pending candidate overlap in each targeted scenario.
Worker lock occurs inside the open native row scope. CORE task-create and DOCUMENT import
use the real repositories' synchronous admission/queue; worker execution is deliberately held.
The document input is invalid before reserve/source, so it is not a successful import proof.
UNKNOWN is explicitly injected document completion metadata, not a real native commit failure.
Generation change uses real lock plus guarded synthetic reauthentication; action change uses
the existing ViewModel route. Lost-Main explicitly discards the held task and locks; it does
NOT prove automatic lost-task detection, accounting release or physical GC. None publishes a
partial/late core. Shared writer epoch behavior and normal session authority remain intact.

## Remaining acceptance blockers (#33/#34)

1. No single outstanding budget lifecycle joins old Compose/snapshots, pending results, new
   slot, materialization and full workspace/editor. Reader-local receipts alone are insufficient.
   Three live graphs are proven; no global candidate/old-frame backlog upper bound is proven.
2. Native Window growth/pool/cipher buffers, ART backing capacities and rendering/frame
   retirement have no defensible complete upper bound/reserve in this accounting. Unknown is
   BLOCKER, never zero. No arbitrary reserve is invented or budget raised.
3. Full-view eager TaskBoard history has no joint admission; stress history cannot be delivered
   within the tested 90s even with actual interaction. Full-view capacity, native end and editor
   memory at that scale remain unaccepted. Required functionality is not switched off.
4. Editor/person detail and noncompact Compose/frame/snapshot old-generation references are
   not completely inventoried by the compact-payload scanner. Task editor draw is a bounded
   observation, not complete form/layout/frame byte calibration; person editor is NOT EXECUTED.
5. No continuous peak/deep ART/native census or hard joint 8+8 bound follows from process
   samples. Joint acceptance remains NOT PROVEN/BLOCKER even where scenario assertions pass.

No second owner, snapshot registry, keyowner, mutation epoch or budget is introduced as a
workaround. #32 pre-release native cleanup-fault/allocator visibility, #39 secure keyguard/
DeviceCredential, #36 editor ABA and RED-4 remain separate gates. Normal product reuse and
UncachedWorkspaceMutations.ramReuseEnabled remain false. No merge/release/force-push.

## Reproduction and installation protection

One terminal blank line in device output and trailing whitespace in a build warning were
normalized for `git diff --check`; native runner outputs remain unedited.
Full logs, case classification, artifact hashes, manifests and process/reference CSVs live
in this directory. `python3 docs/p11-p83/summarize.py` recomputes the inventories. Main-series
reports used build-06; separate touch probes and editor observation have separate test APK
builds/artifact records. Target runtime code is identical across these final runner revisions.
Only test controls differ. The original §82 artifact archive remains unchanged.

Build P77 with `-PpagTestBuildType=p77runtime`, assemble both APKs, run
`:app:verifyP77RuntimeIsolation` and the full `:app:testP77runtimeUnitTest`.
Before any device mutation, freshly check exact device, APK IDs, manifest/runner and signer
using the reviewable device-control.py. Execute only the explicitly selected Custom runner,
e.g. `python3 docs/p11-p83/run-case.py review large replace`; no connected all-suite command.
Do not install on another device/package merely because this example exists.

All three protected installations are compared before/after and left installed unchanged:
org.pag.prototype.a.p76demo, org.pag.prototype.a, org.pag.perf.p11probe. Only own P77 target/test
packages are updated; no data clear, global cleanup, credential/keyguard or protected-app action.
Normal product gates remain false. This WIP must stop for independent GitHub review.
