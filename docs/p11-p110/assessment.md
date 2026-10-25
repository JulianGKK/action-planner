# §110 – Originalownergebundene Präsentationen und Membershipprojektionen

**INCOMPLETE_WIP. #37 bleibt offen; keine Freigabe zum Schließen, Mergen oder Releasen.**

Basis: `10db674002cbd196f8357c0ba0ccb4ab1d067663`, Branch `p11-performance-optimization`. Option A bleibt verbindlich. Der konkrete §109-Listenfehler ist produktiv beseitigt; weitere Präsentationsproducer und Membershippfade wurden implementiert. Der **vollständige** geforderte Formular-/Callback-/Protokoll-/Memory-Vertrag ist damit noch nicht erreicht. Diese Restarbeit wird weder als Frameworkkosten ausgeblendet noch als genehmigte Option B ausgegeben.

## Produktdiff

* `TaskBoard` besitzt eine einmal konstruierte `TaskPresentation`: Originalsortierung der offenen/geschlossenen Tasks, stabile Zusammenfassungen und je betroffener vorhandener Personenidentität zwei lesende Filterviews. `activeTaskContent()` und die `active`/`closed`-Getter geben vorhandene Referenzen zurück. Auch PREPARATION verwendet diese Views. Kein vollständiges neues Payloadarray je Filter-/Compose-Aufruf.
* `OperationalBoard.presentation` hält einmalig Personenübersicht, sortierte eindeutige Ortsvorschläge, lesende bevorzugte Rollenviews und geplante Rollentexte. Wiederholte Rollenformatierung im VM prüft Originalautorität und Personen-/Boardidentität, bevor sie den bestehenden Text ausgibt.
* Der **bestehende** `WorkspaceSnapshot` hat ein einmal bindbares, begrenztes `WorkspaceUiRows`-Member. Dieses enthält Task-/Personenzeilen, Datumstexte, Zugänglichkeitslabels, Rollenzeilenaktionen sowie die ACTIVE-Hubkarten. Es gibt keinen zusätzlichen VM-Snapshotslot, keine unabhängige Registry und keinen zweiten Budgetowner. Hubkarten werden ebenfalls nur bei geänderten Eingangsmetadaten neu aufgebaut: Ops-Identität, Taskzähler, Skalarsummaries, Aktionszeiten und Formatkontext. Ein reiner Tasktiteledit erzeugt keine neue identische Hubkartenliste. Unveränderte Displayobjekte können nach nachgewiesen identischen relevanten Quellen geborgt werden. **Aktionshüllen werden für den neuen Snapshot immer neu gebunden.**
* Taskdatumstexte für gleichen/anderen Kalendertag entstehen einmal. Vier Formatierer werden nach Planungsadmission einmal je Snapshot gebaut und zwischen seinen Zeilen geteilt; keine neuen Parser je Task/Person. Personenalterlabels wählen vorhandene, teils codekonstante Texte anhand skalarer Tages-/Minutengrenzen. Tagesgrenzen verwenden Epochensekunden, damit auch `Long.MIN_VALUE`/`Long.MAX_VALUE` keinen Millisekundenüberlauf erzeugen. Die Zeitzone gehört zur zugelassenen Präsentation; eine allgemeine neue Zeitzonenwechsel-Reprojektion ist nicht implementiert.
* `OriginalRowAction` hält Originalidentität, bestehende Access-Metadaten, Operation, ID und VM-Serviceport. Kein Task-/OperationalBoard, DTO-Listen- oder Snapshotcapture. Dispatch prüft Originalslot, Accessidentität, READY/Generation/Action/Status und die ursprüngliche wirkliche Readautorität vor und nach Interaktion. Erst dann wird der aktuelle Datensatz aufgelöst. Alte Hüllen können nicht auf eine neue Snapshotidentität umgebunden werden.
* Der produktive Lazy-Builder hält Service, Originalidentität und skalare Darstellungsmetadaten. Item-/Key-Callbacks lösen aktuelle Zeilen nach Prüfung dieser Identität auf. Kein Capture der alten Board-/Payloadlisten. Jede tatsächlich neue Builderkonstruktion wird vorher zusätzlich gebucht. `remember` vermeidet Konstruktionen, quittiert aber kein Ende. Scrollanker speichern beim Verlassen nur Koordinaten; die frühere fortlaufende `Pair`-Erzeugung im Scrollflow entfällt.
* `FormOutlinedButton` behandelt die neuen originalgebundenen Rowaktionen über einen vorher gebuchten, wiederverwendeten `FinishedRowClick`. Eine neue Anheftung erhält eine neue Buchung. Dispose/Detach erstattet nichts. Dies ist **nicht** die abgeschlossene Integration sämtlicher generischer Formcontrols.
* Ein neuer ausdrücklicher `WorkspaceDetailRequest.membership`-Vertrag ergänzt den strikten Bestandsdetailpfad. Er gestattet eine einzelne Person-/Task-/Material-/Kostenidentität hinzuzufügen oder zu entfernen; Rollen haben einen eigenen Detailkind. Unveränderte Nachbarzeilen werden weiterhin skalar geprüft. Personenmitgliedschaft umfasst den zugehörigen Plan/State. Rollenreihenfolge wird per SQL-Ordinal im selben Read-Tx bestimmt, ohne wiederholte Normalisierung aller Rollentexte beim Sortieren. Taskdeltas bewahren die relative Originalreihenfolge bestehender Zeilen.
* Task-, Material-, Kosten- und Rollenwriter unterscheiden Commitbestätigung von erfolgreicher neuer Bindung. Bei Refusal bleibt „Gespeichert – Ansicht konnte noch nicht aktualisiert werden.“ maßgeblich; ein bestätigter Create wird nicht wiederholt. Rollen-Neuladen verwendet den originalen Einzelprojektionpfad. Die produktiven Loader werden **in `bindWorkspace()`** angeschlossen: Runtime bindet das Repository erst nach VM-Konstruktion. Ein neuer Hostfall verhindert einen Rückfall in den verweigerten vollständigen Refresh.
* `TaskHistoryUi` bucht und erstellt höchstens 32 Anzeigezeilen je Historypage einmalig; Seitenzahltext wird am Originalcopygate angenommen. Alte History-/Labelaliasse bleiben gebucht.
* `ProtocolModel.filtered` verwendet bei ALL die Originaltimeline und bei KEY_EVENT eine einmalig zugelassene lesende View. `MemoryDraftPresentation` ist eine gebuchte abgeleitete Repräsentation des vorhandenen Drafts: kombinierte Antworten, Konflikt-/Entscheidungssets, Gültigkeit und Labels werden nur bei Änderung der Quellidentitäten erneut erstellt. Memory-Header werden beim Rendern nicht nochmals vollständig gefiltert.

## Originalowner, Autorität und Referenzhalter

Alle produktiven Buchungen bleiben am vorhandenen `OutstandingCoreRead.product` / ursprünglichen `FullviewEntry`. Die ursprüngliche Fullviewverpflichtung wird nicht entfernt. Die Capability hält eine eigene vorab gebuchte Request-Metadatenhülle ohne `expectedAction`-Payload; der ursprüngliche Cancel-Ticket bleibt identisch. Der Repository-Tx prüft weiterhin das tatsächliche vom Aufrufer mitgelieferte Expected-Actionobjekt. SQL-Konsistenzprüfung, Zielmetadaten, Admission und begrenzte Zielmaterialisierung bleiben in **derselben** autorisierten Room-Readtransaktion. Die neue Darstellung ist keine neue Schreibberechtigung.

Für Postcommit, STALE und Membership fordert das Repository unverändert einen neuen tatsächlichen `beforeActiveRead`-Mutationsticketvertrag an. Der bestehende VM-`WorkspaceReadTicket` ist lediglich Cancel-/Publicationmetadatum; seine Weiterverwendung belebt **kein** altes Writer-Mutationsticket. Pending/UNKNOWN/unbestätigtes Cleanup verweigern neue Autorität. Alter Expected, alter Draft, Snapshot, Display und Callback dürfen als externe Appaliasse weiterleben; sie bleiben konservativ zusätzlich gebucht.

Der VM-Slot verwendet Referenzgleichheit als Compose-Statepolicy. Inhaltlich gleiche DTOs dürfen eine neue zugelassene Snapshot-/Accessidentität nicht unsichtbar machen. Eine Postcommit-/Membership-Erfolgsmeldung setzt den tatsächlich neu gebundenen Slot voraus. Ein verweigerter UI-Aufbau wird nicht als erfolgreiche Aktualisierung weitergereicht.

Kein neuer Endreceipt und keine Kostenfreigabe wurden eingeführt. Main-/Worker-Rückkehr, `onDispose`, ACK, Clear, Lock, Reauth, Runtimewechsel oder GC gelten nicht als Ende der alten Darstellungen. Ein lokaler Scrollanker-Update ist ein UI-Ereignis, kein Retentionreceipt. Wiederverwendete immutable Displays sind bereits gebuchte **identische Objekte**, kein Kostenrabatt für neue Kopien.

## Producer-/Halter-Matrix

| Producer / Quelle | Neue oder geborgte Repräsentation | Buchung vor Konstruktion / Halter / Fence | Status und Ende |
|---|---|---|---|
| `activeTaskContent`, `TaskBoard.active/closed` | Dieselbe Boardpresentation und dieselben geordneten Listen | SQL-Preflight mit `COUNT(DISTINCT affectedPersonId)` + disjunkte `TaskPresentation.cost`; Board und gehaltene Test-/UIaliasse | Gegenbeleg behoben; kein Ende behauptet |
| Taskfilter | Vorhandene lesende Filterviews über Originalreihenfolge | Maximal vorhandene affected-ID-Kardinalität, initial gebucht | Keine vollständige zweite Taskpayloadliste je Aufruf |
| `activeTaskCard` produktiv | Vorab gebuchte `TaskUiDisplay`-Karten/Labels; geänderte Quelle erzeugt neue Darstellung | UI-Planungsreservation, dann tatsächliche Displayreservation; Snapshotmember | Kein per-Render-DTO; alte Karten bleiben gebucht |
| `activePeopleSummary` | Originale Summaryliste | `OperationalPresentation.cost`, initialer Boardowner | Keine per-Aufruf-Liste |
| `activePersonCard` produktiv | `PersonUiDisplay`, skalare Auswahl vorhandener Datumslabels | UI-Planung/Displayreservation, gleiche ursprüngliche Capability | Kein per-Render-Karten-DTO |
| Rollenplan-/Orts-/bevorzugte Rollentexte | Originalstrings und lesende Originalrollenviews | Operationspreflight; SQL-Joinlängen vor Konstruktion; aktuelle VM-Quellidentität | Keine wiederholten vollständigen Filter-/Joinlisten |
| Task-/Person-/PREP-Rollen-Rowclick | Metadatenhülle und ID | Snapshotreservation + neue gebuchte Rowwrapper bei Anheftung; Originalslot-/Session-/Mutationprüfung beim Dispatch | Altcallback nach Lock/Slotwechsel gesperrt; keine Freigabe |
| `ProductEntityList` Lazy-Builder/Keys/Items | Service + Rootidentität + Skalare; Zeilen erst bei gültigem Zugriff | 12.316 B je tatsächlicher Builderreservation; Builder und dessen gehaltene Aliasse | Kein Board-/Payloadlistencapture; keine Dispose-Freigabe |
| PREPARATION Task-/Personenzeilen | Obige Originalviews/-texte/-aktionen | Dieselben Source-/UIreservierungen | Wiederholungs-/Scrollpfad geprüft; generische Kopfcontrols noch nicht vollständig abgeschlossen |
| COMPLETED Personen | Vorhandene Personenzeilen/Detailaktionen | Gleicher Snapshotmember und Authorityfence | Readonly; Fixture hat keine Tasks, nicht als 40-Task-Gerätefall ausgeben |
| ACTIVE Hub | Einmalige gebuchte Hubkarten des ACTIVE-Snapshots | UIreservation mit rollen-/quelltextabhängigem Footprint | Kein erneuter vollständiger Kartenaufbau je Hubkomposition |
| Taskhistory | Eine endliche Labelpresentation je <=32-Page | `TaskHistoryUi.cost` + Originalcopygate; Model, alte Page-/Labelaliasse | Keine Vollhistorie, keine Rückerstattung beim Pagewechsel |
| Protocolfilter | Originaltimeline oder einmalige `KeyEvents`-View | Originalcopygate; vorhandenes Timeline-Model | Filterkopie behoben; Folge-DB-/UIvertrag insgesamt offen |
| Memory-Draftpräsentation | Ein gebuchter abgeleiteter Draftmember je Quellidentitätsänderung | `AppConsumerCopies.Footprint.units` vor Kombinieren/Labeln/Validieren; bestehendes Draftmodel | Wiederholtes Lesen borgt; alte Maps/Labels bleiben gebucht |
| Memory-Headeranzeige | Originalheaderiteration | Keine neue vollständige `filter`-Liste | Datums-/Readonly-Antwortpräsentation und Controls noch offen |
| Material-/Kostenmembership | Ein Ziel plus zusätzlich gebuchte neue Membershipliste | Frischer Writer-Readvertrag, gleiches Room-Tx, Metadaten und Admission vor DTO | Create/Delete produktiv; alte Modulboards bleiben gebucht |
| Allgemeine `FormButton`/`FormTextButton`/Checkbox-/Content-/Semantics-Hüllen | Bestehende teils neue Callbacks/Labels | **Kein vollständiger konstruktionsvorheriger Originalownervertrag für alle Aufrufstellen** | Offen; nicht kostenlos und nicht in #33 versteckt |
| `FormTextField` Keyboard-/Focus-/Layout-/Supporting-Callbacks | Bestehende app-eigene Controlhüllen | **Nicht durchgängig vor Konstruktion originalgebunden** | Offen; IME/Framework-Risiken zusätzlich separat |
| Weitere Rollen-/Funktions-/Protokoll-/Memory-/Material-/Kosten-Formatierer und Formularoptionen | Mehrere bestehende neue Strings/Captures | Teilweise vorhandene §109-Copygates; keine abgeschlossene Präsentationsmatrix | Offen; keine globale Abschlussbehauptung |
| Protokoll-/Memory-Folgeprojektion nach Writer | Bestehende Repositoryfolgepfade | Weiterhin kein vollständiger begrenzter Originalowner-Folgepfad | **Produktiver Restblocker**, nicht als genehmigter Funktionsverzicht interpretiert |

[Bytecodeausschnitt der tatsächlichen Rowaktionsfelder und Lazy-Lambda-Signaturen](producer-bytecode.txt) bestätigt die beschriebenen direkten Capturetypen. Er ist kein Beweis über alle Framework-/Compilerhüllen.

Die Matrix benennt bewusst auch nicht integrierte app-eigene Producer. Insbesondere die pauschale Behauptung „alle Callbackkosten sind durch den initialen Fullreader abgedeckt“ wäre weiterhin falsch.

## Vorher / Nachher des ursprünglichen Gegenbelegs

Die Arbeitslast bleibt **32.768 gehaltene Generationen × 40 Tasks**. Der Test wurde nicht entfernt, verkleinert oder auf eine feste Zahl zulässiger Aufrufe begrenzt. Geändert ist das erwartete Ergebnis des tatsächlichen Produktionshelpers.

Vorher: 32.768 unterschiedliche 40-Referenz-Listen, 0 Consumerreservationen, allein `32.768 × 128 + 32.768 × 40 × 8 = 14.680.064 B` zusätzliche modellierte Listenkosten. [Basis-/Quellfingerprint und Vorbeleg](before-counterexample.json), [ursprüngliche §109-Belege](../p11-p109/assessment.md).

Nachher: 32.768 gehaltene Aliasse zeigen auf **dieselbe** Boardpresentation und **dieselbe** Taskliste. Keine neue eigenständige vollständige Payloadrepräsentation beim Helperaufruf; deshalb 0 **marginale** Copygatereservationen. Die einmalige Presentationkonstruktion wird dagegen explizit vor dem Boardmaterialisieren zusätzlich gebucht. SQL zählt die tatsächlichen Filteridentitäten vorab; bei den 40 Tasks der ursprünglichen Probe sind es null, mithin **25.424 B** statt der 474.704-B-Obergrenze für 40 verschiedene Filter. Die Taskzahl und 32.768 Aliasgenerationen bleiben unverändert. Filterindexzugriffe und Taskkomparatoren erzeugen keine eigenen Iteratoren bzw. geboxten Vergleichstimestamps je Zugriff/Vergleich. [Unverkürzte Regression](host-results/app-TEST-org.pag.prototype.product.P109RemainingConsumerContractTest.xml), [100/1.000/32.768 UIrow-Aliasse und Quellenwechsel](host-results/app-TEST-org.pag.prototype.product.P110UiProjectionTest.xml).

Das ist keine Behauptung, alle übrigen Producer seien damit kostenlos oder bereits integriert.

## R8/T16 und Rechnungen

Unverändert: **R8 = 8.388.608 B**, **T16 = 16.777.216 B**, `reuseEnabled=false`, `ramReuseEnabled=false`. Die globale exklusive Transitionreservation und die kumulierten lokalen Übergangskosten sind zu unterscheiden; kein neues T16-Slot je Darstellung.

Engineeringgewichte: Objekt 128 B, String 71 B plus 4 B/UTF16-Einheit, Array 71 B plus Payload, Liste 128 B plus 8 B/Kapazität, Map/Index 128 B plus 128 B/Kapazität, Referenz 8 B. Das sind Buchungsregeln, keine zertifizierten ART-Tiefengrößen.

* `TaskPresentation.cost(40, filters=40)` als oberer Fall ohne Quelltextkopien: O=656, S=656, U=20.992, A=672, Arraypayload=172.672, L=88, LC=992, M=1, MC=88, Ref=1.152. Summe **474.704 B**, jeweils zusätzlich retained und transition. Enthält Summary-/Stringbuilder-/Sortier-/Filtermetadaten und konservative Arraykapazitäten; keine 474.704-B-Buchung je Helperaufruf.
* `OperationalPresentation.cost(8,4,256)` ergibt **44.368 B**. SQL misst die verbundenen Rollennamen vor dem initialen Stringjoin. Bei Reprojektion werden bekannte alte Rollenlängen plus ein konservativer neuer Zielplan und gegebenenfalls neue SQL-Zielrollen-/Joinlängen zusätzlich berücksichtigt. Kein neues Rollen-Textlimit.
* UIplanung für `n = Personen + Tasks + Rollen`: **50.624 + 4.352 × n B** vor dem Prüfen/Konstruieren der neuen UIrepräsentation. Danach eigene `WorkspaceUiRows.cost`: tatsächliche neue Displaykardinalität, tatsächliche Quelltextlängen, alle neuen Metadaten-/Map-/Callbackhalter; unveränderte geborgte Displays werden nicht nochmals konstruiert.
* UI-Displaykosten mit `c` tatsächlich neuen Task-/Personendisplays, `n` allen Personen/Tasks/Rollen und `h=1` bei neuem Hub (sonst 0): O=64+1.024h+64c+8n, S=64+256h+32c, U=2.048+16.384h+16chars+512c, A=8+16c, Arraypayload=1.024+2.048c, L=8, LC=32+8n, M=6, MC=16+4n, Ref=128+64n. `chars` summiert tatsächliche geänderte IDs/Titel/Namen/Orte/aufgelöste Zuordnungen und alle Rollennamen. Keine gekürzte Texteingabe oder pauschale Nullkostenannahme für lange Namen.
* Lazy-Builderreservation: O=64, S=4, U=256, L=4, LC=32, Ref=256 = **12.316 B**. `FinishedRowClick`: O=4, Ref=16 = **640 B** je tatsächlichem neuen Wrapper, ohne Refund bei Detach.
* R8 exakt: **7.913.904 + 474.704 = 8.388.608 B**, Producer läuft einmal. +1-Fall: **7.913.905 + 474.704 = 8.388.609 B**, Producer läuft nicht, alte 7.913.905 B bleiben gebucht.
* T16 exakt: Supplementkontrolle **2.560 B**, weiterer Übergangspuffer **16.730.288 B**, Display **44.368 B**, Summe **16.777.216 B**. +1-Fall: weiterer Puffer **16.730.289 B**, erforderliche Summe **16.777.217 B**, Producer läuft nicht. Das ursprüngliche Record bleibt verpflichtet.

[Exakte Raw-Rechnungen und Producerzähler](ledger-host.json), [Budgettests am tatsächlichen `OutstandingCoreRead`](host-results/data-TEST-org.pag.data.product.P110PresentationBudgetTest.xml).

Für Historylabels gilt zusätzlich O=64+32n, S=16+8n, U=2.048+512n, A=4, Arraypayload=1.024, L=4, LC=4n, Ref=64+16n: **19.852 + 6.872n B**, bei 32 Zeilen **239.756 B**. Ein Formatter pro Page; keine 32 Parser.

Boardpresentationen werden nur zusätzlich vorab gebucht, wenn der Producer tatsächlich ein `TaskBoard`/`OperationalBoard` konstruiert. Writer und History-CAS lesen einzelne DTOs/Metadaten; sie konstruieren diese Boardpresentationen nicht. Ihre unveränderten Basis-/Writer-/Pagekosten bleiben gebucht. Der gepatchte Detailboardaufbau wird getrennt durch `WorkspaceConsistency.cost` gebucht; neue Rollen-Joinlängen bleiben im SQL-Zielpreflight. Ein eigener Hostfall prüft die disjunkte Receiptaddition.

Die Konsistenzprüfung verwendet jetzt feste codeeigene Schema-12-SQL-Deskriptoren statt neuer identischer Querystrings pro Zeile. Werte/IDs werden gebunden und nicht als SQLtext gecacht. Der ursprüngliche Requestpayload wird aus der Accesshülle entfernt; deren neue eigene Metadatenhülle ist mit O=21, Ref=72 (**3.264 B**) statt O=20, Ref=64 ausdrücklich gebucht. Neue Vararg-/Bind-/Continuation-/Listen-/Handoff-Kontrollkosten bleiben konservativ additiv gebucht; neue Boardpresentations werden getrennt addiert. Das ändert keine CAS-/BEGIN-/Commit-/UNKNOWN-Regel.

## Host- und P77-Evidenz

**458 Hosttests, 0 Failures/Errors/Skips**, final [Host 33](host-33.log), [Summen](host-summary.json), [Raw XML](host-results). Enthalten: originale §109-Gegenprobe, Quellen-/Callbackidentität, späte Callbackausführung nach Lock/synchronem Interaktionsrevoke, Refusal vor Producer, Single-Membership-SQL-Validation ohne Text-/Blobreads, exakte Grenzen/+1, alte Originalrecord-Verpflichtung, Schema-12-/ABA-/TaskEvent-/Writer-/Modelregressionen sowie Datumsextreme, primitive Sortierung mit stabilen Ties und Capabilities ohne unnötigen Actionpayload. [Reproduktion](run-host.sh).

**35/35 isolierte Gerätefälle PASS auf dem finalen Build 19:** 13 §109-Produktregressionen + 17 §110-Fälle in der seriellen Matrix, anschließend fünf bestehende §107-Fullviewfälle auf demselben APK. [Legacy-5](legacy-build19.log). Finale Matrix: [Matrix Build 19](matrix-build19.json), [Raw Runner](matrix-build19.log), [Skript](run-matrix.py), [Paket-/Signer-/Hashprüfung](apk-install.json), [Build](p77-build-19.log). Alle P77-Anmeldungen verwenden **synthetische Authentisierung**, keine echte DeviceCredential-Zertifizierung. Nur isolierte P77-Ziel-/Testpakete werden installiert.

Die Wiederholungsprobe invalidiert die echte Composehierarchie und prüft vorhandene Accessibilityinhalte. ACTIVE/PREPARATION beobachten zusätzlich tatsächliche Taskrowkompositionen; COMPLETED hat hier keinen instrumentierten Row-Aufbau (Counter 0), sondern 1.000 bestätigte Root-Kompositionen mit gehaltenen Personenreferenzen. Daraus wird kein wiederholter Personen-/Taskrowaufbau abgeleitet. `frames` im Rawlog bezeichnet den Diagnose-/Kompositionszähler, **keinen Nachweis 1.000 physischer OnDraw-/Displayframes oder einer Touchlatenz**. Testaliasse und Diagnosecallbacks sind Testhalter, keine Produkt-Caches und keine Freigaben. ACTIVE/PREPARATION behalten 20 Personen/40 Tasks; COMPLETED verwendet seine vorhandenen Personen und hat keine Tasks. Die ursprüngliche 40-Task-Hostgegenprobe bleibt ungekürzt.

P107-Boundaryfälle unterscheiden jetzt korrekt **initiale Datenadmission** von einer zusätzlichen §110-UIrepresentation: Eine exakt volle Datenbuchung darf nicht zugleich als kostenlose vollständige UIzulassung gelten. Direkter echter Room-/SQLCipher-Read bei R8/T16 exakt wird zugelassen; jeder zusätzliche getestete UIproducer wird vor Konstruktion verweigert. R8/T16 +1 verweigert bereits vor DTO. Keine dieser Boundaryproben behauptet eine vollständige UI bei erschöpftem Datenbudget. Zusätzlich prüfen die vier `ui-r/t-exact/over`-Fälle den tatsächlichen privaten VM-Setter und `WorkspaceUiRows`-Producer nach einem echten Originalread: bestehende Aliasse bleiben gehalten, zusätzliche Planungs-/Darstellungskosten werden exakt bis R8/T16 aufgefüllt oder um ein Byte überschritten. Exakt entsteht ein neuer gebundener UIrepresentation-Member; +1 erzeugt keinen `WorkspaceUiRows` und erhält den alten Slot. Diese Proben beanspruchen ausdrücklich keinen vollständigen generischen Widget-/Framework-Consumervertrag. Der exponierte-Core-Fence bleibt ein eigener unveränderter Negativfall.

| Finale Wiederholungsprobe | Root-Kompositionen | Gemessene zusätzliche Rowkompositionen | Originale Darstellung | R / lokales T nach 1.000 |
|---|---|---|---|---|
| [ACTIVE](device-p110-repeat-active-build19.txt) | 100 / 1.000 | 400 / 4.004 | gleiche Taskliste/Zeile, 32.768 gehaltene Präsentationsaliasse, danach Filter-/Screenwechsel und Lock | 3.228.408 / 6.698.220 B |
| [PREPARATION](device-p110-repeat-preparation-build19.txt) | 100 / 1.000 | 300 / 3.000 | gleiche Taskliste/Zeile, Scrollen, Altaliasse gehalten | 2.682.504 / 5.399.724 B |
| [COMPLETED](device-p110-repeat-completed-build19.txt) | 100 / 1.000 | 0 / 0 (kein Row-Hook) | vorhandene Personenzeile, keine Tasks; Root-/Alias-/Lockprüfung | 1.259.300 / 2.924.312 B |

Die zusätzlichen Buchungen in ACTIVE/PREPARATION stammen unter anderem von tatsächlich neu angehefteten/ausgetauschten Lazy-/Rowwrappern; die 1.000 Root-Kompositionen buchen keine 1.000 vollständigen Tasklisten. Das beweist weiterhin nicht, dass alle generischen Composablehüllen bereits erfasst sind.

[Geschützte Pakete vorher](protected-before.json) und [nachher](protected-after.json) sind unverändert. [Cleanupinventar](cleanup.json) meldet keine `p11-fixture-*`-Roots. Das ist eine Verzeichnis-/Paketprüfung, kein physischer Löschbeweis. Alle 35 Fälle verwenden ausdrücklich **synthetische P77-Authentisierung**.

Die isolierten Geräte-UIgrenzen betreffen die echte VM-Zuweisung und deren `WorkspaceUiRows`-Producer, nicht einen Ersatzledger. Die vorherige Planungsreservation beträgt hier 337.856 B, die neue Darstellung 172.792 B. Metadatenpadding liegt im **selben** Originalrecord; es behauptet keine entsprechende physische Allokation.

| Build-19-Gerätefall | Rechnung nach Planungsadmission | Tatsächlicher Producer |
|---|---|---|
| R8 exakt | 8.215.816 + 172.792 = 8.388.608 B | konstruiert, neuer Slot gebunden |
| R8 +1 | 8.215.817 + 172.792 = 8.388.609 B | nicht konstruiert, alter Slot erhalten |
| T16 exakt | 16.604.424 + 172.792 = 16.777.216 B | konstruiert, neuer Slot gebunden |
| T16 +1 | 16.604.425 + 172.792 = 16.777.217 B | nicht konstruiert, alter Slot erhalten |

[UI-R8 exakt](device-p110-ui-r-exact-build19.txt), [R8 +1](device-p110-ui-r-over-build19.txt), [UI-T16 exakt](device-p110-ui-t-exact-build19.txt), [T16 +1](device-p110-ui-t-over-build19.txt). `full_consumer_claim=false` ist beabsichtigt: Der noch offene generische Form-/Contentvertrag wird dadurch nicht zertifiziert.

Der ACTIVE-Regressionsfall hält außerdem zwei 32er-Historyseiten und den alten Snapshot: initial R=3.186.436 / lokales T=6.656.536 B; nach beiden Pages R=5.574.844 / T=14.132.104 B; nach COMMITTED mit tatsächlich sichtbarem neuen Titel und Revision 1 R=7.142.291 / T=15.846.599 B. Genau ein vollständiger Graph, altes Mutationsticket widerrufen, keine Rückerstattung. [Raw ACTIVE](device-active-build19.txt).

## Fehlläufe und tatsächliche Korrekturen

Frühere Logs bleiben archiviert. Sie werden nicht in die Zahl final bestandener Fälle eingerechnet.

* Host 1: vier Legacy-Taskfälle scheiterten, weil der alte §109-Detailloader ohne Workspaceport hängenblieb. Eine erste bedingte Konstruktorbindung korrigierte diese Tests, war aber produktiv falsch: Runtime bindet den Port später. P77 `task-create` (Build 8/9) deckte den Rückfall in den verweigerten Fullrefresh auf. Bindung erfolgt jetzt in `bindWorkspace()`; zusätzlicher Hostfall prüft einen bestätigten Create mit verweigerter Reprojektion und **keinen** zweiten Commit.
* Host 3: Kotlin-Rücksprung-/Portdeklarationsfehler; Host 4: zusätzliche Buchung und bisher capabilitylose Legacy-Doubles berücksichtigt. Host 8/9: neue Testfixtures/zusätzliche Supplementkontrolle korrigiert. Host 11: explizite Kotlin-Typisierung korrigiert. Host 16/17: abhängige Modellpresentation benötigt den originalen gebundenen Slot; atomare Mutable-Snapshot-Publikation und referenzielle Slotpolicy korrigiert. Host 18: fehlender Import. Keine Fehler ignoriert.
* Host 31: Der Metadaten-Disjunktionstest änderte eine affected-ID um vier UTF16-Einheiten, verglich aber mit dem alten Basisreceipt. Er ermittelt jetzt die tatsächlichen SQL-Metadaten nach der Fixtureänderung; Produktprüfung unverändert.
* Build 1: Testvariantproperty fehlte. Build 5: fehlender Import. Build 13: der vorhandene Harness liefert den Ledgerzustand als String; Boundaryassertion entsprechend korrigiert.
* Build-11-Material-Create: Membership war gebunden, Sichtbarkeitsprüfung prüfte nach asynchronen Scrollaufrufen zu früh. Harness wartet jetzt auf tatsächliche Sichtbarkeit.
* Build-14-COMPLETED: Harness nahm fälschlich mindestens einen Task an; die Fixture hat dort nur Personen. Die Wiederholungsprobe verwendet nun die tatsächlichen Personenzeilen.
* Build 15/17 ACTIVE: zwei Historyseiten funktionierten, der folgende bestätigte Edit ließ wegen zusätzlicher UIkosten keine neue Bindung zu. Daraufhin Datumsparser pro Snapshot statt pro Zeile gebaut und Historyformatter bereits pro Page geteilt; dafür getrennte kontrollierte Konstruktionen und deren Engineeringfaktoren korrigiert. Anschließend tatsächliche SQL-Filterkardinalität statt Taskzahl für Filtermetadaten verwendet, primitive Sortiervergleiche eingeführt, unveränderte Hubkarten geborgt und Boardpresentationen nicht bei reinen Writer-/History-DTOchecks neu verrechnet. Keine Kostenrücknahme, kein Budgetanstieg und kein Abschwächen des positiven Testablaufs.
* Der frühere zusammengesetzte `membership`-Versuch ist ein fehlgeschlagener Diagnoseversuch und kein positiver Mehrfachcommitnachweis. Finale Einzelcases behaupten nur ihre tatsächlich geprüften Abläufe. Unbegrenzte Änderungen bei absichtlich permanent gehaltenen Altaliasen bleiben wegen R8/T16 zwangsläufig endlich.

## Aktualisierte ursprüngliche #37-Akzeptanzmatrix

| Ursprüngliches Kriterium | §110-Befund |
|---|---|
| Navigation/Clears/readWorkspace/Snapshot/Requests/Forms vollständig abgleichen | Originalgraph und neue Detail-/Membershippfade implementiert; **Gesamtvertrag offen** durch benannte generische Controls und Protokoll-/Memory-Folgepfade. |
| Genau eine bewusst geöffnete ACTIVE-Aktion mit allen Bindungen/Tickets | Ein bestehender VM-Slot, gleiche Originalverpflichtung; neue tatsächliche Mutationstickets, keine alte Ticketreaktivierung. Rowcallbacks prüfen zusätzlich Snapshotidentität. Scoped PASS. |
| Nur zugelassener kompakter Kern, keine verbotenen Inhalte/Schlüssel | Kompakter Kern unverändert; zusätzliche Fullview-/UIrepräsentationen bleiben separat am ursprünglichen Fullviewrecord. Keine Core-Readmission, keine Schlüssel-/P3b-/Dokumentpayload im neuen Rowactionvertrag. |
| Gemeinsame Buchhaltung einschließlich alter/neuer/Form-/UIkopien | Konkreter §109-Listenfehler behoben und zusätzliche Producer gebucht; **nicht vollständig**, solange die offen bezeichneten app-eigenen Hüllen/Formatierer/Folgepfade fehlen. R8/T16 unverändert. |
| Synchroner Revoke bei Lock/Fristen/Action/Status/Permission/UNKNOWN | Originalsession-/Writergates unverändert; neue Row-/Slotfences und gezielte Negativtests. Keine allgemeine neue Lifecycle-/Recovery-/Fristenzertifizierung. |
| Acquire/check/publish je RAM-Hit, keine künstliche Sitzungserweiterung | Row-/Snapshotzugriffe verwenden bestehende reale Accessprüfungen. P77 enthält ausdrücklich synthetische Eingabe-/Invalidationsdiagnostik; keine produktive automatische Sitzungserweiterung. |
| Formularzustand/Fokus/geschützte Bereiche und tatsächliches Referenzende | §109-Drafts/Reloads und neue gebundene Rowaktionen erhalten. **Generischer Formular-/Focuscallbackvertrag offen**, kein globaler Endreceipt/Wipebeweis. |
| Host-/Androidregressionen einschließlich Budget/Action/Reauth/OOM | Gezielte Host-/P77-Belege dokumentiert; fehlende Vollmatrix und ART-/Native-/OOM-/Frameworkkalibrierung bleiben offen. |
| Integration und Performanceaktivierung/Produktupdate trennen | Nur WIP-/P77-Integration. Reuse aus; kein Merge/Release/Produktpaketupdate. #38 Touch→OnDraw bleibt separat offen. |

## Kleinster verbleibender Vertrag / keine erfundene Entscheidungssperre

Der nächste konkret abzugrenzende Rest ist die **Präsentationsidentität eines bestehenden Formulars**: insbesondere `FormTextField`-Keyboard-/Focus-/Layoutcallbacks und die generischen Button-/Content-/Semantics-Captures müssen vor ihrer tatsächlichen Konstruktion am Originalowner gebucht oder als bereits gebuchte, identische Hülle geborgt werden. Eine alte Formularhülle darf keinen neuen Entwurf nach Form-/Snapshotwechsel erreichen. Der neue Rowvertrag deckt diesen Vertrag nicht automatisch ab.

Dazu fehlen weiterhin die produktiven begrenzten Protokoll-/Memory-Nachlese- und Membershipfolgepfade. Die vorhandene Option-A-Freigabe ist **kein** Freibrief, deren Refusals als fertige Version 1.0 zu erklären.

**Es wird keine neue A/B-Entscheidung und keine erfundene technische Unmöglichkeit behauptet.** Diese Punkte sind noch nicht fertig implementiert und nachgewiesen. Damit wurde der gesamte §110-Auftrag nicht vollständig erfüllt. Es wird kein weiteres Ticket als Ersatz für die Implementierung angelegt und keine neue globale Endquittierung erfunden. Dieser WIP ist nur unabhängig zu prüfen; #37/#33/#44 bleiben offen.

## #33 / Native / Compose / Framework

Engineeringledger beweisen keine physische ART-/Native-Speichergrenze. Compose-Snapshots/Compilerhüllen, Renderer, Textlayout, IME, Accessibility, Room-/SQLCipher-/Cursor-/Key-Native-Ressourcen brauchen weiterhin eigene Kalibrierung. Auch die tatsächlich noch fehlenden **app-eigenen** Producer bleiben sichtbar in der Matrix; sie werden nicht zu #33 umetikettiert. Keine neue PSS-/OOM-/Touch→Content-OnDraw-Kampagne und keine physische Speicherlöschgarantie.

## Reviewhalt

WIP wird auf dem angeforderten Branch gesichert und gepusht; Issues #37/#33/#44 erhalten denselben unvollständigen Befund. Paket-/Fixtureinventare und [Quellenfingerprints](source-sha256.txt) sind archiviert. Der letzte Quellunterschied nach Build 19 ist ausschließlich ein aktualisierter Kommentar im Membershipvalidator; kein Verhalten wurde nach den finalen Tests geändert. Git-/Push- und Issuereceipts werden im abschließenden Reviewbeleg archiviert. **STOPP zum unabhängigen Review, kein Merge/Release.**
