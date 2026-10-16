# Korrektur: Dokument-Pfadgrenze beim Produkt-Unlock

## Befund und eng begrenzte Korrektur

Ausgangspunkt `main`, `000151d` (P10b2d). Der reguläre Unlock auf einem
Samsung SM-M526BR endete nach Credentialauthentifizierung in BLOCKED.

Die DB-Komposition verwendete `noBackupFilesDir.canonicalFile`, die neue
Dokumenten-Reconciliation und das Produkt-Dokumentenrepository dagegen den
unaufgelösten Android-App-Pfad. DocumentFileInventory verlangt absichtlich
kanonische, symlinkfreie Pfade. Ein von Android geliefertes App-Verzeichnisalias
wurde dadurch bereits bei Prüfung des Basisverzeichnisses abgewiesen — vor der
zulässigen Behandlung eines fehlenden Dokumentroots bei leerem Bestand.

`productNoBackupRoot` löst ausschließlich den **vertrauenswürdigen, von Android
bereitgestellten** No-Backup-Basispfad an der Kompositionsgrenze auf. ProductRuntime
verwendet dieselbe Basis für DB und Reconciliation; ProductDocumentRepository
verwendet sie konsistent für Staging, Publication und Delete. Die Strukturprüfungen
innerhalb des Dokumentnamespace bleiben unverändert streng. Insbesondere keine
Kanonisierung eines verdächtigen Dokumentroot-/Staging-/Blob-Symlinks als Reparatur,
kein Ersatzroot, keine heuristische Bereinigung und keine Gate-Abschwächung.

Keine Schema-/Migrations-/Auth-/Session-/Cipher-/Permission-/Manifeständerung.
Keine vorhandenen Produktdateien ersetzt, gelöscht oder zurückgesetzt. Die
Korrektur selbst verändert keine gespeicherten Fachdaten. Regulärer Unlock und
bestehende Maintenance behalten ihre bestehenden autorisierten Regeln.

## Tatsächlich beobachteter Produktnachweis

Der Diagnosebuild und anschließend der korrigierte Build wurden jeweils mit
`adb install -r` als Update installiert; keine Deinstallation/pm-clear und kein
Credentialwechsel. Der Nutzer führte Credentialauthentifizierung selbst aus.

**Nach Installation der Pfadkorrektur bestätigte der Nutzer ausdrücklich:
„Aktionsliste erscheint“.** Der reguläre Produkt-Unlock mit dem bestehenden
Bestand ist damit auf diesem Gerät wieder möglich.

Die zuvor vorbereitete [feste Maintenance-Probe](maintenance-probe.md) lieferte
anfangs keine Zeile. Ein datenfreier Transporttest zeigte geräteseitige
Logunterdrückung; nur die beiden Diagnosetags wurden temporär auf W gesetzt.
Die temporären Overrides wurden anschließend auf Leerwert/Standard zurückgesetzt
und geprüft. Kein globaler Log-Level und keine persistenten Loggingeinstellungen
geändert, kein vollständiges Logcat erfasst oder geleert. Eine alte M04-Fehlerzeile
wurde **nicht** beobachtet; die Pfadzuordnung beruht auf Codeprüfung, reproduzierendem
Regressionstest und anschließend erfolgreichem Produkt-Unlock, nicht auf einer
erfundenen Geräte-Diagnosezeile.

## Tests und Builds

**26 gezielte Hosttests bestanden, 0 Failures/Errors:**

- DocumentPlatformRootTest: 2 neue Fälle. Alias reproduziert alte Ablehnung;
  kanonische Komposition akzeptiert leeren Bestand ohne Rootanlage. Dokument-
  Symlinks bleiben blockiert.
- DocumentFileInventoryTest: 8 bestehende Pfad-/Inventar-Schutztests.
- MaintenanceProbeTest: 12 Diagnosetests.
- DocumentReconciliationTest.realSession*: 4 direkte Maintenance-/Sessionfälle.

App-Debug-Build und Data-AndroidTest-Build erfolgreich. Zusätzlich ein gezielter
Android-Test für die reale App-Pfadgrenze ergänzt und auf dem Samsung ausgeführt.

## Separater offener Android-Publicationfehler

Das vorhandene `org.pag.data.test` ließ sich wegen anderer Signatur nicht als
Update installieren. Es wurde **nicht deinstalliert**. Ein nur für diesen Testlauf
verwendetes Gradle-Init-Skript setzte den Test-ApplicationId auf
`org.pag.data.unlockprobe`. Keine Änderung an Gradlequellen oder Produktmanifest.
Das separate Testpaket benutzt ausschließlich frische UUID-Testverzeichnisse,
synthetische SQLCipher-DBs und neue Testschlüssel. Kein Zugriff auf Produktbestand.

Ausgeführt: genau die fünf Methoden von DocumentReconciliationStorageTest.
**1 bestanden, 4 fehlgeschlagen**:

- App-Pfadgrenze/fehlender Dokumentroot: bestanden.
- Closed-Publication mit erneutem Unlock: fehlgeschlagen.
- Revoke nach Move / Wiederaufnahme: fehlgeschlagen.
- Delete-/Rollback-Recovery: fehlgeschlagen, bereits im Publication-Fixtureaufbau.
- Android No-Overwrite-Link/Sync-Test: fehlgeschlagen.

Gemeinsamer Fehler: **Android Os.link wird mit EACCES verweigert**. Damit ist der
bisherige Hardlink-Publishpfad auf diesem Gerät nicht freigegeben. Kein erfolgreicher
Delete- oder kompletter Importnachweis aus diesen vier Methoden ableitbar.
Dies ist vom leeren Dokumentbestand beim Produkt-Unlock getrennt. Der Hardlinkpfad
wurde nicht durch unsicheres Überschreiben oder Fallbackkopieren ersetzt. Neue
Dokumentenimporte auf diesem Gerät vorerst nicht verwenden; Publication benötigt
eine gesonderte sichere Korrektur und erneute Android-Abnahme. Die dokumentierten
Fehler wurden nicht als grüne Abnahme ausgegeben.

## Reproduzierbare fokussierte Befehle

```sh
./gradlew :data:testDebugUnitTest --tests '*DocumentPlatformRootTest' --tests '*DocumentFileInventoryTest' --tests '*MaintenanceProbeTest' --tests '*DocumentReconciliationTest.realSession*' --offline
./gradlew :app:assembleDebug :data:assembleDebugAndroidTest --offline
# Isolierter Test-ApplicationId über .prototype-cache/unlock-probe.init.gradle:
./gradlew :data:assembleDebugAndroidTest --offline --init-script .prototype-cache/unlock-probe.init.gradle
adb shell am instrument -w -r -e class org.pag.data.product.DocumentReconciliationStorageTest org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
```

Testartefakte/Logs unter `.prototype-cache`; keine echten Berichtsinhalte oder
Produktdaten extrahiert. Kein Commit. Kein Push.
