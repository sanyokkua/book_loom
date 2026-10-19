#!/usr/bin/env bash
# The fast prompt-eval loop (15j.J16): one case, one direct call to the loaded model through the production prompt
# builders (ua.bookloom.pipeline.eval.Probe, in :pipeline's test sources).
#   scripts/probe.sh --case <id> [--repeat N] [--dry] [--provider ollama|lmstudio|pseudo] [--url U] [--model M] [--window N]
#   scripts/probe.sh --list [prompt|defects|realrun]
# The first run (or one after a Java source changed) builds the test classes and the classpath file through
# scripts/gradle-lock.sh; after that a run is a plain `java`, with the source resource directories FIRST on the classpath,
# so editing a .prompt file or an eval JSON needs no build at all. Model calls take the same lock (one local model call
# at a time across agents); --dry and --provider pseudo make no call and take none. Do not wrap this script in
# gradle-lock.sh: the lock is not re-entrant. Details: docs/DEVELOPMENT.md, "The fast loop".
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIPELINE="$ROOT/modules/pipeline"
CLASSPATH_FILE="$PIPELINE/build/probe.classpath"
LOCK="$ROOT/scripts/gradle-lock.sh"

is_stale() {
    [ -f "$CLASSPATH_FILE" ] || return 0
    [ -d "$PIPELINE/build/classes/java/test" ] || return 0
    [ -d "$PIPELINE/build/classes/java/main" ] || return 0
    [ -n "$(find "$ROOT"/modules/*/src/main/java "$PIPELINE/src/test/java" -name '*.java' -newer "$CLASSPATH_FILE" -print -quit)" ]
}

if is_stale; then
    echo "probe: building the test classes and the classpath file (once, then only after a Java change)" >&2
    (cd "$ROOT" && "$LOCK" ./gradlew -q :pipeline:testClasses :pipeline:probeClasspath)
fi

makes_a_call=1
previous=""
for argument in "$@"; do
    case "$argument" in
        --dry | --list) makes_a_call=0 ;;
        pseudo) [ "$previous" = "--provider" ] && makes_a_call=0 ;;
    esac
    previous="$argument"
done

CP="$PIPELINE/src/main/resources:$PIPELINE/src/test/resources:$PIPELINE/build/classes/java/main:$PIPELINE/build/classes/java/test:$(cat "$CLASSPATH_FILE")"
# Run from the module so the test logback file lands in modules/pipeline/build/test-logs, as under Gradle.
cd "$PIPELINE"
probe=(java --sun-misc-unsafe-memory-access=allow -Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener -Dorg.gradle.test.worker=probe -cp "$CP" ua.bookloom.pipeline.eval.Probe "$@")
if [ "$makes_a_call" = 1 ]; then
    exec "$LOCK" "${probe[@]}"
fi
exec "${probe[@]}"
