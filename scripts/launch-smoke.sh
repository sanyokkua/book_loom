#!/usr/bin/env bash
#
# The packaging launch smoke: start the packaged image and prove it ACTUALLY STARTED.
#
# "The artifact exists" is not the claim being tested. `:app:run` launches from the classpath, where a missing
# `requires` or an unopened package cannot fail — so a green `:app:run` says nothing about whether the JPMS graph
# resolves. Only the packaged image, running on the module path against its own jlinked runtime, can answer that.
#
# Headless via `-Dglass.platform=Headless`, JavaFX 26's BUILT-IN headless platform (ADR-0019). The frozen
# 03_PACKAGING_JPACKAGE.md#verification names `-Dglass.platform=Monocle -Dmonocle.platform=Headless`, but
# `org.testfx:openjfx-monocle` has no release past 21.0.2 and works by patching javafx.graphics internals, so it
# cannot run against a JavaFX 26 runtime at all. Writing the spec's literal flags would produce a smoke that fails
# on every platform.
#
# "Did not actually launch" is a HARD FAILURE, never a skip (02_QUALITY_GATES.md#jpackage-smoke).
#
# Usage: ./scripts/launch-smoke.sh [path-to-image]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/build/package"
APP_NAME="BookLoom"

TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-90}"

# A temp data dir, and it is not optional: the developer's real data directory may hold a lock belonging to an
# actually-running BookLoom, and a smoke that fought it would fail for a reason unrelated to the code under test.
SMOKE_DATA_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bookloom-smoke.XXXXXX")"
cleanup() {
    rm -rf "${SMOKE_DATA_DIR}"
}
trap cleanup EXIT

# --- Locate the launcher inside the image -------------------------------------------------------------------------

find_launcher() {
    if [ "$#" -ge 1 ] && [ -n "${1:-}" ]; then
        printf '%s' "$1"
        return
    fi
    case "$(uname -s)" in
        Darwin) printf '%s' "${OUTPUT_DIR}/${APP_NAME}.app/Contents/MacOS/${APP_NAME}" ;;
        *) printf '%s' "${OUTPUT_DIR}/${APP_NAME}/bin/${APP_NAME}" ;;
    esac
}

LAUNCHER="$(find_launcher "${1:-}")"
if [ ! -x "${LAUNCHER}" ]; then
    echo "error: no executable image launcher at ${LAUNCHER}" >&2
    echo "       Build one with scripts/package-<os> first." >&2
    exit 1
fi

echo "==> launching ${LAUNCHER}"
echo "    data dir: ${SMOKE_DATA_DIR}"

# BOOKLOOM_DATA_DIR overrides the per-OS layout verbatim, so the log lands somewhere known and disposable.
BOOKLOOM_DATA_DIR="${SMOKE_DATA_DIR}" \
    "${LAUNCHER}" -J-Dglass.platform=Headless >"${SMOKE_DATA_DIR}/stdout.txt" 2>&1 &
APP_PID=$!

LOG_FILE="${SMOKE_DATA_DIR}/logs/bookloom.log"

# --- Wait for the positive startup signal ---------------------------------------------------------------------------

# All three, not just the first: "the process started" is not "the application started". The stage line is the one
# that proves the toolkit initialized and a window was actually shown.
required_signals() {
    grep -q "app started version=" "${LOG_FILE}" 2>/dev/null &&
        grep -q "injector built and two-phase init complete" "${LOG_FILE}" 2>/dev/null &&
        grep -q "primary stage shown" "${LOG_FILE}" 2>/dev/null
}

deadline=$((SECONDS + TIMEOUT_SECONDS))
started=0
while [ "${SECONDS}" -lt "${deadline}" ]; do
    if required_signals; then
        started=1
        break
    fi
    if ! kill -0 "${APP_PID}" 2>/dev/null; then
        # The process died before signalling. Its output is the only diagnosis available.
        echo "error: the image exited before reporting a successful start." >&2
        sed 's/^/    /' "${SMOKE_DATA_DIR}/stdout.txt" >&2 || true
        [ -f "${LOG_FILE}" ] && sed 's/^/    /' "${LOG_FILE}" >&2
        exit 1
    fi
    sleep 1
done

kill "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true

if [ "${started}" -ne 1 ]; then
    echo "error: no startup signal within ${TIMEOUT_SECONDS}s — the image did not actually launch." >&2
    [ -f "${LOG_FILE}" ] && sed 's/^/    /' "${LOG_FILE}" >&2
    sed 's/^/    /' "${SMOKE_DATA_DIR}/stdout.txt" >&2 || true
    exit 1
fi

# --- The stamp check ------------------------------------------------------------------------------------------------

# This is what catches a packaging script that lost `--java-options "-Dbookloom.env=prod"`. Without the stamp the
# image resolves the `-Dev` folder and runs perfectly — against the wrong data, silently, forever.
STARTUP_LINE="$(grep "app started version=" "${LOG_FILE}" | head -1)"
echo "==> ${STARTUP_LINE}"

if ! printf '%s' "${STARTUP_LINE}" | grep -q "environment=PROD"; then
    echo "error: the image reported a non-production environment." >&2
    echo "       The --java-options \"-Dbookloom.env=prod\" stamp is missing from the packaging script (DD-39)." >&2
    exit 1
fi

echo "==> launch smoke passed"
