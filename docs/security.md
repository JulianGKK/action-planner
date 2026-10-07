# Sicherheitskonzept des MVP

## 1. Status und Zuständigkeit

Grundlagen: [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), [data-model.md](data-model.md) und die verbindlichen Sicherheitsentscheidungen. Anforderungen sind prüfbares Sollverhalten, keine bereits erreichte Garantie. Es gibt noch keine Android-Implementierung.

[threat-model.md](threat-model.md) definiert Angreifer und Grenzen, [privacy.md](privacy.md) Datenzwecke/Klassifizierung, [data-retention.md](data-retention.md) Fristen und Wiederanlauf. Dieses Dokument legt keine Kryptografiealgorithmen, Bibliotheken, Schlüsselverpackung oder -ableitung fest. Keine eigene Kryptografie und keine eigenen kryptografischen Primitive.

## 2. Verbindliche Sicherheitsanforderungen

### SEC-01 — Sitzung und App-Sperre

Die App startet immer gesperrt. Sie sperrt sofort bei Gerätesperre und beim Verlassen der App. Eine entsperrte Sitzung wird nicht persistent wiederhergestellt, auch nicht über gespeicherten UI-Zustand, Prozesswiederherstellung oder Rückkehr aus einem Systempicker.

Die Sperre schützt fachliche Datenzugriffe unterhalb der UI. Deep Links, interne Schnittstellen und Hintergrundabläufe dürfen sie nicht umgehen. Bei Sperrung werden Inhaltsanzeige und neue unzulässige Zugriffe beendet; nicht mehr benötigte Klartextreferenzen werden freigegeben. Technische Behandlung laufender Operationen und genaue Lifecycle-Ereignisse sind im Prototyp nachzuweisen.

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

Große Dateien dürfen nicht unnötig vollständig in Arbeitsspeicher geladen werden. Vorschaubilder und Dateimetadaten erhalten den Schutz ihrer Ausgangsdaten. Keine actionübergreifende Deduplizierung.

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

Fristen und Zeitrechnung sind ausschließlich in data-retention.md offen geführt; Klassifizierungsänderungen und indirekte Personenbezüge in privacy.md. Verbindlich geklärte Punkte werden nicht erneut zur Disposition gestellt.

## 4. Ableitbare Testanforderungen

SEC-01 bis SEC-15 bilden Referenzen für das spätere testing.md. Erforderlich sind insbesondere Start-/Lifecycle-/Picker-Tests, Authentifizierung nach Prozesswiederherstellung, Verhinderung paralleler Authentifizierungsflüsse, Prompt-Schleifen, alter Authentifizierungsergebnisse und wiederverwendeter Sitzungen, Zugriffsversuche unterhalb der UI, Klartextprüfung aller kontrollierten Speicherbereiche, Logs und Vorschauen, Manifestprüfung, Backup/Transfer, bösartige/überlange Imports, Kamera-Gate/ImageProxy-Lifecycle, fehlende persistente Klartext-Zwischendateien, fehlender externer Fallback sowie Ausfälle bei Schlüsselzugriff und Migration.

Tests nutzen ausschließlich synthetische Daten. Hardware-, Hersteller- und Flash-Grenzen lassen sich nicht vollständig durch Unit Tests belegen. Prüfergebnisse müssen Gerät, Plattform, Konfiguration und verbleibende Grenzen nennen. Vor echten sensiblen Daten sind unabhängiges Security Review und fachkundige Datenschutzprüfung erforderlich.
