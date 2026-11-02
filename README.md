# Sheeper

A Java learning project: a Minecraft mod for Minecraft 1.19.2 using Forge.

Adds an explosive sheep that approaches when you look away. Hear it bleat? Look back before it's too late!

## Download

Download the mod JAR matching your Minecraft version and mod loader from [GitHub Releases](https://github.com/codemorra/sheeper/releases).

Place the downloaded JAR in your Minecraft installation's `mods` folder. See the release notes for compatibility details and known limitations.

## Project Status

Sheeper look like ordinary sheep, but approach when you look away and explode if they get close enough. Their wool can be used as an explosive block, and their meat brings both rewards and risks: prepare it in a smoker and keep milk handy.

### Finding and breeding

- **Natural spawning:** Each naturally spawning sheep has a configurable chance to become a Sheeper, including during world generation. The default chance is 5%.
- **Spawning them yourself:** Use `/summon sheeper:sheeper` or a spawn egg. Using a Sheeper spawn egg on a Sheeper creates a baby. Sheeper use the vanilla sheep model and textures.
- **Breeding:** Feed them wheat. Two Sheeper produce a Sheeper; a mixed sheep/Sheeper pair has a 50% chance of either type. Two normal sheep produce a normal sheep. Wool colors follow vanilla inheritance rules.

### Sheeper behavior

- **Adults approach when unobserved.** Their eyes glow red as they pursue nearby Survival or Adventure players. At close range, they stop, bleat, swell, turn red, and count down to an explosion.
- **Looking back stops the countdown.** Observation by any eligible nearby player, or leaving fuse range, cancels and fully resets it. The visual warning fades smoothly. Creative players and spectators are ignored.
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
- **Eating it raw:** For six seconds, take half a heart of base damage every second, hear a quiet blast, and receive a small push in a random horizontal direction.
- **Eating it cooked:** Gain 50% movement speed for ten seconds, but take half a heart of base damage every two seconds. The blasts are quieter and add no knockback.
- **Both eating effects can be lethal.** Damage bypasses armor, and nutrition matches the corresponding vanilla mutton. Eating another piece refreshes its effect without stacking it. Raw and cooked effects can run together; drinking milk ends both. Creative players and spectators are unaffected.
- **Pulsing red glow:** Skin and screen turn red with a fast rise and slow decay. Cooked meat uses one quarter of the tint strength.
- **Vapor with every burst:** Raw meat produces three quick puffs at each outlet: mouth/nose, both ears, and the rear. Head puffs are orange-red; rear puffs are green with a warm red tint. Cooked meat produces larger green rear puffs throughout the effect, plus three orange-red rear puffs at each burst.
- **Visible to nearby players:** Skin pulses and vapor are visible to others, and burst sounds can be heard nearby. The screen tint affects only the player who ate the meat. Both meat items also have animated sparkles over their vanilla or resource-pack textures.

### Configuration

Settings are stored in `config/sheeper-common.toml`.

- **Adults and spawning:** Adjust detection radius, observation angle, fuse distance, fuse duration, adult explosion strength, and natural spawn chance. Fuse distance is capped at the detection radius. Spawn chance ranges from 0% (disabled) to 100% (every naturally spawning sheep).
- **Babies:** Adjust extra movement speed, bump knockback, explosion strength, and the minimum/maximum pause between attacks.
- **Attack pauses:** Equal minimum and maximum values give a fixed pause. A maximum below the minimum is treated as equal to the minimum.

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
