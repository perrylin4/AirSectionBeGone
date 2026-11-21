package com.perry.asbg.accessor;

import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public interface HeightmapStorage {
    static long @Nullable [] copySharedData(Heightmap heightmap) {
        return ((HeightmapStorage) heightmap).asbg$copySharedData();
    }

    void asbg$shareStorage();

    /** Returns an independent packed snapshot, or null to use the vanilla getter and clone. */
    long @Nullable [] asbg$copySharedData();
}
