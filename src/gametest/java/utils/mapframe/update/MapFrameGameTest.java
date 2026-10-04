package utils.mapframe.update;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class MapFrameGameTest implements CustomTestMethodInvoker {
    @GameTest(maxTicks = 200)
    @SuppressWarnings("removal") // The scheduler resolves players through PlayerList, so this test needs Fabric's logged-in mock.
    public void framedMapUpdatesWithoutBeingHeld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos framePos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos playerPos = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlockAndUpdate(framePos.relative(Direction.NORTH), Blocks.STONE.defaultBlockState());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);

        loadSamplingChunks(level, playerPos);
        ItemStack mapStack = MapItem.create(level, playerPos.getX(), playerPos.getZ(), (byte) 0, true, false);
        MapId mapId = mapStack.get(DataComponents.MAP_ID);
        helper.assertTrue(mapId != null, "Created map must have an id");

        ItemFrame frame = new ItemFrame(level, framePos, Direction.SOUTH);
        frame.setItem(mapStack);
        helper.assertTrue(level.addFreshEntity(frame), "Item frame must be added to the test level");

        MapFrameUpdateService service = new MapFrameUpdateService();
        service.onEntityLoaded(frame, level);
        for (int i = 0; i < 20; i++) {
            service.tick(level.getServer());
        }

        MapItemSavedData data = level.getMapData(mapId);
        helper.assertTrue(data != null, "Map data must remain registered in server saved data");
        helper.assertTrue(Arrays.stream(toInts(data.colors)).anyMatch(color -> color != 0),
                "A displayed map must sample terrain even when the player is not holding it");
        helper.assertTrue(data.isDirty(), "Updated map data must be marked for world-save persistence");
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }

    private static void loadSamplingChunks(ServerLevel level, BlockPos center) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int chunkX = centerChunkX - 9; chunkX <= centerChunkX + 9; chunkX++) {
            for (int chunkZ = centerChunkZ - 9; chunkZ <= centerChunkZ + 9; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static int[] toInts(byte[] colors) {
        int[] result = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            result[i] = Byte.toUnsignedInt(colors[i]);
        }
        return result;
    }
}
