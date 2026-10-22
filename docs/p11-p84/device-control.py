from pathlib import Path
import subprocess, sys, re, json, hashlib
root=Path.cwd(); out=root/'.prototype-cache/p84'; docs=root/'docs/p11-p84'
adb='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb'
build='/home/julian-tio-valenzuela/Android/Sdk/build-tools/36.0.0/'
out.mkdir(parents=True,exist_ok=True); docs.mkdir(parents=True,exist_ok=True)
serial='RFCT1186YRJ'
protected=['org.pag.prototype.a.p76demo','org.pag.prototype.a','org.pag.perf.p11probe']
pairs=[('org.pag.prototype.a.p77runtime',root/'app/build/outputs/apk/p77runtime/app-p77runtime.apk'),('org.pag.prototype.a.p77runtime.test',root/'app/build/outputs/apk/androidTest/p77runtime/app-p77runtime-androidTest.apk')]
def call(*args):return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT)
def device(*args):return call(adb,'-s',serial,*args)
def snapshot():
 assert 'model:SM_M526BR' in call(adb,'devices','-l') and serial in call(adb,'devices','-l')
 packages=device('shell','pm','list','packages')
 result={}
 for pkg in protected:
  assert 'package:'+pkg+'\n' in packages,pkg
  raw=device('shell','dumpsys','package',pkg)
  result[pkg]=[line.strip() for line in raw.splitlines() if re.search(r'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=|signatures=',line)]
  assert result[pkg]
 return result
mode=sys.argv[1]
if mode=='before':(out/'protected-before.json').write_text(json.dumps(snapshot(),indent=2))
elif mode=='install':
 assert snapshot()==json.loads((out/'protected-before.json').read_text())
 records=[]
 for pkg,apk in pairs:
  manifest=call(build+'aapt','dump','xmltree',str(apk),'AndroidManifest.xml')
  assert 'A: package="'+pkg+'"' in manifest
  if pkg.endswith('.test'):
   assert 'org.pag.prototype.P84OutstandingCoreProbe' in manifest and 'android:targetPackage' in manifest
  cert=call(build+'apksigner','verify','--print-certs',str(apk))
  expected='9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134'
  assert expected in cert
  path=device('shell','pm','path',pkg).strip()
  if path:
   assert path.startswith('package:') and len(path.splitlines())==1
   old=out/(pkg+'.installed.apk');device('pull',path[8:],str(old))
   assert expected in call(build+'apksigner','verify','--print-certs',str(old))
  (docs/('test-manifest.txt' if pkg.endswith('.test') else 'target-manifest.txt')).write_text(manifest)
  records.append({'package':pkg,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'signer':expected,'runner':'org.pag.prototype.P84OutstandingCoreProbe'})
 for pkg,apk in pairs:assert 'Success' in device('install','-r',str(apk))
 assert snapshot()==json.loads((out/'protected-before.json').read_text())
 (docs/'device.txt').write_text(call(adb,'devices','-l'))
 (out/'last-install.json').write_text(json.dumps(records,indent=2))
 print('P77 APK identity/signer/runner checks and protected package comparison passed. Updated only own P77 target and runner.')
elif mode=='after':
 current=snapshot();assert current==json.loads((out/'protected-before.json').read_text())
 (docs/'protected-packages.json').write_text(json.dumps(current,indent=2)+'\n')
 (docs/'installed-artifacts.json').write_text((out/'last-install.json').read_text()+'\n')
 print('All three protected installations unchanged and still present.')
else:raise ValueError(mode)
