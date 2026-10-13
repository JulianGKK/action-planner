# P6c3 — bewusste COMPLETED-Protokollnachbereitung

## Baseline und Umfang

Saubere Baseline: Branch `p6c3-protocol-completed-followup`, HEAD `5ed84b6`
(P6c2-Merge), ActionDatabase Schema 7. Verbindlich bleiben
[P6a.1](product-p6a-protocol.md), [P6b](product-p6b-protocol-implementation.md),
[P6c1](product-p6c1-protocol-storage.md) und [P6c2](product-p6c2-protocol-active-ui.md).
Nur App-Präsentation und RAM-Zustand wurden erweitert. Domain, Data, Schemaexport,
Migrationen, Security, Produktpermissions und Dependencies bleiben unverändert.
Keine P6-Demoerweiterung, kein Commit/Push.

## Bewusster Einstieg und Modus

Die normale abgeschlossene Aktionsansicht bietet „Protokoll ansehen / nachbereiten“.
Vor diesem Tap werden weder ProtocolSummary noch Timeline oder Detail angefordert.
Der Tap prüft READY, Generation, Action-ID und COMPLETED vor und nach der echten
Interaction und bestehendem Editorcleanup. Ein gültiger vorhandener Workspace wird
behalten; andernfalls werden die bestehenden lesbaren P3a/Boarddaten geladen.

`ProtocolUiMode.ACTIVE` und `COMPLETED_FOLLOW_UP` sind explizite RAM-Modi.
Follow-up bindet zusätzlich Action-ID und Sessiongeneration. Die eigene Shell
zeigt „Protokoll nachbereiten“, den Hinweis auf dauerhaft gekennzeichnete neue
Einträge/Fassungen und „‹ Zur abgeschlossenen Aktion“. Kein zweiter ACTIVE-Hub.
Verlassen löscht sämtlichen Protokollzustand.

Writes verwenden ausschließlich den expliziten Modusstatus. Ein Entwurf friert
seinen erwarteten Status beim Öffnen ein. ACTIVE→COMPLETED verwirft ihn über
bestehendes Workspacecleanup; er wird niemals automatisch zum Nachtrag.
COMPLETED-Nachbereitung muss anschließend bewusst neu geöffnet werden.

## Gemeinsame Timeline, Form und Revisionen

Die bestehenden 19 Domainvorlagen, sechs Schnellvorlagen, Reihenfolge und Filter
werden wiederverwendet. Im Follow-up heißen die Erfassungsbereiche
„Schlüsselereignis nachtragen“ und „+ Nachtrag nach Abschluss“.
Vorlagenauswahl erzeugt nur einen RAM-Entwurf mit einmalig vorbelegter Gerätezeit.

Das gemeinsame Formular kennzeichnet neue Nachträge bzw. neue Korrektur-/
Ergänzungsfassungen nach Abschluss. Ereigniszeit bleibt editierbar, auch für einen
Vorgang während ACTIVE. ProductTime/DST-Validierung bleibt unverändert.
Es gibt weder ein recordedAt-Eingabefeld noch eine erfundene Erfassungszeit vor Save.

Create, CORRECTION, ADDENDUM und bestätigtes MARKED_MISTAKEN senden explizit
`ActionStatus.COMPLETED`. Expected bleibt der vollständige eingefrorene Snapshot.
Korrektur darf bewusst Classification und Irrtumsrücknahme ändern; Ergänzung
übernimmt Classification und Irrtumszustand. Alte Fassungen bleiben unverändert.
Feedback: „Nachtrag gespeichert“, „Korrektur gespeichert“, „Ergänzung gespeichert“
bzw. „Irrtumsmarkierung gespeichert“.

## Getrennte Phasen und Zeiten

Entry.createdAfterCompletion erzeugt dauerhaft „Nach Abschluss erfasst“;
Revision.recordedAfterCompletion erzeugt „Diese Fassung wurde nach Abschluss erfasst.“.
Historische Ansichten verwenden ihren eigenen Revisionswert und denselben Entrykontext.
Ein ACTIVE-Original mit späterer Korrektur erhält keinen Entry-Nachtragsmarker.
Timeline zeigt entweder den Entrymarker oder „Aktuelle Fassung nach Abschluss erfasst“.
Diese Aussagen werden niemals aus occurredAt, recordedAt oder aktuellem Status abgeleitet.
Detail trennt „Ereigniszeit“ und „Erfasst am“; ungefähre Ereigniszeit bleibt ausdrücklich
gekennzeichnet. Keine vertrauenswürdige Zeit-/Beweis-/Forensikgarantie.

## STALE, Lock und Navigation

STALE behält den vollständigen Entwurf, blockiert blindes Retry und bietet den
bewussten Weg „Zur aktuellen Fassung · Eingaben verwerfen“. Kein Merge und kein
Wechsel des erwarteten Status. Ein nicht mehr passender projizierter Actionstatus
blockiert den Modus; eine Repository-INVALID_STATE-Antwort schließt zusätzlich den
Follow-up-Zustand, wenn die persistierte Phase bereits nicht mehr zulässig ist.

Requesttoken, Modus, Action, READY und Generation binden Callbacks. Nach synchronen
Readcallbacks wird vor weiteren Refreshes/Feedback erneut geprüft. Lock, Actionwechsel
und Workspaceclear entfernen Modusbindung, Timeline, Filter, Detail, historische
Fassung, Draft, Expected, Personenwahlen, Kennungen, Disclosure, Confirmation,
Fehler und Busy. Reauth stellt die Nachbereitung nicht wieder her; die bestehende
normale Navigation bleibt maßgeblich und erfordert einen neuen bewussten Einstieg.
Back/Cancel und Keyboardcleanup verwenden die bestehenden Produktinputs.

## Privacy und fachliche Grenzen

P6 bleibt SENSITIVE mit dem entschiedenen READY-Zugriff ohne Leadership-Step-up.
Timeline nutzt ausschließlich ProtocolTimelineItem; getEntry folgt erst bewusstem
Entrytap. Kennungen erscheinen nur in Form/Detail/Revisionsdetail. Personenbezüge
verwenden ausschließlich P3a-Pseudonyme; keine P3b-Abfrage oder Identitätsrekonstruktion.

P4, P5a und Aktionsstammdaten bleiben COMPLETED-read-only. Es gibt keine globale
writable-Property, kein Action-Reopen, keine automatischen Task-/Status-/Rollenwrites,
Retention, Export, Medien, Netzwerk, neue Permission oder Kryptografie.

## Accessibility und Nachweise

Textliche Phasenmarker, Heading, große bestehende Controls, ausgewählte Filter,
Disclosure-Semantik und gemeinsame scrollbare Formulare bleiben erhalten. Lange
Labels und Kernaktionen werden in der synthetischen echten ProductScreen-Probe
bei Font Scale 1 und 2 geprüft, ohne Systemfontänderung oder echte Produktdaten.

ProtocolFollowUpModelTest prüft expliziten Einstieg/Phasenbindung, alle vier Writepfade,
Flags, eingefrorenen Entwurf, STALE, Lock/Reauth, Action/Generation und synchrone
Revoke-Grenzen. CompletedProtocolWorkspaceTest prüft reale ViewModelintegration,
kein Prefetch, ACTIVE-Statuswechselcleanup und verweigerte P4/P5a/Action-Writes.
P6c3ProtocolProbe prüft Nachtrag, Korrektur, eigene historische Phasen, Ergänzung,
Irrtumsbestätigung, Kennungsminimierung, Back, Lock und bewussten Wiedereinstieg.
Die bestehende P6c2Probe sichert ACTIVE getrennt ab.

Abschlussprüfung: 750 Unit-Tests grün (App 355, Data 391, Security 4).
Debug-/Demo-APK und App-AndroidTest gebaut; App/Data/Security-Lint und
verifyArchitecture grün. Gemergte Debug-/Demo-Manifeste ohne INTERNET und ohne
neue Produktpermission. Domain/Data/Security-Diff leer, Schemaexport 7 unverändert,
git diff --check grün. Beide synthetischen ProductScreen-Proben P6c3 und P6c2
bestehen auf Galaxy SM-M526BR/API33 jeweils bei Font Scale 1 und 2. Keine
Credentialänderung, kein pm clear, kein Uninstall, keine Produktdatenwrites.

P6c4 bleibt für
übergreifende Integrations-/Accessibilityabnahme, manuelles TalkBack, lange Texte/
32 Kennungen, größere Historien und weitergehende IME-/Layoutfälle vorgesehen.
Keine vollständige manuelle Accessibilityabnahme durch diese Proben behauptet.
