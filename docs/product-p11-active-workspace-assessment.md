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

## 48. Issue #36 – P3a-Personen-CAS und veraltete Formulare

### 48.1 Reproduzierter Ausgangsfehler und implementierter Vertrag

Basis dieses Slices: `58c48be7a7bace81e379a3df21ca8c5790852d94`.
Der unveränderte Hosttest
`missingPersonCasRemainsAnExplicitUnfixedLostUpdateContract` wurde zunächst erfolgreich
gegen den Ausgangscode ausgeführt: Zwei nacheinander akzeptierte Rename-Writes ließen
den zweiten Wert zurück. Das war ein **bestätigter Lost Update**, kein positiver CAS-Proof.
Der Nachfolgetest `stalePersonEditorIsRejectedAndFirstValueSurvives` verlangt jetzt
STALE und den unveränderten Wert des ersten Editors.

**IMPLEMENTIERT:** `PersonRepository.updatePerson(actionId, expected: Person, pseudonym, publish)`
verlangt den vollständigen unveränderlichen Editor-Ausgangsstand. Der alte öffentliche
ID-only-Rename-Vertrag ist entfernt; auch der interne Storage-Testwrapper und Store
nehmen keinen ungeschützten ID-only-Updateauftrag mehr an. Bestehende Lesepfade und
Create bleiben erhalten. Testdoubles und tatsächliche API-Aufrufstellen sind angepasst.

Der produktive `PersonWriterStore` prüft weiterhin zuerst aktuelle READY-Autorität,
existierende Aktion, PREPARATION-only-Schreibregel und gültiges neues Pseudonym.
Innerhalb **derselben äußeren IMMEDIATE-Transaktion** wie das spätere UPDATE werden
Action-ID und die vollständige aktuelle `Person` mit `expected` verglichen: Person-ID,
Action-ID, exaktes bisheriges Pseudonym einschließlich Schreibweise/Whitespace und
`createdAt`. Schema-11-Normalisierungsinkonsistenzen bleiben Storagefehler statt
Rename-Reparatur. Erwartete fremde Action oder abweichender vorhandener Personenstand:
**STALE vor UPDATE**. Fehlende Person: **NOT_FOUND**; fehlende Aktion: ACTION_NOT_FOUND.
PREPARATION-Verweigerung bleibt NOT_ALLOWED, Duplicate-Pseudonym bleibt eigener Konflikt.

Die bestehende `WorkspaceWriteBoundary` bestätigt den fachlichen Konflikt erst nach
Rückkehr des tatsächlichen ROLLBACK und vollständigem DB-/Lease-Cleanup. Ein Rejection-
Objekt allein beweist keinen Rollback. Geworfener/unbestätigter Transaktionsabschluss
bleibt UNKNOWN, Cleanup separat FAILED_OR_UNCONFIRMED. Keine Erfolgspublication, kein
Retry und kein RAM-Resultat bei solchen Fehlern. Ein bestätigter Commit bleibt von der
zulässigen späteren UI-Publication getrennt; Revoke kann diese verhindern, ohne den
bereits persistierten Write rückgängig zu machen.

### 48.2 Gezieltes P3a-Formularverhalten

**IMPLEMENTIERT:** `ProductViewModel.savePerson()` reicht den beim Öffnen des Editors
festgehaltenen `editingPerson`-Snapshot unmittelbar als `expected` durch. Kein frischer
DB-Snapshot beim Speichern und kein automatischer Konflikt-Retry. STALE schließt das
Formular nicht, ersetzt weder den Snapshot noch den lokalen Compose-Pseudonymentwurf
und erzeugt kein „Person gespeichert“. Ein neuer Speicherversuch entfernt auch ein
noch sichtbares Erfolgsbanner eines früheren Vorgangs. Die sichtbare Meldung erklärt den Konflikt.

Der ausdrücklich beschriftete Button „Aktuellen Stand neu laden“ erklärt vorab, dass
dabei der Entwurf ersetzt wird. Nur diese Benutzeraktion liest die Person erneut.
Ein gültiger vollständiger Rückgabestand derselben Person/Aktion ersetzt Snapshot und
Entwurf über eine Editor-Revision; Fehler/Missing/Foreign-Identity ersetzen nichts.
Request-, READY-, Generation-, Action- und Formularprüfungen verhindern die Übernahme
verspäteter Ergebnisse nach Lock oder Navigation. Der Reloadcallback wird einmalig
konsumiert. Die Revision enthält keine Payload und ist keine persistierte Personen-
oder Autoritätsrevision. `navigateActive` und `clearPersons` bleiben unverändert.

### 48.3 GETESTET und reproduzierbare Validierung

Echtes SQLite mit vollständigem Schema 11/FKs/Indizes, unveränderte gemeinsame
Transaktions-/Dispatchgrenze und dieselbe produktive Storelogik:

- Erfolgreicher Rename erhält ID/Action/createdAt; ein neuer sequenzieller Editor kann
  den bestätigten Stand weiterbearbeiten. Zwei Editoren desselben alten Standes:
  zweiter Write STALE, erster Wert bleibt erhalten, bestätigter ROLLED_BACK/COMPLETE.
- Abweichende Pseudonyme, Person-ID, Action-ID und createdAt; fehlende Person;
  SQL-Prepare-Beobachtung belegt **null Personen-UPDATEs** für diese Rejections.
  Bestehende Duplicate-/PREPARATION-/ACTIVE-Regeln und Cross-Action-Isolation erhalten.
- Rename-spezifisch UNKNOWN vor/nach physischem Commit, Cleanupfehler, fehlgeschlagener
  Rollback, Revoke nach Queue/vor/nach Commit, Permission-/Generation-/Actionwechsel,
  Queuekonkurrenz, doppelte Worker-/Mainrunnables und verspätete Callbacks.
  Persistierter Zustand und Callbackfreigabe werden getrennt geprüft.
- Formular-Porttests: tatsächlich eingefrorenes `expected`, kein Detailread beim Save,
  STALE ohne Erfolgsbanner/Formularverlust, expliziter Reload, fehlgeschlagener Reload,
  fremde Identität sowie Lock/Actionwechsel und doppelte/veraltete Reloadcallbacks.
- Bestehende Task-/Corewriter-, Validity-/Publication- und Securityregressionen laufen
  mit. Unbeteiligte Writerimplementierungen und Taskproofs werden nicht geändert.

Room-/Keyscope-/Bindingrennen werden im Host weiterhin vertrauenswürdig simuliert;
das ist kein Android-Native-Laufzeitnachweis. Der neue
`PersonStorageTest.staleEditorCannotReplaceConfirmedRename` und die vorhandenen
Android-Storageproofs werden **nur offline kompiliert**, nicht installiert/ausgeführt.

Fokussierte Reproduktion:
`./gradlew :data:testDebugUnitTest --tests '*CoreWriterIntegrationTest' :app:testDebugUnitTest --tests '*PersonViewModelTest' --offline`.
Vollständiger Abschlusslauf:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`.
**630 App + 766 Data + 4 Security = 1400 Hosttests, 0 Fehler, 0 Skips.**
Domain-Testtask ohne Testquellen; keine zusätzlichen Tests daraus gezählt.
Vollständiger Abschlusslauf erfolgreich; nach der letzten Bannerkorrektur sämtliche
App-Hosttests, Offline-Appbuild, App-Androidtest-Kompilation und Manifestchecks erneut
erfolgreich. Data-Androidtests ebenfalls offline kompiliert. Architekturcheck und
Produkt-/Demo-Manifeste erfolgreich. Bestehende Kotlin-/Android-Deprecationwarnungen
bleiben sichtbar; kein Testfehler wurde unterdrückt.

### 48.4 Ehrliche CAS-Grenzen und offene BLOCKER

**GETESTETER ABA-NEGATIVFALL:** A → B → A stellt bei unveränderter ID/Action/createdAt
exakt denselben `Person`-Wert wieder her. Ein noch auf A basierender alter Editor wird
danach akzeptiert. Vollzustandsvergleich verhindert gewöhnliche veraltete Updates,
beweist aber **keine vollständige ABA-Sicherheit**. Schema 11 hat keine verbindliche
monotone Personenrevision; Mutationsepoche und Zeitstempel ersetzen diese nicht.

[#36](https://github.com/JulianGKK/action-planner/issues/36): Für vollständigen ABA-Schutz
ist später ausdrücklich über eine persistierte monotone Personenrevision und deren
atomare Fortschreibung in allen entsprechenden Writes sowie den Editor-Expected-
Vertrag zu entscheiden, einschließlich Schema-/Migrationsfreigabe. Keine heimliche
Migration in diesem Slice. Operations-/Task-/Action-Editor- und Revisionslücken werden
hier nicht pauschal geschlossen; bestehende Vergleichsverträge gelten unverändert.
Familienübergreifendes Busy-/Editorfeedback bleibt gesonderte Arbeit.

[#35](https://github.com/JulianGKK/action-planner/issues/35): Gemeinsamer Writerport,
Vor-Queue-Invalidierung, eine Mutationsepoche und tatsächliche Commit-/Rollback-/
Cleanup-/Publicationgrenze bleiben unverändert. Weitere Writerfamilien gemäß §46/47
bleiben **UNWIRED / NOT SAFE FOR RAM REUSE**.
[#37](https://github.com/JulianGKK/action-planner/issues/37): Kein Snapshotowner und
keine RAM-Hits aktiviert. [#38](https://github.com/JulianGKK/action-planner/issues/38):
Keine Navigationsintegration oder Performancefreigabe.
[#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/Ressourcen-BLOCKER
weiter offen: `SQLCipherStatement.close()` schließt den Cursor nicht explizit;
Hostcleanup/FD-PASS beweisen keine vollständige native Cursorfreigabe.

Unverändert bleiben SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/
Tink/B2Driver, Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Budget. Keine neuen
Rechte, Klartextpersistenz oder Dependencies. Keine Geräteinstallation/Instrumentation,
keine Änderung installierter Apps. Geändert sind Personen-Domain/API, Personenstore,
die konkrete P3a-Formularübergabe samt Reload, notwendige Test-/Fixture-Signaturen und
dieser Abschnitt. Kein Umbau von TaskModel/TaskScreen oder Produktnavigation.

`git diff --check` und `git status -sb` vor dem regulären WIP-Commit geprüft.
Nur Code/Tests/Dokumentation; keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials,
persönlichen Pfade oder Gerätekennungen. Regulärer Push und unabhängiger Remote-SHA-
Abgleich, danach **STOPP bis zum unabhängigen GitHub-Review**. Kein Force-Push, Merge,
Release oder Produktupdate. Keine Produktionsreife von Release 1.0 behauptet.

## 49. Issue #36 – P3a-Rollenplanung gegen Lost Updates

### 49.1 Ausgangsnachweis und implementierter Expected-Vertrag

Remote-HEAD und lokaler HEAD wurden **vor Änderungen** unabhängig gegen die verbindliche
Basis `21311641df4536602d2a604d557d2c9506d6c475` geprüft; Arbeitsbaum sauber.
Der zunächst ausgeführte echte SQLite-/Schema-11-Hosttest
`rolePlanLostUpdateBaselineAcceptsBothOldEditors` bestätigte den Ausgangsfehler:
A plante `r0` als Hauptfunktion; B speicherte danach den Entwurf aus demselben alten
leeren Ausgangsstand erfolgreich und entfernte As bestätigte Planung. Beide Writes
wurden akzeptiert. Der Test ist jetzt zum positiven Konfliktproof
`rolePlanOldEditorsConflictAndConfirmedFirstPlanSurvives` weiterentwickelt.

**IMPLEMENTIERT:** Der öffentliche `OperationsRepository.planRoles` und der interne
`OperationsStore.plan` verlangen zusätzlich `ExpectedPersonRolePlan`. Kein verbleibender
ungeschützter Plan-Write-Overload. Dieser Wert bindet Action-ID, Person-ID, vollständige
Rollenmenge und `primaryRoleId`. Seine Konstruktion kopiert die Rollenmenge in eine
nicht veränderbare Menge; auch ein Cast auf MutableSet kann sie nicht mutieren.
Der Entwurf bleibt ein separater `PersonRolePlan`; der Repositoryport friert weiterhin
auch dessen Menge synchron vor dem Einreihen ein. Kein Nachladen eines neuen Expected
beim Speichern und keine UI-Boolean-Autorität.

Das bestehende fachliche Modell hat keinen Unterschied zwischen einer fehlenden
`person_role_plans`-Zeile ohne Assignments und einer gespeicherten leeren Planung mit
null Hauptfunktion. Beide sind bewusst `PersonRolePlan(emptySet(), null)` und vergleichen
sich gleich. Leere gegenüber belegter Planung und abweichende Hauptfunktion werden
vollständig verglichen. Eine reine physische Zeilenexistenz ist keine neue fachliche
Revision; keine Schemamigration oder geänderte bestehende Board-Semantik.

### 49.2 Transaktion, Outcome und Cleanup

**IMPLEMENTIERT:** Innerhalb derselben bestehenden äußeren **IMMEDIATE**-Transaktion
werden aktuelle READY-Autorität, Actionstatus PREPARATION, zugehörige existierende
Person und Expected-Scope geprüft. Zwei neue ausschließlich personenbezogene DAO-
SELECTs lesen vollständige Assignments und Planzeile. Bestehende Rollenreferenzen und
Primary-Konsistenz werden geprüft. Vollvergleich gegen Expected geschieht **vor dem
ersten DELETE/INSERT**. Abweichung: `OperationsFailure.STALE`. Fehlende Person/Aktion
oder fehlende/aktionsfremde gewünschte Rolle: NOT_FOUND; unzulässiger Status NOT_ALLOWED;
ungültiger Entwurf INVALID. Ungültige persistierte Zustände bleiben Storagefehler.
Kein Entfernen alter Assignments und kein Einsetzen neuer Hauptfunktion bei Rejection.

`WorkspaceWriteBoundary`, `WorkspaceMutationInvalidation` und
`confirmedWorkspaceTransaction` bleiben unverändert. Eine innerhalb der Transaktion
entstandene fachliche STALE-Rejection wird erst nach tatsächlich zurückgekehrtem äußeren
Rollback und DB-/Lease-Cleanup publiziert: **ROLLED_BACK / COMPLETE**. Erfolgreiche
Writes benötigen **COMMITTED / COMPLETE** und anschließend weiterhin gültige zentrale
Publication. Vor-Queue-Pendingverweigerung bleibt die bestehende separate STALE-
Busyantwort ohne neue DB-Transaktion; sie ist kein Beleg eines DB-Konflikts/Rollbacks.

Fehlender Transaktionsabschluss oder geworfenes Rollback: **UNKNOWN**. Cleanupfehler:
**FAILED_OR_UNCONFIRMED**, auch nach physisch bestätigtem Commit. Kein Erfolgsresultat,
kein optimistischer UI-Abschluss, kein Retry. Revoke vor Commit verhindert den Write;
Revoke nach Commit verhindert die Publication, macht den physischen Commit nicht
rückgängig. Eine Mutationsepoche, ein injizierter Port, keine zweite Sessionautorität.

### 49.3 Formular und ausdrückliches Neuladen

**IMPLEMENTIERT:** `OperationsModel.plan()` friert beim Öffnen den vollständigen Plan
mit Action/Person ein und bindet den Editor an die vorhandene READY-Generation und
Requestlebensdauer. `savePlan()` übergibt genau diesen Expected-Wert und den separat
bearbeiteten Entwurf; kein DBread im Save. STALE lässt Rollenmenge, Hauptfunktion und
Formular offen; kein Erfolgsbanner, kein automatisches Überschreiben oder Wiederholen.
Die PLAN-Ansicht erklärt ausdrücklich: „Die Rollenplanung wurde inzwischen geändert.
Dein Entwurf bleibt erhalten.“ Bestehende Live-Status-Fehlermeldungen bleiben getrennt.

Vor dem Button „Entwurf verwerfen und aktuellen Plan laden“ steht, dass das Neuladen
den bisherigen Entwurf ersetzt. Dieser ausdrücklich bestätigte Klick allein verwendet
den bestehenden autorisierten `board`-Lesepfad; es gibt keinen neuen Ersatzvollread
oder automatischen Refresh bei STALE. Erst ein gültiges vollständiges Ergebnis der
richtigen Aktion/Person in PREPARATION ersetzt Expected und Entwurf. Die aktuellen
Rollenidentitäten/-labels werden für diesen Editor übernommen, damit inzwischen neu
angelegte Funktionen nach dem bewussten Reload sichtbar sind. Kein zweites vollständiges
OperationalBoard wird im Editor gehalten; übrige Reloadpayload wird nicht übernommen.
Bei Abbruch/Abschluss/Lock über bestehendes `clear()` werden Editorreferenzen entfernt.
Das ist keine neue Freigabe für RAM-Retention oder einen Snapshotowner.

Fehlgeschlagenes Neuladen ersetzt den Entwurf nicht. READY-, Generation-, Action-,
Planungsberechtigungs-, Request-, Person- und Expected-Referenzprüfungen sperren alte
Callbacks; Reloadcallback einmalig konsumiert. Lock/Navigation nutzen weiterhin die
bestehenden Clear-/Requestgrenzen. Keine Klartextpersistenz des Formulars und keine
allgemeine Änderung der Produktnavigation.

### 49.4 GETESTET und Offline-Validierung

Echtes SQLite mit Schema 11, FKs/Indizes, tatsächlichen DAO-SQLwrites, produktiver
Storelogik und gemeinsamer Transaktions-/Dispatchgrenze:

- Gleicher Expected bei A/B: A COMMITTED/COMPLETE, B STALE mit bestätigtem Rollback;
  vollständige Rollenmenge und Hauptfunktion von A bleiben erhalten. Frischer Editor
  darf anschließend ändern. Mengenunterschiede und reine Primary-Unterschiede erkannt.
- Leerer/fehlender/gespeicherter leerer gegenüber belegtem Plan; fremde Expected-Action/
  Person, fremde Zielperson und gelöschte/fehlende/aktionsfremde Rollen. SQL-Prepare-
  Beobachtung belegt **null DELETE/INSERT** vor diesen Rejections. Keine Fremdänderung.
- Fehlertrigger bei erneutem Insert der Planzeile **nach** Löschen alter Zuordnungen:
  SQLite-Rollback stellt die vollständige ursprüngliche Planung wieder her; kein
  Callback, UNKNOWN statt fälschlichem Erfolg aus einem Repositorywert.
- Plan-spezifisch Commit, Rollback, UNKNOWN vor/nach physischem Commit, fehlgeschlagenes
  Rollback, Cleanupfehler, Revoke nach Queue/vor/nach Commit, Permission-/Generation-/
  Actionwechsel und konkurrierende Queueanforderungen. Doppelte Worker/Maincallbacks
  haben nur eine Wirkung/Publication; kein RAM-Reuse.
- Mutierbare Quellmengen verändern Expected nicht. Formular-Porttests prüfen unveränderten
  Expected ohne Save-Read, Entwurferhalt bei STALE/Reloadfehler, ausschließlich bewussten
  Reload, keine Erfolgsfeedbacks, doppelte/veraltete Reloadcallbacks und Lock/Generation/
  Action/Planungsberechtigungsverlust. Personen-CAS-/Task-/Operations-/Security- und
  Publicationregressionen bleiben erhalten.

Native Room-/Key-/Bindingrennen werden im Host weiterhin vertrauenswürdig simuliert;
kein Androidlaufzeit- oder Native-Cleanupbeweis. Androidtest
`OperationsStorageTest.expectedPlanRejectsSecondOldEditorThroughProductBoundary`
zusätzlich vorbereitet; vorhandene Storageproofs und mutable-Queue-Fixture auf Expected
angepasst. **Nur offline kompiliert, nicht installiert/instrumentiert.**

Gezielte Reproduktion:
`./gradlew :data:testDebugUnitTest --tests '*CoreWriterIntegrationTest' :app:testDebugUnitTest --tests '*OperationsModelTest' --offline`.
Vollständiger Abschlusslauf:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`.
**634 App + 772 Data + 4 Security = 1410 Hosttests, 0 Fehler, 0 Skips.**
Domain-Testtask ohne Testquellen, keine zusätzlichen Tests gezählt. Offline-Appbuild,
beide Androidtest-Kompilationen, Architekturcheck und Produkt-/Demo-Manifestprüfungen
erfolgreich. Bestehende Kotlin-/Android-Deprecationwarnungen bleiben sichtbar; keine
fehlgeschlagenen Tests unterdrückt. Keine Geräteinstallation oder connectedAndroidTest.

### 49.5 ABA und weiterhin offene Pfade

**GETESTETER NEGATIVFALL / BLOCKER:** A → B → A wird ohne monotone persistierte Revision
nicht erkannt. Derselbe alte Expected-A kann danach wieder erfolgreich schreiben.
Der Negativtest `rolePlanExpectedIsImmutableAndSchema11CannotDetectAba` bleibt erhalten.
Vollvergleich verhindert typische Lost Updates, behauptet keine vollständige ABA-
Sicherheit. Generation, Requestzähler, Zeitstempel und flüchtige Mutationsepoche sind
keine persistierte Planrevision. Eine monotone Revision samt allen Writerhooks und
Migration benötigt später eine gesonderte Prüfung/Freigabe; keine Schema-12-Migration.

**Inventar NOT FULLY CAS SAFE, unverändert:**

- Rollenrename: `OperationsModel.renameRole` → `ProductOperationsRepository.renameRole`
  → `OperationsStore.role` liest die aktuelle Rolle, hat aber keinen Expected-Rollenwert.
  Zwei veraltete Namenseditoren sind damit nicht allgemein gegen Lost Updates geschützt.
- Rollenlöschung: `OperationsModel.deleteRole` → Repository → `deleteRole` prüft aktuelle
  Existenz und Referenzen/IN_USE in der Transaktion, hat aber keinen Expected-Rollenwert.
  Die Bestätigung benennt keinen revisionsgebundenen Zustand. Keine allgemeine CAS-/ABA-
  Garantie für veraltete Löschformulare; bestehende FK-/Referenzsicherheit bleibt erhalten.
- Live-Snapshot-/Action-/Taskeditoren behalten ihre bestehenden spezifischen Expected-
  Vergleiche; keine universelle monotone Revision oder vollständige Editor-CAS-Freigabe.
  Personen-Vollzustands-CAS aus §48 unverändert, einschließlich eigener ABA-Grenze.

[#35](https://github.com/JulianGKK/action-planner/issues/35): Writer-/Invalidierungsvertrag
beibehalten; unverdrahtete Familien aus §46/47 bleiben **UNWIRED / NOT SAFE FOR RAM REUSE**.
[#36](https://github.com/JulianGKK/action-planner/issues/36): P3a-Plan-Vollvergleich und
gezieltes Formular **IMPLEMENTIERT / GETESTET**, monotone Revision und übrige Editorpfade
offen. [#37](https://github.com/JulianGKK/action-planner/issues/37): Snapshotowner
**NOCH NICHT ANGEBUNDEN**. [#38](https://github.com/JulianGKK/action-planner/issues/38):
keine RAM-Hits, Cacheaktivierung, Navigationseinbindung oder Performancefreigabe.
[#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/Cleanup-**BLOCKER**
weiter offen; `SQLCipherStatement.close()` schließt den Cursor nicht explizit.
Hostabschluss/FD-PASS beweisen keine vollständige native Cursorfreigabe.

Unverändert: SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink,
B2Driver, Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Sicherheitsbudget.
Keine neue Sessionautorität, Snapshotowner, Berechtigung oder Dependency. Keine
Geräteinstallation/Instrumentation, keine Änderung installierter Apps, kein Produktupdate.

Geändert: Operations.kt (Domain/API), ProductOperationsRepository.kt, OperationsDatabase.kt
(nur DAO-SELECTs), OperationsModel.kt/OperationsScreen.kt, CoreWriterIntegrationTest.kt,
HostCoreWriterData.kt, OperationsModelTest.kt, OperationsStorageTest.kt,
CoreWriterBoundaryStorageTest.kt, notwendige OperationsRepository-Testdoubles/Fixtures
und dieser Abschnitt. Keine fachliche Änderung anderer Writerfamilien.
`git diff --check`, `git status -sb`, regulärer WIP-Commit/Push und unabhängiger Remote-
SHA-Abgleich gehören zum Abschluss. Keine APKs/Schlüssel/Datenbanken/Rohlogs/Credentials/
persönlichen Pfade/Gerätekennungen einchecken. Danach **STOPP bis zum unabhängigen
GitHub-Review**. Kein Force-Push, Merge oder Release; keine vollständige Release-1.0-Freigabe.

## 50. Issue #36 – Expected-Role-CAS für Rename und Delete

### 50.1 Reproduzierter Ausgangszustand

Vor Arbeitsbeginn stimmten lokaler HEAD und unabhängig über GitHub gelesener Remote-HEAD
mit `0815804f514555c741786e514cc292c8fa1a3f22` überein; Arbeitsbaum sauber.
Zwei gezielte SQLite-/Schema-11-Ausgangstests wurden **vor der Implementierung** erfolgreich
ausgeführt und bestätigten beide Fehler des damaligen ID-only-Vertrags:

- `roleRenameBaselineSilentlyAcceptsBothEditors`: A benennt um, B überschreibt danach
  aus dem alten Editor ohne Konflikt. Beide Writes akzeptiert, Bs Name persistiert.
- `roleDeleteBaselineDeletesRenamedRoleFromOldDialog`: Ein alter Dialog löscht die
  seit seiner Öffnung umbenannte Rolle erfolgreich.

Die Nachfolgetests `roleRenameSecondOldEditorIsStale` und
`roleDeleteOldConfirmationCannotDeleteRenamedRole` verlangen jetzt STALE mit bestätigtem
Rollback und dem erhaltenen neueren Namen. Die Ausgangsfehler werden nicht als positive
CAS-Proofs ausgegeben.

### 50.2 IMPLEMENTIERT – vollständige Expected-Role-Identität

`OperationsRepository.renameRole(actionId, expected: Role, name, publish)` und
`deleteRole(actionId, expected: Role, publish)` verlangen den vollständigen zuvor
gewählten unveränderlichen Role-Wert. Kein öffentlicher ID-only-Overload bleibt erhalten.
Auch `OperationsStore.role` erhält für Rename die Role statt nur einer ID, und
`OperationsStore.deleteRole` verlangt sie verbindlich. Null im gemeinsamen Create-/
Rename-Store bedeutet weiterhin ausschließlich Create; CreateRole-Semantik bleibt
unverändert und kann keine bestehende Rolle per ID überschreiben.

In der **bestehenden äußeren IMMEDIATE-Transaktion** werden aktuelle READY-Autorität und
PREPARATION-Schreibregel geprüft. Danach wird die persistierte Rollenidentität mit
Expected verglichen: **Role-ID, Action-ID, exakter ursprünglicher Name und createdAt**.
Case-/Whitespace-Abweichungen des Expected-Namens zählen als Abweichung, selbst wenn
sie denselben normalisierten Namen ergeben. Abweichende existierende Identität oder
Expected-Action: **STALE vor UPDATE/DELETE**. Tatsächlich fehlende Rolle im zulässigen
Scope: **NOT_FOUND**. Fehlende Aktion bleibt NOT_FOUND, unzulässiger Aktionsstatus
NOT_ALLOWED. Ein ungültiger neuer Rename-Name wird weiterhin INVALID.

Die normalisierten neuen Namen, Duplicate-Prüfung, Foreign Keys und Action-Isolation
bleiben erhalten. Nach passendem Delete-Expected werden weiterhin geplante, aktuelle
Operations- und Taskreferenzen geprüft: **IN_USE**, ohne Löschung. STALE wird nicht durch
eine automatische Umbenennung, Löschung oder einen frisch nachgeladenen Expected
umgangen. Inkonsistente persistierte Rollennormalisierung bleibt Storagefehler.
Keine Schema-/Index-/Migrationsänderung.

### 50.3 Tatsächliche Transaktion, Rollback, UNKNOWN und Cleanup

`WorkspaceWriteBoundary`, `WorkspaceMutationInvalidation` und
`confirmedWorkspaceTransaction` bleiben unverändert. Derselbe injizierte Writerport
invalidiert synchron vor Queueeintritt, dieselbe Mutationsepoche sperrt Pending gegen
weitere Writes/RAM-Reuse. Kein zweiter Owner und keine zweite Invalidierungsinstanz.

Ein innerhalb der Transaktion ermitteltes STALE/IN_USE/NOT_FOUND/DUPLICATE/INVALID wird
nur nach tatsächlich zurückgekehrtem äußerem Rollback und DB-/Lease-Cleanup als
**ROLLED_BACK / COMPLETE** publiziert. Die vorhandene separate Vor-Queue-Busyantwort
ist weiterhin kein DB-Konflikt- oder Rollbacknachweis.
**COMMITTED** stammt vom bestätigten äußeren Transaktionsabschluss, nie allein aus
einem Repositorywert oder SAVEPOINT. Fehlender Abschluss/geworfenes Rollback bleibt
**UNKNOWN**; Cleanupfehler **FAILED_OR_UNCONFIRMED**, auch nach physischem Commit.
In beiden Fällen keine Erfolgspublication und kein automatischer Retry.

Revoke vor Commit verhindert den Write; Revoke nach Commit kann die UI-Publication
verhindern, ohne den persistierten Write zurückzunehmen. Die bestehende zentrale
Publication samt Action-/Generation-/Permissionprüfung bleibt maßgeblich. Native
Room-/Key-/Bindingrennen werden in Hosttests weiterhin vertrauenswürdig simuliert;
keine neue Behauptung vollständiger nativer Ressourcenfreigabe.

### 50.4 IMPLEMENTIERT – beide produktiven Rollenoberflächen

Es gibt **zwei** tatsächliche Aufrufstellen: `OperationsScreen.RoleManagement` und
`PreparationWorkspace.PreparationRoles`. Beide nutzen jetzt denselben modellgebundenen
`RoleEditor`. Beim Öffnen werden Role-Snapshot und READY-Generation festgehalten;
Rename-Entwurf und Delete-Bestätigung sind getrennte explizite Modellzustände.

- Rename übergibt genau den geöffneten Expected-Wert. Der Name wird **nicht mehr beim
  Speicherklick** im Compose-Code geleert. Pending hält Editor und Namen fest. Erst
  gültige erfolgreiche Repositorypublication aus der bewiesenen Writergrenze darf den
  gebundenen Editor abschließen, danach erfolgt der bestehende Workspace-Refresh.
- STALE hält Editor und eingegebenen Namen offen und erklärt, dass die Rolle inzwischen
  geändert wurde. Kein Erfolgsbanner, automatisches Überschreiben oder Retry.
- Die Löschbestätigung zeigt den **ursprünglich gewählten Namen** und verwendet dessen
  vollständigen Role-Snapshot, auch wenn die Liste inzwischen neue Daten enthält.
  Bei STALE oder IN_USE bleibt sie erhalten; IN_USE erklärt die bestehenden Referenzen.
  Sie wird nicht schon beim Bestätigungsklick als erfolgreich erledigt entfernt.
- „Alten Stand verwerfen und Rolle neu laden“ wird nur bei STALE angeboten. Der Text
  erklärt **vor** dem Klick, dass dadurch Entwurf bzw. alte Löschbestätigung ersetzt
  werden. Nur diese ausdrückliche Aktion nutzt den vorhandenen autorisierten Boardread.
  Gültiger vollständiger Rückgabestand der richtigen Rolle/Aktion in PREPARATION ersetzt
  den Editorstand; keine Übernahme des gesamten Reloadboards und keine neue Klartextdatei.
  Fehler/Missing/Foreign-Scope verwerfen den Entwurf nicht. Eine frisch geladene
  Löschbestätigung benötigt einen **erneuten Bestätigungsklick**; Reload löscht niemals.
- „Bearbeitung verwerfen“ bzw. „Löschung abbrechen“ ermöglicht bewusstes Verlassen.
  Bestehendes `ProductViewModel`-Clear bei Lock/BLOCKED, Generation- und Actionwechsel
  verwirft über `OperationsModel.clear()` jetzt auch diese Referenzen. READY-/Generation-/
  Action-/Request- und Editorzugehörigkeitsprüfungen sperren alte Callbacks. Einmaliger
  Callbackverbrauch schützt zusätzlich vor doppelten Mutation-/Reloadcallbacks und
  davor, einen später geöffneten Editor zu überschreiben oder abzuschließen.

Der Editorzähler ist nur lokale Callbackzugehörigkeit, keine neue Autorität oder
persistierte Rollenrevision. Keine allgemeine Navigationsänderung, RAM-Hits oder neue
Retentionsfreigabe. Die bestehende CreateRole-Fachsemantik bleibt erhalten.

### 50.5 GETESTET – SQLite-, Formular- und Offline-Proofs

Echtes SQLite/Schema 11 mit FKs/Indizes, produktiver Storelogik und tatsächlicher
gemeinsamer Transaktions-/Dispatchgrenze:

- A/B mit gleichem Expected: A erfolgreich, B STALE, As Name bleibt erhalten.
  Neuer Editor nach Commit darf umbenennen; aktueller unreferenzierter Expected löscht.
  Alter Delete-Dialog nach Rename: STALE, Rolle bleibt erhalten.
- Exakte Case-/Whitespace-/createdAt-/ID-/Actionabweichungen, fehlende Rolle, Duplicate-
  Namen und ungültiger Name. SQL-Prepare-Beobachtung: **null Rollen-UPDATE/DELETE** bei
  diesen Rejections. Foreign-Actiondaten unverändert; Rollenidentität bleibt intakt.
- IN_USE für geplante Rollen, aktuellen Operationssnapshot und echte Taskreferenz;
  jeweils bestätigter Rollback ohne Löschung. Bestehende FK-/Taskproofs erhalten.
- Für Rename **und** Delete: Commit, STALE-Rollback, UNKNOWN vor/nach physischem Commit,
  Rollbackfehler, DB-/Key-Cleanupfehler, Revoke initial/nach Queue/vor/nach Commit/vor
  Publication, Permission-/Generation-/Actionwechsel und doppelte Worker/Mainrunnables.
  Physischer Zustand und Callbackfreigabe werden separat geprüft. Bestehende
  Cross-Family-Pending-/Epochen-, Personen-/Plan-/Task-/Security-/Publicationproofs bleiben.
- Sechs neue `RoleEditorModelTest`-Methoden prüfen eingefrorenen Expected ohne Save-Read,
  Rename-Entwurf bis bestätigter Portantwort, STALE-Erhalt, Delete-Pending/STALE/IN_USE,
  expliziten Reload ohne Delete, Reloadfehler, doppelte/alte Callbackantworten gegen
  neue Entwürfe, Lifecycle-Clear sowie ungelöste Writes ohne optimistischen Abschluss
  oder automatische Wiederholung. Das sind UI-Porttests, keine nativen Commitbeweise.
- `OperationsStorageTest.roleExpectedSnapshotRejectsOldRenameAndDeleteThroughProductBoundary`
  zusätzlich als Android-/SQLCipher-/Room-Proof vorbereitet. Bestehende Androidproofs
  und Testdoubles ausschließlich auf die notwendige Expected-Signatur angepasst.
  **Nur offline kompiliert, nicht installiert oder instrumentiert.**

Gezielt:
`./gradlew :data:testDebugUnitTest --tests '*CoreWriterIntegrationTest' :app:testDebugUnitTest --tests '*RoleEditorModelTest' --tests '*OperationsModelTest' --offline`.
Vollständiger Abschlusslauf:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`.
**640 App + 778 Data + 4 Security = 1422 Hosttests, 0 Fehler, 0 Skips.**
Domain-Testtask ohne Testquellen, keine zusätzlichen Tests gezählt. Offline-Appbuild,
beide Androidtest-Kompilationen, Architekturcheck und Produkt-/Demo-Manifestchecks
erfolgreich. Bestehende Kotlin-/Android-Deprecationwarnungen bleiben sichtbar; kein
Testfehler unterdrückt. Keine Geräteinstallation oder connectedAndroidTest.

### 50.6 ABA und offene BLOCKER

**GETESTETER ABA-NEGATIVFALL:** `roleFullValueCasStillCannotDetectSchema11Aba` stellt
A → B → A einschließlich unveränderter Role-ID/Action-ID/createdAt her. Ein alter
Rename-Editor wird danach wieder akzeptiert; ebenso kann eine alte Löschbestätigung
nach Wiederherstellung desselben vollständigen Role-Wertes wieder löschen.
Schema 11 enthält keine persistierte monotone Role-Revision. Vollzustandsvergleich
verhindert gewöhnliche Lost Updates/veraltete Löschbestätigungen, **keine vollständige
ABA-Sicherheit**. Zeitstempel oder flüchtige Editor-/Mutationsepochen ersetzen keinen
persistierten Revisionsbeleg. Eine spätere monotone Revision mit Migration und allen
Writerhooks benötigt eine eigene Architekturentscheidung/Freigabe; kein Schema 12.

[#36](https://github.com/JulianGKK/action-planner/issues/36): Personen-, Plan- und nun
Role-Vollvergleich **IMPLEMENTIERT / GETESTET** im jeweiligen begrenzten Vertrag.
Monotone Revision/ABA und übrige allgemeine Task-/Action-/Live-Editor-/Revisionslücken
bleiben offen; keine universelle CAS-/Editorfreigabe. Die historischen offenen
Rename-/Delete-Punkte aus §49 sind mit diesem Slice nur für gewöhnliche Vollwertkonflikte
und diese konkreten Formulare geschlossen, nicht für ABA oder alle Writerfamilien.
[#35](https://github.com/JulianGKK/action-planner/issues/35): gemeinsame Writergrenze
erhalten; übrige Familien aus §46/47 weiterhin **UNWIRED / NOT SAFE FOR RAM REUSE**.
[#37](https://github.com/JulianGKK/action-planner/issues/37): Snapshotowner **NOCH NICHT
ANGEBUNDEN**. [#38](https://github.com/JulianGKK/action-planner/issues/38): keine
Cache-/Navigations-/Performanceintegration und keine Release-1.0-Performancefreigabe.
[#32](https://github.com/JulianGKK/action-planner/issues/32): eigenständiger Ressourcen-
**BLOCKER** bleibt offen. `SQLCipherStatement.close()` schließt den Cursor nicht explizit;
Hostcleanup oder FD-PASS beweisen keine vollständige native Cursorfreigabe.

Unverändert: SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink,
B2Driver, Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Sicherheitsbudget.
Keine zweite Sessionautorität/Invalidierungsinstanz, kein Snapshotowner, RAM-Hit,
Cacheaktivierung, installierte App, Geräteinstallation/Instrumentation oder Produktupdate.

Geändert: Operations.kt (API), ProductOperationsRepository.kt, OperationsModel.kt,
OperationsScreen.kt, PreparationWorkspace.kt; CoreWriterIntegrationTest.kt,
RoleEditorModelTest.kt, OperationsStorageTest.kt und notwendige Test-/Fixture-Signaturen
(einschließlich ausschließlich API-Anpassung im TaskStorageTest) sowie dieser Abschnitt.
Keine fachliche Änderung der Personen-/Plan-/Task-/Actionwriter oder gemeinsamer
Security-/Publicationgrenzen. Keine neuen Dependencies/Permissions.

Abschluss: `git diff --check`, `git status -sb`, regulärer WIP-Commit/Push und unabhängiger
Remote-SHA-Abgleich. Keine APKs/Schlüssel/Datenbanken/Rohlogs/Credentials/persönlichen
Pfade/Gerätekennungen einchecken. Danach **STOPP bis zum unabhängigen GitHub-Review**.
Kein Force-Push, Merge, Release oder vollständige Release-1.0-Freigabe.

## 51. Issue #36 – Editor-Lifecycle-Audit und Task-CAS-Bindung

### 51.1 Ausgangsbefund und geprüfter Datenfluss

Vor Änderungen: Arbeitsbaum sauber; lokaler HEAD und unabhängig über GitHub gelesener
Remote-HEAD identisch mit `ce1e07304419c21b0cbf4b5e838344bdaaa3a19c`.

**REPRODUZIERT:** Der zunächst gegen den unveränderten Code ausgeführte deterministische
Port-Hosttest `workspaceRefreshBaselineRebindsExpectedWhileKeepingOldDraft` öffnet Task A,
bearbeitet dessen Entwurf, liefert Task B über `refresh()` und beobachtet **selected B
bei unverändertem Entwurf A**. `save()` verwendete `selected` als Expected. Der damalige
Testdouble nahm diesen Write an. Das ist ein UI-/Port-Ausgangsnachweis, kein SQLite-
Commitbeweis. Der direkte Codepfad erklärt den CAS-Bypass: `old = selected`, obwohl der
Entwurf auf A basiert. DB-seitiger Vollvergleich gegen das bereits aktuelle B kann
so passen und den alten Entwurf akzeptieren.

Das betrifft beide produktiven Refreshwege:

1. `TaskModel.refresh()` nimmt einen vollständigen Repository-Boardread entgegen und
   ruft `reconcileSelection()` auf.
2. `ProductViewModel.readWorkspace()`/`loadWorkspace()` publiziert erst nach vollständigem,
   aktuellem Read einen `WorkspaceSnapshot` (Aktion/Generation, Personen, Operations,
   Tasks und weitere angeforderte Bereiche) und ruft ebenfalls `tasks.reconcileSelection()`.
   `TaskModel.board` verwendet dabei den gelieferten Workspace statt `ownBoard`.

`reconcileSelection()` aktualisiert den gewählten Task anhand der ID. Vor dem Fix war
dieses Anzeigeobjekt zugleich der einzige Edit-Expected. Weder `WorkspaceSnapshot` noch
die vollständige Readerpublication war damit eine Editor-CAS-Bindung. Kein Umbau dieses
Workspace-/Navigationspfads in diesem Slice.

### 51.2 IMPLEMENTIERT – minimaler Task-Lifecycle-Fix

`TaskModel` hält jetzt **einen separaten privaten `editorExpected`** und die beim Öffnen
bestehende Action-/READY-Generation. `edit()` bindet ihn an den unveränderlichen Task A
und initialisiert den Entwurf. `selected` darf weiterhin als Anzeigesnapshot auf B
wechseln oder nach Löschung fehlen. Refresh, Reconciliation und Hintergrundantworten
ersetzen aber weder Expected noch Entwurf. Auch bei fehlender aktueller Auswahl bleibt
der offene Editor ein Edit, niemals versehentlich ein Create.

`save()` verlangt einen gültigen gebundenen Formularzustand und reicht **A plus separaten
TaskInput-Entwurf** an den unveränderten `ProductTaskRepository.edit` weiter. Ein Task B
führt dort über `TaskStore.existing` zum bestehenden vollständigen SQL-CAS: STALE,
kein UPDATE, kein Zusatzereignis. Der zugehörige Nachfolgetest heißt
`workspaceRefreshKeepsOriginalEditorExpectedAndNewerPersistedTask`. Schichtenbeweis:
Porttest prüft tatsächlich übergebenes Expected A/erhaltenen Entwurf; unabhängiger echter
SQLite-Test prüft die DB-Verweigerung gegen B und den erhaltenen Datensatz.
Keine neue Taskrevision, SQL-Abfrage, Sicherheitsautorität oder Writerregel.

STALE hält Formular und Entwurf offen, ohne Erfolgsmeldung/Retry. PREPARATION- und
ACTIVE-Ansicht erklären den Konflikt. Nur „Entwurf verwerfen und Aufgabe neu laden“
verwendet nach dem sichtbaren Hinweis auf Verlust von Fälligkeit/Zuordnungen den
**bestehenden** autorisierten vollständigen Task-Boardread. Kein neuer unbegrenzter
Fallback, keine stille Teilliste. Dieser Legacy-Read ist weiterhin kein kompakter
Detailreader oder neues Speicher-/Performanceversprechen. Erst gültiges vollständiges
Resultat derselben Aktion/Task-ID ersetzt Expected, Auswahl und **alle** Inputfelder.
Readfehler, fehlender/fremder Task ersetzen nichts. Kein automatischer Reload nach STALE.

Eine lokale `formRevision` bindet die Formularinstanz, nicht Datenbank oder Session.
`TaskForm` verwendet `key(formRevision)` für **den gesamten Feldbaum**. Nur neue bewusste
Formularbindung, Verlassen/Clear oder erfolgreicher expliziter Reload ändern diese
Identität. Normale Board-/Workspace-Refreshes ändern sie nicht. Dadurch werden beim
Reload Titel, Priorität, Zuständigkeit, betroffene Person, Fälligkeit und die lokalen
`remember`-Zustände für Datum/aufgeklappte Auswahl zusammen ersetzt. Ein unverändert
alter Compose-Datumsstring kann nicht absichtlich über denselben Feldbaum mit dem neuen
TaskInput kombiniert werden. Autofokus richtet sich nach Create/Edit-Bindung, nicht
nach einer eventuell durch Refresh verschwundenen `selected`-Anzeige.

Writecallbacks sind an Formularidentität, Action und READY-Generation gebunden und
werden einmalig verarbeitet. Eine reine Read-Requestnummer darf keinen ansonsten
aktuellen Commitcallback während eines Hintergrundrefreshes verlieren. Ein neuer
Editor/Cancel/Navigation/Clear verwirft hingegen die alte Identität. Reloadcallbacks
werden ebenfalls einmalig und nur für ihren noch gültigen Editor übernommen.
Bestehendes `ProductViewModel.clearPersons()` ruft `tasks.clear()` bei Lock/BLOCKED,
Generation- und Actionwechsel auf. Keine allgemeine Navigationsänderung.

Nur ein expliziter Expected-Taskverweis zusätzlich zum separaten Entwurf; keine Registry,
Seiten-/Historienkopie oder neue Freigabe für Produkt-RAM-Retention. Das ersetzt keine
spätere gemeinsame Owner-/Budgetprüfung in #37. 8+8 MiB bleiben unverändert.

### 51.3 Vollständige Editor-Matrix – Ausgangssnapshot und Entwurf

Die Einstufungen beziehen sich **nur auf den genannten getesteten Vertrag**, nicht auf
vollständige ABA-Sicherheit, globale Writerabdeckung oder Releasefreigabe. `Clear` meint
die bestehenden Produkt-Lifecyclehooks; Repositorypublication verlangt weiterhin gültige
Storage-Autorität und bestätigten Outcome/Cleanup. Navigation/Clear darf Entwürfe bewusst
verwerfen, sie darf sie nicht heimlich mit einem frischen Expected weiterverwenden.

| Produktpfad | Ausgangssnapshot / Zeitpunkt der Bindung | Entwurf | Refresh / Navigation / Expected |
|---|---|---|---|
| Action erstellen | Kein vorhandener Expected; `creating` | Lokale ActionForm-Strings, Start-/Enddatum | Formfelder per `remember(existing?.id)`; Navigation beendet die Ansicht. Createcallback hat selbst keine Request-/Editorfence. |
| Action bearbeiten | `editing = selected` bei `ProductViewModel.edit()`; vollständige Action | Lokale ActionForm-Felder | Workspace ersetzt `editing` nicht; bewusstes erneutes `edit()` kann einen neuen Wert gleicher ID einsetzen, während `remember(id)` alte Felder behält. Allgemeine Callbackzugehörigkeit offen. |
| Actionstatus bestätigen | `confirming = selected` beim Öffnen; SQL vergleicht **Status**, nicht komplette Action | Nächster Status, kein Textentwurf | Refresh ersetzt `confirming` nicht. `changed()` entfernt Bestätigung auch bei Rejection. Aktuelle Statusfolge monoton; Metadatenänderungen bei gleichem Status sind kein Konflikt dieses engeren Vertrags. |
| Person erstellen | Kein vorhandener Expected | Lokaler Pseudonymstring | Workspace-Read ersetzt keinen Editorwert; `loadPersons()` ist dagegen ein bewusst destruktiver Reset, auch aus anderen erfolgreichen Actioncallbacks erreichbar. |
| Person bearbeiten | Vollständiges `editingPerson` beim Öffnen, §48 | Pseudonym per `remember(id, personEditorRevision)` | Normales `readWorkspace` ändert Expected nicht. Expliziter Reload ändert Snapshot und Revision; Navigation/Clear verwirft. `loadPersons()` bleibt gesonderte Resetgrenze. |
| Rolle erstellen | Kein vorhandener Expected | Zwei Oberflächen: lokaler Rollenname | Allgemeines `RoleManagement` setzt `name=""` unmittelbar nach `createRole(name)`; PreparationRoles wartet mit `submitted`/Effekt auf `!busy && error==null`. Kein einheitlicher Create-Formularvertrag. |
| Rolle umbenennen | Vollständige `editingRole` beim Öffnen, §50 | Modellgebundener `roleName` | Boardrefresh verändert Expected/Draft nicht. Expliziter Reload ersetzt beides; Cancel/Clear entfernt. Callback zusätzlich an Editorzugehörigkeit gebunden. |
| Rolle löschen | Vollständige `deletingRole` beim Öffnen, §50 | Explizite Bestätigung für ursprünglichen Namen | Liste kann neu sein, Bestätigung bleibt alt; Reload ersetzt nur nach Klick und verlangt erneute Deletebestätigung. Clear entfernt. |
| Rollenplanung | `ExpectedPersonRolePlan` beim `plan()`, unveränderliche kopierte Menge, §49 | Rollenmenge und Hauptfunktion getrennt | Refresh ersetzt Expected nicht; expliziter Reload ersetzt ganzen Plan und Rollenoptionen; Clear/Cancel verwirft. |
| Operations-Livestatus / Funktion | `expected: PersonOperationalState` beim `update()`/`changeFunction()` | Status, Rolle, Ort im Modell | Boardrefresh ersetzt Expected nicht. Keine explizite formularerhaltende Reloadfunktion; Abbrechen/Neuöffnen. Gemeinsame Operations-Readrequest kann ausstehenden Writecallback ungültig machen. |
| Task erstellen | Kein vorhandener Expected; gebundene Action/Generation beim `add()` | Modell-TaskInput plus lokaler Datumsstring | Keine heimliche Bindung an neu selektierten Task; neue Formularidentität setzt lokale Felder zurück. Navigation/Clear verwirft. |
| Task bearbeiten | Privater vollständiger `editorExpected` beim `edit()`, jetzt getrennt von `selected` | TaskInput und Datums-/Auswahlzustände unter Formularidentität | Reconcile/Refresh aktualisiert nur Anzeige. Nur expliziter Reload ersetzt Expected, Input und Feldidentität zusammen. |
| Taskstatus ändern | Beim bewussten Quickaction-Klick aktuell ausgewählter Task; falls Formular offen, dessen eingefrorener Expected | Zielstatus, kein eigener Texteditor | Detailauswahl wird bei Refresh aktualisiert. SQL-Vollvergleich nach Klick, aber kein eigener eingefrorener Bestätigungsdialog für das vor dem Klick sichtbare Detail. |
| Task löschen | Beim Klick aktuell ausgewählter Task; bei offenem Formular dessen eingefrorener Expected | Kein eigener Löschdialog im bisherigen Produkt | PREPARATION-only. SQL-Vollvergleich, aber keine dem Role-Delete vergleichbare persistierte UI-Bestätigungsidentität. Navigation/Clear entfernt Auswahl. |

### 51.4 Editor-Matrix – Konflikt, Publication und Bewertung

Für alle integrierten Mutationen gilt: COMMITTED erst nach äußerem Abschluss,
ROLLED_BACK nur nach bestätigtem Rollback, UNKNOWN/Cleanupfehler ohne Erfolgscallback
und ohne automatischen Retry. Product-Lock/BLOCKED/Clear entfernt sensible Formzustände;
das ist kein Beweis, dass jeder ältere UI-Callback innerhalb derselben READY-Generation
zusätzlich durch eine eigene Editoridentität gefenct ist. Genau diese Unterschiede
werden nachfolgend getrennt ausgewiesen.

| Pfad | STALE / Entwurf / ausdrücklich neu laden | Erfolg / Lock / UNKNOWN und gewöhnliches Risiko | Klasse |
|---|---|---|---|
| Action Create | STALE nicht anwendbar; sonst kein allgemeines strukturiertes Create-Rejectionfeedback | Erfolgscallback beendet `creating`; Repo ist outcome-/scopegeschützt, UIcallback selbst nicht gegen spätere Navigation/Editorwahl gefenct. | PARTIALLY SAFE; CAS NOT APPLICABLE |
| Action Edit | Rejection erhält `editing`; alte lokalen Felder bleiben. Cancel und bewusst neu öffnen, kein eigener Reloadrevision-Schritt | SQL-Vollvergleich korrekt; `changed()` ist nicht Request-/Editor-gebunden. `edit()` gleicher ID + remember(id) bleibt präziser Restbefund, nicht hier repariert. | PARTIALLY SAFE |
| Actionstatus | STALE sichtbar, Bestätigung wird jedoch geschlossen; neu öffnen nötig | Monotoner erwarteter Status verhindert Status-ABA im erlaubten Lifecycle. Kein vollständiger Action-Identitätsvergleich/Editorcallbackvertrag. | PARTIALLY SAFE; Status-CAS SAFE FOR TESTED CONTRACT |
| Person Create | Invalid/Duplicate erhält Formular; kein Expected-Reload nötig | Erfolg erst nach Repoantwort, gefenceter Personrequest; destruktive fremde `loadPersons()`-Resets gesondert prüfen. | PARTIALLY SAFE; CAS NOT APPLICABLE |
| Person Edit | §48: STALE erhält Entwurf, expliziter Reload ersetzt revisioniert; fehlgeschlagener Read erhält Entwurf | Gewöhnlicher Lost Update verhindert; eigener Request-/Generation-/Action-/Reloadschutz getestet. Destruktiver übergreifender Reset kein universeller Editorowner. | SAFE FOR TESTED CONTRACT (§48), ABA offen |
| Role Create | Allgemeine Oberfläche verwirft Namen schon am Klick, auch bei Rejection; Preparation wartet auf Ergebnis | Nachweisbarer Entwurfsverlust trotz fehlendem Commit/Publication in allgemeiner Oberfläche. Keine neue Korrektur in diesem Slice. | UNSAFE / OPEN für Entwurferhalt; CAS NOT APPLICABLE |
| Role Rename | §50: STALE erhält Namen; expliziter Reload oder bewusst verwerfen | Vollwert-CAS, Editorserial/Generations-/Actionschutz und Once-Callbacks getestet. Operations-Refresh/Write-Requestüberschneidung bleibt allgemeiner Livenessrestpunkt. | SAFE FOR TESTED CONTRACT (§50), ABA offen |
| Role Delete | §50: STALE/IN_USE erhält Bestätigung; Reload löscht nicht automatisch | Vollwert-CAS und Referenzgates; alte Dialoge löschen geänderte Rollen nicht. | SAFE FOR TESTED CONTRACT (§50), ABA offen |
| Rollenplan | §49: STALE erhält Menge/Primary, nur ausdrücklicher Reload ersetzt sie | Vollwert-CAS und immutable Expected getestet; Operations-Request-Liveness bleibt gesondert offen. | SAFE FOR TESTED CONTRACT (§49), ABA offen |
| Operations live/Funktion | STALE zeigt bisher „Lage geändert, neu öffnen“; Entwurf bleibt, Cancel/Neuöffnen möglich | SQL-Vollvergleich schützt gewöhnliche veraltete Writes. Kein einheitlicher Reload-/Formidentitäts-/Editorcallbackvertrag; Readrefresh kann Callback verlieren/busy belassen. | PARTIALLY SAFE |
| Task Create | Fehler erhält Input/Feldinstanz; kein Existing-Expected-Reload | Neue Formular-/Action-/Generationsbindung und Once-Publication; UNKNOWN ohne optimistischen Abschluss, Clear verwirft. | SAFE FOR TESTED CONTRACT; CAS NOT APPLICABLE |
| Task Edit | Jetzt STALE mit Entwurferhalt, ausdrücklich bestätigter Reload aller Felder, Fehler erhalten Entwurf | A bleibt Expected trotz Anzeige B; SQL verweigert A. Alte Reload-/Writecallbacks verändern keinen neuen Editor. Keine gemessene native Compose-Fokusfreigabe. | SAFE FOR TESTED CONTRACT dieses Slices; ABA offen |
| Taskstatus | STALE ohne automatischen Retry; Detail muss bewusst neu geöffnet werden, kein Textentwurf | Vollwert-CAS und TaskEvent atomar/exactly once getestet. Aktualisierte Detailauswahl vor Klick ist kein eingefrorener Statusdialog. | PARTIALLY SAFE UI; DB SAFE FOR TESTED CONTRACT |
| Taskdelete | STALE ohne Retry; vorhandene Auswahl kann bewusst neu geöffnet werden | SQL-CAS/PREPARATION/FK-Cascade getestet. Keine Role-Delete-artige Bestätigung, mögliche Anzeige-/Klickintentfrage bei Refresh bleibt UI-Arbeit. | PARTIALLY SAFE |

### 51.5 GETESTET – Task-Expected, Events und Grenzen des UI-Nachweises

Hosttests prüfen Refresh A→B bei erhaltenem Entwurf/Expected A, SQL-STALE mit erhaltenem B,
bewussten B-Reload mit neuem Expected und erfolgreichem Folgewriter, alle TaskInputfelder,
Formularrevision, fehlgeschlagenen/missing/foreign Reload, Lock/Generation/Action-Clear,
verspätete/doppelte Callbacks und Readrefresh während ausstehendem Writecallback.
Letzterer darf einen aktuellen gültigen Commit nicht allein wegen geänderter Readnummer
verlieren. Ein neuer Editor darf durch diesen Callback trotzdem nicht geändert werden.

Echte SQLite-/Schema-11-Tests mit unverändertem `TaskStore`/gemeinsamer Writergrenze
prüfen Edit gegen Transition, Transition gegen Edit und zwei alte Transitionen.
**Genau ein Event je erfolgreicher Statusänderung**, kein Zusatzereignis bei Edit,
STALE oder Rollback. Bestehende UNKNOWN-, Event-SQL-Abbruch-, Cleanup-, Revoke-vor/nach-
Commit-, doppelte Worker-/Maincallback- und Pendingproofs laufen unverändert mit.
Kein automatischer Retry. Native Room-/Key-/Bindingrennen bleiben im Host simuliert.

Der erste neue ABA-Test verwendete fälschlich IN_PROGRESS→OPEN und wurde von der
bestehenden Fachregel als INVALID abgelehnt. Korrigiert wurde ausschließlich die
Testsequenz zu OPEN→CANCELLED→OPEN; keine gelockerten Assertions oder Produktregeln.

`TaskStorageTest.frozenEditorExpectedRejectsNewerTaskAndTransitionWithoutExtraEvents`
ist für die tatsächlichen Repositories/Room/SQLCipher zusätzlich vorbereitet und
**nur offline kompiliert**. Kein Gerätetest. Compose-Fälligkeits-/Auswahlzustände werden
durch den expliziten `key(formRevision)`-Feldbaum und Hostprüfungen der unveränderten/
geänderten Formidentität abgesichert. **Kein gerenderter Android-Fokus-/IME-/Datumstest
wurde ausgeführt**; dieser bleibt Integrationstestbedarf, kein behaupteter Runtime-PASS.
Keine neue Testdependency oder Geräteinstrumentation.

Gezielt:
`./gradlew :app:testDebugUnitTest --tests '*TaskModelTest' :data:testDebugUnitTest --tests '*TaskMutationSqliteTest' --tests '*CoreWriterIntegrationTest' --offline`.
Vollständig:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`.
**648 App + 781 Data + 4 Security = 1433 Hosttests, 0 Fehler, 0 Skips.**
Domain-Testtask ohne Testquellen; keine zusätzlichen Tests gezählt. Der Abschlusslauf
mit den zusätzlichen direkten Workspace-/verschwundenen Auswahlproofs ist ebenfalls
erfolgreich. Offline-Appbuild, beide Androidtest-Kompilationen, Architekturcheck und
Produkt-/Demo-Manifestchecks erfolgreich. Compilerwarnungen bleiben sichtbar; keine
Fehler unterdrückt. Keine Geräteinstallation oder connectedAndroidTest.

### 51.6 ABA-Risikomatrix und Migrationsfolgen – keine Implementierung

| Modell/Vertrag | Befund unter Schema 11 | Nachweis / notwendige spätere Entscheidung |
|---|---|---|
| Person | Tatsächlich reproduziertes A→B→A mit gleichem vollständigem Wert | Negativtest §48; persistierte monotone Personenrevision nötig für vollständigen ABA-Schutz. |
| Role | Tatsächlich reproduziertes A→B→A; betrifft Rename und alte Deletebestätigung | Negativtest §50; monotone Rollenrevision einschließlich Rename/Delete/Recreate-Identität separat entscheiden. |
| PersonRolePlan | Tatsächlich reproduziert; leere fehlende/gespeicherte Planung fachlich gleich | §49; Planrevision muss auch leere Pläne und alle Zuweisungswriter abdecken, darf beim Löschen nicht unbemerkt wieder bei altem Wert beginnen. |
| Task | Vollzustand kann trotz Edit und Statuszyklus wieder gleich werden | Neuer echter SQLite-Negativtest mit **injiziertem gleichem Millisekundenwert**; kein Durchsatzbeweis dreier Writes innerhalb einer realen Millisekunde. `updatedAt=now()` ist nicht verbindlich monoton. Events sind erhalten, aber nicht Teil des Task-CAS. Persistierte Revision müsste Edit/Transition/weitere Taskwriter umfassen. |
| Action, vollständiger Edit | Tatsächlich mit SQLite reproduziert: Titel B und zurück A, createdAt unverändert | Action hat keinen bei Edit fortgeschriebenen Revisionswert. Voll-ABA erfordert eigene persistierte Revision aller relevanten Actionwrites. |
| Action, nur Statusadvance | Status-ABA durch monotone erlaubte Fachfolge ausgeschlossen | Bestehender Expected-Status-Vertrag schützt genau diese Folge. Kein Beweis vollständiger Actionidentität oder UIzustimmung zu zwischenzeitlich geänderten Metadaten. Keine pauschale Revision nur für unmöglichen Rückwärtsstatus nötig. |
| PersonOperationalState | Vollwert-ABA mit gleichem injiziertem `updatedAt` in SQLite reproduziert | Neuer Corewriter-Negativtest; Uhrzeit ist keine garantierte Revision. Monotone persistierte Personen-Operationsrevision nötig für vollständigen ABA-Schutz über alle Snapshotwriter. |

Die neuen Negativtests verwenden die vorhandenen Store-Clock-/Binding-Simulationen
innerhalb echter SQL-Transaktionen. Sie beweisen die fehlende vertragliche Revision,
keine neue Autorität. Sichere spezielle Verträge sind weiterhin volle Expected-Gleichheit
gegen gewöhnliche Konflikte, atomare Status/Eventwrites, monotone Actionstatusfolge
und getestete Lifecyclefences – nicht universelle ABA-Sicherheit.

Eine spätere Entscheidung benötigt Schema-/Migrationfreigabe, Initialisierung alter
Datensätze, atomare Fortschreibung sämtlicher beteiligter Writer, Overflow-/Recreate-/
Delete-Semantik sowie Revision in Detail-/Seiten-/Formularverträgen. UI-Zähler, Uhrzeiten
oder flüchtige Mutationsepochen sind kein Ersatz. **Keine Migration implementiert,
Schema 11 unverändert.**

### 51.7 Priorisierte Restpunkte und Sicherheitsgrenzen

1. **UNSAFE / OPEN:** CreateRole-Entwurferhalt in beiden Oberflächen vereinheitlichen;
   besonders unmittelbares `name=""` in allgemeinem RoleManagement beseitigen. Eigener
   Integrationsslice mit bestätigter Publication/Fehler-/UNKNOWN- und Lifecycleproofs.
2. **PARTIALLY SAFE:** Actionform gleicher ID/remember-Felder, Action-Create/Edit/Status-
   Callbackzugehörigkeit und destruktive fremde `loadPersons()`-Resets prüfen. Dazu
   Operations-Live-Reload-/Formidentität und gemeinsame Read-/Write-Request-Liveness.
   Keine stillschweigende umfassende Editorfreigabe aus bestehenden DB-CAS ableiten.
3. Task-Quickaction-/Delete-Intent bei aktualisierter Detailauswahl sowie tatsächliche
   Android-Compose-Fokus-/Datum-/IME- und Fehlerdarstellung nach Integration nachweisen.
4. Persistierte Revisionen/ABA als gesonderte Architektur-/Migrationsentscheidung.

[#35](https://github.com/JulianGKK/action-planner/issues/35): gemeinsame Writergrenze
unverändert; übrige Familien §46/47 **UNWIRED / NOT SAFE FOR RAM REUSE**.
[#36](https://github.com/JulianGKK/action-planner/issues/36): Task-Expected-Minimalfix und
Audit **IMPLEMENTIERT / GETESTET** im genannten Hostvertrag; Resteditoren/ABA offen.
[#37](https://github.com/JulianGKK/action-planner/issues/37): gemeinsamer Snapshotowner
**NOCH NICHT ANGEBUNDEN**, keine RAM-Hits oder Cacheaktivierung.
[#38](https://github.com/JulianGKK/action-planner/issues/38): keine Navigationseinbindung
oder Performancefreigabe; native UI-/Lazy-/Fokusnachweise bleiben Integration.
[#32](https://github.com/JulianGKK/action-planner/issues/32): unabhängiger Cursor-/Cleanup-
**BLOCKER** bleibt: `SQLCipherStatement.close()` schließt Cursor nicht explizit;
Hostcleanup/FD-PASS beweisen keine vollständige native Cursorfreigabe.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink, B2Driver, Schema 11,
180 s absolut/30 s READY idle und 8+8-MiB-Budget unverändert. Keine zweite Autorität oder
Snapshotowner. Keine Geräteinstallation/Instrumentation oder Änderung installierter Apps.
Geändert: TaskModel.kt, TaskScreen.kt, ActiveTasks.kt, TaskModelTest.kt,
TaskMutationSqliteTest.kt, CoreWriterIntegrationTest.kt (zusätzliche ABA-Proofs),
TaskStorageTest.kt (offline vorbereitet) und dieser Abschnitt. Keine pauschalen anderen
Writer-/Editorumbauten, kein Produktupdate. Abschluss mit Diff-/Statusprüfung, regulärem
WIP-Commit/Push und unabhängigem Remote-SHA-Abgleich. Keine APKs, Schlüssel, Datenbanken,
Rohlogs, Credentials, persönlichen Pfade oder Gerätekennungen einchecken.
Danach **STOPP bis zum unabhängigen GitHub-Review**. Kein Force-Push, Merge, Release oder
vollständige Editor-/Release-1.0-Freigabe.

## 52. Issue #36 – CreateRole-/NewFunction-Entwürfe und Publication

### 52.1 Ausgangsnachweise und Umfang

Vor Änderungen: sauberer Arbeitsbaum, lokaler HEAD und unabhängig über GitHub gelesener
Remote-HEAD `8e157f91d22a27d119835bd870ca8f126ccce2a9`.

**REPRODUZIERT, zunächst rote Hostregressionen:**
`CreateRoleModelTest.normalReadRefreshCannotLoseConfirmedCreateCallback` und
`callbackAfterRejectionCannotCloseLaterFormInstance` schlugen gegen den Ausgangscode
fehl. Ein gewöhnliches `OperationsModel.refresh()` erhöhte denselben Requestzähler,
den der Create-Writecallback prüfte: gültiges Ergebnis verloren, busy/Formular blieben
stehen. Nach einer Rejection konnte außerdem ein erneut ausgeführter alter Callback
eine danach geöffnete NewFunction-Instanz schließen. Zwei weitere Ausgangstests waren
bereits grün: normaler DUPLICATE-Callback erhielt den NewFunction-Entwurf, ausbleibender
Callback erhielt Name/Formular ohne Retry. Diese Fälle werden nicht als Ausgangsfehler
behauptet. Die beiden roten Tests bestehen jetzt unverändert in ihrer Zielaussage.

**Eindeutige Quellcodebefunde, keine behauptete gerenderte Host-UI-Ausführung:**
RoleManagement hatte `ops.createRole(name); name=""` direkt im Klickhandler.
LiveForm hatte zusätzlich `adding=false` im Handler. Beide lokalen Zustandsänderungen
waren unabhängig von Ergebnis/Commit/Publication und konnten Namen bereits vor einem
Callback verwerfen. PreparationRoles verwendete `submitted`, `ops.busy` und `ops.error`
in einem LaunchedEffect statt einer zugehörigen Ergebnisidentität. Für diese dritte
Oberfläche wurde **kein nominaler tatsächlicher Produktions-Fehlabschluss behauptet**;
der indirekte Mechanismus wird durch denselben ausdrücklichen Ergebnispfad ersetzt.
Ein zusätzlicher neuer Porttest belegt, dass ein gar nicht eingereichter Auftrag nicht
allein wegen `!busy && error==null` als erfolgreich behandelt wird. Kein Androidrendering
oder Gerätetest wurde ausgeführt, keine Vermutungen als Laufzeitfehler ausgegeben.

### 52.2 IMPLEMENTIERT – ein flüchtiger Create-Formularvertrag

Alle Texteingaben verwenden jetzt `OperationsModel`-Draft und dieselbe
`RoleCreateForm`-Darstellung. `RoleCreateSurface` unterscheidet die bestehenden
Aufrufstellen; eine zusätzlich wechselnde Formularinstanz unterscheidet auch zwei
aufeinanderfolgende Aufrufe derselben Oberfläche. Die Bindung umfasst Action-ID,
READY-Generation, konkrete Formularinstanz, Screen-/Personkontext, Role-Editorzugehörigkeit
und einen separat identifizierten Create-Schreibauftrag.

`createName` ist der bearbeitbare Draft. Beim Submit wird die tatsächlich gesendete
unveränderliche Stringeingabe gesondert im Auftrag festgehalten. Während Pending sind
Eingabe und erneuter Submit gesperrt; kein automatischer zweiter Write. Formular- und
Jobzähler sind ausschließlich lokale Callbackzugehörigkeit, **keine Sessionautorität,
persistierte Revision, Permission oder Snapshotregistry**. Höchstens ein Createauftrag
im Modell pending; keine persistierten Entwürfe oder neue Lebensdauer nach Clear/Reauth.

Nur eine einmalig verbrauchte, zum noch aktuellen Auftrag/Formular passende Antwort
darf Zustand verändern. Read-Requestnummern gehören nicht zu dieser Createbindung.
Ein anderer Role-Editor oder eine neue Createinstanz kann durch den alten Callback
weder geschlossen noch überschrieben werden. Ein durch Rename/Delete verdecktes
Text-Createformular darf nicht über einen alten UI-Aufruf zusätzlich einreichen.

### 52.3 Alle produktiven Einstiege und Abschlusssemantik

| Einstieg | Neuer gemeinsamer Draft-/Abschlusspfad | Nach gültigem eigenen Erfolg |
|---|---|---|
| Allgemeine Rollenansicht PREPARATION | ROLES-Instanz bei Eintritt; gemeinsames modellgebundenes Feld | Eingabe zurücksetzen, Feld bleibt für nächste Rolle verfügbar. |
| Allgemeine Rollenansicht ACTIVE | Derselbe ROLES-Vertrag, bestehende CreateRole-Rechte | Eingabe zurücksetzen; keine automatische Rollen-/Personenzuordnung. |
| PreparationRoles | PREPARATION-Instanz beim ausdrücklichen Öffnen; kein lokales submitted/busy/error-Abschlussraten mehr | Eigenes Formular schließen und Namen entfernen. |
| Inline-Funktion im aktiven Lageformular | INLINE-Instanz beim Öffnen; kein lokales name/adding-Clear beim Speicherklick | Nur Inline-Unterformular schließen. Personenbindung und Live-Status-/Funktionsentwurf bleiben erhalten. |
| NewActiveFunction | NEW_FUNCTION-Instanz; bisherige change-/saveNewFunction-Aufrufe delegieren an denselben Vertrag | Bestehender Rückweg zur Übersicht, Name entfernen; bisheriges Funktionsfeedback erhalten. |
| Vorlagenbuttons | Kein künstlicher Texteingabedraft und kein Eigentum an einem anderen Formular | Workspace aktualisieren/Feedback; fremde Create-, Rename-, Delete- oder Planentwürfe nicht zurücksetzen. |

INVALID, DUPLICATE und NOT_ALLOWED behalten Name und Formular. Der zugehörige Fehler
wird sichtbar, kein neues Erfolgsfeedback und kein automatischer Retry. Createfehler
und Vorlagenfehler sind getrennt; eine Vorlagen-Rejection ersetzt nicht den Fehler
oder Entwurf eines fremden Text-/Renameeditors. Vorlagenfehler bleiben auch in der
allgemeinen Rollenansicht neben einem offenen Renameeditor sichtbar.

Erfolgsfeedback verwendet die **abgeschickte Eingabe**, nicht einen späteren Draft.
Der bestehende Feedbackvertrag bleibt erhalten: `createRole` meldet „Rolle … angelegt“,
die eigene NewFunction-Seite „Funktion … angelegt“. Der erste vollständige Regressionlauf
fand eine versehentlich verallgemeinerte ACTIVE-Formulierung; die Implementierung wurde
zur bestehenden Erwartung von `Ux1bSnapshotTest` zurückgeführt, nicht dessen Assertion
geändert. Keine unnötige fachliche oder textliche Create-Semantikänderung.

### 52.4 Commit-/Publication-Grenze und Read-/Write-Liveness

**Unverändert:** `OperationsRepository.createRole` → `ProductOperationsRepository` →
`WorkspaceWriteBoundary`/`WorkspaceMutationInvalidation` →
`confirmedWorkspaceTransaction`/`OperationsStore.role(..., expected=null, ...)`.
Kein alternativer Writer, kein direkter UI-SQLpfad, kein Existing-Expected für Create.
PREPARATION/ACTIVE-Regeln, normalisierte Namen und Duplicate-Prüfung bleiben bestehen.

Die produktive Writergrenze invalidiert vor Queueeintritt und belegt äußeren Commit/
Rollback sowie DB-/Lease-Cleanup. Ein beliebiger Callback oder Repositorywert allein
ist **kein** Commitnachweis. Das UI konsumiert den bestehenden vertrauenswürdigen
Repositorypublication-Vertrag; es erfindet keine eigene Commitbestätigung. UNKNOWN,
unbestätigter Abschluss und FAILED_OR_UNCONFIRMED-Cleanup liefern dort keinen
Erfolgscallback. Ohne Callback bleiben Formular/Name pending, bis bestehender
Lock/BLOCKED/Action-/Lifecycle-Clear sie verwirft. Kein optimistischer Erfolg/Retry.

Erst nach zulässiger erfolgreicher Publication: zugehörigen Draft zurücksetzen bzw.
eigenes Formular schließen, bestehenden Workspace-Refresh auslösen und zutreffendes
Feedback freigeben. Nach synchronem Scope-/Navigationswechsel während dieses Refreshes
wird kein altes Folgefeedback veröffentlicht. Doppelte Callbackausführung bewirkt
höchstens einen Abschluss/Refresh/Feedback.

Ein normaler eigener Boardread oder gelieferter Workspace-Refresh verändert nur den
Readrequest, **nicht** die Create-Auftragsidentität. Auch ein Readfehler darf nicht
allein das passende bestätigte Writergebnis verschlucken. Die bestehende allgemeine
`OperationsModel.write`-Implementierung für Rename/Delete/Plan/Live wurde dagegen
**nicht** pauschal umgestellt; deren übrige Read-/Write-Livenessfragen aus §51 bleiben
getrennt offen. Kein zweiter Workspaceowner oder neue Sessionautorität.

### 52.5 Lifecycle und Clear

Bestehende Clear-Grenzen entfernen jetzt den gemeinsamen Draft, die Surface-/Auftrags-
zugehörigkeit und Createfehler. Lock/BLOCKED, Generation- und Actionwechsel können
keinen alten Namen nach Reauth wiederherstellen. Ein alter Callback verändert auch
keine später in derselben READY-Generation neu geöffnete Instanz.

Eintritt in andere bestehende Operationsansichten bzw. Abschluss/Cancel verwirft den
zugehörigen Createzustand. Beim Wechsel des Live-Status verlässt die Inline-Erstellung
ihren sichtbaren Kontext; nur deren Kind-Draft wird verworfen, die bisherigen fachlichen
Status-/Rollenregeln bleiben unverändert. Eine spätere Rückkehr stellt den Kindnamen
nicht heimlich wieder her. Busy sperrt doppelte Einreichung, eine gültige Clear-Grenze
kann weiterhin abbrechen. Keine neue Produktseite oder allgemeine Navigationsarchitektur.

### 52.6 GETESTET und Abschlussvalidierung

`CreateRoleModelTest` prüft mit kontrollierten echten Portaufrufen und zurückgehaltenen
Antworten:

- Pending, INVALID, DUPLICATE und NOT_ALLOWED für alle vier Text-Surfaces;
  einheitliche Semantik beider PREPARATION-Oberflächen und ACTIVE-Inline-Erhalt.
- Eigener bestätigter Erfolg, konkrete abgeschickte Eingabe, kein Submit/Erfolg aus
  bloßem busy/error-Zustand; fehlender Callback ohne Abschluss/Retry.
- Neue Formularinstanz nach Rejection, neuer Auftrag auf derselben Instanz,
  erste verspätete Antwort gegen neue Instanz derselben READY-Generation,
  doppelte Antwort nur einmal verarbeitet.
- Lock/BLOCKED/Generation-/Action-Clear; zusätzlich Scopewechsel vor Antwort ohne
  voreilige Successpublication sowie synchroner Revoke vor Queueeintritt.
- Eigener Readrefresh, gelieferter Workspace-Refresh und Readfehler während Pending;
  der gültige Createcallback bleibt ansprechbar und schließt nur seinen Owner.
- Vorlagenantworten ändern keine offenen Text-/Renameentwürfe. Verdecktes Textformular
  reicht nicht über Rename ein. Inline-Statuswechsel verwirft nur den Kind-Draft.
  Abgewiesene Funktionsnavigation und ein fremder Darstellungstoggle verwerfen keinen Draft.

Die bisherigen ActiveFunctions-, Operations-, Snapshot-/Feedback-, Personen-/Plan-/
Role-CAS-, Task-, Security- und Publicationregressionen laufen vollständig mit.
**Keine neuen DB-relevanten Behauptungen:** Daher keine zusätzlichen SQLite-Tests und
keine Änderung am produktiven Createwriter; die vorhandenen echten SQLite-/Schema-11-
Writer-/Outcome-/Cleanup-Proofs bleiben erhalten. Porttests sind kein neuer nativer
Commit-/Cleanup- oder gerenderter Compose-/Fokusnachweis. Bestehende Androidproofs nur
offline kompiliert; keine Geräteinstallation/Instrumentation und keine neue Dependency.

Gezielt:
`./gradlew :app:testDebugUnitTest --tests '*CreateRoleModelTest' --tests '*OperationsModelTest' --tests '*ActiveFunctionsTest' --tests '*RoleEditorModelTest' --offline`.
Vollständig:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`.
**667 App + 781 Data + 4 Security = 1452 Hosttests, 0 Fehler, 0 Skips.**
Domain-Testtask ohne Testquellen; keine zusätzlichen Tests gezählt. Offline-Appbuild,
beide Androidtest-Kompilationen, Architekturcheck und Produkt-/Demo-Manifestchecks
erfolgreich. Abschließender Clear entfernt auch die privaten Action-/Generations-
Bindungsreferenzen; danach gleiche Abschlussprüfungen erneut. Compilerwarnungen bleiben
sichtbar, keine fehlgeschlagenen Tests unterdrückt. Kein connectedAndroidTest.

### 52.7 Status, verbleibende BLOCKER und Grenzen

[#36](https://github.com/JulianGKK/action-planner/issues/36): Create-Formular-/Auftrags-
bindung für alle genannten Oberflächen **IMPLEMENTIERT / GETESTET** im UI-Portvertrag.
Der CreateRole-Entwurfsverlust-Restpunkt aus §51 ist für diese Aufrufstellen geschlossen;
keine pauschale Editorfreigabe. Action-Editor-/Callbacklücken, Operations-Live-Reload/
Formidentität und übrige gemeinsame Operations-Read-/Write-Liveness bleiben offen.
Persistierte ABA-Grenzen von Person/Role/Plan/Task/Action/Operationsstate aus §51
unverändert; Create ist kein Rename-CAS und erzeugt keine persistierte Revision.
Keine Schema-12-Migration.

[#35](https://github.com/JulianGKK/action-planner/issues/35): produktive Writergrenze,
eine Mutationsepoche und Vor-Queue-Invalidierung erhalten; weitere Familien weiterhin
**UNWIRED / NOT SAFE FOR RAM REUSE** gemäß §46/47.
[#37](https://github.com/JulianGKK/action-planner/issues/37): gemeinsamer Snapshotowner
**NOCH NICHT ANGEBUNDEN**, keine RAM-Hits/Cacheaktivierung. Lokale Formularserials sind
kein atomarer Payloadowner oder Ersatz für zentrale Storage-Publication.
[#38](https://github.com/JulianGKK/action-planner/issues/38): keine Navigations-/
Performanceintegration; gerenderte Android-UI-/Fokusnachweise bleiben gesondert nötig.
[#32](https://github.com/JulianGKK/action-planner/issues/32): Cursor-/Cleanup-**BLOCKER**
weiter unabhängig offen. `SQLCipherStatement.close()` schließt den Cursor nicht explizit;
Hostabschluss/FD-PASS beweisen keine vollständige native Cursorfreigabe.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink, B2Driver, Schema 11,
180 s absolut/30 s READY idle und 8+8-MiB-Budget unverändert. Kein neuer Snapshotowner,
keine Sessionautorität, Klartextpersistenz, Permission oder Dependency. Keine fachliche
Änderung anderer Writerfamilien, kein Gerät, keine installierte App, kein Produktupdate.
Geändert: OperationsModel.kt, OperationsScreen.kt, PreparationWorkspace.kt,
ActiveFunctions.kt, neuer CreateRoleModelTest.kt und dieser Abschnitt.
Abschluss: `git diff --check`, `git status -sb`, regulärer WIP-Commit/Push, unabhängiger
Remote-SHA-Abgleich. Keine APKs/Schlüssel/Datenbanken/Rohlogs/Credentials/persönlichen
Pfade/Gerätekennungen einchecken. Danach **STOPP bis zum unabhängigen GitHub-Review**.
Kein Force-Push, Merge, Release oder vollständige Release-1.0-Freigabe.


## 53. Issue #35 – Material-Writerintegration

### Ausgangsbasis und RED-Nachweis

Verbindliche Basis `76315a20bad21488520cd297d48491212ed5f6fc` vor Änderungen lokal
und unabhängig über den GitHub-Ref bestätigt; Branch `p11-performance-optimization`,
Arbeitsbaum sauber. Keine fremden Änderungen übernommen oder überschrieben.

Bisheriger tatsächlicher Pfad: `MaterialRepository` → `ProductMaterialRepository`
→ eigener `write`/`run` → `withWriteTransaction` → `MaterialStore` → Room-DAOs →
SQLCipher. READY-, Schritt-, Commit- und Publicationprüfungen waren vorhanden.
Es fehlten jedoch der gemeinsame Vor-Queue-Mutationsport und der explizite
Abschlussbeleg aus `confirmedWorkspaceTransaction`; ein zurückgegebener Store-Value
war kein separater Beleg für äußeren Commit und Cleanup.

**GETESTET – RED vor Implementierung:** Neuer `MaterialWriterWiringTest` mit zwei
Methoden, beide fehlgeschlagen: gemeinsame Boundary fehlt; Runtime reicht gemeinsamen
Mutationsport nicht an Material weiter. Dies sind ausdrücklich **statische
Verdrahtungsregressionen**, keine auf Android ausgeführten Repositorytests. Nach
Implementierung beide grün. Die dynamischen Transaktions-/Queue-/Outcome-Nachweise
verwenden zusätzlich den vorhandenen echten SQLite-/Schema-11-Hostharness.

### IMPLEMENTIERT – gemeinsame Writer- und Transaktionsgrenze

`WorkspaceWriterFamily.MATERIAL` ergänzt. `ProductRuntime` übergibt exakt dieselbe
`workspaceMutations`-Instanz an Material wie an Action, Person, Operations und Task.
Kein eigener Material-Epochenzähler. Der kompatible Standalone-Constructordefault ist
wie bei Operations/Task `UncachedWorkspaceMutations`; die produktive Composition
injiziert ausdrücklich den gemeinsamen Port. Vorhandene Material-Fixtures mit
abschließendem Clock-Lambda bleiben kompatibel.

Create/Edit/Delete verwenden `WorkspaceWriteBoundary.submit`:

1. Aktuellen READY-Kontext beziehen und Operation erlauben lassen.
2. Synchron `beforeCoreQueue(MATERIAL, actionId, generation)` anfordern, **bevor**
   `worker.execute` aufgerufen wird. Gemeinsames Pending sperrt auch andere Familien.
3. Im Worker Autorisierung und `READY_TO_OPEN` prüfen; bestehenden Key-Lease und
   `ActionDatabaseAccess` samt SQLCipher-/Room-Preflights verwenden.
4. `useWriterConnection` und `confirmedWorkspaceTransaction` öffnen genau eine äußere
   **IMMEDIATE**-Transaktion. Bereits aktive Transaktionen liefern keinen äußeren
   Commitbeweis; kein SAVEPOINT-Erfolg. MaterialStore eröffnet selbst keine Transaktion.
5. Store-Value verlangt Commit, fachliche Rejection verlangt expliziten tatsächlichen
   äußeren Rollback. Erst normal zurückgekehrter Transaktionsabschluss bei nicht mehr
   aktiver Transaktion setzt COMMITTED bzw. ROLLED_BACK.
6. Erst nach Rückkehr des vollständigen DB-/Key-Scopes kann die Boundary COMPLETE
   setzen und den Abschluss auf dem Ownerthread an das Ticket melden. Danach zentrale
   `StorageContext.publish` und `ticket.isCurrent()` vor dem Ergebnis-Callback.

`WorkspaceWriteBoundary` und `confirmedWorkspaceTransaction` funktional unverändert;
nur ihr Familienkommentar wurde erweitert. `MaterialStore` verwendet als minimale
Hosttest-Seam nun `CoreWriterData.materials` sowie die bereits vorhandenen Action-/
Person-/Task-DAOs. Produktion delegiert auf dieselben Room-DAOs. Keine neue
Repositoryarchitektur, Transaktions-, Session- oder Schlüsselautorität.

| Situation | Beleg / Publication |
|---|---|
| Erfolgreicher äußerer Commit und vollständige Rückkehr aller Scopes | COMMITTED / COMPLETE; Callback nur bei weiterhin gültiger zentraler Publication und aktuellem Ticket |
| Fachliche Rejection mit tatsächlich zurückgekehrtem äußerem Rollback | ROLLED_BACK / COMPLETE; Ablehnung darf veröffentlicht werden |
| Unklarer Commit-/Rollbackabschluss, SQL-Exception ohne positiven äußeren Endbeleg | UNKNOWN; keine Erfolgs- oder fachliche Rejectionpublication |
| Physischer Commit bekannt, danach DB-/Lease-Scope nicht bestätigt zurückgekehrt | COMMITTED / FAILED_OR_UNCONFIRMED; kein Erfolg |
| Revoke nach bekanntem physischem Commit | Commitbeleg bleibt COMMITTED; Scopefehler/Publicationverweigerung erzeugt keinen UI-Erfolg und keinen erfundenen Rollback |
| Pending-Ticket bereits belegt | Vor-Queue-STALE-Rejection (`OperationsFailure.STALE`); **keine** ausgeführte DB-Transaktion und **kein** Rollbacknachweis |
| Workerdispatch vor Start fehlgeschlagen | Bestätigte Nichtausführung, vorhandene Boundary meldet ROLLED_BACK / COMPLETE plus Fehlerpfad; kein Erfolg |
| Main-Dispatch verloren oder fehlgeschlagen | Ticket bleibt Pending, keine Abschluss-/Erfolgspublication, kein automatischer Retry |

UNKNOWN und unbestätigtes Cleanup sperren weitere Nutzung in derselben Generation.
Storagefehler laufen weiterhin über das vorhandene `blocked`/Storage-Failure-Verhalten;
bei bereits widerrufenem Kontext keine unberechtigte Veröffentlichung dieses Fehlers.
COMMITTED allein ist keine UI-Freigabe. `OperationsResult.Value` ohne äußeren
Commitbeleg wird nachweislich unterdrückt. Ein physischer Commit wird nicht automatisch
wiederholt, wenn seine Antwort nicht veröffentlicht werden kann.

### IMPLEMENTIERT – Materialsemantik und Invalidierungsumfang

`MaterialRules`, MaterialInput-Normalisierung, Provenienz-/createdAt-Erhalt,
PREPARATION-/ACTIVE-Schreibrechte, COMPLETED-Read-only und übrige Statusgates bleiben
unverändert. MaterialItem wird vor Edit/Delete vollständig mit dem aktuell persistierten
Datensatz verglichen: ID/actionlokaler Lookup, Action-ID, Name, Mengen-/Ortslabel,
Person-/Taskreferenz, sourceTemplateKey und createdAt. Vergleich und Mutation liegen
nun nachweislich in derselben äußeren Schreibtransaktion. STALE führt vor UPDATE/DELETE
zur Rejection und bestätigtem Rollback. Keine API-Abschwächung, kein ID-only-Bypass.
Person und Task müssen weiterhin derselben Aktion angehören. Keine P3b-Lesung oder
neue Materialberechtigung. Read/Board/Summary verwenden weiterhin den bisherigen
Readpfad und erzeugen keine Mutationsepoche. DB-Schema, Foreign Keys und bestehende
Material-Referenz-/Detach-Trigger bleiben unverändert.

Material verändert nur Materialzeilen. Echte Abhängigkeiten: Materialboard sowie
`MaterialSummary.count`, das PREPARATION-Übersicht und ACTIVE-Hub anzeigen.
Der **weiterhin nicht produktiv installierte** `ActiveWorkspaceMutationAdapter`
ordnet MATERIAL deshalb den neuen Metadatenprojektionen `MATERIAL_BOARD` und
`MATERIAL_SUMMARY` zu; der Übersichtszähler ist derselbe Summarywert. Kein erfundener
Task-/Personenpayload-Write durch Material. Bestehende Familienimpacts bleiben
mindestens erhalten: Task wie zuvor TASKS/TASK_COUNTS/PEOPLE; übrige Core-Familien
weiter konservativ alle Projektionen, nun einschließlich Materialmetadaten.
Eine vollständige spätere Abhängigkeitsprüfung insbesondere von Task-/Personenänderungen
auf Materiallinks/-labels bleibt vor globalem RAM-Reuse notwendig; dieser Slice
behauptet keine fertige globale Cacheinvalidierung.

Runtime bleibt `UncachedWorkspaceMutations`, `ramReuseEnabled == false`. Epochenschritt
vor Queue, ein gemeinsames Pending, kein Ticket macht nach Commit Projektionen sauber.
Keine Payload-Registry, kein Snapshotowner, keine produktive Aktivierung von
`ActiveWorkspaceValidity`, kein RAM-Hit oder längere sensible Payloadlebensdauer.

### GETESTET – SQLite, Rennen und bestehende Regressionen

`CoreWriterIntegrationTest` erweitert den vorhandenen Harness mit echten in-memory
SQLite-Transaktionen aus dem exportierten Schema 11 und `HostCoreWriterData.materials`.
Die tatsächlichen produktiven Stores sowie die unveränderte gemeinsame Boundary und
Transaktionsfunktion laufen im Host. DB-/Lease-/Room-Endfehler und vertrauenswürdige
Scope-/Permissionwechsel sind gezielte Simulationen; kein nativer Cleanupnachweis.
Der Host lädt Tabellen/Indizes/Foreign Keys des Schemas; native Materialtrigger und
Room-generierte SQL-Pfade bleiben Gegenstand der Android-Fixtures.

Nachweise:

- Create/Edit/Delete: COMMITTED / COMPLETE erst nach äußerem Abschluss; Reads erzeugen
  keine neue Epoche. Normalisierung, Provenienz und createdAt bleiben erhalten.
- Zwei Editoren: erster Wert bleibt, alter Edit und alte Löschbestätigung STALE.
  Jeder persistierte Expected-Feldunterschied wird vor UPDATE/DELETE abgelehnt.
- INVALID (einschließlich Textgrenzen), NOT_FOUND, fremde Person/fremder Task,
  fremde/fehlende Aktion und sämtliche Aktionsstatus: keine Fremdänderung,
  bestätigte fachliche Rollbacks. Bestehende Unicode-/Referenzregressionen erhalten.
- SQL-Fehler nach einem echten INSERT-Sideeffect (synthetischer AFTER-INSERT-Trigger):
  SQL-Rollback entfernt die Zeile; trotzdem UNKNOWN ohne positiven äußeren Endbeleg,
  kein Callback. Explizite Rejection nach Sideeffect: ROLLED_BACK / COMPLETE erst nach
  zurückgekehrtem Rollback. Fehlgeschlagener Rollback bleibt UNKNOWN; uncommittierter
  SQL-Zustand wird als solcher geprüft und erst im Harness-Cleanup zurückgerollt.
- Für Create/Edit/Delete separat: Fehler vor/nach physischem Commit, DB- und
  Key-Scopecleanupfehler; tatsächlicher SQL-Zustand und Abschlussbeleg getrennt.
- READY-Verlust vor Queueausführung, während SQL-Write, an der Commitgrenze, nach
  physischem Commit und unmittelbar vor Publication. Generation-, Permission- und
  Actionwechsel unter vertrauenswürdig gebundenem Testport; keine späte Antwort.
- BLOCKED/Storage-not-ready verweigert DB-Write und Erfolg. Verlorener/fehlgeschlagener
  Main-Dispatch bleibt Pending; fehlgeschlagener Workerdispatch erzeugt keinen Write.
- Doppelte Worker- und Main-Runnables: genau eine Wirkung/Completion/Antwort.
  Material gegen Material/Task/Operations sowie die Gegenrichtung: nur erster Auftrag
  kommt in die Queue, ein gemeinsamer Epochenschritt; Busy ist kein DB-Rollbackbeleg.
- Bloßer Material-Value ohne äußeren Transaktionsbeleg: kein Erfolg.
  `ramReuseEnabled` durchgehend false. Adaptertest: beide Materialprojektionen dirty,
  Pending blockiert Reads/weitere Writer, Commit allein erlaubt keinen Reuse.

Reale zentrale Sessionautorisierung wird nicht durch Testbooleans ersetzt. Der
Hostport simuliert vertrauenswürdige Bindings für Racebeweise. Im Produkt bleiben
StorageContext/SessionCoordinator maßgeblich; actiongebundene UI-Antwortannahme zusätzlich
im bestehenden unveränderten `MaterialModel` (Generation, Action, Status, Epoch,
separate Read-/Write-Requests). Eine neue allgemeine Permissionrevision/Snapshotautorität
wurde nicht erfunden. Atomarer zukünftiger Owner-Publication-Vertrag bleibt #37.

**Gezielter Abschlusslauf:** 55 Tests (51 Data, 4 App), keine Fehler/Skips.
Die erste Vorbereitung hatte zwei Testfehler (nicht-void JUnit-Methode und synthetischer
COMPLETED-Datensatz ohne completedAt); korrigiert, keine Assertion abgeschwächt.
Vollständiger Offline-Abschlusslauf und endgültige Testzahlen: siehe Abschluss unten.

### GETESTET / NUR VORBEREITET – Android und Abschluss

`CoreWriterBoundaryStorageTest` erweitert: derselbe gemeinsame Port geht zusätzlich
an das reale `ProductMaterialRepository`. In der ACTIVE-Phase folgen
Create, Edit, STALE-Delete, Boardread, aktuelles Delete und Summaryread; doppelte
Worker-Runnables und anschließender bestehender Abschlussstatuswechsel: 11 gemeinsame
Epochenschritte, 10 bestätigte Commits plus ein
bestätigter Rollback, vier Materialantworten. Erfolgscallback prüft bereits gemeldeten
COMMITTED-/COMPLETE-Beleg. Bestehender `MaterialStorageTest` bleibt unverändert
kompatibel. Androidtests ausschließlich offline kompiliert, **nicht ausgeführt**.
Keine Installation/Instrumentation; kein Android-/SQLCipher-Laufzeit- oder nativer
Cursor-/Keycleanup-Beweis aus Hostwerten.

### NOCH NICHT ANGEBUNDEN / BLOCKER

- [#35](https://github.com/JulianGKK/action-planner/issues/35): Materialwritergrenze
  **IMPLEMENTIERT / GETESTET** im beschriebenen Umfang. ExpenseWriter,
  ProtocolWriter, MemoryReportWriter, DocumentWriter, Leadership/P3b sowie übrige
  Maintenance-/Recovery-/Löschpfade weiter **UNWIRED / NOT SAFE FOR RAM REUSE**.
  Keine globale Invalidierungsfreigabe durch diesen Slice.
- [#36](https://github.com/JulianGKK/action-planner/issues/36): vorhandener
  Material-Expected-CAS erhalten; gewöhnliche veraltete Edits/Deletes getestet.
  Kein neuer persistierter ABA-Schutz. Action-/Operations-Live-Editorlücken aus §51
  bleiben offen; CreateRole-Abschlussvertrag aus §52 erhalten.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): produktiver
  Snapshotowner und atomare Payload-Publication **NOCH NICHT ANGEBUNDEN**.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine Navigation-/
  Performanceintegration oder neue Performancefreigabe.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): eigenständiger
  Cursor-/Cleanup-**BLOCKER** unverändert. `SQLCipherStatement.close()` schließt den
  Cursor nicht explizit. Host-Statementabschluss und simulierte Cleanupfehler sowie
  frühere FD-PASSes beweisen keine vollständige native Cursorfreigabe.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink, B2Driver,
Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Sicherheitsbudget unverändert.
Keine neuen Dependencies/Permissions/Persistenzorte, keine Klartextdateien,
Schemamigration, Produktnavigation, RAM-Hits, Snapshotaktivierung oder Gerätezugriffe.
Keine pauschale Editor-, P11-Snapshot- oder Release-1.0-Freigabe.


**Vollständiger Offline-Abschlusslauf: PASS.**

```text
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest \
  :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin \
  :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
```

1.467 Hosttests: App 668, Data 795, Security 4; **0 Fehler, 0 Errors, 0 Skips**.
Domain ohne Testsources, nicht als zusätzlicher Test gezählt. Bestehende Personen-,
Rollenplan-, Rollenrename/-delete-, Task-, Operations-, Material-, Security- und
Publicationregressionen enthalten. App-Offline-Build, beide Androidtest-Kompilationen,
Architekturprüfung und Prototype-/Demo-Manifestchecks erfolgreich. Compilerwarnungen
(u. a. bestehende unnötige Non-null-Assertions und eine weitere im erweiterten Fixture)
sind sichtbar geblieben; keine Tests übersprungen oder Assertions abgeschwächt.

Geändert: ProductMaterialRepository.kt, ProductRuntime.kt, CoreWriterData.kt,
WorkspaceMutationInvalidation.kt, ActiveWorkspaceMutationAdapter.kt,
ActiveWorkspaceValidity.kt (nur Projektionsbezeichner), WorkspaceWriteBoundary.kt
(nur Kommentar), HostCoreWriterData.kt, CoreWriterIntegrationTest.kt,
MaterialWriterWiringTest.kt, CoreWorkspaceMutationAdapterTest.kt,
CoreWriterBoundaryStorageTest.kt und dieser Assessmentabschnitt.

Abschlusskontrollen: `git diff --check`, `git status -sb`, gezielte Diffprüfung,
regulärer WIP-Commit/Push auf den bestehenden Branch und unabhängiger GitHub-Ref-Abgleich.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen eingecheckt. Danach **STOPP BIS ZUM UNABHÄNGIGEN GITHUB-REVIEW**.
Kein Force-Push, Merge, Release oder Produktupdate.


## 54. Issue #35 – Expense-Writerintegration

### Ausgangsbasis und RED-Nachweise

Vor Änderungen: lokaler HEAD und unabhängiger GitHub-Branchref exakt
`12c7e2054a3c0dd5e7f8e6d4676ad88db6d11b68`, Branch
`p11-performance-optimization`, Arbeitsbaum sauber. Keine fremden Änderungen.

Tatsächlicher vorheriger Pfad: `ExpenseRepository` → `ProductExpenseRepository`
→ eigener `write`/`run` → `withWriteTransaction` → `ExpenseStore` → Room-DAOs →
SQLCipher. Bereits vorhandene READY-/Schritt-/Commit-/Publicationgates ersetzten
weder die gemeinsame Vor-Queue-Invalidierung noch einen expliziten äußeren
Commit-/Rollback-/Cleanupbeleg.

**GETESTET – RED vor Implementierung:** Zwei neue Methoden in
`ExpenseWriterWiringTest` schlugen fehl: gemeinsame Boundary fehlt und Runtime
übergibt den gemeinsamen Mutationsport nicht. Statischer **Wiringnachweis**, kein
SQLite-Transaktions- oder nativer Cleanupbeweis. Nach Implementierung grün;
eine weitere Wiringregression sichert das Einfrieren beider Listen vor dem Writepfad.
Die dynamischen Nachweise verwenden echte SQLite-/Schema-11-Transaktionen.

Zusätzlicher **RED-SQLite-Befund** innerhalb der geforderten Positionsvalidierung:
Ein einzelnes Payment an persistierter Position `4294967296` wurde nach
`getLong(...).toInt()` als Position 0 materialisiert und unerwartet akzeptiert.
Der gezielte Hosttest erwartete ProductStorageFailure und war rot. Die generierte
Room-DAO verwendet tatsächlich diese Int-Konvertierung; der Host bildet sie nach.
Dies ist ein reproduzierter zusätzlicher Validierungsbefund, kein behaupteter
Android-Laufzeitnachweis. Die Query `invalidPaymentTypes` lehnt nun zusätzlich
`position<0 OR position>31` **vor** Paymentmaterialisierung ab. Negativer Wrap
`-4294967296` ebenfalls getestet; danach beide grün. Keine Schemaänderung: nur
DAO-Prüfquery, weiterhin zusätzlich vollständiger Vergleich mit den erwarteten
Positionen `0..N-1`. SQL-Typgates für Amount/Position bleiben vollständig erhalten.

### IMPLEMENTIERT – gemeinsame Writergrenze

`WorkspaceWriterFamily.EXPENSE` ergänzt. `ProductRuntime` injiziert dieselbe
`workspaceMutations`-Instanz wie an Action, Person, Operations, Task und Material.
Standalone-Constructor bleibt entsprechend den vorhandenen Writerfamilien kompatibel:
Default `UncachedWorkspaceMutations`, Clock weiterhin als letzter Parameter. Keine
zweite Registry, Material-/Expense-Epoche oder Sessionautorität.

Create/Edit/Delete verwenden jetzt:

```text
WorkspaceWriteBoundary.submit(EXPENSE, actionId)
  → aktueller READY-Kontext / OPERATION-Gate
  → synchrones Pending-Ticket vor worker.execute
  → READY_TO_OPEN / SessionDbKeyOwner.withDbKey
  → ActionDatabaseAccess inklusive bisheriger SQLCipher-/Room-Preflights
  → useWriterConnection / confirmedWorkspaceTransaction(IMMEDIATE)
  → ExpenseStore / bisherige Room-DAOs
  → tatsächliches äußeres Transaktionsende
  → Rückkehr des vollständigen DB-/Keyscopes
  → Ownerthread: Ticketcompletion
  → zentrale StorageContext.publish und ticket.isCurrent
  → Ergebnis-Callback
```

Die gemeinsame Boundary, `WorkspaceWriteProgress` und
`confirmedWorkspaceTransaction` wurden funktional **nicht verändert**. ExpenseStore
öffnet keine unabhängige äußere Transaktion. Aktive äußere Transaktionen werden vom
bestehenden bestätigten Transaktionsvertrag abgewiesen; ein SAVEPOINT ist kein
äußerer Commitnachweis. Ein Store-Value allein genügt nicht.

| Ereignis | Writerbeleg und freigegebene Antwort |
|---|---|
| Äußerer Commit normal zurückgekehrt, alle DB-/Keyscopes normal zurückgekehrt | COMMITTED / COMPLETE; Erfolg zusätzlich nur unter gültiger zentraler Publication und aktuellem Ticket |
| Fachliche Rejection und äußerer Rollback normal zurückgekehrt | ROLLED_BACK / COMPLETE; erst dann fachliche Ablehnung veröffentlichen |
| Unklares äußeres Commit-/Rollbackende oder Exception ohne positiven Endbeleg | UNKNOWN; kein Erfolgs- oder fachlicher Rejectioncallback |
| Physischer Commit bestätigt, danach DB-/Leasecleanup unbestätigt | COMMITTED / FAILED_OR_UNCONFIRMED; kein Erfolg |
| Später Revoke nach bekanntem physischem Commit | Kein erfundener Rollback; weiterhin keine unberechtigte Publication |
| Bereits Pending vor Queue | STALE-Busy-Ablehnung ohne Worker; keine ausgeführte DB-Transaktion, kein Rollbackbeweis |
| Workerdispatch vor Start fehlgeschlagen | Vorhandener Nichtausführungsbeleg ROLLED_BACK / COMPLETE und Fehlerpfad; kein DB-Rollback vorgetäuscht |
| Verlorener/fehlgeschlagener Main-Dispatch | Pending bleibt bestehen; keine Abschlussantwort und kein automatischer Retry |

UNKNOWN bzw. unbestätigtes Cleanup sperren die betreffende Generation fail-closed;
vorhandenes Storage-Failure-/BLOCKED-Verhalten bleibt maßgeblich. Cleanup wird nicht
aus dem Repository-Value abgeleitet. Die Boundary setzt COMPLETE erst nach Rückkehr
des ganzen Workclosures. Physisch persistierte Daten und unterdrückte UI-Erfolge
sind unterschiedliche Beobachtungen und werden getrennt getestet.

### IMPLEMENTIERT – Parent-/Payment-Atomarität, CAS und Listen

Expense-Hauptdatensatz und sämtliche Payments liegen in derselben äußeren
IMMEDIATE-Transaktion:

- Create: Parent-INSERT und alle geordneten Payment-INSERTs.
- Edit: vollständiger Read einschließlich Payments, vollständiger Expected-Vergleich,
  Parent-UPDATE, clearPayments und alle Ersatz-Payment-INSERTs.
- Delete: vollständiger Expected-Vergleich, Parent-DELETE und bestehende FK-Cascade
  der Children. Kein manuelles Wiederherstellen oder Teil-Recovery außerhalb der Transaktion.

Unverändert verglichen: Expense-ID über actionlokalen Lookup, Action-ID, Titel,
totalCents, gesamte geordnete Paymentliste einschließlich Labels/Beträgen/Anzahl,
createdAt. Veralteter Edit/Delete liefert STALE vor Mutation. Ein aktueller neuer
Editor darf anschließend erfolgreich speichern. Missing/foreign Action oder Expense
verändert keine fremden Daten. PayerLabel bleibt unabhängiger Text ohne Person-FK;
kein P3b-Read und keine neuen Rechte. Insbesondere bleibt die bestehende administrative
COMPLETED-Kostenausnahme erhalten: ExpenseRules erlaubt Schreiben/Löschen auch dort;
dadurch werden keine Task-/Material-/Operationsrechte erweitert.

**GETESTET – Listenaliasing:** List-typisierte ExpenseInput-/Expected-Objekte können
aufrufereigene MutableLists enthalten; im alten direkt erfassten Closure änderten
spätere Listenmutationen die eingereichten Werte. Ein expliziter Aliastest zeigt diese
Eigenschaft. Produktion erstellt jetzt vor dem Queuepfad mittels
`freezeExpenseInput`/`freezeExpenseExpected` eigene, unmodifiable Listen. Paymentblätter
bestehen ausschließlich aus unveränderlichen String-/Long-vals; zusätzliche Kopien
der Blätter sind dafür nicht erforderlich. Tests: Mutation der Quelllisten nach
Queueeintritt verändert weder Create/Editeingabe noch Expected für Edit/Delete;
MutableList-Casts können den eingefrorenen Stand nicht verändern. Nachträgliche
Aliasänderung kann insbesondere einen STALE-Expected nicht in einen aktuellen
Expected verwandeln. Keine neue persistierte Revision oder zusätzliche Langzeitretention.

### IMPLEMENTIERT / GETESTET – Ganzzahlen und minimale DAO-Seam

CoreWriterData lediglich um `ExpenseDao` ergänzt, RoomCoreWriterData delegiert an
dieselben DAOs. ExpenseStore wurde mechanisch auf diesen Datenport umgestellt.
Keine neue Transaktions-, Key- oder Autoritätsverantwortung in der Seam.

ExpenseRules.normalized/valid und EuroMoney unverändert: ausschließlich EUR-Cent als
Long, exakte Summierung mit Overflow-Erkennung, positive Beträge, 1..32 Payments,
exakte Übereinstimmung Gesamt-/Paymentsumme und bisherige Unicodegrenzen.
Materialize prüft weiterhin integerParent, invalidPaymentTypes, lückenlose Positionen,
actionId/expenseId, Normalisierung und vollständige fachliche Gültigkeit. Ein
SQL-REAL-/TEXT-Wert wird nicht durch stillen getLong-Cast freigegeben. Die zusätzliche
Positionsrange schließt den oben belegten Int-Wrap. ExpenseSummary prüft weiter
ungültige Parenttotals und verwendet die exakte SQLite-Integer-SUM; ein Gesamtüberlauf
wird als Fehler behandelt. Summary bleibt bewusst Count/Summe der Parentzeilen,
kein impliziter Vollread aller Payments und kein vollständiger Boardintegritätsbeweis.

HostCoreWriterData bildet die unveränderten DAO-Queries auf echtem Schema-11-SQLite
ab, einschließlich typeof-Gates. Für Expensefelder modelliert der Adapter native
numerische getLong-Konvertierung ausdrücklich, damit der Test nicht schon an einem
strengeren Hostparser scheitert und die Produktionsgates tatsächlich geprüft werden.
Die Boundsquery und ihre Room-generierte SQL-Form sind zusätzlich getestet. Ein
Host-only `afterChange`-Hook injiziert Fehler nach realen SQL-Sideeffects; daneben
prüft ein tatsächlicher SQLite-Triggerfehler den Abbruch beim zweiten Payment.
Keine neue Dependency, Produktionsseam bleibt eng auf DAO-Zugriff beschränkt.

### GETESTET – echte SQLite-Regressionen und Races

Neuer `ExpenseWriterIntegrationTest` verwendet den vorhandenen gemeinsamen
`CoreWriterIntegrationTest.H`-Harness (nur interne Testsichtbarkeit erweitert).
Produktive ExpenseStore-Logik, gemeinsame Boundary und bestätigte äußere Transaktion;
SQLite in-memory aus exportiertem Schema 11, Foreign Keys aktiviert. Outcome-/
Cleanup-/Scopefehler werden vertrauenswürdig simuliert; keine UI-Boolean-Autorität
in der Runtime ergänzt. Bestehende Corewriter-Familienschleifen prüfen EXPENSE
zusätzlich mit echten Parent-/Paymentwrites.

Nachweise:

- Create/Edit/Delete samt allen Payments und exakter Summary in PREPARATION, ACTIVE
  und COMPLETED; Read/Summary erzeugen keine Mutationsepoche.
- Vollwert-CAS: geänderter Parent, Betrag, Label, Reihenfolge, Anzahl und alter
  Delete-Dialog; erster bestätigter Stand bleibt erhalten. INVALID, fehlende/fremde
  Aktion und fehlende Kostenposition: bestätigte fachliche Rollbacks ohne Fremdänderung.
- Ungültige Summen, 0/negative Payments, fehlende bzw. >32 Payments, Longoverflow,
  Unicodegrenzen; maximale gültige 32-Payment-Eingabe bleibt zugänglich.
- SQL-REAL/TEXT für Parent-Total/createdAt und Payment-Amount/Position, ungültige
  Positionen einschließlich Int-Wrap, fehlende/zu viele Children, falsche Summen und
  unnormalisierte Labels: keine Materialisierung/Erfolgspublication. Summary-Maximum
  exakt Long.MAX_VALUE, weiterer positiver Parent führt zu SQLite-Integeroverflow.
- Fehler nach Parent-INSERT, im Payment-INSERT, nach Parent-UPDATE, nach clearPayments,
  bei erneutem Payment-INSERT und nach Parent-DELETE: kompletter vorheriger SQL-Stand
  samt aller Payments bleibt erhalten. Createfehler hinterlässt weder Parent noch
  Childrest. Foreign-action Payment-FK und Parent-/Action-Cascade separat geprüft.
- Fachliche Rejection nach echten Sideeffects: ROLLED_BACK erst nach tatsächlichem
  äußeren Rollback. Fehlgeschlagener Rollback: UNKNOWN, kein Callback; uncommittierter
  SQL-Zustand separat beobachtet und erst im Harness-Cleanup zurückgerollt.
- Für alle drei Writes separat: Lock vor Worker, während Write, vor Commit, nach
  Commit und vor Publication; Generation-/Permission-/Actionwechsel, Storage BLOCKED,
  unklarer Abschluss vor/nach physischem Commit, DB-/Keycleanupfehler.
- SQL-Zustand, Outcome und UI-Callback getrennt; doppelte Worker-/Main-Runnables
  haben nur eine Wirkung. Lost/failed Main-Dispatch bleibt Pending; fehlgeschlagener
  Workerdispatch schreibt nichts. Value ohne bestätigten Commit erzeugt keinen Erfolg.
- EXPENSE ↔ MATERIAL/TASK/OPERATIONS und jede übrige Paarung dieser vier Familien:
  erste Mutation invalidiert vor Queue, alle weiteren Busy ohne Queue/DB-Transaktion;
  eine gemeinsame Epoche, kein zweiter Invalidierer und kein RAM-Hit.
- Negativer ABA-Test: Expense A → B → A stellt inklusive Payments denselben Vollwert
  wieder her; alter Expected wird dann akzeptiert. Keine allgemeine ABA-Sicherheit.

Gezielter Abschlusslauf: **82 Tests (77 Data, 5 App), 0 Fehler, 0 Errors, 0 Skips**.
Ein Vorbereitungsfehler im neuen JUnit-Test (nicht-void Rückgabetyp) wurde korrigiert;
keine Assertion abgeschwächt. RED-Fehler und spätere grüne Nachweise getrennt dokumentiert.

### Android – NUR OFFLINE VORBEREITET, keine Gerätefreigabe

Bestehende ExpenseStorageTest-Aufrufe bleiben kompatibel. Zusätzlicher isolierter
SQLCipher-/Room-Positionswrap-Test vorbereitet. CoreWriterBoundaryStorageTest um
reales ProductExpenseRepository mit gemeinsamem Port erweitert: Create mit nach
Submit geleerter Quellliste, Edit mit Paymentersatz, STALE-Delete, Boardread, aktuelles
Delete und leere Summary; vier Expenseantworten, jeweils doppelte Worker-Runnables.
Mit unveränderten Core-/Materialnachweisen nun 15 gemeinsame Epochenschritte,
13 COMMITTED-/COMPLETE- und zwei ROLLED_BACK-/COMPLETE-Belege.

**Nicht ausgeführt.** Eigener synthetischer Namespace/Key, Keyinvalidate/-fill im
bestehenden Finally. Vor einem später gesondert genehmigten Gerätelauf müssen
Fixture-Isolierung und sichere, vollständige, geprüfte Verzeichnisbereinigung
zusätzlich nachgewiesen werden; der bestehende Corefixture hat derzeit keine so
freigegebene Verzeichnisbereinigung. Offline-Kompilation erteilt keine Gerätefreigabe.
Keine Installation, Instrumentation oder Änderung installierter Apps in diesem Slice.

### NOCH NICHT ANGEBUNDEN / BLOCKER

- [#35](https://github.com/JulianGKK/action-planner/issues/35): Expensegrenze
  **IMPLEMENTIERT / GETESTET** im beschriebenen Host-/Wiringumfang. ProtocolWriter,
  MemoryReportWriter, DocumentWriter, Leadership/P3b, Maintenance/Recovery bleiben
  **UNWIRED / NOT SAFE FOR RAM REUSE**. Keine globale Snapshotinvalidierungsfreigabe.
- [#36](https://github.com/JulianGKK/action-planner/issues/36): Expense-Vollwert-CAS
  einschließlich eingefrorener Payments erhalten; allgemeines persistiertes ABA
  nicht gelöst. Offene Action-/Operations-Live-Editorgrenzen aus §51 unverändert.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): Snapshotowner und
  atomare Payload-Publication **NOCH NICHT ANGEBUNDEN**. Lokale Requesttokens sind
  keine Sessionautorität. StorageContext bleibt zentrale Publicationgrenze;
  unverändertes ExpenseModel prüft zusätzlich Action/Status/Generation/Epoch und
  eigene Write-/Readrequests vor Formularabschluss/Feedback. Keine optimistischen Patches.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine neue Navigation,
  Cache-/Performanceintegration oder Android-Performancefreigabe.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): eigenständiger nativer
  Cursor-/Cleanup-**BLOCKER**. SQLCipherStatement.close() schließt den Cursor nicht
  explizit. Host-Statementcleanup, simulierte DB-/Keycleanupfehler und FD-PASS sind
  kein vollständiger nativer Cursor-/Key-/Room-Leaknachweis.

Runtime bleibt UncachedWorkspaceMutations, ramReuseEnabled=false. Der nicht produktiv
installierte ActiveWorkspaceMutationAdapter kennt EXPENSE_BOARD/EXPENSE_SUMMARY:
Board, Count und totalCents für PREPARATION-Übersicht und ACTIVE-Hub. Keine erfundenen
Material-/Taskänderungen durch Expense; deren bisherige Impacts bleiben erhalten,
übrige konservative Corefamilien umfassen auch die neuen Projektionsmetadaten.
Pending sperrt Reuse, Commit markiert nichts automatisch clean, UNKNOWN/Cleanupfehler
bleiben fail-closed. Kein Payloadowner, keine zweite Registry oder RAM-Freigabe.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink, B2Driver,
Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Sicherheitsbudget unverändert.
Keine neue Permission/Dependency/Persistenz, keine Migration, keine produktive
Cache-/Snapshotaktivierung. Andere Writerproduktpfade unverändert. Keine vollständige
P11-Snapshot-, Editor- oder Release-1.0-Freigabe.


**Vollständiger Offline-Abschlusslauf: PASS.**

```text
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest \
  :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin \
  :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
```

**1.497 Hosttests bestanden:** App 669, Data 824, Security 4. **0 Fehler, 0 Errors,
0 Skips.** Domain ohne Testsources, nicht als weiterer Test gezählt. Bestehende
Material-/Task-/Action-/Person-/Rollenplan-/Operations-/Security-/Publicationregressionen
enthalten. App-Offline-Build, beide Androidtest-Kompilationen, Architekturprüfung und
Prototype-/Demo-Manifestchecks erfolgreich. Vorhandene Compilerwarnungen sichtbar
geblieben; keine Testskips oder abgeschwächten Assertions. Androidtests nur kompiliert.

Geändert: ProductExpenseRepository.kt, ProductRuntime.kt, ExpenseDatabase.kt
(ausschließlich Positionsprüfquery), CoreWriterData.kt,
WorkspaceMutationInvalidation.kt, ActiveWorkspaceMutationAdapter.kt,
ActiveWorkspaceValidity.kt (nur neue Projektionsbezeichner), HostCoreWriterData.kt,
CoreWriterIntegrationTest.kt (Testharnesssichtbarkeit und neue Familienschleifen),
ExpenseProjectionTest.kt, neue ExpenseWriterWiringTest.kt und
ExpenseWriterIntegrationTest.kt, CoreWorkspaceMutationAdapterTest.kt,
CoreWriterBoundaryStorageTest.kt, ExpenseStorageTest.kt und dieser Abschnitt.
WorkspaceWriteBoundary/-Progress/confirmedWorkspaceTransaction sowie die anderen
produktiven Writer wurden funktional unverändert verwendet.

Abschlusskontrollen: `git diff --check`, `git status -sb`, vollständige Diffprüfung,
regulärer WIP-Commit/Push auf den bestehenden Branch und unabhängiger GitHub-Ref-Abgleich.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen eingecheckt. Danach **STOPP BIS ZUM UNABHÄNGIGEN GITHUB-REVIEW**.
Kein Force-Push, Merge, Release oder Produktupdate.


## 55. Issue #35 – Protokoll-Writerintegration

### Ausgangsbasis und getrennte RED-Nachweise

Vor Änderungen lokaler HEAD und unabhängiger GitHub-Branchref exakt
`98671f320cfee81d8b1fbca7d6e60b0eb3152f0d`, Branch
`p11-performance-optimization`, Arbeitsbaum sauber. Keine fremden Änderungen.

Vorheriger Produktpfad: `ProtocolRepository` → `ProductProtocolRepository`
→ eigener `write`/`run` mit `withWriteTransaction` → `ProtocolStore` → Room/SQLCipher.
READY-/Schritt-/Commit-/Publicationgates waren vorhanden. Gemeinsame Invalidierung
vor Workerqueue und expliziter äußerer Commit-/Rollback-/Cleanupbeleg fehlten.
**GETESTET – statischer RED-Wiringtest:** Zwei neue Methoden schlugen vor Integration
fehl (gemeinsame Boundary fehlt; Runtime injiziert gemeinsamen Mutationsport nicht).
Nach Integration grün. Dies ist ausdrücklich kein SQLite-Transaktionsbeweis.
Eine zusätzliche Wiringregression prüft Freeze vor dem Writepfad für alle drei APIs.

Zusätzliche **dynamische RED-SQLite-Befunde**:

- Bei bewusst beschädigten Rohdaten konnte eine einzelne Identifierposition
  `4294967296` nach `getLong(...).toInt()` als 0 erscheinen und akzeptiert werden.
  Der Host bildet die native numerische Konvertierung nach; auch die generierte Room-DAO
  verwendet den Int-Cast. Produktions-INSERTs waren bereits durch den unveränderten
  Identifiertrigger begrenzt, UPDATEs durch Immutability-Trigger gesperrt. Der Test
  entfernt gezielt den UPDATE-Trigger zur Rohkorruptionsprüfung; er behauptet keinen
  erlaubten Produktwrite. Neuer schmaler `invalidIdentifierPositions`-Count prüft
  SQL-type integer und Range 0..31 **vor** Identifiermaterialisierung. Danach grün,
  zusätzlich negative Wraps, REAL/TEXT und normale Positionslücken geprüft.
- Nach realem DELETE der Originalrevision aus einer zweirevisionigen Testhistorie
  konnte ein alter vollständiger Expected weiterhin eine neue Markierung anhängen.
  Der Test erwartete keine Antwort/Änderung und war rot. `existing` verwendet jetzt
  vor Current/CAS zusätzlich das bestehende `checkOriginals`-Gate. Ohne Original
  keine Fortsetzung, Storagefehler statt erfundener fachlicher Ablehnung. Der Test
  ist danach grün. Keine historische Reparatur und kein automatisches Nachtragen.

Beide Befunde wurden nach Korrektur anfänglicher Testhilfsfehler tatsächlich als
Assertion-RED reproduziert; Compile-/Binding-/JUnit-Vorbereitungsfehler sind kein
Ausgangsbeweis. Keine Assertion abgeschwächt und kein Fehler durch Skip ersetzt.

### IMPLEMENTIERT – eine Writerfamilie und gemeinsame Autoritätsgrenze

`WorkspaceWriterFamily.PROTOCOL` ergänzt. ProductRuntime übergibt dieselbe
`workspaceMutations`-Instanz wie an Action, Person, Operations, Task, Material und
Expense. Kein unabhängiges Protokollticket, zweiter Invalidierer oder neuer Owner.
Standalone-Constructordefault und abschließender Clockparameter bleiben analog den
vorhandenen Writerfamilien kompatibel; Produktion injiziert ausdrücklich den gemeinsamen
Port. Runtime weiterhin `UncachedWorkspaceMutations`, `ramReuseEnabled=false`.

Für `createEntry`, `reviseEntry` und `markMistaken`:

```text
WorkspaceWriteBoundary.submit(PROTOCOL, actionId)
  → aktueller READY-/OPERATION-Kontext
  → synchrones Pending-Ticket VOR worker.execute
  → READY_TO_OPEN / SessionDbKeyOwner.withDbKey
  → ActionDatabaseAccess mit bisherigen SQLCipher-/Room-Preflights
  → useWriterConnection / confirmedWorkspaceTransaction(IMMEDIATE)
  → ProtocolStore / dieselben Room-DAOs
  → tatsächlicher äußerer Transaktionsabschluss
  → Rückkehr des vollständigen DB-/Keyscopes
  → Completion am Ownerthread
  → StorageContext.publish + aktuelles Ticket
  → ProtocolResult-Callback
```

Die gemeinsame Boundary, WorkspaceWriteProgress und confirmedWorkspaceTransaction
funktional unverändert. Kein eigener zweiter äußerer Commit im Store/Port. Ein
bereits aktiver äußerer Transaktionsscope ist kein akzeptierter Commitbeweis;
SAVEPOINT-Erfolg ersetzt keinen äußeren Abschluss. `ProtocolResult.Value` und
`ProtocolResult.Rejected` bleiben unverändert eigene Typen; keine Übersetzung in
OperationsResult und kein Verlust von WRONG_ACTION/INVALID_STATE/INVALID/STALE.

| Situation | Beleg und erlaubte Antwort |
|---|---|
| Äußerer Commit normal zurückgekehrt und kompletter Workscope normal zurückgekehrt | COMMITTED / COMPLETE; Erfolg nur bei gültiger zentraler Publication und aktuellem Ticket |
| Fachliche Rejection, expliziter äußerer Rollback normal zurückgekehrt, Cleanup bestätigt | ROLLED_BACK / COMPLETE; erst dann fachlicher Rejectioncallback |
| Unklares Commit-/Rollbackende oder Exception ohne positiven äußeren Endbeleg | UNKNOWN; kein Erfolgs- oder fachlicher Rejectioncallback |
| Commit bekannt, DB-/Keyscope danach nicht bestätigt zurückgekehrt | COMMITTED / FAILED_OR_UNCONFIRMED; kein Erfolg |
| Revoke nach bekanntem physischem Commit | Commit bleibt COMMITTED; keine unberechtigte Antwort und kein erfundener Rollback |
| Fremdes/gleiches Pending vor Queue | ProtocolFailure.STALE als Busy-Rejection; keine ausgeführte DB-Transaktion oder tatsächlicher Rollback |
| Workerdispatch vor Start fehlgeschlagen | Bestehender Nichtausführungsbeleg ROLLED_BACK / COMPLETE plus Fehlerpfad; kein behaupteter DB-Rollback |
| Main-Dispatch verloren/fehlgeschlagen | Pending bleibt bestehen; keine Completionpublication und kein automatischer Retry |

Unbestätigtes Cleanup/UNKNOWN bleiben für dieselbe Generation fail-closed; bestehendes
Storage-Failure-/BLOCKED-Verhalten bleibt maßgeblich. SQL-Zustand, Completion und
freigegebene UI-Antwort sind getrennte Belege. Kein optimistisches Patchen und keine
automatische Wiederholung physisch möglicherweise bereits persistierter Revisionen.

### IMPLEMENTIERT / GETESTET – Append-only- und Relationsatomarität

Eine Create-Transaktion umfasst Entry, Originalrevision Nummer 1, alle betroffenen
Personen, optionale berichtende Person und alle geordneten offiziellen Identifier.
Revise/MarkMistaken lesen und vergleichen den aktuellen vollständigen Stand in derselben
äußeren IMMEDIATE-Transaktion und fügen ausschließlich neue Revision-/Relationszeilen
hinzu. Kein UPDATE eines Originaleintrags oder einer vorherigen Revision.

Original, Revisionstypen, streng steigende Nummern, mistaken-Regeln, templateKey-
Kontinuität, recordedAt, recordedAfterCompletion und createdAfterCompletion bleiben
unverändert. Produktions-Immutability-, Quellen-/Identifier- und Revisionsregeltrigger
sowie sämtliche Schema-11-FKs/Indizes erhalten. Keine Schemamigration.

Fachlich weiterhin nur ACTIVE/COMPLETED. Persistierte Phase ist maßgeblich;
abweichender erwarteter Actionstatus liefert STALE, nicht erlaubte Phase INVALID_STATE.
COMPLETED-Neueinträge und nachträgliche Revisionen behalten ihre getrennten Phasenflags.
Keine Erweiterung auf PREPARATION oder P3b.

Expected-CAS umfasst gesamten Entry und gesamte Currentrevision: Identitäten,
Revisionnummer/-typ, vollständige Eingabe einschließlich Personen/Identifiern,
recordedAt, Phasenflags und mistaken. Zwei alte Editoren können nicht dieselbe
Fortsetzung erfolgreich anhängen: erste Revision bleibt erhalten, zweiter Write
und alte MarkMistaken-Bestätigung STALE. Neu geöffneter Editor darf fortsetzen.
Fremde Expected-Action/Entryrelation bleibt WRONG_ACTION; fehlender Entry/Action
NOT_FOUND; falsche Expected-Revision bei existierendem Current STALE. Fehlendes
Original/Current einer existierenden Historie ist eine Integritätsverletzung, kein
künstliches NOT_FOUND-Ergebnis. Bestehende vollständige Detail-Historienvalidierung
bleibt erhalten. Das zusätzliche Append-Gate prüft Originalpräsenz; es ist kein
neu behaupteter universeller Rohdatenvalidator aller denkbaren Historienbeschädigungen.

SQL-Fehler nach Entry-INSERT, Revision-INSERT und in jeder Relationsfamilie rollen
sämtliche neuen Zeilen zurück. Bei Append bleibt die ganze vorherige Historie
unverändert, bei Create bleibt kein halber Entry/Original-/Relationsrest.
Keine Teil-Recovery außerhalb der Transaktion.

### IMPLEMENTIERT / GETESTET – Freeze und fachliche Integritätsgates

Der bestehende normalized()/frozen()-Vertrag kopierte die Collections bereits:
Mutationen der **ursprünglichen** MutableSets/MutableLists nach Normalisierung änderten
die Kopien nicht. Dafür wurde kein falscher ursprünglicher Aliasverlust behauptet.
Die erzeugten Set-/Listkopien waren jedoch nicht durchgehend unveränderlich;
Mutable-Collection-Casts konnten bei mehrgliedrigen Kopien deren Inhalt verändern.
Das ist separat reproduziert.

Vor Queueeintritt verwendet Produktion nun `freezeProtocolInput` mit eigenen
unmodifiable LinkedHashSet-/ArrayListkopien; Expected wird bis zur verschachtelten
Revision.input genauso eingefroren. Scalarfelder und Identifierblätter sind immutable
Werte. Entwürfe werden wie bisher normalisiert; Expected wird **nicht** nachträglich
normalisiert und dadurch an den Iststand angepasst. Tests mit Create, Revise und
MarkMistaken: Quellen nach Submit leeren/ändern, Expected-/Entwurfscollections trennen,
Casts verweigern Mutation; eingereichte Snapshots bleiben unverändert.
Kein neuer Domainvertrag, Formularregistry oder persistierter Klartextentwurf.

ProtocolRules.normalized/valid unverändert, einschließlich Titel-/Beschreibungs-/
Orts-/Ansage-/Rechtsgrundlagenlimits, templateKey, Klassifikation, sourceType,
announcementKind/Text, Personenbezüge und höchstens 32 Identifier. Personen müssen
aktionslokal existieren; tatsächliche Schema-FKs lehnen fremde Relationen zusätzlich ab.
Identifierreihenfolge und lückenlose Positionen bleiben geprüft. Neue Rohpositionsquery
ist ausschließlich die belegte Integritätshärtung vor Int-Cast, keine spekulative
Ausweitung aller DAOs. Revisionsnummer-Long.MAX_VALUE verweigert Append statt Wrap.

### GETESTET – echte SQLite-Seam, Races und historische Vollständigkeit

CoreWriterData um ProtocolDao erweitert; RoomCoreWriterData delegiert an dieselben
Produkt-DAOs. HostProtocolDao implementiert alle verwendeten Methoden mit den
entsprechenden echten SQL-Queries: Entry/Current/Revisions, Summary/Timeline,
Relationen, Personenprojektion, Originalcheck, Rohpositionscheck und alle Inserts.
Keine vereinfachte fachliche Materialisierung im Testdouble.

ProtocolWriterIntegrationTest nutzt den bestehenden gemeinsamen Corewriter-Harness
mit in-memory SQLite/Schema 11, aktivierten FKs und Indizes. Der Host führt zusätzlich
**den produktiven installProtocolConstraints-Installer** auf derselben SQLite-Verbindung
aus. Der schmale prepare/step/close-Adapter dient nur der Installation der tatsächlichen
Trigger; keine neue Transaktions-, Autoritäts- oder Lifecycleinstanz. DAO-/Storewrites
verwenden weiterhin reale SQL-Zustände. Host-only afterChange injiziert Fehler nach
echten Inserts, echte SQLite-Fehlertrigger ergänzen die Faultsimulation.

Nachweise:

- Create mit mehreren Personen/Identifiern, Correction, Addendum, MarkMistaken,
  wiederholtes unzulässiges Markieren, Addendum/Correction bei mistaken und bewusstes
  Clear: alle alten Revisionen bleiben vollständig erhalten. Direkte UPDATEs jeder
  Protokolltabelle scheitern an produktiven Immutability-Triggern.
- Vollständiger CAS einschließlich Inhalt/Relationen/Flags; zwei alte Editoren,
  alter Markdialog, neue gültige Fortsetzung, falsche Action/Revision und fehlender Entry.
- INVALID-Input, ungültiger Templatewechsel/Revisionsart, fremde/fehlende Personen,
  Unicode-/Text-/Identifiergrenzen: fachlicher Rollback bestätigt. Maximal gültige
  32-Identifier-Eingabe bleibt lesbar. Bewusst erzeugte verwaiste Personenrelation
  (FKs nur im Rohkorruptionstest deaktiviert) führt zum bestehenden Detailfehler.
- Fehlendes Original: Summary/Timeline/Detail verweigert; Append verweigert jetzt
  ebenfalls. Keine automatische Reparatur. Overflowbranch mit bewusst manipuliertem
  Current=Long.MAX_VALUE liefert INVALID_STATE ohne neue Revision; keine Behauptung,
  dass eine solche lückenhafte Rohhistorie fachlich gültig wäre.
- Für Create, Revise, MarkMistaken jede erreichbare Insertgrenze: Fehler nach Entry
  (nur Create), Revision, betroffenen Personen, berichtender Person und Identifiern;
  SQL-Snapshots aller fünf Tabellen bleiben vollständig erhalten. Echte Fehler beim
  zweiten Identifier-/Personeninsert erzeugen keine halben neuen Revisionen.
- Für alle drei Writes: Lock vor Worker, während Write, vor/nach physischem Commit,
  vor Main-Publication; Generation-, Action-, Permissionwechsel, Storage BLOCKED,
  UNKNOWN vor/nach Commit, DB-/Keycleanupfehler. COMMITTED wird nach Revoke nicht
  als Rollback umgedeutet; Callback bleibt unterdrückt.
- Explizite Rejection nach echten Sideeffects: ROLLED_BACK / COMPLETE erst nach
  zurückgekehrtem Rollback. Fehlgeschlagener Rollback: UNKNOWN und keine Antwort;
  uncommittierter SQL-Zustand separat beobachtet, Rollback erst im Testharness-Cleanup.
- Doppelte Worker-/Mainrunnables: eine Wirkung/Antwort. Lost/failed Main-Dispatch und
  failed Workerdispatch für alle drei APIs. Fremde Mutation zwischen alter Completion
  und Publication nimmt altem Ticket die Aktualität; alter Commit bleibt bestehen,
  aber keine alte Antwort. Keine automatische Wiederholung.
- PROTOCOL ↔ PROTOCOL/TASK/MATERIAL/EXPENSE/OPERATIONS und die jeweiligen Gegenrichtungen:
  gemeinsames Pending vor Queue und eine Mutationsepoche. Busy ist kein DB-Rollback.
  Andere Familien behalten ihren OperationsResult-Typ; Protokoll behält ProtocolResult.
- Bare ProtocolResult.Value ohne äußeren Commitbeleg: kein Erfolg. Runtime-RAM-Reuse
  durchgehend deaktiviert. Adaptertests prüfen dirty-Metadaten, Pending-Readsperre,
  UNKNOWN/Cleanup-Fail-closed und keinen automatischen Cleanstatus nach Commit.

Vertrauenswürdige Binding-/Permission-/Cleanup-/Room-Endsimulationen sind keine neue
Produktautorität und kein nativer SQLCipher-/Room-/Keycleanupbeweis. StorageContext
und SessionCoordinator bleiben maßgeblich; bestehende UI-Scopeannahme unverändert.
Gezielter Abschlusslauf: **90 Tests (84 Data, 6 App), 0 Fehler, 0 Errors, 0 Skips**.
Anfängliche neue Testhilfsfehler (Snapshotklammer/-typ, Host-Parameterbindung und
JUnit-Rückgabetyp) wurden korrigiert, ohne Assertions abzuschwächen.

### Read-/RAM-Grenzen und nur offline vorbereitete Androidtests

Summary, listTimeline und getEntry bleiben auf dem bisherigen Readpfad mit
withReadTransaction. Summary liefert weiterhin nur Zähler/Original-Erfassungszeit;
Timeline enthält bereits Titel und EntryDetail vollständigen sensiblen Inhalt.
Die existierenden Spaltenprojektionen/Navigation wurden nicht auf Writer umgestellt.
Der neue Rohpositionscheck verweigert beschädigte Daten; gesunde Readergebnisse
und vorhandene Historien-/Personenprüfungen bleiben erhalten.

Für die zukünftige Invalidierungsplanung ausschließlich Dirty-/Validity-Metadaten:
PROTOCOL_SUMMARY für ACTIVE-Hubzähler/-zeit, PROTOCOL_TIMELINE für die nach Revisionen
veränderten Timelinezeilen und PROTOCOL_DETAIL für die aktuelle Detail-/Historiensicht.
Diese Bezeichner erlauben **keine RAM-Retention ihres Inhalts**. Vollständige Titel/
Texte, Beschreibungen, Identifier, Personenlisten und Revisionshistorien gehören
weiter nicht in den retained ACTIVE-Kern. ActiveCoreStore/Produktnavigation unverändert,
keine Protokollpayloadregistry und keine neuen RAM-Hits. Der Adapter bleibt nicht
produktiv installiert. Bisherige Task-/Material-/Expenseimpacts unverändert;
übrige Corefamilien weiter konservativ alle Projektionsmetadaten.

CoreWriterBoundaryStorageTest erweitert um echtes ProductProtocolRepository und
denselben Port: Create mit nach Submit geleerten Quellcollections, Addendum,
MarkMistaken, STALE-Mark mit alter Revision und vollständige Detailnachlese.
Original unverändert, vier Protokollantworten; zusammen mit bestehenden Familien
19 gemeinsame Epochenschritte, 16 bestätigte Commits und drei bestätigte Rollbacks.
ProtocolStorageTest um Rohpositions- und fehlendes-Original-Regressionsfixture ergänzt;
bestehende Constructor-/API-Aufrufe kompatibel.

**Nur offline kompiliert, nicht ausgeführt.** Vor späterer gesonderter Gerätefreigabe
müssen Fixture-Isolierung und sichere geprüfte Verzeichnisbereinigung separat
abgenommen werden. Bestehende Fixture-finallys invalidieren Keyowner und leeren
Keybytes; daraus folgt keine bereits geprüfte vollständige Verzeichnisbereinigung.
Keine Installation/Instrumentation und keine Veränderung installierter Apps.

### NOCH NICHT ANGEBUNDEN / BLOCKER / ABA

- [#35](https://github.com/JulianGKK/action-planner/issues/35): Protokollwriter
  **IMPLEMENTIERT / GETESTET** im beschriebenen Wiring-/Hostumfang. MemoryReportWriter,
  DocumentWriter, Leadership/P3b, Maintenance/Recovery weiterhin
  **UNWIRED / NOT SAFE FOR RAM REUSE**. Keine globale Writer-/Cachefreigabe.
- [#36](https://github.com/JulianGKK/action-planner/issues/36): vollständiger
  Expected-Revision-CAS erhalten. Spezieller append-only-Vertrag verhindert das
  Wiederverwenden alter Revisionidentität bei Inhalts-A → B → A: getestet sogar bei
  gleichem recordedAt. Revision-ID/-nummer bleiben neu; alter Expected STALE.
  Kein allgemeiner ABA-Schutz für Person/Role/Plan/Task/Action/Operations/Expense
  aus Zeitstempeln oder UI-Tokens abgeleitet. Deren bekannte Grenzen bleiben offen;
  keine Schema-12- oder sonstige Revisionsmigration.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): produktiver
  Snapshotowner/atomare Payload-Publication **NOCH NICHT ANGEBUNDEN**.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine neue Navigation,
  Performance-/Cacheintegration oder Androidperformancefreigabe.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): eigenständiger
  Cursor-/Cleanup-**BLOCKER** unverändert. SQLCipherStatement.close() schließt den
  Cursor nicht explizit. Hoststatementabschluss, simulierte DB-/Leasecleanupfehler
  und frühere FD-PASSes beweisen keine vollständige native Cursorfreigabe.

Unverändert: SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink,
B2Driver, Schema 11, 180 s absolut, 30 s READY idle, 8+8-MiB-Sicherheitsbudget.
Keine Dependencies/Permissions/Persistenzorte, neue Sessionautorität oder zusätzlichen
Rechte. Keine Klartextpersistenz außerhalb der bestehenden DB, Produktnavigation,
RAM-Hits oder Snapshotaktivierung. Andere geprüfte Writer produktiv unverändert.
Kein Release, Produktupdate oder pauschale Release-1.0-/Editor-/P11-Snapshotfreigabe.


**Vollständiger Offline-Abschlusslauf: PASS.**

```text
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest \
  :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin \
  :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
```

**1.527 Hosttests bestanden:** App 670, Data 853, Security 4. **0 Fehler, 0 Errors,
0 Skips.** Domain ohne Testsources, nicht als weiterer Test gezählt. Bestehende
Task-/Action-/Person-/Rollenplan-/Operations-/Material-/Expense-/Protokoll-/Security-/
Publicationregressionen enthalten. App-Offline-Build, beide Androidtest-Kompilationen,
Architekturprüfung und Prototype-/Demo-Manifestchecks erfolgreich. Compilerwarnungen
(u. a. unnötige Non-null-Assertions in bestehenden/erweiterten Fixtures) sichtbar
geblieben. Keine Geräteausführung oder künstlicher nativer PASS.

Geändert: ProductProtocolRepository.kt, ProductRuntime.kt, ProtocolDatabase.kt
(nur zusätzliche Rohpositionsquery), CoreWriterData.kt,
WorkspaceMutationInvalidation.kt, ActiveWorkspaceMutationAdapter.kt,
ActiveWorkspaceValidity.kt (nur Projektionsbezeichner), HostCoreWriterData.kt,
neue HostProtocolDao.kt und HostProtocolConstraints.kt, neue
ProtocolWriterWiringTest.kt und ProtocolWriterIntegrationTest.kt,
ProtocolProjectionTest.kt, CoreWriterIntegrationTest.kt,
CoreWorkspaceMutationAdapterTest.kt, CoreWriterBoundaryStorageTest.kt,
ProtocolStorageTest.kt und dieser Abschnitt. Gemeinsame WriteBoundary/-Progress/
Transaktionsfunktion und andere produktive Writer funktional unverändert.

Abschluss: `git diff --check`, `git status -sb`, vollständige Diffprüfung, regulärer
WIP-Commit/Push auf den bestehenden Branch und unabhängiger GitHub-Ref-Abgleich.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen eingecheckt. Danach **STOPP BIS ZUM UNABHÄNGIGEN GITHUB-REVIEW**.
Kein Force-Push, Merge, Release oder Produktupdate.


## 56. MemoryReport-Writerintegration

### Ausgangsbasis und RED-Nachweise

Vor Änderungen: lokaler HEAD und unabhängiger GitHub-Branchref exakt
`3fb7f53d14111c5bc60fce20ae93ea79225593cc`, Branch
`p11-performance-optimization`, Arbeitsbaum sauber. Keine fremden Änderungen.

Vorheriger Pfad: MemoryReportRepository → ProductMemoryReportRepository → eigener
run-/write-Pfad mit withWriteTransaction → MemoryReportStore → Room/SQLCipher.
READY-/Schritt-/Commit-/Publicationgates waren vorhanden; gemeinsame Invalidierung
vor Workerqueue und expliziter äußerer Transaktions-/Cleanupbeleg fehlten.
**GETESTET – statischer RED-Wiringtest:** Zwei neue Methoden schlugen vor Integration
fehl (Boundary fehlt, Runtime injiziert gemeinsamen Port nicht). Nach Integration grün.
Statische Verdrahtungsprüfung ist kein SQLite-Transaktionsbeweis.

**GETESTET – Freeze-RED vor Härtung:** Der bestehende frozenWrite-Ausdruck wurde
semantisch unverändert in freezeMemorySection extrahiert. Vier Hosttests: Original-
MutableMap-/MutableSet-/Multi.keys-Mutation bereits korrekt isoliert (PASS), aber
Mutable-Casts von Antwortmap, mehrgliedrigem Konfliktset und verschachtelter
Multi.keys-Liste konnten Kopien verändern (drei tatsächliche Assertion-FAILs).
Nach unmodifiable Wrapping dieselben vier Tests grün, keine Assertion abgeschwächt.
Es wird ausdrücklich kein ursprünglicher Originalreferenz-Aliasverlust behauptet.

### IMPLEMENTIERT – eine gemeinsame Writerfamilie

WorkspaceWriterFamily.MEMORY_REPORT ergänzt. ProductRuntime injiziert dieselbe
workspaceMutations-Instanz wie an Action, Person, Operations, Task, Material, Expense
und Protocol. Keine zweite Mutationsepoche, Pending-Registry oder Sessionautorität.
Standalone-Constructordefault und letzter Clockparameter bleiben wie bei den übrigen
Familien kompatibel; produktive Composition injiziert ausdrücklich den gemeinsamen Port.

Alle vier Produktoperationen – create, saveSection, complete, addAddendum – verwenden:

```text
WorkspaceWriteBoundary.submit(MEMORY_REPORT, actionId)
  → aktueller READY-/OPERATION-Kontext
  → synchrones beforeCoreQueue-Ticket VOR worker.execute
  → bisheriger READY_TO_OPEN-/Keyscope
  → ActionDatabaseAccess mit bestehenden SQLCipher-/Room-Preflights
  → useWriterConnection / confirmedWorkspaceTransaction(IMMEDIATE)
  → MemoryReportStore und dieselben Room-DAOs
  → tatsächlicher äußerer Transaktionsabschluss
  → Rückkehr des vollständigen DB-/Key-Leasescopes
  → Ownerthread: Ticketcompletion
  → StorageContext.publish und ticket.isCurrent
  → Ergebnis-Callback
```

Gemeinsame Boundary, WorkspaceWriteProgress und confirmedWorkspaceTransaction
funktional unverändert. Store und Datenport öffnen keine zweite äußere Transaktion.
Aktiver äußerer Transaktionsscope/SAVEPOINT-Erfolg genügt nicht als äußerer Commitbeleg.
Ein OperationsResult.Value allein genügt nicht als Erfolgsevidence.

| Zustand | Beleg / Publication |
|---|---|
| Äußerer Commit normal zurückgekehrt, kompletter Workscope normal zurückgekehrt | COMMITTED / COMPLETE; Erfolg nur bei weiterhin gültiger zentraler Publication und aktuellem Ticket |
| Fachliche Rejection und expliziter äußerer Rollback normal zurückgekehrt, Cleanup bestätigt | ROLLED_BACK / COMPLETE; erst dann fachliche Ablehnung veröffentlichen |
| Unklarer Commit-/Rollbackabschluss oder Exception ohne positiven Endbeleg | UNKNOWN; kein Erfolgs- oder fachlicher Rejectioncallback |
| Commit bekannt, anschließender DB-/Keyscope nicht bestätigt zurückgekehrt | COMMITTED / FAILED_OR_UNCONFIRMED; kein Erfolg |
| Revoke nach bekanntem physischem Commit | Commit bleibt COMMITTED; keine unberechtigte Antwort und kein erfundener Rollback |
| Bereits Pending vor Queueeintritt | Vor-Queue-STALE-Busy-Ablehnung ohne DB-Transaktion; kein ausgeführter Rollbacknachweis |
| Workerdispatch vor Start fehlgeschlagen | Bestehender Nichtausführungsbeleg ROLLED_BACK / COMPLETE plus Fehlerpfad; kein behaupteter tatsächlicher DB-Rollback |
| Main-Dispatch verloren/fehlgeschlagen | Pending bleibt bestehen; keine Completionpublication, kein automatischer Retry |

UNKNOWN/unbestätigtes Cleanup bleiben für dieselbe Generation fail-closed. Vorhandenes
Storage-Failure-/BLOCKED-Verhalten maßgeblich; keine unberechtigte Fehlerpublication bei
bereits widerrufenem Kontext. Keine optimistischen UI-Patches oder automatische
Wiederholung eines möglicherweise physisch bereits committeten Schreibvorgangs.

### IMPLEMENTIERT / GETESTET – Fragebogen-, Header- und Addendumatomarität

Die fachliche Storelogik wurde ausschließlich mechanisch von ActionDatabase-Zugriff
auf CoreWriterData.memoryReports/actions umgestellt. Alle bisherigen Regeln bleiben:

- Create validiert Autor und vollständigen beabsichtigten Abschnitt 0 vor Schreiben;
  Header und sämtliche ersten Antworten gehören zu derselben äußeren Transaktion.
  Leerer Abschnitt ist weiterhin eine fachlich zulässige bewusste Speicherung.
- SaveSection prüft vollständigen Expected-Header und tatsächlichen Detailzustand
  einschließlich Answercodec/Addenda. Abschnitt löschen, sämtliche Ersatzantworten
  schreiben und Headerrevision aktualisieren sind atomar; andere Abschnitte bleiben.
- Complete setzt tatsächlichen DRAFT-Status und gespeicherten Abschnitt 16 voraus.
  Status, Revision, updatedAt/completedAt werden gemeinsam fortgeschrieben.
- AddAddendum setzt tatsächlichen COMPLETED-Status voraus. Vollständige Addendum-
  Sequenz lesen/prüfen, neue Ergänzung einfügen und Headerrevision aktualisieren in
  derselben äußeren Transaktion. Kein Addendum ohne Revisionsupdate oder umgekehrt.

Expected-CAS bleibt vollständige MemoryReportHeader-Gleichheit: ID über actionlokalen
Lookup, Action-ID, authorLabel, questionnaireVersion, Status, revision,
lastSavedSectionOrdinal, createdAt, updatedAt, completedAt. Vergleich und Änderung
in derselben IMMEDIATE-Transaktion. Alter Editor nach bestätigtem Save oder Addendum
STALE; erster Stand bleibt erhalten, neuer gültiger Editor kann fortsetzen.
Keine ID-only-Abkürzung und kein UI-Revisionsersatz. Header ist selbst vollständig
immutable; eine zusätzliche Expected-Collectionkopie ist dort nicht erforderlich.

Persistierte Headerrevision bleibt monoton im vorhandenen Writervertrag.
Long.MAX_VALUE erzeugt wie bisher ProductStorageFailure statt Wrap; bevor
SaveSection/Complete/Addendum mutieren. Bei Exception kein erfundener fachlicher
Rollbackbeleg: UNKNOWN, tatsächlicher SQL-Altzustand bleibt nach Rollback erhalten.
Keine Schemamigration oder neue Revisionsspalte.

MemoryQuestionnaireV1, MemoryReportRules und MemoryAnswerCodec.encode/decode/normalize
unverändert: Reihenfolge 0..16 und maySave/mayComplete, Question-/Sectionzuordnung,
Optionkeys, MULTI_CHOICE, kanonische Reihenfolge, exklusive UNKNOWN/UNCLEAR/
NO_ASSESSMENT, Text-/Countgrenzen und Autorenlabel. Inaktiv gewordene Fragen verlangen
weiter exakt passende retainedConflicts oder bewusste Entfernung; keine stillschweigende
Bereinigung gespeicherter Antworten. Addendumlänge, Trim und Sequenzordnung unverändert.
Schreiben weiterhin ausschließlich ACTIVE/COMPLETED gemäß bestehenden Fachregeln;
keine neuen Rechte/P3b-Reads und keine Reöffnung anderer Writer in COMPLETED.

### IMPLEMENTIERT / GETESTET – unveränderlicher Submit und minimale SQLite-Seam

freezeMemorySection bildet vor Queueeintritt unabhängige unmodifiable Antwortmap,
Konfliktset und für jedes MemoryAnswerValue.Multi eine eigene unmodifiable Keyliste.
Andere Blätter (Text/Option/Count), Strings und Headerfelder sind immutable Werte.
Tests zeigen Originalreferenzisolation und verweigerte Collection-Casts. Gequeueute
Create- und SaveSection-Aufträge behalten den eingereichten Stand auch nach Leeren
aller ursprünglichen Maps/Sets/Keylisten. Keine persistierten Formularentwürfe,
zusätzlichen Payloadowner oder neuen Lebensdauern sensibler Produktdaten.

CoreWriterData gezielt um MemoryReportDao erweitert; Produktion verwendet dieselben
Room-DAOs. Neuer HostMemoryReportDao implementiert sämtliche verwendeten Query-/
Writepfade mit echtem Schema-11-SQLite, FKs und Indizes: Header, Summary, Liste,
Answers, Addenda, invalidHeaders/invalidAddendumTypes, Insert, clearSection und Update.
Keine Testseam als zweite Transaktions-/Sessionautorität, keine Fakeantworten als
Persistenzbeweis. Hostnumerik bildet native getLong-/Intkonvertierung nach, sodass
wirklich die vorhandenen Rohwertgates und nicht ein strengerer Hostparser schützen.

**GETESTET – vorhandene Rohdatenprüfungen genügen für die konkret untersuchten
Intfelder:** questionnaireVersion != 1 bzw. nicht integer und lastSavedSectionOrdinal
außerhalb 0..16 werden vor Headermaterialisierung abgewiesen; unter anderem Werte
4294967297, ±4294967296 und REAL-/TEXT-Werte. Addendumsequence wird vor Int-Cast auf
integer, >=0 und <=2147483647 geprüft, anschließend auf lückenlose Sequenz 0..N-1.
REAL/TEXT, große/negative Wrapwerte und normale Sequenzlücken verweigert. COUNT-Payload
wird strikt über toIntOrNull und kanonischen Codec geprüft. Kein neuer konkreter
Wrap-Bypass dieser Gates nachgewiesen; deshalb **keine DAO-/Treiber-/Schemahärtung auf
Verdacht**. MemoryReportDatabase/DAO-Queries unverändert. Das ist keine pauschale
Aussage über ungetestete extreme Zeilenanzahlen oder sämtliche anderen DB-Modelle.

Summary/List bleiben bewusst Headerreads. Fehlerhafte Antwortpayloads machen Detail
und Folgewrites unzulässig, werden aber durch eine reine Zählsummary weder gelesen
noch als vollständig geprüft ausgegeben. Header-/Addendum-/Answerchecks und deren
bestehende Datenintegritätsgrenzen wurden nicht abgeschwächt.

### GETESTET – echte SQLite- und Outcome-Regressionsmatrix

Neuer MemoryReportWriterIntegrationTest nutzt denselben bestehenden Corewriter-
Harness, Store, Boundary und bestätigte äußere Transaktionsfunktion. In-memory
SQLite aus exportiertem Schema 11, reale SQL-Zustände und FKs. Cross-Family-Protokoll
nutzt auch seine produktiven Trigger. afterChange injiziert gezielt Fehler **nach**
realen SQL-Sideeffects; zusätzliche echte SQLite-Fehlertrigger prüfen DB-Abbrüche.

Nachweise:

- Create mit vollständigem erstem Antwortsatz, SaveSection mit mehreren Antworten,
  zulässige Abschnittswiederholung, Erhalt anderer Abschnitte, Autoränderung nur in
  Abschnitt 0. Fortschritt darf keinen Abschnitt überspringen; bewusst leere
  Abschnitte zählen weiterhin als gespeichert.
- Abhängige Frage wird inaktiv: fehlende/falsche/überzählige retainedConflicts INVALID,
  bewusster passender Erhalt oder explizite Entfernung erfolgreich.
- Ungültige Question-/Sectionkeys, Optionkombinationen, doppelte Multi-Keys, unbekannte
  Optionen, UNCLEAR/UNKNOWN/NO_ASSESSMENT zusammen mit anderen Werten, Count außerhalb
  0..99999, Autor-/Textgrenzen: fachliche Rollbacks. Kanonische Multi-Reihenfolge und
  gültige Countgrenzen 0/99999 gespeichert und gelesen.
- Vollständiger Header-CAS, zwei alte Editoren, STALE nach Revisionsfortschritt und
  zwei Ergänzungseditoren. Neuer Expected darf fortsetzen; alte Ergänzung erzeugt
  weder weiteren Addendumdatensatz noch Headerupdate. Fremde/fehlende Aktion/Report
  sowie reale Child-FKs/Cascade ohne Fremdänderung geprüft.
- Complete vor 16 verweigert, nach 16 erfolgreich; Save nach COMPLETED verweigert,
  Addendum vor COMPLETED verweigert, danach erfolgreich. Vollständige Addendumsequenz
  bleibt auch bei gleichen/rückläufigen Erfassungszeiten korrekt.
- Fehler nach Header-INSERT, nach clearSection, nach erstem/zweitem Answer-INSERT,
  nach Header-UPDATE und nach Addendum-INSERT: vollständiger SQL-Altzustand aller drei
  Tabellen wiederhergestellt. Failed Create ohne Header-/Antwortrest. Headerupdate-
  Fehler nach Answer-/Addendumwrite ohne halbe Zustände. Keine manuelle Teil-Recovery.
- Korrupte Header, Answers (einschließlich kanonischer MULTI-/COUNT-Fehler und
  Question außerhalb gespeicherter Abschnitte) und Addenda: Detail/Folgewrites
  fail-closed ohne Mutation. Vorhandene invalidHeaders-/invalidAddendumTypes-Gates
  ausdrücklich vor unsicherer Materialisierung beobachtet.
- Für **alle vier Writes**: Lock vor Worker, Revoke während Write, vor Commit,
  nach physischem Commit, vor Publication; Action-, Generation-, Permissionwechsel,
  Storage-not-ready/BLOCKED-Vertrag, UNKNOWN vor/nach Commit, DB-/Leasecleanupfehler.
  Physischer SQL-Zustand, Completionevidence und sichtbarer Callback getrennt.
- Für alle vier Writes: doppelter Worker/Main, verspäteter Callback, fehlgeschlagener
  Workerdispatch, verlorener/fehlgeschlagener Main-Dispatch. Neuere Materialmutation
  zwischen alter Completion und Publication unterdrückt alte Antwort, ohne alten
  Commit als Rollback zu erfinden. Kein automatischer Retry.
- Explizite Rejection nach tatsächlich erfolgten SQL-Änderungen: ROLLED_BACK/COMPLETE
  erst nach zurückgekehrtem Rollback. Fehlgeschlagener Rollback UNKNOWN: noch
  uncommittierter Zustand separat sichtbar; abschließender Harnessrollback ist
  kein Produktcommit oder Erfolgsbeleg.
- MEMORY_REPORT ↔ TASK/OPERATIONS/MATERIAL/EXPENSE/PROTOCOL und dieselbe Familie:
  eine Pending-Grenze und gemeinsame Mutationsepoche vor Queue. Weitere Aufträge
  Busy ohne DB-Transaktion. Andere Ergebnistypen, insbesondere ProtocolResult,
  bleiben erhalten. Bare OperationsResult.Value ohne Commitbeleg erzeugt keinen Erfolg.

Testbindings für Scope/Permission, Room-End- und DB-/Keycleanupfehler sind explizite
vertrauenswürdige Simulationen. Produktiv bleiben StorageContext und bestehende
Sessionautorität maßgeblich. Hosttests sind keine nativen Leak-/Cleanupbelege.
Gezielter Abschlusslauf: **91 Tests (84 Data, 7 App), 0 Fehler, 0 Errors, 0 Skips**.
Die RED-Fehler wurden nicht durch abgeschwächte Assertions oder Skips verborgen.

### Sensitive Read-/RAM-Grenze und Androidvorbereitung

summary/list/detail bleiben auf dem bisherigen kurzlebigen withReadTransaction-Pfad.
Summary ausschließlich draftCount/completedCount; Headerlisten enthalten Autorenlabels,
Detail vertrauliche Antworten und Addenda. Bestehendes MemoryReportModel und Navigation
unverändert; es übernimmt Ergebnis/Feedback erst im vorhandenen Scope-/Requestgate.
Dieser Slice behauptet keine zusätzliche allgemeine Editor-Lifecycle-/Livenessfreigabe.

MEMORY_REPORT_SUMMARY wird im weiterhin nicht produktiv installierten Adapter als
Dirty-/Validity-Metadatum geführt: ACTIVE-Hubzähler. MEMORY_REPORT_HEADERS ist wegen
der bestehenden Listenreads nötig (Create, Autor-/Status-/Revisionsänderung);
MEMORY_REPORT_DETAIL wegen Antworten/Addenda/Headerrevision. Diese Bezeichner sind
**keine Retentionsfreigabe**: weder Autorenlabels noch Antworttexte, Addenda oder
vollständige Reports gehören in den retained ACTIVE-Kern. ActiveCoreStore unverändert,
keine neue Payloadregistry und keine RAM-Hits. Bisherige Familienimpacts erhalten;
übrige konservative Corefamilien umfassen auch die neuen Metadaten. Pending blockiert
Reuse, Commit markiert nichts automatisch clean, UNKNOWN/Cleanup bleiben fail-closed.
Runtime bleibt UncachedWorkspaceMutations, ramReuseEnabled=false, Budget unverändert.

CoreWriterBoundaryStorageTest um reales ProductMemoryReportRepository und denselben
Port erweitert. Create mit nach Submit geleerter Antwortmap, SaveSection mit nach
Submit geleerten Multi-Keys, sämtliche Abschnitte bis 16, Complete, Addendum und
STALE-Addendum mit altem Header. Detailnachlese prüft ursprünglichen Abschnitt,
kanonische Multi-Antwort, COMPLETED/revision=18 und genau eine Ergänzung.
20 MemoryReportantworten (19 Commits, ein bestätigter Rollback); mit den bestehenden
Familien insgesamt 39 gemeinsame Epochenschritte, 35 Commits, vier Rollbacks.
Doppelte Worker-Runnables dürfen nichts duplizieren. Bestehende
MemoryReportStorageTest-API-/Constructoraufrufe bleiben kompatibel.

**Nur offline vorbereitet/kompiliert, nicht ausgeführt.** Vor späterer gesonderter
Geräteprüfung müssen Fixture-Isolierung, enger Testselektor, sichere geprüfte
Verzeichnisbereinigung und native Ressourcenfrage #32 separat abgenommen werden.
Keyinvalidate/-fill in vorhandenen Finally-Blöcken ist keine bereits geprüfte
vollständige Verzeichnisbereinigung. Keine Installation/Instrumentation und keine
Änderung installierter Apps.

### NOCH NICHT ANGEBUNDEN / BLOCKER / Revision

- [#35](https://github.com/JulianGKK/action-planner/issues/35): vier MemoryReportwrites
  **IMPLEMENTIERT / GETESTET** im beschriebenen Host-/Wiringumfang. DocumentWriter,
  Leadership/P3b, Maintenance/Recovery weiterhin **UNWIRED / NOT SAFE FOR RAM REUSE**.
  Keine globale Writer-/Cachefreigabe oder Document-/Recovery-Refaktorierung.
- [#36](https://github.com/JulianGKK/action-planner/issues/36): vorhandener persistierter
  Headerrevisionsvertrag erhalten. Antwort-A → B → A bei identischem updatedAt
  getestet: Headerrevision bleibt erhöht, alter Expected STALE. Dies ist ein spezieller
  MemoryReport-Writervertrag und keine allgemeine ABA-Sicherheit anderer Modelle;
  deren bekannte Grenzen unverändert. Keine Schema-/Revisionsmigration.
- [#37](https://github.com/JulianGKK/action-planner/issues/37): produktiver Snapshotowner
  und atomare Payload-Publication **NOCH NICHT ANGEBUNDEN**.
- [#38](https://github.com/JulianGKK/action-planner/issues/38): keine neue Navigation,
  Performanceintegration, Cacheaktivierung oder Androidperformancefreigabe.
- [#32](https://github.com/JulianGKK/action-planner/issues/32): eigenständiger nativer
  Cursor-/Cleanup-**BLOCKER**. SQLCipherStatement.close() schließt den Cursor nicht
  explizit. Hoststatementabschluss, simulierte DB-/Leasecleanupfehler und frühere
  FD-PASSes beweisen keine vollständige native Cursorfreigabe.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink, B2Driver,
Schema 11, 180 s absolut/30 s READY idle und 8+8-MiB-Sicherheitsbudget unverändert.
Keine neuen Dependencies, Permissions, Persistenzorte oder Sessionautorität, keine
Klartextpersistenz außerhalb bestehender DB-Verträge, produktiven RAM-Hits oder
Snapshotaktivierung. Andere geprüfte Writer produktiv unverändert. Keine allgemeine
Release-1.0-/Editor-/P11-Snapshotfreigabe, kein Release oder Produktupdate.


**Vollständiger Offline-Abschlusslauf: PASS.**

```text
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest \
  :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin \
  :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
```

**1.561 Hosttests bestanden:** App 671, Data 886, Security 4. **0 Fehler, 0 Errors,
0 Skips.** Domain ohne Testsources, nicht als weiterer Test gezählt. Bestehende
Person-/Rollenplan-/Operations-/Task-/Material-/Expense-/Protocol-/MemoryReport-/
Security-/Publicationregressionen enthalten. App-Offline-Build, beide Androidtest-
Kompilationen, Architekturprüfung und Prototype-/Demo-Manifestchecks erfolgreich.
Vorhandene Compilerwarnungen in bestehenden/erweiterten Fixtures sichtbar geblieben.
Keine künstliche native PASS-Meldung; Androidtests ausschließlich kompiliert.

Geändert: ProductMemoryReportRepository.kt, ProductRuntime.kt, CoreWriterData.kt,
WorkspaceMutationInvalidation.kt, ActiveWorkspaceMutationAdapter.kt,
ActiveWorkspaceValidity.kt (nur zusätzliche Projektionsbezeichner), HostCoreWriterData.kt,
neue HostMemoryReportDao.kt, MemoryReportWriterWiringTest.kt,
MemoryReportWriterIntegrationTest.kt und MemorySectionFreezeTest.kt,
CoreWriterIntegrationTest.kt, CoreWorkspaceMutationAdapterTest.kt,
CoreWriterBoundaryStorageTest.kt und dieser Abschnitt. MemoryReportDatabase/DAO,
MemoryReportRules/Codec/Questionnaire, gemeinsamer WriteBoundary/-Progress-/
Transaktionsvertrag und andere produktive Writer funktional unverändert.

Abschluss: `git diff --check`, `git status -sb`, vollständige Diffprüfung, regulärer
WIP-Commit/Push auf den bestehenden Branch und unabhängiger GitHub-Ref-Abgleich.
Keine APKs, Schlüssel, Datenbanken, Rohlogs, Credentials, persönlichen Pfade oder
Gerätekennungen eingecheckt. Danach **STOPP BIS ZUM UNABHÄNGIGEN GITHUB-REVIEW**.
Kein Force-Push, Merge, Release oder Produktupdate.

## 57. Dokument-/Recovery-Mutationsinventar

Issue #35 – unabhängiger Inventar-/Kontrakttest-Slice auf Basis
`86a8b5b495ef7299112e089d16c741b1f26d6ca2`.
Branch und lokaler HEAD entsprachen vor Änderungen dem unabhängig per GitHub-Ref
abgefragten Remote-HEAD; Arbeitsbaum und Diff waren leer.
**BESTEHEND / UNWIRED:** Der Dokumentpfad ist weiterhin nicht an
`WorkspaceWriteBoundary` oder die gemeinsame Mutationsepoche angeschlossen.
Dieser Slice verändert ausschließlich Hosttests und dieses Assessment.
Kein konzeptioneller Vertrag ist eine implementierte Integration.

### 57.1 Bestehender Ablauf und Grenzen der Belege

`DocumentRepository → ProductDocumentRepository → DocumentProductDispatch →
DocumentImportStaging / DocumentPublication / DocumentDelete → jeweilige
SqlCipherDocument…Database → DocumentDao / Room / SQLCipher`.
Der Runtime-Documentanschluss erhält keinen `workspaceMutations`-Port.
Import-Reserve, Phasenwechsel, finale Veröffentlichung sowie Delete-Reserve,
Parent-Detach und Jobabschluss verwenden jeweils eigene kurze
`withWriteTransaction`-Scopes. DAO-`@Transaction`-Aufrufe innerhalb dieser Scopes
sind kein zusätzlicher unabhängig dauerhafter Commit. Datei-I/O liegt außerhalb
dieser DB-/Key-Leases. Es gibt keinen globalen Import-/Delete-Rollback.

**BESTEHEND:** READY, aktuelle Operationsberechtigung, persistierter Actionstatus,
Storage-Preflights und bestehender Keyowner autorisieren Produktoperationen.
COMMIT-Gates prüfen Berechtigung, belegen aber allein keinen zurückgekehrten
physischen Commit. Ein erfolgreich zurückgekehrter DB-/Keyscope ist die
bestehende lokale Abschlussgrenze; der Dokumentpfad liefert bislang keinen
expliziten gemeinsamen `WorkspaceWriteCompletion`-Beleg. Eine Exception kann
nach einem vorherigen dauerhaften Schritt eintreten. Weder ein
`DocumentResult.Value` noch ein Jobname beweist fsync oder vollständiges Recovery.

**HOSTGETESTET:** Die Phasentests verwenden echte temporäre Hostdateien und Tink,
die tatsächliche DAO-Validierungslogik und isolierte Fake-DBs mit simuliertem
Rollback. Die Dispatchtests benutzen einen ausdrücklich simulierten dauerhaften
Ledger. Das ist kein neuer SQLite-/Room-/SQLCipher-Commit-, Powerloss- oder nativer
Cleanupnachweis. Die Produkt-DB-Adapter wurden am Code geprüft; Android-Fixtures
wurden ausschließlich offline kompiliert.

### 57.2 Phasen-/Seiteneffektmatrix

Die Spalte DB beschreibt getrennte Schritte bei erfolgreicher Rückkehr des
jeweiligen DB-Scopes. Bei unbekanntem Rückkehr-/Cleanupzustand muss ein frischer
autorisierter Inventarread den tatsächlichen Zustand feststellen. „Datei vorhanden“
beweist ohne zurückgekehrten Sync keine Crash-Dauerhaftigkeit.

| Phase | Vorher → nachher DB | Tatsächliches Dateisystem / Ressourcen | Autorisierte Fortsetzung, Recovery und UI |
| --- | --- | --- | --- |
| A Import reservieren | kein Job → IMPORT_RESERVED; noch kein Document | noch kein Namespacezugriff; neuer Dateischlüssel wird scoped erzeugt und als Jobkopie gespeichert | READY; Ablehnung vor Insert erzeugt keine Datei; nach Reserve kein UI-Erfolg |
| B WRITING markieren | IMPORT_RESERVED → IMPORT_WRITING | weiterhin keine Datei erforderlich; DB-/Keyscope endet vor I/O | READY; Revoke danach lässt WRITING stehen; MAINTENANCE erforderlich |
| C Staging erstellen | IMPORT_WRITING bleibt | bekannte leere Struktur ggf. initialisieren; CREATE_NEW/WRITE/NOFOLLOW_LINKS öffnet genau ein neues Leaf | READY und exaktes Inventar; keine Überschreib-/Unlink-Kompensation; Fehler lässt Job und ggf. Teilroot/Datei |
| D Verschlüsselt schreiben | IMPORT_WRITING bleibt | eine Sourceöffnung, Tink-Stream, begrenzter Buffer, AAD an documentId; keine Klartextdatei | STEP-Gates; Abbruch erhält WRITING und mögliche Teilcipherdatei; kein Retry |
| E Schließen / File-Sync | IMPORT_WRITING bleibt | Streamfinalisierung, flush, FileChannel.force(true), dann Channel.close; use/finally und Schlüssel-/Bufferzeroisierung | Sync-/Closefehler verhindert CLOSED; physische Finalisierung nach Revoke erteilt keine Publication |
| F CLOSED markieren | IMPORT_WRITING → IMPORT_CLOSED samt tatsächlicher Größe | geschlossene, synchronisierte Stagingdatei; Inventar vor DB-Schritt erneut exakt geprüft | READY; fehlgeschlagener Schritt kann WRITING lassen; Revoke nach Rückkehr lässt CLOSED; kein UI-Erfolg |
| G Rename | IMPORT_CLOSED bleibt | authentifiziertes EOF/Größe/Format prüfen; atomic no-replace Rename staging → files; Inode/Größe/mtime prüfen | READY oder MAINTENANCE; Rename ist kein DB-Commit; CLOSED mit finaler Datei ist Recoveryzustand |
| H Verzeichnisse synchronisieren | IMPORT_CLOSED bleibt | files, dann staging fsync; Inventar erneut prüfen | Fehler auch beim zweiten Sync lässt CLOSED mit finalem Blob; nur MAINTENANCE nimmt diesen Zustand wieder auf |
| I FILE_PUBLISHED markieren | IMPORT_CLOSED → FILE_PUBLISHED | finaler Blob vorhanden, beide Sync-Aufrufe vorher zurückgekehrt | DB-Schritt unabhängig; anschließender Fehler macht Rename und vorherige Phase nicht rückgängig |
| J Document veröffentlichen | FILE_PUBLISHED → DocumentRow eingefügt UND Job entfernt in einer DB-Transaktion | finaler Blob erneut vollständig authentifiziert; Snapshot-Schlüssel werden geschlossen/zeroisiert | erst nach vollständiger Rückkehr und gültiger zentraler Main-Publication möglicher UI-Erfolg; Revoke nach Commit erfindet keinen Rollback |
| K Delete reservieren | Document → Document + DELETE_RESERVED | Blob bleibt; Job enthält keinen Schlüssel/Namen/Formatpayload | READY; vollständiger Expected-Document-Vergleich; Job blendet Parent bereits aus count/list/get aus; noch kein UI-Erfolg |
| L Parent entfernen | DELETE_RESERVED → DELETE_FILE_PENDING UND Parent entfernt, atomarer lokaler DB-Schritt | Blob bleibt; kein DB-/Keylease über Unlink | READY oder MAINTENANCE; späterer Fehler stellt Parent nicht wieder her |
| M Unlink | DELETE_FILE_PENDING bleibt | exakt gebundener bekannter Blob wird entfernt; vor Unlink frischer DB-Snapshot und Inventar | fehlender Blob nur bei PENDING akzeptiert; fremde Dateien/Symlinks blockieren; kein manuelles Cleanup |
| N Verzeichnis synchronisieren | DELETE_FILE_PENDING bleibt | files fsync auch bei bereits fehlendem Blob | Fehler lässt PENDING zur Recovery; erneute autorisierte Recovery synchronisiert ohne zweites Unlink |
| O Deletejob abschließen | DELETE_FILE_PENDING → kein Job; Parent bereits weg | Inventar bestätigt Abwesenheit, Sync zurückgekehrt | eigener DB-Schritt; Revoke danach verhindert UI-Erfolg, ändert physischen Abschluss nicht |
| P Import-Recovery | RESERVED abbrechen; WRITING nach Dateientfernung abbrechen; CLOSED/FILE_PUBLISHED über Publication fortsetzen | vollständiges globales Inventar VOR erster Mutation; WRITING-Staging unlink + Sync vor Jobentfernung; CLOSED genau staging XOR final | ausschließlich MAINTENANCE; CLOSED-final wird ohne zweiten Rename erneut geprüft/synchronisiert; unbekannte Artefakte bleiben unangetastet; kein Source-Reopen |
| Q Delete-Recovery | RESERVED über Detach → PENDING → Jobabschluss; PENDING direkt fortsetzen | Reserve benötigt Parent+Blob; PENDING akzeptiert fehlenden Blob und synchronisiert | ausschließlich MAINTENANCE; finales Inventar muss jobs/staging leer und files exakt documents bestätigen; keine direkte Dokument-Erfolgs-UI |

**BLOCKER:** Zwischen B und C ist WRITING ohne Root erreichbar. Recovery verweigert
diesen Zustand derzeit, siehe 57.5. Auch ein partiell initialisierter Root wird
fail-closed erhalten, nicht still rekonstruiert. Prozessabbruch kann diese
Zwischenstände hinterlassen; Host-Exceptions simulieren Unterbrechungsstellen,
keinen Stromausfall oder Kernel-Dauerhaftigkeitsbeweis.

### 57.3 Namespace-Lane, Session und Recovery

**BESTEHEND:** `DocumentProductDispatch.run` erwirbt READY und nimmt
`DocumentNamespaceLane.ticket()` **vor** `worker.execute`. Erst im Worker folgt
`exclusive(ticket)`, dann OPERATION-Gate und Arbeit. Erste DB-Mutation ist beim
Import reserve, beim Delete reserveDelete (nach kurzlebigem Expected-Read).
Dateisystemmutation folgt erst nach zurückgekehrtem WRITING bzw. Delete-Detach.
Der Namespace-Monitor serialisiert Dokumentarbeit/Reader und Recovery, ist aber
weder Sessionautorität noch gemeinsame Mutationsepoche. Er serialisiert nicht
Task-, Operations-, Material-, Expense- oder Protocolwriter.

`drainAndFence` wartet auf laufende Lane-Arbeit und erhöht die lokale Epoche,
sodass ältere noch nicht gestartete Tickets scheitern. Revoke kann an jeder
STEP-/COMMIT-/Publicationgrenze wirksam werden; es rollt frühere getrennte
Commits oder Dateioperationen nicht zurück. Nach Lane-Freigabe wird Main
beauftragt und `StorageContext.publish` geprüft. Die Lane schützt diesen bereits
eingereihten Maincallback nicht erneut, siehe RED-3.

`reconcileProductDocuments` läuft im vorhandenen autorisierten
MAINTENANCE-/Unwrap-Keyscope, durch `documentMaintenanceBarrier` vor READY-Owner-
Vorbereitung. `withDocumentMaintenanceKey` bindet den synchron geborgten Schlüssel
an denselben Context und Thread; die Delete-DB-Seam kopiert/zeroisiert ihre
Einzelschritt-Lease. Recovery drain/fenced dieselbe Lane und validiert ALLE
DB-/Dateibindungen vor Cleanup sowie danach ein frisches finales Inventar.
Ungültiger Scope, Revoke, unbekannte Dateien, fehlende fertige Blobs oder
widersprüchliche Jobs sperren READY. Ein Job allein ist kein Recoveryabschluss.

### 57.4 Vorgeschlagener Evidence- und Invalidierungsvertrag

**VORGESCHLAGEN / UNWIRED, noch zu reviewen:** Ein Dokumentauftrag braucht einen
phasenbezogenen, key-/pfad-/payloadfreien Beleg, getrennt nach:

1. DB-Schritt: nicht gestartet / bestätigt zurückgerollt / bestätigt committet /
   unbekannt, jeweils an Operation und erwartete persistierte Phase gebunden.
2. Dateischritt: nicht gestartet / bestätigt ausgeführt / Sync bestätigt /
   Abschluss unbekannt; Rename und Unlink jeweils getrennt von Verzeichnis-fsync.
3. Namespace: Zeitpunkt und geprüfte Job-/Dokumentbindungen des Inventars;
   Ergebnis ist keine zeitlich unbegrenzte Gültigkeitszusage.
4. Ressourcen: DB-/Keylease, Channels/Streams und Snapshots jeweils vollständig
   geschlossen oder fehlgeschlagen/unbestätigt.
5. Recovery: nicht nötig / bestimmter persistierter Job erneut zu inventarisieren /
   widersprüchlicher Zustand BLOCKED; kein automatischer blinder Retry.
6. UI: noch nicht angeboten / zentral autorisiert veröffentlicht / verweigert /
   Dispatch unbekannt. Ein physischer Commit bleibt auch ohne UI-Erfolg ein Commit.

Nichtausführung vor Mutation und Busy-Rejection sind keine ausgeführten
DB-Rollbacks. Ein späterer Dateifehler macht einen früheren WRITING-Commit nicht
zu ROLLED_BACK. `WorkspaceWriteOutcome` und `WorkspaceWriteCompletion` bleiben
unverändert; ein einzelnes Completion darf nicht den mehrstufigen Auftrag als
globalen Commit bescheinigen. Den gesamten Import in
`confirmedWorkspaceTransaction` zu wickeln wäre falsch.

Spätere enge Integration: auf dem bestehenden Runtime-Ownerthread vor
Worker-Queueeintritt denselben Invalidierungsport um einen explizit geprüften
mehrphasigen Dokumentvertrag erweitern. Betroffen sind tatsächlich Document-
Summary/Anzahl im Workspace und Dokumentliste; Viewer erhalten weiter ihre
bestehenden Expected-/Session-/Dateibindungen. Nur Dirty-/Validity-Metadaten,
keine Dokumenttexte, Blobs oder Schlüssel im ACTIVE-Kern. Bestehende
Namespace-Lane zusätzlich beibehalten; gemeinsame Pending-Grenze und Lane haben
verschiedene Aufgaben. Recovery muss dieselben Metadaten vor ihrer Queue bzw.
vor READY-Freigabe fail-closed halten. Bei unbekanntem Phasen-/Cleanupabschluss
keine alte Projektion wieder freigeben. Erst nach überprüftem Endinventar,
Ressourcenabschluss und aktuellem zentralem Scope mögliche UI-Publication.
Kein zweiter Key-/Sessionowner und keine unabhängige Epoche. Das heutige
`Pending.finish(WorkspaceWriteCompletion)` kann diese mehrphasige Evidence nicht
verlustfrei ausdrücken; die Portentscheidung benötigt gesondertes Review.
`ProductRuntime` bleibt bei `UncachedWorkspaceMutations`, `ramReuseEnabled=false`.

### 57.5 Neue Hostregressionen und tatsächliche RED-Befunde

**HOSTGETESTET:** Neuer `DocumentRecoveryDispatchContractTest` (12 Fälle) trennt
simulierten dauerhaften Zustand, Workerwirkung und Callback. Vier bestehende
Testdateien wurden um zwölf Phasen-/Recoveryfälle erweitert. Insgesamt 24 neue
Tests. Bestehende Assertions wurden nicht abgeschwächt; keine Skips eingeführt.

Neue Phasentests prüfen Revoke vor Reserve, nach zurückgekehrter Reserve,
nach WRITING vor Namespacezugriff und nach CLOSED; zweiten Directory-Syncfehler,
Fehler nach FILE_PUBLISHED samt Snapshot-Zeroisierung; Revoke nach Delete-Reserve,
Unterbrechung nach Parent-Detach mit späterer expliziter Recovery; ungültige
Maintenance, WRITING-Unlink gefolgt von Syncfehler und anschließend frischer
Recovery, fehlgeschlagenen finalen Recovery-DB-Schritt sowie WRITING ohne Root.

Bestehende Suites decken zusätzlich Source-/Write-/File-Sync-/Closefehler,
Rename-Verweigerung/Kollision, FILE_PUBLISHED ohne finale Publication,
Delete-Unlink-/Sync-/Jobfehler, fehlende/abweichende Parent-/Dateibindungen,
Symlinks/Hardlinks/fremde Dateien, MAINTENANCE-Scope, Reader-/Recovery-Drain und
alte Tickets ab. Echte SessionCoordinator-Tests prüfen den Maintenance-Barrier,
READY erst nach Erfolg und unveränderte absolute Frist. READY-Dokumente werden
beim Unlock nur inventarisiert, nicht automatisch vollständig entschlüsselt.

**BLOCKER – RED-1:** Doppelt ausgeführter Document-Worker führt dessen Workclosure
zweimal aus. RED-2: doppelt ausgeführter Main-Runnable publiziert zweimal.
RED-3: nach Workerabschluss ausgeführtes `drainAndFence` unterdrückt dessen
bereits gequeuete Main-Publication nicht. Deterministischer erster RED-Lauf:
11 Tests, 3 Assertionfehler; erwartet jeweils einmal/einmal/keine Publication,
tatsächlich zwei/zwei/eine. Das sind tatsächliche Dispatch-Vertragslücken unter
diesen Executor-/Fencefolgen, kein Beweis, dass ein normaler Executor von selbst
Runnables dupliziert. Ein Revoke bzw. Generationwechsel unterdrückt den Callback
weiterhin über den bestehenden Context.

Reproduktion auf diesem Stand: In `DocumentRecoveryDispatchContractTest` nur
für einen lokalen RED-Lauf in `gapDuplicateWorkerExecutesWorkTwice` die erwartete
Mutationsanzahl 2 → 1, in `gapDuplicateMainPublishesTwice` Callbackanzahl 2 → 1
und in `gapFenceAfterWorkerDoesNotFenceQueuedMainPublication` Callbackanzahl
1 → 0 ändern. Dann
`./gradlew :data:testDebugUnitTest --tests '*DocumentRecoveryDispatchContractTest' --offline`.
Danach ausschließlich diese lokalen RED-Assertionänderungen zurücknehmen.
Die eingecheckten grünen `gap…`-Tests charakterisieren ausdrücklich den offenen
IST-Zustand; sie behaupten keinen sicheren Zielvertrag.

**BLOCKER – RED-4:** `gapWritingWithoutNamespaceBlocksMaintenanceAndPreservesJob`:
synthetisch gültiger IMPORT_WRITING-Job ohne Root; `reconcile()` wirft
ProductStorageFailure. Ein eigener RED-Lauf mit direktem `reconcile()` statt
`invalid{reconcile()}` ergab 1 Test / 1 Fehler. Der Zustand ist durch den neuen
Stagingtest `revokeAfterReturnedWritingPreservesJobBeforeNamespaceCreation`
auch aus dem tatsächlichen Stagingkoordinator erreichbar. Die aktuelle Recovery
bewahrt den Job/Dateizustand und blockiert, statt automatisch zu bereinigen.
RED-4 prüft damit einen vorgeschlagenen Verfügbarkeitsvertrag, keine bereits
zugesagte automatische Reparatur und keine nachgewiesene Vertraulichkeits- oder
Integritätsverletzung. Wiederherstellungspolitik gesondert reviewen; keine
Reparatur in diesem Slice.

**UNWIRED:** Der zusätzliche Cross-Family-Test hält ein gemeinsames Task-Pending
und führt dennoch DocumentDispatch-Arbeit aus. Die gemeinsame Epoche bleibt
unverändert; Operations ist im gemeinsamen Port weiter busy. Das belegt die
fehlende Portverbindung in dieser isolierten Kombination, keine produktive
Cachefreigabe. Es gibt weiterhin keine RAM-Hits.

Weitere Dispatchtests prüfen Lock vor Worker, Generationwechsel nach Wirkung,
BLOCKED/READY-Verweigerung als Contextsimulation, verlorenen Main-Dispatch,
Worker-/Main-Dispatchexceptions und Fehler nach simuliertem dauerhaftem Schritt.
Fehlender Maincallback erzeugt keinen automatischen Retry/Erfolg. Die produktiven
Action-/Permissionwechsel sind über die bestehenden StorageContext-Checks
gebunden; dafür wird hier kein zusätzlicher nativer Integrationstest behauptet.
Eine zunächst falsche Testannahme zur Recovery-Sync-Exception wurde korrigiert:
Recovery reicht IOException weiter, anders als `documentFileBoundary` in den
Produktkoordinatoren. Das war ein Testfehler, keine zusätzliche Produktlücke.

### 57.6 Android-Fixtures: Checkliste vor gesonderter Gerätefreigabe

**BESTEHEND / BLOCKER vor einem nativen Lauf:**

- DocumentText/Image/PdfStorageTest benutzen zufällige `p10c2-`/`p10c1-`/
  `p10c3-`-Unterwurzeln des kanonischen noBackupFilesDir;
  DocumentReconciliationStorageTest benutzt `p10c4-test-`. CoreWriterBoundary-
  StorageTest benutzt `p11-corewriter-` unter noBackupFilesDir. Vor Ausführung
  Paket, exakte Selektoren und tatsächlichen targetContext unabhängig prüfen;
  ein Namenspräfix allein ist keine Isolation gegenüber Produktverzeichnissen.
- Dokumentfixtures haben `@After` mit Key-Zeroisierung und präfix-/parentgeprüftem
  `deleteRecursively`; Rückgabewerte der Löschung werden dort nicht nachgewiesen.
  Diese Routinen sind keine in diesem Slice freigegebene sichere Bereinigung.
  Vor Gerätetest kanonische Bindung, NOFOLLOW-/Symlink-/Hardlinkverhalten,
  exklusiven Besitz, zulässige Leafliste und nachprüfbaren Restzustand reviewen.
  Keine rekursive Löschung eines produktiv genutzten Roots hinzufügen.
- CoreWriterBoundaryStorageTest invalidiert im finally den Owner und zeroisiert
  den Schlüssel, hat aber keine nachgewiesene Verzeichnisbereinigung. Vor Lauf
  gesonderten engen Bereinigungsplan abnehmen, keine pauschale Löschroutine.
- Für jeden konkreten Selektor sämtliche Owner-Bindungen, Snapshot-/Stream-/
  Renderer-Lifetimes, Bytearraykopien und finally-Pfade auch bei Setupfehler,
  Assertionfehler, Revoke und fehlgeschlagenem Cleanup prüfen. Vorhandene finally-
  und @After-Blöcke allein sind kein vollständiger nativer Ressourcenbeweis.
- Vorher-/Nachherinventar des isolierten Testnamespace, DB-/Datei-Endzustand,
  Ownerinvalidierung und Zeroisierung belegen; keine Dokumentjob-Artefakte vor
  autorisierter Recovery manuell entfernen. Keine neuen Verzeichnisse/Permissions.
- Android-/Room-/SQLCiphertests wurden nicht installiert und nicht ausgeführt.
  Offline-Kompilation ersetzt weder native Rename-/fsync-Evidence noch
  Cursor-/Key-Lease-Cleanup. Issue #32 bleibt offen:
  `SQLCipherStatement.close()` schließt den Cursor nicht explizit.

### 57.7 Abschluss und nächste enge Reviewentscheidung

**HOSTGETESTET:** Abschlusszahlen und Offline-Buildnachweise siehe nachfolgenden
Validierungsabsatz. **VORGESCHLAGEN:** Zuerst einen separaten kleinen Dispatch-
Lifecycle-Slice für at-most-once Worker/Main und Fence-gebundene Publication
reviewen; getrennt davon Recoverypolitik für WRITING ohne vollständigen Root
festlegen. Erst danach den mehrphasigen Evidence-/Invalidierungsport reviewen.
Keine globale Completion aus einem einzelnen DB- oder Dateischritt ableiten.

**UNWIRED:** DocumentWriter, Leadership/P3b und Maintenance/Recovery gegenüber
dem gemeinsamen Runtime-Mutationsport; kein Snapshotowner (#37), keine
Performance-/Navigationsintegration (#38). Writer-/Recoveryarbeiten gehören
weiter zu #35; bestehende Editor-/CAS- und ABA-Grenzen zu #36 werden nicht durch
dieses Inventar geschlossen. **BLOCKER:** vier oben reproduzierte Zielvertrags-
REDs, fehlender mehrphasiger Outcome-/Cleanupbeleg und natives Cursor-/Cleanup-
Risiko #32. Keine vollständige Release-1.0- oder P11-Snapshotfreigabe.

Unverändert bleiben SessionCoordinator, StorageAccess, SessionDbKeyOwner,
SQLCipher/Tink/B2Driver, Schema 11, 180 Sekunden absolut / 30 Sekunden READY-idle,
8+8-MiB-Budget, Dateilimits, noBackup-Root-/Namespaceprüfung, CREATE_NEW,
NOFOLLOW, atomic no-replace Rename, File-/Directory-fsync und authentifizierte
Verschlüsselung. Keine Produktionsdatei, Permission, Dependency, Navigation,
installierte App oder bereits integrierte Writerfamilie wurde verändert.

Validierung: gezielter Lauf
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --offline`:
236 Tests, 0 Fehler, 0 Skips. Anschließend vollständiger angeforderter Offline-Lauf
mit `:app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest
:domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin
:app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest
:app:verifyDemoManifest --offline`: **BUILD SUCCESSFUL**. Auswertung ausschließlich
der Debug-Unit-Test-XMLs: App 671, Data 910, Security 4 = **1.585 bestanden,
0 Failures, 0 Errors, 0 Testskips**. Unveränderte App-/Securitychecks und
Androidtest-Kompilationen wurden von Gradle teilweise als UP-TO-DATE bestätigt;
Domain hat keine eigenen Testquellen. Der Gradle-Lifecycle-Task
`preDebugAndroidTestBuild SKIPPED` ist kein übersprungener Testfall.
`git diff --check` erfolgreich. Keine Instrumentation/Installation, keine neuen
Android-Fixtures und kein nativer Cleanupnachweis. Rohlogs/Artefakte bleiben
außerhalb des Commits. Regulärer WIP-Commit/Push; danach unabhängiges GitHub-Review.

## 58. Document-Dispatch-Lifecycle

Issue #35 auf verbindlicher Basis
`184dadb41d2abeb1dc140bea7f3cc02b6ac4fda0`.
Lokaler HEAD und unabhängig über GitHub-Ref geprüfter Remote-HEAD entsprachen
dieser Basis; Branch `p11-performance-optimization`, Arbeitsbaum und Diff waren
vor Änderungen sauber. Der SSH-Remoteabruf scheiterte an der lokalen
SSH-Konfiguration; die Prüfung erfolgte anschließend erfolgreich über die
GitHub-API. Keine fremden Änderungen übernommen oder überschrieben.

### 58.1 Ausgangsbeleg und eng begrenzte Implementierung

**HOSTGETESTET – RED vor Produktänderung:** Die drei §57-Characterizations wurden
auf Zielwerte umgestellt: eine Workerwirkung, eine Main-Publication, null
Publication nach einem Fence zwischen Worker und Main. Der gezielte Lauf
`./gradlew :data:testDebugUnitTest --tests '*DocumentRecoveryDispatchContractTest' --offline`
kompilierte und ergab **12 Tests / genau 3 Assertionfehler** in den drei
bisherigen `gapDuplicateWorker…`, `gapDuplicateMain…` und `gapFenceAfterWorker…`.
Das waren weder Compilefehler noch Skips. Die drei Tests tragen jetzt eindeutige
Zielvertragsnamen und behalten die strengeren Erwartungen.

**IMPLEMENTIERT:** Nur `DocumentProductDispatch` und ein kleiner lesender
Lanezugriff wurden produktiv geändert:

- Auftragslokales `AtomicBoolean started`: CAS vor Eintritt in die Workclosure
  verhindert mehrfache und parallele Workerwirkung desselben Dispatch-Runnable.
  Auch nach Fehler, Revoke oder dauerhaftem Teilfortschritt wird der Auftrag
  nicht erneut ausgeführt. Keine zweite Sourceöffnung, Mutation oder Main-
  Einreihung durch Wiederholung desselben Workers.
- Auftragslokales `AtomicBoolean delivered`: CAS zu Beginn der Main-Zustellung,
  auch im bestehenden Blocked-Fehlerpfad. Wiederholte/parallele Main-Runnables
  können weder Ergebnis, fachliche Rejection noch Blocked-Meldung wiederholen.
  Ein verweigerter erster Zustellversuch ist verbraucht. `publish`-Rückgabe false
  löst keinen Retry aus, auch wenn der Context den Callback vorher ausführte.
- `DocumentNamespaceLane.isCurrent(ticket)` liest ausschließlich die bestehende
  volatile Epoche. Prüfung vor `StorageContext.publish` und nochmals innerhalb
  seines Callbacks. Ein inzwischen beobachteter Fence unterdrückt das Ergebnis.
  Kein Main-Thread erwirbt dazu den lang gehaltenen Namespace-Monitor.

Diese Booleans vergeben keine Session-/Scopeberechtigung. READY-Erwerb,
Operationsgates, Generation und `StorageContext.publish` bleiben maßgeblich.
Ein Lane-Ticket allein autorisiert keine Antwort. Der Blocked-Fehlerpfad behält
seine bestehende Context-STEP-Prüfung; ein Fence vor dem Worker bleibt ein
Storagefehler ohne physische Wirkung und kann weiterhin einmal Blocked melden.

### 58.2 Tatsächlicher Nebenläufigkeitsvertrag

**HOSTGETESTET:** Fence vor Worker verhindert Arbeit. Fence nach Worker vor Main
verhindert Ergebnispublication. Ein deterministisch innerhalb der verzögerten
Context-Publication vor Callbackzulassung ausgeführter Fence verhindert durch
die innere Prüfung ebenfalls das Ergebnis. Ohne Fence wird die gültige Antwort
einmal zugestellt. Revoke/Generationwechsel verhindern die Antwort weiterhin;
später wieder gültiger Context macht einen verbrauchten Main-Runnable nicht
wieder ausführbar. Ein bereits erfolgreich publizierter Auftrag wird durch einen
späteren Fence weder erneut zugestellt noch als zurückgerollt klassifiziert.
Ein eigener Latch-Test hält den Namespace-Monitor und belegt, dass Main trotzdem
abschließen kann; parallele Worker- und parallele Main-Runnables sind separat
getestet.

**BLOCKER / explizite Racegrenze:** Das letzte volatile Lesen und der unmittelbar
folgende fachliche Callback bilden **keine atomare Einheit** mit
`drainAndFence`. Ein konkurrierender Fence nach der inneren Prüfung kann den
bereits zugelassenen Callback nicht rückwirkend verhindern. Die drei konkreten
RED-Reproduktionen und die verzögerte Freigabe sind behoben; eine vollständig
linearisierbare Fence-/Publication-Garantie ist damit ausdrücklich nicht
nachgewiesen. Ein weitergehender atomarer Owner-/Publicationvertrag benötigt
gesondertes Review. Kein Namespace-Monitor um UI-Callbacks, keine blockierende
Kopplung des UI-Threads an lange Dateioperationen, keine große Monitorumgestaltung.
Normale Dokumentaufträge erhöhen die Lane-Epoche nicht: dies ist kein allgemeiner
„neuester Dokumentauftrag gewinnt“-Vertrag. Dafür werden weder eine globale
Jobregistry noch eine zweite Mutationsepoche eingeführt.

### 58.3 Fehler, Reads und Beweisgrenzen

**HOSTGETESTET:** Kontrollierte Worker-/Mainqueues, gespeicherte Runnables,
Context-/Generationssimulation, getrennter physischer Simulationsledger und
Callbackzähler prüfen:

- null READY/BLOCKED-Simulation, Lock vor Worker, Generationwechsel nach Wirkung;
- doppelte Worker, doppelte Mainrunnables und beide zusammen, jeweils one-shot;
- tatsächlich parallele Ausführung beider Runnablearten;
- Fehler nach simuliertem WRITING bzw. DELETE_FILE_PENDING, einmalige Blocked-
  Meldung und keine Wiederholung oder erfundene Rückabwicklung;
- Workerdispatch- und Maindispatch-Exceptions sowie verlorenen Main-Runnable;
  kein später ungebundener Ersatzcallback und kein automatischer Retry;
- Context führt Callback aus und liefert false; Context verweigert ersten
  Versuch, späterer Replay bleibt wirkungslos;
- Fence vor Worker, zwischen Worker/Main und während verzögerter Contextfreigabe;
- Summarywert, Listenrückgabe und normale NOT_ALLOWED-Rejection genau einmal,
  jeweils nur ein Work-/Readaufruf und keine simulierte Dateimutation;
- Read nach Revoke ohne Callback; bestehender gemeinsamer Pending-Port bleibt
  unberührt, `ramReuseEnabled=false`.

Summary-/Listen-Tests prüfen die Dispatchzustellung mit synthetischen Werten,
keine neue DAO-Korrektheitsbehauptung. Bestehende Dokument-Foundation-, Storage-,
Import-, Publication-, Delete-, Recovery-, Read-/Viewer- und Sessiontests bleiben
erhalten. PDF-/Bild-/Text-`DocumentReadDispatch` wurde nicht verändert.
Die neuen Ledger sind **kein SQLite-/SQLCipher-, fsync-, Powerloss- oder nativer
Cleanupbeweis**. Ein physischer Schritt bleibt nach späterem Revoke/Fehler im
Ledger bestehen; es wird kein DB-Rollback aus einer Exception abgeleitet.

### 58.4 Unveränderte offene Grenzen

**UNWIRED:** Keine produktive DOCUMENT-Writerfamilie, keine Anbindung an
`WorkspaceMutationInvalidation`/`WorkspaceWriteBoundary`, kein globaler
COMMITTED-Beleg aus `DocumentResult.Value`. Der mehrphasige DB-/Datei-/Inventar-/
Cleanup-/Recoveryvertrag aus §57 bleibt ein separat zu prüfender Vorschlag (#35).
Die Namespace-Lane ersetzt die gemeinsame Mutationsepoche nicht. Kein
Snapshotowner (#37), keine RAM-Hits oder Performance-/Navigationseinbindung (#38).
Bestehende Editor-/CAS-/ABA-Grenzen (#36) bleiben unverändert.

**BLOCKER:** RED-4 aus §57 bleibt unverändert: IMPORT_WRITING ohne Root führt bei
Maintenance fail-closed zu BLOCKED. Der bestehende Characterization-Test bleibt
erhalten. Keine Root-Neuerstellung, Jobentfernung, Reparaturheuristik oder
Dateilöschung ergänzt. Keine allgemeine Recoveryfreigabe. Zusätzlich bleiben die
oben beschriebene letzte Fence-/Callback-Racegrenze, fehlende mehrphasige Evidence
und nativer Cursor-/Cleanupnachweis #32 offen. Insbesondere schließt
`SQLCipherStatement.close()` den Cursor nicht explizit; Hosttests ersetzen keinen
nativen Ressourcenbeweis.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink/B2Driver,
Schema 11, 180-s-Absoluttimeout, 30-s-READY-idle, 8+8-MiB-Budget,
Root-/Symlink-/Hardlink-/No-Replace-Regeln, Verschlüsselung, File-/Directory-Sync
und Kapazitätslimits wurden nicht verändert. Keine neue Dependency/Permission,
keine Geräteinstallation, keine Änderung installierter Apps oder P3b/Leadership.

### 58.5 Abschlussvalidierung

**HOSTGETESTET:** Gezielter Dokumentlauf mit `--tests '*Document*Test'`:
**249 Tests bestanden, 0 Failures, 0 Errors, 0 Skips**; davon 25 Dispatch-
Vertragstests (13 zusätzliche Fälle gegenüber §57).
Vollständiger angeforderter Offline-Lauf mit
`:app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest
:domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin
:app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest
:app:verifyDemoManifest --offline`: **BUILD SUCCESSFUL**.
Debug-Test-XMLs: App 671, Data 923, Security 4, zusammen **1.598 bestanden,
0 Failures, 0 Errors, 0 Testskips**. Security war UP-TO-DATE; Domain hat keine
Testquellen. Der Gradle-Lifecycle-Task `preDebugAndroidTestBuild SKIPPED` ist kein
übersprungener Testfall. Beide Androidtest-Kompilationen und beide Manifestchecks
waren erfolgreich. Kein Geräte-/Instrumentationlauf. `git diff --check` ist
sauber; keine Rohlogs, APKs oder sonstigen Buildartefakte im Commit.
Regulärer WIP-Commit/Push, anschließend STOPP bis zum unabhängigen GitHub-Review.

## 59. Document-Recovery ohne Root

Issue #35 – Untersuchung von RED-4 auf verbindlicher Basis
`5179204e3b307e2489168bf42a904e749fe8f3e4`.
Vor Änderungen: lokaler HEAD und unabhängig per GitHub-Ref abgefragter Remote-HEAD
identisch, Branch `p11-performance-optimization`, Arbeitsbaum und Diff leer.
**BESTEHEND:** Fail-closed bei rootlosem IMPORT_WRITING bleibt unverändert.
Dieser Slice ergänzt ausschließlich Tests und Assessment, keine Produktkorrektur.

### 59.1 Erreichbarkeit und unterschiedliche Beweisstärken

**HOSTGETESTET:** `actualStagingRevokeBeforeNamespaceThenRealSessionMaintenanceBlocks`
führt den echten `DocumentImportStaging.stage` und echte DocumentDao-Regeln aus.
Eine schmale Test-DB-Brücke übernimmt dieselben DAO-Reserve-/Advance-Aufrufe in
die vorhandene simulierte Transaktionsharness. Nach zurückgekehrtem Advance setzt
sie Revoke, noch bevor der Stagingkoordinator den Namespace initialisiert.
Festgestellt: IMPORT_RESERVED → IMPORT_WRITING, beide simulierten DB-Scopes
zurückgekehrt, kein offener DB-Scope, keine Sourceöffnung, kein DocumentRow,
`pag-documents-v1` fehlt. Anschließend läuft die echte Reconciliation im
MAINTENANCE-Kontext des echten SessionCoordinator-Testflows: Fehler,
Session BLOCKED, kein READY-Owner-/Erfolgsschritt, Job bleibt bestehen.

| Beleg | Tatsächlich geprüft | Grenze |
| --- | --- | --- |
| A Echter Stagingcode | Reserve/Advance vor `initialize`, Revoke unmittelbar nach Advance; Folgerecovery blockiert | Transaktionsengine ist Fake, nicht Room |
| B Persistierten Zustand simulieren | Gültige Jobs, vollständige DAO-Inventarregeln, echte Hostdateien, reale Reconciliation-/Sessionlogik | Fake-Rollback ist kein SQL-Rollback und kein dauerhafter Datenträger |
| C Tatsächliche SQLite-Commitbestätigung | Produktcode geprüft: SqlCipherDocumentImportDatabase verwendet pro Reserve/Advance einen eigenen `withWriteTransaction` innerhalb DB-/Keyscope; Dateizugriff erst nach dessen Rückkehr | **NICHT BEWIESEN in diesem Slice:** kein neuer echter SQLite-/Room-Commitlauf für diesen Dokumentpfad; Compile-only Android-Fixtures sind kein Ausführungsbeleg |
| D Native Crash-/Powerloss-Dauerhaftigkeit | Keine Geräteausführung; keine Crash-/Powerloss-Injektion | **NICHT BEWIESEN:** WAL-/Journal-, File-/Directory-fsync-, Rename- und native Cleanup-Dauerhaftigkeit |

Die tatsächliche Codefolge erlaubt einen zwischen WRITING-Rückkehr und erstem
Namespacezugriff unterbrochenen Import. Die Hosttests bestätigen diese
Erreichbarkeit auf Kontrollflussebene; sie behaupten keinen im Gerätelauf neu
beobachteten dauerhaften SQLite-Commit. Bereits §57/§58 trennen diese Grenzen.

### 59.2 Persistierte Evidence und Ununterscheidbarkeit

**BESTEHEND:** DocumentJobRow speichert ID, Action-/Document-ID, Operation, Phase,
Staging-/Blobname, Anzeigename, Format, optionale Größe und Dateischlüssel.
Bei WRITING ist die Größe null. Kein Feld bezeichnet „Root nie angelegt“,
„Namespaceinitialisierung bestätigt“ oder „erste Dateioperation noch nicht
begonnen“. `DocumentRecoverySnapshot` enthält ausschließlich Blobnamen fertiger
Dokumente und keyfreie Jobheader. Die volle DAO-Validierung läuft vor dieser
Reduktion; sie erfindet keinen zusätzlichen historischen Beleg.
Die Schema-11-Phasen-/Bindungs-/Kapazitätsregeln schützen die zulässige
DB-Zustandsfolge, beobachten aber keine Root-Erstellung oder späteren Rootverlust.
Die DB-Initialisierungsmarker von ProductInventory belegen nicht die Historie des
separaten Dokumentnamespace. UUIDs, fehlende Größe, aktuelle Pfadabwesenheit und
flüchtige Lane-Epoche sind ebenfalls kein solcher Beleg.

**HOSTGETESTET:** `neverCreatedAndCreatedThenLostHaveIdenticalPersistedEvidence`
nimmt den vom echten Stagingcode hinterlassenen WRITING-Job und vergleicht:

1. Historie H0: Revoke vor Namespace-Erstellung; Root fehlt.
2. Historie H1: derselbe Job; danach synthetische Cipherdatei mit Tink in einem
   vollständigen Testnamespace geschrieben/geschlossen, ohne CLOSED-DB-Schritt;
   anschließend externen Namespaceverlust simuliert. Nur einzeln benannte Leaves
   des eigenen TemporaryFolder werden im Test entfernt, keine Produktbereinigung.

Beide Beobachtungen liefern denselben Recovery-Snapshot, denselben vollständigen
Job einschließlich Schlüsselbindung und dieselbe Rootabwesenheit. Beide führen
zur unveränderten fail-closed Ablehnung ohne Abort/Unlink/mkdir/READY.
Der künstlich erzeugte externe Verlust ist ein **Ununterscheidbarkeitsbeleg**,
kein Nachweis, dass PaG selbst den Root löscht, und keine Wahrscheinlichkeitsschätzung
für einen solchen Schaden. H1 ist auch mit einer Unterbrechung nach Dateisync vor
CLOSED vereinbar; native Dauerhaftigkeit wird dadurch nicht behauptet.

**NICHT BEWIESEN:** Aus der aktuellen Datenbank lässt sich für WRITING ohne Root
nicht „nie angelegt“ herleiten. Automatisches Joblöschen würde zumindest die
verbliebene Schlüssel-/Recoverybindung und die erkennbare Schadens-/Abbruchspur
verwerfen. Es rekonstruiert bereits verschwundene Bytes nicht. Dass kein
abgeschlossener Import bestätigt wurde, erlaubt keine Behauptung verlustfreier
Reparatur oder unbeschädigter globaler Dokumentablage.

### 59.3 Zustandsmatrix des bestehenden Recoveryvertrags

Alle regulären Recoveryzeilen setzen einen aktuellen MAINTENANCE-Kontext mit
gültiger Generation, autorisierten Key-/DB-Scopes, gültigen Action-/Job-/Document-
Bindungen und erfolgreich abgeschlossenem globalem Inventar voraus. „READY
möglich“ bedeutet erst nach sämtlichen Jobs, frischem Endinventar, Cleanup und
Maintenance-Barrier; keine Einzelzeile verleiht READY. Bei Revoke bleibt die
Session ggf. LOCKED statt BLOCKED, aber niemals unberechtigt READY.

| Fall | DB und tatsächlicher Dateistand | Mögliche Historie / bestehende Behandlung | READY / BLOCKED |
| --- | --- | --- | --- |
| A | DB ohne Documents/Jobs; kein Root | noch nie Dokumente angelegt; auch historisch leere Ablage nicht unterscheidbar. Inventar bleibt leer, kein mkdir | READY möglich |
| B | nur IMPORT_RESERVED; kein Root | autorisierte Reservierung vor WRITING; im vertrauenswürdigen Phasenvertrag darf diese Reservierung noch keine Datei erzeugt haben. Bestehender Abort entfernt ausschließlich den geprüften Job | READY nach bestehendem Abort möglich; kein neuer Reparaturpfad |
| C | nur IMPORT_WRITING; kein Root | H0 und H1 ununterscheidbar; keine belegte Nichtausführung | BLOCKED; Job erhalten |
| D | mehrere WRITING; kein Root | unabhängige Abbrüche oder Namespaceverlust; keine sichere Teilmenge herleitbar | BLOCKED vor erstem Abort |
| E | RESERVED + WRITING; kein Root | ein früherer WRITING-Zustand macht globales Inventar unzulässig; auch RESERVED nicht vorher bereinigen | BLOCKED; beide Jobs erhalten |
| F | mindestens ein fertiger DocumentRow; kein Root | fehlende erwartete dauerhafte Dokumentablage; unvollständiger Import legitimiert keine Löschung fertiger Daten | BLOCKED; DocumentRows/Keys erhalten |
| G | IMPORT_CLOSED; kein Root | zuvor bestätigter Dateiabschluss laut Phasenvertrag, aktuelle Datei fehlt; keine Authentifizierung möglich | BLOCKED; Job erhalten |
| H | FILE_PUBLISHED; kein Root | finaler Blob erwartet, fehlt; kein Commit aus Phasenname erfinden | BLOCKED; Job erhalten |
| I | DELETE_RESERVED + Parent; kein Root | erwarteter Parent/Blob noch nicht detached; fehlende Ablage nicht als erfolgreichen Delete deuten | BLOCKED; Parent und Job erhalten |
| J | DELETE_FILE_PENDING ohne Parent; kein Root | Parent kann bereits entfernt sein; Unlink kann stattgefunden haben oder Root verschwunden sein. Fehlender Root unterscheidet sich von fehlendem einzelnen Pending-Blob in vollständigem Root | BLOCKED; Job erhalten |
| K | nackter partieller Root | Initialisierung unterbrochen oder beschädigt; keine gesicherte vollständige Struktur | BLOCKED; keine Ergänzung |
| L | Root nur mit staging | unterbrochene Initialisierung oder verlorenes files | BLOCKED; kein mkdir files |
| M | Root nur mit files | unterbrochene Initialisierung oder verlorenes staging | BLOCKED; kein mkdir staging |
| N | Symlink statt Root | fremde/umgebogene Bindung, unabhängig von DB-Phasen | BLOCKED; Ziel unverändert, kein Folgen/Unlink |
| O | fremder Datei-Leaf bei bekanntem Inventar | externer Namespacezustand oder unbekannte Datei; DB legitimiert ihre Löschung nicht | BLOCKED vor jeder bekannten Bereinigung; fertige Blobs erhalten |
| P | WRITING + bekanntes staging im vollständigen Root | unvollständiger, noch nicht publizierter Import; bestehender Vertrag bricht ihn ab: genau bekanntes staging unlink, Directory-Sync, Recheck, geprüfter Job-Abort | READY nach vollständigem bestehenden Recovery möglich; kein Source-Reopen |
| Q | WRITING, staging fehlt, vollständiger Root vorhanden | vor CREATE_NEW abgebrochen oder früheres Recovery-Unlink vor Job-Abort; auch externe Leafentfernung nicht historisch unterscheidbar | bestehende Politik akzeptiert aktuellen vollständigen globalen Zustand, synchronisiert staging, bricht Job ab; READY möglich; kein Beweis „nie geschrieben“ |
| R | Revoke vor Inventar | Autorisierung nicht mehr gültig | keine DB-/Dateiarbeit, kein READY |
| S | Revoke nach DB- oder vollständigem Dateiinventar | Snapshot allein autorisiert keine folgende Mutation | kein Cleanup/Abort nach den jeweiligen Gates; kein READY |
| T | Revoke vor DB-Abort | Unlink/Sync können vorher schon ausgeführt sein | Job bleibt; kein erfundener globaler Rollback/READY |
| U | Fehler während DB-Abort | bestätigter lokaler Rollback lässt Job bestehen; bei unbekanntem Abschluss neuer autorisierter Read erforderlich. Frühere Dateioperation bleibt getrennt | kein Erfolgsabschluss/READY; BLOCKED bei gültiger Session |
| V | Namespace verändert sich zwischen Prüfungen | externe Änderung oder Bindungsverlust; Lane serialisiert PaG-Arbeit, nicht externe Eingriffe | erneutes Inventar blockiert bei erkanntem Fremdleaf; keine stillschweigende Reparatur |

**HOSTGETESTET:** A/B und P/Q waren bereits abgedeckt; zusätzliche Tests prüfen
insbesondere C–O sowie R–V mit erhaltenem DB-/Dateizustand. Der neue Test zu B
prüft ausdrücklich die **bereits bestehende** RESERVED-Abbruchpolitik samt echter
Session-READY-Grenze. Der Test zu V injiziert einen Fremdleaf vor dem zweiten
Dateiinventar: kein Unlink und kein DB-Abort. Daraus folgt keine Garantie gegen
jede externe Manipulation nach der letzten Prüfung.

### 59.4 Bestehende Integrität, Ressourcen und Sessiongrenzen

**BESTEHEND:** Vor erster Mutation validiert DocumentReconciliation die gesamten
DocumentRows und Jobs samt Actionstatus/-bindung, Kapazität, Namen und
Phase-/Parentrelationen über DocumentDao. DocumentFileInventory prüft den
vertrauenswürdigen noBackup-Root, kanonische Identität, NOFOLLOW, erlaubte
Verzeichnisse, bekannte Leafnamen und bei Recovery doppelte Dateiidentitäten.
Fertige Documents müssen vollständig im Namespaceinventar vertreten sein.
Ein einzelner zulässiger Job darf ein global ungültiges Inventar nicht überstimmen.

Maintenance drain/fenced dieselbe Namespace-Lane, bevor Preflight/Inventar und
Recovery laufen; alte Workertickets bleiben ungültig. Frische DB-/Dateiinventare
werden zwischen Jobs und am Ende erneut erhoben. Aktuelle Context-/Generations-
und COMMIT-Gates gelten weiterhin vor/nach einzelnen Schritten. Die geborgte
Maintenance-Keyautorität bleibt kurzlebig, vor READY-Owner-Vorbereitung.
Der Runtimepfad meldet fehlerhafte Maintenance nicht erfolgreich; Revoke ist kein
Rollback früherer DB-/Dateischritte. Die offene letzte Fence-/Publication-
Racegrenze aus §58 wurde nicht verändert oder geschlossen.

**HOSTGETESTET:** Weitere Fälle belegen Revoke nach DB-Inventar und nach erstem
Dateiinventar; Revoke unmittelbar vor Abort nach bereits erfolgtem Unlink;
Fehler nach simuliertem Job-DELETE mit lokalem Fake-Rollback, wobei die vorherige
Dateientfernung bestehen bleibt; keine echte Session-READY-Freigabe nach
fehlgeschlagenem Abort. Ein zweiter autorisierter Unlock mit neuer Generation
führt neue Inventarreads aus und erkennt einen inzwischen neu vorhandenen
rootlosen WRITING-Job, statt einen alten erfolgreichen Snapshot zu verwenden.
Bestehende Scope-, Lane-, Dispatch-, Read-, Publication- und Sessiontests bleiben
erhalten. Die Tests prüfen keine universelle Sicherheit gegenüber kompromittiertem
Kernel/Dateisystem und keinen nativen Ressourcenabschluss.

### 59.5 Bewertung möglicher Recoverypolitiken

| Ansatz | Datenverlust / Integrität / Manipulationsrisiko | Vertraulichkeit / Crash / Schema / Bestand / Maintenance |
| --- | --- | --- |
| A Status quo: rootloses WRITING bleibt BLOCKED | Erhält verbleibende DB-/Key-/Jobbelege; verdeckt möglichen Namespaceverlust nicht. Availability-Lücke bleibt bewusst bestehen | Kein neuer Klartext-/Schlüsselpfad, keine Schemaänderung; alte Imports bleiben interpretierbar. Revoke verlangt neue autorisierte Maintenance; kein zusätzlicher Crashbeweis |
| B Nur bewiesene Nichtausführung bereinigen | RESERVED kann innerhalb des unveränderten vertrauenswürdigen Phasenvertrags abbrechbar sein, wie bereits implementiert. Für WRITING fehlt der notwendige Beleg; Abwesenheit, Größe null oder UI-Flag genügen nicht | Kein zusätzlicher Angriffspfad nur dann, wenn Evidence tatsächlich autoritativ und dauerhaft ist. Heutiges Schema liefert sie für WRITING nicht; Legacy-WRITING darf nicht umgedeutet werden |
| C Zusätzliche vertrauenswürdige Vorbedingungen | Aktuelle leere Documentliste, gültiger Key, korrekte UUIDs oder READY_TO_OPEN beweisen keine fehlenden historischen Datei-Sideeffects. Auch ein einfacher „vorher“-Marker schließt nachfolgende Arbeit vor Markerupdate nicht aus | Neue dauerhafte, gebundene und crashgeordnete Evidence müsste unabhängig reviewed werden; kein flüchtiges Token als Ersatz. Kein solcher kompatibler Beleg im bisherigen Schema 11 gefunden; kein neuer Persistenzort/Marker in diesem Slice |
| D Künftige Stagingphasen geordnet ändern | Vollständig geprüfte und synchronisierte Namespaceinitialisierung vor WRITING könnte das spezielle H0-Fenster für künftige Versuche reduzieren. Rootverlust bliebe Schaden, kein Abbruchbeweis | Ändert die bisherige Grenze „kein Namespacezugriff vor WRITING“ und muss separat sicherheitsgeprüft werden. Parent-/Root-fsync, Zwischencrashs bei mkdir, RESERVED mit Teilroot und Revoke müssen vollständig behandelt werden. Historische WRITING-Jobs bleiben mehrdeutig; Schema-11-Kompatibilität und Maintenance dürfen nicht still gebrochen werden |

**VORGESCHLAGEN:** Status quo A für bestehendes rootloses WRITING beibehalten.
Es gibt auf den geprüften bestehenden Belegen **keinen nachweislich sicheren
destruktiven Minimalpatch**, der die zwei Historien unterscheiden und
verlustfreie automatische Recovery zusagen könnte. Daher weder Joblöschung noch
Root-Rekonstruktion vorschlagen, als wäre ihre Sicherheit schon bewiesen.
Die bestehende Politik für einen vollständigen Root (P/Q) ist eine explizite
Abbruchregel für unpublizierte Imports und kein Beweis fehlender Vorgeschichte;
sie darf nicht allein für höhere Verfügbarkeit auf einen global fehlenden Root
ausgedehnt werden.

Ein nächster enger **Reviewvorschlag, kein implementierter Patch** ist die
präzise Definition einer zukünftigen Namespace-Initialisierungs-/Phasenordnung:
vorheriger globaler Bestand, Rootidentität, vollständig angelegte Verzeichnisse,
File-/Parent-/Directory-Sync-Reihenfolge, explizite Grenzen vor CREATE_NEW und vor
WRITING sowie jede Crash-/Revokezwischenstelle. Benötigte Tests wären zusätzliche
Injektionen nach jedem mkdir/fsync/DB-Ende, fehlender/ersetzter Root, alte
Schema-11-Jobs, vollständiger und partieller Root, Revoke und erneute Maintenance.
Erst nach unabhängig bewiesenem Vertrag einen separaten Präventionsfix für neue
Imports freigeben; keine rückwirkende heuristische Bereinigung alter Jobs.
Eine weitere Persistenz-/Migrationsentscheidung wäre gesondert nötig, falls der
Vertrag dauerhaft zusätzliche historische Evidence benötigt. Keine neue
Sessionautorität oder automatische Wiederöffnung des Importstreams.

### 59.6 Umfang und offene Blocker

**BLOCKER:** RED-4 bleibt Availability-/Recoverypolitik-Blocker. Sichere
Nichtausführung ist für rootloses WRITING aus Schema 11 nicht entscheidbar;
vorhandene gültige Recoveryfälle sind nur innerhalb ihrer geprüften aktuellen
Namespace-/Phasenbedingungen behandelbar. Fehlende native Crash-/Cleanupbelege
(#32), mehrphasige Document-Outcome-/Invalidierungsevidence (#35), Snapshotowner
(#37), Performanceintegration (#38) und bestehende Editor-/CAS-/ABA-Grenzen (#36)
werden nicht durch diese Untersuchung geschlossen. Keine Recovery-, Dokument-,
RAM- oder Release-1.0-Freigabe.

Ausschließlich `DocumentReconciliationTest` und dieses Assessment geändert.
Keine produktive Änderung an Reconciliation, Staging, Publication, Delete,
Namespace-Lane, SQLCipher-Adaptern, Runtime oder anderen Writerfamilien.
SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink/B2Driver,
Schema 11, 180-s-Absoluttimeout, 30-s-READY-idle und 8+8-MiB-Budget bleiben
unverändert. Keine neuen Dependencies/Permissions, Persistenzorte, RAM-Hits,
Snapshotowner oder P3b-/Leadershipänderungen. Keine Geräteinstallation oder
Änderung installierter Apps. Testdateientfernung ausschließlich als explizite
Verlustsimulation im isolierten TemporaryFolder, keine Produktlöschroutine.

### 59.7 Abschlussvalidierung

**HOSTGETESTET:** 22 zusätzliche Recoverytests in der bestehenden Harness;
keine neuen Testdateien. Gezielter Lauf
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --offline`:
**271 bestanden, 0 Failures, 0 Errors, 0 Skips**.
Angeforderter vollständiger Offline-Lauf mit
`:app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest
:domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin
:app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest
:app:verifyDemoManifest --offline`: **BUILD SUCCESSFUL**.
Debug-Test-XMLs: App 671, Data 945, Security 4 = **1.620 bestanden,
0 Failures, 0 Errors, 0 Testskips**. Unveränderte App-/Securitytests und
Androidtest-Kompilationen waren UP-TO-DATE; Domain hat keine Testquellen.
`preDebugAndroidTestBuild SKIPPED` bezeichnet einen Gradle-Lifecycle-Task,
keinen übersprungenen Testfall. Beide Manifestchecks erfolgreich.
`git diff --check` sauber. Keine Android-/Instrumentationausführung und kein
zusätzlicher SQLite-/nativer Dauerhaftigkeitsnachweis. Keine Rohlogs, APKs,
Datenbanken oder Schlüssel im Commit. Regulärer WIP-Commit/Push; anschließend
STOPP bis zum unabhängigen GitHub-Review.

## 60. Document Fence/Publication Ordering

Issue #35, verbindliche Basis
`92cc3d61bb9bb8f6db8a609b13c2e600c714a398`.
Vor Änderung: korrekter Branch `p11-performance-optimization`, lokaler HEAD und
unabhängig über GitHub-Ref abgefragter Remote-HEAD identisch; sauberer Arbeitsbaum,
leerer Diff. §57–§59 und die unveränderten Session-/Read-/Recoverygrenzen geprüft.

### 60.1 Exakter Ausgangs-RED

**RED REPRODUZIERT:** Die beiden §58-Epochenlesungen waren Beobachtungen, keine
gegen `drainAndFence` atomare Callbackzulassung. Eine minimale interne
Scheduling-Seam im Dispatch pausiert **nach** der letzten erfolgreichen
`isCurrent(ticket)`-Prüfung und **vor** dem fachlichen Callback. Sie erhält weder
Context, Schlüssel noch Dokumentpayload und ist im Produkt standardmäßig noop.
Der Test benutzt den echten unveränderten SessionCoordinator und damit dessen
wirklichen `StorageContext.publish`-Monitor; nur Authentifizierung, Queues und
physischer Ledger sind synthetisch.

Deterministische Folge in `lastCheckThenEffectiveFenceMustSuppressOldCallback`:
Worker einmal, Main im echten Sessionmonitor, letzte Laneprüfung TRUE, Latchpause;
anderer Thread führt `drainAndFence {}` aus, Ticket danach veraltet; erst dann
Main fortsetzen. Zielassertion weiterhin **0 fachliche Callbacks**. Vor dem Fix:
`./gradlew :data:testDebugUnitTest --tests '*DocumentFencePublicationOrderingTest' --offline`
kompilierte und ergab **1 Test / 1 Assertionfehler**, tatsächlich 1 Callback.
Kein Stresstest, Compilefehler oder geänderter Sessionvertrag als RED-Beleg.
Der Fencebody ist hier absichtlich leer: das prüft Lane-Metadatenordnung,
keine vollständige autorisierte Recoverypipeline und keinen nativen Exploitnachweis.

### 60.2 Implementierter Vertrag und Linearisierung

**IMPLEMENTIERT:** Eine einzige atomare Referenz hält einen kleinen unveränderlichen
`FenceStamp(epoch)` statt des bisherigen einzelnen volatile Long. Die bestehende
Lane-Epoche bleibt die **einzige** Epoche; Admission erhöht sie nicht. Es gibt
keine aktiven Joblisten, zweite Sessionautorität, Pendingregistry, Payload,
Schlüsselreferenz oder DB-/Dateilebensdauer in diesem Stamp.

Publication geschieht weiterhin nur innerhalb des autoritativen
`StorageContext.publish`. Nach den bestehenden Beobachtungsprüfungen versucht
`tryAdmitPublication(ticket)` genau einmal:

1. aktuellen Stamp lesen, Epoche mit Ticket vergleichen;
2. per `compareAndSet` exakt diesen Stamp durch eine frische Identität mit
   **derselben** Epoche ersetzen;
3. nur bei erfolgreichem CAS den bereits sessionautorisierten Callback ausführen.

**Publication-Zulassung P ist der erfolgreiche CAS.** Der Callback beginnt danach
und darf genau einmal enden. Kein Monitor wird für diese Zulassung erworben oder
über den Callback gehalten. Ein zweiter interner noop-Beobachtungspunkt erlaubt
im Hosttest das Pausieren direkt nach P vor dem Callbackbeginn.

`drainAndFence` behält den bisherigen Namespace-Monitor und Drainvertrag.
Nachdem er die laufende Namespacearbeit drainiert hat, liest er die aktuelle
Epoche, berechnet deren nächsten Wert und speichert einen frischen Stamp atomar.
**Fencewirkung F ist dieser Store**, vor dem folgenden Maintenancebody. Nur
Fencewriter dürfen die Epoche erhöhen und sind schon durch den Namespace-Monitor
serialisiert. Admission verändert die Epoche nicht; daher ist zwischen dem
Epochlesen und Store kein anderer Epochenwert zu verlieren. Overflowverhalten
bleibt Math.addExact/fail-closed wie zuvor.

| Ordnung | Zulässiges Ergebnis |
| --- | --- |
| F vor P-Versuch | altes Ticket wird nicht zugelassen; fachlicher Callback beginnt nicht |
| P vor F | genau dieser bereits zugelassene Callback darf beginnen/enden, auch nach F; kein rückwirkender Rollback |
| beide konkurrieren | erfolgreicher CAS bzw. Fence-Store entscheidet eine atomare Reihenfolge; ein vor F gelesener alter Stamp kann nach F nicht erfolgreich ersetzt werden |
| anderer Admission-CAS gewinnt zuerst | ein konkurrierender einzelner CAS darf fail-closed verlieren, selbst ohne Epochenwechsel; kein Retry, kein späterer Replay |
| Sessionpublication verweigert | kein P-Versuch im Callback, kein fachliches Ergebnis; gültiges Ticket erteilt keine Sessionberechtigung |

Frische Stampidentitäten werden niemals wieder eingesetzt. Ein Store des Fences
macht jeden vorher gelesenen Stamp für CAS ungeeignet; dies ist keine bloße
Umbenennung der früheren TRUE-Beobachtung. Der erfolgreiche CAS ist eine neue,
explizite atomare Entscheidung. Die neue Garantie lautet **kein altes P nach F**,
nicht „kein bereits vor F zugelassener Callback darf nach F beginnen“. Der eigene
Test `admissionBeforeFenceMayBeginCallbackAfterFenceAndFinishOnce` erzwingt gerade
diesen erlaubten zweiten Fall mit Latches und null Callbackbeginn vor F.
Ein ursprünglicher fachlicher Callback und der mehrphasige Import bilden dadurch
weiterhin keine gemeinsame Transaktion.

Die Zulassung hat genau einen CAS, keine Warteschleife und keinen automatischen
Retry. Der Fence benötigt keinen CAS-Retry und wird nicht durch eine Folge
konkurrierender Admissions verdrängt. Ein unter Konkurrenz verweigerter
Zustellversuch bleibt durch delivered verbraucht. Der reguläre Produkt-Mainexecutor
ist seriell, und der echte Sessionmonitor serialisiert außerdem seine eigenen
Publications; die konservative Ablehnung bei anderweitiger Konkurrenz wird
bewusst nicht in eine optimistische Antwort oder neue Queueoperation umgedeutet.

### 60.3 Monitor-/Owneranalyse und Reentranz

**IMPLEMENTIERT / HOSTGETESTET:** Kein zusätzlicher Publication-Monitor,
keine Sperre um fachliche Callbacks. Vorhandene Reihenfolgen und neue Atomics:

| Pfad | Tatsächliche Reihenfolge | Begrenzung |
| --- | --- | --- |
| Dokumentworker | Namespace-Monitor N → kurze Sessionprüfungen S → Arbeit unter N, native Arbeit außerhalb S | unverändert; Main wartet nicht auf N |
| Maintenance | N drainieren → atomarer Fence-Store → Body mit Session-/Key-/DB-Schritten | Atomics halten keinen Monitor; Body weiterhin nur mit bestehender Autorisierung |
| Main | StorageContext.publish hält S → kurzer Stampread/CAS → Callback unter dem bestehenden S | kein N, kein zusätzlicher Monitor, keine DB-/Keylease |
| Revoke | vorhandener S-/Generationvertrag | konkurrierender Revoke wartet bei bereits zugelassener kurzer Sessionpublication wie bisher; kein neuer Lock |
| Reentrant UI | Callback kann S reentrant nutzen oder neue asynchrone Arbeit einreihen | CAS ist bereits beendet, kein Admission-Lock zu verlängern |

Damit entsteht durch Admission kein neues S→N, N→neuen Monitor oder umgekehrtes
Lock-Ordering. Maintenance kann N halten und auf einen kurzen bestehenden S-Read
warten; Main benötigt N weder für Prüfung noch Zulassung und kann S freigeben.
Die Latchtests erzwingen diese Konkurrenz mit echten Sessionprüfungen.
Ein synchroner Workerwait oder Datei-/Room-/Tink-Aufruf innerhalb eines
StorageContext.publish-Callbacks wäre weiterhin eine Verletzung dessen
bestehenden Kurz-/In-memory-Vertrags, keine erlaubte Erweiterung durch diesen Fix.
Es wird keine Sicherheit für beliebige vertragswidrige Callbacks behauptet.

Codebefund zur tatsächlichen Produktoberfläche: DocumentModel-Callbacks ändern
Form-/Metadaten, prüfen den bestehenden Editorzustand und fordern Refreshs an.
MainActivity verwendet den getrennten SingleThread-Worker und Mainexecutor.
Der Fix macht daraus keine synchronen Workerwaits und verändert keine Navigation.
Allgemeine Callbacklatenz unter dem bestehenden Sessionmonitor wurde hier nicht
als Performance- oder universeller Worst-Case-Nachweis gemessen.

### 60.4 Deterministische Ordering-/Livenesstests

**HOSTGETESTET:** Neue `DocumentFencePublicationOrderingTest` mit 13 Fällen:

- ursprünglicher strenger RED: Fence nach letzter TRUE-Beobachtung vor P;
- P zuerst, dann Fence vor Callbackbeginn: einmaliges erlaubtes Ende, Replay wirkungslos;
- Main hält S, Maintenance hält nach F den Namespace-Monitor und fragt S an:
  Main beendet die Antwortverweigerung ohne N-Wait, beide Futures schließen ab;
- langer Namespacehalter, während Main mit echtem Sessioncontext ohne N-Wait publiziert;
- P unter echtem S, konkurrierender Revoke und Fence: F macht Fortschritt,
  vor Revoke zugelassene Publication endet einmal, danach Session LOCKED;
- Callback nutzt Session reentrant und reiht einen weiteren UI-Read ein:
  kein Admission-Lock bleibt gehalten, genau ein Read und genau dessen Antwort;
- Callback lockt die Session selbst: kein Replay, kein neuer Auftrag aus LOCKED;
- Callbackexception: Zustellversuch verbraucht, kein zweiter Callback/Blocked,
  physischer Simulationszustand bleibt FILE_PUBLISHED;
- Generationwechsel mit erneuter Authentifizierung: kein Wiederbeleben alter Mainantwort;
- Storage BLOCKED schlägt ein weiterhin aktuelles Lane-Ticket;
- erneuter Session-Maintenance-Durchlauf führt Fence aus: alte Antwort weg,
  neuer autorisierter Auftrag funktioniert; keine Wiederholung des alten Workers;
- Admissions erhöhen die Epoche nicht; alter Epochenticketstand nach F nicht wieder zulassbar;
- ergänzender endlicher Konkurrenzlauf mit 10.000 Admissionversuchen: Fence
  beendet sich und alte Tickets bleiben verworfen. Dieser Lauf ergänzt die
  Latchbelege, ersetzt sie nicht durch einen Stresstest.

Futures haben begrenzte Testwartezeiten und Pools werden im finally beendet.
Die Protokollanalyse belegt keine neue zyklische Lockabhängigkeit und keinen
Admission-bedingten Monitorwait/Retry-Loop; Tests zeigen konkrete Fortschrittsfolgen.
**NICHT BEWIESEN:** universelle Schedulerfairness, harte Echtzeitlatenzen,
Fortschritt bei nie endender Namespace-I/O oder vertragswidrig endlosen Callbacks.
Der bestehende Namespace-Drain muss auf solche laufende Arbeit weiterhin warten.
Daraus wird keine neue allgemeine Maintenance-/Nativefreigabe abgeleitet.

Ein Zwischenlauf hatte eine Testseam falsch angebunden: Nach Ergänzen des zweiten
optionalen Lambda-Parameters band Kotlin die bisherige trailing lambda an den
post-P-Beobachtungspunkt. Die strenge Null-Assertion wurde nicht verändert;
explizites `probe = …` stellt wieder die geforderte Pause vor P her.
Der anfängliche RED auf der Basis war korrekt, und der erste Fixlauf mit dem
unveränderten einzelnen RED war bereits grün. Der zwischenzeitliche Harnessfehler
wurde nicht als zusätzliche Produktlücke oder erfolgreicher Nachweis ausgegeben.

### 60.5 One-Shot-, Read- und Sessionregressionen

**BESTEHEND / HOSTGETESTET:** started.compareAndSet und delivered.compareAndSet
bleiben unverändert. Vorhandene Tests prüfen Worker/Main wiederholt und tatsächlich
parallel, beide zusammen, einmaliges Blocked nach Fehler, keine Wiederholung nach
Teilfortschritt, Dispatchfehler und verlorenen Main-Runnable. Context verweigert
bzw. führt Callback aus und liefert false: kein zweiter Versuch und keine
Behauptung rückgängig gemachter UI-Sideeffects.
Fence vor Worker/vor Main/beim Context-Gate, Revoke/Generation/BLOCKED und gültige
Summary-/Listen-/Rejection-Rückgaben bleiben abgedeckt. Die bisherige Harness
braucht nur die explizite Constructorbindung für blocked; ihre Assertions wurden
nicht abgeschwächt. Ein Read bleibt genau ein Read, keine zusätzliche Datei-/DB-
Arbeit durch die neue Admission. PDF-/TEXT-/IMAGE-DocumentReadDispatch bleibt
unverändert; dieser Slice gibt seinen gesonderten Ressourcenpfad nicht neu frei.

Die neue Harness nutzt den echten SessionCoordinator, synthetische Auth/Clock,
kontrollierte Executorqueues und ausschließlich simulierte physische Wirkung.
Auch der neue Maintenancecallback ist ein Scope-/Ordering-Test, kein nativer
Reconciliationlauf. **NICHT BEWIESEN:** SQLite-/SQLCipher-Commit, Dateisystem-
Dauerhaftigkeit oder nativer Cursor-/Key-Cleanup aus diesen Ledgerwerten.
Verweigerte Publication ändert einen vorherigen physischen Schritt nicht.

### 60.6 Weiterhin offene Grenzen

**UNWIRED:** Keine DOCUMENT-Writerfamilie am Runtime-Invalidierungsport,
keine WorkspaceWriteBoundary über den gesamten Import, kein globaler COMMITTED-
Beleg aus DocumentResult.Value. Mehrphasige DB-/Datei-/Inventar-/Cleanup-Evidence
bleibt #35. Kein Snapshotowner (#37), keine RAM-Hits oder Navigation-/Performance-
Integration (#38), keine allgemeine Editor-/CAS-/ABA-Freigabe (#36).

**BLOCKER:** RED-4 aus §59 bleibt unverändert: rootloses WRITING ist historisch
mehrdeutig und bleibt fail-closed BLOCKED. Keine Joblöschung, Root-Rekonstruktion
oder Recoveryheuristik. Native Cursor-/Cleanup-Grenze #32 bleibt offen;
`SQLCipherStatement.close()` schließt den Cursor nicht explizit.
Die konkret definierte Publication-/Fence-Zulassungsordnung ist implementiert,
keine pauschale Dokument-/Recovery-/Release-1.0-Freigabe.

Produktänderungen nur im Dispatch und im kleinen Lane-Metadatenzugriff;
DocumentImportStaging.stage, Reconciliation, Maintenance, Publication, Delete,
SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink/B2Driver,
Schema 11, Fristen 180 s absolut / 30 s READY-idle, 8+8-MiB-Budget sowie Root-,
Symlink-/Hardlink-, CREATE_NEW-, No-Replace-, Verschlüsselungs- und Sync-Regeln
bleiben unverändert. Keine zusätzlichen Dependencies, Permissions, Keys,
Persistenzorte, Geräteinstallation, Änderung installierter Apps, Corewriter-
oder P3b-/Leadershipänderung.

### 60.7 Abschlussvalidierung

**HOSTGETESTET:** Gezielter Lauf
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --tests '*Session*Test' --offline`:
**379 bestanden, 0 Failures, 0 Errors, 0 Skips**.
Vollständiger angeforderter Offline-Lauf mit
`:app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest
:domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin
:app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest
:app:verifyDemoManifest --offline`: **BUILD SUCCESSFUL**.
Debug-Test-XMLs: App 671, Data 958, Security 4 = **1.633 bestanden,
0 Failures, 0 Errors, 0 Testskips**. Security war UP-TO-DATE, Domain hat keine
eigenen Testquellen. Gradle-Lifecycle-/Konfigurations-Tasks mit SKIPPED sind keine
übersprungenen Testfälle. Beide Androidtest-Kompilationen und beide Manifestchecks
erfolgreich. Keine Instrumentation/Installation, keine neue native
Ressourcen-/Crashprüfung. `git diff --check` sauber. Keine Rohlogs, APKs,
Datenbanken oder Schlüssel im Commit. Regulärer WIP-Commit/Push; danach STOPP
bis zum unabhängigen GitHub-Review.

## 61. Prävention rootloser WRITING-Zustände

### 61.1 Basis, Umfang und Ergebnis

Issue #35 Writer/Recovery; verbindliche Basis
`0e5b0a7b69afa35fb5c97732337216f0a73bab24`. Lokaler HEAD und GitHub-Remote-Ref
(`gh api repos/JulianGKK/action-planner/git/ref/heads/p11-performance-optimization`)
waren vor Änderungen identisch; Branch korrekt, Arbeitsbaum sauber.
Die SSH-Abfrage `git ls-remote` lieferte zunächst keine Antwort; der unabhängige
GitHub-API-Read bestätigte die Basis. Geprüft wurden §§57–60, P10b1 und der
ursprüngliche P10b2c1-Stagingvertrag sowie Staging-/DB-/DAO-/Job-, Inventory-,
Lane-, Reconciliation-, Maintenance-, Repository-/Runtime- und Publicationpfade
mit ihren bestehenden Hosttests.

**BESTEHEND:** Status quo bleibt produktiv unverändert. **HOSTGETESTET:**
Unterbrechungszustände des echten Stagingcodes und eines ausdrücklich nur im
Test vorhandenen Directory-Vorbereitungsmodells. **VORGESCHLAGEN / NICHT BEWIESEN:**
Eine vollständige, identitätsgebundene und synchronisierte Struktur vor WRITING
kann das spezielle Fenster „WRITING zurückgekehrt, Root noch nie angelegt“
reduzieren. Sie beseitigt weder späteren Rootverlust noch die Teilrootfenster der
Initialisierung. Mit unveränderter Recovery ist keine Variante für sämtliche
Erstimport-Zwischenzustände als verfügbarkeitswahrend nachgewiesen.
Kein konzeptioneller Vertrag ist hier implementiert oder freigegeben.

### 61.2 Tatsächliche heutige Ordnung und Autorität

Alle Schritte laufen unter derselben prozesslokalen Namespace-Lane und einem
READY-Context derselben Generation. OPERATION-/STEP-/COMMIT-Gates autorisieren
jeweils nur den nächsten Schritt; ein zurückgekehrter Check sperrt Revoke nicht
über I/O hinweg. Jede DB-Methode des SQLCipher-Adapters endet ihren eigenen
Room-/DB-Keyscope vor Rückkehr. Der kurzlebige Dateischlüssel bleibt dagegen im
`withNewKey`-Scope bis zum finally-Wipe. Kein gemeinsamer DB-/Filesystemcommit.

| Nr. / Schritt | Autorisierung, Scope und zurückgekehrter DB-Schritt | Filesystem / Recovery-Evidence | Revoke / Prozessabbruch / nächste erlaubte Arbeit |
| --- | --- | --- | --- |
| 1 DB-Inventar | OPERATION, READY, Name/Action; eigener autorisierter DB-/Keyscope, lesender Snapshot | kein Namespacezugriff; bekannte files/staging-Namen, globale DAO-Bindungen; unvollständige Jobs blockieren | Abbruch hinterlässt keinen neuen Job; danach nur Keyerzeugung bei weiter gültigem Context |
| 2 neuer Dateischlüssel | `withNewKey`, bestehende Streaming-/Sessiongates; kein offener DBscope | keine Datei; DEK zunächst nur flüchtig, keine Root-Evidence | Revoke/Abbruch vor Reserve erzeugt keinen Job; dann geprüfte Reserve erlaubt |
| 3 IMPORT_RESERVED | eigene WriteTransaction, vollständige DAO-/Kapazitäts-/Actionprüfung und COMMIT-Gate; Rückkehr beendet DB-/Keylease | Job mit DEK/UUID-Bindung, Größe null; weiterhin keinerlei Namespacezugriff | Revoke danach erhält RESERVED; bei unklarer Rückkehr tatsächlichen DB-Zustand frisch lesen; nächster Schritt nur Expected-Advance |
| 4 IMPORT_WRITING | STEP vor/nach separatem `advance(reserved,null)`; Expected inkl. Key, Actionstatus, Phasen-WHERE und COMMIT | zurückgekehrter WRITING-Schritt, aber Root kann fehlen; Größe null belegt keine Nichtausführung | Revoke/Crash unmittelbar danach erzeugt §59-H0; nur bei gültigem Gate Initialisierung/Inventar erlaubt |
| 5 Namespaceinitialisierung | DBscope bereits zu; bei vor Reserve leerem Inventar `checkInitial`, sonst exaktes `requireInventory` | ggf. mkdir root, staging, files; nach jedem Directorycheck; abschließendes Inventar; KEIN Directory-/Root-/Parent-Sync | nach jedem mkdir Teilroot möglich; kein Rollback/Unlink; danach erst zulässiges CREATE_NEW |
| 6 CREATE_NEW | STEP; `FileChannel.open(CREATE_NEW,WRITE,NOFOLLOW_LINKS)`; anschließend STEP und staging-Directorycheck | genau neues Leaf, noch keine Source; Root-/Leafdauerhaftigkeit nicht durch Open bewiesen | Abbruch lässt WRITING mit leerem Leaf; kein Retry/Überschreiben; nach Gates einmalige Sourceöffnung |
| 7 verschlüsseltes Schreiben | keine DB-/DB-Keylease; eine Sourcefactory, Tink mit documentId-AAD, begrenzte Buffer und Gates | nur Ciphertext, ggf. Teilcipher; WRITING bleibt; keine Klartextdatei | Revoke/Sourcefehler kann Teilcipher erhalten; physischer finally-Close darf enden, autorisiert kein CLOSED |
| 8 FileChannel.force | Tinkfinalisierung/flush → `force(true)` am offenen Channel → close; DB weiter WRITING | zurückgekehrter Fileforce ist kein Sync des Leafnamens, Root oder Parents; keine eigene persistierte Syncphase | nach erfolgreichem Close vor nächstem DBcommit bleibt WRITING; bestehende Recovery verwirft auch authentifizierbare unpublizierte Datei |
| 9 IMPORT_CLOSED | STEP, exaktes Inventar inkl. staging; separater Expected-Advance mit Größe, Action-/COMMIT-Gates; DB-/Keyscope endet | CLOSED samt Größe behauptet Streamabschluss; Publication prüft später erneut vollständig | Revoke nach Commit erhält CLOSED ohne UI-Erfolg; bei unbekannter Rückkehr neu inventarisieren; erst dann bestehende Publication, kein Source-Reopen |

**BESTEHEND:** Rootloses WRITING ist nach Schritt 4 und vor Schritt 5 erreichbar,
auch ohne jede Sourceöffnung. Erfolgreicher Methodenreturn, sichtbarer Hostzustand,
zurückgekehrter Sync und SQLitecommit sind verschiedene Belege. Ein Fehler nach
einem Commit behauptet keinen Rollback früherer Schritte.

### 61.3 Warum die RESERVED-/WRITING-Grenze relevant ist

Der ursprüngliche Vertrag in `product-p10b2c1-documents-import-staging.md`
(Komponenten/Reihenfolge und Rootanlage) bindet alle Dokumentdateioperationen an
die dauerhafte Schreibabsicht; Reserve darf wegen Action-/Kapazitäts-/Commit-
Ablehnung keine Datei erzeugen. Zusammen mit der Sourcegrenze ermöglicht dies
den bestehenden RESERVED-Abort ohne Datei-Cleanup. Das ist der dokumentierte
Sicherheitszweck; eine weitergehende historische Autorenabsicht wird nicht
unterstellt. Heute sind vor WRITING auch Namespaceprüfungen, mkdir, Leafanlage,
Schreiben, Rename und Unlink im Stagingpfad ausgeschlossen.

Leere Verzeichnisse enthalten keinen Dokumentklartext und sind deshalb enger
als ein Stagingleaf zu bewerten. Ihre Anlage ist dennoch eine persistente
Mutation: Teilroot kann nicht von beschädigtem Altbestand unterschieden werden.
Eine Ausnahme benötigt ausdrücklich freigegebenen Zweck, globale Bestandsprüfung
vor Reserve, aktuelle READY-/Generationgates vor/nach jeder Operation, dieselbe
Lane, vertrauenswürdige kanonische Base, erlaubte leere Verzeichnisse, gebundene
Identitäten und Syncgrenzen sowie eine bewertete Recovery für jeden Teilzustand.
Allein „nur mkdir“ oder eine neue Codeposition erteilt diese Erlaubnis nicht.

Unverändert zwingend: `DocumentSource.open()` höchstens einmal, erst nach
zulässigem WRITING und erfolgreichem exklusivem CREATE_NEW samt Folgechecks;
keine Source in Vorbereitung/Recovery, kein Klartext auf dem Dateisystem.

### 61.4 Variantenvergleich

Die nachfolgende Matrix gilt zusätzlich zu ALLEN Zwischenzuständen in 61.5.
„Reduziert“ setzt gültige Bindung, erfolgreiche erforderliche Syncs und den
Ausschluss externer Entfernung zwischen Prüfung und Commit voraus; keine
uneingeschränkte Garantie.

| Variante | Rootloses WRITING / neue Crashfenster / Commitfolge | Sync, Recovery, Historie | Bindung, Revoke, Maintenance, Bestand/Mehrfachimport |
| --- | --- | --- | --- |
| A heutige Folge | verhindert H0 nicht; RESERVED ohne Root, WRITING ohne/mit Teilroot erreichbar; Reserve und WRITING getrennt zurückgekehrt | kein Initialisierungs-Directorysync; bestehende fail-closed Recovery unverändert kompatibel, Legacy unverändert | heutige Gates/Lane/Inventare; Erstimport erlaubt; Folgeimports erhalten bekannte Documents/CLOSED; keine neue TOCTOU-Zusage |
| B Reserve → vollständiger Namespace → WRITING | H0 bei bestätigter haltbarer Anlage reduziert; neuer RESERVED+Teilroot nach jedem mkdir, Fehler oder Revoke; auch Commitfehler lässt RESERVED+Root | staging/files/root/Parent-Sync vor WRITING nötig; Teilroot BLOCKED, vollständiger Root RESERVED abbrechbar; keine neue Rekonstruktion; Legacy-WRITING bleibt mehrdeutig | ändert ausdrücklichen Vor-WRITING-Vertrag; Base-/Root-/Unterdirectoryidentität und Rechecks erforderlich; Lane bleibt über kurze DB- und Dateischritte, Sessionmonitor nicht; Bestandsfall nur Inventar/Sync, niemals fehlende Struktur ergänzen |
| C Namespace vor Reserve | H0 ebenfalls nur bedingt reduziert; zusätzliche Dateioperationen selbst bei späterer Reserveablehnung; Teilroot jetzt ohne Job | gleiche Syncpflicht; nackter Teilroot BLOCKED; kein Job belegt Zweck/Anlage; Fehler nach Sync vor Reserve lässt vollständigen leeren Root | noch größere Vertragsänderung; Revoke/Capacity-Rejection hinterlassen Artefakte ohne Job; Erstimport/Mehrfachimport benötigen exklusive alte Inventare; vorhandene Documents dürfen keine Rekonstruktion legitimieren |
| D neue dauerhafte Evidence | separate gebundene PREPARE-/Namespace-Evidence könnte Absicht erklären; Marker vor mkdir lässt Teilroot, Marker nach mkdir lässt unmarkierten Root; bloßes Bit löst Reihenfolge nicht | braucht eigene crashgeordnete Update-/Sync-/Cleanupsemantik und ggf. Migration; neue Phase in Schema 11 hier verboten; kein Beleg für Legacy | muss Namespace-/Job-/Versionidentität und Mehrfachimport binden, unverändert globale Integritätsprüfung/Revoke/Fence; keine zweite Sessionautorität; extra Persistenz- und Manipulationsrisiken separat reviewen |
| E ohne Migration: nur vorhandenen vollständigen Root vor WRITING prüfen/syncen, keine Initialisierung im Import | vermeidet eigenes mkdir-Fenster bei diesen Imports; kein universeller Schutz gegen späteren Verlust; fehlender/partieller Root vor WRITING ablehnen | kein neuer Teilroot durch Import; RESERVED-Abort bleibt möglich; Erstanlage braucht separaten autorisierten, crashsicher bewerteten Vertrag und ist damit ungelöster Blocker; Legacy unverändert | engster möglicher Teilfix für bestehenden vollständigen Bestand; verschärft Vor-WRITING-Grenze ausdrücklich nur um Read/Sync; Erstimport ohne Root wäre gesperrt, daher kein allein ausreichender Produktfix |

Für B/C/D ist die Erstinitialisierung nicht als sicher abgeschlossen bewertet,
solange Teilroot-Recovery und native Syncgrenzen offen sind. E ist nur ein enger
Reviewkandidat für bereits vollständige Ablagen, keine Freigabe, erste Imports
still abzuschalten. A bleibt implementiert. Keine Variante rechtfertigt
unbekannte Leaves zu entfernen, Symlinks zu folgen oder Hardlinkkollisionen zu
akzeptieren. NOFOLLOW am Leaf schützt keine ausgetauschten Parentkomponenten;
kanonische Namen allein beweisen keine langlebige Namespaceidentität.

### 61.5 Vollständige Zwischenzustandsmatrix

Alle Recoveryentscheidungen setzen frische globale DB-/Filesysteminventare,
gültige MAINTENANCE-Autorität, erfolgreiche DB-/Keycleanupschritte und das finale
Inventar voraus. READY bedeutet ausschließlich mögliche Freigabe durch die
bestehende Sessionbarriere. Revoke führt ggf. LOCKED statt BLOCKED herbei.
Sichtbare Verzeichnisse sind keine persistierte Historienevidence.

| Zustand | Erreichbarkeit / vorhandene Evidence | Unveränderte sichere Recovery / READY oder BLOCKED / Verlust |
| --- | --- | --- |
| A Root fehlt | heute vor mkdir; B nach Reserve vor Vorbereitung, C vor Vorbereitung; DB kann leer/RESERVED/WRITING sein | leer: READY möglich; nur RESERVED: geprüfter Abort/READY möglich; WRITING oder referenzierter Bestand: BLOCKED. Fehlende Bytes nicht rekonstruieren |
| B nur Root | heute Crash nach erstem mkdir mit WRITING; B mit RESERVED; C ohne Job; auch externer Schaden | globaler Teilroot: BLOCKED unabhängig von Jobphase; kein Ergänzen/Abort. Keine legitime irreversible Löschung |
| C Root+staging | heute nach zweitem mkdir; B RESERVED; C joblos; auch verlorenes files | BLOCKED, keine Rootrekonstruktion; mögliche fertige verlorene Dateien bleiben als Schaden erkennbar |
| D Root+files | aus heutiger frischer mkdir-Reihenfolge nicht allein durch Prozessabbruch erreichbar; vorhandener Teilroot, externer Verlust oder unbewiesene Powerlossreihenfolge möglich | BLOCKED auch bei RESERVED; Dateien und DBbelege erhalten, kein staging-mkdir |
| E vollständiger leerer Root | heute nach drittem mkdir, B/C nach Vorbereitung, auch vor jedem Sync; keine Jobphase allein belegt Directorydurability | ohne Job READY möglich; mit RESERVED wie H; WRITING wie J; Sichtbarkeit bestätigt keine überlebte Crashpersistenz |
| F vollständiger Root mit Documents | normaler Folgeimport/bestehender Bestand; DB-Namen und Keys plus aktuelle Leaves | nur exakt gebundener Bestand erlaubt READY; fehlendes/unklares Leaf BLOCKED; kein Vervollständigen oder Löschen fertiger Documents |
| G Teilroot+RESERVED | neu bei B, C nach späterer Reserve falls Prüfung fehlt; heute extern vor Reserve möglich, nachfolgendes Revoke/Commitversagen hält RESERVED | BLOCKED vor jedem Abort; sicherheitlich kein harmloser RESERVED-Zustand. Ohne Vertragsänderung keine automatische Reparatur |
| H vollständiger Root+RESERVED | B nach Vorbereitung, C nach Reserve, heute bereits vorhandener kompletter Root und Revoke vor WRITING | bei fehlendem eigenen staging/final und gültigem Restinventar nur Job aborten, READY möglich; keine Source, kein Unlink; neue Syncgeschichte wird nicht aus DB abgeleitet |
| I Teilroot+WRITING | heute nach mkdir-Unterbrechung, auch Fremdschaden; B nur bei Verlust/fehlerhafter Vorbedingung/fehlender Durability | BLOCKED, Job/Keys erhalten; keine nachträgliche Umdeutung zu sicherem Nichtstart |
| J vollständiger Root+WRITING ohne Datei | heute vor CREATE_NEW; B nach WRITING, Fehler beim CREATE_NEW; frühere Recovery nach Unlink | staging sync, Recheck, geprüfter Jobabort; READY möglich. Kein Beweis „nie geschrieben“, keine Sourcewiederöffnung |
| K WRITING+Teilcipher | echte Schreib-/Revoke-/Streamfehler oder Unterbrechung; bekannte staging-Bindung | genau bekanntes reguläres staging unlink, sync, recheck, dann abort; READY möglich. Unpublizierte Bytes werden bewusst verworfen, keine Wiederaufnahme |
| L WRITING nach Cipherclose vor CLOSED | heute nach Force/Close, vor/bei gescheitertem CLOSEDcommit; Größe in DB weiterhin null | gleiche Abbruchregel wie K, auch bei gültigem Ciphertext. Verlust eines unbestätigten Imports möglich; niemals allein wegen Authentifizierbarkeit publizieren |
| M Root zwischen Prüfungen ausgetauscht | externer Eingriff, keine reguläre PaG-Laneoperation; fileKey-Wechsel innerhalb Inventarrecheck erkennbar | erkannte Änderung BLOCKED; kein Cleanup. Austausch zwischen vollständig getrennten Inventaren mit identischem Inhalt ist nicht durch globale langlebige Root-ID bewiesen; Restore derselben Pfadnamen ist keine Identität |
| N vollständig angelegter Root später weg | Fremdverlust oder unbewiesene Crashpersistenz; ursprünglicher Sync/Commit kann zurückgekehrt sein | WRITING bleibt BLOCKED, auch nach künftigem Präventionsfix. Bei Documents droht realer irreversibler Byteverlust; Joblöschung würde zusätzliche Key-/Schadensevidence entfernen |

Zusätzliche Übergänge: Fehler vor mkdir hinterlässt A; Fehler während mkdir kann
A oder den nächsten sichtbaren Teilzustand hinterlassen, bei unbekanntem Ergebnis
nur neuer autorisierter Read. Syncfehler ändert keine DBphase; nach letztem mkdir
ist der Hostzustand E/H, ohne dass Crashdurability folgt. Fehler nach erfolgreichem
Sync vor WRITING bleibt H; zurückgerollter WRITINGcommit ebenfalls H, unbekannter
Commitabschluss verlangt neuen Read (H oder J). Revoke nach WRITING vor CREATE_NEW
bleibt J, sofern Root erhalten ist. Lane/Fence verhindern alte eigene Workers,
aber keinen privilegierten externen Rootaustausch oder Dateisystemschaden.

### 61.6 Filesystem-Durability und Identität

**BESTEHEND:** `Files.createDirectory` legt genau eine Komponente an; erfolgreicher
Return und Hostsichtbarkeit garantieren keine Crashpersistenz des Parent-Eintrags.
Staging synchronisiert den Dateiinhalt/Metadaten mit `FileChannel.force(true)`;
dies ersetzt weder staging-Directorysync noch root-/Parent-Directorysync.
Publication synchronisiert später files und staging, was keine rückwirkende
Bestätigung der ursprünglichen Rootanlage vor WRITING ist.

**VORGESCHLAGEN:** Für eine neue vollständige Struktur vor einem DB-Phasenwechsel
müssten mindestens staging und files (eigene Directorymetadaten), dann root
(Einträge beider Kinder), dann die vertrauenswürdige noBackup-Base als Parent des
Roots synchronisiert und Fehler weitergereicht werden. Ist auch dieser Parent
neu, reicht dessen Sync für seinen eigenen Eintrag nicht: weitere vertrauenswürdige
Parentkette oder eine separat belegte dauerhafte Plattformvorbedingung nötig.
Bei vorhandenen Verzeichnissen sind Bestands-/Identitätsprüfung und passend
bewertete Syncs nötig; nicht blind neu erstellen. Danach frisches exaktes Inventar,
Contextgate, Expected-WRITINGcommit, anschließend erneute Bindungsprüfung vor
CREATE_NEW. Datei-sync nach Verschlüsselung bleibt zusätzlich erforderlich.
Die konkrete minimale Androidsequenz ist **NICHT BEWIESEN**; dieser Absatz ist
kein Android-fsync-Fix und kein universeller POSIX-/Powerlossbeweis.

Kanonische absolute normalisierte Pfade und NOFOLLOW-Attribute müssen vor/nach
Operationen gelten. `DocumentFileInventory` vergleicht fileKey/Größe/mtime innerhalb
einer Inventarisierung, prüft bei Recovery nicht-null Leaf-fileKeys auf Kollision;
Staging-`checkDirectory` allein bindet keine dauerhafte Root-ID. Für den neuen
Vorbereitungspunkt sind nicht-null Root-/Base-/Unterdirectoryidentitäten über
Prüfung, Sync, Commit und CREATE_NEW oder ein separat reviewed descriptorgebundener
Mechanismus nötig; Leafidentität vor/nach Nutzung weiter prüfen. Fehlende fileKeys
nicht optimistisch als Beweis behandeln. Hardlinkduplikate zwischen bekannten
Leaves bleiben verboten; die bestehenden Checks sind keine universelle Aussage
über privilegierte externe Links außerhalb des inventarisierten Namespace.
Eine Recheckfolge reduziert erkannte Races, schließt TOCTOU gegen austauschbare
Parentkomponenten jedoch nicht atomar. Kein Java-Path-Aufruf ist eine gemeinsame
Transaktion mit SQLite.

Beweisstufen: (1) Java-Aufruf zurückgekehrt; (2) Datei am Host sichtbar;
(3) File-/Directoryforce zurückgekehrt; (4) SQLite-/Roomcommit zurückgekehrt;
(5) Bytes und Namespace über tatsächlichen Crash/Powerloss erhalten. Dieser Slice
belegt ausgewählte (1)–(3) am Host mit Fake-DB; nicht (4) für die neue Folge und
nicht (5). Keine neue native Dauerhaftigkeitsbehauptung.

### 61.7 Deterministische Hostbelege und Grenzen

Die bestehende `DocumentReconciliationTest`-Harness nutzt echte Hostdateien,
echte DAO-Regeln, simulierten Transaktionsrollback und echte Recovery. Die neue
`candidateEmptyDirectories`-Funktion ist ausschließlich ein Testmodell für B:
keine produktive Autorisierung zur Vor-WRITING-Anlage. Sie stoppt nach jedem der
drei mkdir und jedem der vier Host-Directoryforces. Kein Stagingleaf/Sourceaufruf
ist Teil dieses Modells. Assertions prüfen Ereignisfolge, sichtbare Struktur,
RESERVED-Erhalt, Recovery-Aborts/Unlinks/Syncs und echte Session-BLOCKED-/READY-
Entscheidung. Recovery erzeugt dabei keine Verzeichnisse; nur das Testmodell
zählt seine mkdir/Sync-Rückkehrpunkte. Ein späterer echter autorisierter
Maintenance-/Unlockflow liest frisch und abortet nur erlaubte vollständige Fälle.

Weitere neue Tests: mkdir-Kollision durch ein erhaltenes fremdes Leaf,
injektierter Fehler beim ersten Directorysync (ein Versuch, null bestätigte Syncs),
Fehler nach erfolgreichem Parent-Sync, verweigerter WRITINGcommit mit Fake-Rollback,
WRITING vor CREATE_NEW, Rootverlust nach vollständig zurückgekehrter Vorbereitung,
RESERVED mit files-only-Teilroot sowie Rootaustausch innerhalb des Inventarrechecks.
Für den Austausch bleiben alter und neuer Testroot erhalten; keine Recoverymutation.
Die Fehler sind kontrollierte Exceptions/Revoke, keine gestarteten/getöteten
Prozesse oder Stromausfälle. Kandidaten-Sourceöffnungen sind strukturell null,
nicht ein Ausführungstest einer zukünftigen Produktpipeline.

`DocumentImportStagingTest` ergänzt den **echten unveränderten Stagingpfad**:
Revoke nach jedem sichtbar gewordenen mkdir, null Sourceöffnungen/Fileforces,
WRITING bleibt; Commitverweigerung vor WRITING erhält RESERVED ohne Root;
zwei konkurrierende Imports werden über Latches an der ersten Filesyncgrenze
geordnet, der zweite kann vor Freigabe nicht abschließen und beide CLOSED-
Stagings bleiben anschließend erhalten. Die Accesscheck-Injektion ergänzt nur
das Testdouble. Bestehende Tests prüfen bereits Revoke vor/nach Reserve und nach
WRITING, kompletten/partiellen Root, unbekannte Leaves/Symlinkroot, Source-/File-
Sync-/Closefehler, abgeschlossenes Cipherstreaming vor gescheitertem CLOSEDcommit,
bestehende Documents/CLOSED-Stagings, Maintenance-Drain/alte Tickets und spätere
frische Unlockgeneration. Die bestehenden §59-Ununterscheidbarkeitstests bleiben
maßgeblich für Legacy und späteren Rootverlust.

| Beweisstärke | Stand dieses Slices |
| --- | --- |
| Fake-DB + echte DAO-Regeln | HOSTGETESTET, einschließlich Rollbacksimulation, keine Roomengine |
| echte temporäre Hostdateien + Tink/Channel-/Directoryforce | HOSTGETESTET an expliziten Grenzen; kein Powerlossbeweis |
| echte SQLite für neue Präventionsfolge | NICHT BEWIESEN; kein neuer SQLite-Testlauf dieser Folge; historische SQL-Skripte sind kein Ersatz |
| native SQLCipher-/Room-/Keyscope-/Directory-fsync-Prüfung | NICHT BEWIESEN; Androidtests ausschließlich offline kompiliert |

### 61.8 Begrenzter späterer Patchvorschlag und offene Blocker

**VORGESCHLAGEN, kein Implementierungsauftrag:** Zuerst die explizite Ausnahme
für ausschließlich geprüfte Read-/Sync-Arbeit am bereits vollständigen Root
reviewen (E). Nur dort nach Reserve und vor `database.advance(reserved,null)`
das vorhandene vollständige globale Inventar samt gebundener Identität prüfen
und erforderliche Directorysyncs bestätigen; danach Expected-Advance unverändert.
Den bisherigen `initialize`-Aufruf keinesfalls lediglich vor Advance verschieben.
Fehlender/partieller Root würde vor WRITING abgewiesen; der dafür nötige sichere
Erstinitialisierungsvertrag ist vor Produktfreigabe separat zu lösen.

Falls stattdessen B gewählt wird: exakt den heutigen `initialize`-/Bestands-
Prüfblock zwischen zurückgekehrtes Reserve und WRITING verschieben, aber erst
nach Freigabe der neuen Vor-WRITING-Ausnahme, Sync-/Identitätsmechanik und der
Teilrootpolitik. Neue Zwischenstände G/H sind verpflichtend Teil des Vertrags;
G bleibt mit heutiger Recovery BLOCKED, H darf ausschließlich wie bisher aborten.
Kein Catch mit Rekonstruktion, Joblöschung oder Rückstufung als Kompensation.
Solange dieses neue G-Verfügbarkeitsfenster nicht akzeptiert oder sicher gelöst
ist, kein solcher produktiver Patch. C und D sind dafür keine kostenlosen Auswege.

Erforderliche spätere strenge RED-Tests gegen den echten Produktpfad: fehlender
Root darf beim E-Versuch nicht WRITING werden; vor-WRITING-Namespacezugriff braucht
die freigegebene enge Ausnahme; jeder mkdir-/Syncfehler und Revoke vor/nach
Operation/Commit; nie Source vor zulässigem WRITING/CREATE_NEW; Roottausch vor/nach
Sync und Commit; unbekannte Leaves, Symlink-/Hardlinkkollision; alter Ticketfence,
Mehrfachimport, vorhandener Bestand und leere Erstablage. Soll G künftig READY
werden, ist das heute ein RED gegen die bewusst unveränderte Recovery und
benötigt eine separate neue Sicherheitsentscheidung, keine abgeschwächte Assertion.

Androidbelege vor Freigabe: tatsächliche Room-/SQLCipher-Commit-/Cleanupgrenzen,
Base-/Root-/Leafidentität und NOFOLLOW auf Zielplattform, native Directory-/Parent-
Syncfehler und deren Weitergabe, Crash-/Powerloss-Zwischenzustände inklusive
mkdir/Sync/Commit, sichere Fixtureisolation und überprüfbare Bereinigung. Diese
Belege sind hier nicht erhoben; keine Instrumentation, Installation oder Änderung
installierter Apps und keine pauschale Fixturelöschung. Issue #32 bleibt offen.

**BESTEHEND:** Historische Schema-11-WRITING-Jobs bleiben §59-mehrdeutig. Neue
Codeordnung ist kein persistierter Versionsbeleg und kein Reparaturbeweis für
alte Jobs. Selbst neue Imports können durch späteren Rootverlust N erreichen.
Keine Änderung an globalem Inventar, unbekannten Leaves, Symlink-/Hardlinkregeln,
Rootrekonstruktion, Source-Reopen oder READY-Barriere.

**UNWIRED / BLOCKER:** #35 mehrphasige Dokument-Outcome-/Recovery-/Writeranbindung;
#36 Editor/CAS-/ABA-Grenzen; #37 Snapshotowner; #38 Performance-/RAM-Integration;
#32 native Cursor-/Cleanup- und Fixturegrenzen. Der §60-Admission-CAS und die
Core-Mutationsepoche bleiben verschiedene Verträge. DOCUMENT wird nicht im Runtime
verdrahtet, DocumentResult.Value ist kein globaler COMMITTED-Beleg, keine einzelne
confirmedWorkspaceTransaction um die Pipeline und keine zweite Sessionautorität.
SessionCoordinator, StorageAccess, SessionDbKeyOwner, SQLCipher/Tink/B2Driver,
Schema 11, Fristen 180/30 Sekunden, 8+8-MiB-Budget, Lane/Dispatch, Publication,
Delete, Reconciliation, Runtime/Repository, sämtliche Corewriter und P3b bleiben
unverändert. Keine neue Dependency/Permission, kein RAM-Hit, Produktupdate oder
Release. Nur zwei bestehende Hosttestdateien und dieses Assessment geändert.

### 61.9 Abschlussvalidierung

**HOSTGETESTET:** 17 neue Testmethoden (darunter der echte Staging-Revoke nach
jedem der drei mkdir in einer Testmatrix). Gezielter Lauf
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --tests '*Session*Test' --offline`:
**396 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESSFUL**.
Der erste Sandboxstart konnte die Gradle-Wrapper-Lockdatei im vorhandenen Cache
nicht öffnen; der autorisierte Lauf mit Cachezugriff war erfolgreich.

Anschließend vollständig ausgeführt:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`:
**BUILD SUCCESSFUL**. Debug-Test-XMLs: App 671, Data 975, Security 4 =
**1.650 Tests, 0 Failures, 0 Errors, 0 Testskips**. Data wurde vollständig ausgeführt;
unveränderte App-/Securitytests und Androidtest-Kompilationen waren UP-TO-DATE.
Domain hat keine eigenen Testquellen. Gradle-Konfigurations-/Lifecycle-Tasks mit
SKIPPED sind keine übersprungenen Testfälle. Beide Manifestchecks erfolgreich.
Bestehende Assertions unverändert stark, keine Testskips ergänzt.
`git diff --check` sauber; Arbeitsbaum enthält ausschließlich die drei benannten
Dateien. Keine Logs, Builds, DBs oder Schlüssel im Commit. Regulärer WIP-Commit
und Push auf `p11-performance-optimization`, unabhängige Remote-SHA-Bestätigung;
anschließend **STOPP bis zum unabhängigen GitHub-Review**.

## 62. Document Multi-Phase Evidence and Workspace Invalidation

### 62.1 Basis und unveränderte Produktgrenze

Issue #35 Writer/Recovery; verbindliche Basis
`867365b4c3f9f2a39f0f3a5f5088e0be046cdc47`. Vor Änderungen: lokaler HEAD und
unabhängig per GitHub-API gelesener Branch-Ref identisch, Branch
`p11-performance-optimization`, Arbeitsbaum sauber. Untersucht: §§57–61,
Staging/Publication/Delete mit ihren DB-Adaptern und DAO-Regeln, Repository/
Dispatch, Namespace-Lane, Reconciliation/Maintenance, Runtime, gemeinsamer
Invalidierungsport, WriteBoundary/Progress/Completion sowie Uncached- und
ActiveWorkspace-Adapter/Validity und vorhandene Dokument-/Workspace-Hosttests.
Produktgrundlagen: PRODUCT.md, insbesondere Dokument-, Privacy-, Integritäts-
und Sessiongrenzen; keine neue fachliche oder produktive Architekturentscheidung.

**BESTEHEND:** ProductRuntime injiziert dieselbe `workspaceMutations`-Instanz in
Task/Action/Person/Operations/Material/Expense/Protocol/MemoryReport. Die
WorkspaceWriteBoundary erwirbt READY, invalidiert synchron auf dem Ownerthread
vor `worker.execute`, verweigert Busy ohne Write und bestätigt nur einen beendeten
äußeren Transaktionsschritt mit vollständig zurückgekehrtem Cleanup. UNKNOWN
oder unbestätigtes Cleanup sperrt die betreffende Generation konservativ.
`UncachedWorkspaceMutations` hat eine gemeinsame Mutationsepoche und ein Pending;
`ActiveWorkspaceMutationAdapter`/Validity sind weiterhin nicht im Runtime installiert.

**UNWIRED:** ProductDocumentRepository bekommt diese Portinstanz nicht.
DocumentProductDispatch hat keinen gemeinsamen Pending-Beginn und keine gemeinsame
Epochenänderung. Seine Lane serialisiert Dokumentnamespace/Recovery, nicht die
Corewriter. Der §60-Admission-CAS ordnet die zentrale UI-Zulassung gegen den
Namespace-Fence; er ist kein gemeinsamer Mutationsbeginn. Kein RAM-Hitpfad ist
aktiv. Ein grüner GAP-Test belegt die Lücke, keine funktionierende Invalidierung.

### 62.2 Kleiner Evidence-Entwurf, ausdrücklich nur in Testquellen

**VORGESCHLAGEN / HOSTGETESTET als Modell:** `DocumentEvidenceDraft` in
`DocumentMultiPhaseEvidenceTest.kt` beschreibt genau einen Import- oder
Deleteversuch. Keine Produktklasse, persistierte Phase, neue Sessionautorität,
Runtimeanbindung oder Berechtigung. Feste Enum-Schlüssel und beschränkte Maps,
keine Ereignishistorie mit unbegrenztem Wachstum. Der Producer muss jede Aussage
an der tatsächlichen passenden Grenze liefern; das Modell ermittelt keine
Datei-/DBhistorie aus Jobnamen oder Repositoryresultaten.

| Dimension | Getrennte Aussagen / Bedeutung |
| --- | --- |
| DB | Je Schritt NOT_ATTEMPTED, ROLLED_BACK, COMMITTED oder UNKNOWN; separater belegter Änderungsindikator. Reserve-Import, WRITING, CLOSED, PUBLISHED, Document-Insert+Jobentfernung sowie Reserve-Delete, Detach und Deletejobentfernung haben eigene Slots. Ein späteres Rollback überschreibt keinen früheren Commit |
| Datei | Je CREATE, WRITE, CIPHER_CLOSE, FILE_SYNC, RENAME, SYNC_FILES, SYNC_STAGING und UNLINK: NOT_STARTED, RETURNED oder UNKNOWN. Sideeffect und dessen Sync sind verschiedene Slots; UNKNOWN beim Sync kann neben RETURNED beim Rename/Unlink stehen |
| Inventar | NOT_CHECKED, CONSISTENT, CONTRADICTORY, RECHECKED_AFTER_EFFECT; jeder neue DB-/Dateischritt verwirft die alte finale Bestätigung. Erkannter Widerspruch ist innerhalb dieses Versuchs terminal, keine spätere optimistische Umdeutung |
| Ressourcen | Getrennt DB, KEY, STREAM, SNAPSHOT: NOT_ACQUIRED, CONFIRMED, FAILED_OR_UNCONFIRMED. „Kein Handle erworben“ ist nicht dasselbe wie erfolgreiches Cleanup. Gesamtcleanup nur vollständig, wenn kein erworbener/begonnener Scope unbestätigt bleibt |
| Recovery | NOT_REQUIRED, PERSISTED_JOB, AUTHORIZED_RECOVERY_REQUIRED, BLOCKED. PERSISTED_JOB bezeichnet erhaltene phasenbezogene Evidence und verlangt autorisierte Maintenance; UNKNOWN verlangt diese auch ohne sicher beobachteten Job |
| Publication | NOT_ADMITTED, ADMITTED, CALLBACK_EXECUTED, DENIED, DISPATCH_UNKNOWN; unabhängig von physischem Ergebnis. CALLBACK_EXECUTED setzt beobachtete Admission voraus und ist terminal. Ein Callback ist kein Commit-/Sync-/Cleanupbeleg |
| Gesamtergebnis | NOT_EXECUTED, COMPLETE, RECOVERY_REQUIRED, UNKNOWN. COMPLETE setzt gesamte passende Commitkette, benötigte Dateischritte/Syncs, bestätigtes Cleanup, keinen Job und einen frischen konsistenten finalen Check voraus. Keine automatische Abkürzung aus DocumentResult.Value |

NOT_EXECUTED setzt bewiesene Nichtmutation UND keine unbekannten Schritte voraus.
Ein erfolgreich beendeter read-only Reserve-Transaktionsversuch kann COMMITTED
mit ausdrücklich belegtem `changed=false` sein; er ist kein zurückgerollter
SQL-Schritt. Im Fake bestätigt dies der kontrollierte Ablehnungszweig vor Insert.
Im Produkt wäre auch unbekannte Open-/Migrationwirkung zu beachten:
`WorkspaceWriteProgress.opening()` wird zu Recht UNKNOWN. Fehler vor Körperbeginn
beweisen dort nicht automatisch Nichtausführung. Weder Rowcountvergleich noch
Exceptiontyp allein genügt als nativer Nichtmutationsbeleg.

Eine frühe fachliche Rejection kann deshalb mit NOT_EXECUTED zusammenfallen;
eine Rejection nach WRITING nicht. Im konservativen **Test-Fallback** wird nur
NOT_EXECUTED auf den vorhandenen erlaubten Nichtausführungsfall ROLLED_BACK
abgebildet; alles andere auf UNKNOWN, Cleanup bleibt separat. Auch COMPLETE wird
in diesem Fallback nicht als einzelner Core-COMMITTED-Schritt ausgegeben.
Das ist bewusst verlustbehaftet und keine produktiv angeschlossene Lösung.

### 62.3 Importmatrix: lokale Schritte statt globalem Commit

Alle bestätigten DB-Zeilen bedeuten getrennte zurückgekehrte lokale Schritte,
kein gemeinsames ACID mit Filesystem. Ein Key-/DBscope kann nach zurückgekehrtem
Transaktionsende noch scheitern; dann Commit und Cleanup getrennt bewahren.
Bei unklarer Transaktionsrückkehr bleibt der betreffende Schritt UNKNOWN und
benötigt frischen autorisierten Read. Folgende Aussagen sind Codevertrag bzw.
Hostsimulation, kein neuer nativer Dauerhaftigkeitsbeweis.

| Grenze | DB-Evidence | File-/Sync-/Cleanup-Evidence | Gesamt-/Recovery-/Sichtbarkeitsfolge |
| --- | --- | --- | --- |
| vor Reserve, INVALID | keine Mutationsmethode/kein Insert | kein Rootzugriff/Source | bewiesene Nichtausführung im geprüften Pfad; keine globale Commitbehauptung |
| CAPACITY vor Insert | Reserve kann als leere lokale Transaktion zurückkehren; kein neuer Job | keine eigene Datei/Source; bestehender Bestand unverändert | Rejection kann Nichtmutation belegen, sofern Open-/Scopeabschluss ebenfalls geklärt |
| IMPORT_RESERVED | eigener Commit mit IDs/Keybindung/maximaler Kapazität | vor WRITING noch keine Namespacearbeit (§61 unverändert) | persistierter Job; kein DocumentRow; Snapshot-/Mutationsgültigkeit bereits vor Queue dirty halten |
| IMPORT_WRITING | zweiter eigener Commit; vorheriger Reservecommit bleibt | Root kann fehlen; vor CREATE_NEW Source null | unvollständiger Auftrag, Recovery erforderlich; rootloses WRITING weiter BLOCKED |
| Teilcipher | WRITING unverändert | exklusives Leaf und tatsächliche Cipherbytes möglich; close/sync können fehlen oder trotz Eingabefehler zurückkehren | SOURCE_UNAVAILABLE/TOO_LARGE sind keine Rollbackbelege; kein Source-Reopen |
| Cipherclose und Fileforce zurückgekehrt | WRITING bis zum nächsten DB-Ende | Stream-/Fileabschluss bestätigt, Fileforce ersetzt kein Directorysync | noch kein veröffentlichbarer Auftrag; Revoke vor CLOSED erhält WRITING und Datei |
| IMPORT_CLOSED | dritter eigener Commit mit tatsächlicher Größe | geschlossenes staging; später vollständige Authentifizierung nötig | kein globales COMMITTED, kein DocumentRow; bestehende Recovery kann Publication fortsetzen |
| Rename zurückgekehrt, Sync offen | DB bleibt CLOSED | staging→files physisch erfolgt; Identität/Inventar und zwei Directorysyncs getrennt | Fehler macht Rename nicht rückgängig; UNKNOWN/Recovery; vorhandener finaler Blob allein belegt keinen Sync |
| CLOSED mit finalem Blob | derselbe frühere CLOSEDcommit | mögliche Historie: Rename vor Fehler/Crash, einer oder beide Syncs fehlen | nur bestehende autorisierte Recovery mit vollständiger Verifikation/erneutem Sync, kein erneuter Sourceimport |
| FILE_PUBLISHED | vierter eigener Commit, nach zurückgekehrtem files- und staging-Sync | finaler Blob erneut zu verifizieren; Snapshotkeys scoped | noch kein DocumentRow/kein Gesamtcommit; Fehler danach erhält PUBLISHED |
| DocumentRow eingefügt + Job entfernt | fünfter Commit; diese beiden SQL-Effekte sind lokal atomar | erneute Blobprüfung und Inventar vor diesem DB-Schritt, Snapshots anschließend schließen | regulärer Import physisch abgeschlossen laut bestehendem Pfad; Revoke nach Commit erzeugt keinen Rollback und keinen UI-Erfolg |
| finaler frischer Check nach letzter Mutation | neue autorisierte, globale DB-/Fileinventare ohne Job | tatsächliche files entsprechen Documents, staging leer; Ressourcenende separat | vorgeschlagene starke Gesamtevidence COMPLETE erst hier, kein automatisches Clean von Readprojektionen |

**BESTEHEND / LÜCKE:** DocumentPublication prüft Inventar/Blob vor `finish`, dann
DB-Finish, Gate und Snapshotclose; kein zusätzlicher globaler Inventarread nach
dem letzten DBcommit. Der Modelltest führt diesen letzten Check ausdrücklich
selbst aus. Das ist kein Beleg, dass der heutige Repositoryreturn ihn bereits
enthält. §61-Root-/Directory-Durabilitygrenzen bleiben ebenfalls offen.

### 62.4 Deletematrix und Sichtbarkeit vor Dateientfernung

| Grenze | Lokale DB-Evidence | Datei-/Sync-Evidence | Auswirkung und spätere Fehler |
| --- | --- | --- | --- |
| DELETE_RESERVED | erster Commit mit Expected-Parentbindung | Parent/Blob noch vorhanden | count/list/get schließen Parent bereits wegen Deletejob aus; Dirtybeginn muss vorher liegen |
| Parent entfernt + DELETE_FILE_PENDING | zweiter Commit atomar: Parententfernung und Phasenwechsel | Blob weiterhin vorhanden, keine DB-/Keylease über Unlink | späterer Fehler stellt Parent/Key nicht wieder her; globale ROLLED_BACK-Klassifikation verboten |
| vor Unlink | frischer Snapshot/Expected und vollständiges Inventar | Blob vorhanden oder zulässig bereits fehlend | fehlender Blob nur in vollständig gültigem Namespace und PENDING erlaubt, nicht bei fehlendem Root |
| Unlink zurückgekehrt | DB weiter PENDING | Blobname entfernt, Sync noch offen | irreversible physische Entfernung, kein Directory-Durabilitybeleg; späterer Fehler erhält Job |
| Directorysync zurückgekehrt | DB weiter PENDING | files synchronisiert, auch bei bereits fehlendem Pendingblob | eigener Beleg; nachfolgendes DB-Finish kann scheitern, kein Wiederherstellen des Parents |
| Deletejob entfernt | dritter lokaler Commit | fehlender Blob zuvor im Inventar geprüft, Sync zurückgekehrt | abgeschlossener Deletepfad; nachfolgendes Revoke unterdrückt UI, nicht die Entfernung |
| finaler Check nach Jobentfernung | frisches globales DB-/Fileinventar | keine Jobs/unerwarteten staging; files exakt aktuelle Documents | COMPLETE im Entwurf; Readvalidität bleibt bis autorisiertem Reload dirty |

Auch DocumentDelete hat seinen letzten Inventarcheck **vor** `finish`, keinen
neuen globalen Check danach. Der zusätzliche Check ist im Test/Entwurf explizit,
keine hier implementierte Erweiterung. Physische Flash-Erasure und vollständiger
nativer Ressourcenabschluss werden nicht aus dem fehlenden Blob abgeleitet.

### 62.5 Fachliche Ergebnisse, Session und UI sind unabhängige Dimensionen

**BESTEHEND / HOSTGETESTET:** Die neue Harness verwendet echte Staging-,
Publication-, Delete- und Reconciliationkoordinatoren sowie echte DocumentDao-
Validierung mit einem schmalen speichergestützten Adapter. Die Repository-
Importabbildung (`OperationsResult` und Catch von DocumentInputFailure) wird im
Test ausdrücklich um diese Koordinatoren nachgebildet; keine Androidinstanz von
ProductDocumentRepository und keine künstliche Runtimeintegration.
SOURCE_UNAVAILABLE kommt dabei über die echte `oneAttemptSource`-Abbildung eines
synthetischen IOException, TOO_LARGE über den echten Größencheck bei 25 MiB+1.
Der produktive `interruptedGeneration`-Guard bleibt Codebefund und bestehender
UX-Vertrag, kein in dieser Harness ausgeführter Repository-Lifecycle.

INVALID vor Reserve: null Commit/Source/Root. CAPACITY vor Insert: kein neuer
Job/Source/Root, read-only Fake-Reserveende separat gezählt. SOURCE_UNAVAILABLE:
zwei zurückgekehrte Fake-Commits, WRITING und exklusives Leaf bleiben trotz
DocumentResult.Rejected. TOO_LARGE: ebenfalls WRITING, reale Cipherbytes und
Sourceclose; selbst ein Fileforce im finally beweist keinen gültigen Import.
Syncfehler nach Rename: drei frühere Commits, finaler Blob, CLOSED bleibt,
Directorysync nicht bestätigt. Fehler nach FILE_PUBLISHED: vier frühere Commits,
zwei Directorysyncs zurückgekehrt und Snapshotkeys geschlossen/zeroisiert,
aber kein DocumentRow. Revoke/Fehler nach Detach: zwei Deletecommits, Parent weg,
Blob noch da. Unlink vor Syncfehler: Blob weg, Parent weg, PENDING bleibt.
Keine dieser Folgen darf als globaler Rollback gelten.

StorageContext bleibt alleinige Sessionautorität für jede Schritt-/Commit-/UI-
Grenze. Die zentrale `publish`-Zulassung und §60-Lane-CAS bleiben unverändert.
Ein nach P zugelassener Callback darf gemäß §60 enden; Completion ist kein
Erlaubnistoken. Verlorener Main-Runnable: physischer Auftrag kann abgeschlossen
sein, UI-Dispatch bleibt unbekannt; kein Retry/Undo. One-shot Worker/Main verhindern
zweite Wirkungen, nicht das ursprüngliche Partial-Outcome. Ein Maintenance-Fence
verwirft unzugelassene alte Antworten, bestätigt aber keinen Dateischritt.

**UNWIRED / HOSTGETESTET:** Der bestehende GAP-Test ist ausdrücklich so markiert.
Neue Characterizations halten je TASK-/OPERATIONS-Pending und führen echte
DocumentProductDispatch-Workclosures mit simulierten Import-/Deletephasen aus:
Arbeit und Callback finden statt, gemeinsame Epoche bleibt unverändert, Core-
Pending bleibt busy. Umgekehrt wird erst der Dokumentworker beendet, dann ein
Core-Pending begonnen: der spätere Dokumentcallback läuft trotzdem und beendet
oder heilt dieses Pending nicht. Ein UNKNOWN-Coreabschluss bleibt trotz eines
Dokumentcallbacks für dieselbe Generation unsicher. Nur ein frischer autorisierter
Generationskontext kann uncached neue Buchführung beginnen; kein alter Ticket-
oder ACTIVE-Validity-Owner wird dadurch wieder gültig.

### 62.6 Betroffene Readvalidität und minimaler späterer Port

| Projektion | Tatsächlicher Bezug / erforderliche spätere Invalidierung |
| --- | --- |
| DocumentSummary / Dokumentanzahl | `DocumentDao.count`, Repository.summary und WorkspacePart.DOCUMENT_SUMMARY; Delete-Reserve ändert die sichtbare Anzahl schon vor Detach/Unlink, Import erst beim finalen Insert |
| Dokumentliste / Detail-Expected | list/get schließen Deletejobs aus; neue DocumentRows entstehen erst am Importende. Alte Listen und Expected-Snapshots ab Mutationsbeginn ungültig behandeln; Expected weiterhin frisch DAO-seitig prüfen |
| Workspace-Übersicht / Dokumentkarte | ProductWorkspaceRepository liefert count in WorkspaceRead.documents; ACTIVE-Hub rendert daraus die Dokumentanzahl. Der gemeinsame Readticket-/Epochenvertrag muss diese Abhängigkeit einschließen |
| Kapazitäts-/Admissionzustand | RESERVED/WRITING reservieren Slot/Maximalbudget; ein späterer Auftrag kann bereits dadurch anders zugelassen werden. Nicht auf die erste sichtbare neue DocumentRow warten |
| laufende Viewer | eigene PDF/Text/Image-Requests, Expected-/Session-/Dateibindungen, Cancellation und Payloadcleanup bleiben notwendig. Eine globale Dirtymarkierung schließt keinen Decoder/Renderer oder Stream; ab Mutation betroffene Requests/Expected neu prüfen bzw. abbrechen, keine alten Antworten neu zulassen |

Importreserve fügt heute noch keinen sichtbaren Parent hinzu; trotzdem verändert
sie Kapazität und Zulässigkeit weiterer Reads (z.B. Viewer-DAO verweigert
unvollständige Importjobs) und weiterer Arbeit. Delete-Reserve blendet den Parent
bereits aus. Deshalb gemeinsamer Invalidierungsbeginn vor Queue/erster Wirkung,
nicht erst am Repository-Value. Ein Mutex um Dateioperationen oder UI-Refresh am
Ende liefert diesen Readvertrag nicht.

**BESTEHEND / UNWIRED:** ActiveWorkspaceValidity.Projection hat derzeit keine
Dokumentprojektionen; WorkspacePart.DOCUMENT_SUMMARY allein ist kein entsprechendes
Validitylabel. Ein späterer Patch müsste Dokument-Summary/Count, Liste/Expected-
Validity und Übersichtabhängigkeit ausdrücklich ergänzen/reviewen. Dokumentnamen,
IDs von Blobs, URIs, Dateipfade, Inhalte, Keys, Viewer-/Snapshotpayloads gehören
nicht in retained ACTIVE-Metadaten. Action-/Generation-/Permissionbindung darf
bestehende Identitätsmetadaten verwenden; keine zweite Berechtigungsautorität.

**VORGESCHLAGEN:** Derselbe ProductRuntime-Port muss Import/Delete auf dem
Ownerthread vor `worker.execute` in dasselbe Pending und dieselbe Mutationsepoche
aufnehmen. Nullable Admission verweigert Busy ohne Worker, Sideeffect oder
Umgehung; aktuelle Action-/Generationbindung und StorageContext gelten weiter.
Reads/Viewer sind keine Dokumentwriter und dürfen nicht durch bloßes Einwickeln
des gesamten DocumentProductDispatch zu Writeraufträgen werden. Die Namespace-
Lane bleibt zusätzlich für File-I/O, Reader und Maintenance erhalten.

`WorkspaceWriteCompletion(outcome,cleanup)` kann die Phasen nicht verlustfrei
ausdrücken: UNKNOWN unterscheidet keine bekannten Teilcommits von unklarem Ende;
COMMITTED ist hier ein einzelner äußerer Core-Transaktionsbeleg, ROLLED_BACK kein
Label für einen späteren Dateifehler. WorkspaceWriteProgress speichert nur den
letzten Transaktionsoutcome und eignet sich nicht als Dokumentledger. Die
Pipeline gehört deshalb nicht in eine einzige confirmedWorkspaceTransaction.

Zwei spätere Optionen, **keine ist hier implementiert**:

1. Rein konservativer Fallback auf bestehenden Completionport: nur belegte
   Nichtausführung → ROLLED_BACK; alle partiellen/unklaren Dokumentaufträge →
   UNKNOWN, Cleanup getrennt. Sicher fail-closed, aber auch ein vollständiger
   Multi-Phase-Abschluss kann ohne semantische Umdeutung nicht verlustfrei als
   Core-COMMITTED übergeben werden. Das ist eine Sperr-/Untersuchungsoption,
   kein ausreichender produktiver Erfolgsvertrag.
2. Kleinste brauchbare Erweiterung: explizite DOCUMENT-Admission im bestehenden
   Interface (default deny für nicht vorbereitete Adapter) UND typisierter
   Dokumentabschluss auf **demselben Pending**, z.B. `finishDocument(evidence)`.
   Dieser enthält key-/payloadfreie bestätigte Gesamt-/Recovery-/Cleanupmerkmale;
   der begrenzte lokale Ledger bleibt Data-seitig. Kein zweites Pendingregister,
   keine unabhängige Epoche. Vollständiger Abschluss darf Pending lösen, markiert
   aber keine Readprojektion clean. Teilfortschritt/UNKNOWN/Cleanupfehler setzen
   gemeinsame Validity auf RECOVERY_REQUIRED. Vorhandene Core-finish-Semantik
   bleibt unverändert. Bloß DOCUMENT ins Enum aufzunehmen reicht ohne geprüfte
   Default-Deny-/Projection-/Runtime-/Completionpfade ausdrücklich nicht.

Benötigt werden Producer-Hooks am **äußeren** DB-Transaktionsende, getrennt vom
DB-/Keyscopeende, an File-/Sync-Rückkehr, am Ressourcenabschluss und an einem
frischen finalen Inventar. Bei Exception vor dieser Beobachtung keine nachträglich
erfundene Bestätigung. Callbacks benötigen zusätzlich aktuelle Session, eigenes
Ticket und Lane-Admission. Bei RECOVERY_REQUIRED würde der konservative neue
Port reguläre Erfolgs-/Readpublication verweigern; eine neutrale fachliche
Fehlermeldung trotz Teilfortschritt braucht explizites UX-/Publicationreview und
darf weder das Pending sauber abschließen noch veraltete Readpayloads freigeben.
Heute zugelassene Rejectioncallbacks bleiben unverändert und beweisen keine
zukünftige Invalidierungsintegration.

### 62.7 Recovery und neue READY-Generation

**BESTEHEND:** Autorisierte Reconciliation läuft vor READY unter MAINTENANCE,
drain/fenced dieselbe Lane und inventarisiert global vor erster Mutation sowie
zwischen Schritten und am Ende. RESERVED/WRITING mit zulässigem vollständigem
Namespace können nach bestehenden Regeln abbrechen; CLOSED-final wird erneut
verifiziert und synchronisiert; PENDING ohne Parent setzt Delete fort. READY
folgt nur nach bestätigter Sessionbarriere, nicht nach einer einzelnen Joblöschung.

| Zustand | Konservative gemeinsame Validity / bestehende Recovery |
| --- | --- |
| Job nach Revoke / fehlgeschlagenem Fileforce | alte Validity bleibt unsicher; neuer autorisierter Inventarread; bekannte WRITING-Datei nur nach bestehendem vollständigem Namespacevertrag bereinigen |
| Rename erfolgt, DB noch CLOSED | frühere Dateiwirkung erhalten; vorhandene Maintenance verifiziert finalen Blob, synchronisiert beide Verzeichnisse und setzt DBschritte fort |
| Parent weg, Blob da / Unlink ohne Syncbestätigung | Parent nicht rekonstruieren; PENDING-Recovery mit frischen Bindungen, Sync und finalem Jobabschluss |
| unbekanntes Leaf / fehlender Root mit WRITING | BLOCKED, keine Heuristik, keine fremden Unlinks, keine Rootrekonstruktion oder manuelle Joblöschung; RED-4 bleibt offen |
| unbestätigtes Cleanup | kein Clean/READY aus noch so plausiblen Job-/Dateinamen; nativen Scopeabschluss unabhängig klären |
| Recovery final bestätigt | getrennte Evidence für globales Endinventar und Ressourcenende; alter UNKNOWN-Auftrag wird nicht rückwirkend COMPLETE/ROLLED_BACK |
| neue READY-Generation | frische autorisierte Buchführung und neuer Validity-Owner nur nach bestätigter Recovery; alte Owner/Readtickets/Callbacks bleiben terminal ungültig, Daten erst nach neuem autorisiertem Read clean |

**VORGESCHLAGEN:** Vor Maintenance-/Recovery-Queue muss die gemeinsame zukünftige
Validity synchron fail-closed werden, über denselben Owner/Port, nicht über einen
zusätzlichen Maintenance-Pendingowner. Der heutige Interfaceport hat noch keinen
Recovery-/Lifecycle-Eingang: der vertrauenswürdige spätere Lifecycleadapter (#37)
müsste auf dem Ownerthread die alte Validity bei READY-Verlust synchron terminieren
und erst nach bestätigter Maintenance eine neue Bindung vorbereiten. Ein
READY-only-beforeQueue ist kein MAINTENANCE-Admissionport. Dieser Anschluss ist
zusätzlich zu typed finish zu reviewen und hier UNWIRED. Revoke/Lifecycle terminiert alte Owner;
Worker dürfen Owner-Metadaten nicht eigenmächtig zurücksetzen. Kann eine verlorene
Mainzustellung das alte Pending nicht bestätigen, bleibt es unsicher; nur der
bestehende kontrollierte Generations-/Recoverywechsel darf neuen Zustand
vorbereiten. Ein erneuter Unlock oder erfolgreiches Cipherverify allein genügt
nicht. Uncached-Neubuchführung ist ausdrücklich kein Reset einer bestehenden
ActiveWorkspaceValidity-Instanz. Keine zweite Sessionautorität.

### 62.8 Tatsächliche Tests und Grenzen der Belege

Neue enge `DocumentMultiPhaseEvidenceTest`-Harness: echter Tink, echte temporäre
Hostdateien und Channels, echte Koordinatoren/DAO-Regeln; simulierte Transaktionen
mit getrennten Versuchszählern/Commitbestätigungen, keine Roomengine. Fileforce-
Aufrufe, Directorysync-Versuche/Rückkehr, Rename/Unlink, tatsächlicher DB-/Dateistand,
Sourceöffnungsversuche, tatsächlich erworbene Sourcestreams, Sourceclose und
beobachtete Ressourcen sind getrennt erfasst. Die
Fake-DB-Adapter melden nur das von ihnen simulierte Ende; ein injizierter
Cleanupfehler nach diesem Ende erhält den bestätigten lokalen Commit.

Belegt: frühe INVALID-/CAPACITY-Ablehnung, SOURCE_UNAVAILABLE/TOO_LARGE nach
WRITING, Fileforcefehler, Cipherclose/Fileforce vor zurückgerolltem CLOSEDschritt,
Rename vor Syncfehler, Fehler nach PUBLISHED, erfolgreicher Import mit fünf
lokalen Commits, erfolgreicher Delete mit drei, Fehler/Revoke nach Detach,
Unlink ohne Sync, unbestätigtes Cleanup, unbekanntes DBende und späteres Rollback
bei erhaltenem früherem Commit. Die zusätzlichen finalen Inventare sind
**Test-/Entwurfschritte**. DB/key/stream/snapshot-Cleanup im Modell bestätigt nur
die beobachteten Fake-/Hostscopes: inDb=false, erzeugter geliehener Filekey gewiped,
erfasste Channels geschlossen, Sourceclose und Snapshotkeys gewiped. Kein
universeller Beweis für versteckte/native Keykopien, Cursor oder Android-Handles.

Zusätzlich echte Koordinatorarbeit durch unveränderten DocumentProductDispatch:
Worker/Main-Replay führt einmal die fünfphasige Importkette und einmal Callback
aus; ein vollständig entfernter Delete bleibt bei verlorener Mainpublication
physisch abgeschlossen, Dispatch unbekannt; Fence nach fertigem Import verweigert
Callback ohne DB-/Dateiundo. Reconciliation nach Fileforcefehler erhält eine neue
Evidenceinstanz und bereinigt den zulässigen WRITING-Zustand, ohne die alte UNKNOWN-
Instanz zu heilen. Rootloses WRITING und unbekanntes Leaf blockieren weiter.

Die drei neuen GAP-Tests in DocumentRecoveryDispatchContractTest verwenden für
die physische Wirkung ausdrücklich simulierte Ledgers. Ein weiterer Test im
bestehenden ActiveWorkspaceMutationAdapterTest prüft den konservativen UNKNOWN-
Fallback über den **bestehenden TASK-Port**, keine künstliche DOCUMENT-Anbindung:
altes Readticket bleibt verweigert, alte Validity terminal RECOVERY_REQUIRED,
frische Bindung/Generation allein heilt sie nicht; neue Instanz muss frisch lesen.
Ein Strukturtest des Entwurfs prüft ausschließlich feste Enum-/Boolean-/Mapfelder
und den weiterhin deaktivierten RAM-Reuse-Schalter. Kein Heap-/Native-Retention-
Beweis und keine Behauptung, sämtliche Produktpayloadlifetimes seien damit geprüft.

| Beweisstärke | Tatsächlicher Umfang |
| --- | --- |
| Fake-DB | HOSTGETESTET: echte DAO-Regeln, kontrollierter Rollback/Commit/Lease-Simulator; kein SQLitecommit |
| Hostdateien | HOSTGETESTET: echte Tinkstreams, beobachtete File-/Directoryforce-Rückkehr und Filezustände; Hostmove-Testdouble ist kein Android-RENAME_NOREPLACE-Beweis |
| echte SQLite | bestehende unveränderte Skripte `tools/p10b2c1/check_import_staging_sql.py` (3), `tools/p10b2c2/check_publication_sql.py` (4), `tools/p10b2c3/check_delete_sql.py` (4): elf Tests ausgeführt. Belegen getrennte SQL-Phasen/Expected-Updates, atomaren Insert+Jobdelete, atomaren Parentdetach+Pending und Erhalt früherer Phasen bei späterem Rollback. In-memory SQLite ohne Filepipeline/Room/Keyscope, kein neuer Mehrphasen-Datenträger- oder Crashbeweis |
| Android/SQLCipher/Powerloss | NICHT BEWIESEN: keine Instrumentation/Installation, kein nativer Cursor-/Key-/fsync-/Crash-/Cleanupbeleg; Testkompilation allein ersetzt diese nicht |

Ein erster Kompilierlauf der neuen Harness scheiterte an Kotlin-Arrayprojektionen
beim Zusammenführen von Hostleaflisten; durch explizite Listenbildung korrigiert.
Keine Assertion abgeschwächt und kein Test geskippt. Das war ein Testharnessfehler,
kein Produktfehler oder erfolgreicher Lauf.

### 62.9 Nächster enger Patch und weiterhin offene Blocker

**VORGESCHLAGEN:** Zuerst unabhängig den typisierten Abschluss auf demselben
Pending und die dazu nötigen Data-Evidence-Hooks/finalen Check reviewen. Danach
separater kleiner Produktpatch mit synchroner DOCUMENT-Admission nur bei Import/
Delete, derselben Runtime-Portinstanz, expliziten Readprojektionen und
konservativem Recoveryabschluss. Strenge RED-Tests gegen den dann noch unveränderten
Produktpfad: Core-Pending muss Dokumentworker verhindern; Dokumentbeginn erhöht
vor worker.execute dieselbe Epoche und sperrt spätere Corewriter; Teilrejection
bleibt recoverypflichtig; kompletter Abschluss löst Pending ohne Cleanmarkierung;
verlorene Dispatches/unbestätigtes Cleanup bleiben fail-closed; alte Reads/Callbacks
überleben weder Mutation noch neue Generation. Die heutigen GAP-Assertions sind
Characterization, keine bereits bestandenen Zielvertrags-REDs.

**UNWIRED / BLOCKER:** #35 mehrphasiger Writer-/Recoveryanschluss und echte
Producer-Evidence fehlen; rootloses WRITING (§59 RED-4) und §61-Teilroot-/Präventions-
fragen offen. Keine §61-Umordnung oder neue Recoverypolitik. #36 Editor-/CAS-/ABA-
Grenzen unverändert; #37 Snapshotowner nicht installiert; #38 keine Performance-
oder RAM-Hitfreigabe; #32 native Cursor-/Cleanup- und Fixtureisolation offen.
`SQLCipherStatement.close()` ist weiterhin kein expliziter Cursorclosebeleg.

Keine produktive Datei verändert: SessionCoordinator, StorageAccess,
SessionDbKeyOwner, SQLCipher/Tink/B2Driver, Schema 11, 180 Sekunden absolut /
30 Sekunden READY-idle, 8+8-MiB-Budget, noBackup-Root, NOFOLLOW, CREATE_NEW,
Atomic-No-Replace-Rename, File-/Directory-fsync, Namespace-Lane, Dispatch/CAS,
Staging/Publication/Delete/Reconciliation/Maintenance, Runtime/Repository und
bereits integrierte Writer/P3b bleiben unverändert. Keine Dependency/Permission,
Migration, neue Snapshot-/Payloadowner, RAM-Hits, Geräteinstallation, Änderung
installierter Apps, pauschale Fixturelöschung, Produktupdate, Merge oder Release.

### 62.10 Abschlussvalidierung und Übergabe

**HOSTGETESTET:** 27 neue Testmethoden: 23 in der neuen Evidence-Harness, drei
zusätzliche Dispatch-/Bypass-Kontrakte und ein zusätzlicher Validitytest.
Gezielte Data-Auswahl:
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --tests '*Workspace*Test' --tests '*Session*Test' --tests '*Writer*Test' --tests '*TaskMutationSqliteTest' --offline`:
**561 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESSFUL**.
App-Auswahl mit Document/Workspace/Session: **265 Tests, 0 Failures, 0 Errors,
0 Skips**. Nach Präzisierung der Resource-/Source-Beobachtung noch einmal die
23 neuen Evidencefälle gezielt grün; der abschließende vollständige Lauf enthält
sämtliche finalen Harnessänderungen einschließlich isolierter Delete-Evidence.
Separat die elf oben benannten echten Host-SQLite-Skripttests: alle grün,
keine Fehler/Skips. Keine dieser Zahlen bezeichnet native SQLCipher-Evidence.

Anschließend vollständiger geforderter Offline-Lauf:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`:
**BUILD SUCCESSFUL**. Debug-Test-XMLs: App **672**, Data **1.001**, Security **4** =
**1.677 Tests, 0 Failures, 0 Errors, 0 Testskips**. App/Data ausgeführt;
unveränderte Securitytests und Androidtest-Kompilationen UP-TO-DATE bestätigt.
Domain ohne eigene Testquellen. Gradle-Konfigurations-/Lifecycle-Tasks mit SKIPPED
sind keine übersprungenen Testfälle. Beide Manifestchecks und Debugassemble
successful. Keine Androidinstrumentation oder Geräteinstallation.

`git diff --check` sauber. Ausschließlich folgende vier Dateien geändert:

- `data/src/test/kotlin/org/pag/data/product/DocumentMultiPhaseEvidenceTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentRecoveryDispatchContractTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceMutationAdapterTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

Bestehende Assertions nicht abgeschwächt, keine Skips hinzugefügt. Keine Logs,
APKs, Datenbanken, Schlüssel oder Testartefakte im Commit. Regulärer WIP-Commit
und regulärer Push auf `p11-performance-optimization`; Remote-SHA separat über
GitHub bestätigen. Danach **STOPP bis zum unabhängigen GitHub-Review**.

## 63. Typed Document Pending Contract

### 63.1 Basis und enger Implementierungsumfang

Issue #35; verbindliche Basis `fdd36fbe340b5a80ddccc7f536272627faa23256`.
Vor Änderungen waren lokaler HEAD und unabhängig per GitHub-API gelesener
Remote-Ref identisch, Branch `p11-performance-optimization`, Arbeitsbaum und
Diff sauber. Grundlage sind §§57–62 und die geprüften Domain-/Uncached-/
WriteBoundary-/Progress-, ACTIVE-Validity-/Adapter- und Dokumentrepository-/
Dispatchverträge samt §62-Evidencemodell und Writer-/Workspace-Hosttests.

**IMPLEMENTIERT:** Ausschließlich Domain-Metadatenport und dessen vorhandene
Uncached-Implementierung erweitert; dazu reine Host-Porttests und ein enger
Default-Deny-Test am vorhandenen ACTIVE-Adapter. **UNWIRED:** Kein Dokument-
Repository, Dispatch oder Runtime ruft die neue API auf. Kein Data-Producer,
kein neuer globaler Inventarread oder Datei-/Sync-Hook. Ein implementierter
Portvertrag ist keine End-to-End-Writerfreigabe und schließt den §62-Bypass nicht.

Die bestehende Core-Semantik bleibt: WorkspaceWriteCompletion mit COMMITTED,
ROLLED_BACK oder UNKNOWN beschreibt den bestätigten äußeren lokalen
Transaktionsschritt (bzw. belegte Nichtausführung), Cleanup ist separat.
WorkspaceWriteProgress/WorkspaceWriteBoundary und sämtliche Corewriter wurden
nicht geändert. Eine Dokumentpipeline erhält keinen erfundenen äußeren ACID-
Gesamtcommit und wird nicht durch confirmedWorkspaceTransaction gewickelt.

### 63.2 API, Default-Deny und derselbe Pendingowner

**IMPLEMENTIERT:** Neue Domain-Typen:

- `DocumentWriteOutcome`: NOT_EXECUTED, COMPLETE, RECOVERY_REQUIRED, UNKNOWN.
- `DocumentWriteCompletion(outcome, cleanup, finalInventoryConfirmed=false)`;
  Cleanup verwendet unverändert WorkspaceWriteCleanup.
- `WorkspaceMutationInvalidation.beforeDocumentQueue(actionId, generation)`;
  Interface-Default **null**, kein Delegieren an beforeCoreQueue/beforeTaskQueue.
- `WorkspaceMutationInvalidation.Pending.finishDocument(completion)`;
  Interface-Default **false**, kein Delegieren an Core-finish.

WorkspaceWriterFamily bleibt unverändert, ohne DOCUMENT-Eintrag. Damit können
alte Core-/Taskadapter nicht durch einen bisherigen Default-Familienzweig
Dokumente zulassen. Die neue Admission ist ausdrücklich opt-in.

UncachedWorkspaceMutations führt beide Admissions über dieselbe private begin-
Routine: derselbe Ownerthread, derselbe einzelne `pending`-Slot, dieselbe
`latest`-Identität, derselbe Unsicherheitszustand, dieselbe Generation und genau
eine `mutationEpoch`. Erfolgreiche Admission erhöht diese einmal; Busy, ungültige
Metadaten oder terminale Unsicherheit nicht. Kein zweiter Port, Pendingcontainer,
Dokumentepochenzähler, Sessionowner oder Generationenregister.

Das vorhandene innere Ticket erhält lediglich einen CORE-/DOCUMENT-Typmarker
und eine Kopie der Admissiongeneration. Beides sind begrenzte Metadaten. Es
speichert weder Completionwerte noch Dokumentpayloads/Keys/Blobnamen/URIs oder
Callbackreferenzen. Die Portinstanz hält weiterhin höchstens die aktuelle und
letzte Ticketidentität, keine Auftragshistorie.

**HOSTGETESTET:** TASK/OPERATIONS-Pending verweigert Dokumentadmission; Document-
Pending verweigert Task, Operations und zweites Dokument. Abwechselnde Core-/
Dokumentaufträge erhöhen auf derselben Instanz denselben Zähler. Fehlender
Abschluss bleibt Pending und sperrt auch eine angefragte andere Generation;
es gibt keinen Timeout, Busy-Bypass, Retry oder automatisches Recovery im Port.

### 63.3 Typisierte Abschlüsse und finaler Inventarvorbehalt

| Dokumentoutcome | Erforderliche Produzentenaussage | Verhalten bei bestätigtem Cleanup |
| --- | --- | --- |
| NOT_EXECUTED | Tatsächliche Nichtausführung; keine eigene DB-/Dateiwirkung, keine unklare DBöffnung oder Ressource | Pending konsumieren; aktuelles Metadataticket möglich, keine automatische Cleanmarkierung und kein Mutationserfolg daraus |
| COMPLETE | Alle erforderlichen lokalen Phasen/Dateioperationen/Syncs beendet; kein offener Job; frisches konsistentes globales Inventar NACH dem letzten DB-Schritt | Nur mit finalInventoryConfirmed=true konstruierbar; Pending konsumieren, Readprojektionen weiterhin nicht clean |
| RECOVERY_REQUIRED | Bekannte Wirkung/Jobzwischenstand, aber Auftrag nicht vollständig abgeschlossen | Pending konsumieren und dieselbe Generation terminal unsicher halten; weitere Core-/Documentadmission verweigern |
| UNKNOWN | Entscheidende DB-/Datei-/Sync-/Inventar-/Cleanup-Aussage unbestätigt | Gleiche terminale Unsicherheit, keine weitere Mutation dieser Generation |

FAILED_OR_UNCONFIRMED-Cleanup erzwingt für **jeden** Outcome dieselbe terminale
Unsicherheit, auch bei NOT_EXECUTED oder behauptetem COMPLETE. Deshalb darf der
Completiontyp eine solche Kombination transportieren; sie ist niemals eine
saubere Gesamtfreigabe. `finishDocument` liefert true für das einmalige Konsumieren
des korrekten Pending, auch wenn dessen Folge terminale Unsicherheit ist.
True ist weder „Mutation erfolgreich“ noch „Session/Publication zugelassen“.

**IMPLEMENTIERT:** COMPLETE ohne finalInventoryConfirmed ist ungültig:
Konstruktor und data-class-copy werfen IllegalArgumentException. Das eigentliche
Pending bleibt dabei unangetastet. Auch mit fehlgeschlagenem Cleanup darf der
finale Inventarvorbehalt nicht umgangen werden. Ein positiver Inventarwert bei
UNKNOWN/RECOVERY_REQUIRED oder unbestätigtem Cleanup hebt deren Sperre nicht auf;
ein Inventar kann konsistent sein, während eine andere Aussage unbestätigt ist.

**NICHT BEWIESEN / UNWIRED:** Der Boolean ist eine Behauptung eines späteren
vertrauenswürdigen Data-Producers, kein automatisch geprüfter Datenträgerbeleg.
Der Producer muss sämtliche erforderlichen lokalen Commit-/File-/Syncschritte,
fehlende Jobs, den frischen globalen Check nach letztem DB-Ende und bestätigtes
DB-/Key-/File-/Snapshotcleanup unabhängig nachweisen. Die API erzeugt diesen
Beleg nicht. DocumentResult.Value, DocumentResult.Rejected und bloßes Setzen des
Flags dürfen diese Anforderungen nicht ersetzen. §62 trennt weiterhin die lokalen
DB-/Filesystemschritte; die dortige Fake-DB-/Hostharness ist kein produktiver
Producer oder nativer Cleanupnachweis.

### 63.4 Tickettyp, Identität, Generation und Thread

**IMPLEMENTIERT / HOSTGETESTET:** Core-finish auf einem Documentticket liefert
false, ebenso finishDocument auf einem Coreticket. Das gilt für alle jeweiligen
Outcome-/Cleanupkombinationen; das echte Pending bleibt bestehen und wird weder
sauber beendet noch durch den falschen Abschluss künstlich als unsicher konsumiert.
Erst der korrekte Abschlusstyp kann es beenden. Doppelter/staler Abschluss liefert
false und verändert ein neueres Pending nicht. Ticketgenerationsvergleich und
Objektidentität müssen zum aktiven Pending passen; alte Tickets bleiben auch
nach neuem Auftrag/Generationswechsel ungültig.

Ein Abschluss wird am Ticketreceiver aufgerufen, nicht als fremdes Ticket an eine
Portmethode übergeben. Ein Ticket einer anderen Instanz kann ausschließlich seinen
eigenen Port abschließen. Der Isolationstest beendet das andere Pending und
belegt, dass die ursprüngliche Instanz busy bleibt; er behauptet keine nicht
existierende Fremdticket-Parameterprüfung. Alte Ticketreceiver liefern false,
während die ursprüngliche Instanz ein anderes/neueres Pending besitzt.

Admission, beide finish-Methoden und isCurrent prüfen denselben Ownerthread.
Auf einem fremden Thread werfen sie IllegalStateException, bevor Pending/Epoche
geändert werden. Auch falscher Abschlusstyp auf dem fremden Thread erteilt keine
Ausnahme von der Threadbindung. Der begrenzte Executortest bestätigt anschließend
auf dem Ownerthread das erhaltene Pending und dessen regulären Abschluss.

**Unveränderte Generationsregel:** Blank/Whitespace-Action wird verweigert,
negative Generation ebenso; Generation 0 bleibt wie bei Core zulässig. Ein
nichtnegativer Long allein belegt weder aktuelle READY-Autorisierung noch
Maintenanceerfolg. Der spätere Aufrufer muss den gültigen autorisierten Actionbezug und die aktuelle
Generation des zentral autorisierten StorageContext verwenden und vor worker.execute auf
dem Ownerthread synchron anmelden. Der Port besitzt bewusst keinen StorageAccess
oder SessionCoordinator und kann erfundene/veraltete Rohzahlen nicht unabhängig
authentifizieren. Es wird keine neue Monotonie-/Generationentabelle eingeführt.

Wie bisher kann nach beendetem Pending nur ein **tatsächlich autorisierter neuer
READY-Generationskontext nach erfolgreicher Maintenance** die Uncached-Buchführung
neu beginnen. Derselbe Generationswert bleibt nach UNKNOWN/Recovery/Cleanupfehler
terminal gesperrt; bei neuer Admission werden alte Identitäten verworfen. Keine
Zahl/Completion meldet von sich aus Maintenance erfolgreich. Bleibt ein Pending
unbestätigt offen, wird es auch durch eine andere angefragte Generation nicht
verdrängt. Das ist der bestehende Vertrag, kein neuer Session-Recoverymechanismus.

### 63.5 Uncached-Live-Port und ACTIVE weiterhin DENY

**IMPLEMENTIERT:** UncachedWorkspaceMutations stellt die neue Fähigkeit isoliert
bereit. Der bestehende Runtime-Default bleibt dieselbe Uncached-Klasse; es wird
keine zusätzliche Instanz gebaut oder injiziert. `ramReuseEnabled` bleibt
unbedingt false, vor/während/nach jedem Dokumentabschluss. Es gibt weiterhin
keine Read-/Hit-/Clean-/Publicationfunktion auf dem Uncached-Port. isCurrent ist
nur Metadatenidentität; sie kann sowohl nach NOT_EXECUTED als auch nach COMPLETE
true sein und beweist gerade deshalb keinen Mutationserfolg.

**UNWIRED / HOSTGETESTET:** ActiveWorkspaceMutationAdapter bleibt produktiv nicht
installiert und erbt ausdrücklich den neuen Default-Deny. Keine Produktänderung
an Adapter/Validity nötig: beforeDocumentQueue liefert null ohne Corefallback;
ein Corepending verweigert finishDocument über den Default und bleibt pending/
dirty. Anschließendes echtes Core-finish markiert ebenfalls nichts clean, erst
ein frischer autorisierter Read darf das. Der bestehende §62-Test zum terminalen
alten Validityowner/Readticket bleibt erhalten. Keine vorgetäuschten aktiven
Dokumentprojektionen und kein Dokument-Cleanbeweis durch einen Core-Test.

Eine spätere aktive Unterstützung benötigt separat geprüfte DocumentSummary-/
Count-, Listen-/Expected- und Overviewabhängigkeiten sowie Viewer-Cancellation/
Cleanup und Lifecyclebindung. #37 bleibt offen, kein Snapshotowner installiert.
§60-Lane-Admission und gemeinsame Mutationsepoche bleiben getrennte Verträge.

### 63.6 Tatsächliche Hosttests und Beweisgrenzen

Neue `DocumentPendingContractTest` im vorhandenen Data-JUnit-HosttestsourceSet:
21 Methoden, reine echte Domain-Portaufrufe mit synthetischen Metadaten. Keine
neue Testdependency im Domainmodul. Getestet sind gemeinsame Admission/Epoche,
Busy in beiden Richtungen, Default-Deny bei Task-/Coreadaptern und alten Pendings,
blanke Actions/negative Generation, fehlender Abschluss, alle vier Outcomes mit
beiden Cleanupwerten, finaler Inventarvorbehalt inklusive copy, falsche Tickettypen,
Doppelabschluss, altes Ticket, Instanzisolation, neue Generation ohne
Ticketrestauration, Core-UNKNOWN-/Cleanup-Sperre für Dokumente und falscher Thread.
Ein Strukturtest bestätigt ausschließlich Outcome-/Cleanupenum und Boolean im
Completionobjekt sowie fehlende Read-/Clean-/Callbackmethoden am Pending.

Ein neuer Test in ActiveWorkspaceMutationAdapterTest belegt das unverändert
geerbte DENY und das erhaltene dirty Corepending bei falschem Abschluss.
Insgesamt 22 neue Testmethoden, keine abgeschwächten bestehenden Assertions.
Die unveränderten Dokument-GAP-/Evidence-/Recoverytests werden weiterhin ausgeführt:
Sie beweisen weiterhin fehlende Produktanbindung, nicht funktionierende neue
Repositoryinvalidierung. Bestehende Corewriter-Transaktions-/Rollback-/Cleanup-
und Epochenregressionen sichern die unveränderte Core-Semantik zusätzlich ab.

Ein erster isolierter Lauf ergab 21 Tests, einen Fehler im neuen Reflectiontest:
Kotlin generiert für die Defaultmethode eine synthetische `access$…$jd`-Brücke.
Die Prüfung wurde auf die nichtsynthetischen API-Methoden präzisiert; die exakte
Erwartung finish/finishDocument/isCurrent bleibt erhalten. Kein Portfehler,
kein übersprungener Test und keine abgeschwächte fachliche Assertion.

**HOSTGETESTET:** Portzustandsmaschinen-/Thread-/Metadatenvertrag.
**NICHT BEWIESEN:** tatsächliche Nichtausführung, Dokumentgesamtabschluss,
SQLite-/Room-/SQLCiphercommit, nativer Key-/Cursor-/Filecleanup, Directory-fsync
oder Powerloss aus diesen neuen Porttests. Die Completionwerte sind synthetische
Producerbehauptungen. Bestehende DB-/Dateitests behalten ihre bisherigen
Fake-/SQLite-/Hostgrenzen; dieser Slice ergänzt keinen nativen Evidenzbeleg.

### 63.7 Nächster eigenständiger Slice und offene Blocker

**VORGESCHLAGEN:** Als separaten nächsten Slice zunächst vertrauenswürdige
Data-Evidence-Produzenten an den in §62 benannten äußeren DB-End-/Scopecleanup-,
File-/Sync- und finalen globalen Inventargrenzen reviewen. Erst danach separat
Import/Delete synchron vor Queue an dieselbe Runtime-Portinstanz anschließen,
korrekte Generation/Action, Busyverweigerung, typisierten Abschluss und unabhängige
Session-/Lane-/UI-Zulassung nachweisen. Keine Komplettpipeline über Core-COMMITTED
oder confirmedWorkspaceTransaction abkürzen. Lifecycle/Maintenance-/Validity-
Adapter und aktive Dokumentprojektionen benötigen weiterhin eigenes Review.

**UNWIRED / BLOCKER:** #35 Data-Produzenten und Repository-/Dispatch-/Runtime-
Integration fehlen; §59 RED-4 bleibt historisch mehrdeutig und fail-closed,
§61-Präventions-/Teilrootfragen ungelöst. Keine Joblöschung, Rootrekonstruktion,
Importwiederaufnahme, neue Erstinitialisierung oder Änderung der Phasenfolge.
#32 nativer Cursor-/Cleanup-/Fixtureblocker offen. #36 Editor-/CAS-/ABA-Grenzen,
#37 Snapshotowner und #38 Performanceintegration/RAM-Hits unverändert offen.

Unverändert bleiben SessionCoordinator, StorageAccess, SessionDbKeyOwner,
SQLCipher/Tink/B2Driver, Schema 11, 180 Sekunden absolut / 30 Sekunden READY-idle,
8+8-MiB-Budget, Lane/Dispatch/Admission-CAS, FileChannel.force, Directory-fsync,
NOFOLLOW/CREATE_NEW/Atomic-No-Replace-Rename, alle Dokument-DBadapter/-Koordinatoren,
Maintenance-/Recoverypolitik, ProductRuntime, ProductDocumentRepository, bestehende
Corewriter, Navigation und Leadership/P3b. Keine neue Dependency/Permission,
Migration, Geräteinstallation, Änderung installierter Apps, RAM-Hits oder
Snapshot-/Payloadowner; kein Merge, Produktupdate oder Release.

### 63.8 Abschlussvalidierung und Dateiliste

**HOSTGETESTET:** Gezielte Port-/Dokument-/Workspace-/Writer-Auswahl:
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --tests '*Workspace*Test' --tests '*Writer*Test' --tests '*TaskMutationSqliteTest' --offline`:
**487 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESSFUL**.
Gezielte Appauswahl:
`./gradlew :app:testDebugUnitTest --tests '*Workspace*Test' --tests '*Document*Test' --offline`:
**253 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESSFUL**.

Anschließend vollständiger geforderter Offline-Lauf:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`:
**BUILD SUCCESSFUL**. Debug-Test-XMLs: App **673**, Data **1.022**, Security **4** =
**1.699 Hosttests, 0 Failures, 0 Errors, 0 Testskips**. App/Data ausgeführt,
unveränderte Securitytests UP-TO-DATE bestätigt; Domain weiterhin ohne eigene
Testquellen, seine neuen Porttests laufen im vorhandenen Data-HosttestsourceSet.
Beide Androidtest-Kompilationen, Debugassemble und beide Manifestprüfungen
wurden erfolgreich ausgeführt. Keine Androidinstrumentation/-installation.
Gradle-Konfigurations-/Lifecycle-SKIPPED bedeutet keinen übersprungenen Testfall.

`git diff --check` sauber; ausschließlich vier Dateien geändert:

- `domain/src/main/kotlin/org/pag/domain/WorkspaceMutationInvalidation.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPendingContractTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceMutationAdapterTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

Keine Logs, APKs, DBs, Keys oder Testartefakte im Commit. Regulärer WIP-Commit und
Push auf `p11-performance-optimization`; Remote-SHA unabhängig über GitHub prüfen.
Danach **STOPP bis zum unabhängigen GitHub-Review**.

## 64. Produktive Document-Evidence für Import und Delete

### 64.1 Basis und implementierter kritischer Pfad

Verbindliche Basis `81c3e90b580a018f7e676bd066eb274652ca744c`, Branch
`p11-performance-optimization`. Vor Änderungen: lokaler HEAD und unabhängiger
GitHub-Remote-Ref identisch, Arbeitsbaum und Diff leer. §§57–63 sowie Repository,
Dispatch/Lane, Staging/Publication/Delete, deren SQLCipher-/DB-/Keyscopes,
Inventory/DAO, Reconciliation, typisierter Port, WriteBoundary und Runtime geprüft.
Die zwischenzeitliche autorisierte Handyinstallation ist keine Quellcodeänderung;
in diesem Slice keine Geräteinstallation oder Änderung installierter Apps.

**IMPLEMENTIERT:** Produktive auftragslokale Evidence für **beide** Operationen.
ProductDocumentRepository erzeugt pro Import/Delete eine neue
DocumentMutationEvidence und ruft die neuen produktiven Producerpfade auf. Der
Import verwendet beobachtete Staging-/Publication- und SQLCipher-Adapter, Delete
die entsprechende beobachtete Reserve-/Detach-/Unlink-/Finishfolge. Beide führen
nach dem letzten mutierenden DB-Schritt einen zusätzlichen autorisierten globalen
DB-/Filesystemcheck aus. Kein weiterer reiner Modell- oder Analyseslice.

RAM-Cache und schnelle ACTIVE-Navigation bleiben zwingende Release-1.0-Ziele.
Dieser Slice liefert deren produktive Evidence-Voraussetzung, keine alternative
1.0 ohne Cache. **UNWIRED:** Der §63-Pending-Port ist noch nicht angeschlossen;
ProductRuntime, beforeDocumentQueue/finishDocument, ACTIVE-Owner und RAM-Hits
bleiben unverändert. §65 ist der nächste getrennt reviewbare Anschluss, keine
Release- oder Cachefreigabe durch diese Producer.

### 64.2 Begrenzte Evidence und lokaler Abschluss

DocumentMutationEvidence enthält ausschließlich Operationenum, feste Bitmasken
für bestätigte/unklare Schritte, Flags, einen festen kleinen Ressourcenzählervektor
und den eingefrorenen DocumentWriteCompletion. Keine Keys, Inhalte, Dokument-
oder Blobnamen, URIs, Pfade, Sources, Observer-/UI-Callbacks oder Ereignislisten.
Ressourcen je Kategorie sind zusätzlich begrenzt; Überlauf kann keine saubere
Freigabe ergeben. Kein zweiter Pendingowner, Sessionowner oder Persistenzort.
Die Producerklasse hält ihre normalen kurzlebigen DB-/Filesystemargumente; der
Collector hält keine Rückreferenz auf Producer, Snapshots oder Handles.

Ein Collector wird einmal für seine Operation beansprucht und am Ende (auch bei
Exception) eingefroren. Nachträgliche Beobachtungen oder Wiederverwendung für
einen zweiten Auftrag sind verboten; spätere Callbackverluste/Fences ändern den
physischen Abschluss nicht. Kleine synchronisierte Metadatenmethoden können von
den bestehenden Room-Coroutine-/Workerthreads aufgerufen werden. Unter ihrem
Monitor läuft keine Datei-/DBarbeit, kein Sessioncheck und kein fachlicher
Callback; er ist keine Namespace-/Sessionautorität.

| Typisierter Abschluss | Tatsächliche Producerbedingung |
| --- | --- |
| NOT_EXECUTED | Keine beobachtete Dokumentwirkung und keine unbestätigte Operation/Ressource; z.B. Syntaxablehnung vor DB-/Keyarbeit oder CAPACITY vor Insert nach vollständig zurückgekehrten DB-/Keyscopes |
| COMPLETE | Alle erforderlichen getrennten DB-/Datei-/Syncbelege, zulässiger Unlink oder frisch belegte Abwesenheit bei Delete, finales konsistentes globales Inventar und sämtliche beobachteten Ressourcen geschlossen |
| RECOVERY_REQUIRED | Bekannte Wirkung/Phase, aber kein vollständiger Abschluss; etwa SOURCE_UNAVAILABLE/TOO_LARGE bei erhaltenem WRITING oder Revoke nach zurückgekehrtem Detach mit Blob |
| UNKNOWN | Unbestätigtes Transaktions-/Datei-/Syncende, Inventar-/Scopefehler, fehlgeschlagenes oder nicht vollständig beobachtetes Cleanup; frühere bestätigte Schritte bleiben erhalten |

DocumentResult.Value oder Rejected setzen diese Werte **nicht**. Das Repository
verwendet technische Stagingquittungen weiterhin nur zur vorhandenen Ablaufsteuerung.
Der Collector berechnet COMPLETE separat aus seinen Schritt-/Scope-/Inventarbelegen.
Ein fehlgeschlagener DB-Open oder ein nicht zurückgekehrter Keyscope bleibt UNKNOWN,
auch wenn der gewünschte DAO-Körper nicht erreicht wurde. Die erfolgreiche leere
Reserve-Transaktion bei CAPACITY wird als zurückgekehrter lokaler DB-Schritt ohne
Dokumentinsert erfasst, nicht als angeblicher SQL-Rollback. Es wird kein allgemeiner
Nachweis behauptet, dass Room beim Öffnen niemals Migrationsarbeit leisten könnte;
aktuelle READY-Autorität und vollständig zurückgekehrtes Öffnen/Cleanup bleiben
Vorbedingungen. Kein unbestätigter Open wird als Nichtausführung klassifiziert.

### 64.3 Produktive DB- und Cleanupgrenzen

Die beobachteten SQLCipher-Dokumentadapter verwenden einen schmalen
`confirmedDocumentTransaction`-Helper auf der bestehenden Writerconnection:
vorher keine laufende äußere Transaktion; IMMEDIATE-Transaktion; vorhandene
DAO-Regeln/Expected-/Action-/COMMIT-Gates; Bestätigung erst nach Rückkehr aus
withTransaction und erneuter Prüfung, dass keine Transaktion mehr läuft.
Generierte DAO-Transaktionen innerhalb dieses äußeren Schritts sind keine
separaten Commitbelege. Begin-/Body-/Endfehler bleiben UNKNOWN; es wird kein
lokaler oder globaler Rollback aus einer Exception geraten. Die Core-
WorkspaceWriteCompletion-/WriteProgress-Semantik wird nicht geändert/verwendet.

Unabhängig davon umschließt `documentEvidenceScope(DB_KEY)` den vollständigen
Rücklauf aus ActionDatabaseAccess **und** SessionDbKeyOwner.withDbKey. Ein schon
bestätigter lokaler Commit bleibt deshalb bei anschließendem Open-/Health-/
Context-/DBclose-/Keyscopefehler bestätigt; Cleanup bleibt unbestätigt und das
Gesamtergebnis UNKNOWN. ActionDatabaseAccess und SessionDbKeyOwner selbst wurden
nicht umgebaut. Alte unbeobachtete Aufrufer/Maintenance nutzen weiterhin ihren
bisherigen Transaktionspfad.

`documentEvidenceUse` erhält die vorhandene use-/Suppressionsemantik und bestätigt
Close getrennt vom Workkörper: Quellen, Cipher-/Outputstreams, Channels und
Cipherinstanzen schließen auch bei Eingabefehlern; erst zurückgekehrter Close
reduziert den Ressourcenstatus. File-Key und kontrollierte Cipher-Keykopie werden
an ihrem tatsächlichen finally-Wipe beobachtet. Snapshot-Keywipe/Close wird am
DocumentPublicationSnapshot selbst einmalig bestätigt. Factory-/Verify-/Inventar-
und native FileOps-Grenzen werden zusätzlich konservativ umschlossen: eine
exceptionelle Rückkehr zertifiziert deren Cleanup nicht optimistisch.

Ein Sync-/Closefehler kann somit trotz später physisch geschlossenem Channel
FAILED_OR_UNCONFIRMED bleiben. Das ist bewusst konservativ: Der Collector
rekonstruiert keinen sauberen Abschluss aus Dateiexistenz oder Exceptiontypen.
Er bestätigt den logischen Java-/Room-/Keyscope-Vertrag; ein neuer vollständiger
nativer Cursor-/Handle-/Flash-Erasebeweis folgt daraus nicht (#32 bleibt offen).

### 64.4 Beobachtete Importfolge

| Produktgrenze | Getrennte Evidence / spätere Fehler |
| --- | --- |
| IMPORT_RESERVED | Eigener äußerer Commit; Job-/Kapazitätsbindung; kein Namespacezugriff vorher |
| IMPORT_WRITING | Zweiter eigener Commit; Revoke danach kann weiterhin rootloses WRITING erhalten |
| Initialisierung / CREATE_NEW / Schreiben | Bestehende Reihenfolge unverändert, keine Vor-WRITING-Anlage. CREATE und Write-Rückkehr getrennt; Fehler kann partielle Struktur/Datei erhalten |
| Cipherstream-Abschluss | Erst nach Rückkehr des verschlüsselten Stream-close; nachfolgende Cipher-/Source-/Channel-/Keyscopes sind weiter separat offen zu berücksichtigen |
| FileChannel.force(true) | Rückkehr am offenen Channel in SyncOnClose; ein fehlgeschlagener Close/Sync verhindert bestätigten Gesamtabschluss |
| IMPORT_CLOSED | Dritter eigener Commit samt Größe; kein globales COMMITTED, kein fachlicher DocumentRow-Erfolg |
| Atomarer Rename | Physischer staging→files-Schritt separat bestätigt; späterer Syncfehler lässt diesen Schritt und die ältere CLOSED-DBphase bestehen |
| files-Sync / staging-Sync | Zwei unabhängige Rückkehrbelege aus den vorhandenen Directory-fsync-/fdclose-Primitiven, keine neuen Syncaufrufe als Reparatur |
| FILE_PUBLISHED | Vierter eigener Commit; späterer Fehler erhält finalen Blob und PUBLISHED-Job |
| DocumentRow-Insert + Jobentfernung | Fünfter eigener äußerer Commit, beide SQL-Effekte lokal atomar; danach Snapshots schließen |
| Frisches globales Abschlussinventar | Neuer READY-Read NACH dem fünften Schritt und nach Publication-Snapshotabschluss; anschließend erst mögliche COMPLETE-Evidence |

Keine Sourcewiederöffnung, kein Klartexttempfile, keine zusätzliche Kryptografie,
kein Retry und keine rückwirkende Umdeutung früherer Commits. Die Beobachtung
ändert FileChannel.force, Tinkformat/-AAD, CREATE_NEW/NOFOLLOW oder Rename nicht.
Verlorene physische Rückkehrbelege bleiben UNKNOWN, selbst wenn ein Hostread
später passende Daten sieht. Keine gemeinsame DB-/Filesystemtransaktion.

### 64.5 Beobachtete Deletefolge

| Produktgrenze | Evidence / erhaltene Wirkung |
| --- | --- |
| DELETE_RESERVED | Erster lokaler Commit; Parent/Blob noch vorhanden, sichtbare Listen können bereits beeinflusst sein |
| Parent-Detach + DELETE_FILE_PENDING | Zweiter Commit; Parent bleibt auch bei nachfolgendem Fehler entfernt |
| Unlink | Eigenständiger physischer Beleg; keine Gleichsetzung mit Directorysync |
| Bereits fehlender Pendingblob | Nur nach den bestehenden frischen DB-/vollständigen Namespaceprüfungen als ABSENT beobachtet; kein neuer rootloser Abbruchpfad |
| files-Directorysync | Eigener Rückkehrbeleg, auch bei bereits fehlendem Blob, unveränderte bestehende Reihenfolge |
| Deletejob-Remove | Dritter lokaler Commit; danach zusätzlicher globaler Abschlusscheck |
| Frisches Abschlussinventar | Keine Jobs/stagings, files exakt die verbleibenden Documents, vollständiger Root; erst mit Cleanup und übrigen Belegen COMPLETE |

Revoke nach Detach oder Unlinkfehler stellt keinen Parent wieder her. Es gibt
keine globale ROLLED_BACK-Klassifikation für einen solchen Auftrag.

### 64.6 Globaler Abschlusscheck, keine zweite Recoveryarchitektur

Kleinste zusätzliche READY-Lesegrenze: DocumentDao.completedInventory verwendet
die bestehenden globalen deleteState-/DocumentStorageRules-Bindungs-/Kapazitäts-
und Keywipe-Regeln, verlangt READY, keine verbleibenden Jobs und gültige
referenzierte Actionzustände. Die Rückgabe enthält nur flüchtige bekannte
Dateinamen, keine Keys. Beide SQLCipher-Adapter bieten denselben schmalen
DocumentFinalInventoryDatabase-Read; kein neuer Persistenzort oder zweite
Inventarimplementierung.

`confirmDocumentFinalInventory` verlangt die bestehende exklusive Namespace-Lane.
Erst nach vollständigem DB-/Keyscoperücklauf folgt `inspectFinal` auf derselben
DocumentFileInventory: vollständiger Root, nur erlaubte bekannte Leaves,
kanonische Pfade/NOFOLLOW, Directory-/File-Rechecks und die vorhandene Prüfung
nicht-null Leaf-fileKeys auf Hardlinkkollision. Der Leafidentitätscheck wurde
lediglich mit dem vorhandenen Recoverycheck geteilt; dessen Verhalten und
fehlender-Root-Regel bleiben erhalten. Nach Identitätsprüfung folgt ein weiterer
strikter Namespacevergleich. Kein mkdir, unlink, rename, Reparatur- oder
Sourcezugriff durch den Abschlusscheck.

Fehlende erwartete Datei, unbekanntes Leaf, Symlink-/Teilroot, Hardlinkduplikat,
fehlerhafte DB-Bindung, verbleibender Job oder fehlgeschlagener Read verhindert
COMPLETE. Wegen konservativ unbestätigter Inventar-/Cleanupgrenze ist das Ergebnis
in diesen Fehlerfällen UNKNOWN. Der Check ist ein frischer autorisierter
Zeitpunktbeleg nach letzter Dokumentmutation, kein über DB und Dateisystem
atomarer Schutz gegen spätere externe Änderungen. Er authentifiziert nicht
sämtliche bestehenden Dokumentinhalte neu; der neue Import wird wie bisher vor
Publication vollständig geprüft. Kein langfristiger Read-/Snapshot-Gültigkeits-
oder READY-Beleg aus dem Inventarflag.

### 64.7 Hostbelege und native Grenzen

**HOSTGETESTET:** Neue ProductiveDocumentEvidenceTest mit 34 Fällen führt die
**produktiven** Evidence-, Producer-, Transaktionsbeobachter-, Staging-,
Publication-, Delete- und finalen DAO-/Inventorypfade aus. DAO-Speicher und
Transaktionsengine sind ausdrücklich Fake; Tinkstreams und temporäre Hostdateien
sind echt. Die Host-FileOps simulieren die Plattformgrenze und zählen tatsächliche
Move-/Unlink-/Forceaufrufe; Hostmove ist kein nativer RENAME_NOREPLACE-Beweis.

Getrennte Assertions: DBphase/Bestand und lokale Commitanzahl, Filesystemstand,
Fileforce-/Directorysync-Versuche und Rückkehr, Cleanup, typisiertes Outcome,
finaler Inventarread nach genau DOCUMENT_COMMIT/DELETE_FINISH, Sourceversuche/
Close und Callbackzahl (bei direkten Koordinatoraufrufen null).

Belegt sind INVALID, CAPACITY vor Insert, SOURCE_UNAVAILABLE und TOO_LARGE nach
WRITING, Revoke nach WRITING ohne Root, Cipherclose/Fileforce vor fehlgeschlagenem
CLOSEDschritt, Fileforcefehler, Rename vor Syncfehler, Fehler nach PUBLISHED,
vollständiger Import, Fehler/Revoke nach Detach, Unlink vor Syncfehler und
vollständiger Delete. Zusätzliche Tests verweigern COMPLETE bei finalem Readfehler
für beide Operationen, Fremdleaf, Hardlinkduplikat, Symlink-/Teilroot, fehlendem
Blob, ungültigen DB-Bindungen und offenem Job. Cleanupfehler nach Commit,
Sourceclosefehler und ein gezielt injizierter Snapshotclosefehler bleiben UNKNOWN;
frühere Commit-/Syncbelege werden nicht verworfen. Der Snapshotfehler ist eine
Hostinjektion am produktiven Close-Beobachter, kein neu beobachteter nativer Fehler.

Echte DocumentProductDispatch-Folgen prüfen einmalige Worker/Mainwirkung,
SOURCE_UNAVAILABLE-Rejectioncallback ohne COMPLETE, einmaliges Blocked statt
Erfolg bei fehlendem finalem Inventar, verlorene Mainzustellung für Import/Delete
und Maintenance-Fence ohne Undo fertiger physischer Evidence. Wiederverwendung
oder nachträgliches Ändern des eingefrorenen Collectors ist verweigert. Ein
Strukturtest bestätigt nur feste Metadatenfelder; kein Heap-/Native-Erasebeweis.

**HOSTGETESTET, echte SQLite separat:** DocumentEvidenceSqliteTest (2 Fälle)
führt denselben produktiven äußeren Transaktionsbeobachter auf der vorhandenen
realen In-memory-SQLite-Harness mit Schema-11-Entities aus: bestätigte Reserve/
WRITING-Schritte bleiben nach späterem echten SQL-Rollback erhalten; physischer
Commit mit Exception vor Rückkehr bleibt unbestätigt/UNKNOWN. Dies ist kein
Ausführungstest der generierten Room-Dokument-DAOs oder der nativen Adapter und
kein zusätzlicher Trigger-/Powerlossbeweis. Die vorhandenen unveränderten drei
Dokument-SQL-Skripte (3+4+4 Fälle) wurden zusätzlich grün ausgeführt und prüfen
gezielt die vorhandenen Phasen-/Publish-/Delete-Constraints und Atomarität.

**NICHT BEWIESEN:** Android-SQLCipher-/Room-End-to-End-Ausführung der beobachteten
Adapter, native Cursor-/Key-/Descriptorlifetimes, Directory-/Parent-fsync- und
Crash-/Powerlossdauerhaftigkeit sowie sichere Androidfixture-Isolation/Cleanup.
Die SQLCipher-Adapter sind produktiv geändert und offline kompiliert, auf dem
Host werden ihre gemeinsamen Helper/Koordinatoren und reale DAO-Regeln geprüft;
das ist ausdrücklich kein vorgetäuschter nativer Adapterlauf. Kein Androidtest,
keine Installation und keine Änderung installierter Appdateien in diesem Slice.
#32 bleibt offen, einschließlich fehlendem explizitem Cursorclosebeleg durch
SQLCipherStatement.close().

Ein erster Host-Kompilierlauf erkannte eine nicht erhaltene zweistellige
withNewKey(c,work)-Aufrufform. Die ursprüngliche Signatur wurde als delegierender
Einstieg beibehalten und die beobachtete Form separat benannt. Bestehende Tests
unverändert; keine Assertion abgeschwächt oder geskippt.

### 64.8 Voraussetzungen für §65 und unveränderte Blocker

**IMPLEMENTIERT:** Beide logischen Producerketten können bei tatsächlichem
Schritt-/Scope-/Inventarabschluss einen DocumentWriteCompletion mit Outcome COMPLETE liefern;
Partial- und unbekannte Fälle bleiben konservativ. Die Producer werden im
Repositorypfad ausgeführt und ihre Evidence im finally eingefroren. **UNWIRED:**
Es gibt noch keinen Konsumenten dieser Completion im gemeinsamen Runtime-Port;
kein beforeDocumentQueue/finishDocument im Repository oder Dispatch und kein
workspaceMutations-Parameter bei ProductRuntime.documents. §62-Bypass-
Characterizations bleiben deshalb weiterhin wahr, keine falsche PASS-Aussage zur
produktiven Invalidierung.

**VORGESCHLAGEN für den separaten §65-Anschluss:** Genau dieselbe Runtime-
Portinstanz und Epoche synchron auf dem Ownerthread vor worker.execute anmelden,
Busy ohne Worker verweigern, Collector/Completion dem einen Ticket zuordnen und
finishDocument erst nach vollständiger Worker-/Ressourcenbeobachtung auf dem
Ownerthread konsumieren. Ein Scope-/Worker-/Mainfehler darf nie künstlich
NOT_EXECUTED oder sauber COMPLETE werden. Die eingefrorene Evidence muss auch
bei Rejection/Revoke/Exception erhalten bleiben; verlorene Mainzustellung darf
ein Pending nicht optimistisch freigeben. Sessionpublication, one-shot Worker/
Main und §60-Lane-CAS bleiben zusätzlich erforderlich. Keine automatische
Cleanmarkierung; DocumentSummary-/Listen-/Overview-Validity und Viewer-/Lifecycle-
Anschluss bleiben vor aktivem Snapshotowner separat zu reviewen (#37/#38).

**BLOCKER:** RED-4 bleibt unverändert fail-closed: rootloses IMPORT_WRITING ist
historisch mehrdeutig; keine Joblöschung, Rootrekonstruktion, Streamwiederaufnahme
oder §61-Stagingumordnung. Reconciliation/Maintenancepolitik nicht geändert.
Native Cleanup-/Cursor-/Fixturegrenze #32 offen; #36 Editor-/CAS-/ABA-Grenzen
unverändert. RAM-Cache/ACTIVE-Navigation sind noch nicht freigegeben, bleiben
aber verbindlicher Release-1.0-Kritischer Pfad.

SessionCoordinator, StorageAccess, SessionDbKeyOwner, ActionDatabaseAccess,
SQLCipher/Tink/B2Driver, Schema 11, Fristen 180/30 s, 8+8-MiB-Budget, Namespace-
Lane, Dispatch/CAS, bestehende Corewriter, Leadership/P3b und Navigation bleiben
unverändert. Keine neue Dependency/Permission/Migration, kein Snapshotowner,
keine RAM-Hits, Geräteinstallation, automatische Recovery, Merge oder Release.

### 64.9 Abschlussvalidierung und Dateiliste

**HOSTGETESTET:** Gezielter Offline-Lauf
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --tests '*Workspace*Test' --tests '*Writer*Test' --tests '*TaskMutationSqliteTest' --tests '*Session*Test' --offline`:
**618 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESSFUL**.
Darin 34 neue Producerfälle und zwei neue echte Host-SQLite-Observerfälle.
Separat bestehende SQL-Skripte: **11 Tests, 0 Fehler, 0 Skips**.

Anschließend vollständig ausgeführt:
`./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest :domain:test :app:assembleDebug :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:verifyPrototypeManifest :app:verifyDemoManifest --offline`:
**BUILD SUCCESSFUL**. Debug-Test-XMLs: App **673**, Data **1.058**, Security **4** =
**1.735 Hosttests, 0 Failures, 0 Errors, 0 Testskips**. App/Data ausgeführt,
unveränderte Securitytests UP-TO-DATE bestätigt. Domain ohne eigene Testquellen;
bestehende Porttests im Data-SourceSet eingeschlossen. Beide Androidtest-
Kompilationen, Debugassemble und beide Manifestchecks erfolgreich ausgeführt.
Gradle-Konfigurations-/Lifecycle-SKIPPED sind keine übersprungenen Testfälle.
Keine Androidinstrumentation, Installation oder native Crash-/Cleanupprüfung.

`git diff --check` sauber; ausschließlich diese zwölf Dateien geändert:

- `data/src/main/kotlin/org/pag/data/product/DocumentMutationEvidence.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentMutationProducer.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImportStaging.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentPublication.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDelete.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileStreams.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductDocumentRepository.kt`
- `data/src/test/kotlin/org/pag/data/product/ProductiveDocumentEvidenceTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentEvidenceSqliteTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

Bestehende Assertions unverändert stark, keine Testskips ergänzt. Keine Logs,
APKs, DBs, Keys oder Testartefakte im Commit. Regulärer WIP-Commit und Push;
Remote-SHA separat über GitHub bestätigen. Danach **STOPP bis zum unabhängigen
GitHub-Review**.

## 65. Gemeinsame produktive DOCUMENT-Invalidierung

**Ausgangsbasis:** §64 / Commit `2d05d549d1e688a6398bcb8b61c6663c945195ae`.
Der vorhandene `WorkspaceMutationInvalidation`-Port aus §63 hatte bereits einen
typisierten `beforeDocumentQueue`-/`finishDocument`-Vertrag. §64 hatte produktive
Evidence-Erzeuger für Import und Delete ergänzt. Beide waren im Repositorypfad
noch nicht gemeinsam angeschlossen. Dieser Abschnitt dokumentiert den nun
implementierten Anschluss und seine Hostbelege; er aktiviert keine RAM-Hits.

### 65.1 Ein Runtime-Port, Admission vor der Queue

**IMPLEMENTIERT:** `ProductRuntime` gibt seine vorhandene
`workspaceMutations`-Instanz jetzt auch an `ProductDocumentRepository`. Es wird
keine weitere `UncachedWorkspaceMutations`-Instanz erzeugt. Task-, Operations-,
Document- und übrige bestehende Writer verwenden damit dieselbe konkrete
Pendingidentität und dieselbe `mutationEpoch`.

`importDocument` und `deleteDocument` rufen über denselben
`DocumentProductDispatch.runMutation` synchron auf dem aufrufenden Ownerthread
`StorageAccess.acquire(READY)` auf. Derselbe erhaltene Kontext wird auf READY,
nichtnegative Generation und aktuellen STEP-Zugriff geprüft und anschließend
`beforeDocumentQueue(actionId, context.generation)` übergeben. Ein fehlender
Port, leere Action-ID, fehlender/veralteter Kontext, falscher Ownerthread,
unsicherer/beschäftigter Port oder abgelehnte Admission plant keinen Worker und
führt keine Arbeit, Sourceöffnung, Datenbank- oder Dateimutationen aus. Der
Callback wird bei Admissionverweigerung nicht aufgerufen; damit entsteht kein
fälschliches CAPACITY- oder Korruptionsresultat. Direkte Repositorykonstruktionen
ohne Port verweigern Dokumentwrites standardmäßig.

Nach erfolgreicher Admission gibt es genau einen Worker-Runnable und genau ein
lokales `DocumentMutationEvidence`. Das Repository führt dieselben §64-Producer
und Koordinatoren aus. Die Evidence wird im Worker nach beobachteten Schritten
eingefroren. `DocumentResult.Value` und `DocumentResult.Rejected` bestimmen den
Abschluss nicht. SOURCE_UNAVAILABLE nach WRITING, TOO_LARGE nach Schreibbeginn,
Fehler nach FILE_PUBLISHED und Fehler nach Parent-Detach können die gemeinsame
Generation nicht sauber freigeben.

### 65.2 Worker, Ownerthreadabschluss und UI-Publication

**IMPLEMENTIERT:** Der Worker ändert weder Portpending noch Mutationsepoche und
ruft `finishDocument` nicht auf. Erst der Main-/Ownerthread-Runnable konsumiert
`evidence.seal()` mit genau einem `pending.finishDocument(completion)`-Versuch.
Ein wahrer Rückgabewert belegt ausschließlich, dass der Metadatenabschluss
konsumiert wurde. `RECOVERY_REQUIRED`, `UNKNOWN` oder unbestätigtes Cleanup
machen die Generation über den bestehenden Port unsicher; daraus folgt weder
ein fachlicher Erfolg noch eine Publication. Ein unsicherer Abschluss wird
niemals als ROLLED_BACK oder harmlose Rejection ausgegeben.

Eine fachliche Antwort kommt erst nach erfolgreichem Abschlussversuch und nur
wenn Ticketidentität/-Generation, `pending.isCurrent()`, derselbe
`StorageContext.publish`-Gate sowie `DocumentNamespaceLane`-Ticket und
`tryAdmitPublication`-CAS gültig sind. §60 P-gegen-F bleibt unverändert. Ein
späterer Corewriter invalidiert ein noch nicht veröffentlichtes altes
Dokumentticket. Revoke nach vollständig bestätigter physischer Mutation macht
diese nicht rückwirkend zu einem Rollback; die UI-Antwort kann aber unterdrückt
werden. Keine Readprojektion wird durch `finishDocument` automatisch sauber.

Die bestehenden `started.compareAndSet`-/`delivered.compareAndSet`-Gates der
generischen Dispatchroute bleiben erhalten. Die Dokumentroute hat entsprechende
Worker-/Main-one-shot-Gates. Wiederholte Runnables wiederholen weder Producer
noch Callback. Wird der Worker nach Admission nicht gestartet, wird nicht
optimistisch freigegeben. Eine nachweisbare Revoke-/Lane-Fence-Verweigerung vor
Eintritt in Produktarbeit kann als tatsächliche NOT_EXECUTED-Evidence
verbraucht werden, veröffentlicht aber bei ungültigem Kontext keinen Callback.
Eine unerwartete Workerexception ohne passende Producer-Evidence wird UNKNOWN.

Ein verlorener oder vom Mainexecutor abgelehnter Main-Runnable lässt das
gemeinsame Pending offen; dieselbe Generation bleibt blockiert. Es gibt keine
zweite Queue und keinen automatischen Retry. Eine neue READY-Generation kann
alte Tickets ablösen. Dafür wurde `UncachedWorkspaceMutations` minimal
angepasst: nur ein nächster Portaufruf mit einer anderen, vom Caller aus einem
tatsächlichen READY-Kontext gelieferten Generation darf ein altes offenes
DOCUMENT-Pending und dessen Unsicherheitsbuchführung verwerfen. Ein
unvollständiges CORE-Pending behält seine bisherige generationsübergreifende
Blockierregel. Bei gleicher Generation bleibt ein unvollständiger Abschluss
terminal blockierend. Der Port
authentifiziert weder Generation noch Maintenance; erfolgreiche autorisierte
Recovery vor einem neuen READY-Kontext ist Caller-Vertrag. Alte Tickets können
danach weder Core- noch Documentarbeit abschließen.

### 65.3 Import, Delete und Readergrenze

**IMPLEMENTIERT:** Ausschließlich `ProductDocumentRepository.importDocument`
und `deleteDocument` verwenden die gemeinsame Admission und denselben
typisierten §64-Abschluss. Der erfolgreiche Import muss alle lokalen Phasen,
Datei-/Directory-Syncs, Snapshot-/Key-/DB-Cleanup und den frischen globalen
Abschlussinventarcheck nach dem letzten DB-Commit belegen. Delete muss
Parent-Detach, tatsächlichen Unlink/zulässige Abwesenheit, Directory-Sync,
Jobentfernung, Cleanup und Abschlussinventar belegen. Ein zurückgekehrter
Unlink allein ist keine Directory-Sync-Evidence. Frühere lokale Commits bleiben
bei späteren Fehlern bestätigt.

`summary`, `list`, `readPdf`, `readText` und `readImage` behalten den normalen
`DocumentProductDispatch.run` beziehungsweise `DocumentReadDispatch`; sie
rufen `beforeDocumentQueue` nicht auf. Viewer-/Payload-/Cleanupverträge wurden
nicht geändert. Die §62-Characterization des früheren DOCUMENT-Bypasses bleibt
im Testbestand; neue Zieltests prüfen zusätzlich den produktiven
Mutationdispatch.

### 65.4 Hostbelege und Grenzen

**HOSTGETESTET, echte Hostdateien + Fake-DAO/Transaktion:** Neue strenge
`DocumentWriterIntegrationTest`-Fälle verwenden eine einzige konkrete
`UncachedWorkspaceMutations`-Instanz mit dem Produktions-Dispatch. Sie prüfen
Core-/Document-Konflikte, Epochenerhöhung genau einmal, Admission vor Worker,
Completion vor Callback, SOURCE_UNAVAILABLE nach WRITING, Revoke und Lane-Fence
vor Worker, Fence zwischen Worker/Main, verlorene/abgelehnte Main-Zustellung,
falschen Ownerthread, unerwartete Workerexception, Replay, sichere neue
Generation und unveränderte Readerdispatches. Ein Runtime-Kompositionstest
prüft, dass dieselbe `workspaceMutations`-Referenz an `documents` geht.

Ergänzende Tests in `ProductiveDocumentEvidenceTest` führen die tatsächlichen
§64-Import-/Delete-Producer mit temporären Hostdateien und Fake-DAO-/Transaktions-
adaptern durch den neuen Dispatch aus. Sie prüfen COMPLETE für beide Operationen,
gemeinsamen Pendingverbrauch und genau einen Callback, SOURCE-Rejection nach
WRITING, TOO_LARGE mit Teilcipherdatei, Fehler nach FILE_PUBLISHED mit erhaltenem
Blob und früheren Commits, Revoke nach Parent-Detach sowie fehlgeschlagenes
Finalinventar. Die vorhandenen
Characterization-Assertions wurden nicht gelöscht. §64s In-memory-SQLite-Tests
und Dokument-SQL-Skripte bleiben getrennte Belegarten; neue §65-Dispatchtests
verwenden Fake-DAO-Transaktionen.

**NICHT BEWIESEN:** Kein Test führt ProductRuntime in einer Android-Activity mit
generierter Room-DAO, SQLCipher, nativem Cursor/Key-/Directory-Handle oder
Powerloss aus. Runtime-Kompositionstest und gemeinsame Portinstanz im
Host-Dispatch belegen Verdrahtung und Portverhalten, aber keinen nativen
Session-/Executorlauf. `StorageContext.publish`- und Lane-Races werden weiterhin
durch getrennte bestehende Deterministiktests geprüft. Android-Instrumentation
und Geräteinstallation wurden nicht ausgeführt.

### 65.5 ACTIVE, Recovery und offene Releaseabhängigkeiten

**UNWIRED:** `ActiveWorkspaceMutationAdapter`/`ActiveWorkspaceValidity` sind
weiterhin nicht produktiv installiert und verweigern DOCUMENT admission
default-deny. Es gibt keine aktive Validityprojektion für DocumentSummary,
Dokumentliste, Workspace-Overview/-Anzahl, Expected-Snapshots oder laufende
Viewer. `UncachedWorkspaceMutations.ramReuseEnabled` bleibt ausnahmslos `false`;
der Abschluss markiert keinen Readstand sauber und hält keinen
Snapshot-/Payloadowner.

**BLOCKER:** RED-4 bleibt bestehen: historisches rootloses IMPORT_WRITING wird
fail-closed behandelt; keine Joblöschung, Rootrekonstruktion oder geänderte
Stagingfolge. Maintenance-/Reconciliationpolitik und Schema 11 bleiben
unverändert. Native Cursor-/DB-/Key-/Fixture-Cleanupbelege aus #32 sowie
SQLCipher-/Room-End-to-End-Beweise fehlen. Aktive Projektionen,
Ownerthread-Lebensdauer, bounded Payloadowner und RAM-Hits bleiben
Voraussetzungen eines separaten Snapshotowner-/Performance-Reviews (#37/#38).
#36 Editor/CAS bleibt unverändert.

**VORGESCHLAGEN als nächster Releasepfad, nicht hier implementiert:** Nach
unabhängigem Review zuerst den autorisierten ACTIVE-Snapshotowner mit konkret
definierten DokumentSummary-/Listen-/Overview-Abhängigkeiten bauen und prüfen.
Danach Viewer-/Lifecycle-Invalidierung, native Cleanupbelege und kontrollierte
RAM-Hittests ergänzen. Kein Portabschluss allein erlaubt RAM-Reuse oder einen
1.0-Release.

**Dateien in §65:**

- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductDocumentRepository.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentMutationEvidence.kt`
- `domain/src/main/kotlin/org/pag/domain/WorkspaceMutationInvalidation.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentWriterIntegrationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/ProductiveDocumentEvidenceTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPendingContractTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

**Validierung:** Der gezielte Lauf
`./gradlew :data:testDebugUnitTest --tests '*Document*Test' --tests '*Workspace*Test' --tests '*Writer*Test' --tests '*Session*Test' --tests '*Runtime*Test' --offline`
bestand mit **629 Tests, 0 Fehlern und 0 Skips**. Der vollständige geforderte
Offline-Lauf aller App-/Data-/Security-/Domain-Tests, Debugassemble,
Androidtest-Kotlin-Kompilierung beider Module und beider Manifestprüfungen
endete **BUILD SUCCESSFUL**. Test-XMLs: App **673**, Data **1.082**,
Security **4**, gesamt **1.760 Hosttests, 0 Failures, 0 Errors, 0 Testskips**.
`domain:test` hat keine eigenen Testquellen; die Porttests laufen im Data-
TestsourceSet. Androidtest-Code wurde nur kompiliert, keine Instrumentierung
oder Geräteinstallation ausgeführt. `git diff --check` war sauber.

**Status:** Der DOCUMENT-Admission-/Completionanschluss ist **IMPLEMENTIERT**
und auf Hostebene **HOSTGETESTET**. ACTIVE-Projektionen und RAM-Hits bleiben
**UNWIRED**. Native Session-/SQLCipher-/Cleanup-/Powerlossbeweise sind
**NICHT BEWIESEN**. RED-4 und #32 bleiben **BLOCKER**.

### 66. Single ACTIVE Snapshot Owner: sichere Teilmenge und RED-Grenze

**Ausgangsbasis:** `52ad11ea595df5d03d0f7b86758ff35b656dd986`; lokaler HEAD,
Remote-HEAD `p11-performance-optimization` und sauberer Arbeitsbaum stimmten
vor dieser Änderung überein. §§43–65 sowie die Produktdefinition, Runtime-,
ViewModel-, Session-, Bounded-Reader-, Mutation- und P3b-Pfade wurden geprüft.

**BESTEHEND:** `ProductRuntime` gibt dieselbe injizierte
`WorkspaceMutationInvalidation`-Instanz an die Corewriter und seit §65 an
`ProductDocumentRepository`. Im normalen Runtime-Aufruf wird ein
`UncachedWorkspaceMutations`-Owner angelegt. Er zählt die gemeinsame
Mutationsepoche und serialisiert Core-/Document-Pendings. Seine
`ramReuseEnabled`-Eigenschaft ist dauerhaft `false`. Das ist die tatsächliche
gemeinsame Writergrenze; es ist noch kein Snapshotowner.

**UNWIRED:** `ProductActiveCoreReader` ist ausdrücklich nicht Teil von
`ProductRuntime` oder `ProductViewModel`. Er nutzt die vorhandenen
`ActiveCoreStore`/`CoreReadAdmission`-Komponenten und wartet im Workerpfad bis
DB-/Keycleanup beendet ist, bevor sein Readresultat zentral publiziert werden
kann. Es gibt aber keinen produktiven vertrauenswürdigen Lieferanten für
`BoundedActiveBinding.permissionRevision`. Die Suche findet diesen Wert nur im
Bounded-Vertrag, in synthetischen Fixtures und in Tests; `Action`,
`WorkspaceSnapshot`, `SessionCoordinator`, `StorageAccess` und das P3b-Repository
stellen keine passende autoritative Revision für einen Core-Snapshot bereit.
Der Wert `0` aus Fixtures ist kein Produktbeleg und wird nicht verwendet.

Zusätzlich bietet der Live-Mutationsport kein lesbares, pending-freies
Readticket, mit dem der Owner exakt dieselbe Mutationsepoche samt Pending- und
UNKNOWN-Zustand atomar an die Corelesung binden könnte. Eine lokale Shadow-Epoche
oder die Compose-Auswahl `selected` würde keine gemeinsame Autorität herstellen.
Deshalb wurde kein produktiver `ProductActiveCoreReader`-Aufruf, kein
`ActiveWorkspaceValidity`-Owner und keine Cache-Retention in Runtime oder
ViewModel installiert. Das ist die sichere Teilmenge: kein neuer Hitpfad und
keine unsichere Permission- oder Epochbehauptung.

Die bestehenden Typen begrenzen auch den möglichen ViewModel-Anschluss:
`ActiveCorePayload` enthält kompakte People-/Taskseiten mit globalen Counts;
`WorkspaceSnapshot`, `OperationalBoard` und `TaskBoard` stehen für andere,
vollständige UI-Verträge. Der Payload wurde weder gecastet noch als vollständige
Liste ausgegeben. `ActiveWorkspaceValidity.Projection` enthält noch keine
Dokumentanzahl, Dokumentliste, DocumentSummary, Expected-Snapshot- oder
Workspace-Overview-Projektion. §65-Dokumentwriter invalidieren bereits den
gemeinsamen Writerport, aber mangels aktivem Snapshotowner gibt es noch keine
entsprechende aktive Readvalidity, die Overview- und Dokumentprojektionen vor
Queue synchron dirty setzt.

**HOSTGETESTET:** Bestehende Tests prüfen den isolierten Reader-/Admission-
Budgetvertrag, Cleanup-vor-Readpublication, Active-Validity-Races,
Session-/Lock-/180-/30-Sekunden-Widerruf, gemeinsame Writer-Epochen sowie die
fail-closed Mutationfälle. In diesem Slice wurde ein gezielter
`ActiveWorkspaceValiditySessionTest` ergänzt: fehlt die vertrauenswürdige
Bindingquelle, wird die erste Readadmission vor READY-Acquisition verweigert;
es entsteht keine Reuseberechtigung. Das ist ein synthetischer Domain-Porttest,
kein ProductRuntime-, Android-, SQLCipher-, P3b- oder End-to-End-Beleg.

**NICHT BEWIESEN / BLOCKER:** Es gibt keinen produktiven, autoritativen
Permission-/P3b-Revisionsanschluss; keinen gemeinsamen Readticket-Ausgang aus
dem tatsächlichen Writerowner; keine synchrone Retentioninvalidierung aus
diesem Owner; keine aktive Dokument-/Overview-Validity; keine
ProductViewModel/Corereader-Komposition; keine Android-Lifecycle-/Keyguard-
Instrumentierung und keine native SQLCipher-/Cleanupmessung (#32). Die
bestehenden Core-/Document-Writer behalten ihre gemeinsame Admission; es wurde
keine Writerfamilie in eine unabhängige Active-Adapter-Instanz umgeleitet.

**VORGESCHLAGEN, nächster konkreter Anschluss:** Ein separater kleiner
Autorisierungs-/Mutationportschritt muss aus dem zentralen READY-Kontext eine
autoritative ACTIVE-Bindung samt echter Permissionrevision bereitstellen und
aus genau derselben produktiven Writerinstanz einen pending-freien
Readticket-Snapshot (gemeinsame Epoche und terminaler Unsicherheitszustand)
liefern. Derselbe Owner muss vor jedem Familien- und Document-Workerstart
synchron die abhängigen Projections dirty/unpubliziert markieren. Erst danach
kann `ProductRuntime` den vorhandenen `ProductActiveCoreReader` an genau diesen
Owner binden, und `ProductViewModel` einen einzelnen vorbereiteten kompakten
ACTIVE-Kern nach abgeschlossenem DB-/Keycleanup und zentralem kurzen
Referenzswap entgegennehmen. Vor UI-Verwendung sind gezielte RED-Tests für
Action-/Generation-/Permissionbindung, alle Writerfamilien, Document-Overview,
Lock/Fence/late callback und Budgetverweigerung erforderlich. Bis zu diesem
Anschluss bleibt RAM-Reuse **UNWIRED** und verweigert.

**Dateien in §66:**

- `app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceValiditySessionTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

**Status:** Gemeinsame Writergrenze und bounded Reader-/Validity-Bausteine sind
**BESTEHEND**; ihre isolierten Verträge sind **HOSTGETESTET**. Der einzelne
produktive ACTIVE Snapshot Owner, trusted Permissionbindung und aktive
Projektionen sind **UNWIRED**. Eine produktive RAM-Freigabe ist **NICHT
BEWIESEN** und bleibt bis zum genannten Portanschluss **BLOCKER**. Keine
RAM-Hits, Geräteinstallation, Migration, Schema- oder Produktänderung wurden
vorgenommen.

## 67. Trusted ACTIVE Read Admission

**Basis:** Remote-HEAD, lokaler HEAD und Branch waren vor der Änderung exakt
`ff9705c4c0e13b0cba94d69191b11fe1e22c009f`; der Arbeitsbaum war sauber. §§43–66,
die Port-/Reader-/Runtime- und Action-/Leadershippfade wurden erneut geprüft.
GitHub Issues #35, #37 und #38 sind weiterhin offen.

### 67.1 Implementierter gemeinsamer Readticket-Port

**IMPLEMENTIERT:** `WorkspaceMutationInvalidation` besitzt jetzt die
read-only Admission `beforeActiveRead(actionId, generation)`, die finale
Ownerthreadprüfung `isCurrentActiveRead(ticket)` und `revokeActiveRead()`.
Nicht vorbereitete Implementierungen verweigern diese API per Default.
`ActiveReadTicket` ist payloadfrei und bindet Action-ID, READY-Generation,
konkrete Ticketidentität/Portinstanz sowie die aktuelle gemeinsame
Mutationsepoche. Es gewährt weder Session- noch Permissionrecht und hält keine
Payload.

`UncachedWorkspaceMutations` bleibt der einzige produktive Epoch-/Pending-/
UNKNOWN-Owner. Er hält höchstens ein Readticket. Readadmission erhöht die
Mutationsepoche nicht und blockiert keine Writer. Jede erfolgreiche Core- oder
Document-Admission widerruft das Ticket synchron auf dem Ownerthread, bevor
der Aufrufer `worker.execute` erreichen kann; der Writer erhöht danach genau
die bestehende gemeinsame Epoche. Der Core-/Document-Abschluss revalidiert kein
altes Ticket. Pending, UNKNOWN/RECOVERY_REQUIRED, unbestätigtes Cleanup,
ungültige Eingabe und Epochenüberlauf verweigern neue Readadmission. Ein
neues Action-/Generationticket und `revokeActiveRead()` invalidieren die alte
Identität. Eine andere Portinstanz kann das Ticket nicht als aktuell
bestätigen.

Worker dürfen `ticket.isLive()` als atomare Widerrufsanzeige lesen. Das ist
absichtlich kein Epoch- oder Sessionstate. Die endgültige Gültigkeit muss auf
dem Ownerthread über `isCurrentActiveRead` und zusätzlich über den unveränderten
READY-`StorageContext`-/`publish`-Gate geprüft werden. Der Port hält während
I/O keinen Monitor. `ProductRuntime` ruft denselben injizierten Mutationowner
bei LOCKED, BLOCKED und close synchron zum Read-Revoke auf; die bestehende
Session-/Key-/Writerpolitik wurde nicht geändert.

**HOSTGETESTET:** `ActiveReadAdmissionTest` prüft freie Admission ohne zweiten
Epochzähler, Inputvalidierung, fremden Port, Threadgrenze, Core- und
Document-Widerruf vor Queue, alle `WorkspaceWriterFamily`-Corezweige,
UNKNOWN/RECOVERY/Cleanupfehler, verlorenen Mainabschluss, Action- und
Generationwechsel, expliziten Revoke, Überlauf und Default-Deny alter
Adapter. Der Test verwendet den echten `UncachedWorkspaceMutations`-Port,
aber synthetische Tickets und keine Session-/DB-/Androidkomposition. Der
Worker-Livenesscheck ist eine Zwischenprüfung; finale Publication wird in
diesem Slice nicht implementiert. `ramReuseEnabled` bleibt `false`.

### 67.2 Action-, Session- und P3b-Bindung

**BESTEHEND:** `StorageAccess`/`SessionCoordinator` liefern den READY-Kontext
und dessen Generation. Der produktive `ActiveCoreStore` liest die Action in
der autorisierten DB-Transaktion und akzeptiert nur `ActionStatus.ACTIVE`.
`ProductActionRepository` routet ACTION_CREATE, ACTION_EDIT und ACTION_STATUS
durch dieselbe `WorkspaceWriteBoundary` und denselben Mutationport. Damit
invalidiert eine lokale Action-/Statusmutation ein Readticket vor dem Worker.
Der Readticket-Port authentifiziert rohe Action-/Generationswerte ausdrücklich
nicht; der künftige Caller muss sie an den zentral erworbenen
`StorageContext` binden und die frisch gelesene Action-Zeile prüfen.

`BoundedActiveBinding.permissionRevision` hat im Produkt keinen autoritativen
Lieferanten. Weder `Action` noch `WorkspaceSnapshot` enthält eine solche
Revision. Deshalb wurde keine Konstante, UI-Zählung, Hash- oder Timestamp-
Heuristik eingeführt und keine Permissionfreigabe behauptet. Für den
nicht-sensitiven kompakten Kern zeigen die geprüften Pfade keinen separaten
P3b-Zugriffsgrant: die Produktfreigabe beruht auf READY plus ACTIVE-Action;
P3b-Daten und `LeadershipGrant` sind aus dem kompakten Kern ausgeschlossen.
Eine spätere Binding-API muss daher entweder genau diese belegte
READY/ACTIVE-/gemeinsame-Epoche-Regel explizit typisieren oder einen echten
globalen Berechtigungsvertrag ergänzen. Die bestehende numerische
`permissionRevision` darf nicht mit 0 gefüllt werden.

**P3b-Mutationsmatrix, BESTEHEND:** `LeadershipRow.epoch` ist eine
Root-Epoche; `SensitiveRow.revision` ist nur pro Action/Person/Area.
Setup schreibt zunächst RESERVED/Root und später READY sowie Device-Key in
getrennten Schritten. Save erhöht die Area-Revision, löscht bei leerem Inhalt
die Area oder fügt/aktualisiert sie. DeleteArea entfernt nur die Area.
DeletePerson entfernt die P3b-Personzeilen. DeleteAction schreibt erst
DELETE_PENDING, widerruft Grants, löscht den Device-Key und entfernt danach
Rows in einem späteren DB-Schritt. `LeadershipGrant` bindet Root-Epoche,
Area-Revision und erwartete Werte; `revoke()` widerruft flüchtige Grants.
Diese Werte sind keine globale ACTIVE-Revision: Area-Revisionen sind lokal,
Delete/Neuanlage kann wieder bei Revision 1 beginnen, Root-Änderungen erfassen
nicht alle Area-Schritte, und fachliche ABA-Werte sind keine Revisionsbelege.

Die untersuchten P3b-Schreibpfade verändern nur Leadership-/Sensitive-Area-
Datensätze bzw. Device-Key und Grantzustand; sie schreiben nicht Action-,
Person-, Operations- oder Taskkernzeilen. Da der ACTIVE-Kern keine P3b-Payloads,
Grants oder Root-/Area-Metadaten hält, wurde keine zweite P3b-Pendingregistry
oder künstliche globale Revision hinzugefügt. Ein künftiger Snapshot, der
P3b-Abhängigkeiten einschließt, braucht vor Produktivnutzung einen eigenen
synchronen Invalidierungsvertrag für Setup, Save, DeleteArea, DeletePerson und
alle Phasen von DeleteAction. P3b-Grenzen wurden in diesem Slice nicht
produktiv an den Workspaceport angeschlossen.

### 67.3 Unveränderte Ownergrenze und Blocker

**UNWIRED:** `ProductActiveCoreReader` und `ProductCompactTaskReader` werden
weiterhin nicht aus `ProductRuntime`/`ProductViewModel` aufgerufen.
`ActiveWorkspaceValidity` bleibt ein separater, nicht installierter
Vertragsbaustein; er wurde nicht als paralleler Writerowner verwendet. Es gibt
noch keinen einzelnen ACTIVE Snapshotowner, keine retained Payload und keine
RAM-Hits. Der kompakte People-/Taskseitenpayload wird nicht als vollständiges
`WorkspaceSnapshot`, `OperationalBoard` oder `TaskBoard` dargestellt.

**BLOCKER für Permissionbinding:** kein typed produktiver Vertrag verbindet
`StorageContext` READY-Generation, frischen ACTIVE-Action-Read und das neue
Readticket mit dem vorhandenen `BoundedActiveBinding`. Die vorhandene
`permissionRevision` ist dafür unbelegt. Ein separater globaler P3b-Zähler wäre
fachlich falsch für den P3b-freien Kern und würde für P3b zusätzlich keinen
ABA-sicheren Nachweis liefern.

**NICHT BEWIESEN:** keine native Room-/SQLCipher-/Key-/Cursor- oder
Powerlossprüfung, keine Android-Lifecycle-/Keyguardinstrumentierung, kein
physischer Heap-Löschbeleg. #32 bleibt offen. RED-4 und die unveränderte
Dokument-Reconciliation bleiben fail-closed.

**Voraussetzung für den nächsten ACTIVE-Snapshotowner:** den
`BoundedActiveBinding`-Vertrag eng an die belegte READY-Generation, frisch
gelesene ACTIVE-Action und das exakt aktuelle Readticket binden (ohne
`permissionRevision = 0`); den Reader nur unter diesem Ticket ausführen; nach
DB-/Keycleanup auf dem Ownerthread finale Ticket- und StorageContext-Prüfung
vor dem kurzen Referenzswap verlangen. Dazu müssen Lifecycle-, Actionwechsel-
und explizite Revoke-Hooks denselben Port synchron widerrufen. Erst der danach
reviewte kompakte ViewModel-Pfad darf einen einzelnen Kern halten. Document-
Overview-Validity, Viewer-/P3b-Scope und tatsächliche RAM-Hits bleiben weitere
separate gates.

**Dateien in §67:**

- `domain/src/main/kotlin/org/pag/domain/WorkspaceMutationInvalidation.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/test/kotlin/org/pag/data/product/ActiveReadAdmissionTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

**Status:** Readticket-API und gemeinsame Epoche sind **IMPLEMENTIERT** und
gegen den echten Hostport **HOSTGETESTET**. Action-/READY-Autorität ist als
bestehende Daten-/Sessiongrenze untersucht, aber noch nicht als neuer
Snapshotbinding-Vertrag verdrahtet. Globale Permissionrevision, P3b-
Invalidierung und Snapshotowner bleiben **UNWIRED**. Native Grenzen sind
**NICHT BEWIESEN**; #32 und RED-4 bleiben **BLOCKER**. Keine RAM-Hits,
Schemaänderung, Migration oder Geräteinstallation.

## 68. Safe Revoke + Trusted ACTIVE Core Binding

**Basis:** Remote-Branch, lokaler Branch und HEAD waren vor der Änderung auf
`15def393a427894808355541c2c19601c7cbc405`; der Arbeitsbaum war sauber.
GitHub-Issues #35/#37 wurden auf die §67-Reviewgrenze geprüft. §§65–67 sowie
Session-, Runtime-, Mutationport-, ActiveCore- und Action-/Leadershippfade
wurden untersucht.

### 68.1 Session-Revoke auf fremdem Thread

**HOSTGETESTET – RED reproduziert:** Ein neuer Test verwendet den echten
`SessionCoordinator`, synthetische Authentifizierung und kontrollierbare
MonotonicTime. Nach Ausstellung eines echten `ActiveReadTicket` ruft ein
Worker `StorageContext.check()` nach Ablauf der READY-idle-Frist auf. Der
Coordinator invalidiert synchron und ruft seinen Observer auf demselben
Worker auf. Vor dem Fix warf der Runtime-Observer dort die
Ownerthread-Assertion von `revokeActiveRead()`; der Sessionzustand wechselte
bereits zu LOCKED, aber das Ticket blieb live. Das war eine echte
Session-/Port-Threadgrenzenverletzung, keine bloße Source-Substring-Annahme.

**IMPLEMENTIERT:** `UncachedWorkspaceMutations` hält die eine aktuelle
Readticket-Referenz jetzt in einem `AtomicReference`. `revokeActiveRead()`
führt thread-sicher ausschließlich `getAndSet(null)` und den Widerruf der
konkreten Ticket-Liveness aus. Sie verändert weder Pending noch gemeinsame
Mutationsepoche noch Ownerthread-Admission. Admission, finale
`isCurrentActiveRead`-Prüfung, Writerabschluss und Epochbuchführung bleiben
ownerthread-gebunden. Worker dürfen weiterhin nur `ticket.isLive()` lesen;
das ist keine finale Publicationautorität. Es gibt keine zweite Registry,
Epoche oder Sessionautorität.

**HOSTGETESTET:** Der RED/GREEN-Test prüft Idle-Timeout bei 30 Sekunden,
absolute Frist bei 180 Sekunden, Sessionzustand und fehlenden READY-Kontext
nach Sperre, Ticketwiderruf ohne Wiederbelebung, BLOCKED aus Worker-Kontext,
expliziten Lock, mehrfachen/reentranten Observer-Revoke und einen
ownerthread-eigenen Writerstart nach workerseitigem Ticketwiderruf. Die
Session bleibt nach LOCKED/BLOCKED gesperrt; Revoke allein erhöht die Epoche
nicht; ein späterer Writer erhöht nur die vorhandene gemeinsame Epoche und
behält die normale Pendingautorität.

### 68.2 P3b-freier Kern und Bindinggrenze

**BESTEHEND:** `ActiveCoreStore` liest Action und ACTIVE-Status innerhalb der
autorisierten Room-Readtransaktion und schließt P3b-Grants, SensitiveArea-
Metadaten/-Payload, Dokumente, Protokollvolltexte, Memory-Reports, Keys und
DB-Leases aus seinem kompakten Resultat aus. Für diesen Kern ist kein
LeadershipGrant erforderlich. Das belegt jedoch erst die DB-seitige
ACTIVE-Prüfung, nicht die vollständige READY-/Ticketbindung eines
produktiven Callers. P3b-Views bleiben separat und kurzlebig; spätere
P3b-abhängige Projektionen brauchen eigene Invalidierung.

**NICHT BEWIESEN / BLOCKER für B:** Die öffentliche
`BoundedActiveBinding`-Form verlangt weiterhin `permissionRevision`, während
kein autoritativer globaler Wert für den P3b-freien Kern existiert. Der
`ProductActiveCoreReader` erwartet außerdem vor dem DB-Read einen
`CompactTaskState` samt Caller-`currentState`; es gibt keine bestehende
typisierte Factory, die zentral erworbenen READY-`StorageContext`, passende
Generation, gemeinsames Ticket/Epoche und danach frisch transaktional
bestätigte ACTIVE-Action zusammenbindet. `permissionRevision = 0`, UI-Zähler,
P3b-Root-/Area-Revision oder ein Callbackwert wären unbelegte
Ersatzautorität. Deshalb wurde kein Bindingtyp durch bloße Benennung als
vertrauenswürdig ausgegeben.

### 68.3 Reader-/Publicationgrenze

**UNWIRED:** `ProductActiveCoreReader` bleibt außerhalb von `ProductRuntime`
und `ProductViewModel`. Der generische `boundedProjectionRead` publiziert
sein lokales Resultat aktuell innerhalb der Workerarbeit über
`StorageContext.publish`; die geforderte finale gemeinsame Ticketprüfung und
zentrale Publication auf dem Ownerthread existieren noch nicht. Ein
Runtime-Aufruf ohne Refaktor dieses Rückgabe-/Publicationpfads könnte nach
Writer, Revoke oder Callback-`false` keine sichere Übergabe an einen späteren
Snapshotowner belegen. Deshalb wurde der Reader in diesem Slice nicht
angeschlossen und kein RAM-Hit aktiviert.

**VORGESCHLAGEN, nächster begrenzter Slice:** Einen P3b-freien, ausdrücklich
typisierten Core-Readrequest mit Action-ID, READY-Generation und Ticket aus
genau derselben Runtime-Portinstanz einführen; die Action innerhalb der
autorisierten Readtransaktion frisch als ACTIVE bestätigen; die vorhandene
gemeinsame `CoreReadAdmission` beibehalten; DB-/Keycleanup vollständig vor
Rückgabe bestätigen; danach auf dem Ownerthread Ticket/Epoche und
`StorageContext` erneut prüfen und nur über eine kurze zentrale Publication
an genau einen Snapshotowner übergeben. Vor Retention sind RED-Tests für
Writer während I/O, Revoke, Generation-/Actionwechsel, verlorene/doppelte
Mainzustellung und `publish`-Callback-mit-`false` erforderlich. Der
`BoundedActiveBinding`-/Pagingvertrag muss dabei ohne erfundene
Permissionrevision refaktoriert werden; Cursorbindung und Budgettests bleiben
erhalten.

**NICHT BEWIESEN:** Keine Room-/SQLCipher-/native Keycleanup- oder
Cursorinstrumentierung, keine Android-Lifecycle-/Keyguardprüfung, keine
physische Heaplöschung. #32 bleibt offen; RED-4 bleibt fail-closed. Es gibt
keinen Snapshotowner, keine produktive Permissionrevision und keine RAM-Hits.

**Dateien in §68:**

- `domain/src/main/kotlin/org/pag/domain/WorkspaceMutationInvalidation.kt`
- `data/src/test/kotlin/org/pag/data/product/ActiveReadSessionThreadTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

**Status:** Thread-sicherer Ticket-Revoke ist **IMPLEMENTIERT** und mit dem
echten Host-`SessionCoordinator` **HOSTGETESTET**. Trusted P3b-freier
Bindingvertrag und produktive Runtime-Readerverdrahtung bleiben **UNWIRED**;
ihre sichere Freigabe ist **NICHT BEWIESEN** und bleibt **BLOCKER** für
Snapshotowner/RAM-Reuse. Es gab keine Schemaänderung, Geräteinstallation,
P3b-Payloadretention oder RAM-Hits.

## 69. Productive Trusted ACTIVE Core Read

**Basis:** Lokaler HEAD, Branch und unabhängig abgefragter Remote-HEAD waren
`375ef3075e52a76c8ae283b383c6fa125f42d903`; der Arbeitsbaum war sauber.
Der §68-Review in #35 akzeptierte den Threadfix und verlangte den echten
typed Readeranschluss. Der Review in #37 hob zusätzlich die alte
`permissionRevision`-Abhängigkeit, die Workerpublication und den Bedarf an
finalem Ownerthread-Ticket-/Sessiongate hervor. §§65–68 und die unten
genannten Runtime-/Readerpfade wurden geprüft.

### 69.1 P3b-freie Core-Bindung

**IMPLEMENTIERT:** `P3bFreeActiveCoreBinding` enthält nur Action-ID und
READY-Generation. `CompactTaskState.mutationEpoch` ist separat und wird im
Produktpfad ausschließlich aus dem `mutationEpoch` des echten gemeinsamen
`ActiveReadTicket` gebildet. Der historische Typname
`BoundedActiveBinding` ist nur ein Typealias ohne `permissionRevision`;
es gibt keinen 0-/Null-Sentinel, UI-Zähler, Hash oder zweite Epoche. Das
Binding selbst ist kein Session- oder Berechtigungstoken.

Der Produktpfad erhält seine tatsächliche Autorität aus dem auf dem
Ownerthread erworbenen READY-`StorageContext`, derselben Generation, dem
Ticket der injizierten gemeinsamen `WorkspaceMutationInvalidation`-Instanz
und der frischen ACTIVE-Zeile innerhalb der vorhandenen autorisierten
Room-Readtransaktion. `ActiveCoreStore` prüft Action-ID und ACTIVE-Status
nochmals am materialisierten Ergebnis. P3b-Grants, SensitiveArea-Daten,
Dokumente, Protokollvolltexte, Memory-Reports, Schlüssel, Leases und Cursor
sind keine Core-Payloadbestandteile. P3b-Berechtigung und P3b-abhängige
Projektionen sind damit nicht freigegeben.

**BESTEHENDE isolierte Belege:** Die Bounded-/Compact-Hosttests behalten ihre
SQLite-Hostseams, Budgetgrenzen, Seitengrößen und Cursorbindung. Die
unbelegte Permissionrevision ist aus diesem Bounded-/Compact-Vertrag entfernt;
Änderungstests prüfen Action-ID, Generation und gemeinsame Epoche statt einer
fingierten Revision. Diese SQLite-Hosttests sind kein Room-/SQLCipher-
Instrumentierungsbeleg.

### 69.2 Produktive Runtime-Readerverdrahtung

**IMPLEMENTIERT:** `ProductRuntime.activeCore` ist der produktive
Ladeeingang. Runtime übergibt ihm genau ihre bestehende
`workspaceMutations`-Instanz, `SessionCoordinator`/`StorageAccess`, Worker-
und Main-Executor, Inventar und den vorhandenen `SessionDbKeyOwner`. Der
Loader erstellt keine weitere Portinstanz, Pendingregistry, Epoche oder
Payloadregistry.

Auf dem Runtime-Ownerthread holt der Loader einen zentralen READY-Kontext,
prüft Generation und Session und ruft danach synchron `beforeActiveRead` auf
dem gemeinsamen Port auf. Bei fehlender Admission wird kein Worker gestartet.
Ein zweiter Kontext wird nicht erworben. Der Worker erhält denselben
`StorageContext` und das originale Ticket; `ticket.isLive()` ist nur ein
zusätzlicher Widerrufshinweis. `ProductActiveCoreReader.readForTicket` führt
die bestehende `DocumentNamespaceLane`-Fence, den vorhandenen
`ActiveCoreStore`, die Compact-People-/Task-Stores und genau eine bestehende
`CoreReadAdmission` im 8-MiB-retained-/8-MiB-transition-Vertrag aus. Die
cursortragenden temporären Compact-Seiten werden vor der Rückgabe in
`ActiveCorePeoplePage`/`ActiveCoreTaskPage` ohne Cursor, aber mit `hasMore`,
überführt. Die vollständigen Room-, DB-Key- und Statementscopes müssen vor
der Rückgabe geschlossen sein. Der Reader publiziert auf diesem Pfad nicht
selbst und legt keinen Slot an.

### 69.3 Ownerthread- und zentrale Publicationgrenzen

**IMPLEMENTIERT:** Der Main-Runnable ist einmalig konsumierbar. Er prüft auf
dem Ownerthread die Loadidentität, das Originalticket über
`isCurrentActiveRead`, Ticket-Liveness, Action-ID, Generation, gemeinsame
Epoche, ACTIVE-Status des gelesenen Actionresultats, Sessionkontext und
Namespace-Lane-Epoche erneut. Erst danach wird `StorageContext.publish`
aufgerufen. Dessen Callback ändert nur ein lokales Boolean und kann keinen
Payloadowner/Slot installieren. Liefert `publish` nach Callbackausführung
`false`, wird der Callbackkandidat verworfen, das Ticket widerrufen und keine
Resultatübergabe ausgeführt. Bei `true` werden Kontext und Ticket nochmals
geprüft, bevor der einmalige Callback den transienten `ActiveCoreResult`
erhält. Worker- oder Main-Executor-Rejection lässt keine erfolgreiche
Publication entstehen; falscher Thread, doppelter Worker und doppelter Main-
Runnable werden fail-closed behandelt.

**HOSTGETESTET:** `ProductActiveCoreLoaderTest` verwendet den echten
`SessionCoordinator`, `UncachedWorkspaceMutations`, echte Tickets und
deterministische Executor-/Readseams. Geprüft werden READY-Ablehnung,
Core-/DOCUMENT-Pending und UNKNOWN, korrekte Action-/Generation-/Epoch-
Bindung, gemeinsames Ticket, Writer während Workerread, Writer nach Read
und Cleanup vor Main, Sessionablauf auf Worker, falsche Action/Status,
callback-then-false, Executor-Rejection, fremder Mainthread, doppelte
Workerausführung, doppelte Mainausführung, verlorene Mainzustellung und
`ramReuseEnabled == false`.
Diese Tests prüfen den Loader-/Portvertrag; sie simulieren nicht Room,
SQLCipher, Android-Keyscope, native Cursor oder Powerloss.

Die bestehenden `ActiveCoreStoreTest`-/Compact-/Bounded-Tests führen die
tatsächlichen Store-SQL-/Validierungs-/Budgetpfade gegen eine SQLite-
Hostinfrastruktur aus. Das belegt die Hoststorelogik und ihren gemeinsamen
Budgetentscheid, nicht den Android Room-/SQLCipher-Adapter oder reale
Device-Keycleanupgrenzen. Es wurden keine Android-Instrumentationstests oder
Geräteinstallationen ausgeführt.

### 69.4 Snapshotowner und Releasegrenzen

**UNWIRED:** `ProductViewModel` hält und verwendet den neuen Kandidaten noch
nicht. Der Callback ist ein transienter Ladeabschluss, kein RAM-Hit und kein
`WorkspaceSnapshot`, `OperationalBoard` oder vollständiges `TaskBoard`.
Kompakte Seiten behalten ihre expliziten globalen Counts und Paginggrenzen;
sie werden nicht als vollständige Personen-/Aufgabenlisten dargestellt. Es
gibt weiterhin genau keinen produktiven Snapshotowner und keine RAM-Hits.

**NICHT BEWIESEN / BLOCKER:** Keine native SQLCipher-/Room-/Cursor-/Keycleanup-
oder Fixture-Isolierungsbestätigung (#32), keine Android-Lifecycle-/Keyguard-
Prüfung, keine reale 100–150-ms-/p95-Messung (#38), keine physische
Heaplöschung und keine P3b-/Viewer-/Lifecycle-Retentionautorität. RED-4 bleibt
fail-closed. Ein `callback-then-false` kann den lokalen Callbackeffekt nicht
rückgängig machen; der §69-Pfad verhindert aber, dass dieser Effekt selbst
eine dauerhafte Retention autorisiert, weil innerhalb des Publishcallbacks
kein Produktcallback/Slot geschrieben wird.

**Voraussetzung für den ersten sicheren RAM-Hit:** Ein separater
ViewModel-Slice muss genau einen instanzlokalen Snapshotowner an diese
transiente, bereits final geprüfte Übergabe anschließen; er braucht einen
atomaren kurzen Referenzswap, synchrones Unpublishing bei Writer-/Session-/
Action-/Lifecycleverlust und RED-Tests für callback-false, verspätete Main-
Runnable, fremde Generation, UNKNOWN und Cleanupfehler. Erst danach sind
kompakte ACTIVE-UI-Navigation und Messung sinnvoll. Bis dahin bleiben alle
RAM-Hits deaktiviert.

**Dateien in §69:**

- `domain/src/main/kotlin/org/pag/domain/BoundedActiveRead.kt`
- `domain/src/main/kotlin/org/pag/domain/CompactActiveTasks.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductActiveCoreReader.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductActiveCoreLoader.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- Bounded-/Compact-Hosttests und `ProductActiveCoreLoaderTest.kt`
- `docs/product-p11-active-workspace-assessment.md`

**Status:** Typisierte P3b-freie Core-Bindung und produktive Runtime-/Reader-
Verdrahtung sind **IMPLEMENTIERT**. Sequenz-, Ticket-, Session- und
Ownerthread-Loaderverträge sind **HOSTGETESTET**; Store-SQL ist separat
SQLite-hostgetestet. Ein dauerhafter Snapshotowner/RAM-Hit bleibt **UNWIRED**.
Room-/SQLCipher-/Native-/Lifecycle-/Cursorbelege sind **NICHT BEWIESEN**;
#32, RED-4 und #38 bleiben offene **BLOCKER**. Keine Schemaänderung,
Migration, Geräteinstallation oder RAM-Hits.
