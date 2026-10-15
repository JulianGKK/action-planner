# P10b2c3 — sichere Dokumentlöschung mit begrenzter Delete-Recovery

## Baseline und Umfang

Sauberer Branch `p10b2c3-documents-delete`, HEAD `8967a86` (P10b2c2-Merge),
SQLCipher-/Room-Schema **11**. Grundlagen bleiben
[P10a](product-p10a-documents.md), [P10b1](product-p10b1-documents-storage-design.md),
[P10b2a](product-p10b2a-documents-db-foundation.md),
[P10b2b](product-p10b2b-documents-file-storage.md),
[P10b2c1](product-p10b2c1-documents-import-staging.md) und
[P10b2c2](product-p10b2c2-documents-publication.md).

Implementiert ist ausschließlich der interne Weg DELETE_RESERVED →
DELETE_FILE_PENDING → bekannter Blob entfernt → Delete-Job entfernt und eine
gezielte Wiederaufnahme eines benannten Delete-Jobs. Keine Runtime-/UI-Freigabe,
keine allgemeine Reconciliation, kein Startup-Cleanup oder Maintenance-Orchestrator.

## Komponenten und DB-/Dateireihenfolge

`DocumentDelete.delete` verlangt READY, Action-ID und den vollständigen
keyfreien fachlichen DocumentAsset-Expected-Snapshot. Es hält dieselbe
`DocumentNamespaceLane` wie Import und Publication und prüft Generation,
OPERATION/STEP sowie die bestehenden Contextfreigaben. Keine I/O-Arbeit hält
den SessionCoordinator-Monitor.

1. `DocumentDao.reserveDelete`: in kurzer WriteTransaction persistierten
   Actionstatus PREPARATION/ACTIVE/COMPLETED prüfen; Expected vollständig mit
   den sechs gespeicherten Fachmetadaten vergleichen, Action-/Dokumentbindung,
   sämtliche DB-Bindungen und Kapazitätsintegrität prüfen. Bereits vorhandener
   Job oder veralteter Expected blockiert, kein blinder zweiter Delete.
2. Genau einen DELETE_RESERVED-Job mit eigener technischer Job-ID anlegen:
   actionId/documentId/blobName/plaintextSize, operation DELETE. **Kein DEK,
   displayName, format oder stagingName im Job.** COMMIT-Gate prüfen. Die
   bestehenden count/list/get-Projektionen schließen bereits diesen Job aus;
   ihre Queries bleiben unverändert.
3. Room und DB-Key-Lease sind geschlossen. Vollständiges bekanntes Inventar
   prüfen. Bei RESERVED muss der passende Parent samt bekanntem Blob bestehen.
   Fehlender Blob, unbekannte Datei, Symlink oder widersprüchlicher Bestand
   blockiert und erhält den versteckten Parent und Job.
4. `detachDeleteParent`: Expected/Job/Parent/Inventar und Actionstatus erneut
   in einer WriteTransaction prüfen. Zuerst documents-Row entfernen, danach
   denselben Job auf DELETE_FILE_PENDING setzen; Größe/Blobname bleiben gleich.
   COMMIT-Gates vor Mutation und am Ende. Fehler rollen beide Änderungen zurück.
5. Room schließen, frischen PENDING-Snapshot laden und Dateiinventar prüfen.
   Parent muss fehlen. Nur der bekannte reguläre Blob unter files/ darf mit
   dem bestehenden FileOps-Unlink entfernt werden. STEP/Generation davor und
   danach prüfen. Ist genau dieses Ziel bereits abwesend, ist das erst ab
   PENDING idempotent zulässig.
6. Bestehenden Verzeichnissync auf files/ ausführen, auch bei bereits fehlendem
   Ziel: Ein früherer Unlink könnte einem Crash vorausgegangen sein. Kein Sync
   auf einem geschlossenen Descriptor; dieselbe Android-Implementierung wie P10b2c2.
7. Frischen exakten DB-Snapshot und vollständiges Inventar erneut prüfen.
   Das konkrete Ziel muss fehlen. `finishDelete` entfernt ausschließlich diesen
   PENDING-Job in kurzer WriteTransaction mit COMMIT-Gates. Danach erneut Session
   prüfen. Kein UI-/StorageContext.publish-Aufruf.

Bis zum Jobabschluss zählen Parent oder parentloser PENDING-Job weiterhin genau
als ein Slot mit derselben Größe im bestehenden Kapazitätsmodell. Andere
Dokumente/Jobs und alle Stagingdateien bleiben bestehen. Keine neue Datei,
Ersatzdatei, Rootanlage oder Orphan-Heuristik.

`DocumentDeleteSnapshot` enthält ausschließlich einen keyfreien Job, bei
RESERVED keyfreie aktuelle Parentmetadaten und technische Inventarmengen.
DAO-Reads validieren vorhandene Keys intern und überschreiben ihre kontrollierten
Kopien im finally. Kein File-DEK gelangt in die Delete-Koordination. Nach
committed Parentdelete gibt es keinen File-DEK mehr im aktuellen Live-DB-Bestand.
Dies beweist **kein kryptografisches Secure Erase**: historische DB-/WAL-/Flash-
und RAMkopien können bestehen. Kein physisches Überschreiben behauptet.

## Inventar und Pfadgrenze

`DocumentFileInventory` bleibt die kanonische No-Follow-Prüfung für
`noBackupFilesDir/pag-documents-v1/{staging,files}`. Der neue Inventarvertrag
unterscheidet erforderliche Namen und ausschließlich durch DELETE_FILE_PENDING
bekannte optional fehlende Blobnamen. Fremde Namen, Symlinks, unerwartete
Unterverzeichnisse, fehlende Pflichtbestände oder fehlender Root blockieren.
Nur optionale Pendingziele dürfen fehlen; sie werden nicht neu erzeugt.

Stabile IMPORT_CLOSED-Stagings und FILE_PUBLISHED-Finalblobs können im bekannten
Gesamtinventar vorhanden sein, werden aber nicht bearbeitet. RESERVED/WRITING-
Imports oder andere unklare Import-Dateilagen blockieren bis P10b2c4. Es wird
keine Import-Recovery vorweggenommen. Ein nach Unlink wieder auftauchendes Ziel
verhindert den Jobabschluss; nach abgeschlossenem Job wäre es unbekannter Bestand.

Unlink und Verzeichnissync verwenden unverändert `AndroidDocumentPublishFileOps`
aus P10b2c2. Files.delete folgt keinem Blatt-Symlink; die vorherige Inventarprüfung
lehnt Symlinks bereits ab. Es gibt keine allgemeine Mehrkomponenten-Racefreiheit
gegen privilegierte OS-Manipulation. Private Pfade und gemeinsame Serialisierung
aller eigenen Mutatoren bleiben Voraussetzung. Unsupported/IO-/Syncfehler
werden nicht als erfolgreicher Abschluss übersprungen. Keine universelle
Power-Loss-Durability-Garantie und kein physisches Secure Erase.

## Reader, Lane und MAINTENANCE

`DocumentFileInventory.verifyExisting` hält jetzt während Prüfung, Streamöffnung,
vollständigem Lesen und Close dieselbe Namespace-Lane. Laufende vorhandene
Ciphertextreader enden vor einer Delete-Reservierung; wartende Reader prüfen
anschließend Context und aktuellen Bestand erneut. Es gibt keine offenen
Dateistreams oder Klartextconsumer im Rückgabewert. Neue Viewer/Reader müssen
später denselben Vertrag einschließlich Verwerfen etwaiger UI-Referenzen erfüllen;
solche UI-Caches gibt es hier nicht.

`DocumentDelete.recover` akzeptiert nur gültiges MAINTENANCE und eine bestehende
Delete-Job-ID. Es verwendet `DocumentNamespaceLane.drainAndFence`: laufende
Arbeit beenden lassen, alte Admission-Tickets ungültig machen und Wiederaufnahme
innerhalb derselben Lane halten. Nach dem Warten wird der Context erneut geprüft.
Keine eigene Session-State-Machine, keine Deadlineverlängerung, keine automatische
READY-Publication. Runtime-Sessionwechsel und sämtliche künftigen Mutatoren müssen
vor Produktfreigabe tatsächlich an diesen Prozess-lokalen Vertrag angebunden
werden; es wird kein Mehrprozesslock behauptet.

| Persistierter Job | Enger Wiederanlauf |
| --- | --- |
| DELETE_RESERVED | Parent und Bindungen müssen gültig sein, Blob muss vorhanden sein. Dann atomar Parent entfernen und PENDING setzen; fehlender Blob blockiert. |
| DELETE_FILE_PENDING | Parent muss fehlen. Bekannten vorhandenen Blob unlinken oder bereits fehlendes Ziel akzeptieren; sync, DB-/Inventarprüfung, Jobabschluss. |
| Anderer/fehlender Job oder unklare Lage | Neutraler Storagefehler; kein Cleanup/Retry. |

MAINTENANCE darf gemäß P10b1 einen bestehenden Deletejob auch bei
DELETION_PENDING bereinigen, ohne die Action wieder zu öffnen. READY erhält
keinen Zugriff in DELETION_PENDING; MAINTENANCE kann keinen neuen Delete über
diesen Pfad reservieren. DRAFT bleibt gesperrt. Andere Fachbereichsregeln und
insbesondere der bestehende READY-only Operations-Gate sind unverändert.

## SQLCipher und Schlüsselzugriff

`SqlCipherDocumentDeleteDatabase` nutzt vorhandenen ActionDatabaseAccess samt
B4-Room-Lifecycle pro kurzem Schritt. READY verwendet den bestehenden
SessionDbKeyOwner. Während File-I/O bestehen weder Room-Instanz noch DB-Key-Lease.

Vor READY gibt es noch keinen neuen SessionDbKeyOwner. Für die spätere
Maintenance-Anbindung nimmt der Adapter daher einen **synchronen, scoped
Key-Consumer pro DB-Schritt aus dem vorhandenen autorisierten Maintenance-
Keykontext** entgegen. Kein neuer Owner, Alias, Authprompt oder persistenter Key.
Der Consumer muss genau aufrufssynchron, am selben Thread und maximal einmal
aufrufen; später/anderweitig eintreffende Callbacks werden abgewiesen. Eine
kontrollierte DB-Key-Kopie wird ausschließlich für diesen DB-Schritt angelegt
und im finally bestmöglich überschrieben. Fehlender Consumer bedeutet Ablehnung.
Die tatsächliche Runtime-Verdrahtung ist ausdrücklich noch nicht erfolgt.

## Crash, Rollback und Revoke

| Grenze | Tatsächlicher verbleibender Zustand |
| --- | --- |
| Reserve-COMMIT verweigert | Kein neuer Job, Parent/Blob unverändert |
| RESERVED committed, Prüfung scheitert | Parent bleibt verborgen, Blob/Bestand nicht bereinigt |
| Parentdelete oder Phasenupdate scheitert | Vollständiger Rollback: Parent + RESERVED |
| PENDING committed, danach Revoke | Parent weg, keyfreier PENDING-Job und ggf. Blob bleiben |
| Unlinkfehler | PENDING bleibt; kein Abschluss |
| Unlink erfolgt, Revoke/Syncfehler | PENDING + fehlendes Ziel; Wiederanlauf synced erneut |
| Finaler Jobdelete/COMMIT scheitert | PENDING bleibt; fehlendes Ziel ist idempotent |
| Jobabschluss committed, danach Revoke | Parent/Job/Blob dürfen bereits fehlen; keine Erfolgsrückkehr und kein behaupteter Rollback |

Keine Quell-URI, kein Provider-Re-Read, kein Zurückverschieben, kein Klartext,
keine Telemetrie oder sensiblen Fehler-Causes/Logs. Doppelte Invocation gegen
bereits entfernten Parent/Job ist fail-closed und unlinkt nicht erneut.

## Tatsächliche fokussierte Prüfungen

```sh
./gradlew :data:compileDebugKotlin --offline
./gradlew :data:testDebugUnitTest --tests '*DocumentDeleteTest' --offline
python3 tools/p10b2c3/check_delete_sql.py
```

Neue Hosttests verwenden die produktiven DAO-Regeln mit speichergestütztem
Test-DAO und explizit simuliertem Transaktionsrollback. Dateifälle verwenden
nur isolierte temporäre Verzeichnisse, synthetische Tink-Ciphertexte, echte
Host-Unlinks und Verzeichnissync. Keine echten Produktdaten oder Klartextdateien.

Geprüft sind sofortiges Ausblenden/keyfreier Job, atomarer PENDING-Wechsel und
Rollback, fehlende Ziele vor/nach PENDING, beide Recoveryphasen, inkonsistenter
Parent, unbekannte Dateien/Symlinks, Erhalt anderen Ciphertexts und Stagings,
Unlink-/Sync-/Jobdeletefehler, Revoke vor/nach Commits, veralteter Expected,
Scope-/Statusgrenzen, Doppelaufrufe, Wiederauftauchen des Ziels, widerrufene
Maintenance und Drain eines tatsächlich über verifyExisting laufenden Readers.
Ein Lane-Fence weist alte Admission-Tickets zurück. Kein externer Publishcallback.

Vier kleine Host-SQLite-Fälle führen die neuen produktiven Delete-/Updatequeries
und unveränderten Schema-11-Constraints aus: tatsächliche count/list/get-
Sichtbarkeit, Key-null-Job, Parentdelete/PENDING inklusive Rollback und erhaltenem
Budget, Jobabschluss/Rollback sowie bestehendes Action-FK-RESTRICT. Historischer
Fixtureaufbau wird wiederverwendet, seine Testsuite nicht ausgeführt.

Der erste Hostlauf fand die unzulässige Wiederverwendung des READY-only
Operations-Gates für MAINTENANCE. Behoben wurde dies ausschließlich durch einen
Delete-spezifischen Context-/Generation-Gate; bestehende Operationsrechte wurden
nicht erweitert. Der reproduzierende Recoverytest bleibt erhalten.

Keine historische Gesamtsuite, B1–B4-Matrix, Galaxy/ADB, Instrumentation oder Lint.
Die notwendige Data-/KSP-Kompilierung prüft das neue Room-DAO; drei alte Testdoubles
wurden nur um erforderliche Compile-Stubs ergänzt. Ihre Suites liefen nicht.

## Offene Android-Nachweise und Übergabe an P10b2c4

Hosttests sind **kein Android-SQLCipher-/Room-Runtime-Nachweis**. Gezielt offen:
reale Commit-/Rollback-/Revoke-Rennen mit SQLCipher, Room-Background-/Closepfad,
Android-Unlink/No-Follow/Verzeichnissync und Crashhaltbarkeit sowie die tatsächliche
Maintenance-Key-Consumer-/Worker-/Reader-/Lane-Anbindung. Diese Voraussetzungen
müssen vor Runtimefreigabe geprüft werden. Der neue Produkteinstieg bleibt
vollständig unverbunden.

P10b2c4 übernimmt vollständige Import-/Delete-Inventarreconciliation,
Reihenfolge aller Recoveryjobs, Abschlussinventar und Integration vor READY.
Dieser Slice erledigt nur den ausdrücklich benannten Deletejob. Kein Startup-
Cleanup und keine allgemeine Actionlöschung/Retentionimplementierung.
Schema 11, Entities/DDL/Trigger, Kryptografie, Auth, Permissions/Manifest,
Dependencies, ProductRuntime und UI bleiben unverändert. Kein Commit/Push.

## Vollständige Dateiliste

- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDelete.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentFoundationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImportStagingTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPublicationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentDeleteTest.kt`
- `tools/p10b2c3/check_delete_sql.py`
- `docs/product-p10b2c3-documents-delete.md`

## Abschlussergebnis

Finaler fokussierter Gradle-Lauf: **29 neue Hosttests**, 0 Failures,
0 Errors, 0 Skips, BUILD SUCCESSFUL. Gezielte Data-/KSP-/Room-Kompilierung und
automatisch abhängige Architekturprüfung erfolgreich. Separat **4 neue
Host-SQLite-Fälle grün**. Schema-/Entity-/DDL-/Triggervergleich gegen `8967a86`
unverändert; keine neue Dependency/Permission, Auth-/Security- oder Runtimeänderung.
`git diff --check` und zusätzliche Whitespaceprüfung neuer Dateien ohne Befund.
Working Tree enthält ausschließlich die neun oben genannten Dateien.

A) GRÜN – BEREIT FÜR REVIEW
