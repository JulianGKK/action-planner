"""Verify raw provenance and final isolation; never converts diagnostic failure to product PASS."""
import sys,json,pathlib,hashlib,subprocess,collections,datetime
sys.dont_write_bytecode=True
from measure import ROOT,PKG,SERIAL,adb,inventory,digest,write
meta=json.loads((ROOT/'current-install.json').read_text())
assert SERIAL==meta['serial']
assert all(digest(a['apk'])==a['sha256'] for a in meta['apks'])
source_check=subprocess.run(['sha256sum','-c',str(ROOT/f"source-{meta['build']}.sha256")],capture_output=True,text=True)
(ROOT/'source-final-check.log').write_text(source_check.stdout+source_check.stderr)
assert source_check.returncode==0
changed=subprocess.check_output(['git','diff','--name-only','70ffdbaf1fe1678418ccb74af8f6b9ee6ba86029'],text=True).splitlines()
assert not any(p.startswith(('app/src/main/','data/src/main/','domain/')) for p in changed)
positive=[];diagnostic=[];failed=[];incomplete=[];external_error_field_missing=[];counts=collections.Counter()
for folder in sorted((ROOT/'runs').iterdir()):
 if not (folder/'manifest.json').exists():continue
 m=json.loads((folder/'manifest.json').read_text());assert m['serial']==SERIAL
 receipt=json.loads((ROOT/m['install_receipt']).read_text())
 assert m['apk_sha256']==receipt['apks'][0]['sha256'] and m['test_apk_sha256']==receipt['apks'][1]['sha256']
 if not (folder/'summary.json').exists():incomplete.append(folder.name);continue
 s=json.loads((folder/'summary.json').read_text())
 if 'external_errors' not in s:external_error_field_missing.append(folder.name)
 if not s['passed']:failed.append(folder.name);continue
 assert s['protected_packages_unchanged'] is True and s['fixture_roots_remaining']==[] and not s.get('external_errors')
 raw=(folder/'instrumentation.txt').read_text()
 assert 'INSTRUMENTATION_RESULT: result=' in raw
 assert not (folder/'external-error.json').exists()
 if s['expected']=='DIAGNOSTIC':
  diagnostic.append(dict(case=folder.name,product_pass='result=P110 PASS ' in raw,control=s['no_sampling_control']))
 else:
  assert ' PASS ' in raw;positive.append(folder.name);counts[s['family']]+=1
protected=inventory();assert protected==meta['protected_before']
roots=adb('shell','run-as',PKG,'ls','no_backup').stdout.splitlines();roots=[r for r in roots if r.startswith('p11-fixture-')];assert roots==[]
write(ROOT/'final-verification.json',dict(utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),serial=SERIAL,build=meta['build'],apks=meta['apks'],source_hashes_match=True,production_main_domain_diff_empty=True,external_error_field_missing=external_error_field_missing,positive_cases=positive,positive_count=len(positive),positive_families=counts,diagnostics=diagnostic,failed_attempts=failed,incomplete_attempts=incomplete,protected_packages_unchanged=True,protected_inventory=protected,fixture_roots_remaining=roots,synthetic_authentication=True,forced_gc=False,physical_upper_bound_claim=False,closure_status='BLOCKED'))
print(json.dumps(dict(positive=len(positive),families=counts,diagnostics=diagnostic,failed=failed,incomplete=incomplete)))
