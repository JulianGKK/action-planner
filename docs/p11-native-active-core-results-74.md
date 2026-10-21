# §74 – Runtime/OS, Windowwachstum und RAM-Gate-Voraussetzungen

Basis `67c42d6a8b221b633a31747f85339e5a580d9c3b`, Branch
`p11-performance-optimization`. Remote und lokal exakt gleich, Arbeitsbaum sauber.
§73-Reviews #32/#37/#38/#39 gelesen (identischer Reviewtext). Produktcode, Session-,
Key-, Cipher-, Schema-, Writer- und Recoveryverträge bleiben unverändert.
**NO-GO für Produkt-RAM-Hits. Kein vollständiger ACTIVE-Runtime-E2E-Beleg.**

## IMPLEMENTIERT / ausgeführt

* `P74WindowGrowthTest`: echter B2Driver/SQLCipher-4.19.1-Statementpfad, drei
  vollständige 4.000-Zeilen-Zyklen in einem jeweils gehaltenen Window, anschließend
  Produkt-close und beobachteter nativer Handleabschluss.
* `P74RuntimeLifecycleProbe`: tatsächliche MainActivity mit ihrer eigenen
  ProductRuntime, ihrem ProductViewModel und unverändertem ProductScreen. Kein
  Ersatz-Content, Test-Snapshotowner, zusätzlicher Mutationport oder synthetischer Core.
  Tatsächlicher HOME-Intent, OS-onStop/onStart und `Activity.recreate()`. Der Pfad
  bleibt bewusst LOCKED. Alle fünf Prüfungen sind grün; kein READY-/ACTIVE-E2E.
* `ActiveCoreOwnerTest.gapHostReuseHasNoPerHitPublicationAndReportsInteraction`:
  explizite negative Charakterisierung des echten ViewModel-Navigationszweigs bei
  ausschließlich im Hostfake aktiviertem Reuse. Kein Produktgate verändert.

## Isolation / Artefakte / Bereinigung

Das bereits bestätigte isolierte Samsung SM-M526BR / RFCT1186YRJ wurde erneut
identifiziert. Vor Installation waren nur die geschützten PaG-Pakete vorhanden.
Paketnamen, Target, Runner und Signatur wurden aus den tatsächlichen APKs geprüft;
keine vorhandene Installation überschrieben. `aapt` konnte beim Target eine
Framework-Iconreferenz nicht auflösen; die vollständige Manifestprüfung erfolgte
erfolgreich mit `aapt2 dump xmltree`. Keine Produktdaten oder Credentials gelesen.

| Artefakt | Identität / Runner | APK SHA256 |
|---|---|---|
| Data | `org.pag.data.unlockprobe`, Target=Test, `androidx.test.runner.AndroidJUnitRunner` | `e4f3999bb5a123cc7156b820a29bd4ca15d88047c9af5a2223b2dfe3efa6b491` |
| Activity-Target | `org.pag.validation.p74` | `8024959088ef4988a006503908b37951c47dadda81fab16f0a5bc45292d1cf8e` |
| Activity-Test | `org.pag.validation.p74.test`, Target `org.pag.validation.p74`, `org.pag.prototype.P74RuntimeLifecycleProbe` | `22c76e4d9968bfb0b7247099f5f8a0c334de86ffb4b29e868242474a826804cb` |

Data-Zertifikat SHA256:
`6e554c135ecc78bc944e6bca8657cfaaccfa75d7b27a1c146a092c2a9d99478e`.
Activity-Target und -Test:
`9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134`.
Gebaut offline aus der Basis plus diesem §74-Testdiff, vor dem abschließenden Commit.

Nach den Läufen waren beide `no_backup`-Verzeichnisse leer (nur `.`/`..`). Die
kontrollierten Fixture-close-Aufrufe einschließlich des RED-finally wurden ausgeführt.
Alle drei ausschließlich hier neu installierten Testpakete wurden gezielt entfernt.
Paketbestand danach wieder nur `org.pag.prototype.a` und `org.pag.perf.p11probe`.
Versionen, Signaturen, firstInstallTime und lastUpdateTime beider geschützten Pakete
vor/nach identisch. Keine Produkt-App-Installation oder pauschale Bereinigung.

## Window-RED: falsche feste Kapazitätsprämisse nachgewiesen

Herstellerquelle: SQLCipher Android Tag v4.19.1, Commit
`96e35d111605ff3b08309cd6be37729cf38a4155`.
[CursorWindow.cpp, maybeInflate/alloc](https://github.com/sqlcipher/sqlcipher-android/blob/96e35d111605ff3b08309cd6be37729cf38a4155/sqlcipher/src/main/jni/sqlcipher/CursorWindow.cpp#L126):
bei Platzmangel wächst die Allokation per `realloc`, zunächst von höchstens 16 KiB
auf 2 MiB, danach verdoppelt. Kein hartes 65.536-Byte-Limit. Der native Windowobjekt-
Handle bleibt dabei derselbe; sein interner Datenbuffer kann umziehen. Ein unverändertes
`mWindowPtr` ist daher kein Beweis unveränderter Bufferadresse oder Speichergröße.

Bytecode des tatsächlich gepinnten AAR bestätigt: `SQLiteCursor.fillWindow` merkt
die Anzahl der hineingelesenen Rows als Kapazität; `CursorWindow.getWindowSizeBytes`
liefert lediglich das gespeicherte Java-Konstruktorargument. AAR SHA256
`43fc10cae8f1f1059f0933e8942a1eb328989d13426378597cd4add1b34a34fd`,
extrahiertes classes.jar SHA256
`c267d40bad3ce70d3ecbc775e2b23d6a8f55b054c5e7035f4a06746565ac5a52`.

**ANDROID-GREEN:** In jedem der drei neuen Zyklen enthält EIN beobachtetes Window
alle 4.000 Rows mit je 4.096 ASCII-Textbytes, also 16.384.000 Zelltextbytes ohne
Metadaten. `numRows=4000`, `startPosition=0`, Java-size-getter weiterhin 65536,
derselbe Cursor/Windowobjekt-Handle während des Lesens. Nach produktivem close:
Herstellercursorfeld null, gehaltener Cursor geschlossen, `mWindowPtr=0`.
Die Zelltextsumme ist berechnet aus tatsächlich gelesenen Werten, **keine gemessene
Allokationsgröße**. Keine Behauptung physischer Heaplöschung.

**ANDROID-RED:** Die unveränderte Diagnose
`P73NativeFaultTest.actualWindowRefillRequired_RED73` wurde separat erneut ausgeführt:
`AssertionError: No actual refill observed`. Vollständiger Report erhalten. Keine
Assertion abgeschwächt, kein Skip. Ein `startPosition`-Wechsel wäre ein brauchbarer
Hinweis auf anders positionierte Füllung; seine notwendige Erzwingbarkeit durch die
Anfangsgröße war jedoch falsch. Dieser RED ist kein Produktleaknachweis.

Der neue Test ersetzt fachlich die unbegründete feste-Kapazitäts-Erwartung durch
Wachstum/Abschluss, nicht durch unabhängige SQL-Batches. Er wird NICHT als Refill-PASS
gezählt. Refill nach tatsächlichem Allokationsfehler, native OOM-/realloc-Fehlerpfade
und allocatorinterne Restreferenzen bleiben **NICHT BEWIESEN**. Kein OOM erzwungen.
Insbesondere ist ein natives Window außerhalb der Java-Payloadcharge nicht durch
den 8-MiB-Corevertrag pauschal begrenzt.

## Echte Runtime-/OS-Grenze, fehlender ACTIVE-E2E-Anschluss

**ANDROID-GREEN, eng:** initial LOCKED verweigert `runtime.activeCore.loadOwned`
vor Readerarbeit; Runtime-Reuse false, ViewModel-Slot leer. HOME startet tatsächlich
den Launcher, Application-Lifecycle-Callbacks beobachten onStop und beim Zurückholen
onStart. Auch danach keine Readfreigabe. Activity-Neuerstellung erzeugt eine andere
Runtime; die alte bleibt LOCKED. Ein nachfolgender tatsächlicher ViewTree-OnDraw sieht
den leeren Modelslot. Keine Aussage über gelöschte Framebufferpixel oder alte Heaprefs.
Keyguard war bei der Probe nicht gesperrt; kein Keyguardereignis ausgelöst.

**BLOCKER A:** ProductRuntime konstruiert ProductCredentialAuthentication und den
ProductDbEnvelope intern. READY setzt DEVICE_CREDENTIAL-Authentifizierung mit exakt
dem erwarteten CryptoObject voraus, danach `useUnwrappedKey`, autorisierte Maintenance
und SessionDbKeyOwner.bind. Ohne Benutzercredentials ist dieser positive Pfad mit
der vorhandenen Komposition nicht erreichbar. Die Probe ruft `start(initialize)`
absichtlich nicht auf und erzeugt weder Envelope noch DB/Keystoreartefakte.

Der kleinste fehlende Anschluss ist eine separat geprüfte, ausschließlich im Debug-
Testpaket erreichbare Runtime-Initialisierung, die einen synthetischen Fixture-Key
unter der **gleichen** Session/Maintenance/Owner-Lebensdauer installieren kann, ohne
ProductCredentialAuthentication im Produkt zu umgehen. Heute existiert dafür kein
Runtime-Factory-/Initialisierungsport. Eine Injection über Reflection in Session oder
Keyowner würde die zu beweisende Autorität ersetzen und wurde nicht implementiert.
Ein solcher synthetisch authentifizierter Test müsste weiterhin vom realen Android-
Credential-CryptoObject-E2E unterschieden werden. Alternativ braucht letzterer eine
gesondert autorisierte interaktive Testcredential-Abnahme, nicht Benutzercredentials.

Damit weiterhin **NICHT BEWIESEN** im durchgängigen Runtime→DB→ViewModel→Compose-Pfad:
READY→Core, LOCKED/BLOCKED/idle/absolut/Workerexpiry bei gehaltenem Core, Writer/DOCUMENT/
UNKNOWN, Action-/Statuswechsel, Reauth, alter Main, callback-then-false, VM-Clear,
Neuerstellung mit gehaltenem ACTIVE-Core. §73-Teilbelege werden nicht addiert und als
E2E umetikettiert. Tatsächlicher Screenlock-/Keyguardentzug und Prozessneustart bleiben
offen; die OS-Probe beweist nur den beschriebenen LOCKED-Lifecycle.

## Native Faults, Room-/Keycleanup

Erneut **ANDROID-GREEN**: P73-Nativtest für exakt geliehenes Java-Keyarray und Driverende
nach nativer SELECT-Arbeit/definierter Exception; synthetisch injizierte reset/close-
Fehler um echten SQLCipher-Statement/helper. Resetfehler bleibt Fehler und kann den
Cursor offen lassen; separates diagnostisches Rescue zählt nicht als Produktcleanup.

P73ActiveLifetimeTest erneut mit realem Room-/SQLCipher-Core und 200/1.000 Fixture:
Budgetverweigerung durch übergroßen Titel, ungültige Daten, synthetische SQL-Exception
im Metadata-Hook, Worker-idle/absolut, Revoke/BLOCKED/Core-/DOCUMENT-Beginn während
Rowloop, Writer/Lock nach Read vor Main, verlorener Main, UNKNOWN. Auth/Maintenance
synthetisch, Snapshot Atomic-Testslot, kein ProductRuntime-/ViewModel-E2E.

**BLOCKER #32:** ActionDatabaseAccess.use erstellt Room/B2Driver selbst. `usePrepared`
und native Statement-close besitzen hier keine sichere testlokale Factory zum
gezielten Auslösen von Reset-/Close-Fehlern aus Room heraus. Room-close wird im finally
versucht, dann cancelAndJoin und Driver-close; danach erst Health-/Publicationcheck.
SessionDbKeyOwner nullt die Leasekopie im finally. Diese Codegrenzen und Teiltests sind
kein injizierter nativer Room-/DB-close-/Keycleanupfehlerbeweis. Eine Beobachterexception
nach Erfolg wäre ebenfalls kein solcher Beweis. Keine globale Driver-/Keyowneränderung,
kein Retry, keine neue Rollbackklassifikation. Nächster Faultanschluss braucht eine
eng begrenzte Ressourcenfactory im Testscope, die Unsicherheit bis zum Loader prüft.

## 8+8 MiB und tatsächliche Rohwerte

Ein CoreReadAdmission, 8 MiB retained + 8 MiB transition unverändert. Keine weitere
Budgetengine. Engineering-Charges decken ausgewiesene Payload-/Referenzeinheiten ab,
nicht automatisch native Window-, Room-, Compose-, Editor- und Renderallokationen.

Die erneut gemessene 200/1.000-Data-Probe enthält keinen ViewModel/Compose-/Editorgraph:

| Messpunkt | FD | NativeHeap Bytes | PSS KiB | ART GC count |
|---|---:|---:|---:|---:|
| Kandidat wartet auf Main | 83 | 5504704 | 46991 | 11 |
| Atomic-Testslot geladen | 83 | 5504752 | 47025 | 11 |
| Slot widerrufen | 83 | 5511904 | 47045 | 11 |

Einzelbeobachtung ohne erzwungene GC, keine Toleranz als Leakfreiheit. Phasen:
METADATA 32.226303 ms, ADMISSION 0.136719 ms, MATERIALIZATION 24.912344 ms;
keine Median-/p95-Serie oder End-to-End-Latenz. Der Gradle-Hostbuild lief teilweise
parallel. §72-frische Reads waren etwa 1,23–1,31 Sekunden, keine RAM-Hits.

**BLOCKER C:** alter Core + neuer Kandidat + wartender Main + Compose + Frames + voller
Editorgraph + native Buffer/Key-/DBscopes sind nicht gemeinsam exakt vermessen. Die
OS-Probe enthält keinen Core, die Data-Probe keine Composeobjekte. Unicode-/Langtext-
und Eventwachstums-Überlappungsserie nicht ausgeführt. Die Windowprobe zeigt zusätzlich,
warum Javacharge und NativeHeap nicht gleichgesetzt werden dürfen. Kein Gesamt-8+8-
Heap-Limit-PASS. BUDGET_REFUSED bleibt explizite Rejection, keine Teilpublication.

## Zukünftiger per-Hit-Vertrag: konkreter Host-Gegentest

**HOSTGETESTET, negativer Befund:** Hostport reuse=true, bestehender echter ViewModel-
Slot; nach Installation wird Fake-lifetime.publish auf false-after-callback gestellt.
Navigation TASKS hält den Slot trotzdem, ruft publish nicht erneut auf und erhöht den
SessionPort.userInteraction-Zähler um eins. Produktreuse bleibt false, daher kein
produktiver unsicherer Hit. Die grüne Charakterisierung ist kein grüner Hitvertrag.

**BLOCKER E / nächster enger Produktpatch:** ein Runtime-Lifetime-Hitport muss pro Hit
frisch StorageAccess.acquire(READY), Context.check, Originalticket/Port/Action/Generation/
Epoche/Load/ACTIVE/Namespace und kein Pending/UNKNOWN prüfen; dann kurzer zentraler
publish-Gate mit terminaler false-Behandlung. Kein neues Ticket für alten Core, kein
SQL für bereits gültige Daten. ViewModel muss diesen Port verwenden, bevor es Reuse
annimmt. Der Hitpfad darf nicht vorher `interaction()` auslösen und damit READY-idle
erneuern. Bestehende Write-/Fresh-read-Interaktionssemantik separat erhalten. Erst
Gegentest zu GREEN ändern, wenn diese echte Grenze implementiert ist; nicht durch
Fake-Lifetime oder UI-Boolean ersetzen. Kein solcher Port in §74 implementiert.

## Testbilanz und Reproduktion

Gezielte Hosttests ActiveCoreOwnerTest, ProductActiveCoreLoaderTest und
B2StatementCleanupTest erfolgreich. Vollständiger geforderter Offline-Lauf erfolgreich:
**1.811 Hosttests = app683 + data1124 + security4**, domain NO-SOURCE,
**0 Fehler / 0 Skips**. Assemble, Androidtest-Kompilation und beide Manifestprüfungen
erfolgreich. `git diff --check` sauber. Keine Assertions abgeschwächt.

**9 native JUnit-Ausführungen: 8 GREEN, 1 separate diagnostische RED, 0 Skips.**
**5 zusätzliche Custom-Runner-OS-Prüfungen GREEN**, keine zusätzlichen JUnit-Tests.
Alle vollständigen Runnerreports: [Data](p11-native-74/data.txt),
[unveränderte RED-Diagnose](p11-native-74/window-red.txt), [OS](p11-native-74/os.txt).
Rohwerte: [memory.csv](p11-native-74/memory.csv), [phases.csv](p11-native-74/phases.csv).

Erst Paket-/Geräte-/Artefaktprüfungen wie oben, dann isoliert bauen:

```sh
./gradlew -I tools/p11native/unlockprobe.init.gradle :data:assembleDebugAndroidTest --offline
./gradlew -I tools/p11native/owner74.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest --offline
```

Kein blindes Installieren: IDs/Runner/Signaturen müssen erneut aus APKs bestätigt,
fremde Pakete ausgeschlossen sein. Verwendete gezielte Runnerauswahl nach Installation:

```sh
adb -s RFCT1186YRJ shell am instrument -w -r -e class 'org.pag.data.product.P74WindowGrowthTest,org.pag.data.product.P73NativeFaultTest#realKeyLeaseArrayAndDriverEndAfterNativeQueryAndInjectedException,org.pag.data.product.P73NativeFaultTest#injectedResetAndCloseFailuresRemainErrorsAndNativeResourcesAreExplicitlyReclaimed,org.pag.data.product.P73ActiveLifetimeTest' org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
adb -s RFCT1186YRJ shell am instrument -w -r -e class 'org.pag.data.product.P73NativeFaultTest#actualWindowRefillRequired_RED73' org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner
adb -s RFCT1186YRJ shell am instrument -w org.pag.validation.p74.test/org.pag.prototype.P74RuntimeLifecycleProbe
```

## Freigabeentscheidung

**NO-GO** für Produkt-RAM-Gate. Fortschritt: Window-Testprämisse geklärt, reale
LOCKED-OS-Lifecycleprobe und reproduzierbare fehlende Hitautorität. Noch erforderlich:
abgesicherter Runtime-Initialisierungsanschluss für eine zusammenhängende ACTIVE-
Native-/ViewModel-/Compose-Abnahme; tatsächliche Keyguard-/Reauth-/Revoke-Races;
Room-Upstream-Faultcleanup; gemeinsame Ressourcenüberlappung; frische per-Hit-Autorität
ohne Idleverlängerung. #32/#33/#34/#37/#38/#39 bleiben offen, ebenso eigenständig
#36 Editor/ABA und RED-4 rootloses IMPORT_WRITING. Kein Merge/Release. Nach WIP-Push
STOPP bis zum unabhängigen GitHub-Review.

## Vollständige Dateiliste dieses Slices

* `app/src/androidTest/AndroidManifest.xml`
* `app/src/androidTest/kotlin/org/pag/prototype/P74RuntimeLifecycleProbe.kt`
* `app/src/test/kotlin/org/pag/prototype/product/ActiveCoreOwnerTest.kt`
* `data/src/androidTest/kotlin/org/pag/data/product/P74WindowGrowthTest.kt`
* `tools/p11native/owner74.init.gradle`
* `docs/product-p11-active-workspace-assessment.md`
* `docs/p11-native-active-core-results-74.md`
* `docs/p11-native-74/data.txt`
* `docs/p11-native-74/window-red.txt`
* `docs/p11-native-74/os.txt`
* `docs/p11-native-74/memory.csv`
* `docs/p11-native-74/phases.csv`
