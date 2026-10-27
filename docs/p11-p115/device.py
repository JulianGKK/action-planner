#!/usr/bin/env python3
"""Isolated P77 normal-credential root; protected packages are read-only."""
import hashlib,json,pathlib,subprocess,sys,datetime,time
ROOT=pathlib.Path(__file__).parent
ADB='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb'
PKG='org.pag.prototype.a.p77runtime'
BT='/home/julian-tio-valenzuela/Android/Sdk/build-tools/36.0.0/'
def run(args,timeout=120):
 p=subprocess.run(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
 assert p.returncode==0,p.stdout
 return p.stdout
def adb(*args):
 command=[ADB,'-s','RFCT1186YRJ',*args]
 if args[:3]!=('shell','dumpsys','package'):return run(command)
 # USB shell transport may close between inventory reads; retry read-only inventory only.
 for attempt in range(3):
  p=subprocess.run(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=30)
  if p.returncode==0:return p.stdout
  with (ROOT/'inventory-transport.jsonl').open('a') as f:f.write(json.dumps(dict(command=command,attempt=attempt,exit=p.returncode,output=p.stdout))+'\n')
  time.sleep(0.25)
 raise RuntimeError('Inventory transport failed after bounded read-only retries')
def inventory():return {p:[x.strip() for x in adb('shell','dumpsys','package',p).splitlines() if any(k in x for k in ['versionCode=','versionName=','firstInstallTime=','lastUpdateTime=','signatures='])] for p in ['org.pag.prototype.a','org.pag.prototype.a.p76demo','org.pag.perf.p11probe']}
def write(file,v):(ROOT/file).write_text(json.dumps(v,indent=2)+'\n')
mode=sys.argv[1]
if mode=='install':
 if (ROOT/'install.json').exists():
  old=ROOT/'install.json';old.rename(ROOT/('install-'+str(len(list(ROOT.glob('install-*.json')))+1)+'.json'))
 before=inventory();receipt=[]
 for file,pkg in [('app/build/outputs/apk/p77runtime/app-p77runtime.apk',PKG),('app/build/outputs/apk/androidTest/p77runtime/app-p77runtime-androidTest.apk',PKG+'.test')]:
  assert "package: name='"+pkg+"'" in run([BT+'aapt2','dump','badging',file])
  signer=run([BT+'apksigner','verify','--print-certs',file]);assert '9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134' in signer
  receipt.append(dict(file=file,package=pkg,sha256=hashlib.sha256(pathlib.Path(file).read_bytes()).hexdigest(),signer=signer,install=adb('install','--no-streaming','-r',file)))
 assert inventory()==before
 write('install.json',dict(utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),serial='RFCT1186YRJ',apks=receipt,protected_before=before))
 print('Isolated P77 installed; protected packages unchanged')
elif mode=='credential':
 phase=sys.argv[2];assert phase in ['idle','absolute','lifecycle']
 receipt=json.loads((ROOT/'install.json').read_text())
 for apk in receipt['apks']:assert hashlib.sha256(pathlib.Path(apk['file']).read_bytes()).hexdigest()==apk['sha256']
 file=ROOT/(phase+'-'+sys.argv[3]+'.txt');assert not file.exists()
 command=[ADB,'-s','RFCT1186YRJ','shell','am','instrument','-w','-r','-e','phase',phase,PKG+'.test/org.pag.prototype.P115CredentialProbe']
 with file.open('w') as f:
  proc=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
  for line in proc.stdout:
   f.write(line);f.flush()
   if 'P115 ' in line or 'INSTRUMENTATION_RESULT' in line:print(line.rstrip(),flush=True)
  code=proc.wait()
 assert code==0 and 'P115 PASS '+phase in file.read_text(),'Manual product probe not passed; raw evidence retained'
elif mode=='authority':
 receipt=json.loads((ROOT/'install.json').read_text())
 for apk in receipt['apks']:assert hashlib.sha256(pathlib.Path(apk['file']).read_bytes()).hexdigest()==apk['sha256']
 output=adb('shell','am','instrument','-w','-r',PKG+'.test/org.pag.prototype.P115AuthorityProbe')
 file=ROOT/('authority-'+sys.argv[2]+'.txt');assert not file.exists();file.write_text(output);print(output)
 assert 'P115 AUTHORITY PASS' in output,'Authority negative failed; raw evidence retained'
elif mode=='after':
 receipt=json.loads((ROOT/'install.json').read_text());after=inventory();assert after==receipt['protected_before']
 roots=[x for x in adb('shell','run-as',PKG,'ls','no_backup').splitlines() if x.startswith('p11-fixture-')]
 write('cleanup.json',dict(protected_packages_unchanged=True,fixture_roots=roots,retained_real_credential_root="no_backup/pag-db-v1 (encrypted, synthetic action/person/task; isolated UID)",physical_wipe_claim=False));assert not roots
 print('Protected packages unchanged; fixture cleanup confirmed')
