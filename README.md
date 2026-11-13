# Draco Utility

**A product of Draco Studio.**
Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio). Licensed under the **GNU General Public License v3**.

A client-side utility mod for Minecraft Java Edition (Fabric 1.21.1): a settings menu with flight, ESP,
X-ray, a guide line to the nearest target, a minimap, a large map and named waypoints.
Made for **singleplayer** and **your own private server**.

| | |
|---|---|
| Mod name | Draco Utility (`dracoutility`) |
| Version | 1.1.0 |
| Developer | DUCNGUYEN-creator (Draco Studio) |
| Minecraft | 1.21.1 |
| Loader | Fabric Loader 0.16.0 or newer, plus Fabric API for 1.21.1 |
| Java | JDK 21 |
| Environment | Client only (nothing to install on the server) |
| License | GNU GPL v3 (see the `LICENSE` file) |

---

## 1. Read this first

**Permission gate.** Every feature, including the minimap and the large map, only works when you already have
permission level 2 or higher:

- Singleplayer: the world must have **Allow Cheats** turned on.
- Server: you must be an **operator** (`/op YourName`).

If the permission level cannot be determined (for example through a version-translation layer), the mod treats it
as "no permission" and **locks everything**. When locked, the menu shows a red "locked" message (in Vietnamese)
and the buttons are greyed out.

**Intended use.** The mod is meant for singleplayer or a private server with players who agreed to it. Many public
servers forbid ESP, X-ray, flight, auto click and auto PvP, and may ban accounts for them. Using the mod there is the
user's own responsibility. The mod does not try to bypass permissions: teleport, commands, creative mode and stat
boosts are real commands (`/tp`, `/gamemode`, `/effect`, `/attribute`), and the server still checks permissions itself.

**Test status.** Version 1.0.0 built successfully. Version 1.1.0 (colored ESP, guide line, minimap, saved
settings, fullbright, Sodium support) was written **without being compiled or run in a real game yet**. If the build
or the game fails, send the error log so it can be fixed (see section 9). Update this paragraph once it has been
tested.

**Language.** The in-game menu text is currently **Vietnamese**. Only the key binding names are translated through
lang files (English and Vietnamese).

---

## 2. Quick install (Windows)

1. Install **JDK 21** (Temurin is recommended) and **Fabric Loader for 1.21.1** (TLauncher has a ready-made
   "Fabric 1.21.1" version).
2. Download **Fabric API** for 1.21.1 from Modrinth (choose Minecraft 1.21.1 and Fabric) and put the `.jar` in your
   `mods` folder.
3. Close Minecraft.
4. Extract the project, open the extracted folder and double-click **`install.bat`**. It builds the mod and copies
   it to `D:\.minecraft\mods`. If your game folder is different, open `install.bat` in Notepad and change the path.
5. Start the game with the **Fabric 1.21.1** version.

Your `mods` folder should end up like this:

```
mods/
  dracoutility-1.1.0.jar
  fabric-api-<version>+1.21.1.jar
```

Only `.jar` files belong in `mods`. Do not leave the source folder in there, and do not keep two versions of
`dracoutility` at the same time (`install.bat` deletes the old one for you).

---

## 3. Building manually

The project ships with the Gradle wrapper (Gradle 8.10, Fabric Loom 1.7).

```
cd <dracoutility folder>
.\gradlew.bat build        (Windows PowerShell)
./gradlew build            (macOS / Linux)
```

The first build takes a few minutes because Minecraft, the mappings and the libraries must be downloaded.
`BUILD SUCCESSFUL` means it worked.

**Where the .jar file is after building:**

```
<dracoutility folder>\build\libs\dracoutility-1.1.0.jar
```

- Use **`dracoutility-1.1.0.jar`** (this is the mod).
- Do **not** use `dracoutility-1.1.0-sources.jar` (it only contains source code).

Versions are set in `gradle.properties`:

| Key | Value |
|---|---|
| minecraft_version | 1.21.1 |
| yarn_mappings | 1.21.1+build.3 |
| loader_version | 0.16.5 |
| fabric_version | 0.102.0+1.21.1 (the version used to build) |
| mod_version | 1.1.0 |

If Gradle cannot find a version, open `fabricmc.net/develop`, take the latest numbers for 1.21.1 and update
`gradle.properties`.

**Running out of space on drive C?** Gradle stores its libraries in `C:\Users\<name>\.gradle`. To move them, set the
`GRADLE_USER_HOME` environment variable (for example `setx GRADLE_USER_HOME "D:\Downloads\gradle-home"`) and open a
new PowerShell window. That folder is only a download cache; deleting it does not affect a mod you already built.

---

## 4. How to use

### Keys

| Default key | Action | Rebind in |
|---|---|---|
| **Right Shift** | Open / close the menu | Options > Controls > Draco Utility |
| **M** | Open the large map | Options > Controls > Draco Utility |

You must be inside a world with no other screen open (chat, inventory, pause menu). Right Shift is the Shift key on
the **right** side of the keyboard. While the menu is open, clicking the minimap (where there is no button) also
opens the large map.

### Getting permission

- **Singleplayer:** turn on **Allow Cheats** (tab "More") when creating the world. For an existing world:
  Esc, Open to LAN, Allow Cheats: ON, Start LAN World.
- **Private server:** run `op YourName` in the server console. To fly, set `allow-flight=true` in
  `server.properties` and restart the server.

### Menu pages

| Page | Contents |
|---|---|
| **General** | Flight, flight speed, Auto PvP, PvP players too, Creative / Survival button |
| **Display** | ESP, X-ray, ESP entities, ESP items, ESP ores, name labels, X-ray borders, border radius, X-ray brightness, ore filter |
| **Guide** | Guide line on/off, target kind, ore filter, ore scan radius, show waypoints |
| **Click** | Auto click, mode (attack/break or place blocks), speed |
| **Map** | Minimap, size, zoom, open the large map, mark current position |
| **Teleport** | Teleport to X Y Z (`~` works), teleport to a player |
| **Stats** | 7 effects (speed, jump boost, strength, haste, resistance, regeneration, night vision) at level 0 to 5, max health, reset all |
| **Commands** | Type and run commands, command history: select, edit, delete, re-run |

(The page names and button labels in the game are Vietnamese.)

---

## 5. Features in detail

### 5.1 Flight
Survival flight by setting the player's velocity every tick (it does not need the `allowFlying` ability).
Space goes up, Shift goes down, WASD moves in the direction you look, and with no keys pressed you hover in place.
Speeds: 0.2 / 0.4 / 0.6 / 0.8 / 1.0. A dedicated server needs `allow-flight=true`, otherwise it kicks you for
floating. Singleplayer normally does not kick.

### 5.2 ESP (colored boxes through walls)

| Kind | Color |
|---|---|
| Players | Red |
| Hostile mobs | Orange |
| Passive mobs | Green |
| Ores | White |
| Items | Yellow |
| Waypoints | Light blue |

- Every object has a label with its name and distance, for example `Zombie [12m]`. The nearest player is marked
  with a star. Ore labels are shown for the 5 nearest ores only.
- Entity ESP range: 96 blocks. Ore scan radius: 16 / 24 / 32 / 48 blocks (set on the Guide page).
- The top-left HUD also shows the name and distance of the nearest player.
- Separate switches exist for entities, items, ores and name labels.

### 5.3 Guide line
Draws a line from the center of the screen to the **nearest** object of the kind you choose, in the same color as
its ESP box. Target kinds: Ore, Player, Hostile mob, Passive mob, Item, Waypoint.

- For ores you can filter by type: All, Coal, Iron, Copper, Gold, Redstone, Lapis, Diamond, Emerald, Quartz,
  Ancient Debris.
- For waypoints, the target is the waypoint you picked in the large map.
- The line is a **straight line to the target**, not a route around obstacles (see section 8).
- The HUD shows `Guide: <target> - <distance>m`.

### 5.4 X-ray
Uses a whitelist: only **ores, mineral blocks, building blocks, wood, leaves and functional blocks** stay visible;
everything else is see-through (dirt, plain stone, sand, gravel, grass, netherrack...).

- **Ores:** every block whose name contains `_ore`, plus Ancient Debris.
- **Mineral blocks:** coal, iron, copper, gold, redstone, lapis, diamond, emerald, netherite, raw ore, amethyst,
  quartz.
- **Wood and leaves:** every log, planks and leaves block.
- **Building blocks:** bricks, concrete, glass, wool, carpets, stairs, slabs, walls, fences, doors, polished,
  chiseled and smooth stone, cobblestone, purpur, prismarine, copper.
- **Functional blocks:** every block that has block-entity data (chests, furnaces, spawners, signs, beds, beacons,
  hoppers...), plus crafting tables, anvils, rails, pistons, TNT, obsidian, ladders, levers.
- **X-ray borders:** hidden blocks that **block movement** get a red border so you know something solid is in front
  of you. Radius 2 / 3 / 4 / 6 blocks, default 3. Grass and flowers get no border.
- **X-ray brightness (fullbright):** while X-ray is on, the mod adds a client-side night-vision effect so caves are
  not dark. It is removed when X-ray is turned off. The effect exists only on your machine; the server does not know
  about it.
- **Sodium:** two optional mixins target Sodium 0.5.x and 0.6.x. They are skipped silently when Sodium is not
  installed. This part is **untested**.
- Water and lava are **still drawn** while X-ray is on.

### 5.5 Auto click
- **Attack / break mode:** looking at a block holds the attack key so vanilla keeps mining (mining speed is decided
  by the game). Looking at a mob or player attacks every N ticks, never faster than the weapon cooldown.
- **Place mode:** right-clicks the targeted block face every N ticks (you must hold a block).
- **Speed:** 1 / 2 / 4 / 6.7 / 10 / 20 clicks per second.
- It pauses while any screen is open.

### 5.6 Auto PvP
Turns toward and hits the nearest target within 3 blocks. By default it only attacks hostile mobs. "PvP players too"
must be switched on before it attacks players.

### 5.7 Minimap, large map and waypoints
- The **minimap** sits in the top-left corner, north is up. Size 64 / 80 / 96 / 128; zoom 1 / 2 / 4 blocks per cell.
- It shows the surface when the sky is visible; in caves or the Nether it shows the floor around the player.
  There is light relief shading based on height.
- Markers: the player (white square with a red center), entities and items in the ESP colors, ores (white),
  waypoints (light blue).
- **Large map** (key M): scroll the mouse wheel or use the Zoom buttons for 1 / 2 / 4 / 8 blocks per cell.
  **Click the map** to pick a spot, type a name, then press **Save**. The waypoint list has a name button
  (makes it the guide target), **TP** (teleports with a command) and **X** (delete).
- Waypoints are stored per dimension (Overworld, Nether, End). The "mark current position" button on the Map page
  creates one where you stand.
- The minimap hides itself while the F3 screen is open.

### 5.8 Teleport, stats, creative, commands
- **Teleport:** sends `/tp @s x y z` or `/tp @s <name>`. Use `~` for relative coordinates.
- **Stats:** effects use `/effect give @s minecraft:<id> infinite <level-1> true`; max health uses
  `/attribute @s minecraft:generic.max_health base set N`.
- **Creative / Survival:** sends `/gamemode`. Needs permission level 2.
- **Commands:** type any command (no leading slash needed); the server still decides whether you may run it.
  The history keeps up to 50 commands and is saved to a file.

### 5.9 Saved settings
Every toggle and option is saved when the menu closes and when the game exits, so the next launch starts as you
left it. That includes Flight, Auto click and Auto PvP, which stay locked if you have no permission.

---

## 6. Files the mod writes

In the game's `config` folder (for example `D:\.minecraft\config`):

| File | Contents |
|---|---|
| `dracoutility.properties` | All settings |
| `dracoutility_history.txt` | Command history |
| `dracoutility_waypoints.txt` | Waypoints (name, coordinates, dimension, tab separated) |

Deleting a file resets that part to its defaults.

---

## 7. Project layout

```
dracoutility/
  LICENSE                     GNU GPL v3 text
  install.bat                 build and copy the mod to mods (Windows)
  build.gradle, settings.gradle, gradle.properties
  gradlew, gradlew.bat, gradle/wrapper/
  src/main/java/studio/draco/utility/
    DracoUtilityClient.java   entry point, key bindings, tick events
    Access.java               permission gate (level 2+)
    Features.java             state and per-tick logic (flight, click, PvP, fullbright)
    Config.java               save / load settings
    Targets.java              target kinds, colors, entity and ore classification
    OreScanner.java           ore scan, one layer per tick
    XRay.java                 list of blocks kept visible by X-ray
    WorldOverlay.java         in-world drawing: ESP, X-ray borders, guide line, labels
    MiniMap.java              terrain sampling and map drawing
    Hud.java                  minimap and HUD text
    MapScreen.java            large map, waypoints
    UtilityScreen.java        settings menu
    Waypoints.java, History.java
    mixin/BlockMixin.java     X-ray for the vanilla renderer
    mixin/SodiumOcclusion*.java  X-ray for Sodium (optional)
  src/main/resources/         fabric.mod.json, mixins, lang files, icon
```

Every source file starts with the GPL v3 copyright notice. JSON files cannot hold comments, so their license
information lives in `fabric.mod.json` and `LICENSE`.

---

## 8. What exists, limits, and possible improvements

### What exists (1.1.0)
Flight, colored ESP with labels, guide line to the nearest target, whitelist X-ray with borders and fullbright,
auto click with speed and place mode, auto PvP, teleport, commands with history, stat boosts, creative toggle,
minimap, large map, waypoints, saved settings, a mod icon, and Sodium support (untested).

### Current limits
- Water and lava are still visible while X-ray is on.
- The guide line is straight; it does not route around obstacles.
- The minimap always points north; in caves it only draws the floor near the player.
- Ores are scanned in a cube around you, not per chunk; large radii (48) cost more CPU.
- Sodium support relies on the class and method names of Sodium 0.5.x / 0.6.x; if Sodium changes them the feature
  simply does nothing (it does not crash).
- The menu is Vietnamese only.
- Built for 1.21.1 only; not tested together with ViaFabricPlus.

### Possible improvements (worth weighing first)

| Idea | Things to consider |
|---|---|
| Hide water and lava in X-ray | Needs another hook into the fluid renderer; a wrong method name could break game start-up |
| Route-aware guide (pathfinding) | Needs a path search over loaded blocks; much heavier and more complex than a straight line |
| Companion Paper plugin to confirm permissions | More accurate than guessing from the OP level and lets the server owner decide who may use which feature, but it means a plugin and a protocol to maintain |
| Edit the X-ray block list in the menu, with presets | New screens and config format |
| English (and other) localization through lang files | Small change; all menu text must move into lang files |
| More Minecraft versions (1.20.1, 1.21.4...) | Rendering and mappings change between versions; Stonecutter helps but much code is still version-specific |
| Mod Menu integration and a standard config screen | Adds an optional dependency |
| Rotating minimap, cave mode, per-chunk scanning | Should be drawn from a texture to stay smooth |
| Shader-based ESP outlines instead of boxes | Looks better but depends on the renderer and may clash with Sodium / Iris |
| Iris and shader pack compatibility | Needs separate testing |
| Per-world or per-server profiles | Needs management of several settings sets |
| Auto-disable on unknown servers | Safer when sharing the mod; hard to detect "unknown server" reliably |

When extending the mod, keep the permission gate on features that change gameplay (flight, teleport, auto PvP,
X-ray, ESP) so it cannot be used by accident on servers that do not allow it.

---

## 9. Troubleshooting

| Symptom | What to do |
|---|---|
| Build cannot find a Fabric API / Yarn / Loader version | Update the numbers in `gradle.properties` from `fabricmc.net/develop` |
| Build shows red errors in a `.java` file | Copy the error text so the code can be fixed (1.1.0 has not been compiled yet) |
| Build runs out of disk space | Move `GRADLE_USER_HOME` to another drive (section 3) |
| `Access is denied` when creating a folder at the root of a drive | Use another folder, for example under `Downloads`, or run PowerShell as Administrator |
| Game does not start or crashes at launch | Check `mods` has the right Fabric API for 1.21.1 and only one `dracoutility`; send `logs\latest.log` |
| Right Shift does nothing | Another screen is open, or the key conflicts; rebind it in Controls |
| Menu shows the red "locked" message | Turn on Allow Cheats or `/op` yourself |
| Flight pulls you down or kicks you on a server | Set `allow-flight=true` in `server.properties` |
| X-ray has no effect | Make sure it is on and you have permission; with Sodium, that support is untested, so try without Sodium |
| Minimap does not show | Turn it on on the Map page; it is hidden while F3 is open or while locked |

Game log: `<game folder>\logs\latest.log`.

---

## 10. Changelog

- **1.1.0:** colored ESP with name and distance labels, guide line, auto click with speed and place mode, saved
  settings, fullbright with X-ray, minimap, large map and waypoints, Sodium support (experimental), mod icon,
  GPL v3 headers and LICENSE file.
- **1.0.0:** basic menu, flight, ESP, X-ray, teleport, commands with history, stat boosts, creative toggle,
  auto click, auto PvP.

---

## 11. License, credits and disclaimer

**Draco Utility is a product of Draco Studio.**

```
Draco Utility - client-side utility mod for Minecraft (Fabric)
Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

Minecraft is a trademark of Mojang Studios / Microsoft. This project is not affiliated with Mojang, Microsoft,
Fabric or Sodium. Fabric, Fabric API and the Yarn mappings belong to the FabricMC team. The Gradle wrapper files
(`gradlew`, `gradlew.bat`, `gradle/wrapper/`) come from the FabricMC example mod template and keep their own
licenses. The mod is provided "as is", without warranty; users are responsible for how they use it, especially on
servers that have rules against it.
