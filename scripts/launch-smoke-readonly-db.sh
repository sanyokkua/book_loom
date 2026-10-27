#!/usr/bin/env bash
#
# The packaged image with a database file it cannot write: it must show the startup message (and later exit non-zero),
# never die in init() with an exception only a terminal sees (finalize 9 final QA, W2-D4).
#
# It starts the image once to create the database, stops it, makes the file read-only, starts it again and waits for
# the startup-message line that StartupFailureDialog writes just before it shows the message. A window or a start
# signal instead of that line is a failure. Headless as in launch-smoke.sh.
#
# Usage: ./scripts/launch-smoke-readonly-db.sh [path-to-image]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/build/package"
APP_NAME="BookLoom"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-90}"

SMOKE_DATA_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bookloom-smoke-ro.XXXXXX")"
APP_PID=""
cleanup() {
    [ -n "${APP_PID}" ] && kill "${APP_PID}" 2>/dev/null || true
    chmod u+w "${SMOKE_DATA_DIR}/bookloom.db" 2>/dev/null || true
    rm -rf "${SMOKE_DATA_DIR}"
}
trap cleanup EXIT

if [ "$#" -ge 1 ] && [ -n "${1:-}" ]; then
    LAUNCHER="$1"
else
    case "$(uname -s)" in
        Darwin) LAUNCHER="${OUTPUT_DIR}/${APP_NAME}.app/Contents/MacOS/${APP_NAME}" ;;
        *) LAUNCHER="${OUTPUT_DIR}/${APP_NAME}/bin/${APP_NAME}" ;;
    esac
fi
if [ ! -x "${LAUNCHER}" ]; then
    echo "error: no executable image launcher at ${LAUNCHER}; build one with scripts/package-<os> first." >&2
    exit 1
fi
LOG_FILE="${SMOKE_DATA_DIR}/logs/bookloom.log"

# Starts the image in the background and waits until the log holds $1; fails when the process dies first.
launch_until() {
    local wanted="$1"
    BOOKLOOM_DATA_DIR="${SMOKE_DATA_DIR}" \
        "${LAUNCHER}" -J-Dglass.platform=Headless >"${SMOKE_DATA_DIR}/stdout.txt" 2>&1 &
    APP_PID=$!
    local deadline=$((SECONDS + TIMEOUT_SECONDS))
    while [ "${SECONDS}" -lt "${deadline}" ]; do
        if grep -q "${wanted}" "${LOG_FILE}" 2>/dev/null; then
            return 0
        fi
        if ! kill -0 "${APP_PID}" 2>/dev/null; then
            echo "error: the image exited before writing '${wanted}'." >&2
            sed 's/^/    /' "${SMOKE_DATA_DIR}/stdout.txt" >&2 || true
            return 1
        fi
        sleep 1
    done
    echo "error: no '${wanted}' within ${TIMEOUT_SECONDS}s." >&2
    return 1
}

stop_app() {
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
    APP_PID=""
}

echo "==> first start creates the database in ${SMOKE_DATA_DIR}"
launch_until "window shown, initialView="
stop_app
rm -f "${SMOKE_DATA_DIR}/bookloom.db-wal" "${SMOKE_DATA_DIR}/bookloom.db-shm" "${SMOKE_DATA_DIR}/bookloom.lock"
chmod 444 "${SMOKE_DATA_DIR}/bookloom.db"
: >"${LOG_FILE}"

echo "==> second start with a read-only database"
launch_until "startup message shown code=internal exitCode=1"
if grep -q "window shown, initialView=" "${LOG_FILE}"; then
    echo "error: the window was shown although the database cannot be written." >&2
    exit 1
fi
if grep -q "Exception in Application init method" "${SMOKE_DATA_DIR}/stdout.txt"; then
    echo "error: init() threw past the startup message." >&2
    exit 1
fi
stop_app
echo "==> $(grep "startup message shown" "${LOG_FILE}" | head -1)"
echo "==> read-only database smoke passed"
