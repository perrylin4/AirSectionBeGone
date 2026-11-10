package com.perry.asbg.section;

import net.caffeinemc.mods.lithium.common.block.BlockCountingSection;
import net.caffeinemc.mods.lithium.common.block.BlockListeningSection;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.block.TrackedBlockStatePredicate;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData;
import net.caffeinemc.mods.lithium.common.world.section.RandomTickingSectionDataHelper;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

/**
 * A distinct section shell for one chunk slot, backed by globally shared immutable containers.
 */
public final class ImmutableAirVoidSection extends LevelChunkSection
        implements BlockCountingSection, BlockListeningSection, LithiumSectionData {
    private static final SectionData EMPTY_LITHIUM_DATA = createEmptyLithiumData();

    public ImmutableAirVoidSection(
            PalettedContainer<BlockState> states,
            PalettedContainerRO<Holder<Biome>> biomes
    ) {
        super(states, biomes);
    }

    @Override
    public BlockState setBlockState(int x, int y, int z, BlockState state) {
        throw immutableFailure();
    }

    @Override
    public BlockState setBlockState(int x, int y, int z, BlockState state, boolean lock) {
        throw immutableFailure();
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
        throw immutableFailure();
    }

    @Override
    public void readBiomes(FriendlyByteBuf buffer) {
        throw immutableFailure();
    }

    @Override
    public void fillBiomesFromNoise(
            BiomeResolver resolver,
            Climate.Sampler sampler,
            int biomeX,
            int biomeY,
            int biomeZ
    ) {
        throw immutableFailure();
    }

    @Override
    public void recalcBlockCounts() {
        // The immutable contents are known constants. This is also invoked virtually by super().
    }

    @Override
    public LevelChunkSection copy() {
        return new LevelChunkSection(getStates().copy(), getBiomes().copy());
    }

    @Override
    public boolean lithium$mayContainAny(TrackedBlockStatePredicate predicate) {
        return EMPTY_LITHIUM_DATA.getCountsByFlag()[predicate.getIndex()] != 0;
    }

    @Override
    public short lithium$getCount(int predicateIndex) {
        return EMPTY_LITHIUM_DATA.getCountsByFlag()[predicateIndex];
    }

    @Override
    public void lithium$trackBlockStateChange(BlockState newState, BlockState oldState) {
        throw immutableFailure();
    }

    @Override
    public void lithium$addToCallback(SectionedBlockChangeTracker tracker, long sectionPos, Level world) {
        AirVoidSectionManager.addTracker(this, tracker, sectionPos, world);
    }

    @Override
    public void lithium$removeFromCallback(SectionedBlockChangeTracker tracker) {
        AirVoidSectionManager.removeTracker(this, tracker);
    }

    @Override
    public SectionData lithium$getSectionData() {
        return EMPTY_LITHIUM_DATA;
    }

    @Override
    public SectionData lithium$getSectionDataDirect() {
        return EMPTY_LITHIUM_DATA;
    }

    private static SectionData createEmptyLithiumData() {
        // Lithium's constructor currently stores no section reference; the argument is intentionally unused.
        SectionData data = new SectionData(null);
        short[] counts = new short[BlockStateFlags.NUM_TRACKED_FLAGS];
        BlockState air = Blocks.AIR.defaultBlockState();

        for (TrackedBlockStatePredicate predicate : BlockStateFlags.TRACKED_FLAGS) {
            if (predicate.test(air)) {
                counts[predicate.getIndex()] = LevelChunkSection.SECTION_SIZE;
            }
        }
        data.setCountsByFlag(counts);

        byte[] randomTickCounts = new byte[RandomTickingSectionDataHelper.BYTE_COUNT];
        if (BlockStateFlags.RANDOM_TICKING.test(air)) {
            int remaining = LevelChunkSection.SECTION_SIZE;
            for (int i = 0; i < randomTickCounts.length; i++) {
                int count = Math.min(RandomTickingSectionDataHelper.MINISECTION_SIZE, remaining);
                randomTickCounts[i] = (byte) count;
                remaining -= count;
            }
        }
        data.setRandomTickableBlocksByY(randomTickCounts);
        return data;
    }

    private static UnsupportedOperationException immutableFailure() {
        return new UnsupportedOperationException(
                "Attempted to modify an AirSectionBeGone immutable AIR/THE_VOID section; "
                        + "replace it with copy() through its owning chunk before writing"
        );
    }
}
