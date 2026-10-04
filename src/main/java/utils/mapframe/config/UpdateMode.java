package utils.mapframe.config;

import java.util.Arrays;
import java.util.Optional;

public enum UpdateMode {
    PLAYER_PROXIMITY("player_proximity"),
    LOADED_CHUNKS("loaded_chunks");

    private final String configValue;

    UpdateMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<UpdateMode> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.configValue.equals(value))
                .findFirst();
    }
}
