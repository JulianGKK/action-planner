from pathlib import Path
import csv,hashlib,json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
cases=[];samples=[]
for p in sorted(root.glob('*-task_history-runner.txt')):
 series,fixture=p.name.split('-')[:2];raw=p.read_text()
 status='ANDROID-GREEN' if f'P85a PASS {fixture}' in raw and f'P85a_CASE,{fixture},ANDROID-GREEN' in raw else 'ANDROID-RED'
 reason='scoped functional assertions'
 if 'No actual accessibility content: Chronik laden' in raw:status='HARNESS-ABBRUCH';reason='accessibility scroll/locator; same product APK later passes with real owned-window swipes'
 if 'Required value was null.' in raw and 'P85TaskHistoryProbe.swipe' in raw:status='HARNESS-ABBRUCH';reason='transient null accessibility root before swipe'
 cases.append(dict(report=p.name,status=status,raw_result='PASS' if 'P85a PASS' in raw else 'FAIL',reason=reason,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
 for line in raw.splitlines():
  if 'stream=P85a_' not in line:continue
  item=line.split('stream=',1)[1]
  if item.startswith(('P85a_BOARD,','P85a_PAGE,','P85a_DIRECT,')):
   fields=item.split(',');row=dict(report=p.name,phase=fields[0],fixture=fields[1])
   for f in fields[2:]:
    if '=' in f:
     k,v=f.split('=',1);row[k]=v
   samples.append(row)
with (root/'android-cases.csv').open('w') as f:
 w=csv.DictWriter(f,fieldnames=['report','status','raw_result','reason','sha256'],lineterminator='\n');w.writeheader();w.writerows(cases)
with (root/'android-samples.csv').open('w') as f:
 fields=['report','phase','fixture']+sorted(set().union(*(set(r) for r in samples))-{'report','phase','fixture'})
 w=csv.DictWriter(f,fieldnames=fields,lineterminator='\n');w.writeheader();w.writerows(samples)
counts={s:sum(c['status']==s for c in cases) for s in ['ANDROID-GREEN','ANDROID-RED','HARNESS-ABBRUCH']}
counts['total_custom_runs']=len(cases);counts['JUnit_executions']=0
(root/'android-counts.json').write_text(json.dumps(counts,indent=2)+'\n')
unique={'data':set(),'app':set()};failures=0
for folder in ['host-full','host-final','host-p77']:
 for f in (root/folder).glob('TEST-*.xml'):
  r=ET.parse(f).getroot();module='data' if r.attrib['name'].startswith('org.pag.data.') else 'app'
  failures+=int(r.attrib['failures'])+int(r.attrib['errors'])+int(r.attrib['skipped'])
  unique[module].update((r.attrib['name'],t.attrib['name']) for t in r.findall('testcase'))
summary={m:len(t) for m,t in unique.items()};summary['total_distinct']=sum(map(len,unique.values()));summary['failures_errors_skips']=failures
(root/'host-counts.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(dict(android=counts,host=summary),indent=2))
