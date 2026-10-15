# P10b2c2 — vollständige Verifikation und atomare Dokumenten-Publication

## Stand und Umfang

Baseline: sauberer Branch `p10b2c2-documents-publication`, HEAD `13d5ee3`
(P10b2c1-Merge), Schema **11**. Verbindlich bleiben
[P10a](product-p10a-documents.md), [P10b1](product-p10b1-documents-storage-design.md),
[P10b2a](product-p10b2a-documents-db-foundation.md),
[P10b2b](product-p10b2b-documents-file-storage.md) und
[P10b2c1](product-p10b2c1-documents-import-staging.md).

Implementiert ist der interne normale CLOSED-Publication-Pfad. Kein
ProductRuntime-/UI-Anschluss, kein Picker, Viewer, Delete, Cleanup oder
vollständiger Recoverylauf. Eine technische Rückkehr aus `complete` ist keine
UI-Publication; es gibt keinen `StorageContext.publish`-Aufruf.

## Komponenten und Reihenfolge

`DocumentPublication.complete` nimmt ausschließlich eine technische Job-ID und
einen gültigen READY-StorageContext entgegen. Eine StagingReceipt wird nicht
akzeptiert. Die bestehende `DocumentNamespaceLane` bleibt für den gesamten
Ablauf exklusiv belegt; alte Admission-Tickets scheitern nach einem Fence.
Kein SessionCoordinator-Monitor wird über I/O gehalten.

1. READY, OPERATION, STEP und Generation prüfen. Einen konkreten CLOSED-Job in
   kurzer Room-Transaktion laden. Alle Dokument-/Jobbindungen und Kapazitäten,
   den persistierten Actionstatus, Format/Name/Größe/Key und fehlenden Parent
   validieren. Andere unterbrochene RESERVED/WRITING-/Deletephasen blockieren.
2. Room und DB-Key-Lease schließen. Das vollständige bekannte Dateiinventar
   muss exakt passen: CLOSED-Jobs nur im Staging, PUBLISHED-Jobs und fertige
   Dokumente nur unter files. Kein unbekannter, fehlender oder doppelter Bestand.
3. `DocumentFileInventory.verifyExisting` für das konkrete Staging verwenden:
   vollständige Tink-Decryption, authentifiziertes EOF, exakte Größe, hartes
   25-MiB-Limit und PDF-/JPEG-/PNG-/strikte UTF-8-Prüfung. Keine MIME-/Extension-
   Freigabe und keine Klartextweitergabe. Streams vollständig schließen.
4. Nach dieser möglicherweise langen Prüfung den CLOSED-Job inklusive
   Expected-Bindung und Actionstatus erneut aus der DB autorisieren; Room und
   DB-Key-Lease wieder schließen. Inventar erneut prüfen.
5. Ciphertext ohne Überschreiben ins finale Verzeichnis überführen, wie unten
   beschrieben. Anschließend ausschließlich finalen Bestand erwarten.
6. Kurze WriteTransaction: unveränderten CLOSED-Job, Actionstatus, Bindungen und
   Kapazität erneut prüfen; ausschließlich CLOSED → FILE_PUBLISHED setzen,
   Größe unverändert lassen, COMMIT-Gate beachten. Noch kein documents-Insert.
7. Frischen FILE_PUBLISHED-Snapshot laden. Finales Inventar und den endgültigen
   Blob erneut vollständig verifizieren; das alte Verifikationsergebnis wird
   nicht als Dateibeweis oder Commitrecht wiederverwendet.
8. Nach Stream-Close erneut Inventar/Session prüfen. In genau einer kurzen
   WriteTransaction den vollständigen FILE_PUBLISHED-Expected-Snapshot,
   Actionstatus und Kapazität prüfen, documents-Row mit denselben IDs/Namen/
   Metadaten/DEK und repositoryeigener nichtnegativer `importedAt` anlegen und
   den Importjob entfernen. COMMIT-Gates vor Mutation und am Transaktionsende.
9. Abschließend Sessiongeneration prüfen. Keine externe Erfolgsnachricht.

`DocumentPublicationDatabase` ist ausschließlich eine interne technische
Schnittstelle. `SqlCipherDocumentImportDatabase` implementiert sie zusätzlich
zum vorhandenen Stagingport und verwendet unverändert `ActionDatabaseAccess`,
`SessionDbKeyOwner` und den B4-erprobten Room-Lifecycle. Jede Methode beendet
Room und DB-Key-Lease vor ihrer Rückkehr. Keine Room-Instanz über File-I/O.

`DocumentPublicationSnapshot` hält eine kontrollierte DEK-Kopie nur im
`use`-Scope; sämtliche kontrollierten Inventar-/Snapshot-/Insert-Keykopien
werden im finally bestmöglich überschrieben. Scheitert bereits die Rückkehr aus
ActionDatabaseAccess oder Key-Owner nach einem Snapshot, verwirft der Adapter
auch diese noch nicht übergebene Kopie. Keine DEKs im Domainport, keine
Keystrings, Logs oder zusätzliche persistente Keydatei. Tink bleibt unverändert;
AAD ausschließlich documentId als UTF-8. Keine RAM-Overwrite-Garantie.

## Konkrete No-Overwrite-Entscheidung

`Files.move(..., ATOMIC_MOVE)` genügt nicht: Laut
[Android-Files-API](https://developer.android.com/reference/java/nio/file/Files#move(java.nio.file.Path,%20java.nio.file.Path,%20java.nio.file.CopyOption...))
ist bei bestehendem Ziel implementationsabhängig, ob ersetzt oder abgebrochen
wird; die Option ignoriert andere Moveoptionen. Auch ein vorgeschaltetes
Existenzchecken beweist keine exklusive Zielanlage. Der Produktpfad verwendet
**kein Files.move und keinen Rename-/Copy-Fallback**.

Stattdessen bietet `AndroidDocumentPublishFileOps` folgende enge Operationen:

1. `Os.link(staging, final)` erstellt einen Hardlink auf denselben Ciphertext.
   [Android Os.link](https://developer.android.com/reference/android/system/Os#link(java.lang.String,%20java.lang.String))
   ist seit API 21 verfügbar (Projekt-minSdk 30). Die dokumentierte
   [Linux-link-Semantik](https://man7.org/linux/man-pages/man2/link.2.html)
   liefert bei bestehendem Ziel EEXIST, statt es zu ersetzen. Dies schützt auch
   vor einer Kollision, die erst direkt während der Linkoperation entsteht.
2. Ziel und Quelle müssen regulär, kanonisch, ohne Symlinks und gemäß
   BasicFileAttributes dieselbe nicht-null Dateidentität mit gleicher Länge/
   Änderungszeit sein. Beide Namen gehören demselben autorisierten Job.
3. Finale Verzeichnisänderung synchronisieren.
4. Nur den exakt bezeichneten Stagingnamen mit `Files.delete` entfernen.
5. Staging-Verzeichnis synchronisieren und erneut exakt final-only prüfen.

Das ist eine **nicht atomare Ciphertext-Verschiebung über Link und Unlink**,
keine Kopie und keine neue Verschlüsselung. Die Zielanlage selbst ist exklusiv.
Nicht unterstützte Hardlinks, Cross-Device-Lagen oder andere Fehler brechen ab;
kein stiller Fallback. Es wird keine allgemeine Mehrkomponenten-Racefreiheit
gegen privilegierte OS-Manipulation behauptet. Voraussetzung bleiben privater
Namespace und dieselbe Lane für alle eigenen Mutatoren, einschließlich späterer
Maintenance. Die bestehenden kanonischen/No-Follow-Inventargrenzen gelten weiter.

Zwischen Link und Unlink bestehen absichtlich kurz zwei bekannte Namen desselben
Inodes. Das ist kein akzeptierter Endzustand: Fehler/Revoke/Crash können diese
Dateilage bei CLOSED hinterlassen; dann wird blockiert und nichts automatisch
entfernt. P10b1s Regel „beide vorhanden → BLOCKED“ bleibt bestehen. Spätere
Recovery muss diesen Fall ausdrücklich behandeln; dieser Slice repariert ihn
nicht. Es wird keine neue persistente Zwischenphase eingeführt.

## Sync und Haltbarkeitsgrenze

Verzeichnissync nutzt öffentliche Android-APIs: `Os.open` mit O_RDONLY und
O_NOFOLLOW, anschließend `Os.fstat`/S_ISDIR zur Descriptor-Typprüfung,
`Os.fsync` und unbedingtes `Os.close`. Damit erfolgt Sync bei offenem Descriptor.
Fehler werden nicht als Erfolg übersprungen. Zuerst files synchronisieren,
danach Stagingnamen entfernen und staging synchronisieren; erst dann ist der
FILE_PUBLISHED-Commit zulässig. Die Datei selbst wurde bereits durch P10b2c1
nach Tink-Close bei offenem Kanal geforced.

Verzeichnis-fsync und link sind öffentliche Android-/Linux-Primitiven; ihre
konkrete Unterstützung auf dem privaten Zieldateisystem muss vor Runtimefreigabe
auf Android geprüft werden. Ohne Unterstützung scheitert der Pfad geschlossen.
Keine universelle Power-Loss-Durability-, Flash- oder Secure-Erase-Garantie.
Hosttests benutzen echte Linux-Hardlinks und FileChannel.force auf Verzeichnissen;
dies ersetzt nicht die Android-Os-Ausführung.

## Atomarer finaler DB-Schritt

`DocumentDao.commitPublishedDocument` führt Parentinsert und Jobdelete unter
einer Room-WriteTransaction aus. `documents_import_parent` bleibt unverändert
aktiv: der passende FILE_PUBLISHED-Job besteht zum Insertzeitpunkt. Erst danach
wird er in derselben Transaktion entfernt. Nach Commit genau eine documents-Row,
kein Job und ausschließlich finaler Ciphertext. Keine separat committed
Schlüsselduplikation. Insert-/Delete-/COMMIT-Fehler rollen den gesamten DB-Schritt
zurück und erhalten den FILE_PUBLISHED-Job. DDL, Entities, Trigger und Schemaexport
bleiben unverändert auf 11.

Interne DAO-Mutatoren sind kein unabhängiger Produktworkflow. Nur der Koordinator
verbindet Dateiverifikation und DB-Schritte. Kein neuer Repositoryport oder
öffentlicher Insert, keine zweite Datenbank, Authentifizierung oder Keyhierarchie.

## Fehler und Crashzustände

| Grenze | Erhaltener Zustand, kein Erfolg |
| --- | --- |
| Staging/AAD/Key/Format ungültig | CLOSED, Staging bleibt; kein Link |
| Link fehlgeschlagen/Zielkollision | CLOSED; bestehendes Ziel unverändert |
| Fehler zwischen Link und Unlink | CLOSED, ggf. beide Namen; BLOCKED |
| Unlink erledigt, Sync/Phasencommit scheitert | CLOSED + final-only möglich |
| Finale Verifikation fehlschlägt | FILE_PUBLISHED, keine documents-Row |
| Finaler Insert/Jobdelete/COMMIT scheitert | FILE_PUBLISHED, kein halber Parent |
| Finaler Commit erfolgt, danach Revoke | Row darf bestehen; keine veraltete Erfolgsrückkehr, kein behaupteter Rollback |
| Unbekanntes/Symlink/Bindungsfehler | Neutraler Storagefehler; keine Reparatur |
| Zweite Invocation | Aktueller Job muss CLOSED sein; fehlender/anderer Job wird abgelehnt, kein erneutes Verschieben oder zweiter Parent |

Generation und OPERATION-/STEP-/COMMIT-Gates bleiben durchgängig erforderlich;
Job- und Dateiexistenz autorisieren nichts. Datei- und DB-Arbeit verlängern
keine Session. Kein Systemcallback ist User Interaction. Revoke nach einer
bereits erfolgten Dateimutation kann die physische Lage verändert hinterlassen.
Kein Zurückverschieben, Löschen, Quell-Re-Read oder blindes Retry.

## Tatsächliche fokussierte Prüfungen

```sh
./gradlew :data:testDebugUnitTest --tests '*DocumentPublicationTest' --offline
python3 tools/p10b2c2/check_publication_sql.py
```

Neue Host-Testklasse: echte Tink-Verifikation an beiden Orten, Linux-Hardlink/
Unlink und Verzeichnissync in isolierten Testverzeichnissen. Echte DAO-
Validierungsmethoden mit speichergestütztem DAO und explizit simuliertem
Transaktionsrollback, nicht echter Room-/SQLCipher-Runtime.

Abgedeckt: vollständiger Abschluss und Keywipe, beschädigtes/trunkiertes Staging,
falsches AAD/Key/Format, bestehendes und während link entstehendes Ziel,
unbekannte Dateien/Symlinks, Link-/Syncfehler, CLOSED + final-only, Beschädigung
nach FILE_PUBLISHED, Insert-/Delete-Rollback, Revoke vor/nach Commit, doppelte
Invocation, Actionstatus-/Expected-Metadatenwechsel, ungültige Commitzeit und
alte Lane-Tickets nach Fence. Keine externe Publication.

Vier kleine Host-SQLite-Fälle verwenden bestehende Schema-11-Constraints und
die neue Jobdelete-Query: atomarer Keytransfer, Deletefehler mit Parentrollback,
Insertfehler/Commitverweigerung sowie nicht umgehbarer Importparent-Trigger.
Nur Fixtureaufbau wird wiederverwendet; keine historische Suite.

Der anfängliche Kompilierungslauf fand nicht öffentliche/nicht verfügbare
SDK-Namen für unlink/O_DIRECTORY; ersetzt durch Files.delete und öffentliche
Descriptor-Typprüfung, ohne Reflection oder eigene Konstanten. Gezielte Data-/
Room-Kompilierung prüft das erzeugte DAO. Bestehende Foundation-/Staging-
Testdoubles erhielten nur die für die neuen abstrakten DAO-Operationen nötigen
Compile-Stubs; ihre historischen Tests wurden nicht erneut ausgeführt.

## Offene Android-/Runtime-Nachweise und Übergabe

Vor Produktfreigabe gezielt auf Android nachweisen: reale Os.link-No-Overwrite-
Kollision, No-Follow-/Dateidentität, Verzeichnissync und Crashhaltbarkeit sowie
SQLCipher-/Room-Transaktionen einschließlich Insert-/Jobdeletefehler und
Revoke-Rennen. Die dokumentierten API-Semantiken begründen das fail-closed
Verfahren; Hosttests sind kein Gerätesiegel. Bei nicht unterstütztem Filesystem
bleibt es ohne Fallback gesperrt.

Runtime und Maintenance sind weiterhin nicht angeschlossen. Alle künftigen
Mutatoren müssen dieselbe Lane drainen/fencen; keine Mehrprozessgarantie.
Vollständige Recovery der erhaltenen Zustände folgt in P10b2c4, Delete separat.
Keine Cleanupfunktion, SAF/URI-Handoff, UI, Viewer, Export/Share/FileProvider,
Permission, Dependency oder Manifeständerung in diesem Slice. Kein Commit/Push.

## Vollständige Dateiliste

- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImportStaging.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentPublication.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentFoundationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImportStagingTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPublicationTest.kt`
- `tools/p10b2c2/check_publication_sql.py`
- `docs/product-p10b2c2-documents-publication.md`

## Abschlussergebnis

Finaler gezielter Gradle-Lauf: **20 Hosttests**, 0 Failures, 0 Errors,
0 Skips, BUILD SUCCESSFUL. Data-/KSP-/Room-Kompilierung sowie automatisch
abhängige Architekturprüfung erfolgreich. **4 Host-SQLite-Tests grün**.
Keine Instrumentation, Galaxy-/ADB-Kampagne, historische Gesamtsuite oder Lint.
Schema/Entity-/DDL-/Triggervergleich gegen die Baseline ohne Änderung;
`git diff --check` und Whitespaceprüfung neuer Dateien ohne Befund.
Alle acht oben aufgeführten Änderungen sind uncommitted, kein Push.

A) P10b2c2 GRÜN – BEREIT FÜR REVIEW
