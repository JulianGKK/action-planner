"""Verify stored §104 results only; no install, network or product-data access."""
from pathlib import Path
import hashlib, json, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
for directory, expected in [('final-data-xml',97),('final-app-xml',103),('extra-17-data-xml',10)]:
    total=0
    for path in (root/directory).glob('TEST-*.xml'):
        suite=ET.parse(path).getroot()
        assert int(suite.attrib['failures'])==int(suite.attrib['errors'])==0, path
        total+=int(suite.attrib['tests'])
    assert total==expected,(directory,total)
for scenario,name in [('migration','device-migration-04'),('migration_abort','device-migration-abort-01'),
                      ('migration_data','device-migration-data-02'),('native_revisions','device-native-revisions-02'),
                      ('cas_aba','device-cas-02'),('person_ui','device-person-ui-01'),
                      ('publication','device-publication-01'),('editors_ui','device-editors-ui-05')]:
    text=(root/(name+'.txt')).read_text()
    assert f'P104,{scenario},CASE,ANDROID_PASS' in text and f'P104 PASS {scenario}' in text,name
assert json.loads((root/'resume-cleanup.json').read_text())==dict(fixture_roots=[],protected_packages_unchanged=True)
assert json.loads((root/'resume-protected-before.json').read_text())==json.loads((root/'resume-protected-after.json').read_text())
diff=json.loads((root/'schema-diff.json').read_text())
assert {d['table'] for d in diff}=={'persons','roles','person_role_plans','person_operational_states','tasks'}
assert all(d['indices_equal'] and d['foreign_keys_equal'] and len(d['added_fields'])==1 for d in diff)
print('P104 stored evidence PASS: host XML, eight isolated P77 scenarios, schema diff, cleanup/protected inventory')
