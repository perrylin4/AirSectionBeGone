package com.perry.asbg.section;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import com.perry.asbg.accessor.LevelChunkSectionStorage;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.block.TrackedBlockStatePredicate;
import net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData;
import net.caffeinemc.mods.lithium.common.world.section.RandomTickingSectionDataHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;

public final class ASBGSectionManager {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("airsectionbegone.enabled", "true")
    );
    private static final Map<Registry<Biome>, SharedRegistryData> SHARED_DATA = new IdentityHashMap<>();

    private ASBGSectionManager() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void canonicalizeAll(LevelChunk chunk) {
        if (!isEnabled() || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        canonicalizeSections(level, chunk.getSections());
    }

    public static void canonicalizeImposterSections(
            LevelChunk wrapped,
            LevelChunkSection[] backingSections
    ) {
        if (!isEnabled() || !(wrapped.getLevel() instanceof ServerLevel level)) {
            return;
        }

        canonicalizeSections(level, backingSections);
    }

    public static void canonicalizeAllBiomes(LevelChunk chunk) {
        if (!isEnabled() || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        for (LevelChunkSection section : chunk.getSections()) {
            canonicalizeBiomes(level, section);
        }
    }

    public static void prepareBlockWrite(LevelChunk chunk, BlockPos pos, BlockState newState) {
        if (!isEnabled() || newState.isAir() || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int index = chunk.getSectionIndex(pos.getY());
        if (isInvalidIndex(chunk, index)) {
            return;
        }

        SectionRefoldScheduler.cancel(level, sectionPos(chunk, index));
        thawStates(chunk.getSection(index));
    }

    public static void prepareSectionBlockWrite(
            LevelChunkSection section,
            int x,
            int y,
            int z,
            BlockState newState
    ) {
        if (!isEnabled() || !(section.getStates() instanceof ImmutablePalettedContainer<?>)
                || section.getBlockState(x, y, z) == newState) {
            return;
        }

        thawStates(section);
    }

    public static void finishBlockWrite(LevelChunk chunk, BlockPos pos, BlockState newState) {
        if (!isEnabled() || AirKind.fromState(newState) == null
                || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int index = chunk.getSectionIndex(pos.getY());
        if (isInvalidIndex(chunk, index)) {
            return;
        }

        LevelChunkSection section = chunk.getSection(index);
        if (section.getClass() == LevelChunkSection.class
                && section.getStates().getClass() == PalettedContainer.class
                && section.hasOnlyAir()) {
            SectionRefoldScheduler.schedule(level, sectionPos(chunk, index));
        }
    }

    static boolean canonicalizeStates(ServerLevel level, LevelChunkSection section) {
        if (!isEnabled() || section.getClass() != LevelChunkSection.class
                || section.getStates().getClass() != PalettedContainer.class
                || !section.hasOnlyAir()) {
            return false;
        }

        AirKind kind = findUniformAirKind(section.getStates());
        if (kind == null) {
            return false;
        }

        SharedAirData shared = sharedData(level).air(kind);
        ((LevelChunkSectionStorage) section).asbg$setStates(shared.states());

        LithiumSectionData.SectionData sectionData = ((LithiumSectionData) section).lithium$getSectionData();
        sectionData.setCountsByFlag(shared.countsByFlag());
        sectionData.setRandomTickableBlocksByY(shared.randomTickCounts());
        return true;
    }

    static void canonicalizeBiomes(ServerLevel level, LevelChunkSection section) {
        if (!isEnabled() || section.getClass() != LevelChunkSection.class
                || section.getBiomes().getClass() != PalettedContainer.class) {
            return;
        }

        Optional<ResourceKey<Biome>> biomeKey = findUniformBiomeKey(section.getBiomes());
        if (biomeKey.isEmpty()) {
            return;
        }

        ((LevelChunkSectionStorage) section).asbg$setBiomes(
                sharedData(level).biome(biomeKey.get())
        );
    }

    public static void clearCaches() {
        synchronized (SHARED_DATA) {
            SHARED_DATA.clear();
        }
        SectionRefoldScheduler.clearAll();
    }

    private static void canonicalizeSections(ServerLevel level, LevelChunkSection[] sections) {
        for (LevelChunkSection section : sections) {
            canonicalizeStates(level, section);
            canonicalizeBiomes(level, section);
        }
    }

    private static void thawStates(LevelChunkSection section) {
        if (!(section.getStates() instanceof ImmutablePalettedContainer<?>)) {
            return;
        }

        synchronized (section) {
            if (!(section.getStates() instanceof ImmutablePalettedContainer<?>)) {
                return;
            }

            ((LevelChunkSectionStorage) section).asbg$setStates(section.getStates().copy());

            LithiumSectionData.SectionData sectionData = ((LithiumSectionData) section).lithium$getSectionData();
            short[] countsByFlag = sectionData.getCountsByFlag();
            if (countsByFlag != null) {
                sectionData.setCountsByFlag(countsByFlag.clone());
            }
            byte[] randomTickCounts = sectionData.getRandomTickableBlocksByY();
            if (randomTickCounts != null) {
                sectionData.setRandomTickableBlocksByY(randomTickCounts.clone());
            }
        }
    }

    private static AirKind findUniformAirKind(PalettedContainer<BlockState> states) {
        BlockState first = states.get(0, 0, 0);
        AirKind kind = AirKind.fromState(first);
        if (kind == null) {
            return null;
        }
        if (states.bitsPerEntry() == 0) {
            return kind;
        }

        // Writes do not shrink a widened palette back to zero bits.
        // Nonzero bits therefore cannot disprove uniformity; check the stored values.
        for (int y = 0; y < LevelChunkSection.SECTION_HEIGHT; y++) {
            for (int z = 0; z < LevelChunkSection.SECTION_WIDTH; z++) {
                for (int x = 0; x < LevelChunkSection.SECTION_WIDTH; x++) {
                    if (states.get(x, y, z) != first) {
                        return null;
                    }
                }
            }
        }
        return kind;
    }

    private static Optional<ResourceKey<Biome>> findUniformBiomeKey(
            PalettedContainerRO<Holder<Biome>> biomes
    ) {
        Holder<Biome> first = biomes.get(0, 0, 0);
        Optional<ResourceKey<Biome>> key = first.unwrapKey();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        if (biomes.bitsPerEntry() == 0) {
            return key;
        }

        // A widened biome palette can remain nonzero-bit after all values become equal.
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    if (!biomes.get(x, y, z).is(key.get())) {
                        return Optional.empty();
                    }
                }
            }
        }
        return key;
    }

    private static SharedRegistryData sharedData(ServerLevel level) {
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        synchronized (SHARED_DATA) {
            SharedRegistryData existing = SHARED_DATA.get(biomeRegistry);
            if (existing != null) {
                return existing;
            }

            SharedRegistryData created = new SharedRegistryData(
                    biomeRegistry,
                    level.palettedContainerFactory()
            );
            SHARED_DATA.put(biomeRegistry, created);
            return created;
        }
    }

    private static short[] createCounts(BlockState state) {
        short[] counts = new short[BlockStateFlags.NUM_TRACKED_FLAGS];
        for (TrackedBlockStatePredicate predicate : BlockStateFlags.TRACKED_FLAGS) {
            if (predicate.test(state)) {
                counts[predicate.getIndex()] = LevelChunkSection.SECTION_SIZE;
            }
        }
        return counts;
    }

    private static byte[] createRandomTickCounts(BlockState state) {
        byte[] counts = new byte[RandomTickingSectionDataHelper.BYTE_COUNT];
        if (!BlockStateFlags.RANDOM_TICKING.test(state)) {
            return counts;
        }

        int remaining = LevelChunkSection.SECTION_SIZE;
        for (int i = 0; i < counts.length; i++) {
            int count = Math.min(RandomTickingSectionDataHelper.MINISECTION_SIZE, remaining);
            counts[i] = (byte) count;
            remaining -= count;
        }
        return counts;
    }

    private static long sectionPos(LevelChunk chunk, int index) {
        return SectionPos.asLong(
                chunk.getPos().x,
                chunk.getSectionYFromSectionIndex(index),
                chunk.getPos().z
        );
    }

    private static boolean isInvalidIndex(LevelChunk chunk, int index) {
        return index < 0 || index >= chunk.getSections().length;
    }

    private record SharedAirData(
            ImmutablePalettedContainer<BlockState> states,
            short[] countsByFlag,
            byte[] randomTickCounts
    ) {
    }

    private static final class SharedRegistryData {
        private final Registry<Biome> biomeRegistry;
        private final PalettedContainerFactory factory;
        private final Map<AirKind, SharedAirData> airData = new EnumMap<>(AirKind.class);
        private final Map<ResourceKey<Biome>, ImmutablePalettedContainer<Holder<Biome>>> biomeData = new HashMap<>();

        private SharedRegistryData(Registry<Biome> biomeRegistry, PalettedContainerFactory factory) {
            this.biomeRegistry = biomeRegistry;
            this.factory = factory;
            for (AirKind kind : AirKind.values()) {
                BlockState state = kind.state();
                airData.put(kind, new SharedAirData(
                        new ImmutablePalettedContainer<>(state, factory.blockStatesStrategy()),
                        createCounts(state),
                        createRandomTickCounts(state)
                ));
            }
        }

        private SharedAirData air(AirKind kind) {
            return airData.get(kind);
        }

        private ImmutablePalettedContainer<Holder<Biome>> biome(ResourceKey<Biome> key) {
            synchronized (SHARED_DATA) {
                return biomeData.computeIfAbsent(key, biomeKey -> new ImmutablePalettedContainer<>(
                        biomeRegistry.getOrThrow(biomeKey),
                        factory.biomeStrategy()
                ));
            }
        }
    }
}
