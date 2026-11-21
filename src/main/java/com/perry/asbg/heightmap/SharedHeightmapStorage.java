package com.perry.asbg.heightmap;

import java.util.Arrays;
import net.minecraft.util.SimpleBitStorage;
import org.jspecify.annotations.NonNull;

/** An immutable snapshot using the same packed layout and reads as vanilla. */
public final class SharedHeightmapStorage extends SimpleBitStorage {
    private final int hash;

    SharedHeightmapStorage(SimpleBitStorage source) {
        super(source.getBits(), source.getSize(), source.getRaw().clone());
        this.hash = 31 * (31 * getBits() + getSize()) + Arrays.hashCode(backing());
    }

    @Override
    public void set(int index, int value) {
        getAndSet(index, value);
    }

    @Override
    public int getAndSet(int index, int value) {
        int current = get(index);
        if (current != value) {
            throw new UnsupportedOperationException("Cannot change a shared heightmap");
        }
        return current;
    }

    @Override
    public long @NonNull [] getRaw() {
        return backing().clone();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof SharedHeightmapStorage other
                && hash == other.hash
                && getBits() == other.getBits()
                && getSize() == other.getSize()
                && Arrays.equals(backing(), other.backing()));
    }

    private long[] backing() {
        return super.getRaw();
    }
}
