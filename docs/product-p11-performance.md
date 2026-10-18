# P11 — Performance bei unverändertem Sicherheitsvertrag

**Aktueller Abschluss 18.10.2026: Gesamtregression weiterhin BLOCKER.** Der
Semantik-Auslöser ist unabhängig reproduziert und minimal korrigiert: tatsächlicher
Produkt-Footer vorher50/50 Crashes, nachher0/50 bei identischer Test-APK.
Vollständige Aufgabenläufe Schrift1/2, Funktionen und große Listen bestanden;
Aufgaben-Vollpfad Schrift3,5 und P9-Ergänzungsprüfung sind nicht bestanden.
Keine Produkt-/Stufe2-Freigabe. WIP-Ausgangspunkt dieser Untersuchung:
`365341f942c78ec189a3990c160d222a9a18f59d`; ältere Stand-/HEAD-Angaben und
Commitverbote unten sind historisch. Diese WIP-Sicherung ist ausdrücklich beauftragt.
Aktuelle Nachweise: [Assessment27](product-p11-active-workspace-assessment.md#27-unabhängige-semantikeingrenzung-und-gezielte-korrektur);
die widerlegten früheren Lifecycle-Versuche bleiben in Abschnitt26 dokumentiert.

Zweite Klasse-A-Runde: messbare Verbesserungen bei Entsperren/Aktionsöffnung/
Gedächtnisprotokollen; verbleibender Jank und Viewer-Latenzen offen. Manuelle
Abnahme der zweiten Runde und unabhängige Sicherheitsprüfung offen.
Die erste Runde ist unten historisch dokumentiert. Stand: 17.10.2026.
Baseline: `main`, Merge `b3a8c19105e755bc3df6c7207ac0c7e3969702a0`.
Branch: `p11-performance-optimization`; SQLCipher-Schema 11.
Kein Commit, Push, Merge oder Release beauftragt.

## Ziel, Scope und Nachweiskategorien

Die gesamte App untersuchen: Sperrbildschirm/Start, READY-Aktionsliste,
Aktionsöffnung, PREPARATION, ACTIVE, Personen, Aufgaben, Rollen/Funktionen,
Protokoll, Material, Kosten, Gedächtnisprotokolle, Dokumentenliste, JPEG/PNG,
UTF-8/PDF, Navigation, Laden, Statusfeedback, Scrollen und Ressourcen.
Kamera-/SAF-Interaktion und echte Gerätecredential-Authentifizierung gehören
zur getrennten manuellen Produktabnahme; keine automatische Kameraaufnahme
oder Credential-Eingabe und keine Produktdaten als Benchmarkmaterial.

- **GEMESSEN:** reproduzierbare Zahlen mit Methode/Stichprobe; keine Schätzung.
- **BEOBACHTET:** konkreter Code-/UI-Befund ohne quantifizierte Verbesserung.
- **HYPOTHESE:** begründete, noch nicht nachgewiesene Ursache/Optimierungswirkung.
- **NOCH NICHT GETESTET:** offener Nachweis; insbesondere manuelle Produktabnahme.

## Bedrohungsmodell und unveränderte Grenzen

TM-F03 (READY), F05 (Offline-Dateisystem), F06 (RAM/Prozess), F08 (OS/Kernel),
F11 (Journal/Temp) und F17 (Dependencies) bleiben maßgeblich. Dazu F09 (OEM),
F12 (Logs), F13 (Fensterschutz) und F16 (Importpuffer).
180 s absolut, 30 s READY-Inaktivität, sofortiges Foreground-/Screen-off-Lock,
Generation/OPERATION/STEP/COMMIT/PUBLICATION, SessionDbKeyOwner, komplette
SQLCipher-/SQLite-/FK-Integritätsprüfungen, Tink-EOF, DocumentNamespaceLane,
FLAG_SECURE und fehlende persistente Klartexte bleiben unverändert.
Keine zusätzliche Permission, Dependency, Telemetrie, Netzwerkfunktion,
Startup-Initialisierung oder persistenter Performancecache.

Klasse A ist ausschließlich sicherheitsneutral. Klasse B verändert Schutzannahmen
und wird ohne zusätzliche ausdrückliche Freigabe nicht produktiv implementiert.
Dieses Dokument ist keine unabhängige Securityprüfung. Issue #26 bleibt das
verbindliche Gesamt-Release-Gate vor Version 1.0.

## Baseline und Messmethode

Samsung SM-M526BR, Android 13/API 33, Serienziel lokal über `ANDROID_SERIAL`.
Baselineaufnahme: USB versorgt, 100 % Batterie, Batterietemperatur 29,7 °C,
alle drei System-Animationsskalen bereits 0,0; durch P11 nicht verändert.
Build: debuggable, keine Aussage über Releaseoptimierung oder andere OEMs.

Isoliert: `org.pag.perf.p11probe` und `org.pag.data.unlockprobe`.
`P11SyntheticFixture` ist ausschließlich in Debugquellen und prüft die isolierte
Paket-Allowlist **vor** Datei-/Schlüsselarbeit. Sie erzeugt eigene zufällige
Fixturekeys und eine zufällige Unterwurzel im privaten Testpaket. Keine Verwendung
von Produktcredentials, Keystore-Aliasen oder Produktdatenbank. Abschluss widerruft
Fixturecontexts, wischt kontrollierte Schlüssel und löscht nur diese Fixturewurzel.
Synthetische Sessionereignisse dienen der UI-Zeitmessung; sie ersetzen keinen
Nachweis der echten Credential-/Keystore-Latenz oder Sessionregressionstests.

Dataset: drei Aktionen (PREPARATION, ACTIVE, COMPLETED), je 20 synthetische
Personen; PREPARATION/ACTIVE je 100 Tasks, 20 Materialpositionen und 20 Kosten;
ACTIVE zehn Protokolle. Keine P3b-Payloads. Memory-Liste und Rollen leer. Der UI-Benchmark bindet keinen
Leadership-Port; dessen zusätzlicher produktiver Metadatenread ist nicht Bestandteil
der PREPARATION-Zeitreihe. Die atomare Wartegrenze mit Leadership wird separat
durch Hosttests geprüft; daraus wird keine gemessene Produkt-Unlock-/PREP-Zeit.
Viewerfixtures werden im isolierten UI-Paket über die bestehende Importpipeline
verschlüsselt importiert, ohne persistente Klartextquelldatei.

DB: 2 Warmupdurchläufe separat (Index −2/−1), danach 20 Messungen pro Phase;
Bereichsreads zehn Wiederholungen. UI/Viewer/Navigation zehn Wiederholungen.
Cold-/Warm-Start jeweils zehn per `am start -W` ausschließlich im Testpaket;
Cold verwendet Prozessneustart, Warm neue Activity im vorhandenen Prozess.
`TotalTime` ist der Plattform-Startwert, kein vollständiger Nutzer-Interaktionswert.
UI misst Publication und folgenden Framecallback getrennt: letzterer ist eine
technische Annäherung, keine Photon-/menschliche Sichtmessung. Fixtureaufbau und
Dokumentenimport gehören nicht zu den Viewer-Lesezeiten.

Median und P95 nach Nearest-Rank, zusätzlich Minimum/Maximum und Populations-
Standardabweichung. Bei zehn Stichproben ist P95 der schlechteste Einzelwert;
keine präzise Verteilungsbehauptung. Erster Aufruf und Warmup nicht als beliebig
vergleichbare Cold-/Warm-Daten zusammenfassen. Identische Testmethodik vorher/nachher.
Tools: `tools/p11/startup.py`, `tools/p11/summarize.py`, P11DatabaseBenchmark,
P11PerformanceProbe. Rohdaten lokal unter `.prototype-cache/p11-*`, nur feste
Messpunktnamen, Wiederholungsnummern und technische Messwerte; keine Inhalte/IDs.

Framezeiten über den öffentlichen Window-FrameMetrics-Listener, keine zusätzliche
Benchmarkdependency/Startupkomponente. Diese Instrumentation hat eigene Kosten;
kein Anspruch auf messfehlerfreie Release-Framezeiten. Quellen:
[FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics),
[Android-Startzeiten](https://developer.android.com/topic/performance/issues/launch-time).

## Bisherige Architektur und Flaschenhälse

**BEOBACHTET:** ProductViewModel.loadWorkspace stellt mehrere autorisierte
Einzelreads zusammen und publiziert erst den vollständigen aktuellen Snapshot.
Jedes Repository öffnet eigenständig SQLCipher/Room mit vollständigem Preflight.
ACTIVE-Hub: Operations, Tasks sowie fünf Summaries; Actiondetail zuvor separat.
PREPARATION enthält zusätzlich einen gesonderten Leadership-Metadatenread.
Dieser Pfad bleibt von einer möglichen Zusammenfassung fachlicher Reads getrennt:
keine ungefragte P3b-Payload und kein neues Leadership-/Grantverfahren.

**GEMESSEN**, erste abgeschlossene DB-Baseline, 20 Wiederholungen nach Warmup:

| Phase | Median ms | P95 ms |
| --- | ---: | ---: |
| Native Preflight-Öffnung | 578,722 | 582,381 |
| Versionsprüfung | 0,192 | 0,311 |
| cipher_integrity_check | 7,394 | 7,852 |
| integrity_check | 8,252 | 9,019 |
| foreign_key_check | 0,475 | 0,623 |
| Native Room-Öffnung | 583,462 | 593,818 |
| Room-Open einschließlich nativer Öffnung/Schema | 588,041 | 599,608 |
| Query und Transaktion | 2,012 | 2,160 |
| Cleanup | 0,969 | 1,296 |
| Gesamte kurze Operation | 1196,579 | 1211,636 |

Die Opens dominieren diese Fixture. PRE_KEY/POST_KEY-Hookdauer ist separat erfasst;
KDF-/Schlüsselinitialisierung innerhalb der SQLCipher-Open-Blackbox lässt sich
nicht als reine KDF-CPU-Zeit ausgeben. Ebenso ist Room-Open/Schema über öffentliche
Grenzen gemeinsam gemessen; keine erfundene reine Schemavalidierungszeit.
**HYPOTHESE:** Zusammengehörige Reads in einer kurzen autorisierten Operation
können mehrere Opens vermeiden, ohne Prüfungen innerhalb der Operation abzuschalten.
Die folgenden Klasse-A-Änderungen und Nachhermessungen prüfen diese Hypothese.

## Implementierte Klasse-A-Maßnahmen

- Expliziter `WorkspaceReadRequest`: Operations und nur die für die aktuelle Ansicht
  bereits benötigten Boards/Summaries in einer kurzen Read-Transaktion. Dieselben
  Stores, Statusprüfungen und vollständiger DB-Preflight; keine zweite Datenhaltung.
  DB und Key-Lease werden vor Publication geschlossen. DocumentNamespaceLane und
  Maintenance-Ticket umfassen den kombinierten Zugriff. Leadership-Metadaten bleiben
  ein separater autorisierter Read; keine P3b-Payload im neuen Port.
- Atomare Workspace-Publication wartet weiterhin auf alle benötigten Ergebnisse.
  Requestnummer, Generation, Aktion, Status und Bereich werden erneut verglichen.
  Kein neuer über Navigation hinweg gespeicherter Snapshot oder Dokumentcache.
  Bestehende Ports bleiben für nicht gebundene Implementierungen kompatibel.
- Ladehinweis erst nach 200 ms, ohne animierten Balken oder Prozentbehauptung.
  Operationen starten sofort. Das reservierte Layout bleibt stabil; bei Wechsel
  werden bisherige sensible Inhalte weiterhin verworfen. Keine künstliche Wartezeit.
- Statusfeedback als ruhiges Overlay; Accessibility-Timeout und Polite-Ankündigung
  bleiben. Ein Ablauf-Timer statt 50-ms-UI-Updates eines ablaufenden Balkens.
- Tasklisten werden pro Darstellung lokal einmal sortiert statt mehrfach. Keine
  zusätzliche Lebensdauer, keine Änderung an Reihenfolge, Filtern oder Semantik.
  Dies ist zunächst ein Codebefund; ein eigener messbarer Gewinn ist nicht belegt.
- Interne, standardmäßig deaktivierte Phasenuhren: keine Produktlogs, keine IDs,
  Schlüssel, SQL-Parameter oder Parsermeldungen. Ohne Messhook keine Zeitmessung.

## Klasse B — gesonderte Entscheidung, nicht implementiert

Die folgenden Gewinne sind **HYPOTHESEN**, soweit nicht ausdrücklich als Messung
bezeichnet. Es wurden keine unsicheren Varianten in die installierte Produkt-App
übernommen. Die Baseline quantifiziert Openkosten, nicht den sicheren End-to-End-
Gewinn hypothetischer Varianten. Keine Variante erhält durch dieses Dokument eine
Freigabe. Revoke, Deadline und Publication-Gates sind auch mit Freigabe notwendig.

### A. Room länger geöffnet halten

1. Heute: kurze Operation, vollständiger Preflight, Room/Driver schließen vor Ergebnis.
2. Vorschlag: Verbindung über mehrere Benutzeroperationen halten.
3. Erwartet: wiederholte Room-Opens könnten entfallen.
4. Gemessen: einzelne Room-Native-Öffnung ca. 583 ms Median; Varianten-Gewinn nicht gemessen.
5. Trust Boundary: StorageContext/SessionDbKeyOwner, Room-Lifecycle und SQLCipher.
6. TM-F03/F06/F08/F11.
7. Native Schlüssel und DB-Seiten länger im Prozess; bis Pool-/Sessionende statt Operationsende.
8. Größeres RAM-Zugriffsfenster und Rennen zwischen Revoke und parallel genutzten Handles.
9. Exklusiver Owner, streng begrenzte TTL, Revoke-Barriere und nachweisbarer Join/Close nötig.
10. Native Kopien und OEM-Verhalten bleiben; Gates löschen keine physische RAM-Historie.
11. Revoke in Query/Transaction/Close, generationsübergreifende Wiederverwendung, Crash/FD/RAM testen.
12. Empfehlung: vorerst nicht; sichere Bündelung ausschöpfen.
13. Ausdrückliche Benutzerfreigabe und Sicherheitsreview erforderlich.

### B. SQLCipher-Preflights seltener

1. Heute: native Read-only-Öffnung und vollständige Checks je DB-Operation vor Room-RW-Open.
2. Vorschlag: Ergebnis für weitere Operationen als gültig ansehen.
3. Erwartet: eine native Öffnung plus Checks pro ausgelassener Prüfung sparen.
4. Gemessen: native Öffnung ca. 579 ms, beide Integritätschecks zusammen ca. 16 ms;
   keine freigegebene Variante gemessen, diese Kosten sind keine Gewinnzusage.
5. Trust Boundary: erneute Dateisystemprüfung und Manipulationserkennung vor RW-Zugriff.
6. TM-F03/F05/F08/F11.
7. Mit Prüfungs-Cache neue Gültigkeitsdauer; Schlüssel müssen dafür nicht länger leben.
8. Änderung nach letzter Prüfung kann erst später auffallen; RW-/Recovery-Zugriff vorher möglich.
9. Eindeutige Dateiidentität, Mutationserkennung, Invalidierung bei jedem Write/Recovery nötig;
   einfache mtime ist kein gleichwertiger Integritätsnachweis.
10. TOCTOU und privilegierte Manipulation bleiben.
11. Cipher-/SQLite-/FK-Fehler zwischen Reads, Journal-Recovery und Austausch/Symlinks testen.
12. Empfehlung: nicht als neutrale Optimierung übernehmen.
13. Ausdrückliche Freigabe des geänderten Erkennungsfensters und Sicherheitsreview erforderlich.

### C. Integrität beim Unlock statt je Operation

1. Heute: Prüfung am jeweiligen Zugriff, nach READY-/Operationsgate.
2. Vorschlag: einmalige Prüfung beim Unlock als Sessionnachweis.
3. Erwartet: wiederholte Preflightkosten sparen, dafür längeres Unlock.
4. Gemessen: nur heutige Einzelphasen; neue Unlock-/Gesamtlatenz nicht gemessen.
5. Trust Boundary: von Operationsgültigkeit auf Sessiongültigkeit verschoben.
6. TM-F03/F05/F08/F11.
7. Prüfergebnis bis Sessionende; Key-/Klartextdauer nur bei zusätzlichem Pooling verlängert.
8. Spätere Korruption oder Austausch vor Write wird nicht unmittelbar erkannt.
9. Harte Invalidierung nach Mutation, Recovery und Background, unabhängiger Identitätsnachweis nötig.
10. Gleiche Session bedeutet nicht unveränderte Dateien.
11. Alle Integritätsfehler nach Unlock, Revoke während Scan, Wiederanmeldung und Writes testen.
12. Empfehlung: offen lassen; Sicherheitsvertrag wäre verändert.
13. Ausdrückliche Freigabe und unabhängige Prüfung erforderlich.

### D. Kurzlebige entschlüsselte Dokumentcaches

1. Heute: bewusster Zugriff, vollständige Tink-Authentifizierung je Seitenfreigabe.
2. Vorschlag: authentifizierten Klartext für Folgeinteraktionen zwischenspeichern.
3. Erwartet: Decryption/Formatprüfung sparen; DB-Gates bleiben separat nötig.
4. Gemessen: kein Cachebenchmark; keine seriöse Prozentangabe.
5. Trust Boundary: Ciphertextblob zu längerlebendem RAM-Klartext und Dateiversion.
6. TM-F03/F05/F06/F08/F11.
7. Klartext über aktuelle Operation hinaus, zusätzliche Buffer/Owner/TTL.
8. RAM-Exposition, stale Inhalt nach Delete/Blobänderung und späte UI-Publication.
9. Begrenzter Cache, Versionsbindung, Revoke-/Delete-Invalidierung und Cleanup nötig.
10. Vollständiges physisches Löschen von RAM-/OS-Kopien bleibt unbeweisbar.
11. Lock/Delete/Mutation während Cachehit, falsche Generation, Speicherdruck testen.
12. Empfehlung: vorerst ablehnen; Messung muss Nutzen gegenüber Risiko rechtfertigen.
13. Ausdrückliche Freigabe neuer Klartextlebensdauer erforderlich.

### E. PDF-memfd über Seitenwechsel behalten

1. Heute: jeder bewusste Seitenzugriff authentifiziert vollständig, eigener memfd-Lifecycle.
2. Vorschlag: PDF-memfd für mehrere Seiten verwenden.
3. Erwartet: Authentifizierung und Befüllen bei Folgeseiten sparen.
4. Gemessen: heutige Seitenzeiten separat; Wiederverwendungsgewinn nicht gemessen.
5. Trust Boundary: verifizierter Einzelrequest zu längerlebendem Rendererinput.
6. TM-F03/F05/F06/F08.
7. Bis 25 MiB Klartext und FDs länger vorhanden, neue Viewer-/TTL-Bindung.
8. Stale Blobversion, Delete-/Lock-Rennen und größere RAM-Angriffszeit.
9. Eindeutiger Owner, harte Revoke-/Delete-Gates, begrenzte Lebensdauer und FD-Abbruch nötig.
10. memfd ist anonym; keine Garantie gegen OS-Swap/Kopien oder physischen RAM-Rest.
11. Lock/Navigation/Crash/Delete über viele Seiten, FD-Leaks und Ciphertextänderungen testen.
12. Empfehlung: ohne gesonderten Review nicht übernehmen.
13. Ausdrückliche Freigabe der längeren Klartextlebensdauer erforderlich.

### F. Isolierten PDF-Renderer wiederverwenden

1. Heute: requestgebundene isolierte Service-/Parserressourcen, keine DB/Keys im Renderer.
2. Vorschlag: Service und/oder Parser zwischen Requests halten.
3. Erwartet: Bind-/Prozessstart-/Parserkosten reduzieren.
4. Gemessen: keine Variantenmessung; heutige Gesamtdauer ist kein reiner Bind-Messwert.
5. Trust Boundary: Parserprozess, Binder und requestgebundene Eigentümerschaft.
6. TM-F03/F06/F08/F09/F17.
7. Parser/native Daten länger vorhanden; bei Parserreuse auch PDF-Klartext länger.
8. Korrumpierter Parserzustand könnte Folgedokumente beeinflussen; längere Angriffszeit.
9. Harte Prozess-/Requestgrenzen, Crashwatchdog, TTL, Ressourcenbudget und Revoke-Ack nötig.
10. Native Parserrestrisiken und OS-Prozessende sind nicht vollständig kontrollierbar.
11. Crash/Binder-Death, Lock in nativen Aufrufen, späte Antworten und Cross-Request-Zustand testen.
12. Empfehlung: Isolation unverändert erhalten; Service-/Parserreuse gesondert entscheiden.
13. Ausdrückliche Freigabe und unabhängiger Parser-/Lifecycle-Review erforderlich.

### G. Prefetch sensibler Daten

1. Heute: aktions- und bereichsgebundene bewusste Auswahl, keine Viewer-Prefetches.
2. Vorschlag: noch nicht ausgewählte Bereiche/Dokumente vorladen.
3. Erwartet: ausgewählte spätere Navigation schneller, mehr Arbeit und Speicher vorher.
4. Gemessen: kein Prefetchbenchmark, Trefferquote unbekannt.
5. Trust Boundary: Bedarf/Benutzerentscheidung und Publish-/Löschversion.
6. TM-F03/F06/F08/F16.
7. Zusätzliche personenbezogene Daten/Klartexte vor Auswahl länger im RAM.
8. Unnötige Exposition, Worker-Verdrängung, Stale-Daten, RAM-/GC-Spitzen.
9. Strikte erlaubte Datenklassen, Mengen/TTL/Versionierung; niemals ungefragte P3b-Payload.
10. Nicht genutztes Prefetch bleibt unnötige Datenverarbeitung.
11. Keine Auswahl, schneller Aktionswechsel, Lock/Delete, Speicher-/Queue-Last testen.
12. Empfehlung: bedarfsgerechte Bündelung bevorzugen, keinen sensiblen Prefetch hinzufügen.
13. Ausdrückliche Freigabe des geänderten Bedarfs-/Lebensdauervertrags erforderlich.

### H. Schlüsselableitung/kryptografische Parameter ändern

1. Heute: bestehende etablierte SQLCipher-/Tink-/Credential-Parameter unverändert.
2. Vorschlag: KDF/HMAC/Segmentparameter reduzieren oder ableitungsbezogene Caches hinzufügen.
3. Erwartet: eventuell geringere Open-/Ableitungskosten.
4. Gemessen: separater Raw-Key-Vergleich frischer Test-DBs unten; keine reine KDF-CPU-Zeit und keine Änderung von KDF-Iterationszahlen getestet.
5. Trust Boundary: kryptografischer Schutz gespeicherter Daten und Credential-/Key-Lifecycle.
6. TM-F03/F05/F06/F08/F17.
7. Parameteränderung nicht zwingend längere Lebensdauer; Cache würde Keydauer verlängern.
8. Schwächerer Offline-/Manipulationsschutz oder zusätzliche RAM-Schlüsselkopien.
9. Gesondertes Kryptoreview, Kompatibilitäts-/Migrationskonzept und begründete Mindeststärke nötig.
10. Sicherheitsniveau lässt sich nicht aus einem Latenzbenchmark ableiten.
11. Falsche Keys/AAD/Tags, Downgrade, Migration/Recovery und vollständiger Schlüsselcleanup testen.
12. Empfehlung: Parameter nicht pauschal reduzieren; offiziellen Raw-Key-Pfad separat priorisiert reviewen, keine Performancefreigabe als Kryptoreview.
13. Ausdrückliche Sicherheitsentscheidung erforderlich; in P11 nicht implementiert.

## Bewusst unveränderte Bereiche und offene Messgrenzen

**BEOBACHTET:** Aktionsliste und Actiondetail sind jeweils bereits einzelne kurze
DB-Operationen. Ein Pool oder seltenerer Preflight wurde nicht eingeführt. Navigation
zu Protokoll/Material/Kosten enthält nach dem Workspace zusätzlich den autorisierten
Bereichsread; beide Gates bleiben. Personen-/Task-/Rollen-, Protokoll-, Material-,
Kosten- und Memory-Ansichten profitieren vom gemeinsamen Workspacepfad, nicht von
ungefragtem Laden weiterer Bereiche. Writes und deren gezielte bestehende Reloads
bleiben unverändert; keine neue persistente Invalidierungsstruktur.

**BEOBACHTET:** Bild-, Text- und PDF-Reads enthalten bereits einen kurzen DB-Snapshot
vor der Datei-/Renderarbeit. P11 bündelt keine Viewerinteraktionen. Tink-EOF,
Textgrenze 32 KiB, PDF-25-MiB-Grenze, isolierter Parser, begrenzte Einzelbitmap und
requestgebundene memfds bleiben. Textseitenwechsel authentifiziert weiterhin den
ganzen Blob. PDF-Pixeltransfer besitzt begrenzte zusätzliche ByteArray-/Bitmapkopien;
8 MiB Ausgabebitmap begrenzen nicht den gesamten nativen Speicher. Keine vermeintliche
Beschleunigung durch längeres Halten von Klartext oder Parsern.

**MESSGRENZE:** Die UI-Zeitreihe misst Viewer-Endpunkte. Die ergänzende
Phasenprobe unten zerlegt Bounds/EXIF/Clientpixeltransfer; native Serviceallokationen
und reiner Prozessstart bleiben nicht vollständig isoliert. Aus End-to-End-Zeiten
wird kein separater KDF-, Tink- oder IPC-Gewinn abgeleitet.
Echte Credential-/Keystore-Authentifizierung, OEM-Kamera und SAF-Navigation werden
nicht mit Produktdaten automatisiert vermessen. Memory-Liste und Rollen sind im
synthetischen UI-Dataset leer; Aussagen über große befüllte Bestände bleiben offen.
Keine belastbare Release-/Baseline-Profile-Wirkung gemessen; daher keine neue
Profile-/Macrobenchmarkdependency und kein zusätzliches Startupverhalten eingebaut.

Große Tasklisten bleiben in der bestehenden Scrollstruktur. Ein Umbau in verschachtelte
Lazy-Container ohne getrennten Fokus-/Scroll-/Schriftgrößennachweis wurde nicht als
sicherer lokaler Gewinn ausgegeben. Die Frame-/Scrollzeitreihe quantifiziert das
verbleibende Verhalten. Datumsformatierung, Listener und Workerwechsel wurden anhand
der betroffenen Pfade betrachtet; ohne nachgewiesenen Engpass kein zusätzlicher
Cache, Threadpool oder umfangreicher Architekturumbau.

## Ergebnisse der abgeschlossenen UI-Zeitreihen

**GEMESSEN:** beide vollständigen Probeabläufe PASS, je zehn Wiederholungen.
Zeiten bis zum nächsten Framecallback nach Publication, keine Photonmessung.
Die PREPARATION-Fixture enthält keinen Leadership-Port (siehe Methodik).
| Messpunkt | n vorher/nachher | Median vorher/nachher ms | P95 vorher/nachher ms | Δ Median ms | Δ Median % | σ vorher/nachher ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ACTIVE_COSTS_FRAME_CALLBACK | 10/10 | 4824.22/2472.51 | 4876.62/2493.92 | 2351.72 | 48.7 | 25.51/16.79 |
| ACTIVE_FUNCTIONS_FRAME_CALLBACK | 10/10 | 1260.04/1221.17 | 1442.91/1441.20 | 38.87 | 3.1 | 67.34/76.30 |
| ACTIVE_HUB_FRAME_CALLBACK | 10/10 | 9558.40/2421.75 | 9576.24/2446.93 | 7136.65 | 74.7 | 17.57/18.24 |
| ACTIVE_MATERIAL_FRAME_CALLBACK | 10/10 | 4809.03/2438.28 | 4848.06/2478.95 | 2370.75 | 49.3 | 19.64/22.22 |
| ACTIVE_MEMORY_REPORTS_FRAME_CALLBACK | 10/10 | 3608.70/2417.44 | 3668.34/2424.89 | 1191.26 | 33.0 | 22.81/8.75 |
| ACTIVE_PERSONS_FRAME_CALLBACK | 10/10 | 2441.97/1276.93 | 2518.13/1372.90 | 1165.04 | 47.7 | 29.27/34.80 |
| ACTIVE_PROTOCOL_FRAME_CALLBACK | 10/10 | 4813.70/2419.98 | 4828.12/2451.58 | 2393.72 | 49.7 | 13.66/13.84 |
| ACTIVE_TASKS_FRAME_CALLBACK | 10/10 | 2543.61/1363.36 | 2605.47/1424.07 | 1180.26 | 46.4 | 54.12/45.01 |
| BACK_FRAME_CALLBACK | 10/10 | 94.02/35.14 | 267.66/119.98 | 58.88 | 62.6 | 68.86/31.97 |
| DOCUMENT_BACK_FRAME_CALLBACK | 10/10 | 8365.11/1252.47 | 8398.04/1268.61 | 7112.64 | 85.0 | 22.44/17.57 |
| DOCUMENT_LIST_FRAME_CALLBACK | 10/10 | 1240.54/1241.48 | 1267.15/1257.58 | -0.94 | -0.1 | 13.20/13.77 |
| FRAME_LAYOUT | 2671/2858 | 0.08/0.08 | 0.14/0.14 | -0.00 | -0.0 | 0.20/0.18 |
| FRAME_TOTAL | 2671/2858 | 7.63/7.73 | 44.01/41.55 | -0.09 | -1.2 | 24.84/24.44 |
| JPEG_FRAME_CALLBACK | 10/10 | 1238.92/1231.34 | 1250.07/1249.23 | 7.59 | 0.6 | 11.82/7.78 |
| PDF_FIRST_FRAME_CALLBACK | 10/10 | 1600.69/1599.59 | 1625.27/1623.27 | 1.10 | 0.1 | 18.12/20.33 |
| PDF_NEXT_FRAME_CALLBACK | 10/10 | 1593.31/1587.14 | 1606.68/1609.90 | 6.16 | 0.4 | 13.80/13.10 |
| PNG_FRAME_CALLBACK | 10/10 | 1241.13/1244.48 | 1272.89/1257.47 | -3.35 | -0.3 | 17.37/8.63 |
| PREPARATION_FRAME_CALLBACK | 10/10 | 7167.56/2417.49 | 7185.78/2471.31 | 4750.07 | 66.3 | 14.64/20.13 |
| PREP_PERSONS_FRAME_CALLBACK | 10/10 | 1253.92/1252.46 | 1363.11/1317.08 | 1.46 | 0.1 | 36.90/27.50 |
| PREP_TASKS_FRAME_CALLBACK | 10/10 | 2516.33/1356.59 | 2630.16/1577.25 | 1159.75 | 46.1 | 57.99/80.29 |
| READY_LIST_FRAME_CALLBACK | 10/10 | 1209.78/1211.14 | 1251.73/1242.73 | -1.36 | -0.1 | 15.80/12.35 |
| SCROLL_FRAME_TOTAL | 1612/1610 | 7.13/7.09 | 11.77/11.57 | 0.04 | 0.5 | 16.39/16.17 |
| SCROLL_TASKS_READY_FRAME_CALLBACK | 10/10 | 2508.80/1367.90 | 2694.80/1483.14 | 1140.89 | 45.5 | 77.48/74.11 |
| TEXT_FIRST_FRAME_CALLBACK | 10/10 | 1251.59/1233.88 | 1300.39/1261.82 | 17.72 | 1.4 | 17.59/15.93 |
| TEXT_NEXT_FRAME_CALLBACK | 10/10 | 1234.30/1250.55 | 1251.79/1260.16 | -16.25 | -1.3 | 11.45/9.54 |

Metadaten: {'baseline': {'THERMAL_START': 0, 'FD_START': 84, 'NATIVE_START': 5657136, 'THERMAL_END': 0, 'FD_END': 139, 'NATIVE_END': 0, 'FRAME_DROPPED': 0}, 'after': {'THERMAL_START': 0, 'FD_START': 84, 'NATIVE_START': 5634128, 'THERMAL_END': 0, 'FD_END': 139, 'NATIVE_END': 2950048, 'FRAME_DROPPED': 0}}

Große Verbesserungen betreffen die Anzahl kurzer DB-Operationen, nicht reduzierte
Prüfungen pro Operation. ACTIVE-Hub einschließlich Actiondetail: acht statt zwei
Operationen; Dokument-Rückkehr sieben statt einer; PREPARATION-Fixture sechs statt
zwei. Im Produkt kommt der getrennte Leadership-Metadatenread unverändert hinzu.
Bereichsboards behalten ihren eigenen Zugriff. Kein belastbarer Gewinn bei
READY-Liste, Dokumentliste, JPEG/PNG, Text-/PDF-Öffnung oder Viewer-Seitenwechsel;
kleine positive/negative Differenzen sind Messstreuung. Der schnellere BACK-Endpunkt
ist eine Beobachtung, kein isolierter Nachweis einer bestimmten CPU-Ursache.

Frames: vorher 2671, nachher 2858. Überschreitung der zugehörigen positiven
FrameMetrics.DEADLINE: 556/2671 = 20,82 % bzw. 538/2858 = 18,82 %. Kein verlorener
Listenerreport gemeldet. Frame-P95 44,01 → 41,55 ms; Scroll-P95 11,77 → 11,57 ms.
Scrollmedian praktisch unverändert. Einzelspitzen bleiben; kein Nachweis vollständig
jankfreier UI. Unterschiedliche Zahl/Art gerenderter Frames und debuggable
Instrumentation verhindern eine reine kausale Jank-Prozentzuordnung zur Bündelung.
Keine unabhängigen Bildschirmphotonen-/TalkBack-Messungen.

Ressourcen im gesamten UI-Lauf: beide 84 → 139 FDs, kein zusätzlicher Anstieg durch
P11 in dieser Reihe. Das ist kein Beweis unbegrenzter Leakfreiheit. Nativeheap vorher
5.657.136 → **0** Bytes, nachher 5.634.128 → 2.950.048 Bytes. Der Null-Endwert der
Baseline ist kein verwertbarer Nachweis gelöschten Speichers; daraus wird keine
RAM-Verbesserung berechnet. Warm-FD-/PSS-Prüfung und getrennte PDF-Probe ergänzen
diese eingeschränkte Momentaufnahme. Thermalstatus bei beiden Läufen 0 → 0;
keine vom System gemeldete thermische Drosselung, JIT-/Schedulingeffekte bleiben.

## DB-Phasen und Start vor/nach P11

**GEMESSEN:** DB jeweils 20 nach zwei Warmups. Überlappende Open-/Hookphasen
werden nicht addiert. Ein einzelner sicherer Read bleibt unverändert langsam.

| Phase | Median vorher/nachher ms | P95 vorher/nachher ms | σ vorher/nachher ms |
| --- | ---: | ---: | ---: |
| DB_CIPHER_INTEGRITY | 7.394/7.322 | 7.852/7.646 | 1.220/0.699 |
| DB_CLEANUP | 0.969/0.964 | 1.296/1.603 | 0.260/0.575 |
| DB_FOREIGN_KEYS | 0.475/0.457 | 0.623/0.574 | 0.061/0.073 |
| DB_INVENTORY | 0.712/0.610 | 0.901/0.972 | 0.146/0.196 |
| DB_NATIVE_CLOSE | 0.444/0.455 | 0.675/0.721 | 0.094/0.093 |
| DB_NATIVE_OPEN | 578.722/578.615 | 582.381/580.853 | 1.767/1.120 |
| DB_NATIVE_POST_KEY | 0.072/0.060 | 0.100/0.092 | 0.019/0.030 |
| DB_NATIVE_PRE_KEY | 0.072/0.067 | 0.115/0.111 | 0.017/0.017 |
| DB_PRODUCT_INVENTORY | 2.059/1.952 | 5.187/4.955 | 1.399/0.991 |
| DB_QUERY_TRANSACTION | 2.012/1.932 | 2.160/2.109 | 0.202/0.315 |
| DB_ROOM_BUILDER | 0.517/0.527 | 0.709/0.705 | 0.094/0.061 |
| DB_ROOM_NATIVE_OPEN | 583.462/583.739 | 593.818/595.237 | 7.625/5.948 |
| DB_ROOM_OPEN_SCHEMA | 588.041/588.055 | 599.608/600.887 | 8.032/6.538 |
| DB_ROOM_POST_KEY | 0.124/0.135 | 0.245/0.225 | 0.068/0.067 |
| DB_ROOM_PRE_KEY | 0.089/0.095 | 0.217/0.219 | 0.052/0.056 |
| DB_SQLITE_INTEGRITY | 8.252/8.162 | 9.019/8.623 | 0.269/0.203 |
| DB_TOTAL | 1196.579/1197.087 | 1211.636/1207.978 | 8.519/6.295 |
| DB_VERSION | 0.192/0.200 | 0.311/0.295 | 0.040/0.040 |

Start (`am start -W`, jeweils n=10): Cold-Median 777,0 → 754,5 ms, P95
818 → 772 ms, σ 13,69 → 5,76 ms; Warm-Median 118,0 → 117,5 ms, P95
134 → 125 ms, σ 9,40 → 8,20 ms. Warm praktisch unverändert. Die kleine
Cold-Differenz wird ohne isolierten Startup-Ursachennachweis nicht als garantierter
Produktgewinn ausgegeben. Kein automatisches Credential-Unlock vermessen.

Die Einzelreads für Personen, Tasks, Rollen, Protokoll, Material, Kosten, Memory und
Dokumentliste wurden vor/nach je zehnmal separat ausgeführt; sie bleiben jeweils
ungefähr 1,2 s. Die gemeinsame Operation spart wiederholte Opens, nicht den
Sicherheitsaufwand eines einzelnen Reads. Rohdaten lassen sich mit den Tools
unabhängig neu zusammenfassen; negative Differenzen werden nicht verschwiegen.

## Automatisierte Regressionen und Ressourcenprüfung

**GEMESSEN / funktional geprüft:** vollständige Host-Regressionssuite nach den
internen Viewer-Messpunkten: 1.201 Tests (547 App, 650 Data, 4 Security), keine
Fehler. Bestehende 1.195 Tests nicht entfernt/abgeschwächt; sechs neue Batchtests.
Nach Abschaltung standardmäßiger Bild-Zeitlogs betroffene Dokument-Hosttests erneut
bestanden. Bilddiagnose benötigt nun wie Text/PDF explizites Debug-Log-Opt-in;
neue Detailhooks sind ungebunden, keine operative Zeitreihe im Produktbetrieb.

Auf Samsung/API 33 im isolierten Data-Paket bestanden alle 39 funktionalen Fälle:
5 Bild, 8 Text, 6 PDF, 14 Reconciliation/Recovery und 6 neue Workspacefälle.
Diese sichern Ergebnisgleichheit mit bestehenden Stores, expliziten minimalen Bedarf,
Status-/Aktions-/Generationsbindung, DRAFT/DELETION_PENDING, queued Revoke vor
Worker und Publication sowie wiederholte FD-Freigabe ab. Bestehende Suites umfassen
Integrität, EOF/Manipulation, Blobbindung, Recovery und DB-Key-Lifecycle.
Hosttests sichern ergänzend Session-/Commit-/Publication-Rennen, Maintenance und
Viewer-Revoke ab; keine Behauptung eines neuen unabhängigen Sicherheitsreviews.

Warm-FD-Verlauf: zwei Aufwärmzugriffe, danach 20 vollständige Workspacezugriffe;
FD **85 → 85**, bei jeder Iteration höchstens zwei über Warmbasis zugelassen.
PSS-Momentaufnahmen **60.146 → 65.763 KiB**. Das ist ein begrenzter Lastverlauf,
kein stabiler Langzeit-RAM-/GC-Beweis und keine Speicherverbesserung gegenüber
Baseline. Schlüssel/Room schließen vor Publication; keine neue Pool-/Cachelebensdauer.

Der erste Gesamtlauf enthielt zusätzlich den Klasse-B-Keyformat-Benchmark und meldete
insgesamt 40 Fälle mit einem Fehler: dessen zwei frische DBs lagen entgegen dem
Inventarvertrag gemeinsam in einem Verzeichnis (`B2-INVENTORY`). Korrigiert wurden
nur die isolierten Fixtureverzeichnisse. Keine Inventarprüfung abgeschwächt; die 39
funktionalen Tests waren bereits erfolgreich. Der Benchmark wird gezielt wiederholt
und getrennt von Funktionstests bewertet.

## Klasse-B-Messung H — offizielles Raw-Key-Format, keine Produktumstellung

**GEMESSEN, isoliert:** gezielter Wiederholungslauf `P11KeyFormatBenchmark` PASS.
Zwei frisch erzeugte leere Schema-11-Testdatenbanken in eigenen Inventarverzeichnissen,
jeweils dasselbe neue zufällige 32-Byte-Material; bisherige ByteArray-Keyübergabe
gegen offizielles SQLCipher-`x'…'`-Raw-Key-Format. Zwei Warmups je Modus, anschließend
20 gepaarte/abwechselnde komplette Operationen. Beide Pfade führen nachweislich alle
fünf Preflight-Schritte aus, schließen Native/Room und verwenden keine Verbindungspools
über Operationen. Kodierung nur in Android-Testcode, keine geheimen Strings/Logs;
mutable Schlüsselarrays werden im finally gewischt. Keine bestehende DB konvertiert.

| Modus | n | Median ms | P95 ms | Min–Max ms | σ ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Bisheriger abgeleiteter Keypfad | 20 | 1194,428 | 1212,780 | 1181,542–1251,672 | 15,640 |
| Offizieller Raw-Key-Pfad | 20 | 49,049 | 53,169 | 25,628–53,826 | 8,227 |

Differenz dieser frischen Test-DBs: **1145,379 ms / 95,9 %** Median. Dies ist kein
Nachweis gleicher Zeiten mit Bestandsdaten oder des gesamten Unlock-/Viewerflows.
Die drei Integritätschecks bleiben; nicht deren Abschaltung erzeugt den Gewinn.
Die reine KDF-CPU-Zeit bleibt nicht separat gemessen; die Keyrepräsentation ist hier
der kontrolliert veränderte Faktor einer gesamten Open-/Query-/Close-Operation.

Zetetic dokumentiert Raw Keys als explizite 256-Bit-Schlüssel statt Passphrase mit
Ableitung: [offizielle API](https://www.zetetic.net/sqlcipher/sqlcipher-api/),
[Hinweise zu zufälligen Schlüsseln](https://www.zetetic.net/blog/2019/06/07/technical-guidance-using-random-values-as-sqlcipher-keys/).
Das ist eine etablierte Bibliotheksoption, keine neu entwickelte Kryptografie.
Trotzdem Klasse B: andere Schlüsselrepräsentation und Ableitungsannahme, Nachweis
vollständiger Entropie und geschützter Herkunft erforderlich. ByteArray-/JNI-Semantik
der konkret eingesetzten Version prüfen; bestehende abgeleitete Datenbanken sind
nicht durch bloßes Austauschen des Parameters kompatibel. Ein separates crashfestes
Konvertierungs-/Recoverykonzept wäre nötig; P11 führt kein Rekey/keine Migration aus.

Ergänzung zu H: zeitlich keine längere produktive Key-/Klartextlebensdauer erforderlich,
aber native Ableitung verändert. Trust Boundaries, TM-F03/F05/F06/F08/F17 sowie
Key-Wrap-/Falscher-Key-/Downgrade-/Crash-/Konvertierungstests bleiben verpflichtend.
**Empfehlung:** diese Option priorisiert unabhängig reviewen, bevor Pooling oder
Integritätsreduktion erwogen werden. Keine Freigabe und keine Übernahme im Produkt;
ausdrückliche Benutzerentscheidung plus Sicherheitsreview erforderlich.

## Ergänzende Viewerphasen (diagnostisch, keine Vorher-/Nachhergewinnreihe)

**GEMESSEN:** P11ViewerPhaseProbe PASS, je zehn Reads pro Bildformat und je
zehn Text-/PDF-Zugriffe auf erste/folgende Seite. Derselbe Worker und reale
Repository-/Authentifizierungs-/Renderpfad, synthetische Fixture wie UI-Reihe.
Komponentenzeiten sind separat; Beobachter-/Status-IPC hat eigene Kosten.
Überlappende Gesamtdauern nicht addieren. Bestehende Requestphasen haben
Millisekundenauflösung (0 ms bedeutet unter Auflösung), neue App-Teilphasen
Nanosekundenstoppuhr, Ausgabe in ms. Keine Garantie exakter nativer CPU-Zeit.

| Phase | n | Median ms | P95 ms |
| --- | ---: | ---: | ---: |
| JPEG_BITMAP | 10 | 6.389 | 8.788 |
| JPEG_BOUNDS | 10 | 0.161 | 0.245 |
| JPEG_DB_SNAPSHOT | 10 | 1200.500 | 1214.000 |
| JPEG_DECODE | 10 | 10.500 | 12.000 |
| JPEG_DOWNSAMPLE | 10 | 0.008 | 0.051 |
| JPEG_EXIF | 10 | 0.183 | 0.322 |
| JPEG_ORIENTATION | 10 | 0.047 | 0.076 |
| JPEG_READ_AND_AUTH | 10 | 5.500 | 11.000 |
| JPEG_WORKER_START | 10 | 0.000 | 5.000 |
| PDF_0_AUTH_AND_MEMFD | 10 | 4.500 | 7.000 |
| PDF_0_BIND_CONNECTED | 10 | 290.765 | 300.035 |
| PDF_0_BITMAP_ALLOC | 10 | 0.258 | 0.396 |
| PDF_0_CLEANUP | 10 | 13.000 | 15.000 |
| PDF_0_DB_SNAPSHOT | 10 | 1190.000 | 1204.000 |
| PDF_0_INVENTORY | 10 | 1.500 | 3.000 |
| PDF_0_IPC_AND_BITMAP | 10 | 366.000 | 387.000 |
| PDF_0_IPC_RESPONSE | 10 | 58.669 | 61.893 |
| PDF_0_MEMFD_FILL | 10 | 0.000 | 0.000 |
| PDF_0_OUTPUT_MEMFD | 10 | 0.083 | 0.213 |
| PDF_0_PAGE_OPEN | 10 | 0.000 | 1.000 |
| PDF_0_PAGE_RENDER | 10 | 0.000 | 1.000 |
| PDF_0_PIXEL_ARRAY | 10 | 0.095 | 0.204 |
| PDF_0_PIXEL_COPY | 10 | 2.981 | 3.672 |
| PDF_0_PIXEL_READ | 10 | 7.655 | 11.512 |
| PDF_0_RENDERER_INIT | 10 | 37.000 | 42.000 |
| PDF_0_WORKER_START | 10 | 0.000 | 1.000 |
| PDF_1_AUTH_AND_MEMFD | 10 | 5.000 | 6.000 |
| PDF_1_BIND_CONNECTED | 10 | 291.785 | 302.129 |
| PDF_1_BITMAP_ALLOC | 10 | 0.290 | 0.346 |
| PDF_1_CLEANUP | 10 | 14.000 | 16.000 |
| PDF_1_DB_SNAPSHOT | 10 | 1187.000 | 1207.000 |
| PDF_1_INVENTORY | 10 | 1.500 | 4.000 |
| PDF_1_IPC_AND_BITMAP | 10 | 374.500 | 392.000 |
| PDF_1_IPC_RESPONSE | 10 | 60.007 | 62.482 |
| PDF_1_MEMFD_FILL | 10 | 0.000 | 0.000 |
| PDF_1_OUTPUT_MEMFD | 10 | 0.076 | 0.137 |
| PDF_1_PAGE_OPEN | 10 | 0.000 | 1.000 |
| PDF_1_PAGE_RENDER | 10 | 0.000 | 1.000 |
| PDF_1_PIXEL_ARRAY | 10 | 0.051 | 0.146 |
| PDF_1_PIXEL_COPY | 10 | 2.979 | 4.425 |
| PDF_1_PIXEL_READ | 10 | 7.897 | 10.787 |
| PDF_1_RENDERER_INIT | 10 | 38.000 | 41.000 |
| PDF_1_WORKER_START | 10 | 0.500 | 1.000 |
| PNG_BITMAP | 10 | 19.098 | 21.843 |
| PNG_BOUNDS | 10 | 0.130 | 0.307 |
| PNG_DB_SNAPSHOT | 10 | 1200.500 | 1212.000 |
| PNG_DECODE | 10 | 24.000 | 28.000 |
| PNG_DOWNSAMPLE | 10 | 0.008 | 0.074 |
| PNG_EXIF | 10 | 0.001 | 0.001 |
| PNG_ORIENTATION | 10 | 0.040 | 0.106 |
| PNG_READ_AND_AUTH | 10 | 5.000 | 10.000 |
| PNG_WORKER_START | 10 | 1.000 | 1.000 |
| TEXT_0_DB_SNAPSHOT | 10 | 1195.000 | 1220.000 |
| TEXT_0_INVENTORY | 10 | 3.000 | 5.000 |
| TEXT_0_PAGE_PREPARE | 10 | 4.000 | 12.000 |
| TEXT_0_READ_AUTH_UTF8_PAGE | 10 | 17.000 | 24.000 |
| TEXT_0_UTF8_DECODE | 10 | 3.000 | 3.000 |
| TEXT_0_WORKER_START | 10 | 1.000 | 1.000 |
| TEXT_1_DB_SNAPSHOT | 10 | 1193.000 | 1204.000 |
| TEXT_1_INVENTORY | 10 | 1.500 | 4.000 |
| TEXT_1_PAGE_PREPARE | 10 | 3.500 | 5.000 |
| TEXT_1_READ_AUTH_UTF8_PAGE | 10 | 15.500 | 21.000 |
| TEXT_1_UTF8_DECODE | 10 | 2.000 | 3.000 |
| TEXT_1_WORKER_START | 10 | 0.000 | 1.000 |

Bild: Bounds, Sampleberechnung, EXIF, Bitmapdecode und Orientierung getrennt
im tatsächlich verwendeten Decoder beobachtet. Fixture ohne gedrehte EXIF-Pixel;
kein Benchmark aller Orientierungen oder sehr großer Quellen. Keine weiteren
Bildkopien/Caches eingeführt. Read+Auth beinhaltet Datei-I/O und Tink; keine reine
Tink-CPU-Zeit. Text behält integriertes Streaming/UTF8/Pagination; Decoder- und
Paginatorzeit sind erfasst, IO/AEAD nicht künstlich voneinander getrennt. Kein
Gewinn durch Umgehung von EOF oder länger gehaltenen Klartext.

PDF: Bind-to-connected enthält Prozessstart und Binder-/Main-Dispatch, kein reiner
Prozessstartwert. IPC_RESPONSE enthält Servicearbeit; RENDERER_INIT/PAGE_RENDER
sind darin enthaltene Servicezeiten. Clientarray, pread, Bitmapallokation und
Pixelcopy separat. Native Service-Pixelallokation/-copy nicht einzeln zerlegt;
8-MiB-Bitmaplimit bleibt kein Total-Native-Limit. Rund 291 ms Bindung stehen rund
11 ms clientseitigem pread+Bitmapcopy gegenüber. Eine aufwendige neue Mapping-/
IPC-Speicherarchitektur hat damit keinen nachgewiesenen großen Klasse-A-Gewinn.
Wiederverwendung des Parsers/memfd bleibt Klasse B und wurde nicht umgesetzt.

Die vollständige UI-Vorher-/Nachherreihe wurde vor diesen optionalen Detailhooks
aufgenommen. Defaultpfade verwenden keine Detailcallbacks oder neue Uhren;
entsprechende Host-/PDF-/Textregressionen prüfen den finalen Stand. Die zusätzliche
Abschaltung standardmäßiger Bilddiagnoselogs wird nicht als gemessener Gewinn
verkauft. Publication/folgender Frame und Folgeseiten sind in der UI-Reihe getrennt
von diesen Workerkomponenten dokumentiert.

## Erstaufruf separat

**GEMESSEN:** Index 0 der Endpunktreihe, jeweils erster UI-Aufruf dieses
Messpunkts im Probeprozess nach Fixtureaufbau. Kein echter Cold-Unlock, keine
vorher gestarteten Worker-/Importkosten enthalten. Weitere neun Wiederholungen
sind Wiederholungszugriffe im selben Prozess; Haupttabelle berichtet alle zehn
transparent, diese Erstwerte werden nicht als separate belastbare Verteilung
ausgegeben. DB-Warmups und Cold-/Warm-Starts bleiben davon getrennt.

| Erstaufruf | Vorher ms | Nachher ms |
| --- | ---: | ---: |
| READY_LIST | 1251.73 | 1210.15 |
| PREPARATION | 7185.78 | 2471.31 |
| ACTIVE_HUB | 9573.81 | 2425.31 |
| JPEG | 1249.96 | 1227.33 |
| PNG | 1257.60 | 1257.47 |
| TEXT_FIRST | 1300.39 | 1261.13 |
| TEXT_NEXT | 1225.90 | 1250.35 |
| PDF_FIRST | 1625.27 | 1612.27 |
| PDF_NEXT | 1591.47 | 1580.51 |

## Abschließende Geräte-/Buildprüfungen

PDF-Machbarkeit auf Samsung/API 33 erneut PASS: öffentlicher memfd, Seekbarkeit,
isolierter UID/Service, Renderer-/Fehler-/Abbruchpfade; Service-FDs bei letzter
Wiederholung 83, Warmbasis 83. PDF-Viewer-Probe erneut PASS einschließlich
Parser-/Binderfehlern, Größenlimits, Revoke/Navigation, JPEG/PNG und fehlenden
persistenten Klartextdateien im geprüften Testinventar. Parent-FDs 82 → 81;
Nativeheapdifferenz −28.832 Bytes als begrenzter Probenwert, keine Löschgarantie.
Getrennte P10c2TextProbe ebenfalls PASS. Keine echte Kameraaufnahme, kein
Produkt-SAF-/Credential-Test automatisiert; diese bleiben manuell.

Die Probes wurden ausschließlich in `org.pag.pdf.p10c3probe`,
`org.pag.text.p10c2probe`, `org.pag.perf.p11probe` und `org.pag.data.unlockprobe`
ausgeführt. Das Produktpaket war kein Benchmarkziel. Keine Deinstallation,
kein `pm clear`, Reset, Export der Produktdaten oder Eingabe von Credentials.
Neue Raw-Key-Variante liegt ausschließlich in Android-Testcode; kein Raw-Key-
Produktadapter, Rekey, kryptografischer Parameterwechsel oder neuer Cache.

## Manuelle P11-Abnahme — zweite Runde offen

Nach dem signaturkompatiblen Update vom Benutzer zu bestätigen. Die P10c3-
Bestätigung vom 17.10.2026 ersetzt diese P11-Abnahme nicht. Nur synthetische
Dokumente für Dokumentenregressionen; reguläre Credentials ausschließlich selbst
im Systemdialog eingeben. Große Schrift und TalkBack gesondert beurteilen.
Die inzwischen vorliegende Teilrückmeldung zur ersten P11-Runde einschließlich
ausdrücklich bestätigtem Punkt 15 ist im Abschnitt „Zweite Runde“ festgehalten.
Die folgende Liste bezeichnet den erneuten Prüfbedarf nach dem zweiten Update.

1. Entsperren bis Aktionsliste.
2. Aktion öffnen.
3. Zwischen Bereichen wechseln.
4. Zurücknavigation.
5. Personen und Aufgaben öffnen.
6. Protokoll, Material und Kosten.
7. Gedächtnisprotokolle.
8. JPEG/PNG öffnen.
9. UTF-8 öffnen und blättern.
10. PDF öffnen und blättern.
11. Kamera- und SAF-Import.
12. Längere synthetische Datei laden.
13. App während des Ladens sperren.
14. Regulär erneut entsperren.
15. Keine automatische Wiederherstellung vorheriger Inhalte.
16. Ladehinweise und ruhige Erfolgsbanner beurteilen.
17. Subjektive Flüssigkeit beurteilen.
18. Große Schrift und TalkBack, soweit separat möglich.

## Gesamtbewertung und offene Entscheidungen

**Klasse A: automatisiert nachgewiesene Verbesserung der Workspace-/Navigation-
Latenz. Manuelle Produktabnahme offen. OFFENE ENTSCHEIDUNG für Klasse B.**
Kein in diesen Prüfungen gefundener produktiver Sicherheitsblocker; dies ist keine
vollständige unabhängige Security-Abnahme. Issue #26 bleibt verbindlich vor 1.0.

| Punkt | Befund |
| --- | --- |
| A — Baseline | Reproduzierbare DB-/UI-/Startreihen, Warmup/Erstaufruf, Median/P95/Streuung oben. |
| B — Bottleneck | Zwei native SQLCipher-Opens dominieren jeden kurzen Read; wiederholte Workspace-Operationen multiplizierten diese Kosten. |
| C — Klasse A | Explizite kurze Batchtransaktion, atomare Publication, ruhige Loading-/Feedback-UX, lokale Tasksortierung. |
| D — Vorher/nachher | Hub 9,56 → 2,42 s; Dokument-Rückkehr 8,37 → 1,25 s; vollständige Tabellen oben. |
| E — Navigation/UX | Automatisierte Pfade bestanden, subjektive Produktbeurteilung und Accessibility offen. |
| F — SQLCipher | Einzeloperation 1196,6 → 1197,1 ms, keine Integritäts-/KDF-Reduktion im Produkt. |
| G — Workspace | Bestehende Stores und Rechte, ein DB-Read statt mehrfacher Reads; Leadership-Metadaten separat. |
| H — JPEG/PNG | Keine belastbare Öffnungsbeschleunigung; gemessene Komponenten, Schutzgrenzen unverändert. |
| I — UTF-8 | Kein belastbarer Gewinn, vollständiges EOF je Seite und 32-KiB-Limit erhalten. |
| J — PDF | Kein belastbarer Öffnungs-/Blättergewinn, Isolation und requestgebundene memfds erhalten. |
| K — Frames | P95 44,01 → 41,55 ms; Deadlineüberschreitungen 20,82 → 18,82 %, Spitzen bleiben. |
| L — Ressourcen | UI-FDs beide 84 → 139; warmes Workspace 85 → 85; PDF 82 → 81. Nativebaseline-Endwert ungültig, kein RAM-Gewinn behauptet. |
| M — Regressionen | 1201 Hosttests, 39 funktionale Androidfälle; DB-/UI-/Viewerprobes bestanden, Benchmarkfixturefehler korrigiert und gezielt wiederholt. |
| N — Schutz | SessionCoordinator/180s/30s/Gates, Keyowner, SQLCipher/Tink, NamespaceLane, FLAG_SECURE, Schema 11 und Produktpermissions unverändert. |
| O — Restprobleme | Einzel-DB-Open ~1,2 s, PDF-Prozessanlauf ~0,29 s, Frame-Spitzen, begrenzte Fixture-/Gerätematrix. |
| P — Klasse B | A–H mit heutigen Grenzen, Risiken, Gegenmaßnahmen, Testbedarf und Freigabestatus separat dokumentiert. |
| Q — Entscheidung | Priorisierter unabhängiger Review einer möglichen Raw-Key-Umstellung; keine Produktfreigabe, Konvertierung oder Integritätsreduktion in P11. |
| R — Dateien | Vollständige Liste im nächsten Abschnitt. |
| S — Benutzerprüfung | Alle 18 P11-Schritte einschließlich Kamera/SAF, Lock/Reauth und TalkBack noch offen. |

Restgrenzen: offene READY-Sitzung bleibt ein Expositionsfenster; RAM-/OS-/OEM- und
native PDF-Parserrisiken bleiben. Keine Garantie vollständiger physischer RAM-/
Flashlöschung, kein vollständiger Langzeit-/OOM-/Leakbeweis. Nur dieses Samsung/API 33,
debuggable Builds und synthetischer Datenbestand getestet. Kein Releaseprofil-
Performanceversprechen oder quantifizierter Gesamt-Unlockgewinn. Keine neue
P3b-Payload, zweite Session-/Key-/Storagearchitektur oder ungefragter Prefetch.

## Produktupdate und abschließender Git-Stand

**BESTANDEN:** Produkt-/Demobuild und `verifyPrototypeManifest`/`verifyDemoManifest`.
App-ID `org.pag.prototype.a` vor Installation geprüft. Installierter APK-Code und
neue APK besitzen denselben Zertifikat-SHA256:
`9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134`.
Nur APK-Code zur Signaturprüfung gelesen, keine private Produktdatenbank oder
Dokumente ausgelesen/kopiert. `.prototype-cache/debug.keystore` vor/nach unverändert:
SHA256 `a296e242d1a5dfeae24471e435e4ae427f29df812ec1c5a8557c59bce3f2deb7`.
Neue Produkt-APK SHA256:
`6119b589463892b222de4f98aa39350dee08c7db51d6004bd3780e47dbc7b8d5`.

`adb -s "$ANDROID_SERIAL" install -r` meldete **Success**. Produkt-App nicht automatisch
entsperrt; keine Credential-Eingabe und keine manuelle P11-Abnahme vorweggenommen.
Kein App-Datenreset, keine Deinstallation oder Änderung produktiver Bestände durch
eine Testfixture. Ein Hash-/Inhaltsvergleich der Produktdaten wurde bewusst nicht
durchgeführt, da deren automatisches Lesen nicht autorisiert ist.

Branch `p11-performance-optimization`, HEAD unverändert
`b3a8c19105e755bc3df6c7207ac0c7e3969702a0`. `git diff --check` bestanden.
30 lokale Änderungsdateien (15 bereits getrackte, 15 neue); keine gestagten Dateien,
kein Commit, Push, Merge oder Release. Normales `git diff --stat` erfasst nur die
15 getrackten Änderungen; die folgende Liste enthält auch die 15 neuen Dateien.

**GRÜN für den automatisiert geprüften Klasse-A-Umfang.** Weitere erhebliche
DB-/Viewergewinne bleiben eine **OFFENE ENTSCHEIDUNG**; keine Klasse-B-Umstellung
freigegeben. Manuelle P11-Geräte-/Accessibility-Abnahme und unabhängiger Review
bleiben offen, ebenso die expliziten Mess-/Gerätegrenzen oben.

## Dateiliste der ersten Runde (historischer Stand)

Stand der lokalen Änderungen, ohne Commit/Push. Keine Produktdatenbanken,
Schlüssel/Keystores, APKs, Klartext-Testdateien, Logs oder Build-Caches enthalten.

- `app/src/androidTest/AndroidManifest.xml`
- `app/src/androidTest/kotlin/org/pag/prototype/P11PerformanceProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P11ViewerPhaseProbe.kt`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/AndroidDocumentPdfRenderer.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentImageDecoder.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PagShell.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/TaskScreen.kt`
- `app/src/test/kotlin/org/pag/prototype/product/WorkspaceBatchTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P11DatabaseBenchmark.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P11KeyFormatBenchmark.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P11WorkspaceStorageTest.kt`
- `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt`
- `data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductWorkspaceRepository.kt`
- `data/src/main/kotlin/org/pag/data/prototype/b2/B2Driver.kt`
- `data/src/main/kotlin/org/pag/data/prototype/b2/B2Native.kt`
- `docs/product-p11-performance.md`
- `domain/src/main/kotlin/org/pag/domain/Workspaces.kt`
- `tools/p11/compare.py`
- `tools/p11/frames.py`
- `tools/p11/probe.init.gradle`
- `tools/p11/startup.py`
- `tools/p11/summarize.py`

## Zweite Runde – Benutzerbefunde und zusätzlicher Prüfstand

Stand 17.10.2026. Der folgende Abschnitt ergänzt den historischen Bericht der
ersten Runde; deren Messungen werden nicht als neu ausgeführte Tests ausgegeben.
Die gesamte P11-Abnahme bleibt offen.

**BEOBACHTET (Benutzer):** Navigation, Personen und Aufgaben deutlich besser;
Kamera/SAF, Text, PDF, Sperren während Laden und reguläre Reauthentifizierung
funktionieren. Punkt 15 wurde ausdrücklich präzisiert: **kein Autoopen nach
Reauthentifizierung** in der ersten P11-Abnahme. Dies ersetzt keine neue Abnahme
der zweiten Runde. Große Schrift und TalkBack sind weiterhin manuell offen.
Verbleibend: ungefähr vier Sekunden bis zur Aktionsliste, ruckelige Bereichswechsel,
nachträgliche Ladehinweise mit Layoutsprüngen, spürbares Nachladen bei Zurück,
initiale Textöffnung, Bildöffnung, Gedächtnisprotokolle und PDF-Seitenwechsel.
Die störenden Hinweise wurden konkret als „Bereich wird geladen“, „Wird sicher
geöffnet“ und ähnliche Texte benannt, nicht nur als animierte Fortschrittsbalken.

### Entsperrpfad: echte Maintenance mit synthetischer Authentifizierung

**GEMESSEN:** Samsung SM-M526BR/API 33, isoliertes Paket
`org.pag.perf.p11probe`, zehn frische synthetische Sitzungen pro Variante.
`P11UnlockProbe` nutzt den echten SessionCoordinator, die unveränderten Deadlines,
den gleichen Maintenance-Einstieg wie ProductRuntime und echte SQLCipher-/Room-
Operationen. Nur Authentifizierung und Schlüsselquelle sind synthetisch. Kein
Produktcredential, kein Zugriff auf Produktdaten. Credential-Dialog-/Keystore-Zeit
vor erfolgreicher Authentifizierung gehört nicht zu diesen Werten.
Ein OnDraw nach Layout der Liste ist ein UI-Draw-Proxy, keine Messung physisch
sichtbarer Pixel auf dem Panel. Keine Addition fremder Fixtures zur Gesamtlatenz.

| Strecke | Vorher Median / P95 ms | Nachher Median / P95 ms |
|---|---:|---:|
| Authentifizierung erfolgreich → READY | 3575,36 / 3593,79 | 2393,44 / 2411,60 |
| Authentifizierung erfolgreich → Listen-Draw | 4804,81 / 4823,09 | 3623,89 / 3649,32 |
| READY → Listen-Draw | 1229,74 / 1241,72 | 1230,90 / 1237,72 |

Listen-Draw verbessert sich um 1180,91 ms / 24,58 % im Median; Standardabweichung
vorher 11,53, nachher 9,51 ms. **Kein nachgewiesener Gewinn** im separaten
READY→Listen-Read. Die Authentifizierungszeit bleibt Ausgangspunkt der absoluten
180 Sekunden; es wird keine zusätzliche Sitzungszeit gewährt.

Aufschlüsselung des READY-Aktionslisten-Reads derselben zehn Sitzungen:

| Phase | Vorher Median / P95 ms | Nachher Median / P95 ms |
|---|---:|---:|
| Worker-Wartezeit | 0,341 / 0,965 | 0,226 / 0,464 |
| DB-Inventar | 0,635 / 0,994 | 0,628 / 0,794 |
| Native SQLCipher-Öffnung | 580,485 / 586,671 | 578,920 / 584,302 |
| Versionsprüfung | 0,182 / 0,227 | 0,188 / 0,274 |
| cipher_integrity_check | 7,303 / 7,710 | 7,340 / 7,671 |
| integrity_check | 7,971 / 8,567 | 8,033 / 8,470 |
| foreign_key_check | 0,452 / 0,485 | 0,451 / 0,570 |
| Native Close | 0,499 / 0,568 | 0,489 / 1,702 |
| Room-Builder | 0,543 / 0,616 | 0,568 / 1,610 |
| Room native SQLCipher-Öffnung | 579,044 / 584,207 | 579,444 / 581,702 |
| Room-Open einschließlich Schema | 582,975 / 588,381 | 583,494 / 586,396 |
| Eigentliche Query/Transaktionsgrenze | 1,425 / 1,919 | 1,057 / 1,805 |
| Room-/Worker-Cleanup | 0,958 / 1,959 | 0,862 / 1,935 |
| Main-Publication-Wartezeit | 0,396 / 0,613 | 0,357 / 0,587 |
| ViewModel-Callback | 0,292 / 0,677 | 0,269 / 0,643 |
| Publication → Compose-Commit | 23,107 / 29,823 | 24,225 / 31,178 |
| Publication → Listen-Layout | 32,155 / 38,675 | 34,332 / 40,842 |

Verschachtelte Phasen (Room native / Room-Open, Commit / Layout / Draw) dürfen
nicht addiert werden. Kein Hinweis auf relevante Worker-/VM-Blockade in dieser
Fixture. Die beiden nativen Öffnungen dominieren weiterhin; die vorliegende Runde
hat weder deren Schlüsselinitialisierung noch Integritätsverfahren verändert.
Im gemessenen normalen Reopen bleiben zwei Maintenance-Operationen und der
anschließende READY-Listen-Read. Die Liste wird nicht aus Maintenance heraus
vorveröffentlicht oder als neuer Klartextcache über READY gereicht. Weitere
Verkürzung dieses Pfads durch ausgelassene Abschlussprüfungen, Verbindungspooling
oder geänderte SQLCipher-Schlüsselinitialisierung ist keine freigegebene Maßnahme
dieser Runde. Die Größen-/Inhaltsverteilung echter Produktbestände wurde nicht
automatisiert erhoben; deren genaue Latenzen müssen manuell beurteilt werden.

### Maßnahmen der zweiten Runde (Klasse A)

- Normales Wiederöffnen: vollständige Aktionsmodellvalidierung und erstes
  Recovery-Inventar in einer kurzen autorisierten DB-Operation/Transaktion.
  Drei DB-Operationen werden zu zwei; jede verbleibende Operation behält alle
  Preflightprüfungen. Modellvalidierung erfolgt vor jeglicher Recovery-Mutation.
  Frisches abschließendes Inventar, Recovery-Einzelschritte, Namespace-Lane,
  Fehlerbehandlung und Schlüssellebensdauer bleiben erhalten. Initialanlage hat
  unverändert ihren eigenen bisherigen Prüfpfad.
- Bewusstes Öffnen einer Aktion: vorhandener Listeneintrag dient ausschließlich
  als erwarteter Snapshot. Data vergleicht das gesamte persistierte Action-Modell
  im frisch vollständig geprüften Workspace-Read, bevor Fachbereichsdaten gelesen
  werden. Keine Veröffentlichung der vorläufigen Detailansicht. Bei veraltetem
  Snapshot Rückfall auf frischen bisherigen Detail-Read ohne neue Interaktion
  oder Deadlineverlängerung. Leadership-Metadaten bleiben separat.
- Bewusst gewählte ACTIVE-Gedächtnisprotokollliste: vorhandener MemoryReportStore
  liest nur Header innerhalb derselben kurzen Workspace-Transaktion. Kein
  Antworten-/Detail-Prefetch. Keine unnötige Task-Board-Abfrage für diesen Bereich,
  Kosten oder Protokoll. Header und Workspace werden atomar veröffentlicht;
  Editor-Refresh lädt keine Berichtsliste und setzt den Editor nicht zurück.
- Ladehinweise: tatsächlicher Text wird sofort mit unsichtbaren Pixeln und ohne
  Semantik vermessen. Nach 200 ms ändern sich nur Sichtbarkeit/Semantik, nicht
  Abmessungen. Kein Timer verzögert die Operation selbst; keine Animation,
  Prozentangabe oder wiederholt voll laufender Balken. Bild-/Text-/PDF-Flächen
  behalten feste Ansichtsgrößen; Steuerelemente bleiben an definierten Positionen.
- Navigation: nur Scrollposition und Inhaltsabmessungen für feste Zielnamen,
  höchstens 64 Einträge, innerhalb derselben Aktion und Generation. Keine
  Ergebnis-/Klartextcaches. Während Laden nur leere Geometrie, keine alten Inhalte;
  Ergebnisse werden weiterhin frisch autorisiert geladen. Action-/Generations-
  wechsel verwirft die Geometrie. Doppelte Gedächtnisprotokollüberschrift entfällt.

### Loader-Geometrie

**GEMESSEN:** 300-dp-Testspalte, Fontscales 1,0 / 1,5 / 2,0 / 3,5, je zehn
Wiederholungen, Markerposition vor/nach verzögerter Texteinblendung. Vorher bei
3,5: 420→1032 Pixel, 612 Pixel Verschiebung. Nachher bei sämtlichen vier Größen
keine Verschiebung; bei 3,5: 368→368 Pixel. Dies belegt die Geometrie des
gemeinsamen Loader-Bausteins, nicht die vollständige TalkBack-/Navigationsabnahme.

### Navigation und Viewer: vergleichbare zehn UI-Läufe

**GEMESSEN:** `P11PerformanceProbe`, dieselbe Fixture und dieselben Messpunkte
vor/nachher; Vorher-APK ist der eingefrorene P11-Runde-1-Phasenbuild, Nachher-APK
enthält die zweite Runde. Beide mit demselben erweiterten Testtreiber. Kein
Leadership-Port in dieser Fixture; PREPARATION ist daher kein vollständiges
Produktversprechen einschließlich zusätzlichem Leadership-Metadatenread. Leere
Memory-Liste im Zeitbenchmark, nichtleere Header werden separat funktional geprüft.
Alle zehn Durchläufe einschließlich erstem UI-Aufruf; kein nachträgliches Entfernen
langsamer Samples. P95 ist nearest-rank, bei zehn Läufen der größte Messwert.
Android-Thermalstatus jeweils 0→0; keine manipulierten Systemanimationsskalen.

| Tap → Inhalts-Draw | Vorher Median / P95 ms | Nachher Median / P95 ms |
|---|---:|---:|
| Synthetisches READY → Liste (ohne Maintenance) | 1197,69 / 1231,45 | 1193,77 / 1236,85 |
| PREPARATION öffnen | 2399,27 / 2429,91 | 1221,24 / 1271,47 |
| ACTIVE-Lagebild öffnen | 2398,12 / 2418,89 | 1218,46 / 1246,98 |
| ACTIVE → Personen | 1233,13 / 1266,66 | 1236,00 / 1258,44 |
| ACTIVE → Gedächtnisprotokolle | 2396,39 / 2414,17 | 1209,83 / 1223,34 |
| Dokumente → Lagebild | 1212,87 / 1217,17 | 1211,40 / 1228,33 |
| JPEG | 1212,88 / 1230,68 | 1215,75 / 1239,43 |
| PNG | 1231,71 / 1238,91 | 1229,16 / 1238,29 |
| UTF-8 erste Seite | 1226,58 / 1266,12 | 1220,82 / 1255,43 |
| UTF-8 nächste Seite | 1229,70 / 1238,93 | 1227,37 / 1243,21 |
| PDF erste Seite | 1569,84 / 1588,30 | 1589,01 / 1604,32 |
| PDF nächste Seite | 1579,15 / 1598,58 | 1576,45 / 1594,04 |
| PDF zurück | 1576,02 / 1588,47 | 1574,73 / 1594,52 |

Nachgewiesene Medianverbesserungen: PREPARATION 1178,03 ms / 49,10 %,
ACTIVE-Öffnung 1179,66 ms / 49,19 %, Memory-Liste 1186,56 ms / 49,51 %.
Andere kleine Unterschiede werden nicht als belastbare Verbesserung gewertet.
PDF-Erstöffnung ist hier 19,16 ms / 1,22 % langsamer, Seitenwechsel praktisch
unverändert. Keine Behauptung, dass PDF-/Bild-/Textoperationen in Runde 2 schneller
geworden seien. Zeit bis erstem Tap-Draw liegt im Median beispielsweise bei
ACTIVE-Öffnung 8,59→9,32 ms, Personen 12,97→12,77 ms, Memory 12,89→12,00 ms,
JPEG 11,76→11,93 ms, PDF-Erstöffnung 13,66→23,72 ms. Dieser Draw ist die
unmittelbare Bildschirmreaktion, noch nicht der geladene Inhalt.
Die UI-Reihe verwendet den ersten OnDraw mit erfülltem Fertig-Prädikat im Modell
als Inhalts-Draw-Proxy; sie ist kein pixelbasierter Nachweis des exakten ersten
Bitmap-/Textpixels und keine Messung des Panel-Scanouts. Der separate Unlock-Probe
prüft zusätzlich den Layout-Callback des tatsächlichen ersten Listenbuttons.

Ergänzende PDF-Phasenmessung: zehn Läufe je Fall, 70 Lese-/Renderzugriffe insgesamt.
Einseitig, acht Seiten mit unterschiedlichen Formaten, acht Seiten mit
synthetischem Bildinhalt; erste/nächste/zurück jeweils neuer autorisierter Read,
neuer Input und isolierter Rendererauftrag. Nur Nachher-Komponentenmessung,
keine zusätzliche Vorher-/Nachher-Gewinnbehauptung:

| PDF-Variante / Operation | Median / P95 ms |
|---|---:|
| Klein, erste Seite | 1617,67 / 1639,97 |
| Mehrseitig, erste Seite | 1624,40 / 1640,48 |
| Mehrseitig, nächste Seite | 1614,45 / 1642,72 |
| Mehrseitig, zurück | 1612,21 / 1637,21 |
| Größer mit Bild, erste Seite | 1648,00 / 1679,87 |
| Größer mit Bild, nächste Seite | 1662,40 / 1684,73 |
| Größer mit Bild, zurück | 1646,02 / 1679,44 |

Beispiel mehrseitig/nächste Seite, Median / P95 ms: DB-Snapshot 1196 / 1213,
Inventar 1,5 / 3, vollständige Authentifizierung plus Inputbefüllung 7 / 10,
Bind bis verbunden 286,021 / 302,303, PdfRenderer-Init 39 / 40, Page-Open 4 / 4,
Rendern 1 / 1, Ausgabe-memfd 0,080 / 0,167, IPC-Response 67,744 / 68,402,
Pixel-Read 13,333 / 14,011, Bitmapallokation 0,315 / 0,437, Pixelkopie
3,852 / 4,579, Cleanup 16 / 18. Verschachtelte Phasen nicht addieren.
Bind enthält Prozessstart; dessen Einzelkosten wurden nicht separat nachgewiesen.
Die Millisekunden-Phase MEMFD_FILL ist bei diesem kleinen Input 0 gerundet,
nicht ausgelassen. Das größere synthetische Dokument nutzt dieselben Größen- und
Authentifizierungsprüfungen. FD 82→83; nativer Heap 5601456→3342944 Bytes nach
Cleanup/GC. Endliche Stichprobe, kein Langzeit-/Leak- oder physischer Löschbeweis.

### Verbleibende Statusanzeigen

**BEOBACHTET (Quellprüfung):** kein LinearProgressIndicator,
CircularProgressIndicator oder animierter Loading-Baustein im Produktquellpfad.
Es bleiben verzögerte statische Read-/Maintenancehinweise im gemeinsamen
PagReadStatus/PagLoadingState sowie sachliche Import-/Speicher-/Kamerastatus und
die reguläre Android-Authentifizierungsoberfläche. Kein erfundener Fortschritt.
Der optionale historische Fortschrittscallback von expireFeedback bleibt als
Test-/Kompatibilitätshelfer, wird von der Produkt-Banneranzeige nicht verwendet.

### Regressionen der zweiten Runde

**TATSÄCHLICH AUSGEFÜHRT/BESTANDEN:** vollständige Hostregression nach den
Erweiterungen einschließlich PREPARATION: 1216 Tests (562 App, 650 Data,
4 Security), keine Fehler oder ausgelassenen Fälle. Die vorherigen 1201 der
ersten Runde werden damit nicht bloß übernommen. Neue Fälle umfassen frischen
vollständigen Action-Snapshot beim Öffnen, Stale-Rückfall ohne zusätzliche
Interaktion, DRAFT-Rückfall, Lock/Generationswechsel ohne Autoopen, bewusste
Bereichspayload ohne Prefetch/zweite Reads, falsche Board-Bindung,
schnelle Navigation, Editorerhalt und explizite PREPARATION-Auswahl.

Samsung: ursprüngliche relevante Dokument-/Recoveryregression samt den neuen
Maintenancefällen und ersten Workspace-Erweiterungen **44 bestanden**
(5 Bild, 8 Text, 6 PDF, 17 Reconciliation, 8 Workspace; 856,847 s).
Nach Aufnahme der zusätzlichen Bereichspayloads die **10 Workspacefälle erneut
bestanden** (81,567 s): einschließlich Vergleich mit bestehenden Einzelports,
minimaler/gezielter Auswahl, Phasenrechten, stale Action, frischen nichtleeren
Memory-Headern, Queue-/Publication-Revoke und warmen Ressourcen.
Somit 46 unterschiedliche Storage-/Workspacefälle, acht davon zusätzlich erneut
ausgeführt; keine erfundenen 54 unterschiedlichen Fälle. Die Dokument-/Recovery-
Implementierung wurde nach der 44er-Reihe nicht weiter geändert.
Die erste Entdeckung der beiden zusätzlichen Workspace-Tests scheiterte wegen
versehentlich lokaler Testfunktionen; Methoden auf Klassenebene korrigiert und
danach alle zehn erkannt und bestanden. Keine Tests entfernt oder abgeschwächt.

Warme Workspace-Ressourcen nach zwei Anläufen und 20 Wiederholungen: 83 FDs
am Ende, während jeder Wiederholung innerhalb der geprüften Startgrenze +2.
PSS 64218→58212 KiB; kein Langzeit-/Leakbeweis und kein Vergleich eines identischen
Payloadumfangs mit dem alten Runde-1-FD-Test (die finale Testanforderung enthält
absichtlich zusätzliche Boards). Synthetische DBs, keine Produktbestände.

### Frames und Ressourcen der UI-Messreihe

**GEMESSEN:** FrameMetrics vor/nach, 2917 / 2888 Frames. TOTAL Median
7,777→7,820 ms, P95 42,652→42,346 ms. Überschrittene gültige Frame-Deadlines
527/2917 (18,07 %) → 528/2888 (18,28 %), keine verworfenen Listener-Ereignisse.
Kein nachgewiesener Jankgewinn. Scroll-Teilmenge: Median 7,037→6,993 ms,
P95 11,870→11,583 ms; Maxima jeweils etwa 287 ms. Layout/Measure-P95
0,173→0,174 ms erklärt allein die langen TOTAL-Ausreißer nicht. Gepoolte
Durchläufe sind keine kausale Zuordnung einzelner Ausreißer zu einer Navigation.
Ein vollständiger GPU-/Compose-/Releaseprofil-Nachweis bleibt offen; die
stabilere Loader-Geometrie darf nicht als beseitigter Frame-Jank ausgegeben werden.

FD-Zählung jeweils 84→139, identischer Framework-/UI-Anlaufzuwachs vor/nach.
Dies ist kein Leakfreiheitsbeweis; eigene warme FD-/Rendererprüfungen bleiben
maßgeblich. Nativer Startwert etwa 5,6 MiB; Nachher-Endwert des Android-Counters
war 0 und ist für einen Heapvergleich unbrauchbar. Kein behaupteter RAM-Gewinn
aus diesem Wert. Bitmap-/PDF-Einzelgrenzen und Freigabeverträge unverändert;
native Parserallokationen bleiben durch die Bitmapgrenze nicht insgesamt begrenzt.

### Unveränderte Grenzen und offene Entscheidungen

Kein Room-Pooling, keine Integritätsreduktion, keine Parameter-/KDF-Änderung,
keine langlebigeren Schlüssel oder entschlüsselten Dokumentobjekte, kein
PDF-Renderer-/memfd-Reuse über Seitenwechsel, kein Prefetch. Alle Klasse-B-
Entscheidungen aus der ersten Runde bleiben unimplementiert und freigabepflichtig.
Issue #26 bleibt das verbindliche Gesamt-Release-Gate vor 1.0. Android-/OEM-,
PDF-Parser-, RAM-/Kernel-, OOM- und unabhängige Security-Review-Grenzen gelten
weiter. **NOCH NICHT GETESTET:** neue manuelle Samsung-Abnahme der zweiten Runde
einschließlich großer Schrift/TalkBack; keine pauschale GRÜN-Bewertung daraus.

### Zusätzliche Bündelung nach der ersten Messreihe dieser Runde

Die oben dokumentierte erste Nachherreihe ließ bei Protokoll, Material und Kosten
weiterhin zwei unmittelbar zusammengehörige Reads erkennen. Diese Bereiche wurden
anschließend ebenfalls in den vorhandenen Workspace-Read aufgenommen: Timeline,
Materialboard beziehungsweise Kostenboard ausschließlich nach bewusster Auswahl.
Die bestehenden Stores prüfen ihre eigenen Status-/Fachregeln innerhalb derselben
frischen Transaktion. Keine dieser Payloads wird im Hub geladen. Atomare Publication
umfasst Workspace und das jeweils gewählte Bereichsmodell; kein neuer Cache.
Editor-/Schreibpfade und COMPLETED-Einstiege behalten ihre bisherigen Ports und
Widerrufsgrenzen. Eine Workspace-Aktualisierung im Editor lädt kein neues Board
über den Entwurf. Neue Hostfälle prüfen explizite Auswahl ohne zweite Payload-
Reads, falsche Board-Bindung, schnelle Navigation, Lock/späte Ergebnisse und
Editorerhalt. Die finale Vergleichsreihe wird gesondert ausgewiesen; die erste
Nachherreihe oben bleibt als tatsächlich gemessener Zwischenstand erhalten.

Ein fehlgeschlagener Start dieser finalen Reihe wurde verworfen: der Testaufruf
verwendete das nicht zugelassene Label `final`; der Treiber erlaubt `baseline` und
`after`. Der Fehler trat im Instrumentation-onCreate vor Fixture-/Messbeginn auf.
Keine Produktdaten und kein erfolgreicher Benchmarklauf daraus. Wiederholung mit
gültigem Label und vollständig neu gebautem isoliertem Paket.

### Vollständige UI-Vergleichsreihe nach Bereichsbündelung

**BESTANDEN/GEMESSEN:** zehn vollständige Läufe nach Aufnahme der zusätzlich
ausgewählten ACTIVE-Payloads, gleicher Treiber/Fixture wie die Baseline. Die
nachfolgenden Werte ersetzen den Zwischenstand für die Abschlussbewertung.
Die anschließend analoge PREPARATION-Erweiterung betrifft Material/Kosten und
wird in einer eigenen, identischen Vorher-/Nachher-Teilreihe geprüft; die übrigen
gemessenen Pfade wurden dadurch nicht verändert.

| Tap → Inhalts-Draw-Proxy | Vorher Median / P95 / SD ms | Nachher Median / P95 / SD ms |
|---|---:|---:|
| READY-Liste ohne Maintenance | 1197,69 / 1231,45 / 11,48 | 1197,73 / 1226,39 / 9,66 |
| PREPARATION öffnen | 2399,27 / 2429,91 / 10,87 | 1223,68 / 1269,26 / 15,03 |
| ACTIVE öffnen | 2398,12 / 2418,89 / 9,85 | 1211,44 / 1233,10 / 8,33 |
| ACTIVE Personen | 1233,13 / 1266,66 / 12,54 | 1240,44 / 1258,59 / 7,95 |
| ACTIVE Aufgaben | 1276,92 / 1350,87 / 23,57 | 1272,65 / 1336,32 / 21,52 |
| ACTIVE Funktionen | 1204,45 / 1220,10 / 7,27 | 1207,28 / 1216,44 / 6,88 |
| ACTIVE Protokoll | 2410,43 / 2429,02 / 9,19 | 1231,57 / 1247,49 / 6,85 |
| ACTIVE Material | 2417,07 / 2437,85 / 11,00 | 1238,52 / 1282,51 / 15,29 |
| ACTIVE Kosten | 2425,92 / 2442,56 / 10,22 | 1234,69 / 1249,39 / 8,24 |
| ACTIVE Gedächtnisprotokolle | 2396,39 / 2414,17 / 7,01 | 1210,90 / 1225,63 / 7,07 |
| Dokumentenliste | 1216,89 / 1227,03 / 6,35 | 1222,95 / 1233,50 / 7,34 |
| Dokumente → Lagebild | 1212,87 / 1217,17 / 4,00 | 1222,31 / 1236,76 / 8,89 |
| JPEG | 1212,88 / 1230,68 / 7,79 | 1212,30 / 1228,04 / 7,06 |
| PNG | 1231,71 / 1238,91 / 4,93 | 1235,83 / 1260,85 / 10,34 |
| UTF-8 erste Seite | 1226,58 / 1266,12 / 13,03 | 1226,29 / 1307,42 / 25,08 |
| UTF-8 nächste Seite | 1229,70 / 1238,93 / 8,13 | 1231,24 / 1237,44 / 8,15 |
| PDF erste Seite | 1569,84 / 1588,30 / 14,31 | 1583,81 / 1620,87 / 17,76 |
| PDF nächste Seite | 1579,15 / 1598,58 / 12,25 | 1575,89 / 1594,76 / 8,60 |
| PDF zurück | 1576,02 / 1588,47 / 8,24 | 1579,07 / 1596,91 / 8,59 |

Rund 1,18 Sekunden beziehungsweise 49 % Medianverbesserung bei Aktionsöffnung,
Protokoll, Material, Kosten und Gedächtnisprotokollliste. Keine belastbare
Latenzverbesserung der Viewer oder der bereits in Runde 1 gebündelten Rücknavigation.
Insbesondere erhöhte P95 bei erster Text-/PDF-Seite werden nicht verschwiegen.

Frames final: 2797 gültige Deadlines, 500 überschritten (17,88 %), keine
verlorenen Listener-Ereignisse. TOTAL Median 7,862 ms, **P95 48,294 ms gegenüber
42,652 ms vorher**; kein pauschaler Jankgewinn, trotz geringfügig niedrigerer
Deadlinequote. Scroll Median/P95 7,080 / 11,570 ms; Maximalwert 273,446 ms.
Layout-P95 0,176 ms. Unterschiedliche Framezahl ergibt sich aus unterschiedlichen
Operationsdauern; gepoolte Frames sind korreliert und erlauben keine kausale
Prozentbehauptung zur gesamten App-Flüssigkeit. Weitere Frame-/GPU-/Compose-
Ursachen sind nicht abschließend lokalisiert. **PERFORMANCE OFFEN.**

Ressourcen final: FD erneut 84→139, identisch zur Baseline. Nativer Zähler
5622816→7187456 Bytes nach Cleanup/GC; vorher 5647808→5339552. Einzelne
Endmessungen (einschließlich des unbrauchbaren 0-Zwischenwerts) erlauben weder
einen RAM-Gewinn noch einen Langzeit-Leaknachweis. Warme DB-FD-/PSS-Prüfung und
gezielte PDF-Lifecycleprobe gesondert. Thermalstatus wiederum 0→0. Kein Anspruch,
dass die zweite Runde alle subjektiven Ruckler oder nativen Speicherspitzen löst.

### PREPARATION und schnelle Folgetaps: gezielte Ergänzungsreihen

**GEMESSEN/BESTANDEN:** PREPARATION-Material und -Kosten, zehn Läufe vor/nach,
gleiche bisherige Fixture und gleicher optionaler Teilreihentreiber. Alte
eingefrorene Runde-1-APK gegen Bereichsbündelung, ohne Leadership-Port im Probe.
Material Median/P95 2421,24/2429,69→1229,13/1249,22 ms; Kosten
2420,94/2435,03→1236,77/1247,50 ms. Etwa 1,19 Sekunden beziehungsweise
49 % weniger Medianwartezeit. Es werden nur die ausdrücklich gewählten Boards
geladen; kein Protokoll-/P3b-Prefetch in PREPARATION. Bestehende Fachregeln
einschließlich bewusster COMPLETED-Einstiege bleiben in den Stores.

**BEOBACHTET:** Ergebnis-Fencing allein verhinderte alte UI-Publications, ließ
jedoch bereits eingereihte, noch nicht zugelassene alte Workspacejobs weiter
SQLCipher/Room öffnen. Daher zusätzlich WorkspaceReadTicket als atomarer
**Abbruchhinweis**, ausdrücklich keine Session-/Key-/Storage-Autorisierung.
ViewModel-Neuanforderung, Navigation, Lock und Cleanup widerrufen alte Tickets.
Data prüft sie vor Namespace-/Inventar-/Key-/DB-Zulassung und nach vollständigem
Cleanup sowie bei der Publication. Eine schon zugelassene DB-Operation behält
alle ursprünglichen Context-, Integritäts- und Cleanupprüfungen. Keine neue
StorageContext-Implementierung, keine Thread-Unterbrechung, keine gelockerten
Sessiongates. Insbesondere werden echte Storagefehler weiterhin fail closed
behandelt, auch wenn das UI-Ticket während der Arbeit widerrufen wird.

**GEMESSEN/BESTANDEN:** gezielter Burst Personen→Aufgaben→Kosten im selben
Main-Thread-Aufruf, zehn Wiederholungen. Baseline ist hier ausdrücklich die
bereits gebündelte zweite Runde **ohne** Queue-Abbruch; Nachher mit Ticket.
Identischer Rapid-Teilreihentreiber und synthetische Aktionen, keine
Dokumentimporte in beiden Rapid-Fixtures. Nicht mit der größeren vollständigen
UI-Reihe vermischen oder deren Zeiten zur Produktgesamtlatenz addieren.

| Letzter Bereich fertig, Inhalts-Draw-Proxy | Median | P95 | SD |
|---|---:|---:|---:|
| Ohne Queue-Abbruch | 3644,94 ms | 3663,82 ms | 8,59 ms |
| Mit Queue-Abbruch | 1238,05 ms | 1264,09 ms | 11,16 ms |

Differenz 2406,88 ms / 66,03 % im Median. Keine künstliche Wartezeit, kein
Ergebnis-/Klartextcache. In dieser Stichprobe konnten die alten Jobs noch vor
DB-Zulassung entfallen. Ein bereits zugelassener alter Read darf weiterhin seine
vollständige Operation beenden und den neuesten Read entsprechend verzögern;
kein Versprechen stets nur einer Operation unter jedem Scheduling.
FD jeweils 84→135; Thermalstatus jeweils 0→0. Native Endzähler
26397408→16565520 Bytes zwischen Varianten sind einzelne Heap-/Allocator-
Momentaufnahmen und werden nicht als belastbarer RAM-Gewinn behauptet.

Die vollständige Normalnavigationsreihe oben stammt aus dem Stand unmittelbar
vor dem Queue-Ticket; die Ticketänderung wurde gezielt mit dieser separaten
Vorher-/Nachherreihe und zusätzlichen Backend-Abbruchtests geprüft. Keine
Behauptung, alle früheren Messungen seien nach jeder letzten Änderung vollständig
erneut ausgeführt worden.

## Abschlussstand der zweiten Runde

**IMPLEMENTIERT UND INSTALLIERT; PERFORMANCE UND NEUE GERÄTEABNAHME OFFEN.**
Keine pauschale GRÜN-Bewertung der Flüssigkeit. In den ausgeführten Prüfungen kein
neuer Sicherheitsblocker; keine unabhängige Sicherheitsprüfung oder Zusicherung
für andere Geräte/OEMs. Issue #26 bleibt verbindliches Gesamt-Release-Gate.

### Letzte UI-Korrektur und abschließende Tests

Die zusätzliche unveränderte P9-UI-Probe fand eine echte Navigationsregression:
Beim Zurückgehen zu einem Fragebogenabschnitt wurde dessen alte Submit-/Footer-
Position wiederhergestellt. Bereichsübersichten behalten jetzt ihre Rückkehr-
Geometrie; bewusst betretene Editoren/Abschnitte beginnen oben. Viewer behalten
innerhalb derselben offenen Ansicht ihre Geometrie über den Seitenwechsel,
ein erneuter bewusster Einstieg beginnt neu. Nur Pixelwerte, keine Payloadcaches.

Nach Korrektur **BESTANDEN**: unveränderte P9-UI-Probe bei Fontscale 1,0,
einschließlich freier Erinnerung, Abhängigkeiten/Detailentscheidungen, persistenter
Entwürfe, Abschluss, Ergänzungen, Hub-Privatsphäre, COMPLETED und Lock/Reauth.
Keine Behauptung einer manuellen TalkBack-/Großschriftabnahme.

Abschließend **1216 Hosttests bestanden**, keine Fehler/Skips. Nach der Queue-
Erweiterung **alle zwölf Workspace-Androidtests bestanden** (84,582 s), darunter
Abbruch vor Inventar/Key/DB-Zulassung und Abbruch einer wartenden Publication ohne
Widerruf der weiterhin gültigen Testgeneration: ein neuer Read funktioniert.
Zusammen mit den 36 unverändert gebliebenen Dokument-/Recoveryfällen aus der
44er-Reihe ergeben sich **48 verschiedene Storage-/Workspacefälle**, mit gezielten
Wiederholungen der Workspace-Suite; keine Addition von Wiederholungen als neue Fälle.
Warme FD-Zahl 83→83; PSS 64903→66761 KiB. Dies ist eine endliche Momentaufnahme,
kein RAM-Gewinn oder Langzeit-Leaknachweis.

Zusätzlich tatsächlich erneut ausgeführt und **BESTANDEN**:

- PDF-memfd-/Isolationsmachbarkeit, FD 83→83.
- PDF-Viewerprobe mit Fehlern/Lifecycle, FD 82→81, nativer Delta-Zähler −19664 Bytes.
- Text-UI-Probe einschließlich Seitenwechsel/Lock.
- Produkt-/Demobuild sowie beide Manifestprüfungen.
- Python-Toolsyntax und `git diff --check`.

Die zehn vollständigen UI-Läufe, je zehn PREPARATION- und Rapid-Vergleichsläufe,
40 Loader-Geometriefälle je Variante und 70 zusätzlichen PDF-Variantenzugriffe
oben sind Messreihen/Probes, nicht weitere unabhängige Host-/Storage-Testfälle.
Die enge letzte Editor-Scrollkorrektur wurde gezielt mit unveränderter P9-, PDF-
und Text-UI-Probe nachgeprüft; die komplette normale Performancezeitreihe wurde
danach nicht nochmals als angeblich neuer Lauf ausgegeben.

### Abschließende Entsperrmessung mit Queue-Code

Zehn neue synthetische Authentifizierungen mit echtem Coordinator/Maintenance:
Auth→READY Median/P95 **2395,74/2410,26 ms**, Auth→Listen-Draw
**3626,59/3651,08 ms**, READY→Listen-Draw **1232,20/1240,82 ms**.
Auth→Listen-Draw Standardabweichung 9,94 ms; Baseline 11,53 ms.
Gegenüber 4804,81/4823,09 ms vorher: **1178,22 ms / 24,52 %** Medianverbesserung.
Die nachfolgende reine Editor-Scrollkorrektur verändert diesen Readpfad nicht;
keine erfundene neue Produktmessung nach Installation oder Eingabe echter Credentials.

Weitere nachgewiesene Medianunterschiede der Bereichsreihe: ACTIVE öffnen
1186,67 ms / 49,48 %, PREPARATION öffnen 1175,58 ms / 49,00 %, Protokoll
1178,86 ms / 48,91 %, Material 1178,54 ms / 48,76 %, Kosten 1191,23 ms /
49,10 %, Gedächtnisprotokolle 1185,50 ms / 49,47 %. Kleine Viewer-/Rückweg-
Unterschiede gelten weiterhin als kein belastbarer Gewinn. PDF-Erstöffnung
13,97 ms / 0,89 % langsamer im Median; gepoolte Frame-P95 weiterhin schlechter.

### Signaturkompatibles Produktupdate

App-ID vor Installation **org.pag.prototype.a**. Installierter APK-Code und neue
APK durch apksigner geprüft, identischer Zertifikat-SHA256:
`9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134`.
Zur Prüfung ausschließlich installierten APK-Code kopiert, keine private
Produktdatenbank, Dokumente oder Credentials. Keystore vor Build und Prüfung
unverändert, SHA256:
`a296e242d1a5dfeae24471e435e4ae427f29df812ec1c5a8557c59bce3f2deb7`.
Produkt-APK SHA256:
`37821b50e8152f8739063df0764cbd8bb51bf93c277746dba3522c276d26ee76`.

`adb -s "$ANDROID_SERIAL" install -r` meldete **Success**. Kein Uninstall, pm clear,
Datenbankreset, Keystorewechsel oder automatisches Produktentsperren. Kein
automatisches Lesen/Mutieren produktiver Bestände und deshalb kein behaupteter
Hash-/Inhaltsnachweis über deren reale Daten. Timing-Tags für Bild, Text, PDF und
Kamera nach Messung auf WARN gesetzt; neue interne Beobachter bleiben standardmäßig
ungebunden. Keine Telemetrie oder Netzwerkfunktion.

### Gesamtbericht A–P

| Punkt | Ergebnis / verbleibende Grenze |
|---|---|
| A — Engpässe | SQLCipher-/Room-Öffnungen dominieren; vollständige Integritätsprüfungen bleiben. Frameausreißer noch nicht abschließend lokalisiert. |
| B — Klasse A | Maintenance-/Initial-Action-/ausgewählte Bereichs-Reads gebündelt; alte Queue-Einträge übersprungen; stabile Loader-/Viewerflächen und gezielte Scrollgeometrie. |
| C — Aktionsliste | Synthetischer voller Pfad 4,805→3,627 s Median; READY-Read selbst weiterhin etwa 1,23 s. Keine verschobene Deadline. |
| D — Navigation | Bewusste Bereichseinstiege meist 2,4→1,2 s; Schnellwechsel 3,645→1,238 s in eigener Fixture. Rückweg Dokumente→Lagebild etwa 1,22 s, kein neuer Gewinn. |
| E — PDF | Seitenwechsel etwa 1,58 s, keine neue technische Beschleunigung. Vollständige Authentifizierung/neuer isolierter Auftrag bleiben. |
| F — JPEG/PNG | Etwa 1,21/1,24 s; kein belastbarer Ladezeitgewinn. Feste Anzeigefläche, unveränderte Bitmap-/Decodegrenzen. |
| G — Gedächtnisprotokolle | Headerliste 2,396→1,211 s; kein Antworten-Prefetch. Editor-Scrollregression korrigiert und P9-UI erneut bestanden. |
| H — Frames | Deadlinequote 18,07→17,88 %, aber TOTAL-P95 42,65→48,29 ms. Kein behaupteter Jankgewinn; weitere Profilierung offen. |
| I — Ladeanzeigen | Keine animierten Produkt-ProgressIndicators; ruhige verzögerte statische Hinweise ohne nachträgliche Slotvergrößerung. |
| J — RAM/FD | Warme DB-FDs 83→83, PDF-Probes begrenzt; UI-Anlauf-FDs identisch. Heap-/PSS-Momentaufnahmen kein Leakfreiheits- oder RAM-Gewinnbeweis. |
| K — Sicherheit | Coordinator/180 s/30 s/Generation/Gates/SQLCipher/Tink/Namespace/Isolation/FLAG_SECURE/Schema 11 unverändert. Keine neuen Klartextdateien/Caches/Permissions. |
| L — Tests | 1216 Hosttests; 48 verschiedene Storage-/Workspacefälle in passenden Reihen; PDF-/Text-/P9-Probes, Builds und Manifeste bestanden. Genau ausgeführte Umfänge oben. |
| M — Offen | Voller Unlock weiterhin mehrere Sekunden; Viewer-Floor, Frameausreißer, echte subjektive Flüssigkeit, Großschrift/TalkBack, weitere Geräte und unabhängiger Review. |
| N — Klasse B | A–H aus dem gesonderten Entscheidungsabschnitt unverändert unimplementiert/freigabepflichtig. Raw-Key-Testgewinn nur isoliert auf Testdaten; keine Produktumstellung. |
| O — Geräteabnahme | Nach diesem Update alle 18 oben aufgeführten P11-Abnahmepunkte erneut offen. Runde-1-Bestätigung einschließlich „kein Autoopen“ dokumentiert, keine neue Abnahme erfunden. |
| P — Dateien/Git | Vollständige lokale Dateiliste folgt. Branch p11-performance-optimization, HEAD b3a8c19105e755bc3df6c7207ac0c7e3969702a0; kein Commit/Push/Merge/Release. |

### Vollständige Dateiliste – Abschlussstand

45 lokale P11-Dateien einschließlich erster und zweiter Runde. Keine APKs,
Keystores, Datenbanken, Schlüssel, Logs, Build-Caches oder öffentlichen Testdateien.

- `app/src/androidTest/AndroidManifest.xml`
- `app/src/androidTest/kotlin/org/pag/prototype/P11LoadingGeometryProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P11PdfVariantProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P11PerformanceProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P11UnlockProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P11ViewerPhaseProbe.kt`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ActiveWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/AndroidDocumentPdfRenderer.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentImageDecoder.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ExpenseModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/MaterialModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/MemoryReportModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/MemoryReportScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PagShell.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PreparationWorkspace.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScroll.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProtocolModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/TaskScreen.kt`
- `app/src/test/kotlin/org/pag/prototype/product/WorkspaceBatchTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/DocumentReconciliationStorageTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P11DatabaseBenchmark.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P11KeyFormatBenchmark.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P11WorkspaceStorageTest.kt`
- `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt`
- `data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDelete.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentMaintenance.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentReconciliation.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductActionRepository.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductWorkspaceRepository.kt`
- `data/src/main/kotlin/org/pag/data/prototype/b2/B2Driver.kt`
- `data/src/main/kotlin/org/pag/data/prototype/b2/B2Native.kt`
- `docs/product-p11-performance.md`
- `domain/src/main/kotlin/org/pag/domain/Workspaces.kt`
- `tools/p11/compare.py`
- `tools/p11/frames.py`
- `tools/p11/probe.init.gradle`
- `tools/p11/startup.py`
- `tools/p11/summarize.py`


## Active-Workspace-Assessment: neue Benutzerabnahme

Benutzerbestätigung vom 17.10.2026 für die zweite P11-Runde: Aktionsliste ungefähr
3 s, Aktion ungefähr 2 s, Bereichswechsel ungefähr 1 s; Rückkehr zum Lagebild
weiter mit Ladezustand. Personen/Aufgaben akzeptabel leicht verzögert;
Protokoll/Material/Kosten korrekt; Gedächtnisprotokolle deutlich verbessert,
JPEG/PNG akzeptabel, UTF-8 gut, PDF deutlich verbessert/akzeptabel. Kamera/SAF,
große Dateien und Reauth funktionieren. **Kein Autoopen bestanden.**
Layoutsprünge nicht mehr beobachtet, Ladebalken entfernt, Fragebogen-Scrollposition
korrekt. **Große Schrift/TalkBack mit Ja bewertet.** Sperren während laufendem
Laden wurde in dieser zweiten Abnahme **nicht ausdrücklich erneut bestätigt**.
Dies ist eine Benutzerabnahme, keine neue automatisierte Messreihe. P11 ist
operativ weiter nicht abschließend abgenommen.

Der neue Auftrag bewertet einen sitzungsgebundenen RAM-Aktionsworkspace nur
synthetisch und testexklusiv; keine produktive Stufe-2-Freigabe oder Installation.
Codebefunde, A/B/C-Vergleich, geänderte Integritätserkennungszeitpunkte und
Freigabeentscheidungen stehen im
[Active-Workspace-Assessment](product-p11-active-workspace-assessment.md).


## UI-Stabilisierung nach WIP 1025941 (18.10.2026)

Die folgende Runde enthält keine produktive Stufe2 und verändert keinen
SQLCipher-/Session-/Dokumentvertrag. Vollständiger Nachweis, Mutationsmatrix,
Admission-Spezifikation und Dateiliste: [Assessment, Abschnitte18–25](product-p11-active-workspace-assessment.md#18-ui-stabilisierung-nach-wip-1025941-18102026).

**GEMESSEN:** Samsung/API33, gleiche synthetische große Aktion200Personen/
1000Tasks, jeweils zehn vollständige Läufe. Der Constraintsabsturz entsteht aus
269909px alter Inhaltshöhe als `heightIn(min=...)` bei954px Breite; Korrektur auf
tatsächliche Viewportgeometrie. Große Personen-/Taskübersichten sind jetzt lazy,
mit identischen Zeilen/Schlüsseln und generations-/aktionsgebundenen rein
numerischen Scrollankern. Kein neuer remembered Domainindex/Cache.

| Produktpfad große Aktion | Vorher Median/P95 ms | Nachher Median/P95 ms |
|---|---:|---:|
| Personen | 1681,77 / 1972,66 | 1281,28 / 1304,30 |
| Aufgaben | 1670,68 / 2041,84 | 1290,36 / 1302,41 |
| Aufgaben erneut | 1653,56 / 1811,66 | 1283,31 / 1294,70 |
| Frame-P95 gesamtes Navigationsfenster | 886,59 | 111,64 |

VorherA enthält ausschließlich den zuvor separat geprüften Viewportfix, damit
überhaupt eine vollständige valide große Vergleichsreihe möglich ist. Der
unveränderte DB-Pfad bleibt dominant; initiales Öffnen und Hubrückkehr sind nicht
messbar verbessert. In rein test-only RAM-VarianteC fallen Personen407,17→15,99ms
und Aufgaben422,27→19,09ms (Mediane); dies ist keine Produktlatenz und keine
Stufe2-Freigabe. Andere Bereiche verursachen weiterhin Framelastspitzen, C-Frame-
P95 bleibt222,25ms; kein jankfreies Gesamtprodukt behauptet.

**Tatsächlich erneut geprüft:**1266 Hosttests bestanden; zwölf isolierte
SQLCipher-/Room-/Revokefälle bestanden, warme FD83→83. Small/Typical/Large×A/B/C
mit je zehn Sequenzen sowie zwei Scrollreihen bestanden. Große Schrift und
Accessibilitysemantik werden isoliert geprüft, keine neue manuelle TalkBack-
Abnahme behauptet. Aktuelle P11-Diagnosewerkzeuge verwenden ADB/ANDROID_SERIAL
oder CLI-Parameter statt persönlicher Geräte-/SDK-Kennungen. Bereits öffentliche
Git-Commits bleiben dadurch unverändert sichtbar.

**OFFEN:** Stufe2-Zulassung, vollständige Writer-/Recoveryintegration,
Personrename-Konfliktvertrag, belastbare Admission vor Materialisierung,
unbeschränkte Bestände/Felder, weitere Material-/Kosten-/Frameoptimierung,
unabhängiger Securityreview und spätere manuelle Produktabnahme. Kein
Produktupdate in dieser Runde. Issue#26 bleibt Release-Gate vor1.0.

**BLOCKER UI-Stabilität:** Im erweiterten Aufgabenlauf ist ein sporadischer echter
Material3-`ChildSemanticsNode`-Absturz nachgewiesen. Breite und auf Aufgaben
begrenzte Destinationsbindung waren nicht zuverlässig und wurden verworfen; die
breite Variante verursachte zusätzlich eine Fragebogenregression. Keine solche
Bindung bleibt im Produktcode. Die obigen Zahlen betreffen den beibehaltenen
Viewport-/Lazy-WIP-Stand; Performanceerfolg ersetzt keinen fehlerfreien erweiterten
UI-Lauf. Kontrolllauf mit gesicherter Vorher-APK bestanden, bei sporadischem Fehler
keine eindeutige Ursachenzuordnung. Belege, fehlgeschlagene/erfolgreiche Läufe und
nötige weitere Prüfung siehe Assessment26. Keine Produkt-/Stufe2-Freigabe,
kein Produktupdate. Keine Semantik entfernt, keine Exception im Produkt maskiert,
kein Frameworkpatch/Dependencywechsel.


## Semantik-Untersuchung nach WIP365341f

Aktueller Befund und vollständige neue Teststatistik stehen in Assessment27.
Kein weiterer Performanceumbau in dieser Untersuchung. Der direkte Snapshot-Read
im Semantikblock des deaktivierten Lazy-Aufgabenfooters erzeugt den dokumentierten
Appcrash auch ohne Accessibility-Automation. Kontrolliert50/50 Crashes vor und
0/50 nach Übergabe eines während Composition berechneten Semantiktexts, gleiche
Test-APK. Accessibility-Status bleibt ausdrücklich geprüft. Die früheren
Root-Key-Hypothesen bleiben verworfen. Der aktuelle Header ersetzt die historische
Blockereinschätzung des vorherigen Abschlusses; Gesamtfreigabe wegen fehlgeschlagener
Erreichbarkeitsregressionen und ausstehendem Review weiterhin offen.
