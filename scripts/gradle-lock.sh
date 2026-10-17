#!/usr/bin/env bash
# Serialises every gradle-using command across worktrees and the eval batches (one gradle at a time), first come
# first served: each caller takes a ticket in the queue and runs only when its ticket is the oldest and the lock is free.
# Usage: scripts/gradle-lock.sh <command...>
# Not re-entrant: a command that takes the lock must not be wrapped in it (scripts/eval-matrix.sh and scripts/probe.sh
# take it themselves). The directory is the owner's machine default, shared with .temporary_context/15i/g.sh so both
# locks are one; BOOKLOOM_GRADLE_LOCK_DIR overrides it on another machine.
BASE=${BOOKLOOM_GRADLE_LOCK_DIR:-/private/tmp/claude-501}
LOCK="$BASE/bookloom-gradle.lock"
QUEUE="$BASE/bookloom-gradle.queue"
mkdir -p "$QUEUE"
TICKET="$QUEUE/$(date +%s%N 2>/dev/null || python3 -c 'import time;print(time.time_ns())')-$$"
echo $$ > "$TICKET"
cleanup_ticket() { rm -f "$TICKET"; }
trap cleanup_ticket EXIT
oldest() {
  for t in $(ls "$QUEUE" 2>/dev/null | sort); do
    p=$(cat "$QUEUE/$t" 2>/dev/null); if [ -n "$p" ] && kill -0 "$p" 2>/dev/null; then echo "$QUEUE/$t"; return; fi
    rm -f "$QUEUE/$t"   # dead waiter
  done
}
while :; do
  if [ "$(oldest)" = "$TICKET" ] && mkdir "$LOCK" 2>/dev/null; then break; fi
  if [ -f "$LOCK/pid" ] && ! kill -0 "$(cat "$LOCK/pid")" 2>/dev/null; then rm -rf "$LOCK"; fi
  # a lock directory left with no pid file for more than 30 s is stale (its holder died between mkdir and the write)
  if [ -d "$LOCK" ] && [ ! -f "$LOCK/pid" ] && [ $(( $(date +%s) - $(stat -f %m "$LOCK" 2>/dev/null || stat -c %Y "$LOCK") )) -gt 30 ]; then rmdir "$LOCK" 2>/dev/null; fi
  sleep 0.5
done
echo $$ > "$LOCK/pid"
rm -f "$TICKET"
trap 'rm -rf "$LOCK"' EXIT
"$@"
