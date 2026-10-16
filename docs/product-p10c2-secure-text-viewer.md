# P10c2 — geschützte UTF-8-Textanzeige

## Scope und Baseline

Baseline: `main`, Merge `31202354ac9abca5341ede7d25c394c1bcfdebe7`;
Arbeitsbranch `p10c2-secure-text-viewer`, sauberer Ausgangsbestand. Schema **11**
bleibt unverändert. Nur bestehende DocumentFormat.TEXT-Dokumente erhalten einen
internen Read-Only-Viewer. JPEG/PNG behalten den Bildviewer; PDF bleibt ohne
Vorschau. Keine neue Dependency/Permission, Migration, Auth-/Keyhierarchie,
externe App, Clipboardfunktion, Export/Share, HTML/Markdown/WebView, Suche,
Bearbeitung oder Klartextdatei. Keine Performanceoptimierung am Bestand.

## Entscheidung vor Implementierung: begrenzte Extraktion pro Vollscan

Eine logische Seite enthält höchstens **32.768 UTF-8-Bytes**. Die Grenze hält
String-/Layoutarbeit klein und bleibt unabhängig von der 25-MiB-Importgrenze.
Es gibt keine persistente Seitenstruktur und keinen Seitenindex im Dateisystem.
Jeder bewusst angeforderte Seitenwechsel liest dieselbe ausgewählte
Dokumentbindung neu, scannt den gesamten Ciphertext und zählt Seiten von Beginn
an. Nur das angeforderte Fenster wird behalten; andere Klartexte werden nach
blockweiser Prüfung verworfen. Ein bereits gelesenes Ergebnis ist kein
Verifikationsticket für einen späteren Read oder eine spätere Session.

Seiten enden ausschließlich zwischen vollständigen Unicode-Codepoints.
Supplementary-Zeichen bleiben als UTF-16-Surrogatpaar zusammen. Ein CRLF-Paar
bleibt ebenfalls auf derselben Seite. Daher kann eine Seite wenige Bytes unter
dem Limit enden. BOM, Tabs, kombinierte Zeichen, ZWJ/Emoji, Zeilenumbrüche und
fehlender finaler Zeilenumbruch bleiben als Inhalt erhalten. Es erfolgt keine
Normalisierung, Ersetzung, Trimmung oder stille Kürzung. Leere Dateien besitzen
eine leere Seite. Exakte Seitengrenzen erzeugen keine leere Phantomseite.

Die Seitengrenze garantiert Codepoints, nicht unbegrenzt lange graphematische
Cluster: Kombinations-/ZWJ-Folgen können am Seitenwechsel fortgesetzt werden.
Alle Codepoints bleiben vorhanden; vollständige Graphemsegmentation über beliebig
lange Cluster würde die feste Seitenbegrenzung unterlaufen. Normale Layoutfragmente
bevorzugen Plattform-Zeichengrenzen, behalten bei extrem langen Clustern aber die
harte Größenbegrenzung und vollständige Scalars bei. Kein Textverlust beim
Zusammenfügen der Seiten/Fragmente. Diese Darstellungsgrenze ist ausdrücklich
keine Aussage über beschädigte UTF-8-Bytes.

Die gewählte Strategie kostet bei jedem Blättern erneut DB-Preflight und einen
vollen Dateiscan; Rückwärtsblättern ist ebenfalls kein Cache-Hit. Das ist eine
bewusste erste Sicherheits-/Speicherentscheidung, kein Performanceversprechen.
Die Namespace-Lane wird nur während des jeweiligen autorisierten Auftrags
gehalten, niemals während Lesen/Scrollen durch den Nutzer.

## Autorisierter Read-Only-Port und Wiederverwendung von P10c1

`DocumentRepository.readText(actionId, expected, page, request, publish)` ist ein
getrennter, ausschließlich für TEXT freigegebener Port. Text wird nicht als Bild
behandelt. Der Kompatibilitätsdefault bestehender Fakes/Demoports verweigert den
Zugriff. Produktiver Code benutzt dieselben Executor-, StorageContext-,
Generation-, Lane-/Fence- und Publicationgrenzen wie P10c1.

Dafür wurden nur die bestehenden internen Gemeinsamkeiten präzisiert:
`DocumentReadSnapshot` (vorher DocumentImageSnapshot), generischer
`DocumentReadDispatch` (vorher DocumentImageDispatch) und `DocumentReadRequest`.
Die bisherigen Bildtests behalten ihre Assertions; angepasst wurden interne
Typnamen und in einem Always-Throws-Test der nun notwendige generische Ergebnistyp.
Keine zweite Storage-/Sessionarchitektur oder neuer Workerpool.

`DocumentDao.imageSnapshot` und `textSnapshot` verwenden einen privaten
`viewerSnapshot` mit jeweils fest freigegebener Formatmenge. Beide laufen in
kurzer `withReadTransaction`, ohne schreibende Produktoperation. Geprüft werden
READY, OPERATION/STEP, persistierter Actionstatus, Action-/Document-ID, vollständiger
DocumentAsset-Expected-Snapshot, Format, Löschjobs, technische Bindungen und
Kapazitätsintegrität. Ein reservierter Delete-Job verbirgt die Parentrow bereits.
PREPARATION/ACTIVE/COMPLETED erlaubt; DRAFT/DELETION_PENDING abgelehnt.

UI erhält ausschließlich fachliche Metadaten und eine begrenzte Textseite,
niemals Blobname, Key, Row oder Source-URI. DB/Room/Treiber und DB-Key-Lease
sind vollständig geschlossen, bevor Datei-/UTF-8-Arbeit beginnt. Kopierte
File-DEKs werden im finally bestmöglich gewischt. SQLCipher und der bestehende
SessionDbKeyOwner bleiben unverändert.

Ein gezielt abgesicherter Randfall betrifft Viewerabbruch während des DB-Schritts:
Die Auftragsstornierung darf nicht als Widerruf der weiterhin gültigen Session
an den Key-Owner gegeben werden. `withDocumentReadKey` validiert dessen kurze
Lease mit dem ursprünglichen autorisierten Sessioncontext. Sämtliche DB-/Datei-
und Publicationoperationen bleiben zusätzlich am engeren Requestcontext gebunden.
Ein stornierter Auftrag erhält keine Daten; eine echte Sessionrevocation
invalidiert weiterhin den Owner. Es entsteht weder ein neuer Owner noch eine
verlängerte oder persistierte Authfreigabe. Host- und Androidtests prüfen sowohl
Abbruch nach einer echten Read-Transaktion als auch echten Authority-Revoke.

## Namespace und vollständige Authentifizierung

Der bestehende DocumentFileInventory-Readpfad öffnet nur den autorisierten
regulären Blob: kanonische Pfade, opaque Namen, NOFOLLOW, bekannte Namen und
Inventar-/Identitätsprüfung vor und nach dem Read. Fehlender/unbekannter Blob,
Symlink oder widersprüchlicher Bestand wird nicht repariert oder gelöscht.
Dateisystem und gespeicherter Ciphertext werden vom Viewer niemals verändert.
Import-/Delete-Recovery und Reconciliation bleiben bei ihren Koordinatoren.

`DocumentFileStreams.readText` verwendet exakt den vorhandenen Tink-
Verifikationsloop und die unveränderte FileStreamingCipher-Grenze
(AES256_GCM_HKDF_1MB). AAD ausschließlich documentId UTF-8. Alle Segmente,
exakte Klartextlänge und echtes authentifiziertes EOF sind Pflicht. Ein früher
lesbarer Block ist keine Freigabe. Auch bei bereits vollständig extrahierter
Seite wird bis zum Ende weiter authentifiziert und geprüft.

Die Extraktion erhält nur flüchtige CharBuffer-Ausgaben des bestehenden strikten
UTF-8-Formatprüfers. Decoder verwendet REPORT, keine Replacement-Decodierung.
NUL, DEL und die bestehenden verbotenen Steuerzeichen bleiben verboten;
TAB/CR/LF erlaubt. Keine Ausführung oder Linkaktivierung. Erst nach vollständig
abgeschlossenem Datei-/Inventarcheck darf der Seitentransfer veröffentlicht werden.

Ungültige UTF-8-/Textformatbytes werden im Textreader zunächst intern vermerkt,
ohne Text freizugeben. Der Ciphertext wird trotzdem vollständig authentifiziert;
ein später Tag-/Längen-/Closefehler dominiert als Storagefehler. Nur bei sonst
vollständig erfolgreicher Verifikation und abschließend unverändertem Inventar
wird daraus der neutrale Fehler TEXT_FORMAT. Der Import-/allgemeine
Verifikationspfad behält seine bisherigen Fehlerregeln unverändert. Kein
Formatfehler darf einen echten Integritätsfehler oder eine Namespaceänderung
maskieren.

## Speichergrenzen und UI

Der Data-Paginator hält genau ein CharArray von 32.768 UTF-16-Codeunits für das
angeforderte Fenster (höchstens 64 KiB); der finale Transfer benötigt kurz eine
entsprechend begrenzte Kopie. Der ursprüngliche 32-KiB-Streambuffer sowie
bestehende UTF-8-Byte-/Charbuffer bleiben fest begrenzt. Tink hat zusätzlich
segmentweise interne Puffer. Kein 25-MiB-Klartextarray/-String, keine Liste aller
Seiten, kein persistenter Offset-/Seitencache. Obergrenze für Request-Metadaten
1024 Seiten; selbst der ungünstigste erlaubte Scalar-/CRLF-Verschnitt unter
25 MiB bleibt darunter. ASCII-Maximaldatei besitzt exakt 800 Seiten.

`DocumentTextPage` besitzt den mutablen Transferbuffer. Erst unter gültiger
UI-Publication erzeugt DocumentModel die notwendige begrenzte Stringkopie und
schließt/wischt den Transfer sofort. Während des nächsten Reads wird die bisherige
Textreferenz entfernt. Stringkopien sind nicht zuverlässig überschreibbar;
keine Garantie physischer Android-/JVM-RAM-Löschung.

TEXT erhält „Text ansehen“, JPEG/PNG weiterhin „Bild ansehen“, PDF den bestehenden
fehlenden Vorschauhinweis. Keine Inhaltsvorschau auf Hub oder Liste; COMPLETED
behält seinen bewussten Dokumenteneinstieg. Viewer zeigt Name, Ladezustand,
„Seite X von Y“, Vor/Zurück und „Zur Dokumentenliste“. Keine globale Navigation.

Eine feste 360-dp-hohe LazyColumn rendert nur sichtbare Layoutfragmente der einen
Seite. Fragmente höchstens 256 UTF-16-Einheiten, ohne Surrogat-/CRLF-Schnitt;
bevorzugte Plattform-Zeichengrenzen nur innerhalb der harten Limits. Damit werden
auch 32.768 einzelne Zeilenumbrüche nicht als riesiger einzelner Textlayoutblock
allokiert. Fragmentverkettung entspricht exakt dem Seitentext. Kein horizontaler
Scrollzwang oder Ellipsis. Kontrollen vor und nach dem Text bleiben erreichbar.
Normales nicht selektierbares Compose-Text, kein SelectionContainer, LinkAnnotation,
WebView oder Clipboardpfad. Große Schrift folgt bestehendem Produktvertrag.
Menschliche TalkBack-Abnahme bleibt separat offen.

## Session, Lifecycle und Fehler

Öffnen und Blättern sind bewusste Nutzerinteraktionen. UTF-8-Verarbeitung,
Callbacks, Framebeobachtung und Cleanup sind keine Sessioninteraktion.
180 s absolut/30 s READY-Inaktivität, Credentials, SessionCoordinator,
Generation, FLAG_SECURE und Foreground-Lock bleiben unverändert.

`clearText` cancelt den aktuellen RAM-Auftrag und entfernt Text-/Dokument-/
Seiten-/Ladezustand. `clearViewers` wird von bestehendem Model-Cleanup und explizit
bei Activity-Pause/Destroy verwendet. Aktions-/Status-/Bereichswechsel,
LOCKED/BLOCKED/MAINTENANCE und ViewModel-Cleanup schließen die Ansicht. Reauth
öffnet nichts automatisch. Neues bewusstes Öffnen startet bei Seite 1.

Alte/überholte Ergebnisse werden geschlossen, ohne String zu erzeugen.
Schnelle doppelte Seitenwechsel starten keinen zweiten laufenden Auftrag.
Stornierung ist kooperativ zwischen Blöcken und während der Seitenbildung;
Streams und kontrollierte Puffer werden im finally geschlossen/gewischt.
Die Lane wird vor der UI-Publication freigegeben und von Maintenance drained
und gefenced. Keine globale I/O-Arbeit unter dem Sessionmonitor.

Fehler: NOT_FOUND (fehlend/laufend gelöscht), STALE/INVALID (abweichende Auswahl),
NOT_ALLOWED (Typ/Status), ungültige Session (keine alte Publication),
TEXT_UNSUPPORTED (Seite technisch nicht verfügbar), TEXT_FORMAT (vollständig
authentifiziert, aber ungültiger UTF-8-Klartext). Integritäts-/unerwartete
Room-/Inventarfehler bleiben im bestehenden BLOCKED-Pfad. Keine Pfade, Keys,
Exceptiontexte oder Inhalte in Meldungen/Logs; kein automatisches Retry,
kein DB-Reset, keine Löschung oder Ersatzdatei.

## Prüfungen und tatsächlicher Stand

Die bisherigen fokussierten Hostläufe sichern Unicode-/Byte-/Seitengrenzen,
CRLF, Emojis, Kombinationszeichen, lange Zeilen, leere/exakte Seiten, ganze
25-MiB-Dateien, späte Manipulation/Truncation, falsche Keys/AAD/Längen,
strikte UTF-8-Fehler samt Fehlerpriorität, Namespace/Symlinks, readonly-Blobs,
Streamclose/Bufferwipe, erwartete Metadaten, Requests/Generation/Revoke,
Ownererhalt bei Viewerabbruch, echte Ownerinvalidierung bei Sessionrevoke,
Vor/Zurück, schnelle Wechsel, Lock und No-Autoopen sowie Bild-/Kamera-/Import-
und Dokumentenmodellregressionen. **137 fokussierte Hosttests bestanden**
(85 App, 52 Data; davon 33 neue Tests). Keine historische Gesamtsuite ausgeführt.

Android ausschließlich synthetisch und getrennt:
`org.pag.text.p10c2probe` für echte Compose-Oberfläche/Schriftgrößen 1 und 2,
Seitenbuttons, Unicode, reine URL-/HTML-Zeichen, keine Klick-/Copy-Aktion am
Textknoten, newline-lastige Maximalpage, sichtbaren Lock-Cleanup und späte
Ergebnisse. Fakeport in der UI-Probe ist ausdrücklich kein SQLCipher-E2E-Beweis.
Data unter `org.pag.data.unlockprobe`: echte SQLCipher-/Room-Reads, bestehendes
Tink-Staging/Publication, Unicode-Seiten und Wiederöffnen, unveränderte Blobs,
Bindung/Delete-Sichtbarkeit, Fremdformate, späte Manipulation, Widerruf,
authentischer Formatfehler gegenüber Tagfehler, 25-MiB-Datei/letzte Seite und
weiter verwendbarer Sessionowner nach Readerabbruch. Eine bestehende JPEG-/PNG-
Storage-/Decoderprobe dient als gezielte Regression. Keine automatisierten
Tests öffnen oder verändern die Produktdatenbank.

Die erste UI-Probe fand den Accessibility-Textknoten unmittelbar nach dem
Composeupdate noch nicht. Nach begrenztem Warten auf den aktuellen Baum bestanden
dieselben Nicht-Klick-/Nicht-Copy-Prüfungen; keine Schutzprüfung abgeschwächt.
Ein generischer Always-Throws-Hosttest benötigte nach dem typisierten Dispatch
lediglich einen expliziten Ergebnistyp; Assertions bleiben erhalten.

Tatsächlich auf Samsung SM-M526BR/API 33 bestanden:
- Data: 8 neue Text-Storagefälle plus 1 bestehender Bild-Storagefall,
  insgesamt 9 Tests (185,594 s). Nach der abschließenden Inventar-Fehlerprioritäts-
  Korrektur wurden die beiden betroffenen Textfälle erneut bestanden (36,516 s).
- Text-Compose-Probe jeweils mit Font Scale 1 und 2 bestanden.
- Bestehende echte Bilddecoderprobe für JPEG/PNG, EXIF, Downsampling,
  Speichergrenzen und Cleanup bestanden.

Debug-/Demo-Build, App-AndroidTest-Kompilierung, Data-AndroidTest-Build und beide
Manifestprüfungen bestanden. Die Tests liefen ausschließlich in getrennten
synthetischen Testpaketen. Keine Produktdatenbank automatisiert geöffnet,
initialisiert oder zurückgesetzt. Keine Deinstallation. App-ID und Signatur des
Produktupdates wurden mit der installierten APK verglichen; identische Signatur,
unveränderte Versionierung und vorhandener Debug-Keystore.

## Bestätigte manuelle Produktabnahme

Der Benutzer hat auf dem Samsung SM-M526BR, Android API 33, ausdrücklich bestätigt:

1. UTF-8-Text wird korrekt dargestellt.
2. Vorwärts- und Rückwärtsblättern funktioniert.
3. Nach Sperren und erneutem Entsperren öffnet sich kein Text automatisch;
   bewusstes Wiederöffnen funktioniert.
4. JPEG-/PNG-Anzeige, Kameraimport und SAF-Dateiimport funktionieren weiterhin.

**FUNKTIONAL GRÜN — PERFORMANCE OFFEN.** Diese manuellen Ergebnisse sind
getrennt von den oben dokumentierten Host- und isolierten Androidtests zu werten.
Die dort genannten Tests und Builds wurden für diesen Dokumentationsabschluss
nicht pauschal erneut ausgeführt. Nach dem abschließenden Opt-in für die
neue Debug-Zeitdiagnose wurden ausschließlich die 16 Textmodelltests und der
Debug-Build nochmals erfolgreich ausgeführt. Die manuelle Geräteabnahme
bezieht sich auf den zuvor installierten Build; die anschließende Änderung
begrenzt nur die Diagnoseausgabe und ändert keine Viewer-/Sicherheitsentscheidung.

Kleiner offener Punkt für zukünftige Gerätetestvorbereitung: Die per ADB
übertragenen synthetischen Textdateien waren zunächst im Picker nicht sichtbar.
Die Dateien waren vorhanden; nach gezielter Neuerfassung wurden alle drei als
text/plain im Android-Dateiindex bestätigt. Die vorherige Indexlage ist nicht
belegt. Künftig genügt die Übertragung allein nicht: Sichtbarkeit und tatsächliche
Auswählbarkeit müssen sowohl in „Eigene Dateien“ als auch im SAF-Picker vor der
Benutzerabnahme geprüft werden. Kein zusätzlicher Scanpfad oder neue Berechtigung
in PaG; dieser Punkt betrifft die Vorbereitung synthetischer Gerätetests.

## Performance und bekannte Grenzen

Benutzerbefund der finalen Textabnahme: **Textöffnung subjektiv langsam,
Seitenwechsel subjektiv mittel**. Dafür liegen keine konkreten manuellen
Zeitmessungen vor. Die Ursachen sind noch nicht abschließend gemessen;
Untersuchung und Optimierung folgen separat im dedizierten **P11-Block**.
Keine Performancefreigabe. Der bekannte Befund „Bildanzeige langsam“ bleibt offen. P10c2 entfernt keine
SQLCipher-/SQLite-Integritätsprüfung, hält Room/DB-Leases nicht länger offen,
überspringt keine Tink-/Sessiongates und führt keinen Klartextcache ein.

Debugtag `PaGTextTiming`: ausschließlich feste Phasen mit Millisekunden,
zusätzlich durch explizites DEBUG-Logtag-Opt-in begrenzt; standardmäßig inaktiv.
WORKER_START, DB_SNAPSHOT (inklusive bestehendem Preflight und Ressourcenabschluss),
INVENTORY, READ_AUTH_UTF8_PAGE, UTF8_DECODE, PAGE_PREPARE, PUBLICATION,
VISIBLE, PAGE_CHANGE und CLEANUP. Read/Auth/UTF-8/Page sind im Stream verschachtelt:
Der Gesamtwert enthält die separat beobachteten UTF-8-/Pageanteile, ist also
nicht zu diesen zu addieren. UTF8_DECODE zieht die beobachtete Paginatorzeit ab;
PAGE_PREPARE umfasst die Seitenbildung und den Transfer. PUBLICATION endet beim
UI-Callback; VISIBLE wird an einem folgenden Compose-Frame beobachtet und ist
keine externe physische Bildschirmmessung. Keine Namen/Inhalte/IDs/Pfade/Keys.

Bereits auf dem Samsung gemessen (synthetische kleine SQLCipher-Fixture,
kein Produktbenchmark): DB_SNAPSHOT 1181–1214 ms, INVENTORY 1–2 ms,
READ_AUTH_UTF8_PAGE 8–15 ms, UTF8_DECODE 0–1 ms, PAGE_PREPARE 1–8 ms.
Die Werte stammen aus mehreren echten Reads derselben 72.003-Byte-Testdatei.
0 ms bedeutet Messauflösung/Rundung, nicht Abwesenheit von Arbeit. Daraus folgt
keine bewiesene Ursache für die langsame bestehende Produkt-Bildanzeige.
Zusätzlicher tatsächlicher Samsung-Befund für eine synthetische 25-MiB-
ASCII-Datei, letzte Seite 799 von 800 (nullbasiert): DB_SNAPSHOT 1180 ms,
INVENTORY 1 ms, READ_AUTH_UTF8_PAGE 841 ms, davon UTF8_DECODE 358 ms und
PAGE_PREPARE 394 ms. Die Unterwerte sind bereits im Gesamtread enthalten.
Produkt-Workerstart, tatsächliche UI-Publication/Anzeige und manuelle
Seitenwechselzeiten sind noch nicht belastbar gemessen. Die temporäre
Storage-Zeitdiagnose wurde nach den Tests deaktiviert. Keine Performancefreigabe.

Jeder Seitenwechsel kostet einen erneuten vollen Scan. Große Dokumente können
spürbar langsam sein, und die unveränderte absolute Sessionfrist begrenzt die
Lesedauer. Kein persistentes Resume nach Reauth. Keine Garantie physischer
RAM-Löschung, perfekter plattformübergreifender Graphemsegmentierung oder
vollständiger Kontrolle nativer Tink-/Androidpuffer. Der Doku-/Security-Gesamtcheck
vor 1.0 (Issue #26) bleibt separat.
