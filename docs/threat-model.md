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

Schutzziele sind Vertraulichkeit, Integrität und kontrollierte Verfügbarkeit. Normale Neustarts sollen den gültigen Bestand erhalten; Geräteverlust, Schlüsselverlust oder bestimmte nicht sicher reparierbare Fehler können dagegen endgültigen Datenverlust bedeuten. Im MVP kein Backup, Device-to-Device-Transfer, Recovery-Key, Export operativer Daten oder Recovery-Mechanismus. Unklare Fehler erlauben keine automatische Löschung oder Neuinitialisierung. Nachvollziehbarkeit rechtfertigt keine dauerhafte detaillierte Löschhistorie.

## 3. Vertrauensgrenzen und Annahmen

Die normale Schutzannahme ist ein nicht vollständig kompromittiertes Android-System mit funktionierender App-Isolation. Mindest-Android-Version, erforderliche Gerätesperre und Hardware-Keystore-Anforderungen sind noch offen.

Ein ressourcenstarker staatlicher/forensischer Akteur mit Zeit, Werkzeugen und möglicherweise späterem OS-/Kernel-Zugriff gehört ausdrücklich zum Angreifermodell. Er kann ein bereits entsperrtes Android-Gerät übernehmen, während PaG READY ist, das Credential zunächst nicht kennen und durch Bedienung die offene Sitzung künstlich am Leben halten. Android-Bildschirmtimeout ist keine ausreichende Sicherheitsgrenze. Die absolute PaG-Deadline begrenzt regulären Zugriff unabhängig von Aktivität; Inaktivität begrenzt zusätzlich READY (ARCH-31 bis ARCH-34). Während der verbleibenden gültigen Sitzung können Inhalte bereits eingesehen oder kopiert werden.

Das nicht kompromittierte System ist eine Voraussetzung für die angestrebte Durchsetzung, kein Ausschluss späterer Kompromittierung aus der Risikoanalyse. Ein vollständig kompromittiertes laufendes OS/Kernel kann PaG-Zeitmessung, Prozess und Datenzugriff beeinflussen; dafür keine Kontroll- oder Vertraulichkeitsgarantie. Die installierte App gilt als auffindbar, unabhängig von zurückhaltendem Branding.

DEVICE_CREDENTIAL + Keystore-Bindung ist der bevorzugte Security-Prototyp; keine Biometrie oder eigene PaG-Passphrase im ersten Prototyp. Kenntnis des Gerätecredentials kann voraussichtlich auch PaG-Authentifizierung erfüllen; dieses Restrisiko ist akzeptiert. Konkrete Keystore-Bindung bleibt offen.

Vertrauensgrenzen verlaufen zwischen App und Betriebssystem/Keystore, App und anderen Apps, App und Dokumentanbietern sowie Entwicklung/Build und eingebundenen Abhängigkeiten. Importierte Daten sind nicht vertrauenswürdig. Kameraimport nutzt den begrenzten CameraX-In-Memory-Prototyp innerhalb der App; kein automatischer externer Kamera-Fallback. Ohne Nachweis eines appseitigen Pfads ohne persistente Klartext-Zwischendatei und Gate-Umgehung wird Kameraimport verschoben. Externe Klartextöffnung und externe Viewerübergabe sind im MVP ausgeschlossen; siehe ARCH-17 bis ARCH-22 und ARCH-28. Eine Dependency im App-Prozess ist keine isolierte andere App.

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
| TM-10 | Wiederholtes Raten oder Zustandsmanipulation | Falsche Gerätecredential-Eingaben behandelt die Plattform; keine PaG-Zählung im Systemdialog. PaG verhindert parallele Flüsse, Prompt-Schleifen, verspätete Ergebnisse und alte Sitzungen. Eigene Retry-Strategie nur bei späterer PaG-Passphrase separat spezifizieren |

Technische Anforderungen stehen in security.md; Frist- und Wiederanlaufregeln in data-retention.md. Die IDs ermöglichen später die Zuordnung von Tests zu Bedrohungen.

### Forensische Szenarien und getrennte Nachweisgrenzen

A beschreibt angestrebten Schutz, B den erforderlichen Nachweis, C verbleibendes Risiko und D die technische Garantiegrenze. Die Tabelle enthält Prüfaufträge, keine bereits bestandenen Tests.

| ID / Szenario | A: Angestrebter Schutz | B: Erforderlicher praktischer Nachweis | C: Restrisiko | D: Technische Grenze |
|---|---|---|---|---|
| TM-F01: Beschlagnahmt während PaG LOCKED | Keine regulären Inhalte; geschützter persistenter Bestand | Start/Gate, gespeicherte Zustände, Speicherinventar | Frühere Kopien oder Laufzeitreste | LOCKED beweist keine vollständige RAM-/Flash-Bereinigung |
| TM-F02: Android entsperrt, PaG LOCKED, Credential unbekannt | Neue explizite PaG-Authentifizierung erforderlich | Prompt trotz Geräteentsperrung und verbleibender Autorisierung; kein Handle-Bypass | Plattform-/OEM-Lücken | Gerätezustand allein ist kein Kompromittierungsnachweis und keine PaG-Freigabe |
| TM-F03: PaG bereits READY | Absolute Grenze trotz fortgesetzter Bedienung; zusätzliche Inaktivitätsgrenze | B0/B5, UI und Storage, lange I/O, Deadline-Rennen | Abschöpfung bis zum Ablauf möglich | Keine Geheimhaltung bereits angezeigter/verarbeiteter Klartexte |
| TM-F04: Credential später bekannt | Kontrollierte Authentifizierung und Schlüsselzugriff | Auth-/Keystore-Verhalten | Credential kann PaG-Authentifizierung erfüllen | Keine unabhängige PaG-Geheimnisgrenze zugesagt |
| TM-F05: Offline-Dateisystemanalyse | Verschlüsselung aller operativen Bestände; opaque Namen | DB, Dateien und Schlüsselartefakte untersuchen | Größen und technische Metadaten | Keine vollständige Metadaten- oder Rollback-Garantie |
| TM-F06: RAM-/Prozessspeicherzugriff | Klartext-/Schlüssel-Lebensdauer reduzieren | Cache-/Handle-Freigabe bei Lock, Speicherreview mit synthetischen Daten | JVM/native/OS-Kopien | Keine garantierte vollständige Überschreibung; Verarbeitung nicht insgesamt in TEE |
| TM-F07: Debugger/ADB | Testangriffsfläche von Produktbetrieb abgrenzen | Debug-/Release-Konfiguration und zulässige Diagnosepfade prüfen | Bereits freigegebene Debug-/privilegierte Zugriffe | ADB-Fähigkeiten hängen von Gerätezustand ab; kein pauschaler ADB-Schutz |
| TM-F08: Kompromittiertes OS/Kernel | Vorherige Datensammlung und Freigabezeit minimieren | Grenzen dokumentieren, keine Kompromittierungserkennung als Sicherheitsbasis | Prozess-/Zeit-/Schlüsseloperationsmanipulation | Keine zuverlässige Kontrolle des laufenden kompromittierten Systems |
| TM-F09: OEM-/forensische Schwachstellen | Kleine geprüfte Plattformmatrix und minimale Angriffsfläche | Reale Plattform-/Keystore-/Lifecycle-Tests | Unbekannte Exploits | Kein Nachweis der Abwesenheit aller Plattformlücken |
| TM-F10: Backup-/Transferreste | Keine appseitigen Replikate | Speicherbereiche, echte Backup-/Transferläufe | Frühere oder privilegiert erzeugte Kopien | Keine Rücknahme externer Kopien |
| TM-F11: WAL/Journal/Temp/Staging | Keine persistenten operativen Klartext-Nebenbestände | B1/B4, Last, Crash, offene Dateien und Wiederanlauf | Technische Header, historische Seiten und Schlüsselreste | Verschlüsselung der Hauptdatei beweist nicht Schutz jedes Nebenbestands |
| TM-F12: Logs/Exceptions | Keine Inhalte, Credentials, Schlüssel oder sensiblen Pfade | Dependency-Ausgaben, Logcat und Fehlerpfade prüfen | Plattform-/privilegierte Diagnosen | Keine Kontrolle sämtlicher OS-Dumps |
| TM-F13: Screenshots/Recents | Fensterschutz und neutrale Sperransicht | Screenshot-/Recent-/Lifecycle-Tests | Externe Fotografie | Kein Schutzversprechen gegen kompromittierte Bildschirmerfassung |
| TM-F14: Clipboard | Keine unaufgeforderte Kopie | Kopierabläufe und Lock-Grenzen prüfen | Ausdrücklich kopierte Inhalte verlassen Sitzung | Bereits gelesene Kopien nicht zuverlässig rückholbar |
| TM-F15: Fremde IME/Tastatur | Unnötige Eingaben und Weitergabe minimieren | Eingabepfade und Hinweise prüfen; Credential nur im Systemdialog | Tastatur kann fachliche Eingaben verarbeiten | Keine Kontrolle fremder IME oder deren externer Kopien |
| TM-F16: Import-/CameraX-Zwischenzustände | Kontrollierter sessiongebundener Pfad ohne persistente Klartextdatei | Importabbruch; später ImageProxy, Puffer und Deadline-Callbacks | Quelloriginale und Laufzeitkopien | Keine vollständige Kontrolle interner OS-/Hardwarepuffer |
| TM-F17: Supply Chain/Dependencies | Herkunft, feste Versionen und minimaler Umfang | Dependency-/Build-/Quellreview | Schadcode im App-Prozess | Module sind keine Runtime-Sandbox |
| TM-F18: Physische Flash-Reste | Nichterhebung, frühe logische Löschung, wenig Persistenz | Kontrollierte Löschkaskaden und Schlüsselzustände | Alte Zellen, Seiten oder Verpackungen | Keine zuverlässige physische Überschreibung einzelner Flash-Zellen |

Nie erhobene Daten sind stärker geschützt als später gelöschte Daten: Löschung kann historische Kopien und Flash-Reste hinterlassen. Verschlüsselung ruhender Daten schützt keine bereits in gültigem READY verfügbaren Klartexte. Die Maßnahmenpriorität steht verbindlich in PRIV-10; technische Freigabegrenzen in SEC-01 und ARCH-31 bis ARCH-34.

## 5. Verbindliche Abgrenzung und Grenzen

**TM-REQ-01:** Keine Zwangs-/Duress-PIN, Fake-Daten, beschlagnahmeabhängige Löschung oder sonstige Täuschungs-, Verschleierungs- und Umgehungsfunktionen. Löschung folgt gewöhnlichen Benutzeraktionen oder festgelegten Fristen, nicht einem vermuteten Ermittlungsereignis.

**TM-REQ-02:** Keine App-Verbergung als Sicherheitsmechanismus: kein versteckter Launcher, geheime Start-/Dialer-Codes, Tarnung als andere App oder versteckte Erkennungsausweichkomponenten. Keine Notifications, Foreground Services, operativen Widgets/Shortcuts oder sensiblen Lockscreen-Inhalte gemäß SEC-16. Sessionablauf sperrt gewöhnlich und löscht nicht automatisch Daten.

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
| Konkrete absolute/Inaktivitätsdauer | Kurze begrenzte Freigabe; keine realen Zeitwerte hier | Spätere Produktentscheidung; Vertrag in B0 verbindlich |

Der Security-Prototyp prüft insbesondere Authentifizierung, Schlüsselverfügbarkeit und Retention bei Sperrung. Vor Echtdatenbetrieb sind die in PRODUCT.md geforderten unabhängigen Sicherheits- und Datenschutzprüfungen erforderlich. Dieses Threat Model ist keine Zertifizierung.
