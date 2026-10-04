package utils.mapframe.update;

final class MapPixelCursor {
    private int index;

    int imageX() {
        return index / MapCoverage.MAP_PIXELS;
    }

    int imageZ() {
        return index % MapCoverage.MAP_PIXELS;
    }

    void advance() {
        index = (index + 1) % (MapCoverage.MAP_PIXELS * MapCoverage.MAP_PIXELS);
    }
}
