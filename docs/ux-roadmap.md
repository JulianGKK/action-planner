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
„Neu bestätigen“ wird als UX1-S separiert und blockiert die übrige UX1-Arbeit
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

### UX1-S — Frische explizite Sitzungsbestätigung

**Ziel:** Den Zielvertrag aus UX-Philosophie Abschnitt 3 technisch ermöglichen.
Aktuell kann authenticate nur aus LOCKED starten. Diesen Guard nicht einfach
entfernen oder vor dem Prompt einen neuen Drei-Minuten-Timer setzen.

Vor Implementierung Review/Entscheidung über getrennte Versuchslifetime und
weiterlaufende alte Session, Android-Systemdialog/Foreground, authentifizierten
Zeitanker, Key-/Maintenance-Lifetime und atomaren Generationswechsel. Keine
Foreground-/Device-Lock-/Idle-Ausnahme zur Erhaltung alter Daten erfinden.
Screen-Erhalt ist gegenüber Cleanup nachrangig. Fehler/Abbruch darf alten Ablauf
nicht verändern; abgelaufene Session bleibt gesperrt, frischer zulässiger Erfolg
eröffnet neue Session. Kein Leadership-Geheimnis oder alter Request überlebt.

**Abnahmefälle:** Erfolg vor/nach altem Ablauf, Abbruch, Fehler, Idle-Ablauf
während Prompt, echter Foregroundverlust/Device-Lock, Doppel-Tap, überholtes
Auth-Ergebnis, Maintenance verbraucht neue absolute Zeit, Keyowner entzogen,
keine alte Ergebnispublication und kein Editor-Reopen. Gezielter Geräte-/Security-
Nachweis nötig. Bei nicht nachweisbarer sicherer Umsetzung bleibt UX1 ohne Button;
Countdown und normale explizite Entsperrung bleiben nutzbar.

## UX2 — Preparation Workspace

**Ziel:** Planungsbereich mit Header, großen Reitern Übersicht/Personen/Aufgaben/
Rollen, knapper Vorbereitungssummary und sichtbarem nächsten Lifecycle-Schritt.
DRAFT bleibt reduzierter Entwurf, ohne unzulässige P4/P5a-Controls.

Kleine Schritte: Workspace/Navigation → Bereichsübersichten → Formularfokus und
mobile Action-/Task-Zeitauswahl → frühe Leitungsschutz-Anzeige.
Für Leitungsschutz einen aktionsweiten Setup-Metadatenpfad separat prüfen:
heutiges Inventory ist personengebunden. Keine sensitive Payload zur Übersicht
laden, keine neue Freigabe oder Recovery. Zustand unavailable nicht als
„Noch nicht eingerichtet“ darstellen.

**Nicht enthalten:** neue Bereitschaftspflichten, Planungsstatus, Änderungen an
Lifecycle, P3b-Crypto, dueAt-Datentyp, Retention oder persistente Navigation/Drafts.

**Abnahme:** Die Vorbereitungssummary führt direkt in Details, keine vollständige
Datenduplikation. Große nächste Aktion mit bestehender Konsequenzbestätigung;
Person/Task/Rolle anlegen fokussiert primäres Feld. Cancel ohne Mutation,
Navigation/Lock verwirft Entwürfe. Time-Regression mit normaler Zeit, ungültigem
Kalenderdatum, Berlin-Gap/Overlap und unverändertem bestehenden Action-Instant.
Role-IN_USE, exklusive Task-Zuständigkeit, unabhängige Betroffenheit bleiben korrekt.

## UX3 — ACTIVE-Lagebild und Einstieg

**Ziel:** Operativer Workspace Übersicht/Personen/Aufgaben/Funktionen;
Aufmerksamkeit statt Vollständigkeit. Personenfilter, kurze Karten, große
Lage-/Task-/Funktionsaktionen, aktuelle Vor-Ort-Funktionsverteilung.

Zuerst reine Projektionen bestehender P4/P5a-Daten und Workspace-Struktur,
danach Direkt-Routing nach erfolgreichem Unlock/Maintenance/Load. Exakt eine
ACTIVE-Aktion → ihr Lagebild; keine → Liste; mehrere → klare Auswahl.
Kein Routing anhand Default-Leere, alter Sessiondaten oder ungeprüfter Cachewerte.

**Nicht enthalten:** P5b-Vorschläge, automatische Tasks, Personen-/Ortsgeschichte,
neue Risikoklassen, Sollbesetzung, Identitäts-/Kontaktfelder in normaler Übersicht.

**Abnahme:** Gewahrsam/Nicht erreichbar und HIGH sofort sichtbar; Weitere macht
alle P4-Statuswerte erreichbar. Keine Lageangabe als unbekannt behandeln.
ON_SITE bestimmt Funktionsverteilung, Planung bleibt ab ACTIVE unverändert.
P5a-Sortierung und Chronik unverändert, ACTIVE kein Task-Löschen.
Synthetische Szenarien für Lage ändern, betroffene Aufgabe erstellen, Funktion
wechseln und Task wieder öffnen mit wenigen Taps. Revoke/Generation/Action/Request-
Regressionen besonders bei Routing und Navigation; kein alter Editor nach Unlock.

## UX4 — Demo und Tutorial

**Ziel:** Offline 90-Sekunden-Tour und freie Erkundung mit dauerhaftem DEMO.
Separater synthetischer Datenraum, bevorzugt in Memory und ohne echte Product DB,
Sessionkeys oder echte Personen. Echte Sessionregeln in der Tour erklären.

**Nicht enthalten:** Testpersonen in echten Aktionen, Demo-Schalter zum Umgehen
realer Gates, Cloud/Lernprofil, Demo-Import in Produktdaten.

**Abnahme:** Demooperationen erreichen technisch keine echte Repository-/Key-
Quelle; Wechsel beendet Demo-State und reale Nutzung benötigt reguläre Freigabe.
Kalte Starts, Wechsel, Reset und Abbruch bleiben eindeutig DEMO bzw. LOCKED.
Tour mit TalkBack und großer Schrift verständlich; keine Telemetrie.

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
| Erneute Bestätigung | Frisches DEVICE_CREDENTIAL, keine Timerlockerung festgelegt; sichere technische Versuch-/Lifecycle-Semantik und Screen-Kontextübernahme noch nachzuweisen. | Vor UX1-S; Security-Review. |
| Vorbereitung „bereit?“ | Fakten und menschliche Entscheidung, keine neuen Startblocker. Ob künftig fachliche Pflichtchecklisten benötigt werden, ist separater Produktumfang. | Nur bei eigenem späteren Slice. |
| Task-Datum ohne Uhrzeit | UX0 erhält Zeitpunktsemantik und verlangt bei gesetzter Fälligkeit bestätigte Uhrzeit. Eine reine Tagesfälligkeit benötigt neues fachliches Modell. | Separat, nicht Voraussetzung für UX2. |
| Aufmerksamkeit | Gewahrsam/Nicht erreichbar/HIGH und weitere bestehende unklare Lagen sichtbar. Genaue Reihenfolge/Umfang der weiteren Gruppen mit Szenarien finalisieren. | Vor UX3. |
| Funktionslücken | Null aktuelle Vor-Ort-Zuordnungen darf angezeigt werden; keine Sollbesetzung. Sollbedarf wäre eigener fachlicher Beschluss. | Separat bei Bedarf. |
| Kleine Screens / große Schrift | Keine Mini-Tabs; konkrete responsive Anordnung im synthetischen UX-Smoke wählen. | UX2, wiederverwenden UX3. |
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
