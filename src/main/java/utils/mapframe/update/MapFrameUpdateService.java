package utils.mapframe.update;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import utils.mapframe.config.UpdateMode;

/**
 * Tracks maps in loaded item frames and incrementally samples their loaded terrain.
 * All methods run on the server thread; no world state is accessed asynchronously.
 */
public final class MapFrameUpdateService {
    private static final int MAX_FRAME_SCANS_PER_TICK = 128;
    private static final int UPDATE_INTERVAL_TICKS = 2;
    private static final int UPDATE_QUEUE_CAPACITY = 4096;
    private static final long MILLIS_IN_NANOS = 1_000_000L;
    private static final TickBudget TICK_BUDGET = new TickBudget(
            MILLIS_IN_NANOS,
            40 * MILLIS_IN_NANOS,
            45 * MILLIS_IN_NANOS
    );

    private final Map<UUID, TrackedFrame> frames = new HashMap<>();
    private final ArrayDeque<UUID> frameScanQueue = new ArrayDeque<>();
    private final Map<MapId, ActiveMap> activeMaps = new HashMap<>();
    private final SpatialMapIndex<ResourceKey<Level>, MapId> spatialIndex = new SpatialMapIndex<>();
    private final CoalescingQueue<PlayerMapUpdateJob> playerUpdateQueue = new CoalescingQueue<>(UPDATE_QUEUE_CAPACITY);
    private final CoalescingQueue<LoadedMapUpdateJob> loadedUpdateQueue = new CoalescingQueue<>(UPDATE_QUEUE_CAPACITY);
    private final UpdateMode updateMode;
    private final TickCadence updateCadence = new TickCadence(UPDATE_INTERVAL_TICKS);

    public MapFrameUpdateService() {
        this(UpdateMode.PLAYER_PROXIMITY);
    }

    public MapFrameUpdateService(UpdateMode updateMode) {
        this.updateMode = Objects.requireNonNull(updateMode, "updateMode");
    }

    public void onEntityLoaded(Entity entity, ServerLevel level) {
        if (!(entity instanceof ItemFrame frame)) {
            return;
        }

        UUID frameId = frame.getUUID();
        TrackedFrame previous = frames.put(frameId, new TrackedFrame(frame));
        if (previous != null) {
            removeActiveReference(previous);
        }
        frameScanQueue.addLast(frameId);
        refreshFrame(frames.get(frameId), level);
    }

    public void onEntityUnloaded(Entity entity, ServerLevel level) {
        if (entity instanceof ItemFrame frame) {
            unregisterFrame(frame.getUUID());
        }
    }

    public void tick(MinecraftServer server) {
        refreshFrames();
        if (!updateCadence.advance()) {
            return;
        }
        enqueueUpdates(server);
        processUpdates(server);
    }

    public void clear() {
        frames.clear();
        frameScanQueue.clear();
        activeMaps.clear();
        spatialIndex.clear();
        playerUpdateQueue.clear();
        loadedUpdateQueue.clear();
        updateCadence.reset();
    }

    private void refreshFrames() {
        int scans = Math.min(MAX_FRAME_SCANS_PER_TICK, frameScanQueue.size());
        for (int i = 0; i < scans; i++) {
            UUID frameId = frameScanQueue.removeFirst();
            TrackedFrame tracked = frames.get(frameId);
            if (tracked == null) {
                continue;
            }
            if (tracked.frame.isRemoved() || !(tracked.frame.level() instanceof ServerLevel level)) {
                unregisterFrame(frameId);
                continue;
            }

            refreshFrame(tracked, level);
            if (frames.containsKey(frameId)) {
                frameScanQueue.addLast(frameId);
            }
        }
    }

    private void refreshFrame(TrackedFrame tracked, ServerLevel level) {
        MapId currentMapId = tracked.frame.getFramedMapId(tracked.frame.getItem());
        if (!Objects.equals(currentMapId, tracked.mapId)) {
            removeActiveReference(tracked);
            tracked.mapId = currentMapId;
        }

        if (tracked.mapId != null && !tracked.hasActiveReference) {
            tracked.hasActiveReference = addActiveReference(level, tracked.mapId);
        }
    }

    private boolean addActiveReference(ServerLevel level, MapId mapId) {
        ActiveMap existing = activeMaps.get(mapId);
        if (existing != null) {
            existing.references++;
            return true;
        }

        MapItemSavedData data = level.getMapData(mapId);
        if (data == null) {
            return false;
        }

        MapCoverage<ResourceKey<Level>> coverage = new MapCoverage<>(
                data.dimension,
                data.centerX,
                data.centerZ,
                data.scale
        );
        activeMaps.put(mapId, new ActiveMap(coverage));
        spatialIndex.put(mapId, coverage);
        return true;
    }

    private void removeActiveReference(TrackedFrame tracked) {
        if (tracked.mapId == null || !tracked.hasActiveReference) {
            tracked.hasActiveReference = false;
            return;
        }

        ActiveMap activeMap = activeMaps.get(tracked.mapId);
        if (activeMap != null && --activeMap.references == 0) {
            activeMaps.remove(tracked.mapId);
            spatialIndex.remove(tracked.mapId);
        }
        tracked.hasActiveReference = false;
    }

    private void unregisterFrame(UUID frameId) {
        TrackedFrame removed = frames.remove(frameId);
        if (removed != null) {
            removeActiveReference(removed);
        }
    }

    private void enqueueUpdates(MinecraftServer server) {
        if (updateMode == UpdateMode.LOADED_CHUNKS) {
            for (Map.Entry<MapId, ActiveMap> entry : activeMaps.entrySet()) {
                loadedUpdateQueue.offer(new LoadedMapUpdateJob(entry.getValue().coverage.dimension(), entry.getKey()));
            }
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                Set<MapId> nearbyMaps = spatialIndex.query(level.dimension(), player.getX(), player.getZ());
                for (MapId mapId : nearbyMaps) {
                    if (!isHoldingMap(player, mapId)) {
                        playerUpdateQueue.offer(new PlayerMapUpdateJob(level.dimension(), mapId, player.getUUID()));
                    }
                }
            }
        }
    }

    private void processUpdates(MinecraftServer server) {
        long budgetNanos = TICK_BUDGET.availableNanos(server.getAverageTickTimeNanos());
        if (budgetNanos == 0) {
            return;
        }

        if (updateMode == UpdateMode.LOADED_CHUNKS) {
            processLoadedChunkUpdate(server);
        } else {
            processPlayerUpdate(server);
        }
    }

    private boolean processPlayerUpdate(MinecraftServer server) {
        PlayerMapUpdateJob job = playerUpdateQueue.poll();
        if (job == null) {
            return false;
        }
        ActiveMap activeMap = activeMaps.get(job.mapId());
        ServerLevel level = server.getLevel(job.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(job.playerId());
        if (activeMap == null || level == null || player == null || player.level() != level || player.isRemoved()) {
            return true;
        }
        if (!activeMap.coverage.contains(player.getX(), player.getZ()) || isHoldingMap(player, job.mapId())) {
            return true;
        }

        MapItemSavedData data = level.getMapData(job.mapId());
        if (data == null || data.locked || !isSamplingAreaLoaded(level, player, data)) {
            return true;
        }

        ((MapItem) Items.FILLED_MAP).update(level, player, data);
        return true;
    }

    private boolean processLoadedChunkUpdate(MinecraftServer server) {
        LoadedMapUpdateJob job = loadedUpdateQueue.poll();
        if (job == null) {
            return false;
        }

        ActiveMap activeMap = activeMaps.get(job.mapId());
        ServerLevel level = server.getLevel(job.dimension());
        if (activeMap == null || level == null) {
            return true;
        }

        MapItemSavedData data = level.getMapData(job.mapId());
        if (data == null || data.locked) {
            return true;
        }

        int imageX = activeMap.pixelCursor.imageX();
        int imageZ = activeMap.pixelCursor.imageZ();
        LoadedChunkMapSampler.updatePixel(level, data, imageX, imageZ);
        activeMap.pixelCursor.advance();
        return true;
    }

    private static boolean isHoldingMap(ServerPlayer player, MapId mapId) {
        return hasMapId(player.getMainHandItem(), mapId) || hasMapId(player.getOffhandItem(), mapId);
    }

    private static boolean hasMapId(ItemStack stack, MapId mapId) {
        return Objects.equals(stack.get(DataComponents.MAP_ID), mapId);
    }

    /**
     * Mirrors the bounds traversed by {@link MapItem#update} and refuses the update unless every
     * touched chunk is already loaded. This is what prevents map refreshes from loading terrain.
     */
    private static boolean isSamplingAreaLoaded(ServerLevel level, ServerPlayer player, MapItemSavedData data) {
        int scale = 1 << data.scale;
        int playerImageX = (int) Math.floor(player.getX() - data.centerX) / scale + 64;
        int playerImageZ = (int) Math.floor(player.getZ() - data.centerZ) / scale + 64;
        int radius = 128 / scale;
        if (level.dimensionType().hasCeiling()) {
            radius /= 2;
        }

        int minImageX = Math.max(0, playerImageX - radius + 1);
        int maxImageX = Math.min(127, playerImageX + radius - 1);
        int minImageZ = Math.max(-1, playerImageZ - radius - 1);
        int maxImageZ = Math.min(127, playerImageZ + radius - 1);
        if (minImageX > maxImageX || minImageZ > maxImageZ) {
            return false;
        }

        int scaledCenterX = data.centerX / scale;
        int scaledCenterZ = data.centerZ / scale;
        int minBlockX = (scaledCenterX + minImageX - 64) * scale;
        int maxBlockX = (scaledCenterX + maxImageX - 64) * scale + scale - 1;
        int minBlockZ = (scaledCenterZ + minImageZ - 64) * scale;
        int maxBlockZ = (scaledCenterZ + maxImageZ - 64) * scale + scale - 1;
        int minChunkX = SectionPos.blockToSectionCoord(minBlockX);
        int maxChunkX = SectionPos.blockToSectionCoord(maxBlockX);
        int minChunkZ = SectionPos.blockToSectionCoord(minBlockZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(maxBlockZ);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class TrackedFrame {
        private final ItemFrame frame;
        private MapId mapId;
        private boolean hasActiveReference;

        private TrackedFrame(ItemFrame frame) {
            this.frame = frame;
        }
    }

    private static final class ActiveMap {
        private final MapCoverage<ResourceKey<Level>> coverage;
        private final MapPixelCursor pixelCursor = new MapPixelCursor();
        private int references = 1;

        private ActiveMap(MapCoverage<ResourceKey<Level>> coverage) {
            this.coverage = coverage;
        }
    }

    private record PlayerMapUpdateJob(ResourceKey<Level> dimension, MapId mapId, UUID playerId) {
    }

    private record LoadedMapUpdateJob(ResourceKey<Level> dimension, MapId mapId) {
    }
}
