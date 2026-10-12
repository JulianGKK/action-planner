# UX2 — Preparation Workspace

Baseline: sauberer `main`, HEAD `4ba8f5a`. Kein Commit oder Push.
Grundlagen: PRODUCT, UX-Philosophie, UX1b und UX1-S; Fach-/Securityverträge bleiben maßgeblich.

## DRAFT und PREPARATION

DRAFT ist ein ruhiger Entwurf mit Titel, Beschreibung, Beginn/Ende,
Stammdatenbearbeitung und primärem „Vorbereitung beginnen“. Keine Personen-,
Aufgaben-, Rollen- oder P3b-Reads für diesen Screen und keine Workspace-Reiter.

PREPARATION hat vier getrennte Bereiche: Übersicht, Personen, Aufgaben, Rollen.
Der fachliche Header enthält „‹ Aktionen“, Titel und lesbaren Status. Er beginnt
unter dem unveränderten Security-Header. Die Bereichsauswahl verwendet große,
4-dp-Controls mit Häkchen und Selected-/Tab-Semantik. FlowRow bricht bei schmaler
Breite/großer Schrift um; keine verkleinerten Mini-Links oder Pill-Optik.
ACTIVE und COMPLETED behalten ihre bisherige funktionale Darstellung; kein UX3-Hub.

## Bereiche

Übersicht: Beschreibung auf zwei Zeilen begrenzt, Beginn/Ende, „Aktionsdetails
bearbeiten“, Personen-/Rollenanzahl, offene/in-Arbeit-/aktive HIGH-Aufgaben und
Leitungsschutz. Keine Prozentzahl, Completeness Engine oder neue Startpflicht.
Counts führen direkt in den zugehörigen Bereich. Fehlende Providerdaten werden
nicht als Nullzahl ausgegeben.

Leitungsschutz verwendet ausschließlich `LeadershipRepository.actionState`.
UNINITIALIZED bietet nach bewusstem Tap das bestehende Einrichtungsformular,
auch ohne Person; READY zeigt „eingerichtet“; PARTIAL_OR_INVALID und Rejected
bleiben ohne Einrichtung/Step-up und ohne Reparatur. Initial wartet der gesamte
Übersichtsinhalt auf das Metadatenergebnis. Keine personengebundene Inventory-
Abfrage, Passphrase-Anfrage oder Payload-/Grantöffnung durch die Übersicht.
Das explizite Formular verwendet unverändert SensitiveSection/Capture-Schutz
und den bestehenden Setup-Port. Rückkehr aktualisiert den aktionsweiten Status.

Personen: Summary mit Gesamtzahl und fehlender geplanter Hauptrolle; kompakte
Pseudonym-/Planrollenkarten als Tap-Ziel. Keine ACTIVE-Lage auf Preparationkarten
und kein „Geplant: Keine geplanten Rollen“-Spam. Fehlende Rolle knapp „Noch keine
Rolle“. Person hinzufügen, fokussiertes Detail, Rollenplanung und bestehende P3b-
Personfunktionen bleiben erreichbar.

Aufgaben: bestehende P5a-Summary, Sortierung, exklusive Zuständigkeit, Betroffenheit,
Lifecycle und Minimalchronik. „+ Aufgabe“, statische Vorlagen als Vorschläge,
DONE/CANCELLED weiterhin separat aufklappbar. Keine automatische Erstellung/P5b.

Rollen: Rolle plus geplante Zuordnungs-/Hauptrollenzahl. Eigene Rolle bewusst über
„Rolle anlegen“; Vorlagen sind aufklappbare Vorschläge mit echten Anlegecontrols.
Kein Autofokus beim bloßen Lesen; neuer Rolleneditor fokussiert einmalig.
Umbenennen/Löschen verwenden bestehende Regeln einschließlich IN_USE. Formular
schließt erst nach bestätigtem Erfolg; Rejected bleibt als korrigierbarer Fehler.

## Lifecycle und Datum/Zeit

„Vorbereitung beginnen“ und „Aktion starten“ sind primäre, volle Controls mit der
bestehenden Konsequenzbestätigung. Weiterhin nur DRAFT → PREPARATION → ACTIVE →
COMPLETED. Keine Skips, Rückwärtsbewegung, Wiederöffnung oder neuen Pflichtchecks.

Stammdaten öffnen ein fokussiertes Formular mit Rückweg oben. Beginn/Ende bieten
separate lesbare Datum-/Uhrzeitcontrols und jeweils einen bestehenden inline
Android/Material-Picker. Keine zweite Fenster-/Foreground-Ausnahme. Bei neuen
Werten entsteht erst nach bewusster Auswahl beider Teile ein Zeitpunkt.
`selectedInstant`/ProductTime bleiben unverändert: exakt ein Offset, Gap/Overlap
ablehnen, Ende nicht vor Beginn, unveränderte bestehende Instants erhalten.
Task-Fälligkeit behält ihren bisherigen Zeitpunkt-/DST-Vertrag.

## Loading und Navigation

| Bereich | Erforderliche bestehende Reads vor erster Publication |
| --- | --- |
| DRAFT | Aktion |
| Übersicht | Operations-Board, Task-Board, actionState |
| Personen | Operations-Board mit Personen und Plänen |
| Aufgaben | Task-Board und Operations-Board für Zuordnungslabels |
| Rollen | Operations-Board, kein Task-/Metadata-Read |
| Persondetail | Person und bestehendes personengebundenes Inventory |

Ergebnisse werden lokal gesammelt und als ein WorkspaceSnapshot publiziert.
Initial LOADING → vollständiges CONTENT/echtes EMPTY oder ERROR. Refresh behält
den gültigen alten Snapshot bis vollständigem Ersatz; ein Refreshfehler lässt
Content mit Fehlerhinweis stehen. Keine teilweise Countmontage oder Fake-Leere.
Publication prüft READY, Generation, Action-ID/Status, Request und Preparation-
Bereich. Technische Reads/Countdowns sind keine Nutzerinteraktion.

Bereichsauswahl lebt ausschließlich im RAM derselben gültigen Session. Wechsel
verwirft lokale Formulare, bestätigt keine Writes und widerruft sensitive Ansicht.
Lock/Generation-/Actionwechsel entfernen Snapshot, Metadata, Navigation, Editoren,
Countdown und Banner. Keine SavedState-/DataStore-/Preferences-/DB-Navigation.

## Accessibility und Grenzen

Semantische Überschriften, Selected-/Tab-Semantik und Häkchen; Information nicht
nur über Farbe. Touchziele mindestens 48 dp, Primary mindestens 56 dp. Text darf
wachsen/umbrechen, fachliche Rückwege liegen oben. UX1-KeyboardHints und einmaliger
Neuanlagefokus bleiben; kein Timer-Fokusloop. Floating Banner verändert weiterhin
nicht das Contentlayout; Countdown bleibt ohne sekündliche LiveRegion.

Keine Änderung an SessionCoordinator, StorageAccess, LeadershipGrant/Admission,
Crypto/Keystore, P3b-Metadata, DB-Key-Lifecycle, Fachregeln, Schema, Dependencies,
Permissions oder Produktkomponenten. Keine Netzwerkfunktion.

## Prüfungen

`PreparationWorkspaceTest` prüft tatsächliche Model-/Port-/Requestzustände:
DRAFT/Transition, vier Bereiche und gezielte Reads, dreiteilige Atomic Reveal,
Summary, alle Metadatazustände/Error, bewusstes Setup ohne Person, Personen,
Rollen, Tasks, Empty/Error/Refresh, stale Generation/Action/Bereich, Lock-/Setup-
Cleanup und Start ohne künstliche Readinesspflichten. Bestehende UX1/P5a/P3b/
ProductTime-Regressionen gehören zur vollständigen Unit-Suite.

Der bestehende Ux1bShellProbe wird mit synthetischen UX2-Fällen bei Font Scale
1 und 2 erweitert: DRAFT/Lifecycle, echte Tab-/Formcontrols, Selected/Touch-Bounds,
actionState ohne Inventory, Bereichsreads, Pickercontrols, Initial Loading, Lock
während Formular/Load und anschließender unveränderter UX1-S-/Shellprüfung.
Keine neue Testinfrastruktur oder echte Credentialeingabe.

### Finale automatische Nachweise

**547 Unit-Tests grün**, null Failures/Errors/Skips: App 168, Data 375, Security 4.
Enthalten: 30 neue UX2-Fälle, UX1 Shell/Atomic Reveal/Banner, 13 SessionRestart-,
15 P5a TaskModel-, relevante P3b-Metadata-/Model-/Admissionfälle und vier
ProductTime/DST-Regressionen. Die beiden zusätzlichen Cleanupfälle prüfen
Revoke während Bereichswechsel bzw. Setupöffnung, bevor neuer UI-State gesetzt wird.

App-Build und beide AndroidTest-Builds, Lint, Architektur und Merged Manifest/
Permissions erfolgreich. Lint ausschließlich mit bestehendem AGP-Versionshinweis.
Manifest weiterhin USE_BIOMETRIC plus interner signature-basierter Receiver-
Permission und ein exportierter Launcher; keine neue Permission/Komponente,
kein INTERNET. Prüflog: `.prototype-cache/ux2-checks.log`.

Galaxy **SM-M526BR/API33**: erweiterter Ux1b/UX1-S/UX2-Shell-Probe auf finalen APKs
bei Font Scale 1/2 grün; bestehender Ux1a Locked-/Bootstrap-Probe ebenfalls grün.
Nachweise: `.prototype-cache/ux2-shell-galaxy.log`, `ux2-locked-galaxy.log` und
`ux2-probe-build.log`. Frühere Probe-Fehlläufe wurden nicht als grün gezählt:
Accessibility-Transitions werden begrenzt abgewartet, ausgewählte Tabs nicht
fälschlich als klickbare Buttons behandelt, gleichnamige Überschriften ignoriert
und zurückliegende Controls rückwärts statt weiter nach unten gesucht.
Keine Productänderung zum Umgehen einer Probe; die tatsächlichen neuen Tabs und
Formcontrols werden aktiviert, die Selected-/Touch-Bounds anschließend geprüft.

Keine Credentialänderung, kein pm clear/Uninstall und keine Systemschriftänderung.
Installation ausschließlich install -r; Schriftfaktoren nur im synthetischen
Compose-Test. Keine neue große Storage-/Security-Gerätekampagne. Reale Writes,
Pickerbedienung, TalkBack und regulärer DEVICE_CREDENTIAL-Flow bleiben manueller
UX-Smoke. Keine Behauptung, dass synthetische UI-Ports reale Storage-/Authgates beweisen.

Dateiscope: ProductScreen, ProductViewModel, PagShell, OperationsScreen,
TaskScreen und Ux1bShellProbe; neu PreparationWorkspace, PreparationWorkspaceTest
und diese Dokumentation; UX-Roadmap aktualisiert. Insgesamt sieben modifizierte
und drei neue Dateien. Security-/Data-/Domain-Code, Schema und Dependencies unverändert.
`git diff --check` ohne Befund, HEAD `4ba8f5a`; kein Commit, kein Push.

## Kurzer manueller Galaxy-Smoke

1. Regulär entsperren, DRAFT öffnen: Entwurf/Stammdaten, keine Workspace-Reiter.
2. Vorbereitung beginnen bewusst bestätigen; alle vier Bereiche mit Rückwegen öffnen.
3. Person, Aufgabe, eigene Rolle und Vorlagenrolle anlegen; Fokus/Banner prüfen.
4. Leitungsschutz vor erster Person ansehen und bewusst einrichten; P3b-Persondetail testen.
5. Aktionsdetails bearbeiten: Datum/Zeit, Abbruch, Speichern und DST-Fehlermeldung prüfen.
6. Große Schrift/TalkBack: Bereichsauswahl, Touchziele, Überschriften und Rücknavigation.
7. Während Formular und Load sperren/App verlassen: kein alter Inhalt oder Editor nach Unlock.
8. „Aktion starten“ bestätigen: unveränderter ACTIVE-Einstieg, kein UX3-Hub/Restore.
