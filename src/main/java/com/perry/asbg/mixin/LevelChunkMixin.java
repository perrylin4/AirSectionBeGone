package com.perry.asbg.mixin;

import com.perry.asbg.section.AirVoidSectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;"
                    + "Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;"
                    + "Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
                    + "Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;"
                    + "Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At("TAIL")
    )
    private void asbg$canonicalizeAfterConstruction(
            Level level,
            ChunkPos chunkPos,
            UpgradeData upgradeData,
            LevelChunkTicks<Block> blockTicks,
            LevelChunkTicks<Fluid> fluidTicks,
            long inhabitedTime,
            LevelChunkSection[] sections,
            LevelChunk.PostLoadProcessor postLoad,
            BlendingData blendingData,
            CallbackInfo ci
    ) {
        if (level instanceof ServerLevel) {
            AirVoidSectionManager.canonicalizeAll((LevelChunk) (Object) this);
        }
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD")
    )
    private void asbg$prepareBlockWrite(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<BlockState> cir
    ) {
        AirVoidSectionManager.prepareBlockWrite((LevelChunk) (Object) this, pos, state);
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void asbg$finishBlockWrite(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<BlockState> cir
    ) {
        AirVoidSectionManager.finishBlockWrite((LevelChunk) (Object) this, pos, state);
    }
}
