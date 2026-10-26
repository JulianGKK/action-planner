# Beobachtete Kapazitätsfälle

Laufender Messstand. Jeder Maximalwert ist ein tatsächliches diskretes Sample nach Runtimebindung (MAINTENANCE/READY/LOCKED), einschließlich Test-/Frameworkoverhead; kein physischer Bound. Fixturebau ohne Runtime ist separat in cases.csv/phases.csv enthalten. R/T bei Refusal sind angeforderte, nicht konsumierte Kosten. Vollansicht und kompakter Erstkern bleiben getrennt.

| Bestand / Rollen / Events / Textfaktor | Pfad | Ergebnis | R / lokale T Bytes | Prozess: ART / Native Bytes / PSS KiB (beobachtetes Max.) | Raw |
|---|---|---|---|---|---|
| 200/1000 / 100 / 128000 / 15 | core | ZULAESSIG_INITIAL | 3626571 / 15978280 | 6894128 / 15818128 / 150550 | [large100-128k-core-r1](runs/large100-128k-core-r1/events.jsonl) |
| 200/1000 / 100 / 128000 / 15 | core | ZULAESSIG_INITIAL | 3626571 / 15978280 | 7148592 / 15622640 / 150613 | [large100-128k-core-r2](runs/large100-128k-core-r2/events.jsonl) |
| 200/1000 / 100 / 128000 / 15 | full | KONTROLLIERT_VERWEIGERT | 32298068 / 108952520 | 6094952 / 13260944 / 148861 | [large100-128k-full-r1](runs/large100-128k-full-r1/events.jsonl) |
| 200/1000 / 100 / 5000 / 1 | core | ZULAESSIG_INITIAL | 2716851 / 11533000 | 7224680 / 12988416 / 152551 | [large100-core-r1](runs/large100-core-r1/events.jsonl) |
| 200/1000 / 100 / 5000 / 1 | full | KONTROLLIERT_VERWEIGERT | 27054452 / 94026056 | 7111224 / 13390144 / 149199 | [large100-full-r1](runs/large100-full-r1/events.jsonl) |
| 200/1000 / 100 / 5000 / 15 | core | ZULAESSIG_INITIAL | 3626571 / 15978280 | 7379224 / 13891056 / 150151 | [large100-long5k-core-r1](runs/large100-long5k-core-r1/events.jsonl) |
| 200/1000 / 100 / 5000 / 15 | full | KONTROLLIERT_VERWEIGERT | 32298068 / 108952520 | 7275384 / 13240864 / 148207 | [large100-long5k-full-r1](runs/large100-long5k-full-r1/events.jsonl) |
| 200/1000 / 32 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2606307 / 10925816 | 8201464 / 15589776 / 152531 | [large32-128k-core-r1](runs/large32-128k-core-r1/events.jsonl) |
| 200/1000 / 32 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2606307 / 10925816 | 6778936 / 13628848 / 149953 | [large32-128k-core-r2](runs/large32-128k-core-r2/events.jsonl) |
| 200/1000 / 32 / 128000 / 15 | full | KONTROLLIERT_VERWEIGERT | 30657012 / 104425896 | 6265192 / 13287872 / 148904 | [large32-128k-full-r1](runs/large32-128k-full-r1/events.jsonl) |
| 200/1000 / 32 / 5000 / 1 | core | ZULAESSIG_INITIAL | 2039307 / 8399768 | 7223552 / 13417184 / 151131 | [large32-core-r1](runs/large32-core-r1/events.jsonl) |
| 200/1000 / 32 / 5000 / 1 | full | KONTROLLIERT_VERWEIGERT | 25961748 / 90870312 | 6817120 / 13186784 / 148979 | [large32-full-r1](runs/large32-full-r1/events.jsonl) |
| 200/1000 / 32 / 5000 / 15 | core | ZULAESSIG_INITIAL | 2606307 / 10925816 | 7266112 / 13585776 / 150417 | [large32-long5k-core-r1](runs/large32-long5k-core-r1/events.jsonl) |
| 200/1000 / 32 / 5000 / 15 | full | KONTROLLIERT_VERWEIGERT | 30657012 / 104425896 | 7225872 / 13181296 / 147930 | [large32-long5k-full-r1](runs/large32-long5k-full-r1/events.jsonl) |
| 200/1000 / 6 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2212299 / 8979984 | 6676448 / 13594672 / 150614 | [large6-128k-core-r1](runs/large6-128k-core-r1/events.jsonl) |
| 200/1000 / 6 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2212299 / 8979984 | 7141288 / 15474064 / 150786 | [large6-128k-core-r2](runs/large6-128k-core-r2/events.jsonl) |
| 200/1000 / 6 / 128000 / 15 | full | KONTROLLIERT_VERWEIGERT | 30011412 / 102665784 | 5882152 / 13286128 / 148888 | [large6-128k-full-r1](runs/large6-128k-full-r1/events.jsonl) |
| 200/1000 / 6 / 5000 / 1 | core | ZULAESSIG_INITIAL | 1776339 / 7187760 | 7228424 / 12992032 / 149083 | [large6-core-r1](runs/large6-core-r1/events.jsonl) |
| 200/1000 / 6 / 5000 / 1 | full | KONTROLLIERT_VERWEIGERT | 25525812 / 89634360 | 6797568 / 13372912 / 149167 | [large6-full-r1](runs/large6-full-r1/events.jsonl) |
| 200/1000 / 6 / 5000 / 15 | core | ZULAESSIG_INITIAL | 2212299 / 8979984 | 7150488 / 13707680 / 153121 | [large6-long5k-core-r1](runs/large6-long5k-core-r1/events.jsonl) |
| 200/1000 / 6 / 5000 / 15 | full | KONTROLLIERT_VERWEIGERT | 30011412 / 102665784 | 7392136 / 13188384 / 148853 | [large6-long5k-full-r1](runs/large6-long5k-full-r1/events.jsonl) |
| 100/250 / 6 / 5000 / 1 | core | ZULAESSIG_INITIAL | 1776339 / 7187760 | 7220016 / 13490000 / 154935 | [medium-core-r1-retry1](runs/medium-core-r1-retry1/events.jsonl) |
| 100/250 / 6 / 5000 / 1 | core | ZULAESSIG_INITIAL | 1776339 / 7187760 | 7227104 / 13451120 / 151301 | [medium-core-r2](runs/medium-core-r2/events.jsonl) |
| 100/250 / 6 / 5000 / 1 | full | KONTROLLIERT_VERWEIGERT | 9292212 / 30855720 | 6156232 / 13556080 / 151019 | [medium-full-r1-retry3](runs/medium-full-r1-retry3/events.jsonl) |
| 200/1000 / 100 / 128000 / 15 | core | ZULAESSIG_INITIAL | 3626571 / 15978280 | 8251888 / 15763264 / 191634 | [render-large100-functions](runs/render-large100-functions/events.jsonl) |
| 200/1000 / 32 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2606307 / 10925816 | 7150192 / 15596048 / 155076 | [render-large32-functions](runs/render-large32-functions/events.jsonl) |
| 200/1000 / 32 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2606307 / 10925816 | 7126696 / 13583776 / 155483 | [render-large32-people](runs/render-large32-people/events.jsonl) |
| 200/1000 / 32 / 128000 / 15 | core | ZULAESSIG_INITIAL | 2606307 / 10925816 | 7315072 / 15571104 / 154916 | [render-large32-tasks](runs/render-large32-tasks/events.jsonl) |
| 20/40 / 6 / 5000 / 1 | core | KONTROLLIERT_VERWEIGERT | 4544315 / 18259664 | 6601032 / 12942064 / 150725 | [single-long-person-core-r1](runs/single-long-person-core-r1/events.jsonl) |
| 20/40 / 6 / 5000 / 1 | full | KONTROLLIERT_VERWEIGERT | 6425140 / 17080552 | 7070592 / 12935360 / 149959 | [single-long-person-full-r1](runs/single-long-person-full-r1/events.jsonl) |
| 20/40 / 6 / 5000 / 1 | core | ZULAESSIG_INITIAL | 1595155 / 6463024 | 7576520 / 13151632 / 155713 | [small-core-r1](runs/small-core-r1/events.jsonl) |
| 20/40 / 6 / 5000 / 1 | core | ZULAESSIG_INITIAL | 1595155 / 6463024 | 7405504 / 13107520 / 153863 | [small-core-r2](runs/small-core-r2/events.jsonl) |
| 20/40 / 6 / 5000 / 1 | full | ZULAESSIG_INITIAL | 3388560 / 6965988 | 8640192 / 14623968 / 159918 | [small-full-r1-retry1](runs/small-full-r1-retry1/events.jsonl) |
| 20/40 / 6 / 5000 / 1 | full | ZULAESSIG_INITIAL | 3388560 / 6965988 | 8443568 / 13653200 / 161094 | [small-full-r2](runs/small-full-r2/events.jsonl) |
| 20/40 / 6 / 5000 / 256 | core | KONTROLLIERT_VERWEIGERT | 5991115 / 24928144 | 7335424 / 13017952 / 149720 | [unicode-core-r1-retry1](runs/unicode-core-r1-retry1/events.jsonl) |
| 20/40 / 6 / 5000 / 256 | full | KONTROLLIERT_VERWEIGERT | 7336052 / 17609960 | 7341472 / 13626160 / 150702 | [unicode-full-r1](runs/unicode-full-r1/events.jsonl) |
