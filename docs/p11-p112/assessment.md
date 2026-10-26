# §112 – produktiver großer Seiten-/Detailpfad

Basis: `7812ab342fc106c10e32010c6cc39cd035a71fae`, Branch `p11-performance-optimization`.

**Produktcode geändert: ja.** Der bestehende exponierte Core besitzt jetzt gebuchte Childzugriffe für kompakte Personen-/Taskseiten, einzelne Details, Editor-/Expected-/CAS-Folgeprojektionen und die paginierte Personenauswahl im Taskeditor. Die erste Seite referenziert die bereits zugelassenen Corekarten; weitere Seiten verwenden die bestehenden Keyset-Reader mit höchstens 32 Karten. Vollständige Editierdaten werden erst für das gewählte Detail geladen. Der bestehende Task-/Personen-/Rollen-UI-Consumer und Schema-12-CAS bleiben erhalten. Offene und abgeschlossene Tasks haben getrennte Seiten; Chronik bleibt der vorhandene explizite Historypfad.

## Produktnachweise auf P77

Die drei finalen Läufe verwenden dieselben [APKs/Signaturen](install.json) und den [Quellstand](source-final.sha256), echte Room-/SQLCipher-Daten und synthetische P77-Authentifizierung. Seitenwechsel und Kartenöffnung erfolgen durch Accessibility-Klicks auf die echte Produktoberfläche; der Editor-/Speicheraufruf verwendet den bestehenden Produkt-ViewModel-Pfad. Es gibt keinen Ersatzwriter und kein SQL-Testupdate zum Erzeugen des Erfolgs.

| Bestand / Ablauf | Ergebnis | Originalowner nach Ablauf, R / lokale T |
|---|---|---|
| 200 Personen, 1.000 Tasks, 32 Rollen: ACTIVE → Tasks → zweite 32er-Seite → Detail → bearbeiten → COMMITTED → neuer Titel und Revision sichtbar → zurück | [PASS](tasks-final.txt); Revision 17 → 18 zusätzlich direkt in SQL geprüft; neuer Titel auch nach Rückkehr sichtbar | 6.199.801 / 15.518.479 B |
| 200/1.000, 32 Rollen: Personen → zweite 32er-Seite → Personendetail → zurück zur zweiten Seite | [PASS](people-final.txt); ursprüngliche Seite absichtlich als Testreferenz weiter gehalten | 4.469.366 / 13.743.860 B |
| 200/1.000, 100 Rollen, 128.000 gespeicherte Events: zusätzliche Taskseite | [PASS: typisierte, sichtbare T16-Verweigerung](tight-final.txt) | 3.982.035 / 16.333.744 B, einschließlich Refusal-UI |

Jeder finale Lauf bestätigt **einen Core-Read, null Fullworkspace-Materialisierungen, beide Reuseflags false und abgeschlossenes Fixturecleanup**. Die alten Seiten werden im Test gehalten; weder Rücknavigation noch Compose-Disposal erstatten ihre Kosten. Die Personenprobe liest den vorhandenen vollständigen Rollenplan im ausgewählten Detail; fachliche ACTIVE-/PREPARATION-Rechte werden nicht erweitert.

Der schwerste Erstkern hält T = **15.978.280 B**, Rest **798.936 B**. Die tatsächlich angefragte zusätzliche Taskseite benötigt **877.691 B retained / 3.521.712 B transition**. Bereits ohne weitere Kontroll-/UI-Kosten ergäben sich **19.499.992 B T**, also **2.722.776 B über T16**. Zulassung ist damit eindeutig ausgeschlossen. Die finale Quittung nach sichtbarer Verweigerung hält T = 16.333.744 B; ihr Rest von 443.472 B ist ein Nachlaufwert einschließlich Refusal-UI, keine erfundene Messung direkt vor Admission. Alle früheren Kosten bleiben bestehen. Es gibt keinen leeren Erfolgszustand und keine Erhöhung von R8/T16.

## R8/T16 und Sicherheitsvertrag

Der neue Accountingadapter hängt am **ursprünglichen `OutstandingCoreRead.Read`**. Er übernimmt dessen bereits verbuchte Kosten und schreibt jede weitere Buchung in genau diesen Record zurück. Er ist keine zweite Core-Admission, kein neuer Session-/Keyowner und kein zweites Budget. Der ursprüngliche Record bleibt auch nach Mutation/Revoke und bei weiter gehaltenen Referenzen gebucht.

Seiten, Details und History teilen den vorhandenen einmaligen Worker-/Main-Supplementslot. Fehlende Mainquittierung oder unbestätigtes Cleanup gestatten keine nächste Seite. Jede Materialisierung benötigt zusätzliche gemeinsame R8-/T16-Zulassung. SQL-Metadatenprüfung, Prüfung gehaltener Karten/Expected-/Katalogwerte und Payloadlesen erfolgen in derselben autorisierten Readtransaktion. Sessionquelle, READY, Generation, Aktion, Berechtigungen, Writer-/UNKNOWN-Gate und Original-Cancellation werden geprüft; Publication erfolgt erst nach DB-/Keycleanup und erneuter aktueller Autorisierung. Nach einem Write wird der alte Keysetcursor nicht stillschweigend erneuert: Weitere Seiten erfordern bewusstes Laden der ersten Seite.

Ein Detailworkspace enthält nur das gewählte Detail und erforderliche Verweise. Er wird **nicht** als vollständiger Hub oder vollständige Personen-/Taskliste ausgegeben. Der Rückweg zum ACTIVE-Hub bietet die beiden Seitenzugänge; andere Bereiche erhalten aus diesem begrenzten Pfad weiterhin eine ausdrückliche Verweigerung statt erfundener Teilübersichten. Deren allgemeine Produktintegration ist nicht Gegenstand dieser drei Abnahmepfade.

## Gezielt geprüft; vorhandene Kalibrierung weiterverwendet

[Build](build.log), [Hostlauf](host.log), [Host-XML](host): **64 PASS**, null Fehler; ein unverändert historisch deaktivierter 129k-Eventtest ist übersprungen. Auswahl: kompakte Task-/Personenreader, Originalowner-/Childbudget, Lock/Action-/Generationswechsel, gehaltene/alte Callbacks, skalare Seitenkonsistenz und konkurrierendes Schema-12-CAS. Keine 561er-Gesamtauswahl, keine 75er-Gerätematrix und kein 1.000er-Kompositionslauf.

Die [§111-Kalibrierung](../p11-p111/assessment.md), Größen-/Unicode-/Eventmatrix und Native-/ART-/GC-Rohbelege bleiben maßgeblich. Die neuen Läufe sind Funktions- und Admissionnachweise, keine neue physische Heap-/Native-/Renderer-Hardgrenze oder Touch→OnDraw-Messung. #38 und #39 bleiben getrennt offen.

Zwei frühe Taskversuche bleiben rot archiviert: [Testquellenidentität](tasks-first.txt) und [zu frühe vollständige Seitendetails](tasks-source-fix.txt). Die P77-Hülle verwendet für diese drei neuen Proben die ursprüngliche Sessionquelle statt des separaten Fault-Wrappers; keine gelockerte Produktprüfung. Der zu teure Ansatz wurde durch kompakte Seiten plus ausgewähltes Detail ersetzt. [Zwischenstand Task](tasks-lazy-detail.txt), [Person](people-lazy-detail.txt), [Grenze](tight-lazy-detail.txt) sind keine zusätzlichen Abschlussfälle und ersetzen die finalen Rohbelege nicht. Ebenso bleiben die drei `*-pre-hub-fix.txt`-Läufe vor der gezielten Hub-Rückwegkorrektur erhalten; der aktualisierte Hosttest deckt diesen Rückweg ab.

## Entscheidung

**READY_FOR_INDEPENDENT_CLOSURE für #33 im ausdrücklich beauftragten Seiten-/Detail- und Kalibrierungsscope.** Produktcode ist geändert; die beiden positiven großen Abläufe und die echte Budgetgegenprobe sind bestanden. Keine verbleibende funktionale Lücke in diesen Abnahmepfaden. Die Refusal des schwersten Folgereads ist der vereinbarte Budgetvertrag, kein versteckter leergeladener Bestand.

Die vereinbarte Restlücke von #33 (großer Task-/Personen-Seiten-/Detail-/Commitpfad) ist damit geschlossen und zur unabhängigen Prüfung bereit. Andere große Aktionsbereiche werden aus dem begrenzten Modus weiterhin ausdrücklich verweigert, statt einen Detailworkspace als Gesamtbestand auszugeben; deren bereichsübergreifende E2E-Abnahme gehört zu #42. #38/#39 und Release bleiben offen.

**STOPP zum unabhängigen Review. Kein Merge/Release.** Geschützte Pakete und Fixturecleanup: [Abschlussbeleg](cleanup.json).
