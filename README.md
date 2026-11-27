# Sheeper

Sheeper adds explosive sheep, elemental boss fights, and new uses for wool and mutton to Minecraft 1.19.2 with Forge.

## Download

Get the mod JAR from [GitHub Releases](https://github.com/codemorra/sheeper/releases) and place it in your Minecraft installation's `mods` folder.

## Sheeper

- Sheeper can replace naturally spawning sheep. You can also use a spawn egg or `/summon sheeper:sheeper`.
- Breed them with wheat. Mixed pairs of sheep and Sheeper can produce either kind.
- Adults approach when you look away. A bleat warns you before they explode. Look back or move away to stop the fuse.
- Hit an adult and it will pursue you, even while watched, and use a close-range burst.
- Babies approach even while watched, bump players, and explode when hurt.
- Shear adults for explosive wool. Adults that detonate themselves leave no wool or meat.

## Elemental bosses

| Boss | Summon command |
| --- | --- |
| Lava | `/summon sheeper:lava_sheeper` |
| Water | `/summon sheeper:water_sheeper` |
| Lightning | `/summon sheeper:lightning_sheeper` |

Each boss has an elemental appearance and its own health bar, size, and health settings.

### Shared arena rules

Water and Lightning both build a local arena. An opening wave pushes players outward without dealing damage. The sides slow players crossing in either direction, and the arena recedes after defeat.

- Combat begins after crossing the inner edge. Leaving the arena ends participation.
- Indirect attacks use their owner's current arena permission. Creative players can hurt the boss without joining the fight.
- Maximum health scales with peak simultaneous participation while preserving the boss's remaining health percentage.
- With recovery enabled, an empty arena lets the boss heal after a grace period. Full healing resets the fight.
- Both bosses can dodge incoming projectiles when they have time and room to react.

### Water Sheeper

- Swim in a virtual underwater arena that leaves terrain intact. Enter from above or from the sides, and leave through the sides.
- Air drains more slowly than in ordinary water. Bubble fountains restore air and give a gentle lift.
- The boss circles players, fires water projectiles, and pushes players who stay too close. It uses bubble form to dodge or slip through narrow openings.
- A warned water beam tracks its target. Staggered mines burst and send out pushing waves.
- **Maelstrom** pulls swimmers inward, then sends a damaging wave outward. The outer rim offers safety, and the boss is vulnerable after impact.

### Lightning Sheeper

- Rain, wind, and thunder fill a storm arena. Warned lightning strikes can electrify connected water and ignite leaves.
- The boss moves and dodges in lightning form. Melee hits while it crackles or charges can shock the attacker.
- Lightning rows, expanding ground rings, roaming tornadoes, and tracking fans force players to keep moving.
- **Lightning Rod** electrifies the ground during its final barrage. Glowing spheres intercept lightning and shelter players until the charge fades.

## Explosive wool and carpets

- Shears safely harvest placed Sheeper wool and carpets. Other tools can trigger a blast and destroy the block.
- Fire, redstone, and explosions can prime Sheeper wool. Primed wool can set off nearby TNT and other wool.
- Grounded creatures trigger explosive carpets. Sneaking lets players cross safely.

## Sheeper mutton

- Adult Sheeper can drop raw mutton. Cook it in a smoker. Fire, furnaces, and campfires can make it explode.
- Eating it raw causes damage and knockback. Cooked meat grants speed while still dealing periodic damage.
- Both effects can be lethal. Milk clears them.

## Configuration

| File | What it controls |
| --- | --- |
| `config/sheeper-common.toml` | Regular Sheeper, spawning, breeding, wool, carpets, and mutton |
| `config/sheeper/lava.toml` | Lava boss size and health |
| `config/sheeper/water.toml`, `config/sheeper/lightning.toml` | Boss settings, arena rules, attacks, and their enable switches |
| `config/sheeper-client.toml` | Local fog, visuals, particles, and sound volumes |

In multiplayer, the server controls gameplay settings and each client controls its presentation. Durations use seconds and damage uses health points.

## Development

Use JDK 17 and the included Gradle wrapper.

| Command | Purpose |
| --- | --- |
| `./gradlew build` | Build the mod JAR in `build/libs/` |
| `./gradlew verifyMod` | Build, validate resources, and run the GameTests |
| `./gradlew runClient` | Launch the development client |

GameTests use a fresh test world. Results are written to `build/reports/gametest/results.xml`.

## License

See [LICENSE](LICENSE) for the MIT license and [FORGE_LICENSE.txt](FORGE_LICENSE.txt) for the Forge license and third-party notices.
