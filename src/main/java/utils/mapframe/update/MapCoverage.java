package utils.mapframe.update;

import java.util.Objects;

/**
 * Immutable map bounds used by the spatial index.
 *
 * @param <D> dimension key type
 */
public record MapCoverage<D>(D dimension, int centerX, int centerZ, int scale) {
    public static final int MAP_PIXELS = 128;

    public MapCoverage {
        Objects.requireNonNull(dimension, "dimension");
        if (scale < 0 || scale > 4) {
            throw new IllegalArgumentException("Map scale must be between 0 and 4");
        }
    }

    public int blocksPerPixel() {
        return 1 << scale;
    }

    public int minBlockX() {
        return centerX - 65 * blocksPerPixel() + 1;
    }

    public int minBlockZ() {
        return centerZ - 65 * blocksPerPixel() + 1;
    }

    public int maxBlockXExclusive() {
        return centerX + 64 * blocksPerPixel();
    }

    public int maxBlockZExclusive() {
        return centerZ + 64 * blocksPerPixel();
    }

    public boolean contains(double x, double z) {
        int imageX = (int) Math.floor(x - centerX) / blocksPerPixel() + MAP_PIXELS / 2;
        int imageZ = (int) Math.floor(z - centerZ) / blocksPerPixel() + MAP_PIXELS / 2;
        return imageX >= 0 && imageX < MAP_PIXELS && imageZ >= 0 && imageZ < MAP_PIXELS;
    }
}
