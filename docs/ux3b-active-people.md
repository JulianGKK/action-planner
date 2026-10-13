# UX3b — ACTIVE Personenarbeitsbereich

Baseline: sauberer Branch `ux3b-active-people`, HEAD `7f3dec6`.
UX2, UX3a, isolierte UX4-Demo und Alpha-Dokumentation bleiben Grundlage.

## Struktur und Informationshierarchie

Hub → Personenübersicht → Person → Lage-/Funktionsaktion. Unter dem unveränderten
Security- und Aktionsheader stehen Summary und kompakte, vollständig antippbare
Karten. Gesamtzahl sowie nichtleere Zähler für Vor Ort, Gewahrsam,
Nichterreichbarkeit und fehlende Lage verwenden ausschließlich den P4-Snapshot.
Leerzustand: „Noch keine Personen“. Andere P4-Lagen bleiben auf den Karten sichtbar.
Keine neue Sortierung, Filter-, Prioritäts- oder Rechtsbewertungslogik.

Karten zeigen Pseudonym, Lage und vorhandenen Ort/Ziel, aktuelle Funktion und
Aktualität. `currentRole` ist maßgeblich; kein Fallback auf Planrollen.
Ohne aktuelle Zuordnung: „Keine Funktion“. Planung bleibt im Persondetail
nachrangig lesbar. P4 erlaubt Ort/Ziel nur bei Identitätsfeststellung, Gewahrsam
und Unklar; die UI erweitert dies insbesondere nicht auf Vor Ort.
Keine P3b-Payload, Identitäts-/Kontaktfelder oder Inventories in den Karten.

Der Personenarbeitsbereich bietet „Lage ändern“, „Funktion ändern“ und
„Personendetails“. Vorhandene betroffene Aufgaben bleiben hier erreichbar.
Die Liste enthält keine zusätzlichen Lage-/Taskbuttons pro Person.
Lageänderung verwendet das bestehende P4-Formular und den erwarteten Snapshot.
Funktionsänderung zeigt dessen Funktionscontrols ohne Status-/Ortsbearbeitung;
sie ist nur bei ON_SITE verfügbar. Kein Umschreiben der Planung.
Persondetails nutzen unverändert den vorhandenen Person-/P3b-Pfad.

## Aktualität

Reine Anzeige aus `updatedAt`, aktueller Wanduhr und lokaler Gerätezeitzone:

- unter einer Minute: „Gerade aktualisiert“;
- 1–59 Minuten: „Aktualisiert vor X Min.“;
- ab 60 Minuten am selben Tag: „Zuletzt aktualisiert um HH:mm“;
- anderer Kalendertag: „Zuletzt aktualisiert am DD.MM. um HH:mm“.

Die lokale Tagesgrenze hat Vorrang vor dem relativen Alter.
NO_STATUS hat konservativ keine Aktualitätszeile, auch mit gesetztem Timestamp:
das vorhandene Modell unterscheidet technische Initialisierung nicht zuverlässig
von einer bewussten Lageprüfung. Unbekannte (`updatedAt <= 0`) und zukünftige
Zeitpunkte behaupten ebenfalls keine frische Prüfung. Keine neue Historie/Spalte.

Ein sichtbarkeitsgebundener Compose-Job aktualisiert die Darstellungsuhr ungefähr
minütlich. Er ruft weder Repository noch Session/Interaction auf. Dispose,
Navigation und Lock entfernen die Composition und damit den Job.
Wanduhränderungen verändern nur diese Anzeige, niemals Sessiondeadlines.

## Loading, Navigation und Cleanup

Die vorhandene vollständige Workspace-Publication bleibt maßgeblich:
initial Operations und Tasks gemeinsam laden, dann Summary und Liste zeigen.
Taskdaten dienen den vorhandenen betroffenen Aufgaben; keine P3b-Reads.
Refresh erhält den gültigen alten Snapshot bis zum vollständigen Ersatz;
Fehler erhalten den vorhandenen Fehlerhinweis. Keine gemeinsame DB-Transaktion
über die beiden Reads wird behauptet.

Personenfokus nutzt den bereits publizierten Snapshot ohne zusätzlichen Read.
Save/Cancel führen über den bestehenden einzelnen Refresh zurück zur fokussierten
Person. Persondetail und Rückkehr benötigen keinen zusätzlichen Boardload.
Personen-/Formularnavigation setzt die Scrollposition auf den jeweiligen Header
zurück; ein langer Lageeditor hinterlässt keine alte Scrollposition im Fokusbereich.
Zurück zur Liste verwirft den Fokus; Rückkehr zum Hub nutzt dessen bestehenden
vollständigen Refresh. Navigation, Fokus und Formulare bleiben ausschließlich RAM.
Bestehende READY-/Generation-/Action-/Requestguards verwerfen stale Resultate;
Lock/Generationwechsel löschen Inhalt, Fokus und Entwürfe. Ein synchroner Revoke
während Navigation darf die gelöschten Formulardaten nicht wieder herstellen.

## Accessibility und Grenzen

Einspaltige Karten mit umbrechenden Texten, 4-dp-Controls und mindestens 48-dp-
Touchzielen. Pseudonym ist Überschrift, Kartenaktion benennt die zu öffnende Person.
Lage/Funktion erscheinen als Text, nicht allein als Farbe. Relative Zeit ist keine
LiveRegion. Große Schrift und Rücknavigation werden synthetisch auf Gerät geprüft.
Ein manueller TalkBack-/IME-Smoke bleibt sinnvoll.

Keine neue Fach-/Securitylogik, Schemaänderung, Dependency oder Permission.
SessionCoordinator, StorageAccess, LeadershipGrant, Key-Admission, Crypto,
Keystore und DB-Key-Lifecycle bleiben unverändert. Die Demo wird nicht geändert;
die neue Personen-UI ist produktspezifisch. UX3c/d bleiben separate Slices.

## Prüfungen und manueller Smoke

`ActivePeopleTest` prüft Summary, alle P4-Lagen, current vs planned, Ort/Ziel,
Zeitgrenzen/Zeitzonen/NO_STATUS, Fokus und vorhandene Writes/Details, atomic Reveal,
Refresh, stale Requests/Generation und Lock. `Ux3bPeopleProbe` nutzt die echte
ProductScreen-Composition mit synthetischen Ports für Navigation, Lage-/Funktion,
Details, Rückwege und Schriftgröße 1/2; keine Produkt-DB-Writes oder Credentials.

Manuell: verschiedene Lagen, aktuelle/geplante Funktion, keine Funktion,
zulässiger Ort/Ziel, Aktualität und NO_STATUS direkt nach Start; Karte öffnen,
Lage/Funktion ändern, Persondetails mit bestehendem Leadership-Step-up, zurück
zur Liste/Hub; große Schrift/TalkBack und Lock während Formular/Load.

### Automatischer Nachweis

- 613 Unit-Tests grün: App 234 (davon 25 neue UX3b-Fälle), Data 375,
  Security 4; keine Failures/Errors/Skips. UX2/UX3a, P4/P5a, UX1b-Navigation,
  Session-Restart und ProductTime/DST eingeschlossen.
- Produkt-/Demo-App und Produkt-AndroidTest gebaut. Lint ohne Fehler oder neue
  Hinweise; normal nur bestehende Gradle-Versionswarnung, Demo ohne Befund.
  Architektur und beide Manifest-/Permissionprüfungen grün, kein INTERNET.
- Galaxy SM-M526BR/API33: finaler `Ux3bPeopleProbe` bei Font Scale 1/2 grün:
  Summary/Karte/Fokus, Lage, Funktion, current vs planned, Details/Rückwege,
  Touchgrenzen und Lock. Ein echter 61-s-Beobachtungszeitraum bestätigt keine
  zusätzlichen Repository-Reads oder Sessioninteraktionen durch den Anzeige-Tick.
  Keine Credentialänderung, kein pm clear/Uninstall oder Systemfontwechsel.
- Vorherige Probe-Fehlläufe zählen nicht als grün: Scrollposition wurde an die
  Personen-/Formularnavigation gebunden; der zweite Formulareinstieg wartet im
  Probe ausdrücklich auf den neuen Header. Kein Weglassen einer Fachprüfung.
- Lokale Logs: `.prototype-cache/ux3b-checks.log`, `ux3b-probe-build.log`,
  `ux3b-galaxy.log`. `git diff --check` grün. Kein Commit, kein Push.
