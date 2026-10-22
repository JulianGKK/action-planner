# §82 resource lifetime and evidence boundaries

The P77 package supplies synthetic credentials to the guarded ProductRuntime constructor.
It does not replace SessionCoordinator, its Maintenance pass, SessionDbKeyOwner, the shared
mutation port, reader, Room database or ViewModel owner. Normal/release authentication and
RAM gates remain unchanged.

1. Owner acquires original READY StorageContext and shared-port ActiveReadTicket. No key is
   in the ticket. Ticket liveness is atomic; epoch/pending changes remain owner-thread-only.
2. Worker checks original context/ticket/lane, enters namespace lane, calls the actual
   SessionDbKeyOwner.withDbKey. Its borrowed ByteArray is a copy, wiped by its finally.
3. ActionDatabaseAccess inventories the store and opens a read-only SQLCipher preflight DB.
   Its native PRAGMA cursors use scoped close. That DB closes before Room opens.
4. B2Driver copies the borrowed key. Room opens SQLCipher connections through this driver.
   Room schema opening precedes the explicit withReadTransaction and ActiveCoreStore.
5. Compact stores use Room usePrepared. B2 wraps the actual SQLCipherStatement; step/getters
   enforce the original context. Query cursors own native CursorWindows. Statement cleanup
   tries reset then close and preserves both failures. Reset releases the vendor cursor.
6. Room transaction end precedes ActionDatabaseAccess return. Cleanup order is lifecycle
   closing, Room.close, coroutine cancelAndJoin, B2Driver.close. Nested standard use scopes
   preserve a primary work failure and suppress later independent cleanup failures.
7. Driver.close attempts all known connections and wipes its key copy even on failure.
   A close failure is still a failure; the closing bit alone does not confirm resources.
8. withDbKey postcheck then finally wipe complete before the reader returns a candidate.
   Any propagated failure prevents Value/publication; Runtime storageFailure revokes the
   original ticket/keyowner, or an already revoked session causes access rejection.
9. Only successful candidates reach original-ticket owner-thread checks, central publication,
   ProductActiveCoreLifetime and the single ViewModel slot. Failure diagnostics hold no core.

Distinguish Java references, borrowed bytes, statement wrapper/vendor statement, querycursor,
CursorWindow native pointer, SQLCipher DB handle and Room/coroutine scope. A zero observed
Window pointer and !DB.isOpen are specific observations, not complete native heap erasure.
The ticket is metadata, not a DB lease. Candidates and retained VM payloads are separate
lifetimes. No successful close can prove all historical Java/Compose/frame references gone.

## Fault seam

Only the existing guarded P77 authority can install CoreReadTestHooks. P82CleanupFaults is
in data/src/debug and excluded from release DEX. It bounds cursor/window references at 512,
records one borrowed lease reference and one failure and drops diagnostics on verify.
It never reports key bytes, SQL row values, paths or payloads. These temporary diagnostic
references are part of test overhead, not a second cache or a native resource rescue.

Native SQL integer overflow is requested inside the real Room read transaction. Invalid getter
index reaches the real SQLCipher getter. Rowloop exceptions and exceptions AFTER actual
reset/close/Room close/driver close and INSIDE key scope are deliberately injected exceptions,
not genuine OS close failures. Actual native close failures before release, transaction-end
hardware faults and key-wipe failure remain unproven. No diagnostic rescue counts as green.

## §83 handoff

200 people / 1000 tasks, one retained core and next candidate remain the fixture. Observe
retained slot, worker candidate, queued main, Compose/editor/frame references separately
from native Window/connection/key scopes. FD snapshots are raw process observations only.
Engineering charges remain 8 MiB retained plus 8 MiB transition. No full combined native/
Compose/editor overlap acceptance or physical heap erasure is claimed here.
