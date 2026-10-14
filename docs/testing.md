# Prüfplanung — P6 Aktionsprotokoll

## Status und Umfang

Dieses bislang nicht vorhandene Dokument wird als fokussierter **P6-Testplan**
angelegt; es ersetzt keine historischen Testnachweise anderer Slices und behauptet
keine bereits ausgeführten P6-Tests. Fachvertrag: [P6a.1](product-p6a-protocol.md),
technischer Plan: [P6b](product-p6b-protocol-implementation.md).
Referenzen: ARCH-02/09/10/23/26/30/34/36/37, PRIV-03/06/07/11,
SEC-01/05/08/09/10/11/15/17 und RET-02/03/09; Bedrohungen TM-06/09,
TM-F03/06/08/11/12/13/15/18. Nur synthetische Daten.

## Domain und Zeit

- Trim, Pflichttitel 1..120, optionale Leertexte null; Unicode-Codepoints mit
  Supplementary-Zeichen für sämtliche 2000/1000/80/120-Grenzen, keine stille Kürzung.
- 19 eindeutige statische Templatekeys; free null, keine Unique-Regel für Verwendung,
  Vorlage ohne Save erzeugt nichts und klassifiziert keine Rechtslage.
- Quelle optional; REPORTED ohne/mit optionaler Person, fremde Aktion reject;
  andere sourceType mit ReportedBy reject, Ansagetext/WORDING-PARAPHRASE-Paar.
- Betroffene als Menge, mehr als eine Person und keine künstliche kleine Grenze;
  Identifier 0/32 erlaubt, 33 reject, gleiche Werte nicht automatisch zusammengeführt.
- ORIGINAL=1, nächster Append n+1; current nach Nummer bei rückwärtslaufender Uhr;
  MARKED_MISTAKEN sichtbar, ADDENDUM bewahrt Marker, bewusste CORRECTION hebt ihn auf.
- ProductTime Gap/Overlap reject, genau ein Offset, Ereignis-/Erfassungszeit getrennt;
  Draftzeit fixiert, Save-/Retryzeit neu, occurredAtApproximate sichtbar.

## Echte Storage-/Repositorytests

- Fresh Schema9 und Migration 6→7→8→9 (sowie historische Upgradepfade) mit echter
  SQLCipher/Room-Engine: alle alten Tabellen/Rows/Trigger/Ciphertexts erhalten,
  neue P6-Struktur leer; kein destructive fallback. Exportvergleich und tatsächliche
  sqlite_master-Triggerprüfung, nicht nur Room-Schema-Datei.
- Direktes SQL: Same-Action-Entry-/Revision-/Person-FKs, passende Unique-Parentindizes,
  ORIGINAL ausschließlich erste Revision, Nummern-/Typ-/Boolean-/Ansagepaartrigger;
  reported_by nur bei REPORTED, Identifierposition 0..31 und maximale 32 Rows.
- Entry+Original+Children atomar; Revision+Children atomar. Fehler injizieren bei
  jedem Childtyp/Insert und vor Commit; keine leere Entryrow oder halbe Fassung.
  Keine catch+Rejected-Success-Transaktion nach bereits erfolgtem Insert.
- Deterministisch zwei Writers auf denselben Expectedstand: genau ein Append,
  andere Operation STALE. Unique Nummer, kein Lost Update; frühere Rows und
  Children unverändert. Personenrelationstrennung ohne Nummernänderung macht
  alten vollständigen Expectedstand STALE, kein unbemerktes Restore.
- ACTIVE/COMPLETED alle vorgesehenen P6-Pfade; DRAFT/PREPARATION/DELETION_PENDING
  auch Read verweigern. Fehlende/inkonsistente Originalfassung kein Empty.
- COMPLETED-Test gemeinsam mit P4/P5a: P6 create/revise/mark erlaubt, P4-Snapshot/
  Plan/Rollenwechsel und P5a create/edit/transition/delete bleiben verweigert;
  Action bleibt abgeschlossen, kein Reopen oder generisches writable(COMPLETED).
- Draft ACTIVE→COMPLETED: STALE bis bewusst bestätigter neuer Kontext;
  Entry.createdAfterCompletion dauerhaft, recordedAfterCompletion pro Revision;
  Uhr zurückstellen und occurredAt korrigieren darf Marker nicht beseitigen.
- Personen-FKbereinigung ausschließlich Betroffenen-/ReportedBy-Rows; Entry/
  Revision/Identifier/Texte erhalten. Keinen echten unimplementierten Personen-
  löschworkflow behaupten: Integration über kontrollierte synthetische DB-Fixture.
- Gesamte synthetische Action geordnet einschließlich bestehender P4/P5a-
  RESTRICT-Abhängigkeiten entfernen: keine P6-Reste in allen fünf Tabellen.
- Echte Gates/Driver: revoke vor COMMIT-prepare und -step → Rollback auf derselben
  Connection; Commit bereits irreversibel zugelassen → darf physisch fertig sein,
  aber keine stale Publication. Worker-/main-Barrier, alte Generation/Action/Request
  abweisen. DB-/DAO-Handles geschlossen, keine Sessioninteraction durch Ergebnisse.
- SQLCipher-Persistenz/erneutes zulässiges Öffnen zeigt alle Fassungen konsistent,
  bestehende Inventory-/Key-/P3b-Verträge erhalten; keine neue Kryptografieprüfung.

## Privacy und Datenzugriff

- Querybeobachtung mit synthetischen Markertexten: Summary selektiert ausschließlich
  Anzahl/Originalzeit; Timeline ausschließlich schmale aktuelle Projektion.
  Keine Identifier, Description, Ansage, Rechtsgrundlage, P3b oder unnötigen Personen.
  SELECT-*+UI-Hiding gilt nicht als bestandener Datenminimierungstest.
- Detail nur Entry-eigene Fassung/Children und benötigte P3a-Pseudonyme; keine
  automatisch geladenen Task-/Operations-/Sensitiveboards oder historische Namen.
- Keine globale Identifier-/Such-/Matchingstruktur, sensitive Logs/Fehlertexte,
  neue Permission/INTERNET/Komponente/Dependency, Export, Clipboard-Automatik,
  Dateien/Medien, Worker/Notification oder neue Authentifizierung.
- Normale append-only-API besitzt keine Update-/Einzel-Delete-Methode.
  Privacy-/Retentionlöschung bleibt möglich, keine forensische Garantie daraus ableiten.

## Spätere UI-/Lastabnahme

- Hub→Protokoll→Vorlage/frei→Detail→CORRECTION/ADDENDUM/Irrtum;
  Chronologie/Filter nach occurredAt und Zeitkorrektur, Keyevent/Nachtrag als Text.
- COMPLETED-Nachträge mit occurredAt und tatsächlicher recordedAt sichtbar,
  Original nach Abschluss markiert, ACTIVE-Original nicht nachträglich umetikettiert.
- Atomic Initial Reveal, Refresh erhält gültigen Inhalt, Stale/Lock aus Detail/
  Entwurf leert Fokus/Filter/Disclosure; kein SavedState oder alter G2-State.
- Font Scale 1/2, schmaler Phone, große Touchziele, TalkBack, Keyboard/Cancel/Save;
  nicht-invasive synthetische Galaxy/API33-Probe ohne Credentialänderung/pm clear/uninstall.
- Technische Testlasten ohne Produktobergrenze: 1000 Entries×3 Fassungen,
  1 Entry×1000 Fassungen, maximaler Inhalt/32 Identifier und 500 synthetische
  betroffene Personen. Zeiten/RAM/Queryplan/Abbruch bei Deadline messen,
  Detail gebündelt statt N+1; keine unbelegte Latenz- oder Manipulationssicherheitszusage.

## Nachweisstand

P6b selbst: ausschließlich Markdown-/Verweis-/Scopeprüfung und git diff --check.
Keine Migration, Code-/Schemaerzeugung oder Unit-/Gerätetests ausgeführt.
P6c-Abnahme muss echte Testergebnisse mit Testumfang, Plattform, Datum und Grenzen
nachtragen. Rechtliche Zulässigkeit/Retentiondauer/manuelle Inhaltsprüfung sind
keine durch Unit-Tests ersetzbaren Freigaben für Echtdaten.


## P6c1 — konkrete Implementierung und Nachweisverweis

ProtocolRulesTest prüft reine Domainregeln ohne neue Domain-Testdependency;
die bestehende Data-Unit-Testkonfiguration stellt JUnit bereit. ProtocolProjectionTest
prüft die tatsächlich von Room/KSP erzeugten Querystrings auf minimierte Spalten/
Tabellenzugriffe, zusätzlich zu DTO-/Portoberflächen. ProtocolStorageTest prüft
reale SQLCipher-/Roompfade mit RAM-Testkeys im separaten Data-Testpaket.

Coverage: Repository-Port, vollständige Fassungen/Children, Same-Action-FKs,
Sequenz/Unveränderlichkeit/Identifier-Slots, Irrtumszustände, persistierte Phase,
stale Snapshot samt Relationstrennung, kontrolliert konkurrierende Appends,
Childfehler-Rollback, Commitrevoke, Generationspublication, 6→7-Migration/
Migrationrevoke, P4/P5a-COMPLETED-Regression und logische FK-Kaskaden.
Historische Migrationfixtures entfernen neue P6-Testtabellen und seit P7 auch
Materialtabelle/Detach-Trigger vor gezieltem Downgrade und erwarten danach Schema9.
Keine echten Produktdaten/Gerätecredentials; ursprüngliche P6c1-Ergebnisse bleiben
im historischen Nachweis auf Schema7 bezogen.

Tatsächliche Ergebnisse und Grenzen stehen im [P6c1-Nachweis](product-p6c1-protocol-storage.md).
Die oben genannten UI-/Last-/Retention-/Rechtsprüfungen sind hierdurch nicht
als bereits erfüllt zu verstehen; P6c1 hat keine UI und implementiert keine
allgemeine Teilnehmer-/Aktionslöschung oder Retentionjobs.

## P6c2 — ACTIVE UI

ProtocolModelTest prüft Draft-/Zeitvorbelegung, lokales Filtern und unveränderte
Reihenfolge, Quellen-/Betroffenen-/Kennungs-/Ansageinvarianten, vollständigen
Expectedstand, Korrektur/Ergänzung/Irrtum, STALE-Erhalt, Cancel, Lock und alte
Action-/Generationscallbacks. ActiveWorkspaceTest prüft zusätzliche Summary-
Freigabe, Fehler ohne Mischstand und Bereichscleanup.
P6c2ProtocolProbe verwendet echte ProductScreen-Komposition mit ausschließlich
synthetischen Domainports, lokaler Font Scale 1/2 und ohne Credential-/DB-Writes.
Konkrete Ergebnisse und Restgrenzen: [P6c2](product-p6c2-protocol-active-ui.md).

## P6c3 — COMPLETED Follow-up

ProtocolFollowUpModelTest und CompletedProtocolWorkspaceTest prüfen expliziten
Einstieg ohne P6-Prefetch, getrennte Statusbindung, alle Nachbereitungswrites,
Entry-/Revisionsphasen, STALE-Erhalt, ACTIVE-Draftcleanup, Lock/Reauth und späte
Callbacks. Reale ViewModel-Fakeports verweigern weiterhin P4/P5a/Action-Writes.
P6c3ProtocolProbe verwendet echte ProductScreen-Komposition mit synthetischen
Ports bei Font Scale 1/2; P6c2ProtocolProbe bleibt die separate ACTIVE-Regression.
Details und Restgrenzen: [P6c3](product-p6c3-protocol-completed-followup.md).

## P6c4 — Integration / Accessibilityautomation

P6c4ProtocolIntegrationTest schichtet echte ViewModel-/Fakeportmatrizen für
Cross-Area, Phasenwechsel, Lock/Reauth, STALE, neue Summarypublication, DST und
Unicodegrenzen über die vorhandenen Einzeltests. P6c4ProtocolIntegrationProbe
prüft echten ProductScreen mit Privacy-Sentinels, 60 Timelineitems, 24 Fassungen,
32 Personen/Kennungen und maximalen Texten bei kompakter Breite/Font Scale 1/2.
Zusätzlich prüft sie 599/601dp Hub-Inhaltsbreiten um die exakt unitgetestete
600dp-Schwelle und Scroll bis Save/Cancel mit per WindowInsets sichtbarer IME.
Späte Writergebnisse und fremde Editoren beim P6-Einstieg sind ViewModel-Matrizen.
Bestehende P6c2/P6c3-Proben und ProtocolStorageTest bilden die Regression.
Ergebnisse, Freeze, Testgrößen und ausdrücklich manuelle Restabnahme:
[P6c4](product-p6c4-protocol-integration-accessibility.md).


## P7 — einfacher Material-Slice

[P7](product-p7-material.md) bündelt Domain-/Projection-, Model-/Workspace-Tests,
echte SQLCipher-/Room-Instrumentation und eine synthetische ProductScreen-Probe.
Prüfumfang: Unicode 120/40/80, zwölf Vorlagen/Provenienz, Lifecycle, vollständiges
STALE, SAME-ACTION INSERT/UPDATE, Person-/Taskdetach, Actioncascade, Schema8/fresh
und 7→8 mit erhaltenem P6, Commit-/Publicationrevoke, Summaryminimierung,
COMPLETED ohne Prefetch/Writes, Bereichs-/Lockcleanup, Font Scale 1/2 und Demo.
Historische Migrationfixtures entfernen nur die neue synthetische Materialstruktur
samt parentgebundenen Detach-Triggern vor Downgrade und erwarten danach Schema9.
Abnahme grün: 803 Unit-Tests, 38 fokussierte Material-Unit-Tests, 91 echte Storagefälle
und ProductScreen-Proben P7/UX3/P6c2/P6c3 bei Font Scale 1/2 auf Galaxy/API33.
Builds, Lint, Architektur und Manifeste grün; Einzelheiten im P7-Nachweis.
Keine Echtdaten oder Credentialänderung; manuelle TalkBack-/IME-Abnahme bleibt offen.

## P8 — Kosten, EUR-Cents und administrative COMPLETED-Isolation

[P8](product-p8-costs.md): Domain-/Money-/Projectiontests, RAM-Modelmatrix und
realer ProductViewModel mit verzögerten Fakeports. SQLCipher/Room prüft Fresh9,
8→9/alte Migrationen, Paymentintegrität/Positionen/exakte Summe, vollständiges
STALE, atomaren Childersatz, Cascade, Revoke/Retry und unabhängige payerLabels.
Echter ProductScreen synthetisch bei lokaler Font Scale 1/2 auf Galaxy/API33.
COMPLETED nur P8 schreibbar; Tasks/Operations/Material/Aktion bleiben gesperrt.
Keine Credentials, Echtdaten, pm clear oder Uninstall. Abnahme grün: 839 Unit-Tests,
36 fokussierte P8-Unit-Tests, 110 reale Storagefälle und ProductScreen-Proben
P8/P7/UX3/P6c2/P6c3 bei Font Scale1/2 auf Galaxy/API33. Builds/Lint/Architektur/
Manifeste grün, einschließlich Schema9-Freezevergleich. Ergebnisse und manuelle
Restabnahme im P8-Nachweis.
