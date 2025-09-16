# VetroForge Factions Plugin

VetroForge is a factions oriented gameplay plugin. This fork ships a configurable skill system, hologram utilities, scoreboards and loot drop helpers. The sections below highlight the most important parts for setting it up on a Paper or Spigot server.

## Getting started

1. Drop the compiled plugin JAR into your server's `plugins/` directory.
2. Start the server once so the default configuration files (`config.yml`, `skills.yml`) are generated.
3. Review the configuration options inside `plugins/VetroForge/config.yml`.
4. Configure hologram anchor points with `/holo set <TopFactions|TopCrystals|TopKills|TopPrestige|Warzone>`.
5. Reload holograms with `/holo respawn` after updating the data or adjusting the positions.

## Permissions & commands

| Command | Description | Permission | Default |
| ------- | ----------- | ---------- | ------- |
| `/lootdrop at <x> <y> <z> [world]` | Spawns a loot drop marker at the supplied coordinates. | `vetroforge.command.lootdrop` | OP |
| `/holo set <type>` | Stores the player position for the requested hologram. | `vetroforge.command.holo` | OP |
| `/holo remove <type>` | Removes the stored hologram anchor. | `vetroforge.command.holo` | OP |
| `/holo respawn [type]` | Respawns all or a single hologram. | `vetroforge.command.holo` | OP |
| `/skill [player]` | Displays skill progress for yourself or another player. | `vetroforge.command.skill` | Everyone |

## Configuration quick reference

### Skills (`skills.*`)

* `autoLevelAbilities` – automatically unlocks ability entries when the required level is reached.
* `bossBarSeconds` – duration of the XP progress bar after gaining XP.
* `xpCurve.base` & `xpCurve.multiplier` – controls the XP required per level.
* `disabledWorlds`, `disabledSkills`, `disabledAbilities` – simple toggles to restrict the skill system in certain worlds or for certain abilities.
* `stageRequirements` / `abilityRequirements` – stage/ability unlock levels per skill type.
* `xpRewards` – XP values that the bundled listeners will award for different events.

### Holograms (`holo.*`)

* `refreshSeconds` – update interval for live holograms such as the warzone leaderboard.
* `warzoneTitle`, `warzoneLines` – default text lines when no live data is available.
* `positions` – automatically managed section storing hologram anchors set via `/holo set`.

### Warzone (`warzone.*`)

Basic layout used by other systems when computing distances:

```yaml
world: world
center:
  x: 0.0
  y: 64.0
  z: 0.0
radius: 150
```

### Loot drop (`lootdrop.*`)

* `doubleWeekend` – toggles double loot for weekend events.
* `lifetime` – amount of seconds a loot drop will remain before despawning.
* `actionbarRadius` – players within this range receive action bar updates.

## Shutdown & housekeeping

The plugin cleans up holograms, boss bars and scheduled tasks on shutdown. Player skill data is persisted in `skills.yml` on disconnect and server stop.
