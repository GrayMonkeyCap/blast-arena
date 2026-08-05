# Privacy design and threat model

Life Ledger reads the most sensitive stream on a phone: a complete record of someone's
money and movements. The design assumes that promise-based privacy is worthless and aims
for privacy that is *structurally enforced*.

## What is guaranteed, and by what

| Guarantee | Enforced by |
|---|---|
| No data is transmitted | The app declares no `INTERNET` permission. The OS blocks every socket, in every library, including any dependency that tried. |
| No third-party SDK receives data | There are no analytics, ads, attribution, crash-reporting or A/B SDKs in the dependency graph. The version catalog is the audit list. |
| No account exists to leak | There is no identity, sign-up or login anywhere in the codebase. |
| Data at rest is encrypted | SQLCipher-backed Room; the passphrase is a 256-bit random value wrapped by an AES-GCM key in the Android Keystore, hardware-backed where the device provides it. |
| Only the user can read a backup | Backups are AES-256-GCM with a key derived from a user passphrase via PBKDF2-HMAC-SHA256 (210,000 iterations, random salt). A backup without the passphrase is noise. |
| Nothing sensitive reaches logs | Logging is compiled to no-ops in release. Even in debug, SMS bodies, account numbers and UPI ids are never passed to a logger; identifiers go through a redactor. |

## Permissions requested

- `READ_SMS` — to read existing messages once and build history.
- `RECEIVE_SMS` — to process new messages as they arrive.

Both are optional in the sense that the app runs without them; it simply has nothing to
show until you import a file instead.

## Threat model

**In scope, and addressed:**

- *Device lost or stolen, screen locked.* Database encrypted; key in the Keystore and
  non-exportable. App lock plus biometric unlock guards the UI.
- *Malicious app on the same device.* All storage is app-private; the database file is
  ciphertext; no exported components read or write data (the SMS receiver is
  `BROADCAST_SMS`-protected and only enqueues work).
- *Backup file exfiltrated.* Encrypted with a passphrase the app never stores.
- *Network exfiltration by a compromised dependency.* Impossible without the `INTERNET`
  permission; adding one would be visible in the manifest diff.
- *Accidental leakage through logs or crash reports.* No crash reporter exists; logs are
  stripped in release and redacted in debug.

**Explicitly out of scope:**

- A rooted or already-compromised device with an attacker holding the unlocked phone.
- A malicious keyboard or accessibility service capturing the screen.
- Physical coercion to unlock.
- The SMS themselves, which the carrier and sender already have.

## Data retention

The user chooses. Settings offers a retention window after which raw SMS bodies are purged
while the derived transactions are kept, an option to keep nothing raw at all, and a
parser-log retention window. Deletion is real deletion — rows are removed, not flagged.

## Backups

Backups are user-initiated and written wherever the user points the system file picker.
There is no automatic upload, no cloud target and no "convenience" default. Android's own
auto-backup is disabled in the manifest (`android:allowBackup="false"`) so the database
cannot be swept into a Google account without the user's knowledge.

## Auditing this yourself

1. `grep -r "INTERNET" app/src` — no result.
2. Read `gradle/libs.versions.toml` — every dependency the app ships, in one file.
3. Read the merged manifest — two SMS permissions, one exported receiver, guarded by
   `BROADCAST_SMS`.
4. Run the app in airplane mode indefinitely. Nothing degrades.
