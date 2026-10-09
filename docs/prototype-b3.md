# Prototyp B3: verschlüsselte synthetische Streams

## Status und Forschungsfrage

Stand: 2026-10-08. **BESTANDEN IM GETESTETEN FAIRPHONE-/API36-UMFANG**.
Der manuelle Initialisierungs-/Force-stop-Restart-Nachweis und der anschließend
vollständig erneut ausgeführte B1–B3-Regressionslauf sind erfolgreich. Dies ist
keine allgemeine Android-/OEM-Garantie, keine endgültige Produkt-Key-Hierarchie
und kein Nachweis fertiger Produkt-FileAssets.

Forschungsfrage: Kann ein separater persistenter File-Key über den bewiesenen
per-operation DEVICE_CREDENTIAL-/RSA-OAEP-Pfad entpackt und ausschließlich im
RAM als Tink-Streaming-AEAD-Schlüssel verwendet werden? Können vollständige
Dateiverifikation, Publication und Klartextfreigabe den zentralen Session-Vertrag
einhalten? Grundlage: [architecture.md](architecture.md), [security.md](security.md),
[threat-model.md](threat-model.md), [privacy.md](privacy.md) und die unveränderten
Nachweise B1/B1.5/B1.6/B2.

Nicht enthalten: Produktdateien/-repositories, Room-FileAsset-Metadaten,
SAF-Produktimport, Kamera, Viewer, Teilen, Content-/FileProvider, Recovery,
Rotation oder das vollständige Import-/Delete-/Wiederanlaufprotokoll aus B4.
Es gibt keine Netzwerkfähigkeit, Telemetrie, Notifications oder neue Permission.

## Offizielle Quellen und stabile Baseline

Vor Dependency-Änderung geprüft: **Tink Android 1.23.0**, offizielle stabile
Java-/Android-Linie, Release 2026-07-09. Die vorhandene Toolchain und übrigen
Dependencies bleiben unverändert; insbesondere SQLCipher 4.19.1, Room 3.0.3,
AndroidX SQLite 2.7.1 und KSP 2.3.12. Keine Prerelease-Komponente.

- [Release 1.23.0](https://github.com/tink-crypto/tink-java/releases/tag/v1.23.0)
- [Java-/Android-Setup](https://developers.google.com/tink/setup/java)
- [Streaming AEAD und Parameterempfehlung](https://developers.google.com/tink/streaming-aead)
- [AES-GCM-HKDF-Streaming-Spezifikation](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming)
- [Key-Objekte](https://developers.google.com/tink/design/key_objects)
- [Öffentliche Streaming-Key-API 1.23.0](https://github.com/tink-crypto/tink-java/blob/v1.23.0/src/main/java/com/google/crypto/tink/streamingaead/AesGcmHkdfStreamingKey.java)
- [Vordefinierte Parameter 1.23.0](https://github.com/tink-crypto/tink-java/blob/v1.23.0/src/main/java/com/google/crypto/tink/streamingaead/PredefinedStreamingAeadParameters.java)
- [KeysetHandle 1.23.0](https://github.com/tink-crypto/tink-java/blob/v1.23.0/src/main/java/com/google/crypto/tink/KeysetHandle.java)
- [Decrypting-Stream 1.23.0](https://github.com/tink-crypto/tink-java/blob/v1.23.0/src/main/java/com/google/crypto/tink/subtle/StreamingAeadDecryptingStream.java)
- [Ausgeschlossener AndroidKeysetManager 1.23.0](https://github.com/tink-crypto/tink-java/blob/v1.23.0/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java)

Die Setup-Seite nennt Android-Unterstützung ab API 24, passend zum vorläufigen
Prototyp-Minimum 30. Dies ersetzt keinen API-30-Gerätetest. Der Seitenhaupttext
und Release sind maßgeblich; eine teilweise veraltete automatisch erzeugte
Seitenzusammenfassung ist keine Versionsquelle. Keine Übernahme interner/private
Tink-APIs und keine Reflection.

## Key-Management und Verantwortlichkeiten

Eigener Alias `pag.prototype.b3.file.v1`, eigener reservierter Namensraum
`noBackupFilesDir/b3-v1`. Bestehende p15/p16/B2-Fixtures werden nicht verwendet,
migriert oder bereinigt. Der trusted Android-Base-Pfad wird kanonisiert, der
reservierte Kindpfad und dessen Einträge bleiben gegen Symlinks geprüft.

Der unveränderte `RsaWrappingFixture` erzeugt einen separaten kryptografisch
zufälligen 32-Byte-Key und verschlüsselt ihn mit dem öffentlichen RSA-Teil.
RSA 2048/OAEP, SHA-256/MGF1-SHA-1, standardisierte JCA-Parameter und die
PRIVATE-DECRYPT-Policy bleiben genau der B1.5-/B1.6-Nachweispfad. Nur
DEVICE_CREDENTIAL, timeout 0/per-operation, derselbe authentifizierte
Cipher/CryptoObject; kein Zeitfenster-Fallback. Keine eigene KDF oder neue
Wrapping-Konstruktion. DB-Key und File-Key sind unabhängig erzeugte Bestände.

Erst nach erfolgreichem explizitem Prompt und Zulassung des MAINTENANCE-Kontexts
führt der kurze synchrone Key-Consumer das Unwrap aus. :security verwendet
öffentliche `AesGcmHkdfStreamingKey.create`, `SecretBytes.copyFrom`,
`KeysetHandle.importKey`/Builder und den nichtdeprecateten
`getPrimitive(RegistryConfiguration.get(), StreamingAead.class)`-Pfad.
`@AccessesPartialKey` kennzeichnet die bewusst geprüfte Importstelle; der rohe
Key-Zugriff ist für die vorhandene RSA-Verpackung erforderlich.

**Kein Tink-Keyset wird persistent gespeichert.** Der KeysetHandle existiert
kurzzeitig ausschließlich im RAM. Kein AndroidKeysetManager, Keyset-Serializer,
SharedPreferences-Keyset, AndroidX Security Crypto oder paralleler Unlock-State.
Die Tink-Registry registriert Algorithmen; sie ist keine globale File-Key-Registry.

Die kontrollierten Unwrap-Bytes werden durch die bestehende Fixture nach dem
Consumer bestmöglich überschrieben; Arbeitsbuffer ebenfalls. Primitive-/Key-
Referenzen werden nach Ressourcenabschluss verworfen. SecretBytes/Tink/JCA/JVM
erzeugen zusätzliche Kopien. Vollständiges oder augenblickliches Entfernen aller
JVM-/nativen/Provider-Kopien wird ausdrücklich nicht garantiert. Keine Keys in
Strings, UI, Logs, SavedState, Preferences, DataStore oder langlebigen ViewModels.

## Streaming-Parameter, AAD und EOF

Vordefiniert: **AES256_GCM_HKDF_1MB**. Initialer Key 32 Bytes, abgeleiteter
AES-Key 32 Bytes, HKDF-SHA-256 und Ciphertextsegmente 1.048.576 Bytes, vollständig
von Tink vorgegeben. Die offizielle Empfehlung nennt die 1-MiB-Varianten;
AES256 passt zum unveränderten zufälligen 256-Bit-Wrapping-Payload. HKDF,
Nonce/IV/Tag, Segmentzählung und Verschlüsselungsheader implementiert nur Tink.
Keine eigenen Crypto-Header, Chunkformate oder manuell erzeugten Protobufs.

Asset-AAD ist ausschließlich eine zufällige opaque synthetische UUID. Die
verschlüsselte technische Metadatei verwendet eine feste öffentliche opaque
synthetische Manifest-UUID als AAD. **AAD wird authentifiziert, nicht
verschlüsselt**; keine fachlichen Namen, Personen-, Aktion- oder MIME-Angaben.
Verschiedene Asset-ID/AAD verhindern unbemerkte Übernahme von Ciphertext A als B.
Eine vollständige Wiedereinspielung eines früher gültigen Bestands ist damit
nicht generell erkannt; Anti-Rollback gegen externe Bestandsmanipulation ist
kein hier nachgewiesener Schutz.

Streaming AEAD authentifiziert segmentweise. Gültige frühe Segmente können
bereits Klartext liefern, bevor ein Fehler in einem späteren Segment sichtbar
wird. Der explizite Partial-Plaintext-Test prüft genau diesen legitimen Effekt.
Ein gültiger Anfang, stream.close() oder bloße Dateiexistenz beweist keine
Vollständigkeit. Nur normales echtes EOF nach vollständiger Entschlüsselung
aller Segmente und passender Länge erlaubt ein Verifikationsergebnis.
IOException/Decryption-/Integrity-Fehler werden niemals als EOF behandelt.

## Datei-, Inventar- und Publication-Vertrag

| Artefakt | Persistenter Inhalt |
|---|---|
| `0` | 1-Byte technische Initialisierungsabsicht, kein historischer Erstinstallationsbeweis |
| `k/0` | B1.5-technischer Inventarmarker |
| `k/1` | RSA-OAEP-geschützter File-Key; niemals Klartext |
| `k/2` | Öffentliche X.509-RSA-Keykodierung |
| `s/<zufällige UUID>` | Standard-Tink-Ciphertext des synthetischen Streams |
| `m` | Standard-Tink-Ciphertext der kleinen technischen Asset-Metadaten |

Die Metadatei enthält ausschließlich nach Entschlüsselung lesbare opaque Asset-ID,
opaque physischen Dateinamen, erwartete Länge und PUBLISHED-Status sowie eine
technische Payload-Version. Standard-Java-DataStream-Kodierung innerhalb der
Tink-Verschlüsselung ist Anwendungspayload, kein eigener AEAD-/Keyset-Container.
Physische Dateinamen und Filegröße/-anzahl/-zeitpunkte bleiben Metadatenleaks des
Dateisystems; es gibt kein Padding- oder Auffindbarkeitsversprechen.

EMPTY verlangt den vollständig leeren reservierten Namensraum und fehlenden
Alias. Nur der ausdrückliche Initialisierungsklick reserviert ihn. Marker,
Alias, Keyteile, Staging, unbekannte Datei oder sonstiger Teilbestand verhindern
einen stillen Neuanfang. Kein Ersatzalias/-key und keine automatische Bereinigung.

Der autorisierte Erstlauf durchläuft KEY_READY -> STAGING -> VERIFYING ->
vollständig verifiziert -> PUBLISHED. Diese Zwischenzustände gehören zum aktuellen
Versuch, nicht zu einer fertigen B4-Recovery-State-Machine. Nach Process Death
bleibt ein unvollständiger Versuch BLOCKED; kein automatisches Resume/Publish.

Ein vollständiges strukturelles Inventar heißt **COMPLETE_CANDIDATE**, nicht
bereits kryptografisch bestätigt PUBLISHED. Erst Manifest-Entschlüsselung,
Payloadprüfung und volle Asset-Verifikation erlauben einen Restart-Erfolg.
Unknown/fehlende/beschädigte Artefakte werden nicht neu erzeugt oder ersetzt.
COMPLETE_CANDIDATE + Init ist NOT_EMPTY ohne Mutation.

### Write, Validate, Publish und Read

1. Zentralen MAINTENANCE-Kontext prüfen; File-Key explizit entpacken.
2. Opaque Asset-ID/-dateiname erzeugen; ausschließlich verschlüsselt nach `s/`
   schreiben. Quelle wird im RAM generiert; keine Source-/Klartext-Tempdatei.
3. Vor/nach relevanten Quell-/Verschlüsselungsschritten prüfen; eigenen 32-KiB-
   Klartextbuffer bestmöglich überschreiben. Tink benötigt zusätzlich segmentgroße
   Puffer; keine ganze große Datei im App-Arbeitsspeicher des Nutzpfads.
4. Encrypting-Stream vollständig schließen. close kann einen letzten Segment-/
   Tagabschluss physisch schreiben, auch nach Widerruf während Cleanup.
5. Gesamten Ciphertext in einem zweiten Durchlauf bis EOF entschlüsseln,
   Klartext unmittelbar verwerfen und Länge prüfen.
6. Nur an den aktuellen Owner/Quellzugriff gebundenes EOF-Ergebnis erlaubt die
   erneute Commit-Zulassung. Erst danach exklusiv die verschlüsselte Metadatei
   schreiben; kein Überschreiben. Kein persistenter Klartext-Content-Hash.
7. Metadatei vollständig prüfen und Asset nochmals verifizieren; kontrollierter
   interner synthetischer Consumer liest anschließend in einem weiteren Durchlauf.
8. Ressourcen schließen, Key-Consumer beenden; Init endet LOCKED, Reopen erst
   danach READY mit einer festen nicht-sensitiven Testanzeige.

Read-Modi: volle Verifikation ohne Klartextveröffentlichung, dann kontrolliertes
internes Streaming desselben privaten Quellzugriffs. Mindestens doppelte I/O;
der Prototyp prüft zusätzliche Passes bewusst ohne Performanceoptimierung.
Der zusätzliche Read authentifiziert erneut segmentweise. Gleichzeitige externe
Manipulation zwischen Passes ist keine technisch ausgeschlossene Möglichkeit
auf kompromittiertem OS; die Dateien sind im normalen Modell privat appverwaltet.
Der interne Consumer hält keinen Buffer fest und verarbeitet nur begrenzte
synthetische Vergleiche, keine Vorschau oder fachliche Inhalte.

## Session, Generation, Close und Crash-Grenzen

Keine zweite State-Machine. SessionCoordinator/StorageContext bleiben Autorität:
State, Generation, Foreground, unveränderliche absolute Deadline, in READY auch
Inaktivität. B3 arbeitet in MAINTENANCE; dessen absolute Zeit läuft weiter.
Prüfung vor Unwrap/Stream, vor/nach relevanten Chunks, vor Commit und Publication.
Die kurze Klartextübergabe an den synthetischen Consumer wird mit Invalidation
serialisiert; kein Stream-I/O liegt im Coordinator-Monitor. Kein beliebiger
lang laufender Consumer in diesem Callback.

Alte Stream-Owner/EOF-Ergebnisse sind an ihren alten Kontext gebunden und nach
Lock/Deadline/Generationwechsel ungültig. Die bloße Existenz eines Tink-Primitive
oder Key-ByteArrays ist kein Zugriffsrecht. Timer/I/O/Verifikation verlängern
keine absolute Deadline. Home/Display-Lock/Activity Recreation/Process Death
behalten die B0-Regeln; kein automatischer Auth-Prompt oder Session-Revival.

Wird während Encrypt-close widerrufen, kann Cleanup physisch fertig werden,
aber Validierung, Commit-Zulassung und Veröffentlichung werden verweigert.
Staging ist weiterhin untrusted. Läuft eine Deadline erst nach zulässiger
Commit-Zulassung während physischer Manifest-/Flush-Arbeit ab, kann diese Arbeit
enden; danach keine neue Arbeit oder Erfolgspublikation. Keine Atomizität von
Uhr/Keystore/Filesystem, keine exakte physische Abbruch- oder Power-loss-Garantie.
Ein vollständiger vorhandener Bestand muss nach Neustart neu authentifiziert und
vollständig geprüft werden; Dateien werden bei unklarer Lage niemals gelöscht.

## Tests und Evidenz

### Automatische Unit-Tests

Deterministische Fake-Zeit: alle B3-Arbeitspunkte vor/nach Unwrap, Stream-Start,
Quelllesen, Encrypt, close, Vollverifikation und Publish gegen Deadline und neue
Generation. Zusätzlich Foreground-/Display-Lock, alte Owner, spätes Auth-/Gate-
Ergebnis, paralleler Prompt, Abbruch, Init -> LOCKED, Reopen -> READY erst nach
Gate-Abschluss und EMPTY/COMPLETE/Teilbestand ohne Reparatur/Delegate-Aufruf.

Echte Tink/JCA-JVM-Tests: Key-/Parameters-Konstruktion und Roundtrip, EOF-/Länge,
falscher Key/AAD, Partial-Plaintext-Hazard, keine Consumer-Bytes nach Widerruf und
kein Publish nach Widerruf. Der Android-Keystore-/Systemdialog wird damit nicht
simulativ als bestanden erklärt. Final: **182 Unit-Tests bestanden**, 0 failures,
0 errors, 0 skipped; davon **37 neue B3-Tests** und alle bisherigen 145.
Zusätzlich geprüft: EOF-Ergebnis eines fremden Owners wird verworfen, früher
Zugriffsfehler schließt beide Streams, und close kann physisch finalisieren,
ohne nach Session-Widerruf neue Verifikation/Publication zu erlauben.

### Instrumentation-Matrix

Echte Android-Tink-/Dateistreams; positive Key-Consumer bleiben synthetische
Fakes mit flüchtigen zufälligen Keys. Eigene UUID-Fixtures im Test-APK, keine
Haupt-App-Bestandsmutation. Kein automatisches Credential-Injecting.

| Fall | Erwartung |
|---|---|
| Header/erstes/mittleres/letztes Segment Bitflip | Keine volle Verifikation oder Publication |
| Header/erstes Segment/nahe EOF truncation, letztes Segment entfernt | Fehler statt EOF-Erfolg |
| Angefügte Bytes, Segmenttausch, Segmentduplikat | Keine Publication |
| Ciphertext unter fremder opaque ID/AAD, falscher File-Key | Integrität nicht bestätigt |
| Leer, 1 Byte, unter/exakt/über erster Klartext- und 1-MiB-Grenze | Voller Roundtrip mit passender Länge |
| Mehrsegmente und 32 MiB + 17 Bytes | Begrenzter Streaming-Nutzpfad, volle EOF-Verifikation |
| Später Fehler | Frühe Klartextsegmente möglich, trotzdem kein Publish |
| Widerruf an allen relevanten Grenzen | Kein Publish-/Ergebnis, Staging nicht automatisch fortgesetzt |
| Alter Owner | Keine Klartextausgabe nach Widerruf |
| Unbekannte/fehlende Datei | BLOCKED, keine Ersatzdatei/Reparatur |
| Neue Instanz, derselbe Ciphertext und Testkey | Reopen erfolgreich ohne Mutation; kein echter Force-stop-Beweis |
| Neuer per-operation RSA-File-Alias ohne Prompt / fremder Alias | Kein automatisches Unwrap oder Ersatzalias |

Ein Crypto-/Decryption-Fehler unterscheidet nicht zuverlässig falschen Key,
falsche AAD, Truncation, Manipulation oder manchen Read-Fehler. Runtime meldet
deshalb konservativ B3-INTEGRITY-FAILED bzw. B3-LENGTH-MISMATCH. Die feineren
TRUNCATED/AAD-MISMATCH/WRONG-KEY-IDs sind reservierte bekannte Fault-Szenarien,
keine aus Exceptiontext erfundene Diagnose. Analog wird ungültiger Kontext als
SESSION-EXPIRED behandelt; STALE-GENERATION ist durch deterministische Tests
belegt, nicht aus einer ByteArray-/Handle-Existenz abgeleitet.

### Fairphone und Emulatoren

Verbunden: Fairphone 6 / Android 16 / API 36. Nach den letzten B3-Review-
Korrekturen wurde am 2026-10-08 die **vollständige** Suite erneut mit
`:data:connectedDebugAndroidTest` ausgeführt, ohne Klassen-/Methodenfilter.
Ergebnis im finalen XML-Report: **104 Tests bestanden**, **0 failures,
0 errors, 0 skipped**. Laufdauer laut Gradle: 34 min 59 s.

| Suite | Bestandene Instrumentationtests |
|---|---:|
| B1 / SqlCipherEngineTest | 16 |
| B1.5 / RsaWrappingTest | 7 |
| B1.6 / B16IntegrationTest | 16 |
| B2 / B2ComparisonTest | 56 |
| B3 / B3StreamingTest | 9 |
| Gesamt | 104 |

Damit ist auch der vollständige 95-Test-Regressionsanteil nach den B3-Review-
Korrekturen erneut geprüft; der frühere isolierte 9-Test-Lauf wird nicht als
Gesamtnachweis verwendet. Keine Tests oder Sicherheitsassertions wurden für
diesen Abschluss geändert. API 30 / API 37 waren nicht verbunden und bleiben
offen; keine AVD-Neukonfiguration.

Erneut erfolgreich: `./gradlew test`, `:app:assembleDebug`,
`:app:lintDebug :data:lintDebug :security:lintDebug`,
`verifyArchitecture :app:verifyPrototypeManifest`, `:data:assembleDebugAndroidTest`.
Zusätzlich wurden mit `test --rerun-tasks` alle **182 Unit-Tests frisch ausgeführt**:
0 failures, 0 errors, 0 skipped; 37 B3-Tests und 145 bisherige Tests.
Der erneut erzeugte Dependency-Graph und das gemergte Manifest wurden geprüft.
Lokale Build-/Runtime-/Dependency-Evidenz bleibt unter der ignorierten
`.prototype-cache/`. Keine neuen manuellen Geräteversuche in diesem Abschluss;
keine Reparatur oder Bereinigung von Haupt-App-Beständen.

### Tatsächlich beobachteter manueller Fairphone-Nachweis

Fairphone 6 / Android 16 / API 36; vom Nutzer berichtete Realgerätebeobachtung.

| Lauf / ausdrückliche Aktion | Beobachtetes Ergebnis |
|---|---|
| „B3 · neuen synthetischen Filebestand ausdrücklich initialisieren“ | Session LOCKED; Keystore-Autorisierung SUCCEEDED; konkrete Schlüsseloperation SUCCEEDED; B3-PUBLISH-SUCCEEDED; Schlüsseleigenschaften NOT_TESTED |
| Nach Force-stop und Neustart: „B3 · bestehenden Filebestand authentifizieren“ | Session READY; Keystore-Autorisierung SUCCEEDED; konkrete Schlüsseloperation SUCCEEDED; B3-RESTART-SUCCEEDED |

Beim Initialisierungslauf wurde DEVICE_CREDENTIAL erfolgreich abgeschlossen.
Die konkrete Keystore-Operation war erfolgreich; das synthetische Asset wurde
verschlüsselt, vollständig validiert und publiziert. Dieser Pfad endet absichtlich
LOCKED. Schlüsseleigenschaften wurden in diesem Lauf weder ausgewertet noch
angezeigt und werden nicht nachträglich behauptet.

Beim Restart wurde der bestehende Ciphertext nach Process-/App-Neustart verwendet.
Keine automatische Entschlüsselung vor Authentifizierung: Eine erneute explizite
DEVICE_CREDENTIAL-Authentifizierung war erforderlich. Derselbe persistierte
Wrapped File-Key wurde erneut entpackt und das bestehende synthetische Asset
vollständig validiert. Der Restart endete READY mit B3-RESTART-SUCCEEDED.
Keine Neuinitialisierung oder Ersatzschlüsselerzeugung gehört zu diesem Pfad;
kein zeitbasiertes Keystore-Fenster als Fallback.

**Im Restart tatsächlich angezeigte Schlüsseleigenschaften:** TEE,
authHardware=true, RSA2048/OAEP, DEVICE_CREDENTIAL, PER_OPERATION,
KEYSTORE_ALIAS_PRESENT und WRAPPED_KEY_PRESENT. Diese Angaben sind Evidenz für
dieses Gerät und diese Plattformkonfiguration; kein StrongBox-Nachweis und keine
allgemeine Android-/OEM-Garantie. Sie bedeuten nicht, dass App, Klartexte oder
Tink-Verarbeitung innerhalb der TEE laufen.

### Filesystem und Markergrenzen

Testscan über filesDir/cacheDir/noBackupFilesDir/code_cache und DB-Verzeichnis,
64-KiB-gepufferter Scan mit Überlappung für UTF-8/ASCII/UTF-16LE/BE. Quellen
werden im RAM generiert; keine Klartext-Source-Datei. Fault-Fixtures behalten
auch beschädigten Ciphertext; kein Cleanup zum Verbergen von Befunden.

Auch im vollständigen Abschlusslauf bestanden sämtliche B3-Markerprüfungen.
Im vorherigen B3-Review-Lauf waren diese ebenfalls negativ. Die dort zusätzlich
lesende Inventarisierung der behaltenen UUID-Testfixtures erfasste 19 B3-
Testnamensräume, 53 nichtleere und eine leere reguläre Datei sowie Verzeichnisse.
Die leere Datei gehört zu einem absichtlich abgebrochenen Staging-Versuch.
Größter Ciphertext: 33.555.017 Bytes für die 32-MiB-plus-17-Byte-Source;
verschlüsselte `m`-Dateien: 145 Bytes. Die reale Wrapping-Fixture enthielt
`k/0` = 1, `k/1` = 256, `k/2` = 294 Bytes. Keine Ciphertext- oder Keyinhalte
wurden für diese lesende Inventarisierung ausgegeben. Kein neuer SQLCipher-
DB-/WAL-/Journalbestand gehört zum B3-Pfad; die vollständige Suite enthält
weiterhin die getrennten historischen DB-Fixtures.

Negativer Marker-Scan ist ein begrenztes Testsignal, kein vollständiger
Klartextfreiheits-, RAM-, OCR-, Forensik- oder OEM-Systemcache-Nachweis.
Bekannte synthetische Marker stehen außerdem bewusst im Test-/Build-Code;
APK/Quellcode/Host-Reports sind keine operativen geschützten FileAssets.
Keine Schlüsselwerte werden in Testreports aufgenommen.

Die vollständige Gradle-Suite entfernt ihre Test-App nach dem Lauf. Der frühere
B3-AndroidJUnitRunner-Lauf wurde deshalb nach Installation des Test-APK separat
ausgeführt und seine rein synthetischen Fixtures für die lesende Inventarisierung
behalten. Das ist kein Löschen/Reparieren eines Haupt-App-Bestands. Die
APK-/Test-UID ist vom Haupt-App-Bestand getrennt. Debug-APK/Debugger-Zugriff
ist ausdrücklich kein produktiver Schutz gegen autorisierte ADB-/RAM-Analyse.

## Dependencies, Manifest und Sicherheitsreview

Einzige neue direkte Dependency: `com.google.crypto.tink:tink-android:1.23.0`
in :security. Tatsächlich neu aufgelöst: Gson 2.13.2 und Error-Prone-Annotations
2.41.0; JSR305 3.0.2 und AndroidX-Annotation-JVM 1.9.1 sind ebenfalls im Abschluss
(teilweise bereits vorhanden). Keine zusätzliche Kryptobibliothek oder
Networking-/Cloud-/Telemetry-Library. Tink Android bringt keinen zusätzlichen
Keystore-/Keyset-Lifecycle in den benutzten Pfad; die ausgeschlossenen APIs sind
in der Dependency enthalten, werden jedoch nicht aufgerufen.

Keine neuen Manifest-Komponenten/Permissions. Bestehendes USE_BIOMETRIC dient
dem Plattform-Prompt, nicht der Freigabe biometrischer Authentifizierung. Weiterhin
kein INTERNET/CAMERA/POST_NOTIFICATIONS/Storage-Zugriff; nur Launcher exportiert.
FLAG_SECURE und umfassende Backup-/Device-Transfer-Ausschlüsse unverändert,
B3 nur im privaten noBackup-Speicher. OEM-Verhalten bleibt praktisch offen.

Der erneute Security-Review prüfte alle neuen Runtime-/Testpfade auf Keyausgaben,
Strings, Logs/Exceptiontexte, cleartext/keyset persistence, automatic replacement,
Session-Bypass, alte Owner, Provider/Sharing, Permissions und Backup-Lücken.
Kein blockierender Befund im geprüften Diff; keine Klartext-Key-/Keyset-Persistenz,
keine Klartext-Tempdatei, kein Ersatz-Key und kein Zeitfenster-Fallback im B3-Pfad.
EOF-Verifikation, Längenprüfung sowie Commit-/Publication-Widerruf bleiben erhalten.
Architekturmodule bleiben vier; :domain bleibt Android-/Tink-/Cipher-frei;
:data orchestriert, :security besitzt Tink/RSA, :app verdrahtet. UI erhält nur
Domain-Experiment-/Sessionwerte und feste technische IDs.

## B3-Entscheidung und verbindliches PaG-Profil

**Tink Streaming AEAD ist für den getesteten PaG-FileAsset-Pfad geeignet**, unter
folgenden Bedingungen. Diese Entscheidung ersetzt keine finale Produkt-Key-
Hierarchie oder die vollständige restartbare Import-State-Machine aus B4.

- `tink-android 1.23.0`, vordefinierte `AES256_GCM_HKDF_1MB`-Parameter.
- Separater zufälliger 256-Bit File-Key, unabhängig vom DB-Key; eigener
  RSA-OAEP-/per-operation-DEVICE_CREDENTIAL-Wrapping-Pfad.
- Kein AndroidKeysetManager und kein persistiertes Tink-Klartext-Keyset.
- Opaque Asset-ID als AAD; keine vertraulichen fachlichen Metadaten in AAD.
- Ausschließlich verschlüsseltes Staging im privaten ausgeschlossenen Speicher.
- Vollständige zweite Decryption bis echtem EOF und passende Länge vor Publish.
- Keine externe Klartextvorschau oder Freigabe an fremde Apps.
- Zentraler Session-/Generation-/Deadline-Vertrag an allen relevanten Grenzen.
- Cleanup kann physisch enden, erlaubt nach Widerruf aber keine Freigabe.
- Alte Stream-/Primitive-Handles und Validation-Evidence sind nicht über eine
  widerrufene Generation hinweg fachlich verwendbar.

## Weiterhin offen

API 30/37, harte Prozessabbrüche an allen File-Übergängen, Power-loss, echte
Speicherknappheit, Credential-Wechsel/-Entfernung und Schlüsselinvalidierung auf dediziertem Testgerät, OEM-Backup-/Transfer,
vollständige physische RAM-/Flash-Löschung und kompromittiertes OS/Kernel.
Kein Schutzversprechen gegen ein vollständig kompromittiertes laufendes OS.
Vollständiger Import-/Delete-Wiederanlauf, produktive verschlüsselte Room-Metadaten,
endgültige FileAsset-Produktstruktur, gemeinsame/per-Asset-Keysets, endgültige
Key-Hierarchie und Produkt-Sessiondauern bleiben spätere Entscheidungen. Kein Übergang zu B4 oder Produktrepositories.
