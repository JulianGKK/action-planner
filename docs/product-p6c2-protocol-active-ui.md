# P6c2 — ACTIVE Aktionsprotokoll UI

Aktueller Anschluss: [P6c3](product-p6c3-protocol-completed-followup.md) ergänzt
die ausdrücklich geöffnete COMPLETED-Nachbereitung. Die ACTIVE-only-Aussagen
unten beschreiben den ursprünglichen P6c2-Slice; ACTIVE bleibt unverändert.

## Baseline und Umfang

Baseline: sauberer Branch `p6c2-protocol-active-ui`, HEAD `9c2f8b7`,
ActionDatabase Schema 7. Verbindliche Grundlagen: [P6a.1](product-p6a-protocol.md),
[P6b](product-p6b-protocol-implementation.md), [P6c1](product-p6c1-protocol-storage.md).
Dieser Slice ergänzt ausschließlich ACTIVE-Appdarstellung und RAM-Zustand.
COMPLETED-Nachbereitung bleibt P6c3; keine Domain-, Repository-, DB- oder
Securityänderung. Kein Commit/Push.

## Hub, Datenzugriff und Atomic Reveal

MainActivity gibt den bestehenden `ProductRuntime.protocol` an ProductViewModel.
Die optionale Konstruktorvorgabe dient nur bestehenden synthetischen Konstruktionen
wie bei Operations/Tasks; der echte Produkteinstieg übergibt immer den realen Port.
Ein synthetischer Workspace ohne Port erhält keine Protokollkarte, keinen
Ersatzrepository und keine erfundene Summary.

ACTIVE-Hub und Protokollbereich laden ProtocolSummary gemeinsam mit den bestehenden
Boards in einen WorkspaceSnapshot. Erst vollständige erfolgreiche Ergebnisse
werden gemeinsam publiziert, ohne gemeinsame DB-Transaktion zu behaupten.
Während Refresh bleibt ein gültiger alter Snapshot sichtbar. Summaryfehler
publiziert weder Nullzählungen noch gemischte Boards; initial bleibt die Fehleransicht.
Generation, Actionstatus, Action-ID, Bereich und Request binden die Publication.

Die fünfte Karte „Protokoll“ zeigt nur Schlüsselereignisanzahl und letzte erste
Erfassungszeit (`MAX ORIGINAL.recordedAt`), einschließlich irrtümlich markierter
Schlüsselereignisse nach bestehendem Portvertrag. Heute Uhrzeit, an anderen Tagen
Datum und Uhrzeit in Gerätezone. Keine Titel, Pseudonyme, Freitext oder Kennungen.
Die gemeinsame stateless Hubdarstellung behält ihre ein-/zweispaltige Anordnung;
die Demo verwendet weiterhin nur ihre bisherigen vier Karten, ohne P6-Demoinhalt.

## Protokollübersicht und Chronologie

Sechs große Schnellvorlagen: Polizei eingetroffen, Anordnung/Ansage, Frist gesetzt,
Räumung angekündigt, Zwang angedroht und Räumung begonnen. Weitere zwölf Vorlagen
liegen im eingeklappten Bereich, das eigene Schlüsselereignis ist separat erreichbar.
Alle Texte/Keys kommen aus ProtocolRules.templates. Auswahl erzeugt ausschließlich
einen RAM-Entwurf mit einmaliger Gerätewanduhr-Vorbelegung; kein Write.
Freier relevanter Eintrag ist gleichberechtigt erreichbar.

Timeline übernimmt Repositoryreihenfolge unverändert: occurredAt aufsteigend,
Original-recordedAt, Entry-ID. Alle/Schlüsselereignisse filtert ausschließlich den
bereits geladenen Bestand. Ganze Karten öffnen Details. Sichtbar sind Art, kurzer
Titel, Ereigniszeit, Ungefähr-Angabe und gegebenenfalls Korrektur-/Ergänzungs-/
Irrtumsmarker. Keine Orts-, Betroffenen-, Quellen-, Kennungs- oder Textvorschau.
Initial Loading unterscheidet sich von Empty; Refresh behält bisherigen Inhalt.
Ein bewusster Aktualisierungscontrol lädt die Chronologie erneut.
Hub/Timeline rufen niemals getEntry auf; Detailread nur nach explizitem Öffnen
oder gezielter Erfolgsnachladung des gerade gespeicherten Eintrags.

## Formulare und Zeit

Pflichtbereich: Titel, Ereigniszeit, „Zeitpunkt ungefähr“, Speichern/Abbrechen.
Titelhinweis „Keine Namen oder Kennnummern im Titel.“ Beschreibung direkt optional.
Weitere Angaben sind eingeklappt: Ort, Quellenart, betroffene Pseudonyme,
Ansage, Begründung/Rechtsgrundlage laut Polizei und beobachtete amtliche Kennungen.
Keine neuen Felder oder sensitive P3b-Abfrage; Auswahlpersonen aus bestehendem Board.

Quellenperson nur bei Mitgeteilt, unabhängig von der Betroffenenmenge; Wechsel
entfernt die Quellenreferenz. Ansage erfordert Wortlaut/Sinngemäß und Text zusammen;
Entfernen löscht beide Entwurfswerte. Kennungen haben individuelle Typen/Werte,
bewusstes Entfernen und maximal 32 Zeilen, ohne Deduplizierung/Matching.
Attributierte Rechtsgrundlage bleibt ausdrücklich ungeprüfte Angabe.

ProtocolRules bleibt Validierungsautorität, UI erklärt Codepointgrenzen ohne
stille Kürzung oder Echo sensitiver Werte. Fehler behalten den vollständigen Draft.
DateTimeField verwendet ProductTime mit genau einem gültigen lokalen Offset;
DST Gap/Overlap werden abgewiesen. Formularzeit entsteht einmal bei Eintritt,
nicht bei Recomposition/Save. recordedAt kommt ausschließlich vom Repository.
Keine vertrauenswürdige Zeitquelle oder Beweis-/Forensikgarantie.

## Detail, Revisionen und Irrtum

Detail zeigt die aktuelle vollständige Fassung samt Ereignis-/Erfassungszeit,
Quellen-/Betroffenenpseudonymen, Ansage, attribuierter Begründung und Kennungen.
Frühere Fassungen sind initial eingeklappt und bewusst vollständig öffnungsfähig;
jede zeigt ihre eigenen Children. Kein Editieren aus der historischen Ansicht.

Korrigieren friert den vollständigen ProtocolExpectedRevision aus aktuellem Detail
ein. Classification darf geändert werden, templateKey bleibt Herkunft.
Irrtumsmarkierung wird nur durch bewusste CORRECTION-Option zurückgenommen,
standardmäßig aus. Ergänzen beginnt ebenso mit vollständigem Inhalt, bewahrt
Classification im UI und kann den Marker nicht zurücknehmen.
Als irrtümlich markieren benötigt eine ausdrückliche Bestätigung, erzeugt eine
weitere Fassung und löscht nichts. Bereits markierte Einträge erhalten keinen
erneuten Markierungscontrol.

Jeder Write erwartet ausdrücklich ActionStatus.ACTIVE. Erfolg schließt Draft,
liest das gespeicherte Detail, aktualisiert Timeline und Workspace-Summary und
zeigt vorhandenes Floating-Feedback. Keine optimistische erfundene Row/Erfassungszeit.
STALE behält Draft und eingefrorenen Expectedstand; kein Merge/automatischer Retry.
„Zur aktuellen Fassung · Eingaben verwerfen“ ist eine bewusste neue Nutzeraktion.
Ein ACTIVE-Entwurf wird niemals automatisch zum COMPLETED-Nachtrag.

## Navigation, Session und Cleanup

Lagebild → Protokoll → Eintrag → Korrektur/Ergänzung; historischer Eintrag → aktueller
Eintrag. Header hält Bereich „Protokoll“ auch in Unterkontexten. Create-Cancel
verwirft Draft und kehrt zur Übersicht zurück; Revision-Cancel zum aktuellen Detail.
Bereichswechsel und Lock verwerfen Timeline, Detail, Entwurf, Auswahl, Quellenperson,
Kennungen, Filter, Disclosures, Bestätigung und Fehler/Busy aus dem erreichbaren Model-/UI-State. Keine physische
RAM-Überschreibungsgarantie.
Keine SavedState-/Prefs-/Dateipersistenz oder Restoration nach neuer Generation.

ProtocolModel erfasst Sessiongeneration, Action-ID/ACTIVE und UI-Epoch vor echter
Interaktion, prüft nach synchron möglichen Revokes erneut und bindet Read-/Write-
Callbacks zusätzlich an ihre Requestnummern. Display, Readresultate und Refresh
melden keine Benutzerinteraktion. UI-writable ist READY **und ACTIVE**;
ProtocolRules.mayWrite(COMPLETED) wird nicht als UI-Freigabe verwendet.
P4/P5a/P3b werden weder aus Protokollmodel noch aus Protokollscreen aufgerufen.

## Accessibility und Grenzen

Große bestehende Controls (mindestens 48 dp), ganze Timelinekarte als Öffnungsziel,
Überschriften, textliche Art/Marker, Selected-/Toggle-/Expanded-Semantik und
vertikale, umbrechende Auswahl statt Mini-Chips. Keine LiveRegion-Zeitansagen.
Bestehender ProductInputScope räumt Keyboard/Fokus bei Navigation/Lock auf;
Autofokus nur bei bewusstem Create-Eintritt. Deviceprobe prüft Font Scale 1/2.

Keine neue Dependency, Produktpermission, Netzwerk, Export, Medien, Worker,
P3b-Freigabe, Crypto, Retention oder automatische P4/P5a-Mutation.
Schema 7, P6-Port-/Storage-Semantik und sämtliche Securitymechanismen bleiben
unverändert. P6 bleibt SENSITIVE mit dem ausdrücklich beschlossenen READY-Trade-off.
P6c3 muss COMPLETED-Nachtragsbedienung separat schaffen; rechtliche Prüfung,
Retention und Last-/TalkBack-Handabnahme bleiben eigene Anforderungen.

## Prüfungen

Abschlussprüfung am 14.10.2026, ausschließlich synthetische Testdaten:

- Vollständige Unit-Suite: **727 Tests**, 0 Failures/Errors/Skips (App332,
  Data391, Security4), einschließlich UX2/UX3a–d/ACTIVE-Integration,
  UX1/Session-/P4/P5a/P6c1-/DST-Regressionen. 26 neue Fälle: ProtocolModel23,
  drei zusätzliche Workspacefälle. Fokussierter Model-/Workspace-Lauf: 43 grün.
- Debug-/Demo-APK und App-AndroidTest-Build, App/Data/Security-lintDebug und
  verifyArchitecture grün. App-Lint nur bestehende AndroidGradlePluginVersion-
  Warnung; Data/Security ohne Befund. Keine Änderung von Domain/Data-APIs,
  daher kein neuer Data-AndroidTest-Build oder Storage-/Credential-Gerätelauf.
- Galaxy SM-M526BR/API33: finaler P6c2ProtocolProbe-Lauf **Font Scale 1 und 2
  vollständig grün**. Echter ProductScreen mit synthetischen Domainports:
  fünfte Karte, Vorlage ohne Write, Create/Detail/Timeline, Korrektur und geöffnete
  alte Fassung/Rückkehr, Ergänzung, bestätigter Irrtum, lokaler Filter, freier
  Eintrag, Kennungsformular/-detail ohne Kennungen in Hub/Timeline, Lock im
  Korrekturflow und vollständiger Modelcleanup. Keine Authentisierung, echten
  DB-Writes, Credentialänderung, pm clear, Uninstall oder Systemfontänderung.
  APKs per install -r aktualisiert, Produktinstallation und Daten erhalten.
- Vorläufe stoppten an Testtreiberproblemen bei Scroll-/Accessibility-Nodes;
  ein Probe-API-Guard war fälschlich API33 für eine API34-Methode und wurde
  korrigiert. Final nutzt API33 Node.refresh und die angebotenen Scrollaktionen.
  Keine Produkt-Securityänderung dafür. Ein im Modelreview gefundener hängender
  Refreshindikator nach Formularnavigation wurde vor Abschluss korrigiert und
  durch leavingPendingRefreshDoesNotLeavePermanentLoading abgesichert.
- Schema7/Export7, Protocol.kt, ProtocolDatabase, ProductProtocolRepository und
  ProductRuntime bytegleich zu HEAD; gesamter Domain/Data/Security-Diff leer.
  Produktionsmanifest, Dependencies, Permissions, Backup/Transfer unverändert.
  Debug/Demo-Mergedmanifest ohne INTERNET; Demo weiterhin getrennte Application-ID.
  Nur Testmanifest registriert eine weitere synthetische Instrumentation.
- Lokale Links und git diff --check grün. Logs in ignorierter
  .prototype-cache/p6c2-*.log; keine sensitiven Produktwerte geloggt.

Manueller Review bleibt sinnvoll: gesamte Erfassung mit mehreren Betroffenen,
Quellenwechsel, Ansage/Entfernen, Datum/Zeit inklusive DST, lange Texte/32 Kennungen,
TalkBack/IME, Cancel/STALE und Lock in allen Editorvarianten. Die synthetische
Geräteprobe ersetzt keine juristische Echtdatenfreigabe oder vollständige
manuelle Accessibility-/Performanceabnahme.
