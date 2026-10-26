"""Checkpointed device matrix; each completed case has its own raw provenance."""
import json,pathlib,sys
sys.dont_write_bytecode=True
from measure import ROOT,run
cases=[]
for profile in ('small','medium','large6','large32','large100','large6-128k','large32-128k','large100-128k','large6-long5k','large32-long5k','large100-long5k','unicode','single-long-person'):
 for path in ('core','full'):
  cases.append((profile+'-'+path+'-r1','capacity',dict(fixture='p111-'+profile,path=path),'PASS'))
# Independent fresh-process repeats measure variation, not extrapolated peaks.
for profile,path in [('small','core'),('small','full'),('medium','core'),('large32-128k','core'),('large6-128k','core'),('large100-128k','core')]:
 cases.append((profile+'-'+path+'-r2','capacity',dict(fixture='p111-'+profile,path=path),'PASS'))
for scenario in ('person','active','plan','stale','material','expense','preparation','completed','lock-detail','lost-detail-main','duplicate-detail-main','unknown-detail'):
 cases.append(('function-'+scenario,'functional',dict(scenario=scenario),'PASS'))
for scenario in ('repeat-active','repeat-preparation','repeat-completed','material-input','form-repeat','long-role-input','role-rename','protocol-create','memory-create','postcommit-ui-refusal','action-revoke'):
 cases.append(('consumer-'+scenario,'consumer',dict(scenario=scenario),'PASS'))
cases.append(('diagnostic-completed-overview','consumer',dict(scenario='repeat-completed-overview'),'DIAGNOSTIC'))
for scenario in ('repeat_boundary','lock','cancel','sql_error','cleanup_unknown','budget_refused'):
 cases.append(('native-'+scenario,'resource',dict(scenario=scenario),'PASS'))
for scenario in ('r-exact','r-over','t-exact','t-over','exposed'):
 cases.append(('admission-'+scenario,'admission',dict(scenario=scenario),'PASS'))
for family,scenario in [('writer','leadership'),('writer','documents'),('cas','cas_aba'),('cas','native_revisions'),('security','normal_ready'),('security','content_refusal')]:
 cases.append(('security-'+family+'-'+scenario,family,dict(scenario=scenario),'PASS'))
cases.append(('diagnostic-completed-overview-control','consumer',dict(scenario='repeat-completed-overview',p111='false'),'DIAGNOSTIC'))
selected=sys.argv[1] if len(sys.argv)>1 else 'all'
if selected!='all':
 if selected=='capacity':cases=[c for c in cases if c[1]=='capacity']
 elif selected=='ui':cases=[c for c in cases if c[1] in ('consumer','functional')]
 elif selected=='native':cases=[c for c in cases if c[1] in ('resource','admission','writer','security','cas')]
 else:raise ValueError(selected)
for case,family,args,expected in cases:
 if case.startswith('diagnostic-completed-overview'):case+='-viewport-retry1' # Reveal the actual overview person before timing; workload/timeouts unchanged.
 if case=='native-repeat_boundary':case='native-repeat_boundary-schema12-retry1' # Padding includes shared revisionScratch added since the original P100 fixture.
 if case=='consumer-form-repeat':case='consumer-form-repeat-diagnostic-retry1' # Additional state diagnostics; unchanged identity assertion.
 if case=='unicode-core-r1':case='unicode-core-r1-retry1' # Counter now excludes METADATA/ADMITTED/COMPLETE phase marks.
 if case=='medium-full-r1':case='medium-full-r1-retry3' # Wait for the real detail/workspace callback, not initial idle flags.
 if case=='medium-core-r1':case='medium-core-r1-retry1' # Fixture FK assignment order corrected before runtime construction.
 if case=='small-full-r1':case='small-full-r1-retry1' # First fixture probe omitted the explicit history command; raw failure retained.
 result=ROOT/'runs'/case/'summary.json'
 if result.exists():
  old=json.loads(result.read_text())
  assert old['passed'],f'Failed existing case {case}; inspect before a distinct retry'
  print('EXISTING',case,flush=True);continue
 print('BEGIN',case,flush=True)
 if not run(case,family,args,expected):sys.exit(1)
print('MATRIX COMPLETE',selected,flush=True)
