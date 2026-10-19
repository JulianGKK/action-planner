#!/usr/bin/env python3
"""Read-only gate for the self-targeting Data instrumentation APK. No device operations."""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

PACKAGE = "org.pag.data.unlockprobe"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = "org.pag.data.product.BoundedActiveRoomTest"
METHODS = (
    "boundedProjectionEqualsExistingOperationsAndActionReaders",
    "preparationCompletedAndMissingAreRejected",
    "finalPublicationDenialAndRevokedSessionReturnNoPayload",
    "repeatReadClosesConnectionsAndDoesNotChangeProjection",
)
SELECTION = ",".join(TEST_CLASS + "#" + method for method in METHODS)


def digest(value):
    value = value.lower().replace(":", "")
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Expected independent SHA256 trust value")
    return value


def validate(manifest, certificates, expected_certificate):
    blocks = re.split(r"(?m)^\s*E: ", manifest)[1:]
    kinds = Counter(block.split()[0] for block in blocks)
    if kinds != Counter({"manifest": 1, "uses-sdk": 1, "instrumentation": 1, "application": 1}):
        raise ValueError("Unexpected permission, query, component or manifest node")
    if re.findall(r'A: package="([^"]+)"', manifest) != [PACKAGE] or "sharedUserId" in manifest:
        raise ValueError("Unexpected package or shared UID")
    if "org.pag.prototype.a" in manifest or "org.pag.perf.p11probe" in manifest:
        raise ValueError("Product/manual package must not be targeted or referenced")
    instrument = next(b for b in blocks if b.startswith("instrumentation "))
    for attribute, expected in (("name", RUNNER), ("targetPackage", PACKAGE)):
        if re.findall(r'A: android:' + attribute + r'\([^)]*\)="([^"]+)"', instrument) != [expected]:
            raise ValueError("Unexpected instrumentation identity/target")
    app = next(b for b in blocks if b.startswith("application "))
    if "android:name(" in app:
        raise ValueError("Only default android.app.Application allowed")
    for attribute in ("allowBackup", "usesCleartextTraffic"):
        if not re.search(r'A: android:' + attribute + r'\([^)]*\)=\(type 0x12\)0x0\b', app):
            raise ValueError("Backup/cleartext must be disabled")
    if not re.search(r'A: android:debuggable\([^)]*\)=\(type 0x12\)0xffffffff\b', app):
        raise ValueError("Expected isolated debug test APK")
    if '="PaG P11 Bounded Tests"' not in app:
        raise ValueError("Unexpected test label")
    found = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$", certificates, re.M)
    if found != [digest(expected_certificate)]:
        raise ValueError("Signer differs from independently trusted certificate")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--certificate-sha256", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    args = parser.parse_args()
    before = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if before != digest(args.sha256):
        raise ValueError("APK hash differs from frozen artifact")
    manifest = subprocess.check_output([args.aapt, "dump", "xmltree", str(args.apk), "AndroidManifest.xml"], text=True)
    certs = subprocess.check_output([args.apksigner, "verify", "--print-certs", str(args.apk)], text=True)
    validate(manifest, certs, args.certificate_sha256)
    with ZipFile(args.apk) as apk:
        dex = b"".join(apk.read(n) for n in apk.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        if ("L" + TEST_CLASS.replace(".", "/") + ";").encode() not in dex or any(m.encode() not in dex for m in METHODS):
            raise ValueError("Missing compiled test class/method symbols")
        if "lib/arm64-v8a/libsqlcipher.so" not in apk.namelist():
            raise ValueError("Missing ARM64 SQLCipher library")
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != before:
        raise ValueError("Artifact changed during verification")
    print(f"VERIFIED package=test=target={PACKAGE}; runner={RUNNER}; sha256={before}; no device operation")
    print("SELECTION=" + SELECTION)


if __name__ == "__main__":
    main()
