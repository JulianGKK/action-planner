#!/usr/bin/env python3
"""Summarize raw metadata without upgrading any driver-only/manual result to acceptance."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as E,shutil,re
root=Path(__file__).parent

def events(file,prefix='P116'):
 out=[]
 for line in file.read_text().splitlines():
  marker='stream='+prefix+' '
  if marker in line and line.split(marker,1)[1].startswith('{'):out.append(json.loads(line.split(marker,1)[1]))
 return out
old=events(root/'ux-active-35-product-before.txt');new=events(root/'ux-verified-small-35.txt')
def first_scrolls(es):
 g=next(x for x in es if x.get('kind')=='GEOMETRY')
 return [{k:n.get(k) for k in ['class','bounds','scroll']} for n in g['nodes'] if n.get('scroll')]
geometry=dict(before_scroll_regions=first_scrolls(old),after_scroll_regions=first_scrolls(new),before_driver_only_checked_navigation_labels=True)
assert not geometry['before_scroll_regions'] and len(geometry['after_scroll_regions'])>=2
(root/'geometry-summary.json').write_text(json.dumps(geometry,indent=2)+'\n')
raw=[]
for f in sorted(root.glob('*.txt')):
 es=events(f);kinds=[x.get('kind') for x in es]
 if not es:continue
 outcome='FAIL' if 'FAIL' in kinds else 'PASS' if 'PASS' in kinds else 'UNASSESSED'
 if 'manual-talkback' in f.name:outcome='NOT_TESTED (manual skipped; driver result is not acceptance)'
 raw.append(dict(file=f.name,raw_result=outcome,kinds=[x for x in kinds if x!='GEOMETRY']))
(root/'raw-index.json').write_text(json.dumps(raw,indent=2)+'\n')
rows=[];host=root/'host';host.mkdir(exist_ok=True)
for f in sorted(Path('app/build/test-results/testP77runtimeUnitTest').glob('TEST-*.xml')):
 r=E.parse(f).getroot();d={k:int(r.get(k,0)) for k in ['tests','failures','errors','skipped']};d['name']=r.get('name');rows.append(d);shutil.copy(f,host/f.name)
assert not sum(x['failures']+x['errors']+x['skipped'] for x in rows)
(root/'host-summary.json').write_text(json.dumps(dict(suites=rows,total=sum(x['tests'] for x in rows)),indent=2)+'\n')
print('host',sum(x['tests'] for x in rows),'geometry',geometry)
