# §118 – F04 produktiv korrigiert, unabhängige Abnahme empfohlen

Basis `e82b6727f7023380f90d4a958970b3c0c0a6ea77`, Branch `p11-performance-optimization`. Start-HEAD und Arbeitsbaum geprüft, anfangs sauber. **Produktionscode geändert: ja. A/B/C sind im unten ausgewiesenen Geräteumfang GREEN. Empfehlung: unabhängige F04-Abnahme.** Keine eigenständige Schließung von F04 oder #40 und keine Releasefreigabe.

## Produktdiff

Der einzige ursprüngliche Ressourcenrecord verwaltet jetzt nacheinander frische, eindeutige Childbindungen. Alte Action-/Generations-/Context-/Consumerbindungen bleiben unverändert und dauerhaft widerrufen. Alle alten und neuen Kosten werden auf demselben Ledger kumuliert. Die Rootadmission wird nicht erneut geöffnet. [Implementierter Vertrag und PREPARATION-Umfang](owner-contract.md).

| Produktionsdatei | Änderung |
|---|---|
| `data/.../OutstandingCoreRead.kt` | Explizite Childadmission, vorgebuchte Bindungsmetadaten, geteilte kumulative Charges, eindeutige aktuelle Entry, permanenter Altbindungswiderruf, Zugehörigkeitsprüfung, Busy-/Cleanup-/Budgetfence. |
| `domain/.../ActiveCoreRead.kt`, `data/.../ProductActiveCoreLoader.kt` | `openOwned()` als ausdrückliche Childanforderung; normales `loadOwned()` bleibt Root-only. Frischer READY-Kontext und neues originales Mutation-/Readticket, bestehender Compact-Reader und Lifetime. Typisierte Öffnungsverweigerung. |
| `domain/.../Workspaces.kt`, `data/.../ProductWorkspaceRepository.kt` | Expliziter Nachfragegrund; PREPARATION-Fortsetzung ausschließlich über den neuen Abschnittsreader, ohne Root-/Workspace-Fallback. SQL und Payload in derselben autorisierten Transaktion. |
| `data/.../PreparationEntryStore.kt` (neu) | Phasenrichtiger begrenzter Einstieg: vollständige Familien bis 32 Personen / 32 Rollen, bei Bedarf bis 128 Tasks ohne Events; angeforderte skalare Summaries/Leitungsschutz. Counts und Schema12-Metadaten vor gemeinsamer Admission und DTOs. Größere Familien kontrolliert verweigert, keine abgeschnittene oder falsche leere Ansicht. Keine neuen fachlichen Schreib-/Textlimits. |
| `app/.../ProductViewModel.kt` | Bewusstes Öffnen und bestätigter Phasenwechsel verlangen eine frische Childbindung. Neue Consumeridentität; bestehende Präsentationen/Seiten/Details werden weiterverwendet. Automatischer zweiter Öffnungsversuch nach Refusal entfernt. |
| `data/.../OriginalWorkspaceAccess.kt` | Unveränderlichkeit der einzelnen Lesebindung auf dem Ressourcenrecord dokumentiert; keine Contextumbindung. |
| `data/.../OriginalCorePages.kt`, `FullviewReadExclusion.kt`, `ProductTaskRepository.kt`, `ProductWorkspaceRepository.kt` | DB-Cleanup-Unconfirmed-Signale sperren den gemeinsamen Record auch für spätere Children; ACK oder neue Generation heilt sie nicht. |

Writerbudget-Provenienz läuft über die aktuelle unveränderliche Entry und dieselben Ursprungszähler (`reserveHeld()`, bestehender `OriginalWriterBudget`). Writer-/CAS-/UNKNOWN-/Recoveryprüfungen bleiben zusätzlich notwendig. Keine neue Session-, Schlüssel- oder Writerautorität. R8 **8.388.608 B**, T16 **16.777.216 B**, 180s absolut / 30s READY idle und beide Reuseflags **false** unverändert. Quellen des getesteten Builds: [source-receipt.json](source-receipt.json).

## P77-Ergebnisse auf dem finalen Build

Samsung SM-M526BR/API33, ausschließlich isoliertes `.p77runtime`, synthetische Inhalte. A/B verwenden ausdrücklich synthetische P77-Authentisierung. C verwendet den normalen tatsächlichen `ProductCredentialAuthentication`-Pfad und manuelle Eingaben am Gerät.

| Szenario | Tatsächlich geprüft | Ergebnis / Rohbeleg |
|---|---|---|
| A DRAFT → PREPARATION → ACTIVE | Neue Aktion, bestätigte Phasenwechsel; anschließend Personen → Funktionen → Aufgaben → Hub. Tatsächlich sichtbaren Inhalt mit aktualisierten Android-Knoten und nötigem Scrollen prüfen; alte PREPARATION-Publication verweigert. | **PASS** [phase-final](continuation-phase-final.txt). Ein ursprünglicher PREPARATION-Rootworkspace, danach genau ein begrenzter ACTIVE-Childreader. R 2.022.627 B / T lokal 4.690.248 B. Keine zweite Rootmaterialisierung. |
| B ACTIVE → andere PREPARATION-Aktion | Originales ACTIVE-Taskdetail/Form und alte Header-/Row-/Writercallbacks halten; bewusst andere Aktion öffnen. PREPARATION mit **20 Personen / 40 Tasks**, Personen → Rollen → Aufgaben → Übersicht; alter Cross-Action-Publisher verweigert. | **PASS** [action-final-guard](continuation-action-final-guard.txt). Keine Fullworkspace-Materialisierung; neue begrenzte vollständige Familien. Nach B R 6.396.367 B / T lokal 13.937.632 B, alte Writeraufrufe 0. |
| C Lock → echte Reauth → dieselbe ACTIVE-Aktion | Zwei manuell bestätigte normale Android-Credentialanmeldungen, READY Generation **1 → 3**; alte Referenzen halten, dieselbe Aktion bewusst neu öffnen. Frisches Taskdetail bearbeiten/speichern; neue Revision **1** im aktuellen Modell sichtbar. | **PASS** [real-session-retry](credential-real-session-retry.txt): `usable=true`, `NEGATIVE_SAFETY_PASS`, `FRESH_WRITER_PASS`, abschließendes `P117 CONTINUATION PASS`. Vor Lock R 2.105.742 B / T lokal 4.881.975 B; nach erneutem Öffnen R 3.341.245 B / T lokal 8.783.359 B. Die alten Kosten bleiben gebucht. |
| Knappes Restbudget | Vorhandenes autorisiertes Ledger testweise auf **R8 − 300 B = 8.388.308 B** buchen; anschließend bewusst erneut öffnen. Padding ist synthetische Engineeringbuchung, keine physische Heapmessung. | **PASS** [budget-final](continuation-budget-final.txt): `CHILD_BUDGET_REFUSED`, zusätzliche Readeraufrufe **0**, zusätzliche Payloadzeilen **0**, Charges vorher/nachher identisch. |
| A→B→A mit gehaltenen Altaliasen | Nach obigem B erneut ACTIVE öffnen; alle alten Header-/Row-/Form-/Writer-/Publikationsreferenzen nochmals ausführen. | **PASS Sicherheitsumfang**: `ABA_BUDGET_SAFE`; der zusätzliche ACTIVE-Payload passt hier nicht mehr in die verbleibende gemeinsame T-Reservation. Kein positiver dritter Ansichtsaufbau behauptet. Die zusätzliche Bindung bleibt mit 100.000 B gebucht; Altberechtigungen bleiben tot. Positiver A→B→A-Aufbau bei ausreichender Reserve separat im Hosttest. |
| Writer / Action / UNKNOWN | Vorhandener gezielter P115-Gegenfall gegen den aktuellen Build. | **PASS** [authority-negative-final](authority-negative-final.txt): Pending und UNKNOWN verweigert; 1 ursprünglicher Core / 0 Fullworkspaces / 0 Klartextcallbacks. UNKNOWN ausdrücklich injizierte Protokollcompletion, kein tatsächlicher UNKNOWN-SQL-Commit. |

`T lokal` bezeichnet die kumulative lokale Engineeringreservation; das zentrale ursprüngliche T16-Obligo bleibt gehalten. Keine ART-/Native-Grenze oder tatsächliches Speicherretirement aus diesen Zahlen ableiten. Fallbezogene `run-*.json` enthalten die installierten APK-Hashes/Signaturen.

## Sicherheitsgegenproben und Host

**68 Hosttests PASS, 0 Fail/Error/Skip**, isolierte APK-/Test-APK-Builds PASS: [host-build.log](host-build.log), [XML](host), [Zählung](host-summary.json).

Gezielte Suiten: `OutstandingCoreReadTest` (25), `OwnerContinuationBudgetTest` (5), `ProductActiveCoreLoaderTest` (14), `ActiveReadSessionThreadTest` (5), `LeadershipSessionConcurrencyTest` (11), `P110InputCallbackTest` (3), `CompletedProtocolWorkspaceTest.p116*` (5). Der bisherige Rootverweigerungsvertrag einschließlich der §117-Gegenbelege bleibt unverändert als Regression erhalten; neue positive Children werden gesondert geprüft.

Neue Gegenproben: frische Generation mit alten ungültigen Contexts; positiver A→B→A-Childaufbau ohne Altgrant-Reaktivierung; Writerbuchung auf aktuelle Childprovenienz; fremder Ressourcenrecord nicht anerkannt; verlorener Main-Handoff / Pending-Supplement / UNKNOWN / unbestätigtes Cleanup fail-closed; kumulative **R8 und T16 jeweils exakt sowie +1 Byte**, Verweigerung vor Payload, keine Budgetrücksetzung/-freigabe.

Im echten C bleiben alte Header-, Row- und Formularreferenzen sowie die vorgebuchte produktive Writerhülle unwirksam (**0 Writeraufrufe**). Alter StorageContext verweigert Publication; alter Session-Keyowner verweigert alten **und** frischen Context vor Ausführung des Keyconsumers (**0 Aufrufe**). Die Writerhülle ist bewusst testseitig aus dem echten Formularfence gebaut, kein aus Compose extrahierter Savebutton. Der danach frisch autorisierte Writer speichert erfolgreich die neue Revision. Bestehende CAS-/Recovery-/P3b-Verträge und ihre §§109–117-Belege werden übernommen; keine historische Vollkampagne wiederholt.

Reproduktion des finalen Host-/Buildlaufs:

```sh
./gradlew -PpagTestBuildType=p77runtime \
 :data:testDebugUnitTest --tests '*OutstandingCoreReadTest' \
 --tests '*OwnerContinuationBudgetTest' --tests '*ProductActiveCoreLoaderTest' \
 --tests '*ActiveReadSessionThreadTest' --tests '*LeadershipSessionConcurrencyTest' \
 :app:testP77runtimeUnitTest --tests '*P110InputCallbackTest' \
 --tests '*CompletedProtocolWorkspaceTest.p116*' \
 :app:assembleP77runtime :app:assembleP77runtimeAndroidTest
```

## Fehlversuche und Grenzen ausdrücklich getrennt

- Die ersten beiden A-Treiber prüften einen Hubtext ohne nötiges Scrollen/Knotenrefresh. [Erster Lauf](continuation-phase.txt), [Diagnoselauf mit vorhandenem Inhalt](continuation-phase-diagnostic.txt) bleiben **FAIL** archiviert. Der Treiber übernimmt danach die schon in §116 verwendeten Refresh-/Scrollvorbedingungen und prüft denselben Inhaltsmarker; keine Produktlabels/Timeouts verändert. Finaler tatsächlicher Inhalts-/Navigationslauf PASS.
- Der erste zusätzliche Geräte-ABA-Aufbau verlangte trotz unzureichender Restreservation eine dritte Ansicht. [Lauf](continuation-action.txt): B selbst PASS, dritter Aufbau korrekt `BUDGET_REFUSED`; Gesamtversuch FAIL. Die endgültige ABA-Sicherheitsprobe verlangt weiterhin tote Altberechtigungen und akzeptiert ausschließlich Erfolg mit frischem Consumer oder eine explizite Budgetverweigerung. Kein drittes Ansichts-PASS behauptet.
- Die erste Paddingprobe las den Ledgerstand außerhalb des Main-Buchungsabschnitts; dazwischen konnten Composebuchungen entstehen. [Lauf](continuation-budget.txt) FAIL bei der Paddingvorbedingung. Im finalen Test werden Stand und Originalreservation im selben Main-Abschnitt ermittelt; keine Produktionskosten reduziert.
- [Erster echter Credentiallauf](credential-real-session.txt) wurde durch einen Host-Serverneustart am Ausgabekanal unterbrochen. Erstes READY und zweite Dialoganforderung sind belegt, Endergebnis **UNCONFIRMED**, kein PASS und kein nachgewiesener Credentialproduktfehler. Ein einziger vollständiger Wiederholungslauf wurde vom Nutzer ermöglicht; dessen unveränderte Authentisierung und Ergebnisse sind vollständig in `credential-real-session-retry.txt` enthalten. Die adb-Ausgabe wird seitdem direkt auf Datei geschrieben.
- Frühe Buildversuche wurden durch überlappende lokale Gradle/KSP-Ausgaben gestört; ausschließlich generierten Cache gesichert und Daemons beendet, danach seriell erfolgreich gebaut. [Versuchslogs](attempts). Keine Produktdaten und keine Sicherheitsgrenzen zurückgesetzt.

Der PREPARATION-Child ist absichtlich ein begrenzter vollständiger Abschnittseinstieg. Größere Familien als der dokumentierte Scope sind **nicht positiv abgenommen** und werden verweigert. COMPLETED-Fortsetzung, F05/F06/F07 und deren weitere Produktpfade werden durch diesen F04-Nachweis nicht freigegeben. Endliche kumulative Budgets können weitere bewusst angeforderte Öffnungen verweigern; Reauth schafft keine neue Reserve.

Nur isolierte Test-APKs installiert; [Signaturen/Paketnachweis](install.json), [Cleanup und geschützte Pakete](cleanup.json). Geschützte Produktpakete unverändert, temporäre Harnessfixtures entfernt. Normaler Credential-Testbestand bleibt verschlüsselt mit ausschließlich synthetischen Daten erhalten; keine Wipebehauptung. **TalkBack NOT TESTED**, keine TalkBack-Instrumentierung oder Accessibility-Abnahme.

**Nächster Schritt:** unabhängige Prüfung dieses Produktdiffs und der A/B/C-Nachweise. F05, F06, F07 bleiben OFFEN und außerhalb dieses Pakets. Danach die vereinbarte Reihenfolge F05/F06, F07 ohne TalkBack, anschließend vollständiges Sicherheitsaudit #46. **STOPP zum unabhängigen Review. Kein Merge, Tag, Release, Produktupdate oder eigenständiges Issue-Schließen.**
