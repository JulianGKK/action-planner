# §102 – explizite P3b-Recovery / Abschlussprüfung #35

Basis `d355dd58b833b5e7949ef2a061a887bfcc4ddc3b`, Branch `p11-performance-optimization`, 24.10.2026. #35, ergänzend #39/#44.

**READY_FOR_INDEPENDENT_CLOSURE für den belegten Writer-/Commit-/UNKNOWN-/Recoveryumfang von #35.** Die beiden §101-Restpunkte sind in den unten benannten Gegenprüfungen erfüllt; unabhängiger §102-Review steht aus, Issue nicht selbst schließen. Gegenstand sind ausschließlich die zwei Restpunkte des [unabhängigen §101-Reviews](p101-independent-review.json): leere RESERVED-Einrichtung mit sichtbarer, frisch autorisierter Recovery sowie das Publication-Boolean. Das bestehende [Inventar und die Writer-Abnahmematrix §101](../p11-p101/assessment.md) werden übernommen; kein erneutes allgemeines Writer-Audit.

## Autorisierung und tatsächliche Zustände

Die vorhandene Autorisierung genügt innerhalb ihrer unveränderten Grenzen: `ProductRuntime.start` benutzt ausschließlich STORAGE_INITIALIZE/STORAGE_UNWRAP. Der originale `ProductCredentialAuthentication` in `security/ProductDbEnvelope.kt` fordert DEVICE_CREDENTIAL mit dem tatsächlichen CryptoObject an. `SessionCoordinator` nimmt die Antwort nur für seinen AUTHENTICATING-Zustand und gültigen Authentisierungszeitpunkt an; READY folgt erst nach erfolgreicher autorisierter Maintenance. READY kann nicht durch ein UI-Bool hergestellt werden. Sessionfristen und Foreground-/Lockregeln bleiben unverändert.

`prepareRecovery` liest unter originaler READY-Autorität Actionstatus, Rootmetadaten, Aliaszustand und **SQL-COUNT**. Es gibt ein einzelnes bewusst vorbereitbares Command-Handle zurück, keinen StorageContext oder Schlüssel. Das Handle enthält nur Originalmetadaten (16-Byte-Salt, 0/88-Byte-Envelope), Action-/Status-/Generationsbindung und Einmal-/Cancel-Metadaten. Als Issueridentität dient die **bereits vorhandene** private AtomicLong-Requestinstanz; das Handle hält keinen Repository-/Runtime-/Keyowner-Verweis. Keine Registry, kein Cache und keine zweite Authority.

UI: erst Angebot antippen → Aktion und Reauth bestätigen → bestehendes Lock/unlock/DeviceCredential-Verfahren → **zweite ausdrückliche Bestätigung** in frischer READY-Generation. Kein Reset auf Start, Anzeige oder gewöhnlicher Reauth. Die Metadatenabsicht überlebt ausschließlich diesen bewusst begonnenen Authentisierungsvorgang; Lock/Abbruch, Navigation, fremde Generationsantwort und VM-Ende canceln sie. Keine Editor-/Klartexteingaben werden fortgesetzt. Die zweite Bestätigung ist bis zur aktuellen autorisierten Aktionsliste deaktiviert. Jeder Button bindet die konkret angezeigte Auftragsidentität und Generation; alte A-Callbacks können B weder authentisieren, bestätigen noch abbrechen. Auch das ursprüngliche Angebot ist an Action-ID/Generation gebunden. Der Datenpfad prüft unabhängig davon erneut den Originalzustand.

`LeadershipRecoveryIntent` verleiht keine Berechtigung: gleicher ursprünglicher Issuer, Einmalentnahme **vor Admission**, aktueller originaler READY-Kontext, Generation strikt größer als bei Vorbereitung, laufender Request und nicht gecancelte Absicht sind gemeinsam notwendig. Rohgenerationen oder UI-Zustände ersetzen keine Autorisierung. Ein alter/fremder/verbrauchter Auftrag wird ohne neue Mutationsepoche verweigert. Nach unbekanntem Ausgang braucht es einen **neuen** bewusst vorbereiteten Auftrag und erneute vollständige Reauth/Prüfung; keine automatische Wiederholung.

| Zustand | Exakter Vertrag |
| --- | --- |
| RESERVED | Passende Action-ID, canonical UUID-v4-Epoche wie im originalen Setup, Crypto1/Argon2id19/65536KiB/3/1, Salt16, leeres Envelope, COUNT sensitive Bereiche **0**. Abweichungen verweigern. READY wird niemals zu RESERVED umgedeutet. |
| Alias vorhanden | Ausschließlich originaler actionbezogener Alias im App-UID-Keystore; `usable` muss die originalen AES256/GCM/Purpose/Origin/Auth-Attribute bestätigen. Originale Keystore-API löscht ihn; danach `present==false` zwingend. Keine callerdefinierten Aliasnamen. Die ursprüngliche Setup-Sequenz prüft Aliasabwesenheit vor RESERVED und verhindert Replacement. Keine neue epochverschlüsselnde Alias-/Cryptoarchitektur. |
| Alias fehlt | Nur erfolgreich zurückgegebene `present==false` gilt als Abwesenheit. Kein Create/Replacement, kein unnötiger Delete. |
| Alias unklar / unbrauchbar | Exception ist keine Abwesenheit; fail closed, keine Datenbereinigung. `usable=false` ist keine Löschfreigabe. |
| Sensitive Daten bei RESERVED / falsche Metadaten / READY | Keine Resetfreigabe; COUNT statt Nutzdatenmaterialisierung. Gleiche Originalidentität wird vor dem finalen Commit nochmals geprüft. |
| DELETE_PENDING | **Getrennter** sichtbarer Fortsetzungsweg, kein RESERVED-Reset. Der bestehende persistierte Zustand ist bereits der unter ursprünglichem Passwort-/READY-Gate bestätigte Löschauftrag. Rootformat/Epoche/Status/Alias werden erneut geprüft; verbliebene zur Aktion gehörende Bereiche werden wie im bisherigen `deleteAction`-Fortsetzungspfad entfernt, ohne sie zu öffnen. Kein Passwortbypass bei READY; dessen gewöhnlicher `deleteAction` bleibt unverändert. |

## Reihenfolge und Crashgrenze

```text
vorbereitetes Original-Command (nur Metadaten)
  → explizite Bestätigung → originales Lock + DeviceCredential + Maintenance
  → frisches READY → zweite Bestätigung
  → originale LEADERSHIP-BEGIN / Readinvalidierung VOR Workerqueue
  → autorisierte äußere DB-Transaktion: Originalroot + Status + COUNT prüfen
  → originaler Keydelete, falls vorhanden → Abwesenheit bestätigen
  → zweite äußere DB-Transaktion: gleiche Rootidentität + Status + COUNT + Aliasabwesenheit
  → Root entfernen (DELETE_PENDING zusätzlich: bestehende Aktionsbereichsdaten entfernen)
  → äußerer Commit → DB-/Keyscope-Cleanup → Originalticket-Ende
  → originale atomare Session-Publication + aktuelle UI-Identität → Erfolg
```

Room und Keystore sind **keine** gemeinsame Transaktion. Vor dem finalen DB-Commit bleibt der ursprüngliche RESERVED-/DELETE_PENDING-Datensatz erkennbar. Ein Abbruch nach tatsächlichem Keydelete lässt den alten Root mit nun fehlendem Alias bestehen. Erst ein neuer ausdrücklich autorisierter Auftrag darf diesen bereits erledigten externen Schritt bestätigen und den Root entfernen. Eine spätere Ausnahme etikettiert frühere bestätigte Phasen nicht als Rollback. UNKNOWN bleibt fail-closed/BLOCKED gemäß bestehender `LeadershipWriteProgress`/`WorkspaceWriteBoundary`; COMMITTED plus fehlende Cleanupquittierung bleibt COMMITTED/FAILED_OR_UNCONFIRMED, ohne Erfolgsmeldung.

Keine zusätzlichen vollständigen Bereichslisten im Recovery-Worker: neuer DAO-COUNT, keine Schemaänderung. Root-/Command-/Snapshotobjekte sind begrenzte Metadaten und werden nicht als kostenfrei oder physisch gewiped dargestellt. Die vollständige Integration bestehender Fullview-/Form-/Editorzustände bleibt #37; hieraus folgt kein neuer globaler Heapbeweis oder eigenständiges Freikontingent. R8 **8.388.608** / T16 **16.777.216** unverändert, Framework-/Nativekosten separat. Kein `OutstandingCoreRead`-Endsignal oder Freigabesignal aus UI-Entfernung/GC.

## Publication-Restpunkt

Die bisherige private `bounded`-Seam delegiert jetzt an die direkt testbare interne `restrictedLeadershipContext`-Funktion. Rückgabe: **accepted && delivered && allowed()**. Originalkontext und Keyadmission bleiben delegiert und zusätzlich einschränkend; keine neue Publication-Autorität.

`falseAfterCallbackReturnsFalseButAlreadyExecutedSideEffectIsNotUndone` verwendet bewusst einen **defekten künstlichen Provider**: Callbackzähler wird1, delegiertes Ergebnis false, Wrapper false. Auch eine im Callback widerrufene Zusatzbedingung ergibt false. Das beweist **keine** Rücknahme schon ausgeführter UI-Effekte und keine Sicherheit beliebiger Ersatzprovider. Produktiv bleibt ausschließlich die unveränderte `SessionCoordinator.publish`-Monitorsektion maßgeblich: gültige Originalgeneration prüfen und kurze In-memory-Publication unter derselben Sessionverriegelung. Unabhängig geprüfter §101-Codevertrag und erneut ausgeführte Session-/Leadership-Races bleiben erhalten. Kein atomarer Frame-/Retirementnachweis.

## Aktualisierte #35-Abnahmematrix

| Kriterium | Produktpfad | Test / Rohbeleg | Status |
| --- | --- | --- | --- |
| vollständiges Writerinventar | bestehende Runtime-Portinjektion und Repositories | §101-Inventar, 293 Host-/elf finale Gerätebelege, unabhängiger Review; hier nicht unnötig wiederholt | PASS im übernommenen Scope |
| BEGIN vor Queue/Sideeffect, dirty/Pending | vorhandene LEADERSHIP-Boundary; neuer `recover` nutzt dieselbe Instanz | §101 BEGIN-Race, erneut P101LeadershipWriterTest/P101WriterWiringTest; reale Recoveryfälle | PASS |
| tatsächliche DB-/externe Phasen, getrenntes Cleanup, terminal UNKNOWN | `LeadershipWriteProgress`, Recovery COUNT/Transaktions-/Aliasprüfungen | `crash-retry`, `unknown-alias`, `cleanup-receipt`; Host-P3b-Outcomes | **PASS** im unten belegten Umfang |
| kein Saved vor Commit + Cleanup + Autorität | ursprüngliche zentrale Publication + Request-/Command-/VM-Identität | `lock-before/during/after`, Cleanupnegativ, Host-VM-Doppelcallback und Publication-false-Gegenprobe | **PASS** im unten belegten Umfang |
| Lock/Generation/Action/Permission, späte/doppelte Callbacks | Original-Session, Parentregeln, Issuer/Einmal-/Cancel-/Status-/Generationsbindung | 8 neue Data-Hostmethoden, fünf zusätzliche VM-Methoden; Geräte Status/Lock/late duplicate | **PASS** im unten belegten Umfang |
| Task+Event atomar / kein UNKNOWN-Retry | Taskwriter unverändert; Recovery erfordert neuen Auftrag | §101 und §87a unverändert übernommen; `crash-retry` prüft zweiten expliziten Auftrag ohne zweiten Keydelete | PASS bestehender Taskvertrag; Recovery **PASS** wie unten belegt |
| Dokument-/P3b-/Recoverygrenzen, keine neue Authority | bestehendes Dokumentprotokoll unverändert, ursprüngliche Session/Keyowner; neuer enger RESERVED-Reset + getrennte DELETE_PENDING-Fortsetzung | §101 Dokumentbelege und sourcegleiche gemeinsame Gates; sichtbare `present-ui/absent-ui/delete-pending-ui` | **PASS** im unten belegten Umfang |
| echte SQLCipher-/Room-/Keystore-Regressionen, Hostraces und unabhängiger Review | strikt eigenes P77-Paket und synthetische Fixture | neue Rohlogs unten, §101 unabhängiger Teil-PASS | **PASS** Ausführung; **unabhängiger §102-Review ausstehend** |

## Ausführung und Grenzen

[host-build-14.log](host-build-14.log), [JUnit](junit/), [host-summary.json](host-summary.json): **71 aktuelle Hostmethoden in sieben Klassen**, 0 Fehler/Skips. Davon acht neue Data-Gegenmethoden und fünf zusätzliche VM-Methoden; alte fachliche Assertions erhalten. Erneut nur unmittelbar betroffene P3b-/Session-/Writer-/Expositionsfälle; keine Gesamtsuite. P77-App-/AndroidTest-Build und Isolationsprüfung. Keine normalen Release-Builds.

P77: RFCT1186YRJ, Samsung SM-M526BR/API33, ausschließlich `org.pag.prototype.a.p77runtime` und `.test`, eigener P77-Signer; ausschließlich synthetische Daten/Aliase. Echte SessionCoordinator-/Runtime-/Room-/SQLCipher-/AndroidKeystoreoperationen. **P77-Authentisierungsprovider bewusst synthetisch**: die echten UI-Klicks/READY-Generations-/Maintenancewechsel sind prüfbar; es wird **kein** real bedienter Android-DeviceCredential-Prompt behauptet. Dessen unveränderte Produktionskomposition ist oben belegt; globale Credential-/Securityabnahme #39/#46 bleibt unabhängig offen. Keine Fristen-, Foreground- oder Schlüsselregeln für Tests gelockert.

### Frühere Fehler und Bereinigung (keine grünen Abnahmebelege)

- `host-02.log`: neuer UI-Fake zählte `SessionPort.authenticate`, obwohl der vorhandene ViewModel-Konstruktorstart im Test ein No-op war. Fake an den vorhandenen injizierten `begin`-Aufruf angeschlossen, produktive Autorisierung unverändert. Alle bisherigen Tests erhalten.
- [present-ui-01](present-ui-01.txt), [eigener gefilterter Crashbericht](present-ui-01-crash.txt): neuer Dialog ursprünglich außerhalb ProductInputScope, reproduzierbares PRODUCT-FORM-SCOPE. Keine Produktdaten betroffen. Prozessabbruch verhinderte Fixtureteardown: ein eigener Root blieb zurück. [Aufgezeichnete Inode](crash-fixture-inode.txt), expliziter P77-only Root-/Inode-Guard und vorhandener `P11FixtureRoot` entfernten **nur diesen** dokumentierten synthetischen Root plus den bekannten eigenen Testalias; [separater Cleanupnachweis](recorded-crash-cleanup.txt). Kein `pm clear`, keine Produktinstallation/-datenlöschung, keine als Produkt-Recovery ausgegebene Testbereinigung.
- [present-ui-02](present-ui-02.txt): zusätzlicher Dialog kollidierte mit unverändert strikter Activity-Foreground-Autorität (B2-SESSION). Autorisierung nicht abgeschwächt. Finale Bestätigungen als Seite **im selben Activityfenster**, innerhalb originalem ProductInputScope; keine zusätzliche Dialog-/Popup-Autorität. Dieser Versuch lief durch den kontrollierten Fehlerteardown; nachfolgende Inventur leer.

Zuordnung dieser Versuche: [attempt-01-apks.json](attempt-01-apks.json) bzw. [attempt-02-apks.json](attempt-02-apks.json). Frühere Buildlogs bewahren die tatsächlichen Zwischenstände; keine Mehrfachzählung wiederholter Hostmethoden. Der finale Geräteblock und dessen APK-Zuordnung werden separat unten geführt.

## Finale Gerätebilanz und Abschluss

Es gibt **zwölf unterschiedliche akzeptierte Gerätefälle**, mit ausdrücklich getrennter APK-Zuordnung. Zuerst lief der vollständige Zwölferblock auf [before-callback-binding-apks.json](before-callback-binding-apks.json) erfolgreich. Nach abschließender UI-Callback-Härtung wurden ausschließlich die **drei betroffenen sichtbaren Bedienfälle erneut** auf dem [finalen APK-Paar](resume-apks.json) ausgeführt. Die neun direkt am Repository/Sessionpfad geprüften Fälle bleiben gültig: Data-/Domain-/Instrumentationsquellen sind gegenüber dem Zwölferblock bytegleich ([damalige Quellhashes](before-callback-binding-source-sha256.json), aktuelle source-sha256.json). Nur neue UI-Aufrufsignaturen/-Bindungen und der zusätzliche Host-A/B-Gegentest änderten sich; Lock/unlock-/Session-/DB-/Writerpfade unverändert. Keine unnötige komplette Gerätetestwiederholung und keine rückwirkend erfundene Ausführung eines anderen APKs.

[device-summary.json](device-summary.json) nennt je Fall sein APK-Paar; [verify-evidence.py](verify-evidence.py) prüft Marker, Hashgleichheit der wiederverwendeten Pfade und historische Zuordnung. Finaler Host-/P77-Build [host-build-14.log](host-build-14.log): **71/71**. Der frühere zusätzliche sichtbare Feedback-Assert liegt seit [build-visible-assert-13.log](build-visible-assert-13.log) auch im final unveränderten Testrunner. Die drei vor der letzten UI-Härtung erfolgreichen Sichtbarkeitsläufe sind als `*-before-callback-binding.txt` erhalten und werden nicht doppelt als unterschiedliche Abnahmefälle gezählt. Ein neuer Hosttest prüft ausdrücklich, dass alte Buttonidentität A den späteren Auftrag B nicht bestätigt, authentisiert oder abbricht.

| Rohbeleg | Konkrete Gegenprüfung | Status |
| --- | --- | --- |
| [present-ui](present-ui-final.txt) | tatsächlicher RESERVED-Commit + Keycreate, danach injizierter Setupabbruch; Angebot/erste Bestätigung/frisches READY/zweite Bestätigung/echte sichtbare Erfolgsnachricht; Root und Alias danach absent | PASS |
| [absent-ui](absent-ui-final.txt) | kontrollierter Abbruch nach erster bestätigter RESERVED-Transaktion/Driverclose vor Keycreate; wirkliche Abwesenheit, null Keydelete; gleicher sichtbarer Ablauf | PASS |
| [normal-ready](normal-ready-final.txt) | normales Setup erfolgreich, gültiges READY verweigert Recoveryvorbereitung; kein Keydelete/Downgrade | PASS |
| [unknown-alias](unknown-alias-final.txt) | unbrauchbarer Alias verweigert Vorbereitung; injizierte Lookup-Ausnahme **nach** gültiger Vorbereitung und neuer Reauth ergibt UNKNOWN/BLOCKED, kein Erfolg/Keydelete, Root und Alias unverändert | PASS |
| [content-refusal](content-refusal-final.txt) | nach gültiger Vorbereitung/erneuter Reauth synthetische sensitive Zeile unter RESERVED eingefügt; erneute Vorbereitung und alter Auftrag beide verweigert, kein Keydelete/Rootreset | PASS |
| [crash-retry](crash-retry-final.txt) | tatsächlicher Keydelete, danach injizierte Ausnahme vor Rootbereinigung; Root bleibt RESERVED; gewöhnliche Reauth repariert nichts; neuer Auftrag + nochmals Reauth entfernt Root ohne zweiten Keydelete; verbrauchter Auftrag verweigert | PASS |
| [lock-before](lock-before-final.txt) | Worker kontrolliert gehalten; Lock vor Ausführung; Root/Alias unverändert, null Keydelete/Erfolg | PASS |
| [lock-during](lock-during-final.txt) | reale Sessionlock im Rücklauf des tatsächlichen Keydelete; keine Rootbereinigung/Erfolg im alten Scope; expliziter neuer Auftrag/Reauth beendet fehlenden externen Schritt korrekt | PASS |
| [lock-after](lock-after-final.txt) | finaler wirklicher Commit/Cleanup vor gehaltenem Main; Lock/Reauth; alter Main zweimal aufgerufen, kein alter Erfolg | PASS |
| [status-change](status-change-final.txt) | tatsächlicher Actionstatus-Commit nach ACTIVE; prepared Recovery verweigert, Alias nicht gelöscht | PASS |
| [delete-pending-ui](delete-pending-ui-final.txt) | tatsächlicher ursprünglicher passwortautorisierter DELETE_PENDING-Commit und Keydelete, injizierter Abbruch; danach sichtbares Angebot, erneute Reauth und explizite Bestätigung; tatsächlicher Abschluss/Erfolgsnachricht | PASS |
| [cleanup-receipt](cleanup-receipt-final.txt) | reale finale Rootentfernung und Driverclose, anschließend bewusst verlorene Cleanupquittierung; COMMITTED/FAILED_OR_UNCONFIRMED, BLOCKED, null Erfolg, kein Retry; nach autorisierter Inspektion Root absent | PASS |

Die Faults sind **kontrolliert injiziert**, keine behaupteten realen Vendor-/Keystore-Closefehler oder Simulation eines beliebigen Prozess-Crashzeitpunkts. Reale Persistenz/Keyeffekte werden unabhängig danach geprüft. Der echte frühe UI-Prozessabbruch ist separat oben dokumentiert und ausdrücklich kein grüner Recoverybeleg. Neues Hosttesting prüft zusätzlich fremde Issuer/Actionidentitäten, falsche/gleiche Generation, cancelte/einmalverbrauchte Intents, Metadatenabweichungen, irreversible Callbackgrenzen. Das vollständige alte Writerinventar und Task-/Dokument-Atomizität werden nicht aufgrund dieser zwölf Fälle neu behauptet; dafür gelten die spezifischen übernommenen §101-Belege.

[Unveränderte Vertragsdateien](unchanged-contracts.json) sind gegen die Basis bytegleich geprüft: Original-Session, Credential-/Crypto-/Keykonfiguration, gemeinsame Mutation-/Publicationboundary, LeadershipWriteProgress, Runtime und Activity-Foregroundregeln, R8/T16-Ledger und OutstandingCoreRead. Exportiertes Schema unverändert; einziger DAO-Zusatz ist COUNT, keine Migration. [Geänderte Quellhashes](source-sha256.json), [reproduzierbare Befehle](commands.sh), [P77-Installations-/Run-Guard](run-device.py). Final [keine eigenen Fixtureroots, geschützte Paketinstallationen unverändert](resume-cleanup.json), zusätzlich pro Fall eigener Testalias absent. Kein produktiver App-Reset.

**#35: technischer Abschlusskandidat READY_FOR_INDEPENDENT_CLOSURE, unabhängig zu prüfen; nicht eigenständig schließen.** Keine Freigabe für ABA #36, Fullview-/Form-/Coreowner #37, Native-Gesamtkalibrierung #33, Security #39/#46 oder Release #44. Kein nativer Gesamtend-/Wipe-/Framebeweis. `reuseEnabled=false`, `ramReuseEnabled=false`, exponierte zweite Core-Admission weiterhin DENIED (16 unveränderte gezielte Hostfälle und bytegleiche Sperre). Keine automatische Readmission oder produktive Schnellnavigation. WIP pushen und **STOPP zum unabhängigen Review, kein Merge/Release**.
