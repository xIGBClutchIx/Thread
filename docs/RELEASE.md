# Thread Release

Thread releases a recommended universal client artifact plus dedicated Fabric, NeoForge, and Forge
artifacts. Users install exactly one of them.

## Release gate

From the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle verifyReleaseVersion "-PreleaseTag=v0.1.0"
.\gradlew.bat --no-daemon --console=plain runRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain runMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyNeoForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyForgeMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalFabricRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalFabricMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalNeoForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalForgeMcpDisabledProductionClientGameTest
git diff --check
```

Run each packaged task separately so real client processes cannot overlap on the MCP port or mutate
another task's prepared instance.

Change the tag argument when `mod_version` changes. Versions use `MAJOR.MINOR.PATCH`; tags use
`vMAJOR.MINOR.PATCH`.

`verifyReleaseArtifact` delegates to independent universal, Fabric, NeoForge, and Forge checks. The
dedicated checks prove loader purity. The universal check requires all adapters and metadata,
rejects duplicate entries and development/bundled-integration content, and scans compiled common
classes for eager loader-specific references.

`releaseBundle` writes four JARs and SHA-256 checksums under `build/release/`:

```text
thread-universal-<version>.jar
thread-fabric-<version>.jar
thread-neoforge-<version>.jar
thread-forge-<version>.jar
```

## Publishing checklist

1. Update `mod_version` and `CHANGELOG.md`.
2. Run the full gate and the manual MCP smoke test in [Development](DEVELOPMENT.md).
3. Inspect `build/release/` and verify the checksum.
4. Confirm the worktree contains only intended release changes.
5. In a repository with a configured remote, create and verify a code-signed commit.
6. Tag that exact commit as `vMAJOR.MINOR.PATCH`, then push the commit and tag.

The tag workflow repeats formatting, lint, compilation, unit/architecture tests, all three
packaged-client lifecycles for dedicated and universal artifacts on all three loaders, artifact
inspection, checksum generation, and version/tag matching before attaching files to the GitHub
release.

Automatic Modrinth, CurseForge, Maven, common-module, or other loader publishing is not currently
implemented.
