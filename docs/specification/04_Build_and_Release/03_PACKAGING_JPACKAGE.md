**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, release management **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
`docs/specification/04_Build_and_Release/04_CI_CD.md`,
`docs/specification/04_Build_and_Release/05_ICON_AND_BRANDING.md`,
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`,
`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md` (DD-24)

# Packaging — jpackage via committed scripts

Distribution is a self-contained, **unsigned** native package per OS built with the plain **`jpackage` CLI, driven by
small committed shell scripts** — not a Gradle packaging plugin. Gradle's job ends at producing the runnable jar set;
the scripts own everything platform-specific. **There is no Windows installer** (no `.msi`, no WiX toolchain): Windows
ships as a **portable app-image zip**. Each artifact embeds its own trimmed runtime — no system Java, no separate JavaFX
SDK (DD-24).

## approach {#approach}

The pipeline has two clean halves:

1. **Gradle produces the input.** `./gradlew :app:collectDist` (a plain `Sync` task in `:app`) copies the application
   jar plus its full `runtimeClasspath` — including the per-OS JavaFX platform jars resolved by
   `org.openjfx.javafxplugin` — into `app/build/dist/libs/`. This is the only Gradle involvement; no `org.beryx.jlink`
   or other packaging plugin is used.
2. **Committed scripts drive `jpackage`.** A sourced common script holds the shared configuration; one thin script per
   OS adds the platform-specific flags and artifact types:

```
scripts/
  jpackage-common.sh     # shared: app name/vendor/description/copyright, version,
                         #   input/output dirs, main jar/class, jlink trim options,
                         #   prerequisite checks (fails fast if dist/libs is missing)
  package-macos.sh       # .app app-image + .dmg
  package-linux.sh       # app-image + .deb (skipped gracefully without fakeroot)
  package-windows.bat    # app-image ONLY (zipped by CI; no installer)
```

Shared configuration (in `jpackage-common.sh` / mirrored in the `.bat`): app name **BookLoom**, main class
`ua.bookloom.app.Launcher`, main jar auto-discovered in `app/build/dist/libs/`, version from `APP_VERSION` env (CI)
falling back to the Gradle project version, per-OS `--icon` from `docs/specification/assets/icon/dist/`, the production
build stamp `--java-options "-Dbookloom.env=prod"` (DD-39 — without it a launched image would resolve dev paths), and
the jlink size-trim options:

```
--jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6"
```

Why scripts over a plugin: the packaging surface is tiny and platform-specific, `jpackage` flags map 1:1 to what is
written, there is no plugin-version drift on top of JDK churn, and any contributor can run one script locally on their
own OS to reproduce a CI artifact exactly.

## per-os-matrix {#per-os-matrix}

| OS / arch                     | Artifacts                                                         | jpackage type(s)   | Notes                                                                                                                                                                                      |
|-------------------------------|-------------------------------------------------------------------|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| macOS x86_64 (Intel)          | `BookLoom-<v>-macos-x86_64.tar.gz` (`.app`) + `-macos-x86_64.dmg` | `app-image`, `dmg` | `.icns` icon; `.app` tarred to preserve the execute bit                                                                                                                                    |
| macOS aarch64 (Apple Silicon) | `BookLoom-<v>-macos-aarch64.tar.gz` + `.dmg`                      | `app-image`, `dmg` | same, on an ARM runner                                                                                                                                                                     |
| Windows x86_64                | `BookLoom-<v>-windows-x86_64.zip` (**portable app-image only**)   | `app-image`        | **No installer**: `.msi`/`.exe` installers need the WiX toolchain and per-machine install plumbing this project deliberately avoids. The user unzips and runs `BookLoom.exe`. `.ico` icon. |
| Linux x86_64                  | `BookLoom-<v>-linux-x86_64.tar.gz` + `.deb`                       | `app-image`, `deb` | `.png` icon; `.deb` needs `fakeroot` — when absent the script **warns and skips the `.deb`**, still producing the app-image (never a hard failure)                                         |
| Linux aarch64                 | `BookLoom-<v>-linux-aarch64.tar.gz` + `.deb`                      | `app-image`, `deb` | same, on an ARM runner                                                                                                                                                                     |

The `.deb` adds a desktop entry, menu group, and `--linux-package-deps false` (self-contained image, no distro Java
dependency). The portable app-image is the **universal fallback on every OS** — even where a `.dmg`/`.deb` exists, the
tar.gz/zip works without installation.

## jdk-and-javafx-runtime {#jdk-and-javafx-runtime}

`jpackage` jlinks a trimmed runtime into every artifact. Where that runtime must include the JavaFX modules as
**jmods**, the plain Temurin JDK is not enough:

| Context                                        | JDK                                                                             |
|------------------------------------------------|---------------------------------------------------------------------------------|
| Build + test (all CI, local dev)               | **Temurin 25** (standard)                                                       |
| Packaging on macOS, Linux x86_64               | Temurin 25                                                                      |
| Packaging on **Windows** and **Linux aarch64** | **Liberica 25 "Full" (`jdk+fx`)** — ships the JavaFX jmods jpackage needs there |

This mirrors the proven reference setup: `actions/setup-java` selects `distribution: liberica, java-package: jdk+fx` on
exactly those two packaging legs and Temurin everywhere else. A jpackage run that cannot find the JavaFX runtime pieces
fails fast with a clear message — the fix is the JDK selection, never hand-copying jmods (EC-REL-4).

## no-cross-compile {#no-cross-compile}

**jpackage cannot cross-compile.** Each artifact MUST be built on an OS/arch-matching runner, so packaging runs as a
**GitHub Actions matrix** (`04_CI_CD.md#packaging-matrix`): two macOS runners (Intel + Apple Silicon), one Windows
runner (x86_64 — no Windows-ARM runner is available), and two Linux runners (x86_64 + ARM). A single machine cannot
produce the full set; a contributor on any one OS can still produce that OS's artifacts locally with the matching
script.

## unsigned-distribution {#unsigned-distribution}

There is **no code signing and no notarization** (a deliberate scope decision; no certificates or Apple/Microsoft
accounts exist). The OS gatekeepers therefore warn on first launch, and the release notes document the "open anyway"
steps:

- **macOS (Gatekeeper):** first launch is blocked; right-click the app → **Open**, then confirm — or allow it under
  System Settings → Privacy & Security. For the tar.gz `.app`, a quarantine-flag removal
  (`xattr -d com.apple.quarantine`) is the documented alternative.
- **Windows (SmartScreen):** the "Windows protected your PC" dialog appears on the unzipped `BookLoom.exe`; click **More
  info → Run anyway**. (A portable zip also avoids the elevated-install prompt an unsigned installer would trigger.)
- **Linux:** `.deb`/tar.gz run normally; no equivalent gatekeeper.

This is consistent with the offline/privacy posture — no signing service or notarization upload is part of the build
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`).

## embedded-runtime {#embedded-runtime}

Every artifact embeds a jlink-trimmed JDK 25 + JavaFX 25 runtime (native `.dll`/`.dylib`/`.so` included), so the user
installs nothing else. The trim options above (strip debug, no headers/man pages, zip-6 compression) keep the image
small; app jars ride on the image's classpath from the script's `--input` directory.

## app-icons-and-metadata {#icons-metadata}

Per-OS icons and app metadata (name **BookLoom**, vendor, version, description, copyright) are supplied to jpackage by
the scripts. Version is single-sourced from the **git tag** (DD-50, `01_BUILD_AND_TOOLING.md#version-injection`): the
release pipeline passes the **full** version to Gradle (`-PappVersion`, baked into the in-app version resource/About and
the artifact names) and the **numeric** `X.Y.Z` to the scripts (`APP_VERSION` → `jpackage --app-version`, which rejects
suffixes) for the OS package metadata. A build without the injection reports **`dev`** — proof it is not a release
artifact.

The icons are **not hand-made per OS**: all derive from one committed master.
`docs/specification/assets/icon/process_icon.py` removes the backdrop from the owner artwork into
`assets/icon/appicon.png` (1024×1024 RGBA), and `generate_platform_icons.py` derives every platform file into
`assets/icon/dist/` — `macos/BookLoom.icns`, `windows/BookLoom.ico`, `linux/BookLoom.png` (+ the freedesktop `hicolor`
set). The scripts pass the matching file via `--icon` (macOS `.icns`, Windows `.ico`, Linux `.png`). Full contract:
`05_ICON_AND_BRANDING.md` (DD-29, ADR-0011).

**Running-window Stage icon:** the installer/launcher icons are separate from the in-app window icon set on the JavaFX
`Stage`, first needed from **PHASE_09**; the `Stage`-icon PNGs derive from the same master and are pre-committed.

## verification {#verification}

CI runs a **packaging smoke** from the scaffold phase onward (build an app-image via the scripts, confirm the artifact
exists) so packaging never silently breaks. The smoke **launches the image headlessly and asserts the app actually
started** — it does not merely check that the artifact exists:

- **Linux (`ubuntu-latest`):** launch under **Xvfb** (virtual framebuffer) so the JavaFX Stage renders against a virtual
  display.
- **macOS / Windows (and the in-JVM image smoke):** launch with the **JavaFX Monocle** headless platform
  (`-Dglass.platform=Monocle -Dmonocle.platform=Headless`).

A positive startup signal (injector built, two-phase init complete, primary Stage shown) must be observed within a
bounded timeout. **"Did not actually launch" is a hard failure, not a skip** (`02_QUALITY_GATES.md#jpackage-smoke`).
Full artifact sets are produced on `main` pushes (snapshot, short retention) and on tagged releases
(`04_CI_CD.md#release-on-tags`).

## release-edge-cases {#release-edge-cases}

- **EC-REL-1** — a `v*` tag is pushed that is **not on `main`** → the release workflow's verify job fails; no artifacts
  are published for that tag.
- **EC-REL-2** — the tag carries a pre-release suffix (`v1.2.0-rc1`) → the **numeric** `X.Y.Z` part is extracted for
  jpackage (which requires it) while the **full** version names the artifacts and the GitHub Release, which is
  automatically flagged *pre-release* (suffix detection).
- **EC-REL-3** — `fakeroot` is absent on a Linux packaging host → the `.deb` is skipped with a warning; the app-image
  tar.gz is still produced. CI installs `fakeroot` explicitly so this only occurs locally.
- **EC-REL-4** — the packaging JDK lacks the JavaFX jmods (plain JDK on Windows / Linux-ARM) → jpackage fails fast; the
  remedy is the Liberica Full (`jdk+fx`) selection in `#jdk-and-javafx-runtime`, never manual jmod copying.
- **EC-REL-5** — the packaged image fails the headless launch smoke → the packaging job is **red** (`#verification`); an
  artifact that does not start is never published.
- **EC-REL-6** — the macOS `.app` is zipped without preserving the execute bit → the app won't launch after extraction;
  the pipeline always uses `tar -czf` for the `.app` (tar preserves permissions), never plain zip.
- **EC-REL-7** — the release build misses the version injection (`-PappVersion` not passed, or `APP_VERSION` drifts from
  the tag) → the artifact would report `dev` (or a stale version) in About/OS metadata while its filename carries the
  tag version. The packaging-leg **launch smoke asserts the startup log reports the tag version** and fails the leg on
  mismatch; a mis-versioned artifact is never published.
