# Thread Development, Testing, and Release

This is the contributor checklist for source quality, local validation, packaged game tests, and
releases.

## Source standards

- Use Java 25 and the pinned Gradle/Fabric toolchain.
- Do not add Kotlin, `package-info.java`, wildcard imports, or hidden global state.
- Keep Minecraft/Fabric, core, and MCP dependencies inside their documented boundaries.
- Prefer small immutable DTOs, explicit wiring, explicit schemas, and machine-readable errors.
- Keep every game query bounded and every gameplay path behind the single-player guard.
- Do not add a dependency when the JDK or Minecraft-provided runtime already supplies the narrow
  capability safely.

Public core contracts and supported extension points need useful Javadocs. Document snapshot/live
semantics, bounds, threading, absence, errors, and invariants when they are not obvious. Comments
should explain non-obvious lifecycle, logical-side, protocol, compatibility, or safety decisions;
do not narrate syntax or retain commented-out code.

Third-party adapters belong in separate Thread Integrations artifacts. Their metadata-only Fabric
entrypoint must not link target-mod APIs. See [Thread Integrations](INTEGRATIONS.md).

## Formatting and static checks

Spotless is the formatting entry point and uses pinned google-java-format for Java. Checkstyle uses
the small repository-owned ruleset under `config/checkstyle`. Normal Gradle lifecycle tasks include
formatting, lint, compiler, architecture, and unit-test checks.

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck check build
```

CI checks formatting and never rewrites files. Do not disable or suppress a gate simply to make a
change pass; narrow a noisy rule only when the code has a documented legitimate reason.

## Test layers

### Unit and architecture tests

`test` covers core services, schemas, registries, configuration, integration activation/isolation,
Fabric conversion/support code, and the real loopback MCP HTTP server without launching Minecraft.
Fake providers should prove core behavior whenever live Minecraft is unnecessary.

Important regression areas include:

- success/error schema validation and deterministic discovery;
- all session states and multiplayer rejection;
- rich item/block/entity conversion and query caps;
- live recipe variants, alternatives, and provider fallback;
- maximum-flow crafting allocation without inventory double-counting;
- recursive plans, shared supplies, cycles, safe alternatives, and work limits;
- metadata-first integration loading and transactional contribution rollback;
- MCP initialize and stateless discovery, protocol validation, request/origin limits, shutdown, and
  same-port restart;
- package/import and release-artifact boundaries.

Run focused unit coverage with:

```powershell
.\gradlew.bat test
```

### Development client game test

```powershell
.\gradlew.bat runClientGameTest
```

This uses the development classpath for interactive diagnosis. It is useful during provider work
but is not the release proof.

### Packaged-client tests

```powershell
.\gradlew.bat runProductionClientGameTest
.\gradlew.bat runMcpDisabledProductionClientGameTest
```

The first test launches a temporary client with the remapped runtime JAR, Fabric API, and the
separately packaged proof integration/game-test mod. It creates a temporary single-player world,
checks native recipe additions/replacements, activates the external proof candidate, initializes
the real MCP endpoint, lists the catalog, and calls all thirteen tools.

The second test launches the same runtime with MCP disabled and proves Thread initializes without a
listener. Neither task may silently fall back to the source-set development classpath.

## Full local gate

Before committing a V1-complete change, run the release-equivalent gate from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle runProductionClientGameTest `
  runMcpDisabledProductionClientGameTest verifyReleaseVersion "-PreleaseTag=v0.1.0"
git diff --check
```

Change the release tag argument when `mod_version` changes. `verifyReleaseArtifact` rejects test
classes, development-only metadata, and bundled third-party integration content. `releaseBundle`
writes the validated JAR and SHA-256 file to `build/release/`.

## Manual MCP smoke test

Use the release JAR, not a development run, for the final human check:

1. Put the release JAR and the required Fabric API in a clean Minecraft 26.2 instance.
2. Launch to the menu and confirm `http://127.0.0.1:25580/mcp` is listening.
3. Connect a real MCP client, complete `initialize` followed by `tools/list`, and confirm thirteen
   read-only `minecraft.*` tools.
4. Call `minecraft.get_status` in the menu; it must return a controlled unsupported/no-world state.
5. Load an integrated single-player world and call status, player, inventory, equipment, target,
   nearby-entity, recipe, crafting, search, and capability tools.
6. Confirm tools return detached bounded data, recipes reflect the live world, and no call mutates
   the game.
7. Join multiplayer only for rejection verification if appropriate: status remains callable while
   gameplay tools return `UNSUPPORTED` without exposing live state.
8. Exit Minecraft and confirm the listener closes cleanly.

Automated HTTP, packaged-world, disabled-listener, boundary, and limit checks remain authoritative;
the manual smoke test verifies actual client interoperability and installation behavior.

## Release process

1. Update `mod_version` and `CHANGELOG.md`.
2. Run the full local gate and manual smoke test.
3. Inspect `build/release/`, verify the checksum, and confirm the Git worktree contains only the
   intended release changes.
4. Create a signed commit in repositories with a configured remote and verify its signature.
5. Tag the exact commit as `vMAJOR.MINOR.PATCH`, matching `mod_version`, then push the commit and tag.

The tag workflow repeats the clean gate, packaged-client tests, artifact verification, checksum
generation, and version-match check before attaching the files to the GitHub release. Automatic
Modrinth, CurseForge, Maven, or other publishing is not part of V1.

## CI

`.github/workflows/ci.yml` runs on pull requests, primary-branch pushes, and manual dispatch.
`.github/workflows/release.yml` handles `v*` tags. Both use the Gradle Wrapper and the same project
tasks documented above; local work must not rely on a hidden alternative validation path.
