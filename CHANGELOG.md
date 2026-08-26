# Changelog

All notable user-visible changes to Thread are recorded here. Releases follow semantic versioning.

## Unreleased

### Added

- Deterministic direct craftability, missing-ingredient analysis, and bounded recursive crafting
  plans across thirteen read-only tools.
- Optional mod integration discovery with absent-mod-safe class loading, transactional
  contributions, typed recipe/block/entity extension points, and active integration metadata.
- A `thread:integrations` Fabric entrypoint for metadata-only candidates from separately distributed
  Thread Integrations packages.

### Changed

- Split the project into `:common` and `:fabric`, moved loader-neutral Minecraft providers and
  runtime assembly into common, and reduced Fabric to loader/lifecycle/discovery wiring while
  preserving the single Fabric release artifact and all V1 behavior.
- Renamed Minecraft-facing integration extension contracts from Fabric-specific names/IDs to
  loader-neutral `Minecraft*` contracts and `minecraft.*` extension IDs.
- Restored the Minecraft 26.2 Fabric API minimum to the native `0.154.0+26.2` baseline.
- Made recipe-source precedence deterministic: the first usable optional provider wins per item,
  while Minecraft's live recipe manager remains the guarded base and fallback.
- Defined **Thread Integrations** as future separately distributed optional mods/packages rather
  than code bundled into the base Thread artifact.
- Consolidated contributor documentation around the current V1 system and narrowed unused internal
  API surface without changing tool behavior.
- Hardened 0.1.0 packaging with reproducible archives, strict release version/tag validation,
  public integration-contract checks, development-content rejection, and restart coverage.
- Kept unexpected serialization failures out of public tool-error details while retaining concise,
  structured client errors.

### Removed

- Removed the bundled JEI adapter, dependency repository/configuration, capability metadata,
  plugin entrypoints, tests, packaged runs, CI tasks, and installation instructions.

## 0.1.0 - 2026-08-24

### Added

- Ten bounded, read-only Minecraft tools for session, game, player, inventory, equipment, target,
  nearby-entity, recipe, item-search, and capability context.
- Fabric client providers with centralized single-player guards and logical-thread dispatch.
- Loopback-only stateless MCP `2026-07-28` Streamable HTTP transport.
- Persistent safety configuration, tool filtering, request limits, and clean MCP disablement.
- Packaged-client game tests, release artifact validation, SHA-256 generation, and tag-driven GitHub
  releases.
