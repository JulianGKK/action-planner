#!/usr/bin/env python3
"""Run existing isolated full UI regressions; no product package is accepted."""
import argparse,json,os,pathlib,subprocess,re,hashlib
p=argparse.ArgumentParser();p.add_argument('--adb',default=os.environ.get('ADB','adb'));p.add_argument('--serial',default=os.environ.get('ANDROID_SERIAL'));p.add_argument('--aapt',required=True);p.add_argument('--target-apk',type=pathlib.Path,required=True);p.add_argument('--test-apk',type=pathlib.Path,required=True);p.add_argument('--navigation',choices=['auto','legacy','parents','nodes','directed','touch','sweep'],default='auto');p.add_argument('--diagnostics',choices=['off','phases','detailed'],default='phases');p.add_argument('--task-checkpoint',choices=['full','summary'],default='full');p.add_argument('--task-runs',type=int,default=5);p.add_argument('--memory-runs',type=int,default=1);p.add_argument('--function-runs',type=int,default=1);p.add_argument('--function-font',choices=['1','2','both'],default='both');p.add_argument('--memory-font',choices=['1','2','both'],default='both');p.add_argument('--suites',nargs='+',choices=['tasks','functions','memory','lists','occlusion'],default=['tasks','functions','memory','lists']);p.add_argument('--fonts',nargs='+',choices=['1','2','3.5'],default=['1','2']);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--witness',choices=['none','paired'],default='none');p.add_argument('--hub-observation',choices=['active','minimal'],default='active');a=p.parse_args()
if a.hub_observation=='minimal' and a.witness!='none':p.error('minimal observation records only after exhaustion; use witness none')
if not a.serial or not 1<=a.task_runs<=50 or not 1<=a.memory_runs<=50 or not 1<=a.function_runs<=50:p.error('explicit serial and bounded runs required')
if not pathlib.Path('.prototype-cache/debug.keystore').is_file():raise SystemExit('Existing keystore missing; stop')
adb=[a.adb,'-s',a.serial];a.output.mkdir(parents=True,exist_ok=True)
# Validate BOTH packages and certificate equality before installing either APK.
signatures=[]
for apk,pkg in [(a.target_apk,'org.pag.perf.p11probe'),(a.test_apk,'org.pag.perf.p11probe.test')]:
 xml=subprocess.check_output([a.aapt,'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
 if f'A: package="{pkg}" ' not in xml:raise SystemExit('Unexpected isolated package ID; stop before installation')
 cert=subprocess.check_output([str(pathlib.Path(a.aapt).resolve().with_name('apksigner')),'verify','--print-certs',str(apk)],text=True)
 signatures.append(re.findall(r'certificate SHA-256 digest: ([0-9a-f]{64})',cert))
if not signatures[0] or signatures[0]!=signatures[1]:raise SystemExit('Isolated APK certificates differ; stop before installation')
for apk in [a.target_apk,a.test_apk]:subprocess.run(adb+['install','-r',str(apk)],check=True)
assert subprocess.check_output(adb+['shell','getprop','ro.build.version.sdk'],text=True).strip()=='33'
cases=[]
if 'tasks' in a.suites:
 for font in a.fonts:
  for i in range(a.task_runs):cases.append(('Ux3cTasksProbe',['-e','font',font,'-e','checkpoint',a.task_checkpoint],f'tasks-{font}-{i}','TASK_SUMMARY_DIAGNOSTIC_PASS' if a.task_checkpoint=='summary' else 'UX3c synthetic real ProductScreen:'))
if 'functions' in a.suites:
 for i in range(a.function_runs):cases.append(('Ux3dFunctionsProbe',[] if a.function_font=='both' else ['-e','font',a.function_font],f'functions-{a.function_font}-{i}','UX3d synthetic real ProductScreen:'))
if 'memory' in a.suites:
 for i in range(a.memory_runs):cases.append(('P9MemoryReportProbe',[] if a.memory_font=='both' else ['-e','font',a.memory_font],f'memory-{a.memory_font}-{i}','P9 synthetic ProductScreen'))
if 'lists' in a.suites:cases.append(('ListStabilityProbe',[],'lists','LIST_STABILITY_PASS'))
if 'occlusion' in a.suites:cases.append(('UiFeedbackOcclusionProbe',[],'occlusion','OCCLUSION_GEOMETRY_PASS'))
results=[]
for runner,args,label,marker in cases:
 log=a.output/(label+'.txt')
 execution_failure=None
 try:
  with log.open('w') as f:subprocess.run(adb+['shell','am','instrument','-w','-e','navigation',a.navigation,'-e','diagnostics',a.diagnostics,'-e','witness',a.witness,'-e','hubObservation',a.hub_observation]+args+['org.pag.perf.p11probe.test/org.pag.prototype.'+runner],stdout=f,stderr=subprocess.STDOUT,check=True,timeout=1200)
 except subprocess.TimeoutExpired:execution_failure='TIMEOUT'
 except subprocess.CalledProcessError:execution_failure='HARNESS_ERROR'
 text=log.read_text();crash='Process crashed' in text or 'UI_UNCAUGHT,' in text
 passed=marker in text and not crash and execution_failure is None
 category=execution_failure or ('PASS' if passed else 'APP_CRASH' if crash else 'HARNESS_ERROR' if 'INSTRUMENTATION_FAILED' in text else 'UNREACHABLE' if 'category=UNREACHABLE' in text else 'TIMEOUT' if 'TimeoutException' in text else 'ASSERTION' if 'probe failed' in text or 'LIST_STABILITY_FAIL' in text or 'GEOMETRY_FAIL' in text else 'HARNESS_ERROR')
 phase=re.findall(r'phase=([A-Za-z0-9_+.-]+)',text)
 rec={'case':label,'scope':'SUMMARY_DIAGNOSTIC' if runner=='Ux3cTasksProbe' and a.task_checkpoint=='summary' else 'GEOMETRY_DIAGNOSTIC' if runner=='UiFeedbackOcclusionProbe' else 'FULL_UI','navigation':a.navigation,'diagnostics':a.diagnostics,'witness':a.witness,'hub_observation':a.hub_observation,'target_sha256':hashlib.sha256(a.target_apk.read_bytes()).hexdigest(),'test_sha256':hashlib.sha256(a.test_apk.read_bytes()).hexdigest(),'pass':passed,'crash':crash,'status':category,'phase':phase[-1] if phase else None};results.append(rec)
 (a.output/'summary.json').write_text(json.dumps(results,indent=2));print(json.dumps(rec),flush=True)

 # A timed-out on-device instrumentation may still be running. Never overlap/retry its writes.
 if execution_failure:raise SystemExit('Stopped after recorded execution failure; inspect isolated run before continuing')
