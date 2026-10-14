# P9a — Gedächtnisprotokoll: Produkt- und Fragebogenvertrag

## 1. Status, Baseline und Zweck

Verbindlicher Produktvertrag, ausschließlich Spezifikation/Dokumentation.
Baseline: sauberer Working Tree auf `main`, HEAD `a851683` (P8-Merge).
Für P9a wurde der Branch `p9a-memory-protocol-product` angelegt; HEAD unverändert.
`git log -6 --oneline` zeigt P8, P7 und P6c4; ActionDatabase und Schemaexport
stehen auf **9**. P6/P7/P8 sind vorhanden. Keine technische P9-Implementierung,
Schemaänderung oder Migration durch P9a. Technische Ausgestaltung erst P9b.

Grundlagen: [Produkt](../PRODUCT.md), [Datenmodell](data-model.md),
[Privacy](privacy.md), [Security](security.md), [Retention](data-retention.md),
[Testing](testing.md), [UX-Philosophie](ux-philosophy.md),
[Roadmap](ux-roadmap.md), [P6a](product-p6a-protocol.md),
[P6b](product-p6b-protocol-implementation.md) und [P8](product-p8-costs.md).

Der sichtbare Produktname lautet **Gedächtnisprotokoll**, im Plural
**Gedächtnisprotokolle**. Es ist ein persönlicher rückblickender Bericht über
das eigene Erleben während und nach einer Aktion, insbesondere nach
Polizeimaßnahmen, Transport, Gewahrsam oder Entlassung aus der Gesa.
Typischer Beginn ist nach Entlassung; Erfassung während ACTIVE ist ebenfalls
möglich. Zweck ist zeitnahes strukturiertes Festhalten von Erinnerungen,
unter anderem für spätere rechtliche Beratung oder gerichtliche Auseinandersetzungen.

Die App hält berichtete Tatsachen, Erinnerungen, Unsicherheiten, wahrgenommene
Aussagen, Bedingungen und Auffälligkeiten fest. Sie überprüft deren Wahrheit
nicht und bewertet weder Maßnahmen noch Aussagen rechtlich. Sie gibt keine
Zusage gerichtlicher Verwertbarkeit, lückenloser Erinnerung, forensischer
Unveränderlichkeit oder rechtlicher Absicherung. Abschluss bedeutet eine bewusste
Produktaktion, keine Bestätigung, dass alle Fragen beantwortet oder alle Vorgänge
erinnert wurden.

## 2. Eigenständiger Bereich neben P6 und Note

| P6 Aktionsprotokoll | P9 Gedächtnisprotokoll |
| --- | --- |
| Gemeinsames laufendes Aktionsgeschehen, insbesondere ACTIVE-Ereignisse | Persönliche rückblickende Erinnerung aus Sicht einer Person |
| Gemeinsames Lage-/Dokumentationsinstrument mit bewusster Nachbereitung | Strukturierter Fragebogen und eigene freie Erinnerung, auch nach Entlassung |
| Revisionsgebundene Ereigniseinträge | Abschnittsweise gespeicherter Entwurf, abgeschlossener Bericht und spätere Freitextergänzungen |

P9 wird nicht als allgemeine organisatorische `Note` oder P6-Eintrag modelliert.
Keine Zusammenführung, automatische P6-Verknüpfung, Übernahme von P6-Ereignissen
oder Erzeugung von P6-Einträgen aus P9. P9 verändert keine Personen, Aufgaben,
Materialien, Kosten, Rollen oder Lage-Snapshots.

## 3. Freie Erinnerung zuerst

Der erste inhaltliche Bereich heißt **„Freie Erinnerung“**. Vor detaillierten
Fragen steht:

> Beschreibe zunächst in deinen eigenen Worten, was passiert ist.
> Du musst nicht alles wissen oder chronologisch erinnern.

Keine Detailfragen oder Beispielmaßnahmen vor dieser ersten eigenen Schilderung.
Ein vorgeschaltetes lokales Berichtslabel darf der Zuordnung dienen, ohne
Erlebnisse vorzugeben. Erst danach folgen die strukturierten Fragen.
Der Fragebogen ist eine Erinnerungsstütze, keine Behauptung über typische oder
rechtlich relevante Mindestmaßnahmen. Abschnitte und Antworten dürfen offen
bleiben; keine Vermutung „Nein“ aus Überspringen und kein Zwang zu exakten Zeiten.
Eigene Wahrnehmung, sinngemäße Wiedergabe, Unsicherheit und Erinnerungslücken
sollen im eigenen Text erkennbar bleiben. Es gibt keine automatischen Aussagen
über Glaubwürdigkeit, Rechtswidrigkeit oder Qualität eines Berichts.

## 4. Antworttypen und Versionierung

**questionnaireVersion = 1**. Die folgenden Typen sind fachliche Antwortarten,
keine Formular-DSL und keine Festlegung technischer Speicherstrukturen.

| Typ | Fachlicher Vertrag |
| --- | --- |
| BOOLEAN_UNCERTAIN | Ja; Nein; Weiß ich nicht mehr; Nicht relevant. Keine Vorauswahl. |
| SINGLE_CHOICE | Eine ausdrücklich gewählte Option aus der angegebenen Liste. |
| MULTI_CHOICE | Mehrere ausdrücklich gewählte Optionen; Reihenfolge der Optionen ist keine Ereignischronologie. |
| SHORT_TEXT | Kurze eigene Angabe, optional wenn nicht anders festgelegt. |
| LONG_TEXT | Eigene Schilderung; auch Erinnerungslücken oder sinngemäße Wiedergabe zulässig. |
| APPROX_DURATION | Ungefähre Dauer: wenige Minuten; ca. 15 min; ca. 30 min; ca. 1 h; ca. 2 h; mehrere Stunden; weiß ich nicht. Optional eigene ungefähre Angabe. Keine Umwandlung in angeblich exakte Dauer. |
| APPROX_TIME | Ungefähre lokale Zeit oder weiß ich nicht; keine Pflicht zu genauer Uhrzeit, kein automatisches Einsetzen der aktuellen Zeit. Kontext bei Erinnerung über mehrere Tage darf in eigenen Worten angegeben werden. |
| OPTIONAL_COUNT | Optional erinnerte nichtnegative ganze Anzahl; leer/weiß ich nicht ist nicht 0. Keine Schätzung erzwingen. |

Unbeantwortet, ausdrücklich „Nein“, Unsicherheit und „Nicht relevant“ sind
unterschiedliche Bedeutungen. Bei MULTI_CHOICE sind „unklar“/„unbekannt“/
„weiß ich nicht“ alternative Antworten zu konkreten Optionen;
„keine besondere Einschätzung“ ist alternativ zu den subjektiven Bewertungen.
„Anderes“ erlaubt eigene Erläuterung im zugehörigen Detailtext, kein neuer Katalog.
Auch Auswahlfragen dürfen unbeantwortet bleiben.

Jede Frage hat einen stabilen Key, deutsche Formulierung, Antworttyp und gegebenenfalls
Hinweis/Detailabhängigkeit. Ein Key wird nicht für eine andere Bedeutung wiederverwendet.
Spätere Text-/Optionsänderungen benötigen eine zuordenbare Fragebogenversion;
alte Berichte müssen mit ihrer damaligen Bedeutung lesbar bleiben. Stable Key
und questionnaireVersion allein ersetzen nicht den künftig vorzuhaltenden
versionierten Fragekatalog. P9b muss dessen historische Darstellung festlegen.

„Bei Ja“ in den Tabellen bedeutet eine angebotene abhängige Detailfrage, keine
Antwortpflicht. Unbekanntes darf nicht zu einer Ja-Antwort gezwungen werden, um
eine Erinnerung zu schildern; dafür bleiben Freie Erinnerung und ergänzende
Schilderungen verfügbar. Bereits gespeicherte Details dürfen bei geändertem
Elternwert nicht still verloren gehen oder unsichtbar als gültige Antwort stehen
bleiben: vor Speichern bewusst prüfen/behalten oder verwerfen; Zusammenfassung
zeigt verbleibende Details mit ihrem Antwortkontext. P9b konkretisiert diesen Ablauf.
Endliche Text-/Zählgrenzen und technische Antwortkodierung sind vor Implementierung
in P9b festzulegen; P9a gibt keine unbegrenzte Speicherung oder stille Kürzung frei.

## 5. Questionnaire Version 1

Die Reihenfolge A–P folgt auf die erste freie Schilderung; Q ist optional.
Alle Fragen außer authorLabel sind optional, einschließlich A_FREE_NARRATIVE.
Leere Antworten schließen einen bewussten Berichtsabschluss nicht aus.

### A — Freie Erinnerung

| Stable Key | Typ | Sichtbare Formulierung / Hinweis |
| --- | --- | --- |
| A_FREE_NARRATIVE | LONG_TEXT | Beschreibe in deinen eigenen Worten, was passiert ist. Eigene Wahrnehmung; Erinnerung darf unvollständig sein; ungefähre Reihenfolge genügt. |
| A_ALLEGATION | SHORT_TEXT | Welcher Vorwurf wurde dir genannt? Alternativ zum Text: „nicht genannt“ oder „weiß ich nicht mehr“. Kein Straftatenkatalog; keine eigene rechtliche Einordnung. |

Die Alternativen bei A_ALLEGATION sind ausdrückliche Antwortzustände dieser
Frage, keine frei erfundene Aussage und kein zusätzlicher Fachfragebogen.

### B — Auflösung / Ende der Teilnahme

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| B_ENDING_MODE | MULTI_CHOICE | Wie endete deine Teilnahme? Selbst gelöst / selbst gegangen; nach Aufforderung selbst gelöst; durch Polizei gelöst / entfernt; weggetragen; anderes; unklar. Mehrere Schritte können aufeinander gefolgt sein. |
| B_AGREEMENT_OR_CONDITION | BOOLEAN_UNCERTAIN | Gab es Absprachen, Zusagen oder Bedingungen, die beeinflusst haben, ob oder wie du dich gelöst / die Aktion verlassen hast? |
| B_AGREEMENT_DETAILS | LONG_TEXT | Welche Absprachen, Zusagen oder Bedingungen erinnerst du? Bei Ja zu B_AGREEMENT_OR_CONDITION. |
| B_THREATS_OR_PRESSURE | BOOLEAN_UNCERTAIN | Wurden Maßnahmen oder Nachteile angekündigt, falls du dich nicht entsprechend verhältst? |
| B_THREATS_DETAILS | LONG_TEXT | Was wurde angekündigt oder gesagt? Bei Ja zu B_THREATS_OR_PRESSURE. |

Erst bei der Detailfrage angebotene Erinnerungshilfen zu B_AGREEMENT_DETAILS:
Freilassung, Identitätsfeststellung, Behandlung anderer Personen, Soli-Kundgebung,
angekündigte Maßnahmen. Keine Aussage, dass solche Absprachen stattgefunden haben
oder rechtlich wirksam waren.

### C — Warten / weiterer Ablauf

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| C_WAIT_AFTER_END | BOOLEAN_UNCERTAIN | Musstest du nach dem Ende deiner Teilnahme warten? |
| C_WAIT_DURATION | APPROX_DURATION | Wie lange musstest du ungefähr warten? Bei Ja zu C_WAIT_AFTER_END. |
| C_WAIT_PLACE | SHORT_TEXT | Wo musstest du warten? Bei Ja zu C_WAIT_AFTER_END. |
| C_NEXT_STEP_UNCLEAR | BOOLEAN_UNCERTAIN | War unklar, wohin du gebracht wirst oder wie lange es weitergeht? |
| C_WAIT_CONDITIONS | LONG_TEXT | Unter welchen Bedingungen hast du gewartet? Bei Ja zu C_WAIT_AFTER_END. |

### D — Foto / Video

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| D_POLICE_RECORDING_OBSERVED | BOOLEAN_UNCERTAIN | Hast du wahrgenommen, dass Polizei Fotos oder Videos angefertigt hat? |
| D_RECORDING_DEVICE | MULTI_CHOICE | Womit wurden die Aufnahmen angefertigt? Dienstkamera / erkennbare Kamera; Mobiltelefon; Videokamera; unbekannt; anderes. Bei Ja zu D_POLICE_RECORDING_OBSERVED. |
| D_PERSONALLY_PHOTOGRAPHED | BOOLEAN_UNCERTAIN | Wurdest du selbst fotografiert? |
| D_PHOTO_OBJECTED_OR_REFUSED | BOOLEAN_UNCERTAIN | Hast du einer Aufnahme widersprochen oder sie verweigert? |
| D_PHOTO_RESULT | LONG_TEXT | Was geschah danach? Bei Ja zu D_PHOTO_OBJECTED_OR_REFUSED. |

Es geht um wahrgenommene Aufnahmen, keine Aufnahme-/Uploadfunktion der App und
keine Bewertung ihrer Zulässigkeit.

### E — Identitätsfeststellung

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| E_ID_CHECK | BOOLEAN_UNCERTAIN | Wurde deine Identität festgestellt oder aufgenommen? |
| E_ID_DATA | MULTI_CHOICE | Welche Art von Angaben wurde aufgenommen oder verwendet? Ausweisdokument; Personalien mündlich; Adresse; sonstige Angaben; weiß ich nicht. Bei Ja zu E_ID_CHECK. |
| E_ID_DURATION | APPROX_DURATION | Wie lange dauerte das ungefähr? Bei Ja zu E_ID_CHECK. |
| E_ID_PROCESS_DETAILS | LONG_TEXT | Wie lief die Identitätsfeststellung ab? Bei Ja zu E_ID_CHECK. |

E_ID_DATA dokumentiert Arten von Angaben; keine Felder zum erneuten Erfassen
von tatsächlichem Namen, Adresse oder Ausweisnummer. Keine P3b-Übernahme.

### F — Fragen / Aussage

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| F_QUESTIONS_ASKED | BOOLEAN_UNCERTAIN | Wurden dir Fragen gestellt? |
| F_QUESTIONS_DETAILS | LONG_TEXT | Welche Fragen wurden dir gestellt? Wenn möglich sinngemäß oder wörtlich. Bei Ja zu F_QUESTIONS_ASKED; unsicheren Wortlaut als solchen beschreiben. |
| F_RESPONSE_MODE | SINGLE_CHOICE | Wie hast du auf Fragen reagiert? Keine Angaben gemacht; einzelne Fragen beantwortet; weitgehend beantwortet; Aussage ausdrücklich verweigert; weiß ich nicht mehr. Bei Ja zu F_QUESTIONS_ASKED. |
| F_PRESSURE_TO_ANSWER | BOOLEAN_UNCERTAIN | Hast du Druck wahrgenommen, Fragen zu beantworten? |
| F_PRESSURE_DETAILS | LONG_TEXT | Was wurde gesagt oder getan, das du als Druck wahrgenommen hast? Bei Ja zu F_PRESSURE_TO_ANSWER. |

### G — Sensible / auffällige Fragen

Einleitung:

> Hier geht es darum, welche Fragen dir gestellt wurden.
> Du musst keine eigenen Gesundheitsdaten oder Diagnosen angeben.

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| G_SENSITIVE_QUESTIONS | BOOLEAN_UNCERTAIN | Wurden dir Fragen zu Gesundheit, Krankheiten, Medikamenten, Sexualität oder ähnlichen persönlichen Themen gestellt? |
| G_SENSITIVE_TOPICS | MULTI_CHOICE | Zu welchen Themen wurden Fragen gestellt? Allgemeine Gesundheit / Erkrankungen; Medikamente; HIV; Hepatitis; andere Infektionskrankheiten; Schwangerschaft; Sexualität / sexuelle Gesundheit; psychische Gesundheit; Behinderung; Substanzkonsum; anderes; weiß ich nicht mehr. Bei Ja zu G_SENSITIVE_QUESTIONS. |
| G_SENSITIVE_WORDING | LONG_TEXT | Was wurde möglichst genau gefragt? Bei Ja zu G_SENSITIVE_QUESTIONS; eigene Antworten oder Diagnosen sind hierfür nicht erforderlich. |
| G_SENSITIVE_EXPERIENCE | MULTI_CHOICE | Wie hast du diese Fragen persönlich wahrgenommen? Ungewöhnlich; unangemessen; diskriminierend; entwürdigend; einschüchternd; anderes; keine besondere Einschätzung. Optional bei Ja zu G_SENSITIVE_QUESTIONS. |

Die Themenauswahl dokumentiert **die gestellte Frage**, niemals das Vorliegen
eines Gesundheitszustands. Beispielsweise bedeutet „HIV“ ausschließlich eine
erinnerte Frage zu diesem Thema. Keine Diagnosen, Gesundheitsprofile oder
Schlussfolgerungen aus Auswahlwerten. G_SENSITIVE_EXPERIENCE ist ausdrücklich
subjektive Wahrnehmung, keine rechtliche Feststellung.

### H — Durchsuchung / Körper / Sachen

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| H_PERSON_SEARCH | BOOLEAN_UNCERTAIN | Wurdest du durchsucht? |
| H_SEARCH_ACTIONS | MULTI_CHOICE | Was geschah bei der Durchsuchung? Abgetastet; Taschen/Kleidung durchsucht; Schuhe ausziehen; weitere Kleidung ablegen; sonstiges. Bei Ja zu H_PERSON_SEARCH. |
| H_BAG_SEARCH | BOOLEAN_UNCERTAIN | Wurde deine Tasche oder dein Gepäck durchsucht? |
| H_ITEMS_TAKEN | BOOLEAN_UNCERTAIN | Wurden Gegenstände sichergestellt, beschlagnahmt oder dir sonst abgenommen? |
| H_ITEMS_DETAILS | LONG_TEXT | Welche Gegenstände wurden dir abgenommen und wie lief das ab? Bei Ja zu H_ITEMS_TAKEN; unnötige persönliche Identifikatoren vermeiden. |
| H_REASON_GIVEN | BOOLEAN_UNCERTAIN | Wurde dir dafür eine Begründung genannt? Bei Ja zu H_ITEMS_TAKEN. |
| H_REASON_TEXT | LONG_TEXT | Welche Begründung wurde dir genannt? Bei Ja zu H_REASON_GIVEN; wiedergeben, was gesagt wurde. |
| H_RECEIPT_RECEIVED | BOOLEAN_UNCERTAIN | Hast du dafür ein Protokoll, eine Bescheinigung oder andere Unterlagen erhalten? Bei Ja zu H_ITEMS_TAKEN. |
| H_ITEMS_RETURNED_AND_RETAKEN | BOOLEAN_UNCERTAIN | Wurden Gegenstände erst zurückgegeben und später erneut abgenommen? |

Die Frage zu abgenommenen Gegenständen verlangt keine eigene rechtliche
Unterscheidung zwischen den genannten Maßnahmen.

### I — Transport / Fahrzeug / Zelle

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| I_TRANSPORTED | BOOLEAN_UNCERTAIN | Wurdest du transportiert? |
| I_TRANSPORT_TYPE | MULTI_CHOICE | Wie wurdest du transportiert? Polizeifahrzeug; Zellenfahrzeug / Fahrzeugzelle; anderes; unklar. Bei Ja zu I_TRANSPORTED. |
| I_TRANSPORT_WAIT_DURATION | APPROX_DURATION | Wie lange musstest du im Zusammenhang mit dem Transport ungefähr warten? Bei Ja zu I_TRANSPORTED. |
| I_TRANSPORT_WITH_OTHERS | BOOLEAN_UNCERTAIN | Wurdest du gemeinsam mit anderen Personen transportiert? Bei Ja zu I_TRANSPORTED; keine Personenliste erforderlich. |
| I_CONDITIONS | MULTI_CHOICE | Welche Bedingungen hast du wahrgenommen? Schlechte Belüftung; starke Hitze; starke Kälte; Enge; lange Wartezeit; eingeschränkte Bewegungsmöglichkeit; anderes. Bei Ja zu I_TRANSPORTED. |
| I_CONDITIONS_DETAILS | LONG_TEXT | Was erinnerst du zu diesen Bedingungen? Bei Ja zu I_TRANSPORTED. |

### J — Grundbedürfnisse / Versorgung

Alle fünf Versorgungsfragen sind SINGLE_CHOICE mit denselben Optionen:
**verfügbar; auf Anfrage erhalten; verzögert; verweigert; nicht benötigt;
weiß ich nicht / nicht relevant**. Keine Vorauswahl, keine automatische
Ableitung aus anderen Antworten. Die letzte Option ist die ausdrücklich
kombinierte Antwort dieses Abschnitts. Die Auswahl behauptet keine rechtliche
Mindestversorgung. Bei mehreren erlebten Phasen kann die Person die am besten
passende Antwort wählen und den Verlauf optional im Detail schildern.

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| J_WATER | SINGLE_CHOICE | Wie war der Zugang zu Wasser? |
| J_WATER_DETAILS | LONG_TEXT | Was erinnerst du zum Zugang zu Wasser? Optional bei einer relevanten Antwort zu J_WATER. |
| J_TOILET | SINGLE_CHOICE | Wie war der Zugang zu einer Toilette? |
| J_TOILET_DETAILS | LONG_TEXT | Was erinnerst du zum Zugang zur Toilette? Optional bei einer relevanten Antwort zu J_TOILET. |
| J_FOOD | SINGLE_CHOICE | Wie war der Zugang zu Essen? |
| J_FOOD_DETAILS | LONG_TEXT | Was erinnerst du zum Zugang zu Essen? Optional bei einer relevanten Antwort zu J_FOOD. |
| J_MEDICAL_HELP | SINGLE_CHOICE | Wie war der Zugang zu medizinischer Hilfe? |
| J_MEDICAL_HELP_DETAILS | LONG_TEXT | Was erinnerst du zum Zugang zu medizinischer Hilfe? Optional bei einer relevanten Antwort; keine Diagnose erforderlich. |
| J_HEAT_COLD_PROTECTION | SINGLE_CHOICE | Wie war der Zugang zu Schutz vor Hitze oder Kälte? |
| J_HEAT_COLD_PROTECTION_DETAILS | LONG_TEXT | Was erinnerst du zum Schutz vor Hitze oder Kälte? Optional bei einer relevanten Antwort. |
| J_TOILET_ESCORTED | BOOLEAN_UNCERTAIN | Wurdest du beim Toilettengang begleitet? Nur wenn du eine Toilette genutzt hast; Nutzung nicht aus „verfügbar“ ableiten. |

„Relevant“ bezeichnet hier verfügbar, auf Anfrage erhalten, verzögert oder
verweigert. Auch sonstige Unsicherheit kann in der freien Schilderung stehen.

### K — Gesa / Gewahrsam

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| K_CUSTODY | BOOLEAN_UNCERTAIN | Warst du in einer Gesa oder einem anderen Gewahrsamsbereich? |
| K_WAIT_BEFORE_PROCESSING | BOOLEAN_UNCERTAIN | Musstest du dort vor der Bearbeitung warten? Bei Ja zu K_CUSTODY. |
| K_WAIT_DURATION | APPROX_DURATION | Wie lange musstest du dort ungefähr warten? Bei Ja zu K_WAIT_BEFORE_PROCESSING. |
| K_WAIT_CONDITIONS | LONG_TEXT | Unter welchen Bedingungen hast du dort gewartet? Bei Ja zu K_WAIT_BEFORE_PROCESSING. |
| K_SEARCH_AT_CUSTODY | BOOLEAN_UNCERTAIN | Wurdest du im Gewahrsamsbereich durchsucht? Bei Ja zu K_CUSTODY. |
| K_CUSTODY_DETAILS | LONG_TEXT | Was erinnerst du sonst zum Aufenthalt im Gewahrsamsbereich? Bei Ja zu K_CUSTODY. |

### L — ED-Behandlung

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| L_ED | BOOLEAN_UNCERTAIN | Wurde eine erkennungsdienstliche Behandlung durchgeführt? |
| L_ED_MEASURES | MULTI_CHOICE | Welche Maßnahmen erinnerst du? Gesichtsfotos; Ganzkörperfoto; weitere Fotos; Körpergröße gemessen; Fingerabdrücke digital; Fingerabdrücke analog; Handflächen digital; Handflächen analog; sonstige biometrische / körperliche Merkmale; unklar. Bei Ja zu L_ED. |
| L_PHOTO_COUNT | OPTIONAL_COUNT | Wie viele Fotos wurden ungefähr angefertigt, soweit du dich an eine Anzahl erinnerst? Optional bei erinnerter Fotoaufnahme im Rahmen von L_ED. |
| L_ED_DETAILS | LONG_TEXT | Wie lief die erkennungsdienstliche Behandlung ab? Bei Ja zu L_ED. |
| L_ED_OBJECTED | BOOLEAN_UNCERTAIN | Hast du einer dieser Maßnahmen widersprochen oder etwas verweigert? Bei Ja zu L_ED. |
| L_ED_OBJECTED_DETAILS | LONG_TEXT | Welcher Maßnahme hast du widersprochen oder was hast du verweigert? Was geschah danach? Bei Ja zu L_ED_OBJECTED. |

Die Auswahl beschreibt erinnerte Maßnahmen. Sie erfasst keine Fingerabdrücke,
Fotos, Körpermaße oder sonstigen biometrischen Daten selbst.

### M — Begründungen / Rechtsgrundlage

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| M_REASON_OR_LEGAL_BASIS_GIVEN | BOOLEAN_UNCERTAIN | Wurde dir für eine Maßnahme eine Begründung oder Rechtsgrundlage genannt? |
| M_REASON_OR_LEGAL_BASIS_TEXT | LONG_TEXT | Welche Begründung oder Rechtsgrundlage wurde dir für welche Maßnahme genannt? Bei Ja zu M_REASON_OR_LEGAL_BASIS_GIVEN. |

Hinweis: „Bitte möglichst wiedergeben, was dir gesagt wurde. Die App bewertet
die rechtliche Richtigkeit nicht.“ Kein Rechtsgrundlagenkatalog und keine Empfehlung.

### N — Unterlagen / Unterschriften

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| N_DOCUMENTS_RECEIVED | BOOLEAN_UNCERTAIN | Hast du Unterlagen erhalten? |
| N_DOCUMENTS_DETAILS | LONG_TEXT | Welche Unterlagen hast du erhalten? Bei Ja zu N_DOCUMENTS_RECEIVED. |
| N_SIGNED_ANYTHING | BOOLEAN_UNCERTAIN | Hast du etwas unterschrieben? |
| N_SIGNED_DETAILS | LONG_TEXT | Was hast du unterschrieben und unter welchen Umständen? Bei Ja zu N_SIGNED_ANYTHING. |
| N_REFUSED_SIGNATURE | BOOLEAN_UNCERTAIN | Hast du eine Unterschrift verweigert? |
| N_COPIES_RECEIVED | BOOLEAN_UNCERTAIN | Hast du Kopien der Unterlagen erhalten? |

Keine Datei-/Fotoanhänge in P9 v1. Die Frage nach einer früheren Unterschrift
ist keine Unterschriftenfunktion oder Signierung des App-Berichts.

### O — Entlassung

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| O_RELEASED | BOOLEAN_UNCERTAIN | Wurdest du entlassen? |
| O_RELEASE_TIME | APPROX_TIME | Wann wurdest du ungefähr entlassen? Bei Ja zu O_RELEASED. |
| O_ITEMS_RETURNED | BOOLEAN_UNCERTAIN | Wurden dir bei der Entlassung deine Gegenstände zurückgegeben? Bei Ja zu O_RELEASED. |
| O_CONDITIONS_OR_INSTRUCTIONS | BOOLEAN_UNCERTAIN | Gab es bei der Entlassung Auflagen, Hinweise oder weitere Maßnahmen? Bei Ja zu O_RELEASED. |
| O_RELEASE_DETAILS | LONG_TEXT | Was erinnerst du zur Entlassung, zu genannten Auflagen, Hinweisen oder weiteren Maßnahmen? Bei Ja zu O_RELEASED. |

Ein während ACTIVE begonnener oder abgeschlossener Bericht setzt keine bereits
erfolgte Entlassung voraus; Berichtsabschluss setzt O_RELEASED nicht automatisch.

### P — Auffälliges / Seltsames

| Stable Key | Typ | Sichtbare Formulierung / Abhängigkeit |
| --- | --- | --- |
| P_ANYTHING_UNUSUAL | BOOLEAN_UNCERTAIN | Ist dir etwas seltsam, widersprüchlich, unangemessen oder sonst auffällig vorgekommen? |
| P_UNUSUAL_CATEGORIES | MULTI_CHOICE | Wie hast du es persönlich wahrgenommen? Widersprüchlich; ungewöhnlich; einschüchternd; entwürdigend; diskriminierend; körperlich belastend; gesundheitlich belastend; auffällig im Umgang mit anderen Personen; anderes. Bei Ja zu P_ANYTHING_UNUSUAL. |
| P_UNUSUAL_DETAILS | LONG_TEXT | Was kam dir seltsam, widersprüchlich, unangemessen oder sonst auffällig vor? Bei Ja zu P_ANYTHING_UNUSUAL. |

Subjektive Wahrnehmung, keine Rechtsbewertung oder automatisch festgestellte
Diskriminierung. Auswahl ist eine Erinnerungsstütze, keine behauptete Erfahrung.

### Q — Beobachtungen zu anderen (optional)

| Stable Key | Typ | Sichtbare Formulierung / Hinweis |
| --- | --- | --- |
| Q_OBSERVATIONS_OF_OTHERS | LONG_TEXT | Hast du etwas Relevantes bei anderen Personen beobachtet? Wenn möglich keine unnötigen Klarnamen; Pseudonyme / allgemeine Beschreibung bevorzugen. Keine fremden Gesundheitsinformationen sammeln, wenn nicht erforderlich. |

Keine strukturierte fremde Personenakte, Teilnehmerliste oder Personenrelation.

## 6. Berichtsidentität und fachlicher Lifecycle

Jeder Bericht gehört genau einer Aktion und hat eine lokale Identität. Als
lokales Berichtslabel wird einheitlich **authorLabel** verwendet: trim,
Pflicht 1..80 Unicode-Codepoints; beispielsweise das synthetische Kürzel „Motte“.
Es identifiziert die berichtende Perspektive, kein verifiziertes Autorenprofil.
P3a-Pseudonym darf als optionale Eingabehilfe in authorLabel kopiert werden.
Externe oder nicht als Teilnehmer gespeicherte Personen dürfen ein Kürzel nutzen.
Kein persistierter Person-FK, keine Person-ID im Label und kein globales Register.
Personenrename und Personenlöschung ändern Bericht/Label nicht. Label und
Freitext sind eigenständiger sensibler P9-Inhalt; spätere Lösch-/Freitextprüfung
muss sie berücksichtigen. Pseudonyme sind nicht automatisch anonym.

| Aktionsstatus | P9-Produktzugriff |
| --- | --- |
| DRAFT | Kein Zugriff |
| PREPARATION | Kein Zugriff |
| ACTIVE | Berichte bewusst beginnen, gespeicherte Entwürfe fortsetzen, abschließen, lesen und abgeschlossene Berichte ergänzen |
| COMPLETED | Dieselbe P9-Arbeit nach bewusstem Einstieg; Aktion bleibt abgeschlossen |
| DELETION_PENDING | Kein normaler P9-Read/Write |

Gedächtnisprotokolle beziehen sich auf tatsächlich Erlebtes im Zusammenhang
mit einer laufenden oder abgeschlossenen Aktion. **Berichtsstatus** ist separat
vom Aktionsstatus: DRAFT (Entwurf) oder COMPLETED (abgeschlossener Bericht).
Ein ACTIVE begonnener, ausdrücklich gespeicherter Berichtsentwurf darf nach
Aktionsabschluss bewusst erneut geöffnet und fortgesetzt werden. Ein laufender
ungespeicherter Editor wird nicht still über einen Phasenwechsel getragen.
P9b muss Status-/Session-/Aktionsbindung und den sicheren persistierten
Fortsetzungsvertrag spezifizieren; kein Action-Reopen und keine allgemeine
COMPLETED-Schreibfreigabe für P4/P5a/P7/Aktionsdaten.

## 7. Bewusst gespeicherter persistenter Entwurf

Produktentscheidung: verschlüsselte persistente Speicherung des fachlichen
Entwurfs, abschnittsweise ausschließlich über **„Speichern und weiter“**.
Lange Berichte können die absolute Drei-Minuten-Session überschreiten.
Dies ist eine enge P9-Ausnahme zur bisherigen RAM-only-Editorregel aus UX0,
keine Persistenz von Navigation, Authentifizierung, Keys oder Sessionfreigaben.
Kein Background-Autosave erforderlich oder durch P9a freigegeben.
Bloßes Öffnen, Scrollen, Refresh oder Wechseln speichert nicht automatisch.

Nach erfolgreich bestätigter Speicherung übersteht der Abschnitt Lock und
Prozessende als verschlüsselter Fachinhalt. Die aktuelle ungespeicherte Eingabe
darf bei Lock verloren gehen; die Oberfläche muss diese Grenze verständlich
erklären. Kein Erfolg für einen noch unbestätigten Speichervorgang vortäuschen.
Ein verlorener Callback beweist weder Commit noch Rollback; nach Reauth den
tatsächlichen gespeicherten Bestand bewusst neu lesen, nicht blind wiederholen.

Lock entfernt erreichbare Klartextanzeigen, ungespeicherte Eingaben und den
flüchtigen Navigationskontext. Gespeicherte Abschnitte werden dadurch nicht
gelöscht. Nach Reauth kein automatisches Wiederöffnen; der Nutzer wählt den
Entwurf bewusst erneut. Absolute 180 Sekunden und 30 Sekunden READY-Inaktivität
bleiben unverändert. Hintergrundarbeit, Load-/Save-Ergebnisse und Displaytimer
sind keine Benutzerinteraktion und verlängern keine Sitzung. Keine RAM-
Überschreibungsgarantie. Sichere Speicherung, Konflikte, Atomarität, Commit-/
Publicationgates und Wiederanlauf sind noch in P9b zu spezifizieren und zu testen.

## 8. Abschließen und spätere Ergänzungen

Nach einer lesbaren Zusammenfassung folgt die bewusste Aktion
**„Gedächtnisprotokoll abschließen“**. Zusammenfassung unterscheidet eigene
Angaben, ungefähre Zeiten, Unsicherheit, nicht relevante und unbeantwortete
Fragen. Kein Vollständigkeitsscore, keine Pflicht zur Beantwortung aller Fragen.

Nach Abschluss kein normales Editieren oder stilles Überschreiben der bisherigen
Antworten oder des Berichtslabels. Später erinnerte Information kommt über
**„Ergänzung hinzufügen“** als append-only Freitext hinzu. Eine Ergänzung enthält
Inhalt und später repositorygeneriertes recordedAt; stabile Anfügereihenfolge,
nicht allein Sortierung nach verstellbarer Gerätezeit. Alte Antworten bleiben
erhalten, auch wenn eine Ergänzung eine frühere Erinnerung berichtigt.
Keine vollständige P6-Revisionsmaschine, keine Änderung alter Antworten und
zunächst keine Löschung einzelner Ergänzungen im normalen Produktpfad.

recordedAt bezeichnet die tatsächliche appseitige Erfassung, keine erinnerte
Ereigniszeit oder externe Zeitbeglaubigung; nie vom Nutzer editierbar.
Technische Metadaten/Abschlussatomarität werden erst P9b festgelegt.
Append-only im normalen Produktpfad verhindert nicht spätere erforderliche
Privacy-/Retentionlöschung. Ob ein abgeschlossener Bericht manuell als Ganzes
gelöscht werden darf, bleibt ausdrücklich offen.

## 9. Privacy, Security und Retentiongrenze

P9 ist **SENSITIVE**: persönliche Berichte, Labels und Antworten können
Gesundheits-, Sexualitäts- oder andere vertrauliche Bezüge enthalten.
Normale P9-Arbeit in gültigem READY soll analog P6 ohne zusätzlichen
Leadership-Step-up möglich sein, damit direkte Erfassung nach Entlassung
praktisch bleibt. Dies ist eine enge P9-Zugriffsentscheidung, keine Herabstufung
und keine allgemeine Öffnung von P3b. Ein Zugriff in offener Sitzung kann den
Bericht offenlegen; Verschlüsselung ruhender Daten beseitigt dieses Risiko nicht.

Kein automatischer P3b-Read und keine Übernahme von Vollname, Telefonnummer,
Geburtsdatum oder Notfallkontakt. Gesundheits-/Sexualitätsfragen dokumentieren,
**was gefragt wurde**, nicht unnötig den eigenen Gesundheitszustand. Eigene
und fremde intime Angaben sollen nicht für den Fragebogen zusätzlich erhoben
werden. Freitext kann dennoch sensible Angaben enthalten; keine automatische
Erkennung oder Anonymisierung versprechen. Keine Berichtsinhalte/Labels in
Logs, Fehlermeldungen, Notifications oder außen sichtbaren Vorschauen.

Bestehende Session-/Storage-Gates, Auth, Keyhierarchie, LeadershipGrant, P3b-Crypto,
SQLCipher, Backup-/Transferregeln und FLAG_SECURE bleiben unverändert.
Keine neue Kryptografie, Permission, Dependency oder Securityarchitektur.
Gespeicherte Entwürfe gehören in den bestehenden geschützten Produktbestand;
keine Klartextdateien oder SavedState-Kopien. Konkrete technische Abbildung erst P9b.
Externe Tastaturen und bereits offengelegte Kopien bleiben bestehende Schutzgrenzen.

Keine konkrete Retentiondauer oder Retentionengine durch P9a. Entwürfe,
abgeschlossene Antworten, Labels, Ergänzungen und kontrollierte Ableitungen
bleiben aktionsgebunden; vollständige Aktionslöschung ist die Obergrenze.
Kein unabhängiger Restbestand. Fristen/Fristanker und mögliche rechtliche
Aufbewahrungsbedarfe müssen fachkundig geprüft werden. Speichern oder Ergänzen
gibt keine automatische Fristverlängerung frei. Personenlöschung bereinigt
P9-Freitext/authorLabel nicht automatisch; allgemeiner Lösch-/Freitextworkflow
muss P9 ausdrücklich berücksichtigen. Keine physische Überschreibgarantie.

## 10. UI-Ziel, noch keine Umsetzung

ACTIVE-Hubkarte **„Gedächtnisprotokolle“** zeigt ausschließlich Anzahl
abgeschlossener Berichte und Anzahl Entwürfe. Keine authorLabels, Antworten
oder Freitextvorschau; keine Detailprefetches zur Countanzeige.
COMPLETED erhält einen bewussten Einstieg **„Gedächtnisprotokolle“**;
die normale abgeschlossene Aktionsseite lädt keine P9-Inhalte oder P9-Summary vor.
Keine Karte oder Schaltfläche im aktuellen Produkt durch P9a.

Grobe künftige Wege im bestehenden Workspace-Muster:

- Gedächtnisprotokolle → Entwurf fortsetzen / Neues Gedächtnisprotokoll /
  Abgeschlossene Berichte.
- Bericht → Freie Erinnerung → strukturierte Abschnitte → Zusammenfassung →
  Abschließen; nach Abschluss lesen oder Ergänzung hinzufügen.
- Nach Lock/Reauth normaler Produktpfad; bewusster erneuter Einstieg.

Die Oberfläche unterscheidet gespeichert/ungespeichert, Entwurf/abgeschlossen
und Aktionsphase verständlich als Text. Große Schrift, mindestens bestehende
48dp-Touchziele, verständliche Auswahl-/Heading-/Disclosuresemantik, erreichbare
Save-/Backcontrols und vorhersehbarer Tastatur-/Fokusablauf bleiben UX-Ziele.
Kein detailliertes UI-Design, keine Accessibilityabnahme durch dieses Dokument.

## 11. Scopegrenzen und offene Fragen

Nicht freigegeben: Fotos, Audio, Video, Dokumentupload, Datei-/Medienanhänge,
PDF, Export, Sharing, appseitige Unterschriften/Signaturen, Hashchain, Trusted
Timestamp, Integritäts- oder Verwertbarkeitsgarantien, Rechtsbewertung,
automatische Legalitätsbewertung, juristische Handlungsempfehlungen,
globale Polizei-/Personendatenbanken, Person-FK für authorLabel, automatische
P6-Verknüpfung oder P3b-Übernahme, Netzwerk, Notifications und Retentionworker.
Fragen über solche Erlebnisse geben entsprechende Appfunktionen nicht frei.

Offen für spätere gesonderte Arbeit:

- Konkrete Retentiondauer/Fristanker und allgemeiner Löschworkflow.
- Ob abgeschlossene P9-Berichte manuell als Ganzes gelöscht werden dürfen.
- Spätere Dokument-/Dateianhänge oder Export; keine Freigabe in P9 v1.
- Juristische/fachkundige Prüfung des finalen Fragebogens vor Echtdatennutzung.
- Menschliche Accessibility-/TalkBack-Abnahme.
- Weitere Fragen nach Praxiserfahrung, ausschließlich versioniert.
- P9b: sicherer persistierter Draftvertrag, Antwortkodierung und endliche
  Text-/Zählgrenzen, historische Darstellung, Konflikte und geschützte
  Speicher-/Abschluss-/Ergänzungsabläufe. Keine Tabellen- oder Schemanummer
  durch P9a festgelegt.

## 12. Selbstprüfung und spätere Abnahme

P9a-Abnahme ist Dokumentationsreview: Baseline, Verweise, eindeutige Fragekeys,
Antworttypen/Optionen/Abhängigkeiten, freie Erinnerung zuerst, klare P6-/Note-
Abgrenzung, keine Gesundheitsdiagnosen aus Themenauswahl, kein Person-FK und
ehrliche Entwurfs-/Session-/Retentiongrenzen. Ausschließlich Markdownänderungen;
`git diff --check`, Scopeprüfung und technischer Freezevergleich.
Geprüft: 85 eindeutige Fragekeys, alle acht Antworttypen und Abschnitte A–Q;
lokale Dokumentverweise vorhanden, ausschließlich sieben Markdown-Dateien
geändert/neu. SHA-256-Vergleich aller 231 getrackten Nicht-Markdown-Dateien
gegen die saubere Baseline unverändert, einschließlich Schema 9, Kotlin,
Securityimplementierung, Gradle und Manifesten. `git diff --check` ohne Befund.
Keine P9-Unit-, Storage-, UI- oder Gerätetests ausgeführt; keine technische
Implementierung, TalkBack- oder juristische Abnahme behauptet. Kein Commit/Push.

Spätere Solltests, ausschließlich synthetisch: v1-Key-/Versionsdarstellung;
ungeantwortet/Nein/Unsicherheit/Nicht relevant; abhängige Details ohne stillen
Verlust; Labelgrenzen mit Supplementary-Unicode; P3a-Textkopie ohne Person-ID,
Rename/Delete ohne Berichtsmutation; fehlender P3b-Zugriff; alle Aktionsphasen;
bewusstes Abschnittsspeichern/Lock/Prozessende/Reauth ohne automatischen Reopen;
ACTIVE→COMPLETED und DELETION_PENDING; verspätete Commit-/Read-/Publication-
Callbacks; absolute/Idle-Deadlines ohne technische Interaction; Abschluss ohne
Überschreiben, append-only Ergänzungen bei verstellter Wanduhr; Hubprivacy und
COMPLETED-No-Prefetch; keine Mutationen anderer Bereiche; große Schrift/IME/
Semantics und separat menschlicher TalkBack-Check. P9b präzisiert die technischen
Nachweise; Retention-/Löschtests erst nach eigener Spezifikation dieser Funktionen.
