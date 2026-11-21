package com.perry.asbg.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.perry.asbg.accessor.HeightmapStorage;
import com.perry.asbg.heightmap.HeightmapDeduplication;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;

@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {
    @ModifyReturnValue(method = "read", at = @At("RETURN"))
    private ProtoChunk asbg$shareLoadedHeightmaps(ProtoChunk result) {
        if (result instanceof ImposterProtoChunk imposter) {
            HeightmapDeduplication.shareHeightmaps(imposter.getWrapped());
        }
        return result;
    }

    @WrapOperation(
            method = "copyOf",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Heightmap;getRawData()[J"),
            require = 0
    )
    private static long[] asbg$snapshotHeightmap(
            Heightmap heightmap, Operation<long[]> original, @Share("heightmapSnapshot") LocalRef<long[]> snapshot
    ) {
        long[] copy = HeightmapStorage.copySharedData(heightmap);
        snapshot.set(copy);
        return copy == null ? original.call(heightmap) : copy;
    }

    @WrapOperation(
            method = "copyOf",
            at = @At(value = "INVOKE", desc = @Desc(owner = long[].class, value = "clone", ret = Object.class), remap = false),
            require = 0
    )
    // The bytecode calls [J.clone()Ljava/lang/Object; with the long[] as its receiver.
    @SuppressWarnings("DisallowedTargetInsn")
    private static Object asbg$reuseHeightmapSnapshot(
            long[] source, Operation<Object> original, @Share("heightmapSnapshot") LocalRef<long[]> snapshot
    ) {
        // Only skip the clone for the independent array created by our other hook.
        if (source != null && source == snapshot.get()) {
            snapshot.set(null);
            return source;
        }
        return original.call(source);
    }
}
