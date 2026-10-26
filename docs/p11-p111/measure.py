#!/usr/bin/env python3
"""Isolated P77 scalar calibration. No GC request, data export or production-package reset."""
import argparse,datetime,hashlib,json,os,pathlib,re,subprocess,threading,time
ROOT=pathlib.Path(__file__).parent
ADB='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb'
SERIAL='RFCT1186YRJ';PKG='org.pag.prototype.a.p77runtime'
BT='/home/julian-tio-valenzuela/Android/Sdk/build-tools/36.0.0/'
PROTECTED=['org.pag.prototype.a','org.pag.prototype.a.p76demo','org.pag.perf.p11probe']

def command(args,timeout=30,check=True):
 p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=timeout)
 if check and p.returncode:raise RuntimeError(str(args)+': '+p.stdout[-1500:])
 return p

def adb(*args,**kw):return command([ADB,'-s',SERIAL,*args],**kw)
def digest(path):return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def inventory():
 return {pkg:[x.strip() for x in adb('shell','dumpsys','package',pkg).stdout.splitlines() if any(k in x for k in ('versionCode=','versionName=','firstInstallTime=','lastUpdateTime=','signatures='))] for pkg in PROTECTED}

def install(build):
 assert 'BUILD SUCCESSFUL' in (ROOT/f'build-{build}.log').read_text()
 before=inventory();receipt=[]
 for file,pkg in [('app/build/outputs/apk/p77runtime/app-p77runtime.apk',PKG),('app/build/outputs/apk/androidTest/p77runtime/app-p77runtime-androidTest.apk',PKG+'.test')]:
  assert "package: name='"+pkg+"'" in command([BT+'aapt2','dump','badging',file]).stdout
  signer=command([BT+'apksigner','verify','--print-certs',file]).stdout
  assert '9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134' in signer
  receipt.append(dict(package=pkg,apk=file,sha256=digest(file),signer=signer,install=adb('install','-r',file,timeout=90).stdout))
 assert before==inventory()
 values={k:adb('shell','getprop',k).stdout.strip() for k in ['ro.product.manufacturer','ro.product.model','ro.build.version.sdk','ro.build.version.release','ro.build.version.security_patch','ro.build.fingerprint','dalvik.vm.heapgrowthlimit','dalvik.vm.heapsize']}
 meta=dict(build=build,serial=SERIAL,device=values,apks=receipt,protected_before=before,utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),synthetic_authentication=True)
 write(ROOT/f'install-{build}.json',meta);write(ROOT/'current-install.json',meta)
 files=sorted(p for base in ('app/src','data/src','domain/src') for p in pathlib.Path(base).rglob('*') if p.is_file())
 (ROOT/f'source-{build}.sha256').write_text(''.join(digest(p)+'  '+str(p)+'\n' for p in files))
 print('Installed isolated APKs',[(r['package'],r['sha256']) for r in receipt],flush=True)

def run(case,family,extra,expected='PASS'):
 assert re.fullmatch(r'[a-zA-Z0-9_.-]+',case)
 meta=json.loads((ROOT/'current-install.json').read_text())
 for a in meta['apks']:assert digest(a['apk'])==a['sha256'],'Built APK differs from installed receipt'
 folder=ROOT/'runs'/case;folder.mkdir(parents=True,exist_ok=False)
 apk=meta['apks'][0]['sha256'];test=meta['apks'][1]['sha256']
 names={'capacity':'P111CapacityProbe','consumer':'P110ConsumerProbe','functional':'P109FunctionalOwnerProbe','resource':'P100ResourceAcceptanceProbe','admission':'P107FullviewAdmissionProbe','writer':'P101WriterAcceptanceProbe','security':'P102LeadershipRecoveryProbe','cas':'P104EditorCasProbe'}
 args=[ADB,'-s',SERIAL,'shell','am','instrument','-w','-r','-e','p111','true','-e','p111Id',case,'-e','p111ApkHash',apk]
 for k,v in extra.items():args+=['-e',k,str(v)]
 args+=[PKG+'.test/org.pag.prototype.'+names[family]]
 write(folder/'manifest.json',dict(scenario_id=case,device=meta['device'],serial=SERIAL,apk_sha256=apk,test_apk_sha256=test,install_receipt='install-'+str(meta['build'])+'.json',command=args,expected=expected,utc_start=datetime.datetime.now(datetime.timezone.utc).isoformat(),sampling='500ms in-process; approximately 1s external dumpsys; observer overhead included; no forced GC'))
 stop=threading.Event();latest={};mutex=threading.Lock();logs=[];pid=0;external_errors=[]
 def external():
  nonlocal pid
  count=0;ime_count=0
  with (folder/'external.jsonl').open('w') as out:
   while not stop.is_set():
    with mutex:snap=latest.copy()
    current=snap.get('pid')
    if current:
     active=adb('shell','pidof',PKG,timeout=10,check=False).stdout.split()
     if str(current) not in active:
      stop.wait(0.5);continue # Never follow a reused PID belonging to another package.
     if pid!=current:
      pid=current
      log_file=(folder/f'logcat-{pid}.txt').open('w')
      proc=subprocess.Popen([ADB,'-s',SERIAL,'logcat','--pid='+str(pid),'-b','main','-b','system','-b','crash','-b','events','-v','epoch','-T','1','*:V'],stdout=log_file,stderr=subprocess.STDOUT,text=True)
      logs.append((proc,log_file))
     begin=time.time_ns()
     result=adb('shell','dumpsys','meminfo',str(pid),timeout=15,check=False)
     (folder/f'meminfo-{count:04}.txt').write_text(result.stdout)
     status=adb('shell','run-as',PKG,'cat',f'/proc/{pid}/status',timeout=10,check=False)
     selected=[line for line in status.stdout.splitlines() if line.startswith(('VmPeak:','VmSize:','VmHWM:','VmRSS:','RssAnon:','RssFile:','RssShmem:','Threads:'))]
     if count%5==0:
      maps=adb('shell','run-as',PKG,'cat',f'/proc/{pid}/smaps_rollup',timeout=10,check=False)
      (folder/f'smaps-{count:04}.txt').write_text(maps.stdout)
     item=dict(scenario_id=case,apk_sha256=apk,pid=pid,utc_start_ns=begin,utc_end_ns=time.time_ns(),last_internal_elapsed_ns=snap.get('elapsed_ns'),last_internal_session=snap.get('session'),last_internal_phase=snap.get('phase'),session_is_last_observed_not_atomic_with_dumpsys=True,meminfo_file=f'meminfo-{count:04}.txt',returncode=result.returncode,proc_status=selected)
     if snap.get('ui',{}).get('ime_visible') and ime_count<3:
      component=adb('shell','settings','get','secure','default_input_method').stdout.strip();pkg=component.split('/')[0]
      if re.fullmatch(r'[a-zA-Z0-9_.]+',pkg) and pkg!=PKG:
       foreign=adb('shell','dumpsys','meminfo',pkg,timeout=15,check=False)
       file=f'foreign-ime-{ime_count}.txt';(folder/file).write_text(foreign.stdout);item['foreign_ime']=dict(package=pkg,file=file,not_app_ram=True);ime_count+=1
     out.write(json.dumps(item)+'\n');out.flush();count+=1
    stop.wait(1)
 def external_safe():
  try:external()
  except Exception as exc:
   external_errors.append(type(exc).__name__+': '+str(exc))
   write(folder/'external-error.json',external_errors)
 worker=threading.Thread(target=external_safe,daemon=True);worker.start()
 p=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 lines=[];samples=[];events=[]
 try:
  with (folder/'instrumentation.txt').open('w') as raw,(folder/'samples.jsonl').open('w') as sample_file,(folder/'events.jsonl').open('w') as event_file:
   for line in p.stdout:
    raw.write(line);raw.flush();lines.append(line)
    for tag,destination,items in [('P111_JSON ',sample_file,samples),('P111_EVENT ',event_file,events)]:
     if tag in line:
      try:v=json.loads(line.split(tag,1)[1]);destination.write(json.dumps(v,ensure_ascii=False)+'\n');destination.flush();items.append(v)
      except json.JSONDecodeError:continue
      if tag=='P111_JSON ':
       with mutex:latest.clear();latest.update(v)
   code=p.wait()
 finally:
  stop.set();worker.join(30)
  for proc,f in logs:
   proc.terminate()
   try:proc.wait(timeout=5)
   except subprocess.TimeoutExpired:proc.kill();proc.wait()
   f.close()
 output=''.join(lines)
 prefix={'capacity':'P111','consumer':'P110','functional':'P109','resource':'P100','admission':'P107','writer':'P101','security':'P102','cas':'P104'}[family]
 passed=code==0 and prefix+' PASS ' in output
 if expected=='DIAGNOSTIC':passed=code==0 and ('P110 FAIL repeat-completed-overview' in output or 'P110 PASS repeat-completed-overview' in output)
 inspection=adb('shell','run-as',PKG,'ls','no_backup',check=False)
 roots=[v for v in inspection.stdout.splitlines() if v.startswith('p11-fixture-')] if inspection.returncode==0 else None
 inventory_error=None
 try:protected_ok=inventory()==meta['protected_before']
 except Exception as exc:protected_ok=None;inventory_error=type(exc).__name__+': '+str(exc)
 records=[s for s in samples if s.get('kind')=='sample'];passed=passed and roots==[] and protected_ok is True and (bool(records) or extra.get('p111')=='false') and not external_errors;summary=dict(case=case,family=family,passed=passed,expected=expected,process_returncode=code,apk_sha256=apk,test_apk_sha256=test,samples=len(records),events=[e['kind'] for e in events],protected_packages_unchanged=protected_ok,fixture_roots_remaining=roots,physical_upper_bound_claim=False,external_errors=external_errors,inventory_error=inventory_error,fixture_inspection_returncode=inspection.returncode,no_sampling_control=extra.get('p111')=='false')
 for key in ['art_used_bytes','native_allocated_bytes','pss_kib','fd_count']:
  values=[s[key] for s in records if key in s];summary[key+'_observed_min']=min(values) if values else None;summary[key+'_observed_max']=max(values) if values else None
 write(folder/'summary.json',summary);print(json.dumps(summary),flush=True)
 if not passed:print(output[-3500:],flush=True)
 return passed

if __name__=='__main__':
 parser=argparse.ArgumentParser();sub=parser.add_subparsers(dest='mode',required=True)
 ins=sub.add_parser('install');ins.add_argument('build',type=int)
 runp=sub.add_parser('run');runp.add_argument('case');runp.add_argument('family');runp.add_argument('--arg',action='append',default=[]);runp.add_argument('--diagnostic',action='store_true')
 args=parser.parse_args()
 if args.mode=='install':install(args.build)
 else:
  values=dict(v.split('=',1) for v in args.arg)
  if not run(args.case,args.family,values,'DIAGNOSTIC' if args.diagnostic else 'PASS'):raise SystemExit(1)
