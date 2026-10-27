#!/usr/bin/env python3
"""Targeted real ProductScreen font/viewport checks; no manual TalkBack assertions."""
import subprocess,json,pathlib,datetime
root=pathlib.Path(__file__).parent
adb='/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb'
def shell(*args):return subprocess.check_output([adb,'-s','RFCT1186YRJ','shell',*args],text=True).strip()
font_before=shell('settings','get','system','font_scale')
(root/'font-before.json').write_text(json.dumps(dict(value=font_before,utc=datetime.datetime.now(datetime.timezone.utc).isoformat()))+'\n')
cases=[('verified-small-1','1','0','0','active','p111-small'),('verified-small-2','2','0','0','active','p111-small'),('verified-small-35','3.5','0','0','active','p111-small'),('verified-large-1','1','0','0','active','p111-large32'),('verified-large-35','3.5','0','0','active','p111-large32'),('landscape-1','1','1','0','active','p111-small'),('landscape-35','3.5','1','0','active','p111-small'),('highcontrast-2','2','0','1','active','p111-small'),('preparation-person-35','3.5','0','0','preparation','p111-small')]
results=[]
try:
 for label,font,rotation,contrast,mode,profile in cases:
  shell('settings','put','system','accelerometer_rotation','0');shell('settings','put','system','user_rotation',rotation)
  shell('settings','put','system','font_scale',font);shell('settings','put','secure','high_text_contrast_enabled',contrast)
  settings={k:shell('settings','get',a,b) for k,a,b in [('font','system','font_scale'),('rotation','system','user_rotation'),('contrast','secure','high_text_contrast_enabled')]}
  code=subprocess.run(['python3',str(root/'device.py'),'probe','ux',label,'-e','font',font,'-e','mode',mode,'-e','profile',profile]).returncode
  results.append(dict(label=label,settings=settings,driver_exit=code));(root/'matrix.json').write_text(json.dumps(results,indent=2)+'\n')
finally:
 import shlex
 baseline=json.loads((root/'device-settings-before.json').read_text());baseline['system:font_scale']=font_before
 after={}
 for k,v in baseline.items():
  ns,key=k.split(':');cmd=['settings','delete',ns,key] if v=='null' else ['settings','put',ns,key,v]
  subprocess.run([adb,'-s','RFCT1186YRJ','shell',shlex.join(cmd)],check=True,capture_output=True)
  after[k]=shell('settings','get',ns,key)
 (root/'settings-restored.json').write_text(json.dumps(dict(before=baseline,after=after,match=baseline==after),indent=2)+'\n')
 assert baseline==after
