#!/bin/sh
. "$(dirname "$0")/_lib.sh"
mkdir -p /tmp/raa-shots
A exec-out screencap -p > "/tmp/raa-shots/$1.png" && echo "/tmp/raa-shots/$1.png"
