"""Check source isolation and the existing release build's dex/manifest; no installation."""
from pathlib import Path
import hashlib, json, subprocess, zipfile

root = Path(__file__).parent
protected = ['app/src/main', 'data/src/main', 'domain', 'security', 'app/build.gradle.kts',
             'data/build.gradle.kts', 'settings.gradle.kts', 'build.gradle.kts']
assert not subprocess.check_output(['git', 'diff', '--name-only', '0a8f785', '--', *protected], text=True).strip()
apk = Path('app/build/outputs/apk/release/app-release-unsigned.apk')
forbidden = ['P86FullWorkspaceActivity', 'P86FullWorkspaceProbe', 'P86WorkspaceDiagnostics',
             'P77ProductRuntimeHarness', 'p86-stress']
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.endswith('.dex') or name == 'AndroidManifest.xml':
            raw = archive.read(name)
            assert all(s.encode() not in raw and s.encode('utf-16le') not in raw for s in forbidden)
(root/'resume-isolation.json').write_text(json.dumps(dict(
    product_sources_and_build_config_unchanged=True,
    existing_release_apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
    forbidden_absent=forbidden,
    note='Existing release-isolation-build.log artifact; no release installation'), indent=2)+'\n')
print('Source and existing release artifact isolation PASS')
