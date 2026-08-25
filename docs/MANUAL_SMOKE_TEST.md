# Thread V1 Manual MCP Smoke Test

Run this checklist against the installable release JAR, not Gradle development class directories.
Use an MCP-capable client that exposes tool-call traces and retain the client trace plus Minecraft
log with the release evidence.

## Setup

1. Start a clean Minecraft 26.2 Fabric client containing Fabric API and the Thread release JAR.
2. Start at the main menu and connect the MCP client to `http://127.0.0.1:25580/mcp`.
3. Confirm discovery lists twelve tools and every tool has `readOnly: true` annotations.
4. Prepare a survival world with a known block in view, at least one known entity within 16 blocks,
   a diamond pickaxe equipped, and exactly three diamonds plus two sticks in inventory.

## Required scenarios

| # | Prompt or action | Expected Thread evidence |
| --- | --- | --- |
| 1 | Ask whether Minecraft is in a world while still at the main menu. | `minecraft.get_status` reports `MAIN_MENU`, no world/player, and unsupported reason `NO_WORLD`. |
| 2 | Enter the prepared single-player world and ask whether it is ready. | `minecraft.get_status` reports `SINGLEPLAYER`, world/player available, and `supported: true`. |
| 3 | Ask which Minecraft version is running. | `minecraft.get_game_info` reports Minecraft 26.2, Fabric, and Thread 0.1.0. |
| 4 | Ask for current health and hunger. | `minecraft.get_player` returns live `health`, `maxHealth`, `food`, and saturation values. |
| 5 | Ask what is in the inventory. | `minecraft.get_inventory` includes the known canonical item IDs and exact counts. |
| 6 | Ask what is held and equipped. | `minecraft.get_equipment` reports the diamond pickaxe and prepared armor slots. |
| 7 | Look at the known block and ask what it is. | `minecraft.get_target_block` returns its canonical block ID, position, properties, and distance. |
| 8 | Ask what entities are within 16 blocks. | `minecraft.get_nearby_entities` is called with radius 16 and reports the known loaded entity within the bounded result. |
| 9 | Ask how to craft a diamond pickaxe. | `minecraft.get_recipe` returns a recipe requiring three diamonds and two sticks. |
| 10 | Ask whether the held inventory has the materials for that recipe. | `minecraft.can_craft` reports `craftable: true` and identifies at least one craftable recipe variant. |
| 11 | Remove one diamond and ask what is missing. | `minecraft.get_missing_ingredients` reports the affected variant with diamonds `required: 3`, `available: 2`, and `missing: 1`. |

## Safety observations

- Repeat a gameplay prompt in multiplayer and confirm a structured `UNSUPPORTED` error is
  returned before player/world state is exposed.
- During the nearby-entity query, confirm no chunks are loaded solely to satisfy the request.
- Confirm no prompt or tool changes inventory, blocks, entities, player movement, or game rules.
- Shut down Minecraft and confirm the log contains `Thread MCP listener stopped`.

Record the Thread version, JAR checksum, Minecraft/Fabric versions, MCP client name/version, date,
and pass/fail result for each scenario.
