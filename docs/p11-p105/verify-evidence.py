"""Verify stored §105 evidence; no device installation or network calls."""
from pathlib import Path
import xml.etree.ElementTree as ET
import json,hashlib
root=Path(__file__).resolve().parent
for family,total in [('app',55),('data',35)]:
    count=0
    for p in (root/('host-'+family+'-xml')).glob('TEST-*.xml'):
        r=ET.parse(p).getroot()
        assert all(int(r.attrib[k])==0 for k in ['failures','errors','skipped']),p
        count+=int(r.attrib['tests'])
    assert count==total,(family,count)
extra=list((root/'host-publication-xml').glob('TEST-*.xml'))
assert len(extra)==1
suite=ET.parse(extra[0]).getroot()
assert int(suite.attrib['tests'])==1 and all(int(suite.attrib[k])==0 for k in ['failures','errors','skipped'])
for scenario in ['small','large']:
    data=(root/('device-'+scenario+'-02.txt')).read_text()
    for marker in [f'P105 PASS {scenario}',f'P105,{scenario},CASE,ANDROID_PASS',
                   'VM_SCOPES_QUIESCENT=true,original_core_TEST_alias_alive=true',
                   'queued_writer_retains_Expected=true','old_and_new_actual_graphs_simultaneous_TEST=true',
                   'form_draft_visible=true','global_owner_end=BLOCKED']:
        assert marker in data,marker
routes=(root/'device-four-routes.txt').read_text()
for area in ['HUB','PERSONS','TASKS','FUNCTIONS']:
    assert f'P96_ROUTE,{area},visible=PASS' in routes,area
assert 'P96 PASS' in routes
assert json.loads((root/'resume-cleanup.json').read_text())==dict(fixture_roots=[],protected_packages_unchanged=True)
assert json.loads((root/'resume-protected-before.json').read_text())==json.loads((root/'resume-protected-after.json').read_text())
contract=json.loads((root/'source-contract.json').read_text())
assert contract['production_diff_empty']
repo=root.parent.parent
for path,digest in contract['sha256'].items():
    assert hashlib.sha256((repo/path).read_bytes()).hexdigest()==digest,path
print('P105 PASS stored counterevidence: 91 host cases, two P105 scenarios, four P96 routes; global owner closure BLOCKED')
