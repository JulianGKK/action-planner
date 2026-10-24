#!/usr/bin/env bash
# Initialer Lauf (40 Fälle), danach finaler Lauf mit zusätzlichem parallelen Main-Erstaufruf (41).
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
  --tests org.pag.data.product.P97CoreReferenceLifetimeTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest --offline --console=plain
javap -c -p -classpath data/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar \
  'org.pag.data.product.OutstandingCoreRead$WorkerHandoff' \
  'org.pag.data.product.OutstandingCoreRead$MainHandoff'
git diff --check
