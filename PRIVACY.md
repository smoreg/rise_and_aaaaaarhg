# Privacy policy — RiseAndAaaaaaagh!

_Last updated: 2 October 2026_

RiseAndAaaaaaagh! collects no personal data. It has no accounts, no ads, no analytics and no
tracking. The app goes online only for the optional radio alarm, and only as described below.

## Radio alarm

If you set an alarm to play a radio station:

- Two minutes before the alarm, and when you press Test, the app connects to the stream address of
  the station you picked and plays it. The station's server sees your IP address and the app name,
  like any radio player; what it does with that is up to the station.
- Station search sends the name you type to the open directory radio-browser.info, which sees your
  IP address and the search text.
- Nothing else is sent. Alarms without radio never use the network.

## What the app uses on your phone

| Permission | Why | What happens to the data |
|---|---|---|
| Internet, network state | Play the radio station you picked and search the station directory | See "Radio alarm" above. |
| Camera | Scan the QR code or barcode that stops an alarm | Frames are decoded on the device (ZXing) and discarded immediately. Nothing is stored or transmitted. |
| Notifications, full-screen intent | Show the ringing alarm over the lock screen | — |
| Exact alarms, foreground service, wake lock, start at boot | Ring at the right minute, keep ringing, restore alarms after a reboot | — |
| Vibration, flashlight | Wake you up | — |

Motion sensors are read only while an alarm rings in Shake mode, to measure shaking. Readings are
not stored.

Alarms, the names of your codes and settings are stored only in the app's private storage on your
phone. Uninstalling the app deletes them. If you pick your own sound file, the app keeps permission
to read that one file and nothing else.

## Children

The app is not directed at children and collects no data from anyone.

## Contact

Questions: open an issue at https://github.com/smoreg/rise_and_aaaaaarhg/issues
