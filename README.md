# 🗺️ MapFrame

In vanilla Minecraft, a filled map only updates its terrain while a player is holding it and exploring the area it covers. Maps displayed in item frames can therefore become outdated unless someone takes each map down and carries it around manually.

MapFrame keeps maps in loaded item frames updated whenever a player is inside their covered area. Updates are processed gradually, with strict limits to protect server TPS, and the mod never loads extra chunks just to refresh a map. It runs entirely on the server, so players do not need to install anything.

## ⚠️ Limits at Scale

MapFrame is designed to protect server performance when it becomes overloaded. It normally spends no more than 1 ms per tick on map updates, attempts at most four updates per tick, slows down when the server reaches 40 MSPT, and pauses updates at 45 MSPT. Under extreme load, maps will update more slowly instead of consuming unlimited tick time or memory.

The biggest workload comes from many unique maps covering the same busy area, because every player and map combination needs its own sampling work. Showing the same map in several frames does not duplicate MapFrame's sampling, but Minecraft still has to process every item-frame entity and send its normal network updates. Huge map walls can therefore still affect entity processing, bandwidth, and world-save times.

Maps and item frames must already be loaded. MapFrame never force-loads chunks, so a low view distance or a player near the edge of loaded terrain may temporarily prevent updates. A map also remains unchanged when it is locked, when all frames displaying it are unloaded, or when no player is inside the area it covers.

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
