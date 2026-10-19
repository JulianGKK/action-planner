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
