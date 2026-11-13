package com.perry.asbg.mixin;

import com.perry.asbg.section.ASBGSectionManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {
    @Inject(method = "fillBiomesFromNoise", at = @At("RETURN"))
    private void asbg$finishBiomeWrite(
            BiomeResolver resolver,
            Climate.Sampler sampler,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof LevelChunk chunk) {
            ASBGSectionManager.canonicalizeAllBiomes(chunk);
        }
    }
}
