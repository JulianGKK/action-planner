# P11: manuelle, isolierte UI-Abnahme

Vorbereiteter WIP-Stand, **noch nicht auf dem Gerät installiert oder manuell
abgenommen**. Erst unabhängiger GitHub-Review, dann ausdrückliche
Installationsfreigabe. Historische und neue P11-Funktionsfehler bleiben offen.

## Aufbau und Grenzen

Der vorhandene P11-Init-Build und dessen Paket `org.pag.perf.p11probe` werden
weiterverwendet. Ein explizites Manifest-Overlay ersetzt den produktiven Launcher
und die produktive Application durch `P11ManualActivity` und
`android.app.Application`. Es gibt genau eine Activity und keinen Service,
Provider oder Receiver. Kamera-/Biometrieberechtigungen entfallen; keine
Netzwerkberechtigung. Kein Zugriff auf Produktdatenbank, Keystore oder reale
Authentifizierung. Keine neue Dependency und keine Änderung unter `src/main`.

Der Bildschirm ist der aktuelle `ProductScreen` mit `ProductViewModel` und den
aus der bisherigen Funktionsprobe gemeinsam genutzten In-Memory-Ports. Die
manuelle Personenliste enthält Motte und Klee; die automatische Probe behält
ihre bisherige Personenlisten-Konfiguration. Beide Personen können im manuellen
Funktionspfad Funktion und Lage wechseln. Bestätigungsbanner, Scrollen und interne
Navigation kommen unverändert aus dem Produktcode. Kein UIAutomation-Client,
Refresh, Diagnosepuffer oder automatischer Bedienablauf läuft mit.

Nur die Funktionsnavigation ist vollständig für diesen Durchlauf vorbereitet;
andere Fachbereiche sind keine manuelle Vollsimulation. Insbesondere sind
Aufgaben nur als leeres Board vorhanden, Dokumente und sensible Bereiche nicht
angebunden. Dort keine Schreibabläufe testen. Ausschließlich erfundene Daten
eingeben. Die App ist ein Debug-Testziel, keine sichere Produktinstallation.

Der Teststart bietet Schriftfaktoren 1, 2 und 3,5 sowie Datenreset. Android-Zurück
öffnet während des Tests eine Rückfrage zum Teststart; für die fachliche
Rücknavigation die sichtbaren Produktknöpfe benutzen. Während `ProductScreen`
läuft, verkleinert keine zusätzliche Testleiste den Viewport. Schriftwechsel am
Teststart erhält dieselbe Activity und dieselben Daten/Modelle, baut aber die
Composition neu auf: kein Test unveränderter Scroll-/Fokuspositionen.
Reset erzeugt neue Ports und ein neues Modell und räumt das alte Modell über
seinen ViewModel-Lifecycle auf. Activity-Neustart/Rotation/Prozesstod verwirft alle
Daten. Sperren/Entsperren ist rein synthetisch; echte Sessionfristen und
Security-/Credential-Verhalten werden damit nicht geprüft.

## Nur vorbereiten und prüfen (kein Gerät nötig)

Vom Repositoryroot, vorhandene lokale Toolchain und vorhandenen Debug-Keystore
verwenden. Keinen neuen Signaturschlüssel erzeugen oder austauschen.

```sh
test -f .prototype-cache/debug.keystore
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --tests '*P11FunctionsFixtureTest' -I tools/p11manual/probe.init.gradle --offline
mkdir -p .prototype-cache/p11manual-artifacts
cp app/build/outputs/apk/debug/app-debug.apk .prototype-cache/p11manual-artifacts/manual.apk
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/p11manual -p 'test_*.py'
```

Das lokale Einfrieren verhindert Verwechslungen mit späteren gewöhnlichen
Debug-/P11-Builds, die denselben Outputpfad verwenden. Keine `install*`- oder
`connected*`-Gradle-Tasks verwenden. Die automatische Instrumentation-APK wird
für den manuellen Test nicht benötigt und nicht installiert.

Vor jeder eventuellen Installation den Zertifikatsfingerprint unabhängig aus dem
bereits bekannten lokalen Keystore beziehen, beispielsweise interaktiv mit
`keytool -list -v -keystore .prototype-cache/debug.keystore -alias androiddebugkey`.
Passwörter weder in Befehlszeilen/Dateien noch in Git aufnehmen. SHA256 des
Zertifikats lokal als `CERT_SHA256` setzen; nicht erst aus der zu prüfenden APK
als vermeintlichen Vertrauensanker übernehmen. `AAPT` und `APKSIGNER` auf die
lokalen SDK-Buildtools setzen. `MANUAL_APK` ist der eingefrorene lokale Pfad oben.

```sh
python3 tools/p11manual/verify_apk.py --apk "$MANUAL_APK" \
  --aapt "$AAPT" --apksigner "$APKSIGNER" --certificate-sha256 "$CERT_SHA256"
```

Der Prüfer hat **keine Installationsfunktion**. Er prüft die exakte Paketkennung,
den manuellen Launcher, Application, Komponentengrenzen, Berechtigungen,
Backup-/Cleartextverbote und kryptografisch die Signatur gegen das erwartete
Zertifikat. Er meldet den APK-SHA256. Bei jeder Abweichung stoppen. APK, Logs,
Zertifikats-/Keystoredateien und Gerätekennungen ausschließlich lokal behalten.

## Installation erst nach separater Freigabe

Diese Befehle jetzt **nicht** ausführen. Nach Review und ausdrücklicher Freigabe:
genau ein gewünschtes Testgerät lokal als `TEST_SERIAL` wählen, dasselbe
eingefrorene APK erneut mit obigem Prüfer kontrollieren und seinen Hash mit dem
freigegebenen Artefakt vergleichen. Keine andere APK und kein Produktpaket
installieren. `ADB` bezeichnet das lokale adb-Programm.

```sh
python3 tools/p11manual/verify_apk.py --apk "$MANUAL_APK" \
  --aapt "$AAPT" --apksigner "$APKSIGNER" --certificate-sha256 "$CERT_SHA256" && \
  "$ADB" -s "$TEST_SERIAL" install -r "$MANUAL_APK"
```

Anschließend ausschließlich **„PaG P11 UI-Test“** im Launcher öffnen, alternativ:

```sh
"$ADB" -s "$TEST_SERIAL" shell am start -n org.pag.perf.p11probe/org.pag.prototype.P11ManualActivity
```

Bei Signaturkonflikt oder unerwartetem Paket stoppen; keine Schlüssel wechseln,
keine Pakete pauschal löschen, kein `pm clear`. Das Produktpaket
`org.pag.prototype.a` bleibt unangetastet. Bereits entfernte alte Testpakete
nicht wiederherstellen. Nach ausdrücklicher späterer Bereinigungsfreigabe kann
ausschließlich `org.pag.perf.p11probe` entfernt werden.

## Kurzer manueller Funktionsdurchlauf

1. Am Teststart **Synthetische Daten zurücksetzen**, Schriftfaktor **1** wählen,
   dann **Oberfläche öffnen / fortsetzen**. Übungsaktion/Lagebild und zwei
   Personen vor Ort kontrollieren.
2. **Funktionen → Support · 1 → Motte** öffnen. Aktuelle Funktion Support prüfen.
   **Funktion ändern → Blockierende → Übernehmen**. Bestätigungsbanner und neue
   Funktion prüfen; insbesondere verdeckte Bedienelemente beobachten.
3. Über **‹ Support** zur vorherigen Gruppe zurück: nun leer. Über **‹ Funktionen**
   zur Übersicht: Blockierende mit einer Person. **Ohne Funktion · 1 → Klee**
   öffnen; keine aktuelle Funktion. Optional Klee Support zuweisen und erneut
   zurücknavigieren; für den folgenden Ausgangsfall vorher resetten.
4. **Blockierende · 1 → Motte → Lage ändern → In Gewahrsam → Übernehmen**.
   Neue Lage, Banner und Entfernung der aktuellen Funktion prüfen. Über die
   sichtbaren Rückknöpfe zur Funktionsübersicht, dann **‹ Zum Lagebild**.
   Überschrift und **Noch keine aktuellen Funktionen** müssen erreichbar sein.
   Auch langsam in beide Richtungen scrollen; nichts durch Testautomation suchen.
5. Android-Zurück → **Zum Teststart**, Datenreset und denselben Durchlauf bei
   **2**, anschließend **3,5** ausführen. Zusätzlich Schrift **1→2** ohne
   Datenreset in derselben Activity wechseln und erhaltene Daten kontrollieren.
6. TalkBack über Android aktivieren und die Schritte mit Wischfokus,
   Doppeltippen und TalkBack-Scrollgesten wiederholen. Namen, Fokusreihenfolge,
   Erreichbarkeit, Rücknavigation und Banneransage prüfen. Teststart,
   Schriftwahl, Rückfrage und Reset ebenfalls bedienen. Keine Accessibility-
   Automatisierung parallel starten.

Ergebnis je Schrift/TalkBack-Konfiguration getrennt notieren: PASS oder konkrete
Unterphase, erwarteter/tatsächlicher Zustand, Scrollrichtung und Banner-/Fokus-
beobachtung. Fehlversuche erhalten; späterer Erfolg ersetzt sie nicht. Keine
Gerätekennung, echten Daten oder unbereinigten Screenshots/Logs veröffentlichen.
Ein fehlender Fehler in diesen manuellen Durchläufen beweist keine allgemeine
Fehlerfreiheit. TalkBack-Bedienbarkeit ist vorbereitet, aber erst am Gerät manuell
zu bestätigen.
