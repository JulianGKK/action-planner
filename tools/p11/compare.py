#!/usr/bin/env python3
"""Compare identical P11 endpoints. Positive delta means shorter after time."""
import argparse
import sys
sys.dont_write_bytecode=True
from summarize import summarize

parser=argparse.ArgumentParser()
parser.add_argument('logs',nargs='+')
args=parser.parse_args()
data=summarize(args.logs)
before=data['samples'].get('baseline',{})
after=data['samples'].get('after',{})
print('| Messpunkt | n vorher/nachher | Median vorher/nachher ms | P95 vorher/nachher ms | Δ Median ms | Δ Median % | σ vorher/nachher ms |')
print('| --- | ---: | ---: | ---: | ---: | ---: | ---: |')
for name in sorted(before.keys() & after.keys()):
    if not (name.endswith('_FRAME_CALLBACK') or name.startswith('START_') or name=='DB_TOTAL' or name in {'FRAME_TOTAL','FRAME_LAYOUT','SCROLL_FRAME_TOTAL'}):
        continue
    b,a=before[name],after[name]
    delta=b['median_ms']-a['median_ms']
    percent=100*delta/b['median_ms'] if b['median_ms'] else 0
    print(f"| {name} | {b['n']}/{a['n']} | {b['median_ms']:.2f}/{a['median_ms']:.2f} | {b['p95_ms']:.2f}/{a['p95_ms']:.2f} | {delta:.2f} | {percent:.1f} | {b['stdev_ms']:.2f}/{a['stdev_ms']:.2f} |")
print('\nMetadaten:',data['metadata'])
