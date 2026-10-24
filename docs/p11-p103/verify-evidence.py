"""A passing NEGATIVE ABA test never changes #36 to PASS."""
from pathlib import Path
import json,subprocess,hashlib,xml.etree.ElementTree as E
OUT=Path(__file__).parent
counts={}
for p in (OUT/'junit').glob('*.xml'):
 r=E.parse(p).getroot()
 assert not any(int(r.get(k,'0')) for k in ('failures','errors','skipped')),p
 counts[r.get('name')]=len(r.findall('testcase'))
assert sum(counts.values())==97 and len(counts)==7
(OUT/'host-summary.json').write_text(json.dumps(dict(testcases=97,classes=counts,failures=0,skips=0),indent=2)+'\n')
records=[]
for scenario,name in [('cas_aba','cas-aba-03'),('person_ui','person-ui-01'),('publication','publication-01')]:
 p=OUT/(name+'.txt');s=p.read_text()
 assert f'P103,{scenario},CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ram_reuse=false,ABA_safety_NOT_CLAIMED=true' in s,p
 assert f'P103 PASS {scenario}' in s and 'FAILURE,' not in s,p
 records.append(dict(scenario=scenario,raw=p.name,test='PASS',issue36='BLOCKED_SCHEMA_APPROVAL'))
assert 'old_A_after_ABA=COMMITTED,BLOCKER=true' in (OUT/'cas-aba-03.txt').read_text()
assert 'role_and_plan_ABA_accepted=true,BLOCKER=true' in (OUT/'cas-aba-03.txt').read_text()
assert json.loads((OUT/'resume-cleanup.json').read_text())==dict(fixture_roots=[],protected_packages_unchanged=True)
(OUT/'device-summary.json').write_text(json.dumps(dict(cases=records,apks='resume-apks.json',
 excluded_whole_runs=['cas-aba-01.txt','cas-aba-02.txt'],issue36='BLOCKED_SCHEMA_APPROVAL'),indent=2)+'\n')
base='7e637c5d1569558c45cf84af75dff85b06cbe42a'
paths=['app/src/main','app/src/p77runtime','data/src/main','data/src/debug','domain/src/main','security/src/main','data/schemas','PRODUCT.md']
assert not subprocess.check_output(['git','diff',base,'--',*paths])
assert not subprocess.check_output(['git','ls-files','--others','--exclude-standard','--',*paths])
critical=['data/src/main/kotlin/org/pag/data/product/PersonDatabase.kt','data/src/main/kotlin/org/pag/data/product/ProductPersonRepository.kt',
 'data/src/main/kotlin/org/pag/data/product/ProductOperationsRepository.kt','data/src/main/kotlin/org/pag/data/product/ProductTaskRepository.kt',
 'data/src/main/kotlin/org/pag/data/product/OutstandingCoreRead.kt','domain/src/main/kotlin/org/pag/domain/ActiveWorkspaceBudget.kt',
 'data/src/main/kotlin/org/pag/data/product/LeadershipRecovery.kt','data/src/main/kotlin/org/pag/data/SessionCoordinator.kt',
 'data/schemas/org.pag.data.product.ActionDatabase/11.json']
(OUT/'production-unchanged.json').write_text(json.dumps(dict(base=base,checked_directories=paths,tracked_diff='empty',untracked_production_files=[],
 sha256={p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in critical}),indent=2)+'\n')
print('97 host methods / 3 P77 cases PASS; ABA remains BLOCKED_SCHEMA_APPROVAL; production/schema unchanged')
