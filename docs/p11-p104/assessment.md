# §104 – Schema 12 und Editor-/CAS-/ABA-Abnahme

Stand: 2026-10-24. Basis `ac5a3ea2d2e1b1f824e0fef2eb12253c369b3ba0`, Branch `p11-performance-optimization`. Ausdrücklich genehmigter Umfang S12-P + S12-E gemäß [§103-Vorschlag](../p11-p103/schema12-proposal.md). **Abschlusskandidat: READY_FOR_INDEPENDENT_CLOSURE für #36**, vorbehaltlich unabhängiger Code-/Belegprüfung. Kein Issue geschlossen, kein Merge/Release. Keine Freigabe von #33/#37/#39.

## Schema und Integrität

[Schema-Diff](schema-diff.json): genau fünf zusätzliche INTEGER/NOT NULL/DEFAULT-0-Felder `editRevision` in `persons`, `roles`, `person_role_plans`, `person_operational_states`, `tasks`. Bestehende Indizes und Fremdschlüssel sind unverändert. Export: `data/schemas/org.pag.data.product.ActionDatabase/12.json`; historischer Export 11 und Migrationen bis 11 unverändert.

`EditRevisions.kt / ACTION_MIGRATION_11_12` ergänzt die fünf Spalten und fehlende leere Header je Person. Vorhandene Header/Primärrollen bleiben bestehen. Migration validiert INTEGER-Typ/nichtnegativen Bereich und `foreign_key_check`. `ActionDatabaseAccess` registriert die Migration, akzeptiert Versionen 1–12 und verweigert Migration außerhalb des originalen MAINTENANCE-Scopes. Room öffnet/validiert vor Arbeitsbeginn; zusätzliche skalare Konsistenzprüfung erfolgt vor DAO-Materialisierung. Kein destruktiver Fallback/Downgrade. Revision MAX ist lesbar, weitere Änderung kontrolliert `REVISION_EXHAUSTED`.

| Integritätsbeleg | Tatsächlicher Gegenfall | Ergebnis |
|---|---|---|
| `P104MigrationTest` | echtes SQLite11; Inhalt/IDs/Anzahlen, Primärrolle, fehlender Header, Eventhistorie; alle fünf Backfills; FK | PASS |
| `migratedAndFreshTwelveHaveTheSameRoomColumnContract` | PRAGMA-Spaltenvertrag frisches12 vs migriertes12 aus exportierten Room-Schemas | PASS |
| `missingStableHeader...`, `invalidRevisionAffinity...` | fehlender Header, negative Revision, REAL und TEXT sind kein gültiger Expected-Ausgangsstand | PASS |
| [Gerät: Migration](device-migration-04.txt) | tatsächlich SQLCipher11 → originale Runtime-MAINTENANCE → Room12 → READY; alle alten Spalten per tabellenweisem SHA256-Vergleich, Revision0, alle Header, FK | PASS |
| [Gerät: Commitabbruch](device-migration-abort-01.txt) | alle fünf ALTER-Schritte tatsächlich zurückgekehrt; originalen delegierten Testkontext am Commit widerrufen; Rollback auf11 ohne Teilspalten/Datendrift; Runtime bleibt LOCKED, danach ausdrücklicher erneuter Unlock/Migrationslauf | PASS |
| [Gerät: befüllte P3b/Dokumente](device-migration-data-02.txt) | zuvor über originale Writer eingerichtetes READY-P3b samt sensitiver Identität, Dokumentimport, Primärrolle/Plan und Personen mehrerer Aktionsstatus; Vergleich sämtlicher alten Spalten; nach Migration sensitiver Inhalt gleich entschlüsselt, Dokumentmetadaten erhalten | PASS |

Die P77-Vorbereitung erzeugt nur im eigenen synthetischen Fixture einen echten Legacy11-Bestand: entfernt vor der erneuten Runtime-Autorisierung genau die fünf additiven Felder, stellt die originale Room11-Identität/Version her und lässt gezielt fehlende leere Header zu. **Dies ist kein produktiver Downgrade.** Die alte Spaltenprojektion aller Tabellen wird vor/nach Migration mit Zeilenanzahl und SHA256 verglichen; nur die ausdrücklich ergänzten leeren Header werden aus dieser Gleichheitsprojektion ausgenommen und separat auf Vollständigkeit geprüft. Der befüllte Fall erzeugt P3b-Schlüssel und Dokumente über die echten Runtime-Repositories; kein synthetischer Ciphertext wird als erfolgreicher Entschlüsselungsbeweis ausgegeben. `p104PrepareLegacyWhileLocked` setzt eine LOCKED-Runtime voraus. Fresh12 wird in den anderen P77-Fällen durch den echten Room-Aufbau/Schema-Vertrag geprüft.

Der Commitabbruch ist eine **gezielte Autoritätsinjektion**, kein Prozess-Kill und kein behaupteter Vendor-/Closefehler. Er belegt die wirkliche SQLCipher-/Room-Transaktionsgrenze und verhindert READY; Host-Rollback und expliziter nativer Retry ergänzen ihn. Keine Aussage über Hardwareausfall oder sämtliche denkbaren Crashzeitpunkte.

## Writer-/Reader-/Expected-Vertrag

Alle fünf Domain-/Roommodelle führen eine erforderliche Revision ohne Domain-Lesedefault. Full Expected-Snapshots enthalten sie; `ExpectedPersonRolePlan` friert zusätzlich die vollständige Rollen-ID-Menge und Primärrolle ein. Jede akzeptierte Aktualisierung erhöht per bedingtem SQL genau einmal, auch bei gleichem Zielinhalt; `0..MAX-1` ist schreibbar. Originale Action/Datensatzidentität, voller Expected-Vergleich und genau eine betroffene CAS-Zeile bleiben erforderlich. Revision und Nutzdaten teilen die bestätigte äußere Transaktion. Neue produktive Zeilen setzen0 ausdrücklich. Role-/Task-Delete prüfen Expected und beanspruchen die Revision vor dem Entfernen in derselben Transaktion; alte Löschbestätigungen können nach Rename/ABA nicht löschen.

Person-Create legt seinen leeren Planheader atomar mit an. Planänderung löscht den Header nie: CAS setzt Primärrolle vorübergehend auf NULL und Revision+1, ersetzt Zuordnungen, setzt dann die neue Primärrolle bei der erhöhten Revision. FK-RESTRICT bleibt in jedem Schritt eingehalten; der Zwischenzustand wird nicht publiziert. Ein abschließender SQL-Fehler rollt auch Revision/Zuordnungen zurück. Rollenrename ändert **keine** Planrevision: Planbelegung bleibt durch IDs/Primärrolle definiert; keine neue Katalognamenversionsregel.

Task-Edit bleibt eventlos. Eventkapazitätsprüfung, Task-/Revisionsänderung und genau ein Transition-Event liegen weiterhin im originalen äußeren Commit. STALE, MAX, fachliche Ablehnung und Rollback fügen keinen Event hinzu. Keine automatische UNKNOWN-Wiederholung. `updatedAt` bleibt Wallclock.

Readerpfade: `PersonRow/RoleRow/OperationalRow/TaskRow.model`, `OperationsStore`, `BoundedActiveStore`, `BoundedTaskStore`, `CompactPeopleStore.detail` und `CompactTaskStore.detail` übernehmen die echte gespeicherte Revision. Die reine Personen-/Taskkartenprojektion ist **kein editierbarer Expected-Snapshot** und benötigt keinen erfundenen Revisionswert; Detail/Editor liest das vollständige versionierte Modell. `P104ReaderRevisionTest` setzt alle fünf Revisionen auf7 und prüft die tatsächlichen Readerausgaben. FUNCTIONS-Domainrollen tragen ebenfalls7. Fixtures/Demo/Konstruktoraufrufe wurden an erforderliche Felder angepasst; Android-Datenfixtures erzeugen ihre leeren Header ausdrücklich. Historische niedrigere Migrationstests im separaten Daten-Testpaket wurden nicht als erneut geräteseitig abgenommen ausgegeben; dieses Paket wurde nur kompiliert. Maßgeblich sind die neuen expliziten P77-Migrationsfälle. Historische §103-Rohbelege bleiben erhalten, die damaligen ausführbaren ABA-Gegenfälle sind nun S12-Regressionen.

## R8/T16 und große Fixtures

Zusätzliche numerische Builder-/Row-/Boxing-/Referenzkosten werden vor Materialisierung konservativ gebucht: zusätzliche Objekt-/Payload-Allowances je betroffener Zeile in begrenzten Voll-/Seitenreadern; zusätzlicher Planrevision-Index im Vollreader; einmalige gemeinsame `CoreReadAdmission.revisionScratch`-Reserve. Diese wird genau einmal mit den bereits bestehenden vier Handles, gemeinsamem Presentation-Scratch, Worker-/Main-/Formkosten und beiden vorbereiteten Projektionen addiert. Keine neue Registry oder unabhängige Personen-/Task-/Revisionsfreiquote. Zusätzlicher Long im bestehenden Domain-/Expected-Objekt erzeugt keinen neuen vollständigen Datenroot. Bestehende Objekt-Allowances und zusätzliche konservative Revisionseinheiten gelten gemeinsam; keine Deep-Size-Garantie.

`CoreReadAdmissionBudgetTest`: exakt R8/T16, jeweils +1, alte/neue Daten gemeinsam, alle vier Handles, gemeinsamer Scratch, Overflow und verweigerte Admission bleiben geprüft. `P104ReaderRevisionTest` erfasst pro normalem Fixture **einen** Durchlauf statt neuer Performance-Serie:

| Personen / Tasks / Events | Rollen | Retained | Transition | Einordnung |
|---|---:|---:|---:|---|
| 200 / 1.000 / 5.000 | 6 | 1.663.959 | 6.727.680 | kompakter Reader zugelassen |
| identisch | 32 | 1.892.247 | 7.750.792 | kompakter Reader zugelassen |
| identisch | 100 | 2.485.851 | 10.413.800 | rechnerische Readerzulassung, **keine Produktfreigabe** |

Der unveränderte vollständige Workspace-Ansatz verweigert diese großen Fixtures konservativ; keine Buchung wurde dafür aufgeweicht. Der kleine Vollreader-/Taskreader-Fall beweist die Revisionsweitergabe. Fullview-/Formowner-Integration bleibt #37. Werte sind **Ledger-Engineeringkosten**, keine ART-/Native-/PSS-Obergrenze und keine Kalibrierung von Renderer/IME/Accessibility. Keine neuen Speicherbenchmarks oder 129k-Standardläufe.

## Editor-/UX-Matrix und ursprüngliche #36-Abnahme

| #36-Kriterium / Produktpfad | Tatsächliche Regel und Gegenfall | Test / Rohbeleg | Status |
|---|---|---|---|
| Vollständiges Inventar | Person/Pseudonym; Rollenrename/-delete; Planmenge/Primärrolle; Live-Status/Rolle/Ort; Taskcreate/edit/transition/delete; ephemere Form-/Callbacklebensdauer. Action-/Session-/Generation-/Berechtigungswechsel gegatet. Allgemeiner Action-Inhaltseditor erhält keine zusätzliche, nicht genehmigte sechste Revision. | §103-Inventar plus diese Matrix; produktive fünf Writer/DAO-Dateien, OperationsModel, TaskModel, ProductViewModel | PASS im beauftragten #36-Editorumfang |
| DB-seitiger Konfliktvertrag | Voller ursprünglicher Expected inklusive persistierter Einzelrevision; stabiler Header, bedingtes SQL statt bloßem UI-Key; fremde Identität abgewiesen | CoreWriterIntegrationTest, TaskMutationSqliteTest, P104RevisionTest, P104ConcurrentCasTest | PASS |
| Konkurrierende/stale Editoren, ABA | beide lesen r; zwei echte SQLite-Verbindungen konkurrieren je Revisionsart, genau eine CAS-Zeile1 und eine0; A→B→A, gleicher Inhalt und gleiche Wallclock erlauben alten r nicht erneut | [Host](host-final-13.log), `final-data-xml`; [echte Runtime-CAS](device-cas-02.txt); [fünf native Revisionsarten](device-native-revisions-02.txt) | PASS |
| Entwurf/Navigation/Fokus/Neuladen | Expected wird beim Öffnen eingefroren, Hintergrundrefresh ersetzt ihn nicht. STALE erhält Entwurf und zeigt keinen Speichererfolg. Nur explizites Reload ersetzt Expected/Draft. Ein sichtbarer Hinweis am gemeinsamen Navigationsbereich erklärt das Verwerfen bei Verlassen/Bereichswechsel; die Rückkehr zur Aktionsliste ist bei offenem CAS-Formular als „Entwurf verwerfen“ gekennzeichnet. Expliziter Cancel/Zurück verwirft im vorhandenen Lebenszyklus. Kein Erhalt über beliebige Navigation versprochen. Live-Status erhält erstmals den fehlenden sichtbaren Reload und ursprüngliche Generationbindung. | Host Person-/Role-/Task-/Operations-Editorfälle; [sichtbare Person](device-person-ui-01.txt); [sichtbare Rolle/Plan/Task/Live und Verwerfungshinweis](device-editors-ui-05.txt) | PASS konkret geprüfter Form-/Text-/Reopenvertrag; kein physischer Framework-/IME-Wipebeweis |
| TaskEvent genau einmal | Transition mit Revision+1 und Event in derselben äußeren Tx; STALE/MAX/Limit/Rollback ohne zusätzlichen Event; Edit eventlos | 19 TaskMutationSqliteTest-Fälle, P104RevisionTest; Native-CAS/Revisionsfälle | PASS |
| Lock/Action/Generation/Revoke/UNKNOWN/Callback | originale Writer-/Publicationgates unverändert. Commit vor Lock bleibt Commit; verlorener Main zeigt keinen Erfolg; spätes doppeltes Main kann neuen Writer nicht überschreiben; Lock vor Worker schreibt nicht. Alte Editoren/Reloads werden widerrufen. UNKNOWN terminal. | CoreWriter-/Task-/Editor-Hostfälle; [native Publication](device-publication-01.txt), Person-UI Lock/Reauth | PASS |
| Konflikt-/Reauth-UX, keine unzulässige Klartextpersistenz | deutsche STALE-/MAX-Meldungen; Entwürfe RAM-only im autorisierten bestehenden Formlebenszyklus, kein neues SavedState/Datei/Preferences. Live explizites Neuladen. | sichtbare UI-Fälle und Quellpfade; OperationsModelTest.liveStaleKeepsDraftUntilExplicitReloadAndUsesPersistedRevision / liveReloadDuplicateAndOldSessionCallbacksCannotReplaceDraft | PASS app-kontrollierter Vertrag; Framework-/IME-/Accessibilitykopien separat #39 |

Zusätzliche vollständige Revisions-Gegenfälle: `P104RevisionTest` prüft no-op +1 für alle fünf Arten, MAX-1→MAX und danach kontrollierte Verweigerung, Rollback, Fremdidentität, Task-/Eventatomizität und Fehler erst beim finalen Primärrollenschritt. `P103OperationalAbaTest` ist nun die Regression für den ursprünglichen Same-Wallclock-Gegenbeweis. `CoreWriterIntegrationTest` behält seine fachlichen Duplikat-/IN_USE-/Status-/Referenz-/Task-/Cleanupprüfungen; geänderte Expected-Assertions zählen die tatsächliche Revision, keine verlorenen fachlichen Gegenprüfungen.

`ActiveCoreOwnerTest.fullViewReturnSurfacesAdmissionRefusalAndCannotReplayTheOldCore` bestätigt weiterhin die verweigerte zweite Admission nach unbestätigter Exposition. Keine Ableitung aus leeren Feldern, onDispose/onDraw/GC.

## Testbilanz und Fehlversuche

- [Host-Abnahme](host-final-13.log): **97 Data + 103 App = 200 Fälle, 0 Fehler**, XML `final-data-xml/`, `final-app-xml/`, [Zusammenstellung](host-summary.json). Gezielte Klassen/Methoden im Log, keine Gesamtsuite.
- [Zusatzabnahme](final-extra-17.log): **10 Fälle, 0 Fehler** (`extra-17-data-xml/`); enthält die ergänzten Fälle fehlender Header und finaler Primärrollenfehler. Davon sind8 bereits oben enthalten: keine unbereinigte Aufsummierung über wiederholte Läufe. Weitere passende Readerbelege aus [host-03](host-03.log) / `host-03-xml/` werden übernommen; keine neue breite Wiederholung.
- [Kompatibilitätsabschluss](final-compat-20.log): zwei passende Fälle und aktuelle Androidtest-Kompilation erfolgreich. [Navigationshinweis/App-Hosttests und Build](final-ui-21.log): 102 passende App-Fälle grün.
- [P77-App/Androidtest-Build](final-ui-21.log), [Android-Daten-Testkompilation](build-data-android-19.log) erfolgreich. Nur APKs `org.pag.prototype.a.p77runtime` und `.test` wurden installiert. [APK-/Signernachweis](resume-apks.json), [Cleanup/geschützte Pakete](resume-cleanup.json), Vorher/Nachher-Inventare im selben Verzeichnis.
- Sieben isolierte Szenarien: migration, migration_abort, migration_data, native_revisions, cas_aba, person_ui, publication; zusätzlich der kombinierte editors_ui-Fall. Keine Android-Gesamtsuite. Authentisierung ausdrücklich **synthetische P77-Authentisierung**, kein echter DeviceCredential-Nachweis.

| Fehlversuch | Tatsächliche Ursache / Behebung |
|---|---|
| compile-01/02, build-device-02/07, build-ui-09, build-data-android-18 | notwendige Enum-/DAO-/Konstruktoranpassungen bzw. Fehler in neuen Testhilfen (suspend-Funktion, Grant.cancel, WorkspaceSnapshot.actionId); betroffene Builds anschließend grün |
| host-01/02 | rohe alte Fixture-INSERTs ohne neue Spaltenzahl, alte ABA-Erfolgserwartungen/Planrevisionen; erforderliche Test-/Fixtureanpassung. Keine grünen Aussagen daraus übernommen |
| zusätzlicher Budget-Scratch | anfänglich Payloadbytes ohne Array gebucht, daher zu Recht verweigert; Array ergänzt, gemeinsamer Grenztest grün |
| host-04 | Test erwartete unveränderten Zeitstempel trotz gezielt tickender Testuhr; Revision und wirkliche Zeitstempel getrennt geprüft |
| device-migration-01 | Szenario fehlte in der Teststarter-Zulassungsliste; korrigiert |
| device-migration-02/03 | Legacy-Fixturehelper versehentlich zweimal aufgerufen; Diagnose03 zeigt fehlende bereits entfernte Spalte. Zweiten Aufruf entfernt; echte Migration04 grün. Kein Vendor-Cleanupdefekt |
| check-10/11 | Test unterstellte Zulassung des vollständigen großen/History-Readers; korrekt BUDGET_REFUSED. Verweigerung ausdrücklich beibehalten, kleine vollständige Reader separat geprüft |
| device-editors-ui-01 | noch altes Test-APK: paralleler fehlgeschlagener Hosttask verhinderte neue APK-Paketierung. APK danach separat erfolgreich gebaut/installiert |
| device-editors-ui-02/03 | Taskeditor im Modell, aber UI noch in vorbereitender Übersicht. Vorhandene Navigation nach TASKS und kontrollierte Scrollrichtung ergänzt; editors-ui04 sichtbar grün, abschließend editors-ui05 einschließlich Verwerfungshinweis grün |
| host-extra-15 | Testhilfe `H.write` setzt eine Publication voraus und war für den absichtlichen SQL-Fehler ungeeignet. Auf originale Queue und null Erfolgscallbacks umgestellt; Zusatzabnahme17 grün |

Keine injizierte Ausnahme wird als echter SQLCipher-Vendor-Closefehler ausgegeben. Fehlversuchslogs bleiben erhalten. Kein unerklärter grüner Gesamtabschluss aus bloßen Build- oder Hostresultaten.

## Unveränderte Sicherheitsgrenzen und Abschluss

R8 **8.388.608**, T16 **16.777.216 Bytes**. `reuseEnabled=false`, `ramReuseEnabled=false`, exponierter OutstandingCoreRead weiterhin gesperrt. Keine automatische Readmission/Schnellnavigation, kein zweiter Core-/Payload-/Key-/Sessionowner, keine neue Cacheautorität. Keine Änderung von Writer-BEGIN/COMMITTED/ROLLED_BACK/UNKNOWN, Cleanup-Bestätigung, Sessionfristen, READY-/Revoke-/Berechtigungsregeln, SQLCipher-Konfiguration oder B2-/Room-Cleanup. #35-/§102-Recoveryprotokolle unverändert; befülltes P3b/Dokument-Migrationsszenario nutzt die echten vorhandenen Writer und bestätigtes Cleanup. Framework-/Nativekosten separat gemäß Option2; keine physische Wipe-/Freigabegarantie.

#36 ist im ursprünglichen Editor-/CAS-/ABA-/Konfliktumfang **bereit zur unabhängigen Schließungsprüfung**. Fullview-/Formowner-Retirement #37, Native-/Budgetkalibrierung #33 und Sicherheitsabschluss #39 bleiben eigene offene Grenzen. Issue36/Tracker44 werden mit WIP-Verweis aktualisiert; danach **STOPP für unabhängigen Review. Kein Merge/Release.**
