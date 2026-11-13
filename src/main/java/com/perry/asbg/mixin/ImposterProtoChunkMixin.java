package com.perry.asbg.mixin;

import com.perry.asbg.section.ASBGSectionManager;
import com.perry.asbg.accessor.ChunkAccessSectionStorage;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void asbg$canonicalizeBackingSections(
            LevelChunk wrapped,
            boolean allowWrites,
            CallbackInfo ci
    ) {
        ASBGSectionManager.canonicalizeImposterSections(
                wrapped,
                ((ChunkAccessSectionStorage) this).asbg$getBackingSections()
        );
    }
}
