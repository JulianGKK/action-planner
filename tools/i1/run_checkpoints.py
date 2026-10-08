#!/usr/bin/env python3
"""Host-only I1 orchestration; never receives keys or handles product namespaces."""
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = '/home/julian-tio-valenzuela/Android/Sdk/platform-tools/adb'
PACKAGE = 'org.pag.data.test'
RUNNER = PACKAGE + '/androidx.test.runner.AndroidJUnitRunner'
OWNER = Path('.prototype-cache/i1-ah-owner')
DOC = Path('docs/device-galaxy-m52-invasive.md')
ENV = dict(os.environ, ANDROID_USER_HOME=str(Path.cwd() / '.prototype-cache/android'))
META = json.loads((OWNER / 'owner.json').read_text())
SESSION = META['session']

def record(text):
    with DOC.open('a') as out:
        out.write('\n' + text + '\n')
    print(text, flush=True)

def adb(*args, timeout=30, check=True):
    result = subprocess.run([ADB, *args], env=ENV, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError('I1-ADB-BLOCKED')
    return result.stdout.strip()

def owner():
    result = subprocess.run(['python3', 'tools/i1/key_owner.py', 'status', str(OWNER)],
                            capture_output=True, text=True, check=True)
    state = json.loads(result.stdout)
    if state != dict(pid=META['pid'], session=SESSION, state='OWNER_ALIVE'):
        raise RuntimeError('I1-HOST-KEY-LOST')

def command(case, action):
    return [ADB, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'org.pag.data.prototype.I1TransportTest', '-e', 'i1_session', SESSION,
            '-e', 'i1_case', case, '-e', 'i1_action', action, RUNNER]

def prepare(case):
    owner()
    if adb('shell', 'pidof', PACKAGE, check=False):
        raise RuntimeError('I1-TEST-PROCESS-ALREADY-RUNNING')
    log = Path(f'.prototype-cache/i1-ah-{case}-prepare.log')
    if log.exists(): raise RuntimeError('I1-SCENARIO-ALREADY-STARTED')
    record(f'I1 {case} STARTED: Namespace i1-{SESSION}-{case}; Owner PID {META["pid"]}.\n'
           f'LAST SAFE CHECKPOINT: {case} preparation läuft möglicherweise; nie erneut prepare starten.')
    with log.open('w') as out:
        proc = subprocess.Popen(command(case, 'prepare'), env=ENV, stdout=out, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        text = log.read_text()
        if 'INSTRUMENTATION_STATUS: i1=READY_FOR_FORCE_STOP' in text:
            matches = re.findall(r'INSTRUMENTATION_STATUS: pid=(\d+)', text)
            phase = re.findall(r'INSTRUMENTATION_STATUS: phase=(\w+)', text)
            if len(matches) != 1 or len(phase) != 1 or proc.poll() is not None:
                raise RuntimeError('I1-CHECKPOINT-BLOCKED')
            pid = matches[0]
            if adb('shell', 'pidof', PACKAGE) != pid: raise RuntimeError('I1-PID-MISMATCH')
            record(f'I1 {case} READY_FOR_FORCE_STOP: PID {pid}, Phase {phase[0]}; Runner wartet.\n'
                   f'Paket {PACKAGE}, PID durch pidof bestätigt.\nLAST SAFE CHECKPOINT: {case} persistierter Checkpoint, Prozess wartet.')
            return proc, pid
        if proc.poll() is not None: raise RuntimeError('I1-PREPARE-FAILED')
        time.sleep(0.2)
    raise RuntimeError('I1-CHECKPOINT-TIMEOUT-PRESERVE-STATE')

def recover(case):
    owner()
    log = Path(f'.prototype-cache/i1-ah-{case}-recover.log')
    if log.exists(): raise RuntimeError('I1-RECOVERY-ALREADY-RUN')
    with log.open('w') as out:
        result = subprocess.run(command(case, 'recover'), env=ENV, stdout=out, stderr=subprocess.STDOUT, timeout=90)
    text = log.read_text()
    if result.returncode or 'OK (1 test)' not in text or 'INSTRUMENTATION_STATUS_CODE: -2' in text:
        raise RuntimeError('I1-RECOVERY-FAILED-PRESERVE-STATE')
    owner()
    record(f'I1 {case} PASS: neuer Prozess; Reconciliation zweimal, gleiche IDs, kein Ersatzbestand;\n'
           'Normal-cleanup-Marker fehlt; konsistenter Endzustand geprüft.\n'
           + ('Sentinel unverändert (außerhalb B4-Dateiinventar, im Szenario-Namespace).\n' if case in ('C','F') else '')
           + ('Vorhandene Ciphertext-Digests unverändert.\n' if case in ('B','C','D','R') else '')
           + f'LAST SAFE CHECKPOINT: {case} vollständig beendet; kein Test läuft.')

try:
    mode = sys.argv[1]
    if mode == 'force':
        for case in 'ABCDEFGH':
            proc, pid = prepare(case)
            adb('shell', 'am', 'force-stop', PACKAGE)
            proc.wait(timeout=15)
            if pid in adb('shell', 'pidof', PACKAGE, check=False).split():
                raise RuntimeError('I1-PID-STILL-ALIVE')
            if 'OK (1 test)' in Path(f'.prototype-cache/i1-ah-{case}-prepare.log').read_text():
                raise RuntimeError('I1-NORMAL-RETURN-NOT-FORCE-STOP')
            record(f'I1 {case}: echter Android am force-stop {PACKAGE}; alte PID {pid} verschwunden;\n'
                   f'wartender Runner abgebrochen. LAST SAFE CHECKPOINT: {case} beendet, Recovery ausstehend.')
            recover(case)
    elif mode == 'reboot-prepare':
        proc, pid = prepare('R')
        owner()
        record(f'I1 R: Reboot jetzt freigegeben, alter PID {pid}; Owner unverändert.\nLAST SAFE CHECKPOINT: R Checkpoint erreicht; Reboot/Unlock/Recovery ausstehend.')
        adb('reboot')
        record('I1 R: adb reboot ausgelöst, kein Power-loss. Nutzer-Unlock/ADB-Rückkehr erforderlich.')
    elif mode == 'reboot-recover':
        adb('reverse', 'localabstract:pag_i1', f'tcp:{META["port"]}')
        recover('R')
    else: raise RuntimeError('I1-HOST-MODE')
except Exception as error:
    # Fixed class-only diagnostic, never exception payload, keys, device identifier or paths.
    record(f'I1 BLOCKED: Host-Schritt fehlgeschlagen ({type(error).__name__}); Bestand erhalten.\n'
           'LAST SAFE CHECKPOINT: letzter dokumentierter Zustand; keine Wiederinitialisierung/Key-Ersetzung.')
    raise SystemExit(1)
