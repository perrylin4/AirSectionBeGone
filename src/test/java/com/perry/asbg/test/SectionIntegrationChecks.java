package com.perry.asbg.test;

import com.perry.asbg.section.ASBGSectionManager;
import com.perry.asbg.section.AirKind;
import com.perry.asbg.section.ImmutablePalettedContainer;
import com.perry.asbg.section.SectionRefoldScheduler;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.storage.ServerLevelData;

final class SectionIntegrationChecks {
    static void run(MinecraftServer server) throws Exception {
        if (!ASBGSectionManager.isEnabled()) {
            return;
        }
        for (AirKind kind : AirKind.values()) {
            check(AirKind.fromState(kind.state()) == kind, "air classification changed");
        }
        check(AirKind.fromState(Blocks.STONE.defaultBlockState()) == null, "stone classified as air");

        ServerLevel level = server.overworld();
        SectionRefoldScheduler.clear(level);
        List<LevelChunkSection> candidates = new ArrayList<>();
        List<LevelChunk> chunks = new ArrayList<>();
        long originalTime = level.getGameTime();
        try {
            // Equal deadlines sort by section position: 64 failures precede 16 successes.
            for (int x = 8; x < 12; x++) {
                LevelChunk chunk = level.getChunk(x, 8);
                chunks.add(chunk);
                for (int sectionY = 0; sectionY < 20; sectionY++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                    section.setBlockState(0, 0, 0, Blocks.STONE.defaultBlockState(), false);
                    section.setBlockState(0, 0, 0, Blocks.AIR.defaultBlockState(), false);
                    if (candidates.size() < 64) {
                        section.setBlockState(15, 15, 15, Blocks.CAVE_AIR.defaultBlockState(), false);
                    }
                    check(section.hasOnlyAir(), "fixture not empty");
                    candidates.add(section);
                }
            }
            // Exclude any unrelated candidates produced while the test chunks were loading.
            SectionRefoldScheduler.clear(level);
            for (LevelChunk chunk : chunks) {
                for (int sectionY = 0; sectionY < 20; sectionY++) {
                    SectionRefoldScheduler.schedule(level, SectionPos.asLong(chunk.getPos().x, sectionY, 8));
                }
            }
            ((ServerLevelData) level.getLevelData()).setGameTime(originalTime + SectionRefoldScheduler.REFOLD_DELAY_TICKS);
            SectionRefoldScheduler.tick(level);
            for (int index = 64; index < 80; index++) {
                check(!(candidates.get(index).getStates() instanceof ImmutablePalettedContainer<?>),
                        "failed refolds bypassed the per-tick scan budget");
            }
            SectionRefoldScheduler.tick(level);
            for (int index = 64; index < 80; index++) {
                check(candidates.get(index).getStates() instanceof ImmutablePalettedContainer<?>, "valid refold did not run next tick");
            }

            var shared = candidates.getLast().getStates();
            var copy = shared.copy();
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new PalettedContainer<>(Blocks.STONE.defaultBlockState(), level.palettedContainerFactory().blockStatesStrategy()).write(packet);
                copy.read(packet);
                check(copy.get(0, 0, 0) == Blocks.STONE.defaultBlockState(), "mutable copy did not read packet");
                check(shared.get(0, 0, 0) == Blocks.AIR.defaultBlockState(), "packet read polluted shared air palette");
            } finally {
                packet.release();
            }

            SectionRefoldScheduler.clear(level);
            BlockPos unchanged = new BlockPos(8 * 16, 0, 8 * 16);
            check(chunks.getFirst().setBlockState(unchanged, Blocks.AIR.defaultBlockState(), 2) == null,
                    "same-air fixture unexpectedly changed a block");
            Field queuesField = SectionRefoldScheduler.class.getDeclaredField("WORLD_QUEUES");
            queuesField.setAccessible(true);
            check(!((Map<?, ?>) queuesField.get(null)).containsKey(level), "no-op air write queued a refold");
            System.out.println("ASBG_SECTION_TEST_PASS failedScans=64 deferredSuccesses=16 noOpQueue=0 isolatedCopy=true");
        } finally {
            ((ServerLevelData) level.getLevelData()).setGameTime(originalTime);
            SectionRefoldScheduler.clear(level);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
