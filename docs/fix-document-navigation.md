# Korrektur: Dokumentenbereich bleibt in Ladeansicht

## Ursache

`ProductViewModel.openDocuments()` löscht den bisherigen Workspace und öffnet
DocumentModel. ProductScreen entscheidet anhand von `documents.available`, ob der
Dokumentenbereich statt des bisherigen Aktionsworkspace dargestellt wird.
DocumentModels `opened` war jedoch ein normales Boolean-Feld. Compose beobachtete
seine Änderung nicht. Die bisherige Workspace-Unterkomposition konnte daher die
allgemeine Ladeansicht „Geschützter Speicher wird geprüft“ zeigen, während der
übergeordnete Screen nicht zum bereits geöffneten Dokumentenbereich wechselte.

## Minimale Korrektur

`opened` verwendet jetzt `mutableStateOf(false)`, analog zum bestehenden
MaterialModel. Öffnen und Schließen invalidieren die beobachtete Screenentscheidung.
Keine neue Navigation, Timer, automatische Wiederholungen oder Storage-Bypässe.
Keine Änderungen an DB, Schema, Recovery, Dateiverarbeitung, Auth, Fristen,
Permissions oder Manifest durch diese Korrektur. Vorhandene Arbeiten aus der
vorherigen Unlock-/Pfadkorrektur bleiben erhalten.

## Nachweis

Neuer Regressionstest am echten ProductViewModel mit Compose SnapshotStateObserver:
`DocumentWorkspaceTest.documentEntryAndExitInvalidateTheObservedScreenRoute`.
Er beobachtet gezielt die vom Screen gelesene Route und prüft deren Invalidierung
beim Öffnen und Schließen für PREPARATION, ACTIVE und COMPLETED.

- Gegen den alten Code reproduzierbar fehlgeschlagen: fehlende Invalidierung beim Öffnen.
- Nach Korrektur bestanden.
- Beide betroffenen Hosttestklassen: **20 Tests, 0 Failures/Errors**.
- `:app:assembleDebug` erfolgreich.
- Korrigierte APK per `adb install -r` erfolgreich als Update auf Samsung installiert.
  Keine Deinstallation, kein Datenreset oder Credentialwechsel.

```sh
./gradlew :app:testDebugUnitTest --tests '*DocumentWorkspaceTest.documentEntryAndExitInvalidateTheObservedScreenRoute' --offline
./gradlew :app:testDebugUnitTest --tests '*DocumentWorkspaceTest' --tests '*DocumentModelTest' :app:assembleDebug --offline
```

Der erste Befehl wurde vor der Korrektur ausgeführt (erwartet rot), der zweite
danach (grün). Hosttests sind kein Nachweis eines tatsächlich gerenderten Android-
Screens. Der Nutzer bestätigte nach Installation auf dem Samsung ausdrücklich:
„Dokumentenbereich öffnet sich“. Der reale Dokumenteneinstieg ist damit bestätigt.
Der bereits separat dokumentierte Android-`Os.link`-/Publicationfehler wird durch
diesen UI-Fix nicht behoben; keine vollständige Importfreigabe daraus ableiten.

Geändert in diesem Auftrag: DocumentModel.kt, DocumentWorkspaceTest.kt und diese
Dokumentation. Kein Commit. Kein Push.
