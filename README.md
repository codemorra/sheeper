# Sheeper

A Java learning project: a Minecraft mod for Minecraft 1.19.2 using Forge.

Adds an explosive sheep that approaches when you look away. Hear it bleat? Look back before it's too late!

## Download

Download the mod JAR matching your Minecraft version and mod loader from [GitHub Releases](https://github.com/codemorra/sheeper/releases).

Place the downloaded JAR in your Minecraft installation's `mods` folder. See the release notes for compatibility details and known limitations.

## Project Status

Each naturally spawning sheep has a configurable chance to be replaced by a Sheeper (5% by default), both during world generation and later spawning.

Sheeper can be bred with wheat, just like normal sheep. Two Sheeper always produce a baby Sheeper. A Sheeper and a normal sheep have a 50% chance of producing either type. Two normal sheep always produce a normal sheep. Offspring inherit wool colors using vanilla sheep breeding rules.

The Sheeper can also be summoned using `/summon sheeper:sheeper` or a spawn egg. It uses vanilla sheep models and textures.

While unobserved, adult Sheeper silently approach nearby Survival and Adventure players with glowing red eyes. Within fuse range, they stop, bleat, and start a countdown while swelling and turning red. Observation by a living Survival or Adventure player, or leaving fuse range, cancels and fully resets the countdown. Visual warning effects fade smoothly when cancelled.

Baby Sheeper move faster and approach randomly selected Survival and Adventure players, even while being watched. They bump players without dealing direct damage, retreat, and pause before choosing another target. Babies use the same detection radius as adults. Taking damage makes them explode immediately with a smaller explosion. Using a Sheeper spawn egg on a Sheeper creates a baby Sheeper. Babies grow into adults and lose their movement speed bonus.

Adult and baby Sheeper explosions respect the mobGriefing game rule.

Sheeper wool comes in all 16 vanilla colors. On ordinary death, unshorn adult Sheeper drop one matching wool block. Shearing by hand or dispenser yields 1–3 matching wool blocks. Sheeper no longer drop vanilla mutton.

Adult Sheeper also drop 1–2 raw Sheeper mutton on ordinary death, with additional drops from Looting. Sheared adults still drop meat; babies and Sheeper detonating themselves do not. Raw and cooked Sheeper mutton have an animated sparkle overlay over the vanilla or resource-pack meat texture.

Use a smoker to safely prepare Sheeper mutton. Heating it in a furnace or on either type of campfire starts a three-second warning before a small blast damages and knocks back nearby entities. One piece of meat is consumed; the appliance drops its normal loot and remaining contents. Removing the meat or extinguishing the appliance resets the warning. Surrounding blocks and dropped items survive the blast.

Dropped raw Sheeper mutton detonates on contact with fire, soul fire, lava, or a lit campfire, consuming the entire dropped stack. These small blasts preserve surrounding blocks and other dropped items. Cooked Sheeper mutton does not detonate on fire contact.

Eating raw Sheeper mutton restores the same hunger and saturation as vanilla raw mutton, but causes six seconds of internal bursts. Each second, the player takes half a heart of base damage that bypasses armor and receives a small push in a random horizontal direction, accompanied by a quiet blast sound. The damage can be lethal. Eating another piece refreshes the duration; drinking milk ends it. These bursts do not harm nearby entities or blocks. Creative players and spectators are unaffected.

Cooked Sheeper mutton restores the same hunger and saturation as vanilla cooked mutton and increases movement speed by 50% for ten seconds. The player takes half a heart of armor-bypassing base damage every two seconds with a quieter blast sound and no added knockback. This damage can also be lethal. Eating another cooked piece refreshes the duration without stacking the speed bonus. Raw and cooked countdowns run independently, and drinking milk ends both. Creative players and spectators are unaffected.

White Sheeper wool can be dyed using vanilla dyes. It cannot be used to craft vanilla wool products such as beds or carpets. Its animated sparkle overlay preserves the underlying vanilla or resource-pack wool texture.

Placed Sheeper wool acts as TNT when ignited, including redstone activation and chain reactions with TNT and other Sheeper wool. These explosions destroy surrounding blocks independently of mobGriefing.

Use shears to harvest it safely. Mining with any other tool or by hand causes a small immediate explosion that damages and knocks back entities without destroying surrounding blocks or dropping wool. Creative mining is safe and produces no drops.

Settings are stored in `config/sheeper-common.toml`. The detection radius, observation angle, fuse distance, fuse duration, adult explosion strength, and natural spawn chance are configurable. The effective fuse distance cannot exceed the detection radius. `naturalSpawnChance` is a percentage from 0 to 100; 0 disables natural replacement, while 100 replaces every naturally spawning sheep.

Baby movement speed, bump knockback, minimum and maximum attack pauses, and explosion strength are also configurable. Equal minimum and maximum pause values produce a fixed pause. If the maximum is below the minimum, the minimum is used for both.

## Development Environment

- Minecraft 1.19.2
- Minecraft Forge 43.5.0
- JDK 17
- Gradle via the included wrapper

## Build

Run from the project directory:

```bash
./gradlew build
```

The generated mod JAR is located in `build/libs/`.

## Run the Development Client

```bash
./gradlew runClient
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

The Forge license and accompanying third-party notices are included in [FORGE_LICENSE.txt](FORGE_LICENSE.txt).
