"""Explicit P77-only §102 installation/run/cleanup inventory; synthetic fixtures only."""
from pathlib import Path
import hashlib, json, re, subprocess, sys, os

OUT = Path(os.environ.get('P102_EVIDENCE_OUT', 'docs/p11-p102'))
OUT.mkdir(parents=True, exist_ok=True)
ADB = '/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb'
BUILD = '/home/julian-tio-valenzuela/Android/Sdk/build-tools/36.0.0/'
SERIAL = 'RFCT1186YRJ'
SIGNER = '9ffc3a0e025c6fbf53d67d164669a5cc9cdc1481f322d858299f5db1e4982134'
TARGET = 'org.pag.prototype.a.p77runtime'
PROTECTED = ['org.pag.prototype.a', 'org.pag.prototype.a.p76demo', 'org.pag.perf.p11probe']

def call(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)

def device(*args):
    return call(ADB, '-s', SERIAL, *args)

def inventory():
    assert f'{SERIAL} ' in call(ADB, 'devices', '-l')
    return {pkg: [s.strip() for s in device('shell', 'dumpsys', 'package', pkg).splitlines()
                  if re.search(r'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=', s)]
            for pkg in PROTECTED}

mode = sys.argv[1]
if mode == 'install':
    (OUT/'resume-protected-before.json').write_text(json.dumps(inventory(), indent=2)+'\n')
    records = []
    pairs = [(TARGET, Path('app/build/outputs/apk/p77runtime/app-p77runtime.apk')),
             (TARGET+'.test', Path('app/build/outputs/apk/androidTest/p77runtime/app-p77runtime-androidTest.apk'))]
    for pkg, apk in pairs:
        manifest = call(BUILD+'aapt', 'dump', 'xmltree', str(apk), 'AndroidManifest.xml')
        assert f'A: package="{pkg}"' in manifest
        assert SIGNER in call(BUILD+'apksigner', 'verify', '--print-certs', str(apk))
        if pkg.endswith('.test'):
            assert 'org.pag.prototype.P102LeadershipRecoveryProbe' in manifest
            assert set(re.findall(r'android:targetPackage\([^)]*\)="([^"]+)"', manifest)) == {TARGET}
        else:
            assert 'org.pag.prototype.MainActivity' in manifest
        records.append(dict(package=pkg, sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), signer=SIGNER))
    for pkg, apk in pairs:
        assert 'Success' in device('install', '-r', str(apk))
    (OUT/'resume-apks.json').write_text(json.dumps(records, indent=2)+'\n')
    (OUT/'resume-device.txt').write_text(call(ADB, 'devices', '-l').rstrip()+'\n')
elif mode == 'run':
    scenario, name = sys.argv[2:]
    assert scenario in ('normal_ready','present_ui','absent_ui','unknown_alias','content_refusal','crash_retry','lock_before','lock_during','lock_after','status_change','delete_pending_ui','cleanup_receipt')
    assert re.fullmatch('[a-z0-9-]+', name)
    with (OUT/(name+'.txt')).open('w') as report:
        completed = subprocess.run([ADB, '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
                                   '-e', 'scenario', scenario,
                                   TARGET+'.test/org.pag.prototype.P102LeadershipRecoveryProbe'],
                                  stdout=report, stderr=subprocess.STDOUT, timeout=600)
    raw = (OUT/(name+'.txt')).read_text()
    assert completed.returncode == 0 and f'P102,{scenario},CASE,ANDROID_PASS' in raw and f'P102 PASS {scenario}' in raw, raw[-3000:]
elif mode == 'after':
    current = inventory()
    assert current == json.loads((OUT/'resume-protected-before.json').read_text())
    entries = device('shell', 'run-as', TARGET, 'ls', '-1', 'no_backup').splitlines()
    assert not any(s.startswith('p11-fixture-') for s in entries)
    (OUT/'resume-protected-after.json').write_text(json.dumps(current, indent=2)+'\n')
    (OUT/'resume-cleanup.json').write_text(json.dumps(dict(fixture_roots=[], protected_packages_unchanged=True))+'\n')
else:
    raise ValueError(mode)
print('P102', mode, 'checks passed')
