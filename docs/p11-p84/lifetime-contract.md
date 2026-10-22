# §84 one outstanding ACTIVE ownership chain

The owner remains the one ProductActiveCoreLoader in the existing ProductRuntime, handing
one value to the one ProductViewModel slot. OutstandingCoreRead is its private bounded state,
not another snapshot registry, keyowner, mutation epoch, session authority or budget ledger.
It has one current Read, one payload handoff cell, one worker-body cell and one Main-body cell.
Those bodies transfer ownership; they do not store a second payload graph after handoff.

## Admission and charges

The limits remain 8,388,608 retained and 8,388,608 transition bytes. CoreReadAdmission still
admits the people/task/envelope components together in the actual Room transaction. Before
any materializer, the new ownership read validates that exact immutable reservation/receipt.
Normal ProductRuntime always supplies this read to ProductActiveCoreReader.readForTicket.
Standalone benchmark readers are not substituted for this path.

The joint policy is deliberately stricter than fitting two reader-local plans:

- Admission reserves the **entire existing 8 MiB transition envelope**, exclusively, before
  READY acquisition, original ticket, worker queue or keylease. No per-worker extra allowance.
- The local retained charge is reserved before payload materialization, within the existing
  retained limit. The local transition value remains recorded; the unused envelope remainder
  stays booked for unresolved staging/native/presentation costs. It is not granted again.
- A current record, including a retained or presentation-uncertain record, refuses another
  read BEFORE any new ticket/namespace/SQL/keyscope. The bool false is existing admission
  refusal/backpressure, not a successful empty payload and not an automatic retry queue.
- Once a value may have escaped to a consumer, the loader cannot prove that all consumer/
  Compose/frame references ended. It retains the record even after authority close, VM clear,
  observed Compose disposal, session lock, new READY generation or action change.
- A non-exposed candidate can retire after its explicit references/bodies were cleared,
  original authority revoked, worker and any posted Main shell acknowledged, and known DB/key
  scopes ended. This is source-level ownership retirement, not GC or physical RAM wiping.
- A genuinely lost/unacknowledged Main shell continues to block. Unknown queue acceptance
  also blocks until the shell acknowledges. Only actual RejectedExecutionException has the
  executor's nonacceptance contract. Arbitrary execute() exceptions are not assumed unqueued.

Holding the whole envelope is an **engineering exclusion reservation**, not a measured or
certified SQLCipher/Compose reserve. The remainder is NOT asserted to bound all native or
frame allocation. `nativeFrameCalibration=UNCONFIRMED` stays explicit. Without a defensible
upper bound/retirement producer, the release gate is NO-GO. Nothing unknown is called zero.

| Phase | Strong ownership | Retained reservation | Transition reservation / release condition |
|---|---|---:|---|
| Reader admitted | One worker shell/body; no core yet | 0 until metadata | Entire 8 MiB; blocks competing admission |
| Worker materializing | Real Room/key scopes + bounded partial builders | Exact local R, reserved before builders | Entire 8 MiB; local T separately recorded |
| Candidate complete | Worker stack then single handoff cell | Same planned R | Entire 8 MiB; no third producer allowed |
| Main pending | One shell → clearable body/payload cells | Same planned R | Entire 8 MiB; second/third request refused |
| Main delivered | Cell drained into callback, once | Same R | Entire 8 MiB; callback frame still outstanding |
| Retained | One existing VM slot; loader holds metadata only | Same R | Entire 8 MiB remains conservatively booked |
| Compose transition / revoked exposed value | VM may be empty, old consumer/Compose/frame references unconfirmed | 0 as retained ownership ends | Entire 8 MiB; NOT released by Dispose/GC/lock |
| Canceled unpublished / denied | Payload AND function cells cleared, source scopes finish | 0 | Held until worker + posted Main acknowledgements and known scope ends |
| Retired (unpublished only) | No producer-owned payload or heavy callback capture in old shells | 0 | 0 for this known ownership chain; old duplicate cannot affect new identity |
| Cleanup failed/unconfirmed | No publication/retry; uncertain resources quarantined | 0 | Entire 8 MiB; no successful completion fabricated |

The reservation columns are NOT measured heap sizes. R and T represent conservative obligations,
not disjoint allocator byte samples that may be added to PSS. A normal 200/1000 local plan is
still about R 1,655,712 / T 6,599,040; the joint owner books R and T-envelope 8,388,608. The
rough 1,789,568 unused local-T remainder stays held. No calibration claim follows from it.

## Dispatch, cancellation and identity

WorkerHandoff/MainHandoff contain only their Read. Their function cells hold callbacks/context
until execution or cancellation, then are atomically drained/cleared. Cancellation also clears
the payload cell. The shells do NOT immutably capture a result, old callback or StorageContext.
A canceled shell is deliberately kept booked until its execution acknowledgement; it may
physically remain in the queue and is not assumed collected. Therefore neither payloads NOR
heavy callback captures can form an unbounded producer queue through repeated cancellation.
Concurrent duplicate shells have independent one-shot dispatch CAS guards: an empty duplicate
cannot acknowledge the still-running original worker/Main body. Main callback delivery remains
one-shot too. Acknowledgement comes after the function body returns; it is not merely dequeue.

Exactly one ProductActiveCoreLifetime subscribes to the ORIGINAL readticket from admission
through handoff. No extra ticket listener, readticket port, registry, epoch or replacement
keyowner is added. Targeted close still revokes only that ticket. Budget retirement checks
`current === this`; old shells/cleanup cannot clear a newer outstanding read. AuthorizeHit,
fresh READY acquisition, central publication and existing writer/pending fences are unchanged.

The original local Native/Room/B2/SessionDbKeyOwner cleanup contracts remain. ActionDatabaseAccess
reports known Room-close return, coroutine cancellation/join and driver.resourcesClosed only
after they succeed. The key marker comes from the real withDbKey finally. Missing markers
remain false. Unexpected scoped exceptions or suppressed cleanup failures make that read
sticky unconfirmed: DB closed alone cannot certify statement/cursor cleanup. The existing
reset→close/primary/suppressed behavior is not replaced and there is no diagnostic rescue.

## Presentation and full views

ActiveWorkspace records observation of the ORIGINAL lifetime and reports actual Compose
DisposableEffect disposal separately. No frame-retirement acknowledgement is invented.
Even a value delivered and rejected by a consumer may have escaped; close alone cannot free
its reservation. This is intentionally conservative, not a secretly re-enabled RAM gate.

Consequences are material and remain release blockers:

- Initial normal reads and isolated authorized hits work. A new displayed-core→displayed-core
  read is DEFERRED after the first exposed lifetime; no production retirement producer exists.
  This affects ordinary gate-off fresh navigation as well. It is NOT claimed as full normal
  navigation acceptance and NOT a switch to a cache-free release goal.
- The necessary next lifetime fix is a trustworthy presentation/consumer retirement contract
  (or bounded presentation reference/allocator upper bounds) on the SAME lifetime, followed
  by calibrated reserve accounting. VM clear, one OnDraw, Dispose or a new generation alone
  cannot be that producer. Admission must stay closed until then, without raising the limits.
- Clear-before-full-view stays. fullViewStarting fences subsequent compact admission while
  full workspace/editor cost/retirement is unconfirmed. The existing full reader/editor is
  not disabled; no automatic full-view fallback is introduced on budget refusal.
- Full Workspace/TaskBoard eager history and old full-view/Compose/frame overlap are still
  unbounded by this ACTIVE-only lifetime. §85 must connect bounded on-demand history/detail
  to this boundary rather than quietly resetting it or adding another owner.
- The bound proven here is per existing Runtime/Loader ownership chain. Retired activity vs
  newly constructed Runtime/frame overlap is not globally certified. No singleton/second
  registry is added to pretend otherwise. #39 and the overall memory gate remain open.
