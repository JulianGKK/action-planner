# UX4 — Eigenständige PaG Demo

Baseline: sauberer `main`, `e399a27`. Keine Produktdaten und kein Produktmodus
innerhalb der Demo. Kein Commit oder Push.

## Zweck und Kennzeichnung

Interessierte können Vorbereitung und operative Lageführung mit einer einzigen
synthetischen Aktion ausprobieren. Dauerhaft sichtbar: **DEMO – nur Beispieldaten**
und **Bitte keine echten Daten eingeben.** Home erklärt, dass Änderungen nicht
gespeichert werden und die Demo die Sicherheitsfunktionen nicht vollständig nachbildet.
Keine Fake-PIN, Passphrase, Sessiondeadline oder vermeintlich echten Grants.

## Build und technische Grenze

Zusätzlicher debug-artiger Build Type `demo`, keine Flavors:

```sh
./gradlew :app:assembleDemo --offline
```

APK: `app/build/outputs/apk/demo/app-demo.apk`.
Application-ID `org.pag.prototype.a.demo`, sichtbarer Name **PaG Demo**.
Normale Builds behalten `org.pag.prototype.a` und ihre bisherigen Tasknamen.
`BuildConfig.PAG_DEMO_ONLY` ist nur in demo true, in debug/release false.

MainActivity wählt vor Runtime-Konstruktion genau einen Einstieg. Demo startet
direkt mit Home. ProductRuntime, Produktmodel, Worker und Timer-Handler werden
nicht erzeugt; keine Session, DB, Key-Owner, Produkt-Keystore-/P3b- oder Credential-
Initialisierung. Lifecycle-Callbacks berühren Produktkomponenten nur, wenn die
normale Runtime existiert. Produktinitialisierung und Sicherheitslogik bleiben
ansonsten unverändert. Keine Umschaltung über Intent, UI oder gespeicherte Einstellung.

Die Demo hat keine Repository-/Storage-/Session-Capability. `DemoState` enthält
nur Domainwerte und Compose-RAM-State. Architekturprüfung verbietet Data-/Security-
Imports und Produkt-/Persistenzcapabilities in Demoquellen. Bibliotheken des
bestehenden App-Moduls bleiben im APK enthalten; ihre Produkt-Runtime wird in der
Demo nicht initialisiert. Keine Behauptung eines vollständig getrennten Binärmoduls.

Getrennte Packages erhalten getrennte Android-Sandboxes. Die Demo öffnet keine
Produktdateien, weder im normalen noch im eigenen Datenverzeichnis. Merged Demo-
Manifest entfernt USE_BIOMETRIC und erlaubt nur die automatisch generierte interne
Signature-Receiver-Permission. Ein Launcher, keine zusätzlichen Komponenten,
kein INTERNET; Backup deaktiviert. Normalmanifest/Permissions bleiben unverändert.

## Szenarien und Fachregeln

Vorbereitung: Demo-Aktion Innenstadt mit Motte, Meise, Falter, Amsel, Klee und Rabe,
Planrollen, Rollen und Aufgaben. Übersicht/Personen/Aufgaben/Rollen orientieren
sich an UX2. Personen anlegen/bearbeiten, Rollenplanung, Rollen/Vorlagen, Tasks und
Stammdaten bearbeiten funktionieren ausschließlich im RAM. Datum-/Zeitwahl nutzt
die vorhandenen Picker und unveränderte eindeutige ProductTime-/DST-Konvertierung.

„Aktion starten“ bestätigt den bestehenden irreversiblen Phasenwechsel. Hauptplanrolle
wird initiale aktuelle Funktion, Lage NO_STATUS, updatedAt 0; keine behauptete
Anwesenheit. Tasks bleiben erhalten. Danach öffnet der Hub.

Direkt ins Lagebild: ACTIVE mit Vor-Ort-Personen, fehlender Lage, Nichterreichbarkeit,
Gewahrsam sowie OPEN/HIGH/IN_PROGRESS/DONE-Aufgaben. Gemeinsame stateless UX3a-
Hub-Karten und Summaryprojektion für Personen/Aufgaben/Funktionen/Aktionsdetails.
Änderungen reagieren unmittelbar, weil der gesamte Demostate synchron im RAM liegt.
Keine künstlichen I/O-/Loadingverzögerungen.

P4-Anzeige zählt nur ON_SITE mit currentRole; Planrollen bleiben getrennt.
`OperationalRules` entscheiden Rollen-/Ortszulässigkeit. Gewahrsam und
Nichterreichbarkeit entfernen aktuelle Funktion. ACTIVE-Aktionsdetails und
Planung sind read-only. P5a verwendet `TaskRules` für Validierung, Sortierung,
Statusschritte, completedAt und Reopen. Zuständigkeit exklusiv, Betroffenheit
unabhängig. Taskereignisse der Demoänderungen bleiben ausschließlich im RAM.

Leitungsschutz wird nur erklärt: Im Echtbetrieb zusätzliche Leadership-Passphrase;
in der Demo kein Entsperren. Keine Inventories, entschlüsselten Daten, Keys oder
Fake-Schutzversprechen. Personen-/Taskdetails sind eine kleine eigenständige
Demoabbildung; kein großer ProductViewModel-Refactor und kein Fake-SessionPort.
Gemeinsam sind stateless Controls, Keyboard/Fokus, Picker und Hubdarstellung.

## Rundgang, Reset und Lebensdauer

Optionaler Vier-Schritte-Hinweis im ACTIVE-Szenario: Personlage, Aufgabe in Arbeit,
Funktionen, Hubzahlen. Weiter/Überspringen ohne blockierende Overlays oder Persistenz.

Reset ist während eines Szenarios permanent erreichbar. Er verwirft Editoren,
Änderungen, Chronik und Tourfortschritt und lädt das ursprüngliche Szenario neu.
Auch nach Start der Preparation-Demo führt Reset zurück zur Vorbereitung.
Demo beenden führt zu Home und verwirft den State. Activity-Ende/Background-Stop
und neue Instanz starten ebenfalls ohne alte Eingaben. Kein SavedState,
rememberSaveable, Room, Dateien, Preferences oder DataStore. Referenzfreigabe ist
keine Garantie vollständiger RAM-Überschreibung. Eine injizierbare DemoClock erzeugt
relative aktuelle Szenariozeiten; sie ist keine Sicherheitsuhr.

Keine Imports/Exporte, Kamera, Uploads, Netzwerk, Telemetrie oder Rückführung in
Produktdaten. Feedback wird außerhalb der App gegeben.

## Prüfungen und manueller Smoke

`DemoStateTest`: Home, beide Szenarien, lokale CRUD-/Plan-/Task-/Lageänderungen,
Hubsummary, Lifecycle, Fachgrenzen, Reset/Ende/neue Instanz und relative Clock.
`AppModeTest`: nur gewählte Factory, keine Produktkonstruktion im Demozweig,
Compile-Flag und Application-ID. Beide App-Testvarianten prüfen dieselben
Produkt-/UX2-/UX3a-Regressionen.

Demo-Instrumentation separat aktivieren, ohne normale Tasknamen umzubauen:

```sh
./gradlew :app:assembleDemoAndroidTest -PpagTestBuildType=demo --offline
```

Test-APK: `app/build/outputs/apk/androidTest/demo/app-demo-androidTest.apk`.
`Ux4DemoProbe` prüft den echten APK-Einstieg, uninitialisierte Produktfelder/Lazies,
leere DB-/NoBackup-Inventare, reale Compose-Controls und Marker bei Font Scale 1/2.
Im normalen APK prüft derselbe Probe vorhandene ProductRuntime und LOCKED ohne
Credential-Anforderung. Synthetische UI-Proben ersetzen kein manuelles UX-Review.

Manuell: Home → Vorbereitung; Person/Rolle/Task und Datum/Zeit ändern;
Aktion starten; Lage/Funktion und Taskstatus ändern; Hubzahlen prüfen; Direktstart,
Rundgang/Überspringen, Reset und Beenden; große Schrift/TalkBack. Beide App-Icons
parallel prüfen: PaG Demo ohne Auth, normale PaG-App mit normalem LOCKED/Unlock.

### Finale Nachweise

- Normale Unit-Suite: **588 Tests grün** (App 209, Data 375, Security 4),
  einschließlich 24 neuer Demo-/Factoryfälle. Zusätzlich **209 App-Tests gegen
  demo** grün; null Failures/Errors/Skips. UX2/UX3a, Session-Restart, P4/P5a,
  P3b und ProductTime/DST bleiben grün.
- Debug-, Release- und Demo-App-Builds; Debug-/Demo-App-AndroidTest sowie
  Data-Debug-AndroidTest erfolgreich. Architektur und beide Manifestprüfungen grün.
  Lint demo ohne Befund; debug nur mit bestehender AGP-Versionswarnung.
- Erneute Abschlussprüfung am 13.10.2026, Galaxy SM-M526BR/API33:
  echter Demo-Einstieg/Isolation und kompletter Probe bei Font Scale 1 grün;
  Font Scale 2 scheitert reproduzierbar an der Aufgabenkarte im Hub
  (`phase=hub-Aufgaben`, Control nicht erreichbar). Eine versuchsweise erweiterte
  Treffererkennung half nicht und wurde zurückgenommen. Ursache noch offen;
  die vollständige Font-Scale-2-Abnahme ist daher nicht grün. Echter normaler
  LOCKED-/Runtime-Einstieg grün. Beide Packages gleichzeitig vorhanden.
  Keine Credential-Änderung, kein pm clear/Uninstall und keine Änderung der
  Systemschrift. Die früheren grünen Geräteläufe ersetzen diesen offenen Befund
  der erneuten Prüfung nicht. Abschlussstatus: MINOR ISSUE / UI-Probelücke.
- Die anfänglichen UI-Probe-Fehlläufe waren nicht grün: Accessibility-Frames wurden
  begrenzt abgewartet, Rückkehr zum Hub bestätigt und große Karten über ihre
  tatsächlichen Touchziele mit kleinen Wischbewegungen erreichbar gemacht.
  Keine Product-/Demo-Layoutänderung zum Umgehen einer Probe.
- Lokale Prüflogs: `.prototype-cache/ux4-checks.log`, `ux4-demo-androidtest.log`,
  `ux4-final-android-lint.log`, `ux4-demo-galaxy.log`, `ux4-product-galaxy.log`,
  `ux4-regression-galaxy.log`. `git diff --check` grün. Kein Commit, kein Push.
