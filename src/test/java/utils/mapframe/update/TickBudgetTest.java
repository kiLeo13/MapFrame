package utils.mapframe.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TickBudgetTest {
    @Test
    void backsOffAndPausesAtConfiguredThresholds() {
        TickBudget budget = new TickBudget(1_000, 40_000, 45_000);

        assertEquals(1_000, budget.availableNanos(39_999));
        assertEquals(250, budget.availableNanos(40_000));
        assertEquals(0, budget.availableNanos(45_000));
    }

    @Test
    void rejectsOverlappingThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new TickBudget(1_000, 45_000, 45_000));
    }
}
