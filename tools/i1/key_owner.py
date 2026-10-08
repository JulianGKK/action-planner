#!/usr/bin/env python3
"""Synthetic I1 keys only. No intentional key persistence; no transport security claim."""
import argparse
import json
import os
import secrets
import socket
import threading
from pathlib import Path

MAGIC = b'I1K1'

def exact(conn, size):
    data = bytearray()
    while len(data) < size:
        part = conn.recv(size - len(data))
        if not part:
            raise ValueError('I1-PROTOCOL')
        data.extend(part)
    return data

def serve(root):
    root.mkdir(parents=True, exist_ok=True)
    os.umask(0o077)
    meta = root / 'owner.json'
    control_path = root / 'control.sock'
    if meta.exists() or control_path.exists():
        raise SystemExit('I1-OWNER-CONTEXT-EXISTS')
    session = secrets.token_hex(16)  # Non-secret context identifier, never a key.
    keys = bytearray(secrets.token_bytes(64))  # Generated exactly once for this owner.
    lost = threading.Event()
    tcp = socket.socket()
    tcp.bind(('127.0.0.1', 0))
    tcp.listen(8)
    control = socket.socket(socket.AF_UNIX)
    control.bind(str(control_path))
    control.listen(4)
    meta.write_text(json.dumps(dict(pid=os.getpid(), session=session,
                                    port=tcp.getsockname()[1], control=str(control_path))))
    def controls():
        while True:
            conn, _ = control.accept()
            with conn:
                request = conn.recv(32)
                if request == b'LOSE':
                    lost.set()
                    keys[:] = bytes(64)
                state = 'I1-HOST-KEY-LOST' if lost.is_set() else 'OWNER_ALIVE'
                conn.sendall(json.dumps(dict(pid=os.getpid(), session=session, state=state)).encode())
    threading.Thread(target=controls, daemon=True).start()
    while True:
        conn, _ = tcp.accept()
        with conn:
            conn.settimeout(5)
            try:
                request = exact(conn, 37)
                if request[:4] != MAGIC or request[4:36] != session.encode() or lost.is_set():
                    conn.sendall(b'FAIL')
                    continue
                mode = request[36]
                # Negative transport fixtures; never regenerate keys.
                if mode == 1:
                    conn.sendall(MAGIC + session.encode() + keys[:17])
                elif mode == 2:
                    conn.sendall(MAGIC + session.encode() + keys + b'X')
                elif mode == 3:
                    conn.sendall(b'BAD!' + session.encode() + keys)
                elif mode == 0:
                    conn.sendall(MAGIC + session.encode() + keys)
                else:
                    conn.sendall(b'FAIL')
            except (OSError, ValueError):
                pass  # Fixed malformed transport rejection; no exception/key output.

def status(root, lose=False):
    try:
        meta = json.loads((root / 'owner.json').read_text())
        with socket.socket(socket.AF_UNIX) as conn:
            conn.settimeout(3)
            conn.connect(meta['control'])
            conn.sendall(b'LOSE' if lose else b'STATUS')
            print(conn.recv(1024).decode())
    except (OSError, ValueError, KeyError):
        raise SystemExit('I1-HOST-KEY-LOST')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['serve', 'status', 'lose'])
    parser.add_argument('root', type=Path)
    args = parser.parse_args()
    if args.command == 'serve':
        serve(args.root)
    else:
        status(args.root, args.command == 'lose')
