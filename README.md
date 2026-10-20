# Sheeper

A Java learning project: a Minecraft mod for Minecraft 1.19.2 using Forge.
Adds a sheep with Creeper-like behavior.

## Project Status

The Sheeper can be summoned using `/summon sheeper:sheeper` or a spawn egg.
It currently uses vanilla sheep behavior, models, and textures.

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

## Run the Development Client

```bash
./gradlew runClient
```

The generated mod JAR is located in `build/libs/`.

## License

Original project code is licensed under the [MIT License](LICENSE).

The Forge license and accompanying third-party notices are included in
[FORGE_LICENSE.txt](FORGE_LICENSE.txt).
