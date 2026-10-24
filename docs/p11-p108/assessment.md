# §108 – Originalgraph-Navigation, kontrollierte Zusatzprojektionen und verbleibende Produktentscheidung

**Ergebnis: funktionierender Teilfix; #37 bleibt BLOCKED. Kein READY_FOR_INDEPENDENT_CLOSURE.**

Basis: `e6c03083f5128f7df5480d8063a4b8f44341ec02`, Branch `p11-performance-optimization`. §§91–107 und insbesondere [§107](../p11-p107/assessment.md), [§105](../p11-p105/assessment.md), [§104](../p11-p104/assessment.md) bleiben Grundlage. Die ursprünglichen Issuekriterien sind in [issue37-before.json](issue37-before.json) gesichert. Dessen historischer 8+8-/Framework-Gesamtbudgettext wird durch die ausdrücklich beschlossene Option 2/R8+T16 ausgelegt, nicht wieder eingeführt.

## Tatsächlicher Produktdiff

- [WorkspaceAccess](../../domain/src/main/kotlin/org/pag/domain/Workspaces.kt) ist eine metadatenhaltige Fähigkeit auf **denselben ursprünglichen FullviewEntry**, keine Payloadregistry, kein Datencache und keine Session-/Keyautorität. [OriginalWorkspaceAccess](../../data/src/main/kotlin/org/pag/data/product/OriginalWorkspaceAccess.kt) bindet ursprüngliches Requestticket, Action-ID/-Status, Generation, ursprüngliches Mutationsticket und Publicationthread. Jeder Zugriff erwirbt erneut `StorageAccess.acquire(READY)`, prüft Originalticket und aktuelle Mutationsepoche sowie `check/publish`. Alte Tickets werden bei Commit, Rollback, Pending oder UNKNOWN nicht neu gebunden. Keine Sitzung wird durch Lesen verlängert.
- [ProductViewModel](../../app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt) hält weiterhin genau den ursprünglichen Snapshot. Der Getter gibt dieselbe Referenz nur unter erneuter Autorität aus. PERSONS/ROLES/TASKS/Übersicht sowie die entsprechenden Full-ACTIVE-Bereiche nutzen den Originalgraphen. `personDetail()` verwendet exakt dessen Person, ohne `getPerson()`-Read. `clearPersons(preserveWorkspace=true)` verwirft UI-Auswahl/Entwürfe, **nicht** das ursprüngliche Readticket und nicht dessen Verpflichtung. Normale Lock-/Action-Clears bleiben widerrufend.
- Fehlendes Material-/Kostenboard: [ProductWorkspaceRepository.supplement](../../data/src/main/kotlin/org/pag/data/product/ProductWorkspaceRepository.kt) liest ausschließlich die angefragte einzelne Familie. Autorisierte SQL-Metadaten und Materialisierung stehen in derselben Room-Readtransaktion unter ursprünglicher Session/Generation/Mutation und DocumentNamespaceLane. Vor DTOs erfolgt kumulative Zulassung im originalen Entry. Keine zweite Operations-/Task-/Workspaceabfrage. Danach existiert ein gebuchter neuer Snapshotheader mit denselben ursprünglichen Listen plus Modulreferenz; keine zweite vollständige Liste. Wiederholte Bereichswechsel referenzieren dasselbe Board. Jedes dieser beiden Boards wird maximal einmal materialisiert.
- [OutstandingCoreRead](../../data/src/main/kotlin/org/pag/data/product/OutstandingCoreRead.kt): genau eine zusätzliche Readerübergabe gleichzeitig; ursprüngliche Worker-/Main-OnceTasks mit tatsächlichen finally-Enden. Ein Childreceipt ist einmalig. Ein Main-ACK beendet nur den Übergabemarker, **keine** bereits ausgegebene Repräsentation und keine Buchung. Nicht ausgeführter Main, unbestätigtes Cleanup oder unbekannter Ausgang bleiben konservativ gesperrt. Keine Verbindung zur Core-Retirement-Freigabe.
- Taskhistory: [workspaceHistory](../../data/src/main/kotlin/org/pag/data/product/ProductTaskRepository.kt) nutzt dieselbe Originalverpflichtung. Action plus **ein** Task und höchstens 32 Events werden vor ihren DTOs gemeinsam zugelassen. Jede weitere Seite wird zusätzlich gebucht; alte Seiten werden nicht nach Main-Rückkehr abgezogen. Der ursprüngliche Task-Expected einschließlich Schema-12-Revision bleibt maßgeblich. Kein vollständiges Eventarray und kein eigenständiges Freikontingent.
- PREPARATION `leadership.actionState`: die Zustandsklassifikation läuft nun innerhalb des initial zugelassenen Workspace-Reads. Der Preflight berücksichtigt Leadership- und Sensitive-Area-Zeilen einschließlich Salt/Envelope/KeyEnvelope/Payload-Längen. Exportiert wird nur `SensitiveResult<SensitiveState>`. Personinventar verwendet einen separat vorab gebuchten Childread am Originalscope; Ausgabe nur Zustand und Bereichsenums. Temporäre verschlüsselte Inventarzeilen/Arrays werden mitgerechnet, nicht als freier Metadatenread deklariert. Kein P3b-Klartext/Grant/Schlüssel wird dem Snapshot hinzugefügt. Bestehende Klassifikation, Keystore- und §102-Recoverysemantik bleiben bestehen.
- Benannte Formularproduzenten in TaskModel, OperationsModel, MaterialModel, ExpenseModel und Pseudonym-Eingabe laufen erst nach kumulativer Zulassung. Produktive Task-/Material-/Kosten-Textcallbacks erzeugen ihre `copy(...)`-DTOs **innerhalb** des Gates und erfassen nicht mehr dafür den alten gesamten Input. Ablehnung erhält den bisherigen Entwurf und meldet einen Fehler. Interne alte `change(...)`-Adapter bleiben für bestehende Tests; die geänderten Produkt-Screens verwenden die skalaren Methoden. Keine neue Textlängenregel.

Das ist **kein Nachweis vollständiger Formular-/Render-/Writerkosten**. Beispielsweise verbleiben `TaskScreen`-Due-Initialformatierung, `ExpenseDraft.input()/feedback()`, `plannedRoles()`-Filter/Sort/Join, ProtocolScreen-`input.copy`-/Identifierlisten sowie Writer-Normalisierung/Result-/Callbackketten als konkret noch nicht vollständig integrierte Erzeuger. Dieser Patch aktiviert keine zuvor ausgeschaltete Protokoll-/Memory-Funktion und behauptet für ihre alten Wege keinen Budget-PASS. Die bisherigen fachlichen Funktionen wurden nicht als Option B entfernt.

## Referenz- und Budgetvertrag

```text
originaler Session-/Mutation-/Requestkontext
  -> ursprünglicher FullviewEntry [ein gemeinsames R8/T16]
       -> Worker / Main (OnceTask; ACK erst nach Rückkehr)
       -> OriginalWorkspaceAccess (nur Metadaten, kein Payloadgetter)
VM.workspaceValue -> ursprünglicher Snapshot -> Operations/Personen/Taskgraph
  -> Detail/Expected: dieselben unveränderlichen Datensatzreferenzen
  -> zugelassene Plan-/Inputproduzenten: neue, kumulativ gebuchte Werte
  -> höchstens ein ergänzender Childread -> gebuchtes Modul / Historyseite
       -> tatsächlicher Mainabschluss: nur Übergabe fertig; Daten weiter gebucht
TEST-Aliase auf Snapshot und alte Historyseite: absichtlich separat gehalten
```

Keine Liste wird allein wegen eines Bereichswechsels neu aus SQL gelesen. Ein neuer Snapshotheader nach einem Modulread teilt die ursprünglichen Listen, bleibt aber selbst zusätzlich gebucht. Material-/Kostenmodelle referenzieren dasselbe zugelassene Board. Alte Header/Seiten/Inputs können weiterhin existieren: ihre Buchungen werden **nicht** zurückgenommen.

**R8=8.388.608, T16=16.777.216 Bytes unverändert.** Die §107-Formel `FullviewPreflight.reservation(rows,fields,bytes)` bleibt: vier gehaltene und sechzehn Übergangsrepräsentationen der endlichen Readpipeline, geprüfte Addition/Multiplikation, Counts/MAX vor SUM. UTF-8-BLOB-Längen werden konservativ als UTF16-Einheiten angesetzt; keine Dedup-Rabatte. Für P3b-BLOBs ist dies eine Überbuchung, keine Behauptung kostenloser Arrays. Ein Historypreflight filtert den Task nach Action **und** Task-ID.

Disjunkte Ergänzungen:

| Produzent | Vor Materialisierung gebuchte Units |
|---|---|
| Ursprüngliche Capability, Model-Gate-Verbindungen, begrenzter synchroner Kontrollscratch | einmalig `objects=20,references=64` im initialen Workspaceplan; nicht nochmals je Modul/Seite |
| Ein Child plus begrenzte Übergabe-/Hüllenmetadaten | `objects=16,references=64` vor Child-Erzeugung |
| Modul / Inventar / Task+Eventseite | vollständiger eigener Preflight zusätzlich zu allen bisherigen R-/T-Buchungen |
| Bereichsreset mit TaskInput-/Modellkontrollen | `objects=16,references=64` je akzeptiertem Wechsel |
| Taskedit-Input / skalare Änderung | `objects=1,references=12`; neuer Text zusätzlich `strings=1,utf16Units=value.length` |
| Personenformularöffnung / Pseudonymeingabe | Öffnung `objects=4,references=16`; Eingabe `strings=1,utf16Units=value.length,references=1` |
| Plan-Expected / Rollenmenge | getrennte Objekt-/Map-/Listkapazitäten aus tatsächlicher Rollenmenge vor Freeze/Setkopie |
| Material-/Kostenentwürfe | Input-/Payment-/Listkapazitäten und neue Texte vor Konstruktion; numerische initiale Formatierungen konservativ begrenzt, siehe Modelquellen |

Diese Kontrollscratchpositionen sind **kein pauschales Formularbudget** und kein Freibrief für beliebig viele festgehaltene Appcallbacks. Reservierungen addieren alte und neue Daten kumulativ in **beiden** Limits; eine Ablehnung verändert die alte Datenbuchung nicht. Unbestätigtes Childcleanup sperrt die Originalfähigkeit synchron. Alle Child- und Controlbuchungen bleiben nach Rückkehr bestehen. Deshalb ist die Lösung für nachgewiesene endliche Abläufe funktionsfähig, garantiert aber weder beliebig lange Navigation noch alle 157 Historyseiten eines 5.000-Event-Tasks.

Die Zahlen sind konservative **Ledger-/Größenmodellwerte**, keine gemessenen ART-deep-sizes oder native Obergrenzen. `State.transition=16777216` zeigt die konservative Originalreservation; `localTransition` zeigt den detaillierten Plan. Framework-/Compose-/Textlayout-/IME-/Accessibility-/Native-Ressourcen werden nicht als null oder freigegeben gebucht. §108 führt keinen Performancebenchmark und keine neue Native-/PSS-Kalibrierung aus; dafür bleiben #33 und vorhandene Ressourcenbelege zuständig.

## Funktionsmatrix

| Funktion | Originalgraph / neue Repräsentation | Ergebnis |
|---|---|---|
| PREPARATION Übersicht → Person → Plan → zurück | dieselbe Person und Operationsdaten; vorab gebuchtes Expected/Set; zusätzlich autorisiertes P3b-Inventar | **PASS** endlicher Geräteablauf; kein zweiter vollständiger Read |
| PREPARATION → Tasks → Material → Kosten → zurück/wieder öffnen | TaskBoard unverändert; je ein Modulpreflight, ein Board, ein Header; erneute Anzeige per Referenz | **PASS**, Material-/Kostenmaterializer je genau 1; Vollgraph insgesamt 1 |
| Taskformular öffnen, Titel ändern, abbrechen | Referenz auf Original-Expected; einzeln gebuchte Input-/Textproduzenten | **PASS** sichtbarer Entwurf und Abbruch; kein weiterer Vollgraph |
| ACTIVE Personen-/Taskdetail | Originalperson / Originaltask | **PASS**, Person und Taskedittext sichtbar; History separat |
| COMPLETED lesendes Personendetail → Lock | ursprüngliche Person, kein Schreibrecht eingeführt | **PASS** Detail; Rücknavigation/Bereichsabschluss nicht vollständig gerätegeprüft |
| Erste und zweite Taskhistoryseite | je Task-/Actionmetadaten plus 32 Events; alte TEST-Seite absichtlich gehalten | **PASS** 32+32 verschiedene Events, kumulativ gebucht, Vollgraph weiter 1 |
| Beliebig viele weitere Seiten / dauerhafte wiederholte Navigation | alle alten Kosten bleiben gebucht, kein tatsächliches Endreceipt | **BLOCKED** unbegrenzte Funktionszusage; nach Budgetverbrauch kontrollierte Verweigerung |
| Normaler Taskedit speichern | unveränderte echte #35/#36-Transaktion; Revision 0→1, kein neues Event | **PASS** Commit/Storage; **BLOCKED** aktualisierte Fullview nach Commit |
| Personen-STALE | konkurrierender echter Writer, altes Expected, erhaltener Formularzustand; kein Erfolg | **PASS** CAS/Expected/kein Erfolg; **BLOCKED** ausdrücklich angefragtes Neuladen |
| Material-/Kostenformulare, Rollen-/Live-Editoren | benannte Copygates, bestehende Schema12-CAS-Regeln | **PASS** gezielte Hostregressionen/Produzenten; **BLOCKED** vollständige Geräte-/Callback-/Renderbudgetabnahme |
| Protokoll-/Memory-/weitere COMPLETED-Bereiche | bestehende, noch nicht vollständig gebundene Form-/Readwege | **BLOCKED**; keine Produktfreigabe oder stille Deaktivierung |
| Actionwechsel / Lock / Reauth mit altem Snapshot | alte Fähigkeit endgültig ungültig, Originalverpflichtung bleibt | **PASS** sichere Verweigerung; **BLOCKED** erneutes vollständiges Laden im selben Originalrecord |
| Exponierter Compact Core → Fullview / weiterer Vollread | Originalrecord unverändert | **PASS** Verweigerung vor Materialisierung; keine Readmission |

## Tests und Rohbelege

Die Hosttests prüfen den echten Originalrecord und Mutationsport, sowie separat das echte ProductViewModel mit **Fake**-Storagecapability. Sie sind kein Android-/Framework-Endbeweis. P108WorkspaceAccessTest enthält Originalidentität, frische Publication, exakte kumulative R-/T-Grenzen/+1/Overflow, Pending/COMMITTED/ROLLED_BACK/UNKNOWN, Foreignthread, Generation/Lock/Cancel, ein Child, alte doppelte ACKs, Lost Main, Cleanup-UNKNOWN, begrenzte verschachtelte Publication/Exception, false-after-callback und echte SQLite-Metadaten für P3b-BLOBs/einzelne Tasks. False nach ausgeführtem Callback ist kein Undo; die produktive Sessionpublication bleibt atomar.

WorkspaceBatchTest ergänzt vier Originalgraph-/Readticket-/Refusal-/Latecallbackfälle. TaskModelTest ergänzt verweigerte Inputkopie mit identisch erhaltenem Draft und Unicodekosten. Bestehende Task-/Personen-/Operations-/Material-/Kostenregressionen bleiben bestehen; #35/#36-DB-Writer wurden nicht neu implementiert. Die echten Schema12-/CAS-/TaskEvent-/UNKNOWN-/Recovery-Belege aus §§102/104/107 gelten nur für unveränderte Verträge, nicht für diesen fehlenden Gesamtformowner.

**Finale Bilanz:** 160 relevante Hosttests, 0 Fehler/Skipped ([host-summary.json](host-summary.json)); sechs abschließende isolierte Gerätefälle mit bestandenen Assertions, ein früherer Gerätefehlversuch bleibt FAIL. Alle Versuche und ihr begrenzter Aussageumfang: [run-summary.json](run-summary.json).

Hostlogs: [06](host-06.log), [09](host-09.log), [13](host-13.log), abschließend [17](host-17.log); maschinenlesbare XMLs unter [host-results](host-results). P77-Builds [07](native-build-07.log), [08](native-build-08.log), [10](native-build-10.log), [12](native-build-12.log), [14](native-build-14.log), [16](native-build-16.log), final [18](native-build-18.log).

Geräteproben verwenden tatsächlichen ProductRuntime, ursprüngliche Session, SQLCipher/Room, Repositorys, ProductViewModel und Compose im Paket `org.pag.prototype.a.p77runtime`. P77-Authentisierung ist **synthetisch**, kein DeviceCredential-Beweis. Navigation wird gezielt über die tatsächlichen VM-Produktbefehle ausgelöst, Sichtbarkeit über Accessibility geprüft; dies ist kein Touchlatenz- oder vollständiger UI-Endbeweis. Die STALE-Pseudonymeingabe erfolgt zusätzlich über die echte Accessibility-Texteingabe. Jeder Fall verwendet einen getrennten synthetischen Fixturescope, keinen zurückgesetzten exponierten Record.

- [PREPARATION final](device-preparation-final.txt): sichtbare Übersicht/Person und Taskentwurf; Originalreferenzen, Planöffnung/-abbruch; Material/Kosten je 1, Vollgraph 1. Ledger von R965.004/T3.682.224 auf R2.228.747/T7.967.087.
- [ACTIVE final](device-active-final.txt): 20 Personen/40 Tasks/**4.999 Events**; zwei Seiten aus demselben Task mit 4.960 Events (übrige 39 Tasks je ein Event). Nach Commit echte skalare SQLCipher-Beobachtung `title_match=1,revision=1,events=4960`. Normaler Edit erzeugt kein Transitionevent. Ledger nach Seiten R3.097.668/T11.654.928. Danach alte Autorität ungültig, Reload `OWNER_UNCONFIRMED`, kein zweiter Vollgraph.
- [COMPLETED final](device-completed-final.txt): sichtbare Originalperson, lesendes Detail, Lock/Reauth, weiterhin kein zweiter Graph.
- [STALE final](device-stale-final.txt): echte Formulareingabe sichtbar, konkurrierender Commit, STALE ohne Erfolg; bewusstes Neuladen anschließend OWNER_UNCONFIRMED, ursprüngliches Expected bleibt erhalten.
- [Großfixture final](device-large-final.txt): 200 Personen/1.000 Tasks/5.000 Events/32 Rollen, sichtbare Budgetverweigerung und 0 Vollgraph-Materialisierungen.
- [Exposed/Runtime final](device-exposed-final.txt): unveränderter P107-Produktgegenfall erneut vollständig bestanden, einschließlich normaler Foreground-/AUTHENTICATING-/MAINTENANCE-/READY-Rekonstruktion und weiterhin verweigerter Core-/Fullviewaufnahme.
- Finale Sicherheits-/Großfixtureläufe: siehe abschließendes [run-summary.json](run-summary.json). Die Bezeichnung ANDROID_PASS bestätigt nur die im Log genannten Assertions, auch wenn das erwartete funktionale Ergebnis ausdrücklich **BLOCKED** ist.

Fehlversuche werden nicht als PASS gezählt: [Build 01](build-01.log) falscher SensitiveProblem-Feldname, auf `reason` korrigiert, danach Build erfolgreich. [Check 11](final-check-11.log) kombinierte irrtümlich `pagTestBuildType=p77runtime` mit der dann nicht existierenden App-Debug-Testtask; Host und P77-Build getrennt erfolgreich wiederholt. [PREPARATION 01](device-preparation-01.txt) läuft bis Person/Plan, endet aber mit **FAIL timeout material supplement**. Die Wiederholung wartet die tatsächliche Inventarantwort nach Person und Planabbruch ab und besteht ohne Recordreset. Ein bereits laufender Childread darf eine Folgeanfrage weiter verweigern; schnelle Überlappung ist keine bestandene durchgehende Navigation. Die erste Probe hat den konkreten ausstehenden Child nicht vollständig instrumentiert, daher kein beweisbarer Vendorfehler oder vollständiger Kausalnachweis. Der anschließende JDWP-Diagnoseversuch scheiterte am Handshake, lieferte keinen Javastack und ist kein Freigabebeweis. Fixturecleanup des Fehlversuchs lief über Activity-Ende; kein `pm clear` oder produktiver Datenreset.

Paket-/Signer-/APK-SHA256-Nachweis: [resume-apks.json](resume-apks.json); Gerät [resume-device.txt](resume-device.txt). Geschützte Paketinventare [vor](resume-protected-before.json)/[nach](resume-protected-after.json) unverändert. [Cleanup](resume-cleanup.json): keine Fixturewurzeln übrig, kein manueller Datenreset. Nur das freigegebene P77-Ziel-/Testpaket wurde installiert.

## Ursprüngliche #37-Abnahme

| Akzeptanzkriterium | Nachweis / verbleibender Vertrag | Einstufung |
|---|---|---|
| Navigation/Clears/Read/Request/Formlebensdauer vollständig abgleichen | Same-graph Teilnavigation jetzt funktional; Postcommit-/STALE-Reload und alle Form-/Writerholder nicht vollständig integriert | **BLOCKED** gesamt |
| Ein Originalowner mit Action/READY/Permission/Mutation/Tickets | Originalrecord + VM-Slot/Metadatenfähigkeit, keine zweite Registry; frische Gates | **PASS** untersuchter Pfad |
| Nur zugelassener kompakter Kern, keine verbotenen Daten | kompakter Owner unverändert; Fullview eigenständig originalgebucht; P3b-Ausgabe nur enum, keine Schlüssel/Grants/Plaintexts | **PASS** Trennung, kein Gesamtfreigabeanspruch |
| Gemeinsame R8/T16 inkl. Alt/Neu/Formen | gemeinsame kumulative Child-/Inputbuchung und exakte Grenztests; nicht alle bestehenden Appcallback-/Render-/Writerproduzenten erfasst | **BLOCKED** gesamt |
| Lock/Lifecycle/Fristen/Action/Generation/Permission/UNKNOWN ohne späte Publication | Originalregeln unverändert, alte Fähigkeiten verweigert; Host-/P77-Teilgegenfälle, bestehende §§98–107-Belege | **PASS** Widerrufsgates; kein Owner-Endreceipt |
| acquire/check/publish je erneutem Zugriff, keine Sessionverlängerung | OriginalWorkspaceAccess, Foreignthread-/Binding-/false-after-callbacktests | **PASS** neuer Referenzzugriff; produktive RAM-Reuse bleibt aus |
| Formular/Fokus/Schutz und tatsächliches Referenzende | Entwurf/CAS in Teilfällen erhalten; expliziter Reload verweigert, keine vollständige Consumer-/Form-Endquittierung | **BLOCKED** |
| Host/Android inkl. Reauth/Action/Budget/Lifecycle | gezielte reproduzierbare Teilfälle und Wiederverwendung unveränderter Belege; kein vollständiger Form-/Postcommitfunktionstest-PASS | **BLOCKED** Gesamtabschluss |
| Performance-/Produktupdate getrennt | nur P77 installiert, kein Release/Merge, reuse/ramReuse aus | **PASS** |

## Kleinster Restvertrag und Entscheidungsvorlage

**Konkreter Blocker:** Der originale vollständige Snapshot und dessen Editor-/Callbackaliases besitzen nach Ausgabe kein vollständiges app-eigenes Endreceipt. Ein Writer invalidiert korrekt das ursprüngliche Mutationsticket. Danach darf weder der alte Snapshot neue Werte autorisieren noch ein neuer vollständiger Graph an der weiterhin offenen Verpflichtung vorbei geladen werden. Genau das verweigern normaler Task-Commit→Reload und STALE→explizites Personen-Neuladen. Ein erfolgreiches Commit bleibt erfolgreich; die Refusal beschreibt den Read, nicht einen nachträglichen Rollback. Neu gebundene partielle Ergebnisse/Editor-Expected-/Draft-/Writercaptures haben außerdem noch keinen vollständigen vorab budgetierten Consumervertrag. Ein leeres VM-Feld, Main-ACK oder VM_SCOPES_QUIESCENT behebt dies nicht.

**A – Funktionserhalt (bevorzugte Entscheidungsvorlage, hier nicht vollständig implementiert):** Ein kontrollierter Consumer-/Form-/Writerresultvertrag innerhalb des vorhandenen Originalrecords. Er muss jede zusätzliche Apprepräsentation und deren Callbackhalter vor Erzeugung buchen; bereits ausgegebene Altwerte bleiben gebucht, bis deren tatsächliche app-eigene Halter beendet sind. Benötigt werden insbesondere (1) begrenzte Produzenten für verbleibende Formatierungs-/Protokoll-/Payment-/Expected-/Writerresultkopien; (2) vorab zugelassene einzelne Postcommit-/STALE-Projektionen statt Vollreload, unter **neuem originalem** Writer-/Sessionticket, ohne alte Handles wiederzubeleben; (3) referenzarme UI-/Writercallbacks mit tatsächlicher Eintritts-/Rückkehrquittierung; (4) überprüfbare Ablösung/Enden der alten Appholder, bevor irgendeine Kostenrücknahme erlaubt wäre. Neue Scopes müssen an denselben Originalrecord gebunden bleiben. Keine zweite Session/Mutationsepoche/Payloadregistry. Risiken: Compose-Appcallbacks und Texteingabehalter sind nicht allein durch Nodeentfernung quittierbar; Frameworkrestkosten bleiben Option-2-Risiko, nicht null. Bis zu einem solchen Nachweis kann auch A bei kumulativ verbrauchtem Budget nur kontrolliert verweigern. Keine Zusage, dass ein Lifecyclecallback diesen Nachweis liefern wird.

**B – bewusst eingeschränkte Version 1.0 (NICHT aktiviert):** Nur endliche, zugelassene Erstanzeige und Same-graphnavigation, explizite Ressourcenrefusals bei Zusatzprojektionen. Nach erstem Commit bzw. fremdem Writer keine verlässlich aktualisierte Detail-/Boardansicht; kein garantiertes STALE-Neuladen; keine beliebig vielen Historyseiten/Entwurfsänderungen/Bereichswechsel; Actionwechsel/Lock/Reauth schaffen keinen freien Originalrecord. Protokoll-/Memory-/weitere COMPLETED-Bearbeitung wären ohne Integration ebenfalls nicht vollständig verfügbar. Dafür müssten konkrete Speichern-/Entwurf-/Neuladen-UX und ggf. der Umgang mit einer im Prozess endgültig verweigerten weiteren Action ausdrücklich beschlossen werden. Entwürfe dürfen weder still verworfen noch als gespeichert ausgegeben werden; bestätigter Commit und gescheiterter Refresh müssen separat angezeigt werden. Ein sicherer Prozessneustart-/Recoveryproduktweg wäre eigens zu entscheiden, kein automatischer Recordreset. Diese Beschränkungen sind erheblich und werden hier **nicht** als neue 1.0-Regel festgelegt.

**STOPP zur Produktentscheidung und unabhängigem Review.** Kein weiterer allgemeiner Ownerumbau, kein unbewiesenes OWNER_END_CONFIRMED. Unverändert: R8/T16, `reuseEnabled=false`, `ramReuseEnabled=false`, exponierte Core-Readmission gesperrt, originale Session-/Writer-/UNKNOWN-/Recovery-/Key-/Storage-/Schema12-/CAS-Regeln. Keine Schema-/Kryptoänderung, keine physische Wipebehauptung, kein Merge/Release.

## Sicherung und Reviewhalt

Produktcode/Tests/Rohbelege als WIP `c350c08869e621947e670e3496269e914b8674f7` auf `p11-performance-optimization` gepusht. Issues aktualisiert: [#37](https://github.com/JulianGKK/action-planner/issues/37#issuecomment-6390833172), [#33](https://github.com/JulianGKK/action-planner/issues/33#issuecomment-6390833310), [#44](https://github.com/JulianGKK/action-planner/issues/44#issuecomment-6390833462); genaue Kommentartexte und Toolquittierungen in [issue-updates.json](issue-updates.json). Keine Issues künstlich geschlossen. Die nachfolgende reine Dokumentationssicherung ergänzt diese Quittierungen. Danach STOPP zum unabhängigen Review; Option A/B bleibt bewusst zu entscheiden.
