#!/usr/bin/env python3
"""Read-only APK gate. Deliberately has no adb/install functionality."""
import argparse
import hashlib
import pathlib
import re
import subprocess

PACKAGE = "org.pag.perf.p11probe"
ACTIVITY = "org.pag.prototype.P11ManualActivity"


def validate(manifest, certs, expected):
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Expected a trusted certificate SHA-256 (64 hex characters)")
    if re.findall(r'A: package="([^"]+)"', manifest) != [PACKAGE]:
        raise ValueError("Unexpected package; product packages are forbidden")
    if "android:sharedUserId" in manifest:
        raise ValueError("Shared UID forbidden")
    blocks = re.split(r"(?m)^\s*E: ", manifest)[1:]
    def named(kind):
        return [re.search(r'A: android:name\([^)]*\)="([^"]+)"', block).group(1)
                for block in blocks if block.startswith(kind + " ")]
    if named("activity") != [ACTIVITY] or named("action") != ["android.intent.action.MAIN"] or named("category") != ["android.intent.category.LAUNCHER"]:
        raise ValueError("Unexpected launcher")
    permissions = [name for kind in ("uses-permission", "uses-permission-sdk-23", "permission") for name in named(kind)]
    if set(permissions) - {PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}:
        raise ValueError("Unexpected permission")
    names = re.findall(r'A: android:name\([^)]*\)="([^"]+)"', manifest)
    if "android.app.Application" not in names or ACTIVITY not in names:
        raise ValueError("Missing isolated application/launcher")
    if re.search(r"E: (service|provider|receiver|activity-alias|instrumentation)\b", manifest):
        raise ValueError("Unexpected component")
    if len(re.findall(r"E: activity\b", manifest)) != 1:
        raise ValueError("Expected only the manual activity")
    if "org.pag.prototype.MainActivity" in names or "org.pag.prototype.PagApplication" in names:
        raise ValueError("Product entry point present")
    if not re.search(r"A: android:allowBackup\([^)]*\)=\(type 0x12\)0x0\b", manifest):
        raise ValueError("Backup must be disabled")
    if not re.search(r"A: android:usesCleartextTraffic\([^)]*\)=\(type 0x12\)0x0\b", manifest):
        raise ValueError("Cleartext traffic must be disabled")
    if '="PaG P11 UI-Test"' not in manifest:
        raise ValueError("Missing manual test label")
    activity = next(block for block in blocks if block.startswith("activity "))
    if not re.search(r"A: android:exported\([^)]*\)=\(type 0x12\)0xffffffff\b", activity):
        raise ValueError("Launcher must be explicitly exported")
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$", certs, re.M)
    if digests != [expected]:
        raise ValueError("Certificate differs from the independently trusted local certificate")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--certificate-sha256", required=True)
    args = parser.parse_args()
    before = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    manifest = subprocess.check_output([args.aapt, "dump", "xmltree", str(args.apk), "AndroidManifest.xml"], text=True)
    # A failed cryptographic verification aborts before any result is accepted.
    certs = subprocess.check_output([args.apksigner, "verify", "--print-certs", str(args.apk)], text=True)
    validate(manifest, certs, args.certificate_sha256.lower().replace(":", ""))
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != before:
        raise ValueError("APK changed during verification")
    print(f"VERIFIED package={PACKAGE}; launcher={ACTIVITY}; apk_sha256={before}; no installation performed")


if __name__ == "__main__":
    main()
