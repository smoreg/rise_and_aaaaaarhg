# Google Play Console checklist

Everything the Console asks for, with the answers that match the code. Keep it in sync when
permissions change.

## Build

```sh
RAA_STORE_FILE=/path/to/upload.jks tools/release.sh   # tests, lint, signed AAB
```

Upload key: a PKCS12 keystore with alias `upload`; where it lives is kept outside this repo.
Enrol in **Play App Signing**; Google keeps the app signing key, we keep only the upload key.
Back the keystore up somewhere other than this laptop: a lost upload key can be reset through
Play support, but it takes days.

## Store listing

Texts: `fastlane/metadata/android/<locale>/` (en-US default, ru-RU, es-ES, es-419, zh-CN).
Images: `fastlane/metadata/android/en-US/images/` — `icon.png` 512×512, `featureGraphic.png`
1024×500 (both from `tools/gen_store_art.py`), `phoneScreenshots/`.

- Category: Tools. Tags: alarm clock.
- Contact email: the developer account email. Website: https://github.com/smoreg/rise_and_aaaaaarhg
- Privacy policy URL: https://github.com/smoreg/rise_and_aaaaaarhg/blob/main/PRIVACY.md

## App content

- **Ads:** no ads.
- **Data safety:** no data collected, no data shared. The Internet permission is used only to play
  the radio station the user picked and to search radio-browser.info by station name; nothing about
  the user is sent or stored. No proprietary SDKs; barcodes are decoded on the device by ZXing and
  frames are discarded.
- **Content rating:** no violence, no user content, no sharing. The strobe light is the only
  sensitive feature; it is opt-in and shows a photosensitivity warning.
- **Target audience:** 13+ (not designed for children).
- **Government / financial / health apps:** no.

## Permission declarations

| Permission | Declaration text |
|---|---|
| `USE_EXACT_ALARM` | Alarm clock app. Ringing at the exact minute the user set is the core function; every alarm is created by the user. |
| `USE_FULL_SCREEN_INTENT` | Alarm clock: the ringing alarm must take over a locked screen so the user can dismiss it. |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Keeps the user-set alarm playing sound, vibration and light while it rings, until the user dismisses it or the auto-stop fires. Only started by an exact alarm the user scheduled. For a radio alarm, also two minutes before the ring, to play the chosen station muted and check it works; if it does not, the alarm uses its melody. Video: record an alarm ringing on a locked phone and being dismissed. |
| `CAMERA` | Scanning the user's own printed QR code to dismiss an alarm; on-device only. |
| `SYSTEM_ALERT_WINDOW` (optional, asked in Alarm health) | Bring the ringing alarm screen forward while the phone is unlocked and in use; without it Android only shows a heads-up notification. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (optional) | Alarm clock: battery optimisation can delay or kill the alarm; the app asks the user directly instead of sending them to the settings list. |

## Before each release

- Raise `versionCode` and `versionName` in `app/build.gradle.kts`; Play rejects a code it has seen.
- Tag the commit `vX.Y.Z`.
- Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for every locale.

## Assets and licences

See [NOTICE.md](../NOTICE.md): code GPL-3.0, font OFL, sounds synthesised or made by the author
(Suno, paid plan).
