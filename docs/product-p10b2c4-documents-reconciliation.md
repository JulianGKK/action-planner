# P10b2c4 — globale Dokument-Reconciliation vor READY

> Fortschreibung P10b2e: Die hier historisch dokumentierte Hardlink-Publication
> wird auf Android durch atomisches `renameat2(RENAME_NOREPLACE)` ersetzt.
> Recovery akzeptiert weiterhin ausschließlich die eindeutig zugeordneten
> staging-only-/final-only-Zustände; Details und tatsächliche Gerätenachweise
> stehen im [P10b2e-Vertrag](product-p10b2e-android-document-import-fix.md).

## Baseline und Umfang

Branch `p10b2c4-documents-reconciliation`, sauberer Ausgangsbaum auf `9f0730b`
(P10b2c3-Merge), Schema **11**. Verbindlich bleiben
[P10a](product-p10a-documents.md), [P10b1](product-p10b1-documents-storage-design.md),
[P10b2a](product-p10b2a-documents-db-foundation.md),
[P10b2b](product-p10b2b-documents-file-storage.md),
[P10b2c1](product-p10b2c1-documents-import-staging.md),
[P10b2c2](product-p10b2c2-documents-publication.md) und
[P10b2c3](product-p10b2c3-documents-delete.md).

Dieser Slice integriert den globalen Dokumentabgleich in den bestehenden
ProductRuntime-authorizedMaintenance-Worker. Keine Dokumenten-UI, öffentliche
Dokumenten-Repositories, SAF, URI-Handoff, Viewer oder Exportfunktion.
Keine Auth-/Sessionarchitekturänderung, neue Dependency oder Permission.

## Tatsächlicher Runtimeablauf

LOCKED → bestehende Authentifizierung → MAINTENANCE → bestehendes READY bleibt
unverändert. Innerhalb des vorhandenen autorisierten Key-Consumers:

1. `documentMaintenanceBarrier` prüft MAINTENANCE/Generation und OPERATION.
2. `DocumentReconciliation.run` betritt einmal global
   `DocumentNamespaceLane.drainAndFence`. Alte Reader/Worker müssen enden;
   alte wartende Tickets verlieren ihre Zulassung. Nach dem Warten erneut prüfen.
3. Erst jetzt läuft der bestehende Runtime-DB-Preflight: ActionDatabaseAccess
   öffnet authentifiziert, prüft SQLCipher/SQLite/FKs/Room-Schema, liest und
   validiert Actions und schließt vollständig. Keine Bereinigung vor DB-Erfolg.
4. Die neue DAO-Reconciliation-Snapshotoperation liest alle Dokumente und Jobs
   konsistent und validiert sämtliche Bindungen/Kapazitäten. Room wird geschlossen.
5. Den privaten Dokument-Dateibestand prüfen, einschließlich sämtlicher
   Phasenregeln. Erst nach erfolgreicher globaler Prüfung bekannte Jobs bearbeiten.
6. Reihenfolge: unterbrochene RESERVED/WRITING-Imports abbrechen, abgeschlossene
   CLOSED/PUBLISHED-Imports verifiziert fortsetzen, dann vorhandene Delete-Recovery
   nutzen. Innerhalb einer Gruppe deterministisch nach Job-ID. Vor jedem Job
   erneut DB-/Dateisnapshot prüfen; Fortschritt nie aus alten Snapshots erraten.
7. Abschließend erneut DB öffnen/prüfen/snapshotten/schließen und Filesystem
   inventarisieren. Keine Jobs, keine Stagingdateien, exakt ein zugeordneter
   finaler Blob je fertigem Dokument. Erst dann verlässt der Ablauf die Lane.
8. Scoped Maintenance-Key-Übergabe beenden. Erst anschließend darf der bestehende
   Runtimepfad SessionDbKeyOwner binden und mit dem ursprünglichen Context
   veröffentlichen. Erst dessen erfolgreicher Abschluss erlaubt dem unveränderten
   SessionCoordinator den Übergang zu READY.

Die erstmalige Initialisierung bleibt erhalten: bestehende DB-Verzeichnisanlage,
DB-Erzeugung/Validierung und ProductInventory.finish laufen im selben Preflight
innerhalb der Lane. Danach wird der leere Dokumentbestand genauso geprüft;
ein fehlender Dokumentroot wird weder benötigt noch angelegt. ProductRuntime
verwendet weiterhin seinen vorhandenen Worker und Main-Executor, keine neuen
Threads oder Pools. Datei-I/O bleibt im Worker und außerhalb des Sessionmonitors.

`pag-db-v1` bleibt ausschließlich DB-/Envelopebestand. Dokumente werden nur unter
**noBackupFilesDir/pag-documents-v1/{staging,files}** geprüft. Andere app-private
Namespaces werden nicht als Dokumente interpretiert.

## DB-Snapshot, Gates und Schlüssel

`DocumentDao.reconciliationSnapshot` ist ausdrücklich MAINTENANCE-only. Die
bestehenden READY-only Publication-/Importoperationen wurden nicht pauschal
geöffnet. Separate Maintenance-Einstiege verwenden gemeinsame private
Transaktionsschritte nach ihrer eigenen Scope-/Statusprüfung.

Geprüft werden UUIDs für Dokument/Job/Blob/Staging, vorhandene Actionzugehörigkeit,
alle Phasen/Nullkombinationen, normalisierte Namen/Formate/Größen, Keylängen,
Parent-/Jobkombinationen, eindeutige Bindungen und die bestehenden Kapazitäten
inklusive Hidden-/Pending-Beständen. ActionDatabaseAccess prüft weiter bestehende
FKs/Trigger/Room-Struktur. Inventarisierung ist auch in DELETION_PENDING möglich;
Importpublication verlangt weiterhin PREPARATION/ACTIVE/COMPLETED. Bekannte Delete-
Cleanupjobs dürfen nach P10b1 in DELETION_PENDING fortgesetzt werden. Kein Reopen.

Der globale Snapshot enthält nur technische Namen/IDs/Phasen, keine File-DEKs,
Antworttexte oder Dokumentnamen. Benötigte einzelne Publication-Keykopien werden
frisch aus SQLCipher übernommen, kurzzeitig scoped verwendet und bestmöglich
gewiped. Auch kontrollierte DAO-Inventarkopien werden im finally überschrieben.
Ein Expected-Snapshot bleibt über CLOSED → FILE_PUBLISHED bis zur finalen
Verifikation gebunden; ein Metadatenwechsel darf nicht still übernommen werden.

`SqlCipherDocumentReconciliationDatabase` nutzt die bestehende kurze
`SqlCipherDocumentDeleteDatabase.maintenanceStep`-Nutzung mit ActionDatabaseAccess.
Der ursprüngliche DB-Schlüssel bleibt ausschließlich im vorhandenen synchronen
Auth-Maintenance-Key-Consumer. `withDocumentMaintenanceKey` verleiht daraus eine
thread-/context-/generationgebundene scoped Übergabe für einzelne DB-Schritte;
keine neue Key-Ownerinstanz. Außerhalb des Scopes wird die geborgte Referenz
verworfen und eine spätere Nutzung abgelehnt. Pro DB-Schritt kontrollierte Kopie,
finally-Wipe und vollständiger Room-Close/Cancel/Join/Driver-Close; keine solche
DB-Key-Lease, Transaktion oder Room-Instanz während Datei-I/O. Der ursprüngliche
Auth-Consumer behält seine bestehende eigene finally-Wipe-Verantwortung.
Keine dauerhafte Closure, Registry, neue Keydatei, KDF, Alias oder Authprompt.

B4s queryCoroutineContext-/Background-Fault-/Revocation-Behandlung bleibt in
ActionDatabaseAccess unverändert. SessionCoordinator und Fristen unverändert:
180 Sekunden absolut, 30 Sekunden READY-Inaktivität. Keine Recovery-/Timer-/
Storageaktivität ruft userInteraction auf. Langer Workerdrain/Recovery kann die
Session ablaufen lassen; es gibt keine Garantie, maximale Bestände innerhalb
jeder Session zu erledigen, und keine Fristverlängerung.

## Globale Dateiregeln

`inspectRecovery` ist eine zusätzliche MAINTENANCE-only Inventarprüfung.
Normales `inspect` behält seine bisherigen strengen Regeln. Bekannte mögliche
Namen werden ausschließlich aus validierten DB-Daten abgeleitet. Jeder vorhandene
Eintrag muss regulär, kanonisch und symlinkfrei sein; Dateidentitäten müssen
vorliegen und eindeutig sein. Fremde Einträge, Unterverzeichnisse, Namens-/
Identitätskonflikte oder unklare Bindungen blockieren vor jeder Jobmutation.

Ein fehlender Root ist bei leerem Bestand zulässig. Auch ausschließlich RESERVED-
Jobs ohne jedes Artefakt dürfen bei noch nicht angelegtem Root verworfen werden.
Bei fertigen Dokumenten oder anderen Jobphasen wird ein fehlender Root nicht
als neuer leerer Bestand ersetzt. Fehlender Stagingblob eines WRITING-Jobs ist
bei intaktem bekanntem Root zulässig; das Stagingverzeichnis wird synchronisiert.
Partielle Roots blockieren; Recovery erzeugt keine Ersatzverzeichnisse.

Dies bleibt ein privater, prozesslokal serialisierter Namespace, keine universelle
Racefreiheit gegen privilegierte OS-Manipulation oder ein Mehrprozesslock.

## Recovery aller Phasen

| Phase/Dateilage | Kontrollierte Verarbeitung |
| --- | --- |
| IMPORT_RESERVED, beide Namen fehlen | Exakten Job mit COMMIT-Gate entfernen, keine Quelle öffnen |
| IMPORT_RESERVED mit Artefakt | BLOCKED, nichts entfernen |
| IMPORT_WRITING, final fehlt | Nur eigenes vorhandenes Staging unlinken oder dessen Fehlen akzeptieren; staging sync, fehlendes Ziel erneut prüfen, Job zuletzt entfernen |
| IMPORT_WRITING mit finalem Blob | BLOCKED, niemals daraus ein Dokument erzeugen |
| IMPORT_CLOSED, staging-only | Vollständige Tink-/EOF-/Längen-/Formatprüfung; bestehendes exklusives Link/Sync/Unlink/Sync-Verfahren; FILE_PUBLISHED committen |
| IMPORT_CLOSED, final-only | Final vollständig prüfen; Verzeichnissync wiederholen, kein zweiter Move; FILE_PUBLISHED committen |
| IMPORT_CLOSED, both/neither | BLOCKED, keine Hardlink-Reparatur oder Ersatzquelle |
| FILE_PUBLISHED, final-only | Final erneut vollständig verifizieren, danach documents-Insert/Jobdelete atomar |
| FILE_PUBLISHED mit Staging/fehlendem finalen Blob | BLOCKED |
| DELETE_RESERVED | Vorhandenen konsistenten Parent und Blob verlangen; bestehender atomarer Parentdelete/PENDING-Wechsel |
| DELETE_FILE_PENDING | Parent muss fehlen; nur eigenen Blob entfernen oder bereits fehlend akzeptieren; files sync, exakten Job zuletzt entfernen |

`DocumentPublication.recoverInsideLane` verwendet denselben Verifikations-,
Move- und finalen Commitpfad wie P10b2c2. Neue MAINTENANCE-DAO-Einstiege prüfen
vollständig den aktuellen Job und dessen Expected-Bindung. `DocumentDelete.recoverInsideLane` verwendet unverändert das Delete-Protokoll aus P10b2c3;
kein zweites Delete-Protokoll. Beide verlangen die bereits exklusiv gehaltene
Lane; kein separates Fencing pro Job. Bestehende externe Ein-Job-Einstiege
bleiben scopegebunden.

CLOSED/PUBLISHED-Korruption bleibt mit Job und Datei erhalten. Keine teilweise
sichtbare Document-Row; finale Keyübernahme/Parentinsert und Jobdelete bleiben
eine SQLCipher-WriteTransaction mit unveränderten Importparent-Constraints.
Kein Klartexttempfile, keine Klartextweitergabe, kein neuer Cipher. Tink/AAD-
Vertrag unverändert. Keine externe Quelle wird bei Recovery wieder geöffnet.

Fertige Dokumente werden beim Unlock **strukturell**, nicht erneut vollständig
entschlüsselt geprüft. Das ist keine Authentifizierungszusage für jeden unberührten
Ciphertext bei jedem Unlock; spätere Bloböffnung muss vollständig authentifizieren.
Alle fortgesetzten Imports werden vor ihrem finalen Commit vollständig geprüft.

## Fehler, Revoke und Ressourcen

Erwartete StorageAccessRevoked bleiben typisiert. Die Reconciliation erklärt
unerwartete SQL-/Room-Background-Faults nicht zu harmloser Revocation. Der
bestehende Envelope-Key-Consumer meldet konsumierende Exceptions als FAILED;
ProductRuntime erhält dafür lokal den ursprünglichen Exceptiontyp bis zu seiner
Fehlerbehandlung. Keine sensiblen Fehler werden geloggt oder an UI publiziert.
Bei gültiger MAINTENANCE führt erfolgloser Abschluss über den bestehenden
SessionCoordinator-Pfad zu BLOCKED. Ist der Context tatsächlich abgelaufen/
widerrufen, bleibt die bereits invalidierte Session LOCKED und wird nicht READY.

Vor/nach DB- und Dateischritten gelten Generation/OPERATION/STEP/COMMIT.
Revoke verhindert neue autorisierte Schritte; Ressourcenclose kann technisch
enden. Commit vor Revoke kann persistent wirksam bleiben. Kein behaupteter
Rollback nach erfolgreichem Commit, kein blindes Retry oder Quell-Re-Read.
Partielle Arbeit an mehreren Jobs ist persistent nachvollziehbarer Fortschritt,
keine globale DB-/Filesystem-Transaktion.

Wichtige Wiederanlaufzustände bleiben erhalten: WRITING nach Staging-unlink aber
vor Jobdelete, CLOSED + final-only nach Move, FILE_PUBLISHED vor finalem Commit,
PENDING + fehlender Blob vor Delete-Abschluss. Doppelbestände werden nicht anhand
gleicher Inodes heuristisch repariert. Keine Secure-Erase-/Power-Loss-Garantie.

## Tatsächlich durchgeführte Prüfungen

```sh
./gradlew :data:compileDebugKotlin --offline
./gradlew :data:testDebugUnitTest --tests '*DocumentReconciliationTest' :data:compileDebugAndroidTestKotlin --offline
python3 tools/p10b2c4/check_reconciliation_sql.py
```

Neue Hosttests verwenden produktive DAO-Validierung und bestehende Tink-Primitiven
mit simuliertem Transaktionsrollback sowie isolierte synthetische Dateien und
echte Host-Link/Unlink/Verzeichnissync-Operationen. Keine echten Produktdaten.
Geprüft: leerer/fehlender Root, struktureller Erhalt fertiger Dokumente ohne
Vollentschlüsselung, alle sechs Phasen und ihre Dateilagen, beschädigte Imports,
unbekannte Dateien/Symlinks/Bindungsfehler, unveränderte Expected-Metadaten,
Revoke innerhalb Verifikation und nach Commit, finaler Rollback, gemischter
Jobbestand, DB-/Preflightfehler ohne Cleanup, tatsächlicher Readerdrain und alte
Tickets sowie fehlender Pflichtblob/partieller Root.

Gezielte Tests mit echtem SessionCoordinator und der von ProductRuntime verwendeten
Maintenance-Barriere prüfen MAINTENANCE vor Owner-Vorbereitung, READY erst nach
Erfolg, BLOCKED bei Unknownbestand, Ablauf statt READY und unveränderte absolute
Frist auch bei erst nach 170 Sekunden abgeschlossener Maintenance. Diese Hosttests
instanziieren keine Android-Activity und ersetzen keinen echten Credential-Unlock.
Ein Background-Fault-Injektionstest sichert, dass der schwere Fehlertyp nicht
in StorageAccessRevoked umgedeutet wird. Scoped Keyübergabe wird nach Ende abgelehnt.

Drei kleine neue Host-SQLite-Fälle prüfen die neue bedingte Importjobdelete-Query,
Rollback, Ausschluss von Deletejobs und alle sechs Phasen mit den unveränderten
Schema-11-Constraints. Bestehender Fixtureaufbau, keine historische Suite.
Die vier älteren DAO-Testdoubles erhielten nur erforderliche Compile-Stubs.
Keine historischen Gesamtsuites, B1–B4-Matrix, App-/UI-Probes oder Lint ausgeführt.

## Android-Nachweis: ausdrücklich ausstehend

ADB-Verfügbarkeit wurde geprüft. Der erste Sandboxaufruf scheiterte am gesperrten
ADB-Socket; der autorisierte Aufruf außerhalb der Sandbox lieferte eine leere
Geräteliste. **Kein Android-Gerät oder Emulator verfügbar. Keine Gerätetests,
keine Instrumentation ausgeführt.** Kein Wipe, Credentialwechsel oder Zugriff
auf echte Produktdaten.

Vier gezielte synthetische Android-Testmethoden wurden angelegt und ausschließlich
kompiliert: realer SQLCipher-/Room-Publicationpfad mit zweitem Maintenance-Durchlauf,
Revoke nach physischer Verschiebung mit final-only-Wiederanlauf, Commitrollback/
Pending-Delete-Recovery sowie Android-No-Overwrite-Link/Unlink-No-Follow/
Verzeichnissync. Sie nutzen ausschließlich einen UUID-Testnamespace im
standalone Data-Testpaket. Kompilierung ist kein bestandener Android-Lauf.

Vor UI-/Produktfreigabe erforderlich: diese Nachweise tatsächlich auf Android
inklusive Room-Close/Background-Fault-/Revoke-Rennen, SQLCipher-Atomizität,
Dateisystem-/Syncunterstützung und echtem erneutem Unlock ausführen. Eine
allgemeine SIGKILL-/Power-Loss-Durability wird damit ebenfalls nicht behauptet.
Code-Review-Status und Android-Freigabe sind getrennt.

## Verbleibende Produktgrenzen

Dokumenten-UI, SAF-/URI-Handoff, Viewer, neue öffentliche Repositoryports,
Action-Gesamtlöschung und Retentionworkflow bleiben außerhalb dieses Slices.
Import/Publication/Delete sind weiterhin keine sichtbare Produktfunktion.
Nur die Maintenance-Barriere ist jetzt tatsächlich in ProductRuntime integriert.
Schema 11, Entities/DDL/Trigger, SessionCoordinator/Fristen, Security/Auth,
Keystore-Alias, Tink, Manifest/Permissions und Dependencies bleiben unverändert.
Kein Commit. Kein Push.

## Vollständige Dateiliste

- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDelete.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImportStaging.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentPublication.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentMaintenance.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentReconciliation.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentDeleteTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentFoundationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImportStagingTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPublicationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentReconciliationTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/DocumentReconciliationStorageTest.kt`
- `tools/p10b2c4/check_reconciliation_sql.py`
- `docs/product-p10b2c4-documents-reconciliation.md`

## Abschlussnachweis

Finaler gezielter Lauf: **36 Hosttests**, 0 Failures, 0 Errors, 0 Skips,
BUILD SUCCESSFUL. **3 Host-SQLite-Fälle grün**. Data-/KSP-/Room- und
AndroidTest-Kompilierung erfolgreich, automatisch abhängige Architekturprüfung
erfolgreich. **0 Gerätetests ausgeführt**; Android-Freigabe ausstehend.
Schema-/Entity-/DDL-/Trigger- und Security-/Session-/Permission-/Dependency-Freeze
gegen `9f0730b` ohne Änderung. `git diff --check` und Whitespaceprüfung neuer
Dateien ohne Befund. Alle 16 oben aufgeführten Dateien uncommitted.

A) GRÜN – BEREIT FÜR CODE-REVIEW

Android-Freigabe: gesondert ausstehend, reale Nachweise vor UI-/Produktfreigabe erforderlich.
