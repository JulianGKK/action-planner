# P10b2e — Dokumentenimport auf Android

## Ausgangslage und erhaltene Korrekturen

Basis ist `main`, `000151d`, Schema **11**. Die lokalen Korrekturen
[des vertrauenswürdigen Android-Basispfads](fix-document-unlock-path.md) und
[der beobachtbaren Dokumentennavigation](fix-document-navigation.md) bleiben
erhalten. Beide wurden zuvor auf dem Samsung durch Nutzendenrückmeldung
bestätigt. Die begrenzte [Maintenance-Diagnose](maintenance-probe.md) bleibt
rein beobachtend und ausschließlich für debuggable Builds aktiv.

Keine Datenbankinitialisierung, kein Löschen/Ersetzen bestehender Produktdaten,
kein `pm clear`, keine Deinstallation, keine Credentialänderung. Automatisierte
Storage-Gerätetests verwenden ausschließlich zufällige synthetische Bestände im
separaten Testpaket `org.pag.data.unlockprobe`, niemals die Produktdatenbank.

## Nachgewiesene Ursache der Publication

Zwei bestehende Tests wurden vor der Umstellung erneut mit dem bisherigen
Test-APK auf dem Samsung ausgeführt: beide scheiterten bei `Os.link` mit EACCES.
Das ist mit dem Android-Sicherheitsmodell vereinbar: Die
[AOSP-Android-13-SELinux-Regel für normale Apps](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/android13-release/private/app_neverallows.te)
untersagt `file link` für `all_untrusted_apps`. Es wird weder SELinux umgangen
noch eine Berechtigung, ein anderer UID-Kontext oder Root verwendet.

Der zuvor auf Host-Linux erfolgreiche Hardlinkpfad aus P10b2c2 ist deshalb kein
tragfähiger Android-Produktpfad. Dieser Vertrag ersetzt ausschließlich dessen
konkrete Link/Unlink-Technik; Verifikation, Gates und DB-Protokoll bleiben gültig.

## Atomare Verschiebung mit Kernel-No-Overwrite

`DocumentAtomicRename` ruft über die kleine JNI-Brücke `pag_document_fs` die
öffentliche Android-libc-Funktion `renameat2` mit `RENAME_NOREPLACE` auf.
Die Funktion ist ab API 30 öffentlich verfügbar, entsprechend Projekt-minSdk 30
([bionic-API-Einführung](https://android.googlesource.com/platform/bionic/+/720f68a30ce2b31b9530ff3789c1e08f337caa2b%5E1..720f68a30ce2b31b9530ff3789c1e08f337caa2b/)).
Die [Kernel-Semantik](https://man7.org/linux/man-pages/man2/rename.2.html)
verbindet atomare Verschiebung und Zurückweisung eines existierenden Ziels.
Auch ein erst nach der Inventarprüfung erzeugtes Ziel wird nicht ersetzt.

Es gibt **keinen** Rename-, Copy-, Hardlink- oder Java-Fallback. Nicht
unterstützte Dateisysteme, fehlende native Bibliothek und Syscallfehler scheitern
geschlossen. Die JNI-Brücke verarbeitet keine Schlüssel oder Klartextinhalte;
Fehler verlassen sie nur als numerischer Fehlercode. Kotlin gibt ausschließlich
den neutralen Storagefehler weiter. Keine zusätzlichen Logs.

Build: vorhandenes Android-Gradle-Plugin mit festgelegtem NDK
`28.2.13676358` und `ndkBuild`; keine neue Drittanbieter-Runtimebibliothek oder
Gradle-Dependency. Die native Bibliothek wird für die vier Android-ABIs gebaut.
Kein eigener Cipher, keine KDF, keine Änderung an Tink.

Alle eigenen Mutatoren und Maintenance verwenden weiterhin dieselbe
`DocumentNamespaceLane`. Quelle und Ziel werden aus autorisierten UUID-Namen
im privaten Namespace abgeleitet. Vollständiges Inventar, kanonische Pfade,
NOFOLLOW und reguläre Dateien bleiben Voraussetzung. Der atomare Umzug ersetzt
keine dieser Prüfungen. Nach dem Umzug werden Inodeidentität, Länge und mtime
verglichen; nur final-only wird akzeptiert.

## Reihenfolge, Haltbarkeit und Wiederanlauf

1. CLOSED-Job, Action und vollständiges Inventar autorisiert lesen; Room und
   DB-Key-Lease schließen.
2. Staging vollständig entschlüsseln und authentifizieren, echtes EOF, exakte
   Länge, 25-MiB-Grenze und tatsächliches Format prüfen.
3. Job erneut autorisieren, Room schließen; atomar ohne Ersetzen verschieben.
4. Final-only und Dateidentität prüfen, finales und Stagingverzeichnis jeweils
   mit dem vorhandenen Android-`fsync` synchronisieren; Gates erneut prüfen.
5. CLOSED → FILE_PUBLISHED in kurzer SQLCipher-Transaktion mit COMMIT-Gate.
6. Finalen Ciphertext erneut vollständig verifizieren.
7. Dokumenteninsert und Jobdelete in genau einer SQLCipher-Transaktion.
8. Erst anschließend darf der bestehende Repository-Publication-Gate einen
   Erfolg an den aktuellen UI-Auftrag liefern.

Verzeichnis-`fsync` wird auf dem getesteten Gerät tatsächlich ausgeführt;
Fehler verhindern den Phasencommit. Keine universelle Power-Loss-Durability
oder gemeinsame Atomarität von DB und Dateisystem wird behauptet.

| Unterbrechung | Persistenter Zustand / erneuter MAINTENANCE-Lauf |
| --- | --- |
| Vor Move | CLOSED + staging-only: erneut vollständig prüfen und verschieben |
| Nach Move, vor Sync oder Phasencommit | CLOSED + final-only: erneut vollständig prüfen, beide Verzeichnisse synchronisieren, Phase committen |
| Nach FILE_PUBLISHED | final-only: erneut vollständig prüfen; atomarer Dokumentcommit |
| Insert/Jobdelete scheitert | Rollback beider DB-Änderungen; FILE_PUBLISHED bleibt |
| Commit gelungen, anschließend Revoke | Dokument darf bestehen; kein alter Success-Callback und keine Rollbackbehauptung |
| Beide Namen oder kein Name bei CLOSED | BLOCKED; keine heuristische Reparatur |
| Unbekannter Bestand, Symlink, Korruption | BLOCKED, betroffenen Bestand erhalten |

Atomic rename erzeugt keinen regulären Both-Names-Zwischenzustand. Ein solcher
Bestand aus früherer Linkpublication bleibt ausdrücklich BLOCKED. Keine neue
Phase und keine Migration; bestehende Reconciliation unterstützt staging-only
und final-only bereits. Delete und Delete-Recovery bleiben im bestehenden
Protokoll ohne Secure-Erase-Garantie.

## SAF, Lock und bewusste Fortsetzung

Der bestehende OpenDocument-Systempicker läuft unverändert außerhalb der
READY-Sitzung. Pickerstart sperrt PaG. Der Callback öffnet keinen Stream und
führt keine Providerabfrage aus. URI und einmaliges Ticket bleiben ausschließlich
im Activity-RAM; keine persistable URI-Permission und kein SavedState-Restore.

Eine Auswahl wird erst an eine tatsächlich neu veröffentlichte READY-Generation
gebunden. Die alte Generation und ein nur behaupteter Generationswert reichen
nicht. Erst nach bewusster Auswahl derselben Aktion und Öffnen von Dokumente:

- „Datei ausgewählt – Import noch nicht abgeschlossen.“
- „Import fortsetzen“ öffnet Namen-/Formatbestätigung.
- „Import bestätigen“ verbraucht die Quelle einmalig.

Die Handoff-Verfügbarkeit ist Compose-beobachtbar über einen rein technischen
Änderungszähler; die URI wird dadurch weder UI-Fachdaten noch persistenter State.
Abbruch oder verworfene Auswahl wird in derselben Aktion neutral als nicht mehr
verfügbar kenntlich gemacht. Bewusstes Verlassen der Aktion/des Bereichs und
Aktionswechsel verwerfen die Auswahl weiterhin. Lock nach der neuen READY-
Bindung verwirft sie ebenfalls. Kein automatischer Import oder Retry.

Der Quellstream wird im Worker erst nach dauerhafter IMPORT_WRITING-Phase
geöffnet, höchstens einmal, dann geschlossen. Fehlende Quellberechtigung wird
verständlich angezeigt. Die bestehende Pipeline bleibt Staging → Publication;
Quelle und ihre eingebetteten Metadaten werden nicht verändert. Die Originaldatei
außerhalb von PaG wird nicht geschützt oder gelöscht.

## Grenzen und Abnahme

Keine Viewer, externe Öffnung, Export, Sharing, Klartexttempfiles oder neue
Permissions. Schema 11, Authpfad, Keyhierarchie, 180/30-Sekunden-Fristen und
Revoke-/COMMIT-/Publication-Gates bleiben unverändert.

### Bereits ausgeführt

- Native Debug-Kompilierung für arm64-v8a, armeabi-v7a, x86 und x86_64 erfolgreich.
- 96 gezielte Data-Hosttests: Publication 20, Reconciliation 36, Delete 29,
  ProductStorage 11, alle bestanden. Host-Move ist ein Testdouble, kein Beleg
  für Android-Kernel-No-Overwrite.
- 22 App-Hosttests: DocumentModel 14, DocumentWorkspace 8, bestanden;
  darunter explizite Reauth-/Bestätigungsfolge, neue READY-Bindung, beobachtbare
  Handoffverfügbarkeit, Abbruch, Lock, verspätete Callbacks und Navigation.
- Die fünf zuvor betroffenen isolierten Gerätetests bestehen mit der neuen
  Publication auf **Samsung SM-M526BR, Android API 33**: echte SQLCipher-/Room-
  Publication und zweiter Unlock, Revoke nach physischem Move mit Recovery,
  Delete-Rollback/Pending-Recovery, Kernel-No-Overwrite/NOFOLLOW/Verzeichnissync
  sowie vertrauenswürdige Android-Pfadkanonisierung.

- Sieben zusätzliche reale Samsung-Tests bestanden: vier Formate mit einmaligem
  Source-Open, vollständiger Publication, erneutem Maintenance, wiederholter
  Invocation und Löschung; echte Zielkollision nach Inventarprüfung; Korruption
  im Staging; unbekannter Blob/Symlink ohne Cleanup; Revoke vor FILE_PUBLISHED-
  Commit mit final-only-Recovery; finaler SQLCipher-Transaktionsrollback und
  Wiederanlauf; Korruption nach FILE_PUBLISHED ohne Dokumentencommit.
- Zwei abschließende Samsung-Tests bestanden: Revoke nach tatsächlich erfolgtem
  finalem Commit erhält das Dokument ohne Publication; DELETE_RESERVED und
  bereits unlinked DELETE_FILE_PENDING werden mit frischem Maintenance korrekt
  abgeschlossen. Insgesamt **14 reale, isolierte Android-Tests** grün.
- Weitere 21 gezielte Data-Hosttests bestanden: Dispatch 7, vertrauenswürdige
  Pfadgrenze 2, Diagnose 12. Zusammen 117 verschiedene Data- und 22 App-Hosttests.
- App-Debug-APK und App-/Data-AndroidTest-APKs erfolgreich gebaut;
  `verifyArchitecture` und `verifyPrototypeManifest` bestanden. Schemaexport,
  Permissions, Manifest, Auth- und Securityimplementierung unverändert.
- Installierte und neue Produkt-APK haben denselben geprüften Signer.
  Installation ausschließlich per `adb install -r` erfolgreich; Produktdaten
  wurden nicht gelöscht oder ersetzt. Die vier Picker-Beispieldateien wurden
  neu synthetisch erzeugt und in einen separaten öffentlichen Download-Testordner
  kopiert, ohne vorhandene Originaldateien anzutasten.

Die Unterbrechungsfälle wurden mit gezielter Fault-/Revoke-Injektion auf dem
realen Dateisystem und SQLCipher geprüft, nicht durch physische Stromausfälle.
Temporäre Diagnose-Loglevel sind auf dem Gerät nicht aktiviert.

## Gerätebefund zur verlorenen Picker-Auswahl

Der erste manuelle Durchlauf scheiterte trotz funktionierendem Storage. Eine
gezielte rein technische Diagnose zeigte:

`BEGIN → ACTIVITY_PAUSE → ACTIVITY_DESTROY → CLEAR → ACTIVITY_CREATE → RESULT_MISMATCH`.

Android beendete die Activity vor Ergebniszustellung. Dadurch gingen das
Activity-lokale Ticket und die Übergabe verloren; die neue Activity ignorierte
das ungebundene Ergebnis korrekt. Kein Source-Open oder automatischer Import.

Die Android-Entwickleroption „Aktivitäten nicht speichern“ war aktiv. Ein
zunächst per ADB gesetzter Global-Settings-Wert `0` reichte nicht: Der laufende
ActivityManager meldete weiterhin `mAlwaysFinishActivities=true`. Die erste
Prüfung des gespeicherten Werts war somit kein Nachweis wirksamer Deaktivierung.
Androids [Einstellungscontroller](https://android.googlesource.com/platform/packages/apps/Settings/+/4f6e4bfa4f5c899ef9fda6dfa772537adb110b8a/src/com/android/settings/development/KeepActivitiesPreferenceController.java)
aktualisiert mit `setAlwaysFinish` auch den laufenden Dienst.

Die Nutzenden schalteten deshalb ausschließlich diese Option im regulären
Einstellungsdialog einmal ein und wieder aus. Danach: gespeicherter Wert `0`,
der zuvor aktive `mAlwaysFinishActivities=true`-Eintrag im erfolgreich vollständig
ausgegebenen ActivityManager-Dump nicht mehr vorhanden. Es wurde keine
explizite `false`-Zeile ausgegeben. Kein versteckter Binderaufruf, keine Änderung
an PaG-Sperre, Sessionfristen, Credentials oder RAM-Handoff-Vertrag.

Der folgende erfolgreiche Durchlauf wurde sowohl durch Nutzendenrückmeldung als
auch durch feste technische Codes bestätigt:

`BEGIN → ACTIVITY_PAUSE → RESULT_MATCH → SELECTED → ACTIVITY_RESUME →
SESSION_AUTHENTICATING → SESSION_MAINTENANCE → SESSION_READY → READY_BOUND →
CONSUMED`.

Zwischen Pickerstart und Rückgabe gab es keine Activity-Neuerstellung.
Die temporäre ausschließlich debuggable Picker-Probe protokollierte keine IDs,
URIs, Metadaten, Inhalte, Exceptions oder Stacktraces. Sie ist nach dieser
Aufzeichnung vollständig aus dem Code entfernt; der spezielle Geräte-Loglevel
ist auf leer zurückgesetzt und kontrolliert. 22 fokussierte App-Hosttests und
der finale Debug-Build ohne Picker-Probe bestanden erneut. Die bestehende enge
Maintenance-Probe bleibt unverändert rein beobachtend.

Bei tatsächlicher Activity-/Prozessbeendigung bleibt die Auswahl bewusst
verloren. Kein SavedState-/URI-Persistenz-Workaround. Für reproduzierbare Tests
muss der tatsächlich aktive Android-Entwicklerschalter geprüft werden; allein
sein persistierter Settings-Wert genügt nicht.

## Synthetische Testdateien und tatsächliche Produkt-UI-Abnahme

Die vier ADB-Testdateien lagen physisch unter
`Download/PaG-P10b2e-synthetic/`; der Picker zeigte zunächst keine Elemente.
Nur diese vier Testdateien wurden danach für Androids Medienscan angemeldet.
Eine auf genau diesen Unterordner beschränkte MediaStore-Abfrage bestätigte
vier Einträge mit passenden MIME-Typen: image/jpeg, image/png, application/pdf
und text/plain. Die vorherige Indexlage wurde nicht erfasst und wird nicht als
bewiesene Ursache behauptet. Andere Dateien oder Produktdaten wurden hierfür
nicht abgefragt, keine App-Permission ergänzt.

Anschließend bestätigten die Nutzenden ausdrücklich:

- „Alle Dokumente erfolgreich hochgeladen“: JPEG, PNG, UTF-8-TEXT und PDF über
  echten Systempicker, reguläre Sperre, neue Credential-Authentifizierung,
  bewusste Rückkehr in dieselbe Aktion und ausdrückliche Importbestätigung.
- „Beide Punkte kann ich bestätigen“ auf die abschließende Prüfung:
  Dokumente bleiben nach erneutem Sperren/Entsperren vorhanden;
  bestätigtes Löschen bleibt nach erneutem Sperren/Entsperren wirksam.

Die finale APK ohne Picker-Probe wurde signaturgleich ausschließlich als Update
installiert. Bestehende App-Daten, Originaldateien und Credentials blieben
unverändert. Kein Commit, Push oder Release.

## Hinweise für spätere FAQ und Troubleshooting

Die [FAQ-/Troubleshooting-Vorlage](troubleshooting-documents.md) hält die
erforderlich deaktivierte Entwickleroption, nicht benötigtes USB-Debugging,
den Sonderfall gespeicherter/aktiver Systemwerte sowie den bewussten
Picker-/Reauth-Ablauf und die Viewergrenze fest.

## Abschließende Bewertung und Grenzen

**GRÜN — P10b2e bereit für Code-Review.** Der vereinbarte echte Android-
Importweg ist einschließlich vier Formaten, Persistenz, Löschung und gezielten
Storage-/Recovery-Sicherheitsprüfungen bestätigt. `git diff --check` bestanden.

Keine offenen Blocker für diesen Slice. Dateiinhaltsanzeige bleibt bewusst
nicht implementiert. Geräteabnahme gilt für Samsung SM-M526BR/API 33;
andere Geräte/Dateisysteme sind damit nicht automatisch abgenommen. Es wird
keine universelle Crash-/Power-Loss-/Secure-Erase-Garantie behauptet.

## Vollständiger lokaler Änderungsbestand

Einschließlich der erhaltenen vorausgehenden Pfad-/Navigationsfixes und Diagnose:

- `app/src/main/kotlin/org/pag/prototype/MainActivity.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentModel.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentPickerHandoff.kt`
- `app/src/main/kotlin/org/pag/prototype/product/DocumentScreen.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentModelTest.kt`
- `app/src/test/kotlin/org/pag/prototype/product/DocumentWorkspaceTest.kt`
- `data/build.gradle.kts`
- `data/src/androidTest/kotlin/org/pag/data/product/DocumentReconciliationStorageTest.kt`
- `data/src/main/cpp/Android.mk`
- `data/src/main/cpp/document_rename.c`
- `data/src/main/kotlin/org/pag/data/product/DocumentAtomicRename.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentFileInventory.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentPublication.kt`
- `data/src/main/kotlin/org/pag/data/product/DocumentReconciliation.kt`
- `data/src/main/kotlin/org/pag/data/product/MaintenanceProbe.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductDocumentRepository.kt`
- `data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentDeleteTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPlatformRootTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentProductStorageTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentPublicationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/DocumentReconciliationTest.kt`
- `data/src/test/kotlin/org/pag/data/product/MaintenanceProbeTest.kt`
- `docs/fix-document-navigation.md`
- `docs/fix-document-unlock-path.md`
- `docs/maintenance-probe.md`
- `docs/product-p10b2c2-documents-publication.md`
- `docs/product-p10b2c4-documents-reconciliation.md`
- `docs/product-p10b2e-android-document-import-fix.md`
- `docs/troubleshooting-documents.md`
