#!/usr/bin/env python3
"""Same synthetic model-driven ProductScreen instrumentation against explicit old/current isolated APKs."""
import argparse,json,os,pathlib,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--adb',default=os.environ.get('ADB','adb'));p.add_argument('--serial',default=os.environ.get('ANDROID_SERIAL'));p.add_argument('--aapt',required=True);p.add_argument('--target-apk',type=pathlib.Path,required=True);p.add_argument('--test-apk',type=pathlib.Path,required=True);p.add_argument('--runs',type=int,default=50);p.add_argument('--font',choices=['1','2','3.5','alternate'],default='2');p.add_argument('--automation',choices=['true','false'],default='true');p.add_argument('--reuse',action='store_true');p.add_argument('--runner',choices=['SemanticsActualProbe','SemanticsProductRepro'],default='SemanticsActualProbe');p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
if not a.serial or not 1<=a.runs<=100:p.error('explicit serial and 1..100 runs required')
if not pathlib.Path('.prototype-cache/debug.keystore').is_file():raise SystemExit('Existing keystore missing; stop')
adb=[a.adb,'-s',a.serial];a.output.mkdir(parents=True,exist_ok=True)
for apk,pkg in [(a.target_apk,'org.pag.perf.p11probe'),(a.test_apk,'org.pag.perf.p11probe.test')]:
 xml=subprocess.check_output([a.aapt,'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True);assert f'A: package="{pkg}" ' in xml
 subprocess.run(adb+['install','-r',str(apk)],check=True)
assert subprocess.check_output(adb+['shell','getprop','ro.build.version.sdk'],text=True).strip()=='33'
versions={}
with zipfile.ZipFile(a.target_apk) as z:
 for key in ['compose.material3_material3','compose.ui_ui','compose.runtime_runtime','compose.foundation_foundation']:versions[key]=z.read('META-INF/androidx.'+key+'.version').decode().strip()
assert set(versions.values())=={'1.4.0','1.12.1'},versions
results={'runs':0,'pass':0,'crash':0,'harness_fail':0,'runner':a.runner,'font':a.font,'automation':a.automation,'reuse':a.reuse,'versions':versions,'failures':[]}
while results['runs']<a.runs:
 start=results['runs'];log=a.output/f'from-{start}.txt'
 with log.open('w') as f:subprocess.run(adb+['shell','am','instrument','-w','-e','runs',str(a.runs-start),'-e','first',str(start),'-e','font',a.font,'-e','automation',a.automation,'-e','reuse',str(a.reuse).lower(),'org.pag.perf.p11probe.test/org.pag.prototype.'+a.runner],stdout=f,stderr=subprocess.STDOUT,check=True,timeout=1800)
 text=log.read_text();rows=[l.split(',') for l in text.splitlines() if l.startswith('ACT,')]
 begun={int(r[2]) for r in rows if r[4]=='BEGIN'};passed={int(r[2]) for r in rows if r[4]=='PASS'};crash={int(r[2]) for r in rows if r[4]=='CRASH' and int(r[2]) in begun};failed={int(r[2]) for r in rows if r[4]=='HARNESS_FAIL' and int(r[2]) in begun}
 if 'Process crashed' in text and begun:crash|=(begun-passed or {max(begun)})
 if not begun:raise RuntimeError('No actual UI trial started; inspect ignored local log')
 failed|=begun-passed-crash
 assert begun==set(range(start,max(begun)+1))
 results['runs']=max(begun)+1;results['pass']+=len(passed-crash-failed);results['crash']+=len(crash);results['harness_fail']+=len(failed-crash)
 results['failures'].extend({'trial':int(r[2]),'font':r[1],'phase':r[3],'event':r[4],'detail':','.join(r[5:])} for r in rows if r[4] in ['CRASH','HARNESS_FAIL'])
 (a.output/'summary.json').write_text(json.dumps(results,indent=2))
print(json.dumps({k:v for k,v in results.items() if k!='failures'}),flush=True)
