# §86b – Full-Workspace ohne Compact-Port: Machbarkeitsprüfung, WIP

Basis `0a8f785ad8611de8c9c7de6ebfaca7936541353a`, Branch
`p11-performance-optimization`, #37 / #33 / #34; Release-Tracker #44.
Fortsetzung der abgebrochenen Sitzung am 2026-10-23. Frühere Rohbelege vom
2026-10-22 bleiben erhalten; keine erneut erfundenen Geräteergebnisse.

**Entscheidung: Der bestehende Fullview-only-Pfad ist unter den unveränderten
Anforderungen kein belegbar tragfähiger 1.0-Kandidat. #37 muss zusammen mit
#33/#34 architektonisch gelöst werden.** Das ist ein technisches Prüfergebnis,
keine Produktentscheidung, keine Aktivierung und kein Produktionsfallback.
Funktionierende Navigation allein ersetzt weder gemeinsame Admission noch
begrenzte Compose-/Editor-/Nativekosten. Ein numerischer Beweis, dass jeder
Fullview zwingend >8+8 MiB benötigt, wird ausdrücklich nicht behauptet.

## Minimaler Testpfad

`P86FullWorkspaceActivity` liegt ausschließlich in `app/src/p77runtime` und
spiegelt MainActivity mit unveränderten Lifecycle-/Session-/Dokument-Gates.
Abweichungen: isolierter Package-/Buildguard, **kein `bindActiveCore`**, ein
payloadfreies Compose-Commit-Merkmal für Messungen. Der echte ProductViewModel
bindet den echten ProductRuntime-Workspace; echte Repositories/Room/SQLCipher,
bestehende synthetische P77-Authentifizierung und isolierter noBackup-Fixtureroot.
Keine zweite Datenautorität, kein Cache. P77-Reuse wird nur für diese Activity
auf false gesetzt; Produktgates und normale MainActivity bleiben unverändert.
Siehe [vollständiger Rootvergleich](activity-root-diff-final.patch).

`P86WorkspaceDiagnostics` dekoriert ausschließlich P77-Datenbankseams der
Workspace-/Action-/Task-/Operations-/Personrepositories per Reflection. Das ist
Testinstrumentierung, keine allgemeine Laufzeit-API und keine zweite DB-Haltung.
Gezählte Statement-/Cursor-/Room-/Driver-Enden sind keine Allocator-Enden und
decken nicht automatisch sämtliche anderen Repositories ab.

## Funktionale Belege

**3 vollständige funktionale PASS, 3 Harnessabbrüche. Keine Speicherfreigabe.**

| Rohbeleg | Umfang und Ergebnis |
|---|---|
| [small-02](small-02.txt) | FUNCTIONAL_PASS: 20 Personen/40 Tasks je Aktion; echte PREPARATION→ACTIVE→COMPLETED-Schreibvorgänge, Vorbereitung Übersicht/Personen/Rollen/Tasks, ACTIVE Hub/Personen/Funktionen/Tasks, Taskdetail, Editor Abbruch/Save, Rücknavigation, Lock/Reauth, verspätetes Main vor Navigation und Lock. |
| [stress32-01](stress32-01.txt) | FUNCTIONAL_PASS: 200 Personen/1.000 Tasks/129.000 Events in ACTIVE, 32 lange Rollennamen; zwei Navigationsrunden, dieselben Editor-/Lock-/Callbackfälle. |
| [stress100-03](stress100-03.txt) | FUNCTIONAL_PASS: 200/1.000/129.000 mit 100 langen Rollen; zwei Navigationsrunden, Auf-/Zuklappen der 99 ungenutzten Funktionen, vier Historyseiten einschließlich numerischer Touch-IME, Editor Abbruch/Save, späte Callbacks/Lock/Reauth und echter ACTIVE→COMPLETED-Write, danach read-only. |

Stressfixtures enthalten Unicode-Langtexte (Ä/界/🦋), 128.000 zusätzliche Events
auf dem ersten Task plus 1.000 CREATED-Events. SQL bestätigt 129.000 Events.
Paging prüft 128.001 Events des ersten Tasks, Seiten 1/2/4.001/2.001 mit maximal
32 Einträgen, Gesamtzahl, Reihenfolge und IDs. Direkte Mitte über echte injizierte
Touchereignisse auf der numerischen Geräte-IME; kein actionweiter Eventload.
Workspace-Census verlangt `events` leer und `eventsComplete != true`.
Editor öffnet, verwirft und speichert unveränderte Eingabe mit echtem Writer;
dies ist keine neue umfassende Editor-/CAS-/UNKNOWN-Abnahme.
Im 32-Rollen-Beleg sind maximale Textlängen 73/63/118 UTF16-Einheiten
(Pseudonym/Rolle/Tasktitel). Die volle Phasenfolge wird mit kleiner Fixture geprüft;
der große Fall beginnt in ACTIVE. UI-Lock/Reauth und verspätete Publication werden
geprüft, kein neuer OS-Keyguard-/180-s-/Permission-/Recovery-Vollaudit (#39).

Frühere Harnessabbrüche bleiben sichtbar: [small-01](small-01.txt)
erreichte die Aktionslisten-Schaltfläche nicht, [stress100-01](stress100-01.txt)
erreichte den Knopf unter der langen Funktionskarte nicht;
[stress100-02](stress100-02.txt) bestand Auf-/Zuklappen, erreichte danach den
Header „Zum Lagebild“ nicht. Kein vollständiges PASS daraus.
Die Fortsetzung erhöht allein die echten Scrollsuchlimits für „Weitere Funktionen“
und die Headerrückkehr auf 80 Versuche; Produktinhalt, Sessionfristen und
Assertions bleiben bestehen. APK-Zuordnung der ersten Fortsetzung:
[resume-apks-02.json](resume-apks-02.json), endgültiger Runner:
[resume-apks.json](resume-apks.json).

## Touch→OnDraw und Ressourcen

Start ist `System.nanoTime()` unmittelbar vor injiziertem **ACTION_UP**, Ende
der erste beobachtete Decor-OnDraw, bei dem Zielzustand und Compose-SideEffect-
Commit-Merkmal zusammenpassen. Die vorbereitende Suche/Scrollzeit ist nicht
enthalten. Das sind echte Android-Eingaben, keine direkten Modell-Navigationen;
`physical_tap=true` bezeichnet Injektion über UiAutomation, keinen menschlichen
Finger. Kein SurfaceFlinger-/Display-Present- oder Consumer-Retirementnachweis.
Commit-Merkmale verwenden Objektidentitätshashes und sind reine Testheuristik.
Sampler/Reflection/UIAutomation verursachen Zusatzkosten; keine kontrollierte
Benchmarkserie, keine p95 und keine allgemeine Gerätegarantie.

| Einzelmessungen in ms | klein | groß, 32 Rollen |
|---|---:|---:|
| Personen, zwei Runden | 1.400 / 1.379 | 5.195 / 4.938 |
| Funktionen, zwei Runden | 1.317 / 1.325 | 3.326 / 3.298 |
| Tasks, zwei Runden | 1.384 / 1.379 | 5.001 / 4.981 |
| Taskdetail / Editor öffnen | 35 / 109 | 40 / 60 |
| Editor speichern | 3.193 | 8.002 |
| History Seiten 1 / 2 / Ende / Mitte | – | 4.989 / 4.926 / 5.336 / 5.343 |

Die mehrsekündigen erneuten Reads erfüllen das schnelle ACTIVE-Navigationsziel
aus #38/#44 nicht. Der schnelle Detail-/Editorwechsel nutzt bereits geladenes
Material und darf nicht als schnelle gesamte Navigation ausgegeben werden.
Vollständige Einzelwerte: [measurements-final.json](measurements-final.json),
reproduzierbar mit [summarize.py](summarize.py). Frühere Ableitung bleibt erhalten.
100 Rollen: Personen 5.304/5.137 ms, Funktionen 3.375/3.347 ms, Tasks
5.119/4.994 ms in den zwei Runden; Auf-/Zuklappen 46/27 ms;
Editor öffnen 49 ms, Save 7.986 ms. Große Aktion abschließen bis committed
OnDraw 8.510 ms. Paging 5.007/4.941/5.338/5.372 ms für 1/2/Ende/Mitte.

500-ms-Samples: Runtime totalMemory−freeMemory (ART-used),
Debug.getNativeHeapAllocatedSize (native heap), Debug.MemoryInfo.totalPss (KiB),
FD-Zahl und GC-Zähler. Kleine Fixture: beobachtete Maxima ART 15.247.224 B,
native 12.379.536 B, PSS 160.743 KiB; große 32-Rollen-Fixture: ART 44.271.984 B,
native 16.429.168 B, PSS 233.517 KiB. **Prozesssamples, keine Scopekosten oder
Obergrenzen.** Schon die große Baseline enthält ART 43.321.232 B einschließlich
Fixtureaufbau; weder Gesamtwert noch Baseline-Differenz beweist Budgetüberschreitung
oder Einhaltung. Peaks zwischen Samples und native Grafik-/Treiberkosten fehlen.
Automatische GC lief; es gibt keinen erzwungenen GC/Dispose als Freigabe.
100 Rollen, kompletter Lauf einschließlich COMPLETED: beobachtete Maxima
ART 59.490.168 B, native 45.660.768 B, PSS 246.726 KiB, FD 144.
Die Maxima stammen hier aus `P86_FINAL` nach dem Abschluss und liegen deutlich
über den ACTIVE-Samples; auch dieser Prozessanstieg ist keine isolierte
Workspace-/Composekostenrechnung oder Leckdiagnose. Baseline ART 43.485.072 B,
native 10.148.880 B, PSS 156.789 KiB; natürliche GC-Zähler 0→29.

Klein: 1.254/1.254 Statements geschlossen, 1.211 Cursor-Endprüfungen ohne Fehler.
Groß/32: 852/852, 823 Prüfungen ohne Fehler; je 29 Preflight-/Room-/Driver-Enden.
FD klein 117→137 (Maximum 142), groß/32 115→141 (Maximum 145): kein daraus
ableitbarer FD-/Leak-Freiheitsbeweis oder belegtes stabiles Langzeitplateau.
`P86_FINAL` wird vor Activityfinish/Fixturecleanup genommen; es ist kein
Prozesszustand nach vollständigem UI-/Runtimeende.
Bestätigter Wrapperclose/Cursorpointer null ersetzt keine native Freigabekalibrierung.
100 Rollen: 898/898 Statements, 867 Cursor-Endprüfungen, keine gemeldeten
Close-/Cursorfehler; 32 Preflight- und je 31 Room-/Driver-Enden sind separat
gezählt. FD 117→144. Das sind unterschiedliche Hookereignisse, kein universeller
1:1-Ressourcenbeweis. Dateisystemcleanup aller isolierten Fixtureroots und
unveränderte Version-/Installationsmetadaten der drei anderen Apppakete:
[resume-cleanup.json](resume-cleanup.json),
[vorher](resume-protected-before.json)/[nachher](resume-protected-after.json).

## Konkrete Blocker des 8+8-MiB-Vertrags

1. **Keine gemeinsame Vorabzulassung für Fullview.** `WorkspaceStore.read()`
   materialisiert vollständige Operations-/Taskboards. ProductWorkspaceRepository
   und VM nehmen keine gemeinsame begrenzende Fullview-/Editor-/Composebuchung
   vor der Materialisierung vor. Die vorhandene Compact-Admission ersetzt dies
   bei ungebundenem Port nicht. 32/100 Rollen werden hier vollständig gelesen;
   ein erfolgreiches Lesen ist keine Budgetfreigabe.
2. **Konkreter doppelter Payloadbestand:** Ein echter bereits queued Maincallback
   hält den alten Workspace, während nach Navigation ein neuer Workspace vorliegt.
   `P86_OVERLAP`/`held_old_plus_current` belegen zwei lebende Graphen. Ticket-/VM-
   Fences verweigern späte Publication, löschen aber die Capture nicht rückwirkend.
   Der Test beobachtet nur direkte synthetische Resultcaptures, keine Schlüssel.
3. **Unbekannte relevante Kopien:** DTO-Census zählt nur bestimmte erreichbare
   Domain-/Workspaceobjekte, Strings und Collectiongrößen identitätsbereinigt.
   Listen-Kapazitäten werden dabei nur mit Größe angesetzt; Maps, Room-Zeilen,
   Builders, temporäre Filter/Sortierungen, Callbackstacks, TextField-/Layout-/
   Compose-/Render-/Nativekopien fehlen. Großer 32-Rollen-Workspace ca. 1,33 MB,
   Alt+Neu ca. 2,66 MB **partielle Engineeringcharge**, keine Vollkosten und
   keine physische Unter-/Obergrenze. Nicht mit 8 MiB „Restbudget“ verrechnen.
   Bei 100 Rollen beträgt dieselbe partielle Alt+Neu-Charge 2.748.749 B;
   auch hier bleiben die fehlenden Kosten UNKNOWN.
4. **Kein belegtes Ende exponierter Fullview-/Editor-/Composeverbraucher.**
   Unpublish/Lock verhindert geprüfte neue Veröffentlichung, ist kein Beweis,
   dass alte Strings, gerenderte Texte und Formularrepräsentationen beendet sind.
   Derselbe wesentliche Nachweisbedarf wie §86a bleibt ohne Compact-Port bestehen.
5. **Native Übergangskosten unbekannt:** SQLCipher/Room/CursorWindow und die
   Paging-Sortierung (TEMP B-TREE/OFFSET aus §85b) sowie Text-/Rendererkosten sind
   nicht gemeinsam begrenzt/kalibriert. Close-Zähler und PSS schaffen keine
   garantierte 8-MiB-Transitionhülle. UNKNOWN bleibt UNKNOWN.

Die reinen DTO-Zahlen schließen eine spätere budgetierte Fullviewkonstruktion
nicht mathematisch aus. Der **bestehende** Pfad hat aber weder die notwendige
Vorabbegrenzung noch den Endnachweis und ist zugleich langsam. Ein weiterer
Callback oder eine höhere pauschale Reserve behebt diese konkreten Lücken nicht.
Erforderlich bleibt ein gemeinsamer Owner-/Admission-/Consumervertrag (#37,
#33/#34) mit begrenzten Ressourcen; #32 und #38 bleiben ebenfalls offen.

## Reproduktion und unveränderte Grenzen

Gezielter Offline-Build und Hostklassen in der Fortsetzung:

```sh
./gradlew -PpagTestBuildType=p77runtime :app:testP77runtimeUnitTest \
  --tests org.pag.prototype.product.TaskModelTest \
  --tests org.pag.prototype.product.WorkspaceBatchTest \
  :app:assembleP77runtime :app:assembleP77runtimeAndroidTest --offline --console=plain
python3 docs/p11-p86b/run-device.py install
python3 docs/p11-p86b/run-device.py run p86-stress100 stress100-03
python3 docs/p11-p86b/run-device.py after
python3 docs/p11-p86b/summarize.py
```

[Build/Host](resume-build-host-04.log): PASS; 27 TaskModel + 21 WorkspaceBatch =
48 Tests, 0 Fehler/Skips; neue XMLs mit `resume-TEST-`-Präfix. Alte 9 gezielte
Testausführungen werden nicht als 9 zusätzliche unabhängige Tests addiert.
Frühere Buildfehler und die Fortsetzungsfehler bleiben protokolliert:
Sandbox-Cachelock, Projektcache ohne AGP, fehlende P77-Task ohne die Variantproperty.
Mit korrekter Property und vorhandenem Standardcache offline erfolgreich.
Nach der zweiten reinen Scrollharnesskorrektur:
[P77-/Runner-Build](resume-build-05.log) PASS; kein weiterer Host-Suitelauf.

[Releaseisolation](isolation.json), [Build](release-isolation-build.log):
P86-/P77-Harness-/Fixturebezeichner und neue Activity fehlen im Releaseartefakt.
Produktquellen und Buildkonfiguration sind gegenüber der Basis unverändert.
Die Fortsetzung bestätigt dies sowie die Abwesenheit aller P86-/P77-Testklassen
im vorhandenen Releaseartefakt erneut mit [verify-isolation.py](verify-isolation.py)
und [resume-isolation.json](resume-isolation.json), ohne Releaseinstallation.
Die neuen Klassen liegen in P77/debug/androidTest, keine neue Dependency,
Schema-/Crypto-/Berechtigungs-/Netzwerkänderung. Keine Änderung an Writer-/UNKNOWN-,
Frist- (180/30 s), Reuse-, Retirement- oder Budgetregeln; kein Produktupdate.
APK-/Signerprüfung: [resume-apks.json](resume-apks.json), Gerät: [resume-device.txt](resume-device.txt).

**WIP sichern, #37/#44 aktualisieren, STOPP für unabhängigen Review.
Kein Merge, Release oder 1.0-Scopebeschluss.**
