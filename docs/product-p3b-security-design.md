# P3b Security-/Architecture-Entscheidung

Status: Variante B vom Benutzer freigegeben und als zusätzlicher DB-only-Schutz
implementiert. BASIC und die normale PaG-Session bleiben unverändert.

## Entscheidung und Threat Model

RECOMMENDED / gewählt: starkes, eigenes Leitungsgeheimnis pro Aktion mit Argon2id,
zufälligem Aktions-Root-Key und gerätegebundenem Android-Keystore-Envelope.
ALTERNATIVE: erneutes DEVICE_CREDENTIAL (A) wäre einfacher, schützt jedoch nicht
bei bekanntem Gerätecode. Kombination C würde beide Benutzerfaktoren verlangen
und den Aufwand im Ernstfall erhöhen. Sie wird nicht implementiert.
NOT RECOMMENDED: kurze numerische PIN, Passphrase als Datenschlüssel,
Teilnehmer-PIN als alleiniger Zugang oder normale zusätzliche Klartextspalten.

Ziel ist Schutz sensibler Bereiche bei kurzfristigem Zugriff auf eine gültige
BASIC-Sitzung. Ein bekannter Gerätecode allein öffnet diese Bereiche nicht.
Die Leitung benötigt keine Mitwirkung der betreffenden Person.
Kein Schutzversprechen gegen kompromittiertes OS, laufzeitfähigen Angreifer,
Keylogging, erzwungene Geheimnispreisgabe oder Zugriff auf bereits angezeigte Daten.
Die App bleibt auffindbar. Keine Täuschungs-, Duress- oder Selbstzerstörungsmodi.

## Bibliotheken und feste Parameter

Bouncy Castle `bcprov-jdk18on:1.86` liefert die etablierte Java-Argon2id-Implementierung
als Lightweight API, ohne Registrierung eines Security-Providers. Die gepflegte
Bibliothek steht unter der MIT-artigen Bouncy-Castle-Lizenz:
[Downloads](https://www.bouncycastle.org/download/bouncy-castle-java/),
[Lizenz](https://www.bouncycastle.org/licence.html),
[Argon2-Implementierung](https://github.com/bcgit/bc-java/blob/main/core/src/main/java/org/bouncycastle/crypto/generators/Argon2BytesGenerator.java).
Die bereits vorhandene Tink-Abhängigkeit liefert `AesGcmJce` für AES-256-GCM.
Android Keystore verwendet die Plattform-AES-GCM-Implementierung.
Keine eigene KDF, HKDF, Cipher oder Netzwerkfunktion. Keine Provider-Ersetzung.

Argon2id v19: 65536 KiB, 3 Iterationen, Parallelism 1, zufälliger 16-Byte-Salt,
32-Byte-Ausgabe. Algorithmus, Version, Salt und Kosten stehen im Envelope.
Unbekannte oder abweichende Profile werden abgewiesen; keine stillen Downgrades.
Galaxy M52/API33: nach Warm-up fünf Ableitungen 665/647/747/704/693 ms,
Median 693 ms. Synthetische Testeingabe, keine Ausgabe von Eingabe oder Ergebnis.
Das ist eine Gerätebeobachtung, kein allgemeines Leistungsversprechen.

## Schlüssel und Speicherung

Pro Aktion existiert genau ein zufälliger 32-Byte-Root-Key. Dieser ist unabhängig
vom Produkt-DB-Key. Verschachtelte Envelopes:

1. Argon2id-Ausgabe schützt den Root-Key mit Tink-AES-GCM.
2. Ein eigener nicht exportierbarer Keystore-AES-256-GCM-Key schützt dieses Envelope.

Keystore-Alias: `org.pag.product.leadership.aes.v1.<actionId>`.
Keine Benutzer-Authentifizierung am zusätzlichen AES-Key: Gerätebindung ist kein
zweiter Benutzerfaktor und verlangt keinen DEVICE_CREDENTIAL-Dialog.
Keystore kann hardwaregestützt sein; keine StrongBox- oder Hardware-Garantie.
Extern kopierte DB plus Passphrase reicht ohne diese Gerätebindung nicht aus.
Ein Angreifer, der den Keystore unter der App-Sicherheitsdomäne benutzen kann,
kann diese Zusatzschicht umgehen; dann bleiben Passphrase und Argon2id maßgeblich.

Je Person/Bereich wird ein zufälliger 32-Byte-DEK verwendet und unter dem
Aktions-Root-Key AES-GCM-geschützt. Keine selbst entworfene Schlüsselableitung.
Dies vermeidet komplexe langlebige Schlüsselverwaltung und erlaubt nur den gerade
angeforderten DEK im kurzen Grant zu behalten. Ein Installations-Root würde den
Blast Radius und getrennte Retention verschlechtern; ein Root pro Person würde
unnötig viele Zugangs-Envelopes verlangen. Der Aktions-Root ist ein bewusster
Kompromiss. Der Code implementiert aktuell genau ein Zugangs-Envelope pro Aktion.
Spätere unabhängige Leadership-Envelopes können denselben Root schützen, brauchen
aber ein eigenes Design und eine additive Migration.

Schema 4 ergänzt `leadership` (Envelope/Status/KDF-Metadaten) und
`sensitive_person_areas` (aktions-/personengebundene verschlüsselte Bereichspayloads).
Keine sensiblen Klartextspalten. Bestehende Aktionen und BASIC-Personen bleiben
unverändert; zusätzlicher technischer Personenindex ermöglicht den zusammengesetzten
Foreign Key. Migration 3 → 4 ist additiv, registriert und ohne destructive fallback.

AAD bindet Root an Aktion, Envelope-Epoche, Crypto-Version und KDF-Metadaten.
DEK-Envelope bindet Aktion, Person, Bereich, Version und Epoche. Payload-AAD bindet
zusätzlich die Revision. Payload-Schema ist versioniert und beim Decode begrenzt;
übrige Bytes, unbekannte Versionen und manipulierte Ciphertexts werden abgewiesen.

## Passphrase und Provisionierung

16–128 Unicode-Codepoints, Leerzeichen erlaubt, nicht trimmen oder normalisieren,
exakte zweimalige Eingabe. Keine erzwungenen Zeichenklassen oder zusätzliche Zusammensetzungsregel.
Die Mindestlänge allein garantiert keine hohe Entropie; ein starkes Geheimnis ist erforderlich.
Keine Speicherung von Passphrase, Hint, Hash oder Recovery-Geheimnis.

Nur bewusste Einrichtung in PREPARATION: reserviertes Envelope persistieren,
KDF/Root/Keystore-Schicht erzeugen, vollständiges Envelope persistieren, dann READY.
Bei Teilfehler bleibt RESERVED bzw. PARTIAL_OR_INVALID. Keine Ersatzinitialisierung,
Aliasersetzung, automatische Löschung oder Reparatur. READY ist struktureller Status,
kein bereits freigegebener sensibler Schlüssel. Vollständige AEAD-Prüfung erfolgt
mit dem expliziten Passphrase-Zugriff.

UNINITIALIZED: weder Envelope noch Bereichsrows noch zugehöriger Alias.
READY: bekannte, konsistente Struktur und nutzbarer erwarteter Keystore-Key.
PARTIAL_OR_INVALID: fehlende Seite, reservierter/inkonsistenter Zustand,
unbekannte Metadaten oder unbrauchbarer Keystore-Key. BASIC bleibt nutzbar.

## Bereiche, Session und RAM

IDENTITY: optional vollständiger Name und Geburtsdatum.
CONTACT: optional eigene Telefonnummer.
EMERGENCY_CONTACT: optional Name/Pseudonym, Telefonnummer und Beziehung.
Keine weiteren Felder, freien sensiblen Notizen oder participant-controlled Daten.

Jede Freigabe bindet Aktion, Person, Bereich und zentrale Sessiongeneration.
Normale READY-Session bleibt Voraussetzung. Root, KDF-Ausgabe und temporäre
Payloadbytes werden im kontrollierten `finally` bestmöglich genullt. Im Grant bleibt
nur der ausgewählte Bereichs-DEK, niemals ein globaler Root oder Unlock-Flag.

Harte 60 Sekunden ab Grant-Erzeugung, keine Verlängerung durch Lesen/Scrollen.
Frühere normale 3-Minuten-/30-Sekunden-Grenzen bleiben autoritativ.
Navigation, Bereichs-/Personenwechsel, Background, LOCKED/BLOCKED, Generationwechsel
und Prozessverlust widerrufen. Datenpublikation prüft beide Grenzen erneut;
auch vor neuen nativen DB-Schritten und Commit gilt das kombinierte Gate.
Cleanup darf unabhängig vom Gate schließen und kontrollierte Kopien nullen.
UI-Eingaben und entschlüsselte Strings sind flüchtig; für JVM-/Bibliothekskopien,
Strings, OS-/IME-Reste oder physische RAM-Löschung gibt es keine Garantie.

## Zugriffe und Verlust

PREPARATION: Einrichtung, bereichsweises Lesen/Schreiben nach Passphrase.
ACTIVE/COMPLETED: nur Lesen vorhandener Bereiche nach Step-up.
DRAFT/DELETION_PENDING: kein neuer sensibler Zugriffspfad.
Repository prüft persistierten Status und Zugehörigkeit in kontrollierter DB-Arbeit.
Falsche Passphrase: allgemeiner Fehler, keine Mutation, keine BASIC-Sperre.
App-Retry-Limits sind keine Sicherheitsgrenze; v1 behauptet keine solche Grenze.

Vergessenes Leitungsgeheimnis oder verlorener Geräte-Key: sensible Daten nicht
wiederherstellbar. Kein Gerätecode-, Entwickler-, Cloud- oder Master-Recovery.
Credential-Änderungen erhalten keine zusätzliche P3b-Garantie; der nicht auth-bound
Key hat eine andere Policy als P1. Fehlender/unbrauchbarer Key blockiert nur sensible
Daten. Keine neue invasive Credential-Matrix wurde ausgeführt.

## Löschung und Retention

Backend erlaubt autorisierte logische Bereichs-/Personenlöschung in PREPARATION.
BASIC bleibt erhalten. Keine neuen Fristen oder automatischen Retentionjobs.
Aktionsweites Sensitive-Delete verifiziert zuerst das Geheimnis, markiert persistent
DELETE_PENDING, widerruft Grants, löscht den ausschließlich dieser Aktion zugehörigen
Keystore-Key und entfernt Envelope/Rows. Ein unterbrochener, bereits autorisierter
DELETE_PENDING kann ausdrücklich fortgesetzt werden; keine automatische Reparatur.

Einzelne Row-/DEK-Envelope-Löschung garantiert kein Crypto-Shredding: SQLite-/WAL-/
Flash-Reste können alte Envelopes enthalten. Aktionsweite Keylöschung entzieht die
implementierte Keystore-Nutzbarkeit, aber beweist weder physische Keyüberschreibung
noch Flashlöschung. Kopien aus zuvor kompromittierter Laufzeit sind außerhalb des
Vertrags. Bei später mehreren Root-Envelopes müssten alle nutzbaren Kopien betrachtet
werden. Ein eigenständiger Retention-UI-/Zeitplan-Slice bleibt offen.

## UX, Tests und unveränderte Komponenten

Normale Personansicht zeigt BASIC. Explizite Bereichsbuttons öffnen nur einen Bereich;
PREPARATION bietet zusätzlich dessen Erfassung. Alle Felder optional, Geburtsdatum
über Kalender, kein Kontaktezugriff. Sensitive Eingaben deaktivieren Autofill und
Content Capture im betroffenen Fensterbaum, ohne IME-/OS-Schutz zu versprechen.
Keine Speicherung in SavedState/Prefs/DataStore und keine inhaltlichen Logs.

Automatische Tests prüfen KDF/AEAD, Bindungen/Manipulation, Provisionierung,
Teilzustände, Statusregeln, Widerruf/Commit/Publication, Navigation, 60 Sekunden,
Migration, logische Löschung und echten nicht auth-bound Android-Keystore-Pfad.
Separate Test-UID, ausschließlich synthetische Daten, keine manuellen Geheimnisse.
P1-DB-Key/SessionCoordinator/SessionDbKeyOwner/B2Driver, Sessionzeiten, Permissions
und Backup-/Transferausschlüsse bleiben unverändert.

PARTICIPANT_CONTROLLED bleibt ohne Datentyp und Implementierung. Eine spätere
zusätzliche Envelope-Schicht ist möglich, erfordert aber eine neue Entscheidung.
Vor Erweiterungen auf mehrere Leitungszugänge, Recovery, neue Datenklassen,
Retentionfristen oder File-Keys ist erneut ausdrückliche Freigabe notwendig.

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

Prüfstand nach Guard-Fix: 352 Unit-Tests und 24 fokussierte P3b-Galaxy/API33-
Instrumentationtests bestanden; Builds, Lint, Architektur und Produktmanifest
grün. Mini-Audit bestätigt aktionsweite fail-closed Prüfung und unveränderte
AND-/Session-Konstruktion. Die tatsächlichen Touch-/IME-Ereignisse bleiben ohne
End-to-End-UI-Instrumentation; ihre Policies sind lokal getestet.
