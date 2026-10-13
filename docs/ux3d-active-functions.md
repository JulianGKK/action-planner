# UX3d — ACTIVE Funktionenarbeitsbereich

Baseline: sauberer Branch `ux3d-active-functions`, HEAD `8d9ade8` (Merge UX3c).
Der historische Release-Tag `v0.1.0-alpha.1` bleibt unverändert.

## Leitfrage und aktuelle Verteilung

„Wer übernimmt gerade was?“: Hub → Funktionenübersicht → Funktion / Ohne Funktion
→ Person → vorhandener UX3b-Arbeitsbereich. Security- und Aktionsheader bleiben
getrennt. Ausschließlich P4-INTERNAL-Snapshot und Pseudonyme, keine P3b-Inhalte.

Summary zählt ausschließlich ON_SITE: Gesamtzahl, mit currentRoleId != null
zugeordnet, ohne aktuelle Zuordnung sowie unterschiedliche genutzte currentRoleIds.
NO_STATUS zählt auch mit technisch übernommener Hauptfunktion nicht; Gewahrsam,
Nichterreichbarkeit und alle anderen nicht verfügbaren Lagen zählen ebenfalls nicht.
Ohne ON_SITE: „Niemand aktuell vor Ort“. Keine Warnung oder Bewertung.

Primär erscheinen nur aktuell genutzte Funktionen mit Anzahl und kompakter
Pseudonymliste. Gruppen und Personen behalten die gelieferte P4-Reihenfolge;
Rollen kommen aus `ORDER BY normalizedName,id`, keine neue Wichtigkeitssortierung.
„Ohne Funktion“ ist eine eigene Gruppe ausschließlich für ON_SITE/currentRole=null
und wird bei null Personen nicht als leere Karte angezeigt.

Vorhandene Rollen ohne ON_SITE-Zuordnung stehen nachrangig unter
„Weitere Funktionen (N)“, initial eingeklappt, mit Namen ohne Problemformulierung.
Die Übersicht behauptet weder Sollbesetzung noch fehlende/erforderliche Rollen.
Planrollen sind keine aktuellen Gruppen und erhalten keine neue Navigation.
Die bestehende Funktionen-Hubkarte bleibt unverändert auf P4 `roleCount` gestützt.

## Fokus und Änderungen

Eine ganze Funktionskarte öffnet das Detail mit Personen als großen Touchzielen.
Tap auf Person verwendet das bestehende `OperationsModel.focus` und den UX3b-
Bereich mit Lage ändern/Funktion ändern/Personendetails. Keine parallele Personen-
oder Mutationlogik. Funktions-/Lagewechsel verwenden den bestehenden erwarteten
Snapshot und P4-Write; nicht verfügbare Lagen entfernen Funktion gemäß P4.

Ein kleiner flüchtiger `FunctionFocus` unterscheidet ausdrücklich keinen Fokus
von der fokussierten Ohne-Funktion-Gruppe. Nach Save bleibt die Person sichtbar;
Zurück führt zur ursprünglichen Funktion. Deren Mitglieder werden aus dem aktuellen
Board berechnet. Wird sie leer: „Derzeit niemand in dieser Funktion vor Ort.“
Ohne-Funktion-Detail entsprechend neutral. Kein Sprung zum Hub und kein veralteter
Mitglieder-Cache. Der nächste Rückweg führt zur Funktionenübersicht.

„+ Neue Funktion“ ist ein großer Secondary-Control. Das Formular verwendet den
bestehenden P4-createRole-Pfad, Keyboard/Fokus und Feedback. Erfolg führt zur
Übersicht; die neue Rolle ist bis zur tatsächlichen Zuordnung unter Weitere
Funktionen erreichbar. Keine automatische Zuordnung, Rename, Delete oder Planung
in ACTIVE. Fehlgeschlagene Anlage hält den korrigierbaren Entwurf ohne Erfolgsmeldung.
COMPLETED bleibt im bisherigen read-only-Pfad; kein UX5-Umbau.

## Loading und Navigation

Ein vollständiges OperationalBoard publiziert Summary, Gruppen, Ohne Funktion
und Weitere-Zahl gemeinsam. Initial LOADING → vollständiger Content; Refresh hält
den gültigen alten Snapshot bis zum Ersatz. Kein Taskread oder Inventory für
Funktionsdarstellung/-navigation. Persondetails nutzen erst bei explizitem Tap den
bestehenden Person-/P3b-Pfad. Taskaktionen im UX3b-Personenbereich erscheinen nur,
wenn dessen passendes TaskBoard bereits vorhanden ist; kein wirkungsloser Control
und kein Taskread allein für Funktionsnavigation.

Group-/Person-/Rücknavigation und Expand/Collapse benötigen keinen Boardread.
Writes verwenden den vorhandenen einzelnen Workspace-Refresh. Rückkehr zum Hub
lädt dessen bestehende vollständige Projektion. Der Funktionsfokus ist Teil des
vorhandenen Scroll-Resets, keine persistierte Route.
READY-/Generation-/Action-/Requestguards bleiben maßgeblich. Lock, Generation-
oder Actionwechsel löschen Funktions-/Personenfokus, Disclosure, Anlageentwurf
und Workspace. Kein SavedState, DataStore, Preferences oder DB-Navigationsstate.

## Accessibility und Grenzen

Einspaltige, umbrechende Karten mit semantischen Funktionsnamen und Personen-
Überschriften, textlicher Anzahl und benannten Öffnungsaktionen. Mindestens
48-dp-Touchziele, 4-dp-Controls; Weitere-Control benennt Expand/Collapse-Zustand.
Keine Mini-Chips, reine Farbcodierung oder neue LiveRegion. Font Scale 1/2 wird
synthetisch gegen echte Produkt-Compose-UI geprüft. Manuelles TalkBack-/IME-Review
und lange Namen bleiben sinnvoll.

Keine Fach-, Schema-, Security-, Dependency- oder Produktpermissionänderung.
SessionCoordinator, StorageAccess, LeadershipGrant, Key Admission, Sessionrestart,
DB-Key-Lifecycle, Keystore, Crypto und P3b bleiben unverändert. Demo bleibt
unverändert; keine Pflichtrollen, Unterbesetzungslogik, automatische Bewertung,
P5b oder UX5.

## Prüfungen und manueller Smoke

`ActiveFunctionsTest` prüft ON_SITE/currentRole-Verteilung, alle ausgeschlossenen
Lagen, Plantrennung, Reihenfolge und Übereinstimmung mit P4 roleCount, Gruppen-/
Personfokus, Rollenwechsel/Statuswechsel/leere Gruppe, bestehende Erstellung und
Feedback, Fehler/Abbrechen, Atomic Reveal, Refresh, stale Requests/Generation/
Action, Lock sowie COMPLETED. `Ux3dFunctionsProbe` nutzt synthetische Ports gegen
die echte ProductScreen-UI, ohne Produkt-DB-Writes oder Credentials.

Manuell: Hub → Funktionen, genutzte Gruppe und Ohne Funktion öffnen, Person und
Funktion wechseln, zurück zur ursprünglichen/leeren Gruppe; neue Funktion unter
Weitere finden, Lage auf nicht verfügbar ändern, Summary und Hub prüfen;
große Schrift/TalkBack und Lock im Detail/Anlageformular.

### Automatischer Nachweis

- 669 Unit-Tests grün: App 290 (27 neue UX3d-Fälle), Data 375, Security 4;
  keine Failures/Errors/Skips. UX2/UX3a/UX3b/UX3c, P4/P5a, UX1b-Navigation,
  Sessionrestart und ProductTime/DST bleiben grün.
- Produkt-/Demo-Build und Produkt-AndroidTest erfolgreich. Lint ohne Fehler
  oder neue Hinweise; normal nur bestehende Gradle-Versionswarnung, Demo ohne
  Befund. Architektur sowie normale/demo Manifest-/Permissionchecks grün.
  Keine neue Produktpermission und kein INTERNET.
- Galaxy SM-M526BR/API33: finaler synthetischer `Ux3dFunctionsProbe` gegen echte
  ProductScreen bei Font Scale 1/2 grün. Hub, Verteilung, Gruppe/Person,
  Funktionswechsel/Rückkehr zur leeren Gruppe, Ohne Funktion, Neuanlage/Weitere,
  Statuswechsel, Hubsummary und Lock im Detail/Anlageformular geprüft.
  Keine Credentialänderung, kein pm clear/Uninstall und keine Systemfontänderung.
- Der erste Geräte-Probe war nicht grün: ein unmittelbar anschließender Klick
  auf den Pseudonymtext traf noch die Übersichtskarte. Der Probe wartet jetzt
  ausdrücklich auf den Gruppendetail-Header und anschließend den UX3b-Zustand.
  Keine UI-/Fachregeländerung zum Umgehen der Probe.
- Lokale Logs: `.prototype-cache/ux3d-checks.log`, `ux3d-probe-build.log`,
  `ux3d-galaxy.log`. `git diff --check` grün. Alpha-Tag unverändert.
  Kein Commit, kein Push.
