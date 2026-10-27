# §114 – ACTIVE-Navigationslatenz

Basis `d252d16614c67ea4cfeeb4f0f52b657e946fe777`, Branch `p11-performance-optimization`.

**READY_FOR_INDEPENDENT_CLOSURE für #38.** Alle acht Richtungen bestehen auf kleinem und großem Bestand mit p95 ≤150 ms; gezielte Funktions-/Sicherheitsregressionen bestehen.

## Produktdiff

`ProductScreen`/`ActiveChrome` halten Aktionsheader und Navigation außerhalb des Route-Keys; auch der Lazy-Container bleibt bestehen. Nur dessen Dateninhalt wechselt. Die Countdown-Anzeige hat einen eigenen Restart-Scope. Navigation-InteractionSources werden einmalig vor der ersten Berührung am Originalrecord gebucht.

Hub-Zeilen und vollständige Rollen-Anzeigetexte werden einmal je gültiger Projektionsidentität/Writer-Epoche zusätzlich reserviert, erzeugt und anschließend unverändert publiziert. Personen-/Taskseiten geben höchstens 32 bereits geladene Titel-/Pseudonymreferenzen gemeinsam aus. Die Callbacks der bereits vorhandenen ersten Personen-/Taskseiten (je höchstens 32) werden einmal vollständig reserviert und konstruiert. Wiederverwendung gilt nur für dieselbe referenzidentische Originalkartenliste und gültige Originalbindung. Neue SQL-Seiten bleiben beim bestehenden einzeln gebuchten Consumer. Karten/Paging-Buttons verwenden weniger Layout-/Semantics-Unterbäume bei gleicher Buttonrolle und bestehenden Handlern.

`WorkspaceAccess`/`OriginalWorkspaceAccess` verbinden aktuelle Originalpublication und Reservation vor Konstruktion in `consumeCore`/`consumeCurrent`. Routinghints erteilen keine Daten-/Callbackberechtigung. Jede neue UI-Projektion benötigt aktuelle Session-/Action-/Generations-/Writerpublication; jeder Klick erneut frische Autorisierung. Erstseiten-Fences sperren alte Captures nach Lock, Aktions-/Leseticketwechsel oder Revoke dauerhaft. Keine zweite Workspace-Materialisierung, neue Session-/Writer-/Keyautorität, Schemaänderung oder Lockerung von CAS/UNKNOWN. R8 **8.388.608 B**, T16 **16.777.216 B**, 180s/30s und beide Reuseflags **false** bleiben unverändert. Frühere Generationen werden nicht freigegeben oder gegengerechnet.

Nach 40 großen Wechseln: **R 5.712.037 / lokale T 12.072.498 B**, vorher **R 8.234.731 / T 14.595.192 B**. Einsparung jeweils **2.522.694 B** durch weniger tatsächlich erzeugte UI-Generationen, kein Budgetreset. Dies sind Ownerquoten, keine globale physische Heapgrenze.

## Vorher/Nachher: echte Touch→Content-OnDraw-Zeiten

P77 Samsung SM-M526BR/API33, klein 20P/40T/6R und groß 200P/1000T/32R, echte SQLCipher-/Room-Bestände mit synthetischer Testauthentifizierung. [§113-Instrumentierung unverändert](ondraw-unchanged.sha256): Ereignis-DOWN bis korreliertes Zieldaten-`drawContent`-Ende; kein GPU-/Scanoutnachweis. Je Richtung **n=5 einschließlich Kaltwechsel**, keine Auslassung, Median/p95 nearest-rank. [Klein final](small-rows.txt), [groß final](large-rows.txt), [strukturierte Werte](latency.json); vorher die unveränderten §113-`*-final.txt`-Belege.

| Route | klein §113 | klein §114 | groß §113 | groß §114 |
|---|---:|---:|---:|---:|
| HUB → PERSONS | 148.5 / 228.2 | 100.1 / 133.1 | 148.4 / 217.5 | 104.3 / 131.4 |
| PERSONS → FUNCTIONS | 137.6 / 169.0 | 103.5 / 115.3 | 155.8 / 185.5 | 111.3 / 143.1 |
| FUNCTIONS → TASKS | 143.9 / 153.8 | 101.2 / 112.3 | 134.8 / 165.6 | 103.6 / 126.2 |
| TASKS → HUB | 145.2 / 171.1 | 120.5 / 135.9 | 156.3 / 168.7 | 120.1 / 134.8 |
| HUB → TASKS | 138.4 / 159.4 | 101.4 / 106.4 | 133.1 / 158.5 | 103.0 / 109.4 |
| TASKS → FUNCTIONS | 133.8 / 149.3 | 97.1 / 110.5 | 159.4 / 178.0 | 112.8 / 130.5 |
| FUNCTIONS → PERSONS | 148.9 / 156.7 | 100.6 / 113.9 | 142.5 / 148.8 | 106.7 / 110.9 |
| PERSONS → HUB | 149.6 / 164.9 | 115.7 / 132.8 | 143.0 / 174.5 | 123.2 / 129.2 |

Alle Werte **ms, p50 / p95**. Maximaler finaler Routen-p95: **135,9 ms klein  / 143,1 ms groß**. Auch die acht zusätzlichen Einzelwerte der Negativprobe liegen ≤150 ms (Maximum 141,4 ms; n=1, kein weiterer statistischer p95-Nachweis). Frühere `stable/observed/cards/layout/final/atomic/complete/controls/paging/clock`-Zwischenstände bleiben ausdrücklich mit ihren Fehlern/Überschreitungen archiviert und zählen nicht zur Abschlussabnahme.

## Regressionsergebnisse

Alle fünf abschließenden P77-Fälle verwenden dieselben [APKs/Signaturen](install.json), [finale Quellen](source-final.sha256), jeweils **1 Core-Read / 0 Fullworkspaces**, Reuseflags false und bestätigtes Fixturecleanup:

- Kleine und große Vor-/Rücknavigation: jeweils 40 echte Touchwechsel PASS.
- [Budget/Lock](negative-rows.txt): Originalledger exakt T16 gefüllt → BUDGET_REFUSED ohne Zielwechsel/zusätzliche Datenmaterialisierung; Lock → verspäteter Hubcallback gesperrt. Die Füllung ist Metadaten-Faultinjection, keine physische 16-MiB-Allokationsmessung.
- [Task](tasks-rows.txt): zweite 32er-Seite → Detail → tatsächliches Schema12-COMMITTED → neuer Titel/Revision sichtbar und SQL-geprüft → zurück PASS; R 6.797.703 / lokale T 16.116.381 B.
- [Person/Scroll](people-rows.txt): zweite 32er-Seite, zur Zeile 15 gescrollt → Detail → zurück; dieselbe Zeile ohne weiteren Test-Scroll sichtbar, PASS.

[Build/Hostlauf](build.log), [JUnit-XML](host): **22 PASS, 0 Fail/Skip**. Geprüft: referenzgleiche Controls/Anzeigedaten, Original-Childbudget, Writer-/Epoch-/Session-/Action-Gates, permanent unwirksame alte Captures, Budget vor Producer, konkurrierendes Schema12-CAS, expliziter Formreload, Entwurf und Fokuscleanup. Die alten Handle-Kompositionsmarker sind keine Abnahme-/Retirementquittung des neuen Consumers; tatsächlicher Content-OnDraw ist belegt und der exponierte Originalrecord bleibt gebucht. [Geschützte Pakete unverändert, Fixtures leer](cleanup.json). Keine 1.000-Kompositionskampagne, allgemeine Speicherkalibrierung oder historische Gesamtsuite.

## Abschlussentscheidung

**READY_FOR_INDEPENDENT_CLOSURE – #38**, im beauftragten ACTIVE-Vier-Routen-Scope. Kein verbleibender langsamer Produktpfad in den geforderten finalen Richtungen. #38 zur unabhängigen Schließungsprüfung; #44 bleibt ohne gesonderten Releaseentscheid NO-GO. **WIP zur unabhängigen Prüfung; STOPP zum unabhängigen Review. Kein Merge/Release.**
