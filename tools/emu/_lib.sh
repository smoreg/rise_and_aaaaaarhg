# Every script needs SERIAL set explicitly: a silent default would hit the wrong emulator.
: "${SERIAL:?set SERIAL, e.g. SERIAL=emulator-5554}"
A() { adb -s "$SERIAL" "$@"; }
