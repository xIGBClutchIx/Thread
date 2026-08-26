# Thread Release

Thread currently releases one Fabric client artifact. NeoForge, Forge, and a universal JAR are not
part of this release process.

## Release gate

From the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle runProductionClientGameTest `
  runRestartProductionClientGameTest runMcpDisabledProductionClientGameTest `
  verifyReleaseVersion "-PreleaseTag=v0.1.0"
git diff --check
```

Change the tag argument when `mod_version` changes. Versions use `MAJOR.MINOR.PATCH`; tags use
`vMAJOR.MINOR.PATCH`.

`verifyReleaseArtifact` inspects the merged Fabric JAR. It proves that required public common
contracts, shared Minecraft/runtime classes, the Fabric entrypoint, MCP transport, and expanded
Fabric metadata are present. It rejects tests, source files, and bundled third-party integration
implementations.

`releaseBundle` writes the JAR and SHA-256 checksum under `build/release/` using the filename
`thread-fabric-<version>.jar`.

## Publishing checklist

1. Update `mod_version` and `CHANGELOG.md`.
2. Run the full gate and the manual MCP smoke test in [Development](DEVELOPMENT.md).
3. Inspect `build/release/` and verify the checksum.
4. Confirm the worktree contains only intended release changes.
5. In a repository with a configured remote, create and verify a code-signed commit.
6. Tag that exact commit as `vMAJOR.MINOR.PATCH`, then push the commit and tag.

The tag workflow repeats formatting, lint, compilation, unit/architecture tests, all three
packaged-client lifecycles, artifact inspection, checksum generation, and version/tag matching
before attaching files to the GitHub release.

Automatic Modrinth, CurseForge, Maven, common-module, or other loader publishing is not currently
implemented.
