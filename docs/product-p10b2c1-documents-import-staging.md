# P10b2c1 — autorisierte Importreservierung und verschlüsseltes Staging

## Baseline und enger Umfang

Sauberer Branch `p10b2c1-documents-import-staging`, HEAD `e930d20`
(P10b2b-Merge), Schema **11**. Grundlagen bleiben
[P10a](product-p10a-documents.md), [P10b1](product-p10b1-documents-storage-design.md),
[P10b2a](product-p10b2a-documents-db-foundation.md) und
[P10b2b](product-p10b2b-documents-file-storage.md).

Dieser Slice verbindet intern genau IMPORT_RESERVED → IMPORT_WRITING →
IMPORT_CLOSED. Kein finaler Document-Insert, FILE_PUBLISHED, Rename, Delete,
Recovery, SAF, Runtimeanschluss, UI oder Viewer. Eine technische CLOSED-Quittung
ist kein erfolgreicher Produktimport und keine Publication.

## Komponenten und Reihenfolge

`DocumentImportStaging` erhält einen gültigen READY-StorageContext, Action-ID,
normalisierten Namen, ein freigegebenes Format und eine flüchtige Quellfactory.
Der spätere Aufrufer muss die ausdrückliche Bestätigung in einer neuen gültigen
READY-Session nach dem Picker sicherstellen. Hier gibt es keinen Pickercallback
oder automatische Wiederverwendung einer früheren Quelle.

1. OPERATION, Generation, READY und Name prüfen; Namespace-Lane betreten.
2. Kurzen DB-Inventarsnapshot lesen, Bindungen und Kapazitätsintegrität global
   prüfen. Zurückgegeben werden ausschließlich technische Namen ohne Keys.
   Unterbrochene RESERVED/WRITING-Jobs oder andere noch zu reconciliierende
   Phasen blockieren neue Arbeit. Bestehende CLOSED-Stagings werden erhalten.
3. Vier unabhängige UUIDs für Job, Dokument, Staging und finalen Blob sowie einen
   unabhängigen 32-Byte-DEK erzeugen. `DocumentDao.reserveImport` unverändert
   wiederverwenden: persistierter Actionstatus, globale Bindungen, atomare
   action-lokale Kapazität, Insert und COMMIT. Keine doppelte Reservierungslogik.
4. Nach erfolgreicher dauerhafter Reservierung in einer eigenen kurzen
   WriteTransaction den vollständigen erwarteten Job erneut prüfen und RESERVED
   → WRITING committen. Keine Dokumenten-Dateioperation vor diesem Schritt.
5. Room und DB-Key-Lease sind beendet. Privaten Root prüfen/gegebenenfalls im
   definierten Erstfall vervollständigen. Exakt das reservierte Stagingziel
   exklusiv öffnen. Erst danach Quelle genau einmal öffnen und mit den
   unveränderten P10b2b-Primitiven streamend verschlüsseln.
6. Tink-Abschluss, Flush, Dateisync und Close müssen erfolgreich sein. Gesamtes
   bekannte Inventar einschließlich neuem Staging erneut prüfen.
7. Neue kurze WriteTransaction: aktuellen Job einschließlich Key, Namen,
   Action-/Dokument-/Job-ID, Phase und Metadaten gegen Expected vergleichen;
   zulässigen persistierten Actionstatus erneut prüfen. WRITING → CLOSED und
   tatsächliche Größe zusammen schreiben, COMMIT-Gate prüfen.
8. Abschließend Generation/STEP prüfen. Keine Erfolgsnachricht an UI, kein
   `StorageContext.publish`, kein automatisches Öffnen.

`SqlCipherDocumentImportDatabase` nutzt ausschließlich bestehenden
`ActionDatabaseAccess`, `ProductInventory` und `SessionDbKeyOwner`. Jede Methode
beendet ihre Room-Instanz und Key-Lease vor Rückkehr; keine Room-Instanz und kein
DB-Key-Lease über Provider-/Ciphertext-I/O. B4s bestehender Lifecycle mit
queryCoroutineContext/Background-Fault-/Revocation-Behandlung bleibt erhalten.
Der Inventarsnapshot verwendet die generierte Room-`@Transaction` direkt,
keine verschachtelte Write-Anforderung innerhalb einer ReadTransaction.

`DocumentDao.advanceImport` friert die Expected-Keykopie vor suspendierenden
Reads ein. Alle gelesenen kontrollierten Inventar-/Expected-Keykopien werden
bestmöglich gewiped. Atomare Phasenupdates verwenden zusätzlich erwartete ID
und alte Phase im WHERE. Bestehende Schema-11-Trigger bleiben unverändert.

## Kapazität, Schlüssel und Sichtbarkeit

RESERVED und WRITING reservieren weiterhin 25 MiB und einen Slot. Erst der
CLOSED-Commit reduziert auf die tatsächliche Größe. Bestehende Regeln für
100 Dokumente/250 MiB einschließlich pending Beständen bleiben bestehen.
Ablehnung erzeugt keinen neuen Job, keine Dokumenten-Datei und keinen Quellread.

DEK nur als kurzlebige kontrollierte Kopie im Prozess und persistent im
SQLCipher-Job. Keine Schlüsseldatei, neue Hierarchie, Authentifizierung oder
Keystore-Alias. Bestehender Tink-Cipher und AES256_GCM_HKDF_1MB unverändert.
AAD ausschließlich opaque documentId als UTF-8. Keine URI oder Providerangabe
wird persistiert; die Quellfactory wird weder gespeichert noch erneut geöffnet.

Noch keine documents-Row: Summary/List bleiben ohne den angefangenen Import.
CLOSED bedeutet hier ausschließlich erfolgreich abgeschlossene Verschlüsselung
mit bekannter Größe. Vollständige Decryption-/EOF-/Formatprüfung ist weiterhin
zwingend vor späterer Publication. Sie wird hier nicht als erledigt behauptet.

## Rootanlage und Pfadgrenzen

Verwendet wird ausschließlich der vertrauenswürdige noBackupFilesDir mit
`pag-documents-v1/staging/` und `pag-documents-v1/files/`. Es gibt kein `mkdirs`.

Ein vor der eigenen Reservierung nachweislich leerer DB-Dokumenten-/Jobbestand
ist unter derselben exklusiven Lane Voraussetzung für die erstmalige Anlage.
Die eigenen inzwischen committed RESERVED/WRITING-Schritte sind keine fremden
Bestände und ersetzen diesen Leerheitsnachweis nicht. Nach WRITING darf der
fehlende Root komponentenweise mit `createDirectory` angelegt werden.

Ein partieller Root darf unter genau diesem Erstvertrag nur aus bekannten,
leeren staging/files-Verzeichnissen bestehen; nur dann wird er vervollständigt.
Unbekannte Einträge, Symlinks und unerwartete Unterverzeichnisse blockieren.
Bestehen bereits Referenzen, verlangt die Inventarprüfung exakt die erwarteten
Dateien; fehlender Root oder Bestand wird nicht neu angelegt. Ein nach einem
Crash verbliebener WRITING-Job sperrt den neuen Pfad bis zur späteren Recovery,
auch bei nur teilweise angelegtem Root. Dieser Slice nimmt ihn nicht wieder auf.

Kanonische Pfade, No-Follow-Attribute und vorhandene P10b2b-Inventarprüfung
bleiben maßgeblich. Das Staging wird über `FileChannel.open` mit
`CREATE_NEW`, `WRITE`, `NOFOLLOW_LINKS` angelegt: weder Überschreiben noch
Symlink-Follow des Blattziels. Erneute Pfad-/Inventarprüfung vor Quellread bzw.
CLOSED. Unbekanntes wird niemals gelöscht. Erkennt die Prüfung erst nach dem
WRITING-Commit einen unklaren Bestand, bleibt genau dieser Job WRITING;
keine Quellöffnung und keine Reparatur.

Wie P10b2b beansprucht dies keinen universell racefreien Mehrkomponenten-Open
gegen privilegierte OS-Manipulation. Alle künftigen eigenen Namespace-Mutatoren
müssen dieselbe Lane verwenden; keine unkoordinierte Runtimefreigabe.

## Sync und Fehler

`SyncOnClose` besitzt den offenen FileChannel. Tinks Close ruft den Wrapper auf:
Flush → `FileChannel.force(true)` → Channel-Close in finally. Weitere Close-Aufrufe
sind idempotent. Es gibt keinen Sync auf einem bereits geschlossen behaupteten
FileDescriptor. Fehler aus Write, Flush, Tink-Close, Source-Close, force oder
Channel-Close unterbrechen vor CLOSED. Nicht unterstütztes/fehlgeschlagenes
force wird nicht still als Erfolg behandelt. Keine allgemeine Power-loss- oder
Verzeichnis-Durability-Garantie; reale Android-Eigenschaften bleiben zu prüfen.

Providerabort, ungültiges Streamverhalten, Grenzüberschreitung und Fehler nach
Dateianlage erhalten Job WRITING und vorhandenen partiellen Ciphertext.
Kein Cleanup, Retry, Quell-Re-Read oder fachlicher Erfolg. Fehler vor WRITING
lassen höchstens einen RESERVED-Job zurück. Bei verweigertem COMMIT rollt die
betreffende DB-Transaktion zurück. Hat ein Commit bereits stattgefunden und
verweigert der anschließende Contextcheck die Rückkehr, darf kein Rollback
behauptet werden: Nach Reauth muss später der tatsächliche Bestand neu gelesen
werden. Keine URI-basierte Fortsetzung.

Revoke prüft vorhandene OPERATION-/STEP-/COMMIT-Gates und Generation, ohne
Deadlineverlängerung. Bereits begonnener physischer Close/Sync kann enden,
erlaubt aber keinen weiteren Phasencommit. Keine I/O-Arbeit hält den
SessionCoordinator-Monitor. Neutrale bestehende Storagefehler enthalten keine
ursprünglichen Provider-/Pfad-Causes oder sensiblen Inhalte. Keine Logs.

## Serialisierung und deaktivierter Produkteinstieg

`DocumentNamespaceLane` serialisiert Prozess-lokal den gesamten DB-/Dateiablauf
auf einem separaten Monitor. Ein Admission-Ticket wird vor dem Warten erfasst.
`drainAndFence` wartet auf laufende Arbeit, erhöht die Epoch und hält dieselbe
Lane während einer später übergebenen Maintenance-Arbeit. Alte wartende Tickets
werden verworfen. Damit kann nach einem solchen Fence kein zuvor zugelassener
Worker nachträglich eine Datei erzeugen.

Es gibt in diesem Slice keinen Maintenance-/Runtimeanschluss und keine
Recoveryfunktion. Vor einer Produktfreigabe müssen Sessionrevoke, Workerdrain,
Maintenance und alle zukünftigen Mutatoren tatsächlich an diese gemeinsame Lane
gebunden und auf Android nachgewiesen werden. Die Epoch ersetzt keine
Sessionfreigabe und keinen OS-/Mehrprozesslock. **Produkteinstieg bleibt
vollständig deaktiviert**; keine Verwendung durch ProductRuntime oder UI.

## Fokussierte Prüfungen

```sh
./gradlew :data:testDebugUnitTest --tests '*DocumentImportStagingTest' --offline
python3 tools/p10b2c1/check_import_staging_sql.py
```

Die neue Host-Testklasse verwendet die echten DAO-Validierungsmethoden mit einem
speichergestützten DAO-Adapter und explizit simuliertem Transaktionsrollback.
Dateitests verwenden nur isolierte temporäre Verzeichnisse, echte Tink-Streams
und FileChannel.force. Keine Produktdaten und keine Klartextdateien.

Abgedeckt sind:

- genau ein RESERVED-Job vor WRITING, keine Rootanlage vor WRITING und
  kein Quellread vor exklusiver Staginganlage;
- Lifecycle-/Commit-/Budgetablehnung, 100 vorhandene Einträge ohne weitere Datei
  oder Quellread sowie konservatives 250-MiB-Reservierungsbudget;
- unabhängige technische Bindungen, Ciphertext statt Klartext, korrekte Länge,
  genau 25 MiB und erstes überschreitendes Byte;
- force bei offenem Kanal, erfolgreicher CLOSED-Commit, Provider-/Open-/Close-/
  Syncfehler und ungültiges Streamverhalten mit WRITING-Erhalt;
- Revoke während Lesen/vor CLOSED-COMMIT, geänderte Schlüsselbindung oder
  DELETION_PENDING verhindern CLOSED;
- vorhandenes Ziel bleibt unverändert, unbekannter/symlinkender Root blockiert,
  bekannter leerer Teilroot wird nur beim DB-Erstzustand vervollständigt;
- fehlender Root bei vorhandener Referenz bleibt fehlend; zweiter erfolgreicher
  Import erhält das erste CLOSED-Staging;
- keine documents-Row, kein FILE_PUBLISHED, kein Publishcallback;
- laufende Lane-Arbeit wird vor Maintenance gedraint; alte wartende Tickets
  werden anschließend abgelehnt.

Der separate kleine Host-SQLite-Lauf verwendet das neue produktive UPDATE und
die unveränderten Schema-11-Constraints: erwartete Phase/Größe, Rollback vor
CLOSED sowie Ablehnung von Skip/Übergröße. Wiederverwendet wird ausschließlich
der bestehende Fixtureaufbau, nicht die gesamte historische Testsuite.

Hostadapter/Host-SQLite sind **kein Android-SQLCipher-/Room-Runtime-Nachweis**.
Gezielte Data-/KSP-Kompilierung prüft das generierte Room-DAO und unveränderten
Schemaexport. Der anfängliche Testadapter-Rückgabetypfehler wurde behoben.
Keine historische Unit-Suite, B1–B4-Matrix, Instrumentation, Galaxy/ADB, Lint oder
App-Builds. Das vorhandene Foundation-Testdouble erhielt nur die wegen der
neuen abstrakten UPDATE-Methode notwendige Implementierung; seine Suite wurde
nicht erneut ausgeführt.

## Übergabe an P10b2c2 / offene Nachweise

Offen bleiben vollständige Verifikation vor Publish, restartfähige Recovery,
Datei-/DB-Publication, finale Row, Rename, Delete und Runtimeintegration.
Android-spezifisch gezielt offen: reale SQLCipher-/Room-Commit-/Revokeintegration,
FileChannel-/No-Follow-/force-/Close-Fehler unter Android, Haltbarkeit nach Crash
und tatsächlich verdrahtete Worker-/Maintenance-Serialisierung. Hosttests
ersetzen diese Nachweise nicht. Kein sicherer physischer Flash-/RAM-Erase wird
behauptet.

Schema, DDL/Trigger, Securityimplementierung, Manifest, Permissions, Dependencies,
Sessionparameter, ProductRuntime und UI bleiben unverändert. Kein Commit/Push.

## Vollständige Dateiliste

- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImportStaging.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentFoundationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImportStagingTest.kt`
- `tools/p10b2c1/check_import_staging_sql.py`
- `docs/product-p10b2c1-documents-import-staging.md`

## Abschlussergebnis

Finaler fokussierter Gradle-Lauf: **20 Tests, 0 Failures, 0 Errors, 0 Skips**,
BUILD SUCCESSFUL. Data-/Room-KSP-Kompilierung und die automatisch abhängige
Architekturprüfung erfolgreich. Separat **3 Host-SQLite-Tests grün**.
Schemaexport 11, Entity-/DDL-/Triggerbestand unverändert gegenüber `e930d20`.
`git diff --check` und zusätzliche Whitespaceprüfung neuer Dateien ohne Befund.
Working Tree enthält ausschließlich die sechs oben aufgeführten Dateien.

A) P10b2c1 GRÜN – BEREIT FÜR REVIEW
