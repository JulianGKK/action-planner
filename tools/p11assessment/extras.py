#!/usr/bin/env python3
"""Additional isolated-only assessment probes. Never touches product package/data."""
import json,pathlib,subprocess,shutil
adb=['/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb','-s','RFCT1186YRJ']
folder=pathlib.Path('app/build/outputs/apk/androidTest/debug');m=json.loads((folder/'output-metadata.json').read_text());
if m['applicationId']!='org.pag.perf.p11probe.test':raise SystemExit('Refusing non-isolated test APK')
subprocess.run(adb+['install','-r',str(folder/m['elements'][0]['outputFile'])],check=True)
def probe(name,args,component,marker):
    with open('.prototype-cache/'+name+'.txt','w') as log:
        subprocess.run(adb+['shell','am','instrument','-w']+args+[component],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=1200)
    passed=marker in pathlib.Path('.prototype-cache/'+name+'.txt').read_text()
    print(name+(' PASS' if passed else ' FAIL'),flush=True)
    return passed
if not probe('assessment-memory',[],'org.pag.perf.p11probe.test/org.pag.prototype.AssessmentMemoryProbe','MEMORY_PASS'):raise SystemExit('Memory probe failed')
p=pathlib.Path('.prototype-cache/assessment-large-A.txt')
if p.exists() and not pathlib.Path('.prototype-cache/assessment-large-first-crash.txt').exists():shutil.copyfile(p,'.prototype-cache/assessment-large-first-crash.txt')
subprocess.run(['python3','tools/p11assessment/run.py','--sizes','large','--allow-failure'],check=True)
for v in ['A','B','C']:
    assert probe('assessment-scroll-'+v,['-e','variant',v,'-e','size','typical','-e','scrollOnly','true'],'org.pag.perf.p11probe.test/org.pag.prototype.ActiveWorkspaceAssessmentProbe','ASSESSMENT_PASS')
folder=pathlib.Path('data/build/outputs/apk/androidTest/debug');m=json.loads((folder/'output-metadata.json').read_text());
if m['applicationId']!='org.pag.data.unlockprobe':raise SystemExit('Refusing non-isolated storage test APK')
subprocess.run(adb+['install','-r',str(folder/m['elements'][0]['outputFile'])],check=True)
assert probe('assessment-workspace-regression',['-e','class','org.pag.data.product.P11WorkspaceStorageTest'],'org.pag.data.unlockprobe/androidx.test.runner.AndroidJUnitRunner','OK (12 tests)')
