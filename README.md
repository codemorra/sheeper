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

The Sheeper can be summoned using `/summon sheeper:sheeper` or a spawn egg.
It uses vanilla sheep models and textures.

While unobserved, it approaches nearby Survival and Adventure players.
Within fuse range, it stops, bleats, and starts a countdown before exploding.
Observation or leaving fuse range cancels and fully resets the countdown.
Explosions respect the mobGriefing game rule.

The detection radius, observation angle, fuse distance, and fuse duration
are configurable in `config/sheeper-common.toml`.
The effective fuse distance cannot exceed the detection radius.

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
