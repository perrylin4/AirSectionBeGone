package com.perry.asbg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.perry.asbg.accessor.HeightmapStorage;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ClientboundLevelChunkPacketDataMixin {
    // The constructor's lambda is the only (Map.Entry) -> long[] method in this class.
    @WrapOperation(
            method = "(Ljava/util/Map$Entry;)[J",
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
            method = "(Ljava/util/Map$Entry;)[J",
            at = @At(value = "INVOKE", desc = @Desc(owner = long[].class, value = "clone", ret = Object.class), remap = false),
            require = 0
    )
    // The bytecode calls [J.clone()Ljava/lang/Object; with the long[] as its receiver.
    @SuppressWarnings("InvalidInjectorMethodSignature")
    private static Object asbg$reuseHeightmapSnapshot(
            long[] source, Operation<Object> original, @Share("heightmapSnapshot") LocalRef<long[]> snapshot
    ) {
        if (source != null && source == snapshot.get()) {
            snapshot.set(null);
            return source;
        }
        return original.call(source);
    }
}
