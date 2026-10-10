# P4 — Rollenplanung und operatives Lagebild v1

## Umfang und fachliche Trennung

DB-only: aktionsgebundene Rollenplanung in PREPARATION, aktueller Lage-Snapshot in
ACTIVE, lesbarer letzter Zustand in COMPLETED. Personenstammdaten und P3b bleiben
getrennt. Keine Files, Netzwerke, neue Authentifizierung oder Schlüsselhierarchie.

`Role` speichert ID, Aktions-ID, sichtbaren Namen, `normalizedName` und Erstellung.
Vergleich: Trim und `lowercase(Locale.ROOT)`, nicht leer, nur aktionslokal eindeutig.
Sichtbare Eingabe bleibt erhalten. Keine umfassende Unicode-Äquivalenz behauptet.
Die Domain-Vorlagen Blockierende, Support, Hummel, Gesa-Support, Presse und
Aktionsleitung erzeugen erst nach Auswahl normale Rollen. Keine globale Bibliothek,
automatische Rollen oder Demo-Personen. Eigene Rollen sind möglich.

Mehrere geplante Rollen pro Person liegen in `person_planned_roles`. Die optionale
Hauptfunktion liegt in `person_role_plans`: genau eine Row pro Aktion/Person,
zusammengesetzter Fremdschlüssel zur zugehörigen geplanten Rolle. Keine JSON-Liste.
Aktionsgebundene zusammengesetzte Fremdschlüssel sichern auch die operativen Rollen.

## Statusvertrag

| Aktionsstatus | Planung | Operativer Snapshot |
| --- | --- | --- |
| DRAFT | Kein Zugriff | Kein Zugriff |
| PREPARATION | Rollen erstellen/umbenennen, Zuordnungen und Hauptfunktion ändern | Noch nicht operativ veränderbar |
| ACTIVE | Plan lesen, nicht umschreiben; neue eigene Funktion möglich | Lesen und aktualisieren |
| COMPLETED | Read-only | Read-only |
| DELETION_PENDING | Kein normaler P4-Pfad | Kein normaler P4-Pfad |

PREPARATION → ACTIVE erzeugt innerhalb derselben Schreibtransaktion für jede Person
genau einen Snapshot: Hauptfunktion als aktuelle Rolle oder null, NO_STATUS,
kein Ort, gemeinsamer Initialisierungszeitpunkt. Sessionwiderruf vor Commit rollt
Status und alle Snapshots zurück. Es wird keine Anwesenheit oder Verfügbarkeit
behauptet. ACTIVE → COMPLETED erhält den Snapshot und macht ihn read-only.

Rollenlöschung nur in PREPARATION, ausdrücklich bestätigt und nur ohne geplante
oder operative Referenzen. Kein automatisches Cascade für Rollenzuordnungen.
Personenlöschung und Aktions-/Retentionlöschung werden hier nicht eingeführt;
ein späterer Gesamtlöschpfad muss die Abhängigkeiten geordnet auflösen.

## Aktueller Lage-Snapshot

`PersonOperationalState` speichert genau Aktion, Person, nullable aktuelle Rolle,
Status, nullable Ort/Ziel und `updatedAt`. Kein Status-, Bewegungs- oder Auditverlauf.

| Wert | Produkttext |
| --- | --- |
| NO_STATUS | Keine Lageangabe |
| ON_SITE | Vor Ort |
| IDENTITY_CHECK | Identitätsfeststellung |
| IN_CUSTODY | In Gewahrsam |
| NOT_REACHABLE | Nicht erreichbar |
| RELEASED | Wieder frei |
| LEFT | Nicht mehr vor Ort |
| UNCLEAR | Unklar |

Identitätsfeststellung, Gewahrsam, Nicht erreichbar, Nicht mehr vor Ort, Unklar
und Wieder frei setzen die aktuelle Funktion auf null. Nach Wieder frei kann die
Leitung bewusst Vor Ort und eine Funktion wählen; kein automatischer Wiedereinsatz.
NO_STATUS darf die beim Start übernommene Hauptfunktion halten, zählt aber nicht
als Person vor Ort. Die Funktionsverteilung zählt ausschließlich ON_SITE.

Ort/Ziel ist eine manuelle kurze lokale Bezeichnung: Trim, leer → null, maximal
80 Unicode-Codepoints; nur bei Identitätsfeststellung, Gewahrsam oder Unklar.
Kein GPS, Geocoding, Karte oder fremdes Tracking. Statuswechsel entfernt in der
Eingabe den bisherigen Ort; das Repository verwirft Orte bei nicht passenden
Statuswerten. Suggestions entstehen nur aus den derzeitigen Snapshots dieser Aktion.
Zeitpunktanzeige verwendet lokale Gerätezeit, keine persistierten relativen Texte
und keine Notification. Labels sind Koordinationsangaben, keine Rechtsbewertung.

## Bedienung

PREPARATION zeigt Rollenverwaltung mit Vorlagen/eigenem Namen sowie Rollenplanung
im Persondetail. Rollen auswählen, optional Hauptfunktion markieren, speichern
oder ohne Mutation abbrechen. ACTIVE stellt das Lagebild vor die einklappbaren
Aktionsdaten: Gesamt-/Statuszahlen, aktuelle Funktionsverteilung, pseudonyme Karten.

Tap auf Pseudonym öffnet direkt „Lage aktualisieren“: große textbeschriftete
Statusbuttons, aktuelle Auswahl markiert, bei Vor Ort Funktionen (geplante zuerst),
bei ortsbezogenen Statuswerten Suggestions und Ort/Ziel. „Übernehmen“ benötigt
keine weitere Bestätigung. „Neue Funktion“ bleibt aktionsgebunden und verändert
keine Planung. Personendetails und der bestehende P3b-Step-up sind separat erreichbar.
Kein Klarname oder sensibles Kontaktfeld im Lagebild.

Formulare verwenden die P3b-Fokus-/Keyboard-Komponenten. Neue operative Entwürfe
bleiben ausschließlich flüchtiger Compose-/Model-State. Navigation und Lock
verwerfen sie; kein SavedState, Prefs, DataStore oder Logging von Lageinformationen.

## Security-/Privacy-Vertrag

Rollenplanung und operativer Snapshot sind für P4 ausdrücklich operative INTERNAL-
Daten, trotz Personenbezug und erheblicher Sensibilität. Sie bleiben in der normalen
verschlüsselten SQLCipher-Produkt-DB und innerhalb einer gültigen READY-Sitzung
verfügbar. Kein P3b-Step-up pro Live-Änderung: zusätzliche Freigabe würde die schnelle
Koordination behindern. Ein Angreifer in einer offenen Session kann diese Angaben
sehen; das ist der ausdrücklich freigegebene Trade-off, keine Anonymitätsgarantie.

Unverändert: DEVICE_CREDENTIAL, Produkt-DB-Key, Generation, 3 Minuten absolut,
30 Sekunden READY-Inaktivität, App-Verlassen → LOCKED, sämtliche P3b-Crypto-/Grant-
und aktionsweiten Sensitive-Guards. Personenstammdaten bleiben ab ACTIVE unveränderbar.
Repositories prüfen persistierten Status/Zugehörigkeit im selben kontrollierten
Read-/Write-Vorgang. Vor neuem DAO-Schritt, Commit und Publication bleiben zentrale
Gates wirksam. Snapshot-Updates vergleichen den erwarteten gesamten Snapshot,
damit veraltete Eingaben keine inzwischen geänderte Lage blind überschreiben.

Kurze Room-Lebensdauer, keine observable DAO-Flows. Keine neue Dependency,
Permission, exportierte Komponente, Netzwerkfunktion, Kontakte, Telefon/SMS,
Location oder operative Notification. Backup-/Transfer-Ausschlüsse bleiben bestehen.
Keine RAM-/Flash-Löschgarantie. Retentionfristen und -Workflow bleiben offen.

## Additive Migration 4 → 5

Nur vier zusätzliche Tabellen und deren Indizes; bestehende Aktionen, Personen,
Leadership-Envelopes und sensitive Ciphertexts bleiben erhalten. Registrierte
Migration, Schemaexport, kein destructive fallback oder Sensitive-Provisionierung.

Für bereits ACTIVE/COMPLETED vorhandene Personen entstehen nur NO_STATUS-Snapshots
mit null Funktion/Ort und `updatedAt=0` als unbekanntem Aktualisierungszeitpunkt.
Keine historische Lage oder Beobachtungszeit wird erfunden. Vorbereitete Aktionen
bleiben ohne operative Snapshots bis zum regulären atomaren Start.

## Automatische Prüfungen

Status: **P4 AUTOMATISCH GRÜN – AUDIT-BEREIT** auf der getesteten Galaxy/API33-Baseline.

- Frische vollständige Unit-Suite: 380 Tests, 0 Failures, 0 Errors, 0 Skips.
  Darunter 14 P4-Domain-/Policytests und 14 P4-UI-/Modeltests. Letztere prüfen
  ausdrücklich, dass Result-Publication und automatisches Refresh die
  READY-Inaktivitätsfrist nicht durch Benutzeraktivitätsmeldungen verlängern.
- Fokussierte Galaxy M52 / Android 13 / API33-Instrumentation: 66 Tests,
  0 Failures, 0 Errors, 0 Skips: 13 `OperationsStorageTest`, 12
  `ActionLifecycleStorageTest`, 17 `PersonStorageTest`, 24 `LeadershipStorageTest`.
- P4: Vorlagen/eigene Rollen, lokale Eindeutigkeit, mehrere Planrollen/eine
  Hauptfunktion, Aktionsgrenzen inklusive zusammengesetzter DB-Fremdschlüssel,
  unreferenzierte Rollenlöschung, alle Statusgrenzen und operativen Snapshots.
  Atomarer Start und Commit-Rollback, Versionsmigration mit unveränderten
  sensiblen Ciphertexts, stale-state-Abweisung und Generation-/Publication-Gates.
  Motte/Meise mit getrennten Gewahrsamsorten und Support-Funktionswechsel geprüft.
- P2/P3a/P3b-Regression bestanden. Die historischen Migrationstests prüfen jetzt
  Schema 5; die P3b-v3-Fixture entfernt ausschließlich die zusätzlichen P4-Tabellen
  im eigenen synthetischen Testnamespace vor dem historischen Upgrade.
  Keine Abschwächung von Assertions oder Produkt-Crypto-Verträgen.
- App-Build, App-/Data-AndroidTest-Build, App/Data/Security-Lint, Architektur,
  Produktmanifest und `git diff --check` erfolgreich. Getrennte Gradle-Aufrufe;
  bekannter kombinierter Task-Wiring-Befund unangetastet.
- Schemavergleich 4/5: alle vier alten Tabellen einschließlich Indizes/Fields
  unverändert, ausschließlich vier zusätzliche P4-Tabellen und deren Indizes.
- Security-Diff: keine Änderungen an Crypto-/Sensitive-Guard-/Session-/DB-Key-
  Komponenten, B2Driver, Dependencies, Permissions oder Backup-/Transferregeln.
  Nur bestehende DI-Verdrahtung und die additive DB-/Action-Start-Erweiterung.

Ausschließlich synthetische Testdaten und RAM-Testkeys im separaten Data-Testpaket;
keine Produktdatenmutation, Credential-Änderung, manuellen Geheimnisse oder
invasiven Lifecycle-Tests. Tests decken Policies/Model und echte Storage-Verträge
ab; keine End-to-End-Touch-/IME-Instrumentation wird behauptet.

Ein kurzer späterer manueller UX-Smoke kann insbesondere schnelle Statusauswahl,
Scrollwege und Touch-Ziele beurteilen; er ist keine neue invasive Pflichtphase.

LAST SAFE CHECKPOINT: P4 implementiert und automatisch grün, Schema 5 additiv
exportiert; 380 Unit- und 66 fokussierte Galaxy-Tests bestanden. Abschließende
Builds/Lint/Architektur/Manifest und Security-Diff grün. Kein Commit/Push.
Die zehn bereits vor P4 vorhandenen leeren untracked Root-Dateien wurden
unverändert belassen und gehören nicht zum P4-Änderungsumfang.
