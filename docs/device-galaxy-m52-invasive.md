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
