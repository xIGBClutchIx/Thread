# Thread Integrations

Thread's base artifact contains native Minecraft/Fabric support and public integration hooks. It
does not contain adapters for third-party gameplay mods, recipe viewers, quest systems, storage
networks, or automation mods.

## Distribution model

The intended direction is:

```text
Thread
  -> native Minecraft/Fabric support
  -> public integration hooks

Thread Integrations
  -> separate optional mods/packages
  -> JEI
  -> FTB Quests
  -> Create
  -> AE2
  -> other third-party integrations
```

**Thread Integrations** is the umbrella concept for future separately distributed adapters. No
`thread-jei` or other integration package is created by the current cleanup slice, and installing a
third-party mod beside base Thread does not activate support automatically.

This separation keeps the base artifact dependency-light, prevents recipe-viewer or gameplay-mod
APIs from entering Thread core, and lets players install only the adapters their modpack needs.

## Native recipe base

`FabricRecipeProvider` reads the integrated server's final live `RecipeManager` on its owning
logical thread. The result includes vanilla definitions plus active datapack and Fabric-mod recipe
additions, replacements, and removals after reload. Thread does not ship or consult a static vanilla
recipe catalog.

The base read always happens first so the centralized single-player guard and recipe limits remain
authoritative. `CompositeRecipeProvider` can then consider contributed
`IntegrationRecipeProvider` values in stable integration-ID order. The first successful provider
with at least one recipe wins for that item. Empty results, controlled failures, runtime exceptions,
and linkage failures preserve the captured native result unchanged.

Base Thread registers no optional recipe provider, so its recipe lookup, craftability,
missing-ingredient analysis, and recursive planner all consume the live native result.

## Public integration framework

The generic framework remains part of Thread:

- `ThreadIntegration` defines a stable integration identity and one transactional registration
  callback.
- `IntegrationCandidate` and `IntegrationRegistry.discover` defer implementation class loading
  until configuration, target-mod presence, and version compatibility checks pass.
- `IntegrationContext` can contribute read-only tools, bounded contexts, recipe providers, typed
  extensions, and bounded capability metadata.
- `IntegrationExtensionRegistry` keeps contribution ordering deterministic and isolates optional
  recipe/enrichment failures from native behavior.
- `disabledIntegrations` rejects an exact integration ID before its implementation class is loaded.

Successful registration commits all contributions together. A duplicate, callback failure,
construction failure, or linkage failure leaves no partial registration and does not prevent a
healthy later candidate from activating. Active integrations and their contribution metadata are
derived from the live registry and exposed by `minecraft.get_capabilities`.

The base Fabric catalog's bundled-candidate list is intentionally empty. Separately distributed
packages connect through the `thread:integrations` Fabric entrypoint. Its entrypoint class implements
`ThreadIntegrationCandidateProvider` and returns only `IntegrationCandidate` metadata:

```json
{
  "entrypoints": {
    "thread:integrations": [
      "example.thread.integration.ExampleCandidateProvider"
    ]
  }
}
```

```java
public final class ExampleCandidateProvider implements ThreadIntegrationCandidateProvider {
    @Override
    public List<IntegrationCandidate> candidates() {
        return List.of(new IntegrationCandidate(
                IntegrationId.of("example"),
                "target-mod-id",
                ">=1 <2",
                "example.thread.integration.ExampleIntegration"));
    }
}
```

The candidate provider is loaded before target-mod compatibility is known, so it must depend only
on Thread contracts and must not import or initialize target-mod APIs. Thread isolates a broken
provider, collects healthy candidate metadata, applies `disabledIntegrations`, mod presence, and
version checks, then reflectively constructs the named no-argument `ThreadIntegration`
implementation. The actual implementation may depend on the target mod because it is not resolved
until those checks pass.

## Third-party boundary

Integration packages must convert optional-mod and Minecraft objects into detached Thread
DTOs before crossing core/provider contracts. They must retain Thread's read-only, single-player,
threading, bounded-query, and no-forced-chunk-loading rules. Core services, tool contracts, and MCP
transport must remain unaware of the third-party API that supplied a contribution.

JEI, FTB Quests, Create, AE2, EMI, REI, Mekanism, and other third-party systems are not currently
supported by the base Thread artifact.
