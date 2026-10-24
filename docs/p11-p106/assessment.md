# §106 – Fullview-Eintritt: wirksame Überlappungssperre, Gesamtzulassung BLOCKED

Basis: `0f7d38b178706b3f5e9ffe8c498d5342c9e4ef67`, Branch `p11-performance-optimization`.

**Ergebnis: BLOCKED, keine Abnahme von §106 und keine Schließung von #37.** Dieser WIP behebt den §105-Bypass bei vorhandenem ursprünglichem Core funktional. Er implementiert **noch keine vollständige gemeinsame Fullview-/Formular-Admission**. Insbesondere ist `FullviewEntry` ausdrücklich ein Ausschluss-Token und **kein R8/T16-Receipt**. Ein erster Legacy-Fullview ohne alten Core bleibt unzureichend budgetiert. Dieser Restfehler wird weder durch grüne Tests noch durch die neue Sperre als behoben ausgegeben.

## Tatsächlicher Produktdiff

`OutstandingCoreRead.product` ist der gemeinsam verwendete ursprüngliche Record für normale Runtime- und direkte Repository-Eintritte. Runtime-close leert dessen ungeklärte Coreverpflichtung nicht. Der Loader schließt seinen lokalen Zugriff, ohne den gemeinsamen Record durch einen neuen zu ersetzen. P77-Fixtures injizieren ausschließlich ihren eigenen synthetischen Originalrecord; dies ist kein produktiver Resetpfad.

`ProductWorkspaceRepository.read` prüft den Originalrecord vor Workerqueue/Key/Room. Ein pending/exponierter Core oder ein tatsächlich noch laufender Fullview-Worker/Main verweigert mit `WorkspaceFailure.OWNER_UNCONFIRMED`. `WorkspaceStore.read` verlangt zusätzlich die passende Original-Eintrittsidentität, Action/Generation und einen aktiven Request vor dem ersten vollständigen Boardaufbau. Ein direkter Storeaufruf ohne Token wird verweigert. Das Token bescheinigt **keine Größenprüfung**.

Direkte Personen-, Operations-, Task-, Material-, Kosten-, Protokoll- und Gedächtnisprotokoll-Leseports verwenden `FullviewReadExclusion` mit demselben Record. Taskhistory behält ihre ursprüngliche begrenzte Paginglogik und erhält davor die gemeinsame Überlappungssperre. Keine weitere Materialisierung neben einem ungeklärten Core über diese Ports. Writerkörper und deren Commit-/UNKNOWN-Verträge werden nicht neu implementiert.

Die UI erhält einen unterscheidbaren Refusalgrund und zeigt eine verständliche Verweigerung an; die Ablehnung wird nicht als EMPTY oder erfolgreicher Fullview angezeigt. Bestehende Fachablehnungen bleiben getrennt.

## Referenz- und Endvertrag

`Originalrecord → FullviewEntry → einmalige Worker-/Main-Shell → lokaler Originaldispatch → WorkspaceResult/Snapshot/UI/Form`.

Die Entry enthält Metadaten und Dispatchverbindungen, keinen zweiten Payloadcache. Shells übernehmen ihren Callback einmal atomar und lösen ihre gespeicherte Verbindung vor dem Dispatch. Worker/Main quittieren erst nach tatsächlicher Rückkehr im `finally`. Bekannte Queueablehnung darf einen nicht gestarteten Dispatch quittieren; eine falsche Ablehnung während eines gestarteten Aufrufs darf dessen Ende nicht vortäuschen. Verlorener Main bleibt gesperrt. Alte ACKs verändern keine neue Identität. Lokale Runnablereferenzen, Snapshot-/Form-/Writer-Captures und bereits ausgegebene Werte sind damit **nicht global beendet**.

Nach beendetem Fullviewdispatch kann der Legacy-Fullviewpfad weitere Fullviews lesen, obwohl ausgegebene Snapshots noch existieren können. Das ist der verbleibende unbudgetierte Pfad, **kein belegter sicherer Übergang**. Die sticky Fullview-Sperre gegen einen neuen Core bleibt erhalten. `state.retirementConfirmed` bleibt die alte Core-Diagnose und ist kein Fullview-Endreceipt.

## Nachweise und Fehlversuche

Gezielte Hosttests: [Zusammenfassung](host-summary.json), XMLs unter `host-results/`; **159 Tests, 0 Fehler**. Davon 7 neue Ausschluss-/ACK-Gegenfälle, 16 bestehende Outstandingfälle sowie 136 Writer-/Revisions-/Editorregressionen. [Hostlauf](host-10.log), [Writer-/Editorlauf](writer-editor-regression-08.log). Diese belegen keinen gemeinsamen Fullview-Größenvertrag. Der Filter `*ActiveWorkspaceBudgetTest` lieferte keine zusätzliche Testsuite; daraus wird kein neuer Grenzwertnachweis abgeleitet.

Betroffene P77-App-/Testbuilds: [check-07](check-07.log), [korrigierter Probebuild](probe-build-09.log). Frühere Buildfehler bleiben dokumentiert: geänderte Loader-Konstruktorauflösung für bestehenden trailing-lambda-Aufrufer, fehlende/nichtnullable P77-Observation. Alte Konstruktorform wiederhergestellt, explizite No-op-Observation ergänzt. Logs `build-01`, `host-03`, `build-04`; erfolgreiche Folgebuilds daneben.

Gerät: ausschließlich vorhandenes isoliertes P77-Paket, echter ProductRuntime-/Session-/Repository-/SQLCipher-/Room-Pfad, **synthetische P77-Autorisierung**, kein echter DeviceCredential-Nachweis. [Probequelle](../../app/src/androidTest/kotlin/org/pag/prototype/P106FullviewEntryProbe.kt), [geschütztes Ausführungsskript](run-device.py).

- [Erster Exposed-Lauf](device-exposed-01.txt): sichtbare Verweigerung, direkte Repositories und fehlender Storeeintritt bereits bestätigt; danach **Harnessfehler** `NoSuchFieldException(worker)`, daher Gesamtlauf FAIL. Kotlin-lazy-Feldzugriff auf den Delegate korrigiert, kein Produktfix daraus abgeleitet.
- [Wiederholung](device-exposed-02.txt): Gesamtlauf **FAIL**: nach erneut bestätigter Überlappungsverweigerung erreicht der neue synthetische Runtime im Harness nicht READY (Timeout). Der Runtimewechsel ist damit **nicht geräteseitig bestanden**. Kein Produktfehler/Autorisierungsbruch daraus abgeleitet; Ursache nicht abschließend isoliert.
- [Legacy PREPARATION/COMPLETED](device-clean-01.txt): beide weiterhin zugänglich, je 20 Personen und gespeicherte Schema-12-Expected-Revisionen. Explizit `budget_ADMITTED_NOT_CLAIMED=true`; **kein positiver Admissionnachweis**.

APK-/Signer-, Geräte-, geschützte Paketinventar- und Fixturecleanup-Nachweise liegen als `resume-*` daneben. Keine Produktdaten zurückgesetzt, kein `pm clear`, nur synthetische Fixturedateien bereinigt. Keine Performance-/Native-/PSS-/FD-Kalibrierung in diesem Schritt.

## Budget und vollständige §106-Matrix

R8 **8.388.608**, T16 **16.777.216 Bytes**, unverändert. Der Core hält seine ursprüngliche exklusive T16-Verpflichtung. Daneben wird jetzt für die erfassten Ports **keine zweite Fullviewmaterialisierung begonnen**. Für den ersten Legacy-Fullview existiert weiterhin **keine gemeinsame Vorabgrößenrechnung** für Boards, Summary/Timeline/Material/Expense, Reader/Builder, alte/neue Snapshots, Schema-12-Expected, Drafts und Writer-Captures. Unbekannte Kosten werden nicht als null verbucht; es wird überhaupt kein ADMITTED behauptet.

| Anforderung | Produktpfad / Test / Rohbeleg | Ergebnis |
|---|---|---|
| Exponierter/pending Core → Fullview vor Materialisierung verweigern | Originalrecord, Repo-/Store-Gate; P106 exposed und Host | PASS für geprüfte Eintritte |
| Direkte Board-/Detailports dürfen VM-Gate nicht umgehen | gemeinsame ReadExclusion, P77 direkte persons/operations/tasks; Store ohne Token | PASS getestete Ports; weitere Seiteneffektbereiche nicht als vollständige Appzulassung erklärt |
| Aktiver Fullview Worker/Main, verlorener Main, doppelte/falsche/alte ACK | Originalentry/OnceTask; sieben Hostfälle | PASS lokaler Dispatchvertrag |
| Laufender alter Core-Resolver / laufender Writercallback | Coreverpflichtung bleibt sperrend; Writercallback-Ende nicht integriert | BLOCKED vollständiger Writer-/Formscopevertrag |
| Runtime-/Generationwechsel hebt Coreverpflichtung nicht auf | gemeinsamer Originalrecord; P77 replacement | **BLOCKED** Gerätegegenprobe: replacement-READY-Timeout; kein Activity-/Prozess-Endbeweis |
| Cancel, ungültige Identität/Generation, Foreignentry, fehlender Storetoken | Host + direkter Store-Gerätefall | PASS benannte Gatefälle; kein Budgetprüfungs-Cancelnachweis |
| Vor jeder vollständigen Materialisierung gültiger R8/T16-Receipt | erster Legacy-Fullview weiterhin ohne Receipt | **BLOCKED** |
| Exakt R8/T16, +1, Metadatenoverflow, Prüfung vor DTOs | kein Fullview-Metadaten-/Receiptproducer implementiert | **BLOCKED**, keine Ersatzfreigabe aus Coretests |
| Kleine/größere erste Fullviews sicher zugelassen | Legacy kleine PREP/COMPLETE funktional, aber unbudgetiert | **BLOCKED** |
| PREPARATION/COMPLETED weiterhin erreichbar | P77 clean | PASS Bedienbarkeit; BLOCKED gemeinsame Budgetzulassung |
| Personen-/Plan-/Taskeditor, CAS/TaskEvent/UNKNOWN/Recovery | 136 relevante Hostregressionen, passende §§102–105-Belege unverändert | PASS bestehender Teilvertrag; keine neue vollständige Geräteabnahme |
| Lock/Revoke/Action/UNKNOWN keine späte Publication | originale c.publish/Request-/Mutationgates erhalten; bestehende Regressionen | keine globale neue §106-Abnahme; fehlende komplette Geräte-Racematrix BLOCKED |
| Snapshot/Form/Writer-Alt-/Neudaten vollständig gebucht und beendet | keine gemeinsame Formallokations-/Endseam | **BLOCKED** |

## Kleinster Restvertrag und Entscheidungsvorlage

Es fehlt ein **vor dem ersten DTO/Builder ausgestellter, am selben Originalrecord gebuchter Fullview-/Form-Receipt**, der sämtliche gleichzeitig möglichen app-eigenen Kopien konservativ einschließt. Zusätzlich fehlt die Begrenzung der tatsächlichen Ausgaben an Form-/Writer-/UI-Scopes. Die neue Ausschlussidentität ersetzt keinen dieser beiden Verträge.

Konkretes Gegenbeispiel: ein ausgegebener WorkspaceSnapshot und ein offener Editor halten Expected/Draft; ein weiterer zulässiger Legacyread baut einen neuen vollständigen Snapshot. Der vorige Main ist zurückgekehrt, seine Entry also quittiert, aber der alte Snapshot/Editor ist deshalb nicht beendet. Hinzu kommen formseitige neue Texte/Inputs und Callback-Captures ohne gemeinsame Allokationszulassung. Ein pauschaler fester „Formscratch“-Wert ist ohne begrenzte Erzeuger keine belegte Obergrenze.

**Sichere eingeschränkte Alternative, noch nicht aktiviert:** Jeder Read ohne vollständigen gemeinsamen Receipt verweigert vor Materialisierung. Dies würde derzeit auch erste PREPARATION-/COMPLETED-Fullviews und deren Bearbeitung treffen. Dafür verlangt der Auftrag ausdrücklich eine Produktentscheidung; dieser WIP nimmt sie nicht stillschweigend vor.

**Funktionserhaltender Restauftrag:** am selben Originalrecord skalare SQL-Metadaten vor DTOs erheben, Projektion und tatsächliche Reader-/Builder-/Handoffkosten vorab buchen; jede Form-/Expected-/Draft-/Writerausgabe vor Kopie zulassen; alte Snapshots weiter buchen, solange keine tatsächlichen Enden belegt sind. Wo ein Budgetende erreicht wird, eine ausdrückliche UX für Verweigerung/Entwurferhalt vorsehen. Ein unbegrenzt wachsender Form-/Capturepfad kann nicht allein durch ein einmaliges Fullviewtoken als endlich deklariert werden. Ob begrenzte Draftgrößen oder die Verweigerung weiterer Fullview-/Editorübergänge Bestandteil von 1.0 sein dürfen, ist konkret zu entscheiden, falls diese Einschränkung erforderlich wird. Es wird keine neue fachliche Textgrenze eingeführt.

Die vorliegende Änderung ist deshalb ein **Teilfix und Review-WIP**, keine vollständige Erfüllung des Auftrags. #37/#33 bleiben offen; insbesondere ist auch die gerade erhaltene erste Legacy-Fullview-Ausgabe noch nicht sicher gemeinsam zugelassen.

## Unverändert

`reuseEnabled=false`, `ramReuseEnabled=false`; exponierte Core-Retirement-/Readmissionssperre bleibt geschlossen. Keine neue Session-/Keyautorität, kein Cache, keine Schema-/Cryptoänderung, keine Änderung der Writer-/UNKNOWN-/Recovery-/CAS-Regeln. Keine physische Wipebehauptung. Framework-/Compose-/Textlayout-/Render-/IME-/Accessibility-/SQLCipher-Nativekosten getrennt nach Option 2; weder kostenlos noch als vollständig beendet bewertet.

**STOPP zum unabhängigen Review. Kein Merge/Release.**

### Bereinigungsgrenze der fehlgeschlagenen Probe

Die abschließende Cleanupprüfung fand nach dem fehlgeschlagenen Runtimewechsel eine synthetische Restfixture `p11-fixture-c365bbc3-2fab-4c32-9d4a-cc0a8baa315f`. Daher wird **kein vollständig erfolgreicher automatischer Cleanup aller Geräteläufe** behauptet. Nach Force-stop ausschließlich des isolierten P77-Pakets wurde genau dieses synthetische Fixtureverzeichnis explizit entfernt; die anschließende Skriptprüfung bestätigte keine Fixturewurzeln und unveränderte geschützte Paketinventare. Kein Produktpaket und kein Produktalias wurden gelöscht. Das ist manuelle Testharnessbereinigung, kein Beweis eines sicheren produktiven Cleanup-/Owner-Endvertrags.
