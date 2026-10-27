# §117 – F04: kein Produktfix, Ziel nicht erreicht

Basis `ba3123bfe26fa9e9f6671e2efc403c6f5a979ada`, Branch `p11-performance-optimization`. **F04 bleibt OFFEN / Release NO-GO. Keine positive F04-Abnahme.**

## Produkt und Ownervertrag

**Produktionscode tatsächlich geändert: nein. Geänderter produktiver Ownervertrag: keiner.** Keine neue Coreadmission, kein Fullworkspace-Fallback, keine umgebundenen alten Contexts/Writer/Keys und kein Budgetreset eingebaut. R8 **8.388.608 B**, T16 **16.777.216 B**, 180s/30s, CAS/UNKNOWN/Recovery und beide Reuseflags **false** unverändert.

Die konkrete technische Ursache ist die Verknüpfung des einzigen Ressourcenrecords mit den ursprünglichen Aktions-/Generations-/Read-/Consumerbindungen. Seine Rootadmission bleibt nach Exposure gesperrt; die vorhandenen Children benötigen die ursprüngliche gültige Bindung und teilweise einen ACTIVE-Coreslot. Insbesondere der in F04-B tatsächlich benötigte PREPARATION-Einstieg kann weder als ACTIVE-Child gelesen noch durch einen zweiten Vollworkspace ersetzt werden.

Ein sicherer Gesamtersatz erfordert die zusammenhängende Festlegung von Ressourcenprovenienz, frischer Childbindung und phasenrichtiger begrenzter Einstiegsprojektion. **Der kleinste notwendige Architekturentscheid ist in [owner-contract-decision.md](owner-contract-decision.md) konkret beschrieben.** Das ist kein Beweis prinzipieller Unmöglichkeit einer Implementierung; der heutige Vertrag bietet diesen Pfad nicht. Dieser Lauf implementiert die Erweiterung nicht und behauptet deshalb keinen funktionierenden produktiven F04-Fix. Alte Bindungen wiederzubeleben oder den Fence nur zu öffnen wäre keine sichere Erfüllung des Auftrags.

## Gerätebelege: Zielabläufe rot

Samsung SM-M526BR/API33, isoliertes `.p77runtime`, gleiche unveränderte Produktquellen. Fixture-/Auth-Scope je Fall ausdrücklich getrennt:

| Fall | Ausgeführt | Ergebnis |
|---|---|---|
| A DRAFT → PREPARATION → ACTIVE | Tatsächlicher VM/Room/SQLCipher-Phasenpfad, neue synthetische Aktion, P77-Testauthentisierung. [ux-phase.txt](ux-phase.txt) | **FAIL**: ACTIVE-Status erreicht, Ansicht verweigert; `core_reads=0`. Lokale Charges bleiben klein; Root-Fence `FULL_VIEW_UNCONFIRMED`. Kein neuer Compact-Core nach dem PREPARATION-Owner. |
| B ACTIVE → andere PREPARATION-Aktion bewusst öffnen | Tatsächlicher VM/Room/SQLCipher-Pfad, synthetische P77-Authentisierung. [ux-action.txt](ux-action.txt) | **FAIL**: OWNER_UNCONFIRMED. Nicht durch Auswahl einer anderen ACTIVE-/DRAFT-Aktion als Ersatz für den ursprünglichen Gegenfall beschönigt. |
| C Lock → Reauth → dieselbe ACTIVE-Aktion | [Synthetischer Gegenfall](ux-session-synthetic.txt) und neuer normaler Android-Credentialfall unten | **FAIL**: erneutes bewusstes Öffnen bleibt am Originalrecord gesperrt. |

### C mit echtem DeviceCredential

[P117CredentialContinuationProbe](../../app/src/androidTest/kotlin/org/pag/prototype/P117CredentialContinuationProbe.kt) verwendet die vorhandene `P115CredentialActivity` und den öffentlichen normalen `ProductRuntime`, geprüft auf tatsächliche `ProductCredentialAuthentication`. Keine synthetische Authentisierung, keine Credential-Injektion, kein Lesen des Android-Dialogs oder privater Schlüsselbytes. Synthetische Aktion und Task werden über normale Produktionsrepositories in der verschlüsselten Testdatenbank angelegt.

Der Nutzer bestätigte im zweiten Versuch beide normalen Android-Dialoge am Gerät. [credential-real-session-retry.txt](credential-real-session-retry.txt): **echtes READY Generation 1, Lock, erneutes echtes READY Generation 3**. Danach ausdrückliches `detail(actionId)` **nicht nutzbar**. Vorher/nachher derselbe gehaltene Record:

- R lokal **2.099.520 Bytes**;
- T lokal **4.875.753 Bytes**;
- T16 bleibt zentral als ursprüngliche Obligation gebunden;
- `retirementConfirmed=false`, danach `OUTSTANDING_READ`.

Die lokale Restkapazität wird also nicht als zweite Rootadmission ausgegeben. Keine Wiederfreigabe/Neukalibrierung aus diesen Engineeringzahlen ableiten. Die Dezimalwerte sind Bytes, keine physische Heapmessung.

Der erste Versuch [credential-real-session.txt](credential-real-session.txt) erreichte READY nicht. Nutzerbestätigung: Dialog nicht bestätigt oder abgebrochen. Das ist kein nachgewiesener Produktfehler der Credentialprüfung und kein bestandener Sessionfortsetzungsfall.

## Negative Sicherheit

Im echten Credentialfall wurden originale Header-/Rowreferenz, aktuelle Formularfence und produktive Writercallbackhülle absichtlich über Lock/Reauth gehalten. Die Hülle wird nach Originalreservation konstruiert; sie ist kein aus Compose extrahierter Savebutton. Nach Generation 1→3:

- alter StorageContext verweigert Publication;
- alter Keyowner verweigert sowohl alten als auch frischen Context **vor** Ausführung des Keyconsumers;
- Header/Row/Form sind unwirksam, **0 Writeraufrufe**, keine alte Erfolgspublication;
- `NEGATIVE_SAFETY_PASS` ist ausschließlich dieser Negativumfang, **kein PASS für F04**.

[authority-negative.txt](authority-negative.txt): aktueller Pending-Writer-/UNKNOWN-/Cross-Action-Gegenfall PASS, 1 ursprünglicher Core-Read / 0 Fullworkspaces / 0 Klartextcallbacks. Hier ausdrücklich synthetische P77-Authentisierung und injizierte UNKNOWN-Protokollcompletion, kein tatsächlicher UNKNOWN-SQL-Commit. Vorhandene Schema12/CAS-/Recoverybelege aus §§109–116 werden übernommen, nicht erneut als große Kampagne ausgeführt. Kein zweiter unerlaubter Core-Reader/materialisierter Workspace im getesteten verweigerten Ablauf.

## Host, Build, Grenzen

[Hostlauf](host.log), [XML](host), [Übersicht](host-summary.json): **27 PASS, 0 Fail/Error/Skip**. Drei neue `OutstandingCoreReadTest.p117*`-Gegenbelege zeigen freie lokale Kapazität bei trotzdem verweigerter neuer Rootadmission, unveränderte Altcharges, frische Session ohne gültige alte Contexts und fehlende fremde Action-/Generationszuordnung bei der Writerbuchung. Das sind **bestandene Tests des heutigen Verweigerungs-/Sicherheitsvertrags**, keine bestandenen positiven F04-Funktionstests.

Erster Hostversuch separat [archiviert](host-first-attempt.log): Testrechnung vergaß die bereits gebuchte Childkapabilität. Korrigiert wird vor/nach Lock derselbe tatsächlich gebuchte Betrag verglichen, nicht ein kleinerer fiktiver Originalbetrag. Produktionsbudget/Testsicherheitsassertionen unverändert. [Isolierter Build](build.log) PASS.

Neue Dateien/Änderungen ausschließlich Hosttest, manuelle Android-Testprobe, AndroidTest-Manifest und dieser Berichtordner. [Produktionsquellhashes](production-source.sha256) und Gitdiff belegen keine Produktänderung. [APK-/Signaturen](install.json), fallbezogene `run-*.json`, [geschützte Pakete unverändert / Harnessfixtures entfernt](cleanup.json). Normaler verschlüsselter P77-Credentialbestand mit ausschließlich synthetischen §115-/§117-Daten bleibt erhalten; keine physische Wipebehauptung, kein Daten-/Budgetreset innerhalb des Sessionfalls.

**TalkBack NOT TESTED** wie ausdrücklich angeordnet; keine neue TalkBack-Instrumentierung und keine Accessibility-Freigabe daraus. F05, F06 und F07 ausdrücklich unverändert OFFEN.

**Abschluss:** keine Umgehung und kein falsches F04-PASS. Zuerst den konkret beschriebenen Owner-/Child-Projektionsvertrag entscheiden und implementieren, dann exakt A/B/C sowie die vorhandenen Negativfälle wiederholen. Erst nach F04 folgen F05/F06 und F07 ohne TalkBack; danach #46. WIP zur unabhängigen Prüfung. Kein Merge, Release, Tag, Produktupdate oder eigenständiges Issue-Schließen. **STOPP zum unabhängigen Review.**
