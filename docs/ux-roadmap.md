# UX0 — Umbauplan

Stand: 12.10.2026; Codebaseline `3b573ea`, sauberer main vor UX0.
Verbindliches Zielbild: [UX-Philosophie](ux-philosophy.md).
UX0 ist ausschließlich Analyse/Dokumentation. Die folgenden Slices sind ein
Umbauplan, keine Umsetzung und keine Freigabe großer Architekturänderungen.
P3b, P4, P5a, Session-, Storage-, Privacy- und Zeitverträge bleiben maßgeblich.

## Vorgehen und Reihenfolge

UX1 → UX2 → UX3 → UX4 → UX5 bleibt sinnvoll: erst stabile Shell/Zustände,
dann ein Workspace-Muster, anschließend operative Priorisierung und Training.
Read-only-Korrektheit für bestehende P4/P5a-/Aktionsdaten in COMPLETED muss schon UX1–UX3 erhalten bleiben;
UX5 verbessert die Darstellung und repariert nicht erst spät deren Gates.
„Sitzung neu starten“ wird als UX1-S separiert und blockiert die übrige UX1-Arbeit
nicht. Wegen des realen Sessioncodes ist ein zusätzlicher Security-Review zwingend.

Jeder Slice bleibt klein: erst betroffene Code-/Datenpfade prüfen, dann
abgegrenzte Änderungen, passende Tests und Build, abschließend synthetischer
UX-Smoke. Keine neue Library oder große State-/Navigation-Architektur allein
für Komfort. Vor zustandsübergreifender Architekturänderung ausdrückliche Zustimmung.
Keine echten Personen, Telemetrie, Netzwerkzugriffe, Screenshots mit Echtdaten.

## UX1 — Security-aware Shell und Grundlagen

**Ziel:** Klarer sicherer Einstieg, stabile Loadzustände und gemeinsame Bedienung.

1. UX1a: Locked/Authenticating/Maintenance/Blocked verständlich gestalten;
   Primary/Secondary/Tertiary, FormField, StatusBanner und Accessibility-Grundlagen.
   ProductInputs-Fokus-/IME-/HoldToReveal-Verhalten weiterverwenden.
2. UX1b: Action-Loadvertrag und LOADING/CONTENT/EMPTY/ERROR explizit machen;
   danach Person-/Operations-/Taskbereiche mit ehrlichen eigenen Ladezuständen.
   Der heutige ActionRepository-Resultpfad muss gezielt auf Fehlerdarstellung
   geprüft werden; keine erfundenen Erfolge/Leere. Keine lange Cache-Lifetime.
3. UX1c: Nur lesbare Restzeitprojektion aus dem Coordinator; kompakter READY-
   Countdown, Warnung in letzten etwa 30 Sekunden, keine Interaktion durch Ticks.
   Deadline bleibt intern maßgeblich; API-Erweiterung separat reviewen.

**Nicht enthalten:** Re-Auth aus READY, Workspace-Umbau, DB-Migration, neue
Sessiondauer, neues Security-State-Modell oder dauerhafte Editorablage.

**Abnahme:** Vier Screenfragen beantwortet; nach Unlock kein falsches Empty;
Fehler, Leere und Load unterscheidbar; Countdown startet nicht erst ab READY.
Regressionsprüfungen für absolute Deadline trotz kontinuierlicher Aktivität,
Idle trotz Ticks/Bannern/Loads, Foreground/Device-Lock, keine Publication nach
Revoke, Fokus/Keyboard und Textskalierung/TalkBack. Build und passende Unitchecks;
Lifecycle-/IME-Verhalten auf Gerät mit synthetischen Daten prüfen.

### UX1a — implementierter Shell-Slice (12.10.2026)

Der angeforderte UX1a-Scope bündelt Shell-Grundlagen, initialen Action-Load und
read-only Countdown aus den obigen Planungspunkten. Umsetzung und Prüfnachweise:
[UX1a Shell](ux1a-shell.md). Bereichs-Loadzustände, weitere Banner und Legacy-
Buttons bleiben UX2/UX3; keine Re-Auth oder Vorwegnahme von UX1-S.

### UX1b — Polish auf UX1a (12.10.2026)

Manuelle Befunde konkretisieren die ruhige Shell: Atomic Screen Reveal für den
heutigen Workspace, initiales Laden getrennt von Refresh, stabiler READY-Header,
4-dp-Controls, floating Feedback mit Lebensdauerbalken, Rollen-/Taskfeedback,
KeyboardHints und einmaliger Eintrittsfokus. Keine Sessionverlängerung oder
Änderung von Storage/P3b. Details und Grenzen: [UX1b Polish](ux1b-polish.md).
UX2/UX3 behalten fachliche Navigation und Bereichsneugestaltung.

### UX1-S — Konservativer Session-Neustart

**Implementiert:** Bei gültigem READY und `0 < Restzeit <= 30000 ms` erscheint
„Sitzung neu starten“. Expliziter Tap führt sofort normalen Lock/Cleanup aus,
danach normalen DEVICE_CREDENTIAL-Unlock mit Maintenance. Alte Generation,
Editoren, Navigation und Leadership-Grants werden verworfen. Cancel bleibt LOCKED,
Fehler folgen dem bisherigen fail-closed Vertrag. Kein Continuation-Descriptor,
keine neue Auth-State-Machine und keine READY-Foreground-Ausnahme.

Die ursprüngliche weiterlaufende Re-Auth-Idee wurde vor Änderungen gestoppt
und durch diese Produktentscheidung ersetzt. Der heutige Post-Unlock-Einstieg
bleibt die Aktionsliste; ACTIVE-Hub-Routing gehört zu UX3.

**Abnahmefälle:** Sichtbarkeit und tatsächlicher Tap, sofortiger Revoke vor Auth,
Cancel/Fehler/Erfolg, Maintenance-Zeit, Doppeltap, Foreground-/Device-Lock-Revoke,
stale Storage-/UI-Publication, Countdown/Banner/Atomic Reveal/TaskModel und P3b.
Aktuelle Prüfungen und menschlicher Smoke: [UX1-S](ux1s-session-restart.md).

## UX2 — Preparation Workspace

**Implementiert und manuell getestet:** DRAFT bleibt Entwurf/Stammdaten mit „Vorbereitung beginnen“.
PREPARATION hat fachlichen Header und vier große Bereiche Übersicht/Personen/
Aufgaben/Rollen. Kompakte Summary, frühes actionState-Metadata ohne Person,
bewusste Einrichtung, Planrollenkarten, bestehende Tasks/Vorlagen, Rollen mit
geplanter Nutzung und primäres „Aktion starten“. Mobile inline Datum-/Zeitwahl,
gezielte Bereichsreads, Atomic Reveal und RAM-only Navigation/Cleanup.

P3b-META (`4ba8f5a`) liefert bereits den geschützten aktionsweiten Zustand;
UX2 erweitert weder diesen Port noch Security-/Storagecode. Keine Readiness-
Pflicht, neue Fachregel, Migration oder ACTIVE-Hub-Vorwegnahme. Umsetzung,
Prüfnachweise und menschlicher UX-Smoke: [UX2](ux2-preparation-workspace.md).

## UX3 — ACTIVE-Hub und fokussierte Bereiche

Verbindliche Entscheidung aus dem manuellen UX-Review (12.10.2026): Lagebild ist
operativer Hub / Schaltzentrale. **Hub → Bereichsübersicht → Objekt → fokussierte
Aktion** ersetzt die lange Gesamtseite. Details: [UX-Philosophie, Abschnitt 7](ux-philosophy.md#7-active-lagebild-als-operativer-hub--schaltzentrale).
UX3a, UX3b, UX3c und UX3d sind implementiert.
Der [UX3-Integrationsslice](ux3-active-integration.md) prüft Cross-Area-Verträge
und repariert gezielt Task-Fokus, affected Counts und Rücknavigation.

### UX3a — ACTIVE Hub / Navigation

**Implementiert:** [UX3a Lagebild-Hub](ux3a-active-hub.md). Vier Bereichskarten,
gemeinsame Summary-Publication, gezielte Bereichsreads und RAM-only Navigation.
PREPARATION → ACTIVE öffnet den Hub; Post-Unlock bleibt vorerst die Aktionsliste.
Die unten beschriebene automatische ACTIVE-Auswahl ist weiterer Routingumfang.

Große Bereichskarten für Personen, Aufgaben, Funktionen und eigene read-only
Aktionsdetails. Sehr kompakte Summary, höchstens ein datenbasierter Hinweis pro
Karte, klare Öffnungsaktion, optional sinnvolle Quick Action („+ Aufgabe“).
Responsive auf Phone: bei ausreichender Breite zwei Spalten, bei geringer Breite/
großer Schrift eine. Keine kleinen Top-Tabs, Mini-Dashboards oder toten Buttons.
Wichtige Rücknavigation im fachlichen Header / klaren Secondary-Control;
Security-Header `🔒 m:ss [Sperren]` bleibt getrennt.

Routing erst nach erfolgreichem Unlock/Maintenance/Load: genau eine ACTIVE-Aktion
→ ihr Hub; keine → Liste; mehrere → klare Auswahl. Kein Routing anhand Default-
Leere, alter Sessiondaten oder ungeprüfter Cachewerte. Zunächst bestehende
P4/P5a-Projektionen und kleine Navigationsschritte, keine Architektur auf Vorrat.

**Abnahme:** kompakte Orientierung ohne Objektlisten im Hub; Gewahrsam/Nicht
erreichbar, fehlende Lage und HIGH-Zahl erkennbar. Maximal ein Hinweis je Karte,
ohne Regel-/Eskalationsengine. Große Schrift, schmale Breite, TalkBack und klare
Rückwege prüfen. Revoke/Generation/Action/Request, Fokus/IME und Cleanup erhalten;
keine alten Editoren oder Grants nach Lock. Zusätzliche künftige Bereiche benötigen
keinen Navigationsumbau und erscheinen erst bei implementierter Funktion.

### UX3b — Personenübersicht + kompakte Personenkarten

**Implementiert:** [UX3b Personenarbeitsbereich](ux3b-active-people.md).
Summary, kompakte Karten und fokussierte Lage-/Funktionsaktionen. Die bestehende
Reihenfolge bleibt erhalten; Statusfilter sind nicht Teil dieses Slices. ACTIVE:
Pseudonym, aktuelle Lage, zulässiger Ort/Ziel, aktuelle Funktion und Aktualität.
Keine prominente Planrollen-/„Keine geplanten Rollen“-Zeile. PREPARATION bleibt
planungsorientiert; Planungsdetails in ACTIVE nachrangig nachsehbar.
Personenkarte als Tap-Ziel → fokussierter Arbeitsbereich → große Aktionen
„Lage ändern“, „Funktion ändern“, „Personendetails“. Kein redundanter Listenhinweis
„Lage aktualisieren“, keine überladene Karte.

**Abnahme:** currentRole statt geplanter Rolle primär, ohne Funktion knapp
„Keine Funktion“. P4-Status-/Funktions-/Ortsregeln erhalten; insbesondere keine
aktuelle Funktion bei Gewahrsam. Aktualität: unter 1 Minute „Gerade aktualisiert“,
1–59 Minuten „Aktualisiert vor X Min.“, ab 60 Minuten am selben Kalendertag
„Zuletzt aktualisiert um HH:mm“, älterer Kalendertag „Zuletzt aktualisiert am DD.MM.
um HH:mm“. Kalenderwechsel hat Vorrang. NO_STATUS-Initialisierung/updatedAt=0
suggeriert keine geprüfte Lage. Anzeige-Ticks sind keine Sessioninteraction.
Synthetisch Lagewechsel, Funktionswechsel und separat geschützte Personendetails
prüfen; keine neue Personen-/Ortsgeschichte.

### UX3c — Aufgabenübersicht

**Implementiert:** [UX3c Aufgabenarbeitsbereich](ux3c-active-tasks.md).
Kompakte Karten, sichtbarer Personenfilter, Closed-Disclosure und fokussierter
Task mit bestehenden P5a-Aktionen. Detailfokus bleibt nach Save/Transition erhalten.

Hub → Aufgabenübersicht → Task. Summary oben, danach bestehende P5a-Taskkarten
und Sortierung; HIGH unter OPEN/IN_PROGRESS, OPEN und IN_PROGRESS getrennt zählen.
DONE/CANCELLED separat/eingeklappt, klare Erstellung und Taskaktionen.

**Abnahme:** P5a-Prioritäts-/Fälligkeitsreihenfolge mit stabilen Tie-Breakern,
exklusive Zuständigkeit, unabhängige Betroffenheit, Chronik, Templates und stale-
state-Guards erhalten. ACTIVE darf erstellen/bearbeiten/Lifecycle, nicht löschen;
COMPLETED bleibt read-only. Synthetisch betroffene Aufgabe erstellen, abbrechen,
wieder öffnen und Lock während Navigation prüfen. Keine lange Gesamtseite.

### UX3d — Funktionenübersicht

**Implementiert:** [UX3d Funktionenarbeitsbereich](ux3d-active-functions.md).
Aktuelle Gruppen, Ohne Funktion, nachrangige weitere Funktionen und bestehende
P4-Anlage; Gruppen → vorhandener UX3b-Personenbereich mit RAM-only Rückkontext.
Keine Sollbesetzung, Bewertung oder neue Fach-/Securitylogik.

Eigener Bereich „Wer übernimmt gerade was?“ mit aktuellen Funktionen und kompakten
Personen pro Funktion; Planung nachrangig. Summary zählt ausschließlich ON_SITE
mit currentRole, NO_STATUS behauptet keine verfügbare Person.

**Abnahme:** Funktionswechsel und Statuswechsel korrekt projiziert; null Vor-Ort-
Zuordnungen ehrlich zeigen, ohne erfundene Sollzahlen/Pflichtrollen oder automatische
Unterbesetzungsbewertung. Aktionsdetails bleiben ein eigener Bereich.

**Für UX3a–d nicht enthalten:** Änderungen an P4/P5a-Fachregeln, P5b-Vorschläge,
automatische Tasks, Risikoklassen, Sollbesetzung, sensitive Identitäts-/Kontaktfelder
in Übersichten, Protokoll-/Vorfälle-/Dokument-/Uploadimplementierung, Netzwerkpfade,
Sessionverlängerung oder Persistenz von Navigation/Entwürfen. Diese späteren Module
benötigen ihren eigenen Product-/Security-/Privacy-Slice.

## UX4 — Eigenständige PaG Demo

**Implementiert:** [UX4 Demo](ux4-demo.md). Eigener debug-artiger Build Type
`demo`, Application-ID `org.pag.prototype.a.demo`, direkter synthetischer Home-
Einstieg. Vorbereitung, ACTIVE-Quickstart und optionaler Vier-Schritte-Rundgang;
permanentes DEMO, Reset/Ende und ausschließlich RAM. Kein ProductRuntime,
Produktdatenzugriff oder Nachbau echter Session-/P3b-Sicherheitsgrenzen.

Reine Domainregeln sowie stateless Controls/Picker/Hub werden wiederverwendet.
Normale debug/release Build-/Securitypfade bleiben erhalten. Keine Demoimports,
Exporte, Kamera, Cloud, Telemetrie oder falschen Sicherheitsversprechen.
Automatische Abnahme und manueller Smoke stehen im Slice-Dokument.

## UX5 — COMPLETED / Rückblick

**Ziel:** Read-only-Orientierung, letzter Snapshot und vorhandene Task-Chronik
verständlich darstellen, Abschlusszeit/Phase deutlich markieren.

**Nicht enthalten:** Wiederöffnung der Aktion, allgemeine Historie, Exporte,
Archivierungsversprechen, neue Retention, Evidence-/Forensikfunktionen.

**Abnahme:** Für bestehende P4/P5a-/Aktionsdaten keine Schreibcontrols oder
zugelassenen Schreibpfade. Die eigenständige zukünftige P6-Nachbereitung ist eine
eng begrenzte fachliche Ausnahme und wird separat implementiert, nicht durch UX5;
weiterhin
separater P3b-Step-up für vorhandene sensible Bereiche. Keine historische Lage
aus heutigem Snapshot erfinden. TaskEvents bleiben ausschließlich bestehende
Lifecycle-Ereignisse; vergangene Titel-/Personenänderungen werden nicht behauptet.

## P6a.1 — Aktionsprotokoll: Produkt-/Privacyentscheidungen

[Der P6-Vertrag](product-p6a-protocol.md) ist fachlich festgelegt, noch nicht
implementiert. Die separate Implementierungsspezifikation ist als
[P6b](product-p6b-protocol-implementation.md) dokumentiert; keine heutige
Code-/Schemafreigabe. Eigenständiger Bereich mit bewusst erstellten
Schlüsselereignissen/relevanten Einträgen, Vorlagen, vollständigen Revisionen,
Quellenart und optionalen Betroffenen-/Behördenangaben. Keine allgemeine Notiz-
oder Audit-Historie, automatische Rechtsbewertung oder P4-/Taskmutation.

**SENSITIVE, normale Arbeit in gültigem READY ohne Leadership-Step-up:** enge
Zugriffsausnahme für schnelle Dokumentation, keine Klassifizierungsherabstufung
oder automatische P3b-Übernahme. PRIV-03 erlaubt ausschließlich eintragsbezogene
Revisionen und Betroffene derselben Aktion mit Pseudonymanzeige.

ACTIVE erfasst/korrigiert/ergänzt/markiert Irrtümer. COMPLETED hat ausschließlich
für P6 bewusste Nachbereitung einschließlich neuer Einträge mit dauerhaftem
„Nachtrag nach Abschluss“. Ereigniszeit und tatsächliche recordedAt je Fassung
klar unterscheiden. Keine stillen Updates/normalen Einzel-Löschcontrols,
P4/P5a bleiben read-only; kein Reopen der Aktion und kein UX5-Redesign.

Flow: Lagebild → Protokoll → Vorlage/freier Eintrag → Detail → Chronologie.
Hubkarte erst nach implementierter Funktion, nur kompakte Anzahl-/Zeitangaben,
keine Namen/Kennungen/Freitextvorschau. Timeline nach occurredAt mit Textmarkern
für Schlüsselereignis, ungefähre Zeit, Revision/Irrtum und Nachtrag;
Filter Alle/Schlüsselereignisse. Detail zeigt vollständigen Inhalt, betroffene
Pseudonyme, Quellenart, Kennungen, Zeiten und Revisionsverlauf. Kennungen nur dort.

Atomic Reveal/Refresh, RAM-only Navigation/Entwürfe, Lock-Cleanup und Font Scale 2
bleiben verbindlich. Eigene endliche P6-Retentionbetrachtung innerhalb der Aktion;
konkrete Dauer und juristische Prüfung bleiben spätere fachkundige Entscheidungen.
Personenlöschung löst erforderliche Relationen, erhält Texte/Fassungen und verlangt
spätere manuelle Protokollprüfung. Vollständige Aktionslöschung ohne P6-Restbestand.
Keine Medien/Exporte/Permissions/Worker oder neue Securityarchitektur;
keine Beweis-/Forensik-/Zeit-/Verwertbarkeitsgarantien.

## Später — Vorfälle / Beobachtungen / Evidenz

Eigener Product+Security+Privacy-Slice, kein Teil UX1–UX5. Zuerst Zweck und
Datensparsamkeit, Zeitstempel, Editierbarkeit/Integrität, Anhänge/Fotos,
Metadaten, Zugriffsgrenzen und Retention entscheiden. Keine automatische
Rechtsbewertung und keine unbewiesenen Evidence-/Forensikgarantien.

## Echte offene Entscheidungen und Reviewpunkte

| Punkt | Festgelegt / noch zu entscheiden | Zeitpunkt |
| --- | --- | --- |
| Session-Neustart | Entschieden: sofortiger normaler Lock, danach frischer normaler DEVICE_CREDENTIAL-Unlock. Keine Navigation-/Editorübernahme und keine READY-Ausnahme. | UX1-S; Security-Review und manueller Systemdialog-Smoke. |
| Vorbereitung „bereit?“ | Fakten und menschliche Entscheidung, keine neuen Startblocker. Ob künftig fachliche Pflichtchecklisten benötigt werden, ist separater Produktumfang. | Nur bei eigenem späteren Slice. |
| Task-Datum ohne Uhrzeit | UX0 erhält Zeitpunktsemantik und verlangt bei gesetzter Fälligkeit bestätigte Uhrzeit. Eine reine Tagesfälligkeit benötigt neues fachliches Modell. | Separat, nicht Voraussetzung für UX2. |
| Aufmerksamkeit | Festgelegt: kompakte Summary und höchstens ein datenbasierter Hinweis je Hub-Karte, ohne Regel-/Eskalationsengine. Konkrete Microcopy mit synthetischen Szenarien prüfen. | UX3a; keine offene Produktentscheidung für das Hub-Modell. |
| Funktionslücken | Null aktuelle Vor-Ort-Zuordnungen darf angezeigt werden; keine Sollbesetzung. Sollbedarf wäre eigener fachlicher Beschluss. | Separat bei Bedarf. |
| Kleine Screens / große Schrift | ACTIVE: große Hub-Karten, zwei Spalten nur bei ausreichender Breite, sonst eine; keine starre Spaltenzahl oder Mini-Tabs. PREPARATION-Auswahl zugänglich halten. | UX2 / UX3a; synthetischer UX-Smoke. |
| Demo-Lifetime | In-Memory bevorzugt; Reset und Tourablauf konkretisieren. Persistenz wäre zusätzlich zu begründen und strikt getrennt zu prüfen. | Vor UX4. |
| Visuelle Marke | Farben, Logo, Schriftfamilie, exakte Radien und dekorative Animationen offen; Hierarchie und Accessibility bereits verbindlich. | Späterer visueller Slice. |

Diese Punkte verhindern die vollständige UX0-Spezifikation nicht. Sie sind
gezielte spätere Entscheidungen; UX0 gibt keine technische Securityänderung frei.

## UX0-Prüfumfang

Gelesen: PRODUCT, Security, Privacy, Datenmodell, P4/P5a sowie tatsächlicher
Compose-/ViewModel-Code, SessionPort, relevante Coordinator-/Lifecycle-Pfade
und Leadership-Port. Konkrete Friktionen und Wiederverwendung stehen in
UX-Philosophie Abschnitt 14. Kein Refactor, keine .kt-Änderung, keine neue
Dependency, Permission, Persistenz oder Security-Vertragsänderung.
Für reine Dokumentation keine Unit-/Gerätetests oder Builds nötig;
Abnahme über Inhaltsreview, lokale Link-/Scope-Prüfung und `git diff --check`.


## P6b — technischer Plan, noch keine Protokoll-UI

[Implementierungsspezifikation](product-p6b-protocol-implementation.md) konkretisiert
P6a.1 mit fünf additiven geplanten Tabellen, atomaren Fassungen, Same-Action-/
Stale-Gates und minimierten Summary-/Timeline-/Detailprojektionen. Keine Code-
oder Schemaänderung und keine erneute Produktentscheidung.

Nächste getrennte Aufträge: P6c1 Domain+Storage+Repository/Policytests gemeinsam,
P6c2 ACTIVE-UI, P6c3 COMPLETED-Nachbereitung, P6c4 Integration/Accessibility.
Hubkarte erst mit umgesetzter Funktion; keine tote Karte jetzt. ACTIVE/COMPLETED-
Phasenwechsel im Entwurf fordert bewussten neuen Nachbereitungskontext, alte
Fassungen bleiben ehrlich lesbar. Atomic Reveal/Refresh, RAM-only Navigation/
Editoren, Lock-/Stale-Cleanup, Font Scale 2 und bestehende Securityshell erhalten.
[P6-Prüfplan](testing.md) ist Sollabnahme, keine bereits ausgeführte Testkampagne.
Retentiondauer/Rechtsprüfung/Allgemeinlöschworkflow bleiben P6a.1-Restgrenzen.


## P6c1 — Domain / Storage / Repository implementiert

[DB-only-Nachweis](product-p6c1-protocol-storage.md): Protocol-Domainport,
SENSITIVE/READY-Adapter, fünf Tabellen/DAO, additive Migration6→7 und
ProductRuntime.protocol sind umgesetzt. Noch keine ProductViewModel-/Compose-
Integration, Hubkarte, Timeline, Formulare oder COMPLETED-UX.

Nächster eigenständiger Slice P6c2: ACTIVE-UI auf Summary-/Timeline-/Detailprojektionen,
RAM-only Entwürfe und explicit Save/Cancel, vorhandene Session-/Atomic-Reveal-
Verträge. COMPLETED-Nachbereitung fachlich und repositoryseitig bereits erlaubt,
aber deren Bedienung folgt separat. Keine P4/P5a-Mutation oder neue Vorschlagslogik.

## P6c2 — ACTIVE Protokoll

[ACTIVE-UI](product-p6c2-protocol-active-ui.md): fünfte Hubkarte mit ausschließlich
ProtocolSummary, Chronologie/Filter, Schnellvorlagen, RAM-Entwürfe, Details und
bewusste Revisionen/Irrtumsmarkierung. Gemeinsame Hubpublication, Lock-/Stale-
Cleanup und vorhandene Produktinputs; Domain/Data/Schema7/Security unverändert.
Keine P6-Demoerweiterung. COMPLETED-Nachträge folgen separat in P6c3,
Cross-Area-/Last-/Accessibility-Abnahme bleibt im abgegrenzten P6c4-Umfang.

## P6c3 — bewusste COMPLETED-Nachbereitung

[Follow-up](product-p6c3-protocol-completed-followup.md): ausdrücklich geöffneter
P6-Sonderbereich, getrennte ACTIVE-/COMPLETED-Modi, dauerhafte Entry- und
fassungsbezogene Phasenmarker, gemeinsame Formulare und Revisionen. Kein Prefetch
auf der normalen abgeschlossenen Aktionsseite; Lock/Reauth öffnet nichts automatisch.
P4/P5a/Aktionsdaten bleiben read-only; Schema7, Domain/Data/Security und Demo bleiben
unverändert. P6c4 übernimmt die weitere Integration-/Accessibilityabnahme.

## P6c4 — Integration / Accessibility / Abschlussreview

[P6c4](product-p6c4-protocol-integration-accessibility.md) prüft ACTIVE/COMPLETED
als zusammenhängenden Protokollbereich: Phasen-/Bereichs-/Lockcleanup, STALE,
Privacyprojektionen, lange Inhalte und Accessibilitysemantik. Ein kleiner UI-Fix
macht Save/Cancel am Ende langer optionaler Formulare erreichbar. Keine neue
Fachfunktion oder Änderung von Domain/Data/Schema7/Security/Demo.
Menschliche TalkBackabnahme, konkrete Retention und Löschworkflows sowie
juristische Prüfung vor Echtdaten bleiben ausdrücklich getrennte Restpunkte.
Die dokumentierte automatisierte Abnahme ist grün; P6a/P6b/P6c1–P6c4 sind
technisch bereit für Abschlussreview. Dies ist keine Freigabe der offenen Restpunkte.


## P7 — einfache Materialverwaltung in einem Slice

[P7](product-p7-material.md) ergänzt den bestehenden PREPARATION-Bereich und eine
sechste ACTIVE-Hubkarte ausschließlich mit Count. Der bewusst geöffnete Materialbereich
nutzt einfache Liste/Form/Vorschläge; COMPLETED erhält „Material ansehen“ read-only
und keinen zweiten Hub oder Boardprefetch. Gleiche bestehende Workspace-/Input-/
Sessionmuster, kein allgemeines UX-Redesign. Keine Materialstatusmaschine, Kategorien,
Suche/Filter, Historie, automatische Aufgaben oder Demo-Materialdaten. P7 wird nicht
in weitere a/b/c-Slices aufgeteilt; manuelle Accessibility/Retention bleiben getrennt.
Automatisierte P7-Abnahme grün und bereit für Review: vollständige Unit-/Storage-
Regression sowie ProductScreen bei Font Scale 1/2 auf Galaxy/API33 bestanden.

## P8 — einfache Kosten-Sammelliste, ein Slice

[P8](product-p8-costs.md) bündelt Produktvertrag, Domain, Storage, Repository, UI
und Abnahme. PREPARATION-Kostenbereich, siebte ACTIVE-Hubkarte ausschließlich mit
Count/EUR-Gesamtsumme, vorhandener responsive Vertrag und stabile Reihenfolge.
COMPLETED bietet bewusst „Kosten verwalten“, administrative P8-Ausnahme ohne
Reopen/Öffnung anderer Bereiche. Liste/Form ohne eigene Detailnavigation,
1–32 Zahlungen und P3a-Pseudonyme nur als Textkopierhilfe. Keine Kategorien,
Belege/Dateien, Datum, Buchhaltungshistorie, Erstattung oder Demo-Kosten.
Automatisierte Abnahme grün: vollständige Unit-/Storage-Regression, ProductScreen
Font Scale1/2 auf Galaxy/API33, Builds/Lint/Architektur/Manifeste bestanden.
P8 bereit für Review; manuelle Accessibility/IME und Retention bleiben eigenständig.

## P9a — Gedächtnisprotokolle: Produkt-/Fragebogenvertrag, keine UI

[P9a](product-p9a-memory-protocol.md) spezifiziert den eigenständigen persönlichen
Rückblick neben P6 und Note. Freie Erinnerung vor strukturierten Abschnitten;
Questionnaire Version 1 mit stabilen Keys/acht Antworttypen, Unsicherheit und
überspringbaren Fragen. Keine suggestive Vorauswahl, Diagnoseableitung,
Rechtsbewertung oder Zusage gerichtlicher Verwertbarkeit.

Künftiger ACTIVE-Einstieg „Gedächtnisprotokolle“ zeigt nur Anzahl Entwürfe und
abgeschlossener Berichte; keine authorLabels, Antworten oder Textvorschau.
COMPLETED bewusster Einstieg, normale Seite ohne P9-Prefetch einschließlich
Summary. Nur ACTIVE/COMPLETED, kein PREPARATION-Pfad. Keine tote Karte jetzt.
Workspace → Entwurf fortsetzen / Neues Gedächtnisprotokoll / Abgeschlossene
Berichte. Bericht → Freie Erinnerung → Abschnitte → Zusammenfassung → Abschließen;
spätere Ergänzung append-only. Kein vollständiges UI-Design durch P9a.

Ausdrückliche enge Fortentwicklung der UX0-RAM-only-Regel ausschließlich für
P9-Fachentwürfe: verschlüsselte persistente Abschnitte über „Speichern und weiter“.
Gespeicherte Abschnitte überstehen Lock; ungespeicherte aktuelle Eingabe darf
verloren gehen. Kein Background-Autosave, gespeicherter Navigations-/Authstate,
Sessionverlängerung oder automatischer Reopen nach Reauth. Bewusstes Fortsetzen
eines gespeicherten ACTIVE-Entwurfs nach COMPLETED, kein stiller Editorübertrag.
Andere kurze Formulare bleiben RAM-only; P4/P5a/P7/Aktionsdaten read-only nach Abschluss.

Nächster gesonderter Auftrag P9b muss sicheren persistierten Draftvertrag,
Versionendarstellung, Grenzen, Konflikte und bestehende Gates spezifizieren.
P9a gibt keinen Code, Schema, Migration oder Securityumbau frei. Menschliche
TalkBack-/Accessibility-Abnahme, juristische/fachkundige Fragebogenprüfung,
Retentiondauer und allgemeiner Löschworkflow bleiben offen. Abnahme P9a nur
Dokumentations-/Verweis-/Scope-/Freezeprüfung und git diff --check.
