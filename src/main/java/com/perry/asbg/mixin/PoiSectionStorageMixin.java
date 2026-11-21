package com.perry.asbg.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.perry.asbg.poi.LithiumPoiColumns;
import com.perry.asbg.poi.PoiColumnTracker;
import com.perry.asbg.section.ASBGSectionManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lithium adds lithium$getElementAt at the default priority (1000).
@Mixin(value = SectionStorage.class, priority = 900)
public abstract class PoiSectionStorageMixin<R> {
    @Shadow @Final @Mutable private LongSet loadedChunks;
    @Shadow protected abstract boolean outsideStoredRange(long section);

    @Unique
    private PoiColumnTracker asbg$poiColumns;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void asbg$trackPoiColumns(CallbackInfo ci) {
        if (!ASBGSectionManager.isEnabled()) {
            return;
        }
        this.asbg$poiColumns = PoiColumnTracker.forStorage((SectionStorage<?, ?>) (Object) this, this.loadedChunks);
        if (this.asbg$poiColumns != null) {
            this.loadedChunks = this.asbg$poiColumns;
        }
    }

    @Inject(
            method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/SectionStorage$PackedChunk;)V",
            at = @At("HEAD")
    )
    private void asbg$beginColumnLoad(ChunkPos pos, @Coerce Object packed, CallbackInfo ci) {
        if (this.asbg$poiColumns != null) {
            this.asbg$poiColumns.beginLoad(pos.toLong());
        }
    }

    @WrapOperation(
            method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/SectionStorage$PackedChunk;)V",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;put(JLjava/lang/Object;)Ljava/lang/Object;")
    )
    private Object asbg$omitEmptySections(
            Long2ObjectMap<?> storage, long section, Object value, Operation<Object> original
    ) {
        if (this.asbg$poiColumns != null && value instanceof Optional<?> optional && optional.isEmpty()) {
            return storage.remove(section);
        }
        return original.call(storage, section, value);
    }

    @Inject(
            method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/SectionStorage$PackedChunk;)V",
            at = @At("RETURN")
    )
    private void asbg$finishColumnLoad(ChunkPos pos, @Coerce Object packed, CallbackInfo ci) {
        if (this.asbg$poiColumns != null) {
            long column = pos.toLong();
            LithiumPoiColumns.loaded((SectionStorage<?, ?>) (Object) this, column);
            this.asbg$poiColumns.finishLoad(column);
        }
    }

    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private @Nullable Optional<R> asbg$readKnownEmptySection(@Nullable Optional<R> result, long section) {
        return this.asbg$restoreEmptyResult(result, section);
    }

    @Dynamic("Added to SectionStorage by Lithium's optional ai.poi mixins")
    @ModifyReturnValue(method = "lithium$getElementAt", at = @At("RETURN"), remap = false, require = 0)
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private @Nullable Optional<R> asbg$readLithiumKnownEmptySection(@Nullable Optional<R> result, long section) {
        return this.asbg$restoreEmptyResult(result, section);
    }

    @Unique
    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    private @Nullable Optional<R> asbg$restoreEmptyResult(@Nullable Optional<R> result, long section) {
        // Vanilla distinguishes an unloaded section (null) from a loaded empty one (Optional.empty()).
        if (result == null && this.asbg$poiColumns != null && !this.outsideStoredRange(section)) {
            long column = ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section));
            if (this.asbg$poiColumns.isReady(column)) {
                return Optional.empty();
            }
        }
        return result;
    }
}
