# P3b – sensible aktionsgebundene Personendaten

Status: implementiert; automatischer Abschluss im getesteten Galaxy/API33-Umfang bestanden.

## Produktumfang

BASIC bleibt ausschließlich das Pseudonym innerhalb der normalen PaG-Sitzung.
LEADERSHIP_SENSITIVE ergänzt drei unabhängig angezeigte Bereiche:

| Bereich | Optionale Daten |
| --- | --- |
| Identität | Vollständiger Name, Geburtsdatum |
| Eigener Kontakt | Telefonnummer |
| Notfallkontakt | Name/Pseudonym, Telefonnummer, Beziehung |

Keine Pflichtangaben, freien sensiblen Notizen, Kontakteintegration, Files,
Teilnehmer-PIN oder globale Personenkartei. Keine zusätzlichen Zukunftsspalten.
Personengebundene Repositoryzugriffe benötigen Aktion und Person. Persistierter Status
und Zugehörigkeit werden in kontrollierten Datenbankoperationen geprüft.

### P3b-META: aktionsweiter Leitungsschutzstatus

`LeadershipRepository.actionState(actionId, publish)` liefert ausschließlich
`SensitiveResult<SensitiveState>`, ohne Personvoraussetzung. Es dient unter anderem
der frühen Statusanzeige in PREPARATION; dieser Slice implementiert keine UX2-UI.
UNINITIALIZED, READY und PARTIAL_OR_INVALID verwenden dieselbe bestehende
strukturelle Klassifizierung wie das personengebundene Inventory. Sie wurde ohne
Verhaltensänderung nach `LeadershipMetadata` extrahiert. Ein fehlender oder nicht
lesbarer Aktionsbestand wird nicht als UNINITIALIZED ausgegeben.

Der Pfad verwendet READY, Generation, Aktionsbindung, kurze Read-Transaktion,
DB-Key-Owner und die bestehenden Storage-/Publication-Gates. Das vorhandene
atomare Requestticket verhindert Ergebnisse über Repository-Revoke hinweg,
einschließlich einer Prüfung innerhalb der generationgebundenen Publication.
Unbekannte Provider verweigern die neue API mit UNAVAILABLE.

Das bestehende Setup kann bereits ohne Personen provisionieren. Auch ein solcher
Bestand wird READY erkannt. Es werden keine Personen/Pseudonyme gelesen und
keine Bereiche, Payloads, Passphrases, Keys oder Grants an den Caller gegeben.
Intern liest die unveränderte Klassifizierung Root-/Area-Metadaten einschließlich
verschlüsselter Blobs zur Struktur-/Längenprüfung und nutzt die bestehenden
Key-Präsenz-/Nutzbarkeitsprobes. Keine sensitive Entschlüsselung, neue Keystore-
Operation, Grantanlage oder Key-Admission; der normale verschlüsselte DB-Read
benötigt weiterhin den bestehenden DB-Key-Owner.

Regressionen: `LeadershipMetadataTest` prüft die gemeinsame Klassifizierung;
`LeadershipStorageTest` prüft null Personen vor/nach echtem bestehendem Setup,
alle drei Zustände, Konsistenz mit Inventory, fehlende/fremde Aktion,
Generation-/Lock-/Revoke-Gates und verzögerte Publication. Der vorhandene echte
Coordinator-/Repositorytest prüft zusätzlich Statuszugriff und Revoke.

Nachweis auf Baseline `6a94ac1`: 517 Unit-Tests grün (darunter sieben neue
Klassifizierungs-/API-Fälle), App- und beide AndroidTest-Builds, Architektur und
Merged-Manifest-/Permissionsprüfung erfolgreich. Galaxy SM-M526BR/API33:
vollständige LeadershipStorage-Klasse mit 35 Fällen grün; nach finalem
Publicationguard und zusätzlicher Grenzregression gezielt elf Metadatenfälle plus
echter Coordinator-/Repositoryfall auf finaler APK grün (12 Tests, 51,432 s).
Logs: `.prototype-cache/p3b-meta-checks.log`, `p3b-meta-galaxy.log`,
`p3b-meta-final-androidtest.log` und `p3b-meta-galaxy-final.log`.
Keine Credentialänderung, kein pm clear/Uninstall; ausschließlich install -r
der isolierten Test-APK. Session/StorageAccess/LeadershipGrant/Key-Admission,
Crypto-/Keystore-Code, Schema, Dependencies und Manifest unverändert.

## Zugang und Zustände

Explizite Einrichtung nur in PREPARATION, mit zweimal identischer Leitungs-Passphrase.
16–128 Unicode-Codepoints, Leerzeichen erlaubt, keine Normalisierung oder Trim,
keine erzwungenen Zeichenklassen oder zusätzliche Zusammensetzungsregel.
Mindestlänge ist keine Entropiegarantie; ein starkes Geheimnis ist erforderlich.
Genau ein Leitungszugang pro Aktion. Keine Speicherung von Geheimnis oder Hint.

UNINITIALIZED bedeutet vollständig leerer sensibler Zustand einschließlich Alias.
READY bezeichnet das konsistente Envelope-/Keystore-Inventar, nicht Klartextfreigabe.
PARTIAL_OR_INVALID blockiert ausschließlich sensible Daten dieser Aktion.
Keine automatische Reparatur, Ersatzinitialisierung oder Aliasersetzung.
Bereits bewusst begonnenes Setup kann bei Prozess-/Sessionverlust partiell bleiben;
dies ist fail closed und wird nicht automatisch fortgesetzt.

Die Passphrase schützt über Argon2id einen zufälligen Aktions-Root-Key.
Eine zusätzliche nicht exportierbare Keystore-AES-Schicht bindet dessen Envelope
an das Gerät, ohne weitere DEVICE_CREDENTIAL-Anforderung. Zufällige DEKs je
Person/Bereich schützen strukturierte AES-GCM-Payloads. Details und Bibliotheks-
/Lizenzbegründung stehen im [Security-Design](product-p3b-security-design.md).

## Freigabe und Produkt-UI

Die normale Personansicht zeigt nur BASIC und Bereichsbuttons für vorhandene Daten.
In PREPARATION kann jeder noch leere Bereich ausdrücklich erfasst werden.
Ein Klick verlangt die Leitungs-Passphrase und öffnet genau einen Bereich.
Kein vollständiges Profil-Unlock. Geburtsdatum über Kalender, sämtliche Felder optional.
Speichern eines vollständig leeren Bereichs hinterlässt keine Bereichsrow.

PREPARATION erlaubt Schreiben. ACTIVE und COMPLETED erlauben nur Lesen vorhandener
Bereiche nach Step-up. DRAFT und DELETION_PENDING bieten keinen P3b-Zugriffspfad.

Ein Grant bindet Aktion, Person, Bereich und Sessiongeneration. Maximal 60 Sekunden,
nicht durch Lesen oder Scrollen verlängerbar. Die normalen 3 Minuten absolut und
30 Sekunden READY-Inaktivität bleiben früher wirksame Grenzen. Navigation,
Personen-/Bereichswechsel, Background, LOCKED/BLOCKED, Generationwechsel und
Prozessverlust widerrufen. Vor neuer DB-Arbeit, Commit und Ergebnisanzeige werden
die Grenzen erneut geprüft. Room wird weiterhin kurz geöffnet und geschlossen.

Root/KDF-/temporäre Bytes werden bestmöglich genullt; nur der ausgewählte Bereichs-DEK
lebt kurz im instanzlokalen Grant. Abgebrochene/abgewiesene Arbeitsaufträge nullen
auch noch ausstehende app-kontrollierte Passphrase-Puffer. Sensitive Formwerte bleiben
flüchtig, ohne SavedState/Prefs/DataStore; Autofill und Content Capture werden für den
betroffenen Fensterbaum deaktiviert. Kein Schutzversprechen gegenüber IME oder OS.

## Verlust, Löschung und Grenzen

Vergessene Passphrase oder verlorener Geräte-Key: sensible Daten dauerhaft verloren,
BASIC bleibt davon unabhängig. Kein Gerätecode-/Master-/Entwickler-/Cloud-Recovery.
Falsches Geheimnis erzeugt keine Mutation und keine sensible Freigabe.

Backend unterstützt logische Bereichs- und Personenlöschung sowie aktionsweites
Sensitive-Delete unabhängig von BASIC. Noch kein Retentionzeitplan oder Lösch-UI;
keine neue Frist. Aktionsweite Löschung markiert erst DELETE_PENDING, widerruft
Grants, entfernt den eigenen Keystore-Key und schließlich Envelope/Payloads.
Eine unterbrochene bereits autorisierte Löschung ist ausdrücklich fortsetzbar.
Einzelne Row-Löschung garantiert kein Crypto-Shredding alter DB-/WAL-/Flashreste.
Auch Keystore-Keylöschung beweist keine physische Überschreibung.

Kein Schutz gegen kompromittiertes OS, RAM-/Flash-Forensik oder Laufzeit-Keylogging.
Keine allgemeine Samsung-/Android-/StrongBox-Garantie. Keine neue invasive Phase.
Mehrere unabhängige Leitungs-Envelopes und PARTICIPANT_CONTROLLED benötigen später
separate Entscheidungen; v1 implementiert sie nicht. Retention-Produktworkflow und
Echtdatenfreigabe bleiben offen.

## Automatische Evidenz

- Vor dem aktionsweiten Guard-Fix: 343 Unit-Tests, 0 Failures, 0 Errors, 0 Skips.
  Einschließlich 4 Crypto-/KDF-, 5 Domain/Grant/Codec- und 6 UI-Zustandstests.
- Galaxy M52 / Android 13 / API33: fokussierter Produktlauf mit 59 bestandenen Tests
  (16 P3b-Storage, 1 KDF-Benchmark, 17 P3a, 13 P1, 12 P2).
- Nach abschließendem Cleanup-Fix: 18 P3b-Storage-Vertragstests erneut geprüft,
  einschließlich queued cancellation und rejected executor: 18 bestanden, keine Failures.
  Die abschließende Encoder-Puffernullung wurde zusätzlich durch die frische Unit-Suite
  (Codec-Roundtrip-/Fehlerpfade) und erneute Builds geprüft.
- KDF: Argon2id v19 / 64 MiB / t3 / p1 / 32 Byte, Median 693 ms im initialen
  Benchmark, 691 ms im fokussierten Wiederholungslauf. Keine Secret-Ausgabe.
- Echter Test-Keystore-Key: nicht exportierbar, `authRequired=false`,
  `securityLevel=1` (TEE) auf diesem Galaxy. Kein manueller Authdialog.
- Manipulation, falsche Passphrase, Owner-/Bereichs-AAD, persistierter Status,
  Teilzustände, fehlender Key, Migration 3→4 mit BASIC-Erhalt, Read-only-Regeln,
  Ablauf/Navigation/Generation, Commit-Rollback und verspätete Publication geprüft.
- Additives Schema 4 exportiert; Aktionstabelle und BASIC-Personenfelder unverändert.
- App-/AndroidTest-Build, Lint (App/Data/Security), Architektur und Produktmanifest
  getrennt geprüft. Bekanntes kombiniertes Gradle-Wiring unverändert.

Alle Geräteprüfungen liefen in der separaten synthetischen Data-Test-App, ohne
Produktdatenmutation, pm clear, Uninstall, Credential-Änderung oder manuelles Geheimnis.
Ein manueller Test ist für den Securityvertrag nicht erforderlich; optional kann
später die Ergonomie der Eingabe geprüft werden. Keine manuelle Testpflicht hier.

Vorheriger Prüfstand: P3b-Verträge automatisch bestätigt; 343 Unit-Tests,
59 fokussierte Produkt-Storage-Tests und 18 P3b-Cleanup-Bestätigungstests bestanden.
Abschließende Builds/Lint/Architektur/Manifest und git diff --check erfolgreich.
Kein weiterer manueller oder invasiver Geräteschritt erforderlich. Kein Commit/Push.

## Aktionsweiter autoritativer Sensitive-Guard

`PARTIAL_OR_INVALID` sperrt alle normalen LEADERSHIP_SENSITIVE-Zugriffe der
Aktion, auch wenn der ausgewählte Bereich strukturell gültig ist. BASIC bleibt
verfügbar. Der gemeinsame Guard bewertet Envelope, Geräte-Key und sämtliche
Bereichs-Rows in der aktuellen kontrollierten DB-Transaktion. Ein Grant ersetzt
diese Prüfung niemals. Vor Grant-Erteilung wird nach KDF/Entschlüsselung erneut
geprüft; Schreiben prüft zusätzlich Version, Revision und den verschlüsselten
Snapshot. Unbekannte Versionen werden nicht durch Überschreiben repariert.

Die gemeinsame Crypto-Version bindet das Envelope-/Payload-Protokoll; die
verschlüsselte Payload trägt zusätzlich ihre Codec-Version. Normale Operationen
provisionieren `UNINITIALIZED` nicht automatisch. Explizite Einrichtung darf nur
die eigene unveränderte Reservierung konsistent abschließen.

Normale Bereichs-/Personenlöschung und Beginn einer Aktionslöschung benötigen
READY. Die ausdrücklich aufgerufene Fortsetzung eines bereits autorisierten
`DELETE_PENDING`-Löschauftrags bleibt ein separater Cleanup-Pfad; sie darf auch
bei inzwischen fehlendem Geräte-Key abschließen und gibt keine Daten frei.

## Passphrase- und Formularbedienung

Die Einrichtung empfiehlt eine lange, nur für diese Aktion verwendete Passphrase
aus mehreren nicht naheliegenden Wörtern. 16 Zeichen sind eine technische
Mindestlänge, keine Entropiegarantie. Der Einrichtungszähler verwendet dieselbe
Unicode-Codepoint-Zählung wie die Validierung. Beim späteren Step-up fehlt er.

Die Anzeige bleibt standardmäßig maskiert und wird nur beim Gedrückthalten des
Icons sichtbar. Loslassen, Verlassen des Icons, Gesture-Abbruch, Fokusverlust,
Navigation, Fenster-/Foregroundverlust und Disposal maskieren wieder. Der
Zustand ist ausschließlich flüchtiger Compose-State. Strings sind nicht
garantiert physisch nullbar. Formularnavigation, Speichern, Abbrechen und IME
Done entfernen Fokus und schließen die Tastatur; Feldwechsel hält sie offen.
Unverbrauchte Taps außerhalb schließen sie ebenfalls, ohne Buttons oder Scrollen
zu konsumieren.

Die neuen lokalen UI-Tests prüfen Reveal-/Fokus-Policies und Unicode-Grenzen.
Sie ersetzen keine End-to-End-Prüfung tatsächlicher Compose-Touch-/IME-Ereignisse;
dafür besteht aktuell keine UI-Instrumentation-Infrastruktur. Neue Storage-Tests
prüfen aktionsweite Inkonsistenz, Änderungen nach Grant-Erteilung, Versionsschutz,
BASIC-Verfügbarkeit und den getrennten Cleanup-Vertrag.

## Abschluss nach Security-Fix und Formular-UX

- Frische vollständige Unit-Suite: 352 Tests, 0 Failures, 0 Errors, 0 Skips.
  Enthalten: 24 fokussierte P3b-Unit-Tests (4 Crypto, 5 Domain/Grant/Codec,
  6 ViewModel, 9 Formular-/Reveal-Policies).
- Galaxy/API33: alle 24 `LeadershipStorageTest`-Fälle bestanden, einschließlich
  sechs neuer aktionsweiter PARTIAL-/Versions-/Grant-/Cleanup-Regressionsfälle.
  Keine Failures oder ausgelassenen Fälle; ausschließlich separates Data-Testpaket.
- App-Build, App-AndroidTest-Build, Data-AndroidTest-Build, App/Data/Security-Lint,
  Architektur und Produktmanifest jeweils erfolgreich, getrennte Gradle-Aufrufe.
- Merged Manifest: keine zusätzliche Produktpermission oder exportierte Komponente;
  kein Netzwerk-/Kontakte-/Telefon-/SMS-/Location-Zugriff. Backup-/Transfer-Ausschlüsse
  unverändert. Keine neue Dependency für die Formular-UX.
- Mini-Audit: aktueller aktionsweiter READY-Guard vor normalen sensiblen Operationen,
  erneute Prüfung vor Grant-Erteilung und in Schreibtransaktionen; unbekannte
  Versionen und veränderte verschlüsselte Snapshots werden nicht überschrieben.
  BASIC bleibt verfügbar, expliziter DELETE_PENDING-Cleanup separat.
- AND-Komposition, Argon2id-Parameter, Tink/AAD, Keystore-Vertrag und normale
  Session-/DB-Key-Komponenten unverändert durch diesen Fix. Kein manueller Smoke.

LAST SAFE CHECKPOINT: aktionsweiter Sensitive-Fix und Formular-UX abgeschlossen;
352 Unit-Tests und 24 P3b-Galaxy-Tests bestanden. Builds/Lint/Architektur/Manifest
und abschließender Mini-Audit grün. P3b commit-ready; kein Commit/Push ausgeführt.
