# P1 – DB-only Action Vertical Slice

## Umfang

Produkt-Launcher: LOCKED → explizite DEVICE_CREDENTIAL → MAINTENANCE → READY →
Action-Liste → Action anlegen → Action-Detail. Keine Demo-/Seed-Daten. Die bisherige
PrototypeActivity ist unregistrierte historische Evidenz unter androidTest, kein normaler Bildschirm.
Keine Files, Kamera, Imports, weitere Fachentitäten, Retention-Jobs, Export,
Backup, Netzwerk oder DB+File-Key-Orchestrierung. Kein weiterer invasiver Testblock.

## Produktbestand und explizite Einrichtung

Namespace `noBackupFilesDir/pag-db-v1`, Keystore-Alias
`org.pag.product.database.rsa.v1`. Keine Prototypmigration.

| Zustand | Bedeutung |
|---|---|
| EMPTY | Namespace fehlt/ist vollständig leer, Alias fehlt |
| READY_TO_OPEN | Erwartete Struktur und Alias/Policy vorhanden; noch keine Inhaltsfreigabe |
| PARTIAL_OR_INVALID | Alle anderen, unbekannten oder unklaren Kombinationen |

Der Bootstrap läuft strukturell vor Authentifizierung, ohne Erzeugung. Bekannt sind
`intent` (ein Byte), `public` (X.509-Public-Key-Encoding), `envelope` (256 Byte
RSA-OAEP-Ciphertext), `store/d` (SQLCipher) und `complete` (ein Byte). Nur exakt
leere `store/d.lck` bzw. `store/d-journal` sind bekannte Nebenartefakte;
unbekannte/nonempty Sidecars blockieren. Marker sind keine kryptografischen
Authentizitätsbeweise: nach Auth folgen echte DB-/Schema-/Konsistenzprüfungen.

Nur bewusste Einrichtung reserviert `intent` und erzeugt den RSA-Keystore-Alias,
bevor dessen konkrete DECRYPT-Operation im CryptoObject authentifiziert wird.
Erst **nach erfolgreicher Authentifizierung** werden 32 zufällige DB-Keybytes
mit SecureRandom erzeugt, mittels öffentlichem RSA-OAEP verschlüsselt und nur
verpackt persistiert. Derselbe authentifizierte private Cipher entpackt den Key.
Die Parameter entsprechen dem nachgewiesenen Plattformpfad: SHA-256/OAEP,
MGF1-SHA1, leeres standardisiertes PSource-Label; keine eigene KDF/Container-Krypto.
Keine persistente Klartext-Keydatei, keine Autorisierungsfenster-Semantik.

DB-Erstellung, Prüfung und Schließen erfolgen in MAINTENANCE. Erst nach
vollständigem Bestand wird `complete` exklusiv geschrieben und ein Owner
übernommen. Fehler/Abbruch können reservierten Teilbestand hinterlassen: BLOCKED,
keine Reparatur, kein Überschreiben, kein automatischer neuer Key. Keine globale
Atomarität oder Power-loss-Garantie über Keystore/Filesystem/DB.

## Session-resident product DB key

Der freigegebene instanzlokale SessionDbKeyOwner hält eine kontrollierte Kopie
für exakt eine Generation. Jeder kurze Consumer benötigt den ursprünglichen
StorageContext. Leases werden kontrolliert bestmöglich überschrieben. Owner wird
bei LOCKED/BLOCKED, Ablauf, Vordergrundverlust oder neuer Generation unbrauchbar;
keine Reaktivierung, kein Getter, keine Registry und keine Persistenz.

Sessionparameter: 180000 ms absolut ab tatsächlichem erfolgreichen Auth-Callback,
30000 ms Inaktivität nur in READY. Echte Benutzerinteraktion erneuert nur idle.
MAINTENANCE verbraucht absolut; weder Lifecycle noch I/O/Publication zählen als
Interaktion. Zeitbasis elapsedRealtime, App-Verlassen sperrt sofort. Spätere
Anpassbarkeit ist vorgesehen, aber noch kein Einstellungsfeature.

## Room-/SQLCipher-Lebensdauer

Room 3.0.3 / SQLCipher 4.19.1 / SQLite 2.7.1 / KSP 2.3.12 bleiben unverändert.
Pro Operation: READY-Kontext → Owner-Consumer → kontrollierter B2-Driver → Room →
Operation/Transaktion → Close/Coroutine-Drain → Keylease nullen → zentral geprüfte
Publication. Kein Singleton, Flow-DAO oder automatische Wiederöffnung.
OPEN EXISTING verwendet niemals CREATE-Flag. CREATE nur in expliziter Einrichtung.
Version 1; unbekannte Version blockiert ohne destructive migration. Room validiert
sein generiertes Schema; SQLCipher-/SQLite-/Foreign-Key-Integritätschecks sind
zusätzlich vorhanden. Freitext startet SENSITIVE; Datenbank komplett verschlüsselt.

Der historische Driver benötigt einen Maintenance-Scope-Typ; ein Adapter reicht
alle Entscheidungen des ursprünglichen READY/MAINTENANCE-Kontexts unverändert
weiter. Er autorisiert keinen Zugriff selbst. Die in B4 geprüfte instanzlokale
ExceptionHandler-/SupervisorJob-/Close-Behandlung bleibt erhalten: nur typisierte
StorageAccessRevoked gilt als erwarteter Widerruf, andere Background-Fehler
verhindern Erfolg und führen zu BLOCKED.

P1-Test fand zusätzlich Widerruf **vor COMMIT-prepare**: der bestehende Rollback
bei COMMIT-step allein genügte nicht. Beide Pfade rollbacken nun auf derselben
Connection; der verweigerte Commit wird nie ausgeführt. Bestehende B2-/Room-
Regressionen wurden deshalb erneut geprüft, keine Assertion gelockert.

## Tatsächlich implementiertes Action-Modell

P1 verwendet nur id, title, description, start_at, expected_end_at, created_at,
status und SENSITIVE-Klassifizierung. Titel/Beschreibung nicht leer, Ende >= Beginn.
IDs werden einmal erzeugt. Neue Actions sind DRAFT; alle fünf spezifizierten
Statuswerte sind im Modell vorhanden. Keine Statuswechsel-/Wiedereröffnungs-UI.
Unbekannte ID ergibt kein Objekt. Kein DELETED-Datensatz.

Retention-Regel-IDs und completed_at werden erst mit ihren späteren Features
integriert; offene Retention-Regeln werden nicht durch erfundene Defaultwerte
ersetzt. P1 ist kein vollständiges MVP und keine Retention-Implementierung.
Zeit-Eingabe verwendet Material-3-Kalender und Uhrzeitauswahl direkt im Formular.
Die beim Öffnen des Formulars erfasste Gerätezeitzone wird sichtbar angezeigt;
gespeichert werden weiterhin eindeutige Zeitpunkte. Nicht existente oder doppelte
Ortszeiten an Zeitumstellungen werden ausdrücklich abgewiesen, statt still
verschoben oder mit geratenem Offset gespeichert zu werden. Eine beliebige
Action-Zeitzonenauswahl bleibt außerhalb P1. Keine zusätzliche Freitext-Notiz
oder fachliche Entität. Detailwerte werden lokalisiert mit Zeitzone angezeigt.

Drafts ausschließlich remember, kein rememberSaveable, SavedState, DataStore oder
Preferences. UI-/Repository-Ergebnisse verschwinden bei Sperrung; alte asynchrone
Ergebnisse benötigen den originalen Publication-Permit. JVM-/Compose-/JCA-/native
Kopien können nicht zuverlässig physisch gelöscht garantiert werden.

## Prüfstatus

Frischer Unit-Lauf (`./gradlew test --rerun-tasks`): **305 Tests**, davon
**17 P1-Tests** (8 Security/Inventory, 9 Timing/Session), 0 Failures, 0 Errors,
0 Skips. Die Zeitprüfungen verwenden eine injizierte monotone Clock.

Gezielte Instrumentation auf Galaxy M52 / Android 13 / API33: **76 bestanden**:
7 ProductActionsTest, vollständige B2ComparisonTest-Suite mit 58 Fällen
(29 Direct, 29 Room) und 11 B4RoomBlockerTest. Kein Failure/Error/Skip.
Dies ist der vollständige ausgewählte Regressionslauf, keine vollständige
B1–B4-Gesamtsuite. Abgedeckt sind insbesondere persistierte Actions,
unbekannte ID, fehlende DB, falscher Key, abgelaufene/stale Zugriffe,
Result-Widerruf und Rollback nach Widerruf vor COMMIT-prepare.

Automatische DB-Tests verwenden synthetische Testkeys in einem getrennten
Testpaket; sie ersetzen keinen neuen positiven manuellen
Produkt-DEVICE_CREDENTIAL-/Compose-Nachweis. Dieser wurde anschließend im
unten dokumentierten manuellen Galaxy/API33-Smoke erfolgreich bestätigt.
Kein I1/I2/I3-Neulauf und keine neue invasive Pflichtphase.

Nach finaler Trennung der historischen UI unter androidTest erfolgreich:
App-Build, App-AndroidTest-Build, Lint für app/data/security, Architekturprüfung
und Merged-Product-Manifestprüfung. Data-AndroidTest-Build ebenfalls erfolgreich.
`git diff --check` ohne Befund. Der bekannte kombinierte Gradle-Task-Wiring-Punkt
wurde nicht verändert; erforderliche Prüfungen wurden getrennt ausgeführt.

Keine neue Dependency oder Permission. Product-Manifest weiterhin nur mit
Launcher-Activity; keine Provider/Services/Receiver, kein INTERNET. FLAG_SECURE
bleibt aktiv. Cloud-Backup und Device-to-device-Transfer bleiben vollständig
ausgeschlossen. Der neue Produktname ist PaG; Application-ID bleibt aus
Kompatibilitätsgründen unverändert. Der geprüfte Security-Diff enthält keine
Key-/Credential-Persistenz, Klartext-Schattenkopie, Logging oder automatische
Reparatur/Ersatzinitialisierung. Ein negativer Quelltextscan ist kein physischer
RAM-/Flash-Forensiknachweis.

Offen bleibt die spätere bewusste Anpassbarkeit der Sessionwerte.
File-Key-/Multi-Key-Integration und Retention sind ausdrücklich außerhalb P1.

## MANUAL GALAXY/API33 PRODUCT SMOKE

Status: **P1 MANUAL PRODUCT SMOKE BESTANDEN IM GETESTETEN GALAXY/API33-UMFANG**.

- Gerät lesend bestätigt: Samsung SM-M526BR, API33, genau ein vorgesehenes
  Gerät verbunden, `secure=true`.
- Package `org.pag.prototype.a` installiert. Produktnamespace `pag-db-v1`
  vor Start ABSENT; historische Post-Reinstall-Namespaces bleiben erhalten.
- Installierte APK war noch nicht der geprüfte P1-Build. Normales
  datenbewahrendes Update mittels `adb install -r`: `Success`; kein Uninstall,
  kein Clear, keine Credential-Änderung.
- Produkt-App einmal mit MAIN/LAUNCHER-Intent geöffnet. Anschließende lesende
  Inventur: Produktnamespace weiterhin ABSENT, keine Einrichtung ausgelöst.
- Benutzer bestätigt vor Einrichtung: „PaG“, „Gesperrt“ und
  „Geschützten Speicher einrichten“ sichtbar; keine Prototype-Teststeuerung.
  Bootstrap **EMPTY**. Produktalias **ABSENT**, festgestellt über den
  Produkt-Bootstrap: Bei fehlendem Namespace prüft dieser `containsAlias`,
  ohne Keyzugriff oder Cipher-Operation. Vor Tastendruck bleibt der
  Produktnamespace ABSENT. Keine automatische Initialisierung beobachtet.
- Explizite Einrichtung: Benutzer bestätigt erschienenen Android-
  DEVICE_CREDENTIAL-Systemdialog, sichtbare Speicherprüfung (MAINTENANCE)
  und anschließend Action-Liste mit „Noch keine Actions vorhanden“.
  DEVICE_CREDENTIAL **PASS**, READY/leer **PASS**, keine Seed-Action beobachtet.
- Lesende Dateiinventur nach Einrichtung: `intent`/`complete` je 1 Byte,
  `public` 294 Byte, `envelope` 256 Byte, `store/d` 16384 Byte;
  `d-journal` und `d.lck` jeweils leer. Erwartete Produktstruktur vorhanden,
  keine zusätzliche Datei im Produktnamespace. Produktalias PRESENT durch
  erfolgreichen authgebundenen Einrichtungspfad bestätigt, keine unabhängige
  neue Aliasprobe. Dateiinhalt/Keybytes nicht ausgegeben; die Inventur allein
  ist kein kryptografischer Nachweis der Verschlüsselung.
- Benutzer beobachtet anschließend automatische Sperre nach etwa 30 Sekunden.
  Positive vorläufige Inaktivitätsbeobachtung; gezielter ≥35-Sekunden-Test
  mit gespeicherten Actions und anschließendem Unlock folgt weiter unten
  als bestanden dokumentiert.
- Action Alpha **Create/List/Detail PASS**: Nach Aktualisierung der
  Datum-/Uhrzeitauswahl bestätigt der Benutzer erfolgreiche Speicherung,
  korrekte Detailwerte, Status „Entwurf“ (DRAFT) und Alpha genau einmal
  in der Liste. Ausschließlich synthetische Testdaten.
- Action Beta **Create/List/Detail PASS**: Benutzer bestätigt exakt Alpha
  und Beta in der Liste, jeweils Status „Entwurf“, richtige getrennte
  Detailwerte und keine Vermischung oder Duplikate.
- Gezielte ≥35-Sekunden-Inaktivität mit gespeicherten Actions **PASS**:
  Benutzer bestätigt Sperre, verschwundene Inhalte, keinen automatischen
  Authdialog und keine Wiederfreigabe. Nach bewusstem Unlock sind Alpha
  und Beta weiterhin genau einmal vorhanden. Owner-Invalidierung wird
  durch Unit-Vertragsprüfungen abgesichert; im manuellen Test keine
  RAM-/Keyinspektion und keine physische Löschgarantie.
- Aktivitätsreset und Entwurfsabbruch **PASS**: Benutzer bestätigt frische
  Session, über 30 Sekunden echte Bedienung mit Abständen unter 30 Sekunden
  und weiterhin entsperrte App. Dritter synthetischer Entwurf abgebrochen;
  danach weiterhin exakt Alpha und Beta, keine dritte Action gespeichert.
  Dieser Schritt prüft nicht die absolute 3-Minuten-Grenze.
- App-Verlassen **PASS**: Benutzer bestätigt während READY Home und
  direkte Rückkehr zu PaG: sofort gesperrt, keine Action-Inhalte und kein
  automatischer Authdialog. Nach bewusstem DEVICE_CREDENTIAL-Unlock
  weiterhin exakt Alpha und Beta mit korrekten Details.
- Normaler Prozessneustart ausgeführt: einmal `am force-stop` für
  `org.pag.prototype.a`, danach MAIN/LAUNCHER-Start. Vorher PID vorhanden,
  nach Force-stop kein PID. DB-Ciphertext, Wrapped-Key-Envelope und öffentliche
  Identität haben vor/nach diesem Start identische SHA-256-Digests;
  keine Inhalte/Keybytes ausgegeben und keine Daten gelöscht.
  Benutzer bestätigt **Prozessneustart PASS**: Start „Gesperrt“/„Entsperren“,
  keine Einrichtung und kein automatischer Authdialog. Nach bewusstem
  DEVICE_CREDENTIAL-Unlock Alpha und Beta jeweils genau einmal vorhanden,
  mit korrekten Details. Bootstrap READY_TO_OPEN. Keine neue I1-Serie.
- Smoke-Test bei Alpha-Datumseingabe unterbrochen: Benutzer berichtet eine
  Fehlermeldung zum Ende in der Zukunft. Exakter eingegebener Wert/Fehlertext
  nicht technisch erfasst; keine Ursache daraus als bewiesen abgeleitet.
  Der vorhandene Code verlangte nur Ende >= Beginn, keine Zukunftspflicht.
  Benutzer gibt ausdrücklich UI-Änderung frei: ISO-Textfelder ersetzt durch
  Datum-/Uhrzeitauswahl, getrennte verständliche Validierungsfehler. Keine
  Änderung an Modell, Repository, Session-/Keyvertrag oder Storage.
  Pickerzustände ausschließlich mit `remember` erzeugt; keine saveable
  Formwerte, keine zusätzlichen Dialogfenster oder Lockerung der Fokusregel.
  App-Build, App-Lint, AndroidTest-Build, Architektur-/Manifestprüfung und
  diff-check nach dieser UI-Änderung erfolgreich. Normales datenbewahrendes
  APK-Update erfolgreich; manuelle Auswahl/Speicherung anschließend mit
  Alpha und Beta erfolgreich bestätigt.
- Absolute 3-Minuten-Grenze **manuell PASS**: Benutzer bestätigt frische
  Session und regelmäßige echte Bedienung mit Abständen unter 30 Sekunden,
  App durchgehend im Vordergrund. Nach ungefähr drei Minuten Sperre trotz
  fortgesetzter Aktivität, kein automatischer Authdialog. Nach bewusstem
  DEVICE_CREDENTIAL-Unlock Alpha und Beta weiterhin korrekt vorhanden.
  Keine exakte manuelle monotone Zeitmessung behauptet; exakte Deadline-
  Berechnung zusätzlich durch injizierte Clock in Unit-Tests abgesichert.

### Ergebnis und Grenzen

Explizite Einrichtung/Auth/MAINTENANCE/READY, beide synthetischen Actions,
Inaktivität, Aktivitätsreset, Entwurfsabbruch, App-Verlassen, Prozessneustart
und absolute Sessiongrenze sind im beobachteten Umfang bestanden.
Produkt-UI statt Prototype-UI bestätigt. Der ursprüngliche ISO-Eingabefluss
war unpraktisch und wurde während des Smoke-Tests auf ausdrücklichen
Benutzerwunsch durch Datum-/Uhrzeitauswahl ersetzt; Alpha/Beta wurden danach
erfolgreich geprüft. Kein Credential oder personenbezogenes Echtdatum erfasst.

Keine unabhängige vollständige Log-/Dateisystem-Forensik, kein physischer
RAM-/Flash-Löschbeweis, keine allgemeine Android-/Samsung-Garantie. Der
Storage-/Keyvertrag wurde während dieses Smoke-Tests nicht geändert.
Keine neue invasive Phase, kein Clear/Uninstall/Credential-Wechsel.
Während der Schritte nach der freigegebenen UI-Korrektur ausschließlich
Dokumentation geändert. Keine weiteren Tests oder Produktfeatures begonnen.

LAST SAFE CHECKPOINT: P1 MANUAL PRODUCT SMOKE vollständig abgeschlossen;
absolute Grenze trotz Aktivität und anschließender bewusster Unlock PASS,
Alpha/Beta weiterhin korrekt vorhanden. Keine weitere Geräteaktion nötig.

### Test-only Regression-Scan

Wegen umfangreicher erhaltener synthetischer Testfixtures dauerte der bisherige
B2-Markerscan pro Fall sehr lange. Nur der exakte Byte-Substringvergleich wurde
unter androidTest durch eine primitive Schleife ohne Iterator-/Boxing-Overhead
ersetzt. Alle Dateien, vier Kodierungen, Marker und Assertions bleiben gleich.
Ein zusätzlicher Äquivalenzfall je Variante vergleicht die alte und neue Predicate
an synthetischen Bytefolgen einschließlich Grenzen/Überlappungen. Die komplette
B2-Suite umfasst damit 58 statt 56 Fälle. Keine Fixture gelöscht.
Der langsame unvollständige Teillauf wurde beim normalen Test-APK-Update beendet;
er ist ausdrücklich kein vollständiger Regressionnachweis. Maßgeblich ist der
anschließende frische vollständige ausgewählte Lauf.
