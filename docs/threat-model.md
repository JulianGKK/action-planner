# Threat Model des MVP

## 1. Status und Grundlage

Verbindlicher konzeptioneller Stand auf Grundlage von [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), [data-model.md](data-model.md) und den ausdrücklich freigegebenen Sicherheitsentscheidungen. Anforderungen beschreiben das zu implementierende Verhalten, keine bereits nachgewiesene Sicherheit. Empfehlungen und offene Entscheidungen sind keine Implementierungsfreigabe.

Dieses Dokument definiert Schutzgrenzen. [privacy.md](privacy.md) begrenzt die Datenerfassung, [data-retention.md](data-retention.md) die Lebensdauer und [security.md](security.md) die technischen Schutzanforderungen. Konkrete Verfahren und Bibliotheken sind nicht ausgewählt.

## 2. Schutzgüter und Ziele

Primär werden möglichst wenige Daten benötigt. Verschlüsselung ergänzt Datenminimierung und kurze Aufbewahrung, ersetzt sie aber nicht.

| Schutzgut | Beispiele und Risiko |
|---|---|
| Identität und Kontakt | Name, Pseudonym, Telefonnummer; Pseudonyme können identifizierbar bleiben |
| Teilnahme und Beziehungen | Personenstatus, Rollen, Zuständigkeiten und Action-Zuordnung |
| Organisation | Titel, Zeiträume, Aufgaben, Materialstandorte und interne Hinweise |
| Inhalte | Notes, Protokolle, Dokumente, Belege und Kosten |
| Metadaten | Dateinamen, Größen, Zeitpunkte, Suchdaten und Vorschauen |
| Sicherheitszustand | Authentifizierungsdaten, Schlüssel und Sitzungsfreigabe |
| Lebenszyklus | Fristen, Löschaufträge und deren Integrität |

Schutzziele sind Vertraulichkeit, Integrität und kontrollierte Verfügbarkeit. Normale Neustarts sollen den gültigen Bestand erhalten; Schlüsselverlust kann dagegen endgültigen Datenverlust bedeuten. Nachvollziehbarkeit rechtfertigt keine dauerhafte detaillierte Löschhistorie.

## 3. Vertrauensgrenzen und Annahmen

Die normale Schutzannahme ist ein nicht vollständig kompromittiertes Android-System mit funktionierender App-Isolation. Mindest-Android-Version, erforderliche Gerätesperre und Hardware-Keystore-Anforderungen sind noch offen.

Vertrauensgrenzen verlaufen zwischen App und Betriebssystem/Keystore, App und anderen Apps, App und Dokumentanbietern sowie Entwicklung/Build und eingebundenen Abhängigkeiten. Importierte Daten sind nicht vertrauenswürdig. Eine Dependency im App-Prozess ist keine isolierte andere App.

| Zustand | Angestrebter Schutz und Grenze |
|---|---|
| Gerät und App gesperrt | Schutz ruhender Daten durch Plattform, App-Authentifizierung und Verschlüsselung; konkrete Stärke erst nach Prototyp bewertbar |
| Gerät entsperrt, App gesperrt | Zusätzlicher Schutz gegen normale unbefugte Bedienung; Entsperren des Geräts bedeutet nicht automatisch App-Freigabe |
| App entsperrt | Berechtigte Sitzung kann Inhalte lesen; eine Person mit Zugriff auf diese Sitzung kann ebenfalls Inhalte sehen |
| App-Prozess oder System kompromittiert | Keine zuverlässige Vertraulichkeitsgarantie für verfügbare Klartexte oder zulässige Schlüsseloperationen |

Ein entsperrtes Gerät ist nicht automatisch kompromittiert. Ein nicht exportierbarer Keystore-Schlüssel verhindert nicht grundsätzlich seine missbräuchliche Verwendung auf einem kompromittierten Gerät. Hardwareunterstützung und Nutzungsbeschränkungen müssen tatsächlich geprüft werden; siehe [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

## 4. Bedrohungen und Gegenmaßnahmen

| ID | Szenario | Gegenmaßnahmen und verbleibendes Risiko |
|---|---|---|
| TM-01 | Gerät verloren oder gestohlen | Minimierung, Retention, Sitzungssperre und verschlüsselte Speicherung; schwache Geheimnisse und Plattformlücken bleiben Risiken |
| TM-02 | Physischer Zugriff | Sofortige Sperre bei Gerätesperre/Verlassen, geschützte Vorschau; Beobachtung, Fotografie und bereits offene Sitzung bleiben möglich |
| TM-03 | Bösartige App | Privater Speicher, minimale Berechtigungen und kontrollierte Schnittstellen; privilegierte Bedienungshilfen, Tastatur, Overlays oder OS-Lücken können Schutzgrenzen überschreiten |
| TM-04 | Android-Backup oder Gerätetransfer | Vollständiger Ausschluss der geschützten App-Daten gemäß SEC-11; Herstellerverhalten muss getestet werden |
| TM-05 | Kompromittierte Dependency | Begründete Auswahl, Herkunftsprüfung, feste Versionen und Updates; Code im App-Prozess kann verfügbare Inhalte missbrauchen |
| TM-06 | Implementierungsfehler | Zugriffsschutz unterhalb der UI, Transaktionen, Klartext- und Ausfallprüfungen; unbekannte Fehler bleiben möglich |
| TM-07 | Benutzerfehler | Zweckgebundene Felder, Löschfolgen und Importgrenzen erklären; unnötige Freitexte und externe Kopien bleiben Risiken |
| TM-08 | Bösartige/defekte Datei | Begrenzte Formate, Größenprüfung, keine automatischen aktiven Inhalte; Parser- und Rendererfehler bleiben möglich |
| TM-09 | Absturz, Stromverlust, Speicherknappheit | Konsistente Speicherung und wiederanlauffähige Bereinigung; Verfügbarkeit kann vorübergehend eingeschränkt sein |
| TM-10 | Wiederholtes Raten oder Zustandsmanipulation | Progressive Wartezeiten; Neustart- und Zeitverhalten testen; App-Zähler sind kein garantierter Schutz vor Offline-Angriffen oder privilegiertem Rollback |

Technische Anforderungen stehen in security.md; Frist- und Wiederanlaufregeln in data-retention.md. Die IDs ermöglichen später die Zuordnung von Tests zu Bedrohungen.

## 5. Verbindliche Abgrenzung und Grenzen

**TM-REQ-01:** Keine Zwangs-/Duress-PIN, Fake-Daten, beschlagnahmeabhängige Löschung oder sonstige Täuschungs-, Verschleierungs- und Umgehungsfunktionen. Löschung folgt gewöhnlichen Benutzeraktionen oder festgelegten Fristen, nicht einem vermuteten Ermittlungsereignis.

Keine Garantie wird behauptet für:

- Schutz zugänglicher Daten auf einem vollständig kompromittierten Gerät;
- Geheimhaltung gegenüber Zugriff auf eine bereits entsperrte App;
- Rücknahme von externen Originalen, Fotografien, Zwischenablage- oder Empfängerkopien;
- exakte Löschzeit bei ausgeschaltetem Gerät, fehlenden Schlüsseln oder verzögerter Ausführung;
- zuverlässige physische Überschreibung einzelner Flash-Zellen;
- vollständige Entfernung aller Klartextkopien aus Laufzeitspeicher;
- manipulationssichere absolute Zeit in einem vollständig lokalen System.

## 6. Offene Entscheidungen und Nachweise

| Entscheidung | Empfehlung | Vor Implementierung zu klären |
|---|---|---|
| Unterstützte Android-/Gerätematrix | Begrenzte, real getestete Matrix; sichere Gerätesperre prüfen | Ja, vor Security Foundation |
| Hardware-Keystore und Fallback | Tatsächliche Fähigkeiten prüfen; keinen stillen schwächeren Fallback | Ja, nach Security-Prototyp |
| Akzeptiertes Restrisiko | Offene Sitzung, privilegierte Apps und kompromittiertes OS ausdrücklich dokumentieren | Ja, vor Freigabe der Sicherheitsarchitektur |

Der Security-Prototyp prüft insbesondere Authentifizierung, Schlüsselverfügbarkeit und Retention bei Sperrung. Vor Echtdatenbetrieb sind die in PRODUCT.md geforderten unabhängigen Sicherheits- und Datenschutzprüfungen erforderlich. Dieses Threat Model ist keine Zertifizierung.
