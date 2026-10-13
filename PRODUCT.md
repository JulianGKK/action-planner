# PaG Aktionsplaner

**Version:** 0.1
**Status:** Product Definition / MVP Specification
**Projektart:** Offline-first Android-Anwendung
**Primärer Use Case:** Organisation zeitlich begrenzter Aktionen und Veranstaltungen sozialer Bewegungen
**Referenz-Use-Case:** Peacefully against Genocide (PaG)
**Technologieziel:** Android APK, vollständig lokal und ohne zwingende Netzwerkverbindung

---

## 1. Produktvision

**Verbindliche Terminologie:** Technische Bezeichner verwenden `Action`. In der deutschsprachigen Produktoberfläche und fachlichen Dokumentation heißt das Konzept konsequent „Aktion“, in der Mehrzahl „Aktionen“. Die Domain-Entität `Action` repräsentiert eine Aktion. Die App ist ein Aktionsplaner; technische Klassen, APIs, Datenbanknamen und historische Test-IDs werden nicht sprachlich umbenannt.

Verbindliches UX-Zielbild für den schrittweisen Umbau: [UX-Philosophie](docs/ux-philosophy.md)
und [UX-Roadmap](docs/ux-roadmap.md). UX0 ist eine Dokumentationsspezifikation;
bestehende fachliche, Security- und Privacy-Verträge bleiben unverändert.

Der **PaG Aktionsplaner** ist eine lokal betriebene Android-Anwendung zur organisatorischen Durchführung zeitlich begrenzter Aktionen.

Die Anwendung soll insbesondere die organisatorische Arbeit vor, während und nach einer Aktion unterstützen:

* Personen und Rollen verwalten
* Aufgaben koordinieren
* Material organisieren
* relevante Dokumente bereitstellen
* organisatorische Statusinformationen erfassen
* Kosten dokumentieren
* Informationen und Protokolle einer Aktion verwalten
* Daten nach definierten Speicherfristen automatisch entfernen

Die Anwendung verfolgt einen **Privacy-by-Design- und Security-by-Design-Ansatz**.

Die zentrale Annahme lautet:

> Ein Gerät kann verloren gehen oder in fremde Hände gelangen. Die Anwendung muss deshalb so gestaltet sein, dass ein Angreifer möglichst wenig verwertbare Information vorfindet.

Die Anwendung soll nicht versuchen, physische Beschlagnahmung oder staatliche Ermittlungen technisch zu vereiteln. Stattdessen soll sie durch **Datenminimierung, Verschlüsselung, kurze Speicherfristen und lokale Verarbeitung** das Risiko eines unnötigen Datenabflusses reduzieren.

---

# 2. Zielgruppe

## 2.1 Primäre Zielgruppe

Mitglieder bzw. Organisator:innen sozialer Bewegungen, die zeitlich begrenzte Aktionen organisieren.

Insbesondere:

* Aktionskoordination
* lokale Organisationsgruppen
* Helfer:innenkoordination
* Materialkoordination
* Veranstaltungsorganisation

## 2.2 Sekundäre Zielgruppe

Perspektivisch:

* andere politische Gruppen
* NGOs
* soziale Bewegungen
* lokale Initiativen
* Bündnisse
* Demonstrations- und Veranstaltungsorganisationen

Die Anwendung soll technisch nicht auf PaG beschränkt sein.

---

# 3. Grundprinzipien

## P-001 — Offline-first

Die Kernfunktionalität muss vollständig ohne Internetverbindung funktionieren.

## P-002 — Local-first

Alle operativen Daten werden zunächst ausschließlich lokal auf dem Android-Gerät gespeichert.

## P-003 — Privacy by Design

Personenbezogene Daten werden nur erhoben, wenn sie für einen definierten Zweck erforderlich sind.

## P-004 — Security by Design

Sicherheitsanforderungen werden bereits bei Architektur und Datenmodell berücksichtigt und nicht erst nachträglich ergänzt.

## P-005 — Data Minimization

Die Anwendung soll standardmäßig möglichst wenige personenbezogene Daten speichern.

Die Schutzmaßnahmen haben folgende Priorität: Daten möglichst nicht erheben; nicht mehr benötigte Daten früh löschen; Persistenz minimieren; Freigabezeit minimieren; Authentifizierung erzwingen; persistenten Bestand verschlüsseln; Metadaten- und UI-Leaks reduzieren. Verschlüsselung ruhender Daten schützt keine Inhalte, die in einer gültigen READY-Sitzung bereits als Klartext verarbeitet oder angezeigt werden.

## P-006 — Purpose Limitation

Daten sollen einem konkreten organisatorischen Zweck zugeordnet werden.

## P-007 — Storage Limitation

Daten sollen nur so lange gespeichert werden, wie sie für den vorgesehenen Zweck erforderlich sind.

## P-008 — No mandatory cloud

Für den MVP darf kein Cloudkonto erforderlich sein.

## P-009 — No telemetry

Der MVP enthält keine Analyse-, Tracking- oder Werbedienste.

## P-010 — Open architecture

Die Architektur soll eine spätere Prüfung, Erweiterung und unabhängige Sicherheitsanalyse ermöglichen.

---

# 4. Nicht-Ziele des MVP

Folgende Funktionen sind ausdrücklich **nicht Bestandteil von Version 0.1**:

* Online-Synchronisation
* Cloudspeicher
* Webanwendung
* Chat
* Social-Media-Integration
* GPS-Tracking
* kontinuierliche Standortüberwachung
* Push Notifications
* lokale PaG-Notifications und Push-Infrastruktur
* Foreground Services
* operative Widgets und operative dynamische/persistente Shortcuts
* öffentliches Teilnehmer:innenportal
* automatischer Datenabgleich zwischen Geräten
* zentrale Benutzerverwaltung
* komplexes CRM
* öffentliche Kampagnenverwaltung
* automatisierte Medienarbeit
* Umgehung staatlicher Ermittlungsmaßnahmen
* Täuschungs- oder Tarnfunktionen bei Beschlagnahmung

Diese Funktionen können gegebenenfalls in späteren Produktphasen separat bewertet werden.

Im MVP keine Benachrichtigungsberechtigung, keine sensiblen Lockscreen-Inhalte und keine eigenen Notification Channels ohne später nachgewiesene technische Notwendigkeit. Eine solche Notwendigkeit hebt das Verbot von PaG-Notifications nicht auf.

Keine Sicherheitsmechanismen zur Verbergung der installierten App: kein versteckter Launcher, keine geheimen Start- oder Dialer-Codes, keine Tarnung als andere App und keine versteckten Komponenten zur Umgehung normaler App-Erkennung. Zurückhaltendes Branding ist zulässig, kein Schutzversprechen. Die installierte App gilt bei Geräteanalyse als auffindbar.

---

# 5. Zentrales Domänenmodell

Die zentrale Einheit der Anwendung ist die **Aktion**.

```
Organisation
    │
    └── Aktion
         │
         ├── Personen
         │
         ├── Rollen
         │
         ├── Aufgaben
         │
         ├── Material
         │
         ├── Dokumente
         │
         ├── Kosten
         │
         └── Speicherfristen
```

Eine Aktion besitzt einen Lebenszyklus:

```
Entwurf
   ↓
Vorbereitung
   ↓
Aktiv
   ↓
Abgeschlossen
   ↓
Aufbewahrung / Review
   ↓
Löschung
```

---

# 6. Funktionale Anforderungen

## 6.1 Aktionen

### FR-001 — Aktion erstellen

Der Admin muss eine neue Aktion erstellen können. Es können mehrere Aktionen gleichzeitig erfolgen.

Eine Aktion besitzt mindestens:

* Titel
* Beschreibung
* Beginn
* Voraussichtliches Ende
* Status
* Erstellungszeitpunkt
* Standard-Speicherfrist

### FR-002 — Aktion bearbeiten

Der Admin kann Aktionsinformationen ändern.

### FR-003 — Aktionsstatus

Eine Aktion besitzt einen Status:

* Entwurf
* Vorbereitung
* Aktiv
* Abgeschlossen
* Löschung ausstehend

„Gelöscht“ ist ausschließlich das terminale Ergebnis; kein persistierter Aktionsstatus und kein verbleibender Datensatz. Abgeschlossene Aktionen werden im MVP nicht wiedereröffnet.

### FR-004 — Aktionsübersicht

Die Anwendung zeigt eine Übersicht aller aktuell vorhandenen Aktionen.

### FR-005 — Aktion abschließen

Eine Aktion kann manuell abgeschlossen werden.

Das Abschließen löst die definierte Nachbereitungs- und Retention-Logik aus.

P6a.1 definiert eine ausschließlich protokollbezogene COMPLETED-Ausnahme:
bewusste Korrekturen/Ergänzungen und gekennzeichnete neue Nachträge sind fachlich
zulässig. Dies öffnet weder P4/P5a-Schreibpfade noch den Aktionslebenszyklus erneut;
Umsetzung folgt separat. Siehe [P6](docs/product-p6a-protocol.md).


---

# 7. Personenverwaltung

## FR-010 — Person anlegen

Der Admin kann eine Person einer Aktion zuordnen.

Mögliche Felder:

* interne ID
* Name
* Pseudonym
* Telefonnummer
* Notizen
* Rolle(n)
* Status
* Erstellungszeitpunkt
* Löschzeitpunkt

Die Felder müssen optional sein, sofern sie für den konkreten Zweck nicht erforderlich sind.

## FR-011 — Person bearbeiten

Personendaten können geändert werden.

## FR-012 — Person entfernen

Eine Person kann vor Ablauf der regulären Speicherfrist gelöscht werden.

## FR-013 — Personenstatus

Eine Person kann einen organisatorischen Status besitzen.

Beispielsweise:

* angefragt
* zugesagt
* anwesend
* abwesend
* zurück
* unbekannt

Die Statuswerte sollen konfigurierbar sein.

## FR-014 — Keine permanente Standortverfolgung

Der MVP speichert keine kontinuierlichen GPS-Daten.

Organisatorische Statusinformationen können manuell erfasst werden.

## FR-015 — Personenbezogene Daten klassifizieren

Die Anwendung soll intern zwischen unterschiedlichen Sensitivitätsklassen unterscheiden können.

---

# 8. Rollen

## FR-020 — Rolle erstellen

Der Admin kann Rollen definieren.

Beispiele:

* Koordination
* Material
* Anmeldung
* Erste Hilfe
* Dokumentation
* Fahrer:in
* Technik
* Sonstiges
* Hummeln
* Bienenkönigin
* Presse
* Gesa-Support
* KüFA

## FR-021 — Mehrere Rollen

Eine Person kann mehrere Rollen besitzen.

## FR-022 — Rollen pro Aktion

Rollen gelten grundsätzlich innerhalb einer Aktion.

---

# 9. Aufgaben

## FR-030 — Aufgabe erstellen (P5a)

Aktionsgebundene Aufgaben besitzen einen getrimmten Titel (1–120 Unicode-Codepoints),
Priorität Niedrig/Normal/Hoch (Default Normal), Status, optional Fälligkeit sowie
Erstellungs-/Änderungszeit. Kein separates Freitext-Notizfeld.
Zuständigkeit: niemand ODER eine Person ODER eine Rolle derselben Aktion.
Eine unabhängige optionale betroffene Person verbindet die Aufgabe mit dem Lagebild.

## FR-031 — Aufgabenstatus und minimale Chronik

Offen → In Arbeit; Offen/In Arbeit → Erledigt oder Entfällt;
Erledigt/Entfällt → Wieder öffnen (Offen). completedAt nur bei Erledigt.
TaskEvents enthalten ausschließlich Erstellt, In Arbeit, Erledigt, Entfällt,
Wieder geöffnet. Keine Änderungs-, Personenstatus-, Orts- oder allgemeine Audit-Historie.

## FR-032 — Aufgaben als Teil des Lagebilds

PREPARATION erlaubt Erstellen/Bearbeiten/Löschen und bewusste statische Vorlagenauswahl.
ACTIVE erlaubt schnelle Erstellung, Bearbeitung und Lifecycle; kein Löschen.
COMPLETED ist read-only. DRAFT/DELETION_PENDING bieten keinen Task-Verwaltungspfad.
Das ACTIVE-Lagebild ist als UX-Ziel ein operativer Hub mit kompakter Aufgaben-
Summary und Zugang zur Aufgabenübersicht. Diese zeigt aktive Aufgaben nach
Priorität/Fälligkeit sowie separate erledigte/entfallene Aufgaben; betroffene
Aufgabenanzahlen bleiben bei pseudonymen Personen erreichbar. Details und
Umsetzungsslices: [UX-Philosophie](docs/ux-philosophy.md), [UX3a–d](docs/ux-roadmap.md).
Der P5a-Fachvertrag bleibt unverändert.

## FR-033 — Fälligkeit und Vorschläge

Fälligkeit ist optional, nur innerhalb der App angezeigt, ohne Alarm oder Worker.
Statische Vorlagen sind organisatorische Vorschläge, keine rechtliche Pflichtaussage.
sourceTemplateKey ist stabil und nicht unique; keine globale Template-Persistenz.
Situationsabhängige Vorschlagslogik ist erst P5b, keine automatische Aufgabenerzeugung.

Verbindlicher P5a-Vertrag: [docs/product-p5a.md](docs/product-p5a.md).
Task/TaskEvent sind ausdrücklich INTERNAL in der bestehenden SQLCipher-DB;
normaler READY-Zugriff analog P4, keine zusätzliche P3b-Freigabe.

---

# 10. Materialverwaltung

## FR-040 — Material anlegen

Material kann mit folgenden Informationen verwaltet werden:

* Bezeichnung
* Kategorie
* Anzahl
* Standort
* Verantwortliche Person
* Status
* Notiz

## FR-041 — Materialstatus

Beispiele:

* vorhanden
* benötigt
* ausgeliehen
* unterwegs
* defekt
* unbekannt

## FR-042 — Materialsuche

Material muss durchsucht und gefiltert werden können.

---

# 11. Dokumente und Dateien

## FR-050 — Datei importieren

Der Admin kann Dateien innerhalb der Anwendung einer Aktion zuordnen.

Unterstützte Formate sollen im MVP zunächst begrenzt werden.

Priorität:

1. PDF
2. Bilder
3. einfache Textdateien

## FR-051 — Verschlüsselte Dateispeicherung

Sensible Dateien werden verschlüsselt gespeichert.

## FR-052 — Keine öffentliche Speicherung

Die Anwendung darf sensible Dateien nicht automatisch in öffentliche Android-Medienverzeichnisse kopieren.

## FR-053 — Dokument löschen

Dokumente können manuell gelöscht werden.

## FR-054 — Dokument-Retention

Dokumente können eigene Speicherfristen besitzen.

---

# 12. Informations-/Materialbibliothek

## FR-060 — Informationsmaterial

Allgemeines Informationsmaterial kann innerhalb einer Aktion bereitgestellt werden.

Beispiele:

* FAQ
* Fahrpläne
* Checklisten
* öffentliche Informationsdokumente
* organisatorische Hinweise

## FR-061 — Kategorien

Materialien können kategorisiert werden.

## FR-062 — Offline-Verfügbarkeit

Einmal importiertes Material ist offline verfügbar.

---

# 13. Protokolle / Notizen

## FR-070 — Notiz erstellen

Notizen können einer Aktion oder Person zugeordnet werden.

## FR-071 — Sensible Notizen

Sensible Notizen müssen entsprechend der Sensitivitätsklasse geschützt werden.

## FR-072 — Retention

Notizen besitzen eine definierte Speicherfrist.

### P6a.1 — eigener Aktionsprotokollbereich (Produktentscheidungen)

[Der P6-Vertrag](docs/product-p6a-protocol.md) ist fachlich entschieden, noch
nicht implementiert: Schlüsselereignisse und relevante Einträge sind frei oder
über Vorlagen erfassbar. Ereigniszeit, ungefähre Zeit und tatsächliche Erfassungszeit
je Fassung bleiben getrennt. Kategorien sind Dokumentationshilfen, keine
juristische Qualifikation, Rechtsbewertung oder Vollständigkeitsbehauptung.

P6 ist SENSITIVE; normale Arbeit in gültigem READY benötigt trotzdem keinen
zusätzlichen P3b-/Leadership-Step-up. Diese enge operative Zugriffsausnahme
ändert weder PRIV-06 allgemein noch den separaten Schutz von P3b-Identitäts-/
Kontaktwerten. Keine automatische P3b-Übernahme oder P4-/Taskmutation.

ProtocolEntry besitzt vollständige ProtocolRevision-Fassungen: ORIGINAL,
CORRECTION, ADDENDUM und MARKED_MISTAKEN. Normaler Produktpfad hängt an, ohne
stilles Überschreiben oder Einzel-Löschen. 0..n ausdrücklich betroffene Personen
derselben Aktion werden nur über Pseudonyme angezeigt; eintragsbezogene Revisionen
sind die enge PRIV-03-Ausnahme, keine allgemeine Audit- oder Personenhistorie.

ACTIVE erlaubt Erfassung/Korrektur/Ergänzung/Irrtumsmarkierung. COMPLETED erlaubt
bewusste P6-Nachbereitung einschließlich neuer Einträge mit dauerhafter Kennzeichnung
„Nachtrag nach Abschluss“, getrenntem occurredAt und tatsächlichem recordedAt.
P4/P5a und Aktionsstammdaten bleiben read-only; kein Reopen, stilles Editieren oder
normales Einzel-Löschen. DELETION_PENDING besitzt keinen normalen P6-Zugriff.

Optionale Quellenart und Behördenkennungen nur gemäß P6-Vertrag; Kennungen nur im
geöffneten Detail, keine globale Behördenkartei oder aktionsübergreifende
Wiedererkennung. Personenlöschung kann Referenzen lösen, verändert aber Protokoll-
text/Fassungen nicht automatisch; spätere manuelle Inhaltsprüfung ist notwendig.
Eigene endliche P6-Retentionbetrachtung innerhalb der Aktion, konkrete Frist noch
offen. Vollständige Aktionslöschung entfernt alle Fassungen, Relationen und Kennungen.

Verbindliche Unicode-Codepointgrenzen: Titel 120, Beschreibung/Ansage je 2000,
Begründung laut Polizei 1000, lokale Bezeichnung 80, einzelne Kennung 120;
maximal 32 Kennungen je Fassung. Getrimmt, leere optionale Angaben nicht speichern,
keine unbegrenzte Freitextalternative. Weitere Details im P6-Vertrag.

Hubkarte „Protokoll“ erst nach späterer Implementierung, ohne Namen/Kennungen/
Freitextvorschau. Keine Medien, Exporte, neue Permissions oder Securityarchitektur.
Keine Beweis-/Forensik-/Timestamp-/Verwertbarkeitsgarantie; konkrete rechtliche
Prüfung vor Echtdaten erforderlich. Nächster Schritt ist ausschließlich die
separate Implementierungsspezifikation, noch keine technische Umsetzung.

---

# 14. Kostentracker

## FR-080 — Ausgabe erfassen

Eine Ausgabe besitzt:

* Betrag
* Datum
* Kategorie
* Beschreibung
* optionalen Beleg
* Aktion

## FR-081 — Summen

Die Anwendung berechnet:

* Gesamtkosten
* Kosten nach Kategorie

## FR-082 — Belege

Ein Beleg ist optional; gespeicherte Belege werden wie alle operativen Daten verschlüsselt.

---

# 15. Retention / Datenlebenszyklus

Dies ist ein Kernbestandteil des Produkts.

## FR-090 — Speicherfrist

Jedes sensible Datenobjekt soll eine Speicherfrist besitzen.

## FR-091 — Standardfristen

Das System stellt sinnvolle Standardfristen bereit.

## FR-092 — Individuelle Fristen

Der Admin kann die Speicherfrist, soweit organisatorisch und rechtlich zulässig, anpassen.

## FR-093 — Ablaufwarnung

Die Anwendung informiert vor Ablauf einer Speicherfrist.

Warnungen erfolgen ausschließlich innerhalb einer zulässigen entsperrten PaG-Sitzung, nicht als Systembenachrichtigung. Das Verpassen einer Warnung verlängert keine Frist.

## FR-094 — Automatische Löschung

Nach Ablauf der Speicherfrist werden die betreffenden Daten automatisch aus dem logischen Datenbestand entfernt.

## FR-095 — Schlüsselbasierte Löschung

Für verschlüsselte Dateien soll, soweit technisch sinnvoll, die Löschung von Daten durch Verwerfen des zugehörigen kryptografischen Schlüsselmaterials unterstützt werden.

## FR-096 — Löschprotokoll

Die Anwendung darf keine sensiblen Inhalte in Löschprotokollen erhalten.

---

# 16. Dashboard

## FR-100 — Aktionsdashboard

Das Dashboard zeigt mindestens:

* Aktionsstatus
* nächste Aufgaben
* überfällige Aufgaben
* Personenanzahl
* Rollenübersicht
* Materialstatus
* anstehende Löschungen
* Kostenübersicht

Diese Module sind der perspektivische Produktumfang. Das ACTIVE-UX-Ziel bündelt
implementierte Bereiche als große Hub-Karten und führt in fokussierte Übersichten;
noch nicht implementierte Bereiche erscheinen nicht als tote Buttons. Aktuell
verfügbare Bereiche: Personen, Aufgaben, Funktionen und eigene read-only
Aktionsdetails. Protokoll, Vorfälle / Beobachtungen, Dokumente, Uploads und weitere
Module benötigen eigene Freigaben; daraus folgt kein neuer Netzwerkzugriff.

## FR-101 — Schnelle Bedienung

Häufige Aktionen sollen maximal wenige Interaktionen benötigen.

---

# 17. Suche

## FR-110 — Lokale Suche

Personen, Aufgaben, Material und Dokumente sollen lokal durchsucht werden können.

## FR-111 — Sensible Suchdaten

Suchindizes dürfen keine unbeabsichtigten Klartextkopien sensibler Daten erzeugen.

---

# 18. Sicherheit

## SEC-001 — Verschlüsselte lokale Datenbank

Sensible strukturierte Daten dürfen nicht unverschlüsselt auf dem Gerät gespeichert werden.

## SEC-002 — Verschlüsselte Dateien

Sensible Dateien werden verschlüsselt gespeichert.

## SEC-003 — Android Keystore

Kryptografische Schlüssel sollen soweit möglich über den Android Keystore abgesichert werden.

## SEC-004 — Keine hartcodierten Geheimnisse

Passwörter, Schlüssel und andere Geheimnisse dürfen nicht im Quellcode enthalten sein.

## SEC-005 — Keine Klartext-Credentials

Authentifizierungsinformationen dürfen nicht im Klartext gespeichert werden.

## SEC-006 — Kein Cloud Backup

Sensible lokale Daten dürfen nicht automatisch über Android- oder andere Backupmechanismen repliziert werden.

## SEC-007 — Keine Telemetrie

Der MVP sendet keine Nutzungs- oder Diagnosedaten an externe Server.

## SEC-008 — Minimale Berechtigungen

Die Anwendung fordert ausschließlich für ihre Funktionen erforderliche Android-Berechtigungen an.

## SEC-009 — Keine unnötigen Netzwerkberechtigungen

Wenn Netzwerkzugriff für den MVP nicht erforderlich ist, soll die Anwendung diesen nicht benötigen.

## SEC-010 — Sichere Fehlerbehandlung

Fehlermeldungen dürfen keine sensiblen Daten enthalten.

## SEC-011 — Keine sensiblen Logs

Sensible Daten dürfen nicht unbeabsichtigt in Logcat oder Debug-Logs geschrieben werden.

## SEC-012 — App-Vorschau

Sensible Inhalte sollen beim Wechsel in den Android-App-Switcher nicht unnötig sichtbar bleiben.

## SEC-013 — Clipboard

Sensible Daten sollen nicht ohne ausdrückliche Benutzeraktion in die Zwischenablage kopiert werden.

## SEC-014 — Sichere Dateischnittstellen

Im MVP keine externe Klartextöffnung, externe Viewerübergabe oder Exporte operativer Daten. Intern unterstützte Formate werden im Viewer-/Storage-Prototyp festgelegt; falls sichere interne Darstellung nicht vertretbar möglich ist, ist keine Vorschau zulässig.

## SEC-015 — Kryptografische Standardverfahren

Es werden ausschließlich etablierte und überprüfbare kryptografische Verfahren und Bibliotheken eingesetzt.

## SEC-016 — Keine eigene Kryptografie

Das Projekt implementiert keine eigenen kryptografischen Primitive.

---

# 19. Authentifizierung

## SEC-020 — App-Sperre

Die Anwendung besitzt eine lokale Entsperrfunktion.

Sie startet LOCKED und sperrt bei Gerätesperre, Verlassen der App sowie Ablauf einer ihrer Sitzungsgrenzen. Die absolute Grenze beginnt unmittelbar nach erfolgreicher expliziter PaG-Authentifizierung, läuft während MAINTENANCE und READY und ist unveränderlich. Aktivität, I/O und Maintenance verlängern sie nicht. Die zusätzliche Inaktivitätsgrenze gilt für READY; nur qualifizierte tatsächliche Vordergrundinteraktion mit PaG aktualisiert den Aktivitätszeitpunkt. MAINTENANCE zeigt keine Fachinhalte und bleibt durch die absolute Grenze begrenzt.

Sessiondauer verwendet injizierbare monotone, Sleep einschließende Zeit, keine Wanduhr. Ablauf führt zu LOCKED und invalidiert die Generation. Eine neue Sitzung benötigt einen neuen explizit gestarteten DEVICE_CREDENTIAL-Flow; Geräteentsperrung oder verbleibende Keystore-Autorisierung allein genügt nicht. Keine automatischen Prompt-Schleifen. Storage erzwingt denselben Gültigkeitsvertrag wie die UI; Einzelheiten und Prüfpunkte in docs/architecture.md, ARCH-31 bis ARCH-34. Für P1 sind 3 Minuten absolute Sessiondauer und 30 Sekunden READY-Inaktivität freigegeben. Spätere Anpassbarkeit bleibt eine bewusste Produkt-/Securityentscheidung.

## SEC-021 — PIN

Eine lokale PIN kann zur Authentifizierung verwendet werden.

## SEC-022 — Biometrie

Android-Biometrie kann als Authentifizierung nicht verwendet werden.

## SEC-023 — Rate Limiting

Wiederholte fehlgeschlagene Authentifizierungsversuche müssen begrenzt werden.

## SEC-024 — Schlüsseltrennung

Authentifizierungsdaten und eigentliche Datenverschlüsselung sollen kryptografisch sauber voneinander getrennt werden.

---

# 20. Rollen und Berechtigungen

## SEC-030 — Admin

Der MVP besitzt zunächst einen lokalen Admin.

## SEC-031 — Least Privilege

Die Architektur muss spätere Benutzerrollen ermöglichen.

## SEC-032 — Keine implizite Vertrauensannahme

Ein Benutzer darf nicht allein aufgrund des Zugriffs auf einen bestimmten UI-Bildschirm als autorisiert betrachtet werden.

---

# 21. Datenschutz

## PRIV-001 — Dateninventar

Für jeden gespeicherten Datentyp wird dokumentiert:

* Zweck
* Sensitivität
* Speicherort
* Speicherfrist
* Löschverfahren

## PRIV-002 — Datenminimierung

Nicht benötigte Felder werden nicht erhoben.

## PRIV-003 — Zweckbindung

Personendaten werden einer konkreten organisatorischen Verwendung zugeordnet.

## PRIV-004 — Speicherbegrenzung

Personenbezogene Daten werden nicht unbegrenzt gespeichert.

## PRIV-005 — Manuelle Löschung

Der Admin kann Daten vor Ablauf ihrer Frist löschen, sofern keine technische Abhängigkeit besteht.

## PRIV-006 — Keine versteckten Kopien

Die Anwendung darf keine unkontrollierten Kopien personenbezogener Daten erzeugen.

## PRIV-007 — Privacy Review

Vor produktivem Einsatz soll eine fachkundige datenschutzrechtliche Prüfung erfolgen.

---

# 22. Integrität

## INT-001 — Konsistente Daten

Strukturierte Änderungen müssen transaktional und konsistent gespeichert werden. Über Datenbank, Dateisystem und Keystore wird keine atomare Gesamttransaktion behauptet; übergreifende Abläufe müssen definiert wiederanlauffähig sein (docs/architecture.md).

## INT-002 — Keine halbfertigen Schreibvorgänge

Bei Stromverlust oder App-Absturz darf die Datenbank nicht in einen inkonsistenten Zustand geraten.

## INT-003 — Integritätsprüfung

Die Anwendung soll erkennen können, wenn interne Datenstrukturen beschädigt wurden.

## INT-004 — Transaktionen

Mehrteilige strukturierte Datenänderungen müssen über Datenbanktransaktionen abgesichert werden. Datei- und Schlüsseloperationen werden durch die wiederanlauffähigen Abläufe der Architektur koordiniert.

---

# 23. Verfügbarkeit

## AV-001 — Offline-Betrieb

Die Kernfunktionen funktionieren ohne Internet.

## AV-002 — App-Neustart

Nach vollständigem Schließen kann die Anwendung ihre Daten wiederherstellen.

## AV-003 — Stromausfall

Ein unerwarteter Stromverlust darf möglichst nicht zu Datenkorruption führen.

## AV-004 — Kapazitätsgrenzen

Die Anwendung soll definierte Grenzen für:

* Anzahl Personen
* Anzahl Dokumente
* Dateigröße
* Anzahl Aktionen

besitzen oder testen.

---

# 24. Bedienbarkeit

## UX-001 — Einfachheit

Die Anwendung muss auch unter Zeitdruck bedienbar sein.

## UX-002 — Wenige Schritte

Häufige Aktionen sollen mit möglichst wenigen Interaktionen möglich sein.

## UX-003 — Fehlervermeidung

Die Anwendung soll destruktive Aktionen bestätigen oder anderweitig gegen Fehlbedienung absichern.

## UX-004 — Löschwarnung

Vor irreversiblen manuellen Löschungen muss die Konsequenz eindeutig dargestellt werden.

## UX-005 — Offline-Indikator

Der Benutzer soll erkennen können, dass die Anwendung vollständig lokal arbeitet.

## UX-006 — Statussichtbarkeit

Der Zustand einer Aktion muss jederzeit eindeutig erkennbar sein.

---

# 25. Barrierefreiheit

## A11Y-001

Bedienung muss mit Android Accessibility Services möglichst kompatibel sein.

## A11Y-002

Ausreichende Kontraste.

## A11Y-003

Bedienung ohne ausschließlich farbliche Unterscheidungen.

## A11Y-004

Skalierbare Schriftgrößen.

## A11Y-005

Sinnvolle Screenreader-Beschriftungen.

---

# 26. Performance

## PERF-001 — Lokale Operationen

Normale Datenbankoperationen sollen ohne wahrnehmbare Verzögerung erfolgen.

## PERF-002 — Verschlüsselung

Verschlüsselung darf nicht zu einer für die Benutzer:innen unzumutbaren Verzögerung führen.

## PERF-003 — Große Dateien

Große Dateien dürfen nicht unnötig vollständig in den Arbeitsspeicher geladen werden.

## PERF-004 — Startzeit

Die App soll auch bei umfangreicheren Aktionen schnell starten.

## PERF-005 — Speicherverbrauch

Speicherintensive Operationen sollen kontrolliert durchgeführt werden.

---

# 27. Testbarkeit

## TEST-001 — Unit Tests

Geschäftslogik wird automatisiert getestet.

## TEST-002 — Security Tests

Sicherheitskritische Komponenten erhalten dedizierte Tests.

## TEST-003 — Encryption Tests

Es wird getestet, dass sensible Daten nicht versehentlich im Klartext gespeichert werden.

## TEST-004 — Retention Tests

Speicherfristen und Löschvorgänge werden automatisiert getestet.

## TEST-005 — Crash Tests

Die Anwendung wird auf Verhalten bei:

* Absturz
* Stromverlust
* erzwungenem Beenden
* Speicherknappheit

getestet.

## TEST-006 — UI Tests

Die wichtigsten Benutzerabläufe werden automatisiert getestet.

## TEST-007 — Device Tests

Tests erfolgen auf mehreren realen Android-Geräten.

---

# 28. Sicherheits- und Datenschutzdokumentation

Das Projekt soll mindestens folgende Dokumente enthalten:

```
docs/
├── architecture.md
├── threat-model.md
├── data-model.md
├── privacy.md
├── data-retention.md
├── security.md
├── testing.md
└── deployment.md
```

---

# 29. Threat Model — erste Fassung

## Angreifer

### T-001 — Verlust des Gerätes

Das Gerät geht verloren.

### T-002 — Physischer Zugriff

Eine dritte Person erhält physischen Zugriff auf das Gerät.

Ein ressourcenstarker staatlicher/forensischer Akteur mit Zeit und Werkzeugen ist ausdrücklich eingeschlossen. Er kann ein bereits entsperrtes Gerät mit PaG in READY übernehmen, das Gerätecredential zunächst nicht kennen und die Sitzung durch Bedienung offen zu halten versuchen. Android-Bildschirmtimeout ist keine ausreichende Sicherheitsgrenze. Beschlagnahmung in LOCKED, später bekannte Credentials, Offline-Dateisystemanalyse, RAM-/Debuggerzugriff, OEM-/forensische Schwachstellen und späterer OS-/Kernel-Zugriff werden in docs/threat-model.md getrennt bewertet. Gegen ein vollständig kompromittiertes laufendes Betriebssystem wird keine zuverlässige Kontrolle oder Vertraulichkeitsgarantie behauptet. Sessionablauf ist keine beschlagnahmeabhängige Löschung.

### T-003 — Bösartige Anwendung

Eine andere Android-Anwendung versucht, auf Daten zuzugreifen.

### T-004 — Backup

Das Betriebssystem oder ein Dienst erzeugt ungewollte Kopien.

### T-005 — Fehlerhafte Implementierung

Ein Programmierfehler erzeugt eine unverschlüsselte Datenkopie.

### T-006 — Dependency Compromise

Eine externe Bibliothek enthält eine Schwachstelle oder wird kompromittiert.

### T-007 — Benutzerfehler

Der Admin exportiert oder teilt sensible Informationen versehentlich.

---

# 30. Schutzgüter

Die Anwendung muss insbesondere schützen:

* Identität von Teilnehmer:innen
* Kontaktdaten
* Rollen
* Teilnahmeinformationen
* interne Notizen
* organisatorische Statusinformationen
* Dokumente
* Kosteninformationen
* interne Organisationsinformationen

---

# 31. Sicherheitsziel

Das primäre Sicherheitsziel lautet:

> **Vertraulichkeit, Integrität und kontrollierte Verfügbarkeit der lokalen Aktionsdaten bei möglichst geringer Datensammlung.**

Sekundäre Ziele:

* Authentizität
* Nachvollziehbarkeit organisatorischer Änderungen
* kontrollierte Löschung
* sichere Fehlerbehandlung

---

# 32. MVP-Datenmodell

Eine erste konzeptionelle Datenstruktur:

```
Action
 ├── id
 ├── title
 ├── description
 ├── start_at
 ├── end_at
 ├── status
 ├── created_at
 └── retention_policy_id

Person
 ├── id
 ├── action_id
 ├── name
 ├── phone
 ├── email
 ├── status
 ├── created_at
 └── retention_until

Role
 ├── id
 ├── action_id
 └── name

PersonRole
 ├── person_id
 └── role_id

Task
 ├── id, actionId, title
 ├── priority, status
 ├── assigneePersonId ODER assigneeRoleId (optional)
 ├── affectedPersonId (unabhängig, optional)
 ├── dueAt, sourceTemplateKey (optional)
 ├── createdAt, updatedAt
 └── completedAt (nur DONE)

TaskEvent
 └── id, actionId, taskId, type, occurredAt

Material
 ├── id
 ├── action_id
 ├── name
 ├── quantity
 ├── status
 └── location

Document
 ├── id
 ├── action_id
 ├── filename
 ├── mime_type
 ├── size
 ├── created_at
 └── retention_until

Note
 ├── id
 ├── action_id
 ├── person_id
 ├── content
 └── retention_until

Expense
 ├── id
 ├── action_id
 ├── amount
 ├── category
 ├── description
 └── created_at

RetentionPolicy
 ├── id
 ├── name
 └── duration
```

Die konkrete Datenbankstruktur wird erst nach Abschluss des Threat Models finalisiert.

---

# 33. MVP User Flow

## Start

```
App öffnen
    ↓
Authentifizierung
    ↓
Dashboard
```

## Neue Aktion

```
Dashboard
    ↓
Neue Aktion
    ↓
Titel / Zeitraum
    ↓
Aktion angelegt
```

## Person

```
Aktion
    ↓
Personen
    ↓
Person hinzufügen
    ↓
Daten eingeben
    ↓
Rolle auswählen
```

## Aufgabe

```
Aktion
    ↓
Aufgaben
    ↓
Neue Aufgabe
    ↓
Verantwortliche Person
    ↓
Deadline
```

## Abschluss

```
Aktion
    ↓
Abschließen
    ↓
Retention anzeigen
    ↓
Aktion archiviert
    ↓
automatische Löschungen
```

---

# 34. MVP-Sicherheitsarchitektur

Vorgesehene Grundstruktur:

```
┌────────────────────────────────────┐
│           Android App              │
│                                    │
│  ┌──────────────────────────────┐  │
│  │         UI / Compose         │  │
│  └──────────────┬───────────────┘  │
│                 │                  │
│  ┌──────────────▼───────────────┐  │
│  │      Application Layer       │  │
│  └──────────────┬───────────────┘  │
│                 │                  │
│  ┌──────────────▼───────────────┐  │
│  │      Security Layer          │  │
│  │                              │  │
│  │ Authentication              │  │
│  │ Key Management              │  │
│  │ Encryption                  │  │
│  └──────────────┬───────────────┘  │
│                 │                  │
│  ┌──────────────▼───────────────┐  │
│  │      Local Storage           │  │
│  │                              │  │
│  │ Encrypted DB                │  │
│  │ Encrypted Files             │  │
│  └──────────────────────────────┘  │
│                                    │
│          NO SERVER                 │
│          NO CLOUD                  │
└────────────────────────────────────┘
```

---

# 35. Technologie — vorläufig

## Android

* Kotlin
* Jetpack Compose
* Android SDK
* Android Keystore
* Jetpack Security / geeignete etablierte Kryptografie-Bibliotheken
* SQLite-basierter lokaler Speicher

Die konkreten Bibliotheken werden erst nach einer Sicherheits- und Lizenzprüfung festgelegt.

## Entwicklungsumgebung

* Linux
* Android Studio
* JDK
* Android SDK
* Git

## Repository

Zunächst:

* privates Git-Repository
* keine Echtdaten
* keine Produktionsschlüssel
* keine personenbezogenen Testdaten

---

# 36. Entwicklungsphasen

## Phase 0 — Spezifikation

Ergebnisse:

* PRODUCT.md
* Threat Model
* Data Model
* Security Requirements

## Phase 1 — Technischer Prototyp

Ziel:

> Kann Android eine verschlüsselte lokale Datenhaltung zuverlässig realisieren?

Noch keine vollständige Anwendung.

## Phase 2 — Security Foundation

Implementierung:

* Schlüsselverwaltung
* Authentifizierung
* verschlüsselte Datenbank
* verschlüsselter Dateispeicher
* Backup-Ausschluss
* sichere Logs

## Phase 3 — MVP

Implementierung:

* Aktionen
* Personen
* Rollen
* Aufgaben
* Material
* Dokumente
* Kosten
* Retention

## Phase 4 — Security Testing

* statische Analyse
* Dependency Audit
* dynamische Tests
* Datenlecktests
* Gerätesicherheit
* Crash Tests

## Phase 5 — Pilot

Test mit synthetischen Daten in einer realistischen Aktion.

## Phase 6 — unabhängiges Security Review

Vor Verwendung mit echten sensiblen Daten.

---

# 37. Definition of Done für MVP

Der MVP gilt erst dann als fertig, wenn:

* [ ] vollständig offline funktionsfähig
* [ ] keine zwingende Netzwerkverbindung
* [ ] keine Analytics
* [ ] keine Werbung
* [ ] keine Cloud-Abhängigkeit
* [ ] sensible Daten verschlüsselt
* [ ] Schlüssel nicht im Quellcode
* [ ] Backup-Verhalten geprüft
* [ ] sensible Logs ausgeschlossen
* [ ] Retention implementiert
* [ ] Löschung getestet
* [ ] Datenmodell dokumentiert
* [ ] Threat Model dokumentiert
* [ ] Unit Tests vorhanden
* [ ] Security Tests vorhanden
* [ ] reale Android-Geräte getestet
* [ ] keine echten personenbezogenen Testdaten im Repository
* [ ] Build reproduzierbar bzw. nachvollziehbar dokumentiert
* [ ] Security Review durchgeführt
* [ ] Datenschutzkonzept fachlich geprüft

---

# 38. Perspektive nach MVP

Mögliche spätere Funktionen:

### v0.2

* verbesserte Rollenverwaltung
* Checklisten
* Schichten
* Kalenderansicht
* erweiterte Retention Policies
* verschlüsselte Exporte

### v0.3

* mehrere lokale Benutzer
* Geräteschlüssel
* sichere Geräteübertragung
* Offline-Datenexport/-import

### v0.4

* optionaler verschlüsselter Server
* Geräte-/Benutzersynchronisation
* Ende-zu-Ende-Verschlüsselung

### v1.x

* Multi-Tenant
* Webanwendung
* föderierte Organisationen
* gemeinsame Kampagnen
* organisationsübergreifende Aktionen

Diese Funktionen dürfen die Sicherheitsarchitektur des MVP nicht unnötig verkomplizieren.

---

# 39. Leitprinzip

Das wichtigste Prinzip des Projekts lautet:

> **Nicht möglichst viele Daten sicher speichern, sondern möglichst wenige Daten benötigen.**

Die beste Verschlüsselung ist eine sinnvolle Ergänzung zu Data Minimization, aber kein Ersatz dafür.

---

# 40. Status

Dieses Dokument beschreibt die Produktidee und den MVP auf konzeptioneller Ebene.

Es ist **noch keine technische Implementierungsspezifikation**.

Vor Beginn der Implementierung müssen insbesondere folgende Dokumente erstellt werden:

1. `threat-model.md`
2. `data-model.md`
3. `architecture.md`
4. `security.md`
5. `privacy.md`
6. `data-retention.md`

Erst danach wird die konkrete Android-Architektur festgelegt.

## P4 — freigegebener Produktumfang

P4 konkretisiert FR-013 und FR-020 bis FR-022: Rollenplanung und optionale
Hauptfunktion ausschließlich in PREPARATION; aktuelle Funktion, acht feste
Lagewerte und kurzer manueller Ort/Ziel während ACTIVE. Planung bleibt ab ACTIVE
unverändert, während operative Snapshots aktualisierbar sind. Kein Verlauf, GPS
oder automatisches Tracking. Vorlagen sind Auswahlhilfen, keine Pflichtrollen.
Normale gültige PaG-Session genügt für diesen ausdrücklich freigegebenen INTERNAL-
Zugriff; sensible Identitäts-/Kontaktbereiche bleiben unter P3b-Step-up.
Details, Grenzen und Tests: [product-p4.md](docs/product-p4.md).
