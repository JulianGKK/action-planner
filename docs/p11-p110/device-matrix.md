# §110 – finale isolierte P77-Fälle

46 positive Fälle auf demselben Produkt-APK `b3bfcdb3fb19ff3b26e1d8b13776e1a86a5c8e6d2a57d640fddcf33448dde010`. Synthetische Authentisierung und Daten. Drei Test-APKs: Build 33 für 23 Fälle; Build 35 für 17 Folgefälle; Build 41 für COMPLETED und fünf P107-Regressionen. Unterschiede sind ausschließlich dokumentierte Testfixtures/Navigation, synthetischer COMPLETED-Testinput und Diagnostik. Fehlfälle aus Build 33/34/35/36 und der unzureichende COMPLETED-Beleg 37 sowie Build 38/39/40 zählen nicht als PASS.

[Identisches Produkt-APK](build35-target-identity.json), [Quellvergleich](build35-source-difference.json), [Install 33](build33-install.json), [Install 35](build35-install.json), [Install 36](build41-install.json), [Produktidentität 36](build41-target-identity.json), [Quellvergleich 36](build41-source-difference.json), [maschinenlesbare Fälle](device-final.json).

| Probe | Familie | Ergebnis / Raw |
|---|---|---|
| active | run | [PASS](device-active-build33.txt) |
| person | run | [PASS](device-person-build33.txt) |
| plan | run | [PASS](device-plan-build33.txt) |
| stale | run | [PASS](device-stale-build33.txt) |
| material | run | [PASS](device-material-build33.txt) |
| expense | run | [PASS](device-expense-build33.txt) |
| preparation | run | [PASS](device-preparation-build33.txt) |
| completed | run | [PASS](device-completed-build33.txt) |
| large | run | [PASS](device-large-build33.txt) |
| lock-detail | run | [PASS](device-lock-detail-build33.txt) |
| lost-detail-main | run | [PASS](device-lost-detail-main-build33.txt) |
| duplicate-detail-main | run | [PASS](device-duplicate-detail-main-build33.txt) |
| unknown-detail | run | [PASS](device-unknown-detail-build33.txt) |
| postcommit-ui-refusal | consumer | [PASS](device-p110-postcommit-ui-refusal-build33.txt) |
| material-input | consumer | [PASS](device-p110-material-input-build33.txt) |
| form-repeat | consumer | [PASS](device-p110-form-repeat-build33.txt) |
| control-r-exact | consumer | [PASS](device-p110-control-r-exact-build33.txt) |
| control-r-over | consumer | [PASS](device-p110-control-r-over-build33.txt) |
| control-t-exact | consumer | [PASS](device-p110-control-t-exact-build33.txt) |
| control-t-over | consumer | [PASS](device-p110-control-t-over-build33.txt) |
| action-revoke | consumer | [PASS](device-p110-action-revoke-build33.txt) |
| protocol-create | consumer | [PASS](device-p110-protocol-create-build33.txt) |
| memory-create | consumer | [PASS](device-p110-memory-create-build33.txt) |
| state-edit | consumer | [PASS](device-p110-state-edit-build35-tail.txt) |
| person-create | consumer | [PASS](device-p110-person-create-build35-tail.txt) |
| task-create | consumer | [PASS](device-p110-task-create-build35-tail.txt) |
| task-delete | consumer | [PASS](device-p110-task-delete-build35-tail.txt) |
| material-create | consumer | [PASS](device-p110-material-create-build35-tail.txt) |
| material-delete | consumer | [PASS](device-p110-material-delete-build35-tail.txt) |
| expense-create | consumer | [PASS](device-p110-expense-create-build35-tail.txt) |
| expense-delete | consumer | [PASS](device-p110-expense-delete-build35-tail.txt) |
| role-create | consumer | [PASS](device-p110-role-create-build35-tail.txt) |
| role-rename | consumer | [PASS](device-p110-role-rename-build35-tail.txt) |
| role-delete | consumer | [PASS](device-p110-role-delete-build35-tail.txt) |
| ui-r-exact | consumer | [PASS](device-p110-ui-r-exact-build35-tail.txt) |
| ui-r-over | consumer | [PASS](device-p110-ui-r-over-build35-tail.txt) |
| ui-t-exact | consumer | [PASS](device-p110-ui-t-exact-build35-tail.txt) |
| ui-t-over | consumer | [PASS](device-p110-ui-t-over-build35-tail.txt) |
| repeat-active | consumer | [PASS](device-p110-repeat-active-build35-tail.txt) |
| repeat-preparation | consumer | [PASS](device-p110-repeat-preparation-build35-tail.txt) |
| repeat-completed | consumer | [PASS](device-p110-repeat-completed-build41.txt) |
| exposed | P107 | [PASS](device-p107-exposed.txt) |
| r-exact | P107 | [PASS](device-p107-r-exact.txt) |
| r-over | P107 | [PASS](device-p107-r-over.txt) |
| t-exact | P107 | [PASS](device-p107-t-exact.txt) |
| t-over | P107 | [PASS](device-p107-t-over.txt) |

`visible_positive=false` bei Delete bezeichnet die geprüfte Abwesenheit des entfernten Elements. `frames=100/1000` zählt Kompositionen, keine physischen OnDrawframes. COMPLETED enthält in dieser Fixture keine Tasks; dort werden Originalpersonenreferenzen und ein lesendes Personendetail 1.000-mal geprüft. Die vollständige Übersicht ist zusätzlich in Build 40 mit 100 sichtbaren Kompositionen belegt; deren 1.000er-Versuch endete am Testtimeout und zählt nicht als positiver 1.000er-Nachweis. Die 32.768 Aliase sind bewusst gehaltene Originalreferenzen, keine nachträglich freigegebenen Payloads.

[Fullview-Regressionen](legacy-build41.log), [Abschlusskontrolle](after-build41.log), [Cleanup](cleanup.json). Geschützte Pakete unverändert; keine eigenen synthetischen Fixtureroots verblieben. Keine physische Lösch-/Speichergrenze oder DeviceCredential-Gesamtabnahme.
