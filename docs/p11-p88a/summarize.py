"""Derive handle observations and process samples from unedited instrumentation output."""
from pathlib import Path
import json

out=Path(__file__).parent
reports={}
for name in ('paging-02','revoke-01','statement-error-01'):
    raw=(out/(name+'.txt')).read_text()
    assert 'ANDROID_PASS' in raw and 'P88 PASS ' in raw
    report={'process_samples':[]}
    for line in raw.splitlines():
        if 'stream=P88_' not in line:continue
        fields=line.split('stream=',1)[1].split(',')
        values=dict(s.split('=',1) for s in fields if '=' in s)
        if fields[0]=='P88_SAMPLE':report['process_samples'].append(dict(label=fields[2],**values))
        if fields[0]=='P88_FINAL':report['resources']=values
        if fields[0]=='P88_SCOPE':report['owned_keyscope']=values
        if fields[0]=='P88_TRACE' and fields[2]=='VENDOR_CONTROL':
            report.setdefault('vendor_controls',[]).append(dict(mode=fields[3],**values))
    reports[name]=report
(out/'observations.json').write_text(json.dumps(reports,indent=2)+'\n')
print('Three final P88 scenarios summarized; samples are not upper bounds.')
