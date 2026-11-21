package com.perry.asbg.heightmap;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.perry.asbg.accessor.HeightmapStorage;
import com.perry.asbg.section.ASBGSectionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class HeightmapDeduplication {
    private static final Interner<SharedHeightmapStorage> POOL = Interners.newWeakInterner();

    private HeightmapDeduplication() {
    }

    /** Called after the heightmaps of a newly initialized full chunk are populated. */
    public static void shareHeightmaps(LevelChunk chunk) {
        if (!ASBGSectionManager.isEnabled() || !(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }
        for (var entry : chunk.getHeightmaps()) {
            Heightmap heightmap = entry.getValue();
            if (entry.getKey().keepAfterWorldgen() && heightmap.getClass() == Heightmap.class) {
                ((HeightmapStorage) heightmap).asbg$shareStorage();
            }
        }
    }

    public static SharedHeightmapStorage intern(SimpleBitStorage source) {
        return POOL.intern(new SharedHeightmapStorage(source));
    }
}
