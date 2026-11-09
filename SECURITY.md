# 🔒 Security Policy

## Reporting a Vulnerability

If you believe you have discovered a security vulnerability in this project, **please do not open a public issue**.

Instead, report it privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability** ([direct link](https://github.com/tanvirsweb/Google-Drive-Sync/security/advisories/new)).

Please provide a detailed description of the issue, steps to reproduce, and any relevant proof-of-concept material. We aim to acknowledge receipt within **7 business days** and will work with you to assess the issue and release a patch as promptly as possible.

---

## Disclosure Policy

We follow a **coordinated disclosure** process. Once a reported vulnerability is confirmed, we will:

1. Acknowledge receipt of the report.
2. Investigate and verify the issue.
3. Prepare a fix and plan a release.
4. Credit the reporter (if desired).
5. Publicly disclose the vulnerability once a patched release is published.

---

## Supported Versions

We currently provide security updates for the following versions:

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅ Yes     |

Older versions are not maintained. Please keep your installation updated to the **latest stable release**, which also bundles the latest rclone.

---

## How GoogleDriveSync Protects You

- **No tracking**: no analytics, telemetry or ads. The app talks only to Google's servers, and to itself on `127.0.0.1` during sign-in.
- **Sign-in in your own browser**: your Google password is entered on Google's page in your browser, never in the app. rclone receives only an OAuth token, through a loopback address that only this device can reach.
- **Token storage**: the OAuth token is kept in rclone's config file in the app's private storage, which other apps cannot read. It is not encrypted separately, so a rooted or compromised device can read it. **Disconnect** deletes it. App backup is disabled (`allowBackup="false"`), so the token is never copied into device or cloud backups.
- **Your own OAuth client (optional)**: a client ID and secret you enter are stored in the app's private storage and used only for sign-in and token refresh.
- **Your files stay yours**: the app syncs only the folders you pair. Before a sync overwrites or deletes a file on your device, a backup copy is kept (unless you turn this off).
- **Open-source sync engine**: syncing is done by [rclone](https://rclone.org), built from unmodified upstream source as part of each release.

### Permissions and why they're needed

| Permission | Why |
| --- | --- |
| All files access / storage | rclone reads and writes the folders you pair. |
| Internet, network state | Talking to Google Drive; waiting for a connection when offline. |
| Foreground service (data sync), notifications | Showing sync progress; watching folders for real-time sync. |
| Run at startup | Restarting real-time folder watching after a reboot. |

---

## Security Best Practices

- Always download and install GoogleDriveSync from **official releases**.
- Keep your Android operating system and security patches up to date.
- Verify APK signatures when sideloading.
- Review connected apps in your [Google Account permissions](https://myaccount.google.com/permissions) and remove access you no longer use.
- Use a screen lock: anyone holding your unlocked phone can open the synced folders.

---

## Thank You

We appreciate your assistance in keeping GoogleDriveSync secure, private, and reliable!
