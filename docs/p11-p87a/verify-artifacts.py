"""Focused §87a artifact/source checks; no build, installation or release action."""
from pathlib import Path
import hashlib, json, subprocess, xml.etree.ElementTree as ET, zipfile

out=Path(__file__).parent
protected=['data/schemas','security','domain/src/main/kotlin/org/pag/domain/ActiveWorkspaceBudget.kt',
           'domain/src/main/kotlin/org/pag/domain/WorkspaceMutationInvalidation.kt',
           'data/src/main/kotlin/org/pag/data/product/WorkspaceWriteBoundary.kt',
           'data/src/main/kotlin/org/pag/data/product/TaskMutationBoundary.kt',
           'data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt',
           'data/src/main/kotlin/org/pag/data/product/ProductActiveCoreLifetime.kt',
           'domain/src/main/kotlin/org/pag/domain/Actions.kt',
           'data/src/main/kotlin/org/pag/data/SessionCoordinator.kt',
           'app/src/main/kotlin/org/pag/prototype/product/ProductViewModel.kt',
           'app/src/main/AndroidManifest.xml','app/build.gradle.kts','data/build.gradle.kts']
assert all(Path(p).exists() for p in protected)
assert not subprocess.check_output(['git','diff','--name-only','334f58b','--',*protected],text=True).strip()
apk=Path('app/build/outputs/apk/release/app-release-unsigned.apk')
forbidden=['P87TaskEventLimitProbe','P87TaskEventDiagnostics','p87-4999','p87-5001','P86FullWorkspaceActivity']
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.endswith('.dex') or name=='AndroidManifest.xml':
            data=archive.read(name)
            assert all(s.encode() not in data and s.encode('utf-16le') not in data for s in forbidden)
totals=dict(tests=0,failures=0,errors=0,skipped=0)
for report in out.glob('TEST-*.xml'):
    suite=ET.parse(report).getroot()
    for key in totals:totals[key]+=int(suite.attrib[key])
assert totals==dict(tests=58,failures=0,errors=0,skipped=0),totals
paths=subprocess.check_output(['git','diff','--name-only','334f58b'],text=True).splitlines()
paths += ['app/src/androidTest/kotlin/org/pag/prototype/P87TaskEventLimitProbe.kt',
          'data/src/debug/kotlin/org/pag/data/product/P87TaskEventDiagnostics.kt']
sources={p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in paths if p.endswith(('.kt','.py','.xml'))}
(out/'source-sha256.json').write_text(json.dumps(sources,indent=2)+'\n')
(out/'artifact-check.json').write_text(json.dumps(dict(
    host_tests=totals,unchanged=protected,release_forbidden_absent=forbidden,
    release_sha256=hashlib.sha256(apk.read_bytes()).hexdigest()),indent=2)+'\n')
print('P87 artifacts PASS:',totals)
