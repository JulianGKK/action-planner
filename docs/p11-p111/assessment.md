# §111 – Größenstrategie und reale Android-Speicherkalibrierung

**BLOCKED – Entscheidung über großen produktiven Navigations-/Detailpfad ausstehend; keine #33-Abnahme, kein Merge/Release.** Basis `70ffdbaf1fe1678418ccb74af8f6b9ee6ba86029`, Branch `p11-performance-optimization`.

## Gegenstand und unveränderte Regeln

#37 ist unabhängig im app-eigenen Scope abgeschlossen. Dieser Auftrag prüft Größen und reale Ressourcen, kein neuer allgemeiner Owneraudit. R8 = 8.388.608 B und T16 = 16.777.216 B bleiben unverändert. `reuseEnabled=false`, `ramReuseEnabled=false`; keine automatische Readmission eines exponierten Cores. Keine Änderung an Produktions-Session, Keys, Schema, Writer/CAS, Kryptografie, Cacheautorität oder fachlichen Textlimits.

Die bisherige Änderung betrifft ausschließlich Android-Testmanifest/-proben, P77-/Debug-Testfixtures und Messwerkzeuge. Keine Änderung in `app/src/main`, `data/src/main` oder `domain`.

## Messvertrag

* `measure.py`: strikt isoliertes Gerät RFCT1186YRJ und Paket `org.pag.prototype.a.p77runtime`; APK-/Test-APK-SHA pro Fall, API/Builddetails, UTC und monotone Zeit, geschützte Paketinventare, eigene Fixturekontrolle.
* `P111MemorySampler`: ungefähr alle 500 ms tatsächlicher Coordinatorzustand/Generation, Ledger, Materialisierungszähler, ART used/committed/max, Native allocated/size/free, PSS/Dirty/Clean/Teilkategorien, FD-/erkennbarer Fixture-/Window-/Graphics-FD, approximative ART-GC-Zähler/-Zeiten und Allokationszähler. Main-Queue-Latenz, UIzustand und IME-Sichtbarkeit werden getrennt mit eigenem Beobachtungszeitpunkt erfasst.
* Etwa einmal pro Sekunde externe `dumpsys meminfo`- und `/proc/<pid>/status`-Samples; periodisch `smaps_rollup`. Eigene PID vor jedem Zugriff prüfen, nie eine wiederverwendete fremde PID verfolgen. Eigene GC-/Prozesslogs werden PID-gefiltert erfasst. Ein sichtbarer Fremd-IME-Prozess wird separat bezeichnet, nicht zum App-RAM addiert.
* Ein externes PSS-Sample und ein Coordinatorcheck sind nicht atomar. Das externe Manifest nennt ausdrücklich den zuletzt tatsächlich beobachteten Sessionzustand und dessen Zeit; ungemessene Zwischenzustände/Peaks werden nicht ergänzt. Inprozess-Samples enthalten Beginn und Ende des Messfensters. Das Rawlabel `NO_RUNTIME` vor der Bindung bedeutet **Session der Messprobe noch nicht zugeordnet**, nicht bewiesene Nichtexistenz eines Runtimeobjekts. Diese Setup-Nebendaten werden nicht als sessionzugeordnete Produktmessung verwendet; Runtimeauswertungen beginnen erst mit tatsächlich abgefragtem Coordinatorzustand.
* Sampler, Reflection, JSON, IPC, UiAutomation und Metadatenhalter sind ausdrücklich Test-/Beobachteroverhead im gemessenen Prozess. Die Werte sind keine Deepbytes des Produktgraphs. Keine implizite Subtraktion, keine künstliche GC, kein HPROF-GC und kein GC-/Dispose-/Main-/Lock-Endreceipt.
* Kontrolliertes Parken des kompakten ersten Row-/Kandidatenzustands ist separat gekennzeichnet: echte Ressourcen in einem diagnostisch verlängerten Zustand, kein natürlicher Latenz- oder Peak-Nachweis. Autorität und Fristen laufen normal weiter.

Die offizielle [Debug-Referenz](https://developer.android.com/reference/android/os/Debug#getRuntimeStats()) bezeichnet Runtime-Statistiken als approximativ; GC-Zeiten sind Millisekunden. [Androids PSS-Dokumentation](https://developer.android.com/tools/dumpsys#meminfo) beschreibt anteilige geteilte Prozessseiten. Weder PSS noch Heap-Allocatorzähler sind die app-eigene Engineeringquote.

## Fixtures und Pfade

`P111FixtureSpec` definiert 20/40, 100/250, 200/1000 mit 6/32/100 Rollen, 5.000 bzw. 128.000 gespeicherten Events je Aktion. Alle drei Statusbestände behalten ihre vollständige Chronik. Gerade Personen haben in den belegten Fixtures zwei geplante Rollen mit Hauptrolle, ungerade einen leeren Plan. Unterschiedlich lange Mehrbyte-/Surrogattexte, gültige bestehende Task-/Protocol-/Memory-Textgrenzen, Material/Kosten/Protokolle/Memory und Schema-12-Revisionen sind enthalten. Eine gesonderte einzelne lange Person bleibt fachlich gültig; ihr tatsächlicher Read wird separat beobachtet.

Legacy-Events über dem bestehenden 5.000-Neuevent-Writerlimit bleiben lesbar. Kein Trimmen, keine neue fachliche Begrenzung. Core und Fullview laufen in getrennten frischen Testprozessen/Originalrecords. Ein verweigerter Fullview zählt nicht als gescheiterter kompakter Core. Der Kompaktlauf prüft begrenzte Karten, exakte Gesamtzahlen und anschließend die gesperrte Fullview-Readmission. Der Fullview prüft sichtbaren Vollbestand oder typisierte Refusal vor DTO sowie explizite 32er-Historyseiten, ohne Vollchronik im Graphen.

## SQLCipher: zunächst belegte Ressourcengrenze

Version unverändert `sqlcipher-android 4.19.1`. Die lokalen [Window-](sqlcipher-cursorwindow-bytecode.txt) und [Cursor-Bytecodes](sqlcipher-cursor-bytecode.txt) belegen einen Java-Anfangswert von 16.384 B. **Dieser Wert ist kein nativer Maximalwert.** Im versionsgenauen [nativen Quelltext](https://raw.githubusercontent.com/sqlcipher/sqlcipher-android/v4.19.1/sqlcipher/src/main/jni/sqlcipher/CursorWindow.cpp) wächst die Allocation zunächst auf 2 MiB und dann weiter durch Verdoppelung. Deshalb heißen die Diagnostikfelder ausdrücklich `java_window_initial_size_*`; sie behaupten keine aktuelle native Allocation. Tatsächliche Native-/PSS-Samples bleiben separat. Die versionsgenauen C++-Windows verwenden `malloc/realloc/free`; ein FD-Suchtreffer von null bedeutet deshalb nicht null Windows oder null Nativekosten. Die erfassten Room-Konfigurationen (page/cache/temp-store) und bekannten Pointer-/Cursor-/Window-Enden werden im Rohbeleg ausgewiesen; sie beweisen keinen globalen Native-Heap-Bound oder physischen Wipe.

## Bislang beobachtete Testkorrekturen

* Build 1: neue Instrumentation war noch nicht im Testmanifest registriert; kein Produkt-/Kapazitätsbeleg.
* Build 2: falscher Test-Enumname `FUNCTIONS` statt `ROLES`; korrigiert, Buildfehler archiviert.
* Erste kleine Fullview: Daten sichtbar, aber der Test rief den explizit notwendigen Chronik-Ladebefehl nicht auf. Korrigierter Nachlauf mit zwei begrenzten Seiten positiv.
* Erste belegte mittlere Fixture: Abbruch bereits vor Runtimeconstruction. Der Seed setzte eine Hauptrolle vor den dazugehörigen `person_planned_roles`-FK-Eintrag; Reihenfolge korrigiert. Im ersten Crashlauf wurde kein eigener Stack gespeichert; Folgeproben besitzen zusätzlich einen Async-Fehlerstream. Keine Datenbank-/Fachregel gelockert.
* Zwei spätere Versuche wurden durch USB-/ADB-Abbrüche unterbrochen. Rawdaten und fehlende Abschlussquittierungen bleiben getrennt; sie werden nicht als vollständiger Kapazitätsfall gezählt. Kabel/Port wurden auf Rückfrage vom Nutzer geprüft, danach lief die Matrix stabil weiter.
* Die erste Unicode-Gegenprobe zählte im Diagnosefeld zunächst auch die METADATA-Phase als Payloadrow. Der korrigierte Zähler erfasst nur PEOPLE_ROW/TASK_ROW; die typisierte Budgetverweigerung hat tatsächlich null Payloadrow-Callbacks. Die alte Assertion bleibt ein fehlgeschlagener Probe-/Counterbeleg, kein behaupteter Producerverstoß.
* Mittlerer Fullview: Der Test prüfte die noch initial leeren Idleflags vor Abschluss des Action-/Workspacecallbacks. Der Hintergrundpfad hatte tatsächlich bereits eine R-/T-überschreitende Refusal ohne DTO. Die Probe wartet nun auf `detailLoading`, `personsLoading`, Workspaceabschluss und tatsächlichen Content/Fehlerzustand. Der ursprüngliche Lauf bleibt ein fehlgeschlagener Treiberbeleg.

Die kontinuierliche Diagnose liest keine nativen Windowzeilen während Worker-Reset/Close: `nativeState(false)` liefert dafür ausdrücklich `-1` (nicht erhoben). `getNumRows()` wird nur im kontrolliert geparkten oder bereits beendeten Scope verwendet. Der reine Java-Anfangswert und beobachtete Pointerflags sind keine atomare Native-Livenessgarantie.


## Größenbefund aus den abgeschlossenen Kapazitätsläufen

Die vollständigen Einzelwerte, APK-Hashes und Rohverweise stehen in [capacity-matrix.md](capacity-matrix.md), [capacity.csv](capacity.csv) und den jeweiligen `runs/*/manifest.json`. R/T bei einer Verweigerung sind **angefragte Kosten**, keine konsumierten Payloadkosten.

| Bestand | Kompakter Erstkern R / lokale T (B) | Fullview | Bewertung |
|---|---:|---|---|
| 20 Personen / 40 Tasks, 6 Rollen, kurze variable Unicode-Texte | 1.595.155 / 6.463.024 | Zugelassen; sichtbarer Bestand und zwei explizite 32er-Historyseiten | Zulässig im getesteten Umfang |
| 100 / 250, 6 Rollen | 1.776.339 / 7.187.760 | 9.292.212 / 30.855.720 angefragt, vor DTO verweigert | Kompakter Erstkern zulässig; Fullview kontrolliert verweigert |
| 200 / 1.000, 6 Rollen | 1.776.339 / 7.187.760 | 25.525.812 / 89.634.360 angefragt, vor DTO verweigert | Pfade getrennt bewerten |
| 200 / 1.000, 32 Rollen | 2.039.307 / 8.399.768 | Kontrolliert verweigert | Kompakter Erstkern zulässig |
| 200 / 1.000, 100 Rollen | 2.716.851 / 11.533.000 | Kontrolliert verweigert | Kompakter Erstkern zulässig |
| 200 / 1.000, lange Texte, 128.000 Events, 6 Rollen | 2.212.299 / 8.979.984 | Kontrolliert verweigert | Kompakter Erstkern zulässig |
| Entsprechend 32 Rollen | 2.606.307 / 10.925.816 | Kontrolliert verweigert | Kompakter Erstkern zulässig |
| Entsprechend 100 Rollen | 3.626.571 / 15.978.280 | Kontrolliert verweigert | Kompakter Erstkern zulässig; T16-Rest 798.936 B |
| 20 / 40, Unicode-Faktor 256 | 5.991.115 / 24.928.144 angefragt | Ebenfalls T16-verweigert | Kontrollierte Refusal; null kompakte Payloadrow-Callbacks |
| 20 / 40, eine besonders lange gültige Person | 4.544.315 / 18.259.664 angefragt | 6.425.140 / 17.080.552 angefragt | Beide vor Payload verweigert; kein behaupteter nativer Windowfehler |

Die identischen Text-/Rollenbestände mit 5.000 und 128.000 Events ergeben dieselben kompakten Receipts. Die gespeicherte Vollchronik wird nicht zur app-eigenen Payloadliste. Dies ersetzt keine Native-Messung: tatsächliche Native-/PSS-Werte stehen getrennt in den Rohsamples. Kein stilles Abschneiden: Gesamtzahlen sind exakt, Karten bewusst auf die bestehende kompakte Projektion begrenzt und die Vollchronik bleibt separat.

Die Kapazitätsproben beweisen zunächst den **kompakten Erstkern**, nicht eine freigeschaltete schnelle Navigation durch alle 200/1.000 Datensätze. Der Versuch, aus dem exponierten Core in einen vollständigen zweiten Graphen zu wechseln, wird weiterhin `OWNER_UNCONFIRMED`. Weder `reuseEnabled` noch eine zweite Admission werden für die Messung aktiviert.

## Native-Enden und Sicherheitsregressionen

Der aktualisierte [Ressourcenlauf](runs/native-repeat_boundary-schema12-retry1/instrumentation.txt) schließt insgesamt 560/560 Statements, Cursors und Windows, acht Room-/Driver-/Readtransaktionsscopes sowie 16 Native-Connection-/Pool-Enden nachvollziehbar ab. Drei vollständige 200-Personen-/1.000-Task-Batches verwenden begrenzte Readbatches. Die Testpaddingrechnung musste den seit §100 hinzugekommenen Schema-12-`revisionScratch` berücksichtigen; ausschließlich die Testrechnung wurde korrigiert. R_exact = 8.388.608 B und T_exact = 16.777.216 B werden zugelassen, die jeweils überschreitende Gegenprobe verweigert vor dem Materializer. Die [Fullview-Grenzproben](matrix-native-2.log) prüfen separat exakte Datengrenzen und +1 Byte; exakt volle Datengrenze ist keine Zusage, dass zusätzliche UI-Produzenten ebenfalls passen.

Lock, Cancel, SQL-Fehler, unbekanntes Cleanup und Budgetverweigerung wurden isoliert gemessen. Bestätigte bekannte Ressourcenenden sind konkrete lokale Enden; insbesondere `cleanup_unknown` bleibt bei unbestätigtem Ende fail-closed. Die nachfolgend niedrigeren Heap-/PSS-Samples beweisen weder Freigabe aller App-/Frameworkreferenzen noch physischen Wipe. Nach Lock können alte app-eigene Kosten konservativ weiter bestehen.

Die historischen P104-Repository-`cas_aba`-Treiberaufrufe sind mit dem inzwischen geschlossenen Originalownervertrag nicht ohne Weiteres wiederholbar: Der zweite `getPerson` nach einem exponierten Listenread liefert eine typisierte Rejection; der alte Test castet sie in `Value`. [Fehlversuch](runs/security-cas-cas_aba/instrumentation.txt) bleibt rot und wird nicht als gelungener produktiver Editorlauf ausgegeben. Keine Wiederzulassung/Autoritätslockerung zur Reparatur dieser alten Probe. Die aktuelle produktive STALE-/Reloadprobe besteht separat. Der [native SQLCipher-Revisionstest](runs/security-cas-native_revisions/instrumentation.txt) bestätigt fünf Revisionstypen mit ABA-STALE, leeren/erneut belegten Rollenplanheadern, festem Wallclockwert und genau einem TaskEvent je erfolgreichem Übergang. Host-CAS-/Atomizitätsbelege sind zusätzlich archiviert.

Die ausgewählten Hostläufe umfassen 509 plus 52 Tests ohne Fehler/Fehlschlag/Skip ([Hostsummary](host-summary.json), [Securitysummary](host-security-summary.json)); Roh-XML und Gradlelogs sind vorhanden. Dies ist eine ausgewählte Regression, keine Behauptung vollständiger #39-Abnahme.

## Produktions-/Testdiff und Reproduktion

Neue Proben: `P111CapacityProbe` (Room/SQLCipher/Core/Fullview, sichtbarer Content oder typisierte Refusal) und `P111MemorySampler` (skalare Prozess-/Session-/Ledgerdiagnostik). Bestehende P100/P101/P102/P104/P107/P109/P110-Proben erhalten ausschließlich opt-in `p111`-Messung. P110 ergänzt lange reale Unicode-Formulareingaben, COMPLETED-Übersicht sowie Fortschritt/CPU/GC/Sessiondiagnose, ohne Zeitlimits oder Sessionregeln zu verlängern. P83 liest Windowzeilen ausschließlich an kontrollierten Park-/Endpunkten, erfasst tatsächliche SQLitekonfiguration und zählt Payloadrows getrennt von Metadaten. P86 erweitert die vorhandene diagnostische DB-Hülle auf Produktrepositories, ohne eine neue Autorität einzuführen.

`P111FixtureSpec`/`seedP111` erzeugen die beschriebenen synthetischen Bestände vor der Runtime; die Harness lässt bestehende Fixtures unverändert. Die P77-Launchconfiguration hält beide Reuseflags ausdrücklich aus. Produktquellpfade außerhalb Debug/P77/AndroidTest bleiben unverändert. Keine neue Bibliothek und kein Netzwerkpfad in der App.

Reproduktion vom Repositoryroot: `./gradlew :app:assembleP77runtime :app:assembleP77runtimeAndroidTest -PpagTestBuildType=p77runtime --offline`, anschließend nach gesichert erfolgreichem Build `python3 docs/p11-p111/measure.py install <Buildnummer>` mit passendem archivierten `build-<N>.log`; `matrix.py capacity|ui|native` bzw. individuelle `measure.py run <neue-ID> <Familie> --arg ...`. Die Matrix überschreibt keine Rohdaten und stoppt an roten Fällen. Bereits vorhandene fehlgeschlagene IDs werden ausdrücklich analysiert, nicht still als grün übersprungen. Deshalb stehen manuelle Fortsetzungen und gezielte Wiederholungen zusätzlich in eigenen Logs. Alle APK-/Test-APK-Hashes sind pro Fall im Manifest gebunden; wegen iterativer Testdiagnostik existieren mehrere Debug-APK-Versionen, keine Behauptung eines identischen Binärstands über sämtliche Läufe.

Gerät: Samsung SM-M526BR, RFCT1186YRJ, Android 13/API 33, Securitypatch 2025-05-01. **P77-Authentisierung und Nutzdaten sind synthetisch.** Gerätedetails und Signer stehen in `install-*.json`. Build 13: Target SHA-256 `1cf7f812542187208b6f527dc882b4064d8fc14ab64c08c243bc416f0761c4f4`, Test SHA-256 `1e86db1e9d751ef4b0927475453d4fc1f83392a7b8aef0777f49b2263ae2946d`. Keine Produktions-APKinstallation oder Reset der geschützten Pakete.

Die optionalen `initialArea`-Proben setzen **nur den Testanfangsviewport vor erster Payloadpublikation** auf PERSONS/TASKS/FUNCTIONS. Danach rendern die vorhandenen produktiven kompakten Widgets. Diese Proben schalten weder normale Bereichsnavigation noch Reuse an. Ihre sichtbaren Zeilen ergänzen den SQLCipher-/Composekontext, ersetzen aber keinen normalen Navigations- oder Touch→OnDraw-Nachweis.

## Gemessene Prozesslast und Ledgervergleich

Die [Phasentabelle](phases.csv) enthält getrennte Zustände vor/während/nach Materialisierung und Lock. Folgende Werte sind **beobachtete Maximalsamples der genannten Phase**, keine geschätzten ungemessenen Spitzen und keine Budgetkosten. ART/Native in Bytes, PSS in KiB. Das Phase-Label ist ein Testmarker; der daneben tatsächliche `session`-Wert ist maßgeblich (z.B. kann der alte Marker `BOUND_LOCKED` während späterer READY-Samples bestehen bleiben).

| Rohfall / Phase / tatsächlich READY | ART used | Native allocated | PSS | FD |
|---|---:|---:|---:|---:|
| consumer-repeat-active / REPEAT | 8.321.872 | 11.362.576 | 160.909 | 139 |
| consumer-repeat-preparation / REPEAT | 8.452.592 | 10.793.728 | 160.134 | 140 |
| consumer-repeat-completed / COMPOSE_PROGRESS (Detail) | 8.577.440 | 12.369.168 | 157.139 | 139 |
| consumer-material-input / MATERIAL_INPUT | 15.539.128 | 11.454.720 | 164.376 | 139 |
| large100-128k-core-r1 / CORE_MATERIALIZING_PARKED | 6.894.128 | 15.818.128 | 150.085 | 138 |
| derselbe Fall / CORE_VISIBLE | 6.791.488 | 10.811.328 | 150.550 | 136 |

Beispiel Originalowner: ACTIVE wiederholt 100 → 1.000 mit unveränderter Original-Liste/-Zeile: R 3.353.376 → 3.832.520, lokale T 6.823.188 → 7.302.332 B. PREPARATION entsprechend R 2.906.812 → 2.960.884, lokale T 5.624.032 → 5.678.104 B. COMPLETED-Detail bleibt R 1.963.704 / lokale T 4.226.928 B. 100 tatsächliche Materialtexteingaben mit alten gehaltenen Entwürfen: R 4.975.851 → 5.299.256 B; nach bestätigtem Commit/angezeigter neuer Version R 7.266.540 / lokale T 10.813.236 B. Sichtbare Ergebnisse und Receipts sind in den jeweiligen `instrumentation.txt` enthalten. Die globale Transition-Exklusivreservation von 16.777.216 B ist vom lokalen Bedarf getrennt.

Nach Lock/Cleanup beim ACTIVE-Lastfall: beobachtetes ART-Maximum 6.946.144 B, Native 6.407.456 B, PSS 112.410 KiB, FD 103 in drei Samples. Das sind ausschließlich Nachbeobachtungen, keine Referenzfreigabe. Beim großen 100-Rollen-Core bleiben die Testaliases ausdrücklich über Lock gehalten; sein späteres Cleanup-PSS-Maximum 105.765 KiB ist ebenfalls kein Endbeweis.

Ein Sicherheitskontrolllauf `security-writer-leadership` beobachtet bis 76.150.200 B ART used bzw. 216.429 KiB Gesamt-PSS über den gesamten Lauf; die bestehende Leadershipkonfiguration enthält Argon2id mit `memoryKiB=65536`. Diese Operation wird separat ausgewiesen: kein Workspace-Payloadledger und kein aus Prozesssummen beweisbarer KDF-Deepbyteswert. Produktionskryptografie wurde nicht geändert. Entsprechende Prozesswerte fehlen nicht stillschweigend in der Kalibrierung.

GC-Zähleranstiege sind in [gc-observed-intervals.csv](gc-observed-intervals.csv) mit Beginn/Ende des tatsächlich beobachteten Intervalls und Sessionzuständen abgelegt. Die GC-Zeitdifferenz ist eine approximative kumulative Laufzeitdifferenz, keine exakt lokalisierte Einzelpause. Der COMPLETED-Detailtreiber erreicht 1.000 tatsächliche Kompositionspulse in 60,693 s bei 34.571 ms Main-CPU seit Messbeginn; kumulativ angezeigte GC-Zeit 997 ms / Blocking-GC-Zeit 1.007 ms sind ART-Zähler, nicht OnDraw-Latenz. Kein erzwungener GC. Leere PID-Logcatdateien werden nicht als fehlende GC-Aktivität interpretiert; RuntimeStats liefern die beobachtete Evidenz.

## Ursprüngliche #33-Akzeptanzmatrix

Historische 8+8-Formulierungen im Issue werden durch die ausdrücklich beschlossene Grenze **R8/T16 und Option 2** ersetzt, nicht durch eine weitere Budgetänderung.

| Ursprüngliches Kriterium | Bestand → Produktpfad → Ledger → Prozessmessung → Cleanup/Refusal | Stand |
|---|---|---|
| Tatsächlich notwendige Kernprojektion auflisten | Bestehender Core: Action, exakte Summaries, bis 32 Personen-/Taskkarten, vollständiger budgetierter Rollenkatalog; Eventchronik separat. Aktuelle P111-Kapazitätsereignisse bestätigen exakte Gesamtzahlen. | PASS im beschriebenen Projektionsumfang |
| Kompakte Übersicht + explizite vollständige Historie mit Paging/Semantik/Sicherheitsgates | Große kompakte Erstkerne auf echtem SQLCipher; kleine Fullview mit expliziten 32er-Historyseiten, alten gehaltenen Seiten und Lock. Keine simultane Vollchronik, keine falsche EMPTY. Android-UI-Latenz/Schnellnavigation separat. | PASS für Read-/Pagingpfade; #38 offen |
| Kleine/mittlere/große Aktionen, Unicode, Events, Forms/Compose | 20/40, 100/250, 200/1000; 6/32/100 Rollen; 5k/128k und gematchte lange Texte; kontrollierte übergroße Unicode-/Einzeltextrefusal. Wiederholung/Forms und offene Belastungsfälle separat unten. | Größenmatrix PASS; konkrete UI-Stressgrenzen bleiben ausdrücklich offen |
| Engineering gegen ART/Native/Compose/GC kalibrieren; SQLCipherbuffer dokumentieren | R/T je Fall, einzelne tatsächliche Samples und GC-Intervalle, Mainqueue/Kompositionsfortschritt, getrennte Native-Quellenanalyse und bekannte Ressourcenenden. Keine exakte native Höchstkapazität des dynamischen Windows erfunden. | PASS als empirische Kalibrierung im gemessenen Umfang; kein physischer Bound |
| Begründete Lösung schneller Navigation großer Bestände oder konkrete Entscheidung | Erstkern 200/1000 passt; Vollansicht bereits 100/250 verweigert; exponierter Core darf keine zweite Admission erhalten. Der `reuseEnabled=false`-Produktpfad beweist keine bereichsübergreifende kompakte Navigation. | BLOCKED: siehe konkrete Entscheidung |
| Keine unbeschränkten Fallbacks/Budgeterhöhung/Klartextpersistenz | Kein Main-/Domain-/Schema-/Cryptochange; exakte/+1-Refusals, kein zweiter Graph, kein Payload bei Überschreitung, geschützte Paket-/Fixturekontrollen je abgeschlossenem Fall. Rohlogs enthalten ausschließlich synthetische Daten/Metadaten. | PASS im Testscope |

## Konkrete Kapazitätsentscheidung für das unabhängige Review

Der kleinste verbleibende Größenvertrag ist **die zulässige produktive Navigation großer Aktionen, deren Fullview bereits vor Materialisierung verweigert wird**. 200/1.000 ist als kompakter Erstkern auf Android tragfähig; daraus folgt keine vollständig bedienbare große Aktion. Bei ausgeschaltetem Reuse ist der heutige Wechsel vom exponierten Core zur Vollansicht absichtlich gesperrt. Die bestehende Option A für Bearbeiten/Speichern/STALE bleibt unverändert verbindlich; eine bloß lesende oder eingeschränkte Ersatzfunktion wird nicht eingeführt.

Entscheidungsvorlage: Soll für große Aktionen ein expliziter **begrenzter Personen-/Task-/Rollen-Seiten- und Detailpfad unter dem bestehenden ursprünglichen Coreowner** als verbindlicher Produktpfad umgesetzt werden, einschließlich Edit-/Expected-/Membership-Folgeprojektionen und kontrollierter Refusal bei weiter gehaltenen alten Seiten? Der gemeinsame R8/T16-Vertrag und abgeschlossene #37 bleiben Voraussetzung; kein zweiter Graph, kein neuer Budgetowner, keine automatische Core-Readmission und keine neue Sessionautorität. Die Messung begründet diesen Umfang, implementiert aber keine solche größere Architekturänderung im Kalibrierungsauftrag. Eine Budgeterhöhung oder neues fachliches Text-/Rollenkataloglimit ist nicht nötig, um den gemessenen Erstkern zuzulassen, und wird nicht vorgeschlagen.

Schnellnavigation/Touch→Content-OnDraw einschließlich tatsächlicher 200/1.000-Seiten-/Detailnavigation bleibt separat in #38 nachzuweisen. Ein Timeoutaufschub ist kein Performancefix. #39 bleibt für reale DeviceCredential-/Keystore-/Sicherheitsabnahme getrennt. Kein Merge, kein Release, kein RAM-Reuse-GO aus diesen Messungen.

Unter der Mess-/UiAutomationlast beobachtete Main-Heartbeat-Verzögerungen bis 838,78 ms (ACTIVE), 819,07 ms (PREPARATION) und 1.815,01 ms (Materialinput) sind in den Samples enthalten. Dieser Heartbeat ist kein Touch→OnDraw-Instrument und seine Wartezeit kann Phasen überschreiten. Daraus folgt weder eine exakte UI-Einzelinteraktion noch eine isolierte GC-Ursache. Über die READY-Samples dieser Fälle steigen approximative GC-Zähler um 122 / 112 / 55. UI-, Heap- und Testoverhead werden nicht als null behandelt oder von gemessenen Summen abgezogen.

## Lange reale Eingaben und gehaltene Entwürfe

[consumer-long-role-input](runs/consumer-long-role-input/instrumentation.txt) sendet 100 tatsächliche Accessibility-SET_TEXT-Eingaben mit wachsenden Mehrbyte-/Surrogattexten. Die ersten 99 werden angenommen und ihre tatsächlichen alten App-Strings im Test gehalten. Versuch 100 (12.819 UTF-16-Einheiten) wird kontrolliert verweigert; der bisherige Entwurf bleibt identisch, kein Commit wird ausgelöst. R = **8.388.317 B**, verbleibend **291 B**; lokale T = 11.105.537 B. Das ist eine Budgetverweigerung unter den gehaltenen Generationen, kein neues fachliches Rollenname-Limit.

Dieser Fall beobachtet 10.198.784 B ART used, 17.192.384 B Native allocated, 179.042 KiB Gesamt-PSS und bis 144 FD über den Lauf. Die Kategorie `summary.graphics` erreicht tatsächlich beobachtete 45.712 KiB. Die drei sichtbaren Fremd-IME-Meminfo-Samples liegen getrennt als `foreign-ime-*.txt` vor. Dies zeigt gerade, warum Engineered-R8 nicht als Renderer-/IME-/Native-Gesamtgrenze ausgegeben werden darf. Ein Teil dieser Samples ist nach Lock; tatsächliche Zustände und Phasen sind einzeln gespeichert. Kein Frameworkkosten-Nullansatz, keine Freigabeannahme durch Lock.

Der [Formularnachlauf](runs/consumer-form-repeat-diagnostic-retry1/instrumentation.txt) erreicht mit unveränderter strenger Identitätsprüfung 100 und 1.000 Kompositionen, hält 32 alte Entwürfe und zeigt danach neuen Tasktitel samt Revision. Nach 1.000: R 6.395.100 / lokale T 9.864.912 B; nach Commit/Neudarstellung R 7.788.028 / lokale T 11.403.880 B. Der ursprüngliche Fehllauf `consumer-form-repeat` bleibt offen dokumentiert; ohne damalige genaue Divergenzwerte lässt sich seine Ursache nicht nachträglich beweisen. Die zusätzliche Diagnose liefert im Nachlauf keine Divergenz. Kein geänderter Assertionumfang, kein Timeoutaufschub.

Weitere gemessene Produktpfade: `function-person`, `function-active`, `function-plan`, `function-material`, `function-expense` bestätigen Commit → neuer Datenstand/Revision; `function-stale` behält den Entwurf bis zum bewussten Reload. PREPARATION-/COMPLETED-Navigation, Lock vor Detail, verlorene und doppelte Maincallbacks und UNKNOWN werden in eigenen Läufen geprüft. `consumer-role-rename`, `consumer-protocol-create` und `consumer-memory-create` binden neue Folge-/Membershipstände bei bewusst gehaltenem altem Snapshot. Kein zweiter Fullgraph. [Postcommit-UI-Refusal](runs/consumer-postcommit-ui-refusal/instrumentation.txt) erreicht R exakt 8.388.608 B und zeigt nach bestätigtem Commit die korrekte „gespeichert, noch nicht aktualisiert“-Rückmeldung; `no_second_write=true`. Diese UI-/Writerfälle laufen auf kleinen bestehenden Produktfixtures; sie sind keine behauptete große Fullview-Zulassung.

## #38: COMPLETED-Übersicht 1.000 bleibt ein roter Stressfall

Die ersten beiden diagnostischen Läufe (`diagnostic-completed-overview` und `...-control`) erreichten 100 tatsächliche Kompositionen, prüften aber eine nicht in den Viewport gescrollte Person. Der korrigierte Treiber protokolliert den Ausgangszustand (`person_visible_before_reveal=false`), scrollt normal zur Person (`...after_reveal=true`) und startet erst dann die unveränderte Last. Keine Reduktion der Daten, kein Detailwechsel, keine Assertionlockerung und kein Timeoutaufschub.

Der [gesampelte Nachlauf](runs/diagnostic-completed-overview-viewport-retry1/instrumentation.txt) bestätigt sichtbare Übersicht nach 100 tatsächlichen Kompositionen in **32,444 s**, dabei Main-CPU **17.533 ms**. Nach 200: **63,656 s**, Main-CPU **34.044 ms**. Die bestehende zweite Wartefrist endet bei **293** tatsächlich bestätigten Kompositionen, insgesamt **92,810 s** nach Laststart. Tatsächliche Session **READY**, absolute Restlaufzeit **78.777 ms**. Ein Sessionablauf erklärt diesen Abbruch nicht. GC-Zähler beim Timeout: 85, kumulierte GC-Zeit 1.634 ms, Blocking-GC-Zeit 1.647 ms. Diese Werte lokalisieren keine Einzelpause; die erhebliche gemessene Main-CPU-Arbeit und fortlaufende Kompositionsfortschritte sind separat sichtbar.

READY-Maximalsamples im Nachlauf: ART 8.733.088 B, Native allocated 15.553.168 B, PSS 160.481 KiB, FD 139, Mainheartbeat-Verzögerung 345,462 ms. Kein R8-/T16-Refusal und keine Workspace-Neupublikation erklären den Treiberabbruch. Die Probe ist ein Belastungsbefund, kein Touch→Content-OnDraw-Median/p95. `summary.passed=true` mit `expected=DIAGNOSTIC` bedeutet ausschließlich **ordnungsgemäß aufgezeichneter diagnostischer Fehllauf mit Cleanup**, niemals bestandener 1.000er-Produktstresstest.


Der [Nachlauf ohne Sampler](runs/diagnostic-completed-overview-control-viewport-retry1/instrumentation.txt) erreicht 100 sichtbare Kompositionen in 31,468 s (Main-CPU 17.231 ms), 200 in 62,013 s (33.600 ms) und läuft bei **298** nach **91,806 s** ins unveränderte Timeout. Session weiterhin READY, absolute Restlaufzeit 80.565 ms; kumulierte GC-Zeit 223 ms, Blocking-GC-Zeit 0. Ohne Sampler entstehen keine Prozesssamples; solche Werte werden nicht ergänzt. Der Main-Frame-Treiber kann bis zum anschließenden Lock noch einzelne Pulse protokollieren (300 bei 92,415 s); der Timeoutfortschritt bleibt 298. Rückkehr des Testworkers gilt nicht als Ende aller gehaltenen Referenzen. Der ähnliche langsame Verlauf ohne Sampler schließt Beobachteroverhead als **alleinige** Erklärung aus, beweist aber keine vollständige kausale Aufteilung zwischen Compose/Layout/Renderer/CPU/GC. #38 bleibt offen, ohne willkürliche Timeoutverlängerung.

## Abschließende Belegprüfung und Ergebnis

* **75 positive isolierte P77-Fälle:** 36 Kapazität/Viewport, 12 produktive §109-Abläufe, 11 Consumer-/Formularfälle, sechs Ressourcen-, fünf Admission-, ein nativer Schema-12/CAS-, zwei Session-/Security- und zwei P3b-/Dokumentfälle.
* **Vier abgeschlossene diagnostische Fehlläufe**, ausdrücklich keine Produkt-PASS: zwei ursprüngliche Übersichtstreiber mit falschem Ausgangsviewport und zwei korrigierte 1.000er-Übersichtsbelastungen. Sichtbare 100er-Übersicht im korrigierten Scope positiv, 1.000er bleibt rot.
* **Acht fehlgeschlagene und zwei unterbrochene Versuche** separat erhalten: [final-verification.json](final-verification.json). Kein Ersetzen/Löschen negativer Rohdaten. Der alte P104-Zweitreadtreiber bleibt rot; native Schema-12-ABA und aktuelle UI-STALE-Nachweise separat positiv. Der frühere Formularfehllauf ist im strengen Nachlauf nicht reproduziert, Ursache unbewiesen.
* **5.205 tatsächliche Inprozesssamples**, 2.293 beobachtete GC-Zähleranstiegsintervalle; dazu externe Meminfo-/smaps-/Status-/IME-Dateien. Setup/ungebundene Samples und Runtimewerte getrennt. [Messsummary](measurement-summary.json), [Fälle](cases.csv), [Phasen](phases.csv), [UI-Fortschritt](ui-stress.csv), [GC](gc-observed-intervals.csv). Keine Interpolation, keine globale physische Speichergrenze.
* 509 + 52 ausgewählte Hosttests ohne Fehler/Skip, erfolgreiche P77-/Testbuilds bis Build 14. Build 14 hat denselben Targethash wie Build 13; Test-APK SHA-256 **`2ca4a1d610b43b62d57cc6c96169af84f962a30f9e6ef299db102feb8632a760`**. Die Änderung ist ausschließlich der testseitige Ausgangsviewport. [Installbeleg](install-14.json), [Build](build-14.log).
* [verify.py](verify.py) prüft Quellhashes des letzten installierten Standes, per-Fall-APKprovenienz, rohe Abschlussquittierungen und geschützte Pakete. Frühere Summaryversionen ohne `external_errors` werden als fehlendes Feld ausgewiesen, nicht als nachträglich erhobene Messung. Kein `external-error.json` bei positiven Fällen. Nach der Matrix keine eigenen Fixtureverzeichnisse übrig, geschützte Paketinventare unverändert. Das ist kontrolliertes Testfixturecleanup, keine physische Speicherlöschgarantie.

**Entscheidung: BLOCKED.** Empirische Kapazität und Refusals sind dokumentiert; eine vollständige #33-Abnahme wird wegen des konkret fehlenden großen produktiven Navigations-/Detailpfads nicht erklärt. Die oben formulierte Entscheidungsvorlage ist der nächste fachlich/architektonisch abzugrenzende Schritt. Kein neues allgemeines Owneraudit, kein globales Endreceipt, kein stilles Option B. Framework-/Native-/Renderer-/IME-/Accessibility-Spitzen bleiben nach Option 2 separat zu bewerten; die Messreihe ist eine konkrete Gerätekalibrierung und keine Garantie für alle Androidzustände. #38-Stress und #39-Sicherheitsgesamtprüfung bleiben getrennte offene Freigabegrenzen.

**STOPP zum unabhängigen Review. Kein Merge/Release.** Commit-/Push- und Issue-Rücklesebelege werden im Übergabeprotokoll ergänzt.
