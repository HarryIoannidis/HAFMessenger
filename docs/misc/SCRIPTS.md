# SCRIPTS

## Purpose

Document repository automation scripts that package the desktop client for distribution.

## Current Implementation

- Scripts live under top-level `scripts/`.
- Implemented scripts:
  - `scripts/package-linux-appimage.sh`
  - `scripts/package-linux-server-appimage.sh`
  - `scripts/package-mac-app.sh`
  - `scripts/package-windows-app.ps1`
- All packaging scripts:
  - Build required Maven modules through `./mvnw`.
  - Stage runtime dependencies into a packaging input directory.
  - Ensure the freshly built local `shared` jar is included in package input.

## Key Types/Interfaces

- `scripts/package-linux-appimage.sh`
  - Linux-only (`uname -s` must be `Linux`).
  - Creates `app-image` via `jpackage`, then wraps it into `.AppImage` using `appimagetool`.
  - Auto-downloads `appimagetool` on `x86_64` if missing.
- `scripts/package-linux-server-appimage.sh`
  - Linux-only (`uname -s` must be `Linux`).
  - Builds `shared` + `server`, packages a server-focused `.AppImage`.
  - Installs an `AppRun` wrapper that starts `resources/server-launcher.sh`.
  - Runtime launcher manages Dev Tunnel lifecycle for `8443` and writes metadata to user config.
- `scripts/package-mac-app.sh`
  - macOS-only (`uname -s` must be `Darwin`).
  - Uses `jpackage` with classpath-staged jars.
  - Supports `PACKAGE_TYPE=app-image|dmg|pkg`.
- `scripts/package-windows-app.ps1`
  - Windows-only.
  - Uses `jpackage` with classpath-staged jars.
  - Creates Start Menu and Desktop shortcuts.
  - Supports `PACKAGE_TYPE=msi|exe`.

## Flow

1. Run one of the scripts from repository root.
2. Script validates platform/tooling and builds required Maven modules.
3. Script stages jars/dependencies and calls `jpackage`.
4. Output is written under:
   - `client/target/native/` for client packaging scripts
   - `server/target/native/` for `package-linux-server-appimage.sh`

## Error/Security Notes

- Scripts fail fast (`set -euo pipefail`) and stop on missing requirements.
- Linux AppImage flow warns if AppStream metadata is absent; packaging still succeeds.
- Packaging scripts do not embed secrets; runtime config and credentials remain external.

## Related Files

- `scripts/package-linux-appimage.sh`
- `scripts/package-linux-server-appimage.sh`
- `scripts/package-mac-app.sh`
- `scripts/package-windows-app.ps1`
- `README.md`
- `docs/misc/STRUCTURE.md`
