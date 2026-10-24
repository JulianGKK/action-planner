#!/usr/bin/env bash
# Reproduktion auf dem freigegebenen Gerät; neue Rohbelege in eigenem Unterordner,
# historische Läufe/APK-Zuordnungen dieses WIP nicht überschreiben.
set -euo pipefail
export P100_EVIDENCE_OUT=docs/p11-p100/reproduction
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
  --tests org.pag.data.prototype.b2.B2StatementCleanupTest \
  --tests org.pag.data.product.P11DiagnosticCleanupTest \
  --tests org.pag.data.product.P11FixtureRootTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
  --tests org.pag.data.product.CoreReadAdmissionBudgetTest \
  :app:assembleP77runtime :app:assembleP77runtimeAndroidTest \
  :app:verifyP77RuntimeIsolation --offline --console=plain
python3 docs/p11-p100/run-device.py install
python3 docs/p11-p100/run-device.py run repeat_boundary repeat-boundary-02
python3 docs/p11-p100/run-device.py run lock lock-02
python3 docs/p11-p100/run-device.py run cancel cancel-02
python3 docs/p11-p100/run-device.py run sql_error sql-error-02
python3 docs/p11-p100/run-device.py run cleanup_unknown cleanup-unknown-02
python3 docs/p11-p100/run-device.py run budget_refused budget-refused-01
python3 docs/p11-p100/run-device.py after
python3 docs/p11-p100/summarize.py
git diff --check
