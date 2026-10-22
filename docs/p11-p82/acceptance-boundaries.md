# §82 acceptance scope

Base: `f6208d3fab66e2540bcba3c16412345f92bc4cb2`, branch
`p11-performance-optimization`. Latest Issue #32 review describes §74 Window growth,
not a hard 65536-byte capacity or mandatory startPosition change. §81 reviews in
#37/#39/#44 accept only bounded WIP and explicitly leave native upstream cleanup open.

This slice keeps the existing reader/store/budget/session/writer authorities. The
normal product gates remain false. P77 uses the existing exact-package, debuggable,
pre-construction guarded synthetic credential composition. No DeviceCredential or
OS-Keyguard security approval follows from it.

Each accepted case must prove its intended fault point was reached, not merely that
some error happened. A failed case stays in the archive; repeating it never erases
its prior result. An injected post-close exception means a real resource close
returned before an artificial failure. It is not a genuine native close failure.

The initial `first-*` series predates strict reachability assertions. Its apparent
PASS records cannot serve as acceptance of the named fault: unexpected early errors
also satisfied the initial generic rejection checks. The success timeout and failed
primary-row assertion exposed this problem. The entire first series is retained as
HARNESS-ABBRUCH / invalid named-fault evidence, including its raw PASS/FAIL strings.
It must not be added to accepted ANDROID-GREEN counts.

## Remaining native boundaries

- Actual reset failure BEFORE cursor release, native close failure before release,
  transaction-end failure, allocation/OOM, connection-close hardware failure and
  failure of physical key wiping are not safely reproduced by post-close hooks.
- B2's established error sanitization remains in place. Debug diagnostics can
  observe an original vendor exception before mapping; no product exception is
  claimed to carry complete vendor internals through every sanitizer.
- Native pointer zero, closed cursor, !DB.isOpen and wiped borrowed/driver bytearrays
  are independent observations, not a complete heap, native-buffer or key-copy census.
- FD/PSS/NativeHeap/ART GC snapshots are process-wide raw values. No arbitrary
  tolerance or before/after delta is a leak-free acceptance criterion.
- Compose/editor/frame overlap and exact native allocation charging belong to §83.
  Retained/transition engineering limits remain exactly 8+8 MiB.
- #39 secure Keyguard/DeviceCredential, #36 Editor ABA and RED-4 rootless WRITING
  remain separate blockers. No root reconstruction, job deletion or session extension.

No protected package is an instrumentation target. Before each install the local
control verifies Samsung RFCT1186YRJ, exact APK package/runner, installed/new signer
and protected package version/install/update/signature metadata. Only own P77 target
and test APKs may be updated; no package data is cleared or globally cleaned.

## Harness diagnosis

`diagnostic-success-runner.txt` localizes the early failure to Room journal-mode
configuration, before the target transaction. SQLCipher 4.19.1's compiled
SQLCipherConnection.prepare returns JournalModeStatement for journal-mode setting,
not a SQLCipherStatement. The first harness incorrectly asserted the latter for all
statements. The corrected observer reads the known JournalModeStatement.delegate
for cursor diagnostics only; operations still run on the original vendor wrapper.
The original SQLCipherStatement reset/close and journal-mode behavior are unchanged.
The diagnostic run is a HARNESS-ABBRUCH, not an Android product regression.

The §81 targeted lifetime revoke regressions remain in the full passing host suite.
A genuine native cleanup failure after a newer ticket has already replaced the old read
was not separately executed here. No claim of native acceptance for that combination,
or authority to revoke a newer ticket from an old lifetime, follows from these cases.
