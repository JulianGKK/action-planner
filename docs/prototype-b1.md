# Prototyp B0 / B1 – Gültigkeitsvertrag und SQLCipher-Engine

## Status, Zweck und Grenzen

Stand: 2026-10-07. **B0: 39 Unit-Tests bestanden; beide Sessiongrenzen auf Realgerät praktisch beobachtet. B1: praktischer SQLCipher-Engine-Nachweis bestanden auf API-30-/API-37-Emulator und Fairphone 6 (Android 16 / API 36).** Jeder lokale Runtime-Bericht bestätigt 16 Tests, 0 failures, 0 errors und 0 skipped. Dies ist keine fertige produktive Storage-Architektur und kein allgemeiner Sicherheitsnachweis. Die Runtime-Läufe wurden vom Benutzer durchgeführt; in diesem Dokumentationsauftrag werden deren lokale XML-Berichte geprüft und die technischen Build-Prüfungen erneut ausgeführt.

Grundlagen: [architecture.md](architecture.md), insbesondere ARCH-31–35, [security.md](security.md), [threat-model.md](threat-model.md), [privacy.md](privacy.md), [data-retention.md](data-retention.md), PRODUCT.md und AGENTS.md. [prototype-a.md](prototype-a.md) bleibt unveränderter historischer Nachweis. Seine Gerätebefunde werden durch neue B0-Unit-Tests weder ersetzt noch erweitert.

B0 erweitert den zentralen SessionCoordinator. B1 ist ein isolierter Engine-Versuch mit synthetischen Datensätzen. Kein Produktdatenmodell, keine DB-Integration in die Testoberfläche und kein vollständiges Storage-Gate. Maintenance bleibt der injizierbare synthetische Dummy. Keine Vorgriffe auf B2–B5: kein Room, Tink, AndroidKeysetManager, libsodium, SEE, FileAsset-Speicher, Import-/Delete-Protokoll, Kamera, Viewer, Recovery, Rotation oder Produktionsschlüsselmodell.

## Versionen und Herkunft

| Bestandteil | Verwendet | Befund |
|---|---|---|
| SQLCipher for Android | `net.zetetic:sqlcipher-android:4.19.1@aar` | Offizieller stabiler Zetetic-Release; keine Legacy-Library, kein Prerelease |
| AndroidX SQLite Support-Verträge | `androidx.sqlite:sqlite:2.7.1` | Aktueller stabiler Patchstand; keine zusätzliche Verschlüsselungsengine |
| Instrumentation Runner | `androidx.test:runner:1.7.0` | Stabil; ausschließlich androidTest |
| JUnit | `junit:junit:4.13.2` | Bestehende Unit-Testbasis; zusätzlich androidTest |
| Bestehende Toolchain | AGP 9.4.1, Gradle 9.6.1, Kotlin 2.4.20, JDK 17 | Unverändert |
| Android-Prototypbereich | minSdk 30, compileSdk/targetSdk 37 | Unverändert; keine endgültige Produkt-Mindestversion |

Am 2026-10-07 anhand offizieller Quellen überprüft: [SQLCipher-Release](https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.19.1), [Zetetic-Repository](https://github.com/sqlcipher/sqlcipher-android/tree/v4.19.1), [SQLite-Releases](https://developer.android.com/jetpack/androidx/releases/sqlite), [AndroidX-Test-Releases](https://developer.android.com/jetpack/androidx/releases/test). Der Android-Release verwendet laut Hersteller SQLCipher-Core 4.19.0; der bestandene Konfigurationstest bestätigt den erwarteten Versionspräfix 4.19.0 sowie temp_store=2. Ein vollständiger Konfigurationsdump liegt in den hier geprüften XML-Berichten nicht vor.

Das tatsächlich aufgelöste SQLCipher-AAR enthält vier ABIs: arm64-v8a, armeabi-v7a, x86 und x86_64. Sein Manifest nennt minSdk 23 und fügt keine Permission oder Komponente hinzu. Das Projekt bleibt bei minSdk 30. Native ABI-, Page-Size- und Laufzeitkompatibilität sind durch APK-Build allein nicht nachgewiesen.

Lizenzprüfung: SQLCipher-Android verwendet die [BSD-Lizenz mit drei Bedingungen](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/LICENSE); AndroidX SQLite und der Test-Runner verwenden Apache 2.0, JUnit EPL 1.0. Es besteht kein erkennbarer Konflikt mit diesem privaten Prototyp; Lizenzhinweise sind bei einer späteren Distribution zu berücksichtigen.

## Dependency-Inventar und Module

Die vier Module und erlaubten Projektkanten bleiben unverändert. `:domain` enthält nur Kotlin-Verträge, keine Android-/DB-/Kryptoabhängigkeit. `:security` erhält keine Projektabhängigkeit. Nur `:data` erhält die neuen DB- und Instrumentation-Dependencies.

Verglichen mit dem Runtime-Inventar aus prototype-a.md wurden im App-Runtime-Graph genau diese Artefakte zusätzlich aufgelöst:

- `net.zetetic:sqlcipher-android:4.19.1`
- `androidx.sqlite:sqlite:2.7.1`
- `androidx.sqlite:sqlite-android:2.7.1`

SQLCipher wird explizit als AAR eingebunden; die von seinen Support-Interfaces benötigte AndroidX-SQLite-Abhängigkeit wird deshalb ausdrücklich deklariert. Keine sqlite-framework-/sqlite-bundled-Artefakte und kein Room-Codegenerator wurden hinzugefügt. Die Support-Interfaces sind kein Klartext-Fallback. Die übrigen App-Runtime-Versionen entsprechen dem bisherigen Inventar.

Testabhängigkeiten: Runner 1.7.0, transitiv Monitor 1.8.0, Test Services Storage 1.6.0, Tracing 1.1.0, JUnit 4.13.2, Hamcrest Core 1.3, JetBrains Annotations 13.0 sowie die bestehenden Kotlin-/AndroidX-Verträge. Diese Artefakte liegen ausschließlich im Instrumentation-Testpfad. Keine neue Networking-, Cloud-, Telemetrie- oder DI-Library wurde im Graph gefunden. Eine Graph-/Manifestprüfung ersetzt keinen vollständigen Supply-Chain-Nachweis.

## B0: zentraler Gültigkeitsvertrag

`MonotonicTime` und `SessionPolicy` sind injizierbare Domain-Verträge. Android verwendet `SystemClock.elapsedRealtime()`, Tests eine deterministische Fake-Zeit. Keine Wanduhr, persistierte Deadline oder persistierte Session.

Die absolute Deadline wird aus dem im erfolgreichen DEVICE_CREDENTIAL-Callback erfassten monotonen Zeitpunkt berechnet. Der Security-Adapter behält diesen Zeitpunkt bei einer verzögerten Weitergabe bis zur Vordergrundrückkehr bei. Unzulässige Zeitangaben oder ein schon abgelaufener Erfolg öffnen keine Sitzung. Der Systemcallback ist der verfügbare Beobachtungspunkt, keine Garantie über den Zeitpunkt interner OS-Verarbeitung bei suspendierter/kompromittierter Laufzeit.

Die absolute Deadline läuft durch MAINTENANCE und READY und bleibt unveränderlich. Die erste READY-Freigabe initialisiert einmal die Inaktivitätsdeadline; sie zählt nicht als Nutzeraktivität. Es gibt hier keinen erneuten READY-Übergang innerhalb derselben Sitzung. Wartung verbraucht absolute Zeit, aber hat keine Inaktivitätsgrenze. Nur gültige Vordergrundinteraktion in READY aktualisiert Inaktivität. I/O, Timer, Rendering, Flows und Lifecycle verlängern keine Grenze. Aktivität nach Ablauf belebt nichts wieder.

Die Testoberfläche verwendet ausdrücklich synthetische, injizierte Werte: absolut 60 Sekunden und READY-Inaktivität 15 Sekunden. **Keine Produktwerte.** Unit-Tests verwenden 100/20 monotone Millisekunden ohne reale Wartezeiten. Das bisherige 15-Sekunden-Keystore-Experiment ist eine getrennte Plattformautorisierungsgrenze; es ist keine Session-Policy und kein neuer historischer Stage-A-Befund.

Activity-Eingaben werden über `onUserInteraction()` zugeordnet; der ausdrücklich ausgelöste Testzugriff meldet zusätzlich seine Benutzerinteraktion. Nur diese UI-Einstiege sollen den Interaktionsport nutzen; Timer und Storage verwenden ihn nicht. Accessibility-/IME-/OEM-Interaktionsabbildung benötigt praktische Geräteprüfung. Activity-Recreation und Process Death rekonstruieren weiterhin keine Sitzung. Resume prüft vor weiterer Freigabe. Der 250-ms-Vordergrundtimer dient nur der zeitnahen UI-Reaktion: Zugriff und Veröffentlichung prüfen unabhängig davon die Deadline.

Ablauf, Gerätesperre oder Verlassen invalidieren die Generation und entziehen Zugriff/Marker. Keine automatische Authentifizierung, Prompt-Schleife oder Wiederbelebung durch bestehende Keystore-Autorisierung. Ein noch laufender Start des Auth-Adapters verhindert einen parallelen neuen Start auch nach zwischenzeitlicher Sperrung.

## Storage-Access-Contract und Rennen

`StorageAccess.acquire()` liefert einen an Generation und Scope gebundenen `StorageContext`. Die Autorität bleibt der SessionCoordinator. Keine zweite Unlock-State-Machine und kein globales `isUnlocked`. Die Gültigkeitsprüfung umfasst State, Generation, Vordergrund/Lifecycle, absolute Zeit und in READY zusätzlich Inaktivität.

Prüfpunkte: OPERATION, STEP, COMMIT und PUBLICATION. Alle prüfen den aktuellen zentralen Zustand erneut. `publish` prüft und führt nur eine kurze synchrone In-Memory-Veröffentlichung unter derselben Koordination wie Sperrung aus. Darin sind I/O und verzögerte/ungeprüfte Folgeemissionen verboten. Verbraucher dürfen freigegebene Inhalte nicht ohne neue Gültigkeitsprüfung für spätere Sitzungen zwischenspeichern.

MAINTENANCE-Kontexte sind gesondert gebunden und werden durch READY ungültig. Die regulären B1-Engine-Zugriffe verlangen ausdrücklich READY-Scope; ein Maintenance-Kontext kann sie nicht freigeben. Ein Engine-Handle bleibt an seinen ursprünglichen Kontext gebunden und kann nicht durch Reauthentifizierung nachträglich gültig werden.

Native DB-/Keystore-Arbeit und das Starten der injizierten Maintenance liegen außerhalb des zentralen Monitors. Commit-Zulassung ist ein kurzer aktueller Check vor `setTransactionSuccessful`; physischer Commit/Rollback erfolgt außerhalb. Bereits zugelassene native Arbeit kann nach Sperrung/Deadline noch physisch enden. Das ist kein Recht auf neue Arbeitsschritte oder Ergebnisveröffentlichung. Kein Versprechen einer atomaren Zeitgrenze für native Fertigstellung und keine sofortige Löschgarantie für JVM-/native Puffer. Engine-Handles haben im isolierten B1-Versuch einen einzelnen Besitzer; Pool-/Mehrthread-/Coroutine-Integration ist noch nicht nachgewiesen.

## B1: Datenbank und Fehlerverhalten

Eine Tabelle `synthetic(id TEXT PRIMARY KEY, marker TEXT NOT NULL, status INTEGER NOT NULL)`, Schema-Version 1. IDs und Dateinamen sind UUIDs; Marker eindeutig synthetisch. Nur parameterisierte INSERTs. Keine Produktentitäten.

Der Test erzeugt mit SecureRandom einen flüchtigen 32-Byte-Schlüssel. Übergabe an die etablierte SQLCipher-API, keine eigene KDF oder Kryptografie. Kein Schlüssel auf Disk, in Logs, BuildConfig, Preferences oder DataStore. Derselbe Testprozess kann mit derselben Bytefolge wieder öffnen. Nach Testende wird der gehaltene Testarray bestmöglich überschrieben; Kopien/native Puffer sind damit nicht zuverlässig entfernt. **Dies beweist keinen Restart-Zugriff und keine Keystore-Verpackung persistenter PaG-Daten.**

Neue DB: nur ausdrücklich neuer, privat reservierter Pfad; `createNewFile()` muss atomar erfolgreich sein. Nur danach ist CREATE_IF_NECESSARY zulässig. Ein fehlgeschlagener Versuch bleibt liegen und wird nicht automatisch als neuer Bestand behandelt.

Vorhandene DB: fehlender/leer/unzulässig geformter Bestand wird abgewiesen. Kein Create-Flag. Zuerst schreibgeschützt öffnen, Schemaform/-version sowie `cipher_integrity_check` und SQLite-`integrity_check` prüfen. Erst nach erfolgreichem Preflight schreibend öffnen und erneut prüfen. Keine Migration, Reparatur, Neuerstellung, Löschung oder Orphan-Bereinigung. Der nichtdestruktive ErrorHandler ersetzt die Hersteller-Default-Behandlung; SQL-/Exception-Logging des Java-Wrappers wird vor Öffnung deaktiviert. Native/OEM-Logging bleibt praktisch zu prüfen.

Der native Cipher-Loglevel wird vor Keying auf NONE gesetzt; nach Keying muss `cipher_status=1` gelten, sonst wird der Zugriff blockiert. `temp_store=MEMORY` wird im postKey-Hook pro Verbindung gesetzt und überprüft, auch für später erzeugte Pool-Verbindungen. Kein eigenes Containerformat, kein Plaintext-Header-Modus, keine Abschwächung der SQLCipher-Defaults. Normaler Engine-Pfad setzt keine WAL-Produktentscheidung. Die Instrumentation untersucht separat rollback journal und WAL sowie Sortierung, Checkpoint und VACUUM an synthetischen Fixtures. Diese direkten nativen Testschritte sind keine weitere Anwendungsschnittstelle.

Technische Fehlercodes: SESSION_INVALID, MISSING_DATABASE, MISSING_KEY, KEY_OR_DATABASE_UNREADABLE, DATABASE_UNREADABLE, INTEGRITY_FAILED, UNKNOWN_SCHEMA, IO, SPACE, BLOCKED. Falscher Schlüssel und beschädigter Ciphertext sind anhand allgemeiner SQLite-Öffnungsfehler nicht zuverlässig trennbar und bleiben ausdrücklich mehrdeutig. Keine pauschale „corrupt“-Diagnose. SPACE wird nur bei entsprechender Engine-Exception zugeordnet. Exceptions enthalten ausschließlich technische Codes, keine Rohmeldungen, SQL, Inhalte oder Schlüssel. Ein fehlgeschlagener Engine-Zugriff blockiert diesen Handle; Ressourcenfreigabe bleibt möglich. Das ist kein zweiter Session-State und keine B5-Fehlerintegration.

## BESTANDEN: automatische Tests und finaler technischer Lauf

| Prüfung | Ergebnis |
|---|---|
| `./gradlew test` | Bestanden; 39 data-Debug-Unit-Tests (19 bestehende, 20 neue); keine Release-Testausführung behauptet |
| `:app:assembleDebug` | Bestanden |
| `:app:lintDebug :data:lintDebug :security:lintDebug` | Bestanden; keine Fehler |
| `verifyArchitecture :app:verifyPrototypeManifest` | Bestanden |
| `:data:assembleDebugAndroidTest` | Bestanden; native Test-APK kompiliert und gebaut |
| App-Runtime-/Instrumentation-Dependency-Graph | Im vorherigen B0/B1-Lauf aufgelöst/geprüft; Dependencies hier unverändert |
| `git diff --check` | Bestanden |

Der finale Offline-Gradle-Aufruf mit allen oben genannten Test-/Build-/Lint-/Architektur-/Manifest-Tasks sowie assembleDebugAndroidTest war erfolgreich. Der data-Unit-Test-Task war UP-TO-DATE; die unveränderten XML-Berichte enthalten weiterhin 39 bestandene Tests. Kein neuer connectedDebugAndroidTest in diesem Dokumentationsauftrag; die drei erfolgreichen Runtime-Berichte wurden als vorhandene Evidenz geprüft.

19 bestehende Session-Tests wurden nur für Zeit-/Policy-/Erfolgszeitpunkt-Injection angepasst. 20 neue deterministische Tests prüfen Auth-Zeitanker, Maintenance-Zeitverbrauch, READY ohne Neustart der absoluten Grenze, qualifizierte Aktivität, I/O/Timer ohne Idle-Verlängerung, frühere Grenze, Ablauf an Commit/Publikation, alte Generationen/Callbacks, Sleep-Sprung, konservative Rekonstruktion, Vordergrundverlust und fehlende Wiederbelebung durch Plattformautorisierung. Synchronisationstests verwenden Latches ohne Sleeps; begrenzte Await/Join-Timeouts dienen nur der Deadlock-Erkennung. Sie prüfen auch lange synthetische Operation/Maintenance und parallele Auth-Starts während Sperrung.

Die bestehenden Tests prüfen außerdem Dialogabbruch, doppelte Anforderungen, alte Auth-Ergebnisse, Sperrung während Maintenance und READY sowie fehlgeschlagene Schlüsseloperationen. Android Home, Screen-Off, Activity-Recreation und OEM-Lifecycle selbst sind damit keine neuen realen Gerätebefunde.

Lint: :data und :security ohne Befund. :app meldet ausschließlich einen Hinweis auf inzwischen neueres Gradle 9.8.0; die freigegebene bestehende Toolchain wurde nicht außerhalb des Auftrags aktualisiert.

## BESTANDEN: SQLCipher-Instrumentation und manuelle B0-Realgerätetests

16 Instrumentation-Testmethoden decken ab:

- Runtime-Konfiguration, Neuanlage, Write/Read, Commit, Rollback, Close und Wiederöffnung mit demselben flüchtigen Schlüssel.
- Falschen/fehlenden Schlüssel, fehlende DB, beschädigten Header, unklaren leeren Bestand, unbekannte Schema-Version und Verweigerung erneuter Neuanlage über vorhandenem Bestand; byteweiser Erhalt vorhandener Fixtures.
- Ablauf vor Query/Write/Open, nach Query vor Veröffentlichung, vor Commit und nach Write; Rollback und spätere explizite Wiederöffnung.
- Alte Kontexte/Handles nach Reauth und Verweigerung regulärer Zugriffe mit Maintenance-Permit.
- Dateiinventar und Marker-Scan bei offener Transaktion, WAL, Sortierung, Checkpoint, VACUUM und nach Close.

Befehl für die durchgeführten und spätere Runtime-Läufe: `./gradlew :data:connectedDebugAndroidTest`. Der Test-Runner für dieses Library-Modul verwendet die separate Test-App `org.pag.data.test`; er verändert nicht die Stage-A-App-Daten. Kein Orchestrator und kein automatisches `clearPackageData`. Fixtures bleiben im privaten noBackupFilesDir; kein automatisches Löschen unbekannter Bestände. Fehlgeschlagene Versuche werden nicht durch Cleanup „repariert“.

| Runtime | SQLCipher-Suite | B0-Deadline manuell | Evidenz |
|---|---|---|---|
| API 37 Emulator | 16/16 bestanden; 0 failures/errors/skipped; 17,149 s | Kein zusätzlicher manueller Befund gemeldet | api37-sqlcipher.xml |
| API 30 Emulator | 16/16 bestanden; 0 failures/errors/skipped; 38,982 s | Kein zusätzlicher manueller Befund gemeldet | api30-sqlcipher.xml |
| Fairphone 6, Android 16 / API 36 | 16/16 bestanden; 0 failures/errors/skipped; 17,500 s; connectedDebugAndroidTest erfolgreich | Inaktivität und absolute Grenze bestanden | real-api36-sqlcipher.xml; Benutzerbeobachtung |

Die lokalen XML-Berichte unter `.prototype-cache/test-evidence/` wurden auf Suite-/Testzahlen und Fehler-/Skip-Einträge geprüft. Gerätetyp und die manuellen Beobachtungen stammen aus dem Benutzerbericht; die XML-Zusammenfassungen belegen den Instrumentation-Umfang. Die Testevidenz bleibt lokal: `.prototype-cache/` ist weiterhin von Git ignoriert und wird nicht dem Commitumfang hinzugefügt. Die XML-Zeitstempel werden nicht als Nachweis einer Zeitzone interpretiert.

Der erste API-37-Lauf hatte 15 bestandene Tests und einen Fehler in `rollbackJournalAndWalInventoryWhileLive`: `PRAGMA wal_autocheckpoint = 0` wurde fälschlich über execSQL aufgerufen. Die minimale, bereits erfolgte Testkorrektur verwendet rawQuery, erzwingt Cursor-Ausführung mit moveToFirst, prüft den Rückgabewert 0 und schließt per use. Keine Sicherheitsassertion wurde abgeschwächt. Der anschließende vollständige Lauf einschließlich dieses Tests bestand. Keine weitere Implementierungs-/Teständerung durch diesen Dokumentationsauftrag.

**Manuell auf Fairphone 6 beobachtet:** Nach expliziter Authentifizierung und READY führte Nichtbedienung zur automatischen Sperre an der Inaktivitätsgrenze. In einem getrennten Versuch führte fortlaufende normale Bedienung trotzdem zur automatischen Sperre an der absoluten Grenze. Damit sind diese beiden Grenzen auf diesem Gerät praktisch gestützt; eine offene READY-Sitzung lässt sich durch normale Bedienung nicht unbegrenzt erhalten. Es wurden ausschließlich synthetische Prototypwerte verwendet. Keine endgültigen Produktdauern und keine Garantie, dass ein kompromittiertes OS/Kernel dieselben Grenzen zuverlässig erzwingt.

Die Instrumentation verwendet einen kontrollierten Auth-/Maintenance-Fake für den isolierten Engine-Nachweis. Sie beweist keine Keystore-Verpackung oder persistente Storage-Schlüsselbindung. Die manuelle App-Prüfung ergänzt die beiden Deadline-Beobachtungen mit dem aktuellen Plattform-Authentifizierungspfad, ist aber kein vollständiger Lifecycle-/Last-/OEM-Nachweis.

Zusätzliche noch offene praktische Fälle: Process Death mitten in DB-Schreiboperationen; Crash-Recovery mit WAL/Hot Journal; kontrollierte Speicherknappheit/I/O-Fehler; größere Sort-/Temp-Last; Locks während tatsächlicher nativer I/O; Race unmittelbar nach Commit-Zulassung; OEM-/IME-/Accessibility-Verhalten; Hintergrund/Sleep/Resume unter Last. Keine invasiven Credential-Änderungen in diesem Auftrag.

## DB-/WAL-/SHM-/Journal-/Temp-Inventar und Marker-Scan

**Marker-/Inventar-Prüfungen gemäß implementierter Suite auf allen drei Runtimes bestanden.** Die geprüften XML-Berichte enthalten keine detaillierten B1-INVENTORY-Ausgaben. Deshalb werden hier keine einzelnen Dateinamen, Größen oder das tatsächliche Auftreten jedes Nebenbestandtyps behauptet. Der bestandene Scan bedeutet nur: Unter den erreichten Prüfbedingungen wurde in den gescannten Dateien kein implementierter Marker-Treffer gemeldet.

Der instrumentierte Scanner inventarisiert alle erreichbaren Dateien im privaten Datenverzeichnis der separaten Test-App, einschließlich noBackupFilesDir, databases, files, cache und code_cache. Er meldet ausschließlich relative technische Dateinamen, Größe und Trefferstatus. Scanner-Lesefehler lassen den Test fehlschlagen; sie zählen nicht als negatives Scanergebnis. Der Test sucht den rein synthetischen Marker als UTF-8, ASCII, UTF-16LE und UTF-16BE. Laufzeit-Konfiguration wird zusätzlich ohne Schlüssel/Inhalte protokolliert.

| Artefakt | Erwarteter Prüfgegenstand | Tatsächlicher Befund |
|---|---|---|
| main DB | Verschlüsselter Markerbestand | Neuanlage/Write/Read/Reopen und Scan bestanden |
| `-wal` | Ciphertextseiten während WAL | Suite-/Scan-Prüfung bestanden; einzelne Dateibefunde nicht separat belegt |
| `-shm` | Technische WAL-Metadaten, keine Fachinhalte | Suite-/Scan-Prüfung bestanden; einzelne Dateibefunde nicht separat belegt |
| `-journal` | Rollback-Nebenbestand bei offener Transaktion | Suite-/Scan-Prüfung bestanden; einzelne Dateibefunde nicht separat belegt |
| Cache / Temp / sonstige SQLCipher-Nebenbestände | Keine persistenten Klartextmarker | Suite-/Scan-Prüfung bestanden; einzelne Dateibefunde nicht separat belegt |

Ein Marker-Treffer ist ein **Abbruchbefund**. Nicht verstecken oder durch Cleanup beseitigen. Der negative Scan ist nur ein begrenztes Testsignal und kein allgemeiner Beweis vollständiger Klartextfreiheit: Er erfasst weder alle Encodings/Fragmente/Metadaten noch kurzlebige bereits entfernte Dateien, andere Systemprozesse, RAM, forensische Flash-Reste oder beliebige Last-/Crashsituationen. temp_store=MEMORY allein ist kein vollständiger Nachweis aller möglichen Nebenbestände.

## Manifest, Backup und Security-Review

**Positiver Realgerätebefund zum bestehenden FLAG_SECURE-Schutz:** Auf dem Fairphone 6 mit Android 16 war Android Studio Device Mirroring aktiv. Der normale Android-Bildschirm und andere Oberflächen wurden übertragen. Sobald PaG sichtbar war, wurde der Inhalt des PaG-Fensters im übertragenen Bild nicht angezeigt; nach Verlassen von PaG war die übrige Geräteoberfläche wieder sichtbar. Diese Benutzerbeobachtung stützt den bestehenden Screenshot-/Screen-Capture-Schutz für diesen konkreten Geräte- und Mirroring-Pfad. Sie beweist keinen Schutz gegen alle Capture-Methoden, externe Kameras oder ein kompromittiertes OS/Kernel und lässt sich nicht pauschal auf andere OEMs oder Android-Versionen übertragen.

Final gemergtes App-Manifest: nur USE_BIOMETRIC (bestehende Plattform-API-Voraussetzung; freigegebener Typ bleibt ausschließlich DEVICE_CREDENTIAL) und die bestehende app-eigene Signature-Permission für nicht exportierte dynamische Receiver. Keine INTERNET-, CAMERA-, Notification- oder Storage-Permission. Nur MainActivity als Launcher exportiert; keine weiteren Activity-Aliase, Provider, Receiver oder Services. Bestehende Backup-/Transfer-Ausschlüsse unverändert, allowBackup=false.

Auch die separate native Test-APK hat allowBackup=false und explizite Cloud-/Device-Transfer-Ausschlüsse für alle bisherigen Speicher-Domains. Keine exportierten Anwendungskomponenten. Ihre Package-Queries für AndroidX-Testdienste sind ausschließlich Test-Runner-Infrastruktur; sie gelangen nicht in die App. OEM-Backup-/Transferverhalten bleibt praktisch unbewiesen.

Eigener Diff einschließlich neuer Dateien geprüft: keine hardcodierten B1-Schlüssel, Klartext-Key-Persistenz, SQL-Logs, println/Stacktrace-Ausgaben, Netzwerkzugriffe, sensitive Saved-State-/Preferences-Inhalte, destructive migration oder automatische Bereinigung unbekannter Dateien. Die bisherigen Stage-A-Preferences enthalten nur technische Hinweise zur Existenz synthetischer Keystore-Aliase, keine Schlüsselbytes oder operative Daten; daran wurde nichts geändert. Keine langen DB-/Keystore-/Maintenance-Arbeiten im zentralen Monitor. Veröffentlichungs-/Observer-/Cancellation-Callbacks müssen kurz und ohne I/O bleiben.

## WEITERHIN OFFEN / nicht hinreichend nachgewiesen

Die bestandenen Engine-Tests stützen unter den getesteten Bedingungen Roundtrip/Transaktionen, fail-closed bei getesteten Schlüssel-/Bestands-/Schemafehlern, Erhalt der Fehlerfixtures, Session-/Generation-Verweigerung und die begrenzten Marker-Prüfungen. Keine Architekturannahme wurde dadurch widerlegt. Der anfängliche PRAGMA-Fehler war ein Test-API-Fehler, kein Nachweis eines Crypto-/Storage-Fehlers.

Weiterhin offen beziehungsweise technisch nicht vollständig garantierbar:

- Keystore-Verpackung persistenter DB-/FileAsset-Schlüssel und Restart-Zugriff mit gerätegebundenem Storage-Key.
- Credential-Änderung/-Entfernung, gezielte Schlüsselinvalidierung und Verlust-/Fehlerverhalten.
- Stromverlust, tatsächlicher Process Death während nativer Schreibarbeit, Crash-Recovery, echte Speicherknappheit sowie vollständige Durability-/Flush-Strategie.
- Vollständige physische Flash-Löschung und vollständige Klartextfreiheit beziehungsweise sofortige Pufferlöschung in RAM: keine Garantie aus diesen Tests ableiten.
- OEM-Cloud-Backup-/Device-Transferverhalten auf Realgeräten; vollständiges Native-/System-Logging-Inventar.
- Schutz gegen kompromittiertes OS/Kernel: keine zuverlässige Schutzgarantie; Debugger-/RAM-/forensische Restrisiken bleiben.
- Endgültige SQLCipher-direkt-versus-Room-Entscheidung, Schlüsselhierarchie/-granularität, Wrapping/Parameter, Keystore-Operationsbindung versus Autorisierungsfenster und endgültige Hardware-/Android-Anforderungen.
- Endgültige Sessiondauern, vollständige Interaktions-/Lifecycle-Abbildung und Retention-Fristen.
- Tink-/FileAsset-Integration, Import-/Delete-/Wiederanlaufprotokolle und Gesamtintegration B2–B5.

**B1 gilt im abgegrenzten Umfang als bestandener praktischer SQLCipher-Engine-Nachweis, nicht als fertige produktive Storage-Architektur.** B0 ist durch 39 Unit-Tests und die beiden berichteten Realgerätetests gestützt. Es gibt nach diesem Review keinen erkannten Blocker gegen einen Commit des ausdrücklich begrenzten B0/B1-Prototyps; offene Nachweise bleiben Voraussetzung für spätere produktive Nutzung. Kein Commit oder Push in diesem Auftrag.

Nächster vorgesehener Schritt: **separater früher Keystore-Verpackungsnachweis vor B2/B3**. Hier nicht implementiert. Unerwarteter persistenter Klartext, überschriebene Fehlerfixtures, Gate-Bypass, unsichere Fallbacks oder erforderliche eigene Kryptografie bleiben Abbruchkriterien. Die SQLCipher-Verwendung für diesen Engine-Versuch trifft keine endgültige Storage-Produktentscheidung.

Ergebnisfelder für weitere Läufe: Datum, API, ABI, Emulator/Realgerät, Testversion, Testanzahl, erfolgreiche/fehlgeschlagene Test-IDs, Engine-Konfiguration, detailliertes Dateiinventar vor/während/nach Transaktion, Marker-Treffer, Crash-/Low-Space-Befunde, weitere B0-Lifecycle-/Timeout-Befunde und verbleibende Blocker. Nur technische synthetische Angaben eintragen.
