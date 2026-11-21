package com.perry.asbg.section;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.jspecify.annotations.NonNull;

/**
 * A zero-bit paletted container whose contents can be read and copied but never changed.
 */
public final class ImmutablePalettedContainer<T> extends PalettedContainer<T> {
    public ImmutablePalettedContainer(T value, Strategy<T> strategy) {
        super(value, strategy);
    }

    @Override
    public void acquire() {
        // Immutable reads need no ownership lock. Lithium also removes this lock from normal containers.
    }

    @Override
    public void release() {
        // Paired with acquire().
    }

    @Override
    public int onResize(int bits, @NonNull T value) {
        throw immutableFailure();
    }

    @Override
    public @NonNull T getAndSet(int x, int y, int z, @NonNull T value) {
        if (get(x, y, z) == value) {
            return value;
        }
        throw immutableFailure();
    }

    @Override
    public @NonNull T getAndSetUnchecked(int x, int y, int z, @NonNull T value) {
        if (get(x, y, z) == value) {
            return value;
        }
        throw immutableFailure();
    }

    @Override
    public void set(int x, int y, int z, @NonNull T value) {
        if (get(x, y, z) == value) {
            return;
        }
        throw immutableFailure();
    }

    @Override
    public void read(@NonNull FriendlyByteBuf buffer) {
        throw immutableFailure();
    }

    @Override
    public @NonNull PalettedContainer<T> copy() {
        // SingleValuePalette.copy() returns itself. A normal copy could therefore
        // mutate our palette through read(); recreate gives the uniform copy its own palette.
        return super.recreate();
    }

    @Override
    public @NonNull PalettedContainer<T> recreate() {
        return super.recreate();
    }

    private static UnsupportedOperationException immutableFailure() {
        return new UnsupportedOperationException(
                "Attempted to modify an AirSectionBeGone shared immutable PalettedContainer"
        );
    }
}
