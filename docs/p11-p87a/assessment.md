# §87a – 5.000 gespeicherte TaskEvents je Aktion

Basis `334f58bcf457cf15c21710ff7da837a9eae1a47f`, Branch
`p11-performance-optimization`, Produktentscheidung / Issue #47.
Stand 2026-10-23: Implementierungs-WIP für unabhängigen Review; kein Merge/Release.

## Änderung

`TaskRules.MAX_TASK_EVENTS_PER_ACTION = 5_000L`. Gezählt werden alle gespeicherten
CREATED, STARTED, COMPLETED, CANCELLED und REOPENED in `task_events` mit derselben
`actionId`, über alle Tasks. Kein lebenslanger Zähler und keine globale Grenze.

Die Inventur der produktiven Event-Inserts ergibt nur `TaskStore` als Quelle:
Task-Anlage → CREATED, Statuswechsel → STARTED/COMPLETED/CANCELLED/REOPENED.
Der neue gemeinsame private `withEvent()`-Pfad prüft nach Status-/Referenz-/CAS-
Validierung `TaskDao.eventCount(actionId)` mit exaktem `SELECT COUNT(*)`.
Die Prüfung steht **vor** dem abhängigen Task-Insert/Update; Task und Event laufen
weiter in derselben bestehenden bestätigten IMMEDIATE-Schreibtransaktion.
Zwei konkurrierende DB-Verbindungen können dadurch nicht beide den letzten Platz
verbrauchen. Keine neue Transaktions-/Writer-/Ownerimplementierung.

COUNT >=5.000 liefert `OperationsFailure.TASK_EVENT_LIMIT_REACHED` als reguläres
Rejected-Ergebnis. Der bestehende Writer bestätigt den Rollback und das Cleanup;
die UI erhält keinen Speichererfolg und wechselt nicht wegen der Grenze nach
BLOCKED. Unsicherer Commit bzw. fehlgeschlagenes Cleanup bleibt unverändert UNKNOWN
bzw. fail-closed; die Grenze überdeckt solche Fehler nicht.

Beide Compose-Taskansichten verwenden dieselbe fachliche Meldung:

> Die Grenze von 5.000 Aufgabenereignissen je Aktion ist erreicht. Neue Aufgaben
> und Statuswechsel sind nicht möglich. Bestehende Aufgaben können weiter gelesen
> und bearbeitet werden.

Der bestehende TaskModel-Rejected-Pfad behält Entwurf/Selektion und erzeugt kein
„Gespeichert“. Titel/Priorität/Zuordnungen/Fälligkeit ändern erzeugt keinen Event
und bleibt nach den sonstigen Regeln erlaubt. Bewusste PREPARATION-Tasklöschung
mit vorhandener FK-Cascade bleibt unverändert; keine automatische Freigabelöschung.
Der Historyreader ist unverändert: Altbestände >5.000 bleiben lesbar, höchstens
32 Events je Seite. Kein Schema-, Trigger-, Index- oder Kryptografieumbau.

Produktdateien: `domain/.../Tasks.kt`, `Operations.kt`; `data/.../TaskDatabase.kt`,
`ProductTaskRepository.kt`; `app/.../TaskScreen.kt`, `ActiveTasks.kt` und der wegen
des erweiterten Enums notwendige Delegate in `OperationsScreen.kt`.
Fachvertrag: [PRODUCT.md](../../PRODUCT.md), [P5a](../product-p5a.md).

## Gezielte Hostprüfungen

**58 verschiedene Tests, 0 Fehler/Skips**, keine Gesamtsuite:

| XML | Anzahl / Nachweis |
|---|---|
| [TaskMutationSqliteTest](TEST-org.pag.data.product.TaskMutationSqliteTest.xml) | 19: 0→1, 4.999→5.000, 5.000/5.001 verweigern Create und alle Eventtypen; keine abhängige Taskänderung; eventloser Edit im Überbestand; zwei Aktionen; konkurrierende Queuezulassung und Retry; echter paralleler Create/Statuswechsel über zwei SQLite-Verbindungen auf derselben synthetischen Datei; Event-SQL-Fehler mit Taskrollback; CAS vor COUNT; Revoke bei COUNT/vor Commit; Cleanupfehler; bestehende UNKNOWN-/Commit-/Session-/Main-Fences. |
| [TaskRulesTest](TEST-org.pag.data.product.TaskRulesTest.xml) | 6: Lifecycle, fachliche Status-/Inputregeln unverändert. |
| [CompactTaskStoreTest](TEST-org.pag.data.product.CompactTaskStoreTest.xml) | 3 gezielte Methoden: neue 5k-Fixture, direkte Seiten 1/63/126, vollständige 128er-Einzelchroniken bei 20 Tasks, volle Boards ohne Eventprefetch. |
| [CompactPeopleStoreTest](TEST-org.pag.data.product.CompactPeopleStoreTest.xml) | 1 gezielter Fall mit angepasster großer Fixture. |
| [P83OverlapBudgetTest](TEST-org.pag.data.product.P83OverlapBudgetTest.xml) | 1 Fixture-Kompatibilitätsfall; bestehende Admission-/Refusalassertions unverändert, keine neue Budgetfreigabe. |
| [TaskModelTest](TEST-org.pag.prototype.product.TaskModelTest.xml) | 28: zusätzlicher Limitfall in PREPARATION/ACTIVE, Entwurf/Selektion bleiben, kein Erfolgsfeedback, eventloser Edit bleibt erfolgreich; bestehende CAS-/Session-/Callbacktests. |

Host: echter TaskStore, bestätigter Transaktionshelper und TaskMutationBoundary
gegen echte SQLite/Schema 11, mit simulierten Session-/Room-/Ressourcen-Enden.
Der neue Konkurrenzfall startet zwei unabhängige SQLite-Verbindungen gleichzeitig:
genau ein Value, genau ein Limit-Rejected, COUNT=5.000. Er beweist keine Android-
Thread-/Keystore-/SQLCipherimplementierung; diese wird separat begrenzt integriert.
Altbestand: alle 5.001 Zeilen im Hostbestand nachgewiesen; produktiver Reader liefert
exakte Gesamtzahl und erste/letzte Seite ohne 5k-Lesecap.

## Android / echte Speicherung und Meldung

`P87TaskEventLimitProbe` verwendet den bestehenden isolierten §86b-P77-Root mit
ungebundenem Compact-Port: echter ProductRuntime, Repositories, Room/SQLCipher,
Session und Compose, synthetische Authentifizierung. Kein Produktionsfallback.
Navigation und Formdaten werden auf Main vorbereitet; Save-/Statusbuttons erhalten
echte UiAutomation-Touchereignisse. Sichtbare Compose-Accessibilitytexte werden
geprüft, keine neue Performance-/Display-Present-/Retirementbehauptung.

`P87TaskEventDiagnostics` beobachtet nur die **tatsächlichen** actionbezogenen
Writer-COUNT-Rückgaben an der bestehenden P77-Statementseam. Keine Zusatzquery,
keine Schlüssel-/Payloadhaltung, kein Authority-/Cacheport. Bestätigte Writes werden
zusätzlich durch erneute Workspace-/Historyreads geprüft.

| Rohbeleg | Umfang |
|---|---|
| [boundary-4999-01](boundary-4999-01.txt) | ANDROID_PASS: PREPARATION und ACTIVE jeweils unabhängig 4.999→5.000, danach Create-/Transition-Fehlermeldung, READY, kein falscher Erfolg, Taskstatus/Taskzahl unverändert; eventloser Edit commitfähig. COUNT je Aktion `[4999, 5000, 5000, 5000]`. |
| [legacy-5001-01](legacy-5001-01.txt) | ANDROID_PASS: PREPARATION und ACTIVE mit je 5.001 Events, dieselben sichtbaren Create-/Transition-Ablehnungen ohne BLOCKED/Erfolg; kein Verlust von Tasks oder History; eventloser Edit commitfähig. COUNT je Aktion `[5001, 5001, 5001]`. |

Kleine isolierte Grenzfixtures: je Aktion 20 Personen / 40 Tasks, übrige Events
konzentriert auf einem Task; lesbare vollständige Typchroniken, keine echten Daten.
Erste/letzte Seite der ausgewählten Chronik bleiben <=32, Gesamtzahl nach letztem
Slot 4.961 bzw. im Altbestand 4.962. Keine 129k-Wiederholung.

## Reguläre Fixtures, Builds und Grenzen

P77 `stress`/`p86-stress32`/`p86-stress100`: unverändert 200 Personen/1.000 Tasks,
jetzt 1.000 CREATED + 4.000 zusätzliche Events = **5.000** pro ACTIVE-Aktion.
Die zugehörigen §83/§85/§86-Runnererwartungen wurden auf 4.001 ausgewählte Events
und 126 Seiten angepasst. Reguläre große ActiveCore-/People-/Task-Hostfixtures
verwenden ebenfalls <=5.000; neue Fixtureguards sichern das ab. Explizite kleine
Legacy-/Negativfixtures mit 5.001 bleiben getrennt. Der historische 129k-Testcode
ist mit `@Ignore` als Sonderfall markiert und nicht ausgewählt/ausgeführt; seine
bisherigen Rohbelege und alle früheren Berichte bleiben unverändert erhalten.

[host-build-03.log](host-build-03.log): P77-/Runner-, Releasebuild und erste gezielte
Hostauswahl PASS. [final-host-build.log](final-host-build.log): endgültige angepasste
Grenz-/Hostfixtures und P77-/Runnerbuild PASS. Frühere Buildfehler sind erhalten:
fehlendes gespeichertes Fixture-Konstruktorargument und noch nicht erweiterter
exhaustiver OperationsFailure-UI-Branch; beide vor den bestandenen Läufen behoben.
Keine erneute Gesamtsuite oder 4.001-Seiten-Geräteserie.

[artifact-check.json](artifact-check.json) / [Prüfskript](verify-artifacts.py):
Schema 11, Crypto, Budget, Owner-/Writer-/Session-/Berechtigungsgrenzen und
Buildkonfiguration gegenüber der Basis unverändert; Release enthält keine neuen
P77-Probeklassen oder Grenzfixtures. Release wurde nur gebaut, nicht installiert.
APK-/Signaturbelege: [resume-apks.json](resume-apks.json).
Beide Androidfälle vollständig bestanden, keine Harnessabbrüche in diesem Slice.
Fixtureroots bereinigt und andere Appinstallationen unverändert:
[resume-cleanup.json](resume-cleanup.json),
[vorher](resume-protected-before.json) / [nachher](resume-protected-after.json).

Reproduktion der gezielten Hostauswahl (die obigen Buildlogs enthalten die
durchgeführten Teilaufrufe; wiederholte Tests werden nicht doppelt gezählt):

```sh
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
  --tests org.pag.data.product.TaskMutationSqliteTest \
  --tests org.pag.data.product.TaskRulesTest \
  --tests org.pag.data.product.CompactTaskStoreTest.directPageEntryReachesMiddleAndEndWithoutPrefixMaterialization \
  --tests org.pag.data.product.CompactTaskStoreTest.selectedDetailIsCompleteAndLongHistoryHasExactTieOrderedCoverage \
  --tests org.pag.data.product.CompactTaskStoreTest.fullTaskBoardsNeverFetchActionHistoryThrough5000Events \
  --tests org.pag.data.product.P83OverlapBudgetTest.realisticSizesUnicodeAndRoleRefusalKeepCompleteCountsAndReasons \
  --tests org.pag.data.product.CompactPeopleStoreTest.allStatusesWithoutFunctionUnusedRolesAndPlannedRolesRemainDistinct \
  :app:testP77runtimeUnitTest --tests org.pag.prototype.product.TaskModelTest \
  :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:assembleRelease \
  --offline --console=plain
```

Android:

```sh
python3 docs/p11-p87a/run-device.py install
python3 docs/p11-p87a/run-device.py run 4999 boundary-4999-01
python3 docs/p11-p87a/run-device.py run 5001 legacy-5001-01
python3 docs/p11-p87a/run-device.py after
python3 docs/p11-p87a/verify-artifacts.py
```

**Offen:** unabhängiger Review von #47. Diese Produktgrenze löst keine der
bekannten Budget-/Owner-/Native-/Performanceblocker #32–#38. WIP pushen,
Issue #47 aktualisieren, danach **STOPP zum unabhängigen Review**.
