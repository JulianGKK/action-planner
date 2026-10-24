# §107 – Produktive Fullview-Vorprüfung und weiter bestehender Formularblocker

Basis `29f1c369d34ad70df803ce9bbe8afc1ba1b14614`, Branch `p11-performance-optimization`.

**BLOCKED für den vollständigen Auftrag / #37; keine Schließungsempfehlung.** Ein funktionierender Teilfix ist implementiert: Ein erster Workspace-/Boardread bei freiem Originalrecord prüft seine SQL-Projektion vor dem ersten Board-DTO und bucht sie identitätsgebunden am selben `OutstandingCoreRead`. Ein materialisierter Graph bleibt auch nach Main-ACK gebucht. Der §106-Runtimewechsel-Timeout ist als Harnessfehler reproduziert, korrigiert und vollständig erfolgreich erneut geprüft.

**Nicht erfüllt:** Vollständige PREPARATION-/Editor-/Reloadbedienung und gemeinsame Vorabzulassung sämtlicher Form-/UI-/Writerkopien. Die konservative Snapshotverpflichtung verweigert Folgereads; der echte Personen-Detailpfad zeigt diese funktionale Einschränkung reproduzierbar. Eine erste budgetierte Übersicht ist keine Abnahme der gesamten PREPARATION-/COMPLETED-Funktion. Ebenso ist die nachfolgend beschriebene Read-Graph-Admission **kein vollständiger Fullview-/Formscope- oder globaler Owner-Endnachweis**. Unregistrierte alte Form-/Callbackzustände dürfen nicht aufgrund eines freien Records als sicher ausgeschlossen gelten.

## Produktcode und Zulassungsvertrag

- [FullviewPreflight](../../data/src/main/kotlin/org/pag/data/product/FullviewPreflight.kt) liest nur skalare COUNT/MAX/SUM-/Revisionmetadaten. Keine Domainzeilen, vollständigen Listen, Titel, Beschreibungen oder BLOB-Payloads werden dafür nach Kotlin übernommen. Einzige feste Textabfrage ist `PRAGMA encoding`.
- [WorkspaceStore/Repository](../../data/src/main/kotlin/org/pag/data/product/ProductWorkspaceRepository.kt) führt Preflight, Originalbooking und einmalige Materialisierung innerhalb **derselben** `db.withReadTransaction` aus. Vorher wird auf dem Ownerthread ein Readticket aus dem vorhandenen Writerport gebunden. Worker prüft dessen Liveness und die ursprüngliche Session/Generation/Requestcancel; Main zusätzlich `isCurrentActiveRead` innerhalb der ursprünglichen Session-Publication. `ProductRuntime` gibt denselben vorhandenen Mutationsport weiter. Ein direkt konstruierter Workspaceport ohne diesen Anschluss verweigert; er erzeugt keine eigene Ersatzepoche.
- [OutstandingCoreRead](../../data/src/main/kotlin/org/pag/data/product/OutstandingCoreRead.kt): `beginFullview` ist weiterhin **keine** Admission. Erst `entry.admit(reservation)` prüft beide Kosten und Grenzen am Originalrecord. `entry.materialize()` konsumiert diese Zulassung genau einmal, **vor** dem ersten Board-DTO. Fremde, ungestartete oder bereits konsumierte Identitäten können sie nicht verwenden. Die Buchung konkurriert exklusiv mit dem ursprünglichen Core und allen laufenden Fullviewübergaben; kein zweiter parallel freier Budgetowner.
- [Direkte Leseports](../../data/src/main/kotlin/org/pag/data/product/FullviewReadExclusion.kt) verwenden denselben Preflight und Record, eine äußere konsistente Room-Readtransaktion und Original-Mutation-/Publicationgates. Personen, Operations, Tasks, Material, Kosten, Protokoll und Memory erhalten kein eigenes freies Teilbudget. Ein weiterer direkter Boardaufruf bei gehaltenem Snapshot wird vor DB-/Materializerarbeit verweigert. Es gibt keinen automatischen Ersatzread nach Refusal. Ohne materialisierten alten Graph dürfen später bewusst angefragte kleinere Projektionen weiterhin nur mit einer eigenen, gleichfalls am Originalrecord geprüften Zulassung starten; die Ablehnung der größeren Projektion wird nicht als Zulassung übernommen.
- Taskhistory bleibt auf **32 Ereignisse pro Seite** begrenzt. Ihr bestehender Seiten-Sizer übergibt jetzt vor dem ersten Eventtext die Reservation an den Originalscope. Task-/Action-Metadaten und die einzelne Seite werden gemeinsam gebucht; Expected-/Status-DTO-Prüfung erfolgt erst danach. Es wird keine vollständige Eventliste geladen. Die Page-Kostenvalidatoren bleiben Validatoren; sie schaffen keine parallele Originalreservation.
- `BUDGET_REFUSED` ist typisiert und wird in der Produkt-UI als Speicherbudgetverweigerung behandelt. Kein Ersatz-EMPTY und kein „Gespeichert“. Die Budgetmeldung beschreibt ausschließlich den Ladefehler; sie behauptet weder Speichern noch Nichtspeichern eines vorherigen Schreibvorgangs (finaler UI-Gegenfall [large-04](device-large-04.txt)). `OWNER_UNCONFIRMED` bleibt davon getrennt. Ungültige Storage-/Revisionsmetadaten bleiben fail closed, keine reguläre Budgetverweigerung als Storagefehler.

### Tatsächliche Projektionen

| Anfrage | Größenmetadaten vor DTOs |
|---|---|
| Jeder Workspace | Action; Personen; Rollen; Planheader; geplante Rollenrelationen; aktuelle Zustände, jeweils inklusive Schema-12-Revisionen |
| TASKS | Taskzeilen samt Revision, **keine** Eventhistorie; Board liefert unverändert `eventsComplete=false` |
| PROTOCOL_TIMELINE | Konservativ alle Protocoltabellen und mögliche Personenrelationen der Aktion; kein chronikloser Datenverlust |
| MATERIAL_BOARD | Materialzeilen |
| EXPENSE_BOARD | Kosten und Zahlungen |
| MEMORY_HEADERS | Reportheader |
| Protocol-/Material-/Expense-/Memory-/Document-Summary | Bestehende skalare Summaryqueries, feste Ergebnisobjekte in der gemeinsamen Grundreservation; keine Dokumentnamen, Keys oder Dateiinhalte |
| Direkte Detail-/Boardports | Jeweilige konservative Tabellenfamilie; Memorydetail zusätzlich Decoderobjektallowance pro Payloadbyte |
| Taskhistory | Konservativ Task-/Actionmetadaten plus bestehender Sizer für genau die angefragte 32er-Seite |

Die konservativen Familien können mehr buchen als die kleinste mögliche Projektion. Das ist kein neues fachliches Zeilenlimit und keine Produktfreigabe für die maximale Datenmenge. Insbesondere kann die vollständige große Darstellung verweigern, obwohl kompakte Reader dieselbe Aktion begrenzt lesen könnten. Es wird kein neuer Fallback eingerichtet.

## Größenformeln und Grenzen

**R = 8.388.608 Bytes; T = 16.777.216 Bytes.** Keine Änderung der Grenzen oder der bestehenden `ActiveWorkspaceBudget`-Gewichte.

Für die gewählten Tabellen: `n = Summe Zeilen`, `f = Summe(Zeilen × alle Spalten)`, `b = Summe UTF8-BLOB-Bytelängen sämtlicher Textspalten`. Die Spaltenzählung überzählt bewusst numerische Felder für zusätzliche String-/Boxing-/Bindingkosten. Schema-12-Revisionen gehen in `f` ein. Bei Memorydetail wird konservativ `n := n+b` für mögliche Decoder-/Tokenobjekte verwendet. Ein fehlender Revisionswert wird nicht durch einen Null-Default ersetzt.

Je `c = 4` (Retained) beziehungsweise `c = 16` (Transition) lautet die Reservation:

```
objects = 64 + n*8*c
strings = 64 + f*c          utf16Units = 4096 + b*c
arrays = 64                arrayPayloadBytes = 8192
lists = 64 + n*c           listCapacity = f*2*c
maps = 32                  mapCapacity = n*2*c
references = 256 + f*4*c
```

Die bestehenden Ledgergewichte sind Objects 128, String-/Arrayheader je 71, UTF16-Units 4, Arraybytes 1, Listheader 128/Kapazität 8, Mapheader 128/Kapazität 128, Referenzen 8 Bytes. Kein Dedup-Rabatt. Enthalten sind DAO-Rows/Domainmodelle, Normalisierungswerte, Plan-Sets/Maps, Filter-/Sortier-/Mappinglisten, Collections/Builder, Revisionen, skalare Summaryobjekte und die begrenzten Read-/Handoff-/Metadatenobjekte. Die festen Kosten sind keine pauschale Forms-Scratchfreigabe. **Nicht davon als endlich bewiesen sind beliebig viele neue Draft-/UI-/Writer-Captures nach Ausgabe**; dafür bleibt die Abnahme BLOCKED.

Alle Additionen/Multiplikationen sind checked. Vor SQL-SUM wird pro Tabelle `COUNT <= R/128` und pro Textspalte `MAX(bytes) <= R` verlangt. Damit ist jedes einzelne SQL-SUM höchstens `65.536 × 8.388.608 = 2^39`; anschließende Summierung/Einheitenbildung nutzt checked Long-Arithmetik. UTF8-Bytes begrenzen konservativ die UTF16-Units, auch bei NUL/supplementären Zeichen. Nicht-UTF8-Datenbanken werden nicht geraten.

Nach Materialisierungsbeginn hält der Record konservativ die **gesamte T16-Hülle** und die geprüfte R-Buchung. Dieser ungenutzte Rest wird keinem anderen Reader zugesprochen. Die immutable Reservation und numerischen Charges enthalten keine Payload. Die zwei letzten lokalen Schätzkosten sind nur Diagnosemetadaten: `localRetained/localTransition` können nach Refusal oberhalb der Grenzen liegen, während `retained/transition=0` lediglich **keine Payloadbuchung** bedeutet. Es ist keine Aussage, dass keinerlei Framework-/native Ressourcen existieren.

Die Rechnung ist eine konservative Engineeringbuchung der benannten Readrepräsentationen, **keine gemessene ART-Deep-Size, Native- oder PSS-Obergrenze** und kein globaler OOM-Nachweis. SQLCipher-/Room-/Android-/Compose-/Textlayout-/Renderer-/IME-/Accessibility-Ressourcen werden nicht als kostenfrei oder physisch beendet behandelt; Gesamtkalibrierung bleibt #33/#39.

## Snapshot-/Formreferenzen und tatsächliche Enden

```
ursprünglicher OutstandingCoreRead
  ├─ alter Core → unveränderte Sperre
  └─ FullviewEntry (Action/Generation + Originalreadticket in Dispatch)
       → skalare SQL-Metadaten im Original-Readtransaction-Snapshot
       → R/T prüfen → einmalig materialize()
       → Reader/DAO/Builder → WorkspaceRead → Main → WorkspaceSnapshot
       → Tasks/Operations/Personen/Form-Expected/Draft/Callbacks
```

| Stelle | Tatsächliches Ende / verbleibende Verpflichtung |
|---|---|
| Reader/Builder | Originale Room-/DB-/Keyscopes kehren zurück; Exceptions dürfen nicht als erfolgreiche Ausgabe gelten |
| Worker-/Main-Shell | Einmalige Entnahme; ACK erst im tatsächlichen `finally`; verlorener Main bleibt unquittiert; falsches/doppeltes ACK beendet keinen laufenden Dispatch |
| Ausgegebener Graph | `fullviewObligation` bleibt identitätsgebunden erhalten, obwohl `fullEntry` nach den lokalen ACKs endet. Die terminalen Shells halten keinen Callbackbody mehr; die verbleibende Verpflichtung ist Metadaten, kein zweiter Payloadcache |
| WorkspaceResult/Snapshot/Model/Form/Expected/Writer-Capture | Kein gemeinsamer vollständiger Consumer-Endproducer. Daher kein erneuter vollständiger Read nach Materialisierung, auch nicht nach Lock/Reauth/neuer Runtime |
| Bereits übergebene Frameworktexte | Getrennter Option-2-Bereich; nicht rückrufbar, keine physische Wipebehauptung |

Ein Abbruch **vor** Verbrauch der Admission kann nach den tatsächlichen Worker-/Main-Enden erneut zugelassen werden, weil kein vollständiger Graph begann. Ein Fehler **nach** Verbrauch entfernt die Verpflichtung nicht. Weder Main-ACK, leere VM-Felder, `VM_SCOPES_QUIESCENT`, `onDispose`, `onDraw`, WeakReference noch GC geben diesen Scope frei.

## Hostbelege

**238 entdeckte Hostfälle: 237 bestanden, 0 Fehler/Errors, 1 bewusst übersprungener historischer 129k-Sonderfall**, [Zusammenfassung](host-summary.json), XMLs unter `host-results/`.

- [12 neue P107-Fälle](../../data/src/test/kotlin/org/pag/data/product/P107FullviewAdmissionTest.kt): Originalidentity/Einmalverbrauch, exakte getrennte R8/T16/+1, negative/überlaufende Metadaten, Cancel vor/während Preflight, keine Payloadreads im Sizer, alle drei lesbaren Actionstatus, echter SQLite-Snapshot über einen konkurrierenden WAL-Commit, absichtlicher TEST-Alias nach ACK, Lost Main, Materialisierungsfehler ohne Freigabe, gemeinsame History-Admit/Refusal vor Eventtext, Original-Livenesswechsel vor/nach Buchung ohne erreichbaren Materializer sowie terminale Shell-Entkopplung mit negativem Vorher-Test. Logs [13](history-scope-host-13.log), [14](diagnostic-build-host-14.log), final [19](final-scope-build-host-19.log); frühere [12](consistency-host-12.log).
- 7 P106-Ausschluss-/ACK-Fälle, 16 Outstandingfälle, 25 bestandene CompactTaskStore-/Pagingfälle (+1 bereits vorhandener `@Ignore` für den historischen 129k-Sonderfall; dieser wurde ausdrücklich nicht ausgeführt), 14 Budget- und 6 gemeinsame CoreAdmissionfälle.
- 157 Writer-/Revision-/Editor-/Workspace-Hostregressionen aus den im XMLinventar benannten Klassen. [Regressionslauf](regression-05.log). Keine neue umfassende Suite; Schema12-CAS, TaskEvent-/UNKNOWN-Verträge bleiben erhalten. Bestehende Android-CAS-/Recovery-Belege aus §§102/104/105 werden nur für ihre unveränderten Writer-/DB-Verträge wiederverwendet, nicht als Fullview-/Formbudgetbeweis.

Betroffene P77-App-/Testbuilds bestanden, zuletzt [19](final-scope-build-host-19.log), danach reine Budgetmeldung mit [20](ui-build-20.log); geänderte Data-Androidtestquellen zusätzlich **nur kompiliert**, nicht als Suite ausgeführt ([15](cancel-build-15.log), abschließend [21](data-test-compile-21.log)). Kein Releasebuild verändert. Der Source-Patch-Whitespacecheck für `app/data/domain` ist sauber. Der einzige Whitespacehinweis im Gesamtpatch ist eine vom Kotlin-Tool erzeugte abschließende Leerstelle in `probe-build-10.log:132`; der Rohlog bleibt dafür bytegetreu erhalten. Der erste Build scheiterte an einem suspend-SQL-Aufruf innerhalb eines nicht-suspend `checked`-Blocks; SUM zunächst separat unter demselben Gate lesen, dann checked addieren. [Fehlversuch](build-01.log), [erfolgreicher Folgebuild](build-02.log). Nicht als bestandenen Lauf zählen.

## Gerätebelege (synthetische P77-Authentisierung)

Nur `org.pag.prototype.a.p77runtime` und dessen Testpaket, Gerät RFCT1186YRJ, Original ProductRuntime/Session/SQLCipher/B2/Room/Repositories. Kein `pm clear`, kein Produktpaketreset, keine echten Nutzerdaten/Aliase. [Probe](../../app/src/androidTest/kotlin/org/pag/prototype/P107FullviewAdmissionProbe.kt), [signer-/paketgeprüftes Skript](run-device.py), APK-/Signer-/geschützte Inventare und Cleanup in `resume-*`.

Neun fachliche Szenarien, mit gezielten Wiederholungen nach Fehlern/Fixes: [Laufbilanz](device-summary.json) enthält 22 Instrumentationläufe, 20 PASS und 2 ausdrücklich dokumentierte Harness-FAIL. Das ist keine vollständige Androidsuite und kein vollständiger Funktions-PASS.

Die ursprüngliche funktionale Gerätematrix lief mit Builds 09/11. Build14 ergänzt die numerische letzte Fullview-Schätzung/Refusaldiagnose; große und +1-Refusalfälle wurden erneut ausgeführt. Builds15/16 sichern zusätzlich Requestcancel bei frühen Refusals und fassen die finalen Original-Livenessprüfungen in einer gemeinsam hostgeprüften Seam zusammen. Mit Build16 wurden PREPARATION einschließlich echter Queue-Cancel-Gegenprobe, ACTIVE/Draft, Runtimewechsel/Exposed und großer Refusal vollständig erneut geprüft. Die numerischen R/T-Formeln und die vier ursprünglichen Grenzfälle ändern sich dabei nicht. Ältere lokale Null-Schätzkosten in Refusallogs waren **fehlende Diagnose**, kein Nullkostenbeweis.

| Fall | Tatsächliches Ergebnis / Materialisierungszähler | Rohlog |
|---|---|---|
| Exposed Core + direkte Ports + Store ohne Entry | Sichtbarer OWNER_UNCONFIRMED; Workspace-Materialisierungen 0, Core-Reads 1; direkter Operations-/Task-/Personenport verweigert | [exposed-04](device-exposed-04.txt) |
| Erster PREPARATION-Fullview | Sichtbar, 20 Personen/40 Tasks, Schema12-Revisionen; R=961.932/T lokal=3.679.152; 1 Aufbau; weitere Requests bei TEST-Snapshot verweigert | [preparation-05](device-preparation-05.txt) |
| Erster COMPLETED-Fullview | Sichtbar, 20 Personen/40 Tasks; alte Snapshotreferenz bleibt, Folgeread/Lock-Reauth/Actionwechsel verweigern | [completed-01](device-completed-01.txt) |
| Erster ACTIVE-Fullview ohne alten Core | Sichtbar, 20 Personen/40 Tasks, Taskentwurf tatsächlich sichtbar und Expected-Revision 0; kein Speichern behauptet; weiterer Workspace-/Taskboardread verweigert, ebenso nach Lock/Reauth/Actionwechsel | [active-02](device-active-02.txt) |
| Große lange Unicode-Fixture | 200 Personen/1.000 Tasks/5.000 Events/32 Rollen; tatsächliche Schätzung R=23.841.564/T=95.197.680; BUDGET_REFUSED sichtbar; **0 Boardaufbauten** | [large-04](device-large-04.txt), initial [large-01](device-large-01.txt) |
| R exakt | R=8.388.608/T=4.682.608; 1 Aufbau, zusätzlich tatsächlich angelegtes geplantes Testarray 7.175.741 Bytes | [r-exact](device-r-exact-01.txt) |
| R +1 | R=8.388.609/T=4.682.608; BUDGET_REFUSED; 0 Board-/Testarraymaterialisierung | [r-over-02](device-r-over-02.txt) |
| T exakt | R=1.212.796/T=16.777.216; 1 Aufbau, zusätzlich tatsächlich angelegtes geplantes Testarray 12.094.537 Bytes | [t-exact](device-t-exact-01.txt) |
| T +1 | R=1.212.796/T=16.777.217; BUDGET_REFUSED; 0 Board-/Testarraymaterialisierung | [t-over-02](device-t-over-02.txt) |

Die Grenzfälle nutzen **disjunkte, ausdrücklich TEST-seitige echte Bytearrays**, deren Planung zum tatsächlichen SQL-Plan addiert wird. Die exakte Differenz berücksichtigt den bestehenden 71-Byte-Arrayheaderallowance. Der P77-Harness legt das Array erst im nach Admission erreichten Materialisierungshook an und hält es absichtlich als Testreferenz. Bei +1 wird es überhaupt nicht angelegt. Das ist ein echter Ledger-/Eintrittsgegenfall unter Original-DB-Read, **kein Nachweis exakter ART-/Nativekosten** und keine Produkt-Formscratchfreigabe. Normale Runtime kann diese Hooks nicht liefern; der bestehende synthetische Authority-Konstruktor prüft exakt P77-Paket und Debuggable-Flag.

### Terminale Fullview-Shell: reproduzierter Referenzdefekt und kleiner Fix

Der bisherige `OnceTask.ended`-Callback blieb als Feld erhalten, nachdem `work` bereits entkoppelt war. Damit hielt ein extern aufbewahrter terminaler Fullview-Worker weiterhin `Entry → Originalrecord`; bei einem sicher vor Materialisierung beendeten Scope konnte dieser Record später wieder einen zulässigen Originalread B halten. Das ist die gleiche Art unnötiger transitive Verbindung wie §99, **kein belegter Berechtigungsbruch**.

Die gezielte Gegenprobe scheitert am tatsächlichen Worker→Record-Verweis: [roter Reproduktionslauf](shell-reproduction-host-17.log), [separates rotes XML](shell-reproduction-red.xml). Anschließend wurde ausschließlich das vorhandene Feld nullable und vor tatsächlichem Dispatch synchron entnehmbar gemacht. Der entnommene Endcallback bleibt lokal bis zum ursprünglichen `finally`; eine nachweislich nicht gestartete Shell löscht beide Felder. Keine neue Zell-/Payload-/Cacheobjektinstanz entsteht durch diesen Feldersatz. Native Monitorkosten sind damit nicht als null gemessen/behauptet.

Ein verfrühtes `mainRejected()` ohne überhaupt registrierte Main-Shell darf außerdem keinen späteren Main vorquittieren. Dieser Nullfall wird jetzt ignoriert. Der Hostgegenfall prüft Worker/Main nach tatsächlicher Rückkehr, bekannte Nichtausführung, Main weiterhin pending trotz früher falscher Quittierung und fehlende Erreichbarkeit eines späteren Reads B über die terminalen Shells. Der explizit im Test gehaltene alte Entry-/Owneralias ist davon getrennt und kein globaler Endbeweis. [Grüner Fixlauf](shell-fix-build-host-18.log), [abschließender Host-/Buildlauf](final-scope-build-host-19.log).

Die Buchung ausgegebener Snapshots bleibt auch mit diesem Fix geschlossen. PREPARATION inklusive Queue-Cancel sowie Exposed/Runtime wurden mit dem abschließenden Build nochmals vollständig ausgeführt: [preparation-05](device-preparation-05.txt), [exposed-04](device-exposed-04.txt). Die R/T-Formeln sind durch den Feldersatz unverändert.

### Cancellation-Gegenprobe und tatsächlicher Produktfix

Bei der Schlussprüfung wurde zusätzlich ein bestehender Fehler in den frühen Workspace-Refusals gefunden: Der auf Main wartende Refusal prüfte Session-Publication, aber nicht nochmals das Requestticket. Der Produktpfad prüft dieses jetzt vor Callbackausführung. Der P77-Fall [preparation-05](device-preparation-05.txt) hält den ersten echten Worker **vor** Preflight an, hält danach einen zweiten, tatsächlich gequeueten Refusal-Main an, cancelt dessen Ticket, lässt Main wirklich zurückkehren, cancelt/releaset den ersten Worker und bestätigt dessen tatsächliches Ende mit einem Queue-Barrier. Ergebnis: **0 Callbacks, 0 Materialisierungen**. Danach wird der erste erlaubte PREP-Graph ohne Recordreset admitted und sichtbar. Kein Frame-/Dispose-Endreceipt.

Die zwei bestehenden Data-Cancel-Testfälle wurden an den verpflichtenden Original-Writeranschluss angepasst: Ein ungebundener direkter Workspaceport kann keinen frischen Read autorisieren. Ihre Quellen wurden kompiliert; diese Data-Androidsuite wurde ausdrücklich nicht installiert/ausgeführt. Der echte Cancellation-Gegenfall oben läuft ausschließlich im freigegebenen P77-Paket.

### §106-Runtime-Timeout: reproduziert und aufgeklärt

`SessionCoordinator.foregroundAllowed` startet false. `authenticate()` kehrt bei fehlendem Foreground **vor Authentisierungsbeginn** zurück. Der alte Harness konstruierte eine zweite Runtime und rief `start(false)` auf, ohne ihr das Activity-Focusereignis zuzustellen. Der erste Produkt-ViewModel-Owner hatte dieses Ereignis erhalten; daraus folgt es für die neue Session gerade nicht.

[exposed-04](device-exposed-04.txt) führt bewusst zuerst den historischen fehlerhaften Ablauf aus: Activityfocus true, Ersatzsession bleibt LOCKED/Generation0, keine Authentisierung gestartet. Ein echter Workerqueue-Barrier kehrt zurück; kein blockierter Worker wird durch längeres Timeout verschleiert. Danach wird `next.session.foreground(activity.hasWindowFocus())` aus der echten Activitybindung gemeldet und normal `start(false)` ausgelöst. Beobachtet: **LOCKED → AUTHENTICATING → MAINTENANCE → READY**. Die originale Runtime ist LOCKED, Original-Core bleibt `COMPOSE_TRANSITION`, `retirementConfirmed=false`, T16 weiter verpflichtet. Ersatzruntime verweigert danach Core und Fullview. Gesamtlauf inklusive Fixturecleanup **PASS**.

**Einstufung: Test-Harness-Fehler bestätigt**, kein Produkt-READY-Fix und kein Recordreset. Synthetische Authentisierung bleibt als solche benannt. Das ist ein echter Runtimewechsel in derselben Activity, **kein vollständiger Activity-Recreate-/Prozess-/DeviceCredentialnachweis**. Key-/Room-/Maintenance-Enden werden nicht allein aus einem State-String als native Gesamtspeicherfreigabe abgeleitet; READY benötigt unverändert die originale Maintenance/Keybindung.

### Fehlgeschlagene Proben / Bereinigung

- [preparation-01](device-preparation-01.txt): erster Read bereits admitted/sichtbar, danach FAIL beim Taskentwurf. Harness öffnete den Taskmodel-Entwurf im PREPARATION-Übersichtsbereich; dort wird dieses Formular nicht gerendert. Keine bestandene Editorbedienung daraus ableiten. Die Wiederholung prüft nur die tatsächlich sichtbare erste Übersicht; ACTIVE prüft den sichtbaren Taskentwurf.
- Nach diesem Fehllauf blieb eine synthetische Fixture zurück: [fehlgeschlagene Cleanupprüfung](cleanup-failed-01.txt), [genau entfernte Testwurzel und manuelle Maßnahme](manual-cleanup-01.json). Ausschließlich P77 force-stopped und genau diese synthetische Wurzel entfernt, danach keine Restfixture. Kein behaupteter erfolgreicher automatischer Cleanup dieses Fehllaufs.
- [preparation-02](device-preparation-02.txt): Read/Refusals bestätigt, anschließend Harness-FAIL durch verschachteltes `runOnMainSync` innerhalb des bereits auf Main ausgeführten Test-Callbacks. Generation vorher auf dem Instrumentationthread abfragen; kein Produktgate ändern. Die verbesserte Fehlerbehandlung wartet vor Instrumentationende auf Fixturecleanup; [Cleanup nach diesem FAIL](cleanup-after-failure-02.txt) erfolgreich.
- [preparation-03](device-preparation-03.txt), final [preparation-05](device-preparation-05.txt), vollständig PASS **für die angegebenen Admission-/Refusalproben**, einschließlich explizitem funktionalem Personen-Detailblocker. Nicht als vollständige PREPARATION-Abnahme ausgeben. Die finalen regulären Proben und `resume-cleanup.json` bestätigen automatische Fixturebereinigung; der frühere manuelle Fall bleibt getrennt.

Keine neue Native-/PSS-/FD- oder Performancebenchmarkserie. Keine GC-basierte Freigabe.

## Aktualisierte ursprüngliche #37-Akzeptanzmatrix

Historische 8+8-Bezüge im Issue werden gemäß verbindlichem §91 als R8/T16/Option2 beurteilt.

| Kriterium | Produktpfad / tatsächlicher Gegenfall / Beleg | Status |
|---|---|---|
| navigateActive/clearPersons/readWorkspace/Snapshots/Requests/Forms vollständig verbinden | Vorprüfung und nicht rückgesetzte Snapshotbuchung jetzt produktiv; Personen-Detail nach erstem PREP-Snapshot verweigert; neue Form-/UICaptures noch nicht vollständig allokationsgebunden | **BLOCKED** Gesamtfunktion/Ownerintegration |
| Eine ACTIVE-Aktion, Originaltickets/Generation/Action/Status/Mutation/Permission | Gemeinsamer Runtime-Mutationsport, originaler Readticket im Reader/Main, Schema12/Sessionregeln unverändert; neue Gate-/SQLite-/Gerätefälle | **PASS** benannter Read-/Publicationteil; kein zusätzlicher Sessionowner |
| Kompakter Kern beschränkt, kein ungeprüftes Nebenpayload | Compactcode unverändert; Fullview-Boards sind ausdrücklich getrennte Projektionen und dürfen nicht parallel zum exponierten Core entstehen | **PASS** kompakte Abgrenzung; geschützte Nebenbereiche kein neuer Budget-PASS |
| Gemeinsame R8/T16 inkl. alter/neuer/Formkopien | Erstgraph vor DTOs gemeinsam gebucht, alte Snapshotverpflichtung sperrt weitere Graphen; Grenzfälle real geprüft. Neue Draft-/Expected-/UI-/Writercopy-Erzeuger und unabhängig vorgelagerte Formzustände nicht vollständig gebunden | **PASS** Readgraph-Teilfix; **BLOCKED** vollständige Appdatenrechnung |
| Invalidate/Unpublish bei Lock/Foreground/Fristen/Generation/Action/Status/Permission/BLOCKED/Recovery ohne späte Publication | Originalsession + ursprünglicher Writerport, Read-Liveness und finale Publication; Lock/Reauth/Action-Refusals real; bestehende Sicherheitsbelege unverändert | **PASS** benannte Fences; komplette neue Permission-/Writer-Racematrix kein zusätzlicher Geräte-PASS |
| Jeder RAM-Hit acquire/check/publish, keine Sessionverlängerung | Keine neuen RAM-Hits/Reuse implementiert; §§92–99 bleiben maßgeblich | **PASS** unveränderte Sperre; keine Aktivierung |
| Forms/Fokus/geschützte Bereiche/alte Referenzen korrekt | Sichtbarer ACTIVE-Draft, Lock löscht aktuelle VM-Form; absichtlicher Test-Snapshot bleibt. Kein vollständiger Draft-/Callback-Endreceipt oder vollständige Bedienbarkeit | **BLOCKED** |
| Host/Androidregressionen Reauth/Lifecycle/Action/Budget/OOM | 237 bestandene Hostfälle (+1 historischer Skip), gezielte native Admission-/Grenz-/Refusal-/Runtimeproben; tatsächlicher Runtimewechsel PASS. Kein realer Activity-Recreate-/Native-OOM-/physischer Endbeweis | **PASS** dokumentierte Teilmatrix; **BLOCKED** Gesamtabschluss |
| Nur isoliertes Paket, separate Performancefreigabe | Paket-/Signer-/Cleanupnachweise, Flags false, kein Merge/Release | **PASS** |

## Konkreter verbleibender Vertrag und Stopentscheidung

**Der fehlende Vertrag ist die kontrollierte Datenausgabe und Allokations-/Endzuordnung aller Fullview-/Form-/Writerconsumer zum ursprünglichen Scope.** Ein abgeschlossener Dispatcher reicht nicht. Die neue Sperre macht diese Lücke sicher sichtbar, löst ihre funktionale Seite aber nicht.

1. **Personen-Detail und Bereichswechsel:** `ProductViewModel.personDetail()` liest erneut über `getPerson`; `preparationArea()` führt `clearPersons()` und `loadWorkspace()` aus. Der erste Snapshot kann weiterhin beim UI-/Callbackconsumer liegen. [Reale Gegenprobe](device-preparation-03.txt): typisierter Personen-Detailrefusal. PREPARATION ist damit **nicht vollständig funktionsfähig abgenommen**. COMPLETED hat eine sichtbare erste Lesedarstellung, aber kein allgemeines Zurück-/Wiederöffnungsversprechen.
2. **Draft-/Callbackkopien:** etwa `TaskScreen.kt` erzeugt im Appcallback `model.change(i.copy(title=it))`, mit einem Capture des alten `i`. Personenpseudonyme sind in `PersonRules` nur auf nichtleer geprüft; eine gespeicherte DB-Größenrechnung begrenzt weder neue ungespeicherte Zeichen noch die Anzahl alter app-definierter Callback-/Inputkopien. Rollen-/Plan-/Live-/Writer-Expected-Builders benötigen denselben Anschluss. Weder „16 Kopien“ noch ein Forms-Scratch darf als Beweis einer unbekannten Zahl späterer Kopien verwendet werden. Die oben angegebene Rechnung ist ausdrücklich die endliche Readpipeline, nicht diese fehlende Erweiterung.
3. **Vorhandene unabhängige Nebenpfade:** Die PREPARATION-Metadatenanbindung `leadership.actionState()` ruft weiter `sensitiveSnapshot()`/`db.leadership().all(actionId)` auf. Diese außerhalb der Workspace-Readprojektion liegenden P3b-Inventar-/Row-/Bytearrayzustände sind nicht durch den neuen Receipt integriert. Keine erneute #35-Recovery-/Cryptoänderung auf Verdacht; #37/#39 müssen diesen konkreten Datenpfad bei vollständiger Rechnung berücksichtigen. Der neue Workspace-Receipt behauptet nicht, alle außerhalb seiner Projektion möglichen Roots zu kennen.
4. **History/Reload:** Die maximale Historyseite bleibt 32; eine bereits ausgegebene Seite wird ebenso wenig durch Main-ACK beendet. Sichere fortlaufende Seiten-/Editorreloads benötigen kontrollierte Ausgabe und eine tatsächlich begründete gemeinsame Buchung/Endzuordnung. Die fehlende Beendigung darf nicht durch Rücksetzen der Sperre ersetzt werden.

**Sichere heutige Alternative:** zugelassener erster Graph, bestehende skalare Autoritätsgates und kontrollierte Verweigerung jedes zusätzlichen vollständigen Reads bei alter Graphverpflichtung. Sie ist reproduzierbar, aber ein eingeschränkter WIP, **kein genehmigter 1.0-Modus** und keine pauschale Produktabnahme. Kein Text wird still gekürzt oder gelöscht.

**Funktionserhaltende technische Alternative:** vorhandene Fullview-/Editorconsumer am Originalscope vor jeder zusätzlichen Appkopie zulassen und für Navigation bereits benötigte, weiterhin autorisierte Projektionen kontrolliert ausgeben; echte alte Referenzen weiterhin buchen. Vorhandene Budgets können dabei Ressourcenrefusals erzwingen. Das wäre ein weiterer konkreter Implementierungsumfang, keine Freigabe aus diesem Bericht. Es wird nicht behauptet, dafür sei zwangsläufig ein neues fachliches Textlimit nötig.

**Falls stattdessen eine begrenzte 1.0-Bedienung beschlossen werden soll**, ist ausdrücklich zu entscheiden, ob nach einer einmaligen Fullview-Ausgabe Personen-Detail, Bereichswechsel, Reload und weiteres Historypaging verweigert werden dürfen und wie Entwürfe dabei erhalten/verworfene Bereiche dargestellt werden. Der gegenwärtige Auftrag genehmigt keine solche Einschränkung als Produktumfang. Ebenso wären feste neue Textgrenzen separat zu beschließen; sie werden hier nicht eingeführt. Eine höhere R/T-Grenze allein liefert keinen Endproducer für alte Kopien.

An dieser belegten Funktions-/Formgrenze wird gestoppt, statt einen unbewiesenen Owner-Endreceipt oder einen neuen Releasefallback einzubauen. **#37 bleibt BLOCKED/open; kein `READY_FOR_INDEPENDENT_CLOSURE`.**

## Unverändert

R8/T16-Werte; `reuseEnabled=false`; `ramReuseEnabled=false`; Core-Expositionssperre; keine automatische Core-Readmission. Keine neue Payloadregistry, kein Cache und kein Key-/Session-/Writer-/Recoveryprotokoll. Keine Schema-/Cryptoänderung, kein neues Fachlimit. Kein GC-/Frame-/Dispose-Freigabeproducer und keine physische Wipegarantie. Keine Schutzaufhebung durch Runtimewechsel. #33 und #39 bleiben unabhängig offen.

WIP für unabhängigen Review; Issues #37/#33/#44 erhalten den konkreten Teilbefund und die offenen Verträge. Kein Issueabschluss und kein Folgeauftrag auf Verdacht. **STOPP. Kein Merge, kein Release.**
