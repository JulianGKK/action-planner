# Schema-12-Vorschlag – Freigabe erforderlich, nicht implementiert

## Beantragte Entscheidung

**S12-P (kleinster zwingender Umfang):** persistierte monotone `persons.editRevision`, Schema11→12, verpflichtende Revision in jedem Person-Expected und bedingtes Rename. Damit wird ausschließlich der Personen-ABA-Blocker geschlossen. **Keine vollständige #36-Freigabe**, weil Rollen-/Plan-ABA und nichtmonotone Task-/Statuszeitstempel weiterhin bestehen.

**Empfehlung für einen vollständigen #36-Abschlussversuch: S12-P + S12-E.** Zusätzlich eigene Revisionen für `roles`, `person_role_plans`, `person_operational_states` und `tasks`; unveränderte fachliche Zeitstempel. Die Freigabe muss diese Tabellen, die stabile Planheader-Lebensdauer und sämtliche zugehörigen Reader-/Expected-/CAS-/Fixture-/Budgetanpassungen ausdrücklich umfassen. Kein globaler Counter, keine Hilfstabelle, keine zweite Mutationsepoche oder Sessionautorität. Nicht eigenmächtig aus einer Freigabe nur für Personen ableiten.

Aktueller Auftrag stoppt vor dieser Integration. Kein Migration-Objekt, Schema12-Export, persistierter Ersatzcounter oder produktiver DAO-Fix wird hier hinzugefügt. Die folgenden SQL-Fragmente sind **Entwurf, nicht ausgeführter Migrationsnachweis**.

## S12-P: additive Personenrevision

Normativer Bereich: INTEGER `0..9223372036854775807`. Jeder akzeptierte Personen-Updatebefehl erhöht die Revision genau einmal, auch wenn sein Zieltext identisch ist. Create setzt explizit0; kein Rekonstruieren aus Wallclock, Pseudonym oder `createdAt`. Rollback rollt Text **und** Revision zurück. Keine Rücksetzung oder Wiederverwendung einer bestehenden Personenidentität. Bestehendes UUID-Create/PK-ABORT bleibt; künftiger Import/Restore darf keine alte ID mit Revision0 wiederbeleben.

Schema11→12 ausschließlich in originaler autorisierter MAINTENANCE, vor READY und sämtlichen Editor-Reads. Kein Migrationsstart aus einem normalen READY-Read. Versionszulassung in ActionDatabaseAccess, Room-Databaseversion, registrierte Migrationskette und frischer Schema12-Aufbau werden gemeinsam angepasst. Native-Versionen >12 bleiben verweigert; keine Destructive-Migration/Downgrade-/Repairfreigabe. Originale Cipher-/FK-/Integrity-/Keyscope- und Cleanupregeln bleiben.

```sql
-- Innerhalb der von Room bestätigten, atomaren Migration 11 -> 12:
ALTER TABLE persons ADD COLUMN editRevision INTEGER NOT NULL DEFAULT 0;
```

Der DEFAULT backfillt **alle** vorhandenen Personen unabhängig vom Aktionsstatus mit0. Null ist nur der neue Revisions-Ausgangswert, keine Zahl historischer Änderungen. Inhalt, Normalisierung, IDs, `createdAt`, Indizes, referenzierende Tabellen und FK-Beziehungen bleiben unverändert. Historische Migrationen bis11 behalten ihre alten Definitionen; nicht nachträglich Schema3- oder Schema11-DDL auf zwölf umschreiben. Das Room-Freshschema muss denselben NOT NULL/DEFAULT0-Vertrag exportieren (`@ColumnInfo(defaultValue="0")`); nicht ausschließlich in Migrationen versteckte CHECK-Constraints hinzufügen, die das frische Room-Schema nicht abbildet.

Vor Abschluss der Migration und vor READY prüfen:

```sql
SELECT COUNT(*) FROM persons
WHERE typeof(editRevision) != 'integer' OR editRevision < 0;
-- Muss 0 ergeben. REAL/Text/NULL sind kein zulässiger Revisionsersatz.
PRAGMA foreign_key_check; -- keine Verletzungen
```

Zusätzlich bisherige Integritätsprüfungen, gleiche Personenanzahl und unveränderte fachliche Spalten/Indizes; nur synthetische Testdaten dürfen im Testvergleich ausgegeben werden. Datenbankseitige Typaffinität allein ist kein vollständiger Semantiknachweis. Revisionstyp/-bereich auch in Reader-/Writer-Validierung kontrollieren, bevor ein Expected publiziert oder ein Update zugelassen wird. Fehler => bestehendes fail-closed Storageverhalten, kein erfundener Revision0-Fallback. Ein abgebrochener Migrations-Commit darf weder Teil-Schema12 noch READY ergeben.

`PersonRow.model`, Domain `Person`, vollständige Workspace-/Personreader und sämtliche eigenen SQL-Projektionen führen die gespeicherte Revision unverändert mit. Neuer Domainparameter ohne stillen Default für vorhandene Expected-Werte; Compilerfehler bei bisherigen Konstruktoren gezielt beheben. Neu erzeugte Personen/Fixtures setzen0 explizit. Alter UI-Key `personEditorRevision` bleibt ein **flüchtiger Anzeige-/Reloadkey**, keine persistierte CAS-Revision. Kein Schema11-Expected wird beim Save nachträglich mit einer aktuellen Revision aufgewertet.

Unter derselben bestehenden äußeren IMMEDIATE-Transaktion: Original-Action-/Status-/Sessionregeln; vollständigen alten Person-Snapshot inkl. Revision prüfen; Duplikatregeln erhalten; anschließend:

```sql
UPDATE persons
SET pseudonym = :newPseudonym,
    pseudonymNormalized = :newNormalized,
    editRevision = editRevision + 1
WHERE actionId = :actionId AND id = :personId
  AND typeof(editRevision) = 'integer'
  AND editRevision = :expectedRevision
  AND editRevision >= 0 AND editRevision < 9223372036854775807;
```

Genau1 betroffene Zeile erforderlich. Bei0 im **gleichen** Tx nachprüfen: fehlende/andere Identität => bisherige NOT_FOUND/STALE; gültige andere Revision => STALE; unzulässiger Typ/Bereich => Storagefehler; gültige MAX-Revision => kontrollierte fachliche `REVISION_EXHAUSTED`-Ablehnung mit verständlicher UI, kein Wrap, REAL-Promotion oder Reset. Keine automatische Wiederholung mit frisch eingelesener Revision. Erst explizites Neuladen darf Expected/Draft ersetzen. Ergebnisrevision aus dem tatsächlichen DB-Wert zurücklesen, nicht unkontrolliert JVM-`expected+1` rechnen. Den alten unbedingten Rename-DAO entfernen/ersetzen; keinen Umgehungspfad behalten.

Erfolg unverändert erst nach äußerem Commit + separat bestätigtem Cleanup + originalem Publicationgate. UNKNOWN bleibt terminal im bisherigen Vertrag. Kein TaskEvent als Personenrevision missbrauchen, keine zusätzliche Protokollzeile pro Rename und kein Eingriff in #35-Recovery.

## S12-E: zusätzlicher exakt benannter Editorumfang

```sql
ALTER TABLE roles ADD COLUMN editRevision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE person_role_plans ADD COLUMN editRevision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE person_operational_states ADD COLUMN editRevision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD COLUMN editRevision INTEGER NOT NULL DEFAULT 0;

-- Fehlender und leerer Plan waren bisher logisch gleich; stabile Revision braucht Header:
INSERT INTO person_role_plans (actionId,personId,primaryRoleId,editRevision)
SELECT p.actionId,p.id,NULL,0 FROM persons p
WHERE NOT EXISTS (SELECT 1 FROM person_role_plans r
                  WHERE r.actionId=p.actionId AND r.personId=p.id);
```

Alle vier Revisionen erhalten denselben Integer-/Overflow-/Rollbackvertrag. Zeitfelder behalten ihre fachliche Bedeutung; `MAX(now,old+1)` wird nicht stillschweigend als neue Zeitpolitik eingeführt.

- **Rolle:** Domain-/Expected-Role inkl. editRevision; Rename und Löschbestätigung prüfen Revision. Rename erhöht sie über bedingtes SQL; Delete nur bei passender Revision und unveränderten IN_USE-/Task-/Planreferenzregeln. Create neue UUID/revision0, keine ID-Wiederverwendung.
- **Plan:** Expected umfasst gespeicherte Headerrevision plus vollständige immutable Rollenmenge und Primärrolle. Fehlende Header nach Migration sind Fehler, keine implizite Revision0. Neues Person-Create erzeugt seinen leeren Planheader in derselben Tx. Bestehenden `removePlan`→`insertPlan`-Zyklus **ersetzen**, sonst würde die Revision wieder auf0 fallen. Zuerst CAS-Update Header mit `primaryRoleId=NULL` und Revision+1; nur bei Zeile1 alte Zuordnungen löschen und neue einfügen, abschließend Primärrolle bei derselben bereits erhöhten Revision setzen. Alles in derselben Tx; FK-RESTRICT beachten, jede spätere Ablehnung rollt den gesamten Plan zurück. No-op-Plan erhöht ebenfalls einmal. Kein temporärer Nullzustand wird publiziert.
- **Aktueller Rollen-/Status-/Ortszustand:** bestehender vollständiger Expected inkl. eigener editRevision. CAS-Update aller Zielfelder mit Revision+1; `updatedAt` bleibt Wallclock. Initiale Zustandszeilen beim PREPARATION→ACTIVE-Übergang setzen0. In Schema11 vorhandene Zeilen bekommen0; fehlende/inkonsistente Altdaten nicht durch erfundene Beobachtungen „reparieren“.
- **Task:** vollständiger Expected inkl. eigener editRevision für Edit/Transition/Delete; Create UUID/revision0. Jede tatsächliche Aktualisierung erhöht einmal. Eventloser Edit bleibt eventlos. Transition-CAS + Event-COUNT-Gate + abhängiges Taskupdate + exakt ein Event bleiben **eine** bestätigte äußere Transaktion; Revision oder Eventkapazität verweigert => kein Event und kein Task-/Revisionsteilcommit. MAX-Revision als kontrollierte Ablehnung ohne Event.

Diese Einzelrevisionen machen keinen gesamten Rollen-Katalogsnapshot aus einem Expected-Plan: aktuell werden Rollen-IDs/Primärrolle und gegenwärtige Rollenexistenz geprüft, keine Katalognamenversion. Falls Änderungen an referenzierten Rollennamen während Planung ebenfalls zwingend einen Konflikt erzeugen sollen, diese **zusätzliche Fachentscheidung** ausdrücklich treffen und Expected-Referenzrevisionen aufnehmen; keinen globalen Katalogcache erfinden.

## Nötige Integrations- und Abnahmeschritte nach Freigabe

1. Versioniertes Room-Schema12/Migration/Freshschema sowie Model-/DAO-/Readerverträge; alle SQL-/Host-/P77-Fixtureinserts auf explizite Spalten/Revision0 prüfen. Keine alten Writer parallel zur neuen Migrationsgeneration zulassen.
2. Migrationsgegenfälle: gültiges11 mit allen Aktionsstatus, Personen mit/ohne Planheader, Primärrollen/FKs, Taskhistory bis5.000; Inhalt/IDs/Anzahlen erhalten; frisches12 und migriertes12 gleich validiert; abgebrochener Migrationstx, ungültige Werte und unbestätigtes Cleanup => kein READY. Revisionen in jedem relevanten DTO erhalten, kein Defaultmapping bei fehlenden Feldern.
3. Zwei echte SQLite-/SQLCipher-Verbindungen: beide lesen Revisionr; nur ein CAS akzeptiert, zweiter STALE. A→B→A lässt Felder gleich, Revisionr+2; alter Expectedr verweigert. Rollback/Fachablehnung/COUNT-Limit/Key-Revoke, MAX-1/MAX, falsche Typen, fremde Action/IDs, spätes UI und verlorener Main. Planheader darf bei Setwechsel nicht verschwinden. Task+Event atomar und genau einmal.
4. Drafts bei STALE behalten, bewusstes Neuladen übernimmt neue Revision; alter UI-Key/Fokus ist keine DB-Revision. Den in §103 separat benannten Live-Status-Reopen-/Draftvertrag ausdrücklich abnehmen. Lock/Action-/Generationswechsel verwerfen Formzustand; kein plaintext SavedState-/Datei-/Preferencespersistieren.
5. Neue app-kontrollierte Revisionsfelder, Expected-Objekte, Readerbuilder/Worker-/Mainübergaben vor Materialisierung in den bestehenden Budgetrechnungen berücksichtigen. Allein zusätzliche 8-Byte-Skalare sind **keine** vollständige RAMkostenrechnung. R8/T16 bleiben exakt unverändert; exakte Grenz-/+1-Tests für geänderte Repräsentationen. Keine RAM-Reuse-/Core-Readmissionfreigabe; #37 bleibt eigener Integrationsbereich, Native/Frameworkkosten #33/#39 getrennt.

**Benötigte Freigabe:** S12-P allein oder ausdrücklich S12-P+S12-E; keine Zustimmung aus dem Auftrag §103 ableiten. Ohne diese Entscheidung **BLOCKED_SCHEMA_APPROVAL / STOPP**.
