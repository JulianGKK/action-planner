# P10b1 — Dokumente: technischer Storage-/Recovery-Vertrag

## 1. Status und Baseline

Ausschließlich technische Spezifikation, noch keine Produktimplementierung.
Baseline: `p10b1-documents-storage-design`, sauberer Working Tree, HEAD `88759fe`
(P10a-Merge), Schema **10**. [P10a](product-p10a-documents.md) bleibt unverändert
fachliche Source of Truth. Dieses Dokument legt den bevorzugten minimalen
Implementierungsvertrag für P10b2 fest, ohne eine neue Schemanummer, Migration,
Kotlin-, Manifest-, Dependency- oder Permissionänderung zu erzeugen.

Gezielt gelesen: P10a, [B3](prototype-b3.md), [B4](prototype-b4.md),
[FileStreamingCipher](../security/src/main/kotlin/org/pag/security/FileStreamingCipher.kt),
[SessionDbKeyOwner](../data/src/main/kotlin/org/pag/data/product/SessionDbKeyOwner.kt),
[ProductRuntime](../data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt),
sowie relevante Abschnitte von [Architektur](architecture.md), [Security](security.md),
[Datenmodell](data-model.md), [Retention](data-retention.md) und [Testing](testing.md).
Zusätzlich gezielt der bereits produktive Room-Builder/Close-Pfad in
[ActionDatabaseAccess](../data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt).
Keine erneute Komplettanalyse oder B3/B4-Testkampagne.

## 2. Wiederverwendung und ausdrückliche Prototypgrenzen

| Bestehender Nachweis | Für P10b2 übernehmen | Nicht übernehmen |
| --- | --- | --- |
| B3 | FileStreamingCipher, Tink AES256_GCM_HKDF_1MB, 32-Byte-Keyimport, opaque AAD, bounded Streaming, vollständiges EOF/Länge, keine Klartexttempfiles/externe Öffnung, Generation-/Sessiongates | Eigener B3-RSA-Alias/Prompt, k/0–2-Marker, verschlüsseltes synthetisches Manifest, gemeinsamer Test-File-Key, Prototypnamespace/Experiment-UI |
| B4 | Getrennte persistente DB-Phasenschritte/File-I/O, Jobs, Reconciliation vor READY, eindeutiges Inventar, kein heuristisches Löschen, kurze Room-Instanzen und typisierte Background-Fault-Behandlung | Injektierte persistenzlose Test-Key-Owner, 22×4-Großmatrix, zusätzlicher Hidden-Assetstatus, VALIDATED/METADATA_COMMITTED/FILE_REMOVED-Phasen, Fortsetzung eines WRITING-Imports |

Die historischen Fairphone/API36-Ergebnisse bleiben auf ihren getesteten Umfang
begrenzt. B4s Prozessverlustmodell ließ finally/close laufen; es beweist weder
SIGKILL-/Power-loss-Haltbarkeit noch einen echten Multi-Key-Authrestart.
Die neue Produktintegration und geänderten Übergänge benötigen fokussierte
P10b2-Nachweise, keine Behauptung unveränderter vollständiger B4-Abdeckung.

## 3. File-Key-Modell und Schutzgrenze

**Empfohlene Entscheidung:** Je Dokument ein unabhängig kryptografisch zufällig
erzeugter **32-Byte File-DEK**. Persistent ausschließlich als BLOB innerhalb
der vorhandenen SQLCipher-Produkt-DB: physisch at-rest im verschlüsselten
DB-Bestand einschließlich dessen bestehenden Sidecar-Schutzvertrags.
Keine separate Klartext-Keydatei, Preferences/DataStore, persistiertes Tink-Keyset,
Ableitung aus DB-Key/documentId/Passwort, eigene KDF oder zusätzlicher File-Root-Key.
Keine neue Authentifizierung, zweiter DEVICE_CREDENTIAL-Prompt, neuer Keystore-Alias
oder paralleler Session-Key-Owner.

Beim Gebrauch: autorisierte SQLCipher-Row → kurze kontrollierte ByteArray-Kopie
→ **FileStreamingCipher.fromUnwrappedKey(...)**. DAO-/Lesekopien und kontrollierte
Consumerkopie in finally bestmöglich überschreiben und Referenzen verwerfen;
Primitive/Streams danach schließen. Kein File-Key in Domainobjekt, UI, String,
Log, SavedState oder langlebiger Registry. FileStreamingCipher bleibt unveränderte
etablierte Crypto-Grenze; seine Tink-KeysetHandle/SecretBytes-Kopien sind RAM-only
und nicht vollständig überschreibbar. Der Methodenname „Unwrapped“ erzwingt
keinen zusätzlichen RSA-Unwrap oder Authprompt.

SQLCipher ist der persistente Schutz des DEK, keine neue selbst entworfene
Envelopekonstruktion. DB-Key-Leases bleiben unter SessionDbKeyOwner; Keypräsenz
autorisiert nichts. Während File-I/O weder Room offenhalten noch DB-Key-Lease
unnötig verlängern. Im MAINTENANCE-Pfad wird wie bisher der bereits autorisierte
DB-Key-Consumer genutzt; Reconciliation läuft vor Bindung/Publication des
normalen READY-Owners. Keine provisorische zweite Ownerinstanz.

**Vertragsabgleich:** P10a §6 verlangt eigenes zufälliges Schlüsselmaterial ohne
persistenten Klartext; SQLCipher-at-rest erfüllt dieses Speicherziel konzeptionell.
SEC-04/ARCH-16 trennen Verantwortlichkeiten, legen aber keine zusätzliche
File-Root-Key-Verpackung fest. B3s Schlussprofil fordert seinen separaten RSA-Pfad
für den getesteten Prototyp; Einleitung/Schluss lassen die Produkt-Keyhierarchie
offen. B4 §Status führt gerade keine zwei Produktprompts ein. Kein bestehender
verbindlicher Produktvertrag verlangt hier einen zweiten Auth-/Keypfad.
P1 bleibt DB-only; P10b2 ergänzt ausschließlich autorisierte DEK-Nutzung.

Entfernen der aktuellen DEK-Row ist **kein bewiesenes kryptografisches Secure Erase**:
historische SQLCipher-/WAL-/Flashseiten und RAMkopien können verbleiben. P10
bezeichnet diese Lösung nicht als starke kryptografische Löschung. Ziel ist
Entfernung aus dem aktuellen Live-Bestand plus Ciphertext-unlink, ohne physische
Überschreib-/forensische Unwiederherstellbarkeitsgarantie (P10a §7, Retention §7).
Falls P10b2 einen konkreten Vertrags- oder Integrationsblocker findet: STOPP/C
mit Fundstelle, keine eigenmächtige komplexere Schlüsselarchitektur.

## 4. AAD, Container und privates Dateiinventar

AAD ausschließlich **opaque documentId als UTF-8**, stabil für den gesamten
Dokumentlebenszyklus. Keine Actiontitel, displayName, MIME/Format, Person oder
Dateinamen. AAD authentifiziert, nicht geheim; Rename verändert AAD nicht.
Jeder Blob enthält ausschließlich Standard-Tink-Streaming-Ciphertext mit
AES256_GCM_HKDF_1MB, keine eigenen Cryptoheader, Chunks oder Keysetcontainer.
Ciphertextgröße ist nicht plaintextSize; keine Padding-/Metadatenverbergungszusage.

Geplanter Root: **noBackupFilesDir/pag-documents-v1/** mit ausschließlich
**staging/** und **files/**. Kinderdateinamen separat zufällige opaque UUIDs,
ohne Originalextension/Nutzername. documentId, jobId, stagingName und blobName
werden unabhängig reserviert; Namen haben keine Pfadseparatoren.
Root/kinder kanonisch unter trusted noBackupFilesDir prüfen; Symlinks auf jeder
kontrollierten Pfadkomponente ablehnen, nur reguläre Dateien/Verzeichnisse der
erwarteten Form. Kein folgenloses Akzeptieren von canonical escape, fremden
Einträgen, doppelten Blobbindungen oder unerwarteten Unterverzeichnissen.
Keine vorhandene Zieldatei überschreiben, auch nicht bei Rename-Kollision.
Pfad-/Dateioperationen bleiben :data-Verantwortung, keine Crypto-/UI-Pfadfreigabe.

Rootanlage nur als ausdrücklich definierter erstmaliger P10-Speicherschritt unter
autorisierter Koordination. Leere Tabellen und vollständig fehlender Root sind
vor diesem Schritt zulässig. Teilroot oder Dateien ohne verlässliche DB-Zuordnung
sind kein Initialisierungsanlass. Crash während bekannter Rootanlage darf nur
leere erwartete staging/files-Verzeichnisse vervollständigen; fremde Einträge
blockieren. Sobald Rows/Jobs bestehen, darf fehlender erwarteter Bestand nicht
als leerer Neuimportbestand ersetzt werden. Keine Veränderung des DB-Namespace.

## 5. Bevorzugte minimale spätere DB-Struktur

Planungsnamen, **kein gegenwärtiges Schema und keine Schemanummer**. P10b2
führt genau die notwendige additive Migration vom aktuellen Schema 10 ein;
bestehende Entities/Trigger bleiben unverändert.

| documents | Vertrag |
| --- | --- |
| id | opaque UUID, Primary Key |
| actionId | Action-FK **RESTRICT**, nicht blind CASCADE |
| displayName | P10a: Trim, 1–160 Unicode-Codepoints |
| format | geprüfte stabile technische Auswahl PDF/JPEG/PNG/TEXT |
| plaintextSize | tatsächlich gestreamte Bytezahl, 0..26.214.400; nicht Providergröße |
| importedAt | repositoryeigene Zeit des finalen Metadatencommits, keine Quellzeit |
| blobName | eindeutiger opaque finaler Dateiname |
| fileKey | exakt 32 Byte BLOB, nur SQLCipher |

Kein sichtbares Statusfeld. documents-Row bedeutet fachlich READY, sofern
kein Delete-Job sie verbirgt. UNIQUE(actionId,id), eindeutiger blobName;
alle Fachreads explizite Projektion **ohne fileKey**. Countsummary liest nur
sichtbare Rows, keine Namen/Keys/Dateien. Liste nach importedAt ASC/id ASC,
Detail/Bloböffnung nur bewusst; normale COMPLETED-Seite ohne P10-Prefetch.

| document_jobs | Vertrag |
| --- | --- |
| id | opaque UUID, Primary Key |
| actionId | Action-FK RESTRICT |
| documentId | UNIQUE: genau ein aktiver Job je Dokument, keine FK auf documents |
| operation | IMPORT oder DELETE |
| phase | ausschließlich die unten definierten Phasen der jeweiligen Operation |
| stagingName | bei IMPORT reservierter opaque Name, bei DELETE null |
| blobName | reservierter/eindeutig zugeordneter finaler Name |
| displayName, format | bei IMPORT gültige Fachwerte, bei DELETE null |
| plaintextSize | IMPORT RESERVED/WRITING null; CLOSED/PUBLISHED tatsächliche Größe; DELETE bestehende Größe für begrenzte Reservierung |
| fileKey | IMPORT exakt 32 Byte; DELETE immer null, **keine weitere Keykopie** |

Keine URI/Provider/Pfad/Sessiongeneration/Fehlertexte oder sensible Logdaten.
Jobnamen sind technische Basenames, keine gespeicherten Pfade. Keine permanente
History. Job zu importierter Row und Delete-Row müssen immer derselben Action
gehören. Cross-Table-Doppelbindung von Namen/IDs und unzulässige Null-/Phasenkombination
werden im koordinierenden Transaktionspfad und bei Reconciliation strikt abgewiesen.
File-Key vom Importjob zur documents-Row nur innerhalb einer DB-Transaktion
übernehmen und Job löschen, keine persistenten Keyduplikate als separater Schritt.
Bytegenaue Keygleichheit ist keine UI-/Logdiagnose.

**Action-Löschung:** RESTRICT auf beiden Tabellen hält Action und Zielreferenzen
erhalten, bis ein späterer Gesamtlöschkoordinator bekannte P10-Imports abgebrochen
und alle Dokumente/Jobs/Blobs bereinigt hat. Erst dann Actionrow entfernen.
Keine Action-Deletionimplementierung durch P10b1/P10b2, keine reine DB-CASCADE
mit verwaisten Ciphertexts. Interne Maintenance darf bekannte Cleanupjobs auch
bei DELETION_PENDING ohne normalen Fachread fortsetzen; kein Reopen.

## 6. Reservierungen und Serialisierung

Alle Reservierungen unter kurzer SQLCipher-WriteTransaction: persistierten
Actionstatus prüfen (PREPARATION/ACTIVE/COMPLETED), vorhandene Rows/Jobs strikt
validieren, Grenzen atomar prüfen und Job insert vor COMMIT-Gate. Kein optimistisches
UI-Count oder Providergrößenvertrauen. Checked Long-Arithmetik, Overflow blockiert.

Ein IMPORT_RESERVED/WRITING-Job reserviert konservativ **25 MiB und einen Slot**,
unabhängig von Providergröße; kein zusätzliches reservedBytes-Feld. Ab CLOSED
reserviert plaintextSize die bestätigte tatsächliche Größe. Grenzsumme = alle
documents-Rows (auch delete-hidden) + IMPORT-Jobs + DELETE_FILE_PENDING-Jobs ohne
Parent. Bei DELETE_RESERVED Row nur einmal zählen, bei DELETE_FILE_PENDING die
kopierte Größe/ein Slot bis unlink/Jobabschluss erhalten. Damit kann Cleanup
nicht durch parallele Neureservierung aus dem Kapazitätsinventar verschwinden.
Maximal 100 Slots und 250 MiB; dieselbe DB-Writerzulassung serialisiert konkurrierende
Reservierungen. Shrink auf tatsächliche Größe erst mit autorisiertem CLOSED-Commit.
Nachteil bewusst: auch eine kleine Datei kann vorübergehend abgewiesen werden,
wenn weniger als 25 MiB Reservierungsbudget frei ist; kein unnötiges Providervertrauen
oder zusätzliches Reservierungsmodell. Nach Recovery/Abschluss bewusst erneut versuchen.

P10b2 serialisiert Filemutationen und Reconciliation im bestehenden Runtime-Worker-
Pfad bzw. eng scoped Data-Koordination; kein Coordinator-Monitor über I/O und
keine neue Transaktionsplattform. Ein Dokument darf nicht gleichzeitig geöffnet/
gelöscht/mutiert werden; alte laufende Streams erst schließen/joinen, bevor
neue MAINTENANCE-Reconciliation dessen Dateien behandelt. Keine alte WRITING-
Arbeit darf nach Cleanup neue Dateien erzeugen. Ablauf begrenzt Zulassung auch
während dieser Ressourcenbereinigung; Wartezeit verlängert keine Session.

## 7. Import-State-Machine

Start erst nach Picker-Rückkehr, **regulärem neuem READY** und bewusster
Importbestätigung. Pickerresultat allein erzeugt weder Job noch Datei.

| Phase | Persistente Bedeutung / nächster autorisierter Schritt |
| --- | --- |
| IMPORT_RESERVED | IDs/Name/Format/DEK und maximale Kapazität reserviert; noch keine Datei. WriteTransaction → IMPORT_WRITING vor Staginganlage. |
| IMPORT_WRITING | Genau bezeichnetes Staging darf entstehen; Quelle einmal bounded streamen → Tink encrypt. Keine Klartextdatei. Erfolgreich schließen/syncen, tatsächliche Größe atomar setzen → IMPORT_CLOSED. |
| IMPORT_CLOSED | EncryptingStream erfolgreich geschlossen; sync soweit verfügbar erfolgt. Vollständige erneute Decryption/EOF-/Längen-/Formatprüfung zwingend vor Publish; keine VALIDATING/VALIDATED-Erfolgsphase. |
| FILE_PUBLISHED | Validiertes Ciphertextziel liegt in files/, Job noch vorhanden, keine sichtbare Row. Finalen Blob vollständig erneut prüfen, dann finaler DB-Commit. |

Finaler Commit in **einer** SQLCipher-Transaktion: Job/Action/Reservierung erneut
prüfen, documents inklusive DEK und repository-now importedAt insert, Job delete,
COMMIT-Gate. Erst danach fachlich READY. Crash davor lässt Job, Crash danach
lässt ausschließlich Row; kein zusätzlicher METADATA_COMMITTED-Zustand nötig.

close/Flush/Dateisync sind keine EOF-Validierung. Tink kann frühe Klartextsegmente
liefern, bevor ein späterer Fehler erscheint. Nur normales echtes EOF, vollständig
authentifizierte Decryption, passende tatsächliche Länge und Formatprüfung zählen.
IOException/Tagfehler niemals als EOF werten. Eigene begrenzte Buffer best-effort
wipen; kein Whole-File-RAM. Keine Bytes an UI während Importverifikation.

Move staging → files nur ohne Überschreiben unter erlaubtem STEP; nach Move
sync/Verzeichnis-Haltbarkeit soweit Plattformpfad unterstützt, dann persistente
FILE_PUBLISHED-Phase. P10b2 muss konkrete Move-/sync-APIs und Failure-Ergebnisse
benennen; FileDescriptor.sync beweist keine allgemeine Power-loss-Durability.
Nichtatomarer Move mit uneindeutiger Dateilage blockiert, kein heuristisches Merge.

## 8. Import-Recovery und Fehlerentscheidung

Vor Mutation vollständige verlässliche DB-/Dateizuordnung prüfen. Kein Quell-Re-Read.

| Vorgefundener Job / Dateilage | MAINTENANCE-Regel |
| --- | --- |
| RESERVED ohne Dateien | Job verwerfen; kein Dokument/Retry. RESERVED mit Datei widerspricht erlaubter Reihenfolge → BLOCKED. |
| WRITING, final fehlt, bekanntes Staging vorhanden oder noch nicht angelegt | Nie publizieren, auch wenn close inzwischen physisch fertig wurde. Genau bekanntes reguläres Staging entfernen, fehlendes Staging idempotent akzeptieren; erst danach Job löschen. |
| WRITING mit finalem Blob | Widerspruch → BLOCKED, nicht löschen. |
| CLOSED: staging vorhanden, final fehlt | Vollständig validieren, dann Move und FILE_PUBLISHED anerkennen. |
| CLOSED: staging fehlt, final vorhanden | Rename war möglicherweise fertig: final vollständig validieren, dann FILE_PUBLISHED anerkennen. |
| CLOSED: beide vorhanden oder beide fehlen | BLOCKED, keine Auswahl/Löschung/Ersatzdatei. |
| PUBLISHED: staging fehlt, final vorhanden | Final vollständig validieren, documents insert + Job delete atomar. |
| PUBLISHED: fehlender finaler Blob, vorhandenes Staging oder widersprüchliche Row | BLOCKED. |
| Unbekannte fremde Datei / Namenbindung / Symlink | BLOCKED, niemals heuristisch entfernen. |

**CLOSED/PUBLISHED mit ungültigem Ciphertext/EOF/Länge/Format:** BLOCKED,
bekannten Bestand und Job erhalten, nicht freigeben. Begründung: Anders als
WRITING behauptet CLOSED einen erfolgreich abgeschlossenen Stream; der Fehler
kann Key-/Datenschaden oder I/O sein. Keine automatische Zerstörung aufgrund
Entschlüsselungsfehler. Eine spätere explizite Reparatur-/Abbruchentscheidung ist
kein P10b2-Fallback. Laufender Providerabbruch/Grenzfehler verbleibt dagegen
WRITING und darf nach dem definierten unveröffentlichten Versuchscleanup verschwinden.

WRITING-Cleanup kann selbst crashen: solange Job besteht, sind vorhandenes oder
bereits fehlendes genau bezeichnetes Staging akzeptiert. Erst fehlendes Ziel erlaubt
Jobdelete. Dieser enger definierte sichere Cleanup ersetzt bewusst B4s BLOCKED-
WRITING-/verifizierbare-WRITING-Fortsetzung; kein neuer Jobtyp nötig.

## 9. Formatprüfung im vollständigen Verifikationspass

Extension/MIME bestimmen keine Zulassung. Ausgewähltes Format gegen Inhalt prüfen:

- PDF: `%PDF-` am Anfang, keine Vorschau/Parserausführung.
- PNG: exakte acht Byte PNG-Signatur (89 50 4E 47 0D 0A 1A 0A).
- JPEG: SOI/Markerprefix FF D8 FF; keine Bildneukodierung.
- TEXT: gesamter Stream strikt gültiges UTF-8 mit inkrementellem Decoder und
  Fehlerbehandlung REPORT, keine Replacement-Decodierung. Minimale Plaintext-
  Plausibilisierung: NUL und Steuerzeichen U+0001–0008/U+000B–000C/U+000E–001F/
  U+007F ablehnen; TAB/CR/LF zulassen. UTF-8-BOM am Anfang akzeptieren, Bytes
  unverändert speichern. Kein HTML-/Script-/Markdown-Renderer oder URL-Ausführen.

Diese wenigen Prüfungen begrenzen Verwechslungen, sind kein vollständiger Parser,
Malwarescan oder Sicherheits-/Authentizitätsnachweis. Textformat ist Plaintextanzeige,
nicht eine Freigabe aktiver Inhalte. Leerer Text ist technisch gültig; keine
zusätzliche fachliche Mindestdateigröße. Binärformate müssen ihr Prefix besitzen.
Gesamte tatsächliche Streamgröße höchstens 25 MiB; beim ersten darüber hinaus
gelesenen Byte abbrechen, nie speichern als verkürztes Dokument. Bei Decryption
ebenso bounded prüfen, nur passendes EOF zur gespeicherten plaintextSize anerkennen.

## 10. Delete-State-Machine

| Schritt / Phase | Vertrag |
| --- | --- |
| DELETE_RESERVED | Unter WriteTransaction aktuellen Document-Snapshot/Action prüfen, UNIQUE-Job mit documentId/blobName und plaintextSize erzeugen; keine DEK-/Name-/Formatkopie im Job. Existenz verbirgt Parent sofort. |
| DELETE_FILE_PENDING | In einer DB-Transaktion Parent konsistent zum Job prüfen, documents-Row entfernen (Live-DEK weg), Job auf DELETE_FILE_PENDING setzen. Zielreferenz bleibt. |
| File-I/O | Nur exakt bekanntes reguläres files/blobName unlinken; fehlendes Ziel hier idempotent. Kein Secure-Erase-Versprechen. |
| Abschluss | Belegt fehlendes Ziel, kein Staging/anderer Bindungskonflikt; Job in DB-Transaktion entfernen. Erst dann zulässige UI-Erfolgspublication. |

Summary/List/Detail/Blob-Open schließen **jeden DELETE-Job** aus, nicht erst
DELETE_FILE_PENDING. Bereits gelesene UI-/Streamreferenzen am zulässig gestarteten
Delete invalidieren; keine Keyleases oder offenen Reader über Delete mitführen.
STALE oder existierender anderer Job macht keinen blinden zweiten Delete.
Nur Data liest den kurzlebigen Key für autorisierte Zwecke, niemals für Deletejob.

Recovery RESERVED: konsistente Parentrow muss noch existieren, keine andere
Bindung; erwarteter Blob vorhanden, sonst BLOCKED. Dann Parentdelete/Phase atomar
wiederholen. Recovery PENDING: Parent muss fehlen; vorhandenes Ziel unlinken oder
bereits fehlend akzeptieren, anschließend Jobdelete. Unlinkfehler erhält Job und
versteckten Zustand, keine Erfolgsmeldung. Wiederauftauchende Datei nach bereits
gelöschtem Job ist unknown und blockiert. Keine direkte DAO-Produktlöschung ohne Job.

## 11. Maintenance/Reconciliation und Runtimeanschluss

Ausschließlich vorhandener **MAINTENANCE vor READY**, kein eigener Unlock-State.
Reihenfolge: authentifizierte DB öffnen/validieren → Rows/Jobs streng snapshotten
→ Room schließen → Root/staging/files inventarisieren und Bindungen prüfen
→ bekannte Jobs reconciliieren → abschließender DB-/Inventarabgleich → READY.
Jeder DB-Schritt eigene kurze Instanz, Keys nie in nichtautorisierten Snapshots.
P10b2 erweitert den bestehenden authorizedMaintenance-Consumer vor dessen
READY-Ownerpublication; kein UI-/Repository-Bypass.

Unknown Dateien, Symlinks, duplicate Namen, widersprüchliche Phasen oder
fertiges Asset ohne erwarteten Blob → BLOCKED, kein automatisches Löschen.
Fehlgeschlagener DB-Open → **niemals Filesystembereinigung**. Keine Recovery
über externe URI. Bekannte vollständige documents strukturell und nach Bedarf
vollständig prüfen; neue/fortgesetzte Imports vor Commit immer vollständig.
Nicht bei jedem Unlock alle maximal 250 MiB blind entschlüsseln: Reconciliation
erzwingt exakte Zuordnung/Existenz; spätere Bloböffnung volle Integritätsprüfung.
Keine Garantie, alle unberührten Ciphertexts bei jedem Unlock zu authentifizieren.

Ablauf/Revoke während Maintenance: LOCKED, persistente Jobs bleiben; nächster
expliziter Unlock erneut reconciliieren. Recovery/Timer/File-I/O/Roomcallbacks
keine Userinteraction; 180s absolut und 30s READY-Inaktivität unverändert.
Kein Erfolg/READY bei ungeklärtem Inventar oder Background-Fault.

**Konkreter Room-Befund:** [B4 „Praktischer Runtime-Blocker auf Fairphone“](prototype-b4.md#praktischer-runtime-blocker-auf-fairphone)
belegt nach Jobcommit einen `Room Invalidation Tracker Refresh`, der nach Expiry
mit erwarteter Access-Revocation den Prozess beendete, auch ohne observable DAOs.
[B4 „Room-3.0.3-Quellenprüfung und gezielter Blocker-Fix“](prototype-b4.md#room-303-quellenprüfung-und-gezielter-blocker-fix)
verlangt queryCoroutineContext, instanzlokalen Parent/Handler, genaue typisierte
Revocation und irreversible Unexpected-Background-Fault-Sperre, kontrollierten
Close/Cancel/Join/Driverclose. ActionDatabaseAccess verwendet diesen Pfad bereits.
P10b2 nutzt ihn unverändert pro strukturiertem Schritt und hält niemals Room
über Tink-/Provider-/File-I/O offen. Kein improvisierter Lifecycle, kein catch-all
„Widerruf“, keine Wiederverwendung alter Handles. Room-Upgrades erneut prüfen.

## 12. SAF-Picker-Handoff ohne gespeichertes Zugriffsrecht

READY → bewusster „Dokument importieren“-Tap → Pickerstart → alte Session LOCKED
→ optionales Pickerresultat. Unter LOCKED **keine Inhalts-/Metadata-Reads**,
kein Job und keine Datei. Resultat ist keine Autorisierung.

Enger **PendingPickerHandoff**: nur URI + opaque Ziel-actionId in RAM beim
Activity-eigenen Pickeradapter, niemals DB/SavedState/Preferences, keine
persistable permission, Key/StorageContext/Sessiongenerationfreigabe. Kein
Dokumentname/Provider-Metadatencache aus dem gesperrten Callback.
Einmaliger angeforderter Pickerresultat-Capture ist die einzige Ausnahme zur
RAM-Navigationbereinigung, keine Ausnahme zur Inhalts- oder Sessionberechtigung.

Nutzer startet regulär neue Authentifizierung → MAINTENANCE → neues READY.
Dann bewusst **„Import fortsetzen“** für exakt dieselbe noch zulässige Action;
erst jetzt URI-Metadaten/Quelle lesen, Namen/Format vorbereiten und bewussten
Import bestätigen. Zwischen „Fortsetzen“ und Importbestätigung noch kein Job.
Neue Operation mit neuem StorageContext; alten Callback/EOF-Evidence nie fortsetzen.
Bei Actionwechsel/falschem Ziel, Abbruch, fehlgeschlagener Auth, Process Death/
Activity Recreation oder weiterem App-Verlassen Handoff verwerfen. Der ausdrücklich
gestartete reguläre Systemauthdialog darf das wartende Handoff ohne Inhaltlesen
kurzzeitig erhalten, damit keine Picker/Reauth-Endlosschleife entsteht; er erzeugt
keine READY-Foregroundausnahme. Keine automatische Fachnavigation nach Reauth.

Lifecycle-Unterscheidung des einen erwarteten Pickerroundtrips gegenüber anderem
App-Verlassen gehört zum fokussierten P10b2-Handoffnachweis. Nur das passende
ausstehende Pickerrequest darf eine neue RAM-Kandidatur liefern; spätere doppelte/
abgebrochene Resultate verwerfen. Nach erfolgreichem Import/startender verbrauchender
Operation Handoff löschen. Ungültiger URI-Zugriff nach Reauth: verständlich neu
auswählen, kein persistentes URI-Recovery oder blindes Retry.

## 13. Session-, Commit- und Publicationgrenzen

Normaler Zugriff READY; Recovery nur MAINTENANCE. OPERATION vor Start, STEP vor/
nach relevanten DB-/Stream-/Move-/Unlinkschritten, COMMIT für jede persistente
Phase und finalen Metadatencommit, Publication erst nach Ressourcenclose und
erneuter Generation-/Action-/Request-/Deadlineprüfung. Persistenter Job ist
Arbeitsabsicht, keine über Sessionende hinaus nutzbare Zugriffserlaubnis.

Revoke vor zulässiger Phase: keine neue Phase/fachlicher Erfolg. Bereits
zugelassener physischer close/sync/move/unlink kann technisch enden; danach keine
neue autorisierte Phase. Wiederanlauf beurteilt die tatsächlich beobachtbare
Dateilage nach obigen Regeln. DB-Commit vor Revoke kann bestehen bleiben,
aber keine stale Publication, automatische Öffnung oder behaupteter Rollback.
Nach Reauth Bestand neu lesen/reconciliieren, nicht blind gegen URI wiederholen.
Alte Workerressourcen kontrolliert beenden, keine Deadlines verlängern.

## 14. Viewer und verbleibende Grenzen

Dieses Design betrifft nur Storage/Import/Delete plus minimale Projektionen und
Handoff. **Kein PDF-Viewer**, kein Text-/JPEG-/PNG-Viewer in P10b1/P10b2 durch
diesen Vertrag freigegeben. P10a-Text-/Bildviewergrenzen bleiben einem nachfolgenden
kleinen Viewer-Slice vorbehalten, PDF bleibt separate P10c-Frage. Keine externen
Viewer, Klartexttempfiles, Export, Medienaufnahme, neue Auth-/Securityarchitektur,
Dependency/Permission oder Retentionengine. P10a-Originalquelle bleibt bestehen;
Person-/P3b-/P6-/P9-Beziehungen weiterhin ausgeschlossen.
Power-loss/OEM-Durability, RAM-/Flashreste und allgemeiner Actionlöschworkflow
bleiben ehrliche Grenzen, keine durch Dokumentation bestandenen Tests.

## 15. Fokussierte spätere P10b2-Abnahme

Nur synthetische Daten. Keine ständige Wiederholung der 874+-Gesamtsuite,
B1–B4-Großmatrix oder aller historischen Galaxy-Probes. Gezielt:

- Domain: Name/Codepoints, vier Formate, 100/25/250-Grenzen, Lifecycle,
  Summarysichtbarkeit und Konkurrenzreservierung.
- Crypto/File: vorhandener FileStreamingCipher Roundtrip, falsche AAD,
  Truncation/später Tagfehler, Streamgrößenlimit, strikt UTF-8/Signatur,
  kein Klartexttempfile; keine neue allgemeine Crypto-Testkampagne.
- Eine additive Migration vom Schema 10 zum späteren Implementierungsstand,
  bisherige Entity-/Triggerdefinitionen unverändert; keine Schemanummer hier.
- Eine synthetische Import/List/Delete-Smoke-Probe inklusive Handoff/Lock/Reauth,
  COMPLETED-No-Prefetch und ACTIVE-Count-only. Keine Viewerprobe nötig.

**18 fokussierte Storage-/Recoveryfälle** (Varianten gebündelt, keine Matrixexplosion):

| Nr. | Fall / zentrale Assertion |
| --- | --- |
| 1 | Normalimport: unveränderte Bytes, vollständiges EOF, atomarer Row/Jobwechsel |
| 2 | Providerabort/Grenzfehler: WRITING, kein READY/Quellretry |
| 3 | RESERVED restart: ohne Datei verwerfen, mit unerwarteter Datei blockieren |
| 4 | WRITING restart: vorhandenes/fehlendes bekanntes Staging cleanup, nie publish |
| 5 | CLOSED restart: vollständige erneute Prüfung, nicht Phase vertrauen |
| 6 | Rename vor Phasencommit: final-only vollständig prüfen/anerkennen |
| 7 | Rename nicht erfolgt: staging-only prüfen/publish; both/neither blockiert |
| 8 | FILE_PUBLISHED restart: validieren, genau ein Metadatencommit |
| 9 | CLOSED/PUBLISHED beschädigt: BLOCKED, keine stille Entfernung |
| 10 | Delete reserve: alle Projektionen hidden, Job ohne DEK |
| 11 | Parentdelete + PENDING atomar, Live-DEK entfernt, Zielreferenz erhalten |
| 12 | PENDING vor/nach unlink: wiederholen/fehlend idempotent, Job zuletzt löschen |
| 13 | Unknown Blob/Symlink/Doppelbindung/Assetmissing: blockiert, unangetastet |
| 14 | DB unavailable: keine Filemutation/Ersatzinitialisierung |
| 15 | Revoke vor Phase/COMMIT: kein neuer persistenter Schritt oder Success |
| 16 | Commit erfolgreich, Revoke vor Publication: kein Feedback, Reopen realer Bestand |
| 17 | Doppelte Reconciliation/alte Worker: idempotent, keine Wiedererzeugung |
| 18 | Parallele Reservierung/RESTRICT/Roomrefresh: Grenzen, Actionziel erhalten, typisierter Widerruf statt Prozesscrash |

Vor finalem Merge höchstens ein kompakter Regressionlauf der tatsächlich berührten
Module/Pfade; zusätzliche Tests nur bei konkretem neuen Fehler/Upgradebefund.
Echte harte Abbrüche/Backup/OEM-Nachweise bei Bedarf separat, nicht als automatisch
durch Fake-Recovery bewiesen ausgeben. **In P10b1 wurden keine Tests, Builds, Lint,
Instrumentation oder ADB-/Galaxy-Prüfungen ausgeführt.**

## 16. Dokumentationsabnahme

P10a/B3/B4 und alle Implementierungsdateien bleiben unverändert. Ausschließlich
Markdown: dieser Vertrag und notwendige Querverweise/Präzisierungen in Architektur,
Security, Datenmodell, Testing und Roadmap. Schema bleibt 10. Selbstprüfung mit
`git status -sb`, Markdown-/Diffreview und `git diff --check`, keine Buildprüfung.
Tatsächlicher Abschluss: sechs Markdown-Dateien neu/geändert; P10a, B3/B4,
Kotlin, Schema, Gradle, Manifest, Dependencies und Permissions unverändert.
Baselinebranch/HEAD unverändert, `git diff --check` ohne Befund. Keine Tests,
Builds, Lint, Instrumentation oder ADB-/Galaxyprüfungen ausgeführt.
Offen sind die P10b2-Implementierung mit fokussierten Nachweisen, konkrete
Move-/Durability-Plattformprüfung sowie die getrennten Viewer-/Retention- und
allgemeinen Löschworkflows. Kein Securityblocker im gezielt gelesenen Vertrags-
und Codeumfang; keine bereits bestandene Produktintegration behauptet.
Kein Commit. Kein Push.

A) P10b1 STORAGE/RECOVERY DESIGN GRÜN – BEREIT FÜR REVIEW
