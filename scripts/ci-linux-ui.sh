#!/usr/bin/env bash
# Runs the :ui tests that depend on the host's fonts (the fit matrices) on Ubuntu, the way the CI runner does.
# The window's text is measured with the platform's font, so a test that passes on macOS can fail on Linux; CI found
# that late once (run 40489525598). Run this before pushing a change to a screen, a dialog or a UI word.
#
#   scripts/ci-linux-ui.sh                       # the fit tests
#   scripts/ci-linux-ui.sh --tests '*SomeTest'   # any other :ui test selection
#
# Needs a running Docker daemon (Colima: `colima start --cpu 2 --memory 4`). The repository is copied into the volume
# `bookloom-ci-work` (build output stays there, so no Linux file lands in the working tree and a second run only
# recompiles what changed); the Gradle cache lives in `bookloom-ci-gradle`.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
tests=("$@")
if [ ${#tests[@]} -eq 0 ]; then
  tests=(--tests '*FitTest' --tests 'ua.bookloom.ui.screen.ProjectsScreenTest')
fi
quoted=$(printf ' %q' "${tests[@]}")
image=bookloom-ci-ui

# The fonts and the native libraries JavaFX's text layer needs (Pango), as on the GitHub runner image.
docker build -q -t "$image" - >/dev/null <<'DOCKERFILE'
FROM eclipse-temurin:25-jdk
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends rsync fontconfig fonts-dejavu-core \
    fonts-liberation libfreetype6 libpango-1.0-0 libpangoft2-1.0-0 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
DOCKERFILE

docker run --rm \
  -v "$root":/src:ro \
  -v bookloom-ci-gradle:/root/.gradle \
  -v bookloom-ci-work:/work \
  "$image" bash -ec "
    rsync -a --exclude build --exclude .gradle --exclude .git --exclude .temporary_context /src/ /work/
    cd /work
    ./gradlew --no-daemon :ui:test $quoted
  "
