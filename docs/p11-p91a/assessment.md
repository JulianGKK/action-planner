# §91a – 8+16 MiB / Option 2 technisch übernehmen

Basis `0f0c726e856944e2d7b68c2886d50ca73fd118fd`, Branch
`p11-performance-optimization`, Issues #33/#34/#37. WIP zur unabhängigen Prüfung;
keine Freigabe schneller Navigation, kein Merge/Release.

## Verbindlicher Umfang

Die ausdrückliche Produktentscheidung ersetzt für die aktuelle Weiterarbeit den
bisherigen 8+8-Vertrag: **Retained 8.388.608 Bytes, Transition 16.777.216 Bytes**.
Die harten Buchungsgrenzen gelten ausschließlich für vollständig vom App-Owner
kontrollierte Daten und Zwischenstände. Alte/neue Daten, Builder, Worker-/Main-
Handoffs und Formularzustände erhalten keine kostenlosen Nebenbudgets.
Höchstens ein Übergang; keine zweite Payload-Registry oder Sessionautorität.

Android, Compose, Textlayout, Render, IME, Accessibility und Native-Ressourcen
sind **separat** zu beurteilen. Sie sind weder kostenlos noch nachweislich
vollständig freigegeben. Die Engineering-Gewichte sind keine zertifizierten
ART-Objektgrößen, keine physische Gesamt-RAM-Obergrenze und kein OOM-Nachweis.
Historische Messungen und das negative Consumer-End-Ergebnis aus
[§90a](../p11-p90a/assessment.md) bleiben bestehen. Option 2 verändert den
Budgetumfang, liefert aber keinen neuen Endnachweis für app-kontrollierte Werte.

## Umsetzung und Verwendungsaudit

| Stelle | Neue Prüfung / unveränderte Sicherung |
| --- | --- |
| `ActiveWorkspaceBudget` | Explizite `RETAINED_LIMIT_BYTES` / `TRANSITION_LIMIT_BYTES`; Admission anhand des gebuchten Slots; gleiche Overflow-/Sticky-Refusal-/Seal-Regeln. |
| `promote()` | Versiegelter Übergang über 8 MiB wird vor jeder Mutation abgelehnt. Alte Retained-Buchung und Übergang bleiben vollständig erhalten; kein neuer Übergang möglich. Erfolgreiche Übernahme bis 8 MiB bleibt möglich, setzt weiterhin separat belegte alte Referenzenden voraus. |
| `CoreReadAdmission` | Gemeinsame Aggregation beider Pläne plus disjunktem Overhead vor Materialisierung. Ein synchrones Ledger, keine lebenden Tickets über Suspend-Grenzen. |
| `OutstandingCoreRead` | Exklusiver vollständiger 16-MiB-Übergangsumschlag; Receipt R ≤ 8 MiB, T ≤ 16 MiB. Exponierte Werte, nicht quittierte Handoffs und unbestätigtes Cleanup sperren weiterhin neue Reads. |
| `ProductViewModel` | Candidate-Receipt getrennt R8/T16 prüfen. Keine neue Publication-/Retention-Berechtigung. |
| Bounded-/Compact-Personen-/Task-Reader | Einzelwert-/Zeilenzahl-/SQL-Blob-Fetch-Schutzgrenzen bleiben konservativ bei **Retained 8 MiB**. Eine spätere gehaltene Einzelrepräsentation darf nicht allein wegen T16 größer werden. SQL-SUM-Overflowbegründung unverändert. |
| Hosttests / P84-Probe | Receipt- und exklusive Envelope-Erwartungen rollenbezogen; P84-Ausgabelabel aus der Transition-Konstante statt historischem Literal. Historische Rohbelege unverändert. |

`LIMIT_BYTES` entfällt vollständig. Dokument-/Bild-/PDF-Budgets bleiben unabhängig
und unverändert. Keine Schema-, Crypto-, Session-, Writer-, Revoke- oder
Storage-Änderung. `reuseEnabled`, `ramReuseEnabled`, Produktnavigation und
Full-Workspace-Fallback werden nicht aktiviert. Das bestehende unvollständige
Owner-/Form-/Presentation-Gesamtmodell wird durch größere Reader-Admission nicht
als vollständig integriert oder abgenommen behauptet.

## Gezielte Prüfung und Rohbelege

Die neuen Ledger-Tests prüfen R = 8 MiB / +1 Byte und T = 16 MiB / +1 Byte,
T = R+1 und T = 16 MiB ohne erlaubte Promotion, unveränderte alte/neue
Buchungen nach Ablehnung und weiterhin höchstens einen Übergang. Die bisherigen
Overflow-, Sticky-Refusal-, Sealing-, Foreign-Ticket- und terminalen Close-Fälle
bleiben erhalten. Der neue `CoreReadAdmissionBudgetTest` prüft beide exakten
Slotgrenzen vor Materialisierung, disjunkte Plan-/Builder-/Form-Overheads,
Overflow und die getrennte Receipt-Prüfung im exklusiven Outstanding-Read.
Die bestehenden Owner-/Readtests prüfen Revoke, UNKNOWN, Writer-Races, späte
Callbacks, verlorene Main-Handoffs und unbestätigte Cleanup-Enden weiter.

Die ersten Läufe zeigten einen fehlenden Import im neuen Test und genau eine
veraltete 100-Rollen-Refusal-Erwartung. Import korrigiert; der Rollenfall prüft nun
vollständige Gruppen/Pages auch bei 100 Rollen. Keine Produktionssperre dafür
entfernt. Rohlogs: [Compile-Erstlauf](host-tests-initial-compile-failure.log),
[betroffene Hostklassen einschließlich alter Erwartung](host-tests-initial-expectation-failure.log).
Der gezielte Nachlauf und die Builds stehen in [verification.log](verification.log).
Reproduktion: [commands.sh](commands.sh).

Abschließende Testfallbilanz nach dem gezielten Nachlauf: **153 PASS, 0 FAIL,
1 historischer 129k-Fall SKIPPED**, 154 unterschiedliche Testfälle aus elf
betroffenen Klassen. Der Nachlauf ersetzt die korrigierte alte 100-Rollen-Erwartung; ein zusätzlicher
ViewModel-Fall prüft korrekt geschätzte Receipts exakt bei R8/T16 und jeweils +1 Byte
([Log](viewmodel-boundary.log)). Die übrigen bereits erfolgreichen Tests wurden nicht erneut
ausgeführt. [Erstlauf-XML](host-xml/), [Nachlauf-XML](host-rerun-xml/),
[zusammengeführte Bilanz](test-summary.json). Es wurde keine Gesamtsuite ausgeführt.

### Große Fixture und Rollen gesondert

Echte Host-SQLite-Reads, 200 Personen / 1.000 Tasks / **5.000 gespeicherte Events**,
mit Unicode-Erweiterung `Ä界🦋` in der Actionbeschreibung:

| Rollen | Retained-Receipt (Bytes) | Transition-Receipt (Bytes) | Reader-Entscheidung |
| --- | ---: | ---: | --- |
| 6 | 1.605.212 | 6.397.040 | ADMITTED |
| 32 | 1.819.356 | 7.253.616 | ADMITTED |
| 100 | 2.375.968 | 9.480.064 | ADMITTED unter R8/T16 |

**100 Rollen: nur numerischer Reader-PASS.** Der gesamte Rollen-Core wird hier
materialisiert; das ist keine UI-, Latenz- oder Ressourcenabnahme und aktiviert
keinen Produktzugriff. Rollen-/Mitgliederpaging und der selektive Editor bleiben
separate Testpfade. Die größere Transitiongrenze allein beschließt weder eine
neue Katalogstrategie noch schnelle Navigation.

Die unveränderte 100-Task/100-Event-Fullboard-Fixture kostet T = 8.410.240 Bytes,
nach einem Statuswechsel 8.447.616 Bytes: unter T16 jetzt zugelassen, kein
STORAGE_FAILURE/BLOCKED. Überlange, fachlich gültige Texte werden weiterhin vor
Payloadfetch abgelehnt. Der kombinierte Reader erreicht mit langen Texten exakt
T = 16.777.216 Bytes (R = 4.200.256), nächstes ASCII-Byte verweigert. Andere
Reader bleiben beim letzten über 80-Byte-Schritte darstellbaren Wert unter T16;
die exakten +1-Byte-Grenzen werden separat ohne diese Schrittweite geprüft.

### Alte und neue Daten gleichzeitig

Die Überlappungsfixture hält beide tatsächlichen Core-Graphen stark referenziert:
altes R = 4.199.992, neues T = 16.776.160, zusammen **20.976.152 Bytes**.
Einzelne Reader-Pässe sind also weiterhin **keine gemeinsame Owner-Admission**.
Der echte `ProductActiveCoreLoader` verweigert den zweiten Read vor neuem SQL;
Readcount bleibt 1, auch nach `compositionDisposed()` und Lifetime-Close.
Die gesamten 16 MiB bleiben exklusiv reserviert, Retirement unbestätigt.
Das ist der erhaltene Schutz und keine neue Reload-/Reuse-Freigabe.
Kein 129k-Standardlauf und keine neue Android-/ART-/Native-/FD-/PSS-Messung.
Die numerische Änderung hat einen direkten Host-Testvertrag; ein Gerätetest würde
keinen zusätzlichen Beweis für die neuen Slotgrenzen oder Consumer-Enden liefern.

Builds: `:app:assembleP77runtime`, `:app:assembleP77runtimeAndroidTest` und
`:app:assembleRelease` **PASS**, einschließlich Release-Lint-Vital; siehe
[verification.log](verification.log). Bestehende Kotlin-Warnungen bleiben sichtbar.
Keine Installation, kein Release und kein neuer Gerätelauf.

## Ergebnis und Reviewgrenze

Die Produktentscheidung ist in den bestehenden Budget-/Admission-Seams technisch
übernommen. Retained wurde **nicht** auf 16 MiB erhöht. Abgelehnte Promotion lässt
die alte Buchung und beide Ticketidentitäten erhalten; keine Payloadmutation.
Gemeinsame Vorabzulassung und alle bisherigen End-/Cleanup-Sperren bleiben bestehen.
Offen bleiben die vollständige Owner-/Form-/Presentation-Integration und die
separate Framework-/Native-Ressourcenbewertung aus #33/#34/#37. Die übernommenen
Engineering-Gewichte sind weiterhin zu kalibrieren. Keine neuen Touch→OnDraw-
oder physischen Speicherbehauptungen aus Host-Zeiten ableiten.

WIP pushen; danach **STOPP zum unabhängigen Review**, kein Merge/Release.
