# P11 bounded ACTIVE: isolierten Android-Lauf vorbereiten

Der getrennte feste Task-Modus und die korrigierte positive Fixture sind in
[TASKS.md](TASKS.md) dokumentiert. Dieser bestehende ACTIVE-Ablauf bleibt unverändert.

**Nur Vorbereitung. Keine Installation/Testausführung/Bereinigung ohne spätere
ausdrückliche Benutzerfreigabe.** Produkt und manuelle P11-App bleiben unangetastet.
Der Lauf dient genau den vier vorhandenen `BoundedActiveRoomTest`-Methoden;
keine Navigation, kein Benchmark, kein Orchestrator und kein Produktupdate.

## Offline vorbereiten und Artefakt prüfen

Vom Repositoryroot, vorhandene Toolchain und vorhandenen Keystore verwenden:

```sh
test -f .prototype-cache/debug.keystore
./gradlew :data:assembleDebugAndroidTest -I tools/p11bounded/prepare.init.gradle --offline
mkdir -p .prototype-cache/p11bounded-artifacts
cp data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk .prototype-cache/p11bounded-artifacts/bounded-room.apk
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/p11bounded -p 'test_*.py'
```

Ein Neubuild ist gegen sein eigenes neues Artefakt zu prüfen und gegebenenfalls
neu freizugeben; kein neuer Build überschreibt unbemerkt die spätere Freigabe.
Der jetzt eingefrorene APK-SHA256 lautet:

`c5a628f6b6b4a23dc1adeb4af99d136a428f76204e99c906a422e1de0f7ddf42`

`MANUAL_APK` bezeichnet weiterhin das separat eingefrorene manuelle UI-Artefakt;
es wird weder gebaut noch ersetzt. `BOUNDED_APK` ist ausschließlich das oben
eingefrorene `bounded-room.apk`. `AAPT`/`APKSIGNER` lokal auf die vorhandenen
SDK-Buildtools setzen. `CERT_SHA256` **unabhängig** aus dem bekannten lokalen
Keystore/Vertrauensnachweis beziehen, niemals aus der zu prüfenden APK übernehmen.
Der vorhandene lokale Keystore-Dateihash muss gegenüber seinem bekannten Nachweis
unverändert sein. Keine Schlüssel erzeugen, umstellen oder Credentials veröffentlichen.

```sh
python3 tools/p11bounded/verify_apk.py --apk "$BOUNDED_APK" \
  --sha256 c5a628f6b6b4a23dc1adeb4af99d136a428f76204e99c906a422e1de0f7ddf42 \
  --certificate-sha256 "$CERT_SHA256" --aapt "$AAPT" --apksigner "$APKSIGNER"
```

Der Prüfer ist rein lesend und besitzt keine Gerätefunktion. Er prüft kryptografisch
die Signatur, Manifestidentität, Komponenten-/Permissiongrenzen, Backup/Cleartext,
feste Testklassensymbole und enthaltenes ARM64-SQLCipher. Die DEX-Symbolprüfung ist
kein Ersatz für die spätere Runner-Ergebnisprüfung.

## Tatsächlich verifizierte APK-Identität

**Eine APK**, weil dies eine Library-Instrumentation ist:

| Eigenschaft | Tatsächliches APK-Manifest |
|---|---|
| APK-/Testpaket | `org.pag.data.unlockprobe` |
| Instrumentation-Target | `org.pag.data.unlockprobe` (Self-Target) |
| Runner | `androidx.test.runner.AndroidJUnitRunner` |
| Application | Standard `android.app.Application` (kein eigener Name) |
| Permissions / Queries | keine |
| Activities / Services / Provider / Receiver / Aliase | keine |
| Instrumentation | genau eine |
| Backup / Cleartext | deaktiviert |
| Debuggable | ja, isoliertes Testziel |

**Keine zweite Ziel-App installieren.** `:app:assembleDebug`, das allgemeine
Assessment-Probe-Overlay und dessen App-/Instrumentation-APKs gehören nicht zu
diesem Ablauf. Insbesondere `org.pag.prototype.a` und `org.pag.perf.p11probe`
sind weder Ziel noch zu installierende/zu entfernende Pakete.

## Reproduzierbarer Geräteplan – erst nach separater Freigabe

Die folgenden Schritte sind ausschließlich der spätere Freigabeumfang.
`ADB`/`TEST_SERIAL` lokal auf ein eindeutig identifiziertes, autorisiertes Samsung
setzen; Kennung und Rohlogs nicht veröffentlichen. Vor jeder Mutation:

1. APK-Gate erneut mit **dem freigegebenen Hash und unabhängigem Zertifikat** ausführen.
2. Genau ein autorisiertes Gerät auswählen, Hersteller/Modell und READY/ADB-Status
   prüfen. Nur Installationsmetadaten der Produkt- und manuellen Test-App lokal
   dokumentieren (Paketexistenz, Version, firstInstallTime, lastUpdateTime und
   Signaturmetadaten). Keine Produktdaten, Dateien oder Credentials lesen/kopieren.
3. Vorbestand für die drei betroffenen Paketnamen prüfen. **STOPP**, falls
   `org.pag.data.unlockprobe` bereits existiert; nicht ersetzen, löschen oder leeren.
   Der Plan erlaubt keine Wiederverwendung eines unbekannten Testdatenbestands.
4. Separate Freigabe muss ausdrücklich die Installation dieser einen eingefrorenen
   APK, die vier Methoden und gegebenenfalls die nachfolgende Entfernung genau
   dieses vorher fehlenden temporären Pakets umfassen. Keine implizite Bereinigung.

Nach dieser Freigabe nur die eine neue APK installieren, bewusst **ohne `-r`**:

```sh
"$ADB" -s "$TEST_SERIAL" install "$BOUNDED_APK"
```

Danach Paket-/Instrumentationidentität und unveränderte geschützte Apps prüfen.
Keine App im Launcher starten. Genau dieser Selektor, ein Methodenlauf je Eintrag:

```sh
TEST_SELECTION='org.pag.data.product.BoundedActiveRoomTest#boundedProjectionEqualsExistingOperationsAndActionReaders,org.pag.data.product.BoundedActiveRoomTest#preparationCompletedAndMissingAreRejected,org.pag.data.product.BoundedActiveRoomTest#finalPublicationDenialAndRevokedSessionReturnNoPayload,org.pag.data.product.BoundedActiveRoomTest#repeatReadClosesConnectionsAndDoesNotChangeProjection'
"$ADB" -s "$TEST_SERIAL" shell am instrument -w -r \
  -e class "$TEST_SELECTION" \
  org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
```

Keine `connectedAndroidTest`-/Gradle-Installtasks, kein unbeschränkter Klassenlauf,
keine Suite-/Packageauswahl, kein Retry bis grün, keine weitere Instrumentation.
Rohstream ausschließlich lokal aufbewahren. Vier exakte Methodennamen, jeweils
ein Start-/Endergebnis, insgesamt **4**, null Fehler/Skips und regulären Abschluss
abgleichen. Bloßer Prozessstart oder Instrumentation-Endcode ist kein PASS.
Bei Fehler/Nichtzählbarkeit Originalbefund behalten, nicht Assertions ändern.

Die Fixture erzeugt ausschließlich erfundene Daten, einen zufälligen In-Memory-
Schlüssel und einen eigenen verschlüsselten `p11-fixture-*`-Unterbestand unter
den privaten noBackupFiles des Testpakets. `use`/`close` widerruft den Keyowner,
überschreibt das kontrollierte Keyarray und entfernt nur diesen Unterbestand.
Der Boolean-Rückgabewert von `deleteRecursively()` wird in der vorhandenen Fixture
nicht geprüft; erfolgreiche Verzeichnisentfernung ist damit kein eigener Testnachweis.
Prozesscrash kann Reste im **temporären Testpaket** hinterlassen. Das ist keine
Erlaubnis für `pm clear` oder Zugriffe auf Produkt-/manuelle Appdaten.

Nur wenn ausdrücklich freigegeben, das Paket in diesem Lauf neu installiert und
seine Identität unverändert ist, anschließend ausschließlich:

```sh
"$ADB" -s "$TEST_SERIAL" uninstall org.pag.data.unlockprobe
```

Ohne Bereinigungsfreigabe bleibt das Paket bestehen und wird als offen berichtet.
Nachher Paketbestand und Installations-/Signaturmetadaten von Produkt und manueller
P11-App mit dem Vorbestand vergleichen. Keine anderen Pakete entfernen, keine
manuelle Test-App zurücksetzen, keine Schlüssel ändern. APK, Logs, Gerätedetails
und Zertifikats-/Keystoredateien bleiben lokal.

## Aussagegrenzen und konkreter statischer BLOCKER

Room 3.0.3 implementiert `withReadTransaction` über DEFERRED und `usePrepared`
über `delegate.prepare(...).use`. Das tatsächliche SQLCipher-4.19.1-Artefakt zeigt:
`SQLCipherStatement.close()` setzt nur `closed=true`; `reset()` ruft dagegen
`cursor.close()` auf. B2Driver delegiert Statement-close. Zwei Hosttests gegen
die echte Dependencyklasse bestätigen Close-/Bindingverhalten ohne native DB.
**Keine Produktkorrektur in diesem Auftrag.** Expliziter Cursor-Close am Ende
von usePrepared ist deshalb nicht belegt; Endeffekt des DB-/Driver-Cleanup und
FD-/CursorWindow-Verhalten müssen getrennt am Gerät beurteilt werden.

Die vier unveränderten Fälle verwenden 20 synthetische Personen und keine Rollen/
Planungen. Sie prüfen weder nichtleere Rollenpfade noch >32-Zeilen-Folgebatches,
NUL-/Unicode-/Übergrößenfälle auf Android. Deren Hostnachweise bleiben getrennt.
Auch vier Laufzeit-PASS würden keinen allgemeinen Android-/Security-/Budgetnachweis
oder eine Snapshot-/Navigationsfreigabe darstellen.
