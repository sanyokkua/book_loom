#!/usr/bin/env bash
#
# Linux packaging: an app-image plus a `.deb`.
#
# The `.deb` needs `fakeroot`. When it is absent the script WARNS and SKIPS the `.deb` rather than failing: the
# portable app-image is the universal fallback on every OS and works without installation, so a missing packaging
# helper must not cost the artifact that does not need it (EC-REL-3).
#
# Usage: ./scripts/package-linux.sh

set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/jpackage-common.sh"

require_jpackage
require_input
prepare_output
banner

ARCH="$(uname -m)"
ICON="${ICON_DIR}/linux/BookLoom.png"

echo "==> app-image"
jpackage "${JPACKAGE_ARGS[@]}" --type app-image --icon "${ICON}"

IMAGE_DIR="${OUTPUT_DIR}/${APP_NAME}"
if [ ! -d "${IMAGE_DIR}" ]; then
    echo "error: jpackage reported success but ${IMAGE_DIR} does not exist." >&2
    exit 1
fi

TARBALL="${OUTPUT_DIR}/${APP_NAME}-${FULL_VERSION}-linux-${ARCH}.tar.gz"
echo "==> ${TARBALL}"
tar -czf "${TARBALL}" -C "${OUTPUT_DIR}" "${APP_NAME}"

if command -v fakeroot >/dev/null 2>&1; then
    echo "==> deb"
    # `--linux-package-deps false`: the image embeds its own trimmed runtime, so it must not declare a dependency on
    # a distribution JDK the user does not need and may not have.
    jpackage "${JPACKAGE_ARGS[@]}" \
        --type deb \
        --icon "${ICON}" \
        --linux-shortcut \
        --linux-menu-group "Office" \
        --linux-package-deps false
else
    echo "warning: fakeroot is not installed; skipping the .deb." >&2
    echo "         The app-image and its tarball were still produced (EC-REL-3)." >&2
fi

echo "==> done"
ls -1 "${OUTPUT_DIR}"
