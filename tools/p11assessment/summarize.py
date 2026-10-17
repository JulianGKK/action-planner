#!/usr/bin/env python3
"""Summarize synthetic assessment records; nearest-rank P95, no cross-fixture sums."""
import argparse,collections,math,pathlib,statistics,json
p=argparse.ArgumentParser();p.add_argument('files',nargs='+');a=p.parse_args()
rows=collections.defaultdict(list);meta={}
for name in a.files:
    for line in pathlib.Path(name).read_text().splitlines():
        if line.startswith('ASSESS,'):
            _,v,s,phase,i,value=line.split(',');key=(s,v,phase)
            if int(i)>=0:rows[key].append(float(value))
            else:meta[key]=float(value)
print('size,variant,phase,n,median,p95,min,max,sd')
for (s,v,phase),values in sorted(rows.items()):
    x=sorted(values);print(f'{s},{v},{phase},{len(x)},{statistics.median(x):.3f},{x[math.ceil(.95*len(x))-1]:.3f},{min(x):.3f},{max(x):.3f},{statistics.pstdev(x):.3f}')
for (s,v,phase),value in sorted(meta.items()):print(f'META,{s},{v},{phase},{value}')
