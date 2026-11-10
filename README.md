# Random Item Timer

A server-side Fabric mod for Minecraft 26.3. It periodically gives every online player a random item from the game's active item registry, including items registered by compatible mods.

## Features

- Configurable random-item interval from 1 to 120 seconds (default: 30 seconds).
- Optional Simply Swords weapon chance from 10% to 100%, in 10% increments.
- In-game controls to stop and restart item drops.
- Custom countdown label, boss-bar color, and text color.
- Optional item announcement in chat.
- Settings saved to `config/randomitem.json`.

## Commands

These commands require operator/game-master permission:

| Command | Description |
|---|---|
| `/randomitem interval <1-120>` | Set the timer interval in seconds. |
| `/randomitem simplychance <10-100>` | Set Simply Swords weapon chance; use multiples of 10. |
| `/randomitem stop` | Stop random item drops. |
| `/randomitem start` | Resume random item drops. |
| `/randomitem text <label>` | Set the countdown label. Use `%time%` for seconds remaining. |
| `/randomitem color <color>` | Set boss-bar color: pink, blue, red, green, yellow, purple, or white. |
| `/randomitem textcolor <color>` | Set text color: white, yellow, gold, red, light_purple, aqua, green, blue, or gray. |
| `/randomitem announce <true\|false>` | Enable or disable item-name chat announcements. |
| `/randomitem status` | Show current settings. |

## Dependencies

- Minecraft Java Edition 26.3
- Java 25 or newer
- Fabric Loader 0.19.5 or newer
- Fabric API for Minecraft 26.3 (tested with `0.165.2+26.3`)
- Optional: a Minecraft 26.3-compatible Simply Swords mod for Simply Swords weapons to be available in the random pool

The randomizer only selects items that are registered by the mods loaded in the active game. It does not port Simply Swords itself.

## Install

1. Install Fabric Loader for Minecraft 26.3 and the required Fabric API.
2. Put this mod JAR and any compatible content mods in the same profile's `mods` folder.
3. Keep only one copy of each mod in that folder, then launch the Fabric 26.3 profile.
4. When you open the zip file then make sure to choose random-item-mod-2.0.0-26.3.jar jar file

## Build from source

Open the repository folder in IntelliJ IDEA and import it as a Gradle project. Use Java 25. Run `gradlew build` (Windows: `gradlew.bat build`). The mod JAR is written to `build/libs/`.

## License and credits

Random Item Timer is an independent project. Simply Swords is an optional compatible mod and is not bundled with this project.
