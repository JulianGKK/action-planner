"""Reproduce all §81 custom runner attempts; never discard REDs or guarded cases."""
from pathlib import Path
import csv, re
from collections import Counter
base=Path(__file__).resolve().parent
rows=[]
for path in sorted(base.glob('*.txt')):
    match=re.fullmatch(r'([a-z_]+)-(\d+)\.txt',path.name)
    if not match:continue
    scenario,attempt=match.groups();text=path.read_text()
    if 'P81 PASS '+scenario in text:status='GREEN'
    elif 'NOT EXECUTED:' in text:status='BLOCKED_NOT_EXECUTED'
    elif 'P81 FAIL '+scenario in text:status='ASSERTION_RED'
    elif 'Process crashed.' in text:status='HARNESS_ABORT'
    else:raise ValueError('Incomplete/unclassified runner: '+path.name)
    evidence=re.search(r'P81_CASE,.*',text)
    body=evidence.group(0) if evidence else ''
    numbers=[(re.search(r','+key+r'=(\d+)',body).group(1) if re.search(r','+key+r'=(\d+)',body) else '')
             for key in ['reads_total','tickets_total','hits_accepted','hit_attempts']]
    events=re.search(r'os_events=(.*),reads_total=',body)
    if status=='GREEN':
        assert 'real_initial_reader=1,initial_ticket=1,initial_hit=1' in body
        assert 'old_ticket_dead=true,old_lifetime_dead=true,slot_revoked=true' in body
        assert 'fixture_cleanup_confirmed' in body
    rows.append([scenario,int(attempt),status,path.name,'fixture_cleanup_confirmed' in body,*numbers,events.group(1) if events else ''])
with (base/'attempts.csv').open('w',newline='') as out:
    writer=csv.writer(out,lineterminator='\n')
    writer.writerow(['scenario','attempt','result','report','cleanup_confirmed_in_report','reads_total','tickets_total','hits_accepted','hit_attempts','events'])
    writer.writerows(rows)
latest={}
for row in sorted(rows,key=lambda r:(r[0],r[1])):latest[row[0]]=row
assert len(rows)==47 and len(latest)==27
assert Counter(row[2] for row in rows)=={'GREEN':41,'ASSERTION_RED':2,'HARNESS_ABORT':2,'BLOCKED_NOT_EXECUTED':2}
assert Counter(row[2] for row in latest.values())=={'GREEN':26,'BLOCKED_NOT_EXECUTED':1}
print('ALL ATTEMPTS:',dict(Counter(row[2] for row in rows)))
print('LATEST OF 27 SCENARIOS:',dict(Counter(row[2] for row in latest.values())))
