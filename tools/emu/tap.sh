#!/bin/sh
# tap.sh <text> [index] — taps the n-th on-screen node whose text or content-desc matches <text>.
# An exact match wins over a substring match. Retries the UI dump, which comes back empty under load.
. "$(dirname "$0")/_lib.sh"
for try in 1 2 3 4 5; do
  A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  A shell cat /sdcard/ui.xml > "/tmp/raa-ui-$SERIAL.xml"
  [ -s "/tmp/raa-ui-$SERIAL.xml" ] && grep -q "<node" "/tmp/raa-ui-$SERIAL.xml" && break
  sleep 1
done
python3 - "$1" "${2:-0}" "$SERIAL" "/tmp/raa-ui-$SERIAL.xml" <<'PY'
import sys, subprocess, re, xml.etree.ElementTree as ET
t, idx, serial, path = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
exact, partial = [], []
for n in ET.parse(path).iter('node'):
    for v in (n.get('text', ''), n.get('content-desc', '')):
        if not v or t not in v:
            continue
        b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
        (exact if v == t else partial).append(((b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
        break
hits = exact or partial
if len(hits) <= idx:
    print('NOT FOUND', t); sys.exit(1)
x, y = hits[idx]
subprocess.run(['adb', '-s', serial, 'shell', 'input', 'tap', str(x), str(y)])
print('tapped', t, x, y)
PY
