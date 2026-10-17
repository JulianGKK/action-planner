#!/usr/bin/env python3
"""Starts ONLY the isolated P11 app. No clear/uninstall/credentials/product package."""
import argparse,subprocess,re
parser=argparse.ArgumentParser()
parser.add_argument('--adb',required=True)
parser.add_argument('--serial',required=True)
parser.add_argument('--label',choices=['baseline','after'],required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
adb=[args.adb,'-s',args.serial]
component='org.pag.perf.p11probe/org.pag.prototype.MainActivity'
with open(args.output,'w') as output:
    for kind,flags in [('COLD',['-S']),('WARM',[])]:
        for i in range(10):
            command=adb+['shell','am','start','-W']+flags+['-f','0x10008000','-n',component]
            result=subprocess.run(command,text=True,capture_output=True,timeout=30,check=True)
            match=re.search(r'^TotalTime: (\d+)$',result.stdout,re.M)
            if not match: raise SystemExit('Missing isolated startup TotalTime')
            line=f'P11,{args.label},START_{kind},{i},{match[1]}\n'
            output.write(line);output.flush()
            print(line,end='',flush=True)
