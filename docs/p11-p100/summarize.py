"""Strict acceptance of the named observations; not a native upper-bound/leak certificate."""
from pathlib import Path
import csv, json, re, shutil, os, xml.etree.ElementTree as ET
OUT = Path(os.environ.get('P100_EVIDENCE_OUT', 'docs/p11-p100'))
(OUT/'junit').mkdir(parents=True, exist_ok=True)
FILES = ['repeat-boundary-02', 'lock-02', 'cancel-02', 'sql-error-02', 'cleanup-unknown-02', 'budget-refused-01']

def fields(text):
    result = {}
    for item in text.split(','):
        if '=' in item:
            key, value = item.split('=', 1)
            result[key] = int(value) if re.fullmatch(r'-?\d+', value) else value
    return result

cases, samples, boundaries = [], [], []
for name in FILES:
    raw = (OUT / (name + '.txt')).read_text()
    lines = [s.split('stream=P100,', 1)[1] for s in raw.splitlines() if 'stream=P100,' in s]
    final = next(s for s in lines if ',FINAL,' in s)
    case = final.split(',')[0]
    assert f'P100 PASS {case}' in raw and 'CASE,ANDROID_PASS,fixture_close_returned=true,fixture_absent=true' in raw
    assert f'P100 FAIL {case}' not in raw
    result = dict(fields(final), file=name + '.txt', case=case)
    assert result['prepared'] > 0
    assert result['prepared'] == result['closed'] == result['cursors'] == result['cursor_ends']
    assert result['windows'] > 0 and result['windows'] == result['window_ends']
    assert result['native_pool_ends'] == result['native_connection_ptr_zero'] == result['native_cache_empty'] == 2 * result['drivers']
    assert result['drivers'] > 0 and result['drivers'] == result['rooms'] == result['preflights'] == result['lease_ends'] == result['transaction_ends']
    assert result['vendor_reset_errors'] == result['vendor_close_errors'] == 0
    assert result['sql_step_errors'] == (1 if case == 'sql_error' else 0)
    if case == 'repeat_boundary':
        assert 'EXPOSED,second_admission=DENIED,State(phase=COMPOSE_TRANSITION' in raw
        for table, rows, batches in [('persons', 200, 7), ('tasks', 1000, 32)]:
            assert raw.count(f'BATCH,table={table},rows={rows},batches={batches},unicode=true') == 3
    elif case in ('lock', 'cancel'):
        assert 'PARKED_REVOKE,original_ticket_live=false,second_admission=DENIED' in raw
        assert 'retirementConfirmed=false' in raw and 'phase=RETIRED' in raw
    elif case in ('sql_error', 'cleanup_unknown'):
        assert 'FAILURE_POLICY,state=BLOCKED' in raw and 'phase=FAILED_OR_UNCONFIRMED' in raw
        assert ('injected_after_room=true' in raw) == (case == 'cleanup_unknown')
    else:
        admission = fields(next(s for s in lines if ',CORE_ADMISSION,' in s))
        assert admission['decision'] == 'BUDGET_REFUSED' and admission['transition'] > 16777216
        assert 'state=READY,type=BUDGET_REFUSED,payload_row_seen=false' in raw and 'phase=RETIRED' in raw
    cases.append(result)
    samples.extend(dict(file=name+'.txt', case=case, **fields(s)) for s in lines if ',SAMPLE,' in s)
    boundaries.extend(fields(s) for s in lines if ',BOUNDARY,' in s)
assert len(boundaries) == 4
for b in boundaries:
    plus = b['mode'].endswith('plus1')
    limit = 8388608 if b['mode'].startswith('R_') else 16777216
    assert b['requested'] == limit + int(plus)
    assert b['materializers'] == (0 if plus else 2) and b['refused'] == str(plus).lower()
(OUT/'device-summary.json').write_text(json.dumps(dict(final_cases=cases, samples=samples, boundaries=boundaries), indent=2)+'\n')
with (OUT/'samples.csv').open('w') as file:
    writer = csv.DictWriter(file, fieldnames=list(samples[0]), lineterminator="\n"); writer.writeheader(); writer.writerows(samples)
results = []
for source in sorted(Path('data/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    shutil.copyfile(source, OUT/'junit'/source.name)
for source in sorted((OUT/'junit').glob('TEST-*.xml')):
    root = ET.parse(source).getroot()
    results.append({k: root.attrib[k] for k in ['name', 'tests', 'failures', 'errors', 'skipped']})
total = {k: sum(int(r[k]) for r in results) for k in ['tests', 'failures', 'errors', 'skipped']}
assert total == dict(tests=44, failures=0, errors=0, skipped=0)
(OUT/'host-summary.json').write_text(json.dumps(dict(classes=results, total=total), indent=2)+'\n')
print('PASS: 6 named device cases, 4 exact boundary probes, 44 targeted host cases; no native upper bound inferred')
