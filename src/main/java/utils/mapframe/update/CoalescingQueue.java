package utils.mapframe.update;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;

/** Bounded FIFO queue that stores at most one copy of each pending job. */
public final class CoalescingQueue<T> {
    private final int capacity;
    private final LinkedHashSet<T> entries = new LinkedHashSet<>();

    public CoalescingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.capacity = capacity;
    }

    public boolean offer(T value) {
        Objects.requireNonNull(value, "value");
        if (entries.contains(value)) {
            return true;
        }
        return entries.size() < capacity && entries.add(value);
    }

    public T poll() {
        Iterator<T> iterator = entries.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        T value = iterator.next();
        iterator.remove();
        return value;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
