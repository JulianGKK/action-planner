# P5a – Aufgaben als Teil des Lagebilds

## Modell und fachliche Trennung

`Task` gehört genau zu einer Aktion: ID, actionId, Titel, Priorität, Status,
nullable assigneePersonId/assigneeRoleId, nullable affectedPersonId, nullable dueAt,
nullable sourceTemplateKey, createdAt, updatedAt und nullable completedAt.
Titel werden getrimmt, müssen danach sichtbar nicht leer sein und dürfen maximal
120 Unicode-Codepoints enthalten. Keine zusätzlichen Freitextnotizen.

Zuständigkeit bedeutet **niemand, Person oder Rolle**. Repository und SQLite-Insert-/Update-Trigger verweigern
beide Zuständigkeiten gleichzeitig. Zusammengesetzte DB-Fremdschlüssel verhindern
Referenzen auf Personen oder Rollen anderer Aktionen auch bei direkten DB-Schreibvorgängen.
`affectedPersonId` ist davon unabhängig: „EA kontaktieren“, betrifft Motte,
zuständig Gesa-Support. Die UI zeigt ausschließlich Pseudonyme aus P3a, keine
sensiblen Identitäts-/Kontaktfelder aus P3b. Rollenlöschung verweigert referenzierte
Aufgaben mit IN_USE; keine stille Änderung von Tasks.

Prioritäten: LOW/Niedrig, NORMAL/Normal (Default), HIGH/Hoch. Fälligkeit ist optional;
lokale Eingabe/Anzeige, keine Notifications, Alarme, Worker oder Eskalation.
Task-Fälligkeiten verwenden denselben lokalen Zeitvertrag wie Action-Zeiten:
Nicht existente und mehrdeutige Uhrzeiten bei Zeitumstellungen werden abgewiesen,
ohne Verschiebung oder implizite Offset-Auswahl. Die Eingabe bleibt korrigierbar.

## Lebenszyklus und minimale Chronik

Status: OPEN/Offen, IN_PROGRESS/In Arbeit, DONE/Erledigt, CANCELLED/Entfällt.
OPEN → IN_PROGRESS; OPEN/IN_PROGRESS → DONE oder CANCELLED;
DONE/CANCELLED → OPEN. Andere Übergänge einschließlich Wiederholung werden verweigert.
DONE setzt completedAt; alle anderen Status haben null. „Wieder öffnen“ setzt OPEN,
completedAt null und updatedAt auf jetzt. Erledigung ist reversibel.

`TaskEvent` enthält ausschließlich ID, actionId, taskId, type und occurredAt.
Creation erzeugt CREATED; Start STARTED; Erledigung COMPLETED; Entfallen CANCELLED;
Wiederöffnen REOPENED. Task und Event werden in einer kurzen Schreibtransaktion
atomar gespeichert. Erwarteter vollständiger Snapshot verhindert stale Updates
und doppelte Lifecycle-Events nach Retry. Ereignisse werden im Repository nur
angehängt; kein Bearbeitungs-/Löschpfad für einzelne Events. PREPARATION-Tasklöschung
entfernt ausdrücklich die zugehörigen Events per Cascade.

**Produktgrenze §87a/#47:** `TaskRules.MAX_TASK_EVENTS_PER_ACTION = 5_000` zählt
alle gespeicherten CREATED, STARTED, COMPLETED, CANCELLED und REOPENED derselben
Aktion, über alle Tasks. Jede neue Eventquelle prüft den exakten actionbezogenen
COUNT innerhalb derselben bestätigten IMMEDIATE-Schreibtransaktion, vor dem
abhängigen Task-Write. Bei COUNT >=5.000 werden Task-Anlage bzw. Statuswechsel
und Event zusammen mit `TASK_EVENT_LIMIT_REACHED` abgelehnt. Das ist eine reguläre
fachliche Ablehnung, kein STORAGE_FAILURE/BLOCKED und kein Speichererfolg.
Bei 4.999 bleibt genau ein Eventplatz. Titel/Priorität/Zuständigkeit/Fälligkeit
ändern erzeugt keinen Event und bleibt bei sonst erfüllten Regeln erlaubt.
Altbestände >5.000 bleiben unverändert lesbar/paginierbar; neue Events sind dort
ebenfalls gesperrt. Keine automatische Rotation, Kürzung oder Löschung. Die
bewusste PREPARATION-Tasklöschung mit bestehender Cascade-Regel bleibt unverändert;
die Grenze betrifft aktuell gespeicherte Events, keinen lebenslangen Zähler.

Taskdetail zeigt eine kompakte deutschsprachige Chronik ohne Autoreninformationen.
Seit §85a lädt Workspace-/Tasknavigation keine actionweite Eventliste. Die Chronik wird
bewusst je Task in höchstens 32 Ereignissen angefordert, mit exakter Gesamtzahl und
Keyset-Reihenfolge `(occurredAt,rowid)`. Weitere Seiten ersetzen die angezeigte Seite;
„Chronik von Anfang an“ startet ausdrücklich neu. Eine Seitennummer und „Letzte Seite“
ermöglichen den direkten Einstieg auch in lange Chroniken innerhalb der unveränderten
Sessionfristen. Nur ein skalarer Sortieranker wird positionsbezogen gelesen; die Eventpayload
verwendet weiterhin denselben 32er-Keyset-Reader. Nicht angefordert, Laden, Fehler und
tatsächlich leere Historie bleiben getrennt. Lock/Navigation/Editorwechsel verwerfen die
Seite; nach Writer-/Generationswechsel ist ein frischer Einstieg erforderlich. Events werden
weder gelöscht noch gekürzt; Status/Zähler/Editor verwenden weiterhin vollständige Tasks.
Titel-, Prioritäts-, Zuständigkeits-, affectedPerson- und Fälligkeitsänderungen
werden nicht historisiert. Keine Kommentare, Änderungsgründe, Geräte-/Benutzer-/IP-
oder Standortmetadaten. Die aktuelle Titelanzeige ist der heutige Snapshot.

P5a ist **keine allgemeine Audit-Historie**. P4 bleibt ausschließlich der aktuelle
Personen-Lage-Snapshot: keine früheren OperationalStatus-, Rollen- oder Ortswerte,
keine Bewegungs-/Personen-Lagehistorie. Eine allgemeine Aktionschronik ist eine
spätere ausdrückliche Produkt-/Privacy-Entscheidung.

## Aktionsstatus und Bedienung

| Status | Aufgaben |
| --- | --- |
| DRAFT | Kein Task-Verwaltungspfad |
| PREPARATION | Erstellen, bearbeiten, zuordnen, Fälligkeit/Priorität setzen, Vorlagen auswählen, vollständig löschen |
| ACTIVE | Erstellen, bearbeiten, Lifecycle und Wiederöffnen; kein Löschen, stattdessen Entfällt |
| COMPLETED | Aufgaben und Events ausschließlich lesen |
| DELETION_PENDING | Kein normaler Task-Pfad |

Die Prüfung verwendet den persistierten Aktionsstatus innerhalb der Transaktion.
Der Aktionslebenszyklus erlaubt keinen Rückweg aus ACTIVE nach PREPARATION.

ACTIVE bleibt Lagebild: Aufgaben stehen prominent vor den Personen, mit High-Zahl
(offen/in Arbeit), OPEN- und IN_PROGRESS-Zahl. Aktive Tasks: HIGH, NORMAL, LOW;
innerhalb gleicher Priorität Fälligkeit vor keiner Fälligkeit, früheste zuerst,
danach createdAt und ID als stabiler Tie-Breaker. DONE/CANCELLED liegen eingeklappt
separat. Priorität und Status erscheinen als Text, nicht nur Farbe.

„+ Aufgabe“ verlangt nur den Titel, Standard NORMAL/OPEN. Optionale Zuordnungen und
betroffene Person werden unabhängig gewählt. Jede Personenkarte zeigt die Zahl
aktiver betroffener Aufgaben; „Aufgaben ansehen“ filtert, „Aufgabe hinzufügen“
belegt affectedPerson vor, frei änderbar/entfernbar. Taskdetail bietet In Arbeit,
Erledigt, Entfällt und Wieder öffnen ohne zusätzliche Bestätigung oder Step-up.
Formulare verwenden bestehende große Buttons und Fokus-/Keyboard-Komponenten.
Abbrechen speichert nichts; Drafts bleiben flüchtig, Lock/Navigation entfernen sie.

## Vorlagen und P5b-Grenze

Statische Domain-Vorlagen, keine persistierte globale TaskTemplate-Tabelle:

| sourceTemplateKey | Vorschlag |
| --- | --- |
| RALLY_AT_ACTION | Eilkundgebung gegenüber der Aktion vorbereiten/anmelden |
| RALLY_AT_CUSTODY_SUPPORT | Eilkundgebung für Gesa-Support vorbereiten/anmelden |
| PREPARE_ACTION_PHONE | Aktionshandy vorbereiten |
| CHECK_COMMUNICATION | Kommunikationswege prüfen |
| ASSIGN_PRESS | Pressezuständigkeit klären |

„Aufgaben aus Vorlagen“ öffnet erst bei Auswahl einen normalen Task-Entwurf; erst
Speichern erzeugt Task und CREATED. Keine automatische Anlage. Organisatorische
Vorschläge sind keine Aussage über rechtliche Pflichten; die Leitung entscheidet.
Technische Keys werden nicht angezeigt. sourceTemplateKey wird bei Erstellung
übernommen und bleibt bei Bearbeitung erhalten. Keine Unique-Regel: derselbe Key
kann für verschiedene betroffene Personen oder mehrere Aufgaben verwendet werden.

P5b kann später z.B. CONTACT_EA mit affectedPerson und vorgeschlagener Rolle nutzen.
P5a implementiert keine Personenstatus-Regeln, situationsabhängigen Vorschläge oder
automatische Task-Erzeugung.

## Speicherung, Session und Privacy

Task/TaskEvent sind ausdrücklich operative INTERNAL-Daten in der bestehenden
SQLCipher-Produkt-DB `no_backup/pag-db-v1`, keine eigene Kryptoschicht. Titel können
sensibel sein; kurze Titel, bevorzugt Pseudonyme, keine Übernahme von P3b-Payloads.
Normale Taskarbeit ist in einer gültigen READY-Sitzung ohne Leadership-Step-up
zugänglich. Das ist der bewusste operative Trade-off analog P4; eine offene Sitzung
kann diese Daten offenlegen. P3b-Kryptografie und Zugriffsschutz bleiben unverändert.

Bestehende StorageAccess-Prüfungen einschließlich Generation, Vordergrund, absoluter
Deadline, Inaktivität, Commit und Publication bleiben wirksam. Kurze Room-Handles,
vor Commit Gate, nach Revoke keine Publication; keine langlebige DB-Umgehung.
3 Minuten absolut, 30 Sekunden READY-Inaktivität, Vordergrundverlust → LOCKED.
Task-UI-Pfade prüfen nach Interaktions-Callbacks die bestehende READY-Session,
Generation und Aktionsbindung; ein Revoke darf bereinigte Taskdaten auch nach
erneuter Authentisierung nicht automatisch wieder einsetzen.

Schema 5 → 6 ist additiv: tasks und task_events, alte Tabellen und Ciphertexts
bleiben erhalten; kein destructive Fallback. Room-Schema 6 wird exportiert.
Tasks und Events sind aktionsgebunden, Action-FK und TaskEvent-FK löschen abhängig;
ein späterer Action-/Retentionlöschpfad muss wie P4 Referenzen geordnet auflösen.
Keine permanente Archivierung, eigene Retentionjobs, Exporte oder physische
Secure-Overwrite-Garantie. Keine Permissions, Netzwerk-/Cloudfunktion, neuen
exportierten Komponenten oder Dependencies.

## Automatische Prüfung und manueller UX-Smoke

Domain-/UI-Modelltests prüfen Titel-Codepoints, Defaults, unabhängige Zuordnungen,
alle Lifecycle-Paare, completedAt/Reopen, Sortierung/Personenzahlen, Templates,
Cancel ohne Mutation, Read-only und Generation-Entzug. Storage-Instrumentation
prüft die echte SQLCipher-/Room-Migration, Fremdschlüssel/Trigger, Rollen-IN_USE,
persistierte Aktionsstatusgrenzen, CREATED-/Lifecycle-Atomarität, fehlschlagendes
Event-Insert, Commit-Widerruf und unterdrückte Publication nach Generation-Revoke.
UI-Anzeigen nutzen nur Pseudonyme/Rollennamen; keine sensitive P3b-Payload.

Keine automatische End-to-End-Touch-/IME-Prüfung wird behauptet. Sinnvoller späterer
manueller Smoke mit ausschließlich synthetischen Daten:

1. PREPARATION: eine Vorlage auswählen, Entwurf abbrechen (keine Task); erneut
   auswählen und speichern. Titel/Priorität/Fälligkeit bearbeiten, Task löschen.
2. ACTIVE: bei Motte „Aufgabe hinzufügen“, HIGH „EA kontaktieren“, betrifft Motte,
   zuständig Rolle Gesa-Support. Auswahl der betroffenen Person entfernen/wechseln
   und wieder Motte setzen, ohne die Zuständigkeit zu ändern.
3. Lagebild: HIGH prominent, Motte mit einer offenen Aufgabe; „Aufgaben ansehen“
   filtert und führt zum Aufgabenbereich. Task öffnen → In Arbeit → Erledigt →
   Wieder öffnen. Chronik behält Erledigt, completedAt wird wieder null; keine
   zusätzliche Personen-Lagehistorie. Entfällt/Reopen ebenfalls ausprobieren.
4. Entwurf oder Bearbeitung abbrechen: keine Persistenz. Tap außerhalb Textfeld,
   Speichern, Abbrechen und Navigation schließen die Tastatur. Touch-Ziele,
   Scrollwege, optionale Fälligkeit und lange Pseudonyme/Rollennamen beurteilen.
5. COMPLETED: letzte Aufgaben/Chronik lesbar, keine Bearbeitung, Löschung oder
   Wiedereröffnung. Lock verwirft flüchtige Task-Entwürfe.

## Geänderte Dateien

- Domain: `domain/src/main/kotlin/org/pag/domain/Tasks.kt`.
- Data: `data/src/main/kotlin/org/pag/data/product/TaskDatabase.kt`,
  `ProductTaskRepository.kt`, `ActionDatabase.kt`, `ProductOperationsRepository.kt`,
  `ProductRuntime.kt`; `data/schemas/org.pag.data.product.ActionDatabase/6.json`.
- App: `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`; im Verzeichnis
  `app/src/main/kotlin/org/pag/prototype/product/`: `TaskModel.kt`, `TaskScreen.kt`,
  `OperationsScreen.kt`, `ProductScreen.kt`, `ProductTime.kt`, `ProductViewModel.kt`.
- Tests: `app/src/test/kotlin/org/pag/prototype/product/TaskModelTest.kt`,
  `app/src/test/kotlin/org/pag/prototype/product/ProductTimeTest.kt`,
  `data/src/test/kotlin/org/pag/data/product/TaskRulesTest.kt`; im Verzeichnis
  `data/src/androidTest/kotlin/org/pag/data/product/`: `TaskStorageTest.kt`,
  `ActionLifecycleStorageTest.kt`, `PersonStorageTest.kt`, `LeadershipStorageTest.kt`,
  `OperationsStorageTest.kt`. Bestehende Migration-Fixtures berücksichtigen nur
  die zusätzlichen Tabellen und prüfen nun die aktuelle Schema-Version 6.
- Doku: `PRODUCT.md`, `docs/product-p5a.md`, `docs/data-model.md`, `docs/privacy.md`,
  `docs/security.md`.

## Prüfergebnis P5a

- Vollständige Unit-Suite: 405 Tests, 0 Failures/Errors/Skips (App 62, Data 339,
  Security 4). Zusätzlich fokussiert: TaskRulesTest 6, TaskModelTest 15 und ProductTimeTest 4 grün.
  Die neuen Tests decken synchronen Lock/Revoke innerhalb der Interaktion,
  erneute Authentisierung, Aktionswechsel sowie DST-Lücke/-Überlappung ab.
- Galaxy SM-M526BR/API33: 92 Instrumentationstests erfolgreich, davon 12 neue
  TaskStorageTest, 13 OperationsStorageTest, 12 ActionLifecycleStorageTest,
  17 PersonStorageTest, 24 LeadershipStorageTest, zusätzlich 13 ProductActionsTest
  und 1 LeadershipKdfBenchmarkTest. Migration 5 → 6, bestehende
  Migration-Fixtures und P3b-Ciphertexts erfolgreich geprüft.
- App-Build, App-/Data-AndroidTest-Build, App/Data/Security-Lint, Architekturcheck,
  Merged Manifest und Permissions erfolgreich. Einzige App-Lint-Warnung ist die
  bestehende AndroidGradlePluginVersion-Warnung; keine neue P5a-Warnung.
- Schemaexport 6: sämtliche bisherigen Entity-/Index-/Field-Definitionen von
  Schema 5 unverändert, ausschließlich tasks/task_events hinzugefügt. Exklusive
  Zuständigkeit wird bei Migration und frischer DB durch dieselben SQLite-Trigger
  abgesichert; Room exportiert Trigger nicht als Entity-Definitionen.
- Security-Diff: P3b-Crypto, Argon2id, Tink, Keystore, Sensitive-State-Guard,
  Leadership-Step-up, SessionCoordinator und SessionDbKeyOwner unverändert.
  Manifest, Permissions, Dependencies und Backup-/Transferregeln unverändert.
- Keine invasiven Geräteaktionen, Credential-Änderungen oder Produktdatenmutation;
  lediglich install -r des separaten, synthetischen Data-Testpakets. Debug-Signatur
  verwendet das vorhandene Projekt-Testverzeichnis ANDROID_USER_HOME.
- git diff --check erfolgreich. 27 geänderte/neue Dateien, kein Commit/Push;
  main und origin/main bleiben auf 9cec912. Manueller Touch-/IME-Smoke steht oben,
  ist nicht als automatisiert ausgeführt zu verstehen.

Prüflogs liegen im ignorierten `.prototype-cache/p5a-*.log`; die Produktdokumentation
enthält keine echten personenbezogenen Testdaten oder Schlüssel.
