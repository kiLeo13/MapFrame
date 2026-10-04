package utils.mapframe.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SpatialMapIndexTest {
    @Test
    void queriesByDimensionAndExactCoverage() {
        SpatialMapIndex<String, Integer> index = new SpatialMapIndex<>();
        index.put(1, new MapCoverage<>("overworld", 0, 0, 0));
        index.put(2, new MapCoverage<>("nether", 0, 0, 0));

        assertEquals(Set.of(1), index.query("overworld", 0, 0));
        assertEquals(Set.of(2), index.query("nether", 0, 0));
        assertTrue(index.query("overworld", 500, 500).isEmpty());
    }

    @Test
    void replacingAndRemovingValuesCleansOldCells() {
        SpatialMapIndex<String, Integer> index = new SpatialMapIndex<>();
        index.put(1, new MapCoverage<>("overworld", -512, -512, 0));
        index.put(1, new MapCoverage<>("overworld", 512, 512, 0));

        assertTrue(index.query("overworld", -512, -512).isEmpty());
        assertEquals(Set.of(1), index.query("overworld", 512, 512));

        index.remove(1);
        assertTrue(index.query("overworld", 512, 512).isEmpty());
        assertEquals(0, index.indexedValueCount());
    }
}
