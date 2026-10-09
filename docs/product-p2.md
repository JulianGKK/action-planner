# P2 — Aktion bearbeiten und Statuslebenszyklus

## Umfang und fachlicher Vertrag

P2 erweitert den produktiven DB-only-Slice um Bearbeitung und expliziten
Statusfortschritt. Dateien, weitere fachliche Entitäten und Lösch-/Retentionjobs
sind nicht Bestandteil dieses Slices.

Das Datenmodell beschreibt den normalen Lebenszyklus und verlangt beim Abschluss
einen tatsächlichen Abschlusszeitpunkt. Weitere Übergänge sind unter OD-01 noch
offen. P2 verwendet deshalb ausschließlich das freigegebene monotone Minimum:

| Status | Bearbeiten | Nächste bestätigungspflichtige Aktion |
| --- | --- | --- |
| DRAFT | Ja | Vorbereitung beginnen → PREPARATION |
| PREPARATION | Ja | Aktion starten → ACTIVE |
| ACTIVE | Nein | Aktion abschließen → COMPLETED |
| COMPLETED | Nein | Keine |
| DELETION_PENDING | Nein | Keine |

COMPLETED ist terminal; Wiedereröffnung und Rückwärtsübergänge sind ausgeschlossen.
DELETION_PENDING bleibt im Modell, wird durch P2 aber weder angeboten noch erzeugt.
Zusätzliche Übergänge und eine mögliche ACTIVE-Bearbeitung benötigen eine spätere
bewusste Produktentscheidung. Retention-Scheduling wird hier nicht implementiert.

## Umsetzung

`ActionLifecycle` in der reinen Kotlin-Domain ist die gemeinsame Regel für UI und
Repository. Bearbeitung verändert nur Titel, Beschreibung, Beginn und erwartetes
Ende. Identität, Erstellungszeit und Status bleiben erhalten. Der Abschluss setzt
`completedAt`; dieser fachliche Zeitstempel verwendet die Gerätezeit und ist keine
Autorisierungsdeadline.

Repository-Änderungen lesen und prüfen den persistierten Zustand innerhalb derselben
kontrollierten Schreibtransaktion. Bearbeitung vergleicht den vollständigen erwarteten
Datensatz, Statusfortschritt den erwarteten Ausgangsstatus. Veraltete Erwartungen,
unbekannte IDs, unzulässige Statusschritte und ungültige Eingaben werden ohne fachliche
Änderung abgewiesen. Gate-Prüfungen erfolgen vor den Schritten, vor Commit und vor
Ergebnisveröffentlichung; Widerruf erlaubt keine neue Publication.

Room bleibt pro Operation kurz geöffnet. SessionCoordinator, SessionDbKeyOwner,
Envelope, B2Driver sowie die 3-Minuten-/30-Sekunden-Grenzen bleiben unverändert.
Produkt-DB-Schema 2 ergänzt mittels expliziter Migration 1 → 2 ausschließlich die
nullable Spalte `completedAt`. Bestehende P1-Datensätze bleiben erhalten; unbekannte
Schema-Versionen werden blockiert, nicht destruktiv ersetzt. Native Vorprüf-Gates
und unconditional Cleanup bleiben erhalten.

Die Detailansicht zeigt nur erlaubte Aktionen. Jede Transition benötigt eine
Bestätigung, der Abschluss weist auf fehlende Wiedereröffnung hin. Inline-Bestätigung
und bestehende Kalender-/Uhrzeitpicker benötigen kein weiteres Fenster. Entwürfe
bleiben ausschließlich in flüchtigem Compose-State; Abbrechen schreibt nichts.
Gerätezeitzone, Verbot von Ende vor Beginn und DST-Prüfung bleiben erhalten.
Unveränderte Zeitfelder behalten ihren ursprünglichen eindeutigen Zeitpunkt.

## Prüfungen

- Frische gesamte Unit-Suite: 316 Tests, 0 Failures, 0 Errors, 0 Skips.
  Darunter 4 neue Domain-Regeltests und 7 neue ViewModel-Tests für Bearbeitung,
  Abbruch, Bestätigung, Listen-/Detailaktualisierung, terminalen Status und Lock.
- Gezielte verschlüsselte Room-/SQLCipher-Instrumentation im getrennten
  `org.pag.data.test`-Paket auf Galaxy M52 / Android 13 / API33: 25 Tests bestanden
  (13 bestehende `ProductActionsTest`, 12 neue `ActionLifecycleStorageTest`),
  0 Failures, 0 Errors, 0 Skips. Der produktive App-Bestand wurde nicht verändert.
- Die neue Storage-Testklasse umfasst Bearbeitung, konkurrierende Erwartungen,
  monotone Persistenz, unbekannte IDs, terminale Zustände, Commit-/Publication-Widerruf,
  P1-Migration, widerrufene Migration und unbekannte Schema-Versionen.
- JUnit 4.13.2 wird zusätzlich im App-Modul ausschließlich als Testdependency
  verwendet; keine neue Runtime-Dependency, Permission oder Produktkomponente.
- App-Build, App-/data-AndroidTest-Build, Lint für app/data/security,
  Architekturprüfung, Product-Manifestprüfung und `git diff --check` bestanden.
  Backup-/Transfer-Ausschlüsse und ausschließlich exportierte Launcher-Komponente
  sind unverändert. Die Gradle-Aufrufe erfolgten getrennt; der bekannte kombinierte
  Task-Wiring-Befund wurde nicht verändert.

## Offene Punkte

Der manuelle P2-Produktsmoke wurde nach dem automatischen Abschlussbericht bestanden.
Weitere Statusübergänge, Retention und Löschung bleiben getrennte spätere Slices.
Es wurde keine neue invasive Testphase durchgeführt.

## MANUAL GALAXY/API33 PRODUCT SMOKE

Status: **P2 MANUAL PRODUCT SMOKE BESTANDEN IM GETESTETEN GALAXY/API33-UMFANG**.

Vorbereitung: Galaxy M52 / API33 war verbunden. Der lesend
ermittelte Digest der installierten APK unterscheidet sich vom automatisch
geprüften aktuellen P2-Build. Vor dem Test wurde deshalb der P2-Appstand geklärt.
Bei dieser ersten lesenden Prüfung wurde keine App installiert, gestartet oder
authentifiziert und kein Produktbestand verändert.

### Referenz vor dem freigegebenen App-Update

- Installiertes Package: `org.pag.prototype.a`; Update verwendet dieselbe Package-ID.
- Produkt-Namespace `no_backup/pag-db-v1` vorhanden: `intent`, `complete`,
  `public`, `envelope` und `store/d` (16.384 Bytes). `d-journal` und `d.lck`
  sind leer. Ausschließlich Namen, Typen und Größen wurden gelesen.
- Bestehende synthetische P1-Aktionen Alpha/Beta sind durch den abgeschlossenen
  P1-Smoke dokumentiert. Der verschlüsselte Produktbestand ist vor dem Update
  weiterhin vorhanden; fachliche Inhalte werden nach bewusstem Unlock erneut geprüft.
- Geprüfter P2-APK-Digest: SHA-256
  `4f2c98dd2d99148779b2b34decee25ad6b8aa3fea4ad6531e6f329d60989c4af`.
- Datenerhaltendes `adb install -r` ist ausdrücklich freigegeben; kein Clear,
  Uninstall, Restore oder manuelles Verändern von Daten/Keys.

### App-Update

`adb install -r` wurde genau einmal ausgeführt: `Performing Streamed Install`,
`Success`. Der anschließende lesende Vergleich zeigt denselben Produkt-Namespace
mit identischen Dateigrößen und mtimes. Die App wurde danach normal gestartet.
Es wurde keine Einrichtung oder Authentifizierung ausgelöst. Migration und
fachlicher Datenerhalt wurden anschließend durch die manuelle Unlock-Bestätigung
bestätigt (siehe folgenden Abschnitt).

### Bestehende P1-Daten nach dem Update

Benutzer bestätigt: Start gesperrt mit „Entsperren“, kein Einrichtungs- oder
BLOCKED-Zustand. Nach bewusstem Entsperren und DEVICE_CREDENTIAL im Systemdialog
sind Alpha und Beta jeweils genau einmal mit ihren bisherigen Werten vorhanden.
Damit ist der fachliche Datenerhalt beim ersten Öffnen durch P2 einschließlich
der normalen Schema-1→2-Migration im manuellen Produktpfad bestätigt.

### Bearbeiten — Abbrechen

Benutzer bestätigt für Alpha in DRAFT: Bearbeiten geöffnet, synthetische Änderung
vorgenommen und abgebrochen. Detail und Liste behalten die alten Werte, Alpha ist
weiterhin genau einmal vorhanden und der Status bleibt „Entwurf“. PASS.

### Bearbeiten — Speichern

Benutzer bestätigt: Alpha auf „P2 Lifecycle Test“ umbenannt und gespeichert.
Detail und Liste zeigen den neuen Titel, weiterhin genau ein Datensatz und
Status „Entwurf“. Beta bleibt unverändert. PASS.

### DRAFT → PREPARATION

Benutzer bestätigt: Im Entwurf waren „Bearbeiten“ und genau eine Statusaktion
„Vorbereitung beginnen“ vorhanden. Nach geprüftem Hinweis und Bestätigung wird
„Vorbereitung“ angezeigt. Bearbeiten bleibt verfügbar, genau eine nächste
Statusaktion „Aktion starten“ und keine Rückwärtsaktion werden angeboten. PASS.

### PREPARATION bearbeiten

Benutzer bestätigt: Beschreibung auf „Synthetischer P2 Funktionstest – Vorbereitung
geprüft“ geändert und gespeichert. Neue Beschreibung sichtbar, Titel unverändert,
Status weiterhin „Vorbereitung“ und derselbe Datensatz genau einmal vorhanden. PASS.

### PREPARATION → ACTIVE / ACTIVE read-only

Benutzer bestätigt: „Aktion starten“ gewählt und bestätigt. Status „Aktiv“,
bearbeitete Werte korrekt; „Bearbeiten“ wird nicht mehr angeboten. Genau eine
nächste Statusaktion „Aktion abschließen“, keine Rückwärtsaktion. PASS.

### ACTIVE → COMPLETED / COMPLETED terminal

Benutzer bestätigt: Abschluss gewählt, Hinweis auf ausgeschlossene Bearbeitung
und Wiedereröffnung geprüft und bestätigt. Status „Abgeschlossen“, Detail lesbar
und Abschlusszeitpunkt sichtbar. Kein Bearbeiten, weitere Statusaktion,
Wiederöffnen oder Zurücksetzen. Nach Rückkehr zur Liste und erneutem Öffnen bleibt
derselbe abgeschlossene, unveränderbare Zustand erhalten. PASS.

### Lock/Unlock, andere Aktion und fehlender Löschpfad

Benutzer bestätigt: Nach Sperren und bewusstem DEVICE_CREDENTIAL-Unlock bleibt
„P2 Lifecycle Test“ abgeschlossen, bearbeitete Werte und Abschlusszeitpunkt
erhalten; kein Bearbeiten oder Wiederöffnen. Beta unverändert. Kein
Löschen-/Löschvormerkungsbutton im normalen Produktfluss. PASS.

### Prozessneustart — Ausführung

`am force-stop org.pag.prototype.a` wurde einmal ausgeführt und die App danach
normal über ihre Launcher-Activity gestartet. Keine Datenlöschung oder
Initialisierung. Die anschließende sichtbare Post-Restart-Prüfung wurde bestanden
(siehe folgenden Abschnitt).

### Prozessneustart — Ergebnis und Abschluss

Benutzer bestätigt: Start „Gesperrt“ mit „Entsperren“, keine Einrichtung und kein
automatischer Authdialog. Nach bewusstem DEVICE_CREDENTIAL-Unlock ist
„P2 Lifecycle Test“ weiterhin genau einmal vorhanden und abgeschlossen;
bearbeitete Werte und Abschlusszeitpunkt erhalten. Kein Bearbeiten, Wiederöffnen,
Statuswechsel oder Löschpfad. Beta weiterhin unverändert. PASS.

Alle vorgesehenen manuellen Schritte sind bestanden. Die Evidenz gilt für diesen
synthetischen Produktbestand auf Galaxy M52 / Android 13 / API33. Keine weitere
invasive Phase oder Testsuite wurde ausgeführt. Während des Smokes wurde nur diese
Dokumentation geändert; Produktcode blieb unverändert.

LAST SAFE CHECKPOINT: P2 MANUAL PRODUCT SMOKE vollständig bestanden einschließlich
Prozessneustart und erneutem explizitem Unlock; Abschlusszustand und Beta erhalten.
Keine weiteren Geräteaktionen nötig.

## Terminologie-Bereinigung vor dem P2-Commit

Die deutschen Produkttexte und fachlichen Beschreibungen verwenden nun „Aktion“.
Technische Identifier, Schema und Test-IDs bleiben unverändert. Historische
UI-Beschreibungen in der Dokumentation wurden sprachlich vereinheitlicht;
die damaligen Testresultate bleiben unverändert. Der bestandene manuelle
Smoke bezog sich auf die UI vor dieser ausschließlich sprachlichen Anpassung.
