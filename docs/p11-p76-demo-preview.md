# P76 – Isolierte ACTIVE UI Vorschau

Basis `f6960dce6d68fab145f62ed428bd3a71d91b3017`; Branch
`p11-performance-optimization`. Produktweiter RAM-Reuse bleibt false. Dies ist eine
synthetische UI-Vorschau, kein ProductRuntime-/SQLCipher-/authorizeHit-E2E und keine
Security- oder Releasefreigabe.

## Warum die Vorschau kein Live-RAM-Hit ist

ProductRuntime initialisiert seine Session über ProductCredentialAuthentication,
DEVICE_CREDENTIAL-CryptoObject, ProductDbEnvelope, autorisierte Maintenance und den
Keyowner. Für eine sofort vorführbare isolierte Session ohne Benutzercredentials fehlt
ein gesicherter Runtime-Fixture-Bootstrap. Die vorhandene §75-Lifetime verlangt ein
frisches READY-Context und originales Ticket. Ein anderes Test-Session-/Fixture-Read
als Produkt-Hit auszugeben wäre sachlich falsch. Deshalb benutzt P76 ausschließlich den
bestehenden `DemoState`/`DemoScreen` mit erfundenen In-Memory-Daten und weist das in der
UI aus. Vollansichten und Demo-Editoren bleiben erreichbar; es gibt keine SQL-/Key-/P3b-
oder Dokumentdaten.

Der neue Buildtyp `p76demo` trägt die feste ID `org.pag.prototype.a.p76demo`. Nur
sein BuildConfig enthält `PAG_P76_PREVIEW=true`; Produkt- und vorhandene Demo-Defaults
haben false. Die Konstante startet die Vorschau direkt im synthetischen ACTIVE-Lagebild,
sie schaltet keine RAM-Autorisierung ein. Es existiert kein UI-Schalter für Sicherheit.
`verifyP76DemoIsolation` prüft APK-Paket-ID, alleinige dynamische Receiver-Permission,
Backup-/Cleartext-Einstellungen, fehlende Provider/Receiver/Services und Demo-Label.
P76 entfernt Kamera-/Biometric-Permissions und den PDF-Service aus dem Build.

## Gerät, Paket und Ergebnis

Gerät Samsung SM-M526BR, Seriennummer RFCT1186YRJ. Paketbestand vor Installation enthielt
nur `org.pag.prototype.a` und `org.pag.perf.p11probe`; keine Installation für P76-IDs.
Target und Test waren mit dem projektinternen Android-Debug-Zertifikat signiert,
SHA256 `9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134`.

| Artefakt | ID | SHA256 |
|---|---|---|
| Target | `org.pag.prototype.a.p76demo` | `1fccc301f4083ae7df548c68ec6910313e8cb0343eda8c8ed04283ef8ed53d8d` |
| gezielter Draw-Test | `org.pag.prototype.a.p76demo.test` | `2872b71130c189bd717b74aa2068a54e0f67c121cd050efcd4884325f1b859cf` |

Der gezielte Custom-Runner öffnete die echte isolierte Demo-MainActivity/Compose-Oberfläche,
wechselte 20-mal je zu PERSONS, FUNCTIONS, TASKS und HUB und erfasste genau den ersten
ViewTree OnDraw nach jedem Zustandswechsel. 80/80 Draws, Runnerstatus 0. Rohwerte in
[device-draws.csv](p11-p76/device-draws.csv).

| Ansicht | Stichproben | Median | p95 |
|---|---:|---:|---:|
| PERSONS | 20 | 25,057 ms | 32,094 ms |
| FUNCTIONS | 20 | 13,272 ms | 19,298 ms |
| TASKS | 20 | 12,460 ms | 23,535 ms |
| HUB | 20 | 5,952 ms | 9,417 ms |

Das sind synthetische **Statewechsel-bis-erster-Draw**-Messungen auf dem Gerät. Sie
enthalten keinen echten Tastendruck, Sessioncheck, RAM-Hit, SQL-Read, Netzwerk oder
Credentials; sie sind keine Produkt-RAM-Content-OnDraw-Zahlen und kein p95-Releasebeleg.
Runnergebnis: `authorized_ram_hits=0`, `fresh_sql_reads=0`, `draws=80`. Die Vorschau
benutzt DemoState-Werte, nicht ProductActiveCorePayload. Keine `authorizeHit`-Aufrufe
wurden simuliert oder gezählt.

Host: `DemoStateTest` 21/21, keine Fehler/Skips. P76 Target, Instrumentation und
Isolationsprüfung erfolgreich gebaut. Der vollständig gezielte Runner ist Android-
instrumentiert ausgeführt worden. Es gab keinen Product-RAM-Hit.

Installationen: eigenes P76-Testpaket nach dem Lauf entfernt; eigenes P76-Target bleibt
absichtlich installiert und im Vordergrund für die unmittelbar mögliche Präsentation.
Geschützte Pakete wurden vor/nach geprüft; Version, Signatur und Installationszeiten
unverändert. Keine Produktdaten oder Benutzercredentials verwendet.

## Build und Präsentation

```sh
./gradlew -PpagTestBuildType=p76demo :app:assembleP76demo :app:assembleP76demoAndroidTest :app:verifyP76DemoIsolation --offline
adb shell am instrument -w -r org.pag.prototype.a.p76demo.test/org.pag.prototype.P76DemoPreviewProbe
```

Für die Geräteinstallation zuerst APK-Paket-ID, Signatur und Paketbestand erneut
verifizieren. Der bereits installierte P76-Preview startet direkt in einer synthetischen
ACTIVE-Aktion; HUB, Personen, Funktionen und Aufgaben sind dynamisch erreichbar. Die
Anzeige nennt ausdrücklich „SYNTHETISCHE UI-VORSCHAU“ und erklärt, dass keine
Produktautorisierung/RAM-Hits aktiv sind.

## Grenzen / Gate

§75s per-Hit-Contract bleibt produktiv in ProductActiveCoreLifetime implementiert;
P76 ruft ihn absichtlich nicht auf, weil keine sichere isolierte ProductRuntime-READY-
Initialisierung vorhanden ist. Echte ProductRuntime→Room/SQLCipher→ViewModel→Compose-
Reads, per-Hit-Zähler, frische Tickets/Contexts, Session-/Writerrevoke, `authorizeHit`
Latenz und RAM-Hit-/Fresh-Read-Zahlen bleiben nicht nachgewiesen. Für diese Vorschau
sind die entsprechenden Zähler ausdrücklich null/nicht anwendbar.

Reguläres Produkt-RAM-Gate bleibt false. #32/#33/#34/#39 und echtes ACTIVE-E2E bleiben
vor Produktfreigabe offen. RED-4 bleibt fail-closed; #36 Editor-ABA bleibt offen.
P76 ist ausschließlich ein vorführbarer synthetischer UI-Modus.
