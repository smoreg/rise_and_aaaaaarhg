#!/bin/sh
. "$(dirname "$0")/_lib.sh"
A root >/dev/null 2>&1; sleep 1
A shell "sqlite3 /data/user_de/0/dev.smoreg.raa/databases/raa.db \"$1\""
