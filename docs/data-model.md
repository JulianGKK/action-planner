# Datenmodell des MVP

**Projekt:** PaG Action Organizer / action-planner  
**Status:** Fachlicher Modellstand freigegeben; ausdrücklich offene Entscheidungen siehe Abschnitt 17  
**Grundlage:** PRODUCT.md (Version 0.1), AGENTS.md und die anschließenden verbindlichen Modellentscheidungen des Projektverantwortlichen

## 1. Zweck und Verbindlichkeit

Dieses Dokument beschreibt das konzeptionelle Datenmodell des lokal betriebenen Android-MVP: Entitäten, Attribute, Beziehungen, Lebenszyklus und fachliche Löschbeziehungen.

Die ausdrücklich bestätigten Entscheidungen konkretisieren beziehungsweise korrigieren die vorläufigen Darstellungen in PRODUCT.md. Insbesondere werden keine Organisation, keine globale Personenkartei, keine E-Mail und kein persistierter Action-Status `DELETED` eingeführt. PRODUCT.md selbst wurde dabei nicht verändert.

Es werden keine Datenbankbibliothek, Dependencies, konkrete Tabellenstruktur oder kryptografischen Verfahren festgelegt. Die Speicherstruktur wird erst nach Abschluss des Threat Models bestimmt. Retention-Fristen, Schlüsselverwaltung und Löschgarantien bleiben ausdrücklich offen und werden in den vorgesehenen Dokumenten festgelegt.

## 2. Grundsätze

- `Action` ist die zentrale fachliche Einheit. Mehrere Actions können gleichzeitig bestehen.
- Personen und operative Inhalte sind ausschließlich actiongebunden. Es gibt keine gespeicherte Organisation und keine globale Personenkartei.
- Referenzen zwischen operativen Objekten unterschiedlicher Actions sind unzulässig.
- Personenbezogene Felder werden nur für einen definierten organisatorischen Zweck befüllt.
- Organisatorische Rollen verleihen keine technischen App-Berechtigungen.
- Alle operativen Daten werden verschlüsselt gespeichert. Die Sensitivitätsklasse entscheidet nicht über die Verschlüsselung.
- Sensible Inhalte benötigen nachvollziehbare Aufbewahrungs- und Löschregeln.
- IDs, Metadaten und Action-Zuordnungen können ebenfalls sensibel sein.
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
| `Person` | Personeneintrag innerhalb genau einer Action |
| `PersonStatusDefinition` | Konfigurierbarer Personenstatus einer Action |
| `Role` | Organisatorische Rolle einer Action |
| `PersonRole` | Person-Rollen-Zuordnung |
| `Task` | Aufgabe |
| `Material` | Materialposition |
| `Document` | Dokumenteintrag einschließlich Informationsmaterial |
| `FileAsset` | Verwalteter lokaler Dateiinhalt |
| `Note` | Notiz oder typisiertes Protokoll |
| `Expense` | Ausgabe mit optionalem Beleg |
| `RetentionPolicy` | Konzeptionelle Aufbewahrungsregel |

Gemeinsame Schutz- und Retention-Metadaten sind Bestandteile der betroffenen Objekte und erfordern nicht zwangsläufig eigene Entitäten.

## 5. Gemeinsame Schutz- und Retention-Metadaten

`Action`, `Person`, `Role`, `Task`, `Material`, `Document`, `FileAsset`, `Note` und `Expense` besitzen eine Sensitivitätsklasse. Personenbezogene Zuordnungen werden ebenfalls geschützt.

Die folgenden Retention-Attribute sind ein konzeptioneller Vorschlag. Fristen, Fristanker, Vererbung, Überschreibungen, technische Löschzustände und ihre Persistenz werden erst in `data-retention.md` und `security.md` verbindlich festgelegt.

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `sensitivity_class` | Enum | P | Schutzklasse des Objekts |
| `retention_policy_id` | Referenz | P | Zugeordnete Aufbewahrungsregel |
| `retention_anchor_at` | Zeitpunkt | B | Fristbeginn, sobald das auslösende Ereignis eingetreten ist |
| `retention_due_at` | Zeitpunkt | B | Löschtermin, sobald bestimmbar |
| `retention_override_due_at` | Zeitpunkt | O | Individuell festgelegter Termin |
| `effective_delete_at` | Zeitpunkt | A | Wirksamer Termin nach noch festzulegenden Frist- und Elternregeln |
| `deletion_state` | Enum | P | Technischer Zustand eines Löschvorgangs; noch zu spezifizieren |

Als technische Löschzustände kommen beispielsweise `NONE`, `PENDING`, `IN_PROGRESS` und `FAILED_RETRYABLE` infrage. Diese Werte sind keine beschlossene Sicherheitsarchitektur und keine zusätzlichen Action-Statuswerte.

Ein noch nicht bestimmter Fristbeginn erlaubt keine unbegrenzte Speicherung. Ein nach erfolgreicher Löschung weiterhin bestehender Inhaltsdatensatz wird nicht vorgesehen.

## 6. Action

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

Die eigene Aufbewahrung einer Action und ihre Standardregel für Inhalte erfüllen unterschiedliche Aufgaben. `expected_end_at` darf nicht vor `start_at` liegen. Regeln für unvollständige Entwürfe bleiben unter OD-02 offen.

### 6.2 Verbindliche Statuswerte

| Wert | Anzeige | Bedeutung |
|---|---|---|
| `DRAFT` | Entwurf | Action wird konkretisiert |
| `PREPARATION` | Vorbereitung | Organisatorische Vorbereitung läuft |
| `ACTIVE` | Aktiv | Action läuft |
| `COMPLETED` | Abgeschlossen | Durchführung beendet; Nachbereitung und Aufbewahrung |
| `DELETION_PENDING` | Löschung ausstehend | Vollständige Action-Löschung angefordert oder fällig |

`DELETED` ist ausschließlich das terminale Ergebnis der Löschung. Es ist kein persistierter Statuswert und kein verbleibender Action-Datensatz.

„Archiviert“ und „Aufbewahrung / Review“ bezeichnen die Phase `COMPLETED`, keinen weiteren Status. Abgeschlossene Actions werden im MVP nicht wiedereröffnet.

### 6.3 Lebenszyklus

```text
DRAFT → PREPARATION → ACTIVE → COMPLETED → DELETION_PENDING → Löschung abgeschlossen
```

Der Abschluss setzt `completed_at`. Welche Retention-Regeln daran anknüpfen, bleibt offen. Die Löschung einzelner Inhalte bedeutet nicht die vollständige Löschung der Action.

Statuswechsel erfolgen fachlich; geplante Zeiten allein lösen keinen automatischen Wechsel aus. Direkter Abschluss nicht durchgeführter Actions, Rückwärtsübergänge vor Abschluss und Abbruchverhalten bleiben unter OD-01 zu konkretisieren.

## 7. Personen und Rollen

### 7.1 Person

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Genau eine Action |
| `name` | Text | O | Name, sofern erforderlich |
| `pseudonym` | Text | O | Alternative Bezeichnung |
| `phone` | Text | O | Telefonnummer, sofern erforderlich |
| `status_definition_id` | Referenz | O | Aktueller organisatorischer Status |
| `created_at` | Zeitpunkt | P | Erstellung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Name, Pseudonym und Telefonnummer sind unabhängig voneinander optional. Es gibt keine E-Mail und keinen globalen Personeneintrag. Personenbezogene Notizen werden ausschließlich über `Note` modelliert. Ohne identifizierende Angaben wird kein zusätzlicher Pflichtname verlangt.

Ein Löschzeitpunkt wird nicht durch Erhaltung eines gelöschten Personendatensatzes dokumentiert.

### 7.2 PersonStatusDefinition

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Geltungsbereich |
| `label` | Text | P | Nicht leere Bezeichnung |
| `sort_order` | Ganzzahl | O | Auswahlreihenfolge |

Statusdefinitionen gelten pro Action. Ausgangsbeispiele: angefragt, zugesagt, anwesend, abwesend, zurück, unbekannt. Es wird nur der aktuelle Status gespeichert, keine Statushistorie und kein Standortverlauf.

Definitionen werden spätestens mit ihrer Action gelöscht. Das Verhalten beim Entfernen verwendeter Definitionen ist in Abschnitt 15 beschrieben. „Nicht gesetzt“ und ein ausdrücklich gewählter Status „unbekannt“ sind unterscheidbar.

### 7.3 Role

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Genau eine Action |
| `name` | Text | P | Nicht leere Rollenbezeichnung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Eine Person kann mehrere organisatorische Rollen besitzen. Diese Rollen sind keine technischen Zugriffsberechtigungen.

### 7.4 PersonRole

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `person_id` | Referenz | P | Person |
| `role_id` | Referenz | P | Rolle |

Das Paar ist eindeutig. Beide Bezugspunkte gehören derselben Action an. Die Zuordnung ist mindestens so schutzbedürftig wie die Person und endet spätestens mit der Löschung eines Bezugspunkts.

## 8. Task

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Action |
| `title` | Text | P | Nicht leerer Titel |
| `description` | Text | P | Beschreibung gemäß FR-030 |
| `assigned_person_id` | Referenz | O | Höchstens eine verantwortliche Person |
| `status` | Enum | P | Aufgabenstatus |
| `priority` | Enum | P | `LOW`, `NORMAL`, `HIGH`; Standard `NORMAL` |
| `due_at` | Zeitpunkt | O | Fälligkeit |
| `created_at` | Zeitpunkt | P | Erstellung |
| `category` | Text | O | Kategorie im Fachbereich Aufgaben |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Statuswerte: `OPEN` (offen), `IN_PROGRESS` (in Arbeit), `DONE` (erledigt), `DISCARDED` (verworfen).

Aufgaben dürfen ohne verantwortliche Person und ohne Fälligkeit bestehen. Eine zugewiesene Person muss derselben Action angehören.

„Überfällig“ wird abgeleitet: Fälligkeit überschritten und Status `OPEN` oder `IN_PROGRESS`. Ohne Fälligkeit ist eine Aufgabe nicht überfällig.

## 9. Material

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Action |
| `name` | Text | P | Nicht leere Bezeichnung |
| `category` | Text | O | Kategorie im Fachbereich Material |
| `quantity` | Ganzzahl | P | Anzahl, mindestens null |
| `location` | Text | O | Manuell erfasster Materialstandort |
| `responsible_person_id` | Referenz | O | Zuständige Person derselben Action |
| `status` | Enum | P | Materialstatus |
| `note` | Text | O | Materialbezogene Anmerkung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Vorgeschlagene Statuswerte entsprechend PRODUCT.md: `AVAILABLE`, `NEEDED`, `BORROWED`, `IN_TRANSIT`, `DEFECTIVE`, `UNKNOWN`. Ob die Wertemenge abschließend ist, bleibt unter OD-03 offen.

Materialmengen sind ganzzahlig. Es gibt keine Einheitenverwaltung und keine Ausleihhistorie. Der Materialstandort ist kein Personenstandort und kein GPS-Verlauf. Eine Position beschreibt eine Menge mit gemeinsamem Standort und Status; das Teilmengenverhalten bleibt unter OD-03 offen.

## 10. Dokumente, Dateien und Bibliothek

### 10.1 Document

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Action |
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
| `action_id` | Referenz | P | Action des Eigentümers |
| `storage_locator` | Undurchsichtige Referenz | P | Interner Speicherbezug ohne sprechenden Dateinamen |
| `original_filename` | Text | O | Ursprünglicher Dateiname, sofern benötigt |
| `mime_type` | Text | P | Geprüfter Inhaltstyp |
| `size_bytes` | Ganzzahl | P | Größe des ursprünglichen Inhalts |
| `created_at` | Zeitpunkt | P | Import |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Ein `FileAsset` hat genau einen fachlichen Eigentümer: entweder ein `Document` oder eine `Expense`. Die Eigentümerbeziehung wird über deren Dateireferenz dargestellt und muss exklusiv sein. Es gibt keine actionübergreifende Datei-Deduplizierung.

Dateinamen und Datei-Metadaten können personenbezogen sein. Der lokale Import schützt oder löscht die externe Quelldatei nicht automatisch. Eine dauerhafte Quell-URI ist im Modell nicht vorgesehen.

Kryptografisches Schlüsselmaterial ist kein Fachattribut. Erforderliche Schlüsselreferenzen, Importzustände und Bereinigungszustände werden separat spezifiziert.

## 11. Note: Notizen und Protokolle

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Action |
| `person_id` | Referenz | O | Zusätzliche Personenzuordnung |
| `kind` | Enum | P | `NOTE` oder `MINUTES` |
| `title` | Text | O | Überschrift |
| `content` | Text | P | Nicht leerer Inhalt |
| `created_at` | Zeitpunkt | P | Erstellung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Ohne Personenzuordnung ist eine Note actionbezogen. Mit Personenzuordnung ist sie personenbezogen; die Person gehört derselben Action an.

Protokolle sind typisierte Notes. Teilnehmerlisten, Autorenprofile und Versionshistorien gehören nicht zum MVP. Personenbezogene Notizen werden nicht zusätzlich in `Person` gespeichert.

## 12. Expense: Kosten und Belege

| Attribut | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | ID | P | Kennung |
| `action_id` | Referenz | P | Action |
| `amount` | Geldbetrag | P | Ausgabenbetrag |
| `currency_code` | Währungscode | P | Währung; Umfang noch offen |
| `expense_date` | Datum | P | Fachliches Ausgabedatum |
| `category` | Text | P | Kategorie im Fachbereich Kosten |
| `description` | Text | P | Beschreibung |
| `receipt_file_asset_id` | Referenz | O | Höchstens ein Beleg |
| `created_at` | Zeitpunkt | P | Erfassung |
| gemeinsame Metadaten | — | P/B/O/A | Schutzklasse und Retention |

Ausgabedatum und Erfassungszeitpunkt werden unterschieden. Kosten- und Kategoriesummen werden berechnet. Verschiedene Währungen dürfen ohne definierte Umrechnung nicht addiert werden.

Pro Ausgabe gibt es höchstens einen Beleg. Belege werden als operative Daten verschlüsselt gespeichert. Die Formulierung „optional verschlüsselt“ in FR-082 begründet keine Ausnahme von dieser Regel. Währungen und Erstattungen bleiben unter OD-05 offen.

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

Fristanker wie Objekterstellung oder Action-Abschluss, Regelversionierung, individuelle Termine und die Fristauflösung sind Empfehlungen unter OD-06, keine bereits beschlossenen Regeln.

Regeln sollen keine Personennamen, Action-Titel oder sensiblen Freitextbegründungen enthalten. Warnungen ersetzen keine Löschung und dürfen nicht stillschweigend Fristen verlängern.

## 14. Sensitivitätsklassen

Verbindliche Klassen:

| Klasse | Bedeutung | Beispiele |
|---|---|---|
| `PUBLIC_CONTENT` | Inhalt zur Veröffentlichung geeignet | Öffentliche FAQ oder Flyer |
| `INTERNAL` | Interne organisatorische Information | Allgemeine Materialplanung |
| `SENSITIVE` | Personenbezogene oder besonders vertrauliche Information | Kontakte, Teilnahme, Personenzuordnungen, interne Protokolle |

Die Klassen sind Produkt-Schutzkategorien, keine abschließende rechtliche Klassifizierung. Alle operativen Daten werden unabhängig von der Klasse verschlüsselt gespeichert.

Für die konkrete Anwendung werden folgende Regeln empfohlen; ihre Standardwerte und Auswirkungen bleiben unter OD-09 zu bestätigen:

- `Person`, `PersonRole` und personenbezogene Notes sind `SENSITIVE`.
- Personenreferenzen in Aufgaben und Material machen die Zuordnung sensibel.
- Ungeprüfte Importe und Freitext starten konservativ als `SENSITIVE`.
- Öffentlich geeigneter Inhalt kann durch seine Action-Zuordnung sensible Metadaten besitzen.
- Entfernen einer Personenreferenz führt nicht automatisch zu einer Herabstufung.
- Klassifizierung erlaubt keine Umgehung von App-Sperre, Backup-Ausschluss oder Retention.

Zunächst erfolgt die Klassifizierung auf Objektebene; keine zusätzliche Feldklassifizierung ist vorgesehen.

## 15. Beziehungen, Integrität und Löschung

### 15.1 Kardinalitäten

| Beziehung | Kardinalität |
|---|---|
| Action → Person | 1 : 0..n |
| Action → PersonStatusDefinition | 1 : 0..n |
| PersonStatusDefinition → Person | 1 : 0..n; Person hat 0..1 aktuellen Status |
| Action → Role | 1 : 0..n |
| Person ↔ Role | n : m über PersonRole |
| Action → Task | 1 : 0..n |
| Person → zugewiesene Task | 1 : 0..n; Task hat 0..1 verantwortliche Person |
| Action → Material | 1 : 0..n |
| Person → verantwortetes Material | 1 : 0..n; Material hat 0..1 verantwortliche Person |
| Action → Document | 1 : 0..n |
| Document → FileAsset | 1 : 1 |
| Action → Note | 1 : 0..n |
| Person → Note | 1 : 0..n; Note hat 0..1 Person |
| Action → Expense | 1 : 0..n |
| Expense → FileAsset als Beleg | 1 : 0..1 |
| RetentionPolicy → zugeordnete Objekte | 1 : 0..n |

Jedes operative Kindobjekt gehört genau einer Action. Jedes FileAsset besitzt genau einen Eigentümer; die Beziehung zu Dokument und Ausgabe ist exklusiv.

### 15.2 Integritätsregeln

- Referenzen zeigen auf vorhandene Objekte derselben Action.
- PersonRole-Paare sind eindeutig.
- Ein fertiger Dokumentimport besitzt einen verfügbaren verwalteten Dateiinhalt.
- Unvollständige Imports erscheinen nicht als fertige Dokumente.
- Änderungen an Fachobjekten und Beziehungen müssen konsistent erfolgen.
- Datenbanktransaktionen allein garantieren keine atomare Änderung von Dateien und Schlüsselmaterial. Der übergreifende Ablauf wird in der Architektur spezifiziert.
- Gelöschte Inhalte dürfen nicht über Suchindizes, Vorschauen oder Caches weiter verfügbar bleiben; die technische Garantie ist noch festzulegen.

### 15.3 Fachliche Löschbeziehungen

Diese Tabelle beschreibt die fachliche Zielwirkung des grundsätzlich freigegebenen Modells. Sie legt weder technische Löschgarantien noch eine Vorrangregel bei kollidierenden Retention-Fristen fest.

| Gelöschtes Objekt | Fachliche Folge |
|---|---|
| Action | Zugehörige Personen, Rollen, Statusdefinitionen, Zuordnungen, Aufgaben, Material, Dokumente, Dateien, Notes und Ausgaben entfernen |
| Person | PersonRole und personenbezogene Notes entfernen; Personenreferenzen in Aufgaben und Material entfernen |
| Role | PersonRole entfernen; Personen erhalten |
| PersonStatusDefinition | Statusreferenzen entfernen; Personen erhalten |
| Task | Aufgabe und abgeleitete Kopien entfernen; Person erhalten |
| Material | Materialposition und abgeleitete Kopien entfernen; Person erhalten |
| Document | Dokument und zugehöriges FileAsset entfernen |
| Note | Note entfernen; Action und Person erhalten |
| Expense | Ausgabe und optionalen Beleg entfernen |
| FileAsset eines Dokuments | Dokument ebenfalls entfernen, damit kein funktionsloser Eintrag verbleibt |
| FileAsset eines Belegs | Belegreferenz entfernen; Ausgabe bleibt bis zu ihrer eigenen Löschung bestehen |
| RetentionPolicy | Keine Inhaltskaskade; verwendete Regel darf nicht ersatzlos entfernt werden |

Die Entfernung einer Personenreferenz anonymisiert keinen Freitext. Namen oder identifizierende Angaben in Aufgaben, Materialnotizen, Dateien und Belegen können verbleiben. Eine zuverlässige automatische Erkennung wird nicht vorausgesetzt. Ein Bereinigungsablauf ist unter OD-10 offen.

### 15.4 Technische Löschung: ausdrücklich offen

Fachliche Entfernung und technische Bereinigung sind zu unterscheiden. Zu berücksichtigen sind strukturierte Daten, Beziehungen, Dateien, entschlüsselte temporäre Inhalte, Vorschauen, Caches, Suchindizes, Schlüsselmaterial und Speicherrückstände einschließlich Datenbankjournalen.

Schlüsselbasierte Dateilöschung setzt eine geeignete Schlüsselstruktur voraus. Diese ist hier nicht festgelegt. Es wird keine garantierte physische Überschreibung von Flash-Speicher behauptet.

Löschgarantien, Wiederanlauf, Fehlermeldung, mögliche minimale Löschprotokolle sowie Verhalten bei gesperrter App oder ausgeschaltetem Gerät werden in `security.md` und `data-retention.md` festgelegt.

## 16. Abgrenzungen

- Keine gespeicherte Organisation und keine globale Personenkartei im MVP.
- Kategorien bleiben getrennt je Fachbereich. Es gibt keine zentrale Taxonomie. Die einfache Darstellung erfolgt zunächst als Kategorieangabe am Objekt.
- Der lokale Admin ist ein technischer Akteur, keine Teilnehmerperson. Kein persönliches Adminprofil wird eingeführt.
- Authentifizierungsdaten, Fehlversuchszähler, Sperrfristen und Schlüsselreferenzen gehören in ein separates Sicherheitsmodell. Biometrie bleibt gemäß PRODUCT.md ausgeschlossen.
- Suche und Dashboard erzeugen keine zusätzlichen fachlichen Stammdaten. Eventuelle persistierte Indizes müssen Schutz und Löschung ihrer Ausgangsdaten berücksichtigen.
- Eine dauerhafte organisatorische Änderungs- oder Löschhistorie wird nicht automatisch eingeführt.
- Dateninventar, Zweck, Speicherort und konkrete Schutzmaßnahmen werden ergänzend in `privacy.md`, `architecture.md` und `security.md` dokumentiert.

## 17. Offene Entscheidungen

### OD-01 — Weitere Action-Übergänge

**Offen:** Direkter Abschluss aus DRAFT/PREPARATION, Rückwärtsübergänge vor Abschluss, Behandlung nicht durchgeführter Actions und Abbruch eines noch nicht abgeschlossenen Löschvorgangs.

**Empfehlung:** Nicht durchgeführte Actions direkt abschließen, ohne neuen Status. Rückwärtsübergänge vor Abschluss eng begrenzen. Keine Wiedereröffnung nach COMPLETED; dies ist bereits verbindlich. Verhalten von DELETION_PENDING erst zusammen mit dem Löschablauf festlegen.

### OD-02 — Pflichtangaben beim Anlegen einer Action

**Offen:** FR-001 verlangt Beschreibung und Zeitraum, während der einfache User Flow weniger Eingaben nennt. Für Aufgaben ist die Beschreibung nicht ausdrücklich optional.

**Empfehlung:** Die angegebenen Pflichtfelder aus FR-001 und FR-030 beibehalten. Falls unvollständige Entwürfe benötigt werden, zustandsabhängige Pflichtregeln ausdrücklich ergänzen. Optionale Verantwortung und Fälligkeit von Tasks sind bereits verbindlich.

### OD-03 — Materialstatus und Teilmengen

**Offen:** Ob die Beispielstatus abschließend oder konfigurierbar sind; Umgang mit verschiedenen Zuständen und Standorten einer Menge.

**Empfehlung:** Die sechs genannten Werte als feste MVP-Wertemenge verwenden. Unterschiedliche Teilmengen als getrennte Positionen führen. Ganzzahlige Mengen sowie der Ausschluss von Einheitenverwaltung und Ausleihhistorie sind bereits verbindlich.

### OD-04 — Dateiimporte und technische Zustände

**Offen:** Konkrete erlaubte Formate, Inhaltsprüfung, maximale Dateigrößen sowie Import-, Fehler- und Bereinigungszustände.

**Empfehlung:** Einen begrenzten Formatumfang nach der Produktpriorität festlegen. Unvollständige Dateien bis zur erfolgreichen Übernahme nicht fachlich sichtbar machen. Technische Zustände in architecture.md und security.md spezifizieren. Exklusives Eigentum, höchstens ein Beleg und keine actionübergreifende Deduplizierung sind bereits verbindlich.

### OD-05 — Geldbeträge und Währungen

**Offen:** Währungsumfang, Rundung, null beziehungsweise negative Beträge und Erstattungen.

**Empfehlung:** Eine festgelegte Währung je Action und positive Ausgaben verwenden. Beträge exakt speichern. Erstattungen und Umrechnung erst nach eigener fachlicher Spezifikation einführen.

### OD-06 — Retention-Regeln und Metadaten

**Offen:** Standardfristen, Einheiten, Fristanker, maximale Aufbewahrung nicht abgeschlossener Actions, individuelle Termine, Vererbung, Regelversionierung und Neuberechnung.

**Empfehlung:** In data-retention.md Objekterstellung und tatsächlichen Action-Abschluss als mögliche Anker prüfen. Für abschlussabhängige Regeln eine maximale Lebensdauer vorsehen. Verwendete Regeln versionieren; keine stillschweigende Verlängerung bestehender Termine. Die Attribute in Abschnitten 5 und 13 bleiben bis dahin konzeptionell.

### OD-07 — Elternlöschung und längere Kinderfristen

**Offen:** Vorrang von Action-Löschung gegenüber längeren Fristen einzelner Ausgaben, Dokumente oder Belege sowie mögliche Aufbewahrungsausnahmen.

**Empfehlung:** Vollständige Action-Löschung als obere Grenze verwenden und Folgen vorab darstellen. Falls Kostenunterlagen länger benötigt werden, zunächst die Action-Frist passend festlegen. Keine versteckt verbleibenden verwaisten Datensätze. Eine unabhängige Aufbewahrung benötigt eine ausdrückliche fachliche Entscheidung in data-retention.md und privacy.md.

### OD-08 — Schlüsselverwaltung, Kryptografie und Löschgarantien

**Offen:** Schlüsselhierarchie, Authentifizierung, Schlüsselverlust, Dateischlüssel, technische Löschzustände, Wiederanlauf und die nachweisbaren Grenzen der Löschung.

**Empfehlung:** In threat-model.md und security.md ein getrenntes Sicherheitsmodell festlegen; etablierte Verfahren verwenden. Fehlgeschlagene Bereinigung nicht als erfolgreich melden. Minimal erforderliche Arbeitsdaten schützen und begrenzen. Verhalten bei fehlendem Schlüsselzugriff, ausgeschaltetem Gerät und Zeitmanipulation ausdrücklich behandeln. Hier werden keine konkreten Verfahren oder Bibliotheken ausgewählt.

### OD-09 — Anwendung der Sensitivitätsklassen

**Offen:** Standardklassifizierung, erlaubte Herabstufung und Auswirkungen auf Darstellung, Weitergabe und Retention.

**Empfehlung:** Personen und personenbezogene Zuordnungen als SENSITIVE behandeln. Ungeprüfte Importe konservativ klassifizieren; keine automatische Herabstufung nach Entfernung einer Referenz. In privacy.md und security.md Schutzregeln festlegen. Die drei Klassen und Verschlüsselung unabhängig von der Klasse sind bereits verbindlich.

### OD-10 — Verbleibender Personenbezug nach Löschung

**Offen:** Prüfbarer Bereinigungsablauf für identifizierende Freitexte und Dateien, die nicht direkt mit Person verknüpft sind.

**Empfehlung:** Fachliche Beziehungskaskaden um einen nachvollziehbaren manuellen Prüfablauf ergänzen. Entfernte Referenzen nicht als vollständige Anonymisierung bezeichnen. Abgelaufene Personendaten nicht wegen offener Zuständigkeiten unbegrenzt erhalten.

### OD-11 — Zeitmodell

**Offen:** Zeitzonen der Action-Planung, Kalenderdauer gegenüber fester Zeitdauer, Sommerzeit und veränderte Gerätezeit.

**Empfehlung:** Erstellungs- und Löschzeiten als eindeutige Zeitpunkte, Ausgabedaten als Kalendertage behandeln. Eine explizite Zeitzonenregel für Action-Planung festlegen. Fristberechnung und Zeitmanipulation im Retention-Konzept berücksichtigen.

### OD-12 — Grenzen und Validierung

**Offen:** Maximale Textlängen, Objektzahlen, Dateigrößen, Namenvalidierung und gegebenenfalls Eindeutigkeit von Rollen- und Statusbezeichnungen.

**Empfehlung:** Grenzen vor Implementierung mit synthetischen Testfällen bestimmen. Gleichnamige Personen zulassen; keine automatische Zusammenführung über Name oder Telefonnummer. Eindeutigkeit nur bei fachlichem Bedarf erzwingen, insbesondere beim PersonRole-Paar.

### OD-13 — Nachvollziehbarkeit und technische Sicherheitsdaten

**Offen:** Ob eine dauerhafte Änderungshistorie oder ein Löschprotokoll erforderlich ist; Umfang der technischen Authentifizierungsdaten.

**Empfehlung:** Nur nachgewiesenen Bedarf dokumentieren und eigene Retention festlegen. Keine sensiblen Inhalte in Löschprotokollen erhalten. Authentifizierung und Verschlüsselung getrennt in security.md spezifizieren; keine Klartext-Credentials oder persönlichen Admindaten in Fachentitäten aufnehmen.

## 18. Nächste Spezifikationen

Der freigegebene fachliche Modellstand dient als Grundlage für threat-model.md, privacy.md, data-retention.md, security.md und architecture.md. Die offenen Entscheidungen müssen vor ihrer jeweiligen Implementierung geklärt werden. Dieses Dokument gibt keine Android-Implementierung oder Auswahl von Dependencies frei.
