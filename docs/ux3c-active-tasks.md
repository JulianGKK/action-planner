# UX3c — ACTIVE Aufgabenarbeitsbereich

Baseline: sauberer Branch `ux3c-active-tasks`, HEAD `7f8655b` (Merge UX3b).
Der historische Release-Tag `v0.1.0-alpha.1` bleibt unverändert.

## Hierarchie und Übersicht

Lagebild → Aufgabenübersicht → Aufgabe → Lifecycleaktion/Bearbeitung.
Der Securityheader bleibt getrennt und unverändert. Karten zeigen Titel,
textuelle Priorität/Status, Zuständigkeit, optional betroffene Person und Fälligkeit.
Eine ganze Karte ist das Touchziel; keine Lifecyclebuttons innerhalb der Liste.
Zuständigkeit bleibt niemand ODER Person ODER Rolle; Personanzeige ausschließlich
Pseudonym. Betroffenheit ist unabhängig, keine Identitäts-/Kontaktdaten aus P3b.

Summary aus demselben vollständigen TaskBoard: OPEN und IN_PROGRESS getrennt,
HIGH ausschließlich unter aktiven Tasks. Keine aktiven Tasks:
„Keine offenen Aufgaben“. Keine Alarm-, Risikoklasse oder Eskalation.
Aktive und geschlossene Listen stammen aus `TaskBoard.active` / `closed`;
die UI filtert, sortiert aber nicht erneut. HIGH vor NORMAL vor LOW, gesetzte
Fälligkeit zuerst, früheste Fälligkeit, danach createdAt und ID bleiben P5a.

DONE/CANCELLED liegen separat unter „Erledigt / Entfallen (N)“, standardmäßig
eingeklappt. Disclosure ist flüchtiger TaskModel-State, kein SavedState.
Aufklappen zeigt dieselben kompakten Karten mit unterscheidbaren Statuslabels.

## Fokus, Aktionen und Formular

Das Detail zeigt die aktuellen Taskfelder, zulässige P5a-Lifecycleaktionen,
„Aufgabe bearbeiten“ und die bestehende Chronik. OPEN: In Arbeit/Erledigt/Entfällt;
IN_PROGRESS: Erledigt/Entfällt; DONE/CANCELLED: Wieder öffnen.
Die Aktionsliste wird direkt aus `TaskRules.event` abgeleitet.
ACTIVE erlaubt kein Löschen. PREPARATION und COMPLETED verwenden weiterhin ihren
bestehenden TaskScreen; COMPLETED bleibt ausschließlich lesbar.

Erstellen/Bearbeiten verwendet unverändert das vorhandene Formular und seine
Validierung. Nur Titel erforderlich, NORMAL/OPEN; exklusive Zuständigkeit,
unabhängige Betroffenheit, optionaler Timestamp. Keine Tagesfälligkeit,
Notification, Alarm, Worker, neuen Felder oder P5b-Vorschläge.
Fälligkeit erscheint am heutigen lokalen Tag als HH:mm, sonst mit Datum/Jahr;
die Speicherung und DST-Gap-/Overlap-Abweisung bleiben unverändert.

Save und Transition behalten den bestehenden Detailfokus. Erst der vollständig
publizierte Ersatz-Snapshot aktualisiert ausgewählten Task und Chronik gemeinsam.
Auch ein geschlossener Task bleibt nachvollziehbar im Detail. Bewusstes Zurück
führt zur Übersicht, wo er in der passenden Liste liegt. Neue Aufgaben führen
nach Speichern zur aktualisierten Übersicht mit bestehendem Floating Feedback.
Abbrechen einer ACTIVE-Bearbeitung kehrt ohne Write/Read zum ursprünglichen Detail
zurück; Abbrechen einer Neuanlage verwirft den Entwurf.
Rücknavigation steht im Header; Statusänderung setzt den Detail-Scroll auf den
Header zurück. Keine unnötigen Boardreloads für Öffnen/Zurück/Filter/Disclosure.

## Chronik und Personenfilter

Chronik: ausschließlich vorhandene TaskEvents Erstellt, In Arbeit, Erledigt,
Entfällt, Wieder geöffnet mit Zeit. Keine Autorenaussage und keine früheren
Titel, Prioritäten, Zuordnungen, Betroffenheiten oder Fälligkeiten.

„Aufgaben ansehen“ aus dem bestehenden Personenarbeitsbereich bleibt erhalten.
Der Filter ist sichtbar, auch im Detail/Formular, und bezieht sich ausschließlich
auf affectedPersonId. Summary und Closed-Zahl zählen denselben sichtbaren Ausschnitt.
„Alle Aufgaben anzeigen“ entfernt den Filter ohne Read; keine versteckte Persistenz.
Personbezogene Neuanlage behält die bestehende affectedPerson-Vorbelegung, die
entfernt/geändert werden kann. Auch „+ Aufgabe“ im gefilterten Bereich übergibt
den sichtbaren Personenkontext an das bestehende `TaskModel.add`.

## Loading, Cleanup und Accessibility

Vorhandener Workspace-Atomic-Reveal: Operations-/TaskBoard lokal stagen, einmal
gemeinsam publizieren. Summary, Liste und Closed-Zahl erscheinen vollständig;
keine Behauptung einer gemeinsamen DB-Transaktion der beiden Reads.
Refresh behält gültigen Content bis zum Ersatz. READY/Generation/Action/Bereich/
Requestguards begrenzen Publication. Lock/Generation-/Actionwechsel löschen
Taskdetail, Formular, Filter, Disclosure und Daten. Navigation bleibt RAM-only.

Einspaltige Karten, umbrechende Texte, semantischer Titel, benannte Öffnungsaktion,
4-dp-Controls und mindestens 48-dp-Touchziele. Closed-Control benennt seinen
Expandierzustand. Keine Bedeutung nur über Farbe und keine neue LiveRegion.
Große Schrift wird synthetisch auf Gerät geprüft; manuelles TalkBack/IME und
Lange-Titel-/Datum-Review bleiben sinnvoll.

## Grenzen und Prüfungen

Keine Änderung an P5a-/P4-Domain, DB/Schema, Securityarchitektur oder P3b.
SessionCoordinator, StorageAccess, LeadershipGrant, Key-Admission, Sessionrestart,
DB-Key-Lifecycle, Keystore und Crypto sind unverändert. Keine Dependencies,
Produktpermissions oder Demoänderungen. UX3d bleibt separat.

`ActiveTasksTest`: Summary/Sortierung/Karten, Zuordnungen/Fälligkeit, Actions,
Fokus/Save/Transition/Feedback, Closed/Chronik, Filter/Vorbelegung, Atomic Reveal,
Refresh, stale Requests/Generation, Lock und COMPLETED. `Ux3cTasksProbe` verwendet
echte Produkt-Compose-UI mit synthetischen Ports für Hub, Task-Lifecycle,
Bearbeiten/Erstellen, Closed, Personenfilter, Rückwege und Lock bei Font Scale 1/2.
Keine Produkt-DB-Writes, Credentialänderung, Datenlöschung oder Systemfontänderung.

Manueller Smoke: Hub → Aufgaben, Zuordnungen/Betroffenheit/Fälligkeit, Task öffnen,
In Arbeit/Erledigt/Entfällt/Wieder öffnen, bearbeiten/neu anlegen, Chronik,
Closed-Section, Personenfilter entfernen und Person-Vorbelegung; zurück Hub,
große Schrift/TalkBack und Lock während Detail/Formular.

### Automatischer Nachweis

- 642 Unit-Tests grün: App 263 (29 neue UX3c-Fälle), Data 375, Security 4;
  keine Failures/Errors/Skips. UX2/UX3a/UX3b, P5a, UX1b-/Sessionrestart und
  ProductTime/DST-Regressionen eingeschlossen.
- Produkt-/Demo-Build und Produkt-AndroidTest erfolgreich. Lint ohne Fehler
  oder neue Hinweise; normale App nur bestehende Gradle-Versionswarnung,
  Demo ohne Befund. Architektur und beide Manifest-/Permissionchecks grün.
  Produktpermissions unverändert; weder Produkt noch Demo enthält INTERNET.
- Galaxy SM-M526BR/API33: finaler synthetischer `Ux3cTasksProbe` gegen echte
  ProductScreen bei Font Scale 1/2 grün. Hub/Karten, In Arbeit/Erledigt/Wieder
  öffnen, Bearbeiten/Neuanlage, Closed, Personenfilter, Rücknavigation und Lock
  im Detail/Formular geprüft. CANCELLED und die übrigen P5a-Actions sind zusätzlich
  durch die Modeltests abgedeckt. Keine Credentialänderung, kein pm clear,
  kein Uninstall und keine Systemfontänderung.
- Der erste Geräte-Probe war nicht grün: Speichern war nach der autofokussierten
  Neuanlage im Probe nicht erreichbar.
  Der Probe prüft die tatsächliche Titelübernahme und beendet Texteingabe nun
  über die vorhandene IME-Done-Aktion, bevor er Speichern bedient.
  Keine Veränderung von Fachregeln oder Formularvalidierung für die Probe.
- Lokale Logs: `.prototype-cache/ux3c-checks.log`, `ux3c-probe-build.log`,
  `ux3c-galaxy.log`. `git diff --check` grün; historischer Alpha-Tag unverändert.
  Kein Commit, kein Push.
