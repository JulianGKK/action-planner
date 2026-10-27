# §115 – Session und echte Geräteauthentisierung

Basis `45053c7134a76261b7e557197090b73ce2adf4b9`, Branch `p11-performance-optimization`.

**READY_FOR_INDEPENDENT_CLOSURE für #39.** Die bisher fehlende reale DeviceCredential-/Lifecycle-Abnahme besteht auf dem isolierten Samsung SM-M526BR/API33. Kein reproduzierbarer Sicherheitsfehler im geprüften Produktpfad; daher keine Änderung der produktiven Sicherheitsimplementierung. Die unabhängige Schlussprüfung ist ausdrücklich noch auszuführen; #39 wird hier nicht selbst geschlossen. Kein Merge/Release.

## Diff und neue Belege

Nur P77-/AndroidTest-Code und dieser Belegordner geändert: `P115CredentialActivity` bildet den normalen Activity-Lifecycle und die normale Produktverdrahtung ab, konstruiert ausdrücklich den öffentlichen `ProductRuntime` mit **`ProductCredentialAuthentication`**, ohne Harness-/Testauthentisierung. Beobachtet werden ausschließlich Zustände, Generationen und monotone Zeiten. Die Activity ist nicht exportiert und nicht im normalen Release-Source-Set. `P115CredentialProbe` ergänzt die manuell unterstützten Fälle; `P115AuthorityProbe` ergänzt den aktuellen Originalowner-/Writer-Gegenfall. Die Produktionsfristen, Keystorekonfiguration, SQLCipher/Room, Schema12/CAS und Recovery bleiben unverändert. R8 **8.388.608 B**, T16 **16.777.216 B**, beide Reuseflags **false**; keine automatische Core-Readmission.

| Neue P77-Prüfung | Ergebnis / Rohbeleg |
|---|---|
| Normale Android-Credentialeingabe → READY; 30 s READY-Inaktivität; Lock → erneute echte App-Authentisierung | PASS: Lock nach **29.984 ms** ab Test-READY-Beobachtung; Generation **1→3**, alter StorageContext bleibt gesperrt. [idle-first.txt](idle-first.txt) |
| 180 s absolute Frist bei ausdrücklich injizierter Benutzeraktivität alle 10 s | PASS: verbleibende Frist beim Messbeginn **177.367 ms**, Lock nach **177.390 ms**; Authentisierungsdeadline unverändert **180.000 ms**, einschließlich Maintenancezeit. Keine Verlängerung durch Aktivität. [absolute-first.txt](absolute-first.txt) |
| HOME → Foreground, Lock/Reauth, physische Gerätesperre → Geräteentsperren → separate App-Authentisierung | PASS: HOME sperrt; Geräteentsperren allein bleibt LOCKED; echte App-Authentisierungen **Generation 1→6→13**. [lifecycle-first.txt](lifecycle-first.txt) |
| Gehaltene originale Header-/Row-/Form-/Writer-/Workspace-/StorageContext-/Keyownerreferenzen nach Lock und Reauth | PASS: kein neuer Klartextzugriff, keine Schreibarbeit/verspätete Publication; alter Keyowner verweigert auch den neuen READY-Kontext vor Aufruf des Keyconsumers. Lifecyclebeleg oben. |
| Pending Writer → UNKNOWN → Actionwechsel, großer 200/1000/32-Bestand | PASS: alte Header-/Rowcallbacks unwirksam, **0 Klartextcallbacks, 1 Core-Read / 0 Fullworkspaces**. **Hier synthetische P77-Authentisierung und ausdrücklich injizierte UNKNOWN-Protokollcompletion, kein behaupteter UNKNOWN-SQL-Commit.** [authority-first.txt](authority-first.txt) |

Die Form-/Writergegenprobe hält die echte Formularfence und konstruiert daraus die produktive `OriginalInputClick`-Hülle nach Reservation; sie extrahiert keinen Compose-Savebutton. Header und Zeilenbutton stammen direkt aus dem Produktmodell. Die Referenzen werden absichtlich über mehrere Reauthentisierungen gehalten; nicht jede ältere Zeile ist beim anschließenden Lock noch der aktuelle Leseticketinhaber.

**Manuelle Schritte tatsächlich ausgeführt:** `python3 docs/p11-p115/device.py credential idle first`, danach `absolute first`, danach `lifecycle first`; im normalen Android-Dialog jeweils am Gerät authentisieren, niemals Credentials in Chat/Logs übertragen. Im Lifecyclefall nach `MANUAL_DEVICE_LOCK_NOW` physisch sperren, etwa zehn Sekunden gesperrt lassen, normal am Gerät entsperren und die App wieder öffnen; danach erneut separat im App-Credentialdialog authentisieren. Der Nutzer hat diese Schritte übernommen. Keine automatisierte PIN-Eingabe, keine Credential-Screenshots oder Systemdialog-Hierarchien. [install-1.json](install-1.json) gehört zu Idle/Absolut; [install.json](install.json) zu erweitertem Lifecycle/Authority. Keine rückwirkende Behauptung, dass die älteren Zeitläufe die später ergänzten Keyownerassertions enthielten.

## #39 – ursprüngliche Akzeptanzmatrix

Vorhandene Belege werden übernommen, nicht als neue vollständige historische Testkampagne ausgegeben. Historische §109-/§111-Berichte behalten ihre damaligen roten Produktgrenzen; deren Consumer-/Navigationslücken wurden erst in §§110/112–114 geschlossen.

| Kriterium | Bewertung / konkreter Nachweis |
|---|---|
| Echte Sessionfristen, Lock, Lifecycle, Gerätesperre, Reauth/Generation, keine Fristverlängerung | **PASS** neue reale Belege oben; `SessionCoordinatorTest.readOnlyCountdownUsesAuthDeadlineAndDoesNotRenewIdleOrAbsolute` und übrige Sessionregressionen im aktuellen Hostlauf. |
| Action-/Status-/Parent-Child-Bindung, Statuswechsel, P3b/Permission und Cross-Action | **PASS im integrierten Originalowner-Scope**: aktueller Authority-Gegenfall; aktuelle ActiveReadSessionThread-/LeadershipSessionConcurrency-/OutstandingCoreRead-/TaskModel-Regressionen; [§111 ProductSecurity-/DocumentProductDispatch-Hostbelege](../p11-p111/host-security-results), [P3b-Writer](../p11-p111/runs/security-writer-leadership/instrumentation.txt). |
| BLOCKED/Maintenance, Inventar/Schema/FK/Cipher-Preflight, Reconciliation, DocumentNamespaceLane | **PASS bestehender unveränderter Verträge**: [§111 Security-Hostbelege](../p11-p111/host-security-summary.json), insbesondere ProductSecurityTest, DocumentFoundationTest, DocumentReadKeyCancellationTest und DocumentProductDispatchTest; [native Ressourcen-/Preflightprüfung](../p11-p111/runs/native-repeat_boundary-schema12-retry1/instrumentation.txt). Kein zusätzlicher DB-/Keyzugriffspfad durch §115. |
| Commit/Rollback/UNKNOWN/Recovery/Cleanup, verspätete/doppelte Callbacks, Endpublication | **PASS**: aktueller Writer-/UNKNOWN-Gegenfall und Session-/Callbackhosttests; [native Schema12/CAS](../p11-p111/runs/security-cas-native_revisions/instrumentation.txt), [P3b](../p11-p111/runs/security-writer-leadership/instrumentation.txt), [Dokumentwriter](../p11-p111/runs/security-writer-documents/instrumentation.txt), [unbestätigtes Cleanup](../p11-p111/runs/native-cleanup_unknown/instrumentation.txt); [§114 tatsächliches Task-COMMITTED/Revision](../p11-p114/tasks-rows.txt). |
| Klartextminimierung, Kopien/Lebensdauer, keine neue Cachepersistenz/Key-/Lease-/Room-/P3b-/Dokumentpayloadautorität | **PASS app-eigener Vertrag**: [§110 Producer-/Halter-Matrix](../p11-p110/producer-matrix.md), [§114 Produktdiff](../p11-p114/assessment.md), neue gehaltene Captures/alter Keyowner gesperrt. Restgrenzen unten gelten weiter. |
| Große Bestände, Budget, Unicode/invalid, System-/Prozessabbruch | **PASS vorhandener Negativscope**: [§112 enge Transition-Grenze](../p11-p112/tight-final.txt), [§114 Budget vor Materialisierung/Lock](../p11-p114/negative-rows.txt), [§111 Unicode-/Abbruch-/Ressourcenbelege](../p11-p111/assessment.md). Kein neuer physischer OOM-/Globalheap-Nachweis behauptet. |
| Host + isoliertes Android, unabhängiger Code-/Threat-Model-Review | **Technische Belege vollständig; unabhängige Schlussprüfung ausstehend**, wie vom Auftrag verlangt. [Aktueller Hostlauf](host.log): **42 PASS, 0 Fail/Error/Skip**, [JUnit](host); neue vier isolierte Gerätefälle PASS. Keine Selbstabnahme eines unabhängigen Reviews; separates umfassendes 1.0-Audit #46 bleibt eigenständig. |
| Isolierte Installationen/Resets, Release separat | **PASS**: nur `.p77runtime`/`.p77runtime.test`; [signierte APK-Receipts](install.json), [geschützte Pakete unverändert](cleanup.json). Keine Releaseinstallation/-veröffentlichung. |

[Build](build.log) erfolgreich. Aktueller Hostumfang: SessionCoordinatorTest, ActiveReadSessionThreadTest, LeadershipSessionConcurrencyTest, OutstandingCoreReadTest.p113*, ActiveCoreOwnerTest.p114*, P110InputCallbackTest sowie TaskModelTest.lockGenerationActionChangesRejectReloadAndWritePublication. Keine historische Gesamtsuite erneut ausgeführt.

## Restgrenzen und Entscheidung

Der isolierte normale Credentialpfad hat erstmals seine verschlüsselte `no_backup/pag-db-v1` angelegt und enthält ausschließlich die über normale Repositories erzeugte synthetische Aktion/Person/Task. Dieser Testbestand bleibt zur Reproduktion erhalten; kein neuer Produktionspersistenzmechanismus. Die synthetischen Harness-Fixtureroots wurden entfernt. Keine physische Löschgarantie behauptet.

Bereits ausgegebene Klartextstrings/DTOs lassen sich nicht rückwirkend unlesbar machen. Geprüft ist der Entzug weiterer autorisierter Zugriffe, Writes und Publications. Kein globales ART-/Native-/Framework-/IME-Wipereceipt und keine physische Speichergrenze aus den Engineeringquoten. Kein neues globales Endreceipt.

**Kein verbleibender reproduzierbarer Session-/Credential-Produktfehler im beauftragten Scope. READY_FOR_INDEPENDENT_CLOSURE – #39**, vorbehaltlich der ausdrücklich unabhängigen Schlussprüfung. #44 erhält diesen technischen Status, keine Releasefreigabe. **WIP; STOPP zum unabhängigen Review. Kein Merge/Release.**
