// Command rclone is the rclone binary bundled with GoogleDriveSync.
//
// It is upstream rclone's own main (github.com/rclone/rclone/rclone.go), but instead of
// importing every backend and command it registers only what the app uses. That cuts the
// binary to a fraction of its size. Everything else is the unmodified upstream code.
//
// If the app starts calling another rclone command or backend, add its import here.
package main

import (
	"github.com/rclone/rclone/cmd"

	// Backends: Google Drive, and local disk for the device side.
	_ "github.com/rclone/rclone/backend/drive"
	_ "github.com/rclone/rclone/backend/local"

	// Commands used by the app (see SyncCommandBuilder, DriveRemote, RcloneAuthorizer).
	_ "github.com/rclone/rclone/cmd/about"     // storage quota
	_ "github.com/rclone/rclone/cmd/authorize" // Google sign-in
	_ "github.com/rclone/rclone/cmd/bisync"    // two-way sync
	_ "github.com/rclone/rclone/cmd/config"    // config userinfo (account e-mail)
	_ "github.com/rclone/rclone/cmd/copy"      // one-way sync
	_ "github.com/rclone/rclone/cmd/lsjson"    // Drive folder browser
	_ "github.com/rclone/rclone/cmd/mkdir"     // create Drive folders
	_ "github.com/rclone/rclone/cmd/sync"      // one-way sync with mirrored deletions
	_ "github.com/rclone/rclone/cmd/version"   // About dialog
)

func main() {
	cmd.Main()
}
