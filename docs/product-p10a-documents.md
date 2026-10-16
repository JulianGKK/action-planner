# P10a — Dokumente: Produkt-, Privacy- und Security-Vertrag

Gezielte spätere Viewerweiterentwicklung: [P10c1](product-p10c1-secure-image-viewer.md)
ergänzt ausschließlich JPEG-/PNG-Anzeige über einen vollständig authentifizierten,
begrenzten RAM-Pfad. PDF-/Textviewer und externe Klartextübergabe bleiben gesperrt.
Der folgende ursprüngliche P10a-Vertrag bleibt als historischer Ausgangspunkt erhalten.

> Gezielte spätere Erweiterung: [P10d](product-p10d-camera-import.md) erlaubt einen
> direkten internen JPEG-Kameraimport mit CAMERA-Runtimepermission und begrenztem
> RAM-Puffer. Die folgenden Kameraausschlüsse beschreiben den historischen
> P10a/P10b-Scope. Alle übrigen Schutzanforderungen bleiben gültig.


## 1. Status, Baseline und Verbindlichkeit

P10a ist ausschließlich Spezifikation/Dokumentation. Baseline: Branch
`p10a-documents-product-security`, sauberer Working Tree, HEAD `a3e7cab`
(P9b-Merge), ActionDatabase und Schemaexport **10**; P6/P7/P8/P9 vorhanden.
`git status -sb`, `git log -6 --oneline` und `git diff --check` ohne Abweichung.
Keine Implementierung, neue Tabelle, Migration, Schemanummer, Dependency,
Permission, Manifeständerung oder Kryptografieimplementierung durch P10a.

Grundlagen: [Produkt](../PRODUCT.md), [Architektur](architecture.md),
[Threat Model](threat-model.md), [Datenmodell](data-model.md),
[Privacy](privacy.md), [Security](security.md), [Retention](data-retention.md),
[Testing](testing.md), [UX-Philosophie](ux-philosophy.md),
[Roadmap](ux-roadmap.md), [P9a](product-p9a-memory-protocol.md) und
[P9b](product-p9b-memory-protocol-implementation.md).
Dieser Vertrag konkretisiert FR-050–054, FR-095, SEC-002/014/015/016,
INT-001–004 und PERF-003 für den späteren P10-MVP. Widersprüchliche ältere
allgemeine Dokumentenplanung wird für diesen Umfang ersetzt. Historische
Prototypnachweise sind keine Freigabe einer Produkt-Dateipipeline.

Sichtbar heißt der Bereich **„Dokumente“**, ein Objekt **„Dokument“**.
Späterer technischer Domainname vorzugsweise **DocumentAsset**. Keine Evidence-,
Beweis-, Exhibit- oder ForensicFile-Semantik.

## 2. Zweck und enge Fachgrenze

Relevante Dateien genau einer Aktion lokal verschlüsselt aufbewahren und bewusst
innerhalb PaG bereitstellen. Keine globale Dokumentbibliothek, Cloudablage,
Filesharing-, Beweismittel-, Scan-, Foto-, Editor- oder Officefunktion.
FR-060ff Informations-/Materialbibliothek bleibt ein späteres getrenntes Thema.

Jedes Dokument gehört genau einer Action. Keine Verknüpfung mit Person/P3b,
Task, Material, Kosten, P6-Eintrag, P9-Bericht oder Rolle/Funktion; keine
aktionsübergreifende Deduplizierung. Insbesondere keine Anhänge an P6/P9 und
kein Import aus deren Bereichen. Spätere Beziehungen benötigen eigene
Produkt-, Privacy- und Retentionentscheidungen.

## 3. Formate und harte Importgrenzen

| Format | Import im späteren P10b | Interne Anzeige |
| --- | --- | --- |
| PDF | Zulässig nach Prüfung | **Kein PDF-Viewer/Preview in P10b** |
| JPEG | Zulässig nach Prüfung | Nur nach sicherem RAM-/Decoder-Nachweis |
| PNG | Zulässig nach Prüfung | Nur nach sicherem RAM-/Decoder-Nachweis |
| UTF-8-Klartext | Zulässig nach Prüfung | Nur Plaintext mit begrenzter Viewerstrategie |

Keine DOC/DOCX, ODT, XLS/XLSX, PPT/PPTX, Archive/ZIP, HTML, SVG, GIF, WebP,
HEIC, Audio, Video, APK oder ausführbaren Dateien. Der begrenzte Umfang
reduziert Parser-/Angriffsfläche. Importfreigabe ist keine Viewerfreigabe.
Dateiendung und Provider-MIME sind untrusted; P10b legt eine kleine zusätzliche
Signatur-/Formatplausibilisierung einschließlich gültigem UTF-8 fest.
Keine allgemeine MIME-Sniffing-Bibliothek auf Vorrat. Plausibilisierung ist
keine Garantie harmloser oder authentischer Inhalte.

Verbindliche MVP-Grenzen:

- Maximal **100 Dokumente pro Aktion**.
- Maximal **25 MiB Klartextgröße pro Dokument** (26.214.400 Bytes).
- Maximal **250 MiB Klartextsumme pro Aktion** (262.144.000 Bytes).

Auch bei gemeldeter Größe zählt die tatsächliche gelesene Bytezahl. Bei
unbekannter Größe streamen und beim Überschreiten hart abbrechen. Keine stille
Teilübernahme, kein unnötiges vollständiges Laden großer Dateien in RAM.
P10b muss konkurrierende Imports und Zwischenzustände so berücksichtigen,
dass die Aktionsgrenzen nicht umgangen werden. Technische Reservierung und
Recovery sind noch festzulegen; keine zusätzlichen Fachfelder durch P10a.
Grenzänderungen benötigen einen eigenen Produktentscheid.

## 4. Fachmetadaten und Inhaltstreue

Genau sechs fachliche Angaben: **id, actionId, displayName, format,
plaintextSize, importedAt**. format stammt aus dem freigegebenen geprüften
Formatumfang; plaintextSize bezeichnet die tatsächlich importierte Klartextgröße.
importedAt ist appseitige Importzeit, keine externe Erstellungszeit oder
beglaubigter Zeitstempel. Die technische Ausgestaltung folgt erst P10b.

displayName: trim, Pflicht **1–160 Unicode-Codepoints**, keine stille Kürzung.
Provider-Anzeigename darf den Entwurf vorbelegen; danach ist es ein PaG-eigener,
umbenennbarer Dokumentname. Unzulässige Vorbelegung muss bewusst korrigiert
werden. Er ist kein Dateisystempfad.

Nicht zusätzlich dauerhaft speichern: Originalpfad, Quell-URI, Provider-Authority,
Quellordner, persistente URI-Permission, EXIF-/GPS-Auswertung, Beweis-Hash/
Fingerprint, Kategorie, Tags, Notiz, Beschreibung oder Autor:innenprofil.
Technisch notwendige geschützte Blob-/Key-/Recoveryreferenzen sind kein neues
Fachmetadatenmodell; P10b muss deren minimale Ausgestaltung separat begründen.

Die ausgewählten **Dateibytes bleiben unverändert**. Keine Bildneukodierung,
EXIF-Entfernung, PDF-Bereinigung, Textersetzung oder Metadatensanitisierung.
Eingebettete Metadaten können dadurch im verschlüsselten Inhalt verbleiben.
Keine automatische Extraktion, Indexierung oder Anzeige dieser Metadaten.

## 5. SAF und externe Originalquelle

Import über Android-System-Dateiauswahl/Storage Access Framework mit einmaligem
dokumentbezogenem Zugriff, bevorzugt OpenDocument/System Picker. Keine breite
Speicherpermission, MANAGE_EXTERNAL_STORAGE, READ_MEDIA_IMAGES,
READ_MEDIA_VIDEO, READ_EXTERNAL_STORAGE oder Dateisystemsuche.
PaG kopiert den ausgewählten Inhalt in eigenen geschützten Speicher; keine
Live-Dateiverknüpfung, Quellenbeobachtung, automatische Aktualisierung oder Sync.
Keine persistable URI permission und kein dauerhaft gespeicherter URI-Zugriff,
auch nicht als Recoveryhilfe. Nach erfolgreicher Kopie kein späteres Quell-Re-Read.

Verbindliche Importinformation:

> PaG erstellt eine geschützte Kopie. Die ursprüngliche Datei beim ausgewählten
> Anbieter bleibt bestehen und wird von PaG weder geschützt noch gelöscht.

Dies gilt für Downloads, öffentliche lokale Orte, Cloudprovider, USB und andere
DocumentProvider. PaG-Löschung betrifft ausschließlich die PaG-Kopie.
SAF kann durch einen Provider extern bereitgestellte Cloud-Dateien liefern.
PaG selbst bleibt ohne INTERNET-Permission und eigenen Netzwerkpfad; der
Android-/Providerprozess kann Netzwerk nutzen. Keine lokale Quelle versprechen,
keine automatische Synchronisation. Offline-Verfügbarkeit betrifft die erfolgreich
übernommene PaG-Kopie; Verfügbarkeit der Importquelle ist eine separate Grenze.

Picker-Rückkehr hebt ARCH-09/SEC-01 nicht auf: App-Verlassen sperrt, erneute
Freigabe benötigt reguläre Authentifizierung. P10b muss die sichere kurzlebige
Quellübergabe über diesen Ablauf nachweisen. Kein alter Session-/Workflowcallback
darf in einer neuen Generation automatisch importieren; falls kein zulässiger
Quellzugriff mehr besteht, bewusst neu auswählen. Kein blindes URI-Retry nach Reauth.

## 6. Sensitivität, Schlüssel und Speicherziel

Dokumentinhalt **und Dateiname sind standardmäßig SENSITIVE**: Identitäts-,
Rechts-, Gesundheits- und andere vertrauliche Informationen sind möglich.
Normale P10-Arbeit ist in gültigem READY ohne Leadership-Step-up vorgesehen.
Diese enge P10-Freigabe öffnet P3b nicht. Kein automatischer P3b- oder nötiger
P3a-Read. Offene READY-Sitzungen können sensible Inhalte offenlegen.

Ziel für P10b: Fachmetadaten im bestehenden SQLCipher-Produktbestand;
authentifiziert verschlüsselter Blob in app-privatem, vom Backup ausgeschlossenen
Speicher, bevorzugt No-Backup. Blobpfad ausschließlich zufällige/opaque technische
ID, niemals Original-/Anzeigename. Große Dateien über Streaming verarbeiten.
Etabliertes Streaming-AEAD, bevorzugt bestehendes Tink, **sofern** sauber in die
vorhandene geschützte Keyhierarchie integrierbar. Keine eigene Kryptografie.

Gewünschtes Sicherheitsmodell: **eigenes zufälliges Schlüsselmaterial je Blob**,
nicht persistent im Klartext. Schutz durch bestehende Keyhierarchie oder eine
explizit reviewte Erweiterung mit etablierten Primitiven. Ziele sind Dateiisolation,
begrenzte Key-Lifetime und Unterstützung kryptografischer Löschung.
Keine eigene KDF, Cipherkonstruktion oder selbst erfundene Envelope-Kryptografie.
Keine Ableitung aus documentId, statischem Appschlüssel, Passwort oder Hash;
keine hartcodierten Keys und kein Klartextworkaround.

Wenn eine saubere Integration nicht möglich ist: **P10b STOPP / Kategorie C**.
P10a entscheidet kein Verfahren, Keyformat, Alias oder konkrete Verpackung
und ändert keine Securityimplementierung. P1s DB-Key-Owner ist keine automatische
Freigabe langlebiger File-Keys oder einer zweiten Sessionautorität.

## 7. Löschung, Cross-Store-Koordination und Recovery

Manuelle Dokumentlöschung ist vorgesehen. Bestätigung:

> Die geschützte PaG-Kopie dieses Dokuments wird entfernt.
> Die ursprüngliche Datei außerhalb von PaG bleibt davon unberührt.

Ziel: Keymaterial unzugänglich machen, Ciphertextblob und Metadaten entfernen.
Kein Erfolg bei Teilfehler. **DB, Dateisystem und Keymaterial sind nicht gemeinsam
atomar** (INT-001/004, ARCH-19/25). P10b muss vor Implementierung geschützte minimale
Arbeitszustände, Reihenfolge, Haltbarkeit, Abbruchpunkte und idempotenten Wiederanlauf
explizit festlegen; P10a schreibt keine Tabellen-/State-Machine-Struktur vor.

Import muss restart-/crash-recoverable sein. Halbfertige Dokumente niemals als
READY sichtbar; eindeutig zugeordnete temporäre Ciphertextblobs bereinigbar;
verwaiste Ciphertextblobs erkennbar und nach verlässlichem Bestandsabgleich
bereinigbar. Unbekannte oder wegen gescheitertem DB-Open nicht zuordenbare Dateien
nicht vorsorglich löschen: unklare Lage blockiert geschützt. Gültig abgeschlossene
Imports erhalten; verbliebene Arbeitsreste ohne Doppelimport bereinigen.
Pending Delete wieder aufnehmen, Zielreferenzen bis zum belegten Abschluss erhalten,
betroffene Inhalte nicht wieder freigeben. Keine sensitiven Recoverylogs.

Kryptografische Löschung ist ein **Ziel mit nachzuweisenden Grenzen**, keine
absolute Secure-Erase-Zusage. Per-File-Key allein genügt nicht. Historische
DB-/WAL-Seiten, alte Schlüsselverpackungen und Laufzeit-/externe Kopien sind
zu bewerten; nur einen verpackten Key aus einer weiter entschlüsselbaren DB
zu entfernen beweist dessen Unzugänglichkeit nicht. P10b muss diesen Nachweis
separat führen und die tatsächlich erreichte Schutzmaßnahme ehrlich benennen.
Keine Garantie physischer Flashüberschreibung, Entfernung jeder historischen
Flashkopie oder forensischer Unwiederherstellbarkeit.

## 8. Keine Klartexttempfiles und interne Viewergrenzen

Normale Verarbeitung erzeugt **keine temporäre Klartextdatei**, auch nicht in
cacheDir, filesDir, noBackupFilesDir, external cache, MediaStore, Downloads oder
`/tmp`. Klartext nur kontrolliert in flüchtigen Speicher-/Decoderbuffers.
Lock/Bereichswechsel schließt Viewer und verwirft erreichbare Klartext-/Bitmap-
referenzen. Keine Behauptung physisch sicherer Android-/JVM-RAM-Überschreibung.
Keine persistente Thumbnailkopie oder Klartext-Disk-Caches.

**Text:** ausschließlich interner Plaintext. Kein HTML-/Markdown-Ausführen,
WebView, Script, automatisches URL-Öffnen, Netzwerk, externer Viewer oder
Zwischenablagefunktion. RAM-Anzeige zulässig nach Nachweis; P10b muss eine klare
Viewergrenze/Streamingstrategie für große Textdateien wählen, nicht blind alles laden.

**JPEG/PNG:** interner kontrollierter RAM-/Decoderpfad; Pixel-/Speichergrenzen vor
Voll-Decoding prüfen, große Bilder downsamplen. Downsampling nur für Anzeige,
gespeicherte Originalbytes unverändert. EXIF nicht automatisch als UI-Metadaten
anzeigen. Kein Kamera-/Galerie-Sonderimport; weiterhin ausschließlich Systempicker.
Diese Voraussetzungen sind spätere Prüfaufträge, keine bereits sichere Viewerimplementierung.

**PDF:** P10b darf importieren, verschlüsselt speichern, listen, umbenennen und
löschen, erhält aber **keinen PDF-Preview-/Viewerpfad**. Typischerweise nötiger
seekbarer Klartextzugriff und zusätzliche Parserfläche benötigen separate Prüfung.
Sichtbarer Hinweis: **„PDF gespeichert · Vorschau in dieser Version nicht verfügbar.“**
Kein PDF-Klartexttempfile als Workaround.

Eigener späterer **P10c** prüft sicheren internen seekbaren Decryptionpfad,
Systemrenderer/unterstützte Android-API, Speichergrenzen, Parser-/Crashverhalten,
Lock/Session und Verbot externer Links/Aktionen, Formulare und Skripte. Kein
bestimmtes Verfahren durch P10a freigegeben. Ohne vertretbaren internen Pfad ohne
Klartextpersistenz/externe Übergabe bleibt PDF ohne Vorschau.

**SEC-014 bleibt strikt:** kein „Öffnen mit …“, Share Sheet, ACTION_VIEW,
ACTION_SEND, FileProvider-/ContentProvider-Klartextübergabe an fremde Apps,
Export, Speichern unter, Download aus PaG, Druck oder Clipboard-Dateikopie.
Auch kein impliziter externer Viewerfallback.

## 9. Integritäts- und Aussagegrenzen

Authentifizierte Verschlüsselung soll zufällige/manipulierte Ciphertextänderungen
erkennen. Sie bestätigt weder externe Authentizität, Herkunft von einer Person,
Unverändertheit seit externer Entstehung, gerichtliche Verwertbarkeit, Chain of
Custody, Trusted Timestamp noch Beweisqualität. Keine Hash-/Signaturfunktion
zur Erzeugung solcher Erwartungen. Keine Beweis-/Forensik- oder Rechtsgarantie.
Späterer Fehlertext: **„Das Dokument konnte nicht sicher gelesen werden.“**
Keine Aussage „Beweis manipuliert“ und kein vermeintlicher Korruptionsbeweis
allein aus einem Entschlüsselungsfehler.

## 10. Lifecycle und UI-Ziel

| Aktionsstatus | Lesen / Importieren / Umbenennen / Löschen |
| --- | --- |
| DRAFT | Kein P10-Zugriff |
| PREPARATION | Nach bewusstem Einstieg, gültiges READY |
| ACTIVE | Nach bewusstem Einstieg, gültiges READY |
| COMPLETED | Enge administrative P10-Ausnahme nach bewusstem Einstieg |
| DELETION_PENDING | Kein normaler P10-Zugriff |

Dokumente können erst nach einer Aktion eintreffen. Keine allgemeine COMPLETED-
Schreibfreigabe oder Action-Reopen. ActionLifecycle, TaskRules, MaterialRules,
OperationsRules und P6-/P8-/P9-Regeln bleiben unverändert.

PREPARATION erhält später „Dokumente“ mit einfacher Anzahl/bewusstem Einstieg.
ACTIVE erhält die **neunte** Hubkarte „Dokumente“, ausschließlich **„<N> Dokumente“**
aus Summary, keine Liste/Dateiöffnung für Count. Stabile bestehende Hubreihenfolge
mit Dokumente vor Aktionsdetails, bestehender ein-/zweispaltiger responsive Vertrag.
Keine Namen, Inhalte, Typen mit sensitiver Bedeutung, Vorschauen oder Thumbnails
im Hub. COMPLETED: **„Dokumente verwalten“**; vor Tap **0 Dokumentreads,
0 Dateiöffnungen, 0 P10-Writes**, einschließlich Summary. Kein Prefetch.
P10a ergänzt keine heute bedienbare Karte oder neue Navigation.

Bewusst geöffnete Liste zeigt displayName, Format, Größe und „importiert am“;
Sortierung **importedAt ASC → id ASC**. Keine Thumbnails/Inhaltsvorschau,
Kategorien, Filter oder Suche. Aktionen: „Dokument importieren“, intern öffnen
nur bei freigegebenem Formatviewer, Umbenennen, Löschen. PDF ehrlich ohne Vorschau.
Bestehende 48dp-Touchziele, Heading-/Disabledsemantik, große Schrift und klare
Rückwege bleiben Prüfauftrag, keine menschliche Accessibilityabnahme.

Import-UX: „Dokument importieren“, vor/nach Picker deutlich
**„PaG erstellt eine verschlüsselte Kopie. Die Originaldatei außerhalb von PaG
bleibt bestehen.“** Nach Picker Format/Größe prüfen, displayName vorbereiten,
bewusster Import; kein automatisches Öffnen. Falls Bestätigungs-/Namensstufe für
sichere Fehlerrückmeldung nötig, kein automatischer Import durch bloße Picker-
Rückgabe. P10b konkretisiert den Ablauf unter unverändertem Sessionvertrag.

## 11. Fehler, Logging und Sessionrennen

Verständlich unterscheiden: nicht unterstütztes Format, Datei zu groß,
Aktionsspeichergrenze erreicht, Providerzugriff verloren/Quelle nicht lesbar,
Import fehlgeschlagen, PaG-Kopie beschädigt/nicht lesbar, Speicherplatz unzureichend.
Keine Pfade, URIs oder Dateiinhalte in Fehlern; kein stiller externer Fallback.

Nicht loggen: Dateiname, URI, Provider, Inhalt, Dateigröße mit identifizierbarem
Kontext, Keys oder Viewerinhalt. Nur technische Fehlerkategorien ohne Nutzdaten;
keine Telemetrie oder sensitive Recoverydiagnose.

Bestehende Session: 180 Sekunden absolut, 30 Sekunden READY-Inaktivität,
App-Verlassen/Device-Lock und Generationgates unverändert. Lock schließt Viewer,
verwirft gelesene Klartext-/Bitmapreferenzen und unbestätigte UI-Entwürfe.
Sicher abgeschlossener Import bleibt gespeichert; Reauth öffnet nichts automatisch.
Import-/Delete-Recovery und System-/Storagecallbacks sind keine User Interaction,
keine Sessionverlängerung oder gespeicherte Auth-/Navigationsfreigabe.

P10b muss mindestens diese Rennen nachweisen:

- Revoke vor finaler Commitfreigabe: kein sichtbarer fertiger Import;
  Wiederanlauf bereinigt zugeordnete Zwischenzustände sicher.
- Ciphertext vollständig geschrieben, Session vor Publication verloren: kein
  Erfolgsfeedback/automatisches Öffnen. Vollständiger Blob allein beweist keinen
  fachlichen Commit; nach Reauth tatsächlichen Bestand neu bestimmen.
- Providerstream bricht ab: kein halbes READY-Dokument, keine Teilübernahme.

Kein blindes Retry gegen die externe Quelle, kein behaupteter Rollback allein
wegen ausgebliebenem Callback. Revoke/Action-/Phasenwechsel invalidiert alte
UI-Workflows; genaue sichere Import-/Delete-Abbruchpunkte definiert erst P10b.

## 12. Personenbezug, Retention und Backup

Kein personId, Autor:innenprofil, Betroffenenlink oder P3b-Bezug. Personenlöschung
verändert Dokumente nicht. displayName und eingebettete Inhalte können weiterhin
Personenbezug enthalten; späterer manueller Freitext-/Dateiprüfworkflow muss P10
einschließen. Keine automatische Anonymisierung oder Dateiinhaltsmutation.

Keine konkrete Retentiondauer/automatische Retention durch P10a. Dokumente bleiben
aktionsgebunden, vollständige Action-Löschung ist Obergrenze für Metadaten,
Keys, Blobs und kontrollierte Ableitungen; keine unabhängige Dateiinsel.
Manuelle Dokumentlöschung vorgesehen. Andere Aufbewahrungsbedarfe als Tasks/
Material und mögliche rechtliche Interessen sind vor Retentionumsetzung
fachkundig zu prüfen. Keine Speicherung „für immer“.

Backup-/Transferregeln bleiben unverändert: kein Auto Backup, Device Transfer
oder öffentliche Medienablage durch Speicherortwahl reaktivieren. Auch
Ciphertexts, Keyverpackungen und Recoveryartefakte ausschließen. P10b muss
tatsächliche Manifeste, Speicherinventar und Backup-/Transferverhalten prüfen;
No-Backup-Pfad allein ist kein allgemeiner OEM-Nachweis.

## 13. Nicht freigegeben und offene Folgearbeit

Nicht Teil P10a/P10b-MVP: Kamera, Scanner, OCR, Audio/Video, Office, Archive,
HTML/SVG, P6-/P9-Anhänge, Person-/Task-/Material-/Kosten-/Rollenlinks, Kategorien,
Tags, Volltextsuche, Thumbnailcache, Export/Sharing/Druck, externe Viewer,
Klartext-FileProvider, Cloud-/Netzwerksync, Upload/Download aus PaG, Signaturen,
Hashchain, Trusted Timestamp, Beweis-/Forensikgarantien, automatische Retention,
Secure-Erase-Garantie oder globale Bibliothek. Keine neue Dependency/Permission
oder Securitycodeänderung durch diesen Dokumentslice.

Offen vor P10b: reviewte File-Key-/Streaming-AEAD-Integration und Löschgrenzen,
technische minimale Recoveryzustände/Reihenfolge/Haltbarkeit, sichere SAF-Rückkehr,
Formatplausibilisierung, Textviewer-/Pixel-/RAM-Grenzen, konkretes Speicherinventar,
synthetische Crash-/Revoke-/Grenz-/Privacy-/Backup-/Gerätetests. Bei unsicherer
Keyintegration STOPP/Kategorie C, kein Scope-Workaround. PDF separat P10c.
Konkrete Fristen, allgemeiner Action-/Personlöschworkflow, fachkundige Privacy-/
Securityprüfung und menschliche TalkBack-/Accessibilityabnahme bleiben offen.

## 14. Selbstprüfung und spätere Nachweise

P10a prüft Baseline, konsistente Produkt-/Privacy-/Securitygrenzen, lokale Verweise,
Markdown-only-Diff und unveränderten technischen Bestand. Keine Unit-, Build-,
Storage-, Viewer-, Gerätetests oder bereits bestandene P10-Sicherheitsabnahme.
P10b muss synthetisch Grenzwerte 100/101, 25/250 MiB, unbekannte/falsche Providergrößen,
Formatspoofing, Quellenabbruch/Speicherknappheit, Parallelimporte, Crash an jedem
Cross-Store-Übergang, pending Delete/Orphans und Key-/DB-Fehler prüfen.
Hinzu kommen keine Klartexttempfiles/Thumbnails/Logs, unveränderte Importbytes,
minimierte Hubsummary/COMPLETED-No-Prefetch, keine P3b-/anderen Fachmutationen,
Lock/Reauth/late callbacks/Deadlines, Backup/Transfer und ehrlicher PDF-Hinweis.
Dies sind Prüfaufträge, keine durch P10a bestandenen technischen Tests.

Tatsächliche P10a-Dokumentationsprüfung: ausschließlich acht Markdown-Dateien
neu/geändert, alle 245 getrackten Nicht-Markdown-Dateien per SHA-256 gegenüber
der sauberen Baseline unverändert (einschließlich Kotlin, Schema10, Gradle,
Manifesten/XML und Securityimplementierung). Lokale Vertragsverweise vorhanden;
zwölf mechanische Vertragschecks und manuelles Inhalts-/Diffreview ohne Befund.
`git diff --check` ohne Befund. Schema bleibt 10, HEAD und Branch unverändert.
Keine P10-Unit-/Storage-/Viewer-/Gerätetests oder Builds ausgeführt; P10a ist
ein Dokumentationsvertrag und behauptet keine technische Abnahme.

Geänderte Dateien:

- `docs/product-p10a-documents.md` (neu, verbindlicher Vertrag).
- `PRODUCT.md` (FR-050–054/095, getrennte Bibliothek und keine P10-Suche).
- `docs/data-model.md` (nur konzeptionelles DocumentAsset statt breiter alter Planung).
- `docs/privacy.md` (Originalquelle, Metadaten, SENSITIVE/READY und Personenbezug).
- `docs/security.md` (Key-/Klartext-/Viewer-/Sessiongrenzen als spätere Prüfaufträge).
- `docs/data-retention.md` (manuelle Kopienlöschung, Aktionsobergrenze, Löschgrenzen).
- `docs/architecture.md` (Cross-Store-Wiederanlauf und kein Klartexttempfile).
- `docs/ux-roadmap.md` (P10b-Folgeauftrag und separate P10c-PDF-Prüfung).

Kein Commit. Kein Push.

A) P10a DOKUMENTE PRODUCT/SECURITY CONTRACT GRÜN – BEREIT FÜR REVIEW
