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

TASK_CLASS = "org.pag.data.product.BoundedTaskRoomTest"
TASK_METHODS = (
    "boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews",
    "nonActiveAndMissingAreDenied",
    "publicationAndRevokeCannotReturnTaskPayload",
    "oversizeTemplateRefusedAndRepeatedReadsRemainConsistent",
)
TESTS = {"active": (TEST_CLASS, METHODS), "tasks": (TASK_CLASS, TASK_METHODS)}


def selection_for(mode):
    test_class, methods = TESTS[mode]
    return ",".join(test_class + "#" + method for method in methods)


def validate_selection(mode, selection):
    if mode not in TESTS or selection != selection_for(mode):
        raise ValueError("Only the exact four-method selection for this mode is allowed")


def validate_payload(dex, entries, mode, selection):
    validate_selection(mode, selection)
    test_class, methods = TESTS[mode]
    if ("L" + test_class.replace(".", "/") + ";").encode() not in dex or any(m.encode() not in dex for m in methods):
        raise ValueError("Missing compiled test class/method symbols")
    if "lib/arm64-v8a/libsqlcipher.so" not in entries:
        raise ValueError("Missing ARM64 SQLCipher library")


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
    parser.add_argument("--mode", choices=tuple(TESTS), default="active")
    parser.add_argument("--selection", help="If supplied, must equal the fixed selector for the mode")
    args = parser.parse_args()
    selection = args.selection if args.selection is not None else selection_for(args.mode)
    validate_selection(args.mode, selection)
    before = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if before != digest(args.sha256):
        raise ValueError("APK hash differs from frozen artifact")
    manifest = subprocess.check_output([args.aapt, "dump", "xmltree", str(args.apk), "AndroidManifest.xml"], text=True)
    certs = subprocess.check_output([args.apksigner, "verify", "--print-certs", str(args.apk)], text=True)
    validate(manifest, certs, args.certificate_sha256)
    with ZipFile(args.apk) as apk:
        dex = b"".join(apk.read(n) for n in apk.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        validate_payload(dex, apk.namelist(), args.mode, selection)
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != before:
        raise ValueError("Artifact changed during verification")
    print(f"VERIFIED package=test=target={PACKAGE}; runner={RUNNER}; sha256={before}; no device operation")
    print("MODE=" + args.mode)
    print("SELECTION=" + selection)


if __name__ == "__main__":
    main()
