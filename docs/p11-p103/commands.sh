#!/usr/bin/env bash
# Repository root; exact targeted selectors in host-command.json, no full suites.
set -euo pipefail
python3 - <<'PY'
import json,subprocess
subprocess.run(json.load(open('docs/p11-p103/host-command.json')),check=True)
PY
./gradlew -PpagTestBuildType=p77runtime :app:assembleP77runtime :app:assembleP77runtimeAndroidTest :app:verifyP77RuntimeIsolation --offline --console=plain
# Device reruns: choose a NEW evidence directory; never overwrite committed raw logs.
# export P103_EVIDENCE_OUT=docs/p11-p103/review-device
# python3 docs/p11-p103/run-device.py install
# python3 docs/p11-p103/run-device.py run cas_aba cas-aba
# python3 docs/p11-p103/run-device.py run person_ui person-ui
# python3 docs/p11-p103/run-device.py run publication publication
# python3 docs/p11-p103/run-device.py after
