from pathlib import Path
import subprocess,sys,json,time
adb='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb';serial='RFCT1186YRJ'
out=Path('docs/p11-p84')
series,profile,case=sys.argv[1:]
start=time.monotonic()
try:
 p=subprocess.run([adb,'-s',serial,'shell','am','instrument','-w','-r','-e','fixture',profile,'-e','scenario',case,'org.pag.prototype.a.p77runtime.test/org.pag.prototype.P84OutstandingCoreProbe'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=240)
 body=p.stdout
 status='ANDROID-GREEN' if f'P84 PASS {profile} {case}' in body and f'P84_CASE,{profile},{case},ANDROID-GREEN' in body and p.returncode==0 else 'ANDROID-RED' if 'P84 FAIL' in body else 'HARNESS-ABBRUCH'
except subprocess.TimeoutExpired as e:
 body=(e.stdout or b'').decode() if isinstance(e.stdout,bytes) else (e.stdout or '')
 body+='\nHOST TIMEOUT; no acceptance\n';status='HARNESS-ABBRUCH'
(out/f'{series}-{profile}-{case}-runner.txt').write_text(body)
print(json.dumps(dict(series=series,fixture=profile,case=case,status=status,seconds=round(time.monotonic()-start,3))),flush=True)
