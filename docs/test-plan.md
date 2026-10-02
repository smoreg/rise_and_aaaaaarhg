# Test plan

Manual and scripted checks for a release. Every case has an id, so a bug report can say "B11 fails
on Android 14". Cases marked *(emu)* can be driven on an emulator with `tools/emu/*.sh`; cases marked
*(phone)* need a real device (camera, real sensors, vendor firmware).

Setup on an emulator (rooted `google_apis` image):

```sh
export SERIAL=emulator-5554
adb -s $SERIAL root; adb -s $SERIAL install -r app/build/outputs/apk/release/app-release.apk
adb -s $SERIAL shell pm grant dev.smoreg.raa android.permission.POST_NOTIFICATIONS
adb -s $SERIAL shell pm grant dev.smoreg.raa android.permission.CAMERA
tools/emu/clock.sh 092906592026        # 29 Sep 2026 06:59 — the default alarm is 07:00
tools/emu/ringing.sh                   # top activity, alarm audio, scheduled alarms
```

"Rings" below means: `RingingActivity` is the top activity, `ringing.sh` shows `alarm_audio=1`,
and the notification `ringing` exists without action buttons (`dumpsys notification --noredact`).

## A. Scheduling

| Id | Case | Expected |
|---|---|---|
| A1 | One-off alarm at 07:00, clock set to 06:59, screen locked *(emu)* | Rings at 07:00:00 ±2 s. After dismiss the alarm is switched off in the list and no system alarm remains for it. |
| A2 | Repeat Mon+Wed, today Monday after 07:00 | List says the next ring is Wednesday; `dumpsys alarm` shows Wednesday 07:00. Rings on Wednesday, then next is Monday. |
| A3 | Toggle the alarm off, then on | Off: no system alarm for the app. On: scheduled again, list shows "in …". |
| A4 | Edit time from 07:00 to 07:05 while a snooze is pending | Snooze is dropped, next ring is 07:05. |
| A5 | "Skip the next one" on a repeating alarm; then edit its time | List shows "Next one skipped"; the skipped occurrence does not ring; the one after does. After editing the time, the skip follows the new time. |
| A6 | Delete an alarm | No system alarm remains; list updates. |
| A7 | Change the timezone (`cmd alarm set-timezone Europe/Berlin`; `setprop persist.sys.timezone` sends no broadcast) with an alarm set | The alarm stays at 07:00 local time: `dumpsys alarm` shows the new absolute instant. |
| A8 | Move the clock forward past the alarm time by hand | Alarm fires at once (AlarmManager) or is rescheduled to the next day; no crash, no duplicate. |
| A9 | Reboot with alarms set, do not open the app *(emu)* | After boot `dumpsys alarm` lists the app's alarms again. |
| A10 | Reinstall the app (`adb install -r`) | Alarms rescheduled (`MY_PACKAGE_REPLACED`). |
| A11 | Doze: `dumpsys deviceidle force-idle`, alarm in 2 minutes *(emu)* | Rings on time. |
| A12 | Upcoming notice: alarm 2 h 30 min ahead, jump the clock to 2 h 1 min before (a clock jump reschedules everything, so the notice must still be in the future) | Notification "Alarm at 07:00" with "Dismiss early". Tapping it opens the task screen (early phase); finishing the task marks the occurrence handled and the alarm does not ring at 07:00. Leaving the screen (Home) keeps the alarm. Switching the notice off in Settings removes the scheduled notice. |
| A13 | Alarm B fires while alarm A rings | B is pushed back 5 minutes and rings after A is dismissed; B's light phase starting during A does not move B earlier. |
| A14 | Sunrise 10 min, alarm 07:00, clock 06:49 | Screen turns on at 06:50 with the sunrise, no sound; sound at 07:00. "Already awake? Stop it" dismisses during the sunrise and marks the occurrence handled. |
| A15 | Countdown text | "in 22 h 10 min", "in 1 day 2 h", never "in 0 min"; updates every ≤15 s. |

## B. Ringing engine

| Id | Case | Expected |
|---|---|---|
| B1 | Ring with the screen off and locked *(emu)* | Screen turns on, `RingingActivity` is on top of the lock screen without unlocking, sound on `USAGE_ALARM`. |
| B2 | Ring while the phone is unlocked in another app | The ringing screen appears (full-screen or heads-up notification; tapping the notification opens it), sound plays. |
| B3 | Press volume down / mute while ringing | Alarm stream volume stays at the alarm's level; after dismiss the user's previous alarm volume is restored. |
| B4 | Back, Home, swipe away from Recents while ringing | Back: nothing. Home: sound continues, notification stays, opening the app returns to the ringing screen. Recents swipe: same. |
| B5 | Ramp 1 min, volume 80 % | Player volume starts very low and reaches full at ~60 s (`dumpsys audio` player volume, or listen). Ramp "Loud at once": full from the first second. Quiet modes: the ramp pauses while silent and resumes where it stopped. |
| B6 | Vibrate on/off | Vibrator active only when on and only while loud. |
| B7 | Sound: each bundled sound, "Phone's alarm sound", "No sound", own file | All play; "No sound" with no light and no vibration shows the red hint in the editor; a picked file is copied into the app and plays after the original is deleted; a bogus URI falls back to a bundled sound. |
| B8 | Auto-stop 15 min *(emu, jump elapsed time is not possible: wait or set 15 min and use `clock.sh`)* | After 15 min of ringing the alarm stops, notification "The 07:00 alarm gave up", occurrence handled, next scheduled. "Never": still ringing after 40 min. |
| B9 | Snooze 5 min, max 2 | Button says "5 min more · 2 left"; after snooze the list shows "Snoozed until 07:05"; rings at 07:05 with a fresh ramp; after the 2nd snooze the button disappears. |
| B10 | Kill the process while ringing (`am kill dev.smoreg.raa`, then `kill -9 <pid>` as root) | Ringing resumes within 60 s (watchdog) without user action. `am force-stop` is expected to stop everything (Android cancels the app's alarms) — document, do not fail. |
| B11 | Reboot while ringing *(emu)* | After boot the alarm rings again before unlock. |
| B12 | Reboot while ringing after the clock was moved back 12 h | Still rings after boot (record remembers it already rang). Clock moved forward past auto-stop: alarm gives up properly (notification, next occurrence scheduled). |
| B13 | Do Not Disturb, priority mode (`cmd notification set_dnd priority`) | Alarm still audible. ("Total silence", `set_dnd on`, mutes alarms system-wide; nothing an app can do.) |
| B14 | System alarm volume at its minimum before the ring (`cmd media_session volume --stream 4 --set 1`) | Ring is audible at the alarm's level. |
| B15 | Strobe | Enabling shows the photosensitivity dialog; while loud the screen flashes ~2 Hz; not during quiet. |
| B16 | Sunrise brightness | Window brightness rises during the sunrise; full during the ring with light on. |
| B17 | Notification | Category alarm, ongoing, **no action buttons**, visible on lock screen, tapping opens the ringing screen. |

## C. Missions

| Id | Case | Expected |
|---|---|---|
| C1 | Regular: tap the slider; drag halfway and release; drag to the end | Tap: nothing. Halfway: thumb springs back, still ringing. End: dismissed. |
| C2 | Shake, level Aaaagh *(emu: `tools/emu/shake.sh 8 20`)* | Silent while shaking; ring resumes within ~1 s after shaking stops; progress ring drains when idle; a firm shake completes in ~6 s; "A little" is faster, "Earthquake" slower; hard shaking (g=30) is faster than gentle (g=12); walking-level motion (g=4) does not count. On completion: dismissed. |
| C3 | Shake with a dead sensor *(phone without accelerometer, or skip)* | After 2 s without readings the task turns into furious tapping; slow taps never finish, ~8/s finish in ~15 s; silent while tapping. |
| C4 | QR quiet walks *(emu: camera permission granted)* | Screen shows the camera and "Going to the code: 3 min of silence". Press: silence, countdown "Silence for 2:59 more". After 3 min: screams again, button says 2 min; then 1 min; then 1 min again. From the third press on, the small "Oh no! I lost my code!" appears. |
| C5 | Lost code | Turns into shake at level Aaaagh; completing dismisses. |
| C6 | QR alarm with no codes registered | Editor shows the warning card with "Make a code"; the alarm still saves and rings. |
| C7 | Wrong / right code *(phone)* | Wrong: red border and "Not your code" for 1.5 s, still ringing. Right: dismissed. Torch button toggles the flash. |
| C8 | Test button in the editor | Rings immediately with the draft's settings, "Test ring. Nothing is saved."; sunrise compressed to 10 s; dismissing changes no alarm; the button does nothing while a real alarm rings. |
| C9 | Quiet-walk count survives a process kill | Kill mid-walk, resume: the next window is shorter, not 3 min again. |

## D. Codes

| Id | Case | Expected |
|---|---|---|
| D1 | Create and print a code | Name dialog (default "Bathroom"), code appears in the list as "Printed code", the print dialog opens with a page (QR, name, instructions) that fits the paper the system picked. |
| D2 | "Use a barcode I already have" *(phone)* | Camera opens, scanning any barcode asks for a name; same barcode again → "already on the list". |
| D3 | Delete a code | Removed. |
| D4 | Delete the last code while a QR alarm is enabled | Blocked with the explanation. |
| D5 | Print from the list later | Print dialog again with the same code. |

## E. Health and settings

| Id | Case | Expected |
|---|---|---|
| E1 | First launch | Onboarding checklist; every item reflects the real state (`✓` or orange dot with "Fix"); "Fix" opens the right system screen; coming back re-reads the state. "Done, let me in" is not shown again. |
| E2 | Main-screen banner | "N settings can keep your alarm silent" matches the number of orange dots; tap opens Alarm health; disappears when all fixed (vendor item marked). |
| E3 | Vendor item | "Guide for this phone" opens dontkillmyapp.com/<manufacturer>; "Done" toggles the mark. |
| E4 | Settings | Auto-stop choice persists; upcoming notice toggle reschedules; theme Night/Day/Like the phone; language: each of English/Русский/Español/中文 and "Like the phone" switches immediately and survives restart; "Source code on GitHub" opens the repo; About text has version, GPL and © line. |
| E5 | Revoke notifications permission after onboarding | Health shows the dot; alarm still makes sound (screen may not appear — expected on Android 14). |

## F. Editor and list

| Id | Case | Expected |
|---|---|---|
| F1 | Every field | Time, days, label (max 60), mode, quiet minutes 1–5, shake level, sound, volume 10–100 step 10, ramp, vibrate, light, sunrise 1–30 min, snooze 0/5/10/15 and max 1–5 — all save and reload exactly. |
| F2 | 12/24-hour | Follows the system setting in the list, the editor and the ringing screen (`settings put system time_12_24 12`). |
| F3 | Week start | Day chips start with Monday for ru/es, Sunday for en-US. |
| F4 | Landscape | List header shrinks, list visible; editor scrolls; ringing screen stays portrait. |
| F5 | Deleting while it is the next alarm | Header switches to the following alarm or "No alarms." |

## G. Robustness

| Id | Case | Expected |
|---|---|---|
| G1 | 50 alarms | List scrolls smoothly, header picks the earliest, no ANR. |
| G2 | Rapid toggling on/off 20 times | Ends in a consistent state, one system alarm at most. |
| G3 | Rotate the device during shake | Progress not lost (activity is portrait-locked). |
| G4 | Low memory (`am send-trim-memory dev.smoreg.raa RUNNING_CRITICAL`) while ringing | Keeps ringing. |
| G5 | Android 16 image (targetSdk 36) | Back gesture during ring does nothing (predictive back); everything in B and C. |
| G6 | Camera revoked mid-QR ring (`pm revoke`) | Ringing screen shows "Allow camera", alarm keeps ringing; lost-code path still works. |

## R. Radio alarm

Pre-check fires at ring − 2 min (`dumpsys alarm | grep RADIO_CHECK`). "Radio rings" means: `ringing.sh`
shows `alarm_audio=1` and the station is heard; "melody rings" means the alarm's own sound is heard.

| Id | Case | Expected |
|---|---|---|
| R1 | Radio alarm, working station, screen locked *(emu)* | At ring − 2 min a minimal "Checking the radio" notification appears and nothing is heard. At ring time the station rings with the volume ramp. |
| R2 | Airplane mode before the pre-check *(emu)* | Melody rings at ring time. |
| R3 | Broken stream address (`http://127.0.0.1:9/x`) | Melody rings. Pre-check notification gone before the ring. |
| R4 | Network cut after a good pre-check, before the ring | Melody rings (stream stalled ≥ 5 s → failed). |
| R5 | Network cut while the radio rings | Melody takes over within ~5 s and stays until dismiss, no switch back. |
| R6 | Alarm set 20 s ahead | No pre-check scheduled; melody rings. |
| R7 | Alarm set 1 min ahead | Pre-check runs at once; radio rings if the station reached 20 s of steady play. |
| R8 | Doze: `dumpsys deviceidle force-idle` before the pre-check *(emu)* | Same as R1. |
| R9 | Kill the process after the pre-check (`am kill dev.smoreg.raa`) | Alarm rings at its time with the melody. |
| R10 | Quiet walk (QR mode) during a radio ring | Radio muted, not stopped; heard again when the walk is over. |
| R11 | Snooze a radio ring | Next ring gets its own pre-check (snooze ≥ 1 min). |
| R12 | Test button, radio alarm | Silence up to 15 s while connecting, then the station; a dead address gives the melody. |
| R13 | Station search: "jazz"; offline search | List with country, codec, bitrate; offline shows "Search failed". |
| R14 | Turn radio on with sound "No sound" | Sound switches to the default melody; "No sound" is not offered while radio is on. |
| R15 | Upgrade from the previous version with alarms set | Alarms keep ringing (DB 2→3, same PendingIntent codes); radio off on all of them. |
| R16 | HLS (`.m3u8`) and plain http station | Both play (cleartext allowed). |

## H. Localization

| Id | Case | Expected |
|---|---|---|
| H1 | en with the phone in ru | Default language is English: no Cyrillic anywhere in the app when the app language is English. |
| H2 | ru plurals | "через 1 мин", "через 2 мин", "через 5 мин", "1 день", "2 дня", "5 дней", "остался 1 раз", "осталось 2 раза", "осталось 5 раз". |
| H3 | Printed page | Text follows the app language. |
| H4 | Notification texts | Follow the app language, also after reboot before the app is opened. |
