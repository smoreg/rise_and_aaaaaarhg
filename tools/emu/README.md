# Emulator helpers

Small adb wrappers used to drive the app on an emulator by hand or from a test script. Every
script takes the device serial in `SERIAL` (default `emulator-5554`).

| Script | What it does |
|---|---|
| `tap.sh <text> [index]` | Taps the centre of the n-th on-screen node whose text or content-desc contains `<text>` |
| `texts.sh` | Prints every visible text / content-desc, `\|`-separated |
| `shot.sh <name>` | Saves a screenshot to `/tmp/raa-shots/<name>.png` |
| `clock.sh <MMDDhhmm[yyyy]>` | Turns off automatic time and sets the emulator clock (root) |
| `ringing.sh` | One line: top activity, whether alarm audio is playing, next scheduled app alarms |
| `shake.sh <seconds> [g]` | Feeds a shaking accelerometer signal to the emulator for `<seconds>` |
| `db.sh "<sql>"` | Runs SQL against the app database on a rooted emulator |
