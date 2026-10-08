# B4 – Import, Delete und lokaler Wiederanlauf

## Status und Forschungsfrage

**BESTANDEN IM GETESTETEN FAIRPHONE/API36-UMFANG.** B4 ist kein Backup-,
Recovery- oder Produktdaten-System. Untersucht werden ausschließlich lokale
synthetische Storage-Übergänge.

Verbindliche Abgrenzung: B4 verwendet ausschließlich explizit injizierte,
kurzlebige Test-Key-Consumer für getrennte DB- und File-Keys. Es führt keine
zwei DEVICE_CREDENTIAL-Dialoge als Produktvertrag ein. Die tatsächliche
Multi-Key-Authentifizierungs-/Lebensdauer-Orchestrierung bleibt der späteren
Gesamtintegration vorbehalten. Kein Zeitfenster-Fallback, keine gemeinsame
Verpackung und kein neuer Key-Lifecycle werden eingeführt.

B4 beweist **nicht erneut** den realen DEVICE_CREDENTIAL-/Keystore-Pfad.
Dieser wurde separat in B1.6 und B3 nachgewiesen. B4 untersucht den
Restart-/Reconciliation-Vertrag. Die bestehenden Schlüsselpfade bleiben
unverändert.

## Vorbereiteter technischer Aufbau

Alle neuen Komponenten liegen in `:data`. `B4RoomStore` verwendet Room über
den bestehenden kontrollierten B2-SQLCipher-Driver, einschließlich Rollback
auf derselben Connection bei widerrufener Commit-Zulassung. Es gibt keinen
Singleton, keine observable DAOs und keine destruktive Migration.

Die verschlüsselten Tabellen enthalten ausschließlich:

- Job: opaque Job-ID, Asset-ID, Stagingname, finaler Dateiname, erwartete Länge,
  Operation und Phase.
- Asset: opaque Asset-ID, finaler Dateiname, erwartete Länge und Hidden-Status.

Session-Generationen werden nicht als persistentes Zugriffsrecht gespeichert.
Eine Wiederholung benötigt immer einen neuen gültigen MAINTENANCE-Kontext.
Die Dateikryptografie wird unverändert durch `B3Streams` bereitgestellt.
Instrumentation-Fixtures verwenden ausschließlich eigene UUID-Testnamensräume
unter dem privaten `noBackupFilesDir` der Test-App. Darin liegen `q/d` für die
verschlüsselte DB und `f/s`, `f/f` für Staging/finale Ciphertexte.
B4StepStore öffnet für jeden strukturierten Phasenschritt eine eigene
kurzlebige Room-Instanz und schließt sie vor dem nächsten File-/DB-Schritt.
Driver und Tink-Primitive werden ebenfalls wieder geschlossen. Key-Consumer existieren nur
unter `androidTest`: zufällige getrennte 32-Byte-Keys werden im Testprozess
gehalten, je Verbrauch kopiert und die kontrollierte Kopie in finally
überschrieben. Auch die Test-Key-Owner werden am Testende überschrieben.
Es gibt keine persistente Klartext-Keydatei. Bestehende B1–B3-Namensräume
und Keystore-Aliase der Haupt-App bleiben unverändert.

## Importphasen

`IMPORT_RESERVED → STAGING_WRITING → STAGING_CLOSED → VALIDATED →
FILE_PUBLISHED → METADATA_COMMITTED → Job entfernen`

Die Jobreservierung erfolgt vor dem Schreiben. Die Quelle wird ausschließlich
im RAM synthetisch erzeugt. Auf Disk wird ausschließlich Tink-Ciphertext
geschrieben. Nach Streamabschluss wird die vollständige Datei erneut bis EOF
entschlüsselt und ihre Länge geprüft. Erst danach werden die Datei innerhalb
des privaten Filesystems verschoben und die Asset-Metadaten transaktional
angelegt. Es wird niemals über eine vorhandene Zieldatei geschrieben.

VALIDATING ist kein zusätzlicher persistenter Erfolgsstatus: Während der
Prüfung bleibt STAGING_CLOSED bestehen. Nach Wiederanlauf wird die vollständige
Prüfung wiederholt. Auch VALIDATED allein ersetzt keinen neuen kryptografischen
Nachweis für die nächste zulässige Operation.

Ein RESERVED-Job ohne Datei kann ausdrücklich ohne Publikation abgeschlossen
werden. Ein WRITING-Job mit vollständig verifizierbarem Ciphertext kann ohne
Neuschreiben fortgesetzt werden. Fehlender oder unvollständiger Ciphertext
bleibt blockiert. B4 beseitigt solche Dateien nicht automatisch.

## Deletephasen

`DELETE_RESERVED → METADATA_HIDDEN → FILE_DELETE_PENDING → FILE_REMOVED →
METADATA_REMOVED → Job entfernen`

Vor Reservierung wird die bekannte Datei vollständig validiert. Bereits ein
reservierter Delete-Job schließt sein Asset aus der technischen Sichtbarkeit
aus. Der Job bewahrt die notwendigen Zielreferenzen, bevor die Asset-Metadaten
entfernt werden. Nur das genau bezeichnete Ciphertextziel darf entfernt werden.

Eine fehlende Datei vor DELETE_PENDING ist unklar und blockiert. Nach der
persistent autorisierten DELETE_PENDING-Phase ist bereits erfolgtes unlink
idempotent behandelbar. Wiederauftauchen der Datei nach FILE_REMOVED blockiert.
Es gibt keine dauerhafte detaillierte Löschhistorie.

## Inventar und Wiederanlauf

Vor Mutation wird das gesamte Dateiinventar geprüft. Die isolierte Dateiwurzel
enthält genau `s` und `f`; Dateien darin besitzen ausschließlich opaque Namen.
Unbekannte Einträge, Symlinks, widersprüchliche Job-/Asset-Zuordnungen und
unerwartet fehlende Dateien blockieren. Fehlender DB-Zugriff ist niemals ein
Grund für Orphan-Cleanup.

Reconciliation läuft nur in MAINTENANCE. Sie erzeugt weder neue Asset-IDs noch
neue Keys oder Ersatz-Ciphertext. Nach Dateiveröffentlichung, Metadatencommit
oder unlink werden die bereits beobachtbaren Folgen mit der persistenten
Jobphase abgeglichen. Alle verbleibenden Assets werden bis EOF validiert.
Die Test-Fixture verwendet den bestehenden SessionCoordinator mit explizit
synthetischem Authentifizierungsadapter. Erst nach erfolgreicher Reconciliation
dürfte dessen Maintenance-Completion READY freigeben. Der vollständige positive
Room-/Tink-Import-/Delete-Fall ist auf dem Fairphone bestanden. READY wird
im positiven Test erst nach erfolgreicher Reconciliation freigegeben; bei
Maintenance-Ablauf bleibt die Session LOCKED.

## Session und Transaktionsgrenzen

Der zentrale StorageContext wird vor Operationen, Arbeitsschritten, Commit
und Ergebnisfreigabe erneut geprüft. READY-Kontexte dürfen keine
B4-Maintenance ausführen. Absolute Deadline und Generation gelten auch für
den Wiederanlauf; I/O verlängert sie nicht.

Room-Transaktionen umfassen ausschließlich strukturierte Änderungen.
DB-Commit, Filesystem-Move, Dateisynchronisation und Keystore sind keine
globale atomare Transaktion. Lange I/O hält den Session-Monitor nicht fest.
Bereits laufende native Arbeit und Stream-Cleanup können nach Widerruf noch
physisch enden, ohne eine neue Phase oder fachlichen Erfolg zu erlauben.

Der Schreibpfad ruft beim Schließen `FileDescriptor.sync()` auf. Dies ist
kein Nachweis gegen Power-loss; insbesondere Verzeichnis-Durability und
OEM-/Filesystem-Unterschiede sind noch nicht praktisch geprüft.

Löschen meint unlink und Entfernen strukturierter Referenzen. Sichere
physische Überschreibung von Flash-Zellen und vollständiges Löschen aller
JVM-/Tink-/nativen RAM-Kopien werden nicht zugesichert.

## Ausgeführte Fault-Injection

13 Importpunkte: vor/nach Jobreservierung, vor Staginganlage, während Schreiben,
nach Streamabschluss, vor/während/nach Validierung, vor/nach Dateiveröffentlichung,
vor/nach Metadatencommit sowie vor Jobabschluss.

9 Deletepunkte: vor/nach Jobreservierung, vor/nach Hide, vor/nach unlink,
vor/nach Metadatenentfernung sowie vor Jobabschluss.

Die vollständige 22 × 4 Matrix ist als 88 parametrisierte Unit-Tests und
88 parametrisierte Instrumentationtests implementiert. Die Unit-Variante
verwendet einen In-Memory-Jobstore und echtes Tink/Filesystem; die
Instrumentation verwendet echte Room-/SQLCipher-Transaktionen und Tink.
Zusätzlich bestehen 20 Instrumentation-Sicherheitsfälle. Home- und Display-Lock
werden dabei jeweils an allen 22 Punkten über den zentralen Sessionvertrag
simuliert. Es werden keine echten Systemeinstellungen verändert.

Das Prozessverlustmodell wirft einen festen technischen Error und rekonstruiert
alle Storage-Owner sowie eine frische Session aus persistentem DB-/File-Bestand.
**finally und close laufen dabei noch.** Das ist kein tatsächlicher harter
Prozessabbruch oder Force-stop. Die Test-Key-Owner bleiben im Testprozess
verfügbar; daraus wird kein authentifizierter Key-Restartnachweis abgeleitet.

| Abbruchpunkt | Wiederanlaufregel im bestandenen Unit-/Fairphone-Modell |
| --- | --- |
| Vor Import-Job | Kein Auftrag/Asset, kein stiller Neuerstellungsversuch |
| Nach Import-Job | RESERVED ohne Datei ausdrücklich ohne Publikation abschließen |
| Vor Staging-Anlage | WRITING ohne Datei blockiert |
| Während Schreiben | Partielle Datei blockiert, kein Ersatz und kein Cleanup |
| Nach Stream-close | Vollständige Datei neu prüfen, dann fortsetzen |
| Vor/während/nach Validierung | EOF-Validierung wiederholen, kein Vertrauen allein in Phase |
| Vor/nach Rename | Genau eine bekannte Quelldatei oder finale Datei, erneut validieren |
| Vor/nach Metadatencommit | Dieselben Metadaten einmalig committen bzw. anerkennen |
| Vor Import-Jobabschluss | Bekannten vollständigen Bestand prüfen, Job entfernen |
| Vor Delete-Job | Asset bleibt bestehen |
| Nach Delete-Job, vor/nach Hide | Ziel bereits ausschließen, Hide idempotent fortführen |
| Vor/nach unlink | Nur bekanntes Ziel; bereits fehlend nur in DELETE_PENDING zulässig |
| Vor/nach Metadatenentfernung | Entfernte Datei prüfen, Referenzen einmalig entfernen |
| Vor Delete-Jobabschluss | Job ohne gelöschte Fachinhalte abschließen |

Die Tests prüfen zusätzlich zweimalige Reconciliation, unveränderte
Ciphertextbytes, unveränderte IDs und keine doppelte Metadatenerzeugung.

## Praktischer Runtime-Blocker auf Fairphone

Gerät: Fairphone 6 / Android 16 / API 36.

Der erste B4-Instrumentationlauf wurde nach **7 ausgeführten Fällen** beendet:
**6 bestanden, 1 Failure, 0 Errors, 0 Skips**. Fehlgeschlagen ist
`faultRestartAndIdempotence[IMPORT_AFTER_JOB-EXPIRY]`.

Der Aufruf reserviert den Import-Job in einer Room-Transaktion. Anschließend
injiziert der Test den Ablauf der absoluten Sessiondeadline. Ein intern von
Room geplanter `Room Invalidation Tracker Refresh` führt danach einen
Connection-/Transaktionszugriff aus. Der bestehende B2Driver lehnt ihn
korrekt mit der festen technischen Fehlerklasse `B2-SESSION` ab.
Diese Exception bleibt im internen Hintergrund-Coroutine-Pfad unbehandelt;
der Instrumentationprozess endet. Im Testreport ist der Pfad
`TriggerBasedInvalidationTracker.notifyInvalidation →
PassthroughConnection.transaction → B2Driver.prepare → requireB2` belegt.

Es wurden keine Flow-/LiveData-DAOs verwendet. **Auch ohne observable DAOs
ist Room-interne asynchrone Invalidation-Arbeit zu berücksichtigen.** Die
Annahme, dass kurze Operationen allein sämtliche Hintergrundarbeit vor
Session-Widerruf beenden, ist damit für diesen Aufbau widerlegt. Das Gate
wurde nicht umgangen; eine kontrollierte Behandlung dieses legitimen
Widerrufs war im ursprünglichen Lauf noch nicht nachgewiesen.

Beim ursprünglichen Befund erfolgte kein Reparaturversuch, kein schwächerer
Zugriffspfad und keine Lockerung der Assertions. Die vollständige
B1–B4-Instrumentation-Regressionssuite wurde nach dem Fehler gestoppt.
Der nachfolgende gezielte Fix und seine begrenzten Nachweise stehen unten.
Lokale Testevidenz liegt ausschließlich im ignorierten
`.prototype-cache/test-evidence/b4/`; sie gehört nicht ins Repository.

## Vollständige Abschlussläufe am 8. Oktober 2026

Gerät: Fairphone 6 / Android 16 / API 36. Die komplette B4-Suite wurde zuerst
in drei Klassenläufen ausgeführt: 88 Matrixfälle, 20 Sicherheitsfälle und
11 Room-Lifecycle-Fälle. Der ursprünglich kombinierte Klassenfilter führte
nachweislich nur die 88 Matrixfälle aus; die beiden anderen Klassen wurden
deshalb separat vollständig ausgeführt. Alle 119 B4-Fälle bestanden mit
0 Failures, 0 Errors und 0 Skips.

Anschließend wurde `:data:connectedDebugAndroidTest` **ohne Klassenfilter**
ausgeführt. Dieser einzige vollständige Regressionlauf umfasst:

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

Die Importmatrix umfasst 13 Übergänge × vier Modi = 52 Fälle; die
Deletematrix neun Übergänge × vier Modi = 36 Fälle. Die vier Modi sind
Exception, Prozessverlustmodell, Session-Expiry und Generationswechsel.
Jeder Fall rekonstruiert den Storage-Owner und prüft Reconciliation sowie
zweimalige Wiederholung. Die acht Fälle vor Staginganlage beziehungsweise
während Schreiben bleiben erwartungsgemäß BLOCKED, ohne Publikation,
Regeneration oder Bereinigung. Vor Jobreservierung bleibt der Bestand erhalten;
ein RESERVED-Import ohne Datei wird ausdrücklich ohne Publikation beendet.
Die übrigen bekannten vollständigen Import-/Delete-Zustände werden konsistent
fortgesetzt. Publish, Metadatencommit und unlink werden idempotent behandelt.
IDs und vorhandene Ciphertextbytes bleiben erhalten; es entsteht kein
Doppel-Publish, Doppel-Commit oder Ersatz-Key.

Die Sicherheitsfälle bestanden ebenfalls: Unknown State und Orphans bleiben
unangetastet; fehlende Dateien werden nicht ersetzt; manipulierte/trunkierte
Dateien, falscher File-Key und AAD-Swap blockieren. Fehlende oder beschädigte
DB, falscher DB-Key und unbekannte DB-Sidecars führen nicht zu Neuerstellung.
Commit-Widerruf rollt auf derselben Connection zurück. Alte Handles bleiben
nach Generationswechsel unbrauchbar. Home- und Display-Lock-Widerruf werden
jeweils an allen 22 Übergängen über den zentralen Sessionvertrag simuliert.
Maintenance-Ablauf verhindert READY; eine neue legitime Session kann einen
konsistent erhaltenen Bestand anschließend reconciliieren.

Kein neuer Room-Lifecycle-Befund im Gesamtlauf: Der korrekte Zugriffswiderruf
beendete den Instrumentationprozess nicht mehr. Close-/Refresh-Rennen,
alte Instanzen, neue legitime Instanzen sowie die negativen I/O-/SQLite-
Background-Fault-Fälle bestanden. Unerwartete Fehler wurden nicht als
Revocation klassifiziert; sie blockierten die betroffene Instanz.
Die vollständige B2-Regression ist grün, einschließlich Direct und Room,
Fehlerbeständen, Migrationen, Commit-Widerruf, Generationen, Close und d.lck.

`./gradlew test --rerun-tasks` führte alle 63 zugehörigen Gradle-Tasks frisch
aus: **288 Unit-Tests, 0 Failures, 0 Errors, 0 Skips**. Davon B4:
6 Vertragstests, 88 Matrixfälle und 12 Lifecycle-Tests, insgesamt **106**.
Der In-Memory-Unit-Jobstore ersetzt keinen Room-Runtime-Nachweis.

Ebenfalls erfolgreich: `:app:assembleDebug`, Lint für app/data/security,
`verifyArchitecture`, `:app:verifyPrototypeManifest` und
`:data:assembleDebugAndroidTest`. Gradle-/Dependency-Dateien sind unverändert.
Baseline: Room 3.0.3, SQLCipher 4.19.1, AndroidX SQLite 2.7.1,
Tink Android 1.23.0, KSP 2.3.12. Keine neue Dependency.

Das gemergte Manifest enthält weiterhin nur USE_BIOMETRIC für den bestehenden
Plattform-Credential-Pfad und die appinterne Signature-Permission; nur die
Launcher-Activity ist exportiert. Keine INTERNET-/CAMERA-/Notification-/
Storage-Permission, kein Provider oder Service. Backup-/Transfer-Ausschlüsse
und FLAG_SECURE bleiben erhalten. Die Security-Diff-Prüfung fand keinen
neuen Klartext-Key auf Disk, persistiertes Keyset, Zeitfenster-Fallback,
Logging-/Netzwerkpfad, Klartext-Tempbestand oder automatischen Ersatzbestand.
Room bleibt auf einzelne DB-Phasenschritte begrenzt; kein File-I/O hält eine
Room-Instanz offen. Die eigenen Catches klassifizieren oder blockieren und
reichen Fehler weiter, statt beliebige Exceptions zu ignorieren.

Lokale XML-Evidenz liegt ausschließlich im weiterhin ignorierten
`.prototype-cache/test-evidence/b4/`. Sie wird nicht ins Repository aufgenommen.

## Offene Nachweise und manueller Test

API 30 und API 37 bleiben offen: ADB zeigte ausschließlich das Fairphone.
Nicht nachgewiesen sind SIGKILL, echter Force-stop an jedem Übergang,
Power-loss, Kernel-/Filesystem-Crash und echte Speicherknappheit. Das getestete
Prozessverlustmodell führt weiterhin finally/close aus und hält Test-Key-Owner
im selben Testprozess. Es beweist keine vollständige Crash-Durability.

Auch offen: reale Multi-Key-DEVICE_CREDENTIAL-Orchestrierung, finale
Import-/Asset-Struktur und Schlüsselhierarchie, OEM-Backup-/Transferverhalten
sowie RAM-/Flash-Reste. Kein Schutz gegen ein kompromittiertes OS/Kernel
wird zugesichert. Die positiven Ergebnisse gelten für die getestete
Fairphone-/API36-Konfiguration.

Es wurde kein manueller B4-Force-stop-Lauf gestartet. Die Haupt-App besitzt
keine B4-Testoberfläche; die injizierten RAM-Testkeys stehen nach tatsächlichem
Prozessende nicht erneut zur Verfügung. Ein ausführbarer manueller Restart-
Test benötigt daher eine separat freigegebene Teststeuerung beziehungsweise
Key-Bereitstellung. Dafür wurden weder UI noch Authentifizierungsvertrag oder
Key-Hierarchie verändert. Die bestandene Modell-Reconciliation wird nicht
als manueller oder realer Multi-Key-Restartnachweis ausgegeben.

## Room-3.0.3-Quellenprüfung und gezielter Blocker-Fix

Geprüft wurde das offizielle, versionsgenaue Maven-Quellartefakt
[room3-runtime-android 3.0.3 sources](https://dl.google.com/dl/android/maven2/androidx/room3/room3-runtime-android/3.0.3/room3-runtime-android-3.0.3-sources.jar).
SHA-256 des gelesenen Artefakts:
`353a8ac6cc748ebd593eda9a6b5954da9dcb810215b25d0c141630e7969f984d`.
Keine Ableitung aus neueren androidx-main-Sourcen.

Nachgewiesene Kette in diesem Artefakt:

1. `RoomDatabase.useWriterConnection` plant nach dem Writer-Block
   `invalidationTracker.refreshAsync()` ein (`commonMain/RoomDatabase.kt`, 634–638).
   Generierte Schreibpfade planen ebenfalls über `util/DBUtil.kt`, 69–75.
2. Android `InvalidationTracker.refreshAsync()` prüft die Close-Barrier und ruft
   `refreshInvalidationAsync` auf (`InvalidationTracker.android.kt`, 131–134).
3. `pendingRefresh` fasst ausstehende Refreshes zusammen. Bei false → true
   startet `database.getCoroutineScope().launch` den Refresh
   (`commonMain/InvalidationTracker.kt`, 379–394).
4. `notifyInvalidation` registriert sich an der Close-Barrier, setzt
   `pendingRefresh` zurück und fordert eine Writer-Connection an. Darin läuft
   eine IMMEDIATE-Transaktion. Der Catch behandelt nur SQLiteException,
   nicht PaGs Access-Revocation (`InvalidationTracker.kt`, 401–434).
5. Room baut seinen Scope aus `queryCoroutineContext + SupervisorJob(parentJob)`
   (`RoomDatabase.android.kt`, 212–219). Das isoliert Child-Fehler, macht einen
   unbehandelten launch-Fehler aber nicht zu einer kontrollierten PaG-Diagnose.
6. `RoomDatabase.close()` ruft `closeBarrier.close()` auf. Die Barrier sperrt
   neue Teilnehmer und wartet auf bereits registrierte Refreshes. `onClosed`
   cancelt den Scope, stoppt den Tracker und schließt den ConnectionManager
   (`RoomDatabase.android.kt`, 398–406; `concurrent/CloseBarrier.kt`, 53–59, 83–96).

### Fehlerklassifikation und Scope

Der Builder verwendet jetzt die öffentliche API `setQueryCoroutineContext`:
Dispatcher, instanzlokaler Parent-SupervisorJob und instanzlokaler
CoroutineExceptionHandler. Room 3.0.3 bindet seinen eigenen SupervisorJob
an diesen Parent; der konfigurierte Handler bleibt im CoroutineContext.

Nur der finale eigene Typ `StorageAccessRevoked` ist erwarteter Widerruf.
Er bleibt ein B2Exception mit `failure = SESSION` und unveränderter fester
Diagnose `B2-SESSION`. Die zentrale Session-/Scope-Prüfung ist unverändert.
Ein gewöhnlicher B2Exception(SESSION), ein Exceptiontext oder ein
CoroutineName reicht ausdrücklich nicht für diese Klassifikation.

Der instanzlokale Zustand lautet:

- NONE: kein beobachteter Hintergrundfehler;
- EXPECTED_REVOCATION: alte Instanz nicht erneut verwenden, kein altes Ergebnis;
- UNEXPECTED_BACKGROUND_FAULT: Instanz blockiert, Parent cancelt, kein weiterer
  Commit oder Erfolg; feste Diagnose B4-BACKGROUND-FAULT.

Unerwartete Fehler können niemals durch einen späteren erwarteten Widerruf
herabgestuft werden. Es werden keine Exceptionobjekte, Texte oder Pfade
persistiert oder an UI/Logs ausgegeben. Der Handler selbst führt kein DB-I/O
und kein `db.close()` aus, um keine Reentranz in die aktive Close-Barrier zu
verursachen. Der kontrollierte Owner schließt in finally.

Rooms eigener SQLite-Catch darf echte Fehler nicht unbemerkt verbergen.
Der bestehende B2Driver normalisiert deshalb zusätzlich Fehler bei `prepare`,
`getText` und `getLong` über seinen bereits vorhandenen `sanitizedB2`-Pfad.
Diese eigenen typisierten Fehler erreichen den Handler und werden als
UNEXPECTED_BACKGROUND_FAULT behandelt. Kein SQLite-/I/O-Fallback wird erlaubt.
Die Fault-Sperre am Driver ist ebenfalls ein B2Exception, damit dessen
bestehender Same-Connection-Rollback bei verweigerter Commit-Zulassung bleibt.

### Kurze DB-Schritte und Close

`B4StepStore` führt pro Snapshot oder persistenter Phasenänderung genau einen
kurzen Room-Aufruf aus. Danach folgen Room.close, Cancel/Join des ausdrücklich
mitgegebenen Parent-Jobs und Driver.close, bevor der nächste File-/DB-Schritt
zugelassen wird. Der Consumer gibt DB-Keybytes nur für diesen Aufruf heraus.
Keine RoomDatabase bleibt über Tink-I/O oder einen Authentifizierungsdialog offen.

Join wartet auch auf gequeue-te/cancelte Child-Arbeit und die Fehlerbehandlung.
Vor Rückgabe eines erfolgreichen Phasenschritts werden Background-Fault,
Sessiongültigkeit und geschlossene Ressourcen erneut geprüft. Ein fehlerhafter
StepStore bleibt für weitere Schritte unbrauchbar. Eine neue legitime Session
verwendet einen neuen Owner; sie reaktiviert keinen alten Handle.

Close/Cancellation sind Ressourcenbereinigung, kein Sonder-Unlock. Die
absolute Deadline, Generation und Foreground-Prüfung werden nicht gelockert.
Ein bereits zugelassener nativer Schritt kann noch enden. Es gibt keinen
Anspruch auf sofortige Löschung von RAM oder sofortigen Abbruch nativer I/O.

### Gezielte praktische Nachweise

Finaler gezielter Lauf auf Fairphone 6 / Android 16 / API 36:
**11 Instrumentationtests bestanden, 0 Failure/Error/Skip.**

- Expiry nach Commit, bevor der eingeplante Refresh läuft;
- Expiry beim Refresh-Zugriffscheck;
- Generationswechsel am selben Check;
- Close mit pending Refresh;
- Close bei laufendem Refresh und gleichzeitigem Sessionablauf;
- Close direkt nach Write, mehrfacher Close;
- alte DAO-/Room-/Connection-Handles und Reopen nach Close abgewiesen;
- mehrere schnelle Writes mit zusammengefasstem Refresh;
- echter Refresh mit injiziertem I/O-Fehler → Background-Fault/BLOCKED;
- echter Refresh mit injiziertem SQLite-Fehler → Background-Fault/BLOCKED;
- kurzer StepStore: nach Jobreservierung Expiry, dann frische Session und
  konsistente Reconciliation ohne Dateineuerzeugung.

Die Tests halten den echten Refresh deterministisch über einen Testdispatcher
an. Der versionsgeprüfte CoroutineName dient dabei ausschließlich der
Test-Scheduling-Steuerung, niemals einer Security-Entscheidung. Die Grenzen
werden mit Latches und Fake-Zeit geprüft, nicht mit Warte-Sleeps.

**12 gezielte Lifecycle-Unit-Tests bestanden**, 0 Failure/Error/Skip. Sie prüfen
zusätzlich die genaue Typklassifikation, nicht akzeptierte generische
SESSION-Fehler, I/O-/SQLite-/Programmierfehler, irreversiblen Fault-Status,
Idempotenz von Close, gültige neue Instanz und unveränderte Sessionautorität.

Für den finalen Fix ebenfalls erfolgreich: App-Debug-Build, Lint für
app/data/security, Architektur-/Manifestprüfungen sowie Instrumentation-Kompilierung.
Die danach vollständig ausgeführten Unit- und Instrumentationläufe sind oben
mit ihrem jeweiligen Umfang dokumentiert.

Die gespeicherten Job-IDs/Phasen bleiben nach Widerruf rekonstruierbar.
Die neue legitime Room-Instanz kann denselben Bestand öffnen. Keine DB wurde
ersetzt, kein unbekannter Rest entfernt und keine Sicherheitsassertion gelockert.
Die gezielten Tests beweisen keinen vollständigen B4- oder Power-loss-Nachweis.

### Auswirkung auf B2 nach vollständiger Regression

**ROOM BLEIBT EMPFOHLEN**, zusätzlich unter dieser nachgewiesenen
Lifecycle-/ExceptionHandler-Bedingung für Room 3.0.3. Ein Wiederöffnen der
B2-Entscheidung ist nach der vollständigen grünen B4-Matrix und B1–B4-Regression
im getesteten Fairphone-/API36-Umfang nicht nötig. `docs/architecture.md`
wird hier nicht geändert.

Room-/SQLite-/SQLCipher-Upgrades müssen erneut prüfen:
Invalidation-Scheduling, pendingRefresh, CoroutineScope/SupervisorJob,
queryCoroutineContext, Exception-Propagation und Room-interne Catches,
Close-Barrier, Cancel/Join und Connection-Lifecycle. Keine Garantie für
spätere Versionen oder andere OEM-/Android-Konfigurationen.

## Quellen für Filesystem-Grenzen

- [Android FileDescriptor.sync](https://developer.android.com/reference/java/io/FileDescriptor#sync()):
  Synchronisiert die dem Descriptor nachgelagerten Puffer; upstream
  Anwendungspuffer müssen vorher geleert sein.
- [Android Files.move](https://developer.android.com/reference/java/nio/file/Files):
  Ohne ATOMIC_MOVE besteht kein allgemeiner Atomizitätsnachweis. Bei
  fehlgeschlagenem nichtatomarem Move kann die Dateilage uneindeutig sein;
  B4 blockiert dann statt zu überschreiben oder heuristisch zu löschen.
