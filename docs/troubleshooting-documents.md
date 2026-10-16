# Dokumentenimport — Hinweise für FAQ und Troubleshooting

Stand: P10b2e. Grundlage ist die tatsächliche Abnahme auf Samsung SM-M526BR,
Android API 33; andere Geräte sind dadurch nicht automatisch geprüft.
Technische Nachweise: [P10b2e](product-p10b2e-android-document-import-fix.md).

## Welche Entwicklerfunktionen müssen aktiviert oder deaktiviert sein?

| Einstellung/Funktion | Für normalen Dokumentenimport |
| --- | --- |
| Android-Entwickleroptionen insgesamt | Müssen nicht aktiviert sein. |
| „Aktivitäten nicht speichern“ innerhalb der Entwickleroptionen | **Muss deaktiviert sein**, damit der Activity-RAM-Handoff über den Systempicker erhalten bleibt. |
| USB-Debugging / ADB | Nicht erforderlich. Wurde ausschließlich für Entwicklung, Diagnose und Gerätetests verwendet. |
| PaG-Diagnoselogs | Nicht erforderlich. Die temporäre Picker-Probe ist entfernt; temporäre Loglevel wurden zurückgesetzt. Die verbleibende Maintenance-Probe ist auf debuggable Builds begrenzt. |

Es ist keine weitere Entwickleroption als erforderliche Import-Einstellung
nachgewiesen. Nicht pauschal andere Geräteeinstellungen ändern. PaGs Sperre,
Credential-Authentifizierung und Sessionfristen bleiben unverändert.

## Nach der Dateiauswahl fehlt „Import fortsetzen“

PaG sperrt sich beim Öffnen der Android-Systemdateiauswahl. Das ist vorgesehen.
Nach Auswahl der Datei:

1. PaG regulär erneut entsperren.
2. Dieselbe Aktion bewusst öffnen und dort „Dokumente“ öffnen.
3. „Import fortsetzen“ antippen, Namen und Format angeben.
4. Mit „Import bestätigen“ den lokalen verschlüsselten Import starten.

Es erfolgt kein automatischer Import direkt nach der Dateiauswahl.

Wenn die Fortsetzung fehlt, prüfen, ob Android PaG zwischenzeitlich vollständig
beendet hat. Insbesondere **Einstellungen → Entwickleroptionen → „Aktivitäten
nicht speichern“** kontrollieren. Die genaue Menübezeichnung kann je nach Gerät
abweichen. Diese Option beendet die Activity beim Wechsel zum Dateipicker und
verwirft damit die ausschließlich im Activity-RAM gehaltene Übergabe.

Auf dem Test-Samsung war ein Sonderfall nachgewiesen: Der per ADB gespeicherte
Wert stand bereits auf „aus“, während der laufende ActivityManager die Option
weiterhin aktiv hatte. Erst das einmalige Ein- und direkte Wiederausschalten
**im regulären Android-Einstellungsdialog** deaktivierte sie tatsächlich. Das
ist eine gezielte Korrektur dieses Diagnosefalls, kein notwendiger Schritt vor
jedem Import. Keine versteckten Systemaufrufe oder Änderung der App-Sperre nötig.

Eine bereits verlorene Auswahl muss neu erfolgen. Auch bei deaktivierter
Entwickleroption kann Android eine Activity oder einen Prozess beenden. PaG
persistiert dafür absichtlich keine URI und stellt die Auswahl nicht nach
App-Neustart wieder her. Nach der neuen READY-Sitzung die Auswahl bewusst
bestätigen; eine weitere Sperre verwirft die noch nicht verbrauchte Übergabe.

## Der Testordner ist leer

Bei den per ADB erzeugten synthetischen Dateien waren die Dateien im
Dateisystem vorhanden, wurden im Systempicker aber zunächst nicht angezeigt.
Nach gezielter Android-Neuerfassung wurden alle vier mit ihren korrekten
MIME-Typen im Dateiindex bestätigt und anschließend erfolgreich importiert.
Die vorherige Indexlage wurde nicht erfasst; ein zuvor fehlender Indexeintrag
ist keine bewiesene Ursache.

Den Picker schließen und neu öffnen. Für diesen Test liegen die Dateien unter
**Interner Speicher → Download → PaG-P10b2e-synthetic**. Unterstützt werden PDF,
JPEG, PNG und UTF-8-Klartext. Für normalen Import sind weder ADB noch eine breite
Speicherberechtigung oder ein eigener Scanpfad der App erforderlich.

## Kann ich gespeicherte Dokumente schon anzeigen?

Noch nicht. P10b2e unterstützt Import, Liste und bestätigtes Löschen der
verschlüsselten PaG-Kopie. PDF-, Bild- und Textviewer sind nicht implementiert.
Die Originaldatei außerhalb von PaG bleibt beim Import und beim Löschen der
PaG-Kopie unverändert bestehen.

## Direkte Kameraaufnahme (P10d)

„Foto aufnehmen“ öffnet die interne Kamera; „Dokument importieren“ behält den
bisherigen Systempicker-Ablauf. Kamera ist in PREPARATION, ACTIVE und nach
bewusstem Einstieg COMPLETED verfügbar. Keine Dokumenten-/Fotoanzeige in P10d.

Beim ersten Kamerastart fordert Android ausschließlich CAMERA an. Falls dabei
PaG gesperrt wird: regulär neu authentifizieren, dieselbe Aktion/Dokumente öffnen
und „Foto aufnehmen“ bewusst erneut wählen. Eine Berechtigungsantwort startet
niemals automatisch die Kamera. Bei dauerhafter Ablehnung unter Android-
App-Einstellungen → PaG → Berechtigungen die Kamera erlauben; keine Galerie-,
Speicher-, Mikrofon-, Standort- oder Netzwerkberechtigung hinzufügen.

- Keine Kamera / Kamera belegt / Startfehler: Fehlermeldung beachten, den
  Kameraeinstieg abbrechen und später bewusst neu öffnen; SAF bleibt verfügbar.
- Foto aufgenommen: „Aufnahme übernehmen“, Namen vergeben und ausdrücklich
  „Verschlüsselt speichern“. Auslösen allein speichert noch nichts.
- „Erneut aufnehmen“ und „Abbrechen“ verwerfen die alte Aufnahme und den Namen.
- Lock, Sessionablauf, App-Verlassen, Aktions-/Bereichswechsel oder Neustart:
  Kamera wird geschlossen, noch nicht gespeichertes Foto wird verworfen.
  Kein automatisches Wiederöffnen nach Entsperrung. Keine Sessionverlängerung.
- Über 25 MiB, unzulässiger JPEG-/Pixelheader oder nicht zuverlässig übernehmbare
  Orientierung: Aufnahme wird verworfen; keine Teilübernahme oder automatische
  Bildneukodierung. Kamerabilder sind zusätzlich auf 16 Mio. Pixel begrenzt.
- Unterbrochener verschlüsselter Import: regulär sperren und neu entsperren,
  damit bestehende Maintenance abgleichen kann; nicht blind erneut speichern.

Die interne Kamera erfordert keine aktivierten Entwickleroptionen oder
USB-Debugging. Der obige Hinweis „Aktivitäten nicht speichern“ bleibt für
RAM-Lebensdauer und insbesondere den externen SAF-Picker wichtig. Das Deaktivieren
dieser Android-Entwickleroption hebt PaGs eigene App-Sperre nicht auf.
Details und tatsächliche Testgrenzen: [P10d](product-p10d-camera-import.md).
