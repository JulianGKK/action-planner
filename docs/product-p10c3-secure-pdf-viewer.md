# P10c3 — Geschützter interner PDF-Viewer

**Bewertung: FUNKTIONAL GRÜN. PERFORMANCE OFFEN. UNABHÄNGIGER CODE-REVIEW OFFEN.**
Automatisierte technische Prüfungen bestanden; Produktupdate installiert und alle
23 manuellen Abnahmepunkte vom Benutzer bestätigt. Dies ist keine vollständige
unabhängige Sicherheitsfreigabe.

Stand: 17.10.2026. Ausgangspunkt `0e1f6ebe10f73248eb86180996d35ed45a4b6b53`
(P10c2-Merge), Arbeitsbranch `p10c3-secure-pdf-viewer`. Der vom Benutzer ausdrücklich
übernommene lokale Zwischenstand wurde weitergeprüft. Commit und Push des
Featurebranches sind zum Abschluss autorisiert; kein Merge oder Release.
Die ursprünglichen P10a-Aussagen bleiben historisch; diese Erweiterung erlaubt
ausschließlich die hier beschriebene PDF-Anzeige. Release-Gesamtabgleich: Issue #26.

## Phase A: APIs und Gerätenachweis

Öffentliche APIs: `Os.memfd_create` (ab API 30), `MFD_CLOEXEC`, `ftruncate`,
`pwrite`, `pread`, `lseek`, `fstat`, `close`, `ParcelFileDescriptor.dup`,
`PdfRenderer(ParcelFileDescriptor)` und `Page.render(...RENDER_MODE_FOR_DISPLAY)`.
Keine Reflection, versteckten APIs oder API-35-Passwort-/Renderererweiterungen.
minSdk bleibt 30. Quellen: [Os](https://developer.android.com/reference/android/system/Os),
[PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer),
[bindIsolatedService](https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,int,java.lang.String,java.util.concurrent.Executor,android.content.ServiceConnection)).

Samsung SM-M526BR, Android 13/API 33, Sicherheitsstand 2025-05-01, ADB-Ziel
RFCT1186YRJ. Der echte Test lief ausschließlich in `org.pag.pdf.p10c3probe`:
synthetische PDF-Erzeugung direkt im memfd, Größen-/Seek-/pwrite-Prüfung,
PFD-Duplikat per Binder, Öffnen und Rendern einer roten Seite, Seitenanzahl 1,
anschließend Page/Renderer/PFD-Close. Zwölf Durchläufe einschließlich ungültiger
Bytes und ungültigem Seitenindex: **PASS**. Renderer-FDs stabil **83 → 83**.
Abweichende isolierte UID, fehlendes INTERNET, Binder-Tod nach Unbind,
absichtlicher Prozesscrash und Unbind bei beschäftigtem Worker: **PASS**.
Dateiinventar der geprüften privaten Testverzeichnisse unverändert.
Nachweisdatei lokal: `.prototype-cache/p10c3-phase-a-device.log`.

Der Machbarkeitsnachweis wurde vor der fortgesetzten Produktprüfung wiederholt.
Eine Pipe wird nicht verwendet. Es gibt keinen Klartext-Dateifallback.

## Architektur und Prozessentscheidung

Direktes Parsing im Produktprozess würde fremde PDF-Strukturen mit Zugriff auf
die gesamte Prozessumgebung einschließlich möglicher Schlüsselkopien verarbeiten.
Deshalb verworfen. Gewählt: nicht exportierter gebundener `PdfRenderService`,
`isolatedProcess=true`, eigener Prozess, neue `bindIsolatedService`-Instanz je
bewusst angeforderter Seite. Das folgt der Android-Empfehlung für fremde PDFs.

Der Parser erhält nur einen vollständig authentifizierten, maximal 25 MiB großen
PDF-memfd, einen auf 8 MiB vorbereiteten Pixel-memfd, Seitenindex und Reply-Binder.
Keine Dokumentnamen, IDs, Pfade, DB-/File-Keys, Credentials, Quell-URIs oder
Storage-/Auth-Ports. Kein ContentProvider und keine neuen Permissions.
Der isolierte UID-Prozess erbt keine Appberechtigungen. Er wählt keine Dokumente
aus und öffnet keine Produktblobs, Room oder SQLCipher. `PagApplication` enthält
nur passive CameraX-Konfiguration; keine globale DB-/Runtimeinitialisierung.
Die DB bleibt ausschließlich im bisherigen Hauptprozess; Room-Multi-Instance-
Invalidation bleibt entfernt. Der Manifesttest prüft exakt diesen einen Service;
das Demomanifest entfernt ihn weiterhin vollständig.

## Autorisierter Readport, SQLCipher und Tink

`DocumentRepository.readPdf(actionId, expected, page, request, renderer, publish)`
nutzt `DocumentReadDispatch`, den vorhandenen READY-StorageContext und dieselbe
`DocumentNamespaceLane`. Keine zweite Session-, Storage- oder Schlüsselarchitektur.

Die kurze Read-Transaktion prüft bestehenden Gesamtbestand, Action/Status,
vollständigen DocumentAsset-Expectedvergleich, PDF-Format und Delete-Jobs.
PREPARATION/ACTIVE/COMPLETED erlaubt, DRAFT/DELETION_PENDING verweigert.
COMPLETED hat ausschließlich bewussten Einstieg. Technischer Snapshot bleibt in
Data: Blobname, 32-Byte-File-DEK, erforderliche/optionale Blobs und Stagingbestand.
Room und DB-Key-Lease enden **vor** memfd-Befüllung und Parserarbeit.
Schema **11**, SQLCipher/Room, Import-/Delete-/Recoveryverfahren und Blobformat
bleiben unverändert. Kein neues Schema, keine Migration, keine Dependency.

Die bestehende Tink-Streaming-AEAD liest in 32-KiB-Blöcken mit Dokument-ID als AAD.
Die gesamte Datei muss einschließlich letztem Tag und authentifiziertem EOF,
exakter Klartextgröße und bestehender `%PDF-`-Plausibilisierung bestehen.
Der memfd wird schon dabei befüllt, aber erst nach erfolgreichem Stream-Close,
abschließender Dateiidentitäts-/Inventarprüfung und erneutem Sessiongate an den
Parser übergeben. Kontrollierte Puffer und File-DEK-Kopien werden überschrieben.
Bei memfd-Schreibausfall wird weiter vollständig authentifiziert und das finale
Inventar geprüft: Integritäts-/Storagefehler dominieren den neutralen Viewerfehler.
Kein Rendering nach Tag-/AAD-/Key-/Längenfehler. Der bestehende BLOCKED-Vertrag gilt.
Authentifizierte, aber syntaktisch ungültige PDFs sind davon getrennte Viewerfehler.

## FD-Eigentümerschaft und Lifecycle

Data besitzt den `DocumentPdfInput` im `use`-Scope. Er besitzt genau einen
PDF-memfd; Streaming ohne vollständige zusätzliche PDF-ByteArray-Kopie.
Der Androidadapter besitzt außerdem den Pixel-memfd und einen begrenzten
Callbackthread. Beide lokalen Binder-PFD-Duplikate werden unmittelbar nach dem
Senden geschlossen. Der empfangende Prozess besitzt seine unabhängigen Kopien.
`PdfRenderer` übernimmt seinen Eingabe-PFD bei erfolgreicher Konstruktion;
zusätzliches idempotentes PFD-Close deckt den Konstruktorfehler ab. Jede offene
Page endet vor dem Renderer. Output-PFD, Pixelarray und Bitmap werden im Service
vor der Antwort geschlossen/bereinigt. `Bundle.clear()` verwirft auch lazy
Parcel-FD-Kopien. Kein wiederverwendeter Parser oder Seiten-/PDF-Cache.

Beim Erfolg, Fehler, Crash, Timeout oder Revoke wird ungebunden. `onDestroy` läuft
auf dem unabhängigen Servicemainthread und beendet den eigenen isolierten Prozess,
auch wenn der Parserworker beschäftigt ist. OS-Prozessende schließt dessen FDs.
Ein Callback-/Verbindungsfehler kann die lokale finally-Freigabe nicht überspringen.
Bind/Antwort warten maximal 15 Sekunden und prüfen alle 50 ms die aktuelle
Request-/Sessionberechtigung. UI und SessionCoordinator-Monitor warten nicht auf
Parser oder Prozessende. Betriebssystem-Scheduling kann Cleanup verzögern;
es gibt keine Echtzeitgarantie.

Lock, BLOCKED, MAINTENANCE, Ablauf, Background/Screen-off, Activity-Destroy,
ViewModel-Cleanup, Aktions-/Bereichswechsel und Schließen invalidieren Requests
und entfernen die sichtbaren Referenzen. Alte Ergebnisse werden geschlossen,
die neue Session zeigt nichts automatisch. Öffnen/Blättern sind Interaktionen;
Rendering und Callbacks verändern keine Deadline (180 s absolut, 30 s in READY).
FLAG_SECURE bleibt erhalten. Native Calls werden nicht als kooperativ abbrechbar
behauptet; Fencing und Prozessgrenze verhindern spätere UI-Freigabe.

## Seiten- und Speichergrenzen

Eine sichtbare Seite, kein Prefetch, keine Thumbnails, keine Bitmapliste.
Seitenanzahl 1–10.000, Index innerhalb der tatsächlichen Anzahl. API-33-Seitenmaße
sind Integer, daher keine NaN-/Infinity-Eingaben. Vor Allokation: positive Maße,
höchstens 1.000.000 je Quellseite, Seitenverhältnis höchstens 100:1,
Long-Multiplikation und kontrolliertes Skalieren. Ausgabeseiten höchstens
2048 × 2048 und **2.097.152 Pixel / 8.388.608 Bytes ARGB_8888**.
Gleiche Prüfung nochmals im Hauptprozess vor Bitmapallokation; Pixel-FD-Größe,
Index und Seitenanzahl werden unabhängig validiert. IPC-Antworten akzeptieren nur
die erwarteten primitiven Felder/Typen, kurze feste Fehlerkategorien und keine
FD-Anhänge; verworfene Bundles werden geleert.

Zusatzkopien je laufendem Request: je Prozess maximal ein Pixelarray von 8 MiB und eine Bitmap von
8 MiB; gemeinsamer Output-memfd maximal 8 MiB, PDF-memfd maximal 25 MiB. Konservativ
bis 40 MiB kontrollierte Pixelrepräsentationen im laufenden Request zusätzlich zum PDF, plus kleine
Streamingpuffer. Das ist **keine Grenze des gesamten nativen PDF-Speichers**:
PDFium, Binder, Grafiksystem und JVM/GC halten weitere Allokationen/Kopien.
Eine schon angezeigte Bitmap wird wie im Bildviewer nicht gegen den Renderthread
recycelt; ihre Referenzen werden entfernt, tatsächliche Freigabe erfolgt durch
Grafiksystem/GC. Keine garantierte sofortige physische Speicherlöschung.

Ein memfd ist ein anonymes Kernel-Speicherobjekt ohne persistente Pfadablage.
Dies garantiert **nicht**, dass physische Speicherseiten niemals ausgelagert,
komprimiert oder vom OS kopiert werden. Keine Klartext-PDF-Datei in filesDir,
cacheDir, noBackupFilesDir, externem Speicher oder `/tmp`; keine Flash-/RAM-
Secure-Erase-Zusage. Öffentlich bereitgestellte synthetische Importoriginale
gehören ausdrücklich nur zur Testvorbereitung, nicht zum Viewerpfad.

## UI, Fehler und ausgeschlossene Funktionen

„PDF ansehen“ nur bei PDF in der Dokumentenliste. Anzeige: Dokumentname, aktuelle
Seite/Gesamtzahl, „Seite zurück“, „Seite weiter“, „Zur Dokumentenliste“.
Vor Seitenwechsel wird die alte Seite freigegeben und erneut vollständig gelesen.
Ladezustand sperrt weitere Seitenjobs; kein UI-Thread-Parsing.

NOT_FOUND, STALE, NOT_ALLOWED bleiben getrennt; Sessionverlust publiziert nichts.
Integritäts-/Bestandsfehler blockieren wie bisher. Getrennte neutrale Kategorien:
ungültig/nicht unterstützt, zusätzlicher PDF-Passwortschutz (SecurityException
beim Konstruktor), Seite zu groß, anonymer Speicher nicht verfügbar und
Renderer-/Binderfehler. Keine sensitiven Exceptiontexte/Stacktraces aus Appcode.
Kein Passwortdialog oder Versprechen, jedes gültige PDF zu unterstützen.
Originalblob bleibt bei sämtlichen Viewerfehlern erhalten.

Ausschließlich `Page.render`: keine Link-/URI-Auswertung, Skripte, Formulare,
Anhänge, Text-/Signaturauswertung, Clipboard, Druck, Sharing, Export,
ACTION_VIEW/ACTION_SEND, WebView oder externer Viewerfallback.

## Automatisierte Prüfung und bekannte Testgrenzen

Hostlauf: **541 App + 650 Data + 4 Security = 1.195 Tests, keine Fehler/Skips**.
Einschließlich sieben PDF-DAO-Zulassungstests gegen den tatsächlichen Admissioncode.
Neue PDF-Tests prüfen Format-/Status-/Expectedbindung, einzelne Seitenjobs,
Lock/Close/Generation/Action-Wechsel, späte Ergebnisse ohne Publication,
Dimensions-/Indexgrenzen, vollständige Tink-Verifikation, falsche AAD/Keys/Längen,
Tagfehler/Truncation, fehlende/zusätzliche/Symlink-Blobs, finale Inventaränderung,
Sink-Close, Key-Wipe und Ciphertextunverändertheit. Eigene Dispatchtests prüfen
queued Cancellation und Lock vor/nach Rendering sowie Fehler-/BLOCKED-Trennung.
FDs/PDFium/Binder werden auf Android geprüft, nicht durch Hostsimulation bewiesen.

Drei im Ausgangsbestand fehlschlagende Importtests wurden repariert: gültige
UTF-8-Fixtures statt Byte 0x01, damit Commit-/Bindingfehler erreicht werden, und
explizite TOO_LARGE-Erwartung am schon bestehenden Importlimit. Keine gelöschten
Tests oder Änderung/Abschwächung des Importproduktcodes.

Viewerprobe auf Samsung: **PASS**. 15 Renderaufrufe, drei Seiten mit Text/Formen,
Bild und unterschiedlichen Formaten, große skalierte Seite, extreme abgewiesene
Seite, ungültiges/abgeschnittenes/passwortgeschütztes PDF, externer Link,
eingebettete synthetische Datei ohne Export, ungültige Indizes, Cancel,
Bindercrash/-timeout, unerwartete FD-Anhänge/Felder/Feldtypen in IPC-Antworten,
FD-Lifecycle, JPEG/PNG-Decoderregression, Compose-Vor/Zurück,
Lock und bewusster Wiedereinstieg ohne Autoopen. FDs **82 → 83**, native
Heapdifferenz **−32.736 Bytes** nach GC vor dem Compose-Teil. Dies ist ein begrenzter Verlauf,
kein allgemeiner Leak-/OOM-Beweis. Private Testdateiinventare unverändert.
Busy-/Crashinjektion nutzt ausschließlich den unregistrierten Debug-Probeservice
mit Testmanifest; keine produktiven Fehlersteuerungen.

Isolierter SQLCipher-/Recovery-Regressionslauf: **32 Tests bestanden** in 763 s
(PDF 5, Bild 5, Text 8, Reconciliation/Recovery 14). Zusätzlich bestand der neue
Test `realReadPortNeedsNoDbKeyDuringFileAndRenderWork`: echtes Repository, echter
SQLCipher-Snapshot und Tink; DB-Key-Owner beim Erstellen des PDF-Sinks invalidiert,
anschließendes Dateilesen/Rendering gegen Test-Sink und Publication erfolgreich.
Damit **33 unterschiedliche Android-JUnitfälle** bestanden. Der bestehende
`P10c2TextProbe` bestand zusätzlich als isolierter Text-UI-Regressionslauf.
Keine automatische Kameraaufnahme und kein automatisierter Produkt-SAF-Import;
diese benötigen die manuelle Produktabnahme. Hosttests sichern Kameralogik ab.
Beide öffentlichen synthetischen PDFs wurden in Samsungs „Eigene Dateien“ und
im SAF-Picker sichtbar geprüft und dort einzeln ausgewählt; MediaStore bestätigt
für beide `application/pdf`. Keine echte Datei geöffnet oder erfasst.
Die separate manuelle Produktabnahme ist unten als Benutzerbestätigung dokumentiert.
Die Tests belegen keinen Angriff auf den Kernel und keine Garantie gegen alle
bösartigen PDFs. Native Speichererschöpfung/Parserfehler bleiben Restrisiken,
durch isolierte UID und begrenzte Requestdauer eingegrenzt. Sicherheitsupdates des
Geräts bleiben relevant. Nur dieses Samsung/API 33 geprüft, keine komplette API-
oder Herstellermatrix. #26 und unabhängiger Securityreview bleiben Release-Gates.

Die vollständige unabhängige Code-/Sicherheitsprüfung ist noch nicht erfolgt;
der unabhängige GitHub-Code-Review durch ChatGPT folgt nach Commit und Push.
Die hiesige Abschlusskontrolle ersetzt ihn nicht. Issue #26 bleibt das
verbindliche Gesamt-Release-Gate vor Version 1.0.

Eine gesonderte **TalkBack-Abnahme ist offen** und war nicht Bestandteil der
bestätigten 23 Schritte. Fokusreihenfolge, Bedienelemente und Seitenwechsel mit
TalkBack sind noch auf dem Gerät zu prüfen. PDF-Seiten werden grafisch als Bitmap
dargestellt; eine semantische PDF-Textausgabe ist nicht implementiert.

## Reproduktion und Performance

Vor jedem Build vorhandenen `.prototype-cache/debug.keystore` prüfen; bei Fehlen
stoppen. Hosttests ohne App-ID-Override ausführen. Isolierter Build:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest -I tools/p10c3/probe.init.gradle --offline
```

Vor Installation App-IDs `org.pag.pdf.p10c3probe`, `.test` und
`org.pag.data.unlockprobe` prüfen. Niemals Instrumentation gegen Produkt starten.
Probes: `P10c3PdfFeasibilityProbe`, `P10c3PdfViewerProbe`; Data-Runnerklassen
`DocumentPdfStorageTest`, `DocumentImageStorageTest`, `DocumentTextStorageTest`,
`DocumentReconciliationStorageTest`. Danach regulären Produktbuild **ohne**
Init-Skript erstellen und Signaturen vor `adb install -r` vergleichen.

Diagnose nur bei Debugbuild plus explizitem `log.tag.PaGPdfTiming=DEBUG`:
feste Phasennamen und Millisekunden, keine Namen/Inhalte/Pfade/Keys/IDs.
Nach Messung auf INFO zurückgesetzt. Storage-Fixture-Timing ist testlokal.
Rendererprobe: erste Seite **386 ms**, exemplarische letzte erfolgreiche
Einzelphasen: Initialisierung **37 ms**, Page-Open **4 ms**, Render **0 ms**
(Millisekundenauflösung), memfd-Füllung **0 ms**, Bind/IPC/Bitmap gesamt **333 ms**,
Renderer-Cleanup **2 ms**. Im Compose-Teil: Publication **313 / 292 / 337 ms**,
explizite Seitenwechsel **293 / 337 ms**, erster Frame **345 / 310 / 358 ms**.
Der Framecallback ist eine technische Annäherung, kein menschlicher Sichtnachweis.

Echter isolierter Repository-Readport mit synthetischer 2,7-MB-Datei und Test-Sink:
Workerstart **2 ms**, DB-Snapshot/Preflight **1197 ms**, Inventar **2 ms**,
vollständige Tink-Verifikation/Streamübergabe **20 ms**, Publication **1225 ms**.
Direkter Snapshot-/Read-Wiederholungstest: DB **1200 / 1174 ms**, Inventar jeweils
**2 ms**, Authentifizierung/Übergabe jeweils **19 ms**. Der Test-Sink zählt Bytes;
diese Messung enthält keinen nativen PDF-Renderer und keine echte memfd-Befüllung.
`AUTH_AND_MEMFD` misst im Produktpfad gemeinsam Datei-I/O, Tink, Formatcheck,
Sinkübergabe und finale Inventarprüfung; die memfd-Schreibdauer wird separat
gezählt. Eine reine isolierte Tink-CPU-Zeit wurde nicht gemessen.

Diese getrennten Tests nicht zu einer Produktlatenz addieren. Für diese Fixtures
ist die DB-Öffnung deutlich teurer als Streaming oder Seitenrendering; Hypothese
für P11, kein Grund zum Abschalten von SQLCipher-Prüfungen. Produktlatenzen nach
Benutzer-Authentifizierung sind noch nicht gemessen. Beide Diagnosetags nach der
Messung auf INFO geprüft; kein aktives Viewer-Debugtiming im Produktupdate.
P11 bleibt separater Performanceblock. Bestehende Viewer nicht optimiert.

## Produktinstallation und manuelle Abnahme

Regulärer Debug-Produktbuild und Demobuild sowie `verifyPrototypeManifest` und
`verifyDemoManifest` bestanden. App-ID per aapt2: `org.pag.prototype.a`.
Installierte Vorher-APK ausschließlich als Programmcode für den Zertifikatvergleich
gelesen; keine Appdaten/DB kopiert. Vorher-/Nachher-Zertifikat identisch:
`9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134` (SHA-256).
Vorhandener Keystore vor und nach Build unverändert (SHA-256
`a296e242d1a5dfeae24471e435e4ae427f29df812ec1c5a8557c59bce3f2deb7`).
`adb -s RFCT1186YRJ install -r` meldete **Success**. Kein Uninstall, Clear,
DB-Reset oder Schlüsselwechsel; kein automatisches Entsperren. Schema bleibt 11.
Bestehende Produktdaten wurden nicht als Testmaterial geöffnet oder untersucht;
ihr fachlicher Zugriff nach dem Update ist Teil der Benutzerabnahme.

**Benutzerprüfung bestanden:** Der Benutzer hat am 17.10.2026 mit
„Alle Punkte erfolgreich geprüft“ sämtliche folgenden 23 Abnahmeschritte auf dem
Samsung bestätigt. Dies ist die manuelle Benutzerabnahme, kein automatisierter
Testnachweis. Testumfang: ausschließlich synthetische Dokumente.

1. PaG regulär selbst entsperren.
2. Aktion öffnen.
3. Gespeichertes einseitiges synthetisches PDF auswählen.
4. „PDF ansehen“ antippen.
5. Darstellung prüfen.
6. PDF schließen.
7. Mehrseitiges synthetisches PDF öffnen.
8. Eine Seite vorwärts blättern.
9. Eine Seite rückwärts blättern.
10. Seitenreihenfolge prüfen.
11. PDF schließen.
12. PDF erneut bewusst öffnen.
13. PaG bei sichtbarer PDF-Seite sperren.
14. PaG regulär erneut entsperren.
15. Prüfen, dass keine PDF-Seite automatisch erscheint.
16. PDF bewusst erneut öffnen.
17. JPEG und PNG öffnen.
18. UTF-8-Text öffnen und blättern.
19. Direkten Kameraimport mit unkritischer Testszene prüfen.
20. SAF-PDF-Import mit regulärer Reauthentifizierung prüfen.
21. Importiertes synthetisches PDF öffnen.
22. Ein synthetisches Testdokument bewusst löschen.
23. Löschung nach erneutem Entsperren kontrollieren.

Die funktionale manuelle Samsung-Abnahme ist damit abgeschlossen. Konkrete
Produktlatenzen oder eine gesonderte subjektive Performancebewertung wurden
nicht mitgeteilt; die oben dokumentierten Messgrenzen und P11 bleiben bestehen.

## Abschlusskontrolle für Commit und Push

Die oben genannten 1.195 Hosttests, 33 isolierten Android-Storage-/Recoveryfälle,
Machbarkeits-/Viewer-Probes, Text-UI-Regression, Produkt-/Demobuild und
Manifestprüfungen sind die bereits ausgeführten und dokumentierten Nachweise.
Bei der anschließenden Commitvorbereitung wurden sie **nicht erneut ausgeführt**.
Erneut geprüft wurden Git-Ausgangslage, vollständiger Änderungsumfang gegenüber
dem bestätigten P10c2-Merge, Sicherheitsgrenzen im Diff, Keystore-Hash,
Commit-Dateiauswahl und `git diff --check`. Die 23 Benutzerbestätigungen bleiben
ausdrücklich von den automatisierten Tests getrennt.

Beim ersten vollständigen Staging meldete die Whitespaceprüfung die abschließenden
Leerzeichen der festen PDF-xref-Datensätze. Die vier Test-PDFs sind deshalb gezielt
in `.gitattributes` als binäre Fixtures klassifiziert; ihre bereits getesteten Bytes
bleiben unverändert. Die erneute Prüfung umfasst den vollständigen Commitumfang.

Der Commitumfang enthält ausschließlich Quellcode, Manifeste, Buildkonfiguration,
Tests, die vier gezielten synthetischen Android-Testfixtures und Dokumentation.
Keine Produktdatenbanken, produktiven Schlüssel, Keystores, APKs, temporären
Klartext-PDFs, öffentlichen Download-Testdateien, sensitiven Logs oder Build-Caches.

## Abschlussübersicht A–P

| Punkt | Ergebnis |
| --- | --- |
| A — Machbarkeit | memfd + seekbarer PFD + PdfRenderer auf echtem Samsung/API 33 nachgewiesen. |
| B — Isolation | Eigene isolierte UID, nicht exportierter Service, kein Storage/Room/Key/Auth/Netzwerkzugang. |
| C — PDF-Port | Bestehende Repository-, READY-, Expected-, Action-/Status- und Namespacegrenzen. |
| D — Tink | Vollständiges authentifiziertes EOF, Länge, Format und finales Inventar vor Rendererfreigabe. |
| E — SQLCipher/Keys | Schema 11 unverändert; kurze DB-Lease, Key-Owner-Invaliderung vor Dateiarbeit im echten Port getestet. |
| F — Klartext | Kein persistenter Viewerpfad; Testinventare und Code geprüft. Keine physische RAM-/OS-Swap-Garantie. |
| G — Lifecycle | Page/Renderer/PFD/use/finally, Bindercrash/-timeout/Cancel und FD-Anhänge getestet; Renderer-FDs stabil. |
| H — Speicher | Eine Seite, 2.097.152 Pixel/8 MiB je Ausgabebitmap; Zusatzkopien begrenzt, nativer Gesamtheap nicht hart begrenzt. |
| I — UI | Name, Seite/Anzahl, Vor/Zurück und Dokumentenliste; bewusster Einstieg, keine PDF-Aktionen. |
| J — Session | Vorhandene Deadline-/Generation-/Foregroundgates, Cancel/Fencing; Lock ohne Autoopen getestet. |
| K — Host | 1.195 Tests bestanden; Produkt-/Demobuild und beide Manifestprüfungen bestanden. |
| L — Android | Phase A, finale Viewerprobe, Text-UI und 33 unterschiedliche isolierte Storage-/Recovery-JUnitfälle bestanden. |
| M — Performance | Messwerte oben; Produktlatenzen mit Benutzer-Authentifizierung offen, P11 unverändert separat. |
| N — Restrisiken | Native Parser-/OOM-/OS-Risiken, Scheduling, zusätzliche RAM-/Grafikkopien, nur Samsung/API 33 geprüft; #26 bleibt Gate. |
| O — Manuell | Signaturkompatibles Produktupdate erfolgreich; alle 23 Schritte am 17.10.2026 vom Benutzer erfolgreich bestätigt. |
| P — Dateien | Vollständige Liste unten; lokale ignorierte Build-/Testartefakte nicht Teil der Änderung. |

## Vollständige Dateiliste gegenüber main

- `.gitattributes`
- `app/build.gradle.kts`
- `app/src/androidTest/AndroidManifest.xml`
- `app/src/androidTest/assets/p10c3/embedded.pdf`
- `app/src/androidTest/assets/p10c3/extreme-page.pdf`
- `app/src/androidTest/assets/p10c3/link.pdf`
- `app/src/androidTest/assets/p10c3/password.pdf`
- `app/src/androidTest/kotlin/org/pag/prototype/P10c3PdfFeasibilityProbe.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P10c3PdfViewerProbe.kt`
- `app/src/debug/kotlin/org/pag/prototype/pdf/PdfFeasibilityService.kt`
- `app/src/demo/AndroidManifest.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/AndroidDocumentPdfRenderer.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PdfRenderLimits.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PdfRenderService.kt`
- `app/src/main/kotlin/org/pag/prototype/product/PdfTiming.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentPdfModelTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/DocumentPdfStorageTest.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileStreams.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImageRead.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentPdfRead.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductDocumentRepository.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImportStagingTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPdfAdmissionTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPdfDispatchTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPdfReadTest.kt`
- `docs/product-p10c3-secure-pdf-viewer.md`
- `docs/troubleshooting-documents.md`
- `domain/src/main/kotlin/org/pag/domain/Documents.kt`
- `tools/p10c3/probe-manifest.xml`
- `tools/p10c3/probe.init.gradle`
