package com.perry.asbg.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.perry.asbg.accessor.HeightmapStorage;
import com.perry.asbg.heightmap.HeightmapDeduplication;
import com.perry.asbg.heightmap.SharedHeightmapStorage;
import com.perry.asbg.section.ASBGSectionManager;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Heightmap.class)
public abstract class HeightmapMixin implements HeightmapStorage {
    @Shadow @Final @Mutable private BitStorage data;
    @Shadow @Final private ChunkAccess chunk;

    // Sharing is attempted once. A live array obtained before then also closes that opportunity.
    @Unique private boolean asbg$sharingClosed;

    @Override
    public void asbg$shareStorage() {
        if (this.asbg$sharingClosed || this.data.getClass() != SimpleBitStorage.class) {
            return;
        }
        this.data = HeightmapDeduplication.intern((SimpleBitStorage) this.data);
        this.asbg$sharingClosed = true;
    }

    @Override
    public long @Nullable [] asbg$copySharedData() {
        return this.data instanceof SharedHeightmapStorage shared ? shared.getRaw() : null;
    }

    @ModifyReceiver(
            method = "setHeight",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BitStorage;set(II)V")
    )
    private BitStorage asbg$detachBeforeHeightChange(BitStorage storage, int index, int value) {
        if (storage instanceof SharedHeightmapStorage shared && shared.get(index) != value) {
            this.data = shared.copy();
            return this.data;
        }
        return storage;
    }

    @Inject(method = "setRawData", at = @At("HEAD"))
    private void asbg$prepareRawWrite(CallbackInfo ci) {
        this.asbg$detachSharedStorage();
    }

    @Inject(method = "getRawData", at = @At("HEAD"))
    private void asbg$prepareRawAccess(CallbackInfoReturnable<long[]> cir) {
        if (ASBGSectionManager.isEnabled() && !this.asbg$sharingClosed && this.chunk instanceof LevelChunk) {
            this.asbg$sharingClosed = true;
        }
        this.asbg$detachSharedStorage();
    }

    @Unique
    private void asbg$detachSharedStorage() {
        if (this.data instanceof SharedHeightmapStorage shared) {
            this.data = shared.copy();
        }
    }
}
