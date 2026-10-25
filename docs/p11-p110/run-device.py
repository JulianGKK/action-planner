import subprocess,json,pathlib,hashlib,sys
root=pathlib.Path('docs/p11-p110');adb='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb';bt='/home/julian-tio-valenzuela/Android/Sdk/build-tools/36.0.0/'
def run(args):
 p=subprocess.run(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);assert p.returncode==0,p.stdout;return p.stdout
protected=['org.pag.prototype.a','org.pag.prototype.a.p76demo','org.pag.perf.p11probe']
def inventory():
 return {pkg:[x.strip() for x in run([adb,'shell','dumpsys','package',pkg]).splitlines() if any(k in x for k in ['versionCode=','versionName=','firstInstallTime=','lastUpdateTime=','signatures='])] for pkg in protected}
mode=sys.argv[1]
if mode=='install':
 before=inventory();(root/'protected-before.json').write_text(json.dumps(before,indent=2))
 receipt=[]
 prior=root/'apk-install.json'
 if prior.exists(): (root/('apk-install-'+str(len(list(root.glob('apk-install-*.json')))+1)+'.json')).write_text(prior.read_text())
 for apk,pkg in [('app/build/outputs/apk/p77runtime/app-p77runtime.apk','org.pag.prototype.a.p77runtime'),('app/build/outputs/apk/androidTest/p77runtime/app-p77runtime-androidTest.apk','org.pag.prototype.a.p77runtime.test')]:
  badging=run([bt+'aapt2','dump','badging',apk]);assert "package: name='"+pkg+"'" in badging
  signer=run([bt+'apksigner','verify','--print-certs',apk]);assert '9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134' in signer
  receipt.append(dict(package=pkg,sha256=hashlib.sha256(pathlib.Path(apk).read_bytes()).hexdigest(),signer=signer,install=run([adb,'install','-r',apk])))
 assert before==inventory();(root/'apk-install.json').write_text(json.dumps(receipt,indent=2))
elif mode=='run':
 scenario=sys.argv[2];label=sys.argv[3]
 output=run([adb,'shell','am','instrument','-w','-r','-e','scenario',scenario,'org.pag.prototype.a.p77runtime.test/org.pag.prototype.P109FunctionalOwnerProbe'])
 (root/('device-'+scenario+'-'+label+'.txt')).write_text(output);print(output)
 assert 'P109 PASS '+scenario in output, 'Device assertions failed'
elif mode=='consumer':
 scenario=sys.argv[2];label=sys.argv[3]
 args=[adb,'shell','am','instrument','-w','-r','-e','scenario',scenario,'org.pag.prototype.a.p77runtime.test/org.pag.prototype.P110ConsumerProbe']
 p=subprocess.Popen(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 lines=[]
 with (root/('device-p110-'+scenario+'-'+label+'.txt')).open('w') as raw:
  for line in p.stdout:
   lines.append(line);raw.write(line);raw.flush();print(line,end='',flush=True)
 assert p.wait()==0
 output=''.join(lines)
 assert 'P110 PASS '+scenario in output,'Consumer device assertions failed'
elif mode=='legacy':
 scenario=sys.argv[2];assert scenario in ['exposed','r-exact','r-over','t-exact','t-over']
 output=run([adb,'shell','am','instrument','-w','-r','-e','scenario',scenario,'org.pag.prototype.a.p77runtime.test/org.pag.prototype.P107FullviewAdmissionProbe'])
 (root/('device-p107-'+scenario+'.txt')).write_text(output);print(output)
 assert 'P107 PASS '+scenario in output,'Legacy regression failed'
elif mode=='legacy-batch':
 for scenario in ['exposed','r-exact','r-over','t-exact','t-over']:
  p=subprocess.run([sys.executable,__file__,'legacy',scenario],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=240)
  print('P107 '+scenario+': '+('PASS' if p.returncode==0 else 'FAIL'),flush=True)
  if p.returncode: print(p.stdout,flush=True);sys.exit(p.returncode)
elif mode=='batch':
 label=sys.argv[2]
 summary=[]
 for scenario in sys.argv[3:]:
  p=subprocess.run([sys.executable,__file__,'run',scenario,label],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=240)
  print(scenario+': '+('PASS' if p.returncode==0 else 'FAIL'),flush=True)
  summary.append(dict(scenario=scenario,log='device-'+scenario+'-'+label+'.txt',passed=p.returncode==0))
  (root/('batch-'+label+'.json')).write_text(json.dumps(summary,indent=2))
  if p.returncode: print(p.stdout,flush=True);sys.exit(p.returncode)
elif mode=='after':
 after=inventory();(root/'protected-after.json').write_text(json.dumps(after,indent=2));assert after==json.loads((root/'protected-before.json').read_text())
 roots=[x for x in run([adb,'shell','run-as','org.pag.prototype.a.p77runtime','ls','no_backup']).splitlines() if x.startswith('p11-fixture-')]
 (root/'cleanup.json').write_text(json.dumps({'protected_packages_unchanged':True,'fixture_roots':roots,'physical_wipe_claim':False},indent=2))
 assert not roots,roots
 print('Protected packages unchanged; no synthetic fixture roots remain')
