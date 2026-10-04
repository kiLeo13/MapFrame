package utils.mapframe.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapCoverageTest {
    @Test
    void matchesVanillaIntegerDivisionAtMapEdges() {
        MapCoverage<String> coverage = new MapCoverage<>("overworld", 0, 0, 1);

        assertTrue(coverage.contains(-129, -129));
        assertTrue(coverage.contains(127.99, 127.99));
        assertFalse(coverage.contains(-130, 0));
        assertFalse(coverage.contains(128, 0));
    }

    @Test
    void rejectsInvalidScales() {
        assertThrows(IllegalArgumentException.class, () -> new MapCoverage<>("overworld", 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new MapCoverage<>("overworld", 0, 0, 5));
    }
}
