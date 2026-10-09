# P3a — Aktionsgebundene Personenbasis

## Scope und Regeln

P3a ergänzt die bestehende verschlüsselte Produktdatenbank um `Person` mit
`id`, `actionId`, `pseudonym`, `createdAt`. Keine weitere fachliche Entität,
sensible Zukunftsfelder, globale Personenkartei, Suche über Aktionen oder Files.

Das Pseudonym ist verpflichtend und darf nach Trim nicht leer sein. Der sichtbare
Eingabewert bleibt erhalten. Für die Eindeutigkeitsprüfung wird ausschließlich
`trim().lowercase(Locale.ROOT)` verwendet; keine automatische Generierung,
Unicode-Normalisierung, Akzententfernung oder Zusammenführung. Visuell ähnliche
Unicode-Zeichen bleiben unterschiedliche Pseudonyme. Identische Vergleichswerte
sind innerhalb derselben Aktion verboten, in verschiedenen Aktionen erlaubt.

| Aktionsstatus | Personen lesen | Anlegen / bearbeiten |
| --- | --- | --- |
| DRAFT | Nein | Nein |
| PREPARATION | Ja | Ja |
| ACTIVE | Ja | Nein |
| COMPLETED | Ja | Nein |
| DELETION_PENDING | Kein P3a-Zugriff | Nein |

Die gemeinsame pure-Kotlin-Regel `PersonRules` wird von Repository und UI verwendet.
Das Repository prüft den tatsächlich gespeicherten Aktionsstatus; ein alter
UI-Status ist keine Berechtigung. Jeder Personenzugriff benötigt eine `actionId`.
Eine Person wird niemals über eine andere Aktion geladen oder verändert.

## Speicherung und UI

Additive Migration 2 → 3 erzeugt ausschließlich `persons` mit fünf Spalten:
`id`, `actionId`, `pseudonym`, `pseudonymNormalized`, `createdAt`.
Fremdschlüssel zu `actions.id`, Index auf `actionId` und Unique-Index auf
`(actionId, pseudonymNormalized)`. Bestehende Aktionen und Abschlusszeitpunkte
bleiben erhalten. Migration 1 → 2 bleibt registriert; keine destruktive Migration.
Eine unerwartet bereits vorhandene Personen-Tabelle bei Schema 2 blockiert die
Migration, statt sie stillschweigend zu übernehmen. Inkonsistente gespeicherte
Pseudonym-/Vergleichswerte blockieren den Zugriff; Bearbeitung repariert sie nicht.
Die CASCADE-Klausel sieht nur die bereits spezifizierte spätere Aktionsgesamtlöschung
strukturell vor. P3a hat keinen Delete-Port, Button oder Swipe-Löschpfad.

`ProductPersonRepository` nutzt kurze Room-Instanzen mit dem bisherigen
Session-/Key-/SQLCipher-Vertrag. Lesen mit Statusprüfung erfolgt in einer
Lesetransaktion, Schreiben mit Status-/Zugehörigkeits-/Duplikatprüfung in einer
Schreibtransaktion. Der Unique-Index schützt die Eindeutigkeit zusätzlich.
Neue native Schritte, Commit und Publication bleiben gated; Cleanup bleibt unabhängig
von Widerruf möglich. Kein neuer Authdialog, Sessionparameter oder Schlüssel.

Im Aktionsdetail werden Personenanzahl, Pseudonyme und leerer Zustand angezeigt.
PREPARATION bietet „Person hinzufügen“ und Bearbeitung. Das Formular enthält genau
ein Feld „Pseudonym“, Speichern und Abbrechen. ACTIVE/COMPLETED zeigen die Daten
read-only. Keine Platzhalter für Name, Telefon, Notfallkontakte, Rollen oder Notizen.
Flüchtiger UI-State und Navigation werden bei Lock verworfen. Verzögerte
Personen-Leseergebnisse dürfen nicht in die Detailansicht einer anderen Aktion gelangen.

## Datenzugriff und Grenzen

[person-data-access.md](person-data-access.md) dokumentiert BASIC,
LEADERSHIP_SENSITIVE und PARTICIPANT_CONTROLLED. Nur BASIC wird in P3a verwendet,
ohne die SENSITIVE-Einordnung oder Verschlüsselung personenbezogener Daten zu ändern.
Keine Personenlöschung, konkreten neuen Retentionfristen oder Echtdatenfreigabe.
P3b benötigt vor sensiblen Feldern eine ausdrückliche Security-/Step-up-Entscheidung.

## Automatisierte Prüfungen

Frische vollständige Unit-Suite: **328 Tests**, 0 Failures, 0 Errors, 0 Skips.
Davon 12 neue P3a-Tests: 5 Domain-/Regeltests und 7 ViewModel-Tests.

Fokussierte Instrumentation auf Samsung Galaxy M52 / Android 13 / API33:
**42 Tests**, 0 Failures, 0 Errors, 0 Skips:

- `PersonStorageTest`: 17 P3a-Tests.
- `ProductActionsTest`: 13 bestehende P1-Tests.
- `ActionLifecycleStorageTest`: 12 bestehende P2-Tests.

App-Build, App-/Data-AndroidTest-Build, Lint für App/Data/Security,
Architekturprüfung, Produktmanifestprüfung und `git diff --check` erfolgreich.
Die Gradle-Prüfungen liefen getrennt; der bekannte kombinierte Task-Wiring-Befund
wurde nicht verändert. Produktmanifest und Backup-/Transfer-Ausschlüsse sind
unverändert; keine neuen Permissions oder Dependencies.

Tests umfassen Domainnormalisierung
und Statusregeln, ViewModel-Liste/Abbruch/read-only/Lock/Navigation sowie echte
Room-/SQLCipher-Migration, Zugehörigkeit, Duplikate, Fremdschlüssel, Commit-Widerruf,
Publication-Widerruf und synthetische Klartextmarkerprüfung im Testnamespace.
Instrumentation verwendet das getrennte data-Testpaket und flüchtige injizierte
synthetische Schlüssel; keine DEVICE_CREDENTIAL-Dialoge oder Produktdatenmutation.
Dies ist kein erneuter Keystore- oder invasiver Nachweis.

Der automatisierte Stand ist grün. Ein manueller Smoke ist für die geprüften
Storage-/Domain-Verträge nicht erforderlich; eine kurze optionale UX-Prüfung kann
später die Darstellung und Bedienung am Gerät ergänzen. Die Klartextmarkerprüfung
bezieht sich nur auf synthetische Artefakte im Testnamespace, nicht auf RAM-/Flash-
Forensik oder einen vollständigen Scan des Geräts.
