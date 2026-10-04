package utils.mapframe.update;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Samples one map pixel at a time without requesting an unloaded chunk. */
final class LoadedChunkMapSampler {
    private LoadedChunkMapSampler() {
    }

    static boolean updatePixel(ServerLevel level, MapItemSavedData data, int imageX, int imageZ) {
        int blocksPerPixel = 1 << data.scale;
        int blockX = (data.centerX / blocksPerPixel + imageX - 64) * blocksPerPixel;
        int blockZ = (data.centerZ / blocksPerPixel + imageZ - 64) * blocksPerPixel;
        AreaSample current = sampleArea(level, data, blockX, blockZ, blocksPerPixel);
        if (current == null) {
            return false;
        }

        AreaSample previous = sampleArea(level, data, blockX, blockZ - blocksPerPixel, blocksPerPixel);
        double previousHeight = previous == null ? current.averageHeight() : previous.averageHeight();
        MapColor.Brightness brightness = brightness(
                current.color(),
                current.averageHeight(),
                previousHeight,
                current.waterDepth(),
                blocksPerPixel,
                imageX,
                imageZ
        );
        data.updateColor(imageX, imageZ, current.color().getPackedId(brightness));
        return true;
    }

    private static AreaSample sampleArea(
            ServerLevel level,
            MapItemSavedData data,
            int minBlockX,
            int minBlockZ,
            int blocksPerPixel
    ) {
        if (!isAreaLoaded(level, minBlockX, minBlockZ, blocksPerPixel)) {
            return null;
        }

        LevelChunk chunk = level.getChunk(
                SectionPos.blockToSectionCoord(minBlockX),
                SectionPos.blockToSectionCoord(minBlockZ)
        );
        if (chunk.isEmpty()) {
            return null;
        }

        if (level.dimensionType().hasCeiling()) {
            int noise = minBlockX + minBlockZ * 231871;
            noise = noise * noise * 31287121 + noise * 11;
            MapColor color = (noise >> 20 & 1) == 0
                    ? Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO)
                    : Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO);
            return new AreaSample(color, 100.0, 0);
        }

        Map<MapColor, Integer> colorCounts = new LinkedHashMap<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPosition = new BlockPos.MutableBlockPos();
        double averageHeight = 0.0;
        int waterDepth = 0;

        for (int deltaX = 0; deltaX < blocksPerPixel; deltaX++) {
            for (int deltaZ = 0; deltaZ < blocksPerPixel; deltaZ++) {
                int x = minBlockX + deltaX;
                int z = minBlockZ + deltaZ;
                LevelChunk columnChunk = level.getChunk(
                        SectionPos.blockToSectionCoord(x),
                        SectionPos.blockToSectionCoord(z)
                );
                position.set(x, 0, z);
                int columnY = columnChunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                BlockState state;
                if (columnY <= level.getMinY()) {
                    state = Blocks.BEDROCK.defaultBlockState();
                } else {
                    do {
                        position.setY(--columnY);
                        state = columnChunk.getBlockState(position);
                    } while (state.getMapColor(level, position) == MapColor.NONE && columnY > level.getMinY());

                    if (columnY > level.getMinY() && !state.getFluidState().isEmpty()) {
                        int solidY = columnY - 1;
                        belowPosition.set(position);
                        BlockState belowBlock;
                        do {
                            belowPosition.setY(solidY--);
                            belowBlock = columnChunk.getBlockState(belowPosition);
                            waterDepth++;
                        } while (solidY > level.getMinY() && !belowBlock.getFluidState().isEmpty());
                        state = correctStateForFluidBlock(level, state, position);
                    }
                }

                data.checkBanners(level, x, z);
                averageHeight += (double) columnY / (blocksPerPixel * blocksPerPixel);
                colorCounts.merge(state.getMapColor(level, position), 1, Integer::sum);
            }
        }

        MapColor mostCommonColor = MapColor.NONE;
        int highestCount = -1;
        for (Map.Entry<MapColor, Integer> entry : colorCounts.entrySet()) {
            if (entry.getValue() > highestCount) {
                mostCommonColor = entry.getKey();
                highestCount = entry.getValue();
            }
        }
        return new AreaSample(mostCommonColor, averageHeight, waterDepth / (blocksPerPixel * blocksPerPixel));
    }

    private static boolean isAreaLoaded(ServerLevel level, int minBlockX, int minBlockZ, int size) {
        int minChunkX = SectionPos.blockToSectionCoord(minBlockX);
        int maxChunkX = SectionPos.blockToSectionCoord(minBlockX + size - 1);
        int minChunkZ = SectionPos.blockToSectionCoord(minBlockZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(minBlockZ + size - 1);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockState correctStateForFluidBlock(ServerLevel level, BlockState state, BlockPos position) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, position, Direction.UP)
                ? fluidState.createLegacyBlock()
                : state;
    }

    private static MapColor.Brightness brightness(
            MapColor color,
            double averageHeight,
            double previousAverageHeight,
            int waterDepth,
            int blocksPerPixel,
            int imageX,
            int imageZ
    ) {
        double difference;
        if (color == MapColor.WATER) {
            difference = waterDepth * 0.1 + ((imageX + imageZ) & 1) * 0.2;
            if (difference < 0.5) {
                return MapColor.Brightness.HIGH;
            }
            if (difference > 0.9) {
                return MapColor.Brightness.LOW;
            }
            return MapColor.Brightness.NORMAL;
        }

        difference = (averageHeight - previousAverageHeight) * 4.0 / (blocksPerPixel + 4)
                + (((imageX + imageZ) & 1) - 0.5) * 0.4;
        if (difference > 0.6) {
            return MapColor.Brightness.HIGH;
        }
        if (difference < -0.6) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    private record AreaSample(MapColor color, double averageHeight, int waterDepth) {
    }
}
