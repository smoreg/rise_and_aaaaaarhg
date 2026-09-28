# Google Play Console checklist

Everything the Console asks for, with the answers that match the code. Keep it in sync when
permissions change.

## Build

```sh
tools/release.sh   # tests, lint, signed AAB → app/build/outputs/bundle/release/app-release.aab
```

Upload key: `~/s1/raa-upload.jks` (alias `upload`), password in Keychain as `raa-upload-key`.
Enrol in **Play App Signing**; Google keeps the app signing key, we keep only the upload key.
Back the keystore up somewhere other than this laptop: a lost upload key can be reset through
Play support, but it takes days.

## Store listing

Texts: `fastlane/metadata/android/<locale>/` (en-US default, ru-RU, es-ES, es-419, zh-CN).
Still needed by hand: 512×512 icon, 1024×500 feature graphic, at least 2 phone screenshots.

- Category: Tools. Tags: alarm clock.
- Contact email: the developer account email. Website: https://github.com/smoreg/rise_and_aaaaaarhg
- Privacy policy URL: https://github.com/smoreg/rise_and_aaaaaarhg/blob/main/PRIVACY.md

## App content

- **Ads:** no ads.
- **Data safety:** no data collected, no data shared. The app has no Internet permission
  (ML Kit's telemetry permissions are removed in the manifest). Camera frames are processed on
  the device only.
- **Content rating:** no violence, no user content, no sharing. The strobe light is the only
  sensitive feature; it is opt-in and shows a photosensitivity warning.
- **Target audience:** 13+ (not designed for children).
- **Government / financial / health apps:** no.

## Permission declarations

| Permission | Declaration text |
|---|---|
| `USE_EXACT_ALARM` | Alarm clock app. Ringing at the exact minute the user set is the core function; every alarm is created by the user. |
| `USE_FULL_SCREEN_INTENT` | Alarm clock: the ringing alarm must take over a locked screen so the user can dismiss it. |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Keeps the user-set alarm playing sound, vibration and light while it rings, until the user dismisses it or the auto-stop fires. Only started by an exact alarm the user scheduled. Video: record an alarm ringing on a locked phone and being dismissed. |
| `CAMERA` | Scanning the user's own printed QR code to dismiss an alarm; on-device only. |

## Before each release

- Bump nothing by hand: versionCode is the git commit count, versionName the latest `v*` tag.
- `git tag vX.Y.Z` before running `tools/release.sh`.
- Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for every locale.

## Assets and licences

- Code: GNU GPL v3 (`LICENSE`).
- Font: Unbounded, SIL Open Font License (`OFL-Unbounded.txt`).
- Sounds: `klaxon`, `beeps`, `dawn` are synthesised by `tools/gen_sounds.py`.
  `rise_and_shine` is an original track by the author made with Suno; commercial use requires that
  it was generated on a paid Suno plan.
