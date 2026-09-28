# RiseAndAaaaaaagh!

Android alarm clock that stops only when you do something: scan a printed QR code in another room,
spin the phone, or slide all the way. Sunrise light, strobe, volume ramp. No ads, no tracking,
no internet permission.

## Build

```sh
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`python3 tools/gen_sounds.py` regenerates the synthesised sounds (needs ffmpeg with libopus),
`python3 tools/gen_store_art.py` the Play Store icon and feature graphic.

Release: `tools/release.sh` builds the signed App Bundle. Store listing texts live in
`fastlane/metadata/android/`, the Play Console answers in [docs/play-console.md](docs/play-console.md).

## How ringing works

`Scheduler` sets `AlarmManager.setAlarmClock` triggers → `AlarmReceiver` → `RingingService`
(foreground, `systemExempted`) drives sound, vibration and flashlight from the `Ringer` session;
`RingingActivity` shows the task. The notification has no actions, so a smartwatch cannot stop it.
A ring interrupted by a reboot or a killed process resumes (`RingRecord` + watchdog alarm).

## License

GNU GPL v3, see [LICENSE](LICENSE). Font: Unbounded, SIL Open Font License (`OFL-Unbounded.txt`).
"Rise and Shine" is an original track by the author. Privacy: [PRIVACY.md](PRIVACY.md) — the app
collects nothing and has no internet access.
