# 🗺️ MapFrame

In vanilla Minecraft, a filled map only updates its terrain while a player is holding it and exploring the area it covers. Maps displayed in item frames can therefore become outdated unless someone takes each map down and carries it around manually.

MapFrame keeps maps in loaded item frames updated whenever a player is inside their covered area. Updates are processed gradually, with strict limits to protect server TPS, and the mod never loads extra chunks just to refresh a map. It runs entirely on the server, so players do not need to install anything.

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
