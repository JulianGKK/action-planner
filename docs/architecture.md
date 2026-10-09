# Technische Architektur des MVP

## 1. Status, Zweck und Verbindlichkeit

**Status:** Freigegebene Architekturentscheidungen; technische Ausgestaltung und Nachweise durch Prototypen stehen aus. Dieses Dokument beschreibt Sollverhalten und gibt keine bereits erreichten Sicherheitsgarantien ab.

Grundlagen sind [PRODUCT.md](../PRODUCT.md), [AGENTS.md](../AGENTS.md), [data-model.md](data-model.md), [threat-model.md](threat-model.md), [privacy.md](privacy.md), [data-retention.md](data-retention.md) und [security.md](security.md). Die anschließend ausdrücklich bestätigten Architekturentscheidungen konkretisieren diese Grundlagen. Fachliche Entitäten und Kaskaden werden hier nicht neu definiert.

Die stabilen IDs `ARCH-01` bis `ARCH-37` bezeichnen verbindliche Anforderungen und dienen als Referenzen für das spätere testing.md. Empfehlungen, Grenzen und offene Prototypentscheidungen sind gesondert gekennzeichnet. Die relationale MVP-Persistenz ist nach B2 als Room über kontrollierten SQLCipher-SQLiteDriver festgelegt (ARCH-36/37). Weitere Kryptografiebibliotheken, Algorithmen, Schlüsselableitungen, die endgültige Schlüsselhierarchie und Dateiformate bleiben offen.

## 2. Architekturprinzipien

**ARCH-01 — Plattform und Datenminimierung:** Native Android-App mit Kotlin, Jetpack Compose und Single Activity. Die Kernfunktionen arbeiten lokal ohne Internet. Datenminimierung und Zweckbindung nach privacy.md bleiben primäre Schutzmaßnahmen. Schutz sensibler Daten hat Vorrang vor Komfort und Wiederherstellbarkeit.

**ARCH-02 — Schichten und Zustandsfluss:** Fachlicher Aufrufweg ist UI → Domain → Data. Die UI verwendet Unidirectional Data Flow und ViewModels: Benutzeraktionen lösen Anwendungsfälle aus; freigegebene Ergebnisse erzeugen UI-Zustand. Composables und ViewModels sprechen keine Datenbank-, Dateispeicher-, Keystore- oder Kryptografieimplementierungen direkt an.

**ARCH-03 — Schutzgrenzen:** Gradle-Module kontrollieren Compile-Abhängigkeiten und unterstützen Tests; sie sind keine Laufzeit-Sandbox. Code und Dependencies im App-Prozess teilen dessen Schutzgrenze. Root-, Malware- oder Compromise-Erkennung wird nicht als tragende Sicherheitsgrenze verwendet. Die Grenzen gegenüber entsperrter Sitzung und kompromittiertem System stehen in threat-model.md.

## 3. Modulgraph und Verantwortlichkeiten

**ARCH-04 — Exakter Compile-Graph:** Es gibt genau vier Projektmodule. Nur folgende gerichtete Projektabhängigkeiten sind erlaubt:

```text
:app      → :domain, :data, :security
:data     → :domain, :security
:domain   → keine anderen Projektmodule
:security → keine anderen Projektmodule
```

Alle nicht aufgeführten Projektabhängigkeiten sind verboten. Insbesondere sind `:security → :domain`, `:security → :data`, `:data → :app` und sämtliche ausgehenden Projektabhängigkeiten von `:domain` verboten. Es gibt kein zusätzliches Vertragsmodul und keinen Zyklus.

| Modul | Verantwortung | Grenze |
|---|---|---|
| `:app` | Activity, Compose, ViewModels, Navigation, Android-UI-/Kamera-/Picker-Adapter und Composition Root | Konkrete Implementierungen nur zur Verdrahtung; keine operative Speichernutzung in Screens/ViewModels |
| `:domain` | Fachmodelle, Validierung, Anwendungsfälle, Repository- und abstrakte Zugriffsverträge | Möglichst reines Kotlin; keine Android-, SQLite-, Room- oder konkrete Kryptografieabhängigkeit |
| `:data` | Repository-Implementierungen, strukturierte Speicherung, FileAsset-Speicher sowie Import-, Wartungs- und Löschkoordination | Zugriff nur unter zentraler Sitzungs- und Retention-Prüfung |
| `:security` | Plattformnaher Keystore-Zugriff und Mechanismen für Authentifizierung und Verschlüsselung | Keine Fachmodelle, Fachrepositories oder Abhängigkeit auf andere Projektmodule |

**ARCH-05 — Vertragsadapter:** Benötigte fachliche Ports liegen in `:domain`; eigenständige technische Verträge von `:security` liegen in `:security`. Adapter in `:app` oder `:data` übersetzen zwischen diesen Verträgen. Eine Security-Klasse implementiert nicht durch eine neue Modulabhängigkeit einen Domain-Port. UI-nahe Plattformadapter erhalten nur die für ihren Workflow notwendigen Verträge, keine Schlüssel oder Speicherimplementierungen.

## 4. Composition Root und fachlicher Aufrufweg

**ARCH-06 — Verdrahtung:** `:app` ist Composition Root. Constructor Injection und zunächst manuelle Dependency Injection verbinden Implementierungen aus `:data` und `:security` mit Domain-/UI-Verträgen. Nach der Verdrahtung erhalten ViewModels und Composables ausschließlich ihre vorgesehenen Verträge. Ein DI-Framework wird erst nach eigener begründeter Entscheidung ergänzt.

```text
Composable → ViewModel → Domain-Anwendungsfall → Domain-Repository-Port
                                               ↓
                                     :data-Implementierung
                                               ↓
                          Sitzungs-/Retention-Prüfung und Speicherzugriff
                                               ↓
                                 technische :security-Verträge
```

Der Authentifizierungsdialog und die Kamera benötigen Android-UI-Adapter. Das erlaubt ihnen keine eigene Freigabe fachlicher Daten. Wartung nutzt gesonderte interne Zugriffe innerhalb derselben zentralen Koordination; sie erhält keinen UI-Bypass.

**ARCH-07 — Abhängigkeitskontrolle:** Der Modulgraph wird Gradle-seitig geprüft. Zusätzlich müssen Architekturprüfungen unerlaubte Implementierungsimporte aus UI-/ViewModel-Bereichen erkennen: `:app` benötigt die Compile-Abhängigkeiten für die Composition Root, deshalb kann der Modulgraph allein diese Paketgrenze nicht erzwingen. Implementierungsoberflächen bleiben möglichst klein; globale frei zugängliche Speicher-/Schlüssel-Service-Locators werden nicht eingeführt.

## 5. Session-State-Machine und Authentifizierung

**ARCH-08 — Technische Zustände:** Genau folgende Zustände beschreiben die Zugriffsfreigabe. Sie sind keine fachlichen Aktionsstatuswerte.

| Zustand | Bedeutung und erlaubtes Verhalten |
|---|---|
| `LOCKED` | Keine operativen Inhaltszugriffe; neutrale Oberfläche; expliziter Authentifizierungsstart möglich |
| `AUTHENTICATING` | Genau ein frischer Authentifizierungsfluss; keine Inhaltsfreigabe |
| `MAINTENANCE` | Zulässiger Schlüsselzugriff und geschützte interne Prüfung/Bereinigung; keine reguläre fachliche Inhaltsanzeige |
| `READY` | Reguläre Domain-Zugriffe unter fortlaufender Sitzungs- und Retention-Prüfung |
| `BLOCKED` | Geschützter technischer Fehlerzustand; keine Inhaltsfreigabe, keine automatische destruktive Reparatur |

Regulärer Weg: `LOCKED → AUTHENTICATING → MAINTENANCE → READY`. Abbruch oder erfolglose Authentifizierung führen ohne Inhaltsfreigabe nach `LOCKED`. Unklare Schlüssel-, Schema-, Integritäts- oder Datenzustände führen nach `BLOCKED`. Sperrereignisse invalidieren jede laufende Freigabe und führen nach `LOCKED`; das beseitigt keine zugrunde liegende Fehlerursache. Erneuter expliziter Versuch durchläuft wieder das Gate, keine automatische Prompt-Schleife. Während READY kann erforderliche Wartung die reguläre Freigabe aussetzen; Sicherheitsfehler führen nach BLOCKED.

**ARCH-09 — Sofortige Sperre:** Die App startet immer LOCKED und sperrt sofort bei Gerätesperre und Verlassen der App. Eine entsperrte Sitzung wird weder persistent gespeichert noch nach Prozessneustart wiederhergestellt. Rückkehr vom Systempicker ist keine Ausnahme. Bei Sperrung werden operative Zugriffe gesperrt, Kamera und Workflows beendet beziehungsweise invalidiert und sensible ViewModel-/Flow-Caches sowie nicht mehr benötigte Klartextreferenzen freigegeben.

Die genaue Android-Lifecycle-Abbildung einschließlich eigener Systemdialoge muss der Prototyp nachweisen. Ein Authentifizierungsdialog während AUTHENTICATING ist keine offene fachliche Sitzung. Sein Ergebnis darf nur den zugehörigen frischen Versuch fortsetzen; fachliche Anzeige setzt eine zulässige Vordergrundsituation und das gesamte Gate voraus. Eine bereits vorhandene READY-Sitzung wird durch Dialog- oder Picker-Rückkehr niemals übernommen.

**ARCH-10 — Session-Generation:** Eine interne Session-ID beziehungsweise Generation wird bei Sperrung ungültig. Operative Zugriffe, Streams und asynchrone Ergebnisse werden ihrer gültigen Sitzung zugeordnet. Ergebnisse alter Authentifizierungen, Kameraaufnahmen, Imports oder Repository-Flows dürfen weder in gesperrten noch in neueren Sitzungen veröffentlicht werden. Sperrung, Veröffentlichungsprüfung und Commit-Freigabe müssen gegen zeitliche Rennen koordiniert werden; ein alleiniger Check beim Start einer Coroutine genügt nicht. Die konkrete Synchronisation bleibt Prototypaufgabe.

**ARCH-11 — Authentifizierungsansatz:** DEVICE_CREDENTIAL mit Keystore-Bindung ist der bevorzugte Ansatz des ersten Security-Prototyps. Biometrie und eine eigene PaG-Passphrase sind darin ausgeschlossen. Es wird akzeptiert, dass eine Person mit Kenntnis des Gerätecredentials voraussichtlich auch die PaG-Authentifizierung erfüllen kann. Geräteentsperrung allein stellt keine READY-Sitzung her.

PaG zählt keine falschen PIN-/Passworteingaben im Android-DEVICE_CREDENTIAL-Dialog; deren Behandlung liegt beim Plattformmechanismus. PaG verhindert parallele Authentifizierungsflüsse, automatische Prompt-Schleifen, verspätete Ergebnisse alter Versuche und Übernahme alter entsperrter Sitzungen. Keine automatische Datenlöschung aufgrund falscher Eingaben. Eine spätere unabhängige PaG-Passphrase benötigt eine separate Bewertung von Offline-Raten, Ableitung, Wechsel, Verlust, Retry-Begrenzung und zusätzlichen Schlüsselzuständen sowie eine eigene Fehlversuchsspezifikation.

## 6. Unlock-/Maintenance-Gate und Datenzugriffsregeln

**ARCH-12 — Ein zentrales Gate:** Genau eine koordinierende Gate-Implementierung verantwortet folgende Reihenfolge:

1. Explizit gestartete frische Authentifizierung.
2. Zulässigen Schlüsselzugriff herstellen.
3. Geschützte Datenquelle öffnen.
4. Grundlegende nichtdestruktive Integritäts-/Konsistenzprüfung.
5. Unterbrochene Imports und Löschungen eindeutig zuordnen.
6. Fällige Retention ausführen und kontrollierte temporäre Reste bereinigen.
7. Abschließende Konsistenzprüfung.
8. READY für reguläre Domain-Zugriffe freigeben.

Bei unklarer Datenlage werden niemals vorsorglich Dateien, Datenbankinhalte oder Schlüssel gelöscht. Prüfungen behaupten keine vollständige Erkennung beliebiger Manipulation. Ein Öffnungsfehler erlaubt insbesondere keinen Scan mit anschließender vermeintlicher Orphan-Löschung.

**ARCH-13 — Fortlaufender Schutz:** Das Gate ist keine bloße Startprüfung. Repository-Lese-/Schreibzugriffe, Dateiöffnung und Ergebnisemission prüfen die Sitzung und berücksichtigen Retention auch während langer Sitzungen. Fällige Inhalte und Ableitungen werden vor erneutem regulärem Zugriff gesperrt und bereinigt. Bereits angezeigte oder gecachte betroffene Inhalte müssen ebenfalls entzogen werden. UI-Timer allein sind kein Zugriffsschutz.

Interne Maintenance-Zugriffe sind nur für die notwendigen Prüf-/Bereinigungsschritte zulässig. Repositories dürfen konstruiert werden, bevor READY erreicht ist; ihre Existenz ist keine Zugriffserlaubnis. Hintergrundarbeit, UI, Kamera und alternative Einstiegspunkte dürfen das Gate nicht umgehen.

### B0: Zentraler zeitbegrenzter Session-/Storage-Vertrag

**ARCH-31 — Absolute Grenze:** Unmittelbar nach erfolgreicher expliziter PaG-Authentifizierung beginnt eine absolute Deadline. Sie ist innerhalb dieser Sitzung unveränderlich, läuft während MAINTENANCE und READY und wird weder durch UI-Aktivität, Navigation, Scrollen, Tippen noch durch I/O oder Maintenance verlängert. Der Übergang nach READY startet sie nicht neu. Nach Ablauf gilt LOCKED und die Generation wird invalidiert. Eine neue Sitzung benötigt einen neuen explizit gestarteten DEVICE_CREDENTIAL-Flow und das Gate. Geräteentsperrung oder verbleibende Keystore-Autorisierung darf keine Sitzung erzeugen. Kein automatischer Prompt-Loop; für P1 beträgt die absolute Dauer 3 Minuten.

**ARCH-32 — Inaktivität in READY:** Zusätzlich gilt in READY eine Inaktivitätsdeadline. Nur qualifizierte tatsächliche Vordergrundinteraktion mit PaG kann den Aktivitätszeitpunkt aktualisieren. Renderzyklen, Timer, I/O, Flows, Animationen, Lifecycle-Callbacks, Hintergrundarbeit und Maintenance zählen nicht als Nutzeraktivität. Aktivität darf eine bereits abgelaufene Sitzung nicht wiederbeleben und die absolute Deadline niemals verschieben. Die Inaktivitätsgrenze soll nicht allein wegen fehlender sinnvoller Interaktion in MAINTENANCE ablaufen; dort gilt die absolute Grenze ohne Fachinhaltsanzeige. Die Initialisierung bei erster READY-Freigabe ist keine Nutzeraktivität. Die konkrete Interaktionsabbildung und Behandlung erneuter READY-Übergänge sind in B0 zu prüfen, ohne technische Ereignisse als Nutzeraktivität oder eine absolute Verlängerung einzuführen. Für P1 beträgt die READY-Inaktivitätsdauer 30 Sekunden.

**ARCH-33 — Zeit und Wiederaufnahme:** Eine injizierbare monotone, Sleep einschließende Zeitquelle entscheidet über Sessiondauer; auf Android ist elapsedRealtime() der Kandidat. Keine Wanduhr. Timer dienen nur zeitnaher UI-Reaktion, nicht als alleinige Zugriffskontrolle. Sleep verlängert keine Deadline; Resume prüft vor Anzeige. Activity Recreation verwirft die Sitzung konservativ; Process Death startet LOCKED. Alte Callbacks und asynchrone Ergebnisse prüfen Generation und Zeitgültigkeit. Ist nach erfolgreicher Authentifizierung deren Weiterverarbeitung verzögert, darf diese Verzögerung den Beginn der absoluten Deadline nicht nach hinten verschieben. Eine suspendierte oder vollständig kompromittierte Laufzeit erlaubt keine Garantie sekundengenauer Callback-Ausführung oder vollständiger Pufferlöschung.

**ARCH-34 — Storage-Zulassung und Rennen:** Storage besitzt keinen parallelen Unlock-State. Derselbe zentrale Vertrag prüft Session-State, Generation, zulässigen Lifecycle-/Foreground-Zustand, absolute Deadline und in READY zusätzlich Inaktivitätsdeadline. Prüfungen erfolgen mindestens vor neuer Operation, vor neuem relevantem Arbeitsschritt, vor Commit-Zulassung und vor Ergebnisveröffentlichung. Reguläre fachliche Zugriffe benötigen gültiges READY; interne Maintenance-Zugriffe nur das begrenzte MAINTENANCE-Recht aus ARCH-12 unter derselben absoluten Deadline.

Commit-Zulassung und Veröffentlichung müssen Zeit und Gültigkeit erneut gegen Sperrung/Ablauf koordiniert prüfen; ein früherer Check genügt nicht. Lange native DB-/Dateiarbeit darf die zentrale Sperrung nicht durch einen lange gehaltenen Koordinator-Lock verhindern. Bereits laufende native Operationen können physisch noch enden; nach Ablauf aber keine neue Zulassung, kein neuer relevanter Arbeitsschritt und keine fachliche Ergebnisveröffentlichung. Ein alter DB-/Stream-Handle bleibt unzulässig, auch wenn sein natives Objekt noch existiert. Kontrollierter Abbruch/Abschluss und geschützter Wiederanlauf werden für Query, Write, File Read/Write, Import, Delete und Maintenance getrennt getestet. Kein Versprechen sofortiger Löschung aller JVM-/nativen Puffer; kein automatisches Löschen des Bestands bei Sessionablauf.

**ARCH-35 — Prototypreihenfolge und Nachweise:** B0 prüft zuerst den zentralen Session-/Storage-Gültigkeitsvertrag. Danach B1 SQLCipher-Engine-Nachweis, früher Keystore-Verpackungsnachweis, B2 kleiner Vergleich SQLCipher direkt versus Room, B3 Tink Streaming AEAD, B4 Import/Delete/Wiederanlauf und B5 Gesamtintegration. Die B2-Auswahl ist inzwischen unter ARCH-36/37 festgelegt; die übrigen Kandidaten und Prüfaufträge werden dadurch nicht endgültig ausgewählt. Room ist keine Verschlüsselungsengine. Weitere Stage-B-Implementierungen oder Dependencies werden hierdurch nicht freigegeben. AndroidKeysetManager bleibt ausgeschlossen; keine eigene Kryptografie, Biometrie oder PaG-Passphrase in Stage B.

Bereits B1 bis B4 verwenden den B0-Vertrag beziehungsweise kontrollierte Fakes, keine zweite Gate-Implementierung. Nachzuweisen sind absolute Grenze trotz kontinuierlicher Bedienung, READY-Inaktivität, Maintenance ohne sinnvolle Interaktion, absolute Grenze während Maintenance, Wanduhränderungen, Sleep/Resume, Recreation/Process Death, verzögerte Timer und alte Generationen. Ablauf während langer Queries/Streams/Schreiboperationen, Import, Delete und Maintenance sowie genau zwischen Vorprüfung und Commit-Zulassung ist gezielt zu injizieren. Neue Authentifizierung bei verbleibender Plattformautorisierung und Lifecycle-/Keystore-Verhalten benötigen Emulator- und Realgerätetests. Stage A bleibt historischer Nachweis seines getesteten Umfangs; absolute und Inaktivitätsdeadline sind dort noch nicht nachgewiesen.

## 7. Strukturierter Bestand und verschlüsselter FileAsset-Speicher

**ARCH-14 — Single Source of Truth:** Eine verschlüsselte lokale strukturierte Datenquelle ist Single Source of Truth für operative strukturierte Daten. Das Datenmodell bleibt maßgeblich: aktionsgebundene Personen, genau ein Eigentümer je fertigem FileAsset, höchstens ein Beleg je Expense und keine aktionsübergreifende Datei-Deduplizierung. Organisatorische Rollen verleihen keine technischen Zugriffsrechte. Bibliothek, Suche und Dashboard sind geschützte Ableitungen.

Mehrteilige strukturierte Änderungen erfolgen transaktional. Metadaten, Indizes, Journale, Migrationen und temporäre Datenbankartefakte sind in das Schutzinventar einzubeziehen. Die Domain kennt keine konkrete Datenbank oder Kryptografiebibliothek.

**ARCH-36 — Relationale MVP-Persistenz nach B2:** Produktive relationale Datenhaltung erfolgt in :data über Room mit kontrolliertem SQLCipher-SQLiteDriver. Die geprüfte Baseline lautet Room 3.0.3 stable, KSP 2.3.12 (Build-only), AndroidX SQLite 2.7.1 und SQLCipher 4.19.1. [prototype-b2.md](prototype-b2.md) dokumentiert den fairen Vergleich und die Fairphone-Nachweise einschließlich der nicht direkt gelesenen ursprünglichen Direct-Create-ID. Die Auswahl reduziert manuelle SQL-, Cursor-/Null-/Typ-Mapping-, Query- und Schemafehler; explizite Migrationen und Tests bleiben erforderlich. Kein allgemeiner Android-/OEM- oder Produkt-Sicherheitsnachweis.

Kein dauerhaft geöffnetes Application-Singleton der RoomDatabase. Datenbanken werden ausschließlich innerhalb autorisierter Storage-Operationen geöffnet und deterministisch geschlossen; keine automatische Wiederöffnung und keine Wiederverwendung alter DB-/DAO-Handles nach Lock oder Generation-Wechsel. CREATE und OPEN EXISTING sind ausdrücklich getrennt; keine Standard-open-or-create-Semantik, destructive migration, fallbackToDestructiveMigration oder automatische Reparatur/Ersetzung. Der zentrale Vertrag nach ARCH-34 wird vor relevanten Operationen/Schritten, Commit und Result-Publication geprüft. Ein widerrufener Commit muss auf derselben Connection rollbacken. Keine zunächst ungeprüften Flow-/LiveData-/Paging-/Rx-observable DAOs und kein MultiInstanceInvalidationService. Room-/DAO-Typen bleiben vollständig in :data; Schlüsselbytes nur im bestehenden kurzen Key-Verbrauchspfad, keine neue Schlüsselhierarchie oder langlebige Key-Registry.

Direct SQLCipher bleibt Referenz-/Low-Level-Testengine und mögliche spätere Alternative, falls Room den Sicherheitsvertrag nicht mehr erfüllt. Ein solcher Wechsel erfordert erneute ausdrückliche Architekturentscheidung und Nachweise; kein automatischer Runtime-Fallback, kein schwächerer Mechanismus.

**ARCH-37 — Room-Inventar und Upgrade-Vertrag:** Nur die exakt bekannte reguläre leere d.lck-Datei darf als Room-Lockartefakt akzeptiert werden. Nichtleere oder unbekannte Lock-/Sidecar-Dateien blockieren den produktiven Ausgangszugriff; keine automatische Bereinigung. Legitime Journal-/WAL-Zwischenzustände innerhalb autorisierter Arbeit müssen von unklaren persistenten Ausgangslagen getrennt geprüft werden. Die aktuelle B2-Inventarprüfung blockiert nichtleere d.lck und unbekannte Einträge, lässt aber bekannte native Sidecar-Namen zu; vollständige Prüfung nichtleerer bekannter Sidecar-Ausgangslagen bleibt deshalb Produkt-/Regressionstestaufgabe. Keine zusätzliche Aussage zu physischer Löschung oder globaler Atomarität.

Room-/KSP-Abhängigkeiten, kontrollierter Driver-Adapter, d.lck, explizite Commit-Widerrufs-/Rollback-Behandlung und Ausschluss des MultiInstanceInvalidationService sind bewusst akzeptierte Sonderkosten. Room ist nicht automatisch sicher. Upgrades von Room/SQLite/SQLCipher/KSP sind security-relevant: CREATE-vs-OPEN, d.lck/Sidecars, Connection-/Handle-Lifecycle, Commit/Rollback bei Session-Widerruf, Manifest-/Multi-Instance-Komponenten, Migrationen, Close und Key-Lebensdauer werden erneut gegen dieselbe Failure-Matrix geprüft. Spätere Versionen gelten nicht automatisch als gleichwertig. API-30-/37-Nachweise, Power-loss, RAM-/Flash-Grenzen und OEM-Verhalten bleiben offen.

**ARCH-15 — FileAsset-Speicher:** Persistente importierte Dateien und Staging-Inhalte liegen ausschließlich verschlüsselt im internen app-spezifischen Speicher. Physische Dateinamen sind opaque/random und enthalten keine Fach- oder Personeninformationen. Fachliche und ursprüngliche Dateinamen sowie sonstige fachliche Metadaten liegen ausschließlich im geschützten strukturierten Bestand. Ein storage_locator ist kein öffentlich nutzbarer Dateipfad.

Keine Klartext-Sidecars, dauerhaften entschlüsselten Dateien oder öffentlichen/gemeinsam genutzten Klartextkopien. Technische Bild-/Containerheader unterscheiden sich von fachlichen Metadaten; erforderliche Bildheader liegen innerhalb des verschlüsselt gespeicherten Bildinhalts. Notwendige technische Verschlüsselungscontainerfelder sind erst mit der Speicherlösung zu spezifizieren und dürfen keine fachlichen Klartextmetadaten aufnehmen. Die fehlende direkte Menschenlesbarkeit gespeicherter Dateien ist erwünscht.

**ARCH-16 — Schlüsselverantwortung:** Android Keystore bildet den Root of Trust für geeignetes gerätegebundenes Schlüsselmaterial. Datenbank- und Dateischlüssel erhalten funktional getrennte Verantwortlichkeiten. Dies legt keine endgültige Schlüsselhierarchie, Ableitung, Verpackung, Hardwareanforderung oder Autorisierungsdauer fest. Keine eigene Kryptografie und kein stiller schwächerer Fallback. Die Architektur basiert nicht auf deprecated AndroidX-Security-Crypto-Klassen wie EncryptedFile, EncryptedSharedPreferences oder MasterKey.

## 8. Gemeinsame Importarchitektur

**ARCH-17 — Quellen und Adapter:** Kameraimport und System-Dateiimport verwenden denselben kontrollierten Import-Anwendungsfall. Kamera- und URI-/Picker-Adapter kennen keine Schlüssel und erzeugen keine fertigen FileAssets selbständig. Sie liefern einen begrenzten, kurzlebigen Quellzugriff mit Workflow-/Sitzungszuordnung; dessen konkrete Vertragsform bleibt offen. Quellinhalte, Namen, Größenangaben und MIME-Angaben sind nicht vertrauenswürdig.

Allgemeiner Import erfolgt durch ausdrückliche Auswahl über Android-Systemmechanismen. Lokale Quellen werden bevorzugt; EXTRA_LOCAL_ONLY wird angefordert, soweit der gewählte Mechanismus es zuverlässig unterstützt. Das garantiert nicht, dass fremde Dokumentanbieter kein Netzwerk nutzen. Nach Picker-Rückkehr ist frische Authentifizierung erforderlich. Ohne noch zulässigen Quellzugriff muss neu ausgewählt werden. Keine dauerhafte Quell-URI und keine dauerhaft behaltene URI-Berechtigung nach erfolgreichem Import; keine automatische Wiederaufnahme über gespeicherte Quell-URIs. Externe Originale bleiben außerhalb der App-Kontrolle.

**ARCH-18 — Kontrollierte Übernahme:** Der gemeinsame Import folgt diesen Übergängen:

| Schritt | Erforderliche Wirkung | Wiederanlaufanforderung |
|---|---|---|
| 1. Technischen Auftrag anlegen | Minimaler geschützter Arbeitsstatus; noch kein fertiges Fachobjekt | Eindeutig identifizierbaren Versuch ohne Ergebnis kontrolliert abbrechen können |
| 2. Verschlüsselt in internes Staging schreiben | Kein persistenter Klartext; laufende Größen-/Formatbegrenzung | Zugeordnete unvollständige verschlüsselte Staging-Daten nicht als fertig freigeben |
| 3. Abschluss/Integrität prüfen | Vollständigkeit gemäß späterer Speicherlösung prüfen | Nicht belegten Abschluss nicht als Erfolg interpretieren |
| 4. Verschlüsseltes Ergebnis intern veröffentlichen | Datei intern bereitstellen, noch keine reguläre fachliche Freigabe | Veröffentlichung dem Auftrag eindeutig zuordnen; fertigen Dateizustand nicht allein aus Existenz ableiten |
| 5. Fachlich strukturiert committen | FileAsset, genau einen Eigentümer und Freigabestatus konsistent speichern | Commit erkennen; weder Doppelimport noch widersprüchliche Eigentümer erzeugen |
| 6. Technischen Auftrag entfernen | Arbeitsstatus nach bestätigtem Abschluss entfernen | Restauftrag bei bereits bestätigtem Commit ohne Löschung des gültigen Assets entfernen können |

Technischer Importstatus ist kein zusätzlicher fachlicher Aktionsstatus und kein dauerhaftes Inhaltsprotokoll. Vor Schritt 5 bleibt das Ergebnis außerhalb regulärer Repository-/Viewer-Zugriffe. Beim Commit muss die Sitzung noch zulässig sein. Der Eigentümer wird innerhalb derselben strukturierten Transaktion angelegt oder als vorhandener Eigentümer geprüft; bei erfolgreichem Commit müssen Eigentümer und exklusive Dateireferenz konsistent bestehen. Die zugehörige Aktion und ein bereits vorhandener Eigentümer dürfen nicht zur Löschung gesperrt sein.

**ARCH-19 — Keine DB-/Filesystem-Atomizität behaupten:** Eine Datenbanktransaktion umfasst nicht automatisch Dateisystem und Schlüsselzustände. Jeder Übergang benötigt einen definierten Wiederanlauf und Ausfalltests. Nach Process Death darf ein unvollständiger Versuch verloren gehen; das rechtfertigt nur die Bereinigung eindeutig zugeordneter Versuchsreste. Unbekannte Dateien werden nicht allein aufgrund fehlender Zuordnung oder eines gescheiterten DB-Opens gelöscht. Unklare Zustände führen zur geschützten Blockierung. Konkretes Auftragschema, Veröffentlichungstechnik und Haltbarkeits-/Flush-Strategie bleiben offen.

## 9. CameraX-Architektur

**ARCH-20 — Bevorzugter Prototyppfad:** CameraX-In-Memory-Aufnahme über OnImageCapturedCallback ist verbindlich bevorzugt. Aufnahme nur in READY nach expliziter Benutzeraktion; CAMERA ist ausschließlich für diesen Workflow zulässig. Ohne aktiven Workflow keine Kamerainitialisierung/-nutzung und keine Hintergrundaufnahme.

Der Kameraadapter übergibt den aufgenommenen Inhalt kontrolliert an ARCH-17/18. ImageProxy wird auf Erfolgs-, Fehler-, Abbruch- und verspäteten Callback-Pfaden korrekt geschlossen. Bei Sperrung: Kamera beenden, Workflow invalidieren, spätes Ergebnis verwerfen und keine Aufnahme veröffentlichen. Bereits entstandene eindeutig zugeordnete verschlüsselte Reste werden nach den Wiederanlaufregeln behandelt, ohne für Bereinigung neue Schlüsselrechte zu eröffnen.

**ARCH-21 — Begrenzte Bildpipeline:** Nur normale Fotoaufnahme mit kontrollierter/begrenzter Auflösung und einem freigegebenen Standard-Bildpfad. Kein OCR, Dokumentenscanner, Perspektivkorrektur, eigene Filter, RAW oder unnötige HDR-/Extension-Pipeline. Bitmap-Konvertierung und Neukompression möglichst vermeiden. Keine persistente Vorschau, Klartext-Disk-Caches oder zusätzlichen Sidecars.

Der Prototyp prüft tatsächliches Ausgabeformat, Header/Metadaten, Rotation/Orientierung, Kamera-/Konvertierungspuffer, Speicherbedarf und Freigabezeitpunkte. Zusätzliche Klartextkopien im Arbeitsspeicher können entstehen; vollständige Speicherlöschung wird nicht garantiert. Rotation darf nicht stillschweigend eine persistente Klartext-Zwischendatei oder neue komplexe Verarbeitungspipeline einführen.

**ARCH-22 — Nachweis statt Fallback:** CameraX-OutputFileOptions-/OutputStream-Verarbeitung ist kein Beweis für klartextfreie Zwischenverarbeitung. Der gewählte appseitige Callback-Pfad muss ohne persistente Klartext-Zwischendatei und ohne Gate-Umgehung nachgewiesen werden. Das ist keine Garantie über alle internen OS-/Hardwarepuffer auf kompromittierten Geräten.

Kein automatischer Fallback auf ACTION_IMAGE_CAPTURE oder eine externe Kamera-App. Scheitert dieser Nachweis oder wird die Pipeline unverhältnismäßig komplex, wird Kameraimport verschoben. Eine externe Kamera reduziert eigenen Kameracode, verarbeitet Inhalte aber außerhalb des vollständigen App-Kontrollbereichs. Für PaG bleibt daher der begrenzte interne Pfad bevorzugt; zusätzliche Bildbearbeitung kann mehr Implementierungsrisiko als Schutzgewinn erzeugen und ist ausgeschlossen.

## 10. Process Death und temporärer Zustand

**ARCH-23 — Keine sensitive Zustandswiederherstellung:** Kein persistenter sensibler UI-State in SavedStateHandle, rememberSaveable, Preferences, DataStore, Navigationsargumenten oder vergleichbaren Mechanismen. Auch Sitzungsfreigaben werden nicht gespeichert. Process Death darf Eingaben, Navigation und den aktuellen Workflow verlieren. Nicht sensible Einstellungen dürfen nur nach klarer Abgrenzung persistiert werden; dies erlaubt keine operative Zweitdatenquelle.

| Abbruchzeitpunkt | Verhalten nach Neustart |
|---|---|
| Authentifizierung oder READY-Sitzung | LOCKED; neuer expliziter Versuch, keine alte Freigabe |
| Kamera vor fachlichem Commit | Keine Aufnahme automatisch wiederherstellen; eindeutig zugeordnete verschlüsselte Reste im Gate prüfen |
| URI-Import oder Verschlüsselung | Kein persistierter Quellzugriff zur automatischen Fortsetzung; unvollständiger Versuch darf verloren gehen |
| Datei veröffentlicht, DB-Commit unklar | Auftrag/Datei/Bestand geschützt abgleichen; keine voreilige Löschung oder Doppelveröffentlichung |
| Fachlicher Commit fertig, Auftrag verblieben | Bestätigten gültigen Bestand erhalten; zugehörigen Restauftrag bereinigen |
| Löschung unterbrochen | Minimalen geschützten Auftrag fortsetzen; betroffene Inhalte nicht erneut regulär anzeigen |
| Schlüssel-/DB-Öffnung scheitert | BLOCKED; vorhandenen Bestand unangetastet lassen |

Sensitive UI wird erst nach erfolgreichem Gate rekonstruiert. Klartextreferenzen werden möglichst kurz gehalten und freigegeben; dies verspricht keine Überschreibung sämtlicher Laufzeit-/OS-Kopien.

## 11. Retention und Löschkoordination

**ARCH-24 — Fachliche Regeln und Zugriffsobergrenze:** Fristen und Kaskaden folgen data-retention.md und data-model.md. Vollständige Aktionslöschung ist die Obergrenze für untergeordnete Daten; keine versteckten Restbestände. Die Aktionsstatuswerte bleiben DRAFT, PREPARATION, ACTIVE, COMPLETED und DELETION_PENDING; DELETED ist nur das Ergebnis, kein persistierter Datensatz. Keine Wiedereröffnung abgeschlossener Aktionen.

**ARCH-25 — Wiederanlauffähige Bereinigung:** Löschkoordination sperrt betroffene Zugriffe, sichert minimal notwendige geschützte Ziel-/Fortschrittsreferenzen und koordiniert strukturierte Beziehungen, Dateien und Ableitungen. Zielreferenzen dürfen nicht vorzeitig verloren gehen, wenn Dateien noch zu bereinigen sind. Wiederholung darf keinen Inhalt wiederherstellen oder fehlgeschlagene Teilschritte als Erfolg melden. Arbeitsstatus wird nach belegtem Abschluss entfernt; keine dauerhafte detaillierte Löschhistorie.

Keine Schlüssel nur für Hintergrundlöschung verfügbar halten. Fällige Löschungen bei gesperrter App werden beim nächsten zulässigen Unlock vor Inhaltsanzeige nachgeholt. Wird eine laufende Operation gesperrt, darf sie nicht mehr regulär veröffentlichen; ihr sicherer Abbruch-/Abschlusspunkt wird im Storage-/Security-Prototyp festgelegt. Warnungen verlängern keine Fristen.

Die konkrete Reihenfolge über DB, Dateien und gegebenenfalls Schlüssel sowie Retry-/Haltbarkeitsregeln bleiben offen. Keine exakte Ausführungszeit bei ausgeschaltetem Gerät oder fehlenden Schlüsseln, keine garantierte physische Überschreibung von Flash-Zellen und keine unbewiesene kryptografische Löschgarantie. Kalender-/Zeitmodell, Fristanker und Standardfristen werden hier nicht entschieden.

## 12. Fehlerzustände und Datenverlust

**ARCH-26 — Fail closed ohne automatische Zerstörung:** Unklare Schlüssel-, Schema-, Integritäts- oder Datenzustände blockieren Inhaltszugriffe. Eine fehlgeschlagene Entschlüsselung beweist keine Datenkorruption. Kein Klartext-Fallback, stilles Ignorieren von Sicherheitsfehlern, automatischer Wechsel auf schwächere Mechanismen, automatische Ersetzung vorhandener Datenbanken oder Dateibereinigung infolge gescheiterten DB-Opens. Fehlerausgaben enthalten nur unkritische technische Kategorien gemäß SEC-08.

Erstinitialisierung muss von vorhandenem, aber nicht lesbarem Bestand unterscheidbar sein. Bei unklarer Unterscheidung wird blockiert. Endgültiger Datenverlust wird in bestimmten Fehlerfällen akzeptiert, aber niemals als Erlaubnis automatischer Löschung verstanden. Ein endgültiger lokaler Reset wäre eine separate ausdrücklich bestätigte Benutzeraktion ohne Recovery; diese Architektur führt keine automatische Resetfunktion ein.

## 13. Android-Komponenten, Berechtigungen und Oberflächen

**ARCH-27 — Minimale Schnittstellen:** Im MVP keine INTERNET-, Kontakte-, Telefon-, Standort- oder breite Speicherberechtigung. Keine Networking-Library ohne neue explizite Architekturentscheidung. CAMERA ist die einzige zusätzliche fachliche Runtime-Berechtigung und nur für ARCH-20 zulässig. Keine Telemetrie oder automatische Uploads.

Die Launcher-Activity ist nur soweit für den Android-Start erforderlich exportiert; jeder externe Einstieg beginnt geschützt. Sonstige Activities, Services, Receiver und Provider sind standardmäßig nicht exportiert, sofern nicht eine einzeln begründete notwendige Plattformintegration dies verlangt. Keine fachlichen Daten-/Dateiexportschnittstellen, keinen pauschal freigebenden FileProvider und keinen ungeprüften Deep-Link-Zugriff einführen. Zusammengeführtes Manifest einschließlich Dependency-Komponenten, Berechtigungen und automatischer Initializer ist zu prüfen. Keine operativen Direct-Boot-Zugriffe oder Hintergrundkamera.

Im MVP keine lokalen PaG-Notifications, Push Notifications, Push-Infrastruktur, Benachrichtigungsberechtigung oder Foreground Services. Keine eigenen Notification Channels ohne später nachgewiesene technische Notwendigkeit; keine implizite Notification-Freigabe dadurch. Keine operativen Widgets, operativen dynamischen/persistenten Shortcuts oder sensiblen Lockscreen-Inhalte. Retention-/Ablaufwarnungen ausschließlich in zulässiger entsperrter Sitzung. Lange Storage-Workflows begründen keinen Foreground-Service-Bypass.

Keine Sicherheitsmechanismen zur App-Verbergung: kein versteckter Launcher, geheime Start-/Dialer-Codes, Tarnung als andere App oder versteckte Komponenten zur Umgehung normaler App-Erkennung. Zurückhaltendes Branding ist zulässig. Die App gilt als auffindbar; Ressourcenstärke und spätere OS-/Kernel-Kompromittierung eines Angreifers werden im Threat Model berücksichtigt, ohne Kontrollgarantie über ein kompromittiertes laufendes OS.

**ARCH-28 — UI-Schutz und Viewer-Grenzen:** Sensitive Oberflächen werden gegen Screenshots und Recent-App-Vorschauen durch geeigneten Plattformfensterschutz und neutrale gesperrte Ansichten geschützt. Dies garantiert keinen Schutz vor externer Fotografie oder vollständig kompromittiertem System. Keine unaufgeforderte Zwischenablagekopie; weitere Clipboard-UX bleibt nach security.md offen.

Keine externe Klartextöffnung oder automatische Übergabe an externe Viewer im MVP. Interne Formate und Renderer bleiben dem Viewer-/Storage-Prototyp vorbehalten. Wenn ein Format unerwünschte Klartextdateien, Disk-Caches oder unverhältnismäßige Komplexität erfordert, ist keine Vorschau im MVP zulässig und gegenüber externer Klartextöffnung bevorzugt. Keine implizite Freigabe privater Klartext-Zwischendateien und kein universeller eigener Dokumentrenderer.

## 14. Backup-/Recovery-Grenzen

**ARCH-29 — Bewusster Verzicht:** Kein Backup, Cloud-Backup, Device-to-Device-Transfer, Recovery-Key oder Export operativer PaG-Daten im MVP. Ausschlüsse umfassen operative Daten, Authentifizierungsdaten, Schlüsselartefakte, Retention-/Import-Arbeitsdaten und temporäre sensible Inhalte einschließlich verschlüsselter Kopien. Manifest-/Regelkonfiguration allein beweist den Ausschluss nicht; Nachweise auf der unterstützten Gerätematrix sind erforderlich.

Geräteverlust, Schlüsselverlust und bestimmte nicht sicher reparierbare Fehler können endgültigen vollständigen Datenverlust bedeuten. Keine verdeckte Recovery und kein Zugriffsschutz umgehender Schlüssel. Ein zukünftiges verschlüsseltes Offline-Backup wäre ein separates Feature mit eigener Bedrohungsanalyse. Normale Neustarts müssen gültige persistente Daten erhalten; der Backupverzicht hebt Konsistenz- und Haltbarkeitsanforderungen nicht auf.

## 15. Testbarkeit und akzeptierte Nachteile

**ARCH-30 — Injizierbare Grenzen und Ausfallprüfungen:** Zeitquelle, Authentifizierung, Session-Koordination, Ausführungssteuerung, Repository-/Speicherports, Dateisystemzugriffe sowie Kamera-/URI-Quellen werden über explizite Verträge testbar gemacht. Keine zweite Gate-Implementierung für einzelne Features. Domain-Tests benötigen weder Android noch konkrete Datenbank oder Keystore. Tests nutzen ausschließlich synthetische Inhalte.

Für testing.md sind insbesondere abzuleiten:

- Modulgraph und unerlaubte Implementierungsimporte (ARCH-04 bis ARCH-07).
- Start, Gerätesperre, App-Verlassen, Dialog-/Picker-Rückkehr, Process Death und verspätete Ergebnisse; Rennen zwischen Sperrung und Veröffentlichung (ARCH-08 bis ARCH-13, ARCH-23).
- Fehlversuche ohne appseitige Zählung des Systemdialogs, keine Prompt-Schleifen und keine parallelen Flüsse (ARCH-11).
- Retention während langer Sitzungen, Entzug von Caches und Wiederanlauf unterbrochener Löschungen (ARCH-13, ARCH-24/25).
- Ausfälle vor/nach jedem Importschritt einschließlich Speicherknappheit, Commit-Fehlern und unbekannten Dateien; keine Bereinigung bei gescheitertem DB-Open (ARCH-18/19/26).
- Kamera-Lifecycle, ImageProxy-Schließung, begrenzter Speicherbedarf und Klartextprüfung aller kontrollierten Speicherbereiche (ARCH-20 bis ARCH-23).
- Manifest, Provider/Initializer, Screenshot-/Vorschauschutz, Viewer-Caches und Backup-/Transferausschluss (ARCH-27 bis ARCH-29).
- Room-/SQLCipher-Upgrades: CREATE-vs-OPEN, Inventar einschließlich d.lck/Sidecars, kurze Connection-/Handle-Lebensdauer, Commit-Widerruf/Rollback, Migrationen, Manifest und Close (ARCH-36/37).

Reale Keystore-, Kamera-, Lifecycle-, Backup- und Herstellerprüfungen ergänzen Fakes/Unit Tests. Ergebnisse benennen Plattform, Gerät, Konfiguration und Grenzen. Vor Echtdaten gelten die unabhängigen Sicherheits- und Datenschutzprüfungen aus PRODUCT.md.

**Bewusst akzeptierte Nachteile:** Echte Modultrennung erzeugt Adapter-/Verdrahtungsaufwand. Sofortige Sperre verlangt erneute Authentifizierung nach App-Wechsel und kann Workflows abbrechen. Process Death verliert ungespeicherte UI/Imports. Maintenance kann Inhaltszugriff verzögern oder blockieren. Opaque Dateinamen erschweren manuelle Inspektion. Beschränkte Kamera-/Viewerfunktionen und fehlende Recovery verringern Komfort und Verfügbarkeit.

**Komplexitätsgrenze:** Zusätzliche eigene Passphrase, per-Datei-Schlüsselarchitektur, aufwendige Wiederanlaufprotokolle, Bildkonvertierung und universelle Renderer können mehr Implementierungsrisiko als Schutzgewinn erzeugen. Passphrase und endgültige Schlüsselstruktur bleiben Bewertungsfragen; Wiederanlauf wird auf notwendige Konsistenz begrenzt, Bildverarbeitung/Viewer auf einen nachweisbaren Umfang. Keine eigene Kryptografie oder selbst entworfene allgemeine Transaktionsplattform.

## 16. Offene Prototypentscheidungen

| ID | Offene Entscheidung / erforderlicher Nachweis | Zuständiger Schritt |
|---|---|---|
| ARCH-OD-01 | DEVICE_CREDENTIAL-/Keystore-Zusammenspiel, Autorisierungsdauer beziehungsweise Operationsbindung, Gerätecredential-Wechsel/Invalidierung und zulässiger Schlüsselzugriff | Security-Prototyp |
| ARCH-OD-02 | Mindest-Android-Version, Gerätematrix und Hardware-Keystore-Anforderungen; kein stiller schwächerer Fallback | Security-Prototyp, vor Foundation |
| ARCH-OD-03 | Weitere Kryptografiebibliotheken, Algorithmen/Parameter, Verpackung/Ableitung und endgültige Schlüsselhierarchie einschließlich Löschmöglichkeiten; relationale DB-Auswahl ist nach B2 gemäß ARCH-36/37 entschieden | Security-/Storage-Prototyp |
| ARCH-OD-04 | Präzise Lifecycle-Ereignisse, Systemdialoge, Session-Rennen und sichere Abbruch-/Commitpunkte laufender Operationen | Security-/Lifecycle-Prototyp |
| ARCH-OD-05 | Import-/Löschauftragsschema, Containerprüfung, interne Veröffentlichung und Haltbarkeits-/Flush-Strategie über DB/Filesystem; keine Klartextreste | Storage-Prototyp |
| ARCH-OD-06 | CameraX-Version, Auflösung, Standard-Bildpfad, Rotation/Header/Metadaten, Speicherbedarf und persistenzfreier appseitiger Callback-Pfad | Kamera-Prototyp; bei fehlendem Nachweis verschieben |
| ARCH-OD-07 | Interne Viewerbibliothek, unterstützte Dokumentformate, Größenlimits und sichere Parser-/Cache-Verarbeitung | Viewer-/Storage-Prototyp; keine Vorschau zulässig |
| ARCH-OD-08 | Konkrete Speicherorte und Backup-/Transferregeln auf der Gerätematrix | Storage-Prototyp, vor persistenter Speicherung |
| ARCH-OD-09 — P1 entschieden | 3 Minuten absolut / 30 Sekunden READY-Inaktivität; nur echte Vordergrundinteraktion erneuert Inaktivität | elapsedRealtime; App-Verlassen invalidiert sofort; spätere Anpassbarkeit separat |

Die Auswahl SQLCipher direkt versus Room ist mit ARCH-36/37 entschieden. Gemeinsame versus per-Asset FileAsset-Keysets, endgültige Schlüsselhierarchie, Keystore-Operationsbindung versus Autorisierungsfenster, Android-/Hardwareanforderungen und Retention-Fristen bleiben offen. Der B0-Vertrag entscheidet diese übrigen Alternativen nicht.

Weitere fachliche Entscheidungen bleiben in den zuständigen Grundlagen offen: Fristanker/-werte, maximale Lebensdauer, individuelle Fristen, Zeitmodell und Regelversionierung in data-retention.md; Klassifizierungsänderungen, indirekte Personenbezüge und Importmetadatenbehandlung in privacy.md; weitere Aktionsübergänge, Pflichtangaben, Materialstatus und Währungen in data-model.md. Die Architektur entscheidet sie nicht stillschweigend.

## 17. Präzisierungen und ausdrücklich abgelöste ältere Anforderungen

| Bisherige Stelle | Jetzt verbindliche Einordnung |
|---|---|
| SEC-02 / SEC-OD-03: Gerätecredential oder App-Geheimnis noch offen | DEVICE_CREDENTIAL + Keystore ist bevorzugter erster Prototyp; keine eigene PaG-Passphrase darin; endgültige technische Bindung bleibt offen |
| SEC-03, TM-10 und ältere Fehlversuchstests | Die ältere Vorgabe einer eigenen appseitigen Fehlversuchsbegrenzung einschließlich Zähler-/Neustartprüfungen wird für DEVICE_CREDENTIAL ausdrücklich abgelöst. Falsche Systemcredential-Eingaben behandelt die Plattform; PaG erzwingt Ablauf-/Sitzungskontrolle gemäß ARCH-11. Eigene progressive Begrenzung nur bei später separat spezifizierter PaG-Passphrase |
| PRIV-08/SEC-12: Import nur durch System-Dateiauswahl | Die bisherige Beschränkung auf System-Dateiauswahl wird ausdrücklich um bewusst ausgelösten Kameraimport erweitert. System-Dateiimport bleibt bestehen; beide Quellen verwenden denselben Anwendungsfall, Kamera ausschließlich mit CAMERA gemäß ARCH-20/27 |
| PRIV-09, SEC-13 / SEC-OD-08: externe Öffnung offen | Im MVP ausgeschlossen; keine automatische externe Viewerübergabe |
| SEC-07: mögliche private Klartext-Renderausnahme empfohlen | Keine solche Ausnahme mit dieser Architektur freigegeben; ungeeignete Vorschau kann entfallen |
| SEC-09 / SEC-OD-08: Screenshotumfang noch offen | Schutz sensibler Oberflächen verbindlich; konkrete Plattform-/Lifecycle-Nachweise bleiben erforderlich |
| Datenmodell OD-07 und OD-09 | Aktionslöschung begrenzt Kinder; personenbezogene Daten, ungeprüfte Freitexte und Importe standardmäßig SENSITIVE gemäß späteren Privacy-/Retention-Entscheidungen |
| PRODUCT.md: vorläufige Technologien und Datenmodellbeispiele | Relationale DB-Auswahl nach B2 gemäß ARCH-36/37; weitere Kryptobibliotheken offen, keine deprecated Security-Crypto-Architektur; fachliche Korrekturen aus data-model.md gelten weiter |
| PRODUCT.md INT-001: atomare Änderungen | Strukturierte Änderungen bleiben transaktional. Über DB, Dateisystem und Schlüssel wird keine gemeinsame atomare Transaktion behauptet; die ausdrücklich bestätigten Wiederanlaufregeln aus ARCH-18/19/25 konkretisieren diese Grenze |

Diese Präzisierungen, Erweiterungen und Ablösungen dokumentieren ausdrücklich bestätigte Entscheidungen, keine stillen Änderungen der Grundlagen. Die älteren Retry- und Systemauswahlformulierungen sind isoliert gelesen mit dem neuen Stand nicht vereinbar; maßgeblich ist an diesen Stellen die hier dokumentierte spätere Benutzerentscheidung. Ältere Dateien bleiben unverändert und müssen vor der späteren Testableitung an diesen Stellen gemeinsam gelesen werden. Noch ausstehende Sicherheitsverfahren werden nicht vorweggenommen; die ausdrücklich freigegebene B2-Auswahl ist unter ARCH-36/37 dokumentiert.


## P1 – DB-only-Produktruntime

Session-resident product DB key ist ausdrücklich freigegeben: ein instanzlokaler,
generationsgebundener SessionDbKeyOwner innerhalb der Activity-eigenen Runtime,
keine persistente oder globale Schlüsselregistry. Dies ergänzt ARCH-36: kurze
Key-Leases pro Room-Operation; der Owner darf den produktiven DB-Key während der
zulässigen Session halten. Room selbst bleibt kurzlebig. Scope ausschließlich DB;
keine Entscheidung zur späteren DB+File-Orchestrierung.

Für P1 sind ARCH-OD-09 und SEC-OD-10 hinsichtlich Dauer entschieden: 3 Minuten
absolute Grenze ab erfolgreicher Authentifizierung, 30 Sekunden READY-Inaktivität,
MAINTENANCE nur absolut begrenzt, immediate Lock beim Verlassen der App.
Benutzerinteraktion verlängert niemals absolut; Lifecycle, Recomposition,
Storage und Publication verlängern auch die Inaktivität nicht. Zeitbasis ist
monoton und sleep-inclusive. Spätere Parameteränderungen sind Securityentscheidungen.

Die Launcher-Activity ist Produkt-Composition-Root; die frühere PrototypeActivity
bleibt unregistrierte Evidenz unter androidTest. Produkt-Views/ViewModel verwenden nur Domain-Ports.
Produktnamespace pag-db-v1 unter noBackupFilesDir, Alias
org.pag.product.database.rsa.v1; keine Interpretation/Migration von Prototypdaten.
EMPTY erfordert vollständig leeren Namespace plus fehlenden Alias. Jede partielle
oder unbekannte Struktur blockiert ohne Reparatur. READY_TO_OPEN beschreibt nur
strukturelle Vollständigkeit; authentifizierter Integritäts-/Room-Schemacheck in
MAINTENANCE ist zusätzlich notwendig.

Bestehender kontrollierter B2-Driver und typisierte Room-Background-Fault-Behandlung
werden wiederverwendet. Ein Scope-Adapter reicht die ursprünglichen READY- bzw.
MAINTENANCE-Prüfungen unverändert durch und adaptiert lediglich die historische
Maintenance-Typassertion des Drivers. Kein Gate-Bypass. Widerruf bereits vor
COMMIT-prepare sowie bei COMMIT-step führt zu Rollback auf derselben Connection.
Neue Produkt-Repositories bekommen weder freie DAO- noch DB-/Key-Referenzen.
