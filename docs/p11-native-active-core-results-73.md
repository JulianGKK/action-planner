# §73 – Native Faults and ACTIVE Lifetime Validation

Basis `a6d3fe704500a6be2b538a4568b465aa3fbe27c7`. Branch/Remote/lokal identisch,
Arbeitsbaum anfangs sauber. Unabhängige §72-Reviews #32/#37/#38/#39 gelesen.
Keine Cachearchitekturänderung und keine Produkt-RAM-Hits. Go/No-Go unten.

## Isolation und Ausführungsgrenze

Bereits ausdrücklich isoliert bestätigtes Samsung SM-M526BR / RFCT1186YRJ,
Eigentümerprofil. Vor Beginn nur die geschützten PaG-Pakete vorhanden; ausschließlich
Paketmetadaten davon gelesen, keine Produktdaten/Benutzercredentials. Vor Installationen
APK-Paket-/Target-/Runneridentitäten per aapt/aapt2, Signaturen per apksigner geprüft.
Alle Builds offline aus der Basis plus den hier dokumentierten §73-Änderungen.

Data ausschließlich `org.pag.data.unlockprobe`, Target=Test im selben Library-APK,
Runner `androidx.test.runner.AndroidJUnitRunner`, Debug-Zertifikat SHA256
`6e554c135ecc78bc944e6bca8657cfaaccfa75d7b27a1c146a092c2a9d99478e`.

Separater UI-Target `org.pag.validation.p73`, Testpaket `.test`, ausschließlich
`org.pag.prototype.P73ActiveOwnerProbe`; beide vor Beginn nicht vorhanden. Beide signiert
mit Debug-Zertifikat SHA256
`9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134`.
Init-Script nutzt finalizeDsl, damit die Produkt-defaultConfig die isolierte ID nicht
überschreibt. Die Artefaktprüfung erkannte zuvor genau diesen Buildharnessfehler;
das Artefakt mit Produkt-ID wurde NICHT installiert. Ein falscher Import verhinderte zuvor
eine Testkompilation; ebenfalls keine native Testausführung. Der Manifest-Merger benötigte
die neue Runnerdeklaration nach der bestehenden I3-Deklaration. Kein unselektierter Lauf.

## IMPLEMENTIERT: kleine Beobachtungsgrenze, kein neuer Reader

ProductActiveCoreReader.readForTicket reicht optionale interne feste Store-Phasen- und
Zeitbeobachter an den vorhandenen ActiveCoreStore weiter; Produktionsaufrufe behalten
leere Defaults. Alle bestehenden Session-/Ticket-/Lanechecks, SQL- und Budgetregeln sowie
DB-/Keyscopes unverändert. Keine neue Autorität oder Payloadretention. Debug-Fixture
liefert begrenzte native Probe-/Sessionbrücken, niemals Schlüssel an UI/ViewModel.
P73OwnerReadBridge benutzt den vom Test übergebenen EINEN Mutationport und den echten
ProductActiveCoreLoader/ProductActiveCoreLifetime; er ist keine Runtimeinstallation.

## Native B2-/Cleanupbelege

`P73NativeFaultTest` verwendet echten B2Driver/SQLCipher. Vier unabhängige SQL-Batches mit
je1000Rows und4096Zeichen pro Row: Cursorwindow existiert mit mWindowPtr!=0 während Read,
nach Produkt-close ist Herstellercursorfeld null und der gehaltene Windowhandle mWindowPtr=0.
Dies beobachtet den konkreten nativen Handleabschluss trotz weiter bestehender Java-Referenz;
kein Beweis physischer Heap-/Allocatorlöschung und kein universeller Room-Leakbeweis.

Reset-/Close-Fehler werden ausdrücklich synthetisch um die echte Herstellerinstanz injiziert;
die getestete B2StatementCleanup-Klasse ist dieselbe wie im Produktwrapper. Resetfehler vor
Freigabe lässt den Cursor erwartungsgemäß offen; die Exception bleibt primär, close wird
versucht, zweite Exception bleibt suppressed. Anschließende diagnostische Rettung schließt
Cursor/Window explizit und zählt NICHT als erfolgreicher Produktabschluss. Kein nativer
Hardware-/Allocatorfehler und kein vollständiger Room-Upstream-Cleanupfehlernachweis.
Produktive Getter-/Step-/Revoke-/Rollbackbelege aus §72 bleiben getrennte frühere Belege.

SessionDbKeyOwner.withDbKey wird um echte native SELECT-Arbeit verwendet. Nach erfolgreichem
Rücklauf sowie einer eindeutig identifizierten injizierten Exception ist exakt das geliehene
ByteArray nullgesetzt und driver.resourcesClosed wahr. Das beweist diese Java-Leasekopie,
Driver-Keykopie und die beobachteten Connections; keine globale Zählung aller Leases/Handles
oder Löschung interner SQLCipher-Key-/Speicherreste. Keine Keys verlassen die Debug-Probe.

## ANDROID-RED: Window-Refill bleibt ausdrücklich offen

Erster Lauf:4000Rows beweisen KEINE Windowneufüllung (`No actual refill observed`).
Zweiter Lauf:auch angeforderte initiale Windowgröße65536 ergab keinen beobachteten Wechsel
der Startposition. Die Settergröße ist damit nicht als harte maximale Windowgröße belegt.
Die strenge Assertion bleibt erhalten (`actualWindowRefillRequired_RED73`), separat von
GREEN ausgewählt. Der Setter wird nur in der Diagnose verwendet; vorheriger Wert wird
anschließend wiederhergestellt. Keine Produkt-Cursor-/Budget-/Cipherkonfiguration geändert.

Die erfolgreichen vier unabhängigen SQL-Batches sind KEIN nachträglicher PASS für diese
Refillassertion. Vor einem Refillsicherheitsbeleg native Windowwachstums-/Füllsemantik der
gepinnten4.19.1-Version untersuchen und reale refill positions beobachten. Nicht einfach
FD-/PSS-Toleranzen oder Rowanzahl dafür einsetzen.

## Echte Session und nativer Reader

P73ActiveLifetimeTest: tatsächlicher SessionCoordinator mit echter180s/30s-Policy,
kontrollierter MonotonicTime, synthetischer Authentifizierung und synthetischer Maintenance.
READY ist hier ausdrücklich kein Produktcredential-/Maintenance-E2E-Beleg. Testausstellung
und Completion auf Android-Main; native Roomarbeit auf separatem Worker/Room-Scope.
Fixture-Keyowner passend zur tatsächlichen Sessiongeneration, ein gemeinsamer Mutationport.

200Personen/1000Aufgaben in den Lifetime-/Racefixtures. Read über echten Loader/Reader,
Store, Room, SQLCipher/B2 und Keyscopes. Geprüft: Worker-idle-/absoluteExpiry (keine30/180s
Wartezeit, sondern deterministische Zeitfortschreibung), LOCKED/BLOCKED oder Core-/DOCUMENT-
Admission während einer echten PEOPLE_ROW-Phase; Writer/Lock/Foregroundentzug nach
Readercleanup vor Main; verworfener Main-Runnable; UNKNOWN plus unbestätigtes Cleanup
verweigert neue Admission. Atomic-Testslot ist KEIN zweiter produktiver Snapshotowner.

Budgetfall: absichtlich übergroßer Tasktitel im echten SQLitebestand verursacht
BUDGET_REFUSED vor Teilpublication. Das ist ein Feld-/Vorablimit, kein Nachweis jedes
aggregierten8+8MiB-Grenzfalls. Ungültiger leerer Actiontitel führt fail-closed zu BLOCKED.
Eine SQLiteException wird im METADATA-Beobachter synthetisch injiziert, während der reale
Readscope aktiv ist; ebenfalls BLOCKED/keine Publication. Keine Behauptung, der SQLite-
Engine selbst habe an dieser Stelle einen Fehler erzeugt.

Zwei frühere Inputannahmen waren falsch: riesige Template-Provenienz gehört nicht zum
kompakten Core und führte korrekt zu Value; ein nichtleerer Titel mit Randspaces erfüllt
den bestehenden ActionRow-Vertrag. Inputs wurden auf tatsächlich budgetrelevanten Titel
und tatsächlich ungültigen leeren Titel korrigiert, Assertions nicht abgeschwächt.
Alle fehlgeschlagenen Runnerreports bleiben erhalten.

## Tatsächlicher ViewModel-/Compose-Pfad, ausdrücklich getrennte Evidence

P73ActiveOwnerProbe verbindet realen SessionCoordinator, UncachedWorkspaceMutations,
ProductActiveCoreLoader/Lifetime und ProductViewModel mit ProductScreen/Compose auf einer
isolierten MainActivity. Der Corekandidat, Action-/Personrepository, Authentication und
Maintenance sind synthetisch. Das ist KEIN durchgehender ProductRuntime-/DB-E2E-Beleg.
Die MainActivity besitzt ihre normale initial gesperrte Runtime; der Probe ersetzt deren
Content durch seinen Test-ViewModel. Nur dieser Test-ViewModel hält den Testcore. Die
normale Runtime wird nicht authentifiziert, es werden keine Produktcredentials genutzt.

13 Fälle: Lock, Worker-idleExpiry, absoluteExpiry, BLOCKED, expliziter Foregroundentzug,
Corewriter, DOCUMENT, UNKNOWN, Reauth mit altem Main, callback-then-false, Workerlock
zwischen ausgeführtem Installcallback und Publicationrücklauf, Actionwechsel sowie
ACTIVE→COMPLETED. Repositorystatus-/Actionwechsel sind in diesem UI-Harness synthetisch.
Kein alter Callback mintet ein neues Readticket. Admission blockiert Writer nicht.

A: originale Lifetime ungültig. B: tatsächlicher privater ViewModel-Atomic-Slot bei
Workerexpiry bereits auf Worker null (diagnostischer Reflectioncheck, keine Composemutation
auf Worker). C: Main-Verarbeitung/Compose wird abgewartet. D: tatsächlicher nächster
ViewTree-OnDraw mit model.activeCore==null beobachtet. Das ist eine Autoritätsassertion
während OnDraw, keine Pixelanalyse des fertigen Framebuffers. E: weiter existierende
Java-/Rendererreferenzen werden NICHT als gelöscht ausgegeben. Keine Heap-/Frame-Wipe-
Behauptung. Store.clear/Sessionlock/Observercancel nach jedem Fall.

Echte OS-Hintergrund-/Keyguard-/Reauthcredentialereignisse sind damit noch NICHT ausgeführt;
foreground(false) ist ein expliziter Sessionaufruf. Ein durchgängiger authentifizierter
ProductRuntime→nativeCore→ViewModel→UI-Lauf und echte OS-Lifecycleadapter fehlen weiter.

## Rohmesswerte und Speichergrenze

[memory.csv](p11-native-73/memory.csv) und [phases.csv](p11-native-73/phases.csv):
Ein großer nativer Core (200Personen/1000Aufgaben) an drei Übergabepunkten, keine
statistische Zeitserie, kein Median/p95 und kein exakter Retention-/Leakzähler.

| Punkt | FD | NativeHeap Bytes | PSS KiB | ART GC kumulativ |
|---|---:|---:|---:|---:|
|Kandidat wartet auf Main|83|5607328|50568|9|
|Atomic-Testslot geladen|83|5607376|50597|9|
|Atomic-Testslot widerrufen|83|5607424|50601|9|

Diese Messungen betreffen den atomaren Data-Testslot, nicht die komplette Compose-/Editor-
Überlappung. Keine erzwungene GC und keine FD-/PSS-Toleranz als Leakfreigabe. ART/Allocator
können Speicher halten, obwohl Authority und konkrete native Handles bereits beendet sind.
Keine sofortige physische Heaplöschung behauptet. Frühere §72-Serien für kleine/mittlere/
große Bestände bleiben frühere Messungen, kein neuer §73-Überlappungsbeweis.

Store-METADATA30,529896ms, gemeinsame ADMISSION0,130417ms,
MATERIALIZATION20,034062ms. Einzelbeobachtung, keine Performancefreigabe; Host-Gradle
lief teilweise parallel. Scope-/READYchecks, DB-Open/Preflight, DB-/Keycleanup und finale
Publication wurden nicht separat vermessen. §72-frische Reads bleiben ungefähr1,23–1,31
SEKUNDEN, nicht1,2ms. Keine Sicherheitsprüfung entfernt, kein Produkt-RAM-Hit und kein
Content-OnDraw-Latenzresultat. Der Owner-Harness ist ein Revoke-OnDraw-Harness, noch keine
zeitlich korrelierte HUB→PERSONS→FUNCTIONS→TASKS→HUB-Messserie.

8MiB retained +8MiB transition unverändert. Fehlende Gesamtbeobachtung: alter produktiver
Slot + neuer Kandidat + Main-Runnable + Compose-Snapshot + alte Frames + vollständiger
Editorgraph + SQLCipher-/Cursorbuffer. Der synthetische kleine UI-Core ist keine Abnahme
für diese Last. BUDGET_REFUSED bleibt kontrollierte Rejection, kein Fallbacklimit erhöht.

## Vollständige Ausführungsbilanz

| Runnerreport | Ausführungen | bestanden | fehlgeschlagen | Einordnung |
|---|---:|---:|---:|---|
|[Data1](p11-native-73/data-first.txt)|6|5|1|Window-Refill nicht beobachtet|
|[Data2](p11-native-73/data-second.txt)|7|5|2|Refill + falscher Template-Budgetinput|
|[Data3](p11-native-73/data-third.txt)|8|7|1|Falscher Invalid-Input, Randspaces zulässig|
|[Data GREEN](p11-native-73/data-green.txt)|8|8|0|Finale ausgewählte Produktziele|
|[Window RED](p11-native-73/window-red.txt)|1|0|1|Diagnose separat, Assertion unverändert streng|

Damit30JUnit-Testausführungen,25bestanden,5Fehlschläge,0Skips. Nicht rückwirkend als
fehlerfreier Android-Gesamtlauf ausgegeben. Im finalen Teststand sind8 GREEN-Zielmethoden
und1 separat ausgeführte RED-Diagnose enthalten.

[Owner1](p11-native-73/owner-first.txt):10/10Szenarien.
[Owner final](p11-native-73/owner-final.txt):13/13Szenarien.
Das sind23native Custom-Runner-Szenarien, keine23zusätzlichen JUnit-Methoden.
Keine Ownerfehler/Skips. Keine Credential-/Permissionautorität aus diesen synthetischen
Harnesses ableiten. Der Hersteller-RED aus §72 wurde in §73 nicht erneut ausgeführt.

Fixtures nach Data-Läufen per Guard geschlossen; no_backup nach den Läufen leer.
Owner-Target-no_backup ebenfalls leer. Nach abschließender Paketmetadatenprüfung wurden
nur die drei in diesem Lauf neu angelegten Pakete erfolgreich entfernt. Geschützte Pakete
bleiben vorhanden; Version/Signatur/FirstInstall-/LastUpdate-Metadaten vor/nach identisch.
Diese Metadaten ersetzen keine Prüfung interner Produktdaten; solche Daten wurden nicht
geöffnet. Keine pauschale Deinstallation.

Gezielte Hostregressionen und vollständiger angeforderter Offline-Lauf BUILD SUCCESSFUL:
app682 +data1124 +security4 =1810Hosttests,0Fehler/Errors/Skips; domain NO-SOURCE.
AssembleDebug, beide Androidtest-Kompilationen und beide Manifestprüfungen erfolgreich.
Keine Dependency/Permission/Schemaänderung. `git diff --check` sauber.

## Reproduktion und Artefakte

Nur mit bereits bestätigter Isolation und erneuten Paket-/Manifest-/Signaturguards:

```
./gradlew -I tools/p11native/unlockprobe.init.gradle :data:assembleDebugAndroidTest --offline
# Eigene geprüfte unlockprobe-Installation, keine Fremdinstallation überschreiben.
adb -s RFCT1186YRJ shell am instrument -w -r -e class 'org.pag.data.product.P73NativeFaultTest#nativeWindowPointersCloseAcrossIndependentSqlBatches,org.pag.data.product.P73NativeFaultTest#injectedResetAndCloseFailuresRemainErrorsAndNativeResourcesAreExplicitlyReclaimed,org.pag.data.product.P73NativeFaultTest#realKeyLeaseArrayAndDriverEndAfterNativeQueryAndInjectedException,org.pag.data.product.P73ActiveLifetimeTest' org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
# Strenge Diagnose separat (weiterhin RED):
adb -s RFCT1186YRJ shell am instrument -w -r -e class 'org.pag.data.product.P73NativeFaultTest#actualWindowRefillRequired_RED73' org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
./gradlew -I tools/p11native/owner73.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest --offline
# Ausschließlich neue/zweifelsfrei eigene org.pag.validation.p73 + .test installieren.
adb -s RFCT1186YRJ shell am instrument -w org.pag.validation.p73.test/org.pag.prototype.P73ActiveOwnerProbe
```

SHA256 der tatsächlich installierten finalen Artefakte:

- Data GREEN: `936426ba37f6b8477b089234b01eb55df3f3914d8446f0fd7ac42bf7ecdad23a`
- Owner Target: `815e5c4e86f7e0a60d31e9d26236d6a6635bbbee6d4e8c8a06d3443cb1a219cc`
- Owner Test: `2bd4af59099f28724398a0ec1c0c2b2f60de91f1163e87e1209eb6e6b8cca5ca`

## Go/No-Go für separaten RAM-Hit-Slice

**NO-GO zur Produktgate-Aktivierung.** RAM bleibt zwingendes1.0-Ziel, diese offenen
Abnahmen sind Releaseblocker, keine cachelose Releasealternative:

1. Native Window-Refill-/Wachstumsvertrag und Room-Upstreamverhalten nach unbestätigtem
   reset/close/DB-/Keycleanup. Keine saubere Freigabe aus diagnostischer Rettung.
2. Durchgängige reale Runtime-/DB-/ViewModel-/UI-Lifetime sowie echte OS-Hintergrund-,
   Foreground-/Keyguard-/Credential-Reauthadapter prüfen (#39).
3. Gemeinsame8+8MiB-Überlappung inklusive Compose-/Editor-/Frame-/Nativebuffer
   unter repräsentativer Last abnehmen (#33/#34).
4. Jeder künftige Hit zentral StorageAccess.acquire/check/publish UND originale Lifetime/
   gemeinsame Writer-/Ticketepoche prüfen; kein künstlicher Reset der30sIdle-Frist.
   Erst dann separater unabhängiger Gate-Review und Content-OnDraw-Serie (#37/#38).

RED-4 bleibt fail-closed. Kein Rootnachbau, keine Joblöschung. Keine P3b-/Dokumentpayloads
im Core. #36Editor-/ABA-Grenzen bleiben eigenständige offene Freigabepunkte.

## Geänderte Dateien

- `app/src/androidTest/AndroidManifest.xml`
- `app/src/androidTest/kotlin/org/pag/prototype/P73ActiveOwnerProbe.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P73ActiveLifetimeTest.kt`
- `data/src/androidTest/kotlin/org/pag/data/product/P73NativeFaultTest.kt`
- `data/src/debug/kotlin/org/pag/data/product/P11SyntheticFixture.kt`
- `data/src/debug/kotlin/org/pag/data/product/P73OwnerReadBridge.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductActiveCoreReader.kt`
- `tools/p11native/owner73.init.gradle`
- `docs/product-p11-active-workspace-assessment.md`
- `docs/p11-native-active-core-results-73.md`
- `docs/p11-native-73/data-first.txt`
- `docs/p11-native-73/data-second.txt`
- `docs/p11-native-73/data-third.txt`
- `docs/p11-native-73/data-green.txt`
- `docs/p11-native-73/window-red.txt`
- `docs/p11-native-73/owner-first.txt`
- `docs/p11-native-73/owner-final.txt`
- `docs/p11-native-73/memory.csv`
- `docs/p11-native-73/phases.csv`
