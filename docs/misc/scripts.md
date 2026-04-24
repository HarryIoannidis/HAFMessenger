# SCRIPTS

## Purpose

Document repository automation scripts that package the desktop client for distribution.

## Current Implementation

- Scripts live under top-level `scripts/`.
- Implemented scripts:
  - `scripts/package-linux-appimage.sh`
  - `scripts/package-mac-app.sh`
- Both scripts:
  - Build `shared` and `client` through `./mvnw`.
  - Stage runtime dependencies into a packaging input directory.
  - Ensure the freshly built local `shared` jar is included in the package input.

## Key Types/Interfaces

- `scripts/package-linux-appimage.sh`
  - Linux-only (`uname -s` must be `Linux`).
  - Creates `app-image` via `jpackage`, then wraps it into `.AppImage` using `appimagetool`.
  - Auto-downloads `appimagetool` on `x86_64` if missing.
- `scripts/package-mac-app.sh`
  - macOS-only (`uname -s` must be `Darwin`).
  - Uses `jpackage` with classpath-staged jars.
  - Supports `PACKAGE_TYPE=app-image|dmg|pkg`.

## Flow

1. Run one of the scripts from repository root.
2. Script validates platform/tooling and builds required Maven modules.
3. Script stages jars/dependencies and calls `jpackage`.
4. Output is written under `client/target/native/`.

## Error/Security Notes

- Scripts fail fast (`set -euo pipefail`) and stop on missing requirements.
- Linux AppImage flow warns if AppStream metadata is absent; packaging still succeeds.
- Packaging scripts do not embed secrets; runtime config and credentials remain external.

## Related Files

- `scripts/package-linux-appimage.sh`
- `scripts/package-mac-app.sh`
- `README.md`
- `docs/misc/STRUCTURE.md`
