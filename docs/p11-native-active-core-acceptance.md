# §71: spätere isolierte ACTIVE-Core-Abnahme

Vorbereitung, keine Ausführungsfreigabe. In §71 wurde weder installiert noch instrumentiert.
Produkt-RAM-Reuse bleibt false. Bestehende Installationen `org.pag.prototype.a` und
`org.pag.perf.p11probe` dürfen nicht verändert werden.

## Voraussetzungen vor jedem späteren Lauf

Separate ausdrückliche Gerätefreigabe; ausschließlich isoliertes Testgerät/-profil und
vorher bestätigte Paketidentität `org.pag.data.unlockprobe`. Bei bereits vorhandener
Installation dieses Pakets abbrechen und Eigentum/Isolation separat klären. Keine
Benutzercredentials, keine Produktdaten, keine pauschale Deinstallation oder Bereinigung.
Test-APK und Target-APK müssen zum geprüften Commit gehören. Vor Fixture-/DB-/Keyzugriff
prüfen die neuen Tests und die Core-Fixture-Brücken die Target-Paketidentität.
Die bestehende Fixture erlaubt für bestehende andere Testpfade weiterhin auch das
Perf-Paket; dies ist ausdrücklich keine Erlaubnis, dessen Installation zu verwenden.

Nur nach dieser Freigabe: isolierte APKs separat bauen/prüfen/installieren. Runner und
Test-Application-ID aus den gebauten Manifesten bestimmen (nicht raten). Danach gezielt:

```
adb shell am instrument -w -e class org.pag.data.product.P11ActiveCoreNativeTest <geprüftes-testpaket>/androidx.test.runner.AndroidJUnitRunner
```

Dieser Befehl ist hier ausschließlich dokumentiert, nicht ausgeführt. Kein unselektierter
`connectedAndroidTest`-Lauf: vorhandene Suites besitzen andere Paket-/Fixtureannahmen.

## Vorbereitete echte Produktpfade

`P11ActiveCoreNativeTest` → Debug-Fixture → ProductActiveCoreLoader →
ProductActiveCoreReader.readForTicket → ActiveCoreStore → CompactPeopleStore /
CompactTaskStore → autorisierte Room-Readtransaktion → ActionDatabaseAccess / B2Driver /
SQLCipher; SessionDbKeyOwner.withDbKey umschließt den Zugriff. Rückkehr aus dem Reader
liegt nach DB-/Keyscopes; das ist keine native Zählerbeobachtung der Freigabe.

Drei synthetische Bestände: 4/8, 64/200, 200/1000 Personen/Aufgaben. Pro Bestand
22 komplette Reads: Indizes -2/-1 Warmup, 0..19 Messstichproben. Globale Peoplecounts,
Seitengröße und hasMore werden geprüft; keine vollständige Liste behauptet. FD-Anzahl,
NativeHeap, PSS, ART-GC-Zähler und gesamte Read-/Callbacklatenz werden nach jedem Read
beobachtet. Keine GC-Erzwingung, kein FD-/PSS-Schwellwert als Leakfreiheit. Messung enthält
auch Beobachtungs- und Harnesskosten. Median/p95 erst aus tatsächlich erfassten Samples
berechnen; 20 Samples sind explorativ und noch kein belastbarer Release-p95-Beleg.

Non-ACTIVE/missing Action und Revoke nach Read/vor Main sind vorbereitet. Der
Fixturekontext ist synthetisch; dies ersetzt keine reale Android-Session-/Lifecycleabnahme.

## #32: konkreter RED-Test, kein Driverfix

Lokale Bytecodeinspektion des gepinnten SQLCipher-4.19.1-AAR:
SQLCipherStatement.reset ruft Cursor.close auf und nullt cursor;
SQLCipherStatement.close setzt lediglich closed=true. B2Driver delegiert close.
`statementCloseMustReleaseQueryCursor_RED32` erfasst den echten Querycursor via debug-only
Reflection und verlangt isClosed nach statement.close. Erwartet RED; noch NICHT AUSGEFÜHRT.
Ein finally reset schließt die Testressource auch bei Assertionfehler. Reflection ist an
4.19.1 gebunden, kein Produktinterface. Room usePrepared und Connection-Close allein
belegen deshalb nicht den einzelnen Cursorabschluss. Nächster separater Fix muss
reset-vor-close inklusive Ausnahme-/Revoke-/Rollback-/Doppelcloseverhalten prüfen.
Keine Änderung von Cipherparametern/Keys oder globalem Driver in §71.

## Noch zu implementierende native Abnahmematrix (BLOCKER)

Gezielte Hooks fehlen weiterhin für SQL-Exception, Budgetverweigerung im echten Store,
Revoke/Lock während SQL-Batch, BLOCKED/UNKNOWN, DOCUMENT-Beginn während Read sowie
unbestätigtes DB-/Keycleanup. Vor Ausführung ergänzen: mehrere nachweisbare SQLite-Batches,
wachsende TaskEventbestände, wiederholte Abbrüche und Wiederöffnungen; Statements,
Cursor/CursorWindow, Connections und Keyleases getrennt zählen. Bestehende Hosttests
liefern diese nativen Belege nicht. Die drei Wiederholungen und FD+2-Regel im alten
BoundedActiveRoomTest bleiben eine Regression, keine Leakfreiheitsfreigabe.

Lifecycle-Matrix vor RAM-Freigabe: echte READY→LOCKED, 30s idle, 180s absolut, BLOCKED,
Hintergrund, Keyguard, Action-/Generationswechsel, Writer und Revoke zwischen Read und
Publication. Instrumentierung muss synchrone Lifetime-Unbrauchbarkeit separat von
Main-Compose-Entfernung und bereits gerenderten Pixeln beobachten. Keine Behauptung
physischer Heap-/Frame-Löschung. Diese neue Lifecycleinstrumentierung fehlt noch.

Referenzüberlappung messen: alter Slot + neuer Kandidat + Compose-Snapshot + wartender
Main-Runnable + Editorreferenzen + Frame. 8 MiB retained / 8 MiB transition unverändert;
kein doppeltes Budget, keine Payloadakkumulation, BUDGET_REFUSED bleibt Rejection.

## Content-OnDraw: noch gesperrte Folgemessung

Der vorhandene P11PerformanceProbe misst Draw/Frames, bindet aber noch nicht den neuen
Corepfad und läuft im gesperrten bestehenden Perf-Paket. Deshalb NICHT dafür ausführen.
Erst separaten isolierten Host mit produktivem Runtime/ViewModel/Coreport und synthetischer
Session vorbereiten. Sequenz HUB→PERSONS→FUNCTIONS→TASKS→HUB; je Klick Requestbeginn,
autorisierten Contentzustand, ersten zugehörigen OnDraw und FrameMetrics korrelieren.
Frischer DB-Read und später separat freigegebener RAM-Hit sind unterschiedliche Serien.
Nach Warmup Median/p95, Framezeiten, GC/Speicher, Fallback/Revoke berichten. Ziel100–150ms,
keine aktuellen Messwerte. Keine Gateänderung für eine Messung. RAM-Hitserie bleibt gesperrt.
