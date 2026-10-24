#!/usr/bin/env bash
# From repository root. Exact targeted selectors; no complete suites or normal release build.
set -euo pipefail
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
 --tests org.pag.data.product.P101LeadershipWriterTest --tests org.pag.data.product.P101WriterWiringTest \
 --tests org.pag.data.product.CoreWriterIntegrationTest --tests org.pag.data.product.MaterialWriterWiringTest \
 --tests org.pag.data.product.ExpenseWriterIntegrationTest --tests org.pag.data.product.ProtocolWriterIntegrationTest \
 --tests org.pag.data.product.MemoryReportWriterIntegrationTest --tests org.pag.data.product.DocumentMultiPhaseEvidenceTest \
 --tests org.pag.data.product.DocumentRecoveryDispatchContractTest --tests org.pag.data.product.DocumentPendingContractTest \
 --tests org.pag.data.product.DocumentFencePublicationOrderingTest --tests org.pag.data.product.TaskMutationSqliteTest \
 --tests org.pag.data.product.LeadershipSessionConcurrencyTest --tests org.pag.data.product.OutstandingCoreReadTest \
 --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
 :app:testP77runtimeUnitTest --tests org.pag.prototype.product.CoreWorkspaceMutationAdapterTest \
 --tests org.pag.prototype.product.LeadershipViewModelTest \
 :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:verifyP77RuntimeIsolation --offline --console=plain
# Device: use a NEW output directory to preserve committed evidence.
# export P101_EVIDENCE_OUT=docs/p11-p101/review-device
# python3 docs/p11-p101/run-device.py install
# python3 docs/p11-p101/run-device.py run leadership leadership
# ... use the eleven named scenarios in verify-evidence.py; one invocation at a time.
# python3 docs/p11-p101/run-device.py after
