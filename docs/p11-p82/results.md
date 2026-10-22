# §82 native results (Samsung SM-M526BR / RFCT1186YRJ)

Synthetic P77 credential authority; actual Runtime, SessionCoordinator, shared readticket,
SessionDbKeyOwner, Room/SQLCipher 4.19.1/B2, CoreReader/Loader/Lifetime and ViewModel.
Normal product RAM gate remains false. See [scope and remaining blockers](acceptance-boundaries.md)
and [resource lifetime](resource-lifetime.md).

29 accepted Android custom-runner cases GREEN, 0 product RED; 14 early invalid harness cases
are retained and excluded from GREEN. These are **43 actual custom instrumentation invocations**,
not 43 JUnit tests. Three early raw FAIL results and eleven untrustworthy raw PASS results
remain visible in [case-results.csv](case-results.csv) and original reports.

13 strict cases preceded the final denied-END rollback cleanup correction. All 14 cases were
then executed on the final APK, plus two additional success cycles on that same APK. The
repetitions each create/destroy their own real Runtime/fixture, not a long same-Runtime soak.

| Final case | Statements closed | Cursors / Windows | Reported failure | Suppressed |
|---|---:|---:|---|---:|
| [both_after](final-both_after-runner.txt) | 1 | 1 / 1 | IllegalStateException | 1 |
| [cleanup_revoke](final-cleanup_revoke-runner.txt) | 70 | 70 / 70 | StorageAccessRevoked | 0 |
| [close_after](final-close_after-runner.txt) | 1 | 1 / 1 | IllegalStateException | 0 |
| [commit_revoke](final-commit_revoke-runner.txt) | 71 | 70 / 70 | StorageAccessRevoked | 0 |
| [driver_end](final-driver_end-runner.txt) | 70 | 70 / 70 | IllegalStateException | 0 |
| [key_end](final-key_end-runner.txt) | 70 | 70 / 70 | IllegalStateException | 0 |
| [native_getter](final-native_getter-runner.txt) | 17 | 17 / 17 | B2Exception | 0 |
| [native_step](final-native_step-runner.txt) | 17 | 17 / 16 | B2Exception | 0 |
| [reset_after](final-reset_after-runner.txt) | 1 | 1 / 1 | IllegalStateException | 0 |
| [room_end](final-room_end-runner.txt) | 70 | 70 / 70 | IllegalStateException | 0 |
| [row_and_room_end](final-row_and_room_end-runner.txt) | 66 | 66 / 66 | IllegalStateException | 1 |
| [row_failure](final-row_failure-runner.txt) | 66 | 66 / 66 | IllegalStateException | 0 |
| [row_revoke](final-row_revoke-runner.txt) | 66 | 66 / 66 | StorageAccessRevoked | 0 |
| [success](final-success-runner.txt) | 70 | 70 / 70 | none | 0 |

Every accepted case asserts all observed cursor close states, Window pointers zero,
preflight DB end, Room close return, known driver handles closed and driver key copy zero,
borrowed SessionDbKeyOwner array wiped, original ticket revoked, no partial VM core, no
post-fault hit, no epoch change and no second ticket/read. Success checks global 200/1000
counts, 32-item pages, hasMore and one actual authorized hit before lock. An old-context
lease attempt after revoke fails without entering its consumer.

Native failures:

- `native_step`: integer overflow reaches SQLCipher.nativeExecuteForCursorWindow inside
  the real Room transaction. Full pre-sanitization native stack is in its runner report.
- `native_getter`: invalid column reaches CursorWindow.nativeGetLong. This is a genuine
  native getter error, not a wrapper simply throwing a made-up exception.
- `row_revoke`, `cleanup_revoke`, `commit_revoke`: real SessionCoordinator lock from
  the active Room worker / cleanup / before-END hook. `commit_revoke` observes the actual
  denied-END rollback and retains StorageAccessRevoked with all known handles released.
- Other failures are injected rowloop/post-reset/post-close/Room-end/driver-end/keyconsume
  errors. They are deliberately **not** classified as genuine native close hardware faults.
  Combined error cases assert the original throwable and the suppressed secondary identity.

The public vendor close alone still has its §72 limitation; the product wrapper resets first.
No B2 error-sanitization policy, cipher parameter, SQL schema, key format, session limit or budget changed.
Preflight PRAGMA cursors have scoped close and observed DB end; their individual Window
pointers were not reflected. The process/resource observations cannot prove every upstream
reference, allocator buffer, native key copy or complete physical cleanup.

[Raw observations](native-observations.csv) include FD, NativeHeap, PSS, ART GC and cycle_ns.
These are process snapshots with instrumentation overhead. cycle_ns includes checks, read,
optional hit and lock; **not Content-OnDraw**, not a performance optimization or release p95.
No FD/PSS tolerance is a leak-free guarantee. No joint 8+8-MiB peak acceptance is claimed.

The 14 invalid early runs are explained in acceptance-boundaries.md; no assertions were
removed to obtain GREEN. Early compilation failures are archived separately from test results.
Final fixture inventory is empty. The three protected installations remain present and their
version/install/update/signature metadata match the initial snapshot. No package data was
cleared and no protected app was targeted. Own P77 test packages remain available for review.

## Reproduce (only after fresh device/package/signer checks)

Build the P77 APK pair with `-PpagTestBuildType=p77runtime`, including
`:app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:verifyP77RuntimeIsolation`.
Use only the identity-verified own P77 target/test APKs. Select exactly this runner:

```sh
adb -s RFCT1186YRJ shell am instrument -w -r -e scenario native_step   org.pag.prototype.a.p77runtime.test/org.pag.prototype.P82RoomCleanupProbe
```

Other supported scenarios are the final table entries. Do not run an unselected connected
suite. Never install/update/remove/clear org.pag.prototype.a.p76demo, org.pag.prototype.a
or org.pag.perf.p11probe. Recompute archived case counts with `python3 docs/p11-p82/summarize.py`.
No automatic permission for secure keyguard/device-credential manipulation follows from this.

**Recommendation: NO-GO for general RAM gate activation.** #32 remains partially open for
actual cleanup failures before release and incomplete native visibility. §83 must validate
joint retained/candidate/main/Compose/editor/frame/native overlap without raising 8+8 MiB.
#39 secure Keyguard/credential, #36 Editor ABA and RED-4 remain separate release blockers.
