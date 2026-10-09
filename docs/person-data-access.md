# Personen: Daten- und Zugriffsmatrix

## BASIC: verbindlicher P3a-Umfang

Eine Person gehört ausschließlich zu genau einer Aktion. P3a speichert nur die
technischen IDs, das verpflichtende Pseudonym und den Erstellungszeitpunkt.
`pseudonymNormalized` ist ausschließlich ein technischer Vergleichswert für die
aktionsgebundene Eindeutigkeit. Kein Name, Kontaktfeld oder Platzhalter für spätere
sensible Daten wird angelegt.

Die nachfolgenden Zugriffsklassen sind fachliche Kategorien; sie legen für sich
allein keine Schlüsselanzahl oder Authentifizierungszustände fest. P3b verwendet
für LEADERSHIP_SENSITIVE den separat freigegebenen kryptografischen Vertrag. Sie ersetzen nicht die
Sensitivitätsklassen aus privacy.md: Pseudonyme und Aktionszugehörigkeit bleiben
personenbezogen, standardmäßig SENSITIVE und verschlüsselt. BASIC bedeutet keine
öffentliche oder ungeschützte Freigabe.

| Zugriffsklasse | Bedeutung | Umsetzung |
| --- | --- | --- |
| BASIC | Operative Identität innerhalb der zulässigen PaG-Sitzung | Nur Pseudonym in P3a |
| LEADERSHIP_SENSITIVE | Gesondert zugängliche sensible Identitäts-/Kontaktdaten | P3b: zusätzlich verschlüsselte Bereichspayloads mit Leitungs-Passphrase und Gerätebindung |
| PARTICIPANT_CONTROLLED | Daten, welche die Leitung ohne die betreffende Person grundsätzlich nicht lesen können soll | Aktuell kein konkreter Datentyp; keine Teilnehmer-PIN |

## Erfasste und ausdrücklich nicht erfasste Daten

| Datentyp | Zugriffsklasse / Planung | Stand |
| --- | --- | --- |
| Pseudonym | BASIC | In P3a implementiert |
| Vollständiger Name | LEADERSHIP_SENSITIVE | In P3b optional implementiert |
| Geburtsdatum | LEADERSHIP_SENSITIVE | In P3b optional implementiert |
| Eigene Telefonnummer | LEADERSHIP_SENSITIVE | In P3b optional implementiert |
| Notfallkontakt: Name/Pseudonym, Telefonnummer, Beziehung | LEADERSHIP_SENSITIVE | In P3b optional implementiert |
| Zweite Kontaktperson | Ggf. LEADERSHIP_SENSITIVE, Bedarf offen | Nicht implementiert |
| Individueller Rechtskontakt | Ggf. LEADERSHIP_SENSITIVE, Bedarf offen | Nicht implementiert |
| Bevorzugte Supportsprache | Ggf. LEADERSHIP_SENSITIVE, Bedarf offen | Nicht implementiert |
| Zeitkritischer Unterstützungsbedarf | Ggf. LEADERSHIP_SENSITIVE, Bedarf offen | Nicht implementiert |
| Gesundheitsinformationen | Besonders sensibel; nur bei später konkret notwendigem Use Case | Keine Produktfelder, freie Krankengeschichte oder freie sensible Notizen |
| PARTICIPANT_CONTROLLED-Daten | Noch kein konkreter Typ definiert | Keine Umsetzung / Teilnehmer-PIN |

P3a erfasst außerdem keine Adresse, E-Mail, Fotos, Ausweisdokumente/-nummern,
Standorte, Kontaktbuchdaten, Nationalität, Aufenthaltsstatus, medizinischen Details,
freien allgemeinen Notizen, Rollen oder operativen Support-/Gewahrsamsstatus.
Keine Kontakte-, Telefon-, SMS-, Netzwerk- oder sonstige neue Android-Permission.

## Zugriffsregeln und Speicherort

Nur PREPARATION erlaubt Anlegen und Pseudonymänderung. PREPARATION, ACTIVE und
COMPLETED erlauben aktionsgebundene Lesezugriffe innerhalb READY. ACTIVE und
COMPLETED sind read-only; DRAFT und DELETION_PENDING bieten in P3a keinen Zugriff.
Keine globale Liste, aktionsübergreifende Suche oder automatische Zusammenführung.

Alle BASIC-Felder und Indizes liegen in der bestehenden SQLCipher-Produktdatenbank unter
`noBackupFilesDir/pag-db-v1`. Die generationsgebundene Session und der bestehende
DB-Key-Vertrag bleiben autoritativ. UI-Inhalte und Eingaben bleiben flüchtig,
werden bei Lock freigegeben und nicht in SavedState, Prefs oder DataStore gesichert.
Keine Protokollierung von Pseudonymen oder personenbezogenen IDs.

## Aufbewahrung und spätere Löschung

Personendaten werden künftig nicht zwingend dieselbe Aufbewahrungsdauer haben:
BASIC/operative Identität und sensible Identitäts-/Notfalldaten werden getrennt
betrachtet. LEADERSHIP_SENSITIVE soll später separat und ggf. früher löschbar sein
als die übrige Aktionshistorie. P3a legt keine konkrete neue Frist fest und ersetzt
keine bestehende Retentionregel. Retention und Personenlöschung sind noch nicht
implementiert; deshalb besteht noch keine Freigabe für Echtdatenbetrieb.

Die technische Fremdschlüssel-Kaskade ist mit der bestehenden fachlichen
Aktionsgesamtlöschung vereinbar. Sie ist kein Benutzer-Löschpfad, keine
Retentionimplementierung und kein Nachweis physischer Flash-Löschung.

## P3b: getrennte sensible Bereiche

P3b ergänzt ausschließlich die optionalen Bereiche IDENTITY (vollständiger Name,
Geburtsdatum), CONTACT (eigene Telefonnummer) und EMERGENCY_CONTACT
(Name/Pseudonym, Telefonnummer, Beziehung). Sie liegen als zusätzlich
AES-GCM-verschlüsselte Payloads in der SQLCipher-DB, nicht als normal lesbare
Room-Felder. Ein starkes Leitungsgeheimnis pro Aktion und ein gerätegebundenes
Keystore-Envelope schützen den separaten Aktions-Root-Key.

BASIC-READY allein genügt nicht. Die bereichsbezogene Freigabe bindet Aktion,
Person und Sessiongeneration und endet spätestens nach 60 Sekunden, Navigation
oder einer normalen Session-Sperrgrenze. PREPARATION erlaubt Schreiben;
ACTIVE/COMPLETED nur Lesen vorhandener Bereiche. Teilfehler blockieren nur das
sensible Subsystem. Keine Teilnehmer-PIN, kein Recovery und keine weiteren Felder.

Logische sensible Löschoperationen sind unabhängig von BASIC technisch möglich;
ein automatischer Retentionzeitplan und dessen Benutzeroberfläche bleiben offen.
Keine neue Frist. Row-Löschung garantiert weder individuelles Crypto-Shredding noch
Flashüberschreibung. Aktionsweites Entfernen des Geräte-Keys entzieht dessen
Keystore-Nutzbarkeit, ohne physische Löschung zu beweisen. Maßgeblich sind
[Security-Design](product-p3b-security-design.md) und [P3b-Stand](product-p3b.md).
