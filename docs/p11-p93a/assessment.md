# §93a – TASKS-Präsentation am bestehenden CoreSlot

Basis `b059aaad57489bb4cf347da27f1b51e9d4abdf68`, #37/#34.
Nur die kompakte ACTIVE-TASKS-Route umgestellt; WIP zur unabhängigen Prüfung.

## Übergabe und Autorität

`ActiveWorkspace` liest für TASKS keinen `activeCore` mehr. Der neue
`CompactActiveContent(CompactTasksPresentation, …)` erhält ausschließlich ein
widerrufbares Identitätshandle. Zwei Instanzfelder: ursprüngliche Slot-Identität
und widerrufbare Verbindung zum bestehenden ViewModel. Kein Core-/Page-/Kartenfeld,
kein TaskBoard, keine Historie, kein Titelcache, keine zweite Payloadregistry oder
Session-/Schlüsselautorität. Pro originalem CoreSlot genau ein TASKS- und ein
PERSONS-Handle; beide werden bei dessen Invalidation getrennt.

Ausgabe: die vier bisherigen Statuszähler, aktive Gesamtzahl fürs Seitenlabel,
Seitengröße/hasMore und einzelne Titel per Index **0..31**. Ungültige Indizes
lösen keine Ownerdaten auf. Der Owner bleibt der einzige Payloadbesitzer.
PERSONS und TASKS teilen dieselbe private Auflösungs-/Publication-Seam:
Ownerthread, Slotidentität, aktive Route, ACTIVE-Action-ID/-Status, lokale und
frische READY-Generation, Original-Lifetime mit `authorizeHit()` (frischer zentraler
READY-Zugang, Originalticket/Writer-/Mutationsepoche), `publish()` und abschließende
Aktualitätsprüfung. Keine neue Datenbankabfrage, kein Ticket, keine Idle-Erneuerung.
Terminale Ablehnung trennt beide Handle-Verbindungen; alte Identitäten können
keinen späteren Slot verfolgen oder bei erneut gültiger Autorität wieder aufleben.

**Jeder skalare Abruf ist separat autorisiert, kein gesamter Frame atomar.**
Ein zuvor zurückgegebener Titel kann weiterhin im Framework stehen; nach einem
Revoke werden spätere Abrufe verweigert. Keine rückwirkende Textlöschung oder
physische Wipegarantie. Dispose/Draw/GC geben weder Speicher noch Admission frei.
`OutstandingCoreRead`, Session-/Writer-/Crypto-/Storage-Verträge bleiben unverändert.

HUB, FUNCTIONS, Editoren und Fullview behalten ihre bisherigen Übergaben/Inhalte.
PERSONS nutzt dieselbe Oberfläche wie §92a; lediglich die gemeinsame Owner-Seam
und ihre Composition-Metadatenfunktion wurden für beide Adapter zusammengeführt.
Kein Cache/Fallback, keine Aktivierung von Reuse oder schneller Produktnavigation.

## Eine gemeinsame Admission, disjunkte Kosten

`CompactPresentationBudget` ersetzt den bisherigen PERSONS-spezifischen Namen.
Die gemeinsame Reserve wird **einmal** im bestehenden `ActiveCoreStore.envelope`
in dieselbe `CoreReadAdmission` vor beiden Materializern aufgenommen:

| Zusatz gegenüber dem alten Basis-Envelope | Retained B | Transition B |
| --- | ---: | ---: |
| PERSONS-Handle: ein Objekt, drei Referenzen | 152 | 152 |
| TASKS-Handle: ein Objekt, drei Referenzen | 152 | 152 |
| Gemeinsamer Resolver-/Formatierungs-/Builderbereich | 0 | 61.088 |
| **Zusammen** | **304** | **61.392** |

Gegenüber §92a: +152 R / +304 T. Keine unabhängigen Freikontingente.
Der Aufbereitungsbereich zählt 32 Objekte, 128 Referenzen, 32 Strings mit
höchstens 128 UTF16-Einheiten, 64 Arrays mit zusammen 32.768 Payloadbytes;
auch Konkatenationszwischenwerte, Boxes/Closures und Builder-Wachstumsbuffer.
Beide Routen sind auf dem Ownerthread seriell und gegenseitig exklusiv;
kein app-eigener formatierter Zustand wird zwischen ihnen gespeichert.
PERSONS hat neun formatierte Zeilen, TASKS nur Zählerzeile und Seitenlabel.
Die TASKS-Zählerzeile hat selbst bei vier gültigen nichtnegativen Long-Maximalwerten
127 UTF16-Einheiten; die bestehende 128er-Reserve reicht auch hierfür.
Titel aliasieren die bereits gebuchten Corestrings. Gemeinsame Slot-/Ticketkosten
bleiben im bisherigen Basis-Envelope, sie werden nicht nochmals pro Handle gebucht.

**R8 = 8.388.608 B / T16 = 16.777.216 B unverändert.** Engineering-Charges sind
keine zertifizierten ART-/Native-/PSS- oder Gesamt-RAM-Obergrenzen. Bereits an
Compose/Android ausgegebene Texte/Layout-/Semantics-/Renderkopien werden gemäß
Option 2 separat beurteilt, weder kostenlos noch nachweislich vollständig beendet.

## Gezielte Belege

**65 Hostfälle PASS**, keine Gesamtsuite: 28 `ActiveCoreOwnerTest`,
5 `CoreReadAdmissionBudgetTest`, 12 `ProductActiveCoreLifetimeTest`,
16 `OutstandingCoreReadTest`, 4 `P83OverlapBudgetTest`.
[Log](host-tests.log), [XML](host-xml/), [Bilanz](test-summary.json).

Neue TASKS-Fälle: 32 eindeutige Unicode-Titel und exakte 640/200/100/60-Zähler,
aktive Zahl 840; negative/32/Integerextrem-Indizes ohne Autoritätsabruf;
33er-Candidate verweigert; zwei reine Metadatenfelder und dieselbe Handleidentität.
Lock, Revoke, Generation-/Action-/Statuswechsel, frischer Session-Denial ohne
Observerupdate, Kontextablehnung und keine Wiederbelebung; Foreignthread,
Publication-Race, Slotersetzung und doppelte späte Main-Lieferung ohne neuen Read.
Writer-Pending/UNKNOWN über echte `UncachedWorkspaceMutations`-Metadaten, kombiniert
mit dem Host-Lifetime-Testadapter; die unveränderten echten Loader-/Lifetime-Gates
werden separat in den bestehenden Datenhostfällen geprüft.

**Kontrollierter Revoke zwischen zwei Abrufen:** erster `title(0)` liefert den
korrekten String, danach `life.close()`, anschließend `title(1)`, Zähler und Größe
verweigert. Der erste String bleibt im Test erhalten. Das ist ausdrücklich kein
atomarer Frame- oder Rückrufbeweis.

Die Budgetprüfung zählt zwei Handles plus einmal Scratch: R8/T16 exakt PASS,
jeweils ein zusätzliches R- oder T-Byte REFUSED vor Materialisierung; Arbeitszähler
unverändert. Bestehende Overflow-/Admission-/Handoff-/UNKNOWN-Sperren PASS.
Mit stark gehaltenem altem Core verweigert der Loader den zweiten Read weiterhin
vor SQL, auch nach Composition-Dispose; Retirement bleibt unbestätigt.

Große echte SQLite-Fixture: 200 Personen / 1.000 Tasks / 5.000 Events mit Unicode.
R/T bei 6 Rollen **1.605.516 / 6.458.432 B**, bei 32 Rollen
**1.819.660 / 7.315.008 B**, bei 100 Rollen **2.376.272 / 9.541.456 B**.
Numerische Admission ist keine neue 100-Rollen-Produktfreigabe.

P77-App, P77-Android-Test-APK und Release einschließlich Lint-Vital **PASS**:
[Buildlog](builds.log), [Reproduktion](commands.sh).
### Ein isolierter TASKS-Gerätefall: PASS

Samsung SM-M526BR, API 33, echte ProductRuntime/Repositories/SQLCipher/Compose;
`stress`-Fixture 200/1.000/5.000, lange Unicode-Titel. Genau ein materieller
Core-Read für TASKS, kein vollständiger Workspace im ViewModel. Die vorhandene
P86-Testactivity erhält im Instrumentation-Setup den optionalen Compact-Port,
Reuse und ramReuse bleiben false. Ein vor Exposition verworfener Hub-Worker wird
erst tatsächlich quittiert, bevor TASKS erstmals laden darf; kein Umgehen der
Expositionssperre und keine neue Produktnavigation.

[Rohlog](android-probe.log): 32 unterschiedliche Titel entsprechen den gültigen
synthetischen Fixturewerten, Zähler 1.000/0/0/0, tatsächliche sichtbare Titel-Nodes,
Seitenlabel und Zählerzeile vorhanden. Die erste Titelzeile wird gegen den zuvor
über das Handle autorisierten Text exakt als sichtbarer Node bestätigt.

Kontrollierter Writer-Pending zwischen zwei skalaren Abrufen: erster Titel
RETURNED, danach title(1) DENIED. Der erste Titel bleibt erhalten.
Anschließende Protokoll-Metadaten-UNKNOWN-Injektion, doppelte späte Main-Shells,
Dispose und Lock erlauben keine weiteren Werte/Admission; kein behaupteter
SQLCipher-/Vendorfehler. Zweiter Core-Load verweigert, Readcount unverändert,
Retirement UNCONFIRMED. Fixture-Cleanup bestätigt. Kein atomarer Gesamtframe,
keine Wipe-/Framework-/Native-Restgrenze behauptet. [APK-Hashes](apk-sha256.txt).

### Historische Harness-Fehlversuche getrennt

**§93a: kein fehlgeschlagener Host-/Androidlauf; der eine Gerätefall bestand
im ersten Lauf.** Der Test übernimmt die bei §92a korrigierte UiAutomation-
Initialisierung vor Activitystart, den Composition-Commit-Wait und die exakte
Accessibility-Node-Traversierung mit begrenzten langsamen Scrollgesten.
Die damaligen Fehlversuche (Feldtest mit statischer Compose-Konstante,
Sichtbarkeits-/Textsuchoracle) gehören ausschließlich zu
[§92a mit seinen separaten Rohlogs](../p11-p92a/assessment.md).
Sie werden weder als neue §93a-Produktfehler noch als erfolgreiche §93a-Läufe
umetikettiert. Composition/Draw/Accessibility werden ausschließlich als
UI-Testbelege verwendet, niemals als Ressourcen-Endnachweis.

## Reviewgrenze

Kein vollständiger Owner-Umbau, keine neue Freigabe exponierter Ressourcen.
`reuseEnabled`, `ramReuseEnabled`, produktive Schnellnavigation und automatische
Readmission bleiben deaktiviert. Keine neue Latenz-/physische Ressourcenmessung,
keine 129k-Serie. WIP pushen und **STOPP zum unabhängigen Review**; kein Merge/Release.
