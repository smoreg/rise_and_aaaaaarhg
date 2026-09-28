#!/bin/sh
# clock.sh MMDDhhmm[yyyy] — e.g. clock.sh 092906592026 sets 29 Sep 2026 06:59.
. "$(dirname "$0")/_lib.sh"
A root >/dev/null 2>&1; sleep 1
A shell settings put global auto_time 0
A shell "date $1" && A shell date
