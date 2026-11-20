# Sheeper

A Minecraft mod for Minecraft 1.19.2 using Forge.

Adds an explosive sheep that approaches when you look away. Hear it bleat? Look back before it's too late!

## Download

Download the mod JAR from [GitHub Releases](https://github.com/codemorra/sheeper/releases).

Place the downloaded JAR in your Minecraft installation's `mods` folder. See the release notes for compatibility details and known limitations.

## Project Status

Sheeper look like ordinary sheep, but approach when you look away and explode if they get close enough. Their wool can be used as an explosive block, and their meat brings both rewards and risks: prepare it in a smoker and keep milk handy. Gameplay and presentation can be configured separately.

### Finding and breeding

- **Natural spawning:** Each naturally spawning sheep has a configurable chance to become a Sheeper, including during world generation.
- **Spawning them yourself:** Use `/summon sheeper:sheeper` or a spawn egg. Using a Sheeper spawn egg on a Sheeper creates a baby. Sheeper use the vanilla sheep model and textures.
- **Breeding:** Feed them wheat. Sheeper pairs produce Sheeper; mixed sheep/Sheeper pairs can produce either type. Normal sheep pairs produce normal sheep. Wool colors follow vanilla inheritance rules.

### Sheeper behavior

- **Adults approach when unobserved.** Their eyes glow red as they pursue nearby Survival or Adventure players. At close range, they stop, bleat, swell, turn red, and count down to an explosion.
- **Looking back stops the countdown.** Observation by any eligible nearby player, or leaving fuse range, cancels and fully resets it. The visual warning fades smoothly. Creative players and spectators are ignored.
- **Attacked adults retaliate.** Red eyes stay lit as they chase the first attacker, even while watched. A short hiss and slight swelling precede a damaging contact burst with strong knockback to nearby entities, without self-damage or block destruction. They pause briefly, then attack again until the target dies or leaves detection range.
- **Adult deaths cause a burst.** Ordinary deaths leave normal loot and experience without destroying blocks. Every damaging fire hit triggers a small burst; a fatal fire hit also makes them swell, turn red, and self-destruct without loot. A non-fire killing blow still causes an ordinary death, even while burning.
- **Babies are faster and less cautious.** They approach even while watched, bump players without direct damage, then retreat and pause. Taking damage makes them explode immediately with a smaller blast. Their extra speed disappears when they grow up.
- **Mob explosions respect `mobGriefing`.** Sheeper that detonate themselves leave no wool or meat behind.

### Elemental Sheeper bosses

- **Summoning:** Use `/summon sheeper:lava_sheeper`, `/summon sheeper:lightning_sheeper`, or `/summon sheeper:water_sheeper`.
- **Elemental appearance:** Lava has dark wool with glowing magma, Lightning has gray wool with moving electrical lines, and Water has ocean-blue wool. Matching body colors and ambient particles for Lava and Lightning complete the appearance. The layers use Minecraft textures and pick up resource-pack replacements for those textures.
- **Boss foundation:** Each persistent adult boss has an element-colored health bar for players tracking it. Each element has independent size and maximum-health settings.

#### Water Sheeper encounter

- **Water arena:** A bubble front expands a bounded underwater arena before combat begins, gently carrying players outward without damage. Swimming, breathing and underwater fog work without placing water blocks or washing away terrain and objects. Living entities use their usual movement and breathing rules for water. Players can breathe in arena water, including natural water, which keeps its visible surface. After defeat, the boss dissolves into bubbles and the virtual water level recedes, lowering swimmers safely toward the ground. Players can enter from above but must leave sideways; creative and spectator players bypass the upper barrier. Creative players also bypass spawn transport and virtual swimming.
- **Water beam:** A stationary, warned stream with gradual tracking and fixed reach replaces projectile support briefly, followed by a recovery window.
- **Maelstrom:** An immune, rotating ascent draws players inward before a downward impact releases a pressure wave. Contact starts damage and outward transport; reaching the protected outer ring ends both. Damage stops when the wave finishes. The boss becomes briefly vulnerable as the wave spreads.
- **Air fountains:** Swimmers use air more slowly than in ordinary water. Fixed bubble columns refill their air and provide gentle lift in open sections. The number of fountains scales with the arena floor area.
- **Water mines:** Mines appear at random depths and burst in staggered intervals. The core deals light damage. An expanding wave of bubbles pushes swimmers without additional damage.
- **Movement:** The Water Sheeper swims toward and around players throughout the virtual water volume, following height changes and navigating around terrain, with occasional short water sprints. Without participants, it roams the arena at a relaxed pace.
- **Default attack:** Fires water projectiles while circling and switches targets midway if another player is available. If no eligible special is ready, the boss keeps firing and checks again after another projectile interval. The boss cannot repeat the same special in succession. At close range, it pauses shooting and approaches for a melee opening. When any player stays too close, everyone within reach takes damage and is pushed away, independently of its selected target.
- **Arena rules:** Players qualify for targeting and damage permission only after fully crossing the inner transition edge. Participation and damage permission end as soon as the player crosses the actual arena boundary. Indirect attacks use their owner's current permission. Creative players may deal damage but do not join combat or stop regeneration. Boss impulses stop at the outer boundary of connected water arenas; deliberate outward swimming cancels the remaining launch so players can still leave. A configurable soft boundary slows lateral crossings from either side while preserving movement along the edge and vertical swimming. Without combat participants, the boss blocks other damage and regenerates after a grace period; full recovery resets the encounter.
- **Multiplayer health:** Maximum health scales with peak simultaneous participation, preserving the remaining health percentage. Leaving does not lower it until the encounter fully resets.

#### Lightning Sheeper encounter

- **Storm arena:** Local rain, dark skies and thunder accompany the fight, with warned lightning strikes along the arena edge.
- **Teleporting movement:** The boss teleports within its arena, including between attacks.
- **Lightning waves:** Warned strikes target players and random arena locations, spread outward in waves, and leave damaging electrical zones.
- **Fronts and rings:** Lightning lanes travel toward the arena boundary. Expanding electric rings follow the ground and can be avoided by jumping.
- **Keep your distance:** Staying near the boss builds a visible charge that releases a damaging blast and knocks nearby players away.

### Explosive wool

- **Collect all colors.** Unshorn adults drop matching wool on ordinary death. Shearing by hand or dispenser also yields wool. White Sheeper wool can be dyed, but Sheeper wool cannot replace normal wool in recipes for beds or vanilla carpets.
- **Harvest with shears.** Breaking placed wool with another tool or by hand causes a small blast with damage and knockback, without dropping wool or destroying surrounding blocks and dropped items. Creative mining is safe and produces no drops.
- **Ignite it like TNT.** Fire, redstone, and explosion chains can prime Sheeper wool. Primed wool explodes destructively and can chain with both TNT and other Sheeper wool. These block explosions are independent of `mobGriefing`.
- **Sparkles preserve texture packs.** The animated overlay sits over the vanilla or resource-pack wool texture, including while the wool is primed.

### Explosive carpets

- **Craft all colors.** Matching Sheeper wool blocks can be crafted into carpets with the same sparkling wool textures.
- **Watch your step.** Grounded living entities trigger an immediate explosion that consumes the carpet without drops. Crouching or sneaking is safe; Creative players and spectators do not trigger it.
- **Area damage and launch.** Exposed nearby entities take damage and are launched upward and away. Armor and explosion protection apply; walls provide cover. Surrounding blocks remain intact, with no TNT or wool ignition.
- **Harvest with shears.** Other survival tools cause the same mini-explosion as unsafe wool mining, without carpet drops or damage to surrounding blocks and dropped items. Creative mining is safe without drops.

### Sheeper mutton

- **Drops:** Adults drop raw Sheeper mutton on ordinary death, with extra drops from Looting. Sheared adults still drop meat; babies do not.
- **Safe preparation:** Use a smoker. Heating it in a furnace or on either type of lit campfire starts a warning followed by a small blast. Meat is consumed; the appliance drops its normal loot and remaining contents. Removing the meat or extinguishing the appliance resets the warning. The blast damages and knocks back nearby entities while preserving surrounding blocks and dropped items.
- **Keep raw drops away from fire.** A dropped raw stack detonates on contact with fire, soul fire, lava, or a lit campfire. Surrounding blocks and other dropped items survive. Cooked meat does not detonate on fire contact.
- **Eating it raw:** Temporarily take periodic damage, hear quiet internal explosions, and receive small pushes in random horizontal directions.
- **Eating it cooked:** Gain a temporary movement-speed bonus, but take periodic damage. The internal explosions are quieter and add no knockback.
- **Both eating effects can be lethal.** Damage bypasses armor, and nutrition matches the corresponding vanilla mutton. Eating another piece refreshes its effect without stacking it. Raw and cooked effects can run together; drinking milk ends both. Creative players and spectators are unaffected.
- **Pulsing red glow:** Skin and screen turn red with a fast rise and slow decay. Cooked meat uses a weaker tint.
- **Vapor from internal explosions:** Raw meat produces short puff bursts at each outlet: mouth/nose, both ears, and the rear. Head puffs are orange-red; rear puffs are green with a warm red tint. Cooked meat produces larger green rear puffs throughout the effect, plus orange-red rear puff bursts at each internal explosion.
- **Visible to nearby players:** Skin pulses and vapor are visible to others, and internal explosion sounds can be heard nearby. The screen tint affects only the player who ate the meat. Both meat items also have animated sparkles over their vanilla or resource-pack textures.

### Configuration

**Gameplay: `config/sheeper-common.toml`**

The server's copy controls gameplay in multiplayer. Normal Sheeper settings remain grouped under `sheeper`, `spawning`, `baby`, `breeding`, `mutton`, `wool`, and `carpet`.

- **Adults and spawning:** Detection radius, observation angle, fuse distance/duration, adult explosion strength, and natural spawn chance. Fatal fire uses the same fuse duration and explosion strength as stealth. Fuse distance is capped at detection radius.
- **Retaliation and bursts:** Pursuit speed, contact fuse duration, attack pause, and burst radius, damage, and additional knockback. Pursuit uses the existing detection radius. Burst damage and additional knockback can be disabled independently; ordinary hit reactions still apply.
- **Babies:** Extra speed, bump knockback, explosion strength, and minimum/maximum attack pauses. A maximum pause below the minimum is treated as equal to the minimum.
- **Mixed breeding:** Chance of Sheeper offspring from a mixed pair.
- **Raw mutton:** Effect duration, interval and damage of internal explosions, and random knockback.
- **Cooked mutton:** Effect duration, interval and damage of internal explosions, and the movement-speed bonus.
- **Unsafe cooking:** Enable/disable the reaction, set its warning time and explosion strength. Disabling the reaction lets furnace/campfire recipes finish normally. Cooking recipes must take longer than the configured warning.
- **Fire contact:** Enable/disable dropped raw mutton detonation and set its explosion strength. With this reaction disabled, raw drops can burn like ordinary items.
- **Wool:** Unsafe-mining blast strength (also used by carpets), primed explosion strength, and normal fuse duration. Explosion chains still shorten the fuse.
- **Carpets:** Step-explosion radius, base damage, horizontal knockback, and upward push. Distance and cover reduce the launch; cover also reduces damage. Damage and impulse can be disabled independently, while stepping still consumes the carpet.

Durations and intervals use seconds. Projectile speed and particle/processing rates use units per second. Damage uses health points. Food effects and individual damage, knockback, or speed components can be disabled through their settings. Ordinary Minecraft damage immunity still applies to very short intervals. Suppressing an unsafe-cooking blast retains the enabled reaction's consumption and appliance removal. Use the reaction's enable switch to disable it entirely.

Eating durations and normal wool fuses are captured when eating/igniting. New internal-explosion intervals apply after the next scheduled explosion; damage and speed settings are read during the active effect. Other existing Sheeper settings retain their previous behavior.

**Elemental bosses: `config/sheeper/lava.toml`, `lightning.toml`, and `water.toml`**

Each element has its own common config file, with settings under `elemental.<element>`.

- **Shared options:** Each element has independent `size` and `maxHealth` settings. Size changes also affect hitboxes; health changes preserve the remaining health percentage. The server synchronizes boss size to clients.
- **Water:** `elemental.water` controls the virtual water arena, movement, attacks, proximity defense, and recovery.
- **Lightning:** `elemental.lightning` controls arena bounds, spawn waves, teleports, attacks, proximity discharge, and local weather.

**Presentation: `config/sheeper-client.toml`**

Each player controls their own visuals and audio. These settings do not change server-side damage, timing, spawning, or breeding.

- **Tint:** Enable skin and screen tint independently; adjust raw/cooked strength, pulse attack/release/decay times in seconds, and final fade duration. Both tints share the same pulse curve.
- **Vapor:** Enable/disable it, set raw/cooked size and puff counts, and adjust sequence spacing and the continuous cooked stream's interval. Minecraft's Minimal particle setting also hides vapor. An existing short sequence finishes with its captured count/spacing; the next uses the new settings.
- **Sound:** Separate local volume multipliers for warnings, raw internal explosions, and cooked internal explosions. These settings leave ordinary sheep and TNT sounds unchanged.

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

- Builds the mod, checks wool/carpet resources and JAR contents, and runs the GameTests.
- Covers representative fuse/cancellation, death loot, wool interactions, carpet stepping/crouching and harvesting, cooking, eating/milk, baby damage, natural replacement, dye-recipe, elemental boss configuration/boss bars, Water and Lightning boss encounters/persistence, and packet contracts.
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
