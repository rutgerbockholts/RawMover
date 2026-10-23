# RawMover

**Keep raw video footage off your NAS.**

Phones save every recording to `DCIM/Camera`, and that folder is usually synced to a NAS, a photo server
(such as Immich) or the cloud. If you film a lot of raw material that you edit first,
the long raw clips end up eating storage on the server — even though only the edited result matters.

RawMover watches the camera folder and moves every video **longer than a length you choose** to a folder that
isn't synced (default `Movies/Raw`). It happens seconds after you stop recording. Short clips and photos stay
where they are and sync as normal. Your editor and gallery can still open the moved clips.

- Fully automatic — no buttons, no grace period
- Configurable length threshold (slider or exact `m:ss`), optional size threshold
- Only touches camera originals (filename pattern), so edited exports stay put
- Instant move (rename on the same storage), notification with a summary, list of recent moves
- No internet permission, no tracking, no ads
- Android 11+ (minSdk 30)

## Install

1. Download the latest `RawMover-x.y.z.apk` from [Releases](../../releases/latest).
2. Optional: verify the SHA-256 checksum listed in the release notes
   (Windows: `Get-FileHash RawMover-x.y.z.apk -Algorithm SHA256`).
3. Install the APK (allow installs from your browser/file manager when Android asks).

## First run

Open the app and clear the **Status** card:

| Item | Why |
|---|---|
| All files access | Needed to move videos owned by the camera app without a confirmation per file |
| Notifications | Summary of what was moved; warnings if something breaks |
| Battery: unrestricted | Some phones (Samsung especially) defer background work otherwise |

Then record a short clip, type its filename into **Test a filename** to check the pattern matches, and record a clip
longer than your threshold. Within a few seconds it's in `Movies/Raw` and a notification appears.

## ⚠️ Use a scheduled sync, not an instant one

RawMover moves a clip a few seconds after recording. Sync apps that **watch the folder and upload instantly**
can grab the file in those seconds and finish the upload anyway (an open file stays readable after a move).

Use a sync app that runs **on a schedule** (e.g. nightly) without folder watching. A setup that works well:

**[FolderSync](https://foldersync.io)** → folder pair `DCIM` → your NAS share (SMB2/3)
- Sync type: *To remote folder*
- Instant sync (*Monitor device folder*): **off**
- Sync deletions: **off**
- Schedule: daily at night, home Wi-Fi only, while charging

## Settings

| Setting | Default | Notes |
|---|---|---|
| Move videos longer than | 2:00 | Slider 0:30–10:00 in 15 s steps; "Set exact" for 0:05–60:00 |
| Also move large videos | off, 500 MB | Moved if longer than the length **or** larger than this |
| Watch folder | `DCIM/Camera/` | The folder your camera writes to (inside the synced folder) |
| Move to | `Movies/Raw/` | Must be **outside** the folder your sync app uploads |
| Camera filename pattern | Samsung, Pixel, `VID_` names | Regex — only matching files are considered |
| Notify after moving | on | |

Settings take effect on the next run, no restart needed.

**Existing videos:** by default only videos recorded after installation are touched. *Include existing…* also moves
older long videos on the phone. Whether that removes them from your server depends on your sync app: a one-way
sync without deletions keeps the server copy; a two-way sync may delete it.

## How it works

| File | Role |
|---|---|
| `RawMover.kt` | Queries the media index, applies the rules, renames, rescans, logs |
| `MediaWatchJob.kt` | JobScheduler job triggered by changes in the video collection; re-arms itself |
| `SweepJob.kt` | Periodic (3 h) safety-net sweep; also re-arms the watch job |
| `Scheduler.kt` | Schedules/cancels both jobs |
| `BootReceiver.kt` | Re-arms after reboot and app update |
| `Settings.kt` | SharedPreferences-backed settings |
| `MoveLog.kt` | Last 30 moves for the in-app list |
| `Notifier.kt` | Notification channels and messages |
| `ui/SettingsScreen.kt` | The one-screen Compose UI |

No service runs all the time: Android wakes the app when a new video appears in the media index.
Debug: `adb logcat -s RawMover`.

## Build from source

Android Studio with Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, JDK 17.

- Debug: open the project and Run.
- Release: copy `keystore.properties.example` to `keystore.properties` (git-ignored), point it at your keystore,
  select the *release* build variant and build the APK.

## License

[MIT](LICENSE)
