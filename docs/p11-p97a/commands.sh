#!/usr/bin/env bash
# From repository root. Only affected host cases; no device/performance suite.
set -eu
./gradlew -PpagTestBuildType=p77runtime \
  :data:testDebugUnitTest \
  --tests org.pag.data.product.P97CoreReferenceLifetimeTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
  :app:testP77runtimeUnitTest --tests org.pag.prototype.product.ActiveCoreOwnerTest \
  --offline --console=plain
