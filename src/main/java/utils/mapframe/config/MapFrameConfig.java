package utils.mapframe.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

public record MapFrameConfig(UpdateMode updateMode) {
    public static final String FILE_NAME = "mapframe.toml";
    public static final UpdateMode DEFAULT_UPDATE_MODE = UpdateMode.PLAYER_PROXIMITY;

    private static final String DEFAULT_FILE = """
            # Controls when maps displayed in loaded item frames are refreshed.
            #
            # Accepted values:
            #   "player_proximity" - A player must be inside the map area. This is the
            #                        safest and least expensive mode.
            #   "loaded_chunks"    - Updates loaded terrain covered by displayed maps
            #                        without requiring a player inside the map area.
            #
            # Invalid or missing values fall back to "player_proximity". Changes take
            # effect after a server restart.
            #
            # Performance warning for "loaded_chunks": large maps can cover thousands
            # of chunks, and many unique displayed maps multiply the sampling work.
            # Updates are deliberately delayed when necessary to protect server TPS.
            # MapFrame attempts at most 10 terrain updates per second in total, shared
            # by every active displayed map. A full 16,384-pixel sweep therefore takes
            # at least about 27 minutes for one map, and longer for multiple maps.
            # Scale-4 pixels are especially expensive: one can inspect 256 block columns.
            # Only already-loaded chunks are sampled; this mod never loads or generates
            # chunks to complete a map.
            update_mode = "player_proximity"
            """;

    public MapFrameConfig {
        Objects.requireNonNull(updateMode, "updateMode");
    }

    public static MapFrameConfig load(Path path, Consumer<String> warningLogger) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(warningLogger, "warningLogger");

        if (Files.notExists(path)) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, DEFAULT_FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                return defaults();
            } catch (IOException exception) {
                warningLogger.accept("Could not create " + path + ": " + exception.getMessage()
                        + "; using " + DEFAULT_UPDATE_MODE.configValue());
                return defaults();
            }
        }

        final TomlParseResult parsed;
        try {
            parsed = Toml.parse(path);
        } catch (IOException exception) {
            warningLogger.accept("Could not read " + path + ": " + exception.getMessage()
                    + "; using " + DEFAULT_UPDATE_MODE.configValue());
            return defaults();
        }

        if (parsed.hasErrors()) {
            for (TomlParseError error : parsed.errors()) {
                warningLogger.accept("Invalid TOML in " + path + ": " + error);
            }
            warningLogger.accept("Using " + DEFAULT_UPDATE_MODE.configValue() + " because the configuration is invalid");
            return defaults();
        }

        Object rawMode = parsed.get("update_mode");
        if (!(rawMode instanceof String modeValue)) {
            warningLogger.accept("update_mode must be a string; using " + DEFAULT_UPDATE_MODE.configValue());
            return defaults();
        }

        return UpdateMode.fromConfigValue(modeValue)
                .map(MapFrameConfig::new)
                .orElseGet(() -> {
                    warningLogger.accept("Unknown update_mode '" + modeValue + "'; using "
                            + DEFAULT_UPDATE_MODE.configValue());
                    return defaults();
                });
    }

    public static MapFrameConfig defaults() {
        return new MapFrameConfig(DEFAULT_UPDATE_MODE);
    }
}
