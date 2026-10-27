#!/usr/bin/env python3
"""Isolated P77 UX/A11y probes; synthetic authority, protected packages read-only."""
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
elif mode=='probe':
 kind=sys.argv[2];label=sys.argv[3];assert kind in ['ux','credential','authority']
 file=ROOT/(kind+'-'+label+'.txt');assert not file.exists()
 extras=sys.argv[4:]
 write('run-'+label+'.json',dict(utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),kind=kind,label=label,extras=extras,install=json.loads((ROOT/'install.json').read_text())))
 component={'ux':'P116UxProbe','credential':'P117CredentialContinuationProbe','authority':'P115AuthorityProbe'}[kind]
 command=[ADB,'-s','RFCT1186YRJ','shell','am','instrument','-w','-r',*extras,PKG+'.test/org.pag.prototype.'+component]
 with file.open('w') as f:
  proc=subprocess.Popen(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
  for line in proc.stdout:
   f.write(line);f.flush()
   if 'GEOMETRY' not in line:print(line.rstrip(),flush=True)
  code=proc.wait()
 output=file.read_text();assert code==0,output
 if {'ux':'P116 PASS','credential':'P117 CONTINUATION PASS','authority':'P115 AUTHORITY PASS'}[kind] not in output:sys.exit(2)
elif mode=='after':
 receipt=json.loads((ROOT/'install.json').read_text());after=inventory();assert after==receipt['protected_before']
 roots=[x for x in adb('shell','run-as',PKG,'ls','no_backup').splitlines() if x.startswith('p11-fixture-')]
 write('cleanup.json',dict(protected_packages_unchanged=True,fixture_roots=roots,retained_real_credential_root="no_backup/pag-db-v1 (encrypted, synthetic action/person/task; isolated UID)",physical_wipe_claim=False));assert not roots
 print('Protected packages unchanged; fixture cleanup confirmed')
