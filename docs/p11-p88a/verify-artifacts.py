"""P88 source/artifact isolation; cached release is the unchanged §87a release build."""
from pathlib import Path
import hashlib, json, subprocess, xml.etree.ElementTree as ET, zipfile

out=Path(__file__).parent
protected=['app/src/main','data/src/main','domain','security','data/schemas',
           'app/build.gradle.kts','data/build.gradle.kts','settings.gradle.kts','build.gradle.kts']
assert not subprocess.check_output(['git','diff','--name-only','8f63044','--',*protected],text=True).strip()
totals=dict(tests=0,failures=0,errors=0,skipped=0)
for path in out.glob('TEST-*.xml'):
    root=ET.parse(path).getroot()
    for key in totals:totals[key]+=int(root.attrib[key])
assert totals==dict(tests=6,failures=0,errors=0,skipped=0)
apk=Path('app/build/outputs/apk/release/app-release-unsigned.apk')
release_hash=hashlib.sha256(apk.read_bytes()).hexdigest()
assert release_hash==json.loads(Path('docs/p11-p87a/artifact-check.json').read_text())['release_sha256']
forbidden=['P88StatementDiagnostics','P88StatementLifetimeProbe','P86FullWorkspaceActivity']
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.endswith('.dex') or name=='AndroidManifest.xml':
            data=archive.read(name)
            assert all(s.encode() not in data and s.encode('utf-16le') not in data for s in forbidden)
sources=['app/src/androidTest/AndroidManifest.xml',
         'app/src/androidTest/kotlin/org/pag/prototype/P88StatementLifetimeProbe.kt',
         'data/src/debug/kotlin/org/pag/data/product/P88StatementDiagnostics.kt']
(out/'source-sha256.json').write_text(json.dumps({p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in sources},indent=2)+'\n')
(out/'artifact-check.json').write_text(json.dumps(dict(host_tests=totals,unchanged=protected,
    cached_p87_release_sha256=release_hash,release_forbidden_absent=forbidden),indent=2)+'\n')
print('P88 isolation and six targeted host tests PASS')
