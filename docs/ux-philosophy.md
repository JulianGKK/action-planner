# UX0 — Verbindliche UX-/UI-Philosophie für PaG

Stand: 12.10.2026. Analysebaseline: sauberer `main`, `3b573ea` (P5a).
PaG steht für „Peacefully against Genocide“. Dieses Dokument definiert das
Zielbild für folgende Umbauslices, keine bereits implementierten Funktionen.
Das manuelle UX-Review vom 12.10.2026 legt ACTIVE verbindlich als operativen
Hub fest (Abschnitte 5 und 7); ältere P4/P5a- und UX1-Bedienungsnachweise
beschreiben den damaligen Implementierungsstand, nicht dieses neue Zielbild.
Die [Roadmap](ux-roadmap.md) grenzt deren Freigabe und Prüfungen ab.
Grundlagen: [Produkt](../PRODUCT.md), [Security](security.md),
[Privacy](privacy.md), [Datenmodell](data-model.md), [P4](product-p4.md)
und [P5a](product-p5a.md). Deren bestehende fachliche und Sicherheitsverträge
haben Vorrang. UX0 führt keine neue Speicherung oder Berechtigung ein.

## 1. Leitbild

> PaG zeigt nicht die interne Datenstruktur der App.
> PaG zeigt dem Menschen, was in seiner aktuellen Situation wichtig ist
> und was er als Nächstes tun kann.

> PaG geht davon aus, dass eine entsperrte Sitzung jederzeit abrupt enden
> kann. Jede Arbeitsoberfläche muss deshalb schnell verständlich,
> unterbrechbar und in kurzen sicheren Zeitfenstern produktiv nutzbar sein.

Security hat Vorrang vor Komfort. Es gelten Sicherheit/Datenschutz,
Korrektheit, Verständlichkeit/Wartbarkeit, Testbarkeit, Einfachheit, Komfort
in dieser Reihenfolge. Kurze Sessions sind ein Designparameter.

Jeder Hauptscreen muss unmittelbar vier Fragen beantworten:

1. Wo bin ich? Aktionskontext, Phase und Bereich sind sichtbar.
2. Was ist gerade wichtig? Hierarchie folgt Situation und Handlungsbedarf.
3. Was kann ich jetzt tun? Der nächste zulässige Schritt ist gut erreichbar.
4. Hat meine letzte Handlung funktioniert? Ergebnis oder Fehler ist eindeutig.

Zusätzliche Reviewfrage: „Wie viel der kurzen sicheren Sitzung verbraucht
diese Oberfläche unnötig?“ Unnötige Dialogketten, redundante Bestätigungen,
Navigation, Suchwege, Ladeflackern und Layoutsprünge sind zu vermeiden.
Bestätigungen bleiben für fachlich irreversible Schritte sinnvoll; schnelle
P4-Lageänderungen und reversible P5a-Statusschritte erhalten keine neue Dialogkette.
Kurze Vorgänge werden ausdrücklich gespeichert oder abgebrochen. Unterbrechung
darf weder einen Erfolg vortäuschen noch alte Entwürfe wieder einsetzen.

## 2. Unveränderlicher normaler Sessionvertrag

Das Schutzmodell umfasst ein bereits entsperrtes Gerät, das aus der Kontrolle
des Benutzers gerät. Die Architektur gilt als analysierbar. Verstecken von
Funktionen, zurückhaltendes Branding oder unbekannte Abläufe begründen keinen Schutz.

Für alle realen Arbeitsphasen einschließlich DRAFT und PREPARATION gilt:

- Erfolgreiche explizite DEVICE_CREDENTIAL-Authentisierung startet die Sitzung.
- Drei Minuten absolute Dauer ab erfolgreicher Authentisierung; Maintenance
  verbraucht dieses Budget ebenfalls. Der Timer ist inputunabhängig.
- Benutzeraktivität verlängert niemals die absolute Deadline.
- Zusätzlich führen 30 Sekunden READY-Inaktivität zu LOCKED.
- Verlassen des erlaubten Foregrounds oder Device-Lock führt sofort zu LOCKED.
- Ablauf/Revoke invalidiert die Generation und bereinigt operative UI-Daten,
  sensitive Grants, Passphrasen und Editor-State im bestehenden Vertrag.
- Keine automatische Re-Authentisierung, kein Prompt-Loop, keine Wiederherstellung
  einer Sitzung aus SavedState oder Prozesswiederherstellung.
- Storage-/Session-Gates entscheiden über Operation, Commit und Publication.
  Keine parallele UI-Unlock-Autorität und keine Cache-Ausnahme.

Countdown, Banner, Renderzyklen, Animationen, Refresh, Load-Ergebnisse und
Fokusautomatik sind keine Benutzerinteraktion. Interaktionscallbacks können
synchron widerrufen; neue UI-Pfade müssen danach und nach weiteren synchronen
Callbacks die gültige Generation, Aktion und Request-Bindung beachten.
Keine verlängerte Vorbereitungssitzung, kein „Stay unlocked“ und keine
persistenten operativen/sensitiven Entwürfe über Lock. Referenzbereinigung
behauptet keine vollständige RAM- oder Flash-Überschreibung.

## 3. Geschützte Shell, Countdown und Session-Neustart

### Session-Countdown

Während READY ist im geschützten Arbeitsbereich dauerhaft ein kompakter
Countdown sichtbar, beispielsweise `🔒 1:42`, im Format `m:ss`.
Er bezeichnet die maximal verbleibende Zeit bis zur absoluten Deadline,
nicht eine garantierte weitere Nutzungsdauer. Inaktivität, Foregroundverlust
und Gerätesperre können früher sperren. Eine kurze Erklärung ist zugänglich:
„Sitzung maximal noch 1 Minute 42 Sekunden. Inaktivität kann früher sperren.“

Die Anzeige wird aus einer nur lesbaren Projektion der maßgeblichen monotonen,
Sleep einschließenden Deadline und derselben Zeitbasis abgeleitet. Kein eigener
180-Sekunden-Timer ab READY, keine Wanduhr, keine gespeicherte Ersatzdeadline.
Darstellung höchstens in Sekundenschritten, nie negativ; `0:00` erteilt keinen
Zugriff. Verzögerte Ticks dürfen die Gates nicht beeinflussen. Nach Revoke keine
geschützte Ansicht bis zum nächsten Tick stehen lassen. Ziffern erhalten stabile
Breite. Symbol/Farbe allein reichen nicht; Screenreader erhalten verständlichen
Text, aber keine automatische Ansage jeder Sekunde.

### „Sitzung neu starten“ — konservativer UX1-S-Vertrag

Die ursprüngliche Idee einer weiterlaufenden READY-Session mit „Neu bestätigen“
ist verworfen. Der bestehende READY-Foreground-/Authvertrag trägt sie nicht ohne
neue Koordination. UX1-S nutzt ausschließlich **Lock → normaler Unlock**.

Bei gültigem READY und positiver absoluter Restzeit bis einschließlich 30 Sekunden
erscheint „Sitzung neu starten“ als Secondary-Control neben Countdown und Sperren.
Kein automatischer Prompt, kein Timerbanner. Der Hinweis für Accessibility lautet:
„Sitzung neu starten. Ungespeicherte Eingaben werden verworfen.“

Der Tap beendet sofort die alte Session über den normalen Lock-Pfad, invalidiert
deren Generation und bereinigt operative Daten, Navigation, ungespeicherte Editoren,
Leadership-Grants und sensitive Payload. Anschließend startet der normale explizite
DEVICE_CREDENTIAL-Unlock: LOCKED → AUTHENTICATING → MAINTENANCE → READY.
Der Tap setzt keine neue absolute Deadline. Erst erfolgreiche Authentisierung
verankert die neue 180-Sekunden-Deadline; Maintenance verbraucht dieses Budget.
Cancel bleibt LOCKED; Fehler bleiben gemäß bestehendem Vertrag fail closed
(gegebenenfalls BLOCKED). Keine Rückkehr zur alten Session.

Kein Continuation-Descriptor, kein RAM-/SavedState-Editorrestore und keine neue
READY-Foreground-Ausnahme. Die normale Generation-/Storage-/P3b-Sicherheit bleibt
vollständig erhalten. Nach Erfolg werden Daten frisch geladen; die heutige
Aktionsliste bleibt der Post-Unlock-Einstieg. Das ACTIVE-Routing aus Abschnitt 5
ist späterer UX-Umfang, kein zusätzlicher Router in UX1-S.
Details und Nachweise: [UX1-S Session-Neustart](ux1s-session-restart.md).

### Locked-Screen

Zielaufbau ohne operative Namen, Zähler oder zuletzt geöffneten Datensatz:

```text
PaG
Aktionsplaner

PaG ist gesperrt.
Deine Aktionsdaten sind geschützt.

[             Entsperren             ]
```

Große zentrale, eher rechteckige Hauptaktion. Bei uninitialisiertem Produkt:
„Geschützten Speicher einrichten“. BLOCKED/partiell ungültiger Speicher erhält
eine verständliche Fehleransicht ohne automatische Reparatur oder Ersatzanlage.
AUTHENTICATING erklärt den Systemdialog; MAINTENANCE zeigt einen neutralen
Prüfzustand ohne Fachinhalte. Eine manuelle Sperraktion bleibt gut erreichbar.

## 4. Drei Arbeitsmodi und Lifecycle

| Modus | Ziel und Darstellung | Fachliche Grenze |
| --- | --- | --- |
| Entwurf / Vorbereitung | Ruhig planen, erklären, kurze Formulare, fehlende Vorbereitung sichtbar machen; Personen, Rollen, Aufgaben getrennt. | DRAFT bietet Aktionsdaten und „Vorbereitung beginnen“, keine P4-/P5a-Verwaltung. Erst PREPARATION öffnet diese Bereiche. |
| Aktiv | Lage führen; Aufmerksamkeit zuerst, große Ziele, wenige Taps, schnelle Status-/Task-/Funktionsänderung. | Planung und Personenstammdaten bleiben unveränderbar; aktuelle Lage und Tasks folgen P4/P5a. |
| Abgeschlossen | Ruhig lesen und nachvollziehen; letzter Zustand und vorhandene Task-Chronik. | Read-only, kein Wiederöffnen der Aktion, keine irreführenden Bearbeitungscontrols. |

Lifecycle sichtbar: Entwurf → Vorbereitung → Aktiv → Abgeschlossen.
Aktuelle Phase wird textlich und strukturell markiert. Der nächste zulässige
Übergang ist eine große primäre Aktion: „Vorbereitung beginnen“, „Aktion starten“,
„Aktion abschließen“. Eine kurze Konsequenz erklärt die bestehende Irreversibilität:
Start fixiert Planung/Stammdaten und initialisiert Lage ohne Anwesenheit zu behaupten;
Abschluss beendet Änderungen und kann nicht rückgängig gemacht werden.
DELETION_PENDING ist kein weiterer Arbeitsmodus; keine neue Lösch- oder Retention-UI
in UX0. Keine neuen Startpflichten oder automatische Bereitschaftszertifizierung.

## 5. Aktion als Workspace und Routing

Eine Aktion ist ein eigener Arbeitsraum mit sichtbarem Aktionsheader und Phase.
Keine endlose Seite mit allen fachlichen Bereichen hintereinander.

- PREPARATION: **Übersicht | Personen | Aufgaben | Rollen**; Planung ist primär.
- ACTIVE: **Lagebild-Hub → Bereichsübersicht → konkretes Objekt → fokussierte Aktion**.
  Hauptbereiche sind Personen, Aufgaben, Funktionen und eigene Aktionsdetails.
- COMPLETED: lesbare Perspektiven, eindeutig als Rückblick markiert.
- DRAFT: reduzierter Planungsbereich für die erlaubten Aktionsdaten.

ACTIVE verwendet große touchfreundliche Bereichskarten statt klassischer kleiner
Top-Tabs. PREPARATION-Reiter bleiben groß und zugänglich; Auswahl erhält Text-/
Formmarkierung und Semantik. Bei schmalem Bildschirm/großer Schrift zugängliche
Bereichsauswahl statt immer kleinerer Mini-Tabs.

Wichtige Rücknavigation liegt im fachlichen Header oder als klarer Secondary-
Control, etwa „Zum Lagebild“ bzw. „Zur Personenübersicht“. Kein kleiner Hyperlink
am unteren Ende eines langen Screens. Der Security-Header `🔒 m:ss [Sperren]`
bleibt davon getrennt. Zur Aktionsliste und manuelles Sperren bleiben erreichbar.
Bereichswechsel schließt Keyboard, verlässt sensitive Grants und verwirft
ungespeicherte Entwürfe im bestehenden Vertrag; kein verdecktes Autosave.
Detailrückkehr ist vorhersehbar, nach Lock kein alter Editor oder Grant.

Weitere fachliche Module können als zusätzliche Hub-Bereiche ohne Navigationsumbau
hinzukommen: Protokoll, Vorfälle / Beobachtungen, Dokumente, Uploads und weitere
Module. Noch nicht implementierte Funktionen erscheinen nicht als tote Buttons.
Diese Erweiterbarkeit gibt weder ihre Implementierung noch Netzwerkzugriff frei.

Nach erfolgreichem Unlock erst Maintenance und erfolgreichen geschützten Load
abschließen. Exakt eine ACTIVE-Aktion: direkt ihr Lagebild öffnen. Keine ACTIVE:
Aktionsliste/Planungsbereich. Mehrere ACTIVE sind fachlich möglich (PRODUCT FR-001);
deshalb klarer Auswahlzustand mit geschützten Titeln ausschließlich nach Freigabe.
Fehlgeschlagener/unvollständiger Load darf nicht als „keine aktive Aktion“ routen.
Kein persistenter Shortcut oder Titel auf LOCKED, kein automatischer Editor-Reopen.

## 6. Preparation Workspace

Übersicht beantwortet „Ist diese Aktion bereit?“ mit knappen vorhandenen Fakten
und direktem Weg in Details, ohne deren Inhalte vollständig zu duplizieren:

```text
VORBEREITUNG · Demo-Aktion
Personen       8 angelegt                 [Personen öffnen]
Rollen         5 Rollen                   [Rollen öffnen]
Aufgaben       6 offen · 2 hohe Priorität  [Aufgaben öffnen]
Leitungsschutz Noch nicht eingerichtet    [Jetzt einrichten]

NÄCHSTER SCHRITT
[Aktion starten]
```

Alle Zahlen sind synthetische Illustration. „Bereit“ bleibt eine menschliche
Entscheidung; weder fehlende Aufgaben noch fehlender Leitungsschutz werden ohne
neuen fachlichen Beschluss zum Startblocker. Personenbereich verwaltet Pseudonyme
und Planung, Aufgabenbereich P5a, Rollenbereich Rollen und Vorlagen.

Leitungsschutz ist früh als aktionsweiter Zustand sichtbar: „Noch nicht
eingerichtet“ / „Eingerichtet“, außerdem Loading/Error/Unavailable ehrlich zeigen.
Erklärung: „Schützt sensible Identitäts- und Kontaktdaten dieser Aktion zusätzlich.
Die Leitungspassphrase ersetzt nicht das Entsperren mit dem Gerätecode.“
Einrichtung bleibt bewusst, Verlustfolge verständlich, keine neue Recovery.
Kein Lesen von Personengeheimnissen zum Ermitteln des Einrichtungszustands.
Die Baseline koppelt Inventory an ein Persondetail; die spätere aktionsweite
Metadatenabfrage benötigt eine abgegrenzte Gate-/Privacy-Prüfung, keine Cache-Ausnahme.

## 7. ACTIVE: Lagebild als operativer Hub / Schaltzentrale

Das Lagebild zeigt genug Information, um zu erkennen, welcher Bereich
Aufmerksamkeit braucht, und führt mit großen klaren Controls in fokussierte
Bereichsübersichten. Details liegen dort statt auf einer langen gemeinsamen
Seite. Ziel sind weniger visuelle Konkurrenz, weniger Scrollen, schnellere
Orientierung, bessere Erweiterbarkeit und effiziente Arbeit in kurzen sicheren
Sessions. Grundmuster: **Hub → Bereichsübersicht → Objekt → fokussierte Aktion**.

### Hub-Karten und Aufmerksamkeit

Phone: bei ausreichender Breite sinnvoll zweispaltig; bei geringer Breite oder
großer Schrift sauber einspaltig. Keine starre Zwei-Spalten-Annahme, keine
abgeschnittenen Labels oder Mini-Dashboards innerhalb der Karten.

Jede Karte enthält Bereichsname, sehr kompakte Zusammenfassung, höchstens einen
besonders relevanten Hinweis/Aufruf und eine klare Öffnungsaktion. Optional kommt
eine wirklich sinnvolle Quick Action hinzu. Keine Objektlisten im Hub.

| Bereich | Synthetische kompakte Summary | Öffnen / optionale Quick Action |
| --- | --- | --- |
| Personen | 12 insgesamt · 2 in Gewahrsam · 1 nicht erreichbar · 9 ohne aktuelle Lage | Personen öffnen |
| Aufgaben | 6 offen · 2 in Arbeit · 2 hohe Priorität | Aufgaben öffnen; optional „+ Aufgabe“ |
| Funktionen | 8 Blockierende · 3 Support · 1 Hummel · 2 Gesa-Support | Funktionen öffnen |
| Aktionsdetails | Kurzer Aktionskontext / Zeitraum, eindeutig ACTIVE | Aktionsdetails öffnen; read-only |

Attention-Regel: höchstens **ein Hinweis je Karte**, ausschließlich eine
verständliche Projektion vorhandener Daten. Zum Beispiel direkt nach Aktionsstart
„9 Personen ohne aktuelle Lage“ oder „2 Aufgaben mit hoher Priorität“.
Weitere relevante Fakten bleiben in der kompakten Summary und der Bereichsübersicht.
Keine komplexe Regelengine, persistierte Risikoeinstufung, automatische Eskalation,
Rechtsbewertung oder Task-Erzeugung. Ohne relevanten Hinweis entfällt die Zeile.
Eine unbesetzte wichtige Funktion kann einen einzelnen Hinweis erhalten, soweit
sie aus dem vorhandenen Aktionskontext erkennbar ist; keine neue Pflichtrolle,
Sollzahl oder automatische Einstufung als Unterbesetzung. „Derzeit niemand“
bezeichnet ausschließlich null aktuelle Vor-Ort-Zuordnungen.

### Personenübersicht und Personen-Arbeitsbereich

„Personen öffnen“ führt zur kompakten Summary oben und danach zur Personenliste.
Filter „Alle“, „Vor Ort“, „Gewahrsam“, „Nicht erreichbar“, „Weitere“ halten sämtliche
P4-Statuswerte einschließlich „Keine Lageangabe“ erreichbar.

ACTIVE-Karten zeigen primär Pseudonym, aktuelle Lage, zulässigen aktuellen Ort/Ziel
falls vorhanden, aktuelle Funktion (`currentRole`) und Aktualität der Lage.
Ohne aktuelle Funktion genügt „Keine Funktion“. Der prominente Text
„Geplant: Keine geplanten Rollen“ entfällt. PREPARATION zeigt dagegen geplante
Rollen/Funktionen prominent; geplante Informationen bleiben in ACTIVE bei Bedarf
in Details nachsehbar und dominieren die Übersicht nicht.

Synthetische Beispiele unter dem unveränderten P4-Vertrag:

```text
Motte
In Gewahrsam · Gesa 1
Keine Funktion
Aktualisiert vor 5 Min.

Falter
Vor Ort
Gesa-Support
Gerade aktualisiert
```

P4 erlaubt aktuelle Funktionszuordnung nur bei Vor Ort; Gewahrsam und die anderen
entsprechenden Statuswechsel entfernen sie. Die beim Start für NO_STATUS
übernommene Hauptfunktion behauptet keine Anwesenheit oder Verfügbarkeit.
Kein Klarname, Telefonnummer oder anderes P3b-Feld in Hub oder Übersicht.

Die Personenkarte selbst ist das klare große Tap-Ziel; der redundante
Listenhinweis „Lage aktualisieren“ entfällt. Tap öffnet einen fokussierten
Personen-Arbeitsbereich mit großen Aktionen „Lage ändern“, „Funktion ändern“ und
„Personendetails“. Fachlich unzulässige Funktionsänderung bleibt gesperrt.
Bestehende betroffene Aufgaben und deren zulässige Aktionen sind dort nachrangig
erreichbar; ihre aktive Anzahl darf kompakt erscheinen, ohne die Liste zu überladen.
„Offene Aufgaben“ einer Person umfasst OPEN und IN_PROGRESS mit affectedPerson,
nicht deren Zuständigkeit. P3b bleibt separat unter bestehendem Step-up.
Gültige Lageübernahme erhält keine zusätzliche Bestätigung.

### Lage-Aktualität als UI-Projektion

Anzeige aus vorhandenem `updatedAt` und lokaler Gerätezeit; keine Speicherung
relativer Texte, neue Historie oder neue Beobachtungsmetadaten.

| Alter / Kalendertag | Anzeige |
| --- | --- |
| Unter 1 Minute | Gerade aktualisiert |
| 1 bis 59 Minuten | Aktualisiert vor X Min. |
| Ab 60 Minuten am selben lokalen Kalendertag | Zuletzt aktualisiert um HH:mm |
| Älterer lokaler Kalendertag | Zuletzt aktualisiert am DD.MM. um HH:mm |

Bei einem älteren Kalendertag gilt die Datumsanzeige auch über Mitternacht bei
weniger als 60 Minuten Abstand. Technische NO_STATUS-Initialisierung beim Start
und unbekannte Migrationszeit (`updatedAt=0`) dürfen keine geprüfte Lage suggerieren:
„Keine Lageangabe“ bleibt unbekannt, ohne „Gerade aktualisiert“ für den Initial-
Snapshot. Nicht interpretierbare oder zukünftige Zeiten behaupten keine frische
Prüfung. Ein eventueller UI-Tick meldet keine Sessioninteraction, verlängert keine
Deadline und führt keinen fachlichen Refresh aus.

### Aufgabenübersicht

**Hub → Aufgabenübersicht → Task**. Oben kompakte Summary, danach Taskkarten;
keine Rückkehr zum langen Alles-auf-einer-Seite-Lagebild.
Offen zählt OPEN, In Arbeit IN_PROGRESS, hohe Priorität HIGH unter aktiven Tasks
(OPEN/IN_PROGRESS). Bestehende P5a-Sortierung: HIGH, NORMAL, LOW; innerhalb gleicher
Priorität Fälligkeit vor keiner Fälligkeit, früheste zuerst, danach createdAt und
ID als stabile Tie-Breaker. DONE/CANCELLED bleiben separat/eingeklappt.
Priorität und Status erscheinen als Text und Struktur, nicht nur Farbe.

„+ Aufgabe“ ist in ACTIVE als optionale Hub-Quick-Action zulässig und führt in
einen Entwurf; erst explizites Speichern erstellt die Task. Taskdetail und minimale
Chronik bleiben erhalten. Exklusive Zuständigkeit, unabhängige Betroffenheit,
Templates, stale-state-Abweisung, kein ACTIVE-Löschen und COMPLETED-read-only
bleiben unverändert.

### Funktionenübersicht

Eigener Bereich: „Wer übernimmt gerade was?“ Aktuelle operative Funktionen sind
primär, nicht geplante Rollen. Personen pro Funktion kompakt als Pseudonyme anzeigen.
Funktionssummary und Verteilung zählen wie P4 ausschließlich ON_SITE anhand
`currentRole`; NO_STATUS zählt auch mit übernommener Hauptfunktion nicht als
verfügbare Person. Unzugeordnet ehrlich und knapp darstellen. Planungsdetails
bleiben nachrangig in Details erreichbar. Keine Sollbesetzung, Kompetenzprofile
oder automatische fachliche Eskalationsengine.

### Aktionsdetails

Eigener Hub-Bereich, nicht unter Funktionen vermischen. In ACTIVE gemäß bestehendem
Vertrag read-only. Der bestehende zulässige Lifecycle-Schritt „Aktion abschließen“
bleibt getrennt als ausdrückliche Aktion mit seiner Konsequenzbestätigung erreichbar;
der Hub schafft keine Bearbeitung von Aktionsdaten.

## 8. Buttons und Formulare

Ruhige Formensprache: eher rechteckig, geringe Rundung, großzügige Höhe,
klare Verben. Farben, Logo, Schriftfamilie, exakte Radien und dekorative
Animationen bleiben unentschieden.

| Hierarchie | Einsatz | Grenze |
| --- | --- | --- |
| PRIMARY | Zentrale nächste Aktion eines Bereichs; groß und eindeutig. | Nicht alle Aktionen gleichzeitig als gleichrangige Hauptaktionen darstellen. |
| SECONDARY | Klarer alternativer Vorgang, z. B. umrandet. | Nicht für beiläufige Metadaten. |
| TERTIARY | Nachrangige Textaktion mit trotzdem großem Touch-Ziel. | Keine zentrale Lifecycle-, Speicher- oder operative Hauptaktion als winzigen Link tarnen. |

Person hinzufügen fokussiert Pseudonym, Aufgabe hinzufügen Titel, Rolle anlegen
Rollenname und öffnet die passende Tastatur. Fokus nur beim bewussten Eintritt,
nicht bei jeder Recomposition/Load. Screenreader-Fokus nicht unkontrolliert stehlen.
Next folgt fachlicher Eingabereihenfolge. Done darf einfache gültige Formulare
abschließen, wenn das klar erkennbar ist; sonst nur Tastatur schließen.
Ungültige Eingabe verhindert Mutation und erhält korrigierbaren Text mit
feldbezogener Inline-Meldung. Abbrechen erzeugt keine Mutation.
Navigation, Save und Cancel schließen Keyboard sinnvoll. Technische Fehlertexte,
Entity-IDs, Generationen oder rohe Java-Meldungen sind keine Microcopy.

Beispiele: „Person gespeichert“, „Aufgabe wieder geöffnet“, „Rolle Support
angelegt“, „Keine Aufgaben vorhanden“. Deutschsprachig immer Aktion/Aktionen;
`Action` bleibt ein technischer Bezeichner. Hinweise vermeiden unnötige
Identitäts-/Kontaktdaten in Titel und Ortslabel.

## 9. Mobile Datum-/Uhrzeit-Eingabe

Action: kompakte lesbare Zeile, z. B. „Beginn — Sa, 24. Okt. 2026 · 06:00“
mit „Ändern“. Datum und Zeit direkt in einer überschaubaren Inline-Auswahl;
keine unnötige Folge mehrerer Dialoge. Zeitzone verständlich sichtbar.
Beginn/Ende bleiben Pflichtzeitpunkte mit bestehender End-vor-Beginn-Prüfung.

Task: „Fällig — Keine Fälligkeit“, schnelle Datumsauswahl „Heute“, „Morgen“,
„Datum wählen“ und ausdrückliches Entfernen. **Diese Chips wählen nur das Datum
im Entwurf, noch keinen gespeicherten Zeitpunkt.** Das bestehende dueAt ist ein
optionaler eindeutiger Zeitpunkt, kein Kalendertag. Sobald eine Fälligkeit gesetzt
wird, muss die Uhrzeit bewusst gewählt/bestätigt werden; kein implizites Mitternacht,
Tagesende oder „jetzt“. Datum allein als Fälligkeit wäre ein eigener Produktslice.
Bei Uhrzeitverzicht bleibt die Fälligkeit unset und ein unvollständiger
Fälligkeitsentwurf verhindert Speichern.

Gemeinsamer `selectedInstant(LocalDateTime, ZoneId)` bleibt maßgeblich:
0 gültige Offsets → ablehnen; 1 → akzeptieren; mehr als 1 → ablehnen.
DST Gap und Overlap erhalten verständliche Inline-Meldungen, keine stille
Verschiebung oder implizite Offsetwahl. Eingabe bleibt korrigierbar, kein Crash.
Instant-Anzeige in Gerätezone ist keine neue UTC-Persistenzsemantik;
UTC-Kalendertagsrepräsentation des Pickers nicht als lokale Uhrzeit interpretieren.
Unveränderte bestehende Action-Instantwerte bleiben beim Bearbeiten erhalten.

## 10. Loading, Content, Empty, Error und Feedback

| Zustand | Bedeutung und Verhalten |
| --- | --- |
| LOADING | Noch kein erfolgreiches Ergebnis für aktuelle Generation/Aktion; stabile reservierte Struktur mit verständlicher Ladeanzeige. Keine Nullzählungen oder Leerbehauptung. |
| CONTENT | Erfolgreich geladene Inhalte, gebunden an aktuelle Sitzung/Aktion/Request. |
| EMPTY | Ausschließlich erfolgreicher Load mit tatsächlich null Einträgen; Erklärung und zulässiger nächster Schritt. Leeres Filterergebnis als solches benennen. |
| ERROR | Load/Write gescheitert; verständlich erklären, zulässiges Wiederholen anbieten. Kein Erfolg, Empty oder automatische Reparatur vortäuschen. |

Nach Unlock neutral laden, dann möglichst eine stabile aufgebaute Arbeitsansicht.
Bereiche dürfen unabhängig laden, müssen das aber sichtbar kenntlich machen.
Zusammenfassungen aus noch nicht geladenen Tasks zeigen keine „0 Aufgaben“.
Während Refresh dürfen gültige Inhalte derselben Sitzung markiert bestehen bleiben;
nach Revoke sofort bereinigen, keine Anzeige alter Daten als Ladeplatzhalter.
Schreib-Erfolg erst nach zulässiger Repository-Publication melden.

Wo Wirkung nicht unmittelbar klar ist, kurzer Statusbanner nahe oben:
„✓ Rolle Support angelegt“, „✓ Aufgabe wieder geöffnet“, „✓ Person gespeichert“.
Keine Bannerflut für jeden Tap. Automatisches Verschwinden nach einigen Sekunden
als Ausgangspunkt, mindestens etwa fünf Sekunden und an Accessibility-Bedarf
angepasst; Fehler/Handlungsbedarf bleiben bis zur Klärung sichtbar.
Banner ist nicht die einzige Quelle eines relevanten Ergebnisses; Zustand bleibt
am Inhalt erkennbar. Keine operativen Meldungen über Lock konservieren oder als
Systemnotification veröffentlichen. Countdown-Warnung verdrängt keine Fehlermeldung.

## 11. Accessibility

- Touch-Ziele mindestens 48 × 48 dp, operative Hauptaktionen möglichst mindestens
  56 dp hoch; kein knappes Icon/Linkziel als einziger Zugriff.
- Text skaliert mit Systemeinstellung; keine abgeschnittenen Hauptlabels oder
  horizontale Überforderung durch Reiter. Ausreichender Kontrast bei späterer Farbwahl.
- Status immer textlich, zusätzlich Struktur/Icon/Markierung; nie allein Farbe.
- Verständliche Labels, Heading-, Selected-, Expanded-, Disabled- und Button-Semantik.
  Auswahlzustände und Gründe unzulässiger Aktionen sind verständlich erreichbar.
- TalkBack-Lesereihenfolge folgt der visuellen Hierarchie. Statusbanner kann eine
  zurückhaltende Ansage erhalten; Countdown nicht jede Sekunde ungefragt vorlesen.
- Fokus und Hardwarekeyboard funktionieren vorhersehbar; beim Bereichswechsel
  sinnvollen Fokus setzen, sensitive Anzeigen schließen, keine Fokusloops.
- Wesentliche Rückmeldungen sind nicht nur kurz transient sichtbar. Keine
  Verlängerung der Security-Fristen als Accessibility-Ersatz.

## 12. Demo, Rückblick und spätere Beobachtungen

### Demo und Quickstart

Ziele: „90-Sekunden-Tour starten“ und „Demo frei erkunden“. Dauerhaft sichtbares
DEMO, ausschließlich synthetische Daten, sauber getrennte Demo-Datenquelle
bevorzugt in Memory, kein echter Product-DB-/Key-/Sessionzugriff.
Demo kann ohne realen Lockout trainieren; das ist keine Ausnahme für echte Daten.
Tour erklärt den realen Schutzvertrag. Wechsel beendet Demo-State und öffnet
reale Daten nur über reguläre Authentisierung/Gates; keine Datenübernahme.

Synthetisches Szenario: Demo-Aktion; Motte, Blockierende, Vor Ort;
Meise, Blockierende, In Gewahrsam · Gesa 1; Falter, geplant Support,
aktuell Gesa-Support · Vor Ort; HIGH-Task „EA kontaktieren“, betrifft Meise.
Tour und freie Erkundung offline, keine Telemetrie oder Lernfortschrittsprofile.

### Completed / Rückblick

Aktionsstatus und Abschlusszeit, letzter Lage-Snapshot, Planung, Aufgaben und
vorhandene minimale TaskEvents ruhig lesbar. Chronik benennt nur vorhandene
Lifecycle-Ereignisse; sie ist keine Personen-/Bewegungs-/Bearbeitungshistorie.
Keine erfundene Vergangenheit, keine Audit-/Evidence-Garantie, keine permanente
Archivierung oder neue Retention. P3b-Zugriff bleibt separat und zeitlich begrenzt.
Read-only heißt fehlende Bearbeitungscontrols statt scheinbar bedienbarer Controls.

### Vorfälle / Beobachtungen — eigener späterer Slice

Möglicher weiterer Lagebildbereich zur sachlichen Dokumentation. UX0 definiert
kein Datenmodell und keine Evidence-/Forensikversprechen. Vor Implementierung
Product/Security/Privacy klären: Zeitstempel und Zeitquelle, Editierbarkeit,
Integrität und deren Grenzen, Anhänge/Fotos, Metadaten, Retention, Personenbezug,
Zugriff und Löschung. Beobachtung und rechtliche Bewertung getrennt behandeln.
PaG klassifiziert Geschehnisse nicht automatisch als „rechtswidrig“.

## 13. Kleines wiederverwendbares PaG-UI-System

Konzeptionelle Namen, keine Implementierung oder Bibliotheksentscheidung.
Alle Komponenten übernehmen Abschnitt 11 und geben keine Security-Autorität vor.

| Komponente | Zweck / wann einsetzen | Wann nicht | Verhalten / Accessibility |
| --- | --- | --- | --- |
| PagPrimaryButton | Nächste zentrale zulässige Aktion. | Metadaten oder jede Nebenaktion. | Große Fläche, klares Verb, Button-Semantik, Busy verhindert Doppelaufruf; keine implizite Mutation. |
| PagSecondaryButton | Eindeutige Alternative. | Hauptaktion ohne erkennbare Hierarchie. | Umrandete/strukturell unterscheidbare Form, gleichwertiges Touch-Ziel und Disabled-Semantik. |
| PagTertiaryButton | Nachrangige Textaktion. | Zentrale Lifecycle-/operative Aktion. | Textlabel und mindestens 48-dp-Ziel; kein reiner Farb-/Hyperlinkcode. |
| PagHubCard | ACTIVE-Bereich öffnen, kompakte Summary und höchstens ein Hinweis. | Objektlisten, Mini-Dashboard oder nicht implementierter Bereich. | Großes Touch-Ziel, klare Öffnungssemantik; responsive ein-/zweispaltig, optionale Quick Action separat erreichbar. |
| PagSectionTabs | Wenige PREPARATION-Workspace-Bereiche. | ACTIVE-Hub, unbegrenzte Mini-Tabs oder Lifecycle-Schritte. | Selected-Semantik, klare aktive Markierung, große Schrift/kleine Breite berücksichtigen; Wechsel beendet Editor nach Vertrag. |
| PagStatusBanner | Kurzes unklar bleibendes Ergebnis. | Jeder Tap, geheimhaltungsbedürftige Inhalte außerhalb READY. | Zurückhaltende Ansage, adaptierte Dauer, Fehler dauerhaft, bei Revoke bereinigt. |
| PagActionHeader | Aktionsname, Phase und Kontext in READY. | LOCKED/MAINTENANCE oder vollständige Detailkopie. | Heading und Status als Text; stabiler Platz, kurze Details nachrangig. |
| PagLoadingState | Laufender zulässiger Load. | Erfolgreich leere Daten oder Fehler. | Stabiler Platz und lesbares „Wird geladen“; keine simulierten Daten/Zähler oder Interaktionsticks. |
| PagEmptyState | Erfolgreiches leeres Ergebnis/Filter. | Noch nicht geladen. | Menschliche Erklärung, optional zulässiger nächster Button; Kontext/Filter benennen. |
| PagErrorState | Fehlgeschlagener Vorgang. | Gewöhnliche Leere oder Erfolg. | Dauerhafte verständliche Meldung, sichere Retry-Aktion, keine Secrets/Stacktraces/automatische Reparatur. |
| PagLifecycleCard | Phase und nächster erlaubter Übergang. | Task-Lifecycle als Aktionsphase ausgeben. | Aktuell textlich markieren, Konsequenz erklären, große Aktion und bestehende Bestätigung. |
| PagFormField | Fachliche Eingabe mit Inline-Validierung. | Reine read-only Daten. | Label, Fehlersemantik, passender Keyboardtyp/Next/Done; Fokus nur bei Eintritt, Abbrechen ohne Mutation. |
| PagSessionCountdown | Information über absolute Restzeit in READY. | Eigene Deadline/Gate oder Anzeige operativer Daten auf LOCKED. | m:ss, verständliche Restzeitsemantik, stabile Breite, keine sekündliche Ansage/Benutzeraktivität; frühere Sperre erklären. |

## 14. Code-Realitäts-Check der Baseline

Die folgenden Aussagen stammen aus gelesenem Code, nicht nur Abschlussdokumentation.
Pfade unter `app/src/main/kotlin/org/pag/prototype/product/`:

| Stelle | Heutiges Verhalten / Friktion | Wiederverwendung und Umbaugrenze |
| --- | --- | --- |
| ProductScreen.kt: ProductScreen | Ein äußerer verticalScroll für Shell, Aktionsdetails, Personen/Operations/Tasks und Lifecycle. Task-Wechsel scrollt per LaunchedEffect auf 0. | Ruhigen Header/Shell abtrennen, Workspace-Bereiche schaffen; bestehende Lifecycle-Labels und Konsequenztexte nutzen. Scroll-/Fokuswechsel prüfen. |
| ProductScreen.kt: Aktionsliste; ProductViewModel.kt: subscription | actions startet leer; READY stößt listActions an, isEmpty zeigt sofort „Noch keine Aktionen vorhanden“. Kein eigenes Action-Loading/Error-Modell im ViewModel. | Expliziter Loadzustand nötig; ein kosmetischer Spinner allein löst den Port-/Resultvertrag nicht. |
| ProductViewModel.kt: loadPersons/clearPersons | clearPersons leert Tasks, Operations, Sensitive und Personen. Person-Load startet danach Operations-/Task-Loads; personsLoading deckt nur Personen ab. | Quellen-/Bereichsladezustände und Request-Bindung getrennt ausdrücken, ohne alte Daten nach Revoke zu halten. Kein Gesamtrefactor auf Vorrat. |
| OperationsScreen.kt: OperationsSection | Lagebild ist unter PersonsSection verschachtelt; alle Statuszahlen, Rollen, Tasks und Personen folgen untereinander. Taskform/-detail beendet übrige Overview-Ausgabe. | Statuslabels, ON_SITE-Funktionszählung, LiveForm mit 56-dp-Zielen und P4-Policies nutzen; Übersicht/Personen/Funktionen strukturell trennen. |
| OperationsModel.kt / TaskModel.kt | screen/personId bzw. selected/form/filter/busy/error steuern zugleich Daten und Navigation; board=null lässt Sektionen ohne eigenen Load/Error-Inhalt verschwinden. | Schrittweise eindeutige Bereiche/Ergebniszustände; Generation-/Action-/Request-Gates erhalten, insbesondere P5a-guardedInteraction und Cleanup-Nachprüfung. |
| ProductInputs.kt | FormButtons schließen Fokus/IME, ProductInputScope reagiert auf Screen-Key, Lifecycle und Fensterfokus. FormTextField unterstützt FocusRequester, Next bewegt Fokus, Done schließt nur IME. | Gute Grundlage, keine vollständige Autofokus-/Submit-Lösung. Neue Komponenten um bestehendes Verhalten bauen, HoldToReveal und Password-Semantik erhalten. |
| ProductScreen.kt: ActionForm/DateTimeField; TaskScreen.kt: TaskForm | Action nutzt große Inline-Date-/TimePicker mit Übernehmen; Task verlangt formatierten Freitext dd.MM.yyyy HH:mm. Kein automatischer Eintrittsfokus in Standardformularen. | ProductTime.kt unverändert nutzen; kompakte mobile Auswahl konzipieren, Keyboard/Fokus und DST-Fehler erhalten. |
| SensitiveScreen.kt / ProductViewModel.kt: refreshSensitive | Einrichtung erst im Persondetail; Inventory benötigt Aktion und Person. NoSensitiveCapture und kurzlebiger Grant schützen Eingaben. | Frühe aktionsweite Setup-Anzeige braucht passenden Metadatenpfad. Keine P3b-Payload in Header/Lagebild und kein Umgehen der Capture-/Grant-Grenzen. |
| OperationsScreen.kt: RoleManagement | Name/Editing werden beim Auslösen des Write sofort lokal geleert; viele Nebenaktionen als TextButton, kein Erfolgsbanner. | Ergebnisgebundene Rückmeldung statt optimistischem Erfolg, einfache Formularwege und bestehende Validierung nutzen. |
| TaskScreen.kt | Priorität textlich; aktive/geschlossene Tasks, Minimalchronik und Betroffenheit vorhanden. Optionale Auswahlgruppen benötigen mehrere Taps; kein eigener Aufgaben-Workspace. | P5a-Regeln, Sortierung und Summary nutzen; Hierarchie/Navigation verbessern, keine neue Event-Historie. |

`ProductScreen` baut seinen Input-Screen-Key aus Sessiongeneration, Aktions-/Person-
und mehreren Editor-/Unterbereichsfeldern. Eine neue Navigation muss diese
Fokus-/Cleanup-Auslöser bewusst erhalten. `MainActivity` führt Ablaufprüfung und
normale UI-Ereignisse auf main aus; Repository-Publication läuft über Gates zurück
auf main. UI darf keine theoretische zweite Session oder langlebige DAO-Quelle schaffen.

Rein visuell sind Labelhierarchie, Buttonform, Spacing und kompakte read-only
Anordnung, soweit Daten-/Callbackpfade gleich bleiben. Navigation, Loadzustände,
Autofokus/IME, Banner und Routing berühren dagegen Cleanup, echte Interaktion
und Publication und benötigen Regressionen. Countdown-Projektion, erneute
Authentisierung und aktionsweite Leadership-Metadaten sind explizit
Security-relevant. Demo-Quellentrennung ist eine eigene technische Grenze.
