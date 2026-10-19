"""Host-only negative controls; no adb and no real APKs or credentials."""
import unittest
from verify_apk import validate

CERT = "1" * 64
SIGNATURE = f"Signer #1 certificate SHA-256 digest: {CERT}\n"
# Synthetic aapt-format input, deliberately not a copied device/build log.
MANIFEST = '''E: manifest (line=1)
  A: package="org.pag.perf.p11probe"
  E: application (line=2)
    A: android:name(0x01)="android.app.Application"
    A: android:label(0x02)="PaG P11 UI-Test"
    A: android:allowBackup(0x03)=(type 0x12)0x0
    A: android:usesCleartextTraffic(0x04)=(type 0x12)0x0
    E: activity (line=3)
      A: android:name(0x01)="org.pag.prototype.P11ManualActivity"
      A: android:exported(0x05)=(type 0x12)0xffffffff
      E: intent-filter (line=4)
        E: action (line=5)
          A: android:name(0x01)="android.intent.action.MAIN"
        E: category (line=6)
          A: android:name(0x01)="android.intent.category.LAUNCHER"
'''


class ApkGateTest(unittest.TestCase):
    def test_valid_isolated_manifest(self):
        validate(MANIFEST, SIGNATURE, CERT)

    def test_rejects_product_package(self):
        with self.assertRaises(ValueError):
            validate(MANIFEST.replace("org.pag.perf.p11probe", "org.pag.prototype.a"), SIGNATURE, CERT)

    def test_rejects_wrong_certificate(self):
        with self.assertRaises(ValueError):
            validate(MANIFEST, SIGNATURE, "2" * 64)

    def test_rejects_product_entry(self):
        with self.assertRaises(ValueError):
            validate(MANIFEST.replace("android.app.Application", "org.pag.prototype.PagApplication"), SIGNATURE, CERT)

    def test_rejects_network_permission(self):
        extra = 'E: uses-permission (line=7)\n  A: android:name(0x01)="android.permission.INTERNET"\n'
        with self.assertRaises(ValueError):
            validate(MANIFEST + extra, SIGNATURE, CERT)

    def test_rejects_additional_components(self):
        for kind in ("activity", "service", "provider", "receiver", "activity-alias", "instrumentation"):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                validate(MANIFEST + f'E: {kind} (line=7)\n  A: android:name(0x01)="unexpected"\n', SIGNATURE, CERT)

    def test_rejects_backup(self):
        with self.assertRaises(ValueError):
            validate(MANIFEST.replace("allowBackup(0x03)=(type 0x12)0x0", "allowBackup(0x03)=(type 0x12)0xffffffff"), SIGNATURE, CERT)

    def test_rejects_unexported_launcher(self):
        with self.assertRaises(ValueError):
            validate(MANIFEST.replace("exported(0x05)=(type 0x12)0xffffffff", "exported(0x05)=(type 0x12)0x0"), SIGNATURE, CERT)


if __name__ == "__main__":
    unittest.main()
