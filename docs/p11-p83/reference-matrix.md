# §83 reference inventory and budget boundary

Base bffe9951cff11bbca9f610cd2f92a72149716a82. Normal product reuse remains false.
Limits remain 8,388,608 retained bytes and 8,388,608 transition bytes, shared across the
whole relevant operation. A successful reader-local receipt is not a joint memory grant.

| Group | Owner / start → end | Simultaneous bound | Assignment and observation |
|---|---|---|---|
| A old retained core | ProductViewModel.CoreSlot, accepted publication → synchronous clear/revoke | One VM slot; old external references not bounded by this | Original reader retained charge; VM identity and actual Compose roots inspected |
| B worker candidate | ActiveCoreStore / loader worker, metadata admission → main delivery/drop | One per read; namespace serializes DB work, not completed candidates | Existing new-read transition charge; final Value weak identity, no diagnostic strong core owner |
| C queued Main Runnable | Main executor, read completion → execution/discard/process death | No total backlog bound demonstrated; obsolete closures still capture Value | Transition; reflection on actual held loader Runnable; separate diagnostic Gate only defers one existing task |
| D new VM CoreSlot | ProductViewModel, lifetime.publish → clear/revoke | One slot, never two | New retained; old Compose references stay transition until release proved |
| E old Compose state/slots | WrappedComposition.original, composition consuming old payload → recomposition/disposal, snapshot retirement | No multi-generation bound demonstrated | Transition; actual runtime slot graph strong paths, not weak-ref/GC inference |
| F new Compose | Same single composition, recomposition → disposal | Core shared by identity; strings/derived UI models additional | Retained presentation overhead plus transition; root paths and counts, exact allocation UNKNOWN |
| G layout/draw/frames | Compose UI/RenderThread/OS, layout/draw → frame retirement | Not established for all pipeline stages | Transition, BLOCKER; existing P80 draw completion is not physical retirement or size |
| H editor/full view | ProductViewModel.workspace, TaskModel.input/selected, person editor; explicit full view/detail → clear/return/revoke | VM removes compact slot first; full Workspace/TaskBoard history is not jointly budgeted | Transition/then retained as appropriate; real full-view/editor path tested; list counts and process samples, no full heap upper bound |
| I statements/cursor/window | Room usePrepared/B2, prepare/step → B2StatementCleanup.reset+close | Bounded observed per read, not allocator capacity | Transition, known cursor/window pointers separately; dynamic Window growth means initial size is not max; allocator bytes UNKNOWN/BLOCKER |
| J Room/DB connections | ActionDatabaseAccess preflight then Room/B2Driver, open → close | Namespace serializes core DB access; native internal pool allocations not fully inventoried | Transition; preflight close, Room return, B2 known handles observed; complete native allocation reserve not established |
| K keylease/driver copy | SessionDbKeyOwner.withDbKey + B2Driver, lease/copy → finally/driver.close | Borrowed 32 bytes plus B2 32-byte copy per known read; session owner separate | Transition objects/arrays, conservative headers required; borrowed wipe and known driver end; native copies UNKNOWN |
| L temporaries | Stores, aggregate queries, UTF8 blobs/decoded strings/arrays, row builders/sorts/page wrappers → scope/GC | Payload pages <=32 people and <=32 tasks; all functions; histories excluded from compact core | Existing store retained/transition engineering factors (4/16), exact copies/liveness not certified ART sizes |
| M diagnostics | Instrumentation traversal, P83 native object references, P81 Gate, scalar samples; enabled probe → finish/release | Capped 512 observed cursors/windows; traversal 150,000 objects; one held task | Measurement overhead, explicitly outside product graphs; no core registry, no key bytes/row values logged; samples include this overhead |

The P83 traversal starts ONLY at the actual AndroidComposeView `wrapped_composition_tag`
WrappedComposition.original (known pinned Compose implementation), or the actual pending
ProductActiveCoreLoader Runnable. It follows runtime fields/object arrays to payload identities,
stops at models/observers/session/key graphs, and returns only IDs/class-field paths. It can
prove a particular strong path exists at a timestamp. It cannot prove absent graphs have no
other roots. It makes temporary traversal references, cleared on return. No forced GC or heap
dump is used. The materialized DTO/string/list census counts known domain/list edges only;
no exact ART bytes or backing capacities are inferred from counts.

## Charge interpretation

ActiveCoreStore admits compact people + compact tasks + envelope ONCE before either payload
materializer. That admission seals and closes its transaction-local ActiveWorkspaceBudget.
The immutable retained/transition receipts cross cleanup and publication, not the ledger.
ViewModel validates those receipts; it does not add extant Compose, queued candidates, full
workspace/editor or native allocations to a single outstanding reservation. Its clear-before-read
and clear-before-full-view rules prevent two VM slots; they do not physically release E/G.

An old core still reachable from Compose must conservatively stay charged when a new reader
materializes. Once a new VM graph is retained, old Compose/pending graphs need transition
coverage. There is no basis for releasing that charge merely because the old slot is null.
Reader transition allowances are engineering allowances, not measured native/Compose reserves.
They cannot be re-labelled as a verified reserve for unknown allocators. Unknown costs stay
BLOCKER. No additional per-reader budgets, second owner/registry, gate activation or increase
of either hard limit is implemented here.
