# P10c1 — geschützte JPEG-/PNG-Dokumentenanzeige

## Scope und Ausgangsbasis

Baseline `main`, Merge `d6dde840cd0bf05af3481b774971b6bf4429d5d2`;
Arbeitsbranch `p10c1-secure-image-viewer`. Schema bleibt 11. P10c1 erweitert die
historische Viewergrenze gezielt für JPEG und PNG, gleichermaßen für SAF- und
P10d-Kameradokumente. PDF/Text, Zoom, Export, Share, OCR und Bearbeitung sind nicht
enthalten. Import-/Delete-/Maintenanceprotokolle und gespeicherte Bytes bleiben
unverändert. Keine Dependency, Permission, zweite Activity oder Schlüsselhierarchie.

## Autorisierter Read-Only-Port

`DocumentRepository.readImage` erhält Action-ID, vollständigen keyfreien
DocumentAsset-Expected-Snapshot, einen widerrufbaren RAM-Auftrag und den internen
Android-Decoder. Ergebnisse sind ausschließlich ein schließbarer Anzeigeresult
oder bestehende neutrale DocumentFailure-Werte. Der Kompatibilitätsdefault für
bestehende Test-/Demoports verweigert Lesen; die produktive Implementierung
verwendet ausschließlich den neuen autorisierten Data-Pfad.

`ProductDocumentRepository` übernimmt READY-Context, OPERATION/STEP/Generation-
und PUBLICATION-Gates und die vorhandenen Worker-/Main-Executors. Keine Arbeit
an SQLCipher, Datei oder Decoder auf dem UI-Thread. Der Auftrag kann Berechtigung
nur einschränken, niemals erzeugen. Kein Reauth, keine Sessioninteraktion durch
Callbacks, keine Verlängerung von 180 s absolut oder 30 s READY-Inaktivität.

`DocumentDao.imageSnapshot` läuft innerhalb einer expliziten kurzen
`withReadTransaction`, nicht einer `@Transaction`-Write-Transaktion. Geprüft
werden persistierter Actionstatus (PREPARATION/ACTIVE/COMPLETED), exakte Auswahl,
Actionzugehörigkeit, Format JPEG/PNG, technische Bindungen, Jobs, Kapazität und
32-Byte-Key. Ein Delete-Job verbirgt das Dokument bereits vor Parentdelete.
DRAFT/DELETION_PENDING und veraltete/fehlende Auswahlen werden abgelehnt. Nach
bekannt unterbrochenem Import derselben Generation wird Maintenance verlangt.

Der interne Snapshot enthält ausschließlich während des Lesens benötigte Blob-
und Keybindungen plus überprüftes Inventar. Andere materialisierte Rowkeys werden
im DAO gewischt. Auch bei Fehler am Ende von Room-/Key-Lease-Close wird der bereits
erzeugte Snapshot im Repository-finally geschlossen. Keine Row/Key/Blobpfade
über Domain-/UI-Projektionen. Room, Driver und DB-Key-Lease sind vollständig
geschlossen, bevor Datei-/Decoderarbeit beginnt. Keine schreibende DAO-Operation
im Viewerpfad, kein neues Schema oder Migration.

## Namespace, Verifikation und Schlüssel

Lesen, Import, Delete und Maintenance teilen `DocumentNamespaceLane`; ein Reader
hält dieselbe exklusive Lane über Snapshot, Datei und Decoder. Maintenance drained
und fenced alte Tickets. Keine konkurrierende zweite Dateilane.

`DocumentFileInventory` verwendet einen gemeinsamen read-only Open-/Identitätspfad
für bestehende Verifikation und Viewer. Root/Kindpfade kanonisch, opaque UUIDs,
reguläre Dateien, NOFOLLOW, geprüftes Inventar vor/nach Lesen und unveränderte
Dateiidentität/-größe/-mtime. Unbekannte Dateien, Symlinks oder fehlende Pflicht-
blobs werden nicht repariert oder gelöscht. Bekannte DELETE_FILE_PENDING-Ziele
dürfen entsprechend dem bestehenden Vertrag optional fehlen.

`DocumentFileStreams.readImage` verwendet den bestehenden Verifikationsloop und
unveränderten FileStreamingCipher (Tink AES256_GCM_HKDF_1MB). AAD ausschließlich
opaque documentId UTF-8. Alle Segmente, echtes authentifiziertes EOF, tatsächliche
Länge und vorhandene JPEG-/PNG-Signaturprüfung müssen vollständig erfolgreich
sein, bevor irgendein Decoder Klartext erhält. Ausnahmen oder Nullreads sind kein
EOF. Falsche AAD/Keys, Truncation und spätes Tagversagen geben keine Bildbytes frei.

Kontrollierte File-DEK-Kopien werden bestmöglich gewischt, insbesondere vor dem
Decoder. Die SQLCipher-at-rest- und bestehende Keyhierarchie bleibt unverändert.
Die vorübergehend gesammelten Bildbytes sind keine öffentliche Inhalts-API;
der Decoder borgt sie synchron, darf sie nicht behalten und Data wischt sie im
finally. Keine Klartextdateien, Disk-/Thumbnailcaches, Provider oder externe App.

## RAM-/Decodergrenzen und Orientierung

Der tatsächliche komprimierte Klartext bleibt auf 25 MiB begrenzt. Es gibt genau
einen vorab auf validierte erwartete Länge begrenzten ByteArray plus feste
32-KiB-Streamingbuffer. Keine unbeschränkte ByteArrayOutputStream-Akkumulation
im Produktpfad. Fehler bei Verifikation/Inventarabschluss wischen den Ergebnisbuffer.

Der Android-Decoder liest zuerst ausschließlich Bounds (`inJustDecodeBounds`).
Quellmaße müssen positiv, pro Achse maximal 65.535 und insgesamt maximal
268.435.456 Pixel sein. Long-/Math.multiplyExact-Berechnungen und Zweierpotenz-
Downsampling mit aufgerundeten Zielmaßen begrenzen die Anzeigebitmap auf
**2.097.152 Pixel / 8.388.608 Bytes**. ARGB_8888, Software, sRGB, kein Dichteskalieren.
Zusätzlich beträgt jede Anzeigekante maximal 2.048 Pixel; auch bei geringer
Gesamtpixelzahl werden extreme Panoramen vor der Allokation weiter verkleinert.
Auch das tatsächliche Decoderergebnis wird gegen Config, Pixel und
allocationByteCount geprüft. Native Decoder können darüber hinaus interne
Arbeitsbuffer verwenden; keine Behauptung einer vollständigen JVM-/HAL-RAM-Grenze.

EXIF-Orientierungen einschließlich Spiegelung werden auf die RAM-Anzeigebitmap
angewendet. Bei 90/270 Grad tauschen sich die Bildmaße. Ein neuer Transformations-
Bitmap darf vor Freigabe des alten Puffers vorübergehend weitere 8 MiB benötigen:
maximal 16 MiB kontrollierter Pixelspeicher für diesen Decode-/Transformations-
schritt. Das Original wird nie neu codiert oder verändert. PNG wird unverzerrt
angezeigt. Kein EXIF-Tracking oder zusätzliche Metadatenpersistenz.

Grundlagen: [BitmapFactory.Options](https://developer.android.com/reference/android/graphics/BitmapFactory.Options),
[Bitmap-Lebensdauer](https://developer.android.com/reference/android/graphics/Bitmap),
[begrenztes Laden großer Bilder](https://developer.android.com/topic/performance/graphics/load-bitmap).

Authentischer, aber nicht unterstützter/zu großer Bildinhalt ergibt
„Dieses Bild ist für die sichere Anzeige zu groß oder wird nicht unterstützt.“
Er bleibt gespeichert. Decoderfehler werden nicht pauschal als DB-Korruption
behandelt. Authentifizierungs-/Inventar-/unerwartete Roomfehler bleiben Storage-
Fehler im bestehenden sicheren BLOCKED-Pfad. Keine automatische Reparatur/Retry.

## UI und Lifecycle

Nur JPEG/PNG erhalten „Bild ansehen“, erst in der bewusst geöffneten Liste.
Hub/Summary/List lesen keine Inhalte. PDF/Text zeigen weiterhin einen neutralen
Hinweis auf fehlende Vorschau. COMPLETED behält den bewussten Einstieg ohne
Prefetch. Keine neue globale Navigation.

Viewer: Name, Ladezustand, unverzerrte vollständige Fit-Ansicht und „Zur
Dokumentenliste“. Grundfunktion ohne Zoom. Standard-Formbuttons/Headingsemantik,
scrollbarer bestehender Workspace. Keine automatische Vorschau beim Listenopen.

`DocumentModel.clearImage` cancelt den Auftrag, entfernt Bitmap-/Auswahl-/Lade-
referenzen. `clear`, Bereichs-/Aktions-/Statuswechsel und bestehende Session-
Cleanups verwenden dies; MainActivity zusätzlich explizit bei Pause/Destroy.
Neue Anfrage cancelt die alte. Nach Lock/BLOCKED/MAINTENANCE kein Autoopen; neues
bewusstes Öffnen erforderlich. Verspätete Ergebnisse werden geschlossen.

Abbruch ist kooperativ: Gates zwischen Streamblöcken, vor/nach Decoderarbeit
und unmittelbar vor Publication. Ein laufender nativer Decode ist nicht mitten
im Aufruf unterbrechbar, sein begrenztes Ergebnis wird bei Revoke verworfen.
Streams werden im Worker-finally geschlossen. Die Session wartet nicht auf
Bildarbeit unter einem Sessionmonitor.

Nie zur Darstellung übergebene Ergebnisse und Zwischenbitmaps werden beim
Schließen sofort recycelt. `forDisplay` markiert die Übergabe an Compose unter
einem kurzen Puffermonitor; Close kann daher nicht parallel denselben Bitmap
recyceln. Bei schon gerenderten
Bitmaps werden Referenzen entfernt statt `recycle()` parallel zum Renderthread
aufzurufen. Compose-/GPU-/Decoderreferenzen können bis zur Plattformfreigabe
existieren; keine Garantie physischer RAM-Löschung. Kein SavedState/Bundle,
Preferences, persistente Registry oder Bitmapcache. FLAG_SECURE unverändert.

## Tests und tatsächliche Abnahme

**Bestanden: 104 fokussierte Hosttests** (69 App, 35 Data). Davon 27 neue
Viewertests: DocumentImageModelTest 13, DocumentImageReadTest 5,
DocumentImageDispatchTest 9. Die übrigen 77 betreffen die direkt berührten
Dokumenten-/Kamera-/Streaming-/Inventarregressionen. Die Appgruppe wurde nach
der Bitmap-Lebensdauerkorrektur erneut erfolgreich ausgeführt; Data blieb danach
unverändert. Die fokussierten Hosttests umfassen Reader-Authentifizierung, Tag-/AAD-/Key-/
Längenfehler, EOF, Streamclose, Bufferwipe, unbekannte Dateien/Symlinks,
Session-/Request-/Publication-Revoke, Lane-Fence, erlaubte Formate/Status,
Auswahlbindung, verspätete UI-Ergebnisse und Cleanup sowie unmittelbar betroffene
Dokumenten-/Kameraregressionen. Produkt-Debug-/Demo-Build, beide Manifestprüfungen
und App-/Data-AndroidTest-Kompilierung bestanden. Keine historische Gesamtsuite,
keine pauschale Lint-/Instrumentation-Gesamtabnahme behauptet.

Androidtests laufen ausschließlich in getrennten Paketen mit eigenen synthetischen
Beständen. Decoderprobe `org.pag.image.p10c1probe`: echte JPEG/PNG-Decodierung,
EXIF-90-Grad-Drehung, 4000×3000-Downsampling, ungültiger Inhalt, Revokecheck,
8192×16-Panorama mit begrenzten Anzeigekanten,
20 Decoder-Öffnen/Schließen mit synthetischem 1024×1024-PNG, sichtbare Bitmap mit DocumentModel-LOCKED-Cleanup und
spätem Ergebnis, Prüfung kontrollierter Verzeichnisse auf Klartextbildsignaturen.
Die Probe nutzt einen Fakeport; sie ist allein kein SQLCipher-E2E-Nachweis.
Data-Testpaket `org.pag.data.unlockprobe`: echtes SQLCipher/Room, bestehender
Tink-Staging-/Publicationpfad, Read-Only-Snapshot, erneutes Öffnen, unveränderter
Ciphertext, Delete-Sichtbarkeit, Stale/Wrong-Action/Non-Image, Manipulation und
Revoke nach Verifikation. Die manuelle Produktabnahme mit Nutzer-Credentials
ist getrennt unten dokumentiert. Keine automatisierten Tests gegen Produktdaten.

**Tatsächlich auf Samsung SM-M526BR / API 33 ausgeführt:** finale Decoder-/UI-
Probe PASS; vier DocumentImageStorageTest-Methoden PASS, Laufzeit 101,266 s.
Zusätzlich bestand die fünfte Methode für Widerruf während tatsächlicher
Ciphertext-Dateilesung mit Streamclose und ohne Klartextfreigabe (14,076 s).
Der Data-Snapshot läuft dabei explizit in `withReadTransaction`, ebenso im
Produktrepository. Eine versehentliche Write-Transaction-Annotation wurde vor
diesem finalen Lauf entfernt; keine bestehende Gateentscheidung geändert.

Der neue Speichertrendtest hat zunächst einen realen Nacharbeitsbedarf gezeigt:
Referenzentfernung allein hielt bei schnell aufeinanderfolgenden ungerenderten
Decoderresultaten die gemessene Heapzunahme nicht unter der Testschwelle. Die
Produktkorrektur recycelt nun sicher nie gerenderte Ergebnisse unmittelbar.
Der unveränderte Trendcheck (20 × 1-Megapixel, JVM plus Native Heap, nach Test-GC
maximal 32 MiB Zunahme) und alle übrigen Probeprüfungen sind danach bestanden.
Diese Schwelle ist keine allgemeine Heap-/GPU-Garantie und kein Nachweis über
beliebig viele tatsächliche Renderzyklen. Bereits dargestellte Bitmaps werden
weiterhin ohne unsicheres Renderthread-Recycling freigegeben.

Die erste Data-Fixture wurde korrekt abgewiesen, weil Creates unter READY
verboten ist. Korrigiert wurde ausschließlich die synthetische Initialisierung
auf MAINTENANCE, nicht das Produktgate. Ein erster Compilefehler im Probe-
Resultcode wurde ebenfalls im Testcode korrigiert. Keine Tests abgeschwächt.

## Bestätigte manuelle Produktabnahme

Der Benutzer hat auf Samsung SM-M526BR / Android API 33 ausdrücklich bestätigt:

1. Ein vorhandenes JPEG wird korrekt angezeigt.
2. Ein vorhandenes PNG wird korrekt angezeigt.
3. Nach Sperren und erneutem Entsperren öffnet sich kein Bild automatisch.
   Bewusstes erneutes Öffnen funktioniert.
4. Direkter Kameraimport und bisheriger SAF-Dateiupload funktionieren weiterhin,
   jeweils einschließlich anschließender Bildanzeige.

Damit ist die funktionale manuelle Geräteabnahme bestanden. Diese Ergebnisse
sind Benutzerbestätigungen und keine zusätzlichen automatisierten Testläufe.
Die bereits oben dokumentierten Hosttests, isolierten Androidtests und Builds
wurden im reinen Abschlussauftrag nicht erneut ausgeführt. Es wurden nur
Dokumentation, bestehende Ergebnisprotokolle, Quellcode und Git-Diff geprüft.

## Performance — bekannter offener Punkt

**Der Benutzer empfindet das Öffnen der Bilder als langsam.** Dieser Befund ist
nicht behoben. Die Bildanzeige erhält ausdrücklich keine Performancefreigabe;
Ursachenmessung und Optimierung gehören in den späteren dedizierten
Performance-Featureblock. Es liegen keine belastbaren produktiven
Millisekundenmessungen pro Bild oder nach Teilphase vor. Die vorhandenen
Android-Testlaufzeiten (101,266 s / 14,076 s) enthalten Testaufbau, Import und
mehrere DB-Schritte; sie sind keine Messwerte für die Öffnungsdauer eines Bildes.

Der vorhandene Code ergibt folgende Untersuchungspunkte. Dass diese Schritte
ausgeführt werden, ist durch Codeprüfung belegt; ihre jeweiligen Zeitanteile
und ihr Beitrag zum langsamen Öffnen sind **Hypothesen, keine bewiesenen Ursachen**:

| Bereich | Vorhandener Ablauf / Hypothese | Nachweis und Messbedarf |
| --- | --- | --- |
| A — SQLCipher-Datenbanköffnung | `ActionDatabaseAccess.use` öffnet zunächst den nativen Preflightbestand und danach Room mit bestehendem Treiber. Wiederholtes Öffnen könnte Kosten verursachen. | Ablauf geprüft; isolierte Öffnungs-/Schließzeiten fehlen. |
| B — `cipher_integrity_check` | Der bestehende Preflight führt die SQLCipher-Integritätsprüfung aus. Ihr Anteil könnte von DB-Größe und Gerät abhängen. | Aufruf geprüft; kein separater Zeitwert. |
| C — SQLite `integrity_check` | Die SQLite-Integritätsprüfung bleibt Bestandteil desselben Preflights; zusätzlich läuft `foreign_key_check`. | Aufrufe geprüft; Einzelzeiten fehlen. |
| D — Dokument-Snapshot und Inventar | Kurze Read-Only-Transaktion prüft globale Bindungen/Kapazität; Dateiinventar und Identitäten werden vor/nach Lesen geprüft. | Ablauf geprüft; DB-Snapshot- und Inventaranteile noch nicht gemessen. |
| E — Tink und vollständige Dateiverifikation | Der gesamte Blob wird streamend entschlüsselt, authentifiziert und auf EOF, Größe und Format geprüft. Datei-I/O und Tink-Arbeit sind verschachtelt. | Korrektheit getestet; produktive kombinierte Read-/Auth-Zeit fehlt. |
| F — JPEG-/PNG-Decoding | Bounds/EXIF, Downsampling, Softwaredecoding und gegebenenfalls Orientierungstransformation folgen auf die vollständige Verifikation. | Android-Funktionstests bestanden; produktive Decoderzeit fehlt. |
| G — Bitmap-Publication und UI | Nach Workerarbeit folgen Main-Executor, Publication-Gates, Compose-Zustandsänderung und Rendering. | Funktion und Revoke getestet; Queue-, Publication- und tatsächliche Framezeit fehlen. |

Insbesondere darf die vorhandene `DB_SNAPSHOT`-Zeitgrenze nicht mit einer reinen
DAO-Abfrage gleichgesetzt werden: Sie umfasst hier DB-Open, Preflight,
Read-Transaktion und Ressourcenabschluss. `TOTAL` endet beim UI-Callback und
belegt allein noch nicht den ersten tatsächlich dargestellten Frame. Für eine
spätere belastbare Zuordnung sind getrennte, datensparsame Messungen erforderlich.

Debugdiagnose `PaGImageTiming` schreibt ausschließlich feste Phasen und
Millisekunden: WORKER_START, DB_SNAPSHOT, READ_AND_AUTH, DECODE, TOTAL, CLEANUP.
Streaming-Read und Tink-Verifikation sind ineinander verschachtelt und werden
bewusst gemeinsam gemessen; keine erfundene isolierte Cipherzeit. Kein Dateiname,
ID, Pfad, Bild, Key oder Exceptiontext. Keine Freigabe-/Sessionentscheidung
hängt von Timing ab. Temporäre Geräte-Logtagfreigabe wird am Ende zurückgesetzt.
Die vorübergehende Freigabe wurde auf den ursprünglichen leeren Wert
zurückgesetzt. Im Abschlussauftrag wurden keine neuen Diagnosen aktiviert oder
Produktzeitwerte erhoben. Keine erfundenen Millisekunden oder Performanceabnahme.

Die automatisierten Geräteergebnisse stehen oben. Der finale Produktbuild wurde
nach Vergleich identischer Signatur ausschließlich per `install -r` aktualisiert;
keine Produktdaten gelöscht/ersetzt, keine Credentials geändert. Im Abschlussauftrag
keine erneute Installation oder Änderung des Produktbestands. Die funktionale
Samsung-Abnahme ist bestätigt; die Performance bleibt offen. Menschliche TalkBack-Abnahme, andere OEMs,
alle EXIF-Spiegelvarianten auf echtem Gerät und vollständige native Decoder-
Speicherinstrumentierung sind nicht pauschal nachgewiesen. Keine Garantie
physischer RAM-Löschung oder eines fehlerfreien Plattformdecoders. Der große
Dokumentations-Gesamtcheck aus Issue #26 bleibt separat.

## Vollständiger lokaler Änderungsumfang

- `app/src/androidTest/AndroidManifest.xml`
- `app/src/androidTest/kotlin/org/pag/prototype/P10c1ImageProbe.kt`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentImageDecoder.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentImageModelTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/DocumentImageStorageTest.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileStreams.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentImageRead.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductDocumentRepository.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImageDispatchTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentImageReadTest.kt`
- `docs/product-p10a-documents.md`
- `docs/product-p10c1-secure-image-viewer.md`
- `docs/troubleshooting-documents.md`
- `domain/src/main/kotlin/org/pag/domain/Documents.kt`

Keine Änderung von Schema 11, Migrationen, Auth-/Sessionarchitektur,
Permissions, Produktionsmanifest, Dependencies oder bestehenden Import-/
Delete-/Reconciliation-Koordinatoren. Der finale Abschlussauftrag autorisiert
Commit und Branch-Push für den unabhängigen GitHub-Code-Review. Kein Merge,
kein Release und keine Performanceoptimierung in diesem Auftrag.

**Abschließende Bewertung: FUNKTIONAL GRÜN, PERFORMANCE OFFEN.**
