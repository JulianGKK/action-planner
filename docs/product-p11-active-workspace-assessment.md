# P11 — Active Action Workspace Assessment

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

Samsung SM-M526BR, RFCT1186YRJ, API33. Je Variante/Größe zehn Wiederholungen,
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
