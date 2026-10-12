# UX1a — Security-aware UI Shell

Stand: 12.10.2026. Baseline: sauberer `main`, `6277059` (UX0).
Verbindliche Grundlage: [UX-Philosophie](ux-philosophy.md),
[Roadmap](ux-roadmap.md), [Security](security.md), [Privacy](privacy.md).
Kein Commit oder Push.

## Nachfolgender Polish

UX1b baut auf diesem Slice auf. Aktueller Header, 4-dp-Controls, Floating-Banner
mit Fortschritt, gebündelte Bereichsloads und Eingabehints sind in
[ux1b-polish.md](ux1b-polish.md) dokumentiert. Die folgenden UX1a-Nachweise
beschreiben den Stand vor diesem Polish und werden nicht als UX1b-Rerun ausgegeben.

## Scope und Komponenten

Kleiner Compose-Unterbau im bestehenden Theme, ohne neue Dependency, Farbe,
Permission, Persistenz oder Netzwerkpfad. Single Activity, manuelle DI und
bestehende Repository-/ViewModel-Struktur bleiben erhalten.

`PagShell.kt` enthält Primary/Secondary/TertiaryButton, LockedScreen,
LoadingState, EmptyState, ErrorState, SessionCountdown und StatusBanner.
Primary: volle Breite, mindestens 56 dp hoch, 8-dp-Rundung; Secondary:
umrandet, mindestens 48 dp; Tertiary: Textaktion mit mindestens 48 dp.
Alle verwenden die vorhandenen FormInput-Wrapper: vor bewusstem Click werden
IME/Fokus geschlossen; technische Timer erzeugen keine Interaktion.

## Gesperrter Einstieg und Initialisierung

Normal LOCKED zeigt nur PaG/Aktionsplaner, „PaG ist gesperrt.“,
„Deine Aktionsdaten sind geschützt.“ und „Entsperren“. Die Hauptaktion
liegt im zentrierten, bei großen Texten scrollbar bleibenden Einstieg.
Keine letzten Titel, Pseudonyme, Taskdaten, Lagezahlen oder Banner.

EMPTY bietet bewusst „Geschützten Speicher einrichten“ mit kurzer Erklärung.
Rendering löst niemals Bootstrap aus. PARTIAL_OR_INVALID bleibt fail closed;
BLOCKED erklärt den nicht sicher verwendbaren Speicher und behauptet weder
Reparatur noch Datenersatz. AUTHENTICATING verweist auf den Android-Dialog.
MAINTENANCE und initialer Produktload verwenden denselben ruhigen Prüftext.

## Countdown als ausschließlich lesbare Projektion

`SessionPort.remainingAbsoluteMillis()` ist eine optionale Anzeigeprojektion.
Der produktive Coordinator implementiert sie synchronisiert mit seiner
bereits vorhandenen absoluten Deadline und derselben injizierten monotonen,
Sleep einschließenden Uhr (`elapsedRealtime` im ProductRuntime).
Die Methode ruft nur die vorhandene Gültigkeitsprüfung auf und liefert
außerhalb gültigem READY null. Sie setzt weder absolute noch Idle-Deadline.
Keine Deadline wird an UI, SavedState oder Speicherung ausgegeben.
`SessionDbKeyOwner` ist unverändert.

Ein generationgebundener Compose-Effekt fragt etwa einmal pro Sekunde ab.
Kein `interaction()`, kein eigener Sessiontimer, keine Wanduhr, keine
Storagefreigabe. Der bestehende Activity-Expiry-Check und die Coordinator-/
Storage-Gates bleiben Autorität. Die Abfrage kann wie `snapshot()` bereits
bestehenden Ablauf feststellen; sie führt keine zusätzliche Lockregel ein.
Nach Revoke/Generationwechsel werden Restzeit, operative Daten und Banner
sofort bereinigt; der Effekt wird abgebrochen und neu gebunden.

Format `m:ss`, grundsätzlich Sekundenabrundung; die letzte positive
Teilsekunde erscheint als `0:01`, um kein `0:00` mit offenen Daten zu zeigen.
Null wird nicht gerendert. Ziffern sind monospace. Die Anzeige beschreibt
maximale absolute Restzeit, keine garantierte weitere Freigabe; Inaktivität
kann früher sperren. Der zugängliche Beschreibungstext enthält diesen Hinweis.
Keine Live-Region am Countdown, keine automatische sekündliche Ansage.

## Loading, Content, Empty, Error

`ActionListState` ist sealed: Loading, Content(rows), Empty, Error.
`actions` ist ausschließlich eine abgeleitete Kompatibilitätsansicht des
Content-Zustands, kein paralleler Boolean-/Listen-State.
READY startet einen generation- und requestgebundenen Load. Vor dessen
Publication gibt es keine Empty-Behauptung und keine Nullzählung.
Erfolgreiche leere Liste ergibt Empty, Einträge ergeben Content.

Der vorhandene ActionRepository-Port publiziert ausschließlich erfolgreiche
Ergebnisse; geschützte Speicherfehler führen bereits über den Coordinator
zu BLOCKED. Dies wird als Error modelliert und als permanente Fehleransicht
angezeigt, ohne lokalen Retry, Ersatzanlage oder Umgehung frischer Authentisierung.
Ein widerrufener Load ist kein erfolgreicher leerer Load. Die neue UI-Prüfung
ist zusätzliche Publication-Hygiene; bestehende Storage-Gates bleiben maßgeblich.
Nach Unlock ist das frühere Empty-Flackern beseitigt. Kein Direkt-Routing in
ACTIVE, keine Workspace-Tabs und kein Lagebild-/Completed-Umbau in diesem Slice.

## Ergebnisbanner

Personen-Speicherung zeigt nach bestätigter erfolgreicher Repository-Publication
„✓ Person gespeichert“. Request, Aktion und Sessiongeneration werden geprüft;
auch nach dem synchron mögliche weitere Repositoryaufrufe auslösenden Refresh
wird die Generation erneut geprüft. Fehler und stale/revoked Writes erzeugen
keinen Erfolg. Lock/Generationwechsel entfernt den Bannerzustand.

Mindestens 5 Sekunden Anzeigedauer, über Compose AccessibilityManager an
Accessibility-Bedarf angepasst. Polite-Live-Region, textliche Bedeutung, kein
Security-State, keine Systemnotification. Die ID schützt einen neuen Banner
vor dem Ablauf eines alten Timers. Der Timer ruft keine Benutzerinteraktion auf.
Rollen- und Taskbanner bleiben UX2/UX3: ihre eigenen Model-/Request-Pfade werden
hier nicht zusätzlich umgebaut. Änderungen bleiben im Inhalt nachvollziehbar.

## Accessibility, Layout und Fokus

Keine festen Buttonhöhen oder maxLines; Buttons wachsen mit Schrift.
Der Locked-Screen ist scrollbar, Systemleisten werden berücksichtigt.
Der geschützte Header bleibt außerhalb des Inhalts-Scrolls; FlowRow lässt
Headerbestandteile bei großer Schrift umbrechen. Hierarchie über Form und Text,
keine Bedeutung allein über Farbe. Überschriften der neuen Zustände erhalten
Heading-Semantik, Buttons behalten native Button-/Disabled-Semantik.
Keine redundanten ContentDescriptions an textbeschrifteten Buttons.

ProductInputScope behält Lifecycle-/Windowfocus-Cleanup und reagiert zusätzlich
auf den zentralen Screen-State-Wechsel. Credential-Rückkehr erzeugt keinen
Editorfokus. Countdown/Banner-Ticks gehören nicht zum Fokus-Screen-Key.
Vollständige Formular-, Next-/Done- und Autofokus-Überarbeitung bleibt UX2/UX3.
TalkBack-Sprachverständlichkeit und Einhandbedienung benötigen weiterhin den
unten vorgeschlagenen menschlichen Smoke; Bounds-/Texttests ersetzen das nicht.

## Abgrenzung und verbleibende Legacy-UI

Keine „Neu bestätigen“-Attrappe, kein Re-Auth-Pfad, keine neue normale
Session-State-Machine und kein globales isUnlocked. UX1-S bleibt ein separater
Security-Slice. Unverändert: 180000 ms absolut ab erfolgreicher Authentisierung,
30000 ms READY-Inaktivität, Foreground-/Device-Lock, Generation-Revoke,
P3b-Grants, DB-Key-Lifecycle und P5a-TaskModel-Lock-Race-Gates.

UX2/UX3 übernehmen noch die alten FormButtons in Aktionsdetails/-formularen,
Personen-, Rollen-, Planungs-, Lage-, Sensitive- und Taskflächen, Bereichslayouts,
eigene Bereichs-Loadzustände und weitere Ergebnisbanner. Keine repo-weite Migration.

## Automatische Prüfungen

Vollständige Offline-Prüfung mit projektlokalem `ANDROID_USER_HOME`:

```text
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest \
  :security:testDebugUnitTest :domain:test :app:assembleDebug \
  :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest \
  :app:lintDebug verifyArchitecture :app:verifyPrototypeManifest --offline
```

Ergebnis: 416 Unit-Tests (App 72, Data 340, Security 4), null Fehler/Failures/
Skips; Domain hat keine eigenen Tests. Enthalten: sechs neue PagShell-Tests,
Personen-Erfolgs-/Fehler-/Revoke-/Timer-Bannerprüfungen, Coordinator-Restzeit-
Regression und die bestehenden 15 TaskModel-Tests einschließlich P5a-Lock-Races.
Die echte Banner-Timerprüfung wartet mindestens fünf Sekunden und prüft,
dass dabei keine Interaktion erzeugt wird. Bestehende Sessiontests decken
Foreground, Sleep/Deadline, alte Callbacks, Maintenance und Generationcleanup ab.

App-Build, beide AndroidTest-Builds, Lint, Architekturcheck und Manifestcheck
sind erfolgreich. Lint: null Fehler, ein bestehender Gradle-Versionshinweis;
kein Dependency-Update in diesem Slice. Der kombinierte Build deckte eine
fehlende Abhängigkeit von `validateSigningDebugAndroidTest` auf die vorhandene
projektlokale Keystore-Erzeugung auf; diese ist minimal ergänzt.

Produktmanifest unverändert: USE_BIOMETRIC und die bestehende automatisch
zusammengeführte signature-basierte DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION.
Ein bestehender exportierter Launcher, keine neue Komponente/Permission,
kein INTERNET, keine Services/Provider/Receiver im Produktmanifest.
Der zusätzliche Instrumentation-Probe existiert ausschließlich im Test-APK.

Galaxy SM-M526BR, API 33: nicht invasiver Locked-Screen-Probe erfolgreich.
Prüft neutrale Labels, fehlenden Countdown/geschützte Shell, primäres Touch-Ziel;
synthetisches EMPTY bei Compose-fontScale 1 und 2 prüft langes deutsches
Setup-Label, Breite, mindestens 56-dp-Höhe und keine automatische Einrichtung.
Keine Systemschrift-/Credential-Änderung, Screenshots, pm clear oder Uninstall.
App-/Testinstallation ausschließlich `adb install -r`.

Die erste Data-Testinstallation wurde wegen einer unpassenden Debugsignatur
abgewiesen; darauf lief noch das vorherige Test-APK (91 Tests erfolgreich).
Dieser Lauf zählt nicht als Nachweis des aktuellen APKs. Nach Neubau mit dem
bisherigen projektlokalen Android-Testkeystore wurde das aktuelle APK erfolgreich
per install -r aktualisiert und der Produkt-Storage-Lauf erneut gestartet.
Aktueller Lauf: **91 Tests erfolgreich** auf Galaxy SM-M526BR/API33,
911 Sekunden; ProductActions 13, ActionLifecycle 12, Person 17, Operations 13,
Task 12, Leadership 24. Die langen P3b-KDF-Tests sind Bestandteil des bestehenden
isolierten Prüfumfangs. Testdaten liegen ausschließlich in eigenen synthetischen
Data-Testnamespaces, nicht im produktiven Bestand.

Lokale Nachweise unter `.prototype-cache/`: `ux1a-checks.log`,
`ux1a-galaxy-current.log`, `ux1a-locked-galaxy.log`; Unit-XMLs und Lintbericht
liegen in den üblichen Modul-Buildverzeichnissen. Diese Artefakte sind nicht
versioniert. `git diff --check` erfolgreich. Git-Status: 11 modifizierte und
4 neue Dateien auf `main`; keine Commits, kein Push.

Klassifikation: **A) UX1a AUTOMATISCH GRÜN – AUDIT-BEREIT**.
Der vorgeschlagene manuelle Smoke bleibt ausdrücklich ausstehend.

## Vorgeschlagener manueller Galaxy-Smoke

Nur synthetische Inhalte; keine Löschung echter Aktionen für einen Empty-Test:

1. Normalen Locked-Screen und einhändige Hauptaktion prüfen.
2. Explizit entsperren: stabiler Prüf-/Loading-Zustand, kein Empty-Flackern.
3. Countdown etwa `2:59` → …; bei Inaktivität, manueller Sperre und Background
   verschwindet er mit allen operativen Daten. Hintergrundwechsel sperrt sofort.
4. Erfolgreich geladene Content-Liste und einen separat vorbereiteten wirklich
   leeren Testbestand prüfen; EMPTY bietet „Aktion planen“.
5. Große Systemschrift, lange deutsche Labels, Touch-Ziele, Scrollbarkeit und
   TalkBack-Reihenfolge prüfen; Countdown soll nicht sekündlich unterbrechen.
6. Person speichern: bestätigter Banner, lesbare Dauer, Verschwinden nach Lock.

Ein kompletter produktiver Unlock-/Device-Lock-/TalkBack-Smoke wurde hier nicht
automatisch durchgeführt; er benötigt bewusste Credential-Bedienung durch den
Benutzer. Synthetische Komponenten-/Porttests behaupten diesen Nachweis nicht.

## Geänderte Dateien

- [PagShell.kt](../app/src/main/kotlin/org/pag/prototype/product/PagShell.kt): Komponenten, expliziter Listenstate, Format und Banner-Timer.
- [ProductScreen.kt](../app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt): Shell, Header und Zustandsrendering.
- [ProductInputs.kt](../app/src/main/kotlin/org/pag/prototype/product/ProductInputs.kt): kompatible Modifier-/Shape-Parameter für Fokus-/IME-Wrapper.
- [ProductViewModel.kt](../app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt): Loadbindung, read-only Restzeit, Personenfeedback und Cleanup.
- [Session.kt](../domain/src/main/kotlin/org/pag/domain/Session.kt): ausschließlich lesbare SessionPort-Projektion.
- [SessionCoordinator.kt](../data/src/main/kotlin/org/pag/data/SessionCoordinator.kt): minimale Projektionsimplementierung.
- [PagShellTest.kt](../app/src/test/kotlin/org/pag/prototype/product/PagShellTest.kt): Countdown/Load/Generation-Regressionen.
- [PersonViewModelTest.kt](../app/src/test/kotlin/org/pag/prototype/product/PersonViewModelTest.kt): Banner-Erfolg/Fehler/Revoke/Timer.
- [SessionCoordinatorTest.kt](../data/src/test/kotlin/org/pag/data/SessionCoordinatorTest.kt): unveränderte absolute und Idle-Grenzen bei Anzeigeabfragen.
- [Ux1aShellProbe.kt](../app/src/androidTest/kotlin/org/pag/prototype/Ux1aShellProbe.kt): nicht invasiver Geräte-/Großschrift-Probe.
- [AndroidTest-Manifest](../app/src/androidTest/AndroidManifest.xml): zusätzlicher Test-Runner.
- [app/build.gradle.kts](../app/build.gradle.kts): explizite AndroidTest-Signing-Abhängigkeit.
- [security.md](security.md): minimale Erläuterung der read-only Projektion.
- [ux-roadmap.md](ux-roadmap.md): UX1a-Scope ergänzen.
- Diese Datei: Slice, Abgrenzung, Nachweise und Smoke.
