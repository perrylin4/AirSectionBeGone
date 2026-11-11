package com.perry.asbg.mixin;

import com.perry.asbg.section.LevelChunkSectionStorage;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionStorageMixin implements LevelChunkSectionStorage {
    @Shadow
    @Final
    @Mutable
    private PalettedContainer<BlockState> states;

    @Shadow
    private PalettedContainerRO<Holder<Biome>> biomes;

    @Override
    public void asbg$setStates(PalettedContainer<BlockState> states) {
        this.states = states;
    }

    @Override
    public void asbg$setBiomes(PalettedContainerRO<Holder<Biome>> biomes) {
        this.biomes = biomes;
    }
}
