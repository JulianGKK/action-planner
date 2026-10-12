# UX0 — Verbindliche UX-/UI-Philosophie für PaG

Stand: 12.10.2026. Analysebaseline: sauberer `main`, `3b573ea` (P5a).
PaG steht für „Peacefully against Genocide“. Dieses Dokument definiert das
Zielbild für folgende Umbauslices, keine bereits implementierten Funktionen.
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

## 3. Geschützte Shell, Countdown und erneute Bestätigung

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

### „Neu bestätigen“ — Ziel für einen separaten Security-Slice

Zunächst in den letzten etwa 30 Sekunden: „Sitzung endet gleich“ und die klar
erkennbare Aktion „Neu bestätigen“. Das ist eine neue explizite
DEVICE_CREDENTIAL-Systemauthentisierung, niemals die Leitungspassphrase.
Ein Tap allein verändert die absolute Deadline nicht. Kein automatischer Start.
Keine parallelen Authentisierungsversuche; alte Ergebnisse werden verworfen.

| Ereignis | Verbindliches Zielverhalten |
| --- | --- |
| Frische Authentisierung erfolgreich | Neue normale Sitzung, neue Generation, neue absolute Drei-Minuten-Deadline ab frischem Authentisierungszeitpunkt und neue READY-Inactivity-Basis nach zulässiger Maintenance. |
| Abbruch oder Fehler | Alte Deadline bleibt unverändert. Die alte Sitzung besteht ausschließlich weiter, soweit sie nach allen bisherigen Gates noch gültig ist. Kein Wiederbeleben nach Lock. |
| Alte Sitzung läuft während des Versuchs ab | Regulär LOCKED und alte operative/sensitive UI-Daten bereinigen. Ein späteres zulässiges erfolgreiches Ergebnis des ausdrücklich gestarteten frischen Versuchs darf eine neue Sitzung starten. |
| Erfolg vor altem Ablauf | Möglichst den normalen Screen-Kontext erhalten: Bereich und Navigation als Absicht, Daten unter neuer Generation frisch laden. Keine alten Grants, Requests oder Klartext-Editoren in die neue Generation übernehmen. |
| Foreground-/Device-Lock-Revoke | Bestehenden Revoke-Vertrag anwenden. Authentisierung ist keine allgemeine Foreground-Ausnahme; ein unzulässiger/überholter Versuch darf keine neue Freigabe erzeugen. |

Ungespeicherte Leadership-Sensitive-Geheimnisse werden niemals über die
Sicherheitsgrenze konserviert. Auch normale ungespeicherte Task-/Personenentwürfe
werden in UX0 nicht zur Übernahme zwischen Generationen freigegeben. Nach Ablauf
keine automatische Restaurierung alter Editoren. Neu laden ist keine Wiederaufnahme
alter Requests. Die nach neuem Unlock zulässige Navigation folgt Abschnitt 5.

**Technische Grenze der Baseline:** `SessionCoordinator.authenticate()` akzeptiert
nur LOCKED; `SessionSnapshot` enthält keine Deadline. `MainActivity` behandelt
AUTHENTICATING speziell, READY-Fokusverlust sperrt dagegen. Der gewünschte Versuch
aus einer noch gültigen Sitzung ist daher kein reiner Buttonumbau. Vor Umsetzung
müssen Versuchslifetime, alter Sessionablauf, Android-Dialog/Lifecycle, frische
Keystore-Autorisierung, DB-Key-Lifetime und Generation-Publication separat geprüft
werden. Ein laufender Versuch darf weder absolute noch Idle-/Foreground-Gates
aussetzen. Falls der Systemdialog den erlaubten Foreground beendet, sperrt die
alte Sitzung konservativ; Screen-Erhalt ist dann nachrangig. UX0 ändert keine
dieser Regeln und verspricht keinen nahtlosen Erhalt auf jedem Gerät.

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

- PREPARATION: **Übersicht | Personen | Aufgaben | Rollen**.
- ACTIVE: **Übersicht | Personen | Aufgaben | Funktionen**; Übersicht ist das Lagebild.
- COMPLETED: dieselben lesbaren Perspektiven, eindeutig als Rückblick markiert.
- DRAFT: reduzierter Planungsbereich für die erlaubten Aktionsdaten.

Reiter sind groß und touchfreundlich. Auswahl erhält Text-/Formmarkierung und
Semantik; Farbe allein genügt nicht. Bei schmalem Bildschirm/großer Schrift
Umbruch oder zugängliche Bereichsauswahl; keine immer kleineren Mini-Tabs.
Künftige Bereiche: Hauptbereiche plus „Mehr“, ohne unbegrenzte Tabreihe.
Zur Aktionsliste und manuelles Sperren bleiben erreichbar. Bereichswechsel
schließt Keyboard, verlässt sensitive Grants und verwirft ungespeicherte Entwürfe
im bestehenden Vertrag; kein verdecktes Autosave. Detailrückkehr ist vorhersehbar.

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

## 7. ACTIVE: Lagebild als Arbeitsplatz

### Übersicht — „Wo muss die Leitung gerade hinschauen?“

Aufmerksamkeit vor Vollständigkeit. Gewahrsam, Nicht erreichbar,
Identitätsfeststellung, Unklar und fehlende Lageangabe sind aus bestehenden
Statuswerten sichtbar; HIGH-Tasks und aktuelle Funktionsverteilung ergänzen sie.
Keine automatische Rechtsbewertung, keine neue Statusklasse und keine P5b-Regel
oder automatische Task-Erzeugung. Unbekannt ist nicht normal oder erledigt.

```text
AKTIV · Demo-Aktion
AUFMERKSAMKEIT
2 in Gewahrsam · 1 nicht erreichbar · 2 dringende Aufgaben

IN GEWAHRSAM
Motte · Gesa 1 · 1 offene Aufgabe
Meise · Gesa 2 · 1 offene Aufgabe

NICHT ERREICHBAR
Falter

DRINGENDE AUFGABEN
Hoch · EA kontaktieren · Motte · Gesa-Support

FUNKTIONEN · VOR ORT
8 Blockierende · 3 Support · 1 Hummel · 2 Gesa-Support
```

Pseudonyme und manuelle P4-Ortslabels, keine P3b-Identität, Telefonnummer oder
Notfallkontakte. Gruppen verlinken in gefilterte Details. Normale Personen
bleiben im Personenbereich. Aufmerksamkeit ist eine UI-Projektion, keine
persistierte Risikoeinstufung. Reihenfolge innerhalb der weiteren Problemgruppen
ist vor UX3 mit synthetischen Szenarien festzulegen (Roadmap).

### Personen

Eigener Fokus mit „Alle“, „Vor Ort“, „Gewahrsam“, „Nicht erreichbar“, „Weitere“.
Weitere macht alle restlichen P4-Werte einschließlich „Keine Lageangabe“ erreichbar.
Karte: Pseudonym; Lage und zulässiger Ort; geplante Funktion nachrangig;
Anzahl aktiver betroffener Aufgaben. „Offene Aufgaben“ umfasst hier OPEN und
IN_PROGRESS, die Zuständigkeit wird nicht mit Betroffenheit verwechselt.

Öffnen zeigt große Aktionen „Lage ändern“, „Aufgabe hinzufügen“, „Funktion ändern“.
P4 bleibt maßgeblich: Funktionszuordnung nur bei Vor Ort; andere Statuswerte
verwerfen sie wie bisher. Detail-/P3b-Zugriff nachrangig und separat geschützt.
Keine zusätzlichen Bestätigungen für das Übernehmen einer gültigen Lage.

### Aufgaben

Kompakt „2 Hoch · 4 Offen · 2 In Arbeit“, mit definierten Zählmengen:
Hoch zählt HIGH unter aktiven Tasks, Offen OPEN, In Arbeit IN_PROGRESS.
Aktive Tasks in bestehender Prioritäts-/Fälligkeitsreihenfolge und stabilen
Tie-Breakern. HIGH als Text und Struktur hervorheben. DONE/CANCELLED bleiben
zurückhaltend/eingeklappt. Große Aktion „Aufgabe hinzufügen“, keine kleine FAB
als Voraussetzung. Alle P5a-Regeln einschließlich exklusive Zuständigkeit,
optionale Betroffenheit, minimale Chronik, Templates, stale-state-Abweisung,
kein ACTIVE-Löschen und COMPLETED-read-only bleiben erhalten.

### Funktionen

„Wer übernimmt gerade was?“ Nach aktueller Funktion gruppierte Pseudonyme,
primär Personen mit ON_SITE. Das entspricht der bisherigen Funktionszählung;
NO_STATUS mit übernommener Hauptfunktion behauptet keine verfügbare Person.
Geplante Rollen bleiben im Personenbereich sichtbar. Unzugeordnet wird ehrlich
angezeigt. „Gesa-Support: derzeit niemand“ darf null aktuelle Vor-Ort-Zuordnungen
zeigen, bedeutet aber ohne beschlossenen Sollbedarf nicht automatisch Unterbesetzung.
Keine neuen Sollzahlen oder Kompetenzprofile durch UX einführen.

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
| PagSectionTabs | Wenige Workspace-Bereiche. | Unbegrenzte Mini-Tabs oder Lifecycle-Schritte. | Selected-Semantik, klare aktive Markierung, große Schrift/kleine Breite berücksichtigen; Wechsel beendet Editor nach Vertrag. |
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
