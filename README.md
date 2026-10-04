# 🗺️ MapFrame

In vanilla Minecraft, a filled map only updates its terrain while a player is holding it and exploring the area it covers. Maps displayed in item frames can therefore become outdated unless someone takes each map down and carries it around manually.

MapFrame keeps maps in loaded item frames updated without requiring anyone to hold them. By default, a player still needs to be inside the area shown by the map. An optional mode can instead refresh any loaded terrain covered by a displayed map, even when no player is nearby. The mod runs entirely on the server, so players do not need to install anything.

## ⚙️ Configuration

MapFrame creates `config/mapframe.toml` the first time the server starts. Changes take effect after a server restart.

```toml
update_mode = "player_proximity"
```

`update_mode` accepts these values:

- `"player_proximity"` requires a player to be inside the map area. This is the default and the safest option for server performance.
- `"loaded_chunks"` refreshes already-loaded terrain covered by displayed maps without requiring a player inside the map area. It never loads or generates terrain.

If the option is missing, is not a string, contains an unknown value, or the TOML is malformed, MapFrame logs a warning and falls back to `"player_proximity"`. It leaves an invalid file untouched so that it can be corrected.

## ⚠️ Limits at Scale

MapFrame attempts at most ten terrain updates per second across the entire server. It slows down when the server reaches 40 MSPT and pauses updates at 45 MSPT. Each update is an indivisible piece of work, so an unusually expensive pixel can exceed the nominal 1 ms budget once it has started. Under load, maps update more slowly instead of consuming unlimited tick time or memory.

The biggest workload comes from many unique maps. In `player_proximity`, multiple players exploring different parts of a map can also create separate sampling work.

Maps and item frames must already be loaded. MapFrame never force-loads chunks, so unloaded parts of a map remain unchanged. A map also remains unchanged when it is locked or when all frames displaying it are unloaded. In `player_proximity` mode, it additionally needs a player inside its covered area.

`loaded_chunks` does more continuous work because it sweeps all 16,384 pixels of each distinct active map. At the ten-attempt-per-second ceiling, one completely loaded map needs at least about 27 minutes for a full sweep; multiple maps share that rate. Higher map scales make each individual pixel more expensive: scale 4 examines as many as 256 terrain columns for one pixel. If the neighboring pixel needed for height shading is unloaded, MapFrame uses neutral height shading for that pixel until a later sweep.

Showing the same map in many frames does not duplicate terrain sampling, but Minecraft still processes every item-frame entity and its normal network traffic. Avoid enormous frame walls, large numbers of unique maps, or permanently loaded scale-4 maps when using `loaded_chunks` on a busy server.

## 🔨 How to Build

You will need JDK 25 installed.

1. Clone the repository and open its directory:

   ```bash
   git clone https://github.com/kiLeo13/MapFrame.git
   cd MapFrame
   ```

2. Build the mod with the included Gradle wrapper:

   **Windows**

   ```powershell
   .\gradlew.bat build
   ```

   **Linux or macOS**

   ```bash
   ./gradlew build
   ```

3. Find the finished mod in `build/libs/`. Copy the `MapFrame-*.jar` file into your Fabric server's `mods` folder alongside Fabric API, then restart the server.
