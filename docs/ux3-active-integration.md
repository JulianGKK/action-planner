# UX3 — ACTIVE Integration

Baseline: sauberer Branch `ux3-active-integration`, HEAD `32951a5` (Merge UX3d).
UX3a–d werden als zusammenhängender Arbeitsplatz geprüft, ohne neue Fachfunktion.
`v0.1.0-alpha.1` bleibt unverändert.

## Cross-Area-Verträge

- Hub, Personen, Aufgaben und Funktionen beziehen ihre Projektionen aus dem
  jeweils vollständig publizierten WorkspaceSnapshot. OPEN/IN_PROGRESS getrennt;
  HIGH ausschließlich aktive Tasks. Funktionen ausschließlich ON_SITE/currentRole.
  NO_STATUS mit technisch vorhandener Startfunktion zählt nicht als Besetzung.
- Funktionswechsel erscheint identisch in Person, alter/neuer Gruppe und Hub.
  Der RAM-Rückkontext zur alten Gruppe bleibt bestehen, auch wenn sie leer wird.
  Nicht-ON_SITE entfernt die P4-Funktion, zählt aber nicht als Ohne Funktion.
- Taskanlage aus Personenkontext belegt nur affectedPerson vor. Zuständigkeit
  bleibt unabhängig. Personencount zählt ausschließlich aktive affected Tasks
  (OPEN/IN_PROGRESS); Filter verwendet ebenfalls ausschließlich affectedPersonId.
- Personstatus löst keine Taskanlage, Transition, Prioritäts- oder Zuordnungsänderung
  aus. currentRole-Wechsel verändert assigneeRoleId eines Tasks nicht. Diese
  Referenz gehört zur Rolle, nicht zum momentanen Einsatz einer Person.
- Chronik bleibt P5a-TaskEvents, P3b bleibt separater Persondetail-/Step-up-Pfad.
  Keine sensitive Payload/Identitäts-/Kontaktfelder in Summaries oder Karten.

## Navigation und minimale Integrationsfixes

Hub → Bereich → Objekt → bestehende fokussierte Aktion. Unterkontexte bleiben
RAM-only. Funktion → Person → zurück bleibt im bisherigen kleinen FunctionFocus;
Person → Tasks wechselt bewusst in den Aufgabenbereich. Filter ist sichtbar und
entfernbar; Hub-/Bereichswechsel verwirft alte Tasknavigation.

Gefundene und behobene Lücken:

1. UX3b hatte die bestehende affected-task-Anzahl aus den ACTIVE-Karten entfernt.
   Die kompakte Zeile ist wieder vorhanden, ebenso im Personenarbeitsbereich.
   Sie verwendet ausschließlich `TaskBoard.affectedCount`; ohne geladenes TaskBoard
   wird keine Zahl behauptet. Keine neue Count- oder Taskfachregel.
2. Neuanlage führte nach Save zur Übersicht statt zum geforderten Taskdetail.
   Ein privater flüchtiger Task-ID-Merker wird erst nach erfolgreichem Write gesetzt.
   Die Auswahl erfolgt erst bei vollständiger Board-Publication, gemeinsam mit
   Daten/Chronik. Navigation und Lock entfernen den Merker. Eine während Refresh
   bewusst geöffnete andere Aufgabe wird nicht vom alten Create-Fokus überschrieben.
   PREPARATION-Verhalten unverändert; keine neue Domain-Entity/Cachearchitektur.
3. Personenformulare zeigten „‹ Personen“, obwohl Abbrechen zur fokussierten Person
   führt. Bei vorhandenem Personenfokus lautet der Rückweg nun „‹ Zur Person“.
   Direkte Legacy-Formulare ohne Fokus behalten ihren Rückweg zur Personenübersicht.
4. ACTIVE-Persondetails hatten zusätzlich zum Header denselben Rückweg unten.
   ACTIVE nutzt den Header; PREPARATION/COMPLETED bleiben unverändert.
5. Das bestehende Funktionsformular zeigte „Personendetails“, dessen Handler
   akzeptierte aber nur LIVE. Derselbe gebundene Discard-/Persondetailpfad akzeptiert
   nun auch FUNCTION: kein Write, kein Boardread, Entwurf weg, Person-/Gruppenfokus
   erhalten. Der P3b-Handler selbst wird nicht geändert. Ein zusätzlicher Test
   reproduzierte vorher `expected p, got null` und ist nach Fix grün.

Save/Edit/Lifecycle verwenden vorhandene Repositoryoperationen und genau den
bisherigen Refresh. Öffnen, Rückkehr zum Bereich, Filter/Disclosure benötigen
keinen zusätzlichen vollständigen Read. Scroll-/Fokuskeys der vorhandenen Shell
setzen Bereich/Formular/Detail sinnvoll an den oberen Header zurück.
Autofocus und IME-Done bleiben bestehende Formcontrols; kein SavedState-Restore.

## Publication, Session und Cleanup

Initial benötigte Reads werden lokal gestaged und gemeinsam publiziert;
keine Teilzahlen/falsche Leere und keine Behauptung gemeinsamer DB-Transaktion.
Refresh hält gültigen alten Inhalt bis zum vollständigen Ersatz. Der neue
Create-Fokus darf insbesondere nicht zwischen Operations- und Task-Result erscheinen.
Request, Generation, Action/Status und Bereich begrenzen Publication unverändert.

Lock wird aus Hub, Persondetail, Lage-/Funktionsformular, Taskdetail/-formular,
Funktionsdetail und Neue-Funktion-Form geprüft: Workspace, Fokus, Filter,
Disclosure, Entwürfe und vorgemerkte Taskauswahl weg. Alte Callbacks publizieren
auch nach erneutem READY einer anderen Generation nichts. Anzeigeprojektionen
erzeugen keine Repository-Reads/Sessioninteraction; der vorhandene Minuten-Tick
und Countdown bleiben reine Darstellung.

COMPLETED bleibt bereichsübergreifend lesbar und verweigert alle P4-/P5a-Writes,
einschließlich Neuanlagen, Rollen-/Lagewechsel, Taskedit/Lifecycle und Löschen.
Kein UX5-Umbau.

## Tests und Grenzen

`ActiveIntegrationTest` nutzt eine gemeinsame synthetische Welt hinter allen
Domainports und den echten ProductViewModel/OperationsModel/TaskModel. Cross-Area-
Writes werden mit nachfolgenden tatsächlichen Bereichsloads und Projektionen
verglichen; kontrolliert verzögerte Reads prüfen gemeinsame Publication,
Navigation und Revoke. Vor Fix waren zwei neue Create-Fokusfälle reproduzierbar
rot (`expected new, got null`); danach grün.

`Ux3ActiveIntegrationProbe` bedient die echte Produkt-Compose-UI in einem
zusammenhängenden synthetischen Lauf bei Font Scale 1/2: Person/Funktion/Hub,
personbezogene Taskanlage/Detail/Lifecycle/Count/Filter, unabhängiger Lagewechsel,
Funktionen/Hub und verschachtelter Lock. Keine Produkt-DB-Writes, Credentials,
pm clear, Uninstall oder Systemfontänderung. Der vorhandene UX3c-Probe ist auf
den bewusst geänderten Rückweg nach Neuanlage angepasst.

Accessibility: bestehende umbrechende Karten, große Touchziele, semantische
Überschriften/textuelle Status, sichtbare Filter/Disclosure und keine neue LiveRegion.
Native-Proben ersetzen kein manuelles TalkBack-/Langtext-/IME-Review oder echten
Credential-Smoke. Mehrere getrennte Boardreads sind keine gemeinsame DB-Sicht
zu exakt einem Zeitpunkt; UI-Publication ist gemeinsam, Storagegates bleiben autoritativ.

Scope: keine Domain-/DB-/Schema-/Security-/Dependency-/Produktpermissionänderung.
Keine neue Tabelle/Migration, Authentifizierung, sensitive Daten, Netzwerk,
Worker/Notification/Location, automatische Task-/Rollenentscheidung, Sollbesetzung,
P5b, UX5, Protokoll/Vorfälle oder Dokumente/Uploads. Demo bleibt unverändert.

Manueller End-to-End-Smoke: Hub → Person → Funktion ändern → Gruppe/Hub prüfen;
Person → betroffene Aufgabe mit unabhängiger Zuständigkeit anlegen → Lifecycle,
Count/Filter/Hub; Person nicht verfügbar setzen → Aufgaben unverändert und
Funktion entfernt; P3b-Persondetails hin/zurück; große Schrift/TalkBack und Lock
während Formular/Load, danach normales neues Routing ohne alten Editor.

## Automatischer Nachweis

- 685 Unit-Tests grün: App 306 (16 neue Cross-Area-Fälle), Data 375, Security 4;
  keine Failures/Errors/Skips. UX2/UX3a–d, UX1b-/Sessionrestart, P4/P5a, P3b und
  ProductTime/DST eingeschlossen. Keine duplizierte neue Security-Gerätekampagne.
- Produkt-/Demo-Build und Produkt-AndroidTest erfolgreich. Lint ohne Fehler
  oder neue Hinweise; normal nur bestehende Gradle-Versionswarnung, Demo ohne
  Befund. Architektur und beide Manifest-/Permissionchecks grün, kein INTERNET.
- Galaxy SM-M526BR/API33: ein zusammenhängender synthetischer Lauf gegen echte
  ProductScreen bei Font Scale 1/2 grün. Person/Funktion/Hub, Funktionsformular-
  Persondetail-Shortcut ohne Write, Taskanlage → Detail/Lifecycle/Count/Filter,
  unveränderte Tasks nach Personstatuswechsel, Funktionen/Hub und verschachtelter
  Lock geprüft. Keine Credentialänderung, kein pm clear/Uninstall oder Systemfontwechsel.
- Vorherige Fehlläufe zählen nicht als grün: zwei Create-Fokusfälle und der
  Funktionsformular-Shortcut waren vor Fix rot; der erste Geräte-Probe scheiterte
  bei Font Scale 2 an einem Accessibility-Node nach Scroll. Der neue Probe erneuert
  bei `ACTION_CLICK=false` den Tree innerhalb der bestehenden begrenzten Suche;
  dauerhaft unerreichbare Controls führen weiterhin zum Fehler.
- Logs lokal: `.prototype-cache/ux3-integration-before.log`,
  `ux3-integration-shortcut-before.log`, `ux3-integration-checks.log`,
  `ux3-integration-probe-build.log`, `ux3-integration-galaxy.log`.
  `git diff --check` und Whitespaceprüfung neuer Dateien grün.
  Historischer Alpha-Tag unverändert. Kein Commit, kein Push.
