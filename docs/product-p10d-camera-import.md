# P10d — direkter, geschützter Kameraimport

## Produkt- und Securityentscheidung

Baseline: `main`, `61596414f7bbe7d5e5768dd9bc8b961232ee25cc`, Release
v0.1.0-alpha.2; sauberer Ausgangsbaum. Arbeitsbranch
`p10d-direct-camera-import`. Schema bleibt **11**.

P10d erweitert ausdrücklich die historische Kamera-Scopegrenze von
[P10a](product-p10a-documents.md). Zusätzlich zum unveränderten SAF-Import darf
in PREPARATION, ACTIVE und nach bewusstem Einstieg COMPLETED eine interne
JPEG-Aufnahme erstellt und als normales aktionsgebundenes DocumentAsset
verschlüsselt gespeichert werden. DRAFT/DELETION_PENDING bleiben gesperrt.
Keine allgemeine COMPLETED-Schreibfreigabe, P3b-Öffnung oder Personenrelation.

Neue Permission ausschließlich **CAMERA**, erst auf „Foto aufnehmen“.
Kein Mikrofon, Standort, Galerie-/Storagezugriff, INTERNET oder Netzwerkstatus.
Kamera-Features sind optional (`required=false`). Keine Kamera im Hintergrund,
kein Foreground Service oder externer Kameraprovider. Der bestehende
FLAG_SECURE-/Backup-/Sessionvertrag bleibt bestehen.

Risiko: Kamera kann die unmittelbar sichtbare Umgebung erfassen. Deshalb
bewusster Einstieg, sichtbare Livevorschau, ausdrücklicher Auslöser und zwei
Bestätigungsstufen vor Persistenz. Für Tests ausschließlich bewusst gewählte
unkritische Szenen, keine automatischen privaten Fotos.

## CameraX und Dependencyreview

Verwendet werden ausschließlich stabile Artefakte **1.6.2**:
`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`.
[AndroidX-Stableübersicht](https://developer.android.com/jetpack/androidx/versions)
und [ImageCapture-Memory-API](https://developer.android.com/reference/androidx/camera/core/ImageCapture)
bilden die öffentlichen API-Grundlagen. Keine eigene Camera2-State-Machine,
experimentellen CameraX-Versionen oder neuen Kryptoprimitiven.

Die offiziellen 1.6.2-Sources und aufgelösten POMs wurden geprüft. CameraX nutzt
Apache-2.0, camera-core nennt zusätzlich BSD-3-Clause. Transitiv kommen u. a.
camera-camera2-pipe, camera-video über camera-view, AndroidX-EXIF/Lifecycle,
Guava, Dagger/Jakarta Inject und AtomicFU hinzu. Keine Video-/Audiofunktion
wird aufgerufen oder freigegeben. Der zusätzliche transitive
ACCESS_NETWORK_STATE-Manifestbeitrag wird explizit entfernt. Die strikte
Manifestprüfung akzeptiert nur CAMERA zusätzlich zur bestehenden Permissionliste.

`PagApplication` liefert passiv `Camera2Config` mit Minimum-Logging ERROR.
CameraX wird erst auf bewussten Kameraeinstieg angefordert. Der metadata-only
CameraX-Service wird entfernt; es entsteht kein neuer Service-/Providerpfad.
Demo erhält keine CAMERA-Permission oder Kamerafunktion.

Die Sourceprüfung unterscheidet `ProcessingNode.processInMemoryCapture` von
`processOnDiskCapture`: Der gewählte OnImageCapturedCallback-Pfad verwendet
keine OutputFileOptions und erreicht nicht JpegBytes2Disk/DngImage2Disk/FileUtil,
in denen temporäre Dateien angelegt werden. CameraX kann intern RAM-Konvertierung
für Gerätequirks durchführen; die App setzt keine Effekte und keinen ViewPort-Crop.
Keine Behauptung über physische Löschung interner Plattform-/Herstellerbuffer.

## Technische Architektur und Ablauf

- `DocumentCameraModel`: RAM-Unterzustand im bestehenden DocumentModel, keine
  parallele Navigation, persistente Drafts oder zusätzliche Sessionlogik.
- `DocumentCameraController`: PreviewView/Preview und ImageCapture, bestehender
  Activity-LifecycleOwner; primär rückwärtige Kamera. Geeignete Kamera fehlt:
  neutraler Fehler, SAF bleibt nutzbar.
- `CameraPhoto`: genau ein kontrollierter ByteArray-Puffer und einmaliger
  DocumentSource. Keine URI, Bitmap, Datei oder Persistenz im Modell.
- `CameraJpegMetadata`: begrenzte Header-/EXIF-Leseprüfung im RAM, keine
  saveAttributes-/Datei-/Neukodierung.

Ablauf: Dokumente → Foto aufnehmen → Livevorschau → Auslöser → Aufnahme
übernehmen / Erneut aufnehmen / Abbrechen → Dokumentname → Verschlüsselt
speichern → vorhandene Liste. Bestätigung der Aufnahme zeigt JPEG/Größe und
Speicherstatus, keine decodierte Foto-/Dokumentenvorschau. Format ist fest JPEG;
keine Formatliste. Vor ausdrücklichem Speichern kein Repositorycall.

Die bestehende Produktform stellt Überschriften, benannte Buttons und mindestens
56-dp-Formbuttons bereit. Der Kamerabereich bleibt scrollbar; Namensformular mit
IME-Padding. Keine allein farbliche Zustandskennzeichnung oder globale Navigation.

## Berechtigung und Lebensdauer

Der erste Buttondruck prüft CAMERA und fordert sie bei Bedarf über Android an.
Es gibt keine Anfrage beim Appstart oder bloßem Öffnen der Dokumentenliste.
Berechtigungsantwort startet keine Kamera automatisch. Falls Androids Dialog
PaG sperrt: regulär authentifizieren, Aktion/Dokumente bewusst neu öffnen und
„Foto aufnehmen“ erneut antippen. Bei dauerhafter Ablehnung verständlicher
Hinweis auf Android-App-Einstellungen; kein automatischer Settings-/Retrypfad.

Previewbindung erst bei gültigem READY, exakter Aktion/Phase, aktuellem Epoch,
RESUMED-Lifecycle und aktueller Permission. Provider-Future und Captureergebnisse
werden nach Verlassen/Lock/Generationwechsel verworfen. CameraX-Callbacks,
Orientierungssensor, Dateiarbeit und Diagnosetimer zählen nicht als Interaction.
180 Sekunden absolut / 30 Sekunden READY-Inaktivität bleiben unverändert.

Capture-Callbacks verwenden den langlebigen Main-Executor statt eines beim
Navigieren abgeschalteten Workerexecutors: Auch ein verspäteter Proxy kann
zuverlässig geschlossen werden. Auf dem Mainthread erfolgen nur begrenzte
RAM-Kopie und Metadaten-/Boundsprüfung, keine DB-/Dateiarbeit oder volle
Bitmapdekodierung. Proxies werden auf jedem Rückweg geschlossen. Captureticket
lässt nur ein Resultat zu; schneller Doppeltap startet keine zweite Aufnahme.

Beim Lock, ON_PAUSE/STOP/DESTROY, Bereichs-/Aktions-/Statuswechsel oder Abbruch:
Ticket invalidieren; ausschließlich eigene UseCases unbinden; Sensor und
LiveData-/Lifecyclebeobachter entfernen; SurfaceProvider lösen und Preview-
TextureView entfernen; eigene Bytes überschreiben und Referenzen entfernen.
Benennungsentwurf wird bei Abbruch/Retake/Workspace-Cleanup ebenfalls entfernt.
CameraX kann die Hardware asynchron schließen; Androidtests beobachten dafür
aktiv den gebundenen CameraState bis CLOSED, nicht veraltete inaktive LiveData.

## JPEG-, EXIF- und RAM-Grenzen

Zielauflösung 1600×1200 mit begrenzter CameraX-ResolutionStrategy; keine Zusage,
dass jeder Hersteller exakt diese Auflösung liefert. Vor ByteArray-Anlage:
JPEG-Format und tatsächliche Plane-Restgröße prüfen; höchstens 25 MiB.
32-KiB-Kopierschritte, kein wachsender ByteArrayOutputStream im Produktpfad.
SOI-/EOI-Prüfung, Bounds-only-Decoder (kein Bitmap) und maximal 16 Mio. Pixel
als zusätzliche Kameragrenze. Ungültige/überlange Ausgabe wird komplett verworfen.
Keine vollständige JPEG-Entropy-/Malware-/inhaltliche Echtheitsprüfung behauptet.

Die JPEG-Bytes bleiben unverändert. OrientationEventListener setzt die
Capture-Targetrotation; EXIF-Orientierung muss zu ImageInfo.rotationDegrees passen.
Fehlend/UNDEFINED entspricht nur 0 Grad; andere nicht eindeutige oder gespiegelte
Orientierungen werden abgewiesen. Keine stille Seitwärtsaufnahme oder vollständige
Bildneukodierung zur Korrektur. Größen-/Parser-/Proxy-Closefehler wischen die
kontrollierte Kopie bestmöglich und schließen den Proxy.

Keine Standortpermission, kein Standortparameter an CameraX. Unerwartete
GPS-Koordinatentags im erhaltenen JPEG führen zur Ablehnung. Sonstige EXIF-Inhalte
(z. B. Geräte-/Zeit-/Belichtungsinformationen) können eingebettet bleiben; keine
Behauptung vollständiger Metadatenentfernung und keine eigene Metadatenpersistenz.
Der tatsächliche Samsung-Capture wird auf fehlende GPS-Koordinaten geprüft.

Lock/Abbruch kann auch einen bereits geöffneten CameraPhoto-Stream widerrufen.
RAM-Synchronisation erfolgt ohne Session-/DB-/Kameraufrufe unter dem Puffermonitor.
Kontrollierte eigene Bytes werden bestmöglich überschrieben; keine Garantie für
physische RAM-Löschung in Android/JVM/native CameraX-/HAL-Buffern.

## Unveränderte geschützte Importpipeline

Nach Namensprüfung und explizitem Speichern:
ProductDocumentRepository.importDocument → DocumentImportStaging.stage →
DocumentFileStreams.encrypt → DocumentPublication.complete. Die Source öffnet
höchstens einmal, ausschließlich im autorisierten Worker nach dauerhafter
IMPORT_WRITING-Transition. Keine Schlüssel oder Storagekontexte in Kamera-
Callbacks, keine zweite DB/DEK-/Keyhierarchie oder Verschlüsselung.

SQLCipher, Tink, 25 MiB / 100 Dokumente / 250 MiB, COMMIT-/Publication-Gates,
No-Overwrite-Publication und Maintenance bleiben unverändert. Keine Roominstanz
über File-I/O. Erfolg erst nach finalem Dokumentcommit und gültigem UI-Request.
Ein nicht mehr gültiger Callback aktualisiert weder Liste noch Feedback.

Vor Save kann Prozessende nur die RAM-Aufnahme verlieren. Während Import gelten
bestehende Job-/Crashzustände: unterbrochenes WRITING wird beim nächsten echten
Unlock bereinigt; CLOSED/PUBLISHED werden vollständig geprüft fortgesetzt.
Kein blindes Retry der Kamera-Source, kein falsches Rollbackversprechen nach
bereits erfolgtem Commit. Bestehende Delete-/Reconciliationpfade bleiben zuständig.

## Prüfungen und Performance

Am 16.10.2026 bestanden 55 fokussierte Hosttests (CameraPhoto 12,
DocumentCameraModel 17, DocumentModel 18, DocumentWorkspace 8). Anschließend
wurde der zusätzliche Release-Fehlerfall ergänzt und die nun 18 Tests der
DocumentCameraModel-Klasse erneut erfolgreich ausgeführt: insgesamt 56 relevante
Tests. Abgesichert sind RAM-Bounds/Zeroing, einmaliges Öffnen, Stream-Revoke,
ungültige/zu späte/doppelte Ergebnisse, Orientierung, Abbruch/Retake,
Berechtigungsantwort ohne Autostart und bewusste Importbestätigung. Wirft die
Kamerafreigabe nach dem Capture, wird auch die noch nicht übergebene Aufnahme
verworfen. Produkt-Debug-/Demo-Build und beide Manifestprüfungen bestanden;
AndroidTest-Quellen wurden erfolgreich kompiliert.

Separater Android-Testbuild `org.pag.camera.p10dprobe` mit eigenem Testpaket;
keine automatischen Tests gegen die Produktdatenbank. Eine explizit von den
Nutzenden freigegebene unkritische Testszene ist vor echter Captureausführung
Voraussetzung. Probe prüft echte CameraX-Vorschau/JPEG/Proxyclose/GPS-Abwesenheit,
Bounds, sichtbaren Auslöser und Ressourcenfreigabe. Sein Repository ist synthetisch;
er ist kein automatischer SQLCipher-End-to-End-Nachweis. Reale Storage-Nachweise
und manueller Credential-/Kamera-/Import-/Persistenz-/Löschablauf werden separat
als solche berichtet. Keine erfundenen Androidnachweise.

Auf Samsung SM-M526BR / API 33 bestanden beide realen CameraX-Probeläufe mit
Font Scale 1 und 2. Jeweils: Vorschau, zugänglicher Auslöser (mindestens 48 dp),
JPEG-Aufnahme, EXIF-/GPS-Prüfung, Proxy-Close, einmaliger Quellkonsum,
keine JPEG-Datei im privaten Testbestand, Freigabe nach Lock und Verwerfen eines
späten Ergebnisses. Der Test hält eine eigene CameraState-Beobachtung bis CLOSED;
ohne aktiven Beobachter kann LiveData einen alten Zustand liefern. Der
Accessibility-Klick sucht bei Textknoten den tatsächlich klickbaren Elternknoten.
Diese beiden Testkorrekturen ändern keine Produktfreigaben.

Vier gezielte bestehende SQLCipher-/Room-Tests liefen separat unter
`org.pag.data.unlockprobe`: vier Formate durch reale Koordinatoren einschließlich
Persistenz/Löschung; Revoke nach Dateiumzug mit neuer Maintenance; reservierte
und bereits physisch gelöschte Delete-Recovery; konkurrierendes Ziel kann nicht
überschrieben werden. Ergebnis **4/4**, Laufzeit 199,622 s. Kein automatisierter
Test öffnete die Produktdatenbank.

Der Nutzer bestätigte den echten Produktweg Aufnahme → Übernehmen → Name →
verschlüsselt speichern → Dokumentenliste. Installiert wurde ausschließlich
per Update nach Vergleich identischer Signatur; keine Produktdaten gelöscht,
ersetzt oder zurückgesetzt. Normale Credentials wurden ausschließlich vom
Nutzer eingegeben.

Gemessener Produktdurchlauf (ein Messpunkt, keine Benchmarkgarantie):

| Schritt | Zeit |
| --- | ---: |
| Kamera bis STREAMING | 631 ms |
| Auslöser bis Capture-Start | 212 ms |
| JPEG-Übernahme/Prüfung im Callback | 2 ms |
| Auslöser bis JPEG bereit | 401 ms |
| Speicherbestätigung bis Importcommit-Callback | 11.361 ms |
| Speicherbestätigung bis Listenrefresh | 12.632 ms |

Die isolierten CameraX-Läufe benötigten 707/718 ms bis Vorschau und
670/679 ms bis abgeschlossener Aufnahme. Importzeiten enthalten bestehende
SQLCipher-/Verifikationsschritte; diese wurden nicht zur Beschleunigung verkürzt.

Debug-Performancewerte ausschließlich feste Phasen und Millisekunden:
PREVIEW_READY, SHUTTER, JPEG_READY, JPEG_COPY, IMPORT_COMMIT, IMPORT_LIST.
Keine Bilder, Namen, Pfade oder EXIF-Werte im App-Logging. Zeiten verwenden
System.nanoTime nur zur Messung, niemals zur Sessionfreigabe/-verlängerung.
Kein Benchmarkversprechen, kein Performanceumbau an Room/SQLCipher.

## Finale Geräteabnahme und verbleibende Grenzen

Der Nutzer hat auf Samsung SM-M526BR / Android API 33 zusätzlich alle drei
offenen manuellen Prüfungen ausdrücklich erfolgreich bestätigt:

1. Das Kamerafoto bleibt nach regulärem Sperren und erneutem Entsperren vorhanden.
2. Die bestätigte Löschung des Kamerafotos ist erfolgreich und dauerhaft;
   auch nach erneutem Entsperren bleibt es entfernt.
3. Der bestehende SAF-Dateiupload funktioniert weiterhin einschließlich
   Picker, regulärer Reauthentifizierung und bewusster Importfortsetzung.

Damit ist die funktionale Geräteabnahme des Kameraimports bestanden. Dies sind
manuelle Nutzendenbestätigungen, keine zusätzlichen automatisierten Testläufe.
Die oben aufgeführten bereits ausgeführten Tests und Builds wurden für die
reine Dokumentations-/Commitvorbereitung nicht erneut ausgeführt.

Menschliche TalkBack-Abnahme, andere OEMs und Frontkameras sind nicht pauschal
nachgewiesen. Hardwarefehler (belegte/fehlende Kamera, nachträglicher Widerruf)
sind neutral behandelt, aber nicht jeder Fehler wurde auf dem Samsung provoziert.
Keine universelle Crash-/Secure-Erase-Garantie.
Viewer, Kamera-Foregroundservice, Galerieautosave, Cloud, Scanner, OCR, Export,
Dateien-/Fotoanhänge an P6/P9 oder zweite Dokumentenverwaltung sind nicht enthalten.

Die temporäre Geräte-Logtagfreigabe `PaGCameraTiming` wurde nach der Messung
auf ihren ursprünglichen leeren Wert zurückgesetzt. Für normalen Kameraimport
sind weder Entwickleroptionen noch USB-Debugging oder Diagnoselogs erforderlich.
Der vorhandene Hinweis zu „Aktivitäten nicht speichern“ betrifft weiterhin den
SAF-RAM-Handoff. Er wird nicht durch eine Lockerung des App-Locks umgangen.

## Geänderte Dateien

- `PRODUCT.md`, `README.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/demo/AndroidManifest.xml` (nur Permission-/Feature-Ausschluss)
- `app/src/androidTest/AndroidManifest.xml`
- `app/src/main/kotlin/org/pag/prototype/PagApplication.kt`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/CameraJpegMetadata.kt`
- `app/src/main/kotlin/org/pag/prototype/product/CameraPhoto.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentCameraModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentCameraPreview.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt`
- `app/src/test/kotlin/org/pag/prototype/product/CameraPhotoTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentCameraModelTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentModelTest.kt`
- `app/src/androidTest/kotlin/org/pag/prototype/P10dCameraProbe.kt`
- `docs/privacy.md`, `docs/security.md`
- `docs/product-p10a-documents.md`
- `docs/troubleshooting-documents.md`
- `docs/product-p10d-camera-import.md`

Keine Änderungen an Domain-/Data-/Securityimplementierung, Schemaexport,
SQLCipher-Migrationen, SessionCoordinator, Sessionfristen oder Authverfahren.
