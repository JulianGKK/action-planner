# UX1b — Polish auf UX1a

Stand: 12.10.2026. Uncommittierter Slice auf UX1a/UX0 (`6277059`).
Kein Commit oder Push. Verbindliche Grundlage: [UX-Philosophie](ux-philosophy.md).

## Atomic Screen Reveal

Der heute sichtbare fachliche Screen zeigt initial Loading oder einen vollständigen
Snapshot. Die Aktionsliste bleibt bis zur erfolgreichen Publication Loading und
entscheidet danach Empty/Content. Beim Öffnen eines Aktionsdetails wird zunächst
die Aktion geladen. DRAFT benötigt keine Personen-/Operations-/Taskdaten.
PREPARATION, ACTIVE und COMPLETED zeigen im heutigen Design Personen, Rollen,
Lage und Tasks gemeinsam: hier bündelt `ProductViewModel` Operations-Board und
Task-Board in einem einzigen kurzlebigen `WorkspaceSnapshot`.

Das bestehende Operations-Board enthält bereits alle sichtbaren Personen,
Rollenpläne, aktuellen Funktionen und Lagewerte. Ein zusätzlicher Personen-
Listenread wäre redundant und entfällt im produktiven gemeinsamen Arbeitsplatz.
Die Personenliste, OperationsModel und TaskModel beziehen ihre Anzeigedaten aus
demselben UI-Snapshot. Prioritäten, Taskliste und Counts stammen aus demselben
Task-Board. Nach einem ersten Teilresultat gibt es keine UI-Publication.

Initial erforderliche Daten:

| Heutiger Screen | Erster sichtbarer Snapshot |
| --- | --- |
| Aktionsliste | Erfolgreicher vollständiger Action-Load |
| DRAFT-Detail | Aktion mit Status und Zeitfeldern |
| Preparation/ACTIVE/Completed-Übersicht | Aktion, Operations-Board einschließlich Personen/Rollen/Lage, Task-Board einschließlich Tasks/Events |
| Personenliste/-karten | Bestandteil des gemeinsamen Workspace-Snapshots einschließlich sichtbarer Taskzahlen |
| Persondetail | Person plus vorhandene Inventory-Metadaten; keine P3b-Payload ohne bisherigen Step-up |
| Rollen/Plan/Lageformular | Bereits vollständig geladene Operationsdaten; Refresh liest nur das sichtbare Operations-Board |
| Taskliste/-formular/-detail | Vollständiges Task-Board und Personen-/Rollenlabels aus dem Workspace |

TaskEvents gehören zum bestehenden kurzen Board-Port und erscheinen im Taskdetail.
Keine neue große DB-Operation, Connection oder Room-Instanz. Beide bestehenden
Repository-Reads verwenden weiterhin kurze Transaktionen, Sessionkontext,
Key-Owner und autoritatives `context.publish`. Worker-Ausführung bleibt im
bestehenden Runtime-Modell. Es werden keine neuen parallelen DB-Zugriffe eingeführt.
UI-Atomicität ist keine Behauptung einer gemeinsamen DB-Transaktion über beide Reads.

## Initial Load und Refresh

Vor erstem Resultat wird der gesamte betroffene Inhalt hinter einem ruhigen
Loading-State gehalten, einschließlich Detailüberschrift. Persondetails warten
auf ihre vorhandenen Inventory-Metadaten und springen nicht von Pseudonym zu
Setup-/Bereichscontrols. Rejected Metadata wird als bestehender verständlicher
Fehler angezeigt, nicht als fortlaufender Load.

Refresh derselben gültigen Action-/Generationbindung behält den vollständigen
bisherigen Snapshot. Neue Teilresultate werden ausschließlich lokal gesammelt;
der neue vollständige Snapshot ersetzt ihn einmal. Auch die Aktionsliste wird
bei Refresh nicht zunächst gelöscht. Bei Statuswechsel wird der alte unpassende
Workspace verworfen. Ein Loadfehler ist kein erfolgreich leeres Ergebnis.
Storage-/Integrityfehler bleiben BLOCKED, ohne Retry oder Ersatzanlage.

Vor UI-Publication: READY, Generation, Action-ID und Status, Workspace-Request
und Navigationbindung. Lock, Generationwechsel und Actionwechsel invalidieren
alte Reads und entfernen operativen State. Persondetail-Callbacks prüfen auch
Sensitive-Request und Navigation; Lock/Zurück verwerfen verzögerte Metadaten.
Rückkehr von Rollen/Plan/Lage in die gemeinsame Übersicht lädt einen vollständigen
Refresh. Kein Refresh oder technischer Read zählt als Benutzerinteraktion.

## Stabiler READY-Header

MAINTENANCE behält den eigenen neutralen Prüfzustand. Der Sessionobserver fragt
beim Eintritt in READY die vorhandene Restzeitprojektion synchron ab, bevor die
READY-Shell komponiert wird. Falls die Abfrage inzwischen Ablauf feststellt,
bleibt LOCKED maßgeblich und der Action-Load startet nicht.

Arbeitsheader: `🔒 1:42` und ein kompakter umrandeter Secondary-Control `Sperren`.
Kein sichtbares PaG vor dem Timer. Die FlowRow richtet Elemente vertikal aus und
bricht bei großer Schrift um, statt sie zu überlappen. Systemleistenabstand bleibt
berücksichtigt. Der Body wechselt getrennt von Loading zu vollständigem Content.

Countdown weiter read-only aus derselben sleep-inclusive monotonen Deadline.
Keine Verlängerung, kein Re-Auth/Refresh, keine Interaktion, keine LiveRegion.
Fokusbeschreibung: „Sitzung: maximal noch 1 Minute 42 Sekunden. Inaktivität kann
früher sperren.“ Letzte positive Teilsekunde bleibt mindestens `0:01`.

## Button-Hierarchie

Gemeinsame FormInput-Wrapper: 4-dp-Radius, rechteckige Silhouette, Primary mindestens
56 dp und volle Breite, Secondary mindestens 48 dp und klare Kontur. Text darf
vertikal wachsen; Primary-Text ist zentriert. Keyboard-/Fokus-Cleanup bleibt.
`Sperren` ist kompakter Secondary, keine Capsule und kein TextButton.

Gezielter Audit über alle produktiven Screen-Dateien: Speicherung, Hinzufügen,
Löschen, Auswahl mit fachlicher Wirkung, Lifecycle, Security und größere Navigation
sind Primary/Secondary. Tertiary bleibt nur bei Aktionsdetails und optionalen
Task-Disclosure-Gruppen (geschlossene Tasks, Vorlagen, optionale Auswahlgruppen).
Rollenaktionen umbrechen in einer FlowRow bei großer Schrift. Kein neues Theme,
Logo, Farbsystem oder Schriftfamilienentscheid.

## Floating Banner und Fortschritt

`PagStatusBannerHost` ist ein Box-Overlay als Geschwister des scrollenden Inhalts,
am oberen Rand des geschützten Arbeitsbereichs unterhalb des Sessionheaders.
Er verändert die Content-Messung nicht. Kleine Rundung, ruhiger Text, dünner
Balken am unteren Rand. Keine vollflächige modale Interaktionssperre.

Lebensdauer mindestens fünf Sekunden, mit vorhandenem Accessibility-Timeout.
Balken von 100 % zu 0 %, einfache zeitabhängige Updates alle circa 50 ms statt
komplexer Animation. Auch bei reduzierten Animationen bleibt der Verlauf vorhanden.
`System.nanoTime` wird nur für diese UI-Lebensdauer genutzt, niemals als Session-
Zeitquelle. Text erhält eine Polite-LiveRegion; Fortschritt hat bewusst keine
Accessibility-Semantik und keine Prozentansagen. Die Textsemantik ändert sich
während Fortschrittsupdates nicht.

Erfolgsfeedback erst nach bestätigter Repository-Publication:

- Person gespeichert (weiter ohne Pseudonym)
- Rolle „Support“ angelegt — eigene Rolle und Vorlage nutzen denselben Writepfad
- Aufgabe angelegt / gespeichert / wieder geöffnet — ohne Titel

Andere unmittelbar sichtbare Task-Statuswechsel erzeugen keine Bannerflut.
Vor und nach synchron möglichem Refresh werden Bindungen geprüft. Exceptions,
Rejected, stale und revoked Results erzeugen keinen Erfolg. Timer-ID verhindert,
dass alter Ablauf einen neuen Banner löscht. Lock entfernt State und Overlay;
Compose cancelt den Effekt. Keine Persistenz, keine P3b-Daten, keine Notifications.

## Keyboard und Eintrittsfokus

`productKeyboard` liefert ausschließlich KeyboardOptions:

| Feld | Hint |
| --- | --- |
| Pseudonym/Name, Rollenname, Ort/Ziel | Words |
| Actiontitel | Sentences, weil heute frei formulierte Aktionsbezeichnung |
| Tasktitel, Beschreibung, Beziehungstext | Sentences |
| Telefonnummer | Phone, keine Autokorrektur |
| Datum | Keine Großschreibung/Autokorrektur |
| Passphrase | Bisheriger Password-Hint, keine Großschreibung/Autokorrektur |

Keine Wertumformung, Migration oder geänderte Domain-Normalisierung.
Einfache neue Personen-, Rollen- und Taskformulare fokussieren das erste Feld
und öffnen IME einmal beim Eintritt. Kein Recomposition-/Countdown-Fokus.
Bei aktivierter Touch Exploration wird kein Eingabefokus erzwungen; Screenreader-
Bedienung hat Vorrang. Lock entfernt Formularsubtrees und vorhandener FormInput-
Cleanup schließt Keyboard/Fokus. Keine Wiederherstellung aus SavedState.

## Prüfungen und Grenzen

Die vollständige Offline-Prüfung ist erfolgreich:

```text
ANDROID_USER_HOME="$PWD/.prototype-cache/android" ./gradlew \
  :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest \
  :domain:test :app:assembleDebug :app:assembleDebugAndroidTest \
  :data:assembleDebugAndroidTest :app:lintDebug verifyArchitecture \
  :app:verifyPrototypeManifest --offline
```

**446 Unit-Tests**, null Failures/Errors/Skips: App 102, Data 340, Security 4.
Domain hat keine eigenen Tests. UX1b ergänzt 30 Tests gegenüber UX1a:
24 Snapshot-/Feedback-/Keyboardtests, drei Persondetail-Metadatenregressionen
und drei Personen-Refresh-/Revoke-/Navigationsregressionen. Die sechs vorhandenen
PagShell-Tests bleiben; Generationwechsel erwartet jetzt sofort die neue gültige
Projektion statt eines leeren Countdown-Slots.

App- und beide AndroidTest-Builds, Architektur- und Manifestcheck grün. Lint:
null Fehler, nur der vorhandene Gradle-Versionshinweis. Keine neue Dependency.
Merged Produktmanifest: bestehendes USE_BIOMETRIC und signature-basierte interne
Receiver-Permission; ein exportierter Launcher, keine neue Produktkomponente,
kein INTERNET, keine Notification-/Location-/Contacts-/Phone-/SMS-Permission.
Der zusätzliche UX1b-Runner ist ausschließlich Test-Instrumentation.

Galaxy SM-M526BR/API33, aktuelle APKs erfolgreich mit install -r aktualisiert:

- **67 aktuelle Storage-Instrumentationstests erfolgreich**, 540 Sekunden:
  ProductActions 13, ActionLifecycle 12, Person 17, Operations 13, Task 12.
- **UX1b-Probe bei Font Scale 1 und 2 erfolgreich**: READY-Projektion bereits vor
  erster Composition, Header ohne PaG, Countdown/Sperren ohne Überlappung und
  ausreichende Touchfläche, tatsächlicher Person-Eintrittsfokus, Erfolgsbanner,
  keine Timer-Interaktion und unmittelbares Model-Cleanup beim Lock.
  Der gemeinsam produktiv verwendete `PagWorkspace` wird direkt mit einem
  Layoutanker gemessen: dessen Position/Größe bleibt mit/ohne Banner identisch.
- **Bestehender Locked-/Bootstrap-Probe auf UX1b erfolgreich**: neutrale reale
  Locked-Shell, kein geschützter Header, Touchziel und synthetisches EMPTY bei
  Font Scale 1/2 ohne automatische Initialisierung.

Frühe UI-Probeläufe wurden nicht als grün gezählt. Der Test wartet inzwischen
begrenzt auf asynchrone Accessibility-Transitions. Accessibility-Bounds verdeckter
Texte sind kein Layout-Reflow-Nachweis; dafür wird `onGloballyPositioned` benutzt.
Die virtuelle Compose-Accessibility-Node stellt KeyboardOptions nicht als
EditorInfo-Capitalization bereit; Hints werden über Mappingtests und Code-Audit
geprüft, Eingabefokus separat auf dem Gerät. Diese Testkorrekturen umgehen keine
Produktanforderung und benötigten keine Security-Änderung.

Die 24 P3b-Storagefälle aus dem aktuellen UX1a-Nachweis werden nicht als erneut
ausgeführt behauptet; P3b-/Crypto-/Storagecode blieb im UX1b-Slice unverändert.
Leadership-ViewModel-, Crypto- und Sessionregressionen gehören zur vollständigen
aktuellen Unit-Suite. Der reale Credential-/Foreground-/Device-Lock-/TalkBack-
Smoke bleibt menschliche Prüfung.

Lokale Nachweise (nicht versioniert): `.prototype-cache/ux1b-checks.log`,
`ux1b-probe-build.log`, `ux1b-storage-galaxy.log`, `ux1b-shell-galaxy.log`,
`ux1b-locked-galaxy.log`; Unit-XMLs und Lint in den Modul-Buildverzeichnissen.
`git diff --check` erfolgreich, HEAD weiterhin `6277059`, kein Commit/Push.

Neue Port-/ViewModel-Tests
prüfen verzögerte Reads in beiden Reihenfolgen, echte leere Snapshots, Fehler,
Generation/Action/Requestwechsel, Revoke zwischen Reads, Refresh ohne Leeren,
Role-/Template-/Taskfeedback, synchronen Revoke während Refresh, Persondetail-
Inventory, Progress und KeyboardHints. Bestehende 15 P5a-TaskModel-Tests bleiben.

Der Geräteprobe arbeitet mit synthetischen UI-Ports: er beweist Layout/Tree-
Eigenschaften, keine echte Credential-/Keystore-Freigabe. Storage-Instrumentation
prüft davon getrennt echte kurze Repository-/Transaktions-/Revoke-Verträge.
OEM-Tastatur-Großbuchstaben, tatsächliches TalkBack-Sprechen und bewusster realer
Unlock benötigen menschlichen Smoke. Keine Systemschrift-/Credentialänderung,
kein pm clear, kein Uninstall. Installationen ausschließlich install -r.

UX2/UX3 offen: neue Workspace-Navigation, fachliche Lagebild-/Preparation-
Neugestaltung, Date-/Time-Picker-Überarbeitung, bessere Inline-Validierung und
Formularabschluss/Next/Done, weitere Ergebnisrückmeldungen, Completed-Rückblick.
UX1-S bleibt separat: keine Sitzungsverlängerung oder erneute Bestätigung.
SessionPolicy, SessionCoordinator (gegenüber UX1a), Runtime, SessionDbKeyOwner,
Storage-Gates, P3b und Crypto bleiben unverändert. P5a-guardedInteraction bleibt
unverändert; TaskModel erhält nur den gebündelten Board-/Refresh- und Feedbackanschluss.

## Kurzer Galaxy-Smoke

1. Bewusst entsperren: READY zeigt sofort Timer und umrandetes Sperren; Body lädt
   ruhig, dann erscheinen Personen/Tasks/Funktionen/Lage gemeinsam.
2. Schrift groß: Header und deutsche Controls bedienen, rechteckige 4-dp-Formen,
   Textwachstum und Umbrechen prüfen; wichtige Aktionen wirken als Controls.
3. Person, eigene Rolle, Vorlagenrolle und Task anlegen: passende Großschreibung
   und einmaliger Eintrittsfokus; gespeicherte Schreibweise bleibt erhalten.
4. Banner schwebt ohne Inhaltssprung; Balken läuft ab; Rollen-/Taskfeedback lesen.
5. Refresh nach Mutation: kein Leeren/Nullzählen oder abschnittsweises Nachladen.
6. Während Load/Banner manuell sperren bzw. App verlassen: Countdown, Banner,
   Fortschritt und operativer Inhalt verschwinden. TalkBack unterbricht nicht
   jede Sekunde und sagt keine Fortschrittsprozente an.

## Dateiscope

UX1b bearbeitet 19 Dateien. Pfade unter `app/src/main/kotlin/org/pag/prototype/product/`:

- `PagShell.kt`: Workspace-/Bannerhost, 4-dp-Controls, Textsemantik und Progress.
- `ProductInputs.kt`: gemeinsame Controlform, KeyboardHints und Eintrittsfokus.
- `ProductScreen.kt`: stabiler Header, Body-Reveal und Buttonhierarchie.
- `ProductViewModel.kt`: gebündelte Snapshot-/Refresh-/Persondetail-Publication.
- `OperationsModel.kt`, `TaskModel.kt`: Snapshot-/Refresh-Anschluss und Erfolgsfeedback.
- `OperationsScreen.kt`, `TaskScreen.kt`, `SensitiveScreen.kt`: Controls und Feldhints.

Tests und Testmanifest:

- `app/src/test/kotlin/org/pag/prototype/product/Ux1bSnapshotTest.kt` (neu).
- `app/src/test/kotlin/org/pag/prototype/product/PagShellTest.kt`.
- `app/src/test/kotlin/org/pag/prototype/product/PersonViewModelTest.kt`.
- `app/src/test/kotlin/org/pag/prototype/product/LeadershipViewModelTest.kt`.
- `app/src/androidTest/kotlin/org/pag/prototype/Ux1bShellProbe.kt` (neu).
- `app/src/androidTest/kotlin/org/pag/prototype/Ux1aShellProbe.kt` (Countdown-Textmatcher).
- `app/src/androidTest/AndroidManifest.xml` (zusätzlicher Test-Runner).

Dokumentation: `docs/ux1b-polish.md` (neu), `docs/ux1a-shell.md`, `docs/ux-roadmap.md`.

Gemeinsamer uncommittierter UX1a+UX1b-Stand gegenüber UX0: **24 Dateien**,
17 modifiziert und 7 untracked/neue Dateien. Die fünf übrigen UX1a-Dateien sind
im UX1b-Slice nicht verändert worden: `app/build.gradle.kts`,
`domain/src/main/kotlin/org/pag/domain/Session.kt`,
`data/src/main/kotlin/org/pag/data/SessionCoordinator.kt`,
`data/src/test/kotlin/org/pag/data/SessionCoordinatorTest.kt`, `docs/security.md`.
Keine Build-/Cache-/IDE-/Secret-Dateien im Git-Scope. Kein Commit, kein Push.

Klassifikation: **A) UX1b AUTOMATISCH GRÜN – AUDIT-BEREIT**.

## Nachfolgende Minor-Korrektur

Die beiden Read-only-Auditbefunde sind gezielt korrigiert:
`dismissEditForPersonDetail` verwirft das gebundene Lageformular ohne Overview-
Refresh; der direkte Persondetailpfad liest nur Person und Inventory-Metadaten.
Normaler Cancel und bestätigter Save behalten ihren Workspace-Refresh. Bei der
Rückkehr in ein weiterhin ausgewähltes Persondetail starten sie zusätzlich einen
frischen Inventory-Request. `sensitiveInventoryLoading` kennzeichnet tatsächliche
Requests; null ohne Request behauptet keinen laufenden Load.

Publication prüft READY, Generation, Action, Person sowie Request-/Navigation-ID.
Ältere Resultate beenden auch keinen neueren Load. Metadaten umfassen nur den
bestehenden SensitiveState und die vorhandenen Bereichstypen. PARTIAL_OR_INVALID
bleibt fail closed. `clearSensitive` behält sämtliche Revoke-/Payload-/Editor-
Cleanup-Schritte; kein Grant, Klartext oder Passphrase wird bei Rückkehr restauriert.
Sensitive Inhalte benötigen weiterhin den bisherigen expliziten Step-up.

19 neue `Ux1bNavigationTest`-Regressionen unterscheiden Port-Aufrufe und prüfen
Cancel/Save, fehlgeschlagene Writes, Revoke, Generation/Action/Person/Request,
Partial/Error/Uninitialized und einen vorher entsperrten, anschließend widerrufenen
Grant. Vollständige aktuelle Unit-Suite: **465 grün** (App 121, Data 340, Security 4).
Builds, Lint (0 Fehler/1 bestehender Hinweis), Architektur und Manifest grün.

Galaxy/API33: sieben vorhandene P3b-Storage-/Lifecyclefälle erneut grün:
60-s-Ablauf/Navigation-Revoke, Generation, verzögerte Publication, Partial-Setup,
Alias/Ciphertext ohne Envelope, Root-Metadata-Tampering und isolierter Keystore.
Der erweiterte synthetische UX1b-Probe bestätigt keine zusätzlichen Boardreads,
endendes Inventory-Loading und Controls nach Cancel/Save sowie Partial/Lock.
Nachweise: `.prototype-cache/ux1b-minor-checks.log`, `ux1b-minor-p3b-galaxy.log`,
`ux1b-minor-shell-galaxy.log`, `ux1b-minor-locked-galaxy.log`.

Fix-Scope: OperationsModel/Screen, ProductViewModel, SensitiveScreen,
Ux1bShellProbe, neuer Ux1bNavigationTest und diese kurze Ergänzung.
Keine Storage-/Crypto-/Sessionänderung, kein Commit/Push.
