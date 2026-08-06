#!/usr/bin/env bash
#
# macOS packaging: a `.app` app-image plus a `.dmg`.
#
# The `.app` is tarred rather than zipped, and that is not a preference. macOS `.app` bundles carry an executable
# launcher inside `Contents/MacOS/`, and a plain `zip` drops the execute bit — the extracted bundle then refuses to
# launch with an error that says nothing about permissions (EC-REL-6). `tar -czf` preserves the mode.
#
# Usage: ./scripts/package-macos.sh

set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/jpackage-common.sh"

require_jpackage
require_input
prepare_output
banner

ARCH="$(uname -m)"
ICON="${ICON_DIR}/macos/BookLoom.icns"

echo "==> app-image"
jpackage "${JPACKAGE_ARGS[@]}" --type app-image --icon "${ICON}"

APP_BUNDLE="${OUTPUT_DIR}/${APP_NAME}.app"
if [ ! -d "${APP_BUNDLE}" ]; then
    echo "error: jpackage reported success but ${APP_BUNDLE} does not exist." >&2
    exit 1
fi

TARBALL="${OUTPUT_DIR}/${APP_NAME}-${FULL_VERSION}-macos-${ARCH}.tar.gz"
echo "==> ${TARBALL}"
tar -czf "${TARBALL}" -C "${OUTPUT_DIR}" "${APP_NAME}.app"

echo "==> dmg"
jpackage "${JPACKAGE_ARGS[@]}" --type dmg --icon "${ICON}"

echo "==> done"
ls -1 "${OUTPUT_DIR}"
