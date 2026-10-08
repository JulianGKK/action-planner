# Prototyp B2: Room gegenüber direktem SQLCipher-Zugriff

## Status, Forschungsfrage und Grenzen

Stand: 2026-10-08. Architekturprototyp mit ausschließlich synthetischen Daten.
B2 ist im getesteten Umfang auf Fairphone 6 / Android 16 / API 36 abgeschlossen.
Entscheidung: **ROOM EMPFOHLEN**, ausschließlich unter dem unten festgelegten
PaG-Profil. Automatische Engine-/Session-Evidenz und manuell beobachtete
Authentifizierungs-/Force-stop-Läufe sind getrennt dokumentiert. Die Auswahl
der relationalen MVP-Persistenz ist freigegeben; Produktrepositories und eine
endgültige Produkt-Schlüsselhierarchie wurden nicht implementiert.

Die Forschungsfrage lautet, welche Variante unter denselben PaG-Anforderungen
weniger Sicherheits-, SQL-, Mapping- und Migrationsfehler erwarten lässt:
direktes SQLCipher/AndroidX SQLite oder Room auf SQLCipher. Room verschlüsselt
nicht selbst. Maßgeblich bleiben [architecture.md](architecture.md),
[security.md](security.md), [threat-model.md](threat-model.md) und die historischen
Nachweise [B1](prototype-b1.md), [B1.5](prototype-b1-5.md) und
[B1.6](prototype-b1-6.md). Deren dokumentierte Ergebnisse bleiben unverändert.

Nicht enthalten: Produktdatenmodell, Room-Flows/LiveData/Paging/Rx, FileAssets,
Tink, Import/Löschung, Kamera, Recovery, Backup, Export, Netzwerk oder neue
Kryptografie. Insbesondere wird keine Schutzgarantie gegen ein kompromittiertes
OS oder vollständige Beseitigung von Klartext aus RAM/Flash behauptet.

## Offizielle Quellen und Versionen

Am genannten Stand geprüft:

| Komponente | Geprüfter stabiler Stand | Im Vergleich verwendet |
|---|---|---|
| Room 2 | 2.8.5 | Nein; etablierter OpenHelper-Integrationsweg verglichen |
| Room 3 | 3.0.3 | Ja; ausschließlich stabile Driver-/DAO-/Migrations-APIs |
| AndroidX SQLite | 2.7.1 | Ja; vorhandene Version unverändert |
| SQLCipher for Android | 4.19.1 | Ja; vorhandene Zetetic-Linie unverändert |
| KSP | 2.3.12 | Ja; ausschließlich Build-/Compiler-Werkzeug |

Quellen: [Room-Release Notes](https://developer.android.com/jetpack/androidx/releases/room),
[Room-3-Release Notes](https://developer.android.com/jetpack/androidx/releases/room3),
[SQLite-Release Notes](https://developer.android.com/jetpack/androidx/releases/sqlite),
[KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12),
[Zetetic 4.19.1 README und Room-Integration](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/README.md),
[SQLCipherDriver](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/sqlcipher/src/main/java/net/zetetic/database/sqlcipher/driver/SQLCipherDriver.java),
[SQLCipherConnection](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/sqlcipher/src/main/java/net/zetetic/database/sqlcipher/driver/SQLCipherConnection.java),
[SupportHelper](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/sqlcipher/src/main/java/net/zetetic/database/sqlcipher/SupportHelper.java)
und [SQLiteOpenHelper](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/sqlcipher/src/main/java/net/zetetic/database/sqlcipher/SQLiteOpenHelper.java).

Die bestehende Toolchain bleibt AGP 9.4.1, Gradle 9.6.1, Kotlin 2.4.20,
JDK 17; Prototyp minSdk 30, compileSdk/targetSdk 37. Room/KSP kompilieren damit
im tatsächlichen Projekt. Keine Alpha-/Beta-/RC-Version und kein experimentelles
Room-API werden verwendet. Die vorläufige SDK-Grenze ist keine Produktentscheidung.

### Offizielle Integration und notwendige Eingrenzung

Zetetic beschreibt sowohl `SupportSQLiteOpenHelper.Factory` als auch die
`SQLiteDriver`-Integration. Room 3 verwendet hier `setDriver()` und Zetetics
öffentliche `SQLCipherConnection`. Der unveränderte `SQLCipherDriver.open()`
ruft jedoch `openOrCreateDatabase()` auf. Auch der OpenHelper-Pfad verwendet
`CREATE_IF_NECESSARY`. Ein Vorabtest allein würde das native CREATE nicht
entfernen. Deshalb verwendet B2 einen kleinen nichtkryptografischen Driver-
Adapter mit `SQLiteDatabase.openDatabase(OPEN_READWRITE)`, ohne CREATE-Flag.
Eine neue Datei wird ausschließlich im ausdrücklich geprüften EMPTY-Pfad mit
`createNewFile()` exklusiv reserviert. Bestehende Dateien werden zuerst read-only
geprüft. Room bekommt keinen Klartext-/Framework-Fallback-Driver.

Dies ist eine Anpassung der offiziellen Integrationsnaht, keine unveränderte
Zetetic-Standardkonfiguration. Ihr zusätzlicher Review-/Wartungsaufwand zählt als
Nachteil von Room. Direct benötigt ebenfalls Inventar-, Schema- und Session-
Prüfungen und ist durch Weglassen von Room nicht automatisch sicherer.

## Entscheidungsmatrix vor und nach dem praktischen Vergleich

| Frage | Direct | Room + SQLCipher |
|---|---|---|
| Explizites Open/Close | Eigene kurze native DB-Lebensdauer | Kurze RoomDatabase/Driver-Lebensdauer; kein Singleton erforderlich |
| CREATE gegenüber OPEN EXISTING | Exklusive Dateireservierung, natives Open ohne CREATE | Dieselbe Reservierung; eigener begrenzter Driver statt openOrCreate |
| Falscher Key / beschädigte DB | Read-only Preflight; Fehler ist nicht automatisch Korruption | Derselbe Preflight vor Room; keine automatische Ersatz-DB |
| Fehlende / unbekannte Version | Abweisung vor Schreiböffnung | Derselbe Preflight plus Room-Schema-/Identity-Prüfung |
| Destructive Migration | Kein entsprechender Pfad | Kein fallbackToDestructiveMigration oder gleichwertiger Fallback |
| Pool / Handles | SQLCipher-native Connections innerhalb eines Besitzers | Driver meldet native Pool-Fähigkeit; Room-Passthrough-Pool hält eine Driver-Verbindung innerhalb desselben Besitzers |
| Hintergrundarbeit | Kein zusätzlicher Room-Dispatcher | Coroutines/Executor und interne Invalidation-Verarbeitung zusätzlich; Close und widerrufbare Statements nötig |
| Cursor / Statements | Cursor ausdrücklich use/close | Generierter Code schließt Statements; Adapter prüft prepare/step/Lesen/Commit |
| Entity-Caches / Observer | Kurzlebige eigene Resultatlisten | Kurzlebige Entity-/Resultatlisten; keine observable DAOs, keine langlebigen fachlichen Caches |
| WAL / temp | Für B2 TRUNCATE; temp_store=MEMORY | Dieselbe Journalwahl und pro nativer Connection geprüfte Temp-Konfiguration |
| Schema-Validierung | Manuelle Spalten-, Typ-, NULL-, PK-, FK- und Indexprüfung | Zusätzlich Room generiertes Schema/Identity-Validierung |
| Migrationstestbarkeit | Eigene transaktionale Version-/SQL-Logik | Migration und generierte Validierung, weiterhin explizite Migration erforderlich |
| Transaktionen / Constraints | Manuelles Binding, Commit-Prüfung und Rollback | Generierte Insert-Bindings, Room-Transaktion und Driver-Commit-Prüfung |
| SQL-/Mappingfehler | Höherer eigener Prüf-/Testaufwand | Compile-time Query- und Entity-Prüfung vermindert einen Teil dieser Fehler |
| Dependency-/Compiler-Fläche | Bereits vorhandener Stack | Room Runtime, SQLite-Async/Framework, KSP und Compiler-Closure zusätzlich |
| Logging / Debug | Explizit kein SQL-/Fehlertext-Logging | Kein QueryCallback; Schemaexport enthält nur synthetische Schema-Definitionen |
| Zentrale Session/Generation | StorageContext vor Arbeit, Commit und Publication | Derselbe Vertrag, zusätzlich durch Driver-Statements geprüft |
| Vor READY schließen | Ja | Ja; Ressourcen-Schließung wird im Vergleich geprüft |

Keine Theoriezeile ersetzt einen Laufzeitnachweis. Insbesondere Pooling,
Driver-Transaktionsabschluss und Room-Artefakte müssen bei Updates erneut geprüft
werden. Mehr Adapter-/Pool-Komplexität kann mehr Fehler schaffen, als der
zusätzliche Gate-Check verhindert; die unten beschriebenen Fehlversuche zeigen
dies konkret.

## Versuchsaufbau und Architektur

Beide Varianten verwenden `SyntheticRecord(id TEXT PRIMARY KEY NOT NULL,
value TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0)` in v2. v1 hat noch
keine revision. Eine synthetische Kindtabelle mit Foreign Key und Index prüft
Constraints. `PRAGMA user_version` dient der Versionsfixture; Room speichert
zusätzlich seine Schema-Identity in der verschlüsselten DB. Keine Fachentitäten.

Die Module und Abhängigkeitsrichtungen bleiben unverändert. Room-/DAO-/Cursor-/
SQLCipher-Typen bleiben in :data. :domain enthält nur zusätzliche technische
Experimentwerte; :security nur deren Moduswerte. Keine zweite Session-State-
Machine. :app verdrahtet die beiden Adapter; UI/ViewModel erhalten nur den
bestehenden Domain-Vertrag.

Der manuell ausführbare Pfad ist für beide Varianten:

1. LOCKED; expliziter Initialisierungs- oder Reopen-Klick.
2. Vollständiges Inventar prüfen. Init nur EMPTY; Reopen nur COMPLETE.
3. Unveränderter B1.5-WrappingCredentialExperiment mit DEVICE_CREDENTIAL,
   per-operation RSA-OAEP und demselben autorisierten Cipher.
4. Zentrale Session geht nach erfolgreicher Authentifizierung in MAINTENANCE.
5. `useUnwrappedKey()` gibt Bytes ausschließlich an einen synchronen Consumer.
6. DB öffnen, synthetisch schreiben/lesen, geschlossenes Ressourcenende prüfen;
   kein DB-/DAO-/Key-Handle wird in UI oder neue Session übernommen.
7. Key-Consumer beendet, eigene Driver-Kopie bestmöglich überschrieben und
   Operation freigegeben. Erst danach Abschluss im Coordinator: Init -> LOCKED;
   Reopen -> READY mit festem synthetischem Marker.

Die absolute Deadline wird dabei nicht neu gestartet. Maintenance verbraucht
Sessionzeit. StorageContext kontrolliert Scope, Generation, Deadline und
Foreground-Zulässigkeit vor Operation/Schritt/Commit/Publication. Native Arbeit
kann nach Widerruf physisch enden; Commit-Zulassung und späte Veröffentlichung
bleiben verweigert. Kein langer Coordinator-Lock um I/O.

Die RoomDatabase wird nur für diesen kurzen Consumer erzeugt und deterministisch
geschlossen. Room-internes Tracking wird nicht als Nutzeraktivität gewertet.
Kein Auto-Close als Ersatz für explizites Close, kein globaler Singleton,
kein Copy-/Prepackaged-DB-Pfad und keine Multi-Instance-Invalidation.

### Persistente Artefakte und Bootstrap

Neue getrennte Haupt-App-Namensräume unter noBackupFilesDir:
`p2-direct-v1`, `p2-room-v1`; Aliase `pag.prototype.b2.direct.v1` und
`pag.prototype.b2.room.v1`. Bestehende p15/p16 und deren Aliase werden weder
verwendet noch geändert. Technische Struktur je B2-Namensraum:

- `0`: 1-Byte Initialisierungsabsicht.
- `k/0`, `k/1`, `k/2`: unveränderte B1.5-Inventar-/Wrapped-Key-/Public-Key-Artefakte.
- `s/d`: SQLCipher-DB; bekannte native Sidecars nur im selben reservierten Ort.
- `1`: öffentliche zufällige synthetische UUID des Testdatensatzes, kein Secret.
- Room zusätzlich `s/d.lck`: ausschließlich reguläre leere Lockdatei zugelassen.

EMPTY bedeutet vollständigen leeren Namensraum plus fehlenden erwarteten Alias.
COMPLETE erfordert Intent, vollständigen Keybestand, DB, UUID und bekanntes
Inventar. Jeder Teilbestand/unerwartete Eintrag ist BLOCKED. Nach reservierter
Initialisierung erlaubt nur der aktuelle Init-Pfad genau seinen erwarteten
Zwischenzustand; ein Neustart nimmt ihn nicht automatisch wieder auf. Keine
globale Atomarität über Keystore/Filesystem/DB, keine Reparatur oder Bereinigung.
COMPLETE + Init bleibt NOT_EMPTY ohne Mutation. Die synthetische UUID ist bewusst
öffentlich; fachliche Metadaten gehören weiterhin nur in die geschützte DB.

Beide Varianten nutzen für B2 bewusst TRUNCATE statt WAL, um den Vergleich nicht
mit verschiedenen Journalmodi zu verfälschen. Das ist keine endgültige Wahl für
die MVP-Datenbank. SQLCipher- und SQLite-Integrität werden getrennt geprüft.
Falscher Key/unlesbarer Bestand wird nicht sicher als „korrupt“ umgedeutet.

## Praktische Befunde und Fehlerhistorie

### Zusätzliche Room-Artefakte

Der erste Fairphone-Lauf hatte 52 Tests mit 17 Fehlern, überwiegend wegen der
zunächst unbekannten Room-Lockdatei `d.lck`. Lesende Diagnose zeigte eine reguläre
Datei mit Größe 0. Der offizielle Room-3.0.3-Quellcode
`FileLock.jvmAndAndroid.kt` erzeugt sie ausschließlich zur Prozesskoordination
über FileChannel und entfernt sie beim Unlock nicht. B2 akzeptiert nun nur diese
genau benannte leere Datei bei ROOM; nichtleere Lockdateien, alleiniger Lockbestand
und dieselbe Datei bei DIRECT bleiben blockiert. Negative Tests sichern dies ab.

Der gemergte Manifest enthielt zunächst den unbenutzten
`MultiInstanceInvalidationService` aus Room. Er wird in App und Test-APK explizit
entfernt; die Manifestprüfung wurde nicht gelockert. Internes Room-Tracking nutzt
laut geprüftem 3.0.3-Quellcode standardmäßig Temp-Tabellen im Speicher. Dessen
expliziter Konfigurationssetter ist als experimentell markiert und wird nicht
verwendet. Kein persistenter Invalidation-Hilfsbestand wurde damit vorausgesetzt.

### Widerruf unmittelbar vor Commit

Der zweite Lauf hatte 56 Tests mit zwei Room-Fehlern: Wiederöffnung nach
Deadline-/Generationswiderruf vor Commit. Im geprüften
`PassthroughConnectionPool.kt` endet Room eine erfolgreiche Transaktion im
finally-Block mit `END TRANSACTION`; ein erst dort verweigerter Abschluss löst
keinen zusätzlichen Rollback aus. Ein Adapter, der nur diesen Commit blockiert,
lässt die Transaktion daher unvollständig zurück.

Der B2-Driver führt bei abgewiesenem Commit jetzt auf derselben Verbindung
`ROLLBACK TRANSACTION` als Ressourcenbereinigung aus und meldet weiterhin den
Fehler. Er führt den verweigerten Commit nicht aus. Generation-/Deadline-
Assertions und erneutes Öffnen mit unverändertem Inhalt bleiben im Test erhalten.
Das ist ein konkreter zusätzlicher Room-Integrationsaufwand, keine Lockerung.

## Tests und Ergebnisse

### Automatisch ausgeführte JVM-/Build-Prüfungen

`./gradlew test`: 145 Tests, davon 40 neue B2-Tests (12 Key-/Storage-Vertrag,
12 Inventar, 16 Session-/Gate-Integration), ohne Fehler. Die bisherigen 105 Tests
bleiben erhalten. Deterministische Fake-Zeit; keine realen Deadline-Wartezeiten.

Erfolgreich ausgeführt:

- `:app:assembleDebug`
- `:app:lintDebug :data:lintDebug :security:lintDebug`
- `verifyArchitecture :app:verifyPrototypeManifest`
- `:data:assembleDebugAndroidTest`

Die vollständigen lokalen Logs bleiben ignorierte Evidenz unter
`.prototype-cache/`. Compiler, generierte DAOs und synthetische Schemaexporte
kompilieren; kein KAPT oder Room-Gradle-Plugin ist erforderlich.

Nach Eingang der manuellen Fairphone-Evidenz wurden alle oben genannten Tasks
einschließlich `test` erneut angefordert: BUILD SUCCESSFUL. Der unveränderte Code
wurde überwiegend als UP-TO-DATE erkannt; die aktuellen Unit-Reports enthalten
145 Tests, 0 Fehler und 0 Skips. Der erfolgreiche Instrumentation-Lauf mit 95
Tests wurde zum Dokumentationsabschluss nicht erneut ausgeführt. Kein neuer
manueller Geräteversuch, keine Geräte-/Bestandsmutation.

### Gemeinsame Android-Matrix

Die Instrumentation nutzt ausschließlich getrennte UUID-Fixtures im Test-APK
`org.pag.data.test`, zufällige flüchtige Schlüssel und eine Fake-Authentifizierung.
Sie beweist SQLCipher-/Room-Operationen und den realen Coordinator-Vertrag, nicht
die Eingabe eines Gerätecredentials oder einen echten Force-stop.

| Fall je Variante | Prüfkriterium |
|---|---|
| Bewusst neu / bestehend / Close / neue Generation | Marker erhalten; tatsächliche Connection geschlossen, alte API abgewiesen |
| Falscher / fehlender Key | Bestehende Dateien byte-identisch, keine Ersatz-DB |
| Header beschädigt / Datei abgeschnitten | Abweisung und keine Reinitialisierung |
| Version 99 / fehlende Migration | Kein Downgrade, kein Recreate; Bestand erhalten |
| Erwartete DB fehlt / unerwarteter Bestand | Keine Erstellung; keine unbekannten Dateien gelöscht |
| Unknown Sidecar / nichtleere Lockdatei | Inventar verweigert; kein Truncation-/Cleanup-Fallback |
| PK-/Foreign-Key-Verletzung | Fehler und vollständiger Rollback des Schreibversuchs |
| Gewollter Rollback | Ursprünglicher Datensatz erhalten |
| v1 -> v2 | Daten erhalten, revision=0, weitere v2-Schreiboperation möglich |
| Fehler nach ALTER in Migration | Schema und user_version bleiben v1; Datensatz lesbar |
| Deadline vor / nach Open | Kein zugelassener Zugriff, Handle geschlossen |
| Generation vor Query / Commit / Publication | Alter Kontext ungültig; Rollback bzw. keine Ergebnisfreigabe |
| Deadline vor Commit / Publication | Kein Commit bzw. keine Ergebnisfreigabe |
| Foreground-Verlust | Alter Handle nicht fachlich nutzbar |
| Wiederholtes Open/Close | Fünf kurze Lebenszyklen ohne Singleton |

Die 28 identischen Fälle laufen für DIRECT und ROOM. Finaler Lauf auf
Fairphone 6 / Android 16 / API 36: **95 Tests bestanden**, keine Fehler und keine
Skips. Davon 56 B2-Vergleichsfälle (28 Direct, 28 Room) und 39 vorhandene
B1-/B1.5-/B1.6-Instrumentationtests. Auch beide zuvor fehlgeschlagenen Room-
Commit-Widerrufstests bestehen nun einschließlich Rollback und erneuter Öffnung.
API-30- und API-37-Emulatoren waren nicht verbunden; keine AVDs wurden angelegt
oder neu konfiguriert. Diese Plattformläufe bleiben offen.

### Manuelle Fairphone-Evidenz: explizite Authentifizierung und Restart

Vom Nutzer berichtet: Fairphone 6 / Android 16 / API 36, installierte Haupt-App,
getrennte B2-Namensräume, ausschließlich synthetischer Bestand.

| Variante / Schritt | Direkt beobachteter Zustand / Test-ID |
|---|---|
| Direct, erster ausdrücklicher Init | LOCKED; genaue ursprüngliche Create-ID nicht rechtzeitig gelesen |
| Direct, unmittelbar erneuter Init | BLOCKED / B2-BOOTSTRAP-NOT-EMPTY |
| Direct, Force-stop, Neustart, bestehenden Bestand authentifizieren | READY / B2-DIRECT-RESTART-SUCCEEDED |
| Room, erster ausdrücklicher Init | LOCKED / B2-ROOM-DB-CREATE-SUCCEEDED |
| Room, Force-stop, Neustart, bestehenden Bestand authentifizieren | READY / B2-ROOM-RESTART-SUCCEEDED |

Direct: Der erfolgreiche Erstbootstrap ist durch den anschließend vorhandenen
Bestand, die abgewiesene erneute Initialisierung und den erfolgreichen Restart
stark gestützt. Die ursprüngliche Create-ID ist **nicht direkt beobachtet** und
wird nicht nachträglich behauptet. Der Restart-Nachweis ist direkt beobachtet.

Room: Die Create-/Force-stop-/erneute explizite Authentifizierungs-/Reopen-Kette
wurde vollständig direkt beobachtet. Dies stützt den kurzen B1.6-Key-Verbrauchspfad
auch für Room auf diesem Gerät. Kein automatisches Öffnen, kein Ersatzbestand
oder zeitbasierter Auth-Fallback ist Teil dieses Pfads. Der manuelle Befund ist
kein allgemeiner Android-/OEM-Nachweis und kein Nachweis eines Stromverlusts.
Die automatischen Fake-Auth-Tests bleiben getrennte Evidenz und werden dadurch
nicht in echte Credential-Tests umgedeutet. Keine invasiven Credential-/Keystore-
Tests und keine neuen manuellen Läufe zum Dokumentationsabschluss.

### Klartextinventar und Markergrenzen

Klartext ist während autorisierter Arbeit in JVM-Entity-/Record-Listen, Cursor-/
Stream-/SQLCipher-nativen Puffern und temporär als Schlüsselbytes vorhanden.
Room bringt zusätzliche Entity-Objekte, generierte Mappingpfade und interne
Coroutine-/Tracking-Zustände mit; Direct hat stattdessen eigenes Cursor-Mapping.
Beide müssen flüchtige Referenzen reduzieren. Keine Zusage vollständiger
Überschreibung von JVM-/nativen/SQLCipher-Kopien.

Nach jedem Instrumentationstest werden Dateien des Test-App-Datenbereichs auf
den auffälligen synthetischen Marker in UTF-8, ASCII, UTF-16LE und UTF-16BE geprüft.
Ein negativer Scan ist nur ein begrenztes Signal: keine forensische Analyse, kein
Nachweis vollständiger Klartextfreiheit, keine Prüfung aller OEM-/Systempuffer
und kein RAM-Nachweis. Ein Markerfund führt zum Testfehler; nichts wird zur
Verdeckung bereinigt. Room-Schema-JSON enthält nur synthetische Struktur, keine
Rows/Keys. Keine operativen Inhalte in Preferences/DataStore/SavedState/Logs.

Im abschließenden lesenden B1-Inventar wurden für die behaltenen B2-Fixtures
Hauptdateien `d` mit 24.576/28.672 Bytes, leere `d-journal` und leere Room-
`d.lck` beobachtet. Zusätzlich enthielt das Inventar bewusst beschädigte bzw.
negative Fixtures: `d` mit 33 Bytes sowie `d-wal`, nichtleere `d.lck` und
`unknown` mit je 1 Byte. Der 1-Byte-WAL-Eintrag ist eine künstliche Fehlerfixture,
kein Beleg, dass B2 im TRUNCATE-Pfad einen produktiven WAL erzeugt. Kein `d-shm`
wurde im B2-Dateiinventar beobachtet. Die negativen Fixtures wurden nicht
bereinigt. Die Markerprüfungen waren negativ; kein vollständiger Nebenbestands-
oder Forensiknachweis wird daraus abgeleitet.

Eine Zulassungsprüfung und der physische native Commit sind keine atomare
Einheit mit der Uhr. Läuft die Deadline erst nach einer gültigen Commit-Zulassung
während nativer Arbeit ab, kann diese Arbeit physisch noch enden. Das Gate
verweigert neue Schritte und spätere fachliche Veröffentlichung; es behauptet
keinen garantierten physischen Abbruch exakt zum Deadline-Zeitpunkt.

## Dependency-Fläche

Explizit neu: `room3-runtime:3.0.3` (Runtime), `room3-compiler:3.0.3` (KSP-
Compiler) und KSP-Gradle-Plugin 2.3.12 (Build-only). Kein neuer Kryptostack.

Room-spezifische Runtime-Artefakte einschließlich Plattformvarianten:

| Familie | Version / Artefakte |
|---|---|
| Room | 3.0.3: room3-runtime, room3-runtime-android, room3-common, room3-common-jvm |
| SQLite async | 2.7.1: sqlite-async, sqlite-async-android |
| SQLite framework | 2.7.1: sqlite-framework, sqlite-framework-android; transitiv vorhanden, nicht als Driver eingesetzt |

Weitere im aufgelösten Runtime-Abschluss vorkommende Familien sind Annotation
1.9.1 (inkl. JVM), annotation-experimental 1.5.0, Collection 1.5.0 (inkl. JVM/KTX),
Coroutines 1.9.0 (core/JVM/android/BOM), JetBrains annotations 23.0.0 und
JSpecify 1.0.0. Sie sind teilweise schon durch bestehende AndroidX-/Compose-
Abhängigkeiten vorhanden; dies ist kein Anspruch, jede dieser Familien erstmals
hinzugefügt zu haben. App-Kotlin bleibt 2.4.20. SQLCipher 4.19.1/SQLite 2.7.1
bleiben unverändert; keine sqlite-bundled-Engine und kein Netzwerk-SDK.

Der tatsächlich aufgelöste Compiler-Classpath enthält zusätzlich bzw. erneut:

- Room 3.0.3: compiler, compiler-processing, common/JVM, external-antlr,
  migration/JVM.
- auto-common 1.2.1; auto-value-annotations 1.6.3.
- Guava 33.2.1-jre; failureaccess 1.0.2; listenablefuture
  9999.0-empty-to-avoid-conflict-with-guava; jsr305 3.0.2;
  checker-qual 3.42.0; error_prone_annotations 2.26.1.
- JavaPoet 1.13.0; KotlinPoet/JVM/JavaPoet 2.0.0.
- Kotlin metadata JVM 2.3.20, stdlib 2.3.20 und reflect 2.0.10 im isolierten
  Compiler-Classpath; kein Downgrade der App-Kotlin-Version.
- kotlinx-serialization json/JVM, core/JVM und BOM 1.8.1.
- sqlite-jdbc 3.50.3.0 für Build-time Query-Prüfung; keine unverschlüsselte
  Hilfsdatenbank im Android-Runtime-Pfad.
- Annotation/JVM 1.9.1, IntelliJ annotations 12.0, JetBrains annotations 13.0,
  JSpecify 1.0.0.
- symbol-processing-api 2.0.10-1.0.24 als Room-Compiler-Transitivabhängigkeit;
  das tatsächlich verwendete KSP-Plugin ist 2.3.12.

Diese Compiler-Closure erhöht Build-/Supply-Chain-Fläche, ist aber keine
App-Runtime-Dependency. Room/KSP stammen aus den offiziellen AndroidX-/Google-
Projekten unter Apache-2.0; die vorhandene SQLCipher-Linie bleibt unverändert.
Ein vollständiger transitive-License-/Supply-Chain-Audit bleibt vor Produktfreigabe
erforderlich. Die aufgelösten Graphen sind lokale Testevidenz, keine Secrets.

## Manifest, Backup und Security-Review

Keine neuen Permissions. Weiterhin kein INTERNET, CAMERA, POST_NOTIFICATIONS
oder Storage-Zugriff. Nur die Launcher-Activity wird exportiert; kein Service,
Provider oder Receiver nach Manifest-Merge. USE_BIOMETRIC ist die vorhandene
Plattform-Prompt-Berechtigung, keine Freigabe biometrischer Authentifizierung.
FLAG_SECURE, Cloud-/Transfer-Ausschlüsse und noBackup-Speicher bleiben erhalten.

Neue Pfade enthalten weder Klartextkeys noch Key-Strings/Hex/Base64-Ausgabe,
Query-Logging, automatische Ersatz-Aliase/-Keys, destructive migration,
automatische Bereinigung unbekannter Dateien oder Framework-SQLite-Fallback.
SQL verwendet feste Texte und Parameterbinding. Eigene Cursor werden geschlossen,
Room-generierte Statements und native Verbindungen ebenfalls. Fehlermeldungen
enthalten feste technische IDs; kein Exceptiontext, Stacktrace oder Pfad in UI.

## Empfehlung und verbindliches PaG-Profil

**ROOM EMPFOHLEN** für die produktive relationale MVP-Persistenz in :data.
Die Entscheidung ist in [ARCH-36/37](architecture.md#7-strukturierter-bestand-und-verschlüsselter-fileasset-speicher)
übernommen. Direct SQLCipher bleibt technisch valide, Referenz-/Low-Level-
Testengine und mögliche spätere Alternative, wenn Room den Vertrag nicht mehr
ermöglicht. Ein Wechsel benötigt eine ausdrückliche Architekturentscheidung und
Nachweise; kein automatischer Runtime-Fallback.

Beide Varianten bestehen dieselbe Sicherheitsmatrix. Die Wahl beruht auf
Korrektheit und dauerhafter Fehlerreduktion: Room prüft Queries und Entity-Schema
beim Build, generiert Parameterbindung und Cursor-/Null-/Typ-Mapping und validiert
Schema/Migrationen zur Laufzeit. Das vermindert das spätere Risiko manueller SQL-,
Mapping-, Schema- und Queryfehler im wesentlich größeren MVP. Explizite
Migrationen und fachliche Tests bleiben notwendig; Room beweist nicht automatisch
die inhaltliche Richtigkeit einer Migration. Direct wird deshalb trotz seines
kleineren Dependency-Graphen nicht als bevorzugte Produktarchitektur gewählt.
Die Entscheidung beruht nicht auf Convenience.

Die Empfehlung gilt ausschließlich unter folgenden Auflagen:

1. Geprüfte Baseline: **Room 3.0.3 stable, KSP 2.3.12, AndroidX SQLite 2.7.1,
   SQLCipher 4.19.1** und bestehender kontrollierter SQLiteDriver/SQLCipher-Adapter.
2. Kein dauerhaftes Application-Singleton der RoomDatabase. DB nur innerhalb
   autorisierter Storage-Operationen öffnen, deterministisch schließen, keine
   automatische Wiederöffnung und keine Übernahme alter DB-/DAO-Handles.
3. Explizites CREATE gegenüber OPEN EXISTING; keine Standard-open-or-create-
   Semantik, keine destructive migration oder fallbackToDestructiveMigration,
   keine automatische Ersetzung/Reparatur eines nicht lesbaren Bestands.
4. Zentraler Session-/Generation-/Deadline-/Foreground-Vertrag vor relevanten
   Operationen und Schritten, Commit-Zulassung und Result-Publication. Ein
   widerrufener Commit muss auf derselben Connection rollbacken; Ressourcen-
   bereinigung ist keine erneute fachliche Freigabe.
5. Zunächst keine Flow-/LiveData-/Paging-/Rx-observable DAOs und kein
   MultiInstanceInvalidationService. Room-Typen bleiben vollständig in :data.
6. Nur die exakt bekannte reguläre **leere d.lck** als Room-Inventarartefakt
   akzeptieren. Nichtleere oder unbekannte Lock-/Sidecar-Dateien bleiben im
   produktiven Profil BLOCKED; keine automatische Bereinigung. Legitime
   Journal-/WAL-Zwischenzustände während autorisierter Arbeit sind von einer
   unklaren persistenten Ausgangslage zu unterscheiden und separat zu prüfen.
7. Keine Produkt-Keybytes außerhalb des bestehenden kurzen Key-Verbrauchspfads.
   Keine Secrets in Strings, UI, Logs, SavedState, Preferences oder langlebigen
   Caches. Eigene kontrollierte Kopien bestmöglich überschreiben; keine Garantie
   vollständiger JVM-/nativer Speicherlöschung.

**Nachweisgrenze des aktuellen Inventars:** B2 prüft unbekannte Dateinamen,
Dateitypen, Symlinks und nichtleere d.lck. Die bekannten nativen Namen d-wal,
d-shm und d-journal sind im aktuellen Prototyp zugelassen; es gibt keinen
allgemeinen automatischen Test, der jede nichtleere bekannte native Sidecar-
Ausgangslage blockiert. Das strengere Produktprofil aus Punkt 6 ist daher eine
Auflage für spätere Implementierung/Regressionstests, kein bereits vollständig
bestandener B2-Nachweis. Der Dokumentationsabschluss verändert dafür keinen Code.
Die endgültige Journal-/Recovery-/Durability-Policy bleibt offen.

### Sonderkosten und Dependency-Upgrades

Room ist nicht automatisch sicher. Zusätzliche Room-/KSP-Dependencies,
generierte Codepfade und der kontrollierte Driver-Adapter erhöhen Supply-Chain-
und Wartungsfläche. Room erzeugt d.lck; Commit-Widerruf benötigte eine explizite
Rollback-Behandlung; MultiInstanceInvalidationService musste ausgeschlossen
werden. Diese Kosten wurden in die Auswahl einbezogen, nicht verschwiegen.

Upgrades von Room, AndroidX SQLite, SQLCipher und KSP sind security-relevant.
Spätere Versionen gelten nicht automatisch als gleichwertig. Vor Freigabe erneut
prüfen: CREATE-vs-OPEN, d.lck/Sidecar-Inventar, Connection-/Statement-/DAO-Lifecycle,
Commit/Rollback bei Session-Widerruf, Multi-Instance-/Manifest-Komponenten,
Migrationen, Close und Schlüssel-Lebensdauer. Die gemeinsame Direct-/Room-
Failure-Matrix bleibt eine Regressionstestbasis.

### Weiterhin offene Nachweise

API 30 / API 37, Power-loss, echte Speicherknappheit, Credential-Wechsel/
Entfernung, gezielte Key-Invalidierung, endgültige Schlüsselhierarchie,
Produkt-Sessiondauern, WAL-/Durability-/Sidecar-Policy und sichere Migrationen des
echten MVP-Schemas bleiben offen. Keine Aussage zur vollständigen physischen
Flash-Löschung, OEM-Backup-/Transfer-Garantie, vollständiger RAM-Klartextfreiheit
oder Kontrolle eines kompromittierten OS. Performance wurde nur über funktionierendes
wiederholtes Open/Close geprüft, nicht als Benchmark oder Produkt-Latenzgarantie.

B2 ist ein Architekturvergleich mit begrenztem Realgerätenachweis und keine
fertige produktive Storage-Implementierung. Die offenen Plattform-/Ausfalltests
bleiben vor Produktfreigabe erforderlich; sie werden durch die Wahl von Room
nicht als bestanden erklärt.
