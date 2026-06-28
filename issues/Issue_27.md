# Issue 27

## Summary

- Added ad-hoc macOS code signing to the app-image packaging path before the
  app bundle is zipped.
- Clean extended attributes from the generated app bundle before signing and
  create the ZIP without resource-fork, ACL, and quarantine metadata.
- Verify the signed `.app` bundle with `codesign --verify --deep --strict`
  before copying the release archive.
- Stopped automatically opening the generated app after packaging completes.
- Name macOS ZIP archives with the app version and architecture; snapshot
  archives use the base version plus the current short Git commit hash.
- Added `run/start-prod.sh` for local startup from the packaged production jar.
- Documented that the ad-hoc signature improves private download behavior but
  does not replace Developer ID signing and notarization.

## Notes

- Build the signed macOS archive with
  `./run/package.sh macos-app <release-target>`.
- Snapshot output uses
  `<release-target>/v<version>/TopTeacher-<base-version>-<commit>-<architecture>.zip`.
