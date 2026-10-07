# Security-Prototyp A

## Status, Zweck und Nicht-Ziele

Stage A untersucht ausschließlich DEVICE_CREDENTIAL, Android Keystore und die technische Session-State-Machine gemäß ARCH-04 bis ARCH-13, ARCH-16, ARCH-23 und ARCH-26 bis ARCH-30. Keine Produkt-App, Fachentitäten, Datenbank, FileAssets, Import, Kamera, Viewer, Retention-Ausführung oder Recovery. Alle Inhalte sind synthetisch. Bestehende Spezifikationen bleiben maßgeblich; die folgenden Parameter gelten nur für dieses Experiment.

Der Marker `PAG_STAGE_A_SYNTHETIC_ONLY` ist ein öffentlicher Testwert, kein Geheimnis und kein Verschlüsselungsnachweis. Er erscheint erst nach ausdrücklich gestarteter Credential-Authentifizierung, gültiger Generation, synthetischer Maintenance und erfolgreicher synthetischer Schlüsseloperation. Unit Tests beweisen keine Sicherheit des Plattformdialogs, Keystores oder Geräts.

## Toolchain und Dependencies

Offizieller Versionsstand geprüft am 2026-10-07:

| Bestandteil | Festgelegte Prototypversion | Quelle / Zweck |
|---|---|---|
| Android Gradle Plugin | 9.4.1 stable | [Google Maven-Metadaten](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml), [AGP-Kompatibilität](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Gradle | 9.6.1 stable | [Gradle-Releases](https://gradle.org/releases/), Wrapper mit Distributions-SHA-256 |
| JDK | Temurin 17.0.20.1+1 | [Adoptium](https://adoptium.net/temurin/releases/?version=17); lokale Distribution mit veröffentlichter SHA-256 geprüft |
| Kotlin / Compose Compiler Plugin | 2.4.20 | [Kotlin-Release](https://kotlinlang.org/docs/whatsnew2420.html); AGP Built-in Kotlin, kein kotlin-android-Plugin |
| minSdk / compileSdk / targetSdk | 30 / 37 / 37 | Nur Prototypgrenzen, keine endgültige Produkt-Mindestversion |
| SDK Platform | Android 37.0, Revision 2 | Stabile Basis ohne Minor-API-Anforderung |
| SDK Build Tools | 36.0.0 | AGP-Standard; kein NDK benötigt |
| Activity Compose | 1.13.0 | [AndroidX Activity](https://developer.android.com/jetpack/androidx/releases/activity), Activity-/Compose-Brücke |
| Compose BOM | 2026.09.00 | [Stabile BOM](https://developer.android.com/develop/ui/compose/bom); UI und Material3 |
| JUnit | 4.13.2 | Nur JVM-Tests, nicht im App-Laufzeitbestand |

AndroidX/Kotlin stammen aus den offiziellen Google-/Maven-/Gradle-Repositories. AndroidX ist Apache-2.0, Kotlin Apache-2.0, JUnit EPL-1.0. Transitive Laufzeitabhängigkeiten und Manifestkomponenten müssen anhand des tatsächlich aufgelösten Builds überprüft werden. Kein DI-Framework, keine Networking-, Storage-, Kamera- oder AndroidX-Biometric-Dependency. Plattform-BiometricPrompt ist der einzige Authentifizierungsadapter.

Das Repository enthält genau `:app`, `:domain`, `:data`, `:security` mit dem ARCH-04-Graphen. `verifyArchitecture` prüft Projektabhängigkeiten und Implementierungsimporte im UI-Paket. Die Activity ist Composition Root; ViewModel und Composable erhalten nur den Domain-Port. Domain ist Kotlin/JVM ohne Android. Security kennt kein Projektmodul.

## Hypothesen und Entscheidungskriterien

| ID | Hypothese | Praktischer Nachweis | Entscheidung bei negativem Ergebnis |
|---|---|---|---|
| A-01 | Plattform-BiometricPrompt kann ausschließlich DEVICE_CREDENTIAL auf API 30 und 37 verlangen | Credential-Dialog, Abbruch, fehlende Gerätesperre, biometrisch eingerichtetes Testgerät; Callback-Typ muss DEVICE_CREDENTIAL sein | Befund melden; kein AndroidX-/Biometrie-Fallback |
| A-02 | App-Sitzung bleibt unabhängig von Geräte-/Keystore-Autorisierung | Start, Geräteentsperrung, Rückkehr und Prozessneustart bleiben LOCKED; nur expliziter frischer Flow öffnet das Gate | Session-/Lifecycle-Implementierung korrigieren, keine Plattformautorisierung als Freigabe verwenden |
| A-03 | Session-Generation verhindert alte/parallele Freigaben | Doppelklick, alte Callbacks, Sperrung während Maintenance/Testzugriff, Abbruch, neue Generation | Blocker vor jeder weiteren Prototypstufe |
| A-04 | Operationsgebundener Credential-only-Keystore-Zugriff ist praktisch verwendbar | Eigener Alias, timeout=0, AUTH_DEVICE_CREDENTIAL, CryptoObject; einmalige konkrete Operation; zweiter Zugriff ohne neue Operation abgewiesen | Variante als nicht tragfähig dokumentieren; kein stiller schwächerer Schlüssel |
| A-05 | Zeitfenster kann getrennt von PaG-Sitzung untersucht werden | Eigener Alias, Testfenster 15 Sekunden, expliziter Prompt ohne CryptoObject; Zugriff sofort und nach Ablauf; App-Sperrung unabhängig | Ergebnis mit A-04 vergleichen; keine produktive Fensterentscheidung in Stage A |
| A-06 | Neustart, Credential-Änderung/-Entfernung und Herstellerzustände sind kontrolliert behandelbar | Nur dediziertes synthetisches Testgerät: Neustart, Credential-Änderung/-Entfernung, fehlender/invalidierter Schlüssel, KeyInfo-Eigenschaften, Backup/Transfer und Oberflächenschutz | Fail closed; bestehende Schlüssel nicht löschen/ersetzen; Hardware-/Lifecycle-Anforderungen erst nach Befund festlegen |

## Konkrete Implementierung und Grenzen

`SessionCoordinator` ist das einzige Gate. Zustand, Generation, Zugriff und synchroner Ergebnisversand werden gemeinsam synchronisiert. Authentifizierung ist nur aus LOCKED und zulässigem Vordergrund möglich. Doppelte Starts, doppelte/alte Auth-Ergebnisse und alte Maintenance-Ergebnisse werden verworfen; übergebene alte Operationshandles werden freigegeben. Jede Sperrung erhöht die Generation, entfernt den Marker und invalidiert die laufenden Handles. BLOCKED erlaubt keinen Zugriff; „Sperren“ führt zu LOCKED, repariert aber keinen Schlüssel.

`SyntheticMaintenance` führt ausschließlich einen injizierten verzögerten Callback aus; Android-Verdrahtung: eine Sekunde. Keine Datenquelle wird geöffnet oder auf Integrität geprüft. Dieser Dummy ersetzt die Storage-Schritte aus ARCH-12 ausdrücklich nur im Test. Er belegt weder Retention noch Import-/Löschkonsistenz.

Keystore-Schlüssel sind ausschließlich synthetische, persistente Android-Keystore-Aliase mit getrennten Verantwortlichkeiten für die zwei Versuche. Vorhandene Aliase werden auf Schlüsseltyp und Authentifizierungsparameter geprüft, niemals automatisch gelöscht oder ersetzt. Es werden keine produktiven DB-/Dateischlüssel erzeugt. Die synthetische Operation nutzt die etablierte Plattform-JCA-API AES/GCM/NoPadding, 256 Bit, Provider-generierter IV; kein eigenes kryptografisches Verfahren oder Speicherformat. Das ist keine Auswahl für Stage B. Ergebnis, IV, Schlüsselbytes und Credentials werden nicht angezeigt, geloggt oder gespeichert. Kein Entschlüsselungs-/Recovery-Weg.

Operationsgebunden: vorbereiteter Cipher als CryptoObject, erst nach Credential-Callback und Maintenance einmal verwendbar. Verbrauch wird separat gemeldet. Zeitfenster: nach explizitem Prompt jeweils neue Cipher-Operation; 15 Sekunden sind ein Versuchsparameter. Prompt-Erfolg ist kein Beweis für spätere Keystore-Autorisierung. Diese bleibt NOT_TESTED bis zur konkreten Operation. Ein erfolgreicher Zugriff belegt nur diesen Zeitpunkt; nach Ablauf kann eine weitere Operation scheitern. Fehler sperren den Test, entfernen den Marker und eröffnen keine schwächere Konfiguration.

KeyInfo zeigt ausschließlich technische Eigenschaften: Software/TEE/StrongBox beziehungsweise allgemeine Hardwareeigenschaft auf API 30 und hardwareseitige Authentifizierungsdurchsetzung. StrongBox wird nicht angefordert, kein Hardwaretyp wird vorausgesetzt; kein Fallback von einer angeforderten Hardwareanforderung. Geräte-/Herstellervergleiche müssen diese Beobachtung dokumentieren. Nicht-exportierbarkeit schützt nicht gegen ein kompromittiertes OS oder missbräuchlich autorisierte Operationen.

Ein ausschließlich synthetisches, nicht vertrauliches Inventar in privaten SharedPreferences hält je Testalias nur „bereits angelegt“ fest. Keine Credentials, Schlüsselbytes, Sitzungsfreigaben oder Fachmetadaten darin. Fehlt ein bekannt angelegter Alias, wird blockiert statt ein Ersatzschlüssel erzeugt. Ein Inventar-Schreibfehler blockiert ebenfalls, ohne den vorhandenen Schlüssel zu löschen. Keystore und Inventar sind nicht atomar: Process Death zwischen erster Schlüsselerzeugung und Inventar-Commit lässt einen vorhandenen Alias zurück, der vor weiterer Nutzung geprüft wird. Das Inventar ist vom Backup/Transfer ausgeschlossen und kein Vorschlag für produktive Schlüsselverwaltung.

### Lifecycle und Dialogrückkehr

Frischer Coordinator startet LOCKED. Keine sensitive Zustandswiederherstellung über SavedStateHandle/rememberSaveable und kein persistiertes Sessionflag. Activity-Recreation verwirft ebenfalls die Sitzung; dies ist bewusst konservativ. `onPause` und Fokusverlust invalidieren MAINTENANCE/READY, `onStop` und benutzerausgelöster Taskwechsel über `onUserLeaveHint` invalidieren immer, ebenso ACTION_SCREEN_OFF und Gerätesperre beim Vordergrundwechsel. Der Broadcast-Receiver ist dynamisch, ab API 33 nicht exportiert und verarbeitet keine fachlichen Inhalte. Eine injizierte aktuelle Prüfung von resumed, Fensterfokus und Gerätesperre schützt außerdem jeden Testzugriff und seine Ergebnisveröffentlichung, unabhängig von einem möglicherweise noch ausstehenden Lifecycle-Callback.

AUTHENTICATING enthält keine Inhaltsfreigabe. Eigener Systemdialog darf Pause/Fokusverlust auslösen; sein Erfolg wird gegebenenfalls kurzlebig bis zur zulässigen resumed/fokussierten Activity zurückgehalten. `onStop`/Sperrung invalidiert auch diesen Versuch und verwirft das Ergebnis. Dies ist keine Ausnahme für READY oder Systempicker. Kein automatischer neuer Prompt.

**Beobachteter praktischer Stand:** DEVICE_CREDENTIAL-Authentifizierung und Rückkehr nach READY funktionierten in den unten dokumentierten manuellen Tests auf API-30-/API-37-Emulatoren und einem echten Smartphone. Home/App-Wechsel, Gerätesperre und Dialogabbruch verhielten sich in den getesteten Fällen wie vorgesehen. Die genaue Callback-/Lifecycle-Reihenfolge wurde damit nicht vollständig nachgewiesen. Falls ein anderer Plattformdialog `onStop` oder `onUserLeaveHint` auslöst, bricht der konservative Adapter weiterhin ab; kein stilles Abschwächen der Sperre. Offen bleiben insbesondere Wechsel während aller einzelnen Phasen, transparente Activities, Multi-Window, Konfigurationswechsel, Notification Shade und schnelle Lock/Unlock-Sequenzen. AUTHENTICATING-Pause/Fokusverlust ist wegen des eigenen Systemdialogs nicht allein als App-Wechsel klassifizierbar.

### Manifest, Backup und Oberflächen

USE_BIOMETRIC ist für den Plattform-BiometricPrompt erforderlich ([API-Vertrag](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt)); die erlaubte Authentifizierungsmaske und Schlüsselrichtlinie sind ausschließlich DEVICE_CREDENTIAL. Keine biometrische Authentifizierung. Keine INTERNET-, CAMERA-, Speicher-, Kontakt-, Telefon- oder Standortpermission. Keine Deep Links oder fachlichen Provider/Receiver. Die notwendige Launcher-Activity ist exportiert; jeder Start ist geschützt.

FLAG_SECURE gilt ab dem ersten Activity-Start. Ab API 33 zusätzlich keine Recent-App-Screenshots. Kein Clipboard-Code und keine eigenen Logs. Technische Fehler werden ausschließlich als feste Test-IDs ausgegeben, keine Exceptiontexte. Externe Fotografie und kompromittierte Systeme bleiben außerhalb des Schutzversprechens.

allowBackup=false sowie vollständige Ausschlüsse in Legacy-Backupregeln und Data-Extraction-Regeln für Cloud und Device Transfer. Credential-/Device-Protected Root, Dateien, Datenbanken, Preferences und External-Bereiche sind erfasst. Cache/noBackup-Bereiche sind durch Plattformregeln zusätzlich ausgeschlossen. Kein Backup-Agent, Export oder Recovery-Key. Konfiguration beweist kein OEM-Transferverhalten; reale Tests bleiben erforderlich.

## Automatische Tests

`data/src/test/.../SessionCoordinatorTest.kt` verwendet ausschließlich kontrollierte Auth-/Maintenance-/Operations-Fakes. Prüft Start LOCKED, gültige Übergänge, fehlenden Vordergrund, ungültigen Auth-Start aus READY, parallelen Start, Generation bei jeder Sperrung, alte/duplizierte Auth-Callbacks, alte Ergebnisse nach neuer Session, Sperre in Maintenance, READY→LOCKED, Zugriff in jedem Nicht-READY-Zustand, Dialogabbruch ohne Prompt-Schleife, Key-Fehler ohne Fallback, reine Geräteentsperrung, Vordergrundverlust, Autorisierungsablauf und Sperrung während einer Operation.

Build-/Prüfbefehle, mit JDK17 und projektlokalen Caches:

```sh
export JAVA_HOME="$PWD/.prototype-tools/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="$PWD/.prototype-cache/gradle"
export ANDROID_USER_HOME="$PWD/.prototype-cache/android"
./gradlew test
./gradlew :app:assembleDebug :app:lintDebug :data:lintDebug :security:lintDebug :app:verifyPrototypeManifest
./gradlew :app:dependencies --configuration debugRuntimeClasspath
git diff --check
git diff --stat
git status --short
```

JDK/SDK sind lokale, ignorierte Hilfsmittel; auf anderen Entwicklungsrechnern vorhandenes JDK17/SDK verwenden und local.properties setzen. Debug-Signatur liegt projektlokal im ignorierten Cache. Offizieller Wrapper-JAR und Gradle-Distribution wurden mit veröffentlichten SHA-256-Werten geprüft. Kein Build Scan, keine Telemetrie im Prototyp.

## Emulator- und Realgerätetestplan

Alle folgenden Tests sind **Plattform-/Instrumentation- beziehungsweise interaktive Realgerätetests**, keine bestandenen Unit Tests. Kein automatisierter Credential-Eingabeweg, keine fest codierte Geräte-PIN. Testcredentials ausschließlich im Systemdialog manuell verwenden, niemals im Repository dokumentieren. Reale Geräte nur dediziert für synthetische Daten verwenden; Credential-Entfernung kann absichtlich Plattform-Schlüssel invalidieren und darf nicht auf einem Gerät mit benötigten Daten getestet werden.

| Test-ID | Ablauf | Erwartung | Emulator API30/37 | Echtes Gerät |
|---|---|---|---|---|
| A-P01 | Start ohne Auth; Gerät entsperren; App zurückholen | LOCKED, kein Marker, kein Prompt | Beide | Beide API-Grenzen |
| A-P02 | Jede Variante ausdrücklich starten; korrektes Credential; Testzugriff | AUTHENTICATING→MAINTENANCE→READY, Marker erst bei Operationssuccess; kein biometrischer Dialog | Beide | Zwingend, auch mit eingerichteter Biometrie |
| A-P03 | Dialog abbrechen; mehrfach schnell Auth starten | LOCKED, maximal ein Flow, kein Folgeprompt | Beide | Zwingend |
| A-P04 | Home/andere App während Auth, Maintenance und READY; danach zurück | Generation invalidiert; kein spätes READY/Marker, frische Benutzeraktion nötig | Beide | Zwingend wegen Dialog-/OEM-Lifecycle |
| A-P05 | Gerätesperre in allen Phasen, entsperren, zurück | LOCKED, keine Wiederherstellung | Beide | Zwingend |
| A-P06 | Maintenance sperren; Rotation; Prozess beenden/neu starten; Activity recreation | Keine alte Freigabe, frischer Prozess LOCKED | Beide | Zwingend |
| A-P07 | Per-operation: einmal Testzugriff, erneut Testzugriff | Erste Operation erfolgreich oder kontrolliert nicht unterstützt; zweite CONSUMED, keine stille neue Auth/Operation | Beide | Zwingend, Hardwarebindung |
| A-P08 | Zeitfenster: sofort zugreifen, ohne weitere Geräteauth >15s warten, erneut zugreifen | Ablauf wird bei neuer Operation festgestellt; BLOCKED ohne Marker, kein automatischer Prompt | Beide | Zwingend |
| A-P09 | App innerhalb Plattformfenster sperren; zurück | Plattformfenster erzeugt kein READY; neue explizite Auth erforderlich | Beide | Zwingend |
| A-P10 | Gerät neu starten; Credential ändern/entfernen; erneut versuchen | LOCKED; kontrollierter Fehler bei Invalidierung; keine automatische Aliaslöschung/-ersetzung | Grenzen als ergänzender Softwaretest | Zwingend auf dediziertem Gerät |
| A-P11 | KeyInfo-Werte erfassen | Hardware-/Auth-Eigenschaften nur Beobachtung, keine Hardwaregarantie | Softwarewerte dokumentieren | Zwingend für TEE/StrongBox-Eigenschaften |
| A-P12 | Screenshot/Recents, Fokusverlust/Notification Shade, Multi-Window | Kein ungeschützter Marker; konservative Sperre; Verhalten protokollieren | Beide | Zwingend |
| A-P13 | Cloud-Backup/Restore und Device Transfer synthetischer Installation | Keine App-Daten-/Sessionübernahme; OEM-Abweichungen melden | Nur begrenzter Test | Zwingend, Herstellerabhängigkeit |
| A-P14 | APK-/Manifest-/Logcat-/privater Speicher-Review | Keine Netzwerkfähigkeit, unerwartete Exports, Credentials/Keybytes oder zusätzliche Klartextpersistenz | Beide | Ergänzen |

## Abbruchkriterien und Risiken

Stop vor Stage B bei Gate-Bypass, Marker außerhalb gültiger READY-Session, Wiederverwendung alter Callbacks, biometrischer Authentifizierungsmöglichkeit, still schwächerer Key-Konfiguration, unkontrollierter Schlüsselersetzung, unerwarteten Exports/Permissions oder ungeschützter Persistenz. Eine nicht unterstützte Keystore-Variante wird kontrolliert protokolliert, nicht durch Umkonfiguration „repariert“. Falls beide Varianten scheitern oder Lifecycle-Schutz und Plattformdialog nicht vereinbar sind, Architekturentscheidung zur erneuten Prüfung vorlegen.

Debug-APK, ADB und Instrumentation erweitern die Testangriffsfläche; niemals mit Echtdaten verwenden. Die Markerquelle ist öffentlich. Keystore-/Provider-/OS-RAM kann Klartext-/Operationszustände halten; vollständige Speicherüberschreibung wird nicht behauptet. Testfenster ist keine Empfehlung für die Produkt-App. Kein zuverlässiger Schutz gegen kompromittiertes Android, physisches Fotografieren oder privilegierte Kopien. Maintenancedummy und Fakes sind keine Storage-, Retention-, Flash-Lösch- oder Backup-Nachweise.

## Für Stage B verbindlich vorgemerkt

AndroidKeysetManager ist ausgeschlossen: automatische/kompatibilitätsbedingte Klartext-Keyset-Behandlung passt nicht zum Fail-closed-Modell. Tink Streaming AEAD bleibt Kandidat, benötigt separat geprüfte fail-closed Schlüsselverwaltung. Associated Data ist nicht verschlüsselt und darf keine vertraulichen fachlichen Metadaten enthalten. Noch keine Tink-Dependency oder Keyset-Implementierung.

Primär stabile SQLCipher-for-Android-Linie vergleichen: direkte Nutzung gegenüber SQLCipher + Room. Keine Beta-/Prerelease-Basis. Room ist eine Modellierungs-/Zugriffsschicht und keine zweite Verschlüsselungsengine. Keine dieser Dependencies in Stage A. Endgültige Algorithmen, Bibliotheken, Hierarchie, Wrapping, Hardwareanforderungen und Produkt-Mindestversion bleiben offen.

## Ergebnisprotokoll

### Lokale Build-/Unit-Prüfung

Lokaler Lauf am 2026-10-07: `./gradlew test` bestanden, 19 Tests, keine Fehler/Failures/Skipped. `:app:assembleDebug`, `:app:lintDebug`, `:data:lintDebug`, `:security:lintDebug`, `verifyArchitecture` und `:app:verifyPrototypeManifest` bestanden. App-Lint: 0 Fehler, 1 Hinweis auf neueres Gradle 9.8.0; die freigegebene 9.6.x-Basis bleibt bewusst bestehen. Data-/Security-Lint: keine Befunde. Zwei ausschließlich auf Manifest-Entfernungsmarkierungen begrenzte MissingClass-Unterdrückungen sind begründet; die separate Prüfung stellt sicher, dass die entfernten Komponenten tatsächlich fehlen.

Debug-APK: `app/build/outputs/apk/debug/app-debug.apk`. Kompilierte SDK-/Manifestwerte durch aapt2 am APK bestätigt: min 30, compile/target 37. Im gemergten und verpackten Manifest ausschließlich die exportierte Launcher-Activity `org.pag.prototype.MainActivity`, keine Provider, Services, Receiver oder Activity-Aliase. Permissions: `android.permission.USE_BIOMETRIC` und die von AndroidX Core erzeugte eigene Signature-Permission `org.pag.prototype.a.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Diese ist keine fachliche Runtime-Berechtigung. Keine INTERNET-/CAMERA-/Storage-Permission. Automatische Startup-/Emoji-/Process-Lifecycle-/Profile-Initialisierung sowie der exportierte ProfileInstaller-Receiver wurden entfernt; die transitiven Libraries bleiben Bestandteil des Standard-Compose-Graphen.

`git diff --check` und zusätzliche Whitespace-Prüfungen sämtlicher neuer Textdateien bestanden. Neue Dateien bleiben untracked; daher enthält `git diff --stat` allein nur die Änderung der .gitignore. Keine Dateien gestaged, kein Commit/Push. Alle bisherigen Spezifikationen bleiben unverändert. Gegenprüfung: keine neue normative Produkt-/Storage-Entscheidung; API-Bereich, Testfenster und synthetische JCA-Operation sind klar als Prototypkonfiguration begrenzt. Die offenen Architekturfragen zu produktiven Algorithmen, Schlüsselhierarchie, Hardwareanforderungen und Mindestversion bleiben offen.

Die Unit Tests allein bestätigen kein Android-Plattformverhalten. Inzwischen wurden die nachfolgend dokumentierten manuellen Emulator-/Realgeräteversuche durchgeführt. Die getesteten funktionalen Architekturannahmen wurden dabei nicht widerlegt; daraus folgt keine vollständige Sicherheitsfreigabe oder endgültige Produktentscheidung zu Keystore-Bindung, Hardwareanforderungen oder Autorisierungsdauer.

### Tatsächlich beobachtete manuelle Ergebnisse

**Quelle:** Vom Projektverantwortlichen berichtete manuelle Tests mit synthetischen Inhalten auf einem API-37-Emulator, einem API-30-Emulator und einem echten Android-Smartphone. Keine erneute Testausführung im Rahmen dieser Dokumentationsänderung. Testdatum, APK-/Quellstand beziehungsweise Hash, Emulatorprofile sowie API-Version, Security Patch und Build-ID des Smartphones wurden nicht angegeben und werden nicht ergänzt oder aus der Build-Prüfung abgeleitet. Phasenspezifische Testabdeckung wird nur soweit berichtet als bestanden geführt.

| Tatsächlich durchgeführter Fall | API-37-Emulator | API-30-Emulator | Echtes Smartphone |
|---|---|---|---|
| Frischer App-Start und App-Neustart → LOCKED | Bestanden | Bestanden | Bestanden |
| Explizite operationsgebundene DEVICE_CREDENTIAL-Authentifizierung → READY | Bestanden | Bestanden | Bestanden |
| Testzugriff in gültiger READY-Session erfolgreich | Bestanden; Autorisierung und Operation SUCCEEDED | Bestanden | Bestanden |
| Manuelles Sperren → LOCKED; Testzugriff danach nicht möglich | Bestanden | Bestanden | Bestanden |
| Home/App-Wechsel → automatische Sperrung | Bestanden | Bestanden | Bestanden |
| Android-Gerätesperre sperrt PaG; Geräteentsperrung allein öffnet PaG nicht | Bestanden | Bestanden | Bestanden |
| Abbruch des Credential-Dialogs lässt PaG gesperrt | Bestanden | Bestanden | Bestanden |
| Zeitautorisierter 15-Sekunden-Pfad funktioniert zunächst | Bestanden | Bestanden | Bestanden |
| Schlüsselzugriff nach Ablauf des Zeitfensters verweigert; fail closed | Bestanden; genauer Fehlerzustand siehe unten | Bestanden | Bestanden |

Auf dem API-37-Emulator nach Ablauf des Zeitfensters tatsächlich beobachtet:

```text
Session: BLOCKED
Keystore-Autorisierung: DENIED
Konkrete Schlüsseloperation: DENIED
Test-ID: A-KEY-AUTHORIZATION-DENIED
```

Für API 30 wurde keine Abweichung vom erwarteten Session-Verhalten berichtet. Auf dem Smartphone verhielten sich die gleichen nicht-invasiven funktionalen Fälle wie auf den Emulatoren; READY wurde nur nach expliziter PaG-Authentifizierung erreicht. Die genaue Fehleranzeige nach Zeitfensterablauf wurde für API 30 und das Smartphone nicht separat angegeben; der beobachtete Fail-closed-Ausgang ist bestätigt.

| Umgebung | Beobachtete Schlüsseleigenschaften | Einordnung |
|---|---|---|
| API-37-Emulator | SOFTWARE; authHardware=false | Softwarebefund dieser Testumgebung |
| API-30-Emulator | Nicht angegeben | Keine Hardwareeigenschaft aus dem funktionalen Erfolg ableiten |
| Echtes Smartphone | TEE; authHardware=true | Gerätebezogener Befund für die untersuchten synthetischen Schlüssel |

TEE/authHardware=true ist **keine allgemeine Produktgarantie** und kein Nachweis, dass alle später unterstützten Geräte hardwaregestützte Schlüssel besitzen. Es bedeutet nicht, dass Klartexte oder die gesamte App innerhalb der TEE verarbeitet werden. Es ist **kein StrongBox-Nachweis**. Die Beobachtung ersetzt keine Festlegung der produktiven Hardwareanforderungen und keinen Schutzbeweis gegen ein kompromittiertes Betriebssystem.

### Bestanden, noch offen und nicht hinreichend nachgewiesen

- **Bestanden:** Die oben einzeln dokumentierten manuellen Funktionsfälle auf den drei Testumgebungen. A-01, A-02, A-04 und A-05 werden dadurch für diesen berichteten Umfang praktisch gestützt; ihre weitergehenden Prüfpunkte bleiben offen. A-06 ist nur hinsichtlich App-Start/-Neustart und der angegebenen Schlüsseleigenschaften teilweise geprüft.
- **Noch offen:** Gerätecredential ändern, Gerätecredential entfernen, gezielte Schlüsselinvalidierung und Verhalten bei verlorenem/ungültigem Schlüssel. Diese invasiven Tests vorzugsweise auf einem dedizierten synthetischen Testgerät ausführen. Geräteneustart, fehlende Gerätesperre, biometrisch eingerichtete Testkonfiguration, zweiter operationsgebundener Zugriff/CONSUMED sowie Doppelklick-/Parallelversuche sind durch die vorliegenden manuellen Angaben ebenfalls nicht als geprüft belegt.
- **Noch offen:** Vollständige phasenspezifische Lifecycle-/Race-Tests, alte Auth-Callbacks nach neuer Session, Sperrung während Maintenance, Rotation/Activity-Recreation und ausdrücklich erzwungener Process Death. App-Neustart ist kein hinreichender Nachweis aller Process-Death-Fälle; A-03 bleibt auf Plattformebene offen trotz bestehender Unit Tests.
- **Nicht hinreichend nachgewiesen:** OEM-/Cloud-Backup-/Restore-/Device-Transferverhalten, Screenshot-/Recent-Apps-Schutz und dynamischer Logcat-/Speicher-Review. Die bisherigen Build-/Manifestprüfungen bleiben gültige statische Befunde, ersetzen aber diese praktischen Prüfungen nicht.
- **Nicht hinreichend nachgewiesen:** Allgemeine Tragfähigkeit auf weiteren Geräten, vollständige Hardware-/Keystore-Sicherheitsgarantien und produktive Storage-/Retention-Sicherheit. Der Maintenance-Dummy und die synthetische Schlüsseloperation liefern dafür weiterhin keinen Nachweis.

### Vorlage für reale Testläufe

| Feld | Eintrag |
|---|---|
| Datum / Testlauf-ID | offen |
| API / Security Patch / Build-ID | offen |
| Synthetisches Testgerät / Emulatorprofil | offen; keine persönlichen Gerätekennungen |
| APK-/Quellstand / SHA-256 | offen |
| Variante / Test-ID | offen |
| Credential vorhanden / Biometrie eingerichtet | nur ja/nein, niemals Credential notieren |
| KeyInfo-Hardware-/Auth-Eigenschaften | offen |
| Beobachtete State-/Generationsfolge | offen |
| Erwartetes / tatsächliches Ergebnis | offen |
| Lifecycle-/Key-Ausnahme, nur technische Kategorie | offen |
| Bestanden / widerlegt / nicht prüfbar | offen |
| Folgeentscheidung / Restgrenzen | offen |

Emulator- und Realgeräteläufe: **im oben beschriebenen funktionalen Umfang durchgeführt und bestanden**. Die Vorlage dient der Ergänzung fehlender Laufmetadaten und weiterer Tests. Noch offene beziehungsweise nicht hinreichend nachgewiesene Punkte bleiben ausdrücklich von den bestandenen Fällen getrennt; Stage A ist dadurch nicht vollständig abgeschlossen.

## Tatsächlich aufgelöster Runtime-Graph

Direkt angefordert: Activity Compose 1.13.0, Compose BOM 2026.09.00, Compose UI und Material3 über die BOM; Kotlin-Stdlib kommt über die Kotlin-Plugins hinzu. JUnit 4.13.2 ist ausschließlich Testabhängigkeit. Build-Plugins sind AGP und die Kotlin-JVM-/Compose-Compiler-Plugins wie oben angegeben. Keine Storage-, Kamera-, Netzwerk- oder AndroidX-Biometric-Library.

Folgende externe Koordinaten wurden im `debugRuntimeClasspath` aufgelöst, einschließlich BOMs, Constraints und KMP-/Android-Artefaktpaaren; die Liste ist keine Zählung separater APK-Bibliotheken. Alle aufgelösten Versionen sind ohne Alpha/Beta/RC/Snapshot-Suffix. Diese Prüfung ist kein vollständiges Vulnerability-Audit. Lizenz-/Update- und transitive Codeprüfung bleiben vor Foundation und Echtdaten erforderlich.

```text
androidx.activity:activity:1.13.0
androidx.activity:activity-compose:1.13.0
androidx.activity:activity-ktx:1.13.0
androidx.annotation:annotation:1.9.1
androidx.annotation:annotation-experimental:1.4.1
androidx.annotation:annotation-jvm:1.9.1
androidx.arch.core:core-common:2.2.0
androidx.arch.core:core-runtime:2.2.0
androidx.autofill:autofill:1.0.0
androidx.collection:collection:1.5.0
androidx.collection:collection-jvm:1.5.0
androidx.collection:collection-ktx:1.5.0
androidx.compose:compose-bom:2026.09.00
androidx.compose.animation:animation:1.12.1
androidx.compose.animation:animation-android:1.12.1
androidx.compose.animation:animation-core:1.12.1
androidx.compose.animation:animation-core-android:1.12.1
androidx.compose.foundation:foundation:1.12.1
androidx.compose.foundation:foundation-android:1.12.1
androidx.compose.foundation:foundation-layout:1.12.1
androidx.compose.foundation:foundation-layout-android:1.12.1
androidx.compose.material:material-ripple:1.12.1
androidx.compose.material:material-ripple-android:1.12.1
androidx.compose.material3:material3:1.4.0
androidx.compose.material3:material3-android:1.4.0
androidx.compose.runtime:runtime:1.12.1
androidx.compose.runtime:runtime-android:1.12.1
androidx.compose.runtime:runtime-annotation:1.12.1
androidx.compose.runtime:runtime-annotation-android:1.12.1
androidx.compose.runtime:runtime-retain:1.12.1
androidx.compose.runtime:runtime-retain-android:1.12.1
androidx.compose.runtime:runtime-saveable:1.12.1
androidx.compose.runtime:runtime-saveable-android:1.12.1
androidx.compose.ui:ui:1.12.1
androidx.compose.ui:ui-android:1.12.1
androidx.compose.ui:ui-geometry:1.12.1
androidx.compose.ui:ui-geometry-android:1.12.1
androidx.compose.ui:ui-graphics:1.12.1
androidx.compose.ui:ui-graphics-android:1.12.1
androidx.compose.ui:ui-text:1.12.1
androidx.compose.ui:ui-text-android:1.12.1
androidx.compose.ui:ui-unit:1.12.1
androidx.compose.ui:ui-unit-android:1.12.1
androidx.compose.ui:ui-util:1.12.1
androidx.compose.ui:ui-util-android:1.12.1
androidx.concurrent:concurrent-futures:1.1.0
androidx.core:core:1.18.0
androidx.core:core-ktx:1.18.0
androidx.core:core-viewtree:1.0.0
androidx.customview:customview-poolingcontainer:1.0.0
androidx.emoji2:emoji2:1.4.0
androidx.graphics:graphics-path:1.0.1
androidx.interpolator:interpolator:1.0.0
androidx.lifecycle:lifecycle-common:2.9.4
androidx.lifecycle:lifecycle-common-java8:2.9.4
androidx.lifecycle:lifecycle-common-jvm:2.9.4
androidx.lifecycle:lifecycle-livedata-core:2.9.4
androidx.lifecycle:lifecycle-process:2.9.4
androidx.lifecycle:lifecycle-runtime:2.9.4
androidx.lifecycle:lifecycle-runtime-android:2.9.4
androidx.lifecycle:lifecycle-runtime-compose:2.9.4
androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4
androidx.lifecycle:lifecycle-runtime-ktx:2.9.4
androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4
androidx.lifecycle:lifecycle-viewmodel:2.9.4
androidx.lifecycle:lifecycle-viewmodel-android:2.9.4
androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4
androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4
androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4
androidx.navigationevent:navigationevent:1.0.0
androidx.navigationevent:navigationevent-android:1.0.0
androidx.navigationevent:navigationevent-compose:1.0.0
androidx.navigationevent:navigationevent-compose-android:1.0.0
androidx.profileinstaller:profileinstaller:1.4.0
androidx.savedstate:savedstate:1.3.2
androidx.savedstate:savedstate-android:1.3.2
androidx.savedstate:savedstate-compose:1.3.2
androidx.savedstate:savedstate-compose-android:1.3.2
androidx.savedstate:savedstate-ktx:1.3.2
androidx.startup:startup-runtime:1.1.1
androidx.tracing:tracing:1.2.0
androidx.versionedparcelable:versionedparcelable:1.1.1
androidx.window:window:1.5.0
androidx.window:window-core:1.5.0
androidx.window:window-core-android:1.5.0
com.google.guava:listenablefuture:1.0
org.jetbrains:annotations:23.0.0
org.jetbrains.kotlin:kotlin-stdlib:2.4.20
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0
org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3
org.jspecify:jspecify:1.0.0
```

## Erzeugte und geänderte Repository-Dateien

Geändert: `.gitignore` (Quellmodul `:data` nicht mehr pauschal ignorieren; projektlokale Werkzeuge/Caches ausschließen). Neu:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/ui/PrototypeScreen.kt`
- `app/src/main/kotlin/org/pag/prototype/ui/PrototypeViewModel.kt`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `build.gradle.kts`
- `data/build.gradle.kts`
- `data/src/main/AndroidManifest.xml`
- `data/src/main/kotlin/org/pag/data/SessionCoordinator.kt`
- `data/src/main/kotlin/org/pag/data/SyntheticMaintenance.kt`
- `data/src/test/kotlin/org/pag/data/SessionCoordinatorTest.kt`
- `docs/prototype-a.md`
- `domain/build.gradle.kts`
- `domain/src/main/kotlin/org/pag/domain/Session.kt`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `security/build.gradle.kts`
- `security/src/main/AndroidManifest.xml`
- `security/src/main/kotlin/org/pag/security/ExperimentPort.kt`
- `security/src/main/kotlin/org/pag/security/PlatformCredentialExperiment.kt`
- `settings.gradle.kts`
