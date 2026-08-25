# Thread Release Process

Thread V1 uses semantic versions. `mod_version` in `gradle.properties` contains `MAJOR.MINOR.PATCH`
without a prefix; the corresponding Git tag is exactly `vMAJOR.MINOR.PATCH`.

## Prepare

1. Choose the next semantic version and update `mod_version`.
2. Move user-visible changes into a dated section in `CHANGELOG.md`.
3. Update version-specific installation and compatibility text.
4. Run `./gradlew spotlessApply`.
5. Run the clean local release gate:

```powershell
./gradlew.bat clean spotlessCheck check build runProductionClientGameTest runMcpDisabledProductionClientGameTest releaseBundle verifyReleaseVersion "-PreleaseTag=v0.1.0"
```

Replace `v0.1.0` with the release tag. A mismatched tag intentionally fails.

The two production tasks load `build/libs/thread-<version>.jar` as the actual mod and add only an
isolated game-test harness. The enabled run performs discovery and all required live scenarios;
the disabled run proves that the same mod starts without an MCP listener.

## Inspect the bundle

`releaseBundle` writes only these files to `build/release/`:

```text
thread-<version>.jar
thread-<version>.jar.sha256
```

`verifyReleaseArtifact` checks the runtime filename, Fabric identity/version, client environment,
entrypoint, exact Minecraft/Fabric/Java dependencies, required runtime classes, and absence of
source or game-test content. Independently verify the checksum before tagging.

## Publish

1. Commit the release changes.
2. Create the matching annotated tag, for example `git tag -a v0.1.0 -m "Thread 0.1.0"`.
3. Push the commit and tag.
4. Wait for `.github/workflows/release.yml` to pass.
5. Download the workflow artifact and confirm its checksum matches the GitHub release files.
6. Complete [the manual MCP smoke test](MANUAL_SMOKE_TEST.md) against the downloaded JAR.

The tag workflow repeats the clean quality gate and both packaged-client tests, rejects a tag/version
mismatch, creates the JAR/checksum bundle, uploads it as a workflow artifact, and creates or updates
the GitHub release. No Modrinth, CurseForge, Maven, or other registry publication is performed.
