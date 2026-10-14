# Datenschutzkonzept des MVP

## 1. Status, Zweck und Verweise

Grundlage sind [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), das freigegebene [Datenmodell](data-model.md) und die verbindlichen Sicherheitsentscheidungen. Anforderungen sind Sollverhalten für spätere Tests. Empfehlungen und offene Entscheidungen sind ausdrücklich gekennzeichnet.

[threat-model.md](threat-model.md) beschreibt Risiken; [data-retention.md](data-retention.md) Fristen und Kaskaden; [security.md](security.md) Speicherung, Berechtigungen und Schnittstellen. Diese Dokumente ersetzen keine fachkundige datenschutzrechtliche Prüfung.

## 2. Verbindliche Datenschutzanforderungen

- **PRIV-01:** Für jeden gespeicherten Datentyp sind Zweck, Sensitivität, Speicherort, Frist und Löschverfahren zu dokumentieren. Auch temporäre und abgeleitete Daten zählen dazu.
- **PRIV-02:** Nur für den konkreten organisatorischen Zweck benötigte Angaben erfassen. Name, Pseudonym und Telefonnummer bleiben optional. Kein Pflichtname für ansonsten gültige Personeneinträge.
  P3a konkretisiert dies ausdrücklich: ausschließlich verpflichtendes Pseudonym
  zur aktionsgebundenen Koordination, keine Namens-/Telefonfelder. BASIC ist eine
  Zugriffsklasse und hebt die SENSITIVE-Einordnung nicht auf; siehe
  [person-data-access.md](person-data-access.md).
- **PRIV-03:** Keine globale Personenkartei, E-Mail, automatische aktionsübergreifende Personenverknüpfung, Standortverfolgung oder allgemeine Personenstatushistorie. Personenbezogene Notes bleiben Notes; keine globale Teilnehmerliste oder Autorenprofile.
  **Ausdrückliche enge P6a.1-Ausnahme:** Ein Aktionsprotokolleintrag darf 0..n
  ausdrücklich betroffene Personen derselben Aktion referenzieren (UI nur
  Pseudonyme) und eine eintragsbezogene Revisionshistorie besitzen. Diese entsteht
  allein aus bewusster Dokumentation/Korrektur, nicht automatisch aus P4/P5a.
  Keine allgemeinen Teilnehmerlisten, Autorenprofile, Bewegungsprofile oder
  aktionsübergreifende Personen-/Einsatzkräftewiedererkennung. Andere Notes
  erhalten hierdurch keine Versionshistorie. Details im [P6-Vertrag](product-p6a-protocol.md).
- **PRIV-04:** Keine Telemetrie, Tracking-, Werbe-, Cloud-, Synchronisations- oder externen Crash-Reporting-Dienste im MVP. Lokale Kernfunktionen funktionieren ohne Netzwerk. Berechtigungen siehe SEC-10.
- **PRIV-05:** Keine echten personenbezogenen Daten in Entwicklung, Tests, Beispielen, Screenshots oder Repository-Dokumentation; keine Secrets im Repository.
- **PRIV-06:** Personenbezogene Daten, ungeprüfte Freitexte und importierte Dateien sind standardmäßig SENSITIVE. Alle operativen Daten werden unabhängig von ihrer Klasse verschlüsselt.
  **P6a.1-Zugriffsentscheidung ohne Herabstufung:** P6-Daten bleiben SENSITIVE,
  benötigen für normale P6-Arbeit in gültigem READY aber keinen zusätzlichen
  P3b-/Leadership-Step-up. Sensitivitätsklasse und Zugriffsmodus sind getrennt.
  Dies ist der enge Trade-off für schnelle Dokumentation/Nachbereitung, keine
  allgemeine Öffnung von SENSITIVE. P3b-Identitäts-/Kontaktdaten bleiben separat
  geschützt; keine automatische Übernahme. Offenlegungsrisiko in offenen Sessions
  wird ausdrücklich akzeptiert, nicht durch Verschlüsselung ruhender Daten beseitigt.

- **PRIV-07:** Keine unkontrollierten Kopien erzeugen. Appverwaltete abgeleitete Daten müssen Schutz und Löschung ihrer Ausgangsdaten berücksichtigen.

**PRIV-10 — Maßnahmenpriorität:**

1. Daten möglichst nicht erheben.
2. Nicht mehr benötigte Daten früh löschen.
3. Persistenz minimieren.
4. Freigabezeit minimieren.
5. Authentifizierung erzwingen.
6. Persistenten Bestand verschlüsseln.
7. Metadaten- und UI-Leaks reduzieren.

Verschlüsselung ruhender Daten schützt keine Daten, die während einer gültigen READY-Sitzung bereits als Klartext verarbeitet oder angezeigt werden. Bereits offengelegte Inhalte werden durch Lock nicht wieder geheim. Nichterhebung vermeidet Risiken, die spätere Löschung wegen externer Kopien, Journal-/Schlüssel- und Flash-Resten nicht zuverlässig beseitigt.

**PRIV-11 — Freigabezeit:** Absolute Sessiongrenze während MAINTENANCE und READY sowie Inaktivitätsgrenze in READY minimieren reguläre Freigabezeit. Nur qualifizierte tatsächliche Vordergrundinteraktion zählt als Aktivität; keine technischen Ereignisse und keine Verlängerung der absoluten Deadline. Vertrag und offene Dauern in SEC-01 und ARCH-31 bis ARCH-34. Kein vollständiger RAM-Löschnachweis durch Referenzfreigabe.

**PRIV-12 — Keine operativen Außenoberflächen:** Keine lokalen oder Push-Notifications, Push-Infrastruktur, Benachrichtigungsberechtigung oder Foreground Services. Keine eigenen Notification Channels ohne später nachgewiesene technische Notwendigkeit und keine implizite Notification-Freigabe. Keine operativen Widgets, operativen dynamischen/persistenten Shortcuts oder sensiblen Lockscreen-Inhalte. Retention-/Ablaufwarnungen nur innerhalb zulässiger entsperrter Sitzung. Installierte App gilt als auffindbar; keine Verbergungsmechanismen nach SEC-17.

**Empfehlung:** Pseudonyme bevorzugen, wenn sie für den Zweck genügen. Kurze feldbezogene Hinweise sollen unnötige personenbezogene Freitexte vermeiden. Pseudonyme sind nicht automatisch anonym.

## 3. Dateninventar

Die Speicherbereiche sind konzeptionell. Konkrete Pfade und technische Verfahren werden erst in architecture.md/security.md festgelegt. Fristwerte sind ausdrücklich offen; vor Implementierung der Speicherung ist das Inventar entsprechend zu vervollständigen.

| Datentyp | Zweck | Schutzklasse | Geplanter Speicherbereich | Frist und Löschung |
|---|---|---|---|---|
| `Action` | Planung, Durchführung, Nachbereitung | SENSITIVE bei ungeprüftem Freitext; sonst inhaltsabhängig | Privater verschlüsselter strukturierter Speicher | Eigene Frist offen; vollständige Kaskade |
| Person, PersonRole | Personengebundene Koordination | Standard SENSITIVE | Wie Aktion | Frist offen; Personenkaskade |
| Role, PersonStatusDefinition | Aktionsgebundene Konfiguration | SENSITIVE bei ungeprüftem Freitext; sonst inhaltsabhängig | Wie Aktion | Spätestens mit Aktion; Definition/Zuordnung bereinigen |
| Task, TaskEvent (P5a) | Operative Aufgabenkoordination und minimale Lifecycle-Chronik | Ausdrücklich INTERNAL, sensible Titel möglich | Bestehende SQLCipher-Produkt-DB | Aktionsgebunden; TaskEvents mit Task entfernen, keine permanente Archivierung |
| P7 Material | Einfache aktionsgebundene Materialkoordination | Ausdrücklich INTERNAL, sensible operative Inhalte möglich | Bestehende SQLCipher-Produkt-DB | Aktionsobergrenze; Person-/Taskdelete entkoppelt Links; Frist offen |
| Note | Allgemeine organisatorische Notiz, getrennt von P6 | Standard SENSITIVE bei ungeprüftem Inhalt/Personenbezug | Wie Aktion | Eigene Frist offen; personenbezogene Note-Kaskade |
| P6: ProtocolEntry/Revision samt Betroffenen-/Quellenbezügen und Behördenkennungen | Bewusste Ereignisdokumentation und nachvollziehbare Korrektur/Nachbereitung | SENSITIVE; normale Arbeit in gültigem READY ohne zusätzlichen Leadership-Step-up | Bestehende verschlüsselte SQLCipher-Produktstorage | Eigene endliche Retentionbetrachtung, Dauer offen; alle Fassungen/Kennungen gemeinsam, vollständige Aktionslöschung als Obergrenze. Personenlöschung löst notwendige Relationen, keine automatische Textmutation. |
| Document, FileAsset | Offline-Unterlagen, Nachweise | Standard SENSITIVE | Verschlüsselte Metadaten und private verschlüsselte Dateien | Eigene Frist offen; Eigentümerbeziehung beachten |
| Expense / ExpensePayment (P8) | EUR-Kosten und Zahlungslabels | SENSITIVE, enge READY-Freigabe ohne Leadership-Step-up | Bestehende SQLCipher-DB | Frist offen, aktionsgebunden; kein Beleg |
| RetentionPolicy, Fristmetadaten | Aufbewahrung steuern | Geschützt; keine sensiblen Freitextbegründungen | Privater geschützter technischer/strukturierter Speicher | Verwendete Regeln nicht ersatzlos löschen; Details offen |
| Authentifizierungsdaten, Schlüsselreferenzen/-material | Zugriffsschutz | Sicherheitskritisch; keine fachliche Herabstufung | Separater Sicherheitsbereich/Keystore nach Prototyp | Lebenszyklus offen; niemals Klartext-Credentials |
| Löscharbeitsstatus | Wiederanlauf | Geschützte minimale technische Daten | Privater geschützter Bereich | Nur für Bereinigung erforderlich; keine dauerhafte detaillierte Historie |
| Suchindizes, Vorschauen, UI-Ableitungen | Suche/Anzeige | Schutz mindestens entsprechend Ausgangsdaten | Speicher oder geschützter privater Bereich | Mit Ausgangsdaten bereinigen; keine ungeschützten persistenten Kopien |
| Temporärer entschlüsselter Inhalt | Notwendige Verarbeitung/Anzeige | Entsprechend Ausgangsdaten | Kontrollierte Streams/Arbeitsspeicher; keine freigegebene Klartext-Dateiausnahme | Kürzest mögliche Nutzung; Abschluss-/Wiederanlaufbereinigung |

Kostenzwecke rechtfertigen keine zusätzliche Erfassung von Teilnehmeridentitäten. Kosten können sachlich bleiben; Bezeichnung und payerLabels können trotzdem Personenbezüge enthalten.

Das technische Inventar muss auch WAL/Journal/Temp/Staging, RAM-/Stream-/Flow-/UI-Caches, Logs/Exceptions und mögliche Backup-/Transferreste erfassen. Screenshots/Recents, Clipboard, fremde IME/Tastatur und Import-/CameraX-Puffer sind eigene Offenlegungsgrenzen. Eine fremde Tastatur kann fachliche Eingaben verarbeiten; PaG kontrolliert weder deren Verarbeitung noch bereits erzeugte externe Kopien. Forensische Szenarien und Garantiegrenzen stehen in TM-F01 bis TM-F18.

## 4. Sensitivitätsklassen

PUBLIC_CONTENT bezeichnet zur Veröffentlichung geeigneten Inhalt, INTERNAL interne Organisation, SENSITIVE personenbezogene oder besonders vertrauliche Angaben. Die Klassen sind keine abschließende rechtliche Klassifizierung.

Öffentlicher Inhalt kann durch seine Aktionszuordnung sensible Metadaten erhalten. Das Entfernen einer Personenreferenz anonymisiert Freitext oder Dateien nicht.

**Empfehlung:** Keine automatische Herabstufung nach Referenzlöschung. Eine ausdrückliche Klassifizierungsprüfung soll Inhalt und Kontext berücksichtigen. Klassifizierung darf App-Sperre, Verschlüsselung und Backup-Ausschluss nicht abschwächen.

## 5. Importierte Dateien und externe Grenzen

**PRIV-08:** System-Dateiimport nach ausdrücklicher Auswahl über Android-Systemmechanismen; zusätzlich bewusst ausgelöster Kameraimport über den bevorzugten CameraX-In-Memory-Prototyp. Beide verwenden denselben kontrollierten Import-Anwendungsfall (ARCH-17 bis ARCH-22). CAMERA ausschließlich für den Kamera-Workflow in READY; kein automatischer externer Kamera-Fallback. Der appseitige Pfad muss ohne persistente Klartext-Zwischendatei und ohne Gate-Umgehung nachgewiesen werden, andernfalls wird Kameraimport verschoben. Es entsteht eine appverwaltete verschlüsselte Kopie mit genau einem Eigentümer gemäß Datenmodell. Bei System-Dateiimport bleibt das externe Original außerhalb der Kontrolle der App; Kameraaufnahme erzeugt keine Galerie-/MediaStore-Kopie. Eine dauerhafte Quell-URI wird nicht als Fachdatum eingeführt.

**PRIV-09:** Importablauf und Benutzerinformation müssen klar unterscheiden zwischen der verwalteten Kopie und dem externen Original. App-Löschung darf nicht als Löschung des Originals dargestellt werden.

Dateinamen, EXIF-Daten, PDF-Metadaten und eingebettete Inhalte können Personenbezüge enthalten. Systempicker können auch Dokumentanbieter mit externem Netzwerkverhalten anbieten. Die App selbst erhält keine INTERNET-Berechtigung, behauptet aber nicht, alle angebotenen Quellen seien lokal. Siehe [Android-Dateiauswahl](https://developer.android.com/training/data-storage/shared/documents-files).

Automatische Metadatenbereinigung ist nicht beschlossen. Im MVP keine externe Klartextöffnung und keine automatische Übergabe an externe Viewer. Intern unterstützte Formate werden im Viewer-/Storage-Prototyp festgelegt; keine Vorschau ist zulässig, wenn sichere interne Darstellung nicht vertretbar möglich ist (SEC-13, ARCH-28). Keine Cloud-Backups, Device-to-Device-Transfers, Recovery-Keys, Exporte operativer Daten oder Recovery-Mechanismen; endgültiger Datenverlust ist akzeptiert, automatische Löschung/Neuinitialisierung bei Fehlern ausgeschlossen (ARCH-26/29).

## 6. Offene Entscheidungen

| Punkt | Empfehlung | Zeitpunkt |
|---|---|---|
| Zweckbezogene Fristen einschließlich Kostenunterlagen | Tatsächlichen Nachbereitungsbedarf und mögliche Aufbewahrungspflichten fachkundig prüfen | Vor Retention-Implementierung |
| Klassifizierungsänderungen | Keine automatische Herabstufung; ausdrückliche Prüfung | Vor Klassifizierungsfunktion |
| Personenbezug in verbleibenden Freitexten/Dateien | Nachvollziehbarer manueller Prüfablauf zusätzlich zu Kaskaden | Vor Personenlöschung |
| Importformate, Größen und Metadatenbehandlung | Begrenzter Formatumfang; Inhaltstreue gegen Bereinigung abwägen | Vor Importimplementierung |
| Konkrete Speicherorte | Vollständiges Inventar einschließlich Caches/Journale ergänzen | Vor Speicherung |
| Datenschutzverantwortlichkeit und Betroffenenabläufe | Zwecke, Verantwortlichkeit, Information, Auskunft und Löschung fachkundig prüfen | Zwecke vor Implementierung; vollständige Prüfung vor Echtdaten |

Offene Schlüssel-, Authentifizierungs- und Hardwareentscheidungen werden in security.md geführt; Fristberechnung in data-retention.md.

## 7. Ableitbare Prüfungen

PRIV-01 bis PRIV-09 benötigen später Prüfungen für optional leere Personenfelder, aktionsgebundene Referenzen, Standardklassifizierung, fehlende verbotene Datentypen, fehlende Telemetrie und korrekte Original/Kopie-Information beim Systemimport sowie Kamera ohne Galerie-/Klartext-Zwischendatei oder externen Fallback und fehlende externe Viewerübergabe. Speicherinventar und manuelle Personenbezugsprüfung benötigen zusätzlich dokumentierte Reviews; deren Vollständigkeit lässt sich nicht allein durch Unit Tests beweisen.

PRIV-10 bis PRIV-12 ergänzen Review der Erforderlichkeit/Persistenz, Deadline-/Cache-Entzugstests nach ARCH-35 und Prüfungen auf fehlende operative Notifications, Widgets, Shortcuts und Lockscreen-Inhalte. Externe Kopien und vollständige RAM-/Flash-Bereinigung sind keine durch Unit Tests behauptbare Garantie.

## P4-Inventar und ausdrücklicher operativer Trade-off

Rollen, geplante Zuordnungen/Hauptfunktion und aktueller Lage-Snapshot dienen
ausschließlich der Koordination eigener aktionsgebundener Personen. P4 klassifiziert
sie ausdrücklich als operative INTERNAL-Daten für normale gültige READY-Sitzungen.
Der Personenbezug bleibt sensibel: ein Angreifer in der offenen Sitzung kann diese
Informationen sehen. Kein zusätzlicher P3b-Step-up pro Live-Änderung; die schnelle
Kernfunktion begründet diese bewusste Grenze. P3b-Identitäts-/Kontaktfelder bleiben
getrennt geschützt und erscheinen nicht im Lagebild.

Speicher: vier relationale Tabellen in der bestehenden SQLCipher-DB unter
`no_backup/pag-db-v1`, ausschließlich aktueller Snapshot und technischer Zeitstempel.
Ort/Ziel: manuell, kurze Bezeichnung, maximal 80 Codepoints, keine freien Notizen,
GPS oder Historie. Suggestions ausschließlich aus aktuellen Rows derselben Aktion.
Keine persistente globale Ortsliste, Außenoberfläche oder Netzwerkkopie. Frist offen;
kein neuer Retentionjob oder Personenlöschpfad. Einzelne unbenutzte Rollen sind in
PREPARATION nach Bestätigung logisch löschbar, ohne physische Löschgarantie.
Fokus-/Keyboard- und flüchtige UI-State-Regeln bleiben verbindlich.


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


## P6a.1 — Aktionsprotokoll: entschiedene enge Ausnahmen

[Der P6-Vertrag](product-p6a-protocol.md) ist Produktspezifikation, keine
Implementierung oder pauschale rechtliche Freigabe. P6 dokumentiert bewusst
Ereignisse einschließlich begrenzter Fassungen; es ist keine allgemeine Notiz-,
Audit-, Status- oder Bewegungshistorie. Mehrfachbetroffene sind ausdrücklich
Betroffene des einzelnen Vorgangs, keine generierte Teilnehmerliste.

SENSITIVE bleibt auch für ungeprüften Freitext, Gesundheitsbeobachtungen und
Behördenkennungen maßgeblich. Schnelle Dokumentation/Nachbereitung begründet
bewusst Zugriff ohne zusätzlichen Leadership-Step-up während gültigem READY,
keine allgemeine Herabstufung und keinen P3b-Zugriff. Ein Angreifer in offener
Session kann diesen sensiblen Bestand einschließlich früherer Fassungen lesen.
Nur-im-Detail-Kennungen verringern beiläufige Anzeige, sind kein zusätzlicher Schutz.
Keine Klarnamen-/Kontaktübernahme, globalen Personen-/Behördenprofile,
biometrischen Beschreibungen oder aktionsübergreifenden Verknüpfungen.

COMPLETED erlaubt ausschließlich P6-Nachbereitung: Korrekturen/Ergänzungen und
neue Einträge mit deutlichem dauerhaftem Nachtragmarker. recordedAt je Fassung
ist unveränderlich; kein stilles Überschreiben/gewöhnliches Einzel-Löschen, keine
P4/P5a-Writes und kein Reopen. Zeit, Inhalte und Nachtragsstatus müssen ehrlich
unterscheidbar sein, keine forensische oder rechtliche Garantie.

Teilnehmerlöschung darf ProtocolEntry/Revisionen nicht automatisch zerstören
oder inhaltlich ändern. Erforderliche Betroffenen-/REPORTED-Quellenrelationen
werden gelöst; Freitext bleibt unverändert und möglicherweise personenbezogen.
Kein automatisches Rekonstruieren historischer Pseudonyme aus P3b. Der spätere
Löschworkflow braucht eine ausdrückliche manuelle Prüfung sämtlicher Fassungen;
Relationstrennung ist keine Anonymisierung. Diese Privacy-Bereinigung ist vom
normalen append-only-Produktpfad zu unterscheiden.

Längere P6-Retention kann für Nachbereitung relevant sein und erhöht zugleich
Rekonstruktions-/Offenlegungsrisiken. Keine unbegrenzte Speicherung oder unabhängige
Archivinsel; vollständige Aktionslöschung entfernt Einträge, alle Revisionen,
Relationen, Kennungen und kontrollierte Ableitungen. Eine einzelne Altfassung darf
nicht verdeckt weiterbestehen. Dauer und konkrete Prüf-/Löschabläufe bleiben
fachkundiger Retention-/Privacyprüfung vorbehalten, keine Secure-Overwrite-Garantie.

Die App behauptet nicht pauschal, dass Erfassung oder Weitergabe jeder
personenbezogenen Behördenangabe in jedem Kontext zulässig ist. Konkrete
juristische Prüfung bleibt erforderlich. Quellenart ist optional, ohne
Glaubwürdigkeitsbewertung/Zeug:innenkartei; Behördenkennungen nur im Detail,
keine sensitiven Freitextvorschauen im Hub. Keine Medien, Exporte, Außenoberflächen
oder neue Securityarchitektur. Technische Umsetzung muss separat spezifiziert werden.


## P6b — Datenminimierung durch getrennte Readprojektionen

[Technischer P6-Plan](product-p6b-protocol-implementation.md): Summary lädt nur
Keyeventanzahl/letzte Original-Erfassungszeit; Timeline nur aktuelle schmale Fassung
mit Zeit-/Irrtums-/Nachtragsmarkern. Keine Kennungen, langen Texte, P3b oder
unnötigen Betroffenenreads. Detail lädt Entry-eigene Fassungen/Children und nur
benötigte aktuelle P3a-Pseudonyme. Datenzugriff minimieren, nicht erst SELECT-*
auf der Oberfläche verstecken; [später zu prüfen](testing.md).

SENSITIVE-/READY-Trade-off und enge PRIV-03-Ausnahme bleiben exakt P6a.1.
Revisionsgebundene Betroffenen-/ReportedBy-Relationen sind bei späterer Personen-
löschung lösbar ohne Text-/Fassungverlust, keine automatische Anonymisierung.
Vollvergleich des erwarteten Standes schützt vor stale Wiedereinsetzen gelöster
Bezüge. Manuelle Protokollprüfung und konkrete Retention-/Rechtsprüfung bleiben
eigenständige spätere Anforderungen. Keine Code-, Export- oder Medienfreigabe.


## P6c1 — implementierter DB-only-Zugriff

[P6c1](product-p6c1-protocol-storage.md) setzt genau den entschiedenen SENSITIVE/
READY-Vertrag um, ohne Leadership-Step-up oder P3b-Inhalte zu lesen. Summary und
Timeline verwenden explizite schmale SQL-Projektionen; keine Kennungen/langen Texte/
Personen in diesen Reads. Room-generiertes SQL und Modelloberflächen sind durch
gezielte Unitchecks geprüft. Vollständige Inhalte/Childrows erst im Entrydetail,
Personen dort ausschließlich benötigte aktuelle P3a-ID/Pseudonymprojektionen.

Same-Action-FKs, vollständiger Expectedvergleich und atomare vollständige Fassungen
sichern aktionslokale Bezüge. Personenrelationstrennung bewahrt Texte/Revisionen;
keine automatische Anonymisierung, manuelle Prüfung im späteren Löschworkflow
weiterhin erforderlich. Keine globale Identifierkartei, Autorenprofile, UI,
Export/Medien oder neue Erfassungskanäle. Eigene Retentionbetrachtung bleibt innerhalb
der Aktion, konkrete Frist und Echtdaten-Rechtsprüfung unverändert offen.


## P7 — einfache Materialliste

[P7](product-p7-material.md) ist ausdrücklich INTERNAL für normalen gültigen
READY-Zugriff analog P4/P5a. Bezeichnung/Menge/Ort und Personenbezüge können
sensibel sein; offene Sitzungen können diese Inhalte offenlegen. Nur bestehende
P3a-Pseudonyme, kein P3b-Read und keine Namen-/Kontaktkopien. Genau neun Itemfelder,
keine Notiz, Statusmaschine, Kategorie, Historie, Suche/Filter oder automatische Tasks.
Summary nur Count, Board nur Materialrows; Anzeige von Links aus bestehenden Boards,
keine persistierten Pseudonym-/Tasktitelsnapshots. Materialdelete verändert keine
Person/Task; deren Löschung setzt ausschließlich Links null. Aktionscascade,
keine eigene Retentionengine, Exporte, Dateien, Netzwerk, Logs oder Außenoberflächen.

## P8 — SENSITIVE Kosten und eigenständige Zahlungslabels

[P8](product-p8-costs.md) speichert nur Bezeichnung, EUR-Cents und 1–32 Text-/Cent-
Zahlungen. Keine Belege/Kategorien/Datumsfelder. payerLabel ist bewusster lokaler
Snapshot, keine Personrelation. P3a-Pseudonyme dienen nur im App-Formular als
Kopierhilfe; freie externe Labels erlaubt, kurze Pseudonyme/Bezeichnungen empfohlen.
Personenrename/-delete bereinigen payerLabel nicht. Spätere Lösch-/Freitextprüfung
muss diese eigenständigen Inhalte berücksichtigen; keine automatische Anonymisierung.
Kein P3b-Read. Gültiges READY ohne Leadership-Step-up ist eine enge ausdrückliche
P8-Freigabe, keine allgemeine Öffnung von P3b. Hub ausschließlich Anzahl/Gesamtsumme,
keine Titel/Labels/Payments; normaler COMPLETED-Bildschirm ohne Kostenprefetch.
Keine Inhalte in Logs, Notifications, Clipboard oder Export. Retentiondauer offen.
