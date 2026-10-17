#!/usr/bin/env python3
"""FrameMetrics deadline comparison for the bounded P11 probe stream."""
import argparse
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('logs',nargs='+')
args=parser.parse_args()
for filename in args.logs:
    frames={}
    for line in Path(filename).read_text().splitlines():
        if line.startswith('P11,'):
            _,label,name,index,value=line.split(',')
            if name in {'FRAME_TOTAL','FRAME_DEADLINE','SCROLL_FRAME_TOTAL'}:
                frames[int(index)]=(name,float(value))
    totals=[(i,v) for i,(n,v) in frames.items() if n=='FRAME_TOTAL']
    comparable=[(v,frames[i+2][1]) for i,v in totals if frames.get(i+2,('',0))[0]=='FRAME_DEADLINE' and frames[i+2][1]>0]
    over=sum(total>deadline for total,deadline in comparable)
    print(Path(filename).name, 'frames',len(totals),'valid_deadlines',len(comparable),'over_deadline',over,
          'percent',round(100*over/len(comparable),2) if comparable else None)
