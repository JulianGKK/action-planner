# P6c1 — Aktionsprotokoll Domain / Storage / Repository

## Baseline und Umfang

Sauberer Branch p6c1-protocol-domain-storage, HEAD d9c24d2 (P6b-Merge),
ActionDatabase Schema6 vor Änderungen. P6a.1/P6b verbindlich; keine neue
Produkt-/Privacy-/Securityentscheidung. Ausschließlich Domain/Data/Runtime-
Verdrahtung, Tests, Schemaexport und Dokumentation. Keine UI oder Hubkarte.

## Implementierung

- Protocol.kt enthält fünf Fachenumtypen, vollständigen ProtocolInput,
  Entry/Revision/Expected/Saved und minimierte Readmodelle, Result/Port und
  ProtocolRules. Die 19 statischen Keys/Texte entsprechen P6b; CUSTOM_KEY_EVENT
  hat das sichtbare Label „Eigenes Schlüsselereignis“, aber leeren Titelentwurf.
- Unicode-Codepointgrenzen und Trim/optionale Leerwerte, Ansage/Text-Paar,
  ReportedBy nur REPORTED, max32 Kennungen, Set-Betroffene und bekannte Herkunftskeys.
  Bewusstes clearMistaken nur im CORRECTION-Aufruf, kein frei setzbarer Inputzustand.
- Schema7: genau fünf neue P6-Tabellen. Same-Action-FKs und Parent-Uniqueindizes,
  revisionsgebundene Children, Positionsslots0..31, Original/Sequenz-/Typ-/Flag-
  sowie Quellenconstraints; keine normalen UPDATEs und kein Einzel-Delete-Port.
  Fresh und Migration verwenden denselben P6-Constraintinstaller zusätzlich
  zum unveränderten P5a-Installer. Schema6/7: alle zehn alten Entities identisch.
- ProtocolStore läuft in kontrollierter Read-/Write-Transaktion. Vollständiger
  Expectedvergleich umfasst Entry/Revision/Metadaten/fachlichen Inhalt, Betroffenen-
  und Quellenrelation sowie Kennungsreihenfolge, keine Pseudonymanzeigetexte.
  Current=max revisionNumber. Fehler nach erstem Insert propagieren für Rollback;
  keine catch+Rejected-Teilcommits oder REPLACE/IGNORE. Jede Fassung eigene Children.
- MARKED_MISTAKEN kopiert vollständigen Inhalt, hält Historie und setzt true;
  ADDENDUM bewahrt Zustand, CORRECTION kann true ausdrücklich zurücknehmen.
  Nummern/Wanduhr/Phaseflags vom Repository, recordedAt nicht im Input. Dauerhafte
  Entryphase und separate Revisionsphase statt Ableitung aus verstellbarer Wanduhr.
- ProductProtocolRepository nutzt wie P4/P5a READY, bestehende DB-Key-Lease,
  kurze Room-Lebensdauer, STEP/COMMIT und main-context.publish. Keine eigene
  Sessioninteraction; Revoke publiziert keinen fachlichen Datenfehler.
  Mutable Input-/Expected-Collections werden vor Workerdispatch kopiert.
- Portverdrahtung ProductRuntime.protocol, ohne neuen Key/Grant/Step-up.
  P6-Policy ACTIVE/COMPLETED, DRAFT/PREPARATION/DELETION_PENDING verweigert.
  expectedActionStatus mismatch ist STALE. P4/P5a-Policies unverändert.
- Summary liest Count aktueller KEY_EVENT-Fassungen einschließlich markierter
  Irrtümer und MAX Original-recordedAt. Timeline nur explizite schmale aktuelle
  Spalten, Ordnung occurredAt/Original.recordedAt/Entry-ID. Detail erst vollständige
  Fassungen/Children und nur benötigte ID/Pseudonyme. Keine P3b-/P4-/Taskreads.
- Personcascade nur Betroffenen-/Quellenrelationen; Revisionen/Texte/Kennungen
  erhalten. Actioncascade erfasst alle P6-Rows. Dies sind FK-Nachweise, keine neue
  allgemeine Produktpersonen-/Aktionslöschung oder Retentionimplementierung.

## Prüfungen

Abschluss auf Galaxy SM-M526BR / Android13 / API33, nur separates synthetisches
Data-Testpaket. Keine Produktinstallation/-datenmutation, Credentialänderung,
pm clear, Uninstall oder UI-Probe.

- Vollständige Unit-Suite: **701 Tests**, 0 Failures/Errors/Skips
  (App306, Data391, Security4). Enthält bestehende UX2/UX3/P4/P5a/Session-/DST-
  Regressionen und 16 neue P6-Unitchecks (13 Domain, 3 generiertes SQL/Privacy).
  Zusätzlich fokussierter Lauf beider P6-Unitklassen: 16 grün.
- Finale echte SQLCipher-/Room-Instrumentation: **73 Tests**, alle grün:
  ProtocolStorageTest18, TaskStorageTest12, OperationsStorageTest13,
  ActionLifecycleStorageTest12, PersonStorageTest17 und ein fokussierter
  Leadership-v3-Migrationstest. Geprüft fresh7/6→7, alte Upgradepfade,
  Ciphertexts, Migrationrevoke, Kind-Insert-Rollback, persistierte Statusgates,
  produktiver Port, Full-Snapshot-Stale, Personenrelations-/Aktionskaskaden,
  32 Kennungen und zwei kontrolliert konkurrierende Appends ohne Lost Update.
- Der erste Probelauf hatte einen nicht-kanonischen Fixturepfad und wurde vom
  unveränderten B2-Inventar-Gate abgewiesen. Nur Testfixture korrigiert; ein
  anschließender 16-Testlauf sowie obiger finaler 73-Testlauf bestanden.
- App-Debug-Build, Data-/App-AndroidTest-Builds, App/Data/Security-lintDebug,
  verifyArchitecture erfolgreich. Data/Security-Lint ohne Befund; einzige
  App-Lintwarnung ist die bestehende AndroidGradlePluginVersion-Warnung.
- Merged Debugmanifest: kein INTERNET; bestehende USE_BIOMETRIC- und generierte
  DYNAMIC_RECEIVER_NOT_EXPORTED-Permission unverändert. Keine Manifest-/Dependency-
  oder Backup-/Transferänderung. Keine neue Produktkomponente.
- Schemaexport7 gegen6: alle zehn alten Entity-/Feld-/Index-/FKdefinitionen exakt
  gleich, genau fünf neue P6-Entities; keine neue Parentindexduplikation.
  Fresh-/Migration-P6-Triggerdefinitionen auf Gerät identisch geprüft.
- Security-/UI-Diff: Coordinator, StorageAccess, SessionDbKeyOwner,
  LeadershipGrant/ProductLeadershipRepository, Securitymodule/Argon2/Tink/
  Keystore/Auth und gesamte App-/Compose-/ViewModel-/Demoquellen unverändert.
  Bestehende P4/P5a-Domain-/Repositoryregeln unverändert; nur Runtime-Port und
  ActionDatabase-Version/Migration/Callbackanschluss als produktive Bestandsänderung.
- Lokale Links, neuer Source-/Doku-Whitespace, Scope und git diff --check grün.
  Kein Commit/Push. Logs: ignorierte .prototype-cache/p6c1-*.log,
  ausschließlich technische Ergebnisse/synthetische Testwerte.

## Geänderte und neue Dateien

Neu:

- domain/src/main/kotlin/org/pag/domain/Protocol.kt
- data/src/main/kotlin/org/pag/data/product/ProtocolDatabase.kt
- data/src/main/kotlin/org/pag/data/product/ProductProtocolRepository.kt
- data/schemas/org.pag.data.product.ActionDatabase/7.json
- data/src/test/kotlin/org/pag/data/product/ProtocolRulesTest.kt
- data/src/test/kotlin/org/pag/data/product/ProtocolProjectionTest.kt
- data/src/androidTest/kotlin/org/pag/data/product/ProtocolStorageTest.kt
- docs/product-p6c1-protocol-storage.md

Geändert:

- data/src/main/kotlin/org/pag/data/product/ActionDatabase.kt
- data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt
- data/src/androidTest/kotlin/org/pag/data/product/TaskStorageTest.kt
- data/src/androidTest/kotlin/org/pag/data/product/OperationsStorageTest.kt
- data/src/androidTest/kotlin/org/pag/data/product/ActionLifecycleStorageTest.kt
- data/src/androidTest/kotlin/org/pag/data/product/PersonStorageTest.kt
- data/src/androidTest/kotlin/org/pag/data/product/LeadershipStorageTest.kt
- docs/product-p6b-protocol-implementation.md
- docs/data-model.md
- docs/privacy.md
- docs/security.md
- docs/testing.md
- docs/ux-roadmap.md

21 modifizierte/neue Dateien (13 tracked verändert, 8 neu/untracked).

## Grenzen und nächste Schritte

SENSITIVE-P6-Arbeit ohne Leadership-Step-up in gültigem READY ist der entschiedene
operative Trade-off, keine allgemeine Öffnung von SENSITIVE. Kein P3b-Payloadread,
Crypto-/Keystore-/Sessionänderung, neue Permission/Dependency, Netzwerk/Export,
Dateien/Medien/Worker/Notifications oder automatische P4/P5a-Mutation.
Kein Versprechen vertrauenswürdiger Zeit, Beweis-/Forensikintegrität oder physischer
RAM-/Flashüberschreibung. Zeit- und Revisionsordnung sind nachvollziehbare
Anwendungshistorie; die Gerätewanduhr kann rückwärtslaufen.

P6c2 muss erst ACTIVE-Hub-/Timeline-/Detail-/Form-/Modelintegration schaffen,
Atomic Reveal/Refresh, Stale/Lock-Cleanup und Accessibility prüfen. Separate
COMPLETED-Bedienung, konkrete Retention/Rechtsprüfung und späterer allgemeiner
Löschworkflow einschließlich manueller Protokollprüfung bleiben ausstehend.
Die große P6b-Lastmatrix ist nicht als durch diesen Funktionsslice vermessen
behauptet; spätere Skalierungs-/UI-Abnahme bleibt erforderlich.
