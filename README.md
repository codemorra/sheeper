# Sheeper

A Java learning project: a Minecraft mod for Minecraft 1.19.2 using Forge.

Adds an explosive sheep that approaches when you look away. Hear it bleat? Look back before it's too late!

## Download

Download the mod JAR matching your Minecraft version and mod loader from [GitHub Releases](https://github.com/codemorra/sheeper/releases).

Place the downloaded JAR in your Minecraft installation's `mods` folder. See the release notes for compatibility details and known limitations.

## Project Status

Sheeper look like ordinary sheep, but approach when you look away and explode if they get close enough. Their wool can be used as an explosive block, and their meat brings both rewards and risks: prepare it in a smoker and keep milk handy. The values described below are the defaults; gameplay and presentation can be configured separately.

### Finding and breeding

- **Natural spawning:** Each naturally spawning sheep has a configurable chance to become a Sheeper, including during world generation. The default chance is 5%.
- **Spawning them yourself:** Use `/summon sheeper:sheeper` or a spawn egg. Using a Sheeper spawn egg on a Sheeper creates a baby. Sheeper use the vanilla sheep model and textures.
- **Breeding:** Feed them wheat. Two Sheeper produce a Sheeper; a mixed sheep/Sheeper pair has a 50% chance of either type. Two normal sheep produce a normal sheep. Wool colors follow vanilla inheritance rules.

### Sheeper behavior

- **Adults approach when unobserved.** Their eyes glow red as they pursue nearby Survival or Adventure players. At close range, they stop, bleat, swell, turn red, and count down to an explosion.
- **Looking back stops the countdown.** Observation by any eligible nearby player, or leaving fuse range, cancels and fully resets it. The visual warning fades smoothly. Creative players and spectators are ignored.
- **Attacked adults retaliate.** Red eyes stay lit as they chase the first attacker, even while watched. A short hiss and slight swelling precede a contact burst: one heart of base damage and strong knockback to nearby entities, without self-damage or block destruction. They pause briefly, then attack again until the target dies or leaves detection range.
- **Adult deaths cause a burst.** Ordinary deaths leave normal loot and experience without destroying blocks. Every damaging fire hit triggers a small burst; a fatal fire hit also makes them swell, turn red, and self-destruct without loot. A non-fire killing blow still causes an ordinary death, even while burning.
- **Babies are faster and less cautious.** They approach even while watched, bump players without direct damage, then retreat and pause. Taking damage makes them explode immediately with a smaller blast. Their extra speed disappears when they grow up.
- **Mob explosions respect `mobGriefing`.** Sheeper that detonate themselves leave no wool or meat behind.

### Explosive wool

- **Collect all 16 colors.** Unshorn adults drop one matching wool block on ordinary death. Shearing by hand or dispenser yields 1–3 blocks. White Sheeper wool can be dyed, but Sheeper wool cannot replace normal wool in recipes for beds or carpets.
- **Harvest with shears.** Breaking placed wool with another tool or by hand causes a small blast with damage and knockback, without dropping wool or destroying surrounding blocks and dropped items. Creative mining is safe and produces no drops.
- **Ignite it like TNT.** Fire, redstone, and explosion chains can prime Sheeper wool. Primed wool explodes destructively and can chain with both TNT and other Sheeper wool. These block explosions are independent of `mobGriefing`.
- **Sparkles preserve texture packs.** The animated overlay sits over the vanilla or resource-pack wool texture, including while the wool is primed.

### Sheeper mutton

- **Drops:** Adults drop 1–2 raw Sheeper mutton on ordinary death, with extra drops from Looting. Sheared adults still drop meat; babies do not.
- **Safe preparation:** Use a smoker. Heating it in a furnace or on either type of lit campfire starts a three-second warning, then a small blast. One piece of meat is consumed; the appliance drops its normal loot and remaining contents. Removing the meat or extinguishing the appliance resets the warning. The blast damages and knocks back nearby entities while preserving surrounding blocks and dropped items.
- **Keep raw drops away from fire.** A dropped raw stack detonates on contact with fire, soul fire, lava, or a lit campfire. Surrounding blocks and other dropped items survive. Cooked meat does not detonate on fire contact.
- **Eating it raw:** For six seconds, take half a heart of base damage every second, hear a quiet internal explosion, and receive a small push in a random horizontal direction.
- **Eating it cooked:** Gain 50% movement speed for ten seconds, but take half a heart of base damage every two seconds. The internal explosions are quieter and add no knockback.
- **Both eating effects can be lethal.** Damage bypasses armor, and nutrition matches the corresponding vanilla mutton. Eating another piece refreshes its effect without stacking it. Raw and cooked effects can run together; drinking milk ends both. Creative players and spectators are unaffected.
- **Pulsing red glow:** Skin and screen turn red with a fast rise and slow decay. Cooked meat uses one quarter of the tint strength.
- **Vapor from internal explosions:** Raw meat produces three quick puffs at each outlet: mouth/nose, both ears, and the rear. Head puffs are orange-red; rear puffs are green with a warm red tint. Cooked meat produces larger green rear puffs throughout the effect, plus three orange-red rear puffs at each internal explosion.
- **Visible to nearby players:** Skin pulses and vapor are visible to others, and internal explosion sounds can be heard nearby. The screen tint affects only the player who ate the meat. Both meat items also have animated sparkles over their vanilla or resource-pack textures.

### Configuration

**Gameplay: `config/sheeper-common.toml`**

The server's copy controls gameplay in multiplayer. All settings are grouped under `sheeper`, `spawning`, `baby`, `breeding`, `mutton`, and `wool`.

- **Adults and spawning:** Detection radius, observation angle, fuse distance/duration, adult explosion strength, and natural spawn chance. Fatal fire uses the same fuse duration and explosion strength as stealth. Fuse distance is capped at detection radius; spawn chance ranges from 0% to 100%.
- **Retaliation and bursts:** Pursuit speed, contact fuse duration, attack pause, and burst radius, damage, and additional knockback. Pursuit uses the existing detection radius. Zero disables burst damage or additional knockback; ordinary hit reactions still apply.
- **Babies:** Extra speed, bump knockback, explosion strength, and minimum/maximum attack pauses. A maximum pause below the minimum is treated as equal to the minimum.
- **Mixed breeding:** Chance of Sheeper offspring from a mixed pair, from 0% to 100%; default 50%.
- **Raw mutton:** Effect duration, interval and damage of internal explosions, and random knockback.
- **Cooked mutton:** Effect duration, interval and damage of internal explosions, and the movement-speed bonus.
- **Unsafe cooking:** Enable/disable the reaction, set its warning time and explosion strength. Disabling the reaction lets furnace/campfire recipes finish normally. Warning time is limited to 9.95 seconds to stay below the default furnace recipe's ten-second cooking time; custom recipes must also take longer than the warning.
- **Fire contact:** Enable/disable dropped raw mutton detonation and set its explosion strength. With this reaction disabled, raw drops can burn like ordinary items.
- **Wool:** Unsafe-mining blast strength, primed explosion strength, and normal fuse duration. Explosion chains still shorten the fuse.

Durations and intervals use seconds, rounded up to whole game ticks. Damage uses health points: 1 is half a heart. A zero eating duration disables that food's effect for new meals; zero damage, knockback, or speed disables that component. Ordinary Minecraft damage immunity still applies to very short intervals. Explosion strength 0 suppresses the blast but retains the enabled reaction's consumption and appliance removal. Use the reaction's enable switch to disable it entirely.

Eating durations and normal wool fuses are captured when eating/igniting. New internal-explosion intervals apply after the next scheduled explosion; damage and speed settings are read during the active effect. Other existing Sheeper settings retain their previous behavior.

**Presentation: `config/sheeper-client.toml`**

Each player controls their own visuals and audio. These settings do not change server-side damage, timing, spawning, or breeding.

- **Tint:** Enable skin and screen tint independently; adjust raw/cooked strength, pulse attack/release/decay, and final fade duration. Both tints share the same pulse curve. The default cooked strength is 0.25; final fade defaults to two seconds.
- **Vapor:** Enable/disable it, set raw/cooked size and puff counts, and adjust sequence spacing and the continuous cooked stream's interval. Minecraft's Minimal particle setting also hides vapor. An existing short sequence finishes with its captured count/spacing; the next uses the new settings.
- **Sound:** Separate local volume multipliers for warnings, raw internal explosions, and cooked internal explosions. 1 preserves the usual volume and 0 mutes that category, without changing ordinary sheep or TNT sounds.

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

## Verification

Run the automated verification with Java 17:

```bash
./gradlew verifyMod
```

- Builds the mod, checks wool resources and JAR contents, and runs the GameTests.
- Covers representative fuse/cancellation, death loot, wool interactions, cooking, eating/milk, baby damage, natural replacement, dye-recipe, and packet contracts.
- Each run uses a fresh test world in `build/gametest/run/`; development worlds and configuration in `run/` are preserved. Test classes and scenes are excluded from the mod JAR.
- Stops on the first failed test and fails on missing/incomplete results. Results are in `build/reports/gametest/results.xml`; server logs are in `build/gametest/run/logs/`.
- Pull requests targeting `main` automatically run the same verification with Java 17. Reports and server logs are uploaded as the `verification-reports` artifact, including on failure.
- For a build without tests, use `./gradlew assemble`. To compile the test sources without running them, use `./gradlew gameTestClasses`.
- To modify the shared test arena, edit `scripts/generate_test_arena.py` and regenerate it with `python scripts/generate_test_arena.py`. Running the tests does not require Python.

The suite does not replace manual gameplay checks for AI movement, breeding, fire reactions, explosion chains, visuals, sound, and multiplayer synchronization.

## Run the Development Client

```bash
./gradlew runClient
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

The Forge license and accompanying third-party notices are included in [FORGE_LICENSE.txt](FORGE_LICENSE.txt).
