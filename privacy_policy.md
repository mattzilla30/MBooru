# MBooru privacy policy

Updated 2026-10-18. Maintainer: mattzilla30, <mattzilla30@gmail.com>.

MBooru is free, open-source software. It contains no advertising, billing, subscriptions or paid feature gates. The default build does not include Firebase Analytics or Crashlytics. A separately configured build can explicitly enable those services with `-PenableFirebase=true` and a matching Firebase configuration; its distributor must disclose that choice.

## Connections and local data

The app connects directly to the booru sites you select. Those services receive your IP address, searches, requested files and any credentials required for your account, under their own policies. Optional DNS-over-HTTPS sends DNS queries to the selected provider. The update check reads the public MBooru update manifest on GitHub. Reverse-image searches send the image you select to SauceNAO or trace.moe when you invoke that feature. Camera access is used for the scanner when requested.

Sites, history, filters, settings, cached media and download records are stored on your device. Account tokens, HTTP authentication and the SauceNAO key use encrypted device-only storage backed by Android Keystore. The database and settings contain references to those credentials. Cookies are maintained by Android WebView; sign-out clears the relevant host/domain session. Queued media addresses are also kept in the device vault because a CDN address can contain a temporary access token. Cached post metadata can still contain media addresses; avoid sharing database files or diagnostic captures. MBooru does not send account credentials to the maintainer.

## Backups, downloads and deletion

Android backup can include the user database and settings, subject to your device and backup provider. Device-only keys and encrypted credential payloads are excluded. Restoring to another device requires signing in again. Site-configuration exports omit account credentials and HTTP authentication. Imports preserve credentials already stored on this device.

Downloads use a folder you grant through Android's document picker. Queue entries retain the original destination URI, including after you select a different folder. Older folder grants remain available so queued downloads and completed-file actions continue to work; Android Settings can revoke them. Interrupted downloads retain a `.part` staging document for retry. A file is published under its final name only after the transfer succeeds. Deleting app data removes local accounts, history, settings, cookies and queues; downloaded files in your selected folder remain yours.

## Diagnostics and third parties

Default builds do not automatically upload crash reports or usage analytics. API logging is disabled to avoid leaking query credentials. Reports you voluntarily share may contain personal information; review them first. Optional integrations such as Muzei and external websites have their own privacy policies. No TLS certificate-validation bypass is implemented.

Contact <mattzilla30@gmail.com> with privacy questions. Changes to this policy are versioned in the source repository.
