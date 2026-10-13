# P6b — Technische Implementierungsspezifikation Aktionsprotokoll

## 1. Status und tatsächliche Baseline

Analyse/technischer Plan, **keine Umsetzung**. [P6a.1](product-p6a-protocol.md)
ist verbindlich; Entscheidungen zu SENSITIVE/READY, Revisionen, Nachträgen,
Quellen, Kennungen, Textgrenzen und Löschung werden nicht erneut geöffnet.

Geprüfte lokale Baseline: sauberer Branch `p6a-protocol-design`, HEAD `ac67029`
(Define protocol product and privacy model). Abweichung von der Aufgabenangabe:
lokaler main steht auf `e1c3cdd`; HEAD ist dort noch nicht enthalten. Es wurde
kein Checkout/Merge/Fetch vorgenommen. Dieser Plan basiert auf dem vorhandenen
committeten P6a.1 und Produktcode nach UX3, nicht auf einem behaupteten main-Merge.
Vor Umsetzung Baseline/Schema erneut prüfen; falls inzwischen geändert, diesen
Plan zuerst abgleichen, nicht blind eine zweite Migration 6 → 7 anlegen.

Grundlagen: [Produkt](../PRODUCT.md), [Datenmodell](data-model.md),
[Privacy](privacy.md), [Security](security.md), [Architektur](architecture.md),
[Threat Model](threat-model.md), [Retention](data-retention.md),
[P4](product-p4.md), [P5a](product-p5a.md), [UX](ux-philosophy.md) und
[Roadmap](ux-roadmap.md). Nachweise/Abnahmeplanung: [testing.md](testing.md).

Tatsächlich gelesen unter `data/src/main/kotlin/org/pag/data/product/`:
ActionDatabase, ProductRuntime, ProductActionRepository/ActionMutations,
ProductPersonRepository/PersonDatabase, ProductOperationsRepository/
OperationsDatabase und ProductTaskRepository/TaskDatabase; außerdem
`domain/.../StorageAccess.kt`, `Tasks.kt`, `Operations.kt`,
`data/.../SessionCoordinator.kt` und `app/.../ProductTime.kt`.

- ActionDatabase: **Schema 6**, exportSchema=true, zehn Entitytypen;
  registrierte additive Migrationen 1→2→3→4→5→6, Export 6 vorhanden.
- Room 3 über bestehenden kontrollierten SQLCipher-Driver; Activity-eigene
  ProductRuntime, `noBackupFilesDir/pag-db-v1`, bestehender DB-Key-Owner.
- Repositories: READY-Context, Worker, kurze Room-Lebensdauer,
  Read-/Write-Transaktionen, STEP/COMMIT und main-Contextpublication.
- ActionDatabaseAccess prüft vorhandene Version derzeit 1..6; Migrationregistrierung,
  neue obere Versionsgrenze und frische Schemaerzeugung müssen später gemeinsam
  angepasst werden. Kein destructive fallback.
- PersonDao/PersonRepository haben List/Get/Create/Rename, **keinen Personendelete**.
  ActionDao/Repository haben ebenfalls keinen vollständigen Aktionsdelete.
  Leadership.deletePerson/deleteAction bereinigen P3b-Bereiche/Root, nicht die
  Person-/Action-Zeile. Diese Pfade sind kein allgemeiner Retentionworkflow.

## 2. Schichten und Umfang

Domain: reine P6-Modelle, Regeln, statische Vorlagen, Repositoryport und Projektionen.
Data: P6-Rows/DAO/Store, additive Migration, Constraintinstallation und Repository.
ProductRuntime verdrahtet später einen P6-Port mit **denselben** StorageAccess-,
Inventory-, DB-Key-Owner-, Worker-/main- und blocked-Verträgen wie P5a.
App: eigener RAM-only Model-/UI-Zustand über Domainport, keine DAO-/Key-Nutzung.
Keine zusätzliche allgemeine Repositoryplattform, Lockhierarchie oder Cachearchitektur.

Alle P6-Daten bleiben SENSITIVE in bestehender SQLCipher-Produkt-DB. Normales
READY ohne zusätzlichen Leadership-Step-up ist der bereits entschiedene Trade-off,
keine Herabstufung oder allgemeine Freigabe von SENSITIVE. Kein P3b-Payloadread,
Envelope, eigener Key, Crypto-, Keystore- oder Dateipfad. Detailanzeige ist keine
neue Authgrenze. Offene Sessions und kompromittierte Systeme bleiben Risiken.

## 3. Tabellenmodell: fünf kleine P6-Tabellen

Die folgenden Namen/Felder sind technische **Spezifikation**, keine erzeugten
Room-/Schema-/SQL-Dateien. IDs wie bestehende Produktobjekte: zufällige UUID-Strings,
keine personenbezogenen Werte. Enums als stabile TEXT-Namen, Zeitwerte INTEGER
für Long-Epoch-Millis, Booleans als INTEGER 0/1.

### ProtocolEntry → protocol_entries

| Feld | Vertrag |
| --- | --- |
| id | Primärschlüssel, unveränderlich. |
| actionId | Nicht null, genau eine bestehende Aktion. |
| createdAfterCompletion | Nicht null Boolean; Repository setzt aus persistierter Phase beim ORIGINAL-Speichern, niemals später umschreiben. |

Kein currentRevisionId, dupliziertes createdAt, rowid als fachlicher Tie-Breaker
oder zusätzlicher globaler Erstellungszähler. Erste Erfassungszeit folgt aus
Revision 1; stabile Eintrags-ID reicht als letzter Tie-Breaker gemäß P6a.1.

### ProtocolRevision → protocol_revisions

| Feld | Vertrag |
| --- | --- |
| id, actionId, entryId | Eigene PK; dieselbe Aktion wie Entry. |
| revisionNumber | Positiver Long, ORIGINAL=1, danach genau previous+1. Overflow ablehnen, nicht umbrechen. |
| revisionType | ORIGINAL / CORRECTION / ADDENDUM / MARKED_MISTAKEN. |
| classification | KEY_EVENT / RELEVANT; bewusst gewählt, nicht aus Text abgeleitet. |
| templateKey | Optionaler stabiler Herkunftsschlüssel, keine Unique-Regel oder Rechtssemantik. |
| title | Getrimmt, 1..120 Unicode-Codepoints. |
| description | Nullable, getrimmt, maximal 2000 Codepoints; auch beobachtete Maßnahme/Erklärung, kein weiteres Notizfeld. |
| occurredAt | Ereignis-Instant aus Eingabe, Long. |
| occurredAtApproximate | Boolean, immer vorhanden. |
| recordedAt | Repository-Wanduhr beim Speichervorgang, nicht im Input, pro Fassung unveränderlich. |
| recordedAfterCompletion | Boolean aus persistierter Actionphase dieser Revision; macht auch spätere Revisionen eines ACTIVE-Originals ehrlich kenntlich. |
| mistaken | Boolean, wirksamer Irrtumszustand dieser vollständigen Fassung, Regeln in Abschnitt 5. |
| sourceType | Nullable OWN_OBSERVATION / REPORTED / OFFICIAL_ANNOUNCEMENT / UNCLEAR. |
| placeLabel | Nullable, getrimmt, maximal 80 Codepoints. |
| announcementText | Nullable, getrimmt, maximal 2000 Codepoints. |
| announcementKind | Nullable WORDING / PARAPHRASE; genau dann gesetzt, wenn announcementText vorhanden ist. Bildet P6a-Wortlaut/Sinngemäß ab, keine zusätzliche Textart. |
| statedLegalBasis | Nullable, getrimmt, maximal 1000 Codepoints; ungeprüfte Angabe laut Polizei. |

Jede Fassung enthält vollständig alle aktuellen Werte. Kein Patchmodell,
kein Implizieren ausgelassener Felder aus früheren Fassungen. Keine Pseudonym-
oder Klarnamensnapshots. Sensitivität ist durch P6-Typ/Port SENSITIVE festgelegt;
kein nutzeränderbares Klassifizierungsfeld nötig.

### ProtocolAffectedPerson → protocol_affected_persons

Felder actionId, revisionId, personId (alle nicht null).
PK (actionId, revisionId, personId) sichert Set-Semantik.
0..n vorhandene Personen derselben Aktion, keine kleine UI-Maximalzahl.
Keine separate ID/Order-Spalte; Anzeige nach bestehendem PersonDao-Vertrag
persons.createdAt ASC, persons.id ASC. Fachliche Menge bleibt unabhängig davon.

### ProtocolOfficialIdentifier → protocol_official_identifiers

Felder actionId, revisionId, position, type, value (alle nicht null).
PK (actionId, revisionId, position), position ganzzahlig 0..31.
Repository vergibt lückenlos in bewusst gewählter Eingabereihenfolge 0..n−1;
Position ist ausschließlich lokale Reihenfolge, kein globales Identifier-ID.
Typen SERVICE_OR_ID_NUMBER, DISPLAYED_NAME, UNIT_OR_VEHICLE_ID,
OTHER_OFFICIAL_ID; Wert getrimmt, 1..120 Codepoints.

Keine globale Normalisierung, Suche, Unique(type,value), automatische
Zusammenführung gleicher Werte oder Wiedererkennung über Revisionen/Entries/
Aktionen. Auch exakt gleiche type+value dürfen in derselben Revision stehen:
P6a verbietet automatische Zusammenführung; wir führen kein neues Verbot ein.
Sie belegen eigene Positionen und zählen zur Obergrenze 32. UI kann die einzelne
Entwurfszeile bewusst entfernen, ohne andere Beobachtungen zusammenzuführen.

### ProtocolReportedByPerson → protocol_reported_by_persons

Felder actionId, revisionId, personId (nicht null).
PK (actionId, revisionId): maximal eine mitteilende Person je REPORTED-Fassung.
Keine Row heißt kein Quellenpersonenbezug, unabhängig von sourceType=REPORTED.

**Bewusste Wahl eigener Relation statt reportedByPersonId auf Revision:** ein
zusammengesetztes ON DELETE SET NULL würde auch das nicht-null actionId nullen.
RESTRICT + gezieltes Revision-Update wäre technisch möglich, mutierte aber eine
ansonsten unveränderliche Fassung und benötigte eine Sonder-Update-API.
Die kleine Relation erlaubt reine Personenreferenzbereinigung ohne Inhaltsupdate,
analog Betroffenen. Sie ist die konkrete Umsetzung der konzeptionellen Relation,
keine zusätzliche Quelle-/Autoren-/Zeug:innen-Entity. sourceType bleibt auf Revision.

## 4. Same-Action-FKs und Löschwirkung

| Child → Parent | Zusammengesetzte Beziehung | ON DELETE |
| --- | --- | --- |
| Entry → Action | actionId → actions.id | CASCADE |
| Revision → Entry | (actionId,entryId) → entries(actionId,id) | CASCADE |
| Affected → Revision | (actionId,revisionId) → revisions(actionId,id) | CASCADE |
| Affected → Person | (actionId,personId) → persons(actionId,id) | CASCADE, ausschließlich Relationsrow |
| Identifier → Revision | (actionId,revisionId) → revisions(actionId,id) | CASCADE |
| ReportedBy → Revision | (actionId,revisionId) → revisions(actionId,id) | CASCADE |
| ReportedBy → Person | (actionId,personId) → persons(actionId,id) | CASCADE, ausschließlich Relationsrow |

Alle Parentpaare benötigen exakt passende UNIQUE-Indizes; persons(actionId,id)
besteht bereits. Child-actionId verhindert eine Revision zu Entry anderer Aktion
und fremde Personenreferenzen auch bei direktem DB-Insert. Alle ON UPDATE NO ACTION;
IDs/Aktionszuordnung werden nicht umgeschrieben. Kein Person→Revision-Cascade.
Keine eigene Action-/Polizeientität für Kennungen.

Repository prüft zusätzlich im selben kontrollierten Vorgang Parentstatus und
alle Referenzen. Kein Vertrauen auf FK allein. Cross-Action-Versuche erzeugen
NOT_FOUND/WRONG_ACTION ohne Inhalte der anderen Aktion offenzulegen.

## 5. Aktuelle Revision, Stale und Irrtumszustand

Aktuelle Fassung = höchste revisionNumber je Entry, nicht maximale recordedAt:
Wanduhr kann zurückgestellt sein. ORIGINAL ist exakt Revision 1; alle späteren
Fassungen haben nächste Nummer. UNIQUE(entryId,revisionNumber) verhindert Doppelung.
Keine Lücken aus fehlgeschlagenen Writes; nächste Nummer wird in Schreibtransaktion
berechnet und nicht im UI reserviert. Detail ordnet alle Fassungen aufsteigend 1..n.

ExpectedRevision umfasst Entry-/Revision-ID/-Nummer **und den vollständigen
normalisierten gespeicherten Snapshot einschließlich Betroffenen-/Quellenbezug und
Identifierreihenfolge**. Pseudonymanzeigetexte gehören nicht zum Vergleich.
Nur ID/Nummer wäre gegen normale Revisionraces ausreichend, aber gegen spätere
Personenrelationstrennung nicht: diese verändert keine Revisionsnummer. Vollvergleich
verhindert auch das Wiedereinsetzen inzwischen gelöster Bezüge aus einem alten Entwurf.
Snapshot wird serverseitig innerhalb derselben Schreibtransaktion verglichen;
kein separater Read-vor-Write oder blindes Insert nach UI-Check. Stale → STALE,
kein Write; neu lesen und bewusst neu speichern. Keine automatische Mergefunktion.

**mistaken als expliziter Zustand pro vollständiger Fassung:** RevisionType allein
reicht nicht, weil ADDENDUM nach Irrtumsmarkierung diese nicht automatisch aufheben
darf. ORIGINAL setzt false, MARKED_MISTAKEN true, ADDENDUM übernimmt Vorgängerzustand.
CORRECTION erhält Zustand oder darf ihn bewusst zurücknehmen, wie P6a.1 erlaubt.
Nur ausdrückliches Zurücknehmen in CORRECTION setzt true→false; kein impliziter
Reset beim Formularöffnen. Eine false→true-Markierung läuft über MARKED_MISTAKEN,
nicht verborgen über gewöhnliche Korrektur. Markierung erneut bei bereits true
ablehnen (INVALID_STATE), statt doppelte unbegründete Markierungsrevision.

reviseEntry verarbeitet CORRECTION/ADDENDUM; markMistaken verwendet eine eigene
kleine Methode, kopiert die aktuelle vollständige Fassung samt Children und setzt
nur Typ/Zustand/Repository-Erfassungsmetadaten. Kein beliebiger Nutzereingriff in
Originaltyp, Nummer oder recordedAt. Eine spätere CORRECTION/ADDENDUM nach
MARKED_MISTAKEN ist erlaubt; ADDENDUM bewahrt den Irrtumsmarker.
Timeline/Detail zeigen mistaken=true ausdrücklich, ohne Löschen oder stilles Verbergen.

## 6. Zeit, Erfassungsphase und Nachträge

Input enthält occurredAt und occurredAtApproximate, **kein recordedAt**, keine
Revisionnummer oder vom Benutzer kontrollierte Abschlussflags. Lokale Picker
verwenden existing selectedInstant(LocalDateTime,ZoneId): genau ein Offset,
Gap/Overlap reject. Repository erhält eindeutigen Instant; es kann aus Epoch-Millis
nicht beweisen, wie ein externer Caller lokale Zeit ausgewählt hat. DST-Tests gehören
auch an die tatsächliche Inputkonvertierung. Keine neue Normalisierung/Zeitzone.

Injizierbare Wanduhr analog P4/P5a: System.currentTimeMillis in Produktion,
deterministische Fake-Uhr in Tests. Repository sampelt nahe am Revision-Insert
innerhalb der Write-Transaktion, erst nach erfolgreicher Validierung. recordedAt
ist nur bei erfolgreichem Commit gespeicherte Erfassungszeit. Es ist keine exakte
physische Commitzeit oder externe Beglaubigung. Bei Rollback bleibt kein recordedAt;
Retry sampelt neu. CPU-/I/O-Verzögerung oder Uhrverstellung begründen keine
Monotoniebehauptung. Sessionzeit bleibt unverändert elapsedRealtime.

Entry.createdAfterCompletion aus persistiertem Status bei ORIGINAL; unveränderlich.
Revision.recordedAfterCompletion aus Status bei jedem Append. ORIGINAL-Flag und
Entry-Flag müssen identisch sein. ACTIVE-Original + COMPLETED-Korrektur: Entryflag
false, neue Revisionflag true. COMPLETED-Original bleibt true auch nach Zeitkorrektur.
Kennzeichnung nie nur aus aktuellem ActionStatus/occurredAt/recordedAt-Vergleich.

Schreibkommando trägt zusätzlich expectedActionStatus als bestätigten UI-Kontext,
kein Autorisierungsflag. Wechsel ACTIVE→COMPLETED seit Entwurf → STALE, bevor Write.
Neu lesen, bewusste Nachbereitung bestätigen und mit aktuellem Kontext erneut
aufrufen. Repository setzt Flags selbst aus persistiertem Status; keine alte
ACTIVE-Zulassung oder stille Umwandlung eines Entwurfs zum Nachtrag.

Timeline ASC: aktuelle occurredAt, ORIGINAL.recordedAt, entryId (stabile binäre
TEXT-Sortierung). Dies bewahrt exakt P6a.1; keine zusätzliche rowid-Reihenfolge.
Bei zurückgestellter Wanduhr bleibt Ordnung deterministisch, nicht bewiesene Realzeit.

## 7. Statische Vorlagen und Inputvalidierung

Keine persistierte Template-Tabelle. Statische Domaindefinitionen wie P5a,
stabile Keys, jeweils normaler Entwurf; occurredAt einmalig beim Eintritt auf jetzt,
erst Save persistiert. Freier Eintrag beider Arten: templateKey=null.
Die „Eigenes Schlüsselereignis“-Auswahl besitzt CUSTOM_KEY_EVENT, leeren Titelentwurf.

| Key | Freigegebene Vorlage |
| --- | --- |
| POLICE_ARRIVED | Polizei eingetroffen |
| ORDER_OR_ANNOUNCEMENT | Anordnung / Ansage |
| DEADLINE_SET | Frist gesetzt |
| EVICTION_ANNOUNCED | Räumung angekündigt |
| FORCE_THREATENED | Zwang angedroht |
| EVICTION_STARTED | Räumung begonnen |
| PHYSICAL_MEASURE_OR_FORCE | Körperliche Maßnahme / Zwangsmittel angewendet |
| EVICTION_ENDED | Räumung beendet |
| IDENTITY_CHECK_OR_ED | Personalienfeststellung / ED |
| EXCLUSION_ORDER | Platzverweis |
| SEARCH | Durchsuchung |
| SEIZURE_OR_CONFISCATION | Sicherstellung / Beschlagnahme |
| PERSON_TAKEN_AWAY | Person abgeführt |
| CUSTODY | Gewahrsam |
| PERSON_RELEASED | Person freigelassen |
| MEDICAL_CARE_OR_INJURY | Medizinische Versorgung / Verletzung |
| POLICE_OPERATION_ENDED | Polizeieinsatz beendet / Kräfte abgerückt |
| OTHER_POLICE_MEASURE | Sonstige polizeiliche Maßnahme |
| CUSTOM_KEY_EVENT | Eigenes Schlüsselereignis |

templateKey optional, nicht unique. Bei Original aus Vorlage KEY_EVENT; danach
Herkunftskey in vollständige Revision übernehmen, kein nachträgliches Umschreiben
oder Ableiten der aktuellen classification/Rechtsqualifikation aus diesem Key.
Original mit unbekanntem Key ablehnen; bewusst gewählte classification kann durch
CORRECTION geändert werden, Herkunft bleibt nur Herkunft. Keine automatische Anlage.

Text trim wie bestehende Regeln; optionale Leerstrings null. Grenzen:
title 1..120, description/announcementText je ≤2000, statedLegalBasis ≤1000,
placeLabel ≤80, Kennungswert 1..120, Kennungsanzahl ≤32. Character.codePointCount
wie P4/P5a, nicht String.length; keine neue Unicode-Normalisierung/Casefolding.
Normalisierte gespeicherte Werte erneut beim Row-Mapping validieren, unerwartete
Enum-/Typ-/Invariantenwerte als geschützter Storagefehler, nicht Empty behandeln.

affectedPersonIds als Menge; gleiche Person maximal einmal. sourceType optional;
ReportedBy optional **nur** bei REPORTED. OFFICIAL_ANNOUNCEMENT benötigt keine
Quellenperson, vorhandene Ansage/Wortlaut + attributed legal basis genügen.
announcementKind und announcementText gemeinsam gesetzt oder beide null.
Keine unbegrenzten Zusatzfelder, Klarnamen, Identitäts-/Kontaktkopien.

## 8. Repository, Projektionen und Resultate

Kleiner P6-Domainport, im bestehenden callback/publish-Stil, keine UI-Navigation
oder frei nutzbare Datenbankfunktion. Konzeptionelle Methoden (keine Codeimplementierung):

- summary(actionId, publish): ProtocolSummary.
- listTimeline(actionId, publish): vollständige Timelineprojektion.
- getEntry(actionId, entryId, publish): ProtocolEntryDetail.
- createEntry(actionId, expectedActionStatus, input, publish): ORIGINAL plus Entry.
- reviseEntry(actionId, expectedActionStatus, expectedRevision,
  revisionType CORRECTION/ADDENDUM, input, publish).
- markMistaken(actionId, expectedActionStatus, expectedRevision, publish).

Kein normaler delete/updateRevision-Endpunkt. Input enthält vollständige fachliche
Werte und Children; Aufnahme-/Revisionsmetadaten kommen ausschließlich vom Repository.
Create/Append geben minimal ProtocolSaved(entryId,revisionId,revisionNumber) zurück;
Detail/Timeline anschließend über geschützte Reads, keine ungültigen Optimismusdaten.
CORRECTION-Input kann bewusstes Zurücknehmen des Irrtumsmarkers ausdrücken;
ADDENDUM/Markieren/Original haben keinen beliebig setzbaren mistaken-Input.

**ProtocolSummary:** keyEventCount und nullable lastEntryRecordedAt, letztes Original
nach ORIGINAL.recordedAt (MAX), nicht späteste occurredAt oder letzte Korrektur.
Count umfasst aktuell als KEY_EVENT klassifizierte Einträge, auch sichtbar als
irrtümlich markierte; die Zählung ist Eintragszahl, keine Zahl gesicherter Vorfälle.
Leerer erfolgreicher Read = 0/null; vor Load keine Fake-Nullwerte.
MAX(recordedAt) beschreibt die größte gespeicherte Wanduhrzeit, nicht bei
Uhrverstellung garantiert die zuletzt physisch erfolgte Erfassung.
Query liest nur Entry/aktuelle Revision-Klassifikation und Original-Erfassungszeit,
keine Titel, Betroffene, P3b, Kennungen oder langen Textfelder.

**ProtocolTimelineItem:** entryId, aktuelle revisionId/Number/Type, classification,
templateKey/title, occurredAt/Approximate, aktuelle recordedAt und
recordedAfterCompletion, Entry.createdAfterCompletion, mistaken,
originalRecordedAt als Sortierhilfe; hasRevisions = revisionNumber>1.
Explizite ausgewählte Spalten, kein SELECT * plus spätere UI-Redaktion.
Keine description/place/announcement/legal basis, Betroffene, Kennungen oder P3b.
KEY_EVENT-Filter auf genau aktuelle classification; anfangs Projektion filtern,
keine zweite Klassifikationsregel. Beide Filter erhalten gleiche stabile Ordnung.

Aktuelle Rows über höchste Nummer in derselben Read-Transaktion bestimmen,
Originalzeit aus Revision 1; fehlende/inkonsistente Originale sind Storagefehler.
Index für Entry/Nummer unterstützt latest-Lookup. SQL ORDER BY auf Projektion,
keine UI-Listenposition oder uneinheitliche Nachsortierung.

**ProtocolEntryDetail:** Entry-Metadaten und alle vollständigen Revisionen in
Nummernfolge, deren betroffene Personen und REPORTED-Person ausschließlich als
aktuelle P3a-Pseudonymprojektionen, Quellenart, Identifier nach position, Zeit-/
Irrtums-/Nachtragsmarker. Einzelne aufgelöste Personen nur bei benötigten IDs,
kein voller Operations-/TaskBoardread, Leadershipinventory oder P3b-Decrypt.
Children für diesen Entry in gebündelten actiongebundenen Reads; keine Query pro
Kennung. Kein historischer Pseudonymsnapshot. Keine Row nach Relationstrennung
heißt kein auflösbarer Bezug; kein erfundener Name.

Result-/Errorsemantik P6-spezifisch analog OperationsResult:
Value<T> oder Rejected(NOT_FOUND, WRONG_ACTION, INVALID_STATE, INVALID_INPUT, STALE).
NOT_FOUND für fehlende actiongebunden gesuchte Entry-/Personreferenz; kein fremdes
Objekt zum Diagnostizieren laden. WRONG_ACTION für bereits im Kommando widersprüchliche
Aktionsbindung; keine Preisgabe fremder Existenz. INVALID_STATE für persistierte
verbotene Phase/unerlaubten Revisionsschritt, INVALID_INPUT für Fachvalidierung,
STALE für abweichenden erwarteten Snapshot/Status. IN_USE wird nicht benötigt.

SESSION_REVOKED ist **kein** fachlicher Datenfehler: wie bestehende Repositories
bei fehlendem acquire oder ungültigem Context keine operative Callbackpublication,
Session-Cleanup übernimmt. Technische DB-/Integritäts-/Mappingfehler bei noch
zulässigem Context → bestehendes blocked/fail-closed Verhalten, keine sensitiven
Exceptions/Logs oder leere Erfolgsansicht. Erwartete Validierungs-/Konfliktrejects
sind keine BLOCKED-Ursache.

## 9. Transaktionen und Session-/Publicationvertrag

Read: persistierte Action prüfen, P6.mayRead ausschließlich ACTIVE/COMPLETED,
projektionbezogene Reads innerhalb einer withReadTransaction. Kein observable
DAO-Flow, dauerhaftes Handle oder automatisches Wiederöffnen.

Write: withWriteTransaction wie P4/P5a. Ablauf:

1. READY/STEP; Action vorhanden, persistierter Status ACTIVE/COMPLETED und
   expectedActionStatus passend; eigene **P6-Policy**, kein generisches writable(COMPLETED).
2. Vollständigen Input normalisieren/validieren, same-action Referenzen prüfen.
   Bei Append aktuelle vollständige Revision lesen und mit Expected vergleichen.
3. Erst danach repositoryeigene IDs/Nummer/Wanduhr und Phasenflags erzeugen.
4. Create: Entry + ORIGINAL + sämtliche Children; Append: neue Revision +
   vollständige eigene Children. Alte Rows bleiben unverändert.
5. Vor jedem DAO-Arbeitsschritt Context STEP, unmittelbar vor Transaktionsende
   Context COMMIT. Child-Insertfehler → gesamte Transaktion rollbacken.
6. ActionDatabaseAccess deterministisch schließen und seinen Publicationcheck
   passieren; DB-Key-Kopie wie bestehend freigeben. main: context.publish,
   anschließend UI-Request-/Generation-/Actionbindung vor Zustandsannahme prüfen.

Rejected darf nur **vor erstem Insert** regulär zurückgegeben werden. Nach Beginn
von Inserts führen Fehler zum Rollback, nicht zu catch+Rejected innerhalb einer
dann erfolgreich committenden Transaktion. Unique-/Triggerconstraint race als
STALE nur nach belegtem Rollback und korrekt eingeordneter Constraint behandeln;
unbekannte Fehler fail closed. Keine REPLACE/IGNORE-Inserts, die halbe Fassungen
oder verschwundene Referenzen als Erfolg tarnen. Write-Serialisierung der SQLite-
Transaktion plus expected-Vergleich plus Unique(entryId,number) verhindern Lost Update.

Commit-Revoke vor irreversibler Zulassung → Rollback auf derselben Connection
über bestehenden Driver. Bereits vor Revoke irreversibel zugelassener nativer Commit
kann physisch enden; nach Revoke keine neue Arbeit/Publication. Keine verstärkte
falsche Commitgarantie oder neue Securitymonitore. Callbacks keine DB-I/O unter
Coordinator; Repositoryoperationen melden keine userInteraction. Nur bestehende
qualifizierte UI-Interaktion zählt, absolut 180s/READY-Inaktivität 30s unverändert.

Keine automatische Schreibwiederholung nach verlorenem Callback: Commit kann vor
Revoke erfolgt sein. Nach neuem zulässigem Read Bestand prüfen; ein wiederholter
Append auf altem Expected ist STALE. Für Create keine Exactly-once-/Deduplizierung-
Garantie erfinden, keine stillen doppelten Nachträge durch Retryengine.

## 10. Indizes, Constraints und SQLite-/Room-Realisierbarkeit

| Tabelle | PK / zusätzliche notwendige Indizes |
| --- | --- |
| entries | PK id; UNIQUE(actionId,id), zugleich action-prefix für Liste/FK. |
| revisions | PK id; UNIQUE(actionId,id) für Child-FK; UNIQUE(entryId,revisionNumber) für aktuelle/Original-Fassung; INDEX(actionId,entryId) für Parent-FK/Aktionsread. |
| affected | PK(actionId,revisionId,personId) deckt Revision-FK; INDEX(actionId,personId) für Personenbereinigung. |
| identifiers | PK(actionId,revisionId,position) deckt Revision-FK und stabile Reihenfolge. |
| reported_by | PK(actionId,revisionId) deckt Revision-FK; INDEX(actionId,personId) für Quellenreferenzbereinigung. |

Kein Index auf Behördenwert/Namen/Freitext. Timeline-sortiert nur aktuelle
Fassungen aus Entry/Nummer-Join; ein occurredAt-Index auf sämtlichen historischen
Revisionen garantiert diese Ordnung nicht. Für MVP bewusster SQL-Sort der schmalen
Projektion, kein redundanter Current-Snapshot oder spekulativer Timelineindex.
Bei nachgewiesenem Engpass später Queryplan/Index prüfen, nicht vorab Duplikate.

Room3 unterstützt vorhandenes Muster von zusammengesetzten PK/FKs/UNIQUE-Indizes.
Da lokale Baseline CHECK nicht über Entityannotation modelliert, kleine identische
Constraint-Trigger für frische DB und Migration wie installTaskConstraints planen:

- BEFORE INSERT revisions: gültige Enumwerte/0..1-Booleans, positive Nummer;
  erste Nummer exakt 1 und ORIGINAL, folgende exakt max+1 und nicht ORIGINAL;
  Nummer/Booleanfelder auch auf INTEGER-Repräsentation prüfen.
  Irrtumszustand gemäß Abschnitt 5; announcementKind/Text-Paar gültig.
  ORIGINAL-Abschlussflag identisch zum Entryflag. Keine eigene Wanduhrprüfung.
- BEFORE INSERT reported_by: referenzierte Revision hat sourceType=REPORTED.
  PK sichert höchstens eine Row. Kein NULL-Update von actionId.
- BEFORE INSERT identifiers: position ist INTEGER im Bereich 0..31, Type gültig.
  PK + 32 mögliche Slots sichert maximal 32 Rows ohne komplexen COUNT-Trigger.
- BEFORE UPDATE Entry/Revision/Identifier/Relationen: normalen Update verbieten;
  kein UPDATE-DAO geplant. Personenbereinigung entfernt Relationsrows, keine
  Inhaltsupdates. DELETE nicht pauschal sperren, damit FK-/Retentionkaskaden funktionieren.

DB-Trigger beweisen keine Autorisierung oder forensische Integrität; persistierter
Status/Session/Unicode-Codepointvalidierung bleiben Repository-/Domainpflicht.
Triggerdatenfehler werden beim Mapping zusätzlich geprüft. Keine SQL length als
Ersatz für Character.codePointCount (z.B. NUL-Semantik nicht gleichsetzen).
Keine Trigger auf P4/P5a-Tabellen ändern. Schema-/Triggergleichheit fresh/migrated
und alle kritischen Triggerinvarianten sind mit echter SQLCipher/Room zu testen.

Entry mit genau einer Originalfassung ist transaktionale Repositoryinvariante;
FK allein erzwingt keine mindestens-eine-Child-Row. Ein orphan Entry aus direktem
manipuliertem Insert darf nicht als Empty erscheinen. Typ-/Sequenz-/Child-FK-Trigger
verhindern gewöhnliche Inkonsistenzen, kein Anspruch beliebige DB-Manipulationen
oder Child-Nachinsert durch privilegiertes SQL vollständig auszuschließen.

## 11. Additiver Upgradeplan 6 → 7

Nur fünf neue P6-Tabellen mit obigen Indizes/Constraints/Triggern. Bestehende
Action/Person/P3b/P4/P5a-Tabellen, Spalten, Daten, Ciphertexts und Trigger unverändert.
Keine Note-Tabelle zweckentfremden, keine erfundenen Altereignisse oder Retrorevisionen;
P6-Bestand nach Upgrade leer. Kein destructive fallback, Fremdbestand oder Klartextfile.

Später zusammen ändern: Entityliste/DAO und version=7/exportSchema=true,
ACTION_MIGRATION_6_7 registrieren, bekannte Preflight-Versionsspanne 1..7,
Constraintinstallation für frische DB zusätzlich zum unveränderten P5a-Installer.
Schemaexport 7 erzeugen, 6/7 vergleichen (alle alten Definitionen unverändert),
Migrationen 1..6→7 und fresh7 über bestehenden verschlüsselten Zugang prüfen.
Trigger sind nicht allein durch Room-Schemaexport nachgewiesen: sqlite_master/
Constraintverhalten für fresh/migrated vergleichen. Keine realen Dateien hierfür
im heutigen Slice erzeugt.

## 12. Personen- und Aktionslöschung: spätere Integration

Allgemeine Teilnehmerlöschung aktuell **nicht implementiert**. P6 liefert keine
neue Löschfunktion. Späterer zulässiger Persondelete muss bestehende P4/P5a/P3b-
Abhängigkeiten geordnet bereinigen und manuelle P6-Prüfung aller Fassungen sichtbar
machen. Betroffenen-/ReportedBy-Relationsrows können explizit vor Persondelete
entfernt werden; ihre Person-FK-Cascades sind zusätzliche konsistente Absicherung.
Entry/Revision/Identifier und sämtliche Texte bleiben unverändert. Keine historischen
Namen/Pseudonyme aus P3b. Vollvergleich beim Append verhindert stale Relationrestores.

Allgemeine Action-/Retentionlöschung ebenfalls **noch kein Produktworkflow**.
P6-Teil vollständig logisch: Action→Entry→Revision→alle drei Childtabellen.
Alternativ zuerst P6-Childs/Revisionen/Entries in derselben kontrollierten Lösch-
transaktion entfernen, dann Action. Bestehende P4/P5a-RESTRICT-Referenzen erfordern
geordnetes Bereinigen etwa Tasks/Events und Rollenpläne vor ihren referenzierten
Rollen/Personen; eine fehlgeschlagene Actionlöschung darf nicht als Erfolg gelten.
P6 erzeugt keine neue RESTRICT-Sperre oder Archivinsel. Kaskadentests mit gesamtem
bestehendem Modell sind Pflicht, nicht nur isolierter Entry-Delete.

Eigenständiger späterer P6-Retentiondelete umfasst alle Entry-Fassungen/Children
und Ableitungen, nie eine einzige historische Fassung zurücklassen. Kein normaler
Einzel-Löschport für ACTIVE/COMPLETED. Dauer/Fristanker bleiben fachkundigem Slice
vorbehalten; keine unbegrenzte Speicherung, Worker oder neue Löschgarantie.
Logische Entfernung ist keine Secure-Overwrite-/Flash-/RAM-Überschreibgarantie.

## 13. Tests, Last und spätere UI-Integration

Detaillierte Matrix in [testing.md](testing.md). Mindestens:

- Domain: Grenzen mit Supplementary-Unicode, Trim/Leer/null, 19 Templatekeys,
  Quelle/Ansagepaar, 32/33 Kennungen, mehrere Betroffene/Set, Irrtumszustände,
  Revisionsequenz und ProductTime Gap/Overlap. Fake-Wanduhr separat von Sessionuhr.
- Storage: echte SQLCipher/Room fresh7 + Migration, Same-Action-FKs per direkt SQL,
  ORIGINAL/Sequenz/Unique-/Triggerfälle, Entry+Children-/Append+Children-Atomarität
  und Childfehler; zwei kontrolliert konkurrierende erwartete gleiche Revisionen:
  genau ein Erfolg, anderer STALE; Altfassungen unverändert.
- Gates: alle fünf Actionphasen, COMPLETED P6-only neben unveränderten P4/P5a-
  Rejects, Entwurf über Abschluss, Phaseflag bei zurückgestellter Uhr, Commit-
  Revoke-Rollback und unterdrückte main-Publication. Kein Fake entfernt Gates.
- Löschung: Personenrelationen weg, Revisionen/Texte bleiben; stale Draft abweisen;
  geordnete vollständige Actionkaskade ohne P6-Reste. Keine Behauptung, allgemeiner
  Retentionworkflow oder manuelle Prüfung sei dadurch bereits implementiert.
- Privacy: Summary-/Timeline-DAO-Projektionen lesen keine Kennungen/Longtexts/P3b;
  SQL-Querybeobachtung plus selektierte Spalten, nicht nur versteckte UItexte.
  Detail löst nur benötigte Pseudonyme; keine Logs/Identifierindex-/Globalkartei.
- Spätere UI: Hub→Timeline→Vorlage/frei→Detail→Revision; Nachtragmarker, Filter,
  Chronologie nach Zeitkorrektur, Atomic Reveal/Refresh, Stale/Lock/Cancel-Cleanup,
  kein UI-Tick-Sessionreset, Font Scale 1/2/Phone/TalkBack/Keyboard.

Technische Lasttests, **keine Produkt-Maximalzahlen**: 1000 Entries mit je 3
Revisionen, 1 Entry mit 1000 Revisionen, maximale Textfelder und 32 Kennungen je
getesteter Revision; Betroffene aus 500 synthetischen Personen derselben Aktion.
Summary/Timeline sollen keine historischen Volltexte oder Childmaterial laden;
Detail gebündelt statt N+1. Messen Queryplan, Zeit/RAM auf Zielgerät und prüfen
Sessionablauf während Last. Keine garantierten Latenzwerte ohne Messung.

MVP listTimeline als vollständige schmale Projektion, keine neue Pagingdependency
oder Pagination. Bei nachgewiesen zu großer Detail-/Timelinelast später begrenztes
Nachladen historischer Fassungen/Paging im separaten Slice prüfen; keine stille
Auslassung oder willkürliche Produktobergrenze. Die technische Abnahme muss Last
und UI-Erreichbarkeit messen, bevor konkrete Performance behauptet wird.

## 14. Kleine Implementierungsreihenfolge und Restgrenzen

1. **P6c1 Domain + Storage + Repository/Policy:** zusammenhängender kleinster
   DB-only-Slice. Tabellenmigration allein ohne Gates/Port/Atomarität wäre kein
   nutzbarer Produktstand; P4/P5a- und Migration-/Revoke-Tests gehören hierher.
2. **P6c2 ACTIVE UI:** eigener Modelzustand, minimierte Hub-/Timelinereads,
   Details/Revisionen und RAM-only Entwürfe auf dem geprüften Port.
3. **P6c3 COMPLETED-Nachbereitung:** sichtbare bewusste Nachträge/Revisionen,
   Phasenwechsel und unveränderte P4/P5a-read-only-Grenzen integrieren.
4. **P6c4 Integration/Accessibility:** Cross-Area-Unabhängigkeit, Font Scale 1/2,
   synthetic Galaxy/API33, Last-/Lock-/Publicationprüfung und unabhängiges Review.

Kein zusätzlicher Domain-/Securityentscheid offen. Umsetzung muss die hier
festgelegten FK-/Trigger-/Querydetails auf Room/SQLCipher durch Tests nachweisen;
Baseline-Merge/Versionsnummer vor Start abgleichen. Spätere Fristen/Rechtsprüfung,
Personen-/Aktionslöschworkflow, Export/Medien und mögliche separat angeforderte
Integritätsmechanismen bleiben P6a.1-Restgrenzen, keine stillen P6c-Features.
Der fehlende lokale main-Merge ist offen benannt, kein Anlass Branch/Git selbst
zu ändern. Heute nur Dokumentations-/Link-/Scope-/Diffprüfung, keine Produkt-
Unit-/Gerätetests oder Migration ausgeführt.

## 15. Explizite Reviewantworten

| Frage | Antwort im spezifizierten Plan |
| --- | --- |
| Children revisionsgebunden? | Ja, alle Betroffenen/Kennungen/ReportedBy gehören genau einer Revision. |
| Current deterministisch? | Ja, höchste revisionNumber; gleiche Nummer per Unique verboten. |
| Kann stale Korrektur überschreiben? | Nein im vorgesehenen Port: voller Expectedvergleich unter Write-Transaktion, sonst STALE. |
| COMPLETED allgemein writable? | Nein, nur eigene P6-Policy, P4/P5a weiterhin read-only. |
| Persondelete löscht Revision? | Nein, Personcascade nur auf Betroffenen-/Quellenrelationen. |
| Actiondelete lässt P6-Reste? | Nein nach erfolgreicher geordneter Gesamtlöschung; alle P6-Children kaskadieren. Bestehende Restrict-Abhängigkeiten vorher lösen. |
| Hub lädt sensitive Details? | Nein, nur Anzahl/Originalzeit; keine Texte/Kennungen/Personen. |
| Globale Identifier-/Polizeikartei? | Nein, lokale revisionsgebundene Rows ohne Matching. |
| Nutzer setzt recordedAt? | Nein, ausschließlich injizierbare Repository-Wanduhr; nicht im Input. |
| Beweis-/Forensikgarantie? | Nein. |
| Neue Kryptografie/Permission? | Nein, bestehender DB-/Sessionpfad ohne zusätzliche Freigabemechanik. |
| Migration additiv? | Ja, geplanter 6→7-Slice ausschließlich fünf neue Tabellen/Indizes/Trigger, noch nicht umgesetzt. |
