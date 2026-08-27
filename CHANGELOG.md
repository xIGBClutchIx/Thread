# Changelog

All notable user-visible changes to Thread are recorded here. Releases follow semantic versioning.

## Unreleased

### Added

- Actual-look entity inspection through `minecraft.get_target_entity`, reusing the nearby-entity
  contract with bounded equipment, active effects, age, tame ownership, and villager metadata.
- Compact live world/environment context through `minecraft.get_world_info`, including dimension,
  biome, player and global spawn positions, same-dimension spawn distance, difficulty and hardcore,
  day/time, daylight, weather, local light, moon phase, and native biome climate values.
- Read-only vanilla advancement awareness through `minecraft.get_advancements` and
  `minecraft.get_advancement`, with live integrated-server progress, visible/known client gating,
  criteria and timestamps, deterministic filters/search, and bounded results.
- Opt-in `PLAYER_AND_NEARBY` scope for all three crafting analysis tools, with the existing
  player-only behavior retained by default, one consistent bounded source snapshot, structured
  container allocations, and explicit incomplete-discovery status.
- Unified `minecraft.find_item` search across player inventory, offhand/armor, and nearby loaded
  containers, with exact-ID and friendly text matching, aggregate counts, structured locations,
  and deterministic ordering.
- Safe nearby loaded-container discovery and individual inspection across common vanilla storage
  and machines, with compact summaries, full bounded inventory snapshots, selected furnace/brewing
  state, and no chunk loading or crafting changes.
- A recommended `thread-universal-<version>.jar` that packages common output and all three thin
  loader adapters directly, with duplicate detection and the same packaged parity proofs on
  Fabric, NeoForge, and Forge. Dedicated loader JARs remain available.
- Forge 65.1.2 support with a thin loader adapter, Java-service integration discovery, a separate
  release JAR, and the same real packaged-client lifecycle/MCP/recipe proofs as the other loaders.
- NeoForge 26.2 support with a thin loader adapter, Java-service integration discovery, a separate
  release JAR, and real packaged-client lifecycle/MCP/recipe tests.
- Deterministic direct craftability, missing-ingredient analysis, and bounded recursive crafting
  plans within the read-only catalog.
- Optional mod integration discovery with absent-mod-safe class loading, transactional
  contributions, typed recipe/block/entity extension points, and active integration metadata.
- Loader-specific metadata discovery for separately distributed Thread Integrations packages:
  `thread:integrations` on Fabric and standard Java services on NeoForge and Forge.

### Changed

- Expanded `minecraft.get_player` with authoritative armor/air, bounded active effects, movement and
  condition flags, selected hotbar/attack cooldown, game mode/hardcore, and conditional vehicle and
  respawn context while retaining a focused read-only payload.
- Consolidated loader-neutral config, startup, MCP, logging, and shutdown behavior in the shared
  runtime, with one packaged parity contract exercised by Fabric, NeoForge, and Forge.
- Split the project into `:common`, `:fabric`, `:neoforge`, and `:forge`, moved loader-neutral Minecraft
  providers and runtime assembly into common, and kept all loader adapters limited to
  loader/lifecycle/discovery wiring.
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
