# P11 bounded Tasks: isolierte Android-Laufzeitvorbereitung

**Kein Gerätezugriff in diesem Auftrag. Installation, einmaliger Testlauf und
optionale Entfernung benötigen eine neue ausdrückliche Freigabe.**

**Bekannter Fixture-BLOCKER:** Die unveränderte 100-Task-Fixture überschreitet
bereits ohne übergroße Templatekennung das Übergangsbudget. Dieses Artefakt ist
kein erwartbar grüner Vier-Test-Abnahmelauf. Vor einer Gerätefreigabe muss dieser
Befund ausdrücklich bewertet werden; ein diagnostischer Lauf wäre nur mit
Kenntnis dieser Grenze sinnvoll. Keine Assertions, Fixture oder Budgets still ändern.

## Eingefrorenes Artefakt und unabhängiges Gate

Basis: `25d988e3186f56c828b03bdf82ae731b7957e97d`, Branch
`p11-performance-optimization`. Die vier Androidmethoden sind unverändert.
Genau ein isolierter Data-Instrumentation-Build wurde ausgeführt:

```sh
./gradlew :data:assembleDebugAndroidTest -I tools/p11bounded/prepare.init.gradle --offline
```

Gebautes Original: `data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`.
Separat eingefrorene Kopie: `.prototype-cache/p11bounded-task-artifacts/bounded-tasks-room.apk`.
Das ältere ACTIVE-Artefakt `bounded-room.apk` bleibt unverändert separat erhalten.
Ein Neubuild ist ein neues Artefakt und benötigt erneute Verifikation/Freigabe;
die eingefrorene Kopie nicht überschreiben. APKs und Rohlogs nicht in Git aufnehmen.

Task-APK-SHA256:
`72990b3d301fc80c5663154a7e5e41491b718c6c059effbe5120c9185b8f986d`.

Tatsächliches AAPT-Manifest: Paket/Test/Instrumentation-Target jeweils
`org.pag.data.unlockprobe`, Self-Target, genau eine Instrumentation mit
`androidx.test.runner.AndroidJUnitRunner`. Standard `android.app.Application`,
Label `PaG P11 Bounded Tests`, debuggable, Backup und Cleartext deaktiviert,
keine Permissions/Queries/Activities/Services/Provider/Receiver/Aliase.
ARM64 `libsqlcipher.so` und Symbole aller vier Taskmethoden vorhanden.
Die APK enthält auch andere Testklassen; ausschließlich der feste Selektor
begrenzt den späteren Lauf. DEX-Symbolpräsenz ist kein Test-PASS.

`TASK_APK` lokal auf die eingefrorene Kopie setzen, `AAPT`/`APKSIGNER` auf die
vorhandenen SDK-Buildtools. `CERT_SHA256` ausschließlich aus dem bereits bekannten
unabhängigen Keystore-/Vertrauensnachweis beziehen, niemals aus dieser APK.
Keystore-Dateihash gegenüber diesem Nachweis prüfen; keine Schlüssel erzeugen.

```sh
TASK_SELECTION='org.pag.data.product.BoundedTaskRoomTest#boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews,org.pag.data.product.BoundedTaskRoomTest#nonActiveAndMissingAreDenied,org.pag.data.product.BoundedTaskRoomTest#publicationAndRevokeCannotReturnTaskPayload,org.pag.data.product.BoundedTaskRoomTest#oversizeTemplateRefusedAndRepeatedReadsRemainConsistent'
python3 tools/p11bounded/verify_apk.py --mode tasks --selection "$TASK_SELECTION" \
  --apk "$TASK_APK" \
  --sha256 72990b3d301fc80c5663154a7e5e41491b718c6c059effbe5120c9185b8f986d \
  --certificate-sha256 "$CERT_SHA256" --aapt "$AAPT" --apksigner "$APKSIGNER"
```

Der Modus akzeptiert keine andere Auswahl, zusätzliche Methoden, Klassenläufe oder
gekürzte Selektoren. Ohne `--mode` gilt weiterhin unverändert der alte ACTIVE-Modus.
Beide Modi behalten dieselben engen Manifest-/Signatur-/Native-Gates.

## Vorbereiteter einmaliger Ablauf – ausschließlich nach neuer Freigabe

1. Bekannten Fixture-BLOCKER und diagnostische Aussagegrenze ausdrücklich klären.
   Kein grünes Ergebnis unterstellen. Fehlende Freigabe bedeutet STOPP.
2. APK-Gate mit exakt freigegebenem Hash, unabhängigem Zertifikat und Taskselektor
   erneut ausführen. Bei fehlender APK, Hash-/Signatur-/Manifest-/Symbol-/
   Selektorabweichung STOPP, kein Ersatzbuild oder Fallback.
3. Eindeutig autorisiertes Samsung lokal auswählen (`ADB`, `TEST_SERIAL`). Nur
   Paket-/Installationsmetadaten von `org.pag.prototype.a`, `org.pag.perf.p11probe`
   und `org.pag.data.unlockprobe` erfassen: Existenz, Version, firstInstallTime,
   lastUpdateTime, Signaturinformationen. Keine Appdaten/DBs/Credentials lesen.
4. Falls `org.pag.data.unlockprobe` schon vorhanden ist: STOPP. Kein Ersetzen,
   Deinstallieren oder Leeren des Vorbestands. Freigabe muss Installation dieser
   einen APK und diesen einen Vier-Methoden-Lauf explizit umfassen; Entfernung
   ausschließlich des in diesem Lauf neu installierten Pakets gesondert einschließen.
5. Nur die eine APK installieren, ohne `-r`, keine zweite Ziel-App:

```sh
"$ADB" -s "$TEST_SERIAL" install "$TASK_APK"
```

Installierte APK-/Signaturidentität und Instrumentation-Self-Target erneut prüfen;
Metadaten beider geschützten Apps müssen unverändert sein. Kein Launcherstart.
Genau ein Instrumentationaufruf mit dem oben vollständig angegebenen Selektor:

```sh
"$ADB" -s "$TEST_SERIAL" shell am instrument -w -r \
  -e class "$TASK_SELECTION" \
  org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
```

Keine andere Klasse/Suite, kein `connectedAndroidTest`, keine automatischen
Wiederholungen und keine Assertionänderungen. Je exakter Methode ein Start-/
Endereignis, tatsächlich ausgeführt und PASS/FAIL/ERROR/SKIP erfassen, insgesamt
exakt vier. Runnerabschluss und Abbruch/SQLCipher-/Roomfehler gesondert prüfen.
Bei einem Fehler Originalbefund erhalten; kein Neubuild/Retry bis grün.

Nachher ausschließlich bei ausdrücklich eingeschlossener Bereinigung, nachgewiesener
Neuinstallation und erneut identischer Paketidentität:

```sh
"$ADB" -s "$TEST_SERIAL" uninstall org.pag.data.unlockprobe
```

Andernfalls Paket unangetastet lassen und berichten. Abwesenheit des temporären
Pakets sowie unveränderte Versionen/Installationszeiten/Signaturen der Produkt-
und manuellen P11-App prüfen. Kein `pm clear` und keine pauschale Bereinigung.
Gerätekennungen, lokale Pfade, APKs und Rohstream ausschließlich lokal aufbewahren.

## Belegte Testvoraussetzungen und erwartete Grenzen

`P11SyntheticFixture` erstellt 100 ACTIVE-Tasks mit je einem CREATED-Event.
UUIDs sind jeweils 36 ASCII-Bytes, Titel `Synthetic task 0` bis `Synthetic task 99`,
keine Assignees/Betroffenen-/Templatewerte. Die gültige OPEN→IN_PROGRESS-Transition
im ersten Test erzeugt genau ein STARTED-Event: fachlich 100 Tasks / 101 Events.
Der Übergang und die Zählannahme sind fachlich zulässig.

Nach der **aktuellen unveränderten** Budgetformel:

| Bestand | Rows inkl. Action | Textfelder inkl. nullable | UTF8-Bytes | Transition-Charge |
|---|---:|---:|---:|---:|
| 100 Tasks / 100 Events | 201 | 1305 | 16250 | 8410240 Bytes |
| 100 Tasks / 101 Events, eine IN_PROGRESS | 202 | 1309 | 16346 | 8447616 Bytes |

Limit: 8388608 Bytes. Formel aus Reader/Engineeringbudget:
`rows * 20608 + fields * 2272 + bytes * 80 + 3072` (checked Long).
Zwei neue Hosttests gegen echtes SQLite/schema 11 bestätigen baseline/Transition
als BUDGET_REFUSED **vor Payloadquery/BLOB-Fetch**, ohne Storage-BLOCKED.
Die zulässige Templatekennung mit 200000 `x` wird von TaskRules/TaskRow akzeptiert;
der vorhandene Titel bleibt innerhalb 120 Codepoints. Es ist keine echte
Credential und kein erlaubter Grund für eine Budgeterhöhung.

Folgen für die unveränderten Androidtests, **keine ausgeführten Androidresultate**:

- Methode 1 erwartet Value nach Transition; statisch/auf Host ist Budgetverweigerung
  belegt. Ein Castfehler wäre deshalb ein Testvoraussetzungsproblem.
- Methode 2 prüft ausschließlich Ablehnungen; kein Value-Voraussetzungsproblem
  aus dieser Budgetrechnung abgeleitet, Androidausführung weiterhin offen.
- Methode 3 erwartet zunächst ACCESS_REVOKED bei Publicationverweigerung; die
  frühere Budgetverweigerung erreicht dieses Gate nicht. Erwartete Assertion
  passt damit nicht zum aktuellen Fixturebestand.
- Methode 4 kann schon identische Rejected-Ergebnisse vergleichen. Der FD-Check
  wäre kein erfolgreicher vollständiger Taskboard-Read-Nachweis; die spätere
  Budgetverweigerung isoliert nicht die übergroße Templatekennung als Ursache.

Diese Befunde korrigieren keine Produktion und schwächen keine Assertions ab.
Ein späterer sinnvoller grüner Abnahmelauf benötigt eine separat geprüfte
Testvoraussetzungsentscheidung. Keine Android-PASS behaupten.

## Ressourcen-/Cleanupgrenzen

Die Fixture verwendet ausschließlich synthetische Daten und flüchtige Schlüssel
im privaten temporären Testpaketbestand; kein Zugriff auf Produkt-/manuelle App.
Fixture-close widerruft den Owner und überschreibt das kontrollierte Keyarray;
Verzeichnislöschung prüft ihren Boolean nicht. Prozessabbruch kann Testreste
hinterlassen. Cleanup-/FD-/Leaseaussagen müssen später einzeln belegt werden.

Der bekannte **BLOCKER** bleibt: `SQLCipherStatement.close()` schließt seinen
Cursor nicht ausdrücklich. `usePrepared`-/Transaktionsende oder bestandene
FD-Toleranz `nachher <= vorher + 2` beweisen kein Statement-Cursor-Close oder
langfristige CursorWindow-/FD-Freiheit. DB-/Driver-/Lease-Cleanup, FD-Beobachtung
und offene Ressourcenlebensdauer getrennt bewerten. Kein SQLCipher-/B2Driverfix,
keine Snapshotfreigabe, Navigationseinbindung oder Performanceaussage.
