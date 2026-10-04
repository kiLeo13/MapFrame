package utils.mapframe.update;

final class TickCadence {
    private final int intervalTicks;
    private int elapsedTicks;

    TickCadence(int intervalTicks) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be positive");
        }
        this.intervalTicks = intervalTicks;
    }

    boolean advance() {
        elapsedTicks++;
        if (elapsedTicks < intervalTicks) {
            return false;
        }
        elapsedTicks = 0;
        return true;
    }

    void reset() {
        elapsedTicks = 0;
    }
}
