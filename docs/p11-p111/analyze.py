"""Observed samples only: no interpolated peaks, physical bounds, or owner-end inference."""
import csv,json,pathlib,re,collections,math
ROOT=pathlib.Path(__file__).parent

def rows(path):
 if not path.exists():return []
 return [json.loads(s) for s in path.read_text().splitlines() if s.strip()]
def value(d,key):
 try:return int(d[key])
 except (ValueError,TypeError,KeyError):return None

def ledger(text):
 return {k:int(v) for k,v in re.findall(r'\b(retained|transition|localRetained|localTransition|pendingCandidates)=([0-9]+)',text or '')}

def extremes(samples,key):
 a=[s[key] for s in samples if isinstance(s.get(key),(int,float))]
 return (min(a),max(a)) if a else (None,None)

def dump_csv(file,entries):
 if not entries:return
 fields=list(dict.fromkeys(k for row in entries for k in row))
 with (ROOT/file).open('w') as f:
  writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerows(entries)

def analyze():
 cases=[];phases=[];capacity=[];gc=[];stress=[];attempts=[]
 for folder in sorted((ROOT/'runs').iterdir()):
  if not folder.is_dir() or not (folder/'manifest.json').exists():continue
  manifest=json.loads((folder/'manifest.json').read_text());sample=rows(folder/'samples.jsonl');sample=[s for s in sample if s.get('kind')=='sample'];events=rows(folder/'events.jsonl')
  summary=json.loads((folder/'summary.json').read_text()) if (folder/'summary.json').exists() else dict(case=folder.name,passed=False,expected='INCOMPLETE',family='unknown')
  case=dict(case=folder.name,passed=summary['passed'],family=summary['family'],expected=summary['expected'],apk_sha256=manifest['apk_sha256'],test_apk_sha256=manifest['test_apk_sha256'],samples=len(sample),final_ack='INSTRUMENTATION_RESULT: result=' in (folder/'instrumentation.txt').read_text(),raw=str(folder.relative_to(ROOT)))
  if sample:
   case['session_states']=';'.join(sorted({s['session'] for s in sample}));case['first_sample_utc_ms']=sample[0]['utc_ms'];case['last_sample_utc_ms']=sample[-1]['utc_ms']
   runtime_samples=[v for v in sample if v['session']!='NO_RUNTIME']
   for key in ('art_used_bytes','art_committed_bytes','native_allocated_bytes','native_size_bytes','pss_kib','fd_count','fixture_file_fd'):
    case[key+'_observed_min'],case[key+'_observed_max']=extremes(sample,key)
    case[key+'_runtime_observed_min'],case[key+'_runtime_observed_max']=extremes(runtime_samples,key)
  cases.append(case)
  if not summary['passed']:attempts.append(case)
  groups=collections.defaultdict(list)
  for s in sample:groups[(s['phase'],s['session'])].append(s)
  for (phase,session),ss in groups.items():
   row=dict(case=folder.name,phase=phase,session=session,samples=len(ss),first_elapsed_ns=ss[0]['elapsed_ns'],last_elapsed_ns=ss[-1]['elapsed_ns'])
   for key in ('art_used_bytes','native_allocated_bytes','pss_kib','fd_count','fixture_file_fd'):
    row[key+'_observed_min'],row[key+'_observed_max']=extremes(ss,key)
   for prefix,key in [('graphics','summary.graphics'),('java_summary','summary.java-heap'),('native_summary','summary.native-heap')]:
    v=[value(s.get('categories',{}),key) for s in ss];v=[n for n in v if n is not None];row[prefix+'_kib_observed_max']=max(v) if v else None
   # Queue delay may span phases; save the observed number without claiming a phase-local causal peak.
   queue=[s.get('ui',{}).get('main_queue_delay_ns') for s in ss];queue=[v for v in queue if isinstance(v,int)]
   row['main_queue_delay_ns_observed_max']=max(queue) if queue else None
   row.update({'ledger_last_'+k:v for k,v in ledger(ss[-1]['ledger']).items()})
   phases.append(row)
  for a,b in zip(sample,sample[1:]):
   if a['pid']!=b['pid']:continue
   start=value(a.get('gc',{}),'art.gc.gc-count');end=value(b.get('gc',{}),'art.gc.gc-count')
   if start is not None and end is not None and end>start:
    duration0=value(a['gc'],'art.gc.gc-time');duration1=value(b['gc'],'art.gc.gc-time')
    gc.append(dict(case=folder.name,observed_interval_begin_ns=a['elapsed_ns'],observed_interval_end_ns=b['elapsed_ns'],from_phase=a['phase'],to_phase=b['phase'],session_start=a['session'],session_end=b['session'],gc_count_delta=end-start,gc_total_time_delta_ms=None if duration0 is None or duration1 is None else duration1-duration0,main_queue_delay_ns_last_observed=b.get('ui',{}).get('main_queue_delay_ns'),individual_pause_timestamps_known=False,owner_retirement_claim=False))
  if events:
   fixture=next((e for e in events if e['kind']=='FIXTURE_BEGIN'),{})
   for e in events:
    if e['kind'] not in ('CORE_VISIBLE','CORE_REFUSED_BEFORE_PAYLOAD','FULL_VISIBLE','FULL_REFUSED_BEFORE_PAYLOAD','NATIVE_ROW_CAPACITY_BLOCKED'):continue
    charged=ledger(e.get('ledger',''))
    r=e.get('retained',e.get('requested_retained',e.get('retained_receipt',charged.get('localRetained'))))
    t=e.get('local_transition',e.get('requested_transition',e.get('transition_receipt',charged.get('localTransition'))))
    admitted=e['kind'] in ('CORE_VISIBLE','FULL_VISIBLE');blocked=e['kind']=='NATIVE_ROW_CAPACITY_BLOCKED'
    capacity.append(dict(case=folder.name,fixture=fixture.get('fixture'),people=fixture.get('people'),tasks=fixture.get('tasks'),roles=fixture.get('roles'),stored_events_per_action=fixture.get('events'),text_factor=fixture.get('text_factor'),path=next((x['path'] for x in events if x['kind']=='READY_BASELINE'),None),outcome='ZULAESSIG_INITIAL' if admitted else 'BLOCKED_NATIVE' if blocked else 'KONTROLLIERT_VERWEIGERT',retained_receipt_or_requested_bytes=r,local_transition_receipt_or_requested_bytes=t,cost_is_request_not_consumption=not admitted,body_materialized=admitted,case_completed=summary['passed'],elapsed_ns=e['elapsed_ns'],pss_kib_observed_max=case.get('pss_kib_runtime_observed_max'),art_used_bytes_observed_max=case.get('art_used_bytes_runtime_observed_max'),native_allocated_bytes_observed_max=case.get('native_allocated_bytes_runtime_observed_max'),fd_observed_max=case.get('fd_count_runtime_observed_max'),apk_sha256=manifest['apk_sha256'],raw=str(folder.relative_to(ROOT)),native_cleanup=e.get('native_cleanup')))
  for line in (folder/'instrumentation.txt').read_text().splitlines():
   if 'stream=P110,' not in line:continue
   content=line.split('stream=P110,',1)[1]
   if not any(k in content for k in ('REPEAT,','COMPOSE_PROGRESS,','COMPOSE_TIMEOUT,','MATERIAL_INPUT','LONG_FORM','FORM_COMMITTED','FORM_REPEAT','REVOKE,')):continue
   parsed=dict(re.findall(r'([A-Za-z_][A-Za-z0-9_]*)=([^,\n]+)',content))
   for key in list(parsed):
    if key in ('record',):del parsed[key]
   stress.append(dict(case=folder.name,record=content.split(',',2)[1],**parsed))
 dump_csv('cases.csv',cases);dump_csv('phases.csv',phases);dump_csv('capacity.csv',capacity);dump_csv('gc-observed-intervals.csv',gc);dump_csv('ui-stress.csv',stress)
 result=dict(case_count=len(cases),completed_cases=sum(c['passed'] for c in cases),expected_diagnostic_cases=sum(c['passed'] and c['expected']=='DIAGNOSTIC' for c in cases),incomplete_or_failed_attempts=attempts,samples=sum(c['samples'] for c in cases),gc_intervals=len(gc),physical_upper_bound_claim=False,interpolated_peaks=False,owner_retirement_claim=False)
 (ROOT/'measurement-summary.json').write_text(json.dumps(result,indent=2)+'\n')
 lines=['# Beobachtete Kapazitätsfälle','','Laufender Messstand. Jeder Maximalwert ist ein tatsächliches diskretes Sample nach Runtimebindung (MAINTENANCE/READY/LOCKED), einschließlich Test-/Frameworkoverhead; kein physischer Bound. Fixturebau ohne Runtime ist separat in cases.csv/phases.csv enthalten. R/T bei Refusal sind angeforderte, nicht konsumierte Kosten. Vollansicht und kompakter Erstkern bleiben getrennt.','','| Bestand / Rollen / Events / Textfaktor | Pfad | Ergebnis | R / lokale T Bytes | Prozess: ART / Native Bytes / PSS KiB (beobachtetes Max.) | Raw |','|---|---|---|---|---|---|']
 for c in capacity:
  if not c['case_completed']:continue
  lines.append(f"| {c['people']}/{c['tasks']} / {c['roles']} / {c['stored_events_per_action']} / {c['text_factor']} | {c['path']} | {c['outcome']} | {c['retained_receipt_or_requested_bytes']} / {c['local_transition_receipt_or_requested_bytes']} | {c['art_used_bytes_observed_max']} / {c['native_allocated_bytes_observed_max']} / {c['pss_kib_observed_max']} | [{c['case']}]({c['raw']}/events.jsonl) |")
 (ROOT/'capacity-matrix.md').write_text('\n'.join(lines)+'\n')
 print(json.dumps({k:v for k,v in result.items() if k!='incomplete_or_failed_attempts'}))
if __name__=='__main__':analyze()
