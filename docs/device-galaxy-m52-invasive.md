# Galaxy M52 – invasive Testphase I1

## Auftrag und Ausgangszustand

Status: **I1 BESTANDEN IM GETESTETEN GALAXY/API33-UMFANG.**
A–H, Reboot-Recovery und vollständige Abschlussregression sind bestanden.
Der Transport-Spike ist im getesteten Galaxy/API33-Umfang bestanden. Der Spike-Owner ist nach dem absichtlichen
N6-Verlusttest I1-HOST-KEY-LOST/BLOCKED; keine Ersatzkeys erzeugen.

- Gerät laut freigegebenem Profil: Samsung SM-M526BR / Galaxy M52.
- Android 13 / API33, arm64-v8a.
- Commit unter Test: `c6ab95a` – Document Galaxy M52 security baseline.
- Repository vor Anlegen dieses Dokuments sauber.
- Geräteverbindung und aktuelle DEVICE_CREDENTIAL-Verfügbarkeit müssen vor
  Ausführung noch erneut lesend bestätigt werden.
- Keine ADB-Seriennummer wird dokumentiert.

Ziel: echte Android-Force-stop-Grenzen A–H und anschließend einen
OS-gesteuerten Reboot bei Import C untersuchen. Neue Prozesse sollen dieselben
synthetischen DB-/File-Bestände mit frischem Sessionkontext zweimal
reconciliieren. I1 ist kein Multi-Key-Authentifizierungsnachweis.

Die committed Baseline in [device-galaxy-m52-baseline.md](device-galaxy-m52-baseline.md)
bleibt unverändert: 223 Instrumentationtests und 288 frisch ausgeführte
Unit-Tests ohne Failure/Error/Skip; positive manuelle B1.5-/B1.6-/B3-Pfade.

Ausgeschlossen: Credential-Änderung/-Entfernung, pm clear, Uninstall/Reinstall,
Storage-Full, Power-loss, Produktimplementierung, neue Key-Hierarchie,
Zeitfenster-Fallback, Klartext-Key-Persistenz, Reparatur oder Bereinigung eines
unerwarteten Bestands. Kein Commit und kein Push.

## Vorab erkannte Voraussetzung: Schlüssel über Prozessgrenzen

Der bestehende `B4TestFixture` erzeugt zwei getrennte zufällige 32-Byte-Keys
im Instrumentationprozess. `B4TestKeyConsumer` kopiert sie pro Verbrauch und
überschreibt die kontrollierten Kopien in finally. Die Owner werden am
Testende überschrieben. Das bisherige Prozessverlustmodell rekonstruiert
Storage-Owner im selben Prozess; die Key-Owner bleiben dabei verfügbar.

Ein echter Force-stop beendet auch diese Key-Owner. Ein Geräte-Reboot beendet
zusätzlich jeden anderen geräteseitigen RAM-Owner. Ein neuer Prozess kann
mit den bestehenden Consumern daher dieselbe DB und denselben Ciphertext
nicht wieder öffnen. Neu erzeugte Keys wären kein zulässiger Wiederanlauf.

Vor Ausführung muss ein ausdrücklich testinternes Verfahren für dieselben
Keys über diese Grenzen konkretisiert werden. Ein möglicher Ansatz ist ein
Host-RAM-Owner mit geschützter kurzlebiger Übergabe an jeweils neue
Instrumentationprozesse. Keys dürfen dabei weder in Kommandozeilen/Runner-
Argumenten noch Logs, Reports oder Dateien erscheinen. Dieser Ansatz ist
noch nicht implementiert oder freigeprüft; er darf keine Produkt-Key-
Architektur ersetzen. Alternativ wäre eine explizit freigegebene etablierte
verschlüsselte Test-Key-Persistenz erforderlich; sie wird hier nicht erfunden.

## Szenariostand

| Fall | Checkpoint | Stand |
| --- | --- | --- |
| A | Nach Import-Jobreservierung | Nicht gestartet |
| B | Nach Stream-close, vor voller Validation | Nicht gestartet |
| C | Nach File-Publish, vor Metadata-Commit | Nicht gestartet |
| D | Nach Metadata-Commit, vor Jobabschluss | Nicht gestartet |
| E | Nach Delete-Jobreservierung | Nicht gestartet |
| F | Nach Hide, vor unlink | Nicht gestartet |
| G | Nach unlink, vor Metadata-Remove | Nicht gestartet |
| H | Nach Metadata-Remove, vor Jobabschluss | Nicht gestartet |
| Reboot | Import C, erst nach erfolgreichem A–H | Nicht gestartet |

Keine PASS-/FAIL-Einstufung: keine invasiven Tests ausgeführt.
Kein PID-/Force-stop-/Finally-Nachweis liegt vor. Keine neuen Testfixtures
auf dem Gerät angelegt. Baseline-Regression nicht erneut ausgeführt.

## Fortsetzung und Grenzen

Letzter vollständig abgeschlossener Schritt: sauberer Git-Ausgangszustand
geprüft und bestehende B4-Key-Consumer samt Prozessverlust-Abgrenzung gelesen.
Kein Test läuft; aus I1 bleibt ausschließlich dieses Dokument zurück.
Nächste sichere Aktion: testinternen Schlüsseltransport konkretisieren und
prüfen, anschließend Geräteprofil/Credential und Manifest/Runner/Zielprozess
lesend verifizieren. Erst danach Checkpointcode und echte Szenarien starten.

Tatsächlicher Force-stop ist kein allgemeiner SIGKILL-Nachweis; `adb reboot`
ist kein Power-loss-Nachweis. RAM-/Flash-, OEM-Durability- und kompromittiertes-
OS-Grenzen bleiben bestehen.

## I1 KEY TRANSPORT – freigegebener isolierter Spike

Gewählte Methode: Android-Testcode verbindet einen ABSTRACT LocalSocket
`pag_i1` über `adb reverse` mit einem ausschließlich auf Host-Loopback
gebundenen TCP-Socket. Kein Android-INTERNET, kein Produkt-Testhook und
keine neue Komponente. Hosttool: `tools/i1/key_owner.py`.

Feste Version I1K1, nicht geheime 32-stellige Session-ID, zwei 32-Byte-Keys,
exakte Paketlänge und echtes Kanalende. Die Keys werden einmal erzeugt und
nur binär transportiert; keine beabsichtigte persistente Speicherung.
Metadaten enthalten ausschließlich PID, Session-ID, Port und Control-Socket.
Keine Sicherheitsbehauptung über den Transport oder Host-RAM/OS-Reste.

Aussagegrenze: ausschließlich echter Prozessverlust/Restart/B4-Reconciliation;
keine Produkt-Key-Hierarchie, Multi-Key-DEVICE_CREDENTIAL, Produktkey-
Wiederherstellung, Backup/Recovery oder Sicherheitswirkung des Transports.

Owner-Verlust: I1-HOST-KEY-LOST/BLOCKED. Vorhandener Namespace bleibt erhalten;
keine Ersatzkeys, keine Ersatzinitialisierung. Der Owner verweigert Start bei
vorhandenen Verwaltungsartefakten. Ein neuer Namespace erfordert neue bewusste
Entscheidung. Rein lesend: `python3 tools/i1/key_owner.py status
.prototype-cache/i1-owner` meldet PID, aktive Session-ID und Zustand, nie Keys.

Aktueller Stand: Test-APK erfolgreich kompiliert; T1–T7 und N1–N6 noch nicht
auf dem Gerät ausgeführt. A–H weiterhin nicht freigegeben zur Ausführung.
LAST SAFE CHECKPOINT: Transportcode kompiliert, noch kein Host-Owner gestartet.

### Spike-Fortschritt

T1 PASS: Owner PID 1114886, Session b0e485c5d2748c2be0dbf77b9e9e7964.
T2 erster Versuch FAIL: I1-TRANSPORT-BLOCKED vor Dateimutation; kein
synthetischer Gerätebestand erzeugt. Kein Schlüsselersatz oder Reinitialisieren.
Transportdiagnose wird vor erneuter Ausführung in feste Schritt-IDs getrennt.
T3–T7 und N1–N6 noch nicht ausgeführt.
LAST SAFE CHECKPOINT: T1; P1 beendet, Transport noch nicht bestanden.

T2/T3 PASS nach testinternem Fix: Socket-Timeout erst nach connect setzen.
P1 erzeugte mit demselben unveränderten Owner einen eigenen Room/SQLCipher-
und Tink-Bestand; zweimalige Reconciliation und Ciphertextgleichheit bestanden.
P1 endete normal: OK (1 test), 0 Failure/Error/Skip.
LAST SAFE CHECKPOINT: T3; Bestand i1-b0e485c5d2748c2be0dbf77b9e9e7964 vorhanden.

T4/T5 PASS: neuer P2-Lauf öffnete denselben Bestand, Reconciliation zweimal
erfolgreich, Metadaten/Ciphertext unverändert; OK (1 test).
T6 wird jetzt gestartet: geordneter adb reboot, kein Power-loss.
Owner unverändert PID 1114886 / Session b0e485c5d2748c2be0dbf77b9e9e7964.
LAST SAFE CHECKPOINT: T5. Nächste sichere Aktion nach Reboot: normaler
Geräte-Unlock, Owner/status und ADB prüfen, Reverse neu verbinden, P3 open.
Keinesfalls create erneut ausführen.

T6: adb reboot erfolgreich ausgelöst. Host-Owner danach weiterhin OWNER_ALIVE,
identische PID/Session; ADB-Rückkehr/normaler User-Unlock noch ausstehend.
T7 und N1–N6 ausstehend. Kein Test läuft.
LAST SAFE CHECKPOINT: T5; T6-Reboot ausgelöst, Fortsetzung ausschließlich mit
P3 open nach ADB/Unlock und Owner-Statusprüfung.

Manifestprüfung: Test-APK targetPackage org.pag.data.test, AndroidJUnitRunner;
keine zusätzliche Permission/Komponente, weiterhin kein INTERNET.
Produktcode und Produktmanifest unverändert.

### Unterbrechungspunkt nach Reboot

ADB meldet aktuell kein verbundenes Gerät. T7 und die Android-Negativtests
werden deshalb nicht gestartet; kein Fehler wird durch neue Keys repariert.
Der Host-Owner ist weiter aktiv. Zur Fortsetzung Galaxy normal entsperren
und USB/ADB-Verbindung wiederherstellen. Danach status prüfen und ausschließlich
i1_action=open starten, niemals create für diesen Bestand wiederholen.

Security-Review der neuen Dateien: Keybytes werden ausschließlich binär
transportiert und als kontrollierte ByteArrays bestmöglich überschrieben.
Persistiert werden nur nicht geheime Owner-Metadaten und synthetischer
verschlüsselter DB-/File-Bestand. Keine Key-Ausgabe, keine neue Runtime-
Dependency, keine Produktänderung und keine neue Permission.
Dies ist ein Code-Review-Befund, kein absoluter Klartextfreiheitsnachweis.
Whitespaceprüfungen bestanden. N1–N6 bleiben NICHT AUSGEFÜHRT.
LAST SAFE CHECKPOINT: T5 bestanden; T6-Reboot ausgelöst, Owner danach
unverändert bestätigt; T7 wartet auf normal entsperrtes ADB-Gerät.
A–H wurden nicht gestartet.

Fortsetzungsprüfung nach Nutzerbestätigung „Handy entsperrt“:
Host-Owner weiterhin OWNER_ALIVE, PID 1114886, identische I1-Session.
ADB meldet weiterhin kein verbundenes Gerät. Daher kein P3-/Negativlauf
gestartet; kein Gerätebestand verändert und keine Ersatzkeys erzeugt.
LAST SAFE CHECKPOINT unverändert: T5 bestanden; T6-Reboot ausgelöst;
T7 wartet auf wiederhergestellte USB-/ADB-Verbindung.

T6/T7 PASS: nach normalem Unlock/ADB-Rückkehr öffnete P3 denselben
synthetischen Bestand. Zweimalige Reconciliation, gleiche Metadaten und
Ciphertextbytes bestanden: OK (1 test), kein Failure/Error/Skip.
Owner weiterhin identische PID 1114886 / Session; kein Owner-Neustart.
LAST SAFE CHECKPOINT: T7 bestanden. N1–N6 als Nächstes; A–H ungestartet.

## Abschluss des isolierten Transport-Spikes

| Schritt | Ergebnis |
| --- | --- |
| T1 | PASS – Keys einmal erzeugt, dedizierter Host-Owner |
| T2/T3 | PASS – P1 erzeugt Bestand und endet normal |
| T4/T5 | PASS – neuer P2 öffnet denselben Bestand, endet normal |
| T6 | PASS – geordneter Galaxy-Reboot, Host-Owner unverändert |
| T7 | PASS – P3 öffnet nach normalem Unlock denselben Bestand |
| N1 | PASS – fehlender LocalSocket verweigert Zugriff, keine Mutation |
| N2 | PASS – falsche I1-Session verweigert Zugriff |
| N3 | PASS – verkürzte Übertragung verweigert Zugriff |
| N4 | PASS – Zusatzbytes und falsche Version verweigert Zugriff |
| N5 | PASS – zweimaliger Abruf liefert byte-identische Keys; keine Regeneration |
| N6 | PASS – bewusst verlorener Owner-Key-Zustand blockiert; Bestand byte-identisch |

N1–N5 wurden gemeinsam in einem Instrumentationtest ausgeführt, N6 separat.
Fünf erfolgreiche Spike-Läufe: P1, P2, P3, Negativblock, Verlustblock;
jeweils OK (1 test), 0 Failure/Error/Skip. Der frühere erste P1-Transportfehler
bleibt als Diagnosehistorie dokumentiert. Kein A–H-Lauf und kein vollständiger
B1–B4-Regressionslauf wurde als Teil dieses Spikes behauptet.

Für N6 wurde der bereits freigegebene Control-Befehl LOSE absichtlich
irreversibel ausgelöst: Owner bleibt derselbe Prozess, überschreibt seine
kontrollierte Key-ByteArray bestmöglich und verweigert weitere Übertragungen.
Keine zuverlässige Löschung aller Python-/OS-Kopien wird zugesichert.
Der erhaltene Spike-Bestand darf nicht erneut initialisiert oder mit neuen
Keys geöffnet werden. Es wurden keine Ersatzkeys erzeugt.

Für die spätere A–H-Serie ist daher ausdrücklich eine neue separate
synthetische Owner-/Namespace-Konfiguration freizugeben. Der verlorene
Spike-Bestand und seine Verwaltungsdaten bleiben unangetastet; der bestehende
Owner darf nicht automatisch neu gestartet werden.

Produktmanifest und Produktkomponenten unverändert; Testmanifest weiterhin
keine INTERNET- oder neue Permission und keine Transportkomponente.
Keystore-/Session-/Room-Produktverträge, Dependencies und Baseline unverändert.
Code-/Artefaktprüfung zeigt keine beabsichtigte persistente Keydatei oder
Key-Ausgabe; persistente Owner-Daten enthalten nur Verwaltungsmetadaten.
Dieser begrenzte Review ist kein absoluter Key-/Klartextfreiheitsnachweis.
Kein Produkt-/Multi-Key-Authentifizierungsnachweis durch den Transport.

LAST SAFE CHECKPOINT: N6 vollständig bestanden; kein Test läuft.
Host-Owner PID 1114886, Session b0e485c5d2748c2be0dbf77b9e9e7964,
Zustand I1-HOST-KEY-LOST. Der verschlüsselte Spike-Bestand bleibt erhalten.
Nächste sichere Aktion: Ergebnis prüfen und ausdrücklich neue separate
synthetische Konfiguration für A–H freigeben. Keine automatische Fortsetzung.

## Neue freigegebene A–H-Serie

Owner gestartet: PID 1142937, Session c88e8207d176303db2faf1d80d9d0815,
OWNER_ALIVE. Verwaltung: .prototype-cache/i1-ah-owner. Keys einmal erzeugt;
identisch für A–H und Reboot. Alter Owner weiterhin LOST, unverändert.
LAST SAFE CHECKPOINT: neuer Owner gestartet; noch kein A–H-Szenario begonnen.

I1 A STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-A; Owner PID 1142937.
LAST SAFE CHECKPOINT: A preparation läuft möglicherweise; nie erneut prepare starten.

I1 A READY_FOR_FORCE_STOP: PID 13810, Phase IMPORT_RESERVED; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: A persistierter Checkpoint, Prozess wartet.

I1 A: echter Android am force-stop org.pag.data.test; alte PID 13810 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: A beendet, Recovery ausstehend.

I1 A PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
LAST SAFE CHECKPOINT: A vollständig beendet; kein Test läuft.

I1 B STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-B; Owner PID 1142937.
LAST SAFE CHECKPOINT: B preparation läuft möglicherweise; nie erneut prepare starten.

I1 B READY_FOR_FORCE_STOP: PID 13902, Phase STAGING_WRITING; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: B persistierter Checkpoint, Prozess wartet.

I1 B: echter Android am force-stop org.pag.data.test; alte PID 13902 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: B beendet, Recovery ausstehend.

I1 B PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
Vorhandene Ciphertext-Digests unverändert.
LAST SAFE CHECKPOINT: B vollständig beendet; kein Test läuft.

I1 C STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-C; Owner PID 1142937.
LAST SAFE CHECKPOINT: C preparation läuft möglicherweise; nie erneut prepare starten.

I1 C READY_FOR_FORCE_STOP: PID 14001, Phase VALIDATED; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: C persistierter Checkpoint, Prozess wartet.

I1 C: echter Android am force-stop org.pag.data.test; alte PID 14001 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: C beendet, Recovery ausstehend.

### Zielprozess-/Vorbedingungsnachweis

Merged Testmanifest: Paket und instrumentation targetPackage beide
org.pag.data.test; Runner androidx.test.runner.AndroidJUnitRunner.
Main-App org.pag.prototype.a wird nicht beendet. Vor A kein pidof-Testprozess.
READY-Meldung enthält Process.myPid; vor jeder Beendigung muss genau dieser
PID durch pidof org.pag.data.test bestätigt werden. Erst dann am force-stop.
Modell SM-M526BR / API33 und secure=true lesend bestätigt. Keine Produkt-
manifeständerung und keine neue Permission.

Checkpoint-Witness speichert ausschließlich synthetische technische IDs,
Phase und Ciphertext-/Sentinel-Digests (keine Klartext- oder Key-Hashes).
Normal-cleanup wird ausschließlich durch den normalen finally-Pfad geschrieben;
Recovery verlangt sein Fehlen. Keiner dieser Marker erteilt Zugriff.
Neue SessionCoordinator-/StorageContext-/Room-Instanzen je Prozess, echte
monotone elapsedRealtime-Zeit; synthetische Policy, keine Verlängerung während
des Wartens. Keine Room-Instanz wird über den Checkpoint offen gehalten.
Sentinels liegen in C/F im übergeordneten reservierten Szenario-Namespace,
außerhalb f/s und f/f. Das ist kein Nachweis erfolgreicher Reconciliation bei
unbekannten Dateien innerhalb des strikten B4-Dateiinventars: dort weiterhin
BLOCKED. Keine Inventarprüfung wurde abgeschwächt.

I1 C PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
Sentinel unverändert (außerhalb B4-Dateiinventar, im Szenario-Namespace).
Vorhandene Ciphertext-Digests unverändert.
LAST SAFE CHECKPOINT: C vollständig beendet; kein Test läuft.

I1 D STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-D; Owner PID 1142937.
LAST SAFE CHECKPOINT: D preparation läuft möglicherweise; nie erneut prepare starten.

I1 D READY_FOR_FORCE_STOP: PID 14096, Phase METADATA_COMMITTED; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: D persistierter Checkpoint, Prozess wartet.

I1 D: echter Android am force-stop org.pag.data.test; alte PID 14096 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: D beendet, Recovery ausstehend.

I1 D PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
Vorhandene Ciphertext-Digests unverändert.
LAST SAFE CHECKPOINT: D vollständig beendet; kein Test läuft.

I1 E STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-E; Owner PID 1142937.
LAST SAFE CHECKPOINT: E preparation läuft möglicherweise; nie erneut prepare starten.

I1 E READY_FOR_FORCE_STOP: PID 14191, Phase DELETE_RESERVED; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: E persistierter Checkpoint, Prozess wartet.

I1 E: echter Android am force-stop org.pag.data.test; alte PID 14191 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: E beendet, Recovery ausstehend.

I1 E PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
LAST SAFE CHECKPOINT: E vollständig beendet; kein Test läuft.

I1 F STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-F; Owner PID 1142937.
LAST SAFE CHECKPOINT: F preparation läuft möglicherweise; nie erneut prepare starten.

I1 F READY_FOR_FORCE_STOP: PID 14286, Phase METADATA_HIDDEN; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: F persistierter Checkpoint, Prozess wartet.

I1 F: echter Android am force-stop org.pag.data.test; alte PID 14286 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: F beendet, Recovery ausstehend.

I1 F PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
Sentinel unverändert (außerhalb B4-Dateiinventar, im Szenario-Namespace).
LAST SAFE CHECKPOINT: F vollständig beendet; kein Test läuft.

I1 G STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-G; Owner PID 1142937.
LAST SAFE CHECKPOINT: G preparation läuft möglicherweise; nie erneut prepare starten.

I1 G READY_FOR_FORCE_STOP: PID 14383, Phase FILE_DELETE_PENDING; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: G persistierter Checkpoint, Prozess wartet.

I1 G: echter Android am force-stop org.pag.data.test; alte PID 14383 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: G beendet, Recovery ausstehend.

I1 G PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
LAST SAFE CHECKPOINT: G vollständig beendet; kein Test läuft.

I1 H STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-H; Owner PID 1142937.
LAST SAFE CHECKPOINT: H preparation läuft möglicherweise; nie erneut prepare starten.

I1 H READY_FOR_FORCE_STOP: PID 14483, Phase METADATA_REMOVED; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: H persistierter Checkpoint, Prozess wartet.

I1 H: echter Android am force-stop org.pag.data.test; alte PID 14483 verschwunden;
wartender Runner abgebrochen. LAST SAFE CHECKPOINT: H beendet, Recovery ausstehend.

I1 H PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
LAST SAFE CHECKPOINT: H vollständig beendet; kein Test läuft.

## A–H zusammengefasst

Alle acht Force-stop-Fälle PASS, jeweils neuer Recovery-Prozess und zwei
Reconciliations. Vorbereiter wartet im Checkpoint; Android beendet genau
org.pag.data.test, alter PID verschwindet, Runner ohne normalen Testabschluss,
normal-finally-Marker fehlt. A endet ausdrücklich ohne Publikation; B wird
erst nach vollständiger Verifikation publiziert; C/D vorhandener Ciphertext
unverändert. E–H entfernen nur bekannten autorisierten Bestand idempotent.
Keine Hidden→Visible-Rückkehr, Ersatzdatei, Ersatz-ID oder Ersatzkey.
C/F Sentinel unverändert. Kein Room-/Background-Fault im Recovery.
Owner-Kontinuität vor/nach jedem Fall geprüft: PID 1142937, Session identisch.
LAST SAFE CHECKPOINT: H vollständig bestanden, zusätzlicher Reboot noch offen.

I1 R STARTED: Namespace i1-c88e8207d176303db2faf1d80d9d0815-R; Owner PID 1142937.
LAST SAFE CHECKPOINT: R preparation läuft möglicherweise; nie erneut prepare starten.

I1 R READY_FOR_FORCE_STOP: PID 14586, Phase VALIDATED; Runner wartet.
Paket org.pag.data.test, PID durch pidof bestätigt.
LAST SAFE CHECKPOINT: R persistierter Checkpoint, Prozess wartet.

I1 R: Reboot jetzt freigegeben, alter PID 14586; Owner unverändert.
LAST SAFE CHECKPOINT: R Checkpoint erreicht; Reboot/Unlock/Recovery ausstehend.

I1 R: adb reboot ausgelöst, kein Power-loss. Nutzer-Unlock/ADB-Rückkehr erforderlich.

Reboot R ausgelöst; Owner danach weiterhin identische PID/Session OWNER_ALIVE.
LAST SAFE CHECKPOINT: H vollständig bestanden; R persistierter Import-C-
Checkpoint (PID vor Reboot 14586), Reboot ausgelöst, Nutzer-Unlock/ADB-Rückkehr
und Recovery ausstehend. Kein Force-stop-Test läuft; R niemals neu prepare.
Die anschließende vollständige Regression/Unit/Build/Lint ist noch ausstehend.

I1 R PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;
Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.
Vorhandene Ciphertext-Digests unverändert.
LAST SAFE CHECKPOINT: R vollständig beendet; kein Test läuft.

R-Recovery nach normalem Geräte-Unlock PASS: gleicher Owner PID 1142937,
gleiche Session; vorhandener Ciphertext und IDs unverändert; zwei
Reconciliations bestanden. Normal-cleanup-Marker fehlt. Kein Power-loss.
Vollständige unfiltrierte Instrumentation-Regressionssuite jetzt gestartet,
Log .prototype-cache/i1-full-regression.log; Unit-/Build-/Lint-/Architektur-/
Manifest-Prüfungen parallel gestartet, Log .prototype-cache/i1-final-build.log.
Keine Teilmenge wird als Gesamterfolg behauptet.
LAST SAFE CHECKPOINT: A–H und R vollständig bestanden; Gesamtregression
und Host-Prüfungen laufen, Ergebnisse noch offen. Keinesfalls A–H/R neu starten.

Frischer Host-Abschluss: Unit Tests/Failures/Errors/Skips [288, 0, 0, 0]; test --rerun-tasks, Build, Lint app/data/security, Architektur/Manifest und AndroidTest-Kompilierung erfolgreich; 177 von 177 Tasks ausgeführt. Vollständige Geräte-Regression weiterhin laufend, noch kein Gesamtergebnis.

## Verbindlicher Abschlussstand I1

**I1 BESTANDEN IM GETESTETEN GALAXY/API33-UMFANG.** Die vorherigen
LAST-SAFE-CHECKPOINT-Einträge dokumentieren den chronologischen Verlauf;
maßgeblich ist der abschließende Eintrag unten.

| Fall | Tatsächlicher Checkpoint | PID vor am force-stop | Alte PID beendet | Recovery / zweite Reconciliation | Ergebnis |
| --- | --- | ---: | --- | --- | --- |
| A | IMPORT_RESERVED, nach Jobreservierung | 13810 | Ja | Job ohne Publikation abgeschlossen; zweite Reconciliation no-op | PASS |
| B | Stream geschlossen, persistiert noch STAGING_WRITING | 13902 | Ja | Bestehende Datei vollständig verifiziert, erst danach publiziert | PASS |
| C | Rename erfolgt, Phase VALIDATED, Metadata noch nicht committed | 14001 | Ja | Bestehender Ciphertext unverändert; Metadata einmalig committed | PASS |
| D | METADATA_COMMITTED, Job noch vorhanden | 14096 | Ja | Vorhandenen Commit anerkannt, Job abgeschlossen | PASS |
| E | DELETE_RESERVED, vor Hide | 14191 | Ja | Bekannten Delete-Job fortgesetzt, kein Ersatzbestand | PASS |
| F | METADATA_HIDDEN, vor unlink | 14286 | Ja | Keine Rückkehr zu Visible; autorisierte Löschung fortgesetzt | PASS |
| G | unlink erfolgt, Phase FILE_DELETE_PENDING | 14383 | Ja | Fehlende Datei idempotent anerkannt, Metadata entfernt | PASS |
| H | METADATA_REMOVED, Job noch vorhanden | 14483 | Ja | Nur Jobabschluss, kein neues Asset oder neue Datei | PASS |

Namespaces jeweils `i1-c88e8207d176303db2faf1d80d9d0815-A` bis `-H`
im noBackupFilesDir der Test-App. Nach jedem Recovery wurden jobs leer und
die erwartete Assetanzahl geprüft, anschließend zweite Reconciliation sowie
unveränderte Snapshot-/Ciphertext-Daten. Vorhandene Job-/Asset-IDs wurden
gegen die vor dem Abbruch gespeicherte technische Witness geprüft.
Für B/C/D wurden die vorhandenen Ciphertext-Digests vor/nach Recovery
verglichen. Es wurden keine Ersatzkeys, Ersatzdateien oder Ersatz-IDs erzeugt.

Alle acht Vorbereiter warteten auf CountDownLatch und kehrten nicht normal
zurück. Der Host bestätigte Process.myPid gegen pidof des tatsächlichen
Runner-Zielpakets org.pag.data.test und führte erst dann am force-stop aus.
Die alte Ziel-PID verschwand und der Runner wurde abgebrochen. Der ausschließlich
im normalen finally-Pfad geschriebene Cleanup-Marker fehlte bei jedem Recovery.
Das ist ein praktischer Nachweis aus diesem Testaufbau, keine allgemeine
POSIX-/SIGKILL- oder RAM-Löschgarantie.

Unknown-Sentinels C/F waren nach Recovery unverändert. Ihre genaue
Aussagegrenze bleibt: im übergeordneten Szenario-Namespace außerhalb f/s und
f/f; unbekannte Dateien im strikten B4-Inventar blockieren unverändert.
Die vollständige B4-Safety-Regression bestätigte zusätzlich Unknown-/Orphan-
Fail-closed-Verhalten; keine Heuristik zum Löschen wurde hinzugefügt.

### Zusätzlicher Reboot

Eigener Namespace `i1-c88e8207d176303db2faf1d80d9d0815-R`, derselbe A–H-Owner.
Vor Reboot: Import-C-Checkpoint, PID 14586, Phase VALIDATED, finaler
verifizierter Ciphertext vorhanden, noch keine Asset-Metadata.
Nach normalem Geräte-Unlock/ADB-Rückkehr: neuer Testprozess, derselbe
bestehende Job und Ciphertext, einmaliger Metadata-Commit, zweimalige
Reconciliation, Ciphertext-Digests unverändert; **PASS**.
Der normale Cleanup-Marker fehlt auch hier. Kein automatischer Hintergrundstart
oder neuer Ciphertext erforderlich. adb reboot ist ein OS-gesteuerter Neustart,
kein Power-loss-Nachweis.

Owner-Kontinuität: PID 1142937 und Session
c88e8207d176303db2faf1d80d9d0815 vor/nach jedem A–H-Fall und nach Reboot sowie
am Ende identisch, OWNER_ALIVE. Alter N6-Owner PID 1114886 weiterhin
I1-HOST-KEY-LOST, nicht verändert oder repariert. Seine Daten bleiben erhalten.

### Vollständige frische Abschlussprüfungen

Direkter AndroidJUnitRunner ohne Klassen-/Methodenfilter: **OK (224 tests)**.
224 erfolgreiche individuelle Abschlüsse, 0 Failures, 0 Errors, 0 Skips.
Der zusätzliche I1-Test prüft im unparametrisierten Gesamtlauf ausschließlich
die Transportverweigerung ohne expliziten Owner-Kontext; er startet kein
invasives Szenario. Die neun positiven A–H-/R-Recovery-Läufe wurden separat
ausgeführt, jeweils OK (1 test). Absichtlich beendete Vorbereiter werden
nicht als erfolgreich normal abgeschlossene JUnit-Tests gezählt.

| Stufe / Klasse | Bestanden | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| B1 / SqlCipherEngineTest | 16 | 0 | 0 | 0 |
| B1.5 / RsaWrappingTest | 7 | 0 | 0 | 0 |
| B1.6 / B16IntegrationTest | 16 | 0 | 0 | 0 |
| B2 / B2ComparisonTest (28 Direct, 28 Room) | 56 | 0 | 0 | 0 |
| B3 / B3StreamingTest | 9 | 0 | 0 | 0 |
| B4 / B4MatrixTest | 88 | 0 | 0 | 0 |
| B4 / B4SafetyTest | 20 | 0 | 0 | 0 |
| B4 / B4RoomBlockerTest | 11 | 0 | 0 | 0 |
| I1 / I1TransportTest | 1 | 0 | 0 | 0 |
| Gesamt | 224 | 0 | 0 | 0 |

Frische Unit-Tests: **288 bestanden**, 0 Failures/Errors/Skips.
`test --rerun-tasks`, `:app:assembleDebug`, Lint app/data/security,
`verifyArchitecture`, `:app:verifyPrototypeManifest`,
`:data:assembleDebugAndroidTest` erfolgreich. 177 Tasks, alle ausgeführt.
Kein Room-Lifecycle-Prozesscrash oder Session-/Generation-Bypass im Gesamtlauf.

Lokale Evidenz: .prototype-cache/i1-ah-*-prepare.log und *-recover.log,
i1-full-regression.log und i1-final-build.log; ignoriert, nicht zum Committen.
Fairphone- und committed Galaxy-Baseline-Evidenz unverändert.

### Security-Diff und Grenzen

Neue Dateien ausschließlich androidTest, Host-Testtools und dieses Dokument.
Keine Produktionscode-, Manifest-, Dependency- oder Build-Konfigurationsänderung.
Test-APK wurde als Update ohne Deinstallation/Daten-Clear installiert; die
Produkt-App wurde nicht neu installiert. Keine neue Permission/Komponente,
kein INTERNET, keine Netzwerkdependency/Cloud/Telemetrie und kein Produkt-Testhook.
Kein neuer Keystore-/Key-Hierarchy-/Zeitfensterpfad und kein Gate-Fallback.

Keybytes nur binär über den freigegebenen lokalen Kanal; keine Strings,
Hex-/Base64-Ausgabe von Keys, keine Keys in Kommandozeilen, Logs oder Reports.
Code-/Artefaktreview zeigt keine beabsichtigte persistente Keydatei. Persistente
Witness/Marker sind ausdrücklich nicht geheime synthetische Testevidenz,
keine Klartext-Produktdaten oder Keysets. Der Runner meldete keinen
markerHit=true; auch dies ist nur ein begrenztes Testsignal, kein absoluter
Klartextfreiheits- oder Key-Persistenznachweis. Kein Python-Bytecode im Git-Diff.

Die Session wird ausschließlich über den zentralen Coordinator mit dem
freigegebenen synthetischen Auth-Adapter und neuem Context pro Prozess
hergestellt. In A–H/R läuft eine echte monotone Zeitquelle; keine Wartung oder
Hostpause verlängert die Deadline. Keine alte Room-/DAO-/Connection-Instanz
überlebt als wiederverwendbarer Zugriff; kurze Room-Instanzen je DB-Schritt.
Die synthetischen Test-Policies sind keine festgelegten Produktdauern.

Weiterhin ausdrücklich keine Garantie:
- am force-stop als allgemeiner POSIX-/SIGKILL-Nachweis;
- adb reboot als Power-loss-/Flash-Durability-Nachweis;
- Host-Key-Transport als reale Produkt-/Multi-Key-DEVICE_CREDENTIAL-Orchestrierung;
- vollständige RAM-/Flash-Löschung oder Schutz gegen kompromittiertes OS/Kernel;
- allgemeines Android-/OEM-Verhalten, API30/API37 oder Backup-/Transferreste.
Credential-Wechsel/-Entfernung, Key-Invalidierung, App-Daten-Clear/Reinstall,
Storage-Full und Power-loss wurden nicht durchgeführt.

LAST SAFE CHECKPOINT: I1 vollständig abgeschlossen. A–H und R PASS,
224 unfiltrierte Instrumentationtests und 288 frische Unit-Tests bestanden,
Build/Lint/Architektur/Manifest/AndroidTest-Kompilierung erfolgreich.
Kein Test läuft. Beide Host-Key-Owner (Transport-Spike und A–H) wurden nach
Abschluss beendet. Kein weiterer Recovery-Lauf mit diesen Ownern ist möglich
oder nötig. Persistente synthetische Testbestände auf dem Gerät und unter
.prototype-cache bleiben unangetastet. Kein Commit und kein Push.

## I2 DEVICE_CREDENTIAL / KEYSTORE

Status: **BLOCKED vor den Credential-Änderungen** – Voraussetzung eines
vorhandenen B1.6-Haupt-App-Bestands derzeit nicht erfüllt.

- Commit unter Test: `3fbccde` – Add Galaxy force-stop recovery tests.
- Repository vor dieser Ergänzung sauber; I1 und Baseline unverändert.
- Genau ein Galaxy SM-M526BR verbunden, API33; Gerätesperrcode vorhanden,
  `secure=true` lesend über Android Window Policy bestätigt.
- Credential weder geändert noch entfernt; kein Credentialwert erfasst.
- Haupt-App `org.pag.prototype.a`: `no_backup/p15` vorhanden, Dateien
  `0` = 1 Byte, `1` = 256 Byte, `2` = 294 Byte.
- `no_backup/b3-v1` vorhanden: `0` = 1 Byte, `m` = 145 Byte,
  Verzeichnisse `k` und `s`; unter `k` Dateien `0` = 1 Byte,
  `1` = 256 Byte, `2` = 294 Byte.
- `no_backup/p16` fehlt laut direktem lesendem `run-as ... ls`.
  Der aktuelle MainActivity-Code verwendet genau diesen B1.6-Namensraum.
  Dies widerspricht der erwarteten aktuellen Bestandsvoraussetzung;
  die frühere erfolgreiche Galaxy-Restart-Evidenz bleibt erhalten.
- B1.5/B1.6/B3-Authentifizierung in I2 noch nicht durchgeführt;
  Aliaspräsenz und Keystore-Fehlerklassen noch nicht erhoben.
  Dateipräsenz allein beweist keine Aliaspräsenz oder Entschlüsselbarkeit.
- Bestand durch diese Diagnose unverändert: ausschließlich lesende ADB-
  Prüfung; keine Initialisierung, Reparatur, Löschung oder Installation.
- I2-A, I2-B, I2-C und Abschlussregression nicht gestartet.

LAST SAFE CHECKPOINT: I2-Vorprüfung beendet, secure=true bestätigt;
B1.6-Namensraum p16 fehlt. Kein Test läuft. Vor Credential-Änderung muss
die Bestandsabweichung geklärt werden; keine Ersatzinitialisierung.

## I2 PRECONDITION INVESTIGATION

**I2 bleibt BLOCKED.** Ausschließlich lesende Ursachenanalyse; keine
Authentifizierung, Installation, Dateimutation oder Alias-Erzeugung/-Löschung.
Der Nutzer bestätigt: kein absichtlicher Profilwechsel und kein bewusstes
pm clear / Uninstall / Reinstall seit dem dokumentierten B1.6-Nachweis.

### Android-User und Package-Kontext

`am get-current-user`: **0**. `pm list users`: ausschließlich User 0,
Eigentümer, running. Aktuell kein Hinweis auf einen anderen User/Profile-
Kontext; dies beweist keine vollständige historische Kontinuität.

`dumpsys package org.pag.prototype.a`:

- Package userId / UID: **10330**; installiert für User 0.
- firstInstallTime: **2026-10-08 19:08:04**.
- lastUpdateTime: **2026-10-08 19:08:04**.
- versionCode **1**, versionName **0.1-stage-a**.
- dataDir: `/data/user/0/org.pag.prototype.a`.

Die Package-Zeitangaben werden unverändert als Android-Ausgabe wiedergegeben;
sie belegen weder einen Daten-Clear noch dessen Ausbleiben. Keine Seriennummer
oder Credentialwerte wurden dokumentiert.

### Lesendes Dateiinventar

Pfade relativ zum Haupt-App-dataDir; Größen in Bytes, mtime wie von `ls -lan`
ausgegeben (Minutenauflösung, keine zusätzliche Zeitzonenannahme).

| Pfad | Vorhanden / Typ | Größe | mtime |
| --- | --- | ---: | --- |
| no_backup | Verzeichnis | 3452 | 2026-10-08 19:24 |
| no_backup/p15 | Verzeichnis | 3452 | 2026-10-08 19:16 |
| no_backup/p15/0 | Datei | 1 | 2026-10-08 19:16 |
| no_backup/p15/1 | Datei | 256 | 2026-10-08 19:16 |
| no_backup/p15/2 | Datei | 294 | 2026-10-08 19:16 |
| no_backup/p16 | Nicht vorhanden | – | – |
| no_backup/b3-v1 | Verzeichnis | 3452 | 2026-10-08 19:24 |
| no_backup/b3-v1/0 | Datei | 1 | 2026-10-08 19:24 |
| no_backup/b3-v1/m | Datei | 145 | 2026-10-08 19:24 |
| no_backup/b3-v1/k | Verzeichnis | 3452 | 2026-10-08 19:24 |
| no_backup/b3-v1/k/0 | Datei | 1 | 2026-10-08 19:24 |
| no_backup/b3-v1/k/1 | Datei | 256 | 2026-10-08 19:24 |
| no_backup/b3-v1/k/2 | Datei | 294 | 2026-10-08 19:24 |
| no_backup/b3-v1/s | Verzeichnis | 3452 | 2026-10-08 19:24 |
| no_backup/b3-v1/s/f7a7f075-6c4a-4429-85af-f2c596fbf7b6 | Datei | 8388809 | 2026-10-08 19:24 |

`run-as`-Verzeichnissuche unter dem App-dataDir bis Tiefe 6 zeigt ausschließlich
cache, code_cache, files, no_backup sowie die oben vorhandenen Unterverzeichnisse;
keinen alternativen B1.6-Namensraum. Keine Key-/Ciphertextinhalte ausgegeben.

### Alias-Inventar und Aussagegrenze

| Erwarteter Haupt-App-Alias | Zustand |
| --- | --- |
| pag.prototype.b16.storage.v1 | NICHT FESTSTELLBAR |
| pag.prototype.b15.storage.v1 | NICHT FESTSTELLBAR |
| pag.prototype.b3.file.v1 | NICHT FESTSTELLBAR |

`RsaWrappingFixture` verwendet intern lesend `containsAlias` und bietet
`checkedState`, aber es gibt keinen vorhandenen separat aufrufbaren
Haupt-App-/Instrumentation-Inventarpfad für diese drei Aliase ohne
Authentifizierung oder Testmutation. Der normale Unwrap-UI-Pfad bereitet
Cipher/Prompt vor; die RSA-Instrumentation erstellt eigene Testaliase unter
der Test-App-UID. Beides wurde hierfür nicht gestartet. Kein neuer Hook,
Debugger-Auswertungsweg oder Testcode wurde gebaut. Dateipräsenz ersetzt
keinen Aliasnachweis.

### Code und Git-Historie

- B1.6 eingeführt mit Commit `742df2d`: MainActivity verwendet bereits dort
  `noBackupFilesDir.canonicalFile/p16`, darunter `k` und Alias
  `pag.prototype.b16.storage.v1`. In der untersuchten Git-Historie kein
  alternativer Haupt-App-Pfad oder Aliasname gefunden.
- `B16Inventory.reserveNew` legt nach EMPTY-Prüfung p16 und Absichtsmarker
  `0` an; `RsaWrappingFixture` schreibt unter `k` die Wrapping-Artefakte.
  `B16StorageGate` erzeugt nur im expliziten Initialisierungspfad DB `d`;
  `B16Inventory.finish` schreibt Abschlussartefakt `1`.
- `B16-RESTART-SUCCEEDED` liest/prüft den bestehenden Bestand, schließt DB
  und gibt kontrollierte Key-/Operationsreferenzen frei. Kein p16-Cleanup.
- In aktuellen B1.6-Routinen und deren untersuchter Lösch-/Cleanup-Historie
  kein Namespace-Löschpfad gefunden. Wechsel zu B1.5/B3 bereinigt keinen
  anderen persistenten Prototypbestand. `viewModelStore.clear` und
  `removeCallbacks` betreffen flüchtige UI-/Lifecycle-Zustände.
- Testseitige Dateilöschungen sind isolierte Fault-Injections; B16IntegrationTest
  verwendet `b16-test-<UUID>` unter dem Instrumentation-targetContext und
  bewahrt seine verschlüsselten Fixtures. Diese sind nicht Haupt-App-p16.
- I1-Hosttool adressiert `org.pag.data.test`; I1-Androidcode verwendet dessen
  targetContext/noBackupFilesDir und eigene `i1-...`-Namensräume. Kein Zugriff
  auf Main-App-no_backup oder p16 gefunden. B4-Delete betrifft ausschließlich
  den bekannten autorisierten B4-Dateizusammenhang.

### Install-/Clear-Log-Befund und Klassifikation

93 vorhandene lokale Evidenzdateien (Top-Level `.prototype-cache` und
`test-evidence`, Endungen log/txt/sh/py/xml) lesend geprüft. Kein Treffer für
Main-App-Paket oder die gesuchten Install-/Clear-/Uninstall-/Package-/
run-as-rm-Befehlsmuster. Kein konkreter Main-App-Lifecycle-Befehl zwischen
Nachweis und I2 belegt. Shell-History wurde nicht pauschal gelesen.
Gradle-Konfiguration und vorhandene Hosttools zeigen keinen expliziten
Main-App-p16-Löschbefehl. Logs sind keine vollständige Command-Historie;
fehlende Treffer beweisen nicht, dass niemals ein solcher Befehl lief.

**Klassifikation F: Ursache nicht feststellbar.** C (anderer historischer Pfad),
D (aktuell anderer User) und E (konkreter Cleanup-/Installationsbefehl) sind
nicht belegt. A gegenüber B bleibt wegen unbekannter Aliaspräsenz offen.
Keine belastbar wahrscheinlichste Erklärung ableitbar. Hypothesen bleiben
ein selektiver Namespace-Verlust oder eine historisch abweichende Zuordnung
des manuellen Nachweises; keine davon ist bewiesen. Die frühere committed
Restart-Evidenz wird nicht umgedeutet. Ein App-/Keystore-Lifecycle-Verlust
ist ebenfalls nicht nachgewiesen.

LAST SAFE CHECKPOINT: lesende I2-Ursachenanalyse abgeschlossen; p16 fehlt,
Aliaszustände NICHT FESTSTELLBAR, Ursache offen. Gerät und vorhandene
Bestände unangetastet, kein Test läuft. I2 nicht sicher fortsetzbar unter
der Voraussetzung dreier vorhandener positiver Pfade. Keine Credential-
Änderung, Ersatzinitialisierung oder Reparatur. Kein Commit und kein Push.

## I2 PRECONDITION – FINAL DISPOSITION

Die ausdrücklich freigegebene einmalige androidTest-only Alias-Probe lief
auf dem Galaxy/API33 unter der Haupt-App-UID: Testpaket
`org.pag.prototype.a.test`, instrumentation targetPackage
`org.pag.prototype.a`. Damit wurde nicht das getrennte Test-App-Keystore-
Inventar von `org.pag.data.test` abgefragt.

| Alias | Ergebnis |
| --- | --- |
| pag.prototype.b15.storage.v1 | PRESENT |
| pag.prototype.b16.storage.v1 | ABSENT |
| pag.prototype.b3.file.v1 | PRESENT |

Probe verwendet ausschließlich `KeyStore.getInstance("AndroidKeyStore")`,
`load(null)` und drei `containsAlias`-Abfragen. Keine getKey-/Cipher-/
Authentifizierungs-/Dateioperation, keine anderen Aliasnamen ausgegeben.
Keine Activity/kein Credential-Prompt wird durch den Probe-Code gestartet.
Keine Produktcode-, Dependency- oder Permissionänderung. Ausschließlich das
neue separate Test-APK installiert; keine Haupt-App-Installation.
`:app:assembleDebugAndroidTest` und `:app:verifyPrototypeManifest` erfolgreich;
gemergtes Probe-Manifest ohne Permissions und ohne Activity/Service/Provider.

Vorher/nachher: p15 und b3-v1 vorhanden, p16 weiterhin nicht vorhanden.
Dateitypen, Größen, mtimes und SHA-256-Dateidigests der vorhandenen Dateien
identisch; keine Wrapping-/Ciphertextbytes ausgegeben. Der Digestvergleich
stützt die Bytegleichheit im untersuchten Inventar, ist kein Forensiknachweis.

**CASE 2: B16 ABSENT + p16 fehlt.** B1.6 ist bezüglich des erwarteten Alias
und Namensraums aktuell leer. Die Ursache des früheren p16-Verlusts bleibt
**UNKNOWN**; der neue Befund beweist keine historische Löschung/Reinstallation.

Konsequenz: Vor Credential-Änderung ist gemäß ausdrücklicher Freigabe genau
eine neue synthetische B1.6-Initialisierung über den bestehenden normalen
UI-Pfad zulässig. Anschließend muss nach Restart einmal
`B16-RESTART-SUCCEEDED` bestätigt werden; ebenfalls die positiven B1.5-/B3-
Vorprüfungen. Diese Schritte wurden hier noch nicht ausgeführt. Erst danach
I2-A/B/C fortsetzen. Keine weitere Ursachenforschung.

LAST SAFE CHECKPOINT: finale read-only Alias-Probe abgeschlossen, CASE 2,
Dateiinventar unverändert. Kein Test läuft; keine Credential-Änderung und
keine neue B1.6-Initialisierung durchgeführt. I2 wartet auf den freigegebenen
normalen B1.6-Initialisierungs-/Restart-Nachweis und positive Vorprüfungen.
Kein Commit und kein Push.

### I2-Fortsetzung – einmalige neue B1.6-Baseline freigegeben

CASE 2 vom Nutzer bestätigt. Genau eine Initialisierung über den bestehenden
normalen B1.6-UI-Pfad freigegeben; keine neue Initialisierungslogik.
Haupt-App für manuelle Bedienung geöffnet, noch keine Initialisierung durch
Codex ausgelöst. Nutzer soll DEVICE_CREDENTIAL ausschließlich am Gerät
bestätigen. Anschließend ist ein echter Force-stop/Neustart mit erfolgreichem
`B16-RESTART-SUCCEEDED` erforderlich. Bei BLOCKED/abweichender ID anhalten,
keine Wiederholung der Initialisierung. Credential bleibt bis Abschluss der
positiven B1.5-/B1.6-/B3-Vorprüfungen unverändert.

LAST SAFE CHECKPOINT: Haupt-App geöffnet; wartet auf Nutzerbestätigung der
einmaligen B1.6-Initialisierung. Erfolgsbefund noch nicht erhoben. Kein
Credential geändert, kein Reboot oder Regressionlauf gestartet.

### I2-A – Nutzerbestätigung Credential-Änderung

Nutzer meldet: sicherer Gerätesperrcode geändert, Gerät mit geändertem
Credential entsperrt. Kein Credentialwert erhoben oder dokumentiert.
Die neue B1.6-Initialisierung, deren Restart-Nachweis und die positiven
B1.5-/B1.6-/B3-Vorprüfungen vor Änderung sind im aktuellen Gespräch noch
nicht bestätigt; sie werden nicht nachträglich als bestanden behauptet.
Kein Reboot, keine Alias-Probe oder Zugriffstests nach Änderung durch Codex
in diesem Schritt ausgeführt. Keine Initialisierung/Reparatur.

LAST SAFE CHECKPOINT: Credential-Änderung und normales Entsperren vom Nutzer
bestätigt; wartet auf Klärung der bereits vor Änderung ausgeführten
Baseline-Schritte. I2-Fortsetzung vorläufig angehalten, kein Test läuft.
Kein Commit und kein Push.

## I2 BASELINE BEFORE CREDENTIAL CHANGE

Nachträglich vom Nutzer ausdrücklich bestätigt, zeitlich VOR der Änderung:
Die freigegebene einmalige neue B1.6-Initialisierung und der echte Restart
waren erfolgreich. Anschließend alle drei positiven Vorprüfungen bestanden.

| Bestand | Session | Test-ID | Keystore-Autorisierung | Konkrete Schlüsseloperation |
| --- | --- | --- | --- | --- |
| B1.6 | READY | B16-RESTART-SUCCEEDED | SUCCEEDED (nur Operationsbeobachtung) | SUCCEEDED |
| B1.5 | READY | B15-UNWRAP-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B3 | READY | B3-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |

Erst danach DEVICE_CREDENTIAL manuell geändert und Gerät mit geändertem
Credential entsperrt. Kein Credentialwert dokumentiert. Die vorherige
Unbestätigt-Markierung bleibt als Verlauf erhalten und ist hiermit geklärt.
Eine Alias-Probe und p16-Digestaufnahme unmittelbar vor der Änderung wurden
nicht ausgeführt; sie werden nicht nachträglich als Beobachtung behauptet.

### I2-A – read-only Vorprüfung nach Änderung, vor Reboot

Android Window Policy: secure=true, User 0. Vorhandene read-only Probe:
B15 PRESENT, B16 PRESENT, B3 PRESENT. Keine neue Initialisierung/Reparatur.

p15 und b3-v1: Dateistruktur, Größen, mtimes und Dateidigests identisch zur
read-only Aufnahme vor der neuen B1.6-Initialisierung/Credential-Änderung.
p16 jetzt vorhanden mit Verzeichnis k; Dateien 0 (1 Byte), k/0 (1 Byte),
k/1 (256 Byte), k/2 (294 Byte), d (12288 Byte), 1 (36 Byte).
Dies entspricht strukturell dem erwarteten vollständigen B1.6-Inventar;
kryptografische DB-/Unwrap-Prüfung folgt erst im manuellen Zugriffstest.
Mangels p16-Digestaufnahme vor Credential-Änderung ist dessen Bytegleichheit
über die Credential-Änderung NICHT nachgewiesen. Aktuelle Digests wurden
rein lesend als Vergleichsbasis für die folgenden Zugriffstests erfasst.
Lokale ignorierte Evidenz: .prototype-cache/i2-a-before-access-inventory.txt.

LAST SAFE CHECKPOINT: Baseline vom Nutzer bestätigt; nach Credential-Änderung
secure=true, alle drei Aliase PRESENT, p15/b3 unverändert und p16 vorhanden.
Wartet auf jeweils genau einen manuellen bestehenden B1.5-/B1.6-/B3-Zugriff.
Noch kein Zugriffsergebnis nach Änderung erhoben; kein Reboot gestartet.
Kein Commit und kein Push.

### I2-A – positive manuelle Zugriffstests nach Änderung, vor Reboot

Vom Nutzer vollständig bestätigt; jeweils bestehender Bestand, sichtbarer
DEVICE_CREDENTIAL-Dialog und erfolgreiche manuelle Authentifizierung:

| Bestand | Dialog | Session | Test-ID | Keystore-Autorisierung | Schlüsseloperation |
| --- | --- | --- | --- | --- | --- |
| B1.5 | Ja | READY | B15-UNWRAP-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B1.6 | Ja | READY | B16-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B3 | Ja | READY | B3-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |

**PASS im getesteten Galaxy/API33-Umfang vor Reboot.** Keine Fehlerdiagnose
angezeigt; daraus keine allgemeine Aussage über Credential-Änderungen auf
anderen Plattformen ableiten. Keine Initialisierung/Reparatur oder
Aliasänderung durch Codex. Credential-Entfernung weiterhin nicht begonnen.

LAST SAFE CHECKPOINT: I2-A vor Reboot positiv bestätigt und dokumentiert.
Geordneter adb reboot als nächster Schritt freigegeben; danach manueller
Nutzer-Unlock, read-only Vorprüfung und jeweils ein bestehender Zugriff.
Kein Commit und kein Push.

Lesender Vergleich nach den drei Zugriffstests: p15/p16/b3-v1-Dateitypen,
Größen, mtimes und Dateidigests identisch zur Aufnahme vor den Tests.
Damit keine beobachtete Bestandsmutation durch diese Zugriffstests.
Lokale ignorierte Reboot-Vergleichsbasis:
.prototype-cache/i2-a-before-reboot-inventory.txt.

LAST SAFE CHECKPOINT: I2-A-Zugriffe PASS, alle drei Dateiinventare unverändert.
Reboot wird jetzt ausgelöst; manueller Unlock und Nachprüfungen ausstehend.

adb reboot erfolgreich ausgelöst (OS-gesteuerter Neustart, kein Power-loss).
Keine Credential-Entfernung, Initialisierung, Reparatur oder Aliasänderung.

LAST SAFE CHECKPOINT: I2-A vor Reboot vollständig PASS; geordneter Reboot
befohlen. Wartet auf manuellen Geräte-Unlock und ADB-Rückkehr durch Nutzer.
Danach ausschließlich read-only secure/Alias/Dateiinventar, anschließend
je ein bestehender B1.5-/B1.6-/B3-Zugriff. Kein Test läuft.
Kein Commit und kein Push.

### I2-A – read-only Vorprüfung nach geordnetem Reboot

Nutzer bestätigt normales Entsperren und Wiederanschluss. Genau ein Gerät
verbunden: SM-M526BR/API33. Android Window Policy secure=true.
Alias-Probe: B15 PRESENT, B16 PRESENT, B3 PRESENT.
p15/p16/b3-v1-Dateitypen, Größen, mtimes und Dateidigests identisch zur
Aufnahme unmittelbar vor Reboot. Keine Initialisierung/Reparatur/Aliasänderung.
Lokale ignorierte Evidenz: .prototype-cache/i2-a-after-reboot-inventory.txt.

LAST SAFE CHECKPOINT: I2-A-Reboot/read-only Nachprüfung bestanden; alle drei
Aliase vorhanden, Bestandsinventare unverändert. Wartet auf je einen manuellen
bestehenden B1.5-/B1.6-/B3-Zugriff nach Reboot. Keine Credential-Entfernung,
kein Zugriffsergebnis nach Reboot behauptet. Kein Commit und kein Push.

B1.5 post-reboot: **AUTH PASS / SPECIFIC UNWRAP UNCONFIRMED / A-READY observed**.

### I2-A – vollständiger Abschluss einschließlich Reboot

**I2-A PASS IM GETESTETEN GALAXY/API33-UMFANG.** Nutzer bestätigt nach
geordnetem Reboot und erneutem explizitem DEVICE_CREDENTIAL-Zugriff:

| Bestand | Session | Variante soweit bestätigt | Test-ID | Keystore-Autorisierung | Schlüsseloperation |
| --- | --- | --- | --- | --- | --- |
| B1.5 | READY | STORAGE_UNWRAP | B15-UNWRAP-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B1.6 | READY | Nicht separat berichtet | B16-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |
| B3 | READY | Nicht separat berichtet | B3-RESTART-SUCCEEDED | SUCCEEDED | SUCCEEDED |

Der zuvor unbestätigte spezifische B1.5-Unwrap ist damit bestätigt;
A-READY bleibt als vorherige Zwischenbeobachtung dokumentiert.
Credential-Änderung wurde vor und nach Reboot im getesteten Umfang sicher
behandelt. Read-only Nachprüfung vor den Post-Reboot-Zugriffen bestätigte
secure=true, alle drei Aliase PRESENT und unveränderte Dateiinventare samt
Digests gegenüber unmittelbar vor Reboot. Ein zusätzlicher Digestvergleich
nach diesen manuellen Zugriffen wurde hier nicht durchgeführt.
Keine allgemeine Samsung-/Android-Garantie, kein Nachweis zur Credential-
Entfernung oder zu permanent invalidierten Schlüsseln aus I2-A ableiten.

### I2-B – vorbereitet, wartet auf manuelle Credential-Entfernung

Nutzer soll den sicheren Gerätesperrcode ausschließlich manuell über
Android/Samsung Settings vollständig entfernen, sofern das Gerät dies zulässt.
Keine Credentialwerte im Chat/Terminal/Dokument erfassen. Keine Geräteaktion
per ADB in diesem Vorbereitungsschritt, keine Initialisierung, Aliasänderung
oder App-Datenänderung.

Nach Bestätigung zuerst ausschließlich read-only: secure-Status, drei
Aliaszustände und p15/p16/b3-v1-Dateiinventar. Erst danach je ein bestehender
Zugriffspfad. Bei secure=false ohne legitimes DEVICE_CREDENTIAL darf kein
Bestand fachlich READY werden: READY ohne legitime Auth ist FAIL;
BLOCKED/nicht authentifizierbar ist ein zulässiger sicherer Ausgang.
Kein Reboot vor vollständiger Dokumentation des Zustands direkt nach Entfernung.

LAST SAFE CHECKPOINT: I2-A einschließlich Reboot und spezifischem B1.5-Unwrap
vollständig PASS. I2-B vorbereitet, Credential-Entfernung noch nicht bestätigt.
Kein Test läuft; wartet auf Nutzerbestätigung „Credential entfernt“.
Kein Commit und kein Push.

### I2-B – unmittelbar nach manueller Credential-Entfernung, vor Zugriff/Reboot

Nutzer bestätigt „Credential entfernt“. Ausschließlich read-only geprüft:
ein Galaxy SM-M526BR/API33 verbunden; Android Window Policy **secure=false**.
Vorhandene Alias-Probe unter Haupt-App-UID meldet:

| Alias | Zustand nach Entfernung |
| --- | --- |
| B15 / pag.prototype.b15.storage.v1 | ABSENT |
| B16 / pag.prototype.b16.storage.v1 | ABSENT |
| B3 / pag.prototype.b3.file.v1 | ABSENT |

p15, p16 und b3-v1 bleiben vorhanden. Dateitypen, Größen, mtimes und
Dateidigests jeweils identisch zur I2-A-Aufnahme nach Reboot.
Lokale ignorierte Evidenz: .prototype-cache/i2-b-before-access-inventory.txt.
Keine Keywerte oder Ciphertextinhalte ausgegeben, keine getKey-/Cipher-
Operation ausgeführt. Kein PaG-Authentifizierungsversuch vor dieser Inventur.

Beobachtet: alle drei zuvor vorhandenen Aliase sind nach manueller Entfernung
nicht mehr vorhanden, während die geschützten Dateibestände erhalten bleiben.
Keine Aliaslöschung durch Codex oder Probe. Keine konkrete Keystore-Exception
beobachtet; containsAlias=false beweist nicht eine bestimmte Exceptionklasse
oder den internen Mechanismus der Plattformentfernung. Kein Ersatzkey oder
Ersatzbestand erzeugt. Zugriffsergebnisse noch offen; I2-B noch nicht PASS.

LAST SAFE CHECKPOINT: I2-B-read-only Inventur nach Credential-Entfernung
abgeschlossen: secure=false, B15/B16/B3 ABSENT, alle Dateiinventare unverändert.
Wartet auf je einen bestehenden B1.5-/B1.6-/B3-Zugriff; READY ohne legitime
DEVICE_CREDENTIAL-Authentifizierung wäre FAIL. Keine Initialisierung/Reparatur,
kein Reboot, kein Test läuft. Kein Commit und kein Push.

### I2-B – manuelle Zugriffstests ohne Credential, vor Reboot

Vom Nutzer bestätigt, jeweils bestehender Zugriffspfad:

| Bestand | Dialog | Session | Test-ID | Keystore-Autorisierung | Schlüsseloperation |
| --- | --- | --- | --- | --- | --- |
| B1.5 | Nein | BLOCKED | A-AUTH-UNAVAILABLE | NOT_TESTED | NOT_TESTED |
| B1.6 | Nicht sicher notiert | BLOCKED | B16-BOOTSTRAP-INVENTORY | NOT_TESTED | NOT_TESTED |
| B3 | Nein | BLOCKED | B3-BOOTSTRAP-INVENTORY (manuelle Ablesung) | NOT_TESTED | NOT_TESTED |

**PASS für die Zugriffsverweigerung vor Reboot im getesteten Umfang:**
Keiner der drei Bestände wurde ohne legitimes DEVICE_CREDENTIAL READY.
Die IDs sind feste Prototypdiagnosen, keine beobachteten konkreten
Keystore-Exceptionklassen. Insbesondere NOT_TESTED nicht als fehlgeschlagene
private Schlüsseloperation oder permanent invalidierten vorhandenen Key
umdeuten. Aliase waren in der vorherigen read-only Probe ABSENT.
Keine Initialisierung, Ersatzkeys oder Reparatur durch Codex.

LAST SAFE CHECKPOINT: I2-B vor Reboot vollständig geprüft, alle drei Pfade
BLOCKED. Geordneter Reboot freigegeben; kein neues DEVICE_CREDENTIAL einrichten.
Nach Rückkehr zuerst read-only secure/Aliase/Dateiinventar, danach je ein
bestehender Zugriff. Kein Commit und kein Push.

Lesender Dateivergleich nach den blockierten Zugriffen: p15/p16/b3-v1-
Dateitypen, Größen, mtimes und Dateidigests unverändert gegenüber vor Zugriff.
Reboot-Vergleichsbasis: .prototype-cache/i2-b-before-reboot-inventory.txt
(lokal ignoriert). Kein Ersatzbestand beobachtet.

LAST SAFE CHECKPOINT: I2-B vor Reboot PASS, Inventare unverändert.
Geordneter Reboot wird jetzt ausgelöst; Nachprüfungen ausstehend.

adb reboot erfolgreich ausgelöst: geordneter OS-Neustart, kein Power-loss.
Noch kein neues DEVICE_CREDENTIAL; keine Initialisierung/Reparatur/Aliasänderung.

LAST SAFE CHECKPOINT: I2-B vor Reboot vollständig geprüft und dokumentiert,
alle Zugriffe BLOCKED, Dateiinventare unverändert. Reboot ausgelöst; wartet
auf Nutzerbestätigung der Geräterückkehr/ADB-Verbindung. Danach zuerst
read-only secure/Alias/Dateidigests, anschließend je ein bestehender Zugriff.
Kein Test läuft. Kein Commit und kein Push.

### I2-B – read-only Vorprüfung nach Reboot ohne Credential

Nutzer bestätigt Geräterückkehr und Zugänglichkeit. Genau ein Galaxy
SM-M526BR/API33 verbunden. Android Window Policy **secure=false**.
Read-only Haupt-App-Alias-Probe: **B15 ABSENT, B16 ABSENT, B3 ABSENT**.
p15/p16/b3-v1 weiterhin vorhanden; Dateitypen, Größen, mtimes und
Dateidigests jeweils identisch zur Aufnahme unmittelbar vor I2-B-Reboot.
Lokale ignorierte Evidenz: .prototype-cache/i2-b-after-reboot-inventory.txt.
Keine Authentifizierung, Initialisierung, Reparatur oder Aliasänderung.
Noch kein Zugriffsergebnis nach diesem Reboot erhoben.

LAST SAFE CHECKPOINT: I2-B nach Reboot read-only geprüft: secure=false,
alle drei Aliase ABSENT, geschützte Dateibestände unverändert erhalten.
Wartet auf je einen bestehenden B1.5-/B1.6-/B3-Zugriff. READY ohne legitime
DEVICE_CREDENTIAL-Authentifizierung wäre FAIL. Noch kein neues Credential
und kein weiterer Reboot. Kein Commit und kein Push.

### I2-B – vollständiger Abschluss einschließlich Reboot

**I2-B PASS IM GETESTETEN GALAXY/API33-UMFANG.** Nutzer bestätigt nach
geordnetem Reboot ohne sicheres DEVICE_CREDENTIAL jeweils den alten Bestand:

| Bestand | Dialog | Session | Test-ID | Keystore-Autorisierung | Schlüsseloperation |
| --- | --- | --- | --- | --- | --- |
| B1.5 | Nein | BLOCKED | A-AUTH-UNAVAILABLE | NOT_TESTED | NOT_TESTED |
| B1.6 | Nein | BLOCKED | B16-BOOTSTRAP-INVENTORY | NOT_TESTED | NOT_TESTED |
| B3 | Nein | BLOCKED | B3-BOOTSTRAP-INVENTORY | NOT_TESTED | NOT_TESTED |

Kein Bestand wurde ohne legitimes DEVICE_CREDENTIAL fachlich READY.
Vor und nach Reboot secure=false und B15/B16/B3 ABSENT beobachtet;
Dateiinventare samt Digests vor den Post-Reboot-Zugriffen unverändert.
Ein zusätzlicher Digestvergleich nach diesen manuellen Zugriffen wurde in
diesem Dokumentationsschritt nicht ausgeführt. Keine konkrete private
Keystore-Exception beobachtet: NOT_TESTED und Inventar-/Unavailable-IDs
nicht als ausgeführte Schlüsseloperation oder bestimmte Invalidation-
Exception umdeuten. Kein Ersatzbestand oder Ersatzkey durch Codex erzeugt.

### I2-C – vorbereitet, wartet auf manuelle sichere Credential-Einrichtung

Nutzer soll ausschließlich manuell über Android/Samsung Settings wieder
einen sicheren Gerätesperrcode einrichten. Keine Credentialwerte im Chat,
Terminal oder Dokument. Keine Initialisierung, Alias-/Dateiänderung oder
Geräteaktion durch Codex in diesem Vorbereitungsschritt.

Nach Nutzerbestätigung zuerst ausschließlich read-only: secure=true,
B15/B16/B3-Aliaszustände und p15/p16/b3-v1-Inventar samt Digests. Danach
je genau ein alter bestehender Zugriffspfad. Keine Wiederherstellung alter
Aliase voraussetzen; ABSENT/unbrauchbar/BLOCKED oder klarer fail-closed-
Fehler sind zulässig. Keine automatische Ersatzinitialisierung oder
Ersetzung des alten verschlüsselten Bestands. Nach dokumentierten Zugriffen
geordneter Reboot und letzte read-only-/Zugriffswiederholung. Keine neuen
Ersatzbestände oder Gesamtregression vor Abschlussbericht zu I2-C.

LAST SAFE CHECKPOINT: I2-B einschließlich Reboot vollständig PASS,
alle Post-Reboot-Zugriffe BLOCKED ohne Dialog. I2-C vorbereitet; wartet auf
Nutzerbestätigung der manuellen Einrichtung eines sicheren DEVICE_CREDENTIAL.
Kein Test läuft, keine neue Initialisierung. Kein Commit und kein Push.

### I2-C – read-only nach erneuter sicherer Credential-Einrichtung, vor Reboot

Nutzer bestätigt neues sicheres DEVICE_CREDENTIAL manuell eingerichtet.
Kein Credentialwert erfasst. Genau ein Galaxy SM-M526BR/API33 verbunden.
Android Window Policy **secure=true**. Vorhandene read-only Haupt-App-Probe:
**B15 ABSENT, B16 ABSENT, B3 ABSENT**. Alte Aliase sind damit nach erneuter
Credential-Einrichtung weiterhin nicht vorhanden; keine Wiederherstellung
oder Neubildung durch PaG/Probe beobachtet.

p15/p16/b3-v1 weiterhin vorhanden; Dateitypen, Größen, mtimes und Digests
jeweils identisch zur I2-B-Aufnahme nach Reboot. Lokale ignorierte Evidenz:
.prototype-cache/i2-c-before-access-inventory.txt. Keine getKey-/Cipher-
Operation, Authentifizierung, Initialisierung, Alias-/Dateiänderung oder
Reparatur durch Codex. Keine konkrete Keystore-Exception beobachtet.

LAST SAFE CHECKPOINT: I2-C vor Reboot read-only geprüft: secure=true,
alle drei alten Aliase weiterhin ABSENT, Dateibestände unverändert.
Wartet auf je einen alten bestehenden B1.5-/B1.6-/B3-Zugriff; keine
Initialisierung/Ersatzbestände. Reboot erst nach dokumentierten Zugriffen.
Kein Test läuft. Kein Commit und kein Push.

### I2-C – manuelle Zugriffe auf alte Bestände, vor Reboot

Nutzer bestätigt bei erneut eingerichtetem sicheren DEVICE_CREDENTIAL:
secure=true, alte B15/B16/B3-Aliase ABSENT, p15/p16/b3-v1 unverändert.
Jeweils genau ein alter bestehender Zugriffspfad getestet:

| Bestand | Dialog | Session | Test-ID | Keystore-Autorisierung | Schlüsseloperation |
| --- | --- | --- | --- | --- | --- |
| B1.5 | Nein | BLOCKED | B15-BOOTSTRAP-INVENTORY | NOT_TESTED | NOT_TESTED |
| B1.6 | Nein | BLOCKED | B16-BOOTSTRAP-INVENTORY | NOT_TESTED | NOT_TESTED |
| B3 | Nein | BLOCKED | B3-BOOTSTRAP-INVENTORY | NOT_TESTED | NOT_TESTED |

**PASS für fail-closed Zugriff vor Reboot im getesteten Umfang.** Alte
Aliase wurden durch Credential-Einrichtung nicht wieder verfügbar;
kein READY, kein Dialog, keine konkrete Schlüsseloperation beobachtet.
Keine konkrete Keystore-Exceptionklasse aus Inventar-IDs ableiten.
Keine Initialisierung, Ersatzkeys oder Reparatur durch Codex.

LAST SAFE CHECKPOINT: I2-C vor Reboot vollständig geprüft: alle alten Pfade
BLOCKED. Geordneter Reboot freigegeben; danach Nutzer-Unlock und zuerst
read-only Inventur, anschließend je ein alter bestehender Zugriff.
Noch keine neuen Testbestände oder Gesamtregression. Kein Commit und kein Push.

Lesender Vergleich nach den I2-C-Zugriffen bestätigt p15/p16/b3-v1-
Dateitypen, Größen, mtimes und Dateidigests unverändert gegenüber vor Zugriff.
Lokale ignorierte Reboot-Vergleichsbasis:
.prototype-cache/i2-c-before-reboot-inventory.txt.

LAST SAFE CHECKPOINT: I2-C vor Reboot PASS, alle Inventare unverändert.
Geordneter Reboot wird jetzt ausgelöst; Nachprüfungen ausstehend.

adb reboot erfolgreich ausgelöst (geordneter OS-Neustart, kein Power-loss).
Keine neue Initialisierung, Reparatur, Aliasänderung oder Gesamtregression.

LAST SAFE CHECKPOINT: I2-C vor Reboot vollständig PASS und dokumentiert,
alle alten Zugriffe BLOCKED und Dateiinventare unverändert. Reboot ausgelöst;
wartet auf manuellen Nutzer-Unlock/ADB-Rückkehr. Danach zuerst read-only
secure/Alias/Dateidigests und je ein alter Zugriff; bei READY sofort stoppen.
Kein Test läuft. Kein Commit und kein Push.

### I2-C – read-only Vorprüfung nach geordnetem Reboot

Nutzer bestätigt normales Entsperren und Wiederanschluss. Genau ein Galaxy
SM-M526BR/API33 verbunden. Android Window Policy **secure=true**.
Read-only Haupt-App-Alias-Probe: **B15 ABSENT, B16 ABSENT, B3 ABSENT**.
p15/p16/b3-v1 weiterhin vorhanden; Dateitypen, Größen, mtimes und
Dateidigests identisch zur Aufnahme unmittelbar vor I2-C-Reboot.
Lokale ignorierte Evidenz: .prototype-cache/i2-c-after-reboot-inventory.txt.
Keine Authentifizierung, Initialisierung, Reparatur oder Aliasänderung.
Zugriffsergebnisse nach diesem Reboot noch nicht erhoben.

LAST SAFE CHECKPOINT: I2-C nach Reboot read-only geprüft: secure=true,
alle drei alten Aliase ABSENT, Dateibestände unverändert. Wartet auf je einen
alten bestehenden B1.5-/B1.6-/B3-Zugriff; bei READY sofort stoppen.
Keine neuen Testbestände und keine Gesamtregression. Kein Test läuft.
Kein Commit und kein Push.

## Verbindlicher Abschlussstand I2

**I2 BESTANDEN IM GETESTETEN GALAXY/API33-UMFANG.** Dieser Abschlussstand
fasst die chronologischen Einträge zusammen. Post-Reboot-Ergebnisse von I2-C
wurden vom Nutzer mit dem Finalisierungsauftrag ausdrücklich bestätigt.
Keine neue Geräteaktion, Ersatzinitialisierung oder manuelle Regression zur
Wiederherstellung der alten Aliase erforderlich oder durchgeführt.

### I2 BASELINE

Vor Credential-Änderung secure=true. Nutzer bestätigt die bewusst freigegebene
CASE-2-Neuanlage von B1.6 und erfolgreichen echten Restart. Bestehende positive
Vorprüfungen jeweils READY und Keystore-Autorisierung/Schlüsseloperation SUCCEEDED:
B1.5 B15-UNWRAP-SUCCEEDED, B1.6 B16-RESTART-SUCCEEDED,
B3 B3-RESTART-SUCCEEDED. B1.6-Autorisierungsanzeige als Operationsbeobachtung.

Der frühere ungeklärte Verlust von p16/B16-Alias bleibt **CAUSE UNKNOWN**.
Nicht nachträglich mit I2 oder der späteren Credential-Entfernung erklären.
Die CASE-2-Neuanlage war ausdrücklich freigegeben, keine automatische Reparatur.

### I2-A: Credential geändert

Nach manueller Änderung des bestehenden sicheren Credentials:
B15/B16/B3 PRESENT; alle drei positiven bestehenden Pfade erfolgreich.
Nach geordnetem Reboot secure=true, Aliase weiterhin PRESENT,
Dateiinventare/Digests unverändert gegenüber unmittelbar vor Reboot.

| Bestand | Vor Reboot | Nach Reboot | Auth / Keyoperation jeweils |
| --- | --- | --- | --- |
| B1.5 | READY / B15-UNWRAP-SUCCEEDED | READY / B15-UNWRAP-SUCCEEDED (STORAGE_UNWRAP) | SUCCEEDED / SUCCEEDED |
| B1.6 | READY / B16-RESTART-SUCCEEDED | READY / B16-RESTART-SUCCEEDED | SUCCEEDED / SUCCEEDED |
| B3 | READY / B3-RESTART-SUCCEEDED | READY / B3-RESTART-SUCCEEDED | SUCCEEDED / SUCCEEDED |

Explizite DEVICE_CREDENTIAL-Authentifizierung erforderlich und bestätigt.
Credential-Änderung allein erhielt die getesteten Keys auf diesem Gerät.
Keine allgemeine Android-/Samsung-Garantie. Für das neu angelegte p16 fehlt
eine Digestaufnahme unmittelbar vor Credential-Änderung; die Bytegleichheit
über diese Änderung wird deshalb nicht behauptet. Die späteren Reboot- und
Zugriffsvergleiche sind separat belegt.

### I2-B: Credential entfernt

Direkt nach vollständiger manueller Entfernung und nach geordnetem Reboot:
secure=false; **B15 ABSENT, B16 ABSENT, B3 ABSENT**.
p15/p16/b3-v1 bleiben vorhanden; gemessene Dateiinventare/Digests unverändert.

| Bestand | Dialog vor / nach Reboot | Session jeweils | Test-ID jeweils | Auth / Keyoperation jeweils |
| --- | --- | --- | --- | --- |
| B1.5 | Nein / Nein | BLOCKED | A-AUTH-UNAVAILABLE | NOT_TESTED / NOT_TESTED |
| B1.6 | Nicht sicher notiert / Nein | BLOCKED | B16-BOOTSTRAP-INVENTORY | NOT_TESTED / NOT_TESTED |
| B3 | Nein / Nein | BLOCKED | B3-BOOTSTRAP-INVENTORY | NOT_TESTED / NOT_TESTED |

B3-ID vor Reboot stammt aus manueller Ablesung. Keine Freigabe ohne legitimes
DEVICE_CREDENTIAL; kein READY. B1.6-Dialog vor Reboot bleibt unbestätigt.

### I2-C: Neues sicheres Credential eingerichtet

Nach manueller Einrichtung und nach geordnetem Reboot: **secure=true**;
alte **B15/B16/B3 weiterhin ABSENT**; alte p15/p16/b3-v1-Dateibestände und
gemessene Digests unverändert. Altes Schlüsselmaterial wurde nicht wieder
verfügbar; keine neuen Keys erzeugt.

| Bestand | Dialog vor / nach Reboot | Session jeweils | Test-ID jeweils | Auth / Keyoperation jeweils |
| --- | --- | --- | --- | --- |
| B1.5 | Nein / Nein | BLOCKED | B15-BOOTSTRAP-INVENTORY | NOT_TESTED / NOT_TESTED |
| B1.6 | Nein / Nein | BLOCKED | B16-BOOTSTRAP-INVENTORY | NOT_TESTED / NOT_TESTED |
| B3 | Nein / Nein | BLOCKED | B3-BOOTSTRAP-INVENTORY | NOT_TESTED / NOT_TESTED |

Die letzte read-only Aufnahme liegt vor den finalen manuellen Zugriffen;
ein weiterer Digestvergleich danach wurde nicht ausgeführt. Zugriffsergebnisse
nach Reboot sind Nutzerbeobachtungen, nicht automatisch erzeugte JUnit-Ergebnisse.

### Sicherheitsbewertung und Grenzen

- Credential-Entfernung führte auf diesem Galaxy zum beobachteten Verlust/
  Entfernen der drei zuvor vorhandenen auth-bound Keystore-Aliase.
- Verschlüsselte Storage-Artefakte blieben erhalten; PaG behandelte den
  Zustand fail closed, ohne Ersatzkeys, automatische Namespace-Neuanlage
  oder Akzeptanz alter Daten unter neuen Schlüsseln.
- Ein später neu eingerichtetes Credential stellte die alten Aliase nicht
  wieder her. Alte Bestände bleiben unangetastet und nicht entschlüsselbar
  über die getesteten Pfade.
- Permanenter Datenverlust durch verlorenen Keystore-Key ist nach dem
  bisherigen Sicherheitsmodell zulässig und sicherer als automatische
  Ersatzinitialisierung; dies ist keine Backup-/Recovery-Funktion.
- Keine konkrete Keystore-Exceptionklasse beobachtet. containsAlias=false,
  Inventar-IDs und NOT_TESTED nicht als KeyPermanentlyInvalidatedException
  oder ausgeführte private Schlüsseloperation darstellen.
- Keine Garantie, dass Android oder Samsung allgemein bei Credential-
  Entfernung Keys löschen; nur Geräte-/API33-Befund. Keine Zusicherung
  physischer Keyüberschreibung, RAM-/Flash-Restebeseitigung oder Schutz
  gegen kompromittiertes OS/Kernel. Geordnete Reboots sind kein Power-loss.

### Test-only AliasInventoryProbe – Security-Review

Zwei neue Dateien ausschließlich unter app/src/androidTest. Keine
Produktkomponente, Produktpermission, Dependency, Netzwerk oder Telemetrie.
Probe verwendet nur AndroidKeyStore getInstance/load/containsAlias;
keine Keyerzeugung/-löschung, getKey/private-key-Nutzung, Cipher-Operation,
Credential-Anforderung, Dateimutation oder App-State-Initialisierung.
Erfolgsoutput ausschließlich B15/B16/B3 mit PRESENT/ABSENT; keine anderen
Aliasnamen, Key-/Credentialwerte, Exceptiontexte oder Stacktraces.
Fehler beendet Probe erfolglos ohne Aliasbehauptung, niemals als Erfolg.

Testmanifest enthält ausschließlich die notwendige Test-Instrumentation,
Test-Application und uses-sdk; keine Permission oder Activity/Service/
Receiver/Provider. Produktmanifest bleibt unverändert. Keine beabsichtigte
Key-/Credentialpersistenz oder automatische Reparatur hinzugefügt.

LAST SAFE CHECKPOINT: I2-A/B/C einschließlich Reboots vollständig abgeschlossen,
I2 BESTANDEN IM GETESTETEN GALAXY/API33-UMFANG. secure=true, alte Aliase ABSENT,
alte geschützte Dateibestände erhalten; keine weiteren Geräteaktionen,
Ersatzbestände, I3 oder Gesamtinstrumentation starten. Host-Abschlussprüfungen
laufen noch; deren Ergebnis separat nachtragen. Kein Commit und kein Push.

### I2 – technische Abschlussprüfungen

Frische bestehende Unit-Tests: **288 Tests, 0 Failures, 0 Errors, 0 Skips**;
`./gradlew test --rerun-tasks` erfolgreich, 63 von 63 Tasks ausgeführt.
Separat erfolgreich: `:app:assembleDebug`, `:app:assembleDebugAndroidTest`,
`:app:lintDebug`, `verifyArchitecture`, `:app:verifyPrototypeManifest`.
Keine Geräte-Instrumentation oder neue manuelle Baseline zur Grünmachung
 ausgeführt. Keine alten Aliase/Namespaces ersetzt oder neu initialisiert.

Der zunächst kombinierte Lauf mit --rerun-tasks scheiterte beim Packaging
 des App-Test-APK: Gradle meldete die fehlende deklarierte Abhängigkeit von
`:app:packageDebugAndroidTest` auf `:app:preparePrototypeDebugKeystore`.
Dies ist ein offener Build-Taskgraph-Befund, kein fehlgeschlagener I2-
Geräte-/Unit-Test. Keine Build-Konfiguration geändert; getrennte angeforderte
Prüfungen erfolgreich. Der Testcode wurde im kombinierten Lauf frisch
kompiliert; das Test-APK wurde im separaten Lauf erfolgreich paketiert.
Ein kombinierter frischer Lauf bleibt bis zu einer separat freigegebenen
Taskgraph-Korrektur nicht als bestanden zu deklarieren.

Produktmanifest-/Dependency-/Build-Konfigurationsquellen gegenüber HEAD
unverändert. Gemergtes Produktmanifest weiterhin ausschließlich die bestehenden
Permissions USE_BIOMETRIC und DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION;
kein INTERNET/Storage/Camera/Notification-Zugriff oder neue Komponente.
Manifest-Policyprüfung erfolgreich. Gemergtes Testmanifest: uses-sdk,
AliasInventoryProbe-Instrumentation und komponentenlose Test-Application,
keine Permission. Keine Produkt-Key-/Credentialpersistenz, automatische
Reparatur oder Aliaslöschung hinzugefügt. Security-Review der zwei Testdateien
und Dokumentationsänderungen abgeschlossen; Whitespaceprüfung bestanden.
Lokale ignorierte Logs: i2-final-checks.log (kombinierter Fehler),
i2-final-unit.log, i2-final-androidtest.log, i2-final-build-lint.log.

LAST SAFE CHECKPOINT: **I2 vollständig abgeschlossen, BESTANDEN IM GETESTETEN
GALAXY/API33-UMFANG.** Alte Aliase ABSENT, secure=true, alte verschlüsselte
Dateibestände erhalten. 288 frische Unit-Tests und getrennte Build-/Lint-/
Architektur-/Manifest-/Test-APK-Prüfungen erfolgreich; kombinierter Gradle-
Taskgraph-Befund offen. Kein Test läuft. Keine neuen Bestände, Aliase,
Geräteaktionen, Gesamtinstrumentation oder I3. Kein Commit und kein Push.

### I2 – Klassifikation des kombinierten Gradle-Befunds

**A: PRE-EXISTING BUILD-WIRING ISSUE.** Identischer kombinierter Aufruf
`test --rerun-tasks :app:assembleDebug :app:assembleDebugAndroidTest
:app:lintDebug verifyArchitecture :app:verifyPrototypeManifest` im separaten
sauberen HEAD-Worktree (`3fbccde`, ohne I2-Testdateien) reproduziert denselben
Fehler. Worktree unter .prototype-cache/i2-head-build-check; ausschließlich
ignorierte lokale SDK-Konfiguration ergänzt, versionierte Quellen unverändert.
Lokale ignorierte Evidenz: .prototype-cache/i2-head-combined-checks.log.

Gradle 9.6.1 Task-Validation: „Property has implicit dependency“.
Consumer/fehlgeschlagene Task: :app:packageDebugAndroidTest (PackageApplication).
Producer: :app:preparePrototypeDebugKeystore. Betroffen: das projektspezifische
.prototype-cache/debug.keystore (im Vergleichslauf im Worktree).
Consumer verwendet die deklarierte Producer-Ausgabe ohne explizite oder
implizite Taskabhängigkeit. Die vorhandene Signing-Taskbindung adressiert
validateSigningDebug, nicht den AndroidTest-Packaging-Consumer.

Keine I2-induzierte Ursache und kein I2-Sicherheitsfehler. Alle angeforderten
Prüfungen waren in getrennten Aufrufen erfolgreich. Build-Konfiguration bleibt
unverändert; fehlende Taskbindung als separater späterer Build-Hygiene-Punkt
behandeln. I2 ist unter dieser Klassifikation commit-ready; kein Commit erfolgt.

LAST SAFE CHECKPOINT: I2 abgeschlossen und commit-ready; kombinierter
Gradle-Fehler auf sauberem HEAD reproduziert und als vorbestehend klassifiziert.
Keine Geräteaktion, Key-/Aliasänderung, Produktfunktion oder Buildänderung.
Kein Test läuft, I3 nicht begonnen. Kein Commit und kein Push.
