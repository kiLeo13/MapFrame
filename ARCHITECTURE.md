# MapFrame Architecture

## Purpose

MapFrame is a server-side Fabric mod for Minecraft 26.3. A vanilla filled map normally samples terrain only while a player holds it. MapFrame samples maps displayed in loaded item frames according to `config/mapframe.toml`:

- `player_proximity` requires a player inside the map's covered area and dimension, then delegates to the vanilla sampler;
- `loaded_chunks` incrementally samples loaded pixel footprints without requiring a player inside the map area.

Both modes require at least one loaded item frame displaying the map, ignore locked maps, and refuse to load or generate terrain. Player-proximity mode also avoids duplicating vanilla work when the selected player is already holding the same map.

This keeps displayed maps current without introducing client mods, duplicate image storage, or forced chunk loads.

## Runtime flow

1. Fabric entity lifecycle events add and remove loaded item frames from `MapFrameUpdateService`.
2. Frame contents are reconciled through a bounded round-robin scan. This catches maps inserted into or removed from an already-loaded frame without a mixin.
3. Each distinct displayed map is reference-counted. Its immutable dimension, center, and scale are stored as `MapCoverage`.
4. `SpatialMapIndex` places coverage in 256-by-256-block cells. A scale-four map occupies at most 81 cells rather than thousands of chunk keys.
5. Every two ticks, the configured mode submits work to its bounded `CoalescingQueue`.
6. In `player_proximity`, each player queries only their current spatial cell. A validated map/player job calls Minecraft's native `MapItem.update` sampler.
7. In `loaded_chunks`, each distinct active map has a persistent 128-by-128 pixel cursor. A validated map job samples one pixel footprint and advances the cursor whether that footprint is loaded or skipped.
8. Vanilla `MapItemSavedData` records changed pixels and persistence state. Vanilla item-frame tracking sends dirty rectangular patches to clients every ten ticks.

All Minecraft world access occurs on the server thread. The mod does not copy mutable world state to worker threads.

## Performance contract

The defaults are deliberately conservative:

- soft map-work budget: 1 ms per eligible update tick;
- maximum update attempts: 1 every 2 ticks, or 10 per second at 20 TPS;
- frame-content checks: 128 per tick;
- pending-job capacity: 4,096 unique player/map or loaded-map jobs;
- backoff begins at 40 ms average server tick time;
- map work pauses at 45 ms average server tick time.

The time budget is soft because a vanilla sampler call or loaded pixel sample is non-preemptible. Once an attempt starts, it must finish. Successful, skipped, and stale jobs all consume that scheduling opportunity.

Before player-proximity sampling, MapFrame computes the exact block rectangle traversed by the vanilla algorithm and checks every corresponding chunk with `hasChunk`. Loaded-chunk sampling checks the target pixel footprint before reading it. Missing chunks cancel or skip the attempt. MapFrame never calls chunk loading or generation APIs in production code.

Vanilla distributes a player-proximity refresh across 16 sampler steps. At ten calls per second, a single active map can complete that cycle in about 1.6 seconds under ideal conditions, plus the item-frame packet interval. More active maps share the global attempt rate.

Loaded-chunk mode reproduces vanilla terrain-color, fluid-depth, height-shading, ceiling-dimension, and banner sampling one pixel at a time. It requires only the target footprint to be loaded. When the previous pixel needed for height shading is unavailable, it uses the current height as a neutral fallback instead of loading the neighboring chunk. A complete sweep is 16,384 attempts: at the global ceiling, one active map needs at least about 27 minutes, and additional maps increase that time. At scale 4, one pixel represents up to 256 terrain columns, so the nominal 1 ms budget cannot guarantee that an individual attempt finishes inside 1 ms.

## Configuration

`MapFrameConfig` loads `config/mapframe.toml` once during initialization. A missing file is created with explanatory comments and `update_mode = "player_proximity"`. Accepted values are exactly `player_proximity` and `loaded_chunks`.

A missing option, wrong TOML type, unknown value, read failure, or parse error logs a warning and uses `player_proximity` for that server run. Invalid files are not rewritten. Configuration changes require a server restart. The generated TOML, README, and this architecture document state the same fallback and performance contract.

## Caching and persistence

The following state is ephemeral and rebuilt from loaded entities:

- loaded item-frame references;
- per-map frame reference counts;
- spatial-index cells; and
- pending update jobs.

Pixel data is not stored in a database. `MapItemSavedData` is Minecraft's authoritative in-memory and on-disk representation, including color bytes, decorations, dirty rectangles, and save integration. A second database would create consistency and write-amplification problems without improving live updates.

Only loaded item frames activate a map. If every frame displaying a map unloads, MapFrame stops sampling it. Unloaded terrain remains unchanged until Minecraft loads it naturally.

## Source layout

- `utils.mapframe.Mapframe`: Fabric entrypoint and lifecycle-event wiring.
- `utils.mapframe.config.MapFrameConfig`: TOML creation, parsing, validation, and safe fallback.
- `utils.mapframe.config.UpdateMode`: accepted configuration values.
- `utils.mapframe.update.MapFrameUpdateService`: frame registry, scheduling, safety checks, and vanilla sampler integration.
- `utils.mapframe.update.LoadedChunkMapSampler`: player-independent, loaded-only pixel sampling.
- `utils.mapframe.update.MapPixelCursor`: full-map incremental sweep state.
- `utils.mapframe.update.MapCoverage`: vanilla-compatible coverage calculations.
- `utils.mapframe.update.SpatialMapIndex`: coarse dimension-aware lookup cache.
- `utils.mapframe.update.CoalescingQueue`: bounded deduplicating FIFO work queue.
- `utils.mapframe.update.TickBudget`: TPS backpressure policy.

## Testing

Run all unit and server integration tests:

```powershell
.\gradlew.bat test runGameTest
```

Run the complete build, which also compiles the GameTest source set:

```powershell
.\gradlew.bat clean build
```

Unit tests cover configuration parsing and fallback, update cadence, pixel cursor wrapping, vanilla edge arithmetic, dimension-aware spatial lookup, replacement/removal cleanup, queue coalescing/capacity, and tick-budget thresholds. Fabric GameTests exercise both update modes on a real 26.3 server and verify that loaded-chunk mode updates without a nearby player while leaving remote chunks unloaded.

## Known boundaries

- MapFrame updates terrain colors, not unexplored chunks. It will not generate terrain for a wall map.
- A very low server view distance may leave part of vanilla's sampling radius unloaded; those attempts are skipped.
- Locked maps intentionally remain unchanged.
- Sampler attempts are atomic, so the 1 ms budget cannot interrupt unusually slow work.
- Loaded-chunk mode performs continuous sweeps rather than listening to every block mutation. Updates are eventual, not immediate.
- A full loaded-chunk sweep takes at least about 27 minutes per distinct map at the default global rate, and multiple maps share that capacity.
- Scale-4 pixels are the most expensive because each represents up to 256 block columns.
- All Minecraft state remains on the server thread. Virtual threads do not make CPU-bound sampling faster and would make mutable world access unsafe.

## Version matrix

- Minecraft: 26.3
- Java: 25
- Gradle Wrapper: 9.7.1
- Fabric Loader: 0.19.5
- Fabric Loom: 1.18.x (`1.18-SNAPSHOT` resolves to the current compatible release)
- Fabric API: 0.161.0+26.3
