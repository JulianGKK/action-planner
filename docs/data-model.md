# Datenmodell des MVP

**Projekt:** PaG Aktionsplaner / `action-planner`\
**Status:** Fachlicher Modellstand freigegeben; ausdrücklich offene Entscheidungen siehe Abschnitt 17  
**Grundlage:** PRODUCT.md (Version 0.1), AGENTS.md und die anschließenden verbindlichen Modellentscheidungen des Projektverantwortlichen

## 1. Zweck und Verbindlichkeit

Dieses Dokument beschreibt das konzeptionelle Datenmodell des lokal betriebenen Android-MVP: Entitäten, Attribute, Beziehungen, Lebenszyklus und fachliche Löschbeziehungen.

Der freigegebene Modellstand enthält keine Organisation, keine globale Personenkartei, keine E-Mail und keinen persistierten Aktionsstatus `DELETED`. Für Entitäten und Beziehungen ist dieses Dokument maßgeblich; technische Speicherung und Zugriff folgen architecture.md.

Es werden keine Datenbankbibliothek, Dependencies, konkrete Tabellenstruktur oder kryptografischen Verfahren festgelegt. Die Architektur der Speicherung und des Zugriffs steht in [architecture.md](architecture.md). Konkrete Speicherlösung, Schlüsselverfahren und Löschgarantien werden im Security-/Storage-Prototyp nachgewiesen; Retention-Fristen bleiben in data-retention.md offen.

## 2. Grundsätze

- `Action` ist die zentrale fachliche Einheit. Mehrere Aktionen können gleichzeitig bestehen.
- Personen und operative Inhalte sind ausschließlich aktionsgebunden. Es gibt keine gespeicherte Organisation und keine globale Personenkartei.
- Referenzen zwischen operativen Objekten unterschiedlicher Aktionen sind unzulässig.
- Personenbezogene Felder werden nur für einen definierten organisatorischen Zweck befüllt.
- Organisatorische Rollen verleihen keine technischen App-Berechtigungen.
- Alle operativen Daten werden verschlüsselt gespeichert. Die Sensitivitätsklasse entscheidet nicht über die Verschlüsselung.
- Sensible Inhalte benötigen nachvollziehbare Aufbewahrungs- und Löschregeln.
- IDs, Metadaten und Aktionszuordnungen können ebenfalls sensibel sein.
- Dashboard, Kostenübersichten und Suchergebnisse sind abgeleitete Ansichten, keine zusätzlichen fachlichen Stammdaten.

## 3. Notation und Datentypen

| Kennzeichnung | Bedeutung |
|---|---|
| P | Pflichtfeld; kann automatisch erzeugt werden |
| O | Optional; nicht benötigte Inhalte bleiben ungespeichert |
| B | Bedingt erforderlich; Bedingung wird angegeben |
| A | Abgeleitet; keine unabhängige fachliche Speicherung nötig |

Pflichtfelder sind nicht zwangsläufig Eingabefelder. Für ausdrücklich offene Retention-Details bezeichnet die Kennzeichnung die vorgeschlagene fachliche Bedeutung, keine bereits festgelegte technische Speicherung.

| Typ | Bedeutung |
|---|---|
| ID | Interne, unveränderliche Kennung ohne eingebettete personenbezogene Angaben |
| Text | Begrenzter Text; konkrete Längen noch offen |
| Zeitpunkt | Eindeutiger Zeitpunkt; Darstellung und Zeitzonenregeln noch offen |
| Datum | Kalendertag ohne Uhrzeit |
| Dauer | Positive Aufbewahrungsdauer; Einheit und Berechnung noch offen |
| Geldbetrag | Exakter Betrag; kein binärer Gleitkommawert |
| Enum | Wert aus einer festgelegten Menge |
| Referenz | Verweis auf eine andere Entität |

## 4. Entitätenübersicht

| Entität | Zweck |
|---|---|
| `Action` | Organisatorischer Rahmen |
| `Person` | Personeneintrag innerhalb genau einer Aktion |
| `PersonStatusDefinition` | Konfigurierbarer Personenstatus einer Aktion |
| `Role` | Organisatorische Rolle einer Aktion |
| `PersonRole` | Person-Rollen-Zuordnung |
| `Task` | Aufgabe |
| `Material` | Materialposition |
| `Document` | Dokumenteintrag einschließlich Informationsmaterial |
| `FileAsset` | Verwalteter lokaler Dateiinhalt |
| `Note` | Allgemeine aktions-/personenbezogene Notiz, getrennt von P6 und P9 |
| `ProtocolEntry` | Stabile Identität eines bewusst dokumentierten Aktionsvorgangs (P6) |
| `ProtocolRevision` | Vollständige nachvollziehbare Fassung eines P6-Eintrags |
| `ProtocolAffectedPerson` | Betroffenenbezug je Fassung auf bestehende Person derselben Aktion |
| `ProtocolOfficialIdentifier` | Beobachtete amtliche Kennung je Fassung, kein Behördenpersonenprofil |
| `ProtocolReportedByPerson` | Optionaler REPORTED-Quellenbezug auf bestehende Person derselben Aktion |
| `Expense`, `ExpensePayment` | EUR-Kostenposition mit geordneter Zahlungsaufteilung (P8) |
| Gedächtnisprotokoll (P9, konzeptionell) | Persönlicher Bericht mit gespeichertem Entwurf, Antworten nach versioniertem Fragebogen und späteren Ergänzungen; keine technische Entity festgelegt |
| `RetentionPolicy` | Konzeptionelle Aufbewahrungsregel |

Gemeinsame Schutz- und Retention-Metadaten sind Bestandteile der betroffenen Objekte und erfordern nicht zwangsläufig eigene Entitäten.

## 5. Gemeinsame Schutz- und Retention-Metadaten

`Action`, `Person`, `Role`, `Task`, `Material`, `Document`, `FileAsset`, `Note` und `Expense` sowie P6-Einträge/Fassungen/Relationen besitzen eine Sensitivitätsklasse. Personenbezogene Zuordnungen werden ebenfalls geschützt.

Die folgenden Retention-Attribute sind ein konzeptioneller Vorschlag. Fristen, Fristanker, Vererbung, Überschreibungen, technische Löschzustände und ihre Persistenz werden erst in `data-retention.md` und `security.md` verbindlich festgelegt.
P8 besitzt ausschließlich die Felder aus Abschnitt 12. SENSITIVE ist sein fester
Produktvertrag; die folgende konzeptionelle Tabelle ergänzt keine P8-Spalten.

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `sensitivity_class` | Enum | P | Schutzklasse des Objekts |
| `retention_policy_id` | Referenz | P | Zugeordnete Aufbewahrungsregel |
| `retention_anchor_at` | Zeitpunkt | B | Fristbeginn, sobald das auslösende Ereignis eingetreten ist |
| `retention_due_at` | Zeitpunkt | B | Löschtermin, sobald bestimmbar |
| `retention_override_due_at` | Zeitpunkt | O | Individuell festgelegter Termin |
| `effective_delete_at` | Zeitpunkt | A | Wirksamer Termin nach noch festzulegenden Frist- und Elternregeln |
| `deletion_state` | Enum | P | Technischer Zustand eines Löschvorgangs; noch zu spezifizieren |

Als technische Löschzustände kommen beispielsweise `NONE`, `PENDING`, `IN_PROGRESS` und `FAILED_RETRYABLE` infrage. Diese Werte sind keine beschlossene Sicherheitsarchitektur und keine zusätzlichen Aktionsstatuswerte.

Ein noch nicht bestimmter Fristbeginn erlaubt keine unbegrenzte Speicherung. Ein nach erfolgreicher Löschung weiterhin bestehender Inhaltsdatensatz wird nicht vorgesehen.

## 6. Aktion (`Action`)

### 6.1 Attribute

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `title` | Text | P | Nicht leerer Titel |
| `description` | Text | P | Beschreibung gemäß FR-001 |
| `start_at` | Zeitpunkt | P | Geplanter Beginn |
| `expected_end_at` | Zeitpunkt | P | Voraussichtliches Ende |
| `status` | Enum | P | Einer der fünf persistierten Statuswerte |
| `created_at` | Zeitpunkt | P | Erstellung |
| `completed_at` | Zeitpunkt | B | Tatsächlicher Abschlusszeitpunkt nach Abschluss |
| `default_retention_policy_id` | Referenz | P | Standardregel für zugehörige Inhalte; Details noch offen |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und konzeptionelle Retention-Metadaten |

Die eigene Aufbewahrung einer Aktion und ihre Standardregel für Inhalte erfüllen unterschiedliche Aufgaben. `expected_end_at` darf nicht vor `start_at` liegen. Regeln für unvollständige Entwürfe bleiben unter OD-02 offen.

### 6.2 Verbindliche Statuswerte

| Wert | Anzeige | Bedeutung |
|---|---|---|
| `DRAFT` | Entwurf | Aktion wird konkretisiert |
| `PREPARATION` | Vorbereitung | Organisatorische Vorbereitung läuft |
| `ACTIVE` | Aktiv | Aktion läuft |
| `COMPLETED` | Abgeschlossen | Durchführung beendet; Nachbereitung und Aufbewahrung |
| `DELETION_PENDING` | Löschung ausstehend | Vollständige Aktionslöschung angefordert oder fällig |

`DELETED` ist ausschließlich das terminale Ergebnis der Löschung. Es ist kein persistierter Statuswert und kein verbleibender Aktionsdatensatz.

„Archiviert“ und „Aufbewahrung / Review“ bezeichnen die Phase `COMPLETED`, keinen weiteren Status. Abgeschlossene Aktionen werden im MVP nicht wiedereröffnet.

### 6.3 Lebenszyklus

```text
DRAFT → PREPARATION → ACTIVE → COMPLETED → DELETION_PENDING → Löschung abgeschlossen
```

Der Abschluss setzt `completed_at`. Welche Retention-Regeln daran anknüpfen, bleibt offen. Die Löschung einzelner Inhalte bedeutet nicht die vollständige Löschung der Aktion.

P6a.1 erlaubt ausschließlich für das Aktionsprotokoll bewusste COMPLETED-
Nachbereitung: angehängte Korrekturen/Ergänzungen/Irrtumsmarkierung sowie neue
Einträge mit dauerhaftem Nachtragmarker. Erfassungsphase und recordedAt bleiben
nachvollziehbar. Dies ermöglicht weder P4/P5a-Writes, normales Einzel-Löschen
von Protokollfassungen noch Wiedereröffnung der Aktion. Noch nicht implementiert.

Statuswechsel erfolgen fachlich; geplante Zeiten allein lösen keinen automatischen Wechsel aus. Direkter Abschluss nicht durchgeführter Aktionen, Rückwärtsübergänge vor Abschluss und Abbruchverhalten bleiben unter OD-01 zu konkretisieren.

## 7. Personen und Rollen

### 7.1 Person

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Genau eine Aktion |
| `name` | Text | O | Name, sofern erforderlich |
| `pseudonym` | Text | O | Alternative Bezeichnung |
| `phone` | Text | O | Telefonnummer, sofern erforderlich |
| `status_definition_id` | Referenz | O | Aktueller organisatorischer Status |
| `created_at` | Zeitpunkt | P | Erstellung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Name, Pseudonym und Telefonnummer sind unabhängig voneinander optional. Es gibt keine E-Mail und keinen globalen Personeneintrag. Personenbezogene Notizen werden ausschließlich über `Note` modelliert. Ohne identifizierende Angaben wird kein zusätzlicher Pflichtname verlangt.

Ein Löschzeitpunkt wird nicht durch Erhaltung eines gelöschten Personendatensatzes dokumentiert.

**P3a-Präzisierung:** Der ausdrücklich freigegebene erste Produktslice speichert
ausschließlich IDs, Erstellungszeitpunkt und ein verpflichtendes Pseudonym, keine
anderen optionalen Personenfelder oder Platzhalter. Innerhalb einer Aktion ist
`trim().lowercase(Locale.ROOT)` als Vergleichswert eindeutig; die sichtbare Eingabe
bleibt erhalten. Dies konkretisiert für P3a die ältere optionale Pseudonymregel und
den offenen Eindeutigkeitsbedarf aus OD-12. Die neue Zugriffsklasse BASIC ist keine
Herabstufung von SENSITIVE. Statuszugriffe, spätere sensible Felder und offene
Retention sind in [product-p3a.md](product-p3a.md) und
[person-data-access.md](person-data-access.md) abgegrenzt.

### 7.2 PersonStatusDefinition

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Geltungsbereich |
| `label` | Text | P | Nicht leere Bezeichnung |
| `sort_order` | Ganzzahl | O | Auswahlreihenfolge |

Statusdefinitionen gelten pro Aktion. Ausgangsbeispiele: angefragt, zugesagt, anwesend, abwesend, zurück, unbekannt. Es wird nur der aktuelle Status gespeichert, keine Statushistorie und kein Standortverlauf.

Definitionen werden spätestens mit ihrer Aktion gelöscht. Das Verhalten beim Entfernen verwendeter Definitionen ist in Abschnitt 15 beschrieben. „Nicht gesetzt“ und ein ausdrücklich gewählter Status „unbekannt“ sind unterscheidbar.

### 7.3 Role

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Genau eine Aktion |
| `name` | Text | P | Nicht leere Rollenbezeichnung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Eine Person kann mehrere organisatorische Rollen besitzen. Diese Rollen sind keine technischen Zugriffsberechtigungen.

### 7.4 PersonRole

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `person_id` | Referenz | P | Person |
| `role_id` | Referenz | P | Rolle |

Das Paar ist eindeutig. Beide Bezugspunkte gehören derselben Aktion an. Die Zuordnung ist mindestens so schutzbedürftig wie die Person und endet spätestens mit der Löschung eines Bezugspunkts.

## 8. Task und TaskEvent (P5a)

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id`, `actionId` | ID/Referenz | P | Aufgabe und genau eine Aktion |
| `title` | Text | P | Trim, 1–120 Unicode-Codepoints; kein Notizfeld |
| `assigneePersonId` | Referenz | O | Zuständige Person derselben Aktion |
| `assigneeRoleId` | Referenz | O | Zuständige Rolle derselben Aktion, exklusiv zur Person |
| `affectedPersonId` | Referenz | O | Unabhängige betroffene Person derselben Aktion |
| `status` | Enum | P | OPEN, IN_PROGRESS, DONE, CANCELLED |
| `priority` | Enum | P | LOW, NORMAL (Default), HIGH |
| `dueAt` | Zeitpunkt | O | Fälligkeit, ohne Alarm/Worker |
| `sourceTemplateKey` | Text | O | Stabiler technischer Vorlagenkey, ausdrücklich nicht unique |
| `createdAt`, `updatedAt` | Zeitpunkt | P | Aktueller Snapshot |
| `completedAt` | Zeitpunkt | O | Ausschließlich bei DONE; Reopen entfernt ihn |

TaskEvent: `id`, `actionId`, `taskId`, `type`, `occurredAt`; Typen CREATED,
STARTED, COMPLETED, CANCELLED, REOPENED. Append-only Repository, Task und Event
atomar. Keine Historisierung von Snapshot-Bearbeitungen oder Personenlage.

Zuständigkeit: niemand ODER Person ODER Rolle, SQLite-Trigger und zentrale Domain-/
Repository-Prüfung. Zusammengesetzte Foreign Keys sichern alle Aktionsgrenzen.
PREPARATION erlaubt vollständige Task+Event-Löschung; ACTIVE stattdessen CANCELLED;
COMPLETED read-only. Statische Vorlagen sind Domain-Inputs, keine persistierte
TaskTemplate-Tabelle. P5b-Vorschlagslogik bleibt ausgenommen.

Schutzklasse ausdrücklich INTERNAL, bestehende SQLCipher-DB und READY-Session;
Retention an die Aktion gebunden. Schema 5 → 6 additiv. Der detaillierte Vertrag
in [product-p5a.md](product-p5a.md) ersetzt die früheren Task-Felder Beschreibung,
Kategorie, assigned_person_id und DISCARDED ausdrücklich.

## 9. Material (P7)

| Attribut | Typ | Pflicht | Bedeutung |
| --- | --- | --- | --- |
| `id` | ID | P | Kennung |
| `actionId` | Referenz | P | Genau eine Aktion |
| `name` | Text | P | Trim, 1–120 Unicode-Codepoints |
| `quantityLabel` | Text | O | Trim, leer → null, maximal 40 Codepoints; keine Mengenrechnung |
| `placeLabel` | Text | O | Trim, leer → null, maximal 80 Codepoints; manuell, kein GPS |
| `responsiblePersonId` | Referenz | O | Einzelne P3a-Person derselben Aktion |
| `linkedTaskId` | Referenz | O | Einzelne Task derselben Aktion, auch DONE/CANCELLED |
| `sourceTemplateKey` | Text | O | Provenienz, nicht unique/angezeigt, Edit erhält den Key |
| `createdAt` | Zeitpunkt | P | Repositoryeigener Erstellungszeitpunkt |

[P7](product-p7-material.md) ersetzt ausdrücklich die frühere Kategorie-/Status-/
Notiz-/Ganzzahlplanung. Genau diese neun Itemfelder; die konzeptionellen gemeinsamen
Retention-Metadaten sind noch keine zusätzlichen Materialspalten. INTERNAL ist
eine feste operative Einordnung, kein nutzeränderbares Feld. Keine Statusmaschine,
Historie, Einheiten-Domain, Suche/Filter oder automatische Aufgaben.

Schema8 ergänzt nur `materials`; Action-FK CASCADE und Same-Action-Trigger für
INSERT/UPDATE. AFTER DELETE auf persons/tasks setzt die jeweilige Referenz null,
Material bleibt. Keine Pseudonym-/Tasktitelsnapshots oder fremden Mutationen.
Materialdelete in PREPARATION/ACTIVE nach Bestätigung, COMPLETED nur lesen.

## 10. Dokumente, Dateien und Bibliothek

### 10.1 Document

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Aktion |
| `title` | Text | P | Anzeigename |
| `kind` | Enum | P | `DOCUMENT` oder `INFORMATION_MATERIAL` |
| `category` | Text | O | Kategorie im Fachbereich Dokumente |
| `file_asset_id` | Referenz | P | Zugehöriger lokaler Dateiinhalt |
| `created_at` | Zeitpunkt | P | Import beziehungsweise Erstellung des Eintrags |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Die Bibliothek ist eine kategorisierte Sicht auf importierte Dokumente. Sie erzeugt keine zweite Inhaltskopie und keine separate Bibliotheksentität. Die Formatfreigabe bleibt offen; PRODUCT.md priorisiert PDF, Bilder und einfache Textdateien.

### 10.2 FileAsset

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Aktion des Eigentümers |
| `storage_locator` | Undurchsichtige Referenz | P | Interner Speicherbezug ohne sprechenden Dateinamen |
| `original_filename` | Text | O | Ursprünglicher Dateiname, sofern benötigt |
| `mime_type` | Text | P | Geprüfter Inhaltstyp |
| `size_bytes` | Ganzzahl | P | Größe des ursprünglichen Inhalts |
| `created_at` | Zeitpunkt | P | Import |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Ein `FileAsset` hat genau einen fachlichen Eigentümer: ein `Document` (P8-Kosten besitzen keine FileAsset-Beziehung). Die Eigentümerbeziehung wird über deren Dateireferenz dargestellt und muss exklusiv sein. Es gibt keine aktionsübergreifende Datei-Deduplizierung.

Dateinamen und Datei-Metadaten können personenbezogen sein. Der lokale Import schützt oder löscht die externe Quelldatei nicht automatisch. Eine dauerhafte Quell-URI ist im Modell nicht vorgesehen.

Kryptografisches Schlüsselmaterial ist kein Fachattribut. Erforderliche Schlüsselreferenzen, Importzustände und Bereinigungszustände werden separat spezifiziert.

## 11. Note und eigenständiges Aktionsprotokoll (P6)

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Aktion |
| `person_id` | Referenz | O | Zusätzliche Personenzuordnung |
| `kind` | Enum | P | `NOTE`; P6 wird nicht als `MINUTES`-Note modelliert |
| `title` | Text | O | Überschrift |
| `content` | Text | P | Nicht leerer Inhalt |
| `created_at` | Zeitpunkt | P | Erstellung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Ohne Personenzuordnung ist eine Note aktionsbezogen. Mit Personenzuordnung ist sie personenbezogen; die Person gehört derselben Aktion an.

Allgemeine Notes dienen nicht als persönliche Gedächtnisprotokolle (P9).
Allgemeine Notes erhalten keine Versionshistorie, Teilnehmerlisten oder
Autorenprofile. Personenbezogene Notizen werden nicht zusätzlich in `Person`
gespeichert. Die frühere Planung „Protokoll als typisierte Note/MINUTES“ ist durch
**P6a.1 bewusst ersetzt**, nicht durch eine technische Migration.

### 11.1 P6: konzeptionelle Elemente und Beziehungen

Verbindliche Produktentscheidungen: [P6a.1](product-p6a-protocol.md).
Alle folgenden Elemente sind **zukünftig und konzeptionell**, keine bereits implementierten
Room-Tabellen oder Migration. Die geplante technische Ausgestaltung steht in
[P6b](product-p6b-protocol-implementation.md).

| Element | Fachliche Bedeutung |
| --- | --- |
| `ProtocolEntry` | Stabile Identität des Vorgangs, genau eine Aktion; erste Fassung ORIGINAL, weitere Fassungen. Erfassungsphase des Originals erhält dauerhaften COMPLETED-Nachtragmarker. |
| `ProtocolRevision` | Genau ein Entry, vollständiger Inhalt und eigene unveränderliche recordedAt; ORIGINAL, CORRECTION, ADDENDUM oder MARKED_MISTAKEN. Enthält Art, kurzen Titel, occurredAt, occurredAtApproximate, begrenzte optionale Texte, Quellenart und Erfassungsphase. |
| `ProtocolAffectedPerson` | 0..n ausdrücklich betroffene bestehende Personen derselben Aktion je Revision, ausschließlich Pseudonymanzeige; kein einzelnes affectedPersonId-Limit und keine globale Teilnehmerliste. |
| `ProtocolOfficialIdentifier` | 0..32 Angaben je Revision: SERVICE_OR_ID_NUMBER, DISPLAYED_NAME, UNIT_OR_VEHICLE_ID oder OTHER_OFFICIAL_ID; getrimmter Wert höchstens 120 Codepoints. Kein Einsatzkräfteverzeichnis oder Zusammenführen gleicher Werte. |
| `ProtocolReportedByPerson` | Optional 0..1 mitteilende bestehende Person derselben Aktion je REPORTED-Revision; unabhängig von Betroffenen, keine Autoren-/Zeug:innenprofile. |

Quellenart optional: OWN_OBSERVATION, REPORTED, OFFICIAL_ANNOUNCEMENT, UNCLEAR.
Bei behördlicher Ansage genügen die vorhandenen Wortlaut-/sinngemäßen Ansagefelder,
keine weitere Pflichtidentität. Beobachtete Maßnahme gehört in die begrenzte
Beschreibung, nicht in ein zusätzliches unbegrenztes Freitextfeld.

Verbindliche getrimmte Unicode-Codepointgrenzen: Titel 120, Beschreibung/Ansage
je 2000, Begründung/Rechtsgrundlage laut Polizei 1000, Ort 80, Kennung 120.
Leere optionale Angaben nicht speichern, keine stille Kürzung. Betroffenenanzahl
nicht künstlich klein begrenzen; ausschließlich vorhandene Personen derselben Aktion.

Gespeicherte Revisionen werden im normalen Produktpfad nur angehängt, nicht
überschrieben/gelöscht. Jüngste wirksame Fassung für aktuelle Ansicht, ältere im
Detail. MARKED_MISTAKEN bewahrt frühere Fassungen. Keine allgemeine Audit-Historie
oder kryptografische Beweis-/Integritätsgarantie. Atomare vollständige Fassungen
und Konflikterkennung sind fachliche Anforderungen; geplante Mechanik in
[P6b](product-p6b-protocol-implementation.md), Umsetzung folgt separat.

SENSITIVE ist entschieden; normale P6-Arbeit in gültigem READY ohne zusätzlichen
Leadership-Step-up ist die enge Zugriffsausnahme, keine Herabstufung (PRIV-06).
P3b-Werte werden nicht übernommen, P4/P5a nicht automatisch verändert.

Teilnehmerlöschung kann erforderliche Betroffenen-/REPORTED-Relationen lösen,
**erhält aber Entry und Revisionen und verändert Freitext nicht automatisch**.
Keine historischen Pseudonymkopien oder Rekonstruktion aus P3b. Späterer
Personenlöschworkflow prüft sämtliche Fassungen manuell auf verbleibende Bezüge.
Eigene endliche P6-Retentionbetrachtung innerhalb der Aktion, konkrete Dauer offen.
Vollständige Aktionslöschung entfernt Einträge, alle Fassungen/Relationen/Kennungen;
keine P6-Insel oder einzelne versteckte Altfassung, keine Secure-Overwrite-Garantie.

### 11.2 P9: Gedächtnisprotokoll — ausschließlich konzeptionell

Verbindlicher Fach-/Fragebogenvertrag: [P9a](product-p9a-memory-protocol.md).
P9 ist weder Note noch ProtocolEntry und übernimmt keine P6-Ereignisse automatisch.
Ein persönlicher Bericht gehört genau einer Aktion; fachlich besitzt er lokale
Identität, authorLabel (trim, 1–80 Unicode-Codepoints), questionnaireVersion,
eigenen Berichtsstatus DRAFT/COMPLETED und Antworten mit stabilen Fragekeys.
Freie Erinnerung kommt vor Detailfragen. Antworttypen, Optionen und Abhängigkeiten
stehen ausschließlich im versionierten P9a-Katalog; keine Diagnose aus Themenwahl.

P3a-Pseudonym darf als Text in authorLabel kopiert werden, kein persistierter
Person-FK oder Autorenprofil. Personenrename/-delete verändern Bericht/Label nicht;
P9 benötigt später eigene manuelle Inhaltsprüfung. SENSITIVE mit enger gültiger
READY-Freigabe ohne Leadership-Step-up, keine P3b-Übernahme.

Nur ACTIVE/COMPLETED. Bewusst abschnittsweise gespeicherter verschlüsselter
Fachentwurf bleibt bei Lock erhalten und kann nach ACTIVE→COMPLETED bewusst
fortgesetzt werden; ungespeicherter Editor und Navigation bleiben flüchtig.
Nach Reauth kein automatisches Wiederöffnen. Kein Background-Autosave,
kein Action-Reopen und keine allgemeine COMPLETED-Schreibfähigkeit.

Berichtsabschluss schützt bisherige Antworten/Label vor normalem Überschreiben.
Spätere Ergänzungen: append-only Freitext mit repositoryeigenem recordedAt,
stabile Anfügereihenfolge; keine P6-Revisionsmaschine. Keine Medien/FileAsset-
Beziehung. Fristen und vollständige manuelle Berichtslöschung offen;
Aktionslöschung umfasst Entwürfe, Antworten, Ergänzungen und Ableitungen.

Dies sind fachliche Konzepte, keine Room-Tabellen, SQL-Spalten, Migration oder
Ankündigung einer neuen Schemanummer. Technische Antwortkodierung, endliche
Textgrenzen, sichere Draft-/Abschlussabläufe und historische Darstellung erst
P9b. Der implementierte DB-Stand bleibt Schema 9.

## 12. Expense und ExpensePayment: P8 Kosten-Sammelliste

Verbindlich: [P8](product-p8-costs.md). Expense besitzt ausschließlich id,
actionId, title (1–120 Unicode-Codepoints), totalCents (positive Long-Cents),
geordnete payments (1–32) und createdAt. Payment besitzt nur payerLabel
(1–80 Codepoints) und amountCents (>0). Text getrimmt, exakte overflow-sichere
Summe aller Zahlungen = totalCents. EUR-only; kein currency_code, Datum,
Kategorie, separates description-Feld, Beleg/FileAsset oder Erstattung.
Keine Historie/Auditlog. IDs und createdAt bleiben bei Edit unverändert.

Schema9: expenses mit fünf Parentfeldern, Action-CASCADE und UNIQUE(actionId,id).
expense_payments mit actionId/expenseId/position/payerLabel/amountCents,
PK(actionId,expenseId,position), Composite-CASCADE zum Parent. Position exakt
0..n-1, geordnet gelesen und vollständiger Childersatz in derselben Transaktion.
Die bisherigen 16 Schema8-Entities und deren Trigger bleiben unverändert.

Kein Person-FK: P3a-Pseudonym wird nur im Formular als Text kopiert. Rename/Delete
lassen payerLabel unverändert; es ist bewusster eigenständiger Kosteninhalt.
Doppelte Labels erlaubt. Schutzklasse ausdrücklich SENSITIVE, bestehende
SQLCipher-DB und gültiges READY ohne zusätzlichen Leadership-Step-up als enge
P8-Freigabe. Keine P3b-Übernahme oder Änderungen anderer Fachbereiche.

Lesen/Schreiben/Löschen PREPARATION/ACTIVE/COMPLETED, letztere nur im bewusst
geöffneten Kostenbereich. Kein Zugriff DRAFT/DELETION_PENDING. Kein Reopen und
keine allgemeine COMPLETED-Schreibfreigabe. Retention bleibt konzeptionell,
keine zusätzlichen Retentionspalten in P8; Actiondelete ist die Obergrenze.

## 13. RetentionPolicy

Die Entität beschreibt eine konzeptionelle Regel. Die folgende Struktur ist bis zur Ausarbeitung von `data-retention.md` ausdrücklich vorläufig.

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung einer Regel beziehungsweise Regelversion |
| `name` | Text | P | Verständliche Bezeichnung |
| `anchor_event` | Enum | P | Ereignis für den Fristbeginn; Werte noch offen |
| `duration` | Dauer | P | Aufbewahrungsdauer; keine konkreten Fristen festgelegt |
| `max_duration_from_creation` | Dauer | O | Mögliche maximale Lebensdauer |
| `warning_lead_time` | Dauer | O | Möglicher Vorlauf für Ablaufwarnungen |

Fristanker wie Objekterstellung oder Aktionsabschluss, Regelversionierung, individuelle Termine und die Fristauflösung sind Empfehlungen unter OD-06, keine bereits beschlossenen Regeln.

Regeln sollen keine Personennamen, Aktionstitel oder sensiblen Freitextbegründungen enthalten. Warnungen ersetzen keine Löschung und dürfen nicht stillschweigend Fristen verlängern.

## 14. Sensitivitätsklassen

Verbindliche Klassen:

| Klasse | Bedeutung | Beispiele |
|---|---|---|
| `PUBLIC_CONTENT` | Inhalt zur Veröffentlichung geeignet | Öffentliche FAQ oder Flyer |
| `INTERNAL` | Interne organisatorische Information | Allgemeine Materialplanung |
| `SENSITIVE` | Personenbezogene oder besonders vertrauliche Information | Kontakte, Teilnahme, Personenzuordnungen, interne Protokolle |

Die Klassen sind Produkt-Schutzkategorien, keine abschließende rechtliche Klassifizierung. Alle operativen Daten werden unabhängig von der Klasse verschlüsselt gespeichert.

Personenbezogene Daten, ungeprüfte Freitexte und Importe sind gemäß PRIV-06 verbindlich standardmäßig SENSITIVE. Weitere Klassifizierungsänderungen bleiben unter OD-09 offen. Für die Anwendung gilt beziehungsweise wird empfohlen:

- `Person`, `PersonRole` und personenbezogene Notes sind `SENSITIVE`.
- Alle P6-Daten sind SENSITIVE; normale P6-Arbeit in gültigem READY benötigt
  dennoch keinen zusätzlichen P3b-/Leadership-Step-up. Sensitivität und Zugriff
  sind getrennt, keine allgemeine Herabstufung/Öffnung von SENSITIVE (P6a.1).
- Personenreferenzen in Aufgaben und Material bleiben schutzbedürftig. P5a/P7
  klassifizieren ihren engen operativen Bereich ausdrücklich INTERNAL für READY;
  P3b-Identitäts-/Kontaktwerte werden dadurch nicht geöffnet oder übernommen.
- Ungeprüfte Importe und Freitext starten konservativ als `SENSITIVE`.
- Öffentlich geeigneter Inhalt kann durch seine Aktionszuordnung sensible Metadaten besitzen.
- Entfernen einer Personenreferenz führt nicht automatisch zu einer Herabstufung.
- Klassifizierung erlaubt keine Umgehung von App-Sperre, Backup-Ausschluss oder Retention.

Zunächst erfolgt die Klassifizierung auf Objektebene; keine zusätzliche Feldklassifizierung ist vorgesehen.

## 15. Beziehungen, Integrität und Löschung

### 15.1 Kardinalitäten

| Beziehung | Kardinalität |
|---|---|
| `Action` → Person | 1 : 0..n |
| `Action` → PersonStatusDefinition | 1 : 0..n |
| PersonStatusDefinition → Person | 1 : 0..n; Person hat 0..1 aktuellen Status |
| `Action` → Role | 1 : 0..n |
| Person ↔ Role | n : m über PersonRole |
| `Action` → Task | 1 : 0..n |
| Person → zugewiesene Task | 1 : 0..n; Task hat 0..1 zuständige Person oder Rolle |
| `Action` → Material | 1 : 0..n |
| Person → verantwortetes Material | 1 : 0..n; Material hat 0..1 verantwortliche Person |
| Task → verknüpftes Material | 1 : 0..n; Material hat 0..1 Task; loses P7-Link |
| `Action` → Document | 1 : 0..n |
| Document → FileAsset | 1 : 1 |
| `Action` → Note | 1 : 0..n |
| Person → Note | 1 : 0..n; Note hat 0..1 Person |
| `Action` → ProtocolEntry | 1 : 0..n; Eintrag gehört genau einer Aktion |
| ProtocolEntry → ProtocolRevision | 1 : 1..n für gespeicherte Einträge; erste ORIGINAL |
| ProtocolRevision ↔ betroffene Person | 0..n über ProtocolAffectedPerson, gleiche Aktion |
| ProtocolRevision → ProtocolOfficialIdentifier | 1 : 0..32 |
| ProtocolRevision → mitteilende Person | 0..1 über ProtocolReportedByPerson, nur REPORTED und gleiche Aktion |
| `Action` → Expense | 1 : 0..n |
| Expense → ExpensePayment | 1 : 1..32, geordnete Children |
| RetentionPolicy → zugeordnete Objekte | 1 : 0..n |

Jedes operative Kindobjekt gehört genau einer Aktion. Jedes FileAsset besitzt genau einen Eigentümer; die Beziehung zu Dokument und Ausgabe ist exklusiv.

### 15.2 Integritätsregeln

- Referenzen zeigen auf vorhandene Objekte derselben Aktion.
- PersonRole-Paare sind eindeutig.
- Ein fertiger Dokumentimport besitzt einen verfügbaren verwalteten Dateiinhalt.
- Unvollständige Imports erscheinen nicht als fertige Dokumente.
- Änderungen an Fachobjekten und Beziehungen müssen konsistent erfolgen.
- Datenbanktransaktionen allein garantieren keine atomare Änderung von Dateien und Schlüsselmaterial. Der übergreifende Ablauf wird in der Architektur spezifiziert.
- Gelöschte Inhalte dürfen nicht über Suchindizes, Vorschauen oder Caches weiter verfügbar bleiben; die technische Garantie ist noch festzulegen.

### 15.3 Fachliche Löschbeziehungen

Diese Tabelle beschreibt die fachliche Zielwirkung des freigegebenen Modells, keine technische Löschgarantie. Vollständige Aktionslöschung ist die Obergrenze für untergeordnete Daten (RET-02, ARCH-24).

| Gelöschtes Objekt | Fachliche Folge |
|---|---|
| `Action` | Zugehörige Personen, Rollen, Statusdefinitionen, Zuordnungen, Aufgaben, Material, Dokumente, Dateien, Notes, Ausgaben, sämtliche P6-Einträge/Fassungen/Relationen/Kennungen und konzeptionell alle P9-Entwürfe/Berichte/Ergänzungen entfernen |
| Person | PersonRole und personenbezogene Notes entfernen; Personenreferenzen in Aufgaben/Material sowie erforderliche P6-Betroffenen-/Quellenrelationen lösen. P6-Einträge/Fassungen erhalten, Freitext nicht automatisch ändern; manuelle Protokollprüfung erforderlich. P9-Labels/Antworten bleiben eigenständiger Inhalt ohne Person-FK; manuelle P9-Prüfung erforderlich. |
| Role | P4/P5a: einzelne Rolle nur ohne geplante/operative/Task-Referenzen entfernen; kein stilles Entfernen von Zuordnungen |
| PersonStatusDefinition | Statusreferenzen entfernen; Personen erhalten |
| Task | P5a: Aufgabe und TaskEvents vollständig entfernen, nur PREPARATION; Personen erhalten; P7-Materiallink wird null |
| Material | Nur Materialposition/zugehörige Ableitungen entfernen; Person und Task erhalten |
| Document | Dokument und zugehöriges FileAsset entfernen |
| Note | Note entfernen; `Action` und Person erhalten |
| P6-Bestand bei Retention-/Aktionslöschung | Einträge mit sämtlichen Fassungen, Betroffenen-/Quellenrelationen, Kennungen und Ableitungen entfernen; keine gewöhnliche Einzel-Löschung im P6-Produktpfad |
| Expense | Kostenposition und Zahlungschildren entfernen |
| FileAsset eines Dokuments | Dokument ebenfalls entfernen, damit kein funktionsloser Eintrag verbleibt |
| RetentionPolicy | Keine Inhaltskaskade; verwendete Regel darf nicht ersatzlos entfernt werden |

Die Entfernung einer Personenreferenz anonymisiert keinen Freitext. Namen oder identifizierende Angaben in Aufgaben, Materialbezeichnung/-menge/-ort, Dateien, P8-payerLabels und P9-authorLabels/Antworten können verbleiben. Eine zuverlässige automatische Erkennung wird nicht vorausgesetzt. Ein Bereinigungsablauf ist unter OD-10 offen. Für P6 muss er ausdrücklich die manuelle Prüfung sämtlicher Protokollfassungen umfassen, ohne automatische Inhaltszerstörung bei Teilnehmerlöschung.

### 15.4 Technische Löschung: ausdrücklich offen

Fachliche Entfernung und technische Bereinigung sind zu unterscheiden. Zu berücksichtigen sind strukturierte Daten, Beziehungen, Dateien, entschlüsselte temporäre Inhalte, Vorschauen, Caches, Suchindizes, Schlüsselmaterial und Speicherrückstände einschließlich Datenbankjournalen.

Schlüsselbasierte Dateilöschung setzt eine geeignete Schlüsselstruktur voraus. Diese ist hier nicht festgelegt. Es wird keine garantierte physische Überschreibung von Flash-Speicher behauptet.

Löschgarantien, Wiederanlauf, Fehlermeldung, mögliche minimale Löschprotokolle sowie Verhalten bei gesperrter App oder ausgeschaltetem Gerät werden in `security.md` und `data-retention.md` festgelegt.

## 16. Abgrenzungen

- Keine gespeicherte Organisation und keine globale Personenkartei im MVP.
- P7-Material besitzt keine Kategorie. Kategorien anderer geplanter Fachbereiche bleiben getrennt je Fachbereich. Es gibt keine zentrale Taxonomie; Material wird ausschließlich über die in Abschnitt 9 festgelegten P7-Felder dargestellt.
- Der lokale Admin ist ein technischer Akteur, keine Teilnehmerperson. Kein persönliches Adminprofil wird eingeführt.
- Authentifizierungsabläufe und Schlüsselreferenzen gehören in ein separates Sicherheitsmodell. PaG zählt keine falschen DEVICE_CREDENTIAL-Eingaben im Systemdialog; eigene Fehlversuchszähler/Sperrfristen wären erst bei einer späteren PaG-Passphrase separat zu spezifizieren. Biometrie bleibt gemäß PRODUCT.md ausgeschlossen.
- Suche und Dashboard erzeugen keine zusätzlichen fachlichen Stammdaten. Eventuelle persistierte Indizes müssen Schutz und Löschung ihrer Ausgangsdaten berücksichtigen.
- Eine dauerhafte organisatorische Änderungs- oder Löschhistorie wird nicht automatisch eingeführt.
- Dateninventar, Zweck, Speicherort und konkrete Schutzmaßnahmen werden ergänzend in `privacy.md`, `architecture.md` und `security.md` dokumentiert.

## 17. Offene Entscheidungen

### OD-01 — Weitere Aktionsübergänge

**Offen:** Direkter Abschluss aus DRAFT/PREPARATION, Rückwärtsübergänge vor Abschluss, Behandlung nicht durchgeführter Aktionen und Abbruch eines noch nicht abgeschlossenen Löschvorgangs.

**Empfehlung:** Nicht durchgeführte Aktionen direkt abschließen, ohne neuen Status. Rückwärtsübergänge vor Abschluss eng begrenzen. Keine Wiedereröffnung nach COMPLETED; dies ist bereits verbindlich. Verhalten von DELETION_PENDING erst zusammen mit dem Löschablauf festlegen.

### OD-02 — Pflichtangaben beim Anlegen einer Aktion

**Offen:** FR-001 verlangt Beschreibung und Zeitraum, während der einfache User Flow weniger Eingaben nennt. Für Aufgaben ist die Beschreibung nicht ausdrücklich optional.

**Empfehlung:** Die angegebenen Pflichtfelder aus FR-001 und FR-030 beibehalten. Falls unvollständige Entwürfe benötigt werden, zustandsabhängige Pflichtregeln ausdrücklich ergänzen. Optionale Verantwortung und Fälligkeit von Tasks sind bereits verbindlich.

### OD-03 — Vereinfachte Materialliste: durch P7 entschieden

Keine Materialstatusmaschine, Kategorien oder Teilmengenlogik. Menge/Anzahl ist
optionaler kurzer Text, keine Ganzzahlpflicht oder Einheitenverwaltung. Lose
Person-/Tasklinks werden bei Löschung entkoppelt; das Material bleibt. Details
im [P7-Vertrag](product-p7-material.md).

### OD-04 — Dateiimporte und technische Zustände

**Offen:** Konkrete erlaubte Formate, Inhaltsprüfung, maximale Dateigrößen sowie Import-, Fehler- und Bereinigungszustände.

**Empfehlung:** Einen begrenzten Formatumfang nach der Produktpriorität festlegen. Unvollständige Dateien bis zur erfolgreichen Übernahme nicht fachlich sichtbar machen. Technische Zustände in architecture.md und security.md spezifizieren. Exklusives Dokumenteigentum und keine aktionsübergreifende Deduplizierung sind bereits verbindlich.

### OD-05 — Geldbeträge und Währungen: durch P8 entschieden

EUR-only, positive Long-Cents, exakte overflow-sichere Payment-Summe.
Keine Fremdwährung, Umrechnung, Erstattung oder Schuldenberechnung.
Keine persistierte Personenrelation; payerLabel ist eigener Kosteninhalt.
Siehe [P8](product-p8-costs.md).

### OD-06 — Retention-Regeln und Metadaten

**Offen:** Standardfristen, Einheiten, Fristanker, maximale Aufbewahrung nicht abgeschlossener Aktionen, individuelle Termine, Vererbung, Regelversionierung und Neuberechnung.

**Empfehlung:** In data-retention.md Objekterstellung und tatsächlichen Aktionsabschluss als mögliche Anker prüfen. Für abschlussabhängige Regeln eine maximale Lebensdauer vorsehen. Verwendete Regeln versionieren; keine stillschweigende Verlängerung bestehender Termine. Die Attribute in Abschnitten 5 und 13 bleiben bis dahin konzeptionell.

### OD-07 — Elternlöschung und längere Kinderfristen: entschieden

Vollständige Aktionslöschung ist die Obergrenze für untergeordnete Daten. Keine unabhängigen Restbestände oder Aufbewahrungsausnahmen im MVP. Falls Kostenunterlagen länger benötigt werden, muss dies vor Aktionslöschung sichtbar mit passenden Fristen gelöst werden; konkrete Fristen bleiben unter OD-06 offen.

### OD-08 — Schlüsselverwaltung, Kryptografie und Löschgarantien

**Offen:** Endgültige Schlüsselhierarchie, konkrete DEVICE_CREDENTIAL-/Keystore-Bindung, technische Behandlung von Schlüsselverlust, Dateischlüssel, technische Löschzustände und Wiederanlaufdetails sowie die nachweisbaren Grenzen der Löschung. DEVICE_CREDENTIAL ist bevorzugter erster Prototyp; keine Biometrie oder eigene PaG-Passphrase darin, keine Recovery im MVP.

**Empfehlung:** In threat-model.md und security.md ein getrenntes Sicherheitsmodell festlegen; etablierte Verfahren verwenden. Fehlgeschlagene Bereinigung nicht als erfolgreich melden. Minimal erforderliche Arbeitsdaten schützen und begrenzen. Verhalten bei fehlendem Schlüsselzugriff, ausgeschaltetem Gerät und Zeitmanipulation ausdrücklich behandeln. Hier werden keine konkreten Verfahren oder Bibliotheken ausgewählt.

### OD-09 — Anwendung der Sensitivitätsklassen

**Offen:** Erlaubte Herabstufung und weitere Auswirkungen auf Darstellung und Retention. Personenbezogene Daten, ungeprüfte Freitexte und Importe sind verbindlich standardmäßig SENSITIVE; alle operativen Daten werden verschlüsselt. Keine externe Dateiöffnung im MVP.

**Empfehlung:** Keine automatische Herabstufung nach Entfernung einer Referenz. Weitere Klassifizierungsregeln in privacy.md und security.md konkretisieren; die Standardklassifizierung und Verschlüsselung unabhängig von der Klasse bleiben verbindlich.

### OD-10 — Verbleibender Personenbezug nach Löschung

**Offen:** Prüfbarer Bereinigungsablauf für identifizierende Freitexte und Dateien, die nicht direkt mit Person verknüpft sind.

**Empfehlung:** Fachliche Beziehungskaskaden um einen nachvollziehbaren manuellen Prüfablauf ergänzen. Entfernte Referenzen nicht als vollständige Anonymisierung bezeichnen. Abgelaufene Personendaten nicht wegen offener Zuständigkeiten unbegrenzt erhalten.

### OD-11 — Zeitmodell

**Offen:** Zeitzonen der Aktionsplanung, Kalenderdauer gegenüber fester Zeitdauer, Sommerzeit und veränderte Gerätezeit.

**Empfehlung:** Erstellungs- und Löschzeiten als eindeutige Zeitpunkte, Ausgabedaten als Kalendertage behandeln. Eine explizite Zeitzonenregel für Aktionsplanung festlegen. Fristberechnung und Zeitmanipulation im Retention-Konzept berücksichtigen.

### OD-12 — Grenzen und Validierung

**Offen:** Maximale Textlängen, Objektzahlen, Dateigrößen, Namenvalidierung und gegebenenfalls Eindeutigkeit von Rollen- und Statusbezeichnungen.

**Empfehlung:** Grenzen vor Implementierung mit synthetischen Testfällen bestimmen. Gleichnamige Personen zulassen; keine automatische Zusammenführung über Name oder Telefonnummer. Eindeutigkeit nur bei fachlichem Bedarf erzwingen, insbesondere beim PersonRole-Paar.

### OD-13 — Nachvollziehbarkeit und technische Sicherheitsdaten

**P6a.1 entschieden:** ausschließlich eintragsbezogene vollständige P6-Revisionen
als bewusste Dokumentationsfunktion; keine allgemeine Personenstatus-/Audit-Historie.

**Offen:** Weiterer Bedarf einer dauerhaften organisatorischen Änderungshistorie und genauer Umfang technischer Arbeits-/Authentifizierungsdaten. Keine dauerhafte detaillierte Löschhistorie im MVP; nur minimaler geschützter Arbeitsstatus für Wiederanlauf (RET-07, ARCH-25).

**Empfehlung:** Nur nachgewiesenen Bedarf dokumentieren und eigene Retention festlegen. Keine gelöschten Inhalte als Wiederanlaufhilfe erhalten. Authentifizierung und Verschlüsselung getrennt in security.md spezifizieren; keine Klartext-Credentials oder persönlichen Admindaten in Fachentitäten aufnehmen.

## 18. Nächste Spezifikationen

Der freigegebene fachliche Modellstand dient als Grundlage für threat-model.md, privacy.md, data-retention.md, security.md und architecture.md. Die offenen Entscheidungen müssen vor ihrer jeweiligen Implementierung geklärt werden. Dieses Dokument gibt keine Android-Implementierung oder Auswahl von Dependencies frei.

## P4-Präzisierung: Planung und aktuelle Lage

Schema 5 ergänzt `Role` (id, actionId, name, normalizedName, createdAt),
`PersonPlannedRole` (actionId, personId, roleId), `PersonRolePlan` mit optionaler
primaryRoleId und `PersonOperationalState` (actionId, personId, currentRoleId,
operationalStatus, currentPlaceLabel, updatedAt). Genau eine Hauptfunktion, nur aus
der geplanten Menge; genau ein aktueller Snapshot pro Person ab Aktionsstart.
Sämtliche Referenzen sind aktionsgebunden, inklusive zusammengesetzter Foreign Keys.
Keine JSON-Relation, globale Bibliothek, Historie oder zusätzlichen sensiblen Felder.

P4 v1 verwendet NO_STATUS, ON_SITE, IDENTITY_CHECK, IN_CUSTODY, NOT_REACHABLE,
RELEASED, LEFT und UNCLEAR statt konfigurierbarer Statusdefinitionen. Planung
bleibt ab ACTIVE read-only, operative Änderungen sind ausschließlich in ACTIVE
zulässig. COMPLETED erhält den letzten Snapshot read-only. Hauptfunktion wird beim
atomaren Start übernommen, ohne Anwesenheit zu behaupten. Ort/Ziel ist optional,
manuell und auf 80 Unicode-Codepoints begrenzt. Rollenname ist aktionslokal nach
Trim/Locale.ROOT eindeutig. Keine Zeitfristen werden neu festgelegt.

Die P4-INTERNAL-Einordnung ist eine ausdrücklich freigegebene Ausnahme für diesen
schnellen operativen Zugriff; Pseudonyme und P3b-Bereiche behalten ihre bisherigen
Verträge. Alle Daten bleiben verschlüsselt und sitzungsgebunden. Einzelne Rollen
werden nur unreferenziert gelöscht, nicht mit stiller Zuordnungskaskade. Ein
späterer Gesamtlöschpfad muss abhängige Plan-/Snapshot-Rows geordnet entfernen.
Details: [product-p4.md](product-p4.md).


## P5a – operative Aufgaben und minimale Task-Chronik

Der verbindliche implementierte Vertrag steht in [product-p5a.md](product-p5a.md).
Task und TaskEvent sind ausdrücklich INTERNAL für schnelle operative Koordination
in einer gültigen READY-Sitzung, trotz potentiell sensibler kurzer Titel. Bestehende
SQLCipher-DB, Session- und P3b-Verträge bleiben erhalten; kein Leadership-Step-up
für normale Taskarbeit, keine neuen Permissions, Dependencies oder Netzwerkzugriffe.

Schema 6 ergänzt tasks und task_events additiv. Zuständigkeit Person/Rolle/niemand
und optionale betroffene Person sind getrennt. Zusammengesetzte Foreign Keys
verhindern Cross-Action-Referenzen; Insert-/Update-Trigger sichern exklusive Zuständigkeit; das Repository prüft zusätzlich
persistierten Aktionsstatus und erwarteten Snapshot. Lifecycle + Event sind atomar.
TaskEvents sind ausschließlich CREATED/STARTED/COMPLETED/CANCELLED/REOPENED,
append-only im Repository. Keine Edit-Historie, allgemeine Audit- oder Personen-
Lagehistorie. PREPARATION erlaubt vollständiges Task+Event-Löschen, ACTIVE nicht,
COMPLETED ist read-only. Rollenreferenzen verhindern stilles Rollenlöschen.

Daten verbleiben aktionsgebunden, mit späterer Aktionslöschung zu entfernen,
keine permanente Archivierung, Exporte oder Secure-Overwrite-Zusage. Kurze Titel
ohne Notizfeld, Pseudonyme in der UI; keine Übernahme sensibler P3b-Payloads.
Statische Vorlagen erzeugen erst nach Auswahl und Speichern normale Tasks;
sourceTemplateKey ist nicht unique. P5b-Vorschlagsregeln sind nicht implementiert.


## P6b — technische Ausgestaltung der entschiedenen P6-Elemente

[P6b](product-p6b-protocol-implementation.md) legt den geplanten DB-only-Adapter
fest, ohne das Schema zu verändern. ProtocolEntry speichert stabile Identität/
Action und createdAfterCompletion; ProtocolRevision ist vollständige Fassung mit
Nummer, Irrtumszustand, eigenem recordedAt und recordedAfterCompletion. Herkunftskey
bleibt optional, Vorlagen sind statische Definitionen. Current = höchste Nummer,
keine Ableitung aus möglicherweise zurückgestellter Wanduhr.

Betroffene und Kennungen sind revisionsgebundene Child-Rows. ProtocolReportedByPerson
bleibt eine eigene optionale 0..1-Relation: zusammengesetztes Person-FK-SET-NULL
auf Revision würde actionId ebenfalls nullen, gezieltes Revisionupdate widerspräche
der unveränderlichen Inhaltsfassung. Separate Relationsbereinigung löst dies ohne
neue fachliche Entity. Keine historischen Pseudonymkopien, keine globale Kennung.

Same-Action-FKs/Parent-Uniqueindizes, Revisionssequenz/Expectedvollvergleich und
Constraints stehen zentral in P6b. Bestehende Note/P4/P5a-Daten werden nicht
umgenutzt. Additive Migration 6→7 mit leerem neuem Bestand ist nur geplant,
keine Room-Schema-Datei erzeugt. Allgemeine Delete-/Retentionworkflows folgen
separat, vollständige Aktionslöschung bleibt Obergrenze für alle Fassungen/Children.


## P6c1 — implementierte Protokollpersistenz

Aktueller P6c1-Stand: Schema **7** ergänzt ausschließlich protocol_entries,
protocol_revisions, protocol_affected_persons, protocol_official_identifiers und
protocol_reported_by_persons. Alle zehn bisherigen Entity-/Feld-/Indexdefinitionen
von Schema6 bleiben identisch. Der konzeptionelle P6a-/technische P6b-Plan oben
ist durch diesen DB-only-Slice umgesetzt, keine neue Note-/Polizeipersonen-Entity.

Aktuelle Revision nach Nummer, vollständiger Expected-Vergleich inklusive
Relationen/Identifierreihenfolge, repositoryeigene Zeit-/Phasenflags. Children
gehören jeweils ihrer eigenen Revision. Personcascade entfernt nur Betroffenen-/
Quellenrelationen; Actioncascade entfernt alle P6-Daten. Keine allgemeinen
Produktlöschworkflows oder Retentiondauer eingeführt. Port/DAO haben keinen normalen
Update-/Einzel-Deletepfad. Details und Nachweise: [P6c1](product-p6c1-protocol-storage.md).
