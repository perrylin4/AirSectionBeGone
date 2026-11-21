package com.perry.asbg.poi;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.BitSet;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.jspecify.annotations.Nullable;

/** Accessed only at column load/unload boundaries, never during a POI query. */
public final class LithiumPoiColumns {
    private LithiumPoiColumns() {
    }

    public static void loaded(SectionStorage<?, ?> storage, long column) {
        Long2ObjectMap<BitSet> columns = columns(storage);
        if (columns != null && !columns.containsKey(column)) {
            columns.put(column, new BitSet(0));
        }
    }

    public static void unloaded(SectionStorage<?, ?> storage, long column) {
        Long2ObjectMap<BitSet> columns = columns(storage);
        if (columns != null) {
            columns.remove(column);
        }
    }

    public static void clear(SectionStorage<?, ?> storage) {
        Long2ObjectMap<BitSet> columns = columns(storage);
        if (columns != null) {
            columns.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Long2ObjectMap<BitSet> columns(SectionStorage<?, ?> storage) {
        if (!(storage instanceof RegionBasedStorageSectionExtended<?>)) {
            return null; // Lithium's ai.poi mixins may be disabled independently.
        }
        try {
            Object value = ColumnField.VALUE.get(storage);
            if (!(value instanceof Long2ObjectMap<?>)) {
                throw new IllegalStateException("Lithium's POI column index is not initialized");
            }
            return (Long2ObjectMap<BitSet>) value;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access Lithium's POI column index", exception);
        }
    }

    private static final class ColumnField {
        // Resolve after SectionStorage has been transformed. An unconditional Mixin
        // accessor would fail when Lithium's ai.poi mixins are disabled.
        private static final Field VALUE = resolve();

        @SuppressWarnings("JavaReflectionMemberAccess")
        private static Field resolve() {
            try {
                // Lithium's SectionStorageMixin adds this unmapped field at runtime.
                Field field = SectionStorage.class.getDeclaredField("columns");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported Lithium POI column index", exception);
            }
        }
    }
}
