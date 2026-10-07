# Aufbewahrung und Löschung des MVP

## 1. Status und Abgrenzung

Grundlagen sind [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), [data-model.md](data-model.md) und die verbindlichen Retention-Entscheidungen. Zweck und Inventar stehen in [privacy.md](privacy.md), technischer Schlüssel- und Speicherschutz in [security.md](security.md), Schutzgrenzen in [threat-model.md](threat-model.md).

Anforderungen sind verbindlich. Fristanker, konkrete Standardfristen, maximale Dauern, Fristberechnung und Regelversionierung sind unten ausdrücklich Empfehlungen beziehungsweise offene Entscheidungen. Keine konkreten Tageszahlen werden festgelegt.

## 2. Verbindliche Anforderungen

- **RET-01:** Sensible Daten besitzen eine zweckgebundene Aufbewahrungsregel. Nicht benötigte Daten dürfen manuell vor Fristablauf entfernt werden. Eine technische Abhängigkeit ist konsistent aufzulösen, keine automatische Erlaubnis unbegrenzter Aufbewahrung.
- **RET-02:** Vollständige Action-Löschung ist die Obergrenze für alle untergeordneten Daten. Eine längere Kinderfrist verhindert die Kaskade nicht. Kein verwaister Bestand darf verdeckt weiterbestehen.
- **RET-03:** Fällige Löschungen werden bei nächster zulässiger Ausführung nachgeholt, bevor betroffene Inhalte wieder angezeigt oder regulär gesucht, geöffnet beziehungsweise weitergegeben werden. Betroffene Ableitungen zählen dazu.
- **RET-04:** Die App-Sperre wird für Hintergrundlöschung nicht geschwächt. Fehlende Schlüssel werden nicht durch einen ungeschützten Ersatzpfad umgangen.
- **RET-05:** Keine exakte Löschung zu einem Zeitpunkt versprechen, wenn Gerät, Ausführung oder notwendige Schlüssel nicht verfügbar sind. Fristablauf, Ausführung und bestätigter Abschluss werden unterschieden.
- **RET-06:** Fehlgeschlagene oder unvollständige Bereinigung wird nicht als Erfolg angezeigt. Der minimal notwendige technische Arbeitsstatus bleibt geschützt für Wiederanlauf erhalten.
- **RET-07:** Keine dauerhafte detaillierte Löschhistorie. Arbeitsstatus enthält keine gelöschten Inhalte, Namen oder sensiblen Dateinamen und wird nach erfolgreichem Abschluss entfernt, sobald nicht mehr notwendig.
- **RET-08:** Vor irreversibler manueller Löschung werden Umfang und Folgen eindeutig erklärt und bestätigt. Automatische Löschung nach bestätigter Regel benötigt keine erneute Bestätigung. Warnungen verlängern keine Frist.
- **RET-09:** Keine zuverlässige physische Überschreibung einzelner Flash-Zellen behaupten. Erfolgsaussagen beziehen sich auf definierte, überprüfte Bereinigung kontrollierter Speicherbereiche.

## 3. Empfohlenes Fristmodell; noch offen

Empfohlen sind die Anker Objekterstellung und tatsächlicher Action-Abschluss. Für abschlussabhängige Regeln ist eine maximale Lebensdauer ab Erstellung erforderlich, damit nie abgeschlossene Actions keine unbegrenzte Speicherung verursachen. Ihre konkrete Dauer ist offen.

Ein vorgeschlagener wirksamer Termin ist der früheste zutreffende Termin aus:

1. Regeltermin oder ausdrücklich gewähltem individuellen Termin;
2. maximaler Lebensdauer;
3. Action-Löschtermin;
4. Eigentümer-Löschtermin bei abhängigen Objekten.

Ein individueller Termin ersetzt nach dieser Empfehlung den normalen Regeltermin, nicht die Obergrenzen. Verwendete Regeln sollen versioniert werden. Bearbeitung eines Inhalts soll keine Frist neu starten; Änderungen sollen bestehende Termine nicht stillschweigend verlängern. Diese Details sind vor Implementierung zu bestätigen.

Standardregeln sollen personenbezogene Daten, allgemeine Organisation, Dokumente und Kosten/Belege nach Zweck unterscheiden. Es werden keine rechtlichen Fristen pauschal aus Datentypen abgeleitet. Vor Kostenaufbewahrung sind tatsächliche Pflichten zu prüfen. Falls Kostenunterlagen länger benötigt werden, ist dies vor vollständiger Action-Löschung sichtbar zu lösen; der MVP erhält keinen unabhängigen Restbestand.

Die Retention-Attribute aus data-model.md bleiben konzeptionell, bis Anker, Dauerrechnung, Obergrenzen und Überschreibungen konkretisiert sind.

## 4. Fachliche Kaskaden

Die vollständige Beziehungstabelle steht in [data-model.md, Abschnitt 15](data-model.md#15-beziehungen-integrität-und-löschung) und wird hier nicht dupliziert. Vollständige Action-Löschung begrenzt gemäß RET-02 die Aufbewahrung aller Kinder.

Personenlöschung entfernt PersonRole und personenbezogene Notes; Aufgaben-/Materialreferenzen werden entfernt, die sachlichen Objekte bleiben. Dokumentlöschung entfernt dessen FileAsset, Ausgabenlöschung den optionalen Beleg. Frühere Beleglöschung lässt die Ausgabe ohne Beleg bestehen. Keine persistierte Action mit Status DELETED und keine Wiedereröffnung abgeschlossener Actions.

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

Nach Authentifizierung kann eine neutrale Wartungsansicht erscheinen, während fällige Bereinigung läuft. Sie darf betroffene Inhalte nicht anzeigen. Nicht betroffene Daten dürfen nur bei nachgewiesener Abgrenzung verfügbar bleiben; bei unklarem Umfang ist der betroffene Bereich zu sperren.

**Empfehlung:** Warnungen zunächst im entsperrten Dashboard, ohne sensible Systembenachrichtigungen. Vorlauf ist offen. Das Verpassen einer Warnung hält automatische Löschung nicht auf.

Hintergrundarbeit ist nur eine zusätzliche Ausführungsmöglichkeit und setzt zulässigen Schlüsselzugriff voraus. Android kann sie verschieben; siehe [Doze und App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby). Bei Sperrung einer laufenden Bereinigung muss der Ablauf sicher pausieren beziehungsweise ohne Inhaltsfreigabe wiederanlaufen; genaue Umsetzung ist offen.

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

Für testing.md abzuleiten: Fristablauf mit kontrollierbarer Zeit, maximale Lebensdauer, Action-Obergrenze, Kaskaden, fehlende Warnbestätigung, Neustart/Force-Stop, fehlende Schlüssel, Wiederholung nach jedem Abbruchpunkt, Speicherknappheit und kein Zugriff auf fällige Inhalte vor Nachholung. Physische Flash-Löschung ist kein automatisiert behauptbarer Testnachweis.
