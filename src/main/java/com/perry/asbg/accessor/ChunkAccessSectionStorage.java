package com.perry.asbg.accessor;

import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Exposes the section array owned by ChunkAccess itself, bypassing virtual getSections() overrides.
 */
public interface ChunkAccessSectionStorage {
    LevelChunkSection[] asbg$getBackingSections();
}
