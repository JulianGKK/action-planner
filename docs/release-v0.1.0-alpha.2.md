# PaG Action Planner v0.1.0-alpha.2 — Android document import preview

> **Early alpha / pre-release.** Experimental product debug APK for controlled device evaluation. Not security-audited or production-ready.

## In this version

PaG can now add, list and delete action-bound documents in PREPARATION, ACTIVE and COMPLETED. Supported imports are PDF, JPEG, PNG and UTF-8 text. A document is stored as an encrypted PaG copy; the external original is not deleted or modified. No document viewer, preview, share or export is included.

The Android system picker deliberately locks PaG. The user must return, authenticate again, reopen the **same action → Dokumente**, tap **Import fortsetzen**, choose the PaG name and format, and explicitly confirm the import. There is no automatic import and no persistent source URI. If Android destroys PaG's Activity, the selection is lost and must be repeated. In Android Developer Options, **„Aktivitäten nicht speichern“** must be disabled for the RAM-only handoff to survive the picker round trip. Developer Options and USB debugging are not required for regular use. See [Troubleshooting](troubleshooting-documents.md).

The previously affected Android storage path and Compose document-navigation faults have been fixed. Document publication now uses kernel atomic `renameat2(RENAME_NOREPLACE)` for Android API 30+ instead of hard links rejected by Samsung Android security policy. Full ciphertext verification, SQLCipher commit, maintenance recovery and delete safety checks remain in place; no direct plaintext file export.

## Test evidence and limits

- Codex reports **139 targeted host tests** and **14 isolated Android instrumentation tests** passing on Samsung SM-M526BR / Android API 33. Tests cover real SQLCipher/Room, encrypted staging, native no-replace publication, injected failures, recovery and deletion. GitHub CI did not independently rerun these tests.
- The user confirmed successful import of PDF, JPEG, PNG and UTF-8 text through the real SAF workflow, visibility after a fresh unlock, and effective deletion after another unlock.
- No destructive reset, uninstall or product-data replacement was used during testing.
- These results do **not** prove other devices, filesystems, power-loss durability or an independent security audit. Do not use real high-risk operational data for alpha evaluation.
- Android minSdk 30; versionCode **2**, versionName **0.1.0-alpha.2**; unchanged product application ID `org.pag.prototype.a` and SQLCipher schema 11.

## Distribution / signing

**Build the APK from the release-tag commit, never from an uncommitted worktree.** For a same-device update, retain the original project-local debug keystore at `.prototype-cache/debug.keystore`. Using a different keystore will prevent an in-place update; **never uninstall or clear app data to work around a signing mismatch**. Do not ship the keystore as a release asset. A debug-signed APK is an evaluation build and must be labeled as such.

Intended asset name: `PaG-v0.1.0-alpha.2-debug.apk`.

On the release machine: verify version and package identity, compile the tagged APK, verify its signer matches the previous installed app where applicable, and calculate a SHA-256 digest on the final byte-for-byte upload asset. Record the **actual** digest alongside the GitHub release asset, not a placeholder. Keep `--prerelease` enabled when creating the GitHub Release.

The separate historical demo preview `v0.1.0-alpha.1` remains unchanged. This alpha.2 is a **product-app** preview, not a replacement for the separate demo package.

## Technical source

- Feature PR: [#24](https://github.com/JulianGKK/action-planner/pull/24)
- Android import and test details: [P10b2e](product-p10b2e-android-document-import-fix.md)
- [Fix for unlock app path](fix-document-unlock-path.md) · [Document navigation fix](fix-document-navigation.md)
- [User troubleshooting](troubleshooting-documents.md)
