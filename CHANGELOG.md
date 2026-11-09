# Changelog

All notable changes to the **GoogleDriveSync** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-09
### Added
- **Folder pairs**: Pair any folder on your device with a folder in Google Drive. Each pair has its own sync mode, filters, conflict handling and schedule.
- **Pick several Drive folders at once**: Tick folders in the Drive browser. Each one is stored in its own sub-folder of the device folder you choose and becomes its own pair.
- **Sync modes**: Upload only, download only, or two-way sync, powered by a bundled [rclone](https://rclone.org) v1.76.0.
- **Automatic sync**: Scheduled syncs every 15 minutes to 24 hours, optionally only on Wi-Fi or while charging. Real-time mode uploads device changes seconds after they happen.
- **Filters**: Sync only certain file types, skip others, limit by file size, skip hidden files, or add your own rclone filter rules.
- **Conflict handling**: Replace, keep the newest copy, or keep both copies.
- **Works offline**: Synced files live in a normal folder on your device. While you're offline, syncs wait and run automatically once you're back online. Nothing is deleted because Google Drive couldn't be reached.
- **Local backups**: Before a sync overwrites or deletes a file on your device, a copy is kept in the app's backup folder for 30 days (adjustable).
- **Google sign-in in your browser**: Optionally use your own Google OAuth client, entered in the app.
- **Light and dark theme** in the app icon's green.
