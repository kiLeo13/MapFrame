package utils.mapframe.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapPixelCursorTest {
    @Test
    void traversesColumnsAndWraps() {
        MapPixelCursor cursor = new MapPixelCursor();

        assertEquals(0, cursor.imageX());
        assertEquals(0, cursor.imageZ());
        cursor.advance();
        assertEquals(0, cursor.imageX());
        assertEquals(1, cursor.imageZ());

        for (int i = 1; i < MapCoverage.MAP_PIXELS; i++) {
            cursor.advance();
        }
        assertEquals(1, cursor.imageX());
        assertEquals(0, cursor.imageZ());

        for (int i = MapCoverage.MAP_PIXELS; i < MapCoverage.MAP_PIXELS * MapCoverage.MAP_PIXELS; i++) {
            cursor.advance();
        }
        assertEquals(0, cursor.imageX());
        assertEquals(0, cursor.imageZ());
    }
}
