"""Rebuild latency summaries from the unedited custom-runner reports (stdlib only)."""
from pathlib import Path
import collections, json, re, statistics
out=Path(__file__).resolve().parent
result={}
for series in ('before','after'):
    source=(out/f'{series}-stress-timings-runner.txt').read_text()
    positions={}
    for page in (1,2,2001,4001):
        samples=[]
        for rep in range(3):
            match=re.search(rf'P85b_TIME,rep={rep},page={page},delivery_ns=(\d+),ondraw_ns=(\d+),rows=(\d+),total=(\d+)',source)
            assert match, (series,page,rep)
            sample=dict(repetition=rep,delivery_ms=int(match[1])/1e6,ondraw_ms=int(match[2])/1e6,rows=int(match[3]),total=int(match[4]))
            database={};sql=collections.defaultdict(float);steps=collections.defaultdict(float);counts=collections.Counter()
            for line in source.splitlines():
                prefix=f'P85b_DIAG,rep={rep},page={page},'
                if prefix not in line:continue
                line=line.split(prefix)[1]
                if line.startswith('DB,'):
                    phase,nanos=line.removeprefix('DB,').split(',ns=');database[phase]=int(nanos)/1e6
                if not line.startswith('SQL,'):continue
                m=re.match(r'SQL,ns=(\d+),step_ns=(\d+),blobs=(\d+),bytes=(\d+),sql=(.*)',line)
                assert m
                q=m[5]
                if 'FROM task_events' in q:
                    if q.startswith('SELECT COUNT(*) FROM task_events WHERE actionId=? AND taskId=?') and 'occurredAt' not in q:category='event_count'
                    elif q.startswith('SELECT occurredAt,rowid'):category='offset_anchor'
                    elif 'rowid IN (SELECT rowid FROM task_events' in q:category='page_selection_and_'+('fetch' if q.startswith('SELECT rowid,') else 'sizing')
                    elif 'typeof(id)' in q:category='event_validation'
                    elif q.startswith('SELECT EXISTS'):category='continuation_exists'
                    else:category='cursor_validation'
                elif 'FROM tasks' in q or q=='PRAGMA encoding' or q.startswith('SELECT COALESCE((SELECT CASE'):category='task_preflight_and_existence'
                else:category='room_schema_and_expected_snapshot'
                sql[category]+=int(m[1])/1e6;steps[category]+=int(m[2])/1e6;counts[category]+=1
            sample.update(database_ms=database,sql_ms=dict(sql),sql_step_ms=dict(steps),sql_counts=dict(counts));samples.append(sample)
        median={k:statistics.median(s[k] for s in samples) for k in ('delivery_ms','ondraw_ms')}
        for group in ('database_ms','sql_ms'):
            median[group]={k:statistics.median(s[group][k] for s in samples) for k in samples[0][group]}
        positions[str(page)]=dict(samples=samples,median=median)
    result[series]=positions
(out/'latencies.json').write_text(json.dumps(result,indent=2)+'\n')
for page in ('1','2','2001','4001'):
    before=result['before'][page]['median'];after=result['after'][page]['median']
    print(f"{page}: delivery {before['delivery_ms']:.1f} → {after['delivery_ms']:.1f} ms; OnDraw {before['ondraw_ms']:.1f} → {after['ondraw_ms']:.1f} ms")
