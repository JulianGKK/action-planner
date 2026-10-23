#!/usr/bin/env bash
# Run from repository root. Only affected host classes; the 129k test remains ignored.
set -eu
./gradlew -PpagTestBuildType=p77runtime \
  :data:testDebugUnitTest \
  --tests org.pag.data.product.CoreReadAdmissionBudgetTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.P83OverlapBudgetTest \
  --tests org.pag.data.product.BoundedTaskFixturePreconditionTest \
  --tests org.pag.data.product.ActiveCoreStoreTest \
  --tests org.pag.data.product.BoundedActiveStoreTest \
  --tests org.pag.data.product.BoundedTaskStoreTest \
  --tests org.pag.data.product.CompactPeopleStoreTest \
  --tests org.pag.data.product.CompactTaskStoreTest \
  :app:testP77runtimeUnitTest \
  --tests org.pag.prototype.product.ActiveWorkspaceBudgetTest \
  --tests org.pag.prototype.product.ActiveCoreOwnerTest \
  --offline --console=plain
./gradlew -PpagTestBuildType=p77runtime \
  :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:assembleRelease \
  --offline --console=plain

# Additional exact receipt check through the ViewModel, added after the first host run.
./gradlew -PpagTestBuildType=p77runtime :app:testP77runtimeUnitTest \
  --tests org.pag.prototype.product.ActiveCoreOwnerTest.correctlyChargedCandidateUsesRetainedEightAndTransitionSixteenLimits \
  --offline --console=plain
