#!/usr/bin/env python3
"""Fixed P11 CSV parser. Nearest-rank P95, measured repetitions >= 0, no content fields."""
import argparse, collections, json, math, statistics
from pathlib import Path

def summarize(paths):
    groups=collections.defaultdict(list)
    meta=collections.defaultdict(dict)
    for path in paths:
        for line in Path(path).read_text().splitlines():
            if line.startswith('P11,'):
                _,label,group,index,value=line.split(',')
                if int(index)>=0: groups[(label,group)].append(float(value))
            elif line.startswith('P11_META,'):
                _,label,name,value=line.split(',')
                meta[label][name]=int(value)
    result={'samples':{},'metadata':dict(meta)}
    for (label,group),values in sorted(groups.items()):
        ordered=sorted(values)
        result['samples'].setdefault(label,{})[group]={
            'n':len(values),'median_ms':statistics.median(values),
            'p95_ms':ordered[math.ceil(.95*len(ordered))-1],
            'min_ms':min(values),'max_ms':max(values),'stdev_ms':statistics.pstdev(values)}
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('logs',nargs='+')
    parser.add_argument('--output')
    args=parser.parse_args()
    output=json.dumps(summarize(args.logs),indent=2,sort_keys=True)+'\n'
    if args.output: Path(args.output).write_text(output)
    else: print(output,end='')
