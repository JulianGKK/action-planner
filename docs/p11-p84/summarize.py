"""§84 scenario assertions, separate from unproved 8+8 native/frame certification."""
from pathlib import Path
import csv,hashlib,json,re
root=Path(__file__).resolve().parent
cases=[];phases=[];cleanup=[]
for p in sorted(root.glob('*-runner.txt')):
 body=p.read_text();series,fixture,scenario=p.stem.removesuffix('-runner').split('-',2)
 raw='PASS' if 'P84 PASS ' in body else 'FAIL' if 'P84 FAIL ' in body else 'ABORT'
 green=raw=='PASS' and f'P84_CASE,{fixture},{scenario},ANDROID-GREEN' in body
 status='ANDROID-GREEN' if green else 'ANDROID-RED' if raw=='FAIL' else 'HARNESS-ABBRUCH'
 if p.name=='final-roles100-budget-runner.txt':status='HARNESS-ABBRUCH' # held rejected Main was never dispatched by that harness
 cases.append(dict(report=p.name,series=series,fixture=fixture,scenario=scenario,classification=status,raw_result=raw,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),joint_certification='NOT_PROVEN'))
 def values(s):return {k.strip():v.strip() for k,v in (t.split('=',1) for t in s.split(',') if '=' in t)}
 for line in body.splitlines():
  if 'stream=P84_PHASE,' in line:
   _,f,s,phase,rest=line.split('stream=')[1].split(',',4)
   state=re.search(r'state=State\((.*?)\)',rest)
   fields=values(re.sub(r'state=State\(.*?\),?','',rest))
   if state:fields.update({'outstanding_'+k:v for k,v in values(state[1]).items()})
   ids={x for k in ['compose_ids','queued_ids','vm_id'] for x in fields.get(k,'').split('|') if x and x!='0'}
   phases.append(dict(report=p.name,fixture=f,scenario=s,phase=phase,observed_root_core_graphs=len(ids),**fields))
  elif any(x in line for x in ['stream=P84_CLEANUP,','stream=P84_SCOPED_FAULT,','stream=P84_BUDGET_REFUSED,']):
   cleanup.append(dict(report=p.name,raw=line.split('stream=')[1]))
def write(name,rows):
 keys=list(dict.fromkeys(k for r in rows for k in r))
 with (root/name).open('w') as f:
  w=csv.DictWriter(f,fieldnames=keys,lineterminator='\n');w.writeheader();w.writerows(rows)
write('case-results.csv',cases);write('phases.csv',phases);write('cleanup-evidence.csv',cleanup)
counts={k:sum(r['classification']==k for r in cases) for k in ['ANDROID-GREEN','ANDROID-RED','HARNESS-ABBRUCH']}
counts.update(total=len(cases),max_observed_pending_candidates=max((int(r.get('outstanding_pendingCandidates','0')) for r in phases),default=0),joint_memory_certification='BLOCKER')
(root/'android-counts.json').write_text(json.dumps(counts,indent=2)+'\n')
maxima={}
for r in phases:
 m=maxima.setdefault(r['phase'],{})
 for k in ['outstanding_retained','outstanding_transition','outstanding_localRetained','outstanding_localTransition','outstanding_pendingCandidates','observed_root_core_graphs','art_used','native_heap','pss_kib','fd','windows_live','window_rows']:
  v=str(r.get(k,''))
  if v.isdigit():m[k]=max(m.get(k,0),int(v))
(root/'phase-maxima.json').write_text(json.dumps(maxima,indent=2)+'\n')
print(json.dumps(counts))
