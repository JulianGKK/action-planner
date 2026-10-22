# §80 – event-correlated compact ACTIVE content draw

Base: `1ca4d06b6678cd2710fce358e684012fca204d4c`. See assessment §80 for results and release limits.

## Measurement definition

The P77-only `P80ContentFrames` collector holds one metadata-only navigation request.
`begin(area)` runs on Main immediately before the real `model.activeArea(area)` call.
The request has a monotonic navigation ID. Only the corresponding compact area's new
Compose subtree captures it, once, through `remember(observer, area)` inside the existing
area key. The observer receives composition entry, successful Compose `SideEffect`,
nonempty attached global layout, draw start, and `drawContent()` return. No unrelated
Decor draw can complete it. No committed/layout/draw sequence within five seconds fails
the runner. A new request is forbidden until the old frame and content check are consumed.

The endpoint is **Compose content draw recording completed**, not GPU/frame presentation,
SurfaceFlinger latch, physical scanout, or pixels proven on a display. This is programmatic
navigation in the real product Compose path under synthetic P77 authority, not touch input
or DeviceCredential authentication. Composition entry→SideEffect includes subtree work,
not a tracing-accurate duration of every Compose operation. Commit→global-layout completion
includes layout/scheduling; no separate exact layout-start timestamp is claimed.

Accessibility is used only after the correlated draw has completed. The original strict
expected texts, status counts, first item, hasMore labels and exactly one heading remain.
No accessibility IPC, sleep, scroll action or artificial invalidation is in the timed interval.
`ACTION_SHOW_ON_SCREEN`, node refresh and `decorView.invalidate()` were removed entirely.
The previous target content is verified before the next navigation begins, with an initial
HUB verification. No next navigation is dispatched until the target content is visible and
its core object/area still match. There is no UI-derived security authority.

## Raw artifacts and recalculation

- `p77-runtime-compose-runner-01.txt`: first exploratory GREEN, earlier schema without composition entry, no new idle check. Kept intact; not combined with final metrics.
- `p77-runtime-compose-runner-final.txt`: final complete custom-runner report, including 80 frame/content rows, 80 hit times, counters and memory samples; idle expiry asserted.
- `frames.csv`: final raw absolute monotonic nanosecond timestamps and separate durations.
- `summary.csv`: all phase metrics, per area count/median/nearest-rank-p95/min/max/failures.
- `summarize.py`: deterministic stdlib-only parser; fails for partial/failed reports, missing rows or invalid phase order. Run `python3 docs/p11-p80/summarize.py`.
- `validation.txt`: host counts, build results and protected-package metadata comparison.

No warmup observations are discarded. The first PERSONS transition is included (104.105 ms).
Each final area has 20 observations and zero failed attempts. Quantiles are exploratory,
not a release-tail confidence bound. Semantics verification runs outside the measured interval
but its allocations/GC and warming can influence subsequent transitions; no zero-overhead
or isolated GPU benchmark claim is made.

## Commands actually used

```
./gradlew :app:testDebugUnitTest --tests '*ActiveCoreOwnerTest' --tests '*ProductEntityListTest' --tests '*ProductViewModelTest' --offline
./gradlew -PpagTestBuildType=p77runtime :app:testP77runtimeUnitTest --tests '*P80ContentFramesTest' :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:verifyP77RuntimeIsolation --offline
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :app:assembleRelease :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest :app:verifyReleaseVariantSafety --offline
adb -s RFCT1186YRJ shell am instrument -w -r org.pag.prototype.a.p77runtime.test/org.pag.prototype.P77RuntimeE2EProbe
```

P77 isolation is a separate invocation with its explicit AGP test build type: the default
debug test graph does not contain `processP77runtimeAndroidTestManifest`. Both graphs passed;
no absent task or zero-JUnit execution is reported as Android PASS. The custom runner's
explicit `P77 PASS` and all assertions determine Android GREEN.
