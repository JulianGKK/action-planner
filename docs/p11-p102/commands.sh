#!/usr/bin/env bash
# Repository root. Only targeted host tests and isolated P77 builds; no complete suites.
set -euo pipefail
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
 --tests org.pag.data.product.P102LeadershipRecoveryTest \
 --tests org.pag.data.product.LeadershipMetadataTest \
 --tests org.pag.data.product.LeadershipSessionConcurrencyTest \
 --tests org.pag.data.product.P101LeadershipWriterTest \
 --tests org.pag.data.product.P101WriterWiringTest \
 --tests org.pag.data.product.OutstandingCoreReadTest \
 :app:testP77runtimeUnitTest --tests org.pag.prototype.product.LeadershipViewModelTest \
 :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:verifyP77RuntimeIsolation --offline --console=plain
# Device reruns: preserve original raw logs by choosing a NEW output directory.
# export P102_EVIDENCE_OUT=docs/p11-p102/review-device
# python3 docs/p11-p102/run-device.py install
# python3 docs/p11-p102/run-device.py run present_ui present-ui
# Repeat only requested isolated scenarios from verify-evidence.py, sequentially.
# python3 docs/p11-p102/run-device.py after
# verify-evidence.py validates committed evidence in the original directory.
