# Thread Integrations

Thread activates optional integrations from metadata only after configuration, mod presence, and
version compatibility checks succeed. An absent integration never changes vanilla startup or tool
behavior. Active integrations appear in `minecraft.get_capabilities`.

## Native recipe base

Thread does not require JEI to see recipes registered through Minecraft's normal data-driven recipe
system. Its base Fabric provider reads the integrated server's final live `RecipeManager`, which
reflects vanilla definitions plus active datapack and Fabric-mod additions, replacements, and
removals after reload. No hard-coded or static vanilla recipe list is used.

The base read always happens first so Thread's single-player guard and recipe limits remain
authoritative. Optional recipe providers are then considered in stable integration-ID order. The
first successful provider with at least one representable recipe wins for that item. If every
optional provider returns no recipes, reports unavailability, or fails, Thread returns the captured
native result unchanged.

## Just Enough Items (JEI)

| Component | Supported |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.155.0+26.2 or newer for Minecraft 26.2 |
| JEI | 30.26.0.182 through compatible 30.x Fabric builds |
| Thread integration ID | `jei` |

JEI is optional and is not bundled inside Thread. Install the normal JEI Fabric JAR beside Thread
to add JEI's live, modpack-aware recipe catalog to these existing tools:

- `minecraft.get_recipe`
- `minecraft.can_craft`
- `minecraft.get_missing_ingredients`
- `minecraft.get_crafting_plan`

No JEI-specific MCP tools are added. When JEI returns at least one supported recipe for an item,
that result is preferred over the native result. JEI recipe data is converted inside the Fabric integration
adapter into Thread's existing detached recipe DTOs, then flows through the same core crafting
services as vanilla recipes. MCP and core do not import JEI types.

Thread represents recipes only when JEI supplies a stable recipe ID, exactly one item output, and
consumed input slots whose complete alternatives are item stacks with consistent counts.
Source item tags are retained when JEI exposes them. Recipes with fluids or other custom ingredient
types, ambiguous output variants, multiple outputs, missing stable IDs, excessive slots/results, or
layouts JEI cannot build are skipped. Thread does not invent partial recipe data.

JEI access runs on Minecraft's client thread and remains behind Thread's existing successful native
recipe read, so the centralized session guard stays authoritative. If JEI's runtime is unavailable,
returns no supported recipe, or fails, Thread keeps the successful native result.

To disable the adapter while keeping JEI installed:

```json
{
  "schemaVersion": 1,
  "disabledIntegrations": ["jei"]
}
```

Restart Minecraft after changing integration configuration. The disabled check happens before
Thread resolves `JeiIntegration`; the small `jei_mod_plugin` lifecycle bridge may still be loaded by
JEI itself because that is the public API used to supply `IJeiRuntime`, but no Thread JEI recipe
provider is registered.

REI and EMI are not supported by this slice. Installing them does not activate a Thread integration.
