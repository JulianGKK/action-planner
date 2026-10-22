"""Recalculate §80 final-run rows with stdlib only; p95 uses nearest rank.
Run: python3 docs/p11-p80/summarize.py
No partial/failing runner may produce a performance PASS summary.
"""
from pathlib import Path
import csv
from math import ceil
from statistics import median

base = Path(__file__).resolve().parent
report = (base / 'p77-runtime-compose-runner-final.txt').read_text()
assert 'P77 PASS:' in report and 'P77 FAIL:' not in report
assert 'idle_expiry_confirmed=true' in report
frames = [line.split(',')[1:] for line in report.splitlines() if line.startswith('P80_FRAME,')]
hits = [int(line.split(',')[2]) for line in report.splitlines() if line.startswith('P77_HIT,')]
assert len(frames) == len(hits) == 80
assert [int(row[0]) for row in frames] == list(range(1, 81))
metrics = []
with (base / 'frames.csv').open('w', newline='') as out:
    writer = csv.writer(out, lineterminator='\n')
    writer.writerow(['navigation_id', 'area', 'start_ns', 'navigation_return_ns',
                     'composition_start_ns', 'composition_commit_ns', 'layout_complete_ns',
                     'draw_start_ns', 'draw_end_ns', 'semantics_verification_ns', 'authorize_hit_ns'])
    for row, hit in zip(frames, hits):
        assert len(row) == 10
        writer.writerow(row + [hit])
        nav_id, area, *values = row
        start, nav, compose, commit, layout, draw, end, verify = map(int, values)
        assert start <= nav <= compose <= commit <= layout <= draw <= end
        assert verify > 0 and hit > 0
        phases = {
            'authorize_hit': hit,
            'navigation_including_authorize': nav-start,
            'navigation_to_composition_start': compose-start,
            'composition_start_to_commit': commit-compose,
            'navigation_to_commit': commit-start,
            'commit_to_layout_complete': layout-commit,
            'navigation_to_layout_complete': layout-start,
            'navigation_to_draw_start': draw-start,
            'content_draw_recording': end-draw,
            'navigation_to_content_draw_end': end-start,
            'semantics_verification_outside_measurement': verify,
        }
        metrics.extend((area, phase, value / 1e6) for phase, value in phases.items())
with (base / 'summary.csv').open('w', newline='') as out:
    writer = csv.writer(out, lineterminator='\n')
    writer.writerow(['area', 'metric', 'count', 'median_ms', 'p95_ms', 'minimum_ms', 'maximum_ms', 'failed_attempts'])
    for area in ('PERSONS', 'FUNCTIONS', 'TASKS', 'HUB'):
        for phase in dict.fromkeys(phase for a, phase, _ in metrics):
            values = sorted(value for a, p, value in metrics if a == area and p == phase)
            assert len(values) == 20
            stats = [round(x, 6) for x in (median(values), values[ceil(.95 * len(values))-1], min(values), max(values))]
            writer.writerow([area, phase, len(values), *stats, 0])
            if phase in ('navigation_to_content_draw_end', 'semantics_verification_outside_measurement'):
                print(area, phase, *stats)
