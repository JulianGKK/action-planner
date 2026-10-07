# Prototyp B1.5 – Keystore-Verpackungsnachweis

## Status und Forschungsfrage

Stand: 2026-10-08. **Zentrale Hypothese für Fairphone 6 / Android 16 / API 36 im getesteten Umfang: BESTANDEN.** Ein persistenter synthetischer Storage-Key kann durch einen nicht exportierbaren Android-Keystore-RSA-Schlüssel geschützt werden und nach Neustart nur nach expliziter per-operation DEVICE_CREDENTIAL-Authentifizierung wieder entpackt werden. Die vom Nutzer berichteten positiven Bestands- und Force-stop-/Restart-Läufe bestätigen dies für dieses Gerät und diese Plattformkonfiguration, nicht als allgemeine Android-Garantie. Weitere Plattformen und Fehlerfälle bleiben offen.

Kann ein zufälliger synthetischer 256-Bit-Storage-Schlüssel ausschließlich geschützt persistiert und mit genau dem frisch authentifizierten Keystore-Cipher entpackt werden? B0-Session, Generation, Lifecycle und Deadlines bleiben zusätzlich erforderlich. **B1.5 ist keine endgültige Schlüsselhierarchie.** Kein Room, Tink, FileAsset, SQLCipher-Restart, Produktdatenmodell, Import/Delete, Kamera, Recovery oder neue Dependency.

Grundlagen: [architecture.md](architecture.md), [security.md](security.md), [threat-model.md](threat-model.md), [prototype-a.md](prototype-a.md), [prototype-b1.md](prototype-b1.md), PRODUCT.md und AGENTS.md. Historische Stage-A-Ergebnisse bleiben unverändert.

## Etablierte Kandidaten und gewählter Versuch

| Kandidat | Entscheidung für B1.5 | Grund / Grenze |
|---|---|---|
| Android-Keystore RSA-OAEP, DECRYPT_MODE + doFinal | Praktisch implementierter erster Versuch | Einzelner standardisierter OAEP-Ciphertext, keine selbst erfundene IV-/Tag-Struktur; private Operation nicht exportierbar und authentifizierungsgebunden |
| Android-Keystore AES-GCM | Zurückgestellt | Etabliert, aber Parameterpersistenz und standardisierte Kodierung müssten separat nachgewiesen werden |
| AES-Key-Wrap | Zurückgestellt | JCA-Name allein beweist keine Unterstützung durch Android Keystore; keine eigene Ersatzimplementierung |
| JCA wrap/unwrap | Kein zusätzlicher Versuch | Unterstützung der Modi ist providerspezifisch; hier ausschließlich dokumentierter OAEP-ENCRYPT/DECRYPT-Pfad |
| WrappedKeyEntry / PURPOSE_WRAP_KEY | Nicht implementiert | Sicherer Keystore-Import beantwortet nicht die Ausgabe eines Storage-Schlüssels für SQLCipher im normalen Prozess |
| Zeitlich autorisierter Keystore-Schlüssel | Kein B1.5-Fallback | Geräteentsperrung kann ein Autorisierungsfenster eröffnen; schwächere Bindung an eine neue konkrete App-Operation |

Verwendet werden Plattform/JCA-APIs: AndroidKeyStore, KeyPairGenerator, KeyGenParameterSpec, KeyFactory/KeyInfo, SecureRandom, Cipher, OAEPParameterSpec und Plattform-BiometricPrompt/CryptoObject. RSA-2048, OAEP-Hauptdigest SHA-256, MGF1 SHA-1 und leeres Standardlabel sind ausdrücklich festgelegte **Prototypparameter**, keine endgültigen Produktparameter. Der öffentliche Teil verschlüsselt 32 zufällige Bytes; nur der private DECRYPT-Schlüssel benötigt Authentifizierung. Keine KDF oder eigene Kryptografie.

Quellen: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [KeyGenParameterSpec mit RSA-OAEP-Beispiel](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec), [OAEP-Providerparameter](https://developer.android.com/privacy-and-security/cryptography), [Cipher](https://developer.android.com/reference/javax/crypto/Cipher), [setUserAuthenticationParameters](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setUserAuthenticationParameters(int,%20int)), [BiometricPrompt](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt), [KeyStoreException](https://developer.android.com/reference/android/security/KeyStoreException). Geprüft am 2026-10-08. Deprecated Aussagen zu biometrischer per-use-Authentifizierung sind kein Gegenbeweis zum historischen Stage-A-Befund; RSA muss trotzdem separat nachgewiesen werden. Kein aktueller AOSP-Implementierungsnachweis wird behauptet.

## Verbindlicher Bootstrap-Vertrag

Wir garantieren **nicht** den historischen Nachweis einer erstmaligen Installation. Ziel und Implementierungsregel sind stattdessen: Kein vorhandener, teilweise vorhandener, unbekannter oder widersprüchlicher PaG-Bestand wird durch automatische Schlüssel-Neuinitialisierung ersetzt.

Reservierter Namensraum der Test-App: `noBackupFilesDir/p15`; erwarteter Alias: `pag.prototype.b15.storage.v1`. Der vom Context gelieferte private Basisordner wird kanonisiert, weil Android einen legitimen `/data/user/0`-Pfadalias verwenden kann. Der reservierte Unterordner und seine Kinder dürfen keine symbolischen Links sein. Sein vollständiges Inventar entscheidet; nicht nur die erwarteten drei Dateien. DB, WAL/SHM/Journal, FileAssets, Staging, Arbeitszustände und unbekannte Einträge verhindern Initialisierung gleichermaßen. Diese späteren Datenarten werden hier nicht implementiert und müssen künftig innerhalb des reservierten Bestandsinventars bleiben.

| Zustand | Exakte Behandlung |
|---|---|
| EMPTY: Alias fehlt, Namensraum fehlt oder ist vollständig leer | Nur ausdrücklich gewählter Initialisierungsablauf erlaubt Erstellung; Unwrap erstellt nichts |
| COMPLETE: erwarteter Alias mit geprüfter Policy, exakt drei reguläre Dateien mit passenden Längen/Inventarwerten | Nur STORAGE_UNWRAP erlaubt Vorbereitung für frischen expliziten Auth-Flow. STORAGE_INITIALIZE liefert B15-BOOTSTRAP-NOT-EMPTY, ohne Änderung oder Vorbereitung. COMPLETE selbst bezeichnet strukturelle Vollständigkeit, keine bereits bestandene OAEP-Entschlüsselung |
| Alias ohne Wrapped Key; Wrapped Key ohne Alias; Marker ohne Alias | BLOCKED; keine Ersetzung oder Reparatur |
| DB, Nebenbestand, FileAsset, Staging, Arbeitsstatus oder unbekannter Eintrag | BLOCKED; auch wenn alle Schlüsselartefakte fehlen |
| Unerwartete Alias-Eigenschaften, anderes öffentliches Schlüsselmaterial, unlesbare Datei, Symbolic Link, ungültiger Marker oder Ciphertext-Länge | BLOCKED; Bestand bleibt erhalten |
| Formal vollständiger, aber nicht entschlüsselbarer Ciphertext | Kontrollierter Fehler beim Entpackversuch; keine Neuinitialisierung |

Explizite Erstellung: EMPTY prüfen → exklusiv Inventarmarker anlegen und synchronisieren → erneut Inventar und Abbruchstatus prüfen → RSA-Alias erzeugen und Policy prüfen → zufälligen Schlüssel öffentlich verschlüsseln → Ciphertext exklusiv schreiben und synchronisieren → öffentliche Identität exklusiv schreiben und synchronisieren → abschließendes Inventar prüfen. Keine Gesamttransaktion über Keystore/Dateisystem; keine Garantie gegen Stromverlust oder konkurrierende Manipulation durch ein kompromittiertes OS. Ein Teilfehler hinterlässt einen blockierten Bestand. Kein automatisches Cleanup, Reset, Überschreiben oder Wiederholen als neuer Bestand.

Der Marker ist ein technischer Teilausfallnachweis, kein Erstinstallationsbeweis. Sein Fehlen allein erlaubt niemals Erstellung. Unit-Tests injizieren Abbrüche nach Reservierung, Alias-Erzeugung und Ciphertext-Persistenz. Dies sind deterministische Zustandsprüfungen, keine realen Stromverlusttests.

## Persistente Artefakte

| Ort | Inhalt | Geheim? |
|---|---|---|
| Android Keystore | Nicht exportierbarer RSA-Privatschlüssel, öffentlicher Teil und KeyInfo-Policy | Privatschlüssel geheim; nicht als Datei exportiert |
| `p15/0` | Ein Byte als technische Initialisierungsreservierung | Nein |
| `p15/1` | 256 Byte standardisierter RSA-OAEP-Ciphertext des synthetischen Storage-Schlüssels | Geschützter Schlüsselzustand |
| `p15/2` | Standard-X.509/DER-Kodierung des öffentlichen RSA-Schlüssels zur Inventarzuordnung | Nein |

Kein eigener kryptografischer Containerstandard; Dateien 0/2 sind technische Inventarartefakte, keine authentifizierte neue Envelope-Konstruktion. Die OAEP-Parameter sind konstant im Testcode, nicht individuell serialisiert. OAEP beweist keine Absenderidentität und keinen Rollbackschutz. Ein privilegierter Angreifer könnte vollständige Inventarstände manipulieren; kein Schutzversprechen gegen kompromittiertes OS.

Instrumentation nutzt separate UUID-Namensräume und Aliase im privaten Speicher des Test-APK. Synthetische Fehlerfixtures werden nicht als Reparatur entfernt. Keine Klartext-Key-Datei, Strings/Hex/Base64, Secret-Preferences, DataStore, Saved State oder Schlüsselwerte in Reports/Logs. Bestehende Backup-/Transferausschlüsse bleiben erhalten; keine neue OEM-Backupgarantie.

## Authentifizierung und Session-Integration

Der private Schlüssel hat ausschließlich PURPOSE_DECRYPT, userAuthenticationRequired und `setUserAuthenticationParameters(0, AUTH_DEVICE_CREDENTIAL)`. Alias, nicht exportierbarer privater Schlüssel, Herkunft, Größe, Digest/Padding, Authenticator-Typ und per-operation-Eigenschaften werden geprüft. Keine Biometrie, StrongBox-Pflicht, Ersatzalias oder zeitbasierte B1.5-Konfiguration.

Die Vorbereitung erzeugt einen DECRYPT-Cipher mit expliziten OAEP-Parametern. Genau dessen Objekt geht in den ausdrücklich gestarteten DEVICE_CREDENTIAL-Dialog. Ergebnisart und Cipher-Identität müssen passen; ein anderer Cipher wird abgelehnt. Der Callback entpackt noch nichts. Er übergibt eine einmalig verwendbare synthetische Operation an den vorhandenen SessionCoordinator. Nach synthetischer Maintenance und READY löst allein „Testzugriff“ die geschützte Entpackung aus.

READY bedeutet in diesem isolierten Versuch deshalb noch **nicht**, dass eine produktive Datenquelle geöffnet oder ein Storage-Key erfolgreich entpackt wäre. Erfolg wird separat als `B15-UNWRAP-SUCCEEDED` angezeigt; nur ein öffentlicher synthetischer Marker gelangt in die UI. Kein Storage-Key wird für produktive Verwendung freigegeben.

StorageAccessContract prüft Generation und Zeit vor der nativen Arbeit, danach und vor Ergebnisveröffentlichung. Lock/Deadline/Lifecycle entziehen den Kontext; alte Callbacks, Handles und Generationen dürfen keine neue Freigabe erzeugen. Native Arbeit liegt außerhalb des Coordinator-Monitors und kann physisch noch enden. Ein vorhandenes Keystore-Autorisierungsergebnis erzeugt keine neue Sitzung. Initialisierung/Dateiarbeit liegen auf einem Worker; Abbruch wird zwischen den Schritten geprüft. Keine zweite Session-State-Machine.

Entpackte Bytes bleiben nur lokal für den Test, werden nicht in ViewModel/Flow gespeichert und bestmöglich überschrieben. Provider-/JVM-/native Kopien sind nicht zuverlässig vollständig löschbar. Der momentan implementierte Erfolg prüft OAEP-Entschlüsselung und 32-Byte-Länge; eine unabhängige Schlüsselwert-Identitätsprüfung oder SQLCipher-Restartintegration ist noch nicht implementiert. Der beobachtete Restart-Erfolg belegt die erneute erfolgreiche OAEP-Entpackung des unveränderten persistenten Bestands, ohne den Schlüsselwert auszugeben.

## Tatsächliche Prüfungen und Befunde

| Prüfung | Ergebnis |
|---|---|
| Bestehende B0-Unit-Tests | 39 bestanden |
| Neue Bootstrap-Unit-Tests | 15 bestanden: leer, Teilbestände, DB/Nebenbestände/Assets/Staging/Arbeitsstatus, unbekannte Dateien, beschädigter Bestand, öffentliche Identität, Crashpunkte, separate Namensräume und Symlinks |
| Neue Session-/Wrapping-Vertragstests | 7 bestanden: guarded Zugriff, Lock/Deadline während Operation, Generation/Callback, Lifecycle, Parallelflow und verspätete Maintenance; synthetische Fakes, kein Plattformauthentifizierungsbeweis |
| Diagnose-Unit-Tests | 9 bestanden: getrennte Initialize-/Unwrap-Pfade, Nicht-Ersetzung, feste Stufen-IDs, Policy/Invalidierung, BLOCKED-Weitergabe und Dialogabbruch mit DENIED |
| Fairphone 6 / Android 16 / API 36 – frühere Instrumentation | 6 Instrumentationtests bestanden, 0 Fehler, 0 Skips; direkte AndroidJUnitRunner-Ausführung vor der Diagnoseerweiterung |
| Aktuelle Instrumentation-Suite | 7 Tests kompilierbar; zusätzlicher Nicht-Ersetzungs-/Bestandsvorbereitungstest noch nicht auf Gerät ausgeführt |
| RSA-KeyInfo auf diesem Gerät | TEE; authHardware=true; RSA2048/OAEP; DEVICE_CREDENTIAL; PER_OPERATION |
| API-37-Emulator | Nicht ausgeführt; kein solcher Emulator verbunden |
| API-30-Emulator | Nicht ausgeführt; kein solcher Emulator verbunden |
| Positiver Bestandslauf auf Fairphone | BESTANDEN, vom Nutzer tatsächlich beobachtet: expliziter Dialog, READY, Testzugriff, B15-UNWRAP-SUCCEEDED |
| Force-stop / Neustart / explizite Authentifizierung / Entpackung auf Fairphone | BESTANDEN, vom Nutzer tatsächlich beobachtet: Neustart LOCKED, neuer Dialog, Testzugriff erneut B15-UNWRAP-SUCCEEDED; unabhängige Schlüsselidentitätsprüfung bleibt offen |
| Build, Lint, Architektur, Manifest und Instrumentation-Kompilation | Erfolgreich |
| Dependency-Graph | Erfolgreich geprüft; keine neue Dependency, keine Gradle-Änderung |

Die sechs Gerätetests prüfen: explizite Initialisierung/Policy, verweigerte Operation ohne frischen Prompt trotz entsperrtem Gerät, fehlenden Wrapped Key ohne Ersatz, Truncation ohne Ersatz, Ablehnung eines anderen Cipher-Kontexts und JCA-Providerlookup. Das TEE-Ergebnis ist ein spezifischer RSA-Gerätebefund, keine Garantie für andere Geräte oder Verarbeitung des gesamten App-Klartexts in der TEE; kein StrongBox-Nachweis.

Beobachtete und korrigierte technische Fehler vor dem erfolgreichen Lauf:

- Die Inventarprüfung lehnte zunächst auch den legitimen Android-Basispfadalias ab. Nur die vertrauenswürdige Context-Basis wird jetzt kanonisiert; reservierte Symlinks bleiben verboten.
- Explizites `Cipher.getInstance(..., "AndroidKeyStore")` ergab auf dem Fairphone NoSuchAlgorithmException. Der dokumentierte JCA-Pfad ohne fixierten Cipher-Provider bereitet denselben nicht exportierbaren Keystore-Schlüssel mit denselben Parametern vor. Kein Algorithmus-/Autorisierungsfallback.
- Die nicht autorisierte native Operation wurde zunächst als allgemeines FAILED klassifiziert. Die implementierte begrenzte Ursachenprüfung erkennt UserNotAuthenticatedException sowie ab API 33 die öffentliche KeyStoreException-Authentifizierungsinformation. Die NOT_AUTHORIZED-Assertion blieb unverändert.

Der Gradle-connected-Task konnte offline wegen eines fehlenden lokalen UTP-Artefakts (`gradle-work-action:32.4.1`) nicht starten. Stattdessen wurde das gebaute Test-APK installiert und die Suite direkt mit `am instrument` ausgeführt. Die erfolgreichen Runtime-Zahlen stammen aus dessen Ergebnis, nicht aus einem vorgetäuschten erfolgreichen Gradle-connected-Lauf. Lokale Evidenz: `.prototype-cache/b15-instrumentation-direct.log`, `b15-checks.log`, `b15-dependencies.log`; weiterhin ignoriert.

Der Abschlusslauf war erfolgreich: 70 Unit-Tests im aktuellen Ergebnisbestand, keine Fehler; der erneute Gradle-Aufruf verwendete die bereits aktuellen Unit-Testresultate (UP-TO-DATE), statt einen neuen Runtime-Lauf zu behaupten. Build/Lint/Architektur/Manifest und Instrumentation-Kompilation wurden erneut angefordert und erfolgreich geprüft.

Ausgeführter finaler Buildlauf: `test`, `:app:assembleDebug`, `:app:lintDebug :data:lintDebug :security:lintDebug`, `verifyArchitecture :app:verifyPrototypeManifest`, `:data:assembleDebugAndroidTest`. Manifest unverändert: bestehende USE_BIOMETRIC-Plattformvoraussetzung und app-eigene Signature-Permission; nur Launcher exportiert. Keine INTERNET/CAMERA/POST_NOTIFICATIONS/Storage-Permission, neuen Provider/Receiver/Services. FLAG_SECURE und Recent-App-Schutz erhalten.

## Beobachtete manuelle Fairphone-Läufe

Quelle: vom Nutzer berichtete tatsächliche Tests auf Fairphone 6 / Android 16 / API 36 mit der aktualisierten Diagnose-App; keine automatisiert erzeugte Credential-Eingabe und kein neu erfundener Testlauf.

| Lauf | Beobachteter Ablauf | Ergebnis |
|---|---|---|
| Positiver Lauf 1 | Vorhandener COMPLETE-Bestand → „B1.5 · bestehenden Schlüssel authentifizieren“ → DEVICE_CREDENTIAL-Systemdialog erschien → Geräte-PIN erfolgreich → READY → „Testzugriff“ | B15-UNWRAP-SUCCEEDED |
| Positiver Restart-Lauf | „Stopp erzwingen“ → App neu gestartet → LOCKED → „B1.5 · bestehenden Schlüssel authentifizieren“ → erneuter DEVICE_CREDENTIAL-Systemdialog → Geräte-PIN erfolgreich → „Testzugriff“ | Erneut B15-UNWRAP-SUCCEEDED |

Kein automatisches Entpacken beim Neustart. Erneute explizite Authentifizierung war erforderlich. Kein zeitbasiertes Keystore-Fenster als Fallback. Derselbe persistente COMPLETE-Bestand wurde weiterverwendet: keine Neuinitialisierung, kein Ersatzalias, kein Ersatz-Storage-Key. Das ist Geräte-/Konfigurationsevidenz, keine Garantie gegen andere Plattformverhalten oder ein kompromittiertes OS.

Der vorausgegangene interaktive AUTH-INVALID_KEY-Befund wurde nicht durch Löschen oder Reparatur aufgelöst. Lesende Diagnose bestätigte COMPLETE und die erwartete Alias-Policy. Der damalige Sammel-Catch hatte die ursprüngliche Exception verworfen; ihre genaue Ursache ist retrospektiv nicht nachgewiesen. Die anschließende Diagnoseerweiterung trennt Bestandsprüfung, Initialisierung, Vorbereitung und Prompt. Sie führt keine automatische Wiederherstellung ein. Vor und nach dem App-Update stimmten die intern verglichenen Dateidigests der drei Artefakte überein; keine Digests oder Schlüsselwerte werden dokumentiert.

## Feste technische Diagnose-IDs

Implementiert: B15-BOOTSTRAP-INVENTORY, B15-BOOTSTRAP-NOT-EMPTY, B15-KEY-POLICY, B15-KEY-INVALIDATED, B15-WRAPPED-READ, B15-CIPHER-LOOKUP, B15-CIPHER-INIT, B15-CRYPTOOBJECT, B15-PROMPT-START, B15-PROMPT-REJECTED, B15-UNWRAP-AUTH-REQUIRED und B15-UNWRAP-FAILED. Zusätzlich feste IDs für Bootstrap-I/O und ungültigen Sessionkontext. Keine Plattform-Exceptiontexte, Pfade oder Stacktraces in UI/Logs. Fehler bleiben blockierend; Dialogabbruch bleibt LOCKED mit DENIED und Diagnose-ID. Alte Generationen werden weiterhin verworfen.

## Offene Nachweise und Sicherheitsgrenzen

- API-30- und API-37-Emulatorläufe einschließlich positiver Credential-/Restart-Abläufe. Bei der Abschlussprüfung war per adb nur das Fairphone mit API 36 verbunden; kein Emulator gestartet oder neu konfiguriert.
- Authentifizierte gleichlange Bit-Manipulation und Fremdschlüsseltest.
- Echte Process-Death-Ausfallpunkte während Bootstrap/Persistenz. Der bestandene Force-stop-/Restartlauf ist kein Nachweis für diese injizierten Ausfallpunkte.
- Credential-Wechsel/-Entfernung und Key-Invalidierung, ausschließlich auf dediziertem Testgerät.
- Power-Loss, persistente Haltbarkeit und OEM-Backup-/Transferverhalten.
- SQLCipher-Restartintegration mit diesem Key, unabhängige Schlüsselidentitätsprüfung und finale Produkt-Schlüsselhierarchie.
- Vollständige physische RAM-/Flash-Löschung und Schutz gegen kompromittiertes OS/Kernel: keine verlässliche allgemeine Garantie.
- Auth-Abbruch/Home/Display-Lock während des echten RSA-Flows sowie weitere Alias-/Policy-Fehler als Plattformtests.

Keine dieser offenen Eigenschaften wird durch den positiven Bestands-/Restartlauf oder die Unit-Tests als bestanden behandelt. Der Prototyp enthält keinen automatischen Reset für einen teilinitialisierten Bestand.

Noch keine Behauptung zu vollständiger Klartextfreiheit in RAM, Flash-Löschung, Stromverlustfestigkeit, OEM-Transferverhalten oder Schutz gegen kompromittiertes OS/Kernel. Keine endgültige Schlüsselhierarchie, Sessiondauer, Hardware-/Android-Anforderung oder SQLCipher-direkt-vs-Room-Entscheidung.

## Ergebnis und Stop-Regel

Bootstrap-Architekturentscheidung umgesetzt; historische Erstinstallationsfrage ist kein Blocker mehr. **B1.5 als zentraler Wrapping-/Restartnachweis auf dem Fairphone im getesteten Umfang: BESTANDEN.** Negative Instrumentationbefunde und die berichteten positiven Bestands-/Restartläufe stützen per-operation DEVICE_CREDENTIAL mit RSA-OAEP auf diesem Gerät. Der Abschluss betrifft keine endgültige Produkt-Schlüsselhierarchie und erklärt die offenen Plattform-/Fehlerfälle nicht für bestanden.

Kein Zeitfenster wurde benötigt oder als Fallback eingeführt. Scheitert die eigentliche per-operation-Entpackung, stoppen und Befund berichten. In diesem Abschlussauftrag wurde keine SQLCipher-Restart-/B2-/B3-Integration implementiert. Weiterführende Arbeiten benötigen ihren eigenen abgegrenzten Auftrag. Ergebnisfelder für weitere Läufe: Datum, API, Gerätetyp, Quell/APK-Stand, Test-ID, Ergebnis, feste technische Fehlerklasse und KeyInfo-Zusammenfassung; niemals Schlüsselwerte oder Credentials.
