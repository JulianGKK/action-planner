"""Local §101 evidence summary; PASS of a negative test does not close RESERVED recovery."""
from pathlib import Path
import hashlib,json,re,subprocess,xml.etree.ElementTree as E
OUT=Path(__file__).parent
suites={}
for p in (OUT/'junit').glob('*.xml'):
    r=E.parse(p).getroot()
    if r.get('name') not in suites or r.get('timestamp','')>suites[r.get('name')][0]:
        suites[r.get('name')]=(r.get('timestamp',''),p,r)
cases=[];counts={}
for name,(stamp,p,r) in sorted(suites.items()):
    assert not any(int(r.get(k,'0')) for k in ('failures','errors','skipped')),p
    counts[name]=len(r.findall('testcase'))
    for t in r.findall('testcase'):
        assert all(t.find(k) is None for k in ('failure','error','skipped')),p
        cases.append(dict(classname=name,name=t.get('name'),source=str(p),timestamp=stamp))
(OUT/'host-summary.json').write_text(json.dumps(dict(unique_testcases=len(cases),all_pass=True,classes=counts,
    method='newest entire suite per classname (not union of renamed historical test methods)',testcases=cases),indent=2)+'\n')
scenarios=['leadership','lost_main','lost_main_recovery','partial_setup','partial_delete','rollback','status_reject','commit_receipt','cleanup_receipt','documents','migration']
label=OUT/'label-device/commit-receipt.txt'
assert 'P101 PASS commit_receipt' in label.read_text()
assert json.loads((OUT/'label-device/resume-cleanup.json').read_text())==dict(fixture_roots=[],protected_packages_unchanged=True)
records=[]
for scenario in scenarios:
    p=OUT/'final-device'/(scenario.replace('_','-')+'.txt');s=p.read_text()
    assert f'P101,{scenario},CASE,ANDROID_PASS,fixture_cleanup=true,key_alias_absent=true,reuse=false,ram_reuse=false' in s,p
    assert f'P101 PASS {scenario}' in s and 'P101 FAIL' not in s,p
    records.append(dict(scenario=scenario,raw=str(p),test='PASS',product_recovery='BLOCKED' if scenario=='partial_setup' else 'see assessment scope'))
assert 'RESERVED_RECOVERY,BLOCKED' in (OUT/'final-device/partial-setup.txt').read_text()
assert 'late_A_cannot_ack_B=true' in (OUT/'final-device/lost-main-recovery.txt').read_text()
assert 'outcome=COMMITTED,cleanup=FAILED_OR_UNCONFIRMED' in (OUT/'final-device/cleanup-receipt.txt').read_text()
assert 'native_transaction_live=true' in (OUT/'final-device/commit-receipt.txt').read_text()
assert json.loads((OUT/'final-device/resume-cleanup.json').read_text())==dict(fixture_roots=[],protected_packages_unchanged=True)
(OUT/'device-summary.json').write_text(json.dumps(dict(final_tests=records,final_apks='final-device/resume-apks.json',diagnostic_label_recheck=dict(raw='label-device/commit-receipt.txt',apks='label-device/resume-apks.json',test='PASS'),
    historical_attempts=dict(accepted=11,harness_aborts=['commit-receipt-02.txt','commit-receipt-03.txt']),
    issue35='BLOCKED'),indent=2)+'\n')
base='68e22e99e410f5b21ac665599e39b8a197a4a06e'
paths=['domain/src/main/kotlin/org/pag/domain/ActiveWorkspaceBudget.kt','data/src/main/kotlin/org/pag/data/product/OutstandingCoreRead.kt',
       'data/src/main/kotlin/org/pag/data/SessionCoordinator.kt','security/src/main/kotlin/org/pag/security/LeadershipCrypto.kt']
unchanged={}
for name in paths:
    data=Path(name).read_bytes();assert data==subprocess.check_output(['git','show',base+':'+name]),name
    unchanged[name]=hashlib.sha256(data).hexdigest()
(OUT/'unchanged-security-contracts.json').write_text(json.dumps(dict(base=base,sha256=unchanged),indent=2)+'\n')
print('host',len(cases),'PASS; device',len(records),'PASS; #35 BLOCKED (RESERVED recovery)')
