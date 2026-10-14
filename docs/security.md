# Sicherheitskonzept des MVP

## 1. Status und Zuständigkeit

Grundlagen: [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), [data-model.md](data-model.md) und die verbindlichen Sicherheitsentscheidungen. Anforderungen sind prüfbares Sollverhalten, keine bereits erreichte Garantie. Es gibt noch keine Android-Implementierung.

[threat-model.md](threat-model.md) definiert Angreifer und Grenzen, [privacy.md](privacy.md) Datenzwecke/Klassifizierung, [data-retention.md](data-retention.md) Fristen und Wiederanlauf. Dieses Dokument legt keine Kryptografiealgorithmen, Bibliotheken, Schlüsselverpackung oder -ableitung fest. Keine eigene Kryptografie und keine eigenen kryptografischen Primitive.

## 2. Verbindliche Sicherheitsanforderungen

### SEC-01 — Sitzung und App-Sperre

Die App startet immer gesperrt. Sie sperrt sofort bei Gerätesperre und beim Verlassen der App. Eine entsperrte Sitzung wird nicht persistent wiederhergestellt, auch nicht über gespeicherten UI-Zustand, Prozesswiederherstellung oder Rückkehr aus einem Systempicker.

Die Sperre schützt fachliche Datenzugriffe unterhalb der UI. Deep Links, interne Schnittstellen und Hintergrundabläufe dürfen sie nicht umgehen. Bei Sperrung werden Inhaltsanzeige und neue unzulässige Zugriffe beendet; nicht mehr benötigte Klartextreferenzen werden freigegeben. Technische Behandlung laufender Operationen und genaue Lifecycle-Ereignisse sind im Prototyp nachzuweisen.

Zwei unabhängige Grenzen gelten gemäß ARCH-31/32: Die unveränderliche absolute Deadline beginnt unmittelbar nach erfolgreicher expliziter PaG-Authentifizierung und läuft während MAINTENANCE und READY. UI-Aktivität, Navigation, Scrollen, Tippen, I/O oder Wartung verlängern sie nie. Die Inaktivitätsgrenze gilt zusätzlich in READY; nur qualifizierte tatsächliche Vordergrundinteraktion aktualisiert den Aktivitätszeitpunkt. Renderzyklen, Timer, I/O, Flows, Animationen, Lifecycle-Callbacks, Hintergrundarbeit und Maintenance sind keine Nutzeraktivität. In MAINTENANCE läuft keine Inaktivitätsgrenze allein wegen fehlender Interaktionsmöglichkeit; es bleibt absolute Deadline ohne Fachinhaltsanzeige.

Ablauf einer anwendbaren Grenze führt zu LOCKED und invalidiert die Generation. Eine bereits abgelaufene Sitzung darf auch durch nachträgliche Interaktion nicht wiederbelebt werden. Neue Freigabe nur nach einem neuen explizit gestarteten DEVICE_CREDENTIAL-Flow und dem Gate; Geräteentsperrung und verbleibende Keystore-Autorisierung reichen nicht aus. Kein automatischer Prompt-Loop. Für P1 gelten 3 Minuten absolute Dauer ab erfolgreicher Authentifizierung und 30 Sekunden READY-Inaktivität. Spätere Anpassbarkeit ist vorgesehen; Änderungen benötigen eine bewusste Produkt-/Securityentscheidung.

Injizierbare monotone Zeit muss Sleep einschließen; elapsedRealtime() ist der Android-Kandidat. Keine Wanduhr für Sessiondauer. Timer dienen nur zeitnaher UI-Reaktion; die Deadline entscheidet unabhängig davon über Zugriff. Resume prüft vor Anzeige; Activity Recreation verwirft die Sitzung konservativ, Process Death startet LOCKED. Callbacks prüfen Generation und Zeitgültigkeit. Storage verwendet keinen parallelen Unlock-State und prüft zentralen Zustand, Generation, Lifecycle/Foreground, absolute und in READY zusätzliche Inaktivitätsdeadline vor Operation, relevantem Arbeitsschritt, Commit-Zulassung und Veröffentlichung (ARCH-33/34).

Laufende native Arbeit kann physisch noch enden; nach Ablauf keine neue Operation oder Ergebnisveröffentlichung. Alte DB-/Stream-Handles bleiben trotz existierendem nativen Objekt fachlich unzulässig. Abbruch, Schließung und Wiederanlauf sind praktisch nachzuweisen; sofortige Löschung aller JVM-/nativen Puffer wird nicht garantiert. Sessionablauf löst keine automatische Datenlöschung aus.

**Empfehlung:** Manuelle Sperrmöglichkeit und neutrale gesperrte Oberfläche. Nach Dateiauswahl erneute Authentifizierung verlangen; Import erst bei zulässiger Sitzung fortsetzen. Keine Picker-Ausnahme von der sofortigen Sperre.

### SEC-02 — Lokale Authentifizierung

DEVICE_CREDENTIAL + Keystore-Bindung ist der bevorzugte Security-Prototyp gemäß ARCH-11. Biometrie bleibt im MVP ausgeschlossen; im ersten Prototyp keine eigene PaG-Passphrase. Eine Person mit Kenntnis des Gerätecredentials kann voraussichtlich auch die PaG-Authentifizierung erfüllen; dieses Restrisiko ist akzeptiert. Die konkrete Keystore-Bindung und Nutzungsbedingungen bleiben offen.

Authentifizierungsdaten und Datenverschlüsselung bleiben sauber getrennt. Keine Klartext-Credentials, keine Secrets in Repository, Logs oder persistentem UI-Zustand. Ein Zurücksetzen darf keinen ungeschützten Datenzugriff ermöglichen. Im MVP gibt es keinen Recovery-Mechanismus. Gerätecredential-Wechsel, Invalidierung und Schlüsselverlust sind technisch noch zu spezifizieren; endgültiger Datenverlust ist akzeptiert.

Eine spätere unabhängige PaG-Passphrase setzt eine eigene Nutzen-/Risikobewertung voraus. Eine appseitige Geheimnisprüfung wäre nicht automatisch hardwareseitige Keystore-Authentifizierung. Freigabeentscheidung und tatsächliche Schlüsselverwendung müssen zusammen geprüft werden, ohne eine selbst entworfene kryptografische Konstruktion einzuführen.

### SEC-03 — Wiederholte Entsperrversuche

Falsche Gerätecredential-Eingaben behandelt der Plattformmechanismus. PaG zählt keine falschen PIN-/Passworteingaben innerhalb des Android-DEVICE_CREDENTIAL-Systemdialogs und definiert dafür keine eigene Wartezeitstaffel. Keine automatische Datenlöschung aufgrund falscher Eingaben.

PaG verhindert parallele Authentifizierungsflüsse, automatische Prompt-Schleifen, verspätete Ergebnisse alter Versuche und Wiederverwendung alter entsperrter App-Sitzungen. Session-Generation und Gate folgen ARCH-08 bis ARCH-13. Falls später eine eigene PaG-Passphrase eingeführt wird, benötigt sie eine separat spezifizierte Retry-/Wartezeitstrategie einschließlich Neustart-, Zeit- und Offline-Angriffsbetrachtung.

### SEC-04 — Schlüsselverwaltung und Prototyp

Android Keystore wird für geeigneten Schlüsselschutz genutzt. Konkrete Schlüsselhierarchie und Nutzungsbedingungen werden erst nach Security-Prototyp beschlossen. Mindest-Android-Version und erforderliche Hardwarefähigkeiten bleiben offen. Ein schwächerer Fallback darf nicht stillschweigend erfolgen.

Verantwortlichkeiten für Datenbank- und Dateischlüssel werden funktional getrennt; Android Keystore bildet den Root of Trust für geeignetes gerätegebundenes Schlüsselmaterial. **Prüfauftrag:** Individuelle Dateischlüssel auf Nutzen für Löschung prüfen. Das ist ein Untersuchungsauftrag, keine festgelegte Verpackung, Ableitung oder Formatdefinition.

Hardwarebindung muss tatsächlich geprüft werden. Nicht exportierbare Keystore-Schlüssel schützen nicht generell vor missbräuchlichen Operationen auf kompromittiertem System. Ein nach Entsperrung im App-Prozess verfügbarer Datenbankschlüssel ist nicht automatisch dauerhaft hardwareisoliert. Siehe [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

Gerätecredential-Wechsel, Invalidierung und Verlust benötigen definierte Abläufe. Ein eigener PIN-/Passphrase-Wechsel wäre nur bei einer später ausdrücklich eingeführten PaG-Passphrase zu spezifizieren. Kein automatisches Ersetzen verlorener Schlüssel mit anschließender Behauptung erfolgreicher Datenwiederherstellung.

### SEC-05 — Strukturierte Speicherung

Alle operativen Daten werden unabhängig von PUBLIC_CONTENT, INTERNAL oder SENSITIVE verschlüsselt gespeichert. Erforderlicher Schutz umfasst Metadaten, Suchindizes und fachliche Einstellungen sowie Journale, temporäre Datenbankdateien und Migrationspfade. Keine ungeschützte Kopie über gespeicherten UI-Zustand.

Mehrteilige strukturierte Änderungen erfolgen konsistent und transaktional. Beschädigung beziehungsweise Integritätsfehler müssen entsprechend den Fähigkeiten der später geprüften Lösung erkannt und behandelt werden. Es wird keine vollständige Erkennung jeder Manipulation oder jedes Rollbacks behauptet.

### SEC-06 — Dateien

Importierte Inhalte werden als private appverwaltete verschlüsselte Kopien gespeichert; Eigentümer und Kardinalitäten entsprechen data-model.md. Externe Originale bleiben außerhalb der Kontrolle. Verfahren müssen Vertraulichkeit und geeignete Integritätsprüfung leisten; Auswahl bleibt offen.

Große Dateien dürfen nicht unnötig vollständig in Arbeitsspeicher geladen werden. Vorschaubilder und Dateimetadaten erhalten den Schutz ihrer Ausgangsdaten. Keine aktionsübergreifende Deduplizierung.

### SEC-07 — Temporärer Klartext

Keine dauerhaften entschlüsselten Dateien und keine Klartextkopien in öffentlichem oder gemeinsam genutztem Speicher. Klartext wird nur soweit und solange für Verarbeitung erforderlich gehalten.

Anzeige aus kontrollierten Streams/Arbeitsspeicher bevorzugen. Keine private Klartext-Renderausnahme ist freigegeben. Erfordert ein Format unerwünschte Klartextdateien, Disk-Caches oder unverhältnismäßige Komplexität, ist keine Vorschau im MVP zulässig; keine externe Klartextöffnung als Ersatz. Siehe ARCH-28.

Freigeben von Referenzen ist keine Garantie vollständiger Speicherüberschreibung. Laufzeit, Renderer und Betriebssystem können zusätzliche Kopien halten. Auch temporäre Dateilöschung garantiert keine physische Flash-Bereinigung.

### SEC-08 — Logs und Fehlerausgaben

Keine Namen, Telefonnummern, Inhalte, PINs/Passphrases, Schlüssel, sensiblen Dateinamen oder Quell-URIs in Logs, Fehlermeldungen und Debug-Ausgaben. Dependency-Ausgaben sind einzubeziehen. Zulässig sind notwendige unkritische technische Fehlerkategorien; kein externes Crash Reporting.

### SEC-09 — Zwischenablage und Vorschau

Sensible Inhalte nicht ohne ausdrückliche Benutzeraktion kopieren. Eine Weitergabe an die Zwischenablage verlässt die kontrollierte Sitzung; bereits gelesene Kopien lassen sich nicht verlässlich zurückholen.

Sensible Oberflächen erhalten verbindlich Plattformfensterschutz gegen Screenshots und Recent-App-Vorschauen sowie neutrale gesperrte Ansichten. Konkrete Umsetzung und Lifecycle-Nachweise bleiben Prototypaufgaben. FLAG_SECURE unterstützt den Schutz, ersetzt aber keine vollständige Absicherung gegen Overlay-Angriffe oder externe Fotografie. Accessibility soll gemäß PRODUCT.md sinnvoll nutzbar bleiben. Siehe [Android-Fensterschutz](https://developer.android.com/security/fraud-prevention/activities).

### SEC-10 — Berechtigungen und Netzwerk

Im MVP keine INTERNET-Berechtigung und keine Kontakte-, Telefon-, Standort- oder breite Speicherberechtigung. Keine Telemetrie, Cloud, versteckte Synchronisation oder Netzwerkfunktion durch Dependencies. Zusammengeführtes Manifest und Laufzeitverhalten sind zu prüfen, nicht nur die eigene Manifestquelle.

System-Dateiimport erfolgt durch ausdrückliche Auswahl über Android-Systemmechanismen. Zusätzlich ist CameraX-In-Memory über OnImageCapturedCallback als Kamera-Prototyp bevorzugt. CAMERA ist die einzige zusätzliche fachliche Runtime-Berechtigung und ausschließlich für den explizit gestarteten Kamera-Workflow in READY zulässig; ohne Workflow keine Kamerainitialisierung, keine Hintergrundaufnahme. Kein automatischer Fallback auf externe Kamera-Apps. Der appseitige Pfad muss ohne persistente Klartext-Zwischendatei und ohne Gate-Umgehung nachgewiesen werden; scheitert der Nachweis, wird Kameraimport verschoben. Details und begrenzte Bildpipeline: ARCH-20 bis ARCH-22. Dokumentanbieter können eigene externe Dienste nutzen; deren Verhalten wird nicht als lokales App-Verhalten ausgegeben. Keine Netzwerkberechtigung zur vermeintlich bequemen Verarbeitung ergänzen.

### SEC-11 — Backup, Transfer und Recovery

Operative Daten, Authentifizierungsdaten, Schlüsselmaterial und dessen gespeicherte Verpackungen/Referenzen, Retention-/Import-Arbeitsdaten und temporäre sensible Inhalte werden von Cloud-Backup und Gerätetransfer ausgeschlossen. Das gilt auch für verschlüsselte Kopien.

Keystore-Material und appseitige Schlüsselartefakte sind getrennt zu erfassen; Schutz wird nicht allein aus einer Manifestoption abgeleitet. Ausschlüsse müssen alle genutzten Speicherbereiche und unterstützten Plattformversionen abdecken. allowBackup=false allein schließt bei manchen Herstellern Gerätetransfer nicht aus. Siehe [Android-Backupregeln](https://developer.android.com/identity/data/autobackup).

Im MVP kein Backup, Recovery-Key, Export operativer Daten oder Recovery-Mechanismus. Geräteverlust, Schlüsselverlust oder bestimmte nicht sicher reparierbare Fehler können endgültigen Datenverlust bedeuten. Das erlaubt keine automatische Löschung oder Neuinitialisierung bei Fehlern; siehe SEC-15.

Nachweise benötigen reale Backup-/Restore- und Transferprüfungen auf der später festgelegten Gerätematrix. Keine Garantie gegen Kopien eines privilegierten, kompromittierten Systems.

### SEC-12 — Sichere Importe

Quellinhalt, Dateiname, MIME-Angabe und gemeldete Größe sind nicht vertrauenswürdig. Tatsächlich gelesene Größe begrenzen, Inhaltstyp entsprechend dem Format prüfen, keine Zielpfade aus frei gelieferten Namen ableiten. Aktive Inhalte und externe Verweise dürfen nicht automatisch ausgeführt werden.

Formatliste, Größenlimits, Parser/Renderer und Inhaltsprüfungen bleiben offen. Unterbrochene Imports dürfen nicht als fertige Dokumente erscheinen; Rückstände werden kontrolliert bereinigt. Nach erfolgreichem Systemimport keine dauerhafte URI-Berechtigung behalten; keine automatische Wiederaufnahme über persistierte Quell-URIs. Nach Rückkehr aus dem Picker gilt SEC-01. Kamera- und System-Dateiimport verwenden denselben kontrollierten Import-Anwendungsfall gemäß ARCH-17 bis ARCH-19; Adapter kennen keine Schlüssel und erzeugen keine fertigen FileAssets. Bei Sperrung Kamera/Workflow invalidieren, verspätete Ergebnisse verwerfen und ImageProxy korrekt schließen.

### SEC-13 — Dateiöffnung und Weitergabe

Im MVP keine externe Klartextöffnung und keine automatische Übergabe sensibler Dateien an externe Viewer. Intern unterstützte Formate, Renderer und Größenlimits werden erst im Viewer-/Storage-Prototyp festgelegt. Falls sichere interne Darstellung ohne unerwünschte Klartextdateien, Disk-Caches oder unverhältnismäßige Komplexität nicht möglich ist, ist keine Vorschau zulässig. Kein universeller eigener Dokumentrenderer; siehe ARCH-28.

### SEC-14 — Abhängigkeiten

Neue zentrale/sicherheitskritische Dependencies benötigen Prüfung von Nutzen, Wartung, Herkunft, Lizenz, Sicherheitsproblemen, transitiven Abhängigkeiten, Berechtigungen und Laufzeitnetzwerk. Keine Bibliotheksauswahl in diesem Dokument. Keine eigene Kryptografie als Ersatz.

**Empfehlung:** Feste nachvollziehbare Versionen und dokumentierter Update-/Auditprozess. Eine Dependency im App-Prozess darf nicht als isolierte Vertrauenszone behandelt werden.

### SEC-15 — Fehler und Retention

Kein Klartext-Fallback bei Authentifizierungs-, Schlüssel- oder Integritätsfehler. Keine automatische Löschung oder Neuinitialisierung vorhandener Daten bei Fehlern. Fehlgeschlagene Entschlüsselung ist kein ausreichender Korruptionsbeweis. Bei unklarem Schlüssel-, Schema-, Integritäts- oder Datenzustand Bestand unangetastet lassen; keine automatische Dateibereinigung aufgrund eines gescheiterten DB-Opens. Ein endgültiger lokaler Reset wäre eine separate ausdrücklich bestätigte Benutzeraktion ohne Recovery. Betroffene Zugriffe sperren und eine verständliche Meldung ohne sensitive Details anzeigen.

Löschfehler werden nicht als Erfolg gemeldet. Wiederanlauf und fällige Inhalte richten sich nach RET-03 bis RET-07. Die App-Sperre wird nicht für Hintergrundbereinigung geschwächt. Minimale technische Löschdaten sind keine dauerhafte detaillierte Historie.

### SEC-16 — Keine Notifications oder operativen Außenoberflächen

Im MVP keine lokalen PaG-Notifications, Push Notifications, Push-Infrastruktur, Benachrichtigungsberechtigung oder Foreground Services. Keine eigenen Notification Channels ohne später nachgewiesene technische Notwendigkeit; diese wäre keine Freigabe von Notifications. Keine operativen Widgets, operativen dynamischen/persistenten Shortcuts oder sensiblen Lockscreen-Inhalte. Retention-/Ablaufwarnungen ausschließlich innerhalb einer zulässigen entsperrten PaG-Sitzung. Manifest, Dependency-Komponenten und Laufzeitverhalten sind entsprechend zu prüfen.

### SEC-17 — Auffindbarkeit und forensische Grenzen

Die installierte App gilt als auffindbar. Kein versteckter Launcher, geheime Start-/Dialer-Codes, Tarnung als andere App oder versteckte Komponenten zur Umgehung normaler App-Erkennung. Zurückhaltendes Branding ist zulässig, kein Sicherheitsmechanismus. Ressourcenstarke staatliche/forensische Akteure und Zustandswechsel bis zur OS-/Kernel-Kompromittierung gehören zum threat-model.md; das bedeutet keine Schutzgarantie gegen ein vollständig kompromittiertes laufendes OS.

Verschlüsselung ruhender Daten schützt keine bereits in READY verfügbaren Klartexte. Freigabezeit, Schlüssel-/Cache-Lebensdauer und Nebenbestände werden minimiert und geprüft. Fremde IME/Tastaturen können Eingaben verarbeiten; Clipboard, Fotografien, Logs, Backupreste und privilegierte Speicherkopien lassen sich nicht zuverlässig zurückholen. Keine vollständige RAM- oder physische Flash-Löschgarantie.

## 3. Offene Entscheidungen

| ID | Entscheidung | Empfehlung/Prüfauftrag | Zeitpunkt |
|---|---|---|---|
| SEC-OD-01 | Mindest-Android-Version und Gerätematrix | Reale Funktionen und Sicherheitsverhalten prüfen | Vor Security Foundation |
| SEC-OD-02 | Hardware-Keystore-Anforderungen und Fallback | Fähigkeiten feststellen; keinen stillen schwächeren Modus | Security-Prototyp, vor Foundation |
| SEC-OD-03 | Konkrete DEVICE_CREDENTIAL-/Keystore-Bindung, Autorisierungsdauer beziehungsweise Operationsbindung | Frische Authentifizierung und zulässige Schlüsselverwendung gemeinsam nachweisen; keine Biometrie oder eigene PaG-Passphrase im ersten Prototyp | Security-Prototyp |
| SEC-OD-04 | Gerätecredential-Wechsel, Schlüsselverlust/Invalidierung und Fehlerabläufe | Verlustfolgen nachweisen; keine Recovery und keine automatische Löschung/Neuinitialisierung | Vor Foundation |
| SEC-OD-05 | Algorithmen, Bibliotheken, Verpackung, Ableitung und Schlüsselhierarchie | Etablierte Lösungen prüfen; keine eigene Konstruktion | Nach Prototyp, vor Foundation |
| SEC-OD-06 | Lifecycle, laufende Operationen und Picker-Rückkehr | Sofortige Sperre mit Wiederanlauf und erneuter Authentifizierung nachweisen | Prototyp/vor Import |
| SEC-OD-07 | Formatumfang, Größen, Parser und temporäre Klartextanforderungen | Begrenzen; keine stillen Klartextausnahmen | Vor Import/Anzeige |
| SEC-OD-08 | Konkreter Screenshot-/Recent-Apps-Schutz und Clipboard-UI | Verbindlichen Oberflächenschutz nachweisen; Clipboard nur nach ausdrücklicher Benutzeraktion; keine externe Dateiöffnung | Vor jeweiliger UI-Funktion |
| SEC-OD-09 | Konkrete Backupregeln, Speicherorte und Testmatrix | Ausschlüsse praktisch prüfen | Vor Speicherung/Freigabe |
| SEC-OD-10 — P1 entschieden | 3 Minuten absolut / 30 Sekunden READY-Inaktivität | elapsedRealtime; nur echte Benutzerinteraktion erneuert Inaktivität; App-Verlassen sperrt sofort | Produktwerte freigegeben; spätere Anpassbarkeit separat |

Retention-Fristen und deren Kalender-/Zeitrechnung sind in data-retention.md offen geführt; Sessiondauer und monotone Sessionzeit sind davon getrennt. Klassifizierungsänderungen und indirekte Personenbezüge stehen in privacy.md. Verbindlich geklärte Punkte werden nicht erneut zur Disposition gestellt.

## 4. Ableitbare Testanforderungen

SEC-01 bis SEC-17 bilden Referenzen für das spätere testing.md. Erforderlich sind insbesondere Start-/Lifecycle-/Picker-Tests, Authentifizierung nach Prozesswiederherstellung, Verhinderung paralleler Authentifizierungsflüsse, Prompt-Schleifen, alter Authentifizierungsergebnisse und wiederverwendeter Sitzungen, Zugriffsversuche unterhalb der UI, Klartextprüfung aller kontrollierten Speicherbereiche, Logs und Vorschauen, Manifestprüfung, Backup/Transfer, bösartige/überlange Imports, Kamera-Gate/ImageProxy-Lifecycle, fehlende persistente Klartext-Zwischendateien, fehlender externer Fallback sowie Ausfälle bei Schlüsselzugriff und Migration.

SEC-01, SEC-16 und SEC-17 ergänzen Tests für absolute Grenze trotz kontinuierlicher Bedienung, Inaktivität ausschließlich in READY, Maintenance ohne künstliche Aktivität, Sleep/Resume, Wanduhränderung, Recreation/Process Death, verzögerte Timer, alte Callbacks und Ablauf zwischen Prüfung und Commit-Zulassung. SQL-Zugriffe, Streams, Import/Delete und Wartung müssen denselben Vertrag durchsetzen; keine alten Handle-Zugriffe oder Ergebnisemissionen. Fehlende Notifications, Foreground Services, operative Widgets/Shortcuts und Verbergungsmechanismen statisch und dynamisch prüfen. Die B0/B1–B5-Reihenfolge und Nachweise stehen in ARCH-35.

Tests nutzen ausschließlich synthetische Daten. Hardware-, Hersteller- und Flash-Grenzen lassen sich nicht vollständig durch Unit Tests belegen. Prüfergebnisse müssen Gerät, Plattform, Konfiguration und verbleibende Grenzen nennen. Vor echten sensiblen Daten sind unabhängiges Security Review und fachkundige Datenschutzprüfung erforderlich.


## P1 – Session-resident product DB key

Freigegebene Produktentscheidung ausschließlich für den DB-only-Slice:
Nach einer frischen expliziten per-operation DEVICE_CREDENTIAL-Authentifizierung
wird der produktive DB-Key einmal per RSA-OAEP entpackt. Ein instanzlokaler
SessionDbKeyOwner hält eine kontrollierte Kopie ausschließlich für dieselbe
PaG-Sessiongeneration im RAM. Dies ist kein Keystore-Autorisierungsfenster.

Der zentrale Storage-Vertrag bleibt autoritativ: READY bzw. ausdrücklich
zugelassenes MAINTENANCE, identische Generation, zulässiger Vordergrund,
absolute Deadline und in READY zusätzlich Inaktivität. Key-Präsenz ist kein
Zugriffsrecht. Kein Key-Getter, persistentes Login, globale Registry, Key in
SavedState/Preferences/DataStore oder Key-Fingerprint. Consumer erhalten eine
kurze kontrollierte Kopie, die nach Verbrauch bestmöglich überschrieben wird.

LOCKED, BLOCKED, Generationwechsel, Ablauf, App-Verlassen, Fehler in Auth oder
Maintenance und explizites Session-Ende invalidieren den Owner. Ein invalidierter
Owner kann nicht reaktiviert werden; die nächste Sitzung benötigt erneut
Authentifizierung, Unwrap und einen neuen Owner. Room bleibt pro Operation kurz
geöffnet und wird vor Result-Publication geschlossen. Bereits laufende native
Arbeit kann enden; keine neue Zulassung oder fachliche Veröffentlichung nach
Widerruf. Interne JVM/JCA/SQLCipher-Kopien und physische RAM-Löschung sind nicht
garantiert. Kein File-Key- oder Multi-Key-Vertrag wird dadurch entschieden.

UX1a zeigt ausschließlich eine read-only Restzeitprojektion dieser bestehenden
Deadline mit derselben monotonen Uhr. Anzeige-Ticks erzeugen keine Interaktion,
keine neue Deadline und keine Storagefreigabe; siehe [ux1a-shell.md](ux1a-shell.md).
Der bestehende Sessionvertrag bleibt unverändert.

UX1-S ergänzt ausschließlich den expliziten Control „Sitzung neu starten“ bei
gültigem READY und positiver absoluter Restzeit bis einschließlich 30000 ms.
Vor Systemauthentisierung wird die alte Session über den normalen Lock-Pfad
widerrufen und bereinigt. Anschließend gilt unverändert LOCKED → AUTHENTICATING
→ MAINTENANCE → READY mit frischem DEVICE_CREDENTIAL und neuem Zeitanker.
Der Tap erzeugt keine neue Deadline oder READY-Publication. Cancel bleibt LOCKED;
Auth-/Maintenancefehler bleiben gemäß normalem Vertrag fail closed, gegebenenfalls
BLOCKED. Keine Reaktivierung alter Generation, Continuation oder Editorrestoration;
P3b-Grants werden widerrufen und nicht durch Geräteauthentisierung erneuert.
Keine neue READY-Foreground-Ausnahme, kein Storage-Bypass, keine Persistenz.
Die ursprüngliche weiterlaufende Re-Auth-Idee ist verworfen. Details und Nachweise:
[ux1s-session-restart.md](ux1s-session-restart.md).

Produktive Sessionparameter: 180000 ms absolut, ab dem tatsächlichen erfolgreichen
Auth-Callback; 30000 ms Inaktivität ausschließlich in READY. MAINTENANCE zählt
gegen absolut und erhält keine künstliche Aktivität. Timer prüfen nur nach;
elapsedRealtime ist maßgeblich, nicht Wanduhr. Werte sind derzeit bewusst fest,
spätere Anpassbarkeit ist noch kein implementiertes Einstellungsfeature.


## P5a – operative Aufgaben und minimale Task-Chronik

Der verbindliche implementierte Vertrag steht in [product-p5a.md](product-p5a.md).
Task und TaskEvent sind ausdrücklich INTERNAL für schnelle operative Koordination
in einer gültigen READY-Sitzung, trotz potentiell sensibler kurzer Titel. Bestehende
SQLCipher-DB, Session- und P3b-Verträge bleiben erhalten; kein Leadership-Step-up
für normale Taskarbeit, keine neuen Permissions, Dependencies oder Netzwerkzugriffe.

Schema 6 ergänzt tasks und task_events additiv. Zuständigkeit Person/Rolle/niemand
und optionale betroffene Person sind getrennt. Zusammengesetzte Foreign Keys
verhindern Cross-Action-Referenzen; Insert-/Update-Trigger sichern exklusive Zuständigkeit; das Repository prüft zusätzlich
persistierten Aktionsstatus und erwarteten Snapshot. Lifecycle + Event sind atomar.
TaskEvents sind ausschließlich CREATED/STARTED/COMPLETED/CANCELLED/REOPENED,
append-only im Repository. Keine Edit-Historie, allgemeine Audit- oder Personen-
Lagehistorie. PREPARATION erlaubt vollständiges Task+Event-Löschen, ACTIVE nicht,
COMPLETED ist read-only. Rollenreferenzen verhindern stilles Rollenlöschen.

Daten verbleiben aktionsgebunden, mit späterer Aktionslöschung zu entfernen,
keine permanente Archivierung, Exporte oder Secure-Overwrite-Zusage. Kurze Titel
ohne Notizfeld, Pseudonyme in der UI; keine Übernahme sensibler P3b-Payloads.
Statische Vorlagen erzeugen erst nach Auswahl und Speichern normale Tasks;
sourceTemplateKey ist nicht unique. P5b-Vorschlagsregeln sind nicht implementiert.

### UX1-S: Deadlock und finale Grant-Key-Zulassung

Der ursprüngliche Zyklus war Coordinator-Monitor -> synchroner Cleanup ->
Grant-Monitor gegen Grant-Monitor -> Contextcheck -> Coordinator-Monitor.
Der erste Fix zog den Contextcheck aus dem Grant-Monitor. Der Re-Audit wies
anschließend neue Key-Übergaben nach Generationinvalidierung, bei bereits
wartendem Cancel und nach Deadline nach; der Reject erst nach Work war zu spät.

Der finale Pfad erweitert ausschließlich den bestehenden Context um eine kurze
`admitKey`-Operation. Sie besitzt zuerst den Coordinator-Monitor; die Grant-
Kopie besitzt danach den Grant-Monitor. Der scoped Authorizer liest den bereits
gehaltenen Coordinator, ohne einen Monitor zu erwerben oder Observer/Cleanup
aufzurufen. Er prüft READY/Generation, die bestehende Live-Umgebung und eine
gemeinsame monotone Zeitprobe gegen absolute Session-, READY-Inaktivitäts- und
Grantdeadline. Es gibt keinen lock-freien Schattenstate und keinen Two-Phase-
Cleanup. `check`/`publish` und bestehende Storage-Gates bleiben unverändert.

Die erfolgreiche finale Gate-Momentaufnahme innerhalb beider Monitore ist die
logisch atomare Zulassung: lokale Key-Präsenz bleibt unter Grant geschützt,
Sessionzustand unter Coordinator. Beim positiven Live-Environment-Snapshot
sind diese Zustände stabil; die anschließende monotone Zeitprobe unter allen
Deadlines bestätigt auch die Zeitgültigkeit an diesem Punkt. Bound-Context-
Requestchecks müssen ebenfalls erfolgreich sein. Erst dann wird kopiert.
Gibt der Guard false zurück, entsteht keine Lease. Nicht unterstützte Context-
Provider verweigern `admitKey` standardmäßig; es gibt keinen Check-/Publish-
Fallback mit schwächeren Garantien. Produktionsprovider/Grant verwenden dieselbe
elapsedRealtime-Epoche. Guard-Nutzung nach Scope-Ende oder auf anderen Threads
wird abgewiesen; er ist kein dauerhafter Autorisierungstoken.

60 Sekunden sind eine Zulassungsgrenze: keine neue Admission bei erreichten
60 Sekunden. Bereits vollständig zugelassene synchrone CPU-Arbeit darf enden;
Work läuft ohne Coordinator-/Grant-Monitor. `cancel` überschreibt die originale
Grantkopie, nicht aktive Consumerkopien. Diese werden bei Work-Ende/Exception
best-effort überschrieben. Ein später Reject verhindert die Rückgabe eines
stalen Ergebnisses und weitere Storagearbeit, autorisiert aber keine Work.
Der äußere Lease-finally deckt auch Adapterfehler nach der Kopie ab.

Session-Revoke linearisiert weiterhin beim Generation-/Statewechsel vor
synchronem Cleanup. Lokales Cancel linearisiert beim Wipe/null unter Grant;
Repository-Revoke invalidiert zunächst seinen atomaren Request. Die gemeinsame
Reihenfolge ist Coordinator -> Grant; es gibt im untersuchten Scope keine
Grant -> Coordinator-Monitoraufnahme. Lock/Background kehren erst nach Cleanup
zurück, der normale UX1-S-Authrequest erfolgt danach. Keine Deadlineverlängerung,
Generationverschiebung, READY-Ausnahme oder übersprungene Bereinigung.

Drei vor dem finalen Fix rote echte Coordinator-/Grant-Regressionsfälle und
weitere Ordering-/Deadline-/Wipe-Tests einschließlich 128 kontrollierter
Cancel-Wartereihenfolgen sichern die Grenze. Details, Grenzen und Prüfprotokolle:
[UX1-S-Zulassung](ux1s-session-restart.md#finale-key-zulassung-nach-dem-toctou-re-audit).


## P6a.1 — SENSITIVE-Protokoll, begrenzter operativer Zugriff

[Der P6-Vertrag](product-p6a-protocol.md) enthält entschiedene Product-/Privacy-
Anforderungen, noch keine technische Umsetzung/Freigabe. Alle Protokolldaten
sind **SENSITIVE**, einschließlich Freitext, Aussagen, Betroffenenbezügen,
Gesundheitsbeobachtungen, Behördenkennungen und früheren Fassungen.
Sensitivitätsklasse und UI-Zugriffsmodus sind getrennte Konzepte.

Ausdrücklicher enger Trade-off: normale P6-Arbeit in gültiger READY-Sitzung
benötigt keinen zusätzlichen P3b-/Leadership-Step-up, damit schnelle Dokumentation
und bewusste Nachbereitung möglich sind. Offene Sessions können den gesamten
Protokollbestand offenlegen; Verschlüsselung ruhender Daten verhindert dies nicht.
Keine Herabstufung auf INTERNAL und keine allgemeine Öffnung von SENSITIVE.
P3b-Identitäts-/Kontaktwerte bleiben separat geschützt, keine automatische
Übernahme oder Leadership-Freigabe. Detail-only Kennungen sind keine eigene
Securitygrenze, kein neues P3b-/Grant-/Key-Admission-Verfahren.

Bestehende verschlüsselte SQLCipher-Produktstorage, READY-/Generation-/Action-/
Status- und Storage-Gates vor Read, Arbeit, Commit-Zulassung und Publication bleiben
maßgeblich. P6 erlaubt in COMPLETED ausschließlich bewusste Nachbereitung mit
sichtbaren neuen Nachträgen und angehängten Revisionen; keine P4/P5a-Writes,
Aktionswiederöffnung oder normale Einzel-Löschung. DELETION_PENDING ohne Zugriff.

Unverändert: DEVICE_CREDENTIAL, 180 s absolut, 30 s READY-Inaktivität,
Session-Neustart, Foreground-/Device-Lock, StorageAccess, LeadershipGrant/
Key-Admission, DB-Key-Lifecycle, Crypto/Keystore, FLAG_SECURE und Backup-Ausschluss.
Keine neue Securityarchitektur, Dependency oder Permission. Ausgeschlossen:
INTERNET/Netzwerk, Cloud, Telemetrie, Notifications/Worker, Clipboard-Automatik,
externe Viewer/Export, Kamera/Fotos/Video/Audio/Dateianhänge, GPS/Tracking,
automatische P3b-Übernahme/P4-/P5a-Mutation oder externe Zeitstempel.

Append-only betrifft normale Produktänderungen, nicht erforderliche
Privacy-/Retentionlöschung. Aktionslöschung erfasst alle Fassungen, Relationen
und Kennungen, ohne permanente Restbestände oder Secure-Overwrite-Zusage.
Revisionshistorie und Gerätezeit sind nachvollziehbare Anwendungshistorie,
keine Hash-Chain/Signaturen/eigene Kryptografie, Beweissicherheit, Gerichtsfestigkeit,
forensische Unveränderlichkeit oder vertrauenswürdige Zeitquelle. Juristische
Prüfung vor Echtdaten bleibt erforderlich; dieser Slice ändert keinen Securitycode.


## P6b — geplanter Storage-/Publicationpfad

[P6b](product-p6b-protocol-implementation.md) verwendet ausschließlich vorhandene
READY-Contexts, Sessiongeneration, Foreground/Deadlines, kurze DB-Key-Leases und
Room-Lebensdauer samt STEP/COMMIT/Publication. Keine Änderung an Coordinator,
StorageAccess, LeadershipGrant/Key-Admission, Crypto oder Keystore. Vollständige
Revision+Children atomar; Revoke vor irreversibler Commitzulassung rollt zurück,
bereits zugelassene native Arbeit kann enden, stale Publication bleibt untersagt.

COMPLETED-Schreibfähigkeit ist nur P6-Policy, kein allgemeines Aktionsschreibrecht.
SENSITIVE bleibt ohne zusätzlichen Step-up gemäß P6a.1 zugänglich. Keine eigenen
Keys, Envelopes, neuen Permissions, Flows/Worker, Dateien, Exporte oder Netzwerke.
recordedAt aus Repository-Wanduhr, kein Nutzereingabefeld und kein vertrauenswürdiger
Zeitstempel. FK-/Sequenztrigger sind Anwendungsintegrität, keine kryptografische
Manipulationssicherheit. [Tests](testing.md) stehen noch aus; keine technische
Implementierung oder zusätzliche Securityarchitektur durch diesen Dokumentslice.


## P6c1 — bestehende Gates unverändert wiederverwendet

Der [DB-only-Adapter](product-p6c1-protocol-storage.md) erwirbt vorhandenen READY-
Context, prüft OPERATION/STEP/COMMIT, öffnet Room nur kurz mit bestehender DB-Key-
Lease und publiziert auf main ausschließlich durch context.publish. Revoke ist
kein fachlicher Protokollfehler; es beendet Publication. Native Commitnuance bleibt
unverändert. P6 meldet keine Sessioninteraction und verändert keine Deadline.

COMPLETED-Write-Ausnahme ist ausschließlich ProtocolRules; P4/P5a bleiben
unverändert read-only. Keine Änderungen an Coordinator, StorageAccess,
SessionDbKeyOwner-Semantik, LeadershipGrant, P3b-Crypto, Argon2/Tink, Keystore,
Auth, Manifest/Permissions oder Backup-/Transferregeln. Runtimeänderung ist nur
Portverdrahtung; ActionDatabaseänderung nur additiver Schema-/Migrationanschluss.
SENSITIVE trotz READY-Zugriff bleibt der explizite P6a.1-Trade-off. Keine zusätzliche
Kryptografie oder Beweis-/Timestamp-/Forensik-/Secure-Overwrite-Garantie.


## P7 — bestehende READY-Gates für Material

[P7](product-p7-material.md) verwendet dieselbe SQLCipher-DB, StorageAccess READY,
OPERATION/STEP/COMMIT/Publication, kurze DB-Key-Leases und Generationbindung wie
Tasks. INTERNAL mit normalem READY, kein Leadership-Step-up/P3b-Read. Keine
Änderung an Auth, Coordinator, Key-Owner, LeadershipGrant, Crypto/Keystore,
Argon2id/Tink, Permissions oder Backupregeln. Schema 7→8 rein additiv mit einer
Materialtabelle. Same-Action-/Detach-Trigger sind Relationsintegrität, keine neue
Securityarchitektur. Kein Netzwerk/INTERNET, Export, Datei-/Medienpfad, neue Dependency
oder sensitive Logausgabe. COMPLETED read-only; Materialwrites verlängern keine Session.
