#!/usr/bin/env bash
#
# Shared jpackage configuration, sourced by the per-OS drivers.
#
# Scripts rather than a Gradle packaging plugin (DD-24, 03_PACKAGING_JPACKAGE.md#approach): the packaging surface is
# tiny and platform-specific, every jpackage flag below maps 1:1 to what is written, there is no plugin-version drift
# stacked on top of JDK churn, and any contributor can run one script locally to reproduce a CI artifact exactly.
#
# Not executable on its own. Source it:  . "$(dirname "$0")/jpackage-common.sh"

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# --- Identity -----------------------------------------------------------------------------------------------------

APP_NAME="BookLoom"
APP_VENDOR="BookLoom"
APP_DESCRIPTION="Local-first offline book translation"
APP_COPYRIGHT="MIT licensed"

# The main class lives in `ua.bookloom.app.bootstrap`, NOT `ua.bookloom.app`. That package is the scope of the
# ArchUnit rule `bootstrap-no-static-logger`: everything that runs before Logback is configured belongs to it, and
# membership is what the rule polices. The frozen spec names `ua.bookloom.app.Launcher`, written before the package
# existed.
MAIN_CLASS="ua.bookloom.app.bootstrap.Launcher"

# --- Input and output ---------------------------------------------------------------------------------------------

# `modules/app/build/dist/libs/`, not `app/build/dist/libs/`. The frozen spec says the latter in four places and
# ADR-0021 supersedes all four — the nine code directories moved under `modules/` immediately before this change.
INPUT_DIR="${REPO_ROOT}/modules/app/build/dist/libs"
OUTPUT_DIR="${REPO_ROOT}/build/package"
ICON_DIR="${REPO_ROOT}/docs/specification/assets/icon/dist"

# --- Version ------------------------------------------------------------------------------------------------------

# APP_VERSION from the environment (CI passes the tag), falling back to the Gradle project version, falling back to
# `dev`. `dev` is not a degradation: it is how an artifact says it did not come from the release pipeline (EC-REL-7).
resolve_version() {
    if [ -n "${APP_VERSION:-}" ]; then
        printf '%s' "${APP_VERSION}"
        return
    fi
    if [ -x "${REPO_ROOT}/gradlew" ]; then
        local gradle_version
        gradle_version="$("${REPO_ROOT}/gradlew" -q properties --property version 2>/dev/null \
            | awk '/^version:/ {print $2}' || true)"
        if [ -n "${gradle_version}" ] && [ "${gradle_version}" != "unspecified" ]; then
            printf '%s' "${gradle_version}"
            return
        fi
    fi
    printf 'dev'
}

# jpackage requires a strictly numeric X.Y.Z for --app-version and rejects anything else outright, including a
# leading zero ("the first number in an app-version cannot be zero or negative"). A tagged pre-release therefore
# contributes its numeric part to the image while the FULL string names the artifact (EC-REL-2).
#
# `dev` has no numeric part, so it gets the placeholder 1.0.0. That is OS metadata only — the version the running
# application actually reports comes from the build-generated version resource and stays `dev`, which is what makes
# an un-tagged artifact identifiable as one (EC-REL-7). The launch smoke asserts the reported version, not this.
DEV_PLACEHOLDER_VERSION="1.0.0"

numeric_version() {
    local full="$1"
    local numeric
    numeric="$(printf '%s' "${full}" | sed -E 's/^v//; s/^([0-9]+(\.[0-9]+){0,2}).*/\1/')"
    if ! printf '%s' "${numeric}" | grep -Eq '^[1-9][0-9]*(\.[0-9]+){0,2}$'; then
        numeric="${DEV_PLACEHOLDER_VERSION}"
    fi
    printf '%s' "${numeric}"
}

FULL_VERSION="$(resolve_version)"
NUMERIC_VERSION="$(numeric_version "${FULL_VERSION}")"

# --- Prerequisites ------------------------------------------------------------------------------------------------

# Fail fast and say what to run. Without this, jpackage produces an image containing no application and the failure
# surfaces only when someone launches it.
require_input() {
    if [ ! -d "${INPUT_DIR}" ] || [ -z "$(ls -A "${INPUT_DIR}" 2>/dev/null)" ]; then
        echo "error: ${INPUT_DIR} is missing or empty." >&2
        echo "       Run ./gradlew :app:collectDist first." >&2
        exit 1
    fi
    if [ ! -f "${INPUT_DIR}/app.jar" ]; then
        echo "error: app.jar not found in ${INPUT_DIR}." >&2
        exit 1
    fi
}

require_jpackage() {
    if ! command -v jpackage >/dev/null 2>&1; then
        echo "error: jpackage is not on PATH. Use a JDK 25 that ships it." >&2
        echo "       On Windows and Linux aarch64 use Liberica 25 'Full' (jdk+fx): a plain JDK lacks the" >&2
        echo "       JavaFX jmods jpackage needs there, and hand-copying jmods is never the fix (EC-REL-4)." >&2
        exit 1
    fi
}

MAIN_JAR="app.jar"

# --- Shared jpackage arguments --------------------------------------------------------------------------------------

# The production build stamp is NOT optional. Without it a launched image resolves the `-Dev` folder and everything
# appears to work while reading and writing the wrong data (DD-39). The launch smoke asserts the startup line names
# the production directory for exactly this reason — a missing stamp is otherwise invisible.
#
# Exposed as an ARRAY rather than a string. Several values contain spaces — the description most obviously — and a
# `$(...)` expansion would word-split them, handing jpackage `offline` as if it were an option. `"${JPACKAGE_ARGS[@]}"`
# is the only form that preserves argument boundaries.
JPACKAGE_ARGS=(
    --name "${APP_NAME}"
    --app-version "${NUMERIC_VERSION}"
    --vendor "${APP_VENDOR}"
    --description "${APP_DESCRIPTION}"
    --copyright "${APP_COPYRIGHT}"
    --input "${INPUT_DIR}"
    --main-jar "${MAIN_JAR}"
    --main-class "${MAIN_CLASS}"
    --dest "${OUTPUT_DIR}"
    --java-options "-Dbookloom.env=prod"
    --jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6"
)

prepare_output() {
    mkdir -p "${OUTPUT_DIR}"
}

banner() {
    echo "==> ${APP_NAME} ${FULL_VERSION} (jpackage --app-version ${NUMERIC_VERSION})"
    echo "    input:  ${INPUT_DIR}"
    echo "    output: ${OUTPUT_DIR}"
}
