# God Spear

God Spear is an owner-bound progression weapon for Paper, Purpur, Folia, and compatible Paper forks. This build targets Minecraft Java **1.21.11** and uses no NMS. Newer releases are allowed to start with a prominent console warning when they have not been listed under `tested-minecraft`.

## Requirements and installation

- Java 21 or newer
- Paper/Purpur/Folia 1.21.11+
- MySQL 8+, MariaDB 10.6+, or the bundled SQLite storage

Build with `mvn clean package`, then copy `target/GodSpear-1.0.0.jar` into the server's `plugins` directory and restart the server. Do not use Bukkit's `/reload`; use `/godspear reload` for configuration changes.

## Behavior

On first load, an eligible player receives one PDC-backed wooden spear. A full inventory queues delivery without dropping an item. Each valid player kill with the exact registered spear advances one stage: Wood, Stone, Iron, Diamond, Netherite, God. God remains the terminal stage while kill statistics continue.

The item stores its spear UUID, owner UUID/name, stage, kills, creation timestamp, and plugin version in PDC. The database is authoritative. Display name and lore are never trusted for identity. Every stage is unbreakable. God applies Sharpness VII and Lunge V while preserving existing valid enchantments.

Container transfers, dropping, throwing, foreign pickup, shift-click, drag, double-click, hotbar/offhand exploits, creative manipulation, and death drops are guarded. On death the exact item is retained and restored. Join validation removes duplicate or foreign PDC items and repairs the registered item from the database.

Only a direct, uncancelled, fatal `EntityDamageByEntityEvent` from the owning player with their exact registered spear is considered. Projectiles, indirect damage, environmental damage, pets, mobs, fake/offline players, Citizens NPCs, and mismatched PDC are rejected. Anti-farming validation runs through asynchronous database queries.

## Configuration

- `config.yml`: progression, binding, tested versions, anti-farming, God enchant levels, logging
- `messages.yml`: user-facing text
- `storage.yml`: SQLite/MySQL/MariaDB connection settings
- `effects.yml`: visual effect settings
- `sounds.yml`: sound settings

For MariaDB, set `type: mariadb`; for MySQL use `type: mysql`. Set credentials under `mysql`. SQLite uses `plugins/GodSpear/godspear.db`. Database work uses a dedicated bounded executor and HikariCP; Bukkit objects are only touched on the player's entity scheduler.

## Commands

| Command | Purpose |
|---|---|
| `/godspear help` | Show help |
| `/godspear info [player]` | Show authoritative spear data |
| `/godspear destroy` | Begin owner destruction confirmation |
| `/godspear destroy confirm` | Permanently delete active spear and create a no-regeneration tombstone |
| `/godspear give <player>` | Explicitly create a new unique spear |
| `/godspear recover <player>` | Reissue the registered spear after removing copies |
| `/godspear reset <player>` | Reset stage and kills while retaining UUID |
| `/godspear remove <player>` | Administratively remove and tombstone a spear |
| `/godspear setstage <player> <stage>` | Set stage |
| `/godspear setkills <player> <amount>` | Set kills |
| `/godspear reload` | Reload `config.yml` |
| `/godspear validate <player>` | Remove copies and repair authoritative PDC |
| `/godspear debug` | Show runtime compatibility data |

Administrative target commands currently require the target to be online, ensuring inventory mutation occurs safely on the correct Paper/Folia entity thread.

## Permissions

`godspear.use` and `godspear.info` default to all players. `godspear.admin`, `godspear.give`, `godspear.reset`, `godspear.remove`, `godspear.setstage`, `godspear.setkills`, `godspear.reload`, `godspear.validate`, and `godspear.debug` default to operators. `godspear.admin` inherits every administrative permission.

## Compatibility hooks

PlaceholderAPI, Citizens, WorldGuard, GriefPrevention, Towny, Lands, CombatLogX, and Multiverse are declared as soft dependencies. Citizens NPC metadata is respected directly. PvP protection plugins are respected through cancelled damage events; no hard dependency or NMS access is used.

## Operational notes

The `destroyed_owners` table is intentionally retained after an active spear row is deleted. It contains only the owner UUID, former spear UUID, and deletion time, and prevents the first-join path from recreating a destroyed spear. `/godspear give` is the explicit administrative override and clears that tombstone.

Absolute prevention of arbitrary third-party plugins deliberately rewriting inventories is not enforceable through the Bukkit API. God Spear repairs/removes inconsistencies at its validation boundaries and provides `/godspear validate` and `/godspear recover`; plugins that directly mutate inventory without firing events should integrate with those commands.

## Source layout

- `compat`: Paper/Folia scheduler abstraction
- `model`: immutable spear records and stages
- `service`: PDC item authority and inventory operations
- `storage`: asynchronous SQL persistence
- `listener`: PvP, lifecycle, death, and inventory guards
- `command`: player/admin command surface

## Verification

Run `mvn clean verify`. The build compiles against Paper 1.21.11, runs progression unit tests, and creates the shaded deployable JAR. Before a public launch, test the JAR on staging servers for each exact Paper/Purpur/Folia build and with every protection plugin used by that network.
