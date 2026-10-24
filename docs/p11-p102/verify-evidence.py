"""Strict §102 acceptance summary; historical failures/cleanup are never acceptance cases."""
from pathlib import Path
import hashlib,json,subprocess,xml.etree.ElementTree as E
OUT=Path(__file__).parent
classes={}
for p in (OUT/'junit').glob('*.xml'):
 r=E.parse(p).getroot()
 assert all(int(r.get(k,'0'))==0 for k in ('failures','errors','skipped')),p
 assert all(all(t.find(k) is None for k in ('failure','error','skipped')) for t in r.findall('testcase')),p
 classes[r.get('name')]=len(r.findall('testcase'))
assert sum(classes.values())==71 and len(classes)==7
(OUT/'host-summary.json').write_text(json.dumps(dict(testcases=sum(classes.values()),classes=classes,failures=0,skips=0),indent=2)+'\n')
scenarios=['present_ui','absent_ui','normal_ready','unknown_alias','content_refusal','crash_retry','lock_before','lock_during','lock_after','status_change','delete_pending_ui','cleanup_receipt']
records=[]
for scenario in scenarios:
 p=OUT/(scenario.replace('_','-')+'-final.txt');s=p.read_text()
 assert f'P102,{scenario},CASE,ANDROID_PASS,fixture_cleanup=true,key_alias_absent=true,reuse=false,ram_reuse=false' in s,p
 assert f'P102 PASS {scenario}' in s and 'P102 FAIL' not in s and 'FAILURE,' not in s,p
 if scenario in ('present_ui','absent_ui','delete_pending_ui'):
  assert 'same_activity_focus=true' in s and 'visible_feedback=true' in s and 'no_reset_on_reauth=true' in s and 'synthetic_auth_provider=true' in s,p
 records.append(dict(scenario=scenario,result='PASS',raw=p.name,apks='resume-apks.json' if scenario in ('present_ui','absent_ui','delete_pending_ui') else 'before-callback-binding-apks.json'))
assert 'outcome=COMMITTED,cleanup=FAILED_OR_UNCONFIRMED' in (OUT/'cleanup-receipt-final.txt').read_text()
assert 'lookup_exception_not_absence=true' in (OUT/'unknown-alias-final.txt').read_text()
assert 'new_explicit_intent_and_reauth=true' in (OUT/'crash-retry-final.txt').read_text()
assert 'exact_inode_guard=true' in (OUT/'recorded-crash-cleanup.txt').read_text()
assert json.loads((OUT/'resume-cleanup.json').read_text())==dict(fixture_roots=[],protected_packages_unchanged=True)
(OUT/'device-summary.json').write_text(json.dumps(dict(final_cases=records,apks='resume-apks.json',
 historical=dict(accepted_unrepeated_case='normal-ready-01.txt',pre_callback_binding_UI=['present-ui-before-callback-binding.txt','absent-ui-before-callback-binding.txt','delete-pending-ui-before-callback-binding.txt'],excluded=['present-ui-01.txt','present-ui-02.txt'],
 explicit_test_fixture_cleanup='recorded-crash-cleanup.txt'),authentication='P77 synthetic; original SessionCoordinator and product UI; real Keystore/Room/SQLCipher'),indent=2)+'\n')
base='d355dd58b833b5e7949ef2a061a887bfcc4ddc3b'
paths=['domain/src/main/kotlin/org/pag/domain/ActiveWorkspaceBudget.kt','domain/src/main/kotlin/org/pag/domain/WorkspaceMutationInvalidation.kt',
 'data/src/main/kotlin/org/pag/data/product/OutstandingCoreRead.kt','data/src/main/kotlin/org/pag/data/SessionCoordinator.kt',
 'data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt','data/src/main/kotlin/org/pag/data/product/WorkspaceWriteBoundary.kt',
 'data/src/main/kotlin/org/pag/data/product/LeadershipWriteProgress.kt','security/src/main/kotlin/org/pag/security/LeadershipCrypto.kt',
 'security/src/main/kotlin/org/pag/security/ProductDbEnvelope.kt','app/src/main/kotlin/org/pag/prototype/MainActivity.kt',
 'app/src/p77runtime/kotlin/org/pag/prototype/P86FullWorkspaceActivity.kt']
unchanged={}
for name in paths:
 data=Path(name).read_bytes();assert data==subprocess.check_output(['git','show',base+':'+name]),name
 unchanged[name]=hashlib.sha256(data).hexdigest()
assert not subprocess.check_output(['git','diff',base,'--','data/schemas'])
(OUT/'unchanged-contracts.json').write_text(json.dumps(dict(base=base,sha256=unchanged,exported_schema_diff='empty'),indent=2)+'\n')
previous=json.loads((OUT/'before-callback-binding-source-sha256.json').read_text())
for path,h in previous.items():
 if path.startswith('data/') or path.startswith('domain/'):
  assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==h,path
print('71 Host PASS; 12 P77 cases PASS; unchanged original security/budget/reuse fences')
