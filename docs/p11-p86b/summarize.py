"""Derive samples and individual timings from raw output; never infer upper bounds."""
from pathlib import Path
import json, re

root = Path(__file__).parent
reports = {}
for name in ('small-02', 'stress32-01', 'stress100-02', 'stress100-03'):
    path = root/(name+'.txt')
    if not path.exists():
        continue
    raw = path.read_text()
    result = {'functional_pass': 'FUNCTIONAL_PASS' in raw, 'upper_bound': 'UNKNOWN',
              'phases_ms': {}, 'sampled_peaks': {}, 'census': {}}
    for line in raw.splitlines():
        if 'stream=P86_' not in line:
            continue
        fields = line.split('stream=', 1)[1].split(',')
        values = dict(item.split('=', 1) for item in fields if '=' in item)
        if fields[0] == 'P86_TOUCH':
            result['phases_ms'].setdefault(fields[2], []).append(round(int(values['tap_to_ondraw_ns'])/1e6, 3))
        if fields[0] in ('P86_SAMPLE', 'P86_BASELINE', 'P86_HELD', 'P86_FINAL', 'P86_FAILURE'):
            for key in ('art_used', 'native_heap', 'pss_kib', 'fd', 'gc'):
                if key in values:
                    result['sampled_peaks'][key] = max(result['sampled_peaks'].get(key, 0), int(values[key]))
        if fields[0] in ('P86_BASELINE', 'P86_FINAL'):
            result[fields[0][4:].lower()] = values
        if fields[0] == 'P86_CENSUS':
            result['census'][fields[1]] = values
    reports[name] = result
(root/'measurements-final.json').write_text(json.dumps(reports, indent=2)+'\n')
print(json.dumps({name: dict(functional_pass=r['functional_pass'], sampled_peaks=r['sampled_peaks'])
                  for name, r in reports.items()}, indent=2))
