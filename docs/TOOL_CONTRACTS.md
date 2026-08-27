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
  "loader": "<fabric-or-neoforge>",
  "loaderVersion": "<version>",
  "threadVersion": "0.1.0"
}
```

Session/world availability belongs to `minecraft.get_status`; player/world-specific details belong to their dedicated tools. `minecraft.get_game_info` should remain useful from the main menu.

## `minecraft.get_advancements`

Purpose: return a bounded, deterministic list of vanilla advancements Minecraft currently exposes
as visible/known to the local player, with live integrated-server progress.

Input:

```json
{
  "filter": "INCOMPLETE",
  "search": "stone",
  "limit": 64
}
```

`filter` defaults to `ALL` and accepts `ALL`, `COMPLETED`, or `INCOMPLETE`. `search` is optional.
A valid registry ID matches exactly; other text uses case-insensitive all-term matching across the
ID, title, description, and tab title. `limit` defaults to 64 and has a hard maximum of 128.

Example result:

```json
{
  "filter": "INCOMPLETE",
  "search": "stone",
  "limit": 64,
  "knownCount": 42,
  "scannedCount": 42,
  "matchedCount": 1,
  "sourceTruncated": false,
  "truncated": false,
  "advancements": [
    {
      "advancementId": "minecraft:story/mine_stone",
      "title": "Stone Age",
      "description": "Mine Stone with your new Pickaxe",
      "completed": false,
      "completionPercentage": 0.0,
      "completedCriteria": 0,
      "totalCriteria": 1,
      "completedRequirements": 0,
      "totalRequirements": 1,
      "parentAdvancementId": "minecraft:story/root",
      "tabAdvancementId": "minecraft:story/root",
      "tabTitle": "Minecraft",
      "displayType": "TASK",
      "hidden": false,
      "firstProgressAt": null,
      "completedAt": null
    }
  ]
}
```

Results are ordered by advancement registry ID. `knownCount` is the client-visible/known count;
`scannedCount` is the bounded provider count. `sourceTruncated` reports provider scan truncation,
while `truncated` also becomes true when the requested result limit omits matches. The list omits
individual criteria; use `minecraft.get_advancement` for one detailed record.

## `minecraft.get_advancement`

Purpose: return detailed live progress for one exact vanilla advancement ID already visible/known
to the local player.

Input:

```json
{
  "advancementId": "minecraft:story/mine_stone"
}
```

Example result:

```json
{
  "advancementId": "minecraft:story/mine_stone",
  "title": "Stone Age",
  "description": "Mine Stone with your new Pickaxe",
  "completed": false,
  "completionPercentage": 0.0,
  "completedCriteria": 0,
  "totalCriteria": 1,
  "completedRequirements": 0,
  "totalRequirements": 1,
  "criteriaTruncated": false,
  "parentAdvancementId": "minecraft:story/root",
  "tabAdvancementId": "minecraft:story/root",
  "tabTitle": "Minecraft",
  "displayType": "TASK",
  "hidden": false,
  "firstProgressAt": null,
  "completedAt": null,
  "criteria": [
    {
      "name": "mine_stone",
      "completed": false,
      "obtainedAt": null
    }
  ]
}
```

Criteria are ordered by name and bounded to 2,048 entries. `completionPercentage` is based on
Minecraft's requirement groups rather than a naive criterion ratio. `completedAt` is the time the
last required group became satisfied when that can be derived from criterion timestamps. Missing
display metadata is represented as `null`. An unknown or not-yet-exposed advancement returns
`NOT_FOUND`; Thread does not enumerate the integrated server's full registry to fabricate hidden
progress.

Both tools require a supported integrated single-player session, are read-only, and never award
criteria, complete advancements, claim rewards, run commands, or mutate player state. These are
vanilla advancement contracts, not a generic quest/progression API and not FTB Quests support.

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

## `minecraft.get_world_info`

Purpose: return one compact structured snapshot of the supported single-player world and local
environment around the player.

Input: none.

Example result:

```json
{
  "dimensionId": "minecraft:overworld",
  "biomeId": "minecraft:plains",
  "biomeName": "Plains",
  "playerPosition": {"x": 152.2, "y": 67.0, "z": -381.7},
  "worldSpawnDimensionId": "minecraft:overworld",
  "worldSpawnPosition": {"x": 0, "y": 64, "z": 0},
  "distanceFromSpawn": 411.2,
  "difficulty": "normal",
  "hardcore": false,
  "gameTimeTicks": 846200,
  "dayTimeTicks": 301000,
  "worldDay": 12,
  "timeOfDayTicks": 13000,
  "daylightState": "NIGHT",
  "raining": false,
  "thundering": false,
  "localLightLevel": 4,
  "moonPhase": "new_moon",
  "biomeTemperature": 0.8,
  "biomeHasPrecipitation": true
}
```

Canonical IDs come from the live dimension and biome registries. `biomeName` is the current client
language translation when one exists and is otherwise `null`; clients must use `biomeId` as the
stable key. `playerPosition` intentionally overlaps `minecraft.get_player` so this snapshot remains
self-contained.

`worldSpawnPosition` is Minecraft's global world spawn, and `worldSpawnDimensionId` identifies its
dimension. `distanceFromSpawn` is straight-line Euclidean distance in blocks from the player to the
spawn block center. It is `null` when player and spawn are in different dimensions because those
coordinates have no direct distance. Reading spawn never loads its chunk.

`difficulty` is `peaceful`, `easy`, `normal`, or `hard`; `hardcore` is the save's independent native
flag. `gameTimeTicks` is total current-level age. `dayTimeTicks` is the total overworld clock value
affected by normal time progression, sleep, and time commands. `worldDay` is its zero-based
24,000-tick day, while `timeOfDayTicks` is the value from 0 through 23,999 within that day.
`daylightState` is `DAY` before tick 13,000 and from tick 23,000 onward, `NIGHT` otherwise, or
`FIXED` in a fixed-time dimension.

`raining` and `thundering` report Minecraft's current global weather state for the level; they do
not predict future weather or claim precipitation is visibly reaching a player under cover.
`localLightLevel` is Minecraft's combined local raw brightness from 0 through 15 at the player's
block position. `moonPhase` is Minecraft's native lower-case phase value.
`biomeTemperature` is the biome's exposed base temperature, and `biomeHasPrecipitation` reports
whether that biome supports precipitation. Minecraft 26.2 does not expose downfall through a clean
public API, so Thread does not reflect into private climate data or fabricate a downfall value.

The provider resolves the authoritative integrated-server player, checks that the local chunk is
already loaded, and performs no commands, mutation, prediction, or chunk loading. Menu/no-world and
multiplayer calls are rejected by the centralized gameplay guard.

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
    "state": {
      "kind": "furnace",
      "cookingProgress": "0",
      "cookingTotalTime": "200",
      "litTimeRemaining": "0",
      "litTotalTime": "1600"
    }
  }
}
```

The provider reads the block and its block entity from loaded integrated-server state.
Vanilla furnaces expose named `input`, `fuel`, and `output` positions; other vanilla containers use
numeric positions. Empty positions are omitted and at most 64 item positions are inspected. An
unopened loot container reports its loot-table identity without resolving or reading contents.
Unknown block entities still report identity without raw NBT. Loader integrations can register a
higher-priority safe inspector for richer mod-specific state later.

When the normal client raycast has no valid block target, Thread returns a structured `NOT_FOUND`
tool error rather than inventing block data.

## `minecraft.get_nearby_containers`

Purpose: locate container block entities near the local player without returning every complete
inventory.

Input:

```json
{
  "radius": 12,
  "limit": 16
}
```

The hard container radius is 16 blocks and the hard result limit is 64. A lower configured entity
radius/result limit also lowers the corresponding container maximum. Search visits a finite sphere
of block positions ordered by block-center distance, skips positions whose chunks are not already
loaded, and stops after finding `limit + 1` containers so truncation is known without serializing an
unbounded result.

Example result:

```json
{
  "radius": 12,
  "limit": 16,
  "truncated": false,
  "containers": [
    {
      "blockId": "minecraft:furnace",
      "containerTypeId": "minecraft:furnace",
      "displayName": "Furnace",
      "position": {"x": 153, "y": 67, "z": -379},
      "distance": 3.4,
      "slotCount": 3,
      "usedSlotCount": 1,
      "itemSummary": [
        {
          "slot": "fuel",
          "item": {
            "itemId": "minecraft:coal",
            "displayName": "Coal",
            "customName": null,
            "count": 1,
            "maxCount": 64,
            "durability": null,
            "enchantments": [],
            "components": null
          }
        }
      ],
      "itemSummaryTruncated": false
    }
  ]
}
```

`itemSummary` contains at most four representative occupied slots. `usedSlotCount` is null when
contents are deliberately unresolved or the inspector could not count every slot safely. Use
`minecraft.inspect_container` for one full visible inventory rather than treating this summary as a
complete item list.

Base Thread recognizes vanilla `Container` block entities, including chest, trapped chest, barrel,
furnace, smoker, blast furnace, hopper, brewing stand, dispenser, and dropper. A future separate
Thread Integration may recognize a custom machine through the same block-entity inspector registry.

## `minecraft.inspect_container`

Purpose: inspect one known nearby loaded container position with full safe visible contents and
selected machine state.

Input:

```json
{
  "position": {"x": 153, "y": 67, "z": -379}
}
```

The result reuses the `BlockInfo`/`BlockEntityInfo` shape shown for
`minecraft.get_target_block`. The requested block can be anywhere within the hard 16-block
container range; it does not need to be the current camera target. All non-empty visible slots are
returned up to the shared 64-slot block-entity ceiling. Furnaces use named `input`, `fuel`, and
`output` slots plus cooking/burn progress counters. Brewing stands use named bottle, ingredient,
and fuel slots plus brew/fuel counters. Other vanilla containers use numeric slot names.

An out-of-range position returns `OUT_OF_RANGE`. A position in an unloaded chunk returns retryable
`NOT_AVAILABLE`; Thread never loads the chunk to answer. A loaded non-container position returns
`NOT_FOUND`. Menu/no-world and multiplayer calls are rejected by the same centralized session guard
as every other gameplay tool.

Unopened loot containers return their type, slot count, and unresolved-loot metadata without
reading slots or resolving the loot table. Raw NBT, component maps, and Minecraft implementation
objects never enter the result. This tool is read-only and does not move items. Neither container
tool contributes its contents to direct craftability or recursive crafting plans.

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
Classification is nullable. Thread assigns it to Minecraft's hostile enemy, passive animal/ambient
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

Purpose: determine whether the item sources selected by `scope` satisfy at least one live recipe
variant for the requested item. It assesses one recipe execution and performs no crafting action.

Input:

```json
{
  "itemId": "minecraft:diamond_pickaxe",
  "scope": "PLAYER_ONLY"
}
```

`scope` is optional. `PLAYER_ONLY` is the default and reads only the player's 36-slot main
inventory. `PLAYER_AND_NEARBY` explicitly adds eligible containers from one bounded scan of already
loaded chunks. Equipment is not crafting supply in either scope.

Example result:

```json
{
  "itemId": "minecraft:diamond_pickaxe",
  "scope": "PLAYER_ONLY",
  "sourceStatus": {
    "complete": true,
    "nearbyRadius": null,
    "nearbyContainerLimit": null,
    "nearbyContainersTruncated": false,
    "unresolvedContainersSkipped": 0,
    "contentLimitedContainersSkipped": 0
  },
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
            {
              "itemId": "minecraft:diamond",
              "count": 2,
              "sourceAllocations": [
                {
                  "sourceType": "PLAYER_INVENTORY",
                  "count": 2,
                  "inventorySlots": [9],
                  "equipmentSlots": [],
                  "containerSlots": [],
                  "containerPosition": null,
                  "containerTypeId": null,
                  "distance": null
                }
              ]
            }
          ]
        },
        {
          "itemIds": ["minecraft:stick"],
          "tagIds": [],
          "required": 2,
          "available": 2,
          "missing": 0,
          "allocations": [
            {
              "itemId": "minecraft:stick",
              "count": 2,
              "sourceAllocations": [
                {
                  "sourceType": "PLAYER_INVENTORY",
                  "count": 2,
                  "inventorySlots": [10],
                  "equipmentSlots": [],
                  "containerSlots": [],
                  "containerPosition": null,
                  "containerTypeId": null,
                  "distance": null
                }
              ]
            }
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
ingredient may also claim. `allocations` shows the deterministic item choices made from
alternatives, and each `sourceAllocations` list identifies the live player slots or specific
containers that supplied those units. Player sources are consumed first, then nearby containers in
distance/position/type order. The allocation maximizes satisfied requirements while consuming each
item unit at most once. Identical ingredient groups are merged before assessment, even if a
provider supplies duplicates. A supported lookup with no matching recipe returns `craftable: false`
and an empty `recipes` array.

For expanded scope, `sourceStatus` reports the applied radius/container bounds. `complete: false`
means the answer describes the observed safe snapshot but may be conservative because the bounded
scan truncated, unopened loot was excluded, or a container's contents exceeded the safe content
representation. Thread never guesses those contents or loads a chunk to complete the answer.

## `minecraft.get_missing_ingredients`

Purpose: explain shortages for every live recipe variant using the same one-snapshot scoped
assessment as `minecraft.can_craft`.

Input and result use the same contract as `minecraft.can_craft`. Each ingredient remains present so
clients can explain the complete requirement; entries with `missing` greater than zero are the
unmet requirements. This prevents a missing-only projection from hiding how alternative items were
allocated across overlapping ingredient groups.

Both tools support shaped and shapeless recipes represented by the recipe provider. They never
inspect equipment, crafting stations, or fuel. Nearby storage is read only when the request
explicitly selects `PLAYER_AND_NEARBY`.

## `minecraft.get_crafting_plan`

Purpose: recursively explain the intermediate crafts and final raw materials needed for one target
item from the item sources selected by `scope`. The tool is read-only and performs no crafting
action.

Input uses the same canonical `itemId` contract as recipe lookup:

```json
{
  "itemId": "minecraft:crafting_table",
  "scope": "PLAYER_ONLY"
}
```

`scope` has the same optional enum and `PLAYER_ONLY` default as direct craftability.

Representative result when the inventory contains one oak log:

```json
{
  "itemId": "minecraft:crafting_table",
  "scope": "PLAYER_ONLY",
  "sourceStatus": {
    "complete": true,
    "nearbyRadius": null,
    "nearbyContainerLimit": null,
    "nearbyContainersTruncated": false,
    "unresolvedContainersSkipped": 0,
    "contentLimitedContainersSkipped": 0
  },
  "requested": 1,
  "satisfiedFromInventory": 0,
  "satisfiedFromSources": [],
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
          "allocations": [
            {
              "itemId": "minecraft:oak_log",
              "count": 1,
              "sourceAllocations": [
                {
                  "sourceType": "PLAYER_INVENTORY",
                  "count": 1,
                  "inventorySlots": [9],
                  "equipmentSlots": [],
                  "containerSlots": [],
                  "containerPosition": null,
                  "containerTypeId": null,
                  "distance": null
                }
              ]
            }
          ]
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
          "allocations": [
            {"itemId": "minecraft:oak_planks", "count": 4, "sourceAllocations": []}
          ]
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

`satisfiedFromInventory` is retained as the main-inventory portion of a target already present.
`satisfiedFromSources` locates every live source that directly satisfied that target. Step
allocations likewise locate live inputs; a planned intermediate output or hypothetical raw
acquisition has an empty `sourceAllocations` list because it has no live slot or container location.

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

The planner does not inspect equipment, model crafting stations/fuel, perform automatic crafting,
move items, or mutate the game. Nearby storage is eligible only under explicit
`PLAYER_AND_NEARBY`, with the same `sourceStatus` caveat as direct assessment. It remains unaware of
recipe-viewer APIs. Base Thread reads Minecraft's final live recipe manager, including active
vanilla, datapack, and installed-mod additions, replacements, and removals; it does not use a static
vanilla recipe list. A future separate Thread Integrations package may contribute detached recipes
or item sources through explicit integration wiring without changing the planner.

## `minecraft.find_item`

Purpose: find matching items across the player's live inventory, offhand/armor, and nearby loaded
containers without moving items or implicitly changing crafting inputs.

Input:

```json
{
  "query": "minecraft:coal",
  "radius": 12,
  "containerLimit": 16,
  "itemLimit": 16
}
```

An input that matches the canonical registry-ID syntax is exact: `minecraft:coal` does not match
`minecraft:coal_block`. Other input is a case-insensitive all-term match over registry ID, display
name, and custom name, consistent with `minecraft.search_items`.

Example result:

```json
{
  "query": "minecraft:coal",
  "radius": 12,
  "containerLimit": 16,
  "itemLimit": 16,
  "containersTruncated": false,
  "itemsTruncated": false,
  "matches": [
    {
      "item": {
        "itemId": "minecraft:coal",
        "displayName": "Coal"
      },
      "totalCount": 11,
      "sources": [
        {
          "sourceType": "PLAYER_INVENTORY",
          "count": 2,
          "inventorySlots": [7],
          "equipmentSlots": [],
          "containerSlots": [],
          "containerPosition": null,
          "containerTypeId": null,
          "distance": null
        },
        {
          "sourceType": "PLAYER_EQUIPMENT",
          "count": 1,
          "inventorySlots": [],
          "equipmentSlots": ["OFF_HAND"],
          "containerSlots": [],
          "containerPosition": null,
          "containerTypeId": null,
          "distance": null
        },
        {
          "sourceType": "NEARBY_CONTAINER",
          "count": 8,
          "inventorySlots": [],
          "equipmentSlots": [],
          "containerSlots": ["fuel", "input"],
          "containerPosition": {"x": 153, "y": 67, "z": -379},
          "containerTypeId": "minecraft:furnace",
          "distance": 3.4
        }
      ]
    }
  ]
}
```

Distinct item matches are ordered by canonical item ID. Within a match, player inventory comes
first, followed by offhand/armor, then containers ordered by distance, position, and type. Slots in
one source are sorted and duplicate stacks are summed. Main hand is not counted through equipment
because it is already the selected hotbar stack in the 36-slot inventory snapshot.

The hard container radius is 16, the hard container result limit is 64, and the hard distinct-item
limit is 64. Configured lower limits still apply. Nearby scanning uses the same loaded-chunk-only,
distance-ordered provider path as `minecraft.get_nearby_containers`; it never force-loads chunks.
Unopened loot and any container whose full contents cannot be represented safely are excluded from
item totals rather than guessed. `containersTruncated` and `itemsTruncated` tell the client when the
requested bounds omitted additional candidates.

This tool is live lookup context only; crafting never consumes its search result. The three
crafting tools independently build one source snapshot per invocation: they read only the player's
36-slot main inventory by default and add eligible nearby containers only for explicit
`PLAYER_AND_NEARBY` requests.

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

Canonical registry-ID input is matched exactly. Other input uses case-insensitive all-term matching
over registry IDs and display names. No embeddings or fuzzy-search dependency is required. Unlike
`minecraft.find_item`, this tool searches the live item registry and does not report owned stacks or
locations.

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
    "minecraft.get_world_info",
    "minecraft.get_inventory"
  ],
  "integrations": [
    {
      "id": "vanilla",
      "version": "1",
      "metadata": [
        {"key": "thread.tool_count", "value": "19"}
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

The base artifact reports only the required `vanilla` integration. Future separately installed
Thread Integrations packages may add their own stable IDs and bounded contribution metadata without
changing the nineteen built-in `minecraft.*` tool contracts.

## Tool descriptions

Descriptions are part of the product. They should tell an LLM when to use the tool and important bounds.

Good:

> Returns a snapshot of the local Minecraft player's current inventory using canonical item registry IDs. Use this when the answer depends on what the player actually possesses.

Bad:

> Gets inventory.
