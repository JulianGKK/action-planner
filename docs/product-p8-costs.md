# P8 — einfache Kosten-Sammelliste

## Baseline und Produktvertrag

Branch p8-costs, sauberer Working Tree, HEAD e30bca3 (P7-Merge), Schema8.
Ein Slice einschließlich Produktvertrag, Domain, Storage, Repository, UI, Tests.
Vor Änderungen 40 Security-/Konfigurations-/P6-/P7-Dateien als SHA-256-Inventar in
ignorierter .prototype-cache/p8-freeze.json erfasst. Keine neue Architektur.

Expense enthält ausschließlich id, actionId, Bezeichnung, totalCents,
1–32 geordnete Payments und createdAt. Payment nur payerLabel/amountCents.
Bezeichnung trim/1–120 Unicode-Codepoints, Label trim/1–80, Beträge positiv.
EUR-only, positive Long-Cents, keine Float-/Double-Konvertierung. Exakte Summe
mit overflow-sicherer Ganzzahlarithmetik; keine Rundung/Restzahlung/Korrektur.
Doppelte Labels erlaubt. Eingabereihenfolge bleibt erhalten. Keine Kategorien,
Datum, zusätzliche Beschreibung/Notiz, Belege/Dateien, Erstattung, Währungsauswahl,
Schulden/Budget/Bankdaten, Historie/Auditlog oder Export.

EuroMoney akzeptiert positive ungruppierte Dezimaltexte mit Komma oder Punkt
und maximal zwei Nachkommastellen. Keine Vorzeichen, Währungszeichen,
Tausendertrennzeichen, Exponenten oder unvollständigen Dezimaltexte beim Save.
Anzeige deutsch mit genau zwei Dezimalstellen und €; Long-Grenzen geprüft.
RAM-Draft hält Strings, z. B. leer/12, während der Eingabe, Domain erst bei Save.

payerLabel ist eigenständiger expense-lokaler Text/Snapshot. P3a-Pseudonyme werden
nur im Formular kopiert, kein Person-FK und keine personId im Payment.
Rename/Delete verändern gespeicherte Kosten nicht. Externe kurze Labels erlaubt,
Pseudonyme/kurze Bezeichnung empfohlen. Keine P3b-Identitätsrekonstruktion.
Spätere allgemeine Lösch-/Freitextprüfung muss fortbestehende Labels berücksichtigen.

## Lifecycle und UI

ExpenseRules erlauben Read/Create/Edit/Delete PREPARATION/ACTIVE/COMPLETED.
DRAFT/DELETION_PENDING ohne Zugriff. Enge administrative COMPLETED-Ausnahme:
Kosten werden häufig erst nach Abschluss vollständig gesammelt. Nur P8 geöffnet;
TaskRules/MaterialRules/OperationalRules/ActionLifecycle unverändert, kein Reopen.
Kein Nachtragsmarker/Updatehistorie: P8 ist keine Ereignisdokumentation.

PREPARATION: Bereich Kosten, Summary im Overview nur Anzahl/Summe.
ACTIVE: Reihenfolge Personen/Aufgaben/Funktionen/Protokoll/Material/Kosten/Details,
Hub ausschließlich ExpenseSummary, keine Titel/Labels/Payments oder Boardprefetch.
Atomarer Workspace-Snapshot; Refresh erhält alten gültigen Inhalt, keine Fake-0.
Bestehende 600dp-/Font-Scale-Regel unverändert, keine neue Navigation.
COMPLETED: vor „Kosten verwalten“ null ExpenseReads/Writes; bewusster Einstieg
mit Hinweis, dass die Aktion abgeschlossen bleibt. Gleiche Liste/Form, Rückweg
„‹ Zur abgeschlossenen Aktion“. Phasenwechsel verwirft alten ACTIVE-Draft,
keine automatische Wiederöffnung trotz erlaubter COMPLETED-Writes.

Liste createdAt ASC/id ASC; Titel/Gesamtbetrag und geordnete Zahlungslabels/Beträge,
Tap bearbeitet, keine Detailseite. Formular mit Bezeichnung/Gesamt und Paymentzeilen,
freie Bezeichnung/P3a-Auswahlhilfe, bei 32 Addcontrols disabled. Save nur mit exakter
Summe und gültigen Feldern, Feedback ohne automatische Änderung. Jede Zeile entfernbar.
Entfernen verlangt explizite Bestätigung für Parent und gesamte Zahlungsaufteilung.
STALE erhält Draft, keine blinde Wiederholung; „Zur aktuellen Kostenliste · Eingaben
verwerfen“ lädt bewusst neu. Keine SavedState-Persistenz.

## Storage, Session und Privacy

Schema8→9 addiert ausschließlich expenses und expense_payments. Parent Action-CASCADE,
UNIQUE(actionId,id); Children PK(actionId,expenseId,position), Composite-CASCADE.
Keine Person-/FileAsset-FKs. Parent und komplette Children werden atomar geschrieben;
Edit ersetzt Children vollständig. Materialisierung prüft normalisierten Parent,
1–32 Children, lückenlose Positionen 0..n-1, positive Beträge und exakte sichere Summe.
Ungültige DB-Zustände ergeben ProductStorageFailure. Full-Expected enthält alle
geordneten Zahlungen/createdAt. Summary SQL nur COUNT/SUM aus Parent, keine Children.
Zusätzliche COUNT-Prüfungen der SQLite-Storageklasse verhindern, dass REAL-Beträge
oder REAL-Positionen durch den Long-/Int-Adapter still umgewandelt werden.
Summary prüft dafür nur Parent-totalCents, weiterhin keine Titel/Labels/Children.
SQL-SUM-Overflow ergibt Storagefehler, keine still umgebrochene/falsche Gesamtsumme
und kein künstliches fachliches Kostenlimit.

SENSITIVE wegen Finanzangaben und potentiell personenbezogenen Labels. Bewusste
enge READY-Freigabe ohne Leadership-Step-up in derselben SQLCipher-DB. Bestehende
OPERATION/STEP/Read-/WriteTransaction/COMMIT/Publication/Generation-Gates und kurze
DB-Lifetime. Keine Person/P3b-Abfrage im Repository. Lock/Aktions-/Phasen-/Bereichs-
wechsel räumen Board/Draft/Expected/Selected/Confirmation/Error/Busy/Completedflag.
Late callbacks und altes Feedback verworfen, Reauth öffnet Kosten nicht automatisch.
Callbacks/Displaytimer/Refreshresultate sind keine Sessioninteraction.
Keine neue Permission/Dependency/Crypto, Netzwerk/Export/Clipboard/Notifications.
Security, Backup/Transfer, FLAG_SECURE, P3b/Auth/Session/Keyhierarchie bleiben erhalten.
Demo ohne Kostenfachinhalt; Enumfallback lediglich auf bestehenden Demo-Hub.

Keine Retentiondauer/-engine implementiert. Kosten können einen anderen legitimen
Nachbereitungsbedarf als Task/Material haben; konkrete Fristen/rechtliche Pflichten
vor Umsetzung fachkundig prüfen. Vollständige Action-Löschung ist Obergrenze und
entfernt Parent/Children, kein unabhängiger Restbestand/Overwriteversprechen.

## Abnahme

Vollständige Unit-Suite: 839 Tests (App421/Data414/Security4), keine Fehler oder
übersprungenen Tests. Fokussiert zusätzlich 36 P8-Tests: Domain8, Money4,
Projection1, Model13, Workspace10. Realer ProductViewModel und verzögerte Fakeports
prüfen atomare Summarypublication/Refresh, normale COMPLETED-Seite ohne Kostenreads,
explizite P8-Writes bei gesperrten P4/P5a/P7/Aktionspfaden, Cross-Area-Cleanup,
Phasenwechsel, Lockmatrix/Reauth und verworfene späte Publication.

Hostchecks offline grün: Debug, Demo, beide AndroidTest-APKs, app/data/security lint,
verifyArchitecture sowie Prototype-/Demo-Manifestverifier. Kein Lint-Fehler;
einziger bestehender Hinweis ist eine neuere verfügbare Gradle-Version.
40 eingefrorene Dateien unverändert; Schema8 unverändert, Schema9 ergänzt nur
expenses/expense_payments und alle 16 bisherigen Entitydefinitionen bleiben exakt.
Debug weiterhin USE_BIOMETRIC plus eigene generierte Receiverpermission; Demo nur
eigene Receiverpermission. Kein INTERNET, allowBackup=false, cleartext=false.
FLAG_SECURE und alle Session/Auth/P3b/Crypto/Backup-/Dependencydateien unverändert.

Galaxy SM-M526BR/API33: ExpenseStorageTest OK (19 Tests), P8ExpenseProbe bei lokaler
Font Scale 1 und 2 separat OK mit Instrumentation-Code -1. Native Eingabe,
Pseudonymkopie/externe Bezeichnung, exakte Summe, ACTIVE-Hubprivacy/Serverrefresh,
COMPLETED-Writes bei abgeschlossener Aktion, Delete-Cancel, disabled Addcontrols
bei exakt 32 Zahlungen und Lock/Reauth bestanden. Kosten-Logcat-Sentinels: 0 Treffer.
Nur synthetische Ports/DBs und flüchtige Testkeys, APK-Updates install -r;
keine Credentialänderung, pm clear, Uninstall oder Echtdatenmutation.

Während Umsetzung gefunden und abgesichert: STALE durfte durch nachträglich
ungültigen Geldtext nicht zu INVALID werden; Saveguard vor Parsing schützt den
behaltenen Draft bis zum bewussten Reload. Pending Reads können STALE ebenfalls
nicht aufheben. ACTIVE-Kostenbereich refreshes WorkspaceSummary nach Writes
aus dem Repository. Initiale Native-Schema-Testhelfer verwendeten fälschlich READY;
für ihre Diagnoseöffnung wurde der vorhandene MAINTENANCE-Scope eingesetzt.
Reauth-Probe auf tatsächlich vorhandenes Aktionslistenlabel korrigiert.
Keine Security-/Domain-Policy aufgeweicht und kein automatischer Merge.

Bestehende UI-Regressionsproben: P7MaterialProbe separat mit Font Scale 1 und 2,
Ux3ActiveIntegrationProbe, P6c2ProtocolProbe und P6c3ProtocolProbe mit jeweils
Font Scale 1/2 bestanden (Instrumentation-Code -1). Ein kombinierter P7-Lauf
fand beim Wechsel auf Schriftgröße 2 den zweiten Bereichseinstieg nicht;
beide originalen getrennten P7-Läufe bestanden ohne P7-Code-/Probeänderung.
Diese Instrumentationsgrenze ist kein reproduzierter Produktfehler.

Finaler kombinierter SQLCipher-/Room-Lauf: OK (110 Tests), Instrumentation-Code -1.
ExpenseStorageTest19 (einschließlich REAL-Storageklassen und SUM-Overflow),
MaterialStorageTest18, TaskStorageTest12, PersonStorageTest17,
ProtocolStorageTest18, OperationsStorageTest13, ActionLifecycleStorageTest12
und LeadershipStorageTest-Migration aus Version3 ohne Provisionierung1.
Die 91 bestehenden Fälle sind auf Schema9 grün; historische Upgradepfade
1/2/3/4/5/6/7/8→9 sowie Migration-Revoke/Retry ohne Datenersatz geprüft.
Historische Fixtures entfernen ausschließlich synthetische Expensechildren/Parent
vor ihrem Downgrade; bestehende Parenttrigger/P6/P7 bleiben im Produkt unverändert.
Log: .prototype-cache/p8-storage-regression.log. Weitere UI-Logs:
p8-p7-font1-regression.log, p8-p7-font2-regression.log, p8-ux3-regression.log,
p8-p6c2-regression.log, p8-p6c3-regression.log.

Technische lokale Logs: .prototype-cache/p8-final-checks.log, p8-focused.log,
p8-storage.log, p8-galaxy-font1.log, p8-galaxy-font2.log. Logs werden nicht eingecheckt.

```sh
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest :security:testDebugUnitTest --offline
./gradlew :app:testDebugUnitTest --tests '*Expense*' :data:testDebugUnitTest --tests '*Expense*' --tests '*EuroMoney*' --offline
./gradlew :app:assembleDebug :app:assembleDemo :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest --offline
./gradlew :app:lintDebug :data:lintDebug :security:lintDebug verifyArchitecture :app:verifyPrototypeManifest :app:verifyDemoManifest --offline
adb shell am instrument -w -r -e font 1 org.pag.prototype.a.test/org.pag.prototype.P8ExpenseProbe
adb shell am instrument -w -r -e font 2 org.pag.prototype.a.test/org.pag.prototype.P8ExpenseProbe
adb shell am instrument -w -r -e class org.pag.data.product.ExpenseStorageTest org.pag.data.test/androidx.test.runner.AndroidJUnitRunner
```

Manuelle TalkBack-/IME-Abnahme bleibt eigenständig; keine vollständige Accessibilitygarantie.
Smoke ausschließlich mit Testaktion/synthetischen Daten: Kosten erfassen, Pseudonym
kopieren, externes Label, exakte Zahlungssumme, Edit/Remove/Cancel, ACTIVE-Hubprivacy,
COMPLETED-Verwaltung bei abgeschlossener Aktion, Lock/Reauth und große Schrift.
Kein Commit/Push.

## Abschlussstatus

A) P8 KOSTEN AUTOMATISCH GRÜN – BEREIT FÜR REVIEW

Ein vollständiger Slice, keine offenen technischen Fehler in den geprüften Fällen.
git diff --check ohne Befund; Branch p8-costs, HEAD unverändert e30bca3.
42 geänderte/neue Dateien zur Review, kein Commit/Push. Manuelle TalkBack-/IME-
Abnahme, konkrete Retention-/Löschworkflows und fachkundige juristische Prüfung
vor Echtdatennutzung bleiben offen. Kostenlabels werden durch Personenlöschung
nicht anonymisiert. Die technische Long-Kapazität der Summen ist keine Budget-
oder fachliche Kostenobergrenze; unrepräsentierbare Aggregation schlägt kontrolliert
als Storagefehler fehl, keine falsche oder automatisch gerundete Summe.

## Geänderte Dateien

42 Dateien, kein Commit/Push. Security-/Dependency-/Produktmanifestdateien unverändert.

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
- [app/src/test/kotlin/org/pag/prototype/product/MaterialWorkspaceTest.kt](../app/src/test/kotlin/org/pag/prototype/product/MaterialWorkspaceTest.kt)
- [app/src/test/kotlin/org/pag/prototype/product/PreparationWorkspaceTest.kt](../app/src/test/kotlin/org/pag/prototype/product/PreparationWorkspaceTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/ActionLifecycleStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/ActionLifecycleStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/MaterialMigrationFixture.kt](../data/src/androidTest/kotlin/org/pag/data/product/MaterialMigrationFixture.kt)
- [data/src/androidTest/kotlin/org/pag/data/product/MaterialStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/MaterialStorageTest.kt)
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
- [app/src/androidTest/kotlin/org/pag/prototype/P8ExpenseProbe.kt](../app/src/androidTest/kotlin/org/pag/prototype/P8ExpenseProbe.kt)
- [app/src/main/kotlin/org/pag/prototype/product/ExpenseModel.kt](../app/src/main/kotlin/org/pag/prototype/product/ExpenseModel.kt)
- [app/src/main/kotlin/org/pag/prototype/product/ExpenseScreen.kt](../app/src/main/kotlin/org/pag/prototype/product/ExpenseScreen.kt)
- [app/src/test/kotlin/org/pag/prototype/product/ExpenseModelTest.kt](../app/src/test/kotlin/org/pag/prototype/product/ExpenseModelTest.kt)
- [app/src/test/kotlin/org/pag/prototype/product/ExpenseWorkspaceTest.kt](../app/src/test/kotlin/org/pag/prototype/product/ExpenseWorkspaceTest.kt)
- [data/schemas/org.pag.data.product.ActionDatabase/9.json](../data/schemas/org.pag.data.product.ActionDatabase/9.json)
- [data/src/androidTest/kotlin/org/pag/data/product/ExpenseStorageTest.kt](../data/src/androidTest/kotlin/org/pag/data/product/ExpenseStorageTest.kt)
- [data/src/main/kotlin/org/pag/data/product/ExpenseDatabase.kt](../data/src/main/kotlin/org/pag/data/product/ExpenseDatabase.kt)
- [data/src/main/kotlin/org/pag/data/product/ProductExpenseRepository.kt](../data/src/main/kotlin/org/pag/data/product/ProductExpenseRepository.kt)
- [data/src/test/kotlin/org/pag/data/product/EuroMoneyTest.kt](../data/src/test/kotlin/org/pag/data/product/EuroMoneyTest.kt)
- [data/src/test/kotlin/org/pag/data/product/ExpenseProjectionTest.kt](../data/src/test/kotlin/org/pag/data/product/ExpenseProjectionTest.kt)
- [data/src/test/kotlin/org/pag/data/product/ExpenseRulesTest.kt](../data/src/test/kotlin/org/pag/data/product/ExpenseRulesTest.kt)
- [docs/product-p8-costs.md](product-p8-costs.md)
- [domain/src/main/kotlin/org/pag/domain/Expenses.kt](../domain/src/main/kotlin/org/pag/domain/Expenses.kt)
