# Galaxy M52 – unveränderte Baseline B1–B4

## Status und Geräteprofil

**BASELINE BESTANDEN im getesteten Galaxy-/API33-Umfang.**
Automatische B1–B4-Regression sowie die manuellen B1.5-/B1.6-/B3-
DEVICE_CREDENTIAL-/Restart-Pfade sind abgeschlossen und bestanden.
Stand: 8. Oktober 2026. Getesteter Commit: `3dce6e4`.
Der Git-Ausgangszustand war sauber; Code, Tests, Dependencies und
Build-Konfiguration wurden nicht verändert.

| Eigenschaft | Lesend beobachtet |
| --- | --- |
| Hersteller | samsung |
| Modell | SM-M526BR (Galaxy M52) |
| Android | 13 |
| API | 33 |
| ABI | arm64-v8a |
| Build-Fingerprint | samsung/m52xqeea/m52xq:13/TP1A.220624.014/M526BRXXS7CYE1:user/release-keys |

ADB zeigte genau ein verbundenes Gerät mit diesem Profil. Keine Seriennummer
oder ADB-Gerätekennung wird in diesem Dokument gespeichert. Die lesende
Window-/Keyguard-Policy meldete `secure=true`; die automatische RSA-Suite mit
`KeyguardManager.isDeviceSecure` bestand ohne Credential-bedingten Skip.
Kein Sperrcode wurde konfiguriert, geändert oder entfernt.

Diese Evidenz ergänzt die getrennten Fairphone-6-/API36-Nachweise in
[prototype-b4.md](prototype-b4.md) und den vorangegangenen Prototypdokumenten.
Sie überschreibt diese nicht und begründet keine allgemeine Android-/OEM-Garantie.

## Ausführung und vollständige automatische Regression

Test-APK und Haupt-App waren anfangs nicht installiert. Die unveränderten
APKs wurden jeweils erstmals mit `adb install` ohne Ersetzungsoption installiert.
Keine Deinstallation, kein Reinstall und kein App-Daten-Löschen.
Die Haupt-App wurde erst nach erfolgreicher vollständiger Automatik-Suite
für die manuellen Tests installiert und ohne automatischen Auth-Flow geöffnet.

Die vollständige Suite wurde direkt mit dem vorhandenen AndroidJUnitRunner
ausgeführt, ohne Klassen- oder Methodenfilter:

```text
adb shell am instrument -w -r org.pag.data.test/androidx.test.runner.AndroidJUnitRunner
```

Dadurch erfolgte kein Gradle-connected-Installations-/Deinstallationszyklus.
Runner-Meldung: **OK (223 tests)**. Alle 223 individuellen Testabschlüsse
meldeten Erfolg; kein Failure, Error, Ignore oder Assumption-Skip.
Zusätzliche Statusmeldungen mit Code 2 sind technische Diagnosen, keine Tests.
Der abschließende `INSTRUMENTATION_CODE: -1` gehört hier zum erfolgreichen
Runner-Abschluss, nicht zu einem individuellen Testfehler.

| Stufe / Testklasse | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| B1 – SqlCipherEngineTest | 16 | 0 | 0 | 0 |
| B1.5 – RsaWrappingTest | 7 | 0 | 0 | 0 |
| B1.6 – B16IntegrationTest | 16 | 0 | 0 | 0 |
| B2 – B2ComparisonTest (28 Direct, 28 Room) | 56 | 0 | 0 | 0 |
| B3 – B3StreamingTest | 9 | 0 | 0 | 0 |
| B4 – B4MatrixTest | 88 | 0 | 0 | 0 |
| B4 – B4SafetyTest | 20 | 0 | 0 | 0 |
| B4 – B4RoomBlockerTest | 11 | 0 | 0 | 0 |
| **Gesamt B1–B4** | **223** | **0** | **0** | **0** |

Eine Unterbrechung der Gesprächsarbeit unterbrach den Runner nicht. Die zuletzt
ausgeführte lesende Fortschrittsabfrage wurde vollständig wiederholt; der
ursprüngliche Lauf endete erfolgreich. Kein doppelter Testlauf oder neuer
Bestandsbootstrap wurde deshalb ausgelöst.

Lokale Runner-/Build-Evidenz und daraus abgeleitete Testzahlen liegen getrennt
unter der ignorierten `.prototype-cache/test-evidence/galaxy-m52/`.
Fairphone-Evidenz wurde nicht überschrieben. Die Ergebnisquelle ist der direkte
Runner; kein Galaxy-Gradle-connected- oder Galaxy-XML-Lauf wird behauptet.

## Befunde pro Stufe

- **B1:** SQLCipher-Erstellung, Read/Commit/Rollback/Close/Reopen, Fehlerbestände,
  Schlüssel-/Schemafehler, Sessionwiderruf und Sidecar-/WAL-/Journal-Inventar
  bestanden gemäß der vorhandenen Suite. Kein automatischer Ersatzbestand.
- **B1.5:** RSA-Wrapping-Inventar, Policy, verweigertes Unwrap ohne frischen
  Prompt, Nicht-Ersetzung, Truncation und Cipher-Identitätsprüfung bestanden.
  Diese Automatik belegt keinen positiven Systemdialog-/Unwrap-Lauf.
- **B1.6:** Kombinierte synthetische Bootstrap-/DB-/Session- und Fault-Fixtures
  bestanden. Der manuelle DEVICE_CREDENTIAL-/DB-Restart bestand ebenfalls;
  Einzelheiten stehen im Abschnitt zu den manuellen Tests.
- **B2 Direct und Room:** Beide vollständigen 28-Fälle-Matrizen bestanden,
  einschließlich Fehlerbeständen, CREATE vs. OPEN, Migrationen, Constraints,
  Commit-Widerruf, Generationen, Close und kontrollierter d.lck-Behandlung.
  Kein Samsung-spezifischer Adapter oder Workaround.
- **B3:** Echte Tink-Streaming-/EOF-/Manipulations-/AAD-/Längen- und
  Sessionprüfungen bestanden. Partial-Plaintext-Risiko bleibt bestehen;
  vollständige Verifikation vor Publish bleibt erforderlich.
- **B4:** Alle 119 Fälle bestanden: 52 Import- und 36 Delete-Matrixfälle,
  20 Sicherheitsfälle und 11 Room-Lifecycle-Fälle. Reconciliation und
  Idempotenz bestanden; Unknown State/Orphans blieben erhalten, fehlende
  Dateien wurden nicht ersetzt. Maintenance-Ablauf, alte Handles und
  Session-/Generationswiderruf wurden fail closed behandelt.

Kein erneuter Room-Refresh-Prozesscrash. Erwartete typisierte Revocation und
unerwartete I/O-/SQLite-Background-Faults wurden gemäß dem bestehenden
instanzlokalen Handler-Profil unterschieden. Close-/Refresh-Rennen und eine
neue legitime Instanz nach Widerruf bestanden. Keine Gate-Ausnahme oder
Deadline-Verlängerung wurde hinzugefügt.

Es wurden ausschließlich die vorhandenen synthetischen Markerprüfungen benutzt.
Die Suite bestand; Runner-Inventar meldete keinen `markerHit=true`.
Dies ist kein vollständiger Klartextfreiheits-, RAM- oder Forensiknachweis.
Vorhandene testinterne Fault-Mutationen betreffen ausschließlich synthetische
Fixtures; keine Bestandsbereinigung zum Erzwingen grüner Tests.

## Beobachtete Keystore-Eigenschaften

Die automatische B1.5-RSA-Testfixture meldete auf dem Galaxy:

```text
TEE; authHardware=true; RSA2048/OAEP; DEVICE_CREDENTIAL; PER_OPERATION;
KEYSTORE_ALIAS_PRESENT; WRAPPED_KEY_PRESENT
```

Dies ist eine tatsächliche KeyInfo-/Policy-Beobachtung für einen neu erzeugten
synthetischen Testalias auf diesem Gerät. Die Eigenschaften wurden nicht in
allen drei manuellen Läufen erneut direkt abgelesen und werden nicht auf deren
Haupt-App-Aliase übertragen. Keine StrongBox-Garantie und keine
Verarbeitung der gesamten App, SQLCipher oder Tink innerhalb der TEE.

Der vorhandene Provider-Diagnosetest meldete für expliziten Cipher-Lookup mit
Providername AndroidKeyStore `NoSuchAlgorithmException`; der bereits verwendete
Plattform-JCA-Lookup meldete `RSA/ECB/OAEPPadding`. Dasselbe Verhalten ist als
Fairphone-Befund dokumentiert. Der bestehende JCA-Pfad wurde nicht verändert;
kein Algorithmus-/Autorisierungs- oder Zeitfenster-Fallback.

## Manuelle positive Tests

Vom Nutzer tatsächlich beobachtet auf Samsung SM-M526BR / Android 13 / API33.
Alle drei Läufe verwendeten den sichtbaren Android-DEVICE_CREDENTIAL-Pfad.

| Pfad | Session | Test-ID | Keystore-Autorisierung | Konkrete Schlüsseloperation |
| --- | --- | --- | --- | --- |
| B1.5 authentifizierter Testzugriff | READY | B15-UNWRAP-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B1.6 DB-Restart | READY | B16-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B3 File-Restart | READY | B3-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |

Damit sind der positive explizite Keystore-Unwrap-Pfad sowie die bestehenden
synthetischen DB-/File-Restart-Pfade im getesteten Geräteumfang praktisch
belegt. Hardware-Key-Eigenschaften stammen separat aus der automatischen
RSA-Testfixture; für diese manuellen Läufe wird keine erneute direkte
KeyInfo-Ablesung behauptet. Keine PIN oder sonstiges Credential wird gespeichert.

Bei unerwartetem BLOCKED keine Wiederinitialisierung, kein Löschen oder Reparieren.
Ein Credential wird ausschließlich am Gerät eingegeben und nicht dokumentiert.

## Unit-, Build-, Manifest- und Security-Prüfungen

Frisch ausgeführt: `./gradlew test --rerun-tasks` sowie App-Debug-Build,
Lint für app/data/security, `verifyArchitecture`,
`:app:verifyPrototypeManifest` und `:data:assembleDebugAndroidTest`.
Alle 177 Tasks des kombinierten Aufrufs wurden ausgeführt; BUILD SUCCESSFUL.
**288 Unit-Tests bestanden, 0 Failures/Errors/Skips**, davon 106 B4.
Diese Host-Unit-Tests sind kein zusätzlicher Android-Gerätenachweis.

Unveränderte Baseline: SQLCipher 4.19.1, Room 3.0.3, AndroidX SQLite 2.7.1,
KSP 2.3.12, Tink Android 1.23.0. Keine neue Dependency oder Build-Konfiguration.
Keine neue Permission; kein INTERNET, CAMERA, POST_NOTIFICATIONS oder
Storage-Zugriff. Nur die Haupt-App-Launcher-Activity ist exportiert.
Bestehendes USE_BIOMETRIC dient dem DEVICE_CREDENTIAL-Plattformpfad;
biometrische Authentifizierung bleibt ausgeschlossen.
FLAG_SECURE, Recent-App-Schutz und Backup-/Transfer-Ausschlüsse bleiben erhalten.
Das tatsächliche Samsung-Backup-/Transfer- und Screen-Capture-Verhalten ist
damit nicht praktisch nachgewiesen.

## Vergleich und weiterhin offene Grenzen

Galaxy M52 (Samsung / API33) und Fairphone 6 (API36) bestanden beide die
bestehende automatische B1–B4-Regression mit jeweils 223 Tests und die
relevanten manuellen Keystore-/Restart-Pfade. Beide zeigten im getesteten
Umfang hardwaregestützte TEE-Evidenz mit `authHardware=true`; auf dem Galaxy
stammt diese aus der automatischen RSA-Testfixture.
Daraus folgt keine allgemeine Identität des Dateisystem-, Keystore- oder
OEM-Verhaltens und keine allgemeine Samsung-/Android-Garantie.

Offen bleiben echte invasive Force-stop-/SIGKILL-Fälle an Storage-Übergängen,
Credential-Wechsel/-Entfernung, Keystore-Invalidierung, App-Daten-Clear/Reinstall,
Speicherknappheit, Power-loss, OEM-spezifische Backup-/Transfer-Reste,
RAM-/Flash-Reste, kompromittiertes OS/Kernel und reale Multi-Key-
Authentifizierungsorchestrierung. Diese Punkte bleiben für die folgende
invasive Galaxy-Testphase bzw. separate Integrationsuntersuchungen offen;
dieser Abschluss führt keine solchen Tests aus und erteilt keine Schutzgarantie.
Die bestandenen positiven Restart-Pfade sind kein Nachweis für Crash an jedem
Übergang. B4s Prozessverlustmodell führt weiterhin finally/close aus und ist
kein vollständiger SIGKILL-/Force-stop-/Power-loss-Nachweis.
API30/API37 bleiben ebenfalls offen. RAM-/Flash-Reste sind nicht zuverlässig
vollständig löschbar; gegen ein kompromittiertes OS/Kernel besteht keine Garantie.
