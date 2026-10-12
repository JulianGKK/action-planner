# UX3a — ACTIVE Lagebild-Hub

Baseline: sauberer `main`, `8ddbc6c` (manuell getestetes UX2).

ACTIVE beantwortet „Was ist jetzt?“. Unter dem unveränderten Security-Header
stehen der Rückweg zur Aktionsliste, Titel, AKTIV und Lagebild. Der Hub führt
über vier große Bereichskarten zu Personen, Aufgaben, Funktionen und separaten
read-only Aktionsdetails. Keine zukünftigen Module oder toten Controls.

## Summaries und Hinweise

- Personen: Gesamtzahl und nichtleere Zähler für vor Ort, Gewahrsam,
  nicht erreichbar und ohne Lage. Fehlender Snapshot zählt ebenfalls als ohne
  Lage; er behauptet keine Anwesenheit. Höchstens ein Hinweis: Gewahrsam vor
  nicht erreichbar vor fehlender Lage. Das ist Anzeigepriorität, keine Fachregel
  oder Eskalationsengine.
- Aufgaben: OPEN, IN_PROGRESS und HIGH ausschließlich unter aktiven Aufgaben.
  Höchstens ein HIGH-Hinweis. Bestehende P5a-Sortierung und Aktionen bleiben.
- Funktionen: ausschließlich ON_SITE mit currentRole. Bis zu drei belegte
  Funktionen im Hub, danach Anzahl weiterer Funktionen; vollständige Ansicht
  im Bereich. Keine Planrollenzähler, Sollbesetzung oder erfundene Warnung.
- Aktionsdetails: Beginn, voraussichtliches Ende und Status; eigener lesbarer
  Detailbereich ohne Editiercontrol.

Leere Daten erhalten verständliche Zustände statt Reihen mit Nullzahlen.
Keine P3b-Metadaten, Inventories, Payloads oder Grants für Hub-Summaries.

## Navigation und Darstellung

Jede Karte ist ein vollständiges Touchziel mit benannter Öffnungsaktion.
Bestehende 4-dp-Controls, mindestens 48-dp-Ziele, semantische Überschriften und
Text statt alleiniger Farbkennzeichnung bleiben. Zwei Spalten ab 600 dp verfügbarer
Inhaltsbreite und Font Scale höchstens 1,3; sonst eine Spalte. Texte dürfen umbrechen.
Keine neue LiveRegion. Rückwege zum Hub und bei fokussierter Arbeit zum Bereich
stehen oben. Aufgaben aus einer Personenkarte wechseln in den Aufgabenbereich und nutzen
dessen bereits vollständig vorhandene Boards für das synchrone Formular/Filter.
Der anschließende Refresh ist keine Benutzerinteraktion.

PREPARATION → ACTIVE öffnet den Hub. Die normale Post-Unlock-Aktionsliste bleibt
bestehen; automatische Auswahl einer ACTIVE-Aktion nach Unlock ist hier nicht
implementiert. Die bisherigen Personen-/Lageformulare, Aufgaben und Funktionsanlage
werden wiederverwendet. Ihre Detailneugestaltung folgt in UX3b/c/d. PREPARATION
bleibt planungsorientiert mit den bisherigen vier Reitern.

## Loading und Cleanup

Hub: Operations- und Task-Board werden lokal gestaged und gemeinsam als ein
UI-Snapshot publiziert. Keine Behauptung einer gemeinsamen DB-Transaktion.
Initial LOADING → vollständiger Hub. Bei Refresh bleibt der gültige alte Snapshot
bis zum vollständigen Ersatz sichtbar; Fehler erhalten ihn mit Fehlerhinweis.
Personen benötigen beide Boards für vorhandene betroffene Aufgaben;
Aufgaben beide für Labels; Funktionen nur Operations; Aktionsdetails nur die
bereits geschützte Aktion. Bereichswechsel startet einen eigenen Load.

Sessiongeneration, Aktion, Status, Bereich und Request begrenzen Publication.
Interaktion und synchroner Cleanup werden vor neuem Navigationstate erneut geprüft.
Lock/Generationwechsel löschen Hub, Bereich und Formulare.
Nur RAM, keine SavedState-/DB-/Preferences-Navigation. Security- und P4/P5a-
Fachcode bleiben unverändert.

## Prüfungen und manueller Smoke

`ActiveWorkspaceTest` prüft gemeinsame Publication, Refresh/Fehler, Bereichsrückwege,
Stale-Requests/Generation/Cleanup, aktuelle Funktionszählung, Aufmerksamkeit,
Nullzustände, Task-Navigation und responsive Spalten. `Ux1bShellProbe` ergänzt
synthetische echte Compose-Controls für alle vier Bereiche und Rückwege bei
Font Scale 1/2, ohne Produktdaten oder Credentials zu verändern.

Manuell auf Galaxy: Vorbereitung starten → Hub; alle vier Bereiche hin/zurück;
Personstatus und Taskstatus ändern und Hub-Summary prüfen; große Schrift;
Lock während Hub, Formular und Load. UX3b/c/d bleiben separate Slices.

Automatischer Nachweis: 564 Unit-Tests ohne Fehler (App 185, Data 375,
Security 4), davon 17 neue UX3a-Fälle und alle 30 UX2-Fälle. UX1-S,
P4/P5a, P3b-Metadata/Models und ProductTime/DST bleiben grün. App-Build,
App-/Data-AndroidTest-Builds, Lint, Architektur und Merged-Manifest-Prüfung
sind erfolgreich; Lint enthält nur die bestehende AGP-Versionswarnung.
Keine Dependency-/Permission-/Componentänderung, kein Netzwerk.
Galaxy SM-M526BR/API33: erweiterte UX1b/UX2/UX3a-Probe bei Font Scale 1/2
und vorhandene UX1a-Locked-Probe grün. Nur synthetische UI, kein Credential-
Handtest, keine Datenlöschung. Prüflogs lokal unter `.prototype-cache/ux3a-*`.
`git diff --check` ist grün; kein Commit oder Push.
