# P6a — Aktionsprotokoll / polizeiliche Maßnahmen

## 1. Status, Zweck und Abgrenzung

**P6a.1: verbindliche Product-/Privacy-Entscheidungen, noch nicht implementiert.**
Diese Entscheidungen erlauben die separate Implementierungsspezifikation, noch
keine technische Umsetzung. Analysebaseline P6a: sauberer `main`, `e1c3cdd`
(Merge UX3 active workspace integration), 13.10.2026. P6a.1 führt den vorhandenen
uncommitteten Entwurf auf Branch `p6a-protocol-design` fort und ersetzt seine
Reviewempfehlungen durch die ausdrücklich getroffenen Produktentscheidungen.

Grundlagen: [Produkt](../PRODUCT.md), [Privacy](privacy.md),
[Security](security.md), [Datenmodell](data-model.md),
[Retention](data-retention.md), [P4](product-p4.md), [P5a](product-p5a.md),
[UX-Philosophie](ux-philosophy.md), [Roadmap](ux-roadmap.md) und
[UX3-Integration](ux3-active-integration.md). Zusätzlich wurde der tatsächliche
`ProductTime.selectedInstant(LocalDateTime, ZoneId)`-Vertrag gelesen.

Das **Aktionsprotokoll** ist ein eigener aktionsgebundener Bereich zur möglichst
akkuraten zeitlichen Rekonstruktion relevanter Vorgänge, insbesondere
behördlicher/polizeilicher Maßnahmen. Es ist keine allgemeine Notizfunktion,
keine automatische Audit-Historie und keine aus P4-Snapshots rekonstruierte
Personenhistorie. Einträge entstehen ausschließlich durch bewusstes Speichern.
Unbeobachtete Vorgänge, Motive, Rechtsfolgen und frühere Zustände werden nicht
abgeleitet. Kategorien sind Dokumentationshilfen, keine rechtliche Bewertung.

Zwei gleichberechtigt frei erstellbare Arten:

- **SCHLÜSSELEREIGNIS:** bewusst hervorgehobener Zeitpunkt im Aktionsverlauf.
- **RELEVANTER EINTRAG:** weitere für die Rekonstruktion relevante Beobachtung
  oder mitgeteilte Information.

Ein Schlüsselereignis erfordert keine Vorlage; ein relevanter Eintrag erfordert
keine Polizeimaßnahme. Die Auswahl ist menschliche Einordnung, keine automatische
Wichtigkeits-, Gefahren- oder Eskalationsregel.

**Entschiedene Vertragspräzisierung:** P6 ist ein eigener Bereich, keine
`Note`/typisierte Notiz. PRIV-03 erlaubt ausschließlich hier eintragsbezogene
Revisionen und 0..n ausdrücklich betroffene Personen derselben Aktion. Allgemeine
Teilnehmerlisten, Autorenprofile, Personenstatus-/Bewegungshistorien und
aktionsübergreifende Wiedererkennung bleiben ausgeschlossen. PRIV-06 bleibt
unverändert in seiner Standardklassifizierung: **alle P6-Protokolldaten sind
SENSITIVE**. Die enge Ausnahme betrifft nur den Zugriffsmodus: normale P6-Arbeit
in gültigem READY ohne zusätzlichen P3b-/Leadership-Step-up. Sensitivitätsklasse
und UI-Zugriffsmodus sind getrennte Konzepte; keine Herabstufung auf INTERNAL.
P3b-Identitäts-/Kontaktdaten bleiben separat geschützt, ohne automatische Übernahme.

## 2. Schlüsselereignis-Vorlagen

Statische Vorschläge öffnen einen normalen, änderbaren Entwurf. Auswahl allein
speichert nichts und erzeugt keine anderen Fachobjekte. Mehrfachverwendung ist
möglich; gleiche Kategorie bedeutet nicht gleicher Vorgang.

| Endgültige Vorlage | Dokumentationsgrenze |
| --- | --- |
| Polizei eingetroffen | Beobachtetes Eintreffen, keine automatische Einsatzidentifizierung. |
| Anordnung / Ansage | Wahrgenommene Mitteilung; Wortlaut und sinngemäße Wiedergabe unterscheiden. |
| Frist gesetzt | Mitgeteilte Frist, keine Prüfung ihrer Wirksamkeit und kein Alarm. |
| Räumung angekündigt | Ankündigung getrennt von Beginn/Ende. |
| Zwang angedroht | Wahrgenommene Androhung getrennt von tatsächlicher Anwendung. |
| Räumung begonnen | Beobachteter Beginn, Zeitpunkt gegebenenfalls ungefähr. |
| Körperliche Maßnahme / Zwangsmittel angewendet | Beobachtete Handlung, keine automatische Gewalt- oder Rechtsbewertung. |
| Räumung beendet | Beobachtetes Ende, keine Aussage über Rechtsfolgen. |
| Personalienfeststellung / ED | Im Text präzisieren, was beobachtet/mitgeteilt wurde; Begriffe nicht automatisch gleichsetzen. |
| Platzverweis | Wahrgenommene/mitgeteilte Maßnahme, keine Wirksamkeitsprüfung. |
| Durchsuchung | Sachliche Beschreibung des beobachteten Vorgangs. |
| Sicherstellung / Beschlagnahme | Bezeichnung laut Wahrnehmung/Ansage; App entscheidet nicht zwischen Rechtsformen. |
| Person abgeführt | Beobachteter Vorgang; daraus folgt keine automatische Einordnung als Gewahrsam. |
| Gewahrsam | Wahrgenommene/mitgeteilte Angabe, keine gesicherte juristische Qualifikation durch die App. |
| Person freigelassen | Beobachtete/mitgeteilte Freilassung, keine automatische Statusänderung. |
| Medizinische Versorgung / Verletzung | Nur erforderliche Beobachtung, keine Diagnose oder unnötigen Gesundheitsdetails. |
| Polizeieinsatz beendet / Kräfte abgerückt | Beobachtetes Ende/Abziehen, keine Garantie vollständigen Einsatzendes. |
| Sonstige polizeiliche Maßnahme | Freier kurzer Titel und sachliche Beschreibung. |
| Eigenes Schlüsselereignis | Freie Erstellung ohne Behördenbezug oder vorgegebene Kategorie. |

„Gewahrsam / Abführung“ wird durch die getrennten Vorlagen „Person abgeführt“ und
„Gewahrsam“ ersetzt; keine zusätzliche doppelte Sammelvorlage. Androhung und
Anwendung sowie Ankündigung/Beginn/Ende sind unterschiedliche zeitliche Eckpunkte.
Die Kategorien behaupten weder Rechtswidrigkeit noch Rechtmäßigkeit, tatsächliche
juristische Qualifikation oder Vollständigkeit eines Einsatzablaufs.

Keine automatische Hochstufung freier relevanter Einträge, KI-/Textbewertung oder
Eskalation. Keine Vorlage setzt Personenstatus/currentRole, öffnet P3b oder erzeugt,
schließt beziehungsweise priorisiert Tasks. Spätere bewusste Verknüpfungen bleiben
eine getrennte Produktentscheidung; P5b ist ausgeschlossen.

## 3. Inhalt, Pflichtangaben und verbindliche Grenzen

Minimal erforderlich sind Art, sichtbarer kurzer Titel und `occurredAt` samt
`occurredAtApproximate` (Zeitpunkt ungefähr). Jede gespeicherte Fassung erhält
`recordedAt` erst beim erfolgreichen Persistieren. Eine Vorlage liefert einen
änderbaren Titelvorschlag. Weitere Angaben sind optional und standardmäßig leer.

| Inhalt | Zweck und Grenze |
| --- | --- |
| Beschreibung | Begrenzter sachlicher Zusammenhang; beobachtete Maßnahme hier beschreiben, kein zusätzliches unbegrenztes Maßnahmen-/Notizfeld. |
| Ort / lokale Bezeichnung | Manuell, aktionsbezogen; kein GPS, Tracking oder globale Ortskartei. |
| Betroffene Personen | 0..n bestehende Personen derselben Aktion, ausschließlich Pseudonyme in der normalen UI; keine einzelne affectedPersonId-Begrenzung und keine Teilnehmerliste. |
| Wortlaut / sinngemäße Ansage | Bei Nutzung ausdrücklich unterscheiden: „Wortlaut laut Wahrnehmung“ oder „Sinngemäß“; keine Transkription oder geprüfte Zitattreue. |
| Begründung / Rechtsgrundlage laut Polizei | Zugeschriebene Angabe, ausdrücklich ungeprüft; keine Gesetzesprüfung oder Rechtsberatung. |
| Quellenart | Optional strukturierter Wert und gegebenenfalls gemeldete Person nach Abschnitt 5; kein großes freies Quellenfeld. |
| Beobachtete amtliche Kennungen | 0..32 optionale Angaben pro Revision nach Abschnitt 6; nur im geöffneten Detail. |

Alle Texte werden getrimmt. Der Titel muss danach sichtbar nicht leer sein;
leere optionale Felder werden nicht gespeichert. Keine stille Kürzung:
Überschreitung bleibt korrigierbarer Eingabefehler. Grenzen zählen
**Unicode-Codepoints**, nicht Bytes oder UTF-16-Codeunits.

| Text / Anzahl | Verbindliche Obergrenze |
| --- | --- |
| Kurzer Titel | 120 Unicode-Codepoints |
| Beschreibung | 2000 Unicode-Codepoints |
| Wortlaut / sinngemäße Ansage | 2000 Unicode-Codepoints |
| Begründung / Rechtsgrundlage laut Polizei | 1000 Unicode-Codepoints |
| Ort / lokale Bezeichnung | 80 Unicode-Codepoints |
| Einzelne Behördenkennung | 120 Unicode-Codepoints |
| Behördenkennungen je Revision | 32 |

Keine versteckte unbegrenzte Freitextalternative. Eine Erklärung einer Korrektur
oder Unsicherheit kann in der begrenzten Beschreibung stehen; kein zusätzlicher
unbegrenzter Korrekturgrund. Mehrere Betroffene haben keine kleine künstliche
UI-Grenze: möglich sind ausschließlich vorhandene Personen derselben Aktion.
Eine betroffene Person wird nicht durch dieselbe Auswahl mehrfach aufgeführt.

Die Referenzen dokumentieren einen Bezug, keine Status-/Rollen-/Taskmutation.
Keine automatische Übernahme von Klarnamen, Kontaktwerten oder P3b-Payloads.
Pseudonyme sind nicht anonym; Freitext kann identifizierende Angaben enthalten.
Hinweise zur Datensparsamkeit ersetzen weder Zugangsschutz noch Privacyprüfung.
Leer bedeutet „keine Angabe“, nicht „nicht erfolgt“.

## 4. Zeitmodell

- **occurredAt:** wann das Ereignis stattgefunden haben soll, als eindeutiger
  Instant mit bestehender Millisekunden-Semantik. Bei Schnellvorlage einmalig
  beim Eintritt in den Entwurf auf aktuelle Gerätezeit vorbelegt und vor Save
  veränderbar. Tippen/Refresh verschiebt den Entwurfswert nicht.
- **occurredAtApproximate:** „Zeitpunkt ungefähr“, ausdrücklich auch in der
  Chronologie sichtbar. Kein beweisbarer Fehlerbereich oder errechnete Genauigkeit.
- **recordedAt:** wann genau diese Revision erfolgreich persistiert wurde.
  Erst dann erzeugt, innerhalb der gespeicherten Fassung unveränderlich. Nicht
  der Formularöffnungszeitpunkt. Kein Eintrag/recordedAt bei Cancel oder
  fehlgeschlagenem Save; beim erfolgreichen Retry aktuelle Erfassungszeit.
- Erste Erfassungszeit eines Eintrags = recordedAt seiner ORIGINAL-Fassung;
  jede Korrektur/Ergänzung hat eigenes recordedAt. Keine Änderung des ursprünglichen
  Erfassungszeitpunkts durch eine neue Fassung.

Bei COMPLETED-Nachträgen stehen „Ereigniszeit“ und „Erfasst am“ klar nebeneinander.
occurredAt darf während ACTIVE liegen; recordedAt zeigt die tatsächliche spätere
Erfassung. Die Kennzeichnung nach Abschluss folgt der tatsächlichen Erfassungsphase
(siehe Abschnitt 8), nicht einer vermeintlich vertrauenswürdigen Uhrdifferenz.

Lokale Datum-/Uhrzeiteingabe folgt ProductTime:
`zone.rules.getValidOffsets(localDateTime)` muss **genau einen** Offset liefern.
DST Gap und Overlap werden abgewiesen; keine stille Verschiebung oder implizite
Offsetwahl. Bereits gespeicherte Instants werden nicht über ihren Anzeigetext neu
interpretiert. „Ungefähr“ erlaubt keine mehrdeutige technische Zeitrepräsentation.

Anzeige wie bestehende Action-/Taskzeiten in der Gerätezone, verständlich mit
Datum/Uhrzeit und zugänglicher Zonenangabe. Zonenwechsel verändert die Anzeige,
nicht den Instant oder dessen Reihenfolge. Keine neue historische Zonenmetadaten-
Anforderung in P6a.1; der bestehende ProductTime-Vertrag bleibt maßgeblich.

Gerätezeit ist verstellbar und **kein vertrauenswürdiger Timestamp**. Es gibt keine
externe Zeitquelle oder Beglaubigung. Die monotone Sessionuhr entscheidet nur
über Zugriffsdeadlines, nicht über Ereigniszeit. Später erfasste Ereignisse und
Uhrabweichungen werden nicht still normalisiert oder als geplante Ereignisse
bewertet. Prüfung/Fehlermeldungen für auffällige Eingaben sind im separaten
Implementierungsslice aus diesem Vertrag abzuleiten, keine automatische Bewertung.

Chronologie aufsteigend nach occurredAt der jüngsten wirksamen Fassung; bei
Gleichheit erste recordedAt, danach stabile Eintragsidentität. Das ist eine
Darstellungsordnung, kein Nachweis realer Reihenfolge. Zeitkorrektur kann einen
Eintrag umordnen; Marker und Detail machen frühere Fassung/Zeiten nachvollziehbar.
Keine sekündlichen Ansagen oder Sessioninteraktion durch Zeitdarstellung.

## 5. Quellenart

Optionale strukturierte Quellen-/Wahrnehmungsart je Revision:

| Wert | Produkttext |
| --- | --- |
| OWN_OBSERVATION | Eigene Beobachtung |
| REPORTED | Mitgeteilt |
| OFFICIAL_ANNOUNCEMENT | Behördliche Ansage |
| UNCLEAR | Unklar |

Keine Auswahl ist erlaubt und bedeutet keine Quellenangabe, nicht automatisch
UNCLEAR. Bei REPORTED kann optional eine bestehende Person **derselben Aktion**
über ihr Pseudonym referenziert werden. Diese Quellenreferenz ist unabhängig von
den Betroffenen; eine mitteilende Person wird dadurch nicht automatisch betroffen.
Bei anderer/fehlender Quellenart besteht diese REPORTED-Referenz nicht.

Keine Pflichtangabe, Glaubwürdigkeitsbewertung, Autorenprofile oder allgemeine
Zeug:innenkartei; kein großes freies „Quelle“-Feld. Bei OFFICIAL_ANNOUNCEMENT reichen
die vorhandenen Felder für Wortlaut/sinngemäße Ansage und gegebenenfalls Begründung
laut Polizei aus. Behördenkennungen können bei Bedarf bewusst ergänzt werden;
keine zusätzliche Identität der sprechenden Person ist erforderlich.

## 6. Beobachtete Behördenkennungen

Je Fassung eines Eintrags können mehrere **beobachtete Angaben** mit Typ und
begrenztem Wert erfasst werden:

| Fachlicher Typ | Produkttext |
| --- | --- |
| SERVICE_OR_ID_NUMBER | Dienst-/Kennnummer |
| DISPLAYED_NAME | Name laut sichtbarer Kennzeichnung / eigener Wahrnehmung |
| UNIT_OR_VEHICLE_ID | Einheits-/Fahrzeugkennung |
| OTHER_OFFICIAL_ID | Sonstige sichtbare amtliche Kennung |

0..n Angaben je Revision, maximal 32; jeder getrimmte Wert maximal 120
Unicode-Codepoints. Leere Angaben werden nicht gespeichert.

Diese Angaben identifizieren kein von der App bestätigtes Behördenpersonal.
Unsichere Ablesung kann im Kontext ausdrücklich als unsicher beschrieben werden.
Mehrere Angaben sind keine automatisch zusammengeführte Person; Zusammengehörigkeit
wird nicht behauptet. Gleiche Werte werden nicht automatisch zusammengeführt.
Keine globale Einsatzkräfte-/Polizeipersonen-Datenbank,
Suchkartei, Wiedererkennung, aktionsübergreifende Verknüpfung oder biometrische
Beschreibung/Gesichtserkennung. Keine Fotos, Audio-/Videoaufnahme oder Anhänge.

Kennungen erscheinen ausschließlich im Eintragsdetail und dessen ausdrücklich
geöffneter Revisionsansicht, nie in Hubkarte oder Timeline-Kurzvorschau. Die
Vorschau verwendet nur Art, Kategorie/kurzen Titel, Zeitpunkt und Korrekturmarker;
keine Beschreibungsausschnitte oder Identifierlisten. Titelhinweis:
„Keine Namen oder Kennnummern im Titel“. Freitext kann entgegen diesem Hinweis
identifizierende Angaben enthalten; automatische zuverlässige Erkennung oder
Redaktion wird nicht behauptet.

**Die konkrete rechtliche Zulässigkeit der Erfassung und Weitergabe
personenbezogener Behördenangaben wird durch PaG nicht pauschal behauptet und
muss für den konkreten Einsatz separat juristisch bewertet werden.** Die
Dokumentationskategorie ist weder eine Erlaubnis noch eine Aussage über eine
Rechtsgrundlage. Weitergabe/Export wird in P6a nicht angeboten.

## 7. Korrekturen und Ergänzungen: verbindliches Revisionsmodell

**ProtocolEntry** ist die stabile Identität des dokumentierten Vorgangs innerhalb
genau einer Aktion. **ProtocolRevision** ist eine vollständige gespeicherte
Fassung seines Inhalts. Die erste Fassung ist ORIGINAL; weitere Fassungen werden
im normalen Produktpfad ausschließlich angehängt. Kein stilles Editieren oder
Löschen gespeicherter Fassungen und kein normales Einzel-Löschen von Einträgen.

| Revisionstyp | Bedeutung |
| --- | --- |
| ORIGINAL | Erste vollständige Fassung eines neuen Eintrags. |
| CORRECTION | Bewusste Korrektur bestehender Angaben; ursprüngliche Fassung bleibt nachvollziehbar. |
| ADDENDUM | Bewusste Ergänzung desselben dokumentierten Vorgangs. |
| MARKED_MISTAKEN | Irrtumsmarkierung als neue Fassung; entfernt keinen früheren Inhalt. |

Jede Revision hat eigenes unveränderliches recordedAt und vollständigen fachlichen
Inhalt: Art, Titel, occurredAt, occurredAtApproximate, optionale begrenzte Texte,
Quellenart und aktionsgebundene Bezüge/Kennungen. Eine neue Information zu einem
anderen Vorgang erhält einen eigenen Eintrag; kein Ereignisstrom im Freitext.

Aktuelle Ansicht zeigt die jüngste wirksame Fassung. Frühere Fassungen bleiben im
geöffneten Detail nachvollziehbar, nicht als doppelte Ereignisse in der Timeline.
MARKED_MISTAKEN bleibt als „Irrtümlich markiert“ sichtbar. Rücknahme nur durch eine
bewusste weitere CORRECTION, niemals durch Entfernen der Markierungsfassung.
Normale Korrektur von Betroffenen/Quellenreferenzen verändert alte Fassungen nicht.
Die ausdrücklich erlaubte Relationstrennung bei Personenlöschung ist eine separate
Privacy-Ausnahme, keine unsichtbare Inhaltsbearbeitung.

Pseudonyme werden aus vorhandenen Personen aufgelöst; keine historischen
Pseudonymkopien oder Rekonstruktion aus P3b. Revisionshistorie ist ausschließlich
Teil der bewussten P6-Dokumentationsfunktion (enge PRIV-03-Ausnahme), keine
allgemeine Audit-, Personenstatus-, Bewegungs- oder Taskbearbeitungshistorie.
Keine Autoren-/Geräteprofile, IP-Adressen oder Standortmetadaten.

Fachliche Speicheranforderung für den späteren Slice: neue Fassung und ihre
Bezüge vollständig oder gar nicht speichern; konkurrierende Korrekturen nicht
still überschreiben. Ein veralteter Entwurf muss vor erneutem bewussten Speichern
mit der aktuellen Fassung abgeglichen werden. Konkrete Tabellen, Versionsfelder
und Transaktionsmechanik werden hier nicht festgelegt.

**Nachvollziehbare Anwendungshistorie, kein kryptografischer Beweis:** keine
Hash-Chain, Signaturen, eigene Kryptografie oder externe Beglaubigung. Nicht
manipulationssicher, beweissicher, gerichtsfest oder forensisch unveränderlich;
kein vertrauenswürdiger Timestamp. Kontrolle über Gerät/DB kann den Bestand
beeinflussen. Datenschutz-/Retentionlöschung darf sämtliche Fassungen entfernen;
append-only im normalen Produktpfad ist keine Löschsperre.

## 8. Aktionsstatus und begrenzte COMPLETED-Ausnahme

| Status | Verbindlicher P6-Vertrag |
| --- | --- |
| DRAFT | Kein Protokollpfad, keine operativen Entwürfe/Einträge. |
| PREPARATION | Kein operatives Protokoll. |
| ACTIVE | Lesen; neue freie/Vorlageneinträge, Ergänzungen, Korrekturen, Irrtumsmarkierung. |
| COMPLETED | Lesen und bewusste P6-Nachbereitung: Korrektur/Ergänzung vorhandener Einträge, Irrtumsmarkierung über Revisionspfad sowie neue nachträgliche Einträge. |
| DELETION_PENDING | Kein normaler Zugriff oder Nachbereitungspfad. |

Die COMPLETED-Ausnahme ist ausschließlich für rechtliche/organisatorische
**P6-Nachbereitung** entschieden. P4/P5a und Aktionsstammdaten bleiben read-only;
kein Reopen der Aktion, kein stilles Editieren alter Fassungen und kein normales
Löschen einzelner Einträge/Fassungen. Auch Nachbereitung erfordert gültiges READY
und die bestehenden Storage-/Generations-/Aktionsstatusgates.

Ein erstmals in COMPLETED erstellter Eintrag trägt dauerhaft sichtbar
**„NACH ABSCHLUSS ERFASST“ / „Nachtrag nach Abschluss“**. Die fachliche
Erfassungsphase des Originals muss erhalten bleiben; Korrektur von occurredAt
oder spätere Revision darf den Marker nicht beseitigen. Der Marker wird nicht
allein aus Gerätezeitvergleich oder Formularöffnungsphase abgeleitet.
occurredAt darf während ACTIVE liegen; tatsächliches recordedAt bleibt unverändert.
Die App darf diesen Vorgang nie als zeitnah während ACTIVE dokumentiert darstellen.

Bei einem bereits in ACTIVE erstellten Eintrag bleiben erste Erfassung und spätere
COMPLETED-Korrektur/Ergänzung unterscheidbar. Auch diese Fassung wird als nach
Abschluss erfasst kenntlich gemacht; dadurch wird das Original nicht rückwirkend
zum Nachtrag. Ereignis- und Erfassungszeiten stehen im Detail klar nebeneinander.

Ein vor Abschluss geöffneter Entwurf gewährt keine alte ACTIVE-Berechtigung.
Entscheidend ist die tatsächliche Aktionsphase beim erfolgreichen Speichern:
Speichern in COMPLETED muss die Nachbereitung und deren Kennzeichnung bewusst
sichtbar machen. DELETION_PENDING oder fällige Löschung verhindert regulären
Zugriff; neue Revisionen/Nachträge verlängern Retention nicht automatisch.

## 9. Retention, Personenlöschung und Nachbereitung

P6 hat fachlich eine **eigene Retention-Betrachtung innerhalb der Aktion**:
rechtliche Nachbereitung kann länger erforderlich sein als operative P4-/Task-
Nutzung. Keine konkrete Dauer oder pauschale rechtliche Aufbewahrungspflicht wird
festgelegt. Zweckgebundene endliche Frist, Fristanker und fachkundige Privacy-/
Retentionprüfung bleiben dem späteren Retention-Slice vorbehalten.

Entschieden: keine unbegrenzte Speicherung, eigenständige Archivdatenbank oder
permanente P6-Insel. Vollständige Aktionslöschung ist unverändert die Obergrenze
(RET-02/OD-07), auch bei längerer P6-Frist. Alle Originale, Revisionen, Kennungen
und zugehörigen Relationen folgen derselben P6-Löschentscheidung; keine einzelne
Revision bleibt als versteckte Historie erhalten. Korrektur, Ergänzung oder
Nachtrag startet keine Frist automatisch neu.

Bei Abschluss soll der spätere Retention-Slice ausdrücklich sichtbar machen,
dass Protokolldaten für Nachbereitung noch relevant sein können. Daraus folgt
keine automatische Verlängerung oder neue unbefristete Halteoption. Die notwendige
Aktionsobergrenze ist vor vollständiger Aktionslöschung bewusst abzugleichen.

**Teilnehmerperson löschen:** zerstört oder verändert einen Protokolleintrag
nicht automatisch inhaltlich. Wenn deren strukturierte Betroffenenrelation gelöst
werden muss, bleiben ProtocolEntry und ProtocolRevision bestehen. Ebenso ist eine
optionale REPORTED-Quellenrelation bei erforderlicher Personenlöschung zu lösen;
sie darf keinen verdeckten Personenrestbestand erzwingen. Freitext wird nicht
automatisch geändert, keine historischen Pseudonyme aus P3b rekonstruiert oder
als zusätzliche Kopie angelegt. Eine gelöste Referenz macht den Eintrag nicht
anonym; die UI darf keinen noch auflösbaren Personenbezug vortäuschen.

Ein späterer Personenlösch-Workflow muss deshalb ausdrücklich eine **manuelle
Protokollprüfung einschließlich früherer Fassungen** auf verbleibende Personen-
bezüge berücksichtigen. Wie eine erforderliche Inhaltsbereinigung kontrolliert
umgesetzt wird, ist Teil dieses späteren Privacy-/Löschworkflows, keine automatische
Textmutation, kein gewöhnlicher Einzel-Löschbutton in P6. Dies darf nicht mit
normaler Revisionsbearbeitung oder vermeintlicher Anonymisierung verwechselt werden.

Vollständige Aktionslöschung entfernt ProtocolEntry, alle ProtocolRevision,
Betroffenen- und Quellenrelationen sowie Behördenkennungen und kontrollierte
Ableitungen. Relationstrennung bei Personenlöschung ersetzt diese Kaskade nicht.

Längere Speicherung und Fassungen erhöhen Rekonstruktions-/Offenlegungsrisiken
für Teilnahme, Maßnahmen, Gesundheitsbeobachtungen und Behördenangaben; auch
irrtümliche Altfassungen bleiben sensibel. Bestehende RET-03 bis RET-11 gelten:
fällige Inhalte vor Zugriff bereinigen, keine Security-Ausnahme für Löschung,
Teilfehler nicht als Erfolg, keine Secure-Overwrite-/physische Flash-/RAM-Garantie.
Keine Retentionimplementierung oder Worker in diesem Slice.

## 10. UX: fokussierte Erfassung und Chronologie

**Lagebild → Protokoll → Schnellvorlage oder freier Eintrag → Detail → Chronologie.**
Die Hubkarte „Protokoll“ erscheint erst nach implementierter und geprüfter
Funktion. Keine tote Karte im bestehenden UX3. Security-Header und bestehender
Session-Neustart bleiben vom fachlichen Header getrennt und unverändert.

Übersicht: klare Rücknavigation „Zum Lagebild“, Überschrift „Aktionsprotokoll“,
„Schlüsselereignis erfassen“ (Vorlagenauswahl plus freies Schlüsselereignis) und
„Relevanten Eintrag erfassen“.

Hubkarte nach Umsetzung ausschließlich kompakte Metadaten, beispielsweise Anzahl
Schlüsselereignisse und Zeitpunkt des letzten Eintrags. „Letzter Eintrag“ meint
letzte erste Erfassung (ORIGINAL.recordedAt), nicht den jüngsten behaupteten
Ereigniszeitpunkt. Keine Namen, Behördenkennungen oder Freitextvorschau im Hub.

Chronologische Liste mit Datumstrennern;
Filter **Alle / Schlüsselereignisse** sichtbar, Selected-Zustand als Text/Semantik.
Leeres Filterergebnis von tatsächlich leerem Protokoll und laufendem Laden trennen.
Schlüsselereignis, ungefähre Zeit, Korrektur, Irrtum und Nachtrag nach Abschluss
ausdrücklich textlich kennzeichnen; keine Bedeutung ausschließlich über Farbe.

Entwurf: zunächst Titel/Art, Ereigniszeit/„ungefähr“ und kurze Beschreibung.
Weitere Angaben über klar benannte optionale Bereiche, keine lange Pflichtform.
Zeitpunkt „jetzt“ vorbefüllt sichtbar und änderbar; Datum-/Zeitpicker verwenden
bestehende DST-Validierung. Quellen-, Wortlaut- und Behördenangaben erst bewusst
öffnen. Hinweise auf Datensparsamkeit und ungeprüfte zugeschriebene Angaben.
Speichern/Abbrechen als große Controls; neue freie Titelform einmalig fokussieren,
keine Recomposition-Fokusschleife oder verdeckte Save-Navigation durch Keyboard.

Speichern öffnet das fokussierte Detail mit Erfassungszeit und klarer Rückkehr zur
Chronologie. Dort explizit „Korrigieren“, „Ergänzen“, „Als irrtümlich markieren“
bei zulässigem Status; Fassung und Änderungen vor Speichern zeigen. Historie
nachrangig über „Frühere Fassungen“, nicht als zweite Timeline voller Duplikate.
Detail zeigt vollständigen Inhalt, betroffene Pseudonyme, Quellenart, optionale
mitteilende Person, Kennungen, occurredAt/recordedAt und Revisionsverlauf.
COMPLETED-Einstieg bezeichnet neue Einträge ausdrücklich als Nachtrag nach
Abschluss; kein normaler Einzel-Löschcontrol. Kennungen nur im geöffneten Detail.
Keine allgemeine Sammelnotiz oder vermeintliche Rechtsberatung. Änderungen erhalten erst nach gültiger Publication
das bestehende ruhige Erfolgsfeedback.

Initial LOADING → vollständige Chronologie einschließlich Filterzählungen;
Refresh erhält gültigen alten Inhalt bis vollständigem Ersatz. Alte Request-,
Aktions- oder Generationsresultate publizieren nicht. Lock/Generationwechsel
verwerfen Detail, Filter, Offenlegungen und alle Entwürfe sofort. Navigation und
Entwürfe ausschließlich RAM, kein SavedState/rememberSaveable/Prefs/DataStore.
Keine Sessionverlängerung durch Ladeergebnisse, Zeitdarstellung oder UI-Ticks.

Accessibility: mindestens 48-dp-Touchziele, Hauptaktionen möglichst 56 dp,
Heading-/Button-/Selected-/Expanded-Semantik, klarer Rückweg oben, logische
TalkBack-Reihenfolge. Font Scale 2 und schmale Displays: einspaltig/umbrechend,
keine abgeschnittenen Textcontrols oder Mini-Chips als einzige Navigation.
Keine sekündliche LiveRegion und keine zusätzliche Warnungswand.

## 11. SENSITIVE-Klassifizierung, operativer Zugriff und Schutzgrenzen

**Entschieden:** alle P6-Protokolldaten sind fachlich SENSITIVE. Dazu zählen
polizeiliche Maßnahmen, beobachtete Namen/Dienst-/Kennnummern, Aussagen,
Betroffenenreferenzen, Freitext und frühere Fassungen. PRIV-06 wird nicht allgemein
abgeschwächt; es findet keine Herabstufung auf INTERNAL statt.

**Enge Zugriffsentscheidung:** normale P6-Arbeit während gültigem READY benötigt
keinen zusätzlichen P3b-/Leadership-Step-up. Sensitivitätsklasse und UI-
Zugriffsmodus sind getrennte Konzepte. Nutzen ist schnelle Dokumentation einer
laufenden Aktion und bewusste Nachbereitung ohne zusätzlichen Freigabeschritt.
Risiko: Angreifer in offener READY-Sitzung können den vollständigen Bestand samt
früheren Fassungen sehen. Verschlüsselung ruhender Daten beseitigt dieses Risiko
nicht; Detail-only Kennungen sind keine eigene Zugriffsgrenze. Dieser Trade-off
ist ausdrücklich nur für P6 entschieden, kein allgemeiner Zugriff auf SENSITIVE.

Konzeptionell bestehende verschlüsselte SQLCipher-Produktstorage mit gültiger
READY-Sitzung, richtiger Sessiongeneration, Action-/Statusbindung und unveränderten
Storage-Gates vor Read, Arbeitsschritt, Commit-Zulassung und Publication.
P3b-Identitäts-/Kontaktdaten bleiben separat geschützt, keine automatische Übernahme
oder Freigabe. Normale Geräteauthentisierung ist kein Leadership-Grant.

Unverändert bleiben DEVICE_CREDENTIAL, 180 s absolut, 30 s READY-Inaktivität,
Foreground-/Device-Lock, Session-Neustart, StorageAccess, LeadershipGrant/
Key-Admission, Keystore/Crypto, DB-Key-Lifecycle, FLAG_SECURE und Backup-Ausschluss.
Keine neue Securityarchitektur oder technische Implementierungsfreigabe.

Ausgeschlossen: INTERNET/Netzwerk, Cloud, Telemetrie, Notifications, Worker,
Clipboard-Automatik, externe Viewer/Android-Dateifreigabe, Export, Kamera,
Fotos/Video/Audio/Dateien/Anhänge, GPS/automatische Standorterfassung, automatische
P3b-Übernahme oder P4-/P5a-Mutation, externe Zeitstempel und eigene Kryptografie.
Kein Klartext in Logs/Fehlermeldungen, keine Screenshotausnahme. Externe Fotografie,
fremde IME, kompromittiertes OS, Schlüsselverlust und physische Speicherreste
bleiben bestehende Grenzen.

**Keine Zusage von Beweissicherheit, forensischer Integrität, Gerichtsfestigkeit,
Manipulationssicherheit, vertrauenswürdigen Zeitstempeln oder rechtlicher
Verwertbarkeit.** Juristische Prüfung und Beweisprüfung bleiben eigenständig.

## 12. Verbleibende spätere Prüfungen

Die P6a.1-Produktentscheidungen zu SENSITIVE/Zugriff, PRIV-03-Ausnahme,
Mehrfachbetroffenen, COMPLETED-Nachträgen, Revisionstypen, Quellenart,
Kennungstypen, Personenlöschwirkung und Feld-/Anzahlgrenzen sind **entschieden**.
Sie werden nicht erneut als offene Produktentscheidungen geführt.

| Verbleibender Punkt | Separater Zeitpunkt / Grenze |
| --- | --- |
| Retentiondauer und Fristberechnung | Fachkundige Retention-/Privacyentscheidung: endliche Dauer, Anker/Obergrenzen und Abschlussreview. P6 bleibt innerhalb der Aktionsobergrenze. |
| Konkrete rechtliche Anforderungen vor Echtdaten | Zweck/Verantwortlichkeit, Erfassung/Weitergabe von Behördenangaben, mögliche Gesundheitsbezüge, Betroffenenrechte und erforderliche Löschung separat juristisch/Privacy prüfen. Keine pauschale Zulässigkeitsbehauptung. |
| Personenlösch-Workflow | Manuelle Prüfung alter/neuer Protokollinhalte konkretisieren; Relationstrennung und fehlende automatische Textänderung sind bereits entschieden. |
| Technische Umsetzung | Separate Implementierungsspezifikation: Room-/Migrationdetails, sichere atomare Fassungsspeicherung, Konflikterkennung, Gates/Publication, tatsächliche Phasenkennzeichnung und Tests. Noch keine Tabellen/Indizes/Schema-Version. |
| Späterer sicherer Export | Eigene Anforderung und Review für Zweck/Empfänger, Freigabe, Redaktion sämtlicher Fassungen/Kennungen und Schutz externer Kopien. Für P6a ausgeschlossen. |
| Anhänge/Fotos/Audio/Video | Eigener künftiger Product-/Privacy-/Security-Slice, nicht Voraussetzung oder versteckter Teil von P6. |
| Zusätzliche Integritätsmechanismen | Nur bei späterer eigener Anforderung untersuchen; heute keine Hash-Chain/Signaturen/eigene Kryptografie oder Garantie. |

Die fachlichen Entscheidungen sind Grundlage des nächsten Spezifikationsslices,
keine heutige Codefreigabe. Vor Echtdaten bleiben fachkundige Privacy-/Rechtsprüfung
und Prüfung der tatsächlichen Implementierung erforderlich.

## 13. Konzeptionelles Datenmodell und spätere Abnahme

P6 ist keine Note-Erweiterung. Fachliche Elemente:

| Element | Beziehung / Inhalt |
| --- | --- |
| ProtocolEntry | Stabile Identität innerhalb genau einer Aktion; besitzt erste ORIGINAL-Fassung und weitere Fassungen. Erfassungsphase des Originals erhält die dauerhafte Nachtragskennzeichnung. |
| ProtocolRevision | Genau ein Entry; vollständiger Inhalt, Revisionstyp, occurredAt/Approximate, eigenes recordedAt und Erfassungsphase. Keine stille Mutation gespeicherter Fassungen. |
| ProtocolAffectedPerson | 0..n betroffene bestehende Personen derselben Aktion je Fassung; reiner Dokumentationsbezug. Keine künstliche kleine Anzahlgrenze. |
| ProtocolOfficialIdentifier | 0..32 beobachtete typisierte Angaben je Revision, kein eigenständiges Einsatzkräfteprofil. |
| ProtocolReportedByPerson | Optional höchstens eine mitteilende bestehende Person derselben Aktion je REPORTED-Fassung; unabhängig von Betroffenen. |

Diese Beziehungen sind fachlich beschrieben, nicht als konkrete Room-Tabellen,
Indizes, Foreign-Key-Cascades oder Schema-Version festgelegt. Fachlicher Inhalt
und Größen stehen in Abschnitt 3, Zeiten in Abschnitt 4, Quellenart und Kennungen
in den folgenden Abschnitten. P4/Task-Entities und TaskEvent bleiben unverändert.

Für den separaten Implementierungsslice abzuleiten:

- Beide Arten frei und aus Vorlagen, explizite Speicherung/Cancel; keine P4-/Task-
  Mutation, P3b-Reads oder automatische Klassifizierung.
- Alle getrimmten Codepointgrenzen, 32 Kennungen, mehrere Betroffene, optionale
  Quellenart/REPORTED-Bezug gleicher Aktion; keine stille Kürzung oder Freitextumgehung.
- occurredAt beim Vorlagenentwurf, recordedAt je erfolgreicher Fassung;
  DST Gap/Overlap reject, ungefähre Zeit, Tages-/Zonenwechsel und Uhrverstellung.
- Original und Revisionen nachvollziehbar, Konflikte ohne Überschreiben,
  MARKED_MISTAKEN sichtbar, jüngste wirksame Fassung konsistent.
- ACTIVE/COMPLETED-Statusgrenzen einschließlich Entwurf über Abschluss hinweg;
  Nachtragmarker dauerhaft und unabhängig von korrigierten Zeiten.
- Kein normaler Einzel-Löschpfad; Personreferenztrennung ohne automatische
  Inhaltszerstörung, Aktions-/P6-Löschung ohne versteckte Altfassungen/Kennungen.
- Hub nur kompakte Metadaten; Timeline nach occurredAt mit textlichen Markern,
  Kennungen ausschließlich im Detail/Revisionsverlauf.
- Atomic Reveal/Refresh und stale Request/Generation/Action; Lock-Cleanup aller
  Entwürfe/Details/Filter, Displayupdates ohne Sessioninteraction.
- Font Scale 1/2, schmale Breite, TalkBack und Fokus/Keyboard;
  Manifest-/Privacyregressionen, keine forensischen/juristischen Testgarantien.

Noch keine Produktfunktion, Migration, Builds oder Gerätetests für P6. Dieser
Dokumentationsslice wird durch Konsistenz-/Link-/Scope- und Diffprüfung geprüft.
