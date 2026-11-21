package com.perry.asbg.poi;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.LongConsumer;

/** Keeps completed POI loads separate from vanilla's in-progress load markers. */
public final class PoiColumnTracker extends LongOpenHashSet {
    private final LongOpenHashSet readyColumns = new LongOpenHashSet();
    private final LongConsumer onUnload;
    private final Runnable onClear;
    private long unloadVersion;

    public static @Nullable PoiColumnTracker forStorage(SectionStorage<?, ?> storage, LongSet loadedChunks) {
        if (!(storage instanceof PoiManager poi) || loadedChunks.getClass() != LongOpenHashSet.class) {
            return null;
        }
        return new PoiColumnTracker(loadedChunks, column -> LithiumPoiColumns.unloaded(poi, column),
                () -> LithiumPoiColumns.clear(poi));
    }

    public PoiColumnTracker(LongCollection loadedColumns, LongConsumer onUnload, Runnable onClear) {
        super(loadedColumns);
        this.onUnload = Objects.requireNonNull(onUnload);
        this.onClear = Objects.requireNonNull(onClear);
    }

    public void beginLoad(long column) {
        this.readyColumns.remove(column);
    }

    public void finishLoad(long column) {
        this.readyColumns.add(column);
    }

    public boolean isReady(long column) {
        return this.readyColumns.contains(column);
    }

    @Override
    public boolean remove(long column) {
        boolean removed = super.remove(column);
        if (removed || this.readyColumns.contains(column)) {
            this.columnRemoved(column);
        }
        return removed;
    }

    @Override
    public @NonNull LongIterator iterator() {
        LongIterator delegate = super.iterator();
        return new LongIterator() {
            private long current;

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public long nextLong() {
                return this.current = delegate.nextLong();
            }

            @Override
            public void remove() {
                long version = unloadVersion;
                delegate.remove();
                // For wrapped keys, fastutil may already have called remove(long).
                if (version == unloadVersion) {
                    columnRemoved(this.current);
                }
            }
        };
    }

    private void columnRemoved(long column) {
        this.readyColumns.remove(column);
        this.unloadVersion++;
        this.onUnload.accept(column);
    }

    @Override
    public void clear() {
        super.clear();
        this.readyColumns.clear();
        this.onClear.run();
    }
}
