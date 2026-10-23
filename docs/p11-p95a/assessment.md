# §95a – kontrollierte kompakte FUNCTIONS-Präsentation

Basis `1146d6eb232efd4a2ae7d09b5f3c8a4113d1ae41`, #37/#34. WIP zur unabhängigen Prüfung.

## Codebefund und Umsetzung

FUNCTIONS war die letzte direkte `ActiveCorePayload`-Übergabe in `ActiveWorkspace`.
Jetzt haben **alle vier** kompakten `CompactActiveContent`-Overloads ausschließlich
Präsentationshandles als Datenparameter. `ActiveWorkspace` liest dafür keinen Core
mehr; der payloadnehmende Composable entfällt. Fullview, Editoren, fachliche
Rollenregeln und die übrigen drei Darstellungen bleiben unverändert.

`CompactFunctionsPresentation` ist das vierte Handle im vorhandenen CoreSlot.
Drei Instanzfelder: widerrufbare Ownerverbindung, Originalidentität, privater
unveränderlicher Indexbound. Letzterer stammt aus der vollständig materialisierten,
vorab zugelassenen Originalliste und dient nur zur Verweigerung ungültiger Indizes
**vor** Ownerzugriff; er ist keine zweite Liste und keine neue Autorität.
Keine Rollen-, DTO-, Namens-, Zeilen- oder Formularcachefelder.

Autorisierte Einzelzugriffe: vollständige Größe, Personen vor Ort/ohne Funktion,
Name, tatsächlicher Vor-Ort-Zähler und formatierte Zeile für genau einen Index.
Die Darstellung bleibt `Name: Anzahl vor Ort`, mit dem bisherigen Header und Leerzustand.
Alle Rollen werden über `repeat(size)` dargestellt. **Keine 32er-Begrenzung**,
kein neues Paging oder fachliches Auswahlverhalten. Die bisherige Column-/Scroll-
Darstellung bleibt bestehen; keine unbelegte Beschleunigung oder 100-Rollen-UI-
Abnahme durch eine neue Lazy-Architektur.

Jeder gültige Abruf verwendet die bestehende gemeinsame Owner-Seam: Mainthread,
Originalslot, FUNCTIONS-Route, ACTIVE-Action-ID/-Status, lokale **und frische**
READY-Generation, ursprüngliche Lifetime, frischer zentraler `authorizeHit` mit
Writer-/Mutationsticket, Publication und abschließende Prüfung.
Lock/Revoke/terminaler Denial trennt alle vier Handles. Alte Handles können weder
weiterlesen noch einem neuen Slot folgen. Keine neue Session-/Schlüsselautorität,
Payloadregistry, Datenbankabfrage oder Idle-Erneuerung durch diese Abrufe.

Ein Row-String entsteht kurz im Owner und geht unmittelbar an `Text`; Name und
Zähler gehören dabei zum selben autorisierten Original-Core. Verschiedene Zeilen
und Header sind **kein atomarer Gesamtframe**. Bereits ausgegebene Texte bleiben
möglicherweise im Framework, sie werden nicht als rückrufbar/physisch gelöscht
behauptet. `OutstandingCoreRead` und seine Cleanup-/Expositionssperren sind unverändert.

## Vorabzulassung und variable Formatierung

Rollennamen besitzen nach `OperationalRules.validName()` **keine feste Längengrenze**.
Eine pauschale 128-Zeichen-Reserve für alle Funktionszeilen wäre deshalb falsch.
Die bestehenden `Selection`-Metadaten in `CompactPeopleStore.prepareCore()` liefern
bereits Rollenanzahl `n` und UTF8-Gesamtbytes `b` aller Rollen-Textfelder. Es gibt
keine zusätzliche SQL-Abfrage und keinen Textfetch für diese Kalkulation.
Gültige UTF8-Bytes aller Felder sind ein konservativer oberer Bound für die
benötigten UTF16-Namenseinheiten; ID/Action/Normalisierung zählen zusätzlich mit.

Die neue disjunkte Zeilenreserve zählt `u = b + 32*n` (Name + Doppelpunkt/Long-/„vor Ort“-
Suffix) und folgende Engineering-Units **für einen vollständigen Katalogdurchlauf**:

| Unit | Menge |
| --- | ---: |
| Formatter-/Builderobjekte | `8*n` |
| Ausgabe-/Zwischenstrings | `4*n` |
| UTF16-Einheiten | `4*u` |
| zusätzliche Arrays/Builderbuffer | `8*n` |
| Arraypayloadbytes | `16*u` |
| Referenzen | `16*n` |

Kosten nach bestehenden Gewichten: **`3.028*n + 32*b` Transitionbytes**, keine
zusätzliche gehaltene Rollen-/Namensliste. Alle Multiplikationen/Additionen sind
geprüft. Der Store übersetzt Arithmetic-Overflow in BUDGET_REFUSED, nicht in einen
Storagefehler. Die Units werden dem vorhandenen People-Plan zugeschlagen und in
**derselben** `CoreReadAdmission` zusammen mit Taskplan und Envelope vor jedem
Materializer zugelassen. Kein unabhängiges Formatierungskontingent.

Der fixe gemeinsame Envelope-Zusatz umfasst jetzt vier Handles: **2.592 R /
63.680 T Bytes**, gegenüber §94a +152 R/+152 T für das FUNCTIONS-Handle inklusive
seines skalaren Indexbounds. Darin bleiben PERSONS/TASKS/HUB und deren Hüllen/
Callbacks enthalten. Der bestehende gemeinsame Scratch von 61.088 T wird weiterhin
**einmal** gebucht; FUNCTIONS-Header/Resolver nutzen ihn mit. Nur die variablen
neuen Rollen-Zeilenstrings und ihre Formatter kommen disjunkt hinzu.

R8 = **8.388.608 B**, T16 = **16.777.216 B** unverändert. Die Engineering-Units
sind keine zertifizierten ART-/Native-/PSS- oder Gesamt-RAM-Grenzen. Ausgegebene
Compose-/Text-/Layout-/Semantics-/Renderkopien bleiben gemäß Option 2 separat zu
beurteilen, weder kostenlos noch als vollständig beendet nachgewiesen.

## Gezielte Tests / Rohbelege

**77 unterschiedliche Hostfälle PASS**, 0 FAIL: 38 `ActiveCoreOwnerTest`,
6 `CoreReadAdmissionBudgetTest`, 12 `ProductActiveCoreLifetimeTest`,
16 `OutstandingCoreReadTest`, 5 `P83OverlapBudgetTest`.
[Erster erfolgreicher Hostlauf](host-tests-initial.log),
[abschließend verstärkte Langnamensfälle und Builds](verification.log),
[XML](host-xml/), [Bilanz](test-summary.json). Kein fehlgeschlagener Lauf.

Neue Ownerfälle prüfen 0/6/32/100 Rollen vollständig, ohne fehlende/doppelte Namen,
exakte Zähler/Header und Unicode-Namen auch über 320 UTF16-Einheiten; keine
32er-Kappung. Alle vier Composable-Signaturen ohne Core; Handle ohne Listen-/DTO-
felder. Ungültige Indizes einschließlich oberer Grenze ohne Autoritätsaufruf,
Foreignthread, fremde Identität, Publication-Race, Slotwechsel, Lock/Revoke/
Action-/Generation-/Status-/Kontextwechsel, frische Session ohne Observerupdate,
Writer-Pending/UNKNOWN und doppelte Main-Callbacks. Host-Writerfälle verwenden
echte Mutationsmetadaten plus Lifetime-Testadapter; die unveränderten echten
Loader-/Lifetime-Gates werden zusätzlich in den Datenfällen geprüft.

Revoke zwischen Funktionsabrufen: erste Zeile geliefert, dann `close()`, spätere
Namen/Zähler/Zeilen verweigert, erster String unverändert vorhanden.
Keine unbelegte Frameatomarität. Gemeinsame Admission mit allen vier Handles
**und** variabler Zeilenreserve exakt bei R8/T16 PASS; jeweils +1 R-/T-Byte
verweigert ohne Materialisierung. Geprüfter Overflow läuft nicht auf kleine Kosten
über. Erneute Produkt-Core-Admission bei unbestätigter Exposition auch nach
Dispose weiterhin verweigert, ohne neuen SQL-Read.

### Echte SQLite-Reader-Charakterisierung, 200/1.000/5.000

Alle Rollen samt tatsächlicher aktueller Vor-Ort-Zuordnung vollständig geprüft;
Gesamt 200 vor Ort, 0 ohne Funktion. Kein neues Paging/Trimming.

| Rollen | Unicode-Namenspräfix UTF16-Einheiten | R Bytes | T Bytes |
| --- | ---: | ---: | ---: |
| 6 | 60 | 1.791.544 | 7.267.032 |
| 32 | 60 | 2.131.528 | 8.938.272 |
| 100 | 60 | 3.017.260 | 13.296.384 |
| 6 | 320 | 2.680.744 | 11.048.472 |

**100 Rollen: Reader-/Host-PASS, keine vollständige Produktfreigabe.** Weder reale
100-Rollen-UI-Latenz noch Framework-/Native-Restgrenzen wurden neu belegt.
Längere/größere Bestände können durch die gemeinsame Admission kontrolliert
abgelehnt werden; es wird kein Teilkatalog als vollständig ausgegeben.

### Ein isolierter P77-Gerätefall

Samsung SM-M526BR/API 33, echte ProductRuntime/Repositories/SQLCipher/Compose,
normale `stress`-Fixture mit 200 Personen/1.000 Tasks/5.000 Events.
[Rohlog](android-probe.log): sechs tatsächlich sichtbare Funktionszeilen mit
exaktem Unicode-Namen, Rolle 0 mit 200 vor Ort, Rollen 1–5 mit 0; sichtbarer Header
200 vor Ort / 0 ohne Funktion. Vollständigkeit 6 und alle Einzelwerte zusätzlich
über das Handle geprüft. Kein Workspace im ViewModel, ein materieller Core-Read.

Der vorhandene P77-Einstieg quittiert einen vor Exposition verworfenen Hub-Worker,
bevor FUNCTIONS erstmals lädt; er umgeht keine Expositionssperre und aktiviert
keine schnelle Produktnavigation. Writer-Pending zwischen zwei Abrufen,
kontrollierte Protokoll-Metadaten-UNKNOWN-Injektion (kein behaupteter Vendorfehler),
Lock, Dispose und doppelte späte Main-Shells erlauben keine weiteren Werte.
Zweite Admission DENIED, Readcount unverändert, Retirement UNCONFIRMED,
Fixture-Cleanup bestätigt. Bereits gelieferte erste Zeile bleibt erhalten.

P77-App, P77-Test-APK und Release einschließlich Lint-Vital PASS:
[verification.log](verification.log), [APK-Hashes](apk-sha256.txt),
[Reproduktion](commands.sh). Der abschließende Gerätefall bestätigt auch den unveränderten „vor Ort“-Suffix.
Beim Abschlussdiff wurde der zunächst fehlende Suffix korrigiert; der frühere
Lauf mit korrekten Werten, aber unvollständigem Zeilentext ist separat in
[android-probe-initial-format.log](android-probe-initial-format.log) erhalten.
Die betroffenen Ownerfälle und alle Builds wurden erneut geprüft
([Log](verification-format-followup.log)). Frühere
Harness-Sichtbarkeits-/Textsuchfehlversuche bleiben getrennt bei
[§92a](../p11-p92a/assessment.md), keine Umdeutung als §95a-Belege.

## Reviewgrenze

Kein vollständiger Owner-Umbau, keine Änderung von Editoren/Fullview, Session-,
Writer-, Crypto-, Storage- oder Berechtigungsverträgen. Keine Gesamtsuite/129k-Serie.
Keine unbelegte Frame-, Performance- oder Native-Freigabe. `reuseEnabled=false`,
`ramReuseEnabled=false`, keine automatische Readmission, kein Cache/Fallback.
WIP sichern und **STOPP zum unabhängigen Review**; kein Merge/Release.
