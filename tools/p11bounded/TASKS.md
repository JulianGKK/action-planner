# P11 bounded Tasks: isolierte Android-Laufzeitvorbereitung

**Kein Gerätezugriff in diesem Auftrag. Installation, einmaliger Testlauf und
optionale Entfernung benötigen eine neue ausdrückliche Freigabe.**

Die korrigierte positive Fixture verwendet explizit 40 Tasks/40 CREATED-Events,
nach Transition 40 Tasks/41 Events. Ihre Zulassung ist auf dem Host nachgewiesen;
ein Android-PASS steht aus. Cursor-/CursorWindow-Lebensdauer und gemeinsame
Snapshotintegration bleiben BLOCKER. Historischer 100-Task-Budgetnachweis bleibt erhalten.

## Eingefrorenes Artefakt und unabhängiges Gate

Basis: `6e6b0cf21436c8511ec4ed218f799688a437535f`, Branch
`p11-performance-optimization`. Die vier Androidmethodennamen sind unverändert; Assertions prüfen jetzt erfolgreiche
40-Task-Boards vor und nach den gezielten Testereignissen.
Genau ein isolierter Data-Instrumentation-Build wurde ausgeführt:

```sh
./gradlew :data:assembleDebugAndroidTest -I tools/p11bounded/prepare.init.gradle --offline
```

Gebautes Original: `data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`.
Separat eingefrorene Kopie: `.prototype-cache/p11bounded-task-corrected-artifacts/bounded-tasks-room-corrected.apk`.
Die älteren Task- und ACTIVE-Artefakte bleiben unverändert separat erhalten.
Insbesondere das gesperrte 100-Task-Artefakt mit SHA256
`72990b3d301fc80c5663154a7e5e41491b718c6c059effbe5120c9185b8f986d`
wurde nicht ersetzt und ist nicht dieses neue Artefakt.
Ein Neubuild ist ein neues Artefakt und benötigt erneute Verifikation/Freigabe;
die eingefrorene Kopie nicht überschreiben. APKs und Rohlogs nicht in Git aufnehmen.

Task-APK-SHA256:
`e2965c50aea142da8157bd914964b0a9388bbb861fbd739df26dd04d8c2bf115`.

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
  --sha256 e2965c50aea142da8157bd914964b0a9388bbb861fbd739df26dd04d8c2bf115 \
  --certificate-sha256 "$CERT_SHA256" --aapt "$AAPT" --apksigner "$APKSIGNER"
```

Der Modus akzeptiert keine andere Auswahl, zusätzliche Methoden, Klassenläufe oder
gekürzte Selektoren. Ohne `--mode` gilt weiterhin unverändert der alte ACTIVE-Modus.
Beide Modi behalten dieselben engen Manifest-/Signatur-/Native-Gates.

## Vorbereiteter einmaliger Ablauf – ausschließlich nach neuer Freigabe

1. Unabhängigen Review und neue ausdrückliche Freigabe des neuen Hashes und
   Vier-Methoden-Laufs abwarten. Offene Ressourcenfragen anerkennen; kein Android-
   PASS unterstellen. Fehlende Freigabe bedeutet STOPP.
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

## Korrigierte Voraussetzungen und getrennte Negativnachweise

`P11SyntheticFixture(..., taskCountPerAction = 40)` wird ausschließlich in den
vier Taskmethoden explizit verwendet. Default 100 für bestehende Aufrufer bleibt
unverändert. Vor Transition: 40 Tasks/40 CREATED-Events; zulässige
OPEN→IN_PROGRESS-Transition: genau ein zusätzliches STARTED-Event. UUIDs haben
36 ASCII-Bytes; alle Feld-/Relation-/Budgetregeln bleiben unverändert.

| Zustand | Rows inkl. Action | Textfelder inkl. nullable | UTF8-Bytes | Retained-Charge | Transition-Charge |
|---|---:|---:|---:|---:|---:|
| 40 Tasks / 40 Events | 81 | 525 | 6530 | 849184 | 3387520 |
| 40 Tasks / 41 Events | 82 | 529 | 6626 | 858528 | 3424896 |

Beide Repräsentationen liegen unter ihrem jeweiligen 8388608-Byte-Limit.
Dies sind Engineering-Charges, keine gemessenen ART-/Native-Heapgrößen.

- Methode 1 verlangt vor und nach Transition vollständige Values und Vergleich
  mit echtem TaskRepository: Inhalte, gesamte geordnete Eventliste und abgeleitete
  Ansichten. Counts 40/40 und 40/41 erzwingen je zwei Task-/Eventbatches; alle
  vorherigen Events bleiben erhalten, genau ein STARTED für den betroffenen Task.
- Methode 2 behält Ablehnung von Nicht-ACTIVE/fehlend bei.
- Methode 3 verlangt vorher/nachher positive Reads. Ein expliziter Testcallback
  zählt das tatsächliche Erreichen der Publicationgrenze; Budgetverweigerung
  genügt nicht. Nach Revoke kein weiterer Publicationversuch.
- Methode 4 verlangt erfolgreiche vollständige Reads vor Mutation und bei allen
  drei Wiederholungen. Erst nach erfolgreichem Einfügen der fachlich zulässigen
  200000-`x`-Templatekennung folgt BUDGET_REFUSED. FD-Toleranz vergleicht somit
  vollständige Reads, keine Rejected-Werte.

Fünf Fixture-Hostmethoden prüfen auf echtem SQLite/schema 11 die kleinen positiven
Bestände, vollständige Historie/mehrere Batches, Transition/Event genau einmal,
isolierte Übergröße, Publication/Revoke sowie die getrennte große negative Fixture.
Der unveränderte Negativfall bleibt 100 Tasks/100 bzw. 101 Events:
Transition-Charges 8410240 bzw. 8447616 Bytes, BUDGET_REFUSED vor Payloadquery und
BLOB-Fetch, kein Storage-BLOCKED. Keine Budgeterhöhung oder unbegrenzter Fallback.

Für realistische 200 Personen/1000 Aufgaben bleibt der gemeinsame Größen-/
Budget-/Performancekonflikt offen. Schon 1000 Tasks ohne Events überschreiten
mit diesen Engineeringgewichten das retained Budget, selbst bei einer unteren
Rechnung ohne Textbytes. Keine schnelle Navigation solcher Bestände nachgewiesen;
40 Tasks sind ein gezielter Testbestand, keine Lösung dieser Produktanforderung.

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
