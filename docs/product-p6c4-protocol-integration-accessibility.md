# P6c4 — Aktionsprotokoll Integration / Accessibility / Abschlussabnahme

## Baseline und Freeze

Ausgangsbaseline des bestätigten ersten Laufs vom 14.10.2026: sauberer Branch `p6c4-protocol-integration-accessibility`, HEAD
`5e3541b` (P6c3-Merge), ActionDatabase Schema 7. Verbindlich bleiben
[P6a.1](product-p6a-protocol.md), [P6b](product-p6b-protocol-implementation.md),
[P6c1](product-p6c1-protocol-storage.md), [P6c2](product-p6c2-protocol-active-ui.md)
und [P6c3](product-p6c3-protocol-completed-followup.md).

Vor Änderungen SHA-256 für 23 Dateien erfasst: Protocol-Domain, ProtocolDatabase,
ProductProtocolRepository, ActionDatabase, Schemaexport 7, alle vorhandenen
Security-Quellen/-Tests/-Manifest sowie Produktmanifest und Gradle-Builddateien.
Manifest der eingefrorenen Pfad/Hash-Paare (sortiertes JSON) SHA-256:
`e65f07c5d1df0f812f11dc3d9fca6ba9bd39e63b0cf4d5219be942bc0fd30b99`.
Schemaexport 7: `271c2bacb6bb491a86da489e30708a07d4b78f5d6e93e9184575173fa75f4b63`.
Zusätzlich kompletter Git-Diff gegen Domain/Data/Security, Manifest-/Backupregeln
und Dependencydateien prüfen. Rohinventar und technische Logs ausschließlich in
ignorierter `.prototype-cache/p6c4-*`; keine produktiven Inhalte.

Wiederaufnahme nach abgebrochener Session: zunächst `main` auf demselben HEAD
mit sechs geänderten und drei neuen P6c4-Dateien vorgefunden. Der Benutzer hat
bestätigt, dass dies die Ergebnisse des bisherigen Laufs sind, und deren
Weiterführung ausdrücklich freigegeben. Wechsel auf den bereits vorhandenen
P6c4-Branch ohne Verwerfen von Änderungen; HEAD bleibt `5e3541b`. Alle 23
ursprünglichen Freezehashes stimmen. Vor weiteren Ergänzungen zusätzlich
29 Pfad/Hash-Paare einschließlich Runtime, Wrapper und Backupregeln erfasst:
`.prototype-cache/p6c4-resumed-freeze.json`, SHA-256
`3d4ba232e2b544cfccd8a3d3568992e7039bca1cd6b496fdd43ffd01444a7ae7`.
Die Wiederaufnahme wird ausdrücklich nicht als neuer sauberer Ausgangsstand ausgegeben.

## Integration und Isolation

Der neue P6c4ProtocolIntegrationTest verwendet echtes ProductViewModel mit
synthetischen, verzögerten Domainports. Bestehende Tests werden weiterverwendet;
die Ergänzungen prüfen gezielt Cross-Area-Verträge statt alle Einzelregeln neu
zu testen.

| Vertrag | Automatisierte Abnahme |
| --- | --- |
| ACTIVE → COMPLETED | Timeline, Detail, Create, Correction, Addendum, History/alte Fassung und Irrtumsbestätigung über echten Lifecyclepfad; kompletter Cleanup, kein alter Save/Expected und keine automatische Nachbereitung. |
| COMPLETED → DELETION_PENDING | Autoritative neue Actionprojektion schließt sämtliche Kontexte; kein Read/Write und alte Callbackpublication verworfen. Keine Löschfunktion eingeführt. |
| Protokoll → andere Bereiche | Alle sieben Kontexte gegen Personen/Aufgaben/Funktionen/Details/Hub; Timeline, Detail, Draft, Quellen-/Personen-/Kennungsbezüge, Filter, Disclosures, Confirmation und Fehler/Busy weg. |
| Lockmatrix | Beide Phasen × sieben Kontexte über Sessionobserver/echtes ViewModel; verspätete Timeline-/Detailcallbacks verworfen und Reauth ohne Wiederherstellung. Bestehende Tests ergänzen Save-/Revokegrenzen. |
| STALE | Beide Phasen, echte ViewModelintegration: Draft/Expected erhalten, erneuter Save ohne zweiten Write, bewusste aktuelle Fassung lesen, erst danach neuer Korrekturentwurf. |
| Summary nach Write | Ausschließlich neue Repositorysummary; alte vollständige Publication bis Boards vollständig, keine optimistische lokale Zählung, keine Interaction durch Callback/Refresh/Displaytick. |
| Fremddomains | Mutationports für P4/P5a/Personen schlagen bei unerwarteten Aufrufen fehl. P3b-Öffnung und Actionwrites werden geprüft; nur ausdrücklich angeforderter Abschluss ist erlaubt. COMPLETED-read-only-Regression aus P6c3 bleibt erhalten. |
| Späte Writergebnisse | Beide Phasen × Lock/Aktionswechsel/Phasenexit × Erfolg/STALE: kein Detail-/Summary-/Boardrefresh, keine neue Interaction, kein alter Fehler und kein Feedback. |
| Andere Editoren → P6 | Aufgabenentwurf, Funktionsentwurf und geöffnete Person verschwinden beim P6-Einstieg; kein fremder Write, kein P3b-Payload und keine übernommenen Eingaben. |

Navigation und Entwürfe bleiben RAM-only. Keine SavedState-/Preferences-
Restoration, kein generisches COMPLETED-writable und kein Reopen.

## Datenminimierung und Belastungsfälle

P6c4ProtocolIntegrationProbe komponiert echten ProductScreen auf dem Galaxy mit
synthetischen Ports. Detaildaten tragen ausschließlich künstliche
`DETAIL-SENTINEL-*`-Werte für Beschreibung, Ort, Pseudonym, Quelle, Ansage,
attribuierte Rechtsgrundlage und Kennung. Hub/Timeline enthalten diese weder als
Text noch als ContentDescription im durchlaufenen Accessibilitybaum. Hub ruft
kein getEntry oder listTimeline auf; Timeline ruft kein getEntry auf. Bewusster
Entrytap lädt erst dann Detail. Back/Bereichswechsel/Abschluss/Lock entfernen
Detailwerte aus erreichbarem UI-/Accessibilityzustand, keine RAM-Wipegarantie.

Technische Testgrößen, ausdrücklich keine Produktobergrenzen:

- 60 Timelineitems in Repositoryreihenfolge, 30 Schlüsselereignisse; lokale Filter,
  Scroll zum Anfang/Ende, kein Detailprefetch und keine zweite Row pro Revision.
- Ein Entry mit 24 vollständigen Fassungen, unterschiedlichen Revisionstypen,
  eigenen Kennungen/Zeiten und Erfassungsphasen; History eingeklappt, bewusst
  öffnen, Originalfassung ohne spätere Phasenmarker und zurück zum aktuellen Entry.
- 32 P3a-Personen: unabhängige Quellenwahl und Betroffenenset, zugängliche
  Pseudonyme, Mehrfachauswahl mit Checked-State, keine P3b-Reads.
- Maximaler Inhalt mit Supplementary-Unicode: Titel 120, Beschreibung/Ansage
  jeweils 2000, Rechtsgrundlagenfeld 1000, Ort 80 und Kennungswerte 120 Codepoints.
  Echte Form-/Detaildarstellung bleibt vollständig, keine Kürzung/Ellipsis eingeführt.
- 32 Kennungen mit langem DISPLAYED_NAME-Label; deaktivierter Add-Control,
  gezieltes Entfernen/Wiederauffüllen, kein 33. UI-Slot, Save/Cancel am Formularende.

Bulk-Grenzdaten werden über den tatsächlichen ProtocolModel-Entwurf gesetzt;
eine repräsentative Texteingabe erfolgt über Accessibility ACTION_SET_TEXT/IME.
Damit ist die Darstellung/Validierung des vollständigen Inhalts geprüft, aber
keine Behauptung, jede der 32 Zeilen sei einzeln über eine reale Tastatur eingetippt.
Repository-/DB-Invarianten sichern unverändert die vorhandenen P6c1-Tests.

## Accessibility, Layout, Fokus und Zeit

Automatisierte Proben verwenden lokale CompositionLocal-Density/FontScale,
keine globale Systemeinstellung. P6c4 testet kompakte 320-dp-Breite bei Font
Scale 1/2; bestehende P6c2/P6c3-Proben testen die normale Galaxybreite.
Zusätzlich echter ProductScreen mit lokal bereitgestellter virtueller Dichte:
599/601 dp Hub-Inhaltsbreite um die 600-dp-Schwelle, beide Font Scales; fünf
erreichbare Karten mit geprüften Breiten und Touchbounds. Bei zwei Spalten nutzt
die fünfte Karte die volle letzte Zeile; bei großer Schrift einspaltiger Fallback.
Unitcheck prüft exakt 600 dp, 1.3-Fontgrenze und stabile Reihenfolge. Die
Geräteszenarien erhalten jeweils frische Subkompositionen. Keine globale
Dichte-/Font-Einstellung, Orientierungänderung oder Tablet-Neugestaltung.

Native Accessibility-Prüfung: Chronologieheading, klar benannte einzige
Timeline-Clickaktion, Filterselected/checked, Ungefähr-Toggle, Personenchecked,
History-Ausgeklappt-State und disabled Add bei 32. Android kann Semantik auf
Labelcontainer und Clickcontainer verteilen; die Probe untersucht deren Nodepfad.
Vorhandene Controls behalten mindestens 48 dp, Primary 56 dp. Phasen-/Irrtums-/
Ungefährmarker sind Text. Keine Icon-only Kernaktionen oder neue LiveRegion.

Bestehender ProductInputScope/FormInput räumt Fokus/IME auf Save/Cancel/
Navigation/Lock; Create erhält einmaligen Autofokus, Revisionen keinen neuen.
Die Erweiterung des langen Formularflows prüft über WindowInsets eine tatsächlich
sichtbare IME vor und nach Scroll bis zu Save/Cancel, bevor ein Button sie schließt.
Bestehende P6c2/P6c3-Proben prüfen repräsentative IME-Done-Eingaben. Weitere
menschliche Keyboard-/TalkBackprüfung bleibt nötig.

ProductTime bleibt unverändert. ViewModelmatrix testet ACTIVE und COMPLETED
mit Europe/Berlin DST Gap/Overlap, unverändertem Entwurf, unabhängiger Ungefähr-
Angabe und gültigem Retry. Bestehende Datum-/Picker-/Zeitregressionen bleiben grün.
Kein editierbares recordedAt, keine externe Zeitquelle oder Timestampgarantie.

## Loading, Session und Security

Bestehende Atomic-Hub-/P6c2-Tests und neue verzögerte Timeline-/Detailfälle sichern
Initial Loading, Empty erst nach Erfolg, Content, Error/Retry sowie Erhalt gültiger
alter Timeline bei Refreshfehler. Stale Action/Generation/Request publizieren nicht.

Callbacks, Refreshresultate und Countdown sind keine Interaction. Echter Tap
und Draftänderung verwenden den bestehenden guardedInteraction-Vertrag.
180 s absolut/30 s Inaktivität, SessionCoordinator, DB-Key-Owner, LeadershipGrant,
P3b-Crypto, Argon2id, Tink, Keystore/Auth, SQLCipher, FLAG_SECURE und Backup-/
Transferregeln bleiben unverändert. Keine neue Permission/Dependency/Netzwerkfunktion.
P6 bleibt SENSITIVE/READY ohne Leadership-Step-up gemäß engem P6a.1-Trade-off.

## Gefundener Fix und Testtreibergrenzen

Im bestehenden Formular lagen Save/Cancel nur vor den weiteren Angaben.
Bei langen Personen-/Kennungsabschnitten fehlte am Ende ein direkter Abschlussweg.
Kleinster UI-Fix: dieselben Controls über ProtocolFormActions zusätzlich am Ende
der aufgeklappten Angaben; unveränderte Validierung/Busy/STALE- und Save-/Cancelpfade.
Die Belastungsprobe fordert dort ausdrücklich den erreichbaren Save-Control.
Kein Domain-/Model-/Repository-/Schema-/Securityfix erforderlich.

Vorläufe der neuen Probe prüften den ausgewählten Zustand zunächst nur auf dem
klickbaren Vorfahren und verglichen eine CharSequence direkt mit String.
Der Treiber wartet nun auf aktuelle native Label-/Containersemantik und vergleicht
textlich. Dies waren Probeprobleme, keine Änderung fachlicher Zustände.

Bei der Wiederaufnahme meldete der neue Breitentest zunächst die Constraints
des vorherigen virtuellen Dichteszenarios (647 statt 649 dp Gesamtbreite), trotz
neuer LocalDensity. Frische per Breite/Font Scale gekeyte Subkompositionen
beheben die Testtreiberwiederverwendung; der fokussierte Layoutlauf besteht.
Ein zwischenzeitlich fehlender Testimport wurde ergänzt und der Build erneut
ausgeführt. Keine Änderung am bestehenden responsive Produktcode.

## Ausgeführte Abschlussprüfungen

- Vollständige Unit-Suite nach Wiederaufnahme: **765 Tests**, keine Failures/Errors/Skips
  (App 370, Data 391, Security 4), inklusive UX2/UX3/P6c1–P6c4.
  15 neue P6c4-Testmethoden mit den beschriebenen mehrdimensionalen Matrizen.
  Fokussierter P6-Lauf mit Appfilter `*Protocol*` und den beiden P6c1-Data-Klassen:
  **79 Tests** (App 63, Data 16), alle grün. Der Appfilter schließt zwei bestehende
  ActiveWorkspace-Testmethoden mit „Protocol“ im Namen ein.
- Debug-/Demo-APK, App-/Data-AndroidTest, App/Data/Security-Lint und
  verifyArchitecture grün. App-Lint nur bestehende AndroidGradlePluginVersion-
  Warnung; keine neue fachliche Dependency oder Permission.
- Galaxy **SM-M526BR / API33**, Europe/Berlin: finale P6c4-Probe getrennt bei
  Font Scale 1 und 2 auf 320 dp vollständig grün, einschließlich nativer
  mindestens-48-dp-Save-/Cancelbounds, horizontaler Controlgrenzen,
  sichtbarer IME am Ende des langen Formulars und 599/601-dp-Hublayouts.
  Bestehende P6c2- und P6c3-Proben jeweils Font Scale 1/2 vollständig grün.
- Keine offensichtliche ANR-/Timeout-/UI-Regression in den getesteten synthetischen
  Größen. Die Probetreiberzeit enthält Accessibility-I/O/Scrollwaits; kein Benchmark,
  keine unbegrenzte Skalierungs- oder Performancegarantie und keine Pagination.
- Storage-Finalnachweis: unveränderter **ProtocolStorageTest, alle 18 Tests grün**
  auf Galaxy/API33 im separaten `org.pag.data.test`, Laufzeit 175,988 Sekunden.
  Aktuelle Data-AndroidTest-APK gebaut und per `install -r` erfolgreich aktualisiert.
  Vollständiger bestehender P6-Testklassenlauf statt neuer Storagefunktionen:
  produktiver Repositoryport, Create-/Append-Atomarität samt Childfehler-Rollback,
  STALE/Concurrent-Writers, Abschlussflags/Statusgates und P4/P5a-Read-only,
  Relationsbereinigung/Actionkaskade, 6→7-Migration/Migrationrevoke sowie
  Commit-/Generationspublication. Keine echte Produktdatenmutation.
  Der im Vorlauf abgewiesene erste Signaturwechsel und ein Lauf auf alter APK
  zählen nicht zur aktuellen Abnahme; keine Signingkonfiguration, Gerätecredentials
  oder produktiven Keys geändert.
- Alle 23 ursprünglichen und 29 Wiederaufnahme-Freezehashes unverändert;
  kompletter Domain/Data/Security-/Dependency-/
  Produktmanifest-/Backup-Diff leer. Schema bleibt 7, Export bytegleich.
  Gemergtes Debugmanifest: bestehende USE_BIOMETRIC und eigene generierte
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; Demo nur eigene generierte Permission.
  Beide ohne INTERNET und allowBackup=false. Testmanifest ergänzt nur die Probe.
- Gesamter verfügbarer Logcatbestand auf DETAIL-SENTINEL geprüft: 0 Treffer,
  ausschließlich Anzahl ausgegeben, keine Log-/Detailinhalte persistiert.
  Lokale Dokumentlinks und git diff --check grün. Keine Credentialänderung,
  kein pm clear, kein Uninstall, keine echten Produktdatenwrites.
  Demo bleibt fachlich unverändert ohne P6, nur der bestehende Build geprüft.

Wiederholbare Aufrufe und Rohresultate der Wiederaufnahme (alle Logs ignoriert):

| Prüfung | Aufruf / Resultat |
| --- | --- |
| Gesamtabnahme | `.prototype-cache/p6c4-resumed-checks.log`: App/Data/Security `testDebugUnitTest`, Debug/Demo und beide AndroidTest-Builds, drei `lintDebug`, `verifyArchitecture`, beide Manifestverifier; `--offline`. |
| Fokussierte P6-Units | `.prototype-cache/p6c4-resumed-focused.log`: App `--tests '*Protocol*'`; Data `ProtocolRulesTest` und `ProtocolProjectionTest`. |
| ProductScreen | `adb shell am instrument -w -r org.pag.prototype.a.test/org.pag.prototype.P6c2ProtocolProbe` und entsprechend P6c3; neue P6c4-Probe zusätzlich `-e font 1` / `-e font 2`. Logs `p6c4-resumed-active`, `completed`, `integration-font1`, `integration-font2`. Alle final `INSTRUMENTATION_CODE: -1` und `OK`. |
| Storage | `adb shell am instrument -w -r -e class org.pag.data.product.ProtocolStorageTest org.pag.data.test/androidx.test.runner.AndroidJUnitRunner`; `.prototype-cache/p6c4-resumed-storage.log`: `OK (18 tests)`, `INSTRUMENTATION_CODE: -1`. |
| Zuordnung | `.prototype-cache/p6c4-resumed-acceptance.json`: Unit-Ergebniszahlen und SHA-256 der aktuellen Debug-/App-Test-/Data-Test-APKs. |

`git diff --check`, Whitespace einschließlich der neuen untracked Dateien und
lokale Markdown-Verweise bestehen. Final genau neun veränderte/neue Dateien,
ausschließlich App-Protokoll-UI, Tests/Testmanifest und die fünf Nachweisdokumente.
Der komplette Diff der eingefrorenen Bereiche gegen HEAD ist leer.

## Manueller Smoke und TalkBack — noch nicht abgenommen

Nur synthetische Testaktion und künstliche Inhalte verwenden.

1. Lagebild → Protokoll: Orientierung und sinnvoller Lesefokus.
2. Filter lesen/wechseln: Selected-Ansage und lokale Filterwirkung.
3. Timelineeintrag öffnen: verständliche Öffnungsaktion und Rückweg.
4. Vorlage/freier Eintrag: Pflichtfelder, Ungefähr, Ereigniszeit und Cancel/Save.
5. Weitere Angaben: Expanded/Collapsed und Lesereihenfolge.
6. Mehrfachbetroffene und unabhängige Quellenperson: eindeutige Pseudonyme,
   Checked-/Selected-Ansagen, Auswahl ändern.
7. Ansage, Kennungszeile und lange Rechtsgrundlagenbeschriftung; Disabled bei 32.
8. Korrektur und History/alte Fassung: eigener Inhalt, Rückweg, kein historisches Edit.
9. Irrtumsbestätigung: Konsequenz verständlich und Abbruch möglich.
10. COMPLETED: bewusster Einstieg, Nachtrag und getrennte Entry-/Fassungsmarker.
11. Lock/Reauth: kein Entwurf oder automatisch offener Follow-up; zuvor diese
    Schritte mit großer Schrift/offener Tastatur wiederholen und Fokuscleanup prüfen.

Diese Liste ist zugleich der kurze Smoke-Plan. Alle elf Schritte zusätzlich mit
menschlichem TalkBack durchlaufen; noch kein Haken ist als menschlich abgenommen gesetzt.

TalkBack wird nicht per adb ein-/ausgeschaltet. Keine automatisierte Behauptung
vollständiger Barrierefreiheit oder menschlicher TalkBackabnahme.

## P6-Abschlussgrenze

Technische Kette: P6a Product/Privacy, P6b Spezifikation, P6c1 Domain/Storage/
Repository, P6c2 ACTIVE-UI, P6c3 COMPLETED-Nachbereitung und P6c4 Integration/
Accessibilityautomation. Kein neuer Productfeature-Slice.

Weiterhin offen/nicht freigegeben: konkrete endliche Retentiondauer/-workflow,
allgemeiner Personen-/Aktionslöschworkflow samt manueller Freitextprüfung,
juristische Prüfung vor Echtdatennutzung, menschliche TalkBack-/Accessibility-
abnahme und etwaige gesonderte Export-/Medien-/Integritätsanforderungen.
Kein Export, Medien, GPS, Behördenkartei/Suche, automatische P4/P5a-Mutation,
Rechtsbewertung, P5b, Hashchain/Signaturen oder Beweis-/Forensik-/Zeitstempelgarantie.
Kein Commit/Push.

**A) P6c4 INTEGRATION/ACCESSIBILITY AUTOMATISCH GRÜN – P6 TECHNISCH BEREIT FÜR ABSCHLUSSREVIEW**

Dieser Status bezieht sich auf die technische Produktimplementierung P6a/P6b/
P6c1/P6c2/P6c3/P6c4 und die benannten automatisierten Prüfungen. Er setzt keinen
der oben offenen Retention-, Lösch-, Rechts- oder menschlichen Accessibilitypunkte
auf erledigt und ersetzt keine Echtdatenfreigabe.
