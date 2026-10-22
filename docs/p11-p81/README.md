# §81 – native ACTIVE revoke and lifecycle acceptance

Base: `807fada166c3b4d2079f9efb6f50a3467603d1db`. Product reuse remains false.

## What every substantive scenario actually starts with

The isolated `org.pag.prototype.a.p77runtime` Activity constructs its actual `ProductRuntime`.
The existing package/debug-guarded synthetic authentication runs the real authorized
maintenance path and `SessionDbKeyOwner`; the runtime admits one original readticket,
executes `ProductActiveCoreReader` / Room / SQLCipher for 200 persons and 1000 tasks,
publishes to the real `ProductViewModel` core slot and `ProductScreen`, then accepts a
programmatic RAM hit to PERSONS. The runner requires actual visible `Synthetic 0` and
`32 von 200 angezeigt · weitere Einträge verfügbar`, counts 1 reader/1 ticket/1 accepted
hit, and identical core identity. No fake lifetime, fake core or second mutation owner.
This is synthetic test authentication, not DeviceCredential/CryptoObject E2E.

## Product RED → GREEN

Action change and terminal false-after-callback closed the lifetime and removed the slot
but left the original port ticket live. This was stale metadata, not a demonstrated
unauthorized successful hit: the closed lifetime already denied it. The native assertions
in `action_change-02.txt` / `callback_false-02.txt`, and
`lifetime-close-host-red.txt`, retain the actual failure.

`revokeActiveRead(ticket)` now atomically detaches only the exact ticket and always invalidates the owned original,
even if concurrent admission has just detached it. Issuer identity rejects foreign tickets;
CAS preserves a newer entry.
`ProductActiveCoreLifetime.close()` calls it. Pending, generation and mutationEpoch are
not modified by this operation. A late close can never revoke a newer/foreign identity.
The existing global session revoke is unchanged. Worker-safe CAS / duplicate cancellation,
old-lifetime versus new-ticket, unchanged epoch and still-busy writer pending are hosttested.
Transient `loader.load` was always documented to close after callback: its regression now
requires current authorization **inside** callback and a dead ticket **after** callback.
No retained writer/COMMITTED semantics were reinterpreted.

## Test controls and evidence strength

`P81RuntimeFaults` is debug-only, installed only by the exact guarded P77 harness.
Normal runtime uses its original session directly. The P77 core access wrapper delegates
READY acquisition, scope, generation, checks, key admission and publication to the original
coordinator. Its hooks can only deny or cause real revocation; they cannot grant READY.
One bounded executor gate may hold one actual product Runnable. No automatic retry.
Hooks that start a writer or wait for a worker execute outside the coordinator monitor.
The release DEX check additionally excludes `P81RuntimeFaults`.

| Scenario | Actual trigger / interpretation |
|---|---|
| lock, blocked | Real session `lock` / `storageFailure`, not OS keyguard |
| worker_lock | Real worker session lock while Main waits outside session monitor; raw atomic slot checked before Main resumes |
| idle | Actual unchanged 30s device-clock expiry, `B0-IDLE-EXPIRED` |
| worker_idle | Main held around expiry; worker checks real READY context after 30s; ticket and raw slot dead before Main release, no owner-thread exception |
| absolute | Actual unchanged 180s session boundary; 11 real injected touches <30s apart keep idle open, final `B0-ABSOLUTE-EXPIRED`; no clock substitution |
| reauth | Lock and actual synthetic authentication plus maintenance, new generation, no old slot |
| action_change | Actual ViewModel opens other COMPLETED fixture action, original ticket/lifetime dead |
| status_change | Real runtime status writer commits ACTIVE→COMPLETED |
| status_vm | Real `requestAdvance`→`confirmAdvance`→writer→ViewModel COMPLETED→Compose status; full authorized completed content may contain same names, but no compact old-core label/slot |
| core_writer | Actual runtime TASK create, held before worker; exact shared epoch +1, pending denies reads/writes, slot gone before release; actual success callback after SQLCipher transaction |
| document_writer | Actual runtime import admission, held before worker; same fence/epoch proof; INVALID empty name then returns before reserve, source must never open. No document-commit or native file-cleanup claim |
| unknown | Deliberately injected typed UNKNOWN on the same runtime port; generation denies reads/writes, no producer/native recovery claim |
| cleanup_uncertain | Final attempt isolates NOT_EXECUTED + FAILED_OR_UNCONFIRMED, denies further admission. First attempt combined UNKNOWN+failed cleanup. Metadata fault injection, not actual Room/DB/key-close failure |
| home | Real HOME intent; OS onPause/onStop observed, return onStart/onResume/focus observed, stays LOCKED |
| focus | Actual Dialog window obtains focus; real focus=false callback revokes, dismiss returns focus without restoring READY |
| recreate | Actual Activity.recreate, replacement Activity/runtime/model; original authority dead, replacement LOCKED, actual replacement Draw sees no core |
| lost_main | Actual second SQLCipher candidate held at core Main dispatch, then discarded; no slot or optimistic publication; no automatic rescheduling |
| late_generation, late_action | Same real candidate delivered twice after new READY generation / action switch; no resurrection or replacement ticket minted by old callback |
| worker_install | New real candidate installed inside real central publish; test wrapper then worker-locks **after underlying publish returned**, outside monitor, before lifetime terminal checks; atomic slot already gone before Main continues; Main replay inert |
| writer_during_hit | Actual TASK admission between fresh READY acquire and hit publish; held writer invalidates synchronously, hit denied; release reaches real writer callback |
| callback_false | Actual central callback executes, denial-only wrapper returns false; real lifetime closes, exact ticket dead, no retention. Injected interface outcome, not naturally observed SessionCoordinator false-after-callback |
| touch_navigation | Real MotionEvent DOWN/UP via UiAutomation on visible HUB Persons card; actual UI callback invokes real authorizeHit, one read/ticket and same core |
| touch_denied | Real touch after actual TASK pending; no old accepted hit. Legitimate OS input may renew idle, cache refusal is not fabricated user input |
| touch_idle | Real heading touch after 15s keeps READY past original 30s; subsequent programmatic hit at ~30.1s does not extend; locks ~30s after actual touch |
| screen_keyguard | Guarded **NOT EXECUTED**: deviceSecure=true, keyguardSecure=true, initially unlocked. No power key, SCREEN_OFF broadcast, keyguard lock or dismiss command executed |

All GREEN cases require original ticket dead, original lifetime invalid, **raw atomic slot
empty before `activeCore` getter**, and denied old authorizeHit. The runner observes an actual
subsequent Decor OnDraw with an empty slot, then actual visible Compose semantics without
old core content (completed full view is checked separately). Activity replacement has its
own real first draw assertion. No forced `invalidate`, fake OS broadcasts or UI-derived
authority. These observations distinguish synchronous authorization loss, slot removal,
Compose update and subsequent draw. They do not atomically snapshot framebuffer pixels,
prove GPU/physical wipe, or prove collection of deliberately held diagnostic Java references.
OnDraw/semantics observations never authorize a hit.

Lifecycle events from final Activity teardown are also logged. Do not count every final
onPause/onStop as an independently exercised HOME case. Only `home` explicitly requires
stop→start/resume **before** terminal teardown; `focus` requires the window callback;
`recreate` requires a replacement runtime. Reauth uses synthetic credentials only.
Activity recreation is not an OS process-death/recovery test.

## Interaction policy

MainActivity.onUserInteraction forwards actual Android input to session.userInteraction.
Legitimate input renews READY idle, including a touch whose requested cache hit is denied.
Programmatic authorizeHit/navigation does not itself call interaction or extend idle.
Expired/LOCKED input cannot restore READY. Absolute 180s takes precedence despite real
repeated touches. No session policy, deadline, global device setting or key format changed.

## Attempts and unresolved acceptance

All custom-runner reports remain, including early process aborts and RED assertions.
`attempts.csv` and `summarize.py` reproduce attempts and latest scenario states. The two
early process crashes had no assertion report; Main-test assertions were subsequently
caught and returned to the instrumentation thread, yielding the precise product REDs.
They are not retroactively GREEN or independent proven product crash diagnoses.
The intentionally failing host RED and two transition failures are also retained. One
transition failure corrected a new test's mistaken pending.isCurrent expectation (that
method is publication-only after finish); the other was the obsolete transient-ticket
expectation above. Final tests assert the stricter intended lifecycle; none were skipped.

Secure OS keyguard / ACTION_SCREEN_OFF remain BLOCKER / NOT EXECUTED. No unknown device
credential was entered and no lock configuration changed. Genuine persisted recovery
failure and native upstream Room/DB/key-close faults were not injected: direct BLOCKED,
UNKNOWN and cleanup-metadata tests do not prove those paths. #32, #33/#34 reference overlap,
#36 Editor ABA and RED-4 remain independent release gates. No product RAM activation.

## Reproduction and isolation

Build P77 with `-PpagTestBuildType=p77runtime`; then invoke the custom runner explicitly:

```
adb -s RFCT1186YRJ shell am instrument -w -r -e scenario lock org.pag.prototype.a.p77runtime.test/org.pag.prototype.P81ActiveRevokeProbe
```

Use the table's scenario keys (one scenario per invocation). `absolute` takes roughly
three real minutes. Do not substitute a zero-JUnit connected test as PASS. Every update
must first verify serial/model, protected-package metadata, exact target and test APK IDs,
manifest target and runner, build variant and installed/new signer. Only the own P77 APKs
were updated; old APKs were pulled for signer comparison. Certificate SHA256:
`9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134`.
The three protected apps remain installed, with unchanged version/install/update/signature
metadata in `protected-packages.json`. `installed-artifacts.json` records the final APKs.
No protected package was updated, removed, cleared or inspected for private app data.

Each GREEN report waits for actual Activity destruction and successful fixture.close;
`fixture_cleanup_confirmed` is mandatory in the parser. Failed attempts do not make that
claim. Final read-only inventory in the own P77 package found zero p11-fixture roots;
`fixture-roots-after.txt` is the empty raw output. No global cleanup or package uninstall.
