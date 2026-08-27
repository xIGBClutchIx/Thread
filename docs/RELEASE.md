# Thread Release

Thread releases two isolated artifact matrices, one for Minecraft 26.1.2 and one for Minecraft
26.2. Each matrix contains a recommended universal JAR plus dedicated Fabric, NeoForge, and Forge
JARs. Users install exactly one artifact matching their exact Minecraft version.

`gradle/version-matrix.gradle` owns the supported Java/Minecraft/loader coordinates. Universal JARs
come from root packaging tasks rather than per-version universal source projects; release
validation and bundling consume the same matrix and task outputs.

## Release gate

From the repository root, first run the build and artifact gate:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle verifyReleaseVersion "-PreleaseTag=v0.1.0"
```

Then run the restart and MCP-disabled packaged proofs separately for dedicated and universal JARs
on Fabric, NeoForge, and Forge in both version projects. The exact commands are listed in
[Development](DEVELOPMENT.md). Separate invocations prevent real clients from overlapping on the
MCP port or mutating another task's prepared instance. Finish with:

```powershell
git diff --check
```

Change the tag argument when `mod_version` changes. Versions use `MAJOR.MINOR.PATCH`; tags use
`vMAJOR.MINOR.PATCH`.

`verifyReleaseArtifact` independently checks all eight JARs. It proves exact Minecraft and loader
metadata, dedicated loader purity, universal loader coverage, required common/runtime/provider
classes, absence of the other Minecraft version's capability implementation, no duplicate/test/
source/bundled-integration content, and no eager loader references from shared classes.

`releaseBundle` writes these version-labeled JARs and their checksum files under `build/release/`:

```text
thread-universal-26.1.2-<version>.jar
thread-fabric-26.1.2-<version>.jar
thread-neoforge-26.1.2-<version>.jar
thread-forge-26.1.2-<version>.jar
thread-universal-26.2-<version>.jar
thread-fabric-26.2-<version>.jar
thread-neoforge-26.2-<version>.jar
thread-forge-26.2-<version>.jar
```

## Publishing checklist

1. Update `mod_version` and `CHANGELOG.md`.
2. Run the full gate and manual MCP smoke test for both Minecraft versions.
3. Inspect `build/release/` and confirm all eight version-labeled JARs exist.
4. Confirm the worktree contains only intended release changes.
5. In a repository with a configured remote, create and verify a code-signed commit.
6. Tag that exact commit as `vMAJOR.MINOR.PATCH`, then push the commit and tag.

CI and the tag workflow repeat both version matrices. Automatic Modrinth, CurseForge, Maven,
common-module, or other loader publishing is not currently implemented.
