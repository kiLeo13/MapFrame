# Repository Guide

## Scope

MapFrame is a server-only Fabric mod. Keep gameplay logic under `src/main`, pure unit tests under `src/test`, and Minecraft server integration tests under `src/gametest`.

## Required checks

Behavior changes require unit tests and, when Minecraft integration changes, a Fabric GameTest. Before considering work complete, run:

```powershell
.\gradlew.bat test runGameTest
.\gradlew.bat clean build
```

Keep `ARCHITECTURE.md` synchronized with runtime behavior, performance limits, persistence, and operational constraints.

## Performance and safety invariants

- Never force-load or generate chunks for map updates.
- Access Minecraft worlds and entities only from the server thread.
- Keep pending work bounded and coalesced.
- Preserve MSPT backpressure and a finite per-tick attempt cap.
- Reuse vanilla `MapItemSavedData` for pixels and persistence; do not add a database without a demonstrated cross-server or history requirement.
- Do not update locked maps or duplicate vanilla work for a map already held by the same player.

## Build conventions

- Use the checked-in Gradle wrapper and Java 25.
- Keep Fabric/Minecraft versions aligned with the official Fabric 26.3 example project.
- Treat deprecation warnings in production source as porting work, not background noise.
- Do not perform remote Git operations unless explicitly requested.
