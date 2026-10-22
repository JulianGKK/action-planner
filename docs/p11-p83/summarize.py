"""Recompute raw observations; GREEN is a scenario assertion, NOT joint-budget approval."""
from pathlib import Path
import csv,json,re,hashlib
root=Path(__file__).resolve().parent
cases=[];phases=[];admissions=[];full=[];objects=[]
for p in sorted(root.glob('*-runner.txt')):
 body=p.read_text();series,fixture,scenario=p.stem.removesuffix('-runner').split('-',2)
 raw='PASS' if 'P83 PASS ' in body else 'FAIL' if 'P83 FAIL ' in body else 'ABORT'
 green=f'P83_CASE,{fixture},{scenario},ANDROID-GREEN' in body and raw=='PASS'
 status='ANDROID-GREEN' if green else 'ANDROID-RED' if raw=='FAIL' else 'HARNESS-ABBRUCH'
 if p.name=='followup-stress-history_touch-runner.txt':status='HARNESS-ABBRUCH' # null Accessibility root, before any touch
 cases.append(dict(report=p.name,series=series,fixture=fixture,scenario=scenario,classification=status,raw_result=raw,joint_acceptance='NOT_PROVEN',sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
 def values(text):return dict(i.split('=',1) for i in text.split(',') if '=' in i)
 for line in body.splitlines():
  if 'stream=P83_PHASE,' in line:
   _,f,s,phase,rest=line.split('stream=')[1].split(',',4)
   v=values(rest)
   ids=set(x for k in ['compose_ids','queued_ids'] for x in v.get(k,'').split('|') if x and x!='0')
   if v.get('vm_id','0')!='0':ids.add(v['vm_id'])
   if phase in ['candidate_worker_complete','third_candidate_second_pending'] and v.get('candidate_id','0')!='0':ids.add(v['candidate_id'])
   phases.append(dict(report=p.name,fixture=f,scenario=s,phase=phase,strong_core_graphs=len(ids),**v))
  elif 'stream=P83_ADMISSION,' in line:
   _,f,rest=line.split('stream=')[1].split(',',2);admissions.append(dict(report=p.name,fixture=f,**values(rest)))
  elif 'stream=P83_FULL,' in line:
   _,f,rest=line.split('stream=')[1].split(',',2);v=values(rest);full.append(dict(report=p.name,fixture=f,**v))
   phases.append(dict(report=p.name,fixture=f,scenario=scenario,phase='full_workspace_editor',strong_core_graphs='UNKNOWN',native_scope='FULL_VIEW_NOT_INSTRUMENTED',**v))
  elif any(k in line for k in ['stream=P83_FULL_TOUCH,','stream=P83_FULL_BEGIN,','stream=P83_EDITOR_DRAW,']):
   kind,f,rest=line.split('stream=')[1].split(',',2)
   phases.append(dict(report=p.name,fixture=f,scenario=scenario,phase={'P83_FULL_TOUCH':'full_pending_touch','P83_FULL_BEGIN':'full_begin','P83_EDITOR_DRAW':'editor_draw'}[kind],strong_core_graphs='UNKNOWN',native_scope='FULL_VIEW_NOT_INSTRUMENTED',**values(rest)))
  elif 'stream=P83_OBJECTS,' in line:
   _,f,rest=line.split('stream=')[1].split(',',2);objects.append(dict(report=p.name,fixture=f,**values(rest)))
def write(name,rows):
 keys=list(dict.fromkeys(k for r in rows for k in r))
 with (root/name).open('w') as f:
  w=csv.DictWriter(f,fieldnames=keys,lineterminator='\n');w.writeheader();w.writerows(rows)
write('case-results.csv',cases);write('phases.csv',phases);write('admissions.csv',admissions);write('full-views.csv',full);write('objects.csv',objects)
counts={k:sum(r['classification']==k for r in cases) for k in ['ANDROID-GREEN','ANDROID-RED','HARNESS-ABBRUCH']}
counts.update(total=len(cases),joint_acceptance='BLOCKER',max_strong_core_graphs=max((int(r['strong_core_graphs']) for r in phases if str(r['strong_core_graphs']).isdigit()),default=0))
(root/'android-counts.json').write_text(json.dumps(counts,indent=2)+'\n')
maxima={}
for r in phases:
 key=r['phase'];m=maxima.setdefault(key,{})
 for field in ['strong_core_graphs','art_used','art_committed','native_heap','pss_kib','fd','windows_live','window_rows']:
  if str(r.get(field,'')).isdigit():m[field]=max(m.get(field,0),int(r[field]))
(root/'phase-maxima.json').write_text(json.dumps(maxima,indent=2)+'\n')
print(json.dumps(counts))
