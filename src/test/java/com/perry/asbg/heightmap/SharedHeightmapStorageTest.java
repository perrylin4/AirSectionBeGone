package com.perry.asbg.heightmap;

import java.util.Arrays;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedHeightmapStorageTest {
    @Test
    void internChecksContentAndLayoutAfterHashMatching() {
        var plain = new SimpleBitStorage(11, 256);
        var shared = HeightmapDeduplication.intern(plain);
        assertSame(shared, HeightmapDeduplication.intern(new SimpleBitStorage(11, 256)));

        // These bit widths and sizes use the same number of packed longs.
        assertNotSame(shared, HeightmapDeduplication.intern(new SimpleBitStorage(12, 256)));
        assertNotSame(shared, HeightmapDeduplication.intern(new SimpleBitStorage(11, 257)));

        long[] collision = plain.getRaw().clone();
        collision[0] = 1;
        collision[1] = Integer.toUnsignedLong(-31);
        assertEquals(Arrays.hashCode(plain.getRaw()), Arrays.hashCode(collision));
        var other = HeightmapDeduplication.intern(new SimpleBitStorage(11, 256, collision));
        assertNotSame(shared, other);
        assertEquals(0, shared.get(0));
        assertEquals(1, other.get(0));
    }

    @Test
    void publishedDataCannotBeMutatedThroughAnyStorageApi() {
        var source = new SimpleBitStorage(9, 256);
        source.set(19, 7);
        var shared = HeightmapDeduplication.intern(source);
        source.set(19, 3);

        long[] snapshot = shared.getRaw();
        Arrays.fill(snapshot, -1);
        BitStorage copy = shared.copy();
        copy.set(19, 1);

        assertEquals(7, shared.get(19));
        assertEquals(1, copy.get(19));
        assertEquals(7, shared.getAndSet(19, 7));
        shared.set(19, 7);
        assertThrows(UnsupportedOperationException.class, () -> shared.set(19, 8));
        assertThrows(UnsupportedOperationException.class, () -> shared.getAndSet(19, 8));
    }
}
