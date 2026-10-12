# UX1-S — konservativer Session-Neustart

Stand: 12.10.2026. Baseline: sauberer `main`, `358899c`.
Kein Commit oder Push. Grundlagen: [Security](security.md),
[UX-Philosophie](ux-philosophy.md), [Roadmap](ux-roadmap.md),
[UX1a](ux1a-shell.md), [UX1b](ux1b-polish.md), [Produkt](../PRODUCT.md).

## Produktentscheidung und Threat Model

Die ursprüngliche Idee „Neu bestätigen“ mit weiterlaufender READY-Session wurde
vor Änderungen gestoppt. Der bestehende Authpfad startet ausschließlich aus LOCKED;
READY benötigt resumed, Fensterfokus und ein nicht gesperrtes Gerät. Ein Credential-
Dialog kann diesen Zustand beenden. READY-Erhalt hätte neue Foreground-/Auth-
Koordination verlangt. Diese wird ausdrücklich nicht eingeführt.

Ein Angreifer mit Zugriff auf ein bereits entsperrtes Gerät darf das absolute
Expositionsfenster nicht durch Bedienung offen halten. Die neue Aktion verlangt
ausdrückliche frische Systemauthentisierung. Ohne Gerätecredential erhält der
Angreifer keine neue Sitzung. Kenntnis des Gerätecredentials und kompromittiertes
OS bleiben Grenzen des bestehenden Threat Models. Keine eigene Credentiallogik,
keine Biometrie-Erweiterung, keine Ermittlung-/Tarnfunktion.

## Sichtbarkeit und Tap

Nur bei READY und `0 < absolute Restzeit <= 30000 ms` erscheint neben Countdown
und Sperren der umrandete Secondary-Control **„Sitzung neu starten“**.
Die Schwelle ist UX, keine Deadline. Außerhalb READY oder bei null/abgelaufenem
Budget wird keine Neustartaktion angeboten. Kein automatischer Prompt, Banner,
Timeralarm oder Confirm-Dialog. 4-dp-Form, mindestens 48-dp-Touchziel, FlowRow-
Umbruch bei großer Schrift. Accessibility erläutert den bewussten Editorverlust;
der Countdown hat weiterhin keine sekündliche LiveRegion.

Beim Tap prüft das ViewModel die aktuelle Session und Restzeit autoritativ,
registriert normale Interaktion und prüft danach Zustand/Generation erneut.
Ein synchroner Revoke oder verspäteter Tap darf keinen Unlock anstoßen.
Dann folgen synchron bestehendes `lock()` und ausschließlich aus LOCKED das
bestehende `unlock()`/`ProductRuntime.start(false)`.

```text
READY G1 → LOCKED (Revoke/Cleanup) → AUTHENTICATING → MAINTENANCE → READY G2
```

Keine neue Deadline durch Tap, kein READY-Erhalt während Prompt und keine neue
Auth-State-Machine. Der zweite Tap sieht keinen zulässigen READY-Zustand;
der normale Coordinator-Guard verhindert zusätzlich parallele Authrequests.

## Auth, Erfolg, Cancel und Fehler

Unverändert: `ProductCredentialAuthentication`, DEVICE_CREDENTIAL-only,
frisch vorbereiteter operationsgebundener RSA-OAEP-Cipher/CryptoObject,
Prüfung des AuthenticationType und des zugehörigen Ciphers. PaG verarbeitet
keine Credential-Daten. Keine neue Permission, Komponente oder Dependency.

Frische erfolgreiche Authentisierung verankert die neue absolute 180000-ms-
Deadline in elapsedRealtime. Der bestehende normale Maintenancepfad einschließlich
Key-Unwrap, Inventory-/Datasource-/Konsistenzprüfungen bleibt vollständig erhalten;
kein Quick-Unlock. Maintenancezeit zählt gegen die neue absolute Deadline.
Die 30000-ms-READY-Inaktivitätsbasis entsteht wie bisher beim Eintritt in READY.
Die neue Generation ist von G1 verschieden; G1 wird niemals reaktiviert.

Cancel bleibt LOCKED. Authfehler folgen dem vorhandenen fail-closed Verhalten:
technische Auth-/Schlüsselfehler führen zu BLOCKED. Maintenancefehler ebenfalls
keine READY-Freigabe. Keine alten Daten, Restzeit, Editoren oder ungefilterten
Systemfehlermeldungen. Kein automatischer Retry oder Prompt-Loop.

## Cleanup, Routing und P3b

Kein Continuation-Descriptor und keine Übernahme alter Navigation aus RAM.
Keine Persistenz in DB, Preferences, DataStore, SavedState oder rememberSaveable.
Nach neuer READY-Session werden Daten frisch geladen. Die heutige Post-Unlock-
Logik zeigt die Aktionsliste. Automatisches Routing bei genau einer ACTIVE-Aktion
in einen Lagebild-Hub bleibt späterer UX3-Umfang.

Ungespeicherte Aktions-, Personen-, Rollen-, Task-, Lage- und Sensitive-Editoren
werden wie bei normalem Lock verworfen; keine Passphrase-/Payloadrestoration.
Bestehendes FormInput-/Screen-/Lifecycle-Cleanup entfernt Fokus/Keyboard.
P3b-Grants und sensitive Payload werden widerrufen. Normale Geräteauthentisierung
erteilt keinen Leadership-Step-up; erneuter Zugriff benötigt den bestehenden
Passphrasepfad. DB-Key-Owner und alte Operationen unterliegen unverändert Revoke.
Referenzbereinigung behauptet keine vollständige physische RAM-Überschreibung.

## Foreground und Storage

`MainActivity`, Authadapter und Runtime bleiben unverändert. Der finale
Key-Zulassungspfad ergänzt den Coordinator; siehe den TOCTOU-Nachtrag unten.
Vor Prompt ist die alte Session bereits invalidiert. Die vorhandene enge
AUTHENTICATING-Koordination für Pause/Fokusverlust wird normal genutzt;
keine Ausnahme für READY. onStop/onUserLeaveHint, Screen-Off, Device-Lock und
unzulässige Umgebung behalten den normalen fail-closed Vertrag. Verspätete
Ergebnisse widerrufener Authversuche werden verworfen.

Storage prüft weiterhin vor Operation, Schritt, Commit und Publication die
Session-/Generation-/Zeit-/Foregroundbindung. Native Arbeit kann nach Revoke
physisch enden; es wird kein rückwirkendes Undo eines bereits zugelassenen
Commits behauptet. Alte Resultate dürfen nach Generation-Revoke nicht publizieren.
Countdown-, Banner- und Loadjobs haben keine eigene Sessionautorität; bestehende
Generation-/Request-Guards und Cleanup bleiben maßgeblich.

## Automatische Prüfungen

Neue `SessionRestartTest`-Fälle verbinden ViewModel, echten Coordinator und
monotone Testzeit mit verzögerten Auth-/Maintenanceports: Sichtbarkeit, Tap vor
Auth, alter Storagecontext/Commit/Publication, Doppeltap, Cancel, Fehler, Erfolg
mit Maintenanceverbrauch, frischer Load ohne Continuation, doppelter Callback,
verspäteter Tap nach Ablauf und Foreground-/Environment-Revoke.
Zusätzliche UX1b-Regressionen prüfen Taskdraft/stale Writebanner, Atomic Reveal,
Banner-Cleanup und Person-/Lage-Navigation. Ein Leadership-ViewModel-Test prüft
Grant-Revoke und geschlossene Sensitive Area in neuer Generation.

Der erweiterte bestehende `Ux1bShellProbe` misst die reale Compose-Shell bei
Font Scale 1 und 2: Schwelle, Button-Semantik, Touchziel, Umbruch ohne Überlappung,
tatsächlicher Accessibility-Click, Lock/Cleanup vor einem synthetischen Authrequest,
Doppeltap, nicht-READY-Ausschluss und frischer Countdown. Synthetische Ports
beweisen keine reale Credential-/Keystore-Authentisierung.

Vollständige Offline-Prüfung erfolgreich:

```text
ANDROID_USER_HOME="$PWD/.prototype-cache/android" ./gradlew \
  :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest \
  :domain:test :app:assembleDebug :app:assembleDebugAndroidTest \
  :data:assembleDebugAndroidTest :app:lintDebug verifyArchitecture \
  :app:verifyPrototypeManifest --offline
```

**478 Unit-Tests grün**, null Failures/Errors/Skips: App 134, Data 340,
Security 4; Domain besitzt keine eigenen Tests. Gegenüber der Baseline 13 neue
Regressionen (9 SessionRestart, 3 UX1b-Cleanup, 1 Leadership). Bestehende
SessionCoordinator-/TimedSession-/Foreground-/Generation-, P5a-TaskModel-,
UX1-Loading/Banner/Countdown- und P3b-Regressionen sind enthalten.
App und beide AndroidTest-Builds, Architektur und Manifest grün. Lint:
null Fehler, nur der vorhandene AndroidGradlePluginVersion-Hinweis.

Galaxy SM-M526BR/API33: erweiterter synthetischer Shell-Probe bei Font Scale 1/2
erfolgreich einschließlich tatsächlichem Neustart-Click und Lock vor Auth.
Die bestehenden UX1b-Banner-/Fokus-/Navigationprüfungen sind ebenfalls grün.
Aktuelle APKs ausschließlich per `install -r` aktualisiert. **17 isolierte
Storage-/P3b-Geräteregressionen grün** in 210 Sekunden: vollständige TaskStorage-
Klasse (12) einschließlich Commit-Revoke/Generation-Publication und fünf
Leadership-Fälle (Grant-Ablauf/Navigation, normale Revocation/stale Generation,
Commit-Rollback, verspätete Publication und echter nicht exportierbarer
Keystore-Root). Kein realer Credential-Input in diesen Tests.

Merged Produktmanifest: ausschließlich bestehendes USE_BIOMETRIC und interne
signature-basierte DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, ein exportierter
Launcher, keine neue Komponente und kein INTERNET. Keine Dependencyänderung.
Kein invasiver Gerätezugriff, keine Systemschrift-/Credentialänderung,
kein pm clear und kein Uninstall. Der echte Systemcredential-/Device-Lock-/
TalkBack-Smoke bleibt menschliche Prüfung und wird nicht als automatisiert
bestanden behauptet. Lokale Logs `.prototype-cache/ux1s-checks.log`,
`ux1s-shell-galaxy.log`, `ux1s-storage-galaxy.log` sind nicht versioniert.

## Geänderte Dateien

Unter `app/src/main/kotlin/org/pag/prototype/product/`:

- `PagShell.kt`: rein visuelle positive READY-/30-s-Schwelle.
- `ProductScreen.kt`: Secondary-Control im bestehenden responsiven Header.
- `ProductViewModel.kt`: geprüfter Tap → bestehender Lock → normaler Unlock.

Tests: `app/src/test/kotlin/org/pag/prototype/product/SessionRestartTest.kt`
(neu), `LeadershipViewModelTest.kt`, `Ux1bSnapshotTest.kt` und
`app/src/androidTest/kotlin/org/pag/prototype/Ux1bShellProbe.kt`.
Dokumentation: diese Datei (neu), `docs/security.md`, `docs/ux-philosophy.md`
und `docs/ux-roadmap.md`. Insgesamt 11 Dateien; kein Storage-/Auth-/Lifecycle-
Implementierungsumbau, keine versionierten Build-/Cacheartefakte.

`git diff --check` erfolgreich. Git: neun modifizierte und zwei neue Dateien
auf `main`, HEAD weiterhin `358899c`; kein Commit/Push.
Klassifikation: **A) UX1-S SESSION-NEUSTART AUTOMATISCH GRÜN – SECURITY-AUDIT-BEREIT**.

## Vorgeschlagener manueller Galaxy-Smoke

Nur synthetische Daten; Credential ausschließlich selbst im Android-Dialog eingeben.

1. Mit echter Interaktion die 30-s-Inaktivität vermeiden und bis zur absoluten
   Restzeit <=30s warten: Neustart-Control erscheint, Countdown bleibt lesbar.
2. Ungespeicherten Editor öffnen, Sitzung neu starten: alter Inhalt und Countdown
   verschwinden sofort, normaler Systemcredential-Dialog erscheint.
3. Cancel: PaG bleibt LOCKED, keine alte Restzeit oder Editorrestoration.
4. Erneut entsperren und bewusst neu starten; Erfolg: Maintenance, frischer Load,
   neues Budget ungefähr 3:00 ab Auth, Aktionsliste statt alter Person/Task.
5. Vorher geöffneten Leadership-Bereich prüfen: Grant/Payload weg, erneuter
   Zugriff verlangt Leadership-Step-up. Keine Übernahme von Passphrasefeldern.
6. Schnell doppelt tippen: höchstens ein Credential-Dialog. Große Schrift und
   TalkBack: Controls umbrechen, Countdown spricht nicht jede Sekunde.
7. Home/Appwechsel und Device-Lock während Auth: normaler fail-closed Vertrag,
   kein spätes Ergebnis stellt alte Inhalte wieder her.

Keine automatischen Credentialänderungen, kein pm clear, kein Uninstall.

## HIGH-Nachprüfung: P3b-/Coordinator-Monitorzyklus

Die oben dokumentierte erste automatische UX1-S-Prüfung erfasste die reale
Monitorverkettung nicht. Ihre damalige Audit-Bereitschaft ersetzt keinen Security-
Audit. Dieser Nachtrag behandelt ausschließlich den anschließend gefundenen
HIGH-Befund; der vorhandene UX1-S-Working-Tree bleibt Grundlage.

### Nachgewiesene Ursache vor dem Fix

`SessionCoordinator` verwendet seinen Instanzmonitor für Zustands-/Generation-
Checks, `StorageContext.check/publish` und synchrone Observer. `lock()` und
`foreground(false)` rufen unter diesem Monitor `invalidate()` auf: erst neue
Generation/LOCKED, dann Cancel/Release, dann `publish()` mit Observern.

Die tatsächlichen Gegenrichtungen waren:

```text
A: Coordinator.lock / foreground(false) [Coordinator-Monitor]
   -> invalidate -> publish
   -> ProductRuntime.leadershipLifetime -> ProductLeadershipRepository.revoke
      ODER ProductViewModel.session-Observer -> clearPersons -> clearSensitive
   -> LeadershipGrant.cancel [Grant-Monitor]

B: LeadershipGrant.valid [Grant-Monitor]
   -> inLifetime -> echter Coordinator-StorageContext.check(STEP)
   -> valid / enforceValidity [Coordinator-Monitor]

Zusätzlich B: consume [Grant-Monitor] -> valid -> dieselbe Gegenrichtung.
```

Repository-Grantprüfungen in `save`, `deleteArea`, den gebundenen Context-Gates
und der Publication sowie `ProductViewModel.checkSensitiveExpiry` können B
betreten. Bei A hält Thread A den Coordinator und wartet auf den Grant; Thread B
hält den Grant und wartet auf den Coordinator. Das Repository selbst besitzt
keinen zusätzlichen Grant-Verwaltungsmonitor: `active`/`request` sind atomar;
`revoke()` nimmt den Grant-Monitor über `cancel()`.

Vor jeder Produktionsänderung wurde
`LeadershipSessionConcurrencyTest.lockWhileRealGrantChecksRealGeneration`
ausgeführt. Ein Thread hält den echten Coordinator; der zweite prüft einen
echten Grant mit dem unveränderten `acquire(READY)`-Context. ThreadMXBean belegt
BLOCKED auf genau dem Coordinator-Monitor mit dem ersten Thread als Eigentümer.
Erst dann startet dieser `lock()` einschließlich synchronem Cleanup-Observer.
Vor Fix: **kontrollierter Failure nach 5,045 s**, `Synchronous coordinator/grant
cleanup must terminate`; Build fehlgeschlagen. Kein Sleep-Zufallstest und kein
synthetischer StorageContext. Daemon-Threads verhindern einen unbegrenzten
JUnit-Prozesshänger. Lokale Evidenz:
`.prototype-cache/ux1s-deadlock-before-evidence.log`.

### Erster Deadlock-Fix und seine später nachgewiesene Race-Lücke

Nur `LeadershipGrant` wird produktiv geändert. `valid()` prüft lokalen Widerruf
kurz unter seinem Monitor, führt danach `inLifetime()` **ohne Grant-Monitor** aus
und prüft anschließend unter seinem Monitor erneut `bytes != null`.
`consume()` ruft `valid()` außerhalb seines Monitors auf und prüft atomar mit der
Schlüsselkopie nochmals `bytes != null`. `cancel()` überschreibt die eigene
Schlüsselbytes und setzt die Referenz null; kein externer Call unter dem Monitor.
Work, abschließende Gültigkeitsprüfung und `finally`-Wipe der Lease bleiben
außerhalb des Monitors.

Dies schützt ausschließlich gegen einen bereits ausgeführten lokalen Wipe.
Der anschließende Re-Audit zeigte: Bei invalidierter Generation und noch
wartendem `cancel()` können die Bytes weiterhin vorhanden sein; die damalige
Kopie-/Work-Zulassung war daher unsicher. Ein gezielter Latch-Test pausiert nach erfolgreichem echten
Context-Check in der Uhrabfrage: Lock/Cleanup muss währenddessen abschließen;
der Consumer darf anschließend keine Schlüsselkopie/Work erhalten. Ein weiterer
Test pausiert bereits begonnene Consumer-Arbeit: Lock wartet nicht auf Work;
der Consumer wird anschließend abgewiesen und seine Lease im `finally` gelöscht.

`valid()` ist weiterhin keine atomare Autorisierung einer gesamten Operation.
Die unveränderten Repository-/Storage-Gates prüfen vor jedem weiteren Schritt,
Commit und Publication die tatsächliche Generation/Session. Ein bereits vor
Revoke irreversibel zugelassener nativer Commit darf physisch enden; Recovery
bleibt maßgeblich. Die Storage-Gates verweigern weitere G1-Zulassung/Publication. Dies war kein
ausreichender Nachweis für die eigenständige Grant-Key-Zulassung.
Keine Änderung an DB-Transaktionen oder Storage-Gates.

### Historischer Monitor-Audit des ersten Fixes

| Monitor/Verwaltung | Erwerber und darunter gehaltene Calls | Security-Kante nach Fix |
| --- | --- | --- |
| Coordinator-Instanz | Synchronisierte Sessionmethoden, Auth-/Maintenancecallbacks, Contextcheck und -publication; `invalidate` ruft Cancel/Release und Observer synchron; Publication ruft Ergebniscallback synchron | Coordinator -> Grant.cancel; Coordinator -> DB-Key-Owner.invalidate; Coordinator -> Operation.release bleiben |
| Grant-Instanz | `valid`: ausschließlich Bytes-Präsenz; `consume`: ausschließlich Bytes-Präsenz/Kopie; `cancel`: ausschließlich Wipe/null | Keine ausgehende Coordinator-/Owner-/Operation-Kante, keine externe Uhr-/Context-/Work-/Observerfunktion |
| Repository-Grantverwaltung | AtomicReference/AtomicLong und ConcurrentHashMap; `revoke`: Request erhöhen, aktiven Grant entfernen/cancel, pending Cleanups ausführen | Kein eigener verschachtelter Security-Monitor; pending Cleanups überschreiben nur die übergebenen Passphrase-/Confirmationarrays |
| SessionDbKeyOwner-Instanz | `invalidate`: Wipe/null; `withDbKey`: nur Leasekopie unter Monitor | Contextcheck/Consumer/finaler Check außerhalb; keine Owner -> Coordinator-Kante |
| ProductDbEnvelope-Operation | `release`: Cipherreferenz null; Leaseentnahme unter eigenem Monitor | Autorisierungscheck und native Keyarbeit außerhalb; keine Operation -> Coordinator-Kante |
| Room-/SQLCipher-Operationen | Native DB-/Transaktionssynchronisation und Contextchecks auf dem Storage-Worker; ActionDatabaseAccess hat keinen eigenen Monitor, B4RoomLifecycle nutzt atomaren Zustand | Storage -> Coordinator bleibt; Lock-Observer schließen/warten auf keine Room-Verbindung oder Worker-Jobs, daher keine Coordinator -> native Storage-Gegenkante im Cleanup |
| ProductViewModel / TaskModel / OperationsModel | Synchrone Statebereinigung und Requestinvalidierung; keine eigenen Security-Monitore, kein Join/Await auf UI-Jobs | VM-Cleanup -> Grant.cancel als kurze lokale Wipeoperation; keine rückwärtige Grant-Kante |

Für diese untersuchten Security-Monitore gibt es keine zyklische Kante mehr.
Dies ist kein universelles Versprechen für beliebige künftige Observer/Adapter:
unter dem Grant-/Owner-/Operation-Monitor dürfen keine externen Session-Calls
hinzukommen. Ein direkter Test prüft mit dem echten Coordinator-Environmentcallback,
dass `valid()` und `consume()` ihn niemals bei gehaltenem Grant-Monitor aufrufen.

Der Coordinator und seine Observersemantik sind **unverändert**: Generation wird
vor Cleanup invalidiert; Cleanup bleibt synchron unter demselben Monitor und
abgeschlossen, bevor `lock()` zurückkehrt oder eine neue Auth-/G2-Arbeit beginnen
kann. Snapshot-/Observerreihenfolge, LOCKED-/BLOCKED-Notifications und
reentrante Publication-Guards bleiben bestehen. Keine zweiphasige State-Machine,
kein fire-and-forget, kein Cleanup-Timeout im Produkt, keine übersprungene
Bereinigung. LOCKED-Rückkehr ist weiterhin die Grenze für abgeschlossenen
synchronen Cleanup; UI-Observer sehen wie bisher den bereits invalidierten
Snapshot während der Bereinigung.

Task-/Operations-/Person-/Sensitive-Editoren, operative Workspace-/Payload-
Referenzen, Banner und Countdown werden über die bestehenden synchronen
ViewModel-Clears entfernt. Compose-Effects sind State-/Generation-/Feedback-
gebunden; Cleanup wartet nicht auf ihre Jobs. Die Tests behaupten weder eine
vollständige physische Klartext-RAM-Überschreibung noch ein Abbrechen bereits
laufender nativer Arbeit. DB-Key-Owner- und Operation-Revoke-Verträge bleiben
bestehen. Grants bleiben action/person/area/generation-gebunden, maximal 60 s,
ohne Persistenz oder Verlängerung; Navigation, Lock, Background und
Generationwechsel widerrufen sie unverändert.

### Echte Konkurrenzregressionen

`LeadershipSessionConcurrencyTest` enthält elf neue JVM-Fälle:
Grantprüfung während Lock, Consumer-Leaseprüfung während Lock, Grantablauf,
Bereichsschließen, Generationwechsel mit neuer READY-Generation, Cleanup vor
Neustartauth, Background-Lock, Revoke bei laufendem Consumer, Revoke zwischen
Contextcheck und lokaler Kopie, gehaltenes Grant-Cancel während Lock sowie die
direkte Monitor-Invariante. Latches, kontrollierter Monitorbesitz und zeitlich begrenzte
ThreadMXBean-Prüfung ersetzen Sleeps; jeder konkurrierende Fall hat ein kurzes
Testlimit. Alte Contexts müssen alle AccessPoints/Publication abweisen;
Cleanupfälle prüfen zusätzlich Wipe/null des tatsächlichen Grant-Schlüssels.

`SessionRestartTest.realGrantConcurrentWithRestartTapAndProductObserverCleanup`
verbindet das echte ProductViewModel mit echtem Coordinator, Produktions-Grant
und Restzeit 20 s. Test-only Reflection erhält die interne Sichtbarkeit des
Grants; lediglich der Repository-Ladeport ist eine Fixture. Die echte
`restartSession()`-Methode und ihr synchroner Product-Observer müssen terminieren,
Grant/Payload/Area/Person/Navigation/Countdown/Banner entfernen und erst danach
den normalen Authrequest starten. Drei weitere ProductViewModel-Fälle prüfen
normalen Lock, Background-Lock und die echte `closeSensitive()`-Methode während
Lock. Keine künstliche Grant-/Context-Synchronisation.

`LeadershipStorageTest.realCoordinatorAndRepositoryConcurrentLockAndBackgroundCleanup`
öffnet auf dem Gerät über den echten ProductLeadershipRepository einen realen
P3b-Grant aus der isolierten synthetischen SQLCipher-Fixture. Der echte Coordinator
liefert Context/Generation; der synchrone Observer ruft den echten Repository-
Revoke und DB-Key-Owner-Cleanup auf. Die kontrollierte Konkurrenz wird für
expliziten Lock und Background-Lock wiederholt; Grant-Wipe, Owner-Revoke und
alte Commit-/Publication-Ablehnung werden geprüft. Der Test benötigt keine
DEVICE_CREDENTIAL-Eingabe und verändert keine Produktdaten oder Credentials.

### Historische automatische Prüfung des ersten HIGH-Fixes

Die vollständige Offline-Unit-Suite ist grün: **493 Tests**, null Failures,
Errors oder Skips (App 138, Data 351, Security 4; Domain ohne eigene Tests).
Gegenüber dem UX1-S-Working-Tree: elf neue Coordinator-/Grant-JVM-Tests und
vier echte ProductViewModel-/Coordinator-/Grant-Konkurrenztests. Enthalten sind
SessionCoordinator-/TimedSession-, P3b-/LeadershipViewModel-, UX1-S-,
P5a-TaskModel-, Atomic-Reveal-/Banner-/Countdown- und Storage-Revoke-/Commit-/
Publication-Regressionen.

App-Build, App-/Data-AndroidTest-Builds, `:app:lintDebug`, `verifyArchitecture`
und `:app:verifyPrototypeManifest` erfolgreich. Lint: keine Fehler; ausschließlich
der vorhandene AndroidGradlePluginVersion-Hinweis. Merged Produktmanifest:
unverändert USE_BIOMETRIC und interne signature-basierte Receiver-Permission,
ein exportierter Launcher; keine neue Permission/Komponente, kein INTERNET.
Keine neue Dependency und keine Netzwerkfunktion.

Galaxy SM-M526BR/API33: vollständige `LeadershipStorageTest` (25 inklusive neuem
echten Coordinator-/Repositorytest) und `TaskStorageTest` (12) bestanden:
**37 Tests in 540,152 Sekunden**, null Fehler. Isolierte synthetische Fixtures,
keine Credential-Eingabe. APKs ausschließlich per `install -r` aktualisiert;
kein pm clear, kein Uninstall und keine Credential-/Systemschriftänderung.

Für diesen HIGH-Fix wurden ausschließlich sechs Dateien zusätzlich bearbeitet:

- `data/src/main/kotlin/org/pag/data/product/LeadershipGrant.kt`
- `data/src/test/kotlin/org/pag/data/product/LeadershipSessionConcurrencyTest.kt` (neu)
- `data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/SessionRestartTest.kt` (UX1-S-Datei erweitert)
- `docs/ux1s-session-restart.md`
- `docs/security.md`

Alle übrigen UX1-S-Änderungen bleiben Grundlage. `SessionCoordinator`,
Authadapter, Runtime, Manifest und Dependencies sind durch diesen Fix nicht
geändert. Lokale, unversionierte Prüfprotokolle:
`.prototype-cache/ux1s-deadlock-checks.log`, `ux1s-deadlock-before-evidence.log`,
`ux1s-deadlock-galaxy-storage.log`, `ux1s-deadlock-galaxy-session.log`.

Zusätzlich Galaxy/API33: fünf fokussierte bestehende `B16IntegrationTest`-Fälle
(frische Generation/Reopen, Generationwechsel beim Open, Foregroundverlust nach
Unwrap, alter DB-Handle und Deadline vor Commit) sowie die beiden bestehenden
`B4SafetyTest`-Matrizen `homeRevokesEveryImportAndDeleteTransition` und
`deviceLockRevokesEveryImportAndDeleteTransition` bestanden: **7 Tests in
408,444 Sekunden**. Jede B4-Matrix prüft alle 22 Import-/Delete-Grenzen mit
synthetischer Foreground-/Environment-Revoke-Injektion, ohne echte
Gerätecredential-/System-Lock-Änderung. Insgesamt **44 Instrumentationstests grün**.
Der DEVICE_CREDENTIAL-Handtest bleibt ausdrücklich erst nach erneutem Audit.

Beide vorhandenen Galaxy-Shell-Probes sind ebenfalls grün: `Ux1aShellProbe`
(LOCKED/EMPTY, keine geschützte Shell) und `Ux1bShellProbe` einschließlich UX1-S
(Neustart-Schwelle, tatsächlicher Buttonclick, Cleanup vor einmaligem
synthetischem Authrequest, neuer Countdown, Banner/Fokus/Navigation und Font
Scale 1/2). Logs: `.prototype-cache/ux1s-deadlock-galaxy-shell-a.log` und
`ux1s-deadlock-galaxy-shell-b.log`. Kein realer Credential-Input in diesen Probes.

Finale Diffprüfung einschließlich neuer Testdatei erfolgreich;
`git diff --check` ohne Befund. Working Tree: elf modifizierte und drei neue
Dateien einschließlich der unveränderten UX1-S-Grundlage. HEAD weiterhin
`358899c`; kein Commit, kein Push. Kein offener automatischer Prüffehler.

Diese historische Einstufung wurde durch den folgenden Security-Re-Audit
**C) SECURITY ISSUE – NICHT BEREIT** ersetzt. Die frühere grüne Suite deckte
die finalen Key-Zulassungsfenster nicht ab.

## Finale Key-Zulassung nach dem TOCTOU-Re-Audit

### Gefundene Lücke und Vor-Fix-Nachweis

Der erste Deadlock-Fix ließ zwischen erfolgreicher `valid()`-Momentaufnahme und
lokaler Kopie die Generation invalidieren, `cancel()` warten oder die Grantzeit
ablaufen. `bytes != null` allein erkannte die noch wartende Bereinigung nicht.
`work(lease)` erhielt dann nutzbares Material, bevor der spätere Reject/Wipe
wirkte. Ein späterer Storagecheck konnte diese Key-Nutzung nicht rückgängig machen.

Die drei RAM-Probes wurden vor Produktionsänderungen in
`LeadershipKeyAdmissionTest` überführt. Mit echtem Coordinator, unverändertem
acquire-Context und Produktions-Grant waren alle drei rot: Generation vor finaler
Admission, Cancel bereits wartend, Clock 59999 -> 60000 vor finaler Admission.
Jeweils `Invalid generation/expired grant reached key callback`; keine Timeouts
oder Sleep-Zufälle. Evidenz: `.prototype-cache/ux1s-key-admission-before-evidence.log`.

### Architekturentscheidung und Zulassungspunkt

Option A hätte die zahlreichen synchronisierten Coordinator-Pfade einschließlich
reentranten Auth-/Maintenance-/Publication-Callbacks in zwei Phasen aufteilen
müssen. Option B müsste nicht nur Generation, sondern Live-Environment, absolute
und Inaktivitätsdeadline mit verlässlicher Sichtbarkeit abbilden. Für diese
kleine Korrektur wird stattdessen die **gleichwertige Richtung C: autoritative
Zulassung unter festem Coordinator -> Grant-Lock-Order** verwendet.

Der Coordinator erhält einen einzigen zusätzlichen Context-Gatepfad:

```text
consume: frühe valid()-Momentaufnahme (keine Admission)
  -> StorageContext.admitKey(expiresAt) [Coordinator-Monitor]
     -> kurze Kopierfunktion [Grant-Monitor]
        -> bytes vorhanden / nicht lokal widerrufen?
        -> scoped authorize(): originale Coordinatorfelder, keine Monitoraufnahme
           READY + gleiche Generation + foregroundAllowed + Live-Environment
           eine Zeitprobe: now < absolut UND now < idle UND now < expiresAt
           Bound-Context: Requestticket weiterhin gültig
        -> nur bei vollständig erfolgreichem Gate Key kopieren
     -> Grant-Monitor freigeben
  -> Coordinator-Monitor freigeben
  -> work(Lease), anschließend bestehender Ergebnisguard, schließlich Lease-Wipe
```

**Linearization Point der Zulassung:** die erfolgreiche finale Gate-Momentaufnahme
innerhalb beider Monitore, unmittelbar vor der Kopie. Konkret besitzt der Gate-
Aufruf beim positiven Live-Environment-Snapshot bereits beide Monitore: Generation,
READY, foregroundAllowed und lokale Key-Präsenz sind gegen konkurrierende Session-
Invalidierung/lokales Cancel geschützt. Die anschließende gemeinsame monotone
Zeitprobe unter sämtlichen Deadlines bestätigt wegen Monotonie auch deren
Gültigkeit an diesem Snapshotpunkt. Der anschließende gebundene Requestcheck
muss erfolgreich sein; ein gewechseltes Ticket kann nicht den alten Context
reaktivieren. Nur der vollständig erfolgreiche Gate-Aufruf ist Admission.

Ein Thread darf zwischen früher Snapshotprüfung und finaler Admission beliebig
pausieren: der finale Guard liest die aktuellen originalen Sessionfelder und
Zeit innerhalb des geordneten Monitorbesitzes. Bereits invalidiertes G1 oder
now >= einer Deadline wird abgewiesen. Es gibt keine neue volatile/atomare
Schatten-Session. Die Produktionsclocks im Runtime-Coordinator und Leadership-
Repository basieren beide auf SystemClock.elapsedRealtime; `expiresAt` muss
in derselben monotonen Epoche liegen. Nicht zeitverlängernde Anzeige-/I/O-Events
und die vorhandene READY-Inaktivität bleiben unverändert.

Der Guard lebt ausschließlich während `admitKey` auf dessen Besitzerthread.
Er überprüft Threadidentität, Scope-Lebensdauer und Coordinator-Monitorbesitz;
escapte oder fremdthreadige Verwendung wird abgewiesen. Er erwirbt keinen
Security-Monitor und führt kein `enforceValidity`/Cleanup/Observer aus.
Unzulässige Sessionzustände werden vor der Kopierfunktion bzw. nach abgewiesener
Kopie wie bisher invalidiert, jeweils ohne Grant-Monitor. Die Context-Default-
Implementierung liefert null; unbekannte Provider dürfen keine Keykopie liefern.
Der tatsächliche Repository-Bound-Context delegiert die geordnete Zulassung und
prüft seinen bestehenden Requestguard innerhalb des finalen Guards.

### Revoke, Deadline und bereits zugelassene Arbeit

Session-Revoke linearisiert unverändert bei der neuen Generation/LOCKED- oder
BLOCKED-Zuweisung im Coordinator, vor Cancel/Release/Observer. Ein Thread, der
C bereits für Admission hält, beendet diese kurze Admission vor einem späteren
Session-Revoke. Hat Revoke C zuerst, kann der Consumer keine finale Admission
unter altem G1 erhalten, auch wenn er bei der frühen Snapshotprüfung noch den
Grant-Monitor vor dem wartenden Cancel erwirbt.

Lokales `Grant.cancel` linearisiert beim Wipe/null unter Grant. Repository-Revoke
invalidiert zuvor Requestticket/aktive Referenz atomar. Eine positive finale
Zulassung und lokales Cancel sind durch den Grant-Monitor geordnet. C-/G-Felder
werden nicht später zugunsten eines Consumers invalidiert.

**60 s = maximaler Zulassungszeitraum**, kein präemptives CPU-Abbruchversprechen.
Die finale gemeinsame Zeitprobe entscheidet die Deadlineprüfung. Zulassung bei
59999 ms darf Work beginnen/enden, auch wenn danach 60000 ms erreicht werden.
Bei 60000 ms oder mehr gibt es keine neue Admission/Key-Übergabe. Clockwechsel
zwischen früher Prüfung und finalem Gate zählt ausschließlich im finalen Gate.
Absolute Session- und READY-Inaktivitätsdeadline werden in demselben finalen
Zeitvergleich geprüft; Live-Environment bleibt die bestehende Runtime-Prüfung.

Nach Admission hält Work keinen C-/G-Monitor. Revoke wartet nicht auf eine
bereits zugelassene synchrone Consumer-/native Arbeit. Der bestehende finale
`valid()`-Ergebnisguard verhindert stale Rückgabe; er ist **nicht** der
Zulassungspunkt. Weitere Repository-/Storage-Gates bleiben verpflichtend:
kein neu zugelassener G1-Schritt, verhinderbare stale Commits werden abgewiesen,
keine stale Publication. Bereits irreversibel zugelassene native Commits dürfen
physisch enden, Recovery bleibt maßgeblich.

Rejected Admission kopiert keine Bytes. Die erfolgreiche Kopie wird noch im
Kopiercallback in der lokalen Leasevariable erfasst, innerhalb eines äußeren
try/finally. Selbst wenn ein Contextadapter nach der Kopie wirft, erreicht die
Kopie Work nicht und wird best-effort gewiped. Callbackexceptions und spätes
Ergebnis-Reject ebenfalls. Originalbytes bleiben unter Grant geschützt und
werden durch Cancel überschrieben. Keine Garantie vollständiger JVM-/JCA-/
OS-RAM-Überschreibung, keine neue Persistenz oder automatische Grant-Erneuerung.

### Finaler Monitor-/Guard-Audit

| Grenze | Schreiber / Leser | Halten / weitere Security-Monitore |
| --- | --- | --- |
| Coordinator | Unveränderte Session-/Lifecycle-/Auth-/Maintenancepfade schreiben State, Generation und Deadlines; Contextcheck/-publication und neuer Admissionguard lesen diese originalen Felder | C -> G für Admission und synchrones Cancel; C -> Owner/Operation für bestehenden Revoke; keine native Work/Consumer unter C |
| Grant | Konstruktion setzt Deadline/Bytes; cancel schreibt Wipe/null; valid liest Präsenz kurz, consume kopiert nach Gate | Erst C, dann G beim Kopieren. valid verlässt G vor Contextcheck. cancel nur lokale Bytes. Kein Erwerb von C unter allein gehaltenem G |
| Scoped Authorizer | Ownerthread setzt active für den kurzen Scope und entfernt es im finally; ausschließlich Ownerthread darf lesen | Threadcheck erfolgt vor active-Lesen; kein eigener Monitor, kein State-/Deadlinecache, keine externe spätere Autorisierung |
| Repository-Bindung | AtomicLong Request und AtomicReference Active werden von Revoke/Open geändert; bound guard liest das bestehende Ticket | Bound admit delegiert C und führt den Requestcheck im geschützten Guard aus; keine neue Grantverwaltungssperre |
| DB-Key-Owner/Operation | Bestehende Wipe-/Release-/Leasepfade | Eigene Monitore nur lokale Referenzen/Kopien; Contextchecks/native Work außerhalb. Keine Gegenkante zum C-Cleanup |
| UI / Room | Bestehende synchrone UI-Clears und Worker-Storagegates | Kein UI-Jobjoin im Lock, kein Roomclose/-await im C-Cleanup; Work nach Key-Admission außerhalb beider Monitore |

Es gilt der explizite Lock-Order **Coordinator vor Grant**, einschließlich
bereits gehaltenem Coordinator bei finalen autoritativen Lesefunktionen. Ein
künftiger Provider/Grant darf weder `admitKey` noch Contextchecks aus einem allein
gehaltenen Grant-Monitor aufrufen. Der konkrete Runtime-Environmentpredicate und
elapsedRealtime-Clock erwerben keinen weiteren PaG-Security-Monitor. Kein
universelles Versprechen für beliebige künftige Adapter/Observer.

Coordinator-Observer und Cleanup bleiben synchron unter C. Keine verlorenen
LOCKED-Notifications, keine neue Cleanup-in-progress-State-Machine und keine
zweiphasige Revoke-/Authkoordination. Normaler Lock und Background verwenden
weiter denselben Revoke. UX1-S ruft danach ausschließlich den normalen Authflow
auf; dessen MAINTENANCE/G2-Vertrag bleibt bestehen.

### Finale Regressionen

`LeadershipKeyAdmissionTest` enthält 17 Fälle. Die drei vor Fix roten Probes
sind grün; das frühere Cancel-waiting-Interleaving wird zusätzlich 128-mal mit
Latches und genauer ThreadMXBean-Monitoridentifikation wiederholt, ohne Sleeps.
Weitere Fälle: Admission vor Revoke, Revoke vor Admission, 59999 -> 60000 nach
Admission, unmittelbare 60000-Ablehnung, Callbackexception/Wipe, absolute und
idle Deadline vor Admission, Live-Environmentverlust, Zeitfortschritt beim Warten
auf den finalen Grant-Monitor, escaped/fremdthreadiger Guard, Adapterexception
nach Copy/Wipe und Provider ohne atomare Admission (fail closed).

Die elf bisherigen Monitor-/Cleanupregressionen bleiben bestehen. Die direkte
Invariante verlangt jetzt C-Besitz vor einem autoritativen Lesen unter G;
es handelt sich dabei ausdrücklich nicht um eine erneute C-Monitoraufnahme.
Die vier vorhandenen echten ProductViewModel-/Coordinator-/Grant-Fälle sichern
normalen Lock, Background, Sensitive-Schließen und UX1-S mit aktiver Freigabe.

Der bestehende echte Repository-Gerätetest prüft nun eine erfolgreiche tatsächliche
Key-Admission/Lease-Wipe und konkurrierendes consume gegen normalen/Background-
Lock über den produktiven gebundenen Context. Die bisherigen synthetischen
Rules-/Storage-Fixtures implementieren ihre Admission explizit; diese ersetzen
nicht die echten Coordinator-Konkurrenzbeweise.

### Finale automatische Prüfungen und Umfang

Vollständige Offline-Prüfung erfolgreich: **510 Unit-Tests**, null Failures,
Errors oder Skips (App 138, Data 368, Security 4; Domain ohne eigene Tests).
Enthalten sind alle 17 neuen Key-Zulassungsfälle einschließlich 128 Stress-
Wiederholungen, die bisherigen elf Monitorfälle, echte ViewModel-Neustart-/
Normal-/Backgroundfälle, SessionCoordinator/TimedSession, Leadership/P3b,
P5a TaskModel, UX1 Loading/Atomic Reveal/Banner/Countdown sowie Storage-Revoke/
Commit/Publication. Die drei Zulassungsprobes waren vor Änderung rot und sind
nach Änderung grün. Vollständiges Prüflog:
`.prototype-cache/ux1s-key-admission-checks.log`.

App-Build, beide AndroidTest-Builds, Lint, Architektur und Merged Manifest/
Permissions erfolgreich. Lint hat ausschließlich den vorhandenen AGP-
Versionshinweis. Keine neue Permission, Dependency, Produktkomponente oder
Netzwerkfunktion; weiterhin USE_BIOMETRIC plus interner signature-basierter
Receiver-Permission und ein exportierter Launcher, kein INTERNET.

Galaxy SM-M526BR/API33: **23 fokussierte Instrumentationstests in 225,271 s**
grün: sechs P3b-Fälle (Ablauf/Navigation, stale Generation, Commit-Rollback,
verspätete Publication, echter nicht exportierbarer Root und echter Coordinator-
Repository-Key-Consume gegen Normal-/Background-Lock), vollständige TaskStorage-
Klasse (12) und fünf B16-Session-/Generation-/Foreground-/Commitfälle.
Der echte Repositorytest wurde nach Ergänzung des eigenen synthetischen Alias-
Cleanups nochmals auf der finalen Test-APK geprüft: **1 Test in 10,185 s grün**.
Keine Bereinigung fremder/produktiver Aliasse; isolierte Testpackage-Fixtures.
Beide Shell-Probes sind grün: UX1a LOCKED/EMPTY sowie UX1b einschließlich UX1-S-
Click, Cleanup vor Auth, Banner/Countdown/Fokus/Navigation und Font Scale 1/2.
Keine echte DEVICE_CREDENTIAL-Eingabe; menschlicher Smoke erst nach Re-Audit.
Kein pm clear, Uninstall, Credential- oder Systemschriftwechsel; nur install -r.

Gerätelogs unter `.prototype-cache/`:
`ux1s-key-admission-galaxy.log`, `ux1s-key-admission-repository-final.log`,
`ux1s-key-admission-shell-a.log`, `ux1s-key-admission-shell-b.log`.

Dieser TOCTOU-Fix bearbeitet zehn Dateien zusätzlich zur bestehenden Grundlage:

- `domain/src/main/kotlin/org/pag/domain/StorageAccess.kt`: fail-closed Admission-Vertrag.
- `data/src/main/kotlin/org/pag/data/SessionCoordinator.kt`: kurze geordnete Admission und autoritativer finaler Guard.
- `data/src/main/kotlin/org/pag/data/product/LeadershipGrant.kt`: geschützte Kopie vor Work und äußerer Lease-Wipe.
- `data/src/main/kotlin/org/pag/data/product/ProductLeadershipRepository.kt`: Bound-Context-Delegation mit bestehendem Requestguard.
- `data/src/test/kotlin/org/pag/data/product/LeadershipKeyAdmissionTest.kt`: neue 17 Fälle.
- `data/src/test/kotlin/org/pag/data/product/LeadershipSessionConcurrencyTest.kt`: präzise C-vor-G-Invariante.
- `data/src/test/kotlin/org/pag/data/product/LeadershipRulesTest.kt`: explizite Admission der synthetischen Fixture.
- `data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt`: echte Key-Konkurrenz, Fixture-Admission und eigener Testalias-Cleanup.
- `docs/security.md` und diese Datei: Synchronisationsvertrag und korrigierte historische Bewertungen.

`git diff --check` und Whitespaceprüfung der neuen Testdatei ohne Befund.
Working Tree: 15 modifizierte, vier neue Dateien einschließlich UX1-S-/Deadlock-
Grundlage; `main`, HEAD weiterhin `358899c`. Kein Commit, kein Push.

**A) KEY-ZULASSUNGS-RACE BEHOBEN – SECURITY RE-AUDIT-BEREIT**
