#!/usr/bin/env python3
"""Isolated synthetic probes only; raw logs stay ignored and never enter Git."""
import argparse,json,os,pathlib,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--adb',default=os.environ.get('ADB','adb'));p.add_argument('--serial',default=os.environ.get('ANDROID_SERIAL'));p.add_argument('--aapt',required=True);p.add_argument('--install',action='store_true');p.add_argument('--artifacts',type=pathlib.Path);p.add_argument('--runs',type=int,default=50);p.add_argument('--variants',nargs='+',choices=list('ABCDEFGH'),default=list('ABCDEF'));p.add_argument('--fonts',nargs='+',choices=['1','2','3.5'],default=['1','2','3.5']);p.add_argument('--reuse',action='store_true');p.add_argument('--column',action='store_true');p.add_argument('--automation',choices=['true','false'],default='true');p.add_argument('--output',type=pathlib.Path,default=pathlib.Path('.prototype-cache/p11semantics'))
a=p.parse_args()
if not a.serial or not 1<=a.runs<=100:p.error('explicit serial and 1..100 runs required')
if not pathlib.Path('.prototype-cache/debug.keystore').is_file():raise SystemExit('Existing keystore missing; stop')
adb=[a.adb,'-s',a.serial];a.output.mkdir(parents=True,exist_ok=True)
def command(args,**kw):return subprocess.run(args,check=True,text=True,**kw)
for folder,pkg in [(a.artifacts/'target' if a.artifacts else pathlib.Path('app/build/outputs/apk/debug'),'org.pag.test.p11semantics'),(a.artifacts/'test' if a.artifacts else pathlib.Path('app/build/outputs/apk/androidTest/debug'),'org.pag.test.p11semantics.test')]:
 folder=pathlib.Path(folder);m=json.loads((folder/'output-metadata.json').read_text());assert m['applicationId']==pkg
 apk=folder/m['elements'][0]['outputFile'];xml=subprocess.check_output([a.aapt,'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
 assert f'A: package="{pkg}" ' in xml
 if pkg=='org.pag.test.p11semantics':
  assert 'E: uses-permission ' not in xml and 'android.app.Application' in xml
  assert 'org.pag.prototype.MainActivity' not in xml and 'org.pag.semantics.SemanticsProbeActivity' in xml
 if a.install:command(adb+['install','-r',str(apk)])
assert command(adb+['shell','getprop','ro.build.version.sdk'],capture_output=True).stdout.strip()=='33'
records=[]
for font in a.fonts:
 for v in a.variants:
  done=0;passes=0;crashes=0;harness=0;attempts=[]
  while done<a.runs:
   log=a.output/f'{v}-{font}-reuse{int(a.reuse)}-from{done}.txt'
   with log.open('w') as f:command(adb+['shell','am','instrument','-w','-e','variant',v,'-e','font',font,'-e','runs',str(a.runs-done),'-e','first',str(done),'-e','reuse',str(a.reuse).lower(),'-e','column',str(a.column).lower(),'-e','automation',a.automation,'org.pag.test.p11semantics.test/org.pag.semantics.SemanticsProbe'],stdout=f,stderr=subprocess.STDOUT,timeout=1200)
   text=log.read_text();rows=[line.split(',') for line in text.splitlines() if line.startswith('SEM,')]
   begin={int(r[3]) for r in rows if r[5]=='BEGIN'};passed={int(r[3]) for r in rows if r[5]=='PASS'}
   crash={int(r[3]) for r in rows if r[5]=='CRASH' and int(r[3]) in begin}
   failed={int(r[3]) for r in rows if r[5]=='HARNESS_FAIL' and int(r[3]) in begin}
   if 'Process crashed' in text and begin:crash|=(begin-passed or {max(begin)})
   # Never turn a partial/killed run into PASS. Record and continue remaining independent trials.
   completed=passed|crash|failed
   if begin-completed:failed|=begin-completed;completed|=begin
   if not begin:raise RuntimeError(f'No trial started; inspect local log {log}')
   assert completed==set(range(done,max(completed)+1))
   passes+=len(passed-crash-failed);crashes+=len(crash);harness+=len(failed-crash)
   attempts.extend({'trial':int(r[3]),'phase':r[4],'event':r[5],'detail':','.join(r[6:])} for r in rows if r[5] in ['CRASH','HARNESS_FAIL'])
   done=max(completed)+1
  rec={'variant':v,'font':font,'reuse':a.reuse,'column':a.column,'automation':a.automation,'runs':done,'pass':passes,'crash':crashes,'harness_fail':harness,'failures':attempts};records.append(rec)
  (a.output/'summary.json').write_text(json.dumps(records,indent=2));print(json.dumps({k:val for k,val in rec.items() if k!='failures'}),flush=True)
