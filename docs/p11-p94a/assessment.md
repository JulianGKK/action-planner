# §94a – kompakter HUB am bestehenden Core-Owner

Basis `79958ba610fcbaad5a2319a692c5f31940443fb8`, #37/#34. WIP, kein Merge/Release.

## Umsetzung und Lebensdauer

Die HUB-Verzweigung in `ActiveWorkspace` liest keinen `activeCore` mehr.
`CompactActiveContent(CompactHubPresentation, …)` erhält keinen vollständigen Core,
keine alternative Payloadregistry oder Session-/Schlüsselautorität. FUNCTIONS,
Editoren, Fullview und deren bisherige Übergaben bleiben unverändert.

Genau ein widerrufbares HUB-Handle je originalem CoreSlot, neben PERSONS/TASKS.
Es besitzt eine **einzige feste Liste mit drei Metadaten-HubCards**: PERSONS,
FUNCTIONS, TASKS. Diese enthalten nur die bisherigen Routen, eine gemeinsame leere
Summaryliste und keine aufgelösten Daten. Kein Card-/Summary-/Corecache und keine
zweite Kartenliste. Der neue indexbasierte Renderer erzeugt weder `chunked()`-
Zeilenlisten noch eine zusätzliche Ergebnisliste. Kartenraster, Beschriftungen,
Abstände, Mindesthöhe und Navigationsziele entsprechen dem bisherigen kompakten HUB.
Die fünf bisherigen zusätzlichen Navigationsbuttons bleiben Buttons, keine neuen Karten.

Nur sechs skalare Werte aus dem Owner: Personen gesamt/vor Ort; Anzahl Funktionen
und Personen vor Ort; Aufgaben offen/in Arbeit. Insbesondere bleibt die
Funktionszahl `core.functions.items.size`, nicht eine neue fachliche Definition.
Summarystrings werden direkt für `Text` formatiert, nie in Karten gespeichert.
Die bestehende `activeCompactHubCards(core)`-Übergabe entfällt.

Die vorhandene gemeinsame Owner-Seam prüft für jeden Abruf Mainthread,
Originalslot, aktuelle HUB-Route, ACTIVE-Action-ID/-Status, lokale und frische
READY-Generation, Original-Lifetime mit frischem zentralem `authorizeHit()` sowie
Writer-/Mutationsticket, `publish()` und abschließende Aktualität.
Ungültige Indizes/Zeilen werden vor Ownerzugriff verweigert. Eine fremde Identität
kann weder Werte auflösen noch den aktuellen Originalslot verwerfen.

Acht **einmal pro Handle angelegte** Navigationscallbacks (drei Karten, fünf
bestehende Zusatzbuttons) ersetzen wiederholte app-eigene Owner-Callbackbindungen
im HUB. Ihre einzige Verbindung führt über das originale Handle; jede Ausführung
wird erneut über dieselbe Seam autorisiert. Erst nach deren Rückkehr läuft die
bestehende `activeArea()`-Navigation mit ihren unveränderten Gates. Kein Repository-
Aufruf innerhalb der kurzen Publication. Kein neuer Navigations-/Reusepfad.

Revoke/terminale Ablehnung trennt die Ownerverbindung und damit sämtliche zuvor
übergebenen Callbacks. Metadatenhüllen/Callbacks dürfen weiterhin im Framework
existieren, sie können aber keine neuen Daten auflösen. Die Verbindung zum
vollständigen Core bleibt ausschließlich beim vorhandenen Owner.
**Getrennte skalare Abrufe sind kein atomarer Gesamtframe**; bereits ausgegebene
Texte werden weder zurückgerufen noch als physisch gelöscht behauptet.

## Gemeinsame Vorabzulassung R8/T16

Der bestehende `CompactPresentationBudget.reservation` im unveränderten gemeinsamen
`CoreReadAdmission`-Envelope wird vor Materialisierung erweitert. Kein zweites
Ledger und keine kostenlosen oder doppelten Teilkontingente:

| Disjunkter Zusatz zum bisherigen Basis-Envelope | R Bytes | T Bytes |
| --- | ---: | ---: |
| PERSONS- und TASKS-Handles, jeweils ein Objekt/drei Referenzen | 304 | 304 |
| HUB-Handle/Kartenhüllen/Callbacks/Liste | 2.136 | 2.136 |
| Ein gemeinsamer Resolver-/Formatierungs-/Builderbereich | 0 | 61.088 |
| **Alle drei Präsentationen gemeinsam** | **2.440** | **63.528** |

HUB-Units: 13 Objekte (Handle, drei HubCards, acht Callbacks, einmalige EmptyList-
Allowance), eine Dreiereinträge-Liste inklusive Backingkapazität, 40 Referenzen
inklusive konservativer Slack. Die drei Karten teilen die leere Summaryliste;
sie wird nicht dreimal gebucht. Keine Liste für die acht Callbackfelder.
Gegenüber §93a kommen +2.136 R/+2.136 T hinzu.

Der bestehende gemeinsame Bereich zählt 32 Objekte, 128 Referenzen, 32 Strings
mit höchstens 128 UTF16-Einheiten, 64 Arrays mit zusammen 32.768 Payloadbytes:
inklusive Konkatenationszwischenwerten, Builderwachstum, skalarem Scratch und
Callback-/Box-Übergaben. Der HUB formatiert sechs Summaries plus drei Öffnen-Labels;
die längste Summary hat auch bei Long-Maximalwerten weniger als 128 Einheiten.
HUB/PERSONS/TASKS sind auf dem Ownerthread seriell und als aktive Route gegenseitig
exklusiv. Kein app-eigener formatierter Zustand wird zwischen ihnen gespeichert;
gemeinsamer Scratch wird daher einmal gebucht. Beide Handlearten und die HUB-
Metadatenkonstruktion werden auch im Übergang disjunkt gezählt.

**8.388.608 Bytes Retained / 16.777.216 Bytes Transition unverändert.** Die
Engineering-Units sind keine zertifizierten ART-/Native-/PSS-Obergrenzen.
Framework-eigene Compose-/Text-/Layout-/Semantics-/Renderkopien und interne
Consumer-/Callbackwrapper werden gemäß Option 2 separat beurteilt, weder
kostenlos noch nachweislich beendet. Die app-kontrollierten acht Verbindungen
sind ausdrücklich vorab gebucht und widerrufbar. `OutstandingCoreRead` und alle
bisherigen Cleanup-/Expositionssperren sind unverändert; gelöschter VM-Slot,
Dispose, Frame-Commit und GC erlauben keine neue Admission.

## Gezielte Belege

**70 unterschiedliche Hostfälle PASS**: 33 `ActiveCoreOwnerTest`,
5 `CoreReadAdmissionBudgetTest`, 12 `ProductActiveCoreLifetimeTest`,
16 `OutstandingCoreReadTest`, 4 `P83OverlapBudgetTest`.
[Hostlog](host-tests.log), [ergänzte Invalid-Identity-Prüfung](host-identity-followup.log),
[XML](host-xml/), [Bilanz](test-summary.json). Kein fehlgeschlagener Lauf.

Neue HUB-Fälle prüfen exakt drei Karten und sechs korrekte Originalwerte,
gleiche Metadatenobjekte/Callbacks, leere Karten-Summaries, alle drei bisherigen
Navigationsziele und keine weitere Navigation durch alte Callbacks. Der typisierte
HUB-Composable-Overload hat keinen Core-Parameter. Elf Handle-Instanzfelder
enthalten nur Identität, Ownerverbindung, eine Metadatenliste und acht Callbacks.
Ungültige Indizes/Zeilen, fremder Slot, Foreignthread, Lock/Revoke/Generation/
Action/Status/frische Session und Kontextablehnung, Publication-Race, Slotwechsel,
Writer-Pending/UNKNOWN und doppelte späte Main-Lieferungen bleiben fail-closed.
Host-Writerfälle verwenden echte Mutationsmetadaten mit dem Host-Lifetimeadapter;
die echten Loader-/Lifetime-Gates werden separat in den bestehenden Datenfällen geprüft.

Kontrollierter Revoke: erster HUB-Wert/formatierter Text geliefert, danach
`life.close()`, spätere Werte/Karten und alte Navigation verweigert. Der zuvor
zurückgegebene Text bleibt erhalten. Keine unbelegte atomare Frameausgabe.
Die gemeinsame Budgetprüfung zählt drei Handles/Metadaten und einmal Scratch:
R8/T16 exakt PASS, je ein zusätzliches R- oder T-Byte REFUSED vor Materialisierung.
Overflow-/Refusal-/UNKNOWN-/Handoff- und erneute Admission-Prüfungen weiterhin PASS.

Große echte Host-SQLite-Fixture 200 Personen/1.000 Tasks/5.000 Events mit Unicode:
R/T bei 6 Rollen **1.607.652/6.460.568 B**, bei 32 Rollen
**1.821.796/7.317.144 B**, bei 100 Rollen **2.378.408/9.543.592 B**.
Nur bestehender Reader-Testumfang; keine neue 100-Rollen-Produktfreigabe.
Bei stark referenziertem altem Core bleibt die Overlap-Fixture über T16;
der zweite Produktread wird vor neuem SQL verweigert, auch nach Dispose.

P77-App, P77-Test-APK und Release einschließlich Lint-Vital **PASS**:
[Buildlog](builds.log), [Reproduktion](commands.sh), [APK-Hashes](apk-sha256.txt).

### Ein isolierter Androidfall

Samsung SM-M526BR/API 33, echte ProductRuntime/Repositories/SQLCipher/Compose,
`stress`-Fixture 200/1.000/5.000. [Rohbeleg](android-probe.log): alle drei
**tatsächlich sichtbaren anklickbaren Karten**, mit jeweils passenden Angaben im
selben Button-Subtree: Personen 200 insgesamt/200 vor Ort; Funktionen 6/200 vor Ort;
Aufgaben 1.000 offen/0 in Arbeit. Kartenmetadatenanzahl exakt drei, kein Workspace
im ViewModel, ein materieller Core-Read. Keine reine Owner-only-Sichtbarkeitsbehauptung.

Writer-Pending gezielt zwischen zwei skalaren Abrufen: erster Wert RETURNED,
späterer Wert DENIED, erster Wert weiterhin vorhanden. Protokoll-Metadaten-UNKNOWN
kontrolliert injiziert (kein behaupteter Vendorfehler); alte Navigation, Lock,
Dispose und doppelte Main-Shells lösen keine weiteren Daten/Reads auf.
Zweite Admission DENIED, Retirement UNCONFIRMED, Fixture-Cleanup bestätigt.
Kein schneller Navigationstest und keine neue Latenz-/physische Ressourcenmessung.

Der §94a-Gerätefall bestand im ersten Lauf. Frühere Harness-Sichtbarkeits-/
Textsuchfehlversuche bleiben separat bei [§92a](../p11-p92a/assessment.md);
keine Umdeutung als neue §94a-Fehler oder erfolgreiche §94a-Belege.

## Reviewgrenze

Kein vollständiger Owner-Umbau, keine Änderung von FUNCTIONS/Editoren/Fullview,
Session-/Writer-/Crypto-/Storageverträgen. `reuseEnabled=false`,
`ramReuseEnabled=false`, keine produktive Schnellnavigation/automatische
Readmission, kein Cache/Fallback. Keine Gesamtsuite/129k-Serie.
WIP pushen und **STOPP zum unabhängigen Review**. Kein Merge/Release.
