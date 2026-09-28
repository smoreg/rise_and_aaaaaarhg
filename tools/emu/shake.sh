#!/bin/sh
# shake.sh <seconds> [g] — alternates the accelerometer between ±g m/s² beyond gravity.
. "$(dirname "$0")/_lib.sh"
secs="${1:-8}"; g="${2:-20}"
end=$(( $(date +%s) + secs ))
while [ "$(date +%s)" -lt "$end" ]; do
  A emu sensor set acceleration "$g:0:9.8" >/dev/null
  sleep 0.08
  A emu sensor set acceleration "-$g:0:9.8" >/dev/null
  sleep 0.08
done
A emu sensor set acceleration 0:0:9.8 >/dev/null
