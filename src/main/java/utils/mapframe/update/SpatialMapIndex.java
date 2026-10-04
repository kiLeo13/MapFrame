package utils.mapframe.update;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A coarse spatial index that avoids comparing every active map with every player. */
public final class SpatialMapIndex<D, T> {
    static final int CELL_SIZE = 256;

    private final Map<CellKey<D>, Set<T>> cells = new HashMap<>();
    private final Map<T, IndexedValue<D>> values = new HashMap<>();

    public void put(T id, MapCoverage<D> coverage) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(coverage, "coverage");
        remove(id);

        Set<CellKey<D>> memberships = cellsFor(coverage);
        values.put(id, new IndexedValue<>(coverage, memberships));
        for (CellKey<D> cell : memberships) {
            cells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(id);
        }
    }

    public void remove(T id) {
        IndexedValue<D> removed = values.remove(id);
        if (removed == null) {
            return;
        }

        for (CellKey<D> cell : removed.cells()) {
            Set<T> ids = cells.get(cell);
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) {
                    cells.remove(cell);
                }
            }
        }
    }

    public Set<T> query(D dimension, double x, double z) {
        CellKey<D> cell = new CellKey<>(dimension, cellCoordinate(x), cellCoordinate(z));
        Set<T> candidates = cells.get(cell);
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptySet();
        }

        Set<T> result = new HashSet<>();
        for (T id : candidates) {
            IndexedValue<D> indexed = values.get(id);
            if (indexed != null && indexed.coverage().contains(x, z)) {
                result.add(id);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public void clear() {
        cells.clear();
        values.clear();
    }

    int indexedValueCount() {
        return values.size();
    }

    private static int cellCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private static <D> Set<CellKey<D>> cellsFor(MapCoverage<D> coverage) {
        int minCellX = Math.floorDiv(coverage.minBlockX(), CELL_SIZE);
        int minCellZ = Math.floorDiv(coverage.minBlockZ(), CELL_SIZE);
        int maxCellX = Math.floorDiv(coverage.maxBlockXExclusive() - 1, CELL_SIZE);
        int maxCellZ = Math.floorDiv(coverage.maxBlockZExclusive() - 1, CELL_SIZE);
        Set<CellKey<D>> result = new HashSet<>();

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                result.add(new CellKey<>(coverage.dimension(), cellX, cellZ));
            }
        }
        return result;
    }

    private record CellKey<D>(D dimension, int x, int z) {
    }

    private record IndexedValue<D>(MapCoverage<D> coverage, Set<CellKey<D>> cells) {
    }
}
