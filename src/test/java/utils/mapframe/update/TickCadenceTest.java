package utils.mapframe.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TickCadenceTest {
    @Test
    void allowsOneRunEveryConfiguredNumberOfTicks() {
        TickCadence cadence = new TickCadence(2);

        assertFalse(cadence.advance());
        assertTrue(cadence.advance());
        assertFalse(cadence.advance());
        assertTrue(cadence.advance());
    }

    @Test
    void resetRestartsTheInterval() {
        TickCadence cadence = new TickCadence(2);

        assertFalse(cadence.advance());
        cadence.reset();

        assertFalse(cadence.advance());
        assertTrue(cadence.advance());
    }
}
