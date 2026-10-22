"""One explicit, isolated P77 case; unedited instrumentation output, no zero-JUnit PASS."""
from pathlib import Path
import subprocess, sys, json, time, selectors
adb='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb';serial='RFCT1186YRJ'
out=Path('docs/p11-p85b')
series,profile,case=sys.argv[1:]
assert profile in ('small','stress') and case in ('timings','touch','editor_lock','fences','storage_error')
assert series.replace('-','').isalnum()
start=time.monotonic();body=''
p=subprocess.Popen([adb,'-s',serial,'shell','am','instrument','-w','-r','-e','fixture',profile,'-e','scenario',case,'org.pag.prototype.a.p77runtime.test/org.pag.prototype.P85bTaskHistoryProbe'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
selector=selectors.DefaultSelector();selector.register(p.stdout,selectors.EVENT_READ)
with (out/f'{series}-{profile}-{case}-runner.txt').open('w',buffering=1) as report:
 while True:
  if time.monotonic()-start>240:
   p.kill();p.wait();line='\nHOST TIMEOUT; no acceptance\n';report.write(line);body+=line;break
  if selector.select(timeout=1):
   line=p.stdout.readline()
   if not line:break
   report.write(line);body+=line
   if 'P85b_' in line and 'P85b_DIAG' not in line:print(line.strip(),flush=True)
 p.wait()
status='ANDROID-GREEN' if f'P85b PASS {profile}' in body and f'P85b_CASE,{profile},{case},ANDROID-GREEN' in body and p.returncode==0 else 'ANDROID-RED' if 'P85b FAIL' in body else 'HARNESS-ABBRUCH'
print(json.dumps(dict(series=series,fixture=profile,case=case,status=status,seconds=round(time.monotonic()-start,3))),flush=True)
