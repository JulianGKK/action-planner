# §118 – implementierter Fortsetzungsvertrag

Basis: `e82b6727f7023380f90d4a958970b3c0c0a6ea77`. Die Entscheidung aus §118 wird produktiv umgesetzt; kein zweiter Ressourcenowner und kein Endreceipt.

## Ressourcen und Bindungen

`OutstandingCoreRead.current` bzw. `fullviewObligation` bleibt der erste Ressourcenrecord. `begin()` und `beginFullview()` bleiben nach Exposure ausgeschlossen. Eine explizite Öffnung verwendet stattdessen `continueRead()` (begrenzter ACTIVE-Core) oder `continueSection()` (PREPARATION). Ein fehlgeschlagenes Childadmit fällt niemals auf eine Rootadmission zurück.

Jeder Aufruf erzeugt nach Vorbuchung eine neue Objektidentität. `continuation`/`activeEntry` halten ausschließlich die aktuelle Bindung; es gibt keine Liste alter Payloads, keine zusätzliche Quote und keine Snapshotregistry. Die erste Entry bleibt als Ressourcenprovenienz erhalten. Alle nachfolgenden Entry-Charges delegieren auf deren kumulative R-/T-Zähler. Ein ACK beendet ausschließlich den Worker/Main-Handoff. Es zieht keine Charges ab. Auch abgebrochene bzw. unveröffentlichte Children werden nicht vom gemeinsamen Ledger abgezogen.

Die alten Entry-Bindungen werden unwiderruflich `bindingRevoked`. Ihr Action-/Generationswert wird nicht überschrieben. A→B→A erzeugt eine dritte Bindung, keine Reaktivierung der ersten. `ownsFullview()` prüft zusätzlich die Zugehörigkeit zum konkreten ursprünglichen Record; ein fremdes Ledger wird nicht anerkannt.

Die fixe Vorbuchung `bindingCost` umfasst Bindung, Ticket-/Request-/Queue-Metadaten, Capability sowie begrenzten SQL-Kontrollscratch. Wird erstmals ein Ressourcenhandle für einen bereits exponierten Compact-Read benötigt, wird auch dieser vor seiner Konstruktion zusätzlich gebucht. Die spätere Payloadreservation wird vollständig und zusätzlich gegen dieselben R8-/T16-Grenzen geprüft. Lokale Reserve ist kein neues zentrales T16-Ticket.

## Autorisierung, Reader und Publication

`ActiveCoreLoadPort.openOwned()` ist der ausdrückliche Öffnungspfad. Normales `loadOwned()` bleibt Root-only und darf eine Verweigerung nicht automatisch in eine Fortsetzung umdeuten. Der ViewModel verwendet den expliziten Pfad beim bewussten Öffnen und nach bestätigter Aktionsänderung. `WorkspaceReadRequest.explicitOpening` ist nur ein Nachfragegrund, keine Berechtigung.

Der Loader beschafft einen aktuellen READY-Kontext. Die vorhandene Mutation-/Writerinstanz muss eine neue eindeutige Readticketidentität zulassen. Pending/UNKNOWN verweigern weiterhin. Der normale Runtime-Keyowner wird erst im Worker abgefragt. Der alte StorageContext oder Keyowner wird nie in eine neue Bindung übernommen. Der ACTIVE-Reader bleibt der vorhandene `ProductActiveCoreReader`/`ActiveCoreStore` mit unveränderten ACTIVE-Prüfungen, 32er-Seiten und gemeinsamer SQL-Metadaten-/Payloadtransaktion.

Der ViewModel vergibt auch eine neue Consumeridentität. `OriginalWorkspaceAccess` und `ProductActiveCoreLifetime` behalten je ihre unveränderliche Session-/Readbindung. Publication prüft weiter READY, Generation, Request, Aktion/Status, Readticket und Writer-/Mutationsepoche. Alte Header-/Row-/Form-/Writerreferenzen können weder den neuen Consumer noch dessen frische Autorisierung borgen. Der frühere automatische zweite Öffnungsversuch nach einem verweigerten kombinierten Read entfällt.

Ein neuer Child darf erst beginnen, wenn der bisherige Worker/Main-Handoff und DB-/Keyumfang beendet sind und kein Supplement in flight ist. Unsicheres DB-Cleanup vergiftet den gemeinsamen Ressourcenrecord über die vorhandene Unconfirmed-Fence; ein Generationswechsel ersetzt dieses Signal nicht. DB-Cleanupbestätigungen aus Workspace-, Seiten-/Detail-/Supplement- und direkten Fullview-/Historypfaden werden hierfür weitergereicht. Kein GC, Dispose, OnDraw oder Runtimewechsel bestätigt Retirement.

## Begrenzter PREPARATION-Einstieg

`PreparationEntryStore` ist ein eigener Reader, keine umetikettierte ACTIVE-Projektion. Er liest im selben autorisierten Room-Readtransaction-Kontext zuerst festen Status und skalare Membershipcounts. Zulässiger Einstiegsumfang:

- vollständige Personen-/Rollen-/Planfamilie bei höchstens 32 Personen und 32 Rollen;
- nur bei ausdrücklichem Bedarf eine vollständige Taskfamilie bei höchstens 128 Tasks; keine Eventhistorie;
- ausschließlich angeforderte skalare Material-/Kosten-/Dokumentzusammenfassungen und Leitungsschutzstatus.

Dies sind Grenzen dieses vollständigen Abschnittsreads, keine neuen fachlichen Schreib-/Textlimits. Größere Familien werden vor ihren DTOs verweigert, niemals abgeschnitten oder als leer/vollständig ausgegeben. Die vollständigen Familien innerhalb dieses Umfangs erlauben den unveränderten PREPARATION-Consumern exakte Membership und Zusammenfassungen. Material-/Kostenboards, Protokolltimeline, Memorypayload und Dokumentpayload werden hier nicht als zweiter Workspace vorgeladen. Vorhandene autorisierte Detail-/Membership-/Supplementpfade bleiben separat.

Nach Counts prüft `FullviewPreflight` die tatsächlichen Schema12-Zeilen/Textkosten. Erst nach gemeinsamer Reservation beginnt die Konstruktion über die vorhandenen `OperationsStore`-/`TaskStore`-/Summaryproduzenten. Ein Refusal führt zu einem Fehlerzustand, nicht zu CONTENT/EMPTY. Es gibt keinen Rückweg in `WorkspaceStore` nach fehlgeschlagenem Fortsetzungsadmit. Der bereits vorhandene erste PREPARATION-Rootread in A ist keine Fortsetzung und bleibt der einzige dortige Rootread.

## Writerbudget

`OriginalWriterBudget` verwendet unverändert den aktuellen READY-Kontext und die Originalreservation. `reserveHeld()` ordnet die Buchung jetzt der aktuellen unveränderlichen Childentry zu und verlangt deren Liveness sowie Action/Generation. Ihre Charges landen auf dem ersten Ressourcenrecord. Die Entry selbst ist kein Writergrant: Pendingticket, DB-Preflights, Schema12-CAS, UNKNOWN und Recovery bleiben zusätzlich erforderlich. Alte Writercallbacks werden nicht mit neuen Writerreferenzen ausgestattet.

## Unverändert

R8 = 8.388.608 B; T16 = 16.777.216 B; 180s absolut / 30s READY idle; `reuseEnabled=false`, `ramReuseEnabled=false`; keine neuen Schlüssel, Migrationen, Autoritäten oder fachlichen Textlimits. Kumulative Kosten bedeuten weiterhin eine endliche Zahl zulässiger Öffnungen, insbesondere bei absichtlich gehaltenen Altaliasen. Erschöpfung wird kontrolliert verweigert und nicht durch Reauth oder einen unsicheren Freigabenachweis geheilt.
