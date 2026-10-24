# Sheeper

A Java learning project: a Minecraft mod for Minecraft 1.19.2 using Forge.

Adds an explosive sheep that approaches when you look away.
Hear it bleat? Look back before it's too late!

## Download

Download the mod JAR matching your Minecraft version and mod loader
from [GitHub Releases](https://github.com/codemorra/sheeper/releases).

Place the downloaded JAR in your Minecraft installation's `mods` folder.
See the release notes for compatibility details and known limitations.

## Project Status

Each naturally spawning sheep has a configurable chance to be replaced
by a Sheeper (5% by default), both during world generation and later spawning.
Breeding normal sheep always produces normal sheep.

The Sheeper can also be summoned using `/summon sheeper:sheeper`
or a spawn egg. It uses vanilla sheep models and textures.

While unobserved, adult Sheeper silently approach nearby Survival and Adventure
players with glowing red eyes.
Within fuse range, they stop, bleat, and start a countdown while
swelling and turning red.
Observation by a living Survival or Adventure player, or leaving fuse range,
cancels and fully resets the countdown.
Visual warning effects fade smoothly when cancelled.

Baby Sheeper move faster and approach randomly selected Survival and Adventure
players, even while being watched.
They bump players without dealing direct damage, retreat, and pause before
choosing another target.
Babies use the same detection radius as adults.
Taking damage makes them explode immediately with a smaller explosion.
Using a Sheeper spawn egg on a Sheeper creates a baby Sheeper.
Babies grow into adults and lose their movement speed bonus.

All Sheeper explosions respect the mobGriefing game rule.

Settings are stored in `config/sheeper-common.toml`.
The detection radius, observation angle, fuse distance, fuse duration,
adult explosion strength, and natural spawn chance are configurable.
The effective fuse distance cannot exceed the detection radius.
`naturalSpawnChance` is a percentage from 0 to 100;
0 disables natural replacement, while 100 replaces every naturally spawning sheep.

Baby movement speed, bump knockback, minimum and maximum attack pauses,
and explosion strength are also configurable.
Equal minimum and maximum pause values produce a fixed pause.
If the maximum is below the minimum, the minimum is used for both.

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

The Forge license and accompanying third-party notices are included in
[FORGE_LICENSE.txt](FORGE_LICENSE.txt).
