# P11 — Active Action Workspace Assessment

**Aktueller Abschluss Abschnitt30, 19.10.2026: Gesamtstatus weiterhin BLOCKER.**
Veraltete Accessibilityprojektion am Hub kontrolliert nachgewiesen; die vollständige
Ursache der alten hub-after-change-Ausfälle und der historischen status-group-
Fehler bleibt unzugeordnet. Unveränderte alte Baseline8/10; erste Diagnose-APK legacy10/10 und dritte APK
minimal10/10 getrennt bewertet, keine daraus erfundene Korrektur. Nur Tests,
Diagnose-Runner und Bericht geändert; keine produktive Stufe2/Produktänderung.
Die zwei temporär benötigten isolierten Testpakete wieder entfernt; ausschließlich
Produkt-App verbleibt im PaG-Paketbestand. Details und Grenzen:
[Abschnitt30](#30-funktionsnavigation-erreichbarkeit-und-feedback-overlay).

**Historischer Abschluss Abschnitt29, 18.10.2026: Gesamtstatus weiterhin BLOCKER.**
Aufgaben3,5: Teststeuerungsfehler eingegrenzt und testseitig korrigiert;
identische Test-APK besteht jeweils10/10 vollständige Läufe auf beiden Zielständen,
Standardalias zusätzlich bei Schrift1/2/3,5 bestanden. Funktionen: historischer
status-group-Ausfall nicht exakt reproduziert; ein späterer Hub-Erreichbarkeitsfehler
bleibt offen. Keine Produktänderung/Stufe2-Freigabe.17 Test-/Demo-Pakete auf
Benutzerwunsch entfernt, Produkt-App unverändert erhalten. Neue Belege und Grenzen:
[Abschnitt29](#29-abschlussdiagnose-aufgaben35-und-funktionenstatus).
Alle älteren Fehlversuche und Befunde unten bleiben historisch erhalten.

**Historischer Abschluss aus Abschnitt28 (18.10.2026): Gesamtregression weiterhin BLOCKER.**
P9-Addendum: veralteter Accessibility-Vorfahr nachgewiesen, minimal testseitig
korrigiert, jeweils10/10 vollständige Schrift2-Läufe auf beiden eingefrorenen
Zielständen bestanden. Aufgaben Schrift3,5 bleibt ungeklärt; zusätzliche
Funktions-Erreichbarkeitsregression im Abschlusslauf offen. Kein Produktcode
geändert, der belegte Lazy-Footer-Semantikfix bleibt erhalten. Keine Produkt-/
Stufe2-Freigabe. Ausgangspunkt dieser Runde:
`b89b7671a9d57805478c131df94c828da732297a`; ältere Stand-/HEAD-Angaben und
Commitverbote unten sind historisch. WIP-Sicherung ausdrücklich beauftragt.
Aktuelle Nachweise: [Assessment28](#28-nachprüfung-der-offenen-ui-erreichbarkeitsregressionen).
Die früheren fehlgeschlagenen Versuche bleiben in Abschnitt26/27 dokumentiert.

Stand: 17./18.10.2026. Lokaler Arbeitsbranch `p11-performance-optimization`, HEAD
`b3a8c19105e755bc3df6c7207ac0c7e3969702a0`. Grundlage ist der **uncommittete
lokale P11-Stand**, einschließlich Runde 2. Kein produktiver Stufe-2-Store,
kein Produktupdate, keine Migration, kein Commit/Push/Merge/Release.

## 1. Produktziel und Nachweisgrenzen

Eine bewusst geöffnete Aktion soll innerhalb derselben gültigen READY-Generation
weitgehend ohne erneute DB-Abfrage navigierbar sein. SQLCipher bleibt Source of
Truth. Eine zusätzliche RAM-Vorhaltung ist eine freigabepflichtige Klasse-B-
Architekturänderung: Mehr Klartext ist gleichzeitig und länger verfügbar; bei
RAM-Treffern fehlen die bisherigen erneuten DB-Preflights. Keine Gleichsetzung
von „keine Klartextdatei“ mit unveränderter Sicherheit.

Kennzeichnung: **BEOBACHTET** = Code-/Benutzerbefund; **GEMESSEN** = benannte
synthetische Messreihe; **HYPOTHESE** = zu überprüfende Erwartung;
**NOCH NICHT GETESTET** = kein Nachweis. Die isolierte Machbarkeit ist keine
vollständige unabhängige Sicherheitsprüfung. Issue #26 bleibt Release-Gate vor 1.0.

## 2. Neue Benutzerabnahme (zweite P11-Runde)

**BEOBACHTET, Benutzerangaben:** Entsperren/Aktionsliste ungefähr 3 s,
Aktionsöffnung ungefähr 2 s, Bereichswechsel ungefähr 1 s. Zurück zum Lagebild
erscheint erneut ein Ladezustand. Personen/Aufgaben leicht verzögert, akzeptabel;
Protokoll/Material/Kosten funktional korrekt; Gedächtnisprotokolle deutlich besser,
noch kleine Verzögerung. JPEG/PNG akzeptabel, UTF-8 gut, PDF deutlich verbessert
und akzeptabel. Kamera/SAF, große Dokumente und Reauthentifizierung funktionieren.
**Kein Autoopen bestanden.** Keine Layoutsprünge mehr; Ladebalken entfernt;
Fragebogen-Scrollposition korrekt; große Schrift/TalkBack vom Benutzer mit Ja bewertet.
**Sperren während laufendem Laden wurde in dieser zweiten Abnahme nicht ausdrücklich
erneut bestätigt.** Frühere automatische Tests und frühere Abnahmen ersetzen diese
fehlende erneute Bestätigung nicht. Die P11-Gesamtabnahme ist nicht abgeschlossen.

Die genannten Sekunden sind subjektive Produktbefunde, keine synthetischen
Median-/P95-Messwerte. Operativ stören insbesondere Bereichswechsel, Rückkehr und
bestehende Eingabemasken. Dieses Assessment ist keine neue Produktabnahme.

## 3. Tatsächlicher Codebestand und heutige RAM-Nutzung

Fundstellen beziehen sich auf den lokalen Stand; Dateien unter
`app/src/main/kotlin/org/pag/prototype/product/`,
`data/src/main/kotlin/org/pag/data/product/`, `domain/src/main/kotlin/org/pag/domain/`.

| Fundstelle | Befund und Abhängigkeit |
|---|---|
| `PagShell.kt: WorkspaceSnapshot` | Personenliste, OperationalBoard, optional TaskBoard und Bereichssummaries, Action-ID/Status/Generation; Leadership-State nur Metadatum. Keine fachübergreifende Version. Personenliste referenziert dieselben Personobjekte wie das OperationalBoard; zusätzliche Listenrepräsentation. |
| `ProductViewModel.kt: navigateActive`, `preparationArea`, `clearPersons` | Bereichswechsel ruft `clearPersons` auf: Workspace null, Request/Ticket widerrufen, alle Bereichsmodelle/Viewer/Grants/Entwürfe bereinigt. Danach `loadWorkspace`. Ein gemeinsamer Clear vermischt zwingende Revocation mit bisheriger Navigation. |
| `ProductViewModel.kt: readWorkspace` | Ein kurzer kombinierter Read, exakt angeforderte Teile; atomare Snapshot-Publication mit Action/Generation/Status/Request/Area-Fencing. Aktuelles Hub enthält Operations/Tasks und Summaries, keine Material-/Kostenboards, keine Protokolldetails. |
| `ProductViewModel.kt: activePersonTasks` | Enger bereits bestehender Wiederverwendungsfall: vorhandenes Operations-/Taskboard für Person→Aufgaben übernehmen; trotzdem aktueller Reload. Kein allgemeiner Cache. |
| `ProductViewModel.kt: personDetail`, `editPerson` | `getPerson` ist neuer DB-Read; bei vorhandenem Leadership-Port anschließend getrennte `inventory`. Pseudonymbearbeitung nur PREPARATION. Keine pauschale Gleichsetzung von ACTIVE-Personenfokus und editierbarer Person. |
| `TaskModel.kt: open`, `edit`, `add` | Vorhandene Aufgabe bzw. neue Maske schon RAM-basiert aus geliefertem TaskBoard; `input()` baut Formularkopie. `refresh`, Write-Erfolg laden Workspace nach. |
| `OperationsModel.kt: focus`, `update`, `plan`, `roles`, `focusFunction` | Fokus und Rollen-/Statusformulare nutzen das vorhandene Board; Cancel/Write-Rückkehr kann `refresh` auslösen. Organisatorische Rolle ist kein technisches Zugriffsrecht. |
| `ActiveWorkspace.kt`, `PreparationWorkspace.kt` | Bereichsprojektionen des jeweiligen aktuellen Snapshots. Summary ist nicht Detailboard. Filter, Sortierung, Zähler und Composable-Aufbau bleiben auch ohne DB Arbeit. |
| `ProtocolModel.kt: refresh`, `open`, `loadDetail` | Timeline getrennt von Detail/Revisionen; vorhandene Detailtexte und Formulardrafts nur nach bewusstem Einstieg. |
| `MaterialModel.kt`, `ExpenseModel.kt` | Board separat; bei kombiniertem Bereichsread nach `enter(deferRead=true)` atomar übernommen. Formulare verwenden Boardobjekte; Writes bleiben Repository-Operationen. Kosten enthalten SENSITIVE Zahlungslabels. |
| `MemoryReportModel.kt: enter`, `readList`, `readDetail`, `showSection` | Headers getrennt von Antworten/Nachträgen. Persistenter fachlicher DRAFT ist kein persistierter UI-Cache. Dirty-/Leave-Vertrag muss erhalten bleiben. |
| `DocumentModel.kt`, `DocumentRepository` | Dokumentliste/Asset-Snapshot getrennt von Bild/Text/PDF. Jeder Inhaltszugriff bleibt bewusster, vollständig authentifizierter Read; Viewer-Request und sensitive Ausgabe bei Clear widerrufen. |
| `ProductDocumentRepository.kt` | Metadatensnapshot/DEK nur im Data-Pfad, Namespace-Lane, Inventar und finale Gates; Dateilesen nicht durch Workspace ersetzen. |
| `ProductWorkspaceRepository.kt: WorkspaceStore.read` | Frische Action, optional exakter `expectedAction`, Status-/Fachregeln; bestehende Stores; kein LeadershipStore, kein P3b, keine File-DEKs. Jeder Miss öffnet vollständig preflighted DB. |
| `ActionDatabase.kt: ActionDatabaseAccess.use` | Inventar, native Öffnung/Schlüssel, Version, cipher_integrity_check, integrity_check, foreign_key_check, native Close, Room/Schema, Transaktion, vollständiges Cleanup. Schema 11. |
| `SessionDbKeyOwner.kt` | Eine Generation, kurzlebige kopierte Lease pro DB-Operation, Überschreiben im finally; kein Keygetter. Der bestehende sessionresidente DB-Key bleibt hiervon getrennt. |
| `ProductRuntime.kt`, `SessionCoordinator.kt` | Zentrale Autorität; READY/Generation/Umgebung/absolute und Idle-Deadline, StorageAccess/Publications. Bei Lock Key-/Leadership-Widerruf. Keine zweite Store-eigene Uhr oder Authentifizierung zulässig. |
| `ProductLeadershipRepository.kt`, `Leadership.kt` | Eigene passphrasen-/Grant-/Epoch-/Person-/Area-Grenze, eigene Admission/Lifetime. Weder ein normaler Workspace noch ein gecachter Leadership-State gewährt P3b-Zugriff. |
| `ProductScreen.kt`, `ProductScroll.kt` | Ruhige reservierte Ladeflächen, generations-/aktionsgebundene Scrollgeometrie. Keine persistierte Payload. Große Column-/Sortier-/Formatierarbeit kann trotz RAM-Treffer janken. |

**Zwingend erhalten:** Clear bei Lock, anderem Action-Kontext, Destroy,
Generationwechsel, Status-/Berechtigungsentzug; Viewer-/P3b-/Draft-Cleanup beim
Verlassen, Dirty-Dialoge, Request-Fencing, Refresh nach relevanten Writes.
**Zu trennen:** Navigationsreset von normalem strukturiertem Board und Widerruf
sensitiver Detail-/Grant-/Viewerzustände. Ein pauschales Entfernen von
`clearPersons()` wäre falsch. `loadWorkspace()` nach Commit ist keine unnötige
Navigation. Ein Callback darf keine neue Benutzerinteraktion erzeugen.

## 4. DB-Operationen pro normaler Navigation

**BEOBACHTET:** Mit gebundenem WorkspaceRepository je ein vollständiger DB-Read
für ACTIVE Hub→Personen→Aufgaben→Funktionen→Material→Kosten→Hub. Operations wird
jedes Mal erneut gelesen. Material/Kosten erhalten ihren Boardteil in derselben
Operation. Hub-Summaries werden bei Rückkehr wieder angefordert. Protokolltimeline
und Gedächtnisprotokollheaders werden nur bei ihrem bewussten Bereichseinstieg
angefordert. Dokumentliste bleibt eigener autorisierter Repository-Read.

Initiale Aktionsöffnung vergleicht die Action aus der Liste innerhalb desselben
kombinierten Reads; kein zweiter GetAction auf dem erfolgreichen Normalpfad.
PREPARATION-Übersicht hat im Produkt zusätzlich den separaten Leadership-State-
Read. Personen-Detail mit Leadership benötigt `getPerson` plus `inventory`;
ohne Leadership einen. Bestehende Taskmaske: null zusätzliche Reads, sobald ihr
Board vorliegt. Die Tabelle zählt **DB-Operationen**, nicht einzelne SQL-Statements;
OperationsStore und Bereichsstores enthalten jeweils mehrere Queries.

## 5. Stufe 1: enger bestehender Snapshot

Testvariante B hält ausschließlich den ersten bereits bewusst geladenen Hub-
Snapshot. Ein Bereich darf ihn nur nutzen, wenn alle angeforderten Teile vorhanden
und Bindung/Version/Berechtigung unverändert sind. Keine zusätzlichen Bereiche
vorab laden. Material/Kosten bleiben Misses; Hub/Personen/Aufgaben/Funktionen können
Treffer sein. Produktiv sind weiterhin Clear/Revocation und alle Write-Invalidierungen
explizit zu trennen; hierfür wurde **kein Produktcode verändert**.

Auch diese längere Wiederverwendung kann den nächsten Integritätscheck verschieben;
„nur vorhandener Snapshot“ ist nicht automatisch Klasse A. Rein lokale
Formularöffnung innerhalb desselben noch aktuellen Boards gibt es bereits.
Stufe 1 kann einen Teil der Navigation beschleunigen, deckt aber nicht erstmals
benötigte Boards, Header, Dokumentmetadaten oder die heutige Personen-Detailkette ab.

## 6. Stufe 2: kontrollierter Entwurf

`ActiveActionSessionStore` als instanzlokaler Read-Model-Owner für **genau eine**
Action, keine Datenregistry. Schlüssel: `(generation, actionId, actionStatus,
permissionRevision, domainRevisions, loadEpoch)`. Bindung ist kein Freigabetoken.
Jeder Einstieg und jede Publication verwenden die bestehende zentrale READY-
Storage-Autorität; Snapshots/Forms erhalten zusätzlich lokale Gültigkeitstokens.
Keine zweite Sessionverwaltung und keine neuen kryptografischen Parameter.

Die normale Navigation ist eine Projektion der ausdrücklich zugelassenen,
vorbereiteten Domain-Daten. Kein DAO, Room, Connection, DB-Key-Lease, File-DEK,
Credential, `SensitiveOpened` oder Grant im Store. Kein SavedStateHandle,
rememberSaveable für Payload, DataStore, Preferences oder Filesystem. Keine
Hintergrundbefüllung außerhalb einer bewusst geöffneten, autorisierten Aktion.

Initiale Ladephase: kurze autorisierte DB-Operation, frische Action/Fachrechte,
vorab begrenzte Mengen/Textumfänge, konsistentes erlaubtes Read-Model, DB/Lease
schließen, finale Gates, atomare Store-/UI-Publication. Nicht zugelassene oder
zu große Teilbereiche bleiben bedarfsorientiert. Kein ungültiger Teilzustand als
vollständig geladen darstellen; unbekannte Zähler nicht als null ausgeben.

Bereichsprojektionen dürfen keine Capability oder Room-Typen enthalten. Keine
alleinige Prüfung von `SessionSnapshot.state == READY`: Deadline und Umgebung
müssen aus dem Coordinator durchgesetzt werden. Read-Hits verlängern keine Frist;
nur bewusste Interaktion ruft den vorhandenen Interaktionsport auf.

## 7. Datenmatrix (Vorschlag zur Freigabe, keine heutige Freigabe)

P/A/C = PREPARATION/ACTIVE/COMPLETED, immer aktuelle READY-Generation und korrekte
Action. DRAFT/DELETION_PENDING ausgeschlossen. „Budget“ bedeutet zusätzlicher,
noch zu validierender Store-Grenzwert aus Abschnitt 11, kein neues produktives
Fachlimit. Gesamte strukturierte Aktion konservativ SENSITIVE behandeln; INTERNAL
ist keine Lockerung der Session-/Storagegates.

| Kategorie/Inhalt | Klasse; heutiger Read | Aufnahme/Ladezeit | Dauer/Maximalumfang | Invalidierung und Restrisiko |
|---|---|---|---|---|
| Aktionsstammdaten/Titel/Zeiten/Status | SENSITIVE; READY, Action | Ja, bewusstes Öffnen | Aktionsscope; Budget, derzeit Titel/Beschreibung nicht allgemein begrenzt | Jeder Actionwrite/Status/Deletion; veralteter Status darf keine Rechte erhalten |
| Personen/Pseudonyme | SENSITIVE (keine Ausweitung der engen P4-INTERNAL-Ausnahme); P/A/C | Ja, normale strukturierte Sicht | Scope; Budget; kein globales Mengen-/Pseudonymlängenlimit im aktuellen PersonRules | Create/Rename/Delete, Rollen-/Referenzprojektionen; erhöhte gleichzeitige Identifizierbarkeit |
| Personenstatus/Ortslabel | INTERNAL/personenbezogen; P/A/C-Board, Statuszustände v.a. A | Ja | Scope/Budget; Ortslabel 80 Codepoints | Status/Person/Action; Orts-/Teilnahmebezüge im RAM |
| Rollen/Funktionen/Pläne | INTERNAL; P/A/C, Planwrite P, live A | Ja | Scope/Budget, kein generelles Rollenmaximum/Name-Maximum | Role/Plan/Status/Person; organisatorische Rolle verleiht keine Apprechte |
| Aufgaben | INTERNAL; P/A/C | Ja | Scope/Budget; Titel 120 Codepoints, keine Gesamtzahlgrenze | Task/Person/Role/Status; Zähler, Assignees und Fokus invalidieren |
| TaskEvents | INTERNAL; heute Bestandteil TaskBoard | Nur minimal erforderliche Events; eigener Budgetanteil | Scope/Budget; Chronik wächst ohne feste Gesamtgrenze | Transition/Delete; nicht unbeschränkt historisieren, spätere minimierte Projektion prüfen |
| Material | INTERNAL; P/A/C | Ja, explizit freizugebende initiale Aufnahme | Scope/Budget; Name120, Menge40, Ort80 | Material/Person/Task/Status; Zuordnungen und Count aktualisieren |
| Kosten/Zahlungen | SENSITIVE; P/A/C ausdrücklich erlaubt | Ja nur nach expliziter RAM-Risikoentscheidung | Scope/Budget; Titel120, Label80, 1–32 Zahlungen/Position; keine Gesamtzahlgrenze | Kostenwrite/Delete; Summen und Labels konsistent; erhöhte finanzielle Klartextmenge |
| Protokollmetadaten/Timeline | SENSITIVE; A/C | Headerprojektion ja nach Freigabe | Scope/Budget; Titel120, Revision-/Entryzahl nicht global begrenzt | Neue Revision/Markierung/Personbezug/Status; schon Titel können sensitiv sein |
| Vollständige Protokolltexte/Revisionen | SENSITIVE; A/C, bewusster Detailread | **Nein** im allgemeinen Store; Detailowner bei Bedarf | Bisheriger Detail-/Formular-Lifecycle; Beschreibung/Ansage je2000, Rechtsgrund1000, max32 Kennungen120; History nicht global begrenzt | Detail verlassen/Revoke/Revision; Metadatenberechtigung rechtfertigt kein Preload aller Texte |
| Gedächtnisprotokoll-Metadaten | SENSITIVE; A/C | Header ja nach Freigabe | Scope/Budget; Autor80, keine Reportzahlgrenze | Create/Save/Complete/Addendum/Delete; revisionsgebundene Header, Draftcount |
| Vollständige Gedächtnisprotokolle/Antworten/Nachträge | SENSITIVE; A/C, bewusster Detailread | **Nein** im allgemeinen Store | Separater Bedarf; Fragebogenlimits, Nachtrag4000, Anzahl Nachträge nicht global begrenzt | Detail-/Editor-/Dirtyvertrag; kein Autoload der Antworten |
| Dokumentmetadaten | SENSITIVE; P/A/C + Dokumentbestand/Lane | Ja nur Domain-Asset ohne technischen Speicherort/Keys; bewusstes Öffnen und freigegebene Initialaufnahme | Scope/Budget; 100/Action, 25MiB/Datei, 250MiB/Action sind Bloblimits, keine Metadaten-RAM-Garantie | Import/Delete/Recovery/Action; stale Asset muss Viewer weiter ablehnen |
| Dokumentinhalte, Textseiten, Bitmaps, PDF-memfd | SENSITIVE; eigener bewusster Read | **Ausgeschlossen** | Bestehende Viewerlimits/-Lebensdauer | Jeder Viewerrevoke; Tink EOF, Inventar, Assetbindung, Namespace-Lane und PDF-Isolation unverändert |
| P3b-Identität/Kontakt/Notfallkontakt | SENSITIVE + Leadership/Person/Area/Grant | **Ausgeschlossen** | Bestehender Grant-/Detailowner | Grantablauf/Revoke/Area/Person/Navigation; kein Sammelpreload |
| Leadership-Grants, Opened, Passphrasen | Security-Capability/Secret; eigener Port | **Ausgeschlossen**, auch nicht als angeblich opake Cachehilfe | Bestehender kurzer Vertrag; Passphrase CharArray | Bestehender Widerruf/Best-effort-Wipe; kein Store darf Grant verlängern |
| Schlüsselmaterial/DEKs/Credentials | Secret; Data/Security-only | **Ausgeschlossen** | Unveränderte Owners/Leases | Originale Invalidierung; keine neue Keyhierarchie |

Normale Titel, Pseudonyme und Zahlungs-/Autorlabels können vom Benutzer dennoch
mit identifizierenden Angaben gefüllt werden. Der Ausschluss typisierter P3b-
Payloads garantiert weder Anonymität noch inhaltlich identitätsfreie Strings.
Der gesamte aggregierte Workspace braucht entsprechend konservativen Schutz.

## 8. Konsistenz, Writes und Invalidierung

SQLCipher bleibt ausschließlich bestätigte Wahrheit. Formularentwurf darf sofort
lokal reagieren; „gespeichert“ und neue bestätigte Zähler erst nach erfolgreichem
DB-Commit **und** erlaubter Result-Publication. Kein Write-behind, Autosave durch
Navigation oder optimistischer gespeicherter Zustand. Kein persistierter Store.

Ein zentraler, aktionsbezogener Mutationsepoch wird bei jeder zugelassenen Mutation
als „pending/dirty“ markiert, bevor ein weiterer Treffer möglich ist. Dies ist
keine DB-Version und kein Manipulationsnachweis. Bereits laufende Loads tragen
alten Epoch; nach erfolgreichem Commit Ergebnis atomar einspielen **nur**, wenn
es alle betroffenen Projektionen korrekt aktualisieren kann; sonst gezielt dirty
markieren und einmal neu lesen. Keine Fortschreibung aus veralteten Formulardaten.
Bei fehlgeschlagenem Write alten bestätigten Bestand nur behalten, wenn nachweislich
kein Commit erfolgt ist und die Session/Version noch gilt. Bei unklarem Ausgang
keinen Erfolgszustand, invalidieren; bestehende Fail-closed-/Recoveryregeln nutzen.

| Mutation | Mindestens betroffen |
|---|---|
| Actionstatus/-daten/-löschung | Gesamter Scope, Rechte, Formulare, alle Counts; auf andere Action nie übernehmen |
| Person Create/Rename/Delete | Operations/Personen, Aufgabenreferenzen, Materialzuordnung, Protokoll-Pseudonymprojektionen, Hub; P3b getrennt widerrufen |
| Task Create/Edit/Transition/Delete | Tasks/Events, Statuszähler, Personen-Aufgabenzähler, Material-Taskreferenzen |
| Rolle/Funktion/Plan/Status | Operations/Funktionszähler, Taskassignees/-labels, fokussierte Formulare |
| Material | Board/Count, Hub und verbundene Formularoptionen |
| Kosten/Zahlungen | Board, Count/Summe, editierte Position und Hub |
| Protokollergänzung/Korrektur/Markierung | Timeline/Summary, betroffener Detailowner mit erwarteter Revision; alte Historie nicht überschreiben |
| Gedächtnisprotokoll Save/Complete/Addendum/Delete | Header/revision/summary, betroffener Detail-/Formularowner; Dirty-/Conflictentscheidung erhalten |
| Dokumentimport/-löschung/Recovery | Assetliste/Summary, Viewerrequest für betroffenes Asset widerrufen; kein optimistisches READY-Asset vor Publication |
| Maintenance/Reconciliation/Storagefehler | Gesamten Scope verwerfen; keine Cache-Navigation über BLOCKED oder Recovery hinweg |

Bestehende Task-/Material-/Kostenwrites vergleichen erwartete Objekte; Protokoll
und Gedächtnisprotokoll besitzen Revisionserwartungen. Personrename verwendet
gegenwärtig **keinen** vollständigen erwarteten Personsnapshot, sondern frische
Existenz-/Pseudonym-/Statusprüfung. Keine vorhandene allgemeine CAS-Garantie
erfinden. Ein künftiger Store muss parallele Editoren ausschließen oder einen
geprüften Expected-Snapshot-Vertrag ergänzen, bevor stale Formulare schreiben.

Neue Projektionen, Sortierungen und defensive Kopien außerhalb des Coordinator-
Monitors vorbereiten; im Publication-Gate nur final prüfen und begrenzt Referenzen/
Versionen atomar austauschen. Keine 8-MiB-Iteration unter `StorageContext.publish`.

Writes und Reads brauchen konsistente Reihenfolge pro Aktion, aber keine
langlaufende Arbeit unter dem SessionCoordinator-Monitor. Die bestehende
DocumentNamespaceLane bleibt für Dokumentbestand zuständig, keine zweite
konkurrierende Filesystem-Synchronisation. Nicht dokumentbezogene Versions-
Invalidierung ist eine Domain-Verantwortung, kein neuer Dateisystemlock.

Lock beim Write: Commitgate bleibt original; vor Commit Revoke → Rollback,
nach Commit aber vor Publication Revoke → kein UI-Erfolg/keine Store-Publication.
Beim nächsten bewussten Öffnen wird DB-Wahrheit gelesen. Prozessende verliert nur
RAM/Drafts gemäß heutigem Vertrag; Wiederanlauf über normale Maintenance, kein
Wiederherstellen des Stores oder alter Formulare/Viewer. Keine doppelte Speicherung
beim Callback-Retry; keine automatische Wiederholung eines unklaren Writes.

## 9. Veränderte Schutzannahmen und Threat-Model-Matrix

Die maßgeblichen Verträge wurden gegen `threat-model.md`, `security.md`,
`privacy.md`, `architecture.md`, `ux-philosophy.md` und `PRODUCT.md` abgeglichen.
Historische Sollbeschreibungen werden nicht als aktuelle Implementierung ausgegeben.

| Entscheidungspunkt | Mehr gleichzeitiger RAM-Klartext | Weniger DB-Reads/Preflights | Scope-/Versions-/Commit-Verantwortung |
|---|---|---|---|
| A Bisher | Bereichssnapshot, bedarfsorientierte Detailboards, bei Navigation Clear | Jeder Repository-Read prüft aktuellen Bestand/DB | Repository prüft frische Rows, UI Request/Generation |
| B Änderung | Erlaubte strukturierte Daten einer Aktion gemeinsam bis Scopeende halten | Gültigen RAM-Treffer ohne DB öffnen | Store-Scope und Projektionen müssen nach allen Writes aktuell bleiben |
| C Schutzgüter | Personenbezüge, Organisation, Kosten, sensible Header | Integrität, Verfügbarkeit, zuverlässiger BLOCKED-Zeitpunkt | Fachberechtigung, Status, korrekte Zähler und gespeicherte Wahrheit |
| D Threat IDs | TM-F03/F06/F08/F17; F05/F11 bei versehentlicher Persistenz | TM-F05/F08/F11/F17, lokale Integritätsfehler | TM-F03/F06/F08/F17, fachliche Access Control |
| E Zusätzliche Klartextmenge | Material-/Kostenboards/Header zusätzlich zu Operations/Tasks; keine pauschale feste Menge bei unbeschränkten Fachzahlen | Keine eigene zusätzliche Datenkopie erforderlich, aber retained Read-Model | Versionskarten, Formularkopien, möglicherweise Übergangssnapshots |
| F Lebensdauer | Vom bewussten Laden bis Actionwechsel/Lock/Generationende, maximal verbleibende gültige Sitzung; frühere Bereiche bleiben länger | Kein neuer Key-/Connection-Lifetime, jedoch längerer Abstand zwischen Checks | Alte Formulartokens dürfen nach Dirty/Versionwechsel nicht gültig bleiben |
| G Angriffsfläche | Mehr auf einmal abschöpfbare Strings/Objekte; Abhängigkeiten im selben Prozess sehen denselben Speicher | Spätere Erkennung von Datei-/Page-/Schema-/FK-/Inventarfehlern | Fehlende Invalidierung, stale callback, fehlender Write-Hook, doppelte Summen |
| H READY-Übernahme | Schnellerer Zugriff auf bereits zugelassene Kategorien, weniger zeitliche Navigationsbarriere; kein neues P3b-Recht | Offene Sitzung kann alten bestätigten RAM-Stand anzeigen, bevor DB-Fehler erkannt wird | Fehlerhafter Scope kann fremde/alte Action zeigen; muss hart abgewehrt werden |
| I RAM-Kompromittierung | Größerer gleichzeitiger Klartextumfang; Referenzlöschung holt Kopien nicht zurück | Store schützt nicht gegen Speicherveränderung durch privilegierten Angreifer | Epoch-/Versionstokens sind keine kryptografische Integrität |
| J OS-Kompromittierung | Keine Vertraulichkeits-/Wipegarantie, OS kann RAM kopieren/auslagern | Zeit/Gates/Dateien manipulierbar; kein Compromise-Detektor als Schutzbasis | Zentrale Autorität setzt nicht gegen vollständig kompromittiertes OS durch |
| K DB-Erkennungszeit | Bestehende Checks bei realen Operationen bleiben, aber Kategorie kann länger ohne neue Operation verfügbar sein | Explizit verändert: erst nächster tatsächlicher DB-Zugriff/Unlock-Maintenance statt jedem Bereichswechsel | Versionskarte erkennt nur bekannte Appwrites; kein externer DB-Manipulationsnachweis |
| L Gegenmaßnahmen | Allowlist, eine Action, Budget, keine Volltexte/P3b/Keys, kein SavedState, sofortige Revocation, kurze Detailowner | Initialer voller Preflight, weiterhin jeder Write/Miss/Viewer voll geprüft, bewusster Refresh; gewünschte maximale Snapshotfrische ausdrücklich entscheiden | Zentraler Mutations-/Dirtyport, atomare bestätigte Projektionen, Expected-Snapshots, Epoch-Fencing und Tests jedes Writepfads |
| M Restrisiko | Mehr und länger verfügbarer Klartext bleibt absichtlich bestehen | Spätere Erkennung bleibt selbst mit korrekt implementiertem RAM-Store | Nicht erfasster Write/aliasierter Mutable-Wert kann trotz gültiger Session stale Daten liefern |
| N Messnachweis | Domain-/UI-Heap getrennt messen, keine Gleichsetzung mit DB-Dateigröße | A/B/C zeigen Navigation ohne DB; exakte Werte unten | Testprototyp prüft Version-/Revoke-Fencing, kein vollständiger produktiver Writebeweis |
| O Empfehlung | Nur begrenzte erlaubte Strukturdatensichten; keine beliebige RAM-Ausweitung | Zeitliche Änderung explizit akzeptieren oder betroffene Bereiche weiter DB-basiert lassen | Erst zentraler Invalidierungsvertrag, dann schrittweise Integration |
| P Freigabe | Erforderlich: Datenkategorien, Budget und Lebensdauer | **Erforderlich: geänderter Zeitpunkt der Integritäts-/Fehlererkennung** | Erforderlich: konsistentes Write-/Freshnessmodell; keine stillschweigende Freigabe durch Testgeschwindigkeit |

### Integritätsprüfung: konkreter Zeitverlauf

Heute: Öffnen → voller Preflight/Read → Bereichswechsel → neuer voller Preflight.
Vorschlag: Öffnen → voller Preflight/Read → RAM-Treffer → **kein** neuer Inventar-,
`cipher_integrity_check`-, `integrity_check`-, `foreign_key_check`- oder Room-
Schemacheck. Ein nach dem Snapshot entstandener Diskfehler wird erst beim nächsten
wirklichen DB-Zugriff erkannt. Mit fortgesetzter gültiger Bedienung kann der
Read-Model-Zeitraum nahezu die verbleibenden 180 Sekunden umfassen; wenn später
keine DB-Operation erfolgt, gibt es keine zugesicherte Erkennungsfrist in Wanduhrzeit.

Ein RAM-Hit kann einen neuen Diskfehler nicht entdecken und deshalb auch nicht
allein dessen BLOCKED auslösen. Meldet ein anderer zulässiger Pfad StorageFailure,
muss der zentrale Coordinator sofort BLOCKED veröffentlichen und alle Store-/UI-
Referenzen widerrufen. Ein im RAM gehaltener Versioncounter, Dateimtime oder Hash
des alten Snapshots ersetzt keine Prüfung des aktuellen verschlüsselten Bestands.
Auch bisherige Integritätschecks erkennen nicht jedes Rollback oder jede denkbare
Manipulation; diese Grenze wird durch den Store nicht gelöst.

Unverändert bleiben bei echter DB-Arbeit alle bisherigen Preflights und Gates,
SQLCipher-Kryptoparameter, TRUNCATE-/Temp-Policy, kurze Room-Verbindung/Key-Lease und
fail-closed-Fehlerbehandlung. Ebenso unverändert: 180 s ab erfolgreicher Auth, 30 s
READY-Idle, Foreground-/Screen-Lock, Generation, FLAG_SECURE, Namespace-Lane,
Tink bis authentifiziertem EOF, PDF-Isolation, Import-/Delete-Recovery. Dass diese
Produktpfade **nicht verändert** wurden, ist kein Nachweis gleicher Angriffsfläche
für eine erst künftig freizugebende längere RAM-Vorhaltung.

## 10. Isolierter Machbarkeitsprototyp und Messmethodik

Alle neuen ausführbaren Quellen liegen unter `tools/p11assessment/`. Nur explizite
Gradle-Init-Skripte nehmen sie in Tests bzw. den **isolierten** Debug-Target auf.
Ohne diese Skripte ist kein Assessment-Store in Produktquellen eingebunden.
Target `org.pag.perf.p11probe`, Instrumentation `org.pag.perf.p11probe.test`;
beide unterscheiden sich von `org.pag.prototype.a`. Keine produktive Installation.
Die Fixture prüft die Paket-Allowlist vor jeder Datei-/Keyarbeit und legt einen
eigenen zufälligen synthetischen Unterbestand an; keine Produktcredentials/-DB.

`AssessmentStore` hält genau einen typisierten WorkspaceRead, delegiert READY/
Generation/Publication an StorageAccess und bindet Action/Status/Version/Epoch.
Er besitzt keinen eigenen Timer, keine Keys, keinen IO-/SavedState-Port. Das ist
**kein produktionsreifer Sicherheitsadapter**: Beispielsweise sind vollständige
Payload-/Aliasvalidierung, verbindliche Kostenmessung vor Materialisierung,
Writeintegration und Permissionversionierung nicht implementiert. Das Budget ist
im Navigationsversuch ein explizit injizierter Testwert, keine gemessene produktive
Speicherbegrenzung. Die Sicherheitsentscheidung darf daraus kein OOM- oder
vollständiges Security-Prädikat ableiten.

Varianten über dieselbe reale ProductViewModel-/ProductScreen-/SQLCipher-Route:

- **A:** aktueller lokaler P11-Readpfad, keine Store-Hits.
- **B:** erster ohnehin geladener Hub-Snapshot bleibt testexklusiv verfügbar;
  keine zusätzliche Payload-Vorabladung, fehlende Bereiche lesen DB.
- **C:** bei bewusster initialer Öffnung zusätzlich Task-/Material-/Kostenboards
  und Summaries in **einem** echten autorisierten Workspace-Read vorbereiten;
  weitere abgedeckte Bereiche projizieren RAM. Kein allgemeines Vorladen von
  Volltexten, Dokumentbytes oder P3b. Vorbereitete Header/Dokumentliste sind im
  Entwurf enthalten, in dieser Navigationsprobe jedoch **nicht** vorgetäuscht:
  leere/ungeprüfte Bereiche sind kein Nachweis ihrer Skalierung.

Fixture small: 5 Personen, 10 Aufgaben mit je einem CREATED-Event, 5 Material,
5 Kosten mit einer Zahlung; typical: 20/100/20/20; large: 200/1000/200/200.
Drei synthetische Aktionen, ACTIVE mit zehn Protokolleinträgen. Keine P3b-Daten,
keine echten Identitäten. Rollen/Memory-/Dokumentbestand in dieser Probe leer.
„Typical“ ist ein reproduzierbarer Testname, keine Behauptung über Nutzerdaten.

**Kardinalitäten recherchiert:** `PersonRules`, `OperationalRules`, `TaskRules`,
`MaterialRules`, `ExpenseRules`, `ProtocolRules`, `MemoryReportRules` und ihre
DB-Stores enthalten keine allgemeine Aktionsmengenobergrenze dieser Kategorien.
Dokumente haben ein echtes Maximum von 100/Action; Zahlungen 32/Position;
mehrere Textfelder besitzen die oben genannten Längenlimits. Ein „größter
zulässiger Aktionsbestand“ ist deshalb derzeit nicht endlich definiert. Large
ist ein erlaubter Lastfall, **kein** Nachweis für jede erlaubte Aktion.

Samsung SM-M526BR, API33 (Serienziel lokal gewählt). Je Variante/Größe zehn Wiederholungen,
reihenweise A→B→C, gleicher Fixturegenerator und Sequenz. Reales SQLCipher inkl.
KDF/Preflight bleibt pro Miss unverändert. Erstaufruf/JIT enthalten, kein
ungeprüfter Warmup-Abzug. Kein randomisiertes Cross-over; zeitliche Geräte-/JIT-
Effekte bleiben Einschränkung. Thermik und Framework-FD-Warmup separat berichten.
Keine Addition verschiedener Fixtures zu erfundener Produktgesamtzeit.

Zeitpunkte: Tap-Callback inklusive Main-Queue-Wartezeit, erster OnDraw nach Tap,
und erster OnDraw nach erfülltem Modellprädikat **plus** Compose-SideEffect-
Bestätigung. Das ist ein UI-Draw-Proxy, kein bloßer Methodenwert und kein optischer
Nachweis physisch ausgegebener Pixel. Es wird keine RenderThread-/Display-Present-
Messung behauptet. WindowFrameMetrics liefert TOTAL_DURATION/DEADLINE und verlorene
Messmeldungen; letztere sind nicht gleich verlorene Displayframes. Vorher-/Nachher-
Heapdifferenzen der UI enthalten JIT/Compose/GC und sind keine präzise Deep Size.
Die Navigationsfixture bindet ihren DB-KeyOwner nicht an einen echten
Geräte-SessionCoordinator; ihre Storage-Gates und der SessionPort sind synthetisch.
Der Navigations-SessionPort erzeugt synthetische READY/Lock-Events mit konstanter
Generation 1 passend zur Fixture. Er beweist **keine echten Gerätecredentials oder
Deadlines**; diese prüft die separate Host-Suite am echten SessionCoordinator.

Die reale `personDetail`-Kette bleibt absichtlich DB-basiert, ohne Leadership-Port
in dieser Probe. Das Ergebnis darf weder als bereits DB-freie Personenmaske noch
als Produktlatenz einschließlich Leadershipinventory verkauft werden. Bestehende
Taskmaske und neue Taskmaske verwenden den echten vorhandenen RAM-Formularpfad.

## 11. Speicherbudget und kontrollierte Degradierung

**BEOBACHTET:** Kein endliches Maximum für normale Fachbestände; Personpseudonyme,
Rollennamen und Actiontexte haben zudem nicht überall ein Längenmaximum. Deshalb
ist ein bloßes „eine Aktion“ keine RAM-Grenze. TaskBoard hält Tasks **und** Events;
`WorkspaceSnapshot.persons` zusätzlich eine Liste der Personreferenzen. Formulare
bauen Eingabekopien; Projektionen erzeugen sortierte/gefilterte Listen. Strings,
Maps/Sets, Objekt-/Arrayheader, Referenzen und Compose-Slots kosten RAM unabhängig
von der DB-Dateigröße. Ein immutable Kotlin-`List`-Typ garantiert keine tiefe
Unveränderlichkeit gegen einen veränderbaren Alias.

Die separate `AssessmentMemoryProbe` hält 16 unabhängig erzeugte vollständige
synthetische Domain-Graphen je Messung, misst Java-Used-Heap vor/nach explizitem GC
und dividiert die Differenz durch 16; 20 Wiederholungen je Größe. Enthalten sind
Person/OperationalState/Plan, Task+Event, Material, Expense+Payment und Listen/
Strings. Nicht enthalten: produktive Volltexte, große Rollen-/Metadatenbestände,
Bitmaps, DB-/nativer Heap oder UI/Formulardoppelrepräsentation. Das ist eine
**Geräte-Schätzung tatsächlicher Retention**, keine exakte Objektgröße und keine
statistisch unabhängige Zahl identischer ART-Objekte. GC/JIT-/Allocatorrauschen
bleibt; negative/ausreißende Differenzen werden nicht als Speicherersparnis verkauft.
UI-Heapdifferenzen und Native-/FD-Punkte separat, nicht addiert zur Deep Size.

**Vorgeschlagene, noch freizugebende Engineering-Grenze:** zunächst höchstens
8 MiB konservativ berechnete retained Domain-Payload einschließlich Listen/Maps/
Strings/Indizes, zusätzlich höchstens eine Übergangsrepräsentation mit gleichem
Budget. Insgesamt 16 MiB Store-bezogene Arbeitsdaten sind **kein** Total-App-/ART-/
Native-Limit; Compose, Formulare und Viewer brauchen getrennte Budgets. Das ist
eine vorläufige konservative Zielgrenze, kein empirisch bewiesenes optimales Limit.
Nach Messung von maximalen Feldern und realer UI-Doppelrepräsentation prüfen.

Zwingende Vorbereitung: In derselben autorisierten Operation Count-/Textumfang-
Admission **vor** unbeschränkter Row-Materialisierung; overflowfeste Long-Arithmetik,
budgetierte schrittweise Reader statt erst `list()` vollständig laden und danach
prüfen. Begrenzt defensive Kopien erzeugen, keine doppelte Stringinhalte ohne Bedarf.
Bei Budgetüberschreitung betroffenen Teil als NOT_LOADED markieren und auf den
bestehenden bedarfsorientierten Pfad degradieren. Kein Abschneiden als vollständige
Liste, kein Empty/Nullzählung, keine automatische Budgeterhöhung und kein zweiter
Aktionscache. Auch ein zu großer Einzelwert muss kontrolliert abgelehnt/bedarfsgeladen
werden; keine unbegrenzte Cachebefüllung, die erst nach Allokation ein Limit prüft.

Cleanup: Publication zuerst widerrufen, Storeepoch erhöhen, neue Reads abweisen,
laufende Requests canceln, Formulare/Projektionsreferenzen entfernen, Store leeren;
kontrollierte Byte-/Chararrays bestmöglich überschreiben. Kotlin-/Java-Strings,
Compose-/IME-/Runtimekopien und physische RAM-Seiten lassen sich nicht vollständig
nachweisbar überschreiben. Keine Promise physischer Löschung oder Schutz gegen
privilegierte Speicherabbilder/OS-Auslagerung.

## 12. Tests und verbleibende Nachweislücken

Neue Hosttests am testexklusiven Store: erlaubter Treffer, fehlende Autorisierung,
falsche Generation/Action/Version/Status, stale Loadticket nach Clear, verweigerte
Publication, abgebrochener Request, fehlender Bereich, Budgetverweigerung,
Version-/Formulartoken-Invalidierung. Modelltests von Commitfolgen sind **keine**
echten SQLCipher-Commits. Sie ersetzen keine Transaktions-/Rollbacktests.

Neue Tests mit dem **unveränderten echten SessionCoordinator** und synthetischer
AuthenticationExperiment: LOCKED, expliziter Lock, Idleablauf nach 30 s, absolute
180 s trotz Bedienung, Foregroundverlust, simuliertes gesperrtes Environment,
BLOCKED/StorageFailure, Reauth ohne Storewiederherstellung, verspätete Load-
Publication nach Lock. Beobachter leert den Store synchron außerhalb READY.
Dies sind Host-/Kontrollpfadtests, keine neue Gerätecredential-Abnahme.

Die Geräte-Navigationsprobe widerruft nach jeder Sequenz Workspace/Store und prüft
fehlenden Zugriff. Kein Persistenz-/SavedState-Port im Prototyp, keine Inhalte in
seinen technischen Messmeldungen; Fixture-Dateien enthalten nur synthetischen
SQLCipher-Testbestand. Alle Store-Hits vermeiden bewusst DB-Reads; die Zähler
berichten dies separat, nicht als zusätzlich durchgeführte Integritätsprüfung.

**Offene Pflichtnachweise vor produktiver Umsetzung:** Activity-/Prozessende mit
wirklicher Storeintegration; Maintenancephase; alle Permission-/Leadership-
Transitions (P3b/Grantaufnahme konstruktiv ausgeschlossen); echte konkurrierende
Load-/Write-/Commit-/Publicationrennen; jede Mutation aus Abschnitt 8, Reconciliation,
Delete-Recovery, Person-CAS/Konflikte, stale Formulare, Actionstatus/Deletion-Pending,
Mutable-Aliase/defensive Kopien, Fall-back bei maximalen Größen/Overflow, OOM/
GC-Stress, SavedState-/Datei-/Log-/Manifestaudit des **späteren** Produktstores.
Ein nicht integrierter testexklusiver Store kann diese produktiven Pfade nicht
bereits bestanden haben. Keine bestehende Regression wird entfernt/abgeschwächt.

## 13. Konkreter Implementierungsplan nach ausdrücklicher Freigabe

Kein Migrationsbedarf für einen rein flüchtigen Read-Model-Owner; Schema 11 bleibt.
Persistierte fachliche Versionsspalten wären eine separate spätere Entscheidung,
keine heimliche Migration. Vorgehen in kleinen überprüfbaren Slices:

1. **Verträge/Allowlist:** Domain-Port `ActionWorkspaceReadPort` mit expliziten
   erlaubten Teilen, `WorkspaceBinding`, `DomainRevisions`, `LoadEpoch` und
   NOT_LOADED/LOADING/CONTENT/EMPTY/ERROR je Projektion. Keine Domain-Abhängigkeit
   auf Data/Compose. Header als eigene Typen; kein allgemeiner `Any`-/Map-Cache.
   Scope/Permissions aus bestehenden Fachregeln, keine zweite Securitypolicy.
2. **Mutationserfassung vor Cache:** Data-/Usecase-seitiger instanzlokaler
   `ActionMutationObserver`/Invalidierungsport für **sämtliche** Write-/Recovery-
   Pfade; pending epoch vor Write, bestätigte Änderungen nach Commit/Publication.
   Atomare Version-/Read-Fencing-Tests zuerst. Alle Writes weiterhin originale
   SQLCipher-Transaktionen/Preflights/COMMIT-Gates. Keine DB-Connection im Observer.
3. **Budgetierte Readprojektion:** `ProductActionWorkspaceReader` im bestehenden
   ActionDatabaseAccess, kurze autorisierte Readtransaktion; vorab begrenzte
   Counts/Strings, danach bounded Materialisierung. Bestehende Store-/Statusregeln
   wiederverwenden; kein P3b/DEK/Volltext. WorkspaceReader schließt Room/Lease vor
   Publication. Status/ExpectedAction prüfen; fehlende Bereiche bleiben explizit.
4. **Owner für Operations/Tasks:** instanzlokaler `ActiveActionSessionStore` im
   bestehenden Runtime-/ViewModel-Lifecycle, genau ein Binding; echte StorageAccess-
   Zulassung/Publication und vorhandener synchroner Sessionbeobachter. `close` ist
   idempotent. Zuerst Operations/Taskboard; keine Material-/Kosten-/Headerausweitung
   ohne zugehörige Budget-/Write-/Permissiontests. Keine globale Registry.
5. **Navigation trennen:** ProductViewModel trennt `leaveSensitiveDetailsAndDrafts`
   von `invalidateWorkspace`. Ersteres behält Grant-/Viewer-/Dirty-/Requestvertrag;
   zweiteres ist für Lock/Action/Version nötig. `ProductScreen`, ActiveWorkspace und
   PreparationWorkspace erhalten stabile Bereichsprojektionen statt Fachrepository-
   Calls bei jedem Tap. Compose hält keine zusätzlichen vollständigen Boards.
   Scroll-/Fokus-/A11y-Verträge und erste Drawmessung testen.
6. **Formulare:** Task/Operations/Material/Kosten öffnen aus aktueller Projektion,
   Personpseudonym getrennt von Leadershipinventory; keine alte P3b-Statefreigabe.
   Formulartoken trägt Scope/Version/Expectedobjekt; Save bleibt DB. Nach Dirty-/
   Versionswechsel kein stilles Überschreiben, kein gespeicherter Erfolg vor Commit.
   Bestehende Personrename-Konfliktgrenze ausdrücklich lösen.
7. **Weitere erlaubte Kategorien:** Material/Kosten und dann Protocol-/Memory-
   Header/DocumentAsset-Metadaten einzeln ergänzen. Kosten-RAM und sensible
   Timeline-/Autorlabels separat abnehmen. Volltexte und Dokumentinhalte bleiben
   bedarfsorientiert in bisherigen Owners. Keine Parser-/Tink-/Memfd-Lifetimeänderung.
8. **Hardening/Abnahme:** alle Abschnitt-12-Pflichtfälle mit echter Integration,
   synthetische Androidtests und vergleichbare A/B/C/UI-/GC-/FD-Messung. Maximal-
   Feld-/Mengenfälle, A11y und Lock beim Laden/Speichern erneut manuell prüfen.
   Unabhängiger Review plus #26 vor Release. Erst danach ausdrücklich autorisiertes,
   signaturkompatibles Produktupdate; dieser Assessmentauftrag installiert keines.

**Rollback:** Pro Bereich auf vorhandenen bedarfsorientierten Readport zurückschalten;
Store leeren, nichts auf Disk migrieren/zurückschreiben. Fallback benötigt dieselben
Gates und darf keinen Storagefehler als normalen Cache-Miss verstecken. Fehlende
Store-Autorisierung darf niemals einen alten Snapshot veröffentlichen. Ein DB-
Integritätsfehler bleibt BLOCKED; keine automatische Reparatur. Kein produktiver
Fallbacktest darf Nutzerdaten verändern.

## 14. Erforderliche Benutzerentscheidungen

Vor produktivem Stufe 2 ausdrücklich festzulegen:

- Erlaubte initiale RAM-Kategorien, insbesondere Kosten/Zahlungslabels und sensible
  Protokoll-/Memoryheader, sowie längere gleichzeitige Vorhaltung bis Scopeende.
- Akzeptanz späterer Integritäts-/Inventar-/Schema-/FK-Fehlererkennung auf RAM-Hits;
  Initial-/Write-/Miss-/Viewerchecks bleiben voll. Alternativ mehr DB-Frische mit
  entsprechend verbleibender Navigationslatenz. Keine bereits erteilte Freigabe.
- Vorläufiges 8-MiB-Domainbudget plus begrenzte Übergangsrepräsentation und sichtbare
  bedarfsorientierte Degradierung statt garantierter Sofortnavigation jeder Größe.
- Invalidierungs-/Konfliktvertrag einschließlich Personrename und aller Recovery-
  Writes; kein Optimistic-Save oder Write-behind.

Nicht Bestandteil dieser Empfehlung: Room offen halten, SQLCipher-KDF/HMAC ändern,
DB-Preflight innerhalb echter DB-Operationen entfernen, entschlüsselte Dokumente/
PDF-memfd/Renderer über Interaktionen cachen, P3b vorladen oder Session verlängern.
Die bisherigen P11-Klasse-B-Entscheidungen bleiben davon getrennt und unfreigegeben.

### Zusätzliche UI-Befunde ohne Produktänderung

**BEOBACHTET:** `ProductScreen` verwendet eine gescrollte Column; Task-/Personen-
Bereiche bauen ihre Rows mit `forEach` auf. `TaskBoard.active` filtert/sortiert bei
jedem Getteraufruf; `affectedCount(personId)` greift darauf erneut zu.
`OperationsScreen` fragt personenbezogene Taskcounts je Person mehrfach ab.
Mit P Personen und T Tasks kann dadurch mehrfach P×T-Filter-/Sortierarbeit anfallen,
zusätzlich zur gesamten Composition nicht sichtbarer Zeilen. Das sind konkrete
Kandidaten für versiongebundene einmalige Projektionen und Lazy-Listen, keine
bereits nachgewiesene Ursache jedes gemessenen Frames. Ein RAM-Store allein entfernt
weder diese Getterarbeit noch Composition/Layout.

**HYPOTHESE für spätere kleine Slices:** Einmal pro unveränderter Taskrevision
Active-/Closed-Liste und affectedCount-Map bilden; keine neue Payload aufnehmen.
Lazy-Darstellung mit stabilen Action-/Entitykeys unter Erhalt von Fokus, Scroll,
A11y und Formularzuständen prüfen. Zunächst getrennte Frame-/Allokationsmessung;
keine Umsetzung im Assessment und keine Vermischung ihres möglichen Gewinns mit
A/B/C. Bei Lock dieselben Projektionsreferenzen entfernen.

### Budget-Admission konkretisieren

Vor Materialisierung je erlaubter Kategorie `COUNT` und Summe konservativer
Text-/Relationseinheiten innerhalb desselben kurzen konsistenten Readzugriffs
erheben. SQL-Zeichenlängen nicht mit Kotlin-UTF16-Bytes gleichsetzen: für eine
konservative Grenze Codepoints mit bis zu vier Nutzbytes plus String-/Arrayheader,
Referenzen/Alignment und Objekt-/Collectionkosten belasten; Long-Overflow verweigert.
Danach während jeder geladenen Row den Charge nachprüfen, bevor weitere Listen/
Maps wachsen. Keine formale Garbage-Collector-Heapbegrenzung daraus ableiten.
Die belastbare Chargeregel ist mit den tatsächlich gemessenen ART-Graphen und
maximalen Unicode-/Feldvarianten zu validieren. Ein leerer oder kleiner kurzer
ASCII-Testbestand beweist dies nicht. Budgetablehnung ist kein Storageintegritäts-
fehler; beschädigte DB-Werte/Inventar bleiben dagegen der vorhandene BLOCKED-Pfad.

## 15. Gerätemesswerte

**GEMESSEN:** Zehn vollständige Wiederholungen je small/typical × A/B/C,
insgesamt 60 erfolgreiche Navigationssequenzen. Angaben Median / P95 in ms;
bei zehn Stichproben ist nearest-rank P95 das Maximum. Gleiche Reihenfolge,
keine Ausreißer entfernt. Standardabweichung/Min/Max reproduzierbar mit
`tools/p11assessment/summarize.py`; keine Konfidenzintervalle aus zehn Läufen.

| Fixture | Operation, bis bestätigtem Content-OnDraw | A aktuell | B erster Snapshot | C vorbereitet |
|---|---|---:|---:|---:|
| small | READY → Liste (ohne Auth/Maintenance) | 1213.65 / 1243.08 | 1211.97 / 1239.62 | 1205.43 / 1223.84 |
| small | Bewusste Aktionsöffnung | 1211.50 / 1243.33 | 1214.28 / 1261.71 | 1219.30 / 1254.90 |
| small | Lagebild → Personen | 1216.49 / 1241.88 | 30.02 / 46.09 | 28.86 / 47.41 |
| small | Personen → Aufgaben | 1216.22 / 1236.26 | 31.52 / 55.26 | 32.42 / 50.41 |
| small | Aufgaben → Funktionen | 1201.89 / 1210.28 | 19.77 / 26.67 | 18.70 / 23.47 |
| small | Funktionen → Material | 1223.48 / 1247.89 | 1224.10 / 1240.08 | 29.64 / 49.02 |
| small | Material → Kosten | 1218.31 / 1232.54 | 1220.50 / 1235.69 | 27.25 / 39.47 |
| small | Kosten → Lagebild | 1207.43 / 1209.91 | 21.90 / 28.85 | 20.27 / 23.87 |
| small | Vorhandene Aufgabe + edit() | 36.67 / 105.69 | 41.70 / 115.02 | 40.27 / 111.91 |
| small | Neue Taskmaske | 19.46 / 26.84 | 19.75 / 28.02 | 19.98 / 28.85 |
| small | Vorhandene Person, echter DB-Detailpfad | 1202.03 / 1223.44 | 1204.26 / 1212.07 | 1205.14 / 1214.59 |
| small | Drei schnelle Bereichswechsel | 1207.33 / 1214.39 | 11.09 / 14.33 | 11.79 / 14.04 |
| typical | READY → Liste (ohne Auth/Maintenance) | 1224.05 / 1248.22 | 1212.73 / 1238.63 | 1210.29 / 1232.97 |
| typical | Bewusste Aktionsöffnung | 1213.67 / 1267.37 | 1216.80 / 1254.74 | 1227.51 / 1276.61 |
| typical | Lagebild → Personen | 1235.84 / 1285.84 | 38.83 / 91.44 | 43.45 / 82.63 |
| typical | Personen → Aufgaben | 1280.39 / 1376.25 | 74.52 / 195.14 | 80.39 / 188.35 |
| typical | Aufgaben → Funktionen | 1205.39 / 1213.97 | 29.86 / 48.70 | 28.65 / 46.53 |
| typical | Funktionen → Material | 1234.95 / 1257.66 | 1235.00 / 1256.30 | 31.96 / 59.94 |
| typical | Material → Kosten | 1227.63 / 1251.05 | 1233.84 / 1266.42 | 33.39 / 57.99 |
| typical | Kosten → Lagebild | 1211.64 / 1220.86 | 18.67 / 25.96 | 18.67 / 27.42 |
| typical | Vorhandene Aufgabe + edit() | 40.46 / 122.33 | 47.28 / 120.14 | 46.11 / 128.05 |
| typical | Neue Taskmaske | 17.62 / 23.01 | 19.54 / 29.30 | 19.41 / 26.15 |
| typical | Vorhandene Person, echter DB-Detailpfad | 1204.64 / 1217.42 | 1205.41 / 1224.48 | 1211.35 / 1235.86 |
| typical | Drei schnelle Bereichswechsel | 1217.32 / 1225.80 | 11.47 / 13.19 | 11.54 / 13.17 |

C ersetzt keinen Unlock-/Listenread. Initiales DB-Laden enthält die vorbereiteten
Boards und deren Materialisierung; die reine zusätzliche Storeinstallation ist
nicht gesondert zeitaufgelöst. Die Messung `INITIAL_DB_AND_BUILD` umfasst DB,
Worker/Main und Storeadmission, nicht nur RAM-Aufbau.

| Typical | Metrik | A | B | C |
|---|---|---:|---:|---:|
| OPEN | Tapreturn | 3.65 / 8.01 | 4.02 / 8.98 | 3.95 / 8.45 |
| OPEN | erster Tap-OnDraw | 14.25 / 18.92 | 14.09 / 17.91 | 15.82 / 19.43 |
| PERSONS | Tapreturn | 3.61 / 7.53 | 3.54 / 4.56 | 3.68 / 4.13 |
| PERSONS | erster Tap-OnDraw | 17.46 / 21.97 | 38.81 / 91.42 | 43.43 / 82.61 |
| TASKS | Tapreturn | 1.76 / 5.50 | 2.41 / 3.15 | 1.88 / 2.68 |
| TASKS | erster Tap-OnDraw | 15.78 / 23.29 | 74.51 / 195.12 | 80.38 / 188.33 |
| MATERIAL | Tapreturn | 2.49 / 8.70 | 1.57 / 3.39 | 1.94 / 3.36 |
| MATERIAL | erster Tap-OnDraw | 12.92 / 19.86 | 12.05 / 14.84 | 31.93 / 59.91 |
| COSTS | Tapreturn | 1.22 / 2.86 | 1.36 / 2.66 | 1.98 / 3.65 |
| COSTS | erster Tap-OnDraw | 12.80 / 16.15 | 13.90 / 17.90 | 33.37 / 57.96 |
| HUB | Tapreturn | 1.27 / 2.74 | 1.57 / 3.17 | 1.82 / 2.97 |
| HUB | erster Tap-OnDraw | 15.61 / 21.13 | 18.65 / 25.93 | 18.65 / 27.40 |

Tap-OnDraw kann den neuen Ladezustand statt fertigen Inhalts zeichnen; maßgeblich
für nutzbaren Inhalt ist die obere Tabelle. Nicht jeder Treffer liegt im nächsten
Frame. Task-P95 und einzelne Rückwechsel überschreiten die 100–150-ms-Hypothese.
Vorhandene Task-/neue Masken sind bereits in A RAM-basiert; deren Zeit wird nicht
als durch einen neuen Store erzielter Gewinn ausgegeben. Personen-Details benötigen
weiter ihren DB-Read, produktiv gegebenenfalls plus Leadershipinventory.

### Frames und FDs der vollständigen Sequenzen

| Fixture/Variante | Frames | TOTAL ms Median/P95 | über eigene DEADLINE | verlorene FrameMetrics-Meldungen | FDs Anfang → Ende |
|---|---:|---:|---:|---:|---:|
| small/A | 721 | 14.95 / 52.37 | 47.16% | 0 | 112 → 142 |
| small/B | 552 | 16.18 / 55.85 | 46.20% | 0 | 113 → 141 |
| small/C | 509 | 18.40 / 55.96 | 49.12% | 0 | 113 → 139 |
| typical/A | 726 | 15.12 / 130.40 | 48.07% | 0 | 112 → 140 |
| typical/B | 547 | 16.63 / 161.63 | 50.27% | 0 | 113 → 139 |
| typical/C | 502 | 19.82 / 158.89 | 51.20% | 0 | 113 → 140 |

**Keine Jankverbesserung nachgewiesen.** Framepopulationen enthalten unterschiedliche
Anzahlen an Loading-/Contentwechseln; ein RAM-Treffer beseitigt die übrige UI-Arbeit
nicht. Over-deadline ist ein Jankproxy, kein SurfaceFlinger-Dropped-Framezähler.
Verlorene Displayframes wurden nicht separat gemessen. Die FD-Zunahme enthält
Framework-/Compose-/Thread-/IME-Warmup; sie beweist weder Leakfreiheit noch einen
Store-FD-Leak. Die Stores selbst besitzen keine FDs. Bestehende warm wiederholte
DB-Read-/Cleanup-Regression separat prüfen. Thermikstatus in allen sechs Reihen
Anfang/Ende 0. USB, Akku100%, 30,5°C beim Zwischenstand, Auflösung1080×2400,
font_scale0.9; keine Übertragung dieser Zahlen auf große Schrift/TalkBack. Deren
Benutzerabnahme ist ein separater Funktionsbefund. Gerät nicht randomisiert
neu gestartet; JIT/Framework-Erstaufrufe sind enthalten.

### RAM-Objekte: gesonderte synthetische Retention

20 Gerätewiederholungen je Größe, 16 unabhängige Graphen pro Wiederholung,
expliziter GC vor/nach Aufbau; kein Live-Produktbestand. Letzte vollständige Reihe:

| Domain-Graph | bytes/Aktion Median/P95 | ungefähr KiB | reiner synthetischer Objektaufbau ms Median/P95 |
|---|---:|---:|---:|
| small | 5682 / 5682 | 5,55 | 0,88 / 1,01 |
| typical | 35394 / 35394 | 34,56 | 0,89 / 2,24 |
| large | 349394 / 349394 | 341,21 | 1,48 / 1,57 |

IDs und Textlängen des separaten Graphbuilders unterscheiden sich von den
UUIDs/Strings real materialisierter DB-Rows. Die Werte sind keine exakte Deep Size
des SQLCipher-WorkspaceRead. Die Tabelle verwendet die zweite vollständige Memoryprobe nach korrigierter
Testmanifestregistrierung. Die erste vollständige Reihe hatte dieselben
Retention-Median/P95-Werte; Wiederholungen werden nicht zu zusätzlichen
UI-Sequenzen umgedeutet. Diese Methodenlaufzeiten sind **keine UI-Performance**, keine echte
initiale DB-Ladung und keine Garantie für Maximaltexte. Die zwei UI-Heapstände pro
Navigation schwanken auch negativ durch GC/JIT/alte Compositionreferenzen; daraus
wird keine zusätzliche Speicherersparnis abgeleitet. Domain-Objektgröße allein
begrenzt weder Compose noch nativen Gesamtprozessspeicher.

### Regressionsergebnisse und Messfehler auseinanderhalten

Vollständige Hostsuite (abschließend mit explizitem Task-`--rerun`): **1239 Tests, 0 Fehler, 0 übersprungen**
(585 App einschließlich 23 Assessmenttests, 650 Data, 4 Security). Der erste Versuch
mit der isolierten App-ID scheiterte am bestehenden AppModeTest: Der Test erwartet
zu Recht die Produkt-BuildConfig. Kein Test geändert/abgeschwächt; separate
`host.init.gradle` injiziert nur Testquellen bei unveränderter Produkt-App-ID,
danach Suite erfolgreich. Unveränderte Data-/Security-Tasks waren zunächst
UP-TO-DATE; abschließend wurden alle drei Testtasks ausdrücklich neu ausgeführt.
Keine produktive APK-Installation daraus.

Ein Zusatzlauf konnte zunächst den Navigationstest nicht registrieren, weil der
generierte Instrumentationrunner dessen Manifestnamen ersetzte. Das ist ein
Harnessfehler, kein Produkt-/Architekturbenchmark. Isolierter Runner jetzt explizit
im Init-Build gesetzt und die tatsächlich gepackten Einträge geprüft. Die vorherige
60-Sequenzen-Reihe hatte ihre Instrumentation erfolgreich registriert und bleibt
eigenständig gültig. Der große ursprüngliche Prozesscrash ist davon getrennt.

### Großer Lastfall und Grenzen

Large B und C: je zehn vollständige Sequenzen. Large A: zwei Abbrüche beim
erneuten Einstieg `TASKS_FOR_FORM`; keine vollständige Serie, kein valides
Median/P95 der gesamten A-Variante. Android meldet APP CRASH(EXCEPTION).
Ein dritter gezielter Diagnoselauf bestätigt `IllegalArgumentException` aus
`ConstraintsKt.throwInvalidConstraintException → createConstraints → Constraints →
SizeNode.getTargetConstraints → SizeNode.measure → ScrollNode.measure`.
**GEMESSEN:** Fehler in der Compose-Größen-/Scrollmessung, kein belegter OOM.
**HYPOTHESE:** `ProductScroll.kt: productScrollGeometry` übernimmt beim erneuten
asynchronen Laden die gesamte alte Listenhöhe als `heightIn(min=...)`; bei 1000
vollständig komponierten Taskrows überschreitet dies die darstellbaren Constraints.
Der Stack und die Reproduktion grenzen den Fehler ein; der konkrete numerische
Min-/Max-Constraintwert wurde nicht aufgezeichnet. B/C vermeiden den Loader auf
Treffern, beheben damit aber nicht allgemein die Layoutgrenze. Keine Produktkorrektur
im Assessment. Bereits davor dauerten Personen-/Taskansichten etwa zwei Sekunden.

| Large, bis Content-OnDraw | B Median/P95 ms | C Median/P95 ms |
|---|---:|---:|
| OPEN | 1290.04 / 1332.72 | 1344.62 / 1380.69 |
| PERSONS | 416.79 / 729.26 | 417.28 / 726.82 |
| TASKS | 418.62 / 816.36 | 432.90 / 833.33 |
| FUNCTIONS | 114.18 / 185.85 | 115.00 / 181.79 |
| MATERIAL | 1344.49 / 1399.30 | 77.14 / 108.78 |
| COSTS | 1408.68 / 1480.53 | 102.64 / 175.25 |
| HUB | 32.53 / 43.10 | 35.63 / 54.16 |
| EXISTING_TASK | 127.46 / 217.37 | 126.46 / 218.30 |
| NEW_TASK | 19.25 / 27.08 | 17.82 / 23.31 |
| EXISTING_PERSON | 1262.11 / 1270.52 | 1259.83 / 1270.25 |
| RAPID | 13.41 / 15.85 | 12.93 / 45.62 |

Large Frame-TOTAL Median/P95: B 21.66 / 1065.67 ms, C 23.63 / 1078.01 ms. Keine 100–150-ms-Garantie: Personen/Tasks bleiben selbst ohne DB mehrere hundert ms langsam; Frame-P95 rund eine Sekunde. Layout-/Projektionskosten sind ein eigenständiges Hindernis.

Workspace-Operationszähler pro vollständiger Sequenz: **A 12 Misses/0 Hits, B 3 Misses/9 Hits, C 1 Miss/11 Hits**. Hinzu kommen bewusst getrennte Listen-/Persondetailreads. Es sind autorisierte DB-Operationen, jeweils mit nativer Preflightverbindung plus Roomverbindung; nicht zwölf einzelne SQL-Statements. Die aktuelle Action, Generation und Version bleiben in diesen read-only Sequenzen unverändert. Kein Messnachweis für Writekonflikte daraus.

### UI-Heapstände (ungefähre Differenz, kein Cachebudget)

| Reihe | Java-Used-Heap nach OPEN minus vorher, bytes Median/P95 | Bereich Min…Max bytes |
|---|---:|---:|
| large/B | 1548288.00 / 1796032.00 | 1531904 … 1796032 |
| large/C | 3301376.00 / 3549120.00 | -17700872 … 3549120 |
| small/A | 778240.00 / 991824.00 | -5479424 … 991824 |
| small/B | 778240.00 / 993216.00 | 761856 … 993216 |
| small/C | 827392.00 / 1058752.00 | 794624 … 1058752 |
| typical/A | 843776.00 / 1042368.00 | 827392 … 1042368 |
| typical/B | 843776.00 / 1058752.00 | -3550120 … 1058752 |
| typical/C | 1007616.00 / 1255360.00 | -28573264 … 1255360 |

Negative Werte sind GC-/Kompositions-/JIT-Effekte des gesamten Prozesses, keine negative Objektgröße. Sie validieren das vorgeschlagene Storebudget nicht. Zusätzliche Native-/GC-Zähler der getrennten Scrollreihe werden separat berichtet.

### Personenmasken und Leadership sauber trennen

Ein zukünftiger Pseudonymeditor könnte bei PREPARATION direkt aus einem gültigen
Person-Read-Model öffnen. Das wäre ein eigener begrenzter UI-Einstieg, nicht das
heimliche Überspringen der heutigen `personDetail`-Kette einschließlich zusätzlicher
Leadershipinventory. Leadership-State/Personinventory verbleiben vorzugsweise beim
bisherigen Leadershipowner; der neue normale Store benötigt dafür weder
`SensitiveResult` noch `SensitiveOpened` oder Grantfelder. Eine fehlende Inventory
bedeutet UNKNOWN/LOADING, keine Freigabe oder Behauptung leerer P3b-Daten.
Personbasis und zusätzliche Bereiche dürfen separate reservierte Ladeflächen
nutzen. P3b-Payload wird weiterhin nur bewusst über den originalen Grantpfad gelesen.
Der neue Basiseditor ist im Geräteprototyp ausdrücklich nicht implementiert;
seine Version-/Person-CAS-/UI-/A11y-Abnahme bleibt Slice 6.

### Getrennte Scrollreihen (typical, je zehn Wiederholungen)

Reale Taskansicht, je acht synthetische Wischgesten pro Wiederholung.
Framezuordnung über ein Flag am asynchronen FrameMetrics-Callback; Randframes
können noch initiales Layout enthalten. Kein Perfetto-/Display-Present-Nachweis.

| Variante | erfasste Scrollframes | Scroll TOTAL ms Median/P95 | Max ms | FDs Anfang→Ende | Native Heap Anfang→Ende bytes | GCcount / GCzeit-Differenz |
|---|---:|---:|---:|---:|---:|---:|
| A | 1748 | 7.49 / 11.25 | 463.10 | 112→136 | 48226288→8865952 | 11 / 292 ms |
| B | 1752 | 7.51 / 11.32 | 464.14 | 112→136 | 48216800→9652448 | 12 / 327 ms |
| C | 1746 | 7.51 / 11.46 | 465.49 | 113→136 | 48212384→10399248 | 12 / 351 ms |

Alle drei Reihen PASS, Thermikstatus Anfang/Ende0. GC enthält ausdrücklich
20 angeforderte `System.gc()` für Heapstände; **keine natürliche produktive
GC-Rate** daraus ableiten. Native Endpointdifferenzen sind kein Beleg für
Retentionsfreiheit. Unterschiedliche Frames/Maxima ohne randomisierten Cross-over
beweisen keinen Store-verursachten Jankgewinn. RAM-Hits ändern die tatsächliche
Listenrenderarbeit nicht. Framework-FD-Warmup weiterhin enthalten; keine
aktionsübergreifende/mehrstündige Leakaussage.

Bei den A-Abstürzen wird der normale Fixture-Close nicht erreicht. Der isolierte
Prozess endet, Kernelhandles werden beim Prozessende freigegeben; das ist kein
Nachweis kontrollierten Buffer-Wipes oder erfolgreichen finally-Cleanups.
Verschlüsselte synthetische Orphan-Unterbestände können im isolierten Testpaket
verbleiben. Keine Produktdatenbereinigung und keine Klartext-Cachedatei eingeführt.

### Widerrufskosten im Geräteprototyp

Lock + synchrones Model-/Store-Clear (Methoden-/Queuezeit, **kein** optisch gemessener Lockframe), Median/P95 ms:

| Größe | A | B | C |
|---|---:|---:|---:|
| small | 3.79 / 8.45 | 1.39 / 6.83 | 5.72 / 7.27 |
| typical | 6.28 / 9.01 | 5.91 / 9.26 | 5.47 / 7.24 |
| large | keine vollständige Reihe | 6.99 / 9.40 | 0.97 / 9.41 |

### Isolierte bestehende Workspace-Storage-Regression

**Erneut auf Samsung ausgeführt:** `P11WorkspaceStorageTest`, **12/12 bestanden**,
84,911 s. Reale SQLCipher-/Room-Fixtures ausschließlich in
`org.pag.data.unlockprobe`. Enthalten sind exakte Batch-/Minimalprojektionen,
Status-/Generation-/stale Actionbindung, Queue-/Publication-Cancellation und
frischer erlaubter Zugriff, explizite Bereichspayloads sowie warme Wiederholung.
Warme FD-Zählung **83 → 83**; PSS-Punkte **65226 → 56838 KiB**, keine allgemeine
Speicherersparnis/-Leakfreiheit daraus behauptet. Keine Produktdatenbank geöffnet.
Diese zwölf Fälle sind bestehende Regression, keine zwölf neuen Stufe-2-
Writeintegrationstests. JPEG/PNG/UTF-8/PDF/Kamera/SAF wurden im Assessment nicht
nochmals als neue Produktgeräteabnahme ausgegeben; ihre Produktquellen blieben
bytegleich und die bestehenden Hosttests bleiben Regressionsbestandteil.

## 16. Empfehlung und Entscheidung

**B — Stufe 2 nur mit zusätzlichen Gegenmaßnahmen geeignet. Keine produktive
Freigabe, keine GRÜN-Bewertung der vollständigen Zielarchitektur.**

Der synthetische Nachweis zeigt einen echten Nutzen: Gleiche reale Compose-
Bereichsansichten können nach vorbereiteter Initialladung ohne weitere Workspace-
DB-Operationen wechseln. Schon ein vorhandener Hub-Snapshot beschleunigt die
abgedeckten Bereiche; für Material/Kosten braucht es zusätzliche Aufnahme.
Der unveränderte reale Taskformularpfad ist bereits schnell. Eine DB-freie
Personen-/Leadershipdetailmaske wurde nicht implementiert oder behauptet.

Die Ziele sind nicht vollständig erreicht: Große Personen-/Tasklisten bleiben
mehrere hundert ms langsam und weisen etwa eine Sekunde Frame-P95 auf; der
aktuelle A-Lastpfad stürzt bei Rückkehr in den Aufgabenbereich ab. Die Diagnose bestätigt eine Compose-Constraints-Exception; der vermutete
Beitrag der reservierten alten Listenhöhe und skalierbare UI-Grenzen müssen vor
Produktfreigabe korrigiert und regressionsgeprüft werden.
Ein 8-MiB-Domainbudget alleine verhindert weder Layoutgrenzen noch Compose-OOM.
Header-/Dokumentmetadaten mit nichtleeren Maximalbeständen, umfangreiche Rollen,
Unicode-/Maximalfelder und vollständige produktive Writes sind noch nicht als
Stufe-2-Integration gemessen/getestet. Die vorhandene Klasse-A-P11-Arbeit bleibt
vollständig erhalten; dieses Assessment implementiert keinen produktiven Cache.

Zwingend vor Umsetzung: ausdrückliche Zustimmung zu mehr gleichzeitig verfügbarem
RAM-Klartext und späteren Integritäts-/Fehlerprüfungen bei Treffern; budgetierte
Admission vor Materialisierung; begrenzte/lazy UI und reproduzierbare Laststabilität;
lückenloser zentraler Mutations-/Invalidierungsvertrag samt Stale-Form-/Commit-
Tests; unveränderte Coordinator-/Viewer-/P3b-Grenzen. Danach kleine Slices aus
Abschnitt 13, unabhängiger Review und Issue #26 als Release-Gate.

**Unverändert/erhalten:** Produktquellen, Schema11, Key-/Room-/SQLCipher-/Tink-
Lifecycle, Dokumentviewer/Parserisolation, Import/Delete, Credentials, Deadline,
Generation und FLAG_SECURE. Bestehender Signaturschlüssel unverändert.
**Absichtlich nicht gleichgesetzt:** Unveränderte Produktquellen heute bedeuten
nicht unveränderte Schutzannahmen nach einer späteren Storeeinführung.

Es gab ausschließlich isolierte Testinstallationen. Keine Produktinstallation,
kein Produktdatenzugriff/-export/-reset, keine Produktcredentials, kein Commit,
Push, Merge oder Release. Keine neue manuelle Produktabnahme angefordert oder
vorweggenommen. Für die Zukunft sind insbesondere Lock beim Laden/Speichern,
kein Autoopen, große Schrift/TalkBack, alle Writekategorien und Budgetdegradierung
am integrierten Produkt erneut abzunehmen; die Benutzerbestätigung der zweiten
P11-Runde bleibt davon getrennt mit ihren ausdrücklich genannten Grenzen.

## 17. Vollständige Dateiliste des lokalen Arbeitsstands

45 P11-Pfade waren vor diesem Assessment bereits verändert/untracked. Deren
Sourcebytes wurden per SHA-256-Bestandsliste erhalten; einzige gezielte Ergänzung
in dieser Gruppe ist die Benutzerabnahme im P11-Bericht. 14 neue Assessmentpfade,
insgesamt 59 lokale geänderte/untracked Dateien. Keine Produktquelle verändert.
Builds/Logs/APKs/Keystore und synthetische Testbestände liegen außerhalb dieser
Git-Dateiliste im ignorierten Arbeitsbestand, nicht in einem Commit.

| Datei | Zuordnung |
|---|---|
| `app/src/androidTest/AndroidManifest.xml` | Vorhandenes P11 unverändert erhalten |
| `app/src/androidTest/kotlin/org/pag/prototype/P11LoadingGeometryProbe.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/androidTest/kotlin/org/pag/prototype/P11PdfVariantProbe.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/androidTest/kotlin/org/pag/prototype/P11PerformanceProbe.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/androidTest/kotlin/org/pag/prototype/P11UnlockProbe.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/androidTest/kotlin/org/pag/prototype/P11ViewerPhaseProbe.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/MainActivity.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/AndroidDocumentPdfRenderer.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/DocumentImageDecoder.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/ExpenseModel.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/MaterialModel.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/MemoryReportModel.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/MemoryReportScreen.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/PagShell.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/ProductScroll.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/ProtocolModel.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/main/kotlin/org/pag/prototype/product/TaskScreen.kt` | Vorhandenes P11 unverändert erhalten |
| `app/src/test/kotlin/org/pag/prototype/product/WorkspaceBatchTest.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/androidTest/kotlin/org/pag/data/product/DocumentReconciliationStorageTest.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/androidTest/kotlin/org/pag/data/product/P11DatabaseBenchmark.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/androidTest/kotlin/org/pag/data/product/P11KeyFormatBenchmark.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/androidTest/kotlin/org/pag/data/product/P11WorkspaceStorageTest.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/DocumentDelete.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/DocumentMaintenance.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/DocumentReconciliation.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/ProductActionRepository.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/product/ProductWorkspaceRepository.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/prototype/b2/B2Driver.kt` | Vorhandenes P11 unverändert erhalten |
| `data/src/main/kotlin/org/pag/data/prototype/b2/B2Native.kt` | Vorhandenes P11 unverändert erhalten |
| `docs/product-p11-active-workspace-assessment.md` | Assessment neu |
| `docs/product-p11-performance.md` | P11-Bericht: Abnahme ergänzt |
| `domain/src/main/kotlin/org/pag/domain/Workspaces.kt` | Vorhandenes P11 unverändert erhalten |
| `tools/p11/compare.py` | Vorhandenes P11 unverändert erhalten |
| `tools/p11/frames.py` | Vorhandenes P11 unverändert erhalten |
| `tools/p11/probe.init.gradle` | Vorhandenes P11 unverändert erhalten |
| `tools/p11/startup.py` | Vorhandenes P11 unverändert erhalten |
| `tools/p11/summarize.py` | Vorhandenes P11 unverändert erhalten |
| `tools/p11assessment/AndroidManifest.xml` | Assessment neu |
| `tools/p11assessment/README.md` | Assessment neu |
| `tools/p11assessment/android/org/pag/prototype/ActiveWorkspaceAssessmentProbe.kt` | Assessment neu |
| `tools/p11assessment/android/org/pag/prototype/AssessmentMemoryProbe.kt` | Assessment neu |
| `tools/p11assessment/extras.py` | Assessment neu |
| `tools/p11assessment/fixture/org/pag/data/product/AssessmentSyntheticFixture.kt` | Assessment neu |
| `tools/p11assessment/host.init.gradle` | Assessment neu |
| `tools/p11assessment/probe.init.gradle` | Assessment neu |
| `tools/p11assessment/run.py` | Assessment neu |
| `tools/p11assessment/shared/org/pag/assessment/AssessmentStore.kt` | Assessment neu |
| `tools/p11assessment/summarize.py` | Assessment neu |
| `tools/p11assessment/test/org/pag/assessment/AssessmentSessionTest.kt` | Assessment neu |
| `tools/p11assessment/test/org/pag/assessment/AssessmentStoreTest.kt` | Assessment neu |

## 18. UI-Stabilisierung nach WIP 1025941 (18.10.2026)

Dieser Nachtrag ersetzt keine Stufe-2-Freigabe. Produktiv geändert werden nur
Layout/Scroll-Metadaten, sichtbare Listenkomposition und eine semantisch äquivalente
Taskzählung. Keine Mutationsepoch, kein ActionSessionStore, keine neue DB-/Key-/
Dokumentvorhaltung. Die historischen Tabellen oben bleiben als damalige Befunde
stehen; neue Nachweise sind ausdrücklich separat.

**GEMESSEN:** Erneute isolierte Reproduktion auf Samsung SM-M526BR/API33:
`heightIn(min=269909 px)` bei eingehenden Constraints `(minWidth=0,maxWidth=954,
minHeight=0,maxHeight=Int.MAX_VALUE)` löst `IllegalArgumentException` in
`ConstraintsKt.createConstraints → SizeNode.measure` aus. Die Messung erfolgt vor
dem fehlschlagenden Modifier, ohne Exceptionmaskierung oder Dateninhalte.
Der exakt gleiche Zahlenfall ist zusätzlich als fokussierter Hosttest enthalten.
Damit ist die bisherige Hypothese zur übernommenen **gesamten Inhaltshöhe** bestätigt.

**Korrektur:** `ProductScreen` bestimmt die echte, begrenzte Viewporthöhe mittels
`BoxWithConstraints`, abzüglich des bestehenden vertikalen Paddings. Nur diese
Höhe darf während eines Reads als Layoutminimum dienen. `ProductScroll` speichert
keine Inhaltshöhe mehr. Es gibt keinen künstlichen 100000-/262000-px-Ersatzwert,
kein Abschneiden von Inhalten und keinen catch-Fallback. Pixelpositionen werden
nach dem Contentlayout wiederhergestellt; während des Loads wird nicht gegen eine
kurze Platzhalterliste auf null zurückgescrollt.

**GEMESSEN:** Der vorher abbrechende große A-Fall besteht mit **nur** dieser
Viewportkorrektur zehn vollständige Sequenzen. Dieser getrennte Zwischenstand
lief vor der Lazy-Optimierung und dient als valide A-Vergleichsbasis. Reine
Crashbehebung beseitigt die große Listenkomposition noch nicht.

**BEOBACHTET:** Personen-/Taskübersichten werden bisher vollständig in einer
`Column.verticalScroll` komponiert. `affectedCount` hat pro Aufruf erneut
`active` gefiltert und sortiert, obwohl eine Zählung keine Sortierung benötigt.
Die isolierte Android-CPU-Probe vergleicht eingefrorenen alten Algorithmus und
unsortierten Scan auf denselben 200 Personen/1000 Aufgaben, fünf Warmups, zwanzig
Messungen, alternierende Reihenfolge. Ergebnis für alle 200 Counts zusammen:
75,75 / 77,90 ms Median/P95 → 11,32 / 13,28 ms. Das ist ein Methodenprofil,
**keine UI-Latenz**; die Status-/Referenzverteilung dieser CPU-Fixture ist nicht
mit einer Produktgesamtzeit addierbar. Gleichheit aller Zähler wird geprüft.

**Listenänderung:** Personen-/Taskübersichten in ACTIVE und PREPARATION haben
jetzt einen einzigen begrenzten `LazyColumn`-Root, nicht eine Lazy-Liste unter
unbegrenztem `verticalScroll`. Stabile typisierte Entitykeys, gleiche Reihenfolge,
gleiche Zeilen-/Button-/Headingsemantik; Formulare/Details bleiben im bisherigen
Pfad. Header und Navigation werden wiederverwendet, Footer/Vorlagen/abgeschlossene
Aufgaben bleiben erreichbar. Taskzählung scannt direkt; keine neue Domain-Map,
kein remembered Payloadindex, kein Ergebnis- oder aktionsübergreifender Cache.
Abgeleitete Aufgabenlisten werden weiterhin nur als aktuelle UI-Projektion
berechnet, nicht über das Verlassen der Ansicht hinaus vorgehalten. Workspace-
Reads, Berechtigungen, SQLCipher-/Tink-/PDF-Lifecycle sind unverändert.

Scrollrückkehr speichert ausschließlich feste Destinationnamen und numerische
Pixel-/Lazyindex-/Offsetanker, begrenzt auf 64 Einträge, gebunden an aktuelle
Generation/Action. Keine Entity-IDs oder Inhalte in dieser Ankerkarte, kein
SavedState/rememberSaveable. Lock/Actionwechsel verwirft die Komposition samt
Ankerkarte. Bei unverändertem Bestand wird Index/Offset exakt restauriert; bei
verändertem Bestand gelten die normalen Lazy-Key-/Indexbegrenzungen, keine
Zusicherung, dass eine gelöschte Zeile weiter als Anker existiert.

**GEMESSEN:** Isolierte UI-Probe bei lokalen Schriftfaktoren 1, 2 und 3,5 erreicht
jeweils die letzten der 1000 Aufgaben und 200 Personen, navigiert dreimal
Aufgaben→Lagebild→Aufgaben und prüft Index/Offset (≤2 px Toleranz), Labels und
Scroll-Actions im Accessibilitybaum. Testfenster-Paketprüfung vor Baumzugriff;
keine Analyse anderer Apps. Lock während schneller Navigation, wartender realer
SQLCipher-Read/late Callback, neue synthetische READY-Generation ohne Autoopen,
keine Formulardaten/P3b-Freigabe nach Lock werden geprüft. Keine Änderung der
Systemschrift oder realen Credentials. Die Session dieser Geräteprobe bleibt
synthetisch; echte Deadlines/Coordinator-Gates bleiben separate Hostregression.
Die erste Accessibilityabfrage war vor der Baumaktualisierung erfolgt; der Test
wartet jetzt auf tatsächliche Semantik statt die Anforderung zu entfernen.
**NICHT GETESTET:** Neue manuelle TalkBack-Endabnahme; strukturierte Semantiktests
sind keine vollständige Screenreader-Bedienungsabnahme.

## 19. Vollständiger zukünftiger Mutations-/Invalidierungsvertrag

**Spezifikation + test-only Proof, keine produktive Integration.** Zuständig ist
später ein instanzlokaler `ActionMutationObserver` im bestehenden Composition Root,
auf einem serialisierten Owner, ausschließlich Metadata: Action/Generation,
Berechtigungsrevision, Sequenz/Epoch, betroffene Projektionsbits, Pending/Outcome.
Keine Domain-Payload, DB-/Key-Lease oder eigene Securitypolicy darin. Nur Data/
Usecase-Adapter dürfen Transaktionsergebnisse melden; Compose erhält niemals einen
Port, mit dem es einen Commit behaupten oder eine Berechtigung schaffen kann.

Projektionscodes: **A** Action/Status; **P** Personen/Pseudonyme; **O** Rollen,
Funktionen, Pläne und Status; **T** Tasks/Events; **Z** Hub-/Personen-Aufgabenzähler;
**M** Material/Summary; **K** Kosten/Payments/Summe; **R** Protokollheader/Summary;
**G** Gedächtnisprotokollheader/Summary; **D** Dokumentasset-Metadaten/Summary.
P3b-State/Inventory/Grants sind ein separater Owner, kein allgemeiner Workspace.

Gemeinsamer Vertrag für jede folgende Zeile:

- **BEGIN:** Nach originaler Scope-/Syntaxzulassung, vor Queue/erster Sideeffect,
  Pendingtoken erzeugen und betroffene Projektionen dirty markieren. Alte Reads
  verlieren ihre Epoch. Keine weitere hitfähige Projektion aus dem betroffenen
  Zustand und keine zweite implizite Writeausführung. Observer ersetzt keine
  Fach-/Storageprüfung; unzulässige Mutation bleibt unzulässig.
- **Bestätigung:** Native COMMIT-/Transaktionsrückkehr ist von vollständigem
  DB-/Lease-Cleanup und originalem `StorageContext.publish` zu unterscheiden.
  Ein erfolgreicher Commit kann ohne UI-Publication enden. Keine Meldung
  „gespeichert“ vor tatsächlichem bestätigtem Commit und gültiger Publication.
- **Fehler:** Sicher abgelehnt/rollback → kein Erfolg, betroffene Projektion
  konservativ dirty bis genau einem autorisierten Reload. Commit-Ausgang unklar/
  Cleanupfehler → kein Erfolg, Scope unbrauchbar; originalen Storage-/BLOCKED-
  Vertrag beachten, kein automatischer Retry/zweiter Write. Fachliche INVALID/
  STALE-Ablehnung ist nicht automatisch Storagekorruption.
- **Revoke:** Alle Tickets unbrauchbar; alte Commit-/Readcallbacks dürfen den
  neuen Scope nicht ändern. Bestätigter DB-Commit bleibt DB-Wahrheit, auch wenn
  seine UI-Bestätigung widerrufen wurde. Neue Authentifizierung liest bewusst neu.
- **Aktualisierung:** Nur vollständiger bestätigter Projektionspatch für die
  aktuelle Epoch oder einmaliger kontrollierter Reload. Keine Teilpatches, die
  veraltete Counts als aktuell markieren. Rechenarbeit außerhalb des Coordinator-
  Monitors; im Gate nur kurze Gültigkeitsprüfung und atomarer Referenzwechsel.

| Mutation / tatsächlicher heutiger Einstieg | Betroffen / benötigte Invalidierung | Bestätigungspunkt und besondere Fehler-/Widerrufsgrenze |
|---|---|---|
| `ProductActionRepository.createAction` | Aktionsliste; neuer Draft nicht im aktiven Workspace | Autorisierte Inserttransaktion + Cleanup + Publication; kein automatischer Aktions-/Storewechsel |
| `updateAction` / `changeAction(expected,changes)` | A, ggf. gesamter abgeleiteter Header | Frische persistierte Action und bestehender Expectedvergleich; kein Update aus alter Formularversion |
| `advanceActionStatus` / `advanceAction(id,expectedStatus)` | Gesamter Scope inkl. aller Rechte/Editoren/Zähler | Persistierter Statusvergleich, Transaktion, Publication; kein PREPARATION-Write aus ACTIVE/C |
| `ProductPersonRepository.createPerson` | P/O/Z, Formularoptionen T/M, Hub | Originale Pseudonym-/Duplicate-/Statusprüfung, Insertcommit |
| `updatePerson` / `changePerson` | P/O, T-/M-Labels, sichtbare R-Referenzprojektionen, Z | **Kein vollständiger Expected-Person-CAS heute.** Frische Existenz/Duplicate/Statusprüfung ist keine universelle Konfliktgarantie |
| P3a-Person löschen | Würde P/O/T/Z/M/R und Formularreferenzen betreffen | **Kein allgemeiner P3a-Delete-Port im heutigen PersonRepository/DAO.** Nur Zukunftsspezifikation, keine bereits vorhandene Lösch-/CAS-Garantie behaupten |
| `ProductOperationsRepository.createRole` | O/Z und T-/Formularoptionen | PREPARATION/ACTIVE gemäß Originalregeln, fachlicher Duplicatecheck/Commit |
| `renameRole`, `deleteRole` | O, T-/M-Labels, Rollen-/Funktionszähler | Heute PREPARATION; Löschung nur ohne verbotene Referenzen; In-use bleibt Ablehnung, keine implizite Löschung |
| `planRoles` | O, Hauptrollen-/Hubprojektion | PREPARATION, gültige actionlokale Referenzen, atomarer Plancommit |
| `updateSnapshot(expected, status, role, place)` | O/Z, Status-/Funktions-/Ortsprojektion | ACTIVE, exakter bestehender OperationalState-Vergleich; keine Statusmutation aus stale Formular |
| `ProductTaskRepository.create` | T/Z, P-Aufgabenzähler, M-Taskoptionen | Task+CREATED-Event in derselben Transaktion, korrekte Actionreferenzen |
| `edit(expected,input)` | T/Z, Personen-/Rollenlabels, M-Verknüpfungsanzeige | Bestehender voller Taskvergleich, Originalstatusregeln; keine optimistic-save-Bestätigung |
| `transition(expected,status)` | T/Events/Z/P-Aufgabenzähler | Taskstatus und minimales Event atomar; einmaliges Event, kein Callback-Retrywrite |
| `delete(expected)` | T/Events/Z/M-Referenzoptionen | Nur PREPARATION gemäß heute; kein stilles Entfernen im Cache vor Commit |
| `ProductMaterialRepository.create/edit/delete` | M/Count, Hub, betroffene Formularoptionen | PREPARATION/ACTIVE; edit/delete erwartete Position. Andere Fachbereiche nicht stillschweigend mitmutieren |
| `ProductExpenseRepository.create/edit/delete` | K/Payments/Count/Summe, Hub | P/A/C gemäß P8; erwartete Position bei edit/delete, Total-/Paymentvalidierung bleibt Data |
| `ProductProtocolRepository.createEntry` | R/Summary und gezielt Detailowner | ACTIVE/COMPLETED bewusst; Entry/Originalrevision/Relationen atomar |
| `reviseEntry`, `markMistaken` | R, betroffene aktuelle Revision/Detailanzeige | Erwartete Revision + Actionstatus, append-only; keine Historie überschreiben/normal löschen |
| `ProductMemoryReportRepository.create` | G/Count, neuer Detailowner | Erste Section/Header in originaler Transaktion, keine vorzeitige Draftzähleränderung |
| `saveSection(expected,section)` | G/Revision/Summary und genau dieser Editor | Persistierte Headerrevision/Fragebogen-/Conflictregeln, kein doppeltes Section-save durch Refresh |
| `complete(expected)` | G/Draft-/Completedcount, Detailowner | Nur vollständiger DRAFT nach Regeln, bestätigte neue Headerrevision |
| `addAddendum(expected,text)` | G/Revision, betroffener Detailowner | Append-only, ursprünglicher Text bleibt, kein generischer Volltextcache |
| Einzelnes Gedächtnisprotokoll löschen | Würde G/Detailowner betreffen | Kein solcher öffentlicher Port heute; zukünftiger Pfad muss explizit angebunden werden |
| `ProductDocumentRepository.importDocument` / Staging/Publication | D ab Beginn dirty; kein READY-Asset aus Reservephase | Mehrere DB-/Dateisystemschritte: Reserve/Write/Publish/Mark/Finish. Logischer Erfolg erst nach kompletter Publication + finalem Originalgate; Zwischencommit ist kein fertiger Import |
| `deleteDocument(expected)` / DocumentDelete | D + betroffener Viewerrequest, Counts erst nach bestätigtem Schritt | Deletejournal/Dateiarbeit/Jobabschluss nach bestehender Lane; Fehler/Abbruch bleiben recoverybedürftig, kein alter Assethit während Delete |
| `DocumentReconciliation.run`, `reconcileProductDocuments`, Maintenancebarrier | Gesamter Scope unzugänglich; D/Recoverybestand neu | MAINTENANCE ist nie READY; keine Storeübernahme in neue Generation; jeder Recoverycommit behält Originalgates |
| `ProductLeadershipRepository.setup` | Separater Leadership-State/Inventoryowner, ggf. sichtbarer Status | Passphrase/Keystore-/Root-/Epochvertrag unverändert, nie Payload oder Passphrase im Observer |
| `save(grant,data)`, `deleteArea(grant)` | Nur betreffende P3b-Area/Inventory/Grantowner | Originaler Grant, Lifetime, Expectedroot/Revision und COMMIT-/Publicationgates; normale Navigation erteilt kein P3b-Recht |
| Leadership `deletePerson` | Löscht **nur sensible Areas dieser Person**, nicht P3a-Person | DAO `removePerson` betrifft `sensitive_person_areas`; Pseudonym/Taskzähler nicht als gelöschte Person darstellen |
| Leadership `deleteAction` | Löscht **nur Leitungsschutz/sensible Areas**, nicht ActionRow | DELETE_PENDING-Intent/Keystorelöschung/Rootcleanup; nicht mit allgemeiner Aktionslöschung verwechseln |
| Allgemeine Aktions-/Retentionlöschung | Gesamter zukünftiger Scope, alle Referenzen | Noch kein allgemeiner öffentlicher ActionRepository-Delete-Port heute; vor späterem Anschluss zwingender Gate-/Recovery-/Observerprüfpunkt |

Diese Matrix ergänzt und präzisiert die groben hypothetischen Deletezeilen aus
Abschnitt 8: Methodennamen allein beweisen keine fachliche Personen-/Aktionslöschung.
Die Produktwriter wurden für diesen Auftrag nicht umgebaut.

### Konkreter Port-/Zustandsentwurf

`begin(scope, expectedProjectionVersions, affectedParts) -> MutationTicket?`
(identitätsgebundenes, einmaliges Ticket),
`recordOutcome(ticket, ConfirmedCommit | DefiniteRollback | Unknown)`,
`publishCommittedProjection(ticket, completeProjectionPatch, context)` und
`requestAuthoritativeRead(scope, parts) -> ReadTicket` als getrennte Ereignisse.
Scope = Action-ID + READY-Generation + bestehende Fachrechte/Revision;
Ticket = Scope + monotoner Epoch/Sequence + betroffene Allowlist-Teile.
Versionen sind lokale Invalidierungsmetadaten, **kein** DB-Manipulationsnachweis.
Alle Daten-/Commitautorität verbleibt bei Originalrepositories/StorageAccess.

Vorläufig ein aktiver Write pro Scope; weitere bewusste Writes nicht automatisch
wiederholen, sondern bestehende Busy-/Bestätigungsverträge anwenden. Ein aktiver
Reload pro Scope/Version; gleiche Anforderungen coalescen, nach Querybeginn neu
angeforderte Teile höchstens in einem nachfolgenden autorisierten Read, kein
unbegrenztes Fan-out und kein stiller Prefetch. Mutation widerruft alte Readtickets;
auch derselbe Callback darf nur einmal publizieren. Frischer Reload invalidiert
verspätete alte Projektionspatches. Scopewechsel/Lock/Storage-Revoke widerrufen alle
Tickets. Bei mehrstufiger Dokumentarbeit bleibt Pending über die gesamte logische
Operation bestehen; Subcommitmeldungen dürfen keinen fertigen Cacheeintrag schaffen.

`MutationContractProof` unter `tools/p11assessment/shared` enthält nur diese
Metadata-Zustandsmaschine; Tests prüfen Pendinghits, Single-flight/Readcoalescing,
Commit vs Publication, Rollback/Unknown, vollständige Patches, alte/doppelte
Callbacks, neue Mutation, Action-/Permission-/Generationswechsel und Revoke nach
Commit. Das sind isolierte Protokolltests, **keine** real integrierten SQLCipher-
Write-/Commit-/Recoverytests. Insbesondere wird Personrename dadurch nicht zu CAS.
Die vollständige produktive Integration bleibt unfreigegeben und ungetestet.

## 20. Testbarer Budget-/Admissionvertrag (noch nicht produktiv)

8 MiB retained Domain-Payload bleiben ein **Vorschlag**, keine nachgewiesene harte
ART-/Compose-/Nativegrenze. Produktlimits unverändert: Dokumente100/Action,
25MiB/Dokument, 250MiB/Action; Payments1–32/Position, Task-/Kosten-/Materialtitel120,
Paymentlabel80, Materialmenge40/Ort80, Protokoll-/Fragebogenfeldlimits wie oben.
Person-/Task-/Role-/Material-/Expense-/Revision-/Reportgesamtzahlen bleiben
unbeschränkt; manche Action-/Pseudonym-/Rollentexte besitzen kein generelles
Längenmaximum. Ein einzelner riesiger Wert darf Admission nicht erst nach seiner
vollständigen Materialisierung scheitern lassen.

Verbindliche spätere Phasen innerhalb derselben autorisierten Readtransaktion:

1. Zulässige Kategorien und Actionstatus bestimmen, Counts/Relationen sowie
   Textlängenaggregate lesen, noch keine unbeschränkte `List<Row>` erstellen.
   Aggregate müssen eindeutige Einheit tragen: SQL-Codepoints, UTF16-Codeunits,
   UTF8-Bytes nicht gleichsetzen. Jede Konvertierung/Add/Mul overflowfest in Long.
2. Typbezogenen konservativen Charge für Objekte, Strings/Backingarrays,
   Referenzen, Maps/Lists/Hash-Kapazität und Alignment prüfen. Bis vier Nutzbytes
   je Unicode-Codepoint; bei UTF16-Einheiten absichtlich konservativ überzählen.
   Keine Stringinterner-/Deduplikationsannahme. Geteilte Referenzen dürfen nur
   nach explizitem Ownershipnachweis entlasten; zunächst jede Repräsentation
   separat belasten. Formularkopien, Compose und Übergangssnapshots separat.
3. Nur bei ausreichender Admission in begrenzten Batches materialisieren;
   vor der nächsten Row Kosten/Längen nachprüfen und zwischen Schritten Original-
   Storagegates prüfen. Ein Cursor-/DAO-Pfad, der davor bereits ganze unbeschränkte
   Strings/Listen liest, erfüllt diesen Vertrag nicht. Keine DB-/Key-Lease über
   UI-Navigation oder langes Warten halten; bei Überschreitung geordnet schließen.
4. Budget REFUSED: kein Teilergebnis als vollständig/leer präsentieren, keine
   Hintergrund-Writes, keine höhere Grenze automatisch, keine zweite Action.
   Betroffenen Bereich explizit NOT_LOADED/bedarfsgeladen über bisherigen Port
   bereitstellen. Verweigert Admission vor dem vollen Snapshot, bleibt ein
   nachfolgender Bereichsread eine echte autorisierte DB-Operation mit unverändertem
   Preflight. Kein unkontrolliertes Retry-/Refreshloop. Auch dessen bisherige
   unbeschränkte Materialisierung ist vor einem solchen automatischen Fallback
   gesondert zu begrenzen. Solange der Originalport keine ausreichende Grenze
   durchsetzen kann, bleibt der Teil kontrolliert nicht verfügbar statt den
   verweigerten großen Bestand sofort doch vollständig zu laden. Eine bewusste
   Bereichsauswahl ist keine Erlaubnis für unbeschränkte Allokation. Die
   Budgetentscheidung beweist keine beliebige Gesamt-App-OOM-Freiheit.
5. Ungültige negative Counts/inkonsistente Rows/Inventar-/DB-Fehler sind **nicht**
   normale Budgetknappheit: originale Validierung/fail-closed. Bloßer Chargeoverflow
   oder gültiger, aber zu großer Bestand ist Budgetverweigerung ohne behauptete
   Korruption. Ein SQL-Aggregatfehler wird nicht pauschal als Budget REFUSED maskiert.

`BudgetContractProof` ist ausschließlich ein isoliertes Prüfmodell mit expliziten
128-/64-/8-/4-Byte-Chargeannahmen; **keine** zertifizierte tatsächliche Objektgröße.
Es prüft Boundary, Überlauf, negative Aggregate, supplementary Unicode, konservative
Doppelrepräsentation und sticky Refusal vor weiterer Materialisierung. Vor
produktiver Umsetzung müssen DTO-/ART-Gewichte und Maximalfelder kalibriert und
alle Reader wirklich begrenzt sein. Keine neuen Produktlimits/Migrationen oder
Cacheaktivierung erfolgen hier. Budgetfehler verleihen keine Storage-/Sessionrechte.

## 21. Öffentliche Diagnoseangaben

Aktuelle P11-Dateien wurden auf gerätespezifische Serienkennungen und absolute
benutzerspezifische Home-/SDK-Pfade geprüft. `run.py` und `extras.py` beziehen ADB
über `--adb`/`ADB`, Serienziel über `--serial`/`ANDROID_SERIAL`; fehlendes Ziel wird
abgelehnt, kein willkürliches erstes Gerät. Zusätzliche Auswahl-/Ausgabeparameter
ändern nicht Fixturegrößen oder Messergebnisse. README/Installbeispiele nutzen
neutrale Umgebungsvariablen. Gerätemodell/API, Messzahlen und technische Aussagen
bleiben erhalten. Keine echten Kennungen durch erfundene echte Kennungen ersetzt.

**Wichtig:** Bereinigung aktueller Dateien entfernt frühere Inhalte nicht aus dem
bereits veröffentlichten WIP-Commit oder dessen Kopien. Keine History-Rewrite-,
Force-Push- oder destruktive Repositorybereinigung. Git-Autor-Metadaten und frühere
veröffentlichte Kennungen sind eine getrennte Datenschutzentscheidung, falls
weiterer Bereinigungsbedarf besteht. Zertifikats-/Buildfingerprints sind keine
privaten Schlüssel; es wurden weder Keystore noch Credentials ins Repo übernommen.

## 22. Einschränkungen und offene Freigaben

Stufe2 bleibt unfreigegeben: zusätzliche gleichzeitige RAM-Klartexte, längere
Retention, spätere DB-Integritätsfehlererkennung auf Hits, belastbares Budget und
vollständige Writerintegration brauchen gemeinsame Entscheidung und unabhängigen
Review. Die neuen test-only Modelle sind keine produktive Versions-/CAS-Garantie.
Personrename und zukünftige P3a-/Aktionslöschung erfordern besonders sorgfältige
Spezifikation. Keine bestehende Deadline, KDF/Keyformat, SQLCipherprüfung, Room-
Lebensdauer, P3b-, Tink-, Namespace-, PDF-, Backup-/Permissiongrenze geändert.

Kein Produktupdate in diesem Auftrag; keine neue manuelle Geräteabnahme vorweggenommen.
Issue #26 bleibt Gesamt-Release-Gate. Große unbeschränkte Texte/Role-/Historybestände
und vollständige reale TalkBackbedienung sind nicht durch die 200/1000-Row-Fixture
bewiesen. Neue A/B/C-Daten bleiben isolierte Tests, keine automatische Analyse
realer Produktdaten. Verbliebene DB-Latenz wird ohne Sicherheitsabbau ausgewiesen.

## 23. Neue Gerätemessungen der UI-Stabilisierung

**GEMESSEN, 18.10.2026:** Samsung SM-M526BR, Android13/API33; unveränderte
Small/Typical/Large-Fixtures und Sequenz aus Abschnitt10, jeweils zehn vollständige
Wiederholungen für A/B/C. Alle neun Reihen bestehen. Vorher großes A separat mit
nur Viewportfix vollständig ausgeführt; vorher großes C unveränderter WIP-Pfad,
ebenfalls frisch zehnmal gemessen. Keine verkürzte Fixture und kein Ausschluss
langsamer Wiederholungen. P95 ist nearest rank, bei n=10 also der Maximalwert.
Reihenfolge statt randomisiertem Cross-over; JIT/GC/zeitliche Geräteeffekte bleiben.
Android-Thermalstatus jeweils0 an Reihenanfang/-ende, keine Garantie konstanter
CPU-Frequenz. Schriftfaktor für Lastmessungen unverändert; die separaten lokalen
1/2/3,5-Schrifttests sind keine manipulierten schnelleren Lastfixtures.

### Direkter Vorher-/Nachhervergleich großer Aktionen

Millisekunden Median / P95 bis Content-OnDraw; gleiche reale UI und SQLCipher.
A = Produktpfad, C = ausschließlich test-only vorbereiteter RAM-Bestand.

| Pfad | Vorher | Nachher | Mediandifferenz | Verbesserung |
|---|---:|---:|---:|---:|
| A Initiale Öffnung | 1285.74 / 1348.31 | 1291.57 / 1347.39 | -5.83 ms | -0.5% |
| A Personen | 1681.77 / 1972.66 | 1281.28 / 1304.30 | 400.49 ms | 23.8% |
| A Aufgaben | 1670.68 / 2041.84 | 1290.36 / 1302.41 | 380.32 ms | 22.8% |
| A Funktionen | 1258.50 / 1275.07 | 1261.89 / 1276.10 | -3.39 ms | -0.3% |
| A Lagebild zurück | 1280.81 / 1295.01 | 1284.82 / 1295.33 | -4.01 ms | -0.3% |
| A Aufgaben erneut | 1653.56 / 1811.66 | 1283.30 / 1294.70 | 370.26 ms | 22.4% |
| C Initiale Öffnung | 1346.02 / 1385.13 | 1346.77 / 1393.00 | -0.75 ms | -0.1% |
| C Personen | 407.17 / 726.78 | 15.99 / 43.03 | 391.18 ms | 96.1% |
| C Aufgaben | 422.27 / 820.28 | 19.09 / 35.79 | 403.18 ms | 95.5% |
| C Funktionen | 115.03 / 178.94 | 19.13 / 28.68 | 95.90 ms | 83.4% |
| C Lagebild zurück | 34.57 / 43.72 | 38.10 / 67.05 | -3.53 ms | -10.2% |
| C Aufgaben erneut | 413.67 / 559.05 | 19.65 / 34.47 | 394.02 ms | 95.2% |

Negative Differenz ist keine Verbesserung: Hub C und initiale Öffnung werden
nicht als optimiert behauptet. A bleibt DB-basiert und erreicht kein allgemeines
<150-ms-Ziel. C zeigt den isolierten UI-Anteil, **keine produktive Cachefreigabe**.

### Vollständige neue Größen-/Variantenreferenz

Jeweils Content-OnDraw Median / P95 in ms. B/C sind allein Testvarianten.
Historische Small-/Typical-Vorherwerte stehen in Abschnitt15; es wird dafür keine
zusätzliche frische Vorhermessung behauptet. Der direkte neue Vorher-/Nachher-
Nachweis oben gilt für den zuvor problematischen großen Lastfall.

| Größe/Variante | Initial öffnen | Personen | Aufgaben | Funktionen | Hub zurück | Aufgaben erneut |
|---|---:|---:|---:|---:|---:|---:|
| small A | 1206.54 / 1264.25 | 1202.41 / 1238.66 | 1205.55 / 1211.23 | 1204.24 / 1213.66 | 1206.59 / 1213.93 | 1204.57 / 1217.51 |
| small B | 1215.24 / 1258.96 | 24.09 / 47.27 | 21.24 / 27.56 | 23.59 / 26.30 | 16.40 / 34.51 | 21.25 / 27.97 |
| small C | 1214.79 / 1269.59 | 23.45 / 48.72 | 18.26 / 27.79 | 19.52 / 31.75 | 20.23 / 24.21 | 19.95 / 23.63 |
| typical A | 1220.42 / 1251.57 | 1211.22 / 1246.21 | 1211.60 / 1230.85 | 1204.33 / 1213.37 | 1214.07 / 1222.27 | 1212.76 / 1220.03 |
| typical B | 1219.90 / 1253.94 | 20.28 / 46.04 | 17.75 / 27.23 | 22.13 / 27.73 | 16.81 / 27.20 | 18.71 / 22.38 |
| typical C | 1230.11 / 1262.25 | 20.06 / 44.18 | 17.58 / 26.29 | 19.16 / 27.33 | 23.08 / 30.41 | 19.60 / 25.22 |
| large A | 1291.57 / 1347.39 | 1281.28 / 1304.30 | 1290.36 / 1302.41 | 1261.89 / 1276.10 | 1284.82 / 1295.33 | 1283.30 / 1294.70 |
| large B | 1290.08 / 1324.17 | 20.98 / 33.66 | 22.64 / 36.60 | 20.27 / 25.99 | 37.36 / 68.03 | 20.22 / 23.86 |
| large C | 1346.77 / 1393.00 | 15.99 / 43.03 | 19.09 / 35.79 | 19.13 / 28.68 | 38.10 / 67.05 | 19.65 / 34.47 |

Zusätzliche tatsächliche Interaktionsphasen des großen Produktpfads A:

| Ablauf | Tap-Rückkehr Median/P95 | Erster Draw Median/P95 | Content-OnDraw Median/P95 |
|---|---:|---:|---:|
| Personen | 3.62 / 8.63 | 13.96 / 21.38 | 1281.28 / 1304.30 |
| Aufgaben | 1.97 / 6.52 | 15.16 / 21.08 | 1290.36 / 1302.41 |
| Funktionen | 1.58 / 6.84 | 17.64 / 21.71 | 1261.89 / 1276.10 |
| Hub zurück | 1.21 / 1.55 | 33.29 / 60.96 | 1284.82 / 1295.33 |
| Schnelle Wechsel | 8.17 / 14.47 | 12.31 / 18.35 | 1287.44 / 1304.82 |
| Bestehende Aufgabe | 0.91 / 1.32 | 37.69 / 113.10 | 37.71 / 113.11 |
| Neue Aufgabe | 0.68 / 1.14 | 18.01 / 21.70 | 18.03 / 21.71 |
| Bestehende Person | 1.71 / 9.19 | 15.68 / 26.55 | 1262.53 / 1286.79 |

Die Personenmaske bleibt bewusst DB-basiert; die vorhandenen Taskformulare nutzen
den schon bestehenden Formularpfad. Kein zusätzliches Vorladen zur Maskenöffnung.
Tap-Rückkehr ist Callback/Main-Queue-Messung, kein Nachweis gefühlter Bedienung;
OnDraw ist der oben definierte UI-Proxy und kein physischer Display-Present.

### Frames und Scrollen

| Reihe | Frames | Frame-Median / P95 ms | Über gemeldeter Deadline |
|---|---:|---:|---:|
| Groß A vorher (Viewportfix) | 717 | 22.01 / 886.59 | 54.67% |
| Groß A nachher | 726 | 18.47 / 111.64 | 51.52% |
| Groß C vorher | 504 | 22.19 / 1072.65 | 54.17% |
| Groß C nachher | 511 | 19.89 / 222.25 | 52.25% |

A Frame-P95 886,59→111,64 ms (−87,4%); C 1072,65→222,25 ms (−79,3%).
Diese Fenster enthalten auch Material/Kosten/Editoren und Initialisierung, nicht
nur Personen/Tasks. Weiterhin hohe Deadlineüberschreitungsanteile sind ein offener
Befund und werden nicht als jankfrei ausgegeben. Nachher Layout-Measure-P95 großes
A0,387 / C0,346 ms; keine entsprechende alte Layout-Unterphase aufgezeichnet,
daher kein erfundener prozentualer Layoutgewinn. Keine verlorenen FrameMetrics-
Callbacks in diesen Reihen; das beweist keine null verlorenen Displayframes.

Separate gleiche Scrollgesten, zehn Wiederholungen C:
Typical Scroll-Frame Median/P95 6,98/11,34 ms; Large7,04/11,27 ms.
Gesamte Scrollfenster 5,06% / 4,87% über Deadline, null verlorene Messcallbacks.
Historisches Typical-Scrollen7,51/11,46 ms ist nur Referenz aus Abschnitt15;
keine frische große Vorher-Scrollreihe verfügbar. Daher kein belegter großer
Scroll-Prozentgewinn. Echte manuelle Flüssigkeit/TalkBack bleibt abnahmepflichtig.

### Heap, GC und FD-Grenzen der Messung

| Große Reihe | FD Start→Ende | Native Heap Start→Ende MiB | GC-Anzahl Δ / GC-Zeit Δ ms |
|---|---:|---:|---:|
| A vorher | 110→140 | 9.92→8.54 | 133 / 7162 |
| A nachher | 110→139 | 9.74→3.29 | 50 / 1603 |
| C vorher | 113→141 | 9.75→13.19 | 112 / 7171 |
| C nachher | 110→141 | 9.74→11.09 | 24 / 1036 |

Die Navigation enthält explizite GC-Anforderungen für Heapmessungen und komplette
Prozess-/Compose-/Framework-Warmups. Daher keine reine natürliche GC-Rate und kein
Leakbeweis aus Endpunkten. Java-Open-Delta großes C vorher3,15/3,38 MiB Median/P95,
nachher3,16/3,38 MiB: keine nachgewiesene Domain-RAM-Ersparnis. A-Nachher-Median
negativ wegen GC/Heaprauschen, **nicht** als Speichergewinn verwendbar. Exakte
Objektallokationszahlen wurden nicht profiliert; Slot-/Rowbedarf wird durch Lazy
begrenzt, aber keine exakte Allocation-Prozentzahl behauptet. FD-Wachstum in UI-
Prozessreihen bleibt Frameworkwarmup/weiter zu prüfen, kein vollständiger FD-
Leakfreiheitsnachweis. Getrennte warme Storage-FD-Regression unten.

Rohmessungen und APKs liegen ausschließlich lokal ignoriert unter
`.prototype-cache/p11ui-before`, `p11ui-viewport-only`, `p11ui-after`;
keine Logs, APKs, Credentials, Test-PDFs oder Produktbestände im Versionsumfang.

## 24. Regressionsstand und Sicherheitsabgrenzung dieser Runde

**Tatsächlich erneut ausgeführt:** vollständige Hostregression mit erzwungener
Ausführung und separatem Host-Init-Skript: **1266 Tests**, davon App612, Data650,
Security4, null Fehler/Failures/Skips. Gegenüber1239 sind27 fokussierte Tests für
Viewport3, Listenkonsistenz4, Mutationsprotokoll13 und Admission7 hinzugekommen.
Keine bestehende Prüfaussage entfernt oder abgeschwächt. Die zusätzlichen
Protokolltests liegen ausschließlich im test-only Assessment-Quellensatz.

**Tatsächlich erneut ausgeführt:** reale isolierte SQLCipher-/Room-
`P11WorkspaceStorageTest` **12/12**, 84,555 s. Warme20-Read-Resourceprobe
FD83→83; PSS64471→65275 KiB. Batchgleichheit, minimale angeforderte Daten,
Action-/Status-/Generationsbindung, Revoke vor/bei DB-Operation/Publication und
originale Integritäts-/Cleanupverträge bleiben Bestandteil dieser Suite.
Dies ist ein begrenzter synthetischer Test, keine beliebige Leak-/OOM-Garantie.

Die neun Navigationsreihen und zwei Scrollreihen sind vollständig bestanden.
Die abschließende große ListStability-Probe ist bestanden und prüft ACTIVE- und
PREPARATION-Listen bei Schrift1/2/3,5 einschließlich
letzter Zeile, mehrfacher Rückkehr, numerischer Scrollanker, Semantik, Lock während
Navigation, später DB-Rückkehr, neuer READY-Generation und ohne Autoopen/Formular-
Reanimation. Neue manuelle Geräteabnahme wird daraus nicht abgeleitet.
Zusätzliche ältere UI-Probes werden getrennt ausgewiesen, nicht stillschweigend
als durch diese Navigationsmessungen abgedeckt behauptet.

**BEOBACHTET:** Die ersten zusätzlichen Ux3c/Ux3d-Läufe scheiterten bei Schrift2
am Auffinden einer Übersichtszusammenfassung nach Create/Zurück (Task-create-return
bzw. neue Funktion). Die Liste kehrt nun absichtlich zum vorherigen Scrollanker
zurück; die gesuchte Zusammenfassung lag oberhalb dieser Position. Die Probes
suchen deshalb vor derselben Inhaltsassertion explizit rückwärts zur
Zusammenfassung. Ein einmaliges `top()` direkt im Clickcallback genügte bei
verzögertem Accessibilitybaum nicht. Der gesonderte Diagnoselauf bestätigt
vor der Textsuche zwei offene Tasks und geschlossene Detailauswahl im Modell;
der reine Textsuchlauf scheiterte mit null passenden sichtbaren Nodes.
Keine Status-/Write-/Count-/Text-/Lockassertion wurde entfernt. Ux3c beendet das
Top-Suchen außerdem erst dann ohne weitere Scrollaktion, wenn der reale
Accessibilityknoten keine Backward-Scrollaktion mehr anbietet. Ein früher erster
Ux3c-Aufruf endete mit Android-„Process crashed“ ohne verwertbaren Crashpuffer;
daraus wird keine weitere gesicherte native/Compose-Root-Cause erfunden.
Gedächtnisprotokollprobe P9 mit Schrift1+2 ist erneut vollständig bestanden
(Fragebogen/Abhängigkeiten/Entwürfe/Abschluss/Ergänzung/Statuswechsel/Lock).

Frische Android-Einzelprobes für JPEG/PNG, UTF8, PDF und Kamera/SAF wurden in
dieser UI-Runde nicht erneut ausgeführt; frühere P11-Nachweise bleiben historisch.
Die zugehörigen Hostregressionen sind Teil des erneut ausgeführten Bestands.

**Quellabgleich:** Keine Änderung an SessionCoordinator, DEVICE_CREDENTIAL,
Deadlines, StorageAccess, SessionDbKeyOwner, SQLCipher-/Room-/Key-Lifecycle,
P3b-Grants, Tink, Dokumentblobformat, PDF-Isolation, Namespace-Lane, Recovery,
Manifest-/Permission-/Backup-/Signaturvertrag. Schema11 unverändert. Einzige
Domainänderung ist semantisch äquivalente Taskzählung ohne unnötige Sortierung;
produktive UI-Daten bleiben durch Originalrepositories autorisiert. Nur numerische
Scrollmetadaten werden generations-/aktionslokal gehalten, keine neue Domain-
Retention oder produktive Mutation-/Stage2-Integration.

Keystorefingerprint vor/nach identisch; keine Produktinstallation und keine
Produktdaten/Credentials benutzt. Öffentliche P11-Quellen/Berichte frei von der
zuvor enthaltenen Serienkennung und persönlichen SDK-/Homepfaden; lokale ignorierte
Diagnosen bleiben lokal. Die Bereinigung entfernt alte öffentliche Gitinhalte
nicht. Kein Commit/Push/History-Rewrite in diesem Auftrag.

## 25. Vollständige geänderte Dateiliste dieser Runde

Relativ zu WIP `1025941def4c6788917fd607bf2c61d792b70515`: 26 Quellen, Tests,
Testkonfigurationen und Berichte. Abschnitt17 bleibt die historische WIP-Dateiliste.
Keine Buildartefakte/Keystores/Produktbestände/Logs werden hinzugefügt.

- `app/src/androidTest/kotlin/org/pag/prototype/Ux3cTasksProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/Ux3dFunctionsProbe.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActivePeople.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActiveTasks.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductEntityList.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScroll.kt`
- `app/src/main/kotlin/org/pag/prototype/product/TaskScreen.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ProductEntityListTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ProductViewportTest.kt`
- `docs/product-p11-active-workspace-assessment.md`
- `docs/product-p11-performance.md`
- `domain/src/main/kotlin/org/pag/domain/Tasks.kt`
- `tools/p11assessment/AndroidManifest.xml`
- `tools/p11assessment/README.md`
- `tools/p11assessment/android/org/pag/prototype/ActiveWorkspaceAssessmentProbe.kt`
- `tools/p11assessment/android/org/pag/prototype/ListStabilityProbe.kt`
- `tools/p11assessment/android/org/pag/prototype/ProjectionProfileProbe.kt`
- `tools/p11assessment/extras.py`
- `tools/p11assessment/run.py`
- `tools/p11assessment/shared/org/pag/assessment/BudgetContractProof.kt`
- `tools/p11assessment/shared/org/pag/assessment/MutationContractProof.kt`
- `tools/p11assessment/test/org/pag/assessment/BudgetContractTest.kt`
- `tools/p11assessment/test/org/pag/assessment/MutationContractTest.kt`

## 26. Abschluss: zusätzlicher UI-Stabilitätsblocker

**BLOCKER – keine Produkt-/Stufe2-Freigabe.** Der ursprüngliche große Constraints-
Absturz ist zahlenbasiert nachgewiesen und korrigiert (Abschnitt18). Im erweiterten
Aufgabenlauf tritt jedoch ein weiterer sporadischer echter Appabsturz auf:

`IllegalStateException → ChildSemanticsNode.applySemantics:92 →
TraversableNodeKt.traverseAncestors:380 →
LayoutNode.calculateSemanticsConfiguration/invalidateSemantics →
SnapshotStateObserver.notifyInvalidatedScopes`.

**GEMESSEN:** Android meldet APP CRASH(EXCEPTION) für das isolierte Paket;
Uncaught-Beobachtung gibt den Fehler an die originale Android-Behandlung weiter,
kein catch im Produkt. Offline tatsächlich aufgelöste Abhängigkeiten:
Material3-android1.4.0, Compose-UI1.12.1. Die vorhandenen AndroidX-Sources zeigen
für die Traversierung die Vorbedingung `node.isAttached`. Das ist ein anderer
Stack als die Constraints-Exception, kein nachgewiesener SQLCipher-/Sessionfehler.

**Untersucht und verworfen:** Breite Compose-Gruppen pro Destination bestanden
zunächst zwei vollständige Aufgabenläufe, Funktionen, große Listen und neun
Messreihen. Der nachfolgende vollständige P9-Fragebogenlauf scheiterte aber in
`all-empty-sections`. Die engere Trennung nur für Aufgabenliste/-detail/-formular
bestand P9 und einen Aufgabenlauf; nach einer weiteren Testscrollkorrektur trat
der echte obenstehende Semantikabsturz erneut auf. Diese Korrekturhypothese ist
somit nicht belastbar und wurde vollständig aus dem Produktcode entfernt, ebenso
die beiden ausschließlich dafür hinzugefügten Owner-Tests. Kein Zurücksetzen/
Überschreiben fremder P11-Arbeiten. Die Viewportkorrektur, Lazy-WIP-Optimierung,
Taskzählung, Datenschutzbereinigung und test-only Verträge bleiben reviewbar.

**Kontrollmessung:** Paketgeprüfte gesicherte Vorher-APK im isolierten Paket,
gleiche aktuelle Aufgabenprobe mit Schrift1+2: ein kompletter Lauf bestanden.
Ein erfolgreicher Kontrolllauf beweist bei einem sporadischen Fehler weder dessen
Abwesenheit im WIP noch, dass allein Lazy-Recycling ihn verursacht. Zusammenhang
mit Wiederverwendung/Disposition von Material3-Semantikknoten ist eine begründete
**HYPOTHESE**, keine vollständig bewiesene spezifische Row-/Frameworkursache.

**Testgrenzen:** Aufgabenprobe sucht nach Rückkehr gezielt rückwärts zur Summary,
prüft IME-Done auf Erfolg an einem frischen Editable-Node und nutzt echte
Accessibility-Scrollaktionen (Gestenfallback). Save-/Status-/Count-/Lockassertionen
bleiben erhalten; frühere falsche Suchrichtung und ein nicht erreichter Savebutton
werden nicht als bestandene Läufe umgedeutet. Kein Nachweis manueller Touch-/IME-
Bedienung daraus. Neue Crashbeobachtung protokolliert nur Klasse/Stackfundstellen,
keine Exceptioninhalte, Namen oder IDs.

**Verbindlicher Abschlussstand:** Abschnitt23 enthält die tatsächlich vollständig
bestandenen neun Navigations-/zwei Scrollreihen des beibehaltenen Viewport-/Lazy-
Stands ohne die verworfene Destinationsbindung. Sie sind Performancebelege, keine
vollständige funktionale GRÜN-Bewertung. Große ACTIVE-/PREPARATION-Listen,
Schrift1/2/3,5, Scrollanker, Lock/late Callback/kein Autoopen bestanden in den
separaten Probes; zusätzliche vollständige Aufgabenregression ist wegen des
sporadischen Absturzes **nicht bestanden**. Der P9-Lauf des ursprünglichen
beibehaltenen UI-Stands bestand; spätere Versuchsstände sind klar getrennt.

**Nächster notwendiger Schritt:** Minimale isolierte Material3-/Lazy-/Snapshot-
Reproduktion und belegte Lifecycle-/Upstreamkorrektur, danach sämtliche Task-/
Formular-/A11y-/Fragebogen- und Lastregressionen. Ein Bibliothekswechsel braucht
Abhängigkeits-/TM-F17-Prüfung; kein ungeprüftes Alpha-/Downgrade und kein Entfernen
von Semantik, catch-Fallback oder Frameworkpatch in diesem Auftrag. Bis zur
Klärung kein Produktupdate und keine produktive Erprobung von Stufe2.

Unabhängiger Review, belastbare Admission, komplette Write-/Recoveryintegration,
Personrename-Konfliktvertrag, hohe restliche Frame-/DB-Latenz und spätere manuelle
Produkt-/TalkBackabnahme bleiben offen. Schema11 und Sicherheitsverträge unverändert;
keine Produktdaten/Credentials/Installation verändert, kein Commit/Push/Merge/Release.

## 27. Unabhängige Semantikeingrenzung und gezielte Korrektur

Stand: 18.10.2026. Ausgangspunkt dieser Untersuchung ist WIP
`365341f942c78ec189a3990c160d222a9a18f59d`. Abschnitt26 bleibt als historischer
Fehler-/Versuchsbericht erhalten. Die beiden dort widerlegten Lifecycle-Ansätze
wurden nicht wieder eingeführt. Keine allgemeine Performanceoptimierung und keine
produktive Stufe2-Integration in dieser Runde.

### 27.1 Reproduktion und Methodik

**GEMESSEN:** Samsung SM-M526BR, Android13/API33. Nur isolierte Pakete
`org.pag.test.p11semantics` und `org.pag.perf.p11probe` mit ihren Testpaketen.
Keine Produktinstallation, Credentials oder Produktdatenbank. Die Minimal-App
nutzt `android.app.Application`, eine nicht exportierte synthetische Activity,
keine Produkt-MainActivity, keinen Renderer und keine Permissions. Buildinjektion
nur durch explizite Gradle-Init-Skripte; normale Produktkonfiguration unverändert.

Die A–F-Probe enthält 32 synthetische Zeilen mit eindeutigen stabilen Keys und
einem ContentType. Reihenfolge: Liste zeichnen, ans Ende scrollen, State ändern,
zurückscrollen, optional Liste → Detail → Formular → Detail → Liste. Frische
Activity pro Versuch; Schriftfaktor lokal über LocalDensity, keine globale
Geräteänderung. Kein setContent-Schriftwechsel innerhalb dieser ersten Matrix.

| Variante | Kontrollierter Unterschied | Accessibility-Automation |
|---|---|---|
| A | LazyColumn mit Text | Frische gleichzeitige Baumabfragen |
| B | Material3-OutlinedButtons | Wie A |
| C | B + bisheriges onClick-Label + Heading am Kindtext | Wie A |
| D | C + Detail-/Formularnavigation über Modellkommandos | Wie A |
| E | Gleiche Navigation wie D | Kein Zugriff auf uiAutomation |
| F | Gleiche Navigation, frische Accessibility-Clickaktionen | Gleichzeitige Baumabfragen |
| G | D + Snapshot-State direkt innerhalb stateDescription-Semantik | Kontrolliert an/aus |
| H | G, aber State in Composition gelesen, Beschreibung als Wert übergeben | Kontrolliert an/aus |

G/H unterscheiden allein die Stelle der Zustandsabfrage. Beide behalten
onClick-Label, Heading und stateDescription. Zusätzlicher G/H-Vergleich mit
vollständig komponierter scrollbarer Column statt LazyColumn, gleiches Fixture.
Keine Navigation-Root-Keys, entfernte Semantik, catch-Fallbacks oder Frameworkpatches.

**Harnesskontrolle:** Zustandsänderungen und Modellassertionen auf Main;
Composition-/Draw-Versionsfence und Idle-Prüfung vor dem nächsten Schritt.
Accessibility-Abfragen besitzen ihre jeweils frisch geholten Nodes und geben
sie im finally frei; keine Nodes über Navigation behalten. API33-recycle ist
bereits deprecated, wird zur expliziten Eigentümerschaft dennoch aufgerufen.
Gleichzeitige Abfragen sind gezielter Stress für asynchrone Semantikupdates.
Ohne Automation wird die uiAutomation-Verbindung überhaupt nicht angefordert.
Polling wird vor Activity-Ende gestoppt/joined; Activity-Destroy wird abgewartet.

Ein anfänglicher Pilot mit je zwei A–F-Versuchen scheiterte zwölfmal an einer
fehlerhaften **Test-Fence**: Der Versions-State wurde erst im SideEffect gelesen,
nicht während Composition. Kein Appcrash, keine gültige Produktstatistik. Nach
explizitem Composition-State-Read und Draw-Fence bestanden zwölf korrigierte
Piloten; nur anschließend gestartete vollständige Reihen zählen unten.
Synchrone Test-/Modellfehler werden getrennt als HARNESS_FAIL ausgegeben.
Asynchrone UI-Ausnahmen gehen nach anonymisiertem Klassen-/Stackbeleg an den
originalen Android-Uncaught-Handler; Appcrashes werden niemals verschluckt.
Ein Prozesscrash wird als fehlgeschlagener Versuch gezählt, danach startet der
Host den nächsten unabhängigen Versuch. Keine Wiederholung bis GRÜN statt Zählung.

### 27.2 Kontrollierte Häufigkeiten

**GEMESSEN:** A–F jeweils 50 vollständige Versuche bei Schrift1, 2 und3,5:
900/900 bestanden, 0 Appcrashes, 0 Harnessfehler. Das beweist nicht die
Fehlerfreiheit des vollständigen Produkt-/Aufgabenpfads.

| Minimalfall, Schrift2 | Versuche | Bestanden | Appcrash | Harnessfehler |
|---|---:|---:|---:|---:|
| G Lazy, direkte State-Abfrage, Automation an | 50 | 0 | 50 | 0 |
| H Lazy, in Composition berechneter Text, Automation an | 50 | 50 | 0 | 0 |
| G Column, direkte State-Abfrage, Automation an | 50 | 50 | 0 | 0 |
| H Column, in Composition berechneter Text, Automation an | 50 | 50 | 0 | 0 |
| G Lazy, direkte State-Abfrage, Automation aus | 50 | 0 | 50 | 0 |
| H Lazy, in Composition berechneter Text, Automation aus | 50 | 50 | 0 | 0 |

Alle G-Lazy-Crashes entstehen im Schritt `INVALIDATE` auf Thread `main`, nachdem
zuvor sichtbare Buttons aus dem Viewport gescrollt wurden. Übereinstimmender
bereinigter Stackkern:

```text
IllegalStateException (main)
TraversableNodeKt.traverseAncestors:380
ChildSemanticsNode.applySemantics:92
LayoutNode.calculateSemanticsConfiguration:482
LayoutNode.invalidateSemantics:442
OwnerSnapshotObserver.onCommitAffectingSemantics:47
SnapshotStateObserver.ObservedScopeMap.notifyInvalidatedScopes:1176
```

**BEOBACHTET / quellengestützt:** Ein direkter Snapshot-Read innerhalb der
Semantik wird durch den Semantik-Snapshotobserver beobachtet. Die anschließende
Zustandsänderung invalidiert auch den aus dem Lazy-Viewport entfernten
Material3-Semantikknoten. Die Traversierung setzt einen angehängten Modifiernode
voraus. Gleichbleibende Keys/ContentTypes, Standardbuttons allein und zusätzliche
statische onClick-/Heading-Semantik allein erzeugen den Fehler in dieser Matrix
nicht. Der genaue interne AndroidX-Lifecycle ist damit nicht pauschal für alle
Widgets/Versionen bewiesen; der anwendungsseitige Auslöser ist reproduziert.

### 27.3 Nachweis am tatsächlichen Produktpfad

`SemanticsProductRepro` verwendet echtes ProductScreen/TaskModel und die
synthetischen Ux3c-Ports, keine Datenbank. 67 ACTIVE-Aufgaben: drei bestehende
synthetische Aufgaben plus 64 synthetische offene Kopien mit eindeutigen IDs.
Über die bereits vorhandene nullable LocalProductListObservation wird nur
LazyListState beobachtet. Der Runner komponiert den geschlossenen Aufgabenfooter
am Listenende, scrollt ihn aus dem Viewport und ruft dann das echte
`TaskModel.toggleClosed()` auf. Keine Accessibility-Automation im maßgeblichen
Vorher-/Nachhervergleich.

**GEMESSEN, Schrift2, identische eingefrorene Test-APK:**

| Tatsächlicher Produkt-Footer | Versuche | Bestanden | Appcrash | Harnessfehler |
|---|---:|---:|---:|---:|
| WIP vor Korrektur | 50 | 0 | 50 | 0 |
| Eng begrenzte Korrektur | 50 | 50 | 0 | 0 |

Vorher jeweils `INVALIDATE_FOOTER`, `main`, derselbe Stackkern wie oben.
Nachher zusätzlich Lock-Cleanup-Assertion erfüllt. Der Fehler ist daher kein
rein unzulässiger Accessibility-Harnessablauf. Die Reproduktion ist eigenständig
vom früheren Constraints-Absturz und benötigt keine großen alten Mindesthöhen.

**Korrektur:** Ausschließlich im `closed-toggle`-Lazy-Item von ProductEntityList
wird `model.tasks.closedExpanded` während Composition in den bisherigen deutschen
Semantiktext umgesetzt. Das semantics-Lambda erhält diesen unveränderlichen
Wert. Dadurch abonniert es nicht mehr selbst diesen Snapshot-State. Beschriftung,
onClick, Heading, Buttonrolle und Aus-/Eingeklappt-Information bleiben erhalten.
Keine zusätzlichen Domainprojektionen oder längere Payload-Lebensdauer.
Die alte Column-Darstellung wird nicht pauschal umgebaut. Viewport-, Lazy-,
Scrollanker-, Formular- und Sessionverträge bleiben bestehen.

Zur Identifikation eingefrorener lokaler Artefakte (APKs selbst nicht im Repo):

| Artefakt | SHA256 |
|---|---|
| Gesicherte alte isolierte Vorher-APK | `9c79a95181fb4d81d3142398dbd190a385def4cd917013d8d489338c572d96bd` |
| WIP-Ziel vor diesem Fix | `9cdecf178ef3a1a6862b4068e8c1793057df734761329fcabd32bada6783c81e` |
| Ziel mit minimalem Fix | `e3160b230ab0445fbccd17504e7d6be981bbbe18e50a1e689b874200a91318a2` |
| Identische Test-APK des maßgeblichen Vorher/Nachher-Repros | `5dba97283b0920ab8fab106932f7947f850227d2e76851582c2514f039cfa240` |

Die alte gesicherte isolierte APK ist ein binärer Assessment-Vergleich, kein
nachträglich attestierter exakter Quellcommit. Ihr früheres isoliertes
Diagnoseinstrument ist im normalen Testpfad inaktiv. Der Footer-Repro benötigt
die neue Lazy-Beobachtung und wird nicht gegen die alte Column-APK ausgeführt.
Der separate SemanticsActualProbe nutzt dieselbe Test-APK für beide alten/neuen
Ziele und nur deren gemeinsame öffentliche Modell-/ProductScreen-Schnittstelle.

### 27.4 AndroidX-/Dependencybefund

**GEMESSEN:** Offline aufgelöster debugRuntimeClasspath: Compose-BOM2026.09.00,
Material3-android1.4.0, UI/Runtime/Foundation-android jeweils1.12.1. Dieselben vier
Version-Marker sind direkt aus alter und aktueller isolierter APK geprüft.
Material3 verlangt im transitiven Graphen teilweise Foundation1.8.1; die
aufgelöste BOM-Version ist1.12.1. Kein zweites gleichzeitig ausgewähltes UI-
Versionsset, aber daraus folgt keine pauschale Kompatibilitäts-/Fehlerfreiheitsgarantie.

Die vorhandenen offiziellen Sources-JARs zeigen:

- Material3 ChildParentSemantics.kt:91–98 traversiert in applySemantics Vorfahren.
- UI DelegatableNode.kt:101 prüft node.isAttached vor dieser Traversierung.
- LayoutNode.kt:430–447 invalidiert Semantik, außerhalb Modifierupdates sofort
  über calculateSemanticsConfiguration; der Getter der Semantik hat bereits
  einen Attached-/Deactivated-Guard. Der unmittelbare Invalidierungspfad und der
  Getter sind unterschiedliche Pfade.
- LayoutNode.kt:473–482 beobachtet Semantik-State-Reads und wendet Modifiersemantik an.
- LayoutNode.kt:1487–1496 setzt bei onDeactivate isDeactivated und ruft
  resetModifierState auf; NodeChain.kt:242–245 führt dabei den Detach-Lifecycle
  aus und markiert die Modifiernodes als detached. Der LayoutNode kann weiterhin
  attached sein. LayoutNode.isValidOwnerScope prüft an633–634 nur dieses
  LayoutNode-isAttached. OwnerSnapshotObserver.kt:45–47 lässt damit die
  Semantikinvalidierung passieren, obwohl die Modifierkette deaktiviert ist.
  Das erklärt den reproduzierten Unterschied zwischen Lazy und Column und die
  fehlgeschlagene Modifier-isAttached-Vorbedingung. Keine Reflection oder privaten
  Runtimezugriffe für diese Analyse; ausschließlich die gelieferten Sources.

**Offiziell belegte historische verwandte Fehler:** AndroidX-Commit
[7e18d15](https://github.com/androidx/androidx/commit/7e18d15aa986445a345990c9837368e48468f731)
führte 2024 vorübergehend einen catch um diese Traversierung ein.
[ddc49c9](https://github.com/androidx/androidx/commit/ddc49c903f3d3e853ff6440720699de8f17ee1c9)
ergänzte den Attached-/Deactivated-Guard im damaligen collapsedSemantics-Getter
(Bugs353595612/353538515).
[f12a2f9](https://github.com/androidx/androidx/commit/f12a2f984b82171e3624efa69908d2c8117f992c)
entfernte den catch anschließend wieder zugunsten der UI-seitigen Prüfung.
Der entsprechende Getter-Guard existiert in unseren1.12.1-Quellen bereits.
Diese historischen Commits sind daher **kein Nachweis einer noch zu installierenden
Korrektur** für den aktuellen Invalidierungsstack. Der ebenfalls gefundene
[725b701](https://github.com/androidx/androidx/commit/725b7018e95f5108ed022670789f2050ab9e58d6)
betrifft InspectorInfo statt Laufzeitsemantik und ist als Lösung verworfen.
Keine geprüfte aktuelle Upstreamkorrektur für genau unseren Versions-/Reprofall
nachgewiesen. Kein Upgrade, Downgrade, Alpha, Fork oder Patch. Ein solcher Wechsel
bleibt eine eigene TM-F17-Entscheidung.

### 27.5 Weitere Nachtests und Grenzen

**GEMESSEN:** Identische SemanticsActualProbe-Test-APK gegen alte isolierte
Column-APK und WIP vor dem Fix: jeweils50/50 bestanden, 0 Crashes/Harnessfehler.
Jeweils dieselbe Activity, setContent mit alternierender Schrift1/2, gleichzeitige
frische Accessibility-Baumabfragen. Reale ProductScreen-/Modellpfade für Status,
Bearbeiten, Speichern, Erstellen, Filter und Lock; die Portdaten sind synthetisch.
**Grenze:** Modellkommandos ersetzen nicht sämtliche Accessibility-/IME-Aktionen
von Ux3c und erzwingen keine Footer-Deaktivierung. Die Kontrollreihe widerlegt
keine seltenen Vollpfadfehler. Die bloße Activity-/setContent-Wiederverwendung
reproduziert den Crash in dieser kontrollierten Reihe nicht.

**GEMESSEN:** Zusätzliche Produkt-Footer-Reihen mit Fix, jeweils50/50 bestanden
bei Schrift1 und3,5. Hier wird mit frischen eigenen Accessibility-Nodes vor/nach
dem Aufklappen ausdrücklich stateDescription=Eingeklappt/Ausgeklappt geprüft.
0 Crashes, 0 Harnessfehler; Lock-Cleanup ebenfalls geprüft.

**Tatsächlich erneut ausgeführte Hostregression:** app612 + data650 + security4
=1266 Tests, 0 Failures/Errors/Skipped, Offline-Build erfolgreich. App neu
ausgeführt; zunächst gecachte Data-/Security-Ergebnisse anschließend ausdrücklich
mit --rerun neu ausgeführt. Keine Tests
entfernt oder abgeschwächt. Die vorhandene Ux3c-Probe akzeptiert zusätzlich den
expliziten Schriftfaktor3,5; Standard1+2 und alle bestehenden Assertions bleiben.
Keine neuen kryptografischen/DB-Verträge.

**Vollständige Ux3c-Geräteläufe:** Schrift1 und2 jeweils drei vollständige
Accessibility-/IME-getriebene Läufe bestanden. Schrift3,5: drei von drei Läufen
scheitern in phase=hub an einer Erreichbarkeitsassertion (visible/click,
Probezeilen127/117/195), **ohne Appcrash**. Diese neu zusätzlich versuchte
Großschrift-Vollregression ist nicht bestanden; die erfolgreichen Footer- und
Lastprobes ersetzen sie nicht. Die identische neue Test-APK scheitert auch in einem Kontrolllauf gegen den
unveränderten WIP vor dem Fix exakt an phase=hub/Zeilen127/117/195. Damit ist
diese Abweichung nicht neu durch den Footer-Fix eingeführt. Die genaue Produkt-/
Harnessursache bleibt offen; ein Vorherlauf ist keine Häufigkeitsstudie.
Keine weitere spekulative Produktkorrektur in diesem Auftrag.


**Weitere vollständig ausgeführte Gerätereihen:** Ux3d mit Schrift1+2 bestanden.
ListStabilityProbe mit200 Personen/1000 Aufgaben/200 Rollen je synthetischer Aktion
bestand bei Schrift1/2/3,5: wiederholter Tasks-/Hub-Einstieg, gleiche numerische
Scrollanker, echte Scroll-/Labelsemantik, bestehendes Formular, ACTIVE- und
PREPARATION-Personen-/Aufgabenlisten, schnelle Navigation, Lock während laufendem
Read, späte Callback-Verwerfung, READY-Wechsel ohne Action-/Formular-/P3b-Autoopen.
Keine neue manuelle TalkBack-/Produktabnahme daraus abgeleitet.

**P9-Vollregression nicht bestanden:** Ein kompletter Lauf Schrift1+2 erreicht
bei Schrift2 die Phase addendum und scheitert an der Erreichbarkeit eines Controls
(Probezeilen130/117/176). Kein Appcrash; die vollständige Reihe ist dennoch fehlgeschlagen.
Kein Ersatz durch erfolgreiche Minimal-/Modell-/Listenprobes. **Kontrolllauf mit identischer Test-APK gegen unveränderten WIP vor dem Fix:
Schrift1+2 vollständig bestanden (ein Lauf).** Vorher1/1 bestanden gegenüber
Nachher0/1 bestanden ist ausdrücklich kein stabiler Kausalitätsnachweis bei
asynchroner UI-/Scrollinstrumentierung. Es erlaubt weder die Behauptung einer
nachgewiesenen neuen P9-Produktursache noch das Wegerklären des fehlgeschlagenen
Nachtests als reines Harnessproblem. Der neue P9-Befund betrifft addendum und
ist getrennt von der früheren all-empty-sections-Regression der verworfenen
Root-Key-Variante. **P9 bleibt ein offener Regressionsblocker.** Keine Wiederholung
bis GRÜN und kein spekulativer zusätzlicher Produktfix.

| Vollpfadkontrolle, identische Instrumentierung | Vorher-WIP | Minimalfix |
|---|---|---|
| Ux3c explizit Schrift3,5 | 0/1 bestanden, phase=hub | 0/3 bestanden, phase=hub |
| P9 Standard Schrift1+2 | 1/1 bestanden | 0/1 bestanden, Schrift2/addendum |

Diese kleinen Vollpfad-Kontrollzahlen werden nicht mit den50er-Reproreihen
vermengt. Die alten isolierten Column-/WIP-Modellkontrollen mit je50 Läufen sind
keine vollständigen P9-/Ux3c-Accessibility-/IME-Vollpfadkontrollen.

### 27.6 Sicherheitsgrenzen und offene Freigabe

Nur der Leseort eines UI-Statuswerts im Lazy-Footer ist produktiv geändert.
Keine Veränderung an SessionCoordinator/180s/30s, Generation-/Storagegates,
SessionDbKeyOwner, SQLCipher-Preflight/Room-/Key-Lease, Tink, Leadership/P3b,
Dokumentisolation/NamespaceLane, Recovery, Schema11, Permissions, App-ID oder
Signatur. Bestehender Keystore unverändert. Keine neuen Produktdependencies,
RAM-Caches, Klartextdateien, Produktinstallation oder Produktdatenzugriffe.
Keine volle physische RAM-Löschung oder umfassende Sicherheitsprüfung behauptet.
Die Tests sind synthetisch; aktuelle neue Quellen/Belege enthalten keine
Geräteseriennummern, Home-/SDK-Pfade, Credentials oder Rohlogs.

**Empfehlung:** Den nachgewiesenen eng begrenzten Semantikfix unabhängig reviewen;
keine Rückkehr zu den beiden widerlegten Root-Key-Versuchen. Der gelieferte
Frameworkpfad bleibt außerhalb dieser Anwendungskorrektur ein Upstream-Thema.
Die Quell-/Commitsuche ist keine vollständige Prüfung aller AndroidX-Bugtracker.
Ein Dependencywechsel bleibt gesondert freigabepflichtig(TM-F17).

**BLOCKER/offen:** Fehlgeschlagene Großschrift-Aufgaben- und P9-Vollregression,
Abgrenzung ihrer genauen UI-/Harnessursache und unabhängiger Review. Kein pauschales
GRÜN, keine Produkt-/Stufe2-Erprobung. Weitere allgemeine Performance-, Admission-,
Write-/Recovery- und Securityentscheidungen bleiben gemäß älteren Abschnitten offen.
Issue#26 bleibt Release-Gate vor1.0. Kein Merge/Release/Produktupdate.

**Nächster kleinster Diagnoseschritt:** Die zwei kombinierten Erreichbarkeitsphasen
in test-only feste Unterphasen zerlegen (Aufgaben-Summary gegenüber Titel;
P9 Ergänzung öffnen/speichern/Bestätigung). Nach jedem Schritt Modellroute,
Zeichenfence und ausschließlich eigene Node-/Scroll-Geometrie getrennt prüfen.
Keine Texte/Produktdaten loggen, keine pauschalen Delays, keine Assertions entfernen
und keinen weiteren Produktfix ohne kontrollierten Nachweis erzwingen.

### 27.7 Vollständige Dateiliste dieser Untersuchungsrunde

- `app/src/main/kotlin/org/pag/prototype/product/ProductEntityList.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/Ux3cTasksProbe.kt`
- `docs/product-p11-active-workspace-assessment.md`
- `docs/product-p11-performance.md`
- `tools/p11semantics/README.md`
- `tools/p11semantics/probe.init.gradle`
- `tools/p11semantics/TargetManifest.xml`
- `tools/p11semantics/TestManifest.xml`
- `tools/p11semantics/target/org/pag/semantics/SemanticsProbeActivity.kt`
- `tools/p11semantics/android/org/pag/semantics/SemanticsProbe.kt`
- `tools/p11semantics/run.py`
- `tools/p11semantics/actual.init.gradle`
- `tools/p11semantics/ActualTestManifest.xml`
- `tools/p11semantics/actual/org/pag/prototype/SemanticsActualProbe.kt`
- `tools/p11semantics/actual/org/pag/prototype/SemanticsProductRepro.kt`
- `tools/p11semantics/actual.py`
- `tools/p11semantics/regressions.py`

17 Textdateien. APKs, eingefrorene Testartefakte, Rohlogs, lokale Runner und
maschinelle Messzusammenfassungen verbleiben ausschließlich im ignorierten
lokalen Diagnosebereich; keine Veröffentlichung dieser Dateien. Anonymisierte
Häufigkeiten/Stackfundstellen stehen oben. Keine Git-History-Bereinigung.

## 28. Nachprüfung der offenen UI-Erreichbarkeitsregressionen

Stand: 18.10.2026, Untersuchungsbasis WIP
`b89b7671a9d57805478c131df94c828da732297a`. Ausschließlich Teststeuerung,
Diagnostik und Bericht. Der Produktcode einschließlich des belegten
Lazy-Footer-Semantikfixes bleibt unverändert. Kein Stufe2-Store, keine allgemeine
Performanceoptimierung und keine neue Sicherheits-/Storagearchitektur.
Abschnitt27 und seine fehlgeschlagenen Versuche bleiben historisch erhalten.

### 28.1 Versuchsaufbau und Grenzen

Nur Samsung SM-M526BR, Android13/API33 und isoliertes Paket
`org.pag.perf.p11probe` mit zugehörigem Testpaket. Keine Produktinstallation,
Credentials, echten Dokumente oder Produktdatenbank. APK-Paketnamen werden vor
jeder Installation aus dem binären Manifest geprüft; ausschließlich Updates der
isolierten Pakete, kein uninstall/pm clear. Vorhandener Keystore unverändert.

Die zwei eingefrorenen Ziel-APKs sind dieselben wie in27.3: WIP unmittelbar vor
Semantikfix (SHA256 `9cdecf178ef3a1a6862b4068e8c1793057df734761329fcabd32bada6783c81e`)
und mit Fix (`e3160b230ab0445fbccd17504e7d6be981bbbe18e50a1e689b874200a91318a2`).
Ein Vergleichsarm verwendet jeweils dieselbe neue Test-APK gegen beide Ziele.
Produkt-APKs werden während einer Vergleichsreihe nicht neu gebaut. Die Fake-
Repositories/Fixtures, ursprünglichen fachlichen Erwartungen und Texte bleiben
identisch. Zusätzliche Assertions prüfen Modellzustand und genau eine Speicherung.

Neue test-only `UiReachabilityTrace` protokolliert ausschließlich feste
Phasennamen, Zustandsenums/Booleans, Zähler und Geometrie. Keine Node-Texte,
Dokumentinhalte, IDs, Gerätekennungen oder lokalen Pfade. `LocalProductListObservation`
beobachtet die bereits vorhandene LazyListState-Schnittstelle; sie führt keine
Navigation aus und lädt keine Daten. Sichtbare `task-summary`-Messdaten sind ein
Beleg für die zugehörige Item-Komposition/Messung, kein Beleg für die vollständige
Composition aller offscreen Items. `rootComposition` zählt nur den vorhandenen
Root-Hook, nicht jede verschachtelte Recomposition. Echte Draws werden am
ViewTreeObserver beobachtet. Die Accessibility-Scrollereignisbeobachtung lieferte
hier keine verwertbaren ScrollY-Werte (`none`); daraus wird kein Scrolloffset
abgeleitet. Lazy-Indizes/-Offsets und Node-Bounds werden separat ausgewertet.

**Diagnosegrenzen:** Der erste Pilot zählte Wrapper-Composition/Draw und erfasste
damit nicht alle verschachtelten Updates. Diese Zähler sind kein gültiger
Frame-/Recomposition-Nachweis. Danach wurden vorhandener Root-Hook und tatsächliche
View-Draws benutzt. Instrumentierung/Logging kann das Timing verändern. Darum
werden Pilot-, Diagnose- und Validierungsreihen nicht zu einer Erfolgsquote
zusammengelegt. Rohlogs, Screens-/APK-Artefakte und lokale Runner bleiben ignoriert.
Keine Latenz-/Performanceverbesserung aus diesen Funktionstests behauptet.

### 28.2 Aufgaben Schrift3,5: Unterphasen und offener Status

**GEMESSEN:** Die identische eingefrorene Diagnose-Test-APK läuft fünf vollständige
Versuche je Zielstand. Beide Ziele: **0/5 vollständige Tests bestanden, 5/5
UNREACHABLE in `start`, keine Appcrashes**. Unter der ausführlichen Diagnostik
passieren alle Versuche die früher kombinierte Hub-Phase; sie scheitern nach der
Statusänderung an der folgenden Label-Erreichbarkeit. Das ist kein bestandener
Volltest und keine Widerlegung der älteren Huberreichbarkeitsfehler.

| Unterphase | Nachweis / Ergebnis der Diagnosereihe |
|---|---|
| A Lagebild | Erwartete Überschrift erreichbar |
| B Aufgaben-Tap | Ursprüngliche Accessibility-Clickaktion ausgeführt |
| C ViewModel | ActiveArea.TASKS |
| D Bestand | Workspace.tasks und TaskModel.board vorhanden |
| E Zähler | 1 OPEN, 1 IN_PROGRESS; spätere Statusänderung ergibt korrekt 0/2 |
| F Komposition/Messung | Lazy-`task-summary` erscheint in visibleItemsInfo, z.B. Offset118/Größe1194px |
| G Accessibility | Erwarteter Summarytext wird schließlich als exakter Node-Text gefunden |
| H Normale Scrollaktionen | Summary wird ohne programmatisches scrollToItem erreicht |
| I Erste Aufgabe | Erwarteter Titel im Baum sichtbar |
| J Bedienung | Echte Clickaktion öffnet erwartete synthetische Aufgabe; Detailzuordnung stimmt |

**BEOBACHTET:** In einem feingranularen Ablauf ist die Summary bereits gemessen,
während die Baumabfrage noch keinen Treffer enthält. Eine spätere Abfrage liefert
das Label, während der Lazy-Viewport bereits weitergezogen ist. Die ursprüngliche
Suche scrollt nach wenigen festen Polls immer weiter in dieselbe Richtung und
nimmt damit eine sofort aktuelle Accessibilityprojektion an. Der Modell-/Domain-
zustand ist dabei korrekt. Nach Klick auf Statusaktionen kann das erwartete
Statuslabel bei Schrift3,5 oberhalb des aktuellen Viewports liegen; weiter nach
unten gerichtete Suche findet es nicht. Das spricht für timing-/positionsabhängige
Teststeuerung, beweist aber noch keine vollständige eindeutige Root Cause sämtlicher
Großschrift-Erreichbarkeitsfehler.

**HYPOTHESE/offen:** Ursachenanteile von verzögerter Accessibilityprojektion,
Scroll-/Fokusposition und Suchrichtung. Kein Nachweis einer falschen Actionbindung,
fehlender Domain-Summary oder einer neuen unbeschränkten Viewport-/Constraints-
Allokation. Keine pauschale Erklärung als Produkt-/AndroidX-Bug.

**Geprüfte, nicht übernommene Testkorrekturen:**

| Versuch | Ergebnis eines vollständigen Fix-Ziel-Piloten | Einordnung |
|---|---|---|
| Frische Nodes, Accessibility-Idle, begrenzte Suche in beide Richtungen | Fehler `edit`, Eingabefeld nicht sofort sichtbar | Keine vollständige Korrektur |
| Zusätzlich frische Button-Vorfahren und Scrollsuche nach Editable | Fehler `J_FIRST_TASK_OPEN`; neuer Scrollcontainer noch nicht zuverlässig vorhanden | Kein Vollerfolg |
| Zusätzlich begrenzte Choreographer-Frame-Fence; fehlender Container nicht sofort als Rand | Fehler `edit`, Rücknavigation nach Speichern | Kein Vollerfolg |
| Zusätzliche rohe/normalisierte Klicklabel-Diagnose | Wieder Fehler `edit`, Rücknavigation | Keine belegte reine Whitespaceursache |

Alle diese Piloten ohne Appcrash. Die ursprünglichen fachlichen Assertions wurden
nicht entfernt. Keine Sleeps/Timeoutverlängerungen oder Produktlayoutänderungen
als Lösung übernommen. Die erfolglosen Navigationsvarianten wurden nach der
Auswertung aus dem finalen Testcode entfernt; ihre Ergebnisse bleiben hier
sichtbar. Final bleibt die ursprüngliche Aufgabensteuerung mit klar getrennten
Diagnosephasen und zusätzlichen Modellprüfungen. **Aufgaben3,5 weiterhin BLOCKER.**

**Engster nächster Schritt:** Den nachgewiesen offenen Rücknavigationspunkt nach
Speichern isolieren. Vor/nach jeder normalen Scrollaktion die frisch gelesene
Geometrie und einen festen Labelsatz für Header/Rückknopf/Chronik erfassen,
zusammen mit Taskselected/Form- und Fokuszustand. So lässt sich tatsächliches
Überspringen von ausstehender Semantikpublication unterscheiden. Keine privaten
APIs, kein programmatisches Erzwingen einer Produktnavigation und kein weiterer
Produktfix ohne Vorher-/Nachhernachweis.

### 28.3 P9-Ergänzung: konkreter veralteter Vorfahr

**GEMESSEN:** Die neue Unterphasendiagnostik trennt:

| Phase | Erhaltener/zusätzlicher Nachweis |
|---|---|
| A abgeschlossen | Modellheader und synthetischer Repositoryheader COMPLETED |
| B zulässig/sichtbar | DETAIL-Modus, nicht busy/loading; Ergänzungsbutton erreichbar |
| C öffnen | Genau eine akzeptierte Accessibility-Clickaktion |
| D Modus | ADDENDUM tatsächlich aktiviert |
| E Feld | Erwartetes Eingabefeld erreichbar |
| F Eingabe | Modellwert entspricht dem unveränderten synthetischen Testtext |
| G speichern | addendumValid; erwarteter Speicherbutton erreichbar/aktiv |
| H Bestätigung | Genau ein zusätzlicher Repositorywrite; DETAIL und nicht busy |
| I Inhalt | Genau eine Ergänzung mit richtigem Inhalt im Port und Modell-Detail |
| J Anzeige | Ursprüngliche Erwartung „Ergänzung1“ im UI erreichbar |

**Beleg der tatsächlichen G-Fehlstelle:** Der zuvor aktualisierte Label-Knoten ist
vorhanden, der über `getParent()` geholte Button-Vorfahr trägt jedoch veraltete
Informationen. Unveränderter gültiger Modellzustand (ADDENDUM, busy=false,
loading=false, completed=true), vor Refresh `clickable=true, enabled=false,
Höhe147px`; nach erfolgreichem öffentlichen `refresh()` desselben Vorfahren
`enabled=true`, zunächst sichtbare Höhe104px, später147px. Der alte Test verwirft
den vermeintlich deaktivierten Button und scrollt weiter. Es wurde bis dahin
keine Ergänzung gespeichert. Das ist kein nachgewiesener Daten-/Commitfehler.
Die historische ungetrennte Addendum-Phase lässt sich nicht rückwirkend eindeutig
einem A–J-Schritt zuordnen; der hier getrennt reproduzierte G-Auslöser ist
konkret belegt. Die alte Schleife aktualisiert die beim Baumdurchlauf geholten Nodes bereits,
aber `getParent()` kann erneut eine gecachte andere NodeInfo-Instanz liefern.

**Minimaler Testfix:** Nur die während der Vorfahrensuche neu geholten
AccessibilityNodeInfo-Instanzen vor Auswertung von isClickable/isEnabled/Bounds
aktualisieren; fehlgeschlagenen Refresh als obsoleten Node verwerfen. Dasselbe
bei Editable-Vorfahren. Suchrichtung, ursprüngliche Versuchsgrenzen, sichtbare
Mindesthöhe48dp und Originalassertionen bleiben erhalten. Kein neuer Produkt-
Scroll-/Fokusmechanismus, kein pauschaler Delay, kein Keyboard-/Formularbypass.
Die finale G-Aktion hat außerdem eine explizite Einmalzustellungsassertion:
auch eine abgelehnte/unklare performAction-Zustellung wird nicht automatisch als
Schreibaktion wiederholt. Keine Wiederholung einer akzeptierten Speicheraktion; zusätzlicher Writezähler
muss exakt um eins steigen. `navigation=legacy` bleibt explizite Testkontrolle;
normaler P9-Probelauf aktualisiert Vorfahren.

Öffentliche API-Grundlage: [AccessibilityNodeInfo.refresh](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#refresh())
aktualisiert eine Instanz und meldet einen nicht mehr vorhandenen View als
ungültig. Verwandte experimentelle Event-Idle-/Show-on-screen-Ansätze wurden
gegen die minimale Vorfahrenkorrektur abgegrenzt; keine Produktverwendung.

**Nachweisgrenze:** Reports ist der unveränderte synthetische In-memory-
Repositoryport der bestehenden P9-Probe. „Persistierter Inhalt“ hier bedeutet
bestätigter Portbestand plus erneutes Modell-Detail, keinen neu gemessenen echten
SQLCipher-Addendum-Diskwrite. SQLCipher-/Sessiontests werden separat aufgeführt.

### 28.4 Kontrollierte Reihen, verworfene Geometriehypothese

**GEMESSEN, P9 Schrift2, vollständige Originalabläufe:** Derselbe eingefrorene
Test-APK-Stand mit ausschließlich aktivierter Vorfahrenaktualisierung wird gegen
beide eingefrorenen Ziel-APKs ausgeführt. Kein Abbruch nach erstem Erfolg,
keine automatische Wiederholung fehlgeschlagener Fälle.

| Zielstand | Vollständige Läufe | PASS | Appcrash | UNREACHABLE | Assertion | Timeout | Harnessfehler |
|---|---:|---:|---:|---:|---:|---:|---:|
| Vor Semantikfix | 10 | 10 | 0 | 0 | 0 | 0 | 0 |
| Mit Semantikfix | 10 | 10 | 0 | 0 | 0 | 0 | 0 |

Test-APK SHA256:
`0e8b93bfbdb02dd7de80d09abd8a2d62b24b22e1cfdb446617f0dab186880a45`.
Die spätere finale Probe ergänzt lediglich die konservative Einmalzustellung der
Speicheraktion; ihr kompletter Schrift1→2-Lauf wird separat validiert. Keine
Vermischung dieser finalen Regression mit der eingefrorenen10er-Vergleichsreihe.
Zehn Läufe je Stand sind ein begrenzter funktionaler Regressionsnachweis,
keine Garantie gegen jede seltene UI-/OEM-Variante.

**Frühere neue Kontrollen bleiben sichtbar:** Diagnose-APK
`ec2ee0c49b3588cb08156482df4e64e720248bfff53cda3723540c1e53b78d66`
mit ursprünglicher P9-Steuerung: Vorher2/2 bestanden; Fix-Ziel1/2 bestanden,
ein UNREACHABLE in der späteren Phase active-completed-draft. Die Addendum-
Unterphasen waren dort passiert. Zusätzlich erster diagnostischer Schrift2-Pilot
bestanden; nicht zur10er-Validierung addiert. Ein experimenteller Idle-/Suchlauf
OHNE aktualisierte Vorfahren scheitert in G_SAVE_ADDENDUM; der erste reine
Vorfahren-Refresh-Pilot besteht vollständig und liefert den obenstehenden
before/after-enabled-Beleg. Auch diese Einzelversuche werden nicht zu einer
Statistik mit veränderter Methodik zusammengefasst.

**GEMESSEN, zusätzliche isolierte Scrollmessung:** Der AndroidX1.12.1-Quellpfad
berechnet die Accessibility-Scrollstrecke aus GetScrollViewportLength oder einem
Koordinatenfallback. Daraus entstand die **HYPOTHESE**, Column.verticalScroll könne
statt Viewporthöhe die ganze Inhaltshöhe verwenden und mittlere Controls überspringen.
Eine separate Probe mit derselben Padding/verticalScroll/heightIn-Reihenfolge,
40 synthetischen Buttons, eigenem öffentlich beobachtetem ScrollState, frischem
State je Versuch und normalen ACTION_SCROLL_FORWARD-Aktionen prüft dies.
Kein ProductViewModel, keine Repository-/Credentialzugriffe und kein Produktpatch.

| Schrift | Explizites Viewportmetadatum | Wiederholungen | tatsächlicher Viewport | Delta pro Scrollaktion | maxScroll |
|---|---|---:|---:|---:|---:|
| 1 | aus / an | jeweils5 | 1533px | 1533px | 5145px |
| 2 | aus / an | jeweils5 | 1533px | 1533px | 5705px |
| 3,5 | aus / an | jeweils5 | 1533px | 1533px | 16505px |

Alle30 Messversuche abgeschlossen, keine Appcrashes. Die Boxhöhe1659px enthält
zusätzlich126px Padding und wird ausdrücklich nicht als Scrollviewport ausgegeben.
Die generelle statische Whole-content-Scrollhypothese ist damit für diese Probe
verworfen. **Kein Produkt-Viewportmetadatum hinzugefügt.** Das ist keine vollständige
Aussage über dynamische Banner-/IME-/Fokusänderungen im kompletten Aufgabenpfad.
Der Aufgaben-Blocker bleibt offen. Test-APK dieser Messung:
`4bbd8345604676560ef548e08389d9eb32df5d1fe2836743cc56d053de24b9bd`.

### 28.5 Abschlussregressionen

**Tatsächlich erneut ausgeführte Hosttests:** app612 + data650 + security4 =1266,
alle ausdrücklich mit --rerun, 0 Failures/Errors/Skipped. Darin ProductViewportTest:
der exakte alte269909px/954px-Zahlenfall wirft erwartungsgemäß, begrenzter realer
Viewport ergibt gültige Constraints, Infinity/kleiner Viewport erfindet keine
riesige Mindesthöhe. Keine Tests gelöscht oder abgeschwächt.

**GEMESSEN, finale Regressions-Test-APK**
`f73111d0e76c02243bde269e3cb7674c61ea473ca1ddf6ab294832873b4ffa3b`:

| Prüfung | Ergebnis |
|---|---|
| Vollständige Aufgabenprobe Schrift1 | 1/1 bestanden |
| Vollständige Aufgabenprobe Schrift2 | 1/1 bestanden |
| Vollständige Aufgabenprobe Schrift3,5 | 0/1, UNREACHABLE/H_SUMMARY_REACH, kein Appcrash |
| Unveränderte Ux3d-Funktionen Schrift1→2 | Fehlgeschlagen bei Schrift2/status-group; Label nicht erreichbar |
| Vollständiger P9 Schrift1→2, gleiche Activity | Bestanden, einschließlich finaler Einmalzustellungs-/Inhaltsassertionen |
| Große ACTIVE-/PREPARATION-Listen | Schrift1/2/3,5 bestanden, Scrollanker/Formulare/schnelle Navigation/Lock/late Callback/kein Autoopen |
| Bestehender tatsächlicher Produkt-Semantikrepro | 50/50 bestanden, Schrift2 ohne Accessibility-Automation, 0 Crashes/Harnessfehler |

Die Ux3d-Ausgabe meldet Probezeilen105/96/188:105 ist die unveränderte
visible()-Fehlstelle „Label nicht erreichbar“. Der generische Runner führt den
alten, noch nicht kategorisierten Probeabbruch als ASSERTION; hier wird die
nachgewiesene UI-Erreichbarkeitsfehlstelle präzisiert, **nicht als Erfolg umgedeutet**.
Welche der kombinierten status-group-Erwartungen genau fehlt, ist ohne zusätzliche
Ux3d-Unterphasen offen. Kein Zusammenhang mit dem P9-Fix behauptet, keine
ungeprüfte Erweiterung des Auftrags auf einen Funktions-Produktfix. Die früheren
bestandenen Funktionsläufe bleiben historische Einzelläufe und ersetzen diesen
fehlgeschlagenen Abschlusslauf nicht.

**GEMESSEN:** Der ursprüngliche große A-Fall (200 Personen/1000 Aufgaben/200 Rollen,
VariantA, tatsächlicher SQLCipher-/Workspace-/Composepfad) besteht zehn vollständige
Sequenzen mit ASSESSMENT_PASS und null Workspace-Cachehits. Kein Constraints-
Appcrash. Die Probe meldet vor ihrem abschließenden Fixture-Cleanup FD110→140;
das ist keine Warm-Steady-State-/Leakanalyse und wird nicht als neuer Nachweis
konstanter FD-Zahlen ausgegeben. Kein Resource-/Performancegesamturteil daraus.

**Gesamtregression nicht bestanden.** Keine neue manuelle Produkt-/TalkBack-/
Credentialsabnahme behauptet.


### 28.6 Sicherheitsstatus und Entscheidung

**BEOBACHTET am Diff:** Keine Änderung unter app/data/domain/security src/main.
SessionCoordinator, 180s/30s, StorageAccess/Generation, SessionDbKeyOwner,
SQLCipher-Preflights/Room-/Key-Lease-Lifetime, Tink, Leadership/P3b,
Dokumenten-/PDF-Isolation, Import/Delete-Recovery, Schema11, Produkt-App-ID,
Permissions und Signatur unverändert. Der Semantikfix aus27 bleibt unverändert.
Keine neue Dependency, kein Produkt-RAM-Store, kein Klartextcache/-file, kein
Produktupdate und keine Produktdaten/Credentials verwendet. Die Tests verleihen
keine fachlichen Rechte; Aktualisierung eines Accessibility-Snapshots umgeht
keine Domain-/Write-/Commitprüfung.

**P9:** Konkreter Harness-Auslöser belegt, minimal testseitig korrigiert,
10+10 vollständige Einzel-Schrift2-Vergleiche und finaler vollständiger
Schrift1→2-Pfad bestanden. Keine Behauptung, der historische ungetrennte
Addendumfehler könne in jedem Einzelfall rückwirkend derselben Unterphase
zugeordnet werden. Kein Produktfix notwendig oder durchgeführt.

**Aufgaben3,5:** Ursache noch nicht verlässlich vollständig eingegrenzt.
Modellbestand/Counts und Summarymessung sind vorhanden; Instrumentierung verändert
Erreichbarkeit/Fehlerphase. Mehrere Test-Navigationsideen nicht vollständig
bestanden, statische Whole-content-Scrollhypothese falsifiziert. Daher STOPP für
weitere spekulative Änderungen. Der nächste kleinste Diagnosepunkt ist die
Rücknavigation nach Speichern mit klar getrennten frischen Node-/Fokus-/Scroll-
Snapshots und minimaler Observerlast; anschließend isolierte Ein-Variablen-
Kontrollen statt weiterer kombinierter Navigationsänderungen.

**Zusätzlich offen:** Neuer fehlgeschlagener Ux3d-status-group-Abschlusslauf,
unabhängiger Review und P11-/Releasefragen gemäß älteren Abschnitten. Keine
Gesamt-GRÜN-/Produkt-/Stufe2-Freigabe. Issue#26 bleibt verbindliches Release-Gate
vor1.0. WIP-Commit/Push ausdrücklich beauftragt; kein Force-Push, Merge, Release
oder Produktupdate. Nach Sicherung keine weitere Fehleranalyse vor unabhängigem
Review.

### 28.7 Vollständige Dateiliste dieser Runde

- `app/src/androidTest/kotlin/org/pag/prototype/Ux3cTasksProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P9MemoryReportProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/UiReachabilityTrace.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/UiScrollViewportProbe.kt`
- `tools/p11semantics/ActualTestManifest.xml`
- `tools/p11semantics/regressions.py`
- `tools/p11semantics/README.md`
- `docs/product-p11-active-workspace-assessment.md`
- `docs/product-p11-performance.md`

9 Textdateien. Keine APKs, Datenbanken, Keystores, Credentials, Rohlogs,
Gerätekennungen, persönlichen Pfade oder Klartext-Testdokumente im Commit.
Bereits öffentliche Historie nicht umgeschrieben.

## 29. Abschlussdiagnose Aufgaben3,5 und Funktionenstatus

Untersuchungsbasis `ec5cd329bf5ecdd10a11964f613c0fc18f5b72fd`.
Nur isolierte synthetische Testpakete auf Samsung SM-M526BR/Android13/API33.
Die belegten Footer-/P9-Korrekturen bleiben erhalten. Keine Änderung unter
`src/main`, keine Produktinstallation und keine Stufe2-Integration.
Die fehlgeschlagenen Versuche aus26–28 bleiben unverändert dokumentiert.

### 29.1 Kontrollaufbau und Instrumentierungseinfluss

Ziel-APKs unverändert aus27.3: vor Footerfix SHA256
`9cdecf178ef3a1a6862b4068e8c1793057df734761329fcabd32bada6783c81e`,
mit Fix `e3160b230ab0445fbccd17504e7d6be981bbbe18e50a1e689b874200a91318a2`.
Pro Vergleichsarm identische eingefrorene Test-APK. Kein Produkt-Rebuild innerhalb
einer Reihe. Paketprüfung vor isolierten Updates, unveränderter Keystore.
Keine Produktdaten, Credentials, Dokumente oder realen Identitäten.

`UiReachabilityTrace` ergänzt feste Routennamen, Operations-Screen/Busy,
Taskselected/Form, Feedback/IME/Fokus als Booleans, Node-Bounds/Actions,
Refresh-Erfolg und angenommene Scrollaktion. Ein ohne Refresh gelesener Node ist
**nicht als aktuell bewiesen**. Lazy-Index/Offset und viewport/summary-Messdaten
werden auf Main gelesen; nach einer Scrollannahme ist die Bewegung noch asynchron.
Erst der folgende Suchsnapshot zeigt die spätere Lazy-Position. Bei Column ist
kein direkter ScrollState-Offset exponiert: Bounds/Actionrand sind kein Ersatz
für einen gemessenen Pixeloffset. Die weiterhin ausbleibenden ScrollY-Events
werden nicht zu erfundenen Offsets umgedeutet. Root-Composition-/Draw-Zähler
beweisen nicht sämtliche verschachtelten Renderzustände.

Marker0–7 in der detaillierten Suche bezeichnen ausschließlich die festen
synthetischen Matcher für Aufgaben-Rückknopf, Formular-Rückknopf, Lagebild-Rückknopf,
Bereichsüberschrift, Chronik, Speichern, Bearbeiten und geänderten synthetischen
Titel. Ausgegeben werden nur Nummern und Bounds. Keine Node-Texte oder IDs.
Alle Baumabfragen sind auf das isolierte Zielpaket begrenzt.

**GEMESSEN, Ausgangskontrolle:** Unveränderte ec5-Test-APK: Aufgaben3,5 scheitert
wieder in H_SUMMARY_REACH; Funktionen Standard1→2 besteht einmal. Das widerlegt
den vorherigen sporadischen Funktionsfehler nicht. Neue ausführliche Diagnose mit
ursprünglicher Node-Steuerung: Aufgaben3,5 passiert H und scheitert später in
`start`; Aufgaben2 und Funktionen2 bestehen je einen Volltest. Die Beobachtung
selbst verändert Timing/Fehlerphase. Diese Piloten sind keine Validierungsserie.

### 29.2 H_SUMMARY_REACH: isoliert nachgewiesener Testfehler

**GEMESSEN:** Kontroll-Test-APK SHA256
`7c70aa16e1da2b586b002684b83935a55e480a549917b70b65b797340b763026`.
Fünf vorher festgelegte Versuche je Ziel und Steuerung; nur Phasendiagnostik,
keine ausführlichen Node-Logs. Kontrollmodus `checkpoint=summary` endet ausdrücklich
nach den unveränderten A–H-Assertions. Sein Marker TASK_SUMMARY_DIAGNOSTIC_PASS
ist **kein** vollständiger UX3c-Erfolg.

| Eingefrorenes Ziel | Ursprünglicher Baum ohne Refresh | Ausschließlich Baum-Refresh |
|---|---:|---:|
| Vor Footerfix | 0/5; alle H_SUMMARY_REACH | 5/5 Teilnachweise |
| Mit Footerfix | 0/5; alle H_SUMMARY_REACH | 5/5 Teilnachweise |

Keine Appcrashes. Gleiche Fixtures, fachliche Counts, Scrollrichtung, 50 Versuche,
ursprüngliche 80-ms-Pollschritte und normale Accessibility-Scrollaktionen.
Kein scrollToItem, keine ViewModel-Navigation. Als einzige Kontrollvariable werden
neu geholte Nodes vor Auswertung mit öffentlichem refresh() aktualisiert;
fehlgeschlagener Refresh verwirft einen obsoleten Node.

**BEOBACHTET:** Legacy-Snapshot nach akzeptiertem ersten Scroll:
Lazy0/1832, Summary Offset118/Größe1194, aber null passende Textnodes. Die alte
Steuerung scrollt sofort nochmals; beim später eintreffenden Label steht Lazy
bereits2/478 ohne sichtbares Summaryitem. Refresh-Kontrolle findet die Summary
bei Lazy0/1832 mit Bounds(63,497–1017,939), ohne zweiten Scroll. Die alte Suche
verwechselt ihre gecachte Accessibilityprojektion mit dem aktuellen sichtbaren
Zustand und scrollt über das Ziel hinweg. Modell/Counts sind korrekt.

**Minimale testseitige Korrektur:** Aufgaben-Standardsteuerung aktualisiert den
neu abgefragten Baum; `navigation=legacy` bleibt die explizite Kontrollvariante.
Keine Produktsemantik entfernt, keine Assertion abgeschwächt und keine Wartezeit
verlängert. Der vollständige Aufgaben3,5-Pfad ist dadurch noch nicht bestanden.

### 29.3 Spätere Aufgabenfehler getrennt betrachten

**GEMESSEN/Diagnosepiloten, jeweils ein vollständiger Versuch auf Fix-Ziel:**

| Testkontrolle | Ergebnis |
|---|---|
| Nur Node-Refresh, ausführliche Logs | H bestanden; UNREACHABLE in start |
| Nur Node-Refresh, nur Phasenlogs | H bestanden; UNREACHABLE in start |
| Zusätzlich gerichtete Statussuche rückwärts und Formularfeldsuche vorwärts | UNREACHABLE EDIT_RETURN_CLICK nach bestätigtem Edit |
| Rückknopfsuche ab Top stattdessen vorwärts | Ebenfalls UNREACHABLE EDIT_RETURN_CLICK |
| Gleicher Vorwärtsversuch mit festen Geometriemarkern | Ebenfalls UNREACHABLE EDIT_RETURN_CLICK |

Alle ohne Appcrash. Diese unterschiedlichen Pilotstände werden nicht als eine
Erfolgsstatistik addiert. Ursprüngliche Status-/Event-/Edit-/Count-Erwartungen
bleiben erhalten; akzeptierte Speicheraktionen werden nicht wiederholt.

**BEOBACHTET, start:** Statusmutation ergibt korrekt0 OPEN/2 IN_PROGRESS.
Nach dem tief liegenden Statusbutton liegt die erwartete Statuszeile oberhalb
des Viewports. Ausschließlich vorwärts gerichtete Labelsuche erreicht den unteren
Rand; weitere Aktionen werden abgelehnt. Rückwärts gerichtete Gegenprobe erreicht
die Status-/DONE-/Reopen-Assertions und anschließend den Editor. Das ist eine
zusätzliche Testpositionsannahme, unabhängig vom Summarycache.

**BEOBACHTET, Rückkehr nach Edit:** Modellroute TASK, selected=true, form=false,
korrekter neuer Titel und1 OPEN/1 IN_PROGRESS. Fokus=false, IME=false. Nach top()
bietet der frisch gelesene Scrollnode keine Backwardaktion mehr. Der Rückknopf
ist dennoch nicht im zugänglichen Baum. Auch Vorwärtssuche passiert ihn in diesem
Pilot nicht. Während Feedback=true liegt der zugängliche Scrollbereich bei
(63,843–1017,2211); nach Feedbackende bei(63,379–1017,2211). Der reale Layout-
Viewport wird durch das Overlay nicht kleiner; der zugängliche Ausschnitt schon.
Marker für Lagebild-Rückkehr und Aufgabenüberschrift sind am oberen Rand sichtbar;
nach einer Vorwärtsaktion erscheint der geänderte Titel, aber nicht der erwartete
Aufgaben-Rückknopf. Ein einfacher falscher Modellzustand oder offene IME erklärt
diesen Befund nicht. **HYPOTHESE:** Ganzseitige Scrollschritte und Bannerüberdeckung
lassen eine dazwischenliegende schmale Zielgeometrie aus.

Weitere Geometrie-/Touchkontrollen und Abschlussstatus werden unten getrennt
berichtet. Eine neue Produktkorrektur wird aus dieser Hypothese nicht abgeleitet.

### 29.4 Funktionen: Unterphasen und Kontrollgrenzen

Die bisher kombinierte status-group-Phase ist jetzt getrennt:

| Phase | Prüfung |
|---|---|
| SG_A_OVERVIEW | ActiveArea.FUNCTIONS, OperationsScreen.OVERVIEW |
| SG_B_DOMAIN_GROUP | Synthetische Person hat erwartete aktuelle Funktion |
| SG_C_GROUP_TREE | Fester Gruppenmatcher/Geometrie, keine neue Navigation |
| SG_D_GROUP_REACH | Ursprüngliches top(), anschließend Beobachtung |
| SG_E_GROUP_CLICK | Ursprüngliche echte Button-/Accessibilityaktion |
| SG_F_GROUP_MODEL | FUNCTION_DETAIL und erwarteter FunctionFocus |
| SG_G_PERSON_DOMAIN | Person ON_SITE und ursprünglicher sichtbarer Gruppencount |
| SG_H_PERSON_REACH | Matcher-/Geometriesnapshot |
| SG_I_PERSON_OPEN | Ursprünglicher Click, PERSON und erwartete Personbindung |
| SG_J_FUNCTION | Ursprüngliche sichtbare aktuelle Funktion |

C/D/H sind Beobachtungen, keine behaupteten Sichtbarkeitsassertionen: Die
Erreichbarkeit wird erst durch E bzw. I und die nachfolgenden Modell-/Textassertionen
bestätigt. Keine zusätzlichen sicht()-Suchläufe im maßgeblichen Kontrollarm.
Suchaktionen und ursprüngliche gesamte Funktionsfolge bleiben erhalten.
Explizite Einzelfonts1/2 sind zusätzlich wählbar; Standard1→2 in derselben Activity
bleibt erhalten. Einzelfont2 ist nicht gleichbedeutend mit dem Schriftwechselpfad.

`navigation=parents` ist eine getrennte Kontrolloption für die Hypothese veralteter
getParent()-Instanzen; sie ist für Funktionen **nicht automatisch aktiviert**.
Ein erfolgreicher P9-Nachweis beweist diesen Fehler nicht für Funktionen.

**GEMESSEN, Kontroll-Test-APK7c70… ohne Vorfahren-/Baum-Refresh, nur Phasenlogs:**

| Zielstand | Vollständige Schrift2-Versuche | PASS | Fehler |
|---|---:|---:|---|
| Vor Footerfix | 10 | 10 | Keine |
| Mit Footerfix | 10 | 9 | 1× hub-after-change, visible()-Erreichbarkeit |

Alle20 passieren die getrennten status-group-Schritte; keine Appcrashes.
Der spätere Fehler wird nicht als status-group-Fehler oder Erfolg umgedeutet:
ACTIVE_HUB, korrekter aktualisierter Bestand, alle Personen-/Gruppen-/Status-
Assertions zuvor bestanden; anschließend fehlende Label-Erreichbarkeit im Hub.
Die damalige Probe meldet generisch ASSERTION, die aufgezeichnete visible()-
Fundstelle präzisiert die Erreichbarkeit. Der finale Code kategorisiert solche
Abbrüche explizit UNREACHABLE. Die Kontroll-APK besaß noch zusätzliche reine
Baumabfragen in C/D/H, obwohl nur Phasenlogs aktiv waren. Diese Beobachterlast ist
eine Einschränkung; im finalen Teststand werden diese Abfragen nur bei detailed
angefordert. Kein Kausalitätsbeweis für den historischen status-group-Ausfall.

**GEMESSEN/Piloten:** Ein vollständiger Funktionen2-Lauf mit ausschließlich
aktiviertem Vorfahrenrefresh und detaillierter Diagnostik bestanden. Dabei kein
vorher/nachher-Wechsel von isEnabled an den beobachteten Clickvorfahren gemessen.
Somit kein P9-ähnlicher disabled→enabled-Auslöser für Funktionen belegt.
Ein vollständiger Standard1→2-Lauf mit ausgeschalteten Diagnosebeobachtern und
unverändertem ProductScreen-Root bestanden; ebenso ein finaler1→2-Lauf mit
Phasendiagnostik. Diese Einzelläufe schließen Instrumentierungs-/Timingeffekte
nicht aus. Die ursprüngliche Assertionsfolge bleibt in beiden erhalten.

**Root-Cause-Status Funktionen:** Historischer status-group-Ausfall weiterhin
nicht eindeutig einer A–J-Unterphase zugeordnet/reproduziert. Die aktuellen
Kontrollen liefern korrekte Modellzustände und echte erfolgreiche Gruppen-/
Personenaktionen, aber keine verlässliche Widerlegung eines seltenen Fehlers.
Kein Produktfix, keine spekulative automatische Übernahme des P9-Vorfahrenfixes.
Der neue spätere Hub-Erreichbarkeitsfehler bleibt separat offen.

### 29.5 Zusätzliche Kontrollen und Kandidatenvalidierung

Die erste synthetische `UiFeedbackOcclusionProbe` endet mit Android-Meldung
Process crashed, ohne verwertbaren Java-/Native-Stack im lokalen Crashpuffer.
**Kein** bestandener Geometrienachweis und keine bewiesene Produkt-/Bannerursache.
Die Probe hat keine Repositories/ViewModels/Produktdaten und nur feste synthetische
Geometrie. Eine nachfolgende Fassung ergänzt ausschließlich feste Phasen und
Uncaught-Klassen/Stackfundstellen mit Weitergabe an den Originalhandler.
Eine UI-/Framework-Ausnahme darf nicht verschluckt oder aus der Statistik entfernt
werden. Der ursprüngliche Abbruch bleibt als eigener Pilot sichtbar.

**GEMESSEN, Kandidatenpiloten auf Fix-Ziel, Test-APK402bc5…:**
Touchscroll zum Rückknopf erreicht sowohl die Edit-Rückkehr als auch die Rückkehr
nach Create; der vollständige Versuch scheitert später in create-return-label.
Dort Lazy0/0, korrekte2 OPEN/1 IN_PROGRESS, Summary unterhalb des hohen Headers;
die ausschließlich rückwärts gerichtete Suche ist am oberen Rand. Der Rückknopf
ist somit über normale Touch-/Accessibility-Clickaktionen erreichbar; keine
bewiesene grundsätzliche Produktunerreichbarkeit. Ein ausschließliches
Accessibility-Scroll-/TalkBack-Prädikat folgt daraus nicht.

Ein vollständiger `sweep`-Pilot mit ausführlicher Diagnostik besteht Schrift3,5.
Dieser Kontrollmodus nutzt frische Nodes/Vorfahren, berücksichtigt die bereits
beobachteten Status-/Formularpositionen und kehrt die Suchrichtung nur nach einer
abgelehnten Scrollaktion an einem tatsächlich vorhandenen Container um. Kein
fehlender/transienter Container wird als Rand gewertet. Ursprünglich50 Versuche,
gleiche Pollintervalle, keine neue Sleep-/Timeoutverlängerung, keine Wiederholung
akzeptierter oder unklar zugestellter Writes. Alle ursprünglichen Status-/Event-/
Edit-/Create-/Filter-/Lockassertionen bleiben. Ein einzelner Pilot ist noch keine
belastbare Korrektur. Die vorab festgelegten vollständigen10er-Reihen ohne
Detaildiagnostik werden separat ausgewiesen; die Standardsteuerung wird daraus
nicht vorzeitig als bestanden bezeichnet.

### 29.6 Abschlussregression des Standard-Testpfads vor Sweep-Freigabe

**GEMESSEN**, eingefrorene Test-APK SHA256
`402bc5fdd4bd7ec3249aaaae0aeb52de02bd449f6fc59f8b655a256e41496169`:

| Prüfung | Ergebnis |
|---|---|
| Aufgaben1, Standard mit Baum-Refresh | 1/1 vollständiger Lauf bestanden |
| Aufgaben2, Standard mit Baum-Refresh | 1/1 vollständiger Lauf bestanden |
| Aufgaben3,5, Standard mit Baum-Refresh | 0/1, UNREACHABLE in start; H passiert |
| Funktionen Standard1→2 | 1/1 vollständiger Lauf bestanden |
| P9 Standard1→2 | Vollständig bestanden, einschließlich Addendum/Einmalzustellung/Portinhalt/Lock |
| Große ListStabilityProbe | ACTIVE/PREPARATION, Schrift1/2/3,5 bestanden, Scrollanker/Formulare/Lock/late Callback/kein Autoopen |
| Tatsächlicher Material3-Footer-Repro | 50/50 bestanden, Schrift2 ohne Automation,0 Crashes/Harnessfehler |

Die neue spätere Task-Teststeuerung wird nicht rückwirkend als Bestandteil dieses
Standardlaufs ausgegeben. Vollständige Ux3c-Ergebnisse werden nie durch einen
Summary-Teilnachweis, den Footer-Repro oder eine Modellprobe ersetzt.
Aufgelöste Zielversionsmarker unverändert: Material3-android1.4.0,
Compose UI/Runtime/Foundation1.12.1. Kein Dependencywechsel.

**Erneut ausgeführt:**7 fokussierte Hosttests (ProductViewportTest3,
ProductEntityListTest4),0 Failures/Errors/Skips; Offline-Testbuild bestanden.
Der exakte historische Constraints-Zahlenfall bleibt enthalten. **Nicht erneut
ausgeführt:** vollständige1266er-Hostsuite und12er-Storage-Suite; deren frühere
Nachweise in28/24 bleiben historisch. Keine Produktquelle oder fachliche Mutation
in dieser Runde geändert. Keine neue manuelle Geräte-/TalkBack-/Credentialabnahme.

### 29.7 Bewertung der Aufgaben-Kandidatenkorrektur

**GEMESSEN:** Zehn vorab festgelegte vollständige Schrift3,5-Läufe auf dem
Fix-Ziel mit `navigation=sweep`, nur Phasenlogs:10/10 bestanden,0 Appcrashes,
0 Harness-/Assertion-/Erreichbarkeits-/Timeoutfehler. Kein Austausch der Fixture,
kein Abbruch nach erstem Erfolg. Die identische Test-APK402bc5… besteht auch auf dem eingefrorenen Vorher-Ziel
zehn vollständige Läufe:10/10,0 Crashes/Harness-/Assertion-/Erreichbarkeits-/Timeoutfehler.
Beide Reihen sind unabhängig vom einen ausführlich diagnostizierten Piloten.

Daraus wird `auto` ausschließlich in der Aufgaben-Teststeuerung auf denselben
Sweep-Pfad abgebildet. `legacy`, `nodes`, `parents`, `directed` und `touch` bleiben
explizite Kontrollvarianten. Die späteren Normalfont-/Auto-Abnahmeläufe werden
mit der neu gebauten Alias-Test-APK separat geprüft. Kein Produktfix und keine
Änderung des fachlichen UI-/Session-/Storagevertrags.

**Nachgewiesen:** Die alte Steuerung benötigt frische Accessibilityprojektionen
und darf bei Großschrift keine feste Anfangsposition voraussetzen. Zusammenfassung
unter hohem Header, Statuszeile oberhalb des Statusbuttons, offscreen Editorfeld
und erhaltene Listenanker sind unterschiedliche Suchsituationen. Die Testkorrektur
benutzt reguläre Scroll-/Click-/IME-Aktionen mit unveränderten Originalassertionen.
Sie erklärt nicht pauschal jede AndroidX-/TalkBackbesonderheit. Die genaue Rolle
der Bannerüberdeckung bleibt von diesen vollständigen Bedienungsnachweisen
getrennt; es wurde kein spekulativer Banner-Produktfix vorgenommen.

**GEMESSEN, finale Standardalias-Abnahme**, Test-APK SHA256
`6be304facc0e0314af8eeaf2ff5c12dd77f625505568de7546dbc04b571d6da5`:
Aufgaben1/2/3,5 jeweils1/1 vollständiger Lauf bestanden; Funktionen1→2 ebenfalls
1/1 bestanden. Diese zusätzlichen Läufe werden nicht mit den10+10-Vergleichen
vermengt. Finale Abbildung auto→sweep ist nur eine Aliaswahl derselben geprüften
Aufgabensteuerung. Alle vorherigen fehlgeschlagenen Standards/Piloten bleiben
oben gezählt. Zehn Fälle pro Ziel sind ein begrenzter Regressionsnachweis, keine
Garantie gegen jede seltene Geräte-/Screenreader-/Frameworkkonstellation.

### 29.8 Geometrienachweis und Korrektur der eigenen Diagnoseprobe

**GEMESSEN:** Zweiter Geometrie-Pilot mit festen Phasen/Uncaught-Beobachtung,
Test-APK `5b7e5434f6533e27b6d5fc79d2440676d9d8a3a2dbca25162153d15966f62c80`,
bricht ebenfalls ab. Jetzt konkreter Stack auf main:

```text
UninitializedPropertyAccessException
UiFeedbackOcclusionProbe.onStart [MEASURE_false_0, damalige Probezeile67]
Instrumentation.SyncRunnable.run:2353
Handler.handleCallback:942
Looper.loopOnce:226
```

**Nachgewiesener Harnessfehler:** Der neue Test fragt den lokalen lateinit-
ScrollState vor der ersten Composition ab; runOnMainSync ist keine Zusicherung,
dass das zuvor gesetzte Composecontent bereits initialisiert/gemessen ist.
Die Ausnahme im Test-Runnable beendet tatsächlich den Appprozess. Das ist hier
wegen konkreter Quell-/Stackzuordnung ein Testfehler, nicht allein wegen des
Umstands, dass Instrumentation läuft. Für den ersten Pilot ohne Stack bleibt die
exakte rückwirkende Zuordnung unbewiesen. Beide Abbrüche werden erhalten:
0/1 ursprünglicher Pilot,0/1 instrumentierter Pilot; keine Produkt-/Material3-
Exception daraus erfunden.

**Minimaler Probe-Fix:** Atomare Composition-Readiness wird im SideEffect gesetzt;
die bestehende begrenzte Messbedingung greift erst danach auf ScrollState zu und
verlangt zusätzlich positive tatsächliche Viewport-/Scrollmaße. Keine zusätzliche
Sleep-/Timeoutverlängerung, kein catch um Produktcomposition, keine verschwiegene
Exception. Uncaught-Klassen/Stackbeobachtung delegiert weiterhin an den Originalhandler.

**GEMESSEN**, korrigierte Test-APK SHA256
`7ff8e77640bd540fb828bdbd64e7b54d5d2380d663b01f6b759ac5b52f36a453`:
ein vollständiger Geometrieablauf mit je5 unabhängigen Setups ohne/mit Overlay,
alle10 Messfälle abgeschlossen,0 Crashes/Harnessfehler. Schrift3,5, bestehender
PagWorkspace-Banner, fester synthetischer Marker hinter einem dem hohen Header
entsprechenden Spacer. Kein ProductViewModel/Repository, keine Produktnavigation.
Das synthetische Feedback bleibt absichtlich während des Versuchs vorhanden.

| Kontrollvariable | Fälle | Tatsächlicher Scrollviewport | Delta nach ACTION_SCROLL_FORWARD | Marker im Accessibilitybaum nach Seite | Nach zwei kleinen normalen Touchscrolls |
|---|---:|---:|---:|---:|---:|
| Ohne Overlay | 5 |1832px|1832px|5/5|5/5|
| Mit Overlay | 5 |1832px|1832px|0/5|5/5|

Identische gemessene Markerlayout-Bounds nach Seitenschritt in Rootkoordinaten:
(63,426–563,652),226px Höhe. Screenkoordinaten liegen hier90px darunter.
Layoutviewport/Delta sind korrekt begrenzt, **keine** Wiederbelebung der verworfenen
Whole-content-Scrollhypothese aus28.4. Das Overlay verdeckt jedoch einen Teil
dieses Viewports im Accessibilitybaum; eine kleine dazwischenliegende Geometrie
kann an einem ganzseitigen Scrollendpunkt verdeckt sein. Kleine Touchscrolls
machen denselben Marker zugänglich. Dies ist ein kontrollierter Komponentenbefund,
kein vollständiger Beweis jedes Zeitverlaufs im ursprünglichen Aufgaben-/Funktionenfall.

**Einordnung:** Die durchgehenden Aufgaben-Volltests belegen Bedienbarkeit über
normale Accessibilityaktionen mit positionsrobuster Steuerung. Temporäre
Bannerüberdeckung ist ein echtes Verhalten der vorhandenen UI-Komponente;
welchen Anteil sie an einem historischen Einzelfehler hatte, wird nicht rückwirkend
behauptet. Ein allgemeines TalkBack-/Overlay-UX-Urteil oder spekulativer Produktfix
folgt daraus nicht. Neue manuelle Screenreader-Endabnahme bleibt offen.

### 29.9 Sicherheitsstatus, Gerätebereinigung und verbleibender BLOCKER

**BEOBACHTET am vollständigen Diff:** Keine Änderung an Produktquellen,
SessionCoordinator/180s/30s, StorageAccess/Generation, SessionDbKeyOwner,
SQLCipher-/Room-/Key-Lifecycle, Tink, Leadership/P3b, Dokument-/PDF-Isolation,
Recovery, Schema11, Permissions, Produkt-App-ID/Signatur oder RAM-Vertrag.
Kein produktiver Store/Cache, kein Produktupdate, keine Produktcredentials/-daten
verwendet. Keystore-Fingerprint unverändert. Keine neuen Dependencies/Netzwerk-
funktionen, keine Klartextpersistenz. Die Prüfungen sind begrenzte Nachweise und
kein Ersatz für vollständigen unabhängigen Securityreview oder Issue#26.

**Auf zusätzlichen ausdrücklichen Benutzerauftrag:**17 eindeutig identifizierte
isolierte PaG-Test-/Demo-Pakete entfernt, erst nach Ende aller Testläufe. Die
Lösch-Allowlist schließt `org.pag.prototype.a` technisch aus. Vorher/nachher bleiben
Produktversion und firstInstallTime/lastUpdateTime identisch; keine Produktdatenbank
kopiert, geöffnet, analysiert, zurückgesetzt oder gelöscht. Kein pm clear, keine
Produktdeinstallation, kein Signaturwechsel. Abschließender PaG-Paketbestand:
ausschließlich `org.pag.prototype.a`. Andere Apps und öffentliche Dateien unberührt.
Diese Bereinigung ist keine Produktinstallation und keine neue Produktabnahme.

**BLOCKER bleibt:** Die historische Funktionen-status-group-Ursache ist nicht
verlässlich exakt eingegrenzt. Zusätzlich ein gemessener späterer Hub-
Erreichbarkeitsfehler in der10er-Fix-Ziel-Reihe. Erfolgreiche spätere Einzelläufe
ersetzen den Fehlversuch nicht. Kein erfundener Funktionsfix und kein pauschales GRÜN.
Aufgaben3,5 ist für die unveränderte synthetische Vollprobe testseitig korrigiert;
daraus entsteht keine neue Produkt-/Stufe2-Freigabe.

**Engster nächster Diagnoseschritt nach unabhängigem Review:** Funktionen bei
fehlendem Gruppen-/Hubziel vor der nächsten Scrollaktion einen positionsgleichen
Vorher-/Refresh-Nachher-Kontrollsnapshot erfassen; feste Unterphasen beibehalten,
Scrollrand/Overlay und aktuelle Node-Vorfahren getrennt prüfen. Dazu identische
Test-APK mit abgeschalteten gegenüber aktiven Beobachtern sowie Einzel-Schrift2
gegen gleiche-Activity1→2 vergleichen. Keine neue Navigation/Writewiederholung,
kein Produktfix ohne konkreten Auslöser. Dieser nächste Schritt wurde hier nicht
als bereits ausgeführt oder bestanden ausgegeben. Nach WIP-Push STOPP/Review.

### 29.10 Vollständige Dateiliste dieser Runde

- `app/src/androidTest/kotlin/org/pag/prototype/Ux3cTasksProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/Ux3dFunctionsProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/UiReachabilityTrace.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/UiFeedbackOcclusionProbe.kt`
- `tools/p11semantics/ActualTestManifest.xml`
- `tools/p11semantics/regressions.py`
- `tools/p11semantics/README.md`
- `docs/product-p11-active-workspace-assessment.md`

8 Textdateien. Eingefrorene APKs, rohe Instrumentierungs-/Crashausgaben,
maschinenlesbare Zusammenfassungen, temporäre lokale Runner und Bereinigungs-
Allowlist verbleiben ausschließlich im ignorierten Diagnosebereich. Keine
Datenbanken, Keys/Keystores, Credentials, Produktdaten, Gerätekennungen,
persönlichen Pfade oder Buildartefakte im vorgesehenen Commitumfang.
Keine alten Veröffentlichungen oder Git-Historie umgeschrieben.


## 30. Funktionsnavigation: Erreichbarkeit und Feedback-Overlay

### 30.1 Auftrag und unveränderte Grenzen

Ausgangspunkt `e1dc8a8d9bacefc05ded82465ac0c1ac16666577`. Ausschließlich
Funktions-UI-Erreichbarkeit untersuchen; kein Produktfix auf Verdacht. Die
historischen fehlgeschlagenen Versuche in26–29 bleiben erhalten. Insbesondere
belegen weder9/10 gegenüber10/10 noch ein einzelner späterer Erfolg eine
Kausalität des Footerfixes. Keine produktive Stufe2, keine neue RAM-Vorhaltung,
keine Änderung an Session, SQLCipher, Dependencies oder Produktinstallation.

### 30.2 Testseitige Unterphasen und Beobachtungsgrenzen

Die ursprüngliche vollständige synthetische Funktionsfolge bleibt erhalten:
Gruppen-/Personenöffnung, Funktionsänderung, Rückkehr zur leeren Gruppe,
Ohne-Funktion-Gruppe, neue Funktion, weitere Funktionen, Statusänderung,
Hubinformationen sowie Lock im Detail und im Formular. Keine akzeptierte oder
unklar zugestellte Schreibaktion wird erneut ausgeführt. Ursprüngliche50
Suchversuche und Pollintervalle bleiben unverändert. Keine neue künstliche
Wartezeit oder direkte ViewModel-Navigation im untersuchten Benutzerpfad.

| Unterphase | Zusätzlicher Nachweis |
|---|---|
| HC_A_COMMIT | Genau2 bestätigte synthetische Status-/Funktionsupdates, erwarteter letzter Status |
| HC_B_OPERATIONS | OVERVIEW, kein verbliebener Personen-/Gruppenfokus |
| HC_C_BACK_CLICK | Ursprüngliches top() und echte Rückknopfaktion |
| HC_D_ROUTE | HUB und Operations-OVERVIEW |
| HC_E_WORKSPACE | Workspace vorhanden, gleiche Generation, ACTIVE, Operationsboard stimmt überein, Taskboard vorhanden |
| HC_F_DOMAIN | Erwartete Verteilung und originale Hubcardprojektion fachlich korrekt |
| HC_G_DRAW | Root-Draw-/Compositionzähler als begrenzter Proxy |
| HC_H_TREE | Bei Detaildiagnostik reine geometrische Baumbeobachtung |
| HC_I_REFRESH | Nur expliziter Paired-Kontrollarm: dieselben Nodes aktualisieren, anschließend frischen Baum abfragen |
| HC_J_GEOMETRY | Viewport-/Scrollereignis-/Fokus-/IME-Snapshot |
| HC_K_FEEDBACK | Feedbackstatus zum aktuellen Modellzeitpunkt |
| HC_L1_HEADING | Ursprüngliche sichtbare Lagebildüberschrift |
| HC_L2_CONTENT | Ursprünglicher sichtbarer Funktionsinhalt |

**Grenzen:** Der Fake-Repositoryzähler ist kein SQLCipher-Commitnachweis und keine
produktive Write-Regression. Root-Draws beweisen keine Messung einer bestimmten
Hubcard. Accessibility-Bounds sind die zugängliche Geometrie; der zuletzt gemeldete
Scroll-Eventwert ist kein direkter Compose-ScrollState-Read. Die Probe behauptet
keine noch nicht beobachteten Itemkoordinaten. Der vollständige Funktionslauf bleibt
erforderlich; zusätzliche Modellassertionen ersetzen keine UI-Erreichbarkeit.

`--witness paired` ist eine eigene Diagnosevariable, keine inerte Beobachtung:
refresh() kann den UIAutomation-Cache verändern. Vor einer weiteren Scrollaktion
werden feste Counts, Bounds, Sichtbarkeit, Enabled-Status und Scrollaktionen zuerst
an den vorhandenen Instanzen, dann nach refresh() an denselben Instanzen und am
neu abgefragten aktualisierten Baum aufgezeichnet. Dazwischen keine Click-/Scroll-
aktion, aber keine atomare Sperre gegen asynchrone Layout-/Semantikpublication.
Drawzähler und Geometrien sind deshalb mit zu vergleichen. Dasselbe optionale
Verfahren ist an den ursprünglichen Gruppen-/Personenclicks anschaltbar.
Die Navigation verwendet standardmäßig weiterhin keine Funktions-Refreshkorrektur.
Feste Matcherliterale bleiben nur intern; Inhalte/IDs werden nicht ausgegeben.

### 30.3 Vergleichsmethodik

Samsung SM-M526BR, Android13/API33. Ein eingefrorenes isoliertes Ziel mit dem
belegten Footerfix; identische synthetische Fixtures. Unveränderte historische
Test-APK zuerst als eigene Baseline, anschließend eine einzige neue Diagnose-APK
für sämtliche Ein-Variablen-Kontrollen. Keine Erfolgsquoten über APK-/Beobachter-
versionen addieren. Einzelfont2 startet eine frische Activity; `both` durchläuft
1→2 in derselben Activity und ist eine andere Konfiguration.

Der Runner prüft beide Paket-IDs und beide APK-Signaturen **vor** der ersten
Installation; nur `org.pag.perf.p11probe` und dessen `.test` zugelassen, identische
verifizierte Zertifikate erforderlich. Resultate enthalten Ziel-/Test-APK-SHA256,
Kontrollflags, Fehlerkategorie und konkrete Fehlerphase. Rohbelege ausschließlich
im ignorierten lokalen Diagnosebereich. Ein Diagnoselauf darf durch Beobachterlast
anders verlaufen; daraus folgt noch keine Produktkorrektur.


### 30.4 Unveränderte Baseline vor jeder Korrektur

**GEMESSEN:** Zehn vorab festgelegte vollständige Schrift2-Läufe auf dem Fix-Ziel,
Navigation legacy, nur Phasenbeobachter, unveränderte Test-APK aus29.8: 8/10
bestanden,2/10 UNREACHABLE in hub-after-change (Versuche2 und8 bei einsbasierter
Zählung). Keine Appcrashes, Harness-/Assertion-/Timeoutfehler. Alle zehn passieren
sämtliche status-group-Unterphasen. Die Baseline wird nicht mit der älteren9/10-
Reihe addiert und beweist weiterhin keinen Zusammenhang mit dem Footerfix.

Ziel-SHA256:
`e3160b230ab0445fbccd17504e7d6be981bbbe18e50a1e689b874200a91318a2`.
Unveränderte Baseline-Test-SHA256:
`7ff8e77640bd540fb828bdbd64e7b54d5d2380d663b01f6b759ac5b52f36a453`.
Neue Diagnose-Test-SHA256 für alle nachfolgenden Ein-Variablen-Piloten:
`78551cfdf1040401e8bf4c1d49f9db429d610e33c6ef2cfb00bcab29eaf15bf7`.

**BEOBACHTET an beiden Fehlerabschlüssen:** Route ACTIVE_HUB, Workspace und
Taskboard vorhanden, Operations-OVERVIEW, kein Personen-/Gruppenfokus,
feedback=false, focus=false, ime=false. Die ursprüngliche kombinierte
visible()-Fundstelle trennt Überschrift und Funktionsinhalt noch nicht. Im Modell war am
Fehlerabschluss kein Feedback aktiv; direkte Bannergeometrie dieser Fehlversuche fehlt; damit ist ein
früherer Einfluss von Overlay, Scrollposition oder Semantikcache nicht ausgeschlossen.
Keine Produktkorrektur aus diesem Befund abgeleitet.


### 30.5 Geometrie und erster Paired-Kontrollarm

**GEMESSEN, erste Diagnose-APK,2 vollständige Schrift2-Piloten mit detaillierter
Diagnostik und Paired-Kontrolle:** Beide bestanden. Dabei keine neue akzeptierte
Click-/Scrollaktion zwischen den jeweiligen Vorher-/Refresh-Nachher-Snapshots.

| Messstelle | Vorher | Dieselben Nodes nach Refresh / frischer Baum |
|---|---|---|
| Statusgruppe, erster Clickversuch |1 Ziel, enabled=true, sichtbar; Bounds(126,2145–770,2211)|Unverändert, gleicher Drawzähler220|
| Person der Statusgruppe |1 Ziel, enabled=true, sichtbar; Bounds(421,1640–660,1757)|Unverändert, gleicher Drawzähler222|
| Hub direkt nach Rückkehr |Zielinhalt nicht im zugänglichen Ausschnitt; Scrollbounds(63,293–1017,2211), back=false/forward=true|Weiterhin kein Ziel, gleiche Bounds/Draw320|
| Hubüberschrift |1 Ziel, Bounds(63,784–407,901)|Unverändert, gleicher Draw320|

Gruppenphase feedback=true; Hubphase feedback=false, focus=false, ime=false.
Die Gruppe liegt in diesen Aufnahmen am unteren Viewportrand, nicht im oberen
Bannerbereich. Das beweist nicht die Lage im historischen status-group-Ausfall.
Ein P9-ähnlicher Aktualitätswechsel des beobachteten Zielnodes ist hier nicht
aufgetreten. Für beliebige neu abgefragte Elterninstanzen entsteht daraus keine
allgemeine Aktualitätsgarantie. Im Vorfahren-Kontrollarm wird refresh() tatsächlich
auf jedem neu geholten Clickvorfahren ausgeführt; erfolgreiche Volltests allein
beweisen aber noch keinen vorher vorhandenen Vorfahrenfehler.

**BEOBACHTET:** Die erste reine Hub-Baumabfrage enthält29 Nodes, die nächste bereits
34; Draw bleibt320, ohne Scrollaktion dazwischen. Das geschieht bereits **vor**
dem Paired-Refresh und beweist keine dadurch verursachte Reparatur. Zielinhalt
wird in beiden Abfragen noch nicht im zugänglichen Baum gefunden.

**GEMESSEN, identischer Verlauf in beiden Detailpiloten:**

| Suchversuch (nullbasiert) | Beobachtung |
|---|---|
|0–5|Hubinhalt nicht im Baum; Taskmarker noch y1750–1867; Draw320|
|Nach5|Normale ACTION_SCROLL_FORWARD akzeptiert|
|6|Noch alte Accessibilitygeometrie, Ziel fehlt; Draw bereits322; nächste Scrollaktion abgelehnt|
|7|Ziel bei(126,732–954,928) vorhanden; Taskmarker jetzt y230–282; Draw weiterhin322|

Zwischen6 und7 keine weitere **akzeptierte** Scrollaktion. Damit ist ein
zeitversetzter Accessibility-Geometriewechsel beobachtet, ohne neue Drawzähler-
änderung in diesem Intervall. Das ist **kein** Beweis, dass genau dieser Übergang
die beiden Baseline-Ausfälle verursacht oder dass ein Ziel grundsätzlich
unerreichbar wäre. Der unveränderte Test erreicht es hier regulär.
Die Scrollbounds sind zugängliche Bounds, keine direkte Messung des Compose-
ScrollState oder einer gesamten Inhaltshöhe. Kein Rückgriff auf die widerlegte
Whole-content-Constraints-Hypothese.

**Eng begrenzte zusätzliche Diagnose, keine Navigationkorrektur:** Die erste
Paired-Fassung beobachtet0/5/49 und verpasst damit den gerade relevanten Punkt6.
Die zweite Diagnose-Test-APK erfasst im optionalen Paired-Arm auch jeden weiteren
Hub-Suchversuch ab5 unmittelbar vor einer möglichen Scrollaktion. `none` behält
seinen bisherigen Pfad. Für diese neue Instrumentierung werden eigene gleich große
Detailkontrollen ohne/mit Paired-Refresh verwendet; keine Zusammenlegung mit der
ersten APK. SHA256:
`3057dd6da2303f7034d4138e09a3af8a32e425708761636b9b679fd79b2f5539`.


### 30.6 Getrennte Ein-Variablen-Vergleiche

**GEMESSEN**, erste Diagnose-Test-APK `78551c…`, identisches Ziel und Fixtures:

| Konfiguration | Vollständige Versuche | Ergebnis |
|---|---:|---|
| A: legacy, Phasenlogs, frische Activity direkt Schrift2 |2 initial +8 vorab festgelegte Ergänzung|10/10 PASS|
| B: ausschließlich Baum-Refresh, sonst wie A |2|2/2 PASS|
| C: ausschließlich Clickvorfahren-Refresh, sonst wie A |2|2/2 PASS|
| D: ausschließlich Diagnose aus, legacy/Schrift2 |2|2/2 PASS|
| E: legacy/Phasenlogs, Schrift1→2 in derselben Activity |2 vollständige Doppelfontläufe|2/2 PASS|
| F: legacy/Phasenlogs, frische Activity direkt Schrift1 |2|2/2 PASS|
| Eigener Pilot: legacy/Detaildiagnostik, erstes Paired-Intervall |2|2/2 PASS|

Alle enthalten die ursprüngliche gesamte Funktionsfolge einschließlich
status-group, Hub nach Statusänderung sowie Lock im Detail und Formular.
Keine Appcrashes, Harness-/Assertion-/Erreichbarkeits-/Timeoutfehler in diesen
Kontrollarmen. A wurde nach den kleinen Kontrollarmen ergänzt; der zeitliche
Blockwechsel wird ausdrücklich nicht verschwiegen. Beide A-Blöcke verwenden
dieselbe APK und exakt dieselben Flags. Keine Vermengung mit der alten Baseline,
der zweiten Diagnose-APK oder anderen Schriftkonfigurationen.

D deaktiviert den bestehenden Trace samt Event-/Drawbeobachtern und zusätzlichem
Compose-Wrapper; ProductScreen läuft direkt. Das ist der definierte vorhandene
Observer-off-Modus, keine Behauptung, ein einzelner Eventlistener erkläre den
Fehler. Die zusätzlichen fachlichen Diagnoseassertionen bleiben auch in D bestehen.

**Auswertung:** 8/10 der alten gegenüber10/10 der neuen unveränderten Navigation
sind kein belastbarer Nachweis eines Beobachtereffekts und keine Fehlerfreiheits-
garantie. Neue Modellchecks/Phasenpublications können Timing beeinflussen; die
seltenen Fehler können auch in einer begrenzten Reihe ausbleiben. In B/C fehlt
ein reproduzierbar fehlschlagender gleich instrumentierter Kontrollarm. Deshalb
weder Baum- noch Vorfahren-Refresh als bewiesenen Funktionsfix aktivieren.
Die2er-Piloten für Schrift1 und1→2 ersetzen keine zehnfache Post-Fix-Abnahme.
Es gibt hier bislang keine nachgewiesene Korrektur, die eine solche Abnahme
bereits rechtfertigen würde.


### 30.7 Punktgenaue Refresh-/Vorfahrenkontrollen

**GEMESSEN**, zweite Diagnose-APK `3057dd…`, identisches Ziel:

| Variante, jeweils Schrift2 | Vollständige Versuche | Ergebnis |
|---|---:|---|
| legacy, detailliert, ohne Paired |2|2/2 PASS|
| legacy, detailliert, Paired vor jedem weiteren Hubscroll ab5 |2|2/2 PASS|
| Nur Vorfahren-Refresh, detailliert, ohne Paired |2|2/2 PASS|

Keine Crashes/Harness-/Assertion-/Erreichbarkeits-/Timeoutfehler. Die Daten werden
nicht mit der ersten APK verrechnet. Beide Vorfahrenläufe beobachten jeweils52
Clickvorfahren; kein enabled=false→true, clickable=false→true oder fehlgeschlagenes
refresh() in diesen Aufnahmen. Das widerlegt keinen seltenen anderen Verlauf.

**GEMESSEN, beide Paired-Läufe am zuvor ausgelassenen Suchversuch6:**

| Abfrage vor einer weiteren Scrollaktion | Nodes/Ziel | Scrollaktionen |
|---|---|---|
| Vorhandene Projektion |34 Nodes,0 Ziele|back=false, forward=true|
| Dieselben Instanzen nach refresh() |20/34 noch aktualisierbar,0 Ziele in der alten Liste|back=true, forward=false|
| Neu aufgebauter aktualisierter Baum |32 Nodes,1 sichtbares/enabled Ziel bei(126,732–954,928)|back=true, forward=false|

Bounds des Scrollcontainers unverändert(63,293–1017,2211); kein Click/Scroll
zwischen den drei Abfragen; Draw bleibt je nach Lauf321 bzw.322. Feedback=false,
Viewfocus=false, IME=false, Modell ACTIVE_HUB/OVERVIEW, Workspace vorhanden.
**Nachgewiesen ist eine zeitweise veraltete Accessibilityprojektion:** Eine alte
flach gesammelte Nodeliste wird durch Refresh ihrer überlebenden Elemente nicht
zu einer vollständigen aktuellen Liste. Entfernte/neu zugängliche Kinder erfordern
eine neue Baumabfrage. Das Ziel ist an diesem Punkt regulär vorhanden und nicht
von einem aktiven Banner verdeckt. Die unveränderte Vollprobe hätte hier jedoch
auch ohne prophylaktischen Refresh bestanden. Daher noch **keine bewiesene
Zuordnung der vollständigen Baseline-Ausfälle** zu genau diesem Übergang und keine
abgeleitete Default-/Produktkorrektur. Die damaligen Detailstatusmeldungen enthalten
jeweils einen Mainthread-Modellsnapshot; ihr Timingeffekt bleibt eine Grenze.

### 30.8 Minimale Instrumentierung als weitere Kontrollvariable

Die aktive Diagnose fügt vor der Hubsuche neue Mainthread-Checks/Phasenmeldungen
ein. Um den Originalpfad nicht gerade dadurch vorzeitig zu synchronisieren,
führt `--hub-observation minimal` die ursprüngliche Hubfolge direkt aus; lediglich
feste Unterphasennamen trennen Rückknopf, Überschrift und Inhalt. Keine neuen
HC_A..K-Checks oder proaktiven SG-/HC-Snapshots auf diesem Weg. **Alle ursprünglichen
fachlichen und UI-Assertions bleiben erhalten.** Die zusätzlichen neuen
Diagnoseassertionen laufen weiterhin im getrennten active-Arm.

Nach bereits ausgeschöpfter ursprünglicher50er-Suche wird die letzte erfolglose
Nodeliste paarweise untersucht. Ein danach durch Refresh auffindbares Ziel macht
den vorherigen Fehlversuch ausdrücklich **nicht** nachträglich grün. Keine weitere
Navigation, kein Wiederholen eines Writes, keine längere Suche. Aktuelle
Fensterfokus-/Root-Eigentümerschaft nur als Booleans; kein fremder Paketname/Inhalt.
Im finalen Paired-Sampler werden zunächst alle skalaren Samples gesammelt und
erst anschließend Statusmeldungen/Mainthread-Modellreads ausgeführt. `sampleDraw`
bezieht sich auf den jeweiligen tatsächlichen Samplezeitpunkt. Auch dies friert
Androids asynchrone Semantikpublication nicht atomar ein.

Dritte Diagnose-Test-APK:
`49b08e5bbdca8b8653b5668188b28559e3a80229cda75b543986095d7703feaf`.
Eigene vorab festgelegte Reihe:2 active-Kontrollen und10 minimal-Läufe,
legacy/Phasenlogs/Schrift2, identische APK und Fixtures. Nicht mit älteren APKs
oder prophylaktischem Refresh addieren. Kein Funktionsfix aus einem bloßen
ausbleibenden Fehler ableiten.


**GEMESSEN, dritte APK:**2/2 active-Kontrollen und10/10 minimal-Volltests bestanden,
keine Appcrashes, Harness-/Assertion-/Erreichbarkeits-/Timeoutfehler. Alle
ursprünglichen status-group-/Status-/Hub-/Lockassertionen bestanden. Dieser
Kontrollarm reproduziert den vollständigen alten Fehler somit nicht. Einziger
Wechsel zwischen diesen beiden Armen ist der beschriebene Diagnoseumfang.
Die Zahlen werden nicht mit den vorigen APKs zu einer Gesamterfolgsquote addiert.

**NICHT GETESTET durch einen neuen vollständigen Fehlversuch:** Der nach
Sucherschöpfung vorgesehene Diagnoseeintritt wurde in der dritten APK nicht
benötigt. Seine letzte gespeicherte Nodeliste stammt vor der letzten ursprünglichen
Scrollaktion. Ein erst danach im Refresh auftauchendes Ziel wäre deshalb nicht
allein bereits ein Cacheursachenbeweis; auch letzter Scroll/verspätete Publication
wären getrennt zu prüfen. Die ursprüngliche Failure bleibt in jedem Fall bestehen.

### 30.9 Abschließende Regression und finale Samplerprüfung

**GEMESSEN, dritte/finale Test-APK `49b08e…`:**

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| legacy/Phasenlogs/active, Paired vor weiteren Hubscrolls, Schrift2 |2/2 vollständige Funktionsläufe bestanden|
| Ux3cTasksProbe Schrift2, unveränderte auto→sweep-Steuerung |1/1 vollständiger Lauf bestanden|
| P9MemoryReportProbe Schrift2, bestehender Vorfahrenrefresh |1/1 vollständiger Lauf einschließlich Addendum bestanden|
| Große ListStabilityProbe |ACTIVE/PREPARATION, Schrift1/2/3,5 bestanden; Scrollanker, Semantik, Formulare, Lock, späte SQLCipher-Rückkehr, kein Autoopen nach synthetischem READY|

Dieselben drei UI-Regressionen wurden zuvor mit der ersten Diagnose-APK ebenfalls
jeweils einmal bestanden; getrennte Wiederholung wegen der späteren Änderungen
am gemeinsamen Trace. Keine vermeintlich neue Produktoptimierung daraus ableiten.

Der finale Sampler reproduziert den34→32-Nodes-/0→1-Ziel-Wechsel erneut in beiden
vollständigen Paired-Läufen, teilweise mehrere Suchiterationen hintereinander.
Jetzt keine Mainthread-Statuspublication zwischen den skalaren Samples:
`sampleDraw` bleibt je Lauf442 bzw.499; ownedRoot=true, windowFocus=true,
feedback=false, IME=false; gleiche Ziel-/Scrollbounds wie30.7. Der reguläre
unaktualisierte Baum kann also auch nach einem frischen Kontrollbaum erneut die
alte Projektion liefern. Die ursprüngliche Suche erreicht den Inhalt hier
anschließend trotzdem. Keine automatische Umstellung der Funktionssteuerung.

**GEMESSEN separat, zweite Test-APK, identischer unveränderter Produktstand:**
Bestehender Material3-Footer-Semantikrepro50/50 bestanden, Schrift2 ohne
Accessibility-Automation;0 Crashes. Keine automatische Wiederholung eines
abgebrochenen Trials. Dies ist kein Ersatz für eine Funktionsvollregression.
Viewport-/Lazy-/Footer-/P9-/Aufgaben-Sweep-Fixes bleiben unverändert.

**Erneut ausgeführt:**7 fokussierte Kotlin-Hosttests (ProductViewportTest3,
ProductEntityListTest4),0 Failures/Errors/Skips. Exakter historischer Constraints-
Zahlenfall enthalten. Isolierte Offline-Testbuilds bestanden, Python-Syntaxprüfung
bestanden. Zusätzlich4 hostseitige Runner-Negativkontrollen mit vollständig
simulierten aapt/apksigner/adb: falsche Ziel-ID, falsche Test-ID, abweichende
Zertifikate, fehlgeschlagener Zertifikatsprüfer. Alle vor jedem Mock-ADB-Aufruf
abgelehnt; keine Device-/Produktinstallation in diesen Kontrollen. Der Runner
prüft beide Pakete und Zertifikate vor Installation, Paketprüfung auch bei Python-
Optimierung aktiv. Vor der unveränderten alten Baseline wurden die realen
isolierten APK-Signaturen zusätzlich extern geprüft.

**NICHT ERNEUT GETESTET:** Vollständige1266er-Hostsuite, gesonderte12er-Storage-
Suite, Credential-/Dokument-/Kamera-/SAF-Vollregressionen, reale manuelle TalkBack-
Abnahme und Produktabnahme. Ihre früheren Nachweise bleiben historisch.
Session-Revoke in den UI-Proben ist ein synthetischer Port-/Generationswechsel,
keine automatisierte Eingabe echter Geräte-/Produktcredentials. Keine behauptete
neue Zehner-Post-Fix-Abnahme für jede Schriftkonfiguration: kein Fix freigegeben.
Keine Performance-/Jankverbesserung gemessen oder behauptet.

### 30.10 Root-Cause-Status und nächster kleinster Schritt

**BEOBACHTET/NACHGEWIESEN:** Der Accessibility-Client kann am Hub noch eine alte
Nodeliste/Scrollaktionsprojektion liefern, während ein kontrollierter neu
aufgebauter aktualisierter Baum das sichtbare/enabled Ziel bereits anbietet.
Für diesen beobachteten Übergang keine falschen Domainwerte, kein verbliebener
Operationsfokus, keine IME, kein aktives Modellfeedback und keine fremde aktive
Accessibility-Root in den finalen Samples. Nicht pauschal als Material3-Bug oder
allgemein fehlerfreies TalkBack-Verhalten bewerten.

**BLOCKER bleibt:** Die2 vollständigen Fehler der unveränderten Baseline sind
weiterhin hub-after-change/visible()-Fehler; ihre damalige gemeinsame Fundstelle
trennt Überschrift und Funktionsinhalt nicht. Neue HCL1/HCL2-Kontrollen bestehen,
sie liefern keine rückwirkende eindeutige Zuordnung. Historischer status-group-
Ausfall ebenfalls nicht neu reproduziert. Keine nachgewiesene Produktursache und
keine verlässliche vollständige Kausalkette vom beobachteten Zwischenbaum zum
terminalen Suchfehler. Keine Default-/Produktkorrektur auf Verdacht.

**HYPOTHESEN offen:** verspätete/zwischengespeicherte Accessibilitypublication,
Such-/Scrollgrenze oder Scrollanimation, frühere Overlay-/Ankerwirkung,
Beobachter-/Fenster-Timing. B/C-Piloten und ausbleibende Fehler belegen keine
alleinige Ursache. Das alte `focus=false` war Viewfokus, kein Fensterfokus;
Window-/Root-Zugehörigkeit der alten Fehlversuche wurde nicht erhoben.

**Engster nächster Diagnoseschritt nach unabhängigem Review:** Im minimalen
Originalpfad ausschließlich bereits abgefragte skalare Such-/Scrollmetadaten in
höchstens50 Einträgen sammeln: Versuch, Rootzugehörigkeit, Ziel-/Scrollbounds,
verfügbare Richtung und tatsächlicher Rückgabewert der Scrollaktion. Ausgabe erst
nach dem ohnehin feststehenden Fehlversuch, keine zusätzlichen Mainthread-/
Accessibility-Abfragen oder Statusmeldungen vor dem Suchende. Damit ließen sich
letzte Scrollaktion, Suchrand und unverändert veraltete Projektion trennen, ohne
sie vorab durch Logs/Refresh zu beeinflussen. Dieser zusätzliche Puffer ist hier
**nicht implementiert/getestet**. Erst bei konkretem vollständigem Fehlernachweis
einen einzelnen Harnessfix ableiten und danach die beauftragten10er-Font-/
Schriftwechsel-Vollregressionen durchführen. Kein spekulativer Produktfix erzwingen.

**Verbleibendes Produkt-UX-Risiko:** Die in29.8 belegte vorübergehende Overlay-
Überdeckung bleibt real; für die terminalen Funktionsfehler nicht kausal bewiesen.
Keine Bannerabschaltung, keine geänderte Bestätigungsdauer/-semantik, keine
entfernte Accessibilityinformation. Neue manuelle Screenreader-Abnahme offen.
Issue#26 bleibt verbindliches Gesamt-Release-Gate; keine Stufe2-Freigabe.

### 30.11 Sicherheits-/Gerätestatus

**BEOBACHTET am Diff:** Nur androidTest, Diagnose-Runner/README und Bericht.
Keine Änderung an src/main, SessionCoordinator/180s/30s, StorageAccess/Generation,
SessionDbKeyOwner, SQLCipher-Preflight/Room-/Key-Lifecycle, Tink, Leadership/P3b,
Dokument-/PDF-Isolation, Recovery, Schema11, Permissions, App-ID oder Signatur.
Keine neuen Dependencies, Produktcaches, Netzwerkfunktionen oder produktive
Klartextpersistenz.
Die ursprünglichen Tests/Assertions wurden nicht entfernt/abgeschwächt. Kein
vollständiger unabhängiger Securitynachweis aus den begrenzten UI-Tests behauptet.

Temporär ausschließlich `org.pag.perf.p11probe` und `org.pag.perf.p11probe.test`
installiert; Paket-IDs, Zielpaket der Instrumentation und gleiche verifizierte
APK-Zertifikate geprüft. Die zuvor absichtlich entfernten17 Pakete wurden nicht
pauschal wiederhergestellt. Nach Abschluss sämtlicher Gerätetests beide benötigten
Testpakete per exakter Allowlist wieder entfernt; Produkt-ID technisch ausgeschlossen.
Abschließender PaG-Paketbestand ausschließlich `org.pag.prototype.a`; andere Apps
nicht entfernt. SHA256 der Host-Keystore-Datei unverändert. Keine Produktinstallation,
kein Produkt-Uninstall/pm clear, kein Datenbankreset/-export/-kopieren, keine echten
Credentials/Produktdaten, keine Produktmigration. Nur isolierte synthetische Bestände.

### 30.12 Vollständige Dateiliste und Reviewstatus

- `app/src/androidTest/kotlin/org/pag/prototype/Ux3dFunctionsProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/UiReachabilityTrace.kt`
- `tools/p11semantics/regressions.py`
- `tools/p11semantics/README.md`
- `docs/product-p11-active-workspace-assessment.md`

5 Textdateien. Keine Produktquellen geändert. APKs, Rohstreams, maschinenlesbare
Mess-/Cleanupresultate, temporäre Runner und Mocktools ausschließlich im ignorierten
lokalen Diagnosebereich; keine Keys/Keystores, Credentials, DBs, Produktdaten,
Gerätekennungen, persönlichen Pfade, Logs oder Buildartefakte im Commitumfang.
Keine alten Fehlversuche/Veröffentlichungen oder Git-Historie entfernt/umgeschrieben.
Vorgesehener Abschluss: geprüfter WIP-Commit/Push auf bestehendem Featurebranch,
kein Force-Push/Merge/Release/Produktupdate; danach STOPP/unabhängigen Review abwarten.

## 31. Begrenzte Abschlussdiagnose im ursprünglichen Suchpfad

### 31.1 Auftrag, historische Fehler und feste Versuchszahl

Ausgangsstand `8a8a2c93c733fe0b731a6c9338948cb95d8a3bde`.
Die unveränderte historische Baseline aus Abschnitt30 bleibt **8/10**:
2 vollständige UNREACHABLE-Fehler in hub-after-change; kein Appcrash.
Die zeitweise veraltete Accessibilityprojektion ist konkret belegt, ihre
vollständige Kausalkette zum damaligen Suchabbruch weiterhin nicht nachgewiesen.
Auch der historische status-group-Ausfall ist damit nicht nachträglich erklärt.
Keine dieser Beobachtungen wird durch erfolgreiche neue Wiederholungen gelöscht.

Vor Beginn festgelegt: genau20 vollständige Versuche mit Schriftfaktor2 und
frischer Activity sowie5 vollständige Doppelfontläufe1→2 in derselben Activity.
Keine weiteren Varianten, keine Wiederholung bis zum ersten PASS. Identische
synthetische Fixtures, gleicher eingefrorener Zielstand und dieselbe neue Test-APK
in beiden Armen. Originalnavigation `legacy`, `diagnostics=phases`,
`hubObservation=minimal`, `witness=none`; kein prophylaktischer Refresh.
Die ursprünglichen fachlichen Assertions, Klick-/Schreiboperationen,50 Suchversuche,
Scrollbeginn ab Versuch5 und vorhandenen Wartezeiten bleiben erhalten.

### 31.2 Diagnosepuffer und Aussagegrenzen

**BEOBACHTET am Testdiff:** Ausschließlich `Ux3dFunctionsProbe.visible()` erhält
im Minimalmodus ein festes Array von50 optionalen Strings pro Suche. Jeder Eintrag
enthält ausschließlich skalare Werte: Versuch, feste interne Phase,
Rootzugehörigkeit, Ziel vorhanden/positive Rechteckfläche, Schnitt mit dem bereits
bekannten Scrollrechteck, Ziel-/Scrollbounds, Scrollcontainer vorhanden,
Backward-/Forward-Aktion, Entscheidung WAIT/BACK/FORWARD und zurückgegebenes
Aktionsergebnis beziehungsweise notAttempted. Kein Nodeobjekt wird im Puffer
gehalten; keine Texte oder IDs werden ausgegeben.

Die Geometrie und Aktionsliste stammen ausschließlich aus lokalen Metadaten der
bereits vom ursprünglichen Suchversuch gelieferten Nodes. Keine zusätzliche
UIAutomation-/Root-/Parent-Abfrage, kein Refresh, kein neuer Mainthread-
Synchronisationspunkt und keine Statusausgabe innerhalb der Suchschleife.
`beforeDecision` bezeichnet den Zustand vor der Scrollentscheidung;
`afterDecisionAccepted` den Rückgabewert danach. Es ist **keine** zusätzliche
Messung der tatsächlichen Scrollposition nach der Aktion. Geometrie aus der
nächsten regulären Iteration lässt sich gegebenenfalls vergleichen. Nach der
letzten Aktion gibt es keinen weiteren Suchversuch; ein späterer Treffer wird
nicht abgefragt und kann FAIL niemals in PASS umwandeln.

Erst nach vollständiger Sucherschöpfung wird UNREACHABLE gesetzt, dann werden
höchstens50 Einträge ausgegeben und der ursprüngliche Fehler ausgelöst. Bei Erfolg
wird kein Puffer ausgegeben. Der bisherige nachträgliche Paired-Refresh im
Minimalmodus und das Vorhalten seines letzten Nodebaums entfallen. Aktive separate
Diagnosemodi bleiben unverändert und wurden für diese Abschlussreihe nicht benutzt.
Der Puffer kann das Timing durch begrenzte lokale Getter/Allokationen beeinflussen;
er ist kein nachweislich vollständig wirkungsloser Beobachter.

Ein positives Action-Ergebnis beweist keine bereits abgeschlossene Bewegung;
eine alte Aktionsliste beweist keine physische Scrollgrenze. Cached Bounds beweisen
weder aktuelle Sichtbarkeit noch Aktualität des Accessibilitybaums. Ein Ziel mit
positiver Höhe wird vom ursprünglichen Suchpfad weiterhin unmittelbar akzeptiert;
die neue Diagnostik verschärft oder lockert diese Bedingung nicht.

### 31.3 Builds, Artefakte und begrenzte Prüfungen

**GEMESSEN:** Isolierter Offline-Android-Testbuild bestanden. Eingefrorener Ziel-
APK-SHA256 `e3160b230ab0445fbccd17504e7d6be981bbbe18e50a1e689b874200a91318a2`;
neue Test-APK `66aeef1fdf31fb88c6da7f39cb7f11575ed19bfd98c62217bc9df0c6f8fa1945`.
Vor Installation prüft der bestehende Runner beide exakten isolierten Paket-IDs
und die verifizierten übereinstimmenden Signaturzertifikate. Gerät Samsung
SM-M526BR, Android13/API33. Keine Produkt-APK installiert.

6 fokussierte statische Prüfbedingungen des Puffer-/Suchvertrags bestanden:
50er-Array, keine Statusausgabe in der Schleife, kein letzter gespeicherter
Nodebaum, kein zusätzlicher Refresh/Rootzugriff im Suchcode, unveränderte
Versuchs-/Scroll-/Warteparameter, Reihenfolge Fehlerfeststellung→Ausgabe→Exception.
Dies sind Codeprüfungen, keine dynamischen Tests des erschöpften Fehlerpfads.
Keine allgemeine neue UI-Kampagne und keine erneute vollständige Host-/Storage-
oder Benchmarkreihe. Frühere Tests aus Abschnitt30 bleiben historische Nachweise.

### 31.4 Konkreter vollständiger Fehlversuch und letzter Suchverlauf

**GEMESSEN im Minimalpfad:** Der vierte und siebte Versuch des frischen
Schrift2-Arms scheiterten vollständig in `HC_L2_CONTENT` (nach der bereits
bestandenen Suche `HC_L1_HEADING`). Alle status-group-Unterphasen bestanden in
diesen beiden Läufen. Fehlerkategorie UNREACHABLE, kein Appcrash. Jeweils exakt50
Puffereinträge, identischer folgender skalarer Verlauf (Versuche nullbasiert):

| Versuche | Root des Testpakets | Ziel vorhanden | Scrollbounds | Back / Forward angeboten | Entscheidung | Ergebnis |
|---|---|---|---|---|---|---|
| 0–4 | true | false | 63:293:1017:2211 | false / true | WAIT | nicht versucht |
| 5 | true | false | unverändert | false / true | FORWARD | true |
| 6–7 | true | false | unverändert | false / true | FORWARD | false |
| 8–49 | true | false | unverändert | true / false | FORWARD | false |

Zielbounds stets absent, positive Zielgröße/Schnitt mit Scrollrechteck false;
Scrollcontainer in allen50 Versuchen vorhanden.45 Scrollversuche, davon1 angenommen
und44 abgelehnt. Nach der **letzten** Scrollentscheidung ist das Aktionsergebnis
false; der terminale Fehler beruht hier also nicht auf einem erst im allerletzten
Versuch angenommenen Befehl ohne anschließende Suchchance. Die Puffer werden erst
nach Fehlerfeststellung ausgegeben und verändern die Fehlversuche nicht in PASS.

**BEOBACHTET nach dem feststehenden Fehler:** Das vorhandene äußere Fehlerevent
meldet ACTIVE_HUB, area=HUB, Operations OVERVIEW, Workspace/TaskBoard vorhanden,
kein Personen-/Gruppenfokus, feedback=false, IME=false und windowFocus=true.
Diese nachträgliche Zustandsaufnahme beweist keinen unveränderten Feedbackzustand
während aller vorausgehenden Versuche. Der Minimalarm erhebt absichtlich keine
zusätzlichen HC-Modell-/Kartenmessungen innerhalb der Suche.

**EINGEGRENZT:** Der ursprüngliche Suchpfad fährt nach dem einen angenommenen
Vorwärtsbefehl mit unveränderter Richtung fort, auch nachdem die angebotenen
Aktionen ausschließlich rückwärts lauten. Die Suche findet den erwarteten
Funktionsinhalt weder vor dem Befehl noch in den44 folgenden Suchversuchen.
Eine vorübergehend nicht aktualisierte Scrollaktionsprojektion ist mit den
Versuchen6/7 vereinbar: Vorwärts wird noch angeboten, aber abgelehnt. Ob dies eine
laufende Scrollanimation, gecachte Metadaten oder einen anderen Übergang beschreibt,
wird nicht zusätzlich abgefragt. Die später angebotene Rückwärtsaktion wurde vom
Originalpfad nicht betätigt; dies ist eine dokumentierte Suchgrenze.

**NICHT BEWIESEN:** aktuelle Pixel-/Scrollposition, Erreichbarkeit nach einem
Richtungswechsel, aktueller vollständiger Semantikbaum, Ort/Komposition des
fehlenden Zielelements oder konkrete Produktursache. Die Bounds des Containers
sind kein Inhalts-Scrolloffset. Ohne zusätzliche Refresh-/Renderabfrage lässt sich
nicht entscheiden, ob der Inhalt zwischen Anfang/Ende übersprungen wurde, der
Baum unvollständig/veraltet blieb oder ein echter UI-Erreichbarkeitsfehler besteht.
Der frühere separate Refreshnachweis aus30 erklärt diese beiden terminalen
Fehler nicht automatisch. Keine behauptete alleinige Banner-/Material3-Ursache,
keine nachgewiesene kleinste Harnesskorrektur, kein spekulativer Produktfix.

### 31.5 Abgeschlossene feste Vergleichsreihen

**GEMESSEN, primärer Arm:**20/20 vorab festgelegte Versuche ausgeführt;
**18 PASS,2 UNREACHABLE,0 Appcrash**. Fehler ausschließlich im vierten/siebten
Versuch, jeweils `HC_L2_CONTENT`. Kein neuer status-group-Fehler. Alle10 SG_A–J-
Unterphasen bestanden auch in beiden fehlgeschlagenen Vollversuchen. Die18
bestandenen Vollversuche einschließlich ihrer Lock-Detail-/Lock-Form-Assertions
bleiben von den2 zuvor abgebrochenen Läufen getrennt. Kein nachträgliches Umwerten.
Alle20 Ergebnisdatensätze haben dieselben eingefrorenen APK-SHA256. Nachprüfung
der Rohstreams: erfolgreiche Suchen geben keinen Fehlerpuffer aus, beide Fehler
genau50 Einträge und anschließend weiterhin FAIL. Kein Wiederholungsversuch mit
geänderten Assertions, kein neuer Diagnosearm.


**GEMESSEN, zusätzlicher Arm:**5/5 vorab festgelegte vollständige Doppelfontläufe
1→2 in derselben Activity bestanden,0 Appcrashes. Identische APKs und Kontrollflags
wie im primären Arm; keine Fehlerpuffer in den5 Rohstreams. Dies sind begrenzte
erfolgreiche Wiederholungen einer anderen Konfiguration, keine allgemeine
Fehlerfreiheit und keine Aufhebung der2 neuen oder historischen Fehler.
Die25 Ergebnisdatensätze und zugehörigen Rohstreams wurden abschließend erneut
abgeglichen; keine zusätzlichen Gerätetestversuche ausgeführt.

### 31.6 Abschlussentscheidung und verbleibende Abnahme

Die zeitlich und methodisch begrenzte automatische Fehlersuche endet hier.
Der vollständige intermittierende Fehler ist im Minimalpfad erneut erfasst und
auf die Inhaltssuche nach erfolgreicher Hubüberschrift eingegrenzt. Belegt ist die
unverändert vorwärts gerichtete Suchgrenze bei anschließend ausschließlich
angebotener Rückwärtsaktion. Eine ausreichende Kausalkette zu Zielposition,
Baumaktualität oder einer kleinsten testseitigen Korrektur fehlt weiterhin.
Deshalb keine Korrektur auf Verdacht, keine zusätzliche Variante und keine neue
Regression bis zum ersten grünen Ergebnis. Gesamtstatus weiterhin offen;
keine künstliche GRÜN-Bewertung, keine produktive Stufe2-Freigabe.

Offen bleiben die unabhängige manuelle UI-/Produktabnahme und TalkBack-Abnahme,
insbesondere Funktionsnavigation, Schriftvergrößerung/-wechsel, Scroll-/Fokus-
verhalten und die historisch belegte vorübergehende Feedbacküberdeckung.
Automatische synthetische Accessibilityproben ersetzen diese Abnahme nicht.
Weitere Entscheidungen erst nach unabhängigem GitHub-Review; Issue#26 bleibt
Gesamt-Release-Gate.

### 31.7 Sicherheit, vollständige Dateiliste und Übergabe

Nur testseitige Diagnose und Dokumentation geändert. Produktcode einschließlich
SessionCoordinator und180s/30s-READY-Fristen, StorageAccess/Generation,
SessionDbKeyOwner, SQLCipher-Preflight, Room-/Key-Lifecycle, Tink, Leadership/P3b,
Dokumentisolation und Recovery unverändert. Keine Änderung an App-ID,
Signaturschlüssel, Dependencies oder produktiver Speicherung; kein Cache und
keine Raw-Key-Umstellung. Keine echten Credentials oder Produktdaten verwendet.

Nach Abschluss wurden ausschließlich die beiden für diese Reihe zugelassenen
isolierten Pakete `org.pag.perf.p11probe.test` und `org.pag.perf.p11probe` entfernt;
beide Entfernungen erfolgreich. Vorheriger/nachheriger PaG-Paketbestand geprüft:
anschließend ausschließlich `org.pag.prototype.a` vorhanden. Kein Produktupdate,
kein Produkt-Uninstall/pm clear, keine pauschale Wiederherstellung früher bereinigter
Pakete. Rohbelege und Buildartefakte verbleiben ausschließlich im ignorierten
lokalen Diagnosebereich.

Vollständiger Commitumfang:

- `app/src/androidTest/kotlin/org/pag/prototype/Ux3dFunctionsProbe.kt`
- `tools/p11semantics/README.md`
- `docs/product-p11-active-workspace-assessment.md`

3 Textdateien; keine APKs, Logs, Gerätekennungen, persönlichen Pfade,
Produktdaten, Schlüssel oder Keystores im Commitumfang. Offline-Testbuild bestanden;
Rohstream-/Ergebnisabgleich bestanden. Abschließende Diff-/Statusprüfung und
WIP-Commit/Push auf dem bestehenden Featurebranch vorgesehen, ohne Force-Push,
Merge, Release oder Produktupdate. Anschließend STOPP bis zum unabhängigen Review.


## 32. Vorbereitung der manuellen isolierten P11-UI-Abnahme

### 32.1 Umfang und Wiederverwendung

Basis `9ba2b9cc61027b0531e99faef31e2d4fb235ac0f`, bestehender Branch
`p11-performance-optimization`. Das vorhandene isolierte P11-Zielpaket ist als
Buildgrundlage geeignet. Sein bisheriger Launcher ist jedoch der produktive
Einstieg, den die Instrumentation erst nach Start durch synthetischen Inhalt
ersetzt. Für eine manuelle Bedienung ohne diesen Einstieg wird deshalb ein
explizites Debug-Manifest-/Quelloverlay auf dem vorhandenen P11-Init-Skript
bereitgestellt, keine zweite Testarchitektur oder neuer Produktmodus.

Launchername **PaG P11 UI-Test**, Paket unverändert isoliert
`org.pag.perf.p11probe`. `P11ManualActivity` und `android.app.Application` ersetzen
im manuellen Zielmanifest MainActivity/PagApplication; der PDF-Service entfällt.
Der tatsächliche aktuelle `ProductScreen` und `ProductViewModel` werden direkt
mit synthetischen Ports betrieben. Die bisherigen Fixtures aus Ux3dFunctionsProbe
sind in einen nur explizit eingebundenen gemeinsamen P11-Testquellbereich
verschoben. Automatische Navigation, Assertions und Diagnosepuffer bleiben
unverändert; ihre bisherigen APK-/Messnachweise werden nicht auf neue Builds
übertragen.

Vorbereitet sind Übungsaktion (ACTIVE), Motte mit Support, Klee ohne aktuelle
Funktion und die weitere Funktion Blockierende. Die Operations-Fixture erlaubt
jetzt auch Klee-Änderungen und weist veraltete erwartete Snapshots zurück.
Die manuelle Personenliste verwendet dieselben beiden erfundenen Personen;
der automatische Probe-Standard behält seine historische leere Personenliste.
Bestätigungsbanner und Funktions-/Statusnavigation stammen aus unverändertem
Produktcode. Keine zusätzlichen Beobachter, Refreshs oder Suchsteuerungen.

### 32.2 Manuelle Steuerung und Grenzen

Startbildschirm mit Schrift1/2/3,5, Öffnen/Fortsetzen und Reset. Die Schriftwahl
ändert ausschließlich LocalDensity im Testziel, keine Geräteeinstellung.
Während des Produktbildschirms keine zusätzliche Testtoolbar und kein veränderter
Viewport durch einen Testwrapper. Android-Zurück bietet die Rückkehr zum Teststart;
fachliche Rücknavigation erfolgt über die sichtbaren Produktknöpfe.

Reset verwirft die In-Memory-Ports und räumt das alte Modell über ViewModelStore
auf; neue Daten und neue Hubnavigation für den nächsten Durchlauf. Eine sichtbare
Live-Region meldet die neue Durchlaufnummer. Schriftwechsel am Start erhält
Activity/Modelldaten, öffnet aber die Composition neu; kein unveränderter
Scroll-/Fokuszustand zugesichert. Activity-Neustart/Rotation/Prozesstod setzt die
synthetischen Daten zurück. Die synthetische Session unterstützt im manuellen
Modus rein lokalen Lock/Unlock ohne Plattformauthentifizierung; der automatische
Probe-Standard erlaubt weiterhin keine Authentifizierung. Observer werden beim
Modellabbau freigegeben. Keine Simulation echter Sessionfristen/Sicherheitsabnahme.

Nur der beauftragte Funktionspfad ist als manuelle Probe vorbereitet. Andere
Fachbereiche sind nicht vollständig simuliert; Aufgaben haben ein leeres Board,
Dokumente und sensible Bereiche sind nicht angebunden. Keine Vollproduktabnahme
behauptet. `tools/p11manual/README.md` enthält die kurze Funktionsfolge,
Schrift-/TalkBack-Prüfpunkte, Resetgrenzen und getrennte sichere Build-, Prüf-
und spätere Installationsschritte. TalkBack verwendet die tatsächliche
Compose-Semantik; reale Bedienbarkeit und Banneransage bleiben manuell zu prüfen.

### 32.3 Verifikation ohne Installation

**GEMESSEN:** Manueller isolierter Offline-Debugbuild bestanden;4 fokussierte
Fixture-Hosttests bestanden (beide Personen, Status-/Funktionswechsel, veraltete
Snapshots, frische Ausgangsdaten, synthetischer Unlock und Observerfreigabe
sowie Modell-Einstieg/Workspace/Wiederentsperren).
Ein zunächst gefundener Nullobserver beim Lock nach Subscriptionfreigabe wurde
in der Test-Fixture korrigiert, Tests danach erfolgreich. Bestehende automatische
P11-Assessment-Instrumentation nach gemeinsamer Fixture-Auslagerung offline gebaut;
keine Instrumentation auf einem Gerät gestartet. Keine neue UI-Testkampagne.
Auch die normalen Produkt-/Demo-Manifestprüfungen ohne Testoverlay bestanden.

8 Python-Prüfungen des nur lesenden APK-Gates bestanden: isoliertes Ziel,
Ablehnung der Produkt-ID, falscher Zertifikate, produktiver Application,
Netzwerkberechtigung, zusätzlicher Komponenten, Backup und unexportiertem Launcher.
Der reale eingefrorene manuelle APK wurde ebenfalls geprüft: exakte isolierte
Paketkennung, einzige manuelle Launcher-Activity, Standard-Application,
keine Services/Provider/Receiver/Instrumentation, nur paketlokale AndroidX-
Signaturberechtigung, Backup und Cleartexttraffic aus. `apksigner verify` bestanden,
Zertifikat mit unabhängig bekanntem lokalem Fingerprint abgeglichen.
Der vorhandene lokale Keystore-Dateihash ist gegenüber dem vorherigen Nachweis
unverändert; keine Schlüsselneuerzeugung/-umstellung. Der manuelle Init-Build
bricht bei fehlendem vorhandenem Keystore bereits vor Projektkonfiguration ab.

Lokal eingefrorener manueller APK-SHA256:
`742d53923cb6d1e3a5b6463967f92a42c78bdb504c977d6010acd3ebd972453c`.
APKs und Rohbuildausgaben ausschließlich im ignorierten lokalen Diagnosebereich.
Der APK-Prüfer hat keine Installations-/ADB-Funktion. Vor einer späteren Installation
müssen Paket, Signatur und freigegebener Artefakthash erneut geprüft werden.
Es wurde **nichts installiert**, keine Produkt-App gestartet/aktualisiert/gelöscht,
kein pm clear ausgeführt und kein bereits bereinigtes Testpaket wiederhergestellt.
Keine reale UI-/TalkBack-Abnahme erfolgt; keine GRÜN-Bewertung daraus ableiten.

### 32.4 Schutzgrenzen, Dateiliste und Übergabe

Keine Produktquellen, Produkt-App-ID, SessionCoordinator/180s-/30s-Fristen,
StorageAccess/Generation, SessionDbKeyOwner, SQLCipher-Preflight,
Room-/Key-Lifecycle, Tink, Leadership/P3b, Dokumentisolation oder Recovery geändert.
Keine produktive DB, echten Daten/Credentials, Geräteauthentifizierung,
Netzwerkfunktion oder produktiver RAM-Cache. Keine neue Dependency.

Vollständiger Änderungsumfang:

- `app/src/androidTest/kotlin/org/pag/prototype/Ux3dFunctionsProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/p11fixture/P11FunctionsFixture.kt`
- `tools/p11manual/probe.init.gradle`
- `tools/p11manual/AndroidManifest.xml`
- `tools/p11manual/android/org/pag/prototype/P11ManualActivity.kt`
- `tools/p11manual/test/org/pag/prototype/P11FunctionsFixtureTest.kt`
- `tools/p11manual/verify_apk.py`
- `tools/p11manual/test_verify_apk.py`
- `tools/p11manual/README.md`
- `docs/product-p11-active-workspace-assessment.md`

10 Textdateien. Kein APK, Log, Schlüssel/Keystore, Credential, Produktdatum,
Gerätekennung oder persönlicher Pfad im Commitumfang. Vorgesehene Übergabe:
Diff-/Statusprüfung, WIP-Commit und Push auf dem bestehenden Branch,
Commit-/Remote-SHA unabhängig abgleichen. Kein Merge/Release/Produktupdate.
Anschließend STOPP bis zum unabhängigen GitHub-Review und ausdrücklicher
Installationsfreigabe. Die18/20 und5/5 aus Abschnitt31 bleiben historische,
getrennte Ergebnisse; die2 neuen und2 ursprünglichen vollständigen Fehler bleiben
anerkannt. Ursache und manuelle UI-/TalkBack-/Produktabnahme weiterhin offen.


## 33. Manuelle P11-UI-/TalkBack-Prüfung

### 33.1 Vom Benutzer manuell bestätigt

Ausgangsstand `0a6fc8247e84a8ccc07f5e3fe1482ef3acb3569f`, Branch
`p11-performance-optimization`. Der Benutzer hat die manuelle Prüfung persönlich
mit der isolierten Test-App **PaG P11 UI-Test** auf dem Samsung durchgeführt.
Die folgenden Ergebnisse sind Benutzerbestätigungen:

- Schriftfaktor **1**: **„Noch keine aktuellen Funktionen“** sichtbar und erreichbar.
- Schriftfaktor **2**: **„Noch keine aktuellen Funktionen“** sichtbar und erreichbar.
- Schriftfaktor **3,5**: **„Noch keine aktuellen Funktionen“** sichtbar und erreichbar.
- **TalkBack funktioniert** laut Benutzer im getesteten Kontext.

Für den manuell getesteten Ablauf wurde damit bei Schriftfaktor 1, 2 und 3,5 kein
Erreichbarkeitsproblem festgestellt. Eine separate TalkBack-Prüfung mit jedem der
drei Schriftfaktoren ist nicht bestätigt. Weitere Testfälle, Wiederholungszahlen
oder detailliertere TalkBack-Ergebnisse liegen mit dieser Rückmeldung nicht vor.

### 33.2 Durch Codex automatisiert getestet

Die historischen automatischen Ergebnisse aus Abschnitt 31 bleiben unverändert:
**18/20 bestanden, 2 UNREACHABLE bei HC_L2_CONTENT**. Diese Testreihe erhält
auch nach der erfolgreichen manuellen Prüfung keine rückwirkende GRÜN-Bewertung.
Die dort getrennt dokumentierten Ergebnisse und Fehlernachweise bleiben erhalten.
Die manuellen Benutzerbestätigungen sind keine automatisierten Codex-Ergebnisse.

Für diese Berichtsergänzung wurden keine neuen UI-Tests durchgeführt und keine
APK gebaut oder installiert. Die Prüfung der Änderung beschränkt sich auf
Git-Status und Dokumentationsdiff einschließlich `git diff --check`.

### 33.3 Weiterhin offen

Die erfolgreichen manuellen Prüfungen sind kein allgemeiner Produkt- oder
Accessibility-Fehlerfreiheitsnachweis und ersetzen keine vollständige Sicherheits-
oder Produktabnahme. Eine vollständige Accessibility-Abnahme einschließlich der
nicht separat bestätigten TalkBack-/Schriftkombinationen bleibt offen.
Die Ursache der historischen automatischen Fehler bleibt ungeklärt; die
automatische UI-Ursachensuche bleibt vorerst abgeschlossen. Issue #26 bleibt
Gesamt-Release-Gate; keine Freigabe für produktive Stufe 2.

Ausschließlich dieser Bericht wird ergänzt. Keine Produktcode-, RAM-Cache- oder
Sicherheitsarchitekturänderung, keine Stufe 2-Implementierung und keine Verwendung
echter Produktdaten. Kein Gerätezugriff für diesen Dokumentationsauftrag;
Produkt-App und vorhandene Test-App auf dem Samsung bleiben unangetastet.
Nach regulärem Commit/Push auf dem bestehenden WIP-Branch und Remote-SHA-Abgleich
STOPP. Weitere Arbeit erst nach unabhängigem GitHub-Review; kein Force-Push,
Merge, Release oder Produktupdate.


## 34. Stufe 2 – Architektur- und Sicherheitsentscheidung

Grundlage: `8435c6debdb87a89a576f8eb480f3785a7f806f2` auf
`p11-performance-optimization`. Reiner Code-/Dokumentationsabgleich der Abschnitte
6–14, 19–20 und 33 mit dem aktuellen Produktbestand; keine Implementierung.
**BESTÄTIGT** bedeutet hier durch Quelleninspektion belegt oder ausdrücklich als
historischer Mess-/Benutzernachweis bezeichnet, keine neue Testausführung.
**VORGESCHLAGEN** bezeichnet den Integrationsentwurf; **NICHT GETESTET** dessen
produktive Wirkung; **BENÖTIGT FREIGABE** die ausdrücklich offene Entscheidung.

Quellanker: Product unter `app/src/main/kotlin/org/pag/prototype/product/`,
Domain unter `domain/src/main/kotlin/org/pag/domain/`, Repositories/Stores,
`ActionDatabase.kt`, `SessionDbKeyOwner.kt`, `LeadershipGrant.kt` und Dokument-
Recovery unter `data/src/main/kotlin/org/pag/data/product/`; zentrale Autorität
`data/src/main/kotlin/org/pag/data/SessionCoordinator.kt`, Commitadapter
`data/src/main/kotlin/org/pag/data/prototype/b2/B2Driver.kt`. Die gelesenen
Testverträge liegen unter `tools/p11assessment/shared/org/pag/assessment/`
(`MutationContractProof.kt`, `BudgetContractProof.kt`), ihre Tests unter
`tools/p11assessment/test/org/pag/assessment/` (`MutationContractTest.kt`,
`BudgetContractTest.kt`). Ergänzend Produktziel und bestehende Security-/Threat-
Model-/Privacy-/Architekturvorgaben berücksichtigt.

### 34.1 A – Heute implementierte Schutzmechanismen

**BESTÄTIGT:** `SessionCoordinator` bleibt die zentrale Autorität für READY,
Generation, Umgebung, Ablauf und Publication. `ProductSessionTiming.policy` in
`domain/src/main/kotlin/org/pag/domain/Actions.kt` setzt 180 Sekunden absolut und 30 Sekunden READY-Idle.
Die absolute Frist läuft ab erfolgreicher Authentifizierung, nicht ab dem ersten
Cachehit; nur gültige Benutzerinteraktion erneuert Idle. `acquire`, `check` und
`publish` erzwingen die aktuelle Gültigkeit. `MainActivity` koppelt Vordergrund,
Fensterfokus und Gerätesperre an diese Autorität. Ein gespeichertes READY-Flag
oder ein lokales Ticket ist keine ausreichende Zulassung.

**BESTÄTIGT:** `ProductWorkspaceRepository` liest ausdrücklich angeforderte Teile
über `DocumentNamespaceLane`, `SessionDbKeyOwner`, `ActionDatabaseAccess` und eine
kurze Readtransaktion. Persistierte Action, Status, Generation und optional
`expectedAction` werden geprüft; Operations sind immer enthalten. Inventar,
SQLCipher-/SQLite-Integrität, Fremdschlüssel und Room-Schema werden bei realer
DB-Arbeit geprüft. Room/Driver und Key-Lease werden vor Result-Publication
aufgeräumt. `B2Driver` kontrolliert auch den tatsächlichen Commit und behandelt
verweigerten Commit mit Rollback. Das ist kein allgemeiner Konfliktschutz jedes
Fachwrites und kein Nachweis für eine künftig längere Snapshotlebensdauer.

**BESTÄTIGT:** `ProductViewModel` verwirft Requests über Requestnummern und
`WorkspaceReadTicket`, prüft Action/Status/Generation und leert bei Nicht-READY
oder Generationwechsel synchron über den Sessionbeobachter den Workspace,
Bereichsmodelle, Formulare und sensible Owner. `WorkspaceSnapshot` in `PagShell.kt`
und `WorkspaceRead` in `domain/src/main/kotlin/org/pag/domain/Workspaces.kt` sind vorhandene Readmodelle.
Sie besitzen heute keine vollständige Mutations-/Permissionrevision und keine
Admission vor unbeschränktem Laden. `WorkspaceSnapshot` enthält außerdem optionale
Leadership-/weitere Bereichssummaries; sein vollständiger Inhalt ist daher keine
zulässige Allowlist für den ersten Slice. Kotlin-Listen garantieren keine tiefe
Unveränderlichkeit. `selected` und die Aktionsliste sind weitere bestehende
Referenzhalter, die eine spätere Ownershipprüfung berücksichtigen muss.

### 34.2 B – Veränderte Schutzannahmen

**BENÖTIGT FREIGABE:** Längere gemeinsame RAM-Vorhaltung ist ausdrücklich eine
sicherheitsrelevante **Klasse-B-Änderung**, auch bei Wiederverwendung vorhandener
Snapshots ohne neuen Store. Mehr gleichzeitig identifizierbare Personen-, Rollen-
und Aufgabenbezüge sowie länger lebende Strings vergrößern den Klartextbestand.
Referenzfreigabe garantiert weder Überschreiben aller Runtime-/Compose-/IME-Kopien
noch Schutz gegen privilegierte Speicherabbilder oder ein kompromittiertes OS.

**BESTÄTIGT als Entwurfsfolge:** Ein RAM-Treffer führt keinen neuen DB-Preflight aus.
Ein zwischenzeitlich entstandener Disk-/Inventar-/Schema-/FK-Fehler wird erst bei
nächster tatsächlicher DB-Operation bzw. Unlock-Maintenance erkannt. Lokale
Revisionen erkennen bekannte Appmutationen, keine externe DB-Manipulation. Es gibt
keine neue garantierte Erkennungsfrist in Wanduhrzeit. Das ist separat zu akzeptieren;
unveränderte Preflights bei echten Operationen beseitigen dieses Restrisiko nicht.

| Ereignis | VORGESCHLAGENE zwingende Reaktion; produktive Integration NICHT GETESTET |
|---|---|
| Lock, 180-s-Deadline, 30-s-Idle, Foreground-/Screen-Lock-Verlust | Zentrale Autorität entscheidet; sofort Hits/Publication sperren, Epoch/Tickets widerrufen und alle Scope-/Formularreferenzen synchron lösen; kein Verlängern durch Read/Render/Callback |
| Actionwechsel oder Generationwechsel | Alten Scope vollständig verwerfen; exakt eine Action; keine Übernahme bei Reauth, selbst bei gleicher Action-ID |
| Berechtigungsänderung/-widerruf | Fachrechte neu aus zentral zuständigen Regeln ableiten, betroffene Tokens/Owner invalidieren; fehlt ein zuverlässiger Revisionspfad, kein Hit |
| Storage BLOCKED oder Maintenance/Recovery | Gesamten Scope unzugänglich machen; kein Cache-Fallback über BLOCKED, keine READY-Navigation in MAINTENANCE |
| Prozessende/Ownerende | Keine Persistenz oder SavedState-Wiederherstellung; bewusster neuer autorisierter Read beim Neustart; physische RAM-Löschung nicht zugesichert |
| Verspäteter Read-/Writecallback | Action, Status, Generation, Berechtigungsrevision, Mutations-/Load-Epoch und einmalige Ticketidentität vor atomarer Publication prüfen |
| Fremde Actiondaten/Referenzen | Top-Level-Binding und sämtliche Kind-/Relation-Action-IDs sowie Alias-/Ownershipgrenzen prüfen; keine Vermischung über Formular-/Projektionskopien |

Ein lokaler Treffer schafft **keine Berechtigung**. Jeder Treffer benötigt die
bestehende READY-Storage-Autorität und finale Publication; lokale Revisionen sind
nur zusätzliche Invalidierung. Keine eigene Sessionverwaltung oder eigene Timer.
Aufwendige Projektion/Kopien außerhalb des Coordinator-Monitors, dort nur kurzer
Gültigkeitscheck und atomarer Referenztausch.

### 34.3 C/D – Kleinster Slice und erforderliche Codeänderungen

**VORGESCHLAGEN:** Zunächst nur die bewusst geöffnete **ACTIVE**-Aktion:
Aktionsstammdaten, Personenübersicht/Pseudonyme, Operations/Funktionen und
Aufgabenübersicht einschließlich der für unveränderte TaskBoard-Semantik nötigen
Events, unter gemeinsamer Admission. Keine zusätzliche Eventhistorisierung.
PREPARATION/COMPLETED-Wiederverwendung erst separat integrieren; ihre heutigen
fachlichen Read-/Write-Regeln bleiben bestehen. Auch eine Actionbeschreibung
benötigt wegen fehlendem allgemeinem Längenmaximum Admission.

Vor einem zusätzlichen Store ist die Erweiterung des bestehenden instanzlokalen
Workspace-Owners im `ProductViewModel` vorzuziehen: genau ein zugelassener,
versionierter Kernsnapshot, Bereichssichten referenzieren ihn. `WorkspaceRead` und
Operations/Tasks wiederverwenden; keine parallele zweite Payloadregistry.
`navigateActive`/`clearPersons` leeren heute bewusst viel Zustand und lesen erneut;
sie dürfen nicht lediglich durch Entfernen des Clear-/Loadaufrufs geändert werden.
**NICHT GETESTET:** Ob dies ohne separaten Owner ausreichend wartbar bleibt.
Ein eigener Store wäre erst nach belegter Ownershipnotwendigkeit zu entscheiden.

Erforderliche spätere Änderungen, derzeit sämtlich **VORGESCHLAGEN**:

1. Domain: explizite Kern-Allowlist, Binding mit Status/Generation/Permissionrevision,
   Projektionsversionen und Load-/Mutationsepoch; Zustände NOT_LOADED/LOADING/
   CONTENT/EMPTY/ERROR/BUDGET_REFUSED. Actiondaten konsistent im Read zurückgeben;
   heutiges `WorkspaceRead` enthält nur Action-ID/Status, nicht die ganze Action.
2. Data/Composition Root: rein metadatenhaltige, serialisierte Mutationserfassung
   vor Queue/Sideeffect an allen unten genannten Wegen; Commit-Ausgang, vollständiges
   Cleanup und erlaubte Publication getrennt melden. Keine Commitautorität in Compose.
3. Data: vorhandenen Workspace-Reader/DAOs um Count-/Text-/Relationsadmission und
   begrenzte Materialisierung in derselben autorisierten Readtransaktion ergänzen.
   `OperationsStore.board` lädt heute Rollen, Personen, Pläne/Zustände vollständig;
   `TaskStore.board` lädt komplette Tasks und Events. Nachträgliches Prüfen genügt nicht.
4. Product: Clear für sensible Detailowner/Drafts vom Revoke des Kernworkspaces
   trennen; alle bisherigen P3b-/Viewer-/Dirtyregeln erhalten. Gültige Kernhits durch
   StorageAccess zulassen, keine neuen Reads für abgedeckte Navigation. Versionierte
   Projektions-/Formulartokens, Single-flight-Reload, begrenzte Kopien und synchrone
   Revokeanbindung ergänzen; Actionliste/selected nicht als zweite Wahrheit verwenden.
5. Zunächst konservativ vollständigen Kern dirty setzen und einmal autorisiert
   neu lesen, statt unvollständige Projektionspatches einzuführen. Personrename und
   parallele/stale Editoren vor Freigabe ausdrücklich lösen. Kein Writeport wird
   durch diesen Auftrag geändert.

Der ACTIVE-Hub fordert heute auch Protokoll-, Material-, Kosten-, Memory- und
Dokumentsummaries an. Der erste Slice darf diese nicht stillschweigend langfristig
mitführen. Entweder verbleiben diese Teilreads bei Hub-Rückkehr im bisherigen
Bedarfspfad oder erhalten erst nach separater Entscheidung eine Wiederverwendung.
Unbekannte Counts niemals als null/leer oder als frisch darstellen.

### 34.4 D – Abgleich sämtlicher produktiver Mutationsfamilien mit Abschnitt 19

**BESTÄTIGT:** Die folgenden Einstiege existieren im aktuellen Data-Code; ein
zentraler produktiver `ActionMutationObserver`/Permissionrevisionsvertrag fehlt.
UI-Callbacks wie `loadWorkspace` nach Erfolg sind kein vollständiger Ersatz:
Commit kann erfolgreich sein, obwohl sein Callback später nicht publizieren darf.
Die Invalidierungen der Tabelle sind **VORGESCHLAGEN**, nicht bereits integriert.

| Produktiver Writepfad | Heutige Grenze und fehlender Integrationspunkt |
|---|---|
| `ProductActionRepository.createAction`, `updateAction`/`changeAction`, `advanceActionStatus`/`advanceAction` | Create betrifft Aktionsliste, nicht automatischen Scopewechsel; Update prüft Expected-Action, Transition Expected-Status. Gesamten betroffenen Scope/Rechte/Editoren vor Mutation invalidieren; Status kann Operations initialisieren. Auch Create benötigt Outcome-Erfassung am wirklichen Commit, nicht erst UI-Callback |
| `ProductPersonRepository.createPerson`, `updatePerson`/`changePerson` | Frische Existenz-/Status-/Duplicateprüfung, **kein universeller Expected-Person-CAS**. P/O/T-Zähler und Labels sowie spätere Material-/Protokollreferenzprojektionen invalidieren. Mindestens ein Editowner, Tokenentwertung und frischer Reload; parallele Writer/Rename-/Rollenplan-Konflikte müssen zusätzlich geschlossen werden |
| `ProductOperationsRepository.createRole`, `renameRole`, `deleteRole`, `planRoles`, `updateSnapshot` | Originalstatus-/Referenzregeln erhalten; Rollenlöschung in-use abgelehnt. Snapshotwechsel vergleicht Expected-OperationalState; Rename/Plan haben keinen universellen CAS. Operations, Aufgabenassignees/-labels, Counts und Formulare invalidieren; Status kann Funktion/Ort entfernen |
| `ProductTaskRepository.create`, `edit`, `transition`, `delete` | Task+CREATED bzw. Transition+Event atomar; edit/transition/delete prüfen Expected-Task, Delete nur PREPARATION. Tasks/Events, Personen-Aufgabenzähler und Material-Taskoptionen invalidieren; kein erneutes Event durch Callback-Retry |
| `ProductMaterialRepository.create/edit/delete` | Expected-Position bei edit/delete; Personen-/Taskreferenzen bleiben actionlokal. Material/Hub/Formularoptionen invalidieren, Kern nur bei tatsächlicher Abhängigkeit; keine implizite Task-/Personenmutation erfinden |
| `ProductExpenseRepository.create/edit/delete` | Expected-Position einschließlich Payments, Betrags-/Paymentregeln bleiben Data; Kosten/Summen/Hub invalidieren, auch erlaubte COMPLETED-Ausnahme erweitert keine Kernrechte |
| `ProductProtocolRepository.createEntry/reviseEntry/markMistaken` | Entry/Revision/Relationen atomar, bestehende Revisionserwartung und append-only-Regeln erhalten. Header/Summary und gezielten Detailowner invalidieren; kein Volltextpatch im Kern |
| `ProductMemoryReportRepository.create/saveSection/complete/addAddendum` | Persistierte Headerrevision/Conflictregeln erhalten; Header/Counts/Detaileditor invalidieren. Keine doppelte Section-/Nachtragsmutation durch Refresh; kein allgemeiner öffentlicher Report-Delete-Port |
| `ProductDocumentRepository.importDocument/deleteDocument` | Pending über gesamte Staging-/Publication-/Deleteoperation, nicht nur einzelne DB-Commits. Import erst nach vollständiger Publication fertig; Delete widerruft betroffenen Viewerrequest. Originale Namespace-Lane/Journale/Recovery behalten; Fehlerzustand nicht als Cache-Miss behandeln |
| `DocumentReconciliation.run`, `reconcileProductDocuments`, Maintenancebarrier | Gesamten Scope sperren/verwerfen; Recoverywrites/Subcommits erzeugen keinen READY-Cache. Neue Generation lädt bewusst neu; kein zweiter Dateisystemlock |
| `ProductLeadershipRepository.setup/save/deleteArea/deletePerson/deleteAction`, `revoke`/Grantablauf | Separater Schutz-/Grant-/Inventoryowner, keine Payload im Kern. Permission-/sichtbare Statusänderung zuverlässig melden oder Scope konservativ verwerfen. `deletePerson` entfernt sensible Areas, keine P3a-Person; `deleteAction` Leitungsschutz, keine ActionRow |
| Zukünftige allgemeine Personen-/Aktions-/Retentionlöschung | Heute kein allgemeiner öffentlicher P3a-Person-/Action-Delete-Port; keine vorhandene Garantie behaupten. Vor Einführung sämtliche Scope-/Referenz-/Recoveryhooks ergänzen |

**VORGESCHLAGEN:** BEGIN vor möglichem Sideeffect entwertet alte Reads/Hits.
Definitiver Rollback/fachliche Ablehnung liefert keinen Erfolg und führt zu einem
kontrollierten Reload; fachliches STALE/INVALID ist keine automatische Korruption.
Commit vor Lock, Publication nach Lock: DB-Wahrheit bleibt, aber weder gespeicherter
UI-Erfolg noch Snapshotpatch; nach neuer Freigabe neu lesen. Lock vor Commit muss
am bestehenden Commitgate ablehnen/rollbacken. Bei unbekanntem Commit-Ausgang oder
Cleanupfehler Kernscope unbrauchbar machen, ursprünglichen BLOCKED-/Recoveryvertrag
beachten, keinen automatischen Write-Retry. Alte/doppelte Callbacks dürfen weder
neuen Scope noch neueren Reload überschreiben.

**BESTÄTIGT:** `MutationContractProof` und seine 13 Testmethoden modellieren nur
Metadata, Fake-StorageAccess und injiziertes Binding. Sie prüfen u.a. Pending,
Coalescing, Teilpatchablehnung und Revoke zwischen Commit/Publication. Sie beweisen
keinen SQLCipher-Commit, keine echte Permissionrevision und keine Einbindung aller
Writer. Konkrete Modellgrenze: UNKNOWN leert `clean`, verwirft aber nicht selbst
Binding/Scope und erlaubt später `startRead`; die strengere produktive
Unknown-/Recoveryentscheidung aus Abschnitt 19 benötigt einen Adapter und eigene
Tests. Der Proof ist keine unverändert übernehmbare Produktionsimplementierung.

### 34.5 D/E – Speicherbudget und Nachweisplan

**VORGESCHLAGEN / BENÖTIGT FREIGABE:** 8 MiB konservativ belasteter retained
Domainbestand einschließlich Indizes/Listen/Maps/Strings; maximal **eine** separat
auf ebenfalls höchstens 8 MiB begrenzte Übergangsrepräsentation. Alte und neue
Snapshots plus Builder-/Row-/Projektionskopien dürfen nicht ungezählt parallel
leben. Formulare und Compose brauchen eigene begrenzte Kopier-/Ownershipregeln;
das ist keine harte ART-Heap-, Native-, Gesamt-App- oder OOM-Garantie.

**BESTÄTIGT:** Personen-, Aufgaben-/Event- und Rollenzahlen sind nicht global
begrenzt; auch lange Action-/Pseudonym-/Rollentexte bleiben ein Admissionproblem.
`BudgetContractProof` und sieben Tests prüfen beispielhafte 128-/64-/8-/4-Byte-
Charges, Boundary, sticky Refusal, negative Aggregate, Unicode und Long-Overflow.
Sie messen weder reale ART-Objektgrößen noch einen produktiven DAO-Reader.
SQL-Codepoints, UTF16-Einheiten und UTF8-Bytes müssen explizit unterschieden werden;
supplementary Unicode, String-/Arrayheader, Alignment, Hashkapazität, Referenzen,
Relationen und doppelte Repräsentationen zählen. Add/Mul und SQL-Aggregatgrenzen
müssen Überlauf vor Allokation kontrollieren; keine Deduplikationsannahme ohne Beleg.

**NICHT GETESTET / BLOCKER:** Admission muss vor vollständigen Strings/Rowlisten
in derselben konsistenten Transaktion erfolgen, danach bounded batches mit
Nachprüfung und Storagegates. Die heutigen vollständigen `list`-/`events`-/Board-
Reads erfüllen dies nicht. BUDGET_REFUSED beendet geordnet ohne Teilerfolg,
Retryloop oder automatische Budgeterhöhung. Ein bisheriger unbeschränkter Readport
ist kein zulässiger automatischer Fallback; ohne begrenzten Bedarfspfad bleibt der
Teil kontrolliert nicht verfügbar. Ungültige Rows/negative Aggregate/DB-Fehler
bleiben Validierungs-/Storagefehler, gültige Übergröße/Chargeoverflow Budgetfälle.

Vor **jeder produktiven Freigabe** sind mit echter Integration erforderlich
(**NICHT GETESTET** für diesen Slice):

- Revoke-/Hit-/Publicationtests am echten Coordinator: 180 s trotz Bedienung,
  30 s Idle, Lock, Foreground/Screen-Lock, BLOCKED, Maintenance, Generation,
  Action-/Permissionwechsel; keine fremden Actiondaten und keine Wiederbelebung
  durch alte/doppelte Callbacks, Owner-/Prozessende oder SavedState.
- Reale SQLCipher-Commit-/Rollback-/Cleanup-/Unknown-Rennen für jede Tabellenzeile
  aus 34.4; Commit erfolgreich/Publication verweigert, Lock während Write,
  Single-flight-Reload, Personrename-/Parallelformular-Konflikte, TaskEvents
  genau einmal, Status-/Referenzinvalidierung und Dokument-Recoverygrenzen.
- Admission vor Materialisierung bei sehr großen Mengen und Einzelwerten,
  Unicode-/Long-/SQL-Overflow, Aliaskopien, Übergangsbestand/Formularkopien,
  kontrollierte Verweigerung ohne unbeschränkten Fallback; Kalibrierung der
  Charges, GC-/Allokationsstress sowie Persistenz-/Log-/Leakprüfung.
- Bestehende Security-/Domain-/Workspace-/Viewerregressionen und spätere
  synthetische Content-OnDraw-/DB-Zugriffsnachweise; UI-/TalkBack-/Fokus-/Scroll-
  Abnahme des tatsächlich integrierten Slices. Die manuelle Bestätigung in
  Abschnitt 33 gilt nur für den damaligen Ablauf, nicht für Stufe 2.

### 34.6 Performance – vorhandene Messungen und offene Wirkung

**BESTÄTIGT, historische synthetische Messung aus Abschnitt 23:** Median/P95 bis
Content-OnDraw in ms; B hält den ersten Snapshot, C lädt zusätzlich Boards vor.
Keine neue Gerätemessung für diesen Auftrag.

| Typical, Navigation | A Produktpfad | B Snapshot | C vorbereitet |
|---|---:|---:|---:|
| Personen | 1211,22 / 1246,21 | 20,28 / 46,04 | 20,06 / 44,18 |
| Aufgaben | 1211,60 / 1230,85 | 17,75 / 27,23 | 17,58 / 26,29 |
| Funktionen | 1204,33 / 1213,37 | 22,13 / 27,73 | 19,16 / 27,33 |
| Hub zurück | 1214,07 / 1222,27 | 16,81 / 27,20 | 23,08 / 30,41 |

**VORGESCHLAGEN, erwarteter Nutzen:** Der erste Slice entspricht eher B als dem
breiteren C. Abgedeckte Wechsel Personen ↔ Aufgaben ↔ Funktionen und deren
Kernprojektionen könnten ohne erneuten DB-Read unter 100–150 ms liegen. Das ist
kein gemessener produktiver Slice. Hub-Rückkehr kann wegen ausgeschlossener
Summaries weiterhin DB benötigen; die B-/C-Hubwerte sind dafür keine Zusage.
Aktionsliste/initiales Laden, Writes/Reloads, Personen-Detail/Leadershipinventory,
Material/Kosten, Protokolle/Memory und Dokumentpfade bleiben DB-/bedarfsgesteuert.
Taskformulare nutzen schon heute vorhandenen RAM und liefern keinen zusätzlichen
Storegewinn. Initiale Öffnung typical B bleibt 1219,90 / 1253,94 ms.

Abschnitt 15 zeigt historisch auch Task-P95 über 150 ms (typical B 195,14 ms);
Abschnitt 23 enthält spätere UI-Optimierungswerte. Diese Reihen nicht zu einer
Produktgarantie vermischen. Synthetische Navigation nutzt synthetische Gates und
belegt keine echten Gerätecredentials/Deadlines. **NICHT GETESTET:** End-to-End-
Latenz mit real integrierter Admission, Invalidierung, maximalen Feldlängen und
allen zentralen Sicherheitsgates. Das Ziel rechtfertigt keine Gateumgehung.

### 34.7 F/G/H – Freigabe, Ausschlüsse und technische BLOCKER

**BENÖTIGT FREIGABE:** Benutzer muss vor produktiver Umsetzung ausdrücklich
akzeptieren: längeren/größeren gleichzeitigen Klartextbestand des engen Slices,
spätere Integritätsfehlererkennung auf RAM-Hits, fehlende physische Wipegarantie,
8-MiB-Engineeringbudget plus begrenzten Übergang sowie mögliche kontrollierte
Nichtverfügbarkeit großer Bereiche und DB-Latenz nach Invalidierung. Kategorien,
Scope-Lebensdauer und Konflikt-/Freshnessvertrag sind Teil dieser Entscheidung.
Dieser Architekturauftrag erteilt diese Freigabe nicht.

**VORGESCHLAGEN ausgeschlossen:** Material und Kosten zunächst nur spätere,
separat freizugebende Erweiterungen. Ebenso keine neue längere Vorhaltung von
Protokoll-/Memory-/Dokumentheadern oder deren Summaries im Kern. Keine P3b-Inhalte,
Leadership-Inventare/-Grants/Opened, Schlüssel/Credentials, Dokumentinhalte,
PDF-/Bild-/Textseiten, Protokollvolltexte/-revisionen oder Gedächtnisprotokoll-
Antworten/Nachträge. Keine Aufnahme von DAO/Room/Connection/Lease/Capability.
Bestehende bedarfsorientierte Detailowner bleiben zuständig.

**Technische BLOCKER:** fehlende produktive Mutation-/Outcome-/Permissionhooks;
ungelöste stale-/parallele Editorgrenzen ohne universellen CAS; fehlende bounded
Reader und vorkalkulierte Admission; fehlender belegter Ownership-/Revokevertrag
für längere Snapshotnutzung; sämtliche Integrationsnachweise aus 34.5 und
End-to-End-Performance offen. Unabhängiger Review und Issue #26 bleiben Gates.
Historisch **18/20 bestanden, 2 UNREACHABLE bei HC_L2_CONTENT** bleiben unverändert;
keine rückwirkende GRÜN-Bewertung und keine Wiederaufnahme der automatischen
UI-Ursachensuche. Abschnitt 33 bestätigt manuell Erreichbarkeit bei 1/2/3,5 und
TalkBack im getesteten Kontext, keine separate TalkBack-Abnahme je Schriftfaktor
und keine allgemeine Produkt-/Accessibility-/Sicherheitsabnahme.

Für diesen Auftrag nur Berichtänderung; keine Produktdatei, kein neuer RAM-Store,
keine Tests/Builds/Benchmarks/Installationen und kein Gerätezugriff. SQLCipher-
Parameter, Raw-Key-Vertrag, Preflights realer DB-Operationen, Sessiondauer,
P3b/Leadership, Tink, Dokument-/PDF-Isolation, Recovery, Keystore, App-ID und
Signatur unverändert. Produkt-App und manuelle Test-App bleiben unangetastet.
Nach geprüftem regulärem Commit/Push und Remote-SHA-Abgleich STOPP bis zum
unabhängigen GitHub-Review; kein Force-Push, Merge, Release oder Produktupdate.


## 35. Stufe 2A-1 – Gültigkeits- und Budgetgrundlagen

### 35.1 Auftrag, Basis und tatsächlich implementierter Umfang

**IMPLEMENTIERT:** Basis und GitHub-Branch vor Änderungen unabhängig abgeglichen:
`c50da043826247e49709c44cd9f6e466c0104d68`,
`p11-performance-optimization`. Der Benutzer hat für den späteren engen ACTIVE-
Slice den größeren/länger lebenden RAM-Klartextbestand, spätere Erkennung neuer
DB-Fehler auf RAM-Hits und fehlende physische Wipegarantie ausdrücklich akzeptiert.
Performance bleibt Produktanforderung; diese Zustimmung hebt keine zentralen
Gates, Budget- oder Integrationsnachweise auf. Dieser Auftrag setzt ausschließlich
kompilierbare Sicherheitsgrundlagen um, keine Snapshot-Wiederverwendung.

Neue interne Klassen im bestehenden Product-Paket:

- `ActiveWorkspaceValidity`: instanzlokale Metadaten für genau eine ACTIVE-Bindung
  (Action-ID/Status, READY-Generation, Berechtigungsrevision), Mutations-/Load-Epoch,
  fünf Projektionsgruppen ACTION/PEOPLE/OPERATIONS/TASKS/TASK_COUNTS, Pending/Dirty,
  einmalige identitätsgebundene Read-/Mutationstickets und terminaler Revoke.
- `ActiveWorkspaceBudget`: getrennte mengenbasierte Admission mit ADMITTED,
  BUDGET_REFUSED und INVALID_DATA sowie maximal einem gehaltenen und einem
  separat begrenzten Übergangsbestand. Opaque Repräsentationstickets; veränderliche
  Buchhaltung bleibt privat, Inspektion liefert nur Metadatenkopien.

Beide Klassen halten keine Domain-Payload, DB-/Room-/DAO-/Connectionobjekte,
Keys, Credentials, Dokumente, P3b-Inhalte oder Grants. Keine Persistenz, globale
Registry, zweite Session, eigene Uhr oder neue Dependency. Sie sind auf den
Erzeugerthread beschränkt; Workerresultate müssen später dorthin dispatcht werden.

**NICHT INTEGRIERT:** Keine Aufrufstelle in ProductViewModel/Runtime, Navigation,
WorkspaceRepository oder Data-Writern. `navigateActive`, `loadWorkspace`, Clears,
`WorkspaceSnapshot`, `WorkspaceRead`, Reader und Writer unverändert. Kein Payload-
Store, RAM-Hit, optimistisches Speichern, Write-behind oder Hintergrundvorhalten.
Das heutige Produktverhalten und seine DB-Zugriffe bleiben bestehen. Keine neue
Performanceverbesserung gemessen oder behauptet.

### 35.2 Gültigkeits-, Commit- und UNKNOWN-/Recoveryvertrag

**IMPLEMENTIERT:** Die Bindung ist nach Erzeugung unveränderlich. Eine Änderung
von Action, Generation oder Berechtigungsrevision bzw. ein fehlender aktueller
Scope beendet den Owner. Nicht-ACTIVE-Bindungen sind unzulässig; der künftige
Adapter muss bei Statusverlust null liefern und synchron widerrufen.
`StorageAccess.acquire(READY)`, Context-Scope/Generation und OPERATION werden bei
Zulassung geprüft; jede Publication liegt zusätzlich im originalen
`StorageContext.publish`. Dort werden Bindung, beide Epochs und Pending erneut
geprüft. Ein lokal gültiges Ticket reicht niemals. Kein Fristupdate durch Checks.

BEGIN ist vom zukünftigen vertrauenswürdigen Data-Adapter nach ursprünglicher
Syntax-/Scopezulassung und **vor Queue/erstem Sideeffect** aufzurufen. Betroffene
Gruppen werden sofort dirty, alte Reads/Patches ungültig. Es gibt höchstens eine
Pendingmutation und einen Read; identische Readanforderungen coalescen, abweichende
parallele Anforderungen werden ohne automatische Queue abgewiesen. Readbeginn
entwertet alte Patches und die angeforderten Projektionen. Epoch-Inkremente sind
mit `Math.incrementExact` gegen Überlauf geschützt; Überlauf beendet den Owner.

COMMITTED ist nur eine vertrauenswürdige Data-Outcomemeldung, keine Commit-
Autorisierung und keine UI-Erfolgsmeldung. Erst bestätigter Commit **und** COMPLETE-
Cleanup ermöglichen einen vollständigen, einmaligen Projektionspatch unter dem
finalen Gate. ROLLED_BACK bleibt für betroffene Teile dirty bis zu einem frischen,
autorisierten Reload. Teil-/doppelte/verspätete Resultate und fremde Tickets werden
abgewiesen; Completionversuche verbrauchen ihr gültiges Ticket auch bei Ablehnung.
Während der Publication sind neue Reads/Mutationen gesperrt. Callbackfehler
(einschließlich `Error`) setzen terminal RECOVERY_REQUIRED und werden weitergereicht.
Publication darf später nur vorbereitete Referenzen tauschen, keine IO/Projektionen
berechnen oder Lifecycleänderungen auslösen. Es gibt keinen zusätzlichen Monitor.

**IMPLEMENTIERT:** UNKNOWN sowie FAILED-Cleanup eines aktuellen Reads/Writes
setzen terminal **RECOVERY_REQUIRED**, entfernen alle gültigen Tickets und
Cleanmarkierungen. Derselbe Owner erlaubt danach weder Hits noch neue Reads,
Writes oder Publication; auch Revoke/Reauth hebt diesen Zustand nicht auf. Kein
Reset, Rebind oder automatischer Write-Retry. Dies schließt ausdrücklich die
bekannte Lücke des historischen `MutationContractProof`, der nach UNKNOWN später
wieder einen Read beginnen kann. Der historische Proof bleibt unverändert und
wird weiter separat getestet; er ist keine produktive Outcomeintegration.

**NICHT INTEGRIERT / BLOCKER:** Der künftige Lifecycle-/Data-Adapter muss bei Lock,
Ablauf, Foreground-/Screen-Lock-Verlust, BLOCKED, Action-/Generation-/Permission-
wechsel synchron `revoke` ausführen und Payload-/Formularreferenzen lösen.
Die Klasse prüft zusätzlich die zentrale Autorität bei jeder Zulassung; sie
abonniert selbst keine zweite Session. Nach UNKNOWN darf ein neuer Owner nicht
als lokaler Reset erzeugt werden: ursprüngliche Storage-/Maintenance-/Recovery-
Entscheidung und neuer bewusster autorisierter Load sind vorher nachzuweisen.
Diese übergreifende Lifecyclepflicht wird nicht durch eine globale Registry ersetzt.

Die heutigen Repositorycallbacks unterscheiden nicht überall bestätigten Commit,
Cleanup und widerrufene Publication. `ActionDatabaseAccess.use` prüft nach Cleanup
noch die Publication; ein verweigerter Rückgabepfad beweist deshalb keinen Rollback.
Der Commitadapter `B2Driver` bleibt zuständig für echtes Commitgate/Rollback.
Lock vor Commit darf ihn nicht umgehen; Commit vor Lock bleibt DB-Wahrheit,
auch wenn danach kein UI-Erfolg mehr erlaubt ist. Die neuen Tests beweisen diese
Metadaten-/Coordinatorentscheidungen, keine neu angeschlossene SQLCiphertransaktion.

### 35.3 Admissionannahmen und verbleibende Readerblocker

**IMPLEMENTIERT:** Je Repräsentation fest 8 MiB Engineeringbudget; höchstens ein
gehaltener und ein separat auf 8 MiB begrenzter Übergangsbestand pro Ledger.
Kein stiller Resize/Reset. Erst ein erfolgreich versiegelter Übergangsbestand
kann die alte Buchhaltung ersetzen; alte Tickets werden dabei ungültig. Ein
verweigerter Übergang kann weder versiegelt/promoviert noch durch weitere
Übergänge umgangen werden. `close` ist terminal. Das künftige Payload-Ownership
muss alte Referenzen tatsächlich lösen, bevor ein weiterer Übergang startet;
Buchhaltung allein kann externe Runtime-/Formularkopien nicht freigeben.

`Units` erfasst disjunkte Objekt-, String-/UTF16-, Array-/Byte-, Listen-/Kapazitäts-,
Map-/Kapazitäts-, Index-/Kapazitäts- und Referenzzahlen. Konservative Charges:
128 Byte/DTO, 71/String inkl. Header-/Alignmentreserve plus 4/UTF16-Einheit,
71/Array plus Payloadbytes, 128/Liste plus 8/Kapazität, 128/Map oder Index plus
128/Kapazität sowie 8/zusätzlicher Referenz. Doppelte Repräsentationen werden
separat gezählt; keine Deduplikationsannahme. Kapazitäten sind obere Grenzen der
Allokation, keine bloßen Listengrößen. Sämtliche Add/Mul erfolgen checked in Long.
Negative oder strukturell widersprüchliche Aggregate sind INVALID_DATA, gültige
Übergröße/Chargeoverflow BUDGET_REFUSED. Verweigerung ist sticky, ungültige Daten
werden auch nach Budgetverweigerung ausdrücklich als INVALID_DATA erkannt.

**BLOCKER:** Diese Gewichte sind noch nicht für reale DTO-/ART-Graphen kalibriert;
sie sind keine harte ART-/Native-/App-Heapgrenze und keine OOM-Garantie. Admission
ist keine Fach-/Storagefreigabe. Sie muss vor der nächsten Materialisierung
aufgerufen werden; ein erfolgreiches Seal beweist allein weder vollständigen
Inhalt noch sichere Reader-/Cleanupintegration. Formulare/Compose und temporäre
Row-/Builder-/Sortierkopien brauchen explizite begrenzte Ownership-/Chargeregeln.

Konkrete Voraussetzungen für Readerarbeit in **2A-2**, keine aktuelle Umsetzung:

1. In derselben kurzen autorisierten Readtransaktion zuerst frischen Actionstatus,
   Counts/Relationen und begrenzte Textaggregate prüfen. Auch IDs, normalisierte
   Pseudonyme/Rollennamen und Actiontitel/-beschreibung zählen. SQL-Codepoints,
   Byteaggregate und Kotlin-UTF16 einschließlich NUL-/Surrogatfällen dürfen nicht
   ungeprüft gleichgesetzt werden; SQL- und Long-Overflow getrennt behandeln.
2. `OperationsStore.board`/`PersonDao.list`/`OperationsDao.roles/planned/plans/states`
   laden heute vollständige Bestände; `TaskStore.board`/`TaskDao.list/events` alle
   Tasks und Events. Personen-, Rollen-, Task-/Eventzahlen sind unbeschränkt,
   mehrere Action-/Pseudonym-/Rollentexte haben kein allgemeines Längenmaximum.
   Auch `actions().get()` materialisiert Actiontexte bereits vollständig.
3. Diese DAOs brauchen zunächst reine Metadaten-/Längenabfragen ohne Stringpayload
   sowie deterministische, begrenzte Batches/Cursorpfade, mit Prüfung auch großer
   Einzelwerte vor `getText`/Rowmaterialisierung. Batchgröße allein begrenzt keine
   einzelne Zeichenkette. Dafür sind konkrete DAO-Erweiterungen nötig; eine
   Schemaänderung/Migration wird in 2A-1 weder benötigt noch durchgeführt.
4. Reservierte obere Grenzen vor Fetch prüfen, tatsächliche materialisierte
   Einheiten danach gegen Reservierung nachprüfen; sämtliche temporären und
   retained Repräsentationen budgetieren. Alle Storagegates, Validierungen,
   Preflights und bedingungsloses Schließen von Cursor/Room/Lease erhalten.
5. Bei Ablehnung kein vollständiges/EMPTY-Ergebnis aus Teilbestand, keine
   Budgeterhöhung, zweite Action, Retryschleife oder unbeschränkter Fallback über
   heutige Vollreader. Ohne begrenzten Bedarfspfad bleibt der Bereich kontrolliert
   nicht verfügbar. Beschädigte Daten bleiben originale fail-closed-Storagefehler.

### 35.4 Tests und konkrete Ergebnisse

**GETESTET:** Abschlusslauf mit vorhandener lokaler Toolchain, offline;
keine Geräteverbindung, Credentials, Installation oder neue Benchmarkkampagne.
JUnit-XML-Ergebnisse abgeglichen, keine Fehler oder übersprungenen Tests:

| Host-Suite | Ergebnis |
|---|---:|
| ActiveWorkspaceValidityTest | 23/23 |
| ActiveWorkspaceValiditySessionTest (echter unveränderter SessionCoordinator, synthetische Auth/Uhr) | 8/8 |
| ActiveWorkspaceBudgetTest | 13/13 |
| Historische MutationContractTest / BudgetContractTest | 13/13 und 7/7 |
| Historische AssessmentSessionTest | 9/9 |
| WorkspaceBatchTest / ActiveWorkspaceTest | 21/21 und 20/20 |
| ActivePeopleTest / ActiveTasksTest / ActiveFunctionsTest | 25/25, 29/29, 27/27 |
| ProductViewModelTest | 7/7 |
| Data: SessionCoordinatorTest / B2SessionTest / B2KeyContractTest | 20/20, 16/16, 12/12 |
| Data: DocumentDeleteTest / DocumentProductStorageTest / DocumentReconciliationTest | 29/29, 11/11, 36/36 |
| Security: LeadershipCryptoTest | 4/4 |

Summe: **202 App-Hosttests** (davon **44 neue**), **124 Data-Hosttests**,
**4 Security-Hosttests**, insgesamt **330 bestanden**. Bestehende Proofs wurden
nur über `tools/p11assessment/host.init.gradle` in Hosttests eingebunden;
kein Probe-Overlay im Produktbuild. Offline `:app:assembleDebug`,
`:app:verifyPrototypeManifest`, `:app:verifyDemoManifest` und Architekturprüfung
bestanden. Vorhandener Keystore-Dateihash vor/nach Build unverändert.

Die neuen Tests decken Action-/Generation-/Permissionabweichung, Pending/Dirty,
Commit ohne Publication, Revoke/Lock vor Commitmeldung und nach Commit vor
Publication, doppelte/alte/fremde Tickets, Mutation während Read, Mutation oder
neuen Read unmittelbar am finalen Gate, Teilpatches, Rollback, terminal UNKNOWN,
Cleanupfehler, Threadkonfinierung und defensive Kopien der Projektionsmengen ab.
Coordinatorfälle prüfen zusätzlich BLOCKED, 30-s-Idle ohne Verlängerung durch
Checks, absolute 180 s trotz Interaktion, Foreground-/Screen-Lock-Verlust und
fehlenden Scope-Reuse nach Reauth. Echte Geräteauthentifizierung wird nicht behauptet.

Budgettests prüfen exakte Grenze, Überschreitung, Long-Mul-/Add-Overflow, alle
negativen Aggregate, inkonsistente Counts, extrem langen Einzeltext nur anhand
seiner Länge, Supplementary Unicode, Objekt-/Collection-/Index-/Array-/Kopierkosten,
zwei getrennte Maximalbudgets, Verweigerung vor simuliertem Fetch und weitere
Materialisierungsversuche nach Refusal, Fremdtickets, terminal Close sowie fehlende
Storagefreigabe bei Budgetverweigerung. Das ist kein Test eines bereits bounded
produktiven DAO-Readers.

Ein initialer Wrapperstart scheiterte am Sandbox-Dateisystem; der Projektcache
enthielt zudem nicht alle vorhandenen CameraX-Artefakte. Abschlussprüfungen nutzten
den vollständigen vorhandenen Benutzer-Gradlecache **offline**, ohne Dependency-
oder Versionsänderung. Die Fehler wurden nicht als Testerfolge gezählt.
Die bestehende onActivityResult-Deprecation bleibt außerhalb dieses Slices.

### 35.5 Mutationshooks, weitere Freigabegates und Dateiliste

**NICHT INTEGRIERT / BLOCKER:** Alle produktiven Writefamilien aus 19/34.4 benötigen
weiterhin nachgewiesene BEGIN-/Commit-/Cleanup-/Publication-Outcomes:
Action create/update/status, Person create/rename, Role create/rename/delete,
Plan/Snapshot, Task create/edit/transition/delete einschließlich atomarer Events,
Material/Kosten samt Referenz-/Summaryabhängigkeiten, Protokollrevisionen,
Memory-Section/Complete/Addendum, Dokumentimport/-löschung sowie Reconciliation/
Maintenance und Permission-/Leadershipwiderruf. Mehrstufige Dokumentarbeit bleibt
über Subcommits hinweg pending; ihre eigene Lane/Recovery wird nicht ersetzt.
P3b-Inhalte gehören niemals in den künftigen Kern. Nicht vorhandene allgemeine
P3a-Person-/Action-Deleteports werden nicht erfunden.

Personrename hat weiterhin keinen universellen Expected-Person-CAS. Vor längerer
Snapshotnutzung sind einzelne Editowner, aktuelle Formulartokens, Invalidierung
und sichere Abwehr paralleler/staler Rename-/Plan-/Rolleneditoren nachzuweisen;
ein Metadatenticket ersetzt keinen persistierten Konfliktvergleich.

Für **2A-2** erforderlich: unabhängiger Review dieses Slices; vertrauenswürdige
Scope-/Permissionquelle, vollständiger Revoke-/Recovery-/Owner-Lifecycle und
Writer-Outcomeadapter mit echten Commit-/Cleanup-Rennentests; bounded Reader und
Kalibrierung/Actual-Unit-Nachweise aus 35.3; eng allowlisteter Kernsnapshot ohne
zweiten Payloadstore. Erst nach diesen Nachweisen darf Navigation getrennt
integriert und auf 100–150 ms geprüft werden. Die Grundlagen allein erfüllen
keinen dieser noch offenen Integrationsnachweise.

Unverändert: SessionCoordinator, StorageAccess, 180-s-/30-s-Fristen,
SessionDbKeyOwner, SQLCipher-/Raw-Key-Vertrag, Tink, P3b/Leadership,
DocumentNamespaceLane, Dokument-Recovery/PDF-Isolation, Schema 11, App-ID,
Signatur/Keystore und sämtliche bestehende Navigations-/Writepfade. Keine echten
Produktdaten oder neue Klartextpersistenz. Produkt-App und installierte manuelle
P11-Test-App bleiben unangetastet. Historische **18/20, 2 UNREACHABLE bei
HC_L2_CONTENT** bleiben ohne neue UI-Ursachensuche dokumentiert; Abschnitt 33
wird nicht auf neue Software oder alle TalkBack-/Schriftkombinationen übertragen.

Vollständiger Commitumfang:

- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspaceValidity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspaceBudget.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceValidityTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceValiditySessionTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceBudgetTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

Nur diese sechs Quell-/Test-/Berichtsdateien; keine APKs, Logs, DBs, Schlüssel,
Keystores, Gerätekennungen oder persönlichen Pfade im Commit. Regulärer WIP-Push
und unabhängiger Remote-SHA-Abgleich; danach STOPP bis zum unabhängigen GitHub-
Review. Kein Force-Push, Merge, Release, Produktupdate oder weitere Integration.


## 36. Stufe 2A-2a – Bounded ACTIVE-Reader

### 36.1 Implementierter Umfang und Modulgrenze

**IMPLEMENTIERT, NICHT AN NAVIGATION ANGESCHLOSSEN:** Ausgangs- und Remotecommit
vor Änderungen unabhängig als `5c3e41cb0852f7862e332d2eeefb51a58bdb979d` auf
`p11-performance-optimization` bestätigt. `ProductBoundedActiveReader` ist ein
separat aufzurufender synchroner Worker-Lesepfad für genau eine aktuelle
ACTIVE-Bindung. Er liefert ausschließlich Action-Metadaten und `OperationalBoard`
mit Personen, Rollen, Rollenplanungen und operativen Personenstatus. Kein TaskBoard,
keine Events, Material-/Kosten-/Protokoll-/Memory-/Dokument- oder P3b-Payload.

`BoundedActiveStore` verwendet Room-`PooledConnection.usePrepared` innerhalb von
`withReadTransaction`. Die vorhandene Room-Schnittstelle reicht dafür aus; kein
neuer DAO-Vollread, keine Schemaänderung/Migration und kein neuer Driver.
`ActiveWorkspaceBudget` wurde mit unveränderter Chargelogik von App nach Domain
verschoben und für Data zugänglich gemacht; der bestehende App-Test importiert
jetzt diesen gemeinsamen Vertrag. Keine Data→App-Abhängigkeit und keine zweite
Budgetimplementierung. `ActiveWorkspaceValidity` bleibt unverändert und weiterhin
nicht mit Produktreadern/-writern verdrahtet.

`BoundedActiveBinding`/`BoundedActiveResult` definieren die Metadatenbindung und
fünf getrennte Ergebnisse: ADMITTED mit vollständiger Payload, BUDGET_REFUSED,
INVALID_DATA, STORAGE_FAILURE und ACCESS_REVOKED. Fehlende/fremde oder nicht mehr
ACTIVE-gebundene Aktionen werden für diesen Pfad als ACCESS_REVOKED abgewiesen;
PREPARATION/COMPLETED sind dabei keine behauptete Storagekorruption.

### 36.2 Tatsächliche Admission- und Abfragereihenfolge

**IMPLEMENTIERT:** Der Reader benutzt die originalen READY-Gates, Inventarprüfung,
DocumentNamespaceLane, SessionDbKeyOwner und ActionDatabaseAccess. Damit bleiben
sämtliche SQLCipher-/SQLite-/FK-/Room-Schema-Preflights und die ursprünglichen
DB-/Lease-Cleanupgrenzen erhalten. Kein Handle verlässt den Aufruf.

Innerhalb derselben konsistenten autorisierten Readtransaktion:

1. Aktuelle Action-ID/READY-Generation/Berechtigungsrevision gegen die vom
   vertrauenswürdigen künftigen Adapter gelieferte Bindung prüfen. `PRAGMA encoding`
   muss UTF-8 bestätigen; andere Kodierung wird ohne Umrechnungsschätzung abgewiesen.
   Ein skalares CASE prüft den persistierten Actionstatus, ohne Actiontexte zu laden.
2. Für die sechs Tabellen `actions`, `persons`, `roles`, `person_planned_roles`,
   `person_role_plans` und `person_operational_states`: ausschließlich COUNT,
   Typ-/Konsistenzflags und MAX der Feld-Bytegrößen lesen. Geprüft werden Text-/
   Integer-/Nulltypen, aktive Actionmetadaten und bekannte Status-/Funktions-/
   Ortskombinationen. Statusmengen werden aus den bestehenden Domain-Regeln abgeleitet.
3. Skalare EXISTS/NOT-EXISTS-Prüfungen sichern actionlokale Personen-/Rollenreferenzen,
   die Zugehörigkeit der Hauptrolle zum Rollenplan und den erforderlichen operativen
   Status jeder ACTIVE-Person. Bekannte Typ-/Referenzfehler sind INVALID_DATA,
   auch wenn der übrige Bestand groß ist.
4. Vor SUM gilt die sichere Untergrenze: maximal 65536 Tabellenzeilen insgesamt
   (8 MiB / 128 Byte) und maximal 8 MiB je Textfeld. Bereits größere Werte verweigern
   vor Payloadquery und Textfetch. Danach SUM der Bytegrößen **je Feld**: höchstens
   65536 × 8 MiB = 2^39 pro Summe. Kein Double/TOTAL und kein ungesichertes
   SQL-Längen-SUM über beliebig große Bestände. Negative/widersprüchliche Aggregate
   werden abgewiesen; tatsächliche SQL-Fehler bleiben STORAGE_FAILURE.
5. Zeilen-, Feld-, Text-, Relations- und Kapazitätsobergrenzen mit checked Long-
   Arithmetik in den vorhandenen Budgetvertrag übernehmen. **Beide** Reservierungen
   müssen vor der ersten Payloadquery zugelassen sein: höchstens 8 MiB Kern und
   ein zusätzlich auf 8 MiB begrenzter Übergangsbestand. Keine zweite Action,
   automatische Budgeterhöhung oder unbeschränkte Ersatzlesestrecke.
6. Erst jetzt Payloadqueries: deterministisches Rowid-Keyset mit LIMIT 32,
   einschließlich des ersten möglichen Rowidwerts ohne Startwert-Overflow.
   Jede Textspalte liefert zunächst Bytegröße und eine durch CASE begrenzte
   BLOB-Projektion. Vor `getBlob` erneut Länge, NULL, Storagegate und Bindung prüfen.
   Keine `actions().get()`, `persons().list()` oder Operations-Vollreader in diesem Pfad.
7. Tatsächliche Zeilen-/Byteeinheiten gegen die zugelassenen Obergrenzen prüfen;
   nach jeder Tabelle müssen Counts und Textsummen exakt übereinstimmen. Bestehende
   `ActionRow`-/`PersonRow`-/`RoleRow`-/`OperationalRow.model()` und `OperationalRules`
   sichern Fachwerte, Normalisierung, Rollenplan und Referenzen. Ausgabe wie bisher:
   Personen nach createdAt/id, Rollen nach normalizedName/id. Die Raw-/Builderdaten
   bleiben nur temporär, ohne ungezählte vollständige Rowlisten.
8. Erst nach vollständigem Materialisieren/Validieren die Readtransaktion, Room/
   Driver und Key-Lease beenden. Der gemeinsame reale Ergebnisadapter
   `boundedActiveRead` lässt Payload erst im originalen `StorageContext.publish`
   unter nochmals aktueller Bindung zurückkehren. Spätere UI-Publication benötigt
   später erneut die zuständige Autorität; ein Rückgabewert ist kein Freigabetoken.

### 36.3 Einheiten, Reservierung und tatsächliche Grenzen

**IMPLEMENTIERT:** `length(CAST(text AS BLOB))` misst bei verifiziertem UTF-8 die
vollständigen kodierten Bytes einschließlich NUL und Suffix. Es wird ausdrücklich
nicht `length(text)` als Kotlin-/UTF16-Größe benutzt. Nutztexte werden als BLOB mit
strengem Java-UTF8-Decoder (REPORT) gelesen, nicht über ersetzendes `getText`.
Ungültige Fortsetzungsbytes, Overlong-Sequenzen, kodierte Surrogate und Werte
außerhalb Unicode werden als INVALID_DATA abgewiesen.

Konservative Umrechnung für **gültiges** UTF-8: 1/2/3/4 UTF8-Bytes entsprechen
höchstens 1/1/1/2 UTF16-Einheiten. Damit ist die vollständige Bytezahl eine obere
Grenze der UTF16-Einheiten, auch für NUL (1→1) und supplementary Unicode (4→2).
Decodergebnislänge und tatsächlich gefetchte Bytes werden zusätzlich kontrolliert.
Die Kodierung wird bei unbekannter/anderer DB-Encoding nicht geraten.

Die Reservierung verwendet für Kern/Übergang Multiplikatoren 4/16 auf die
vollständigen Text-/Feldzahlen, `rows × copies × 8` Objektallowances,
`rows × copies` Listen-/Mapkapazitäten, `rows × copies × 16` Referenzen sowie
Arrays/Bytepuffer. Feste Containerreserven kommen hinzu. Darin sind Raw-Blobs,
Decoder-Charpuffer, String-/Normalisierungs-/Trimkopien, Rowwrapper, Personen-/
Rollenmaps, Planungssets, Sortier- und Ergebnislisten konservativ mitgerechnet.
Bereits Nullable-Felder werden als mögliche Allokationen belastet; keine
Deduplikationsannahme. Diese Engineering-Charges sind bewusst restriktiv und
weiterhin **keine zertifizierten ART-Größen oder harte Native-/Gesamt-App-Grenze**.

Die Reservierungsrechnung ist vollständig synchron; die threadgebundene
Budgetbuchhaltung wird nicht über suspendierende Room-Aufrufe mitgeführt. Ihre
versiegelten Kostenobergrenzen werden als skalare Werte übernommen, der reine
Metadatenledger geschlossen. Seal bestätigt hier ausschließlich die eingefrorene
Kostenreservierung, niemals vollständigen Inhalt. Erst die späteren tatsächlichen
Count-/Byteprüfungen und das originale Cleanup-/Publicationgate erlauben ADMITTED.
Ein erzwungener Coroutine-Threadwechsel ist dafür separat getestet.

**BLOCKER für spätere Vorhaltung:** Dieser Einstieg akzeptiert keinen schon
gehaltenen Kernbestand und besitzt keinen Payloadowner. Vor Einbindung in längere
Snapshotnutzung sind die Reservierungen in genau einen Scopeowner zu übertragen,
auch bei Reload/Formular-/Sortierkopien. Beliebig viele vom Aufrufer behaltene
Rückgabewerte werden hiermit nicht global budgetiert. Kopierallowances/ART-Retention
und Android-Driverpuffer sind noch zu kalibrieren. Die SQLite-Engine muss bei
Größenabfragen Daten intern lesen können; der Nachweis betrifft ausbleibende
Kotlin-/Domain-/Blobmaterialisierung vor Admission, keine garantierte Begrenzung
sämtlicher nativer SQLCipher-/SQLite-Arbeitspuffer.

### 36.4 Fehler, Cleanup und unveränderte Produktwirkung

**IMPLEMENTIERT:** Budgetverweigerung beendet den Read ohne Payload, EMPTY,
Teil-CONTENT, Retry, Prefetch oder Fallback und löst kein Storage-BLOCKED aus.
Nicht geladene Texte werden dadurch nicht als fachlich gültig bestätigt. Unter
Admission erkannte inkonsistente Felder/Referenzen oder ungültiges UTF-8 sind
INVALID_DATA und benutzen das zentrale Blocked-Verhalten. Echte SQL-/Inventar-/
Preflight-/Cleanupfehler sind STORAGE_FAILURE; ein Cleanupfehler darf weder eine
erfolgreiche Lesung noch eine vorherige Budgetverweigerung als Erfolg erhalten.
Es gibt keine Reparatur und keinen neuen Recoverymechanismus.

Sperre/Generation-/Berechtigungs-/Actionbindungsverlust führt zu ACCESS_REVOKED
und ohne Payload zurück. Statements enden über `usePrepared`; Originaltransaktion,
Room/Driver und Lease über ihre bestehenden finally-Grenzen. Eine verspätete
Freigabe wird nach Cleanup erneut zentral abgewiesen. Dieser Read erzeugt keine
Write-Outcomes und schließt keine offene Mutations-/UNKNOWN-/Recoveryintegration.

**NICHT INTEGRIERT:** ProductRuntime, ProductViewModel, navigateActive,
loadWorkspace, vorhandene Repositories, Clears/Formulare/Detailowner und sämtliche
Produktwriter bleiben unverändert. Einziger expliziter Android-Aufrufer ist eine
neue Methode der bereits paketgeschützten synthetischen Debugfixture für Tests.
Kein RAM-Hit, keine längere produktive Snapshotvorhaltung, keine behauptete
Navigationsverbesserung. TaskBoard/TaskEvents werden weiterhin unverändert gelesen.

### 36.5 Konkrete Testnachweise und offene Android-Laufzeitprüfung

**GETESTET:** 22 neue `BoundedActiveStoreTest`-Hostfälle führen die produktiven
SQL-Abfragen, UTF8-Dekodierung, Materialisierung und den produktiven Ergebnisadapter
gegen **echtes SQLite** aus. Python-Standardbibliothek hält dafür eine ausschließlich
in-memory DB mit Tabellen/Indizes aus dem vorhandenen Schema-11-JSON. Der kleine
Test-Statementadapter ersetzt keine Repositoryantworten; Python und dieser Adapter
sind ausschließlich Hosttestinfrastruktur, keine Appdependency oder Klartextdatei.
Room-Callbacks/SQLCipher-Android-Driver werden damit nicht vorgetäuscht.

Abgedeckt sind:

- Kleine ACTIVE-Aktion mit exakter erwarteter Action-/Operationsprojektion,
  Planung/Hauptrolle/Status, stabile Reihenfolge über mehrere Batches und Ausschluss
  einer zweiten Aktion mit großem Textbestand.
- PREPARATION/COMPLETED, fehlende/fremde Bindung, falsche Generation sowie
  ungültige Personen-/Rollen-/Hauptrollenreferenzen und fehlende Statuszeilen.
- 5000 Personen; 1000 Rollen samt 1000 Planungsrelationen; Count-Untergrenze mit
  33000 Personen; 8-MiB-plus-ein-Byte-Maximum vor SUM; sehr lange Einzeltexte in
  Action/Person/Rolle und großer Suffix hinter NUL. Bei Refusal **keine vorbereitete
  Payloadquery, kein Blobfetch und keine vollständige Rowmaterialisierung**.
- Exakt 8 MiB Übergangscharge im echten Reader und Verweigerung nach einem weiteren
  Nutzbyte, ohne neue Payloadquery; bestehende Budgettests prüfen zusätzlich die
  exakte Bytegrenze, Grenzwert plus eine Einheit und Long-Add-/Mul-Overflow.
- NUL und supplementary Unicode ohne Abschneiden, Unicode-Normalisierung mit
  Expansion/Kontext, malformed UTF8/Overlong/Surrogaten/ungültigem Wertebereich,
  tatsächlich UTF16-kodierte SQLite-DB, falsche SQL-Typen und ungültige Domainwerte.
- Tatsächlicher SQLite-Integer-SUM-Overflow ist ein Fehler; die Readerguards
  verhindern SUM bereits bei zu großen Counts/Einzelwerten, ohne Float-Ausweichpfad.
- Revoke an jeder Readerphase, Generation-/Bindungswechsel beim Materialisieren,
  Sperre/Generation-/Permissionwechsel am finalen Publicationgate, Cleanupfehler
  nach Erfolg oder Refusal, kein BLOCKED bei Budgetknappheit und geschlossene
  Statements/Readtransaktionen. Erzwungene Coroutine-Threadwechsel bestehen.

**GETESTET:** Abschlussstand: 202 App-Hosttests einschließlich aller 44 neuen
2A-1-Fälle und der unveränderten historischen Proofs, 146 Data-Hosttests
(124 bestehende plus 22 Readerfälle), 4 Security-Hosttests: **352 bestanden,
keine Fehler/Skips**. Fokussierte bestehende Workspace-/ACTIVE-/ViewModel-,
SessionCoordinator-/B2-Key-/B2-Session-, Dokument-Delete-/Storage-/Reconciliation-
und LeadershipCrypto-Regressionen enthalten. Offline-Debugbuild, Architektur-
und Produkt-/Demo-Manifestprüfungen bestanden; vorhandener Keystorehash unverändert.

**NUR KOMPILIERT, NICHT AUSGEFÜHRT:** Vier `BoundedActiveRoomTest`-Fälle benutzen
die vorhandene paketbeschränkte isolierte SQLCipher-/Room-Fixture: Gleichheit
mit bisherigen Action-/Operationsreadern, Phasenablehnung, finale Publication-
verweigerung/Revoke und wiederholte Reads mit Cleanup-/FD-Kontrolle. Die gesamte
Data-Androidtestsuite wurde offline kompiliert. Kein Gerät kontaktiert und keine
APK installiert; diese vier Fälle haben daher **kein Laufzeit-PASS**. Die direkte
Gleichheit mit dem alten realen Room-Operationsreader ist vorbereitet, aber noch
nicht am Android-Driver nachgewiesen. Das bleibt technischer Freigabe-BLOCKER.

Gefundene Testfehler wurden untersucht: Der erste Hostadapterimport setzte eine
Indexliste für jede Schemaentität voraus; danach war ein Fehlerklassifikationstest
noch mit einem vorher absichtlich beschädigten Feld befüllt. Beide Testaufbauten
wurden korrigiert. Diese Fehlläufe werden nicht als erfolgreiche Prüfungen gezählt.

### 36.6 Vollständiger Umfang und verbleibende BLOCKER

Gemessener Diffumfang vor Commit: **11 logische Dateien einschließlich einer
Verschiebung, 1124 Einfügungen und 4 Löschungen** (`git diff --cached --stat`).
Vollständige Pfade:

- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspaceBudget.kt`
  → `domain/src/main/kotlin/org/pag/domain/ActiveWorkspaceBudget.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceBudgetTest.kt`
- `domain/src/main/kotlin/org/pag/domain/BoundedActiveRead.kt`
- `data/src/main/kotlin/org/pag/data/product/BoundedActiveStore.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductBoundedActiveReader.kt`
- `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/BoundedActiveRoomTest.kt`
- `data/src/test/kotlin/org/pag/data/product/BoundedActiveStoreTest.kt`
- `data/src/test/kotlin/org/pag/data/product/SqliteHostConnection.kt`
- `data/src/test/resources/bounded_active_sqlite.py`
- `docs/product-p11-active-workspace-assessment.md`

**BLOCKER:** Android-SQLCipher-/Room-Laufzeitnachweise, Chargen-/Kopierkalibrierung,
späterer einheitlicher Scope-/Payload-/Formularowner mit begrenztem Übergang,
vertrauenswürdige Permission-/Status-/Mutationsversionierung und alle noch offenen
Writer-/Outcome-/Recoveryhooks aus 35.5. Personrename/Plan-/Rolleneditoren benötigen
weiterhin eine nachgewiesene Konfliktlösung; kein universeller CAS ist hinzugekommen.
Task-/Event-Admission ist ein eigener nächster Reader-Slice, nicht verdeckt erledigt.
Erst danach kann eine gesondert geprüfte Navigationseinbindung folgen.

SessionCoordinator, 180-s-absolut/30-s-READY-idle, StorageAccess, SessionDbKeyOwner,
SQLCipher/Tink, P3b/Leadership, DocumentNamespaceLane/-Recovery, PDF-Isolation,
Schema 11, Raw-Key-Vertrag, App-ID und Signatur/Keystore unverändert. Keine neuen
Produktdaten-/Credential-/Klartextpersistenzpfade. Produkt-App und installierte
manuelle Test-App bleiben unangetastet. Historische 18/20 mit 2 UNREACHABLE bleiben
unverändert; weder neue UI-Ursachensuche noch neue Accessibility-/Produktabnahme.

Nur notwendige Quellen/Tests und dieser Bericht im regulären WIP-Commit; keine
APKs, DBs, Logs, Schlüssel, persönlichen Pfade oder Gerätekennungen. Nach Push und
unabhängigem Commit-/Remote-SHA-Abgleich STOPP bis zum unabhängigen GitHub-Review;
kein Force-Push, Merge, Release, Produktupdate oder weitere Integration.

## 37. Android-SQLCipher-/Room-Laufzeitnachweis – isolierte Vorbereitung

### 37.1 Auftrag und Artefaktgrenze

**VORBEREITET, NICHT AM GERÄT GETESTET:** Ausgangsstand lokal und auf dem
WIP-Remote vor Änderungen identisch:
`d4860848d0dfe45e9a067b488d645a8e72767d41`. Ausschließlich die vier bestehenden
`BoundedActiveRoomTest`-Methoden sollen später ausgeführt werden. Keine Änderung
an Produktquellen, diesen Androidtestmethoden, Navigation, Snapshotvorhaltung,
Writepfaden oder Sicherheitsarchitektur. Kein Gerätezugriff, keine Installation,
Deinstallation, Datenlöschung oder `connectedAndroidTest` in diesem Auftrag.

Das neue ausschließlich auf `:data` wirkende Init-Skript baut offline die
Library-Instrumentation. **Eine APK, kein separates Ziel-App-APK.** Das gebaute
Manifest wurde tatsächlich mit AAPT geprüft, nicht aus Gradle-Annahmen abgeleitet:

| Eigenschaft | Verifiziertes Ergebnis |
|---|---|
| Test-/APK-Paket | `org.pag.data.unlockprobe` |
| Instrumentation-Target | `org.pag.data.unlockprobe` (Self-Target) |
| Runner | `androidx.test.runner.AndroidJUnitRunner` |
| Application | Standard `android.app.Application`, kein eigener Name |
| Label | `PaG P11 Bounded Tests` |
| Permissions / Queries | keine |
| Activities / Services / Provider / Receiver / Aliase | keine |
| Instrumentation | genau eine |
| Backup / Cleartext | deaktiviert |
| Debuggable / minSdk / targetSdk | ja / 30 / 37 |

Der erste Manifestcheck fand geerbte Biometriepermission und Testservice-Queries.
Das neue test-only Manifest entfernt diese sowie den Room-Multiinstance-Service;
der abschließend gebaute und geprüfte Stand hat die oben dokumentierte leere
Permission-/Komponentenmenge. Produktmanifest und vorhandenes manuelles
P11-Testmanifest wurden nicht verändert.

Eingefrorener APK-SHA256:
`c5a628f6b6b4a23dc1adeb4af99d136a428f76204e99c906a422e1de0f7ddf42`.
Gebautes Original und eingefrorene Kopie sind nach dem Abschlussbuild identisch.
Ein Signer; kryptografische APK-Signaturprüfung bestanden. Zertifikat gegen den
**bereits vorhandenen unabhängigen lokalen Vertrauensnachweis** geprüft, nicht
gegen einen aus dieser APK übernommenen Vertrauensanker. Bestehender
Keystore-Dateihash und eingefrorenes manuelles APK unverändert. Keine neuen
Schlüssel und keine Keystoreumstellung. ARM64-SQLCipher und DEX-Symbole der
Testklasse/vier Methoden vorhanden; Symbolpräsenz ersetzt keinen Testlauf.

Der rein lesende APK-Prüfer erzwingt Hash, unabhängiges Zertifikat, Self-Target,
Runner und die genaue Manifeststruktur. `org.pag.prototype.a` und
`org.pag.perf.p11probe` sind ausgeschlossen. APKs, Vertrauensdateien und Logs
bleiben lokal und werden nicht eingecheckt.

### 37.2 Treiberabgleich und konkrete statische Grenze

Geprüft wurden die vorhandenen Reader-/Fixture-/B2-Cleanupquellen, die lokal
vorliegenden Room-3.0.3-Quellen und die tatsächliche Dependency
`net.zetetic:sqlcipher-android:4.19.1`. AAR-SHA256:
`43fc10cae8f1f1059f0933e8942a1eb328989d13426378597cd4add1b34a34fd`.
Die folgenden Aussagen unterscheiden Quell-/Bytecodebefund von Androidlaufzeit:

| Gegenstand | Statisch/auf Host bestätigt; offene Androidgrenze |
|---|---|
| `withReadTransaction` | Room verwendet Readerconnection und DEFERRED-Transaktion; tatsächliche Androidausführung offen. |
| `PooledConnection.usePrepared` | Room-Passthrough verwendet `delegate.prepare(sql).use`; kein explizites `reset()` an dieser Grenze. |
| `PRAGMA encoding` | Reader akzeptiert ausschließlich exakt `UTF-8`; Androidantwort noch nicht ausgeführt. |
| `CAST(TEXT AS BLOB)` / `getBlob` | Reader misst BLOB-Bytelängen, lädt begrenzte BLOBs und dekodiert strikt UTF-8. SQLCipher delegiert `getBlob` an Android-Cursor. NUL-/Supplementary-Verhalten am Androidtreiber nicht nachgewiesen. |
| Rowid-Keyset | Actionfilter, `ORDER BY rowid LIMIT 32`, nächste Seite über gebundenes `rowid > ?`; keine OFFSET-Vollmaterialisierung. Vier Fälle decken keine Folgeseite ab. |
| Bindings / NULL | Echte SQLCipher-Klasse erhält explizites NULL und Long als typisierte Mapwerte; `step()` übergibt ein Object-Array an `rawQuery`. Native Auswertung offen. |
| Statement-/Cursor-Cleanup | Konkrete unten beschriebene Abweichung; explizites Cursor-Close bei Statementende nicht bestätigt. |
| Transaktion / Lease | Vorhandenes finales DB-/Driver-/Lease-Cleanup bleibt erhalten; Endwirkung am Androidtreiber noch offen. |

**KONKRETER STATISCHER BLOCKER:** Der Bytecode von
`SQLCipherStatement.close()` setzt lediglich `closed=true`; er schließt den
gehaltenen Cursor nicht. `reset()` ruft dagegen `cursor.close()` auf.
[B2Driver](../data/src/main/kotlin/org/pag/data/prototype/b2/B2Driver.kt) delegiert
`SQLiteStatement` und überschreibt `close()` nicht. Zusammen mit Rooms `.use`
ist damit die Annahme eines expliziten Cursor-Close am Ende jedes
`usePrepared` widerlegt. Zwei neue Hostdiagnosen gegen die tatsächliche
Dependencyklasse (nur der Cursor ist ein Proxy, keine native DB) bestätigen
Close-/Reset- und Bindingverhalten. Der Test dokumentiert den Befund, keine
Freigabe eines gewünschten Cleanupverhaltens.

Das beweist **keinen endgültigen DB-/FD-/Native-Leak**: Das bestehende
[ActionDatabase-Cleanup](../data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt)
schließt weiterhin Room, beendet den Parent, schließt den Driver und prüft
Lifecycle/Publication. Dessen Android-Endeffekt und CursorWindow-Verhalten bleiben
zu untersuchen. Keine spekulative Produktkorrektur vorgenommen.

Die bestehende `P11SyntheticFixture.close()` widerruft den Owner, überschreibt
das kontrollierte Keyarray und versucht nach Pfadprüfung `deleteRecursively()`.
Dessen Boolean wird nicht geprüft: erfolgreiche Verzeichnisentfernung ist kein
eigener nachgewiesener Testbefund. Auch dies wurde nicht verdeckt korrigiert.

### 37.3 Exakter späterer Lauf und Datenschutz

Der [reproduzierbare Ablauf](../tools/p11bounded/README.md) enthält Build,
APK-Gate und den vollständigen festen Instrumentation-Selektor für ausschließlich:

1. `boundedProjectionEqualsExistingOperationsAndActionReaders`
2. `preparationCompletedAndMissingAreRejected`
3. `finalPublicationDenialAndRevokedSessionReturnNoPayload`
4. `repeatReadClosesConnectionsAndDoesNotChangeProjection`

**BENÖTIGT GESONDERTE FREIGABE:** Installation dieser eingefrorenen APK und
Ausführung dieser vier Methoden; eine nachfolgende Deinstallation benötigt
expliziten Einschluss. Vorher Gerät eindeutig prüfen, nur Installations-/Signatur-
metadaten der Produkt-/manuellen App festhalten, keine Produktdaten lesen.
Falls das temporäre Paket schon vorhanden ist: STOPP, kein Ersetzen oder Leeren.
Installation ohne `-r`. Keine zweite APK, keine allgemeine Suite, keine weiteren
Tests, kein automatisches Wiederholen. Exakt vier Methodenergebnisse und regulären
Runnerabschluss auswerten; Nichtausführung/Skips sind kein PASS.

Die unveränderte paketbeschränkte Fixture erzeugt ausschließlich synthetische
Daten und einen zufälligen flüchtigen DB-Schlüssel im privaten temporären
Testpaketbestand. Kein echter Login, keine Produktdaten und kein Produktkeystore-
zugriff durch den Test. Bei Crash können dort Reste bleiben. Cleanup betrifft
nur den geprüften Fixture-Unterbestand beziehungsweise nach ausdrücklicher
Freigabe das in diesem Lauf neu installierte temporäre Paket. Kein `pm clear`,
keine Berührung der Produkt-App oder der vorhandenen manuellen P11-App.
Vorher-/Nachher-Metadatenvergleich ist im Plan vorgeschrieben, hier nicht erfolgt.

### 37.4 Konkrete Prüfungen und Aussagegrenzen

**GETESTET:** Offline 202 App-, 148 Data- und 4 Security-Hosttests:
**354 bestanden, 0 Fehler, 0 Skips**. Darin bestehende 2A-1-Verträge, historische
Mutation-/Budget-Proofs, 22 bounded Readerfälle, Workspace-/ACTIVE-/ViewModel-,
Session-/B2-Key- und Dokumentregressionen sowie die zwei neuen Treiberdiagnosen.
Zusätzlich **6 Python-APK-Gatetests bestanden**, einschließlich Ablehnung falscher
Pakete/Targets/Runner, zusätzlicher Komponenten/Permissions und Signerabweichungen.
Abschließendes reales APK-Gate mit AAPT und kryptografischem Apksigner bestanden.

**OFFLINE KOMPILIERT:** Data-Androidtest-APK einschließlich der vier Methoden,
Produkt-Kotlin und Produkt-/Demo-Manifestprüfungen; Build erfolgreich. Exakte
lokale Befehle: `:data:assembleDebugAndroidTest` mit dem neuen Init-Skript sowie
App-Hosttests mit dem bestehenden `tools/p11assessment/host.init.gradle`,
fokussierte Data-Hosttests und `:security:testDebugUnitTest`, jeweils `--offline`.

**NICHT GETESTET:** Alle vier Android-Laufzeitergebnisse. Die vorhandenen Fälle
verwenden 20 Personen und keine Rollen/Planungen. Sie decken keine nichtleeren
Rollenpfade, keine >32-Zeilen-Folgebatches und keine Android-NUL-/Unicode-/Übergrößen-
oder vollständigen Budgetgrenzfälle ab. Hostbefunde werden nicht in Android-PASS
umgedeutet. Selbst vier erfolgreiche spätere Fälle lösen diese Grenzen und den
Cursor-Cleanupbefund nicht automatisch. Keine neue Performanceverbesserung,
Snapshotfreigabe, Sicherheits- oder Produktabnahme behauptet.

### 37.5 Umfang und Abschlussgrenze

Gemessener Commitumfang: sieben Dateien, 509 Einfügungen, keine Löschungen
(`git diff --cached --stat`); ausschließlich Test-/Runner-/Prüfwerkzeuge und Bericht:

- `data/src/test/kotlin/org/pag/data/product/BoundedDriverContractTest.kt`
- `tools/p11bounded/prepare.init.gradle`
- `tools/p11bounded/AndroidManifest.xml`
- `tools/p11bounded/verify_apk.py`
- `tools/p11bounded/test_verify_apk.py`
- `tools/p11bounded/README.md`
- `docs/product-p11-active-workspace-assessment.md`

**BLOCKER:** Gesonderte Gerätefreigabe und tatsächlicher Androidlauf;
Statement-/Cursor-Cleanupbewertung, nicht nachgewiesene Fixture-Verzeichnislöschung
und die genannten Abdeckungslücken. Keine Produktionskorrektur, kein Anschluss
an ProductViewModel/navigateActive/loadWorkspace, keine Session-/Storagegate-,
SQLCipher-/Tink-/Schema-11-/P3b-/Dokument-/App-ID-/Signaturänderung. Installierte
Apps unangetastet. Regulärer WIP-Commit/Push, unabhängiger Remote-SHA-Abgleich,
danach STOPP bis zum unabhängigen Review beziehungsweise ausdrücklicher Freigabe.

## 38. Stufe 2A-2a – Android-SQLCipher-/Room-Laufzeitergebnisse

### 38.1 Freigabe und verifiziertes Artefakt

**AM ANDROIDGERÄT AUSGEFÜHRT:** Auf ausdrückliche Benutzerfreigabe erfolgten eine
Installation, genau ein fest selektierter Instrumentationlauf und die anschließende
Entfernung ausschließlich des neu installierten `org.pag.data.unlockprobe`.
Ausgangsbranch `p11-performance-optimization`, freigegebener Commit
`71e231a1e59217e113c06c33a9446a28e9182bfc`, Arbeitsverzeichnis vorher sauber.
Genau ein über ADB erreichbares Samsung wurde eindeutig ausgewählt. Keine
Gerätekennung oder Rohlogs werden veröffentlicht.

Ausschließlich das vorhandene eingefrorene APK verwendet, **kein Neubuild**.
SHA256 erneut exakt bestätigt:
`c5a628f6b6b4a23dc1adeb4af99d136a428f76204e99c906a422e1de0f7ddf42`.
Der vorhandene `tools/p11bounded/verify_apk.py` bestand einschließlich
kryptografischer Signaturprüfung gegen den bereits unabhängig bekannten lokalen
Zertifikatsnachweis. Bestehender Keystore-Dateihash unverändert. Self-Target und
Paket `org.pag.data.unlockprobe`, Runner `androidx.test.runner.AndroidJUnitRunner`,
Standard-Application, keine Permissions/Queries oder zusätzlichen Komponenten,
Backup/Cleartext deaktiviert. Details des Gates unverändert wie Abschnitt 37.

Vorher ausschließlich Paket-/Installationsmetadaten der drei freigegebenen
Paketnamen lokal erfasst: Existenz, Version, firstInstallTime, lastUpdateTime und
Signaturinformationen. Das temporäre Paket war nicht installiert. Installation
**ohne `-r`** erfolgreich. Danach installierte Test-APK lesend gegen das eingefrorene
Artefakt verglichen: vollständiger Dateihash identisch; damit auch identische
signierte APK. Installierte Instrumentation mit korrektem Self-Target bestätigt.
Keine geschützten Appdaten/DBs gelesen oder kopiert, keine echten Credentials.
Fixture und Tests unverändert: ausschließlich synthetische Daten und flüchtiger
synthetischer DB-Schlüssel im privaten Testpaketbestand.

### 38.2 Einzelresultate und Runnerabschluss

Der vollständige feste Methodenselektor aus `tools/p11bounded/README.md` wurde
**einmal** aufgerufen. Keine andere Testklasse, keine Suite, kein Retry,
kein `connectedAndroidTest`, keine Änderung von Assertions oder Produktcode.
Die tatsächliche Runnerreihenfolge war 2, 4, 3, 1 der folgenden Auftragsliste:

| Methode in `BoundedActiveRoomTest` | Tatsächlich ausgeführt | Ergebnis |
|---|---|---|
| `boundedProjectionEqualsExistingOperationsAndActionReaders` | einmal | PASS |
| `preparationCompletedAndMissingAreRejected` | einmal | PASS |
| `finalPublicationDenialAndRevokedSessionReturnNoPayload` | einmal | PASS |
| `repeatReadClosesConnectionsAndDoesNotChangeProjection` | einmal | PASS |

**GESAMT: 4/4 PASS, 0 FAIL/ERROR/SKIP.** Der Originalstream enthält für jede
exakte Klasse/Methode genau ein Startereignis (Status 1) und ein erfolgreiches
Endereignis (Status 0), insgesamt acht Methodenstatusereignisse. Runnerabschluss
`OK (4 tests)`, abschließender Instrumentationcode `-1`, ADB-Exitcode 0.
Kein unerwarteter Prozessabbruch oder SQLCipher-/Room-Fehler im Runnerstream
beobachtet. Keine zusätzliche Logcat-/Gerätetestkampagne durchgeführt; daraus
folgt keine Aussage über sämtliche geräteweiten Warnungen.

Bestätigt sind im Umfang dieser Fixture die gleiche Action-/Operationsprojektion
wie bei den bestehenden Readern, Ablehnung von PREPARATION/COMPLETED/fehlender
Action, verweigerte finale Publication und Revoke ohne Payload sowie wiederholte
Reads mit identischer Projektion und bestandener FD-Toleranzprüfung.
Die **22 Host-Readerfälle** aus Abschnitt 36 bleiben davon getrennte Nachweise;
es wurden hier keine zusätzlichen Hosttests oder Benchmarks ausgeführt.

### 38.3 Cursor, Driver, Lease und Dateideskriptoren

**A – Statement-Cursor-Close: NICHT NACHGEWIESEN.** Der statisch und im Host
belegte Befund bleibt unverändert: `SQLCipherStatement.close()` setzt nur das
Closed-Flag, schließt den gehaltenen Cursor nicht ausdrücklich. Ein bestandener
Androidtest beseitigt diesen Befund nicht. Keine Produktkorrektur vorgenommen.

**B – Datenbank-/Driver-/Lease-Cleanup: BEGRENZTER LAUFZEITNACHWEIS.** Die
Readeraufrufe kehrten erfolgreich durch den vorhandenen Rückgabepfad zurück.
Dieser führt vor Rückgabe DB-/Room-Close, Parent-Beendigung, Driver-Close und
Lifecycle-/Publicationprüfungen aus; `withDbKey` verlässt seinen Lease-Scope.
Es trat dabei kein gemeldeter Cleanupfehler auf. Die Tests instrumentieren jedoch
weder jeden Close-Aufruf noch native Cursorfreigabe, Lease-Zeroisierung oder
sämtliche Runtime-Kopien separat. Vollständige physische Ressourcen-/Wipegarantie
ist damit nicht nachgewiesen.

**C – FD-Beobachtung: TOLERANZPRÜFUNG BESTANDEN.** Die vierte Methode erstellt
einen initialen Read, erfasst die FD-Anzahl, führt drei weitere Reads aus und
prüft anschließend `FD_nachher <= FD_vorher + 2`. Diese Assertion bestand.
Die absoluten Zahlen werden vom bestehenden Test nicht ausgegeben. Damit wurde
kein Anstieg über diese Toleranz in diesem kurzen Ablauf beobachtet; insbesondere
kein Langzeitnachweis eines ausbleibenden dauerhaften FD-Anstiegs. Es gab keinen
separaten Cursorbestand-/CursorWindow-Zähler und keine erzwungene GC-Prüfung.

**D – OFFEN:** Lebensdauer und Freigabe einzelner Cursor beziehungsweise nativer
CursorWindow-Ressourcen; Verhältnis von Statementende zu endgültigem DB-/Driver-
Cleanup. Die vorhandene Fixture prüft den Boolean von `deleteRecursively()` nicht.
Daher bleibt erfolgreiche einzelne Fixture-Verzeichnislöschung unbewiesen,
obwohl kein Closefehler gemeldet wurde und anschließend das ganze ausdrücklich
freigegebene temporäre Paket erfolgreich deinstalliert wurde.

### 38.4 Paketstatus und Bereinigung

| Paket | Vor Installation | Nach Installation | Nach Bereinigung |
|---|---|---|---|
| `org.pag.data.unlockprobe` | fehlt | neu installiert, Identität verifiziert | fehlt |
| `org.pag.prototype.a` | vorhanden | Metadaten unverändert | Metadaten unverändert |
| `org.pag.perf.p11probe` | vorhanden | Metadaten unverändert | Metadaten unverändert |

Vor der Deinstallation erneut Paketexistenz und sämtliche lokal erfassten
Versions-/Zeit-/Signaturmetadaten gegen die nachgewiesene Neuinstallation
abgeglichen. Ausschließlich `org.pag.data.unlockprobe` entfernt; ADB meldete
Erfolg, abschließende Paketabfrage bestätigte Abwesenheit. Für beide geschützten
Apps sind Paketversionen, firstInstallTime, lastUpdateTime und erfasste
Signaturinformationen im Vorher-/Nachhervergleich exakt identisch. Keine andere
Installation/Deinstallation, kein `pm clear`, kein Launcherstart, kein Produktupdate
und keine Änderung der manuellen P11-App. Rohstream, Metadaten, installierte
Test-APK-Kopie und Geräteidentität ausschließlich lokal aufbewahrt, nicht in Git.

### 38.5 Weiterhin offene Grenzen und nächste Schritte

Der Android-Laufzeitblocker für **genau diese vier Fälle** ist mit 4/4 bestanden
abgearbeitet. Keine allgemeine Android-/Sicherheits-/Performancefreigabe.
Unveränderte Abdeckungslücken: 20 synthetische Personen, keine nichtleeren
Rollen-/Planungsbestände, keine >32-Zeilen-Folgebatches, keine Android-NUL-/
Supplementary-Unicode-/Übergrößen-/vollständigen Budgetgrenzfälle. Kein separater
Lock-/Generationwechsel während eines laufenden Batches und kein Langzeit-
Ressourcennachweis durch diese vier Methoden. Die Hostnachweise bleiben getrennt.

**BLOCKER vor weiterer produktiver Integration:** Cursor-/Ressourcenlebensdauer
bewerten; weitere Android-Abdeckung nur nach gesondertem Auftrag; konservative
Budget-/Kopierkalibrierung, Scope-/Payload-/Formularowner, Permission-/Status-/
Mutationsversionierung und noch offene Writer-/Outcome-/Recoveryhooks einschließlich
Personrename-/Editorkonflikten aus Abschnitten 35–37. Task-/Event-Reader bleibt
ein eigener Slice. Keine RAM-Hits, keine verlängerte Snapshotvorhaltung und keine
Navigationseinbindung aktiviert; keine neue Navigationszeit gemessen oder behauptet.

Ausschließlich dieser bereinigte Bericht geändert. Regulärer WIP-Commit und Push
mit unabhängigem Remote-SHA-Abgleich; danach STOPP bis zum unabhängigen GitHub-
Review. Kein Force-Push, Merge, Release, Produktupdate oder weitere Implementierung.

## 39. Stufe 2A-2b – Bounded Task-/Eventreader

### 39.1 Implementierte Architektur und unveränderte Produktgrenze

**IMPLEMENTIERT / NICHT INTEGRIERT:** Lokaler Ausgangsstand und GitHub-Branch
`p11-performance-optimization` vor Änderungen unabhängig identisch bestätigt:
`341552c45a5a6c0088c13686ea8173dde2668c15`; Arbeitsverzeichnis sauber.
Neue getrennte Worker-Lesestrecke `ProductBoundedTaskReader` mit
`BoundedTaskStore` und Domain-Ergebnis `BoundedTaskResult`. Genau eine aktuelle
ACTIVE-Bindung aus Action-ID, READY-Generation und Berechtigungsrevision; kein
Berechtigungstoken. Fünf getrennte Ergebnisse: ADMITTED mit vollständigem
TaskBoard, BUDGET_REFUSED, INVALID_DATA, STORAGE_FAILURE, ACCESS_REVOKED.

Der neue Pfad verwendet dieselben originalen READY-/Storagegates, Inventarprüfung,
DocumentNamespaceLane, SessionDbKeyOwner, SQLCipher-/FK-/Room-Schema-Preflights,
ActionDatabaseAccess und kurze `withReadTransaction` wie der bestehende bounded
Reader. Keine Änderungen an diesen zentralen Klassen. ActionRow wird innerhalb
der Admission mitgezählt und nach Zulassung mit dem bestehenden Modell validiert;
der zurückgegebene Domainbestand enthält nur TaskBoard und Budgetmetadaten.
Personen-/Rollenpayload wird nicht zusätzlich gelesen; SQL prüft deren Referenzen.

Nur explizite Tests rufen den neuen Pfad auf. `P11SyntheticFixture.boundedTasks`
ist ein neuer paketgeschützter synthetischer Debug-Einstieg. Keine Aufrufstelle
in ProductRuntime/ProductViewModel, navigateActive oder loadWorkspace; bestehende
Repositories, TaskDao, TaskStore.board und sämtliche produktiven Writer bleiben
unverändert. Keine Snapshot-Wiederverwendung, RAM-Hits, längere Lebensdauer,
Hintergrundvorhaltung, neuen Clears/Formularverträge oder optimistischen Writes.
Keine neue Performanceverbesserung gemessen oder behauptet.

### 39.2 SQL-Admission vor Materialisierung

**IMPLEMENTIERT:** Innerhalb derselben konsistenten autorisierten Readtransaktion:

1. Frische Bindung, READY-Generation und Storagegate prüfen. `PRAGMA encoding`
   muss exakt UTF-8 liefern. Skalares Actionstatus-CASE ohne Actiontexte prüft
   ACTIVE; fehlende/fremde/nicht-ACTIVE-Aktion wird ACCESS_REVOKED.
2. Für genau drei Tabellen `actions`, `tasks`, `task_events` ausschließlich COUNT,
   Typ-/Konsistenzflags und MAX der BLOB-Bytelängen abfragen. Erfasst werden alle
   Task-/Eventfelder, auch IDs, Referenzen, nullable sourceTemplateKey und Actiontexte.
   Text/Integer/NULL-Typen, Prioritäten/Status/Eventtypen, ausschließliche
   Person-/Rollenzuweisung und DONE/completedAt-Konsistenz werden geprüft.
3. Skalare EXISTS/NOT-EXISTS-Abfragen prüfen Personenassignee, betroffene Person,
   Rollenassignee und Event→Task jeweils **innerhalb derselben Action**.
   Keine vollständigen Personen-/Rollen-/Task-/Eventlisten dabei laden.
4. Vor SQL-SUM: höchstens 65536 Zeilen insgesamt und höchstens 8 MiB je Einzeltext.
   Damit ist jede anschließende Feldsumme höchstens 2^39; Integer-SUM statt
   Double/TOTAL. Negative oder widersprüchliche Aggregate sind INVALID_DATA,
   echte SQL-Fehler STORAGE_FAILURE. Gültige Übergröße verweigert kontrolliert.
5. Counts, Feldanzahlen, vollständige UTF8-Bytes und Kapazitäten mit checked Long-
   Add/Mul in den vorhandenen `ActiveWorkspaceBudget` übernehmen. Sowohl retained
   als auch transition müssen vor der ersten Payloadquery zugelassen sein.
6. Erst danach alle Rows über deterministisches `ORDER BY rowid LIMIT 32` und
   gebundene Keyset-Fortsetzung laden, ohne OFFSET oder Abschneiden. NULL-Start
   erhält auch Long.MIN_VALUE-Rowids; keine Rowid-Inkremente mit Overflow.
   Textspalten liefern Länge und durch CASE begrenztes `CAST(TEXT AS BLOB)`.
   Vor `getBlob` erneut Gate, Bindung, Länge und NULL prüfen.
7. Striktes UTF8-Decoding mit REPORT, tatsächliche BLOB-Bytes und UTF16-Länge
   nachprüfen. Für gültiges UTF8 ist Byteanzahl eine konservative Obergrenze der
   UTF16-Einheiten (einschließlich NUL und 4-Byte→2-UTF16-Supplementary-Zeichen).
   Ungültige Kodierung wird nicht ersetzt oder geschätzt. Nach jeder Tabelle
   müssen Zeilenzahl und Feldbytesummen exakt den reservierten Aggregaten entsprechen.
8. Bestehende `ActionRow.model`, `TaskRow.model`, `TaskEventRow.model` prüfen die
   Fachwerte. Erst nach vollständigem TaskBoard, Transaktions-/DB-/Driver-/Lease-
   Cleanup folgt Rückgabe über das originale `StorageContext.publish` mit nochmals
   aktueller Bindung. Ein späterer UI-Aufrufer benötigt weiterhin sein eigenes
   aktuelles Publicationgate; ADMITTED ist keine dauerhafte Freigabe.

Es gibt keinen `TaskDao.list()`-/`events()`-Vollread vor Admission, keine vollständige
Zwischen-Rowliste, keinen stillen Prefetch, zweiten Actionbestand, Retry oder
unbeschränkten Ersatzpfad. Bei verweigerter Admission lief in den entsprechenden
Hosttests **keine Payloadquery und kein BLOB-Fetch**; nicht erst nachträglich
budgetierte Vollmaterialisierung. SQLite muss zur Aggregation intern lesen:
dies ist keine Garantie über sämtliche nativen SQLCipher-/Enginearbeitspuffer.

### 39.3 Task-/Event-Semantik

**GETESTET AUF HOST:** Vollständige Task-/Eventfelder einschließlich nullable
Assignee-/Betroffenenreferenzen, dueAt, sourceTemplateKey, Zeitwerten und completedAt.
TaskRow nutzt unverändert TaskRules: getrimmter nichtleerer Titel bis 120
Unicode-Codepoints, exklusive Zuständigkeit, gültige Enums und DONE/completedAt.
Der neue Reader ändert keine Statusübergänge oder Schreibregeln.

Alle Events der gebundenen Action werden erhalten; keine Historienobergrenze
mit stillem Abschneiden. Nicht zulassbare Gesamtmengen liefern kein Teilboard
und kein EMPTY. Events werden zunächst in Rowidfolge gelesen und anschließend
stabil nach occurredAt sortiert: gleiche Semantik wie vorhandenes
`TaskDao.events ORDER BY occurredAt,rowid`, auch bei gleichen Zeitwerten.

`TaskDao.list` definiert bisher **kein ORDER BY**. Der bounded Pfad liefert Tasks
in deterministischer Rowidfolge; Gleichheit wird deshalb nach Identität/Inhalt
und den vorhandenen TaskBoard-Projektionen geprüft, nicht gegen einen zufälligen
Queryplan. `active`, `closed`, `affectedCount` und TaskRules.order bleiben
unverändert. Closed bleibt nach updatedAt absteigend sortiert; eine bisher nicht
vertraglich definierte Gleichstandsreihenfolge wird nicht als Garantie erfunden.

Doppelte Eventidentität wird vom Schema-Primärschlüssel abgelehnt. Unbekannte
Eventtypen, falsche Feldtypen und fehlende/actionfremde Taskreferenzen werden
INVALID_DATA. Wiederkehrende Eventtypen und gleiche Zeitwerte bleiben vollständig
erhalten: Der vorhandene Reader definiert **keine zusätzliche historische
Statusfolgenprüfung**. Insbesondere erfindet dieser Slice keine Chronikreparatur,
Deduplikation oder neue Ereignisregeln, die gültige vorhandene Reads verändern.

### 39.4 Gemeinsames Budget: Voraussetzung und ausdrückliche Integrationssperre

**IMPLEMENTIERT:** Konservative Reservierung über unveränderte Budgetgewichte:
4/16-fache Text-/Feld-/Kapazitätsallowances für retained/transition, einschließlich
Domainobjekten, Strings, UTF16, BLOB-/Decoder-/Trimkopien, Arrays, Row-/Builderlisten,
Referenzen sowie Sortier-/Projektionskapazität. Keine Deduplikationsrabatte.
Beide Kostenobergrenzen werden synchron vor Materialisierung berechnet und
versiegelt; kein threadgebundener Ledger wird über suspendierende Roomqueries
mitgeführt. Mengenprüfung danach beweist Vollständigkeit, nicht das Seal allein.
`BoundedTaskReservation` weist die beiden **disjunkten Units** zusätzlich zu den
Charges aus; sie lassen sich in demselben Scopeledger mit weiteren Units belasten.
Der Hosttest bestätigt identische Charges bei Replay und sticky Verweigerung bei
zusätzlicher Überbelegung. Diese Metadaten halten keine Payload/Keys/DAOobjekte.

**NICHT INTEGRIERT / BLOCKER:** Der Einstieg ist ausdrücklich ein eigenständiger
Testreader ohne schon gehaltenen Kernbestand. Seine lokalen 8 MiB retained plus
maximal eine 8-MiB-transition sind **keine weiteren freien 8 MiB** neben dem
Personen-/Funktionsreader. Eine gemeinsame Snapshotintegration ist gesperrt.
Rückgabe-Units erst nach dem Laden zu addieren wäre keine gemeinsame Admission!

Vor Zusammenführung erforderlich: vorgelagerte Metadaten-/Reservierungsphase für
Personen/Funktionen/Tasks/Events derselben Action und konsistenten Version, alle
Units in **einem** Scopebudget vor jeder Payloadmaterialisierung zulassen, höchstens
eine gemeinsame begrenzte Übergangsrepräsentation, eindeutiger Payload-/Formular-
Owner und Buchhaltung aller alten/neuen/abgeleiteten Kopien. Die vorhandene
Personenlesestrecke weist bisher nur skalare Charges aus; ihr vorgelagerter
komponierbarer Admissionvertrag fehlt ebenso wie der gemeinsame Orchestrator.
Es wird hier kein zweiter Store oder scheinbarer Shared-Owner eingeführt.

Beliebig viele vom Testaufrufer festgehaltene Rückgaben oder TaskBoard-getter-
Kopien sind nicht global gebucht; deshalb keine Freigabe für kombinierte längere
Vorhaltung. Engineering-Charges sind weiterhin keine harte ART-/Native-/App-Heap-
oder OOM-Garantie; Kopierallowances und Laufzeitretention benötigen Kalibrierung.

### 39.5 Revoke, Fehler und Ressourcen-BLOCKER

**GETESTET AUF HOST:** Revoke an Metadaten-/Admission-/Row-/Text-/Completegrenzen,
Generation-/Binding-/Berechtigungswechsel und späte Publication nach Cleanup
liefern keine Payload. Admissionverweigerung ist BUDGET_REFUSED ohne BLOCKED;
inkonsistente Daten INVALID_DATA mit zentralem Blocked-Verhalten, SQL-/Lesungs-/
Cleanupfehler STORAGE_FAILURE. Cleanupfehler übersteuern sowohl erfolgreiche Reads
als auch vorherige Budgetverweigerung. Keine Teil-CONTENT-Publication, kein
leeres Ergebnis aus Teilbestand und keine automatische Wiederholung.

Statements enden am vorhandenen `usePrepared`-Scope; Transaktion, Room/Driver und
Key-Lease laufen durch originale finally-Grenzen, auch bei Exception/Revoke.
Der SQLite-Hostadapter prüft null offene Statements vor Publication und bei
Harnessende sowie Prozessclose. Dies ist **kein Android-Cursorfreigabenachweis**.

**WEITERHIN BLOCKER:** SQLCipherStatement.close schließt den gehaltenen Cursor
nicht ausdrücklich; weder B2Driver noch SQLCipher-Adapter wurden geändert.
Statementende und Transaktionsende beweisen keine Cursor-/CursorWindowfreigabe.
DB-/Driver- und Leaseende bleiben maßgebliche originale Cleanupgrenzen; ihre
vollständige native Ressourcenwirkung, insbesondere über viele Batches und bei
Ablehnung/Exception, ist für diesen neuen Pfad nicht am Gerät nachgewiesen.
Die vier früheren Android-PASS aus Abschnitt 38 sind unveränderte begrenzte
Nachweise des Personen-/Funktionspfads und heben diesen BLOCKER nicht auf.

### 39.6 Konkrete Testergebnisse und offene Androidprüfung

**GETESTET:** 25 neue `BoundedTaskStoreTest`-Hostmethoden gegen echtes SQLite
mit Schema 11 aus der vorhandenen Repositoryschemadefinition, keine Fake-
Repositoryantworten. Darunter Vergleich mit tatsächlich ausgeführten bisherigen
DAO-SQL-Abfragen und Domainprojektionen; kleine/leere ACTIVE-Boards, fremde/
nicht-ACTIVE-Aktionen, Referenzen, 45 Tasks/99 Events über mehrere Batches,
2000 Tasks bzw. Events als Ablehnungsfälle, sehr lange Einzeltexte und NUL-Suffix,
Supplementary Unicode/NUL, ungültiges UTF8, Enums/Typen/Completion/Assignee,
doppelte Identität und wiederkehrende Events, exakte 8-MiB-transition-Grenze plus
nächstes Byte, Reservoir-Replay/Überbelegung, Count-/MAX-Gates vor SUM, Long-Mul-/
Addschutz des Budgetvertrags, echter SQLite-SUM-Overflow, extreme Rowids,
Revoke-/Binding-/Publication- und Exception-/Cleanupfälle.

Abschlusslauf offline: **202 App + 173 Data + 4 Security = 379 bestanden,
0 Fehler, 0 Skips**. Data enthält weiterhin die 22 Personen-/Funktionsreaderfälle,
zwei tatsächlichen Treiberdiagnosen und bestehende SessionCoordinator-/B2-Key-/
B2-Session-/Dokumentregressionen. App enthält 2A-1-Gültigkeit/Budget, historische
Mutation-/Budget-Proofs und Workspace-/ACTIVE-/ViewModel-Regressionen.
Architekturprüfung, Produkt-Kotlin, Produkt-/Demo-Manifestprüfungen und
`:data:compileDebugAndroidTestKotlin` mit bestehendem isoliertem Init-Skript
bestanden, alles `--offline`, keine neuen Dependencies oder Schemaänderung.

Ein erster fokussierter Lauf hatte 20/21 bestanden: Der Test zählte bei
Berechtigungswechsel fälschlich keinen Eintritt in den zentralen publish-Callback,
obwohl dessen inneres Bindinggate die Payload korrekt ablehnte. Die Zählassertion
wurde präzisiert; keine Readerkorrektur oder abgeschwächte Payloadassertion.
Anschließend 21/21 und nach Erweiterung im Abschlusslauf 25/25 bestanden.

**NUR OFFLINE KOMPILIERT, NICHT AUSGEFÜHRT:** Vier neue `BoundedTaskRoomTest`-
Methoden für spätere isolierte SQLCipher-/Room-Ausführung: Vergleich mit realem
Taskrepository samt Events/abgeleiteten Ansichten (100 Tasks, nach Transition
101 Events), Nicht-ACTIVE/fehlend, Publication/Revoke und Wiederholungs-/FD-
Toleranz plus Übergrößenverweigerung. Keine APK gebaut/installiert, kein Gerät
kontaktiert, keine Instrumentation oder Benchmarkkampagne ausgeführt. Der frühere
eingefrorene Vier-Test-APK-Nachweis gilt nicht automatisch für diese neuen Tests;
ein späteres Artefakt und sein exakter Runnerumfang benötigen eigene Prüfung/
Freigabe. Noch keine Android-Laufzeit-PASS für den Taskreader.

### 39.7 Voraussetzungen für Snapshotintegration und vollständige Dateiliste

**BLOCKER:** Gemeinsame Admission/Ownership aus 39.4, Cursor-/Ressourcennachweis,
Task-Androidlauf und weitere reale Unicode-/Grenz-/Fehlerpfadabdeckung,
Budgetkalibrierung; vertrauenswürdige Scope-/Permission-/Statusversionierung und
originaler synchroner Revoke-Lifecycle. ActiveWorkspaceValidity ist weiterhin
nicht verdrahtet. Readtickets und Mutation-/Load-Epochs müssen am späteren
Adapter mit zentralen Gates verbunden werden, ohne neue Sessionautorität.

Alle Mutations-/Outcome-/Recoveryhooks aus 35.5 bleiben offen: Task+Event-Commit
atomar und genau einmal, BEGIN vor Sideeffect, sichere Commit-/Rollback-/UNKNOWN-
Unterscheidung, kein stale Patch/Write-Retry, Lock-/Publicationrennen, Actionstatus,
Personen-/Rollen-/Referenzänderung, Dokument-/Maintenancegrenzen. Personrename
bleibt ohne universellen CAS; parallele/stale Personen-, Rollenplan- und Taskeditoren
benötigen begrenzte Owner/Tokens und reale Konfliktvergleiche. Keine Writeport-
oder Recoveryänderung in diesem Slice. Erst nach unabhängiger Prüfung und allen
Nachweisen kann Snapshotintegration gesondert beauftragt werden.

Vollständige geänderte Dateiliste (sieben Dateien):

- `domain/src/main/kotlin/org/pag/domain/BoundedTaskRead.kt`
- `data/src/main/kotlin/org/pag/data/product/BoundedTaskStore.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductBoundedTaskReader.kt`
- `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt`
- `data/src/test/kotlin/org/pag/data/product/BoundedTaskStoreTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/BoundedTaskRoomTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

SessionCoordinator und 180-s-absolut/30-s-READY-idle, StorageAccess, Preflights,
SessionDbKeyOwner, B2Driver, SQLCipher/Tink, Leadership/P3b, Dokument-Recovery/
DocumentNamespaceLane/PDF-Isolation, Schema 11 und Raw-Key-Vertrag unverändert.
Keine neuen Berechtigungen, Klartextpersistenz, echten Produktdaten oder Credentials.
Installierte Produkt-App und manuelle Test-App unangetastet. Keine APKs, Schlüssel,
DBs, Rohlogs, persönlichen Pfade oder Gerätekennungen im Commit. Regulärer WIP-
Commit/Push, unabhängiger Remote-SHA-Abgleich, danach STOPP bis zum unabhängigen
GitHub-Review. Kein Force-Push, Merge, Release, Produktupdate oder weitere Integration.

## 40. Stufe 2A-2b – Android-Laufzeitvorbereitung

### 40.1 Basis, unveränderte Methoden und Artefakt

**VORBEREITET, KEIN GERÄTEZUGRIFF:** Lokaler Branch und GitHub-Remote vor Änderungen
unabhängig identisch bestätigt: `p11-performance-optimization`,
`25d988e3186f56c828b03bdf82ae731b7957e97d`; Arbeitsverzeichnis sauber.
Die vier vorhandenen Methoden in `BoundedTaskRoomTest` wurden unverändert geprüft:

1. `boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews`
2. `nonActiveAndMissingAreDenied`
3. `publicationAndRevokeCannotReturnTaskPayload`
4. `oversizeTemplateRefusedAndRepeatedReadsRemainConsistent`

Keine Assertion-, Androidfixture- oder Produktänderung. Genau einmal offline
`:data:assembleDebugAndroidTest -I tools/p11bounded/prepare.init.gradle --offline`
ausgeführt; isolierte Data-Library-Instrumentation, **eine APK**, kein separates
Ziel-App-APK. Gebautes APK separat eingefroren; das ältere eingefrorene ACTIVE-
Artefakt wurde nicht ersetzt. SHA256 des finalen Task-Artefakts:

`72990b3d301fc80c5663154a7e5e41491b718c6c059effbe5120c9185b8f986d`.

Ein Neubuild benötigt neue Verifikation und gegebenenfalls neue Freigabe; keine
unbemerkte Ersetzung der eingefrorenen Kopie. APK/Schlüssel/Rohlogs bleiben lokal.
Keine Installation, Deinstallation, Instrumentation oder sonstiger Gerätezugriff.

### 40.2 Tatsächliche APK-Identität und kryptografische Prüfung

**VERIFIZIERT am gebauten Manifest mit AAPT:**

| Eigenschaft | Ergebnis |
|---|---|
| Paket/Testpaket | `org.pag.data.unlockprobe` |
| Instrumentation-Target | `org.pag.data.unlockprobe`, Self-Target |
| Runner | `androidx.test.runner.AndroidJUnitRunner`, genau eine Instrumentation |
| Application / Label | Standard `android.app.Application` / `PaG P11 Bounded Tests` |
| Permissions / Queries | keine |
| Activities / Services / Provider / Receiver / Aliase | keine |
| Backup / Cleartext | deaktiviert |
| Debuggable / minSdk / targetSdk | ja / 30 / 37 |
| Native Library | ARM64 `libsqlcipher.so` enthalten |

Kryptografische APK-Signaturprüfung mit Apksigner bestanden; genau ein Signer
entspricht dem **bereits zuvor unabhängig bekannten lokalen Zertifikatsnachweis**.
Kein aus der zu prüfenden APK übernommener Vertrauensanker. Bestehender Keystore-
Dateihash gegenüber seinem unabhängigen Nachweis unverändert, keine neuen Keys.
Exakter APK-Hash vor/nach Prüfung identisch. DEX enthält Testklassendeskriptor und
Symbole aller vier Taskmethoden; das ist Symbolpräsenz, kein Android-Test-PASS.
Andere Testklassen bleiben im Data-Test-APK enthalten, werden aber durch den
späteren exakten Selektor nicht ausgeführt.

`verify_apk.py` besitzt jetzt die geschlossenen Modi `active` und `tasks`.
Default `active`, ursprüngliche Klasse/Methoden und sämtliche Manifest-/Signatur-
Gates bleiben erhalten. `tasks` hat ausschließlich die vier oben genannten
Methoden; ein optional übergebener Selektor muss bytegenau dem festen Selektor
entsprechen. Keine beliebige Klassen-/Package-/Suiteauswahl oder zusätzliche
Methode. Der Prüfer besitzt weiterhin keine Gerätefunktion. Das neue Task-APK
bestand den Taskmodus; das ältere eingefrorene ACTIVE-APK bestand erneut den
unveränderten ACTIVE-Prüfumfang mit seinem ursprünglichen Hash.

### 40.3 Konkreter Fixture-BLOCKER vor einem sinnvollen Abnahmelauf

**AUF HOST BESTÄTIGT, KEIN PRODUKTFEHLERNACHWEIS:** Die Fachannahme 100 ACTIVE-
Tasks mit zunächst 100 CREATED-Events ist korrekt. OPEN→IN_PROGRESS ist zulässig
und erzeugt ein STARTED-Event: danach 100 Tasks/101 Events. Task-/Event-UUIDs
belegen jeweils 36 ASCII-Bytes; nullable Felder werden im Budget als mögliche
Allokationen belastet. Die vorhandene Fixture überschreitet allerdings bereits
**vor** der zusätzlichen Templatekennung das unveränderte Übergangsbudget:

| Zustand | Rows inkl. Action | Textfelder inkl. nullable | UTF8-Bytes | Transition-Charge |
|---|---:|---:|---:|---:|
| vor Transition: 100 Tasks / 100 Events | 201 | 1305 | 16250 | 8410240 Bytes |
| nach Transition: 100 Tasks / 101 Events | 202 | 1309 | 16346 | 8447616 Bytes |

Limit 8388608 Bytes; Überschreitung 21632 beziehungsweise 59008 Bytes. Aus den
aktuellen Reader-/Budgetgewichten folgt
`rows * 20608 + fields * 2272 + bytes * 80 + 3072` für die Übergangsrepräsentation.
Neue nichtproduktive Hostdiagnosen bilden die relevanten Fixturefelder mit
synthetischen gleichlangen UUIDs auf echtem SQLite/schema 11 nach, prüfen die
SQL-Aggregate und belegen am tatsächlichen BoundedTaskStore für beide Zustände
BUDGET_REFUSED **vor Payloadquery und BLOB-Fetch**, null offene Statements und
kein Storage-BLOCKED. Keine Fehlklassifikation als Korruption.

Die übergroße sourceTemplateKey-Kennung aus 200000 `x` ist nach den aktuellen
TaskRules/TaskRow-Regeln zulässig; ihr Titel bleibt unter 120 Codepoints. Sie
benötigt keine neue Freitext-/Credentialregel. Die unveränderte Budgetverweigerung
lässt sich in diesem Androidfall aber nicht isoliert auf sie zurückführen,
weil bereits der Ausgangsbestand verweigert wird.

**Folgen für die vier unveränderten Methoden – abgeleitet, nicht am Gerät gemessen:**

- Methode 1 castet nach Transition auf Value. Für diese Fixture ist bereits die
  Budgetverweigerung belegt; ein entsprechender Castfehler wäre ein
  Testvoraussetzungsproblem, kein Nachweis defekter Tasksemantik.
- Methode 2 prüft Nicht-ACTIVE/fehlend; aus der Budgetrechnung folgt kein solches
  Value-Voraussetzungsproblem. Androidresultat trotzdem weiterhin offen.
- Methode 3 erwartet zuerst ACCESS_REVOKED bei verweigerter Publication; die
  vorgelagerte Budgetverweigerung erreicht das Publicationgate nicht und passt
  daher nicht zur bestehenden Erwartung. Weitere Assertions könnten unerreicht bleiben.
- Methode 4 kann identische Rejected-Werte vergleichen; der FD-Check wäre kein
  Nachweis erfolgreicher vollständiger Reads. Die abschließende Budgetablehnung
  weist nicht spezifisch die übergroße Templatekennung als Ursache nach.

**BLOCKER:** Das unveränderte Artefakt ist technisch prüfbar und als diagnostischer
Vier-Methoden-Lauf reproduzierbar, aber kein erwartbar grüner Abnahmelauf. Vor
Gerätefreigabe muss diese Grenze ausdrücklich bewertet werden. Ein sinnvoller
grüner Nachweis benötigt eine getrennt geprüfte Testvoraussetzungsentscheidung;
keine Assertions abschwächen, kein Budget erhöhen und keine Testdatenprobleme
als Produktfehler umdeuten. Die Offline-Kompilation aus 39 bewies diese fachliche
Budgetvoraussetzung nicht; Abschnitt 40 ergänzt den jetzt konkreten Befund.

### 40.4 Offline-Ergebnisse, Laufplan und Abbruchbedingungen

**GETESTET:** Zehn Python-Prüfertestmethoden bestanden. Enthalten sind falsche
Produkt-/manuelle Paketkennung, falsches Target/Runner, zusätzliche Permissions/
Komponenten/Queries, Backup/Cleartext/Custom-Application-Abweichungen, fehlende/
falsche/mehrere Signer sowie beide exakten Modi. Neue Payload-/Selektortests
verweigern jede einzeln fehlende Methode in beiden Modi, fehlende/falsche Klasse,
fehlendes ARM64, Teil-/Extra-/Doppelauswahl, vertauschte Reihenfolge und Kreuzwahl
zwischen ACTIVE/Tasks. Bestehende ACTIVE-Grenzen wurden nicht abgeschwächt.

**GETESTET:** 51 Data-Hosttests bestanden, 0 Fehler/Skips: zwei neue
Fixture-Voraussetzungsdiagnosen, 25 bestehende Taskreaderfälle, 22 bestehende
Personen-/Funktionsreaderfälle und zwei tatsächliche SQLCipher-Treiberdiagnosen.
Offline-APK-Build einschließlich Androidtest-Kompilation und Architekturprüfung
bestanden. Keine neuen Abhängigkeiten, keine zusätzliche Geräte-/Benchmarkkampagne.
Die vier Androidmethoden sind ausschließlich gebaut, **nicht ausgeführt**.

Der [exakte einmalige Task-Laufplan](../tools/p11bounded/TASKS.md) enthält den
vollständigen festen Vier-Methoden-Selektor, den read-only Gateaufruf mit Taskmodus
und eingefrorenem Hash sowie alle zukünftigen Installations-/Ergebnis-/Cleanup-
Schritte. **BENÖTIGT NEUE AUSDRÜCKLICHE FREIGABE:** tatsächliche Installation und
Ausführung; eine Paketentfernung muss ebenfalls ausdrücklich eingeschlossen sein.

Vor späterer Installation: Fixture-BLOCKER bewerten, genaues autorisiertes Gerät
prüfen, nur Installations-/Signaturmetadaten der drei betroffenen Pakete lokal
sichern. Bei bereits vorhandenem temporären Paket STOPP, kein Ersetzen/Leeren.
Bei fehlender APK, Hash-/Signatur-/Manifest-/Symbol-/Selektorabweichung STOPP,
kein Ersatzbuild oder Fallback. Nur die eine neue APK ohne `-r`; Self-Target und
unveränderte geschützte Apps danach prüfen. Genau ein Lauf, kein Retry bis grün,
keine weiteren Methoden und kein connectedAndroidTest. Pro Methode Originalresultat
und Runnerabschluss erfassen. Bereinigung ausschließlich des nachweislich neu
installierten, erneut identifizierten temporären Pakets bei entsprechender
Freigabe. Produkt-/manuelle P11-App niemals installieren, ersetzen, löschen oder
leeren; abschließend Metadatenvergleich. Derzeit kein Gerätebestand abgefragt.

### 40.5 Ressourcen- und Integrationsgrenzen; Dateiliste

**WEITERHIN BLOCKER:** SQLCipherStatement.close schließt den Cursor nicht
explizit. Keine Änderung an SQLCipher oder B2Driver. Statement-/Transaktionsende,
DB-/Driver-/Lease-Cleanup, FD-Toleranz und offene native Cursor-/CursorWindow-
Lebensdauer bleiben getrennte Nachweise. Ein späteres FD-PASS löst den statischen
Befund nicht. Insbesondere die aktuelle baseline-Budgetverweigerung begrenzt die
Aussage des vorbereiteten FD-Tests zusätzlich. Fixture-Verzeichnislöschung prüft
weiterhin ihren Boolean nicht; Crash kann Reste im temporären Paket hinterlassen.

Gemeinsame Scope-Admission und Payload-/Formularownership, Budgetkalibrierung,
Task-Androidlauf samt weitergehender Grenz-/Fehlerabdeckung, Mutation-/Outcome-/
Recovery-/Editorhooks und vertrauenswürdige Permission-/Statusversionierung aus
39 bleiben offen. Keine Snapshotintegration, RAM-Hits, Writerhooks, Änderungen an
Sicherheitsgates/Schema oder neue Performanceaussagen. Kein Gerät, keine echten
Daten/Credentials, keine Veränderung installierter Apps.

Vollständige geänderte Dateiliste:

- `tools/p11bounded/verify_apk.py`
- `tools/p11bounded/test_verify_apk.py`
- `tools/p11bounded/README.md`
- `tools/p11bounded/TASKS.md`
- `data/src/test/kotlin/org/pag/data/product/BoundedTaskFixturePreconditionTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

Nur Test-/Prüfer-/Berichtsdateien, keine Produktquellen, Androidtestmethoden,
APKs, Schlüssel, Rohlogs, Gerätekennungen oder persönlichen Pfade im Commit.
Regulärer WIP-Commit/Push mit unabhängigem Commit-/Remote-SHA-Abgleich; danach
STOPP bis zum unabhängigen GitHub-Review. Kein Force-Push, Merge oder Release.

## 41. Stufe 2A-2b – Korrigierte Android-Testvoraussetzungen

### 41.1 Ausgangslage und enge Änderung

**IMPLEMENTIERT AUSSCHLIESSLICH IN TESTINFRASTRUKTUR:** Basis lokal und auf
GitHub vor Änderungen unabhängig bestätigt:
`6e6b0cf21436c8511ec4ed218f799688a437535f`, Branch
`p11-performance-optimization`, Arbeitsverzeichnis sauber. Der unabhängig
bestätigte Fehler aus 40 betrifft die Testvoraussetzung: Schon 100 Tasks mit
100 CREATED-Events benötigen mehr als das Übergangsbudget. Das damalige APK
bleibt unverändert eingefroren und wird nicht als Gerätelauf freigegeben.
Keine Aussage aus 40 wird rückwirkend in ein Android-PASS umgedeutet.

`P11SyntheticFixture` akzeptiert jetzt einen expliziten letzten optionalen
Parameter `taskCountPerAction`; Default **weiterhin 100**. Negative Werte werden
vor Dateisystemarbeit abgewiesen. Nur die vier Task-Androidmethoden wählen
explizit 40. Bestehende Benchmark-/manuelle-/ACTIVE-Testaufrufer behalten ihren
bisherigen Default; keine Änderung ihrer Aufrufstellen. Innerhalb dieser Fixture
wird die Auswahl für die bestehenden PREPARATION-/ACTIVE-Taskbestände verwendet;
COMPLETED bleibt unverändert ohne angelegte Tasks.

Ein optionaler Testcallback zählt ausschließlich im Debug-StorageContext-Wrapper
von `boundedTasks` den Eintritt in dessen Publicationmethode. Kein neuer Hook in
Produkt-StorageAccess, Writer, Session oder Reader. Default ist ein leerer Callback.

### 41.2 Positive Fixture und aussagekräftige Assertions

**AUF HOST GETESTET:** Gleiche Actionmetadaten, 36-Byte-UUIDs und Tasktitel wie
in der synthetischen Androidfixture, aber explizit 40 Aufgaben. Die vollständige
Chronik umfasst zunächst 40 CREATED-Events. Eine zulässige OPEN→IN_PROGRESS-
Transition erzeugt genau ein STARTED-Event; danach 40 Tasks/41 Events.

| Zustand | Rows inkl. Action | Textfelder inkl. nullable | UTF8-Bytes | Retained-Charge | Transition-Charge |
|---|---:|---:|---:|---:|---:|
| 40 Tasks / 40 Events | 81 | 525 | 6530 | 849184 Bytes | 3387520 Bytes |
| 40 Tasks / 41 Events | 82 | 529 | 6626 | 858528 Bytes | 3424896 Bytes |

Beide Reservierungen bleiben unter jeweils 8388608 Bytes. Die Zahlen sind
unveränderte konservative Engineering-Charges, keine gemessenen ART-/Native-
Heapgrößen. Es wurde weder das 8+8-MiB-Budget erhöht noch eine andere Produktregel
verändert. Die Hosttests bestätigen die tatsächlichen Readercharges.

Die exakt vier bisherigen Androidmethodennamen bleiben erhalten:

1. `boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews`: Vor **und** nach
   Transition muss der bounded Reader Value liefern und gegen das echte bestehende
   TaskRepository bestehen. Unverändert vollständige Taskinhalte, sämtliche Events
   einschließlich Reihenfolge und TaskBoard-Ansichten; zusätzlich Count-/ID- und
   Exactly-once-Assertions für das STARTED-Event. Vorherige Events bleiben erhalten.
   40 Tasks und 40/41 Events liegen jeweils über 32 und erfordern mehrere Batches.
2. `nonActiveAndMissingAreDenied`: PREPARATION/COMPLETED/fehlend weiterhin abgelehnt.
3. `publicationAndRevokeCannotReturnTaskPayload`: Erfolgreicher kompletter Read
   vor der Ablehnung, dann beobachteter Eintritt in die Publicationgrenze **genau
   einmal**, dort verweigert. Danach wieder kompletter Read; Revoke verhindert
   weitere Payload und einen weiteren Publicationeintritt. Frühere Budgetablehnung
   kann diese Assertions nicht erfüllen.
4. `oversizeTemplateRefusedAndRepeatedReadsRemainConsistent`: Ein vollständiges
   erfolgreiches Board als Ausgangsbasis; alle drei Wiederholungen müssen ebenfalls
   Value sein und dasselbe komplette Board liefern. Erst danach erfolgreiche
   einmalige Erstellung des Tasks mit fachlich zulässiger 200000-`x`-Templatekennung,
   anschließend BUDGET_REFUSED. FD-Toleranz vergleicht jetzt erfolgreiche Reads,
   keine gleichen Rejected-Werte. Keine Assertion abgeschwächt, nur explizite
   Bestandszahlen angepasst und positive Voraussetzungen/Beobachtung ergänzt.

**NUR OFFLINE KOMPILIERT:** Diese Assertions sind noch nicht auf Android ausgeführt.
Die Hosttests ersetzen weder den echten Android-Repositoryvergleich noch dessen
Treiber-/Ressourcenwirkung. Kein Android-PASS behauptet.

### 41.3 Getrennter großer Negativnachweis und Größenkonflikt

**WEITERHIN AUF ECHTEM SQLITE BESTÄTIGT:** Die ursprünglichen 100 Tasks mit
100 beziehungsweise 101 Events bleiben als separater Host-Negativfall erhalten.
Transition-Charges 8410240 beziehungsweise 8447616 Bytes; Ergebnisse jeweils
BUDGET_REFUSED **vor Payloadquery, BLOB-Fetch und vollständiger Materialisierung**.
Null offene Hoststatements und kein Storage-BLOCKED wegen gültiger Übergröße.
Kein automatischer Retry, Budgetwachstum oder unbeschränkter Fallback.

**OFFENER PRODUKTANFORDERUNGSKONFLIKT:** Realistische Bestände von **200 Personen
und 1000 Aufgaben** sind durch die kleine positive Fixture nicht gelöst. Schon
1000 Tasks allein ohne Events überschreiten mit den bestehenden Engineering-
Gewichten das retained Budget, sogar bei einer unteren Rechnung ohne Textbytes;
Personen/Funktionen, Chronik und temporäre Kopien kommen hinzu. Die gemeinsame
Scope-Admission ist weiterhin offen. Es gibt weder einen Nachweis schneller
Navigation solcher Bestände noch eine produktive Snapshotfreigabe. Der heutige
Produktpfad bleibt unverändert; kleinere Testdaten sind kein Erreichen seines
Performanceziels. Budget-/Kopierkalibrierung und ein tragfähiger Größenvertrag
brauchen eine gesonderte Architektur-/Sicherheitsentscheidung und reale Nachweise,
keine stillschweigende Budgeterhöhung oder Datenabschneidung.

### 41.4 Konkrete Offline-Prüfungen

**GETESTET:** Fünf `BoundedTaskFixturePreconditionTest`-Methoden gegen echtes
SQLite mit Schema 11: kleiner positiver Bestand mit kompletter geordneter Chronik,
Transition und Event genau einmal, je fünf Payloadqueries pro Read (eine Action-,
zwei Task- und zwei Eventbatches), tatsächliche Charges, isolierte Übergröße nach
vier erfolgreichen Reads, verweigerte Publication erst nach Materialisierung/
Cleanup und Revoke ohne weitere Reads; dazu getrennte 100-Task-Verweigerungen
und unveränderte Domainzulässigkeit der großen Templatekennung. Der Host schreibt
synthetische SQL-Transaktionen mit den bestehenden TaskRules; der Androidtest
benutzt weiterhin das echte TaskRepository für Transition und Erstellung.

Abschlusslauf **offline: 202 App + 178 Data + 4 Security = 384 bestanden,
0 Fehler, 0 Skips**. Enthalten: alle 25 Taskreaderfälle, 22 ACTIVE-Readerfälle,
zwei tatsächliche SQLCipher-Treiberdiagnosen, Gültigkeits-/Budgetgrundlagen und
historische Proofs sowie Session-/B2-Key-/Dokument-/Workspace-/ACTIVE-/ViewModel-
Regressionen. Zehn bestehende APK-Prüfertests ebenfalls bestanden.

Ein erster fokussierter Lauf hatte 4/5 bestanden: In der neuen positiven
Chargeassertion waren Erwartungswerte falsch übertragen. Die Werte wurden mit
der unveränderten Formel nachgerechnet und korrigiert; keine Reader-/Budget-
änderung und keine Lockerung der Value-/Vollständigkeitsassertionen. Abschlusslauf
5/5 für diese Suite. Fehllauf ist kein erfolgreicher Nachweis.

Produkt-Kotlin, Architektur- und Produkt-/Demo-Manifestprüfungen sowie die gesamte
Data-Androidtest-Kompilation und der isolierte Data-Test-APK-Build bestanden.
Vorhandene Infrastruktur `tools/p11bounded/prepare.init.gradle`, alles `--offline`;
keine neuen Abhängigkeiten, kein Gerät, keine Instrumentation oder Benchmarkkampagne.

### 41.5 Neues getrenntes APK und künftiger Laufplan

**GEBAUT UND VERIFIZIERT, NICHT INSTALLIERT:** Das neue APK wurde separat
unter einem neuen Artefaktnamen eingefroren. Neuer SHA256:

`e2965c50aea142da8157bd914964b0a9388bbb861fbd739df26dd04d8c2bf115`.

Der alte Task-Hash
`72990b3d301fc80c5663154a7e5e41491b718c6c059effbe5120c9185b8f986d`
bezeichnet weiterhin das **unveränderte alte** Artefakt, nicht das korrigierte.
Auch ältere ACTIVE-/manuelle APKs und der vorhandene Keystore-Dateihash wurden
unverändert bestätigt. Kein Keystorewechsel und kein neues Credential.

Tatsächliches Manifest mit AAPT und bestehendem Taskmodus des APK-Prüfers geprüft:
Paket/Test/Self-Target `org.pag.data.unlockprobe`, genau eine Instrumentation mit
`androidx.test.runner.AndroidJUnitRunner`, Standard-Application und Testlabel,
keine Permissions/Queries/Activities/Services/Provider/Receiver/Aliase;
Backup/Cleartext deaktiviert. ARM64-SQLCipher und Klassendeskriptor/Symbole exakt
der vier ausgewählten Methodennamen vorhanden. Andere Data-Testklassen bleiben
im APK enthalten und sind nicht Teil des festen Laufselektors.

Kryptografische Apksigner-Prüfung bestanden; ein Signer entspricht dem bereits
unabhängig bekannten lokalen Zertifikatsanker, nicht aus dem neuen APK als
Vertrauen übernommen. Exakter neuer APK-Hash vor/nach Prüfung bestätigt.
Ein Neubuild benötigt erneute Verifikation und neue Freigabe.

Der [aktualisierte feste Laufplan](../tools/p11bounded/TASKS.md) bezieht sich auf
den neuen Hash und unverändert genau vier Methoden. **BENÖTIGT NEUE AUSDRÜCKLICHE
FREIGABE:** Geräteinstallation und einmalige Ausführung; Bereinigung ausschließlich
des dann nachweislich neu installierten temporären Pakets ebenfalls ausdrücklich
freigeben. Kein Ersetzen eines vorhandenen Pakets, kein -r, keine weiteren Tests,
kein Retry bis grün. In diesem Auftrag keinerlei Gerätezugriff oder Paketmutation.

### 41.6 Weiterhin offene Grenzen und vollständiger Umfang

**BLOCKER:** Android-Laufzeitresultate der korrigierten Tests, separate reale
Grenz-/Unicode-/Fehlerpfadabdeckung, Cursor-/CursorWindow-Lebensdauer und Ressourcen-
kalibrierung. SQLCipherStatement.close schließt seinen Cursor weiterhin nicht
explizit. FD-Toleranz ist weder Statement-Close- noch Langzeit-FD-/Native-Leak-
nachweis. DB-/Driver-/Leaseende und tatsächliche Cursorfreigabe bleiben getrennt.
Fixture-Verzeichnislöschung prüft weiterhin ihren Boolean nicht. Kein spekulativer
SQLCipher-/B2Driverfix. Frühere vier ACTIVE-Android-PASS lösen diese Fragen nicht.

Gemeinsame Admission/Ownership, Mutations-/Outcome-/UNKNOWN-/Recoveryhooks,
Personrename-/Editor-/Permission-/Statusversionierung und realistischer
Größen-/Performancevertrag bleiben vor Snapshotintegration erforderlich.
ActiveWorkspaceBudget, ActiveWorkspaceValidity, BoundedTaskStore, BoundedActiveStore,
B2Driver, SessionCoordinator, StorageAccess und SessionDbKeyOwner unverändert;
ebenso 8+8 MiB, SQLCipher/Tink, Sessionfristen, Schema 11, Produktnavigation/-writer
und RAM-Snapshot-Lebensdauer. Keine produktive Änderung oder schnelle Navigation
behauptet. Installierte Produkt-App und manuelle P11-App unangetastet.

Vollständige geänderte Dateiliste:

- `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/BoundedTaskRoomTest.kt`
- `data/src/test/kotlin/org/pag/data/product/BoundedTaskFixturePreconditionTest.kt`
- `tools/p11bounded/README.md`
- `tools/p11bounded/TASKS.md`
- `docs/product-p11-active-workspace-assessment.md`

Nur Test-/Fixture-/Berichtsdateien. Keine APKs, Schlüssel, Datenbanken, Rohlogs,
persönlichen Pfade, Gerätekennungen oder echten Produktdaten im Commit. Regulärer
WIP-Commit/Push mit unabhängigem Remote-SHA-Abgleich; danach STOPP bis zum
unabhängigen GitHub-Review. Kein Force-Push, Merge, Release oder Produktupdate.


## 42. Stufe 2A-2b – Android-Laufzeitergebnisse

### 42.1 Freigabe und Artefaktprüfung

**AUF SAMSUNG GETESTET (2026-10-20):** Ausgangscommit
`98d50f05c34862aa3aea69b2643c591550239c0f`, Branch
`p11-performance-optimization`, vor Beginn sauberes Arbeitsverzeichnis.
Ausdrückliche einmalige Benutzerfreigabe für Installation, genau einen Lauf der
vier festgelegten Taskmethoden und anschließende Entfernung ausschließlich von
`org.pag.data.unlockprobe`. Genau ein autorisiertes Samsung ausgewählt;
keine Produktinstallation, kein Release oder Merge.

Ausschließlich die eingefrorene korrigierte APK aus Abschnitt 41 verwendet:
SHA256 `e2965c50aea142da8157bd914964b0a9388bbb861fbd739df26dd04d8c2bf115`.
Kein Neubuild. `tools/p11bounded/TASKS.md` vollständig gelesen und
`verify_apk.py --mode tasks` mit dessen unverändertem vollständigen Selektor
vor Installation erfolgreich ausgeführt. Kryptografische Signatur gegen den
bereits unabhängig vorhandenen Zertifikatsnachweis geprüft; Keystore-Dateihash
entspricht ebenfalls dem früheren unabhängigen Nachweis. Vertrauen nicht aus
der zu prüfenden APK abgeleitet.

APK-Hash, Signatur, Paketkennung und Self-Target-Instrumentation mit
AndroidJUnitRunner bestätigt; enges Manifest ohne Permissions, zusätzliche
Komponenten oder Queries, Backup/Cleartext deaktiviert, Standard-Application,
ARM64-SQLCipher sowie alle vier Methodensymbole vorhanden. Installation ohne
`-r`, keine zweite APK. Danach installierte APK zurückgelesen: Bytes/Hash exakt
identisch zum verifizierten Artefakt, damit auch dessen Signatur; Paketmetadaten
und Instrumentation-Self-Target erneut bestätigt. Kein Launcherstart.

### 42.2 Vier Einzelresultate und tatsächlicher Runnerabschluss

Genau ein `am instrument -w -r` mit dem festen Vier-Methoden-Selektor.
Kein `connectedAndroidTest`, keine zusätzlichen Tests, kein Retry, keine
Assertionänderung oder Neuinstallation.

| Methode in `BoundedTaskRoomTest` | Ergebnis |
|---|---|
| `boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews` | PASS |
| `nonActiveAndMissingAreDenied` | PASS |
| `publicationAndRevokeCannotReturnTaskPayload` | PASS |
| `oversizeTemplateRefusedAndRepeatedReadsRemainConsistent` | PASS |

Für jede dieser exakt vier Methoden je ein Startstatus 1 und Abschlussstatus 0,
keine weitere Methode, kein FAIL/ERROR/SKIP. Tatsächliche Runnerreihenfolge:
Publication/Revoke, Legacyvergleich, Übergröße/Wiederholungen, Nicht-ACTIVE/fehlend.
Regulärer Abschluss: `OK (4 tests)`, `INSTRUMENTATION_CODE: -1`, ADB-Exitcode 0;
Runnerzeit 23,858 Sekunden. Diese Zeit ist keine Produktperformancemessung.

**DURCH BESTANDENE ASSERTIONS BELEGT:** Vollständige 40-Task-Boards als Value,
Vergleich mit echtem TaskRepository einschließlich Taskinhalten, vollständiger
geordneter Eventchronik und abgeleiteter Ansichten. Vor Transition 40 CREATED-
Events, danach 41 Events mit genau einem zusätzlichen STARTED für den betroffenen
Task; vorherige Events bleiben erhalten. 40 Tasks und 40/41 Events überschreiten
jeweils die Batchgröße 32 und durchlaufen mehrere Task-/Eventbatches. Keine
separate Laufzeit-Queryzählung behauptet.

Nicht-ACTIVE und fehlende Aktion korrekt verweigert. Publicationgrenze tatsächlich
über den Callback genau einmal erreicht und dort verweigert, mit positiven Reads
vorher/nachher. Nach Revoke keine Payload und kein weiterer Publicationversuch.

Übergrößentest: vollständiger Ausgangsread und drei weitere vollständige,
identische erfolgreiche Reads; anschließend erfolgreiche einmalige Erstellung
des Tasks mit der fachlich zulässigen 200000-`x`-Templatekennung, erst danach
BUDGET_REFUSED. Keine vorzeitige Budgetablehnung als positiver Read gewertet.
FD-Toleranz nach den erfolgreichen Wiederholungen bestanden (`nachher <= vorher + 2`);
absolute FD-Zahlen werden vom Test nicht ausgegeben.

### 42.3 Ressourcen, Bereinigung und Paketstatus

Im vollständigen Instrumentationstream keine gemeldeten SQLCipher-/Roomfehler,
Runnerabbrüche oder Cleanupfehler. Die bestandenen Methoden schließen die
Fixture-`use`-Blöcke ohne gemeldete Close-Exception ab. Das beweist weder die
physische Entfernung sämtlicher Testdateien durch die Fixture noch native
Cursorfreigabe: Die Fixture prüft den Boolean ihrer Verzeichnislöschung weiterhin
nicht. Keine zusätzlichen Appdaten-/Datenbankprüfungen vorgenommen.

**STATISCHER BEFUND WEITER OFFEN / BLOCKER:**
`SQLCipherStatement.close()` schließt den Cursor nicht explizit.
Ein FD-PASS beweist keine vollständige native Cursorfreigabe, keine langfristige
CursorWindow-/FD-Freiheit und keinen Statement-Cursor-Close. DB-/Driver-/Leaseende
und native Ressourcenlebensdauer bleiben getrennt zu bewerten. Kein Treiberfix.

Vorher ausschließlich Paketmetadaten der drei freigegebenen Paketkennungen
erfasst, keine Appdaten oder Datenbanken bestehender Apps geöffnet:

| Paket | Vorher | Nach Installation / vor Entfernung | Nachher |
|---|---|---|---|
| `org.pag.prototype.a` | vorhanden | Version, firstInstallTime, lastUpdateTime und Signaturmetadaten unverändert | unverändert vorhanden |
| `org.pag.perf.p11probe` | vorhanden | Version, firstInstallTime, lastUpdateTime und Signaturmetadaten unverändert | unverändert vorhanden |
| `org.pag.data.unlockprobe` | nicht vorhanden | neu installiert, verifizierte APK-Identität und Self-Target bestätigt | entfernt, Abwesenheit bestätigt |

Vor Deinstallation temporäre Paketidentität einschließlich Version,
Installationszeiten und Signaturmetadaten erneut mit dem Zustand unmittelbar
nach Installation verglichen; identisch. Ausschließlich das in diesem Lauf
neu installierte temporäre Paket deinstalliert, Ergebnis `Success`.
Kein `pm clear`, keine andere App entfernt. Abschließender Metadatenvergleich
für beide geschützten Apps identisch zum Vorzustand. Rohbelege verbleiben lokal;
keine Gerätekennungen, persönlichen Pfade, APKs, Credentials oder Rohlogs im Git.

### 42.4 Verbleibende technische BLOCKER und Umfang

Der ausstehende Androidnachweis genau dieser vier korrigierten Methoden ist
jetzt erbracht. Offen bleiben separate reale Grenz-/Unicode-/Fehlerpfadabdeckung,
Cursor-/CursorWindow-Lebensdauer, Ressourcen- und Budget-/Kopierkalibrierung sowie
der realistische Größen-/Performancevertrag für 200 Personen/1000 Aufgaben.
Die 40-Task-Fixture löst den dokumentierten Größenkonflikt nicht.

Gemeinsame Admission/Ownership, Mutations-/Outcome-/UNKNOWN-/Recoveryhooks und
Personrename-/Editor-/Permission-/Statusversionierung bleiben vor gemeinsamer
Snapshotintegration erforderlich. Keine neue Produktperformance oder
Snapshotfreigabe, keine Änderung an Produktcode, Budgets, Schema, Abhängigkeiten,
Navigation, Writer oder Sessionregeln.

Einzige geänderte versionierte Datei:
`docs/product-p11-active-workspace-assessment.md` (dieser Abschnitt).
Kein neuer Build, da nur Ergebnisdokumentation geändert und ausschließlich das
freigegebene eingefrorene Artefakt getestet wurde. Nach Dokumentationscommit und
regulärem Push mit unabhängigem Remote-SHA-Abgleich STOPP bis zum unabhängigen
GitHub-Review; kein Force-Push, Merge, Release oder Produktupdate.

## 43. Issue #33 – Skalierbare kompakte ACTIVE-Aufgabenprojektion

### 43.1 Implementierter Slice und tatsächliche Abhängigkeiten

**IMPLEMENTIERT UND AUF ECHTEM SQLITE GETESTET (2026-10-20):** Ausgangs-SHA
`cbe9196b6ddd4a98ca3e8af6a07dc7efba30d0a6`, Branch
`p11-performance-optimization`, sauberer Ausgangszustand und Remote-SHA bestätigt.
Neue Domainverträge in `CompactActiveTasks.kt`, bounded SQL in
`CompactTaskStore.kt`, expliziter Workerzugang in `ProductCompactTaskReader.kt`
und reproduzierbare Schema-11-Hosttests in `CompactTaskStoreTest.kt`.
Kein Anschluss an ProductRuntime, ProductViewModel oder Produktnavigation.

Die Prüfung der bestehenden Abhängigkeiten ergibt:

| Bestand | Tatsächlicher Bedarf / Konsequenz für den späteren Anschluss |
|---|---|
| `BoundedActiveStore` | Vollständige Action-/Personen-/Rollen-/Operationsprojektion mit eigener bisheriger Standalone-Admission; gemeinsame Buchung noch offen. |
| `BoundedTaskStore` | Vollständiges `TaskBoard`, alle Templatekennungen und alle Events; bisheriger 100-Task-Budgetkonflikt bleibt für diesen Reader bestehen. |
| `ActiveWorkspaceBudget` | Ein retained- und höchstens ein transition-Kontingent à 8 MiB; Engineeringgewichte, keine zertifizierten ART-/Native-Größen. |
| `ActiveWorkspaceValidity` | Owner-Thread, Mutation-/Load-Epochen, einmalige Read-/Mutationtickets, Pending-/UNKNOWN-/Cleanup-/Revoke-Gates; keine Produktintegration und keine Payloadautorität. |
| `ProductViewModel` | `WorkspaceSnapshot.tasks` bleibt `TaskBoard`; `readWorkspace()` fordert es unter anderem für Hub, Personen, Aufgaben und Material an. `clearPersons()` bereinigt zusätzlich Tickets, Formulare, Auswahl und sensible Bereiche. Ein bloßer Austausch der Taskliste würde diese Verträge verletzen. |
| `TaskModel` | Auswahl/Reconcile suchen das vollständige Taskmodell im Board; Bearbeitung und Writer benötigen den vollständigen erwarteten Snapshot einschließlich Provenienz. Form-/Request-/Revoke-Lebensdauer darf nicht an einer Übersichtszeile hängen. |
| `TaskScreen` / `ActiveTasks` | `TaskBoard.active/closed`, Filter, Summary und vollständige Eventliste; nicht-lazy `forEach`. Karten suchen Personen/Rollen wiederholt in Listen. Personenbezogene Taskzahlen verwenden die vollständige Aufgabenmenge. |
| `TaskBoard` / `TaskRules` | ACTIVE-Reihenfolge HIGH/NORMAL/LOW, Fälligkeit vor null, früheste Fälligkeit, createdAt, Kotlin-UTF16-ID; CLOSED nach updatedAt absteigend. Personenfilter bedeutet affectedPersonId, unabhängig von Zuständigkeit. |

Die bestehenden Reader, TaskBoard-/TaskRules-Semantik, Writer, Schema und Proofs
wurden nicht verändert. Die neue Projektion ist ausdrücklich **kein TaskBoard**.

### 43.2 Read-, Paging- und Detailvertrag

`CompactTaskPage` enthält die exakten globalen und gefilterten OPEN-, IN_PROGRESS-,
aktiven HIGH-, DONE- und CANCELLED-Zahlen sowie höchstens 32 `CompactTaskItem`s.
HIGH zählt nur OPEN/IN_PROGRESS; DONE wird nicht mit CANCELLED gleichgesetzt.
Ein optionaler Personenfilter betrifft ausschließlich affectedPersonId, sowohl
für Zähler als auch für Seiten. Kein Label/Pseudonym und keine Templatekennung,
Eventliste oder vollständige Editorkopie in der Übersichtszeile.

ACTIVE und CLOSED sind explizite getrennte Seitenstrecken. SQL-Keyset statt
OFFSET: ACTIVE nach Prioritätsrang, Null-Fälligkeitsrang, Fälligkeit, createdAt und
ID; CLOSED nach updatedAt DESC und ID. Letztere ID-Reihenfolge präzisiert bisher
nicht fachlich festgelegte Gleichstände; `TaskBoard.closed` bleibt unverändert.
Kein Negieren von Long-Zeitwerten, daher auch Long.MIN_VALUE korrekt.

Die SQL-Auswahl verwendet skalare Sortierschlüssel und LIMIT 32. Erst die
zugelassenen Zeilen werden materialisiert und maximal 32 Objekte in Kotlin sortiert.
ASCII-IDs nutzen BINARY; für Unicode-IDs wird pro einzelner bounded ID ein
UTF16-Schlüssel als korrelierte skalare Rekursion gebildet. Eine globale rekursive
ID-Tabelle wird vermieden. Ergänzende Tests bestätigen supplementary Unicode und
NUL über Seitengrenzen gegen Kotlin-Sortierung; UTF8-BINARY allein wäre falsch.

Fortsetzungen tragen Query, Action-/Generation-/Permissionbindung und Mutations-
epoche sowie den letzten Sortierschlüssel. Sie sind Metadaten, **keine neuen
Sicherheits- oder Sessiontokens**. Der letzte Schlüssel wird zusätzlich gegen eine
aktuell vorhandene passende Zeile geprüft. Keine Wiederverwendung für eine andere
Person, Lane oder einen anderen Zustand. Eine volle letzte Seite darf eine weitere
Fortsetzung liefern; dann markiert eine explizite leere Endseite mit `next=null`
den Abschluss. Eine kurze Seite beendet die Strecke direkt. Keine stille Kürzung.

`detail()` liefert ausschließlich bei ausdrücklicher Auswahl **einen vollständigen
Task** einschließlich unveränderter sourceTemplateKey, IDs und Zeitfelder. Erst
nach vorgelagerter Größenprüfung; bei Überbudgetierung Rejected, kein gekürztes
Editormodell und kein unbegrenzter Ersatzread.

`events()` liefert die vollständige Chronik über ausdrücklich markierte Seiten
mit exakter Gesamtzahl, jeweils maximal 32 Events und Fortsetzung nach
`(occurredAt,rowid)`, entsprechend dem bisherigen TaskDao-Chronikvertrag. Keine
Ereignisse werden gelöscht oder abgeschnitten. Der rowid-Anker muss noch zur
Aufgabe/Transaktionsepoche gehören. Er darf weder persistiert noch über Wartung,
Rebuild, Recovery oder eine neue Session hinweg verwendet werden. Einzelfalltests
lesen sämtliche 128 Events jeweils einer OPEN-, IN_PROGRESS-, DONE- und CANCELLED-
Aufgabe einschließlich Zeitgleichständen ohne Lücken/Duplikate und prüfen die
Lifecycle-Folge gegen `TaskRules.event` bis zum tatsächlichen Endstatus.

### 43.3 Autorität, Konsistenz und gemeinsames Budget

Jeder Aufruf erwirbt READY neu über `StorageAccess`; Generation und aktueller
vertrauenswürdiger Zustand müssen passen. Workerzugang verwendet den bestehenden
`SessionDbKeyOwner`, `ActionDatabaseAccess`, unveränderte SQLCipher-/Integritäts-/
FK-/Room-Preflights, Namespace-Lane und eine kurze Room-Readtransaktion. Persistierter
Aktionsstatus muss ACTIVE sein. Keine zweite Sessionautorität, kein Keygetter,
keine gehaltene DB/Lease und keine neue Klartextdatei. Bestehendes B2-temp_store-
MEMORY-Gate bleibt unverändert. Vollständige Action-/Personen-/Funktionspayload
ist nicht Teil dieses Taskreads und muss später gemeinsam aufgenommen werden.

Query-, Zeilen-, Decoder- und Abschlussgrenzen prüfen aktuelle Autorität.
Publication erst nach Transaktions-/DB-/Key-Cleanup erneut über `StorageContext`;
auch ein false-Rückgabewert nach ausgeführtem Callback verweigert den Rückgabepayload.
INVALID_DATA und Storage-/Cleanupfehler verweigern Content und melden BLOCKED;
Budgetverweigerung ist kein Storagekorruptionssignal. Revoke oder geänderter
Zustand verhindern Publication. Keine automatische Wiederholung.

**VERTRAGS-/INTEGRATIONSBLOCKER:** `currentState` muss später aus dem bestehenden
synchronen Mutation-/Validity-/Permissionowner kommen. Während Pending, UNKNOWN,
fehlgeschlagenem Cleanup, Recovery, Revoke oder nicht sicher beobachteten Writes
muss er null liefern; Epochen dürfen nicht wiederverwendet werden. Jeder relevante
Write/Wartungswechsel muss invalidieren. Ein UI-Boolean oder bloßes SQL COUNT/MAX
ersetzt diesen Vertrag nicht. Bei jeder Rejection alle alten Seiten/Details
verwerfen; nie zwei Epochen zu einem Snapshot zusammenfügen. Der neue Adapter ist
nur ein expliziter Standalone-Proofpfad, kein produktiv freigegebener Owner.

Vor Payloadmaterialisierung: feste skalare Typ-/Enum-/Relationsprüfungen, globale
Titel-/ID-Validierung, dann COUNT/MAX/SUM der ausgewählten höchstens 32 Zeilen.
Titel werden als UTF8-BLOB mit maximal 480 Bytes und exakt bestehender 120-
Codepoint-/Trimsemantik geprüft, einschließlich NUL. Auch ungültige Titel auf
späteren Seiten dürfen keine gültigen Gesamtzähler erzeugen. UTF8 wird streng
validiert; keine Ersatzzeichen-Decodierung. Kennungen/Referenzen über 256 UTF8-
Bytes verweigern die Admission explizit als BUDGET_REFUSED; dies ist keine neue
Domainregel oder Datenkürzung. Provenienz bleibt für die Übersicht unangetastet
und wird nur für das ausgewählte Detail vermessen/materialisiert.

Disjunkte exportierte `BoundedTaskReservation`-Units für Domainobjekte, Strings,
UTF16, Arrays, Listen/Kapazitäten, Indizes, Sortierung, Referenzen und Metadaten;
4-fache retained- und 16-fache transition-Allowances. Zusätzlich je Repräsentation
16 KiB Text-/Arraybasis vor dem Kopiefaktor für SQL-/Binding-/Decoder-/Cursor-/
Zähler-/Pagingmetadaten. Konservative Überlappungs-/UI-/Formkopie-Allowances,
keine Dedup-Rabatte. Tatsächliche Zeilen-/Bytezahlen dürfen beim Fetch die
vorgelagerte Reservation nicht überschreiten; Prüfung vor jedem BLOB-Fetch.
Seitengröße und Maxima begrenzen die SUM-/Chargearithmetik; das bestehende
`ActiveWorkspaceBudget` und seine unveränderten 8+8 MiB bleiben das Gate.

**KEINE ZWEITE RAM-FREIGABE:** Die Units sind für die spätere Addition mit
Personen/Funktionen **vor** deren Materialisierung exportiert. Standalone-PASS
berechtigt weder zur gleichzeitigen Retention anderer Readergraphs noch zum
Ansammeln aller Task-/Eventseiten. Höchstens die zugelassene Seite/Detailrepräsentation
ersetzt die vorige; weitere Seitenverweise müssen aufgegeben werden. Die Tests
sammeln erwartete/gelieferte IDs nur als Testorakel, nicht als Produktcache.
Die tatsächliche gemeinsame Scope-Admission bleibt
[#34](https://github.com/JulianGKK/action-planner/issues/34).

### 43.4 Reproduzierbare Lasttests, Charges und Hostzeiten

**HOST, KEIN ANDROID-LAUF:** Python 3.14.4 / SQLite 3.46.1, OpenJDK 17.0.20.1.
Bestehende `SqliteHostConnection`, echte In-Memory-SQLite-Tabellen/Indizes aus
Room-Schema 11; Bridge-IPC, SQL, Validierung, Decodierung und Host-Cleanup sind
in den Zeiten enthalten. Personen besitzen gültige Operationszustände; vollständige
synthetische Task-/Lifecycle-Eventbestände verbleiben im SQL-Store. Der reale
Produktadapter adressiert SQLCipher, seine Android-Laufzeit mit diesem großen
Bestand wurde in diesem Auftrag **nicht ausgeführt**. Host-SQLite ist kein
SQLCipher-/Room-/Native-/Keystore-Performancebeleg.

| Größe | Personen | Tasks | gespeicherte Events | OPEN / IN_PROGRESS / HIGH aktiv / DONE / CANCELLED |
|---|---:|---:|---:|---|
| klein | 20 | 40 | 480 | 8 / 8 / 5 / 16 / 8 |
| mittel | 100 | 250 | 10000 | 50 / 50 / 33 / 100 / 50 |
| groß | 200 | 1000 | 128000 | 200 / 200 / 133 / 400 / 200 |

| Größe | Admission ms | erste Seite gesamt ms | davon Materialisierung/Publication ms | Folgeseite ms | Taskdetail ms | Chronikseite ms | komplette ausgewählte Chronik ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| klein | 4,899 | 6,109 | 0,975 | entfällt | 5,125 | 5,907 | 5,545 |
| mittel | 22,917 | 24,110 | 1,710 | 24,361 | 22,687 | 23,872 | 46,978 |
| groß | 86,343 | 87,786 | 1,481 | 87,096 | 83,831 | 85,374 | 424,674 |

Admission misst bis zum ADMITTED-Hook einschließlich globaler Prüfungen,
Zähler und Seitenreservation. Materialisierung misst anschließend Fetch bis
Publication; erste Seite gesamt umfasst beides. Folgeseite, Detail und Chronikseite
enthalten jeweils erneut die vollständige Autorisierungs-/Metadatenstrecke.
Je Messung ein verworfener Warmup, danach Median aus fünf Durchläufen. Separate
Mediane sind nicht zwingend additiv. Klein besitzt nur 16 aktive Tasks und damit
keine ACTIVE-Folgeseite. Komplette ausgewählte Chronik: 12/40/128 Events, also
1/2/5 Seitenaufrufe einschließlich der notwendigen Endseite bei 128 Events.

Charges der gemessenen Operationen, jeweils **retained / transition in Bytes**:

| Operation | klein | mittel | groß |
|---|---:|---:|---:|
| erste ACTIVE-Seite | 588284 / 2346224 | 752852 / 3004496 | 750532 / 2995216 |
| ACTIVE-Folgeseite | entfällt | 750172 / 2993776 | 753592 / 3007456 |
| vollständiges einzelnes Detail | 435460 / 1734928 | 435460 / 1734928 | 435460 / 1734928 |
| erste Chronikseite | 519844 / 2072464 | 683324 / 2726384 | 683324 / 2726384 |

Alle Werte sind tatsächliche Engineering-Charges des Readers, **keine gemessenen
ART-Deep-Sizes, Native-Buffer-/GC- oder Composegrößen**. Die konservativen Gewichte
und SQLCipher-/SQLite-internen Allokationen benötigen reale Kalibrierung. Der
zusätzliche Detailgrenztest erreicht mit 83179 UTF8-Bytes Templatekennung genau
8388608 Transition-Bytes (retained 2098880); ein weiteres Byte verweigert vor jedem
weiteren Payload-BLOB. Bei 200000 und über 8 MiB Templatebytes ebenfalls kontrollierte
Verweigerung. Keine automatische Budgeterhöhung und kein unbounded Fallback.

18 neue Hostmethoden prüfen alle drei Größen, exakte Gesamt-/Filterzahlen,
beide Lanes mit vollständiger Keyset-Abdeckung ohne Duplikate/Lücken, viele CLOSED-
Tasks/Prioritäten/Gleichstände, mehrseitigen Personenfilter, maximale 120-Supplementary-
Codepoint-Titel, Unicode/NUL in Titeln/IDs/Provenienz, ungültige UTF8/Enums/Typen/
Titel auch außerhalb der ersten Seite, Cross-Action-Referenzen, exklusive Zuständigkeit,
Übergrößen, exakte Budgetgrenze, falsche Fortsetzungen, konkrete Mutation zwischen
Seiten, Revoke-/UNKNOWN-/Publicationrennen, abgebrochene Reads und Cleanupfehler.
Die Übersichtsabfrage fragt die Eventtabelle nicht ab und lädt keine Provenienz.

Ein neuer Regressionstest deckte zunächst einen Fehler im ASCII-Schnellpfad auf:
Byte-/TEXT-Längengleichheit allein erkannte ein ungültiges einzelnes UTF8-Byte
nicht. Zusätzliche echte ASCII-Klassenprüfung und strenge bounded UTF8-Prüfung
korrigieren das; die Rejection-Assertion blieb unverändert. Abschlusslauf grün.
Frühere schnellere Vorabzeiten ohne globale Textvalidierung sind kein Endergebnis.

Reproduktion der neuen Fälle: `./gradlew :data:testDebugUnitTest --tests
'*CompactTaskStoreTest' --offline` (für eine erneute Messung bei UP-TO-DATE gezielt
`--rerun-tasks`). Gesamte Abschlussprüfung offline: App/Data/Security-
`testDebugUnitTest`, `:app:assembleDebug`, App-/Data-`compileDebugAndroidTestKotlin`,
Produkt-/Demo-Manifestprüfungen und Architekturcheck bestanden.
**613 App + 722 Data + 4 Security = 1339 Tests, 0 Fehler, 0 Skips**.
Keine Geräteinstallation, Instrumentation oder Änderung installierter Apps;
vorhandene Android-PASS werden nicht als Performancebeweis übernommen.

### 43.5 Tragfähigkeit und verbleibende Release-/Integrationsblocker

**FÜR DEN GETESTETEN TASK-DATENPFAD TRAGFÄHIG:** 1000 Aufgaben und 128000 gespeicherte
Events sind vollständig seitenweise zugänglich; erste Seite und Folgeseiten bleiben
nach den konservativen Engineering-Charges deutlich innerhalb 8 MiB retained und
8 MiB transition. Die vollständige Task-/Eventmenge muss dafür nicht gleichzeitig
im RAM liegen. Das löst den bisherigen Voll-TaskBoard-Größenengpass für diesen
neuen Vertrag, ohne den bisherigen Reader zu verändern.

**KEINE PRODUKT-/RELEASEFREIGABE:** Gemeinsame Retention der 200 Personen/Funktionen
ist noch nicht gebucht; tatsächliche ART-/Native-/Compose-Speicherwirkung ist nicht
vermessen. Globale Validierung dominiert die Hostzeit und wird bei jedem Seiten-/
Detailread wiederholt; komplette lange Chroniken sind langsamer als die erste Seite.
SQLCipher-Öffnung und alle unveränderten Integritäts-/Room-Preflights kommen auf
Android hinzu. Kein Nachweis realer Content-OnDraw-Zeiten unter 100–150 ms.

Für die spätere Integration erforderlich:

1. [#33](https://github.com/JulianGKK/action-planner/issues/33): Engineeringgewichte,
   tatsächliche ART-/Native-/GC-/Compose-Retention und realen großen SQLCipher-Bestand
   isoliert nachweisen; keine Abschlusserklärung dieses Gesamtissues allein durch Host-PASS.
2. [#34](https://github.com/JulianGKK/action-planner/issues/34): gemeinsame vorgelagerte
   Action-/Personen-/Funktions-/Task-Admission. Personenkarte-/Hubzahlen aus exakten
   SQL-Summaries beziehen, niemals aus nur 32 Zeilen ableiten; gruppierte Personenzähler
   gegebenenfalls gemeinsam budgetieren. Keine zwei 8-MiB-Readerfreigaben addieren.
3. [#37](https://github.com/JulianGKK/action-planner/issues/37): ausschließlich vorhandenen
   ProductViewModel-Owner nutzen; echte Mutations-/Permission-/Load-/UNKNOWN-/Recovery-
   Hooks, atomare Owner-Publication, Seitenaustausch und synchrones Revoke aller
   Form-/Detail-/Chronikreferenzen. Keine Navigationstickets durch Cursor ersetzen.
4. TaskModel/WorkspaceSnapshot um den getrennten kompakten Vertrag erweitern;
   vollständigen Task bei Auswahl vor Editor/Transition nachladen, Expected-Task-
   Writervertrag erhalten, Änderungen invalidieren alle betroffenen Seiten/Zähler.
   Material-Taskauswahl und weitere heutige TaskBoard-Verbraucher getrennt umstellen.
5. [#38](https://github.com/JulianGKK/action-planner/issues/38): TaskScreen/ActiveTasks auf
   begrenzte lazy Listen mit stabilen Task-/Eventkeys und kontrollierter Nachladung
   umstellen. Kein `forEach` über alle Tasks/Events, kein Akkumulieren aller Seiten,
   kein wiederholtes Vollsortieren oder Filtern einer Ersatz-TaskBoard-Liste;
   gemeinsame lookup-/Form-/Scrollkosten budgetieren. Anschließend echte Navigation
   einschließlich Median/p95 und Lifecycle messen.
6. [#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/CursorWindow-/
   Native-/FD-/Lease-Cleanup bleibt BLOCKER. `SQLCipherStatement.close()` schließt
   den Cursor weiterhin nicht explizit. Host-Statement-Close und frühere FD-PASS
   beweisen keine vollständige native Cursorfreigabe. Kein spekulativer B2Driver-
   oder SQLCipherfix in diesem Slice.

Geändert ausschließlich die vier neuen Code-/Hosttestdateien oben und dieser
Assessmentabschnitt. Keine Dependency-/Schema-/Permission-/Netzwerkänderung,
keine Produktwritepfade oder RAM-Hits aktiviert. Keine APKs, Datenbanken, Schlüssel,
Credentials, Rohlogs, persönlichen Pfade oder Gerätekennungen im Commit.
Regulärer WIP-Commit/Push mit unabhängigem Remote-SHA-Abgleich; danach STOPP bis
zum unabhängigen GitHub-Review. Kein Force-Push, Merge, Release oder Produktupdate.

## 44. Issue #34 – Gemeinsame ACTIVE-Kern-Admission

### 44.1 Implementierter gemeinsamer Reader

**IMPLEMENTIERT UND AUF ECHTEM SQLITE GETESTET (2026-10-20):** Branch
`p11-performance-optimization`, sauberer Ausgangszustand und lokal/remote unabhängig
bestätigte Basis `86c682856fa7f57cb778628627aba01dd1a03675`.

`ActiveCoreRead.kt` definiert einen gemeinsamen, explizit begrenzten Vertrag:
ein `ActiveCorePayload` mit genau einer Action, vollständigem `OperationalBoard`
(Personen, Rollen/Funktionen, Rollenplanung und aktuellem Operationsstatus), einer
kompakten ersten ACTIVE-Aufgabenseite mit höchstens 32 Einträgen und exakten
globalen/gefilterten Taskzählern. Die optional gefilterte Person bedeutet weiterhin
`affectedPersonId`, nicht Zuständigkeit. Kein TaskBoard, keine vollständige Taskliste,
keine Template-/Editor- oder Ereignischronik im retained Kern. Die bestehenden
bewussten Detail-/Chronik-/Folgeseitenreads aus #33 bleiben getrennt erhalten.

`ActiveCoreStore` bereitet beide Teile auf derselben `PooledConnection` innerhalb
einer einzigen Readtransaktion vor. `ProductActiveCoreReader` verwendet dafür die
bestehenden Grenzen: READY-`StorageAccess`/`StorageContext`, aktuellen vertrauenswürdigen
Binding-/Epochzustand, vorhandenen `SessionDbKeyOwner`, `ActionDatabaseAccess`,
unveränderte SQLCipher-/Integrity-/FK-/Room-Preflights und Namespace-Lane.
Kein neuer Sessionowner, keine eigene Autorisierung und keine Produktinstanziierung.

### 44.2 Metadaten zuerst, genau ein Ledger, danach Materialisierung

`BoundedActiveStore` ist in `prepare()` und eine nachgelagerte Materialisierung
geteilt; `CompactTaskStore` entsprechend in `preparePage()` und Materialisierung.
Die bisherigen Einzel-`read()`-/`page()`-Einstiege verwenden diese gleichen Pläne
und behalten ihre Fachsemantik, Prüfungen und exakten bisherigen Charges bei.
Detail-/Eventpfade und der vollständige `BoundedTaskStore` bleiben erhalten.

Die gemeinsamen Schritte sind:

1. Ausschließlich Encoding-/ACTIVE-/Typ-/Enum-/Relations-/COUNT-/MAX-/SUM-Prüfungen
   für Action/Personen/Rollen/Planungen/Operationsstatus und die kompakte Taskseite
   samt vollständigen Taskzählern. Kein Personen-/Rollen-/Task-BLOB-Fetch davor.
   Einzeltext- und Kardinalitätsgrenzen schützen bereits die Metadatenerfassung;
   nicht sicher repräsentierbare Metadaten werden früh fail-closed verweigert.
2. Unveränderliche, disjunkte `ActiveCoreReservation`-Units beider Teile bilden.
   Gemeinsame Hüllen-/Plan-/Quittungs-/Referenzkapazität zusätzlich buchen:
   **2080 retained / 7808 transition Bytes**. Action selbst ausschließlich im
   Personen-/Funktionenteil; Taskunits enthalten keine zweite Actionzeile.
3. `CoreReadAdmission` validiert jeden Unitsbestand einzeln, damit negative oder
   strukturell fehlerhafte Werte sich nicht gegenseitig aufheben können. Danach
   komponentenweise `Math.addExact` über alle Zähler. Overflow verweigert kontrolliert.
4. **Ein** `ActiveWorkspaceBudget`: gesamte retained-Units zulassen/sealen, dann
   gesamte transition-Units zulassen/sealen. Maximal **8388608 / 8388608 Bytes**.
   Keine nachträgliche Addition bereits materialisierter Readerresultate, keine
   zwei unabhängigen Payloadfreigaben, keine automatische Budgeterhöhung.
5. Erst nach dieser gemeinsamen Zulassung beide Pläne materialisieren. Ein fehlender,
   fehlerhafter oder entzogen autorisierter Teil verhindert den gesamten Rückgabepayload.
   Erst der vollständige Kern darf nach DB-/Transaktions-/Key-Cleanup publiziert werden.

`ActiveWorkspaceBudget.estimateUnits()` ist ausschließlich eine reine Kosten-/
Strukturdiagnose mit den unveränderten Engineeringgewichten, auch oberhalb des Limits;
kein Ledger, kein Retentiongrant und keine zweite Budgetfreigabe. Ein dortiges
ADMITTED bezeichnet nur berechenbare Units. Entscheidend bleibt ausschließlich die
anschließende gemeinsame `admit`-/`seal`-Entscheidung. Die Diagnose erlaubt exakte
Überbudgetzahlen statt eines irreführenden Chargestands 0 nach Verweigerung.

Das Ledger wird synchron auf dem jeweils aktuellen Ownerthread konstruiert, benutzt,
gesealt und im `finally` geschlossen. Keine DB-Suspension innerhalb dieses Abschnitts.
Die interne Buchungsquittung enthält nur unveränderliche Units/Kosten und höchstens
zwei transaktionslokale Planidentitäten, **keine Representation-Tickets oder Ledger**.
Private Quittungskonstruktion, Identitätsprüfung und atomarer Einmalverbrauch verhindern
fremde/wiederholte Planmaterialisierung. Diese Quittung ist keine Storageautorität.
Der echte Suspend-/Resume-Hosttest wechselt zwischen zwei Executor-Threads über die
SQL-Aufrufe; Vorbereitung und Materialisierung bleiben korrekt, ohne threadgebundene
Ledger weiterzugeben.

### 44.3 Autorität, Fehler und Ownershipgrenzen

Ein `CompactTaskState` bindet denselben Kern an genau eine ACTIVE-Action-ID,
READY-Generation, Berechtigungsrevision und Mutationsepoche. Es ist derselbe
Metadatenvertrag wie bei kompaktem Paging, keine zweite Sessionautorität.
Jeder SQL-/Zeilen-/Materialisierungsabschluss prüft den aktuellen Kontext und
vertrauenswürdigen Zustand; Publication prüft erneut nach vollständigem Cleanup.
Auch ein false-Rückgabewert von `StorageContext.publish` nach ausgeführtem Callback
liefert kein Value zurück. Vor der Rückgabe werden Kontext und Epoche auch nach einem
akzeptierten Callback nochmals geprüft; Mutation/Revoke während dessen Rückkehr liefern
kein Value. Das ersetzt nicht die spätere atomare Owner-Publication aus #37.

Tests simulieren vertrauenswürdige Bindings/Epochen mit atomaren Referenzen;
keine UI-Boolean-Autorität. Bei initialem UNKNOWN oder Revoke kein Read; bei Pending/
UNKNOWN/Recovery muss der spätere Adapter null liefern, bei BLOCKED/Sessionentzug
READY verweigern. Jede Mutation muss vor ihrem Seiteneffekt invalidieren. Getestet
an allen gemeinsamen Phasen: Revoke, Mutation, Permission-, Action-, Generationwechsel,
UNKNOWN, BLOCKED und Recovery. Ebenfalls Entzug nach DB-Cleanup, nach Lease-Cleanup
und unmittelbar vor Publication. Keine Teil-CONTENT- oder falsche EMPTY-Publication.

SQL-/Storage- und Cleanupfehler verweigern den gesamten Kern und melden Storage-
BLOCKED. INVALID_DATA bleibt getrennt. **BUDGET_REFUSED meldet keine DB-Korruption**;
alle Überbudgetfälle haben null BLOCKED-Signale. Kein Retry und kein unbounded Fallback.

`PreparedCoreRead` hält nur kurzlebige Metadaten/Materialisierungsclosures in der
laufenden Transaktion. Der gemeinsame Pfad ruft keine bisherigen Payloadreader auf
und hält keine alten Readerresultate. In der Rückgabe sind ausschließlich Domainkern,
Kosten und Units, keine Pläne, Quittungen, SQL-/Storagekontexte, Schlüssel oder Leases.
Keine zweite Registry. Einzelaufrufe der bestehenden Reader erteilen weiterhin keine
Erlaubnis, deren Graphen zusätzlich zu diesem Kern zu halten.

**WEITER OFFEN:** Reale produktive Writer-/Outcome-/UNKNOWN-/Recoveryhooks gehören
zu [#35](https://github.com/JulianGKK/action-planner/issues/35), der einzige Payload-/
Snapshot-/Formularowner zu [#37](https://github.com/JulianGKK/action-planner/issues/37).
Ein gemeinsames Reader-Value erlaubt noch keine längere Produkt-RAM-Vorhaltung.
ProductViewModel, TaskModel, TaskScreen und sämtliche Produktwritepfade unangetastet;
keine RAM-Hits oder Navigation angebunden.

### 44.4 Realistische SQLite-Bestände und kombinierte Charges

Echte SQLite-Hosttests mit allen Tabellen/Indizes aus Schema 11. Je sechs reale
Rollen, zwei geplante Rollen pro Person, eine davon primär und aktuell bei ON_SITE.
Taskzuständigkeit abwechselnd Person/Rolle sowie unabhängig betroffene Person;
alle Referenzen auf echte Datensätze derselben Aktion. Vollständige synthetische
Lifecycle-Historien bleiben im SQL-Store; kein Eventhistorien-Prefetch durch den Kern.

| Bestand | Personen | Rollen | Tasks | gespeicherte Events | gemeinsames Ergebnis |
|---|---:|---:|---:|---:|---|
| klein | 20 | 6 | 40 | 480 | ADMITTED, vollständige Personen/Funktionen + 16 aktive Tasks |
| mittel | 100 | 6 | 250 | 10000 | BUDGET_REFUSED vor Payload |
| groß | 200 | 6 | 1000 | 128000 | BUDGET_REFUSED vor Payload |

Tatsächliche unveränderte Engineering-Charges, **retained / transition in Bytes**:

| Bestand | Action + Personen/Funktionen/Operations | kompakte Taskseite | gemeinsame Hüllen | insgesamt |
|---|---:|---:|---:|---:|
| klein | 808348 / 3224176 | 580304 / 2314304 | 2080 / 7808 | **1390732 / 5546288** |
| mittel | 3803868 / 15206256 | 739152 / 2949696 | 2080 / 7808 | **4545100 / 18163760** |
| groß | 7562268 / 30239856 | 739612 / 2951536 | 2080 / 7808 | **8303960 / 33199200** |

**KONKRETER GRÖSSENBLOCKER:** Beim großen Bestand passt retained rechnerisch noch
mit 84648 Bytes Rest, aber transition überschreitet das Limit um **24810592 Bytes**.
Die vollständige Personen-/Funktionenprojektion alleine benötigt 30239856 Transition-
Bytes. Ihre Metadaten: 1007 Zeilen (1 Action + 200 Personen + 6 Rollen + 400 Planungs-
relationen + 200 Primärplanungen + 200 Operationszustände), 3629 Textfelder inklusive
nullable und 15493 UTF8-Bytes. Nach unveränderter Formel:
`1007*20608 + 3629*2272 + 15493*80 + 3072 = 30239856`.
Es dominieren die konservativen Objekt-/Map-/Sortier-/Kopiergewichte, nicht die
128000 Events: Der Kern fragt deren Tabelle gar nicht ab. Die kompakte Taskseite
löst diesen verbleibenden Personen-/Funktionen-/Kopierkonflikt nicht.

Zusätzlicher echter Grenzfall: **35 Personen / 250 Tasks** mit denselben Relationen.
Beide Komponenten passen einzeln in 8+8 MiB, gemeinsam aber **2111080 / 8427680 Bytes**;
transition liegt **39072 Bytes** über dem Limit. Null Payloadqueries/-BLOBs und keine
Korruptionsmeldung. Genau diese Verweigerung wäre bei zwei Einzelzulassungen verloren.

Exakte Gesamtgrenze: Bei 20/40 fügen 35529 weitere Actionbeschreibungsbytes eine
zulässige Gesamtcharge von **2101312 / 8388608 Bytes** hinzu. Ungekürzter Kern wird
geliefert. Ein weiteres Byte verweigert vor beiden Materialisierern; keine Aufgabe,
Person oder Rolle wird zur Budgetpassung weggelassen. Alle großen Refusals liefern
weder Teilpersonen noch Taskseiten/Zähler als eigenständigen CONTENT-Ersatz.

### 44.5 Median/p95 und Grenzen der Messung

Host: Python 3.14.4, SQLite 3.46.1, OpenJDK 17.0.20.1, In-Memory-SQLite über die
bestehende Bridge. Zwei Warmups, anschließend 20 Messungen je Größe. Median als
Mittel der beiden mittleren Werte, p95 als nearest rank (19. Wert von 20).
Enthalten: Bridge-IPC/SQL, Metadatenvalidierung, Unitsbildung, gemeinsame Admission,
gegebenenfalls Materialisierung und beim Gesamtwert Host-Transaktions-/Cleanup-/
Publicationgrenzen. Kein Android-/SQLCipher-/Keystore-/Compose-Navigationsnachweis.

| Größe | Metadaten Median / p95 ms | Admission Median / p95 ms | Materialisierung Median / p95 ms | Gesamt Median / p95 ms |
|---|---:|---:|---:|---:|
| klein | 3,703 / 4,465 | 0,046 / 0,059 | 4,806 / 6,042 | 9,015 / 10,667 |
| mittel | 6,104 / 7,234 | 0,047 / 0,084 | entfällt: verweigert | 6,318 / 7,656 **Verweigerung** |
| groß | 9,926 / 11,393 | 0,044 / 0,054 | entfällt: verweigert | 10,168 / 11,672 **Verweigerung** |

Die kleinen Gesamtzeiten sind vollständige zugelassene Kernreads; die mittleren/
großen Zeiten ausschließlich Verweigerungszeiten. Kein 0-ms-Materialisierungs-PASS.
Die ASCII-Titel dieser gemeinsamen Relationsfixture unterscheiden sich von den
langen Unicode-/NUL-Titeln aus 43; keine direkte Performancegleichsetzung. Alle
vorhandenen Unicode-/NUL-/Sortier-/Paging-/Budgetproofs bleiben im Regressionlauf.
Engineering-Charges sind keine gemessenen ART-Deep-Sizes, Native-/GC- oder Compose-
Retention. Keine 100–150-ms-Produktnavigation oder Release-1.0-Performance behauptet.

### 44.6 Tests, Prüfergebnis und verbleibende Arbeit

12 neue Hostmethoden: realistische Größen und Median/p95, vollständige zugelassene
Personen-/Rollen-/Planungs-/Operationsprojektion, exakte globale und Personenfilter-
Taskzähler aus dem Gesamtbestand, bounded Taskseite mit echter Fortsetzung bei 250
Tasks, Metadaten ohne BLOBs, genau eine Actionpayloadquery, zwei einzeln passende
aber kumulativ zu große Graphen, exakte gemeinsame Budgetgrenze + nächstes Byte,
alle Zustandsrennen, verspätete Publication, Cross-Action-/Typ-/Relations-/Domainfehler,
SQL-/Storage-Injektionen, einen echten SQLite-Enginefehler und beide Cleanupfehler, echte Suspend-Threadwechsel, fremde/wiederholte Pläne,
negative Units und Overflow. Budgetfehler publizieren nie Teilpayload und blockieren
den Store nicht. Die bestehenden Reader-/Host-/Android-Proofs wurden nicht abgeschwächt.

Ein erster neuer Negativtest scheiterte beim Anlegen der Fixture: UPDATE mehrerer
Rollen auf denselben normalizedName verletzte deren UNIQUE-Index, noch vor dem Reader.
Mutation auf genau eine Rolle begrenzt; der unveränderte erwartete INVALID_DATA-Test
erreicht damit tatsächlich den Reader und belegt die unterdrückte Publication. Abschluss grün.

Reproduktion: `./gradlew :data:testDebugUnitTest --tests '*ActiveCoreStoreTest' --offline`
(bei UP-TO-DATE für erneute Messwerte gezielt `--rerun-tasks`). Abschlusslauf:
**613 App + 734 Data + 4 Security = 1351 Tests, 0 Fehler, 0 Skips**.
`:app:assembleDebug`, App-/Data-`compileDebugAndroidTestKotlin`, Architekturcheck und
Produkt-/Demo-Manifestprüfungen offline bestanden. Keine neuen Androidtests ausgeführt,
keine Instrumentation, Geräteinstallation oder Änderung installierter Apps.

- [#34](https://github.com/JulianGKK/action-planner/issues/34): gemeinsame Metadaten-/
  Admission-/Materialisierungsstrecke jetzt implementiert und auf SQLite geprüft;
  unabhängiger Review und reale isolierte SQLCipher-/Room-/Ressourcennachweise bleiben nötig.
- [#33](https://github.com/JulianGKK/action-planner/issues/33): großer gemeinsamer
  Größen-/Speichervertrag weiterhin BLOCKER, jetzt mit obigen exakten Refusalzahlen.
  Keine stillschweigende Gewichts-/Budgetreduktion oder Auslassung von Personen/Rollen.
- [#35](https://github.com/JulianGKK/action-planner/issues/35) und
  [#37](https://github.com/JulianGKK/action-planner/issues/37): reale Writer-/UNKNOWN-/
  Recoveryhooks, synchroner Revoke und einziger Produkt-Snapshot-/Formularowner offen.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine Navigationseinbindung,
  RAM-Hits oder Compose-Aktivierung; reale Content-OnDraw-Median/p95 erst nach Integration.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): unverändert offener Cursor-/
  CursorWindow-/Native-/FD-/Lease-BLOCKER. `SQLCipherStatement.close()` schließt den
  Cursor nicht explizit. Hosttests prüfen Statement-/Transaktionsende und simulierte
  Handle-/Lease-Cleanupreihenfolge vor Publication; tatsächliches Android-Room-/Native-/
  Leaseende folgt im unveränderten Produktionswrapper, wurde hier nicht am Gerät geprüft.
  Weder Host-Close noch frühere FD-PASS beweisen vollständige native Cursorfreigabe.

Neue Dateien: `ActiveCoreRead.kt`, `CoreReadAdmission.kt`, `ActiveCoreStore.kt`,
`ProductActiveCoreReader.kt`, `ActiveCoreStoreTest.kt`. Angepasst: Phasentrennung
in `BoundedActiveStore.kt` und `CompactTaskStore.kt`, reine Kostendiagnose in
`ActiveWorkspaceBudget.kt`, Standalone-Hinweis in `ProductCompactTaskReader.kt`
und ausschließlich dieser neue Assessmentabschnitt.
SessionCoordinator, B2Driver, SQLCipher/Tink, Schema 11, Sessionfristen, Produkt-
writepfade, ProductViewModel, TaskModel, TaskScreen und Navigation unverändert.
Keine neuen Dependencies, Netzwerk-/Permissionpfade, Klartextdateien oder Registry.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen im Commit. Regulärer WIP-Commit/Push mit unabhängigem Remote-SHA-
Abgleich; danach STOPP bis zum unabhängigen GitHub-Review. Kein Force-Push, Merge,
Release oder Produktupdate.


## 45. Issue #33/#34 – Kompakte Personen- und Funktionsprojektion

### 45.1 Umsetzung und erhaltene Fachsemantik

**IMPLEMENTIERT, SQLITE-HOSTGETESTET, NICHT PRODUKTIV ANGEBUNDEN (2026-10-20).**
Sauberer Ausgangszustand auf `p11-performance-optimization`, Basis
`8efaddc382fee7b92a5dcd9f74b868639b99d47c`.
Der gemeinsame `ActiveCorePayload` enthält jetzt genau eine Action, eine
`CompactPeoplePage` mit höchstens 32 Karten, `CompactFunctions` mit vollständigen
Rollenidentitäten und exakten Belegungszahlen sowie die bisherige begrenzte Taskseite.
Er enthält **kein OperationalBoard**, keine vollständige Taskliste und keine Events.
Die vollständigen `BoundedActiveStore`-/`BoundedTaskStore`-Reader und deren Grenzen,
Fachmodelle, Writes sowie vorhandenen Host-/Android-Proofs bleiben verfügbar.

Geprüfte tatsächliche Abhängigkeiten:

- `ActivePeople` verwendet heute `OperationalBoard.persons`, vollständige Statuscounts,
  Rollenlookup, Ort/Zeit und `TaskBoard.affectedCount`. Neue Karten enthalten genau
  Pseudonym, Erstellungszeit/ID, Operationsstatus, aktuelle Rollen-ID/-Name, Ort,
  Updatezeit und exakte Anzahl betroffener OPEN/IN_PROGRESS-Tasks. Die letzte Zahl
  kommt aus allen Tasks der Aktion, nicht aus der Taskseite oder Zuständigkeit.
  Die vorhandenen Anzeige-/Zeitregeln bleiben Sache der späteren UI-Integration.
- `ActiveFunctions`/`activeFunctionPeople` gruppieren ausschließlich **ON_SITE** nach
  aktueller Rolle. Geplante Rollen und NO_STATUS mit vorgemerkter aktueller Rolle
  zählen dort nicht. `CompactFunctions` liefert alle Rollen, auch unbesetzte, mit
  exakten ON_SITE-Zählern, Ohne-Funktion-Zahl, ON_SITE-Gesamtzahl und Zahl verwendeter
  Funktionen. Keine vollständigen Pseudonym-/Mitgliederlisten pro Gruppe im Kern.
- `OperationalBoard` sortiert Personen nach `createdAt`, dann Kotlin-UTF16-ID;
  Rollen nach `OperationalRules.normalizedName`, dann UTF16-ID. Dies bleibt erhalten,
  einschließlich Gleichständen, Unicode, Supplementary-Zeichen und eingebettetem NUL.
- `OperationsModel` erwartet vollständige Rollenplanung, `preferredRoles`, aktuellen
  Snapshot als erwarteten Writezustand und derzeit auch globale Ortsvorschläge.
  `ProductViewModel` hält weiterhin ein vollständiges `WorkspaceSnapshot.operations`.
  Beide sind **nicht** auf den neuen Vertrag umgestellt. Ortsvorschläge sämtlicher
  Personen werden nicht heimlich in den neuen Detailread aufgenommen.

### 45.2 Paging, vollständige Details und Autorität

`CompactActivePeopleReader`/`ProductCompactPeopleReader` bieten ausdrückliche
Workerreads: allgemeine Personenseite, Funktionsmitgliederseite und vollständiges
Detail einer ausgewählten Person. Funktionsfilter `Function(null)` bedeutet ON_SITE
ohne Funktion, `Function(roleId)` ausschließlich aktuelle ON_SITE-Mitgliedschaft;
`All` bedeutet alle Personen. Dies ist getrennt vom Taskfilter `affectedPersonId`.
Jede Seite enthält globale Verteilung aller acht Statuswerte und die **gesamte**
Trefferzahl ihres Filters; nie aus den höchstens 32 Karten abgeleitet.

Keyset nach `(createdAt, UTF16(id))`, kein OFFSET und keine Kotlin-Vollsortierung
aller Personen. SQL wählt nur begrenzte Schlüssel, dann werden die zugelassenen
höchstens 32 Karten materialisiert/sortiert. Für ASCII-IDs BINARY-Fastpath, sonst
skalare UTF8-BLOB-Rekursion mit UTF16-Sortierschlüssel, einschließlich NUL. IDs sind
vor Sortierung auf 256 UTF8-Bytes begrenzt. Rollen werden erst nach Admission als
vollständiger **zugelassener** Katalog sortiert; übergroße Kataloge werden verweigert.
Keine Rollenplanung aller Personen oder Ereignischronik im Kartenpfad.

Fortsetzung enthält dieselbe Action/Generation/Permission/Mutationsepoche wie das
bestehende `CompactTaskState`, zusätzlich Filter und letzten tatsächlichen Schlüssel.
Jeder Read prüft READY und den aktuellen vertrauenswürdigen Zustand erneut sowie
Existenz/Filterzugehörigkeit des Endpunkts. Eine volle letzte Seite kann noch einen
Cursor liefern; die folgende leere Seite markiert ausdrücklich das Ende. Wiederholung
ist ein bewusster neuer SQL-Read, kein Cache. Seiten verschiedener Zustände dürfen
niemals zusammengefügt werden; Rejection verwirft bisherige Seiten. Ein gestohlener
oder konstruierter Cursor ist keine Autorisierung.

`detail(personId)` lädt genau die vollständige `Person`, sämtliche geplanten Rollen-IDs,
optionale Hauptrolle und vollständigen aktuellen `PersonOperationalState`. Auch mehr
als 32 Planungsrelationen werden vollständig in 32er-SQL-Batches geliefert, nach eigener
Admission des gesamten **ausgewählten** Details; bei Budgetüberschreitung Rejection,
keine abgeschnittene Planung. Rollenwahl nutzt die vollständigen Rollenidentitäten,
kein erfundenes Teil-OperationalBoard. Globale Vorschlags-/Editor-UX muss #38 gesondert
lösen. Taskdetail/-chronik bleibt im unveränderten bewussten Taskreader mit Eventpaging.

Gleiche produktive Grenzen wie §44: `SessionCoordinator`, READY-`StorageAccess` und
`StorageContext`, vorhandener `SessionDbKeyOwner`, `ActionDatabaseAccess`, Namespace-
Lane, SQLCipher-/Room-Preflights und genau eine Readtransaktion. Keine parallele
Sessionautorität, keine Änderung an B2Driver, Schema 11 oder Sessionfristen.
`boundedProjectionRead` ist die unverändert fail-closed arbeitende, gemeinsam genutzte
Cleanup-/Publicationgrenze des bisherigen Kernreaders: Return/Publication erst nach
Transaktions-/DB-/Key-Cleanup; nochmals Kontext-/Epochprüfung nach Publicationcallback.
Revoke, Pending/UNKNOWN, Action-/Permission-/Generations-/Mutationswechsel, BLOCKED,
Recovery und Cleanup-/Storagefehler liefern keine Teilpayload. Die Adapterzustände
werden nur vertrauenswürdig simuliert; reale Writer-/UNKNOWN-Hooks bleiben #35.

### 45.3 Metadaten, gemeinsame Admission und konkrete Charges

`CompactPeopleStore` nutzt den bestehenden vollständigen Reader ausschließlich für
dessen **metadatenreines** `prepare` als Encoding-/Typ-/Status-/Relations-/Kardinalitäts-
Preflight. Dieser Vollgraphplan wird weder zugelassen noch materialisiert oder im
Resultat gehalten. Die bisherigen frühen Einzeltext-/Metadaten-Kardinalitätsgrenzen
bleiben konservativ erhalten. Task-Preflight sichert auch betroffene Taskzahlen.
Danach COUNT/MAX/SUM der tatsächlich ausgewählten Karten, Action und vollständigen
Rollenzusammenfassung, einschließlich transient gelesener Normalisierungsfelder.
Globale Referenz-/Typ-/Statusfehler verweigern bereits vor Payload; vollständige
Text-/Domainvalidierung erfolgt für jeden **ausgewählten** Payload vor dessen
Publication, nicht als behauptete Vollvalidierung aller Offpage-Editordaten.

Alle Metadaten beider Kernkomponenten zuerst, dann genau eine synchrone
`CoreReadAdmission` mit einem Ledger und unverändert **8388608 Bytes retained sowie
8388608 Bytes transition**. Erst danach Action, Personen/Funktionen und Taskseite.
Threadgebundene Ledger/Representation-Tickets überqueren keine suspendierende
SQL-Operation; Units und Einmal-Materialisierungspläne bleiben wie §44 unveränderlich.
Keine zweite Actionbuchung oder versteckte Personenseite. Kein Retry/Vollread-Fallback.

Neue Units beschreiben die wirklich verkleinerte Darstellung, mit unveränderten
Engineeringgewichten und 4-/16-fachen Kopier-/Übergangsfaktoren. Personenkomponente:
`r = Actionzeilen + Karten + Rollen`, `f = alle ausgewählten Textfelder inklusive
nullable`, `b = deren UTF8-Bytes`. Pro Faktor `c` werden `(r+16)*c*8` Objekte,
`(f+16)*c` Strings/Arrays, `(b+16384)*c` UTF16-Units und Arraypayloadbytes,
16 Listen, `(r+32)*c` Listenkapazität, 8 Maps mit `r*c` Kapazität sowie
`(r+32)*c*16` Referenzen gebucht. Die feste Reserve deckt Metadaten, Sortier-/Cursor-
und Formularreferenzen ab. Decoder-/Zeilen-/Zwischenobjekte sind enthalten; keine
Gewichts- oder Limiterhöhung. Taskunits und gemeinsame Hülle unverändert (§44).

Echtes SQLite/Schema 11; sechs Rollen, zwei geplante Rollen und eine Hauptrolle pro
Person, ON_SITE-Zuordnung, unabhängige Taskzuständigkeit/betroffene Person. Vollständige
synthetische Lifecycle-Eventbestände sind in SQL gespeichert und per COUNT geprüft;
der Kern fragt die Eventtabelle überhaupt nicht ab.

| Personen / Tasks / gespeicherte Events | Action + Karten + Funktionen R / T Bytes | Taskseite R / T Bytes | gemeinsame Hülle R / T Bytes | Gesamt R / T Bytes | Ergebnis |
|---|---:|---:|---:|---:|---|
| 20 / 40 / 480 | 718460 / 2864624 | 580304 / 2314304 | 2080 / 7808 | **1300844 / 5186736** | ADMITTED |
| 100 / 250 / 10000 | 861548 / 3436976 | 739152 / 2949696 | 2080 / 7808 | **1602780 / 6394480** | ADMITTED |
| 200 / 1000 / 128000 | 861548 / 3436976 | 739612 / 2951536 | 2080 / 7808 | **1603240 / 6396320** | ADMITTED |

Beim großen Bestand liegen retained **6785368 Bytes**, transition **1992288 Bytes**
unter dem jeweiligen Limit. Gegenüber §44 sinkt der gesamte Transitionbedarf von
33199200 auf 6396320 Bytes; die damaligen 30239856 Bytes der vollständigen
Personenkomponente werden durch 3436976 Bytes für Action + 32 Karten + sechs
Funktionszusammenfassungen ersetzt. Alle 200 Personen bleiben über Keysets und
vollständige bewusste Details erreichbar; keine Aufgabe oder Eventhistorie gelöscht.

**Getrennte Kataloggrenze:** 200/1000/128000 mit allen 200 ON_SITE-Personen in einer
Funktion und **32** vorhandenen Rollen: **1816064 / 7247616 Bytes**, ADMITTED;
alle Rollenidentitäten einschließlich 31 unbesetzter Funktionen vorhanden, sieben
Mitgliederseiten ohne Lücken/Duplikate. Mit **100** Rollen: **2372676 / 9474064 Bytes**,
BUDGET_REFUSED vor erstem Payload-BLOB, transition **1085456 Bytes** über Limit.
Die gemeinsame Admission enthält dann 6514720 Transition-Bytes für die Personen-/
Funktionskomponente (133 Zeilen, 693 Textfelder, 6468 UTF8-Bytes):
`133*20608 + 693*2272 + 6468*80 + 1681920 = 6514720`, dazu unverändert 2951536
Task- und 7808 Hüllenbytes. Ursache ist der vollständige Rollenkatalog mit den
konservativen Übergangsgewichten; kein Event-Prefetch. Nicht als PASS umgedeutet. Noch größere
Rollenkataloge benötigen einen gesonderten paginierten Katalog-/Editorwahlvertrag;
kein unbounded Ersatzread und keine automatische Budgeterhöhung.

Zwei einzeln passende reale Projektionen mit gepolsterter Actionbeschreibung werden
weiterhin gemeinsam verweigert: **2471880 / 9870880 Bytes**, null Payload und kein
Korruptionssignal. Bytegrenze des neuen kleinen Kerns: zusätzliche 40023 Beschreibungs-
bytes ergeben **2101304 / 8388576 Bytes** (32 Bytes unter Limit); nächstes Byte erhöht
transition um 80 und wird vor beiden Materialisierern verweigert. Keine Behauptung,
dass dieser durch 80 teilbare Eingabepfad exakt das letzte Limitbyte erreicht.

Tatsächliche Standalone-Charges **R / T Bytes** (keine zusätzliche Retentionfreigabe):

| Personen | erste Personenseite | Personenfolge | Funktionsmitgliederfolge r0 | vollständiges Detail p0 |
|---|---:|---:|---:|---:|
| 20 | 660664 / 2633440 | 422784 / 1681920 | 422784 / 1681920 | 451928 / 1798496 |
| 100 | 803752 / 3205792 | 804352 / 3208192 | 422784 / 1681920 | 451928 / 1798496 |
| 200 | 803752 / 3205792 | 804352 / 3208192 | 446752 / 1777792 | 451928 / 1798496 |

Eine Einzelrückgabe autorisiert **keine** Co-Retention von Kern + weiteren Seiten oder
Editoren. Der Readvertrag ersetzt Seiten; gemeinsame Lebensdauer-/Ersetzungsbuchung
und Formularreferenzen müssen im einzigen zukünftigen Owner aus #37 erfolgen. Keine
zweite unabhängige 8-MiB-Produktfreigabe durch den Standalone-Reader.

### 45.4 Reproduzierbare Hostzeiten und Aussagegrenzen

Bestehende In-Memory-SQLite-Bridge mit Schema 11 auf dem Host aus §44; zwei Warmups,
20 Messwerte je Größe, Median der beiden mittleren Werte, p95 nearest rank (19/20).
Erster gemeinsamer Kernread bedeutet ein frischer vollständiger Readeraufruf auf
warmem Host, kein RAM-Hit und keine Messung eines kalten Androidstarts. Gesamt umfasst
Hosttransaktion, simuliertes DB-/Lease-Cleanup und Publication; IPC ist eingeschlossen.

| Personen | Metadaten Median / p95 ms | Admission Median / p95 ms | Materialisierung Median / p95 ms | gemeinsamer Kernread Median / p95 ms |
|---|---:|---:|---:|---:|
| 20 | 5,619 / 6,707 | 0,019 / 0,032 | 2,435 / 3,032 | 8,266 / 9,798 |
| 100 | 8,255 / 11,222 | 0,014 / 0,017 | 3,859 / 4,657 | 12,400 / 16,109 |
| 200 | 17,168 / 18,495 | 0,014 / 0,018 | 4,030 / 4,799 | 21,377 / 23,268 |

| Personen | Personenfolge Median / p95 ms | Funktionsmitgliederfolge Median / p95 ms | vollständiges Personendetail Median / p95 ms |
|---|---:|---:|---:|
| 20 | 4,172 / 5,026 | 4,035 / 4,872 | 4,095 / 4,877 |
| 100 | 8,066 / 10,326 | 6,150 / 7,898 | 5,576 / 7,362 |
| 200 | 12,286 / 14,698 | 10,742 / 12,209 | 9,776 / 12,629 |

Die Personenfolge enthält bei 100/200 weitere 32 Karten; bei 20 ist der bewusste
Endpunktread leer. Funktionsfolge r0 enthält bei 200 noch zwei Mitglieder nach den
vollen ersten 32; bei 20/100 ist der Endpunktread leer. Der gesonderte 200-in-einer-
Funktion-Test prüft außerdem sieben echte Mitgliedsseiten. Detail enthält sämtliche
Planungsrelationen und Operationsdaten der gewählten Person, keine gekürzte Karte.
**Engineering-Charges sind keine gemessenen ART-/Native-Heapwerte.** Hostmedian/p95
sind keine SQLCipher-/Android-/Compose-Navigationsperformance, insbesondere kein
100–150-ms-Produktnachweis. Für den getesteten großen Sechs-Rollen-Bestand ist der
Datenpfad innerhalb 8+8 MiB tragfähig; dies ist keine universelle Größenfreigabe.

### 45.5 Prüfungen, Änderungsumfang und verbleibende BLOCKER

Sieben neue SQLite-Hostmethoden plus weiterentwickelte zwölf Kernmethoden:
alle drei Größen, vollständige Personen-/Gruppenpagingabdeckung ohne Duplikate,
exakte Status-/Funktions-/Filterzahlen, Ohne Funktion, NO_STATUS-Rollenvormerkung,
unbenutzte Rollen, gemischte Planungen, vom Assignee unabhängige Taskbetroffenheit,
Detailgleichheit mit dem bisherigen OperationalBoard (wo dessen Vollbudget zulässt)
und erwarteten vollständigen Fachmodellen für alle 200 Personen, 100 geplante Rollen
in einem Detail über mehrere Batches, UTF16-/NUL-/Unicode-/Zeitgleichstände, lange
Pseudonyme/Funktionsnamen, überlange Orte/Texte vor Payload, ungültige Cross-Action-
Referenzen, stale/fremde Cursor, Mutation/Revoke/UNKNOWN, Threadwechsel, SQL-/Storage-
und Cleanupfehler, verspätete Publication und gemeinsame Budgetverweigerung.
Keine Rollenplanungs- oder Eventpayloadqueries im Kern. Die bestehenden Vollreader-
und Taskpaging-Proofs bleiben Teil des Regressionlaufs.

Die ersten neuen Negativfixtures scheiterten vor dem Reader an UNIQUE-/FK-Constraints
(mehrere geplante Rollen auf dieselbe ID; DROP einer referenzierten Tabelle).
Fixtures gezielt korrigiert, erwartete Fail-closed-Assertions beibehalten. Der erste
100-Rollen-Versuch war eine echte Budgetverweigerung; oben ausdrücklich als solche
beibehalten und um den zugelassenen 32-Rollen-Fall ergänzt.

Reproduktion: `./gradlew :data:testDebugUnitTest --tests '*ActiveCoreStoreTest' --tests
'*CompactPeopleStoreTest' --offline` (für neue Messungen bei UP-TO-DATE gezielt
`--rerun-tasks`). Vollständiger Offline-Lauf: **613 App + 741 Data + 4 Security = 1358 Hosttests,
0 Fehler, 0 Skips**. Nach Ergänzung weiterer Assertions für exakte Filtercounts jeder
Seite und betroffene Taskzahlen aller 200 Karten zusätzlich 19 fokussierte Kern-/
Personenmethoden grün; keine Produktionsänderung zwischen den Läufen. Tabellenzeiten
stammen aus dem vollständigen Offline-Lauf. Offline `:app:assembleDebug`, App-/Data-
`compileDebugAndroidTestKotlin`, Architekturcheck und Produkt-/Demo-Manifestchecks
erfolgreich. Keine Androidtests ausgeführt, kein connectedAndroidTest, keine
Geräteinstallation oder Veränderung installierter Apps.

- [#33](https://github.com/JulianGKK/action-planner/issues/33) /
  [#34](https://github.com/JulianGKK/action-planner/issues/34): nachgewiesener
  200-Personen-Vollgraphblocker für den getesteten 6-/32-Rollen-Bestand behoben;
  größere Rollenkataloge weiterhin explizit budgetbegrenzt. Unabhängiger Review nötig.
- [#35](https://github.com/JulianGKK/action-planner/issues/35): reale Writer-/UNKNOWN-/
  Recovery-/Permissionhooks und atomare Invalidierung vor Writes bleiben offen.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): einziger produktiver
  Snapshot-/Seiten-/Detail-/Formularowner, synchrone Revoke-Lebensdauer und Ersatz-
  Admission offen. Keine Registry oder neue sensible Produkt-RAM-Lebensdauer eingebaut.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): ActivePeople und
  ActiveFunctions verwenden weiter vollständige Boards, eager `forEach` und volle
  Pseudonymjoins. Vor Integration separate Seiten-/Lade-/Fehler-/Leer-/Endzustände,
  stabile Lazy-Keys/Listen, Zähler aus Metadaten statt Kartenzahl, bewusste vollständige
  Editorloads, bounded Vorschlags-/Rollenwahl und Taskfilteranschluss implementieren.
  ProductViewModel, OperationsModel, TaskModel, TaskScreen und Navigation unverändert;
  keine RAM-Hits aktiviert. Reale Content-OnDraw-Messung erst nach Integration.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/CursorWindow-/
  Native-/FD-/Lease-BLOCKER separat unverändert offen. `SQLCipherStatement.close()`
  schließt den Cursor nicht explizit. Host-Statementende und simulierte Cleanupreihenfolge
  sowie frühere FD-PASS beweisen keine vollständige native Cursorfreigabe.

Neue Dateien: `CompactActivePeople.kt`, `CompactPeopleStore.kt`,
`ProductCompactPeopleReader.kt`, `CompactSqlText.kt`, `CompactPeopleStoreTest.kt`.
Geändert: `ActiveCoreRead.kt`, `ActiveCoreStore.kt`, `ProductActiveCoreReader.kt`
(gemeinsame identische Publicationgrenze), `CompactTaskStore.kt` (identischen skalaren
UTF8-Validator extrahiert; Preflight wiederverwendbar), `ActiveCoreStoreTest.kt` und
nur dieser neue Assessmentabschnitt. Keine Dependencies, Klartextpersistenz,
Berechtigungen, Produktwrite-/UI-/Security-/Schemaänderungen. Keine APKs, Schlüssel,
Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder Gerätekennungen im Commit.
Regulärer WIP-Commit/Push mit unabhängigem Remote-SHA-Abgleich; danach **STOPP bis
zum unabhängigen GitHub-Review**. Kein Force-Push, Merge, Release oder Produktupdate.


## 46. Issue #35 – Produktive Mutations- und Invalidierungsverträge

### 46.1 Status und präziser Integrationsumfang

**IMPLEMENTIERT (2026-10-20):** regulärer Branch
`p11-performance-optimization`, sauberer und unabhängig lokal/remote bestätigter
Ausgangspunkt `52b0dcb284abb196027c6bffeb8de417d4dc5778`.
Erster begrenzter produktiver Slice: **ProductTaskRepository.create/edit/transition/
delete**, einschließlich atomarer TaskEvents. Gemeinsamer synchroner Vor-Queue-Port,
getrennte tatsächliche Transaktions-/Cleanupbelege, Einmalabschluss und gegatete
Repositorypublication. Kein pauschaler Umbau anderer Writer.

**GETESTET:** dieselbe TaskStore-Fachlogik, dieselbe Transaktionsbelegfunktion und
dieselbe Queue-/Publicationgrenze mit echtem SQLite/Schema 11; bestehender
ActiveWorkspaceValidity-Vertrag mit neuem Adapter und allen bisherigen Regressionen.
**NOCH NICHT ANGEBUNDEN:** der Adapter an einen produktiven ACTIVE-Snapshotowner,
aktuelle Action-/Permissionbindung und Read-/Editorlebensdauer. Es existiert weiterhin
kein produktiver Snapshotowner oder RAM-Hit. ProductRuntime verwendet ausdrücklich
die begrenzte Metadatenfence `UncachedWorkspaceMutations`: **ramReuseEnabled bleibt
unveränderlich false**, ohne Read-/Hit-/Payload-API. Andere Writerfamilien sind
**UNWIRED / NOT SAFE FOR RAM REUSE**. Dieser Slice gibt keine produktive Wiederverwendung
oder schnelle Navigation frei.

### 46.2 Vollständige Writer-Matrix des gegenwärtigen Produktpfades

Inventar: alle in ProductRuntime verdrahteten Repositories und produktiven Start-/
Maintenance-/Datei-/Keystorepfade, deren Stores/DAOs, Transaktionen und Trigger.
Prototyp-/manuelle Probe-Repositories sind getrennte Testdatenpfade und keine
zusätzlichen Produktwriter. Keine Behauptung über noch nicht implementierte Lösch-
oder Berechtigungssysteme.

Für die Tabellen gelten folgende **konkreten Grenzprofile**; jede Zeile nennt das
zutreffende Profil, damit Queue, Commit, Rollback, UNKNOWN, Cleanup und Publication
nicht aus einem Value-Callback erraten werden:

- **S (bisheriger einzelner SQL-Write):** Einstieg im genannten öffentlichen Repository,
  acquire(READY) vor `worker.execute`; bisher **keine** Workspaceinvalidierung dort.
  Worker: Inventory/SessionDbKeyOwner.withDbKey/ActionDatabaseAccess.use; T =
  `withWriteTransaction` (Room IMMEDIATE), C = äußerer SQL-END-Rücklauf, B2 prüft COMMIT
  nochmals am tatsächlichen Statement. R = Throw löst Room-Rollback aus; normal
  zurückgegebene fachliche Rejections können eine leere Transaktion committen.
  U = kein positiver Outcome-Beleg bei Throw, insbesondere END/Revoke/Cleanup;
  bisher generischer Fehlerpfad statt sicherer Commit-/Rollbackklassifikation.
  L = Native-Preflight-Close, Room.close, parent.cancelAndJoin, driver.close,
  Lifecycle-Healthy-Prüfung, DB-Publicationcheck, Keylease-Wipe/-Scopeende.
  P = `main.execute { context.publish { callback(result) } }` erst nach Rücklauf
  dieser Scopes; Fehler melden bei noch gültigem Kontext Storage-BLOCKED. **UNWIRED.**
- **T (neuer Task-Slice):** acquire/check READY, synchroner Invalidierungsport **vor**
  Workerqueue. T = tatsächliche Room-`useWriterConnection` und nicht verschachtelte
  IMMEDIATE-Transaktion. C/R-Beleg erst nach erfolgreichem äußerem END/ROLLBACK und
  `inTransaction()==false`, noch vor Connectionpool-/DB-/Key-Cleanup. Fachrejection
  ruft ausdrücklich `TransactionScope.rollback(result)`. U = jeder geworfene
  BEGIN/Body/END/ROLLBACK ohne bestätigten Endrücklauf bleibt UNKNOWN; kein Retry.
  L = erst vollständiger normaler Rücklauf aus demselben DB-/Keyscope bestätigt COMPLETE;
  sonst FAILED_OR_UNCONFIRMED, auch bei bereits belegtem COMMITTED. P = Main-Abschluss
  der Invalidierung, Einmalkonsum, danach ursprüngliches Context.publish + aktuelle
  Ticket-/Bindingprüfung. Keine Payload bei unbekanntem/fehlerhaftem Ausgang.
- **L (Leadership):** `run` reserviert einmalige Cleanup-Runnable, acquire READY und
  queue; `mutate`/Grantprüfungen und Requestcounter ergänzen den Kontext. SQL-T/C/R wie S,
  außerhalb SQL ggf. Keystore- und Kryptoschritte. U ist nicht atomar über diese Ressourcen;
  SensitiveProblem wird fachlich abgelehnt, sonst UNAVAILABLE; kein Workspace-Outcome-Beleg.
  Cleanup wischt Passphrasen/temporäre Keys, cancelt Grants bei Revoke/Queuefehler und
  schließt jeden DB-/Keyscope. Main-Publication prüft Request/Grant und ursprüngliches
  Context.publish. Dies ersetzt **keine** gemeinsame Workspaceinvalidierung.
- **D (Dokumente):** `DocumentProductDispatch.run`: acquire READY + Namespace-Ticket
  vor Queue, Worker unter exklusiver Lane, jede unten genannte DB-Phase eigenständig
  transaktional. Keine globale DB/Dateisystematomarität, kein Workspace-Vor-Queue-Hook.
  Persistierte Jobphasen markieren bestätigte Teilschritte; zwischen ihnen gilt UNKNOWN/
  Recoverybedarf. File-/Stream-/Snapshot-/Key-/DBcleanup und Laneende vor endgültiger
  Main-Contextpublication. Sourcefehler: MAINTENANCE_REQUIRED bzw. Rejection, kein
  behaupteter Gesamtrollback. Storage-/Cleanupfehler: BLOCKED; Revoke unterdrückt Callback.
- **M (Maintenance/Start):** bestehender SessionCoordinator autorisiert AUTHENTICATING/
  MAINTENANCE, kein READY-Reuse; Runtime queue mit Cancelprobe. DB-/File-/Keysteps nur
  unter diesem Kontext. Keine neue Sessionautorität oder Recoveryzustandsmaschine.
  READY erst nach gesamter Barriere/Operation-Cleanup, erneuten Kontextchecks und
  `done(success)`; Zwischenfehler bleiben unklar/partial/BLOCKED, keine READY-Freigabe.

Kernlegende: **A** Action, **P** Personen/Karten, **O** Operations/Funktionssummary,
**T** Tasks/Details/Chronik, **Z** globale/gefilterte/personenbezogene Taskzähler.
Module außerhalb des jetzigen retained Kerns sind ausdrücklich zusätzlich genannt;
„kein direkter Kernpayload“ bedeutet niemals Freigabe für einen ungeprüften RAM-Hit.
Bei UNKNOWN, Permission-/Sessionverlust oder Recovery muss der spätere einzige Owner
sämtliche betroffenen Bindings/Seiten invalidieren und darf keinen Scopehit liefern.

| Produktiver Einstieg vor Queue | T / C / R / U / Cleanup / endgültige Publication | betroffene Projektionen | Anschluss |
|---|---|---|---|
| ProductActionRepository.createAction | S; Syntaxprüfung vor run; eine Action-INSERT-Transaktion | A/Aktionsliste; spätere Scopeauswahl | UNWIRED / NOT SAFE FOR RAM REUSE |
| updateAction → changeAction | S; gespeicherte Action == expected, Felder/Status im selben T geprüft; COMMIT-Gate nach UPDATE | A; abgeleitete Kernheader/Editoren | UNWIRED / NOT SAFE FOR RAM REUSE |
| advanceActionStatus → advanceAction | S; erwarteter Status; bei PREPARATION→ACTIVE alle Operationssnapshots + Action-UPDATE atomar; bei COMPLETED completedAt; keine separate Callback-Commitannahme | A/P/O/T/Z, gesamtes Binding/Paging | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductPersonRepository.createPerson/updatePerson → changePerson | S; Persistenzstatus PREPARATION, Duplikatprüfung und INSERT/Rename im selben T | P, personbezogene Label/Editoren, Tasks mit Personenbezug/Z | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductOperationsRepository.createRole/renameRole/deleteRole | S; Scope/Normalisierung/Referenzen im T, RoleDelete IN_USE bei Planung/Status/Taskreferenzen | O/P; Rollenlabels in Tasks/Editoren | UNWIRED / NOT SAFE FOR RAM REUSE |
| planRoles → OperationsStore.plan | S; assignments entfernen/neu einfügen + primary plan im selben T, nur PREPARATION | P/O/Detail-/Editorplanung; spätere ACTIVE-Initialzustände | UNWIRED / NOT SAFE FOR RAM REUSE |
| updateSnapshot → OperationsStore.snapshot | S; vollständiger erwarteter Snapshot, Status/Rolle/Ort und UPDATE im T | P/O, Funktionsmitglieder-/Personenseiten, Detail | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductTaskRepository.create | T; Task-INSERT + genau ein CREATED-INSERT im selben T; keine automatische Wiederholung | T/Z/P-Karten (betroffene Taskzahlen) | IMPLEMENTIERT; Snapshotowner NOCH NICHT ANGEBUNDEN |
| edit | T; vollständiger Taskvergleich im IMMEDIATE-T, UPDATE; kein zusätzliches Event bei Inhaltsedit | T/Z/P-Karten | IMPLEMENTIERT; Snapshotowner NOCH NICHT ANGEBUNDEN |
| transition | T; expected + TaskRules, UPDATE + genau ein fachliches TaskEvent im selben T | T/Z/P-Karten und Chronik | IMPLEMENTIERT; Snapshotowner NOCH NICHT ANGEBUNDEN |
| delete (nur PREPARATION) | T; expected und Status im T; Event-FK-CASCADE; produktiver materials_detach_task-Trigger nullt Materiallink im selben T | T/Z/P; zusätzlich Material-/Linkprojektion außerhalb aktuellem Kern | IMPLEMENTIERT für Kern; Material-RAM bleibt UNWIRED |
| ProductMaterialRepository.create/edit/delete | S; Referenzen, expected, Task-/Personenlinks und Materialänderung im T | Materialsummary/Board/Editor außerhalb Kern; kein direkter Kernpayload | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductExpenseRepository.create/edit/delete | S; Parent + sämtliche Payments atomar, expected/Statusprüfung; keine Zahlungs-Teilpublication | Kostensummary/Board/Editor außerhalb Kern | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductProtocolRepository.createEntry/reviseEntry/markMistaken | S; expectedActionStatus/expectedRevision; Entry/Revision + betroffene/gemeldete Personen + Kennzeichen im selben T; Eingabelisten vor Queue eingefroren | Aktionsprotokollsummary/Timeline/Detail außerhalb Kern; Personenbezug unabhängig von Taskassignee | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductMemoryReportRepository.create/saveSection | S; eingefrorene Abschnittswerte, Header/Revision + Antworten atomar; CAS über Header | Berichtsummary/Editor/Antworten außerhalb Kern | UNWIRED / NOT SAFE FOR RAM REUSE |
| complete/addAddendum | S; expected Header/Status, Abschluss bzw. append-only Nachtrag + Revision im T | Berichtsummary/Detail außerhalb Kern | UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductLeadershipRepository.setup | L; RESERVED-INSERT; Argon2id/Envelope/Devicealias; gesondertes READY-UPDATE mit expected Root; kein atomarer Gesamtcommit | P3b/Leadership-Scopes, Permissionrevision/gesamtes sensibles Binding | UNWIRED / NOT SAFE FOR RAM REUSE |
| save/deleteArea → mutate | L; Grant/Root-Epoch/expected Bereich, INSERT/UPDATE/DELETE im T; key/cipher cleanup im finally | sensibles Personendetail/Grant, gegebenenfalls gesamtes Permissionbinding | UNWIRED / NOT SAFE FOR RAM REUSE |
| deletePerson | L; nur sensitive Bereiche der Person, passphrasen-/rootgeprüfte SQL-Löschung; **kein** Löschen der Basis-Person | P3b/Personendetail/Grant | UNWIRED / NOT SAFE FOR RAM REUSE |
| deleteAction | L; DELETE_PENDING-UPDATE; revoke + Devicealias-Löschung; danach Bereiche/Root in zweitem T entfernen. Teilzustände bewusst nicht als Gesamtrollback interpretieren | alle P3b-/Permissionbindings dieser Aktion; Kern bei geändertem Leserecht invalid | UNWIRED / NOT SAFE FOR RAM REUSE |
| open/revoke und Runtime.leadershipLifetime | L für Open; Request-/Grant-/Keyänderung ohne Fach-SQLwrite; Revoke synchron, Pending-Cleanup idempotent; keine neue globale Berechtigungsrevision implementiert | sensible Detail-/Formularrechte; für späteren Permissionadapter gesamte Actionbindung | UNWIRED für Workspace / NOT SAFE FOR RAM REUSE |

Rollen/Funktionen verleihen **keine** Appberechtigungen. Es gibt keinen produktiven
allgemeinen Permission-/RBAC-Writer; `permissionRevision` muss künftig vom geprüften
Rechteadapter kommen, nicht aus einer Rollenkarte oder UI-Boolean. Änderungen/UNKNOWN
in diesem Bereich sind bis zu dessen Anschluss kein Beleg für RAM-Reuse.

| Dokument-/Recovery-/Startpfad vor Queue | tatsächliche Transaktionen/Commit, Rollback/UNKNOWN, Cleanup und Publication | Projektionen / Anschluss |
|---|---|---|
| ProductDocumentRepository.importDocument → DocumentImportStaging.stage | D: reserveImport → IMPORT_RESERVED-T; advanceImport → IMPORT_WRITING-T **vor** Quelldateiöffnung; CREATE_NEW, Verschlüsselung, Streamclose/fsync; advanceImport(length) → IMPORT_CLOSED-T. Jeder T eigener Commit/Roomrollback, Filezustand bleibt bei Fehler ggf. liegen | Dokumentcount/Listen/Detail/Namespace; Kernbindung bei Recovery/Storagefehler. UNWIRED / NOT SAFE FOR RAM REUSE |
| DocumentPublication.complete | D: gebundene Snapshots; atomarer RENAME_NOREPLACE + beide Directory-syncs; markPublished → FILE_PUBLISHED-T; finish → Dokument-INSERT/Jobentfernung im T. Erfolg erst nach Gesamtpipeline; UNKNOWN zwischen SQL-/Fileschritten, keine optimistische Importpublication | Dokumentmodule/Namespace, Recoverybindung. UNWIRED / NOT SAFE FOR RAM REUSE |
| ProductDocumentRepository.deleteDocument → DocumentDelete.delete | D: read expected; reserveDelete → DELETE_RESERVED-T; detachDeleteParent → Parententfernung + DELETE_FILE_PENDING-T; unlink/fsync/Inventar; finishDelete-T. Keine globale Rückrollbarkeit nach unlink, Job ermöglicht Recovery | Dokumentmodule/Namespace. UNWIRED / NOT SAFE FOR RAM REUSE |
| DocumentReconciliation.run (MAINTENANCE), Abort abgebrochener Imports | M/D: Lane drainAndFence, Inventar; IMPORT_RESERVED/WRITING-Staging entsorgen/syncen und Job entfernen in eigenen abort-T; IMPORT_CLOSED/FILE_PUBLISHED kontrolliert weiterpublizieren, Deletejobs über obige Schritte abschließen; danach frisches Inventar/Jobscheck. Fehler verhindern READY; kein Teil-Recovery als erfolgreicher Kerncommit | gesamte Generation/Bindung/Seiten/Editoren. UNWIRED für Workspace / NOT SAFE FOR RAM REUSE |
| ProductRuntime.authorizedMaintenance → reconcileProductDocuments | M: bestehende unwrapped-Key-Leihe, Dokumentbarriere; SessionDbKeyOwner.bind erst nach Reconciliation; operation.release und Fehler-Keyinvalidate vor Main done(success). Callback bindet keine eigenen Sessionfristen | gesamte Generation/Binding; kein READY-Hit während MAINTENANCE. Snapshot-Revoke-Adapter noch UNWIRED |
| ProductRuntime.start(initialize) / ProductCredentialAuthentication.start | M/Setup: acquire Credentialflow; im Worker ProductInventory.reserve(intent), ProductDbEnvelope.prepareNewRoot (Keystore + Publicdatei), später authentifiziert Envelope schreiben; keine DBtransaktion vor Keyauth. Cancel/Fehler kann PARTIAL hinterlassen; kein erfundener Rollback/Reparaturpfad | gesamte Storage-/Sessionwurzel. UNWIRED für Workspace / NOT SAFE FOR RAM REUSE |
| Initialisierungs-Maintenance: prepareDatabaseDirectory / ActionDatabaseAccess.use(creates) / Inventory.finish | M: Verzeichnis/Room-Schema/DBanlage, erneute Preflights, complete-Marker mit fsync; Einzeldateischritte nicht global atomar. keykopien/finale Scopes werden gewischt/beendet; READY nur nach Barriere | gesamte Storagewurzel/Generation. UNWIRED für Workspace / NOT SAFE FOR RAM REUSE |
| ActionDatabaseAccess.open: Room-Migrationen 1→11 und OnCreate-Constrainttrigger | Implizite mögliche SQLwrites bereits beim Öffnen, vor Repositorybody; unter bestehendem Driver-/Contextguard. Migrationen transaktional von Room, Journal-/Dateierzeugung nicht gleich Fachsnapshot. Kein eigener Workspacehook in bisherigen Read-/Openpfaden; kein neuer Migrationscode hier | alle Projektionen. BLOCKER für spätere Wiederverwendung; Schema muss vor Scopeadmission stabil sein |
| LOCKED/BLOCKED/Close/Grant-Revoke/Recoverybeginn | kein Fach-SQL-T: SessionCoordinator widerruft bestehende Autorität; Runtime invalidiert Keyowner/Grants. Zukünftiger einziger Snapshotowner muss synchron sämtliche Payloadreferenzen freigeben; dies ist nicht durch Taskoutcomes ersetzt | alle Bindings/Seiten/Editoren. #37 NOCH NICHT ANGEBUNDEN |

Nicht vorhandene Pfade explizit: kein produktiver allgemeiner Action-/Basis-Person-
Delete-Endpoint, keine globale Retention-/Purgeimplementierung und kein allgemeiner
P3b-Passphrasenwechsel-/RBAC-Writer. Entsprechende Ziele/Schemastatus sind keine bereits
abgedeckten Writer. Die Schema-FK-/Material-Detachtrigger sind Folgewrites ihrer
Parenttransaktion; künftige Parentdeletepfade müssen diese Module ebenfalls invalidieren.
Neue Pfade müssen **vor** möglicher RAM-Aktivierung dieselbe Matrix erweitern.

### 46.3 Zentraler Vor-Queue-Vertrag und bestehende Validity

Domainport `WorkspaceMutationInvalidation.beforeTaskQueue(actionId,generation)` ist
synchron auf dem Ownerthread, nach bestehender READY-Zulassung und **vor** dem ersten
`worker.execute`. Ein verweigertes Ticket führt zu keinem Writequeue-Eintrag; ein
zweiter überlappender Editor bekommt STALE. Metadaten allein erteilen keine Storage-
oder Writeberechtigung; Persistenzstatus, Referenzen, expected Task und COMMIT werden
weiterhin in der autorisierten Transaktion geprüft.

`ActiveWorkspaceMutationAdapter` delegiert auf **dieselbe** bestehende
ActiveWorkspaceValidity-Instanz. Taskmutation invalidiert TASKS, TASK_COUNTS und PEOPLE
(die neuen Personenkarten enthalten betroffene Taskzahlen). Keine Action-/Operations-
Payload wird durch Taskwrites fachlich geändert. Das jeweilige globale Mutationsepoch-
Fencing verhindert trotzdem Seitenmischung. Pending sperrt sämtliche beginRead-/
canReuse-Versuche dieser Instanz. COMMITTED oder ROLLED_BACK + COMPLETE schließen nur
Pending; beide bleiben dirty bis zu einem vollständigen autorisierten Reload.
UNKNOWN oder FAILED_OR_UNCONFIRMED wird auf RECOVERY_REQUIRED abgebildet. Kein
Repository-Value ruft `publishMutation` auf oder erklärt eine Teilprojection für clean.

Die neue `writerResultCurrent`-Prüfung erlaubt lediglich einen aktuellen fachlichen
Repositorycallback, niemals einen RAM-Hit. Neuer Read/neue Mutation, Action-/Generation-/
Permissionwechsel, Revoke oder Recovery fencen den alten Writerabschluss. Doppelte
Abschlussversuche werden von bestehenden Ticketidentitäten bzw. der Dispatch-Einmalgrenze
abgewiesen. Keine neuen Session-/Berechtigungstokens oder Payloadregistry.

**NOCH NICHT ANGEBUNDEN:** ProductRuntime konstruiert keinen ActiveWorkspaceValidity-
Snapshotowner und installiert den Adapter nicht automatisch. Der Default
UncachedWorkspaceMutations hält nur Epoch + höchstens eine pending/latest Ticketidentität,
keine Action-/Personen-/Taskpayload und keine Read-API. Er zählt und sperrt produktive
Taskarbeit vor Queue; UNKNOWN/unbestätigtes Cleanup sperrt diese Generation. Nach einem
neuen, vom bestehenden READY-Kontext gelieferten Generationwechsel und ohne altes Pending
ist frische **ungecachte** Writebuchführung zulässig. Das restauriert niemals eine
revokte/unklare ActiveWorkspaceValidity, kein Payload und keinen Cache. Verlorene Main-
Dispatches bleiben pending/dirty; keine Workerthread-Reparatur oder automatische Wiederholung.
Die spätere Komposition mit einem einzigen vertrauenswürdigen Owner bleibt #37.

### 46.4 Tatsächlicher Transaktions- und Cleanupbeleg im Taskwriter

`confirmedTaskTransaction` verwendet die bereits geöffnete Room-Writerconnection.
Ein bereits aktiver äußerer T wird verweigert: ein SAVEPOINT-RELEASE ist kein
bestätigter Gesamtcommit. Schon ab Ressourcen-/Openversuch ist der Ausgang UNKNOWN
(Öffnen kann selbst Room-Migrationswrites auslösen); vor dem äußeren T ebenfalls. Erst nach
Rückkehr aus `Transactor.withTransaction(IMMEDIATE)` und überprüft beendetem T wird
COMMITTED (Value) oder ROLLED_BACK (explizite fachliche Rejection) notiert.
Die lokale Room-3.0.3-Quellimplementierung wurde dafür geprüft: withWriteTransaction
ist useWriterConnection + IMMEDIATE; Passthrough führt END/ROLLBACK im finally aus.
Der bestehende B2Driver prüft weiterhin am wirklichen END/COMMIT; unverändert.

Keine Behauptung eines bestätigten Rollbacks bei beliebiger Exception: selbst wenn
SQLite tatsächlich zurückgerollt hat, fehlen nach geworfenem END/ROLLBACK oder
spätem Wrapperfehler ggf. verlässliche Belege. Ein synthetisch erfolgreicher Task-
Rückgabewert ohne diesen Transaktionsbeleg wird verworfen. Kein Commitbeleg aus
`OperationsResult.Value` allein, kein optimistischer Erfolgszustand bei UNKNOWN.

`TaskWriteProgress` trennt Outcome und Cleanup. Öffnen setzt Cleanup auf
FAILED_OR_UNCONFIRMED. COMPLETE wird erst nach dem normalen Rücklauf aus der gesamten
Inventory-/Keylease-/ActionDatabaseAccess-/Room-/Transaktionsarbeit gesetzt. Ein nach
bestätigtem Commit geworfener DB-/Lease-/Publicationcheck erhält den bekannten
COMMITTED-Befund, **aber kein erfolgreicher Gesamtabschluss**; Validity wird unbrauchbar.
Wenn der bestehende Wrapper bei Revoke nach Cleanup wirft, kann der Slice Cleanup
nicht separat beweisen und verweigert konservativ. SessionDbKeyOwner,
ActionDatabaseAccess, SessionCoordinator, StorageAccess und B2Driver wurden dafür
nicht umgebaut. Throw vor jedem Ressourcen-/Transaktionsversuch ist als Nichtausführung
ROLLED_BACK/COMPLETE erkennbar, kein erfolgreicher Fachwrite.

Ein AtomicBoolean konsumiert den Workerdispatch einmal (auch duplizierte Runnables
schreiben nicht zweimal), ein zweites den Mainabschluss. Tatsächlicher Persistenzcommit
kann vor einem später verweigerten Callback liegen; es wird deshalb **nicht erneut
geschrieben**. Main-Publication erfolgt erst nach Cleanup und Ticketfinish, unter dem
ursprünglichen StorageContext und aktueller Metadatenprüfung. Schlägt Mainqueue fehl,
bleibt Pending bestehen; fällt die Workerqueue sicher vor Beginn aus, wird Nichtausführung
abgeschlossen. Keine Reparatur durch zweite Ausführung.

### 46.5 Tests, belegte Fälle und Grenzen

**GETESTET auf echtem SQLite/Schema 11:** TaskStoreData ist ein kleiner interner
Persistenzport. Produktiv delegiert RoomTaskStoreData auf dieselben bisherigen Room-
DAOs; HostTaskData führt echte SQL-Statements auf der vorhandenen In-Memory-Bridge aus.
TaskStore-Regeln, erwarteter vollständiger Taskvergleich und Write-/Eventlogik bleiben
identisch. Hosttransactor führt wirklich BEGIN IMMEDIATE/COMMIT/ROLLBACK aus und
injiziert gezielte Fehler/Races; keine gemockten Commitantworten als SQLbeweis.

Elf neue Data-Hostmethoden prüfen: Vor-Queue-Epoch, Pending/zweite Editoren, Create mit
CREATED, Edit ohne Event, vier fachliche Statuswechsel mit **genau einem** weiteren
Event je Erfolg, STALE-Konflikte mit explizitem Rollback/ohne Event, ungültige Eingaben,
Delete samt Event-CASCADE, Event-/Body-/BEGIN-/END-/ROLLBACKfehler, echten SQLite-
Event-INSERT-Abbruchtrigger, bestätigten SQLcommit mit danach verlorenem Endrücklauf
(UNKNOWN, gespeicherte Zeile + Event bleiben genau einmal), Revoke vor Queue/nach Queue/
vor Commit/nach Commit/vor Publication, Generationwechsel vor Maincallback, beide
Cleanupfehler nach Commit sowie Cleanupfehler nach bestätigtem Rollback, doppelte
Worker/Main-Runnables, Queuefehler, verbotene Nesting-Savepoints und einen Value ohne
Transaktionsbeleg. Bei Fehlern keine Erfolgspublication, kein Retry, kein Doppelevent.

Vier neue App-Adaptermethoden plus alle bestehenden Validity-/Session-/Securitytests:
sauber geladene Projektionen synchron dirty/Pending vor Queue, verweigerter RAMread,
alle Outcome-/Cleanupkombinationen, weiterhin dirty nach Commit/Rollback, terminales
UNKNOWN, Action-/Generation-/Permission-/UNKNOWN-/Revoke-Bindingverlust, neue Reads/
Editoren gegen alte Resultate und doppelte Abschlüsse. Der Produktdefault kann auch
nach neuem Generationstart niemals RAM-Reuse erlauben.

Ein zusätzlicher **nur offline kompilierter** Androidtest
`TaskStorageTest.repositoryWriterEvidenceIsAfterCleanupAndNeverInventedByTaskCallback`
ist für den echten ProductTaskRepository→Room/B2→DB-/Key-Cleanup-Pfad vorbereitet.
Er prüft den Commit bei noch verzögertem Callback, Einmalabschluss, fachlichen Rollback
und genau ein Statusereignis. **NICHT AM GERÄT GETESTET.** Hosttests ersetzen weder
native Room-/SQLCipher-/Keystore-/Cursorfreigabe noch einen realen Session-Revoke-
Instrumentationsnachweis. Keine Geräteinstallation oder connectedAndroidTest.

Reproduktion fokussiert:
`./gradlew :data:testDebugUnitTest --tests '*TaskMutationSqliteTest' :app:testDebugUnitTest --tests '*ActiveWorkspace*' --offline`.
Abschlusslauf mit `:app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest
:app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
:app:verifyPrototypeManifest :app:verifyDemoManifest --offline`: **617 App + 752 Data +
4 Security = 1373 Hosttests, 0 Fehler, 0 Skips**; Architekturcheck, Offline-Appbuild,
beide Androidtest-Kompilationen und Produkt-/Demo-Manifeste grün. Anschließend wurde
die verstärkte UNKNOWN-Assertion vor Tasktransaktionsbeginn mit den fokussierten
Data-/Validity-/Sessiontests nochmals geprüft; keine funktionale Produktionsänderung
zwischen diesen Läufen. Kein Androidtest
installiert oder ausgeführt. `git diff --check` und `git status -sb` geprüft.

### 46.6 BLOCKER und Grenzen für die folgenden Slices

- [#35](https://github.com/JulianGKK/action-planner/issues/35): Taskwritergrenze und
  integrierbarer bestehender Validity-Adapter IMPLEMENTIERT/hostgetestet. Alle übrigen
  obigen Writer und impliziten Migrationen bleiben ausdrücklich **UNWIRED / NOT SAFE
  FOR RAM REUSE**. Kein vollständiger globaler Invalidierungsschutz behauptet.
- [#36](https://github.com/JulianGKK/action-planner/issues/36): vorhandener vollständiger
  expected-Taskvergleich im IMMEDIATE-T bleibt bestehen. Kein neuer revisionsbasierter
  allgemeiner Editor-CAS-/ABA-Vertrag, keine Optimistic-UI-Aktivierung. Monotone Revision,
  Scope-/Formularbindung und Konflikt-UX aller Editoren sind ein eigener Slice.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): produktiver einziger
  Snapshot-/Formular-/Seitenowner und autoritative Action-/Permissionadapter, synchroner
  Payload-Revoke, Recovery-/Generationwechsel sowie Anschluss **aller** Writer fehlen.
  Der Metadatenport ist kein zweiter Payloadowner. Keine längere RAM-Vorhaltung freigegeben.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine Navigation verändert,
  keine RAM-Hits/Snapshotaktivierung und keine neue Produktperformancebehauptung.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/Cleanup-/Native-
  BLOCKER unverändert. SQLCipherStatement.close() schließt den Cursor nicht explizit;
  erfolgreiche DBwrapper-Rückkehr, Hoststatement-Close und frühere FD-PASS beweisen keine
  vollständige native Cursorfreigabe. Dieser Slice erzeugt keinen spekulativen Driverfix.

Unverändert: SessionCoordinator/StorageAccess/SessionDbKeyOwner, SQLCipher/Tink/B2Driver,
Schema 11, 180-s-Absoluttimeout/30-s-READY-Idle und 8+8-MiB-Budget. ProductViewModel,
TaskModel, TaskScreen, übrige Writer und Produktnavigation unangetastet. Keine neuen
Permissions, Dependencies, Klartextdateien oder Registry. Keine Änderung installierter Apps.

Neue Dateien: WorkspaceMutationInvalidation.kt, TaskMutationBoundary.kt,
ActiveWorkspaceMutationAdapter.kt, TaskMutationSqliteTest.kt, HostTaskData.kt und
ActiveWorkspaceMutationAdapterTest.kt. Geändert: ProductTaskRepository.kt,
ProductRuntime.kt (Portkomposition, weiterhin ungecachter Default),
ActiveWorkspaceValidity.kt (reine Writerresultat-Fence), TaskStorageTest.kt (nur offline
vorbereiteter zusätzlicher Androidproof) und dieser Assessmentabschnitt.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen im Commit. Regulärer WIP-Commit/Push; unabhängiger Remote-SHA-Abgleich;
danach **STOPP bis zum unabhängigen GitHub-Review**. Kein Force-Push, Merge, Release
oder Produktupdate. Release 1.0 nicht als performant/produktionsreif freigegeben.


## 47. Issues #35/#37 – Gemeinsame Action-, Personen- und Operationswriter

### 47.1 Implementierter Umfang und Sicherheitskorrektur

**IMPLEMENTIERT (2026-10-20):** sauberer Branch `p11-performance-optimization`,
Ausgangsbasis `ec1468741fc9dbeac77f59f9cf8f13e1352449ff` lokal und unabhängig über
GitHub bestätigt. Neben Tasks jetzt sämtliche produktiven Action-, P3a-Personen-
und Operationswrites mit synchroner Vor-Queue-Invalidierung, tatsächlichem äußeren
Transaktionsbeleg, getrenntem Cleanup und einmaligem Mainabschluss. Keine globale
Cachefreigabe, Navigation oder Installation.

**Nachgewiesener Publicationfehler:** Im bisherigen `ActiveWorkspaceValidity.publish`
wurde `delivered=true` innerhalb des Callbacks gesetzt und nach einem zentralen
`false` trotzdem zurückgegeben. Ein erst nach dem Callback eintretender Revoke wurde
vor dem Rückgabewert nicht verifiziert; derselbe zentrale Callback konnte außerdem
mehrfach Sideeffects ausführen. Gegen die unveränderte Basis liefen die ersten vier
neuen Hostmethoden mit **drei Fehlern**, eine bestehende Ticket-Einmalbedingung war
bereits korrekt. Keine Änderung an SessionCoordinator oder StorageAccess als Umgehung.

**KORRIGIERT UND GETESTET:** nur die erste synchrone Callbackinvokation im geöffneten
Publicationfenster kann Ownerzustand berühren. Doppelte oder nach Rückkehr eintreffende
Callbacks sind inert. Clean-Markierung/positiver Rückgabewert erst nach zentralem
`accepted=true`, tatsächlich ausgeführtem Callback und erneuter Prüfung des ursprünglichen
PUBLICATION-Kontexts sowie aktueller Binding-/Generation-/Permission-/Mutations-/
Loadepochen. Zentral verweigert oder bei den finalen Prüfungen widerrufen:
**false**, keine Wiederverwendung; Gateexception nach Callback: RECOVERY_REQUIRED,
kein positiver Abschluss. Verbrauchte Read-/Mutationtickets bleiben einmalig.

Die fünf neuen Publication-Hostmethoden prüfen zentral `false` **nach** ausgeführtem
Callback, Revoke/Permission-/Generation-/Actionwechsel bzw. Ownerrevoke vor Rückkehr,
doppelte/deferred/späte Callbackinvokation, doppelte Ticketpublication und einen
geworfenen finalen Authoritycheck. Die Sideeffectzähler belegen ausdrücklich:
**Ein bereits ausgeführter Effekt bleibt ausgeführt. false ist keine Rückabwicklung.**

### 47.2 Sicherer atomarer Owner-Publication-Vertrag – weiterhin Anschlussblocker

Die korrigierte Metadatenklasse ist kein produktiver Payloadowner und keine
Transaktion für beliebige UI-Callbacks. Sie kann nachträglich beobachteten Revoke
oder Verweigerung melden, aber keine bereits angezeigte Information zurückholen.
Auch ein positives Ergebnis ist keine dauerhafte Autorisierung nach Methodenrückkehr.
Für [#37](https://github.com/JulianGKK/action-planner/issues/37) verbindlich:

1. Vollständigen, budgetzugelassenen Payload und immutable Referenzhülle **vor** der
   Ownerpublication vorbereiten; sämtliche DB-/Key-/Ressourcenabschlüsse davor.
2. Der Callback unter dem bestehenden zentralen `StorageContext.publish` darf nur den
   vorbereiteten Referenzslot des **einzigen** Owners austauschen, nach Prüfung von
   Scope, Generation, Permission, Action, Mutationsepoche und erwarteter Slotidentität.
   Keine IO, Navigation, Notifications, Formularwrites oder sonstigen irreversiblen
   UI-Effekte als angeblich atomaren Publicationcallback verwenden.
3. Scope-/Permission-/Sessionrevoke muss synchron denselben Owner serialisieren und
   dessen Referenzslot/Seiten/Formulare leeren; nicht erst über einen verspäteten
   UI-Observer. Kein Ownerlock unter dem Koordinatormonitor. Nach zentralem false oder
   unklarem Abschluss darf der Slot nicht als gültig erreichbar bleiben; niemals einen
   alten Slot in eine inzwischen gewechselte Generation zurückkopieren.
4. Acknowledgement nur bei zentraler Annahme **und** noch gültigem gebundenem Slot.
   Auch jeder spätere Zugriff benötigt frische zentrale Autorität. Nachträgliches
   boolesches Prüfen allein macht beliebige UI-Sideeffects nicht atomar.

**NOCH NICHT ANGEBUNDEN / BLOCKER:** dieser Referenzslot-/Revoke-/Lebensdauervertrag
und seine atomare Produktkomposition. Er ist hier dokumentiert, nicht als bereits
implementierter Snapshotowner ausgegeben. ProductViewModel/TaskModel/TaskScreen und
Produktnavigation bleiben unangetastet; keine produktiven RAM-Hits.

### 47.3 Ein Port, eine Epoche, alle Kernwriter

`WorkspaceMutationInvalidation` wird erweitert, nicht durch eine zweite Epochmaschine
ersetzt. Feste `WorkspaceWriterFamily`-Metadaten: TASK, ACTION_CREATE, ACTION_EDIT,
ACTION_STATUS, PERSON, OPERATIONS. ACTION_CREATE ist eine globale Actionlistenänderung
mit explizit null Scope-ID; alle übrigen Anfragen tragen ihre Action-ID. Kein neues
Session-/Berechtigungstoken und kein vom UI frei wählbarer Projektions-Subset.

**ProductRuntime injiziert genau dieselbe Portinstanz** in Action-, Personen-,
Operations- und Taskrepository. Keine zusätzlichen unabhängigen Family-Epochen.
Der ungecachte Default `UncachedWorkspaceMutations` besitzt weiterhin eine einzige
Epoche und höchstens eine Pending-/Latest-Identität. Seine RAM-Reuse-Antwort bleibt
unveränderlich false. Die bisherigen Standalone-Konstruktoren bleiben für isolierte
ungecachte Komponenten/Tests quellkompatibel; sie dürfen nicht als Komposition eines
gemeinsamen Produkt-Snapshots verwendet werden. Dafür ist der gemeinsame injizierte
Port zwingend. Keine Registry und kein zweiter Snapshotowner.

Der bestehende `ActiveWorkspaceMutationAdapter` delegiert weiterhin auf **dieselbe**
ActiveWorkspaceValidity-Instanz. Tasks invalidieren wie zuvor PEOPLE/TASKS/TASK_COUNTS;
alle neuen Kernfamilien konservativ **alle fünf Projektionen**, einschließlich Action,
Operations/Funktionen, Personen- und Tasklabels. `beginMutation` erhöht genau einmal
Mutation-/Loadepoche und verwirft alte Read-/Patchtickets. Damit sind alte Kernseiten,
Details und komplette Binding+Epoch-Snapshotidentitäten vor dem Writequeue-Eintrag
ungültig. Pending verhindert jeden Reuse-/Readbeginn dieser Validityinstanz.

Nach Commit oder bestätigtem Rollback bleiben alle betroffenen Teile dirty. Kein
Repositorycallback ist ein vollständiger Kernpatch und keiner ruft publishMutation
für einen Teilpayload auf. Bei Action-/Generation-/Permissionwechsel oder ACTIVE-
Statusausstieg muss die vertrauenswürdige Bindingquelle ungültig werden; die Tests
prüfen, dass die alte Instanz dann terminal revokt wird. Eine neue ACTIVE-Bindung
braucht einen neuen, vollständig verifizierten Scope. Persistierte ACTIVE-Preflights
verweigern außerdem einen Kernread für inzwischen COMPLETED. Der zukünftige einzige
Owner-/Bindingadapter bleibt ausdrücklich unverdrahtet (#37).

Alte Task-only-Portimplementierungen verweigern neue Familien standardmäßig mit null,
statt still an deren Invalidierung vorbeizuschreiben. Falsche Action/Generation am
ACTIVE-Adapter: keine Queue, alte Validity revokt. Änderungen an anderen Aktionen
benötigen die spätere korrekte Owner-/Scope-Routingkomposition; keine Lockerung des
bestehenden Single-ACTIVE-Bindingvertrags. Produktiver ungecachter Default ist nicht
auf eine UI-Action gebunden und verwendet weiterhin die reale READY-Autorität.

### 47.4 Tatsächliche Transaktion, Cleanup und Writer-Matrixdelta

`WorkspaceWriteBoundary` und `confirmedWorkspaceTransaction` sind die gemeinsame
Implementierung des bereits geprüften Taskvertrags. Alte Taskseams `TaskMutationBoundary`,
`TaskWriteProgress` und `confirmedTaskTransaction` bleiben als dünne Delegates/Typalias
erhalten; **die vorhandenen Taskwriter-Proofquellen wurden nicht geändert**.
Ein reiner interner DAO-Port `CoreWriterData` erlaubt denselben Action-/Personen-/
Operationsstore auf echtem SQLite im Hosttest; produktiv delegiert er auf dieselben
Room-DAOs. Keine neue Datenbank-/Schlüssel-/Storageautorität.

Für jede der folgenden Zeilen: BEGIN nach READY-Zulassung synchron vor worker.execute;
Worker umschließt Inventory + originale DB-Keylease + ActionDatabaseAccess.use;
Room.useWriterConnection + neuer **äußerer** IMMEDIATE-T; kein bereits offener Parent-T
zugelassen. Fachresultate steuern nur den gewünschten Commit oder expliziten
`TransactionScope.rollback(result)`. **Erst der erfolgreiche äußere END/ROLLBACK-
Rücklauf mit inTransaction()==false ist der Beleg.** Geworfener Open/BEGIN/Body/END/
ROLLBACK: UNKNOWN, selbst wenn SQL in Wirklichkeit bereits committed oder zurückgerollt
ist. Kein SAVEPOINT-Erfolg als Gesamtcommit und keine automatische Wiederholung.

COMMITTED / ROLLED_BACK / UNKNOWN und COMPLETE / FAILED_OR_UNCONFIRMED bleiben getrennt.
COMPLETE erst nach normaler Rückkehr aus dem gesamten DB-/Keyscope; ein bekannter
Commit mit geworfenem Cleanup ist kein erfolgreicher Gesamtabschluss. Main-Finish
meldet diesen Beleg einmal, danach ursprüngliche Contextpublication + aktuelle
Ticketprüfung. Doppelter Workerdispatch führt nicht zu einem zweiten Write; doppelter
Mainabschluss nicht zu einem zweiten Callback. Fehlender Maindispatch bleibt Pending.

| Produktiver Writeeinstieg | tatsächlicher T / fachliche Besonderheit | konservativ invalidiert vor Queue | neuer Anschluss |
|---|---|---|---|
| ProductActionRepository.createAction | neue UUID-Action, DRAFT-INSERT; Syntaxablehnung bleibt vor Queue wie bisher; äußerer Commit separat bestätigt | alle Kernteile + Actionlisten-/Bindingepoch | IMPLEMENTIERT |
| updateAction → ActionWriterStore.change | unveränderter vollständiger expected-Actionvergleich; Felder/Status und UPDATE im selben T | alle Kernteile, Seiten, Header/Details | IMPLEMENTIERT |
| advanceActionStatus → ActionWriterStore.advance | expectedStatus; PREPARATION→ACTIVE: sämtliche Personensnapshots aus Planung + Actionstatus **atomar**; ACTIVE→COMPLETED: Status + completedAt; kein unabhängiger Snapshotcommit | gesamter Kern, Seiten/Details und Binding+Epochidentität | IMPLEMENTIERT |
| ProductPersonRepository.createPerson/updatePerson | unveränderte PREPARATION-Regel; Duplikatcheck/INSERT oder Rename im T; kein neuer expected-Person-CAS | Personenkarten, Funktionsmitglieder, Operations-/Tasklabels, Details; konservativ gesamter Kern | IMPLEMENTIERT; #36 bleibt offen |
| ProductOperationsRepository.createRole/renameRole/deleteRole | bestehende Phasenregeln/Normalisierung/Duplikate; Delete prüft Planungs-/Live-/Taskreferenzen, IN_USE bleibt Rejection | Funktionsidentitäten/-zähler/-mitglieder, Personen-/Tasklabels/Details; gesamter Kern | IMPLEMENTIERT |
| planRoles | primary plan/assignments im selben T; vor Queue ausgewählte roleIds defensiv eingefroren | Planung/Details, spätere ACTIVE-Snapshots; gesamter Kern | IMPLEMENTIERT |
| updateSnapshot | bisheriger vollständiger expected-Operationssnapshot; Status/Rolle/Ort/Updatezeit im T; fremde Rolle wird auch bei danach löschendem Status verweigert | Statusverteilung, exakte ON_SITE-Zähler/-Mitglieder, Ohne Funktion und Personen-/Taskanzeigen; gesamter Kern | IMPLEMENTIERT |
| Tasks/TaskEvents | unveränderte Fachlogik/atomare Ereignisse aus §46; dieselbe gemeinsame Dispatch-/Transaktionsimplementierung | bisherige konservative Taskteile + globale Epoche | ERHALTEN / GETESTET |

Die alten `ActionDatabase.changeAction/advanceAction/changePerson`-Transaktionswrappers
bleiben für bestehende Storageproofs verfügbar. **Produktive neue Writepfade rufen
Storebodies im gemeinsamen äußeren T auf**, nicht diese Wrappers als vermeintliche
Commitbelege innerhalb eines Parent-T. Sämtliche bisherigen Persistenz-/Status-/
Referenz-/COMMIT-Gates bleiben erhalten; Schema/DAOs/Driver mussten nicht geändert werden.

API-Grenzen ausdrücklich:

- Zweiter überlappender Write wird vor Queue verweigert; es gibt keine unbegrenzte
  Writerqueue. Actionänderung/Operations/Tasks erhalten vorhandenes STALE, Personen
  vorhandenes NOT_ALLOWED (das P3a-Resultat kennt kein BUSY/STALE). Das ist eine
  Vor-Queue-Ablehnung **ohne** SQL-/Rollbackbeleg, keine behauptete DB-Rechteänderung.
- `createAction` hat nur `(Action)->Unit`, keinen Rejectionkanal. Bei verweigertem
  Queuezugang daher kein Callback und kein Write, wie bei bisheriger Authorityablehnung.
  Keine erfundene Action, kein Korruptionssignal. Vollständiges Busy-/Fehlerfeedback
  benötigt einen eigenen API-/Editorvertrag; hier nicht als gelöst ausgegeben.
- Pseudonymänderungen bleiben PREPARATION-only; Rollen-Rename/-Delete/Planung ebenso.
  Kein ACTIVE-Schreibrecht hinzugefügt. Das Invalidierungsprotokoll gilt auch für diese
  Vorbereitungsschritte, weil sie spätere Kernlabels/Snapshots bestimmen.
- Zwei nacheinander zugelassene Personenänderungen aus alten Formularen können weiter
  beide committen; der letzte Pseudonymwrite gewinnt. Der neue echte SQLtest belegt
  dies ausdrücklich als **unbehobenen** Expected-Person-CAS-/Lost-Updatefall aus #36.

### 47.5 Konkrete Tests und Offlineergebnis

**GETESTET:** zehn neue CoreWriterIntegrationTest-Hostmethoden auf echtem SQLite mit
allen Schema-11-Tabellen/Indizes/FKs. Tatsächliche unveränderte Storelogik mit echten
DAO-SQLwrites; derselbe gemeinsame Transaktions-/Dispatchcode wie im Produkt. Native
Room-/Keyscope- und Bindingrennen bleiben im Host vertrauenswürdig simuliert.

- Actioncreate/Edit/STALE und beide wichtigen Statuswechsel; primäre Rollenplanung
  wird bei ACTIVE vollständig in NO_STATUS-Snapshots übernommen. Ein echter SQLite-
  Abbruchtrigger beim zweiten Personensnapshot lässt **null** Teilsnapshots und
  PREPARATION unverändert zurück, UNKNOWN ohne Callback.
- Personencreate/Rename/Duplikat und bewusst nachgewiesener fehlender CAS; Rollencreate/
  Rename/Delete/IN_USE/Planung und Operationsstatus. Frischer kompakter SQL-Kernread
  bestätigt richtige Pseudonyme/Rollennamen, drei Personen, zwei ON_SITE, eine IN_CUSTODY,
  exakt eine Person ohne Funktion und eine verwendete Funktion. Kein RAM-Hit.
- Gemeinsames Pending quer über Personen/Operations/Tasks/Actioncreate, exakt eine
  Epoche und ein Writequeueeintrag. Legacy-Blank-Taskscope weiterhin ohne Write/Epoch.
- Für **jede der fünf neuen Writearten** END-vor-/END-nach-Fehler und DB-/Key-Cleanupfehler,
  Revoke initial/nach Queue/vor Commit/nach Commit/vor Publication, Action-/Permission-/
  Generationwechsel und doppelte Worker-/Mainrunnables. Persistierte Effekte werden von
  Callbackfreigabe getrennt geprüft; kein Doppeleffekt durch automatische Wiederholung.
- Fachrejection **nach** tatsächlichem temporärem SQLwrite: erfolgreiches ROLLBACK entfernt
  ihn; geworfenes Rollback ist UNKNOWN, nicht aus dem Rejectionobjekt als bestätigt
  klassifiziert. Cleanupfehler nach bestätigtem Rollback ebenfalls kein Callback.

Drei neue CoreWorkspaceMutationAdapterTest-Methoden prüfen jede Family gegen denselben
bestehenden Validityowner: vollständige abhängige Dirtysets, Pending gegen RAMread,
einheitliche Epoche, Task/Core-Überschneidung, Statusausstieg, fremde Scope-ID, Generation,
Permission, UNKNOWN/Cleanup und fail-closed Verhalten alter Task-only-Adapter.
Dazu fünf Publication-Racemethoden und sämtliche vorhandenen Task-/Security-Proofs.

**NUR OFFLINE VORBEREITET/KOMPILIERT:**
`CoreWriterBoundaryStorageTest.sharedNativeWritersAndFrozenPlansKeepConfirmedOutcomesAndOneEpoch`
übt die tatsächlichen Repositories/Room/B2 mit einem gemeinsamen Port, Actionstatus,
Personen/Rollen, vor Queue eingefrorenen Plänen, sieben bestätigten Writes/einer Epoche
pro Write und doppeltem Workerdispatch. Noch **nicht am Gerät ausgeführt**. Die vorhandenen
Android-/Taskproofs bleiben unverändert und werden nur kompiliert.

Reproduktion fokussiert:
`./gradlew :data:testDebugUnitTest --tests '*CoreWriterIntegrationTest' --tests '*TaskMutationSqliteTest' :app:testDebugUnitTest --tests '*ActiveWorkspace*' --tests '*CoreWorkspaceMutationAdapterTest' --offline`.
Vollständiger Abschlusslauf:
`:app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :app:assembleDebug
:data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
:app:verifyPrototypeManifest :app:verifyDemoManifest --offline`.
**625 App + 762 Data + 4 Security = 1391 Hosttests, 0 Fehler, 0 Skips.** Architekturcheck,
Offline-Appbuild, beide Androidtest-Kompilationen und Produkt-/Demo-Manifeste erfolgreich.
Keine Geräteinstallation und kein connectedAndroidTest. `git diff --check` sowie
`git status -sb` geprüft.

### 47.6 Verbleibende BLOCKER und Änderungsgrenzen

- [#35](https://github.com/JulianGKK/action-planner/issues/35): Action/Personen/Operations/
  Tasks produktiv an gemeinsame Writergrenze angeschlossen und hostgetestet. Kein Pfad
  erforderte Lockerung eines bestehenden Sicherheitsvertrags. Material/Kosten,
  Aktionsprotokoll/Gedächtnisprotokolle, Dokument-Staging/Publication/Delete/Recovery,
  P3b/Leadership/Berechtigungen, Maintenance/Initialisierung sowie implizite Schema-
  Migrationswrites bleiben **UNWIRED / NOT SAFE FOR RAM REUSE**, siehe vollständige
  Matrix §46. Keine vollständige globale Invalidierungsabdeckung behauptet.
- [#36](https://github.com/JulianGKK/action-planner/issues/36): Personen-CAS/Lost Updates,
  monotone Revision/ABA, familienübergreifendes Editor-/Busy-/Fehlerfeedback und
  Formularscope bleiben eigene Arbeit; bestehender Action-/Operations-/Taskvergleich
  wird nicht als universeller CAS ausgegeben.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): atomarer einziger
  Payload-/Referenzslot-/Formular-/Seitenowner samt synchronem Revoke und geprüfter
  Bindingquelle weiter **NOCH NICHT ANGEBUNDEN / BLOCKER**. Falscher Publication-
  Erfolgswert korrigiert; irreversible Callbacksideeffects nicht rückgängig behauptet.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine Navigation, RAM-Hits,
  Snapshotaktivierung oder neue Android-Performanceaussage.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/Cleanup-/Native-
  Risiko unverändert offen. `SQLCipherStatement.close()` schließt den Cursor nicht
  explizit; Wrapper-/Hostabschluss und frühere FD-PASS beweisen keine vollständige
  native Cursorfreigabe. Kein spekulativer Driver-/SQLCipherfix.

Unverändert: SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink,
B2Driver, Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Budget. Die vorhandenen
Writerbodies behalten ihre bisherigen Datenlese-/Fachregeln; keine neue Retention-
freigabe für Editor-/Writepayload oder produktiven Snapshot. ProductViewModel,
TaskModel, TaskScreen und Navigation unangetastet. Keine neuen Dependencies/Permissions,
Klartextpersistenz, installierten Apps oder Produktupdates.

Neue Dateien: WorkspaceWriteBoundary.kt, CoreWriterData.kt, HostCoreWriterData.kt,
CoreWriterIntegrationTest.kt, ActiveWorkspacePublicationRaceTest.kt,
CoreWorkspaceMutationAdapterTest.kt und CoreWriterBoundaryStorageTest.kt.
Geändert: WorkspaceMutationInvalidation.kt, ActiveWorkspaceValidity.kt,
ActiveWorkspaceMutationAdapter.kt, TaskMutationBoundary.kt (nur gemeinsame Delegation),
ProductRuntime.kt, ProductActionRepository.kt, ActionMutations.kt,
ProductPersonRepository.kt, ProductOperationsRepository.kt und dieser Abschnitt.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen im Commit. Regulärer WIP-Commit/Push, unabhängiger Remote-SHA-Abgleich;
danach **STOPP bis zum unabhängigen GitHub-Review**. Kein Force-Push, Merge oder Release.
Keine Freigabe von Release 1.0 als performant/produktionsreif.
