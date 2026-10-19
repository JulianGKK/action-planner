import unittest
from verify_apk import (PACKAGE, RUNNER, SELECTION, METHODS, TESTS, TASK_METHODS,
                        selection_for, validate_selection, validate_payload, validate)

CERT = "a" * 64
MANIFEST = f'''E: manifest (line=1)
  A: package="{PACKAGE}"
  E: uses-sdk (line=2)
  E: instrumentation (line=3)
    A: android:name(0x1)="{RUNNER}"
    A: android:targetPackage(0x2)="{PACKAGE}"
  E: application (line=4)
    A: android:label(0x3)="PaG P11 Bounded Tests"
    A: android:debuggable(0x4)=(type 0x12)0xffffffff
    A: android:allowBackup(0x5)=(type 0x12)0x0
    A: android:usesCleartextTraffic(0x6)=(type 0x12)0x0
'''
CERTS = f"Signer #1 certificate SHA-256 digest: {CERT}\n"


class GateTest(unittest.TestCase):
    def test_expected_manifest_and_exact_four_method_selection(self):
        validate(MANIFEST, CERTS, CERT)
        self.assertEqual(len(METHODS), 4)
        self.assertEqual(len(SELECTION.split(",")), 4)
        self.assertTrue(all("BoundedActiveRoomTest#" in s for s in SELECTION.split(",")))

    def test_product_and_manual_ids_rejected(self):
        for wrong in ("org.pag.prototype.a", "org.pag.perf.p11probe"):
            with self.assertRaises(ValueError):
                validate(MANIFEST.replace(PACKAGE, wrong), CERTS, CERT)

    def test_wrong_target_and_runner_rejected(self):
        for text in (MANIFEST.replace('targetPackage(0x2)="' + PACKAGE, 'targetPackage(0x2)="foreign'),
                     MANIFEST.replace(RUNNER, "foreign.Runner")):
            with self.assertRaises(ValueError):
                validate(text, CERTS, CERT)

    def test_permissions_components_and_queries_rejected(self):
        for node in ("uses-permission", "permission", "service", "provider", "receiver", "activity", "queries", "activity-alias"):
            with self.assertRaises(ValueError):
                validate(MANIFEST + f"  E: {node} (line=5)\n", CERTS, CERT)

    def test_backup_cleartext_custom_application_rejected(self):
        for bad in (MANIFEST.replace("allowBackup(0x5)=(type 0x12)0x0", "allowBackup(0x5)=(type 0x12)0xffffffff"),
                    MANIFEST.replace("usesCleartextTraffic(0x6)=(type 0x12)0x0", "usesCleartextTraffic(0x6)=(type 0x12)0xffffffff"),
                    MANIFEST + '    A: android:name(0x7)="foreign.Application"\n'):
            with self.assertRaises(ValueError):
                validate(bad, CERTS, CERT)

    def test_wrong_missing_or_multiple_signers_rejected(self):
        for certs in ("", CERTS.replace(CERT, "b" * 64), CERTS + CERTS.replace("#1", "#2")):
            with self.assertRaises(ValueError):
                validate(MANIFEST, certs, CERT)


class PayloadGateTest(unittest.TestCase):
    entries = ["lib/arm64-v8a/libsqlcipher.so"]

    def dex(self, mode, missing=None):
        cls, methods = TESTS[mode]
        return b"\0".join([("L" + cls.replace(".", "/") + ";").encode()] +
                          [m.encode() for m in methods if m != missing])

    def test_both_fixed_selections_and_payloads(self):
        self.assertEqual(SELECTION, selection_for("active"))
        self.assertEqual(len(TASK_METHODS), 4)
        for mode in TESTS:
            validate_payload(self.dex(mode), self.entries, mode, selection_for(mode))

    def test_each_missing_method_rejected_in_both_modes(self):
        for mode, (_, methods) in TESTS.items():
            for missing in methods:
                with self.subTest(mode=mode, missing=missing), self.assertRaises(ValueError):
                    validate_payload(self.dex(mode, missing), self.entries, mode, selection_for(mode))

    def test_missing_or_wrong_class_and_missing_arm64_rejected(self):
        for mode in TESTS:
            for dex, entries in ((b"", self.entries), (self.dex("active" if mode == "tasks" else "tasks"), self.entries),
                                 (self.dex(mode), [])):
                with self.assertRaises(ValueError):
                    validate_payload(dex, entries, mode, selection_for(mode))

    def test_wrong_partial_extra_duplicate_and_cross_mode_selection_rejected(self):
        for mode in TESTS:
            expected = selection_for(mode)
            for bad in ("", TESTS[mode][0], expected.split(",")[0], expected + "," + expected.split(",")[0],
                        selection_for("active" if mode == "tasks" else "tasks"),
                        expected.replace("#", ".", 1), ",".join(reversed(expected.split(",")))):
                with self.subTest(mode=mode, bad=bad), self.assertRaises(ValueError):
                    validate_selection(mode, bad)
        with self.assertRaises(ValueError):
            validate_selection("other", SELECTION)


if __name__ == "__main__":
    unittest.main()
