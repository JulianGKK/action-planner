# Isoliertes Active-Workspace-Assessment

Keine Produktaktivierung. Vor Aufruf `ADB` auf das lokale adb-Programm und
`ANDROID_SERIAL` auf das bewusst gewählte Testgerät setzen; alternativ
`--adb` und `--serial` verwenden. Keine Gerätekennung oder benutzerspezifischen
SDK-Pfade im Repository hinterlegen. Init-Skripte werden nur ausdrücklich aufgerufen.
Produkt-App, Credentials, DB, Schlüssel und Dateien sind kein Testmaterial.
Keine neue Dependency/Permission; kein Netzwerk, kein SavedState/Disk-Cache.

```sh
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest -I tools/p11assessment/host.init.gradle --offline
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -I tools/p11assessment/probe.init.gradle --offline
python3 tools/p11assessment/run.py --adb "$ADB" --serial "$ANDROID_SERIAL" --install
python3 tools/p11assessment/summarize.py .prototype-cache/assessment-small-A.txt .prototype-cache/assessment-small-B.txt .prototype-cache/assessment-small-C.txt
```

`run.py` prüft die APK-Metadaten auf exakt `org.pag.perf.p11probe` und dessen
Testpaket sowie Samsung/API33; Installation nur dieser isolierten Pakete mit `-r`.
Kein uninstall/pm clear. Der synthetische Unterbestand wird vom Fixtureowner
nach der Probe geschlossen/entfernt; der Produktpfad wird nicht geöffnet.

Zusätzlich nach demselben isolierten Build/Update des Testpakets:

```sh
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w org.pag.perf.p11probe.test/org.pag.prototype.AssessmentMemoryProbe
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -e variant C -e size large -e scrollOnly true org.pag.perf.p11probe.test/org.pag.prototype.ActiveWorkspaceAssessmentProbe
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w org.pag.perf.p11probe.test/org.pag.prototype.ListStabilityProbe
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w org.pag.perf.p11probe.test/org.pag.prototype.ProjectionProfileProbe
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w org.pag.perf.p11probe.test/org.pag.prototype.Ux3cTasksProbe
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w org.pag.perf.p11probe.test/org.pag.prototype.Ux3dFunctionsProbe
"$ADB" -s "$ANDROID_SERIAL" shell am instrument -w org.pag.perf.p11probe.test/org.pag.prototype.P9MemoryReportProbe
```

Für vergleichbare Scrollreihen A/B/C jeweils dieselbe Größe verwenden.
Erneutes Instrumentieren beendet nur den isolierten Testprozess. Keine produktive
Installation für diesen Assessmentauftrag. Logs/Buildartefakte unter dem ignorierten
`.prototype-cache/` aufbewahren, keine produktiven Logs exportieren.

Grenzen: Teststore kein fertiger Securityadapter; injizierter Speichercharge ist
keine Allokationsmessung. Navigationssession synthetisch, echte Coordinatorfristen
separat in AssessmentSessionTest. MemoryProbe misst GC-Heapdifferenzen unabhängiger
Domain-Graphen ohne Compose/DB/native Pixel. OnDraw nach Modell+Compose-Bestätigung
ist ein UI-Proxy, keine Display-Present-Messung. FrameMetrics lost callbacks nicht
mit verlorenen Displayframes verwechseln. Große Fixture ist kein Fachmaximum;
mehrere Fachbestände haben derzeit keine Gesamtzahlgrenze. Kein ungeprüfter
Produktperformance-/Sicherheitsnachweis aus diesen Probes.
