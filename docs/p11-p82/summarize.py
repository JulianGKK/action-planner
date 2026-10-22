"""Recompute the §82 accepted-case inventory and raw observations, without editing reports."""
from pathlib import Path
import csv, json, re
root=Path(__file__).resolve().parent
rows=[]; facts=[]
for path in sorted(root.glob('*-runner.txt')):
    body=path.read_text()
    prefix,case=path.name.removesuffix('-runner.txt').split('-',1)
    receipt=re.search(r'P82_CASE,([^,]+),GREEN,([^\n]+)',body)
    raw='PASS' if 'P82 PASS ' in body else 'FAIL' if 'P82 FAIL ' in body else 'ABORT'
    if prefix in ('first','diagnostic'):
        status='HARNESS-ABBRUCH'
    else:
        status='ANDROID-GREEN' if receipt and raw=='PASS' else 'ANDROID-RED' if raw=='FAIL' else 'HARNESS-ABBRUCH'
    rows.append(dict(report=path.name,series=prefix,case=case,classification=status,raw_runner_result=raw))
    if status=='ANDROID-GREEN':
        values=dict(item.split('=',1) for item in receipt.group(2).split(',') if '=' in item)
        assert values['statements']==values['closed'],path.name
        assert all(values[k]=='true' for k in ('window_ptrs_zero','preflight_closed','room_closed','driver_closed','lease_wiped','fixture_cleanup')),path.name
        assert values['readers']==values['tickets']=='1',path.name
        assert values['partial_core']=='false',path.name
        assert values['fault_reached']=='true',path.name
        if case=='commit_revoke':assert int(values['rollbacks'])>0
        facts.append(dict(report=path.name,**values))
with (root/'case-results.csv').open('w') as f:
    w=csv.DictWriter(f,fieldnames=['report','series','case','classification','raw_runner_result'],lineterminator='\n');w.writeheader();w.writerows(rows)
keys=['report','scenario','statements','rollbacks','closed','cursors','windows','rows','failure','vendor_error','suppressed','preflight_closed','room_closed','driver_closed','lease_wiped','window_ptrs_zero','fault_reached','readers','tickets','partial_core','fd_before','fd_after','native_before','native_after','pss_kib_before','pss_kib_after','gc_count','cycle_ns','fixture_cleanup']
with (root/'native-observations.csv').open('w') as f:
    w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore',lineterminator='\n');w.writeheader();w.writerows(facts)
counts={k:sum(r['classification']==k for r in rows) for k in ('ANDROID-GREEN','ANDROID-RED','HARNESS-ABBRUCH')}
(root/'android-counts.json').write_text(json.dumps(dict(total=len(rows),**counts),indent=2)+'\n')
print(json.dumps(dict(total=len(rows),**counts)))
