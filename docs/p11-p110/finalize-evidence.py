"""Validate raw case receipts; never infer a PASS from a build or an older product APK."""
from pathlib import Path
import hashlib,json
r=Path(__file__).parent
first=json.loads((r/'matrix-build33.json').read_text())
assert len(first)==24 and not first[-1]['passed'] and first[-1]['case']=='state-edit'
first=first[:-1]
tail=json.loads((r/'matrix-build35-tail.json').read_text())
assert len(tail)==18 and tail[-1]['case']=='repeat-completed' and not tail[-1]['passed']
tail=tail[:-1]
assert len(first)==23 and len(tail)==17 and all(x['passed'] for x in first+tail)
last=[dict(case='repeat-completed',mode='consumer',passed=True)]
receipts={label:json.loads((r/f'{label}-install.json').read_text()) for label in ('build33','build35','build41')}
product=receipts['build33'][0]['sha256']
assert all(v[0]['sha256']==product for v in receipts.values())
assert hashlib.sha256(Path('app/build/outputs/apk/p77runtime/app-p77runtime.apk').read_bytes()).hexdigest()==product
cases=[]
for group,label,suffix in ((first,'build33','build33'),(tail,'build35','build35-tail'),(last,'build41','build41')):
 for x in group:
  prefix='device-' if x['mode']=='run' else 'device-p110-'
  raw=prefix+x['case']+'-'+suffix+'.txt'
  expected=('P109' if x['mode']=='run' else 'P110')+' PASS '+x['case']
  assert expected in (r/raw).read_text(),raw
  cases.append(dict(case=x['case'],mode=x['mode'],passed=True,raw=raw,product_apk_sha256=product,test_apk_sha256=receipts[label][1]['sha256']))
assert len({x['case'] for x in cases})==41
for case in ('exposed','r-exact','r-over','t-exact','t-over'):
 raw=f'device-p107-{case}.txt'
 assert 'P107 PASS '+case in (r/raw).read_text(),raw
 cases.append(dict(case=case,mode='P107',passed=True,raw=raw,product_apk_sha256=product,test_apk_sha256=receipts['build41'][1]['sha256']))
assert all('P107 '+c+': PASS' in (r/'legacy-build41.log').read_text() for c in ('exposed','r-exact','r-over','t-exact','t-over'))
cleanup=json.loads((r/'cleanup.json').read_text())
assert cleanup['protected_packages_unchanged'] and not cleanup['fixture_roots']
result=dict(status='46_PASS',synthetic_authentication=True,physical_memory_claim=False,product_apk_sha256=product,cases=cases,cleanup=cleanup)
(r/'device-final.json').write_text(json.dumps(result,indent=2)+'\n')
lines=['# §110 – finale isolierte P77-Fälle','','46 positive Fälle auf demselben Produkt-APK `'+product+'`. Synthetische Authentisierung und Daten. Drei Test-APKs: Build 33 für 23 Fälle; Build 35 für 17 Folgefälle; Build 41 für COMPLETED und fünf P107-Regressionen. Unterschiede sind ausschließlich dokumentierte Testfixtures/Navigation, synthetischer COMPLETED-Testinput und Diagnostik. Fehlfälle aus Build 33/34/35/36 und der unzureichende COMPLETED-Beleg 37 sowie Build 38/39/40 zählen nicht als PASS.','','[Identisches Produkt-APK](build35-target-identity.json), [Quellvergleich](build35-source-difference.json), [Install 33](build33-install.json), [Install 35](build35-install.json), [Install 36](build41-install.json), [Produktidentität 36](build41-target-identity.json), [Quellvergleich 36](build41-source-difference.json), [maschinenlesbare Fälle](device-final.json).','','| Probe | Familie | Ergebnis / Raw |','|---|---|---|']
for c in cases:lines.append(f"| {c['case']} | {c['mode']} | [PASS]({c['raw']}) |")
lines+=['','`visible_positive=false` bei Delete bezeichnet die geprüfte Abwesenheit des entfernten Elements. `frames=100/1000` zählt Kompositionen, keine physischen OnDrawframes. COMPLETED enthält in dieser Fixture keine Tasks; dort werden Originalpersonenreferenzen und ein lesendes Personendetail 1.000-mal geprüft. Die vollständige Übersicht ist zusätzlich in Build 40 mit 100 sichtbaren Kompositionen belegt; deren 1.000er-Versuch endete am Testtimeout und zählt nicht als positiver 1.000er-Nachweis. Die 32.768 Aliase sind bewusst gehaltene Originalreferenzen, keine nachträglich freigegebenen Payloads.','','[Fullview-Regressionen](legacy-build41.log), [Abschlusskontrolle](after-build41.log), [Cleanup](cleanup.json). Geschützte Pakete unverändert; keine eigenen synthetischen Fixtureroots verblieben. Keine physische Lösch-/Speichergrenze oder DeviceCredential-Gesamtabnahme.']
(r/'device-matrix.md').write_text('\n'.join(lines)+'\n')
print('46 unique family/case receipts validated on identical product APK')
