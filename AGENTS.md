# AGENTS.md

## Projekt

`action-planner` ist eine private, offline-first Android-Anwendung für die Organisation und Dokumentation von Aktionen.

Die zentrale fachliche Einheit ist eine **Aktion**. Eine Aktion kann unter anderem Personen, Rollen, Aufgaben, Material, Dokumente, Notizen/Protokolle, Kosten und Aufbewahrungsregeln enthalten.

Die vollständige fachliche Zielsetzung steht in `PRODUCT.md`. Diese Datei ist vor größeren fachlichen oder architektonischen Entscheidungen zu lesen.

## Grundprinzipien

* Offline-first: Die Anwendung muss grundsätzlich ohne Internet funktionieren.
* Datenschutz und Datensparsamkeit haben hohe Priorität.
* Keine unnötige Erfassung personenbezogener Daten.
* Keine Telemetrie, kein Tracking und keine unnötigen Netzwerkverbindungen.
* Keine Cloud-Abhängigkeit für die Kernfunktionen.
* Keine echten personenbezogenen Daten in Quellcode, Tests, Beispieldaten, Screenshots oder Dokumentation.
* Keine Secrets, Passwörter, API-Schlüssel oder privaten Schlüssel im Repository.
* Sicherheitsrelevante Entscheidungen müssen nachvollziehbar dokumentiert werden.

## Technologie

Die Zielplattform ist Android.

Bevorzugte Technologien:

* Kotlin
* Jetpack Compose
* Android Jetpack
* Android Keystore für Schlüsselmaterial
* lokale, verschlüsselte Datenspeicherung für schützenswerte Daten

Neue Bibliotheken nur hinzufügen, wenn sie einen klaren Nutzen haben.

Vor dem Hinzufügen einer sicherheitskritischen oder zentralen Abhängigkeit zuerst prüfen, ob sie etabliert, gepflegt und für das Projekt geeignet ist.

Keine eigene Kryptografie implementieren.

## Arbeitsweise

Arbeite schrittweise und nachvollziehbar.

Vor einer größeren Änderung:

1. Bestehende Projektstruktur untersuchen.
2. Relevante Dokumentation lesen.
3. Bestehenden Code verstehen.
4. Einen kleinen, klar abgegrenzten Änderungsumfang festlegen.
5. Erst danach Änderungen durchführen.

Bevorzugt kleine Änderungen gegenüber großen Umbauten.

Bestehende funktionierende Strukturen nicht ohne Grund ersetzen.

Keine großen Architekturänderungen ohne ausdrückliche Zustimmung.

## Tests

Neue Funktionalität soll nach Möglichkeit durch Tests abgesichert werden.

Nach Änderungen:

* relevante Tests ausführen
* Build prüfen
* Fehler nicht einfach ignorieren
* bei fehlgeschlagenen Tests Ursache untersuchen

Wenn ein Test nicht sinnvoll automatisierbar ist, dies ausdrücklich erklären.

## Git

Git wird zur Nachvollziehbarkeit der Entwicklung verwendet.

Vor Änderungen:

* `git status` prüfen

Nach Änderungen:

* `git status` prüfen
* `git diff` prüfen
* relevante Tests ausführen

Keine Commits oder Pushes durchführen, außer der Benutzer fordert dies ausdrücklich an.

Keine Änderungen außerhalb des Projektverzeichnisses durchführen, sofern dies nicht ausdrücklich notwendig und vom Benutzer bestätigt ist.

## Datenschutz und Sicherheit

Die Anwendung kann potentiell sensible personenbezogene Informationen verarbeiten.

Deshalb:

* Daten nur speichern, wenn sie für einen definierten Zweck benötigt werden.
* Sensible Daten nicht in Logs schreiben.
* Keine sensiblen Daten in Fehlermeldungen oder Debug-Ausgaben ausgeben.
* Keine sensiblen Daten unnötig in Zwischenablagen, App-Vorschauen oder temporären Dateien halten.
* Lokale Speicherung muss gegen unbefugten Zugriff geschützt werden.
* Android Keystore für geeigneten Schlüsselschutz verwenden.
* Aufbewahrungs- und Löschfristen berücksichtigen.
* Datenlöschung muss tatsächlich überprüfbar sein.

Die Anwendung soll keine Funktionen zur Umgehung von Strafverfolgung, zur Täuschung nach einer Beschlagnahme oder zur Verschleierung rechtswidriger Handlungen implementieren.

Datensparsamkeit, Verschlüsselung, Zugriffsschutz und geplante Löschung sind ausdrücklich erwünscht.

## Netzwerk

Netzwerkzugriff ist standardmäßig nicht erforderlich.

Keine Netzwerkkommunikation hinzufügen, nur weil sie technisch bequem ist.

Insbesondere keine:

* Analytics
* Crash-Reporting-Dienste
* Werbe-SDKs
* unnötigen Cloud-Dienste
* automatischen Uploads
* versteckten Synchronisationsmechanismen

Wenn Netzwerkzugriff für eine konkrete Funktion erforderlich wäre, muss dies vor der Implementierung ausdrücklich begründet und abgestimmt werden.

## Abhängigkeiten

Vor dem Hinzufügen einer neuen Dependency prüfen:

* Ist sie wirklich notwendig?
* Gibt es bereits eine vorhandene Lösung?
* Ist sie aktiv gepflegt?
* Welche Berechtigungen oder Netzwerkzugriffe bringt sie mit?
* Gibt es Auswirkungen auf Datenschutz oder Sicherheit?
* Ist die Lizenz für das Projekt geeignet?

Keine Dependency nur für eine triviale Hilfsfunktion hinzufügen.

## UI und UX

Die Oberfläche soll:

* einfach
* verständlich
* ruhig
* konsistent
* auch für technisch unerfahrene Benutzer verständlich

sein.

Keine unnötig komplexen UI-Muster.

Bei fachlich wichtigen Entscheidungen lieber zuerst die vorhandene Produktdokumentation berücksichtigen als eigene Annahmen treffen.

## Umgang mit Unsicherheit

Wenn eine Anforderung unklar ist:

* nicht einfach eine weitreichende Annahme treffen
* die kleinste sinnvolle Interpretation wählen
* Unsicherheiten deutlich benennen
* bei sicherheitskritischen oder architektonischen Fragen nachfragen

## Prioritäten

Bei Zielkonflikten gilt grundsätzlich:

1. Sicherheit und Datenschutz
2. Korrektheit
3. Verständlichkeit und Wartbarkeit
4. Testbarkeit
5. Einfachheit
6. Komfort

## Ergebnis einer Coding-Aufgabe

Nach einer Coding-Aufgabe kurz berichten:

* Was wurde geändert?
* Welche Dateien wurden geändert?
* Welche Tests wurden ausgeführt?
* Gibt es offene Punkte oder bekannte Einschränkungen?

Keine unnötigen Änderungen am Projekt durchführen.
