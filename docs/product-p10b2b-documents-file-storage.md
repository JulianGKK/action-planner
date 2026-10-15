# P10b2b — verschlüsselte Dokument-Dateiprimitiven

## Stand und Umfang

Baseline: Branch `p10b2b-documents-file-storage`, HEAD `9b4652e`
(P10b2a-Merge), vor Änderungen sauber. Produktbestand weiterhin Schema 11.
Verbindlich bleiben [P10a](product-p10a-documents.md),
[P10b1](product-p10b1-documents-storage-design.md) und
[P10b2a](product-p10b2a-documents-db-foundation.md).

Implementiert sind ausschließlich interne Primitive in `:data` und fokussierte
Hosttests. Es gibt keinen Produktanschluss, keine neue fachliche Dokument-Row,
keine DB-Phasenänderung, Recovery, Rootanlage oder automatische Publication.

## Komponenten und Kryptografiegrenze

`DocumentFileStreams` übernimmt bereits geöffnete Streams und schließt sie auch
bei Fehlern oder früher Zugriffsverweigerung. Der spätere Koordinator muss deren
sichere Beschaffung und vorherige autorisierte DB-/Jobkoordination gewährleisten.
Die Primitive öffnen selbst keinen schreibbaren Produktpfad.

Wiederverwendet wird der unveränderte `FileStreamingCipher` mit Tinks
`AES256_GCM_HKDF_1MB`. Aus B3 werden Streaming, opaque AAD, vollständige
EOF-/Längenverifikation und Session-Gates übernommen; nicht übernommen werden
Prototyp-Keyverwaltung oder Klartextconsumer. B4 bleibt Grundlage für die erst
später zu implementierende Koordination; keine Room-Instanz wird hier geöffnet.

`withNewKey` erzeugt mittels `SecureRandom` unabhängige 32-Byte-DEKs im
kontrollierten Callback-Scope. Die Cipheroperation verwendet eine zusätzliche
kurzlebige Kopie. Kontrollierte Schlüsselkopien und Arbeitsbuffer werden im
`finally` bestmöglich überschrieben. Aufrufer dürfen Schlüssel nicht aus dem
Scope herausreichen; spätere Persistenz erfolgt ausschließlich gemäß P10b1 in
SQLCipher. Hier gibt es keine Keypersistenz, Keydatei, Tink-Keysetpersistenz,
zusätzlichen Keystore-Alias, Authpfad, Key-Owner oder neue Schlüsselableitung.
AAD ist ausschließlich die kanonische opaque documentId als UTF-8.
Tink/JVM können weitere nicht kontrolliert überschreibbare Kopien halten;
physische RAM-Löschung wird nicht zugesichert.

## Streaming und Formatprüfung

Die Schleifen verwenden feste 32-KiB-Arbeitsbuffer; der inkrementelle Textdecoder
benutzt ebenfalls begrenzte Buffer. Hinzu kommen bestehende Tink-Segmentbuffer.
Es wird keine vollständige Datei in RAM geladen. Verschlüsselung zählt tatsächliche
Klartextbytes und liest höchstens das erste Byte oberhalb von 25 MiB, bevor sie
abbricht. Unvollständiger Ciphertext darf danach bestehen bleiben, gilt aber
niemals als Dokument oder erfolgreicher Import.

Verifikation entschlüsselt bis zum echten EOF, authentifiziert sämtliche Segmente
und verlangt exakt die erwartete Klartextlänge innerhalb des Limits. Exceptions,
Null-Reads, Truncation und Close-Fehler ergeben keinen Erfolg. Es gibt keinen
Klartextconsumer und keine externe Klartextübergabe.

- PDF: `%PDF-` ab Byte 0.
- JPEG: `FF D8 FF` ab Byte 0.
- PNG: exakte acht Byte PNG-Signatur.
- TEXT: gesamter Inhalt strikt inkrementell als UTF-8, ohne Replacement.
  NUL, C0-Steuerzeichen außer TAB/CR/LF sowie DEL werden abgelehnt.
  Anfangs-BOM und leerer Text sind zulässig. Originalbytes bleiben unverändert.

Binärsignaturen sind keine vollständige PDF-/Bildparserprüfung und keine
Malwaregarantie. Es gibt keine Viewer, Rekodierung, EXIF-Auswertung oder
Klartexttempfiles.

## Namespace und Inventar

`DocumentFileInventory` erhält später den vertrauenswürdigen `noBackupFilesDir`
und untersucht nur `pag-documents-v1/staging/` und `pag-documents-v1/files/`.
Es erstellt, verschiebt, überschreibt und löscht nichts. Ein fehlender Root ist
nur bei leerem erwarteten Inventar zulässig; ein partieller Root wird abgelehnt.

Dateinamen müssen kanonische opaque UUIDs ohne Extension sein. Getrennte,
disjunkte Namensmengen müssen später aus einem autorisierten konsistenten
DB-/Jobbestand kommen. Unbekannte Dateien, Unterverzeichnisse, Symlinks,
Traversal oder widersprüchliche Namen werden abgelehnt, niemals bereinigt.
Vorhandene Dateien müssen Teil der bekannten Menge sein. Fehlende einzelne
bekannte Artefakte werden als Inventarergebnis sichtbar: Ob ihr Fehlen zur
Jobphase passt oder BLOCKED erfordert, entscheidet erst P10b2c.

Kanonische Pfade, Normalisierung und `NOFOLLOW_LINKS`-Attribute werden geprüft.
Bekannter Ciphertext wird ausschließlich mit `READ` und `NOFOLLOW_LINKS`
geöffnet; Datei-/Verzeichnisattribute und Inventar werden anschließend erneut
geprüft. IO-/Zugriffsfehler gelten nicht pauschal als fehlende Datei.
Diese Kontrollen ersetzen keine spätere Serialisierung des privaten Namespaces.
Sie sind keine Garantie eines universell racefreien Mehrkomponenten-Opens gegen
feindliche gleichzeitige OS-/Root-Manipulation. Reales Android-/Dateisystemverhalten
von NIO/No-Follow ist in diesem Slice nicht auf einem Gerät geprüft.

Unbekannte Bestände liefern `ProductStorageFailure`; erst der spätere
Koordinator überführt dies in den bestehenden BLOCKED-Pfad. Es gibt weder
Orphan-Cleanup noch eigenständigen Sessionzustand.

## Sessiongrenzen und technisches Ergebnis

Bestehender `StorageContext`, OPERATION und STEP werden vor/nach relevanten
Schritten und an Stream-/Verifikationsgrenzen geprüft. Kontext und Generation
bleiben gebunden. Revoke schließt Streams kontrolliert, liefert keinen Erfolg
und ruft keine Publication auf. Physischer Stream-Close darf noch stattfinden;
daraus entsteht keine neue Autorisierung. File-I/O zählt nicht als Interaktion.

`DocumentVerification` enthält nur technische Metadaten und Kontextbindung,
keine Klartextbytes, Keys oder offenen Streams. `requireCurrent` verwirft einen
anderen Kontext oder eine andere/ungültige Generation. Das Ergebnis ist weder
Commitrecht noch dauerhaft gültiger Dateibeweis: Die spätere Koordination muss
Inventar, Phase und Commit-Gate erneut prüfen.

Erwartete Revoke-Fehler bleiben typisiert. Andere Exceptions werden ohne
ursprüngliche Causes/Suppressed-Providerdiagnosen in den bestehenden neutralen
Storagefehler übersetzt. Es gibt keine Inhalts-, Pfad- oder Keylogs.

## Tatsächliche Prüfung

Fokussierter Offline-Hostlauf:

```sh
./gradlew :data:testDebugUnitTest --tests "*DocumentFileStreamsTest" --tests "*DocumentFileInventoryTest" --offline
```

Die Testgruppe deckt unveränderte Bytes, falsche AAD/Keys, Truncation, späten
Segmentfehler, Längenfehler, exakt 25 MiB und unmittelbare Überschreitung,
Signaturen, UTF-8 über Buffergrenzen, verbotene Steuerzeichen, IO-/Close-Fehler,
Revoke während Verschlüsselung/Verifikation, Schlüsselkopien und fehlende
Publication ab. Inventartests verwenden ausschließlich isolierte Testverzeichnisse
und prüfen fehlenden/partiellen Root, unbekannte Einträge, Traversal, Symlinks,
lesende Verifikation sowie vollständige Verifikation einer 25-MiB-Testdatei.
Keine Klartextdatei wird erzeugt. Test-Fixtures erzeugen ausschließlich Ciphertext
mit `CREATE_NEW`; das Produkt enthält keinen Dateierzeuger.

Ein erster Lauf fand einen JUnit-Testmethodenfehler (Rückgabetyp statt Unit),
der korrigiert wurde. Der anschließende Lauf war grün; der finale Lauf ergänzt
zusätzlich Revoke während Verifikation: **21 Tests grün**, davon 13 Streamtests
und 8 Inventartests, 0 Failures, 0 Errors, 0 Skips. Gradle: BUILD SUCCESSFUL.
`git diff --check` und die zusätzliche Whitespaceprüfung aller neuen Dateien
sind ohne Befund. Alle fünf Änderungen sind neue Dateien im vereinbarten Scope.

Keine historische Gesamtsuite, B1–B4-Matrix, Instrumentation, Galaxy-/ADB-Prüfung,
Lint oder App-Build ausgeführt. Gradle kompilierte die für diese Hosttests
notwendigen Quellen. SQLCipher-/Schema-Implementierung und Securitymodul bleiben
unverändert.

## Übergabe an P10b2c und offene Nachweise

P10b2c muss autorisierte DB-/Jobreservierung, private Rootanlage und sichere
exklusive Streambeschaffung vor jeder persistenten Mutation, Phasenübergänge,
sync, Rename/Publish, Delete, restartfähige Reconciliation, Commit-/Publication-
Gates und koordinierte Inventarsnapshots implementieren. Fehlende erwartete
Artefakte sind dann strikt phasenabhängig zu bewerten. Keine externe URI darf
für Recovery erneut gelesen werden. Ein Ciphertext allein ist nie READY.

Reale Android-No-Follow-/Backup-/Durability-Eigenschaften sowie diese Koordination
sind offen. SAF-Handoff, Runtimeanschluss, UI und Viewer sind nicht implementiert.
PDF bleibt ohne Viewer. Keine neue Dependency, Permission, Manifeständerung,
Securityarchitektur oder Secure-Erase-Garantie.

## Geänderte Dateien

- `data/src/main/kotlin/org/pag/data/product/DocumentFileStreams.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentFileStreamsTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentFileInventoryTest.kt`
- `docs/product-p10b2b-documents-file-storage.md`
