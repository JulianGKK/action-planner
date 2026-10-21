# §72 – isolierte native SQLCipher-/Core-Abnahme

Basis `bdc71f92e7c4fedd775c4afa56e12976298d586c`, Branch und Remote identisch,
Arbeitsbaum initial sauber. §71-Review #32 gelesen. Ausführung am 21.10.2026 auf
Samsung SM-M526BR (adb RFCT1186YRJ), vom Benutzer ausdrücklich als isoliertes
Testgerät bestätigt. Nur Eigentümerprofil vorhanden. Beide geschützten Pakete
org.pag.prototype.a und org.pag.perf.p11probe waren vorhanden und wurden weder
installiert, aktualisiert, geöffnet, bereinigt noch deinstalliert.

## Paket- und Artefaktgrenze

org.pag.data.unlockprobe war vor Beginn nicht installiert. Der vorhandene Init-Script
setzt ausschließlich data.testApplicationId; Library-Instrumentierung ist ein einziges
selbstinstrumentierendes APK (Target und Test identisch), kein Produkt-Target-APK.
Manifest aus gebautem APK: package und targetPackage org.pag.data.unlockprobe,
Runner androidx.test.runner.AndroidJUnitRunner, allowBackup=false, minSDK30,
TargetSDK37. apksigner verify erfolgreich; Debug-Zertifikat SHA256
`6e554c135ecc78bc944e6bca8657cfaaccfa75d7b27a1c146a092c2a9d99478e`.
Keine neue Permission; das bestehende zusammengeführte Testmanifest enthält USE_BIOMETRIC.
Build aus Basis plus den dokumentierten §72-Änderungen; keine fremden APKs verwendet.
Das erstmals selbst installierte Testpaket durfte für die folgenden Teststände aktualisiert
werden. Keine unselektierte connectedAndroidTest-Suite.

## ANDROID-RED: Herstellergrenze

Gepinnt SQLCipher Android 4.19.1. Bytecode: SQLCipherStatement.close setzt closed,
reset schließt cursor und setzt ihn null. Unveränderte Herstellerklasse bleibt getrennt
vom Produktwrapper. Exakter Test:
`P11ActiveCoreNativeTest#statementCloseMustReleaseQueryCursor_RED32`.

Erster Versuch: 1 Test / 1 Fehler StorageAccessRevoked, vor Query. Ursache war der
§71-Testharness: direkter B2Native-Aufruf mit READY statt interner MAINTENANCE-Scopebrücke.
Korrigiert entsprechend ActionDatabaseAccess, unveränderte checks des ursprünglichen
READY-Kontexts; keine Sessionautorisierung ausgeweitet.

Zweiter Versuch: 1 Test / 1 Fehler `IllegalStateException: VENDOR_CURSOR_OPEN_AFTER_CLOSE`.
Cursor nach close tatsächlich offen. reset nach close kehrte ohne Exception zurück;
kein unterdrückter Cleanupfehler im Report. Der Querycursor wird von reset freigegeben
(gemäß Bytecode und bestehendem Test mit echter Herstellerklasse/Fakecursor); separate
native Cursorzustandsassertion nach reset fehlt weiterhin. Connection-use und Keyscope
liefen anschließend zurück. Diagnostic-Cleanup versucht reset und close unabhängig und
bewahrt die primäre Diagnose, statt sie mit einem finally-Fehler zu maskieren.

## IMPLEMENTIERT: Produktwrapper

B2Driver bindet B2StatementCleanup an jedes regulär vorbereitete Statement.
Einmalig reset-vor-close; beide Versuche auch bei Exception und ohne Sessiongate.
Erster Fehler bleibt primär, zweiter suppressed; Doppelclose ist idempotent. Kein Retry,
keine erfundene Rollbackklassifikation. DB-/Document-Abschlussverträge bleiben bestehen.
Keine Änderungen an SQLCipher, Openflags, Cipherparametern, Schema oder Keyformat.
Interne direkt delegierte Rollbackstatements enthalten keine Querycursor; deren
Autorisierungs-/Rollbacksemantik bleibt unverändert.

## ANDROID-GREEN: tatsächlicher B2Driver

`productiveB2StatementCloseReleasesCursor_GREEN32`: SELECT-Cursor während Read offen,
nach Produkt-close geschlossen; fünfmal 500 Rows per rekursivem SELECT sowie leerer
SELECT, getLong-/getText-Indexfehler, SQL-overflow bei step, Revoke beim Getter,
BEGIN/ROLLBACK, Doppelclose, Connectionclose, resourcesClosed des Drivers. Reflektive
Beobachtung nur debug-only; keine Cursorreferenz verlässt die Fixture.

Der erste Wrapperversuch scheiterte im Beobachtungsharness an zwei Feldern derselben
Herstellerinstanz (`single()`); Identitätsduplikate werden nun entfernt. Keine
Produktassertion abgeschwächt. Finale native Auswahl und Messwerte siehe unten.

## Abnahmegrenzen / BLOCKER

Cursor.isClosed ist kein direkter Beleg freier CursorWindow-/nativer Buffer. Keine genaue
Zählung aller Room-statements/-cursor; Driver.resourcesClosed prüft Connectionzustand und
Driver-Keykopie. Rückkehr aus withDbKey liefert strukturellen Lease-finally-Beleg, keine
native Zählung oder physische Heaplöschung. Der direkte Hersteller-RED bleibt rot.

Noch nicht nativ injiziert: reset-/close-Exception, DB-/Keycleanupfehler, Budgetverweigerung,
SQL-Exception im Core-Store, Rowloop-Revoke, BLOCKED/UNKNOWN und Core-/DOCUMENT-Pending.
Host-Cleanupfehler-Tests sind keine nativen Faultbelege. 500 Rows beweisen nicht mehrere
CursorWindow-Füllungen; die kompakte Coreseite bleibt maximal32. Wachsende Eventserien
und nachweisbare native Batch-/Windowgrenzen fehlen weiterhin.

#39 Lifecycle-Harness fehlt: echte SessionCoordinator/ViewModel/Compose-Verbindung mit
READY→LOCKED, 30s idle, 180s absolut, Hintergrund, Keyguard, BLOCKED, Reauth, Actionwechsel,
Writerbeginn und altem Main-Runnable/OnDraw. A sofortiger Autoritätsentzug, B Compose-State,
C Pixel und D Heapreferenzen separat messen. Der synthetische Fixturekontext ersetzt dies
nicht. Kein Heap-/Frame-Wipe behauptet.

8 MiB retained + 8 MiB transition unverändert. Für Gatefreigabe müssen Slot, Kandidat,
Main-Runnable, Compose, Editor, Native-/Cursorbuffer sowie GC-/Frameüberlappung gemeinsam
abgenommen werden; vorhandene Messwerte erteilen keine zweite Budgetfreigabe.
ActiveCoreLoadPort.reuseEnabled und UncachedWorkspaceMutations.ramReuseEnabled bleiben
false. Keine P3b-/Dokumentpayloads. RED-4 unverändert fail-closed.

Nächster Schritt: gezielte native Fault- und Lifecycle-/Referenzüberlappungsabnahme,
dann separater unabhängiger RAM-Gate-Review; Content-OnDraw-Harness für kompakte Navigation
und echte autorisierte Hits erst unter diesem Vertrag. Keine Release-/RAM-Freigabe hier.

## Reproduzierbare gezielte Befehle (nur isoliert)

Der verwendete Init-Script ist jetzt unter tools/p11native/unlockprobe.init.gradle
versioniert. Identischer Inhalt wie der im tatsächlichen Build benutzte lokale Script.

```
./gradlew -I tools/p11native/unlockprobe.init.gradle :data:assembleDebugAndroidTest --offline
# Vor Installation: aapt dump xmltree APK AndroidManifest.xml; apksigner verify --print-certs APK.
# Nach eindeutiger Paket-/Deviceprüfung nur dieses eigene Paket installieren.
adb -s RFCT1186YRJ shell am instrument -w -r -e class 'org.pag.data.product.P11ActiveCoreNativeTest#statementCloseMustReleaseQueryCursor_RED32' org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
adb -s RFCT1186YRJ shell am instrument -w -r -e class 'org.pag.data.product.P11ActiveCoreNativeTest#productiveB2StatementCloseReleasesCursor_GREEN32,org.pag.data.product.P11ActiveCoreNativeTest#repeatedRealCoreReads,org.pag.data.product.P11ActiveCoreNativeTest#realReaderRejectsNonActiveAndRevokeBeforePublication' org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
```

Vendor-RED explizit separat behandeln; niemals dessen Fehlschlag aus einem behaupteten
grünen Gesamtlauf verschwinden lassen. Keine Änderungen an bestehenden geschützten Apps.

## Tatsächliche Messergebnisse und Validierung

Finale native Auswahl: **3 Tests / 0 Fehler / 0 Skips**, Runnerzeit93,59s.
Separater Hersteller-RED: **1 Test / 1 erwarteter Fehler**, Runnerzeit1,646s.
Zusätzlich zwei fehlgeschlagene Harnessversuche (Scopebrücke, Reflexionsduplikat),
also insgesamt 6 tatsächlich ausgeführte Testfälle in vier Runneraufrufen:
3 bestanden, 1 Hersteller-RED, 2 korrigierte Harnessfehler; keine Skips.
Nicht als „alle Androidtests grün“ interpretieren.

[Rohmesswerte CSV](p11-native-active-core-72.csv),
[vollständiger Hersteller-RED](p11-native-72/vendor-red.txt),
[vollständiger finaler GREEN-Lauf](p11-native-72/product-green.txt).
CSV-Zeilen -2/-1 sind Warmups; pro Bestand genau20 Messstichproben.
p95 = nearest rank ceil(0,95*n), nicht interpoliert.

| Personen/Aufgaben | Median ms | p95 ms | FD min–max | NativeHeap Bytes min–max | PSS KiB min–max | ART GC Start→Ende |
|---|---:|---:|---|---|---|---|
|4/8|1226,030|1237,321|83–83|4715680–5445008|46463–53151|1→6|
|64/200|1241,126|1266,177|83–83|3763968–4839808|46958–52527|9→15|
|200/1000|1292,015|1309,214|83–83|2884352–3847488|47712–53402|22→28|

Gemessen wird der vollständige frische synthetische Core-Read bis Callback, einschließlich
DB-Open und Cleanup; keine RAM-Hits und kein Content-OnDraw. Der Zeitwert wird im
bestehenden Messcode erst nach Debug.MemoryInfo-/FD-Abfrage gebildet und enthält deren
Overhead. GC-Zähler pro Prozess kumulativ; keine erzwungene GC. Diese20 Samples sind
explorativ, keine Release-p95-Freigabe. Keine FD-/PSS-Toleranz als Leakfreiheitsbeweis.

Nach dem Lauf war no_backup im Testpaket leer (run-as ausschließlich eigenes Paket).
Fixture-close hatte keinen Fehler gemeldet. Das nur in diesem Lauf angelegte Testpaket
wurde anschließend erfolgreich deinstalliert; beide geschützten Pakete bleiben vorhanden.
Versions-, Signatur-, FirstInstall-/LastUpdate-Metadaten beider geschützter Pakete vor/nach
identisch. Keine Produktdaten gelesen. Keine Benutzercredentials.

Vollständiger angeforderter Offline-Lauf BUILD SUCCESSFUL: app682 + data1124 + security4
= **1810 Hosttests / 0 Fehler / 0 Errors / 0 Skips**, domain NO-SOURCE; assemble,
Androidtest-Kompilationen und Manifestprüfungen erfolgreich. Vier neue Produktcleanup-
und zwei Diagnosecleanup-Hosttests. Keine abgeschwächten bestehenden Assertions.

Test-APK final SHA256 `c86c9ca2ae82ab264bd84ca22eae370b55ee87c3009da7ec2d15e2deb9a7b1b9`.
Erster Vendor-APK-Stand `7f0981e6369d92fed3a8b34a181a5ff905b4f40edcf9f517b14c486dc3a48005`
enthielt noch den Scopefehler und ist ausdrücklich kein Hersteller-RED-Beleg.
