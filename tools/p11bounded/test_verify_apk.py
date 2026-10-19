import unittest
from verify_apk import PACKAGE, RUNNER, SELECTION, METHODS, validate

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


if __name__ == "__main__":
    unittest.main()
