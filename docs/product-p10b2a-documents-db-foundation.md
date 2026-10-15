# P10b2a — Document-Domain und SQLCipher-DB-Fundament

## Baseline und Umfang

Sauberer Ausgangsbaum auf `p10b2a-documents-db-foundation`, HEAD `5450fbe`
(P10b1-Merge), Schema 10. [P10a](product-p10a-documents.md) und
[P10b1](product-p10b1-documents-storage-design.md) bleiben unverändert.
Dieser Slice implementiert ausschließlich Domain und interne DB-Grundlagen.
Keine freigegebene Dokumentenfunktion in ProductRuntime oder UI.

## Domain und interne Datenstruktur

DocumentAsset enthält ausschließlich id/actionId/displayName/format/plaintextSize/
importedAt, DocumentSummary ausschließlich count. PDF/JPEG/PNG/TEXT, Name trim
1–160 Unicode-Codepoints, Größe 0..25 MiB, maximal 100 Dokumente/250 MiB je Aktion.
Normale DocumentRules erlauben PREPARATION/ACTIVE/COMPLETED; DRAFT und
DELETION_PENDING gesperrt, keine Änderung anderer Lifecycle-Regeln.
Keine Repository-Schreibschnittstelle, Person-/P3b-/P6-/P7-/P8-/P9-Verknüpfung.

Schema **11** ergänzt genau documents und document_jobs mit den vereinbarten
Feldern. Action-FKs RESTRICT, UNIQUE(actionId,id) und blobName für documents;
Actionindex und eindeutige documentId/blobName/stagingName für Jobs.
documents.fileKey nicht-null, exakt 32 Byte BLOB; Importjob ebenso, Deletejob
immer ohne Key. Ausschließlich persistente SQLCipher-Produkt-DB, kein Alias,
Authpfad, Key-Owner, Keyfile oder Tink-Keyset hinzugefügt.

Rowklassen mit Keys sind intern und besitzen keine datenhaltige toString-Methode.
Fachprojektionen enthalten keinen fileKey/blobName. Summary COUNT und Liste/
Einzelmetadaten schließen jeden DELETE-Job aus, bereits DELETE_RESERVED.
documents hat kein Statusfeld. Keine öffentliche READY-Insertmethode;
zusätzlicher INSERT-Trigger verlangt einen passenden FILE_PUBLISHED-Importjob.
Dies ist DB-Konsistenz, **kein Nachweis vorhandener/verifizierter Datei**.

## Reservierung und Constraints

Einzige koordinierte Writeoperation derzeit: internes reserveImport unter Room-
Transaction, bestehendem READY-/OPERATION-/STEP-/COMMIT-Vertrag und persistiertem
Actionstatus. Kein File-I/O und keine Phasenfortsetzung implementiert.
Keyinput vor suspendierenden Reads kopiert; kontrollierte Kopien einschließlich
gelesener Inventar-Keys in finally bestmöglich überschrieben. Keine Publication
oder Sessioninteraction aus DAO; spätere Koordination muss diese Gates ergänzen.

RESERVED/WRITING reservieren 25 MiB und einen Slot, CLOSED/PUBLISHED tatsächliche
Größe. Alle documents-Rows inklusive hidden + Importjobs + parentlose DELETE_FILE_PENDING
zählen; DELETE_RESERVED nicht doppelt. Checked Long-Arithmetik, korrupte Bindungen
blockieren. Globale Namen-/Actionbindungen validieren, dann action-lokales Budget.
Konservativ kann bei weniger als 25 MiB freiem Budget auch eine kleine neue Datei
abgewiesen werden, wie P10b1 festlegt.

Ein zusätzlicher atomarer INSERT-Trigger schützt Budget und Initialphase auch
gegen konkurrierende Writer: keine elf gleichzeitig maximal reservierten Jobs
oder 101. Dokumentplatz. Job-Constraints prüfen Operation/Phase, Nullkombinationen,
Keylänge, Same-Action-Deletebindung und Namenkollisionen. DB-Phasenübergänge nur
monoton entlang der vereinbarten vier Import-/zwei Deletephasen, technische
Identität/Key unveränderlich; Dokumentupdate lässt lediglich displayName ändern.
Diese Constraints implementieren keine Import-, Delete-Datei- oder Recoverypipeline.

## Migration und bestehende Architektur

ACTION_MIGRATION_10_11 rein additiv; dieselben neuen Constraints bei Fresh-DB
über Callback und bei Migration installiert. Bestehender ActionDatabaseAccess
registriert die Migration, DAO/Entities und akzeptiert Versionen 1..11.
SQLCipher, Driver, Room-Lifecycle, Auth, Key-Owner und ProductRuntime unverändert.
Schemaexport 11 erhält sämtliche **21** bisherigen Entitydefinitionen exakt;
keine Änderung der alten Triggerimplementierungen. Neue Constraints betreffen
ausschließlich die zwei neuen Tabellen.

Historische synthetische Migrationfixtures entfernen documents/document_jobs
vor Downgrade und erwarten nach Upgrade Schema 11. Table-Inventarerwartungen
berücksichtigen die zwei neuen Tabellen; alte Trigger werden weiterhin exakt
gegen ihren bisherigen Bestand geprüft, neue P10-Constraints separat im Hostcheck.
Keine historischen Gerätetests ausgeführt.

## Fokussierte Prüfungen und Grenzen

- `:data:compileDebugKotlin --offline`: gezielte Data-/Room-Kompilierung und Export.
- `:data:testDebugUnitTest --tests '*DocumentFoundationTest' --offline`: 11 Tests
  für Domain, Reservierung, Bindungen, Keykopien/Fehlercleanup und erzeugte DAO-Projektionen.
- `python3 tools/p10b2a/check_document_database.py`: 7 Host-SQLite-Fälle mit dem
  produktiven Migration-/Constraint-SQL und Roomexport. Additive Migration/alte
  Entitydefinitionen/Rows/Trigger, Fresh-vs-Migration, RESTRICT, Jobunique/Bindings,
  Key-/Phasenconstraints, Deletehidden, Grenzen und zwei konkurrierende Writer.
- `:data:compileDebugAndroidTestKotlin --offline`: ausschließlich Kompilierung
  der aktualisierten synthetischen Migrationsfixtures, keine Instrumentation.

Host-SQLite ist **kein echter Android-SQLCipher-/Room-Runtime-Test**. Der vorhandene
produktive SQLCipher-Zugriff wird wiederverwendet; dessen neue Schema-/Driver-/
Commitintegration auf Android ist später gezielt nachzuweisen. Kein vollständiger
Unitlauf, keine B1–B4-Matrix, App-/APK-/Demo-Builds, Lint, Galaxy oder ADB.
Die ersten Testhelferannahmen über optionale Schema-Indexfelder/Actiondefaults
und KSPs escaped `get` wurden korrigiert, Assertions nicht abgeschwächt.

Offen: vollständige verifizierte Importkoordination/finaler READY-Commit,
Delete-DB-/Dateikoordination, FileStreamingCipher-Nutzung, Reconciliation,
SAF-Handoff, Runtime/Projektion-Gates und UI. Unfertige DB-Jobs sind nicht
über das Produkt erzeugbar. Kein Viewer/Export/Netzwerk oder Retentionworker.
Live-DEK-Löschung ist keine starke Crypto-Erase-Garantie.
Kein Commit. Kein Push.

## Geänderte Dateien und Abschluss

- `domain/src/main/kotlin/org/pag/domain/Documents.kt`.
- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt` und `ActionDatabase.kt`.
- `data/schemas/org.pag.data.product.ActionDatabase/11.json`.
- `data/src/test/kotlin/org/pag/data/product/DocumentFoundationTest.kt`.
- `tools/p10b2a/check_document_database.py`.
- Synthetische AndroidTestfixtures unter `data/src/androidTest/kotlin/org/pag/data/product/`:
  `MaterialMigrationFixture.kt`, `ActionLifecycleStorageTest.kt`, `ExpenseStorageTest.kt`,
  `LeadershipStorageTest.kt`, `MaterialStorageTest.kt`, `MemoryReportStorageTest.kt`,
  `OperationsStorageTest.kt`, `PersonStorageTest.kt`, `ProtocolStorageTest.kt`, `TaskStorageTest.kt`.
- Dieses Dokument, `docs/data-model.md` und `docs/testing.md`.

Alle oben genannten gezielten Prüfungen erfolgreich: 11 Unit-Tests, 7 Host-SQLite-
Fälle und notwendige Data-/Fixturekompilierung. `git diff --check` ohne Befund.
Branch/HEAD unverändert, Änderungen uncommitted. Auth/Securityimplementierung,
ProductRuntime, UI, Manifest/Permissions und Gradle/Dependencies unverändert.
Keine bestehenden SQLCipher-Produktdaten geöffnet oder verändert.

A) P10b2a GRÜN – BEREIT FÜR REVIEW
