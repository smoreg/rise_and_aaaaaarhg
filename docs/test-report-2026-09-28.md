# Test report — 28 Sep 2026

Run against `docs/test-plan.md` on four emulators (three Android 14 / API 34, one Android 16 / API 36,
`google_apis` images) with the debug build, by four independent testers. Every failure below is
fixed in the same day's commits and was re-verified on the emulator where it was found, unless
marked otherwise.

## Results

| Section | Cases | Result |
|---|---|---|
| A Scheduling | A1–A15 | all PASS (A7 via `cmd alarm set-timezone`) |
| B Ringing engine | B1–B17 | all PASS (B8 "never" variant not run; B13 in priority DND — total silence mutes alarms system-wide) |
| C Missions | C1–C9 | PASS; C3 and C7 BLOCKED on emulator (no dead sensor, no readable QR in the virtual camera) |
| D Codes | D1–D5 | PASS; D2 BLOCKED on emulator (camera) |
| E Health and settings | E1–E5 | all PASS |
| F Editor and list | F1–F5 | all PASS (F1 slider glitch not reproduced) |
| G Robustness | G1–G6 | all PASS (G5 on Android 16: 14 cases) |
| H Localization | H1–H4 | all PASS |

## Found and fixed

Ordered by severity.

1. `MediaPlayer.prepare()` ran on the main thread: ANR during the ring on a slow device. Now on IO.
2. Android 16 held the foreground-service notification back for 10 s, so the ring had no screen for
   10 s; a direct `startActivity` from the service was refused (BAL). Notification is now
   `FOREGROUND_SERVICE_IMMEDIATE`, the full-screen PendingIntent opts into background starts, the
   service opens the screen directly only with the overlay permission, and `MainActivity` opens it
   itself when the app is in front. Screen now appears in 0.2–0.4 s.
3. A resumed ring after a reboot with the clock moved back stayed silent (record now remembers that
   the sound had started).
4. A dead audio decoder left the alarm silent; the user's alarm volume was lost after a process
   kill; the clock on the ringing screen froze. All three fixed.
5. Fast slider flicks failed 4/10 (drag deltas only, release position dropped). Rewritten on raw
   pointer events on the track: 10/10.
6. The quiet-walk window and its count are kept across a process kill.
7. A heads-up of the app's own notification covered the ringing screen (quiet channel while the
   screen is visible); the notification carried no full-screen intent after a restart (re-posted).
8. Overlapping alarms: another alarm's sunrise no longer pulls it earlier; the two-hour notice is
   within a 10-minute window instead of up to an hour late; the notice is cancelled when the ring
   starts and restored if the early-dismiss screen is left.
9. Status-bar icons were dark on the dark theme; landscape header overflowed; text below the horizon
   was dark on dark; "Sound at" showed the alarm time instead of the test ring time; 12-hour
   notifications lacked AM/PM; the system's "next alarm" showed the sunrise time.
10. Codes screen: Back left the whole screen from the scanner; a denied camera said nothing.
11. Rapid on/off toggles could leave an alarm cancelled (scheduling serialized).

## Not verifiable on an emulator — check on a phone

- Scanning a real QR / barcode (C7, D2), the torch while scanning.
- Shake thresholds with a real accelerometer; whether walking ever counts as shaking (it should not).
- Vendor firmware: the "Phone maker's restrictions" item is manual by design.
- B8 with auto-stop "Never" (40 minutes of ringing).
