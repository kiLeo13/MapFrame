# MapFrame Architecture

## Purpose

MapFrame is a server-side Fabric mod for Minecraft 26.3. A vanilla filled map normally samples terrain only while a player holds it. MapFrame also samples a map when all of these conditions are true:

- at least one loaded item frame displays the map;
- a player is inside that map's covered area and dimension;
- the map is unlocked;
- the player is not already holding that same map; and
- every chunk the vanilla sampler may read is already loaded.

This keeps displayed maps current without introducing client mods, duplicate image storage, or forced chunk loads.

## Runtime flow

1. Fabric entity lifecycle events add and remove loaded item frames from `MapFrameUpdateService`.
2. Frame contents are reconciled through a bounded round-robin scan. This catches maps inserted into or removed from an already-loaded frame without a mixin.
3. Each distinct displayed map is reference-counted. Its immutable dimension, center, and scale are stored as `MapCoverage`.
4. `SpatialMapIndex` places coverage in 256-by-256-block cells. A scale-four map occupies at most 81 cells rather than thousands of chunk keys.
5. At the end of a server tick, each player queries only their current spatial cell. Matching map/player pairs enter a bounded `CoalescingQueue`.
6. `MapFrameUpdateService` validates the job and calls Minecraft's native `MapItem.update` sampler.
7. Vanilla `MapItemSavedData` marks changed pixels and persistence state dirty. Vanilla item-frame tracking sends dirty rectangular patches to clients every ten ticks.

All Minecraft world access occurs on the server thread. The mod does not copy mutable world state to worker threads.

## Performance contract

The defaults are deliberately conservative:

- normal map work budget: 1 ms per server tick;
- maximum sampler calls: 4 per tick;
- frame-content checks: 128 per tick;
- pending-job capacity: 4,096 unique map/player pairs;
- backoff begins at 40 ms average server tick time;
- map work pauses at 45 ms average server tick time.

The time budget is soft because the vanilla sampler is one non-preemptible call. Once a sampler call starts, it must finish; no additional call starts after the budget expires. Both successful and stale job attempts count toward the four-attempt cap.

Before sampling, MapFrame computes the exact block rectangle traversed by the vanilla algorithm and checks every corresponding chunk with `hasChunk`. A missing chunk cancels that attempt. MapFrame never calls chunk loading or generation APIs in production code.

Vanilla distributes a map refresh across 16 sampler steps. Under light load a single active map normally completes a sampling cycle in roughly 16 ticks, plus the vanilla item-frame packet interval. More active maps increase latency through fair FIFO scheduling rather than increasing tick cost.

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
- `utils.mapframe.update.MapFrameUpdateService`: frame registry, scheduling, safety checks, and vanilla sampler integration.
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

Unit tests cover vanilla edge arithmetic, dimension-aware spatial lookup, replacement/removal cleanup, queue coalescing/capacity, and tick-budget thresholds. The Fabric GameTest boots a real 26.3 server, creates an item-frame map, leaves the player's hands empty, runs the scheduler, and verifies that terrain colors change and the vanilla map data is marked dirty for saving.

## Known boundaries

- MapFrame updates terrain colors, not unexplored chunks. It will not generate terrain for a wall map.
- A very low server view distance may leave part of vanilla's sampling radius unloaded; those attempts are skipped.
- Locked maps intentionally remain unchanged.
- The vanilla sampler is atomic, so the 1 ms budget cannot interrupt an individual unusually slow call.
- Update limits are constants for now. A validated configuration file can be added if deployment measurements show different defaults are needed.

## Version matrix

- Minecraft: 26.3
- Java: 25
- Gradle Wrapper: 9.7.1
- Fabric Loader: 0.19.5
- Fabric Loom: 1.18.x (`1.18-SNAPSHOT` resolves to the current compatible release)
- Fabric API: 0.161.0+26.3
