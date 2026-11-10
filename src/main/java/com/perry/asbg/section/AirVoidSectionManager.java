package com.perry.asbg.section;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.caffeinemc.mods.lithium.common.tracking.block.ChunkSectionChangeCallback;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.caffeinemc.mods.lithium.common.world.LithiumData;
import net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;

public final class AirVoidSectionManager {
    private static final Map<Registry<Biome>, SharedContainers> SHARED_CONTAINERS = new IdentityHashMap<>();
    private static final Map<ImmutableAirVoidSection, ChunkSectionChangeCallback> PLACEHOLDER_CALLBACKS =
            new WeakHashMap<>();

    private AirVoidSectionManager() {
    }

    public static void canonicalizeAll(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            canonicalizeSection(level, chunk, index);
        }
    }

    public static void prepareBlockWrite(LevelChunk chunk, BlockPos pos, BlockState newState) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int index = chunk.getSectionIndex(pos.getY());
        if (!isValidIndex(chunk, index)) {
            return;
        }

        LevelChunkSection section = chunk.getSection(index);
        if (section instanceof ImmutableAirVoidSection immutableSection) {
            if (!newState.isAir()) {
                thaw(level, chunk, index, immutableSection);
            }
            return;
        }

        if (newState == Blocks.AIR.defaultBlockState()
                && section.getClass() == LevelChunkSection.class
                && containsOnlyVoid(section.getBiomes())) {
            invalidateMutableCallback(level, chunk, index, section);
        }
    }

    public static void finishBlockWrite(LevelChunk chunk, BlockPos pos, BlockState newState) {
        if (newState != Blocks.AIR.defaultBlockState()
                || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int index = chunk.getSectionIndex(pos.getY());
        if (isValidIndex(chunk, index)) {
            canonicalizeSection(level, chunk, index);
        }
    }

    public static void prepareBiomeWrite(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section instanceof ImmutableAirVoidSection immutableSection) {
                thaw(level, chunk, index, immutableSection);
            } else {
                invalidateMutableCallback(level, chunk, index, section);
            }
        }
    }

    public static void addTracker(
            ImmutableAirVoidSection section,
            SectionedBlockChangeTracker tracker,
            long sectionPos,
            Level world
    ) {
        synchronized (PLACEHOLDER_CALLBACKS) {
            ChunkSectionChangeCallback callback = PLACEHOLDER_CALLBACKS.get(section);
            if (callback == null) {
                if (sectionPos == Long.MIN_VALUE || world == null) {
                    throw new IllegalArgumentException("Expected world and section position for listener initialization");
                }
                callback = ChunkSectionChangeCallback.create(sectionPos, world);
                PLACEHOLDER_CALLBACKS.put(section, callback);
            } else if (sectionPos != Long.MIN_VALUE && callback.getSectionPos() != sectionPos) {
                throw new IllegalStateException("An immutable section shell was reused for two world positions");
            }
            callback.addTracker(tracker);
        }
    }

    public static void removeTracker(ImmutableAirVoidSection section, SectionedBlockChangeTracker tracker) {
        synchronized (PLACEHOLDER_CALLBACKS) {
            ChunkSectionChangeCallback callback = PLACEHOLDER_CALLBACKS.get(section);
            if (callback != null) {
                callback.removeTracker(tracker);
            }
        }
    }

    public static void clearCaches() {
        synchronized (SHARED_CONTAINERS) {
            SHARED_CONTAINERS.clear();
        }
        synchronized (PLACEHOLDER_CALLBACKS) {
            PLACEHOLDER_CALLBACKS.clear();
        }
    }

    private static void canonicalizeSection(ServerLevel level, LevelChunk chunk, int index) {
        LevelChunkSection section = chunk.getSection(index);
        if (!isCanonicalizationCandidate(section)) {
            return;
        }

        invalidateMutableCallback(level, chunk, index, section);
        SharedContainers shared = sharedContainers(level);
        chunk.getSections()[index] = new ImmutableAirVoidSection(shared.states(), shared.biomes());
    }

    private static boolean isCanonicalizationCandidate(LevelChunkSection section) {
        if (section.getClass() != LevelChunkSection.class || !section.hasOnlyAir()) {
            return false;
        }
        return containsOnlyExactAir(section.getStates()) && containsOnlyVoid(section.getBiomes());
    }

    private static boolean containsOnlyExactAir(PalettedContainer<BlockState> states) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (states.bitsPerEntry() == 0) {
            return states.get(0, 0, 0) == air;
        }

        boolean[] exactAir = {true};
        states.count((state, count) -> {
            if (count != 0 && state != air) {
                exactAir[0] = false;
            }
        });
        return exactAir[0];
    }

    private static boolean containsOnlyVoid(PalettedContainerRO<Holder<Biome>> biomes) {
        if (biomes.bitsPerEntry() == 0) {
            return biomes.get(0, 0, 0).is(Biomes.THE_VOID);
        }

        boolean[] onlyVoid = {true};
        biomes.count((biome, count) -> {
            if (count != 0 && !biome.is(Biomes.THE_VOID)) {
                onlyVoid[0] = false;
            }
        });
        return onlyVoid[0];
    }

    private static void thaw(
            ServerLevel level,
            LevelChunk chunk,
            int index,
            ImmutableAirVoidSection section
    ) {
        invalidatePlaceholderCallback(level, chunk, index, section);
        chunk.getSections()[index] = section.copy();
    }

    private static void invalidatePlaceholderCallback(
            ServerLevel level,
            LevelChunk chunk,
            int index,
            ImmutableAirVoidSection section
    ) {
        ChunkSectionChangeCallback callback;
        synchronized (PLACEHOLDER_CALLBACKS) {
            callback = PLACEHOLDER_CALLBACKS.remove(section);
        }
        if (callback != null) {
            long sectionPos = sectionPos(chunk, index);
            detachWorldCallback(level, sectionPos, callback);
            callback.onChunkSectionInvalidated(SectionPos.of(sectionPos));
        }
    }

    private static void invalidateMutableCallback(
            ServerLevel level,
            LevelChunk chunk,
            int index,
            LevelChunkSection section
    ) {
        LithiumSectionData.SectionData sectionData;
        try {
            sectionData = ((LithiumSectionData) section).lithium$getSectionDataDirect();
        } catch (NullPointerException ignored) {
            return;
        }

        ChunkSectionChangeCallback callback = sectionData.getChangeListener();
        if (callback == null) {
            return;
        }

        sectionData.setChangeListener(null);
        long sectionPos = sectionPos(chunk, index);
        detachWorldCallback(level, sectionPos, callback);
        callback.onChunkSectionInvalidated(SectionPos.of(sectionPos));
    }

    private static void detachWorldCallback(
            Level level,
            long sectionPos,
            ChunkSectionChangeCallback callback
    ) {
        var callbacks = ((LithiumData) level).lithium$getData().chunkSectionChangeCallbacks();
        if (callbacks.get(sectionPos) == callback) {
            callbacks.remove(sectionPos);
        }
    }

    private static SharedContainers sharedContainers(ServerLevel level) {
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        synchronized (SHARED_CONTAINERS) {
            SharedContainers existing = SHARED_CONTAINERS.get(biomeRegistry);
            if (existing != null) {
                return existing;
            }

            PalettedContainerFactory factory = level.palettedContainerFactory();
            SharedContainers created = new SharedContainers(
                    new ImmutablePalettedContainer<>(
                            Blocks.AIR.defaultBlockState(),
                            factory.blockStatesStrategy()
                    ),
                    new ImmutablePalettedContainer<>(
                            biomeRegistry.getOrThrow(Biomes.THE_VOID),
                            factory.biomeStrategy()
                    )
            );
            SHARED_CONTAINERS.put(biomeRegistry, created);
            return created;
        }
    }

    private static long sectionPos(LevelChunk chunk, int index) {
        return SectionPos.asLong(
                chunk.getPos().x,
                chunk.getSectionYFromSectionIndex(index),
                chunk.getPos().z
        );
    }

    private static boolean isValidIndex(LevelChunk chunk, int index) {
        return index >= 0 && index < chunk.getSections().length;
    }

    private record SharedContainers(
            ImmutablePalettedContainer<BlockState> states,
            ImmutablePalettedContainer<Holder<Biome>> biomes
    ) {
    }
}
