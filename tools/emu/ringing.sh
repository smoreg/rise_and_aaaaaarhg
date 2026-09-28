#!/bin/sh
. "$(dirname "$0")/_lib.sh"
top=$(A shell dumpsys activity activities | grep topResumedActivity | sed -E 's/.* u0 ([^ ]+) .*/\1/' | head -1)
audio=$(A shell dumpsys audio | grep "state:started" | grep -c USAGE_ALARM)
svc=$(A shell dumpsys activity services dev.smoreg.raa | grep -c "ServiceRecord.*RingingService")
next=$(A shell dumpsys alarm | grep -A3 "dev.smoreg.raa" | grep -oE "origWhen=[0-9: .-]+" | sort -u | tr '\n' ' ')
echo "top=$top alarm_audio=$audio service=$svc next=[$next]"
