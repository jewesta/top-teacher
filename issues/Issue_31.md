# Issue 31

## Summary

- Review and integrate the Linux packaging work from the former `linux-build`
  branch.
- Remove tracked Eclipse/STS metadata and ignore `.project`, `.classpath`, and
  `.settings` artifacts throughout the repository.
- Keep the Linux launcher as a repository-only helper for starting an existing
  jar; do not include it in release artifacts or present it as a Linux build.
- Make browser startup, production settings, and application shutdown reliable
  enough for this temporary helper use case.
