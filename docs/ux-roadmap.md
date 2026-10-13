# UX0 — Umbauplan

Stand: 12.10.2026; Codebaseline `3b573ea`, sauberer main vor UX0.
Verbindliches Zielbild: [UX-Philosophie](ux-philosophy.md).
UX0 ist ausschließlich Analyse/Dokumentation. Die folgenden Slices sind ein
Umbauplan, keine Umsetzung und keine Freigabe großer Architekturänderungen.
P3b, P4, P5a, Session-, Storage-, Privacy- und Zeitverträge bleiben maßgeblich.

## Vorgehen und Reihenfolge

UX1 → UX2 → UX3 → UX4 → UX5 bleibt sinnvoll: erst stabile Shell/Zustände,
dann ein Workspace-Muster, anschließend operative Priorisierung und Training.
Read-only-Korrektheit für COMPLETED muss schon UX1–UX3 erhalten bleiben;
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
UX3a, UX3b und UX3c sind implementiert; UX3d bleibt Zielplanung.

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

**Abnahme:** Keine Schreibcontrols oder zugelassenen Schreibpfade; weiterhin
separater P3b-Step-up für vorhandene sensible Bereiche. Keine historische Lage
aus heutigem Snapshot erfinden. TaskEvents bleiben ausschließlich bestehende
Lifecycle-Ereignisse; vergangene Titel-/Personenänderungen werden nicht behauptet.

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
