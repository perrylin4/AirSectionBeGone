package com.perry.asbg.mixin;

import com.perry.asbg.accessor.ChunkAccessSectionStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
public interface ChunkAccessSectionStorageMixin extends ChunkAccessSectionStorage {
    @Override
    @Accessor("sections")
    LevelChunkSection[] asbg$getBackingSections();
}
