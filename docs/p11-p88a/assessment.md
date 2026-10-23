# §88a – SQLCipher-Statement-, Cursor- und Window-Enden

Basis `8f630448670c0c806b30c050aa5a424e7440681f`, Branch
`p11-performance-optimization`, Issue #32, 2026-10-23.

**Ergebnis:** Die SQLCipher-4.19.1-Asymmetrie ist erneut nativ reproduziert:
Vendor-`close()` allein lässt einen geöffneten Cursor und sein Window offen;
`reset()` schließt beide. **Der bestehende Projektpfad behebt genau das bereits
seit §72 mit `B2StatementCleanup` (reset vor close). Dort wurde hier kein weiterer
fehlender Abschluss reproduziert. Deshalb kein zusätzlicher Produktfix.**
Nur P77-Diagnostik/Instrumentation und Dokumentation geändert. #32 bleibt für
unabhängigen Review und seine übrigen Native-/Scope-Nachweise offen; kein Merge/Release.

## Tatsächlicher Pfad an der Basis

Primärbelege sind die tatsächlich eingebundenen AARs, nicht eine angenommene aktuelle
Upstreamfassung: [Koordinaten/SHA-256](vendor-artifact.txt),
[SQLCipher-Bytecode](vendor-bytecode.txt), [Room-usePrepared](room-connection-bytecode.txt),
[Room-Transactor](room-passthrough-bytecode.txt),
[Cursor-/Window-Dispose-Auszug](window-close-bytecode-extract.txt).

1. Room 3.0.3 verwendet bei diesem Driver den Passthrough-Pfad. `usePrepared`
   bereitet das Statement vor und schließt es über `closeFinally` auch bei Exception.
2. Der von `B2Driver.prepare` zurückgegebene Wrapper leitet `close()` an den schon
   vorhandenen `B2StatementCleanup` weiter: zuerst `reset()`, danach `close()`, beide
   Versuche auch bei Fehler/Revoke; erster Fehler bleibt primär, zweiter suppressed.
   Der Denied-Commit-Rollbackpfad verwendet denselben Abschluss.
3. Zetetics `SQLCipherStatement.reset()` ruft `cursor.close()`, setzt den Cursor
   auf null und löscht das stepped-Merkmal. `close()` setzt ausschließlich `closed=true`.
   Auch `JournalModeStatement` delegiert reset/close; die Beobachtung entpackt diesen
   bekannten Wrapper. Ein Vendor-Closed-Flag ist kein Cursorabschluss.
4. Cursorabschluss schließt Query/Driver sowie das Window; dessen letzte Referenz
   führt zu `CursorWindow.dispose() → nativeDispose(mWindowPtr) → mWindowPtr=0`.
   Die Diagnose prüft `Cursor.isClosed`, das null gesetzte Statement-Cursorfeld
   und den Pointer des vorher tatsächlich geöffneten Windowobjekts separat.
5. `ActionDatabaseAccess.use` beendet anschließend Room, cancelt/joint den eigenen
   Coroutineparent und schließt B2Driver. `resourcesClosed()` prüft Driver-Closing,
   `isOpen=false` für die bekannten nativen DB-Verbindungen und die genullte
   Java-Keykopie des Drivers. Preflight-Native-Close wird getrennt beobachtet.
6. Außerhalb des DB-Scopes beendet `SessionDbKeyOwner.withDbKey` die geliehene
   Java-Keykopie in `finally`. Dieser Arrayabschluss wird im kontrollierten Scope
   **nach Rückkehr bzw. Exception** geprüft; der Test nullt das Array nicht selbst.

Die ursprüngliche Issue-Beschreibung „B2Driver ergänzt keine Freigabe“ beschreibt
damit einen älteren Stand. Der §72-Abschluss ist in der hier geprüften Basis vorhanden.

## Isolierte Androidfälle

Samsung SM-M526BR, Android 13/API 33, arm64-v8a;
[Geräteumgebung](device-environment.txt). Bestehender P77-§86b-Root, echter
ProductRuntime/Session/Keyowner, ActionDatabaseAccess, Room und B2Driver/SQLCipher;
synthetische Authentifizierung und isolierter noBackup-Fixtureroot.
Normale `stress`-Fixture: **200 Personen / 1.000 Tasks / 5.000 TaskEvents** in ACTIVE;
der kontrollierte COUNT prüft tatsächlich 5.000, keine 129k-Fixture.

`P88StatementDiagnostics` dekoriert nur die vorhandene Taskrepository-Statementseam.
Zusätzlich öffnet ein kontrollierter Testreader einen eigenen Scope auf derselben
synthetischen DB **mit dem originalen Runtime-READY-Kontext und originalen Keyowner**.
Keine zweite Session-/Schlüsselautorität und kein produktiver Read-/Cacheport.
Die Cursor-/Windowreferenzen dienen dem Endtest und werden nach reset aufgegeben;
die Trace ist begrenzt und enthält keine Payloads, Schlüssel oder Pointeradressen.

| Maßgebender Rohbeleg | Geprüfter Fall |
|---|---|
| [paging-02](paging-02.txt) | Kontrollierter `usePrepared`-Scope, negativer Vendor-Close-Gegenfall und reset-Vergleich; danach echte TaskRepository-/VM-History: zweimal Seiten 1, Keyset-Folgeseite 2, direkte Mitte 63, letzte Seite 126. Exakte IDs/Reihenfolge, 4.001 Events des ausgewählten Tasks, maximal 32 pro Seite; letzter Rest ein Event. |
| [revoke-01](revoke-01.txt) | Nach tatsächlich geöffnetem SELECT-Cursor im kontrollierten Room-Scope echte `runtime.session.lock()`. Der folgende B2-Getter verweigert mit `StorageAccessRevoked`; Cleanup funktioniert trotz Revoke, die geliehene Java-Keykopie ist null, eine neue Lease über den alten Owner/Kontext wird vor Consumer-Eintritt verweigert. Runtime/VM LOCKED. |
| [statement-error-01](statement-error-01.txt) | Nach geöffnetem äußeren Cursor führt ein inneres echtes `usePrepared("SELECT abs(-9223372036854775808)")` einen Vendor-SQL-Overflow aus. Ein tatsächlicher Vendor-step-Fehler, als bestehendes `B2Exception(KEY_OR_UNREADABLE)` sanitisiert; beide Statementpfade schließen, Keylease null, keine suppressed Cleanupfehler. |

Der Fehler ist bewusst durch SQL ausgelöst, **kein injizierter Java-Closefehler**.
Sein kontrollierter Testcaller fängt ihn ab; Runtime bleibt dort READY. Das ist
kein neuer Test des ProductTaskRepository-STORAGE_FAILURE/BLOCKED-Verhaltens und
keine Autorisierung eines gewöhnlichen Produkt-Retrys nach Speicherfehler.
Revoke/Overflow verwenden den realen kontrollierten Room/B2-Scope, Paging zusätzlich
den echten ProductTaskRepository-/TaskModel-Pfad. Keine Touch-Latenzabnahme behauptet.

### Konkrete Endbeobachtungen

| Finaler Lauf | prepare / reset / Closed-Flag | Cursor beobachtet / geschlossen | Window beobachtet / Pointer null | Preflight / Room / Driver-Enden |
|---|---:|---:|---:|---:|
| Paging | 276 / 276 / 276 | 276 / 276 | 276 / 276 | 9 / 9 / 9 |
| Revoke | 18 / 18 / 18 | 18 / 18 | 18 / 18 | 1 / 1 / 1 |
| SQL-Fehler | 19 / 19 / 19 | 19 / 19 | 18 / 18 | 1 / 1 / 1 |

Die Windowzahlen zählen beobachtete Objekte, keine künstliche Gleichsetzung mit
Statements: Beim fehlerhaften SQL wurde ein Cursor, aber kein Window erfasst.
Die Tabelle zählt die dekorierten B2-Statements; die zwei absichtlich unwrapped
Vendor-Gegenproben sind separat unten belegt und darin nicht mitgezählt.
Alle erfassten Windows waren bei ihrer ersten Beobachtung nonzero und nach reset
null. Kein Room-Close-Return mit noch ungeschlossenem erfasstem Statement;
alle Driver-`resourcesClosed`-Beobachtungen true. In allen drei kontrollierten
Scopes geliehene Java-Keykopie null; 0 suppressed Cleanupfehler.

**0 tatsächliche Vendor-reset-/close-Exceptions**, 1 Vendor-step-Exception im
Overflowfall. Ein echter Vendor-Closefehler wurde nicht reproduziert; dessen
generelle Beherrschung wird nicht aus den synthetischen Hostfehlern abgeleitet.

Der negative Vendor-Gegenfall benutzt absichtlich den nackten Adapter auf der
bereits autorisierten, von Room/B2 gehaltenen Verbindung; kein zusätzlicher expliziter
B2Native.open oder Keyowner. Nach `prepare(...).use { step() }` lautet die Beobachtung:
`statement_closed=true,cursor_open=true,window_live=true`. Danach führt der Test
**nur zur eigenen Bereinigung** reset aus und prüft Cursor geschlossen/Window null.
Der Vergleich `reset_then_close` hat schon direkt nach `use` beide Enden erreicht.
Starke Referenzen auf Cursor und Window bleiben während dieser Prüfung erhalten;
der Unterschied beruht nicht auf Finalizer/GC. Die manuelle Bereinigung ist keine
neue Produktfreigabe oder Retirementquittung.

## Prozesssamples sind kein Freigabebeweis

[observations.json](observations.json), aus Rohtext mit [summarize.py](summarize.py):

| Lauf, vor Scope → vor Activityfinish | Native-Heap Bytes | PSS KiB | FD | natürliche GC-Zähler |
|---|---:|---:|---:|---:|
| Paging | 10.996.800 → 11.398.560 | 182.883 → 161.915 | 136 → 132 | 0 → 2 |
| Revoke | 10.931.568 → 10.858.240 | 185.712 → 152.025 | 136 → 134 | 0 → 1 |
| SQL-Fehler | 11.006.064 → 10.991.424 | 182.739 → 183.365 | 134 → 136 | 0 → 0 |

ART-used wird ebenfalls roh erfasst. Alle Werte sind **Prozesssamples an einzelnen
Punkten**, einschließlich Fixtureaufbau/Compose/Runtime, keine Scopekosten,
gemessenen Peaks oder Obergrenzen. Ein FD-Anstieg um zwei oder sinkender PSS beweist
weder Leak noch Leakfreiheit. Kein erzwungener GC; die Endassertions verwenden
Objektzustände und Pointer, nicht Heapdifferenzen. Vor-Finish-Samples sind kein
Zustand nach vollständigem Activity-/Runtimeende.

## Tests, Builds und Grenzen

**3 finale Androidszenarien bestanden, insgesamt 4 erfolgreiche Ausführungen**:
Der erste [paging-01](paging-01.txt)-PASS bleibt separat erhalten. Danach wurde nur
der Harness um die explizite suppressed-Cleanupfehler-Assertion und eine eindeutige
„not_applicable“-Ausgabe verschärft; alle drei finalen Fälle liefen mit dem neuen
APK. [Erstes APK](apks-01.json), [finale APKs/Signer](resume-apks.json).
Keine Harnessabbrüche, keine als reale Vendor-Closefehler deklarierten Injektionen.

**6 gezielte Hosttests, 0 Fehler/Skips**:
[B2StatementCleanupTest 4](TEST-org.pag.data.prototype.b2.B2StatementCleanupTest.xml),
[P11DiagnosticCleanupTest 2](TEST-org.pag.data.product.P11DiagnosticCleanupTest.xml).
Diese prüfen Reihenfolge, Doppelclose, primäre/suppressed Fehler mit synthetischen
Statements; sie ersetzen keinen Vendorfehler. Keine Gesamtsuite.
[Host-/P77-/Runnerbuild](build-host-01.log) PASS,
[finaler P77-/Runnerbuild](build-02.log) PASS. Reproduktion:

```sh
./gradlew -PpagTestBuildType=p77runtime :data:testDebugUnitTest \
  --tests org.pag.data.prototype.b2.B2StatementCleanupTest \
  --tests org.pag.data.product.P11DiagnosticCleanupTest \
  :app:assembleP77runtime :app:assembleP77runtimeAndroidTest --offline --console=plain
python3 docs/p11-p88a/run-device.py install
python3 docs/p11-p88a/run-device.py run paging paging-02
python3 docs/p11-p88a/run-device.py run revoke revoke-01
python3 docs/p11-p88a/run-device.py run statement_error statement-error-01
python3 docs/p11-p88a/run-device.py after
python3 docs/p11-p88a/summarize.py
python3 docs/p11-p88a/verify-artifacts.py
```

[Bereinigung](resume-cleanup.json): keine isolierten Fixtureroots übrig;
andere Appinstallationen [vorher](resume-protected-before.json)/[nachher](resume-protected-after.json)
unverändert. [Artefaktprüfung](artifact-check.json): sämtliche Produktquellen,
Budgets, Session-/Writer-/Owner-/Reuse-Gates, Schema, Crypto und Buildkonfiguration
unverändert. Das unveränderte Releaseartefakt aus §87a enthält die Probe nicht;
hier kein neuer normaler Releasebuild oder Produktupdate.

**Restgrenzen:** Individuell erfasst sind Room/B2-Statementcursor/-Windows der
kontrollierten Scopes und des Taskrepositories. Preflight meldet seinen separaten
Native-DB-Close, nicht jeden einzelnen nativen Preflightcursor. Keine Erfassung
aller anderen Repositories, nativer sqlite3_stmt-Caches, versteckter temporärer
Sortierbuffer, sämtlicher nativer Keykopien, Allocator-/Grafik-/Framekosten oder
deren maximaler Restbelegung. Windowpointer null und DB-isOpen false sind konkrete
Handle-Enden, keine globale SQLCipher-Wipe-/Heap-/8+8-MiB-Garantie.

**Kein neuer Produktfix erforderlich für den reproduzierten Abschlussfall.
#32 bleibt für Review/Restumfang offen. WIP pushen, Issue aktualisieren,
danach STOPP zum unabhängigen Review. Kein Merge/Release.**
