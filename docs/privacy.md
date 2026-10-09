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
- **PRIV-03:** Keine globale Personenkartei, E-Mail, automatische aktionsübergreifende Personenverknüpfung, Standortverfolgung oder Statushistorie. Personenbezogene Notes werden über Note modelliert. Protokolle erhalten keine Teilnehmerlisten, Autorenprofile oder Versionshistorie.
- **PRIV-04:** Keine Telemetrie, Tracking-, Werbe-, Cloud-, Synchronisations- oder externen Crash-Reporting-Dienste im MVP. Lokale Kernfunktionen funktionieren ohne Netzwerk. Berechtigungen siehe SEC-10.
- **PRIV-05:** Keine echten personenbezogenen Daten in Entwicklung, Tests, Beispielen, Screenshots oder Repository-Dokumentation; keine Secrets im Repository.
- **PRIV-06:** Personenbezogene Daten, ungeprüfte Freitexte und importierte Dateien sind standardmäßig SENSITIVE. Alle operativen Daten werden unabhängig von ihrer Klasse verschlüsselt.
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
| Task, Material | Arbeits- und Materialkoordination | SENSITIVE bei Personenbezug/ungeprüftem Freitext | Wie Aktion | Frist offen; Referenzen und Ableitungen bereinigen |
| Note, typisiertes Protokoll | Organisatorische Dokumentation | Standard SENSITIVE bei ungeprüftem Inhalt/Personenbezug | Wie Aktion | Eigene Frist offen; personenbezogene Kaskade |
| Document, FileAsset, Beleg | Offline-Unterlagen, Nachweise | Standard SENSITIVE | Verschlüsselte Metadaten und private verschlüsselte Dateien | Eigene Frist offen; Eigentümerbeziehung beachten |
| Expense | Ausgaben und Summen | SENSITIVE bei ungeprüftem Freitext/Personenbezug | Verschlüsselter strukturierter Speicher | Frist offen; optionalen Beleg mitlöschen |
| RetentionPolicy, Fristmetadaten | Aufbewahrung steuern | Geschützt; keine sensiblen Freitextbegründungen | Privater geschützter technischer/strukturierter Speicher | Verwendete Regeln nicht ersatzlos löschen; Details offen |
| Authentifizierungsdaten, Schlüsselreferenzen/-material | Zugriffsschutz | Sicherheitskritisch; keine fachliche Herabstufung | Separater Sicherheitsbereich/Keystore nach Prototyp | Lebenszyklus offen; niemals Klartext-Credentials |
| Löscharbeitsstatus | Wiederanlauf | Geschützte minimale technische Daten | Privater geschützter Bereich | Nur für Bereinigung erforderlich; keine dauerhafte detaillierte Historie |
| Suchindizes, Vorschauen, UI-Ableitungen | Suche/Anzeige | Schutz mindestens entsprechend Ausgangsdaten | Speicher oder geschützter privater Bereich | Mit Ausgangsdaten bereinigen; keine ungeschützten persistenten Kopien |
| Temporärer entschlüsselter Inhalt | Notwendige Verarbeitung/Anzeige | Entsprechend Ausgangsdaten | Kontrollierte Streams/Arbeitsspeicher; keine freigegebene Klartext-Dateiausnahme | Kürzest mögliche Nutzung; Abschluss-/Wiederanlaufbereinigung |

Ausgaben- und Belegzwecke rechtfertigen keine zusätzliche Erfassung von Teilnehmeridentitäten. Kosten können sachlich bleiben; Freitext und Belege können trotzdem Personenbezüge enthalten.

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
