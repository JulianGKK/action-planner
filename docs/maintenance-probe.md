# Temporäre Maintenance-Diagnose für debuggable Builds

Baseline: `main`, `000151d` (P10b2d). Ziel ist die Eingrenzung eines BLOCKED nach
regulärer Credentialauthentifizierung. Keine Reparatur und keine Abschwächung
von Prüfungen. Bestehende Produktdaten wurden für diesen Diagnose-Slice nicht
auf dem Gerät geöffnet, migriert, verändert oder gelöscht.

## Ausgabe und Phasen

`ProductRuntime` erzeugt pro authorizedMaintenance-Worker einen `MaintenanceProbe`.
Aktiv ausschließlich, wenn die eigene `ApplicationInfo` FLAG_DEBUGGABLE trägt;
kein Rückschluss aus einer Library-BuildConfig. Erfolgreiche Maintenance ist still.
Bei erfolglosem Abschluss genau ein Ausgabeversuch unter **PaGMaintenanceProbe**:

```text
M03_DB_DOCUMENT_SNAPSHOT ROOM_BACKGROUND
```

Dies ist ein **synthetisches Formatbeispiel, kein Samsung-Gerätebefund**.
Die Nachricht besteht nur aus zwei geschlossenen Enumwerten. Keine Exception-
Messages, Stacktraces, Klassennamen, Pfade, Nutzdaten, SQL-Ergebnisse oder Keys.
Kein Throwable wird an Log.w übergeben. Auch Loggingfehler ändern keine Freigabe.

| Code | Letzter begonnener Schritt |
| --- | --- |
| M01_AUTHORIZED_KEY | Vorhandener autorisierter Key-Consumer / Maintenance-Zulassung |
| M02_DB_PREFLIGHT | Vorhandener DB-Preflight nach Lane-Drain/Fence |
| M03_DB_DOCUMENT_SNAPSHOT | Erster autorisierter Dokument-/Jobsnapshot |
| M04_FILE_INVENTORY | Erstes privates Dateiinventar und phasenabhängiger Abgleich |
| M05_JOB_RECOVERY | Verarbeitung bekannter Jobs einschließlich ihrer internen Prüfungen |
| M06_FINAL_INVENTORY | Abschließender DB-/Dateiabgleich und Prüfung auf Restjobs |
| M07_READY_OWNER | Bestehende abschließende Inventarprüfung, Ownerbindung/Publication |

Verschachtelte Snapshotprüfungen während Recovery überschreiben M05 nicht;
der abschließende Snapshot bleibt M06. Die Kategorien sind ebenfalls feste
Enumwerte: Session-Revoke, Storage-Ablehnung, Room-Background-Fault, begrenzte
DB-/I/O-/Security-Kategorien, unbekannter Fehler oder unvollständige Keyoperation.
Keine Klassifizierung anhand von Nachrichtentexten oder Stacktraces.

Die vorhandene Exceptionweitergabe aus dem Envelope-Consumer bleibt erhalten.
`done(success)`, READY-/COMMIT-Gates, Owner-Lifetime und Recoveryentscheidungen
bleiben unverändert. Fehlgeschlagene gültige Maintenance bleibt BLOCKED;
tatsächlicher Session-Revoke behält den bisherigen Lockpfad. Fehlender Dokumentroot
bei leerem Bestand bleibt erlaubt. Keine neue DB-/Filesystemoperation durch die Probe.

## Tatsächliche Prüfung

```sh
./gradlew :data:testDebugUnitTest --tests '*MaintenanceProbeTest' :app:assembleDebug --offline
./gradlew :data:testDebugUnitTest --tests '*MaintenanceProbeTest' --tests '*DocumentReconciliationTest.realSession*' --offline
```

**12 neue Diagnosetests + 4 bestehende gezielte Session-/Maintenance-Tests grün**,
Debug-APK erfolgreich gebaut. Isolierte synthetische Host-Testverzeichnisse;
keine Android-Geräte-/SQLCipher-Abnahme durch diese Hosttests.

Geprüft: Debuggable-Flag, keine Ausgabe ohne Flag/bei Erfolg, maximal eine Zeile,
alle sieben Phasen, fehlender Root bei leerem Bestand, Fehler ohne Cleanup,
keine fremden Daten/Exceptiontexte in der Ausgabe, Log-Sinkfehler ohne Freigabe,
BLOCKED bei unbekanntem Inventar, READY erst nach Erfolg, unveränderte Fristen
und Ablauf während Maintenance.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Der erste Diagnoseauftrag wurde zunächst ohne Installation/Geräte-Unlock beendet.
Im anschließenden Reparaturauftrag wurden Diagnose- und Korrekturbuild als Update
installiert und der reguläre Unlock vom Nutzer bestätigt. Details und die separaten
fehlgeschlagenen Publication-Gerätetests: [Pfadkorrektur](fix-document-unlock-path.md).
Eine alte Samsung-Maintenance-Fehlerzeile wurde nicht beobachtet.
Nach bewusstem manuellem Reproduzieren mit dem Diagnosebuild nur den festen Tag
lesen; kein komplettes Logcat erfassen und keinen Logpuffer leeren:

```sh
adb logcat -d -s PaGMaintenanceProbe:W '*:S'
```

Die erhaltene Zeile lokalisiert zunächst die oben angegebene Schrittgrenze und
Fehlerkategorie. Sie belegt noch keine konkrete Ursache innerhalb von SQLCipher,
Room oder Inventarvalidierung. Keine Reparatur aus dem Code allein ableiten.

Geändert: ProductRuntime.kt, DocumentReconciliation.kt, MaintenanceProbe.kt,
MaintenanceProbeTest.kt und diese Dokumentation. Schema/Migrationen, Auth,
Sessionfristen, Verschlüsselung, Manifest und Permissions bleiben unverändert.
Kein Commit. Kein Push.
