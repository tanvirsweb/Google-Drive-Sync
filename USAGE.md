# 📖 GoogleDriveSync User Guide

Welcome to **GoogleDriveSync**! This guide covers connecting your Google account, pairing folders, choosing how and when they sync, and what happens when you're offline.

---

## 🚀 First Start

1. Open **GoogleDriveSync** from your home screen or app drawer.
2. **Storage access**: tap **Grant access** and turn on *All files access* for GoogleDriveSync. The app needs it to read and write the folders you choose. On Android 10 and older, allow the storage permission instead.
3. **Notifications** (Android 13+): allow them to see sync progress and problems.
4. **Connect Google Drive**: tap **Connect** on the account card (see below).

---

## 🔑 Connecting Your Google Account

1. Tap **Connect** (or ⋮ → **Google account**).
2. *(Optional)* Enter your own **Client ID** and **Client secret**. Leave them empty to use rclone's shared client. Your own client gives you your own quota and is usually faster:
   - In [Google Cloud Console](https://console.cloud.google.com/), create a project, enable the **Google Drive API**, and create an **OAuth client ID** of type **Desktop app**.
   - Copy its ID and secret into the app.
3. Tap **Sign in with Google**. The Google sign-in page opens in your browser.
4. Choose your account and allow access. When the page says **Success**, come back to the app.

The account screen shows your e-mail and how much Drive storage you've used. **Disconnect** removes the sign-in from the device; it doesn't touch any files.

---

## 📂 Adding Folder Pairs

Tap **Add folder pair**.

### One Drive folder
1. Under **Google Drive folder**, tap **Choose** and browse to the folder. Tap a folder's name to open it, then tap **Select this folder**. You can also create a folder (📁+) or type a path (✏️).
2. Under **Device folder**, tap **Choose** to pick a folder with the system picker, or **Use default folder** for `Internal storage/GoogleDriveSync/<name>`.
3. Pick a sync mode and other settings (below), then tap **Save**.

### Several Drive folders at once
1. In the Drive browser, **tick the checkbox** next to each folder you want. Ticks are remembered while you browse, so you can pick folders from different places. Then tap **Select N folders**.
2. Under **Destination folder on device**, choose where to keep them (or **Use default folder**).
3. Tap **Save**. Each Drive folder is stored in its own sub-folder of the destination, for example `GoogleDriveSync/Photos` and `GoogleDriveSync/Work`, and becomes its own pair with the settings you chose.

The first sync of a new pair starts right away, so the Drive folders download into your device folder.

---

## 🔄 Sync Modes

| Mode | What it does |
| --- | --- |
| **Upload only** | Device → Drive. New and changed files on the device are uploaded. |
| **Download only** | Drive → device. Files are downloaded and kept on the device for offline use. |
| **Two-way** | Changes on either side are copied to the other, including deletions. |

**Mirror deletions** (one-way modes): also delete files on the destination that were deleted on the source. Off by default, so a download never deletes files from your device.

---

## ⚖️ When a File Changed on Both Sides

- **Keep newest**: the most recently modified copy wins.
- **Keep both**: nothing is overwritten. The other copy is kept with a "conflict" name (for example `report.conflict-20261109-101500.pdf`, or `report.pdf.conflict1` / `.conflict2` in two-way mode).
- **Replace**: the source copy overwrites the other. In two-way mode, the device copy wins.

**Keep local backups** (on by default): before a sync overwrites or deletes a file on your device, a copy is moved to the app's backup folder. Open a pair's ⋮ menu → **Local backups** to see where they are. They are kept for 30 days; change this on the account screen.

---

## 🔍 Filters

- **Only these file types**: e.g. `jpg, png, pdf`. Empty means everything.
- **Skip these file types**: e.g. `tmp, log`.
- **Min / Max size** in KB.
- **Skip hidden files and folders** (names starting with `.`).
- **Custom rules** (advanced): one [rclone filter rule](https://rclone.org/filtering/) per line, e.g. `- cache/**` or `+ *.docx`.

---

## ⏰ Automatic Sync

- **Manual**: only when you tap **Sync now**.
- **Scheduled**: every 15 minutes to 24 hours.
- **Real time**: device changes are uploaded about 10 seconds after they stop, and Drive is checked on the schedule. A small "Watching folders" notification stays while real-time pairs exist.
- **Only on Wi-Fi** / **Only while charging** apply to automatic syncs. **Sync now** always runs on any connection.

Pull down on the main screen, or tap 🔄 in the toolbar, to sync all pairs now.

---

## 📴 Offline

- Your synced files are ordinary files in a device folder; open them with any app, online or not.
- While you're offline, a pair shows **Waiting for network** and the app shows an offline banner. Syncs run automatically once you're back online.
- Changes you make offline are flagged as **Local changes waiting to sync**.
- Nothing on your device is deleted or rolled back because Google Drive couldn't be reached.

---

## 🛠️ Managing Pairs

Tap a pair to edit it, or use its ⋮ menu:

- **View log**: what the last sync did, with rclone's messages. **Copy** puts it on the clipboard.
- **Local backups**: location and size of the backup copies.
- **Reset two-way state** (two-way pairs): the next sync rebuilds its state by merging both sides. No files are deleted.
- **Delete**: removes only the pairing. Files on the device and in Drive are not touched.

**Stop** on a running pair cancels the current sync; it runs again at its next scheduled time.

---

## ❓ Troubleshooting

- **"Storage access not granted"**: turn on *All files access* for GoogleDriveSync in Android settings.
- **"Google account not connected"**: sign in again on the account screen.
- **Paths too long for two-way sync**: rclone's two-way mode needs the combined folder paths to fit in a file name. Use shorter folder names or a one-way mode.
- **A sync failed**: open **View log** on the pair for rclone's exact error.
