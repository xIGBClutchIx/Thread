# Changelog

All notable user-visible changes to Thread are recorded here. Releases follow semantic versioning.

## Unreleased

### Added

- Deterministic direct craftability, missing-ingredient analysis, and bounded recursive crafting
  plans across thirteen read-only tools.
- Optional mod integration discovery with absent-mod-safe class loading, transactional
  contributions, typed recipe/block/entity extension points, and active integration metadata.
- Optional JEI `30.x` recipe integration for supported modified recipes, alternatives, stable
  variants, craftability, missing ingredients, and recursive plans.

### Changed

- Raised the Minecraft 26.2 Fabric API minimum to `0.155.0+26.2`, matching the supported JEI line.

## 0.1.0 - 2026-08-24

### Added

- Ten bounded, read-only Minecraft tools for session, game, player, inventory, equipment, target,
  nearby-entity, recipe, item-search, and capability context.
- Fabric client providers with centralized single-player guards and logical-thread dispatch.
- Loopback-only stateless MCP `2026-07-28` Streamable HTTP transport.
- Persistent safety configuration, tool filtering, request limits, and clean MCP disablement.
- Packaged-client game tests, release artifact validation, SHA-256 generation, and tag-driven GitHub
  releases.
