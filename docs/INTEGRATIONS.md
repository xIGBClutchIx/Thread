# Thread Integrations

Thread Integrations are separately distributed optional mods that contribute read-only
capabilities to base Thread. Each base loader JAR contains native Minecraft support and the
extension contracts, but no third-party gameplay-mod or recipe-viewer adapter.

## Supported external API

The supported loader-neutral integration surface is:

| Contract | Purpose |
| --- | --- |
| `ThreadIntegrationCandidateProvider` | Metadata-only loader discovery entrypoint |
| `IntegrationCandidate` | Stable ID, target mod, version requirement, and deferred implementation class name |
| `IntegrationId` | Stable ordering/configuration/capability identity |
| `ThreadIntegration` | Identity plus one transactional registration callback |
| `IntegrationContext` | Contributes tools, contexts, recipes, typed extensions, and capability metadata |
| `GameTool` / `ContextProvider` | Read-only core tool and bounded context contracts |
| `IntegrationRecipeProvider` | Optional detached recipe contribution |
| `IntegrationExtensionPoint` | Typed extension key |
| `CoreIntegrationExtensionPoints` | Core-owned extension keys such as recipe providers |

These contracts expose only Java and Thread core types. They do not expose a loader, Minecraft, MCP,
or target-mod types.

Thread also provides shared Minecraft-edge contracts for integrations that must inspect an already
selected Minecraft object:

- `MinecraftIntegrationExtensionPoints`;
- `MinecraftBlockEntityInspector`;
- `MinecraftBlockEnricher`;
- `MinecraftEntityEnricher`.

Those interfaces deliberately live under `platform.minecraft`, outside core. They can be reused by
any client loader because they depend on Minecraft rather than Fabric, but they are not plain-Java
core API. They may accept Minecraft inputs on the owning logical thread; contributions must return
detached Thread DTOs and remain bounded/read-only. Optional-mod objects, raw NBT, and component
maps must never be returned. Their stable extension IDs use the `minecraft.*` namespace.

## Internal implementation surface

The following types may be public for cross-package wiring or tests but are not external API
promises:

- `IntegrationRegistry`, `IntegrationInfo`, `IntegrationActivation`, and
  `IntegrationActivationStatus`;
- `IntegrationEnvironment`, `IntegrationLoader`, `IntegrationLoadException`, and
  `ReflectiveIntegrationLoader`;
- `IntegrationExtensionRegistry` and `CompositeRecipeProvider`;
- loader integration catalogs/environments, shared Minecraft provider/enricher
  registries, client runtime/lifecycle wiring, configuration, and MCP classes;
- the built-in `VanillaIntegration` implementation.

External packages should not construct registries, loaders, platform providers, or transports.
Their entry is a loader-discovered candidate provider followed by the loader-neutral
`ThreadIntegration` callback.

## Packaging and discovery

An external Fabric JAR declares a metadata-only entrypoint:

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

The candidate provider loads before target compatibility is known. It must import only Thread
contracts and have no target-mod side effects. Thread isolates a broken provider and continues
collecting candidates from other external JARs.

An external NeoForge JAR lists the same provider in the standard Java service file
`META-INF/services/me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider`:

```text
example.thread.integration.ExampleCandidateProvider
```

The provider class uses the same Java API shown above. NeoForge candidate requirements use Maven
version-range syntax such as `[1.0,2.0)`; Fabric candidates use Fabric Loader predicate syntax such
as `>=1 <2`. Implementations remain class-name strings on both loaders.

For each candidate, Thread checks in this order:

1. the stable integration ID is enabled;
2. the target mod is loaded;
3. the target version satisfies the candidate requirement;
4. `ReflectiveIntegrationLoader` resolves and constructs the named no-argument implementation;
5. the implementation registers its contributions transactionally.

The reflective loader is intentional. A class-name string is the narrow mechanism that prevents an
absent optional API from being verified or linked before presence/version checks. Java
`ServiceLoader` is used only to collect NeoForge candidate-provider metadata; it never constructs
the deferred integration implementation. Do not replace the implementation class name with a class
literal or eager implementation import.

## Transactional registration

`ThreadIntegration.register` receives one short-lived `IntegrationContext`. Contributions become
visible only after the callback and all duplicate/contract validation succeed. Callback,
construction, linkage, duplicate, or metadata failures leave no partial registration and do not
stop later candidates.

Integrations must not retain the context, start generic background workers, or assume start/stop/
reload callbacks. Add lifecycle only when a concrete external integration proves and tests that
need.

Capability metadata is bounded and derived from committed contributions. `minecraft.get_capabilities`
therefore reports only active integrations and their usable surfaces.

## Recipe behavior

`MinecraftRecipeProvider` reads the integrated server's final live `RecipeManager` first. That native
result establishes the authoritative single-player guard and query limits.

`CompositeRecipeProvider` then evaluates external `IntegrationRecipeProvider` contributions in
stable integration-ID order. The first successful non-empty result may replace the native result
for that item. Empty results, controlled failures, runtime exceptions, and linkage errors preserve
the already-captured native fallback. Core crafting services and MCP never know which source won.

## Integration requirements

Every external Thread Integration must:

- remain read-only and single-player-only;
- use the owning Minecraft logical thread;
- keep scans, results, metadata, and payloads bounded;
- avoid force-loading chunks or resolving hidden/unopened state;
- convert target-mod and Minecraft objects to Thread DTOs at the platform edge;
- isolate optional dependency failures and preserve native fallback behavior;
- test absence, disabled configuration, incompatible versions, class/linkage failures,
  transactional rollback, and unchanged vanilla behavior.

JEI, EMI, REI, FTB Quests, Create, AE2, Mekanism, and similar systems are not supported by the base
artifact. Each requires its own separately versioned and tested integration JAR.
