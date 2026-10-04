package utils.mapframe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.mapframe.config.MapFrameConfig;
import utils.mapframe.update.MapFrameUpdateService;

public class Mapframe implements ModInitializer {
    public static final String MOD_ID = "mapframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MapFrameConfig config = MapFrameConfig.load(
                FabricLoader.getInstance().getConfigDir().resolve(MapFrameConfig.FILE_NAME),
                LOGGER::warn
        );
        MapFrameUpdateService updateService = new MapFrameUpdateService(config.updateMode());
        ServerEntityEvents.ENTITY_LOAD.register(updateService::onEntityLoaded);
        ServerEntityEvents.ENTITY_UNLOAD.register(updateService::onEntityUnloaded);
        ServerTickEvents.END_SERVER_TICK.register(updateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(ignored -> updateService.clear());
        LOGGER.info("MapFrame initialized with update_mode={}", config.updateMode().configValue());
    }
}
