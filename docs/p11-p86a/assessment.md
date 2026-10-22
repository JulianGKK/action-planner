# §86a — ACTIVE-Core-Retirement, WIP (#37)

Basis: `0d477bb6d66997429b4d22198bb8e31f38711361`, Branch
`p11-performance-optimization`, Prüfung am 2026-10-22.

**NO-GO für erneute Readmission nach Consumer-Exposure und für Fullview-Rückkehr.**
Kein sicherer kleiner Retirement-Fix aus den vorhandenen Signalen ableitbar.
Produktcode und Gates bleiben unverändert. Geändert sind ausschließlich zwei
Hosttestdateien, dieser Nachweis und der Assessment-Verweis. 60 gezielte Hosttests
und Debug-Build erfolgreich; davon zehn neue Gegentests. Kein Android-Lauf, da kein
funktionaler Fix entstanden ist. Kein behaupteter physischer Freigabe-/Wipe-Nachweis.

## Tatsächlicher Referenz- und Autoritätsvertrag

Die unten verlinkten Produktdateien sind gegenüber der Basis unverändert.

| Station | Tatsächliche Referenzen / Übergabe | Belastbares Signal und dessen Grenze |
|---|---|---|
| [ProductActiveCoreLoader](../../data/src/main/kotlin/org/pag/data/product/ProductActiveCoreLoader.kt) | Ein Loader im Runtime, ein `OutstandingCoreRead`, ein originales Ticket/Lifetime. `begin()` liegt vor READY-Acquire, Ticket und Worker. | Refusal erzeugt keinen neuen Read, keine neue Autorität und keinen Fallback. `reuseEnabled=false` umgeht dieses Admission-Gate nicht. |
| [OutstandingCoreRead](../../data/src/main/kotlin/org/pag/data/product/OutstandingCoreRead.kt) | Worker-/Main-Shell → `Read`; darin atomar leerbare Work-Zellen und genau eine Payload-Zelle. `Read` hat als innere Klasse auch die Referenz zum bestehenden Outstanding-Owner. | `getAndSet(null)` entfernt nur diese explizite Zellreferenz. Ein schon entnommener Body bzw. dessen lokale Variablen leben bis zum Rücklauf weiter. One-shot-CAS verhindert, dass ein Duplikat das noch laufende Original quittiert. |
| Worker / Reader | Ergebnis auf Worker-Stack → `candidate()` → Payload-Zelle; Ticket/Context/Callback über die Work-Zellen. | `workerFinished()` nach Rücklauf des Bodys; Revoke allein beendet keinen bereits laufenden Worker. Unbekannte Queue-Annahme bzw. verlorene Shell bleiben outstanding. |
| Main | `takeForMain()` leert die Zelle, hält Ergebnis lokal. `callbackOwnsValue()` setzt **vor** Aufruf des Consumers `exposed=true`. | `mainFinished()` beendet den Delivery-Abschnitt; `dispatchMain` quittiert erst im äußeren `finally`. Ein Consumer kann den Wert auch bei sofortigem Reject/Close bereits behalten haben. Keine Composition nötig, um Exposure auszulösen. |
| [ProductActiveCoreLifetime](../../data/src/main/kotlin/org/pag/data/product/ProductActiveCoreLifetime.kt) | Originalticket, READY-Context, Owner-Thread, Load-Identität, Lane, ein Invalidierungslistener; keine direkte Payload. Der Listener kann den VM referenzieren. | `close()` einmalig: originales Ticket widerrufen, Listener entnehmen/aufrufen, Memory-Revoke, Subscription entfernen. `isCurrent`/`publish` prüfen weiterhin zentrale Autorität. Close ist kein Rückruf bereits herausgegebener Objektverweise. |
| [ProductViewModel.CoreSlot](../../app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt) | Ein `AtomicReference<CoreSlot?>`; Slot enthält `Value`, Lifetime, Identität und Subscription. `activeCore` gibt den rohen `ActiveCorePayload` zurück. | `getAndSet(null)` / identitätsgebundenes CAS nehmen den Slot aus dem Owner. Ein zuvor gelesener Slot und die aus ihm entnommenen unveränderlichen Werte werden dadurch nicht gelöscht. `CoreSlot.close()` widerruft Autorität, mutiert aber die `val value` nicht. |
| VM-Invalidierung | Worker-Revoke entfernt atomaren Slot vor dem Dispatch von `coreSignal++`. Session-Observer entfernt bei fremdem Thread die Core-Autorität vor der Main-Zustandsaktualisierung. | Neue Zugriffe sind gefenct; ein bereits laufender Compose-/Callback-Leser erhält dadurch keinen rückwirkenden Entzug seiner lokalen Referenzen. Verspätete Signal-Callbacks enthalten keinen neuen Install-Swap. |
| [ActiveWorkspace](../../app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt) | Lokale `core`-Referenz → `CompactActiveContent` → Personen-/Rollen-/Aufgabentexte und abgeleitete HubCard-/Stringwerte. `DisposableEffect` hält die ursprüngliche Lifetime. | `compositionObserved()` wird im Composable-Body aufgerufen, nicht erst nach bestätigtem Render-Commit. Observed/Disposed sind sticky Booleans, keine Zählung aller Consumer. Dispose meldet genau einen Effect-Abbau; es bestätigt weder sämtliche Compose-Referenzen noch Text-/Render-/Native-Kopien. |
| [Storage-Cleanup](../../data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt) | Room-DB, Coroutine-Parent, B2-Driver/Native-Handles; [Reader](../../data/src/main/kotlin/org/pag/data/product/ProductActiveCoreReader.kt) meldet diese an denselben Read. | `storageEnded(true)` erst nach Room-Close, `cancelAndJoin` und Driver-Close/`resourcesClosed`; Fehler bleiben unbestätigt. Das sind bekannte Ressourcenenden, keine Messung freigegebener Allocatorseiten oder Renderkonsumenten. |
| [Key-Scope](../../data/src/main/kotlin/org/pag/data/product/SessionDbKeyOwner.kt) | Lokale Keylease im `withDbKey`; keine Keylease im Core. | Lease wird im `finally` mit Nullen überschrieben; Reader meldet anschließend `keyEnded`. Das zertifiziert weder alle Kopien in Native-Bibliotheken noch den gesamten Prozessspeicher. |

Damit sind vier Aussagen ausdrücklich getrennt:

1. **Autorität widerrufen:** Originalticket nicht live, Lifetime geschlossen,
   Slot entfernt, zentraler READY-/Generation-/Writer-/Lane-Fence weiterhin wirksam.
2. **Explizite Kotlin-Referenzen entfernt:** Work-/Payload-Zellen und VM-Slot leer.
   Keine Aussage „alle Kotlin-/Compose-Referenzen entfernt“: rohe Werte können
   bereits auf Consumer-Stacks bzw. in Compose und abgeleiteten Daten liegen.
3. **Handoffs beendet:** tatsächlicher Rücklauf des einmaligen Worker-/Main-Bodys,
   keine laufende Delivery, bekannte Storage-/Key-Scope-Enden. Cancel oder Dequeue
   allein reichen nicht. Alte Duplikate dürfen keinen neueren Read freigeben.
4. **Render-/Native-Consumer freigegeben:** **kein vorhandener Producer bestätigt
   dies für die ursprüngliche Read-Identität und alle daraus erzeugten Kopien**.
   Dispose, OnDraw, GC, WeakReferences, Sessionwechsel und Activity-Neuanlage sind
   dafür kein Ersatz. Es fehlt außerdem eine kalibrierte Schranke für verbleibende
   native bzw. Präsentationskosten innerhalb des vorhandenen Budgets.

Die neuen Tests halten eine normale Consumer-Referenz gezielt über Close/Dispose
hinweg und können sie anschließend weiterhin lesen. Das ist ein deterministischer
Gegenbeweis gegen „Slot leer ⇒ alle Referenzen weg“, kein simulierter Render-Endpunkt.

## Bestehende Freigabebedingung und Fullview-Gate

Ein **nicht exponierter** Read kann im bestehenden Code erst retire werden, wenn
`revoked && workerEnded && !delivering && !callbackOwned` gilt, ein eventuell
geposteter Main-Body quittiert ist und Storage/Key bestätigt beendet sind.
`exposed=true` verhindert Retirement unabhängig von Observed/Disposed.
`current === this` schützt den neueren Read vor verspätetem Cleanup.
Auch der bestehende nicht exponierte Retirement-Vertrag ist ein Ende bekannter
Ownership-Scopes, keine Behauptung physischer Heap-Löschung.

Erneute Admission verlangt zusätzlich `!closed && !fullView && current == null`.
`state.retirementConfirmed` bedeutet lediglich **kein aktueller Core-Record**;
es ist weder ein globales Retirement-Zertifikat noch ein Readmission-Boolean.
Bei `fullView=true` kann es deshalb zusammen mit `transition=0` true sein, während
der Loader korrekt `FULL_VIEW_UNCONFIRMED` verweigert. Unbekannte Fullviewkosten
sind dadurch nicht null oder bezahlt.

`useFullActiveView()` leert den Core und kündigt Fullview an. `readWorkspace()`
kündigt jeden nicht kompakten Pfad bei gebundenem Core-Port ebenfalls an, also auch
**PREPARATION/COMPLETED ohne vorherigen Core**. Der doppelte Aufruf beim expliziten
Fullview-Einstieg ist idempotent. `clearPersons()` setzt nur das UI-Flag
`fullActiveView=false`, nicht den Loader-Fence. Es gibt kein Fullview-Endsignal,
keine Zuordnung zu dessen asynchronen Read-/Editor-/Consumer-Scopes und keinen
Nachweis, ob gar kein Wert herausgegeben wurde. Ein bloßer Boolean-Reset bei Back,
Fehler, Sessionwechsel oder „History ist jetzt bounded“ wäre daher unbegründet.
Die neuen Tests belegen sowohl diesen unabhängigen Fence ohne Core als auch das
Retirement eines vorher unpublizierten Core bei weiterhin gesperrter Fullview-Rückkehr.

## Minimale umsetzbare Alternative / nächster Slice

**Jetzt bleibt Quarantäne im bestehenden Owner.** Keine automatische Wiederholung,
kein neuer Runtime/Loader als Reset, kein Fullreader-Fallback auf Core-Refusal,
keine Wiederaktivierung von Reuse. Der VM zeigt bei verweigerter Rückkehr bereits
seinen Fehlerzustand statt eine späte Payload zu installieren. Eine gesondert
prüfbare kleine Verbesserung wäre, die terminale Refusal-Ursache typisiert über
den bestehenden Port an diesen Fehlerpfad zu geben; dies wäre ausdrücklich keine
Wiederherstellung der Navigation.

Der kleinste funktionale **Teilvorschlag**, der ohne Render-Retirement auskommen
kann: zunächst nur **nachweislich nicht exponierte, abgebrochene Fullview-Versuche**
freigeben. Dazu den permanenten Boolean im selben Outstanding-Owner durch
identitätsgebundene Fullview-Metadaten ergänzen, ohne Payload-Registry oder neue
Sessionautorität. Alle zugehörigen Workspace-/Detail-Handoffs müssen denselben
Versuch zuordnen; vor jedem Consumer-Aufruf muss Exposure irreversibel markiert
werden. Nur `kein Consumer-Exposure + alle Worker/Main/Storage/Key-Enden bestätigt
+ keine andere Fullview-Verpflichtung + kein exponierter Core outstanding` darf
den Fullview-Fence lösen. Abbruch des Tickets allein reicht nicht. Bereits
abgeschlossene oder ältere Fullview-Versuche dürfen keinen neueren freigeben.
Fehlende Zuordnung, Cleanup-UNKNOWN oder erfolgreiche Exposure bleiben gesperrt.
Das ist ein implementierbarer separater Vertrag für den Abbruchpfad, hier **nicht
implementiert oder als gelöst bewertet**; der aktuelle Fullreader liefert ihn nicht.

Für einen **bereits angezeigten** Core/Fullview löst dieser Teilvorschlag nichts.
Der notwendige nächste Architekturentscheid ist ein kontrollierter
Präsentations-Consumer-Vertrag am ursprünglichen Owner: keine ungezählten rohen
Payload-Borrows, definierte Kotlin-/Compose-Enden und ein tatsächlich vom verwendeten
Renderer/Native-Consumer produzierter, identitätsgebundener Endnachweis (oder eine
nachgewiesene begrenzte Restbelegung). Nur einen `presentationEnded()`-Callback
hinzuzufügen wäre kein solcher Producer. Für die jetzige Compose-Text-Pipeline ist
er in der Integration nicht vorhanden. Keine kleine Reset-/Dispose-Korrektur kann
diese fehlende Garantie schaffen; #37 bleibt P0/offen.

## Ausgeführte Nachweise

Ein gezielter Offline-Lauf, keine historischen Komplettsuites:

```sh
./gradlew :data:testDebugUnitTest \
  --tests org.pag.data.product.OutstandingCoreReadTest \
  --tests org.pag.data.product.ProductActiveCoreLifetimeTest \
  --tests org.pag.data.product.ProductActiveCoreLoaderTest \
  :app:testDebugUnitTest --tests org.pag.prototype.product.ActiveCoreOwnerTest \
  :app:assembleDebug --offline --console=plain
```

| Nachweis | Ergebnis / Umfang |
|---|---|
| [OutstandingCoreReadTest](TEST-org.pag.data.product.OutstandingCoreReadTest.xml) | 16/16; sechs neue Tests: Consumer-Escape trotz Close, Dispose ohne Revoke, unabhängiger Fullview-Fence, Main-Abbruch vor Fullview, Worker vor Start abgebrochen/spätes Duplikat, InterruptedException nach Revoke. Bestehende Tests prüfen laufenden Worker, verlorenen Main, unbestätigtes Cleanup sowie Lock/Action/Generation/Writer/UNKNOWN. |
| [ProductActiveCoreLifetimeTest](TEST-org.pag.data.product.ProductActiveCoreLifetimeTest.xml) | 12/12; reales SessionCoordinator-/Ticket-/Lifetime-Verhalten, Lock/Timeout/Reauth, gezielter Original-Revoke, zentrale Publication false/revoke, Generation/Scope/Writer/Lane. |
| [ProductActiveCoreLoaderTest](TEST-org.pag.data.product.ProductActiveCoreLoaderTest.xml) | 14/14; spätes Main, Lock/BLOCKED/Generation, falsches Binding, Writer, Executor-Rejection/Duplikate und fehlgeschlagene Publication. |
| [ActiveCoreOwnerTest](TEST-org.pag.prototype.product.ActiveCoreOwnerTest.xml) | 18/18; vier neue Tests: entnommene Referenz bleibt trotz Slot-Clear lesbar, Fullview-Rückkehr zeigt Refusal ohne Replay, PREPARATION kündigt Fullview an, alter Generation-Callback kann neueren Slot nicht ersetzen/widerrufen. |
| [Buildprotokoll](host-build.log) | `BUILD SUCCESSFUL in 9s`; Debug-APK-Build geprüft, keine Installation. 60 Tests, 0 Fehler/Skips. |

Data-Tests nutzen den echten Loader/Lifetime/Outstanding-Owner mit synthetischen
Read-Kandidaten; die Sessiontests nutzen den echten Coordinator mit Testauth.
App-Tests nutzen den echten VM mit Domain-Port-Doubles. Die Fullview-Refusal im
VM-Test wird injiziert; ihr persistenter Producer wird separat im Data-Test geprüft.
**Kein integrierter Android-/Compose-/SQLCipher-Retirement-Nachweis.** Worker-Abbruch
im neuen Test ist eine synthetische InterruptedException; bekannte Scope-Enden
werden damit nicht als echter nativer Cleanup-Test ausgegeben.

**PASS nur für die geprüften Autoritäts-/Refusal-Regressionen; NO-GO für #37 und
die beabsichtigte erneute Produkt-Readmission.** `reuseEnabled=false`,
`ramReuseEnabled=false`, 8+8 MiB, READY-/Writer-/Sessionregeln und sämtliche
Produktdateien unverändert. Native-/Frame-Kalibrierung, vollständige Consumer-Enden
und Fullview-Abschluss bleiben Restblocker. Sicheren Test-/Dokumentationsstand als
WIP committen/pushen, Issue aktualisieren, danach STOPP zum unabhängigen Review.
Kein Merge/Release.
