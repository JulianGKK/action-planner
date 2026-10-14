# Aufbewahrung und Löschung des MVP

## 1. Status und Abgrenzung

Grundlagen sind [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), [data-model.md](data-model.md) und die verbindlichen Retention-Entscheidungen. Zweck und Inventar stehen in [privacy.md](privacy.md), technischer Schlüssel- und Speicherschutz in [security.md](security.md), Schutzgrenzen in [threat-model.md](threat-model.md).

Anforderungen sind verbindlich. Fristanker, konkrete Standardfristen, maximale Dauern, Fristberechnung und Regelversionierung sind unten ausdrücklich Empfehlungen beziehungsweise offene Entscheidungen. Keine konkreten Tageszahlen werden festgelegt.

## 2. Verbindliche Anforderungen

- **RET-01:** Sensible Daten besitzen eine zweckgebundene Aufbewahrungsregel. Nicht benötigte Daten dürfen manuell vor Fristablauf entfernt werden. Eine technische Abhängigkeit ist konsistent aufzulösen, keine automatische Erlaubnis unbegrenzter Aufbewahrung.
- **RET-02:** Vollständige Aktionslöschung ist die Obergrenze für alle untergeordneten Daten. Eine längere Kinderfrist verhindert die Kaskade nicht. Kein verwaister Bestand darf verdeckt weiterbestehen.
- **RET-03:** Fällige Löschungen werden bei nächster zulässiger Ausführung nachgeholt, bevor betroffene Inhalte wieder angezeigt oder regulär gesucht, geöffnet beziehungsweise weitergegeben werden. Betroffene Ableitungen zählen dazu.
- **RET-04:** Die App-Sperre wird für Hintergrundlöschung nicht geschwächt. Fehlende Schlüssel werden nicht durch einen ungeschützten Ersatzpfad umgangen.
- **RET-05:** Keine exakte Löschung zu einem Zeitpunkt versprechen, wenn Gerät, Ausführung oder notwendige Schlüssel nicht verfügbar sind. Fristablauf, Ausführung und bestätigter Abschluss werden unterschieden.
- **RET-06:** Fehlgeschlagene oder unvollständige Bereinigung wird nicht als Erfolg angezeigt. Der minimal notwendige technische Arbeitsstatus bleibt geschützt für Wiederanlauf erhalten.
- **RET-07:** Keine dauerhafte detaillierte Löschhistorie. Arbeitsstatus enthält keine gelöschten Inhalte, Namen oder sensiblen Dateinamen und wird nach erfolgreichem Abschluss entfernt, sobald nicht mehr notwendig.
- **RET-08:** Vor irreversibler manueller Löschung werden Umfang und Folgen eindeutig erklärt und bestätigt. Automatische Löschung nach bestätigter Regel benötigt keine erneute Bestätigung. Warnungen verlängern keine Frist.
- **RET-09:** Keine zuverlässige physische Überschreibung einzelner Flash-Zellen behaupten. Erfolgsaussagen beziehen sich auf definierte, überprüfte Bereinigung kontrollierter Speicherbereiche.
- **RET-10:** Retention-/Ablaufwarnungen ausschließlich innerhalb einer zulässigen entsperrten PaG-Sitzung. Keine lokalen oder Push-Notifications, Benachrichtigungsberechtigung, Push-Infrastruktur oder Foreground Services; Außenoberflächen richten sich nach SEC-16.
- **RET-11:** Maintenance und Löschung verwenden denselben zentralen Session-/Storage-Vertrag wie andere Zugriffe (ARCH-31 bis ARCH-34). Absolute Deadline begrenzt auch MAINTENANCE ohne Fachinhaltsanzeige; dort keine Inaktivitätssperre allein wegen fehlender sinnvoller Nutzerinteraktion. Neue Operations-/Schritt-/Commit-Zulassung endet bei Ablauf; physisch laufende native Arbeit kann noch enden, darf aber kein altes Ergebnis fachlich veröffentlichen. Minimalen geschützten Arbeitsstatus erhalten und beim nächsten zulässigen Unlock nachholen. Keine Verlängerung der Deadline und keine zusätzliche Datenlöschung allein aufgrund Sessionablaufs.

## 3. Empfohlenes Fristmodell; noch offen

Empfohlen sind die Anker Objekterstellung und tatsächlicher Aktionsabschluss. Für abschlussabhängige Regeln ist eine maximale Lebensdauer ab Erstellung erforderlich, damit nie abgeschlossene Aktionen keine unbegrenzte Speicherung verursachen. Ihre konkrete Dauer ist offen.

Ein vorgeschlagener wirksamer Termin ist der früheste zutreffende Termin aus:

1. Regeltermin oder ausdrücklich gewähltem individuellen Termin;
2. maximaler Lebensdauer;
3. Aktionslöschtermin;
4. Eigentümer-Löschtermin bei abhängigen Objekten.

Ein individueller Termin ersetzt nach dieser Empfehlung den normalen Regeltermin, nicht die Obergrenzen. Verwendete Regeln sollen versioniert werden. Bearbeitung eines Inhalts soll keine Frist neu starten; Änderungen sollen bestehende Termine nicht stillschweigend verlängern. Diese Details sind vor Implementierung zu bestätigen.

Standardregeln sollen personenbezogene Daten, allgemeine Organisation, Dokumente und P8-Kosten nach Zweck unterscheiden. Es werden keine rechtlichen Fristen pauschal aus Datentypen abgeleitet. Vor Kostenaufbewahrung sind tatsächliche Pflichten zu prüfen. Falls Kostenunterlagen länger benötigt werden, ist dies vor vollständiger Aktionslöschung sichtbar zu lösen; der MVP erhält keinen unabhängigen Restbestand.

Die Retention-Attribute aus data-model.md bleiben konzeptionell, bis Anker, Dauerrechnung, Obergrenzen und Überschreibungen konkretisiert sind.

## 4. Fachliche Kaskaden

Die vollständige Beziehungstabelle steht in [data-model.md, Abschnitt 15](data-model.md#15-beziehungen-integrität-und-löschung) und wird hier nicht dupliziert. Vollständige Aktionslöschung begrenzt gemäß RET-02 die Aufbewahrung aller Kinder.

Personenlöschung entfernt PersonRole und personenbezogene Notes; Aufgaben-/Materialreferenzen werden entfernt, die sachlichen Objekte bleiben. Dokumentlöschung entfernt dessen FileAsset. P8-Kostenlöschung entfernt Parent und Zahlungschildren; P8 besitzt keine Belege. Keine persistierte Aktion mit Status DELETED und keine Wiedereröffnung abgeschlossener Aktionen.

Freitext und Dateien können indirekte Personenbezüge behalten. Referenzlöschung ist keine garantierte Anonymisierung; der ergänzende Prüfablauf bleibt in privacy.md offen.

## 5. Ausführung, Warnungen und Gerätezustände

| Zustand | Erforderliches Verhalten |
|---|---|
| Zulässiger Zugriff mit verfügbaren Schlüsseln | Fälligkeit prüfen und betroffene Inhalte vor regulärem Zugriff bereinigen |
| App gesperrt, Schlüssel nicht verfügbar | Kein Umgehen der Sperre; Nachholung bei nächster zulässiger Ausführung |
| Gerät ausgeschaltet oder vor erforderlicher Geräteentsperrung | Keine garantierte Ausführung; später nachholen |
| App zwangsbeendet oder Hintergrundarbeit verzögert | Keine Termingarantie; Prüfung beim nächsten zulässigen Start/Zugriff |
| Löschung unterbrochen/fehlgeschlagen | Betroffene Inhalte nicht wieder regulär freigeben; Arbeitsstatus erhalten und fortsetzen |
| Schlüssel dauerhaft verloren | Keine erfolgreiche vollständige Bereinigung behaupten, solange Nachweis fehlt; keine Recovery; technische Verlustbehandlung offen, keine automatische Löschung/Neuinitialisierung bei unklarem Fehler |

Nach Authentifizierung kann eine neutrale Wartungsansicht erscheinen, während fällige Bereinigung läuft. In MAINTENANCE dürfen keine Fachinhalte angezeigt werden. Nicht betroffene Daten dürfen nur in einer gültigen READY-Sitzung bei nachgewiesener Abgrenzung verfügbar bleiben; bei unklarem Umfang ist der betroffene Bereich zu sperren.

Warnungen verbindlich nur innerhalb zulässiger entsperrter Sitzung, keine Systembenachrichtigungen. **Empfehlung:** Anzeige im Dashboard; Vorlauf bleibt offen. Das Verpassen einer Warnung hält automatische Löschung nicht auf.

Hintergrundarbeit ist nur eine zusätzliche Ausführungsmöglichkeit und setzt zulässigen Schlüsselzugriff voraus. Android kann sie verschieben; siehe [Doze und App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby). Bei Sperrung einer laufenden Bereinigung muss der Ablauf sicher pausieren beziehungsweise ohne Inhaltsfreigabe wiederanlaufen; genaue Umsetzung ist offen.

Ein technischer Hintergrundablauf benötigt zusätzlich die zentrale Session-/Lifecycle-Zulässigkeit; Schlüsselverfügbarkeit allein reicht nicht. Kein eigener Unlock-State und kein Foreground Service zur Verlängerung. Resume prüft vor Anzeige; bekannte unterbrochene Aufträge werden geschützt abgeglichen, unbekannte Dateien nicht vorsorglich gelöscht. Retention-Kalenderzeit und ihre offenen Fristen sind von der monotonen Sessionzeit getrennt.

## 6. Wiederanlauf und Erfolgskriterien

Der Ablauf muss wiederholbar sein. Schon entfernte Bestandteile dürfen bei Wiederholung keinen neuen Inhalt erzeugen oder die übrige Bereinigung verhindern.

Fachliche Schritte sind Umfang feststellen, minimalen geschützten Arbeitszustand sichern, Inhalte vom regulären Zugriff ausschließen, strukturierte Daten/Referenzen und Dateien/Ableitungen bereinigen sowie den Abschluss prüfen. Die technische Reihenfolge ist offen: strukturierte Änderungen bleiben transaktional, aber über Datenbank, Dateisystem und Keystore wird keine atomare Gesamttransaktion behauptet. ARCH-19/25 verlangen definierte wiederanlauffähige Übergänge.

Der Arbeitsstatus darf nur notwendige interne Zielreferenzen, Fortschritt und unkritische Fehlerkategorien enthalten; genaue Attribute und Aufbewahrung sind noch festzulegen. Interne IDs und Fristen werden ebenfalls geschützt. Keine Kopie des zu löschenden Inhalts als Wiederanlaufhilfe. Unklare Schlüssel-, Schema-, Integritäts- oder Datenzustände erlauben keine vorsorgliche Löschung oder Neuinitialisierung; eine nicht zuordenbare Datei wird nicht wegen eines gescheiterten DB-Opens gelöscht (ARCH-26).

Erfolgskriterien müssen vor Implementierung mindestens das Fehlen regulärer Inhaltszugriffe, die konsistente Entfernung von Beziehungen und die Bereinigung verwalteter Dateien, Suchdaten, Vorschauen und temporärer Inhalte abdecken. Zusätzliche kryptografische Löschbehauptungen brauchen einen separaten Nachweis.

## 7. Technische Löschgrenzen

Logische Datenbanklöschung entfernt nicht zwingend alte Seiten oder Journale. Dateientfernung beweist keine physische Überschreibung. Überschreiben einer Datei erlaubt keine zuverlässige Aussage über alle historischen Flash-Zellen.

Schlüsselbasierte Löschung setzt voraus, dass keine nutzbaren Schlüsselkopien, alten Verpackungen oder Klartexte verbleiben. Nur einen verpackten Dateischlüssel aus einer weiterhin entschlüsselbaren Datenbank zu entfernen genügt nicht als Nachweis: Speicherrückstände könnten ihn enthalten. Die Schlüsselarchitektur bleibt bis zum Prototyp offen.

Externe Originale, Empfängerkopien und Fotografien liegen außerhalb der Kaskade. Geräte-/Cloud-Backups werden gemäß SEC-11 ausgeschlossen; dieser Ausschluss muss geprüft sein und ist keine Behauptung der Kontrolle über privilegierte externe Kopien.

## 8. Offene Entscheidungen und Prüfungen

| Entscheidung | Empfehlung | Vor Implementierung |
|---|---|---|
| Fristanker und maximale Lebensdauer | Erstellung/Abschluss; endliche Obergrenze | Ja |
| Standardfristen und individuelle Anpassung | Zweckbezogene endliche Regeln; Verlängerung ausdrücklich | Ja |
| Regelversionen und Neuberechnung | Keine stillen Verlängerungen; Änderung nicht als Neuerstellung behandeln | Ja |
| Zeitmodell und Zeitmanipulation | Eindeutige Zeitpunkte; Kalender-/Zeitdauer unterscheiden; Auffälligkeiten behandeln | Ja |
| Warnvorlauf und Darstellungsort | Entsperrtes Dashboard | Ja, vor Warnfunktion |
| Arbeitsstatus, Reihenfolge und Retry | Minimal, geschützt, wiederanlauffähig; keine Erfolgsmeldung bei Teilfehler | Ja, nach Prototyp |
| Löschen bei Schlüsselverlust | Keine unbewiesene Erfolgszusage; kontrollierte Bereinigung prüfen | Ja, mit SEC-OD-04 |

Für testing.md abzuleiten: Fristablauf mit kontrollierbarer Zeit, maximale Lebensdauer, Aktionsobergrenze, Kaskaden, fehlende Warnbestätigung, Neustart/Force-Stop, fehlende Schlüssel, Wiederholung nach jedem Abbruchpunkt, Speicherknappheit und kein Zugriff auf fällige Inhalte vor Nachholung. Physische Flash-Löschung ist kein automatisiert behauptbarer Testnachweis.


## P7 — Material innerhalb der Aktionsobergrenze

[P7](product-p7-material.md) implementiert bewusst nur bestätigtes Materialdelete
in PREPARATION/ACTIVE und automatische Person-/Taskreferenzentkopplung. Das Item
bleibt nach Detach, vollständige Aktionslöschung entfernt es per CASCADE. Übrige
P4/P5a-Löschabhängigkeiten bleiben unverändert. Keine Retentionfelder/-dauer/-worker,
allgemeine Personen-/Aktionslöschung oder physische Überschreibgarantie.
Materialbezeichnung/Menge/Ort können nach Detach weiterhin Personenbezüge enthalten;
Referenzentkopplung ist keine Anonymisierung. Konkrete endliche Retention bleibt offen.

## P8 — Grenze der Kostenaufbewahrung

[P8](product-p8-costs.md) implementiert keine Frist und keinen Retentionworker.
Kosten können einen anderen legitimen Nachbereitungs-/Aufbewahrungsbedarf als
Tasks/Material haben. Konkrete Fristen und mögliche rechtliche Pflichten müssen
vor Retentionimplementierung fachkundig geprüft werden. Actiondelete bleibt die
Obergrenze und entfernt Expenses/Payments vollständig, kein unabhängiger Restbestand.
Personenlöschung verändert payerLabel nicht; spätere manuelle Freitextprüfung muss
Kostenlabels ausdrücklich berücksichtigen. Keine Secure-Overwrite-Garantie.

## P9a — Gedächtnisprotokolle: Entwürfe und abgeschlossene Berichte

[P9a](product-p9a-memory-protocol.md) entscheidet verschlüsselte persistente
Fachentwürfe durch ausdrückliches abschnittsweises „Speichern und weiter“.
Lock entfernt ungespeicherte Eingabe/Anzeige, nicht bereits gespeicherte Abschnitte.
Dies ist keine unbegrenzte Aufbewahrung, Retentionimplementierung oder automatische
Verlängerung durch Speichern/Ergänzen. Keine konkrete Dauer/Fristanker festgelegt.
Der Nachbereitungsbedarf ist vor Retentionumsetzung fachkundig zu prüfen.

Vollständige Aktionslöschung bleibt Obergrenze für authorLabel, gespeicherte
Entwürfe, abgeschlossene Antworten, sämtliche Ergänzungen und kontrollierte
Ableitungen. Keine unabhängige Archivinsel. Append-only im normalen Produktpfad
hebt notwendige Privacy-/Retentionlöschung nicht auf. Ob abgeschlossene Berichte
manuell als Ganzes gelöscht werden dürfen, bleibt ausdrücklich offen; RET-01
nimmt diesen künftigen P9-Workflow nicht vorweg. Keine normale Einzel-Löschung
von Ergänzungen zunächst vorgesehen.

Kein Person-FK: Personenrename/-delete ändern authorLabel/Antworten nicht.
P9-Inhalte müssen bei späterer Lösch-/Freitextprüfung berücksichtigt werden;
keine garantierte Anonymisierung oder physische Überschreibung. Allgemeiner
Löschworkflow, Fristen, mögliche fachkundige Aufbewahrungsanforderungen und
technische Bereinigungsnachweise bleiben gesonderte spätere Arbeit.

## P9b — Storagekaskade ohne Retentionengine

[P9b](product-p9b-memory-protocol-implementation.md) setzt Reports/Answers/
Addenda aktionsgebunden mit CASCADE um. Abschnittspersistenz übersteht Lock,
ungespeicherte Eingabe wird verworfen; kein Autosave oder Fristneustart.
Keine konkrete Dauer, Retentionworker oder manuelle Berichtslöschung freigegeben.
Personendelete verändert authorLabel/Antworttexte nicht. Der spätere allgemeine
Lösch-/Freitextworkflow muss dies berücksichtigen. Vollständige Aktionslöschung
bleibt Obergrenze; keine physische Überschreibgarantie. P9a-Restfragen bleiben offen.
