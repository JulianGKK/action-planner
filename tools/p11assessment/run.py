#!/usr/bin/env python3
"""Only isolated APKs and synthetic instrumentation; never product install/data/credentials."""
import json, pathlib, subprocess, argparse
p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');p.add_argument('--allow-failure',action='store_true');p.add_argument('--sizes',nargs='+',choices=['small','typical','large'],default=['small','typical','large']);args=p.parse_args()
adb=['/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb','-s','RFCT1186YRJ']
def call(*a,**kw):return subprocess.run(list(a),check=True,text=True,**kw)
for folder,expected in [('app/build/outputs/apk/debug','org.pag.perf.p11probe'),('app/build/outputs/apk/androidTest/debug','org.pag.perf.p11probe.test')]:
    data=json.loads(pathlib.Path(folder,'output-metadata.json').read_text())
    if data['applicationId']!=expected:raise SystemExit('Refusing non-isolated APK metadata')
    if args.install:call(*adb,'install','-r',str(pathlib.Path(folder,data['elements'][0]['outputFile'])))
assert call(*adb,'shell','getprop','ro.product.model',capture_output=True).stdout.strip()=='SM-M526BR'
assert call(*adb,'shell','getprop','ro.build.version.sdk',capture_output=True).stdout.strip()=='33'
for size in args.sizes:
    for variant in ['A','B','C']:
        output=pathlib.Path(f'.prototype-cache/assessment-{size}-{variant}.txt')
        with output.open('w') as log:
            r=call(*adb,'shell','am','instrument','-w','-e','variant',variant,'-e','size',size,'org.pag.perf.p11probe.test/org.pag.prototype.ActiveWorkspaceAssessmentProbe',stdout=log,stderr=subprocess.STDOUT,timeout=1200)
        text=output.read_text()
        passed='ASSESSMENT_PASS' in text
        print(f'{size} {variant} '+('PASS' if passed else 'FAIL'),flush=True)
        assert passed or args.allow_failure, f'Isolated probe failed: {output}'
