package utils.mapframe.update;

/** Converts current server load into an update budget for one tick. */
public record TickBudget(long normalBudgetNanos, long backoffAtNanos, long pauseAtNanos) {
    public TickBudget {
        if (normalBudgetNanos <= 0) {
            throw new IllegalArgumentException("Normal budget must be positive");
        }
        if (backoffAtNanos < 0 || pauseAtNanos <= backoffAtNanos) {
            throw new IllegalArgumentException("MSPT thresholds are invalid");
        }
    }

    public long availableNanos(long averageTickNanos) {
        if (averageTickNanos >= pauseAtNanos) {
            return 0;
        }
        if (averageTickNanos >= backoffAtNanos) {
            return Math.max(1, normalBudgetNanos / 4);
        }
        return normalBudgetNanos;
    }
}
