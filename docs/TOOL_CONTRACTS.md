# Thread V1 Tool Contracts

These are semantic contracts, not mandatory byte-for-byte JSON. Keep names and meanings stable once V1 clients depend on them.

All results should prefer canonical registry IDs over localized display names.

## Common conventions

### Position

```json
{
  "x": 123.5,
  "y": 64.0,
  "z": -20.25
}
```

Block positions should use integers where appropriate.

### Item stack

```json
{
  "itemId": "minecraft:iron_ingot",
  "count": 23,
  "maxCount": 64,
  "displayName": "Iron Ingot"
}
```

`displayName` is convenience metadata and must not be used as the canonical key.

### Bounded results

Queries that can return many entries should include:

```json
{
  "truncated": false,
  "limit": 64,
  "items": []
}
```

## `minecraft.get_status`

Purpose: report the current Minecraft session state and whether gameplay tools are available. This tool must work from menus and during unsupported multiplayer sessions.

Input: none.

Example while in the main menu:

```json
{
  "state": "MAIN_MENU",
  "worldLoaded": false,
  "playerAvailable": false,
  "supported": false,
  "reason": "NO_WORLD"
}
```

Example in a supported single-player world:

```json
{
  "state": "SINGLEPLAYER",
  "worldLoaded": true,
  "playerAvailable": true,
  "supported": true,
  "reason": null
}
```

Example while connected to multiplayer:

```json
{
  "state": "MULTIPLAYER",
  "worldLoaded": true,
  "playerAvailable": true,
  "supported": false,
  "reason": "MULTIPLAYER_UNSUPPORTED"
}
```

Keep the state enum small and stable. Do not expose implementation details such as Minecraft screen class names. AI clients should be able to call this as a cheap preflight before gameplay tools.

## `minecraft.get_game_info`

Purpose: identify the running Minecraft environment.

Input: none.

Example result:

```json
{
  "minecraftVersion": "<pinned-version>",
  "loader": "fabric",
  "loaderVersion": "<version>",
  "threadVersion": "0.1.0"
}
```

Session/world availability belongs to `minecraft.get_status`; player/world-specific details belong to their dedicated tools. `minecraft.get_game_info` should remain useful from the main menu.

## `minecraft.get_player`

Purpose: return current local-player status.

Input: none.

Example result:

```json
{
  "health": 18.0,
  "maxHealth": 20.0,
  "food": 14,
  "saturation": 3.5,
  "experienceLevel": 21,
  "experienceProgress": 0.42,
  "position": {"x": 152.2, "y": 67.0, "z": -381.7},
  "dimension": "minecraft:overworld",
  "gameMode": "survival"
}
```

Do not include identity/account identifiers unless a future use case explicitly requires them.

## Shared item shape

Inventory, equipment, recipe results, and inspected block-entity contents use the same non-empty
item shape. `displayName` is the localized base name; `customName` is separate. Durability is null
for non-damageable items, and enchantments sort by canonical registry ID. `components` contains a
fixed set of selected values. Thread does not serialize arbitrary component or NBT data.

```json
{
  "itemId": "minecraft:diamond_pickaxe",
  "displayName": "Diamond Pickaxe",
  "customName": "Workhorse",
  "count": 1,
  "maxCount": 1,
  "durability": {"remaining": 1500, "maximum": 1561, "damage": 61},
  "enchantments": [
    {"enchantmentId": "minecraft:efficiency", "level": 5}
  ],
  "components": {
    "rarity": null,
    "unbreakable": false,
    "repairCost": 2,
    "lore": ["Mining tool"],
    "potionId": null,
    "storedItemStacks": 0
  }
}
```

## `minecraft.get_inventory`

Purpose: return the local player's main-inventory snapshot without duplicating held or armor
equipment.

Input: none.

Example result:

```json
{
  "selectedHotbarSlot": 0,
  "slots": [
    {
      "slot": 0,
      "stack": {
        "itemId": "minecraft:diamond_pickaxe",
        "displayName": "Diamond Pickaxe",
        "customName": null,
        "count": 1,
        "maxCount": 1,
        "durability": {"remaining": 1561, "maximum": 1561, "damage": 0},
        "enchantments": [],
        "components": null
      }
    }
  ]
}
```

Slots cover the 36 main inventory positions (`0` through `35`). Empty positions are omitted.
Query held and armor positions through `minecraft.get_equipment`.

## `minecraft.get_equipment`

Purpose: return held and equipped items.

Input: none.

Example result:

```json
{
  "slots": [
    {
      "slot": "MAIN_HAND",
      "item": {
        "itemId": "minecraft:diamond_pickaxe",
        "displayName": "Diamond Pickaxe",
        "customName": null,
        "count": 1,
        "maxCount": 1,
        "durability": {"remaining": 1561, "maximum": 1561, "damage": 0},
        "enchantments": [],
        "components": null
      }
    },
    {"slot": "OFF_HAND", "item": null},
    {
      "slot": "HEAD",
      "item": {
        "itemId": "minecraft:iron_helmet",
        "displayName": "Iron Helmet",
        "customName": null,
        "count": 1,
        "maxCount": 1,
        "durability": {"remaining": 165, "maximum": 165, "damage": 0},
        "enchantments": [],
        "components": null
      }
    },
    {"slot": "CHEST", "item": null},
    {"slot": "LEGS", "item": null},
    {"slot": "FEET", "item": null}
  ]
}
```

Every response contains each of the six positions once. Empty positions use `item: null`.

## `minecraft.get_target_block`

Purpose: inspect the block the player is currently looking at within normal interaction/raycast range.

Input: optional bounded distance only if needed by implementation. Prefer game-standard targeting behavior.

Example result:

```json
{
  "blockId": "minecraft:furnace",
  "displayName": "Furnace",
  "position": {"x": 153, "y": 67, "z": -379},
  "properties": {
    "facing": "north",
    "lit": "false"
  },
  "distance": 3.4,
  "blockEntityPresent": true,
  "blockEntity": {
    "typeId": "minecraft:furnace",
    "inventorySize": 3,
    "items": [
      {
        "slot": "input",
        "item": {
          "itemId": "minecraft:iron_ore",
          "displayName": "Iron Ore",
          "customName": null,
          "count": 3,
          "maxCount": 64,
          "durability": null,
          "enchantments": [],
          "components": null
        }
      }
    ],
    "state": {"kind": "furnace"}
  }
}
```

The provider reads the block and its block entity from loaded integrated-server state.
Vanilla furnaces expose named `input`, `fuel`, and `output` positions; other vanilla containers use
numeric positions. Empty positions are omitted and at most 64 item positions are inspected. An
unopened loot container reports its loot-table identity without resolving or reading contents.
Unknown block entities still report identity without raw NBT. Fabric integrations can register a
higher-priority safe inspector for richer mod-specific state later.

When the normal client raycast has no valid block target, Thread returns a structured `NOT_FOUND`
tool error rather than inventing block data.

## `minecraft.get_nearby_entities`

Purpose: summarize loaded entities around the local player.

Input:

```json
{
  "radius": 16,
  "limit": 64
}
```

Server-configured maxima always win over requested values.

Example result:

```json
{
  "radius": 16,
  "limit": 64,
  "truncated": false,
  "entities": [
    {
      "entityType": "minecraft:zombie",
      "displayName": "Zombie",
      "customName": null,
      "distance": 8.4,
      "position": {"x": 160.0, "y": 67.0, "z": -380.0},
      "living": true,
      "health": 20.0,
      "maxHealth": 20.0,
      "classification": "HOSTILE"
    }
  ]
}
```

Living entities include health and max health. Non-living entities use null for both fields.
Classification is nullable. Fabric assigns it to Minecraft's hostile enemy, passive animal/ambient
creature/villager, and neutral-mob families. Thread does not infer labels for other entities or
return entity NBT.

## `minecraft.get_recipe`

Purpose: return recipes in the current running game that produce the requested item.

Input:

```json
{
  "itemId": "minecraft:diamond_pickaxe"
}
```

Example result:

```json
{
  "itemId": "minecraft:diamond_pickaxe",
  "recipes": [
    {
      "recipeId": "minecraft:diamond_pickaxe",
      "type": "minecraft:crafting_shaped",
      "result": {"itemId": "minecraft:diamond_pickaxe", "count": 1},
      "ingredients": [
        {"itemIds": ["minecraft:diamond"], "tagIds": [], "count": 3},
        {"itemIds": ["minecraft:stick"], "tagIds": [], "count": 2}
      ]
    }
  ]
}
```

Minecraft recipe ingredients can represent alternatives/tags. The DTO must not falsely collapse alternatives into a single required item. If exact grouped counts are not always representable, preserve ingredient slots/groups instead of inventing certainty.

`itemIds` contains the complete resolved item alternatives used for comparisons. `tagIds` retains
the canonical source tags as provenance, so a tag-backed ingredient exposes both its current item
members and the tag that supplied them.

## `minecraft.can_craft`

Purpose: determine whether the player's current main inventory satisfies at least one live recipe
variant for the requested item. It assesses one recipe execution and performs no crafting action.

Input:

```json
{
  "itemId": "minecraft:diamond_pickaxe"
}
```

Example result:

```json
{
  "itemId": "minecraft:diamond_pickaxe",
  "craftable": false,
  "recipes": [
    {
      "variant": 1,
      "recipeId": "minecraft:diamond_pickaxe",
      "type": "minecraft:crafting_shaped",
      "resultCount": 1,
      "craftable": false,
      "ingredients": [
        {
          "itemIds": ["minecraft:diamond"],
          "tagIds": [],
          "required": 3,
          "available": 2,
          "missing": 1,
          "allocations": [
            {"itemId": "minecraft:diamond", "count": 2}
          ]
        },
        {
          "itemIds": ["minecraft:stick"],
          "tagIds": [],
          "required": 2,
          "available": 2,
          "missing": 0,
          "allocations": [
            {"itemId": "minecraft:stick", "count": 2}
          ]
        }
      ]
    }
  ]
}
```

`craftable` is true when any returned variant is craftable. Every relevant recipe remains a list
entry even when recipe IDs repeat. `variant` is a stable one-based ordinal assigned after canonical
sorting and identifies an individual returned variant.

`available` is the number actually allocated to that ingredient group, not a sum that another
ingredient may also claim. `allocations` shows the deterministic item choices made from alternatives.
The allocation maximizes satisfied requirements while consuming each inventory unit at most once.
Identical ingredient groups are merged before assessment, even if a provider supplies duplicates.
A supported lookup with no matching recipe returns `craftable: false` and an empty `recipes` array.

## `minecraft.get_missing_ingredients`

Purpose: explain shortages for every live recipe variant using the same fresh recipe/inventory
assessment as `minecraft.can_craft`.

Input and result use the same contract as `minecraft.can_craft`. Each ingredient remains present so
clients can explain the complete requirement; entries with `missing` greater than zero are the
unmet requirements. This prevents a missing-only projection from hiding how alternative items were
allocated across overlapping ingredient groups.

Both tools support shaped and shapeless recipes represented by the recipe provider. They inspect
only the player's 36 main-inventory slots and do not inspect equipment, nearby storage, crafting
stations, or fuel.

## `minecraft.get_crafting_plan`

Purpose: recursively explain the intermediate crafts and final raw materials needed for one target
item from the player's current 36-slot main inventory. The tool is read-only and performs no
crafting action.

Input uses the same canonical `itemId` contract as recipe lookup:

```json
{
  "itemId": "minecraft:crafting_table"
}
```

Representative result when the inventory contains one oak log:

```json
{
  "itemId": "minecraft:crafting_table",
  "requested": 1,
  "satisfiedFromInventory": 0,
  "craftable": true,
  "maxDepth": 32,
  "steps": [
    {
      "step": 1,
      "itemId": "minecraft:oak_planks",
      "variant": 1,
      "recipeId": "minecraft:oak_planks",
      "type": "minecraft:crafting_shapeless",
      "executions": 1,
      "resultCount": 4,
      "ingredients": [
        {
          "itemIds": ["minecraft:oak_log"],
          "tagIds": ["minecraft:oak_logs"],
          "required": 1,
          "available": 1,
          "missing": 0,
          "allocations": [{"itemId": "minecraft:oak_log", "count": 1}]
        }
      ]
    },
    {
      "step": 2,
      "itemId": "minecraft:crafting_table",
      "variant": 1,
      "recipeId": "minecraft:crafting_table",
      "type": "minecraft:crafting_shaped",
      "executions": 1,
      "resultCount": 1,
      "ingredients": [
        {
          "itemIds": ["minecraft:oak_planks"],
          "tagIds": ["minecraft:planks"],
          "required": 4,
          "available": 4,
          "missing": 0,
          "allocations": [{"itemId": "minecraft:oak_planks", "count": 4}]
        }
      ]
    }
  ],
  "missingMaterials": [],
  "issues": []
}
```

The example shortens tag-backed `itemIds` arrays for readability. Live results include every
resolved member as required by the recipe contract.

Steps are dependency-first and use stable one-based sequence numbers. `executions` accounts for
recipe output batches; `resultCount` is the total produced by those executions, including surplus
that can satisfy a later branch. Ingredient allocations include current inventory, planned
intermediate output, and raw materials listed for acquisition. A step-level `missing` count is
therefore reserved for a branch that could not be resolved safely; unavailable raw leaves instead
appear in the top-level `missingMaterials` list.

`craftable` is true only when the current inventory can complete the plan without acquiring a raw
material and no safety issue stopped a branch. A target already present may be satisfied directly,
with no steps. A target or ingredient with no recipe becomes a deterministic exact-item raw
shortage.

All selected branches share one inventory and crafted-surplus ledger, so an item consumed by one
branch cannot satisfy another. Recipe variants and ingredient alternatives are compared locally by
fewest safety issues, unresolved units, raw shortages, steps, then canonical order. This predictable
strategy is not exhaustive global optimization.

Cycle and limit termination is structured rather than exceptional:

```json
{
  "type": "CYCLE",
  "itemId": "example:a",
  "required": 1,
  "path": ["example:a", "example:b", "example:a"]
}
```

`type` is `CYCLE`, `MAX_DEPTH`, or `PLAN_LIMIT`. Cycle detection uses only the active dependency
path, so the same item can validly appear in separate branches. The production maximum depth is 32;
step, explored-branch, and scaled-quantity ceilings provide additional bounds. A non-cyclic variant
is preferred over a cyclic one under the deterministic selection order.

The planner does not inspect equipment or nearby storage, model crafting stations/fuel, perform
automatic crafting, or mutate the game. It remains unaware of recipe-viewer APIs; when the optional
JEI integration is active, supported JEI recipes arrive through the same `RecipeProvider` contract
as vanilla recipes. REI and EMI are not integrated.

## `minecraft.search_items`

Purpose: resolve user/model-friendly item queries into canonical registry IDs.

Input:

```json
{
  "query": "diamond pick",
  "limit": 10
}
```

Example result:

```json
{
  "query": "diamond pick",
  "limit": 10,
  "truncated": false,
  "items": [
    {
      "itemId": "minecraft:diamond_pickaxe",
      "displayName": "Diamond Pickaxe"
    }
  ]
}
```

V1 search can be simple case-insensitive matching over registry IDs and display names. No embeddings/fuzzy-search dependency is required.

## `minecraft.get_capabilities`

Purpose: let clients understand the Thread installation without assuming features.

Input: none.

Example result:

```json
{
  "threadVersion": "0.1.0",
  "readOnly": true,
  "tools": [
    "minecraft.get_status",
    "minecraft.get_game_info",
    "minecraft.get_player",
    "minecraft.get_inventory"
  ],
  "integrations": [
    {
      "id": "vanilla",
      "version": "1",
      "metadata": [
        {"key": "thread.tool_count", "value": "13"}
      ]
    }
  ]
}
```

Derive this from actual registrations/configuration rather than maintaining a second hard-coded
list. Only active integrations appear. Each integration includes a stable ID, its integration
contract version, and up to 32 sorted metadata entries with bounded keys and values.

Registry-owned `thread.*` entries describe actual contributions and, for optional candidates, the
target mod/version requirement that passed discovery. Integration-owned metadata uses its own
namespace. Disabled, absent, incompatible, or failed integrations never appear as active
capabilities and their classes are not resolved before presence/compatibility checks.

With supported JEI active, this list includes integration ID `jei`, contract version `1.0.0`,
target-mod/version metadata, recipe-provider contribution metadata, and the adapter's safe recipe
model/unsupported policy. The tool list remains the same thirteen `minecraft.*` tools.

## Tool descriptions

Descriptions are part of the product. They should tell an LLM when to use the tool and important bounds.

Good:

> Returns a snapshot of the local Minecraft player's current inventory using canonical item registry IDs. Use this when the answer depends on what the player actually possesses.

Bad:

> Gets inventory.
