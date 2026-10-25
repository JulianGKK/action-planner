# §109 – Option A: produktive Bestandsbearbeitung unter dem Originalowner

**BLOCKED für den Gesamtabschluss von #37; kein `READY_FOR_INDEPENDENT_CLOSURE`.** Die produktiven COMMITTED-/STALE-Abläufe für bestehende Personen, Tasks, Rollenpläne, Material und Kosten sind implementiert. Die unten nachgewiesene verbleibende UI-Consumerlücke verhindert die vollständige R8/T16- und Version-1.0-Abnahme. Option B wurde nicht aktiviert.

Basis: `6b1ad58b4b5da0885a8465a13824c6a0ab3b0cb0`, Branch `p11-performance-optimization`. [Ausdrückliche Option-A-Freigabe](https://github.com/JulianGKK/action-planner/issues/37#issuecomment-6393199228). Ausgangskriterien: [#37](issue-37-before.json), [#33](issue-33-before.json), [#44](issue-44-before.json). Keine neue Session-, Writer-, Recovery-, Key- oder Cacheautorität, keine Schema-13-Migration, keine Kryptografieänderung oder neue fachliche Textgrenze. Keine Netzwerkfunktion in der App hinzugefügt.

## Tatsächlicher Produktdiff

- `WorkspaceRepository.detail()` liest genau eine Bestandsdetailfamilie nach Action und Datensatz-ID: Person, Task, Personenrollenplan, Material oder Kosten einschließlich Zahlungen. Provenienz muss auf denselben ursprünglichen `FullviewEntry` zeigen. Die alte Mutationsepoche bleibt ungültig. Der bestehende Writerowner muss über `beforeActiveRead()` ein **neues** Ticket zulassen; Pending, UNKNOWN oder unsicheres Cleanup werden nicht durch Lesen geheilt.
- `WorkspaceConsistency` prüft weiterverwendete Zeilen innerhalb derselben Room-Readtransaktion durch skalare SQL-Zählungen und vollständige Gleichheitsprädikate. Enthalten sind Actionfelder, Personen, Rollen, Planheader/-zuordnungen, OperationalStates, angefragte ursprüngliche Taskliste sowie bereits vorhandene Material-/Kostenboards. Schema-12-Revisionsfelder werden zusätzlich auf INTEGER geprüft. Die ausgetauschte Zielzeile wird dabei ausgespart. Abweichungen anderer gehaltener Zeilen führen zu STALE **vor** der Zielmaterialisierung. Diese Prüfung liest keine Textspalten in App-DTOs ein.
- Danach folgen im selben Read-Tx SQL-Byte-/Zeilenpreflight, zusätzliche Originalreservation und erst anschließend Action-/Ziel-DTOs. Unveränderte DTOs werden per Referenz übernommen. Neue Listen und Header, Detail, Expected, Formularübergaben und Callbackhüllen werden zusätzlich gebucht. Es gibt weder einen zweiten Operations-/Task-/Workspace-Vollread noch eine zweite Payloadregistry. Die weiterverwendeten Zeilen sind SQL-geprüft; ein alter Snapshot wird nicht pauschal unter einem neuen Ticket als aktuell erklärt.
- Der VM tauscht ausschließlich seinen vorhandenen `workspaceValue`-Slot aus. Es gibt keinen zweiten Detailcache. Das neue `WorkspaceDetail` ist ein Übergabe-DTO. Sein Zugriff wird durch das neue `OriginalWorkspaceAccess` autorisiert. Das alte Accessobjekt bleibt widerrufen. Das ursprüngliche noch aktive Cancellationticket bleibt der gemeinsame Abbruchfence; ein **abgebrochenes** Ticket wird nicht reaktiviert.
- Bestätigter Commit bleibt bis zur erfolgreichen Detailbindung von erfolgreicher Ansichtsaktualisierung getrennt. `commitConfirmed` sperrt einen erneuten Save. Während des Reads erscheint „Gespeichert – Ansicht wird aktualisiert …“, bei Refusal dauerhaft **„Gespeichert – Ansicht konnte noch nicht aktualisiert werden.“** Erfolgsmeldung und neuer Wert werden erst nach neuer Bindung ausgegeben. Ein Refresh versucht keinen Commit.
- Bewusstes STALE-Neuladen ersetzt Expected und Entwurf erst im autorisierten neuen Callback. Bei Refusal bleiben beide erhalten. Personen-Eingabe und Task-Fälligkeit bleiben separat erhalten; kein Last-Writer-Wins. Personen-/Task-/Rollenplanrevisionen werden im Detail bzw. Editor angezeigt.
- `OriginalWorkspaceAccess.consume()` reserviert Kopien **bereits gehaltener** Form-/Callbackwerte unter erneutem READY-/Generations-/Originalrequestcheck. Es gewährt keine frische Datenansicht, SQL-Lesung, neue Expected-Revision oder Schreibberechtigung. Insbesondere macht es `publish()` des alten Mutationstickets nicht wieder erfolgreich. Jeder Writer verwendet weiterhin seine eigene bestehende BEGIN-/CAS-/Completionautorität.
- `OriginalWriterBudget` ist ausschließlich ein Buchungsadapter auf denselben Record. Er bucht vor Freeze/Normalisierung die Eingabe-/Expected-/Result-/Callbackhüllen; im tatsächlichen Writer-Tx folgt vor Store-DTOs ein SQL-Preflight des aktuellen Bestands. Budgetverweigerung im Tx wird bestätigt zurückgerollt. Die bestehenden COMMITTED/ROLLED_BACK/UNKNOWN-/Cleanupregeln wurden nicht ersetzt. Bootstrap ohne kontrollierten Fullview bleibt als bestehender anderer Produktpfad erkennbar und ist **kein** nachgewiesener globaler Budgetgrant.
- Task-/Material-FK-Existenzprüfungen verwenden skalare `EXISTS` statt unnötiger Person-/Rollen-/Task-DTOs. Keine Schemaänderung; nur DAO-Abfragen und entsprechende Hostadapter.

Die Detailpatchliste ersetzt derzeit **bestehende Mitglieder**. Neuanlagen, Löschungen und allgemeine Rollen-/Protokoll-/Memory-Folgeprojektionen sind damit nicht vollständig implementiert/abgenommen. Insbesondere verweigert ein Membershipdelta die Patchbindung, statt eine unvollständige Liste als aktuell zu veröffentlichen. Ein bestätigter Anlagecommit wird dabei nicht wiederholt. Das ist eine verbleibende WIP-Grenze, keine genehmigte Version-1.0-Einschränkung.

## Originalowner und Referenzvertrag

```text
bestehende READY-/Session-/Writerautorität
  -> OutstandingCoreRead.product / ursprünglicher FullviewEntry
       alte Snapshot-/Expected-/Draft-/Callbackkosten bleiben gebucht
       + Submission-/Freeze-/Writerresult-/Callbackreservation
       + SQL-Writerpreflight im tatsächlichen Writer-Tx
       -> bestätigtes Writerende, altes Readticket weiterhin ungültig
       -> bestehender Mutationowner erteilt neues Readticket
       + Konsistenzscratch / neue Listen- und UI-Headerkapazitäten
       + einzelner Detailpreflight im autorisierten Room-Read-Tx
       -> OnceTask Worker -> OnceTask Main -> vorhandener VM-Slot
```

Main-/Worker-Ende quittiert ausschließlich die tatsächlich zurückgekehrte Übergabehülle. Es zieht **keine** Datenkosten ab. Lost Main hält die Zusatzoperation offen; doppelte und verspätete Aufrufe der gleichen Shell führen nicht zu mehrfacher Publication oder Freigabe. Unsicheres Cleanup sperrt konservativ. Lock, Reauth, UI-Wechsel, `workspace=null`, Dispose, GC und VM-Quieszenz erzeugen keinen Endnachweis. Es wurde **keine neue Kostenfreigabe** implementiert, somit auch kein behaupteter globaler Owner-Endreceipt. Neue Wrapper/Listen dürfen alte DTOs weiterhin referenzieren; ihre ursprünglichen Kosten bleiben vollständig erhalten.

Bei unzureichendem Budget, unbestätigtem Owner, falschem Scope oder unbekannter SQL-Konsistenz wird typisiert verweigert. Der exponierte kompakte Core erhält daraus keine Fullview-Readmission. `reuseEnabled=false` und `ramReuseEnabled=false` bleiben unverändert.

## Integrierte benannte Formular-/Writerproduzenten

| Produzent | Vorherige Konstruktion / jetziger Vertrag | Halter und Ende |
|---|---|---|
| Task-Due-Initialformatierung und Parsing | Initialformatierung im Model-Copygate; Textänderung bucht Text/Formatter-/DTOkosten vor `parseTaskDue` und Inputcopy. UI parst/formatiert nicht bei jeder Komposition erneut. Invalides Datum bleibt Entwurf und verhindert Save. | Model-Due/Input und alte UI-Aliase bleiben gebucht. |
| Task-Titel, Priorität, Zuständigkeit und Input | Skalare Produktmethoden; DTO-Copy erst im Gate. Trimmvalidierung zählt ohne Zwischenstring; dynamischer UI-Trimmzähler entfernt. | Alte Inputs nicht durch Wechsel oder Return freigegeben. |
| ExpenseDraft `input()` / `feedback()` | Ableitung nach gebuchter Entwurfsänderung; Save-Input erneut vor Konstruktion gebucht. Screen ruft diese kopierenden Methoden nicht erneut auf. | Abgeleitetes Feedback/Validität im selben Model, alte Ergebnisse bleiben gebucht. |
| Payment-Kopien, Beträge und Feedback | Zahlungszeilen/Listen vor Map/Copy gebucht. Europarser arbeitet ohne Regex/Split/Substrings; Summenprüfung ohne Zwischenliste. Geld-/Zahlungsanzeigetexte werden vor Formatierung zugelassen. | Draft-, Input-, Result- und alte Labelreferenzen bleiben gebucht. |
| Rollen-/Plan-Filter, Sortierung, Sets, Join, Expected | Filter-/Sort-/Joinzwischenlisten für geplante Rollen durch zwei Durchläufe/StringBuilder ersetzt, Ausgabe vorab gebucht. Öffnen/Toggle/Save-Set und Expectedwrapper separat zugelassen. | Plan-Expected bleibt bis bewusstem autorisiertem Reload unverändert; kein Refund. |
| Materialeingaben | Bestehende skalare Copygates bleiben erhalten; Writerinput/aktueller SQL-Bestand/Result-/Callbackkosten zusätzlich. | Entwurf und alle alten Kopien weiter gebucht. |
| Protokolltexte, Personen- und Identifierlisten | Produkt-Screen übergibt Skalare/Index an Modelmethoden; kein `input.copy` oder `mapIndexed` im Textcallback vor dem Gate. BeginDraft, Expected, Savecopy und Identifieränderungen gebucht. | Keine Erfolg-/Entwurfsfreigabe aus Worker-/Main-Rückkehr. Gesamte Protokoll-Folgeprojektion damit nicht abgenommen. |
| Memorytexte/-Optionen/-Maps/-Conflicts | Text-/Optionsobjekte und Collections im Modelgate. Gemeinsame Antworten werden pro Konfliktberechnung einmal hergestellt; Map-/Set-/Codecableitungen und SectionSave vorab gebucht. | Alter Entwurf/Antworten/Conflictentscheidungen bleiben gebucht. Gesamter Memory-Read-/UI-Pfad nicht als freigegeben erklärt. |
| Writer-Normalisierung, Freeze, Expected, Result-DTO, Feedback und Handoffs | Personen/Tasks/Operations/Material/Kosten/Protokoll/Memory binden neue Submissionkosten vor den benannten Kopien; Store-DTOs erst nach SQL-Preflight im Writer-Tx. | Bestehende atomare Writeboundary; Kosten bleiben auch nach realem Return/ACK erhalten. |
| Wiederholte app-eigene Texteingaben | Jeder akzeptierte skalare Producer addiert die neue Darstellung; Refusal erhält den alten Entwurf. Keine neue fachliche Textlänge. | Keine Annahme, dass alte Compose-/IME-/Callbackaliase bereits beendet seien. |

Diese Liste beschreibt die tatsächlich integrierten Produzenten. Sie ist **keine** Vollständigkeitsbehauptung für sämtliche app-eigenen Render-/Compose-Callbackkonstruktionen. Der konkrete Gegenbeleg unten verhindert genau diese Behauptung.

## Exakte R8-/T16-Rechnungen

Unverändert: **R = 8.388.608 B, T = 16.777.216 B.** Nach der bestehenden Engineeringformel:

```text
B(U) = 128*objects + 71*strings + 4*utf16Units
     + 71*arrays + arrayPayloadBytes
     + 128*lists + 8*listCapacity
     + 128*maps + 128*mapCapacity
     + 128*indexes + 128*indexCapacity + 8*references
```

- Jede Originalreserve addiert **getrennt** zum alten R und T. Es gibt keinen pauschalen Abzug nach Commit, Navigation, Readende oder Frame.
- Detail-/SQL-Reservationsformel bleibt `FullviewPreflight.reservation(rows,fields,bytes)` mit vier gehaltenen/sechzehn Übergangsrepräsentationen; UTF-8-BLOB-Längen als konservative UTF16-Einheiten, geprüfte Arithmetik, Counts/MAX vor SUM. Der Ziel-ID-Filter gilt auch für Personen-/Plan-/Material-/Kostenfamilien und Zahlungszeilen.
- Konsistenz-/Patchscratch: `N=32 + Rollen + 4*Personen + Summe Planrollen + Tasks + Materialzeilen + Kostenzeilen/Zahlungen`. Die in `WorkspaceConsistency.cost()` deklarierten Units ergeben **15.188*N B zusätzlich in R und T**. Strings betreffen feste SQL-/Buildertexte, nicht eine erneute Vollmaterialisierung der alten Payload. Die weiterverwendeten alten Payloadkosten werden nicht rabattiert.
- Writer-Submission für `c` Eingabe-/Expected-UTF16-Einheiten und `n` benannte Elemente: **76.448 + 128*c + 9.952*n B zusätzlich in R und T**, anschließend zusätzlich der aktuelle SQL-Storepreflight. Kein separates Writerkontingent.
- Neue Detailübergabe verwendet zusätzlich die vorhandene einmalige Childkontrollreservation (`objects=16,references=64`); VM-Baseline-/Übergabehüllen reservieren außerdem `objects=32,references=128`.
- `P109WriterBudgetTest` konstruiert gültige 72-B-Units (`arrays=1,payload=1`). Ausgang `limit-72` ergibt exakt das jeweilige R-/T-Limit; Ausgang `limit-71` ergibt exakt **limit+1** und muss ohne Änderung alter Charges verweigert werden. Overflow und falsche Action/Generation sind separat geprüft. Ein aktueller großer SQL-Personentext wird vor jedem Writer-DTO budgetverweigert.

[Maschinenlesbare Geräteledgersummen und Restbudgets](ledger-summary.json). `State.transition=16777216` bleibt die konservative Originalverpflichtung; `localTransition` ist der detaillierte kumulative Plan. Die Zahlen sind **Engineering-Ledgerwerte**, keine gemessenen ART-deep-sizes, PSS-/Native-Grenzen oder OOM-Garantie.

## Host-/P77-Nachweise und Grenzen

**383 ausgeführte Hostfälle, 0 Fehler/Errors/Skips**, einschließlich des ausdrücklich als verbleibender Blocker bezeichneten Gegenbelegs. Abschließend [Host 17](host-17.log), [P77-Build 16](p77-build-16.log). [Hostsummen und XML-Verzeichnis](host-summary.json), [Raw-XMLs](host-results), [Geräteversuche](run-summary.json), [13er-Gerätematrix](batch-reviewed.json): **13/13 aktuelle P77-Fälle bestanden**. Zusätzlich fünf aktuelle P107-Regressionsfälle bestanden (Exposed, R/T exakt, R/T +1), ältere APK-Version wie in den Installquittierungen gesichert. Die letzte Matrix nutzt Build 16 und umfasst auch die echte sichtbare Planrevision. Die zuletzt ausgeführten Hostchecks und nativen Builds werden im Summenartefakt benannt. [Reproduzierbares Geräteskript](run-device.py); Ausführung vom Repositoryroot. Das Skript installiert ausschließlich die geprüften P77-Ziel-/Testpakete, prüft Paket-ID/Signer/Hash und geschützte Paketinventare.

P77 injiziert pro isoliertem Fall einen frischen Test-Originalrecord; innerhalb des Falles gibt es keinen Recordreset und alle Ports verwenden denselben Record. Produktion verwendet weiterhin `OutstandingCoreRead.product`. Daraus folgt kein globaler Owner-Endnachweis über Testfälle hinweg. P77 verwendet echten ProductRuntime, bestehende Session, SQLCipher/Room, Repositorys, VM und Compose. **Authentisierung ist synthetisch**, kein DeviceCredential-Nachweis. Navigation und viele Formularbefehle werden über die tatsächlichen VM-/Modelbefehle ausgelöst; Sichtbarkeit über Accessibility geprüft. Personenentwürfe werden tatsächlich per Accessibility eingegeben. Material/Kosten werden bei Bedarf bis zum sichtbaren Eintrag gescrollt. Keine Touch-Latenz-, allgemeine IME-Ende- oder Rendererfreigabe daraus ableiten. Lock/Reauth belegt hier die Verweigerung alter Handles und nicht das erneute erfolgreiche Öffnen einer gesamten Vollansicht nach Reauth.

| Positiver Ablauf | Nachweis |
|---|---|
| PREPARATION Person bearbeiten → COMMITTED → neuer Wert und Revision 1 sichtbar | [Person](device-person-reviewed.txt), alter Snapshot weiterhin als TEST-Alias gehalten, altes Ticket verweigert, Vollgraph insgesamt 1. |
| ACTIVE Tasktitel → COMMITTED → neuer Titel und Revision 1 sichtbar | [Task](device-active-reviewed.txt); echter skalarer SQLCipher-Nachweis Revision 1, unveränderte 4.960 Events des Tasks. |
| Rollenplan ändern → neues Expected/Revision | [Plan](device-plan-reviewed.txt); ergänzende tatsächliche Revisions-/Auswahl-Sichtprüfung [Plan-Revisionsprobe](device-plan-revision.txt). |
| Zwei Editoren → STALE → Entwurf sichtbar erhalten → bewusst neu laden → neuer Text/Revision | [STALE](device-stale-reviewed.txt); konkurrierender echter Writer, kein Erfolg vor Reload, alter Expected als Alias behalten. |
| Material-/Kostenformular → Commit → neuer Wert sichtbar | [Material](device-material-reviewed.txt), [Kosten](device-expense-reviewed.txt). |
| Originalgraphnavigation Person → Plan → Tasks → Material/Kosten → zurück | [PREPARATION](device-preparation-reviewed.txt), ein Vollgraph, Modulmaterializer je 1. |
| Zwei zusätzliche begrenzte Historyseiten innerhalb desselben Originalbudgets | [ACTIVE](device-active-reviewed.txt): 32+32 unterschiedliche Events, alter erster TEST-Seitenalias bleibt gehalten. |
| COMPLETED lesendes Detail; Lock/Reauth | [COMPLETED](device-completed-reviewed.txt). |

| Negativfall | Nachweis / genaue Reichweite |
|---|---|
| Weiter gehaltener alter Snapshot und Expected | Personen-/STALE-/Task-Gerätelogs; alte Accesspublication bleibt verweigert. Keine neue Kostenfreigabe. |
| Lock vor Detailworker | [Lock](device-lock-detail-reviewed.txt): tatsächlicher Worker gehalten, Lock, Freigabe; 0 Datencallbacks. |
| Lost Main / nach Lock verspätete doppelte Callbackausführung | [Lost Main](device-lost-detail-main-reviewed.txt): zweite Projektion verweigert, danach beide verspäteten Aufrufe ohne Value. |
| Doppelte Mainausführung | [Duplicate](device-duplicate-detail-main-reviewed.txt): genau ein Callback/Value, alte Kosten bleiben gebucht. |
| UNKNOWN / unbestätigtes Cleanup | [UNKNOWN](device-unknown-detail-reviewed.txt) injiziert ausdrücklich **Metadatencompletion**, nicht einen behaupteten echten UNKNOWN-DB-Commit. Neue Detailautorität verweigert. Echte Writer-/Rollback-/CAS-/Cleanupregressionen sind Hostbelege; kein neuer vollständiger P77-UNKNOWN-Writerlauf behauptet. |
| Refused Refresh darf keinen neuen Commit auslösen | `TaskModelTest.committedDraftWaitsForDetailBindingAndRefusalNeverRepeatsWrite`: bestätigter Writer, abgewiesener Detailread, Entwurf gehalten, persistente Unterscheidung, erneutes Save verursacht keinen zweiten Write. |
| Action-/Generation-/Permissionwechsel und Foreignthread | `P108WorkspaceAccessTest`, neue `heldAllowed/consume`-Gegenfälle, Writerbudgettests; neue P77-Lock-/Reauth-Gegenfälle. Kein pauschaler neuer Android-Permission-Kompletttest. |
| Exakt R8/T16, jeweils +1; keine DTOs bei Budgetrefusal | `P109WriterBudgetTest`, `P108WorkspaceAccessTest`, SQL-Preflighttests; [Großfixture](device-large-reviewed.txt) mit 0 Vollgraphmaterialisierungen. Bestehende physische P107-Paddingproben werden separat dokumentiert. |
| Exponierter Compact Core bleibt für Fullview gesperrt | Unveränderte Originalrecord-Fences; separate aktuelle [P107-Exposed-Regressionsprobe](device-p107-exposed.txt), soweit im Runsummenartefakt erfolgreich ausgewiesen. |

Alle Probe-Assertions-PASS sind auf die genannten Aussagen begrenzt. Sie bedeuten **keine vollständige Consumer-/Framework-/Nativefreigabe**. Frühere Fehlversuche bleiben erhalten: ACTIVE 01 wurde durch den irrtümlich parallel gestarteten Instrumentierungsversuch unterbrochen (kein erfolgreicher Lauf); Material 01/Kosten 01 scheiterten an der Sichtprüfung, obwohl der neue Modellwert bereits gebunden war; Kostenzeilen können ihren Accessibility-Text zusammenfassen. Lock-detail „final“ wartete fälschlich auf einen Readcallback im bereits LOCKED-Zustand. Die korrigierte Probe authentisiert erneut und benutzt für den verbotenen Vollread die aktuelle Generation; die alten Handles bleiben ungültig. Das erklärt **diese** Fehler, nicht die ältere, weiterhin nicht abschließend geklärte §108-Supplement-Timeoutursache. Build-/Compilerfehler in früheren Logs wurden korrigiert, anschließend neu geprüft; sie werden nicht als bestandene Tests gezählt.

## Genau ein verbleibender Consumer-/Lebensdauervertrag – konkreter Gegenbeleg

**Der vollständige Vertrag für wiederholte app-eigene UI-Präsentationsgenerationen fehlt.** Beispiel: Der produktive ACTIVE-Screen ruft `activeTaskContent()` direkt auf. Dieser Helper erzeugt neue Filter-/Sort-/Summarylisten und Texte, ohne das vorhandene Task-Consumer-Gate zu konsultieren. Die initialen vier/sechzehn Repräsentationen sind keine unbegrenzte Wiederholungsfreigabe. Auch app-eigene, von Compose erneut erzeugte Callback-/Labelhüllen dürfen nicht automatisch zu kostenlosen Frameworkressourcen erklärt werden.

`P109RemainingConsumerContractTest` verwendet denselben produktiven Helper und hält bewusst 32.768 erzeugte Generationen mit je einer 40er-Liste als **TEST-Aliase**. Das angebundene Consumer-Gate wird dabei **0-mal** aufgerufen. Allein die nach bestehender Engineeringformel zusätzlich zu buchenden gehaltenen Listen ergeben:

```text
lists = 32.768
listCapacity >= 32.768 * 40 = 1.310.720
B >= 128*32.768 + 8*1.310.720 = 14.680.064 B > R8
```

Das lässt sämtliche alten Graphkosten, weitere Sort-/Filtertemporaries, Summarystrings, DTOs und Callbacks weg. Dies ist ein reproduzierbarer **Host-Gegenbeleg zur Producer-Vollständigkeit**, keine Behauptung, dass 32.768 reale Android-Kompositionen gemessen wurden, und keine physische ART-Größenmessung. Ein grüner Test dieses benannten Gegenbelegs zählt ausdrücklich **nicht** als Produktbudget-PASS. Er verhindert `READY_FOR_INDEPENDENT_CLOSURE` trotz nun funktionierender tatsächlicher COMMITTED-/STALE-Bearbeitung.

**Konkrete Entscheidungsvorlage für den unabhängigen Review:** Für diese wiederholbare Darstellung muss ein einheitlicher Originalowner-Vertrag festgelegt werden: entweder jede neue app-eigene Präsentationsgeneration vor Konstruktion kumulativ am vorhandenen Entry zulassen, oder eine genau einmal zugelassene unveränderliche Präsentationsrepräsentation innerhalb des bestehenden Owners nachweislich ausschließlich borgen. Callbackhüllen dürfen nur gebuchte Metadaten/IDs halten; ein späterer tatsächlicher Datensatz-/Textzugriff muss erneut autorisiert sein. Neue Listenelemente/Entfernungen benötigen denselben vollständigen Membership-/Generationsvertrag. Keine zweite Snapshotregistry, keine neue Autorität oder Budgetgrenze. Ohne vollständigen Erzeuger- und Halterbeweis weder „kostenlos“ noch eine Freigabe aus Dispose/GC/Main-ACK ableiten. Ein größerer UI-Lebensdauerumbau und seine Borrowingregeln werden hier **nicht stillschweigend als beschlossen oder umgesetzt** ausgegeben. Option A bleibt bindend; dies ist keine neue A/B-Frage und keine genehmigte Funktionskürzung.

## Vollständig aktualisierte ursprüngliche #37-Akzeptanzmatrix

| Ursprüngliches Kriterium | §109-Stand |
|---|---|
| Navigation, Clears, readWorkspace, Snapshot, Requests und Formlebensdauer vollständig abgleichen | Bestandsedit/Reload und endliche Navigation funktionieren auf dem Originalrecord. **BLOCKED gesamt** durch wiederholte Präsentations-/Callbackgenerationen und noch nicht abgeschlossene Membership-/Consumerintegration. |
| Genau eine geöffnete ACTIVE-Aktion mit Action/Status/READY/Permission/Mutation/Load und einmaligen Tickets | **PASS des neuen Pfads:** gleicher Record/VM-Slot, neue echte Mutationstickets, alte Tickets bleiben ungültig. Kein unabhängiger Payload-/Budgetowner. |
| Nur zugelassener kompakter Kern, keine verbotenen Daten/Schlüssel | Kompakter Kern unverändert, Reuse aus, keine Core-Readmission. Fullviewdetails bleiben separat am ursprünglichen FullviewEntry gebucht. **PASS der Trennung**, keine allgemeine Payloadfreigabe. |
| Gemeinsame Budgetbuchhaltung einschließlich Alt/Neu/Compose/Form | R8/T16 und kumulative Store-/Detail-/benannte Producercharges; exakte Grenzen geprüft. **BLOCKED**: dargestellter konkreter Consumer-Gegenbeleg. Historischen 8+8-Text nicht wieder eingeführt. |
| Synchroner Revoke bei Lock, Foregroundverlust, Fristen, Action/Status/Permission, Maintenance/BLOCKED/Recovery | Neue Read-/Publicationgates und geprüfte Lost-/Duplicate-/Lockfälle fail closed. Session-/Timer-/Recoverycode unverändert. **PASS untersuchter Fälle**, keine neue vollständige Android-Fristen-/Recoveryabnahme. |
| Acquire/check/publish je RAM-Hit, keine künstliche Sitzungserweiterung | **PASS der neuen Zugriffe**: frisches Storageacquire; alte Draftkopie ausdrücklich kein frischer Datenhit. Keine Aktivierung von RAM-Reuse. |
| Formzustand/Fokus/geschützte Bereiche und tatsächliches Referenzende | Echter sichtbarer STALE-Draft und bewusster neuer Expected; bestätigter Save von Refresh getrennt. **BLOCKED gesamt**: kein vollständiger Appcallback-/Consumer-Endvertrag. Keine Wipegarantie. |
| Host-/Androidregressionen inklusive Reauth/Lifecycle/Action/OOM/Budget | Umfangreiche gezielte Host-/P77-Matrix mit echten positiven UI-Abläufen. **BLOCKED Gesamtfreigabe**: Consumer-Gegenbeleg und keine vollständige ART-/Native-/OOMkalibrierung. |
| Noch kein Produktupdate; Performanceaktivierung getrennt | **PASS**: nur P77-Ziel-/Testpaket, geprüfte Signer/Inventare, kein Merge/Release, Reuse aus. |

## #33 / Compose / Framework / Native

Die neuen Reservationsfaktoren sind nachprüfbare Engineeringansätze, keine vermessene Heapkalibrierung. Native SQLCipher-/Room-/Cursor-/Key-Scratch, Compose-Snapshots/Compilerhüllen, Textlayout/Renderer, IME und Accessibility sind weder null noch über ein normales Lifecycleereignis freigegeben. Keine neue PSS-/Native-/Frame-/Touchbenchmarkreihe in diesem Patch. Der oben gezeigte **app-eigene** Producer fehlt unabhängig davon; er wird nicht in #33-Frameworkrestkosten versteckt. Keine Behauptung physischer Speicherlöschung.

[Paket-/Signer-/APK-Hashquittierung](apk-install.json), [ältere Installquittierungen](apk-install-1.json), [geschützte Pakete vorher](protected-before.json)/[nachher](protected-after.json), [Fixturecleanup](cleanup.json). Keine Produktinstallation, kein Datenreset und keine physische Wipebehauptung.

## Sicherung und Reviewhalt

Produktdiff, Tests und Rohbelege werden als WIP auf dem verlangten Branch gesichert. Issues #37/#33/#44 erhalten den tatsächlichen Funktionsfortschritt und den konkreten verbleibenden Consumervertrag; keine Issues werden als abgeschlossen markiert. Commit-/Push- und Kommentarquittierungen werden im Sicherungsnachtrag ergänzt.

**STOPP zum unabhängigen Review. Kein Merge, kein Release.**
