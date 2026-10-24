#!/usr/bin/env bash
# Im Repository ausgeführte gezielte Offline-Hostprüfungen; keine Gesamtsuite.
# initial-host-tests.log (frühe Implementierung, bisherige 41 Ownerfälle)
./gradlew -PpagTestBuildType=p77runtime :app:testP77runtimeUnitTest \
  --tests org.pag.prototype.product.ActiveCoreOwnerTest --offline --console=plain
# host-tests.log (Zwischenstand, 106 unterschiedliche Fälle)
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
  --tests org.pag.data.product.CoreReadAdmissionBudgetTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
  --tests org.pag.data.product.P97CoreReferenceLifetimeTest \
  :app:testP77runtimeUnitTest --tests org.pag.prototype.product.ActiveCoreOwnerTest \
  --tests org.pag.prototype.product.ActiveWorkspaceBudgetTest --offline --console=plain
# final-owner-budget-tests.log (finale Quellen, 53 Owner- und 6 Admissionfälle)
./gradlew -PpagTestBuildType=p77runtime :app:testP77runtimeUnitTest \
  --tests org.pag.prototype.product.ActiveCoreOwnerTest :data:testDebugUnitTest \
  --tests org.pag.data.product.CoreReadAdmissionBudgetTest --offline --console=plain
# final-handoff-tests.log (frische vollständige XML-Belege für die 4 Dataklassen)
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
  --tests org.pag.data.product.P97CoreReferenceLifetimeTest \
  --tests org.pag.data.product.CoreReadAdmissionBudgetTest --offline --console=plain
javap -p -classpath app/build/intermediates/compile_app_classes_jar/p77runtime/bundleP77runtimeClassesToCompileJar/classes.jar \
  'org.pag.prototype.product.ProductViewModel$VmCoreScopes'
git diff --check
