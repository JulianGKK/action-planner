# P7 — einfache Materialverwaltung

## Baseline und verbindlicher Produktvertrag

Sauberer Branch `p7-material`, HEAD `1fd065e` (P6c4-Merge), Schema 7 vor Änderungen.
P7 ist ein einziger Slice einschließlich Domain, Storage, Repository, UI und Tests.
Bestehende Task-, P3a-, Workspace-, Runtime- und Sessionmuster bleiben maßgeblich.
Vor Änderungen 34 Security-/P6-/Konfigurationsdateien als SHA-256-Inventar in
ignorierter `.prototype-cache/p7-freeze.json` erfasst.

Material ist eine aktionsgebundene Liste. Ein Item enthält ausschließlich `id`,
`actionId`, `name`, nullable `quantityLabel`, `placeLabel`, `responsiblePersonId`,
`linkedTaskId`, `sourceTemplateKey` und `createdAt`. Keine Kategorie, Statusmaschine,
Notiz, Events, Historie, Suche/Filter, Mengenrechnung oder automatische Aufgaben.
Name getrimmt 1–120 Unicode-Codepoints; Menge optionaler Text bis 40, Ort bis 80.
Optionale leere Texte werden null; keine stille Kürzung oder Zahlenvalidierung.

Lesen in PREPARATION/ACTIVE/COMPLETED; Create/Edit/Delete nur PREPARATION/ACTIVE.
DRAFT/DELETION_PENDING ohne Zugriff; COMPLETED read-only, kein Reopen.

## Statische Vorschläge

| Key | Bezeichnung |
| --- | --- |
| GLUE | Klebstoff |
| DUCT_TAPE | Gewebeband |
| SIGNS | Schilder / Plakate |
| BANNER | Banner |
| MARKERS | Marker / Stifte |
| SAFETY_VESTS | Warnwesten |
| FIRST_AID | Erste-Hilfe-Set |
| WATER | Wasser |
| POWER_BANKS | Powerbanks |
| CHARGING_CABLES | Ladekabel |
| ACTION_PHONE | Aktionshandy |
| RAIN_PROTECTION | Regenschutz |

Auswahl öffnet nur einen normalen Entwurf. Eigener Eintrag ohne Herkunftskey.
Key nicht unique, nicht angezeigt und beim Editieren unverändert. Keine Template-Tabelle.

## Speicherung, Links und Löschung

Schema 7→8 ergänzt ausschließlich `materials` mit den neun Itemfeldern, Action-FK
CASCADE, UNIQUE(actionId,id) und Indizes auf actionId/Person sowie actionId/Task.
Same-Action-Trigger prüfen Person/Task bei INSERT und UPDATE. AFTER DELETE auf
persons/tasks setzt ausschließlich die jeweilige Materialreferenz null. Fresh
und Migration installieren dieselben Trigger; bestehende P6-Trigger bleiben unverändert.
Repository prüft zusätzlich Referenzen und persistierte Phase in derselben Transaktion.
Edit/Delete vergleichen den vollständigen erwarteten Item-Snapshot, auch nach Detach.
Sortierung createdAt ASC, id ASC. Summary liest nur COUNT(*), Board nur Materialrows.

Links ändern weder Person/Funktion/Lage noch Taskzuweisung/Status/Titel/Events.
DONE/CANCELLED-Links bleiben bestehen. Keine Pseudonym-/Tasktitelsnapshots.
Materialdelete entfernt nur das Item; vollständige Aktionslöschung entfernt alle Items.
Der P3a-Port hat noch keinen allgemeinen Persondelete: Tests verwenden kontrollierte
synthetische Personenlöschung, kein neuer Produktlöschworkflow.

## UI, Session und Privacy

PREPARATION erhält Material als weiteren bestehenden Bereich. ACTIVE erhält eine
sechste Hubkarte mit ausschließlich Repositorycount, niemals Board-/Detailprefetch.
Count gehört zur vollständigen Workspacepublication, Refresh erhält den gültigen
alten Snapshot und Fehler erfinden keine Null. Materialbereich lädt Board bewusst separat.
COMPLETED bietet „Material ansehen“ ohne Board-/Summaryprefetch; Liste read-only,
Rückweg „‹ Zur abgeschlossenen Aktion“.

Liste: Bezeichnung, optionale Menge/Ort und aufgelöste P3a-Pseudonyme/Tasktitel.
Tap bearbeitet. Formular: Bezeichnung, Menge, Ort, einzelne verantwortliche Person,
einzelne Task, explizite Keine-Optionen, Save/Cancel. Entfernen verlangt Bestätigung:
„Der Materialeintrag wird entfernt. Verknüpfte Person und Aufgabe bleiben bestehen.“
Große bestehende Controls, umbrechende Labels und vorhandener Fokus-/IME-Cleanup.

MaterialModel hält nur RAM-State; Lock, Bereichs-/Aktions-/Phasenwechsel verwirft
Board, Selected, Draft, Expected, Confirmation, Error/Busy. Generation, Aktion,
Phase und Request binden Callbacks; keine automatische Wiederherstellung nach Reauth.
Callbacks/Refresh sind keine Sessioninteraction. Keine eigene Navigation-/Securityarchitektur.

Material ist ausdrücklich INTERNAL analog P4/P5a in derselben SQLCipher-DB.
Normales READY ohne Leadership-Step-up; Inhalte können trotzdem sensibel sein.
Nur P3a-Pseudonyme, kein P3b-Read. OPERATION/STEP/COMMIT/Publication und kurze
DB-Key-Leases bleiben unverändert. Keine neuen Permissions/Dependencies/Crypto,
Netzwerk, Notifications, Zwischenablage, Export, Dateien oder Medien.

Materialaufbewahrung bleibt innerhalb der zeitlichen Aktionsobergrenze. Konkrete Retention und allgemeine
Personen-/Aktionslöschworkflows bleiben separat. Logische Löschung ist keine
physische Überschreibgarantie; Referenzentkopplung anonymisiert Freitexte nicht.
Demo erhält keinen Materialinhalt. Kein Commit/Push.

## Prüfnachweis

Vollständige Unit-Suite:
803 Tests (App 398, Data 401, Security 4), keine Fehler oder übersprungenen Tests.
38 fokussierte Materialtests (28 App, 10 Data) bestanden zusätzlich.
Debug, Demo und beide AndroidTest-APKs gebaut; app/data/security lint,
verifyArchitecture und beide Manifest-Verifier erfolgreich. Einziger Lint-Hinweis:
neuere Gradle-Version verfügbar; keine Dependencyänderung in P7.

Freezevergleich: 34 Dateien unverändert, insbesondere Security/Session/Auth,
Leadership/P3b/Crypto, Produktmanifest/Backupregeln, Gradle/Dependencies und P6-Domain,
Repository/Storage/Schema 7. Schema 8 fügt nur materials hinzu; alle 15 bisherigen
Entitydefinitionen exakt unverändert. Debugpermissions weiterhin USE_BIOMETRIC plus
generierte eigene Receiverpermission; Demo nur eigene Receiverpermission.
Kein INTERNET, allowBackup=false, usesCleartextTraffic=false; FLAG_SECURE unverändert.

Tests schichten Domaingrenzen, echtes ProductViewModel mit verzögerten Fakeports,
native ProductScreen-Accessibility-Aktionen und reale SQLCipher-/Room-Transaktionen.
Same-Action-Validierung, beide Detach-Trigger, vollständiger STALE-Snapshot,
unveränderte DONE/CANCELLED-Tasks, Actioncascade und Revoke vor COMMIT/Publication
sind explizite Storagefälle. Historische Migrationfixtures entfernen vor dem
synthetischen Downgrade auch die zwei neuen Trigger auf den Elterntabellen;
Produkt-Person-/Task-Repositories benötigen keinen Cleanupcode.

Die ProductScreen-Probe prüft Vorlage ohne Write, native Texteingabe, P3a-Person,
DONE-Task, Create/Edit, Verantwortlichkeit entfernen, Deleteconfirmation abbrechen,
ACTIVE-Hubprivacy/Countrefresh, freien Eintrag, COMPLETED ohne Prefetch/read-only
sowie Lock aus dem Formular. Lokale Density für Font Scale 1/2, keine globale
Schrifteinstellung, keine automatische TalkBack-Aktivierung.
Probe-Diagnosen enthalten ausschließlich technische Phase und Stackzeilennummern.
Ein Scrolltreiberfehler beim Rückweg zur COMPLETED-Überschrift wurde korrigiert;
dies erforderte keine Produkt-/Domain-/Securityänderung.

Galaxy SM-M526BR/API33: P7MaterialProbe separat bei Font Scale 1 und 2 jeweils OK mit
Instrumentation-Code -1; MaterialStorageTest OK (18 Tests). Logcat-Sentinelcheck
für Material-Testinhalte: 0 Treffer. Keine Credentialänderung, pm clear, Uninstall
oder Mutation echter Produktdaten; APK-Updates ausschließlich install -r.
Die Tests erzeugen eigene synthetische DB-Fixtures und flüchtige Testschlüssel.
Bestehende Storage-Regression: OK (73 Tests): TaskStorageTest 12,
PersonStorageTest 17, ProtocolStorageTest 18, OperationsStorageTest 13,
ActionLifecycleStorageTest 12 und LeadershipStorageTest-Migration aus Version 3
ohne Provisionierung 1. Zusammen mit MaterialStorageTest 91 bestandene reale
Storagefälle. Historische Upgradepfade 1/2/3/4/5/6/7→8 ohne Datenersatz geprüft.
Vorhandene Ux3ActiveIntegrationProbe mit echtem ProductScreen bei Font Scale 1/2:
OK (Instrumentation-Code -1), einschließlich Personen/Funktionen/Tasks/Lagebild,
Lifecycle und verschachteltem Lock. UX2-, Session-, P3a/P3b-, P4/P5a- und
P6-Unitregressionen sind Bestandteil der vollständigen grünen Unit-Suite.
Vorhandene P6c2ProtocolProbe bei Font Scale 1/2: OK (Instrumentation-Code -1),
einschließlich Vorlagen/Create/Detail/Timeline/Revisionen/History/Irrtumsmarker,
Filter/Identifierprivacy und Lock. P6-Produktcode blieb unverändert.
Vorhandene P6c3ProtocolProbe bei Font Scale 1/2: OK (Instrumentation-Code -1),
einschließlich bewusstem COMPLETED-Einstieg ohne Prefetch, Nachtrag/Revisionen,
History/Phasenmarker, Irrtumsmarkierung, Privacy, Lock/Reauth und read-only Grenzen.

Reproduzierbare Hostprüfungen (offline):

```sh
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest --offline
./gradlew :app:testDebugUnitTest --tests '*Material*' :data:testDebugUnitTest --tests '*Material*' --offline
./gradlew :app:assembleDebug :app:assembleDemo :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest --offline
./gradlew :app:lintDebug :data:lintDebug :security:lintDebug verifyArchitecture :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
```

Geräteaufrufe nur für die synthetischen Testpakete:

```sh
adb shell am instrument -w -r -e font 1 org.pag.prototype.a.test/org.pag.prototype.P7MaterialProbe
adb shell am instrument -w -r -e font 2 org.pag.prototype.a.test/org.pag.prototype.P7MaterialProbe
adb shell am instrument -w -r -e class org.pag.data.product.MaterialStorageTest org.pag.data.test/androidx.test.runner.AndroidJUnitRunner
```

Lokale technische Logs: `.prototype-cache/p7-final-checks.log`,
`p7-unit-focused.log`, `p7-galaxy-font1.log`, `p7-galaxy-font2.log`, `p7-storage.log`,
`p7-storage-regression.log`, `p7-ux3-regression.log`, `p7-p6c2-regression.log`,
`p7-p6c3-regression.log`.
Keine Logs werden eingecheckt; der Nachweis hält Ergebnisse und Aufrufe fest.

### Manuelle Restabnahme (nicht als bestanden markiert)

Nur eine Testaktion und erfundene Pseudonyme/Inhalte verwenden:

1. PREPARATION → Material → Vorschlag → optionale Felder/Person/Task → Speichern.
2. Karte bearbeiten, Keine-Optionen, Entfernen bestätigen und Abbrechen vergleichen.
3. ACTIVE-Lagebildcount → Material → freier Eintrag → Rückweg.
4. COMPLETED → bewusster Einstieg → read-only Liste → Rückweg.
5. TalkBack: Überschriften, ausgewählte Person/Task, Feldlabels, Buttons und Bestätigung.
6. Große Schrift und offene Tastatur: Scroll, Speichern/Abbrechen und Backcontrols.
7. Lock aus dem Formular, Reauth und sauberer normaler Produkteinstieg.

Keine vollständige Barrierefreiheit oder Performancegarantie behauptet.
Konkrete Retentiondauer/-workflow, allgemeine Personen-/Aktionslöschung und
juristische Prüfung vor Echtdatennutzung bleiben außerhalb P7.

## Abschlussstatus

A) P7 MATERIAL AUTOMATISCH GRÜN – BEREIT FÜR REVIEW

Ein vollständiger Slice; keine offenen technischen Fehler in den geprüften Fällen.
Alle automatisierten Nachweise oben bestanden; git diff --check ohne Befund.
Branch bleibt p7-material, HEAD unverändert 1fd065e, 39 geänderte/neue Dateien
für Review, keine Commits oder Pushes. Kein allgemeiner Löschworkflow oder
Retentionworker und keine Freigabe der manuellen/juristischen Restabnahmen.

## Geänderte Dateien

39 Dateien; keine Security-, Dependency- oder Produktionsmanifeständerung.

- [PRODUCT.md](../PRODUCT.md)
- [app/src/androidTest/AndroidManifest.xml](../app/src/androidTest/AndroidManifest.xml)
- [app/src/main/kotlin/org/pag/prototype/MainActivity.kt](../app/src/main/kotlin/org/pag/prototype/MainActivity.kt)
- [app/src/main/kotlin/org/pag/prototype/demo/DemoScreen.kt](../app/src/main/kotlin/org/pag/prototype/demo/DemoScreen.kt)
- [app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt](../app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt)
- [app/src/main/kotlin/org/pag/prototype/product/PagShell.kt](../app/src/main/kotlin/org/pag/prototype/product/PagShell.kt)
- [app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt](../app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt)
- [app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt](../app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt)
- [app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt](../app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt)
- [app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceTest.kt](../app/src/test/kotlin/org/pag/prototype/product/ActiveWorkspaceTest.kt)
- [app/src/test/kotlin/org/pag/prototype/product/PreparationWorkspaceTest.kt](../app/src/test/kotlin/org/pag/prototype/product/PreparationWorkspaceTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/ActionLifecycleStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/ActionLifecycleStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/OperationsStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/OperationsStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/PersonStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/PersonStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/ProtocolStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/ProtocolStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/TaskStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/TaskStorageTest.kt)
- [data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt](../data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt)
- [data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt](../data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt)
- [docs/data-model.md](data-model.md)
- [docs/data-retention.md](data-retention.md)
- [docs/privacy.md](privacy.md)
- [docs/security.md](security.md)
- [docs/testing.md](testing.md)
- [docs/ux-roadmap.md](ux-roadmap.md)
- [app/src/androidTest/kotlin/org/pag/prototype/P7MaterialProbe.kt](../app/src/androidTest/kotlin/org/pag/prototype/P7MaterialProbe.kt)
- [app/src/main/kotlin/org/pag/prototype/product/MaterialModel.kt](../app/src/main/kotlin/org/pag/prototype/product/MaterialModel.kt)
- [app/src/main/kotlin/org/pag/prototype/product/MaterialScreen.kt](../app/src/main/kotlin/org/pag/prototype/product/MaterialScreen.kt)
- [app/src/test/kotlin/org/pag/prototype/product/MaterialModelTest.kt](../app/src/test/kotlin/org/pag/prototype/product/MaterialModelTest.kt)
- [app/src/test/kotlin/org/pag/prototype/product/MaterialWorkspaceTest.kt](../app/src/test/kotlin/org/pag/prototype/product/MaterialWorkspaceTest.kt)
- [data/schemas/org.pag.data.product.ActionDatabase/8.json](../data/schemas/org.pag.data.product.ActionDatabase/8.json)
- [data/src/androidTest/kotlin/org/pag/data/product/MaterialMigrationFixture.kt](../data/src/androidTest/kotlin/org/pag/data/product/MaterialMigrationFixture.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/MaterialStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/MaterialStorageTest.kt)
- [data/src/main/kotlin/org/pag/data/product/MaterialDatabase.kt](../data/src/main/kotlin/org/pag/data/product/MaterialDatabase.kt)
- [data/src/main/kotlin/org/pag/data/product/ProductMaterialRepository.kt](../data/src/main/kotlin/org/pag/data/product/ProductMaterialRepository.kt)
- [data/src/test/kotlin/org/pag/data/product/MaterialProjectionTest.kt](../data/src/test/kotlin/org/pag/data/product/MaterialProjectionTest.kt)
- [data/src/test/kotlin/org/pag/data/product/MaterialRulesTest.kt](../data/src/test/kotlin/org/pag/data/product/MaterialRulesTest.kt)
- [docs/product-p7-material.md](product-p7-material.md)
- [domain/src/main/kotlin/org/pag/domain/Materials.kt](../domain/src/main/kotlin/org/pag/domain/Materials.kt)
