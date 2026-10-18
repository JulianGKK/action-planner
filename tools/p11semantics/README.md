# Isolated semantics investigation

Explicit test builds only. No product cache, dependency change, credentials or product installation.
Raw outputs and APKs belong under ignored `.prototype-cache/`; never commit them.
Use the existing signing key. Do not generate a replacement key.

## Build and run minimal ablations

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  -I tools/p11semantics/probe.init.gradle --offline
python3 tools/p11semantics/run.py --adb "$ADB" --serial "$ANDROID_SERIAL" \
  --aapt "$AAPT" --install --runs 50
```

Set `ADB`, `ANDROID_SERIAL` and `AAPT` locally; do not put their values in Git.
The runner verifies APK manifests and API 33 before instrumentation. The minimal target
uses `android.app.Application`, has no product Activity/renderer, and no permissions.
Only the non-exported synthetic Activity runs. The normal application build is unaffected.

All variants use 32 synthetic rows, stable unique item keys and one content type.
Every trial renders the initial list, scrolls to its end, invalidates state, scrolls back,
and performs the optional navigation. Fixed font factors are 1, 2 and 3.5.

| Variant | Content / navigation | Accessibility automation |
|---|---|---|
| A | Text rows | Concurrent fresh tree queries |
| B | Material3 OutlinedButton rows | Same queries |
| C | B + explicit onClick label + child heading | Same queries |
| D | C + list → detail → form → detail → list | Same queries; model-driven navigation |
| E | Same navigation as D | Never accesses Instrumentation.uiAutomation |
| F | Same navigation as D | Fresh AccessibilityNodeInfo click actions + concurrent queries |
| G | D + stateDescription reading Snapshot state inside semantics | Concurrent queries; diagnostic hypothesis |
| H | G with the description value captured during composition | Concurrent queries; matched control |

G/H additionally support `--column` to compare eager Column against LazyColumn.
`--reuse` replaces content in the same Activity between trials; default finishes and
awaits Activity destruction each trial. It does not retain accessibility nodes.
`--artifacts DIR` selects frozen `target/` and `test/` APK metadata/APKs to avoid
silently changing binaries while another test build overwrites Gradle outputs.

All model mutations run on the main thread. A composition + draw version fence precedes
subsequent steps; a successful method call alone is not a rendered-frame assertion.
The fresh tree query owns and releases every acquired node. Nodes are not cached across
navigation. Concurrent queries deliberately exercise asynchronous semantics invalidation.
API 33 deprecates recycle(); it is still called to make ownership explicit.
Condition polling has a bounded timeout; it is not a product delay or crash workaround.

Synchronous test failures are transported back to instrumentation and reported separately.
Asynchronous application errors are reported with fixed phase/class/stack locations and
then delegated to the original Android uncaught handler. They are not suppressed.
After a process crash the host starts a new independent trial until the requested number
of attempts is reached. Check crash AND harness-failure counts; partial trials are never PASS.
No exception message, node text, credentials, serial number or local path is emitted by the
probe. Local command/build output can still contain environment information and stays ignored.

## Identical actual ProductScreen control instrumentation

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  -I tools/p11assessment/probe.init.gradle \
  -I tools/p11semantics/actual.init.gradle --offline
python3 tools/p11semantics/actual.py --adb "$ADB" --serial "$ANDROID_SERIAL" \
  --aapt "$AAPT" --target-apk "$ISOLATED_TARGET_APK" \
  --test-apk "$ISOLATED_TEST_APK" --runs 50 --font 2 \
  --automation true --output .prototype-cache/semantics-actual-current
```

Only package `org.pag.perf.p11probe` and its test package are accepted. Pass the exact same
new test APK to the old isolated target and current isolated target. The runner reads
library version markers from each target APK. `--automation false` never activates the
UIAutomation connection. `--font alternate --reuse` reproduces setContent replacements
with alternating 1/2 font factors; omit reuse for a fresh-Activity control.

This runner uses real ProductScreen with synthetic Ux3c repository ports and drives public
model commands. It is an ablation, **not the complete Ux3c regression**: its text entry,
status and save operations do not reproduce every accessibility/IME/button click.
The original Ux3cTasksProbe is also registered in this test manifest for separate full runs.
Do not substitute model-driven success for a failing full UI regression.

## Deterministic actual Lazy-footer reproduction

`actual.py --runner SemanticsProductRepro --font 2 --automation false` uses 67 synthetic
ACTIVE tasks (the existing three plus 64 cloned synthetic open tasks with unique IDs).
An existing nullable test observation exposes only LazyListState. The runner scrolls to
compose the closed-task footer, scrolls to the top to deactivate it, then changes
closedExpanded through the real public TaskModel command. It checks lock cleanup after
success. This runner needs the current Lazy implementation and is not ABI-compatible
with the old eager-Column APK; use SemanticsActualProbe for the old/current comparison.

Run the same frozen test APK against frozen pre-fix and fixed target APKs to isolate the
one-line product behavior change. With `--automation true`, additional fresh owned-tree
queries assert the original German collapsed/expanded accessibility descriptions.
No accessibility information is removed by the fix. `--column` is only a minimal-probe
control, not a proposed product rollback.

## Full existing regressions

```sh
python3 tools/p11semantics/regressions.py --adb "$ADB" --serial "$ANDROID_SERIAL" \
  --aapt "$AAPT" --target-apk "$ISOLATED_TARGET_APK" --test-apk "$ISOLATED_TEST_APK" \
  --task-runs 3 --fonts 1 2 3.5 --output .prototype-cache/semantics-regressions
```

These are the original accessibility/IME-driven Ux3c, Ux3d and P9 flows plus the existing
large ACTIVE/PREPARATION ListStabilityProbe. Ux3c additionally accepts explicit font 3.5;
its default 1+2 path and all assertions are preserved. ListStability uses synthetic
SQLCipher fixtures in the isolated package, unlike the database-free minimal/model probes.
Failures remain failures. The host records each full run separately; it does not retry a
failed run until green. A framework exception is not an expected passing outcome here.

Results and limitations are recorded in assessment section 27. In particular, a full
Ux3c font-3.5 run must not be treated as passing merely because the targeted footer test
passes. The existing full flow has a separate reachability failure at that font size.
The host summary preserves that failure instead of silently reducing the font or fixture.

## P11 reachability follow-up (assessment section 28)

The task and P9 probes emit fixed A–J subphases and scalar `REACH` diagnostics.
`--diagnostics detailed` additionally records matched-node counts and bounds;
no node text, document content, IDs or local device identifiers are recorded.
The task driver remains unchanged: experimental alternate navigation did not pass
its complete font-3.5 flow and was removed. That case remains a blocker.

P9 now refreshes newly fetched parent NodeInfo instances before reading their
clickable/enabled/bounds metadata. Refreshing the earlier walked child is insufficient
to refresh a later cached getParent() result. `--navigation legacy` disables this
specific test correction for controls; `auto` (default) and `parents` enable it.
These switches do not change product UI, permissions, storage or model authority.

```sh
python3 tools/p11semantics/regressions.py --adb "$ADB" --serial "$ANDROID_SERIAL" \
  --aapt "$AAPT" --target-apk "$ISOLATED_TARGET_APK" --test-apk "$ISOLATED_TEST_APK" \
  --suites memory --memory-font 2 --memory-runs 10 --navigation parents \
  --output .prototype-cache/reachability-memory
```

Use the exact same frozen test APK for both frozen target APKs. `--memory-font both`
retains the original full 1→2 font/Activity path; `1` or `2` runs a complete single-font
flow. Task repeat counts and P9 repeat counts are separate. The runner preserves failures
and records PASS, APP_CRASH, UNREACHABLE, ASSERTION, TIMEOUT or HARNESS_ERROR.
A timeout/execution failure stops the series: an on-device write might still be running,
so it is never automatically overlapped or retried. P9 also asserts exactly one additional
write for the accepted addendum-save action and the complete synthetic persisted result.
Reports uses the original in-memory synthetic repository, not a product database.

`UiScrollViewportProbe` is a separate synthetic measurement (registered only in the
explicit test manifest). It creates fresh ScrollState per font/variant/trial, applies
the same padding/verticalScroll/heightIn order, and records actual scroll deltas and
viewportSize after ACTION_SCROLL_FORWARD. It does not navigate ProductScreen or load
repositories. The explicit getScrollViewportLength branch is a falsification control,
not a product fix. All 30 static cases produced the correct viewport-sized delta;
the broad wrong-content-height hypothesis was rejected. Dynamic full-flow reachability
remains outside that isolated measurement. See assessment 28 for unsuccessful task
experiments and the additional failed functions regression; no overall green result.
