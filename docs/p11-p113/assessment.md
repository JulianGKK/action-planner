# §113 – produktive ACTIVE-Navigation

Basis `11719a78697f4d9222ec23becedd627a466c5dcd`, Branch `p11-performance-optimization`.

**Produktcode geändert; vollständiger Navigationszyklus funktional PASS. #38 noch nicht schließbar: p95 überschreitet 150 ms.**

## Produktänderung

`ProductViewModel`, `ActiveWorkspace`, `ProductEntityList` und `ProductScreen` verwenden die bereits zugelassenen Originalprojektionen für Hub, Personen, Funktionen und Tasks auch im großen `boundedPage`-Modus. Der bisherige dauerhafte `OWNER_UNCONFIRMED`-Bereichswechsel entfällt. Personen/Tasks behalten 32er-Keysetseiten und die §112-Details. Funktionen verwenden denselben vollständigen budgetierten Rollenkatalog und bestehende skalare Präsentationshandles; der Lazy-Consumer komponiert sichtbare Zeilen. Listenheader stehen in einer geordneten Column; Content-OnDraw wird an tatsächlich sichtbaren Datenzeilen beobachtet, auch bei wiederhergestelltem Scrollanker.

Wenn eine Mutation den Originalkern veraltet hat, lesen `OriginalCorePages`/`CompactPeopleStore` nur die fehlende Hubzählung bzw. Rollenprojektion. SQL-Metadatenprüfung und Payload bleiben in derselben autorisierten Transaktion; zusätzliche Childreservation erfolgt vor Materialisierung. `WorkspaceAccess.publishCore` bindet jede gelieferte Projektion an Originalaktion, Generation und exakte Writer-Epoche. Der alte Core bleibt ausschließlich gebuchte Herkunft; er wird nicht nach Mutation neu autorisiert. Kein zweites Fullworkspace, keine Core-Readmission, kein RAM-Reuse, keine Altseiten-/Callback-Rückerstattung.

Echte Bearbeitungsentwürfe blockieren Bereichswechsel bis Speichern/Abbrechen; lesende Details dürfen verlassen werden. Scrollanker bleiben numerisch. `MainActivity`/P77-Activity melden physische Benutzerinteraktion an die unveränderte Session, ohne beim DOWN den für UP benötigten Callback durch eine neue Präsentationsrevision zu entwerten. `ProductInputs` räumt Fokus/IME nur auf, wenn ein Textfeld Fokus hatte. Klick und Inhalt eines Buttons werden jetzt mit einer gemeinsamen konservativen Quote **vor** Konstruktion gebucht; OriginalUiSlot bucht Halter und ersten Sizingdescriptor zusammen, ohne Refund oder neue Budgetautorität.

Die frische Sessionpublication umfasst bei `OriginalWorkspaceAccess.consume` jetzt auch die Reservation: originale Bindungsprüfung innen, Reservierung vor Producer und erneute Prüfung nach Return. Keine spekulative Konstruktion vor Autorisierung. `readBindingLive` ist nur ein widerrufbarer Hinweis für schon gelieferte UI: neue Werte benötigen weiterhin `publishCore`, Konstruktionen/Klickereignisse `consume` mit frischer READY-/Originalcontext-Prüfung. Es ersetzt keine Daten-/Writerberechtigung. R8 **8.388.608 B**, T16 **16.777.216 B**, Session 180 s/30 s, Writer/UNKNOWN/Recovery/Schema12-CAS unverändert.

## P77-Nachweise

Samsung SM-M526BR/API33, isolierte `.p77runtime`/`.p77runtime.test`, synthetische Authentifizierung, echte Room-/SQLCipher-Bestände. Die drei Navigationsfälle verwenden dieselben [APKs/Signaturen](navigation-install.json) und den [Quellstand](source-navigation.sha256).

- [Klein](small-final.txt): 20 Personen/40 Tasks/6 Rollen, fünf vollständige Vor- und Rückzyklen, **40 echte Touchwechsel PASS**. Schluss R 6.511.779 / lokale T 11.379.648 B.
- [Groß](large-final.txt): 200 Personen/1.000 Tasks/32 Rollen, dieselben **40 Touchwechsel PASS**. Schluss R 8.234.731 / lokale T 14.595.192 B. Der verbleibende R-Spielraum ist endlich; alte Generationen werden nicht als freigegeben ausgegeben.
- [Negativ](negative-final.txt): echter Originalledger bis exakt T16 belegt, weiterer Bereichswechsel ergibt typisiert `BUDGET_REFUSED` und belässt den Hub. Die Testauffüllung ist ausdrücklich **Metadaten-Faultinjection, keine physisch allokierte 16-MiB-Messung**. Nach Lock ist der alte Callback unwirksam und der alte Präsentationswert nicht verfügbar.

Zusätzliche gezielte §112-Regressionen wegen geändertem Listenheader/Inputpfad: [Task COMMITTED](tasks-regression-refreshed.txt) inklusive neuer sichtbarer Revision/Titel und Rückseite sowie [Personendetail/Rückseite](people-regression.txt) **PASS**. Der erste [Taskversuch](tasks-regression.txt) fand die Seitensteuerung mit gecachten Accessibility-Knoten nicht; `P112PagesProbe` aktualisiert Knoten jetzt vor jeder Abfrage und wartet auf UI-Idle. Kein Produktgate wurde dafür verändert. Diese beiden Läufe verwenden [dieselbe Produkt-APK, aktualisierte Test-APK](install.json), [finale Quellen](source-final.sha256) und den [Treiberbuild](driver-build.log).

Alle drei Navigationsfälle: **ein Core-Read, null Fullworkspace-Materialisierungen, beide Reuseflags false, Fixturecleanup**. Geschützte Pakete unverändert: [Abschlussinventar](cleanup.json). Frühere rote/zwischenzeitliche Läufe bleiben archiviert; insbesondere `small-ime-fix.txt` ist ein Treiber-API33-Fehler, kein positiver Produktlauf. USB-Transportabbrüche sind separat protokolliert. Keine 75er-Gerätematrix, Speicherstudie oder 1.000-Kompositionskampagne.

## Echte Touch→Content-OnDraw-Zeiten

`P113NavigationProbe` injiziert MotionEvent DOWN/UP auf die echten Produktoberflächen. Beginn ist der Ereigniszeitpunkt des DOWN (Uptime/Nanotime-Abgleich, etwa 1 ms Auflösung), Ende das korrelierte `drawContent`-Ende des Zieldateninhalts. Raw enthält DOWN/UP, Composition, Commit, Layout und Draw. Keine GPU-/Display-Scanout-Behauptung. Je Richtung **n=5**, keine Warmupauslassung; Median und p95 nearest-rank (hier Maximum). Kaltwechsel sind enthalten. Die gezielte Negativprobe mit n=1 ist kein zusätzlicher Latenzabnahmelauf.

| Route | klein p50 / p95 ms | groß p50 / p95 ms |
|---|---:|---:|
| HUB → PERSONS | 148.5 / 228.2 | 148.4 / 217.5 |
| PERSONS → FUNCTIONS | 137.6 / 169.0 | 155.8 / 185.5 |
| FUNCTIONS → TASKS | 143.9 / 153.8 | 134.8 / 165.6 |
| TASKS → HUB | 145.2 / 171.1 | 156.3 / 168.7 |
| HUB → TASKS | 138.4 / 159.4 | 133.1 / 158.5 |
| TASKS → FUNCTIONS | 133.8 / 149.3 | 159.4 / 178.0 |
| FUNCTIONS → PERSONS | 148.9 / 156.7 | 142.5 / 148.8 |
| PERSONS → HUB | 149.6 / 164.9 | 143.0 / 174.5 |

**Restfehler für #38:** Der Touchwechsel baut im nach Route/Screen gekeyten `ProductScreen` den gesamten Bereich einschließlich Header und gebuchten Controls erneut auf; der Content erscheint dadurch auf mehreren Routen erst nach 150 ms. Die bereits behobenen redundanten Prüfungen, IME-Aufrufe, getrennten Buttonreservationen und eager Rollenkomposition senken den kleinen Lauf von anfänglich ca. 223–414 ms Median auf 134–150 ms; sie reichen für p95 nicht aus. Im finalen großen Lauf liegen beispielsweise Funktionenwechsel bei 156/159 ms Median, Hubwechsel bei 143/156 ms. Raw-Stufen: UP→Zielkomposition rund 68–72 ms auf Listenrouten; danach Funktionen-Layout rund 41–46 ms, Hub Commit→Layout rund 43–49 ms (Mediane). Das ist kein SQL-Folgeseitenblocker.

**Nötiger gezielter Fix:** Den gemeinsamen Originalowner-Header/Navigation aus dem pro Route verworfenen Inhaltscontainer lösen und die dafür nötigen Kontroll-/Semanticskonstruktionen gesammelt vorab am selben Record buchen; nur den eigentlichen Inhaltsbereich wechseln. Frische Daten-/Klickpublication und alte Callback-Identitäten müssen dabei erhalten bleiben. Danach dieselben Touchrouten erneut gegen p95 ≤150 ms prüfen. Keine generische neue Speicher-/Stressteststudie. #38 bleibt **OPEN / NOT_READY_FOR_CLOSURE**; die volle historische COMPLETED-1000-Kampagne wurde nicht wiederholt und wird nicht als bestanden behauptet.

## Gezielte Regression und Reviewstopp

[Data-Host/Build](data-host-build.log), [App-Host/Build](app-host-build.log), [JUnit-XML](host): **22 PASS, null Fail/Skip**. Enthalten: Originalprojektion/Epochen-/Session-/Action-/Writer-Gates, reale Funktionen-Childadmission vor Payload, konkurrierendes Schema12-CAS, Formularentwurf, Input-/alte Token-/Focuscleanup, unveränderte bzw. konservativ vollständig gebuchte UI-Quotes. Keine historische Gesamtsuite.

§111-Kalibrierungsbelege gelten weiter. #42 erhält den funktionalen Vier-Routen-Nachweis im freigegebenen Scope, keine E2E-Gesamtfreigabe anderer Produktbereiche. #44 bleibt Release-NO-GO. **WIP zum unabhängigen Review; kein Merge/Release.**
