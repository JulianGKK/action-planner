# Prototyp B1.6 – Keystore → SQLCipher Restart-Integration

## Status, Forschungsfrage und Grenzen

B1.6 ist **keine endgültige Produkt-Key-Architektur**. Ausschließlich synthetische
Daten, bestehende Plattform-/SQLCipher-APIs und die vier vorhandenen Module werden
verwendet. B1 und B1.5 bleiben historische Nachweise; deren Dokumentation und der
Fairphone-p15-Bestand werden nicht verändert.

Hypothese: Der persistente, in B1.5 mittels nicht exportierbarem RSA-Keystore-Key
geschützte synthetische Storage-Key kann nach frischer expliziter,
operationsgebundener DEVICE_CREDENTIAL-Authentifizierung eine bestehende
SQLCipher-Fixture öffnen; nach Force-stop ist derselbe Ablauf erneut erforderlich.

**Zentrale Restart-Hypothese: BESTANDEN im getesteten Umfang auf Fairphone 6 /
Android 16 / API 36.** Der Nutzer hat nach Force-stop / Neustart und erneuter
expliziter Authentifizierung `DATABASE_REOPEN`, READY und
`B16-RESTART-SUCCEEDED` beobachtet. Das stützt den End-to-End-Pfad mit vorhandenem
persistent geschütztem Storage-Key und derselben bestehenden SQLCipher-Fixture.

Davon getrennt bleibt die ursprüngliche manuelle EMPTY-Erstbootstrap-Historie
wegen der früheren groben Diagnose nicht vollständig rekonstruierbar. Der
vorhandene p16-Bestand wurde lesend als strukturell COMPLETE bestätigt.
Kein allgemeiner Android-/OEM-Nachweis und keine endgültige Produkt-Key-Architektur.

Keine Room-/Tink-/FileAsset-/Kamera-/Import-/Delete-/Recovery-Funktion. Keine neue
Dependency, kein Zeitfenster-Fallback, keine neue KDF oder Kryptografie.

## Architektur und Modulgrenzen

`:app` verdrahtet einen eigenen `RsaWrappingFixture`, den bestehenden
`WrappingCredentialExperiment` und den Data-Adapter `B16AuthenticationExperiment`.
Der zentrale `SessionCoordinator` bleibt alleinige Session-Autorität. UI und
ViewModel erhalten weiterhin ausschließlich Domain-/UI-Verträge.

Der Security-Adapter verwendet denselben per-operation RSA-OAEP-Cipher und exakt
dessen CryptoObject im DEVICE_CREDENTIAL-Dialog. Die Primitive, Key-Policy und
Parameter aus B1.5 bleiben unverändert. Der einzige zusätzliche Security-Vertrag
ist ein synchroner, nicht speichernder Verbraucher entpackter Schlüsselbytes.

Nach dem Auth-Callback entsteht ein widerrufbarer MAINTENANCE-Kontext. Ein Worker
führt Unwrap → DB-Open/Create → Fixture-Prüfung → Close aus. Erst nach erneuter
Generations-/Zeitprüfung darf der Coordinator das technische Ergebnis anzeigen.
Native Arbeit läuft außerhalb seines Monitors. Es gibt keinen zweiten Unlock-State.

Die bestehende `SqlCipherEngine` erhält einen ausdrücklich angegebenen Zugriffsscope.
Standard bleibt READY; ein Maintenance-Permit erlaubt weiterhin keinen regulären
B1-Zugriff. B1.6 gibt ausdrücklich MAINTENANCE an. Jeder Handle bleibt an seinen
ursprünglichen Kontext und Scope gebunden und wird innerhalb des Aufrufs geschlossen.

## Persistente Artefakte und Bootstrap

Privater, separater Namensraum: `noBackupFilesDir/p16`.
Keystore-Alias: `pag.prototype.b16.storage.v1`. B1.5 nutzt weiterhin p15 und seinen
anderen Alias. Keine Wiederverwendung oder Reparatur des p15-Bestands.

| Artefakt | Inhalt |
|---|---|
| `0` | Ein Byte technische Initialisierungsabsicht, exklusiv erstellt |
| `k/0` | Bestehender B1.5-Inventarmarker |
| `k/1` | RSA-OAEP-geschützter synthetischer 256-bit Storage-Key, 256 Byte |
| `k/2` | Standard-X.509-Encoding des öffentlichen RSA-Schlüssels |
| `d` | SQLCipher-Testdatenbank, B1-Schema-Version 1 |
| `d-wal`, `d-shm`, `d-journal` | Falls vorhanden: ausdrücklich inventarisierte DB-Nebenbestände |
| `1` | Abschlussartefakt: öffentliche synthetische Fixture-UUID, 36 ASCII-Byte |

Die UUID ist ausschließlich nicht-sensitive technische Testmetadaten. Dieselbe
UUID steht in der verschlüsselten Testzeile, zusammen mit
`PAG_B16_SYNTHETIC_DATABASE_ONLY` und Status 16. Sie wird nicht in UI/Logs ausgegeben
und ist kein Produkt-Dateiformat oder kryptografischer Container. Der Storage-Key
steht niemals ungeschützt in einem dieser Artefakte.

| Zustand | Bedingung / Konsequenz |
|---|---|
| EMPTY | Vollständig leerer p16-Namensraum und Security-Inventar EMPTY, insbesondere fehlender erwarteter Alias. Nur ausdrücklich ausgelöste Initialisierung erlaubt. |
| COMPLETE | Bekannte reguläre Dateien, gültige Marker/UUID, vollständiges Security-Inventar einschließlich Alias-Policy, nichtleere DB. Vor Auth nur **strukturelle** Vollständigkeit. Entschlüsselung, Schema und Integrität werden anschließend geschützt geprüft. |
| BLOCKED | Jede andere Kombination, unbekannte Datei, Symlink, Teilbestand, fehlendes Artefakt oder widersprüchliches Inventar. Keine Mutation als Reparatur. |

Eine beschädigte DB kann äußerlich COMPLETE aussehen; ihr geschütztes Öffnen
blockiert dann. Ebenso beweist korrekt lange Ciphertext-Datei keine erfolgreiche
Entschlüsselung. Es gibt keinen historischen Erstinstallationsnachweis.

### Initialisierung und Ausfallpunkte

1. EMPTY vollständig prüfen und p16 exklusiv durch `0` reservieren.
2. Unter `k` die unveränderte B1.5-Erzeugung ausführen: Marker, RSA-Alias,
   zufälliger Storage-Key, OAEP-Verschlüsselung, geschützte Artefakte, Policyprüfung.
3. Privaten DECRYPT-Cipher vorbereiten und expliziten Systemdialog starten.
4. Nach Auth-Erfolg, gültiger Generation und MAINTENANCE-Kontext entpacken.
5. Nur im laufenden, ausdrücklich reservierten Versuch eine neue DB exklusiv
   erstellen, synthetische Zeile transaktional committen und wieder lesen.
6. Handle schließen, Gültigkeit erneut prüfen, Abschlussartefakt exklusiv mit
   `fd.sync()` schreiben und das kombinierte Inventar prüfen.
7. Erfolg `B16-DB-CREATE-SUCCEEDED`, anschließend LOCKED mit invalidierter Generation.

Dies ist keine gemeinsame atomare Transaktion über Keystore und Dateisystem.
Ein nach Prozessende unvollständiger Versuch wird **nicht automatisch fortgesetzt**:
BLOCKED und unverändert erhalten ist die bewusst minimale Wiederanlaufentscheidung.

| Unterbrechung | Nach Neustart |
|---|---|
| Absicht reserviert, Alias noch nicht vorhanden | BLOCKED |
| Alias erzeugt, Wrapped-Key noch nicht geschrieben | BLOCKED |
| Wrapped-Key geschrieben, DB noch nicht vorhanden | BLOCKED |
| DB erstellt, Fixture noch nicht committed | BLOCKED |
| Fixture committed, Abschlussartefakt fehlt | BLOCKED |
| Abschlussartefakt vorhanden, anderes Artefakt fehlt | BLOCKED |
| Bestand vollständig | LOCKED; erst explizite Auth erlaubt geschützte Validierung |

Kein Ersatzalias, Ersatz-Key, Überschreiben, Löschen, automatische Neuerstellung,
Migration oder Reparatur. Nach erteilter Commit-Zulassung kann bereits laufende
native Arbeit trotz unmittelbar folgendem Lock noch physisch enden. Das Ergebnis
darf dann nicht veröffentlicht werden. `fd.sync()` ist kein Power-loss-Nachweis
für Verzeichniseinträge oder das Gesamtsystem.

## Session-Vertrag und Schlüssel-Lebensdauer

B0 bleibt vollständig maßgeblich: Vordergrund-/Gerätesperrzustand, Generation,
absolute monotone Sleep-einschließende Deadline; Inaktivität zusätzlich in READY.
Maintenance verbraucht absolute Sessionzeit und zeigt keine DB-Fachinhalte.
Keine neue Deadline beim Wechsel zu READY, kein automatischer Auth-Prompt.

Prüfungen erfolgen vor Unwrap, vor DB-Open, an Engine-/Query-/Write-Grenzen,
vor Commit-Zulassung und vor Veröffentlichung. Alte Callbacks/Handles dürfen
nach Lock, Ablauf oder Reauth keine Ergebnisse in eine andere Session übertragen.
Ein vorhandener Key oder Cipher ist niemals Zugriffsrecht.

Die entpackte ByteArray wird synchron direkt an die bestehende Engine übergeben.
Sie liegt nicht in Strings, Preferences, Saved State, ViewModels, Flows oder einer
Singleton-Registry. Die DB wird vor Rückkehr aus dem Verbraucher geschlossen;
`finally` überschreibt die kontrollierte ByteArray bestmöglich. SQLCipher-/JVM-/
Provider-/native Kopien können nicht zuverlässig vollständig gelöscht garantiert
werden. Keystore-Backing bedeutet keine Verarbeitung aller Klartexte in der TEE.

## Technische IDs und Fehler

Feste IDs, keine Exceptiontexte, Pfade, Schlüsselwerte oder Stacktraces in UI/Logs:
`B16-BOOTSTRAP-EMPTY`, `B16-BOOTSTRAP-COMPLETE`, `B16-BOOTSTRAP-BLOCKED`,
`B16-BOOTSTRAP-NOT-EMPTY`, `B16-BOOTSTRAP-INVENTORY`,
`B16-UNWRAP-SUCCEEDED`, `B16-DB-CREATED-PENDING`, `B16-DB-CREATE-SUCCEEDED`,
`B16-DB-OPEN-SUCCEEDED`, `B16-DB-INTEGRITY`, `B16-DB-SCHEMA`,
`B16-SESSION-EXPIRED`, `B16-RESTART-SUCCEEDED` und feste Auth-/Unwrap-Diagnosen.

`B16-DB-WRONG-KEY-OR-UNREADABLE` ist absichtlich mehrdeutig: Ein Enginefehler
beweist weder einen falschen Schlüssel noch Datenkorruption. Die Tests können
einen bewusst injizierten falschen Schlüssel kennen; die App kann diese Ursache
nicht generell bestimmen. `B16-STALE-GENERATION` ist als technischer Code reserviert;
alte Ergebnisse werden verworfen, ohne ihn in einer neueren Session zu veröffentlichen.
Für zentrale Deadline-/Lifecycle-Invalidierung gelten weiterhin die B0-/A-IDs.

COMPLETE bei erneutem Initialisierungsversuch wird vor jeder Mutation als
`B16-BOOTSTRAP-NOT-EMPTY` abgewiesen. Der Auth-/Delegate-Pfad wird nicht gestartet.
Teil-/unklare Bestände behalten `B16-BOOTSTRAP-INVENTORY`. Beide enden weiterhin
BLOCKED; keine Reparatur oder Ersatzinitialisierung. Regressionstests prüfen die
exakte UI-ID sowie unveränderte Datei-/Verzeichnisstruktur, Datei-Bytes und
Änderungszeiten in ausschließlich lokalen synthetischen Testfixtures.

## Automatische Prüfungen und Befunde

Stand dieses Auftrags:

| Prüfung | Ergebnis |
|---|---|
| `./gradlew test` | BESTANDEN: 105 Unit-Tests, 0 Fehler; davon 35 B1.6-Tests |
| `:app:assembleDebug` | BESTANDEN |
| `:app:lintDebug :data:lintDebug :security:lintDebug` | BESTANDEN |
| `verifyArchitecture :app:verifyPrototypeManifest` | BESTANDEN |
| `:data:assembleDebugAndroidTest` | BESTANDEN |
| B1.6-Instrumentation auf Fairphone 6/API 36 | BESTANDEN: 16 Tests im ersten Lauf |
| Gemeinsamer B1/B1.5/B1.6-Realgerätelauf vor dem Diagnose-Fix | BESTANDEN: 39 Tests (16 B1, 7 B1.5, 16 B1.6), 0 Fehler |
| API-30-Emulator | OFFEN: nicht verbunden, nicht ausgeführt |
| API-37-Emulator | OFFEN: nicht verbunden, nicht ausgeführt |

Neue Unit-Tests prüfen Inventar-/Crash-Kombinationen, keine Ersatzinitialisierung,
Dispatch von Initialisierung vs. bestehendem Unwrap, Gate vor READY, Initialisierung
zurück nach LOCKED, verspätete Auth-/Gate-Callbacks, Generation, Abbruch,
Promptfehler, Vordergrundverlust, Gerätesperr-Umgebungsprüfung, Prozess-Recreation
und absolute Deadline über Maintenance hinweg.

Die 16 neuen Instrumentationtests verwenden eine injizierte synchrone Key-Consumer-
Testimplementierung und einen nur flüchtigen Zufallsschlüssel. Sie prüfen die echte
SQLCipher-Engine, Erstellen/Schließen/Wiederöffnen derselben Fixture in neuer
Generation, falschen Schlüssel, beschädigten Bestand, unbekanntes Schema,
Deadline vor/nach Unwrap, vor/während Open, vor Commit und nach Query,
Generationswechsel, Vordergrundverlust, alte Handles sowie Fault-Injection nach
DB-Erzeugung, Fixture-Commit und vor Abschluss. Dies ist **kein authentifizierter
Keystore-/Force-stop-End-to-End-Nachweis**. Der Schlüssel dieser Tests wird nicht
als Persistenz-/Restartlösung verwendet.

DB-/Nebenbestände werden nach dem festen synthetischen Marker gescannt. Der
negative Scan ist ausschließlich ein begrenztes Testsignal, kein vollständiger
Nachweis der Klartextfreiheit. Die öffentliche technische UUID ist davon ausgenommen.
Testfixtures bleiben erhalten, ohne automatische Bereinigung unbekannter Dateien.
Lokale Testprotokolle liegen unter der ignorierten `.prototype-cache/`.

## Manuelle Realgeräteprüfung und Restart-Ablauf

Die Debug-App wurde als Update auf dem verbundenen Fairphone 6/Android 16/API 36
installiert. Keine Deinstallation, kein Löschen oder automatische UI-Initialisierung.
Die Prüfsummen der drei bestehenden p15-Dateien waren vor/nach dem Update identisch.

### Tatsächlich beobachteter manueller Restart

Vom Nutzer berichtet auf Fairphone 6 / Android 16 / API 36:
Nach Force-stop / Neustart und expliziter Authentifizierung zeigte die App:

- SESSION: READY
- Variante: DATABASE_REOPEN
- Keystore-Autorisierung: SUCCEEDED
- Konkrete Schlüsseloperation: SUCCEEDED
- Test-ID: B16-RESTART-SUCCEEDED
- Schlüsseleigenschaften: TEE, authHardware=true, RSA2048/OAEP,
  DEVICE_CREDENTIAL, PER_OPERATION, KEYSTORE_ALIAS_PRESENT, WRAPPED_KEY_PRESENT

Der bestehende persistente Schlüsselbestand und die bestehende DB wurden über den
Reopen-Pfad verwendet; dieser besitzt keinen Initialisierungs-/Reparaturpfad.
Kein zeitbasiertes Autorisierungsfenster als Fallback. Der Erfolg beruht auf neuer
expliziter Authentifizierung, nicht auf automatischer Öffnung beim Prozessstart.

TEE/authHardware=true ist ein positiver Gerätebefund für den B1.6-Alias, kein
StrongBox-Nachweis, keine Aussage über alle Geräte und keine Verarbeitung der
gesamten App oder ihrer Klartexte innerhalb der TEE.

| Ergebnisfeld | Befund |
|---|---|
| Restart / DATABASE_REOPEN nach Force-stop und expliziter Authentifizierung | BESTANDEN im berichteten Geräte-/Plattformumfang |
| Ursprünglicher manueller EMPTY-Erstbootstrap / Dialog / Create | Nicht vollständig rekonstruierbar; nicht rückwirkend als bestanden bewertet |
| Vorhandener p16-Bestand | Lesend strukturell COMPLETE bestätigt; später erfolgreicher authentifizierter Reopen |
| B1.6-Alias-Hardware-Backing | TEE / authHardware=true beobachtet; kein StrongBox-Nachweis |
| API 30 / API 37 positiver Auth-Restart | OFFEN |

Der historische `B16-BOOTSTRAP-INVENTORY`-Befund allein identifiziert keinen
konkreten Auslöser: der frühere Sammel-Catch verwarf die genaue Ursache. Am später
lesend geprüften COMPLETE-Bestand weist `reserveNew()` erneute Initialisierung vor
Mutation ab. Der Diagnose-Fix präzisiert nur diese Ablehnung und ändert keine
Sicherheitszulassung. Keine neuen manuellen Läufe, Installation, Initialisierung
oder Geräteänderung wurden für diesen Dokumentations-/Diagnose-Fix gestartet.

## Manifest, Dependencies, Backup und Review

Keine Gradle-/Dependency-/Manifeständerung. Bestehende SQLCipher-Version 4.19.1
und AndroidX-SQLite-Abhängigkeit bleiben unverändert. Kein Room/Tink/libsodium/SEE.
Das gemergte App-Manifest enthält USE_BIOMETRIC (Plattform-API-Voraussetzung;
zulässiger Authentifizierungstyp bleibt ausschließlich DEVICE_CREDENTIAL) und die
bestehende app-eigene Signature-Permission. Keine INTERNET-, CAMERA-,
POST_NOTIFICATIONS- oder Storage-Permission. Nur die Launcher-Activity ist exportiert.
FLAG_SECURE und Recent-App-Schutz bleiben erhalten.

p16 liegt unter noBackupFilesDir; `allowBackup=false`, vollständige Cloud-/
Device-Transfer-Ausschlüsse und die bestehenden XML-Regeln bleiben erhalten.
OEM-/forensisches Backup-/Transferverhalten wird dadurch nicht allgemein bewiesen.

Security-Diff-Prüfung: keine neuen Schlüsselwerte in Disk/Logs/UI, keine produktiven
Daten, automatische Reparatur oder schwächere Auth-Konfiguration. Keine I/O-Arbeit
im langen Coordinator-Monitor. Vorhandene historische Stage-A-Preferences halten
nur dessen technische Inventarmetadaten, keine B1.6-Schlüssel oder operativen Daten.

## Offene Grenzen

API-30-/37-Emulatorbefunde, vollständige manuelle EMPTY-Erstbootstrap-Rekonstruktion,
echte Process-Death-Ausfallpunkte während Initialisierung/I/O sowie Power-loss,
Credential-Wechsel/-Entfernung, Key-Invalidierung, echte Speicherknappheit,
physische RAM-/Flash-Reste, OEM Backup/Transfer sowie endgültige Produkt-Key-
Hierarchie bleiben offen. Schutz gegen ein vollständig kompromittiertes laufendes
OS/Kernel liegt ausdrücklich außerhalb der Garantie. Keine endgültigen
Sessiondauern oder SQLCipher-direkt-vs.-Room-Entscheidung.
