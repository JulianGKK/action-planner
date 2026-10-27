# F04 – kleinster notwendiger Vertragsentscheid

**Vorschlag zur unabhängigen Entscheidung, nicht implementierter Vertrag.** Die folgenden Punkte sind keine Freigabe, die bestehenden Sicherheitsprüfungen zu lockern. Keine Aussage, dass eine passende Erweiterung grundsätzlich unmöglich wäre: Mit den heutigen Schnittstellen ist die geforderte Fortsetzung nicht vorhanden.

## Konkrete Grenze

`OutstandingCoreRead` ist zugleich Ressourcenfence und Träger einer einzelnen initialen Projektion. Ein exponierter `Read` wird nach Revoke nicht retired. `begin()` und `beginFullview()` verweigern danach neue Rootadmissionen unabhängig vom freien lokalen R/T-Rest. Das ist die richtige bestehende Ausschlussgarantie, kein zu entfernender Fehlerzweig.

Seine vorhandenen Childzugriffe sind enger als F04:

- `Read.children()` verlangt die noch aktuelle ursprüngliche Lifetime; nach Revoke liefert es keinen neuen Provenienzzugriff.
- `FullviewEntry` trägt unveränderlich die ursprüngliche Action und Generation. `reserveHeld()` verlangt genau diese Identität, auch für spätere Writerbuchungen.
- `OriginalWorkspaceAccess` enthält den ursprünglichen StorageContext, Requeststatus und Readticket. Ein frischer READY-Kontext ersetzt diese nicht.
- `OriginalCorePages` verlangt eine aus dem Compact-Core stammende Entry, ACTIVE und dieselbe Action/Generation. Ein PREPARATION-Fullviewowner kann diese Herkunft nicht liefern.
- `CompactPeopleStore`/`BoundedActiveStore` und normale Taskseiten beweisen ausdrücklich ACTIVE. PREPARATION hineinzureichen oder die Statusprüfung zu entfernen wäre keine zulässige Wiederverwendung dieser Verträge.
- Hub-/Funktions-Präsentationen greifen teilweise zusätzlich auf `acquireCoreScope()` zurück. Ein bloß neu erzeugter Childzugriff ohne gültigen ursprünglichen Coreslot würde noch keinen funktionsfähigen Consumer ergeben.

Damit genügt **weder** „neuen Kontext einsetzen“, **noch** „begin bei freiem lokalen Budget erlauben“, **noch** „Entry auf neue Action/Generation umbenennen“. Letzteres ersetzt nicht die ursprünglichen Context-/Writer-/Request-/Consumerbindungen und muss insbesondere ABA und alte Captures berücksichtigen. Ein neuer vollständiger PREPARATION-Workspace wäre die ausdrücklich verbotene Ausweichlösung.

## Minimal festzulegender Ersatzvertrag

1. **Ressourcenprovenienz statt alter Datenberechtigung:** Der ursprüngliche Record bleibt die einzige Ressourcen-/Buchungsidentität. Ein opaker Provenienzhandle enthält nur diese Identität, keine DTOs, StorageContexts, Schlüssel, Writer oder zweite Quoten. Alle bisherigen R-/T-Kosten bleiben unverändert gebunden. Kein neues `Read`, kein Ersetzen des alten Records, kein Rücksetzen seiner Charges.
2. **Neue Lesebindung als gebuchtes Child:** Eine aktuelle ausdrückliche UI-Anforderung erzeugt nach frischer READY-/Generation-/Action-/Status-/Berechtigungs- und Writerprüfung eine neue, identitätseindeutige Readbindung. Die bisherigen Bindungen bleiben dauerhaft widerrufen, auch bei A→B→A. Der Ressourcenhandle selbst erlaubt keinerlei Datenlesen oder Writeroperation. Metadaten/Callbackhüllen vor Konstruktion buchen.
3. **Eigener begrenzter Einstieg je zulässiger Phase:** ACTIVE darf vorhandene Keyset-/Detailreader verwenden. Der notwendige andere PREPARATION-/COMPLETED-Einstieg benötigt einen explizit phasenrichtigen begrenzten Projektionsvertrag; keine ACTIVE-Bindung umdeuten. Globale Counts, geladene Membership und nächste Seite müssen unterscheidbar sein. Fehlende Projektion ist niemals eine leere Liste oder vollständiger CONTENT. Bestehende Komponenten dürfen keine vollständigen Boards voraussetzen, wenn nur eine Seite zugelassen wurde.
4. **Eine Transition, gemeinsames Ledger:** Vor jedem Materializer alte Charges + neue R-/T-Reservation gegen R8/T16 prüfen; genau ein zulässiger Worker/Main-Handoff. ACK beendet nur diesen Handoff, nicht die alten gehaltenen Repräsentationen. Fehler/UNKNOWN oder unsicheres Cleanup erlauben keinen neuen Grant. Bei fehlendem Restbudget typisierte Verweigerung vor Payload.
5. **Keys/Writer weiterhin zentral:** Frischer DB-Keyowner ausschließlich aus dem normalen aktuellen Runtime-/Credentialpfad. Alte Contexts und Keyowner nie übernehmen. Writerbuchung muss eine aktuelle autorisierte Childbindung auf denselben Ressourcenrecord zurückführen, statt dessen ursprüngliche Action-/Generationsfelder umzuschreiben. CAS, UNKNOWN und Recovery bleiben unverändert; erneute Authentisierung heilt UNKNOWN nicht.
6. **Explicit intent und Publication:** Nur bewusstes Öffnen bzw. bestätigter Phasenwechsel darf den Child-Einstieg anfordern; kein automatischer Retry nach verweigerter Coreadmission. Neue Consumer halten die neue Bindung, nicht den alten Coreslot. Jede Publication prüft aktuelle Requestidentität, Action, Status, Generation, Writer und ursprüngliche Ressourcenprovenienz. Alte Header-/Row-/Form-/Writer-Captures dürfen den neuen Einstieg nicht nachträglich auslösen oder dessen frische Autorisierung borgen.

Schematische Grenze, keine neue implementierte API:

```
ursprünglicher Ressourcenrecord ── unveränderte kumulative R/T-Charges
       ├─ alte Read-/UI-Bindungen: dauerhaft revoked, weiter gebucht
       └─ explizit angefragtes Child
            frische Session + Action/Status + Writer/Recovery
            → gemeinsames R/T-Admit
            → Metadaten und begrenzter Payload in derselben autorisierten Tx
            → neuer identitätseindeutiger Consumergrant
```

**Entscheidend zu bestätigen:** Der „Originalowner“ bezeichnet die fortbestehende Ressourcen-/Buchungsidentität und darf mehrere nacheinander frisch autorisierte, phasenrichtige Childbindungen tragen. Eine alte Aktions-/Sessionbindung ist weder der neue Grant noch ein Freigabereceipt. Für den bislang nicht vorhandenen PREPARATION-Child müssen Datenumfang und Vollständigkeit explizit bestimmt sein.

Dieser Vertragswechsel betrifft Reader, Writerbudget-Provenienz und Consumer zusammen. Ihn nur durch eine Freigabe im Fence oder einen Setter für die alten Identitätsfelder zu simulieren wäre unsicher. In §117 wurde er deshalb nicht als kleiner boolescher Produktpatch ausgegeben. Die drei F04-Zielabläufe bleiben rot. F05/F06/F07 werden dadurch weder behoben noch freigegeben.
