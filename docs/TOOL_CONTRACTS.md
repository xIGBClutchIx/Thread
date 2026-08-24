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

## `minecraft.get_inventory`

Purpose: return the local player's inventory snapshot.

Input: none.

Example result:

```json
{
  "selectedHotbarSlot": 0,
  "slots": [
    {
      "slot": 0,
      "itemId": "minecraft:diamond_pickaxe",
      "count": 1,
      "maxCount": 1,
      "displayName": "Diamond Pickaxe"
    }
  ]
}
```

Empty slots may be omitted or represented explicitly. Pick one convention and test it.

## `minecraft.get_equipment`

Purpose: return held and equipped items.

Input: none.

Example result:

```json
{
  "mainHand": {"itemId": "minecraft:diamond_pickaxe", "count": 1},
  "offHand": null,
  "head": {"itemId": "minecraft:iron_helmet", "count": 1},
  "chest": null,
  "legs": null,
  "feet": null
}
```

## `minecraft.get_target_block`

Purpose: inspect the block the player is currently looking at within normal interaction/raycast range.

Input: optional bounded distance only if needed by implementation. Prefer game-standard targeting behavior.

Example result:

```json
{
  "blockId": "minecraft:blast_furnace",
  "position": {"x": 153, "y": 67, "z": -379},
  "properties": {
    "facing": "north",
    "lit": "false"
  },
  "distance": 3.4
}
```

V1 does not need to dump block entity inventories or NBT.

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
      "distance": 8.4,
      "position": {"x": 160.0, "y": 67.0, "z": -380.0}
    }
  ]
}
```

Do not return full entity NBT in V1.

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
        {"items": ["minecraft:diamond"], "count": 3},
        {"items": ["minecraft:stick"], "count": 2}
      ]
    }
  ]
}
```

Minecraft recipe ingredients can represent alternatives/tags. The DTO must not falsely collapse alternatives into a single required item. If exact grouped counts are not always representable, preserve ingredient slots/groups instead of inventing certainty.

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
    {"id": "vanilla", "version": "1"}
  ]
}
```

Derive this from actual registrations/configuration rather than maintaining a second hard-coded list.

## Tool descriptions

Descriptions are part of the product. They should tell an LLM when to use the tool and important bounds.

Good:

> Returns a snapshot of the local Minecraft player's current inventory using canonical item registry IDs. Use this when the answer depends on what the player actually possesses.

Bad:

> Gets inventory.
