# Thread Development and Testing

This is the contributor checklist for source quality, local validation, packaged game tests, and
releases.

## Module workflow

Thread is built as `:common` plus `:fabric`. Shared core, configuration, MCP, runtime assembly, and
loader-neutral Minecraft providers belong in `:common`. Fabric entrypoints, Loader API access,
lifecycle events, and integration entrypoint discovery belong in `:fabric`.

Root Gradle tasks aggregate both modules and remain the normal contributor interface. See
[Build](BUILD.md) for module-specific commands and outputs.

## Source standards

- Use Java 25 and the pinned Gradle/Fabric toolchain.
- Do not add Kotlin, `package-info.java`, wildcard imports, or hidden global state.
- Keep Minecraft, loader, core, and MCP dependencies inside their documented module boundaries.
- Apply the portability rule literally: code that another loader can invoke unchanged stays in
  common; code that calls Fabric APIs stays in the Fabric adapter.
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

The root `test` task runs `:common:test` and `:fabric:test`. They cover core services, schemas,
registries, configuration, integration activation/isolation, shared Minecraft conversion/support,
Fabric discovery, and the real loopback MCP HTTP server without launching Minecraft. Fake
providers should prove core behavior whenever live Minecraft is unnecessary.

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
- common-to-Fabric module direction and the absence of Fabric imports/dependencies in common.

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
.\gradlew.bat runRestartProductionClientGameTest
.\gradlew.bat runMcpDisabledProductionClientGameTest
```

The first test launches a temporary client with the merged `thread-fabric-<version>.jar`, Fabric
API, and the
separately packaged proof integration/game-test mod. It creates a temporary single-player world,
checks native recipe additions/replacements, activates the external proof candidate, initializes
the real MCP endpoint, lists the catalog, calls all thirteen tools, then closes the world and proves
gameplay calls return controlled errors at the menu.

The restart test launches that packaged client again from the same instance and proves an existing
configuration is reloaded, including a changed MCP port. The final test uses a separate fresh
instance with MCP disabled and proves Thread initializes without a listener. None of these tasks
may silently fall back to the source-set development classpath.

## Full local gate

Before committing a V1-complete change, run the release-equivalent gate from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle runProductionClientGameTest `
  runRestartProductionClientGameTest runMcpDisabledProductionClientGameTest `
  verifyReleaseVersion "-PreleaseTag=v0.1.0"
git diff --check
```

Change the release tag argument when `mod_version` changes. Release versions and tags must use
`MAJOR.MINOR.PATCH` and `vMAJOR.MINOR.PATCH`, respectively. `verifyReleaseArtifact` rejects test
classes, source files, and bundled third-party integrations while checking common contracts,
shared runtime/provider classes, the Fabric entrypoint, and expanded Fabric metadata.
`releaseBundle` writes `thread-fabric-<version>.jar` and its SHA-256 file to `build/release/`. See
[Release](RELEASE.md) for the publishing checklist.

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

The authoritative release gate, artifact layout, and publishing checklist are documented in
[Release](RELEASE.md).

## CI

`.github/workflows/ci.yml` runs on pull requests, primary-branch pushes, and manual dispatch.
`.github/workflows/release.yml` handles `v*` tags. Both use the Gradle Wrapper and the same project
tasks documented above; local work must not rely on a hidden alternative validation path.
