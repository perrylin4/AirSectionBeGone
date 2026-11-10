package com.perry.asbg.section;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

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
    public int onResize(int bits, T value) {
        throw immutableFailure();
    }

    @Override
    public T getAndSet(int x, int y, int z, T value) {
        throw immutableFailure();
    }

    @Override
    public T getAndSetUnchecked(int x, int y, int z, T value) {
        throw immutableFailure();
    }

    @Override
    public void set(int x, int y, int z, T value) {
        throw immutableFailure();
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
        throw immutableFailure();
    }

    @Override
    public PalettedContainer<T> copy() {
        return super.copy();
    }

    @Override
    public PalettedContainer<T> recreate() {
        return super.recreate();
    }

    private static UnsupportedOperationException immutableFailure() {
        return new UnsupportedOperationException(
                "Attempted to modify an AirSectionBeGone shared immutable PalettedContainer"
        );
    }
}
