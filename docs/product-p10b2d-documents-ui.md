# P10b2d — erste sichtbare Dokumentenfunktion

## Baseline und Umfang

Branch `p10b2d-documents-ui`, sauberer Ausgangsbaum auf `2b52284`
(P10b2c4-Merge). Schema **11** bleibt unverändert. Verbindlich bleiben
[P10a](product-p10a-documents.md), [P10b1](product-p10b1-documents-storage-design.md)
und die Storageverträge aus
[P10b2c1](product-p10b2c1-documents-import-staging.md),
[P10b2c2](product-p10b2c2-documents-publication.md),
[P10b2c3](product-p10b2c3-documents-delete.md) und
[P10b2c4](product-p10b2c4-documents-reconciliation.md).

Implementiert ist der sichtbare Weg Auswahl → reguläres erneutes Entsperren →
bewusstes Öffnen derselben Aktion/Dokumentenliste → Importbestätigung →
verschlüsselter Import → Liste → bestätigte PaG-Kopielöschung.
**Code-Review und Android-Freigabe sind getrennt. Die Android-Freigabe steht aus.**
Keine Viewer, Umbenennung, Export-/Sharefunktion oder Inhaltsvorschau in diesem Slice.

## Sichtbare Navigation

| Aktionsstatus | Einstieg und Datenzugriff |
| --- | --- |
| DRAFT | Kein Dokumentenbereich, kein P10-Read |
| PREPARATION | Bewusster Einstieg „Dokumente“, Übersicht ausschließlich Anzahl |
| ACTIVE | Neunte Hubkarte „Dokumente“, ausschließlich „N Dokumente“, vor Aktionsdetails |
| COMPLETED | „Dokumente verwalten“; vor Tap weder Summary/List noch Dateiöffnung/Mutation |
| DELETION_PENDING | Kein normaler Dokumentenzugriff |

Die Dokumentensummary gehört zum atomaren Workspace-Snapshot für ACTIVE-Hub
und PREPARATION-Übersicht. Erst nach allen erforderlichen Quellen publizieren;
Refresh behält den bisherigen gültigen Snapshot, Fehler erfindet keine Null.
Die bestehenden Area-Enums und Demo-Zweige bleiben unverändert. Der bewusste
Dokumenteneinstieg verwendet den bestehenden Workspace-/Cleanup-Pfad und ein
RAM-only DocumentModel, wie andere eigenständig geöffnete Fachansichten.
HubCard besitzt dafür eine optionale Dokumenten-Zielkennung und Beschriftung;
alle bisherigen Karten und der responsive ein-/zweispaltige Renderer bleiben erhalten.

Die bewusst geladene Liste enthält ausschließlich Namen, Format, tatsächliche
Klartextgröße und Importdatum. Reihenfolge `importedAt ASC, id ASC`. Kein Blobread
für Summary/Liste, keine Thumbnails und keine automatische Entschlüsselung.
Leer: „Noch keine Dokumente gespeichert.“
PDF: „PDF gespeichert · Vorschau in dieser Version nicht verfügbar.“
Auch JPEG/PNG/TEXT erhalten in diesem Slice keinen Viewer.

## SAF, Lock und erneute Bestätigung

`MainActivity` verwendet `ActivityResultContracts.OpenDocument` mit PDF/JPEG/PNG/
text/plain als **untrusted Filter**. Kein eigener Dateibrowser und keine Storage-
oder INTERNET-Permission. Es wird ausschließlich eine `content:`-URI übernommen.

1. Der ausdrückliche Importtap prüft aktuelle Aktion und READY-Generation.
2. Ein ausschließlich flüchtiger Requestcode wird mit Action-ID und alter Generation
   gebunden; nur ein ausstehender Picker pro Activity. Requestcodes werden innerhalb
   derselben Activity nicht wiederverwendet.
3. Die bestehende Session wird bereits vor dem Pickerlaunch gesperrt. Die bisherigen
   onPause/onStop/onUserLeaveHint/Focus-/Screen-Off-Regeln bleiben erhalten.
4. Die Ergebniszustellung speichert höchstens die URI in `PendingDocumentPicker`.
   Sie liest keine Metadaten/Bytes, öffnet keinen Stream und zählt nicht als Interaction.
5. Regulär neu authentifizieren. Weder Pickerergebnis noch URI sind eine Autorisierung.
   Die erste neue READY-Generation wird gebunden; weiterer Lock verwirft die Übergabe.
6. Dieselbe Aktion und „Dokumente“ bewusst öffnen. Dann „Import fortsetzen“.
7. Einen **PaG-eigenen Namen** eingeben und eines der vier Formate ausdrücklich
   auswählen. Es gibt keine Provider-Metadatenabfrage und keine automatische
   Übernahme des externen Namens. Name: trim, 1–160 Unicode-Codepoints.
8. Erst „Import bestätigen“ konsumiert die Quelle einmalig und beauftragt das
   Repository mit einem frisch erworbenen READY-StorageContext.

Die Ergebniszustellung nutzt bewusst `OpenDocument.createIntent/parseResult`
mit direktem `startActivityForResult/onActivityResult` statt einer lifecyclegebundenen
ActivityResultRegistry-Registrierung: deren ausstehende Ergebnis-Intents können
ansonsten Teil des SavedState werden. Der direkte Android-Pfad ist deprecated,
aber im unterstützten API-Bereich vorhanden. Die lokale Android-37-SDK-Quelle
(`android/app/Activity.java`, onActivityResult-Vertrag) dokumentiert die Zustellung
vor erneutem onResume. Dieser Integrationsentscheid benötigt den unten genannten
realen Lifecycle-Nachweis; Kompilierung ist kein Gerätenachweis.

Keine URI in Bundle/SavedStateHandle/Preferences/SQLCipher oder Logs, kein
`takePersistableUriPermission`. Nicht passende oder nach Activity-Neustart
verwaiste Pickerresultate werden ignoriert. Abbruch, Screen-Off, anderes Actionziel,
erneutes externes Verlassen nach Rückkehr, explizites Verwerfen/Lock und Activity-
Zerstörung entfernen die Übergabe. Die normale Credential-UI darf den eng begrenzten
Handoff bis zum ersten neuen READY behalten. Die Quelle überlebt keinen Process Death.
Der RAM-Halter ist für kurze Sessionbeobachter-/Mainthreadzugriffe synchronisiert;
kein I/O läuft unter diesem Monitor.

Verbindlicher UI-Hinweis:

> PaG erstellt eine verschlüsselte Kopie. Die Originaldatei außerhalb von PaG bleibt bestehen.

Die Originalquelle kann von einem Cloud-DocumentProvider stammen. PaG besitzt
weiterhin keinen eigenen Netzwerkpfad und schützt/löscht die externe Quelle nicht.

## Repository und vorhandene Storagekoordination

`DocumentRepository` liefert nur `DocumentAsset` mit seinen sechs bestehenden
Fachfeldern und `DocumentSummary(count)`. Kein Key, Blobname, Job, Provider oder
Quell-URI im Domainport. `DocumentSource` ist eine einmalige Streamfactory, keine
Storage-/Sessionfreigabe. `DocumentResult/DocumentFailure` trennen begrenzte
Benutzerrückmeldungen von strukturellen Storagefehlern.

`ProductRuntime.documents` verwendet den bestehenden Worker/Main-Executor,
SessionDbKeyOwner und ActionDatabaseAccess. `DocumentProductDispatch` erwirbt
READY, bindet das bestehende Lane-Ticket, prüft OPERATION/STEP und führt alle Arbeit
im Worker aus. Callback ausschließlich via `StorageContext.publish` auf Main.
Kein DB-/File-I/O innerhalb dieses kurzen Publicationcallbacks. Alte Tickets nach
Maintenance-Fence sind abgewiesen; keine zweite Lane und kein neuer Workerpool.

Summary/List prüfen den **persistierten** Actionstatus in kurzen ReadTransactions.
Sie verwenden ausschließlich die bestehenden keyfreien DAO-Projektionen, welche
Deletejobs ausblenden. Keine Dateizugriffe/Blobverifikation für diese Reads.

Import verwendet unverändert die Koordinatorreihenfolge:

- atomare 25-MiB-Reservierung mit 100-/250-MiB-Aktionsbudget;
- dauerhafter WRITING-Wechsel vor Staginganlage und Quellstreamöffnung;
- `DocumentImportStaging.stage` streamend mit zufälligem File-DEK/Tink;
- `DocumentPublication.complete` in derselben gültigen Generation: vollständige
  Stagingverifikation, bestehender No-Overwrite-Link/Sync/Unlink/Sync-Pfad,
  FILE_PUBLISHED, vollständige finale Verifikation, atomarer documents-Insert/Jobdelete;
- erst danach technisch erfolgreicher Rückgabewert und gated UI-Refresh.

Der Activity-Source verbraucht die URI atomar genau einmal. `openInputStream`
läuft erst in der autorisierten stage-Sourcefactory nach WRITING, nie im
Pickercallback/UI. Verlorene Berechtigung, Öffnungs-/Lesefehler oder Quellenabbruch
führen zu neutralem SOURCE_UNAVAILABLE ohne Pfad/Providertext und ohne Retry.
Die Datei wird nicht vollständig im RAM gesammelt; keine Klartexttempfiles.

### Nachgewiesener Integrationsfix: falsche Quelldateien

Die bisherige rein technische Strecke prüfte das Format erst nach CLOSED.
Bei einer normal falsch ausgewählten Datei hätte dies einen nicht publizierbaren
CLOSED-Bestand hinterlassen, der nach Vertrag beim Unlock blockiert bleiben muss.
Darum prüft die bestehende kleine `DocumentFormatCheck`-Implementierung jetzt
**zusätzlich während des Verschlüsselungsstreams**, vor dem CLOSED-Commit:
PDF-/JPEG-/PNG-Präfix und vollständiges striktes UTF-8 einschließlich Steuerzeichen.
Keine zweite Pipeline, neue Kryptografie, Änderung der Originalbytes oder Parserbibliothek.
Die verbindlichen vollständigen Ciphertext-/EOF-/Längen-/Formatprüfungen vor und
nach Dateiverschiebung bleiben unverändert streng.

Nur erwartete neue Quellformat-/Größen-/Providerfehler sind typisierte
`DocumentInputFailure`. Ungültige persistierte Daten, Tink-/Authentifizierungsfehler,
Ciphertext-I/O, Sync-/Ciphertext-Close- und Roomfehler bleiben Storagefehler/BLOCKED.
Eine unterdrückte zusätzliche Close-/Syncexception darf nicht als harmloser
Quellfehler maskiert werden. Grenzen der generischen bisherigen encrypt-Nutzung
und der Verifikationsfehler bleiben erhalten.

Abgelehnte Quellimporte bleiben WRITING und werden ausschließlich durch die
bestehende nächste MAINTENANCE bereinigt. Der Produktadapter merkt sich für diese
Generation rein einschränkend, dass vor weiteren Mutationen ein regulärer Unlock
nötig ist (MAINTENANCE_REQUIRED). Diese RAM-Sperre erteilt keine Rechte und ersetzt
keine DB-/Sessionprüfung. Keine neue Recovery, kein Quell-Re-Read, kein heimliches
Löschen des Stagingbestands. Ein neuer Picker sperrt ohnehin und führt wieder durch
den normalen Unlock. Nach erfolgtem Commit und anschließendem Revoke kann der
persistierte Import vorhanden sein; keine behauptete Rückabwicklung oder alte
Erfolgspublication. Nach Reauth tatsächlichen Bestand neu lesen/reconciliieren.

## Bestätigte Löschung

„Löschen“ verwendet ausschließlich den vollständigen Asset-Expected-Snapshot aus
der autorisierten Liste. Bestätigung:

> Die geschützte PaG-Kopie dieses Dokuments wird entfernt.
>
> Die ursprüngliche Datei außerhalb von PaG bleibt davon unberührt.

Abbrechen schreibt nichts. Das Repository prüft aktuellen Status und vollständige
keyfreie Metadaten erneut, dann nutzt es **DocumentDelete.delete**, dessen eigene
Transaktionen alle Bindungen nochmals prüfen. DELETE_RESERVED blendet sofort aus;
Parentdelete/PENDING sind atomar, nur bekanntes Ciphertextziel wird entfernt,
Job zuletzt. Kein direkter DAO-Delete im Produktport. Nur vollständiger Erfolg
aktualisiert die Liste und Hubsummary. Fehler simulieren keinen Erfolg.
Keine physische Secure-Erase- oder starke kryptografische Löschgarantie.

## UI-State, Session und Privacy

DocumentModel hält Liste, Auswahl, Name/Format, Bestätigungen, Loading/Busy/Error
nur im RAM. Alle Callbacks sind an Epoch/Request, Sessiongeneration, Action-ID
und **exakten** Status gebunden. Lock/Generationwechsel, Action-/Statuswechsel und
Bereichsende löschen den Zustand über den bestehenden ViewModel-Cleanup.
Nach Reauth kein automatisches Öffnen oder Fortsetzen. Bereichs-/Formwechsel
verwendet den vorhandenen ProductInputScope und Scroll-/Fokuscleanup.

Shared Formcontrols, Heading- und Selected-Semantik, bestehende 48dp-Ziele und
scrollbarer Workspace bleiben erhalten. Formatauswahl zeigt zusätzlich „✓“;
Speichern/Abbrechen sind explizit. Menschlicher TalkBack-/IME-/Font-Scale-Nachweis
steht zusammen mit den tatsächlichen UI-Gerätetests aus.

SENSITIVE, normales gültiges READY ohne Leadership-Step-up. Keine P3a-/P3b-Reads
im Dokumentenrepository, keine Personen-/P6-/P7-/P8-/P9-Verknüpfung. Kein Viewer,
Bitmapdecoder, Thumbnailcache, externer Intentviewer, Export/Sharing, Clipboard-
Dateifunktion oder Klartext-FileProvider. SessionCoordinator, Auth, Keyhierarchie,
FLAG_SECURE sowie 180 Sekunden absolut und 30 Sekunden READY-Inaktivität bleiben
unverändert. Callback/Recovery/File-I/O verlängert keine Session.

## Tatsächliche Tests und Kompilierung

Nur synthetische Daten und isolierte Host-Dateiverzeichnisse. Keine historische
Gesamtsuite oder B1–B4-Matrix.

- **36 neue Hosttests bestanden:** DocumentModelTest (12), DocumentWorkspaceTest (6),
  DocumentProductStorageTest (11), DocumentProductDispatchTest (7).
- **63 direkt betroffene Regressionstests bestanden:** ActiveWorkspaceTest (20),
  MaterialWorkspaceTest (12), ExpenseWorkspaceTest (10), MemoryReportWorkspaceTest (8)
  sowie DocumentFileStreamsTest (13).
- **2 neue Host-SQLite-Fälle bestanden:** keyfreie Produktprojektionen und
  Reihenfolge/Deletejob-Sichtbarkeit mit echten Schema-11-Constraints.
- App-/Data-/KSP-/Room-Kompilierung, Demo-Kotlin-Kompilierung,
  App-/Data-AndroidTest-Kompilierung, verifyArchitecture und bestehende
  Produkt-/Demo-Manifestprüfungen erfolgreich.

Neue Tests prüfen u. a. keine Reads vor Completed-Einstieg, DRAFT-Sperre, atomare
Hubsummary ohne Fake-0/Dateinamen, Snapshot-Erhalt bei Refreshfehler, genau neun
Karten, Namens-Codepointgrenze, vier Formate, einmaligen Handoff erst in neuer
Generation, explizite Bestätigung, Abbruch ohne Write, Lock/Action/Status/Epoch-
Cleanup, stale Callbacks, Delete-Expected/Bestätigung/Abbruch/Fehler, Quellenöffnung
nur nach WRITING ohne offene DB, alle vier Formate bis zum finalen Commit,
25 MiB und erstes Byte darüber, ungültiges UTF-8, Provider-/Permissionabbruch,
keine sichtbare halbe Row, tatsächlichen ciphertext-only Bestand, neutrale Fehler,
Syncfehlerdominanz, reine Workerarbeit, Publication nach Commit und Revoke/Fence.

Die erste Testiteration fand einen rekursiv inferierten Testfixturetyp und einen
unzulässigen Budget-Testaufbau mit bereits unterbrochenen Jobs; korrigiert wurden
die Fixtures. Der SQL-Reihenfolgentest versuchte zunächst eine zurecht verbotene
Änderung von importedAt; er erzeugt nun verschiedene Importzeiten über den normalen
Parentinsertpfad. Keine Produktionsconstraints zur Testanpassung abgeschwächt.
Die bestehende generische Größenfehler-Semantik wurde beim Streamingreview erhalten.

Reproduzierbar, fokussierte Befehle:

```sh
./gradlew :app:compileDebugKotlin :data:compileDebugKotlin --offline
./gradlew :app:testDebugUnitTest --tests '*DocumentModelTest' --tests '*DocumentWorkspaceTest' --tests '*ActiveWorkspaceTest' --tests '*MaterialWorkspaceTest' --tests '*ExpenseWorkspaceTest' --tests '*MemoryReportWorkspaceTest' :data:testDebugUnitTest --tests '*DocumentProductStorageTest' --tests '*DocumentProductDispatchTest' :app:compileDebugAndroidTestKotlin :data:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
./gradlew :data:testDebugUnitTest --tests '*DocumentProductStorageTest' --tests '*DocumentProductDispatchTest' --tests '*DocumentFileStreamsTest' :app:compileDebugKotlin :data:compileDebugAndroidTestKotlin --offline
./gradlew :app:compileDemoKotlin --offline
python3 tools/p10b2d/check_document_projections.py
```

## Android-Freigabe ausdrücklich offen

ADB-Geräteliste außerhalb der Sandbox geprüft: **kein Gerät/Emulator verbunden**.
**0 Gerätetests ausgeführt.** AndroidTest-Quellen einschließlich der gezielten
P10b2c4-SQLCipher-/Filesystemnachweise wurden kompiliert, nicht ausgeführt.
Kein Wipe, keine Credentialänderung und kein Zugriff auf echte Produktdaten.

Vor Produkt-/Android-Freigabe tatsächlich nachzuweisen:

- Systempicker-Rückkehr und bestehender Lock, normale neue Credentialauth,
  RAM-Handoff ohne SavedState, einmaliger URI-Zugriff inkl. Berechtigungsverlust,
  Activity-Neustart/Prozessverlust/erneutes Verlassen und alte Resultate;
- realer sichtbarer Produktweg Import → SQLCipher-Commit → Liste → bestätigte
  Löschung → erneut entsperren, mit synthetischen Dateien aller vier Formate;
- aus P10b2c4: echte Room-/SQLCipher-Commit-/Rollback-/Revoke-/Closepfade,
  Android Os.link No-Overwrite, No-Follow/Unlink, Verzeichnissync und Wiederanlauf
  bekannter unterbrochener Import-/Delete-Jobs;
- reale ProductScreen-Navigation/Responsiveness, Font Scale 1/2, IME und TalkBack.

Hosttests ersetzen diese Nachweise nicht. Kein allgemeiner Power-Loss-/SIGKILL-
Durabilitynachweis, keine Garantie eines zeitnah endenden blockierenden externen
Providerreads. Nach Revoke keine neue autorisierte Arbeit/Publication; physischer
Ressourcenabschluss kann technisch noch auslaufen.

## Vollständige geänderte Dateiliste

- `domain/src/main/kotlin/org/pag/domain/Documents.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductDocumentRepository.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileStreams.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImportStaging.kt`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentPickerHandoff.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PagShell.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentModelTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentWorkspaceTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentProductStorageTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentProductDispatchTest.kt`
- `tools/p10b2d/check_document_projections.py`
- `docs/product-p10b2d-documents-ui.md`

Schema/Entities/DDL/Trigger/Migrationen, Security-/Authimplementation,
Dependencies/Permissions/Manifest und historischer Demo-/Prototypcode unverändert.
Keine neue Keyhierarchie oder Authentifizierung. Kein Commit. Kein Push.
