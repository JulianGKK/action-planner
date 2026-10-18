#!/usr/bin/env python3
"""Run existing isolated full UI regressions; no product package is accepted."""
import argparse,json,os,pathlib,subprocess
p=argparse.ArgumentParser();p.add_argument('--adb',default=os.environ.get('ADB','adb'));p.add_argument('--serial',default=os.environ.get('ANDROID_SERIAL'));p.add_argument('--aapt',required=True);p.add_argument('--target-apk',type=pathlib.Path,required=True);p.add_argument('--test-apk',type=pathlib.Path,required=True);p.add_argument('--task-runs',type=int,default=5);p.add_argument('--suites',nargs='+',choices=['tasks','functions','memory','lists'],default=['tasks','functions','memory','lists']);p.add_argument('--fonts',nargs='+',choices=['1','2','3.5'],default=['1','2']);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
if not a.serial or not 1<=a.task_runs<=50:p.error('explicit serial and bounded runs required')
if not pathlib.Path('.prototype-cache/debug.keystore').is_file():raise SystemExit('Existing keystore missing; stop')
adb=[a.adb,'-s',a.serial];a.output.mkdir(parents=True,exist_ok=True)
for apk,pkg in [(a.target_apk,'org.pag.perf.p11probe'),(a.test_apk,'org.pag.perf.p11probe.test')]:
 xml=subprocess.check_output([a.aapt,'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True);assert f'A: package="{pkg}" ' in xml
 subprocess.run(adb+['install','-r',str(apk)],check=True)
assert subprocess.check_output(adb+['shell','getprop','ro.build.version.sdk'],text=True).strip()=='33'
cases=[]
if 'tasks' in a.suites:
 for font in a.fonts:
  for i in range(a.task_runs):cases.append(('Ux3cTasksProbe',['-e','font',font],f'tasks-{font}-{i}','UX3c synthetic real ProductScreen:'))
if 'functions' in a.suites:cases.append(('Ux3dFunctionsProbe',[],'functions','UX3d synthetic real ProductScreen:'))
if 'memory' in a.suites:cases.append(('P9MemoryReportProbe',[],'memory','P9 synthetic ProductScreen'))
if 'lists' in a.suites:cases.append(('ListStabilityProbe',[],'lists','LIST_STABILITY_PASS'))
results=[]
for runner,args,label,marker in cases:
 log=a.output/(label+'.txt')
 with log.open('w') as f:subprocess.run(adb+['shell','am','instrument','-w']+args+['org.pag.perf.p11probe.test/org.pag.prototype.'+runner],stdout=f,stderr=subprocess.STDOUT,check=True,timeout=1200)
 text=log.read_text();crash='Process crashed' in text or 'UI_UNCAUGHT,' in text
 rec={'case':label,'pass':marker in text and not crash,'crash':crash};results.append(rec)
 (a.output/'summary.json').write_text(json.dumps(results,indent=2));print(json.dumps(rec),flush=True)
