#!/bin/sh
. "$(dirname "$0")/_lib.sh"
for try in 1 2 3 4 5; do
  A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  out=$(A shell cat /sdcard/ui.xml)
  case "$out" in *"<node"*) break ;; esac
  sleep 1
done
printf '%s' "$out" | python3 -c "
import sys, xml.etree.ElementTree as ET
print(' | '.join(t for n in ET.fromstring(sys.stdin.read()).iter('node') for t in [n.get('text') or n.get('content-desc')] if t))"
