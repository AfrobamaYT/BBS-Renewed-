#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/produktionstest/diagnostics
glxinfo -B | tee build/produktionstest/diagnostics/glxinfo.txt
glxinfo -t > build/produktionstest/diagnostics/glx-visuals.txt

# Keep native JVM evidence before the outer time limit terminates a hung run.
(
    sleep 120
    jps -l > build/produktionstest/diagnostics/jvms.txt
    for test_pid in $(jps -q); do
        timeout 15s jstack "$test_pid" > "build/produktionstest/diagnostics/threads-$test_pid.txt" 2>&1 || true
    done
) &
diagnostics_pid=$!
trap 'kill "$diagnostics_pid" 2>/dev/null || true' EXIT

timeout --kill-after=15s 180s ./gradlew --no-daemon -I .github/produktionstest.gradle produktionstest -Pbbs.externalXvfb
