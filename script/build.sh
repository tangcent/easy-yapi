#!/usr/bin/env bash
source "$(dirname "$0")/_common.sh"
trap maybe_stop_daemon_on_memory_pressure EXIT

cd "$(dirname "$0")/.." || exit 1

./gradlew clean build -x test "$@"

