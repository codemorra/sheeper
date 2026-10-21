# Sheeper

A Java learning project: a Minecraft mod for Minecraft 1.19.2 using Forge.
Adds a sheep with Creeper-like behavior.

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

Original project code is licensed under the [MIT License](LICENSE).

The Forge license and accompanying third-party notices are included in
[FORGE_LICENSE.txt](FORGE_LICENSE.txt).
