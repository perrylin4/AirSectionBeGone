package com.perry.asbg.section;

import com.google.common.collect.MapMaker;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SectionRefoldScheduler {
    public static final int REFOLD_DELAY_TICKS = 200;
    public static final int MAX_REFOLDS_PER_WORLD_TICK = 64;
    private static final int MAX_QUEUE_POLLS_PER_WORLD_TICK = 256;

    private static final Map<ServerLevel, WorldQueue> WORLD_QUEUES =
            new MapMaker().weakKeys().makeMap();

    private SectionRefoldScheduler() {
    }

    public static void schedule(ServerLevel level, long sectionPos) {
        WorldQueue queue = WORLD_QUEUES.computeIfAbsent(level, ignored -> new WorldQueue());
        if (queue.bySection.containsKey(sectionPos)) {
            return;
        }

        Candidate candidate = new Candidate(sectionPos, level.getGameTime() + REFOLD_DELAY_TICKS);
        queue.bySection.put(sectionPos, candidate);
        queue.candidates.add(candidate);
    }

    public static void cancel(ServerLevel level, long sectionPos) {
        WorldQueue queue = WORLD_QUEUES.get(level);
        if (queue == null) {
            return;
        }

        Candidate candidate = queue.bySection.remove(sectionPos);
        if (candidate == null) {
            return;
        }

        queue.candidates.remove(candidate);
        if (queue.candidates.isEmpty()) {
            WORLD_QUEUES.remove(level);
        }
    }

    public static void tick(ServerLevel level) {
        if (!ASBGSectionManager.isEnabled()) {
            return;
        }

        WorldQueue queue = WORLD_QUEUES.get(level);
        if (queue == null) {
            return;
        }

        long gameTime = level.getGameTime();
        int folded = 0;
        int polled = 0;

        while (folded < MAX_REFOLDS_PER_WORLD_TICK
                && polled < MAX_QUEUE_POLLS_PER_WORLD_TICK
                && !queue.candidates.isEmpty()) {
            Candidate candidate = queue.candidates.first();
            if (candidate.dueTime() > gameTime) {
                break;
            }

            queue.candidates.pollFirst();
            queue.bySection.remove(candidate.sectionPos());
            polled++;

            int chunkX = SectionPos.x(candidate.sectionPos());
            int chunkZ = SectionPos.z(candidate.sectionPos());
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }

            int index = chunk.getSectionIndexFromSectionY(SectionPos.y(candidate.sectionPos()));
            if (index >= 0 && index < chunk.getSections().length
                    && ASBGSectionManager.canonicalizeStates(level, chunk.getSection(index))) {
                folded++;
            }
        }

        if (queue.candidates.isEmpty()) {
            WORLD_QUEUES.remove(level);
        }
    }

    public static void clear(ServerLevel level) {
        WORLD_QUEUES.remove(level);
    }

    public static void clearAll() {
        WORLD_QUEUES.clear();
    }

    private record Candidate(long sectionPos, long dueTime) implements Comparable<Candidate> {
        @Override
        public int compareTo(Candidate other) {
            int order = Long.compare(dueTime, other.dueTime);
            // Sections scheduled in the same tick must remain distinct in the sorted set.
            return order != 0 ? order : Long.compare(sectionPos, other.sectionPos);
        }
    }

    private static final class WorldQueue {
        // Both collections hold the same candidate; cancellation removes it immediately.
        private final Long2ObjectMap<Candidate> bySection = new Long2ObjectOpenHashMap<>();
        private final NavigableSet<Candidate> candidates = new TreeSet<>();
    }
}
