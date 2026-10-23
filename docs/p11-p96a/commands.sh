#!/usr/bin/env bash
# Repository root; only affected cases and isolated P77 application.
set -eu
./gradlew -PpagTestBuildType=p77runtime \
  :app:testP77runtimeUnitTest --tests org.pag.prototype.product.ActiveCoreOwnerTest \
  :data:testDebugUnitTest \
  --tests org.pag.data.product.CoreReadAdmissionBudgetTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.P83OverlapBudgetTest \
  :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:assembleRelease \
  --offline --console=plain
adb install -r app/build/outputs/apk/p77runtime/app-p77runtime.apk
adb install -r app/build/outputs/apk/androidTest/p77runtime/app-p77runtime-androidTest.apk
adb shell am instrument -w org.pag.prototype.a.p77runtime.test/org.pag.prototype.P96CoreSurfaceProbe
