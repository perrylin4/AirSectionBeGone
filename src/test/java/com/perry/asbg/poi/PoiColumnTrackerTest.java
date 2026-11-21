package com.perry.asbg.poi;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PoiColumnTrackerTest {
    private final List<Long> unloaded = new ArrayList<>();
    private final AtomicInteger cleared = new AtomicInteger();
    private final PoiColumnTracker columns = new PoiColumnTracker(
            new LongOpenHashSet(), unloaded::add, cleared::incrementAndGet
    );

    @Test
    void vanillaLoadMarkerDoesNotPublishAnEmptyResult() {
        columns.add(10);
        assertFalse(columns.isReady(10));
        columns.finishLoad(10);
        assertTrue(columns.isReady(10));
        columns.beginLoad(10);
        assertTrue(columns.contains(10));
        assertFalse(columns.isReady(10));
    }

    @Test
    void c2meStyleRemovalInvalidatesEvenAnEmptyColumn() {
        columns.add(10);
        columns.finishLoad(10);
        assertTrue(columns.remove(10));
        assertFalse(columns.isReady(10));
        assertEquals(List.of(10L), unloaded);
        assertFalse(columns.remove(10));
        assertEquals(1, unloaded.size());
        columns.add(10);
        assertFalse(columns.isReady(10));
    }

    @Test
    void pendingLoadCompletionCanBePublishedBeforeVanillaAddsItsMarker() {
        columns.finishLoad(10);
        assertTrue(columns.isReady(10));
        assertFalse(columns.contains(10));
        columns.add(10);
        columns.remove(10);
        assertFalse(columns.isReady(10));
    }

    @Test
    void iteratorRemovalAlsoInvalidatesTheColumnIndex() {
        columns.add(10);
        columns.finishLoad(10);
        LongIterator iterator = columns.iterator();
        assertEquals(10, iterator.nextLong());
        iterator.remove();
        assertFalse(columns.isReady(10));
        assertEquals(List.of(10L), unloaded);
        assertThrows(IllegalStateException.class, iterator::remove);
        assertEquals(1, unloaded.size());
    }

    @Test
    void bulkRemovalAndClearDoNotLeaveCompletedColumnsBehind() {
        for (long column : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            columns.add(column);
            columns.finishLoad(column);
        }
        columns.removeIf((long column) -> column < 0);
        assertFalse(columns.isReady(-1));
        assertFalse(columns.isReady(Long.MIN_VALUE));
        assertEquals(2, unloaded.size());
        columns.clear();
        assertTrue(columns.isEmpty());
        assertFalse(columns.isReady(0));
        assertFalse(columns.isReady(Long.MAX_VALUE));
        assertEquals(1, cleared.get());
    }

    @Test
    void wrappedIteratorKeysDoNotNotifyUnloadTwice() {
        for (long column = 0; column < 10000; column++) {
            columns.add(column);
            columns.finishLoad(column);
        }
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long column = iterator.nextLong();
            iterator.remove();
            assertFalse(columns.isReady(column));
        }
        assertTrue(columns.isEmpty());
        assertEquals(10000, unloaded.size());
        assertEquals(10000, new HashSet<>(unloaded).size());
    }
}
