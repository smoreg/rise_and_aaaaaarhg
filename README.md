# RiseAndAaaaaaagh!

Android alarm clock that stops only when you do something: scan a printed QR code in another room,
shake the phone, or slide all the way. Sunrise light, strobe, volume ramp. No ads, no tracking,
no internet permission.

## Build

```sh
# needs JDK 17+ and the Android SDK
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`python3 tools/gen_sounds.py` regenerates the synthesised sounds (needs ffmpeg with libopus),
`python3 tools/gen_store_art.py` the Play Store icon and feature graphic.

Testing: `docs/test-plan.md` is the manual/emulator test plan, `tools/emu/` the adb helpers it
uses, `docs/test-report-2026-09-28.md` the latest run.

Release: `RAA_STORE_FILE=/path/to/upload.jks tools/release.sh` builds the signed App Bundle. Store listing texts live in
`fastlane/metadata/android/`, the Play Console answers in [docs/play-console.md](docs/play-console.md).

## How ringing works

`Scheduler` sets `AlarmManager.setAlarmClock` triggers → `AlarmReceiver` → `RingingService`
(foreground, `systemExempted`) drives sound, vibration and flashlight from the `Ringer` session;
`RingingActivity` shows the task. The notification has no actions, so a smartwatch cannot stop it.
A ring interrupted by a reboot or a killed process resumes (`RingRecord` + watchdog alarm).

## License

Copyright (C) 2026 Kirill Semenchenko. Free software under the GNU GPL v3 or later, see
[LICENSE](LICENSE). Bundled sounds, the font and libraries are listed in [NOTICE.md](NOTICE.md).
Privacy: [PRIVACY.md](PRIVACY.md) — the app collects nothing and has no internet access.
