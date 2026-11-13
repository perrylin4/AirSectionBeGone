package com.perry.asbg.accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

public interface LevelChunkSectionStorage {
    void asbg$setStates(PalettedContainer<BlockState> states);

    void asbg$setBiomes(PalettedContainerRO<Holder<Biome>> biomes);
}
